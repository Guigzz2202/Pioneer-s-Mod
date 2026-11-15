package fr.pioneers.client.look;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 1.0.5-beta (lot 14) : zones de l'ecran occupees par les AUTRES affichages du mod (minimap, Periple, jauges d'equipement), relevees a leur
 * dernier dessin ({@link #mark} / {@link #clear}) ; la fiche au regard les evite ({@link CardPlacement}). Une zone non redessinee depuis
 * {@link #STALE_MS} est oubliee (affichage masque sans passer par {@link #clear}). Classe pure (aucune classe de Minecraft).
 */
public final class HudZones {

    private HudZones() {
    }

    public static final long STALE_MS = 250;
    private static final Map<String, long[]> ZONES = new LinkedHashMap<>();

    /** L'affichage {@code id} vient d'etre dessine dans {x0, y0, x1, y1} (px d'interface). */
    public static synchronized void mark(String id, int x0, int y0, int x1, int y1) {
        ZONES.put(id, new long[]{x0, y0, x1, y1, System.currentTimeMillis()});
    }

    /** L'affichage {@code id} n'est plus dessine. */
    public static synchronized void clear(String id) {
        ZONES.remove(id);
    }

    /** Zones encore valides, chacune elargie de {@code margin} px. */
    public static synchronized List<int[]> active(int margin) {
        long now = System.currentTimeMillis();
        List<int[]> out = new ArrayList<>();
        for (long[] z : ZONES.values()) {
            if (now - z[4] <= STALE_MS) {
                out.add(new int[]{(int) z[0] - margin, (int) z[1] - margin, (int) z[2] + margin, (int) z[3] + margin});
            }
        }
        return out;
    }
}
