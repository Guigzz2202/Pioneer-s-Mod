package fr.pioneers.wild;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.goal.MeleeAttackGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 6, specification §4) : le BISON. Troupeaux de 4 a 10, paisibles (ils s'eloignent au pas d'un joueur trop proche) ;
 * mais si l'un d'eux est attaque, TOUT LE TROUPEAU a 20 blocs se defend ({@link #defend}) : les adultes chargent l'agresseur pendant 20 s
 * au plus, jusqu'a 24 blocs. Male a grosse bosse et bisonne ; bisonneau fauve.
 */
public class Bison extends WildAnimal {

    int anger;

    public Bison(EntityType<? extends Animal> type, Level level) {
        super(type, level, WildRules.BISON);
    }

    @Override
    protected void registerGoals() {
        super.registerGoals();
        goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.4, true));
    }

    @Override
    protected boolean fleesWhenHurt() {
        return isBaby();
    }

    @Override
    public boolean hurt(@NotNull DamageSource source, float amount) {
        boolean hit = super.hurt(source, amount);
        if (hit && !level().isClientSide && source.getEntity() instanceof LivingEntity attacker && !(attacker instanceof Bison)
            && !(attacker instanceof Player p && (p.isCreative() || p.isSpectator()))) {
            defend(attacker);
        }
        return hit;
    }

    /** Le troupeau (adultes a 20 blocs, celui-ci compris) charge l'agresseur ; rend le nombre de defenseurs. */
    public int defend(LivingEntity attacker) {
        int n = 0;
        for (WildAnimal w : kin(WildRules.DEFEND_RADIUS)) {
            if (w instanceof Bison b && !b.isBaby()) {
                b.rage(attacker);
                n++;
            }
        }
        return n;
    }

    void rage(LivingEntity t) {
        if (getTarget() != t) {
            playOwn("angry");
        }
        setTarget(t);
        anger = WildRules.ANGER_TICKS;
        setAction(WildRules.CHARGE);
    }

    @Override
    public void aiStep() {
        super.aiStep();
        if (level().isClientSide || anger <= 0) {
            return;
        }
        LivingEntity t = getTarget();
        if (--anger <= 0 || t == null || !t.isAlive() || distanceTo(t) > WildRules.GIVE_UP_DISTANCE) {
            anger = 0;
            setTarget(null);
            setAction(WildRules.IDLE);
        }
    }

    public boolean angry() {
        return anger > 0 && getTarget() != null;
    }
}
