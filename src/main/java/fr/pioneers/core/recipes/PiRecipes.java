package fr.pioneers.core.recipes;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.tech.machine.ProcessingRecipe;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.Container;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

/**
 * Types de recettes du mod (tous data-driven : datapacks et KubeJS via {@code event.custom(...)}).
 * Identifiants : voir {@link PioneersAPI.RecipeTypes}.
 */
public final class PiRecipes {

    private PiRecipes() {
    }

    public static final DeferredRegister<RecipeType<?>> RECIPE_TYPES = DeferredRegister.create(Registries.RECIPE_TYPE, Pioneers.MODID);
    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS = DeferredRegister.create(Registries.RECIPE_SERIALIZER, Pioneers.MODID);

    public static final RegistryObject<RecipeType<AssemblyRecipe>> ASSEMBLY = type(PioneersAPI.RecipeTypes.ASSEMBLY);
    public static final RegistryObject<RecipeSerializer<AssemblyRecipe>> ASSEMBLY_SERIALIZER = RECIPE_SERIALIZERS.register("assembly",
          AssemblyRecipe.Serializer::new);

    //0.20.34 : compression gravitationnelle et synthese planetaire = recettes industrielles (multiblocs)
    public static final RegistryObject<RecipeType<SolarSimulationRecipe>> SOLAR_SIMULATION = type(PioneersAPI.RecipeTypes.SOLAR_SIMULATION);
    public static final RegistryObject<RecipeSerializer<SolarSimulationRecipe>> SOLAR_SIMULATION_SERIALIZER = RECIPE_SERIALIZERS.register(
          "solar_simulation", SolarSimulationRecipe.Serializer::new);

    /** Kit de machines : broyeur et compresseur (le four electrique prend les recettes de cuisson vanilla). */
    public static final RegistryObject<RecipeType<ProcessingRecipe>> CRUSHING = RECIPE_TYPES.register("crushing",
          () -> RecipeType.simple(Pioneers.rl("crushing")));
    public static final RegistryObject<RecipeSerializer<ProcessingRecipe>> CRUSHING_SERIALIZER = RECIPE_SERIALIZERS.register(
          "crushing", () -> new ProcessingRecipe.Serializer(true));
    public static final RegistryObject<RecipeType<ProcessingRecipe>> COMPRESSING = RECIPE_TYPES.register("compressing",
          () -> RecipeType.simple(Pioneers.rl("compressing")));
    public static final RegistryObject<RecipeSerializer<ProcessingRecipe>> COMPRESSING_SERIALIZER = RECIPE_SERIALIZERS.register(
          "compressing", () -> new ProcessingRecipe.Serializer(false));

    /** Age mecanique : une recette par machine (meule, tamiseur, concasseur, scie, tour, marteau-pilon), travail compte en tours. */
    public static final Map<MechanicalRecipe.Kind, RegistryObject<RecipeType<MechanicalRecipe>>> MECHANICAL =
          new EnumMap<>(MechanicalRecipe.Kind.class);
    public static final Map<MechanicalRecipe.Kind, RegistryObject<RecipeSerializer<MechanicalRecipe>>>
          MECHANICAL_SERIALIZERS = new EnumMap<>(MechanicalRecipe.Kind.class);

    static {
        for (var kind : MechanicalRecipe.Kind.values()) {
            MECHANICAL.put(kind, RECIPE_TYPES.register(kind.id(), () -> RecipeType.simple(Pioneers.rl(kind.id()))));
            MECHANICAL_SERIALIZERS.put(kind, RECIPE_SERIALIZERS.register(kind.id(), () -> new MechanicalRecipe.Serializer(kind)));
        }
    }

    private static <T extends Recipe<?>> RegistryObject<RecipeType<T>> type(ResourceLocation id) {
        return RECIPE_TYPES.register(id.getPath(), () -> RecipeType.simple(id));
    }

    // ------------------------------------------------------------------
    // Cache des recettes d'assemblage par poste (triees par identifiant)
    // ------------------------------------------------------------------

    //Caches separes client (1) / serveur (0) : en solo les deux gestionnaires de recettes alternent
    private static final RecipeManager[] cachedManager = new RecipeManager[2];
    private static final int[] cachedGeneration = {-1, -1};
    private static int generation;
    @SuppressWarnings("unchecked")
    private static final Map<AssemblyStation, List<AssemblyRecipe>>[] BY_STATION = new Map[]{new EnumMap<>(AssemblyStation.class),
                                                                                             new EnumMap<>(AssemblyStation.class)};

    /** A appeler quand les recettes changent (rechargement, synchronisation client). */
    public static void invalidate() {
        generation++;
    }

    /** Numero du dernier rechargement (caches des autres registres de recettes). */
    public static int generation() {
        return generation;
    }

    public static synchronized List<AssemblyRecipe> assembly(Level level, AssemblyStation station) {
        RecipeManager manager = level.getRecipeManager();
        int side = level.isClientSide ? 1 : 0;
        Map<AssemblyStation, List<AssemblyRecipe>> byStation = BY_STATION[side];
        if (manager != cachedManager[side] || cachedGeneration[side] != generation) {
            cachedManager[side] = manager;
            cachedGeneration[side] = generation;
            byStation.clear();
            for (AssemblyStation s : AssemblyStation.values()) {
                byStation.put(s, new ArrayList<>());
            }
            for (AssemblyRecipe recipe : manager.getAllRecipesFor(ASSEMBLY.get())) {
                byStation.get(recipe.getStation()).add(recipe);
            }
            for (List<AssemblyRecipe> list : byStation.values()) {
                list.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
            }
        }
        return byStation.getOrDefault(station, List.of());
    }

    @Nullable
    public static AssemblyRecipe assembly(Level level, AssemblyStation station, @Nullable ResourceLocation id) {
        List<AssemblyRecipe> list = assembly(level, station);
        if (list.isEmpty()) {
            return null;
        }
        if (id != null) {
            for (AssemblyRecipe recipe : list) {
                if (recipe.getId().equals(id)) {
                    return recipe;
                }
            }
        }
        return list.get(0);
    }

    @SuppressWarnings("unchecked")
    private static final Map<RecipeType<?>, List<?>>[] SORTED = new Map[]{new HashMap<>(), new HashMap<>()};
    private static final RecipeManager[] sortedManager = new RecipeManager[2];
    private static final int[] sortedGeneration = {-1, -1};

    /** Toutes les recettes d'un type, triees par identifiant (mises en cache jusqu'au prochain rechargement). */
    @SuppressWarnings("unchecked")
    public static synchronized <T extends Recipe<Container>> List<T> sorted(Level level, RecipeType<T> type) {
        RecipeManager manager = level.getRecipeManager();
        int side = level.isClientSide ? 1 : 0;
        if (manager != sortedManager[side] || sortedGeneration[side] != generation) {
            sortedManager[side] = manager;
            sortedGeneration[side] = generation;
            SORTED[side].clear();
        }
        return (List<T>) SORTED[side].computeIfAbsent(type, t -> {
            List<T> list = new ArrayList<>(manager.getAllRecipesFor(type));
            list.sort(Comparator.comparing(recipe -> recipe.getId().toString()));
            return Collections.unmodifiableList(list);
        });
    }
}
