package fr.pioneers.tech.industry.client.big;

/**
 * 1.0.6 (17/11/2026, Guillaume : « la decoupeuse est censee representer un bras d'imprimante 3D qui decoupe en gros ») : PORTIQUE DE
 * DECOUPE de la Decoupeuse ({@code gear_hobber}), dessine dans le volume de travail de sa structure (3 x 3 x 3 cases sous la charpente,
 * T:industry/nbt_models.py). Repere de {@link BigMesh} (px) : x en travers, y en haut, z le long de l'axe.
 * <ul>
 *     <li>fixe : banc de fonte a rainures en T, rails de guidage le long de z sous la charpente du haut ;</li>
 *     <li>anime : poutre du portique qui roule le long de z, chariot qui court sur la poutre (x), torche qui descend, decoupe le contour
 *     d'un engrenage dans la tole posee sur le banc (saignee qui se trace, metal encore rouge juste derriere la torche, gerbe
 *     d'etincelles), remonte puis revient au point de repos ; la tole suivante remplace la piece decoupee.</li>
 * </ul>
 */
final class Lot12 {

    /** Cycle (unites de phase : 1 par tick a plein regime) et ses etapes. */
    private static final float CYCLE = 240, GO = 30, DOWN = 45, CUT = 205, UP = 220;
    /** Hauteurs (px) : dessus du banc, dessus de la tole, bout de la torche en haut et a la coupe. */
    private static final float BED = 6.5F, SHEET = 8, TIP_UP = 20, TIP_CUT = SHEET + 0.6F;
    /** Contour decoupe : rayon moyen, hauteur des dents, nombre de dents, segments de la saignee. */
    private static final float R = 7.5F, TOOTH = 1.3F;
    private static final int TEETH = 10, SEGMENTS = 120;

    private Lot12() {
    }

    // ------------------------------------------------------------------ partie fixe

    static void gantry(BigMesh m, float w, float h, float l) {
        //banc : socle de fonte, plateau d'acier, rainures en T le long de z
        m.box(4, 0, 4, w - 4, BED - 1.5F, l - 4, BigMat.CAST, null, 4);
        m.box(3, BED - 1.5F, 3, w - 3, BED, l - 3, BigMat.STEEL_D, null, 4);
        for (float x = 8; x < w - 7; x += 8) {
            m.box(x - 0.6F, BED, 3.5F, x + 0.6F, BED + 0.05F, l - 3.5F, BigMat.CAST, null, 1);
        }
        //rails de guidage du portique (le long de z), sous la charpente du haut
        float top = h - 4;
        for (float x : new float[]{1.5F, w - 4.5F}) {
            m.box(x, top - 3, 1, x + 3, top, l - 1, BigMat.CAST_L, null, 4);
            m.box(x + 1, top, 1, x + 2, top + 1, l - 1, BigMat.CHROME, null, 2);
        }
        //chaine porte-cables couchee le long du rail de droite
        m.box(w - 9, top - 2, 2, w - 6, top - 0.5F, l - 2, BigMat.RUBBER, null, 4);
    }

    // ------------------------------------------------------------------ partie animee

    /** Point du contour a l'angle {@code th} (dents arrondies) : {x, z}. */
    private static float[] contour(float cx, float cz, double th) {
        double s = Math.sin(TEETH * th);
        double r = R + TOOTH * Math.max(-1, Math.min(1, s * 2.5));
        return new float[]{cx + (float) (r * Math.cos(th)), cz + (float) (r * Math.sin(th))};
    }

