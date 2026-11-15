package fr.pioneers.kinetics.parts;

import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticNetwork;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Instruments sur l'arbre : tachymetre (vitesse : comparateur 1 niveau par 10 tr/min) et dynamometre (charge du reseau : comparateur
 * proportionnel, 15 = surcharge). Le cadran est dessine sur les flancs.
 * <p>
 * 1.0.2-beta (Guillaume : « le tachymetre et le dynamometre se placent sur le cote, ils ne regardent pas le joueur ») : a la pose, le cadran
 * se tourne vers le joueur ({@link #dialToward} : parmi les faces perpendiculaires a l'arbre, celle qui lui fait face ; le second cadran est
 * au dos) ; face retenue par la tile (cle {@code dial}, envoyee au client). Instrument pose avant : cadrans d'avant (aucune cle).
 */
public class GaugeBlock extends AxisKineticBlock {

    public final boolean dynamometer;

    public GaugeBlock(boolean dynamometer, Properties properties) {
        super(properties);
        this.dynamometer = dynamometer;
    }

    @Override
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Tile tile ? tile.signal : 0;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    @Override
    public void setPlacedBy(@NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState state, @Nullable LivingEntity placer,
          @NotNull ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (placer != null && level.getBlockEntity(pos) instanceof Tile tile) {
            tile.dial = dialToward(axis(state), Vec3.atCenterOf(pos), placer.getEyePosition());
            tile.setChanged();
            tile.markClientsDirty();
        }
    }

    /** Face du cadran (perpendiculaire a l'arbre {@code axis}) tournee vers {@code eye} depuis le centre {@code center} du bloc. */
    public static Direction dialToward(Direction.Axis axis, Vec3 center, Vec3 eye) {
        Vec3 to = eye.subtract(center);
        Direction best = null;
        double score = -Double.MAX_VALUE;
        for (Direction d : Direction.values()) {
            if (d.getAxis() == axis) {
                continue;
            }
            double s = to.x * d.getStepX() + to.y * d.getStepY() + to.z * d.getStepZ();
            if (s > score) {
                score = s;
                best = d;
            }
        }
        return best;
    }

    public static class Tile extends KineticTile {

        int signal;
        /** 1.0.2-beta : face du cadran tournee vers le joueur a la pose (null : instrument d'avant, cadrans d'origine). */
        @Nullable
        Direction dial;
        /** Aiguille du cadran (0..1), envoyee au client. */
        float needle, sentNeedle = -1;

        public Tile(BlockPos pos, BlockState state) {
            super(PiKinetics.GAUGE_TILE.get(), pos, state);
        }

        public boolean dynamometer() {
            return getBlockState().getBlock() instanceof GaugeBlock block && block.dynamometer;
        }

        public float needle() {
            return needle;
        }

        /** Face du cadran (null : disposition d'avant). */
        @Nullable
        public Direction dial() {
            return dial != null && dial.getAxis() != axis(getBlockState()) ? dial : null;
        }

        @Override
        public double shaftFactor(Direction face) {
            return face.getAxis() == axis(getBlockState()) ? 1 : 0;
        }

        @Override
        public double maxTorque() {
            return 8_000;
        }

        @Override
        public double maxSpeed() {
            return 400;
        }

        @Override
        protected void serverTick(ServerLevel level) {
            KineticNetwork net = network;
            float value = dynamometer() ? (float) Math.min(1.2, net == null ? 0 : net.loadRatio()) / 1.2F : (float) Math.min(1, Math.abs(speed) / 150.0);
            needle = value;
            int s = dynamometer() ? Math.min(15, (int) Math.round((net == null ? 0 : net.loadRatio()) * 14) + (net != null && net.machines() > 0 ? 1 : 0))
                                  : Math.min(15, (int) Math.ceil(Math.abs(speed) / 10.0));
            if (s != signal) {
                signal = s;
                level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            }
        }

        @Override
        protected boolean clientDirty() {
            if (Math.abs(needle - sentNeedle) > 0.02F) {
                sentNeedle = needle;
                return true;
            }
            return false;
        }

        @Override
        protected void writeClient(CompoundTag tag) {
            tag.putFloat("n", needle);
            if (dial != null) {
                tag.putByte("d", (byte) dial.get3DDataValue());
            }
        }

        @Override
        protected void readClient(CompoundTag tag) {
            needle = tag.getFloat("n");
            dial = tag.contains("d") ? Direction.from3DDataValue(tag.getByte("d")) : null;
        }

        @Override
        protected void saveAdditional(@NotNull CompoundTag tag) {
            super.saveAdditional(tag);
            if (dial != null) {
                tag.putByte("dial", (byte) dial.get3DDataValue());
            }
        }

        @Override
        public void load(@NotNull CompoundTag tag) {
            super.load(tag);
            dial = tag.contains("dial") ? Direction.from3DDataValue(tag.getByte("dial")) : null;
        }
    }
}
