package fr.pioneers.society.clan.client.ui;

import static fr.pioneers.society.clan.client.ui.ClanTab.H;
import static fr.pioneers.society.clan.client.ui.ClanTab.W;
import static fr.pioneers.society.clan.client.ui.ClanTab.X;
import static fr.pioneers.society.clan.client.ui.ClanTab.Y;

import fr.pioneers.Pioneers;
import java.util.List;
import java.util.Locale;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : Fiche du clan : embleme (banniere de la Mairie), nom et devise, regime, faits (Representant, effectif,
 * territoire, chunkloading, anciennete, role du joueur, votes ouverts), niveau et points, echelle d'appartenance (Solitaire, clan, faction,
 * peuple), permissions du joueur (icones, infobulles), et quitter le clan (confirmation). 1.0.5-beta (lot 20, retour 10) : un fait par
 * ligne, chacun avec son icone, le libelle et le detail en infobulle ; droits du joueur ranges par famille (toutes les permissions, Acheter
 * des claims compris : elle manquait), compteur « Vos droits : x / y » ; quitter = action irreversible (deuxieme clic).
 */
final class SheetFiche extends ClanTab.Fiche {

    static final String[] LADDER = {"solo", "clan", "faction", "people"};
    static final int FACTS_Y = Y + 45, FACTS_W = 136, RX = X + 146, RIGHTS_Y = Y + 140;

    @Override
    String id() {
        return "sheet";
    }

