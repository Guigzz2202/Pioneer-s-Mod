package fr.pioneers.client.pet;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToIntFunction;

/**
 * Mise en page et DESSIN PURS de la roue du rappel ({@link PetWheelScreen}), sans classe de Minecraft : la roue construit sa liste de dessin
 * ({@link #paint}) que l'ecran rejoue telle quelle (quadrilateres, icones, textes) et que le banc {@code tools/pets/WheelBench.java} trame
 * aux vraies tailles de fenetre (vraie {@code Window.calculateScale}) pour la planche et les controles.
 * <p>
 * 1.0.5-beta (lot 13) : cases rectangulaires sur une ELLIPSE (rayon horizontal jusqu'a 200 px pour 122 en hauteur : 1,6 a 1,9 fois plus
 * large que haute aux tailles courantes), fond en bandes d'un pixel d'interface (marches de 2 a 4 pixels d'ecran) : « un ovale tout moche »
 * (retour de Guillaume du 11/11/2026).
 * <p>
 * 1.0.5-beta (lot 21) : une vraie ROUE : anneau circulaire (les pixels d'interface sont carres, l'echelle d'interface de Minecraft est
 * entiere : un cercle en coordonnees d'interface est un cercle a l'ecran), un SECTEUR par animal (separes d'un petit jour), icone et nom
 * dans le secteur, secteur vise eclaire et souligne d'un arc d'accent, MOYEU au centre (titre, ou nom et distance de l'animal vise ;
 * relacher dans le moyeu = annuler), points de page. Les arcs sont des quadrilateres fins (128 par tour) poses en coordonnees flottantes :
 * le contour est trace a la resolution de l'ecran, sans marches.
 */
public final class WheelLayout {

    /** Hauteur reservee en bas a la consigne, marge au bord de l'ecran, rayon exterieur borne, secteurs par page au plus. */
    public static final int HINT_H = 16, MARGIN = 6, MIN_OUTER = 56, MAX_OUTER = 120, MAX_PER_PAGE = 12;
    /** Corde minimale d'un secteur au rayon moyen (icone 16 px + nom), trou central (fraction du rayon exterieur), quadrilateres par tour. */
    public static final int MIN_ARC = 34, SEGS = 128, ICON = 16;
    public static final double HOLE = 0.46, GAP = 2.0, BACK_PAD = 5.0, RIM = 2.5;
    /** Couleurs : fond, secteur, secteur vise, bord, accent, texte, texte pale, distance d'un animal charge, moyeu. */
    public static final int BACK = 0xB00B1016, CELL = 0xD0182028, CELL_ON = 0xF0304656, EDGE = 0xFF2E3A44, ACCENT = 0xFF3FD0A0,
          TEXT = 0xFFE0F5EA, DIM = 0xFF8FA3A0, NEAR = 0xFF8CF5C6, HUB = 0xE00E1318;

    /** Quadrilatere (4 sommets x, y, dans le sens des aiguilles d'une montre a l'ecran, comme {@code GuiGraphics.fill}) et sa couleur ARGB. */
    public record Quad(float[] xy, int color) {
    }

    /** Icone de l'espece (coin haut-gauche, echelle : 16 px x echelle). */
    public record Icon(String type, float x, float y, float scale) {
    }

    /** Texte (coin haut-gauche, echelle, couleur ARGB), deja ajuste a sa place. */
    public record Text(String text, float x, float y, float scale, int color) {
    }

    /** Une case de la roue : type d'entite, nom, ligne « ou » (distance ou dimension), animal charge (distance en vert). */
    public record Cell(String type, String name, String where, boolean loaded) {
    }

    public final int width, height, cx, cy, outer, inner, hub, count, perPage, pages;
    /** Rayon moyen de l'anneau (centre des icones). */
    public final double mid;

