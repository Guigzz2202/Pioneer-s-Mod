package fr.pioneers.clan.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.core.ClanDefs.Constitution;
import fr.pioneers.clan.core.ClanDefs.CounterPower;
import fr.pioneers.clan.core.ClanDefs.Decider;
import fr.pioneers.clan.core.ClanDefs.LevelDef;
import fr.pioneers.clan.core.ClanDefs.Majority;
import fr.pioneers.clan.core.ClanDefs.PermDef;
import fr.pioneers.clan.core.ClanDefs.RoleDef;
import fr.pioneers.clan.core.ClanDefs.Rule;
import fr.pioneers.clan.core.ClanDefs.Rules;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;

/**
 * 1.9 (MAJ RP, bloc 4) : lecture et verification des fichiers de {@code data/pioneers/clans/}. Un fichier invalide est REFUSE avec un message
 * clair (liste {@code errors}) et le reste se charge ; une constitution incomplete (decision manquante, majorite inconnue, plafond hors bornes)
 * est refusee entiere. Classe pure (Gson seulement) : utilisee par le jeu ({@link ClanDefsLoader}) et par le banc.
 */
public final class ClanDefsParser {

    private ClanDefsParser() {
    }

    /** Construit les definitions ; chaque probleme est ajoute a {@code errors} (fichier, raison). */
    public static ClanDefs parse(Map<String, JsonObject> regimeFiles, JsonObject roles, JsonObject perms, JsonObject levels, JsonObject rules,
                                 List<String> errors) {
        Map<String, Constitution> regimes = new TreeMap<>();
        for (Map.Entry<String, JsonObject> e : regimeFiles.entrySet()) {
            try {
                Constitution c = regime(e.getKey(), e.getValue());
                regimes.put(c.id(), c);
            } catch (RuntimeException ex) {
                errors.add("regimes/" + e.getKey() + ".json : " + ex.getMessage());
            }
        }
        Map<String, Constitution> ordered = new LinkedHashMap<>();
        regimes.values().stream().sorted((a, b) -> Integer.compare(a.order(), b.order())).forEach(c -> ordered.put(c.id(), c));
        for (String r : ClanDefs.REGIMES) {
            if (!ordered.containsKey(r)) {
                errors.add("regimes/" + r + ".json : constitution absente ou refusee");
            }
        }
        Map<String, RoleDef> roleMap = new LinkedHashMap<>();
        if (roles != null) {
            try {
                roleMap = roles(roles);
            } catch (RuntimeException ex) {
                errors.add("roles.json : " + ex.getMessage());
                roleMap = new LinkedHashMap<>();
            }
        } else {
            errors.add("roles.json : absent");
        }
        Map<String, PermDef> permMap = new LinkedHashMap<>();
        if (perms != null) {
            try {
                permMap = perms(perms, ordered.keySet());
            } catch (RuntimeException ex) {
                errors.add("permissions.json : " + ex.getMessage());
                permMap = new LinkedHashMap<>();
            }
        } else {
            errors.add("permissions.json : absent");
        }
        Map<Integer, LevelDef> levelMap = new LinkedHashMap<>();
        if (levels != null) {
            try {
                levelMap = levels(levels);
            } catch (RuntimeException ex) {
                errors.add("levels.json : " + ex.getMessage());
                levelMap = new LinkedHashMap<>();
            }
        } else {
            errors.add("levels.json : absent");
        }
        Rules r = null;
        if (rules != null) {
            try {
                r = rules(rules);
            } catch (RuntimeException ex) {
                errors.add("rules.json : " + ex.getMessage());
            }
        } else {
            errors.add("rules.json : absent");
        }
        return new ClanDefs(ordered, roleMap, permMap, levelMap, r);
    }

