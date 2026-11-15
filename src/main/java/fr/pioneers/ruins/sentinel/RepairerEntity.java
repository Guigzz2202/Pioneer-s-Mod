package fr.pioneers.ruins.sentinel;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.9 (MAJ RP, bloc 1) : Reparateur (0,8 x 1,6 bloc, bras outils). N'attaque pas : il suit le plus abime de ses semblables, garde ses
 * distances avec le joueur et soigne {@code heal} PV toutes les {@code heal_interval} ticks a {@code heal_radius} blocs (aussi apres le
 * combat, pendant son retour). MKIII : bulle de bouclier sur les Sentinelles proches ({@code shield_duration} toutes les
 * {@code shield_interval} ticks).
 */
public class RepairerEntity extends SentinelEntity {

    private int healCooldown;
    private int shieldCooldown;
    private int workTimer;
    private int seek;
    @Nullable
    private SentinelEntity patient;

    public RepairerEntity(EntityType<? extends RepairerEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public SentinelRole role() {
        return SentinelRole.REPAIRER;
    }

    @Override
    protected String wakePhrase() {
        return "repair";
    }

    @Override
    protected float wakePitch() {
        return 1.3F;
    }

    @Override
    protected void combatTick(ServerLevel sl, LivingEntity t) {
        maintain(sl, true);
        double heal = stats().get("heal_radius", 6);
        double flee = stats().get("flee_distance", 4);
        if (distanceToSqr(t) < flee * flee) {
            //Garde ses distances
            Vec3 away = position().subtract(t.position()).normalize().scale(5);
            if (--seek <= 0 || getNavigation().isDone()) {
                seek = 10;
                getNavigation().moveTo(getX() + away.x, getY(), getZ() + away.z, 1.1);
            }
        } else if (patient != null && distanceToSqr(patient) > heal * heal * 0.4) {
            if (--seek <= 0 || getNavigation().isDone()) {
                seek = 10;
                getNavigation().moveTo(patient, 1.0);
            }
        } else {
            getNavigation().stop();
        }
        getLookControl().setLookAt(patient != null ? patient : t, 30.0F, 30.0F);
    }

    @Override
    protected void returnTick(ServerLevel sl) {
        maintain(sl, false);
        if (patient == null) {
            super.returnTick(sl);
        } else {
            getLookControl().setLookAt(patient, 30.0F, 30.0F);
            double heal = stats().get("heal_radius", 6);
            if (distanceToSqr(patient) > heal * heal * 0.4 && (--seek <= 0 || getNavigation().isDone())) {
                seek = 10;
                getNavigation().moveTo(patient, 0.9);
            }
        }
    }

    /** Choisit un patient, soigne, pose la bulle de bouclier (MKIII, en combat). */
    private void maintain(ServerLevel sl, boolean fighting) {
        if (workTimer > 0 && --workTimer == 0) {
            setFlag(FLAG_WORK, false);
        }
        if (healCooldown > 0) {
            healCooldown--;
        }
        if (shieldCooldown > 0) {
            shieldCooldown--;
        }
        double support = stats().get("support_radius", 12);
        double radius = stats().get("heal_radius", 6);
        if (tickCount % 20 == 0 || patient != null && (!patient.isAlive() || patient.getHealth() >= patient.getMaxHealth())) {
            patient = null;
            float worst = 1;
            for (SentinelEntity s : allies(sl, support)) {
                float ratio = s.getHealth() / s.getMaxHealth();
                if (s != this && ratio < worst) {
                    worst = ratio;
                    patient = s;
                }
            }
        }
        if (healCooldown == 0) {
            SentinelEntity best = null;
            float worst = 1;
            for (SentinelEntity s : allies(sl, radius)) {
                float ratio = s.getHealth() / s.getMaxHealth();
                if (s != this && ratio < worst) {
                    worst = ratio;
                    best = s;
                }
            }
            if (best != null) {
                healCooldown = stats().ticks("heal_interval", 20);
                best.heal((float) stats().get("heal", 4));
                beam(sl, best);
                if (fighting) {
                    say(sl, "repair", false);
                }
            }
        }
        int duration = stats().ticks("shield_duration", 0);
        if (fighting && duration > 0 && shieldCooldown == 0) {
            shieldCooldown = stats().ticks("shield_interval", 400);
            float reduction = (float) stats().get("shield_reduction", 0.6);
            for (SentinelEntity s : allies(sl, radius)) {
                s.giveShield(duration, reduction);
            }
            sl.playSound(null, getX(), getY(), getZ(), SoundEvents.BEACON_ACTIVATE, SoundSource.HOSTILE, 1.0F, 1.6F);
        }
    }

    private List<SentinelEntity> allies(ServerLevel sl, double radius) {
        return sl.getEntitiesOfClass(SentinelEntity.class, getBoundingBox().inflate(radius),
              s -> s.isAlive() && (s.site() == site || site == 0) && s.distanceToSqr(this) <= radius * radius);
    }

    private void beam(ServerLevel sl, SentinelEntity to) {
        workTimer = 15;
        setFlag(FLAG_WORK, true);
        Vec3 a = new Vec3(getX(), getY() + 0.7, getZ());
        Vec3 b = new Vec3(to.getX(), to.getY() + to.getBbHeight() * 0.5, to.getZ());
        Vec3 d = b.subtract(a);
        int n = Math.min(30, (int) (d.length() / 0.4));
        for (int i = 1; i <= n; i++) {
            Vec3 p = a.add(d.scale(i / (double) n));
            sl.sendParticles(ParticleTypes.WAX_ON, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        sl.sendParticles(ParticleTypes.ELECTRIC_SPARK, b.x, b.y, b.z, 6, 0.2, 0.3, 0.2, 0.1);
    }
}
