package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.H;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Gouvernement : regime (embleme, accroche), Representant, Conseil, mandats, changement de regime en
 * attente ; propositions ouvertes (intitule traduit : {@code label}/{@code labelArgs} pour la guerre et les traites, sinon d'apres le type
 * et ses donnees), bulletins, decompte (jamais pendant un vote a bulletin secret), voter (pour, contre, abstention, ou un candidat) ; les
 * dix dernieres fermees ; proposer selon le regime : taxes, identite, regime, contre-pouvoir (le bouton dit « Decider » quand la decision
 * revient au joueur seul, « Proposer » sinon ; le serveur revoit tout). 1.0.5-beta (lot 20, retour 10) : en-tete en faits a icone (une
 * information par ligne, accroche du regime en infobulle), cartes de proposition a hauteur variable (intitule et motif replies au lieu
 * d'etre coupes, delai et bulletins a icone, l'auteur en infobulle), formulaires en pastilles a icone, decider seul un regime payant en
 * deux clics.
 */
final class GovernmentFiche extends ClanTab.Fiche {

    static final int LIST_Y = Y + 42, LIST_H = 148, BAR_Y = Y + H - 14, CARD = 44;
    static final String[] FORMS = {"taxes", "identity", "regime", "counter"};
    static final String[] FORM_ICONS = {"ico_tax", "perm_edit_identity", "nav_government", "perm_moderate"};
    static final String[] TAXES = {"activity", "dues", "market"};
    static final String[] FIELDS = {"name", "tag", "motto", "color"};
    static final String[] REGIMES = {"democracy", "empire", "council", "cooperative"};
    @Nullable
    private static String form;
    private static String taxKind = "activity", field = "name", regime = "democracy";
    private static int color = -1;

    @Override
    String id() {
        return "government";
    }

    @Override
    void enter() {
        ClanUiClient.request("government");
        if (ClanTab.may("treasury_view")) {
            ClanUiClient.request("treasury");
        }
    }

    /** Ouvre un formulaire de proposition (depuis une autre fiche). */
    static void form(@Nullable String f) {
        form = f;
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("government");
        if (waiting(u, t)) {
            return;
        }
        header(u, t);
        if (form == null) {
            proposals(u, t);
        } else {
            switch (form) {
                case "taxes" -> taxes(u, t);
                case "identity" -> identity(u, t);
                case "regime" -> regime(u, t);
                default -> counter(u, t);
            }
        }
        //1.0.5-beta (lot 20) : formulaires en pastilles a icone
        int bw = (W - 6) / 4;
        for (int i = 0; i < FORMS.length; i++) {
            String f = FORMS[i];
            u.navChip(X + i * (bw + 2), BAR_Y, i == 3 ? W - 3 * (bw + 2) : bw, 14, Ui.gui(FORM_ICONS[i]), Component.translatable(
                  "clan.pioneers.ui.gov.form_short." + f), f.equals(form), 0, () -> form = f.equals(form) ? null : f);
            u.tip(X + i * (bw + 2), BAR_Y, i == 3 ? W - 3 * (bw + 2) : bw, 14, Component.translatable("clan.pioneers.ui.gov.form." + f));
        }
    }

