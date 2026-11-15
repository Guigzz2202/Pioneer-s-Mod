package fr.pioneers.kinetics.parts;

import fr.pioneers.kinetics.KineticBlock;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/** Renvoi d'angle : boite de chene ferree ; ses six faces tournent ensemble (1:1), on y entre et en sort dans tous les sens. */
public class GearboxBlock extends KineticBlock {

    public GearboxBlock(Properties properties) {
        super(properties);
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    public static class Tile extends KineticTile {

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.GEARBOX_TILE.get(), pos, state);
        }

        @Override
        public double shaftFactor(Direction face) {
            return 1;
        }

        @Override
        public double maxTorque() {
            return 6_000;
        }

        @Override
        public double maxSpeed() {
            return 240;
        }

        @Override
        public double efficiency() {
            return 0.97;
        }

        @Override
        public double inertia() {
            return 0.8;
        }
    }
}
