package fr.pioneers.world.wild;

import fr.pioneers.world.fauna.FaunaGroup;
import fr.pioneers.world.fauna.FaunaRegistry;
import fr.pioneers.world.fauna.FaunaSpecies;

/**
 * GENERE par tools/gc/wild/wild.py (1.0.5-beta, lot 6) : le gibier des forets et des plaines dans le socle de faune, une ligne par espece
 * (groupe GIBIER : partage le budget et le plafond du gibier, aucune densite en plus). Appele par
 * {@link fr.pioneers.world.fauna.FaunaRegistry#bootstrap}. Ne pas modifier a la main.
 */
public final class WildFauna {

    private WildFauna() {
    }

    public static void register() {
        FaunaRegistry.add(FaunaSpecies.of("pioneers:boar", FaunaGroup.GAME).biomes("minecraft:forest", "minecraft:flower_forest", "minecraft:dark_forest", "#minecraft:is_taiga").seasons(1.0, 1.0, 1.2, 0.8).size(2, 5).weight(6).cap(10));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:deer", FaunaGroup.GAME).biomes("minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:meadow").seasons(1.0, 1.0, 1.0, 0.8).size(3, 6).weight(8).cap(12));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:turkey", FaunaGroup.GAME).biomes("minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:meadow").seasons(1.2, 1.0, 1.0, 0.6).size(3, 6).weight(6).cap(12));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:hare", FaunaGroup.GAME).biomes("minecraft:plains", "minecraft:sunflower_plains", "minecraft:meadow", "minecraft:snowy_plains", "minecraft:snowy_taiga").seasons(1.0, 1.0, 1.0, 1.0).size(1, 2).weight(6).cap(8));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:bison", FaunaGroup.GAME).biomes("minecraft:plains", "minecraft:sunflower_plains", "minecraft:savanna_plateau").seasons(1.0, 1.0, 1.0, 0.8).size(4, 10).weight(5).cap(16));
    }
}
