package fr.pioneers.logistics.caravan;

import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.herd.PiHerd;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.memory.MemoryModuleType;
import net.minecraft.world.entity.ai.memory.WalkTarget;
import net.minecraft.world.entity.animal.camel.Camel;
import net.minecraft.world.entity.animal.horse.AbstractChestedHorse;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.network.NetworkHooks;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 10) : la VIE DES CARAVANES (serveur).
 * <ul>
 *     <li>gestes sur une bete de bat ({@link #interact}) : bat (pose, cisailles), feuille de route (depart ; Maj : arret), betes tenues en
 *     laisse attachees EN FILE derriere elle (laisse vanilla d'un animal a l'autre), sacoches (Maj + clic main vide), foin et ble au
 *     chameau ;</li>
 *     <li>la TETE ({@link #tickHead}) : relit sa file chaque seconde (suiveurs = betes tenues en laisse par la precedente), garde son ticket
 *     de chunks ({@link CaravanTickets}), va au relais vise par sauts de {@code HOP} blocs (navigation vanilla ; chameau : cible de marche
 *     de son cerveau), attend un suiveur trop loin, ramene un suiveur qui va casser sa laisse, saute un obstacle apres {@code STUCK_SECONDS}
 *     sans progres (secours annonce) ; 1.0.4-beta (lot 29) : marche au vrai pas ({@link CaravanRules#navSpeed}, point de marche garde,
 *     detours essayes tout de suite, suiveurs au pas de la tete : {@link #walk}, {@link #follow}) ; au relais : rations, rabats ouverts, transferts ({@link #serve}), depart a la condition du relais ;
 *     s'arrete si le relais vise a disparu ;</li>
 *     <li>vues envoyees aux clients ({@link PacketCaravan}) ; bat lache a la mort de la bete (avec son contenu).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Caravans {

    private Caravans() {
    }

    // ------------------------------------------------------------------ droits
    /** Le joueur peut-il modifier ce qui appartient a {@code owner} (lui-meme, son clan, un operateur en creatif ; sans proprietaire : oui) ? */
    public static boolean mayEdit(@Nullable Player player, @Nullable UUID owner) {
        if (owner == null || player == null || player.getUUID().equals(owner)) {
            return true;
        }
        if (player.isCreative() && player.hasPermissions(2)) {
            return true;
        }
        MinecraftServer server = player.getServer();
        if (server == null || !ClanApi.enabled()) {
            return false;
        }
        Clan a = ClanApi.clanOf(server, player.getUUID()), b = ClanApi.clanOf(server, owner);
        return a != null && b != null && a.id.equals(b.id);
    }

    // ------------------------------------------------------------------ evenements
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void join(EntityJoinLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || !PiCaravan.carrier(e) || e instanceof Camel || !(e instanceof AbstractHorse h)) {
            return;
        }
        if (h.goalSelector.getAvailableGoals().stream().noneMatch(g -> g.getGoal() instanceof HoldGoal)) {
            h.goalSelector.addGoal(0, new HoldGoal(h));
        }
    }

    /** But bloquant (cheval, ane, mulet) : pendant le voyage, rien d'autre ne bouge la bete (la caravane la conduit). */
    static final class HoldGoal extends Goal {

        private final Mob mob;

        HoldGoal(Mob mob) {
            this.mob = mob;
            setFlags(EnumSet.of(Flag.MOVE, Flag.JUMP));
        }

        @Override
        public boolean canUse() {
            CaravanData d = PiCaravan.get(mob);
            return d != null && moving(d);
        }

        @Override
        public boolean canContinueToUse() {
            return canUse();
        }
    }

    /** La bete fait-elle partie d'une caravane qui roule ou s'arrete a un relais ? */
    static boolean moving(CaravanData d) {
        return d.role != CaravanData.NONE && (d.state == CaravanData.TRAVEL || d.state == CaravanData.STOP);
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void interact(PlayerInteractEvent.EntityInteract event) {
        Entity t = event.getTarget();
        if (!PiCaravan.carrier(t) || !(t instanceof Mob mob)) {
            return;
        }
        CaravanData d = PiCaravan.get(t);
        if (d == null) {
            return;
        }
        Player p = event.getEntity();
        ItemStack held = p.getItemInHand(event.getHand());
        boolean client = t.level().isClientSide;
        String action = null;
        if (held.getItem() instanceof PackSaddleItem) {
            action = "equip";
        } else if (held.is(Items.SHEARS) && d.pack) {
            action = "unequip";
        } else if (held.getItem() instanceof RouteSheetItem) {
            action = p.isSecondaryUseActive() ? "stop" : "assign";
        } else if (d.pack && (held.is(Items.SADDLE) || held.is(Items.CHEST))) {
            action = "refuse";
        } else if (t instanceof Camel c && !(t instanceof DromedaryEntity) && (held.is(Items.HAY_BLOCK) || held.is(Items.WHEAT)) && !c.isBaby()
                   && c.canFallInLove()) {
            action = "feed";
        } else if (held.isEmpty() && p.isSecondaryUseActive() && d.pack) {
            action = "open";
        } else if (!led(p, mob).isEmpty()) {
            action = "tie";
        }
        if (action == null) {
            return;
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(client));
        if (client) {
            return;
        }
        switch (action) {
            case "equip" -> equip(p, mob, d, held);
            case "unequip" -> {
                unequip(p, mob, d);
                held.hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(event.getHand()));
            }
            case "stop" -> stop(p, mob, d);
            case "assign" -> assign(p, mob, d, held);
            case "refuse" -> p.displayClientMessage(Component.translatable("caravan.pioneers.msg.no_saddle").withStyle(ChatFormatting.GOLD), true);
            case "feed" -> {
                ((Camel) t).setInLove(p);
                if (!p.getAbilities().instabuild) {
                    held.shrink(1);
                }
            }
            case "open" -> {
                if (p instanceof ServerPlayer sp) {
                    NetworkHooks.openScreen(sp, new SimpleMenuProvider((id, inv, pl) -> new PackMenu(id, inv, mob, d),
                          Component.translatable("gui.pioneers.pack.title", mob.getDisplayName())), buf -> {
                        buf.writeVarInt(mob.getId());
                        buf.writeVarInt(d.slots);
                    });
                }
            }
            default -> tie(p, mob, led(p, mob));
        }
    }

    // ------------------------------------------------------------------ bat
    /** Peut-on poser un bat sur cette bete ? (adulte ; cheval, ane, mulet apprivoises, sans selle ni coffre ; chameau sans selle) */
    public static boolean canWear(Mob mob) {
        if (mob.isBaby() || !(mob instanceof AbstractHorse h)) {
            return false;
        }
        if (!h.isTamed() || h.isSaddled()) {
            return false;
        }
        return !(mob instanceof AbstractChestedHorse c) || !c.hasChest();
    }

    /** Pose le bat (contenu de l'objet repris) ; faux et message sinon. */
    public static boolean equip(@Nullable Player p, Mob mob, CaravanData d, ItemStack stack) {
        if (d.pack) {
            return false;
        }
        if (!canWear(mob)) {
            tell(p, Component.translatable("caravan.pioneers.msg.pack_no").withStyle(ChatFormatting.GOLD));
            return false;
        }
        int slots = CaravanRules.slots(PiHerd.packKg(mob));
        var contents = PackSaddleItem.contents(stack);
        for (int i = slots; i < contents.getSlots(); i++) {
            if (!contents.getStackInSlot(i).isEmpty()) {
                tell(p, Component.translatable("caravan.pioneers.msg.pack_heavy").withStyle(ChatFormatting.RED));
                return false;
            }
        }
        for (int i = 0; i < d.items.getSlots(); i++) {
            d.items.setStackInSlot(i, contents.getStackInSlot(i));
        }
        d.pack = true;
        d.slots = slots;
        if (p == null || !p.getAbilities().instabuild) {
            stack.shrink(1);
        }
        mob.level().playSound(null, mob, SoundEvents.ARMOR_EQUIP_LEATHER, SoundSource.NEUTRAL, 1, 0.9F);
        tell(p, Component.translatable("caravan.pioneers.msg.pack_on", slots).withStyle(ChatFormatting.GREEN));
        sync(mob, d, true);
        return true;
    }

    /** Retire le bat : l'objet (avec le contenu des sacoches) va au joueur ou tombe. */
    public static ItemStack unequip(@Nullable Player p, Mob mob, CaravanData d) {
        ItemStack it = new ItemStack(PiCaravan.PACK_SADDLE.get());
        PackSaddleItem.store(it, d.items);
        for (int i = 0; i < d.items.getSlots(); i++) {
            d.items.setStackInSlot(i, ItemStack.EMPTY);
        }
        d.pack = false;
        d.slots = 0;
        if (p != null && !p.addItem(it.copy())) {
            mob.spawnAtLocation(it, 1);
        } else if (p == null) {
            mob.spawnAtLocation(it, 1);
        }
        mob.level().playSound(null, mob, SoundEvents.SHEEP_SHEAR, SoundSource.NEUTRAL, 1, 1.1F);
        tell(p, Component.translatable("caravan.pioneers.msg.pack_off"));
        sync(mob, d, true);
        return it;
    }

    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        CaravanData d = PiCaravan.carrier(e) ? PiCaravan.get(e) : null;
        if (d == null || !d.pack || e.level().isClientSide) {
            return;
        }
        ItemStack it = new ItemStack(PiCaravan.PACK_SADDLE.get());
        PackSaddleItem.store(it, d.items);
        d.pack = false;
        event.getDrops().add(new ItemEntity(e.level(), e.getX(), e.getY() + 0.5, e.getZ(), it));
        if (d.role == CaravanData.HEAD && e.level().getServer() != null) {
            CaravanTickets.release(e.level().getServer(), e.getUUID());
        }
    }

    // ------------------------------------------------------------------ file (longe)
    /** Betes de bat que le joueur tient en laisse (sauf la cible), de la plus proche de la cible a la plus loin. */
    static List<Mob> led(Player p, Mob target) {
        List<Mob> out = p.level().getEntitiesOfClass(Mob.class, p.getBoundingBox().inflate(12),
              m -> m != target && m.isLeashed() && m.getLeashHolder() == p && PiCaravan.carrier(m));
        if (target.isLeashed() && target.getLeashHolder() == p) {
            return List.of();
        }
        out.sort(Comparator.comparingDouble(m -> m.distanceToSqr(target)));
        return out;
    }

    /** La file derriere {@code head} : chaque suiveur est tenu en laisse par le precedent. */
    public static List<Mob> chain(Mob head) {
        List<Mob> out = new ArrayList<>();
        Mob cur = head;
        for (int k = 0; k < GeneratedCaravan.MAX_FOLLOWERS; k++) {
            Mob from = cur;
            List<Mob> c = head.level().getEntitiesOfClass(Mob.class, from.getBoundingBox().inflate(12),
                  m -> m != head && !out.contains(m) && m.isLeashed() && m.getLeashHolder() == from && PiCaravan.carrier(m));
            if (c.isEmpty()) {
                break;
            }
            c.sort(Comparator.comparingDouble(m -> m.distanceToSqr(from)));
            cur = c.get(0);
            out.add(cur);
        }
        return out;
    }

    /** Attache les betes tenues par le joueur en file derriere la cible (au bout de sa file). */
    public static int tie(Player p, Mob target, List<Mob> led) {
        List<Mob> file = chain(target);
        Mob tail = file.isEmpty() ? target : file.get(file.size() - 1);
        int n = file.size(), tied = 0;
        for (Mob m : led) {
            if (n >= GeneratedCaravan.MAX_FOLLOWERS) {
                tell(p, Component.translatable("caravan.pioneers.msg.file_full").withStyle(ChatFormatting.GOLD));
                break;
            }
            m.setLeashedTo(tail, true);
            tail = m;
            n++;
            tied++;
        }
        if (tied > 0) {
            tell(p, Component.translatable("caravan.pioneers.msg.tied", tied).withStyle(ChatFormatting.GREEN));
            target.level().playSound(null, target, SoundEvents.LEASH_KNOT_PLACE, SoundSource.NEUTRAL, 1, 1);
        }
        return tied;
    }

    // ------------------------------------------------------------------ depart, arret
    /** La bete prend l'itineraire de la feuille et part (tete de caravane). Faux et message sinon. */
    public static boolean assign(Player p, Mob head, CaravanData d, ItemStack sheet) {
        RouteSheetItem.Route r = RouteSheetItem.Route.of(sheet);
        if (d.owner != null && d.role == CaravanData.HEAD && !mayEdit(p, d.owner)) {
            tell(p, Component.translatable("caravan.pioneers.msg.not_yours", d.ownerName).withStyle(ChatFormatting.RED));
            return false;
        }
        if (d.role == CaravanData.FOLLOWER) {
            return false;
        }
        if (r.stops().size() < 2) {
            tell(p, Component.translatable("caravan.pioneers.msg.no_route").withStyle(ChatFormatting.GOLD));
            return false;
        }
        String dim = head.level().dimension().location().toString();
        if (!r.dim().equals(dim)) {
            tell(p, Component.translatable("caravan.pioneers.msg.other_dim").withStyle(ChatFormatting.RED));
            return false;
        }
        for (int i = 0; i < r.stops().size(); i++) {
            int leg = r.leg(i);
            if (leg > GeneratedCaravan.MAX_LEG) {
                tell(p, Component.translatable("caravan.pioneers.msg.too_far", leg).withStyle(ChatFormatting.RED));
                return false;
            }
            BlockPos s = r.stops().get(i);
            if (head.level().isLoaded(s) && head.level().getBlockEntity(s) instanceof RelayTile relay && !mayEdit(p, relay.relay().owner)) {
                tell(p, Component.translatable("caravan.pioneers.msg.not_yours", relay.relay().ownerName).withStyle(ChatFormatting.RED));
                return false;
            }
        }
        if (head.isLeashed() && head.getLeashHolder() == p) {
            head.dropLeash(true, !p.getAbilities().instabuild);
        }
        d.clearRoute();
        d.stops.addAll(r.stops());
        d.names.addAll(r.names());
        d.loop = r.loop();
        d.dim = dim;
        d.role = CaravanData.HEAD;
        d.head = null;
        d.owner = p.getUUID();
        d.ownerName = p.getGameProfile().getName();
        int best = 0;
        double bd = Double.MAX_VALUE;
        for (int i = 0; i < d.stops.size(); i++) {
            double dd = d.stops.get(i).distToCenterSqr(head.position());
            if (dd < bd) {
                bd = dd;
                best = i;
            }
        }
        d.next = best;
        d.state = CaravanData.TRAVEL;
        d.best = Double.MAX_VALUE;
        d.stuck = 0;
        d.detour = 0;
        d.hopTarget = null;
        d.rescues = 0;
        if (head.level() instanceof ServerLevel level) {
            refresh(level, head, d);
            PiFarmDevices.award(level, head.blockPosition(), "caravan");
        }
        tell(p, Component.translatable("caravan.pioneers.msg.started", 1 + d.file.size(), d.stops.size()).withStyle(ChatFormatting.GREEN));
        return true;
    }

    /** Arret demande par le joueur (sur la tete ou un suiveur). */
    public static void stop(Player p, Mob mob, CaravanData d) {
        Mob head = mob;
        CaravanData hd = d;
        if (d.role == CaravanData.FOLLOWER && d.head != null && mob.level() instanceof ServerLevel level
            && level.getEntity(d.head) instanceof Mob h && PiCaravan.get(h) != null) {
            head = h;
            hd = PiCaravan.get(h);
        }
        if (!mayEdit(p, hd.owner)) {
            tell(p, Component.translatable("caravan.pioneers.msg.not_yours", hd.ownerName).withStyle(ChatFormatting.RED));
            return;
        }
        dissolve(head, hd);
        tell(p, Component.translatable("caravan.pioneers.msg.stopped"));
    }

    /** La caravane s'arrete et se defait (les laisses restent : la file se reforme au prochain depart). */
    static void dissolve(Mob head, CaravanData d) {
        for (Mob f : d.file) {
            CaravanData fd = PiCaravan.get(f);
            if (fd != null) {
                fd.role = CaravanData.NONE;
                fd.state = CaravanData.IDLE;
                fd.open = false;
                sync(f, fd, true);
            }
            stopNav(f);
        }
        d.clearRoute();
        d.role = CaravanData.NONE;
        d.file.clear();
        stopNav(head);
        if (head.level().getServer() != null) {
            CaravanTickets.release(head.level().getServer(), head.getUUID());
        }
        sync(head, d, true);
    }

    /** Arret force (relais disparu) : la caravane garde son itineraire, le proprietaire et les joueurs proches sont prevenus. */
    public static void halt(Mob head, CaravanData d, int reason, String what) {
        d.state = CaravanData.STOPPED;
        d.reason = reason;
        d.open = false;
        stopNav(head);
        for (Mob f : d.file) {
            CaravanData fd = PiCaravan.get(f);
            if (fd != null) {
                fd.state = CaravanData.STOPPED;
                fd.open = false;
                sync(f, fd, true);
            }
            stopNav(f);
        }
        if (head.level().getServer() != null) {
            CaravanTickets.release(head.level().getServer(), head.getUUID());
        }
        announce(head, d, Component.translatable("caravan.pioneers.msg.relay_gone", what).withStyle(ChatFormatting.RED));
        sync(head, d, true);
    }

    // ------------------------------------------------------------------ tete
    @SubscribeEvent
    public static void tick(LivingEvent.LivingTickEvent event) {
        LivingEntity e = event.getEntity();
        if (e.level().isClientSide || !(e instanceof Mob mob) || !PiCaravan.carrier(e)) {
            return;
        }
        CaravanData d = PiCaravan.get(e);
        if (d == null) {
            return;
        }
        ServerLevel level = (ServerLevel) e.level();
        if (d.role == CaravanData.HEAD && d.active()) {
            tickHead(level, mob, d);
        } else if (d.role == CaravanData.FOLLOWER) {
            if (mob instanceof Camel camel && moving(d)) {
                camel.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
                if (camel.isCamelSitting()) {
                    camel.standUpInstantly();
                }
            }
            if (d.state == CaravanData.TRAVEL) {
                follow(mob, d);
            }
            if ((e.tickCount + e.getId()) % 40 == 0 && (!mob.isLeashed() || mob.getLeashHolder() == null)) {
                d.role = CaravanData.NONE;
                d.state = CaravanData.IDLE;
                d.open = false;
            }
        }
        if ((d.pack || d.role != CaravanData.NONE) && (e.tickCount + e.getId()) % 10 == 0) {
            sync(mob, d, false);
        }
    }

    /** Un tick de la tete d'une caravane (public : GameTests). */
    public static void tickHead(ServerLevel level, Mob head, CaravanData d) {
        if (d.stops.isEmpty()) {
            dissolve(head, d);
            return;
        }
        d.next = Math.max(0, Math.min(d.stops.size() - 1, d.next));
        long now = level.getGameTime();
        if ((now + head.getId()) % 20 == 0 || d.file.isEmpty() && head.tickCount < 5) {
            refresh(level, head, d);
        }
        BlockPos target = d.stops.get(d.next);
        RelayTile relay = null;
        if (level.isLoaded(target)) {
            if (!(level.getBlockEntity(target) instanceof RelayTile r)) {
                halt(head, d, CaravanData.REASON_RELAY_GONE, d.next < d.names.size() ? d.names.get(d.next) : target.toShortString());
                return;
            }
            relay = r;
        }
        Vec3 stand = relay != null ? relay.standPoint() : Vec3.atBottomCenterOf(target);
        double dist = Math.hypot(stand.x - head.getX(), stand.z - head.getZ());
        d.dist = (int) Math.round(dist);
        List<Mob> members = members(head, d);
        if (d.state == CaravanData.TRAVEL) {
            hunger(head, d, members);
            d.waiting = followers(level, head, d);
            if (dist <= GeneratedCaravan.ARRIVE) {
                d.state = CaravanData.STOP;
                d.stopTicks = 0;
                stopNav(head);
                return;
            }
            if (d.waiting) {
                stopNav(head);
                d.hopTarget = null;
                return;
            }
            d.pace = pace(level, head, members);
            walk(level, head, d, stand, d.pace, now);
            if ((now + head.getId()) % 20 == 0) {
                //1.0.4-beta (lot 29) : progres = s'etre rapproche d'au moins un demi-bloc depuis le meilleur point ; 5 s sans progres : autre
                //direction ; secours (teleportation) seulement apres STUCK_SECONDS sans aucun progres
                if (dist < d.best - 0.5) {
                    d.best = dist;
                    d.stuck = 0;
                    d.detour = 0;
                } else {
                    d.stuck += 20;
                    if (d.stuck % 100 == 0) {
                        d.detour++;
                        d.hopTarget = null;
                    }
                    if (d.stuck >= GeneratedCaravan.STUCK_SECONDS * 20) {
                        rescue(level, head, d, stand);
                    }
                }
            }
        } else if (d.state == CaravanData.STOP) {
            stopNav(head);
            d.stopTicks++;
            if (relay == null) {
                return;
            }
            RelayUnit u = relay.relay();
            if (d.stopTicks == 1) {
                boolean desert = desert(level, head.blockPosition());
                for (Mob m : members) {
                    CaravanData md = PiCaravan.get(m);
                    boolean camel = PiCaravan.camel(m);
                    float full = CaravanRules.fedBlocks(camel, desert);
                    if (md != null && md.fed < full / 2 && relay.takeRation(camel)) {
                        md.fed = full;
                    }
                    if (md != null) {
                        md.open = md.pack && u.order != CaravanRules.PASS;
                        sync(m, md, true);
                    }
                }
                level.playSound(null, head, head instanceof Camel ? SoundEvents.CAMEL_EAT : SoundEvents.HORSE_EAT, SoundSource.NEUTRAL, 0.8F, 1);
            }
            if (d.stopTicks % GeneratedCaravan.TRANSFER_TICKS == 0) {
                serve(relay, members);
            }
            boolean full = true, empty = true;
            int[] filter = u.filterIds();
            for (Mob m : members) {
                CaravanData md = PiCaravan.get(m);
                if (md == null || !md.pack) {
                    continue;
                }
                full &= md.full();
                for (int i = 0; i < md.slots; i++) {
                    ItemStack s = md.items.getStackInSlot(i);
                    if (!s.isEmpty() && (u.order == CaravanRules.LOAD || u.order == CaravanRules.PASS
                                         || CaravanRules.unloads(u.order, CaravanRules.keeps(filter, BuiltInRegistries.ITEM.getId(s.getItem()))))) {
                        empty = false;
                    }
                }
            }
            if (CaravanRules.ready(u.cond, d.stopTicks, u.wait, full, empty)) {
                int[] n = CaravanRules.next(d.stops.size(), d.next, d.dir, d.loop);
                d.next = n[0];
                d.dir = n[1];
                d.state = CaravanData.TRAVEL;
                d.best = Double.MAX_VALUE;
                d.stuck = 0;
                d.detour = 0;
                d.hopTarget = null;
                d.rescues = 0;
                for (Mob m : members) {
                    CaravanData md = PiCaravan.get(m);
                    if (md != null) {
                        md.open = false;
                        sync(m, md, true);
                    }
                }
                level.playSound(null, head, head instanceof Camel ? SoundEvents.CAMEL_AMBIENT : SoundEvents.HORSE_AMBIENT, SoundSource.NEUTRAL, 1, 1);
            }
        }
    }

    /** Tete + suiveurs. */
    static List<Mob> members(Mob head, CaravanData d) {
        List<Mob> out = new ArrayList<>(d.file.size() + 1);
        out.add(head);
        for (Mob m : d.file) {
            if (m.isAlive()) {
                out.add(m);
            }
        }
        return out;
    }

    /** Chaque seconde : la file relue, l'etat recopie aux suiveurs, le ticket de chunks suit la tete. */
    static void refresh(ServerLevel level, Mob head, CaravanData d) {
        d.file = chain(head);
        String nextName = d.next < d.names.size() ? d.names.get(d.next) : "";
        for (int i = 0; i < d.file.size(); i++) {
            CaravanData fd = PiCaravan.get(d.file.get(i));
            if (fd != null) {
                fd.role = CaravanData.FOLLOWER;
                fd.index = i + 1;
                fd.head = head.getUUID();
                fd.state = d.state;
                fd.reason = d.reason;
                fd.ownerName = d.ownerName;
                fd.followerNext = nextName;
                fd.dist = d.dist;
                fd.waiting = d.waiting;
                fd.pace = d.pace;
            }
        }
        if (d.active()) {
            CaravanTickets.hold(level, head.getUUID(), head.chunkPosition());
        }
    }

    /** Suiveurs : un suiveur pres de casser sa laisse est ramene derriere son meneur (secours) ; vrai si la tete doit attendre. */
    static boolean followers(ServerLevel level, Mob head, CaravanData d) {
        boolean wait = false;
        for (Mob f : d.file) {
            Entity h = f.getLeashHolder();
            if (h == null || !f.isAlive()) {
                continue;
            }
            double gap = f.distanceTo(h);
            if (gap > GeneratedCaravan.RESCUE_GAP) {
                Vec3 dir = f.position().subtract(h.position()).multiply(1, 0, 1);
                dir = dir.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : dir.normalize();
                Vec3 p = h.position().add(dir.scale(2.5));
                f.teleportTo(p.x, surface(level, p, h.getY()), p.z);
                f.getNavigation().stop();
            } else if (gap > GeneratedCaravan.WAIT_GAP) {
                wait = true;
            }
        }
        return wait;
    }

    /** Hauteur de marche en (x, z) (surface sans feuillage) ; {@code fallback} si le chunk n'est pas charge. */
    static double surface(ServerLevel level, Vec3 p, double fallback) {
        BlockPos b = BlockPos.containing(p.x, fallback, p.z);
        if (!level.isLoaded(b)) {
            return fallback;
        }
        return level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, b.getX(), b.getZ());
    }

    /** Prochain point de marche : le point d'attache s'il est a moins de {@code HOP}, sinon {@code HOP} blocs dans sa direction (detour). */
    static Vec3 hop(ServerLevel level, Mob head, Vec3 stand, int detour) {
        double dx = stand.x - head.getX(), dz = stand.z - head.getZ(), len = Math.hypot(dx, dz);
        if (len <= GeneratedCaravan.HOP && detour == 0) {
            return stand;
        }
        double[] turns = {0, 40, -40, 75, -75};
        double a = Math.atan2(dz, dx) + Math.toRadians(turns[detour % turns.length]);
        double step = Math.min(len, GeneratedCaravan.HOP);
        Vec3 p = new Vec3(head.getX() + Math.cos(a) * step, head.getY(), head.getZ() + Math.sin(a) * step);
        return new Vec3(p.x, surface(level, p, head.getY()), p.z);
    }

    /**
     * 1.0.4-beta (lot 29) : pas de la caravane en blocs / s : {@code PACE}, ralenti par la charge et la faim (le facteur de navigation de
     * chaque bete en est tire par {@link CaravanRules#navSpeed}, loi quadratique du jeu ; avant : facteur lineaire, d'ou une marche a
     * 0,25 bloc / s puis le secours).
     */
    static double pace(ServerLevel level, Mob head, List<Mob> members) {
        float fill = 0;
        int packs = 0;
        boolean hungry = false, camels = true;
        for (Mob m : members) {
            CaravanData md = PiCaravan.get(m);
            if (md == null) {
                continue;
            }
            hungry |= md.hungry();
            camels &= PiCaravan.camel(m);
            if (md.pack) {
                fill += md.fill();
                packs++;
            }
        }
        float k = CaravanRules.speedFactor(packs == 0 ? 0 : fill / packs, hungry, camels && desert(level, head.blockPosition()));
        return GeneratedCaravan.PACE * k;
    }

    /** Facteur de navigation de cette bete pour marcher a {@code pace} blocs / s. */
    static double navSpeed(Mob m, double pace) {
        return CaravanRules.navSpeed(pace, m.getAttributeValue(Attributes.MOVEMENT_SPEED));
    }

    /** Intervalle maximal entre deux calculs du chemin de la tete (ticks) : on garde le meme point de marche entre-temps. */
    static final int REPATH_TICKS = 60;

    /**
     * 1.0.4-beta (lot 29) : marche de la tete vers {@code stand}. Un POINT DE MARCHE ({@code HOP} blocs vers l'etape) est garde tant qu'il
     * n'est pas atteint (3 blocs), que la navigation suit son chemin et au plus {@link #REPATH_TICKS} ticks (avant : nouveau point et
     * nouveau chemin toutes les 10 ticks, la bete repartait sans cesse). Au choix d'un point, les directions de detour sont essayees tout de
     * suite (un chemin doit exister, dans un chunk charge) ; le facteur de navigation est tenu a chaque tick (loi quadratique).
     */
    static void walk(ServerLevel level, Mob head, CaravanData d, Vec3 stand, double pace, long now) {
        if (head instanceof AbstractHorse h && h.isEating()) {
            h.setEating(false);
        }
        double speed = navSpeed(head, pace);
        Vec3 t = d.hopTarget;
        boolean camel = head instanceof Camel;
        boolean renew = t == null || now - d.hopAt >= REPATH_TICKS || Math.hypot(t.x - head.getX(), t.z - head.getZ()) < 3
                        || !camel && head.getNavigation().isDone();
        if (!renew) {
            if (!camel) {
                head.getNavigation().setSpeedModifier(speed);
            }
            return;
        }
        Vec3 chosen = null;
        Path path = null;
        for (int k = 0; k < 5 && chosen == null; k++) {
            Vec3 c = hop(level, head, stand, d.detour + k);
            BlockPos b = BlockPos.containing(c);
            if (!level.isLoaded(b)) {
                continue;
            }
            Path p = head.getNavigation().createPath(b, 1);
            if (p != null && (p.canReach() || p.getEndNode() != null && p.getEndNode().distanceTo(b) < 4)) {
                chosen = c;
                path = p;
            }
        }
        if (chosen == null) {
            chosen = hop(level, head, stand, d.detour);          //aucun chemin net : la navigation vanilla fait au mieux (chemin partiel)
        }
        d.hopTarget = chosen;
        d.hopAt = now;
        if (camel) {
            steer(head, chosen, speed);
        } else if (path != null) {
            head.getNavigation().moveTo(path, speed);
        } else {
            head.getNavigation().moveTo(chosen.x, chosen.y, chosen.z, speed);
        }
    }

    /**
     * 1.0.4-beta (lot 29) : un suiveur en route. La laisse vanilla le mene a 2 blocs de son meneur (chemin refait a chaque tick) au facteur 1
     * ({@code followLeashSpeed}) : un cheval moyen y marchait a 2,2 blocs / s, un chameau a 0,35 ; tires par la laisse au-dela de 6 blocs, ils
     * avancaient par a-coups jusqu'au secours. Ici, au debut de chaque tick (avant l'IA), le facteur du chemin en cours est remis au pas de la
     * caravane, un peu plus vite s'il est en retard ({@link CaravanRules#followerPace}).
     */
    static void follow(Mob f, CaravanData d) {
        Entity holder = f.getLeashHolder();
        if (holder == null || !f.isAlive()) {
            return;
        }
        if (f instanceof AbstractHorse h && h.isEating()) {
            h.setEating(false);
        }
        double pace = CaravanRules.followerPace(d.pace > 0 ? d.pace : GeneratedCaravan.PACE, f.distanceTo(holder));
        f.getNavigation().setSpeedModifier(navSpeed(f, pace));
    }

    static void steer(Mob m, Vec3 p, double speed) {
        if (m instanceof Camel camel) {
            if (camel.isCamelSitting()) {
                camel.standUpInstantly();
            }
            camel.getBrain().setMemory(MemoryModuleType.WALK_TARGET, new WalkTarget(p, (float) speed, 1));
        } else {
            m.getNavigation().moveTo(p.x, p.y, p.z, speed);
        }
    }

    static void stopNav(Mob m) {
        m.getNavigation().stop();
        if (m instanceof Camel camel) {
            camel.getBrain().eraseMemory(MemoryModuleType.WALK_TARGET);
        }
    }

    /** Biome chaud et sec (desert, savane seche, badlands) ? */
    public static boolean desert(Level level, BlockPos pos) {
        Biome b = level.getBiome(pos).value();
        return b.getBaseTemperature() >= GeneratedCaravan.DESERT_TEMP && !b.hasPrecipitation();
    }

    /** Faim : les blocs parcourus par la tete usent la ration de chaque bete. */
    static void hunger(Mob head, CaravanData d, List<Mob> members) {
        double moved = Double.isNaN(d.lastX) ? 0 : Math.hypot(head.getX() - d.lastX, head.getZ() - d.lastZ);
        d.lastX = head.getX();
        d.lastZ = head.getZ();
        if (moved <= 0 || moved > 4) {
            return;
        }
        for (Mob m : members) {
            CaravanData md = PiCaravan.get(m);
            if (md != null && md.fed > 0) {
                md.fed = (float) Math.max(0, md.fed - moved);
            }
        }
    }

    /** Secours : bloquee depuis {@code STUCK_SECONDS}, la caravane saute l'obstacle ({@code HOP} blocs vers l'etape) ; annonce. */
    static void rescue(ServerLevel level, Mob head, CaravanData d, Vec3 stand) {
        Vec3 p = hop(level, head, stand, 0);
        double y = surface(level, p, head.getY());
        head.teleportTo(p.x, y, p.z);
        Vec3 back = new Vec3(head.getX() - stand.x, 0, head.getZ() - stand.z);
        back = back.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : back.normalize();
        int i = 1;
        for (Mob f : d.file) {
            Vec3 q = p.add(back.scale(2.5 * i++));
            f.teleportTo(q.x, surface(level, q, y), q.z);
            f.getNavigation().stop();
        }
        d.stuck = 0;
        d.detour = 0;
        d.hopTarget = null;
        d.rescues++;
        d.best = Math.hypot(stand.x - p.x, stand.z - p.z);
        announce(head, d, Component.translatable("caravan.pioneers.msg.rescue", head.blockPosition().toShortString()).withStyle(ChatFormatting.GOLD));
    }

    /** Message au proprietaire (s'il est en ligne) et aux joueurs a moins de 32 blocs. */
    static void announce(Mob head, CaravanData d, Component msg) {
        MinecraftServer server = head.level().getServer();
        if (server == null) {
            return;
        }
        ServerPlayer owner = d.owner == null ? null : server.getPlayerList().getPlayer(d.owner);
        if (owner != null) {
            owner.sendSystemMessage(msg);
        }
        for (Player p : head.level().players()) {
            if (p != owner && p.distanceToSqr(head) < 32 * 32) {
                p.sendSystemMessage(msg);
            }
        }
    }

    // ------------------------------------------------------------------ transferts au relais
    /**
     * Un pas de transfert au relais (au plus {@code TRANSFER_ITEMS} objets par bete) selon l'ordre et le filtre : decharger (bat -> conteneur),
     * charger (conteneur -> bat), les deux (decharge ce que le filtre ne retient pas, charge ce qu'il retient). Vrai si quelque chose a bouge.
     */
    public static boolean serve(RelayTile relay, List<Mob> members) {
        IItemHandler c = relay.container();
        RelayUnit u = relay.relay();
        if (c == null || u.order == CaravanRules.PASS) {
            return false;
        }
        int[] filter = u.filterIds();
        boolean moved = false;
        for (Mob m : members) {
            CaravanData d = PiCaravan.get(m);
            if (d == null || !d.pack || !fr.pioneers.society.clan.ClanApi.canTransfer(m.level(), m.blockPosition(), relay.getBlockPos())) {
                continue;
            }
            int budget = GeneratedCaravan.TRANSFER_ITEMS, before = budget;
            if (u.order == CaravanRules.UNLOAD || u.order == CaravanRules.BOTH) {
                for (int i = 0; i < d.slots && budget > 0; i++) {
                    ItemStack s = d.items.getStackInSlot(i);
                    if (s.isEmpty() || !CaravanRules.unloads(u.order, CaravanRules.keeps(filter, BuiltInRegistries.ITEM.getId(s.getItem())))) {
                        continue;
                    }
                    ItemStack part = d.items.extractItem(i, budget, true);
                    ItemStack rest = ItemHandlerHelper.insertItemStacked(c, part, false);
                    int n = part.getCount() - rest.getCount();
                    if (n > 0) {
                        d.items.extractItem(i, n, false);
                        budget -= n;
                    }
                }
            }
            if (u.order == CaravanRules.LOAD || u.order == CaravanRules.BOTH) {
                for (int j = 0; j < c.getSlots() && budget > 0; j++) {
                    ItemStack s = c.getStackInSlot(j);
                    if (s.isEmpty() || s.getItem() instanceof PackSaddleItem
                        || !CaravanRules.loads(u.order, CaravanRules.keeps(filter, BuiltInRegistries.ITEM.getId(s.getItem())))) {
                        continue;
                    }
                    ItemStack part = c.extractItem(j, budget, true);
                    if (part.isEmpty()) {
                        continue;
                    }
                    int can = part.getCount() - insertPack(d, part, true).getCount();
                    if (can <= 0) {
                        continue;
                    }
                    ItemStack got = c.extractItem(j, can, false);
                    ItemStack rest = insertPack(d, got, false);
                    if (!rest.isEmpty()) {
                        ItemHandlerHelper.insertItemStacked(c, rest, false);
                    }
                    budget -= got.getCount() - rest.getCount();
                }
            }
            if (budget < before) {
                moved = true;
                m.level().playSound(null, m, u.order == CaravanRules.UNLOAD ? SoundEvents.BUNDLE_REMOVE_ONE : SoundEvents.BUNDLE_INSERT,
                      SoundSource.NEUTRAL, 0.6F, 0.9F + m.getRandom().nextFloat() * 0.2F);
            }
        }
        return moved;
    }

    /** Range dans les cases utilisables du bat ; rend le reste. */
    static ItemStack insertPack(CaravanData d, ItemStack stack, boolean simulate) {
        ItemStack rest = stack;
        for (int i = 0; i < d.slots && !rest.isEmpty(); i++) {
            rest = d.items.insertItem(i, rest, simulate);
        }
        return rest;
    }

    /** Caravanes a l'arret devant ce relais (pour son interface). */
    public static int stoppedAt(RelayTile relay) {
        if (relay.getLevel() == null) {
            return 0;
        }
        Vec3 s = relay.standPoint();
        return relay.getLevel().getEntitiesOfClass(Mob.class, new AABB(s, s).inflate(GeneratedCaravan.ARRIVE + 1), m -> {
            CaravanData d = PiCaravan.carrier(m) ? PiCaravan.get(m) : null;
            return d != null && d.role == CaravanData.HEAD && d.state == CaravanData.STOP;
        }).size();
    }

    // ------------------------------------------------------------------ vues des clients, serveur
    static void sync(Mob m, CaravanData d, boolean force) {
        if (m.level().isClientSide) {
            return;
        }
        CompoundTag v = d.view(d.dist);
        long h = v.hashCode();
        if (!force && h == d.lastView) {
            return;
        }
        d.lastView = h;
        fr.pioneers.core.network.PiNetwork.CHANNEL.send(PacketDistributor.TRACKING_ENTITY.with(() -> m), new PacketCaravan(m.getId(), v));
    }

    @SubscribeEvent
    public static void track(PlayerEvent.StartTracking event) {
        Entity e = event.getTarget();
        CaravanData d = PiCaravan.carrier(e) ? PiCaravan.get(e) : null;
        if (d != null && (d.pack || d.role != CaravanData.NONE) && event.getEntity() instanceof ServerPlayer sp) {
            fr.pioneers.core.network.PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new PacketCaravan(e.getId(), d.view(d.dist)));
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        Entity e = event.getEntity();
        if (event.getLevel().isClientSide || !PiCaravan.carrier(e) || e.getRemovalReason() == null || !e.getRemovalReason().shouldDestroy()) {
            return;
        }
        if (e.level().getServer() != null && CaravanTickets.holds(e.getUUID())) {
            CaravanTickets.release(e.level().getServer(), e.getUUID());
        }
    }

    private static int serverTicks;

    @SubscribeEvent
    public static void serverTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        if (serverTicks++ == 0) {
            CaravanTickets.restore(server);
        }
        if (serverTicks % 600 == 0) {
            CaravanTickets.sweep(server);
        }
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        CaravanTickets.clear();
        serverTicks = 0;
    }

    static void tell(@Nullable Player p, Component msg) {
        if (p != null) {
            p.displayClientMessage(msg, true);
        }
    }
}
