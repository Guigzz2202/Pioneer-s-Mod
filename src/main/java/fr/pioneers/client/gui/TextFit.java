package fr.pioneers.client.gui;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;

/**
 * 1.9.1 (retours de Guillaume) : texte borne a sa place, commun aux ecrans de papier (Pacte, Charte, Sceau, onglet Clan) et aux ecrans
 * sombres (restauration, Console, echanges, etals). Mesure avec la vraie police ({@code font.width}), repli en lignes ({@code font.split}),
 * ou resserre jusqu'a 75 % puis coupe avec « … » : le texte entier passe alors en infobulle. Rien ne sort de sa case, quelle que soit la
 * langue. Les maquettes de {@code tools/gc/rp/screens_ui_preview.py} reproduisent ces regles a l'identique (a tenir alignees).
 */
public final class TextFit {

    /** Resserrement maximal d'une ligne avant de la couper (75 % de l'echelle demandee). */
    public static final float MIN = 0.75F;
    /** Largeur des infobulles du texte entier (repliees). */
    public static final int TIP_W = 220;
    private static final String ELLIPSIS = "…";

    private TextFit() {
    }

    /** Recoit l'infobulle d'un texte coupe : sa case (coordonnees de dessin) et ses lignes. */
    @FunctionalInterface
    public interface Tips {

        void tip(int x, int y, int w, int h, List<Component> lines);
    }

    /** Infobulles d'un ecran sans outils propres : retient celle sous la souris, a dessiner apres tout le reste. */
    public static final class Hover implements Tips {

        private final List<Component> lines = new ArrayList<>();
        private int mx, my;

        /** A appeler au debut de chaque image. */
        public Hover at(int mx, int my) {
            this.mx = mx;
            this.my = my;
            lines.clear();
            return this;
        }

        @Override
        public void tip(int x, int y, int w, int h, List<Component> l) {
            if (mx >= x && my >= y && mx < x + w && my < y + h) {
                lines.clear();
                lines.addAll(l);
            }
        }

        public boolean active() {
            return !lines.isEmpty();
        }

        public void render(GuiGraphics g, Font font) {
            if (!lines.isEmpty()) {
                //1.0.1 : calque commun des infobulles (apres tout l'ecran, hors de tout ciseau)
                TooltipLayer.components(font, lines, mx, my);
            }
        }
    }

    /** Ligne prete a dessiner : texte montre (entier ou coupe), echelle retenue, largeur dessinee. */
    public record Line(Component shown, float scale, int width, boolean cut) {
    }

    // ------------------------------------------------------------------ mesures
    public static int width(Font f, FormattedText t, float scale) {
        return (int) Math.ceil(f.width(t) * scale);
    }

    /** Pas entre deux lignes a l'echelle {@code scale} (10 px a l'echelle 1, comme le Carnet). */
    public static int step(float scale) {
        return Math.round(10 * scale);
    }

    /** Hauteur d'une ligne seule (les glyphes font 8 px avec le jambage). */
    public static int height(float scale) {
        return (int) Math.ceil(8 * scale);
    }

    /** Une ligne dans {@code w} : echelle demandee, sinon resserree jusqu'a 75 %, sinon coupee avec « … ». */
    public static Line measure(Font f, Component c, int w, float scale) {
        int full = f.width(c);
        if (full * scale <= w) {
            return new Line(c, scale, (int) Math.ceil(full * scale), false);
        }
        float k = w / (float) Math.max(1, full);
        if (k >= scale * MIN) {
            return new Line(c, k, Math.min(w, (int) Math.ceil(full * k)), false);
        }
        k = scale * MIN;
        Component cut = Component.literal(cut(f, c.getString(), (int) (w / k))).withStyle(c.getStyle());
        return new Line(cut, k, Math.min(w, (int) Math.ceil(f.width(cut) * k)), true);
    }

    /** Debut de {@code s} suivi de « … », dans {@code maxW} pixels de police. */
    public static String cut(Font f, String s, int maxW) {
        String head = f.plainSubstrByWidth(s, Math.max(0, maxW - f.width(ELLIPSIS)));
        return head.stripTrailing() + ELLIPSIS;
    }

    /** Lignes de {@code t} repliees dans {@code w} (pixels d'ecran) a l'echelle {@code scale}. */
    public static List<FormattedCharSequence> lines(Font f, FormattedText t, int w, float scale) {
        return f.split(t, Math.max(1, (int) (w / scale)));
    }

