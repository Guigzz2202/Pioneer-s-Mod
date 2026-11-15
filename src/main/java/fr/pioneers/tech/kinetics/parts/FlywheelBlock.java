package fr.pioneers.tech.kinetics.parts;

import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.core.registries.PiKinetics;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;

/** Volant d'inertie (pierre, fer) : stocke l'energie cinetique 1/2 J w2 et lisse les a-coups (rafales d'un moulin, coups du marteau-pilon). */
public class FlywheelBlock extends AxisKineticBlock {

    private static final Map<Direction.Axis, VoxelShape> SHAPES = new EnumMap<>(Direction.Axis.class);

    static {
        SHAPES.put(Direction.Axis.Y, Block.box(0, 5, 0, 16, 11, 16));
        SHAPES.put(Direction.Axis.X, Block.box(5, 0, 0, 11, 16, 16));
        SHAPES.put(Direction.Axis.Z, Block.box(0, 0, 5, 16, 16, 11));
    }

    public final boolean iron;

    public FlywheelBlock(boolean iron, Properties properties) {
        super(properties);
        this.iron = iron;
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        return SHAPES.get(axis(state));
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends KineticTile {

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.FLYWHEEL_TILE.get(), pos, state);
        }

        public boolean iron() {
            return getBlockState().getBlock() instanceof FlywheelBlock block && block.iron;
        }

        @Override
        public double shaftFactor(Direction face) {
            return face.getAxis() == axis(getBlockState()) ? 1 : 0;
        }

        @Override
        public double inertia() {
            return iron() ? 1_200 : 400;
        }

        @Override
        public double maxTorque() {
            return iron() ? 12_000 : 5_000;
        }

        @Override
        public double maxSpeed() {
            return iron() ? 300 : 150;
        }

        /** Energie stockee (J). */
        public double energy() {
            double w = speed * 2 * Math.PI / 60;
            return 0.5 * inertia() * w * w;
        }

        @Override
        protected double renderRadius() {
            return 1;
        }
    }
}
