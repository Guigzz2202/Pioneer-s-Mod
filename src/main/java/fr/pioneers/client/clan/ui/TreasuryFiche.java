package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.H;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import fr.pioneers.Pioneers;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Tresor (permission de le voir) : solde, gel et arret du chunkloading, taxes en vigueur et plafonds du
 * regime, dividende en attente (Cooperative), retraits du jour face au plafond du role, verser et retirer (au-dela du plafond, le serveur
 * propose une depense selon le regime), journal des mouvements. 1.0.5-beta (lot 20, retour 10) : faits a icone (taxes : la valeur, le
 * plafond en infobulle), alertes a icone, la regle des retraits en infobulle du bouton Retirer, mouvements replies sur plusieurs lignes au
 * lieu d'etre coupes.
 */
final class TreasuryFiche extends ClanTab.Fiche {

    static final int CW = 136, RX = X + 146, RW = W - 146;
    static final ResourceLocation STAT_TRADE = Pioneers.rl("textures/gui/pioneer/stat/trade.png");

    @Override
    String id() {
        return "treasury";
    }

    @Override
    void enter() {
        ClanUiClient.request("treasury");
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("treasury");
        if (waiting(u, t)) {
            return;
        }
        u.icon12(Ui.gui("ico_coin"), X, Y);
        u.fit(Component.translatable("clan.pioneers.ui.treasury.balance_short"), X + Ui.GAP, Y + 3, CW - Ui.GAP, Ui.DIM, Ui.SMALL);
        //1.9.1 : le montant (grands chiffres) est borne pour laisser la place au mot « Shining »
        Component amount = Component.literal(Ui.group(t.getLong("treasury")));
        int aw = u.fit(amount, X, Y + 14, CW - 34, Ui.INK, 2F);
        u.fit(Component.translatable("clan.pioneers.ui.tokens"), X + aw + 4, Y + 22, 30, Ui.DIM, Ui.SMALL);
        int y = Y + 34;
        boolean eco = t.getBoolean("economy");
        if (!eco) {
            u.fact(Ui.gui("ico_warning"), Component.translatable("clan.pioneers.ui.treasury.no_economy_short"), X, y, CW, Ui.MINUS,
                  Component.translatable("clan.pioneers.ui.treasury.no_economy"));
            y += Ui.FACT;
        }
        if (t.getBoolean("frozen")) {
            u.fact(Ui.gui("ico_warning"), Component.translatable("clan.pioneers.ui.treasury.frozen_short"), X, y, CW, Ui.MINUS,
                  Component.translatable("clan.pioneers.ui.treasury.frozen"));
            y += Ui.FACT;
        }
        if (t.getLong("chunkloadStop") > 0) {
            Component d = Ui.duration(t.getLong("chunkloadStop") - ClanUiClient.now());
            u.fact(Ui.gui("ico_warning"), Component.translatable("clan.pioneers.ui.treasury.stop_short", d), X, y, CW, Ui.MINUS,
                  Component.translatable("clan.pioneers.ui.treasury.chunkload_stop", d));
            y += Ui.FACT;
        }
        y += 2;
        y += u.section(Ui.gui("ico_tax"), Component.translatable("clan.pioneers.ui.treasury.taxes"), X, y, CW) + 1;
        u.fact(Ui.gui("ico_tax"), Component.translatable("clan.pioneers.ui.treasury.activity_short", t.getInt("activity") + " %"), X, y, CW, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.treasury.activity"), Component.translatable("clan.pioneers.ui.treasury.cap_tip",
                    t.getInt("capActivity") + " %"));
        y += Ui.FACT;
        u.fact(Ui.gui("ico_coin"), Component.translatable("clan.pioneers.ui.treasury.dues_short", t.getInt("dues")), X, y, CW, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.treasury.dues"), Component.translatable("clan.pioneers.ui.treasury.cap_tip",
                    Integer.toString(t.getInt("capDues"))));
        y += Ui.FACT;
        u.fact(STAT_TRADE, Component.translatable("clan.pioneers.ui.treasury.market_short", t.getInt("market") + " %"), X, y, CW, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.treasury.market"), Component.translatable("clan.pioneers.ui.treasury.cap_tip",
                    t.getInt("capMarket") + " %"));
        y += Ui.FACT;
        if (t.getLong("pool") > 0) {
            u.fact(Ui.gui("ico_coin"), Component.translatable("clan.pioneers.ui.treasury.pool_short", Ui.group(t.getLong("pool"))), X, y, CW, Ui.TEXT,
                  Component.translatable("clan.pioneers.ui.treasury.pool"));
            y += Ui.FACT;
        }
        u.iconButton(X, y + 1, CW, 14, Ui.gui("perm_set_taxes"), Component.translatable("clan.pioneers.ui.treasury.change_taxes"),
              ClanTab.status() == ClanTab.Status.MEMBER, () -> {
                  GovernmentFiche.form("taxes");
                  ClanTab.select("government");
              });
        y += 20;
        //retraits du jour
        u.fit(Component.translatable("clan.pioneers.ui.treasury.mine_short"), X, y, CW, Ui.INK, Ui.SMALL);
        y += 9;
        long cap = t.getLong("myCap"), used = t.getLong("myUsed");
        u.bar(X, y, CW, 4, cap > 0 ? used / (double) cap : 0, Ui.INK_LIGHT);
        y += 6;
        u.fit(Component.translatable("clan.pioneers.ui.treasury.used", Ui.group(used), Ui.group(cap)), X, y, CW, Ui.DIM, Ui.SMALL);
        y += 10;
        //verser, retirer (la regle du plafond en infobulle de Retirer)
        EditBox box = ClanTab.numberBox(u, "treasury", X, y, 56, Component.translatable("clan.pioneers.ui.amount"));
        long n = ClanTab.parse(box);
        CompoundTag a = new CompoundTag();
        a.putLong("amount", n);
        u.button(X + 60, y, 36, 14, Component.translatable("clan.pioneers.ui.treasury.donate"), eco && n > 0, () -> {
            ClanUiClient.act("donate", "treasury", a);
            box.setValue("");
        });
        u.button(X + 98, y, CW - 98, 14, Component.translatable("clan.pioneers.ui.treasury.withdraw"), eco && n > 0 && ClanTab.may("withdraw"), () -> {
            ClanUiClient.act("withdraw", "treasury", a);
            box.setValue("");
        });
        u.tip(X + 98, y, CW - 98, 14, Component.translatable("clan.pioneers.ui.treasury.withdraw"), Component.translatable(
              "clan.pioneers.ui.treasury.spend_hint", Math.round(t.getDouble("spendShare") * 100)));
        //mouvements
        u.section(Ui.gui("nav_journal"), Component.translatable("clan.pioneers.ui.treasury.moves"), RX, Y, RW);
        ListTag lines = t.getList("lines", Tag.TAG_COMPOUND);
        int top = Y + 16, h = Y + H - top;
        if (lines.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.treasury.no_move"), RX, top, RW, Ui.DIM, Ui.SMALL);
            return;
        }
        journal(u, lines, RX, top, RW, h, 6);
    }

    /** Hauteur d'une ligne de journal (texte replie sur au plus 3 lignes, plus 1 px d'ecart). */
    static int lineHeight(Ui u, CompoundTag l, int w) {
        return u.wrapHeight(Ui.line(l), w - 29, Ui.SMALL, 3) + 1;
    }

    /**
     * Lignes de journal (jour, texte traduit REPLIE sur 3 lignes au plus : 1.0.5-beta, lot 20 ; avant : coupe sur une ligne), dans une zone qui
     * defile.
     */
    static void journal(Ui u, ListTag lines, int x, int top, int w, int h, int slot) {
        int content = 0;
        for (int i = 0; i < lines.size(); i++) {
            content += lineHeight(u, lines.getCompound(i), w);
        }
        u.s.scrollArea(slot, x, top, w, h, content);
        u.s.beginClip(u.g, x, top, w, h);
        int y = top - u.s.scrollOf(slot);
        for (int i = 0; i < lines.size(); i++) {
            CompoundTag l = lines.getCompound(i);
            int lh = lineHeight(u, l, w);
            if (y + lh > top && y < top + h) {
                if (i % 2 == 1) {
                    u.fill(x, y - 1, x + w - 3, y + lh - 1, 0x0A243660);
                }
                u.fit(Component.translatable("clan.pioneers.ui.day", l.getLong("d")), x, y, 24, Ui.DIM, Ui.SMALL);
                u.wrap(Ui.line(l), x + 26, y, w - 29, Ui.TEXT, Ui.SMALL, 3);
            }
            y += lh;
        }
        u.s.endClip(u.g);
    }
}
