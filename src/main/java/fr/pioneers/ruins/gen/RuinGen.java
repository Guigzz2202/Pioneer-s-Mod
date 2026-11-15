package fr.pioneers.ruins.gen;

import fr.pioneers.Pioneers;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.multi.ShapeTemplate;
import fr.pioneers.ruins.RuinCatalog;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.server.ServerAboutToStartEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 1.9 (MAJ RP, bloc 1) : generation des ruines et de leurs annexes (premiere generation de structures du mod).
 * <ul>
 * <li>deux features ({@code pioneers:ruin}, {@code pioneers:ruin_annex}), ajoutees a tous les biomes de l'Overworld a l'etape des structures
 * de surface ({@code data/pioneers/forge/biome_modifier/add_ruins.json}) ;</li>
 * <li>sites sur une grille deterministe par tier ({@link RuinGrid} : graine du monde, espacement et separation en chunks, distance au point
 * de reference), terrain lu dans le bruit du generateur ({@link NoiseTerrain}) : la ruine et ses annexes, posees chacune dans son chunk,
 * s'accordent sans donnee sauvee ;</li>
 * <li>plan reel du multibloc, degrade ({@link RuinDesign}), pose par {@link RuinBuilder} ; annexes vanilla par {@link AnnexBuilder} ;</li>
 * <li>reglages : configuration commune ({@code ruins.*}) et {@code data/pioneers/ruins/generation.json} ({@link GenData}).</li>
 * </ul>
 */
public final class RuinGen {

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Pioneers.MODID);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> RUIN = FEATURES.register("ruin", RuinFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> ANNEX = FEATURES.register("ruin_annex", AnnexFeature::new);

    private RuinGen() {
    }

    public static void init(IEventBus modBus) {
        FEATURES.register(modBus);
        MinecraftForge.EVENT_BUS.addListener(GenData::onReload);
        MinecraftForge.EVENT_BUS.addListener(GenData::onStopped);
        MinecraftForge.EVENT_BUS.addListener(RuinGen::warm);
    }

    /**
     * Avant la creation des mondes (fil principal) : calcule une fois les gabarits des multiblocs ruinables, pour que les fils de generation
     * ne les construisent pas sous verrou au premier site rencontre.
     */
    private static void warm(ServerAboutToStartEvent event) {
        for (RuinCatalog.Entry e : RuinCatalog.entries().values()) {
            IndustryMachine m = IndustryMachine.byKey(e.machine());
            if (m != null) {
                for (ShapeTemplate t : ShapeTemplate.of(m)) {
                    t.cells();
                }
            }
        }
    }
}
