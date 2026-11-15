package fr.pioneers.world.ruins.sentinel;

import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : Gardien, beviaire lourd de melee (1 x 2,2 blocs). MKIII : bouclier frontal, une part des degats venus de face
 * ({@code front_shield} dans un cone de {@code front_angle} degres) est arretee.
 */
public class GuardianEntity extends SentinelEntity {

    public GuardianEntity(EntityType<? extends GuardianEntity> type, Level level) {
        super(type, level);
    }

    @Override
    public SentinelRole role() {
        return SentinelRole.GUARDIAN;
    }

    @Override
    protected float absorb(@NotNull DamageSource source, float amount) {
        double k = stats().get("front_shield", 0);
        if (k <= 0 || source.is(DamageTypeTags.BYPASSES_SHIELD) || source.is(DamageTypeTags.BYPASSES_ARMOR)) {
            return amount;
        }
        Vec3 from = source.getSourcePosition();
        if (from == null) {
            return amount;
        }
        Vec3 look = Vec3.directionFromRotation(0, yBodyRot);
        Vec3 to = new Vec3(from.x - getX(), 0, from.z - getZ());
        if (to.lengthSqr() < 1.0E-4) {
            return amount;
        }
        double angle = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, look.dot(to.normalize())))));
        if (angle > stats().get("front_angle", 60)) {
            return amount;
        }
        playSound(SoundEvents.SHIELD_BLOCK, 0.9F, 0.7F);
        return (float) (amount * (1 - k));
    }
}
