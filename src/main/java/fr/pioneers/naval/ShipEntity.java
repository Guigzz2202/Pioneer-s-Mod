package fr.pioneers.naval;

import fr.pioneers.Pioneers;
import fr.pioneers.core.fluid.Buckets;
import fr.pioneers.rail.train.Car;
import fr.pioneers.rail.train.CarType;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.DismountHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 0.20.11 : vue d'un navire (le vrai navire est dans {@link NavalWorld}) : creee la ou le chunk est charge, placee a chaque tick, jamais
 * sauvegardee. Boite = enveloppe du navire tourne (clic n'importe ou sur la coque). Comme les vehicules du rail, la pose complete arrive
 * dans un paquet par tick et le client la rejoue avec deux ticks de retard (mouvement regulier). Le ferry porte des voyageurs assis.
 */
public class ShipEntity extends Entity {

    private static final EntityDataAccessor<Byte> KIND = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Byte> TIER = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BYTE);
    /** bit 0 : feux allumes, bit 1 : en propulsion (fumee, sillage), bit 2 : a quai. */
    private static final EntityDataAccessor<Byte> FLAGS = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BYTE);
    private static final EntityDataAccessor<Float> FILL = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> YAW = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> ODO = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> SPEED = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> STAMP = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<BlockPos> ORIGIN = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Vector3f> OFFSET = SynchedEntityData.defineId(ShipEntity.class, EntityDataSerializers.VECTOR3);
    private static final double DELAY = 2;
    private static final int SAMPLES = 12;

    @Nullable
    public UUID ship;
    private long lastSync, lastHit;
    private int hits;
    private final int[] sStamp = new int[SAMPLES];
    private final double[][] sPose = new double[SAMPLES][5];
    private int sCount, sHead, lastStamp = Integer.MIN_VALUE;
    private double play;
    public float yawO, yawR, odoO, odoR;
    private boolean first = true;

    public ShipEntity(EntityType<?> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }

    @Override
    protected void defineSynchedData() {
        entityData.define(KIND, (byte) 0);
        entityData.define(TIER, (byte) 0);
        entityData.define(FLAGS, (byte) 0);
        entityData.define(FILL, 0F);
        entityData.define(YAW, 0F);
        entityData.define(ODO, 0F);
        entityData.define(SPEED, 0F);
        entityData.define(STAMP, 0);
        entityData.define(ORIGIN, BlockPos.ZERO);
        entityData.define(OFFSET, new Vector3f());
    }

    public ShipKind kind() {
        return ShipKind.byId(entityData.get(KIND));
    }

    public int tier() {
        return Mth.clamp(entityData.get(TIER), 0, 3);
    }

    public int flags() {
        return entityData.get(FLAGS) & 0xFF;
    }

    public float fill() {
        return entityData.get(FILL);
    }

    public float speed() {
        return entityData.get(SPEED);
    }

    /** Cap synchronise (serveur) ou rejoue (client). */
    public float heading() {
        return level().isClientSide ? yawR : entityData.get(YAW);
    }

    /** Serveur : pose et etat, a chaque tick. */
    public void sync(NavalWorld w, Ship s, long now) {
        lastSync = now;
        ship = s.id;
        entityData.set(KIND, (byte) s.kind.ordinal());
        entityData.set(TIER, (byte) s.tier);
        long time = level().getDayTime() % 24000;
        boolean night = time > 12500 && time < 23500 || level().isRaining();
        int flags = (night ? 1 : 0) | (s.effort > 0.05F ? 2 : 0) | (s.state == Ship.State.DOCKED ? 4 : 0);
        entityData.set(FLAGS, (byte) flags);
        entityData.set(FILL, s.fill());
        entityData.set(YAW, s.heading);
        entityData.set(ODO, s.odometer % 10_000F);
        entityData.set(SPEED, s.speed);
        if (!Double.isFinite(s.x) || !Double.isFinite(s.y) || !Double.isFinite(s.z)) {
            return;
        }
        BlockPos origin = entityData.get(ORIGIN);
        if (first || Math.abs(s.x - origin.getX()) > 2048 || Math.abs(s.z - origin.getZ()) > 2048 || Math.abs(s.y - origin.getY()) > 512) {
            origin = BlockPos.containing(s.x, s.y, s.z);
            entityData.set(ORIGIN, origin);
            first = false;
        }
        entityData.set(OFFSET, new Vector3f((float) (s.x - origin.getX()), (float) (s.y - origin.getY()), (float) (s.z - origin.getZ())));
        entityData.set(STAMP, (int) now);
        setYRot(s.heading);
        setPos(s.x, s.y, s.z);
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        if (!level().isClientSide) {
            ejectSafely();
        }
        super.remove(reason);
    }

    public void ejectSafely() {
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
    public void tick() {
        if (level().isClientSide) {
            yawO = yawR;
            odoO = odoR;
            receive();
            if (sCount > 0) {
                int newest = sStamp[Math.floorMod(sHead - 1, SAMPLES)];
                double target = newest - DELAY;
                if (first) {
                    play = target;
                } else {
                    play += 1;
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
                    odoO = (float) p[4];
                    first = false;
                }
                yawR = yawO + Mth.wrapDegrees((float) p[3] - yawO);
                odoR = (float) p[4];
                setYRot(yawR);
                if (Math.abs(odoR - odoO) > 5_000) {
                    odoO = odoR;
                }
            }
            NavalClientHooks.tick(this);
            return;
        }
        if (level().getGameTime() - lastSync > 3) {
            discard();
            return;
        }
        if (level() instanceof ServerLevel sl && ship != null && tickCount % 5 == 0) {
            Ship s = NavalWorld.get(sl).ship(ship);
            if (s != null) {
                wake(sl, s);
            }
        }
    }

    /** 0.20.25 : plantes aquatiques cassees par la coque (kelp, herbiers, nenuphars : tag {@code pioneers:ship_breakable}). */
    public static final TagKey<Block> BREAKABLE = TagKey.create(Registries.BLOCK, Pioneers.rl("ship_breakable"));
    private static ResourceKey<DamageType> shipDamage;

    public static ResourceKey<DamageType> shipDamage() {
        if (shipDamage == null) {
            shipDamage = ResourceKey.create(Registries.DAMAGE_TYPE, Pioneers.rl("ship"));
        }
        return shipDamage;
    }

    /**
     * 0.20.25 : sillage du navire, toutes les 5 ticks : la coque (longueur x bau, de 2 blocs sous la surface jusqu'au-dessus) casse les plantes
     * aquatiques sur son passage ; les creatures sous la coque ou contre elle sont repoussees sur le cote et, si le navire avance, blessees.
     */
    private void wake(ServerLevel sl, Ship s) {
        double fx = s.forwardX(), fz = s.forwardZ();
        double half = ShipKind.length(s.tier) / 2.0, side = ShipKind.beam(s.tier) / 2.0;
        int surface = (int) Math.floor(s.y - 0.875);
        double reach = half + side + 1;
        boolean moving = s.speed > 0.01F;
        if (moving) {
            BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
            for (int x = (int) Math.floor(s.x - reach); x <= (int) Math.floor(s.x + reach); x++) {
                for (int z = (int) Math.floor(s.z - reach); z <= (int) Math.floor(s.z + reach); z++) {
                    double dx = x + 0.5 - s.x, dz = z + 0.5 - s.z;
                    if (Math.abs(dx * fx + dz * fz) > half + 0.5 || Math.abs(dx * fz - dz * fx) > side + 0.5) {
                        continue;
                    }
                    for (int y = surface - 2; y <= surface + 1; y++) {
                        p.set(x, y, z);
                        if (sl.isLoaded(p) && sl.getBlockState(p).is(BREAKABLE)) {
                            sl.destroyBlock(p, false);
                        }
                    }
                }
            }
        }
        AABB box = new AABB(s.x - reach, surface - 3, s.z - reach, s.x + reach, s.y + 0.3, s.z + reach);
        float bps = s.speed * 20;
        for (LivingEntity victim : sl.getEntitiesOfClass(LivingEntity.class, box)) {
            if (victim.isSpectator() || !victim.isAlive() || victim.getVehicle() instanceof ShipEntity
                || victim instanceof Player pl && pl.getAbilities().instabuild) {
                continue;
            }
            double dx = victim.getX() - s.x, dz = victim.getZ() - s.z;
            double along = dx * fx + dz * fz, across = dx * fz - dz * fx;
            double r = victim.getBbWidth() / 2;
            if (Math.abs(along) > half + r || Math.abs(across) > side + r) {
                continue;
            }
            //Pousse vers le bord le plus proche (dehors de la coque), un peu vers l'arriere si le navire avance
            double sign = across >= 0 ? 1 : -1;
            Vec3 out = new Vec3(fz * sign, 0, -fx * sign);
            if (moving) {
                DamageSource src = new DamageSource(sl.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(shipDamage()), this);
                victim.hurt(src, 2 + s.tier + bps * 0.6F);
            }
            Vec3 kick = out.scale(0.55 + bps * 0.05).add(fx * -0.15, 0.15, fz * -0.15);
            victim.setDeltaMovement(kick);
            victim.hurtMarked = true;
        }
    }

    @Override
    public void lerpTo(double x, double y, double z, float yRot, float xRot, int steps, boolean teleport) {
    }

    private void receive() {
        int stamp = entityData.get(STAMP);
        if (stamp == lastStamp) {
            return;
        }
        if (sCount > 0 && stamp < lastStamp) {
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
            double prev = sPose[Math.floorMod(sHead - 1, SAMPLES)][3];
            yaw = (float) (prev + Mth.wrapDegrees(yaw - prev));
        }
        p[3] = yaw;
        float odo = entityData.get(ODO);
        if (sCount > 0) {
            double prev = sPose[Math.floorMod(sHead - 1, SAMPLES)][4];
            if (Math.abs(odo - prev) > 5_000) {
                odo = (float) (prev + (odo - prev - Math.signum(odo - prev) * 10_000));
            }
        }
        p[4] = odo;
        sStamp[sHead] = stamp;
        sHead = (sHead + 1) % SAMPLES;
        sCount = Math.min(SAMPLES, sCount + 1);
    }

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
        double[] out = new double[5];
        for (int i = 0; i < 5; i++) {
            out[i] = a[i] + (b[i] - a[i]) * k;
        }
        return out;
    }

    // ------------------------------------------------------------------ boite : enveloppe du navire tourne

    @NotNull
    @Override
    protected AABB makeBoundingBox() {
        int t = tier();
        double hl = ShipKind.length(t) / 2.0, hw = ShipKind.beam(t) / 2.0;
        double a = Math.toRadians(getYRot());
        double ex = Math.abs(Math.sin(a)) * hl + Math.abs(Math.cos(a)) * hw, ez = Math.abs(Math.cos(a)) * hl + Math.abs(Math.sin(a)) * hw;
        double y0 = getY() - ShipKind.draft(t) / 16.0, y1 = getY() + ShipKind.freeboard(t) / 16.0 + 0.6;
        return new AABB(getX() - ex, y0, getZ() - ez, getX() + ex, y1, getZ() + ez);
    }

    @NotNull
    @Override
    public AABB getBoundingBoxForCulling() {
        return getBoundingBox().inflate(1, 3, 1);
    }

    @Override
    public boolean isPickable() {
        return !isRemoved();
    }

    @Override
    public boolean canBeCollidedWith() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @NotNull
    @Override
    public InteractionResult interact(@NotNull Player player, @NotNull InteractionHand hand) {
        if (level().isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (ship != null && player instanceof ServerPlayer sp && level() instanceof ServerLevel sl) {
            NavalWorld w = NavalWorld.get(sl);
            Ship s = w.ship(ship);
            if (s != null && Buckets.isWaterBucket(player.getItemInHand(hand))) {
                fillWater(sp, hand, w, s);
                return InteractionResult.CONSUME;
            }
            if (s != null) {
                NavalNetworking.openShip(sp, w, s);
                return InteractionResult.CONSUME;
            }
        }
        return InteractionResult.PASS;
    }

    /** 0.20.22 : un seau d'eau verse 1 000 mB dans la reserve d'eau de la machine a vapeur (soute d'abord), le seau revient vide. */
    private void fillWater(ServerPlayer player, InteractionHand hand, NavalWorld w, Ship s) {
        Car target = null;
        for (List<Car> group : List.of(s.engines, s.holds)) {
            for (Car c : group) {
                if (c.waterCapacity() > c.water && (target == null || c.type == CarType.TENDER)) {
                    target = c;
                }
            }
        }
        if (target == null) {
            boolean steam = s.engines.stream().anyMatch(c -> c.waterCapacity() > 0);
            player.displayClientMessage(Component.translatable(steam ? "naval.pioneers.water.full" : "naval.pioneers.water.none"), true);
            return;
        }
        if (!Buckets.drainHeld(player, hand, Fluids.WATER)) {
            return;
        }
        target.water = Math.min(target.waterCapacity(), target.water + Buckets.VOLUME);
        level().playSound(null, blockPosition(), SoundEvents.BUCKET_EMPTY, SoundSource.NEUTRAL, 1, 1);
        player.displayClientMessage(Component.translatable("naval.pioneers.water.added", target.water, target.waterCapacity()), true);
        w.setDirty();
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        if (level().isClientSide || isRemoved()) {
            return false;
        }
        if (source.getEntity() instanceof ServerPlayer player && ship != null && level() instanceof ServerLevel sl) {
            long now = level().getGameTime();
            hits = now - lastHit > 60 ? 1 : hits + 1;
            lastHit = now;
            if (hits >= 3 || player.getAbilities().instabuild) {
                NavalNetworking.pickUp(player, NavalWorld.get(sl), this);
            }
            return true;
        }
        return false;
    }

    @Override
    public ItemStack getPickResult() {
        return ShipItem.stack(kind(), tier());
    }

    // ------------------------------------------------------------------ voyageurs (ferry)

    @Override
    protected boolean canAddPassenger(@NotNull Entity passenger) {
        return getPassengers().size() < kind().seats(tier());
    }

    /** Place {@code i} : rangees de deux sur le pont, de l'arriere vers l'avant (blocs, repere du navire : x en travers, z vers l'avant). */
    public static double[] seat(int tier, int i) {
        double hl = ShipKind.length(tier) / 2.0;
        int rows = Math.max(1, ShipKind.holds(tier));
        double z0 = -hl * 0.3, z1 = hl * 0.45;
        double z = z0 + (z1 - z0) * ((i / 2) + 0.5) / rows;
        double x = (i % 2 == 0 ? -1 : 1) * ShipKind.beam(tier) * 0.22;
        return new double[]{x, z};
    }

    @Override
    public double getPassengersRidingOffset() {
        return ShipKind.freeboard(tier()) / 16.0 + 0.1;
    }

    @Override
    protected void positionRider(@NotNull Entity passenger, @NotNull MoveFunction move) {
        if (!hasPassenger(passenger)) {
            return;
        }
        int seat = getPassengers().indexOf(passenger);
        double[] p = seat(tier(), seat);
        double a = Math.toRadians(heading());
        double fx = Math.sin(a), fz = Math.cos(a);
        //Repere : avant (fx, fz), cote (fz, -fx)
        double x = getX() + fx * p[1] + fz * p[0], z = getZ() + fz * p[1] - fx * p[0];
        move.accept(passenger, x, getY() + getPassengersRidingOffset() + passenger.getMyRidingOffset(), z);
    }

    @NotNull
    @Override
    public Vec3 getDismountLocationForPassenger(@NotNull LivingEntity passenger) {
        Vec3 here = position();
        if (!Double.isFinite(here.x) || here.y < level().getMinBuildHeight()) {
            return passenger.position();
        }
        //A quai : sur le quai (cote du port) ; sinon le plus pres possible au sec, sinon sur le pont
        double a = Math.toRadians(heading());
        Vec3 side = new Vec3(Math.cos(a), 0, -Math.sin(a));
        double off = ShipKind.beam(tier()) / 2.0 + 0.9;
        for (double k : new double[]{off, -off, off + 1, -off - 1}) {
            Vec3 at = here.add(side.scale(k));
            for (int dy = 0; dy <= 2; dy++) {
                Vec3 v = DismountHelper.findSafeDismountLocation(passenger.getType(), level(), BlockPos.containing(at.x, here.y + dy, at.z), true);
                if (v != null) {
                    return v;
                }
            }
        }
        return here.add(0, getPassengersRidingOffset() + 0.2, 0);
    }

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return null;
    }

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
