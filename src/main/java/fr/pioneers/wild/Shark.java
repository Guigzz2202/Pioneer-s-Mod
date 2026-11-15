package fr.pioneers.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

/**
 * 1.0.5-beta (lot 9) : les REQUINS (blanc, marteau, tigre, baleine, gris ; une classe, la sorte dans la bete). Ils respirent sous l'eau et
 * nagent sans arret. Blanc et tigre attaquent le nageur a vue, marteau et gris seulement un joueur qui SAIGNE, le requin-baleine jamais
 * ({@link Wild9Goals.SharkTarget}, {@link Wild9Rules#sharkAttacks}) ; ils chassent de temps en temps les poissons, jamais dans un claim ni
 * les poissons laches par un joueur. Les requins-marteaux vont en bancs.
 */
public class Shark extends MarineAnimal {

    public Shark(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level, kind);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Wild9Goals.SwimAttack(this, 1.7));
        goalSelector.addGoal(2, new Wild9Goals.SwimFlee(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new Wild9Goals.School(this, 10));
        goalSelector.addGoal(6, new Wild9Goals.Cruise(this, 0.8));
        targetSelector.addGoal(1, new HurtByTargetGoal(this) {
            @Override
            public boolean canUse() {
                return species().damage() > 0 && super.canUse();
            }
        });
        targetSelector.addGoal(2, new Wild9Goals.SharkTarget(this));
    }

    @Override
    protected boolean fleesWhenHurt() {
        return species().damage() <= 0;
    }
}
