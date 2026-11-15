package fr.pioneers.core.recipe;

import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.bronze.foundry.CrucibleUnit;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.materials.GeneratedMaterials;
import fr.pioneers.core.PiTags;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.kinetics.kitchen.KitchenMachineType;
import fr.pioneers.kinetics.kitchen.KitchenProcess;
import fr.pioneers.kinetics.machines.MechanicalRecipe;
import fr.pioneers.machine.MachineKind;
import fr.pioneers.machine.ProcessingRecipe;
import fr.pioneers.recipe.PiRecipes;
import fr.pioneers.transport.CableTier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeMap;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;

/**
 * MAJ Recettes (lot 7) : recettes d'une machine a selecteur, d'apres sa source (texte court, le meme des deux cotes, envoye aussi au
 * terminal logistique pour les machines qu'il regle a distance) :
 * <ul>
 *     <li>{@code industrial:<cle>:<0|1>} : recettes industrielles de la machine (1 : multibloc, les recettes « multibloc seulement »
 *     comprises ; 0 : atelier, forge, four a coke, sans elles) ;</li>
 *     <li>{@code mechanical:<sorte>} : recettes d'une machine mecanique ({@code milling}, {@code hammering}...) ;</li>
 *     <li>{@code kit:<machine>} : broyeur, compresseur ou four electrique du kit ;</li>
 *     <li>API Machine (lot 8b) : {@code kitchen:<poste>} : recettes des stations d'un poste mecanique de la cuisine (meme liste et meme ordre
 *     que {@code KitchenProcess.recipes}) ; ({@code bench:<atelier>} des ateliers a vapeur : retire au lot 38 de la 1.0.5 avec le
 *     marteau-pilon et la presse d'etabli) ;</li>
 *     <li>1.0.5-beta (lot 38) : {@code family:<famille>} : recettes d'une machine T1 (vapeur) de la famille de tiers, tier minimal <= 1
 *     ({@code api.machine.tier.TierRecipes}) ;</li>
 *     <li>1.0.2-beta (lot 2) : {@code alloy:crucible} : metaux purs et alliages du fourneau a creuset (identifiants {@code pioneers:alloy/<metal>},
 *     lingots aux proportions de {@link Metals#RECIPES}, choix d'un metal pur = filtre de ce metal).</li>
 * </ul>
 * Listes gardees jusqu'au prochain rechargement des recettes (une par cote : en solo les deux gestionnaires de recettes alternent).
 */
public final class RecipeSources {

    private static final class Side {

        RecipeManager manager;
        int generation = -1;
        final Map<String, List<RecipeChoice>> lists = new HashMap<>();
    }

    private static final Side[] SIDES = {new Side(), new Side()};

    private RecipeSources() {
    }

    public static String industrial(IndustryMachine machine, boolean multiblock) {
        return "industrial:" + machine.key + ":" + (multiblock ? 1 : 0);
    }

    public static String mechanical(MechanicalRecipe.Kind kind) {
        return "mechanical:" + kind.id();
    }

    public static String kit(MachineKind kind) {
        return "kit:" + kind.name;
    }

    /** Lot 8b : poste mecanique de la cuisine. */
    public static String kitchen(KitchenMachineType type) {
        return "kitchen:" + type.key();
    }

    /** 1.0.5-beta (lot 41, retour 60) : recettes d'un poste MANUEL de la cuisine ({@code station:<poste>}, ordre des identifiants). */
    public static String station(Station station) {
        return "station:" + station.id();
    }

    /** Recettes de la source (vide si elle est inconnue). */
    public static synchronized List<RecipeChoice> choices(Level level, String source) {
        //1.0.5-beta (lot 38) : machine T1 d'une famille de tiers : recettes de la famille de tier minimal <= 1 (liste gardee par TierRecipes)
        if (source.startsWith(fr.pioneers.api.machine.tier.TierRecipes.SOURCE)) {
            return fr.pioneers.api.machine.tier.TierRecipes.choices(level, source.substring(fr.pioneers.api.machine.tier.TierRecipes.SOURCE.length()));
        }
        if (source.startsWith("industrial:")) {
            //Liste deja gardee par IndustryRecipes (meme generation que ses recettes)
            String[] p = source.split(":");
            IndustryMachine machine = p.length > 1 ? IndustryMachine.byKey(p[1]) : null;
            return machine == null ? List.of() : IndustryRecipes.choices(level, machine, p.length > 2 && "1".equals(p[2]));
        }
        Side side = SIDES[level.isClientSide ? 1 : 0];
        RecipeManager manager = level.getRecipeManager();
        int generation = PiRecipes.generation();
        if (manager != side.manager || side.generation != generation) {
            side.manager = manager;
            side.generation = generation;
            side.lists.clear();
        }
        return side.lists.computeIfAbsent(source, s -> build(level, s));
    }

