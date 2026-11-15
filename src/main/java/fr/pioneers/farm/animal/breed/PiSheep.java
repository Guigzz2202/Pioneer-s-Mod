package fr.pioneers.farm.animal.breed;

import fr.pioneers.farm.animal.AnimalSpecies;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Race de mouton (1.0.3-beta lot 8) : mouton vanilla dont la race est le type ({@link PiBreeds}) ; butin vanilla (toison de la couleur de la
 * laine, ou mouton tondu). La couleur du petit est la teinte naturelle de sa race ({@code AnimalEvents.baby}).
 */
public class PiSheep extends Sheep implements BreedAnimal {

    public PiSheep(EntityType<? extends Sheep> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimalSpecies species() {
        return AnimalSpecies.SHEEP;
    }

    @Override
    public int breed() {
        return Math.max(0, PiBreeds.breedOf(getType()));
    }

    @Override
    public ResourceLocation getDefaultLootTable() {
        return isSheared() ? EntityType.SHEEP.getDefaultLootTable() : super.getDefaultLootTable();
    }

    @Nullable
    @Override
    public Sheep getBreedOffspring(ServerLevel level, AgeableMob mate) {
        Sheep child = PiBreeds.offspring(this, mate, level, getRandom(), Sheep.class);
        if (child != null) {
            child.setColor(getColor());
        }
        return child;
    }
}
