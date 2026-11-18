package fr.pioneers.logistics.rail.train;

import fr.pioneers.logistics.rail.RailWorld;
import java.util.ArrayList;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 0.20.0 : vue d'un vehicule dans le monde. Le vrai vehicule est dans {@link RailWorld} (il roule aussi dans les chunks non charges) ;
 * cette entite n'est creee que quand son chunk est charge, placee a chaque tick par le reseau, et jamais sauvegardee.
 * <p>
 * Client (0.20.1) : la pose complete (heure du serveur, position, cap, tangage, compteur) arrive dans un seul paquet par tick ; elle est
 * rangee dans un anneau puis rejouee avec {@link #DELAY} ticks de retard, interpolee entre deux poses : le mouvement reste regulier meme si
 * les paquets arrivent par a-coups.
 */
public class RailCarEntity extends Entity {

    private static final EntityDataAccessor<Byte> TYPE = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> TIER = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.BYTE);
    /** bit 0 : en traction (fumee), bit 1 : phares, bit 2 : en marche arriere. */
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> PITCH = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ODO = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> FILL = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> LOAD = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.INT);
    /** Pose complete dans un seul paquet par tick : heure du serveur, origine (bloc) et ecart (flottants). */
    private static final EntityDataAccessor<Integer> STAMP = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> ORIGIN = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> OFFSET = SynchedEntityData.defineId(RailCarEntity.class, EntityDataSerializers.VECTOR3);
    /** Places de la voiture voyageurs (px, comme {@code CarModels.SEAT_Y/SEAT_Z} : code commun, sans classe client). */
    private static final double SEAT_Y = 3.6 + 5.2;
    private static final double[] SEAT_Z = {4.6, 11.0};
    /** Retard de lecture (ticks) : absorbe l'irregularite des paquets. */
    private static final double DELAY = 2;
    private static final int SAMPLES = 12;

    @Nullable
    public UUID train, car;
    private long lastSync, lastHit;
    private int hits;
    //Lecture client : poses recues (anneau), horloge de lecture en ticks du serveur
    private final int[] sStamp = new int[SAMPLES];
    private final double[][] sPose = new double[SAMPLES][6];
    private int sCount, sHead, lastStamp = Integer.MIN_VALUE;
    private double play;
    public float yawO, yawR, pitchO, pitchR, odoO, odoR;
    private boolean first = true;

    public RailCarEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(TYPE, (byte) 0);
        entityData.define(TIER, (byte) 0);
        entityData.define(FLAGS, (byte) 0);
        entityData.define(YAW, 0F);
        entityData.define(PITCH, 0F);
        entityData.define(ODO, 0F);
        entityData.define(FILL, 0F);
        entityData.define(LOAD, 0);
        entityData.define(STAMP, 0);
        entityData.define(ORIGIN, BlockPos.ZERO);
        entityData.define(OFFSET, new Vector3f());
    }

    public CarType carType() {
        return CarType.byId(entityData.get(TYPE));
    }

    public int tier() {
        return entityData.get(TIER);
    }

    public boolean working() {
        return (entityData.get(FLAGS) & 1) != 0;
    }

    /** Etat synchronise : bit 0 en traction, bit 1 feux, bit 2 marche arriere, bit 3 sous tension (pantographe leve). */
    public int flags() {
        return entityData.get(FLAGS) & 0xFF;
    }

    public float fill() {
        return entityData.get(FILL);
    }

    /** Objet charge (tremie : couleur du tas), id brut du registre. */
    public int load() {
        return entityData.get(LOAD);
    }

    /** Serveur : pose et etat, a chaque tick. */
    public void sync(Train t, Car c, Train.Pose pose, long now, int load) {
        lastSync = now;
        train = t.id;
        car = c.id;
        entityData.set(TYPE, (byte) c.type.ordinal());
        entityData.set(TIER, (byte) c.tier);
        //bit 3 : sous tension (pantographe leve) ; bit 4 : echappement propre (hydrogene)
        int flags = (t.effort > 0.05F ? 1 : 0) | (c.type.locomotive && (t.auto || t.driverIdle < 40) ? 2 : 0) | (t.speed < 0 ? 4 : 0)
              | (c.electrified ? 8 : 0) | (c.type == CarType.GAS_LOCO && RailFuels.clean(c.gas) ? 16 : 0);
        entityData.set(FLAGS, (byte) flags);
        entityData.set(YAW, pose.yaw());
        entityData.set(PITCH, pose.pitch());
        entityData.set(ODO, (c.flipped ? -t.odometer : t.odometer) % 10_000F);
        entityData.set(FILL, c.fill());
        entityData.set(LOAD, load);
        Vec3 at = pose.center();
        if (!Double.isFinite(at.x) || !Double.isFinite(at.y) || !Double.isFinite(at.z)) {
            return;
        }
        BlockPos origin = entityData.get(ORIGIN);
        if (first || Math.abs(at.x - origin.getX()) > 2048 || Math.abs(at.z - origin.getZ()) > 2048 || Math.abs(at.y - origin.getY()) > 512) {
            origin = BlockPos.containing(at);
            entityData.set(ORIGIN, origin);
            first = false;
        }
        entityData.set(OFFSET, new Vector3f((float) (at.x - origin.getX()), (float) (at.y - origin.getY()), (float) (at.z - origin.getZ())));
        entityData.set(STAMP, (int) now);
        setPos(at.x, at.y, at.z);
        setYRot(pose.yaw());
    }

    public long lastSync() {
        return lastSync;
    }

    /** Serveur : vue gardee en vie sans bouger (pose du train invalide : voie cassee sous lui). */
    public void keepAlive(long now) {
        lastSync = now;
    }

    /**
     * 0.20.4 : fait descendre les passagers a un endroit sur, et place le joueur explicitement : sans cela, le client garde la position
     * qu'il devine lui-meme (vehicule deja retire de son cote).
     */
    public void ejectSafely() {
        if (level().isClientSide) {
            return;
        }
        for (Entity p : new ArrayList<>(getPassengers())) {
            Vec3 to = p instanceof LivingEntity le ? getDismountLocationForPassenger(le) : position().add(0, 1, 0);
            p.stopRiding();
            if (p instanceof ServerPlayer sp) {
                sp.connection.teleport(to.x, to.y, to.z, sp.getYRot(), sp.getXRot());
            } else {
                p.teleportTo(to.x, to.y, to.z);
            }
        }
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide) {
            ejectSafely();
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        if (level().isClientSide) {
            yawO = yawR;
            pitchO = pitchR;
            odoO = odoR;
            receive();
            if (sCount > 0) {
                int newest = sStamp[Math.floorMod(sHead - 1, SAMPLES)];
                double target = newest - DELAY;
                if (first) {
                    play = target;
                } else {
                    play += 1;
                    //Horloge recalee doucement sur le serveur ; grand ecart (pause, retard) : on saute
                    double err = target - play;
                    play = Math.abs(err) > 8 ? target : play + err * 0.08;
                }
                double[] p = sample(play);
                setPos(p[0], p[1], p[2]);
                if (first) {
                    xo = p[0];
                    yo = p[1];
                    zo = p[2];
                    yawO = (float) p[3];
                    pitchO = (float) p[4];
                    odoO = (float) p[5];
                    first = false;
                }
                //Cap : rester continu (pas de saut de 360)
                yawR = yawO + Mth.wrapDegrees((float) p[3] - yawO);
                pitchR = (float) p[4];
                odoR = (float) p[5];
                setYRot(yawR);
                if (Math.abs(odoR - odoO) > 5_000) {
                    //Le compteur est repasse a zero (modulo) : pas de saut d'animation
                    odoO = odoR;
                }
            }
            RailClientHooks.tick(this);
            return;
        }
        //Serveur : vue orpheline (train retire, chunk du train plus suivi) : on disparait
        if (level().getGameTime() - lastSync > 3) {
            discard();
        }
    }

    /** La position vient de la pose synchronisee ({@link #receive}) : on ignore les paquets de deplacement. */
    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
    }

    /** Client : nouvelle pose recue (une par tick du serveur, dans un seul paquet). */
    private void receive() {
        int stamp = entityData.get(STAMP);
        if (stamp == lastStamp) {
            return;
        }
        if (sCount > 0 && stamp < lastStamp) {
            //Horloge du serveur revenue en arriere (commande /time, autre monde) : on repart de zero
            sCount = 0;
            first = true;
        }
        lastStamp = stamp;
        BlockPos o = entityData.get(ORIGIN);
        Vector3f d = entityData.get(OFFSET);
        double[] p = sPose[sHead];
        p[0] = o.getX() + d.x();
        p[1] = o.getY() + d.y();
        p[2] = o.getZ() + d.z();
        float yaw = entityData.get(YAW);
        if (sCount > 0) {
            //Cap deroule par rapport a la pose precedente (interpolation sans saut de 360)
            double prev = sPose[Math.floorMod(sHead - 1, SAMPLES)][3];
            yaw = (float) (prev + Mth.wrapDegrees(yaw - prev));
        }
        p[3] = yaw;
        p[4] = entityData.get(PITCH);
        float odo = entityData.get(ODO);
        if (sCount > 0) {
            double prev = sPose[Math.floorMod(sHead - 1, SAMPLES)][5];
            if (Math.abs(odo - prev) > 5_000) {
                //Compteur repasse a zero : on decale tout l'anneau pour rester continu
                double shift = odo - prev - Math.signum(odo - prev) * 10_000;
                odo = (float) (prev + shift);
            }
        }
        p[5] = odo;
        sStamp[sHead] = stamp;
        sHead = (sHead + 1) % SAMPLES;
        sCount = Math.min(SAMPLES, sCount + 1);
    }

    /** Pose a l'heure {@code t} (ticks du serveur) : interpolee entre deux poses recues, prolongee de 2 ticks au plus. */
    private double[] sample(double t) {
        int newest = Math.floorMod(sHead - 1, SAMPLES);
        int oldest = Math.floorMod(sHead - sCount, SAMPLES);
        if (sCount == 1 || t <= sStamp[oldest]) {
            return sPose[sCount == 1 ? newest : oldest].clone();
        }
        if (t >= sStamp[newest]) {
            int before = Math.floorMod(newest - 1, SAMPLES);
            int span = Math.max(1, sStamp[newest] - sStamp[before]);
            double k = Math.min(2, t - sStamp[newest]) / span;
            return mix(sPose[newest], sPose[before], -k);
        }
        for (int i = 1; i < sCount; i++) {
            int b = Math.floorMod(newest - i + 1, SAMPLES), a = Math.floorMod(newest - i, SAMPLES);
            if (sStamp[a] <= t) {
                double k = (t - sStamp[a]) / Math.max(1, sStamp[b] - sStamp[a]);
                return mix(sPose[a], sPose[b], k);
            }
        }
        return sPose[oldest].clone();
    }

    private static double[] mix(double[] a, double[] b, double k) {
        double[] out = new double[6];
        for (int i = 0; i < 6; i++) {
            out[i] = a[i] + (b[i] - a[i]) * k;
        }
        return out;
    }

    /** Le maillage fait un bloc entier (et plus pour la cheminee) : pas de disparition au bord de l'ecran. */
    @NotNull
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(0.6, 0.5, 0.6);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean isPushable() {
        return true;
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public void push(@NotNull Entity other) {
        if (!level().isClientSide && other instanceof Player player && train != null && level() instanceof ServerLevel sl) {
            RailWorld.get(sl).pushedBy(train, car, player);
        }
    }

    @NotNull
    @Override
    public InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (train != null && player instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
            return RailWorld.get(sl).interact(sp, this, hand);
        }
        return InteractionResult.PASS;
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player && train != null && level() instanceof ServerLevel sl) {
            //Trois coups (un seul en creatif) pour ramasser, comme un wagonnet
            long now = level().getGameTime();
            hits = now - lastHit > 60 ? 1 : hits + 1;
            lastHit = now;
            if (hits >= 3 || player.getAbilities().instabuild) {
                RailWorld.get(sl).pickUp(player, this);
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return RailItems.stack(carType(), tier());
    }

    // ------------------------------------------------------------------ passagers

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return getPassengers().size() < carType().seats();
    }

    @Override
    public double getPassengersRidingOffset() {
        return carType() == CarType.STEAM ? 0.42 : carType() == CarType.PASSENGER ? SEAT_Y / 16.0 - 0.05 : 0.35;
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        //Conducteur : dans la cabine (arriere de la locomotive a vapeur) ; draisine : de part et d'autre du balancier
        float yaw = (float) Math.toRadians(level().isClientSide ? yawR : entityData.get(YAW));
        int seat = getPassengers().indexOf(passenger);
        double back = switch (carType()) {
            case STEAM -> -0.30;
            case DRAISINE -> seat == 0 ? -0.22 : 0.22;
            //Voiture voyageurs : les deux banquettes (repere du maillage : avant vers +z, centre a 8 px)
            case PASSENGER -> (SEAT_Z[Math.max(0, Math.min(1, seat))] - 8) / 16.0;
            default -> 0;
        };
        double x = getX() + Math.sin(yaw) * back, z = getZ() + Math.cos(yaw) * back;
        move.accept(passenger, x, getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset(), z);
    }

    @NotNull
    @Override
    public Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        Vec3 here = position();
        if (!Double.isFinite(here.x) || !Double.isFinite(here.y) || !Double.isFinite(here.z) || here.y < level().getMinBuildHeight()) {
            return passenger.position();
        }
        float yaw = (float) Math.toRadians(entityData.get(YAW));
        //Descendre a cote de la voie (d'un cote, sinon de l'autre), sinon au-dessus du vehicule : jamais dans un bloc
        Vec3 side = new Vec3(Math.cos(yaw), 0, -Math.sin(yaw));
        for (double k : new double[]{1.1, -1.1}) {
            Vec3 at = here.add(side.scale(k));
            for (int dy = 0; dy <= 1; dy++) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), BlockPos.containing(at.x, here.y + 0.3 + dy, at.z), true);
                if (v != null) {
                    return v;
                }
            }
        }
        Vec3 up = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), BlockPos.containing(here.x, here.y + 0.3, here.z).above(), true);
        return up != null ? up : here.add(0, 1.2, 0);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

    // ------------------------------------------------------------------ jamais sauvegardee

    @Override
    protected void readAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    protected void addAdditionalSaveData(@NotNull CompoundTag tag) {
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
