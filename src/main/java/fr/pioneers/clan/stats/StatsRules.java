package fr.pioneers.clan.stats;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 1.9 (MAJ RP, bloc 4) : regles des statistiques de clan, lues dans {@code data/pioneers/clans/stats.json}. Classe PURE (aucune classe de
 * Minecraft) : le banc {@code tools/rptest/WarBench} l'exerce.
 *
 * <p><b>Potentiel de frappe</b> (decision no 5) : P = 10 C + 20 A + 10 R + V, C le niveau du skill Combat (0..10), A le tier de la meilleure
 * arme tenue ou portee (0..4), R le tier moyen de l'armure portee (0..4), V = 10 pour l'Eclaireur : 230 au plus. Potentiel d'un clan = somme
 * de ses membres actifs, majoree de 5 % par vocation distincte presente (15 % au plus).
 *
 * <p><b>Production</b> : chaque action du tableau {@code production} vaut un objet de reference de la table de valeurs (bloc 3) fois le
 * nombre d'actions, avec la fatigue de repetition du gain d'XP ; plafond de 10 000 jetons-equivalent par joueur et par jour de jeu.
 */
public final class StatsRules {

    /** Valeur d'une action : objet de reference (et quantite) de la table de valeurs, sinon valeur fixe (centiemes de jeton). */
    public record Ref(String item, double count, long value) {
    }

    public record Values(int combat, int weapon, int armor, int scout, String scoutVocation, double vocationBonus, double vocationBonusMax,
                         double[] weaponDamage, double[] armorScore, double[] armorReference, double toughnessReference, int bow, int crossbow,
                         int rifle, int[] brackets, long minIntervalMs, int days, long aggregateMs, long productionCap,
                         Map<String, Ref> production) {
    }

    public static final Values DEFAULTS = new Values(10, 20, 10, 10, "scout", 0.05, 0.15, new double[]{3, 5, 6.5, 9},
          new double[]{0.2, 0.55, 0.8, 1.05}, new double[]{3, 8, 6, 3}, 12, 1, 2, 4, new int[]{400, 1500}, 60_000L, 30, 600_000L, 1_000_000L,
          Map.of());

    private static volatile Values current = DEFAULTS;

    private StatsRules() {
    }

    public static Values get() {
        return current;
    }

    public static void set(Values v) {
        current = v == null ? DEFAULTS : v;
    }

    public static Values parse(JsonObject o) {
        Values d = DEFAULTS;
        JsonObject p = o.has("potential") ? o.getAsJsonObject("potential") : new JsonObject();
        Map<String, Ref> prod = new LinkedHashMap<>();
        if (o.has("production")) {
            for (Map.Entry<String, JsonElement> e : o.getAsJsonObject("production").entrySet()) {
                if (e.getKey().startsWith("_")) {
                    continue;
                }
                JsonObject r = e.getValue().getAsJsonObject();
                prod.put(e.getKey(), new Ref(r.has("item") ? r.get("item").getAsString() : "", r.has("count") ? r.get("count").getAsDouble() : 1,
                      r.has("value") ? r.get("value").getAsLong() : 0));
            }
        }
        return new Values(i(p, "combat", d.combat()), i(p, "weapon", d.weapon()), i(p, "armor", d.armor()), i(p, "scout", d.scout()),
              p.has("scout_vocation") ? p.get("scout_vocation").getAsString() : d.scoutVocation(), dd(p, "vocation_bonus", d.vocationBonus()),
              dd(p, "vocation_bonus_max", d.vocationBonusMax()), arr(p, "weapon_damage", d.weaponDamage()), arr(p, "armor_score", d.armorScore()),
              arr(p, "armor_reference", d.armorReference()), dd(p, "toughness_reference", d.toughnessReference()), i(p, "bow", d.bow()),
              i(p, "crossbow", d.crossbow()), i(p, "rifle", d.rifle()), ints(p, "brackets", d.brackets()),
              p.has("min_interval_seconds") ? p.get("min_interval_seconds").getAsLong() * 1000L : d.minIntervalMs(), i(o, "days", d.days()),
              o.has("aggregate_minutes") ? o.get("aggregate_minutes").getAsLong() * 60_000L : d.aggregateMs(),
              o.has("production_cap") ? o.get("production_cap").getAsLong() * 100L : d.productionCap(), Map.copyOf(prod));
    }

