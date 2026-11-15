package fr.pioneers.farm.aquarium;

import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.GeneratedFish;

/**
 * 1.0.5-beta (lot 14, Guillaume : « Aquarium : nombre max de poissons logique selon leur taille ») : PLACES des poissons, pures (aucune
 * classe de Minecraft ; controle {@code tools/check_p105_14.py}, GameTests {@code PatchP105Lot14Tests}).
 * <ul>
 *     <li>un poisson prend {@link #places} : sa longueur reelle (table des especes : {@code cmMin..cmMax}, selon sa taille 0,8..1,2 comme a la
 *     peche) divisee par {@link #CM_PER_PLACE}, arrondie, au moins 1 : poisson tropical, poisson-clown, sardine 1 ; perche, truite 2 ;
 *     morue 6, saumon 6, brochet 6 ; esturgeon, thon, espadon 17 a 24 ;</li>
 *     <li>un bassin offre {@link #CELL_PLACES} places par case PLEINE D'EAU ({@link #capacity}) : un poisson plus grand qu'une case demande un
 *     bassin de plusieurs cases ;</li>
 *     <li>les crustaces restent comptes a part (2 par case, au fond) ;</li>
 *     <li>1.0.5-beta (lot 25) : un REQUIN (silhouette {@code shark} : le requin gris peche) demande un bassin d'au moins
 *     {@link #SHARK_MIN_CELLS} cases d'eau ({@link #minWetCells} : 3 x 3 x 2, il y nage a sa taille) ; il va dans la case qui a le plus
 *     d'eau autour d'elle ({@code Aquariums.fishCell}).</li>
 * </ul>
 */
public final class AquariumRules {

    private AquariumRules() {
    }

    /** Places par case d'eau ; centimetres de poisson par place. */
    public static final int CELL_PLACES = 16;
    public static final double CM_PER_PLACE = 12.5;

    /** Longueur reelle (cm) d'un poisson de cette espece a cette taille (0,8 = le plus petit, 1,2 = le plus grand, comme a la peche). */
    public static double length(FishDef def, float size) {
        double t = Math.max(0, Math.min(1, (size - 0.8) / 0.4));
        return def.cmMin() + t * (def.cmMax() - def.cmMin());
    }

    /** Places prises par un poisson. */
    public static int places(FishDef def, float size) {
        return Math.max(1, (int) Math.round(length(def, size) / CM_PER_PLACE));
    }

    /** Places d'un bassin de {@code wetCells} cases pleines d'eau. */
    public static int capacity(int wetCells) {
        return Math.max(0, wetCells) * CELL_PLACES;
    }

    /** 1.0.5-beta (lot 25) : cases d'eau minimales d'un bassin pour un requin. */
    public static final int SHARK_MIN_CELLS = 18;

    /** 1.0.5-beta (lot 25) : espece a silhouette de requin. */
    public static boolean shark(FishDef def) {
        return def.shape() >= 0 && def.shape() < GeneratedFish.SHAPES.length && "shark".equals(GeneratedFish.SHAPES[def.shape()]);
    }

    /** 1.0.5-beta (lot 25) : cases d'eau minimales du bassin pour cette espece (1, requin {@link #SHARK_MIN_CELLS}). */
    public static int minWetCells(FishDef def) {
        return shark(def) ? SHARK_MIN_CELLS : 1;
    }

    /** Le poisson tient-il ? ({@code used} : places deja prises dans le bassin.) */
    public static boolean fits(int used, int need, int wetCells) {
        return used + need <= capacity(wetCells);
    }
}
