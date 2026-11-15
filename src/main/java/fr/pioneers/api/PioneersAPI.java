package fr.pioneers.api;

import fr.pioneers.Pioneers;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.environment.BodyEnvironment;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.api.pressure.IPressureHandler;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.registries.IForgeRegistry;
import net.minecraftforge.registries.RegistryManager;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Point d'entree de l'API de Pioneer's, pour les addons, les integrations et les scripts (KubeJS...).
 * <ul>
 *     <li><b>Donnees</b> : registre des {@link DataType}, capacite {@link #DATA_HANDLER} ;</li>
 *     <li><b>Gaz</b> : registre des {@link Gas}, capacite {@link #GAS_HANDLER} (socle propre, sans Mekanism) ;</li>
 *     <li><b>Environnement</b> : gravite, air respirable, combinaison exigee ({@link #environment(Level)}, {@link #isBreathable}) ;</li>
 *     <li><b>Recettes</b> : identifiants des types de recettes data-driven ({@link RecipeTypes}).</li>
 * </ul>
 */
public final class PioneersAPI {

    private PioneersAPI() {
    }

    public static final String MODID = "pioneers";

    /** Cle du registre des types de donnees. */
    public static final ResourceKey<Registry<DataType>> DATA_TYPE_REGISTRY_NAME = ResourceKey.createRegistryKey(Pioneers.rl("data_type"));

    /** Capacite « donnees » des blocs et des objets. */
    public static final Capability<IDataHandler> DATA_HANDLER = CapabilityManager.get(new CapabilityToken<>() {});

    /** Cle du registre des gaz. */
    public static final ResourceKey<Registry<Gas>> GAS_REGISTRY_NAME = ResourceKey.createRegistryKey(Pioneers.rl("gas"));

    /** Capacite « pression » des blocs (air comprime). */
    public static final Capability<IPressureHandler> PRESSURE_HANDLER = CapabilityManager.get(new CapabilityToken<>() {});

    /** Capacite « chaleur » des blocs. */
    public static final Capability<IHeatHandler> HEAT_HANDLER = CapabilityManager.get(new CapabilityToken<>() {});

    /** Capacite « gaz » des blocs et des objets. */
    public static final Capability<IGasHandler> GAS_HANDLER = CapabilityManager.get(new CapabilityToken<>() {});

    private static IForgeRegistry<Gas> gasRegistry;
    /** Gaz d'autres mods (passerelles de compatibilite) : identifiant -> gaz, ou null. */
    private static Function<ResourceLocation, Gas> foreignGases = id -> null;

    @NotNull
    public static IForgeRegistry<Gas> gasRegistry() {
        if (gasRegistry == null) {
            gasRegistry = RegistryManager.ACTIVE.getRegistry(GAS_REGISTRY_NAME);
        }
        return Objects.requireNonNull(gasRegistry, "Le registre des gaz n'est pas encore cree");
    }

    /** Gaz de cet identifiant : enregistre ici, sinon fourni par une passerelle (gaz d'un autre mod), sinon null. */
    @Nullable
    public static Gas gas(ResourceLocation id) {
        Gas gas = gasRegistry().containsKey(id) ? gasRegistry().getValue(id) : null;
        if (gas != null && !gas.isEmptyType()) {
            return gas;
        }
        return foreignGases.apply(id);
    }

    /** Reserve aux passerelles de compatibilite. */
    public static void setForeignGases(Function<ResourceLocation, Gas> resolver) {
        foreignGases = Objects.requireNonNull(resolver);
    }

    /** Gaz d'autres mods a proposer dans les listes de choix (passerelles). */
    private static Supplier<Collection<Gas>> foreignGasList = List::of;

    public static void setForeignGasList(Supplier<Collection<Gas>> list) {
        foreignGasList = Objects.requireNonNull(list);
    }

    /** Tous les gaz connus, visibles : ceux d'ici puis ceux des passerelles (sans doublon d'identifiant). */
    public static List<Gas> allGases() {
        LinkedHashMap<ResourceLocation, Gas> all = new LinkedHashMap<>();
        for (Gas gas : gasRegistry().getValues()) {
            if (!gas.isEmptyType() && !gas.isHidden()) {
                all.put(gas.getRegistryName(), gas);
            }
        }
        for (Gas gas : foreignGasList.get()) {
            if (!gas.isEmptyType() && !gas.isHidden() && gas.getRegistryName() != null) {
                all.putIfAbsent(gas.getRegistryName(), gas);
            }
        }
        return new ArrayList<>(all.values());
    }

    private static IForgeRegistry<DataType> dataTypeRegistry;
    private static IHelper helper = IHelper.DEFAULT;

    @NotNull
    public static IForgeRegistry<DataType> dataTypeRegistry() {
        if (dataTypeRegistry == null) {
            dataTypeRegistry = RegistryManager.ACTIVE.getRegistry(DATA_TYPE_REGISTRY_NAME);
        }
        return Objects.requireNonNull(dataTypeRegistry, "Le registre des types de donnees n'est pas encore cree");
    }

    /** Environnement de la dimension (gravite, atmosphere, combinaison requise). */
    public static BodyEnvironment environment(Level level) {
        return helper.environment(level);
    }

    /** L'air est-il respirable a cet endroit (atmosphere terrestre ou piece pressurisee) ? */
    public static boolean isBreathable(Level level, BlockPos pos) {
        return helper.isBreathable(level, pos);
    }

    /** Tier de combinaison complete porte par l'entite (0 = aucune). */
    public static int suitTier(LivingEntity entity) {
        return helper.suitTier(entity);
    }

    /** Reserve au mod : branche l'implementation. */
    public static void setHelper(IHelper implementation) {
        helper = Objects.requireNonNull(implementation);
    }

    /** Implementation fournie par Pioneer's. */
    public interface IHelper {

        IHelper DEFAULT = new IHelper() {
        };

        default BodyEnvironment environment(Level level) {
            return BodyEnvironment.EARTH;
        }

        default boolean isBreathable(Level level, BlockPos pos) {
            return true;
        }

        default int suitTier(LivingEntity entity) {
            return 0;
        }
    }

    /** Identifiants des types de recettes (utilisables tels quels dans les datapacks et KubeJS : {@code event.custom({type: ...})}). */
    public static final class RecipeTypes {

        private RecipeTypes() {
        }

        /** Tables d'assemblage (fusees, rover, modules, equipements) : grille de 5x10 en forme de produit. */
        public static final ResourceLocation ASSEMBLY = Pioneers.rl("assembly");
        /** Simulateur du systeme solaire : production d'une planete simulee par cycle. */
        public static final ResourceLocation SOLAR_SIMULATION = Pioneers.rl("solar_simulation");
    }
}