    // ------------------------------------------------------------------ regimes
    static Constitution regime(String file, JsonObject o) {
        String id = str(o, "id");
        if (!id.equals(file)) {
            throw new IllegalArgumentException("id « " + id + " » different du nom du fichier");
        }
        JsonObject rep = obj(o, "representative");
        String mode = str(rep, "mode");
        if (!List.of("elected", "appointed", "council", "rotating").contains(mode)) {
            throw new IllegalArgumentException("representative.mode inconnu : " + mode);
        }
        JsonObject council = o.has("council") ? obj(o, "council") : new JsonObject();
        int cmin = intOr(council, "min", 0), cmax = intOr(council, "max", 0);
        if (cmin < 0 || cmax < cmin || cmax > 12) {
            throw new IllegalArgumentException("council.min/max hors bornes");
        }
        if ("council".equals(mode) && cmax < 1) {
            throw new IllegalArgumentException("un representant elu par le Conseil demande un Conseil");
        }
        Map<String, Rule> decisions = new LinkedHashMap<>();
        JsonObject d = obj(o, "decisions");
        for (String k : ClanDefs.DECISIONS) {
            if (!d.has(k)) {
                throw new IllegalArgumentException("decision manquante : " + k);
            }
            Rule r = rule(d.getAsJsonObject(k), "decisions." + k);
            if (r.by() == Decider.COUNCIL && cmax < 1) {
                throw new IllegalArgumentException("decisions." + k + " revient au Conseil, mais le regime n'en a pas");
            }
            decisions.put(k, r);
        }
        for (String k : d.keySet()) {
            if (!ClanDefs.DECISIONS.contains(k)) {
                decisions.put(k, rule(d.getAsJsonObject(k), "decisions." + k));
            }
        }
        JsonObject caps = obj(o, "caps");
        int tax = caps.get("activity_tax").getAsInt(), dues = caps.get("dues").getAsInt();
        if (tax < 0 || tax > 100 || dues < 0) {
            throw new IllegalArgumentException("caps hors bornes");
        }
        String succession = str(o, "succession");
        if (!List.of("emergency_election", "heir", "council", "rotation").contains(succession)) {
            throw new IllegalArgumentException("succession inconnue : " + succession);
        }
        JsonObject cp = obj(o, "counter_power");
        String cpType = str(cp, "type");
        Rule cpRule = "none".equals(cpType) ? null : rule(cp, "counter_power");
        CounterPower counter = new CounterPower(cpType, cpRule, intOr(cp, "cooldown_days", 0), cp.has("imposes") ? str(cp, "imposes") : "");
        JsonObject adv = obj(o, "advantage");
        JsonObject fed = o.has("federal") ? obj(o, "federal") : new JsonObject();
        Rule councilElection = council.has("election") ? rule(council.getAsJsonObject("election"), "council.election")
              : new Rule(Decider.MEMBERS, Majority.SIMPLE, false);
        Rule repElection = rep.has("election") ? rule(rep.getAsJsonObject("election"), "representative.election")
              : new Rule("council".equals(mode) ? Decider.COUNCIL : Decider.MEMBERS, Majority.SIMPLE, false);
        return new Constitution(id, intOr(o, "order", 0), mode, intOr(rep, "term_days", 0), bool(rep, "reelectable", true), cmin, cmax,
              dbl(council, "per_members", 0.5), intOr(council, "term_days", 0), councilElection, repElection, decisions, tax, dues, succession,
              counter, dbl(adv, "claim_budget", 0), bool(adv, "dividend", false), intOr(fed, "contribution_max", 0), dbl(fed, "weight_cap", 0.4));
    }

    static Rule rule(JsonObject o, String where) {
        Decider by = Decider.of(str(o, "by"));
        if (by == null) {
            throw new IllegalArgumentException(where + ".by inconnu : " + str(o, "by"));
        }
        Majority m = Majority.SIMPLE;
        if (by != Decider.REPRESENTATIVE) {
            m = Majority.of(str(o, "majority"));
            if (m == null) {
                throw new IllegalArgumentException(where + ".majority inconnue : " + str(o, "majority"));
            }
        }
        boolean electorate = "electorate".equals(o.has("base") ? str(o, "base") : "") || m == Majority.UNANIMITY;
        return new Rule(by, m, electorate);
    }

