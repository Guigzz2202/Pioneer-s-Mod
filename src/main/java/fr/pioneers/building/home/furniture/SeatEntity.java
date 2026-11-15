package fr.pioneers.building.home.furniture;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 : siege invisible d'une {@link ChairBlock} (comme les mods de meubles) : sans modele, sans collision, jamais
 * sauvegarde ; disparait des que personne n'est assis ou que la chaise n'est plus la. Le passager regarde le cote de la chaise (rotation
 * bornee a ±100 degres comme dans un bateau) ; il se leve devant la chaise.
 */
public class SeatEntity extends Entity {

    /** Hauteur du siege au-dessus du bas du bloc : le joueur assis (decale de -0,35 par Minecraft) a les hanches sur l'assise. */
    public static final double HEIGHT = 0.3;

    public SeatEntity(EntityType<? extends SeatEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (!level().isClientSide && (getPassengers().isEmpty() || !(level().getBlockState(blockPosition()).getBlock() instanceof ChairBlock))) {
            ejectPassengers();
            discard();
        }
    }

    @Override
    public double getPassengersRidingOffset() {
        return 0;
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction move) {
        super.positionRider(passenger, move);
        clamp(passenger);
    }

    @Override
    public void onPassengerTurned(@NotNull Entity passenger) {
        clamp(passenger);
    }

    private void clamp(Entity passenger) {
        passenger.setYBodyRot(getYRot());
        float d = net.minecraft.util.Mth.wrapDegrees(passenger.getYRot() - getYRot());
        float c = net.minecraft.util.Mth.clamp(d, -100F, 100F);
        passenger.yRotO += c - d;
        passenger.setYRot(passenger.getYRot() + c - d);
        passenger.setYHeadRot(passenger.getYRot());
    }

    @NotNull
    @Override
    public Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        BlockPos chair = blockPosition();
        Direction front = Direction.fromYRot(getYRot());
        for (BlockPos p : new BlockPos[]{chair.relative(front), chair.relative(front.getClockWise()), chair.relative(front.getCounterClockWise()),
                                         chair.relative(front.getOpposite())}) {
            Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), p, true);
            if (v != null) {
                return v;
            }
        }
        return Vec3.atBottomCenterOf(chair.above());
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }
}
