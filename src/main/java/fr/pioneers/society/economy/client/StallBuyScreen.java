package fr.pioneers.society.economy.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : ecran d'achat d'un etal ou d'un comptoir (style sombre du mod) : annonces actives (icone, nom, lot, stock en lots,
 * prix du lot, pourcentage de la valeur de base en couleur avec son signe), choix du nombre de lots (1, 4, 16, le plus possible) et achat.
 * Totaux, stock et pourcentages viennent du serveur ; l'ecran n'envoie que le numero de l'annonce et le nombre de lots.
 * <p>
 * 1.9.1 (retours de Guillaume : textes qui se superposaient) : prix et pourcentage sont mesures d'abord, le nom et le lot prennent la place
 * qui reste ; le titre laisse la place au solde. Tout texte passe par {@link TextFit} (resserre, puis coupe avec l'infobulle).
 */
public class StallBuyScreen extends Screen {

    private static final int W = 300, H = 232, ROW = 20, ROWS = 8, LIST_Y = 32;
    private static final int TEXT = 0xFFE8E8E8, DIM = 0xFF8A96A2, ACCENT = 0xFF3FD0A0, WARN = 0xFFE3A23B;
    /** Libelles des trois premiers choix avant qu'une annonce soit choisie (les vrais nombres viennent du serveur). */
    private static final int[] CHOICES = fr.pioneers.society.economy.stall.StallService.CHOICES;

    private StallClient.View view;
    private int selected = -1, choice, scroll, left, top;
    private final TextFit.Hover hover = new TextFit.Hover();

    public StallBuyScreen(StallClient.View view) {
        super(view.title());
        this.view = view;
    }

    public long pos() {
        return view.pos();
    }

    /** Nouvelle vue du serveur : la selection suit le numero de l'annonce. */
    public void read(StallClient.View v) {
        int index = selected >= 0 && selected < view.entries().size() ? view.entries().get(selected).index() : -1;
        view = v;
        selected = -1;
        for (int r = 0; r < v.entries().size(); r++) {
            if (v.entries().get(r).index() == index) {
                selected = r;
            }
        }
    }

    @Override
    protected void init() {
        left = (width - W) / 2;
        top = (height - H) / 2;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private List<StallClient.Entry> rows() {
        return view.entries();
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        this.hover.at(mx, my);
        GuiDraw.panel(g, left, top, W, H);
        //1.9.1 : le solde est mesure d'abord (140 px au plus), le titre prend le reste
        int bw = TextFit.right(g, font, Component.translatable("stall.pioneers.balance", Long.toString(view.balance())), left + W - 148, top + 7, 140,
              ACCENT, 0.9F, this.hover);
        TextFit.fit(g, font, view.title(), left + 8, top + 7, W - 16 - bw - 8, TEXT, 1F, this.hover);
        TextFit.fit(g, font, Component.translatable("stall.pioneers.buy.hint"), left + 8, top + 20, W - 16, DIM, 0.7F, this.hover);
        //liste des annonces
        List<StallClient.Entry> rows = rows();
        GuiDraw.screen(g, left + 8, top + LIST_Y, W - 16, ROWS * ROW + 4);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - ROWS)));
        for (int r = 0; r < ROWS && scroll + r < rows.size(); r++) {
            int i = scroll + r;
            StallClient.Entry e = rows.get(i);
            int y = top + LIST_Y + 2 + r * ROW;
            boolean sel = i == selected, hover = inRow(mx, my, r);
            if (sel || hover) {
                g.fill(left + 9, y, left + W - 9, y + ROW, sel ? 0xFF253240 : 0xFF1A232C);
            }
            g.renderItem(e.stack(), left + 12, y + 2);
            if (e.lot() > 1) {
                g.renderItemDecorations(font, e.stack().copyWithCount(e.lot()), left + 12, y + 2);
            }
            //1.9.1 : prix (ligne 1) et pourcentage (ligne 2) mesures a droite, nom et lot dans le reste
            int pw = TextFit.right(g, font, Component.translatable("stall.pioneers.tokens", Long.toString(e.price())), left + W - 12 - 100, y + 3, 100,
                  TEXT, 0.8F, null);
            int qw = TextFit.right(g, font, StallClient.percent(e.pct()), left + W - 12 - 100, y + 12, 100, StallClient.percentColor(e.pct()), 0.65F,
                  null);
            TextFit.fit(g, font, e.stack().getHoverName(), left + 33, y + 3, W - 12 - 33 - pw - 6, TEXT, 0.75F, this.hover);
            TextFit.fit(g, font, Component.translatable("stall.pioneers.buy.row", e.lot(), e.lots()), left + 33, y + 12, W - 12 - 33 - qw - 6, DIM,
                  0.65F, this.hover);
        }
        if (rows.isEmpty()) {
            TextFit.center(g, font, Component.translatable(view.open() ? "stall.pioneers.buy.empty" : "stall.pioneers.result.closed"), left + 15,
                  top + LIST_Y + 3 * ROW, W - 30, DIM, 0.85F, this.hover);
        }
        //choix du nombre de lots et achat
        int by = top + LIST_Y + ROWS * ROW + 10;
        StallClient.Entry e = selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
        for (int k = 0; k < 4; k++) {
            int x = left + 8 + k * 40;
            boolean on = choice == k, hover = mx >= x && mx < x + 38 && my >= by && my < by + 16;
            g.fill(x, by, x + 38, by + 16, on ? 0xFF2A3540 : hover ? 0xFF222B33 : 0xFF182027);
            if (on) {
                g.fill(x, by + 15, x + 38, by + 16, ACCENT);
            }
            boolean known = e != null && e.choices().length == 4;
            Component label = k == 3 ? (known ? Component.translatable("stall.pioneers.buy.max", String.valueOf(e.choices()[3]))
                  : Component.translatable("stall.pioneers.buy.max_short"))
                  : Component.translatable("stall.pioneers.buy.times", String.valueOf(known ? e.choices()[k] : CHOICES[k]));
            boolean possible = e != null && k < e.ok().length && e.ok()[k] != 0;
            TextFit.center(g, font, label, x + 1, by + 4, 36, possible || e == null ? (on ? TEXT : DIM) : 0xFF5A646E, 0.75F, this.hover);
        }
        boolean can = e != null && choice < e.totals().length;
        boolean possible = can && e.ok()[choice] != 0;
        int ax = left + W - 128;
        boolean hover = can && mx >= ax && mx < ax + 120 && my >= by && my < by + 16;
        g.fill(ax, by, ax + 120, by + 16, !can ? 0xFF182027 : hover ? 0xFF2F4A3E : possible ? 0xFF26392F : 0xFF2A2A2A);
        g.fill(ax, by + 15, ax + 120, by + 16, possible ? ACCENT : 0xFF3A2E26);
        Component label = can ? Component.translatable("stall.pioneers.buy.do", String.valueOf(e.choices()[choice]),
              Long.toString(e.totals()[choice])) : Component.translatable("stall.pioneers.buy.pick");
        TextFit.center(g, font, label, ax + 2, by + 4, 116, can ? TEXT : DIM, 0.75F, this.hover);
        if (!view.message().getString().isEmpty()) {
            TextFit.center(g, font, view.message(), left + 8, top + H - 13, W - 16, WARN, 0.8F, this.hover);
        }
        super.render(g, mx, my, pt);
        int hr = hoverRow(mx, my);
        if (hr >= 0 && mx < left + 30) {
            TooltipLayer.item(font, rows.get(hr).stack(), mx, my);
        } else {
            this.hover.render(g, font);
        }
    }

    private boolean inRow(int mx, int my, int r) {
        int y = top + LIST_Y + 2 + r * ROW;
        return mx >= left + 9 && mx < left + W - 9 && my >= y && my < y + ROW;
    }

    private int hoverRow(int mx, int my) {
        for (int r = 0; r < ROWS && scroll + r < rows().size(); r++) {
            if (inRow(mx, my, r)) {
                return scroll + r;
            }
        }
        return -1;
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        int hr = hoverRow(mx, my);
        if (hr >= 0) {
            selected = hr;
            return true;
        }
        int by = top + LIST_Y + ROWS * ROW + 10;
        for (int k = 0; k < 4; k++) {
            int x = left + 8 + k * 40;
            if (mx >= x && mx < x + 38 && my >= by && my < by + 16) {
                choice = k;
                return true;
            }
        }
        int ax = left + W - 128;
        if (selected >= 0 && selected < rows().size() && mx >= ax && mx < ax + 120 && my >= by && my < by + 16) {
            StallClient.Entry e = rows().get(selected);
            if (choice < e.choices().length) {
                CompoundTag t = new CompoundTag();
                t.putLong("pos", view.pos());
                t.putInt("i", e.index());
                t.putInt("n", e.choices()[choice]);
                RpNet.toServer(RpActions.STALL_BUY, t);
            }
            return true;
        }
        return super.mouseClicked(mxd, myd, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        scroll = Math.max(0, Math.min(Math.max(0, rows().size() - ROWS), scroll - (int) Math.signum(delta)));
        return true;
    }
}
