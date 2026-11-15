package fr.pioneers.space;

import fr.pioneers.Pioneers;
import fr.pioneers.geology.OutcropFeature;
import net.minecraft.world.level.levelgen.feature.Feature;
import net.minecraft.world.level.levelgen.feature.configurations.NoneFeatureConfiguration;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Elements de generation du monde (crateres lunaires).
 */
public final class PiFeatures {

    private PiFeatures() {
    }

    public static final DeferredRegister<Feature<?>> FEATURES = DeferredRegister.create(ForgeRegistries.FEATURES, Pioneers.MODID);

    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CRATER = FEATURES.register("crater", CraterFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> BOULDER = FEATURES.register("boulder", BoulderFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> PANCAKE_DOME = FEATURES.register("pancake_dome", PancakeDomeFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> PHOBOS_GROOVE = FEATURES.register("phobos_groove", PhobosGrooveFeature::new);
    /** Lac parfaitement plat (lave, methane). */
    public static final RegistryObject<Feature<FlatPoolFeature.Config>> FLAT_POOL = FEATURES.register("flat_pool", FlatPoolFeature::new);
    public static final RegistryObject<Feature<LineamentFeature.Config>> LINEAMENT = FEATURES.register("lineament", LineamentFeature::new);
    public static final RegistryObject<Feature<SpireFeature.Config>> SPIRE = FEATURES.register("spire", SpireFeature::new);
    public static final RegistryObject<Feature<PlumeStreakFeature.Config>> PLUME_STREAK = FEATURES.register("plume_streak", PlumeStreakFeature::new);
    /** Derniere passe : aucun liquide en marche ou suspendu. */
    public static final RegistryObject<Feature<FluidCleanupFeature.Config>> FLUID_CLEANUP = FEATURES.register("fluid_cleanup", FluidCleanupFeature::new);
    /** Affleurements au-dessus des gisements (age du bronze). */
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> OUTCROPS = FEATURES.register("outcrops", OutcropFeature::new);
    public static final RegistryObject<Feature<NoneFeatureConfiguration>> CO2_GEYSER = FEATURES.register("co2_geyser", Co2GeyserFeature::new);
}
