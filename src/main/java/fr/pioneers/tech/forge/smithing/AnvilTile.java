package fr.pioneers.tech.forge.smithing;

import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.forge.client.SmithingClient;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.ForgeTile;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.SmithingGrid;
import fr.pioneers.tech.forge.SmithingRecipe;
import fr.pioneers.core.network.PacketTileAction;
import fr.pioneers.tech.forge.PiForge;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Enclume (spec forge primaire par. 1.10-11 et par. 2) : garde la piece posee ({@code heated_metal}, forge en cours dans son NBT {@code work}).
 * <ul>
 * <li>metal chauffe en main, enclume vide : on pose la piece (refusee si le metal demande une enclume plus dure, {@link ForgeMetals#anvilTier}) ;</li>
 * <li>lingot chauffe brut sur une piece du meme metal, tous deux a temperature : soudure (+ 1 lingot, {@link SmithingMoves#weld}) ;</li>
 * <li>marteau (tag {@code pioneers:tools/hammers}) : ouvre l'ecran du minijeu (client) ;</li>
 * <li>main vide : on reprend la piece.</li>
 * </ul>
 * Les coups arrivent par {@link PacketTileAction} (portee et droits verifies par {@code PacketGuard}) ; le serveur seul decide
 * ({@link #handleAction}). Code d'une action : bits 0-3 = {@link #OP_SELECT} ou un coup de {@link SmithingMoves} ; coup : x bits 4-7,
 * z bits 8-11, direction bits 12-13 ; choix de forme : rang dans {@link SmithingRecipe#forMetal} a partir du bit 4. NBT de la tuile :
 * {@code piece}.
 */
public class AnvilTile extends ForgeTile implements PacketTileAction.Handler {

    public static final int OP_SELECT = 4;
    public static final TagKey<Item> HAMMERS = TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath("pioneers", "tools/hammers"));
    /** Chaleur perdue par coup (degres C, divisee par la racine cubique des lingots). */
    public static final float HIT_LOSS_C = 1F; //1.1.1 : 3 avant
    /** Usure du marteau : un point tous les {@code HAMMER_WEAR} coups, et un a la fin de chaque piece. */
    public static final int HAMMER_WEAR = 16;
    /** Ticks au moins entre deux coups d'un meme joueur. */
    public static final int MIN_HIT_TICKS = 2;
    /** La forme reste reservee a celui qui travaille la piece pendant 10 s. */
    public static final int WORKER_TICKS = 200;

    protected ItemStack piece = ItemStack.EMPTY;
    private final Map<UUID, Long> lastHit = new HashMap<>();
    @Nullable
    private UUID worker;
    private long workerTick;
    // Client : grille et recette a dessiner, relues a chaque mise a jour (aucune allocation par image)
    @Nullable
    private SmithingGrid shown;
    @Nullable
    private ResourceLocation shownRecipe;
    private int version;
    private ItemStack shownShape = ItemStack.EMPTY;

    public AnvilTile(BlockPos pos, BlockState state) {
        super(PiForge.ANVIL_TILE.get(), pos, state);
    }

    /** Enclume : 0 pierre, 1 bronze, 2 fer. */
    public int tier() {
        return getBlockState().getBlock() instanceof AnvilBlock anvil ? anvil.tier : 0;
    }

    public ItemStack piece() {
        return piece;
    }

    /** Lingots de matiere dans la piece (au moins 1). */
    public static int ingots(ItemStack piece) {
        return Math.max(1, Math.min(SmithingMoves.MAX_INGOTS, HeatedMetal.amount(piece) / ForgeMetals.INGOT_MB));
    }

    /** Grille de la piece : la forge en cours, sinon le bloc de depart. */
    public static SmithingGrid gridOf(ItemStack piece) {
        SmithingGrid g = HeatedMetal.work(piece);
        return g != null ? g : SmithingGrid.initial(ingots(piece));
    }

    /** Budget de coups du bonus "Forge main" pour cette recette et ce nombre de lingots. */
    public static int budget(SmithingRecipe recipe, int ingots) {
        int n = Math.max(ingots, recipe.ingots());
        //Le plan de reference coute quelques millisecondes : une fois par recette, forme et nombre de lingots
        return BUDGETS.computeIfAbsent(recipe.getId() + "#" + n + "#" + recipe.target().hashCode(),
              k -> SmithingMoves.budget(SmithingGrid.initial(n), recipe.target()));
    }

    private static final Map<String, Integer> BUDGETS = new ConcurrentHashMap<>();

    public static boolean isHammer(ItemStack stack) {
        return !stack.isEmpty() && stack.is(HAMMERS);
    }

    public static int encodeMove(int op, int x, int z, int dir) {
        return op & 15 | (x & 15) << 4 | (z & 15) << 8 | (dir & 3) << 12;
    }

    public static int encodeSelect(int index) {
        return OP_SELECT | index << 4;
    }

    // ------------------------------------------------------------------
    // Interaction a la main
    // ------------------------------------------------------------------

    @Override
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        ItemStack held = player.getItemInHand(hand);
        boolean client = level == null || level.isClientSide;
        if (isHammer(held)) {
            if (piece.isEmpty()) {
                if (!client) {
                    player.displayClientMessage(Component.translatable("message.pioneers.anvil.empty"), true);
                }
            } else if (client) {
                DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> SmithingClient.open(worldPosition));
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (HeatedMetal.is(held)) {
            if (!client) {
                if (piece.isEmpty()) {
                    place(player, held);
                } else {
                    weld(player, held);
                }
            }
            return InteractionResult.sidedSuccess(client);
        }
        if (held.isEmpty() && !piece.isEmpty()) {
            if (!client) {
                HeatedMetal.settle(piece, level);
                player.setItemInHand(hand, piece.copy());
                piece = ItemStack.EMPTY;
                sound(SoundEvents.CHAIN_PLACE, 0.6F, 1.1F);
                sync();
            }
            return InteractionResult.sidedSuccess(client);
        }
        return InteractionResult.PASS;
    }

    private void place(Player player, ItemStack held) {
        String metal = HeatedMetal.metal(held);
        if (ForgeMetals.anvilTier(metal) > tier()) {
            player.displayClientMessage(Component.translatable("message.pioneers.anvil.too_soft",
                  Component.translatable("block.pioneers." + anvilId(ForgeMetals.anvilTier(metal))), Metals.name(metal))
                  .withStyle(ChatFormatting.RED), true);
            return;
        }
        if (HeatedMetal.amount(held) > SmithingMoves.MAX_INGOTS * ForgeMetals.INGOT_MB) {
            player.displayClientMessage(Component.translatable("message.pioneers.anvil.weld_full"), true);
            return;
        }
        piece = held.copyWithCount(1);
        held.shrink(1);
        claim(player);
        sound(SoundEvents.ANVIL_PLACE, 0.4F, 1.4F);
        sync();
    }

    private static String anvilId(int tier) {
        //1.0.2-beta : plus d'enclume en pierre (lot 2) ni en bronze (lot 5) : l'enclume en fer forge tout metal forgeable a la main
        return "iron_anvil";
    }

    /** Soudure d'un lingot chauffe brut sur la piece. */
    private void weld(Player player, ItemStack held) {
        String metal = HeatedMetal.metal(piece);
        if (!HeatedMetal.shape(piece).isEmpty() || HeatedMetal.hasWork(held) || !HeatedMetal.shape(held).isEmpty()
              || !metal.equals(HeatedMetal.metal(held))) {
            player.displayClientMessage(Component.translatable("message.pioneers.anvil.occupied"), true);
            return;
        }
        if (!HeatedMetal.isWorkable(piece, level) || !HeatedMetal.isWorkable(held, level)) {
            player.displayClientMessage(Component.translatable("message.pioneers.anvil.weld_cold"), true);
            return;
        }
        //Toute la matiere du metal tenu (une loupe peut depasser un lingot) ; jamais plus de SmithingMoves.MAX_INGOTS (8) lingots sur une piece
        int m0 = HeatedMetal.amount(piece), m1 = HeatedMetal.amount(held);
        if (m1 <= 0 || m0 + m1 > SmithingMoves.MAX_INGOTS * ForgeMetals.INGOT_MB) {
            player.displayClientMessage(Component.translatable("message.pioneers.anvil.weld_full"), true);
            return;
        }
        //Temperature de la piece soudee : moyenne ponderee des deux
        float t0 = HeatedMetal.temperatureNow(piece, level), t1 = HeatedMetal.temperatureNow(held, level);
        if (HeatedMetal.hasWork(piece)) {
            //Un lingot entier = 32 pixels sur sa bande ; le reste (moins d'un lingot) est rendu en pepites a la fin
            SmithingGrid grid = HeatedMetal.work(piece);
            for (int i = m0 / ForgeMetals.INGOT_MB; i < (m0 + m1) / ForgeMetals.INGOT_MB; i++) {
                SmithingMoves.weld(grid, i);
            }
            HeatedMetal.setWork(piece, grid, HeatedMetal.workRecipe(piece));
        }
        HeatedMetal.setAmount(piece, m0 + m1);
        HeatedMetal.heat(piece, (t0 * m0 + t1 * m1) / (m0 + m1), level);
        held.shrink(1);
        sound(SoundEvents.ANVIL_LAND, 0.35F, 1.3F);
        if (level instanceof ServerLevel server) {
            server.sendParticles(ParticleTypes.LAVA, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, 4, 0.15,
                  0.05, 0.15, 0);
            BronzeEvents.award(server, worldPosition, "smith_weld");
            finishIfDone(player);
        }
        sync();
    }

    // ------------------------------------------------------------------
    // Coups (serveur)
    // ------------------------------------------------------------------

    @Override
    public void handleAction(ServerPlayer player, int action) {
        InteractionHand hand = hammerHand(player);
        if (!(level instanceof ServerLevel server) || piece.isEmpty() || !HeatedMetal.shape(piece).isEmpty() || hand == null) {
            return;
        }
        //Cadence : 2 ticks au moins entre deux coups d'un meme joueur (paquets en rafale)
        long now = server.getGameTime();
        Long last = lastHit.get(player.getUUID());
        if (last != null && now - last < MIN_HIT_TICKS) {
            return;
        }
        lastHit.put(player.getUUID(), now);
        int op = action & 15;
        String metal = HeatedMetal.metal(piece);
        if (op == OP_SELECT) {
            //Seul celui qui travaille la piece choisit la forme (ou n'importe qui s'il l'a laissee depuis 10 s)
            if (worker != null && !worker.equals(player.getUUID()) && now - workerTick < WORKER_TICKS) {
                return;
            }
            List<SmithingRecipe> list = SmithingRecipe.forMetal(server, metal, tier());
            int index = action >>> 4;
            if (index < 0 || index >= list.size()) {
                return;
            }
            SmithingRecipe recipe = list.get(index);
            HeatedMetal.setWork(piece, gridOf(piece), recipe.getId());
            claim(player);
            finishIfDone(player);
            sync();
            return;
        }
        SmithingRecipe recipe = SmithingRecipe.byId(server, HeatedMetal.workRecipe(piece));
        if (recipe == null || !recipe.acceptsMetal(metal) || recipe.anvilTierFor(metal) > tier()) {
            return;
        }
        if (!HeatedMetal.isWorkable(piece, server)) {
            sound(SoundEvents.STONE_HIT, 0.5F, 0.6F);
            return;
        }
        int x = action >> 4 & 15, z = action >> 8 & 15, dir = action >> 12 & 3;
        SmithingGrid grid = HeatedMetal.work(piece);
        if (grid == null || !SmithingMoves.apply(grid, op, x, z, dir)) {
            return;
        }
        HeatedMetal.setWork(piece, grid, recipe.getId());
        claim(player);
        HeatedMetal.countHit(piece, op == SmithingMoves.OP_CUT ? 1 : 0);
        //1.9 : skill Forge et metallurgie (perte par coup, refroidissement plus lent)
        HeatedMetal.loseHeat(piece, server, (float) (HIT_LOSS_C * fr.pioneers.core.profile.PioneerApi.factor(player, "smithing.hit_loss"))
              / (float) Math.cbrt(ingots(piece)));
        HeatedMetal.setTauFactor(piece, (float) (1 - fr.pioneers.core.profile.PioneerApi.modifier(player, "smithing.cooling")));
        int hits = HeatedMetal.workTag(piece).getInt(HeatedMetal.HITS);
        if (hits % HAMMER_WEAR == 0) {
            wearHammer(player, hand);
        }
        float pitch = 1.25F + server.random.nextFloat() * 0.35F;
        switch (op) {
            case SmithingMoves.OP_DRAW -> sound(SoundEvents.ANVIL_USE, 0.3F, pitch - 0.35F);
            case SmithingMoves.OP_CUT -> sound(SoundEvents.ANVIL_LAND, 0.18F, 1.9F);
            default -> sound(SoundEvents.ANVIL_USE, 0.3F, pitch);
        }
        Direction facing = getBlockState().hasProperty(AnvilBlock.FACING) ? getBlockState().getValue(AnvilBlock.FACING) : Direction.SOUTH;
        double lx = AnvilGeometry.X0 + (x + 0.5) * AnvilGeometry.CELL, lz = AnvilGeometry.Z0 + (z + 0.5) * AnvilGeometry.CELL;
        double px = worldPosition.getX() + AnvilGeometry.worldX(facing, lx, lz) / 16.0;
        double pz = worldPosition.getZ() + AnvilGeometry.worldZ(facing, lx, lz) / 16.0;
        server.sendParticles(ParticleTypes.SMALL_FLAME, px, worldPosition.getY() + AnvilGeometry.TOP / 16.0 + 0.05, pz, 2, 0.03, 0.02, 0.03, 0.03);
        finishIfDone(player);
        //Coup ordinaire : envoi groupe au client (au plus toutes les 2 ticks, serverTick)
        setChanged();
        markClient();
    }

    @Override
    public void serverTick(ServerLevel level) {
        flushClient(level, MIN_HIT_TICKS);
        if (!lastHit.isEmpty() && level.getGameTime() % 200 == 0) {
            long now = level.getGameTime();
            lastHit.values().removeIf(t -> now - t > 200);
        }
    }

    /** Main qui tient le marteau (principale d'abord) ; null sans marteau. */
    @Nullable
    private static InteractionHand hammerHand(Player player) {
        return isHammer(player.getMainHandItem()) ? InteractionHand.MAIN_HAND
              : isHammer(player.getOffhandItem()) ? InteractionHand.OFF_HAND : null;
    }

    /** Ce joueur travaille la piece (seul lui choisit la forme pendant {@link #WORKER_TICKS}). */
    private void claim(Player player) {
        worker = player.getUUID();
        workerTick = level == null ? 0 : level.getGameTime();
    }

    private void wearHammer(Player player) {
        InteractionHand hand = hammerHand(player);
        if (hand != null) {
            wearHammer(player, hand);
        }
    }

    private void wearHammer(Player player, InteractionHand hand) {
        if (!player.getAbilities().instabuild) {
            player.getItemInHand(hand).hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        }
    }

    /** Fin de forge si la forme est atteinte : piece chauffee a tremper, chute rendue en pepites, bonus "Forge main". */
    private void finishIfDone(Player player) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        SmithingRecipe recipe = SmithingRecipe.byId(server, HeatedMetal.workRecipe(piece));
        SmithingGrid grid = HeatedMetal.work(piece);
        if (recipe == null || grid == null || !recipe.matches(grid)) {
            return;
        }
        CompoundTag work = HeatedMetal.workTag(piece);
        int hits = work.getInt(HeatedMetal.HITS), scrap = work.getInt(HeatedMetal.SCRAP);
        String metal = HeatedMetal.metal(piece);
        int budget = budget(recipe, ingots(piece));
        //Tout ce qui n'est pas dans la forme (chute coupee, reste d'une loupe soudee) revient en pepites
        int spare = HeatedMetal.amount(piece) - recipe.pixels() * ForgeMetals.INGOT_MB / ForgeMetals.PIXELS_PER_INGOT;
        ItemStack out = recipe.forge(piece);
        if (out.isEmpty()) {
            return;
        }
        boolean hand = budget >= 0 && hits <= budget;
        if (hand) {
            ItemStack part = HeatedMetal.shape(out);
            HeatedMetal.setShape(out, HandForged.mark(part));
            HeatedMetal.setHandForged(out, true);
        }
        piece = out;
        if (player instanceof net.minecraft.server.level.ServerPlayer sp) {
            fr.pioneers.core.profile.PioneerApi.gain(sp, "smithing.forge"); //1.9 : piece forgee a la main
        }
        //Chute et reste : en pepites (16 mB)
        ItemStack nuggets = ForgeMetals.nugget(metal, Math.max(0, spare) / ForgeMetals.NUGGET_MB);
        if (!nuggets.isEmpty() && !player.getInventory().add(nuggets)) {
            server.addFreshEntity(new ItemEntity(server, worldPosition.getX() + 0.5, worldPosition.getY() + 1, worldPosition.getZ() + 0.5, nuggets));
        }
        wearHammer(player);
        sound(SoundEvents.ANVIL_USE, 0.7F, 0.75F);
        sound(SoundEvents.SMITHING_TABLE_USE, 0.8F, 1.0F);
        server.sendParticles(ParticleTypes.LAVA, worldPosition.getX() + 0.5, worldPosition.getY() + 0.8, worldPosition.getZ() + 0.5, 6, 0.2, 0.05,
              0.2, 0);
        player.displayClientMessage(Component.translatable(hand ? "message.pioneers.anvil.done_hand" : "message.pioneers.anvil.done",
              HeatedMetal.shape(out).getHoverName(), hits, budget).withStyle(hand ? ChatFormatting.GOLD : ChatFormatting.GREEN), true);
        BronzeEvents.award(server, worldPosition, "smith_part");
        if (hand) {
            BronzeEvents.award(server, worldPosition, "smith_hand_forged");
            if (scrap == 0) {
                BronzeEvents.award(server, worldPosition, "smith_clean");
            }
        }
    }

    private void sound(SoundEvent event, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, event, SoundSource.BLOCKS, volume, pitch);
        }
    }

    /** Sauvegarde et envoi immediat au client (piece, grille). */
    private void sync() {
        setChanged();
        if (level != null) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    @Override
    public void dropContents(Consumer<ItemStack> drop) {
        super.dropContents(drop);
        if (!piece.isEmpty()) {
            drop.accept(piece.copy());
            piece = ItemStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // Client
    // ------------------------------------------------------------------

    /** Grille a dessiner (client) ; null sans piece. */
    @Nullable
    public SmithingGrid shownGrid() {
        return shown;
    }

    /** Piece finie a dessiner (client, relue a chaque mise a jour) ; vide sinon. */
    public ItemStack shownShape() {
        return shownShape;
    }

    @Nullable
    public ResourceLocation shownRecipe() {
        return shownRecipe;
    }

    /** Augmente a chaque mise a jour recue (l'ecran relit alors ses caches). */
    public int version() {
        return version;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.put("piece", piece.save(new CompoundTag()));
    }

    @Override
    protected void readClient(CompoundTag tag) {
        piece = ItemStack.of(tag.getCompound("piece"));
        refreshShown();
    }

    private void refreshShown() {
        shownShape = piece.isEmpty() ? ItemStack.EMPTY : HeatedMetal.shape(piece);
        shown = piece.isEmpty() || !shownShape.isEmpty() ? null : gridOf(piece);
        shownRecipe = piece.isEmpty() ? null : HeatedMetal.workRecipe(piece);
        version++;
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("piece", piece.save(new CompoundTag()));
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        piece = ItemStack.of(tag.getCompound("piece"));
        refreshShown();
    }
}
