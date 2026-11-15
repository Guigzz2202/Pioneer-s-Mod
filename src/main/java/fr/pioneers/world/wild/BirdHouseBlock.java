package fr.pioneers.world.wild;

import fr.pioneers.farm.device.DeviceSites;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 7, specification §9, §11 n° 12) : le NICHOIR A OISEAUX (a ne pas confondre avec le PONDOIR des poules, {@code nest_box}).
 * Une petite maison de bois au toit a deux pans et au trou d'envol, POSEE CONTRE UN MUR (dos au mur, {@link #POST} faux) ou SUR UN POTEAU
 * (sur une barriere, un muret, une buche... : {@link #POST} vrai) ; 11 bois. Il ATTIRE pigeons et pics-verts : de temps en temps, s'il y a
 * moins de 2 de ces oiseaux a 32 blocs, un oiseau arrive de loin et vient se poser pres de lui (tuile {@link BirdHouseTile}, dans le budget
 * des oiseaux de la zone) ; et il fait tomber de temps en temps une PLUME DESSOUS (tick aleatoire, deux fois plus souvent si un oiseau est
 * pres, au plus 3 plumes au sol : {@link Wild7Rules#featherDrop}).
 */
public class BirdHouseBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty POST = BooleanProperty.create("post");
    /** Formes : sur poteau (centree), au mur (dos contre la face opposee a FACING). */
    static final VoxelShape POST_SHAPE = Block.box(3, 0, 3, 13, 14, 13);
    static final VoxelShape[] WALL = {Block.box(3, 2, 6, 13, 16, 16), Block.box(0, 2, 3, 10, 16, 13), Block.box(3, 2, 0, 13, 16, 10),
                                      Block.box(6, 2, 3, 16, 16, 13)};

    public BirdHouseBlock(Properties p) {
        super(p);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(POST, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> b) {
        b.add(FACING, POST);
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        Direction clicked = ctx.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            BlockState s = defaultBlockState().setValue(FACING, clicked).setValue(POST, false);
            if (s.canSurvive(ctx.getLevel(), ctx.getClickedPos())) {
                return s;
            }
        }
        BlockState s = defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite()).setValue(POST, true);
        return s.canSurvive(ctx.getLevel(), ctx.getClickedPos()) ? s : null;
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        if (state.getValue(POST)) {
            BlockState below = level.getBlockState(pos.below());
            return !below.isAir() && (below.isFaceSturdy(level, pos.below(), Direction.UP) || !below.getCollisionShape(level, pos.below()).isEmpty());
        }
        Direction f = state.getValue(FACING);
        BlockPos back = pos.relative(f.getOpposite());
        return level.getBlockState(back).isFaceSturdy(level, back, f) || level.getBlockState(back).is(net.minecraft.tags.BlockTags.LOGS);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction dir, @NotNull BlockState other, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos otherPos) {
        return !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        if (state.getValue(POST)) {
            return POST_SHAPE;
        }
        return WALL[state.getValue(FACING).get2DDataValue()];
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new BirdHouseTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PiWild7.BIRD_HOUSE_TILE.get(), BirdHouseTile::serverTick);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void randomTick(@NotNull BlockState state, @NotNull ServerLevel level, @NotNull BlockPos pos, @NotNull RandomSource random) {
        feather(level, pos, random.nextInt(Wild7Rules.FEATHER_ODDS));
    }

    /** Plume dessous (tick aleatoire) ; rend vrai si une plume est tombee. */
    public static boolean feather(ServerLevel level, BlockPos pos, int roll) {
        BlockPos below = pos.below();
        if (!level.getBlockState(below).getCollisionShape(level, below).isEmpty() && !level.getBlockState(pos).getValue(POST)) {
            return false;
        }
        BlockPos drop = level.getBlockState(pos).getValue(POST) ? pos.relative(level.getBlockState(pos).getValue(FACING)) : below;
        AABB zone = new AABB(drop).inflate(0.5, 3, 0.5).move(0, -2, 0);
        int lying = 0;
        for (ItemEntity e : level.getEntitiesOfClass(ItemEntity.class, zone, e -> e.getItem().is(Items.FEATHER))) {
            lying += e.getItem().getCount();
        }
        boolean near = !level.getEntitiesOfClass(WildBird.class, new AABB(pos).inflate(16)).isEmpty();
        if (!Wild7Rules.featherDrop(roll, near, lying)) {
            return false;
        }
        ItemEntity it = new ItemEntity(level, drop.getX() + 0.5, drop.getY() + 0.6, drop.getZ() + 0.5, new ItemStack(Items.FEATHER));
        it.setDeltaMovement(0, -0.02, 0);
        level.addFreshEntity(it);
        return true;
    }

    /** Nichoirs a moins de {@code r} blocs (registre des sites). */
    public static List<BlockPos> near(Level level, BlockPos pos, double r) {
        return DeviceSites.near(level, pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, r, DeviceSites.Kind.BIRD_HOUSE);
    }
}
