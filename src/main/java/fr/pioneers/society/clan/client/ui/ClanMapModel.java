package fr.pioneers.society.clan.client.ui;

import java.util.HashMap;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : copie client de la carte strategique ({@code CLAN_MAP}) : grille complete a l'abonnement, puis seules les
 * cases qui changent (en coordonnees absolues : quand le joueur bouge, les cases deja connues glissent, les nouvelles arrivent). Les
 * constantes reprennent celles du serveur ({@code clan.ui.ClanMapNet}), qu'on ne peut pas lire d'ici (classe serveur).
 */
final class ClanMapModel {

    static final int FOG = 0, FREE = 1, OWN = 2, ALLY = 3, NEUTRAL = 4, ENEMY = 5, PLOT = 6;
    static final int OUTPOST = 1 << 4, DECLINE = 1 << 5, CAPTURED = 1 << 6, SIEGE = 1 << 7, LOADED = 1 << 8, FEDERAL = 1 << 9, RALLIED = 1 << 10,
          HALL = 1 << 11, TOWER = 1 << 12, ZONE_SHIFT = 13, OWNER_SHIFT = 16;

    /** Un clan de la palette : nom (vide pour un Solitaire : {@code soloOf} = son personnage), etiquette, teinture 0..15. */
    record Owner(String name, String soloOf, String tag, int color) {
    }

    static int r = 15, n = 31, cx, cz;
    static String dim = "";
    static int[] cells = new int[0];
    static final Map<Integer, Owner> PALETTE = new HashMap<>();
    static int version;

    private ClanMapModel() {
    }

    /** 1.9.2 : derniere reponse a une zone tracee (apercu ou application, {@code ClanTerritory.area}) et son numero. */
    @Nullable
    static CompoundTag area;
    static int areaVersion;

    static void receive(CompoundTag t) {
        if (t.contains("area")) {
            area = t.getCompound("area");
            areaVersion++;
            return;
        }
        int ncx = t.getInt("cx"), ncz = t.getInt("cz");
        int nr = Math.max(1, Math.min(32, t.getInt("r")));
        if (t.getBoolean("full")) {
            r = nr;
            n = 2 * r + 1;
            PALETTE.clear();
            dim = t.getString("dim");
            int[] c = t.getIntArray("cells");
            cells = c.length == n * n ? c : new int[n * n];
        } else if (cells.length == n * n) {
            //glissement : les cases connues suivent leurs coordonnees absolues
            int[] moved = new int[n * n];
            for (int i = 0; i < moved.length; i++) {
                int ox = ncx - r + i % n - (cx - r), oz = ncz - r + i / n - (cz - r);
                moved[i] = ox >= 0 && oz >= 0 && ox < n && oz < n ? cells[oz * n + ox] : FOG;
            }
            int[] idx = t.getIntArray("idx"), val = t.getIntArray("val");
            for (int i = 0; i < idx.length && i < val.length; i++) {
                if (idx[i] >= 0 && idx[i] < moved.length) {
                    moved[idx[i]] = val[i];
                }
            }
            cells = moved;
        } else {
            return;
        }
        cx = ncx;
        cz = ncz;
        ListTag pal = t.getList("palette", Tag.TAG_COMPOUND);
        for (int i = 0; i < pal.size(); i++) {
            CompoundTag e = pal.getCompound(i);
            PALETTE.put(e.getInt("i"), new Owner(e.getString("name"), e.getString("soloOf"), e.getString("tag"), e.getInt("color") & 15));
        }
        version++;
    }

    static boolean ready() {
        return cells.length == n * n && n > 1;
    }

    /** Case (gx, gz) de la grille (0..n-1), ou {@link #FOG}. */
    static int at(int gx, int gz) {
        return gx < 0 || gz < 0 || gx >= n || gz >= n || !ready() ? FOG : cells[gz * n + gx];
    }

    static int kind(int cell) {
        return cell & 15;
    }

    @Nullable
    static Owner owner(int cell) {
        return PALETTE.get((cell >>> OWNER_SHIFT) & 255);
    }

    static void clear() {
        cells = new int[0];
        PALETTE.clear();
        dim = "";
        version++;
    }
}