    private WheelLayout(int width, int height, int count) {
        this.width = width;
        this.height = height;
        this.count = count;
        this.cx = width / 2;
        this.cy = (height - HINT_H) / 2;
        this.outer = Math.max(MIN_OUTER, Math.min(MAX_OUTER, Math.min(cx, cy) - MARGIN));
        this.inner = Math.max(30, (int) Math.round(outer * HOLE));
        this.hub = inner - 4;
        this.mid = (outer + inner) / 2.0;
        this.perPage = Math.max(1, Math.min(MAX_PER_PAGE, (int) Math.floor(2 * Math.PI * mid / MIN_ARC)));
        this.pages = Math.max(1, (count + perPage - 1) / perPage);
    }

    public static WheelLayout of(int width, int height, int count) {
        return new WheelLayout(width, height, Math.max(0, count));
    }

    /** Nombre d'animaux sur la page {@code page} (0..pages-1). */
    public int slotsOn(int page) {
        int p = Math.max(0, Math.min(pages - 1, page));
        return Math.max(0, Math.min(perPage, count - p * perPage));
    }

    /** Angle du milieu du secteur {@code slot} (0 en haut, sens des aiguilles d'une montre a l'ecran). */
    public static double angle(int slot, int slots) {
        return Math.PI * 2 * slot / Math.max(1, slots);
    }

    /** Point a l'angle {@code a} et au rayon {@code r} du centre. */
    public double[] at(double a, double r) {
        return new double[]{cx + Math.sin(a) * r, cy - Math.cos(a) * r};
    }

    /** Centre de l'icone du secteur {@code slot} (un peu au-dessus du rayon moyen : le nom passe dessous). */
    public double[] iconCenter(int slot, int slots) {
        double[] p = at(angle(slot, slots), mid);
        return new double[]{p[0], p[1] - 4};
    }

    /** Largeur permise du nom sous l'icone : corde du secteur au rayon moyen, moins une marge (borne a 64 px). */
    public float nameWidth(int slots) {
        double chord = slots <= 1 ? 2 * mid : 2 * mid * Math.sin(Math.PI / slots);
        return (float) Math.max(16, Math.min(64, chord - 8));
    }

    /**
     * Largeur permise d'un nom centre en {@code x} entre {@code y0} et {@code y1} : au plus {@link #nameWidth}, et les quatre coins dans
     * l'anneau (a un pixel des bords).
     */
    public float nameRoom(double x, double y0, double y1, int slots) {
        double dx = Math.abs(x - cx), dyMax = Math.max(Math.abs(y0 - cy), Math.abs(y1 - cy)), dyMin = Math.min(Math.abs(y0 - cy), Math.abs(y1 - cy));
        if (y0 <= cy && y1 >= cy) {
            dyMin = 0;
        }
        double out = outer - 1.0, in = inner + 1.0;
        double h = Math.sqrt(Math.max(0, out * out - dyMax * dyMax)) - dx;
        if (dyMin < in) {
            h = Math.min(h, dx - Math.sqrt(in * in - dyMin * dyMin));
        }
        return (float) Math.max(8, Math.min(nameWidth(slots), 2 * h));
    }

    /**
     * Secteur vise par la souris : celui dont l'angle contient la direction de la souris depuis le centre, ou qu'elle soit hors du moyeu
     * (-1 : dans le trou du centre, ou page vide).
     */
    public int sector(double mx, double my, int slots) {
        double dx = mx - cx, dy = my - cy;
        if (slots <= 0 || dx * dx + dy * dy < (inner - 0.5) * (inner - 0.5)) {
            return -1;
        }
        double a = Math.atan2(dx, -dy);
        if (a < 0) {
            a += Math.PI * 2;
        }
        return (int) Math.floor(a / (Math.PI * 2) * slots + 0.5) % slots;
    }

    // ------------------------------------------------------------------ geometrie

