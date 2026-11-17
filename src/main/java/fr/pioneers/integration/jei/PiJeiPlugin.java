package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.MachineRegistry;
import fr.pioneers.api.machine.MachineSpec;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.KilnRecipe;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.client.gui.core.GuiMachine;
import fr.pioneers.guide.client.GuideLinks;
import fr.pioneers.space.content.universe.GalaxyType;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.core.vanilla.VanillaReplacements;
import fr.pioneers.world.geology.DepositType;
import fr.pioneers.world.geology.DepositTypes;
import fr.pioneers.world.geology.fluid.FluidDepositType;
import fr.pioneers.world.geology.fluid.FluidDepositTypes;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.Cleanroom;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.tech.mechanics.PiMechanics;
import fr.pioneers.core.recipes.AssemblyRecipe;
import fr.pioneers.core.recipes.AssemblyStation;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.recipes.SolarSimulationRecipe;
import fr.pioneers.space.base.PiBaseMachines;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.space.content.PiCosmicBlocks;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.tech.kinetics.PiKinetics;
import fr.pioneers.logistics.robotics.PiRobotics;
import fr.pioneers.space.research.PiScienceMachines;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.content.PiStabilizer;
import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.space.assembly.PiWorksMachines;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IModIngredientRegistration;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import mezz.jei.api.registration.IRecipeTransferRegistration;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;

/**
 * Integration JEI : les recettes propres au mod (assemblage par poste, Gravity Compressor, synthese planetaire, Simulateur du systeme
 * solaire), ce que le collecteur de gaz tire de son environnement, les chambres, les machines a donnees, et les donnees elles-memes comme
 * ingredient ; depuis la 0.10, la recolte des geantes gazeuses, l'absorbeur solaire et les galaxies du Simulateur d'univers. Charge par JEI
 * seulement s'il est installe.
 */
@JeiPlugin
public class PiJeiPlugin implements IModPlugin {

    public static final Map<AssemblyStation, RecipeType<AssemblyRecipe>> ASSEMBLY = new EnumMap<>(AssemblyStation.class);
    public static final RecipeType<SolarSimulationRecipe> SOLAR_SIMULATION = JeiOrder.create("solar_simulation",
          SolarSimulationRecipe.class);

    public static final RecipeType<ChamberCategory.Entry> BLACK_HOLE = JeiOrder.create("black_hole",
          ChamberCategory.Entry.class);
    public static final RecipeType<ChamberCategory.Entry> STELLAR_CHAMBER = JeiOrder.create("stellar_chamber",
          ChamberCategory.Entry.class);
    public static final RecipeType<AnalysisCategory.Entry> ASTEROID_ANALYSIS = JeiOrder.create("asteroid_analysis",
          AnalysisCategory.Entry.class);
    public static final RecipeType<AnalysisCategory.Entry> GEOLOGY_ANALYSIS = JeiOrder.create("geology_analysis",
          AnalysisCategory.Entry.class);
    public static final RecipeType<AnalysisCategory.Entry> BIOLOGY_ANALYSIS = JeiOrder.create("biology_analysis",
          AnalysisCategory.Entry.class);
    public static final RecipeType<AnalysisCategory.Entry> BIOME_ANALYSIS = JeiOrder.create("biome_analysis",
          AnalysisCategory.Entry.class);

    public static final RecipeType<GalaxyType> UNIVERSE = JeiOrder.create("universe_simulation", GalaxyType.class);
    public static final RecipeType<DroneHarvestCategory.Entry> DRONE_HARVEST = JeiOrder.create("drone_harvest",
          DroneHarvestCategory.Entry.class);
    public static final Map<MachineType, RecipeType<MechanicalRecipe>> MECHANICAL =
          new EnumMap<>(MachineType.class);

    static {
        for (MachineType type : MachineType.values()) {
            if (type.recipe != null) {
                MECHANICAL.put(type, JeiOrder.create(type.recipe.id(), MechanicalRecipe.class));
            }
        }
    }

    public static final Map<BenchType, RecipeType<BenchRecipe>> BENCH =
          new EnumMap<>(BenchType.class);

    static {
        for (BenchType type : BenchType.values()) {
            BENCH.put(type, JeiOrder.create("bench_" + type.key(), BenchRecipe.class));
        }
    }

    /** Age industriel : une categorie par machine (atelier et multibloc ensemble, four electrique compris), sauf les sources d'energie. */
    public static final Map<IndustryMachine, RecipeType<IndustrialRecipe>> INDUSTRY =
          new EnumMap<>(IndustryMachine.class);

