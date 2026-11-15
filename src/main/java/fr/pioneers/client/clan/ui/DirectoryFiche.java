package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.H;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : annuaire des clans (clans solo exclus), par pages : embleme, nom, regime, niveau, effectif, potentiel de
 * frappe en trois paliers (jamais le chiffre), relation. Fiche publique d'un clan : devise, Representant (nom de personnage), faction ; un
 * allie voit la liste des personnages, jamais un pseudo de compte. Depuis la fiche, selon les permissions : proposer un traite, signer une
 * offre recue, declarer la guerre (motif, confirmation). 1.0.5-beta (lot 20, retour 10) : rangee = embleme, nom, puis niveau / membres /
 * force en faits a icone (regime et resume en infobulle) ; fiche publique en faits a icone ; actions en boutons a icone, la guerre en deux
 * clics.
 */
final class DirectoryFiche extends ClanTab.Fiche {

    static final int ROW = 24, LIST_Y = Y + 15;
    private static int page;
    @Nullable
    private static String card;

    @Override
    String id() {
        return "clans";
    }

    @Override
    void enter() {
        card = null;
        ask();
    }

    private static void ask() {
        CompoundTag t = new CompoundTag();
        t.putInt("page", page);
        ClanUiClient.request("directory", t);
    }

    static void open(String id) {
        card = id;
        CompoundTag t = new CompoundTag();
        t.putString("clan", id);
        ClanUiClient.request("card", t);
    }

    @Override
    void draw(Ui u) {
        if (card != null) {
            card(u);
            return;
        }
        CompoundTag t = ClanUiClient.sheet("clans");
        if (waiting(u, t)) {
            return;
        }
        int pages = Math.max(1, t.getInt("pages")), pg = t.getInt("page");
        u.icon12(Ui.gui("nav_clans"), X, Y - 2);
        u.fit(Component.translatable("clan.pioneers.ui.dir.title", t.getInt("total")), X + Ui.GAP, Y, W - 90 - Ui.GAP, Ui.INK, 1F);
        u.button(X + W - 86, Y - 1, 14, 12, Component.literal("<"), pg > 0, () -> {
            page = pg - 1;
            ask();
        });
        u.center(Component.translatable("clan.pioneers.ui.page", pg + 1, pages), X + W - 43, Y + 1, 54, Ui.TEXT, Ui.SMALL);
        u.button(X + W - 14, Y - 1, 14, 12, Component.literal(">"), pg + 1 < pages, () -> {
            page = pg + 1;
            ask();
        });
        u.separator(X, Y + 12, W);
        ListTag l = t.getList("clans", Tag.TAG_COMPOUND);
        if (l.isEmpty()) {
            u.wrap(Component.translatable("clan.pioneers.ui.dir.empty"), X, LIST_Y + 1, W, Ui.DIM, Ui.SMALL, 3);
            return;
        }
        int h = Y + H - LIST_Y;
        u.s.scrollArea(7, X, LIST_Y, W, h, l.size() * ROW);
        u.s.beginClip(u.g, X, LIST_Y, W, h);
        int y = LIST_Y - u.s.scrollOf(7);
        boolean inside = u.over(X, LIST_Y, W, h);
        for (int i = 0; i < l.size(); i++, y += ROW) {
            if (y + ROW <= LIST_Y || y >= LIST_Y + h) {
                continue;
            }
            CompoundTag c = l.getCompound(i);
            boolean over = inside && u.over(X, y, W, ROW - 1);
            u.fill(X, y, X + W, y + ROW - 1, over ? 0x22243660 : 0x0C243660);
            u.emblem(c.getCompound("banner"), X + 2, y + 3, 16);
            int x = X + 22;
            if (!c.getString("tag").isBlank()) {
                Component tag = Component.literal("[" + c.getString("tag") + "] ");
                u.text(tag, x, y + 2, Ui.dyeInk(c.getInt("color")));
                x += u.width(tag, 1F);
            }
            u.fit(Component.literal(c.getString("name")), x, y + 2, X + W - 74 - x, Ui.INK, 1F);
            facts(u, c, X + 22, y + 11);
            relation(u, c.getString("rel"), X + W - 3, y + 3, 66);
            if (over) {
                u.tip(X, y, W, ROW - 1, Component.literal(c.getString("name")), summary(c), Component.translatable("clan.pioneers.ui.dir.open_tip"));
            }
            String id = c.getString("id");
            int cy = Math.max(y, LIST_Y), ch = Math.min(y + ROW, LIST_Y + h) - cy;
            if (ch > 0) {
                u.click(X, cy, W, ch, () -> open(id));
            }
        }
        u.s.endClip(u.g);
    }

