package fr.pioneers.village;

import java.util.List;

/**
 * 1.9.1 (retours de Guillaume) : regles de l'hostilite des villages et des reparations, LOGIQUE PURE (aucune classe Minecraft : le banc
 * {@code tools/rptest/RepairBench} la teste). Les valeurs viennent de {@code data/pioneers/village/repairs.json} (ecrit par
 * {@code tools/gc/rp/village_data.py}) ; les constantes ci-dessous sont les valeurs par defaut si le fichier manque.
 * <p>
 * Trois manieres de racheter une faute, toutes des ACTES (le temps ne pardonne toujours rien) et toutes sous le plafond journalier des gains
 * de reputation ({@code ReputationApi}, +30 par peuple et par jour) : l'amende honorable au Tableau des besoins, l'emissaire Colporteur, les
 * taches de reparation. Elles rachetent une faute, elles ne font pas d'amis : un gain s'arrete a 0 (reputation du peuple comme affinite d'un
 * villageois). Elles ne creent ni ne rendent jamais de jetons ni d'objets : aucune boucle de gain possible.
 */
public final class RepairRules {

    private RepairRules() {
    }

    // ------------------------------------------------------------------ reglages
    /** Hostilite d'un village : fuite, distance, golems, treve du drapeau blanc. */
    public record Hostility(int fleeAffinity, int distanceAffinity, int golemVillageAverage, int golemCallAffinity, int fleeRange, int keepDistance,
                            int callRange, int hitWitnessRange, int callSeconds, int truceBoardRange, int truceBreakDays, int messageSeconds) {
    }

    /** Amende honorable : cout en jetons (base + profondeur^2 / diviseur, multiplie par le rang du jour), en marchandises (x facteur). */
    public record Amends(int baseTokens, int depthDivisor, double goodsFactor, int goodsMax, int perDay, int peopleGain, int villagerGain) {
    }

    /** Emissaire Colporteur : cout de l'amende x commission, effet apres quelques jours, un envoi par jour. */
    public record Emissary(double feeFactor, int days, int peopleGain, int perDay, int reach) {
    }

    /** Une sorte de tache : gains (peuple, villageois), delai, parametre (quantite x facteur, morts, parcelles, presence). */
    public record Quest(String id, int peopleGain, int villagerGain, int days, double factor, int count, double presence) {
    }

    public record Settings(Hostility hostility, Amends amends, Emissary emissary, List<Quest> quests, int villageFactor, int windowDays, int maxDepth) {

        public Quest quest(String id) {
            for (Quest q : quests) {
                if (q.id().equals(id)) {
                    return q;
                }
            }
            return null;
        }
    }

    // valeurs par defaut (les memes que repairs.json)
    public static final int FLEE_AFFINITY = -60, DISTANCE_AFFINITY = -20, GOLEM_VILLAGE_AVERAGE = -40, GOLEM_CALL_AFFINITY = -60;
    public static final int FLEE_RANGE = 12, KEEP_DISTANCE = 4, CALL_RANGE = 24, HIT_WITNESS_RANGE = 16, CALL_SECONDS = 30;
    public static final int TRUCE_BOARD_RANGE = 24, TRUCE_BREAK_DAYS = 1, MESSAGE_SECONDS = 30;
    public static final int AMENDS_BASE = 50, AMENDS_DIVISOR = 40, AMENDS_GOODS_MAX = 256, AMENDS_PER_DAY = 2, AMENDS_PEOPLE = 10, AMENDS_VILLAGER = 5;
    public static final double AMENDS_GOODS_FACTOR = 1.5, EMISSARY_FEE = 1.5;
    public static final int EMISSARY_DAYS = 2, EMISSARY_PEOPLE = 15, EMISSARY_PER_DAY = 1, EMISSARY_REACH = 6;
    public static final int VILLAGE_FACTOR = 3, WINDOW_DAYS = 3, MAX_DEPTH = 300;

