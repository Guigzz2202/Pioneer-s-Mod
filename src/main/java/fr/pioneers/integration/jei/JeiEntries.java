package fr.pioneers.integration.jei;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.content.stabilizer.Regime;
import fr.pioneers.content.stabilizer.StabilizerCoreTile;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.data.AnalysisData;
import fr.pioneers.data.DataUnits;
import fr.pioneers.data.ItemBiomeDataCell;
import fr.pioneers.data.PiDataTypes;
import fr.pioneers.data.SpaceBiomes;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.registries.PiItems;
import fr.pioneers.registries.PiStabilizer;
import fr.pioneers.registries.PiWorldBlocks;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.research.BodyResearch;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.tile.TileEntityAsteroidCrusher;
import fr.pioneers.tile.TileEntityBioSequencer;
import fr.pioneers.tile.TileEntityBiomeAnalyzer;
import fr.pioneers.tile.TileEntityGeoSpectrometer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.RegistryObject;

/**
 * Les productions sans recette JSON, decrites pour JEI : chambres, machines a donnees (1.10.2 : les pages d'information des types de
 * donnees sont retirees ; les sondes ont leur categorie, {@link Productions}).
 */
final class JeiEntries {

    private JeiEntries() {
    }

    private static Component energyPerRate(long fe) {
        return Component.translatable("jei.pioneers.per_tick", PiCategory.energy(fe));
    }

    static ChamberCategory.Entry blackHole() {
        Regime regime = Regime.SINGULARITY;
        return new ChamberCategory.Entry(null, new FluidStack(Fluids.WATER, (int) regime.inputPerOutput), new ItemStack(PiStabilizer.SINGULARITY_CORE),
              GasStack.of(PiGasTypes.VOID_SINGULARITY.get(), 1), List.of(
              Component.translatable("jei.pioneers.chamber.rate", PiCategory.mb(regime.inputPerRatePerTick),
                    StabilizerCoreTile.MIN_RATE, StabilizerCoreTile.MAX_RATE),
              Component.translatable("jei.pioneers.chamber.energy", energyPerRate(regime.energyPerRatePerTick)),
              Component.translatable("jei.pioneers.chamber.ignition", PiCategory.energy(StabilizerCoreTile.IGNITION_ENERGY_FE))));
    }

    static ChamberCategory.Entry stellarChamber() {
        Regime regime = Regime.STAR;
        return new ChamberCategory.Entry(null, new FluidStack(Fluids.LAVA, (int) regime.inputPerOutput),
              new ItemStack(PiStabilizer.STELLAR_CORE), GasStack.of(PiGasTypes.STELLAR_PLASMA.get(), 1), List.of(
              Component.translatable("jei.pioneers.chamber.rate", PiCategory.mb(regime.inputPerRatePerTick),
                    StabilizerCoreTile.MIN_RATE, StabilizerCoreTile.MAX_RATE),
              Component.translatable("jei.pioneers.chamber.energy", energyPerRate(regime.energyPerRatePerTick)),
              Component.translatable("jei.pioneers.chamber.ignition", PiCategory.energy(StabilizerCoreTile.IGNITION_ENERGY_FE))));
    }

    private static Component every(int ticks) {
        return Component.translatable("jei.pioneers.analysis.rate", PiCategory.seconds(ticks));
    }

    static List<AnalysisCategory.Entry> asteroids() {
        List<AnalysisCategory.Entry> list = new ArrayList<>();
        Component feeds = Component.translatable("jei.pioneers.analysis.asteroids");
        for (int i = 0; i < TileEntityAsteroidCrusher.TYPES.length; i++) {
            var meteorite = PiWorldBlocks.METEORITES.get(TileEntityAsteroidCrusher.TYPES[i] + "_meteorite");
            if (meteorite != null) {
                list.add(new AnalysisCategory.Entry(List.of(new ItemStack(meteorite.asItem())), true,
                      DataStack.of(PiDataTypes.asteroid(i), AnalysisData.PER_SAMPLE), every(20), TileEntityAsteroidCrusher.ENERGY_PER_TICK_FE, feeds));
            }
        }
        return list;
    }

