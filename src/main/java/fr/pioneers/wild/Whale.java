package fr.pioneers.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

/**
 * 1.0.5-beta (lot 9) : les CETACES (baleine bleue, a bosse, orque, cachalot ; une classe, la sorte dans la bete). Tres longs a tuer, ils
 * remontent respirer et SOUFFLENT en surface ({@link MarineAnimal#breathe}), puis replongent (la baleine a bosse montre sa queue). Paisibles :
 * la baleine bleue et la baleine a bosse fuient a la nage ; le cachalot et l'orque rendent les coups ; l'orque chasse en groupe phoques,
 * otaries, manchots, pingouins et requins ({@link Wild9Goals.OrcaHunt}), jamais le joueur.
 */
public class Whale extends MarineAnimal {

    public Whale(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level, kind);
    }

    @Override
    protected void registerGoals() {
        //la sorte n'est pas encore connue ici (constructeur de Mob) : tous les buts, chacun verifie l'espece en jeu
        goalSelector.addGoal(0, new Wild9Goals.Breathe(this));
        goalSelector.addGoal(1, new Wild9Goals.SwimAttack(this, 1.6));
        goalSelector.addGoal(2, new Wild9Goals.SwimFlee(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new Wild9Goals.School(this, 12));
        goalSelector.addGoal(6, new Wild9Goals.Cruise(this, 1.0));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return species().damage() > 0 && super.canUse();
            }
        }.setAlertOthers());
        targetSelector.addGoal(2, new Wild9Goals.OrcaHunt(this));
    }
}
