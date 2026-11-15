package fr.pioneers.farm.animal.breed;

import fr.pioneers.farm.animal.AnimalSpecies;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Race de la vache (1.0.3-beta lot 8) : Cow vanilla dont la race est le type ({@link PiBreeds}) ; butin de la vache vanilla. */
public class PiCow extends Cow implements BreedAnimal {

    public PiCow(EntityType<? extends Cow> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimalSpecies species() {
        return AnimalSpecies.COW;
    }

    @Override
    public int breed() {
        return Math.max(0, PiBreeds.breedOf(getType()));
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return EntityType.COW.getDefaultLootTable();
    }

    @Nullable
    @Override
    public Cow getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return PiBreeds.offspring(this, mate, level, getRandom(), Cow.class);
    }
}
