package fr.pioneers.api.machine.tier;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.PowerRules;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.core.PiTags;
import fr.pioneers.core.recipe.RecipeChoice;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.machine.ProcessingRecipe;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 38, retours 49, 52, 53 ; specification docs/specification-age-bronze-1.0.5.md §2.2) : MOTEUR DES FAMILLES DE TIERS. Une
 * machine de tier global N fait toutes les recettes de sa famille de tier minimal m <= N. Ici, la VUE COMMUNE des recettes BASSES (m <= 1)
 * d'une famille, quelle que soit leur source ({@link GeneratedTierFamilies.Family#low} : recettes mecaniques {@code pioneers:milling}...,
 * m = 0 ; recettes a vapeur {@code pioneers:steam_processing}, m = champ {@code min_tier} ; cuissons vanilla de la famille « cuisson », m = 1) :
 * entrees comptees, eau, sorties et chances, duree de base, rendement. Les machines a vapeur (T1) les font directement
 * ({@code SteamProcess}) ; les machines MKI et plus les recoivent converties ({@code IndustryRecipes.lowTier}).
 * <ul>
 *     <li>duree de base : recette mecanique = tours x 1 200 / vitesse nominale de SA machine T0 ; recette a vapeur = son champ {@code time} ;
 *     cuisson = {@link #SMELT_TIME} ; au tier N : ÷ 2^(N - m) ({@link PowerRules#globalDuration}) ;</li>
 *     <li>rendement : recettes A RENDEMENT seulement ({@link GeneratedTierFamilies#YIELD_RECIPES} : matieres premieres) : sorties et chances
 *     x R(N) / R(m) ; regle 1 de Guillaume (05/11/2026, ecart ecrit a la spec §2.4) : entree ou produit dans {@code pioneers:fixed_crush_yield}
 *     -> gain plafonne a T1 ({@link TierRecipe#capped}) ;</li>
 *     <li>ordre (choix sans verrou) : plus haut m d'abord, puis la plus complete (entrees + eau : le melangeur essaie le colorant avant le gris
 *     clair, comme avant), puis l'identifiant ;</li>
 *     <li>doublons (meme famille, meme entree unique, meme produit principal) : la recette de plus haut m n'est gardee que si elle rend plus a T1
 *     ({@link #dedupe}).</li>
 * </ul>
 * Listes gardees jusqu'au prochain rechargement des recettes, une par cote (en solo les deux gestionnaires alternent) : aucune allocation au
 * tick (regles de performance §5).
 * <p>
 * 1.0.5-beta (lot 47, reponse de Guillaume du 14/11/2026 : « Ouvre les recettes KI qui ne desequilibreraient pas le jeu ») : la machine a vapeur
 * fait AUSSI les recettes MKI de sa famille que la source unique tools/gc/core/p105_lot47.py a ouvertes
 * ({@link GeneratedTierFamilies#OPEN_MKI} : criteres ecrits dans la specification §7 quinquies) : vue {@link TierRecipe} de source
 * {@code mki:<machine MKI>}, tier minimal 1, memes entrees et sorties, sans rendement, duree au MKI x {@link GeneratedTierFamilies#OPEN_SLOWDOWN}
 * (la machine MKI reste deux fois plus rapide). Elles viennent APRES les recettes de la famille ({@link #natives}) : sans verrou, une machine
 * a vapeur d'un monde existant fait ce qu'elle faisait ; le selecteur les propose a la suite. Les machines MKI ne les recoivent pas d'ici (ce
 * sont deja leurs recettes : {@code IndustryRecipes.low} lit {@link #natives}).
 */
public final class TierRecipes {

    /** Duree de base d'une cuisson de la famille « cuisson » (ticks au tier 1 : celle du four a vapeur d'avant). */
    public static final int SMELT_TIME = 100;
    /** Source des recettes du selecteur d'une machine T1 : {@code family:<famille>}. */
    public static final String SOURCE = "family:";
    /** 1.0.5-beta (lot 47) : source d'une recette MKI ouverte a la vapeur : {@code mki:<cle de la machine MKI>}. */
    public static final String OPEN_SOURCE = "mki:";

    /** Une sortie : objet (avec sa quantite) et sa chance. */
    public record Out(ItemStack stack, float chance) {
    }

    /**
     * Une recette de famille, telle que la voient les machines : identifiant (celui de la recette d'origine), famille, tier minimal global, entrees
     * comptees (dans n'importe quel ordre), eau (mB, melangeur), sorties (la premiere = produit principal), duree de base (ticks au tier m),
     * rendement, plafond a T1 (regle 1), source ({@code mechanical:<sorte>}, {@code steam:<machine>}, {@code smelting}).
     */
    public record TierRecipe(ResourceLocation id, String family, int minTier, List<Counted> inputs, int water, List<Out> outputs, int time,
                             boolean yield, boolean capped, String source) {

        public ItemStack main() {
            return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).stack();
        }

        /** 1.0.5-beta (lot 47) : recette MKI ouverte a la machine a vapeur (deux fois plus lente qu'au MKI). */
        public boolean opened() {
            return source.startsWith(OPEN_SOURCE);
        }

        /** Duree (ticks) au tier global {@code tier}. */
        public int duration(int tier) {
            return PowerRules.globalDuration(time, tier, minTier);
        }

        /** Multiplicateur des sorties et des chances au tier global {@code tier} (1 sans rendement). */
        public double yieldFactor(int tier) {
            if (!yield) {
                return 1;
            }
            return PowerRules.yieldFactor(capped ? Math.min(tier, Math.max(1, minTier)) : tier, minTier);
        }

        /** Nombre de sortes d'entrees (eau comprise) : la plus complete est essayee d'abord. */
        public int specificity() {
            return inputs.size() + (water > 0 ? 1 : 0);
        }

        /** Produit principal attendu par operation, sans rendement (somme des sorties du meme objet que la premiere, x chance). */
        public double expectedMain() {
            if (outputs.isEmpty()) {
                return 0;
            }
            Item item = outputs.get(0).stack().getItem();
            double n = 0;
            for (Out o : outputs) {
                if (o.stack().is(item)) {
                    n += o.stack().getCount() * (double) Math.min(1F, o.chance());
                }
            }
            return n;
        }

        /** Case utilisee par chaque entree (cases distinctes, quantite suffisante), ou null (meme regle que {@link SteamRecipe#match}). */
        @Nullable
        public int[] match(List<ItemStack> slots) {
            int[] used = new int[inputs.size()];
            boolean[] taken = new boolean[slots.size()];
            for (int i = 0; i < inputs.size(); i++) {
                used[i] = -1;
                for (int s = 0; s < slots.size(); s++) {
                    ItemStack stack = slots.get(s);
                    if (!taken[s] && !stack.isEmpty() && inputs.get(i).ingredient().test(stack) && stack.getCount() >= inputs.get(i).count()) {
                        taken[s] = true;
                        used[i] = s;
                        break;
                    }
                }
                if (used[i] < 0) {
                    return null;
                }
            }
            return used;
        }

        /** L'objet entre-t-il dans la recette ? */
        public boolean takes(ItemStack stack) {
            for (Counted c : inputs) {
                if (c.ingredient().test(stack)) {
                    return true;
                }
            }
            return false;
        }

        /**
         * Recette du selecteur : tier MK = m - 2 (tier global 0 -> -2, 1 -> -1 : toujours a la portee d'une machine T1 et des MKI), entrees, eau,
         * sorties, duree de base (ticks).
         */
        public RecipeChoice choice() {
            List<ItemStack> outs = new ArrayList<>(outputs.size());
            for (Out o : outputs) {
                outs.add(o.stack());
            }
            List<IndustrialRecipe.FluidIn> fluids = water > 0 ? List.of(new IndustrialRecipe.FluidIn(Fluids.WATER, null, water)) : List.of();
            return new RecipeChoice(id, minTier - 2, false, inputs, fluids, List.of(), List.of(), List.copyOf(outs), List.of(), List.of(), time, false);
        }
    }

    /** Cache d'un cote (client ou serveur). */
    private static final class Side {

        RecipeManager manager;
        int generation = -1;
        final Map<String, List<TierRecipe>> recipes = new HashMap<>();
        /** 1.0.5-beta (lot 47) : recettes de la famille sans les recettes MKI ouvertes. */
        final Map<String, List<TierRecipe>> natives = new HashMap<>();
        final Map<String, Map<ResourceLocation, TierRecipe>> byId = new HashMap<>();
        final Map<String, List<RecipeChoice>> choices = new HashMap<>();
        final Map<String, Map<Item, Boolean>> accepts = new HashMap<>();
    }

    private static final Side[] SIDES = {new Side(), new Side()};

    /** Ordre d'essai : plus haut m, puis la plus complete, puis l'identifiant (meme ordre client / serveur). */
    public static final Comparator<TierRecipe> ORDER = Comparator.comparingInt((TierRecipe r) -> -r.minTier())
          .thenComparingInt(r -> -r.specificity()).thenComparing(r -> r.id().toString());

    private TierRecipes() {
    }

    private static Side side(Level level) {
        Side side = SIDES[level.isClientSide ? 1 : 0];
        RecipeManager manager = level.getRecipeManager();
        int generation = PiRecipes.generation();
        if (manager != side.manager || side.generation != generation) {
            side.manager = manager;
            side.generation = generation;
            side.recipes.clear();
            side.natives.clear();
            side.byId.clear();
            side.choices.clear();
            side.accepts.clear();
        }
        return side;
    }

    /** La famille {@code key} (null si elle n'existe pas). */
    @Nullable
    public static GeneratedTierFamilies.Family family(@Nullable String key) {
        for (GeneratedTierFamilies.Family f : GeneratedTierFamilies.FAMILIES) {
            if (f.key().equals(key)) {
                return f;
            }
        }
        return null;
    }

    /** Famille d'un bloc (id sans espace de noms), ou null. */
    @Nullable
    public static String familyOf(String blockId) {
        return GeneratedTierFamilies.FAMILY_OF.get(blockId);
    }

    /** Tier global d'un bloc (id sans espace de noms) : 0 .. 5, -1 (multibloc : ses carcasses), ou -2 hors des familles. */
    public static int tierOf(String blockId) {
        Integer t = GeneratedTierFamilies.TIER_OF.get(blockId);
        return t == null ? -2 : t;
    }

    /**
     * Les recettes que fait la machine T1 de la famille, dans l'ordre d'essai : recettes basses (m <= 1) puis, lot 47, recettes MKI ouvertes a
     * la vapeur (liste non modifiable ; vide si la famille n'en a pas).
     */
    public static synchronized List<TierRecipe> of(Level level, @Nullable String family) {
        if (family == null) {
            return List.of();
        }
        Side side = side(level);
        return side.recipes.computeIfAbsent(family, f -> {
            List<TierRecipe> low = natives(level, f);
            List<TierRecipe> open = opened(level, f, low);
            if (open.isEmpty()) {
                return low;
            }
            List<TierRecipe> all = new ArrayList<>(low.size() + open.size());
            all.addAll(low);
            all.addAll(open);
            return List.copyOf(all);
        });
    }

    /** 1.0.5-beta (lot 47) : les recettes basses (m <= 1) de la famille seulement, sans les recettes MKI ouvertes (machines MKI, doublons). */
    public static synchronized List<TierRecipe> natives(Level level, @Nullable String family) {
        if (family == null) {
            return List.of();
        }
        return side(level).natives.computeIfAbsent(family, f -> build(level, f));
    }

    /** Recette de la famille par son identifiant (verrou du selecteur), ou null. */
    @Nullable
    public static synchronized TierRecipe byId(Level level, @Nullable String family, @Nullable ResourceLocation id) {
        if (family == null || id == null) {
            return null;
        }
        Side side = side(level);
        return side.byId.computeIfAbsent(family, f -> {
            Map<ResourceLocation, TierRecipe> map = new HashMap<>();
            for (TierRecipe r : of(level, f)) {
                map.putIfAbsent(r.id(), r);
            }
            return map;
        }).get(id);
    }

    /** Recettes du selecteur d'une machine T1 de la famille (meme ordre que {@link #of}). */
    public static synchronized List<RecipeChoice> choices(Level level, @Nullable String family) {
        if (family == null) {
            return List.of();
        }
        Side side = side(level);
        return side.choices.computeIfAbsent(family, f -> {
            List<RecipeChoice> out = new ArrayList<>();
            for (TierRecipe r : of(level, f)) {
                out.add(r.choice());
            }
            return List.copyOf(out);
        });
    }

    /** Une recette de la famille prend-elle cet objet ? (filtre des entonnoirs, cables et robots ; garde par objet sans NBT) */
    public static synchronized boolean accepts(Level level, @Nullable String family, ItemStack stack) {
        if (family == null || stack.isEmpty()) {
            return false;
        }
        Side side = side(level);
        List<TierRecipe> list = of(level, family);
        if (stack.hasTag()) {
            return scan(list, stack);
        }
        return side.accepts.computeIfAbsent(family, f -> new HashMap<>()).computeIfAbsent(stack.getItem(), i -> scan(list, stack));
    }

    private static boolean scan(List<TierRecipe> list, ItemStack stack) {
        for (TierRecipe r : list) {
            if (r.takes(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Recette a faire pour ces piles et cette eau : la recette verrouillee si {@code locked} (null si elle ne convient pas : la machine attend),
     * sinon la premiere qui convient dans l'ordre de {@link #of}.
     */
    @Nullable
    public static TierRecipe find(List<TierRecipe> list, List<ItemStack> stacks, int water, @Nullable TierRecipe locked) {
        if (locked != null) {
            return (locked.water() <= water && locked.match(stacks) != null) ? locked : null;
        }
        for (TierRecipe r : list) {
            if (r.water() <= water && r.match(stacks) != null) {
                return r;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Construction
    // ------------------------------------------------------------------

    private static List<TierRecipe> build(Level level, String key) {
        GeneratedTierFamilies.Family family = family(key);
        if (family == null || !family.engine()) {
            return List.of();
        }
        List<TierRecipe> out = new ArrayList<>();
        for (String source : family.low()) {
            if (source.startsWith("mechanical:")) {
                mechanical(level, key, source, out);
            } else if (source.startsWith("steam:")) {
                steam(level, key, source, out);
            } else if (source.equals("smelting")) {
                smelting(level, key, out);
            }
        }
        out = dedupe(out, 1);
        out.sort(ORDER);
        return List.copyOf(out);
    }

    private static void mechanical(Level level, String family, String source, List<TierRecipe> out) {
        String kindId = source.substring("mechanical:".length());
        for (MechanicalRecipe.Kind kind : MechanicalRecipe.Kind.values()) {
            if (!kind.id().equals(kindId)) {
                continue;
            }
            int rpm = GeneratedTierFamilies.NOMINAL_RPM.getOrDefault(kindId, 30);
            for (MechanicalRecipe r : PiRecipes.sorted(level, PiRecipes.MECHANICAL.get(kind).get())) {
                List<Out> outs = new ArrayList<>();
                for (MechanicalRecipe.Result res : r.results()) {
                    //1.0.2-beta : plus de case de maille : rendement de base = maille de fibres (1)
                    if (res.mesh() <= 1) {
                        outs.add(new Out(res.stack().copy(), Math.min(1F, res.chance())));
                    }
                }
                if (outs.isEmpty()) {
                    continue;
                }
                int time = Math.max(1, Math.round(r.turns() * 1200F / rpm));
                List<Counted> ins = List.of(new Counted(r.input(), r.count()));
                out.add(new TierRecipe(r.getId(), family, 0, ins, 0, List.copyOf(outs), time, isYield(r.getId()), capped(ins, outs), source));
            }
        }
    }

    private static void steam(Level level, String family, String source, List<TierRecipe> out) {
        String key = source.substring("steam:".length());
        SteamMachine machine = null;
        for (SteamMachine m : SteamMachine.values()) {
            if (m.key().equals(key)) {
                machine = m;
            }
        }
        if (machine == null) {
            return;
        }
        for (SteamRecipe r : PiRecipes.sorted(level, PiBronze.STEAM_PROCESSING.get())) {
            if (r.machine() != machine || r.minTier() > 1) {
                continue;
            }
            List<Out> outs = new ArrayList<>(2);
            outs.add(new Out(r.result().copy(), 1F));
            if (!r.byproduct().isEmpty() && r.chance() > 0) {
                outs.add(new Out(r.byproduct().copy(), r.chance()));
            }
            out.add(new TierRecipe(r.getId(), family, r.minTier(), r.inputs(), r.water(), List.copyOf(outs), Math.max(1, r.time()),
                  isYield(r.getId()), capped(r.inputs(), outs), source));
        }
    }

    private static void smelting(Level level, String family, List<TierRecipe> out) {
        for (SmeltingRecipe r : PiRecipes.sorted(level, RecipeType.SMELTING)) {
            if (r.getIngredients().isEmpty() || r.getIngredients().get(0).isEmpty()) {
                continue;
            }
            ItemStack result = r.getResultItem(level.registryAccess()).copy();
            if (result.isEmpty()) {
                continue;
            }
            out.add(new TierRecipe(r.getId(), family, 1, List.of(new Counted(r.getIngredients().get(0), 1)), 0, List.of(new Out(result, 1F)),
                  SMELT_TIME, false, false, "smelting"));
        }
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 47) : recettes MKI ouvertes a la vapeur
    // ------------------------------------------------------------------

    /** Recette MKI ouverte a la vapeur pour la famille ? (liste generee {@link GeneratedTierFamilies#OPEN_MKI}) */
    public static boolean isOpen(@Nullable ResourceLocation id, @Nullable String family) {
        return id != null && family != null && family.equals(GeneratedTierFamilies.OPEN_MKI.get(id.toString()));
    }

    /**
     * Recettes MKI ouvertes de la famille, vues comme des recettes du T1 (tier minimal 1, duree au MKI x OPEN_SLOWDOWN, sans rendement), dans
     * l'ordre (la plus complete, puis l'identifiant). Garde-fou d'execution (la liste generee peut retarder sur des recettes changees par un
     * paquet de donnees) : recette absente, multibloc seulement, gaz, fluide autre que l'eau du melangeur, sortie fluide, donnees, energie, plus
     * d'entrees que la machine, ou doublon d'une recette de la famille : sautee.
     */
    private static List<TierRecipe> opened(Level level, String key, List<TierRecipe> low) {
        if (!GeneratedTierFamilies.OPEN_MKI.containsValue(key)) {
            return List.of();
        }
        return open(key, PiRecipes.sorted(level, PiIndustry.INDUSTRIAL.get()), PiRecipes.sorted(level, PiRecipes.CRUSHING.get()),
              PiRecipes.sorted(level, PiRecipes.COMPRESSING.get()), low);
    }

    /**
     * Conversion PURE (sans monde : banc tools/lot47/Lot47Bench.java) des recettes MKI ouvertes de la famille {@code key} parmi ces recettes
     * industrielles et du kit (broyage, compactage), en ecartant les doublons de {@code low}.
     */
    public static List<TierRecipe> open(String key, List<IndustrialRecipe> industrial, List<ProcessingRecipe> crushing, List<ProcessingRecipe> compressing,
          List<TierRecipe> low) {
        GeneratedTierFamilies.Family family = family(key);
        SteamMachine machine = null;
        for (SteamMachine m : SteamMachine.values()) {
            if (key.equals(m.family())) {
                machine = m;
            }
        }
        if (family == null || machine == null || family.industry() == null || !GeneratedTierFamilies.OPEN_MKI.containsValue(key)) {
            return List.of();
        }
        List<TierRecipe> out = new ArrayList<>();
        String source = OPEN_SOURCE + family.industry();
        for (IndustrialRecipe r : industrial) {
            if (!isOpen(r.getId(), key) || !family.industry().equals(r.machine().key) || r.tier() != 0 || r.industrialOnly() || !r.gases().isEmpty()
                || !r.fluidResults().isEmpty() || !r.gasResults().isEmpty() || !r.data().isEmpty() || r.energy() > 0 || r.items().isEmpty()) {
                continue;
            }
            int water = 0;
            boolean other = false;
            for (IndustrialRecipe.FluidIn f : r.fluids()) {
                if (machine == SteamMachine.MIXER && f.test(new net.minecraftforge.fluids.FluidStack(Fluids.WATER, 1))) {
                    water += f.amount();
                } else {
                    other = true;
                }
            }
            if (other) {
                continue;
            }
            List<Out> outs = new ArrayList<>(r.results().size());
            for (IndustrialRecipe.Result res : r.results()) {
                outs.add(new Out(res.stack().copy(), res.chance()));
            }
            add(out, low, machine, new TierRecipe(r.getId(), key, 1, r.items(), water, List.copyOf(outs),
                  Math.max(1, r.time()) * GeneratedTierFamilies.OPEN_SLOWDOWN, false, false, source));
        }
        //recettes du kit (broyage, compactage) : la duree de la machine du kit au MKI
        kit(key, crushing, IndustryRecipes.CRUSH_TIME, source, machine, low, out);
        kit(key, compressing, IndustryRecipes.COMPRESS_TIME, source, machine, low, out);
        out.sort(ORDER);
        return List.copyOf(out);
    }

    private static void kit(String key, List<ProcessingRecipe> recipes, int time, String source, SteamMachine machine, List<TierRecipe> low,
          List<TierRecipe> out) {
        for (ProcessingRecipe r : recipes) {
            if (!isOpen(r.getId(), key) || r.getResult().isEmpty()) {
                continue;
            }
            List<Counted> ins = r.hasBinder() ? List.of(new Counted(r.getInput(), r.getCount()), new Counted(r.getBinder(), r.getBinderCount()))
                  : List.of(new Counted(r.getInput(), r.getCount()));
            add(out, low, machine, new TierRecipe(r.getId(), key, 1, ins, 0, List.of(new Out(r.getResult().copy(), 1F)),
                  time * GeneratedTierFamilies.OPEN_SLOWDOWN, false, false, source));
        }
    }

    /** Ajoute la recette ouverte si elle tient dans la machine (l'outil va dans la case Outil) et ne double pas une recette de la famille. */
    private static void add(List<TierRecipe> out, List<TierRecipe> low, SteamMachine machine, TierRecipe t) {
        int tool = fr.pioneers.tech.industry.recipe.Remanents.toolInput(fr.pioneers.tech.industry.recipe.Remanents.STEAM + machine.key(), t.inputs());
        if (t.inputs().size() - (tool >= 0 ? 1 : 0) > machine.inputs) {
            return;
        }
        for (TierRecipe n : low) {
            if (sameInputs(n, t)) {
                return;
            }
        }
        out.add(t);
    }

    /** Memes entrees (chaque entree recouvre une entree distincte de meme quantite) et meme produit principal. */
    public static boolean sameInputs(TierRecipe a, TierRecipe b) {
        if (a.inputs().size() != b.inputs().size() || a.main().isEmpty() || b.main().isEmpty() || !a.main().is(b.main().getItem())) {
            return false;
        }
        boolean[] used = new boolean[b.inputs().size()];
        for (Counted ca : a.inputs()) {
            boolean hit = false;
            for (int k = 0; k < used.length && !hit; k++) {
                Counted cb = b.inputs().get(k);
                if (!used[k] && ca.count() == cb.count() && overlap(ca.ingredient(), cb.ingredient())) {
                    used[k] = true;
                    hit = true;
                }
            }
            if (!hit) {
                return false;
            }
        }
        return true;
    }

    private static boolean isYield(ResourceLocation id) {
        return GeneratedTierFamilies.YIELD_RECIPES.contains(id.toString());
    }

    /** Regle 1 (05/11/2026) : entree ou produit principal dans {@code pioneers:fixed_crush_yield} : rendement plafonne a T1. */
    public static boolean capped(List<Counted> inputs, List<Out> outputs) {
        if (!outputs.isEmpty() && outputs.get(0).stack().is(PiTags.Items.FIXED_CRUSH_YIELD)) {
            return true;
        }
        for (Counted c : inputs) {
            for (ItemStack s : c.ingredient().getItems()) {
                if (s.is(PiTags.Items.FIXED_CRUSH_YIELD)) {
                    return true;
                }
            }
        }
        return false;
    }

    /**
     * Doublons (specification §2.2 point 4) : meme famille, une seule entree (meme quantite, objets communs), meme produit principal, tiers
     * minimaux differents : la recette de plus haut m n'est gardee que si elle rend STRICTEMENT plus au tier {@code tier} (sinon elle ne ferait
     * que masquer la recette basse, mieux multipliee) ; sinon c'est la plus basse qui part.
     */
    public static List<TierRecipe> dedupe(List<TierRecipe> list, int tier) {
        Map<ResourceLocation, TierRecipe> keep = new LinkedHashMap<>();
        for (TierRecipe r : list) {
            keep.put(r.id(), r);
        }
        for (TierRecipe hi : list) {
            for (TierRecipe lo : list) {
                if (hi == lo || hi.minTier() <= lo.minTier() || !keep.containsKey(hi.id()) || !keep.containsKey(lo.id()) || !same(hi, lo)) {
                    continue;
                }
                double eh = hi.expectedMain() * hi.yieldFactor(tier), el = lo.expectedMain() * lo.yieldFactor(tier);
                if (eh > el + 1e-9) {
                    keep.remove(lo.id());
                } else {
                    keep.remove(hi.id());
                }
            }
        }
        return keep.size() == list.size() ? new ArrayList<>(list) : new ArrayList<>(keep.values());
    }

    /** Meme entree unique (meme quantite, au moins un objet commun) et meme produit principal. */
    public static boolean same(TierRecipe a, TierRecipe b) {
        if (a.inputs().size() != 1 || b.inputs().size() != 1 || a.water() != b.water() || a.main().isEmpty() || !a.main().is(b.main().getItem())) {
            return false;
        }
        Counted ca = a.inputs().get(0), cb = b.inputs().get(0);
        return ca.count() == cb.count() && overlap(ca.ingredient(), cb.ingredient());
    }

    /** Deux ingredients acceptent-ils au moins un meme objet ? */
    public static boolean overlap(Ingredient a, Ingredient b) {
        for (ItemStack s : a.getItems()) {
            if (b.test(s)) {
                return true;
            }
        }
        return false;
    }

    /** Pour les journaux et les bancs : resume d'une recette. */
    public static String describe(TierRecipe r, int tier) {
        return Pioneers.MODID + " " + r.id() + " m" + r.minTier() + " x" + String.format(java.util.Locale.ROOT, "%.3f", r.yieldFactor(tier)) + " "
               + r.duration(tier) + "t";
    }
}
