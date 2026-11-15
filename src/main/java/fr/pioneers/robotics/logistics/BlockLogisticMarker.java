package fr.pioneers.robotics.logistics;

import fr.pioneers.client.robotics.ClientRobotics;
import fr.pioneers.core.Directions;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.SimpleWaterloggedBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Balise logistique : petit boitier lumineux pose sur une face d'un coffre ou d'une machine, comme un levier. Ses reglages (role,
 * face d'acces, ressources, priorite...) s'ouvrent a la pose et d'un clic droit ; sa couleur dit son role ({@link MarkerRole}).
 */
public class BlockLogisticMarker extends BaseEntityBlock implements SimpleWaterloggedBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;
    public static final BooleanProperty WATERLOGGED = BlockStateProperties.WATERLOGGED;
    public static final EnumProperty<MarkerRole> ROLE = EnumProperty.create("role", MarkerRole.class);
    private static final VoxelShape[] SHAPES = new VoxelShape[6];

    static {
        //Socle 8x8 de 2 px contre le bloc, lanterne 4x4 de 3 px
        for (Direction dir : Directions.ALL) {
            SHAPES[dir.get3DDataValue()] = Shapes.or(box(dir, 4, 0, 4, 12, 2, 12), box(dir, 6, 2, 6, 10, 5, 10));
        }
    }

    /** Boite donnee pour une balise posee sur le dessus d'un bloc (FACING = UP), tournee vers {@code dir}. */
    private static VoxelShape box(Direction dir, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (dir) {
            case UP -> Block.box(x0, y0, z0, x1, y1, z1);
            case DOWN -> Block.box(x0, 16 - y1, z0, x1, 16 - y0, z1);
            case NORTH -> Block.box(x0, z0, 16 - y1, x1, z1, 16 - y0);
            case SOUTH -> Block.box(x0, z0, y0, x1, z1, y1);
            case WEST -> Block.box(16 - y1, z0, x0, 16 - y0, z1, x1);
            case EAST -> Block.box(y0, z0, x0, y1, z1, x1);
        };
    }

    public BlockLogisticMarker(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.UP).setValue(WATERLOGGED, false).setValue(ROLE, MarkerRole.PROVIDER));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, WATERLOGGED, ROLE);
    }

    @NotNull
    @Override
    public VoxelShape getShape(BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES[state.getValue(FACING).get3DDataValue()];
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        BlockState state = defaultBlockState().setValue(FACING, face)
              .setValue(WATERLOGGED, context.getLevel().getFluidState(context.getClickedPos()).getType() == Fluids.WATER);
        return state.canSurvive(context.getLevel(), context.getClickedPos()) ? state : null;
    }

    @Override
    public boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        BlockPos attached = pos.relative(state.getValue(FACING).getOpposite());
        return !level.getBlockState(attached).isAir() && !(level.getBlockState(attached).getBlock() instanceof BlockLogisticMarker);
    }

    @NotNull
    @Override
    public BlockState updateShape(BlockState state, @NotNull Direction direction, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        if (state.getValue(WATERLOGGED)) {
            level.scheduleTick(pos, Fluids.WATER, Fluids.WATER.getTickDelay(level));
        }
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @NotNull
    @Override
    public FluidState getFluidState(BlockState state) {
        return state.getValue(WATERLOGGED) ? Fluids.WATER.getSource(false) : super.getFluidState(state);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new TileEntityLogisticMarker(pos, state);
    }

    @NotNull
    @Override
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (level.isClientSide && level.getBlockEntity(pos) instanceof TileEntityLogisticMarker marker) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.openPointEditor(marker));
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer, @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        //Balise toute neuve : on ouvre ses reglages
        if (level.isClientSide && placer instanceof Player && level.getBlockEntity(pos) instanceof TileEntityLogisticMarker marker) {
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientRobotics.openPointEditor(marker));
        }
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("robotics.pioneers.marker.desc");
        tips.hint("robotics.pioneers.marker.place");
    }

    /** Couleur de la balise (rendu). */
    public static int color(BlockState state) {
        return state.hasProperty(ROLE) ? state.getValue(ROLE).color : 0xFFFFFF;
    }
}
