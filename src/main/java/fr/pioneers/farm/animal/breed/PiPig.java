package fr.pioneers.farm.animal.breed;

import fr.pioneers.farm.animal.AnimalSpecies;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Race de le cochon (1.0.3-beta lot 8) : Pig vanilla dont la race est le type ({@link PiBreeds}) ; butin de le cochon vanilla. */
public class PiPig extends Pig implements BreedAnimal {

    public PiPig(EntityType<? extends Pig> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimalSpecies species() {
        return AnimalSpecies.PIG;
    }

    @Override
    public int breed() {
        return Math.max(0, PiBreeds.breedOf(getType()));
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return EntityType.PIG.getDefaultLootTable();
    }

    @Nullable
    @Override
    public Pig getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return PiBreeds.offspring(this, mate, level, getRandom(), Pig.class);
    }
}
