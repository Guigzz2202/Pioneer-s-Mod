package fr.pioneers.world.ruins.sentinel;

import fr.pioneers.core.profile.RpSounds;
import java.util.HashSet;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : Contremaitre, mini-boss MKIII (un par ruine, 1,6 x 3,2 blocs), barre de boss aux joueurs proches tant qu'il est
 * eveille. Trois phases selon sa sante : 1, ordres (appelle {@code summons} Patrouilleurs, une fois par eveil, sans butin) ; 2, sous
 * {@code phase2} : charges et coups de zone ; 3, sous {@code phase3} : surchauffe, tourelles d'epaule qui tirent, plus rapide.
 */
public class ForemanEntity extends SentinelEntity {

    private final ServerBossEvent boss = new ServerBossEvent(getDisplayName(), BossEvent.BossBarColor.RED, BossEvent.BossBarOverlay.NOTCHED_10);
    private final Set<ServerPlayer> watchers = new HashSet<>();
    private int phase = 1;
    private boolean called;
    private int chargeCooldown, chargeTicks, slamCooldown, gunCooldown;
    private boolean leftGun;
    private Vec3 chargeDir = Vec3.ZERO;

    public ForemanEntity(EntityType<? extends ForemanEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public SentinelRole role() {
        return SentinelRole.FOREMAN;
    }

    @Override
    protected String wakePhrase() {
        return "foreman";
    }

    @Override
    protected float wakePitch() {
        return 0.6F;
    }

    /** Phase courante (1..3), synchronisee pour l'animation. */
    public int phase() {
        return level().isClientSide ? Math.max(1, aux()) : phase;
    }

    @Override
    protected void onWake(ServerLevel sl) {
        super.onWake(sl);
        called = false;
    }

    @Override
    protected void onEngage(ServerLevel sl, @Nullable LivingEntity target) {
        super.onEngage(sl, target);
        if (!called) {
            called = true;
            callPatrollers(sl);
        }
    }

    /** Phase 1 : appelle des Patrouilleurs (sans butin) tant qu'il en manque autour de lui. */
    private void callPatrollers(ServerLevel sl) {
        int want = stats().ticks("summons", 2);
        int have = sl.getEntitiesOfClass(PatrollerEntity.class, getBoundingBox().inflate(48), p -> p.isAlive() && p.summoned() && p.site() == site)
              .size();
        for (int i = have; i < want; i++) {
            PatrollerEntity p = SentinelTypes.PATROLLER.get().create(sl);
            if (p == null) {
                return;
            }
            BlockPos at = blockPosition().offset(random.nextInt(7) - 3, 4, random.nextInt(7) - 3);
            p.setup(tier(), site, at, random.nextFloat() * 360);
            p.summoned = true;
            sl.addFreshEntity(p);
            p.wakeUp(getTarget() instanceof Player pl ? pl : null);
        }
        say(sl, "summon", true);
        sl.playSound(null, getX(), getY(), getZ(), RpSounds.ALARM.get(), SoundSource.HOSTILE, 1.5F, 0.8F);
    }

    @Override
    protected void combatTick(ServerLevel sl, LivingEntity t) {
        if (chargeCooldown > 0) {
            chargeCooldown--;
        }
        if (slamCooldown > 0) {
            slamCooldown--;
        }
        if (gunCooldown > 0) {
            gunCooldown--;
        }
        if (chargeTicks > 0) {
            chargeTick(sl, t);
            return;
        }
        double speed = rules().common("chase_speed", 1.0) * (phase >= 3 ? stats().get("overheat_speed", 1.25) : 1);
        chase(t, speed);
        strike(t, stats().ticks("attack_interval", 30));
        if (phase >= 2) {
            double d = distanceTo(t);
            if (slamCooldown == 0 && d <= stats().get("slam_radius", 4)) {
                slam(sl);
            } else if (chargeCooldown == 0 && d >= 4 && d <= 14 && getSensing().hasLineOfSight(t)) {
                chargeTicks = stats().ticks("charge_ticks", 12);
                chargeCooldown = stats().ticks("charge_interval", 120);
                chargeDir = new Vec3(t.getX() - getX(), 0, t.getZ() - getZ()).normalize();
                setFlag(FLAG_WORK, true);
                getNavigation().stop();
                playSound(RpSounds.SERVO_WAKE.get(), 1.4F, 0.6F);
            }
        }
        if (phase >= 3 && gunCooldown == 0 && distanceToSqr(t) <= sq(stats().get("gun_range", 18)) && getSensing().hasLineOfSight(t)) {
            gunCooldown = stats().ticks("gun_interval", 20);
            leftGun = !leftGun;
            Vec3 side = Vec3.directionFromRotation(0, yBodyRot + (leftGun ? 90 : -90)).scale(0.55);
            Vec3 from = new Vec3(getX() + side.x, getY() + 2.85, getZ() + side.z);
            playSound(RpSounds.PISTON.get(), 0.9F, 1.6F);
            shoot(sl, t, from, (float) stats().get("gun_damage", 6), SHOT);
        }
    }

    private void chargeTick(ServerLevel sl, LivingEntity t) {
        chargeTicks--;
        double v = stats().get("charge_speed", 0.9);
        setDeltaMovement(chargeDir.x * v, getDeltaMovement().y, chargeDir.z * v);
        setYRot((float) (Math.atan2(chargeDir.z, chargeDir.x) * 180 / Math.PI) - 90);
        yBodyRot = yHeadRot = getYRot();
        if (getBoundingBox().inflate(0.6).intersects(t.getBoundingBox())) {
            t.hurt(damageSources().mobAttack(this), (float) stats().get("charge_damage", 12));
            t.knockback(1.6, -chargeDir.x, -chargeDir.z);
            playSound(RpSounds.PISTON.get(), 1.4F, 0.5F);
            chargeTicks = 0;
        }
        if (horizontalCollision) {
            chargeTicks = 0;
        }
        if (chargeTicks == 0) {
            setFlag(FLAG_WORK, false);
            setDeltaMovement(getDeltaMovement().multiply(0.2, 1, 0.2));
        }
    }

    /** Coup de zone : tous les joueurs a portee (hors creatif) sont frappes et repousses. */
    private void slam(ServerLevel sl) {
        slamCooldown = stats().ticks("slam_interval", 80);
        double r = stats().get("slam_radius", 4);
        swing(InteractionHand.MAIN_HAND);
        for (Player p : sl.getEntitiesOfClass(Player.class, getBoundingBox().inflate(r), p -> targetable(p) && p.distanceToSqr(this) <= r * r)) {
            p.hurt(damageSources().mobAttack(this), (float) stats().get("slam_damage", 10));
            Vec3 d = p.position().subtract(position()).normalize();
            p.knockback(1.2, -d.x, -d.z);
        }
        sl.sendParticles(ParticleTypes.EXPLOSION, getX(), getY() + 0.2, getZ(), 3, r * 0.4, 0.1, r * 0.4, 0);
        sl.sendParticles(ParticleTypes.CAMPFIRE_COSY_SMOKE, getX(), getY() + 0.2, getZ(), 10, r * 0.4, 0.1, r * 0.4, 0.02);
        sl.playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.HOSTILE, 0.7F, 1.4F);
        playSound(RpSounds.PISTON.get(), 1.5F, 0.4F);
    }