    static {
        for (IndustryMachine m : IndustryMachine.values()) {
            switch (m.source) {
                //MAJ Recettes (lot 1) : le four electrique a sa categorie (cuissons filtrees par IndustryRecipes : nourriture et age du bronze)
                //1.0.6 : la Taillerie de pierre (recettes du tailleur de pierre converties) a sa page
                case INDUSTRIAL, CRUSHING, COMPRESSING, SMELTING, STONECUTTING -> INDUSTRY.put(m, JeiOrder.create("industry_" + m.key,
                      IndustrialRecipe.class));
                default -> {
                }
            }
        }
    }

    /**
     * 1.0.4-beta (lot 32, Guillaume : « Garder, masquer partout ») : page PROPRE au multibloc de salle blanche d'une machine qui a aussi des
     * compactes (graveur optique) : memes recettes (celles a sa portee), sans tier, duree et FE/t reels ; la page commune
     * ({@code industry_<cle>}) garde la pastille MK pour les compactes. Catalyseur : le controleur (fiche de l'API Machine,
     * T:api/catalog_adapter.py jei_types).
     */
    public static final Map<IndustryMachine, RecipeType<IndustrialRecipe>> CLEANROOM = new EnumMap<>(IndustryMachine.class);

    static {
        for (IndustryMachine m : INDUSTRY.keySet()) {
            if (m.atelier != IndustryMachine.Atelier.NONE && Cleanroom.is(m)) {
                CLEANROOM.put(m, JeiOrder.create("cleanroom_" + m.key, IndustrialRecipe.class));
            }
        }
    }

    public static final Map<SteamMachine, RecipeType<SteamRecipe>> STEAM =
          new EnumMap<>(SteamMachine.class);

    static {
        for (SteamMachine m : SteamMachine.values()) {
            if (m != SteamMachine.FURNACE) {
                STEAM.put(m, JeiOrder.create(m.id(), SteamRecipe.class));
            }
        }
    }

    public static final RecipeType<KilnRecipe> KILN = JeiOrder.create("kiln",
          KilnRecipe.class);
    public static final RecipeType<CastingRecipe> CASTING = JeiOrder.create("casting",
          CastingRecipe.class);
    public static final RecipeType<BronzeCategories.Blast> BLAST = JeiOrder.create("blast_furnace", BronzeCategories.Blast.class);
    public static final RecipeType<DepositType> DEPOSIT = JeiOrder.create("deposit",
          DepositType.class);
    public static final RecipeType<FluidDepositType> FLUID_DEPOSIT = JeiOrder.create("fluid_deposit",
          FluidDepositType.class);

    public static final RecipeType<Body> SOLAR_ABSORBER = JeiOrder.create("solar_absorber", Body.class);
    /** Categories explicatives : combustibles, ressources sauvages, chaleur, air comprime. */
    public static final RecipeType<GuideCategory.Entry> FUELS = JeiOrder.create("fuels", GuideCategory.Entry.class);
    /** 1.4 : meule de charbonnier (schema). */
    public static final RecipeType<CharcoalPitCategory.Entry> CHARCOAL_PIT = JeiOrder.create("charcoal_pit",
          CharcoalPitCategory.Entry.class);
    /** MAJ simplification : categorie « Chaleur » = les radiateurs MKI a MKIV et leurs chiffres (GuideEntries.radiators). */
    public static final RecipeType<GuideCategory.Entry> THERMAL = JeiOrder.create("thermal", GuideCategory.Entry.class);
    public static final RecipeType<GuideCategory.Entry> AIR = JeiOrder.create("compressed_air", GuideCategory.Entry.class);
    /** 0.20.24 : reacteurs a fission et a fusion (combustible, cibles, couvertures, fusion, surgeneration). */
    public static final RecipeType<ReactorCategory.Entry> REACTORS = JeiOrder.create("reactors", ReactorCategory.Entry.class);

    static {
        for (AssemblyStation station : AssemblyStation.values()) {
            ASSEMBLY.put(station, JeiOrder.create("assembly_" + station.id(), AssemblyRecipe.class));
        }
    }

    @NotNull
    @Override
    public ResourceLocation getPluginUid() {
        return Pioneers.rl("jei_plugin");
    }

    private static ItemLike station(AssemblyStation station) {
        //1.9.2 : fusees et robots : controleurs des multiblocs d'assemblage
        return PiWorksMachines.stationBlock(station);
    }