    private static Component node(ResearchNode node) {
        return Component.translatable(node.getTranslationKey());
    }

    static List<AnalysisCategory.Entry> geology() {
        List<AnalysisCategory.Entry> list = new ArrayList<>();
        for (MiningMaterial material : MiningMaterial.values()) {
            ResearchNode node = BodyResearch.node(material.body, ResearchNode.Kind.GEOLOGY);
            DataType data = PiDataTypes.geological(material.body);
            if (node == null || data == null) {
                continue;
            }
            RegistryObject<Item> raw = PiItems.RAW.get(material);
            if (raw != null) {
                list.add(new AnalysisCategory.Entry(List.of(new ItemStack(raw.get())), true, DataStack.of(data, AnalysisData.PER_SAMPLE), every(10),
                      TileEntityGeoSpectrometer.ENERGY_PER_TICK_FE, node(node)));
            }
            var block = PiBlocks.RAW_BLOCKS.get(material);
            if (block != null) {
                list.add(new AnalysisCategory.Entry(List.of(new ItemStack(block.asItem())), true, DataStack.of(data, 9 * AnalysisData.PER_SAMPLE),
                      every(10), TileEntityGeoSpectrometer.ENERGY_PER_TICK_FE, node(node)));
            }
        }
        return list;
    }

    static List<AnalysisCategory.Entry> biology() {
        List<AnalysisCategory.Entry> list = new ArrayList<>();
        for (SpacePlant plant : SpacePlant.values()) {
            ResearchNode node = BodyResearch.node(plant.planet.body(), ResearchNode.Kind.BIOLOGY);
            DataType data = PiDataTypes.biological(plant.planet.body());
            RegistryObject<Item> produce = PiItems.PRODUCE.get(plant);
            if (node != null && data != null && produce != null) {
                list.add(new AnalysisCategory.Entry(List.of(new ItemStack(produce.get())), true, DataStack.of(data, AnalysisData.PER_SAMPLE), every(5),
                      TileEntityBioSequencer.ENERGY_PER_TICK_FE, node(node)));
            }
        }
        return list;
    }

    static List<AnalysisCategory.Entry> biomes() {
        List<AnalysisCategory.Entry> list = new ArrayList<>();
        Component rate = Component.translatable("jei.pioneers.analysis.continuous",
              Component.translatable("jei.pioneers.per_tick", DataUnits.format(TileEntityBiomeAnalyzer.DATA_PER_TICK)));
        for (Map.Entry<String, Body> biome : SpaceBiomes.BODIES.entrySet()) {
            String id = "pioneers:" + biome.getKey();
            DataType data = PiDataTypes.biome(id);
            if (data == null) {
                continue;
            }
            ItemStack cell = ItemBiomeDataCell.recorded(PiItems.BIOME_DATA_CELL.get(), biome.getValue(), id);
            ResearchNode node = BodyResearch.node(biome.getValue(), ResearchNode.Kind.SURFACE);
            list.add(new AnalysisCategory.Entry(List.of(cell), false, DataStack.of(data, TileEntityBiomeAnalyzer.DATA_PER_TICK), rate,
                  TileEntityBiomeAnalyzer.ENERGY_PER_CELL_FE, node == null ? null : node(node)));
        }
        return list;
    }

    /**
     * Pages d'information des objets de la 0.10 : 1.10.2, seulement l'usage sans recette possible (scanner de gaz, blindage des drones) ; ce
     * qui se produit (hydrogene metallique, diamants de pluie, matieres du Simulateur) est dans les categories de la baie de drones et du
     * Simulateur, le kit d'aerostat dans « Gestes ».
     */
    static void itemInfo(IRecipeRegistration registration) {
        for (Item item : List.of(PiItems.GAS_SCANNER.get(), PiItems.DRONE_ARMOR_PLATING.get())) {
            String id = BuiltInRegistries.ITEM.getKey(item).getPath();
            registration.addIngredientInfo(new ItemStack(item), mezz.jei.api.constants.VanillaTypes.ITEM_STACK,
                  Component.translatable("jei.pioneers.info." + id));
        }
    }
}
