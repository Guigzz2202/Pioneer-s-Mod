package fr.pioneers.logistics.rail.track;

import fr.pioneers.core.Wrenches;
import fr.pioneers.logistics.rail.RailWorld;
import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.block.PostKind;
import fr.pioneers.logistics.rail.block.RailPostBlock;
import fr.pioneers.logistics.rail.net.Piece;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : bloc de voie (toutes sortes). La piece logique est inscrite au reseau ({@link RailWorld}) a la pose et retiree a la casse.
 * Pose automatique et cle : {@link TrackPlacement}. 0.20.3 : la voie doit reposer sur un bloc plein ({@link #supported}).
 * Les blocs de voie ne sont pas solides (on marche au travers, comme les rails du jeu de base) ; leur modele est cuit en Java
 * ({@code client/rail/TrackModels}).
 */
public abstract class TrackBlock extends Block {

    private static final VoxelShape FLAT = Block.box(0, 0, 0, 16, 4, 16);
    private static final VoxelShape BUFFER = Block.box(0, 0, 0, 16, 9, 16);

    public final TrackQuality quality;
    public final Piece.Kind kind;

    protected TrackBlock(TrackQuality quality, Piece.Kind kind, Properties properties) {
        super(properties);
        this.quality = quality;
        this.kind = kind;
    }

    public abstract EnumProperty<TrackShape> shapeProperty();

    @Nullable
    public TrackShape shape(BlockState state) {
        return state.hasProperty(shapeProperty()) ? state.getValue(shapeProperty()) : null;
    }

    /** Formes que ce bloc peut prendre. */
    public boolean allows(TrackShape shape) {
        return shapeProperty().getPossibleValues().contains(shape);
    }

    public BlockState withShape(BlockState state, TrackShape shape) {
        return allows(shape) ? state.setValue(shapeProperty(), shape) : state;
    }

    /** 0.20.7 : collage tourne d'un plan (robots) : la forme tourne avec. */
    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        TrackShape s = shape(state);
        return s == null ? state : withShape(state, s.rotated(rotation.ordinal()));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, @NotNull Mirror mirror) {
        TrackShape s = shape(state);
        if (s == null || mirror == Mirror.NONE) {
            return state;
        }
        return withShape(state, s.mirrored(mirror == Mirror.FRONT_BACK));
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        TrackShape s = shape(state);
        if (s == null) {
            return FLAT;
        }
        if (s.family() == TrackShape.Family.SLOPE) {
            return s.slopeHigh() ? Block.box(0, 0, 0, 16, 12, 16) : Block.box(0, 0, 0, 16, 8, 16);
        }
        return s.family() == TrackShape.Family.BUFFER ? BUFFER : FLAT;
    }

    @NotNull
    @Override
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return Shapes.empty();
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return true;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
        return TrackPlacement.placementState(this, ctx.getLevel(), ctx.getClickedPos(), ctx.getHorizontalDirection(), ctx.getPlayer());
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            TrackPlacement.afterPlace(level, pos, placer);
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        if (level instanceof ServerLevel sl) {
            Piece piece = TrackBlocks.pieceOf(state, pos);
            if (piece != null) {
                RailWorld.get(sl).trackPlaced(piece);
            }
        }
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (level instanceof ServerLevel sl && !next.is(this) && shape(state) != null) {
            RailWorld.get(sl).trackRemoved(pos);
            if (!moving && !(next.getBlock() instanceof TrackBlock)) {
                TrackPlacement.afterRemove(level, pos);
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    /** 0.20.3 : une voie repose sur un bloc plein (ni dans le vide, ni sur une autre voie) ; une fosse de dechargement la porte aussi. */
    public static boolean supported(LevelReader level, BlockPos pos) {
        BlockPos below = pos.below();
        BlockState under = level.getBlockState(below);
        if (under.getBlock() instanceof TrackBlock) {
            return false;
        }
        if (under.getBlock() instanceof RailPostBlock post && post.kind == PostKind.PIT) {
            return true;
        }
        return canSupportRigidBlock(level, below);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return supported(level, pos);
    }

    /** Support retire : la voie tombe (comme les rails du jeu de base). */
    @Override
    @SuppressWarnings("deprecation")
    public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
          boolean moving) {
        super.neighborChanged(state, level, pos, block, from, moving);
        if (!level.isClientSide && from.equals(pos.below()) && !supported(level, pos)) {
            dropResources(state, level, pos);
            level.removeBlock(pos, false);
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!Wrenches.isWrench(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            TrackPlacement.cycle(this, level, pos, state, player.isShiftKeyDown(), player);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Face d'une forme vers laquelle se tourne le joueur (pose). */
    public static Direction look(@Nullable Player player, Direction fallback) {
        return player == null ? fallback : player.getDirection();
    }
}
