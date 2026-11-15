package fr.pioneers.society.rp.client;

import fr.pioneers.core.profile.PioneerDefs;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 1.9 (MAJ RP) : morceaux communs a l'initiation, au Carnet et au Pupitre : grille des cartes de vocation et panneau de lecture (1.9.1).
 * 1.12.0 (lot E4) : l'editeur d'allure est retire (barbe et coiffure : skin du joueur ; habillage : garde-robe cosmetique).
 */
public abstract class PioneerScreen extends ParchmentScreen {

    /**
     * 1.9.1 (retours de Guillaume : onze vocations, textes qui debordaient sur la carte voisine) : grille de cartes (embleme et nom, 4 par
     * rangee, 3 rangees) et panneau de lecture a droite (vocation survolee, sinon choisie, sinon actuelle). Le panneau donne tout le texte
     * replie : plus aucune ligne d'avantage ou de defaut a l'echelle 0,5 sous la carte.
     */
    public static final int TILE_W = 57, TILE_H = 52, TILE_GAP = 2, TILE_VGAP = 3, TILE_COLS = 4, TILE_ROWS = 3, PANEL_GAP = 6;
    public static final int GRID_W = TILE_COLS * TILE_W + (TILE_COLS - 1) * TILE_GAP, GRID_H = TILE_ROWS * TILE_H + (TILE_ROWS - 1) * TILE_VGAP;
    /** Indice de defilement du panneau (seulement si son texte depasse). */
    protected static final int PANEL_SCROLL = 2;

    protected PioneerScreen(Component title) {
        super(title);
    }

    // ------------------------------------------------------------------ cartes de vocation
    /**
     * Grille des vocations en ({@code x0}, {@code y0}) et panneau jusqu'a {@code right} ; {@code selected} = choix en cours, {@code current}
     * = vocation actuelle (cadre dore, non choisissable au Pupitre), {@code pick} = clic sur une carte. Renvoie la hauteur ({@link #GRID_H}).
     */
    protected int vocationPicker(GuiGraphics g, int mx, int my, int x0, int y0, int right, @Nullable String selected, @Nullable String current,
          Consumer<String> pick) {
        List<PioneerDefs.Vocation> list = RpGui.vocations();
        PioneerDefs.Vocation hovered = null;
        for (int i = 0; i < list.size(); i++) {
            PioneerDefs.Vocation v = list.get(i);
            int x = x0 + (i % TILE_COLS) * (TILE_W + TILE_GAP), y = y0 + (i / TILE_COLS) * (TILE_H + TILE_VGAP);
            boolean sel = v.id().equals(selected), cur = v.id().equals(current), on = over(mx, my, x, y, TILE_W, TILE_H);
            if (on) {
                hovered = v;
            }
            g.fill(x, y, x + TILE_W, y + TILE_H, sel ? 0x40243660 : on ? 0x22243660 : 0x10243660);
            int edge = sel ? INK : cur ? GOLD : INK_LIGHT;
            frame(g, x, y, TILE_W, TILE_H, edge);
            if (sel || cur) {
                frame(g, x + 1, y + 1, TILE_W - 2, TILE_H - 2, edge);
            }
            icon(g, RpGui.emblem(v.id()), x + (TILE_W - 32) / 2, y + 3, 32);
            tileName(g, RpGui.vocationName(v.id()), x + 2, y + 37, TILE_W - 4, INK);
            String id = v.id();
            click(x, y, TILE_W, TILE_H, () -> pick.accept(id));
        }
        int px = x0 + GRID_W + PANEL_GAP;
        PioneerDefs.Vocation shown = hovered != null ? hovered : PioneerDefs.get().vocation(selected != null && !selected.isEmpty() ? selected
              : current);
        vocationPanel(g, shown, px, y0, right - px, GRID_H, current);
        return GRID_H;
    }

    /** Nom d'une carte : une ligne (reduite jusqu'a 0,75), sinon deux lignes coupees aux espaces et aux traits d'union. */
    private void tileName(GuiGraphics g, Component name, int x, int y, int w, int color) {
        if (RpGui.fitScale(font, name, w, 1F) > 0) {
            fitCentered(g, name, x + w / 2, y + 2, w, color, 1F);
            return;
        }
        List<String> ls = RpGui.splitWords(font, name.getString(), w / RpGui.MIN_SCALE);
        if (ls.size() == 2) {
            for (int i = 0; i < 2; i++) {
                fitCentered(g, Component.literal(ls.get(i)).withStyle(name.getStyle()), x + w / 2, y + i * 7, w, color, RpGui.MIN_SCALE);
            }
            tooltip(pageMx, pageMy, x, y, w, 14, name);
        } else {
            fitCentered(g, name, x + w / 2, y + 2, w, color, 1F);
        }
    }