    // ------------------------------------------------------------------ en-tete
    private static void header(Ui u, CompoundTag t) {
        String reg = t.getString("regime");
        u.image(Ui.gui("regime_" + reg), X, Y, 32, 32, 32, 32);
        u.tip(X, Y, 32, 32, Component.translatable("clan.pioneers.regime." + reg), Component.translatable("clan.pioneers.regime." + reg + ".text"));
        u.fit(Component.translatable("clan.pioneers.regime." + reg), X + 38, Y + 1, W - 38, Ui.INK, 1F);
        u.tip(X + 38, Y + 1, W - 38, 8, Component.translatable("clan.pioneers.regime." + reg), Component.translatable("clan.pioneers.regime." + reg + ".hook"));
        //1.0.5-beta (lot 20) : un fait par ligne, a icone (mode de designation, accroche du regime en infobulle)
        long now = t.getLong("now");
        long term = "cooperative".equals(reg) ? t.getLong("rotateAt") : t.getLong("term");
        boolean showTerm = term > now && !"empire".equals(reg);
        u.fact(Ui.gui("rank_representative"), Component.literal(t.getString("representative")), X + 38, Y + 11, W - 38 - (showTerm ? 76 : 0), Ui.TEXT,
              Component.translatable("clan.pioneers.ui.gov.rep", t.getString("representative"), Component.translatable("clan.pioneers.repmode."
                    + t.getString("repMode"))));
        if (showTerm) {
            u.fact(Ui.gui("ico_time"), Ui.duration(term - now), X + W - 72, Y + 11, 72, Ui.TEXT, Component.translatable("clan.pioneers.ui.gov.term_short",
                  Ui.duration(term - now)));
        }
        ListTag council = t.getList("council", Tag.TAG_STRING);
        if (!t.getString("pending").isEmpty()) {
            Component pend = Component.translatable("clan.pioneers.ui.gov.pending_short", Component.translatable("clan.pioneers.regime." + t.getString(
                  "pending")), Ui.duration(t.getLong("pendingAt") - now));
            u.fact(Ui.gui("ico_warning"), pend, X + 38, Y + 24, W - 38, Ui.MINUS, pend);
        } else if (!council.isEmpty()) {
            Component names = Component.literal(String.join(", ", Ui.strings(council)));
            u.fact(Ui.gui("ico_council"), names, X + 38, Y + 24, W - 38, Ui.TEXT, Component.translatable("clan.pioneers.ui.gov.council_short", names));
        } else if (!t.getString("heir").isEmpty()) {
            u.fact(Ui.gui("ico_heir"), Component.literal(t.getString("heir")), X + 38, Y + 24, W - 38, Ui.TEXT, Component.translatable(
                  "clan.pioneers.ui.gov.heir_short", t.getString("heir")));
        }
        u.separator(X, Y + 38, W);
    }

    // ------------------------------------------------------------------ propositions
    /** Intitule d'une proposition : {@code label}/{@code labelArgs} si present, sinon d'apres le type et ses donnees. */
    static Component label(CompoundTag p) {
        if (p.contains("label")) {
            ListTag a = p.getList("labelArgs", Tag.TAG_STRING);
            Object[] args = new Object[a.size()];
            for (int i = 0; i < args.length; i++) {
                args[i] = Ui.arg(a.getString(i));
            }
            return Component.translatable(p.getString("label"), args);
        }
        String type = p.getString("type");
        return switch (type) {
            case "taxes" -> Component.translatable("clan.pioneers.ui.gov.p.taxes." + p.getString("d_kind"), p.getInt("d_value"));
            case "kick" -> Component.translatable("clan.pioneers.ui.gov.p.kick", p.getString("target"));
            case "spend" -> Component.translatable("clan.pioneers.ui.gov.p.spend", Ui.group(p.getLong("d_amount")));
            case "regime" -> Component.translatable("clan.pioneers.ui.gov.p.regime", Component.translatable("clan.pioneers.regime." + p.getString("d_regime")));
            case "identity" -> Component.translatable("clan.pioneers.ui.gov.p.identity", Component.translatable("clan.pioneers.identity."
                  + p.getString("d_field")), p.getString("d_value"));
            case "roles" -> Component.translatable("clan.pioneers.ui.gov.p.roles", Component.translatable("clan.pioneers.roles.op." + p.getString("d_op")),
                  Ui.txt(p.getString("d_role")));
            default -> Component.translatable("clan.pioneers.vote.type." + type);
        };
    }

    /** 1.0.5-beta (lot 20) : hauteur d'une carte de proposition ouverte (intitule replie sur 2 lignes, motif sur 2 lignes). */
    static int cardHeight(Ui u, CompoundTag p) {
        int h = 2 + u.wrapHeight(label(p), W - 4, 1F, 2) + 13;
        if (!p.getString("text").isBlank()) {
            h += u.wrapHeight(Component.translatable("clan.pioneers.quoted", p.getString("text")), W - 4, Ui.SMALL, 2) + 1;
        }
        return h + 18;
    }

    /** Hauteur d'une ligne de decision fermee (intitule replie sur 2 lignes, decompte dessous). */
    static int closedHeight(Ui u, CompoundTag p) {
        return u.wrapHeight(Component.literal("+ ").append(label(p)), W - 70, Ui.SMALL, 2) + 11;
    }

