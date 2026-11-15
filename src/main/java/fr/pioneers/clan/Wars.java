package fr.pioneers.clan;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;

/**
 * 1.9 (MAJ RP, bloc 4) : guerres entre clans (et guerres federales). Une guerre est une ligne de {@link ClanData#wars} : attaquant
 * ({@code a}), defenseur ({@code b}), {@code start} (fin du preavis, ms), {@code end} (0 = en cours), captures, motif. Lecture seule ici ;
 * declaration, sieges et fin de guerre dans le paquet des sieges.
 */
public final class Wars {

    private Wars() {
    }

    /** Les deux clans (ou leurs factions) sont-ils en guerre ouverte (preavis ecoule, pas encore finie) ? */
    public static boolean atWar(MinecraftServer server, Clan a, Clan b) {
        ClanData data = ClanData.get(server);
        long now = System.currentTimeMillis();
        for (Tag x : data.wars) {
            CompoundTag w = (CompoundTag) x;
            if (w.getLong("start") > now || w.getLong("end") > 0 && w.getLong("end") <= now) {
                continue;
            }
            if (sides(w, a, b) || sides(w, b, a)) {
                return true;
            }
        }
        return false;
    }

    private static boolean sides(CompoundTag w, Clan x, Clan y) {
        boolean left = w.hasUUID("a") && (w.getUUID("a").equals(x.id) || x.faction != null && w.getUUID("a").equals(x.faction));
        boolean right = w.hasUUID("b") && (w.getUUID("b").equals(y.id) || y.faction != null && w.getUUID("b").equals(y.faction));
        return left && right;
    }
}