    // ------------------------------------------------------------------ roles, permissions, niveaux
    static Map<String, RoleDef> roles(JsonObject o) {
        Map<String, RoleDef> out = new LinkedHashMap<>();
        for (JsonElement e : o.getAsJsonArray("roles")) {
            JsonObject r = e.getAsJsonObject();
            String id = str(r, "id");
            int rank = r.get("rank").getAsInt();
            if (rank < 1 || rank > 100) {
                throw new IllegalArgumentException("rang hors 1..100 : " + id);
            }
            int color = intOr(r, "color", 0);
            if (color < 0 || color > 15) {
                throw new IllegalArgumentException("couleur hors 0..15 : " + id);
            }
            Set<String> perms = new LinkedHashSet<>();
            for (JsonElement p : r.getAsJsonArray("perms")) {
                String pid = p.getAsString();
                if ("*".equals(pid)) {
                    Permission.all().forEach(x -> perms.add(x.id()));
                } else if (Permission.byId(pid) == null) {
                    throw new IllegalArgumentException("permission inconnue « " + pid + " » dans le role " + id);
                } else {
                    perms.add(pid);
                }
            }
            if (out.put(id, new RoleDef(id, rank, color, r.get("withdraw_cap").getAsLong(), bool(r, "fixed", false), Set.copyOf(perms))) != null) {
                throw new IllegalArgumentException("role en double : " + id);
            }
        }
        RoleDef rep = out.get("representative"), recruit = out.get("recruit");
        if (rep == null || recruit == null || rep.rank() != 100) {
            throw new IllegalArgumentException("Representant (rang 100) et Recrue obligatoires");
        }
        for (RoleDef r : out.values()) {
            if (r != rep && r.rank() >= 100 || r != recruit && r.rank() <= recruit.rank()) {
                throw new IllegalArgumentException("rang du role " + r.id() + " : le Representant est seul au rang 100, la Recrue la plus basse");
            }
        }
        return out;
    }

    static Map<String, PermDef> perms(JsonObject o, Set<String> regimes) {
        Map<String, PermDef> out = new LinkedHashMap<>();
        for (JsonElement e : o.getAsJsonArray("permissions")) {
            JsonObject p = e.getAsJsonObject();
            String id = str(p, "id");
            Permission perm = Permission.byId(id);
            if (perm == null) {
                throw new IllegalArgumentException("permission inconnue du jeu : " + id);
            }
            String family = str(p, "family");
            if (!family.equals(perm.family)) {
                throw new IllegalArgumentException("famille de " + id + " : " + family + " (attendu " + perm.family + ")");
            }
            Set<String> reserved = new LinkedHashSet<>();
            for (JsonElement r : p.getAsJsonArray("reserved")) {
                if (!ClanDefs.REGIMES.contains(r.getAsString())) {
                    throw new IllegalArgumentException("regime inconnu dans les reserves de " + id + " : " + r.getAsString());
                }
                reserved.add(r.getAsString());
            }
            String decision = p.has("decision") ? str(p, "decision") : "";
            if (!decision.isEmpty() && !ClanDefs.DECISIONS.contains(decision)) {
                throw new IllegalArgumentException("decision inconnue pour " + id + " : " + decision);
            }
            out.put(id, new PermDef(id, family, decision, Set.copyOf(reserved)));
        }
        for (Permission p : Permission.values()) {
            if (!out.containsKey(p.id())) {
                throw new IllegalArgumentException("permission absente : " + p.id());
            }
        }
        return out;
    }

