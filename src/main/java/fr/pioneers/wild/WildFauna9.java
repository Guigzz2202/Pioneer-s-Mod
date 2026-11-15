package fr.pioneers.wild;

import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaSpecies;

/**
 * GENERE par tools/gc/wild/wild9.py (1.0.5-beta, lot 9) : la mer et les cotes dans le socle de faune, une ligne par espece (partagent
 * le budget et le plafond de leur groupe : faune marine, predateurs pour l'orque et les requins blanc et tigre, oiseaux pour la
 * mouette ; lieu : l'eau libre ou la cote). Appele par {@link fr.pioneers.fauna.FaunaRegistry#bootstrap}. Ne pas modifier a la main.
 */
public final class WildFauna9 {

    private WildFauna9() {
    }

    public static void register() {
        FaunaRegistry.add(FaunaSpecies.of("pioneers:blue_whale", FaunaGroup.MARINE).biomes("minecraft:deep_ocean", "minecraft:deep_cold_ocean", "minecraft:deep_frozen_ocean").seasons(1.0, 1.2, 1.0, 0.8).size(1, 1).weight(1).cap(1).rarity(0.5).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:humpback_whale", FaunaGroup.MARINE).biomes("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:lukewarm_ocean").seasons(1.2, 1.0, 1.0, 0.8).size(1, 2).weight(2).cap(2).rarity(0.35).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:orca", FaunaGroup.PREDATOR).biomes("minecraft:cold_ocean", "minecraft:deep_cold_ocean", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 2).weight(1).cap(2).rarity(0.3).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:sperm_whale", FaunaGroup.MARINE).biomes("minecraft:deep_ocean", "minecraft:deep_lukewarm_ocean", "minecraft:deep_cold_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 1).weight(1).cap(1).rarity(0.5).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:great_white_shark", FaunaGroup.PREDATOR).biomes("minecraft:ocean", "minecraft:deep_ocean", "minecraft:cold_ocean", "minecraft:lukewarm_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 1).weight(3).cap(3).rarity(0.45).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:hammerhead_shark", FaunaGroup.MARINE).biomes("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 3).weight(3).cap(3).rarity(0.35).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:tiger_shark", FaunaGroup.PREDATOR).biomes("minecraft:warm_ocean", "minecraft:lukewarm_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 1).weight(3).cap(3).rarity(0.45).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:whale_shark", FaunaGroup.MARINE).biomes("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 1).weight(1).cap(1).rarity(0.5).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:gray_reef_shark", FaunaGroup.MARINE).biomes("minecraft:warm_ocean", "minecraft:lukewarm_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 2).weight(4).cap(3).rarity(0.4).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:manta_ray", FaunaGroup.MARINE).biomes("minecraft:warm_ocean", "minecraft:lukewarm_ocean", "minecraft:deep_lukewarm_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(1, 3).weight(3).cap(3).rarity(0.3).where(FaunaSpecies.Where.WATER));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:seal", FaunaGroup.MARINE).biomes("minecraft:stony_shore", "minecraft:snowy_beach", "minecraft:frozen_ocean", "minecraft:deep_frozen_ocean").seasons(1.0, 1.0, 1.0, 1.0).size(2, 5).weight(4).cap(8).where(FaunaSpecies.Where.LAND));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:sea_lion", FaunaGroup.MARINE).biomes("minecraft:beach", "minecraft:stony_shore").seasons(1.0, 1.0, 1.0, 1.0).size(2, 5).weight(4).cap(8).where(FaunaSpecies.Where.LAND));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:seagull", FaunaGroup.BIRD).biomes("minecraft:beach", "minecraft:stony_shore", "minecraft:snowy_beach").seasons(1.2, 1.2, 1.0, 0.8).size(2, 5).weight(6).cap(10).nearBuildings().where(FaunaSpecies.Where.LAND));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:penguin", FaunaGroup.MARINE).biomes("minecraft:frozen_ocean", "minecraft:deep_frozen_ocean", "minecraft:snowy_beach", "minecraft:ice_spikes").seasons(1.0, 1.0, 1.0, 1.0).size(3, 6).weight(6).cap(10).where(FaunaSpecies.Where.LAND));
        FaunaRegistry.add(FaunaSpecies.of("pioneers:razorbill", FaunaGroup.MARINE).biomes("minecraft:stony_shore", "minecraft:snowy_beach").seasons(1.0, 1.0, 1.0, 1.0).size(3, 6).weight(5).cap(8).where(FaunaSpecies.Where.LAND));
    }
}
