package fr.pioneers.society.rp.client;

import fr.pioneers.Pioneers;
import fr.pioneers.client.gui.TooltipLayer;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP) : base des ecrans « de papier » (Carnet, initiation, Pupitre, lecture, villageois, echanges, clan) : page de parchemin
 * (texture generee par {@code tools/gc/rp/notebook_gui.py}), encre bleue, zones de clic enregistrees a chaque image (la derniere dessinee
 * l'emporte), defilement par zone. Couleurs : un accent qui porte un sens est toujours double d'un signe (+ et −).
 * <p>
 * 1.9.1 (retours de Guillaume : textes superposes) : tout texte de longueur variable passe par {@link #fit} (une ligne : echelle reduite
 * jusqu'a 0,75 puis coupe par « … » avec l'infobulle du texte entier) ou {@link #wrap} (lignes repliees, nombre de lignes borne) ; pastilles
 * et boutons bornent leur libelle ; les infobulles sont repliees a {@link #TOOLTIP_W} ; les icones sont dessinees avec le melange alpha.
 */
public abstract class ParchmentScreen extends Screen {

    public static final ResourceLocation PAGE = Pioneers.rl("textures/gui/pioneer/notebook.png");
    public static final int PAGE_W = 384, PAGE_H = 240;
    public static final int INK = 0xFF24365E, INK_LIGHT = 0xFF4A5F8C, TEXT = 0xFF3A2E22, DIM = 0xFF7A6A55, FAINT = 0xFFB9A889,
          PLUS = 0xFF2F6B30, MINUS = 0xFF9A2E25, PAPER = 0xFFEFE3C8, PAPER_DARK = 0xFFE2D2B0, GOLD = 0xFFA87C2A;

    /** 1.9.1 : largeur des infobulles repliees. */
    public static final int TOOLTIP_W = 220;
    protected int left, top;
    /** 1.9.1 : souris en coordonnees de la page pendant {@link #drawContents} (infobulles des textes coupes). */
    protected int pageMx = -1, pageMy = -1;
    private final List<Zone> zones = new ArrayList<>();
    private final List<Component> hover = new ArrayList<>();
    protected final int[] scroll = new int[8];
    private final List<int[]> scrollAreas = new ArrayList<>();

    private record Zone(int x, int y, int w, int h, Runnable action) {
    }

    protected ParchmentScreen(Component title) {
        super(title);
    }

    @Override
    protected void init() {
        left = (width - PAGE_W) / 2;
        top = (height - PAGE_H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        zones.clear();
        hover.clear();
        scrollAreas.clear();
        renderBackground(g);
        g.blit(PAGE, left, top, 0, 0, PAGE_W, PAGE_H, PAGE_W, PAGE_H);
        g.pose().pushPose();
        g.pose().translate(left, top, 0);
        pageMx = mx - left;
        pageMy = my - top;
        drawContents(g, mx - left, my - top, pt);
        drawScrollbars(g);
        g.pose().popPose();
        super.render(g, mx, my, pt);
        if (!hover.isEmpty()) {
            //1.9.1 : lignes repliees (une description longue ne sort plus de l'ecran)
            List<FormattedCharSequence> lines = new ArrayList<>();
            int tw = Math.max(120, Math.min(TOOLTIP_W, width / 2));
            for (Component c : hover) {
                List<FormattedCharSequence> l = font.split(c, tw);
                lines.addAll(l.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : l);
            }
            //1.0.1 : calque commun (dessinee apres tout l'ecran, hors de tout ciseau)
            TooltipLayer.lines(font, lines, mx, my);
        }
    }

    /** Contenu de la page, en coordonnees de la page (0..PAGE_W, 0..PAGE_H). */
    protected abstract void drawContents(GuiGraphics g, int mx, int my, float pt);

    // ------------------------------------------------------------------ 1.9 (MAJ RP, bloc 4) : acces publics (onglets d'autres paquets)
    /** Coin de la page a l'ecran (les outils de dessin sont publics : l'onglet Clan vit dans son propre paquet). */
    public int pageLeft() {
        return left;
    }

    public int pageTop() {
        return top;
    }

    public int scrollOf(int slot) {
        return scroll[slot];
    }

    public void scrollTo(int slot, int value) {
        scroll[slot] = value;
    }

    // ------------------------------------------------------------------ outils de dessin (coordonnees de la page)
    public void text(GuiGraphics g, Component c, int x, int y, int color) {
        g.drawString(font, c, x, y, color, false);
    }

    public void text(GuiGraphics g, String s, int x, int y, int color) {
        g.drawString(font, s, x, y, color, false);
    }

    public void textScaled(GuiGraphics g, Component c, int x, int y, int color, float scale) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    public void centered(GuiGraphics g, Component c, int cx, int y, int color) {
        g.drawString(font, c, cx - font.width(c) / 2, y, color, false);
    }

    // ------------------------------------------------------------------ 1.9.1 (retours de Guillaume) : textes bornes
    private void drawAt(GuiGraphics g, Component c, float x, float y, int color, float k) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, c, 0, 0, color, false);
        g.pose().popPose();
    }

    /**
     * Une ligne de texte bornee a {@code w} : a l'echelle {@code scale} si elle tient, sinon reduite (jamais sous 0,75 : {@link RpGui#minScale}), sinon
     * coupee par « … » avec l'infobulle du texte entier. Le texte reduit reste centre sur la ligne d'origine. Renvoie la largeur dessinee.
     */
    public int fit(GuiGraphics g, Component c, int x, int y, int w, int color, float scale) {
        float k = RpGui.fitScale(font, c, w, scale);
        if (k > 0) {
            drawAt(g, c, x, y + (scale - k) * 4, color, k);
            return (int) Math.ceil(font.width(c) * k);
        }
        k = RpGui.minScale(scale);
        String cut = RpGui.ellipsize(font, c.getString(), w / k);
        drawAt(g, Component.literal(cut).withStyle(c.getStyle()), x, y + (scale - k) * 4, color, k);
        tooltip(pageMx, pageMy, x, y, w, Math.max(8, Math.round(9 * scale)), c);
        return w;
    }

    public int fit(GuiGraphics g, Component c, int x, int y, int w, int color) {
        return fit(g, c, x, y, w, color, 1F);
    }

    /** Largeur qu'occupera {@code c} borne a {@code w} (pour aligner a droite ou centrer). */
    public int fitWidth(Component c, int w, float scale) {
        float k = RpGui.fitScale(font, c, w, scale);
        return k > 0 ? (int) Math.ceil(font.width(c) * k) : w;
    }

    /** {@link #fit} centre sur {@code cx}, dans {@code w} au plus. */
    public int fitCentered(GuiGraphics g, Component c, int cx, int y, int w, int color, float scale) {
        int tw = fitWidth(c, w, scale);
        return fit(g, c, cx - tw / 2, y, w, color, scale);
    }

    /** {@link #fit} aligne a droite sur {@code rx}, dans {@code w} au plus. */
    public int fitRight(GuiGraphics g, Component c, int rx, int y, int w, int color, float scale) {
        int tw = fitWidth(c, w, scale);
        return fit(g, c, rx - tw, y, w, color, scale);
    }

    /** Pas de ligne d'un texte replie a l'echelle {@code scale}. */
    public static int lineStep(float scale) {
        return Math.max(6, Math.round(10 * scale));
    }

    /** Lignes de {@code c} repliees dans {@code w} a l'echelle {@code scale}. */
    public List<FormattedCharSequence> lines(FormattedText c, int w, float scale) {
        return font.split(c, Math.max(8, (int) Math.floor(w / scale)));
    }

    /** Hauteur de {@link #wrap} (sans borne de lignes). */
    public int wrapHeight(FormattedText c, int w, float scale) {
        return lines(c, w, scale).size() * lineStep(scale);
    }

    /**
     * Texte replie dans {@code w} a l'echelle {@code scale}, au plus {@code maxLines} lignes : la derniere est alors coupee par « … » et
     * l'infobulle donne le texte entier. Renvoie la hauteur utilisee.
     */
    public int wrap(GuiGraphics g, Component c, int x, int y, int w, int color, float scale, int maxLines) {
        List<FormattedCharSequence> ls = lines(c, w, scale);
        int step = lineStep(scale);
        int n = Math.min(ls.size(), Math.max(1, maxLines));
        for (int i = 0; i < n; i++) {
            FormattedCharSequence l = ls.get(i);
            if (i == n - 1 && ls.size() > n) {
                StringBuilder rest = new StringBuilder(RpGui.plain(l));
                for (int j = i + 1; j < ls.size(); j++) {
                    rest.append(' ').append(RpGui.plain(ls.get(j)));
                }
                drawAt(g, Component.literal(RpGui.ellipsize(font, rest.toString(), w / scale)).withStyle(c.getStyle()), x, y + i * step, color,
                      scale);
                tooltip(pageMx, pageMy, x, y, w, n * step, c);
            } else {
                g.pose().pushPose();
                g.pose().translate(x, y + i * step, 0);
                g.pose().scale(scale, scale, 1);
                g.drawString(font, l, 0, 0, color, false);
                g.pose().popPose();
            }
        }
        return n * step;
    }

    /** Texte coupe en lignes ; renvoie la hauteur utilisee. */
    public int wrapped(GuiGraphics g, FormattedText text, int x, int y, int w, int color) {
        List<FormattedCharSequence> lines = font.split(text, w);
        int yy = y;
        for (FormattedCharSequence l : lines) {
            g.drawString(font, l, x, yy, color, false);
            yy += 10;
        }
        return yy - y;
    }

    public int wrappedHeight(FormattedText text, int w) {
        return font.split(text, w).size() * 10;
    }

    public boolean over(int mx, int my, int x, int y, int w, int h) {
        return mx >= x && my >= y && mx < x + w && my < y + h;
    }

    public void click(int x, int y, int w, int h, Runnable action) {
        zones.add(new Zone(left + x, top + y, w, h, action));
    }

    public void tooltip(int mx, int my, int x, int y, int w, int h, Component... lines) {
        if (over(mx, my, x, y, w, h)) {
            hover.clear();
            hover.addAll(List.of(lines));
        }
    }

    public void tooltip(int mx, int my, int x, int y, int w, int h, List<Component> lines) {
        if (over(mx, my, x, y, w, h)) {
            hover.clear();
            hover.addAll(lines);
        }
    }

    /** Cadre fin a l'encre. */
    public void frame(GuiGraphics g, int x, int y, int w, int h, int color) {
        g.fill(x, y, x + w, y + 1, color);
        g.fill(x, y + h - 1, x + w, y + h, color);
        g.fill(x, y, x + 1, y + h, color);
        g.fill(x + w - 1, y, x + w, y + h, color);
    }

    /** Bouton a l'encre (fond papier plus sombre au survol). 1.9.1 : libelle borne a la largeur du bouton. */
    public void button(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component label, boolean enabled, @Nullable Runnable action) {
        boolean on = enabled && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? 0x40243660 : 0x18243660);
        frame(g, x, y, w, h, enabled ? INK : FAINT);
        fitCentered(g, label, x + w / 2, y + (h - 8) / 2, w - 4, enabled ? INK : FAINT, 1F);
        if (enabled && action != null) {
            click(x, y, w, h, action);
        }
    }

    /** Onglet / pastille selectionnable. 1.9.1 : libelle borne a la largeur de la pastille. */
    public void chip(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component label, boolean selected, boolean enabled, Runnable action) {
        chip(g, mx, my, x, y, w, h, label, selected, enabled, 1F, action);
    }

    /** 1.9.1 : pastille dont le libelle est dessine a l'echelle {@code scale} (barre d'onglets : une echelle commune a toutes). */
    public void chip(GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component label, boolean selected, boolean enabled, float scale,
          Runnable action) {
        boolean on = enabled && over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, selected ? 0x50243660 : on ? 0x28243660 : 0x10243660);
        frame(g, x, y, w, h, selected ? INK : enabled ? INK_LIGHT : FAINT);
        fitCentered(g, label, x + w / 2, y + (h - 8) / 2, w - 4, selected ? INK : enabled ? INK_LIGHT : FAINT, scale);
        if (enabled) {
            click(x, y, w, h, action);
        }
    }

    /** Barre de progression a l'encre. */
    public void bar(GuiGraphics g, int x, int y, int w, int h, double frac, int color) {
        g.fill(x, y, x + w, y + h, 0x30243660);
        g.fill(x, y, x + (int) Math.round(w * Mth.clamp(frac, 0, 1)), y + h, color);
        frame(g, x, y, w, h, INK_LIGHT);
    }

    public void icon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        RpGui.blitBlended(g, tex, x, y, size); //1.9.1 : avec le melange alpha
    }

    /**
     * 1.9.1 (retours de Guillaume : « personnage mal positionne ») : le pionnier en pied dans le cadre {@code w x h} (coordonnees de la
     * page) : centre, en entier, pieds sur le bas du cadre, coupe au cadre ; {@code yaw} en degres (0 = de face).
     */
    public void portrait(GuiGraphics g, LivingEntity e, int x, int y, int w, int h, float yaw) {
        int scale = RpGui.pioneerScale(w, h, 4);
        beginClip(g, x + 1, y + 1, w - 2, h - 2);
        RpGui.pioneer(g, x + w / 2, y + h - 4, scale, yaw, e);
        endClip(g);
    }

    public void separator(GuiGraphics g, int x, int y, int w) {
        g.fill(x, y, x + w, y + 1, FAINT);
    }

    // ------------------------------------------------------------------ defilement
    /** Declare une zone qui defile (molette) avec l'indice {@code slot} de {@link #scroll} et sa hauteur de contenu. */
    public void scrollArea(int slot, int x, int y, int w, int h, int contentH) {
        scroll[slot] = Mth.clamp(scroll[slot], 0, Math.max(0, contentH - h));
        scrollAreas.add(new int[]{slot, left + x, top + y, w, h, contentH});
    }

    /**
     * 1.0.5-beta (lot 19, relecture : « liste Villages : derniere ligne coupee ») : une zone qui defile montre sa BARRE (2 px au bord droit :
     * rail pale, curseur a la place de la vue) ; avant, rien ne disait qu'une liste continuait sous le bord. Coordonnees de la page.
     */
    private void drawScrollbars(GuiGraphics g) {
        for (int[] a : scrollAreas) {
            int x = a[1] - left, y = a[2] - top, w = a[3], h = a[4], content = a[5];
            if (content <= h || h <= 0) {
                continue;
            }
            int thumb = Math.max(8, h * h / content);
            int ty = y + (h - thumb) * scroll[a[0]] / Math.max(1, content - h);
            g.fill(x + w - 2, y, x + w, y + h, 0x18243660);
            g.fill(x + w - 2, ty, x + w, ty + thumb, INK_LIGHT);
        }
    }

    public void beginClip(GuiGraphics g, int x, int y, int w, int h) {
        g.enableScissor(left + x, top + y, left + x + w, top + y + h);
    }

    public void endClip(GuiGraphics g) {
        g.disableScissor();
    }

    /** 1.0.5-beta (lot 16) : liste ouverte d'un selecteur de joueur sous ce point (elle passe avant les zones de la page). */
    @Nullable
    private fr.pioneers.client.gui.PlayerPicker picker(double mx, double my) {
        for (net.minecraft.client.gui.components.events.GuiEventListener c : children()) {
            if (c instanceof fr.pioneers.client.gui.PlayerPicker p && p.captures(mx, my)) {
                return p;
            }
        }
        return null;
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        fr.pioneers.client.gui.PlayerPicker p = picker(mx, my);
        if (p != null) {
            return p.mouseScrolled(mx, my, delta);
        }
        for (int[] a : scrollAreas) {
            if (mx >= a[1] && my >= a[2] && mx < a[1] + a[3] && my < a[2] + a[4]) {
                scroll[a[0]] = Mth.clamp(scroll[a[0]] - (int) (delta * 18), 0, Math.max(0, a[5] - a[4]));
                return true;
            }
        }
        return super.mouseScrolled(mx, my, delta);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        fr.pioneers.client.gui.PlayerPicker p = picker(mx, my);
        if (p != null) {
            setFocused(p);
            return p.mouseClicked(mx, my, button);
        }
        //un clic hors d'un selecteur ouvert le referme
        for (net.minecraft.client.gui.components.events.GuiEventListener c : children()) {
            if (c instanceof fr.pioneers.client.gui.PlayerPicker q && q.isFocused() && !q.isMouseOver(mx, my)) {
                q.setFocused(false);
                if (getFocused() == q) {
                    setFocused(null);
                }
            }
        }
        if (button == 0) {
            for (int i = zones.size() - 1; i >= 0; i--) {
                Zone z = zones.get(i);
                if (mx >= z.x() && my >= z.y() && mx < z.x() + z.w() && my < z.y() + z.h()) {
                    RpClient.play(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.3F);
                    z.action().run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }
}