    /** Panneau de lecture d'une vocation (rien de choisi : l'invite). Defile s'il depasse (jamais dans les donnees actuelles). */
    protected void vocationPanel(GuiGraphics g, @Nullable PioneerDefs.Vocation v, int px, int py, int pw, int ph, @Nullable String current) {
        g.fill(px, py, px + pw, py + ph, 0x0C243660);
        frame(g, px, py, pw, ph, FAINT);
        int ix = px + 5, iw = pw - 10;
        if (v == null) {
            Component hint = Component.translatable("pioneer.pioneers.vocation.pick_hint");
            int h = Math.min(wrapHeight(hint, iw, 1F), 50);
            wrap(g, hint, ix, py + (ph - h) / 2, iw, DIM, 1F, 5);
            return;
        }
        int content = panelBody(g, v, ix, 0, iw, current, false);
        boolean scroll = content > ph - 8;
        int y0 = py + 4;
        if (scroll) {
            scrollArea(PANEL_SCROLL, px, py, pw, ph, content + 8);
            beginClip(g, px + 1, py + 1, pw - 2, ph - 2);
            y0 -= this.scroll[PANEL_SCROLL];
        } else {
            this.scroll[PANEL_SCROLL] = 0;
        }
        panelBody(g, v, ix, y0, iw, current, true);
        if (scroll) {
            endClip(g);
        }
    }

    /** Corps du panneau ; {@code draw} faux = mesure seulement. Renvoie la hauteur. */
    private int panelBody(GuiGraphics g, PioneerDefs.Vocation v, int x, int y0, int w, @Nullable String current, boolean draw) {
        final float s = RpGui.MIN_SCALE;
        int y = y0;
        if (draw) {
            fit(g, RpGui.vocationName(v.id()), x, y, w, INK);
        }
        y += 11;
        if (v.id().equals(current)) {
            if (draw) {
                fit(g, Component.translatable("pioneer.pioneers.current"), x, y, w, GOLD, s);
            }
            y += 8;
        }
        Component phrase = Component.translatable("vocation.pioneers." + v.id() + ".phrase").withStyle(st -> st.withItalic(true));
        y += draw ? wrap(g, phrase, x, y, w, DIM, s, 4) : Math.min(4, lines(phrase, w, s).size()) * lineStep(s);
        y += 2;
        if (draw) {
            separator(g, x, y, w);
        }
        y += 4;
        if (v.neutral()) {
            Component n = Component.translatable("pioneer.pioneers.vocation.neutral");
            Component more = Component.translatable("pioneer.pioneers.vocation.neutral_more");
            y += (draw ? wrap(g, n, x, y, w, TEXT, 1F, 6) : Math.min(6, lines(n, w, 1F).size()) * lineStep(1F)) + 4;
            return y + (draw ? wrap(g, more, x, y, w, DIM, s, 6) : Math.min(6, lines(more, w, s).size()) * lineStep(s)) - y0;
        }
        if (draw) {
            fit(g, Component.translatable("pioneer.pioneers.affinity"), x, y, w, DIM, s);
        }
        y += 8;
        MutableComponent names = Component.empty();
        for (int i = 0; i < v.affinities().size(); i++) {
            String a = v.affinities().get(i);
            if (draw) {
                icon(g, RpGui.skillIcon(a), x + i * 18, y, 16);
            }
            names.append(i > 0 ? Component.literal(", ") : Component.empty()).append(RpGui.skillName(a));
        }
        if (v.affinities().size() >= 3) { //1.0.5-beta (lot 10) : trois affinites (Eclaireur) : noms sous les icones, sur toute la largeur
            int lh = Math.min(2, lines(names, w, s).size()) * lineStep(s);
            if (draw) {
                wrap(g, names, x, y + 18, w, TEXT, s, 2);
            }
            y += 19 + lh;
        } else {
            if (draw) {
                int nx = x + v.affinities().size() * 18 + 2;
                int lh = Math.min(2, lines(names, w - (nx - x), s).size()) * lineStep(s);
                wrap(g, names, nx, y + (16 - lh) / 2 + 1, w - (nx - x), TEXT, s, 2);
            }
            y += 19;
        }
        PioneerDefs.Trait[] traits = {v.advantage(), v.defect()};
        for (int k = 0; k < 2; k++) {
            Component title = Component.literal(k == 0 ? "+ " : "− ").append(Component.translatable("trait.pioneers." + traits[k].id()));
            Component desc = Component.translatable("trait.pioneers." + traits[k].id() + ".desc");
            int color = k == 0 ? PLUS : MINUS;
            y += draw ? wrap(g, title, x, y, w, color, 1F, 2) : Math.min(2, lines(title, w, 1F).size()) * lineStep(1F);
            y += draw ? wrap(g, desc, x, y, w, TEXT, s, 5) : Math.min(5, lines(desc, w, s).size()) * lineStep(s);
            y += 3;
        }
        return y - y0;
    }
}
