package fr.pioneers.farm.animal.breed;

import fr.pioneers.farm.animal.AnimalSpecies;
import fr.pioneers.farm.animal.PetFood;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/** Race de la poule (1.0.3-beta lot 8) : Chicken vanilla dont la race est le type ({@link PiBreeds}) ; butin de la poule vanilla. */
public class PiChicken extends Chicken implements BreedAnimal {

    public PiChicken(EntityType<? extends Chicken> type, Level level) {
        super(type, level);
    }

    @Override
    public AnimalSpecies species() {
        return AnimalSpecies.CHICKEN;
    }

    @Override
    public int breed() {
        return Math.max(0, PiBreeds.breedOf(getType()));
    }

    /** 1.0.4-beta (lot 4) : toute graine (vanilla, du mod, des astres : tag pioneers:seeds/all) la nourrit et la fait pondre. */
    @Override
    public boolean isFood(ItemStack stack) {
        return PetFood.seed(stack) || super.isFood(stack);
    }

    @Override
    protected ResourceLocation getDefaultLootTable() {
        return EntityType.CHICKEN.getDefaultLootTable();
    }

    @Nullable
    @Override
    public Chicken getBreedOffspring(ServerLevel level, AgeableMob mate) {
        return PiBreeds.offspring(this, mate, level, getRandom(), Chicken.class);
    }
}
