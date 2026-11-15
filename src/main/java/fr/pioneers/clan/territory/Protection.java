package fr.pioneers.clan.territory;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.seal.SealItems;
import fr.pioneers.clan.war.Siege;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Saddleable;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.decoration.HangingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AttachedStemBlock;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CocoaBlock;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DispenserBlock;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.SweetBerryBushBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraftforge.common.IPlantable;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMobGriefingEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingDestroyBlockEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.BonemealEvent;
import net.minecraftforge.event.entity.player.FillBucketEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ExplosionEvent;
import net.minecraftforge.event.level.PistonEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.9 (MAJ RP, bloc 4) : protection complete des chunks reclames. Chaque geste du joueur passe par {@code ClanApi.canModify} (une lecture
 * de l'index) : casse, pose, clic droit et gauche, conteneurs, machines et vehicules du mod, montures, cadres et supports d'armure,
 * projectiles, seaux, outils (labour, ecorcage), engrais. Pour tous, dans un claim : explosions sans degat de bloc, propagation du feu
 * coupee, fluides et pistons arretes a la frontiere, aucun degat de bloc par les creatures, aucun pietinement de culture. Rien ne tourne
 * par tick hors de la file des fluides et du feu (positions notees au passage, relues en fin de tick).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Protection {

    /** Blocs qu'un visiteur utilise en zone Ouverte (portes, trappes, boutons, leviers, cloches, etablis sans stock). */
    public static final TagKey<Block> OPEN_USE = TagKey.create(Registries.BLOCK, Pioneers.rl("clan/open_use"));
    /** Blocs qui gardent des objets sans etre des conteneurs (lutrin, jukebox, pot de fleur, etagere sculptee...). */
    public static final TagKey<Block> STORAGE = TagKey.create(Registries.BLOCK, Pioneers.rl("clan/storage"));
    /** Objets qui allument un feu (le feu pose par un joueur autorise ne s'eteint pas tout seul). */
    public static final TagKey<Item> IGNITERS = TagKey.create(Registries.ITEM, Pioneers.rl("clan/igniters"));

    private static final Map<UUID, Long> WARNED = new HashMap<>();
    private static final Map<Level, LongOpenHashSet> QUEUE = new WeakHashMap<>();
    private static final Map<Level, Long2LongOpenHashMap> LIT = new WeakHashMap<>();
    /** Cache d'activite : clans actifs et au moins un claim (relu chaque seconde : evite tout travail sur un monde sans claim). */
    private static boolean active;
    private static int recheck;

    private Protection() {
    }

    static boolean active() {
        return active;
    }

    // ------------------------------------------------------------------ decision et message
    static boolean allowed(Player player, Level level, BlockPos pos, ClanApi.Action action) {
        if (player instanceof FakePlayer) {
            return ClanAgents.canAct(level, ClanAgents.origin(), pos);
        }
        return ClanApi.canModify(player, level, pos, action);
    }

    /** Refus a la barre d'action (une fois par seconde au plus). */
    static void warn(Player player, Level level, BlockPos pos) {
        warn(player, level, pos, null);
    }

    /**
     * Refus a la barre d'action (une fois par seconde au plus). 1.0.3-beta (lot 16) : le message dit pourquoi ({@link ClanAccess#denial} :
     * geste ferme a la categorie du visiteur, zone privee, ou le message d'avant).
     */
    static void warn(Player player, Level level, BlockPos pos, @javax.annotation.Nullable ClanApi.Action action) {
        if (!(player instanceof ServerPlayer sp) || player instanceof FakePlayer) {
            return;
        }
        long now = System.currentTimeMillis();
        Long last = WARNED.get(sp.getUUID());
        if (last != null && now - last < 1000) {
            return;
        }
        WARNED.put(sp.getUUID(), now);
        sp.displayClientMessage(ClanAccess.denial(sp.getServer(), level, sp.getUUID(), pos, action).copy().withStyle(ChatFormatting.RED), true);
    }

    private static boolean check(Player player, Level level, BlockPos pos, ClanApi.Action action) {
        if (level.isClientSide || !active || allowed(player, level, pos, action)) {
            return true;
        }
        warn(player, level, pos, action);
        return false;
    }

    /**
     * 1.0.3-beta (lot 16) : plante cultivee (cultures vanilla et du mod, tiges, baies, verrues, cacao) : la casser, la recolter ou la
     * cliquer est un geste de culture (groupe « Cultures » des permissions des visiteurs ; pour un membre : permission de batir, comme avant).
     */
    public static boolean crop(BlockState state) {
        Block b = state.getBlock();
        return b instanceof CropBlock || b instanceof StemBlock || b instanceof AttachedStemBlock || b instanceof SweetBerryBushBlock
               || b instanceof NetherWartBlock || b instanceof CocoaBlock || b instanceof fr.pioneers.farm.crop.CropTopBlock
               || state.is(BlockTags.CROPS);
    }

    /** 1.0.3-beta (lot 16) : objet de culture (graine ou plant a semer, engrais, houe) : l'utiliser est un geste de culture. */
    public static boolean farmTool(ItemStack held) {
        if (held.isEmpty()) {
            return false;
        }
        if (held.is(Items.BONE_MEAL) || held.canPerformAction(ToolActions.HOE_TILL) || held.getItem() instanceof IPlantable) {
            return true;
        }
        return held.getItem() instanceof BlockItem bi && crop(bi.getBlock().defaultBlockState());
    }

    /** Sorte d'action d'un clic droit sur ce bloc. */
    public static ClanApi.Action classify(Level level, BlockPos pos, BlockState state) {
        if (state.is(OPEN_USE)) {
            return ClanApi.Action.INTERACT;
        }
        //1.0.3-beta (lot 16) : cultures (le mod les recolte au clic droit) et terre labouree : geste de culture
        if (crop(state) || state.getBlock() instanceof FarmBlock) {
            return ClanApi.Action.FARMLAND;
        }
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(pos) : null;
        if (be != null && Pioneers.MODID.equals(ForgeRegistries.BLOCKS.getKey(state.getBlock()).getNamespace())) {
            return ClanApi.Action.MACHINE;
        }
        if (state.is(STORAGE) || be instanceof Container || be != null && be.getCapability(ForgeCapabilities.ITEM_HANDLER).isPresent()) {
            return ClanApi.Action.CONTAINER;
        }
        //tout autre clic droit (comparateur, gateau, composteur, lit...) change le monde : il demande la permission de batir
        return ClanApi.Action.PLACE;
    }

    /** Sorte d'action sur une entite (null : rien a proteger, monstres et joueurs). */
    public static ClanApi.Action classify(Entity e, boolean attack) {
        if (e instanceof HangingEntity || e instanceof ArmorStand) {
            return attack ? ClanApi.Action.BREAK : ClanApi.Action.CONTAINER;
        }
        if (e instanceof Boat || e instanceof AbstractMinecart || e instanceof Saddleable
            || !(e instanceof LivingEntity) && Pioneers.MODID.equals(ForgeRegistries.ENTITY_TYPES.getKey(e.getType()).getNamespace())) {
            return ClanApi.Action.VEHICLE;
        }
        if (e instanceof Animal || e instanceof WaterAnimal || e instanceof AbstractGolem || e instanceof Allay) {
            return attack ? ClanApi.Action.ATTACK_ANIMAL : ClanApi.Action.ENTITY;
        }
        return null;
    }

    // ------------------------------------------------------------------ gestes des joueurs
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBreak(BlockEvent.BreakEvent event) {
        ClanApi.Action a = crop(event.getState()) ? ClanApi.Action.FARMLAND : ClanApi.Action.BREAK;
        if (!check(event.getPlayer(), (Level) event.getLevel(), event.getPos(), a)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onPlace(BlockEvent.EntityPlaceEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide || !active) {
            return;
        }
        Entity e = event.getEntity();
        if (e instanceof Player p) {
            boolean ok = check(p, level, event.getPos(), ClanApi.Action.PLACE);
            if (ok && event instanceof BlockEvent.EntityMultiPlaceEvent multi) {
                for (var snap : multi.getReplacedBlockSnapshots()) {
                    if (!check(p, level, snap.getPos(), ClanApi.Action.PLACE)) {
                        ok = false;
                        break;
                    }
                }
            }
            if (!ok) {
                event.setCanceled(true);
            }
        } else if (e != null && ClanAccess.claimed(level, event.getPos())) {
            //creature qui pose un bloc (enderman, golem de neige) : jamais dans un claim
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide || !active) {
            return;
        }
        Player p = event.getEntity();
        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack held = event.getItemStack();
        ClanApi.Action used = classify(level, pos, state);
        boolean block = allowed(p, level, pos, used);
        if (!block) {
            event.setUseBlock(Event.Result.DENY);
        }
        //Drapeau de siege : SiegeFlagItem verifie lui-meme guerre, creneau et chunk (le bloc clique reste protege, sans avertissement)
        if (Siege.isSiegeFlag(held)) {
            return;
        }
        //le Sceau de fonction fait ses propres controles (permission SEALS, territoire du clan)
        if (!held.isEmpty() && !held.isEdible() && !held.is(SealItems.FUNCTION_SEAL.get())) {
            BlockPos next = pos.relative(event.getFace() == null ? Direction.UP : event.getFace());
            //1.0.3-beta (lot 16) : semer, mettre de l'engrais, labourer = geste de culture
            ClanApi.Action put = farmTool(held) ? ClanApi.Action.FARMLAND : ClanApi.Action.PLACE;
            boolean item = allowed(p, level, pos, put) && allowed(p, level, next, put);
            if (!item) {
                event.setUseItem(Event.Result.DENY);
                if (block) {
                    used = put;
                }
            } else if (held.is(IGNITERS)) {
                //feu allume par un joueur autorise : il ne sera pas eteint par la coupure de la propagation
                Long2LongOpenHashMap lit = LIT.computeIfAbsent(level, k -> new Long2LongOpenHashMap());
                lit.put(next.asLong(), level.getGameTime() + 5);
                lit.put(pos.asLong(), level.getGameTime() + 5);
            }
            block &= item;
        }
        if (!block) {
            warn(p, level, pos, used);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        ClanApi.Action a = crop(event.getLevel().getBlockState(event.getPos())) ? ClanApi.Action.FARMLAND : ClanApi.Action.BREAK;
        if (!check(event.getEntity(), event.getLevel(), event.getPos(), a)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        ClanApi.Action a = classify(event.getTarget(), false);
        if (a != null && !check(event.getEntity(), event.getLevel(), event.getTarget().blockPosition(), a)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        ClanApi.Action a = classify(event.getTarget(), false);
        if (a != null && !check(event.getEntity(), event.getLevel(), event.getTarget().blockPosition(), a)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttack(AttackEntityEvent event) {
        ClanApi.Action a = classify(event.getTarget(), true);
        if (a != null && !check(event.getEntity(), event.getEntity().level(), event.getTarget().blockPosition(), a)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onMount(EntityMountEvent event) {
        if (event.isMounting() && event.getEntityMounting() instanceof Player p && event.getEntityBeingMounted() != null
            && !check(p, event.getLevel(), event.getEntityBeingMounted().blockPosition(), ClanApi.Action.VEHICLE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBucket(FillBucketEvent event) {
        if (!(event.getTarget() instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (!check(event.getEntity(), event.getLevel(), pos, ClanApi.Action.BREAK)
            || !check(event.getEntity(), event.getLevel(), pos.relative(hit.getDirection()), ClanApi.Action.PLACE)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onToolModification(BlockEvent.BlockToolModificationEvent event) {
        Player p = event.getPlayer();
        //1.0.3-beta (lot 16) : labour = geste de culture (pour un membre : batir, comme avant)
        ClanApi.Action a = event.getToolAction() == ToolActions.HOE_TILL ? ClanApi.Action.FARMLAND : ClanApi.Action.PLACE;
        if (p != null && event.getLevel() instanceof Level level && !check(p, level, event.getPos(), a)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onBonemeal(BonemealEvent event) {
        if (event.getEntity() != null && !check(event.getEntity(), event.getLevel(), event.getPos(), ClanApi.Action.FARMLAND)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onProjectile(ProjectileImpactEvent event) {
        Projectile pr = event.getProjectile();
        if (pr.level().isClientSide || !active || !(pr.getOwner() instanceof Player p)) {
            return;
        }
        HitResult hit = event.getRayTraceResult();
        boolean ok = true;
        if (hit instanceof BlockHitResult b && b.getType() == HitResult.Type.BLOCK) {
            BlockState s = pr.level().getBlockState(b.getBlockPos());
            ok = allowed(p, pr.level(), b.getBlockPos(), s.is(OPEN_USE) ? ClanApi.Action.INTERACT : ClanApi.Action.BREAK);
        } else if (hit instanceof EntityHitResult e) {
            ClanApi.Action a = classify(e.getEntity(), true);
            ok = a == null || allowed(p, pr.level(), e.getEntity().blockPosition(), a);
        }
        if (!ok) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ pour tous : monde et creatures
    @SubscribeEvent
    public static void onFarmland(BlockEvent.FarmlandTrampleEvent event) {
        if (active && event.getLevel() instanceof Level level && !level.isClientSide && ClanAccess.claimed(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onExplosion(ExplosionEvent.Detonate event) {
        Level level = event.getLevel();
        if (level.isClientSide || !active) {
            return;
        }
        event.getAffectedBlocks().removeIf(pos -> ClanAccess.claimed(level, pos));
        event.getAffectedEntities().removeIf(e -> (e instanceof HangingEntity || e instanceof ArmorStand) && ClanAccess.claimed(level, e.blockPosition()));
    }

    @SubscribeEvent
    public static void onMobGriefing(EntityMobGriefingEvent event) {
        Entity e = event.getEntity();
        if (e != null && !(e instanceof Player) && active && !e.level().isClientSide && ClanAccess.claimed(e.level(), e.blockPosition())) {
            event.setResult(Event.Result.DENY);
        }
    }

    @SubscribeEvent
    public static void onLivingDestroy(LivingDestroyBlockEvent event) {
        Level level = event.getEntity().level();
        if (active && !level.isClientSide && ClanAccess.claimed(level, event.getPos())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide || !active) {
            return;
        }
        BlockPos piston = event.getPos();
        if (!ClanApi.canTransfer(level, piston, event.getFaceOffsetPos())) {
            event.setCanceled(true);
            return;
        }
        PistonStructureResolver s = event.getStructureHelper();
        if (s == null || !s.resolve()) {
            return;
        }
        Direction move = s.getPushDirection();
        for (BlockPos p : s.getToPush()) {
            if (!ClanApi.canTransfer(level, piston, p) || !ClanApi.canTransfer(level, piston, p.relative(move))) {
                event.setCanceled(true);
                return;
            }
        }
        for (BlockPos p : s.getToDestroy()) {
            if (!ClanApi.canTransfer(level, piston, p)) {
                event.setCanceled(true);
                return;
            }
        }
    }

    @SubscribeEvent
    public static void onFluidPlace(BlockEvent.FluidPlaceBlockEvent event) {
        if (!(event.getLevel() instanceof Level level) || level.isClientSide || !active) {
            return;
        }
        BlockPos pos = event.getPos();
        boolean fire = event.getNewState().getBlock() instanceof BaseFireBlock;
        if (!ClanApi.canTransfer(level, event.getLiquidPos(), pos) || fire && ClanAccess.claimed(level, pos)) {
            event.setNewState(event.getOriginalState());
        }
    }

    /** Un bloc vient de changer : feu ou fluide qui coule a une frontiere ? On note la position, relue en fin de tick. */
    @SubscribeEvent
    public static void onNotify(BlockEvent.NeighborNotifyEvent event) {
        if (!active || !(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        BlockState state = event.getState();
        BlockPos pos = event.getPos();
        if (state.getBlock() instanceof BaseFireBlock) {
            QUEUE.computeIfAbsent(level, k -> new LongOpenHashSet()).add(pos.asLong());
            return;
        }
        FluidState f = state.getFluidState();
        if (!f.isEmpty() && edge(pos)) {
            //fluide qui coule, ou source posee par un distributeur voisin (seau vide d'un autre cote de la frontiere)
            QUEUE.computeIfAbsent(level, k -> new LongOpenHashSet()).add(pos.asLong());
        }
    }

    private static boolean edge(BlockPos pos) {
        int x = pos.getX() & 15, z = pos.getZ() & 15;
        return x == 0 || x == 15 || z == 0 || z == 15;
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        LongOpenHashSet q = QUEUE.get(level);
        if (q == null || q.isEmpty()) {
            return;
        }
        long[] all = q.toLongArray();
        q.clear();
        Long2LongOpenHashMap lit = LIT.get(level);
        long now = level.getGameTime();
        if (lit != null) {
            lit.long2LongEntrySet().removeIf(e -> e.getLongValue() < now);
        }
        for (long l : all) {
            BlockPos pos = BlockPos.of(l);
            if (!level.isLoaded(pos)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof BaseFireBlock) {
                if ((lit == null || !lit.containsKey(l)) && fireForbidden(level, pos)) {
                    level.removeBlock(pos, false);
                }
            } else {
                FluidState f = state.getFluidState();
                if (!f.isEmpty() && !f.isSource() && crossed(level, pos, f)) {
                    //fluide venu de l'autre cote d'une frontiere : il s'arrete (sans prevenir les voisins, pour ne pas relancer l'ecoulement)
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS);
                } else if (f.isSource() && state.getBlock() instanceof LiquidBlock && dispensed(level, pos)) {
                    //source posee par un distributeur de l'autre cote d'une frontiere refusee : retiree
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
        }
    }

    /** Feu dans un claim, ou au bord d'un claim (il brulerait le bloc voisin de l'autre cote). */
    static boolean fireForbidden(Level level, BlockPos pos) {
        if (ClanAccess.claimed(level, pos)) {
            return true;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if ((n.getX() >> 4 != pos.getX() >> 4 || n.getZ() >> 4 != pos.getZ() >> 4) && ClanAccess.claimed(level, n)) {
                return true;
            }
        }
        return false;
    }

    /** Un distributeur d'un autre chunk, tourne vers cette case, a-t-il pu y vider un seau a travers une frontiere refusee ? */
    static boolean dispensed(Level level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockPos n = pos.relative(d);
            if (n.getX() >> 4 == pos.getX() >> 4 && n.getZ() >> 4 == pos.getZ() >> 4) {
                continue;
            }
            BlockState s = level.getBlockState(n);
            if (s.getBlock() instanceof DispenserBlock && s.getValue(DispenserBlock.FACING) == d.getOpposite() && !ClanApi.canTransfer(level, n, pos)) {
                return true;
            }
        }
        return false;
    }

    /** Ce fluide qui coule n'est nourri que par-dessus une frontiere refusee ? */
    static boolean crossed(Level level, BlockPos pos, FluidState f) {
        boolean fed = false;
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = pos.relative(d);
            FluidState o = level.getFluidState(n);
            if (o.isEmpty() || !o.getType().isSame(f.getType())) {
                continue;
            }
            if (ChunkPos.asLong(n.getX() >> 4, n.getZ() >> 4) == ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)
                || ClanApi.canTransfer(level, n, pos)) {
                return false;
            }
            fed = true;
        }
        return fed;
    }

    // ------------------------------------------------------------------ cache
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && --recheck <= 0) {
            recheck = 20;
            active = ClanApi.enabled() && ClaimIndex.any();
            if (WARNED.size() > 256) {
                WARNED.clear();
            }
        }
    }

    /** Index modifie : la protection se rallume tout de suite (sans attendre la seconde suivante). */
    static void refresh() {
        active = ClanApi.enabled() && ClaimIndex.any();
    }
}
