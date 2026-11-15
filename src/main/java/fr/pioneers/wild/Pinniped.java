package fr.pioneers.wild;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.FollowParentGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.ai.goal.WaterAvoidingRandomStrollGoal;
import net.minecraft.world.entity.ai.navigation.AmphibiousPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 9) : PHOQUE et OTARIE (une classe, la sorte dans la bete). Amphibies : ils se PRELASSENT sur les rochers, la plage et la
 * banquise ({@link Wild9Goals.Bask} ; l'otarie se dresse et aboie), PLONGENT des qu'un joueur approche ({@link Wild9Goals.Dive}), nagent,
 * remontent respirer et regagnent la cote ({@link Wild9Goals.Shore}). Proies de l'orque.
 */
public class Pinniped extends MarineAnimal {

    public Pinniped(EntityType<? extends Animal> type, Level level, int kind) {
        super(type, level, kind);
    }

    @NotNull
    @Override
    protected PathNavigation createNavigation(@NotNull Level level) {
        return new AmphibiousPathNavigation(this, level);
    }

    @Override
    protected void registerGoals() {
        goalSelector.addGoal(0, new Wild9Goals.Breathe(this));
        goalSelector.addGoal(1, new Wild9Goals.Dive(this));
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.1, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new FollowParentGoal(this, 1.1));
        goalSelector.addGoal(6, new Wild9Goals.Shore(this));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new Wild9Goals.Bask(this));
        goalSelector.addGoal(8, new Wild9Goals.Cruise(this, 1.0));
        goalSelector.addGoal(9, new WaterAvoidingRandomStrollGoal(this, 0.8, 0.002F));
        goalSelector.addGoal(10, new LookAtPlayerGoal(this, Player.class, 8.0F));
        goalSelector.addGoal(11, new RandomLookAroundGoal(this));
    }
}