    /** Niveau, membres, force (3 paliers) : trois faits a icone sur une ligne. */
    static void facts(Ui u, CompoundTag c, int x, int y) {
        u.icon12(Ui.gui("ico_level"), x, y);
        x += Ui.GAP + u.fit(Component.translatable("clan.pioneers.ui.dir.level_short", c.getInt("level")), x + Ui.GAP, y + 3, 50, Ui.TEXT, Ui.SMALL) + 8;
        u.icon12(Ui.gui("nav_members"), x, y);
        x += Ui.GAP + u.fit(Component.translatable("clan.pioneers.ui.dir.members_short", c.getInt("members")), x + Ui.GAP, y + 3, 30, Ui.TEXT, Ui.SMALL) + 8;
        u.icon12(Ui.gui("ico_strength"), x, y);
        pips(u, c.getString("strength"), x + Ui.GAP, y + 4);
    }

    /** Force en trois paliers (jamais le chiffre). */
    static void pips(Ui u, String strength, int x, int y) {
        int n = "strong".equals(strength) ? 3 : "medium".equals(strength) ? 2 : 1;
        for (int i = 0; i < 3; i++) {
            u.fill(x + i * 6, y, x + i * 6 + 4, y + 5, i < n ? Ui.INK : 0x30243660);
        }
    }

    /** « Democratie · niveau 3 · 12 membres · force : moyenne ». */
    static Component summary(CompoundTag c) {
        MutableComponent m = Component.translatable("clan.pioneers.regime." + c.getString("regime"));
        m.append(" · ").append(Component.translatable("clan.pioneers.ui.dir.level", c.getInt("level")));
        m.append(" · ").append(Component.translatable("clan.pioneers.ui.dir.members", c.getInt("members")));
        m.append(" · ").append(Component.translatable("clan.pioneers.ui.dir.strength", Component.translatable("clan.pioneers.strength." + c.getString(
              "strength"))));
        return m;
    }

    /** Relation (signe + mot) alignee a droite, bornee a {@code w} (1.9.1). */
    static void relation(Ui u, String rel, int rx, int y, int w) {
        if (rel.isEmpty() || "neutral".equals(rel)) {
            return;
        }
        String sign = "same".equals(rel) ? "• " : "ally".equals(rel) ? "= " : "war".equals(rel) ? "! " : "";
        int c = "war".equals(rel) ? Ui.MINUS : "ally".equals(rel) ? Ui.PLUS : Ui.GOLD;
        u.right(Component.literal(sign).append(Component.translatable("clan.pioneers.ui.relation." + rel)), rx, y, w, c, Ui.SMALL);
    }