    static void gantryLife(BigMesh m, float w, float h, float l, BigMachines.Anim a) {
        float cx = w / 2, cz = l / 2;
        float t = a.phase % CYCLE;
        //angle de coupe atteint (0 .. 2 pi), position de la torche (x, z) et bout de la torche (y)
        double done;
        float hx, hz, tip;
        float[] start = contour(cx, cz, 0);
        if (t < GO) {
            float u = Lot4.smooth(t / GO);
            done = 0;
            hx = cx + (start[0] - cx) * u;
            hz = cz + (start[1] - cz) * u;
            tip = TIP_UP;
        } else if (t < DOWN) {
            done = 0;
            hx = start[0];
            hz = start[1];
            tip = TIP_UP + (TIP_CUT - TIP_UP) * Lot4.smooth((t - GO) / (DOWN - GO));
        } else if (t < CUT) {
            done = 2 * Math.PI * (t - DOWN) / (CUT - DOWN);
            float[] p = contour(cx, cz, done);
            hx = p[0];
            hz = p[1];
            tip = TIP_CUT;
        } else if (t < UP) {
            done = 2 * Math.PI;
            hx = start[0];
            hz = start[1];
            tip = TIP_CUT + (TIP_UP - TIP_CUT) * Lot4.smooth((t - CUT) / (UP - CUT));
        } else {
            float u = Lot4.smooth((t - UP) / (CYCLE - UP));
            done = 2 * Math.PI;
            hx = start[0] + (cx - start[0]) * u;
            hz = start[1] + (cz - start[1]) * u;
            tip = TIP_UP;
        }
        boolean cutting = t >= DOWN && t < CUT && a.speed > 0.3F;
        //tole sur le banc, et saignee tracee jusqu'a la torche (les derniers segments encore rouges)
        m.color(0xFFB8C0C8);
        m.box(cx - 11, BED, cz - 11, cx + 11, SHEET, cz + 11, BigMat.STRIP, null, 4);
        m.color(0xFFFFFFFF);
        int last = (int) Math.floor(done / (2 * Math.PI) * SEGMENTS);
        for (int k = 0; k < Math.min(last, SEGMENTS); k++) {
            float[] p = contour(cx, cz, 2 * Math.PI * (k + 0.5) / SEGMENTS);
            boolean hot = cutting && last - k <= 5;
            if (hot) {
                m.glow(12).color(0xFF000000 | heat(1 - (last - k) / 6F));
                m.box(p[0] - 0.45F, SHEET - 0.02F, p[1] - 0.45F, p[0] + 0.45F, SHEET + 0.08F, p[1] + 0.45F, BigMat.MOLTEN, null, 1);
                m.glow(0).color(0xFFFFFFFF);
            } else {
                m.color(0xFF1E2024);
                m.box(p[0] - 0.5F, SHEET - 0.02F, p[1] - 0.5F, p[0] + 0.5F, SHEET + 0.06F, p[1] + 0.5F, BigMat.STEEL_D, null, 1);
                m.color(0xFFFFFFFF);
            }
        }
        //portique : poutre en travers (x) qui roule le long de z, sabots sur les rails, chariot sur la poutre
        float top = h - 4;
        m.box(2, top - 7, hz - 2.5F, w - 2, top - 4, hz + 2.5F, BigMat.PAINT_O, null, 4);
        for (float x : new float[]{1, w - 5}) {
            m.box(x, top - 4, hz - 4, x + 4, top - 1.5F, hz + 4, BigMat.CAST, null, 4);
        }
        m.box(hx - 4, top - 9, hz - 4.5F, hx + 4, top - 2.5F, hz - 2.5F, BigMat.CAST_L, null, 4);
        m.box(hx - 3.5F, top - 9, hz - 2.5F, hx + 3.5F, top - 7, hz + 2.5F, BigMat.CAST_L, null, 4);
        //coulisseau vertical et torche (corps du moteur, buse, bout)
        float body = tip + 5;
        m.box(hx - 1.6F, body, hz - 4.4F, hx + 1.6F, top - 7, hz - 3.6F, BigMat.CHROME, null, 2);
        m.cyl('y', hx, hz, 2.1F, body, top - 7, BigMat.MOTOR, 10, null, 4);
        m.cyl('y', hx, hz, 0.9F, tip + 1, body, BigMat.CHROME, 8, null, 2);
        m.cyl('y', hx, hz, 0.5F, tip, tip + 1, BigMat.CAST, 6, null, 1);
        if (cutting) {
            //arc et gerbe d'etincelles (meme dessin que la soudeuse : coeur blanc, grains jaunes qui jaillissent et retombent)
            float fl = 0.6F + 0.5F * BigMachines.hash((int) (a.phase * 3), 11);
            m.glow(15).color(0xFFE8F4FF);
            m.box(hx - fl * 0.6F, SHEET, hz - fl * 0.6F, hx + fl * 0.6F, SHEET + fl, hz + fl * 0.6F, BigMat.LED, null, 1);
            for (int c = 0; c < 10; c++) {
                float ph = (a.phase * 0.35F + c * 0.1F) % 1F;
                double an = c * 0.628 + (int) (a.phase * 0.35F + c * 0.1F) * 1.7;
                float x = hx + (float) Math.cos(an) * ph * 5, z = hz + (float) Math.sin(an) * ph * 5,
                      y = SHEET + 0.4F + ph * 3 - ph * ph * 3.6F;
                m.color(c % 3 == 0 ? 0xFFFFF0C0 : 0xFFFFC060);
                m.box(x - 0.2F, Math.max(SHEET, y), z - 0.2F, x + 0.2F, Math.max(SHEET, y) + 0.4F, z + 0.2F, BigMat.LED, null, 1);
            }
            m.glow(0).color(0xFFFFFFFF);
        }
    }

    /** Couleur du metal chaud (1 : jaune blanc, 0 : rouge sombre), RGB. */
    private static int heat(float k) {
        k = Lot4.clamp01(k);
        int r = 255, g = (int) (60 + 170 * k), b = (int) (20 + 120 * k * k);
        return r << 16 | g << 8 | b;
    }
}
