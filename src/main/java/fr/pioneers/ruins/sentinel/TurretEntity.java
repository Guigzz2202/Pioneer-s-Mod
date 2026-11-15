package fr.pioneers.ruins.sentinel;

import fr.pioneers.core.profile.RpSounds;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.BodyRotationControl;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * 1.9 (MAJ RP, bloc 1) : Tourelle, fixe sur son socle (1 x 1,4 bloc), tir a {@code range} blocs : le corps ne tourne jamais, seule la tete
 * vise. MKII : tir toutes les {@code fire_interval} ticks une fois alignee. MKIII : laser charge {@code laser_charge} ticks (rayon visible,
 * dessine par le rendu d'apres {@link #FLAG_WORK} et la cible en {@link #aux()}), annule si la cible sort de la vue.
 */
public class TurretEntity extends SentinelEntity {

    private static final DustParticleOptions LASER = new DustParticleOptions(new Vector3f(1.0F, 0.25F, 0.08F), 1.2F);
    private int fireCooldown;
    private int charge;

    public TurretEntity(EntityType<? extends TurretEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public SentinelRole role() {
        return SentinelRole.TURRET;
    }

    @Override
    protected @NotNull BodyRotationControl createBodyControl() {
        return new BodyRotationControl(this) {
            @Override
            public void clientTick() {
            }
        };
    }

    @Override
    public int getMaxHeadYRot() {
        return 180;
    }

    @Override
    protected boolean isHome() {
        return true;
    }

    @Override
    protected void returnTick(ServerLevel sl) {
        cancelCharge();
    }

    @Override
    protected void alertTick(ServerLevel sl, @Nullable LivingEntity target) {
        if (target != null) {
            getLookControl().setLookAt(target, 20.0F, 20.0F);
        }
    }

    @Override
    protected String wakePhrase() {
        return "turret";
    }

    @Override
    protected float wakePitch() {
        return 0.8F;
    }

    private Vec3 muzzle() {
        Vec3 dir = Vec3.directionFromRotation(getXRot(), yHeadRot);
        return new Vec3(getX(), getY() + 15 / 16.0, getZ()).add(dir.scale(0.7));
    }

    private boolean aimed(LivingEntity t) {
        double want = Mth.atan2(t.getZ() - getZ(), t.getX() - getX()) * Mth.RAD_TO_DEG - 90.0;
        return Math.abs(Mth.wrapDegrees(want - yHeadRot)) <= stats().get("aim_tolerance", 12);
    }

    @Override
    protected void combatTick(ServerLevel sl, LivingEntity t) {
        getLookControl().setLookAt(t, 20.0F, 20.0F);
        double range = stats().get("range", 18);
        boolean inRange = distanceToSqr(t) <= range * range;
        boolean sight = getSensing().hasLineOfSight(t);
        float damage = (float) getAttributeValue(Attributes.ATTACK_DAMAGE);
        int interval = stats().ticks("fire_interval", 30);
        int laser = stats().ticks("laser_charge", 0);
        if (fireCooldown > 0) {
            fireCooldown--;
        }
        if (laser > 0) {
            if (charge > 0) {
                if (!inRange || !sight || aux() != t.getId()) {
                    cancelCharge();
                    return;
                }
                if (++charge >= laser) {
                    shoot(sl, t, muzzle(), damage, LASER);
                    sl.sendParticles(ParticleTypes.FLASH, t.getX(), t.getY() + t.getBbHeight() * 0.6, t.getZ(), 1, 0, 0, 0, 0);
                    swing(InteractionHand.MAIN_HAND);
                    cancelCharge();
                    fireCooldown = interval;
                }
                return;
            }
            if (fireCooldown == 0 && inRange && sight && aimed(t)) {
                charge = 1;
                setAux(t.getId());
                setFlag(FLAG_WORK, true);
                sl.playSound(null, getX(), getY(), getZ(), RpSounds.LASER_CHARGE.get(), SoundSource.HOSTILE, 1.2F, 1.0F);
            }
            return;
        }
        if (fireCooldown == 0 && inRange && sight && aimed(t)) {
            fireCooldown = interval;
            swing(InteractionHand.MAIN_HAND);
            playSound(RpSounds.PISTON.get(), 0.9F, 1.5F);
            shoot(sl, t, muzzle(), damage, SHOT);
        }
    }

    private void cancelCharge() {
        charge = 0;
        setFlag(FLAG_WORK, false);
        setAux(-1);
    }

    @Override
    protected void onSleep(ServerLevel sl) {
        cancelCharge();
        super.onSleep(sl);
    }

    @Override
    public void travel(@NotNull Vec3 input) {
        //Fixe : seule la chute compte
        Vec3 v = getDeltaMovement();
        setDeltaMovement(0, v.y, 0);
        if (onGround() && v.y <= 0 && (tickCount + getId()) % 10 != 0) {
            return;
        }
        super.travel(Vec3.ZERO);
        setDeltaMovement(0, getDeltaMovement().y, 0);
    }

    @Override
    public void knockback(double strength, double x, double z) {
    }

    @Override
    public boolean isPushedByFluid() {
        return false;
    }

    @Override
    protected void playStepSound(@NotNull BlockPos pos, @NotNull BlockState state) {
    }

    /** Le rayon du laser depasse la boite : rendu garde tant qu'il charge. */
    @Override
    public @NotNull AABB getBoundingBoxForCulling() {
        AABB box = super.getBoundingBoxForCulling();
        if (flag(FLAG_WORK)) {
            Entity t = level().getEntity(aux());
            if (t != null) {
                return box.minmax(t.getBoundingBox());
            }
        }
        return box;
    }
}
