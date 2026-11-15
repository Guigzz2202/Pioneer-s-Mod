package fr.pioneers.client.minimap;

import net.minecraft.nbt.CompoundTag;

/**
 * 1.10.3 : lieu remarquable releve dans un chunk connu du client (blocs et tuiles du chunk : rien ne vient du serveur pour eux).
 *
 * @param kind  sorte (pictogramme, calque)
 * @param color couleur RGB (role d'un point logistique, sinon celle de la sorte)
 * @param name  nom (gare, port) ou vide
 */
public record Poi(Kind kind, int x, int y, int z, int color, String name) {

    /** Sortes, avec leur case dans l'atlas des pictogrammes et leur couleur par defaut. Ordre sauve sur le disque : ajouter EN FIN. */
    public enum Kind {
        STATION(Icons.STATION, 0xE0E6EC), HARBOR(Icons.HARBOR, 0x7FC8F0), RAIL_POST(Icons.POST, 0xB8C2CC), SUBSTATION(Icons.SUBSTATION, 0xF2D45C),
        SEA_BEACON(Icons.BUOY, 0xF07A3C), HUB(Icons.HUB, 0x3FD0A0), RELAY(Icons.RELAY, 0x5FB8E8), TERMINAL(Icons.TERMINAL, 0x9FD8C8),
        POINT(Icons.POINT, 0xFFFFFF), CHEST(Icons.CHEST, 0xD8B070), TUBE_STATION(Icons.TUBE, 0xF2B640);

        public final int icon, color;

        Kind(int icon, int color) {
            this.icon = icon;
            this.color = color;
        }

        /** Calque qui montre la sorte. */
        public boolean shown(MinimapLayers l) {
            return switch (this) {
                case STATION, HARBOR, RAIL_POST, SUBSTATION -> l.stations;
                case SEA_BEACON -> l.sea;
                case HUB, RELAY, TERMINAL, TUBE_STATION -> l.robotics;
                case POINT, CHEST -> l.points;
            };
        }

        /** Montree seulement de pres (ecart d'echelle) : postes et coffres, nombreux. */
        public float minScale() {
            return switch (this) {
                case RAIL_POST, CHEST, POINT, TERMINAL -> 1F;
                default -> 0F;
            };
        }
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("k", (byte) kind.ordinal());
        t.putInt("x", x);
        t.putInt("y", y);
        t.putInt("z", z);
        t.putInt("c", color);
        if (!name.isEmpty()) {
            t.putString("n", name);
        }
        return t;
    }

    public static Poi load(CompoundTag t) {
        Kind[] all = Kind.values();
        int k = t.getByte("k");
        return new Poi(all[Math.max(0, Math.min(all.length - 1, k))], t.getInt("x"), t.getInt("y"), t.getInt("z"), t.getInt("c"), t.getString("n"));
    }
}