    // ------------------------------------------------------------------ fiche publique d'un clan
    private static void card(Ui u) {
        u.iconButton(X, Y - 1, 64, 13, Ui.gui("nav_clans"), Component.translatable("clan.pioneers.ui.back"), true, () -> card = null);
        CompoundTag t = ClanUiClient.sheet("card");
        CompoundTag c = t == null ? null : t.getCompound("card");
        if (t == null || c == null || !card.equals(c.getString("id"))) {
            if (t != null && !t.getString("error").isEmpty()) {
                u.fit(Component.translatable(t.getString("error")), X, Y + 16, W, Ui.DIM, Ui.SMALL);
            } else {
                u.fit(Component.translatable("clan.pioneers.ui.loading"), X, Y + 16, W, Ui.DIM, Ui.SMALL);
            }
            return;
        }
        relation(u, c.getString("rel"), X + W, Y + 1, W - 70);
        int y = Y + 16;
        u.frame(X, y, 36, 36, Ui.FAINT);
        u.emblem(c.getCompound("banner"), X + 2, y + 2, 32);
        int x = X + 42;
        if (!c.getString("tag").isBlank()) {
            Component tag = Component.literal("[" + c.getString("tag") + "] ");
            u.text(tag, x, y + 2, Ui.dyeInk(c.getInt("color")));
            x += u.width(tag, 1F);
        }
        u.fit(Component.literal(c.getString("name")), x, y + 2, X + W - 40 - x, Ui.INK, 1F);
        if (!c.getString("motto").isBlank()) {
            u.wrap(Component.translatable("clan.pioneers.quoted", c.getString("motto")), X + 42, y + 14, W - 82, Ui.DIM, Ui.SMALL, 2);
        }
        String regime = c.getString("regime");
        u.image(Ui.gui("regime_" + regime), X + W - 32, y, 32, 32, 32, 32);
        u.tip(X + W - 32, y, 32, 32, Component.translatable("clan.pioneers.regime." + regime), Component.translatable("clan.pioneers.regime." + regime
                                                                                                                      + ".hook"));
        u.separator(X, y + 40, W);
        //faits publics : une ligne chacun, icone + valeur
        int fy = y + 44, fw = 136;
        u.fact(Ui.gui("nav_government"), Component.translatable("clan.pioneers.regime." + regime), X, fy, fw, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.fact.regime"));
        fy += Ui.FACT;
        u.fact(Ui.gui("ico_level"), Component.translatable("clan.pioneers.ui.sheet.level_short", c.getInt("level")), X, fy, fw, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.fact.level"));
        fy += Ui.FACT;
        u.fact(Ui.gui("nav_members"), Component.translatable("clan.pioneers.ui.dir.members", c.getInt("members")), X, fy, fw, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.dir.members_tip"));
        fy += Ui.FACT;
        u.fact(Ui.gui("rank_representative"), Component.literal(c.getString("representative")), X, fy, fw, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.fact.representative"));
        fy += Ui.FACT;
        String strength = c.getString("strength");
        int sw = u.fact(Ui.gui("ico_strength"), Component.translatable("clan.pioneers.strength." + strength), X, fy, fw - 24, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.dir.strength_label"), Component.translatable("clan.pioneers.ui.dir.strength_tip"));
        pips(u, strength, X + sw + 4, fy + 3);
        fy += Ui.FACT;
        if (!c.getString("faction").isEmpty()) {
            u.fact(Ui.gui("nav_faction"), Component.literal(c.getString("faction")), X, fy, fw, Ui.TEXT, Component.translatable("clan.pioneers.ui.dir.faction"));
        }
        //allie : la liste des personnages (jamais un compte)
        int rx = X + 146, rw = W - 146;
        ListTag chars = c.getList("characters", Tag.TAG_STRING);
        if (!chars.isEmpty()) {
            u.icon12(Ui.gui("nav_members"), rx, y + 44);
            u.fit(Component.translatable("clan.pioneers.ui.dir.characters_short"), rx + Ui.GAP, y + 47, rw - Ui.GAP, Ui.INK, Ui.SMALL);
            u.wrap(Component.literal(String.join(", ", Ui.strings(chars))), rx, y + 58, rw, Ui.TEXT, Ui.SMALL, 6);
        }
        actions(u, t, c);
    }

    private static void actions(Ui u, CompoundTag t, CompoundTag c) {
        if (!t.contains("p_diplomacy") || t.getBoolean("mineSolo") || c.getBoolean("solo")) {
            return;
        }
        int y = Y + 142;
        u.separator(X, y - 3, W);
        String name = c.getString("name");
        List<String> active = Ui.strings(t.getList("active", Tag.TAG_STRING));
        ListTag offers = t.getList("offers", Tag.TAG_COMPOUND);
        u.icon12(Ui.gui("ico_treaty"), X, y - 1);
        u.fit(Component.translatable("clan.pioneers.ui.dir.propose"), X + Ui.GAP, y + 2, W - Ui.GAP, Ui.INK, Ui.SMALL);
        y += 13;
        String[] kinds = {"nap", "alliance", "trade"};
        int bw = (W - 4) / 3;
        boolean diplo = t.getBoolean("p_diplomacy");
        for (int i = 0; i < kinds.length; i++) {
            String k = kinds[i];
            boolean sent = false, received = false;
            for (int j = 0; j < offers.size(); j++) {
                if (k.equals(offers.getCompound(j).getString("kind"))) {
                    sent |= !offers.getCompound(j).getBoolean("received");
                    received |= offers.getCompound(j).getBoolean("received");
                }
            }
            //1.0.5-beta (lot 20) : nom court du traite sur le bouton, le nom entier en infobulle
            Component label = active.contains(k) ? Component.literal("= ").append(Component.translatable("clan.pioneers.treaty.short." + k))
                  : received ? Component.translatable("clan.pioneers.ui.dir.sign", Component.translatable("clan.pioneers.treaty.short." + k))
                        : Component.translatable("clan.pioneers.treaty.short." + k);
            boolean rec = received;
            u.button(X + i * (bw + 2), y, bw, 14, label, diplo && !active.contains(k) && !sent && !t.getBoolean("atWar"),
                  () -> {
                      DiplomacyFiche.treaty(rec ? "accept" : "propose", name, k);
                      open(c.getString("id"));
                  });
            if (sent) {
                u.tip(X + i * (bw + 2), y, bw, 14, Component.translatable("clan.pioneers.treaty.kind." + k), Component.translatable("clan.pioneers.ui.dir.sent"));
            } else {
                u.tip(X + i * (bw + 2), y, bw, 14, Component.translatable("clan.pioneers.treaty.kind." + k));
            }
        }
        y += 19;
        if (!t.getBoolean("war")) {
            return;
        }
        u.icon12(Ui.gui("ico_war"), X, y - 1);
        u.fit(Component.translatable("clan.pioneers.ui.dir.war"), X + Ui.GAP, y + 2, W - Ui.GAP, t.getBoolean("atWar") ? Ui.MINUS : Ui.INK, Ui.SMALL);
        u.tip(X, y - 1, W, 12, Component.translatable("clan.pioneers.ui.dir.war"), Component.translatable("clan.pioneers.ui.dir.war_hint"));
        y += 13;
        if (t.getBoolean("atWar")) {
            u.fit(Component.literal("! ").append(Component.translatable("clan.pioneers.ui.dir.at_war")), X, y, W, Ui.MINUS, Ui.SMALL);
            return;
        }
        boolean may = t.getBoolean("p_war");
        EditBox reason = ClanTab.textBox(u, "war:" + card, X, y, W - 126, Component.translatable("clan.pioneers.ui.dir.reason"),
              Math.max(1, t.getInt("reasonMax")));
        String why = reason.getValue().trim();
        u.confirm("war:" + card, X + W - 120, y, 120, 14, Ui.gui("ico_war"), Component.translatable("clan.pioneers.ui.dir.declare"), may && !why.isEmpty(),
              () -> {
                  CompoundTag a = new CompoundTag();
                  a.putString("clan", name);
                  a.putString("reason", why);
                  ClanUiClient.act("war_declare", "diplomacy", a);
                  reason.setValue("");
              }, Component.translatable(may ? "clan.pioneers.ui.dir.reason" : "clan.pioneers.perm.declare_war"));
    }
}
