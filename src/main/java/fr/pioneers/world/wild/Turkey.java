package fr.pioneers.world.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 1.0.5-beta (lot 6, specification §4) : le DINDON et la dinde. Groupes de 3 a 6 ; le male FAIT LA ROUE (queue deployee en eventail,
 * ailes trainantes, il tourne lentement sur lui-meme ; surtout au printemps). Il fuit en courant ; il ne tombe pas (plane un peu).
 */
public class Turkey extends WildAnimal {

    public Turkey(EntityType<? extends Animal> type, Level level) {
        super(type, level, WildRules.TURKEY);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        Vec3 v = getDeltaMovement();
        if (!onGround() && v.y < 0) {
            setDeltaMovement(v.multiply(1, 0.7, 1));        //descente planee, comme la poule
        }
    }

    @Override
    protected boolean isFlapping() {
        return false;
    }

    @Override
    public boolean causeFallDamage(float distance, float multiplier, net.minecraft.world.damagesource.DamageSource source) {
        return false;
    }

    /** Fait la roue (male adulte, action « parade »). */
    public boolean strutting() {
        return isMale() && !isBaby() && action() == WildRules.DISPLAY;
    }
}