    @Override
    public void registerIngredients(@NotNull IModIngredientRegistration registration) {
        registration.register(DataIngredient.TYPE, DataIngredient.all(), new DataIngredient.Helper(), new DataIngredient.Renderer());
        registration.register(GasIngredient.TYPE, GasIngredient.all(), new GasIngredient.Helper(), new GasIngredient.Renderer());
    }

    @Override
    public void registerCategories(IRecipeCategoryRegistration registration) {
        IGuiHelper helper = registration.getJeiHelpers().getGuiHelper();
        for (AssemblyStation station : AssemblyStation.values()) {
            registration.addRecipeCategories(new AssemblyCategory(helper, ASSEMBLY.get(station), station, station(station)));
        }
        registration.addRecipeCategories(new SolarSimulationCategory(helper, SOLAR_SIMULATION),
              new ChamberCategory(helper, BLACK_HOLE, "jei.pioneers.black_hole", PiStabilizer.SINGULARITY_CORE),
              new ChamberCategory(helper, STELLAR_CHAMBER, "jei.pioneers.stellar_chamber", PiStabilizer.STELLAR_CORE),
              new AnalysisCategory(helper, ASTEROID_ANALYSIS, "block.pioneers.asteroid_crusher", PiScienceMachines.ASTEROID_CRUSHER),
              new AnalysisCategory(helper, GEOLOGY_ANALYSIS, "block.pioneers.geo_spectrometer", PiScienceMachines.GEO_SPECTROMETER),
              new AnalysisCategory(helper, BIOLOGY_ANALYSIS, "block.pioneers.bio_sequencer", PiScienceMachines.BIO_SEQUENCER),
              new AnalysisCategory(helper, BIOME_ANALYSIS, "block.pioneers.biome_analyzer", PiScienceMachines.BIOME_ANALYZER),
              new DroneHarvestCategory(helper, DRONE_HARVEST),
              new AbsorberCategory(helper, SOLAR_ABSORBER),
              new UniverseCategory(helper, UNIVERSE));
        //1.0.3-beta (lot 3) : concasseur, broyeur a vapeur et broyeur MKN = une seule categorie « Broyage » (GrindingCategory)
        MECHANICAL.forEach((type, recipeType) -> {
            if (!merged(recipeType)) {
                registration.addRecipeCategories(new MechanicalCategory(helper, recipeType, type));
            }
        });
        //1.0.6 (demande de Guillaume du 16/11/2026 : « JEI ne groupe toujours pas les machines a vapeur et les machines MK ») : plus de page
        //« Broyage » a part : la famille du broyage est la page du broyeur, comme toutes les familles de tiers (familyPage)
        registration.addRecipeCategories(new ProcessCategory(helper));  //1.0.3-beta (lot 10) : une page « Procede » par materiau complexe
        registration.addRecipeCategories(new PartsCategory(helper));   //1.0.3-beta (lot 4) : une page par piece metallique, toutes ses voies
        MeltingCategories.categories(registration, helper);
        KitchenCategory.categories(registration, helper);
        BENCH.forEach((type, recipeType) -> registration.addRecipeCategories(new BronzeCategories.Bench(helper, recipeType, type)));
        STEAM.forEach((m, recipeType) -> {
            if (!merged(recipeType)) {
                registration.addRecipeCategories(new BronzeCategories.Steam(helper, recipeType, m));
            }
        });
        INDUSTRY.forEach((m, recipeType) -> {
            if (!merged(recipeType)) {
                registration.addRecipeCategories(new IndustryCategory(helper, recipeType, m, IndustryCategory.icon(m)));
            }
        });
        CLEANROOM.forEach((m, recipeType) -> registration.addRecipeCategories(new IndustryCategory(helper, recipeType, m,
              PiIndustry.controller(m).getBlock(), true)));
        registration.addRecipeCategories(new BronzeCategories.Kiln(helper, KILN), new BronzeCategories.Casting(helper, CASTING),
              new BronzeCategories.BlastCategory(helper, BLAST), new BronzeCategories.Deposit(helper, DEPOSIT));
        registration.addRecipeCategories(new ChemistryCategories.FluidDeposit(helper, FLUID_DEPOSIT));
        ForgeCategories.categories(registration, helper);
        registration.addRecipeCategories(new ReactorCategory(helper, REACTORS));
        registration.addRecipeCategories(new CharcoalPitCategory(helper, CHARCOAL_PIT));
        registration.addRecipeCategories(new DryingLineCategory(helper));          //1.0.3-beta (lot 7) : sechage sur la corde a linge
        registration.addRecipeCategories(new GuideCategory(helper, FUELS, "jei.pioneers.fuels", PiVanillaMaterials.COAL.get()),
              new GuideCategory(helper, THERMAL, "jei.pioneers.thermal", PiIndustry.RADIATORS.get(CableTier.BASIC)),
              new GuideCategory(helper, AIR, "jei.pioneers.compressed_air", PiTransport.AIR_COMPRESSOR),
              new GuideCategory(helper, BoilerJei.TYPE, "jei.pioneers.boiler", PiBronze.FIREBOX));    //1.0.3-beta (lot 14) : chaudiere
        //1.11.3 (lot E3) : page « Multiblocs » (apercu 3D, blocs necessaires)
        registration.addRecipeCategories(new MultiblockCategory(helper));
        //1.10.2 : productions sans recette JSON (butins, monde, cultures, gestes, ruines, generateurs, sondes, recherche...)
        Productions.categories(registration, helper);
    }