    private static List<RecipeChoice> build(Level level, String source) {
        List<RecipeChoice> out = new ArrayList<>();
        if (source.startsWith("mechanical:")) {
            String id = source.substring("mechanical:".length());
            for (MechanicalRecipe.Kind kind : MechanicalRecipe.Kind.values()) {
                if (kind.id().equals(id)) {
                    for (MechanicalRecipe r : PiRecipes.sorted(level, PiRecipes.MECHANICAL.get(kind).get())) {
                        List<ItemStack> outs = new ArrayList<>();
                        for (MechanicalRecipe.Result res : r.results()) {
                            outs.add(res.stack());
                        }
                        out.add(RecipeChoice.simple(r.getId(), r.input(), r.count(), outs).withTime(Math.max(1, Math.round(r.turns())), true));
                    }
                }
            }
        } else if (source.startsWith("kitchen:")) {
            String key = source.substring("kitchen:".length());
            for (KitchenMachineType type : KitchenMachineType.values()) {
                if (type.key().equals(key)) {
                    for (KitchenRecipe r : KitchenProcess.recipes(level, type)) {
                        out.add(kitchenChoice(level, type, r));
                    }
                }
            }
        } else if (source.startsWith("station:")) {
            //1.0.5-beta (lot 41) : poste manuel de la cuisine (selecteur de son interface) : ses recettes, par identifiant
            String key = source.substring("station:".length());
            for (Station st : Station.values()) {
                if (st.id().equals(key)) {
                    List<KitchenRecipe> list = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(fr.pioneers.farm.PiKitchen.TYPES.get(st).get()));
                    list.sort(java.util.Comparator.comparing(r -> r.getId().toString()));
                    for (KitchenRecipe r : list) {
                        out.add(stationChoice(r));
                    }
                }
            }
        } else if (source.equals(CrucibleUnit.SOURCE)) {
            for (GeneratedMaterials.Metal metal : Metals.all()) {
                if (!metal.crucible()) {
                    continue;
                }
                Map<String, Integer> parts = metal.composition().isEmpty() ? Map.of(metal.id(), 1) : Metals.RECIPES.get(metal.id());
                ItemStack result = ForgeMetals.ingot(metal.id(), 1);
                if (parts == null || result.isEmpty()) {
                    continue;
                }
                List<BenchRecipe.Counted> items = new ArrayList<>();
                int n = 0;
                for (Map.Entry<String, Integer> e : new TreeMap<>(parts).entrySet()) {
                    items.add(new BenchRecipe.Counted(Ingredient.of(ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "ingots/" + e.getKey()))),
                          e.getValue()));
                    n += e.getValue();
                }
                out.add(new RecipeChoice(CrucibleUnit.choiceId(metal.id()), 0, false, List.copyOf(items), List.of(), List.of(), List.of(),
                      List.of(result.copyWithCount(n)), List.of(), List.of(), 0, false));
            }
        } else if (source.startsWith("kit:")) {
            String name = source.substring("kit:".length());
            if (MachineKind.FURNACE.name.equals(name)) {
                for (SmeltingRecipe r : PiRecipes.sorted(level, RecipeType.SMELTING)) {
                    ItemStack result = r.getResultItem(level.registryAccess());
                    if (!r.getIngredients().isEmpty() && PiTags.Items.electricFurnaceAllows(result)) {
                        out.add(RecipeChoice.simple(r.getId(), r.getIngredients().get(0), 1, List.of(result.copy()))
                              .withTime(MachineKind.FURNACE.ticks(CableTier.BASIC), false));
                    }
                }
            } else {
                RecipeType<ProcessingRecipe> type = MachineKind.COMPRESSOR.name.equals(name) ? PiRecipes.COMPRESSING.get() : PiRecipes.CRUSHING.get();
                for (ProcessingRecipe r : PiRecipes.sorted(level, type)) {
                    //MAJ Recettes (lot 8a) : liant du compacteur en 2e entree
                    MachineKind kind = MachineKind.COMPRESSOR.name.equals(name) ? MachineKind.COMPRESSOR : MachineKind.CRUSHER;
                    RecipeChoice c = r.hasBinder() ? RecipeChoice.withBinder(r.getId(), r.getInput(), r.getCount(), r.getBinder(), r.getBinderCount(),
                          List.of(r.getResult().copy())) : RecipeChoice.simple(r.getId(), r.getInput(), r.getCount(), List.of(r.getResult().copy()));
                    out.add(c.withTime(kind.ticks(CableTier.BASIC), false));
                }
                //1.0.3-beta (lot 5) : le broyeur fait aussi les recettes de l'ancienne dechiqueteuse (pioneers:industrial, machine crusher)
                if (MachineKind.CRUSHER.name.equals(name)) {
                    for (fr.pioneers.industry.recipe.IndustrialRecipe r : fr.pioneers.api.machine.unit.KitProcess.industrial(level)) {
                        out.add(RecipeChoice.of(r).withTime(MachineKind.CRUSHER.ticks(CableTier.BASIC), false));
                    }
                }
            }
            //1.0.4-beta (lot 11, fusion des machines) : recettes RECUES des machines absorbees (voie B de KitProcess, duree de leur recette)
            fr.pioneers.industry.IndustryMachine machine = fr.pioneers.industry.IndustryMachine.byKey(name);
            if (machine != null) {
                for (fr.pioneers.industry.recipe.IndustrialRecipe r : fr.pioneers.industry.recipe.IndustryRecipes.kitReceived(level, machine)) {
                    out.add(RecipeChoice.of(r));
                }
            }
        }
        return List.copyOf(out);
    }

    /** 1.0.5-beta (lot 41) : recette d'un poste manuel vue par le selecteur (gestes si le poste en demande, sinon duree en ticks). */
    private static RecipeChoice stationChoice(KitchenRecipe r) {
        List<BenchRecipe.Counted> items = new ArrayList<>();
        for (KitchenRecipe.Counted c : r.ingredients()) {
            items.add(new BenchRecipe.Counted(c.ingredient(), Math.max(1, c.count())));
        }
        List<IndustrialRecipe.FluidIn> fluids = r.fluid() == null ? List.of()
              : List.of(new IndustrialRecipe.FluidIn(r.fluid().fluid(), r.fluid().tag(), r.fluid().amount()));
        List<ItemStack> outs = new ArrayList<>();
        for (KitchenRecipe.Result res : r.results()) {
            outs.add(res.stack().copy());
        }
        List<FluidStack> fluidOuts = r.fluidResult().isEmpty() ? List.of() : List.of(r.fluidResult().copy());
        boolean strokes = r.strokes() > 0;
        return new RecipeChoice(r.getId(), 0, false, List.copyOf(items), fluids, List.of(), List.of(), List.copyOf(outs), fluidOuts, List.of(),
              strokes ? r.strokes() : Math.max(0, r.time()), strokes);
    }

    /** Lot 8b : recette de cuisine vue par le selecteur (ingredients, liquide d'entree, produits, liquide produit, tours de l'arbre). */
    private static RecipeChoice kitchenChoice(Level level, KitchenMachineType type, KitchenRecipe r) {
        List<BenchRecipe.Counted> items = new ArrayList<>();
        for (KitchenRecipe.Counted c : r.ingredients()) {
            items.add(new BenchRecipe.Counted(c.ingredient(), Math.max(1, c.count())));
        }
        List<IndustrialRecipe.FluidIn> fluids = r.fluid() == null ? List.of()
              : List.of(new IndustrialRecipe.FluidIn(r.fluid().fluid(), r.fluid().tag(), r.fluid().amount()));
        List<ItemStack> outs = new ArrayList<>();
        for (KitchenRecipe.Result res : r.results()) {
            outs.add(res.stack().copy());
        }
        List<FluidStack> fluidOuts = r.fluidResult().isEmpty() ? List.of() : List.of(r.fluidResult().copy());
        int turns = Math.max(1, Math.round(Math.max(1, r.strokes()) * type.turnsPerStroke));
        return new RecipeChoice(r.getId(), 0, false, List.copyOf(items), fluids, List.of(), List.of(), List.copyOf(outs), fluidOuts, List.of(), turns, true);
    }
}
