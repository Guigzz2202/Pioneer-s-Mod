package fr.pioneers.client.paperwork;

import fr.pioneers.client.rp.CodexBook;
import fr.pioneers.client.rp.RpClient;
import fr.pioneers.client.rp.RpGui;
import fr.pioneers.economy.contract.InvoiceItem;
import fr.pioneers.economy.mail.Letters;
import fr.pioneers.paperwork.PaperBinKind;
import fr.pioneers.paperwork.PaperBinMenu;
import fr.pioneers.paperwork.PaperBinRules;
import fr.pioneers.ruins.LoreItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16) : ecran d'un bac a papiers, au papier du Carnet. BAC A LETTRES : une LISTE lisible (point de cire = non lue, icone de la
 * sorte, objet, expediteur, date), tri (date, expediteur, sorte), filtre (texte dans l'expediteur ou l'objet), « non lues » ; clic = lire (la
 * lettre ou la facture s'ouvre, Echap revient au bac), clic droit = prendre ; boutons « Tout ranger » (papiers de l'inventaire) et « Relever
 * la boite aux lettres ». BAC D'ARCHIVES : les chapitres du Codex a gauche avec la PROGRESSION de la collection (ecrits ranges / total, sans
 * reveler les textes absents), les ecrits du chapitre a droite (titre, nombre), tri Codex / titre ; clic = lire (classe au Codex), clic droit
 * = prendre. Maquette fidele : {@code T:rp/carnet16_preview.py} (memes constantes).
 */
public class PaperBinScreen extends AbstractContainerScreen<PaperBinMenu> {

    public static final int PAPER = 0xFFF3E7CC, PAPER_DARK = 0xFFE2D2B0, INK = 0xFF24365E, INK_LIGHT = 0xFF4A5F8C, TEXT = 0xFF3A2E22, DIM = 0xFF7A6A55,
          FAINT = 0xFFB9A889, WAX = 0xFF9A2E25, PLUS = 0xFF2F6B30;
    /** Mise en page (coordonnees du panneau) : titre, pastilles, filtre, liste, boutons. */
    public static final int PAD = 8, TITLE_Y = 6, CHIPS_Y = 19, CHIP_H = 12, SORT_W = 50, FILTER_Y = 33, LIST_Y = 48, LIST_BOTTOM = 128, ROW = 20,
          BTN_Y = 131, BTN_H = 14, CELLS_Y = 33, CELL_W = 18, CELL_H = 12, CH_NAME_Y = 49, E_LIST_Y = 60, E_ROW = 11;
    private static PaperBinRules.Sort sort = PaperBinRules.Sort.DATE;
    private static boolean unreadOnly, byTitle;
    private static int chapter = 1;
    private EditBox filter;
    private int scroll;
    private final List<Object[]> zones = new ArrayList<>();
    private final List<Component> hover = new ArrayList<>();

    public PaperBinScreen(PaperBinMenu menu, Inventory inv, Component title) {
        super(menu, inv, title);
        imageWidth = PaperBinMenu.W;
        imageHeight = PaperBinMenu.H;
        inventoryLabelY = 10000;
        titleLabelY = 10000;
    }

