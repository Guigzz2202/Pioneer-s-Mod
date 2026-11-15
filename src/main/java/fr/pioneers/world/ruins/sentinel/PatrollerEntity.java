package fr.pioneers.world.ruins.sentinel;

import fr.pioneers.core.profile.RpSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : Patrouilleur, drone volant (0,6 bloc), tir faible a {@code range} blocs. Endormi, il fait sa ronde en anneau
 * autour de la ruine (centre = Controleur delabre du site, rayon = distance de son poste), voyants eteints, sans recherche de chemin : vol
 * dirige a la main. Il repere a 16 blocs (8 accroupi). MKII-III : donne l'alerte a tout le site ({@code alarm}) ; MKIII : esquive les
 * projectiles ({@code dodge}). En Veille il se pose en vol stationnaire. Etourdi, il tombe.
 */
public class PatrollerEntity extends SentinelEntity {

    private int spin = 1;
    private int fireCooldown;
    private float bearing;

    public PatrollerEntity(EntityType<? extends PatrollerEntity> type, Level level) {
        super(type, level);
        setNoGravity(true);
        spin = level.random.nextBoolean() ? 1 : -1;
        bearing = level.random.nextFloat() * Mth.TWO_PI;
    }

    @Override
    public SentinelRole role() {
        return SentinelRole.PATROLLER;
    }

    @Override
    protected boolean dormantImmobile() {
        return false;
    }

    /** Centre de la ronde : le Controleur delabre du site (le poste s'il n'y a pas de site). */
    private BlockPos center() {
        return site != 0 ? BlockPos.of(site) : post;
    }

    private double ringRadius() {
        BlockPos c = center();
        double dx = post.getX() - c.getX(), dz = post.getZ() - c.getZ();
        return Mth.clamp(Math.sqrt(dx * dx + dz * dz), stats().get("ring_min", 6), stats().get("ring_max", 24));
    }

    @Override
    protected Vec3 leashAnchor() {
        return Vec3.atBottomCenterOf(center());
    }

    /** Sur son anneau, a la hauteur de son poste. */
    @Override
    protected boolean isHome() {
        BlockPos c = center();
        double dx = getX() - c.getX() - 0.5, dz = getZ() - c.getZ() - 0.5;
        double r = Math.sqrt(dx * dx + dz * dz);
        return Math.abs(r - ringRadius()) <= 2.5 && Math.abs(getY() - post.getY()) <= 2.5;
    }

    // ------------------------------------------------------------------ vol
    private void steer(Vec3 goal, double speed) {
        Vec3 d = goal.subtract(position());
        double len = d.length();
        Vec3 want = len < 1.0E-3 ? Vec3.ZERO : d.scale(Math.min(speed, len * 0.25) / len);
        Vec3 v = getDeltaMovement().scale(0.8).add(want.scale(0.2));
        if (horizontalCollision) {
            v = v.add(0, 0.08, 0);
        }
        setDeltaMovement(v);
    }

    private void face(double x, double z) {
        float yaw = (float) (Mth.atan2(z - getZ(), x - getX()) * Mth.RAD_TO_DEG) - 90.0F;
        setYRot(Mth.approachDegrees(getYRot(), yaw, 12));
        yBodyRot = getYRot();
        yHeadRot = getYRot();
    }

    private void ring(double speed, double lift) {
        BlockPos c = center();
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5, rr = ringRadius();
        double a = Math.atan2(getZ() - cz, getX() - cx) + spin * Math.max(0.15, speed * 6 / rr);
        Vec3 goal = new Vec3(cx + Math.cos(a) * rr, post.getY() + lift + Math.sin(tickCount * 0.05) * 0.3, cz + Math.sin(a) * rr);
        steer(goal, speed);
        Vec3 v = getDeltaMovement();
        if (v.horizontalDistanceSqr() > 1.0E-4) {
            face(getX() + v.x, getZ() + v.z);
        }
    }

