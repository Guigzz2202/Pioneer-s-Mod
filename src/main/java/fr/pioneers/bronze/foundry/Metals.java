package fr.pioneers.bronze.foundry;

import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.materials.GeneratedMaterials;
import fr.pioneers.registries.PiBronze;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.ToIntFunction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.Nullable;

/**
 * Metaux de la fonderie (tables generees : couleur, fusion, composition des alliages) et ce que chaque objet donne une fois fondu. 1 lingot =
 * 144 mB. 1.0.2-beta (lot 2, notes de Guillaume : « plus de notion d'impurete : soit on obtient l'alliage soit on n'obtient rien ») : un
 * melange qui n'est ni un metal pur ni un alliage dans ses proportions ne coule plus en « lingot impur ».
 * <p>
 * 1.0.3-beta (lot 7b, Guillaume : « les forges doivent etre plus smart quand on veut faire un melange : ne pas se bloquer a cause des
 * proportions, mais utiliser tres intelligemment ce qu'elles ont en entree et selon le filtre ») : REGLE COMMUNE de toutes les forges a bain
 * (creuset en argile et sa pince, fourneau a creuset, fonderie automatique, fondeur d'alliages a vapeur) :
 * <ul>
 *     <li>rapport IDEAL d'un alliage = {@link #weights} (= {@link #RECIPES} : bronze et laiton 3:1, soudure 3:2) = le MILIEU de ses fourchettes
 *     (fourchettes recentrees, plus de tolerance de 0,5 %) ;</li>
 *     <li>a la coulee ({@link #pourable}, {@link #plan}) : un bain qui est exactement un metal ou un alliage ({@link #resolve}) coule tout
 *     entier ; sinon on coule la PLUS GRANDE part possible de l'alliage vise (filtre) ou, sans filtre, de l'alliage qui prend le plus du bain
 *     ({@link #portions} : decomposition gloutonne au rapport ideal), et le surplus RESTE dans le bain (metal pur ou autre alliage) et se
 *     coule ensuite ; filtre sur un metal pur : sa part libre seulement ; bain plein qu'un filtre ne fait plus avancer : le surplus coule
 *     ({@code overflow}) ;</li>
 *     <li>la part coulee est retiree EXACTEMENT ({@link #pour} : entiers, plus grands restes, jamais plus que le bain n'a) : aucune perte ni
 *     duplication de mB ;</li>
 *     <li>sortie de secours ({@link #spill}) : le bain rendu en lingots et pepites de chaque metal, les restes de moins d'une pepite en une
 *     scorie.</li>
 * </ul>
 */
public final class Metals {

    public static final int INGOT = 144;
    /**
     * Proportions de recette des alliages du creuset (lingots ; memes que le fondeur d'alliages a vapeur et le creuset electrique) : ce que
     * le selecteur de recette du fourneau a creuset dose ({@code CrucibleUnit}).
     */
    public static final Map<String, Map<String, Integer>> RECIPES = Map.of(
          "bronze", Map.of("copper", 3, "tin", 1),
          "brass", Map.of("copper", 3, "zinc", 1),
          "solder", Map.of("tin", 3, "lead", 2));
    private static final Map<String, GeneratedMaterials.Metal> METALS = new LinkedHashMap<>();
    private static Map<Item, GeneratedMaterials.Melt> melts;

    static {
        for (GeneratedMaterials.Metal metal : GeneratedMaterials.METALS) {
            METALS.put(metal.id(), metal);
        }
    }

    private Metals() {
    }

    @Nullable
    public static GeneratedMaterials.Metal get(String id) {
        return METALS.get(id);
    }

    public static Iterable<GeneratedMaterials.Metal> all() {
        return METALS.values();
    }

    /** Temperature de fusion (degres C). */
    public static int meltingC(String id) {
        GeneratedMaterials.Metal metal = METALS.get(id);
        return metal == null ? 1000 : metal.melting() - 273;
    }

    public static int colour(String id) {
        GeneratedMaterials.Metal metal = METALS.get(id);
        return metal == null ? 0x6A5A4A : metal.colour();
    }

    public static Component name(String id) {
        return Component.translatable("metal.pioneers." + id);
    }

