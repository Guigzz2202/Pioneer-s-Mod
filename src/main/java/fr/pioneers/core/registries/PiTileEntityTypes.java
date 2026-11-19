package fr.pioneers.core.registries;

import fr.pioneers.Pioneers;
import fr.pioneers.space.LaunchPortTile;
import fr.pioneers.space.farm.GreenhousePortTile;
import fr.pioneers.space.mining.MiningPortTile;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Tile entities (blocs avec donnees et logique) du mod.
 */
public final class PiTileEntityTypes {

    private PiTileEntityTypes() {
    }

    /** Blocs a entite "simples" (hors machines Mekanism). */
    public static final DeferredRegister<BlockEntityType<?>> VANILLA_TILES =
          DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, Pioneers.MODID);

    // --- Pas de tir ---
    // --- Recherche spatiale ---
    /** Port de serre (bloc simple, pas une machine Mekanism). */
    public static final RegistryObject<BlockEntityType<GreenhousePortTile>> GREENHOUSE_PORT =
          VANILLA_TILES.register("greenhouse_port", () -> BlockEntityType.Builder.of(
                GreenhousePortTile::new, PiBlocks.GREENHOUSE_PORT.getBlock()).build(null));
    /** Port minier (bloc simple, pas une machine Mekanism). */
    public static final RegistryObject<BlockEntityType<MiningPortTile>> MINING_PORT =
          VANILLA_TILES.register("mining_port", () -> BlockEntityType.Builder.of(
                MiningPortTile::new, PiBlocks.MINING_PORT.getBlock()).build(null));
    /** Port du complexe de lancement. */
    public static final RegistryObject<BlockEntityType<LaunchPortTile>> LAUNCH_PORT =
          VANILLA_TILES.register("launch_port", () -> BlockEntityType.Builder.of(
                LaunchPortTile::new, PiBlocks.LAUNCH_PORT.getBlock()).build(null));
    /** 1.0.7 (lot 4) : chantier de megastructure (livraison sans inventaire propre). */
    public static final RegistryObject<BlockEntityType<fr.pioneers.space.mega.MegaSiteTile>> MEGASTRUCTURE_SITE =
          VANILLA_TILES.register("megastructure_site", () -> BlockEntityType.Builder.of(
                fr.pioneers.space.mega.MegaSiteTile::new, PiBlocks.MEGASTRUCTURE_SITE.getBlock()).build(null));
}
