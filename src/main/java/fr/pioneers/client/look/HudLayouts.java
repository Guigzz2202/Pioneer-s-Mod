package fr.pioneers.client.look;

/**
 * 1.0.5-beta (lot 26, retour 16 : « point d'ancrage immuable ») : places PURES des affichages a l'ecran du mod dont la taille varie avec
 * leur contenu (aucune classe de Minecraft : banc {@code tools/look/AnchorBench.java}). Chacune passe par {@link HudAnchor} : le point
 * d'ancrage ne depend que de l'ecran et des reglages, la boite grandit vers le cote oppose a l'ancre et n'est ecretee que contre ce bord.
 * <p>
 * Recensement (toutes les fiches et infobulles POSEES par le mod a l'ecran ; les infobulles au curseur, vanilla ou du mod, restent au
 * curseur) :
 * <ul>
 *     <li>fiche au regard : {@code client/farm/FarmHud} ({@link CardPlacement#anchor}, point tenu par {@link CardPlacement.Latch}) ;</li>
 *     <li>minimap et ses lignes d'information : {@code client/minimap/MinimapHud} ({@link #minimap}) ;</li>
 *     <li>Periple : {@code client/guide/JourneyHud} ({@link #journey}) ;</li>
 *     <li>bandeau des messages du Carnet et gains d'XP / de jetons : {@code client/rp/Toasts} ({@link #toast}, {@link #popupLeft}) ;</li>
 *     <li>jauges d'equipement : {@code client/gear/GearHud} ({@link #gear}) ;</li>
 *     <li>message de survie (oxygene, combinaison) : {@code client/ClientSurvival} ({@link #survivalLine}) ;</li>
 *     <li>barre de lancer et de combat de la peche : {@code client/farm/fish/FishingHud} ({@link #fishingBar}) ;</li>
 *     <li>choix de la source / de la cible d'un ordre robotique : {@code client/robotics/ClientRobotics} ({@link #pickPanel}) ;</li>
 *     <li>bandeaux des evenements spatiaux : {@code client/space/ClientSpaceEvents} ({@link #banner}) ;</li>
 *     <li>altimetre des geantes gazeuses et altimetre / carburant de la fusee : {@code client/space/GiantClient},
 *     {@code client/space/RocketClientEvents} ({@link #gaugeLabel}) ;</li>
 *     <li>hors du recensement (ils suivent un point du MONDE, pas un point de l'ecran) : etiquettes des waypoints
 *     ({@code client/minimap/WaypointHud}) ; capsules d'oxygene de {@code ClientSurvival} (empilees sur les barres vanilla, comme elles).</li>
 * </ul>
 */
public final class HudLayouts {

    private HudLayouts() {
    }

    /** Ecart au bord du Periple (px de son echelle). */
    public static final int JOURNEY_OFF = 4;

    /**
     * Periple : boite {x0, y0, x1, y1} (px de l'echelle du Periple) de {@code w} x {@code h} ; en haut au centre, le milieu du bord haut est
     * sous les barres de boss ({@code top} = leur bas, 0 sans boss), jamais sous le bandeau des messages (qui se pose sous le Periple).
     */
    public static int[] journey(HudAnchor a, int sw, int sh, int w, int h, int top) {
        int ax = a.pointX(sw, JOURNEY_OFF), ay = a.pointY(sh, JOURNEY_OFF);
        if (a.v == 0) {
            ay = Math.max(ay, top + 2);
        }
        return a.box(ax, ay, w, h, sw, sh);
    }

    /**
     * Minimap : {xCarte, yCarte, haut des lignes, hauteur des lignes gardee} pour une carte de {@code size} px, cadre {@code border}, lignes
     * d'information de {@code infoH} px. Le point d'ancrage est le coin EXTERIEUR du cadre a ({@code ox}, {@code oy}) du coin de l'ecran : en
     * bas, la carte reste posee et les lignes s'empilent AU-DESSUS ; en haut, dessous. Seules les lignes sont ecretees (bord oppose).
     */
    public static int[] minimap(HudAnchor corner, int screenW, int screenH, int ox, int oy, int size, int border, int infoH) {
        int ax = corner.pointX(screenW, ox), ay = corner.pointY(screenH, oy);
        int frame = size + 2 * border;
        int[] fb = corner.box(ax, ay, frame, frame, screenW, screenH);
        int mapX = fb[0] + border, mapY = corner.v == 2 ? fb[3] - border - size : fb[1] + border;
        int room = corner.v == 2 ? Math.max(0, fb[1]) : Math.max(0, screenH - fb[3]);
        int keep = Math.min(Math.max(0, infoH), room);
        int infoTop = corner.v == 2 ? fb[1] - keep : fb[3];
        return new int[]{mapX, mapY, infoTop, keep};
    }

    /** Bandeau des messages du Carnet : boite {x0, y0, x1, y1}, milieu du bord haut fixe en ({@code sw / 2}, {@code top}). */
    public static int[] toast(int sw, int sh, int w, int h, int top) {
        return HudAnchor.TOP_CENTER.box(HudAnchor.TOP_CENTER.pointX(sw, 0), Math.max(0, top), w, h, sw, sh);
    }

    /** Ligne de gain (XP, jetons) au-dessus de la barre d'objets : bord gauche d'un texte de {@code w} px centre sur l'ecran. */
    public static int popupLeft(int sw, int w) {
        return HudAnchor.BOTTOM_CENTER.left(HudAnchor.BOTTOM_CENTER.pointX(sw, 0), w);
    }

    /** Jauges d'equipement : boite {x0, y0, x1, y1}, coin bas gauche fixe (4 px du bord gauche, 7 px du bas). */
    public static int[] gear(int sw, int sh, int w, int h) {
        return HudAnchor.BOTTOM_LEFT.place(w, h, sw, sh, 4, 7);
    }

    /** Message de survie : centre de la ligne (abscisse) et haut de la ligne, au-dessus de la barre d'experience. */
    public static int[] survivalLine(int sw, int sh) {
        return new int[]{HudAnchor.BOTTOM_CENTER.pointX(sw, 0), sh - 64};
    }

    /** Barre de la peche : bord gauche d'une barre de {@code w} px dont le milieu reste au milieu de l'ecran. */
    public static int fishingBar(int sw, int w) {
        return HudAnchor.CENTER.left(HudAnchor.CENTER.pointX(sw, 0), w);
    }

    /** Choix robotique : boite {x0, y0, x1, y1} sous la mire, milieu du bord haut fixe a 11 px sous le centre. */
    public static int[] pickPanel(int sw, int sh, int w, int h) {
        return HudAnchor.TOP_CENTER.box(sw / 2, sh / 2 + 11, w, h, sw, sh);
    }

    /** Bandeau d'evenement spatial : boite {x0, y0, x1, y1}, milieu du bord haut fixe en ({@code sw / 2}, {@code y}). */
    public static int[] banner(int sw, int sh, int w, int h, int y) {
        return HudAnchor.TOP_CENTER.box(HudAnchor.TOP_CENTER.pointX(sw, 0), y, w, h, sw, sh);
    }

    /** Etiquette d'une jauge verticale au bord droit : bord gauche d'un texte de {@code w} px dont le bord DROIT reste en {@code right}. */
    public static int gaugeLabel(int right, int w) {
        return HudAnchor.CENTER_RIGHT.left(right, w);
    }
}