    /**
     * Anneau (ou disque si {@code r0} = 0) entre les rayons {@code r0} et {@code r1}, de l'angle {@code a0} a {@code a1} : quadrilateres
     * (au moins {@link #SEGS} par tour), sommets dans l'ordre de {@code GuiGraphics.fill} (aire signee negative en coordonnees d'interface :
     * face avant malgre l'elimination des faces arriere).
     */
    public void ring(List<Object> out, double r0, double r1, double a0, double a1, int color) {
        int n = Math.max(1, (int) Math.ceil(Math.abs(a1 - a0) / (Math.PI * 2) * SEGS));
        for (int i = 0; i < n; i++) {
            double u = a0 + (a1 - a0) * i / n, v = a0 + (a1 - a0) * (i + 1) / n;
            double[] p0 = at(u, r1), p1 = at(u, r0), p2 = at(v, r0), p3 = at(v, r1);
            out.add(quad(new double[]{p0[0], p0[1], p1[0], p1[1], p2[0], p2[1], p3[0], p3[1]}, color));
        }
    }

    /** Aire signee (lacet) d'un quadrilatere en coordonnees d'interface (y vers le bas). */
    public static double area(float[] q) {
        double s = 0;
        for (int i = 0; i < 4; i++) {
            int j = (i + 1) % 4;
            s += q[2 * i] * (double) q[2 * j + 1] - q[2 * j] * (double) q[2 * i + 1];
        }
        return s / 2;
    }

    static Quad quad(double[] p, int color) {
        float[] q = new float[8];
        for (int i = 0; i < 8; i++) {
            q[i] = (float) p[i];
        }
        if (area(q) > 0) {                                           //meme sens que GuiGraphics.fill (aire negative)
            float[] r = new float[8];
            for (int i = 0; i < 4; i++) {
                r[2 * i] = q[2 * (3 - i)];
                r[2 * i + 1] = q[2 * (3 - i) + 1];
            }
            q = r;
        }
        return new Quad(q, color);
    }

    // ------------------------------------------------------------------ dessin