    public static int wrapHeight(Font f, FormattedText t, int w, float scale, int max) {
        int n = Math.min(max, lines(f, t, w, scale).size());
        return n <= 0 ? 0 : n * step(scale);
    }

    /** Lignes de l'infobulle du texte entier. */
    public static List<Component> tipLines(Font f, FormattedText t) {
        List<Component> out = new ArrayList<>();
        for (FormattedCharSequence l : f.split(t, TIP_W)) {
            out.add(Component.literal(plain(l)));
        }
        return out;
    }

    public static String plain(FormattedCharSequence seq) {
        StringBuilder b = new StringBuilder();
        seq.accept((i, style, cp) -> {
            b.appendCodePoint(cp);
            return true;
        });
        return b.toString();
    }

    // ------------------------------------------------------------------ dessin
    public static void draw(GuiGraphics g, Font f, Component c, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(f, c, 0, 0, color, false);
        g.pose().popPose();
    }

    public static void draw(GuiGraphics g, Font f, FormattedCharSequence c, float x, float y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(f, c, 0, 0, color, false);
        g.pose().popPose();
    }

    /** Ligne bornee a la case [x, x + w], alignee a gauche ; renvoie la largeur dessinee. */
    public static int fit(GuiGraphics g, Font f, Component c, float x, float y, int w, int color, float scale, @Nullable Tips tips) {
        return fit(g, f, c, x, y, w, color, scale, tips, 0);
    }

    /** Ligne bornee a la case [x, x + w], centree. */
    public static int center(GuiGraphics g, Font f, Component c, float x, float y, int w, int color, float scale, @Nullable Tips tips) {
        return fit(g, f, c, x, y, w, color, scale, tips, 1);
    }

    /** Ligne bornee a la case [x, x + w], alignee a droite. */
    public static int right(GuiGraphics g, Font f, Component c, float x, float y, int w, int color, float scale, @Nullable Tips tips) {
        return fit(g, f, c, x, y, w, color, scale, tips, 2);
    }

    /** {@code align} : 0 a gauche, 1 centre, 2 a droite. Une ligne resserree reste centree en hauteur sur la ligne prevue. */
    public static int fit(GuiGraphics g, Font f, Component c, float x, float y, int w, int color, float scale, @Nullable Tips tips, int align) {
        if (w <= 0) {
            return 0;
        }
        Line l = measure(f, c, w, scale);
        float x0 = align == 0 ? x : align == 1 ? x + (w - l.width()) / 2F : x + w - l.width();
        draw(g, f, l.shown(), x0, y + (scale - l.scale()) * 4, color, l.scale());
        if (l.cut() && tips != null) {
            tips.tip((int) x, (int) y, w, Math.max(6, height(scale)), tipLines(f, c));
        }
        return l.width();
    }

    /**
     * Texte replie dans {@code w}, au plus {@code max} lignes ; s'il en reste, la derniere est coupee avec « … » et le texte entier passe en
     * infobulle. Renvoie la hauteur utilisee ({@code lignes x step(scale)}).
     */
    public static int wrap(GuiGraphics g, Font f, Component c, float x, float y, int w, int color, float scale, int max, @Nullable Tips tips) {
        return wrap(g, f, c, x, y, w, color, scale, max, tips, false);
    }

    public static int wrap(GuiGraphics g, Font f, Component c, float x, float y, int w, int color, float scale, int max, @Nullable Tips tips,
          boolean centered) {
        if (w <= 0 || max <= 0) {
            return 0;
        }
        List<FormattedCharSequence> lines = lines(f, c, w, scale);
        int step = step(scale), n = Math.min(lines.size(), max);
        for (int i = 0; i < n; i++) {
            boolean last = i == n - 1 && lines.size() > n;
            int lw;
            if (last) {
                Component cut = Component.literal(cut(f, plain(lines.get(i)) + " " + plain(lines.get(i + 1)), (int) (w / scale)))
                      .withStyle(c.getStyle());
                lw = width(f, cut, scale);
                draw(g, f, cut, centered ? x + (w - lw) / 2F : x, y + i * step, color, scale);
            } else {
                lw = (int) Math.ceil(f.width(lines.get(i)) * scale);
                draw(g, f, lines.get(i), centered ? x + (w - lw) / 2F : x, y + i * step, color, scale);
            }
        }
        if (n < lines.size() && tips != null) {
            tips.tip((int) x, (int) y, w, n * step, tipLines(f, c));
        }
        return n * step;
    }
}