    public static final Settings DEFAULTS = new Settings(
          new Hostility(FLEE_AFFINITY, DISTANCE_AFFINITY, GOLEM_VILLAGE_AVERAGE, GOLEM_CALL_AFFINITY, FLEE_RANGE, KEEP_DISTANCE, CALL_RANGE,
                HIT_WITNESS_RANGE, CALL_SECONDS, TRUCE_BOARD_RANGE, TRUCE_BREAK_DAYS, MESSAGE_SECONDS),
          new Amends(AMENDS_BASE, AMENDS_DIVISOR, AMENDS_GOODS_FACTOR, AMENDS_GOODS_MAX, AMENDS_PER_DAY, AMENDS_PEOPLE, AMENDS_VILLAGER),
          new Emissary(EMISSARY_FEE, EMISSARY_DAYS, EMISSARY_PEOPLE, EMISSARY_PER_DAY, EMISSARY_REACH),
          List.of(new Quest("deliver", 12, 6, 2, 2.5, 0, 0), new Quest("watch", 15, 8, 2, 0, 3, 0.7), new Quest("replant", 10, 6, 2, 0, 6, 0),
                new Quest("raid", 25, 10, 5, 0, 0, 0)),
          VILLAGE_FACTOR, WINDOW_DAYS, MAX_DEPTH);

    private static volatile Settings current = DEFAULTS;

    public static Settings get() {
        return current;
    }

    public static void set(Settings s) {
        current = s == null ? DEFAULTS : s;
    }

    // ------------------------------------------------------------------ calculs
    /**
     * Profondeur de la haine (0..300) : la plus forte de la reputation du peuple (en negatif) et de la moyenne du village (x facteur, une
     * moyenne de -100 vaut une reputation de -300).
     */
    public static int depth(Settings s, int peopleRep, int villageAverage) {
        int d = Math.max(-peopleRep, -s.villageFactor() * villageAverage);
        return Math.max(0, Math.min(s.maxDepth(), d));
    }

    /** Il y a quelque chose a racheter : le peuple ou le village en veut au joueur. */
    public static boolean needed(int peopleRep, int villageAverage) {
        return peopleRep < 0 || villageAverage < 0;
    }

    /** Cout d'une amende en jetons : base + profondeur^2 / diviseur, multiplie par (1 + amendes deja payees ce jour a ce peuple). */
    public static long amendsCost(Settings s, int depth, int doneToday) {
        if (depth <= 0) {
            return 0;
        }
        long base = s.amends().baseTokens() + (long) depth * depth / Math.max(1, s.amends().depthDivisor());
        return base * (1 + Math.max(0, doneToday));
    }

    /** Cout d'un emissaire : l'offrande (amende du jour, rang 0) plus la commission du Colporteur. */
    public static long emissaryCost(Settings s, int depth) {
        return (long) Math.ceil(amendsCost(s, depth, 0) * s.emissary().feeFactor());
    }

    /**
     * Marchandises d'une amende : quantite d'un objet de valeur {@code centi} (centiemes de jeton) qui vaut le cout en jetons x facteur.
     * -1 si l'objet ne vaut rien ou s'il en faudrait plus que le plafond (on en choisit alors un autre).
     */
    public static int goodsCount(Settings s, long tokens, long centi) {
        if (centi <= 0 || tokens <= 0) {
            return -1;
        }
        long want = (long) Math.ceil(tokens * s.amends().goodsFactor() * 100.0 / centi);
        return want > s.amends().goodsMax() ? -1 : (int) Math.max(1, want);
    }

    /** Quantite a livrer pour une tache « livrer » : valeur de l'amende x facteur de la tache, bornee comme une amende. */
    public static int deliverCount(Settings s, int depth, long centi) {
        Quest q = s.quest("deliver");
        if (q == null || centi <= 0 || depth <= 0) {
            return -1;
        }
        long want = (long) Math.ceil(amendsCost(s, depth, 0) * q.factor() * 100.0 / centi);
        return want > s.amends().goodsMax() * 2L ? -1 : (int) Math.max(1, want);
    }

    /** Gain de reputation d'un rachat : jamais au-dessus de 0. */
    public static int peopleGain(int gain, int rep) {
        return Math.max(0, Math.min(gain, -rep));
    }

    /** Gain d'affinite d'un villageois : jamais au-dessus de 0. */
    public static int villagerGain(int gain, int affinity) {
        return Math.max(0, Math.min(gain, -affinity));
    }

