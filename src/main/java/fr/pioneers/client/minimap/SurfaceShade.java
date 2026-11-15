package fr.pioneers.client.minimap;

/**
 * 1.10.3 : couleur finale des 16 x 16 colonnes d'un chunk, SANS classe de Minecraft (le banc l'execute). Entrees remplies par
 * {@link ChunkScanner} : couleur de base RGB de la colonne (couleur de carte du bloc, teintee par le biome), hauteur du dessus, profondeur
 * d'eau, couleur du fond sous l'eau ; hauteurs de la rangee nord et de la colonne ouest voisines (chunks voisins, ou la rangee du bord si
 * le voisin manque). Sortie : pixels ABGR (format de {@code NativeImage}), opaques.
 * <p>
 * Ombrage du relief : comme les cartes vanilla, une colonne plus haute que sa voisine du nord est eclairee, plus basse assombrie ; la
 * voisine de l'ouest compte pour moitie (lumiere venant du nord-ouest), le tout borne pour ne jamais ecraser les couleurs. Eau : teinte
 * de l'eau du biome, plus sombre avec la profondeur ; sur les hauts-fonds (1 a 2 blocs), le fond transparait.
 */
public final class SurfaceShade {

    private SurfaceShade() {
    }

    /** Colonne sans bloc (vide de l'End, d'une station orbitale) : presque noir bleute, opaque. */
    public static final int VOID_RGB = 0x07090E;

    /**
     * @param base     couleur de base RGB par colonne (index z * 16 + x)
     * @param height   hauteur du dessus par colonne ({@link Integer#MIN_VALUE} : colonne vide)
     * @param depth    profondeur d'eau (0 : a sec)
     * @param floor    couleur RGB du fond sous l'eau (lue seulement si depth &gt; 0)
     * @param north    hauteurs de la rangee z = -1 (16 valeurs)
     * @param west     hauteurs de la colonne x = -1 (16 valeurs)
     * @param out      pixels ABGR (256)
     */
    public static void shade(int[] base, int[] height, int[] depth, int[] floor, int[] north, int[] west, boolean relief, boolean waterDepth,
          int[] out) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int i = z * 16 + x;
                int h = height[i];
                if (h == Integer.MIN_VALUE) {
                    out[i] = abgr(VOID_RGB);
                    continue;
                }
                int rgb = base[i];
                int d = depth[i];
                float k = 1F;
                if (d > 0) {
                    if (waterDepth) {
                        k = d <= 1 ? 1.06F : d <= 2 ? 1F : d <= 5 ? 0.9F : d <= 10 ? 0.8F : 0.72F;
                        if (d <= 2) {
                            rgb = mix(rgb, floor[i], d <= 1 ? 0.45F : 0.25F);
                        }
                    }
                } else if (relief) {
                    int hn = z > 0 ? height[i - 16] : north[x];
                    int hw = x > 0 ? height[i - 1] : west[z];
                    if (hn == Integer.MIN_VALUE) {
                        hn = h;
                    }
                    if (hw == Integer.MIN_VALUE) {
                        hw = h;
                    }
                    float slope = (h - hn) + 0.5F * (h - hw);
                    k = 1F + Math.max(-0.24F, Math.min(0.2F, slope * 0.07F));
                }
                out[i] = abgr(k == 1F ? rgb : scale(rgb, k));
            }
        }
    }

    public static int scale(int rgb, float k) {
        int r = Math.min(255, Math.round(((rgb >> 16) & 255) * k));
        int g = Math.min(255, Math.round(((rgb >> 8) & 255) * k));
        int b = Math.min(255, Math.round((rgb & 255) * k));
        return r << 16 | g << 8 | b;
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round(((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = Math.round(((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = Math.round((a & 255) * (1 - t) + (b & 255) * t);
        return r << 16 | g << 8 | bl;
    }

    /** Multiplie deux couleurs RGB (teinte du biome sur un gris de carte). */
    public static int multiply(int a, int b) {
        int r = ((a >> 16) & 255) * ((b >> 16) & 255) / 255;
        int g = ((a >> 8) & 255) * ((b >> 8) & 255) / 255;
        int bl = (a & 255) * (b & 255) / 255;
        return r << 16 | g << 8 | bl;
    }

    /** RGB -&gt; ABGR opaque (pixels de {@code NativeImage}). */
    public static int abgr(int rgb) {
        return 0xFF000000 | (rgb & 255) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 255;
    }

    /** RGB + alpha -&gt; ABGR. */
    public static int abgr(int rgb, int alpha) {
        return (alpha & 255) << 24 | (rgb & 255) << 16 | (rgb & 0xFF00) | (rgb >> 16) & 255;
    }

    /** ABGR -&gt; RGB. */
    public static int rgb(int abgr) {
        return (abgr & 255) << 16 | (abgr & 0xFF00) | (abgr >> 16) & 255;
    }
}