    private static void proposals(Ui u, CompoundTag t) {
        ListTag open = t.getList("open", Tag.TAG_COMPOUND), closed = t.getList("closed", Tag.TAG_COMPOUND);
        long now = t.getLong("now");
        int content = 14 + (open.isEmpty() ? 10 : 0) + 16;
        for (int i = 0; i < open.size(); i++) {
            content += cardHeight(u, open.getCompound(i)) + 3;
        }
        for (int i = 0; i < closed.size(); i++) {
            content += closedHeight(u, closed.getCompound(i));
        }
        u.s.scrollArea(3, X, LIST_Y, W, LIST_H, content);
        u.s.beginClip(u.g, X, LIST_Y, W, LIST_H);
        int y = LIST_Y - u.s.scrollOf(3);
        u.icon12(Ui.gui("ico_vote"), X, y);
        u.fit(Component.translatable("clan.pioneers.ui.gov.open"), X + Ui.GAP, y + 3, W - Ui.GAP, Ui.INK, Ui.SMALL);
        y += 14;
        if (open.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.gov.none_open"), X, y, W, Ui.DIM, Ui.SMALL);
            y += 10;
        }
        for (int i = 0; i < open.size(); i++) {
            CompoundTag p = open.getCompound(i);
            int ch = cardHeight(u, p);
            proposal(u, p, now, y, ch);
            y += ch + 3;
        }
        y += 2;
        u.icon12(Ui.gui("nav_journal"), X, y);
        u.fit(Component.translatable("clan.pioneers.ui.gov.closed"), X + Ui.GAP, y + 3, W - Ui.GAP, Ui.INK, Ui.SMALL);
        y += 14;
        for (int i = closed.size() - 1; i >= 0; i--) {
            CompoundTag p = closed.getCompound(i);
            String st = p.getString("status");
            int c = "passed".equals(st) ? Ui.PLUS : "rejected".equals(st) || "failed".equals(st) ? Ui.MINUS : Ui.DIM;
            String sign = "passed".equals(st) ? "+ " : "rejected".equals(st) || "failed".equals(st) ? "− " : "· ";
            int lh = u.wrap(Component.literal(sign).append(label(p)), X, y, W - 70, Ui.TEXT, Ui.SMALL, 2);
            u.right(Component.translatable("clan.pioneers.ui.gov.status." + st), X + W - 3, y, 64, c, Ui.SMALL);
            Component sub = p.contains("yes") ? Component.translatable("clan.pioneers.ui.gov.tally", fmt(p.getDouble("yes")), fmt(p.getDouble("no")))
                  : Component.translatable("clan.pioneers.vote.secret");
            ListTag won = p.getList("won", Tag.TAG_STRING);
            if (!won.isEmpty()) {
                sub = Component.translatable("clan.pioneers.ui.gov.won", String.join(", ", Ui.strings(won)));
            }
            u.fit(sub, X + 8, y + lh, W - 11, Ui.DIM, Ui.SMALL);
            y += lh + 11;
        }
        u.s.endClip(u.g);
    }

    private static String fmt(double v) {
        return v == Math.rint(v) ? Long.toString((long) v) : String.format(java.util.Locale.ROOT, "%.1f", v);
    }