    /**
     * Liste de dessin de la roue, dans l'ordre : fond, secteurs, arc du secteur vise, moyeu, icones, textes. {@code cells} : les animaux de
     * la page ; {@code hovered} : secteur vise (-1 aucun) ; {@code title} / {@code sub} : textes du moyeu quand rien n'est vise (sous-titre
     * replie sur 3 lignes s'il est long) ; {@code page} / pages : points de page ; {@code hint} : consigne en bas de l'ecran ;
     * {@code width} : largeur d'un texte a l'echelle 1 (la police du jeu).
     */
    public List<Object> paint(List<Cell> cells, int hovered, int page, String title, String sub, String hint, ToIntFunction<String> width) {
        List<Object> out = new ArrayList<>();
        int slots = cells.size();
        ring(out, 0, outer + BACK_PAD, 0, Math.PI * 2, BACK);
        if (slots > 0) {
            double half = Math.PI / slots, gap = slots > 1 ? GAP / 2 / mid : 0;
            for (int i = 0; i < slots; i++) {
                double a = angle(i, slots);
                ring(out, inner, outer, a - half + gap, a + half - gap, i == hovered ? CELL_ON : CELL);
            }
            if (hovered >= 0 && hovered < slots) {
                double a = angle(hovered, slots), g2 = slots > 1 ? GAP / 2 / outer : 0;
                ring(out, outer + 0.5, outer + 0.5 + RIM, a - half + g2, a + half - g2, ACCENT);
            }
        } else {
            ring(out, inner, outer, 0, Math.PI * 2, CELL);
        }
        ring(out, 0, hub, 0, Math.PI * 2, HUB);
        ring(out, hub, hub + 1, 0, Math.PI * 2, hovered >= 0 ? ACCENT : EDGE);
        //icones
        for (int i = 0; i < slots; i++) {
            double[] c = iconCenter(i, slots);
            float s = i == hovered ? 1.25F : 1F;
            out.add(new Icon(cells.get(i).type(), (float) (c[0] - ICON * s / 2), (float) (c[1] - ICON * s / 2), s));
        }
        //noms sous les icones (largeur : corde du secteur, et rangee entiere dans l'anneau)
        for (int i = 0; i < slots; i++) {
            double[] c = iconCenter(i, slots);
            centered(out, cells.get(i).name(), (float) c[0], (float) c[1] + 10, 0.75F, i == hovered ? TEXT : DIM,
                  nameRoom(c[0], c[1] + 10, c[1] + 10 + 7 * 0.75, slots), width);
        }
        //moyeu : l'animal vise, sinon le titre et le compte
        float hw = 2 * hub - 10;
        if (hovered >= 0 && hovered < slots) {
            Cell c = cells.get(hovered);
            centered(out, c.name(), cx, cy - 10, 1F, TEXT, hw, width);
            centered(out, c.where(), cx, cy + 2, 0.75F, c.loaded() ? NEAR : DIM, hw, width);
        } else {
            centered(out, title, cx, cy - 10, 1F, ACCENT, hw, width);
            List<String> lines = wrap(sub, hw / 0.75F, width);
            if (lines.size() > 3) {
                lines = new ArrayList<>(lines.subList(0, 3));
            }
            for (int k = 0; k < lines.size(); k++) {
                centered(out, lines.get(k), cx, cy + 2 + k * 7, 0.75F, DIM, hw, width);
            }
        }
        if (pages > 1) {
            float y = cy + hub - 8, x0 = cx - (pages - 1) * 3.5F;
            for (int p = 0; p < pages && p < 9; p++) {
                double px = x0 + p * 7;
                double r = p == page ? 1.75 : 1.1;
                int color = p == page ? ACCENT : DIM;
                for (int i = 0; i < 8; i++) {
                    double u = Math.PI * 2 * i / 8, v = Math.PI * 2 * (i + 1) / 8;
                    out.add(quad(new double[]{px + Math.sin(u) * r, y - Math.cos(u) * r, px, y, px, y, px + Math.sin(v) * r, y - Math.cos(v) * r},
                          color));
                }
            }
        }
        centered(out, hint, cx, height - HINT_H + 4, 1F, TEXT, this.width - 8, width);
        return out;
    }

    /** Texte centre en {@code x} : resserre jusqu'a 70 % puis coupe avec « … » s'il depasse {@code max} (comme {@code GuiDraw.text}). */
    static void centered(List<Object> out, String s, float x, float y, float scale, int color, float max, ToIntFunction<String> width) {
        if (s == null || s.isEmpty()) {
            return;
        }
        String shown = s;
        int w = width.applyAsInt(s);
        if (w * scale > max) {
            float fit = Math.max(scale * 0.7F, Math.min(scale, max / Math.max(1, w)));
            y += (scale - fit) * 4;
            scale = fit;
        }
        if (w * scale > max) {
            String plain = s;
            while (plain.length() > 1 && width.applyAsInt(plain + "…") * scale > max) {
                plain = plain.substring(0, plain.length() - 1);
            }
            shown = plain + "…";
            w = width.applyAsInt(shown);
        }
        out.add(new Text(shown, x - w * scale / 2, y, scale, color));
    }

    /** Coupe aux espaces en lignes d'au plus {@code max} px (a l'echelle 1). */
    static List<String> wrap(String s, float max, ToIntFunction<String> width) {
        List<String> lines = new ArrayList<>();
        if (s == null || s.isEmpty()) {
            return lines;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : s.split(" ")) {
            String next = cur.length() == 0 ? word : cur + " " + word;
            if (cur.length() > 0 && width.applyAsInt(next) > max) {
                lines.add(cur.toString());
                cur = new StringBuilder(word);
            } else {
                cur = new StringBuilder(next);
            }
        }
        if (cur.length() > 0) {
            lines.add(cur.toString());
        }
        return lines;
    }
}
