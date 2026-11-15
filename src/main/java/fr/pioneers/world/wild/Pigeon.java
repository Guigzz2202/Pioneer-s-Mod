package fr.pioneers.world.wild;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.goal.BreedGoal;
import net.minecraft.world.entity.ai.goal.TemptGoal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ServerLevelAccessor;

/**
 * 1.0.5-beta (lot 7, specification §4, §7) : le PIGEON, oiseau des villes et des campagnes, attire par les constructions (exception du
 * socle : il reapparait pres des villages et dans les claims). En groupes de 3 a 6 ; il se JUCHE SUR LES TOITS ({@link Wild7Goals.Roost}),
 * picore au sol, ROUCOULE en faisant le beau (poitrail gonfle, queue en eventail). S'envole a 5 blocs. Moins nombreux en hiver. Robes :
 * bleu barre, ecaille, rouge, blanc. Plumes, et parfois un pigeon a cuire.
 */
public class Pigeon extends WildBird {

    public Pigeon(EntityType<? extends Animal> type, Level level) {
        super(type, level, Wild7Rules.PIGEON);
    }

    @Override
    protected void addOwnGoals() {
        goalSelector.addGoal(3, new BreedGoal(this, 1.0));
        goalSelector.addGoal(4, new TemptGoal(this, 1.0, Ingredient.of(food()), false));
        goalSelector.addGoal(5, new Wild7Goals.Roost(this));
        goalSelector.addGoal(6, new WildGoals.Herd(this));
        goalSelector.addGoal(7, new Wild7Goals.Idle(this));
    }

    @Override
    protected int pickVariant(ServerLevelAccessor level, RandomSource random) {
        return Wild7Rules.pigeonCoat(random.nextDouble());
    }

    @Override
    protected void playOwnQuiet() {
        playOwn("flap");
    }

    @Override
    public int getAmbientSoundInterval() {
        return 200;
    }

    @Override
    protected float getSoundVolume() {
        return 0.6F;
    }
}
