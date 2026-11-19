package fr.pioneers.space.sky.client.mega;

import java.util.Arrays;

/**
 * 1.0.7 (« ciel grandiose ») : tampon d'une image des megastructures en 3D, calcul pur (aucune classe de Minecraft : le banc du ciel le
 * dessine aussi). Repere du sol, camera a l'origine (x est, y haut, z sud), unites du ciel (la voute est a 100).
 * <ul>
 *     <li>faces PLEINES : quadrilateres deja eclaires (couleur finale), triees de la plus lointaine a la plus proche au dessin (peintre),
 *     les faces tournees vers l'arriere sont ecartees a l'ajout ;</li>
 *     <li>LUEURS : quadrilateres additifs dessines apres les faces pleines, avec une texture douce : POINTS (feux, hublots, halos ; degrade
 *     rond, sommets dans l'ordre (0,0) (1,0) (1,1) (0,1)) et TRAITS (faisceaux, cables, anneaux ; degrade en travers : u = 0 puis 1 sur
 *     les deux premiers sommets).</li>
 * </ul>
 * Tout est preallouee : aucune allocation par image (regles de performance) ; au-dela de la capacite, les faces sont ignorees.
 */
public final class MegaBuffer {

    /** Valeurs par quadrilatere : 4 sommets (x, y, z) puis r, g, b, a. */
    public static final int STRIDE = 16;
    public static final int SOLID_MAX = 6144, GLOW_MAX = 4096;

    public final float[] solid = new float[SOLID_MAX * STRIDE];
    public final float[] dots = new float[GLOW_MAX * STRIDE];
    public final float[] lines = new float[GLOW_MAX * STRIDE];
    /** Ordre de dessin des faces pleines : (distance en 1/1000 << 20) | indice, trie par distance decroissante au dessin. */
    private final long[] order = new long[SOLID_MAX];
    public int solidCount, dotCount, lineCount;
    /** Echelle appliquee a l'ajout (unites du modele vers unites du ciel : les angles ne changent pas, la voute reste a portee). */
    public double scale = 1;

    public void clear() {
        scale = 1;
        solidCount = 0;
        dotCount = 0;
        lineCount = 0;
    }

    public void solid(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3,
          double z3, float r, float g, float b, float a) {
        if (solidCount >= SOLID_MAX) {
            return;
        }
        put(solid, solidCount * STRIDE, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        double cx = (x0 + x1 + x2 + x3) / 4, cy = (y0 + y1 + y2 + y3) / 4, cz = (z0 + z1 + z2 + z3) / 4;
        long d = (long) (Math.sqrt(cx * cx + cy * cy + cz * cz) * scale * 1000);
        order[solidCount] = (d << 20) | solidCount;
        solidCount++;
    }

    public void dot(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3,
          double z3, float r, float g, float b, float a) {
        if (dotCount >= GLOW_MAX || a <= 0.004F) {
            return;
        }
        put(dots, dotCount * STRIDE, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        dotCount++;
    }

    public void line(double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2, double x3, double y3,
          double z3, float r, float g, float b, float a) {
        if (lineCount >= GLOW_MAX || a <= 0.004F) {
            return;
        }
        put(lines, lineCount * STRIDE, x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, r, g, b, a);
        lineCount++;
    }

    private void put(float[] t, int o, double x0, double y0, double z0, double x1, double y1, double z1, double x2, double y2, double z2,
          double x3, double y3, double z3, float r, float g, float b, float a) {
        double s = scale;
        t[o] = (float) (x0 * s);
        t[o + 1] = (float) (y0 * s);
        t[o + 2] = (float) (z0 * s);
        t[o + 3] = (float) (x1 * s);
        t[o + 4] = (float) (y1 * s);
        t[o + 5] = (float) (z1 * s);
        t[o + 6] = (float) (x2 * s);
        t[o + 7] = (float) (y2 * s);
        t[o + 8] = (float) (z2 * s);
        t[o + 9] = (float) (x3 * s);
        t[o + 10] = (float) (y3 * s);
        t[o + 11] = (float) (z3 * s);
        t[o + 12] = r;
        t[o + 13] = g;
        t[o + 14] = b;
        t[o + 15] = a;
    }

    /** Trie les faces pleines (la plus lointaine d'abord). */
    public void sort() {
        Arrays.sort(order, 0, solidCount);
    }

    /** Indice de la {@code k}-ieme face pleine a dessiner (apres {@link #sort()}). */
    public int solidAt(int k) {
        return (int) (order[solidCount - 1 - k] & 0xFFFFF);
    }
}