    @Nullable
    public static GeneratedMaterials.Melt melt(ItemStack stack) {
        if (melts == null) {
            Map<Item, GeneratedMaterials.Melt> map = new HashMap<>();
            for (GeneratedMaterials.Melt melt : GeneratedMaterials.MELTS) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(melt.item()));
                if (item != null && item != Items.AIR) {
                    map.put(item, melt);
                }
            }
            melts = map;
        }
        return melts.get(stack.getItem());
    }

    /**
     * Ajoute {@code mb} de {@code metal} a un melange : un alliage fondu compte comme ses composants (milieu de chaque fourchette, ramene a
     * 100 %), recursivement ; le total ajoute reste exactement {@code mb}. Ainsi du bronze fondu avec du cuivre et de l'etain donne du bronze.
     */
    public static void addTo(Map<String, Integer> mix, String metal, int mb) {
        if (mb <= 0) {
            return;
        }
        Map<String, Double> parts = components(metal);
        int left = mb, i = 0;
        for (Map.Entry<String, Double> e : parts.entrySet()) {
            int part = ++i == parts.size() ? left : (int) Math.round(mb * e.getValue());
            part = Math.min(part, left);
            left -= part;
            if (part > 0) {
                mix.merge(e.getKey(), part, Integer::sum);
            }
        }
    }

    /** Composition elementaire d'un metal (fractions qui font 1) : lui-meme s'il est pur ou inconnu, sinon les metaux de base de l'alliage. */
    public static Map<String, Double> components(String metal) {
        Map<String, Double> out = new LinkedHashMap<>();
        components(metal, 1.0, out, 0);
        return out;
    }

    private static void components(String metal, double weight, Map<String, Double> out, int depth) {
        GeneratedMaterials.Metal m = METALS.get(metal);
        if (m == null || m.composition().isEmpty() || depth > 4) {
            out.merge(metal, weight, Double::sum);
            return;
        }
        double sum = 0;
        for (GeneratedMaterials.Share share : m.composition()) {
            sum += (share.min() + share.max()) / 2.0;
        }
        for (GeneratedMaterials.Share share : m.composition()) {
            components(share.metal(), weight * (share.min() + share.max()) / 2.0 / sum, out, depth + 1);
        }
    }

    /** Le melange, alliages decomposes (anciennes sauvegardes, lingots impurs d'avant 1.3.2). */
    public static Map<String, Integer> normalized(Map<String, Integer> mix) {
        Map<String, Integer> out = new LinkedHashMap<>();
        mix.forEach((m, v) -> addTo(out, m, v));
        return out;
    }

    /**
     * Ce que devient un melange : un metal pur (seul), un alliage dont toutes les parts tombent dans leurs fourchettes et rien d'autre ; sinon
     * RIEN (texte vide : voir {@link #mixed}). 1.0.2-beta : plus de tolerance de 2 % ni de « melange impur ». 1.0.3-beta (lot 7b) : plus de
     * tolerance de 0,5 % non plus (les fourchettes sont centrees sur le rapport de recette) ; un bain qui ne tombe pas dans une fourchette
     * se coule quand meme en partie ({@link #pourable}).
     */
    public static String resolve(Map<String, Integer> raw) {
        Map<String, Integer> mix = normalized(raw);
        int total = 0;
        for (int v : mix.values()) {
            total += v;
        }
        if (total <= 0) {
            return "";
        }
        for (Map.Entry<String, Integer> e : mix.entrySet()) {
            if (e.getValue() >= total) {
                return e.getKey();
            }
        }
        for (GeneratedMaterials.Metal metal : METALS.values()) {
            if (metal.composition().isEmpty()) {
                continue;
            }
            int covered = 0;
            boolean ok = true;
            for (GeneratedMaterials.Share share : metal.composition()) {
                int amount = mix.getOrDefault(share.metal(), 0);
                double pct = amount * 100.0 / total;
                if (pct < share.min() - 1e-9 || pct > share.max() + 1e-9) {
                    ok = false;
                    break;
                }
                covered += amount;
            }
            if (ok && covered >= total) {
                return metal.id();
            }
        }
        return "";
    }

    /**
     * 1.0.2-beta : DOSAGE d'un bain verrouille sur {@code locked} : ajouter {@code adds} (un seul metal) garde-t-il le bain sur ses proportions ?
     * Oui si le metal ajoute est le plus en retard (part / poids ideal la plus basse), ou s'il ne passe pas devant le plus avance. Sans verrou,
     * ou verrou sur un metal pur : toujours. 1.0.3-beta (lot 7b) : tout alliage ({@link #weights}, rapport ideal = milieu des fourchettes) et
     * toutes les forges a selecteur (fourneau a creuset, fonderie automatique) ; ce qui n'est pas pris reste dans sa case.
     */
    public static boolean dosed(Map<String, Integer> mix, String locked, Map<String, Integer> adds) {
        Map<String, Integer> recipe = weights(locked);
        if (recipe.isEmpty() || adds.size() != 1) {
            return true;
        }
        String metal = adds.keySet().iterator().next();
        Integer share = recipe.get(metal);
        if (share == null) {
            return false;
        }
        double ahead = 0, behind = Double.MAX_VALUE;
        for (Map.Entry<String, Integer> e : recipe.entrySet()) {
            double k = mix.getOrDefault(e.getKey(), 0) / (double) e.getValue();
            ahead = Math.max(ahead, k);
            behind = Math.min(behind, k);
        }
        double mine = mix.getOrDefault(metal, 0) / (double) share;
        return mine <= behind + 1e-6 || (mix.getOrDefault(metal, 0) + adds.get(metal)) / (double) share <= ahead + 1e-6;
    }

    /**
     * 1.0.3-beta (lot 7b) : metal que le bain verrouille sur l'alliage {@code locked} attend (le composant le plus en retard sur le rapport
     * ideal) ; vide sans verrou, verrou sur un metal pur, ou bain deja au rapport.
     */
    public static String waitingFor(Map<String, Integer> mix, String locked) {
        Map<String, Integer> recipe = weights(locked);
        String best = "";
        double behind = Double.MAX_VALUE, ahead = 0;
        for (Map.Entry<String, Integer> e : recipe.entrySet()) {
            double k = mix.getOrDefault(e.getKey(), 0) / (double) e.getValue();
            ahead = Math.max(ahead, k);
            if (k < behind - 1e-9) {
                behind = k;
                best = e.getKey();
            }
        }
        return best.isEmpty() || ahead - behind < 1e-6 && behind > 0 ? "" : best;
    }

    /** 1.0.2-beta : un bain verrouille sur {@code locked} (vide : aucun verrou) accepte-t-il ce metal (lui-meme ou un de ses composants) ? */
    public static boolean allowedBy(String locked, String metal) {
        return locked.isEmpty() || metal.equals(locked) || components(locked).containsKey(metal);
    }

    /**
     * 1.0.2-beta : PRELEVEMENT proportionnel de {@code amount} mB dans le bain (la composition du reste ne change pas) ; renvoie ce qui est
     * pris par metal. 1.0.3-beta (lot 7b) : EXACT (plus grands restes, jamais plus qu'un metal n'a) : la somme prise vaut
     * min(amount, total).
     */
    public static Map<String, Integer> take(Map<String, Integer> mix, int amount) {
        int total = mix.values().stream().mapToInt(Integer::intValue).sum();
        Map<String, Double> ideal = new LinkedHashMap<>();
        int left = Math.min(Math.max(0, amount), total);
        mix.forEach((m, v) -> ideal.put(m, left * v / (double) Math.max(1, total)));
        Map<String, Integer> poured = split(left, ideal, mix);
        subtract(mix, poured);
        return poured;
    }

    // ------------------------------------------------------------------
    // 1.0.3-beta (lot 7b) : regle commune des forges a bain (coulee intelligente, aucune forge bloquee)
    // ------------------------------------------------------------------

    /** Une part coulable du bain : un metal (pur ou alliage) et la quantite qui peut en sortir (mB). */
    public record Portion(String metal, int mb) {
    }

    /**
     * Ce que la forge coulera : {@code metal} (vide : rien), {@code mb} = toute la part coulable de ce metal, {@code rest} = le bain une fois
     * cette part coulee (metal -> mB), {@code waiting} = metal que le filtre attend (alliage verrouille), {@code reason} = pourquoi rien ne
     * coule ({@link #WHY_NONE}...).
     */
    public record Plan(String metal, int mb, Map<String, Integer> rest, String waiting, int reason) {
        /** Vrai si la forge peut couler maintenant ({@code metal} est alors ce qui coule). */
        public boolean pours() {
            return reason == WHY_NONE && !metal.isEmpty() && mb > 0;
        }
    }

    /** Raisons d'un plan vide : bain vide, attend un metal (filtre alliage), metal filtre tout pris dans un alliage, trop peu pour le moule. */
    public static final int WHY_NONE = 0, WHY_EMPTY = 1, WHY_WAITING = 2, WHY_BOUND = 3, WHY_TOO_LITTLE = 4;

    /** Vrai si ce metal est un alliage de bain (composition connue). */
    public static boolean isAlloy(String id) {
        GeneratedMaterials.Metal m = METALS.get(id);
        return m != null && !m.composition().isEmpty();
    }

    /**
     * Rapport IDEAL d'un alliage en poids entiers (metal -> poids) : {@link #RECIPES}, sinon les milieux des fourchettes ; vide pour un metal pur
     * ou inconnu. Le milieu des fourchettes des alliages de {@link #RECIPES} est leur rapport de recette.
     */
    public static Map<String, Integer> weights(String alloy) {
        Map<String, Integer> recipe = RECIPES.get(alloy);
        if (recipe != null) {
            return recipe;
        }
        GeneratedMaterials.Metal m = METALS.get(alloy);
        if (m == null || m.composition().isEmpty()) {
            return Map.of();
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (GeneratedMaterials.Share share : m.composition()) {
            out.put(share.metal(), Math.max(1, share.min() + share.max()));
        }
        return out;
    }

    /** Quantite (mB) de l'alliage {@code alloy} que le bain {@code mix} (elementaire) peut donner au rapport ideal : 0 s'il manque un composant. */
    public static int alloyMax(Map<String, Integer> mix, String alloy) {
        Map<String, Integer> w = weights(alloy);
        if (w.isEmpty()) {
            return 0;
        }
        int sum = w.values().stream().mapToInt(Integer::intValue).sum();
        long best = Long.MAX_VALUE;
        for (Map.Entry<String, Integer> e : w.entrySet()) {
            best = Math.min(best, (long) mix.getOrDefault(e.getKey(), 0) * sum / e.getValue());
        }
        return (int) Math.max(0, Math.min(best, Integer.MAX_VALUE));
    }

    /**
     * DECOMPOSITION du bain : l'alliage qui prend le plus du bain au rapport ideal d'abord (egalite : ordre de la table des metaux), puis les
     * suivants sur ce qui reste, puis les metaux purs restants (du plus abondant au moins abondant). La somme des parts vaut exactement le
     * total du bain.
     */
    public static List<Portion> portions(Map<String, Integer> raw) {
        Map<String, Integer> mix = normalized(raw);
        Map<String, Integer> alloys = new LinkedHashMap<>();
        for (int guard = 0; guard < 32; guard++) {
            String best = "";
            int most = 0;
            for (GeneratedMaterials.Metal metal : METALS.values()) {
                int a = metal.composition().isEmpty() ? 0 : alloyMax(mix, metal.id());
                if (a > most) {
                    most = a;
                    best = metal.id();
                }
            }
            if (most <= 0) {
                break;
            }
            subtract(mix, split(most, ideal(best, most), mix));
            alloys.merge(best, most, Integer::sum);
        }
        List<Portion> out = new ArrayList<>();
        alloys.forEach((m, v) -> out.add(new Portion(m, v)));
        out.sort((a, b) -> Integer.compare(b.mb(), a.mb()));
        List<Portion> pure = new ArrayList<>();
        mix.forEach((m, v) -> {
            if (v > 0) {
                pure.add(new Portion(m, v));
            }
        });
        pure.sort((a, b) -> Integer.compare(b.mb(), a.mb()));
        out.addAll(pure);
        return out;
    }

    /**
     * Ce que le bain peut couler, dans l'ordre de preference, selon le filtre {@code locked} (vide : aucun) :
     * <ul>
     *     <li>bain qui est exactement un metal ou un alliage ({@link #resolve}) admis par le filtre : tout le bain ;</li>
     *     <li>filtre sur un alliage : sa plus grande part au rapport ideal (rien s'il manque un composant) ;</li>
     *     <li>filtre sur un metal pur : sa part libre (hors alliages de la decomposition) ;</li>
     *     <li>sans filtre : la decomposition ({@link #portions}).</li>
     * </ul>
     * {@code overflow} (bain plein qu'aucune entree admise ne peut plus completer) : apres la part du filtre, le reste de la decomposition, pour
     * que le bain ne reste jamais bloque.
     */
    public static List<Portion> pourable(Map<String, Integer> raw, String locked, boolean overflow) {
        Map<String, Integer> mix = normalized(raw);
        int total = mix.values().stream().mapToInt(Integer::intValue).sum();
        List<Portion> out = new ArrayList<>();
        if (total <= 0) {
            return out;
        }
        String whole = resolve(mix);
        if (!whole.isEmpty() && (locked.isEmpty() || locked.equals(whole))) {
            out.add(new Portion(whole, total));
            return out;
        }
        List<Portion> parts = portions(mix);
        if (locked.isEmpty()) {
            return parts;
        }
        if (isAlloy(locked)) {
            int a = alloyMax(mix, locked);
            if (a > 0) {
                out.add(new Portion(locked, a));
            }
        } else {
            for (Portion p : parts) {
                if (p.metal().equals(locked)) {
                    out.add(p);
                }
            }
        }
        if (overflow) {
            Map<String, Integer> rest = new LinkedHashMap<>(mix);
            for (Portion p : out) {
                pour(rest, p.metal(), p.mb());
            }
            out.addAll(portions(rest));
        }
        return out;
    }

    /**
     * Plan de coulee (interface et choix de la forge) : la premiere part de {@link #pourable} qui remplit un moule ({@code need} : mB qu'un moule
     * demande pour ce metal, 0 = toute quantite, negatif = aucun moule pour ce metal) ; a defaut la premiere part (raison
     * {@link #WHY_TOO_LITTLE}) ; rien : la raison.
     */
    public static Plan plan(Map<String, Integer> raw, String locked, boolean overflow, ToIntFunction<String> need) {
        Map<String, Integer> mix = normalized(raw);
        if (mix.values().stream().mapToInt(Integer::intValue).sum() <= 0) {
            return new Plan("", 0, Map.of(), "", WHY_EMPTY);
        }
        List<Portion> list = pourable(mix, locked, overflow);
        Portion chosen = null;
        for (Portion p : list) {
            int n = need.applyAsInt(p.metal());
            if (n >= 0 && p.mb() >= n) {
                chosen = p;
                break;
            }
        }
        if (chosen != null) {
            Map<String, Integer> rest = new LinkedHashMap<>(mix);
            pour(rest, chosen.metal(), chosen.mb());
            return new Plan(chosen.metal(), chosen.mb(), rest, "", WHY_NONE);
        }
        String waiting = waitingFor(mix, locked);
        if (!list.isEmpty()) {
            Portion first = list.get(0);
            Map<String, Integer> rest = new LinkedHashMap<>(mix);
            pour(rest, first.metal(), first.mb());
            return new Plan(first.metal(), first.mb(), rest, waiting, isAlloy(locked) && !waiting.isEmpty() ? WHY_WAITING : WHY_TOO_LITTLE);
        }
        int why = isAlloy(locked) ? WHY_WAITING : locked.isEmpty() ? WHY_TOO_LITTLE : WHY_BOUND;
        return new Plan("", 0, mix, waiting, why);
    }

    /** Plan sans connaitre les moules (toute quantite convient). */
    public static Plan plan(Map<String, Integer> raw, String locked, boolean overflow) {
        return plan(raw, locked, overflow, m -> 0);
    }

    /**
     * COULEE de {@code amount} mB de {@code metal} hors du bain {@code mix} (elementaire, modifie) : bain qui est ce metal ou cet alliage = au
     * prorata ; metal pur = ce metal seul ; alliage = au rapport ideal. Renvoie les parts retirees (leur somme vaut {@code amount}, ou moins si
     * le bain ne l'a pas : la forge verifie avant avec {@link #pourable}).
     */
    public static Map<String, Integer> pour(Map<String, Integer> mix, String metal, int amount) {
        if (amount <= 0 || mix.isEmpty()) {
            return new LinkedHashMap<>();
        }
        if (metal.equals(resolve(mix))) {
            //Au prorata (la composition du reste ne change pas) ; une petite coulee d'alliage dont l'arrondi sortirait des fourchettes (16 mB a
            //81 / 19 %) se fait au rapport ideal
            Map<String, Integer> copy = new LinkedHashMap<>(mix);
            Map<String, Integer> prorata = take(copy, amount);
            if (!isAlloy(metal) || metal.equals(resolve(prorata)) || alloyMax(mix, metal) < amount) {
                mix.clear();
                mix.putAll(copy);
                return prorata;
            }
        }
        Map<String, Integer> poured;
        if (!isAlloy(metal)) {
            poured = new LinkedHashMap<>();
            int part = Math.min(amount, mix.getOrDefault(metal, 0));
            if (part > 0) {
                poured.put(metal, part);
            }
        } else {
            poured = split(Math.min(amount, alloyMax(mix, metal)), ideal(metal, amount), mix);
        }
        subtract(mix, poured);
        return poured;
    }

    /**
     * SORTIE DE SECOURS (« Vider le bain ») : chaque part du bain ({@link #pourable} sans filtre : le bain entier s'il est un metal ou un alliage,
     * sinon sa decomposition : bronze en lingots de bronze, surplus en cuivre...) en lingots et pepites ; les restes de moins d'une pepite (et
     * les metaux sans pepite) font une scorie. Le bain est vide ensuite.
     */
    public static List<ItemStack> spill(Map<String, Integer> mix) {
        List<ItemStack> out = new ArrayList<>();
        int residue = 0;
        for (Portion p : pourable(mix, "", false)) {
            int v = p.mb();
            int ingots = v / INGOT;
            int nuggets = (v - ingots * INGOT) / ForgeMetals.NUGGET_MB;
            ItemStack i = ingots > 0 ? ForgeMetals.ingot(p.metal(), ingots) : ItemStack.EMPTY;
            ItemStack n = nuggets > 0 ? ForgeMetals.nugget(p.metal(), nuggets) : ItemStack.EMPTY;
            int kept = (i.isEmpty() ? 0 : ingots * INGOT) + (n.isEmpty() ? 0 : nuggets * ForgeMetals.NUGGET_MB);
            stacks(out, i);
            stacks(out, n);
            residue += v - kept;
        }
        if (residue > 0) {
            out.add(new ItemStack(PiBronze.SLAG.get()));
        }
        mix.clear();
        return out;
    }

    private static void stacks(List<ItemStack> out, ItemStack stack) {
        while (!stack.isEmpty()) {
            int n = Math.min(stack.getCount(), stack.getMaxStackSize());
            out.add(stack.copyWithCount(n));
            stack.shrink(n);
        }
    }

    /** Parts ideales (reelles) de {@code amount} mB de l'alliage {@code alloy}. */
    private static Map<String, Double> ideal(String alloy, int amount) {
        Map<String, Integer> w = weights(alloy);
        int sum = Math.max(1, w.values().stream().mapToInt(Integer::intValue).sum());
        Map<String, Double> out = new LinkedHashMap<>();
        w.forEach((m, v) -> out.put(m, amount * v / (double) sum));
        return out;
    }

    /**
     * Repartit {@code amount} mB entiers selon les parts reelles {@code ideal} (somme = amount), sans depasser {@code caps} : parties entieres,
     * puis les unites restantes aux plus grands restes qui ont de la place, puis a qui a encore de la place.
     */
    static Map<String, Integer> split(int amount, Map<String, Double> ideal, Map<String, Integer> caps) {
        Map<String, Integer> out = new LinkedHashMap<>();
        int given = 0;
        for (Map.Entry<String, Double> e : ideal.entrySet()) {
            int cap = caps.getOrDefault(e.getKey(), 0);
            int part = Math.min(cap, (int) Math.floor(e.getValue() + 1e-9));
            out.put(e.getKey(), part);
            given += part;
        }
        List<String> order = new ArrayList<>(ideal.keySet());
        order.sort((a, b) -> Double.compare(frac(ideal.get(b)), frac(ideal.get(a))));
        for (int pass = 0; pass < 2 && given < amount; pass++) {
            for (String m : order) {
                if (given >= amount) {
                    break;
                }
                int room = caps.getOrDefault(m, 0) - out.get(m);
                int want = pass == 0 ? Math.min(1, room) : room;
                int add = Math.min(want, amount - given);
                if (add > 0) {
                    out.put(m, out.get(m) + add);
                    given += add;
                }
            }
        }
        out.values().removeIf(v -> v <= 0);
        return out;
    }

    private static double frac(double v) {
        return v - Math.floor(v + 1e-9);
    }

    private static void subtract(Map<String, Integer> mix, Map<String, Integer> parts) {
        parts.forEach((m, v) -> {
            int rest = mix.getOrDefault(m, 0) - v;
            if (rest <= 0) {
                mix.remove(m);
            } else {
                mix.put(m, rest);
            }
        });
    }

    /** Quantite lisible : « 4 lingots », « 4 lingots + 36 mB », « 36 mB ». */
    public static Component quantity(int mb) {
        int ingots = mb / INGOT, rest = mb - ingots * INGOT;
        if (ingots == 0) {
            return Component.translatable("gui.pioneers.bath.mb", rest);
        }
        Component n = Component.translatable(ingots == 1 ? "gui.pioneers.bath.ingot" : "gui.pioneers.bath.ingots", ingots);
        return rest == 0 ? n : Component.translatable("gui.pioneers.bath.ingots_mb", n, rest);
    }

    /** Ce qui coule (« bronze 4 lingots »), ou la raison quand rien ne coule (« attend de l'etain », « trop peu : bronze 64 mB »...). */
    public static Component pourWhat(Plan plan) {
        if (plan.pours()) {
            return Component.translatable("gui.pioneers.bath.what", name(plan.metal()), quantity(plan.mb()));
        }
        return switch (plan.reason()) {
            case WHY_EMPTY -> Component.translatable("gui.pioneers.bath.empty");
            case WHY_WAITING -> plan.waiting().isEmpty() ? Component.translatable("gui.pioneers.bath.nothing")
                  : Component.translatable("gui.pioneers.bath.waits", name(plan.waiting()));
            case WHY_BOUND -> Component.translatable("gui.pioneers.bath.bound");
            default -> plan.mb() > 0 && !plan.metal().isEmpty()
                  ? Component.translatable("gui.pioneers.bath.too_little", name(plan.metal()), quantity(plan.mb()))
                  : Component.translatable("gui.pioneers.bath.nothing");
        };
    }

    /** Ce qui restera (« cuivre 36 mB, etain 4 mB », ou « rien »). */
    public static Component restWhat(Plan plan) {
        if (plan.rest().isEmpty()) {
            return Component.translatable("gui.pioneers.bath.nothing_left");
        }
        MutableComponent list = Component.empty();
        for (Map.Entry<String, Integer> e : plan.rest().entrySet()) {
            if (!list.getSiblings().isEmpty()) {
                list.append(", ");
            }
            list.append(Component.translatable("gui.pioneers.bath.part", name(e.getKey()), e.getValue()));
        }
        return list;
    }

    /** Ligne « Coule : bronze 4 lingots », ou la raison quand rien ne coule. */
    public static Component pourLine(Plan plan) {
        return plan.pours() ? Component.translatable("gui.pioneers.bath.pours", pourWhat(plan)) : pourWhat(plan);
    }

    /** Ligne « Reste : cuivre 36 mB, etain 4 mB » (ou « Reste : rien »). */
    public static Component restLine(Plan plan) {
        return Component.translatable("gui.pioneers.bath.rest", restWhat(plan));
    }

    /** Une ligne : « Coule : bronze 4 lingots · reste : cuivre 36 mB » (barre d'action du creuset en argile, fiches). */
    public static Component planLine(Plan plan) {
        return Component.empty().append(pourLine(plan)).append(Component.literal(" · ")).append(restLine(plan));
    }

    /** Vrai si le melange n'est pas vide et n'est ni un metal pur ni un alliage : il ne coule pas. */
    public static boolean mixed(Map<String, Integer> mix) {
        return !mix.isEmpty() && resolve(mix).isEmpty();
    }

    /** Nom du metal ou de l'alliage, ou « Melange sans alliage » (texte vide). */
    public static Component nameOrMixed(String id) {
        return id.isEmpty() ? Component.translatable("metal.pioneers.mixed") : name(id);
    }

    /** Temperature a atteindre pour que le melange coule : celle du metal (ou alliage) obtenu, sinon celle du plus refractaire. */
    public static int pourC(Map<String, Integer> mix) {
        String result = resolve(mix);
        if (!result.isEmpty()) {
            return meltingC(result);
        }
        int max = 0;
        for (String metal : mix.keySet()) {
            max = Math.max(max, meltingC(metal));
        }
        return max;
    }
}