    @Override
    void enter() {
        ClanUiClient.request("sheet");
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("sheet");
        if (waiting(u, t)) {
            return;
        }
        boolean solo = t.getBoolean("solo");
        String regime = t.getString("regime");
        //embleme, nom, devise, regime
        u.frame(X, Y, 36, 36, Ui.FAINT);
        u.emblem(t.getCompound("banner"), X + 2, Y + 2, 32);
        int x = X + 42;
        String tag = t.getString("tag");
        if (!tag.isBlank()) {
            Component tc = Component.literal("[" + tag + "] ");
            u.text(tc, x, Y + 2, Ui.dyeInk(t.getInt("color")));
            x += u.width(tc, 1F);
        }
        u.fit(Ui.clanName(t.getString("name"), t.getString("solo_of")), x, Y + 2, X + W - 40 - x, Ui.INK, 1F);
        String motto = t.getString("motto");
        if (!motto.isBlank()) {
            u.wrap(Component.translatable("clan.pioneers.quoted", motto), X + 42, Y + 14, W - 82, Ui.DIM, Ui.SMALL, 2);
        }
        u.image(Ui.gui(solo ? "member_solo" : "regime_" + regime), X + W - 32, Y, 32, 32, 32, 32);
        u.tip(X + W - 32, Y, 32, 32, Component.translatable("clan.pioneers.ui.fact.regime"), Component.translatable("clan.pioneers.regime." + regime),
              Component.translatable("clan.pioneers.regime." + regime + ".hook"));
        u.separator(X, Y + 40, W);
        //faits : une ligne chacun, icone + valeur, libelle en infobulle
        long now = ClanUiClient.now();
        int fy = FACTS_Y;
        if (!solo) {
            u.fact(Ui.gui("rank_representative"), Component.literal(t.getString("representative")), X, fy, FACTS_W, Ui.TEXT,
                  Component.translatable("clan.pioneers.ui.fact.representative"));
            fy += Ui.FACT;
            u.fact(Ui.gui("nav_members"), Component.translatable("clan.pioneers.ui.sheet.members_short", t.getInt("members"), t.getInt("max")), X, fy,
                  FACTS_W, Ui.TEXT, Component.translatable("clan.pioneers.ui.fact.members"), Component.translatable("clan.pioneers.ui.fact.members_tip",
                        t.getInt("active")));
            fy += Ui.FACT;
        }
        u.fact(Ui.gui("ico_chunk"), Component.translatable("clan.pioneers.ui.chunks", t.getInt("claims")), X, fy, FACTS_W, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.fact.territory"));
        fy += Ui.FACT;
        u.fact(Ui.gui("perm_chunkload"), Component.translatable("clan.pioneers.ui.slots", t.getInt("slots")), X, fy, FACTS_W, Ui.TEXT,
              Component.translatable("clan.pioneers.ui.fact.chunkload"));
        fy += Ui.FACT;
        u.fact(Pioneers.rl("textures/gui/pioneer/stat/date.png"), Component.translatable("clan.pioneers.ui.ago", Ui.duration(now - t.getLong("founded"))),
              X, fy, FACTS_W, Ui.TEXT, Component.translatable("clan.pioneers.ui.fact.founded"));
        fy += Ui.FACT;
        if (!solo) {
            String ins = insignia(t.getString("role"), t.getInt("rank"));
            u.icon12(Ui.gui("rank_" + ins), X, fy);
            u.fit(Ui.txt(t.getString("roleName")), X + Ui.GAP, fy + 2, FACTS_W - Ui.GAP, Ui.TEXT, 1F);
            u.tip(X, fy, FACTS_W, Ui.ICON, Component.translatable("clan.pioneers.ui.fact.role"), Component.translatable("clan.pioneers.ui.fact.rank_tip",
                  t.getInt("rank")));
            fy += Ui.FACT;
            int votes = t.getInt("openVotes");
            u.fact(Ui.gui("ico_vote"), Component.translatable("clan.pioneers.ui.nav_badge.government", votes), X, fy, FACTS_W, votes > 0 ? Ui.INK : Ui.DIM,
                  Component.translatable("clan.pioneers.ui.fact.votes"), Component.translatable("clan.pioneers.ui.fact.votes_tip"));
            if (votes > 0) {
                u.click(X, fy, FACTS_W, Ui.ICON, () -> ClanTab.select("government"));
            }
        }
        //niveau
        int rw = W - 146;
        u.icon12(Ui.gui("ico_level"), RX, Y + 47);
        u.fit(Component.translatable("clan.pioneers.ui.sheet.level_short", t.getInt("level")), RX + Ui.GAP, Y + 46, rw - Ui.GAP, Ui.INK, 1.5F);
        u.tip(RX, Y + 46, rw, 13, Component.translatable("clan.pioneers.ui.fact.level"), Component.translatable(t.getLong("next") > 0
              ? "clan.pioneers.ui.sheet.points" : "clan.pioneers.ui.sheet.points_max", Ui.group(t.getLong("points")), Ui.group(t.getLong("next"))));
        long pts = t.getLong("points"), next = t.getLong("next");
        u.bar(RX, Y + 62, rw, 5, next > 0 ? pts / (double) next : 1, Ui.INK_LIGHT);
        u.fit(next > 0 ? Component.translatable("clan.pioneers.ui.sheet.points_short", Ui.group(pts), Ui.group(next))
              : Component.translatable("clan.pioneers.ui.sheet.points_max_short", Ui.group(pts)), RX, Y + 70, rw, Ui.DIM, Ui.SMALL);
        //echelle d'appartenance
        int level = solo ? 0 : !t.getString("people").isEmpty() ? 3 : !t.getString("faction").isEmpty() ? 2 : 1;
        u.fit(Component.translatable("clan.pioneers.ui.sheet.ladder_short"), RX, Y + 82, rw, Ui.DIM, Ui.SMALL);
        for (int i = 0; i < LADDER.length; i++) {
            int lx = RX + i * 34, ly = Y + 91;
            if (i == level) {
                u.fill(lx - 1, ly - 1, lx + 33, ly + 33, 0x30243660);
                u.frame(lx - 1, ly - 1, 34, 34, Ui.INK);
                u.image(Ui.gui("member_" + LADDER[i]), lx, ly, 32, 32, 32, 32);
            } else {
                u.tinted(Ui.gui("member_" + LADDER[i]), lx, ly, 32, 32, 32, 32, 0xFFFFFF, 0.3F);
            }
            u.tip(lx, ly, 32, 32, Component.translatable("clan.pioneers.ui.ladder." + LADDER[i]),
                  Component.translatable("clan.pioneers.ui.ladder." + LADDER[i] + ".desc"));
        }
        Component where = switch (level) {
            case 2 -> Component.translatable("clan.pioneers.ui.ladder.in_faction", t.getString("faction"));
            case 3 -> Component.translatable("clan.pioneers.ui.ladder.in_people", t.getString("faction"));
            default -> Component.translatable("clan.pioneers.ui.ladder." + LADDER[level]);
        };
        u.fit(where, RX, Y + 127, rw, Ui.INK, Ui.SMALL);
        //droits du joueur, par famille (toutes les permissions, dans l'ordre de la vue Roles)
        List<String> have = Ui.strings(t.getList("perms", Tag.TAG_STRING));
        List<String> all = RolesView.ordered();
        u.separator(X, RIGHTS_Y - 3, W);
        u.fit(Component.translatable("clan.pioneers.ui.sheet.rights", have.size(), all.size()), X, RIGHTS_Y, W, Ui.DIM, Ui.SMALL);
        u.tip(X, RIGHTS_Y, W, 7, Component.translatable("clan.pioneers.ui.sheet.rights_tip"));
        rights(u, all, have, X, RIGHTS_Y + 9);
        //bas : quitter (membre), inviter (Solitaire), choisir un regime
        int by = Y + H - 14;
        if (solo) {
            u.wrap(Component.translatable("clan.pioneers.ui.sheet.solo_hint"), X, by - 1, W - 104, Ui.DIM, Ui.SMALL, 2);
            u.iconButton(X + W - 100, by, 100, 14, Ui.gui("ico_invite"), Component.translatable("clan.pioneers.ui.members.invite_short"), true,
                  () -> ClanTab.select("members"));
            return;
        }
        if (t.getBoolean("choose") && t.getBoolean("isRep")) {
            u.iconButton(X, by, 136, 14, Ui.gui("nav_government"), Component.translatable("clan.pioneers.ui.sheet.choose_regime"), true,
                  () -> ClanTab.select("government"));
        }
        u.confirm("leave", X + W - 136, by, 136, 14, Ui.gui("nav_home"), Component.translatable("clan.pioneers.ui.sheet.leave"), true,
              () -> ClanUiClient.act("leave", "sheet", new CompoundTag()));
    }

    /** Icones des permissions, 17 px, un ecart de 5 px entre les familles, a la ligne en bout de fiche ; absente = pale. */
    static void rights(Ui u, List<String> all, List<String> have, int x0, int y0) {
        int x = x0, y = y0;
        String family = null;
        for (String p : all) {
            String f = RolesView.family(p);
            if (family != null && !f.equals(family)) {
                x += 5;
            }
            family = f;
            if (x + 16 > X + W) {
                x = x0;
                y += 17;
            }
            boolean on = have.contains(p);
            if (on) {
                u.image(Ui.gui("perm_" + p), x, y, 16, 16, 16, 16);
            } else {
                u.tinted(Ui.gui("perm_" + p), x, y, 16, 16, 16, 16, 0xFFFFFF, 0.22F);
            }
            u.tip(x, y, 16, 16, Component.translatable("clan.pioneers.perm." + p), Component.translatable("clan.pioneers.perm." + p + ".desc"),
                  Component.translatable(on ? "clan.pioneers.ui.perm.granted" : "clan.pioneers.ui.perm.denied"));
            x += 17;
        }
    }

    /** Insigne d'un role : celui du role propose de meme identifiant, sinon celui du rang le plus proche en dessous. */
    static String insignia(String role, int rank) {
        String r = role.toLowerCase(Locale.ROOT);
        return switch (r) {
            case "representative", "lieutenant", "steward", "surveyor", "guard", "recruiter", "artisan", "member", "recruit", "solo" -> r;
            default -> rank >= 100 ? "representative" : rank >= 80 ? "lieutenant" : rank >= 60 ? "steward" : rank >= 50 ? "guard" : rank >= 40
                  ? "artisan" : rank >= 30 ? "member" : "recruit";
        };
    }
}
