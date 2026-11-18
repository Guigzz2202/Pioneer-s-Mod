package fr.pioneers.logistics.transport.logistic;

import fr.pioneers.core.Directions;
import fr.pioneers.core.Texts;
import fr.pioneers.core.Wrenches;
import fr.pioneers.core.block.Covers;
import fr.pioneers.core.block.Waterlogging;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §1) : cable logistique (6 niveaux, opaque ou transparent) et cable supraconducteur. Chaque face vaut NONE, CABLE (vers un cable
 * logistique ; le supraconducteur seulement vers lui-meme) ou PLUG (fiche vers un bloc dont la tile expose, sur l'une quelconque de ses 6
 * faces, au moins une capacite d'un type permis au niveau). Les anciens cables (blocs herites de la 1.10, convertis au chargement par
 * {@code LegacyMigration}) n'exposent rien : jamais de fiche vers eux.
 * <p>
 * Pas de tile sur un cable nu (spec §7.1) : {@link #newBlockEntity} n'en cree que si une face est une fiche ou si le cable est transparent ;
 * une coupure a la cle cree la tile qui la memorise. Aucun tick par bloc : le reseau ({@link LogisticNetworks}) fait tout.
 * <p>
 * Interactions : cle = coupe / retablit le cote vise (reconnexion facile comme avant) ; Maj + cle, main vide ou objet non posable = diagramme
 * ({@link #DIAGRAM}, branche par D2b ; en D2a : resume du reseau dans la barre d'action). 1.0.4-beta (lot 27) : bloc plein en main = cache
 * ({@link Covers} : etat {@link Covers#COVERED}, tuile creee, forme pleine ; casser rend d'abord la cache).
 */
public class LogisticCableBlock extends Block implements EntityBlock, SimpleWaterloggedBlock {

    /** Branchement d'une face (3 valeurs : 3^6 x 2 = 1 458 etats par bloc). */
    public enum Link implements StringRepresentable {
        NONE, CABLE, PLUG;

        public boolean linked() {
            return this != NONE;
        }

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Ouverture du diagramme (D2b : {@link DiagramService#open} ; D2a : resume dans la barre d'action, garde : {@code statusMessage}). */
    public interface DiagramHook {

        /** @param focus face visee (Maj + clic sur une fiche : diagramme ouvert sur le point de cette machine), ou null */
        void open(ServerPlayer player, BlockPos cable, @Nullable Direction focus);
    }

    public static DiagramHook DIAGRAM = DiagramService::open;

    public static final Map<Direction, EnumProperty<Link>> PROPERTIES = new EnumMap<>(Direction.class);

    static {
        for (Direction direction : Directions.ALL) {
            PROPERTIES.put(direction, EnumProperty.create(direction.getName(), Link.class));
        }
    }

    /** Geometrie (identique aux anciens cables, modeles du generateur alignes) : coeur 5..11, fiche 4..12 sur 2 px. */
    public static final int CORE_MIN = 5, CORE_MAX = 11;
    private static final VoxelShape CORE = Block.box(CORE_MIN, CORE_MIN, CORE_MIN, CORE_MAX, CORE_MAX, CORE_MAX);
    private static final Map<Direction, VoxelShape> ARMS = new EnumMap<>(Direction.class), PLUGS = new EnumMap<>(Direction.class);
    private static final Map<BlockState, VoxelShape> SHAPES = new ConcurrentHashMap<>();

    static {
        int a = CORE_MIN, b = CORE_MAX, p = a - 1, q = b + 1;
        ARMS.put(Direction.NORTH, Block.box(a, a, 0, b, b, a));
        ARMS.put(Direction.SOUTH, Block.box(a, a, b, b, b, 16));
        ARMS.put(Direction.WEST, Block.box(0, a, a, a, b, b));
        ARMS.put(Direction.EAST, Block.box(b, a, a, 16, b, b));
        ARMS.put(Direction.DOWN, Block.box(a, 0, a, b, a, b));
        ARMS.put(Direction.UP, Block.box(a, b, a, b, 16, b));
        PLUGS.put(Direction.NORTH, Block.box(p, p, 0, q, q, 2));
        PLUGS.put(Direction.SOUTH, Block.box(p, p, 14, q, q, 16));
        PLUGS.put(Direction.WEST, Block.box(0, p, p, 2, q, q));
        PLUGS.put(Direction.EAST, Block.box(14, p, p, 16, q, q));
        PLUGS.put(Direction.DOWN, Block.box(p, 0, p, q, 2, q));
        PLUGS.put(Direction.UP, Block.box(p, 14, p, q, 16, q));
    }

    public final CableLevel level;
    public final boolean clear;

    public LogisticCableBlock(CableLevel level, boolean clear, Properties properties) {
        super(properties);
        this.level = level;
        this.clear = clear;
        BlockState state = stateDefinition.any();
        for (EnumProperty<Link> property : PROPERTIES.values()) {
            state = state.setValue(property, Link.NONE);
        }
        registerDefaultState(state.setValue(Waterlogging.WATERLOGGED, false).setValue(Covers.COVERED, false).setValue(Covers.FRAMED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(Waterlogging.WATERLOGGED, Covers.COVERED, Covers.FRAMED);
        PROPERTIES.values().forEach(builder::add);
    }

    public static Link link(BlockState state, Direction side) {
        return state.getValue(PROPERTIES.get(side));
    }

    /** Masque des cotes relies a un cable (bits = Direction.ordinal). */
    public static int cableMask(BlockState state) {
        int mask = 0;
        for (Direction d : Directions.ALL) {
            if (state.getValue(PROPERTIES.get(d)) == Link.CABLE) {
                mask |= 1 << d.ordinal();
            }
        }
        return mask;
    }

    /** Masque des fiches (bits = Direction.ordinal). */
    public static int plugMask(BlockState state) {
        int mask = 0;
        for (Direction d : Directions.ALL) {
            if (state.getValue(PROPERTIES.get(d)) == Link.PLUG) {
                mask |= 1 << d.ordinal();
            }
        }
        return mask;
    }

    // ------------------------------------------------------------------
    // Infobulle
    // ------------------------------------------------------------------

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter world, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        //1.11.2 : tier des niveaux MKI a MKIV (bois, bronze, supraconducteur : pas de tier)
        int mk = level.ordinal() - CableLevel.BASIC.ordinal();
        if (mk >= 0 && mk < 4) {
            tips.tier(mk);
        }
        for (ChannelKind kind : ChannelKind.all()) {
            if (!level.permits(kind)) {
                continue;
            }
            String value = Texts.format(level.rate(kind));
            tips.spec("logistic.pioneers.rate." + kind.id(), value);
        }
        if (level != CableLevel.SUPER) {
            if (level.maxTemperature < 1e9) {
                tips.spec("logistic.pioneers.tooltip.max_temperature", (int) level.maxTemperature - 273);
            }
            tips.spec(level.lined ? "logistic.pioneers.tooltip.lined" : "logistic.pioneers.tooltip.unlined");
            tips.desc("logistic.pioneers.tooltip.channels");
        } else {
            tips.desc("logistic.pioneers.tooltip.superconducting");
        }
        tips.hint("logistic.pioneers.tooltip.use");
        tips.hint("cover.pioneers.hint");
    }

    // ------------------------------------------------------------------
    // Tile : seulement si une fiche, un cable transparent, ou une coupure (creee a la cle)
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return clear || plugMask(state) != 0 || state.getValue(Covers.COVERED) ? new LogisticCableTile(pos, state) : null;
    }

    // ------------------------------------------------------------------
    // Branchements (calcules dans updateShape / neighborChanged, jamais par tick)
    // ------------------------------------------------------------------

    /**
     * Branchement d'un cote. 1.0.5-beta (lot 45, retour 63) : une face DECONNECTEE (machine reglee sur « Rien » partout, masque de la tile tenu
     * par le reseau) n'a pas de fiche : NONE ; si la machine n'est plus la, la fiche latente disparait (serveur).
     */
    Link compute(BlockGetter world, BlockPos pos, Direction side) {
        BlockEntity own = world.getBlockEntity(pos);
        if (own instanceof LogisticCableTile tile && tile.isCut(side)) {
            return Link.NONE;
        }
        BlockPos next = pos.relative(side);
        //Jamais de lecture dans un chunk non charge : le cote garde son etat (et sa fiche latente)
        if (world instanceof LevelReader reader && !reader.hasChunkAt(next)) {
            BlockState current = world.getBlockState(pos);
            return current.getBlock() == this ? current.getValue(PROPERTIES.get(side)) : Link.NONE;
        }
        Link link = compute0(world, pos, side, next);
        if (own instanceof LogisticCableTile tile && tile.isDetached(side)) {
            if (link == Link.PLUG) {
                return Link.NONE;
            }
            if (world instanceof Level l && !l.isClientSide) {
                tile.clearDetached(side);
            }
        }
        return link;
    }

    private Link compute0(BlockGetter world, BlockPos pos, Direction side, BlockPos next) {
        BlockState neighbor = world.getBlockState(next);
        if (neighbor.getBlock() instanceof LogisticCableBlock other) {
            if (!level.joins(other.level)) {
                return Link.NONE;
            }
            boolean cut = world.getBlockEntity(next) instanceof LogisticCableTile tile && tile.isCut(side.getOpposite());
            return cut ? Link.NONE : Link.CABLE;
        }
        BlockEntity tile = world.getBlockEntity(next);
        if (tile == null || tile instanceof LogisticCableTile) {
            return Link.NONE;
        }
        return PortCache.kindsOf(tile, level.permittedMask()) != 0 ? Link.PLUG : Link.NONE;
    }

    public BlockState refresh(BlockGetter world, BlockPos pos, BlockState state) {
        BlockState updated = state;
        for (Direction side : Directions.ALL) {
            updated = updated.setValue(PROPERTIES.get(side), compute(world, pos, side));
        }
        return updated;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        BlockState state = refresh(context.getLevel(), context.getClickedPos(), defaultBlockState());
        return Waterlogging.placed(state, context.getLevel(), context.getClickedPos());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor world,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        Waterlogging.tick(state, world, pos);
        return state.setValue(PROPERTIES.get(side), compute(world, pos, side));
    }

    /** Une machine voisine a change ses capacites (ou est posee avec sa tile) : on recalcule la fiche. */
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, world, pos, block, from, moving);
        if (!world.isClientSide) {
            BlockState updated = refresh(world, pos, state);
            if (updated != state) {
                world.setBlock(pos, updated, Block.UPDATE_ALL);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, world, pos, old, moving);
        if (world.isClientSide) {
            return;
        }
        if (old.getBlock() != this) {
            LogisticNetworks.placed(world, pos, state);
        } else if (old != state) {
            LogisticNetworks.changed(world, pos, old, state);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        Covers.dropOnRemove(state, world, pos, next);
        if (!world.isClientSide && next.getBlock() != this) {
            LogisticNetworks.removed(world, pos, state);
        }
        super.onRemove(state, world, pos, next, moving);
    }

    // ------------------------------------------------------------------
    // Cle et diagramme
    // ------------------------------------------------------------------

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level world, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        //1.0.4-beta (lot 27) : cache ; lot 30 : cadre de cache en main (il s'installe), lime (retire la cache puis le cadre), bloc plein sur
        //un cadre (il devient la cache) ; la cle garde son role (Covers)
        InteractionResult covered = Covers.use(state, world, pos, player, hand, hit);
        if (covered != null) {
            return covered;
        }
        ItemStack held = player.getItemInHand(hand);
        boolean wrench = Wrenches.isWrench(held);
        //Un bloc posable en main : on le pose (pas de diagramme)
        if (!wrench && held.getItem() instanceof BlockItem) {
            return InteractionResult.PASS;
        }
        if (world.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        Direction side = sideHit(pos, hit);
        if (!wrench || player.isShiftKeyDown()) {
            if (player instanceof ServerPlayer sp) {
                //1.0.5-beta (lot 45, retour 63) : Maj + clic vers une machine deconnectee : schema ouvert sur elle aussi
                boolean detached = world.getBlockEntity(pos) instanceof LogisticCableTile t && t.isDetached(side);
                Direction focus = player.isShiftKeyDown() && (link(state, side) == Link.PLUG || detached) ? side : null;
                DIAGRAM.open(sp, pos.immutable(), focus);
            }
            return InteractionResult.CONSUME;
        }
        LogisticCableTile tile = world.getBlockEntity(pos) instanceof LogisticCableTile t ? t : null;
        if (reconnect(state, world, pos, tile, side, player)) {
            return InteractionResult.CONSUME;
        }
        if (tile == null) {
            //Coupure d'un cable nu : la tile qui la memorise est creee maintenant
            tile = new LogisticCableTile(pos, state);
            world.setBlockEntity(tile);
        }
        boolean cut = !tile.isCut(side);
        tile.setCut(side, cut);
        if (cut) {
            //1.0.5-beta (lot 45) : une face coupee n'est plus une fiche latente (la machine sort du reseau et du schema)
            tile.clearDetached(side);
        }
        world.setBlock(pos, refresh(world, pos, state), Block.UPDATE_ALL);
        LogisticNetworks.invalidate(world, pos);
        player.displayClientMessage(Component.translatable(cut ? "logistic.pioneers.side.cut" : "logistic.pioneers.side.restored",
              Component.translatable("direction.pioneers." + side.getName())).withStyle(cut ? ChatFormatting.DARK_GRAY : ChatFormatting.GREEN), true);
        world.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8F, cut ? 0.8F : 1.2F);
        return InteractionResult.CONSUME;
    }

    /**
     * Reconnexion facile, sans viser la face exacte : on vise un cable voisin qui a coupe le cote tourne vers celui-ci (son cote est
     * retabli), ou une face du coeur sans branchement d'un cable qui a des faces coupees (elles sont toutes retablies).
     */
    private boolean reconnect(BlockState state, Level world, BlockPos pos, @Nullable LogisticCableTile tile, Direction side, Player player) {
        if (tile != null && tile.isCut(side) || link(state, side).linked()) {
            return false;
        }
        BlockPos next = pos.relative(side);
        List<Direction> restored = new ArrayList<>();
        if (world.getBlockState(next).getBlock() instanceof LogisticCableBlock other && level.joins(other.level)
            && world.getBlockEntity(next) instanceof LogisticCableTile neighbor && neighbor.isCut(side.getOpposite())) {
            neighbor.setCut(side.getOpposite(), false);
            restored.add(side);
        } else if (tile != null) {
            for (Direction d : Directions.ALL) {
                if (tile.isCut(d)) {
                    tile.setCut(d, false);
                    restored.add(d);
                }
            }
        }
        if (restored.isEmpty()) {
            return false;
        }
        world.setBlock(pos, refresh(world, pos, state), Block.UPDATE_ALL);
        for (Direction d : restored) {
            BlockPos other = pos.relative(d);
            BlockState otherState = world.getBlockState(other);
            if (otherState.getBlock() instanceof LogisticCableBlock cable) {
                world.setBlock(other, cable.refresh(world, other, otherState), Block.UPDATE_ALL);
                LogisticNetworks.invalidate(world, other);
            }
        }
        LogisticNetworks.invalidate(world, pos);
        player.displayClientMessage(Component.translatable("logistic.pioneers.side.reconnected", restored.size()).withStyle(ChatFormatting.GREEN), true);
        world.playSound(null, pos, SoundEvents.ITEM_FRAME_ROTATE_ITEM, SoundSource.BLOCKS, 0.8F, 1.2F);
        return true;
    }

    /** D2a : resume du reseau dans la barre d'action (le diagramme arrive en D2b). */
    private static void statusMessage(ServerPlayer player, BlockPos cable, @Nullable Direction focus) {
        LogisticNetwork network = LogisticNetworks.network(player.level(), cable);
        if (network == null) {
            player.displayClientMessage(Component.translatable("logistic.pioneers.status.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        int live = 0;
        for (int uid : network.channelUids()) {
            live += uid != 0 ? 1 : 0;
        }
        player.displayClientMessage(Component.translatable(network.isAuto() ? "logistic.pioneers.status.auto" : "logistic.pioneers.status",
              Component.translatable("logistic.pioneers.level." + network.level.id()), network.graph.size, network.attaches().size(), live)
              .withStyle(ChatFormatting.AQUA), true);
    }

    /** Cote vise : le bras touche s'il depasse du coeur, sinon la face du coeur. */
    public static Direction sideHit(BlockPos pos, BlockHitResult hit) {
        Vec3 local = hit.getLocation().subtract(Vec3.atCenterOf(pos));
        double ax = Math.abs(local.x), ay = Math.abs(local.y), az = Math.abs(local.z);
        double max = Math.max(ax, Math.max(ay, az));
        if (max > 0.2) {
            if (max == ax) {
                return local.x > 0 ? Direction.EAST : Direction.WEST;
            } else if (max == ay) {
                return local.y > 0 ? Direction.UP : Direction.DOWN;
            }
            return local.z > 0 ? Direction.SOUTH : Direction.NORTH;
        }
        return hit.getDirection();
    }

    // ------------------------------------------------------------------
    // Volumes, eau
    // ------------------------------------------------------------------

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        //1.0.4-beta (lot 30) : cadre de cache (avec ou sans cache) : forme pleine
        if (Covers.framed(state)) {
            return Shapes.block();
        }
        return SHAPES.computeIfAbsent(state.setValue(Waterlogging.WATERLOGGED, false).setValue(Covers.FRAMED, false), s -> {
            VoxelShape shape = CORE;
            for (Direction d : Directions.ALL) {
                Link link = s.getValue(PROPERTIES.get(d));
                if (link.linked()) {
                    shape = Shapes.or(shape, ARMS.get(d));
                    if (link == Link.PLUG) {
                        shape = Shapes.or(shape, PLUGS.get(d));
                    }
                }
            }
            return shape.optimize();
        });
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos) {
        return !state.getValue(Covers.COVERED);
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 27) : cache (voir Covers)
    // ------------------------------------------------------------------

    @Override
    @SuppressWarnings("deprecation")
    public int getLightBlock(@NotNull BlockState state, @NotNull BlockGetter world, @NotNull BlockPos pos) {
        return state.getValue(Covers.COVERED) ? world.getMaxLightLevel() : super.getLightBlock(state, world, pos);
    }

    @Override
    public boolean onDestroyedByPlayer(BlockState state, Level world, BlockPos pos, Player player, boolean willHarvest, FluidState fluid) {
        return !Covers.breakCover(state, world, pos, player) && super.onDestroyedByPlayer(state, world, pos, player, willHarvest, fluid);
    }

    @Override
    @SuppressWarnings("deprecation")
    public float getDestroyProgress(@NotNull BlockState state, @NotNull Player player, @NotNull BlockGetter world, @NotNull BlockPos pos) {
        return Covers.progress(state, player, world, pos, super.getDestroyProgress(state, player, world, pos));
    }

    @Override
    public SoundType getSoundType(BlockState state, LevelReader world, BlockPos pos, @Nullable Entity entity) {
        return Covers.sound(state, world, pos, entity, super.getSoundType(state, world, pos, entity));
    }

    @NotNull
    @Override
    public ItemStack getCloneItemStack(@NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull BlockState state) {
        ItemStack cover = Covers.pick(state, world, pos);
        return cover != null ? cover : super.getCloneItemStack(world, pos, state);
    }

    /** Une cache est un bloc plein : l'eau n'y entre pas. */
    @Override
    public boolean canPlaceLiquid(@NotNull BlockGetter world, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull Fluid fluid) {
        return !state.getValue(Covers.COVERED) && SimpleWaterloggedBlock.super.canPlaceLiquid(world, pos, state, fluid);
    }

    /** Etanche (spec §1.2, comme les anciens cables) : gorge d'eau dans l'eau, jamais emporte par elle. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public FluidState getFluidState(@NotNull BlockState state) {
        return Waterlogging.fluid(state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canBeReplaced(@NotNull BlockState state, @NotNull Fluid fluid) {
        return false;
    }
}