    /**
     * API Machine (lot 1) : catalyseurs TIRES DES FICHES ({@link MachineRegistry}) : chaque bloc d'une fiche est catalyseur des types de
     * recettes qu'elle declare ({@code jei} dans tools/gc/api : ateliers, kit, forge au charbon, four a coke, controleurs, combustibles,
     * reacteurs, machines mecaniques, machines et ateliers a vapeur, four de potier, fonderie, analyses, renouvelables, rotation). Remplace
     * l'ancienne boucle sur le catalogue et les lignes ecrites a la main de ces familles ; restent ecrites a la main les pieces de multibloc,
     * les outils et les postes hors API (etablis, forge primaire, cuisine).
     */
    private static void apiCatalysts(IRecipeCatalystRegistration registration) {
        Map<String, RecipeType<?>> types = recipeTypes();
        for (MachineSpec spec : MachineRegistry.all()) {
            for (String uid : spec.jei()) {
                RecipeType<?> type = types.containsKey(uid) ? types.get(uid) : Productions.type(uid);
                if (type == null) {
                    Pioneers.LOGGER.warn("JEI : type de recette inconnu {} (fiche {})", uid, spec.key());
                    continue;
                }
                for (Block b : MachineRegistry.blocks(spec)) {
                    registration.addRecipeCatalyst(new ItemStack(b), type);
                }
            }
        }
    }

    /** Types de recettes que les fiches peuvent citer, par identifiant ({@code pioneers:industry_<cle>}, {@code minecraft:furnace}...). */
    private static Map<String, RecipeType<?>> recipeTypes() {
        Map<String, RecipeType<?>> out = new HashMap<>();
        List<RecipeType<?>> all = new ArrayList<>();
        all.addAll(INDUSTRY.values());
        all.addAll(CLEANROOM.values());
        all.addAll(MECHANICAL.values());
        all.addAll(STEAM.values());
        all.addAll(BENCH.values());
        all.addAll(List.of(ASTEROID_ANALYSIS, GEOLOGY_ANALYSIS, BIOLOGY_ANALYSIS, BIOME_ANALYSIS, REACTORS, FUELS, BoilerJei.TYPE, KILN, CASTING, BLAST, DEPOSIT,
              FLUID_DEPOSIT, mezz.jei.api.constants.RecipeTypes.SMELTING, mezz.jei.api.constants.RecipeTypes.FUELING,
              mezz.jei.api.constants.RecipeTypes.STONECUTTING));   //1.0.6 : la Taillerie de pierre, catalyseur du tailleur de pierre
        //1.0.5-beta (lot 13, journal de Guillaume : « type de recette inconnu pioneers:forge_hearth ») : pages de la forge primaire citees par
        //les fiches (foyer de forge) ; le catalyseur du foyer vient donc de sa fiche (plus de ligne a la main dans ForgeCategories)
        all.addAll(List.of(ForgeCategories.HEARTH, ForgeCategories.ANVIL, ForgeCategories.QUENCH));
        for (RecipeType<?> t : all) {
            RecipeType<?> page = familyPage(t);
            out.put(JeiOrder.uid(t), page != null ? page : t);
        }
        out.put(JeiOrder.uid(GrindingCategory.TYPE), INDUSTRY.get(IndustryMachine.CRUSHER));   //1.0.6 : alias de l'ancienne page « Broyage »
        //1.0.3-beta (lot 6) : anciennes pages de fonte par poste -> « Fusion des metaux » ; « Coulee en moule » -> « Coulee »
        MeltingCategories.MERGED.forEach(uid -> out.put(uid, MeltingCategories.MELTING));
        out.put(JeiOrder.uid(MeltingCategories.MELTING), MeltingCategories.MELTING);
        out.put("pioneers:forge_mold", CASTING);
        return out;
    }