    @Override
    protected void init() {
        super.init();
        if (menu.kind == PaperBinKind.LETTERS) {
            filter = new EditBox(font, leftPos + PAD + 14, topPos + FILTER_Y, imageWidth - 2 * PAD - 14, 12, Component.translatable("paperwork.pioneers.filter"));
            filter.setMaxLength(32);
            filter.setHint(Component.translatable("paperwork.pioneers.filter").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            filter.setResponder(v -> scroll = 0);
            addRenderableWidget(filter);
        }
    }

    // ------------------------------------------------------------------ outils
    private void zone(int x, int y, int w, int h, Runnable left, @Nullable Runnable right) {
        zones.add(new Object[]{leftPos + x, topPos + y, w, h, left, right});
    }

    private void tip(int mx, int my, int x, int y, int w, int h, Component... lines) {
        if (mx >= leftPos + x && my >= topPos + y && mx < leftPos + x + w && my < topPos + y + h) {
            hover.clear();
            hover.addAll(List.of(lines));
        }
    }

    /** Echelle d'un texte dans {@code w} : {@code scale} s'il tient, resserre jusqu'a 0,75 ; negative s'il faut couper. */
    private float scaleOf(Component c, int w, float scale) {
        int full = Math.max(1, font.width(c));
        if (full * scale <= w + 0.01F) {
            return scale;
        }
        float k = w / (float) full;
        return k >= Math.min(scale, RpGui.MIN_SCALE) ? k : -1;
    }

    /** Largeur dessinee d'un texte borne a {@code w}. */
    private int measure(Component c, int w, float scale) {
        float k = scaleOf(c, w, scale);
        return k > 0 ? (int) Math.ceil(font.width(c) * k) : w;
    }

    /** Texte borne : a l'echelle si possible, resserre jusqu'a 0,75, puis coupe par « … » (texte entier en infobulle). */
    private int fit(GuiGraphics g, Component c, int x, int y, int w, int color, float scale, int mx, int my) {
        float k = scaleOf(c, w, scale);
        Component shown = c;
        if (k < 0) {
            k = Math.min(scale, RpGui.MIN_SCALE);
            shown = Component.literal(font.plainSubstrByWidth(c.getString(), (int) (w / k) - font.width("…")).stripTrailing() + "…").withStyle(c.getStyle());
            tip(mx, my, x, y, w, 9, c);
        }
        g.pose().pushPose();
        g.pose().translate(leftPos + x, topPos + y + (scale - k) * 4, 0);
        g.pose().scale(k, k, 1);
        g.drawString(font, shown, 0, 0, color, false);
        g.pose().popPose();
        return Math.min(w, (int) Math.ceil(font.width(shown) * k));
    }

    private int fitRight(GuiGraphics g, Component c, int rx, int y, int w, int color, float scale, int mx, int my) {
        return fit(g, c, rx - measure(c, w, scale), y, w, color, scale, mx, my);
    }

    private void chip(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean on, Runnable action) {
        boolean over = mx >= leftPos + x && my >= topPos + y && mx < leftPos + x + w && my < topPos + y + CHIP_H;
        g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + CHIP_H, on ? 0x50243660 : over ? 0x28243660 : 0x10243660);
        frame(g, x, y, w, CHIP_H, on ? INK : INK_LIGHT);
        int tw = Math.min(w - 4, (int) Math.ceil(font.width(label) * 0.75F));
        fit(g, label, x + (w - tw) / 2, y + 3, w - 4, on ? INK : INK_LIGHT, 0.75F, mx, my);
        zone(x, y, w, CHIP_H, action, null);
    }

    private void button(GuiGraphics g, int mx, int my, int x, int y, int w, Component label, boolean enabled, Runnable action) {
        boolean over = enabled && mx >= leftPos + x && my >= topPos + y && mx < leftPos + x + w && my < topPos + y + BTN_H;
        g.fill(leftPos + x, topPos + y, leftPos + x + w, topPos + y + BTN_H, over ? 0x40243660 : 0x18243660);
        frame(g, x, y, w, BTN_H, enabled ? INK : FAINT);
        int tw = Math.min(w - 4, font.width(label));
        fit(g, label, x + (w - tw) / 2, y + 3, w - 4, enabled ? INK : FAINT, 1F, mx, my);
        if (enabled) {
            zone(x, y, w, BTN_H, action, null);
        }
    }

    private void frame(GuiGraphics g, int x, int y, int w, int h, int c) {
        int X = leftPos + x, Y = topPos + y;
        g.fill(X, Y, X + w, Y + 1, c);
        g.fill(X, Y + h - 1, X + w, Y + h, c);
        g.fill(X, Y, X + 1, Y + h, c);
        g.fill(X + w - 1, Y, X + w, Y + h, c);
    }

