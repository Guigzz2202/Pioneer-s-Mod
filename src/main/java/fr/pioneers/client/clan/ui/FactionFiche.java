package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.H;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Faction (visible quand le clan est dans une faction), branchee sur les fiches du serveur des factions
 * ({@code FACTION_ACTION} sheet -&gt; {@code FACTION_VIEW} sorte « sheet », recue par le crochet {@code FactionClient.sheetHandler}) :
 * identite, regime federal, niveau, Representant federal, tresor et contribution, zones et chunkloading federaux, peuple ; clans membres et
 * Delegues (les membres de la faction voient les personnages, jamais un pseudo) ; propositions federales et vote. 1.0.5-beta (lot 20,
 * retour 10) : vues en pastilles a icone, fiche en faits a icone (un par ligne, libelle en infobulle).
 */
final class FactionFiche extends ClanTab.Fiche {

    private static final String[] VIEWS = {"faction", "clans", "government"};
    private static final String[] VIEW_ICONS = {"nav_faction", "nav_clans", "ico_vote"};
    private static String view = "faction";

    @Override
    String id() {
        return "faction";
    }

    @Override
    void enter() {
        ask(view);
    }

    static void ask(String sheet) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "sheet");
        t.putString("sheet", sheet);
        RpNet.toServer(RpActions.FACTION_ACTION, t);
    }

    @Override
    void draw(Ui u) {
        int cw = (W - 4) / 3;
        for (int i = 0; i < VIEWS.length; i++) {
            String v = VIEWS[i];
            u.navChip(X + i * (cw + 2), Y, i == 2 ? W - 2 * (cw + 2) : cw, 13, Ui.gui(VIEW_ICONS[i]), Component.translatable("clan.pioneers.ui.faction.view."
                  + v), v.equals(view), 0, () -> {
                      view = v;
                      ask(v);
                  });
        }
        CompoundTag t = ClanUiClient.sheet("faction:" + view);
        if (t == null) {
            u.fit(Component.translatable("clan.pioneers.ui.loading"), X, Y + 18, W, Ui.DIM, Ui.SMALL);
            return;
        }
        if (!t.getString("error").isEmpty()) {
            u.wrap(Component.translatable(t.getString("error")), X, Y + 18, W, Ui.DIM, Ui.SMALL, 3);
            return;
        }
        switch (view) {
            case "clans" -> clans(u, t);
            case "government" -> government(u, t);
            default -> fiche(u, t);
        }
    }

    private static void fiche(Ui u, CompoundTag t) {
        int y = Y + 18;
        int x = X;
        if (!t.getString("tag").isBlank()) {
            Component tag = Component.literal("[" + t.getString("tag") + "] ");
            u.text(tag, x, y, Ui.dyeInk(t.getInt("color")));
            x += u.width(tag, 1F);
        }
        u.fit(Component.literal(t.getString("name")), x, y, X + W - 40 - x, Ui.INK, 1F);
        String regime = t.getString("regime");
        u.image(Ui.gui("member_faction"), X + W - 32, y - 2, 32, 32, 32, 32);
        if (!t.getString("motto").isBlank()) {
            u.wrap(Component.translatable("clan.pioneers.quoted", t.getString("motto")), X, y + 11, W - 40, Ui.DIM, Ui.SMALL, 2);
        }
        u.separator(X, y + 32, W);
        //1.0.5-beta (lot 20) : un fait par ligne, a icone, le libelle en infobulle
        long now = ClanUiClient.now();
        int fy = y + 36, fw = 136;
        String[][] left = {{"nav_government", "regime"}, {"ico_level", "level"}, {"rank_representative", "representative"}, {"nav_clans", "clans"},
              {"nav_members", "members"}, {"ico_vote", "votes"}};
        Component[] lv = {Component.translatable("clan.pioneers.regime." + regime), Component.translatable("clan.pioneers.ui.faction.level_short",
              t.getInt("level")), Component.literal(t.getString("representative")), Component.translatable("clan.pioneers.ui.faction.clans_short",
              t.getInt("clans"), t.getInt("maxClans")), Component.translatable("clan.pioneers.ui.faction.members_value", t.getInt("members"),
              t.getInt("active")), Component.translatable("clan.pioneers.ui.nav_badge.government", t.getInt("openVotes"))};
        for (int i = 0; i < left.length; i++) {
            Component label = Component.translatable("clan.pioneers.ui.faction." + left[i][1]);
            if ("level".equals(left[i][1])) {
                u.fact(Ui.gui(left[i][0]), lv[i], X, fy, fw, Ui.TEXT, label, Component.translatable("clan.pioneers.ui.faction.points_tip", t.getLong("next") > 0
                      ? Ui.group(t.getLong("points")) + " / " + Ui.group(t.getLong("next")) : Ui.group(t.getLong("points"))));
            } else {
                u.fact(Ui.gui(left[i][0]), lv[i], X, fy, fw, Ui.TEXT, label);
            }
            fy += Ui.FACT;
        }
        int rx = X + 146, rw = W - 146, ry = y + 36;
        String[][] right = {{"ico_coin", "treasury"}, {"ico_tax", "contribution"}, {"ico_chunk", "zones"}, {"perm_chunkload", "loaded"}};
        Component[] rv = {Component.literal(Ui.group(t.getLong("treasury"))), Component.translatable("clan.pioneers.ui.treasury.percent_cap",
              t.getInt("contribution"), t.getInt("contributionMax")), Component.literal(t.getInt("zones") + " / " + t.getInt("zoneBudget")),
              Component.literal(t.getInt("loaded") + " / " + t.getInt("slots"))};
        for (int i = 0; i < right.length; i++) {
            u.fact(Ui.gui(right[i][0]), rv[i], rx, ry, rw, Ui.TEXT, Component.translatable("clan.pioneers.ui.faction." + right[i][1]));
            ry += Ui.FACT;
        }
        if (t.getBoolean("loadSuspended")) {
            u.fact(Ui.gui("ico_warning"), Component.translatable("clan.pioneers.ui.territory.suspended"), rx, ry, rw, Ui.MINUS,
                  Component.translatable("clan.pioneers.ui.territory.suspended"));
            ry += Ui.FACT;
        }
        int py = Math.max(fy, ry) + 6;
        u.separator(X, py - 3, W);
        if (!t.getString("people").isEmpty()) {
            u.fill(X, py, X + 3, py + 18, 0xFF000000 | t.getInt("peopleColor"));
            u.image(Ui.gui("member_people"), X + 6, py, 16, 16, 32, 32);
            u.fit(Component.translatable("clan.pioneers.ui.faction.people", t.getString("peopleName")), X + 26, py + 4, W - 26, Ui.INK, 1F);
        } else {
            u.wrap(Component.translatable("clan.pioneers.ui.faction.no_people"), X, py, W, Ui.DIM, Ui.SMALL, 2);
        }
        if (t.getLong("leaving") > now) {
            u.fit(Component.literal("− ").append(Component.translatable("clan.pioneers.ui.faction.leaving", Ui.duration(t.getLong("leaving") - now))), X,
                  Y + H - 8, W, Ui.MINUS, Ui.SMALL);
        }
    }

    private static void clans(Ui u, CompoundTag t) {
        ListTag l = t.getList("clans", Tag.TAG_COMPOUND);
        int top = Y + 18, h = Y + H - top;
        u.s.scrollArea(7, X, top, W, h, l.size() * 32);
        u.s.beginClip(u.g, X, top, W, h);
        int y = top - u.s.scrollOf(7);
        for (int i = 0; i < l.size(); i++, y += 32) {
            CompoundTag c = l.getCompound(i);
            u.fill(X, y, X + W, y + 30, 0x0C243660);
            int x = X + 2;
            if (!c.getString("tag").isBlank()) {
                Component tag = Component.literal("[" + c.getString("tag") + "] ");
                u.text(tag, x, y + 1, Ui.dyeInk(c.getInt("color")));
                x += u.width(tag, 1F);
            }
            u.fit(Component.literal(c.getString("name")), x, y + 1, X + W - 80 - x, Ui.INK, 1F);
            if (c.getBoolean("founder")) {
                u.right(Component.translatable("clan.pioneers.ui.faction.founder"), X + W - 2, y + 2, 74, Ui.GOLD, Ui.SMALL);
            }
            MutableComponent info = Component.translatable("clan.pioneers.ui.faction.delegate", c.getString("delegate"));
            info.append(" · ").append(Component.translatable("clan.pioneers.regime." + c.getString("regime")));
            info.append(" · ").append(Component.translatable("clan.pioneers.ui.dir.level", c.getInt("level")));
            info.append(" · ").append(Component.translatable("clan.pioneers.ui.faction.members_value", c.getInt("members"), c.getInt("active")));
            u.fit(info, X + 2, y + 11, W - 4, Ui.DIM, Ui.SMALL);
            u.fit(Component.literal(String.join(", ", Ui.strings(c.getList("characters", Tag.TAG_STRING)))), X + 2, y + 20, W - 4, Ui.TEXT, Ui.SMALL);
        }
        u.s.endClip(u.g);
    }

    private static void government(Ui u, CompoundTag t) {
        ListTag props = t.getList("proposals", Tag.TAG_COMPOUND);
        int top = Y + 18, h = Y + H - top;
        long now = ClanUiClient.now();
        int open = 0;
        for (int i = 0; i < props.size(); i++) {
            if ("open".equals(props.getCompound(i).getString("status"))) {
                open++;
            }
        }
        if (open == 0) {
            u.fit(Component.translatable("clan.pioneers.faction.vote.none"), X, top, W, Ui.DIM, Ui.SMALL);
            return;
        }
        u.s.scrollArea(7, X, top, W, h, open * 32);
        u.s.beginClip(u.g, X, top, W, h);
        int y = top - u.s.scrollOf(7);
        for (int i = 0; i < props.size(); i++) {
            CompoundTag p = props.getCompound(i);
            if (!"open".equals(p.getString("status"))) {
                continue;
            }
            u.fill(X, y, X + W, y + 30, 0x0C243660);
            u.fit(Component.translatable("clan.pioneers.vote.type." + p.getString("type")), X + 2, y + 1, W - 4, Ui.INK, 1F);
            MutableComponent info = Component.translatable("clan.pioneers.ui.gov.closes", Ui.duration(p.getLong("closes") - now));
            if (!p.getString("majority").isEmpty()) {
                info.append(" · ").append(Component.translatable("clan.pioneers.majority." + p.getString("majority")));
            }
            if (!p.getBoolean("secret") && p.contains("t_yes")) {
                info.append(" · ").append(Component.translatable("clan.pioneers.ui.gov.tally", Math.round(p.getDouble("t_yes")), Math.round(p.getDouble(
                      "t_no"))));
            }
            u.fit(info, X + 2, y + 11, W - 4, Ui.DIM, Ui.SMALL);
            String id = p.getString("id");
            int by = y + 17;
            if (!p.getBoolean("elector")) {
                u.right(Component.translatable("clan.pioneers.ui.gov.not_elector"), X + W - 2, by + 3, W - 4, Ui.DIM, Ui.SMALL);
            } else if (p.getBoolean("voted")) {
                u.right(Component.literal("+ ").append(Component.translatable("clan.pioneers.ui.gov.voted")), X + W - 2, by + 3, W - 4, Ui.PLUS,
                      Ui.SMALL);
            } else if (by >= top && by + 13 <= top + h) {
                if (p.getBoolean("election")) {
                    ListTag cands = p.getList("candidates", Tag.TAG_COMPOUND);
                    int cx = X + 2;
                    for (int k = 0; k < cands.size() && cx + 70 <= X + W; k++, cx += 72) {
                        CompoundTag c = cands.getCompound(k);
                        String tok = c.getString("token");
                        u.button(cx, by, 70, 12, Component.literal(c.getString("name")), true, () -> vote(id, tok));
                    }
                } else {
                    int bw = 58;
                    u.button(X + W - 3 * bw - 6, by, bw, 12, Component.literal("+ ").append(Component.translatable("clan.pioneers.vote.yes")), true,
                          () -> vote(id, "yes"));
                    u.button(X + W - 2 * bw - 4, by, bw, 12, Component.literal("− ").append(Component.translatable("clan.pioneers.vote.no")), true,
                          () -> vote(id, "no"));
                    u.button(X + W - bw - 2, by, bw, 12, Component.translatable("clan.pioneers.vote.abstain"), true, () -> vote(id, "abstain"));
                }
            }
            y += 32;
        }
        u.s.endClip(u.g);
    }

    private static void vote(String id, String choice) {
        CompoundTag t = new CompoundTag();
        t.putString("op", "vote");
        t.putString("proposal", id);
        t.putString("choice", choice);
        RpNet.toServer(RpActions.FACTION_ACTION, t);
    }
}