    /** Categorie reunie dans la page de sa famille ({@link #familyPage}) : pas de page a elle (son identifiant reste un alias pour les fiches). */
    static boolean merged(RecipeType<?> type) {
        return familyPage(type) != null;
    }

    /**
     * 1.0.6 (demande de Guillaume du 16/11/2026 : « JEI ne groupe toujours pas les machines a vapeur et les machines MK, cf. laminoir ») : UNE
     * page par famille de tiers (T:gc/api/tier_families.py) : la page de sa machine MKI ; les pages basses (machine mecanique : meule,
     * concasseur, tamis, scie, tour ; machine a vapeur) y sont reunies, leurs recettes montrees converties (filigrane mecanique ou vapeur,
     * energie et duree au MKI) et leurs machines en catalyseurs. Null : page a part.
     */
    @org.jetbrains.annotations.Nullable
    static RecipeType<?> familyPage(RecipeType<?> type) {
        for (Map.Entry<SteamMachine, RecipeType<SteamRecipe>> e : STEAM.entrySet()) {
            if (e.getValue() == type) {
                return industryOf(fr.pioneers.api.machine.tier.GeneratedTierFamilies.STEAM_FAMILY.get("steam:" + e.getKey().key()));
            }
        }
        for (Map.Entry<MachineType, RecipeType<MechanicalRecipe>> e : MECHANICAL.entrySet()) {
            if (e.getValue() == type) {
                for (fr.pioneers.api.machine.tier.GeneratedTierFamilies.Family f : fr.pioneers.api.machine.tier.GeneratedTierFamilies.FAMILIES) {
                    if (f.engine() && f.low().contains("mechanical:" + e.getKey().recipe.id())) {
                        return industryOf(f.key());
                    }
                }
            }
        }
        return null;
    }

    @org.jetbrains.annotations.Nullable
    private static RecipeType<?> industryOf(@org.jetbrains.annotations.Nullable String family) {
        fr.pioneers.api.machine.tier.GeneratedTierFamilies.Family f = fr.pioneers.api.machine.tier.TierRecipes.family(family);
        IndustryMachine m = f == null || f.industry() == null ? null : IndustryMachine.byKey(f.industry());
        return m == null ? null : INDUSTRY.get(m);
    }


