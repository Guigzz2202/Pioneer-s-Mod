package fr.pioneers.tech.forge.smithing;

/**
 * 1.0.3-beta (lot 14, Guillaume : « il faut que l'on puisse poser le porte-outils au mur et que la texture s'adapte ; de plus les outils sont
 * assez mal affiches depuis la mise a jour d'orientation ») : PLACE de chaque outil du porte-outils, sans Minecraft (lue par
 * {@code J:tech/forge/client/ToolRackRenderer} et par le banc du lot, {@code scratchpad/p103/lot14/bench/RackDump.java}).
 * <p>
 * Repere : modele FACADE AU SUD (z = 16 cote joueur), px du bloc. L'outil est d'abord mis DEBOUT de face (vue FIXED de l'objet puis
 * {@link #ZTURN}) ; {@code bounds} = son encombrement dans ce repere debout, en px a l'echelle 1 (minX, maxX, minY, maxY), mesure sur son
 * vrai modele. Causes du mauvais rendu d'avant : outils penches VERS le joueur (+8 deg) au lieu de s'appuyer sur la traverse du fond, tetes
 * de 8 px de large tous les 4,5 px (elles se chevauchaient), taille unique quelle que soit la largeur de l'outil.
 * <ul>
 *   <li>Emplacements a {@link #SLOT_X} 3 / 8 / 13 ; l'outil du MILIEU est {@link #STAGGER} px plus bas : les tetes voisines ne sont jamais a
 *   la meme hauteur (elles ne se chevauchent pas) ; echelle = au plus {@link #SCALE_MAX}, reduite pour que la tete tienne entre les manches
 *   voisins ({@link #HEAD_ROOM}) et que l'outil tienne dans la hauteur ;</li>
 *   <li>AU SOL : pied pose dans l'encoche de la traverse avant (y {@link #FOOT_Y}, celle du milieu jusqu'a la semelle), outil penche vers
 *   l'arriere jusqu'a la traverse haute (angle calcule : le manche touche son appui, rien ne traverse le bois) ;</li>
 *   <li>AU MUR : outil suspendu, presque vertical (penche de {@link #WALL_LEAN} deg contre le mur), dans le plan {@link #WALL_Z} devant la
 *   planche du fond, la tete au-dessus de ses deux chevilles ({@link #pegY}).</li>
 * </ul>
 */
public final class ToolRackLayout {

    /** Placement : translation (px), inclinaison autour de x (degres ; negatif = le haut vers le fond), echelle de l'outil. */
    public record Place(float x, float y, float z, float lean, float scale) {
    }

    public static final float[] SLOT_X = {3F, 8F, 13F};
    /** Quart de tour (degres, autour de z) qui met un outil du mod (dessine en diagonale, vue FIXED) debout, tete en haut. */
    public static final float ZTURN = -45F;
    public static final float SCALE_MAX = 0.48F, STAGGER = 3.5F;
    /** Largeur permise a une tete : jusqu'aux manches des voisins (5 px de chaque cote, moins le demi-manche et une marge). */
    public static final float HEAD_ROOM = 2 * (5F - 0.9F);
    // ------------------------------------------------------------------ au sol (memes chiffres que T:home/racks.py)
    /** Pied : dessus des encoches (cotes) et de la semelle (milieu) ; profondeur du pied (milieu de l'encoche). */
    public static final float FOOT_Y = 4.6F, FOOT_Y_MID = 1.6F, FOOT_Z = 10.7F;
    /** Appui du haut : devant des crochets (cotes) / de la traverse haute (milieu). */
    public static final float REST_Y = 12.2F, REST_Z = 7.9F, REST_Y_MID = 10.6F, REST_Z_MID = 7.9F;
    /** Hauteur maximale d'un outil au sol (px). */
    public static final float FLOOR_MAX_H = 14F;
    // ------------------------------------------------------------------ au mur
    /** Plan des outils (devant la planche du fond, entre les chevilles) ; haut des outils (cotes, milieu) ; inclinaison contre le mur. */
    public static final float WALL_Z = 2.9F, WALL_TOP = 15.4F, WALL_LEAN = -4F;
    /** Planche du fond (z 0..1) et chevilles (z 1..4.2) : T:home/racks.py les lit ici. */
    public static final float WALL_BOARD = 1F, PEG_Z = 4.2F, PEG_DX = 1.3F;

    private ToolRackLayout() {
    }

    /** Hauteur des chevilles de l'emplacement {@code slot} (px) : 3 px sous le haut de l'outil. */
    public static float pegY(int slot) {
        return WALL_TOP - (slot == 1 ? STAGGER : 0) - 3F;
    }

    /** Echelle d'un outil d'encombrement {@code bounds} pour une hauteur permise {@code maxH} (px). */
    public static float scale(float[] bounds, float maxH) {
        //largeur comptee de part et d'autre du MANCHE (x = 0 du repere debout) : une tete de hache d'un seul cote compte double
        float w = Math.max(1, 2 * Math.max(Math.abs(bounds[0]), Math.abs(bounds[1]))), h = Math.max(1, bounds[3] - bounds[2]);
        return Math.min(SCALE_MAX, Math.min(HEAD_ROOM / w, maxH / h));
    }

    /** Place de l'outil de l'emplacement {@code slot} (0..2) ; {@code wall} : porte-outils mural. */
    public static Place place(boolean wall, int slot, float[] bounds) {
        float x = SLOT_X[slot];
        if (wall) {
            float top = WALL_TOP - (slot == 1 ? STAGGER : 0);
            float s = scale(bounds, top - 0.8F);
            //le haut de l'outil (maxY) au niveau voulu ; l'inclinaison est petite : on la compte sur la hauteur
            double a = Math.toRadians(WALL_LEAN);
            float y = (float) (top - bounds[3] * s * Math.cos(a)), z = (float) (WALL_Z - bounds[3] * s * Math.sin(a));
            return new Place(x, y, z, WALL_LEAN, s);
        }
        boolean mid = slot == 1;
        float footY = mid ? FOOT_Y_MID : FOOT_Y, restY = mid ? REST_Y_MID : REST_Y, restZ = mid ? REST_Z_MID : REST_Z;
        float lean = (float) -Math.toDegrees(Math.atan2(FOOT_Z - restZ, restY - footY));
        float s = scale(bounds, FLOOR_MAX_H - footY + 1);
        //le bas de l'outil (minY) pose sur le pied : rotation autour de x de lean, (0, y, 0) -> (0, y cos, y sin)
        double a = Math.toRadians(lean);
        float y = (float) (footY - bounds[2] * s * Math.cos(a)), z = (float) (FOOT_Z - bounds[2] * s * Math.sin(a));
        return new Place(x, y, z, lean, s);
    }
}
