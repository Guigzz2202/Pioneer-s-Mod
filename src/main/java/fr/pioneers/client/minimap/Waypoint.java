package fr.pioneers.client.minimap;

import com.google.gson.JsonObject;

/**
 * 1.10.3 : un waypoint (cote client seulement, sauve par monde ou serveur dans {@code <jeu>/pioneers/minimap/<monde>/waypoints.json}).
 * {@code dim} : cle de dimension ({@code minecraft:overworld}) ; {@code icon} : indice dans {@link Icons#WAYPOINT} ; {@code death} : point de
 * mort automatique ; {@code beacon} : balise dans le monde (avant la 1.0.2 ; garde a l'ecriture pour un client plus ancien).
 * <p>
 * 1.0.2 : {@code style} : rendu dans le monde propre a ce waypoint ({@link #STYLE_DEFAULT} = reglage de la minimap, puis balise, point,
 * invisible). Un fichier d'avant la 1.0.2 (sans {@code style}) est relu sans erreur : {@code beacon} false -&gt; invisible, sinon par defaut.
 */
public final class Waypoint {

    public String name;
    public int x, y, z;
    public String dim;
    public int color;
    public int icon;
    public boolean visible = true, beacon = true, death;
    public long created;
    /** 1.0.2 : 0 = reglage de la minimap, 1 = balise, 2 = point, 3 = invisible. */
    public int style = STYLE_DEFAULT;
    public static final int STYLE_DEFAULT = 0, STYLE_COUNT = 4;
    /** 1.0.2 : distance affichee sous l'etiquette (hysteresis, jamais sauvee ; negative = pas encore affichee). */
    double shownDist = -1;

    public Waypoint(String name, int x, int y, int z, String dim, int color, int icon) {
        this.name = name;
        this.x = x;
        this.y = y;
        this.z = z;
        this.dim = dim;
        this.color = color;
        this.icon = icon;
        this.created = System.currentTimeMillis();
    }

    public Waypoint copy() {
        Waypoint w = new Waypoint(name, x, y, z, dim, color, icon);
        w.visible = visible;
        w.beacon = beacon;
        w.death = death;
        w.created = created;
        w.style = style;
        return w;
    }

    JsonObject save() {
        JsonObject o = new JsonObject();
        o.addProperty("name", name);
        o.addProperty("x", x);
        o.addProperty("y", y);
        o.addProperty("z", z);
        o.addProperty("dim", dim);
        o.addProperty("color", color);
        o.addProperty("icon", icon);
        o.addProperty("visible", visible);
        o.addProperty("style", style);
        o.addProperty("beacon", style != 3);
        o.addProperty("death", death);
        o.addProperty("created", created);
        return o;
    }

    static Waypoint load(JsonObject o) {
        Waypoint w = new Waypoint(str(o, "name", "?"), num(o, "x"), num(o, "y"), num(o, "z"), str(o, "dim", "minecraft:overworld"),
              num(o, "color") & 0xFFFFFF, num(o, "icon"));
        w.visible = !o.has("visible") || o.get("visible").getAsBoolean();
        w.beacon = !o.has("beacon") || o.get("beacon").getAsBoolean();
        w.death = o.has("death") && o.get("death").getAsBoolean();
        w.created = o.has("created") ? o.get("created").getAsLong() : 0;
        if (o.has("style")) {
            w.style = Math.max(0, Math.min(STYLE_COUNT - 1, num(o, "style")));
        } else {
            //fichier d'avant la 1.0.2 : seule la balise coupee se retrouve
            w.style = w.beacon ? STYLE_DEFAULT : 3;
        }
        w.beacon = w.style != 3;
        return w;
    }

    private static String str(JsonObject o, String k, String d) {
        return o.has(k) && o.get(k).isJsonPrimitive() ? o.get(k).getAsString() : d;
    }

    private static int num(JsonObject o, String k) {
        try {
            return o.has(k) ? o.get(k).getAsInt() : 0;
        } catch (RuntimeException e) {
            return 0;
        }
    }

    /** 1.0.2 : rendu effectif dans le monde (le reglage de la minimap pour {@link #STYLE_DEFAULT}). */
    public MinimapConfig.BeaconStyle resolvedStyle(MinimapConfig.BeaconStyle global) {
        return style <= STYLE_DEFAULT || style >= STYLE_COUNT ? global : MinimapConfig.BeaconStyle.values()[style - 1];
    }

    public double distanceTo(double px, double py, double pz) {
        double dx = x + 0.5 - px, dy = y - py, dz = z + 0.5 - pz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
