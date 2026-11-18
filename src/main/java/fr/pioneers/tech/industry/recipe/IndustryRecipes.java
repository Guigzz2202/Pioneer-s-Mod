package fr.pioneers.tech.industry.recipe;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.api.machine.tier.TierRecipes;
import fr.pioneers.api.machine.tier.TierRecipes.TierRecipe;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.core.PiTags;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe.Result;
import fr.pioneers.tech.machine.ProcessingRecipe;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.space.environment.Environments;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Recettes de chaque machine de l'age industriel : celles du type {@code pioneers:industrial} de la machine, plus, pour les homologues du kit,
 * les recettes deja existantes converties (broyage, compression, cuisson du four). Mises en cache jusqu'au prochain rechargement des
 * recettes. Les combustibles des generateurs thermiques sont des recettes fabriquees a la demande ({@link #fuel}).
 */
public final class IndustryRecipes {

    /** Duree d'une cuisson du four electrique (ticks, tier basique). */
    /**
     * 1.0.5-beta (lot 14) : cuisson au four industriel (multibloc), comme le four du kit ({@code MachineKind.Rules.FURNACE_TICKS} ; avant 100).
     * Lot 21 : 80 (multibloc : x 0,8 de la regle industrielle, 64 / 32 / 16 / 8 ticks par operation, en parallele).
     */
    public static final int SMELT_TIME = fr.pioneers.tech.machine.MachineKind.Rules.FURNACE_TICKS;
    /** 1.0.6 : duree d'une taille de pierre a la Taillerie MKI (ticks). */
    public static final int CUT_TIME = 40;
    /** Broyage et compression du kit : durees (ticks, tier basique). */
    public static final int CRUSH_TIME = 160, COMPRESS_TIME = 200;

    /** Cache d'un cote (client ou serveur : en solo les deux gestionnaires de recettes alternent). */
    private static final class Side {

        final Map<IndustryMachine, List<IndustrialRecipe>> recipes = new EnumMap<>(IndustryMachine.class);
        /** Objets acceptes par machine (piles sans NBT seulement). */
        final Map<IndustryMachine, Map<Item, Boolean>> accepts = new EnumMap<>(IndustryMachine.class);
        /** 0.20.28 : ingredients-objets distincts des recettes ou entre cet objet (le plus grand nombre). */
        final Map<IndustryMachine, Map<Item, Integer>> kinds = new EnumMap<>(IndustryMachine.class);
        /** MAJ Recettes (lot 7) : recettes du selecteur (atelier, multibloc) et recettes par identifiant (verrou). */
        final Map<IndustryMachine, List<RecipeChoice>> choices = new EnumMap<>(IndustryMachine.class), multiChoices = new EnumMap<>(IndustryMachine.class);
        final Map<IndustryMachine, Map<ResourceLocation, IndustrialRecipe>> byId = new EnumMap<>(IndustryMachine.class);
        /** 1.0.4-beta (lot 11) : recettes recues par une machine du kit (machines absorbees : voie industrielle de KitProcess). */
        final Map<IndustryMachine, List<IndustrialRecipe>> kitReceived = new EnumMap<>(IndustryMachine.class);
        /** 1.0.4-beta (lot 13) : recettes d'une machine hors d'une atmosphere (sans la famille de la separation de l'air). */
        final Map<IndustryMachine, List<IndustrialRecipe>> airless = new EnumMap<>(IndustryMachine.class);
        /** 1.0.5-beta (lot 38) : recettes propres de la machine (et du kit) DOMINEES par une recette basse convertie de sa famille. */
        final Map<IndustryMachine, Set<ResourceLocation>> dominated = new EnumMap<>(IndustryMachine.class);
        /** 1.0.5-beta (lot 38) : recette propre dominee -> recette basse convertie qui la remplace ({@link #successor}). */
        final Map<IndustryMachine, Map<ResourceLocation, ResourceLocation>> successors = new EnumMap<>(IndustryMachine.class);
        RecipeManager manager;
        int generation = -1;
    }

    private static final Side[] SIDES = {new Side(), new Side()};
    private static final Map<Item, IndustrialRecipe> FUELS = new HashMap<>();
    private static int fuelGeneration = -1;

    private IndustryRecipes() {
    }

    private static Side side(Level level) {
        Side side = SIDES[level.isClientSide ? 1 : 0];
        RecipeManager manager = level.getRecipeManager();
        int generation = PiRecipes.generation();
        if (manager != side.manager || side.generation != generation) {
            side.manager = manager;
            side.generation = generation;
            side.recipes.clear();
            side.accepts.clear();
            side.kinds.clear();
            side.choices.clear();
            side.multiChoices.clear();
            side.byId.clear();
            side.kitReceived.clear();
            side.airless.clear();
            side.dominated.clear();
            side.successors.clear();
        }
        return side;
    }

    /**
     * 1.0.4-beta (lot 13, fusion des machines) : famille des recettes de l'ancienne unite de separation de l'air, fondue dans le Separateur de
     * gaz (multibloc) : elles ne travaillent que dans une atmosphere (azote de l'astre), comme l'unite d'avant.
     */
    public static final String AIR_FAMILY = "air_separation_unit";

    public static synchronized List<IndustrialRecipe> of(Level level, IndustryMachine machine) {
        Side side = side(level);
        List<IndustrialRecipe> all = side.recipes.computeIfAbsent(machine, m -> build(side, level, m));
        //1.0 : l'unite de separation de l'air (qui remplace le collecteur de gaz) ne travaille que dans une atmosphere (azote de l'astre) ;
        //1.0.4-beta (lot 13) : elle est fondue dans le Separateur de gaz : ses recettes (famille AIR_FAMILY) seules sont retirees hors d'une
        //atmosphere, les autres recettes du separateur restent
        if (machine == IndustryMachine.GAS_SEPARATOR && !level.isClientSide && Environments.get(level).nitrogen() <= 0) {
            return side.airless.computeIfAbsent(machine, m -> {
                List<IndustrialRecipe> out = new ArrayList<>(all.size());
                for (IndustrialRecipe r : all) {
                    if (!AIR_FAMILY.equals(r.family())) {
                        out.add(r);
                    }
                }
                return out.size() == all.size() ? all : List.copyOf(out);
            });
        }
        return all;
    }

    private static List<IndustrialRecipe> build(Side side, Level level, IndustryMachine machine) {
        List<IndustrialRecipe> out = new ArrayList<>();
        for (IndustrialRecipe recipe : PiRecipes.sorted(level, PiIndustry.INDUSTRIAL.get())) {
            if (recipe.machine() == machine) {
                out.add(recipe);
            }
        }
        switch (machine.source) {
            case CRUSHING -> convert(level, machine, PiRecipes.CRUSHING.get(), CRUSH_TIME, out);
            case COMPRESSING -> convert(level, machine, PiRecipes.COMPRESSING.get(), COMPRESS_TIME, out);
            case SMELTING -> {
                for (SmeltingRecipe r : PiRecipes.sorted(level, RecipeType.SMELTING)) {
                    if (r.getIngredients().isEmpty()) {
                        continue;
                    }
                    ItemStack result = r.getResultItem(level.registryAccess()).copy();
                    //MAJ Recettes (lot 1) : plus de four joker, seulement la nourriture et l'age du bronze
                    if (PiTags.Items.electricFurnaceAllows(result)) {
                        out.add(simple(r.getId(), machine, r.getIngredients().get(0), 1, result, SMELT_TIME));
                    }
                }
            }
            //1.0.6 (Taillerie de pierre, decision de Guillaume du 16/11/2026 : « toute la taille ») : chaque recette du tailleur de pierre
            //(vanilla et mod), avec le rendement de tier compte depuis la main (T0) ; le cuivre se travaille a la Chaudronnerie
            case STONECUTTING -> {
                for (StonecutterRecipe r : PiRecipes.sorted(level, RecipeType.STONECUTTING)) {
                    if (r.getIngredients().isEmpty()) {
                        continue;
                    }
                    ItemStack result = r.getResultItem(level.registryAccess()).copy();
                    ResourceLocation rid = ForgeRegistries.ITEMS.getKey(result.getItem());
                    if (result.isEmpty() || rid == null || rid.getPath().contains("copper")) {
                        continue;
                    }
                    out.add(cut(r.getId(), machine, r.getIngredients().get(0), result));
                }
            }
            default -> {
            }
        }
        //1.0.5-beta (lot 38, specification de l'age du bronze §2) : recettes basses de la famille de tiers de la machine, converties ; une
        //recette propre (ou du kit) qu'une recette basse rend inutile (meme entree, meme produit, moins au MKI) est retiree, et inversement
        Map<ResourceLocation, ResourceLocation> replaced = low(level, machine, out);
        side.dominated.put(machine, Set.copyOf(replaced.keySet()));
        side.successors.put(machine, replaced);
        //1.7 (refonte des composants) : la recette la plus complete d'abord (plus d'entrees distinctes), puis l'ordre des identifiants ;
        //sans cela, une recette dont les entrees sont contenues dans celles d'une autre (« 4 plaques d'acier ») passait avant elle et la
        //rendait impossible des que ses ingredients etaient presents (tri stable : l'ordre des identifiants reste entre recettes egales)
        out.sort(ORDER);
        return List.copyOf(out);
    }

    /**
     * MAJ Recettes (lot 7) : recettes du selecteur de la machine, dans son ordre ({@link #of}) ; {@code multiblock} faux : sans les recettes
     * « multibloc seulement » (atelier, forge au charbon, four a coke). Rien pour les machines sans recette (generateurs a combustible
     * solide seulement, puits, pompes, foreuses, cuves, reacteurs, calcul).
     */
    public static synchronized List<RecipeChoice> choices(Level level, IndustryMachine machine, boolean multiblock) {
        Side side = side(level);
        return (multiblock ? side.multiChoices : side.choices).computeIfAbsent(machine, m -> {
            List<RecipeChoice> out = new ArrayList<>();
            for (IndustrialRecipe r : all(side, level, m)) {
                if (multiblock || !r.industrialOnly()) {
                    out.add(RecipeChoice.of(r));
                }
            }
            return List.copyOf(out);
        });
    }

    /** Toutes les recettes de la machine, sans la condition d'atmosphere de {@link #of} (le selecteur les montre partout). */
    private static List<IndustrialRecipe> all(Side side, Level level, IndustryMachine machine) {
        return side.recipes.computeIfAbsent(machine, m -> build(side, level, m));
    }

    /** MAJ Recettes (lot 7) : recette de la machine par son identifiant (recette verrouillee), ou null. */
    @Nullable
    public static synchronized IndustrialRecipe byId(Level level, IndustryMachine machine, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Side side = side(level);
        return side.byId.computeIfAbsent(machine, m -> {
            Map<ResourceLocation, IndustrialRecipe> map = new HashMap<>();
            for (IndustrialRecipe r : all(side, level, m)) {
                map.putIfAbsent(r.getId(), r);
            }
            return map;
        }).get(id);
    }

    /**
     * 1.0.4-beta (lot 11, fusion des machines) : recettes RECUES par une machine du kit (broyeur, four) : ses recettes {@code pioneers:industrial}
     * hors de son dossier propre ({@code industry/<cle>/}, voie du kit d'avant : ex-dechiqueteuse, cuissons propres du four) et hors
     * « multibloc seulement » ; dans l'ordre de {@link #of} (la plus complete d'abord). Elles tournent comme a l'atelier de la machine absorbee
     * (duree {@code Tiers.ticks}, tier exige, rendement du minerai) : {@code KitProcess}.
     */
    public static synchronized List<IndustrialRecipe> kitReceived(Level level, IndustryMachine machine) {
        return side(level).kitReceived.computeIfAbsent(machine, m -> {
            List<IndustrialRecipe> out = new ArrayList<>();
            String own = "industry/" + m.key + "/";
            Set<ResourceLocation> gone = dominated(level, m);
            for (IndustrialRecipe r : PiRecipes.sorted(level, PiIndustry.INDUSTRIAL.get())) {
                if (r.machine() == m && !r.industrialOnly() && !r.getId().getPath().startsWith(own) && !gone.contains(r.getId())) {
                    out.add(r);
                }
            }
            //1.0.5-beta (lot 38) : et les recettes basses converties de sa famille (broyage, compactage, cuisson)
            for (IndustrialRecipe r : of(level, m)) {
                if (r.converted()) {
                    out.add(r);
                }
            }
            out.sort(ORDER);
            return List.copyOf(out);
        });
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 38) : recettes basses des familles de tiers
    // ------------------------------------------------------------------

    /** Produits que les machines MKI a MKIV ne tirent pas des recettes basses (= T:core/kit_recipes.KIT_DUST_DENIED). */
    public static final Set<String> LOW_DENIED = Set.of("pioneers:gold_dust");

    /** Famille de tiers dont la machine est la machine MKI a MKIV (moteur des familles), ou null. */
    @Nullable
    public static String tierFamily(IndustryMachine machine) {
        for (GeneratedTierFamilies.Family f : GeneratedTierFamilies.FAMILIES) {
            if (f.engine() && machine.key.equals(f.industry())) {
                return f.key();
            }
        }
        return null;
    }

    /**
     * Identifiants des recettes propres de la machine (dont les recettes du kit {@code crushing} / {@code compressing}) retirees parce qu'une
     * recette basse convertie de sa famille fait mieux (voie A du kit : {@code KitProcess} les saute).
     */
    public static synchronized Set<ResourceLocation> dominated(Level level, IndustryMachine machine) {
        Side side = side(level);
        all(side, level, machine);
        return side.dominated.getOrDefault(machine, Set.of());
    }

    /** Recettes de la machine sans les recettes basses converties (pages JEI industrielles, guide). */
    public static List<IndustrialRecipe> withoutLow(List<IndustrialRecipe> list) {
        for (IndustrialRecipe r : list) {
            if (r.converted()) {
                return list.stream().filter(x -> !x.converted()).toList();
            }
        }
        return list;
    }

    /**
     * Ajoute a {@code out} les recettes basses (m = 0 ou 1) de la famille de tiers de la machine, converties au MKI ({@link #convertLow}) ;
     * doublons (specification §2.2 point 4, decision D5 : compares au MKI, N = 2) : une recette propre a une seule entree qui donne le meme
     * produit principal que la recette basse (meme quantite d'entree, objets communs) est retiree si la recette basse rend STRICTEMENT plus ;
     * sinon la recette basse n'est pas ajoutee. Une recette basse de meme identifiant qu'une recette propre (cuissons du four) n'est pas
     * ajoutee. Rend les recettes propres retirees, chacune avec la recette basse qui la remplace.
     */
    private static Map<ResourceLocation, ResourceLocation> low(Level level, IndustryMachine machine, List<IndustrialRecipe> out) {
        List<TierRecipe> lows = lows(level, machine);
        if (lows.isEmpty()) {
            return Map.of();
        }
        Set<ResourceLocation> ids = new HashSet<>();
        for (IndustrialRecipe r : out) {
            ids.add(r.getId());
        }
        Map<ResourceLocation, ResourceLocation> gone = new HashMap<>();
        for (TierRecipe t : lows) {
            if (ids.contains(t.id()) || t.minTier() > 1 || t.inputs().isEmpty()) {
                continue;
            }
            //MAJ Recettes (lot 2, decision L2-9) : le broyeur MKI ne fait pas le concasse d'or en poudre (or natif : voir kit_recipes.KIT_DUST_DENIED)
            if (LOW_DENIED.contains(String.valueOf(ForgeRegistries.ITEMS.getKey(t.main().getItem())))) {
                continue;
            }
            IndustrialRecipe c = convertLow(t, machine);
            boolean add = true;
            for (IndustrialRecipe own : out) {
                if (own.converted() || gone.containsKey(own.getId()) || !same(own, c)) {
                    continue;
                }
                if (expected(c) > expected(own) + 1e-9) {
                    gone.put(own.getId(), c.getId());
                } else {
                    add = false;
                }
            }
            if (add) {
                out.add(c);
                ids.add(c.getId());
            }
        }
        if (!gone.isEmpty()) {
            out.removeIf(r -> gone.containsKey(r.getId()));
        }
        return Map.copyOf(gone);
    }

    /**
     * Recettes basses que la machine fait au MKI : celles de sa famille de tiers (lot 47 : sans les recettes MKI ouvertes a la vapeur), sauf
     * celles qu'une autre machine fait au MKI ; 1.0.6 (« un role par machine ») : plus les recettes basses d'autres familles dont le geste
     * est le role de cette machine ({@link GeneratedTierFamilies#LOW_MOVED} : anneaux et ressorts du tour -> Cintreuse...).
     */
    private static List<TierRecipe> lows(Level level, IndustryMachine machine) {
        String family = tierFamily(machine);
        List<TierRecipe> out = new ArrayList<>();
        if (family != null) {
            for (TierRecipe t : TierRecipes.natives(level, family)) {
                String to = GeneratedTierFamilies.LOW_MOVED.get(t.id().toString());
                if (to == null || to.equals(machine.key)) {
                    out.add(t);
                }
            }
        }
        if (GeneratedTierFamilies.LOW_MOVED.containsValue(machine.key)) {
            for (GeneratedTierFamilies.Family f : GeneratedTierFamilies.FAMILIES) {
                if (!f.engine() || f.key().equals(family)) {
                    continue;
                }
                for (TierRecipe t : TierRecipes.natives(level, f.key())) {
                    if (machine.key.equals(GeneratedTierFamilies.LOW_MOVED.get(t.id().toString()))) {
                        out.add(t);
                    }
                }
            }
        }
        return out;
    }

    /**
     * 1.0.6 : recette du tailleur de pierre convertie a la Taillerie de pierre : 1 entree, 1 produit, rendement de tier compte depuis le T0
     * (la main : « meilleur rendement qu'a la main ») : x1,75 au MKI ... x3,5 au MKIV (la fraction est tiree au sort, RecipeMatch).
     */
    private static IndustrialRecipe cut(ResourceLocation id, IndustryMachine machine, Ingredient input, ItemStack result) {
        IndustrialRecipe r = new IndustrialRecipe(id, machine, 0, CUT_TIME, List.of(new Counted(input, 1)), List.of(), List.of(),
              List.of(new Result(result, 1F)), List.of(), List.of(), 0, false, false, false, 0, 0, false);
        double[] y = new double[4];
        for (int i = 0; i < 4; i++) {
            y[i] = fr.pioneers.api.machine.PowerRules.yieldFactor(i + 2, 0);
        }
        return r.withTierYield(y);
    }

    /**
     * 1.0.5-beta (lot 38, aucune perte sur un monde existant) : recette de la machine qui remplace un identifiant disparu (lot en cours d'un
     * multibloc sauve avant la mise a jour, dont les entrees sont deja prises) : recette propre dominee -> recette basse convertie ; recette
     * retiree -> {@code PiRemaps.recipeId} (copies « steam_* », doublons a vapeur), ou la recette de la machine qui fait la meme chose
     * (meme entree, meme produit) ; sinon null.
     */
    @Nullable
    public static synchronized IndustrialRecipe successor(Level level, IndustryMachine machine, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        Side side = side(level);
        List<IndustrialRecipe> list = all(side, level, machine);
        ResourceLocation next = side.successors.getOrDefault(machine, Map.of()).get(id);
        if (next == null) {
            ResourceLocation moved = fr.pioneers.core.common.PiRemaps.recipeId(id);
            next = moved.equals(id) ? null : moved;
        }
        if (next == null) {
            return null;
        }
        IndustrialRecipe direct = byId(level, machine, next);
        if (direct != null) {
            return direct;
        }
        //Remplacante absente de cette machine (une recette propre fait mieux) : la recette de la machine qui fait la meme chose
        String family = tierFamily(machine);
        if (family != null) {
            for (TierRecipe t : TierRecipes.natives(level, family)) {   // lot 47 : sans les recettes MKI ouvertes a la vapeur
                if (t.id().equals(next) && !t.inputs().isEmpty()) {
                    IndustrialRecipe c = convertLow(t, machine);
                    for (IndustrialRecipe r : list) {
                        if (same(r, c)) {
                            return r;
                        }
                    }
                }
            }
        }
        return null;
    }

    /**
     * Recette basse convertie pour une machine MKI a MKIV : tier 0 (toute machine de la famille la fait), duree de base / 2^(2 - m) (la regle
     * x 2 par tier d'ecart de {@code Tiers.ticks} fait la suite), rendement R(N) / R(m) aux tiers N = 2..5 sur toutes les sorties (plafond a T1
     * des concasses : regle 1), eau du melangeur en fluide d'entree.
     */
    public static IndustrialRecipe convertLow(TierRecipe t, IndustryMachine machine) {
        int time = Math.max(1, t.time() >> Math.max(0, 2 - t.minTier()));
        List<IndustrialRecipe.FluidIn> fluids = t.water() > 0 ? List.of(new IndustrialRecipe.FluidIn(Fluids.WATER, null, t.water())) : List.of();
        List<Result> results = new ArrayList<>(t.outputs().size());
        for (TierRecipes.Out o : t.outputs()) {
            results.add(new Result(o.stack().copy(), o.chance()));
        }
        IndustrialRecipe r = new IndustrialRecipe(t.id(), machine, 0, time, t.inputs(), fluids, List.of(), results, List.of(), List.of(), 0, false,
              false, false, 0, 0, false);
        double[] y = null;
        if (t.yield()) {
            y = new double[4];
            for (int i = 0; i < 4; i++) {
                y[i] = t.yieldFactor(i + 2);
            }
        }
        return r.withTierYield(y).withLow(t.minTier(), true);
    }

    /** Meme entree unique (quantite, objets communs), sans fluide ni gaz, meme produit principal objet. */
    private static boolean same(IndustrialRecipe a, IndustrialRecipe b) {
        if (a.items().size() != 1 || b.items().size() != 1 || !a.gases().isEmpty() || !b.gases().isEmpty() || a.fluids().size() != b.fluids().size()
            || a.results().isEmpty() || b.results().isEmpty() || !a.data().isEmpty()) {
            return false;
        }
        ItemStack ma = a.results().get(0).stack(), mb = b.results().get(0).stack();
        if (ma.isEmpty() || !ma.is(mb.getItem())) {
            return false;
        }
        Counted ca = a.items().get(0), cb = b.items().get(0);
        return ca.count() == cb.count() && TierRecipes.overlap(ca.ingredient(), cb.ingredient());
    }

    /** Produit principal attendu par operation au MKI (tier 0), rendement compris. */
    private static double expected(IndustrialRecipe r) {
        ItemStack main = r.results().get(0).stack();
        double n = 0;
        for (Result x : r.results()) {
            if (x.stack().is(main.getItem())) {
                n += x.stack().getCount() * (double) Math.min(1F, x.chance());
            }
        }
        return n * r.tierYield(0) * (r.ore() ? Tiers.oreBonus(0) : 1);
    }

    /** 1.0.4-beta (lot 11) : la recette est-elle une recette recue par le kit de sa machine (voir {@link #kitReceived}) ? */
    public static boolean isKitReceived(IndustrialRecipe r) {
        return r.machine().atelier == IndustryMachine.Atelier.KIT && !r.industrialOnly() && !r.getId().getPath().startsWith("industry/" + r.machine().key + "/");
    }

    /**
     * Ordre d'essai des recettes d'une machine : la plus complete d'abord ({@link #specificity}) ; a egalite, 1.0.4-beta (lot 11, fusion des
     * machines) : les recettes PROPRES de la machine (sans famille) avant les recettes recues d'une machine absorbee, pour qu'une base garde le
     * comportement d'avant la fusion quand deux recettes acceptent les memes entrees (l'autre se verrouille dans l'onglet Recettes ; liste
     * T:industry/fusion.py SELECTOR). Tri stable : l'ordre des identifiants reste entre recettes egales.
     */
    public static final Comparator<IndustrialRecipe> ORDER = Comparator.comparingInt(IndustryRecipes::specificity).reversed()
          .thenComparingInt(r -> r.family() == null ? 0 : 1);

    /** Nombre d'entrees distinctes d'une recette (objets, fluides, gaz, donnees) : la plus complete est essayee d'abord. */
    public static int specificity(IndustrialRecipe r) {
        return r.items().size() + r.fluids().size() + r.gases().size() + r.data().size();
    }

    private static void convert(Level level, IndustryMachine machine, RecipeType<ProcessingRecipe> type, int time, List<IndustrialRecipe> out) {
        for (ProcessingRecipe r : PiRecipes.sorted(level, type)) {
            out.add(simple(r.getId(), machine, r.getInput(), r.getCount(), r.getResult().copy(), time, r.getBinder(), r.getBinderCount()));
        }
    }

    private static IndustrialRecipe simple(ResourceLocation id, IndustryMachine machine, Ingredient input, int count, ItemStack result, int time) {
        return simple(id, machine, input, count, result, time, Ingredient.EMPTY, 0);
    }

    /** MAJ Recettes (lot 8a) : le liant du compacteur devient la 2e entree d'objets de la recette du multibloc (et de JEI). */
    private static IndustrialRecipe simple(ResourceLocation id, IndustryMachine machine, Ingredient input, int count, ItemStack result, int time,
          Ingredient binder, int binderCount) {
        //1.0.3-beta (lot 3) : concasses de tier 1 : 2 par minerai a tous les tiers (le creuset electrique fait le gain du tier)
        boolean ore = result.is(ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "crushed_ores")))
              && !result.is(fr.pioneers.core.PiTags.Items.FIXED_CRUSH_YIELD);
        List<Counted> items = binderCount > 0 ? List.of(new Counted(input, count), new Counted(binder, binderCount)) : List.of(new Counted(input, count));
        return new IndustrialRecipe(id, machine, 0, time, items, List.of(), List.of(), List.of(new Result(result, 1F)), List.of(),
              List.of(), 0, false, false, false, 0, 0, ore);
    }

    /** Recette de combustion d'un combustible solide (generateur thermique) : la duree de combustion, 1 024 FE/t au tier basique. */
    @Nullable
    public static synchronized IndustrialRecipe fuel(ItemStack stack) {
        if (stack.isEmpty()) {
            return null;
        }
        int burn = solidBurnTime(stack);
        if (burn <= 0) {
            return null;
        }
        if (fuelGeneration != PiRecipes.generation()) {
            fuelGeneration = PiRecipes.generation();
            FUELS.clear();
        }
        return FUELS.computeIfAbsent(stack.getItem(), item -> new IndustrialRecipe(fuelId(item),
              IndustryMachine.SOLID_COMBUSTION_GENERATOR, 0, burn, List.of(new Counted(Ingredient.of(item), 1)), List.of(), List.of(), List.of(), List.of(), List.of(),
              Tiers.BASE_POWER, false, false, false, 0, 0, false));
    }

    /**
     * 1.0.3-beta (lot 6, Guillaume : « les seaux de liquide ne doivent pas rentrer dans le generateur a combustion solide ») : un objet qui
     * CONTIENT ou porte un fluide (seau de lave, seaux du mod, capsules, tout objet a capacite fluide) n'est jamais un combustible SOLIDE, meme
     * avec une duree de combustion vanilla ; sa place est le generateur a combustion liquide. Seule regle : interface, Maj + clic, entonnoirs,
     * cables et robots passent par {@link #accepts}, la combustion par {@link #fuel}, JEI et les infobulles par {@code GeneratorOutput.solidFuel}.
     */
    public static boolean holdsFluid(ItemStack stack) {
        if (stack.isEmpty()) {
            return false;
        }
        Item item = stack.getItem();
        if (item instanceof net.minecraft.world.item.BucketItem || item instanceof net.minecraft.world.item.MilkBucketItem) {
            return true;
        }
        return stack.getCapability(net.minecraftforge.common.capabilities.ForgeCapabilities.FLUID_HANDLER_ITEM).isPresent();
    }

    /** Duree de combustion d'un combustible SOLIDE (0 : ne brule pas, ou porte un fluide : {@link #holdsFluid}). */
    public static int solidBurnTime(ItemStack stack) {
        if (stack.isEmpty() || holdsFluid(stack)) {
            return 0;
        }
        return ForgeHooks.getBurnTime(stack, null);
    }

    private static ResourceLocation fuelId(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return ResourceLocation.fromNamespaceAndPath(key.getNamespace(), "fuel/" + key.getPath());
    }

    /**
     * 1.0 : le generateur a combustion (unique : l'ancien generateur thermique et le groupe electrogene y sont reunis) brule aussi les
     * combustibles solides, en plus de ses recettes de liquides et de gaz.
     */
    public static boolean burnsSolid(IndustryMachine machine) {
        return machine == IndustryMachine.SOLID_COMBUSTION_GENERATOR || machine.source == IndustryMachine.Source.FUEL;
    }

    /** Toutes les recettes, pour une machine qui accepte cet objet en entree (filtre des cases). */
    public static synchronized boolean accepts(Level level, IndustryMachine machine, ItemStack stack) {
        if (burnsSolid(machine) && solidBurnTime(stack) > 0) {
            return true;
        }
        if (machine.source == IndustryMachine.Source.FUEL || burnsSolid(machine) && holdsFluid(stack)) {
            //Lot 6 (1.0.3) : un objet a fluide n'entre jamais dans le generateur solide, meme si une recette le citait
            return false;
        }
        if (stack.hasTag()) {
            return scan(level, machine, stack);
        }
        return side(level).accepts.computeIfAbsent(machine, m -> new HashMap<>()).computeIfAbsent(stack.getItem(), i -> scan(level, machine, stack));
    }

    /**
     * 0.20.28 : nombre d'ingredients-objets differents de la recette la plus fournie ou entre cet objet (1 si aucune) : le port d'entree
     * intelligent garde des cases libres pour les autres ingredients ({@code SmartPorts.Items}).
     */
    public static synchronized int itemKinds(Level level, IndustryMachine machine, ItemStack stack) {
        return side(level).kinds.computeIfAbsent(machine, m -> new HashMap<>()).computeIfAbsent(stack.getItem(), i -> {
            int best = 1;
            for (IndustrialRecipe r : of(level, machine)) {
                for (Counted c : r.items()) {
                    if (c.ingredient().test(stack)) {
                        best = Math.max(best, r.items().size());
                        break;
                    }
                }
            }
            return best;
        });
    }

    /** 0.20.28 : le fluide sert-il d'entree a une recette de la machine ? */
    public static boolean acceptsFluid(Level level, IndustryMachine machine, FluidStack fluid) {
        for (IndustrialRecipe r : of(level, machine)) {
            for (IndustrialRecipe.FluidIn in : r.fluids()) {
                if (in.test(fluid)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Le gaz sert-il d'entree a une recette de la machine ? (filtre des reservoirs de gaz) */
    public static boolean acceptsGas(Level level, IndustryMachine machine, GasStack gas) {
        for (IndustrialRecipe r : of(level, machine)) {
            for (IndustrialRecipe.GasAmount in : r.gases()) {
                if (in.test(gas)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** 0.20.34 : un type de donnees sert-il a une recette de cette machine (reservoirs de donnees du synthetiseur) ? */
    public static boolean acceptsData(Level level, IndustryMachine machine, DataType type) {
        for (IndustrialRecipe r : of(level, machine)) {
            for (DataStack in : r.data()) {
                if (in.isTypeEqual(type)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean scan(Level level, IndustryMachine machine, ItemStack stack) {
        for (IndustrialRecipe r : of(level, machine)) {
            for (Counted c : r.items()) {
                if (c.ingredient().test(stack)) {
                    return true;
                }
            }
        }
        return false;
    }
}