    private static int i(JsonObject o, String k, int d) {
        return o.has(k) ? o.get(k).getAsInt() : d;
    }

    private static double dd(JsonObject o, String k, double d) {
        return o.has(k) ? o.get(k).getAsDouble() : d;
    }

    private static double[] arr(JsonObject o, String k, double[] d) {
        if (!o.has(k)) {
            return d;
        }
        JsonArray a = o.getAsJsonArray(k);
        double[] out = new double[a.size()];
        for (int n = 0; n < out.length; n++) {
            out[n] = a.get(n).getAsDouble();
        }
        return out;
    }

    private static int[] ints(JsonObject o, String k, int[] d) {
        if (!o.has(k)) {
            return d;
        }
        JsonArray a = o.getAsJsonArray(k);
        int[] out = new int[a.size()];
        for (int n = 0; n < out.length; n++) {
            out[n] = a.get(n).getAsInt();
        }
        return out;
    }

    // ------------------------------------------------------------------ potentiel de frappe
    /** P = 10 C + 20 A + 10 R + V (arrondi) ; C 0..10, A 0..4, R 0..4 (moyenne), V vrai pour l'Eclaireur. */
    public static int potential(Values v, int combat, int weaponTier, double armorTier, boolean scout) {
        int c = Math.max(0, Math.min(10, combat));
        int a = Math.max(0, Math.min(4, weaponTier));
        double r = Math.max(0, Math.min(4, armorTier));
        return (int) Math.round(v.combat() * c + v.weapon() * a + v.armor() * r + (scout ? v.scout() : 0));
    }

    /** Potentiel d'un clan : somme des actifs, + 5 % par vocation distincte (15 % au plus). */
    public static int clanPotential(Values v, int sum, int distinctVocations) {
        double bonus = Math.min(v.vocationBonusMax(), Math.max(0, distinctVocations) * v.vocationBonus());
        return (int) Math.round(sum * (1 + bonus));
    }

    /** Tier 0..4 d'une valeur selon quatre seuils croissants (atteindre le n-ieme seuil donne le tier n). */
    public static int tierOf(double value, double[] thresholds) {
        int t = 0;
        for (int n = 0; n < thresholds.length && n < 4; n++) {
            if (value >= thresholds[n]) {
                t = n + 1;
            }
        }
        return t;
    }

    /** Score d'une piece d'armure : protection rapportee a la reference de son emplacement, plus la robustesse rapportee a sa reference. */
    public static double armorScore(Values v, int slot, double armor, double toughness) {
        double ref = slot >= 0 && slot < v.armorReference().length ? v.armorReference()[slot] : 8;
        return armor / Math.max(1e-6, ref) + toughness / Math.max(1e-6, v.toughnessReference());
    }

    /** Palier public de l'annuaire : 0 faible, 1 moyen, 2 fort (jamais le chiffre). */
    public static int bracket(Values v, int potential) {
        int b = 0;
        for (int n = 0; n < v.brackets().length; n++) {
            if (potential >= v.brackets()[n]) {
                b = n + 1;
            }
        }
        return Math.min(2, b);
    }

    // ------------------------------------------------------------------ production
    /** Valeur produite (centiemes de jeton) : valeur unitaire x nombre d'actions x fatigue, arrondie. */
    public static long produced(long unitCenti, double refCount, double actions, double fatigue) {
        return Math.max(0, Math.round(unitCenti * refCount * actions * Math.max(0, fatigue)));
    }

    /** Ajout borne par le plafond journalier : ce qui reste a crediter aujourd'hui. */
    public static long capped(long add, long doneToday, long cap) {
        return Math.max(0, Math.min(add, cap - doneToday));
    }
}
