package fr.pioneers.space.farm;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.world.item.Item;

/**
 * Nourriture spatiale : chaque recolte comestible donne un effet lie au climat de son astre.
 */
public class ItemSpaceFood extends Item {

    public ItemSpaceFood(SpacePlant plant) {
        super(new Properties().food(food(plant)));
    }

    private static FoodProperties food(SpacePlant plant) {
        FoodProperties.Builder builder = new FoodProperties.Builder().nutrition(plant.nutrition).saturationMod(plant.saturation);
        if (plant.effect != null) {
            builder.effect(() -> new MobEffectInstance(plant.effect, plant.effectTicks), 1.0F);
        }
        return builder.build();
    }
}