    @Override
    protected void dormantTick(ServerLevel sl) {
        if (standby) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }
        ring(stats().get("ring_speed", 0.12), 0.5);
    }

    @Override
    protected void alertTick(ServerLevel sl, @Nullable LivingEntity target) {
        setDeltaMovement(getDeltaMovement().scale(0.8));
        if (target != null) {
            face(target.getX(), target.getZ());
        }
    }

    @Override
    protected void returnTick(ServerLevel sl) {
        BlockPos c = center();
        double cx = c.getX() + 0.5, cz = c.getZ() + 0.5, rr = ringRadius();
        double a = Math.atan2(getZ() - cz, getX() - cx);
        Vec3 goal = new Vec3(cx + Math.cos(a) * rr, post.getY() + 0.5, cz + Math.sin(a) * rr);
        if (isHome()) {
            ring(stats().get("ring_speed", 0.12), 0.5);
        } else {
            steer(goal, getAttributeValue(Attributes.FLYING_SPEED) * 0.8);
            face(goal.x, goal.z);
        }
    }

    @Override
    protected void combatTick(ServerLevel sl, LivingEntity t) {
        double keep = stats().get("keep_distance", 6);
        bearing += spin * 0.03F;
        if (random.nextInt(80) == 0) {
            spin = -spin;
        }
        Vec3 goal = new Vec3(t.getX() + Math.cos(bearing) * keep, t.getY() + stats().get("hover", 3), t.getZ() + Math.sin(bearing) * keep);
        steer(goal, getAttributeValue(Attributes.FLYING_SPEED));
        face(t.getX(), t.getZ());
        getLookControl().setLookAt(t, 30.0F, 30.0F);
        if (fireCooldown > 0) {
            fireCooldown--;
            return;
        }
        double range = stats().get("range", 10);
        if (distanceToSqr(t) <= range * range && getSensing().hasLineOfSight(t)) {
            fireCooldown = stats().ticks("fire_interval", 30);
            swing(InteractionHand.MAIN_HAND);
            playSound(RpSounds.PISTON.get(), 0.6F, 1.8F);
            shoot(sl, t, new Vec3(getX(), getY() + 0.2, getZ()), (float) getAttributeValue(Attributes.ATTACK_DAMAGE),
                  SHOT);
        }
    }

    @Override
    protected void onWake(ServerLevel sl) {
        super.onWake(sl);
        if (stats().get("alarm", 0) > 0) {
            sl.playSound(null, getX(), getY(), getZ(), RpSounds.ALARM.get(), SoundSource.HOSTILE, 1.4F, 1.1F);
            say(sl, "alarm", true);
            Sentinels.wakeSite(sl, site, blockPosition());
        }
    }

    @Override
    protected float wakePitch() {
        return 1.5F;
    }

    @Override
    protected float absorb(@NotNull DamageSource source, float amount) {
        double dodge = stats().get("dodge", 0);
        if (dodge > 0 && source.is(DamageTypeTags.IS_PROJECTILE) && random.nextDouble() < dodge) {
            Vec3 side = Vec3.directionFromRotation(0, getYRot() + (random.nextBoolean() ? 90 : -90));
            setDeltaMovement(getDeltaMovement().add(side.scale(0.7)).add(0, 0.15, 0));
            playSound(RpSounds.SERVO_WAKE.get(), 0.5F, 2.0F);
            return 0;
        }
        return amount;
    }

    // ------------------------------------------------------------------ physique du drone
    @Override
    public void travel(@NotNull Vec3 input) {
        if (isDeadOrDying() || stunned()) {
            //Plus de portance : il tombe
            setDeltaMovement(getDeltaMovement().add(0, -0.04, 0));
            move(MoverType.SELF, getDeltaMovement());
            setDeltaMovement(getDeltaMovement().multiply(0.9, 0.98, 0.9));
            return;
        }
        if (level().isClientSide) {
            return;
        }
        move(MoverType.SELF, getDeltaMovement());
        setDeltaMovement(getDeltaMovement().scale(0.91));
        calculateEntityAnimation(true);
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, @NotNull DamageSource source) {
        return false;
    }

    @Override
    protected void checkFallDamage(double y, boolean onGround, @NotNull BlockState state, @NotNull BlockPos pos) {
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
    }

    @Override
    public boolean onClimbable() {
        return false;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putByte("SentinelSpin", (byte) spin);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        spin = tag.getByte("SentinelSpin") < 0 ? -1 : 1;
    }
}
