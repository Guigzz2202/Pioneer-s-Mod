package fr.pioneers.client.space;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.world.level.Level;

/**
 * 0.20.46 (audit) : cadence des particules lancees par les rendus de blocs. Elles partaient a chaque image (debit proportionnel aux FPS) ;
 * elles partent maintenant a la premiere image de chaque tick de jeu, une fois par tuile : le meme debit qu'avant a 20 images par seconde.
 * Rendu seulement (fil de rendu) ; cles faibles : une tuile dechargee disparait d'elle-meme.
 */
public final class FxTicks {

    private static final Map<Object, long[]> LAST = new WeakHashMap<>();

    private FxTicks() {
    }

    /** Vrai a la premiere image du tick de jeu en cours pour cette tuile (a appeler une seule fois par image et par tuile). */
    public static boolean firstFrame(Object tile, Level level) {
        long now = level.getGameTime();
        long[] last = LAST.get(tile);
        if (last == null) {
            LAST.put(tile, new long[]{now});
            return true;
        }
        if (last[0] == now) {
            return false;
        }
        last[0] = now;
        return true;
    }
}
