package fr.pioneers.space.giant;

import fr.pioneers.registries.PiRobotics;
import fr.pioneers.registries.PiSounds;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.UnitTier;
import fr.pioneers.tile.TileEntityDroneBay;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Drone collecteur : il decolle de sa baie, vole jusqu'a une poche de gaz, l'aspire, puis rentre deposer sa cargaison.
 * Un drone blinde descend dans la couche des orages (poches riches, hydrogene metallique, pluies de diamants) ; un drone nu qui s'y
 * aventurerait serait foudroye. Les drones ne sont pas sauvegardes : la baie les considere comme rentres apres un rechargement.
 */
public class HarvesterDroneEntity extends Entity {

    public enum Phase {OUTBOUND, HARVEST, RETURN}

    private static final EntityDataAccessor<Byte> PHASE = SynchedEntityData.defineId(HarvesterDroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Boolean> ARMORED = SynchedEntityData.defineId(HarvesterDroneEntity.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Byte> CARGO_KIND = SynchedEntityData.defineId(HarvesterDroneEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Integer> CARGO = SynchedEntityData.defineId(HarvesterDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TARGET = SynchedEntityData.defineId(HarvesterDroneEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Byte> TIER = SynchedEntityData.defineId(HarvesterDroneEntity.class, EntityDataSerializers.BYTE);

    /** Debit d'aspiration (mB/tick pour un gaz ; un objet toutes les 10 ticks). */
    private static final int GAS_RATE = 40;
    private static final int MAX_FLIGHT = 3600;

    @Nullable
    private BlockPos bay;
    private int slot;
    private int flightTicks;

    public HarvesterDroneEntity(EntityType<? extends HarvesterDroneEntity> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(PHASE, (byte) 0);
        entityData.define(ARMORED, false);
        entityData.define(CARGO_KIND, (byte) 0);
        entityData.define(CARGO, 0);
        entityData.define(TARGET, -1);
        entityData.define(TIER, (byte) 0);
    }

    public void launch(BlockPos bay, int slot, boolean armored, GasPocketEntity target, UnitTier tier) {
        this.bay = bay.immutable();
        entityData.set(TIER, (byte) tier.ordinal());
        this.slot = slot;
        entityData.set(ARMORED, armored);
        setTarget(target);
        entityData.set(PHASE, (byte) Phase.OUTBOUND.ordinal());
    }

    private void setTarget(@Nullable GasPocketEntity target) {
        GasPocketEntity old = getTarget();
        if (old != null) {
            old.release(getId());
        }
        entityData.set(TARGET, target == null ? -1 : target.getId());
        if (target != null) {
            target.claim(getId());
        }
    }

    @Nullable
    public GasPocketEntity getTarget() {
        return level().getEntity(entityData.get(TARGET)) instanceof GasPocketEntity pocket && pocket.isAlive() ? pocket : null;
    }

    public Phase getPhase() {
        return Phase.values()[Mth.clamp(entityData.get(PHASE), 0, 2)];
    }

    private void setPhase(Phase phase) {
        entityData.set(PHASE, (byte) phase.ordinal());
    }

    /** Tier du drone robotique (vitesse, debit, emport ; rendu). */
    public UnitTier getTier() {
        return UnitTier.byIndex(entityData.get(TIER));
    }

    public boolean isArmored() {
        return entityData.get(ARMORED);
    }

    public int getCargo() {
        return entityData.get(CARGO);
    }

    public PocketKind getCargoKind() {
        return PocketKind.values()[Mth.clamp(entityData.get(CARGO_KIND), 0, PocketKind.values().length - 1)];
    }

    public int getSlot() {
        return slot;
    }

    @Nullable
    private TileEntityDroneBay bayTile() {
        if (bay == null || !level().isLoaded(bay)) {
            return null;
        }
        BlockEntity tile = level().getBlockEntity(bay);
        return tile instanceof TileEntityDroneBay droneBay ? droneBay : null;
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            clientEffects();
            return;
        }
        TileEntityDroneBay home = bayTile();
        if (home == null) {
            //Baie detruite : le drone tombe et redevient un objet
            spawnAtLocation(new ItemStack(PiRobotics.UNITS.get(UnitKind.DRONE).get(getTier()).get()));
            discard();
            return;
        }
        flightTicks++;
        if (tickCount % 30 == 0) {
            level().playSound(null, blockPosition(), PiSounds.DRONE_HUM.get(), SoundSource.NEUTRAL, 0.5F, isArmored() ? 0.85F : 1.1F);
        }
        //Foudre : un drone nu dans la couche des orages ne tient pas longtemps
        if (!isArmored() && PocketKind.Layer.at(getY()) == PocketKind.Layer.STORM && random.nextInt(60) == 0) {
            strike(home);
            return;
        }
        Phase phase = getPhase();
        GasPocketEntity target = getTarget();
        if ((flightTicks > MAX_FLIGHT || home.isRecalling()) && phase != Phase.RETURN) {
            setTarget(null);
            setPhase(Phase.RETURN);
            phase = Phase.RETURN;
        }
        switch (phase) {
            case OUTBOUND -> {
                if (target == null) {
                    GasPocketEntity next = getCargo() < capacity() ? home.findTarget(this) : null;
                    if (next != null) {
                        setTarget(next);
                    } else {
                        setPhase(Phase.RETURN);
                    }
                    return;
                }
                Vec3 approach = approachPoint(target, home);
                if (steer(approach, (isArmored() ? 0.38 : 0.45) * getTier().speed) < 0.8) {
                    setPhase(Phase.HARVEST);
                }
            }
            case HARVEST -> {
                if (target == null) {
                    setPhase(getCargo() >= capacity() ? Phase.RETURN : Phase.OUTBOUND);
                    return;
                }
                steer(approachPoint(target, home), 0.2);
                if (getCargo() > 0 && getCargoKind() != target.getKind()) {
                    setPhase(Phase.RETURN);
                    return;
                }
                entityData.set(CARGO_KIND, (byte) target.getKind().ordinal());
                int room = capacity() - getCargo();
                int itemTicks = Math.max(2, (int) Math.round(10 / getTier().work));
                int take = target.getKind().isGas() ? Math.min((int) Math.round(GAS_RATE * getTier().work), room) : tickCount % itemTicks == 0 ? Math.min(1, room) : 0;
                if (take > 0) {
                    entityData.set(CARGO, getCargo() + target.drain(take));
                }
                if (tickCount % 20 == 0) {
                    level().playSound(null, blockPosition(), PiSounds.POCKET_HARVEST.get(), SoundSource.NEUTRAL, 0.6F, 0.9F + random.nextFloat() * 0.2F);
                }
                if (getCargo() >= capacity() || !target.isAlive()) {
                    setTarget(null);
                    setPhase(Phase.RETURN);
                }
            }
            case RETURN -> {
                Vec3 dock = Vec3.atBottomCenterOf(bay).add(0, 1.1, 0);
                if (steer(dock, (isArmored() ? 0.4 : 0.48) * getTier().speed) < 0.6) {
                    home.dock(this);
                    level().playSound(null, bay, PiSounds.DRONE_DOCK.get(), SoundSource.BLOCKS, 0.8F, 1.0F);
                    discard();
                }
            }
        }
    }

    /** Capacite d'emport pour le contenu vise. */
    public int capacity() {
        GasPocketEntity target = getTarget();
        PocketKind kind = target != null ? target.getKind() : getCargoKind();
        return (int) Math.round(kind.tripCapacity(isArmored()) * getTier().capacity);
    }

    /** Point de travail : au bord de la bulle, cote baie. */
    private Vec3 approachPoint(GasPocketEntity target, TileEntityDroneBay home) {
        Vec3 center = target.position().add(0, target.getRadius(), 0);
        Vec3 toBay = Vec3.atCenterOf(home.getBlockPos()).subtract(center);
        Vec3 dir = toBay.lengthSqr() < 1e-4 ? new Vec3(0, 1, 0) : toBay.normalize();
        return center.add(dir.scale(target.getRadius() + 1.2));
    }

    /** Vole vers un point (acceleration douce) ; renvoie la distance restante. */
    private double steer(Vec3 to, double maxSpeed) {
        Vec3 delta = to.subtract(position());
        double distance = delta.length();
        Vec3 wanted = distance < 1e-3 ? Vec3.ZERO : delta.scale(Math.min(maxSpeed, distance * 0.25) / distance);
        Vec3 velocity = getDeltaMovement().add(wanted.subtract(getDeltaMovement()).scale(0.2));
        setDeltaMovement(velocity);
        setPos(getX() + velocity.x, getY() + velocity.y, getZ() + velocity.z);
        if (velocity.horizontalDistanceSqr() > 1e-4) {
            setYRot((float) (Mth.atan2(-velocity.x, velocity.z) * Mth.RAD_TO_DEG));
        }
        return distance;
    }

    /** Foudroye : le drone est perdu. */
    private void strike(TileEntityDroneBay home) {
        if (level() instanceof ServerLevel server) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(server);
            if (bolt != null) {
                bolt.moveTo(getX(), getY(), getZ());
                bolt.setVisualOnly(true);
                server.addFreshEntity(bolt);
            }
            server.sendParticles(ParticleTypes.EXPLOSION, getX(), getY(), getZ(), 3, 0.3, 0.3, 0.3, 0);
        }
        setTarget(null);
        home.lost(this);
        discard();
    }

    private void clientEffects() {
        //Feux de position et jet d'aspiration
        if (tickCount % 4 == 0) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, getX(), getY() - 0.1, getZ(), 0, -0.05, 0);
        }
        GasPocketEntity target = getTarget();
        if (getPhase() == Phase.HARVEST && target != null) {
            int c = target.getKind().color;
            Vector3f color = new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
            Vec3 from = target.position().add(0, target.getRadius(), 0);
            Vec3 to = position();
            for (int i = 0; i < 3; i++) {
                double t = random.nextDouble();
                Vec3 p = from.lerp(to, t);
                Vec3 v = to.subtract(from).scale(0.06);
                level().addParticle(new DustParticleOptions(color, 1.0F), p.x, p.y, p.z, v.x, v.y, v.z);
            }
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide) {
            GasPocketEntity target = getTarget();
            if (target != null) {
                target.release(getId());
            }
        }
        super.remove(reason);
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }
}
