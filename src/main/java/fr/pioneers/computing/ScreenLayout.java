package fr.pioneers.computing;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0.4-beta (lot 26, Guillaume : « l'ecran de supervision est tres buggue, il ne gere pas tres bien l'affichage », PJ : texte coupe ou tronque
 * selon la taille, cadres dedoubles) : MISE EN PAGE d'un ecran de supervision, sans aucune classe du client (le rendu
 * {@code RenderSupervisionScreen} execute le plan ; le banc {@code tools/screens/ScreenBench} l'execute avec les largeurs de la vraie police
 * pour les planches). Unites : {@link #UNIT} par bloc, origine en haut a gauche de l'ecran, y vers le bas. Regles :
 * <ul>
 *     <li>rien sous le cadre du bloc (1,5 px = {@link #FRAME}) : le contenu commence a {@link #INSET} ;</li>
 *     <li>le titre tient sur la largeur : taille de police reduite (jusqu'a 0,5), puis deux lignes, puis coupe avec « … » ;</li>
 *     <li>les textes longs (consigne de liaison) passent a la ligne a la plus grande taille qui les fait tenir dans la surface (0,35 au
 *     moins ; au-dela, coupes avec « … ») ;</li>
 *     <li>lignes libelle / valeur a la taille du mur (1 des 3 blocs de large, 0,85 a 2 blocs, 0,7 a 1 bloc), autant que la hauteur en tient ;
 *     graphe a droite (3 blocs de large et plus) ou dessous (2 blocs de haut et plus) ;</li>
 *     <li>mode texte (ordinateur) : au moins 8 lignes, coupees a la largeur.</li>
 * </ul>
 */
public final class ScreenLayout {

    public static final int UNIT = 64, FRAME = 6, INSET = 9, LINE = 9;
    public static final int BG = 0xFF07100E, PANEL = 0xFF0E1C1A, GRID = 0xFF16302B, ACCENT = 0xFF3FD0A0, TEXT = 0xFFCFF8E6, DIM = 0xFF6FA898,
          WARN = 0xFFE0A030, HOT = 0xFFE04848, TERM_BG = 0xFF030806, TERM_BAR = 0xFF123C2C, TERM_TEXT = 0xFF8CF5C6;
    static final String ELLIPSIS = "…";

    private ScreenLayout() {
    }

    /** Largeur d'un texte dans la police a la taille 1 (unites de l'ecran). */
    public interface Measure {
        int width(String s);
    }

    /** Aplat (couche : les suivantes passent devant). */
    public record Rect(float x0, float y0, float x1, float y1, int argb, float layer) {
    }

    /** Texte : coin haut gauche, taille de police. */
    public record Text(String s, float x, float y, float scale, int argb) {
    }

    /** Une ligne de valeur : libelle, valeur, remplissage de la jauge (0..1, ou < 0 sans jauge). */
    public record Row(String name, String value, double fill) {
    }

    /** Plan de dessin. */
    public static final class Plan {
        public final List<Rect> rects = new ArrayList<>();
        public final List<Text> texts = new ArrayList<>();
        /** Zone du graphe (x0, y0, x1, y1), ou null. */
        public float[] graph;
        /** Lignes de valeurs effectivement montrees. */
        public int rows;

        void rect(float x0, float y0, float x1, float y1, int argb, float layer) {
            if (x1 > x0 && y1 > y0) {
                rects.add(new Rect(x0, y0, x1, y1, argb, layer));
            }
        }

        void text(String s, float x, float y, float scale, int argb) {
            if (!s.isEmpty()) {
                texts.add(new Text(s, x, y, scale, argb));
            }
        }
    }

    // ------------------------------------------------------------------ outils de texte

    /** Coupe {@code s} a la largeur {@code max} (taille 1) en ajoutant « … » si besoin. */
    public static String clip(String s, float max, Measure m) {
        if (m.width(s) <= max) {
            return s;
        }
        int ew = m.width(ELLIPSIS);
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            String next = b.toString() + s.charAt(i);
            if (m.width(next) + ew > max) {
                break;
            }
            b.append(s.charAt(i));
        }
        return b.toString().stripTrailing() + ELLIPSIS;
    }

    /** Retour a la ligne a la largeur {@code max} (taille 1) : par mots, un mot trop long est coupe. */
    public static List<String> wrap(String s, float max, Measure m) {
        List<String> out = new ArrayList<>();
        for (String para : s.split("\n", -1)) {
            String line = "";
            for (String word : para.split(" ")) {
                if (word.isEmpty()) {
                    continue;
                }
                String cand = line.isEmpty() ? word : line + " " + word;
                if (m.width(cand) <= max) {
                    line = cand;
                    continue;
                }
                if (!line.isEmpty()) {
                    out.add(line);
                    line = "";
                }
                //mot plus long que la ligne : coupe aux caracteres
                while (m.width(word) > max && word.length() > 1) {
                    int k = 1;
                    while (k < word.length() && m.width(word.substring(0, k + 1)) <= max) {
                        k++;
                    }
                    out.add(word.substring(0, k));
                    word = word.substring(k);
                }
                line = word;
            }
            out.add(line);
        }
        return out;
    }

    /** Fond et cadre interieur communs : aplat sombre sous tout l'ecran. */
    private static void base(Plan p, int cw, int ch, int bg) {
        p.rect(2, 2, cw - 2, ch - 2, bg, 0);
    }

    /**
     * Titre : une ligne a la plus grande taille (1 a 0,5) qui le fait tenir, sinon deux lignes (0,75 a 0,5), sinon coupe ; bandeau et filet
     * d'accent dessous. Renvoie le bas du bandeau.
     */
    static float title(Plan p, String title, int cw, Measure m) {
        float aw = cw - 2 * INSET;
        int w = Math.max(1, m.width(title));
        float one = Math.min(1F, aw / w), two = 0;
        List<String> twoLines = null;
        for (float s = 0.75F; s >= 0.5F - 1e-4F && one < 0.75F; s -= 0.05F) {
            List<String> l = wrap(title, aw / s, m);
            if (l.size() <= 2) {
                twoLines = l;
                two = s;
                break;
            }
        }
        float scale;
        List<String> lines;
        if (one >= 0.75F || one >= 0.5F && one >= two) {
            scale = one;
            lines = List.of(title);
        } else if (twoLines != null) {
            scale = two;
            lines = twoLines;
        } else {
            scale = 0.5F;
            List<String> l = wrap(title, aw / scale, m);
            lines = l.size() <= 1 ? List.of(clip(title, aw / scale, m))
                  : List.of(l.get(0), clip(String.join(" ", l.subList(1, l.size())), aw / scale, m));
        }
        float y = INSET - 1, lh = LINE * scale;
        float bottom = y + lines.size() * lh + 3;
        p.rect(FRAME - 1, FRAME - 1, cw - FRAME + 1, bottom, PANEL, 0.1F);
        p.rect(FRAME - 1, bottom, cw - FRAME + 1, bottom + 1, ACCENT, 0.1F);
        for (int i = 0; i < lines.size(); i++) {
            p.text(lines.get(i), INSET, y + 1 + i * lh, scale, TEXT);
        }
        return bottom + 1;
    }

    /** Ecran sans liaison : titre et consigne a la plus grande taille qui tient dans la surface. */
    public static Plan unlinked(int cw, int ch, String title, String hint, Measure m) {
        Plan p = new Plan();
        base(p, cw, ch, BG);
        float top = title(p, title, cw, m) + 3;
        para(p, hint, INSET, top, cw - INSET, ch - INSET, 1F, DIM, m);
        return p;
    }

    /** Paragraphe a la plus grande taille (de {@code max} a 0,35) qui le fait tenir dans la boite ; sinon coupe avec « … ». */
    static void para(Plan p, String s, float x0, float y0, float x1, float y1, float max, int argb, Measure m) {
        float aw = x1 - x0, ah = y1 - y0;
        if (aw <= 4 || ah <= 3) {
            return;
        }
        float scale = 0.35F;
        List<String> lines = null;
        //le mot le plus long tient sur une ligne (pas de mot coupe tant que la taille le permet)
        int longest = 1;
        for (String word : s.split("[ \n]")) {
            longest = Math.max(longest, m.width(word));
        }
        float top = Math.max(0.35F, Math.min(max, aw / longest));
        for (float k = top; k >= 0.35F - 1e-4F; k -= 0.05F) {
            List<String> l = wrap(s, aw / k, m);
            if (l.size() * (LINE + 1) * k <= ah) {
                lines = l;
                scale = k;
                break;
            }
        }
        if (lines == null) {
            List<String> l = wrap(s, aw / scale, m);
            int fit = Math.max(1, (int) (ah / ((LINE + 1) * scale)));
            lines = new ArrayList<>(l.subList(0, Math.min(fit, l.size())));
            if (l.size() > fit) {
                lines.set(fit - 1, clip(lines.get(fit - 1) + " " + l.get(fit), aw / scale, m));
            }
        }
        for (int i = 0; i < lines.size(); i++) {
            p.text(lines.get(i), x0, y0 + i * (LINE + 1) * scale, scale, argb);
        }
    }

    /** Taille des lignes de valeurs selon la largeur de l'ecran. */
    static float rowScale(int cw) {
        return cw >= 3 * UNIT ? 1F : cw >= 2 * UNIT ? 0.85F : 0.7F;
    }

    /**
     * Ecran lie a une machine : titre, lignes libelle / valeur avec jauges, graphe des dernieres valeurs ({@code hist}, {@code graphName},
     * {@code graphValue}, bornes {@code hiText} / {@code loText}) quand l'ecran est assez grand.
     */
    public static Plan machine(int cw, int ch, String title, List<Row> rows, double[] hist, int history, String graphName, String graphValue,
          String hiText, String loText, Measure m) {
        Plan p = new Plan();
        base(p, cw, ch, BG);
        float top = title(p, title, cw, m) + 3;
        boolean wide = cw >= 3 * UNIT, tall = ch >= 2 * UNIT;
        boolean graph = !graphName.isEmpty() && (wide || tall);
        float rs = rowScale(cw);
        //ecran d'un bloc de large : libelle sur une ligne, valeur dessous (alignee a droite), jauge sous la valeur
        boolean stacked = cw < 2 * UNIT;
        float rowH = stacked ? 2 * LINE * rs + 4 : 13 * rs;
        float x0 = INSET, x1 = graph && wide ? Math.min(cw / 2F, 2 * UNIT) : cw - INSET;
        float listBottom = graph && !wide ? (top + ch - INSET) / 2 : ch - INSET;
        int n = Math.max(0, Math.min(rows.size(), (int) ((listBottom - top) / rowH)));
        p.rows = n;
        float room = x1 - x0;
        for (int i = 0; i < n; i++) {
            Row r = rows.get(i);
            float y = top + i * rowH;
            String val = clip(r.value(), (stacked ? room : room * 3 / 5) / rs, m);
            float vw = m.width(val) * rs;
            String name = clip(r.name(), (stacked ? room : room - vw - 4) / rs, m);
            p.text(name, x0, y, rs, DIM);
            p.text(val, x1 - vw, stacked ? y + LINE * rs : y, rs, TEXT);
            if (r.fill() >= 0) {
                float gy = (stacked ? y + LINE * rs : y) + 9 * rs + 0.5F;
                p.rect(x0, gy, x1, gy + 2, GRID, 0.1F);
                float x = (float) (x0 + (x1 - x0) * Math.max(0, Math.min(1, r.fill())));
                p.rect(x0, gy, x, gy + 2, r.fill() > 0.9 ? HOT : r.fill() > 0.75 ? WARN : ACCENT, 0.2F);
            }
        }
        if (graph) {
            float gx0 = wide ? x1 + 6 : INSET, gx1 = cw - INSET;
            float gy0 = (wide ? top : listBottom + 3) + 11, gy1 = ch - INSET;
            if (gy1 - gy0 >= 16 && gx1 - gx0 >= 24) {
                p.graph = new float[]{gx0, gy0, gx1, gy1};
                p.rect(gx0, gy0, gx1, gy1, PANEL, 0.1F);
                for (int k = 1; k < 4; k++) {
                    float y = gy0 + (gy1 - gy0) * k / 4;
                    p.rect(gx0 + 1, y, gx1 - 1, y + 1, GRID, 0.2F);
                }
                float gs = gx1 - gx0 >= 96 ? 0.85F : 0.7F;
                String gv = clip(graphValue, (gx1 - gx0 - 4) / gs, m);
                float vw = m.width(gv) * gs;
                p.text(gv, gx1 - 2 - vw, gy0 - 10, gs, TEXT);
                if (gx1 - gx0 - vw - 8 >= 24) {
                    //nom de la grandeur seulement s'il reste de la place (sinon : la valeur seule)
                    p.text(clip(graphName, (gx1 - gx0 - vw - 8) / gs, m), gx0 + 2, gy0 - 10, gs, DIM);
                }
                if (hist.length > 0) {
                    double lo = Double.MAX_VALUE, hi = -Double.MAX_VALUE;
                    for (double v : hist) {
                        lo = Math.min(lo, v);
                        hi = Math.max(hi, v);
                    }
                    if (hi - lo < 1e-6) {
                        hi = lo + 1;
                    }
                    float gh = gy1 - gy0 - 4, gw = gx1 - gx0 - 4;
                    float prev = -1;
                    for (int i = 0; i < hist.length; i++) {
                        float x = gx0 + 2 + Math.round(gw * (i + (history - hist.length)) / (double) (history - 1));
                        float y = gy1 - 2 - Math.round(gh * (hist[i] - lo) / (hi - lo));
                        float a = prev < 0 ? y : Math.min(prev, y), b = prev < 0 ? y : Math.max(prev, y);
                        p.rect(x, a, Math.min(gx1 - 1, x + 2), b + 2, ACCENT, 0.3F);
                        prev = y;
                    }
                    p.text(hiText, gx0 + 3, gy0 + 2, 0.6F, DIM);
                    p.text(loText, gx0 + 3, gy1 - 7, 0.6F, DIM);
                }
            }
        }
        return p;
    }

    /** Mode texte (ordinateur) : au moins 8 lignes visibles, coupees a la largeur ; liseré en haut. */
    public static Plan terminal(int cw, int ch, List<String> lines, Measure m) {
        Plan p = new Plan();
        base(p, cw, ch, TERM_BG);
        p.rect(FRAME - 1, FRAME - 1, cw - FRAME + 1, FRAME, TERM_BAR, 0.1F);
        int rows = Math.max(8, lines.size());
        float scale = Math.min(1F, (ch - 2 * INSET) / (rows * (float) LINE));
        float max = (cw - 2 * INSET) / scale;
        for (int i = 0; i < lines.size(); i++) {
            p.text(clip(lines.get(i), max, m), INSET, INSET + i * LINE * scale, scale, TERM_TEXT);
        }
        return p;
    }
}
