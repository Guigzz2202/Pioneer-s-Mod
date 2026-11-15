package fr.pioneers.client.kinetics;

import fr.pioneers.kinetics.KineticTile;
import java.util.ArrayList;
import java.util.List;

/** Rendus supplementaires des blocs-entites de l'age mecanique (cordes, roues, ailes, machines) : enregistres par les lots suivants. */
public final class KineticExtras {

    private KineticExtras() {
    }

    public interface Extra {

        boolean draw(KDraw d, KineticTile tile, float partial);
    }

    private static final List<Extra> EXTRAS = new ArrayList<>();

    public static void register(Extra extra) {
        EXTRAS.add(extra);
    }

    static void draw(KDraw d, KineticTile tile, float partial) {
        for (Extra extra : EXTRAS) {
            if (extra.draw(d, tile, partial)) {
                return;
            }
        }
    }
}