    /**
     * 1.0.3-beta (lot 5, generateurs separes ; demande de Guillaume : « JEI : une categorie par sorte avec les combustibles et leurs FE ») : la
     * categorie du generateur a combustion SOLIDE montre ses recettes (aucune aujourd'hui) puis une page par combustible solide (duree de
     * combustion vanilla, FE/t et FE au total par GeneratorOutput), du plus au moins energetique ; liquides et gaz ont leurs categories
     * (generateur a combustion liquide, turbine a gaz) par leurs recettes.
     */
    private static List<IndustrialRecipe> solidFuels(Level level) {
        List<IndustrialRecipe> out = new ArrayList<>(IndustryRecipes.of(level, IndustryMachine.SOLID_COMBUSTION_GENERATOR));
        List<IndustrialRecipe> solids = new ArrayList<>();
        for (net.minecraft.world.item.Item item : net.minecraftforge.registries.ForgeRegistries.ITEMS) {
            IndustrialRecipe r = fr.pioneers.tech.industry.GeneratorOutput.solidFuel(new ItemStack(item));
            if (r != null) {
                solids.add(r);
            }
        }
        solids.sort(java.util.Comparator.comparingLong((IndustrialRecipe r) -> -fr.pioneers.tech.industry.GeneratorOutput.total(r, false))
              .thenComparing(r -> r.getId().toString()));
        out.addAll(solids);
        return out;
    }
    @Override
    public void registerRecipes(IRecipeRegistration registration) {
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        RecipeManager manager = level.getRecipeManager();
        for (AssemblyStation station : AssemblyStation.values()) {
            List<AssemblyRecipe> recipes = new ArrayList<>();
            for (AssemblyRecipe recipe : manager.getAllRecipesFor(PiRecipes.ASSEMBLY.get())) {
                if (recipe.getStation() == station) {
                    recipes.add(recipe);
                }
            }
            registration.addRecipes(ASSEMBLY.get(station), recipes);
        }
        registration.addRecipes(SOLAR_SIMULATION, manager.getAllRecipesFor(PiRecipes.SOLAR_SIMULATION.get()));
        registration.addRecipes(BLACK_HOLE, List.of(JeiEntries.blackHole()));
        registration.addRecipes(STELLAR_CHAMBER, List.of(JeiEntries.stellarChamber()));
        registration.addRecipes(ASTEROID_ANALYSIS, JeiEntries.asteroids());
        registration.addRecipes(GEOLOGY_ANALYSIS, JeiEntries.geology());
        registration.addRecipes(BIOLOGY_ANALYSIS, JeiEntries.biology());
        registration.addRecipes(BIOME_ANALYSIS, JeiEntries.biomes());
        registration.addRecipes(DRONE_HARVEST, DroneHarvestCategory.entries());
        registration.addRecipes(SOLAR_ABSORBER, AbsorberCategory.entries());
        registration.addRecipes(UNIVERSE, List.of(GalaxyType.values()));
        MECHANICAL.forEach((type, recipeType) -> {
            if (!merged(recipeType)) {
                registration.addRecipes(recipeType, manager.getAllRecipesFor(PiRecipes.MECHANICAL.get(type.recipe).get()));
            }
        });
        registration.addRecipes(PartsCategory.TYPE, PartsCategory.recipes(level));
        //1.0.3-beta (correctif) : une page « Procede » illisible ne doit plus couper toutes les recettes enregistrees apres elle
        try {
            registration.addRecipes(ProcessCategory.TYPE, ProcessCategory.recipes());
        } catch (RuntimeException e) {
            Pioneers.LOGGER.error("JEI : pages « Procede » ignorees", e);
        }
        MeltingCategories.recipes(registration, level);
        KitchenCategory.recipes(registration, manager);
        registration.addRecipes(DryingLineCategory.TYPE, DryingLineCategory.recipes(manager));
        for (BenchType type : BenchType.values()) {
            registration.addRecipes(BENCH.get(type), BenchTile.recipes(level, type));
        }
        STEAM.forEach((m, recipeType) -> {
            if (!merged(recipeType)) {
                registration.addRecipes(recipeType, SteamProcessTile.ownRecipes(level, m));
            }
        });
        INDUSTRY.forEach((m, recipeType) -> {
            if (!merged(recipeType)) {
                //1.0.3-beta (lot 6) : les fontes du creuset electrique (une entree -> un metal fondu) sont dans « Fusion des metaux »
                //1.0.5-beta (lot 38) : sans les recettes basses converties (elles sont sur les pages de leur famille basse, la machine en catalyseur)
                List<IndustrialRecipe> shown = m == IndustryMachine.SOLID_COMBUSTION_GENERATOR ? solidFuels(level) : IndustryRecipes.withoutLow(IndustryRecipes.of(level, m));
                //1.0.6 : page de la famille de tiers : aussi les recettes basses converties (mecaniques, a vapeur), sauf les cuissons vanilla (elles
                //ont la page du four, la machine en catalyseur)
                if (IndustryRecipes.tierFamily(m) != null) {
                    shown = IndustryRecipes.of(level, m).stream().filter(r -> !r.converted()
                          || !(manager.byKey(r.getId()).orElse(null) instanceof net.minecraft.world.item.crafting.SmeltingRecipe)).toList();
                }
                if (m == IndustryMachine.ELECTRIC_CRUCIBLE) {
                    shown = shown.stream().filter(r -> !MeltingCategories.isMelt(r)).toList();
                }
                //1.0.4-beta (lot 11, fusion des machines) : machine de base : recettes rangees par famille (propres d'abord), pastille de famille
                if (fr.pioneers.tech.industry.GeneratedFusions.BASE.containsValue(m.key)) {
                    shown = shown.stream().sorted(java.util.Comparator.comparingInt(IndustryCategory::familyRank)).toList();
                }
                registration.addRecipes(recipeType, shown);
            }
        });
        //1.0.4-beta (lot 32) : page de salle blanche : les recettes a sa portee (toutes aujourd'hui), sans tier
        CLEANROOM.forEach((m, recipeType) -> registration.addRecipes(recipeType, IndustryRecipes.of(level, m).stream()
              .filter(r -> Cleanroom.reachable(m, r.tier())).toList()));
        registration.addRecipes(KILN, manager.getAllRecipesFor(PiBronze.KILN.get()));
        //1.0.2-beta (lot 2) : les coulees au moule d'enclume (bloc pose, a la pince) vont a la page « Coulee en moule », pas a la table
        //1.0.3-beta (lot 6, JEI des forges unifie) : TOUTES les coulees sur la meme page (table, moule automatique, moule pose a la pince :
        //l'ex-page « Coulee en moule » repetait les memes recettes) ; le moule d'enclume y revient (pastille « moule pose »)
        registration.addRecipes(CASTING, manager.getAllRecipesFor(PiBronze.CASTING.get()));
        ForgeCategories.recipes(registration, level);
        registration.addRecipes(BLAST, BronzeCategories.blast());
        registration.addRecipes(DEPOSIT, DepositTypes.all());
        registration.addRecipes(FLUID_DEPOSIT, FluidDepositTypes.all());
        registration.addRecipes(REACTORS, ReactorCategory.entries());
        //1.10.2 : les pages d'information ne gardent que l'usage sans recette possible (le reste est dans les categories de productions)
        //1.11.2 (lot E2, demande de Guillaume) : plus de page d'information du selecteur de recette sur toutes les machines (le guide l'explique)
        MachineInfoJei.info(registration);
        JeiEntries.itemInfo(registration);
        Productions.recipes(registration);
        registration.addRecipes(FUELS, GuideEntries.fuels(level));
        registration.addRecipes(CHARCOAL_PIT, List.of(new CharcoalPitCategory.Entry()));
        registration.addRecipes(THERMAL, GuideEntries.radiators());
        registration.addRecipes(AIR, GuideEntries.entries("compressed_air"));
        registration.addRecipes(BoilerJei.TYPE, BoilerJei.entries());
        GuideEntries.info(registration);
        //1.11.3 (lot E3) : un plan de multibloc par page
        multiblocks = MultiblockCategory.entries();
        registration.addRecipes(MultiblockCategory.TYPE, multiblocks);
    }

