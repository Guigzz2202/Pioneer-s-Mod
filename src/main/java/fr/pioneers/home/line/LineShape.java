package fr.pioneers.home.line;

/**
 * 1.0.3-beta (lot 11, Guillaume : « largement ameliorer la texture du fil quand un item est accroche dessus (impression de levitation) ») :
 * forme de la corde a linge CHARGEE, sans aucun type de Minecraft (utilisee par le rendu {@code J:client/home/LineVisuals} et par le banc
 * des rendus {@code tools/kinetics}). La corde de {@link Clothesline#point} (chainette de fleche {@code sag}) flechit en plus a chaque piece :
 * une piece fait un V (lineaire de chaque cote, nul aux crochets ; {@link #DIP} sous une piece du milieu, moins pres des crochets, comme une
 * corde tendue chargee en un point) ; les V s'ajoutent, total borne a {@link #DIP_MAX}.
 */
public final class LineShape {

    /** Creux d'une piece au milieu de la corde (blocs : 1,2 px) ; creux total au plus 3,5 px. */
    public static final double DIP = 1.2 / 16, DIP_MAX = 3.5 / 16;

    private LineShape() {
    }

    /** Creux supplementaire (blocs, vers le bas) au point {@code t} (0..1) sous les pieces accrochees aux positions {@code loads}. */
    public static double dip(double t, double[] loads) {
        double y = 0;
        for (double ti : loads) {
            y += DIP * 4 * (t < ti ? t * (1 - ti) : ti * (1 - t));
        }
        return Math.min(DIP_MAX, y);
    }

    /** Hauteur de la corde chargee (meme unite que les extremites) a {@code t} : droite a-b, chainette, creux des pieces (x {@code unit}). */
    public static double y(double ay, double by, double sag, double t, double[] loads, double unit) {
        return ay + (by - ay) * t - sag * 4 * t * (1 - t) - dip(t, loads) * unit;
    }
}
