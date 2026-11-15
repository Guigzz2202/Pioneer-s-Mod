package fr.pioneers.society.clan.faction;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.core.ClanDefs.Majority;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;

/**
 * 1.9 (MAJ RP, bloc 4) : regles PURES des factions et des peuples de joueurs, lues dans {@code data/pioneers/clans/federal.json} (valeurs de
 * depart de la specification, hypotheses 16 a 21). Aucune classe du serveur : le banc {@code tools/rptest/FactionBench} l'exerce
 * directement. Delais reels en millisecondes ; ralliement en jours de jeu.
 */
public final class FactionRules {

    public static final long HOUR = ClanDefs.HOUR, DAY = ClanDefs.DAY;

    /** Une des huit teintes reservees aux peuples de joueurs : identifiant, couleur RGB, teinture des bannieres. */
    public record Tint(String id, int rgb, String dye) {
    }

    /** Valeurs (toutes modifiables en donnees). */
    public record Values(long pactCost, int pactMinClanLevel, long pactExpireMs, int tagMin, int tagMax, int minClans, int maxClans,
                         long leaveNoticeMs, long rejoinMs, long[] levels, int zonesPerLevel, int chunkloadBase, long warCost, long termMs,
                         long rotationMs, long regimeCooldownMs, Majority recallMajority, Map<String, String> decisions, int secessionLevel,
                         long secessionAgeMs, int secessionClans, int secessionActive, long secessionCost, int schism, String schismPeople,
                         Map<String, Majority> secessionMajority, long doctrineCooldownMs, int nameMin, int nameMax, int mottoMax, int saluteMax,
                         List<Tint> tints, Map<String, String> doctrines, List<String> reservedNames, int rallyRadius, int rallyReputation,
                         String rallyTier, long rallyCost, int rallyDays, int rallyInterval, int rallyMax, int rallyPenalty, int declineClans,
                         int declineActive, long declineMs, long nameReleaseMs) {

        @Nullable
        public Tint tint(String id) {
            for (Tint t : tints) {
                if (t.id().equals(id)) {
                    return t;
                }
            }
            return null;
        }
    }

    /** Ligne du tableau « qui tranche quoi » d'un type de decision federale ({@code admit} -&gt; {@code kick}...). */
    public static final List<String> FEDERAL = List.of("admit", "exclude", "contribution", "spend", "war", "peace", "treaty", "workers", "regime",
          "rally");

    public static final Values DEFAULTS = new Values(3000, 2, 7 * DAY, 3, 5, 2, 8, 24 * HOUR, 7 * DAY, new long[]{0, 5000, 15000, 40000, 100000},
          20, 1, 500, 14 * DAY, 14 * DAY, 30 * DAY, Majority.THREE_QUARTERS, defaultDecisions(), 3, 30 * DAY, 3, 24, 20000, -60, "restorers",
          defaultSecession(), 60 * DAY, 3, 24, 60, 60, defaultTints(), defaultDoctrines(), List.of("restaurateurs", "restorers", "mains nues",
          "bare hands", "colporteurs", "peddlers"), 200, 60, "esteemed", 5000, 3, 7, 6, -40, 2, 12, 14 * DAY, 30 * DAY);

    private static volatile Values current = DEFAULTS;

    private FactionRules() {
    }

    public static Values get() {
        return current;
    }

    public static void set(@Nullable Values v) {
        current = v == null ? DEFAULTS : v;
    }

