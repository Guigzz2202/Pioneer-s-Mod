package fr.pioneers.world.wild;

import fr.pioneers.world.fauna.FaunaGroup;
import fr.pioneers.world.fauna.FaunaRegistry;
import fr.pioneers.world.fauna.FaunaSpecies;

/**
 * GENERE par tools/gc/wild/wild7.py (1.0.5-beta, lot 7) : les predateurs et la petite faune dans le socle de faune, une ligne par espece
 * (partagent le budget et le plafond de leur groupe ; rats et pigeons reapparaissent aussi pres des constructions). Appele par
 * {@link fr.pioneers.world.fauna.FaunaRegistry#bootstrap}. Ne pas modifier a la main.
 */
public final class WildFauna7 {

    private WildFauna7() {
    }

    public static void register() {
        FaunaRegistry.add(FaunaSpecies.of("pioneers:gray_wolf", FaunaGroup.PREDATOR).biomes("#minecraft:is_taiga", "minecraft:snowy_plains", "minecraft:grove", "minecraft:snowy_slopes", "minecraft:windswept_hills", "minecraft:windswept_forest", "minecraft:windswept_gravelly_hills").seasons(1.0, 1.0, 1.0, 1.1).size(3, 5).weight(4).cap(6));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:bear", FaunaGroup.GAME).biomes("minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:dark_forest", "#minecraft:is_taiga", "minecraft:windswept_forest", "minecraft:grove").seasons(1.0, 1.0, 1.0, 1.0).size(1, 2).weight(2).cap(3));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:woodpecker", FaunaGroup.BIRD).biomes("minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:old_growth_birch_forest", "minecraft:dark_forest", "minecraft:taiga").seasons(1.2, 1.2, 1.0, 0.5).size(1, 2).weight(4).cap(4));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:rat", FaunaGroup.GAME).biomes("*").seasons(1.0, 1.0, 1.0, 1.0).size(1, 2).weight(2).cap(4).nearBuildings());
        FaunaRegistry.add(FaunaSpecies.of("pioneers:pigeon", FaunaGroup.BIRD).biomes("*").seasons(1.2, 1.2, 1.0, 0.5).size(2, 3).weight(2).cap(4).nearBuildings());
        FaunaRegistry.add(FaunaSpecies.of("pioneers:snake", FaunaGroup.PREDATOR).biomes("minecraft:plains", "minecraft:sunflower_plains", "minecraft:forest", "minecraft:flower_forest", "minecraft:birch_forest", "minecraft:savanna", "minecraft:savanna_plateau", "minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle", "minecraft:desert").seasons(1.0, 1.2, 1.0, 0.3).size(1, 1).weight(3).cap(4));
    }
}
