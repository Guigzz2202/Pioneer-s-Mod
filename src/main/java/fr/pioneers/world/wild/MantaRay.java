package fr.pioneers.world.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.Level;

/** 1.0.5-beta (lot 9) : la RAIE MANTA : glisse en bancs en battant lentement des ailes ; inoffensive, elle s'ecarte d'un nageur trop proche. */
public class MantaRay extends MarineAnimal {

    public MantaRay(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild9Rules.MANTA);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(1, new Wild9Goals.SwimFlee(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(5, new Wild9Goals.School(this, 6));
        goalSelector.addGoal(6, new Wild9Goals.Cruise(this, 0.9));
    }
}
