package fr.pioneers.society.economy.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TextFit;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : ecran d'echange commun (villageois, Colporteurs) : onglets Acheter, Vendre et service eventuel (reparation,
 * affutage), liste avec icone, nom, prix et pourcentage applique (vert sous la base, rouge au-dessus, avec un signe), quantite (1, 8, 64,
 * tout), quota du jour, solde. Tout chiffre affiche vient du serveur ; l'ecran n'envoie que la ligne et la quantite voulues.
 * <p>
 * 1.9.1 (retours de Guillaume : textes qui se superposaient) : chaque ligne mesure d'abord sa partie droite (pourcentage, prix, quota) et
 * le nom et l'information prennent la place qui reste ; le nom du marchand laisse la place au solde. Tout texte passe par {@link TextFit}
 * (resserre, puis coupe ; l'infobulle de l'objet donne deja son nom entier).
 */
public class TradeScreen extends Screen {

    private static final int W = 300, H = 232, ROW = 18, ROWS = 8;
    private static final int GOOD = 0xFF6FCF7F, BAD = 0xFFE3735F, TEXT = 0xFFE8E8E8, DIM = 0xFF8A96A2, ACCENT = 0xFF3FD0A0;
    private static final int[] QTY = {1, 8, 64, Integer.MAX_VALUE};

    private record Row(ItemStack stack, String id, int stock, int have, long unit, long lot, int pct, int left, String refusal, int slot) {
    }

    private Component name = Component.empty();
    private long balance;
    private final List<Row> buy = new ArrayList<>(), sell = new ArrayList<>(), services = new ArrayList<>();
    private String service;
    private Component message = Component.empty();
    private int tab, selected = -1, qty, scroll, left, top, tax;
    private final TextFit.Hover hover = new TextFit.Hover();

    public TradeScreen(CompoundTag t) {
        super(Component.translatable("economy.pioneers.trade.title"));
        read(t);
    }

