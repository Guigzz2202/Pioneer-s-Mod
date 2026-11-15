package fr.pioneers.clan.core;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import javax.annotation.Nullable;

/**
 * 1.9 (MAJ RP, bloc 4) : definitions des clans lues dans {@code data/pioneers/clans/} : constitutions des regimes
 * ({@code regimes/*.json} : qui tranche chaque type de decision, majorites, plafonds, mandat, succession, contre-pouvoir, avantage), roles
 * proposes ({@code roles.json}), 25 permissions et reserves par regime ({@code permissions.json}), niveaux ({@code levels.json}) et regles
 * communes ({@code rules.json} : Charte, identite, membres, vote, changement de regime, tresor, horloge). Classe PURE (aucune classe de
 * Minecraft) : le banc {@code tools/rptest/ClanBench} la charge directement.
 */
public final class ClanDefs {

    /** Qui tranche une decision. */
    public enum Decider {
        REPRESENTATIVE, COUNCIL, MEMBERS;

        @Nullable
        public static Decider of(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Majorite : part des voix exprimees (oui / (oui + non)) ou part du corps electoral actif ({@link #UNANIMITY}, base « electorate »). */
    public enum Majority {
        SIMPLE(0.5, true), P60(0.6, false), TWO_THIRDS(2.0 / 3.0, false), THREE_QUARTERS(0.75, false), UNANIMITY(1.0, false);

        /** Seuil ; {@code strict} : il faut le depasser (majorite simple), sinon l'atteindre. */
        public final double share;
        public final boolean strict;

        Majority(double share, boolean strict) {
            this.share = share;
            this.strict = strict;
        }

        @Nullable
        public static Majority of(String s) {
            try {
                return valueOf(s.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException | NullPointerException e) {
                return null;
            }
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /** Une regle de decision : qui tranche, a quelle majorite, et sur quelle base (voix exprimees ou corps electoral actif). */
    public record Rule(Decider by, Majority majority, boolean electorateBase) {

        public boolean direct() {
            return by == Decider.REPRESENTATIVE;
        }
    }

    /** Contre-pouvoir : type (recall, revolution, no_confidence, none), regle, delai entre deux tentatives (jours), regime impose. */
    public record CounterPower(String type, @Nullable Rule rule, int cooldownDays, String imposes) {

        public boolean none() {
            return rule == null || "none".equals(type);
        }
    }

    /**
     * Constitution d'un regime. {@code repMode} : elected (elu par les membres), appointed (designe a vie, heritier), council (elu par le
     * Conseil), rotating (porte-parole tournant). {@code succession} : emergency_election, heir, council, rotation.
     */
    public record Constitution(String id, int order, String repMode, int termDays, boolean reelectable, int councilMin, int councilMax,
                               double councilPerMembers, int councilTermDays, Rule councilElection, Rule repElection,
                               Map<String, Rule> decisions, int activityTaxMax, int duesMax, String succession, CounterPower counter,
                               double claimBonus, boolean dividend, int federalContributionMax, double federalWeightCap) {

        @Nullable
        public Rule rule(String decision) {
            return decisions.get(decision);
        }

        public boolean hasCouncil() {
            return councilMax > 0;
        }

        /** Sieges du Conseil pour un effectif donne (entre min et max, jamais plus que l'effectif). */
        public int councilSeats(int members) {
            if (!hasCouncil()) {
                return 0;
            }
            int want = (int) Math.round(members * councilPerMembers);
            return Math.min(members, Math.max(councilMin, Math.min(councilMax, want)));
        }
    }

    /** Role propose a la creation : rang, couleur (teinture), plafond de retrait journalier (-1 = sans plafond), fixe (non supprimable). */
    public record RoleDef(String id, int rank, int color, long withdrawCap, boolean fixed, Set<String> perms) {
    }

    /** Permission : famille et regimes qui la reservent (seul le Representant l'exerce). */
    public record PermDef(String id, String family, String decision, Set<String> reservedBy) {
    }

    /** Niveau du clan : points requis, effectif maximal, emplacements de chunkloading. */
    public record LevelDef(int level, long points, int members, int chunkload) {
    }

    /** Regles communes (valeurs de depart de la specification, modifiables en donnees). */
    public record Rules(long charterCost, int charterSigners, int charterRadius, int charterMinutes, int nameMin, int nameMax, int tagMin,
                        int tagMax, int mottoMax, List<String> bannedWords, int inviteMinutes, int rejoinDays, int absenceDays, int dissolveDays,
                        int activeDays, int maxRoles, int voteHours, double quorum, int retryHours, int voteHistory, int voteTextMax,
                        long regimeCost, int regimeDelayDays, int regimeCooldownDays, double spendShare, int marketTaxMax, int marketRadius,
                        double alertShare, long alertMin, int maxJumpMinutes) {

        public long voteMs() {
            return voteHours * HOUR;
        }
    }

    public static final long MINUTE = 60_000L, HOUR = 3_600_000L, DAY = 86_400_000L;
    /** Les quatre regimes, dans l'ordre d'affichage. */
    public static final List<String> REGIMES = List.of("democracy", "empire", "council", "cooperative");
    /** Regime d'un clan solo : decide comme un Empire. */
    public static final String SOLO = "solo", EMPIRE = "empire", DEMOCRACY = "democracy", COUNCIL = "council", COOPERATIVE = "cooperative";
    /** Types de decision du tableau « qui tranche quoi » (et l'identite du clan). */
    public static final List<String> DECISIONS = List.of("taxes", "roles", "kick", "spend", "war", "treaty", "regime", "identity");

    public final Map<String, Constitution> regimes;
    public final Map<String, RoleDef> roles;
    public final Map<String, PermDef> perms;
    public final Map<Integer, LevelDef> levels;
    @Nullable
    public final Rules rules;

    private static volatile ClanDefs current = new ClanDefs(Map.of(), Map.of(), Map.of(), Map.of(), null);

    public ClanDefs(Map<String, Constitution> regimes, Map<String, RoleDef> roles, Map<String, PermDef> perms, Map<Integer, LevelDef> levels,
                    @Nullable Rules rules) {
        this.regimes = Collections.unmodifiableMap(new LinkedHashMap<>(regimes));
        this.roles = Collections.unmodifiableMap(new LinkedHashMap<>(roles));
        this.perms = Collections.unmodifiableMap(new LinkedHashMap<>(perms));
        this.levels = Collections.unmodifiableMap(new LinkedHashMap<>(levels));
        this.rules = rules;
    }

    public static ClanDefs get() {
        return current;
    }

    public static void set(ClanDefs defs) {
        current = defs;
    }

    /** Tout est-il charge (regimes, roles, regles) ? Sinon la fondation de clans est refusee avec un message clair. */
    public boolean ready() {
        return rules != null && !roles.isEmpty() && regimes.keySet().containsAll(REGIMES) && !levels.isEmpty();
    }

    /** Constitution d'un regime ; le clan solo decide comme un Empire. */
    @Nullable
    public Constitution regime(String id) {
        return regimes.get(SOLO.equals(id) ? EMPIRE : id);
    }

    @Nullable
    public LevelDef level(int level) {
        LevelDef d = levels.get(level);
        if (d == null && !levels.isEmpty()) {
            int max = levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(1);
            d = levels.get(Math.min(Math.max(1, level), max));
        }
        return d;
    }

    /** Niveau atteint pour un total de points (les niveaux ne baissent jamais : appelant). */
    public int levelFor(long points) {
        int best = 1;
        for (LevelDef d : levels.values()) {
            if (points >= d.points() && d.level() > best) {
                best = d.level();
            }
        }
        return best;
    }

    /** La permission est-elle reservee au Representant dans ce regime ? */
    public boolean reserved(String regime, String perm) {
        PermDef p = perms.get(perm);
        return p != null && p.reservedBy().contains(SOLO.equals(regime) ? EMPIRE : regime);
    }
}
