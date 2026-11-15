package fr.pioneers.forge;

/**
 * 1.0.5-beta (lot 19, relecture du lot 15 : « la plaque d'acier a 1 350 degres redevient gris terne, plus sombre qu'a 950 ») : COULEUR D'UNE
 * PIECE CHAUFFEE, PURE (rendu {@code J:client/forge/HeatedMetalItemRenderer}, banc et planche du lot 19).
 * <p>
 * Avant : teinte multiplicative vers la couleur d'incandescence + UNE passe additive ; les deux multiplient la texture : une texture sombre
 * (acier, fer) plafonnait a 2 x sa clarte et le blanc de 1 350 degres la rendait grise. Maintenant la piece vise une CLARTE CIBLE qui monte
 * toujours avec la temperature ({@link #targetLum}), quelle que soit la clarte moyenne de sa texture ({@code meanLum}) :
 * facteur par canal = melange de 1 (la vraie texture) et de (cible / clarte de la texture) x teinte de l'incandescence, dose par l'intensite
 * de l'incandescence ({@link ForgeMetals#glow}), jamais sous le dosage du lot 15 ({@link #lot15}, qui allait bien aux textures claires). Le rendu realise ce facteur en une passe teintee (facteurs &lt;= 1) puis autant de passes
 * additives que necessaire ({@link #passes}, au plus {@link #MAX_ADD}) : la texture reste visible, la piece blanchit en chauffant.
 */
public final class HeatGlow {

    /** Passes additives au plus (une texture tres sombre, clarte 0,1, a 1 400 degres en demande 8). */
    public static final int MAX_ADD = 8;
    /** Clarte moyenne minimale prise en compte (une texture presque noire n'explose pas le facteur). */
    public static final float MIN_LUM = 0.10F;
    /** Clarte cible (0..1) aux temperatures de reference : rouge sombre, cerise, orange, jaune, blanc. */
    static final float[] T = {450F, 650F, 850F, 1100F, 1400F};
    static final float[] LUM = {0.16F, 0.33F, 0.58F, 0.80F, 0.94F};

    private HeatGlow() {
    }

    /** Clarte cible d'une piece a {@code tempC} (toujours croissante ; 0 sous 450 degres : pas d'incandescence). */
    public static float targetLum(float tempC) {
        if (tempC < T[0]) {
            return 0F;
        }
        for (int i = 1; i < T.length; i++) {
            if (tempC <= T[i]) {
                float u = (tempC - T[i - 1]) / (T[i] - T[i - 1]);
                return LUM[i - 1] + (LUM[i] - LUM[i - 1]) * u;
            }
        }
        return LUM[LUM.length - 1];
    }

    /** Clarte (Rec. 709) d'une couleur RGB 0..1. */
    public static float lum(float r, float g, float b) {
        return 0.2126F * r + 0.7152F * g + 0.0722F * b;
    }

    /**
     * Facteur RGB (par canal, &gt;= 0) a appliquer a la texture d'une piece a {@code tempC} dont la clarte moyenne (pixels opaques) est
     * {@code meanLum} : {1, 1, 1} sous 450 degres.
     */
    public static float[] factor(float tempC, float meanLum) {
        int glow = ForgeMetals.glow(tempC);
        float a = (glow >>> 24) / 255F;
        if (a <= 0) {
            return new float[]{1F, 1F, 1F};
        }
        float r = ((glow >> 16) & 0xFF) / 255F, g = ((glow >> 8) & 0xFF) / 255F, b = (glow & 0xFF) / 255F;
        float gl = Math.max(0.05F, lum(r, g, b));
        float k = targetLum(tempC) / Math.max(MIN_LUM, meanLum);
        float[] f = {1F - a + a * k * r / gl, 1F - a + a * k * g / gl, 1F - a + a * k * b / gl};
        //plancher : le dosage du lot 15 (teinte 75 % + passe additive 90 %), deja bien lumineux sur une texture claire (et sur un modele 3D
        //dont les faces ombrees sont plus sombres que la moyenne de sa texture)
        float[] low = lot15(a, r, g, b);
        for (int c = 0; c < 3; c++) {
            f[c] = Math.max(f[c], low[c]);
        }
        return f;
    }

    /** Facteur du lot 15 : teinte multiplicative a 75 % vers la couleur ramenee a sa composante la plus forte, plus 90 % de la couleur. */
    static float[] lot15(float a, float r, float g, float b) {
        float t = a * 0.75F, m = Math.max(1F / 255F, Math.max(r, Math.max(g, b))), add = a * 0.9F;
        return new float[]{1F - t + t * r / m + add * r, 1F - t + t * g / m + add * g, 1F - t + t * b / m + add * b};
    }

    /**
     * Decoupe un facteur en passes de rendu : la premiere est la teinte (multiplicative, &lt;= 1 par canal), les suivantes s'AJOUTENT a l'image
     * (memes quadrilateres, chacune &lt;= 1 par canal), au plus {@link #MAX_ADD}. La somme des passes vaut le facteur (borne a
     * 1 + {@link #MAX_ADD}).
     */
    public static float[][] passes(float[] f) {
        float[] tint = {Math.min(1F, f[0]), Math.min(1F, f[1]), Math.min(1F, f[2])};
        float rest = 0F;
        float[] over = new float[3];
        for (int c = 0; c < 3; c++) {
            over[c] = Math.min(MAX_ADD, Math.max(0F, f[c] - 1F));
            rest = Math.max(rest, over[c]);
        }
        int n = rest <= 0.004F ? 0 : (int) Math.ceil(rest - 1E-4F);
        float[][] out = new float[1 + n][];
        out[0] = tint;
        for (int i = 1; i <= n; i++) {
            out[i] = new float[]{over[0] / n, over[1] / n, over[2] / n};
        }
        return out;
    }

    /** Couleur finale (0..1, bornee) d'un texel {@code rgb} rendu avec ces passes (banc, planche). */
    public static float[] shade(float[] rgb, float[][] passes) {
        float[] out = new float[3];
        for (float[] p : passes) {
            for (int c = 0; c < 3; c++) {
                out[c] += rgb[c] * p[c];
            }
        }
        for (int c = 0; c < 3; c++) {
            out[c] = Math.min(1F, out[c]);
        }
        return out;
    }
}