    /** Ce qui empeche un rachat (ou NONE). */
    public enum Refusal {
        NONE, NOTHING, DAILY, TOKENS, GOODS, PEDDLERS_HOSTILE, PEDDLERS_SELF, IN_FLIGHT, QUEST_BUSY, QUEST_DONE, TOO_FAR
    }

    /** Amende honorable possible ? */
    public static Refusal amends(Settings s, int depth, int doneToday, boolean canPay) {
        if (depth <= 0) {
            return Refusal.NOTHING;
        }
        if (doneToday >= s.amends().perDay()) {
            return Refusal.DAILY;
        }
        return canPay ? Refusal.NONE : Refusal.TOKENS;
    }

    /**
     * Emissaire possible ? Refuse si les Colporteurs sont eux-memes Hostiles (ou t'ont banni), pour se reconcilier avec les Colporteurs, s'il
     * y en a deja un en route vers ce peuple, au-dela de l'envoi du jour, ou sans les jetons.
     */
    public static Refusal emissary(Settings s, boolean peddlersHostile, boolean toPeddlers, int depth, boolean inFlight, int sentToday, boolean canPay) {
        if (peddlersHostile) {
            return Refusal.PEDDLERS_HOSTILE;
        }
        if (toPeddlers) {
            return Refusal.PEDDLERS_SELF;
        }
        if (depth <= 0) {
            return Refusal.NOTHING;
        }
        if (inFlight) {
            return Refusal.IN_FLIGHT;
        }
        if (sentToday >= s.emissary().perDay()) {
            return Refusal.DAILY;
        }
        return canPay ? Refusal.NONE : Refusal.TOKENS;
    }

    /** Accepter une tache : une seule a la fois ; une sorte deja faite dans ce village pendant la fenetre ne revient pas. */
    public static Refusal accept(int depth, boolean busy, boolean doneThisWindow) {
        if (depth <= 0) {
            return Refusal.NOTHING;
        }
        if (busy) {
            return Refusal.QUEST_BUSY;
        }
        return doneThisWindow ? Refusal.QUEST_DONE : Refusal.NONE;
    }

    /** Fenetre de tirage des taches (jours de jeu). */
    public static long window(Settings s, long day) {
        return Math.floorDiv(day, Math.max(1, s.windowDays()));
    }

    /**
     * Tache « d'action » proposee par un village pendant une fenetre : veille, champs ou raid, tiree de facon reproductible (village,
     * fenetre). {@code canReplant} : le village a assez de parcelles vides.
     */
    public static String actionQuest(long village, long window, boolean canReplant) {
        long h = village * 0x9E3779B97F4A7C15L + window * 0xC2B2AE3D27D4EB4FL;
        h ^= h >>> 31;
        int roll = (int) Math.floorMod(h, 10);
        //veille 5 sur 10, champs 3 sur 10 (veille si pas de parcelles), raid 2 sur 10
        if (roll < 5) {
            return "watch";
        }
        if (roll < 8) {
            return canReplant ? "replant" : "watch";
        }
        return "raid";
    }

    /** Veille reussie : presence suffisante pendant la nuit et assez de monstres tues pres de la place. */
    public static boolean watchDone(Settings s, int present, int samples, int kills) {
        Quest q = s.quest("watch");
        if (q == null || samples <= 0) {
            return false;
        }
        return present >= Math.ceil(samples * q.presence()) && kills >= q.count();
    }

    /** Golems agressifs envers ce joueur (sans compter un appel de temoin) : moyenne du village trop basse ou peuple Hostile. */
    public static boolean golemsHostile(Settings s, int villageAverage, boolean peopleHostile) {
        return peopleHostile || villageAverage <= s.hostility().golemVillageAverage();
    }

    /** Comportement d'un villageois envers ce joueur : 2 fuite, 1 garde ses distances, 0 rien. */
    public static int fear(Settings s, int affinity, boolean peopleHostile) {
        if (peopleHostile || affinity <= s.hostility().fleeAffinity()) {
            return 2;
        }
        return affinity <= s.hostility().distanceAffinity() ? 1 : 0;
    }
}
