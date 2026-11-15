package fr.pioneers.society.clan.war;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

/**
 * 1.9 (MAJ RP, bloc 4) : regles de la diplomatie et de la guerre, lues dans {@code data/pioneers/clans/war.json} (valeurs de depart de la
 * specification, hypothese no 6). Classe PURE (aucune classe de Minecraft) : le banc {@code tools/rptest/WarBench} l'exerce directement.
 * Toutes les durees sont en temps REEL du serveur (ms), les creneaux en heures de l'horloge locale du serveur.
 */
public final class WarRules {

    public static final long MINUTE = 60_000L, HOUR = 3_600_000L, DAY = 86_400_000L;

    /** Valeurs des regles (toutes modifiables en donnees). */
    public record Values(long declareCost, int reasonMax, long noticeMs, long minAgeMs, double maxRatio, int memberFloor, int maxOutgoing,
                         int maxWars, int windowCount, int windowHours, long windowChangeMs, int[] defaultWindows, int radius, int checkTicks,
                         long captureMs, long captureOfflineMs, int capturesPerDay, long holdMs, long idleMs, long sealMs, double townHallShare,
                         long expireMs, long truceMs, double tributeShare, long tributeMax, long napMs, long ruptureMs, long offerMs) {
    }

    /** Pas de la capture : la barre avance, s'arrete, recule ou attend (personne). */
    public enum Step {
        ADVANCE, PAUSE, RETREAT, IDLE
    }

    public static final Values DEFAULTS = new Values(500, 100, 24 * HOUR, 3 * DAY, 4.0, 10, 1, 3, 2, 2, 7 * DAY, new int[]{19, 21}, 8, 20,
          10 * MINUTE, 30 * MINUTE, 3, 15 * MINUTE, 5 * MINUTE, 24 * HOUR, 0.5, 7 * DAY, 3 * DAY, 0.05, 5000, 7 * DAY, 24 * HOUR, 48 * HOUR);

    private static volatile Values current = DEFAULTS;

    private WarRules() {
    }

    public static Values get() {
        return current;
    }

    public static void set(Values v) {
        current = v == null ? DEFAULTS : v;
    }