    public void read(CompoundTag t) {
        name = Component.Serializer.fromJson(t.getString("name"));
        balance = t.getLong("balance");
        tax = t.getInt("tax");
        buy.clear();
        for (Tag x : t.getList("buy", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            ItemStack s = ItemStack.of(c.getCompound("stack"));
            buy.add(new Row(s, "", c.getInt("stock"), 0, c.getLong("unit"), c.getLong("lot"), c.getInt("pct"), -1, c.getString("refusal"), -1));
        }
        sell.clear();
        for (Tag x : t.getList("sell", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            ItemStack s = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(c.getString("item"))));
            sell.add(new Row(s, c.getString("item"), -1, c.getInt("have"), c.getLong("unit"), c.getLong("unit"), c.getInt("pct"), c.getInt("left"),
                  c.getString("refusal"), -1));
        }
        //on montre d'abord ce que le joueur possede
        sell.sort((a, b) -> Boolean.compare(b.have() > 0, a.have() > 0));
        service = t.contains("service") ? t.getString("service") : null;
        services.clear();
        for (Tag x : t.getList("services", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            services.add(new Row(ItemStack.of(c.getCompound("stack")), "", -1, 1, c.getLong("price") * 100, c.getLong("price") * 100, 0, -1, "",
                  c.getInt("slot")));
        }
        message = t.contains("message") ? Component.translatable(t.getString("message")) : Component.empty();
        if (selected >= rows().size()) {
            selected = -1;
        }
    }

    private List<Row> rows() {
        return tab == 0 ? buy : tab == 1 ? sell : services;
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

    @Override
    public void removed() {
        CompoundTag t = new CompoundTag();
        t.putBoolean("close", true);
        RpNet.toServer(RpActions.TRADE_QUOTE, t);
        super.removed();
    }

    private static String tokens(long centi) {
        long whole = centi / 100, frac = Math.abs(centi % 100);
        return frac == 0 ? Long.toString(whole) : whole + "," + (frac < 10 ? "0" : "") + frac;
    }

    @Override
    public void render(@NotNull GuiGraphics g, int mx, int my, float pt) {
        renderBackground(g);
        this.hover.at(mx, my);
        GuiDraw.panel(g, left, top, W, H);
        //1.9.1 : le solde est mesure d'abord (120 px au plus), le nom du marchand prend le reste
        int bw = TextFit.right(g, font, Component.translatable("economy.pioneers.tokens", Long.toString(balance)), left + W - 128, top + 7, 120,
              ACCENT, 1F, this.hover);
        TextFit.fit(g, font, name, left + 8, top + 7, W - 16 - bw - 8, TEXT, 1F, this.hover);
        //onglets
        String[] tabs = service == null ? new String[]{"buy", "sell"} : new String[]{"buy", "sell", service};
        for (int i = 0; i < tabs.length; i++) {
            int x = left + 8 + i * 74;
            boolean on = i == tab, hover = mx >= x && mx < x + 70 && my >= top + 20 && my < top + 34;
            g.fill(x, top + 20, x + 70, top + 34, on ? 0xFF2A3540 : hover ? 0xFF222B33 : 0xFF182027);
            if (on) {
                g.fill(x, top + 33, x + 70, top + 34, ACCENT);
            }
            TextFit.center(g, font, Component.translatable("economy.pioneers.trade.tab." + tabs[i]), x + 2, top + 24, 66, on ? TEXT : DIM, 0.85F,
                  this.hover);
        }
        //liste : a droite le pourcentage puis le prix (mesures), a gauche le nom et l'information, le quota au bout de l'information
        List<Row> rows = rows();
        GuiDraw.screen(g, left + 8, top + 38, W - 16, ROWS * ROW + 4);
        scroll = Math.max(0, Math.min(scroll, Math.max(0, rows.size() - ROWS)));
        for (int r = 0; r < ROWS && scroll + r < rows.size(); r++) {
            int i = scroll + r;
            Row row = rows.get(i);
            int y = top + 40 + r * ROW;
            boolean sel = i == selected, hover = mx >= left + 9 && mx < left + W - 9 && my >= y && my < y + ROW;
            if (sel || hover) {
                g.fill(left + 9, y, left + W - 9, y + ROW, sel ? 0xFF253240 : 0xFF1A232C);
            }
            g.renderItem(row.stack(), left + 11, y + 1);
            g.renderItemDecorations(font, row.stack(), left + 11, y + 1);
            boolean refused = !row.refusal().isEmpty();
            int limit = left + W - 11;
            if (!refused) {
                if (tab < 2) {
                    //vert sous la base, rouge au-dessus, avec un signe
                    int base = 100;
                    boolean good = tab == 0 ? row.pct() <= base : row.pct() >= 50;
                    String sign = tab == 0 ? (row.pct() < base ? "−" : row.pct() > base ? "+" : "") : "";
                    limit -= TextFit.right(g, font, Component.literal(sign + row.pct() + "%"), limit - 30, y + 6, 30, good ? GOOD : BAD, 0.65F, null)
                          + 4;
                }
                limit -= TextFit.right(g, font, Component.literal(tokens(tab == 0 ? row.lot() : row.unit())), limit - 70, y + 5, 70, TEXT, 0.85F,
                      null) + 6;
            }
            TextFit.fit(g, font, row.stack().getHoverName(), left + 31, y + 2, limit - left - 31, refused ? DIM : TEXT, 0.75F, null);
            Component info = tab == 0 ? (row.stock() < 0 ? Component.translatable("economy.pioneers.trade.lot", row.stack().getCount())
                  : Component.translatable("economy.pioneers.trade.stock", row.stock()))
                  : tab == 1 ? Component.translatable("economy.pioneers.trade.have", row.have())
                  : Component.translatable("economy.pioneers.trade.damage", row.stack().getMaxDamage() - row.stack().getDamageValue(),
                        row.stack().getMaxDamage());
            int infoEnd = limit;
            if (!refused && tab == 1 && row.left() >= 0) {
                infoEnd -= TextFit.right(g, font, Component.translatable("economy.pioneers.trade.quota", row.left()), limit - 70, y + 10, 70,
                      row.left() == 0 ? BAD : DIM, 0.65F, null) + 6;
            }
            TextFit.fit(g, font, refused ? Component.translatable(row.refusal()) : info, left + 31, y + 10, infoEnd - left - 31, DIM, 0.65F, null);
        }
        if (rows.isEmpty()) {
            TextFit.center(g, font, Component.translatable("economy.pioneers.trade.empty." + (tab == 0 ? "buy" : tab == 1 ? "sell" : "service")),
                  left + 15, top + 40 + 3 * ROW, W - 30, DIM, 0.85F, this.hover);
        }
        //quantite et action
        int by = top + 44 + ROWS * ROW;
        if (tab < 2) {
            for (int k = 0; k < QTY.length; k++) {
                int x = left + 8 + k * 30;
                boolean on = qty == k, hover = mx >= x && mx < x + 28 && my >= by && my < by + 14;
                g.fill(x, by, x + 28, by + 14, on ? 0xFF2A3540 : hover ? 0xFF222B33 : 0xFF182027);
                TextFit.center(g, font, k == 3 ? Component.translatable("economy.pioneers.trade.all") : Component.literal(Integer.toString(QTY[k])),
                      x + 1, by + 3, 26, on ? TEXT : DIM, 0.8F, this.hover);
            }
        }
        Row row = selected >= 0 && selected < rows.size() ? rows.get(selected) : null;
        boolean can = row != null && row.refusal().isEmpty();
        long preview = 0;
        int n = 0;
        if (row != null) {
            if (tab == 0) {
                n = qty == 3 ? (row.stock() < 0 ? 64 : row.stock()) : Math.min(QTY[qty], row.stock() < 0 ? 64 : row.stock());
                preview = row.lot() * n / 100;
            } else if (tab == 1) {
                n = Math.min(qty == 3 ? row.have() : QTY[qty], row.have());
                if (row.left() >= 0) {
                    n = Math.min(n, row.left());
                }
                preview = row.unit() * n / 100;
            } else {
                n = 1;
                preview = row.unit() / 100;
            }
            can &= n > 0;
        }
        int ax = left + W - 108;
        boolean hover = can && mx >= ax && mx < ax + 100 && my >= by && my < by + 14;
        g.fill(ax, by, ax + 100, by + 14, !can ? 0xFF182027 : hover ? 0xFF2F4A3E : 0xFF26392F);
        g.fill(ax, by + 13, ax + 100, by + 14, can ? ACCENT : 0xFF262E36);
        Component label = Component.translatable("economy.pioneers.trade.do." + (tab == 0 ? "buy" : tab == 1 ? "sell" : "service"),
              Long.toString(preview));
        TextFit.center(g, font, label, ax + 2, by + 3, 96, can ? TEXT : DIM, 0.8F, this.hover);
        if (!message.getString().isEmpty()) {
            TextFit.center(g, font, message, left + 8, top + H - 14, W - 16, 0xFFE3A23B, 0.8F, this.hover);
        } else if (tab == 1 && tax > 0) {
            //impot d'activite du clan, preleve a la source : montre avant la vente
            TextFit.center(g, font, Component.translatable("economy.pioneers.trade.clan_tax", tax, Long.toString(preview * tax / 100)), left + 8,
                  top + H - 14, W - 16, DIM, 0.75F, this.hover);
        }
        super.render(g, mx, my, pt);
        if (hoverRow(mx, my) >= 0) {
            Row h = rows.get(hoverRow(mx, my));
            TooltipLayer.item(font, h.stack(), mx, my);
        } else {
            this.hover.render(g, font);
        }
    }

    private int hoverRow(int mx, int my) {
        if (mx < left + 9 || mx >= left + W - 9 || my < top + 40 || my >= top + 40 + ROWS * ROW) {
            return -1;
        }
        int i = scroll + (my - top - 40) / ROW;
        return i < rows().size() ? i : -1;
    }

    @Override
    public boolean mouseClicked(double mxd, double myd, int button) {
        int mx = (int) mxd, my = (int) myd;
        int nTabs = service == null ? 2 : 3;
        for (int i = 0; i < nTabs; i++) {
            int x = left + 8 + i * 74;
            if (mx >= x && mx < x + 70 && my >= top + 20 && my < top + 34) {
                tab = i;
                selected = -1;
                scroll = 0;
                RpClient.play(net.minecraft.sounds.SoundEvents.BOOK_PAGE_TURN, 1.2F);
                return true;
            }
        }
        int hr = hoverRow(mx, my);
        if (hr >= 0) {
            selected = hr;
            return true;
        }
        int by = top + 44 + ROWS * ROW;
        if (tab < 2) {
            for (int k = 0; k < QTY.length; k++) {
                int x = left + 8 + k * 30;
                if (mx >= x && mx < x + 28 && my >= by && my < by + 14) {
                    qty = k;
                    return true;
                }
            }
        }
        int ax = left + W - 108;
        if (selected >= 0 && selected < rows().size() && mx >= ax && mx < ax + 100 && my >= by && my < by + 14) {
            Row row = rows().get(selected);
            CompoundTag t = new CompoundTag();
            RpClient.play(RpSounds.QUILL.get(), 1.0F);
            if (tab == 0) {
                t.putInt("i", selected);
                t.putInt("n", qty == 3 ? 64 : QTY[qty]);
                RpNet.toServer(RpActions.TRADE_BUY, t);
            } else if (tab == 1) {
                t.putString("item", row.id());
                t.putInt("n", qty == 3 ? Math.max(1, row.have()) : QTY[qty]);
                RpNet.toServer(RpActions.TRADE_SELL, t);
            } else {
                t.putInt("slot", row.slot());
                RpNet.toServer(RpActions.TRADE_SERVICE, t);
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