    static Map<Integer, LevelDef> levels(JsonObject o) {
        Map<Integer, LevelDef> out = new TreeMap<>();
        long last = -1;
        for (JsonElement e : o.getAsJsonArray("levels")) {
            JsonObject l = e.getAsJsonObject();
            LevelDef d = new LevelDef(l.get("level").getAsInt(), l.get("points").getAsLong(), l.get("members").getAsInt(), l.get("chunkload").getAsInt());
            if (d.points() <= last && d.level() > 1 || d.members() < 2 || d.chunkload() < 0) {
                throw new IllegalArgumentException("niveau " + d.level() + " incoherent (points croissants, effectif >= 2)");
            }
            last = d.points();
            out.put(d.level(), d);
        }
        if (!out.containsKey(1) || out.get(1).points() != 0) {
            throw new IllegalArgumentException("le niveau 1 doit exister a 0 point");
        }
        return new LinkedHashMap<>(out);
    }

    static Rules rules(JsonObject o) {
        JsonObject ch = obj(o, "charter"), id = obj(o, "identity"), m = obj(o, "members"), v = obj(o, "vote"), rc = obj(o, "regime_change"),
              tr = obj(o, "treasury"), cl = obj(o, "clock");
        List<String> banned = new ArrayList<>();
        for (JsonElement e : id.getAsJsonArray("banned_words")) {
            banned.add(e.getAsString().toLowerCase(Locale.ROOT));
        }
        double quorum = v.get("quorum").getAsDouble();
        if (quorum < 0 || quorum > 1) {
            throw new IllegalArgumentException("vote.quorum hors 0..1");
        }
        Rules r = new Rules(ch.get("cost").getAsLong(), ch.get("signers").getAsInt(), ch.get("radius").getAsInt(), ch.get("minutes").getAsInt(),
              id.get("name_min").getAsInt(), id.get("name_max").getAsInt(), id.get("tag_min").getAsInt(), id.get("tag_max").getAsInt(),
              id.get("motto_max").getAsInt(), List.copyOf(banned), m.get("invite_minutes").getAsInt(), m.get("rejoin_days").getAsInt(),
              m.get("absence_days").getAsInt(), m.get("dissolve_days").getAsInt(), m.get("active_days").getAsInt(), m.get("max_roles").getAsInt(),
              v.get("hours").getAsInt(), quorum, v.get("retry_hours").getAsInt(), v.get("history").getAsInt(), v.get("text_max").getAsInt(),
              rc.get("cost").getAsLong(), rc.get("delay_days").getAsInt(), rc.get("cooldown_days").getAsInt(), tr.get("spend_share").getAsDouble(),
              tr.get("market_tax_max").getAsInt(), tr.get("market_radius").getAsInt(), tr.get("alert_share").getAsDouble(),
              tr.get("alert_min").getAsLong(), cl.get("max_jump_minutes").getAsInt());
        if (r.nameMin() < 1 || r.nameMax() < r.nameMin() || r.tagMin() < 1 || r.tagMax() < r.tagMin() || r.voteHours() < 1 || r.maxRoles() < 2
            || r.marketTaxMax() < 0 || r.marketTaxMax() > 100 || r.spendShare() <= 0 || r.spendShare() > 1) {
            throw new IllegalArgumentException("valeurs hors bornes");
        }
        return r;
    }

    // ------------------------------------------------------------------ aides
    private static JsonObject obj(JsonObject o, String k) {
        if (!o.has(k) || !o.get(k).isJsonObject()) {
            throw new IllegalArgumentException("bloc « " + k + " » manquant");
        }
        return o.getAsJsonObject(k);
    }

    private static String str(JsonObject o, String k) {
        if (!o.has(k)) {
            throw new IllegalArgumentException("champ « " + k + " » manquant");
        }
        return o.get(k).getAsString();
    }

    private static int intOr(JsonObject o, String k, int def) {
        return o.has(k) ? o.get(k).getAsInt() : def;
    }

    private static double dbl(JsonObject o, String k, double def) {
        return o.has(k) ? o.get(k).getAsDouble() : def;
    }

    private static boolean bool(JsonObject o, String k, boolean def) {
        return o.has(k) ? o.get(k).getAsBoolean() : def;
    }

    static List<String> strings(JsonArray a) {
        List<String> out = new ArrayList<>();
        if (a != null) {
            a.forEach(e -> out.add(e.getAsString()));
        }
        return out;
    }
}