    private static void proposal(Ui u, CompoundTag p, long now, int y, int ch) {
        boolean visible = y + ch > LIST_Y && y < LIST_Y + LIST_H;
        if (!visible) {
            return;
        }
        u.fill(X, y, X + W - 3, y + ch, 0x0E243660);
        int yy = y + 2;
        yy += u.wrap(label(p), X + 2, yy, W - 7, Ui.INK, 1F, 2);
        //delai, bulletins, majorite : faits a icone ; l'auteur en infobulle
        Component left = Ui.duration(p.getLong("closes") - now);
        int x = X + 2;
        u.icon12(Ui.gui("ico_time"), x, yy);
        int lw = u.fit(left, x + Ui.GAP, yy + 3, 60, Ui.TEXT, Ui.SMALL);
        u.tip(x, yy, Ui.GAP + lw, 12, Component.translatable("clan.pioneers.ui.gov.closes_tip"));
        x += Ui.GAP + lw + 8;
        u.icon12(Ui.gui("ico_vote"), x, yy);
        int bw = u.fit(Component.translatable("clan.pioneers.ui.gov.ballots_short", p.getInt("ballots"), p.getInt("electorate")), x + Ui.GAP, yy + 3, 40,
              Ui.TEXT, Ui.SMALL);
        u.tip(x, yy, Ui.GAP + bw, 12, Component.translatable("clan.pioneers.ui.gov.ballots_tip"));
        x += Ui.GAP + bw + 8;
        if (!p.getString("maj").isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.majority." + p.getString("maj")), x, yy + 3, X + W - 5 - x, Ui.DIM, Ui.SMALL);
        }
        u.tip(X, y, W - 3, 2 + 10, Component.translatable("clan.pioneers.ui.gov.by_tip", p.getString("author").isEmpty() ? Component.translatable(
              "clan.pioneers.vote.system") : Component.literal(p.getString("author"))));
        yy += 13;
        if (!p.getString("text").isBlank()) {
            yy += u.wrap(Component.translatable("clan.pioneers.quoted", p.getString("text")), X + 2, yy, W - 7, Ui.TEXT, Ui.SMALL, 2) + 1;
        }
        int by = yy + 1;
        Component tally = p.contains("yes") ? Component.translatable("clan.pioneers.ui.gov.tally", fmt(p.getDouble("yes")), fmt(p.getDouble("no")))
              : Component.translatable("clan.pioneers.vote.secret");
        u.fit(tally, X + 2, by + 3, 88, Ui.TEXT, Ui.SMALL);
        String id = p.getString("id");
        boolean inView = by >= LIST_Y && by + 13 <= LIST_Y + LIST_H;
        if (!p.getBoolean("elector")) {
            u.right(Component.translatable("clan.pioneers.ui.gov.not_elector"), X + W - 5, by + 3, W - 100, Ui.DIM, Ui.SMALL);
        } else if (p.getBoolean("voted")) {
            u.right(Component.literal("+ ").append(Component.translatable("clan.pioneers.ui.gov.voted")), X + W - 5, by + 3, W - 100, Ui.PLUS, Ui.SMALL);
        } else if (p.getBoolean("election")) {
            ListTag cands = p.getList("cands", Tag.TAG_COMPOUND);
            int cx = X + 94, cw = 60;
            for (int i = 0; i < cands.size() && cx + cw <= X + W - 3; i++, cx += cw + 2) {
                CompoundTag c = cands.getCompound(i);
                String tok = c.getString("token");
                if (inView) {
                    u.button(cx, by, cw, 13, Component.literal(c.getString("char")), true, () -> vote(id, tok));
                }
            }
        } else if (inView) {
            int vw = 58;
            u.button(X + W - 3 * vw - 9, by, vw, 13, Component.literal("+ ").append(Component.translatable("clan.pioneers.vote.yes")), true,
                  () -> vote(id, "yes"));
            u.button(X + W - 2 * vw - 7, by, vw, 13, Component.literal("− ").append(Component.translatable("clan.pioneers.vote.no")), true,
                  () -> vote(id, "no"));
            u.button(X + W - vw - 5, by, vw, 13, Component.translatable("clan.pioneers.vote.abstain"), true, () -> vote(id, "abstain"));
        }
    }

    private static void vote(String id, String choice) {
        CompoundTag a = new CompoundTag();
        a.putString("proposal", id);
        a.putString("choice", choice);
        ClanUiClient.act("vote", "government", a);
    }

    // ------------------------------------------------------------------ formulaires
    /** Qui tranche cette decision dans ce regime, et le libelle du bouton (Decider / Proposer). */
    private static Component decide(Ui u, CompoundTag t, String decision, int y) {
        CompoundTag r = t.getCompound("decisions").getCompound(decision);
        String by = r.getString("by");
        boolean mine = "representative".equals(by) && ClanTab.isRep();
        Component who = "representative".equals(by) ? Component.translatable("clan.pioneers.ui.gov.decided_by_rep")
              : Component.translatable("clan.pioneers.ui.gov.decided_by_vote", Component.translatable("clan.pioneers.decider." + by),
                    Component.translatable("clan.pioneers.majority." + r.getString("maj")));
        u.fit(who, X, y, W, Ui.DIM, Ui.SMALL);
        return Component.translatable(mine ? "clan.pioneers.ui.gov.decide" : "clan.pioneers.ui.gov.propose");
    }

