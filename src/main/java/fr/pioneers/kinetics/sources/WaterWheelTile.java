package fr.pioneers.kinetics.sources;

import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticEvents;
import fr.pioneers.kinetics.KineticTile;
import fr.pioneers.kinetics.KineticsConfig;
import fr.pioneers.registries.PiKinetics;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.Vec3;

public class WaterWheelTile extends KineticTile {

    private double tau0;
    private boolean blocked;
    private int wetCells;
    private boolean sentBlocked;
    private int sentWet = -1;

    public WaterWheelTile(BlockPos pos, BlockState state) {
        super(PiKinetics.WATER_WHEEL_TILE.get(), pos, state);
    }

    public boolean large() {
        return getBlockState().getBlock() instanceof WaterWheelBlock block && block.large;
    }

    public int radius() {
        return large() ? 2 : 1;
    }

    public Direction.Axis axis() {
        return AxisKineticBlock.axis(getBlockState());
    }

    public boolean blocked() {
        return blocked;
    }

    public int wetCells() {
        return wetCells;
    }

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == axis() ? 1 : 0;
    }

    @Override
    public double sourceTorque() {
        return blocked ? 0 : tau0;
    }

    @Override
    public double sourceFreeSpeed() {
        return large() ? 12 : 16;
    }

    /**
     * 1.2.0 (retour alpha : « la roue tourne encore quand on retire l'eau ») : a sec, les aubes freinent (paliers, air) : la roue s'arrete
     * en 3 s environ au lieu de rouler presque sans fin sur son inertie.
     */
    @Override
    public double friction() {
        return wetCells == 0 || tau0 == 0 ? (large() ? 20 : 7) : super.friction();
    }

    @Override
    public double inertia() {
        return large() ? 180 : 60;
    }

    @Override
    public double maxTorque() {
        return 20_000;
    }

    @Override
    public double maxSpeed() {
        return 80;
    }

    @Override
    protected double renderRadius() {
        return radius() + 0.6;
    }

    @Override
    protected void serverTick(ServerLevel level) {
        if (level.getGameTime() % 20 == (worldPosition.asLong() & 15)) {
            scan(level);
        }
        if (wetCells > 0 && Math.abs(speed) > 0.5 && level.getGameTime() % 5 == 0) {
            level.sendParticles(ParticleTypes.SPLASH, worldPosition.getX() + 0.5, worldPosition.getY() - radius() + 0.4, worldPosition.getZ() + 0.5, 3,
                  0.6, 0.1, 0.6, 0.05);
        }
        if (tau0 != 0 && !blocked && Math.abs(speed) > 1 && level.getGameTime() % 200 == 0) {
            KineticEvents.award(level, worldPosition, "water_wheel");
        }
    }

    /**
     * Couple de l'eau sur les aubes : pour chaque case du disque de la roue contenant de l'eau qui coule, force = sens du courant ; couple
     * autour de l'axe = r x F. La chute d'eau (courant vers le bas) pese 2,5 fois plus.
     */
    private void scan(ServerLevel level) {
        Direction.Axis axis = axis();
        Vec3 a = axis == Direction.Axis.X ? new Vec3(1, 0, 0) : new Vec3(0, 0, 1);
        Vec3 up = new Vec3(0, 1, 0);
        Vec3 h = up.cross(a);                                     //(h, up, a) direct : h x up = a
        int r = radius();
        double total = 0;
        int wet = 0;
        boolean hit = false;
        for (int i = -r - 1; i <= r + 1; i++) {
            for (int j = -r - 1; j <= r + 1; j++) {
                if (i == 0 && j == 0 || i * i + j * j > (r + 0.6) * (r + 0.6)) {
                    continue;
                }
                BlockPos p = worldPosition.offset((int) Math.round(h.x * i), j, (int) Math.round(h.z * i));
                if (!level.isLoaded(p)) {
                    continue;
                }
                BlockState state = level.getBlockState(p);
                FluidState fluid = state.getFluidState();
                boolean ring = i * i + j * j > (r - 0.5) * (r - 0.5);
                if (!fluid.isEmpty()) {
                    if (fluid.is(FluidTags.WATER) && ring) {
                        Vec3 flow = fluid.getFlow(level, p);
                        boolean falling = flow.y < -0.1;
                        if (flow.lengthSqr() > 1e-4) {
                            double fh = flow.dot(h), fy = flow.y;
                            double torque = i * fy - j * fh;               //composante axiale de r x F
                            total += torque * (falling ? 2.5 : 1);
                            wet++;
                        }
                    }
                } else if (!state.isAir() && !state.canBeReplaced() && i * i + j * j <= r * r + 1) {
                    hit = true;
                }
            }
        }
        blocked = hit;
        wetCells = wet;
        double cap = (large() ? 8_000 : 2_400) * KineticsConfig.sourcePower();
        double perCell = (large() ? 420 : 600) * KineticsConfig.sourcePower();
        tau0 = Math.max(-cap, Math.min(cap, total * perCell));
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("kinetics.pioneers.info.water_wheel", wetCells, fmt(Math.abs(tau0))).withStyle(ChatFormatting.AQUA));
        if (blocked) {
            lines.add(Component.translatable("kinetics.pioneers.water_wheel.blocked").withStyle(ChatFormatting.RED));
        }
    }

    @Override
    protected boolean clientDirty() {
        boolean dirty = blocked != sentBlocked || (wetCells > 0) != (sentWet > 0);
        sentBlocked = blocked;
        sentWet = wetCells;
        return dirty;
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        tag.putBoolean("blocked", blocked);
        tag.putInt("wet", wetCells);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        blocked = tag.getBoolean("blocked");
        wetCells = tag.getInt("wet");
    }
}