    private static Map<String, String> defaultDecisions() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("admit", "kick");
        m.put("exclude", "kick");
        m.put("contribution", "taxes");
        m.put("spend", "spend");
        m.put("war", "war");
        m.put("peace", "treaty");
        m.put("treaty", "treaty");
        m.put("workers", "spend");
        m.put("regime", "regime");
        m.put("rally", "spend");
        return m;
    }

    private static Map<String, Majority> defaultSecession() {
        Map<String, Majority> m = new LinkedHashMap<>();
        m.put("democracy", Majority.THREE_QUARTERS);
        m.put("empire", null);
        m.put("council", Majority.THREE_QUARTERS);
        m.put("cooperative", Majority.UNANIMITY);
        return m;
    }

    private static List<Tint> defaultTints() {
        return List.of(new Tint("ochre", 0xB8812C, "orange"), new Tint("purple", 0x7A2A6A, "purple"), new Tint("cobalt", 0x2E5BD0, "light_blue"),
              new Tint("amber", 0xF0B229, "yellow"), new Tint("sage", 0x8EA67A, "green"), new Tint("rust", 0x9E4127, "brown"),
              new Tint("ivory", 0xEAE3CC, "white"), new Tint("lampblack", 0x2B2927, "black"));
    }

    private static Map<String, String> defaultDoctrines() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("lettered", "restorers");
        m.put("earthbound", "bare_hands");
        m.put("mercantile", "peddlers");
        return m;
    }

    // ------------------------------------------------------------------ lecture du fichier
    /** Lit {@code federal.json} ; toute valeur absente garde sa valeur de depart. Une valeur hors bornes leve une erreur au message clair. */
    public static Values parse(JsonObject o) {
        Values d = DEFAULTS;
        JsonObject pact = obj(o, "pact"), id = obj(o, "identity"), comp = obj(o, "composition"), gov = obj(o, "government"), sec = obj(o, "secession"),
              ppl = obj(o, "people"), rally = obj(o, "rally"), dec = obj(o, "decline");
        long[] levels = d.levels();
        if (o.has("levels")) {
            JsonArray a = o.getAsJsonArray("levels");
            levels = new long[a.size()];
            for (int i = 0; i < a.size(); i++) {
                levels[i] = a.get(i).getAsLong();
                if (i == 0 && levels[i] != 0 || i > 0 && levels[i] <= levels[i - 1]) {
                    throw new IllegalArgumentException("levels : seuils croissants depuis 0 attendus");
                }
            }
        }
        Map<String, String> decisions = new LinkedHashMap<>(d.decisions());
        if (gov.has("decisions")) {
            for (Map.Entry<String, JsonElement> e : gov.getAsJsonObject("decisions").entrySet()) {
                String row = e.getValue().getAsString();
                if (!ClanDefs.DECISIONS.contains(row)) {
                    throw new IllegalArgumentException("government.decisions." + e.getKey() + " : ligne inconnue " + row);
                }
                decisions.put(e.getKey(), row);
            }
        }
        Map<String, Majority> secMaj = new LinkedHashMap<>(d.secessionMajority());
        if (sec.has("majority")) {
            for (Map.Entry<String, JsonElement> e : sec.getAsJsonObject("majority").entrySet()) {
                String m = e.getValue().getAsString();
                secMaj.put(e.getKey(), "representative".equals(m) ? null : majority(m, "secession.majority." + e.getKey()));
            }
        }
        List<Tint> tints = d.tints();
        if (ppl.has("tints")) {
            tints = new ArrayList<>();
            for (JsonElement e : ppl.getAsJsonArray("tints")) {
                JsonObject t = e.getAsJsonObject();
                tints.add(new Tint(t.get("id").getAsString(), Integer.parseInt(t.get("rgb").getAsString(), 16), t.get("dye").getAsString()));
            }
        }
        Map<String, String> doctrines = d.doctrines();
        if (ppl.has("doctrines")) {
            doctrines = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> e : ppl.getAsJsonObject("doctrines").entrySet()) {
                doctrines.put(e.getKey(), e.getValue().getAsString());
            }
        }
        List<String> reserved = d.reservedNames();
        if (ppl.has("reserved_names")) {
            reserved = new ArrayList<>();
            for (JsonElement e : ppl.getAsJsonArray("reserved_names")) {
                reserved.add(fold(e.getAsString()));
            }
        }
        Values v = new Values(lng(pact, "cost", d.pactCost()), num(pact, "min_clan_level", d.pactMinClanLevel()),
              days(pact, "expire_days", d.pactExpireMs()), num(id, "tag_min", d.tagMin()), num(id, "tag_max", d.tagMax()),
              num(comp, "min_clans", d.minClans()), num(comp, "max_clans", d.maxClans()), hours(comp, "leave_notice_hours", d.leaveNoticeMs()),
              days(comp, "rejoin_days", d.rejoinMs()), levels, num(o, "zones_per_level", d.zonesPerLevel()),
              num(o, "chunkload_base", d.chunkloadBase()), lng(o, "war_cost", d.warCost()), days(gov, "term_days", d.termMs()),
              days(gov, "rotation_days", d.rotationMs()), days(gov, "regime_cooldown_days", d.regimeCooldownMs()),
              gov.has("recall_majority") ? majority(gov.get("recall_majority").getAsString(), "government.recall_majority") : d.recallMajority(),
              decisions, num(sec, "min_level", d.secessionLevel()), days(sec, "min_age_days", d.secessionAgeMs()),
              num(sec, "min_clans", d.secessionClans()), num(sec, "min_active", d.secessionActive()), lng(sec, "cost", d.secessionCost()),
              num(sec, "schism", d.schism()), str(sec, "schism_people", d.schismPeople()), secMaj,
              days(ppl, "doctrine_cooldown_days", d.doctrineCooldownMs()), num(ppl, "name_min", d.nameMin()), num(ppl, "name_max", d.nameMax()),
              num(ppl, "motto_max", d.mottoMax()), num(ppl, "salute_max", d.saluteMax()), tints, doctrines, reserved,
              num(rally, "radius", d.rallyRadius()), num(rally, "min_reputation", d.rallyReputation()), str(rally, "origin_tier", d.rallyTier()),
              lng(rally, "cost", d.rallyCost()), num(rally, "days", d.rallyDays()), num(rally, "interval_days", d.rallyInterval()),
              num(rally, "max", d.rallyMax()), num(rally, "origin_penalty", d.rallyPenalty()), num(dec, "min_clans", d.declineClans()),
              num(dec, "min_active", d.declineActive()), days(dec, "days", d.declineMs()), days(dec, "name_release_days", d.nameReleaseMs()));
        if (v.minClans() < 2 || v.maxClans() < v.minClans() || v.tagMin() < 1 || v.tagMax() < v.tagMin() || v.tints().isEmpty()) {
            throw new IllegalArgumentException("composition ou identite hors bornes");
        }
        return v;
    }

    private static Majority majority(String s, String where) {
        Majority m = Majority.of(s);
        if (m == null) {
            throw new IllegalArgumentException(where + " : majorite inconnue " + s);
        }
        return m;
    }

    private static JsonObject obj(JsonObject o, String k) {
        return o.has(k) && o.get(k).isJsonObject() ? o.getAsJsonObject(k) : new JsonObject();
    }

    private static int num(JsonObject o, String k, int d) {
        return o.has(k) ? o.get(k).getAsInt() : d;
    }

    private static long lng(JsonObject o, String k, long d) {
        return o.has(k) ? o.get(k).getAsLong() : d;
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) ? o.get(k).getAsString() : d;
    }

    private static long days(JsonObject o, String k, long d) {
        return o.has(k) ? Math.round(o.get(k).getAsDouble() * DAY) : d;
    }

    private static long hours(JsonObject o, String k, long d) {
        return o.has(k) ? Math.round(o.get(k).getAsDouble() * HOUR) : d;
    }

    // ------------------------------------------------------------------ niveau, budgets
    /** Niveau de faction (1..5) aux points cumules des clans. */
    public static int level(Values v, long points) {
        int best = 1;
        for (int i = 0; i < v.levels().length; i++) {
            if (points >= v.levels()[i]) {
                best = i + 1;
            }
        }
        return best;
    }

    public static int maxLevel(Values v) {
        return v.levels().length;
    }

    /** Budget de zones federales : 20 chunks par niveau. */
    public static int zoneBudget(Values v, int level) {
        return v.zonesPerLevel() * Math.max(1, level);
    }

    /** Emplacements de chunkloading federaux : 1 + niveau / 2. */
    public static int chunkloadSlots(Values v, int level) {
        return v.chunkloadBase() + Math.max(1, level) / 2;
    }

    /** Contribution federale demandee, bornee au plafond du regime federal ({@code -1} si hors bornes). */
    public static int contribution(int wanted, int cap) {
        return wanted < 0 || wanted > Math.max(0, cap) ? -1 : wanted;
    }

    /** Part due par un clan : pourcentage de ses recettes, jamais plus que son tresor (jamais de dette). */
    public static long contributionDue(long income, int percent, long treasury) {
        if (income <= 0 || percent <= 0) {
            return 0;
        }
        return Math.max(0, Math.min(treasury, income * Math.min(100, percent) / 100));
    }

    // ------------------------------------------------------------------ composition
    /** Un clan peut-il entrer dans une faction (cle de refus, ou {@code null}) ? Un clan n'est que dans une faction. */
    @Nullable
    public static String canEnter(Values v, Clan c, @Nullable Faction f, long now) {
        if (c.solo || c.members.size() < 2) {
            return "clan.pioneers.faction.error.solo";
        }
        if (c.faction != null) {
            return "clan.pioneers.faction.error.already";
        }
        if (c.factionCooldown > now) {
            return "clan.pioneers.faction.error.cooldown";
        }
        if (f != null && f.clans.size() >= v.maxClans()) {
            return "clan.pioneers.faction.error.full";
        }
        return null;
    }

    /** Inscrit le clan (deja verifie). */
    public static void join(Faction f, Clan c) {
        if (!f.clans.contains(c.id)) {
            f.clans.add(c.id);
        }
        c.faction = f.id;
    }

    /** Retire le clan : 7 jours avant une autre faction. */
    public static void part(Values v, Faction f, Clan c, long now) {
        f.clans.remove(c.id);
        if (f.id.equals(c.faction)) {
            c.faction = null;
            c.factionCooldown = now + v.rejoinMs();
        }
    }

    // ------------------------------------------------------------------ gouvernement federal
    /** Qui vote une decision federale : le Representant federal seul, tous les membres, ou les Delegues (une voix par clan). */
    public enum Voters {
        REPRESENTATIVE, MEMBERS, DELEGATES
    }

    /** Regle federale : qui vote, a quelle majorite, sur quelle base. */
    public record FedRule(Voters voters, Majority majority, boolean electorateBase) {
    }

    /**
     * Regle d'une decision federale. Democratie : tous les membres des clans votent (un joueur une voix, aucun clan au-dessus du plafond de
     * poids), a la majorite de la ligne du tableau ; Empire : le Representant federal decide ; Conseil : les Delegues votent (une voix par
     * clan) ; Cooperative : les Delegues, a la majorite de la Cooperative (deux tiers, unanimite pour le regime). Secession et doctrine : la
     * majorite la plus forte du regime.
     */
    @Nullable
    public static FedRule rule(Values v, ClanDefs defs, String regime, String decision) {
        ClanDefs.Constitution k = defs.regime(regime);
        if (k == null) {
            return null;
        }
        Voters voters = switch (regime) {
            case ClanDefs.EMPIRE -> Voters.REPRESENTATIVE;
            case ClanDefs.DEMOCRACY -> Voters.MEMBERS;
            default -> Voters.DELEGATES;
        };
        if ("secession".equals(decision) || "doctrine".equals(decision)) {
            Majority m = v.secessionMajority().get(regime);
            return m == null ? new FedRule(Voters.REPRESENTATIVE, Majority.SIMPLE, false) : new FedRule(voters == Voters.REPRESENTATIVE
                  ? Voters.DELEGATES : voters, m, true);
        }
        String row = v.decisions().get(decision);
        ClanDefs.Rule r = row == null ? null : k.rule(row);
        if (r == null) {
            return null;
        }
        if (voters == Voters.REPRESENTATIVE) {
            return new FedRule(Voters.REPRESENTATIVE, Majority.SIMPLE, false);
        }
        return new FedRule(voters, r.majority(), r.electorateBase());
    }

    /**
     * Corps electoral d'un vote federal : electeur -&gt; groupe (identifiant du clan). Membres : tous les membres des clans ; Delegues : les
     * Representants. {@code clans} : les clans de la faction.
     */
    public static Map<UUID, String> electorate(Collection<Clan> clans, Voters voters) {
        Map<UUID, String> out = new LinkedHashMap<>();
        for (Clan c : clans) {
            String g = c.id.toString();
            if (voters == Voters.MEMBERS) {
                c.members.keySet().forEach(u -> out.put(u, g));
            } else if (c.representative != null) {
                out.put(c.representative, g);
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ secession
    /** Ce qu'il faut savoir pour juger une secession. */
    public record Secession(int level, long ageMs, int clans, int activeMembers, long treasury, int playerPeoples, int maxPeoples,
                            boolean alreadyPeople, boolean economy) {
    }

    /** Premiere condition de secession non remplie (cle de langue), ou {@code null}. */
    @Nullable
    public static String secession(Values v, Secession s) {
        if (s.alreadyPeople()) {
            return "clan.pioneers.people.error.already";
        }
        if (s.level() < v.secessionLevel()) {
            return "clan.pioneers.people.error.level";
        }
        if (s.ageMs() < v.secessionAgeMs()) {
            return "clan.pioneers.people.error.age";
        }
        if (s.clans() < v.secessionClans()) {
            return "clan.pioneers.people.error.clans";
        }
        if (s.activeMembers() < v.secessionActive()) {
            return "clan.pioneers.people.error.active";
        }
        if (s.playerPeoples() >= s.maxPeoples()) {
            return "clan.pioneers.people.error.max";
        }
        if (s.economy() && s.treasury() < v.secessionCost()) {
            return "clan.pioneers.people.error.cost";
        }
        return null;
    }

    /** Nom d'un peuple : longueur, caracteres, filtre, unicite (noms des peuples existants et noms encore reserves). */
    @Nullable
    public static String peopleName(Values v, String name, List<String> banned, Collection<String> taken) {
        int n = name.codePointCount(0, name.length());
        if (n < v.nameMin() || n > v.nameMax()) {
            return "clan.pioneers.people.error.name_length";
        }
        for (int i = 0; i < name.length(); ) {
            int c = name.codePointAt(i);
            if (!(Character.isLetterOrDigit(c) || c == ' ' || c == '\'' || c == '’' || c == '-')) {
                return "clan.pioneers.people.error.name_chars";
            }
            i += Character.charCount(c);
        }
        if (!Character.isLetter(name.codePointAt(0))) {
            return "clan.pioneers.people.error.name_chars";
        }
        String f = fold(name);
        for (String b : banned) {
            String fb = fold(b);
            if (!fb.isEmpty() && (f.contains(fb) || f.replace(" ", "").contains(fb.replace(" ", "")))) {
                return "clan.pioneers.error.name_banned";
            }
        }
        if (v.reservedNames().contains(f)) {
            return "clan.pioneers.people.error.name_taken";
        }
        for (String t : taken) {
            if (fold(t).equals(f)) {
                return "clan.pioneers.people.error.name_taken";
            }
        }
        return null;
    }

    /** Texte replie : sans accents, minuscules, espaces simples. */
    public static String fold(String s) {
        return s == null ? "" : Normalizer.normalize(s, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT).trim()
              .replaceAll("\\s+", " ").replace('’', '\'');
    }

    /** La doctrine peut-elle changer (une fois par 60 jours) ? */
    public static boolean doctrineChangeAllowed(Values v, long lastChange, long now) {
        return lastChange <= 0 || now - lastChange >= v.doctrineCooldownMs();
    }

    // ------------------------------------------------------------------ ralliement
    /** Ce qu'il faut savoir pour juger un ralliement. */
    public record Rally(boolean people, boolean villagerPeople, double distance, int reputation, String originTier, long treasury, boolean inProgress,
                        long daysSinceLast, int rallied, boolean economy) {
    }

    /** Ordre des paliers de reputation (du plus bas au plus haut). */
    public static final List<String> TIERS = List.of("hostile", "wary", "unknown", "esteemed", "ally");

    /** Premiere condition de ralliement non remplie (cle de langue), ou {@code null}. */
    @Nullable
    public static String rally(Values v, Rally r) {
        if (!r.people()) {
            return "clan.pioneers.rally.error.not_people";
        }
        if (!r.villagerPeople()) {
            return "clan.pioneers.rally.error.village";
        }
        if (r.inProgress()) {
            return "clan.pioneers.rally.error.in_progress";
        }
        if (r.rallied() >= v.rallyMax()) {
            return "clan.pioneers.rally.error.max";
        }
        if (r.daysSinceLast() >= 0 && r.daysSinceLast() < v.rallyInterval()) {
            return "clan.pioneers.rally.error.interval";
        }
        if (r.distance() > v.rallyRadius()) {
            return "clan.pioneers.rally.error.distance";
        }
        if (r.reputation() < v.rallyReputation()) {
            return "clan.pioneers.rally.error.reputation";
        }
        if (TIERS.indexOf(r.originTier()) < TIERS.indexOf(v.rallyTier())) {
            return "clan.pioneers.rally.error.origin";
        }
        if (r.economy() && r.treasury() < v.rallyCost()) {
            return "clan.pioneers.rally.error.cost";
        }
        return null;
    }

    /**
     * Ralliement : le village passe au peuple de joueurs, son peuple d'origine est garde pour un retour. Renvoie {peuple, peuple d'avant}.
     * Seuls ces deux champs changent : noms, metiers et affinites du registre ne sont pas touches.
     */
    public static String[] rallyPeople(String current, String player) {
        return new String[]{player, current};
    }

    /**
     * Retour (dissolution du peuple de joueurs) : un village de ce peuple revient a son peuple d'origine (a {@code fallback} si l'origine est
     * perdue) ; un village qui n'est plus a ce peuple ne bouge pas. Renvoie {peuple, peuple d'avant}.
     */
    public static String[] returnPeople(String current, String previous, String player, String fallback) {
        if (!current.equals(player)) {
            return new String[]{current, previous};
        }
        return new String[]{previous == null || previous.isEmpty() ? fallback : previous, ""};
    }

    // ------------------------------------------------------------------ guerre federale
    /** Ou va un chunk capture : au clan qui l'a pris s'il a le budget ; sinon, dans une guerre federale, a la reserve des zones federales. */
    public enum Capture {
        CLAN, RESERVE, FREE
    }

    public static Capture capture(boolean fits, boolean federalWar, int zones, int zoneBudget) {
        if (fits) {
            return Capture.CLAN;
        }
        return federalWar && zones < zoneBudget ? Capture.RESERVE : Capture.FREE;
    }

    // ------------------------------------------------------------------ traites federaux
    /** Sortes de traite (memes que ceux des clans) et cle de fin de chacune (meme format que {@code Clan.treaties}). */
    public static final List<String> TREATIES = List.of("nap", "alliance", "trade");

    public static String treatyEnd(String kind) {
        return switch (kind) {
            case "alliance" -> "end";
            case "trade" -> "tradeEnd";
            default -> "napEnd";
        };
    }

    /** Le traite de cette etiquette court-il (meme regle que les traites de clan : la non-agression a un terme, les autres jusqu'a rupture) ? */
    public static boolean treatyActive(@Nullable net.minecraft.nbt.CompoundTag t, String kind, long now) {
        if (t == null || !t.getBoolean(kind)) {
            return false;
        }
        long end = t.getLong(treatyEnd(kind));
        return "nap".equals(kind) ? end > now : end <= 0 || end > now;
    }

    /**
     * Traite FEDERAL entre deux clans : signe par la faction de l'un avec l'autre clan, avec la faction de l'autre, ou par la faction de
     * l'autre avec le premier. Les etiquettes vivent dans {@code Faction.treaties} (jamais dans {@code Clan.treaties}) : un clan qui quitte
     * sa faction perd aussitot ses traites federaux. Deux clans d'une meme faction ne se lient pas par un traite federal.
     */
    public static boolean federalTreaty(@Nullable Faction fa, @Nullable Faction fb, Clan a, Clan b, String kind, long now) {
        if (fa != null && fb != null && fa.id.equals(fb.id)) {
            return false;
        }
        if (fa != null && (treatyActive(fa.treaties.get(b.id), kind, now) || fb != null && treatyActive(fa.treaties.get(fb.id), kind, now))) {
            return true;
        }
        return fb != null && (treatyActive(fb.treaties.get(a.id), kind, now) || fa != null && treatyActive(fb.treaties.get(fa.id), kind, now));
    }

    // ------------------------------------------------------------------ declin
    /** Le peuple doit-il etre en declin (sous 2 clans ou 12 membres actifs) ? */
    public static boolean declining(Values v, int clans, int activeMembers) {
        return clans < v.declineClans() || activeMembers < v.declineActive();
    }

    /** Etat du declin apres un controle : fin du declin (ms, 0 = aucun) ; dissolution quand {@code now} atteint la fin. */
    public static long declineUntil(Values v, boolean declining, long current, long now) {
        if (!declining) {
            return 0;
        }
        return current > 0 ? current : now + v.declineMs();
    }
}