    private static void taxes(Ui u, CompoundTag t) {
        int y = LIST_Y;
        u.fit(Component.translatable("clan.pioneers.ui.gov.form.taxes.title"), X, y, W, Ui.INK, 1F);
        Component button = decide(u, t, "taxes", y + 11);
        CompoundTag tr = ClanUiClient.sheet("treasury");
        int cw = (W - 4) / 3;
        for (int i = 0; i < TAXES.length; i++) {
            String k = TAXES[i];
            u.chip(X + i * (cw + 2), y + 22, cw, 13, Component.translatable("clan.pioneers.ui.gov.tax_short." + k), k.equals(taxKind), true, () -> taxKind = k);
        }
        if (tr != null) {
            String cur = switch (taxKind) {
                case "dues" -> "dues";
                case "market" -> "market";
                default -> "activity";
            };
            String capKey = "cap" + Character.toUpperCase(cur.charAt(0)) + cur.substring(1);
            u.fit(Component.translatable("clan.pioneers.ui.gov.tax_now", tr.getInt(cur), tr.getInt(capKey)), X, y + 40, W, Ui.TEXT, Ui.SMALL);
        }
        EditBox v = ClanTab.numberBox(u, "tax:" + taxKind, X, y + 52, 70, Component.translatable("clan.pioneers.ui.gov.value"));
        long n = ClanTab.parse(v);
        u.button(X + 76, y + 52, 110, 14, button, !v.getValue().isEmpty() && (ClanTab.may("set_taxes") || ClanTab.may("propose")), () -> {
            CompoundTag a = new CompoundTag();
            a.putString("kind", taxKind);
            a.putInt("value", (int) Math.min(Integer.MAX_VALUE, n));
            ClanUiClient.act("tax", "government", a);
            form = null;
        });
        u.wrap(Component.translatable("clan.pioneers.ui.gov.tax_hint." + taxKind), X, y + 72, W, Ui.DIM, Ui.SMALL, 4);
    }

    private static void identity(Ui u, CompoundTag t) {
        int y = LIST_Y;
        u.fit(Component.translatable("clan.pioneers.ui.gov.form.identity.title"), X, y, W, Ui.INK, 1F);
        Component button = decide(u, t, "identity", y + 11);
        int cw = (W - 6) / 4;
        for (int i = 0; i < FIELDS.length; i++) {
            String f = FIELDS[i];
            u.chip(X + i * (cw + 2), y + 22, cw, 13, Component.translatable("clan.pioneers.identity." + f), f.equals(field), true, () -> field = f);
        }
        boolean ok;
        String value = "";
        if ("color".equals(field)) {
            for (int c = 0; c < 16; c++) {
                int sx = X + c * 17, sy = y + 42;
                u.fill(sx, sy, sx + 15, sy + 15, 0xFF000000 | Ui.dye(c));
                u.frame(sx - 1, sy - 1, 17, 17, color == c ? Ui.INK : Ui.FAINT);
                int cc = c;
                u.click(sx, sy, 15, 15, () -> color = cc);
                u.tip(sx, sy, 15, 15, Component.translatable("color.minecraft." + net.minecraft.world.item.DyeColor.byId(c).getName()));
            }
            ok = color >= 0;
        } else {
            int max = "name".equals(field) ? 24 : "tag".equals(field) ? 4 : 60;
            EditBox b = ClanTab.textBox(u, "identity:" + field, X, y + 42, W - 116, Component.translatable("clan.pioneers.ui.gov.new." + field), max);
            value = b.getValue().trim();
            ok = !value.isEmpty();
        }
        String val = value;
        u.button(X + W - 110, y + 42, 110, 14, button, ok && (ClanTab.may("edit_identity") || ClanTab.may("propose")), () -> {
            CompoundTag a = new CompoundTag();
            a.putString("field", field);
            a.putString("value", val);
            a.putInt("color", Math.max(0, color));
            ClanUiClient.act("identity", "government", a);
            form = null;
        });
        u.wrap(Component.translatable("clan.pioneers.ui.gov.identity_hint"), X, y + 64, W, Ui.DIM, Ui.SMALL, 4);
    }

