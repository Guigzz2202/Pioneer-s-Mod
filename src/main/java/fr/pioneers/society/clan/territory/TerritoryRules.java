package fr.pioneers.society.clan.territory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 1.9 (MAJ RP, bloc 4) : regles PURES du territoire (aucune classe du jeu, banc {@code tools/rptest/TerritoryBench}). Chunks en entiers
 * longs, meme codage que {@code ChunkPos.asLong} : x dans les 32 bits bas, z dans les 32 bits hauts.
 */
public final class TerritoryRules {

    private TerritoryRules() {
    }

    // ------------------------------------------------------------------ chunks
    public static long chunk(int x, int z) {
        return (long) x & 0xFFFFFFFFL | ((long) z & 0xFFFFFFFFL) << 32;
    }

    public static int cx(long chunk) {
        return (int) (chunk & 0xFFFFFFFFL);
    }

    public static int cz(long chunk) {
        return (int) (chunk >>> 32 & 0xFFFFFFFFL);
    }

    /** Les huit voisins (cotes et coins). */
    public static long[] around(long chunk) {
        int x = cx(chunk), z = cz(chunk);
        long[] out = new long[8];
        int i = 0;
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx != 0 || dz != 0) {
                    out[i++] = chunk(x + dx, z + dz);
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ budget
    /** Reglages du budget (configuration du serveur et donnees du territoire). */
    public record Budget(int base, int perMember, int perLevel, int cap, int soloCap, double cooperativeBonus) {
    }

    /**
     * Budget de claims : B = base + parMembre x actifs + parNiveau x niveau ; Cooperative +10 % (arrondi a l'inferieur) ; plafond. Le clan
     * solo compte un seul membre et a son propre plafond.
     */
    public static int budget(Budget b, int active, int level, boolean cooperative, boolean solo) {
        int members = solo ? 1 : Math.max(0, active);
        long raw = (long) b.base() + (long) b.perMember() * members + (long) b.perLevel() * Math.max(1, level);
        if (cooperative && !solo) {
            raw = (long) Math.floor(raw * (1 + b.cooperativeBonus()) + 1e-9);
        }
        return (int) Math.min(raw, solo ? b.soloCap() : b.cap());
    }

    /**
     * 1.0.3-beta (lot 16) : budget total = part gratuite ({@link #budget(Budget, int, int, boolean, boolean)}, bornee par {@code cap} /
     * {@code soloCap}) + claims achetes au tresor (jamais bornes par ces plafonds : ils ont leur propre plafond d'achat).
     */
    public static int budget(Budget b, int active, int level, boolean cooperative, boolean solo, int bought) {
        return (int) Math.min(Integer.MAX_VALUE, (long) budget(b, active, level, cooperative, solo) + Math.max(0, bought));
    }

    // ------------------------------------------------------------------ achat de claims (1.0.3-beta, lot 16)
    /**
     * Reglages de l'achat : prix d'un claim achete ({@code price}, Shining), plafonds d'achat d'un clan et d'un clan solo. 1.0.5-beta (lot 13,
     * Guillaume : « prix fixe 100 Shining ») : prix FIXE (avant : progression base x (1 + (k - 1) / pas)^2 du lot 16 de la 1.0.3).
     */
    public record Purchase(long price, int max, int soloMax) {
    }

    /** Prix du k-ieme claim achete (k = 1, 2...) : le prix fixe (jamais negatif). */
    public static long claimPrice(Purchase p, int k) {
        return Math.max(0, Math.min(p.price(), 1_000_000_000L));
    }

    /**
     * 1.0.5-beta (lot 13) : PUR, prix du n-ieme emplacement de chunkloading d'un clan (n = 1, 2...) : min(max, arrondi(base + coef x ln n)),
     * jamais negatif. Avec 50, 108, 500 : 50, 125, 169, 200, 224, 244 (1er a 6e), 275 (8e), 350 (16e), 425 (32e), 500 (64e et au-dela).
     */
    public static long chunkloadPrice(long base, double coef, long max, int n) {
        double raw = Math.max(0, base) + Math.max(0, coef) * Math.log(Math.max(1, n));
        return Math.max(0, Math.min(Math.max(0, max), Math.round(raw)));
    }

    /** Prix de {@code n} claims achetes a la suite, apres les {@code bought} deja achetes. */
    public static long claimsPrice(Purchase p, int bought, int n) {
        long sum = 0;
        for (int i = 1; i <= n; i++) {
            sum = Math.min(Long.MAX_VALUE / 2, sum + claimPrice(p, bought + i));
        }
        return sum;
    }

    /** Plafond d'achat (clan solo ou non). */
    public static int buyCap(Purchase p, boolean solo) {
        return Math.max(0, solo ? p.soloMax() : p.max());
    }

    /** Verdict d'un achat (ordre des controles : le premier refus l'emporte). */
    public enum BuyVerdict {
        OK, AMOUNT, CAP, TREASURY
    }

    /** Peut-on acheter {@code n} claims (1 a 64) apres {@code bought}, avec ce tresor ? */
    public static BuyVerdict canBuy(Purchase p, int bought, int n, boolean solo, long treasury) {
        if (n < 1 || n > 64) {
            return BuyVerdict.AMOUNT;
        }
        if (bought + n > buyCap(p, solo)) {
            return BuyVerdict.CAP;
        }
        return claimsPrice(p, bought, n) > treasury ? BuyVerdict.TREASURY : BuyVerdict.OK;
    }

    // ------------------------------------------------------------------ reclamer
    /** Verdict d'une demande de claim (ordre des controles : le premier refus l'emporte). */
    public enum Verdict {
        OK, OUTPOST, TAKEN, OWN, TOUCHES_OTHER, NEAR_SPAWN, NEAR_BELL, RUIN, BUDGET, NOT_ADJACENT, OUTPOST_SINGLE, NO_OUTPOST,
        /** 1.9.2 : chunk assiege ou sous scelles de guerre (zone : claim ou abandon refuse). */
        WAR
    }

    /** Ce qu'il faut savoir d'un chunk du clan pour les regles (avant-poste ou non). */
    public interface Owned {
        /** Le chunk appartient-il au clan, et est-ce un avant-poste ? null : pas au clan. */
        Boolean outpost(long chunk);
    }

    /**
     * Regle de pose d'un claim dans UNE dimension. {@code owner} lit l'index (identifiant du clan proprietaire, ou null) ; {@code mine} les
     * claims du clan dans cette dimension ; {@code clanHasAny} : le clan a deja un claim (toutes dimensions) ; {@code outposts} : nombre
     * d'avant-postes du clan ; {@code used}/{@code budget} : poids deja pris et budget.
     */
    public static Verdict canClaim(long chunk, Object clanId, java.util.function.LongFunction<Object> owner, Owned mine, boolean dimHasAny,
          int outposts, int maxOutposts, int outpostWeight, int used, int budget, boolean nearSpawn, boolean nearBell, boolean ruin) {
        Object here = owner.apply(chunk);
        if (here != null) {
            return here.equals(clanId) ? Verdict.OWN : Verdict.TAKEN;
        }
        boolean touchesMine = false, touchesOutpostOnly = false;
        for (long n : around(chunk)) {
            Object o = owner.apply(n);
            if (o == null) {
                continue;
            }
            if (!o.equals(clanId)) {
                return Verdict.TOUCHES_OTHER;
            }
            Boolean out = mine.outpost(n);
            if (out == null) {
                continue;
            }
            if (out) {
                touchesOutpostOnly = true;
            } else {
                touchesMine = true;
            }
        }
        if (nearSpawn) {
            return Verdict.NEAR_SPAWN;
        }
        if (nearBell) {
            return Verdict.NEAR_BELL;
        }
        if (ruin) {
            return Verdict.RUIN;
        }
        if (touchesMine || !dimHasAny) {
            //contigu au territoire, ou premier claim de la dimension (il fonde le territoire de cette dimension)
            return used + 1 > budget ? Verdict.BUDGET : Verdict.OK;
        }
        if (touchesOutpostOnly) {
            return Verdict.OUTPOST_SINGLE;
        }
        if (outposts >= maxOutposts) {
            return Verdict.NO_OUTPOST;
        }
        return used + outpostWeight > budget ? Verdict.BUDGET : Verdict.OUTPOST;
    }

    // ------------------------------------------------------------------ reclamer une zone (1.9.2)
    /** Taille maximale d'une zone tracee sur la carte (chunks du rectangle). */
    public static final int AREA_MAX = 256;

    /** Les chunks du rectangle (coins compris, dans n'importe quel ordre), ligne par ligne du nord-ouest au sud-est. */
    public static List<Long> rectangle(int x0, int z0, int x1, int z1) {
        int ax = Math.min(x0, x1), bx = Math.max(x0, x1), az = Math.min(z0, z1), bz = Math.max(z0, z1);
        List<Long> out = new ArrayList<>();
        for (int z = az; z <= bz; z++) {
            for (int x = ax; x <= bx; x++) {
                out.add(chunk(x, z));
            }
        }
        return out;
    }

    /**
     * 1.9.2 (retours de Guillaume : « drag and drop la zone que je souhaite claim ») : plan d'un claim en zone. Chaque chunk est juge par
     * {@link #canClaim} comme s'il etait reclame seul, mais en comptant ceux de la zone deja acceptes (ils deviennent du territoire : la
     * contiguite se propage depuis le territoire existant, et le budget se consomme chunk par chunk). On repasse sur la zone tant qu'un
     * chunk de plus est accepte (ordre stable : du nord-ouest au sud-est a chaque passe). Une zone ne cree jamais d'avant-poste : un chunk
     * qui n'en toucherait aucun autre du territoire est refuse ({@link Verdict#NOT_ADJACENT}) ; le premier chunk d'une dimension sans
     * territoire le fonde, comme un claim seul. {@code blocked} : refus propres au chunk (apparition, cloche, ruine, guerre), lus une fois.
     *
     * @return verdict de chaque chunk de {@code chunks} (dans le meme ordre) : {@link Verdict#OK} = accepte
     */
    public static Map<Long, Verdict> planArea(List<Long> chunks, Object clanId, java.util.function.LongFunction<Object> owner, Owned mine,
          boolean dimHasAny, int used, int budget, java.util.function.LongFunction<Verdict> blocked) {
        return planArea(chunks, clanId, owner, mine, dimHasAny, used, budget, blocked, false);
    }

    /**
     * 1.0.3-beta (Guillaume : « les claims de clan ne requierent plus d'etre voisins ») : {@code free} vrai = aucun chunk n'a besoin de
     * toucher le territoire (chacun compte 1, comme un claim contigu) ; seuls restent les refus du chunk lui-meme (pris, touche un autre
     * clan, apparition, cloche, ruine, guerre) et le budget.
     */
    public static Map<Long, Verdict> planArea(List<Long> chunks, Object clanId, java.util.function.LongFunction<Object> owner, Owned mine,
          boolean dimHasAny, int used, int budget, java.util.function.LongFunction<Verdict> blocked, boolean free) {
        Map<Long, Verdict> out = new java.util.LinkedHashMap<>();
        Set<Long> planned = new LinkedHashSet<>();
        Map<Long, Verdict> fixed = new java.util.HashMap<>();
        java.util.function.LongFunction<Object> owner2 = c -> planned.contains(c) ? clanId : owner.apply(c);
        Owned mine2 = c -> planned.contains(c) ? Boolean.FALSE : mine.outpost(c);
        List<Long> left = new ArrayList<>();
        for (long c : chunks) {
            if (!out.containsKey(c)) {
                out.put(c, null);
                left.add(c);
            }
        }
        boolean progress = true;
        while (progress && !left.isEmpty()) {
            progress = false;
            for (java.util.Iterator<Long> it = left.iterator(); it.hasNext(); ) {
                long c = it.next();
                Verdict v = judge(c, clanId, owner2, mine2, !free && (dimHasAny || !planned.isEmpty()), used + planned.size(), budget, blocked, fixed);
                if (v == Verdict.OK) {
                    planned.add(c);
                    out.put(c, Verdict.OK);
                    it.remove();
                    progress = true;
                }
            }
        }
        for (long c : left) {
            out.put(c, judge(c, clanId, owner2, mine2, !free && (dimHasAny || !planned.isEmpty()), used + planned.size(), budget, blocked, fixed));
        }
        return out;
    }

    private static Verdict judge(long c, Object clanId, java.util.function.LongFunction<Object> owner, Owned mine, boolean dimHasAny, int used,
          int budget, java.util.function.LongFunction<Verdict> blocked, Map<Long, Verdict> fixed) {
        //refus propres au chunk : lus une seule fois (cloche, ruine : recherches couteuses)
        Verdict b = fixed.computeIfAbsent(c, k -> {
            Verdict x = blocked.apply(k);
            return x == null ? Verdict.OK : x;
        });
        Verdict v = canClaim(c, clanId, owner, mine, dimHasAny, 0, 0, 1, used, budget, b == Verdict.NEAR_SPAWN, b == Verdict.NEAR_BELL,
              b == Verdict.RUIN);
        if (v == Verdict.OK && b != Verdict.OK) {
            //autre refus du chunk (guerre) : apres les controles de la regle
            return b;
        }
        //pas d'avant-poste en zone : un chunk isole est « non contigu »
        return v == Verdict.OUTPOST || v == Verdict.NO_OUTPOST || v == Verdict.OUTPOST_SINGLE ? Verdict.NOT_ADJACENT : v;
    }

    /** Resume d'un plan : chunks acceptes, et refus comptes par raison (ordre de l'enum). */
    public static Map<Verdict, Integer> tally(Map<Long, Verdict> plan) {
        Map<Verdict, Integer> out = new java.util.EnumMap<>(Verdict.class);
        for (Verdict v : plan.values()) {
            out.merge(v, 1, Integer::sum);
        }
        return out;
    }

    // ------------------------------------------------------------------ declin
    /** Un chunk tenu, pour le declin : cle ("dimension|chunk"), poids (2 pour un avant-poste), distance au centre du territoire. */
    public record Held(String key, int weight, double distance) {
    }

    /**
     * Chunks a mettre en declin quand le poids tenu depasse le budget : du plus exterieur (le plus loin du centre) vers l'interieur, juste
     * assez pour revenir sous le budget. Egalite : ordre stable par cle (determinisme).
     */
    public static Set<String> declining(Collection<Held> held, int budget) {
        int total = 0;
        for (Held h : held) {
            total += h.weight();
        }
        Set<String> out = new LinkedHashSet<>();
        if (total <= budget) {
            return out;
        }
        List<Held> order = new ArrayList<>(held);
        order.sort(Comparator.comparingDouble(Held::distance).reversed().thenComparing(Held::key));
        for (Held h : order) {
            if (total <= budget) {
                break;
            }
            out.add(h.key());
            total -= h.weight();
        }
        return out;
    }

    /** Distance (en chunks) d'un chunk a un centre ; un chunk d'une autre dimension que le centre passe avant tous les autres. */
    public static double distance(long chunk, boolean sameDimension, long center) {
        if (!sameDimension) {
            return 1e12;
        }
        double dx = cx(chunk) - cx(center), dz = cz(chunk) - cz(center);
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Centre d'un ensemble de chunks : celui qui est le plus proche du barycentre (egalite : le plus petit numero). */
    public static long center(Collection<Long> chunks) {
        if (chunks.isEmpty()) {
            return 0;
        }
        double sx = 0, sz = 0;
        for (long c : chunks) {
            sx += cx(c);
            sz += cz(c);
        }
        sx /= chunks.size();
        sz /= chunks.size();
        long best = 0;
        double bd = Double.MAX_VALUE;
        for (long c : chunks) {
            double dx = cx(c) - sx, dz = cz(c) - sz;
            double d = dx * dx + dz * dz;
            if (d < bd || d == bd && c < best) {
                bd = d;
                best = c;
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ chunkloading
    /** Emplacements de chunkloading : ceux du niveau (donnees), 6 au plus, 3 pour un Solitaire. */
    public static int chunkloadSlots(int fromLevel, int cap, boolean solo, int soloCap) {
        return Math.max(0, Math.min(fromLevel, solo ? Math.min(cap, soloCap) : cap));
    }

    /** Emplacements par defaut si les donnees de niveau manquent : 1 + niveau / 2. */
    public static int slotsFor(int level) {
        return 1 + Math.max(1, level) / 2;
    }

    // ------------------------------------------------------------------ points
    /** Points du jour pour le territoire et les fonctions, plafonnes. */
    public static long dailyPoints(int chunks, int perChunk, int chunkCap, int functions, int perFunction, int functionCap) {
        return Math.min((long) chunks * perChunk, chunkCap) + Math.min((long) functions * perFunction, functionCap);
    }

    /**
     * Points de production : jetons cumules du jour, 1 point par {@code per} jetons, au plus {@code cap} pour la journee. Renvoie les
     * points NOUVEAUX a donner (deja donnes {@code given}).
     */
    public static long productionPoints(long tokensToday, int per, long cap, long given) {
        long total = Math.min(cap, tokensToday / Math.max(1, per));
        return Math.max(0, total - given);
    }

    /** Fonctions designees par type (comptage depuis la table position -> fonction). */
    public static int count(Map<String, String> functions, String fn) {
        int n = 0;
        for (String f : functions.values()) {
            if (f.equals(fn)) {
                n++;
            }
        }
        return n;
    }
}