    @Override
    protected void serverExtra(ServerLevel sl) {
        float ratio = getHealth() / getMaxHealth();
        int p = ratio <= stats().get("phase3", 0.25) ? 3 : ratio <= stats().get("phase2", 0.6) ? 2 : 1;
        if (p > phase && brain.awake()) {
            say(sl, "phase" + p, true);
            playSound(RpSounds.ALARM.get(), 1.2F, p == 3 ? 1.4F : 1.0F);
        }
        phase = p;
        setAux(phase);
        setFlag(FLAG_HEAT, phase >= 3 && brain.awake());
        if (phase >= 3 && brain.awake() && random.nextInt(3) == 0) {
            Vec3 back = Vec3.directionFromRotation(0, yBodyRot).scale(-0.5);
            sl.sendParticles(ParticleTypes.LARGE_SMOKE, getX() + back.x, getY() + 2.3, getZ() + back.z, 1, 0.3, 0.2, 0.3, 0.01);
        }
        boss.setProgress(ratio);
        boss.setVisible(brain.awake());
        if (tickCount % 20 == 0) {
            boss.setName(getDisplayName());
            double r = stats().get("boss_radius", 48);
            Set<ServerPlayer> now = new HashSet<>();
            if (brain.awake()) {
                for (ServerPlayer pl : sl.players()) {
                    if (pl.distanceToSqr(this) <= r * r) {
                        now.add(pl);
                    }
                }
            }
            for (ServerPlayer pl : new HashSet<>(watchers)) {
                if (!now.contains(pl)) {
                    boss.removePlayer(pl);
                    watchers.remove(pl);
                }
            }
            for (ServerPlayer pl : now) {
                if (watchers.add(pl)) {
                    boss.addPlayer(pl);
                }
            }
        }
    }

    @Override
    public void setCustomName(@Nullable Component name) {
        super.setCustomName(name);
        boss.setName(getDisplayName());
    }

    @Override
    public void remove(@NotNull RemovalReason reason) {
        boss.removeAllPlayers();
        watchers.clear();
        super.remove(reason);
    }

    @Override
    public void stopSeenByPlayer(@NotNull ServerPlayer player) {
        super.stopSeenByPlayer(player);
        boss.removePlayer(player);
        watchers.remove(player);
    }

    @Override
    public void knockback(double strength, double x, double z) {
        super.knockback(strength * 0.2, x, z);
    }

    private static double sq(double v) {
        return v * v;
    }

    @Override
    public void addAdditionalSaveData(@NotNull CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putBoolean("SentinelCalled", called);
    }

    @Override
    public void readAdditionalSaveData(@NotNull CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        called = tag.getBoolean("SentinelCalled");
        if (hasCustomName()) {
            boss.setName(getDisplayName());
        }
    }

    @Override
    protected float getStandingEyeHeight(@NotNull Pose pose, @NotNull EntityDimensions size) {
        return size.height * 0.9F;
    }
}