    private static void regime(Ui u, CompoundTag t) {
        int y = LIST_Y;
        boolean choose = t.getBoolean("choose");
        u.fit(Component.translatable(choose ? "clan.pioneers.ui.gov.form.regime.choose" : "clan.pioneers.ui.gov.form.regime.title"), X, y, W, Ui.INK, 1F);
        Component button = choose ? Component.translatable("clan.pioneers.ui.gov.decide") : decide(u, t, "regime", y + 11);
        String now = t.getString("regime");
        int cw = (W - 6) / 4;
        for (int i = 0; i < REGIMES.length; i++) {
            String r = REGIMES[i];
            int rx = X + i * (cw + 2), ry = y + 22;
            boolean sel = r.equals(regime);
            u.fill(rx, ry, rx + cw, ry + 50, sel ? 0x40243660 : u.over(rx, ry, cw, 50) ? 0x20243660 : 0x0C243660);
            u.frame(rx, ry, cw, 50, sel ? Ui.INK : Ui.FAINT);
            u.image(Ui.gui("regime_" + r), rx + (cw - 32) / 2, ry + 3, 32, 32, 32, 32);
            u.center(Component.translatable("clan.pioneers.regime." + r), rx + cw / 2, ry + 39, cw - 4, r.equals(now) ? Ui.GOLD : Ui.INK, Ui.SMALL);
            u.click(rx, ry, cw, 50, () -> regime = r);
            u.tip(rx, ry, cw, 50, Component.translatable("clan.pioneers.regime." + r), Component.translatable("clan.pioneers.regime." + r + ".text"));
        }
        int ty = y + 76;
        ty += u.wrap(Component.translatable("clan.pioneers.regime." + regime + ".hook"), X, ty, W, Ui.TEXT, Ui.SMALL, 2) + 2;
        long ready = t.getLong("regimeReady"), clock = t.getLong("now");
        Component cost = choose ? Component.translatable("clan.pioneers.ui.gov.regime_free")
              : ready > clock ? Component.translatable("clan.pioneers.ui.gov.regime_wait", Ui.duration(ready - clock))
                    : Component.translatable("clan.pioneers.ui.gov.regime_cost", Ui.group(t.getLong("regimeCost")));
        u.wrap(cost, X, ty - 3, W - 116, ready > clock && !choose ? Ui.MINUS : Ui.DIM, Ui.SMALL, 2);
        //1.0.5-beta (lot 20) : decider seul un regime payant est irreversible (cout preleve) : deux clics
        Runnable send = () -> {
            CompoundTag a = new CompoundTag();
            a.putString("regime", regime);
            ClanUiClient.act("regime", "government", a);
            form = null;
        };
        boolean ok = !regime.equals(now) || choose;
        boolean alone = !choose && "representative".equals(t.getCompound("decisions").getCompound("regime").getString("by")) && ClanTab.isRep();
        if (alone && t.getLong("regimeCost") > 0) {
            u.confirm("regime:" + regime, X + W - 110, ty - 3, 110, 14, Ui.gui("nav_government"), button, ok, send);
            u.tip(X + W - 110, ty - 3, 110, 14, Component.translatable("clan.pioneers.ui.gov.decide_cost_tip", Ui.group(t.getLong("regimeCost"))));
        } else {
            u.iconButton(X + W - 110, ty - 3, 110, 14, Ui.gui("nav_government"), button, ok, send);
        }
    }

    private static void counter(Ui u, CompoundTag t) {
        int y = LIST_Y;
        String type = t.getString("counter");
        u.fit(Component.translatable("clan.pioneers.ui.gov.form.counter.title"), X, y, W, Ui.INK, 1F);
        u.fit(Component.translatable("clan.pioneers.counter." + type), X, y + 13, W, Ui.TEXT, 1F);
        int ty = y + 25;
        if (!"none".equals(type)) {
            ty += u.wrap(Component.translatable("clan.pioneers.counter." + type + ".desc"), X, ty, W, Ui.DIM, Ui.SMALL, 4) + 4;
        }
        long ready = t.getLong("counterReady"), now = t.getLong("now");
        boolean wait = ready > now;
        if (wait) {
            u.fit(Component.literal("− ").append(Component.translatable("clan.pioneers.ui.gov.counter_wait", Ui.duration(ready - now))), X, ty, W, Ui.MINUS,
                  Ui.SMALL);
        }
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("clan.pioneers.ui.gov.counter_tip"));
        u.button(X, ty + 10, 140, 14, Component.translatable("clan.pioneers.ui.gov.counter_launch"), !"none".equals(type) && !wait, () -> {
            CompoundTag a = new CompoundTag();
            a.putString("type", type);
            ClanUiClient.act("counter", "government", a);
            form = null;
        });
        u.tip(X, ty + 10, 140, 14, tip);
    }
}
