package fr.pioneers.rail.block;

import fr.pioneers.rail.RailWorld;
import fr.pioneers.rail.track.TrackBlock;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.IronBarsBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.6 : ouvrages du chemin de fer (dessines en Java par RailBakedModel / WorksModels) : pilier de viaduc, garde-corps, portail de tunnel,
 * dalle de quai, abri de quai, lampadaire de quai, barriere de passage a niveau.
 */
public final class Works {

    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;
    public static final BooleanProperty NORTH = BlockStateProperties.NORTH, EAST = BlockStateProperties.EAST, SOUTH = BlockStateProperties.SOUTH,
          WEST = BlockStateProperties.WEST;
    public static final BooleanProperty CLOSED = BooleanProperty.create("closed");
    public static final BooleanProperty BLINK = BooleanProperty.create("blink");

    private Works() {
    }

    // ------------------------------------------------------------------ pilier de viaduc

    /** Pilier : se raccorde en hauteur ; chapiteau quand rien de pilier au-dessus, semelle quand rien de pilier dessous. */
    public static class Pillar extends Block {

        private static final VoxelShape SHAFT = Block.box(3, 0, 3, 13, 16, 13);
        private static final VoxelShape CAP = Block.box(1, 12.5, 1, 15, 16, 15);
        private static final VoxelShape FOOT = Block.box(1, 0, 1, 15, 2.5, 15);

        public Pillar(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(UP, false).setValue(DOWN, false));
        }