    private void icon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        RpGui.blitBlended(g, tex, leftPos + x, topPos + y, size);
    }

    // ------------------------------------------------------------------ dessin
    @Override
    protected void renderBg(@NotNull GuiGraphics g, float pt, int mx, int my) {
        int x = leftPos, y = topPos;
        g.fill(x - 1, y - 1, x + imageWidth + 1, y + imageHeight + 1, 0xFF6B5638);
        g.fill(x, y, x + imageWidth, y + imageHeight, PAPER);
        g.fill(x + 3, y + 3, x + imageWidth - 3, y + imageHeight - 3, 0xFFF6EDD6);
        for (Slot s : menu.slots) {
            if (s.isActive()) {
                g.fill(x + s.x - 1, y + s.y - 1, x + s.x + 17, y + s.y + 17, FAINT);
                g.fill(x + s.x, y + s.y, x + s.x + 16, y + s.y + 16, PAPER_DARK);
            }
        }
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        zones.clear();
        hover.clear();
        renderBackground(g);
        super.render(g, mx, my, pt);
        if (menu.kind == PaperBinKind.LETTERS) {
            drawLetters(g, mx, my);
        } else {
            drawArchives(g, mx, my);
        }
        if (!hover.isEmpty()) {
            List<FormattedCharSequence> lines = new ArrayList<>();
            for (Component c : hover) {
                lines.addAll(font.split(c, 220));
            }
            g.renderTooltip(font, lines, mx, my);
        } else {
            renderTooltip(g, mx, my);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics g, int mx, int my) {
    }

    private int stored() {
        int n = 0;
        for (int i = 0; i < menu.kind.slots; i++) {
            if (!menu.paper(i).isEmpty()) {
                n++;
            }
        }
        return n;
    }

    private void header(GuiGraphics g, int mx, int my, String icon, Component right) {
        icon(g, RpGui.stat(icon), PAD, TITLE_Y - 1, 10);
        int rw = fitRight(g, right, imageWidth - PAD, TITLE_Y + 1, 90, DIM, 0.75F, mx, my);
        fit(g, title, PAD + 14, TITLE_Y, imageWidth - 2 * PAD - 14 - rw - 6, INK, 1F, mx, my);
        g.fill(leftPos + PAD, topPos + 16, leftPos + imageWidth - PAD, topPos + 17, FAINT);
        tip(mx, my, imageWidth - PAD - rw, TITLE_Y, rw, 9, Component.translatable("paperwork.pioneers.help." + menu.kind.id));
    }

    // ------------------------------------------------------------------ bac a lettres
    private void drawLetters(GuiGraphics g, int mx, int my) {
        header(g, mx, my, "letter", Component.translatable("paperwork.pioneers.places", stored(), menu.kind.slots));
        int x = PAD;
        for (PaperBinRules.Sort s : PaperBinRules.Sort.values()) {
            chip(g, mx, my, x, CHIPS_Y, SORT_W, Component.translatable("paperwork.pioneers.sort." + s.name().toLowerCase(Locale.ROOT)), sort == s, () -> {
                sort = s;
                scroll = 0;
            });
            x += SORT_W + 3;
        }
        List<PaperBinRules.Paper> all = new ArrayList<>();
        int unread = 0;
        for (int i = 0; i < menu.kind.slots; i++) {
            PaperBinRules.Paper p = PaperBinRules.paper(i, menu.paper(i));
            if (p != null) {
                all.add(p);
                unread += p.read() ? 0 : 1;
            }
        }
        chip(g, mx, my, x, CHIPS_Y, imageWidth - PAD - x, Component.translatable("paperwork.pioneers.unread", unread), unreadOnly, () -> {
            unreadOnly = !unreadOnly;
            scroll = 0;
        });
        icon(g, RpGui.stat("history"), PAD, FILTER_Y + 2, 9);
        List<PaperBinRules.Paper> shown = PaperBinRules.sorted(all, sort, filter == null ? "" : filter.getValue(), unreadOnly);
        int h = LIST_BOTTOM - LIST_Y, rows = h / ROW;
        scroll = Mth.clamp(scroll, 0, Math.max(0, shown.size() - rows));
        g.fill(leftPos + PAD, topPos + LIST_Y - 1, leftPos + imageWidth - PAD, topPos + LIST_BOTTOM + 1, 0x0C243660);
        if (shown.isEmpty()) {
            fit(g, Component.translatable(all.isEmpty() ? "paperwork.pioneers.empty.letter_bin" : "paperwork.pioneers.none_match"), PAD + 4, LIST_Y + 4,
                  imageWidth - 2 * PAD - 8, DIM, 1F, mx, my);
        }
        for (int r = 0; r < rows && scroll + r < shown.size(); r++) {
            PaperBinRules.Paper p = shown.get(scroll + r);
            int y = LIST_Y + r * ROW;
            letterRow(g, mx, my, p, y);
        }
        if (shown.size() > rows) {
            int track = h, bar = Math.max(6, track * rows / shown.size()), by = LIST_Y + (track - bar) * scroll / Math.max(1, shown.size() - rows);
            g.fill(leftPos + imageWidth - PAD - 2, topPos + by, leftPos + imageWidth - PAD, topPos + by + bar, INK_LIGHT);
        }
        int bw = (imageWidth - 2 * PAD - 4) / 2;
        button(g, mx, my, PAD, BTN_Y, bw, Component.translatable("paperwork.pioneers.deposit"), true, () -> menu.ask("deposit", 0));
        button(g, mx, my, PAD + bw + 4, BTN_Y, bw, Component.translatable("paperwork.pioneers.mailbox"), true, () -> menu.ask("mailbox", 0));
        tip(mx, my, PAD + bw + 4, BTN_Y, bw, BTN_H, Component.translatable("paperwork.pioneers.mailbox.tip"));
    }

    private void letterRow(GuiGraphics g, int mx, int my, PaperBinRules.Paper p, int y) {
        int x0 = PAD, w = imageWidth - 2 * PAD - 4;
        boolean over = mx >= leftPos + x0 && my >= topPos + y && mx < leftPos + x0 + w && my < topPos + y + ROW - 1;
        if (over) {
            g.fill(leftPos + x0, topPos + y, leftPos + x0 + w, topPos + y + ROW - 1, 0x22243660);
        }
        if (!p.read()) {
            icon(g, RpGui.stat("unread"), x0 + 1, y + 6, 8);
        }
        boolean invoice = "invoice".equals(p.kind()), document = "document".equals(p.kind());
        CompoundTag docTag = document ? InvoiceItem.document(menu.paper(p.slot())) : null;
        icon(g, docTag != null ? fr.pioneers.client.economy.DocumentPaper.icon(docTag.getString("type")) : RpGui.stat(invoice ? "contract" : "letter"),
              x0 + 10, y + 5, 9);
        Component kind = docTag != null ? fr.pioneers.client.economy.DocumentPaper.typeName(docTag.getString("type"))
              : invoice ? Component.translatable("item.pioneers.contract_invoice") : Letters.known(p.kind().substring(7))
              ? Component.translatable("mail.pioneers.kind." + p.kind().substring(7)) : Component.translatable("item.pioneers.letter");
        Component subject = p.subject().isEmpty() ? kind : Component.literal(p.subject());
        //ligne 1 : l'objet (ou la sorte) ; ligne 2 : l'expediteur a gauche, la date a droite
        fit(g, subject, x0 + 22, y + 2, w - 24, p.read() ? TEXT : INK, 1F, mx, my);
        ZoneDate d = ZoneDate.of(menu.paper(p.slot()));
        int dw = d == null ? 0 : fitRight(g, Component.literal(d.text()), x0 + w - 2, y + 11, 54, DIM, 0.75F, mx, my);
        Component who = p.sender().isEmpty() ? kind : Component.literal(p.sender());
        fit(g, who, x0 + 22, y + 11, w - 24 - dw - 6, INK_LIGHT, 0.75F, mx, my);
        g.fill(leftPos + x0, topPos + y + ROW - 1, leftPos + x0 + w, topPos + y + ROW, 0x18243660);
        int slot = p.slot();
        tip(mx, my, x0, y, w, ROW - 1, kind, Component.translatable("paperwork.pioneers.row.tip"));
        zone(x0, y, w, ROW - 1, () -> read(slot), () -> menu.ask("take", slot));
    }

    /** Lire la case : la lettre ou la facture s'ouvre ici (Echap revient au bac) ; le serveur la marque lue. */
    private void read(int slot) {
        ItemStack s = menu.paper(slot);
        CompoundTag l = Letters.data(s);
        //1.0.5-beta (lot 26) : facture d'avant (migree a la lecture) ou document imprime : la feuille mise en forme
        CompoundTag doc = InvoiceItem.document(s);
        menu.ask("read", slot);
        if (l != null) {
            Minecraft.getInstance().setScreen(new fr.pioneers.client.economy.LetterScreen(l, this));
        } else if (doc != null) {
            Minecraft.getInstance().setScreen(new fr.pioneers.client.economy.DocumentScreen(doc, this));
        }
    }

    /** Date d'un papier au format court de la langue (fuseau du serveur ecrit dans le papier). */
    record ZoneDate(String text) {

        @Nullable
        static ZoneDate of(ItemStack s) {
            CompoundTag l = Letters.data(s);
            CompoundTag inv = InvoiceItem.data(s);
            CompoundTag doc = inv == null && s.getTag() != null && s.getTag().contains(InvoiceItem.DOCUMENT) ? s.getTag().getCompound(InvoiceItem.DOCUMENT) : null;
            long ms = l != null ? l.getLong("d") : inv != null ? inv.getLong("printed") : doc != null ? doc.getLong("printedMs") : 0;
            String z = l != null ? l.getString("z") : inv != null ? inv.getString("zone") : doc != null ? doc.getString("zone") : "";
            if (ms <= 0) {
                return null;
            }
            java.time.ZoneId zone;
            try {
                zone = java.time.ZoneId.of(z);
            } catch (java.time.DateTimeException e) {
                zone = java.time.ZoneId.systemDefault();
            }
            return new ZoneDate(fr.pioneers.client.economy.ContractsTab.date(ms, zone, true));
        }
    }

    // ------------------------------------------------------------------ bac d'archives
    private void drawArchives(GuiGraphics g, int mx, int my) {
        Map<String, Integer> have = new HashMap<>();
        Map<String, Integer> slotOf = new HashMap<>();
        for (int i = 0; i < menu.kind.slots; i++) {
            String id = PaperBinRules.loreId(menu.paper(i));
            if (id != null) {
                have.merge(id, menu.paper(i).getCount(), Integer::sum);
                slotOf.putIfAbsent(id, i);
            }
        }
        int distinct = 0, total = 0;
        Map<Integer, int[]> progress = new LinkedHashMap<>();
        for (int c = 1; c <= CodexBook.CHAPTERS; c++) {
            List<String> ids = chapterIds(c);
            int n = 0;
            for (String id : ids) {
                n += have.containsKey(id) ? 1 : 0;
            }
            progress.put(c, new int[]{n, ids.size()});
            distinct += n;
            total += ids.size();
        }
        header(g, mx, my, "archive", Component.translatable("paperwork.pioneers.collection", distinct, total));
        chip(g, mx, my, PAD, CHIPS_Y, SORT_W, Component.translatable("paperwork.pioneers.sort.codex"), !byTitle, () -> byTitle = false);
        chip(g, mx, my, PAD + SORT_W + 3, CHIPS_Y, SORT_W, Component.translatable("paperwork.pioneers.sort.title"), byTitle, () -> byTitle = true);
        //1.0.5-beta (lot 19) : un compteur NOMME (« Places : 55 / 192 ») sous la collection (« Collection : 55 / 176 ») : avant, « 55 / 128 » seul
        int pw = fitRight(g, Component.translatable("paperwork.pioneers.places", stored(), menu.kind.slots), imageWidth - PAD, CHIPS_Y + 3, 90, DIM, 0.75F,
              mx, my);
        tip(mx, my, imageWidth - PAD - pw, CHIPS_Y + 2, pw, 9, Component.translatable("paperwork.pioneers.help.places"));
        //une case par chapitre : numero, remplissage = collection (rangés / total), nom et compte en infobulle
        for (int c = 1; c <= CodexBook.CHAPTERS; c++) {
            int[] pr = progress.get(c);
            int x = PAD + (c - 1) * CELL_W;
            boolean on = c == chapter, empty = pr[1] == 0;
            g.fill(leftPos + x, topPos + CELLS_Y, leftPos + x + CELL_W - 1, topPos + CELLS_Y + CELL_H, on ? 0x40243660 : 0x10243660);
            g.fill(leftPos + x, topPos + CELLS_Y + CELL_H - 3, leftPos + x + (CELL_W - 1) * pr[0] / Math.max(1, pr[1]), topPos + CELLS_Y + CELL_H,
                  pr[0] == pr[1] && !empty ? PLUS : INK_LIGHT);
            if (on) {
                frame(g, x, CELLS_Y, CELL_W - 1, CELL_H, INK);
            }
            Component num = Component.literal(Integer.toString(c));
            int nw = measure(num, CELL_W - 3, 0.75F);
            fit(g, num, x + (CELL_W - 1 - nw) / 2, CELLS_Y + 2, CELL_W - 3, empty ? FAINT : on ? INK : INK_LIGHT, 0.75F, mx, my);
            tip(mx, my, x, CELLS_Y, CELL_W - 1, CELL_H, Component.translatable("codex.pioneers.chapter." + c),
                  Component.translatable("paperwork.pioneers.chapter.tip", pr[0], pr[1]));
            if (!empty) {
                int cc = c;
                zone(x, CELLS_Y, CELL_W - 1, CELL_H, () -> {
                    chapter = cc;
                    scroll = 0;
                }, null);
            }
        }
        int[] pr = progress.getOrDefault(chapter, new int[]{0, 0});
        int cw = fitRight(g, Component.literal(pr[0] + " / " + pr[1]), imageWidth - PAD, CH_NAME_Y + 1, 40, pr[0] == pr[1] ? PLUS : DIM, 0.75F, mx, my);
        fit(g, Component.literal(chapter + ". ").append(Component.translatable("codex.pioneers.chapter." + chapter)), PAD, CH_NAME_Y, imageWidth - 2 * PAD
              - cw - 6, INK, 1F, mx, my);
        //ecrits ranges du chapitre ; les absents ne sont que comptes (rien n'est revele)
        int ex = PAD, ew = imageWidth - 2 * PAD;
        List<String> ids = new ArrayList<>(chapterIds(chapter));
        ids.removeIf(id -> !have.containsKey(id));
        if (byTitle) {
            ids.sort((a, b) -> title(a).getString().compareToIgnoreCase(title(b).getString()));
        }
        int missing = pr[1] - ids.size();
        int erows = (LIST_BOTTOM - E_LIST_Y) / E_ROW - (missing > 0 ? 1 : 0);
        scroll = Mth.clamp(scroll, 0, Math.max(0, ids.size() - erows));
        g.fill(leftPos + ex, topPos + E_LIST_Y - 1, leftPos + ex + ew, topPos + LIST_BOTTOM + 1, 0x0C243660);
        int y = E_LIST_Y;
        for (int r = 0; r < erows && scroll + r < ids.size(); r++, y += E_ROW) {
            String id = ids.get(scroll + r);
            int slot = slotOf.get(id);
            boolean over = mx >= leftPos + ex && my >= topPos + y && mx < leftPos + ex + ew && my < topPos + y + E_ROW - 1;
            if (over) {
                g.fill(leftPos + ex, topPos + y, leftPos + ex + ew, topPos + y + E_ROW - 1, 0x22243660);
            }
            int n = have.get(id);
            int nw = n > 1 ? fitRight(g, Component.literal("×" + n), ex + ew - 2, y + 2, 20, DIM, 0.75F, mx, my) : 0;
            fit(g, title(id), ex + 2, y + 1, ew - 4 - nw - (nw > 0 ? 3 : 0), TEXT, 1F, mx, my);
            if (id.startsWith("note_")) {
                tip(mx, my, ex, y, ew, E_ROW - 1, Component.translatable("codex.pioneers." + id), Component.translatable("paperwork.pioneers.row.tip_lore"));
            } else {
                tip(mx, my, ex, y, ew, E_ROW - 1, title(id), Component.translatable("paperwork.pioneers.row.tip_lore"));
            }
            zone(ex, y, ew, E_ROW - 1, () -> menu.ask("read", slot), () -> menu.ask("take", slot));
        }
        if (missing > 0) {
            fit(g, Component.translatable("paperwork.pioneers.missing", missing), ex + 2, y + 2, ew - 4, FAINT, 0.75F, mx, my);
        }
        button(g, mx, my, PAD, BTN_Y, 120, Component.translatable("paperwork.pioneers.deposit"), true, () -> menu.ask("deposit", 0));
    }

    /** Entrees d'un chapitre du Codex (notes de parchemin pour le chapitre des notes). */
    static List<String> chapterIds(int c) {
        List<String> out = new ArrayList<>();
        if (c == CodexBook.NOTES_CHAPTER) {
            out.addAll(CodexBook.notes());
        } else {
            for (CodexBook.Entry e : CodexBook.chapter(c)) {
                out.add(e.id());
            }
        }
        return out;
    }

    static Component title(String id) {
        if (id.startsWith("note_")) {
            //une note n'a pas de titre : son numero (le texte en infobulle et a la lecture)
            return Component.translatable("paperwork.pioneers.note_title", id.substring(5).replaceFirst("^0+(?=.)", ""));
        }
        if (!I18n.exists("codex.pioneers." + id + ".title")) {
            return Component.translatable("codex.pioneers." + id);
        }
        return Component.translatable("codex.pioneers." + id + ".title");
    }

    // ------------------------------------------------------------------ souris
    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (int i = zones.size() - 1; i >= 0; i--) {
            Object[] z = zones.get(i);
            int x = (int) z[0], y = (int) z[1], w = (int) z[2], h = (int) z[3];
            if (mx >= x && my >= y && mx < x + w && my < y + h) {
                Runnable r = button == 1 ? (Runnable) z[5] : button == 0 ? (Runnable) z[4] : null;
                if (r != null) {
                    RpClient.play(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.3F);
                    r.run();
                    return true;
                }
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double delta) {
        int d = (int) -Math.signum(delta);
        scroll = Math.max(0, scroll + d);
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (filter != null && filter.isFocused() && key != 256) {
            return filter.keyPressed(key, scan, mods) || true;
        }
        return super.keyPressed(key, scan, mods);
    }
}
