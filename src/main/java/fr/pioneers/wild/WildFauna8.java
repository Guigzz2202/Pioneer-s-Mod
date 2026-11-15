package fr.pioneers.wild;

import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaSpecies;

/**
 * GENERE par tools/gc/wild/wild8.py (1.0.5-beta, lot 8) : la savane et la jungle dans le socle de faune, une ligne par espece (partagent
 * le budget et le plafond de leur groupe). Appele par {@link fr.pioneers.fauna.FaunaRegistry#bootstrap}. Ne pas modifier a la main.
 */
public final class WildFauna8 {

    private WildFauna8() {
    }

    public static void register() {
        FaunaRegistry.add(FaunaSpecies.of("pioneers:giraffe", FaunaGroup.GAME).biomes("minecraft:savanna", "minecraft:savanna_plateau").seasons(1.0, 1.0, 1.0, 1.0).size(2, 5).weight(4).cap(8));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:lion", FaunaGroup.PREDATOR).biomes("minecraft:savanna", "minecraft:savanna_plateau").seasons(1.0, 1.0, 1.0, 1.0).size(2, 4).weight(3).cap(5));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:rhino", FaunaGroup.GAME).biomes("minecraft:savanna").seasons(1.0, 1.0, 1.0, 1.0).size(1, 2).weight(2).cap(3));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:elephant", FaunaGroup.GAME).biomes("minecraft:savanna").seasons(1.0, 1.0, 1.0, 1.0).size(3, 6).weight(3).cap(8));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:monkey", FaunaGroup.GAME).biomes("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle").seasons(1.0, 1.0, 1.0, 1.0).size(3, 6).weight(5).cap(10));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:tiger", FaunaGroup.PREDATOR).biomes("minecraft:jungle", "minecraft:sparse_jungle", "minecraft:bamboo_jungle").seasons(1.0, 1.0, 1.0, 1.0).size(1, 1).weight(2).cap(2));
    }
}