    /** 1.11.3 : pages « Multiblocs » (calculees a l'enregistrement des recettes, reprises pour les catalyseurs). */
    private static List<MultiblockCategory.Entry> multiblocks;

    /** 0.16.5 : clic sur la fleche de progression des interfaces de machines -> recettes de la machine. */
    @Override
    public void registerGuiHandlers(mezz.jei.api.registration.IGuiHandlerRegistration registration) {
        registration.addGuiContainerHandler(GuiMachine.class, new MachineArrowHandler());
        //1.12.0 (lot E4) : la liste de JEI s'ecarte du panneau cosmetique de l'inventaire
        registration.addGuiContainerHandler(net.minecraft.client.gui.screens.inventory.InventoryScreen.class,
              new mezz.jei.api.gui.handlers.IGuiContainerHandler<>() {
                  @Override
                  public java.util.List<net.minecraft.client.renderer.Rect2i> getGuiExtraAreas(
                        net.minecraft.client.gui.screens.inventory.InventoryScreen screen) {
                      return fr.pioneers.equipment.cosmetic.client.CosmeticPanel.areas(screen);
                  }
              });
        //MAJ Recettes (lot 7) : glisser un objet sur la case « Recette » verrouille la recette qui le fait
        registration.addGhostIngredientHandler(GuiMachine.class, new RecipeLockJei.Ghost());
    }

    /** MAJ Recettes (lot 7) : bouton « + » de JEI sur une machine a selecteur : verrouille la recette montree. */
    @Override
    public void registerRecipeTransferHandlers(@NotNull IRecipeTransferRegistration registration) {
        registration.addUniversalRecipeTransferHandler(new RecipeLockJei.Transfer(registration.getTransferHelper()));
    }