    // ------------------------------------------------------------------ lecture du fichier
    /** Lit {@code war.json} ; toute valeur absente garde sa valeur de depart. */
    public static Values parse(JsonObject o) {
        Values d = DEFAULTS;
        JsonObject dec = obj(o, "declaration"), win = obj(o, "windows"), sg = obj(o, "siege"), end = obj(o, "end"), tr = obj(o, "treaties");
        int[] def = d.defaultWindows();
        if (win.has("default") && win.get("default").isJsonArray()) {
            JsonArray a = win.getAsJsonArray("default");
            def = new int[a.size()];
            for (int i = 0; i < a.size(); i++) {
                def[i] = a.get(i).getAsInt();
            }
        }
        return new Values(lng(dec, "cost", d.declareCost()), num(dec, "reason_max", d.reasonMax()), hours(dec, "notice_hours", d.noticeMs()),
              days(dec, "min_age_days", d.minAgeMs()), dbl(dec, "max_ratio", d.maxRatio()), num(dec, "member_floor", d.memberFloor()),
              num(dec, "max_outgoing", d.maxOutgoing()), num(dec, "max_wars", d.maxWars()), num(win, "count", d.windowCount()),
              num(win, "hours", d.windowHours()), days(win, "change_days", d.windowChangeMs()), def, num(sg, "radius", d.radius()),
              num(sg, "check_ticks", d.checkTicks()), minutes(sg, "capture_minutes", d.captureMs()),
              minutes(sg, "capture_minutes_offline", d.captureOfflineMs()), num(sg, "captures_per_day", d.capturesPerDay()),
              minutes(sg, "hold_minutes", d.holdMs()), sg.has("idle_seconds") ? sg.get("idle_seconds").getAsLong() * 1000L : d.idleMs(),
              hours(sg, "seal_hours", d.sealMs()), dbl(sg, "town_hall_share", d.townHallShare()), days(end, "expire_days", d.expireMs()),
              days(end, "truce_days", d.truceMs()), dbl(end, "tribute_share", d.tributeShare()), lng(end, "tribute_max", d.tributeMax()),
              days(tr, "non_aggression_days", d.napMs()), hours(tr, "rupture_hours", d.ruptureMs()), hours(tr, "offer_hours", d.offerMs()));
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

    private static double dbl(JsonObject o, String k, double d) {
        return o.has(k) ? o.get(k).getAsDouble() : d;
    }

    private static long minutes(JsonObject o, String k, long d) {
        return o.has(k) ? Math.round(o.get(k).getAsDouble() * MINUTE) : d;
    }

    private static long hours(JsonObject o, String k, long d) {
        return o.has(k) ? Math.round(o.get(k).getAsDouble() * HOUR) : d;
    }

    private static long days(JsonObject o, String k, long d) {
        return o.has(k) ? Math.round(o.get(k).getAsDouble() * DAY) : d;
    }

    // ------------------------------------------------------------------ creneaux de siege
    /** La minute du jour (0..1439) tombe-t-elle dans l'un des creneaux (heure de debut, duree {@code hours}) ? Un creneau peut passer minuit. */
    public static boolean inWindow(int[] starts, int minuteOfDay, int hours) {
        if (starts == null) {
            return false;
        }
        for (int s : starts) {
            int from = Math.floorMod(s, 24) * 60;
            int since = Math.floorMod(minuteOfDay - from, 1440);
            if (since < hours * 60) {
                return true;
            }
        }
        return false;
    }

    /** Fin (minute du jour, 0..1439) du creneau ouvert a cette minute, ou -1. */
    public static int windowEnd(int[] starts, int minuteOfDay, int hours) {
        if (starts != null) {
            for (int s : starts) {
                int from = Math.floorMod(s, 24) * 60;
                if (Math.floorMod(minuteOfDay - from, 1440) < hours * 60) {
                    return Math.floorMod(from + hours * 60, 1440);
                }
            }
        }
        return -1;
    }

    /** Creneaux en vigueur : le changement demande ne s'applique qu'a partir de {@code from} (7 jours apres la demande). */
    public static int[] effectiveWindows(int[] current, int[] pending, long from, long now) {
        return pending != null && from > 0 && now >= from ? pending : current;
    }

    /** Creneaux valides ? Nombre exact, heures 0..23, aucun chevauchement (ecart circulaire au moins egal a la duree). Cle de refus ou null. */
    public static String validateWindows(int[] starts, int count, int hours) {
        if (starts == null || starts.length != count) {
            return "clan.pioneers.war.windows.count";
        }
        for (int s : starts) {
            if (s < 0 || s > 23) {
                return "clan.pioneers.war.windows.hour";
            }
        }
        for (int i = 0; i < starts.length; i++) {
            for (int j = i + 1; j < starts.length; j++) {
                int d = Math.floorMod(starts[i] - starts[j], 24);
                if (Math.min(d, 24 - d) < hours) {
                    return "clan.pioneers.war.windows.overlap";
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ capture
    /** Attaquants et defenseurs presents : la capture avance tant qu'un attaquant reste, s'arrete si les defenseurs sont au moins aussi nombreux, recule s'ils sont seuls. */
    public static Step step(int attackers, int defenders) {
        if (attackers > 0) {
            return defenders >= attackers ? Step.PAUSE : Step.ADVANCE;
        }
        return defenders > 0 ? Step.RETREAT : Step.IDLE;
    }

    /** Duree d'une capture : 10 minutes si un defenseur au moins est connecte, 30 sinon. */
    public static long captureDuration(Values v, boolean defenderOnline) {
        return defenderOnline ? v.captureMs() : v.captureOfflineMs();
    }

    /** Avancement (0..1) apres un controle de {@code dtMs} : la barre avance ou recule de dt / duree, borne a 0..1. */
    public static double advance(double progress, Step step, long dtMs, long durationMs) {
        double d = durationMs <= 0 ? 1 : (double) Math.max(0, dtMs) / durationMs;
        double p = switch (step) {
            case ADVANCE -> progress + d;
            case RETREAT -> progress - d;
            default -> progress;
        };
        return Math.max(0, Math.min(1, p));
    }

    /** Capture achevee (a la precision du calcul pres : 600 controles d'une seconde font exactement 10 minutes). */
    public static boolean complete(double progress) {
        return progress >= 1 - 1e-9;
    }

    /** Duree d'un controle (ms) bornee : un serveur qui rame ne saute pas une capture, une horloge qui saute non plus. */
    public static long checkDelta(long elapsedMs, int checkTicks) {
        long nominal = checkTicks * 50L;
        return Math.max(0, Math.min(2 * nominal, elapsedMs));
    }

    /** Le chunk de la Mairie n'est capturable qu'apres la perte de la moitie du territoire tenu a l'ouverture de la guerre. */
    public static boolean townHallOpen(int initialClaims, int lost, double share) {
        return initialClaims <= 0 || lost >= Math.ceil(initialClaims * share);
    }

    /** Fin des scelles des conteneurs d'un chunk capture (24 heures reelles). */
    public static long sealUntil(Values v, long now) {
        return now + v.sealMs();
    }

    /**
     * Conteneurs d'un chunk scelle : seul l'ancien proprietaire les ouvre jusqu'a la fin des scelles ; ensuite, la regle ordinaire du chunk
     * s'applique (renvoie null). Meme lecture que {@code ClanApi.canModify}.
     */
    public static Boolean sealedAccess(long sealedUntil, String sealedFor, String actorClan, long now) {
        if (sealedUntil <= now) {
            return null;
        }
        return actorClan != null && actorClan.equals(sealedFor);
    }

    // ------------------------------------------------------------------ declaration
    /** Potentiel d'un clan retenu pour le rapport de force : au moins {@code memberFloor} par membre actif (un pionnier n'est jamais nul). */
    public static int ratioPotential(int potential, int activeMembers, int memberFloor) {
        return Math.max(potential, activeMembers * memberFloor);
    }

    /** Ce qu'il faut savoir pour juger une declaration. */
    public record Declaration(boolean attackerSolo, boolean defenderSolo, long attackerAgeMs, long defenderAgeMs, int attackerPotential,
                              int defenderPotential, int attackerOutgoing, int attackerWars, int defenderWars, boolean nonAggression,
                              boolean truce, boolean atWar, boolean allied, int reasonLength, long treasury, boolean frozen) {
    }

    /** Cle de refus d'une declaration de guerre, ou null si elle est permise. Le faible peut toujours attaquer le fort. */
    public static String refuse(Values v, Declaration d) {
        if (d.attackerSolo() || d.defenderSolo()) {
            return "clan.pioneers.war.refuse.solo";
        }
        if (d.atWar()) {
            return "clan.pioneers.war.refuse.already";
        }
        if (d.allied()) {
            return "clan.pioneers.war.refuse.allied";
        }
        if (d.attackerAgeMs() < v.minAgeMs() || d.defenderAgeMs() < v.minAgeMs()) {
            return "clan.pioneers.war.refuse.young";
        }
        if (d.nonAggression()) {
            return "clan.pioneers.war.refuse.nap";
        }
        if (d.truce()) {
            return "clan.pioneers.war.refuse.truce";
        }
        if (d.attackerOutgoing() >= v.maxOutgoing()) {
            return "clan.pioneers.war.refuse.outgoing";
        }
        if (d.attackerWars() >= v.maxWars() || d.defenderWars() >= v.maxWars()) {
            return "clan.pioneers.war.refuse.too_many";
        }
        if (d.attackerPotential() > v.maxRatio() * d.defenderPotential()) {
            return "clan.pioneers.war.refuse.ratio";
        }
        if (d.reasonLength() <= 0 || d.reasonLength() > v.reasonMax()) {
            return "clan.pioneers.war.refuse.reason";
        }
        if (d.frozen() || d.treasury() < v.declareCost()) {
            return "clan.pioneers.war.refuse.cost";
        }
        return null;
    }

    // ------------------------------------------------------------------ fin de guerre
    /** Expiration : 7 jours sans capture depuis l'ouverture (ou la derniere capture). */
    public static boolean expired(Values v, long start, long lastCapture, long now) {
        return now - Math.max(start, lastCapture) >= v.expireMs();
    }

    /** Tribut d'une capitulation : 5 % du tresor du vaincu, 5 000 jetons au plus. */
    public static long tribute(Values v, long treasury) {
        return Math.max(0, Math.min(v.tributeMax(), (long) Math.floor(treasury * v.tributeShare())));
    }

    /** Index du jour reel (date locale du serveur) pour le plafond de captures par jour. */
    public static long realDay(long epochMs, long zoneOffsetMs) {
        return Math.floorDiv(epochMs + zoneOffsetMs, DAY);
    }
}