        @Override
        protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(UP, DOWN);
        }

        private BlockState connect(BlockState s, BlockGetter level, BlockPos pos) {
            return s.setValue(UP, level.getBlockState(pos.above()).is(this)).setValue(DOWN, level.getBlockState(pos.below()).is(this));
        }

        @Override
        public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
            return connect(defaultBlockState(), ctx.getLevel(), ctx.getClickedPos());
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
              @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
            return dir.getAxis() == Direction.Axis.Y ? connect(state, level, pos) : state;
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
            VoxelShape s = SHAFT;
            if (!state.getValue(UP)) {
                s = Shapes.or(s, CAP);
            }
            if (!state.getValue(DOWN)) {
                s = Shapes.or(s, FOOT);
            }
            return s;
        }
    }

    // ------------------------------------------------------------------ garde-corps

    /** Garde-corps : se raccorde comme une barriere (meme collision que les barreaux de fer). */
    public static class Railing extends IronBarsBlock {

        public Railing(Properties properties) {
            super(properties);
        }
    }

    // ------------------------------------------------------------------ portail de tunnel

    public enum PortalPart implements StringRepresentable {
        WALL, JAMB_L, JAMB_R, LINTEL_L, LINTEL_C, LINTEL_R;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public static final EnumProperty<PortalPart> PART = EnumProperty.create("part", PortalPart.class);

    /**
     * Portail de tunnel : maconnerie qui forme seule son cadre (piedroits, linteau et cle) autour d'une ouverture d'un bloc de large. FACING :
     * la face avant (vers celui qui pose).
     */
    public static class Portal extends HorizontalDirectionalBlock {

        public Portal(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(PART, PortalPart.WALL));
        }

        @Override
        protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, PART);
        }

        private boolean same(BlockGetter level, BlockPos pos, Direction facing) {
            BlockState s = level.getBlockState(pos);
            return s.is(this) && s.getValue(FACING) == facing;
        }

        /** Piedroit : la colonne monte (jusqu'a 3 blocs) jusqu'a un bloc qui a du portail du cote de l'ouverture. */
        private boolean lintelAbove(BlockGetter level, BlockPos pos, Direction side, Direction f) {
            for (int k = 1; k <= 3; k++) {
                BlockPos q = pos.above(k);
                if (!same(level, q, f)) {
                    return false;
                }
                if (same(level, q.relative(side), f)) {
                    return true;
                }
            }
            return false;
        }

        private BlockState form(BlockState s, BlockGetter level, BlockPos pos) {
            Direction f = s.getValue(FACING);
            Direction right = f.getCounterClockWise();
            boolean l = same(level, pos.relative(right.getOpposite()), f), r = same(level, pos.relative(right), f);
            boolean up = same(level, pos.above(), f), down = same(level, pos.below(), f);
            PortalPart p = PortalPart.WALL;
            if (l && r && !down) {
                p = PortalPart.LINTEL_C;
            } else if (r && !l && down && !same(level, pos.relative(right).below(), f)) {
                p = PortalPart.LINTEL_L;
            } else if (l && !r && down && !same(level, pos.relative(right.getOpposite()).below(), f)) {
                p = PortalPart.LINTEL_R;
            } else if (up && !r && lintelAbove(level, pos, right, f)) {
                p = PortalPart.JAMB_L;
            } else if (up && !l && lintelAbove(level, pos, right.getOpposite(), f)) {
                p = PortalPart.JAMB_R;
            }
            return s.setValue(PART, p);
        }

        @Override
        public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
            return form(defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()), ctx.getLevel(), ctx.getClickedPos());
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
              @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
            return form(state, level, pos);
        }

        @Override
        @SuppressWarnings("deprecation")
        public void neighborChanged(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Block block, @NotNull BlockPos from,
              boolean moving) {
            //Les coins (diagonales) comptent aussi : updateShape ne les voit pas
            BlockState n = form(state, level, pos);
            if (n != state) {
                level.setBlock(pos, n, Block.UPDATE_ALL);
            }
        }
    }

    // ------------------------------------------------------------------ dalle de quai

    /** Dalle de quai (demi-bloc) : bordure de securite tournee vers la voie voisine (FACING). */
    public static class PlatformSlab extends HorizontalDirectionalBlock {

        private static final VoxelShape SHAPE = Block.box(0, 0, 0, 16, 8, 16);

        public PlatformSlab(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        }

        @Override
        protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING);
        }

        @Nullable
        static Direction track(BlockGetter level, BlockPos pos) {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(d)).getBlock() instanceof TrackBlock || level.getBlockState(pos.relative(d).below()).getBlock() instanceof TrackBlock) {
                    return d;
                }
            }
            return null;
        }

        @Override
        public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
            Direction t = track(ctx.getLevel(), ctx.getClickedPos());
            return defaultBlockState().setValue(FACING, t != null ? t : ctx.getHorizontalDirection());
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
              @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
            if (dir.getAxis().isHorizontal() && other.getBlock() instanceof TrackBlock && !(level.getBlockState(pos.relative(state.getValue(FACING))).getBlock()
                  instanceof TrackBlock)) {
                return state.setValue(FACING, dir);
            }
            return state;
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
            return SHAPE;
        }
    }

    //1.9.2 (retours de Guillaume) : abri de quai supprime (remap PiRemaps)

    // ------------------------------------------------------------------ lampadaire de quai

    /** Lampadaire de quai (eclaire 15, dessine sur deux blocs de haut). */
    public static class Lamp extends Block {

        private static final VoxelShape SHAPE = Block.box(5.5, 0, 5.5, 10.5, 16, 10.5);

        public Lamp(Properties properties) {
            super(properties);
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
            return SHAPE;
        }
    }

    // ------------------------------------------------------------------ barriere de passage a niveau

    /**
     * Barriere de passage a niveau, posee sur la route a cote de la voie (FACING : vers la voie) : fermee (feux rouges qui clignotent, passage
     * bloque) tant qu'un train occupe la voie a moins de {@link #RANGE} blocs.
     */
    public static class Barrier extends HorizontalDirectionalBlock {

        public static final int RANGE = 8;
        private static final VoxelShape[] POSTS = new VoxelShape[4], ARMS = new VoxelShape[4];

        static {
            for (Direction d : Direction.Plane.HORIZONTAL) {
                POSTS[d.get2DDataValue()] = rotate(1, 0, 1, 3.5, 14, 3.5, d);
                ARMS[d.get2DDataValue()] = rotate(0, 0, 1.2, 16, 24, 3.2, d);
            }
        }

        /** Boite du repere canonique (voie au nord) tournee vers d. */
        static VoxelShape rotate(double x0, double y0, double z0, double x1, double y1, double z1, Direction d) {
            return switch (d) {
                case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
                case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
                case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
                default -> Block.box(x0, y0, z0, x1, y1, z1);
            };
        }

        public Barrier(Properties properties) {
            super(properties);
            registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(CLOSED, false).setValue(BLINK, false));
        }

        @Override
        protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
            builder.add(FACING, CLOSED, BLINK);
        }

        @Override
        public BlockState getStateForPlacement(@NotNull BlockPlaceContext ctx) {
            Direction t = PlatformSlab.track(ctx.getLevel(), ctx.getClickedPos());
            return defaultBlockState().setValue(FACING, t != null ? t : ctx.getHorizontalDirection());
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
            return POSTS[state.getValue(FACING).get2DDataValue()];
        }

        @NotNull
        @Override
        @SuppressWarnings("deprecation")
        public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
            int i = state.getValue(FACING).get2DDataValue();
            return state.getValue(CLOSED) ? Shapes.or(POSTS[i], ARMS[i]) : POSTS[i];
        }

        @Override
        @SuppressWarnings("deprecation")
        public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
            super.onPlace(state, level, pos, old, moving);
            if (!old.is(this)) {
                level.scheduleTick(pos, this, 5);
            }
        }

        @Override
        @SuppressWarnings("deprecation")
        public void tick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
            boolean closed = RailWorld.get(level).trainNear(pos, RANGE);
            boolean blink = closed && (level.getGameTime() / 10) % 2 == 0;
            if (closed != state.getValue(CLOSED) || blink != state.getValue(BLINK)) {
                if (closed && !state.getValue(CLOSED)) {
                    level.playSound(null, pos, SoundEvents.NOTE_BLOCK_BELL.value(), SoundSource.BLOCKS, 0.8F, 1.6F);
                }
                level.setBlock(pos, state.setValue(CLOSED, closed).setValue(BLINK, blink), Block.UPDATE_CLIENTS);
            }
            level.scheduleTick(pos, this, 5);
        }
    }
}