    @Override
    public void registerRecipeCatalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(CHARCOAL_PIT, PiMechanics.COLLIER_POLE.get(), PiMechanics.CLAY_DRAIN.getBlock());
        for (AssemblyStation station : AssemblyStation.values()) {
            registration.addRecipeCatalyst(station(station), ASSEMBLY.get(station));
        }
        registration.addRecipeCatalysts(SOLAR_SIMULATION, PiSpaceSim.CONTROLLER, PiSpaceSim.PROJECTOR,
              PiSpaceSim.COIL);
        registration.addRecipeCatalysts(BLACK_HOLE, PiStabilizer.SINGULARITY_CORE, PiStabilizer.CASING, PiStabilizer.EMITTER);
        registration.addRecipeCatalysts(STELLAR_CHAMBER, PiStabilizer.STELLAR_CORE, PiStabilizer.CASING, PiStabilizer.EMITTER);
        registration.addRecipeCatalysts(DRONE_HARVEST, PiBaseMachines.DRONE_BAY, PiRobotics.UNITS.get(UnitKind.DRONE).get(UnitTier.T1).get(), PiItems.GAS_SCANNER.get());
        registration.addRecipeCatalysts(SOLAR_ABSORBER, PiCosmicBlocks.ABSORBER_COLLECTOR, PiCosmicBlocks.ABSORBER_CASING, PiCosmicBlocks.ABSORBER_PORT);
        KitchenCategory.catalysts(registration);
        registration.addRecipeCatalysts(DryingLineCategory.TYPE, fr.pioneers.building.home.PiHome.ROPE_HOOK.getBlock(), fr.pioneers.tech.kinetics.PiKinetics.FIBER_ROPE.get(),
              fr.pioneers.tech.kinetics.PiKinetics.TARRED_ROPE.get(), fr.pioneers.tech.kinetics.PiKinetics.LEATHER_BELT.get());
        KitchenMachineCatalysts.register(registration);   //1.6 : postes mecaniques et machines electriques de la cuisine
        registration.addRecipeCatalysts(familyPage(MECHANICAL.get(MachineType.SIFTER)) != null ? familyPage(MECHANICAL.get(MachineType.SIFTER))
              : MECHANICAL.get(MachineType.SIFTER), PiMechanics.HAND_SIEVE);
        BENCH.forEach((type, recipeType) -> registration.addRecipeCatalyst(PiBronze.BENCHES.get(type).getBlock(), recipeType));
        registration.addRecipeCatalysts(BLAST, PiBronze.BLAST_FURNACE.getBlock(), PiBronze.TUYERE.getBlock(),
              PiBronze.FIRE_BRICKS.getBlock(), PiKinetics.machine(MachineType.BELLOWS));
        registration.addRecipeCatalysts(DEPOSIT, PiBronze.STEAM_DRILL.getBlock(),
              PiIndustry.controller(IndustryMachine.DRILL).getBlock());
        apiCatalysts(registration);
        ForgeCategories.catalysts(registration);
        MeltingCategories.catalysts(registration);
        PartsCategory.catalysts().forEach(c -> registration.addRecipeCatalyst(new ItemStack(c), PartsCategory.TYPE));
        ChemistryCategories.catalysts(registration, FLUID_DEPOSIT);
        PiIndustry.RADIATORS.values().forEach(r -> registration.addRecipeCatalyst(new ItemStack(r.getBlock()), THERMAL));
        GuideEntries.catalysts(registration, "compressed_air", AIR);
        GuideEntries.catalysts(registration, "fuels", FUELS);
        BoilerJei.catalysts(registration);
        registration.addRecipeCatalysts(UNIVERSE, PiSpaceSim.CONTROLLER, PiSpaceSim.PROJECTOR,
              PiSpaceSim.COIL);
        Productions.catalysts(registration);
        // 1.0.3-beta (lot 6b) : la page « Multiblocs » n'a plus de catalyseurs (la colonne de tous les controleurs encombrait la page) ;
        // chaque controleur est la sortie de son plan : R sur un controleur ouvre son seul multibloc
    }

    @Override
    public void onRuntimeAvailable(@NotNull mezz.jei.api.runtime.IJeiRuntime runtime) {
        //Refonte du guide (1.0.1-beta) : un clic sur une icone du guide ouvre ses recettes, un clic droit ses usages
        GuideLinks.setJei((stack, uses) -> runtime.getRecipesGui().show(runtime.getJeiHelpers().getFocusFactory()
              .createFocus(uses ? RecipeIngredientRole.INPUT : RecipeIngredientRole.OUTPUT, mezz.jei.api.constants.VanillaTypes.ITEM_STACK, stack)));
        List<ItemStack> hidden = new ArrayList<>();
        BuiltInRegistries.ITEM.getTagOrEmpty(ItemTags.create(Pioneers.rl("hidden_vanilla_tools")))
              .forEach(holder -> hidden.add(new ItemStack(holder.value())));
        //1.0 : materiaux et outils vanilla remplaces par ceux du mod
        VanillaReplacements.replaced().forEach(item -> hidden.add(new ItemStack(item)));
        if (!hidden.isEmpty()) {
            runtime.getIngredientManager().removeIngredientsAtRuntime(mezz.jei.api.constants.VanillaTypes.ITEM_STACK, hidden);
        }
    }

    @Override
    public void onRuntimeUnavailable() {
        GuideLinks.setJei(null);
    }
}
