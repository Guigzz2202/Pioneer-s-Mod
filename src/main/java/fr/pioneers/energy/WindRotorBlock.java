package fr.pioneers.energy;

import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume : « les eoliennes devaient avoir un meilleur design, pas seulement la tete ») : rotor d'eolienne, bloc a part
 * pose sur la face avant d'une nacelle ({@link PiEnergy#WIND}). Un rotor par tier (longueur des pales {@link EnergyKind#WIND_BLADE}, disque
 * a degager {@link EnergyKind#WIND_RADIUS}) ; la nacelle produit au tier le plus faible des deux. {@code FACING} = sens du vent (face avant
 * de la nacelle) : le moyeu touche la nacelle par sa face arriere. Le bloc ne dessine rien lui-meme : moyeu et pales tournent dans
 * {@code client/energy/WindRotorRenderer} (vitesse lue sur la nacelle). Collision : le moyeu seulement (les pales sont du decor).
 */
public class WindRotorBlock extends HorizontalDirectionalBlock implements EntityBlock {

    public final int tier;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public WindRotorBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
        for (Direction d : Direction.Plane.HORIZONTAL) {
            //Repere local : arriere (z 0) contre la nacelle, avant vers le vent ; moyeu 5..11 de 0 a 8,6 px
            shapes[d.get2DDataValue()] = local(d, 4.5, 4.5, 0, 11.5, 11.5, 8.6);
        }
    }

    /** Boite locale (z vers l'avant) tournee vers {@code facing}. */
    private static VoxelShape local(Direction facing, double x0, double y0, double z0, double x1, double y1, double z1) {
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, z0, 16 - x0, y1, z1);
            case WEST -> Block.box(16 - z1, y0, 16 - x1, 16 - z0, y1, 16 - x0);
            case EAST -> Block.box(z0, y0, x0, z1, y1, x1);
            default -> Block.box(x0, y0, 16 - z1, x1, y1, 16 - z0);
        };
    }

    /** Vrai si l'etat est un rotor tourne vers {@code front} (pose devant une nacelle orientee ainsi). */
    public static boolean facesAway(BlockState state, Direction front) {
        return state.getBlock() instanceof WindRotorBlock && state.getValue(FACING) == front;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.@NotNull Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction face = context.getClickedFace();
        if (face.getAxis().isHorizontal()) {
            //Pose sur la face avant d'une nacelle : le rotor prend son orientation
            BlockState behind = context.getLevel().getBlockState(context.getClickedPos().relative(face.getOpposite()));
            if (behind.getBlock() instanceof GeneratorBlock g && g.kind == EnergyKind.WIND && behind.getValue(MachineBlock.FACING) == face) {
                return defaultBlockState().setValue(FACING, face);
            }
        }
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        VoxelShape s = shapes[state.getValue(FACING).get2DDataValue()];
        return s == null ? Shapes.block() : s;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.ENTITYBLOCK_ANIMATED;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(@NotNull BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new WindRotorTile(pos, state);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable BlockGetter level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("energy.pioneers.tip.wind_rotor", EnergyKind.WIND_RADIUS[tier] * 2 + 1);
    }
}
