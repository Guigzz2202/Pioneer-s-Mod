package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.H;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import fr.pioneers.Pioneers;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Diplomatie. Clans : creneaux de siege (et leur changement, effectif 7 jours plus tard), guerres
 * (preavis, en cours, treve ; cessez-le-feu, capitulation), sieges en cours (avancement du drapeau quand il est charge), traites et offres
 * (signer, refuser, rompre). Peuples : la reputation du clan aupres de chaque peuple (le micro-peuple : jauge, palier, gain du jour), celle
 * de la faction, et l'estime des villages connus. Proposer un traite ou declarer la guerre se fait depuis la fiche d'un clan de l'annuaire.
 * 1.0.5-beta (lot 20, retour 10) : vues et sections a icone, motif d'une guerre en infobulle, capituler et rompre un traite en deux clics.
 */
final class DiplomacyFiche extends ClanTab.Fiche {

    static final int LIST_Y = Y + 58, LIST_H = Y + H - LIST_Y;
    private static String view = "clans";
    private static int first = -1, second = -1;

    @Override
    String id() {
        return "diplomacy";
    }

    @Override
    void enter() {
        ClanUiClient.request("diplomacy");
        first = second = -1;
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("diplomacy");
        if (waiting(u, t)) {
            return;
        }
        int cw = (W - 2) / 2;
        u.navChip(X, Y, cw, 13, Ui.gui("nav_clans"), Component.translatable("clan.pioneers.ui.diplo.clans"), "clans".equals(view), 0, () -> view = "clans");
        u.navChip(X + cw + 2, Y, W - cw - 2, 13, Ui.gui("nav_peoples"), Component.translatable("clan.pioneers.ui.diplo.peoples"), "peoples".equals(view), 0,
              () -> view = "peoples");
        if ("peoples".equals(view)) {
            peoples(u, t.getCompound("rep"));
        } else {
            clans(u, t);
        }
    }

    // ------------------------------------------------------------------ clans
    static String hour(int h) {
        return String.format(java.util.Locale.ROOT, "%02d:00", Math.floorMod(h, 24));
    }

    private static void clans(Ui u, CompoundTag t) {
        long now = t.getLong("now");
        int hours = Math.max(1, t.getInt("windowHours"));
        int[] w = t.getIntArray("windows");
        int y = Y + 18;
        u.icon12(Ui.gui("ico_siege"), X, y - 3);
        u.fit(Component.translatable("clan.pioneers.ui.diplo.windows_short"), X + Ui.GAP, y, 120 - Ui.GAP, Ui.INK, Ui.SMALL);
        u.tip(X, y - 3, 120, 12, Component.translatable("clan.pioneers.ui.diplo.windows"), Component.translatable("clan.pioneers.ui.diplo.windows_hint"));
        //1.9.1 : l'etat (ouvert, ferme) est mesure d'abord, les horaires prennent le reste
        boolean open = t.getBoolean("inWindow");
        Component state = Component.literal(open ? "+ " : "· ").append(Component.translatable(open ? "clan.pioneers.ui.diplo.windows_open"
              : "clan.pioneers.ui.diplo.windows_closed"));
        int sw = u.right(state, X + W, y, 60, open ? Ui.MINUS : Ui.DIM, Ui.SMALL);
        if (w.length >= 2) {
            Component when = Component.translatable("clan.pioneers.ui.diplo.windows_value", hour(w[0]), hour(w[0] + hours), hour(w[1]), hour(w[1] + hours));
            u.fit(when, X + 122, y, Math.min(110, W - 122 - sw - 4), Ui.TEXT, Ui.SMALL);
        }
        int[] pending = t.getIntArray("pendingWindows");
        if (pending.length >= 2) {
            u.fit(Component.translatable("clan.pioneers.ui.diplo.windows_pending", hour(pending[0]), hour(pending[1]), Ui.duration(t.getLong("windowsFrom")
                                                                                                                                   - now)), X, y + 9, W,
                  Ui.DIM, Ui.SMALL);
        }
        boolean edit = (t.getBoolean("p_siege") || t.getBoolean("p_war")) && !t.getBoolean("solo") && w.length >= 2;
        if (edit) {
            if (first < 0) {
                first = w[0];
                second = w[1];
            }
            int ey = y + 19;
            stepper(u, X, ey, first, v -> first = v);
            stepper(u, X + 64, ey, second, v -> second = v);
            boolean changed = first != w[0] || second != w[1];
            u.button(X + 132, ey, 70, 13, Component.translatable("clan.pioneers.ui.diplo.windows_set"), changed, () -> {
                CompoundTag a = new CompoundTag();
                a.putInt("first", first);
                a.putInt("second", second);
                ClanUiClient.act("windows", "diplomacy", a);
            });
            u.tip(X + 132, ey, 70, 13, Component.translatable("clan.pioneers.ui.diplo.windows_hint"));
        }
        u.separator(X, LIST_Y - 4, W);
        list(u, t, now);
    }

    /** Heure de debut d'un creneau : [−] 19:00 [+]. */
    private static void stepper(Ui u, int x, int y, int value, java.util.function.IntConsumer set) {
        u.button(x, y, 13, 13, Component.literal("−"), true, () -> set.accept(Math.floorMod(value - 1, 24)));
        u.center(Component.literal(hour(value)), x + 30, y + 3, 32, Ui.INK, 1F);
        u.button(x + 47, y, 13, 13, Component.literal("+"), true, () -> set.accept(Math.floorMod(value + 1, 24)));
    }

    private static void list(Ui u, CompoundTag t, long now) {
        ListTag wars = t.getList("wars", Tag.TAG_COMPOUND), sieges = t.getList("sieges", Tag.TAG_COMPOUND), treaties = t.getList("treaties",
              Tag.TAG_COMPOUND);
        int content = 12 + Math.max(1, wars.size()) * 34 + 14 + Math.max(1, sieges.size()) * 10 + 14;
        for (int i = 0; i < treaties.size(); i++) {
            CompoundTag c = treaties.getCompound(i);
            content += 12 + 15 * (c.getList("treaties", Tag.TAG_COMPOUND).size() + c.getList("offers", Tag.TAG_COMPOUND).size());
        }
        ListTag federal = t.getList("federal", Tag.TAG_COMPOUND);
        if (!federal.isEmpty()) {
            content += 14;
            for (int i = 0; i < federal.size(); i++) {
                content += 9 * federal.getCompound(i).getList("treaties", Tag.TAG_COMPOUND).size();
            }
        }
        u.s.scrollArea(4, X, LIST_Y, W, LIST_H, Math.max(content, 10));
        u.s.beginClip(u.g, X, LIST_Y, W, LIST_H);
        int y = LIST_Y - u.s.scrollOf(4);
        boolean mayWar = t.getBoolean("p_war"), mayDiplo = t.getBoolean("p_diplomacy");
        //guerres
        u.icon12(Ui.gui("ico_war"), X, y - 2);
        u.fit(Component.translatable("clan.pioneers.ui.diplo.wars"), X + Ui.GAP, y + 1, W - Ui.GAP, Ui.INK, Ui.SMALL);
        y += 12;
        if (!t.getBoolean("war")) {
            u.fit(Component.translatable("clan.pioneers.ui.diplo.war_off"), X, y, W, Ui.DIM, Ui.SMALL);
            y += 34;
        } else if (wars.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.diplo.no_war"), X, y, W, Ui.DIM, Ui.SMALL);
            y += 34;
        }
        for (int i = 0; i < wars.size() && t.getBoolean("war"); i++, y += 34) {
            CompoundTag w = wars.getCompound(i);
            String st = w.getString("state");
            u.fill(X, y - 1, X + W, y + 31, "truce".equals(st) ? 0x0C243660 : 0x16992E25);
            MutableComponent head = Component.literal("truce".equals(st) ? "· " : "! ").append(Component.literal(w.getString("other")));
            head.append(" — ").append(Component.translatable(w.getBoolean("attacker") ? "clan.pioneers.war.side.attacker" : "clan.pioneers.war.side.defender"));
            u.fit(head, X + 2, y, W - 4, "truce".equals(st) ? Ui.TEXT : Ui.MINUS, 1F);
            long until = "notice".equals(st) ? w.getLong("start") : "truce".equals(st) ? w.getLong("truce") : 0;
            MutableComponent info = Component.translatable("clan.pioneers.ui.diplo.war_state." + st);
            if (until > now) {
                info.append(" (").append(Ui.duration(until - now)).append(")");
            }
            info.append(" · ").append(Component.translatable("clan.pioneers.ui.diplo.captures_short", w.getInt("captures")));
            u.fit(info, X + 2, y + 10, W - 4, Ui.DIM, Ui.SMALL);
            //1.0.5-beta (lot 20) : le motif (texte libre, parfois long) en infobulle de la carte
            if (!w.getString("reason").isBlank() && y >= LIST_Y && y + 18 <= LIST_Y + LIST_H) {
                u.tip(X, y, W, 18, head, Component.translatable("clan.pioneers.quoted", w.getString("reason")));
            }
            boolean act = mayWar && !"truce".equals(st) && !w.getBoolean("federal");
            if (y + 18 >= LIST_Y && y + 31 <= LIST_Y + LIST_H) {
                String other = w.getString("other");
                CompoundTag a = new CompoundTag();
                a.putString("clan", other);
                Component cf = Component.translatable(w.getBoolean("ceasefireReceived") ? "clan.pioneers.ui.diplo.ceasefire_accept"
                      : w.getBoolean("ceasefireOffered") ? "clan.pioneers.ui.diplo.ceasefire_sent" : "clan.pioneers.ui.diplo.ceasefire");
                u.button(X + W - 196, y + 18, 120, 13, cf, act && !w.getBoolean("ceasefireOffered"), () -> ClanUiClient.act("war_ceasefire", "diplomacy", a));
                u.confirm("surrender:" + other, X + W - 74, y + 18, 72, 13, null, Component.translatable("clan.pioneers.ui.diplo.surrender"),
                      act, () -> ClanUiClient.act("war_surrender", "diplomacy", a));
            }
        }
        //sieges
        y += 2;
        u.icon12(Ui.gui("ico_siege"), X, y - 2);
        u.fit(Component.translatable("clan.pioneers.ui.diplo.sieges"), X + Ui.GAP, y + 1, W - Ui.GAP, Ui.INK, Ui.SMALL);
        y += 12;
        if (sieges.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.diplo.no_siege"), X, y, W, Ui.DIM, Ui.SMALL);
            y += 10;
        }
        for (int i = 0; i < sieges.size(); i++, y += 10) {
            CompoundTag s = sieges.getCompound(i);
            int p = s.getInt("progress");
            Component where = Component.translatable("clan.pioneers.ui.map.coords", s.getInt("x"), s.getInt("z"));
            Component line = s.getBoolean("defending") ? Component.translatable("clan.pioneers.ui.diplo.siege_def", where)
                  : Component.translatable("clan.pioneers.ui.diplo.siege_att", s.getString("clan"), where);
            //1.9.1 : l'avancement (« hors de vue » est plus long que 40 px) est mesure d'abord
            int pw = u.right(p >= 0 ? Component.literal(p + " %") : Component.translatable("clan.pioneers.ui.diplo.unloaded"), X + W, y, 60, Ui.TEXT,
                  Ui.SMALL);
            u.fit(Component.literal(s.getBoolean("defending") ? "− " : "+ ").append(line), X, y, W - pw - 4, s.getBoolean("defending") ? Ui.MINUS
                  : Ui.PLUS, Ui.SMALL);
        }
        //traites
        y += 4;
        u.icon12(Ui.gui("ico_treaty"), X, y - 2);
        u.fit(Component.translatable("clan.pioneers.ui.diplo.treaties"), X + Ui.GAP, y + 1, W - Ui.GAP, Ui.INK, Ui.SMALL);
        y += 12;
        if (treaties.isEmpty()) {
            u.wrap(Component.translatable("clan.pioneers.ui.diplo.no_treaty"), X, y, W, Ui.DIM, Ui.SMALL, 2);
        }
        for (int i = 0; i < treaties.size(); i++) {
            CompoundTag c = treaties.getCompound(i);
            String name = c.getString("clan");
            int x = X;
            if (!c.getString("tag").isBlank()) {
                Component tag = Component.literal("[" + c.getString("tag") + "] ");
                u.text(tag, x, y, Ui.dyeInk(c.getInt("color")), 1F);
                x += u.width(tag, 1F);
            }
            u.fit(Component.literal(name), x, y, X + W - 80 - x, Ui.INK, 1F);
            u.right(Component.translatable("clan.pioneers.ui.relation." + c.getString("relation")), X + W, y + 1, 76, Ui.DIM, Ui.SMALL);
            y += 12;
            ListTag kinds = c.getList("treaties", Tag.TAG_COMPOUND);
            for (int k = 0; k < kinds.size(); k++, y += 15) {
                CompoundTag x2 = kinds.getCompound(k);
                String kind = x2.getString("kind");
                long end = x2.getLong("end");
                MutableComponent l = Component.literal("= ").append(Component.translatable("clan.pioneers.treaty.kind." + kind));
                if (x2.getBoolean("breaking")) {
                    l.append(" · ").append(Component.translatable("clan.pioneers.ui.diplo.breaking", Ui.duration(end - now)));
                } else if (end > now) {
                    l.append(" · ").append(Component.translatable("clan.pioneers.ui.diplo.until", Ui.duration(end - now)));
                }
                u.fit(l, X + 8, y + 3, W - 90, Ui.TEXT, Ui.SMALL);
                if (y >= LIST_Y && y + 13 <= LIST_Y + LIST_H) {
                    u.confirm("break:" + name + ":" + kind, X + W - 78, y, 78, 13, null, Component.translatable("clan.pioneers.ui.diplo.break"),
                          mayDiplo && !x2.getBoolean("breaking"), () -> treaty("break", name, kind));
                }
            }
            ListTag offers = c.getList("offers", Tag.TAG_COMPOUND);
            for (int k = 0; k < offers.size(); k++, y += 15) {
                CompoundTag o = offers.getCompound(k);
                String kind = o.getString("kind");
                boolean received = o.getBoolean("received");
                Component l = Component.translatable(received ? "clan.pioneers.ui.diplo.offer_in" : "clan.pioneers.ui.diplo.offer_out",
                      Component.translatable("clan.pioneers.treaty.short." + kind), Ui.duration(o.getLong("expires") - now));
                u.fit(l, X + 8, y + 3, received ? W - 114 : W - 8, Ui.TEXT, Ui.SMALL);
                if (received && y >= LIST_Y && y + 13 <= LIST_Y + LIST_H) {
                    u.button(X + W - 102, y, 50, 13, Component.translatable("clan.pioneers.ui.diplo.sign"), mayDiplo, () -> treaty("accept", name, kind));
                    u.button(X + W - 50, y, 50, 13, Component.translatable("clan.pioneers.ui.decline"), mayDiplo, () -> treaty("refuse", name, kind));
                }
            }
        }
        //traites federaux : herites de la faction, ou signes par une faction avec le clan (ils se decident au niveau federal)
        if (!federal.isEmpty()) {
            y += 4;
            u.fit(Component.translatable("clan.pioneers.ui.diplo.federal"), X, y, W, Ui.INK, Ui.SMALL);
            u.tip(X, y, W, 8, Component.translatable("clan.pioneers.ui.diplo.federal_tip"));
            y += 10;
            for (int i = 0; i < federal.size(); i++) {
                CompoundTag c = federal.getCompound(i);
                ListTag kinds = c.getList("treaties", Tag.TAG_COMPOUND);
                for (int k = 0; k < kinds.size(); k++, y += 9) {
                    CompoundTag x2 = kinds.getCompound(k);
                    long end = x2.getLong("end");
                    MutableComponent l = Component.literal("= ").append(Component.translatable("clan.pioneers.treaty.short." + x2.getString("kind")));
                    l.append(" · ").append(Component.translatable("clan.pioneers.ui.diplo.with", c.getString("clan")));
                    if (x2.getBoolean("breaking")) {
                        l.append(" · ").append(Component.translatable("clan.pioneers.ui.diplo.breaking", Ui.duration(end - now)));
                    } else if (end > now) {
                        l.append(" · ").append(Component.translatable("clan.pioneers.ui.diplo.until", Ui.duration(end - now)));
                    }
                    u.fit(l, X + 8, y, W - 50, Ui.TEXT, Ui.SMALL);
                    u.right(Component.translatable("clan.pioneers.ui.diplo.federal_mark"), X + W, y, 38, Ui.GOLD, Ui.SMALL);
                }
            }
        }
        u.s.endClip(u.g);
    }

    static void treaty(String verb, String clan, String kind) {
        CompoundTag a = new CompoundTag();
        a.putString("verb", verb);
        a.putString("clan", clan);
        a.putString("kind", kind);
        ClanUiClient.act("treaty", "diplomacy", a);
    }

    // ------------------------------------------------------------------ peuples (reputation du clan)
    static ResourceLocation peopleIcon(String id) {
        return switch (id) {
            case "restorers", "bare_hands", "peddlers" -> Pioneers.rl("textures/gui/village/people_" + id + ".png");
            default -> Ui.gui("member_people");
        };
    }

    static Component peopleName(String id, String name) {
        return name == null || name.isBlank() ? Component.translatable("people.pioneers." + id) : Component.literal(name);
    }

    private static void peoples(Ui u, CompoundTag rep) {
        ListTag gauges = rep.getList("gauges", Tag.TAG_COMPOUND);
        int y = Y + 18;
        int cap = rep.getInt("dailyCap");
        for (int i = 0; i < gauges.size() && y < Y + 120; i++, y += 22) {
            CompoundTag g = gauges.getCompound(i);
            String id = g.getString("people");
            int v = g.getInt("value");
            u.fill(X, y, X + 3, y + 19, 0xFF000000 | g.getInt("color"));
            u.image(peopleIcon(id), X + 6, y + 1, 16, 16, peopleIcon(id).getPath().contains("member_") ? 32 : 16, peopleIcon(id).getPath().contains(
                  "member_") ? 32 : 16);
            u.fit(peopleName(id, g.getString("name")), X + 26, y, 92, Ui.INK, Ui.SMALL);
            String tier = g.getString("tier");
            u.image(Pioneers.rl("textures/gui/village/rep_" + tier + ".png"), X + 26, y + 8, 10, 10, 16, 16);
            u.fit(Component.translatable("reputation.pioneers.tier." + tier), X + 38, y + 10, 80, Ui.TEXT, Ui.SMALL);
            int gx = X + 122, gw = 100, gy = y + 6;
            u.fill(gx, gy, gx + gw, gy + 5, 0x30243660);
            int mid = gx + gw / 2;
            int end = mid + (int) Math.round(gw / 2.0 * Math.max(-300, Math.min(300, v)) / 300.0);
            u.fill(Math.min(mid, end), gy, Math.max(mid, end), gy + 5, v >= 0 ? Ui.PLUS : Ui.MINUS);
            u.fill(mid, gy - 1, mid + 1, gy + 6, Ui.INK);
            u.frame(gx, gy, gw, 5, Ui.INK_LIGHT);
            u.fit(Component.literal((v > 0 ? "+" : v < 0 ? "−" : "") + Math.abs(v)), gx + gw + 4, y + 5, 26, v >= 0 ? Ui.PLUS : Ui.MINUS, Ui.SMALL);
            u.fit(Component.translatable("clan.pioneers.ui.diplo.today", g.getInt("today"), cap), X + W - 26, y + 5, 26, Ui.DIM, 0.5F);
            if (g.contains("faction")) {
                u.fit(Component.translatable("clan.pioneers.ui.diplo.faction_rep", Component.translatable("reputation.pioneers.tier." + g.getString(
                      "factionTier")), g.getInt("faction")), gx, y + 13, W - 122, Ui.DIM, 0.5F);
            }
            u.tip(X, y, W, 19, Component.translatable("clan.pioneers.ui.diplo.gauge_tip"));
        }
        y = Math.max(y, Y + 18) + 2;
        int peddlers = rep.getInt("peddlers");
        if (peddlers > 0) {
            u.fit(Component.translatable("clan.pioneers.ui.diplo.peddlers", peddlers), X, y, W, Ui.TEXT, Ui.SMALL);
            y += 9;
        }
        u.separator(X, y + 1, W);
        y += 4;
        u.fit(Component.translatable("clan.pioneers.ui.diplo.villages"), X, y, W, Ui.INK, Ui.SMALL);
        y += 10;
        ListTag vl = rep.getList("villages", Tag.TAG_COMPOUND);
        if (vl.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.diplo.no_village"), X, y, W, Ui.DIM, Ui.SMALL);
            return;
        }
        int h = Y + H - y;
        u.s.scrollArea(4, X, y, W, h, vl.size() * 9);
        u.s.beginClip(u.g, X, y, W, h);
        int yy = y - u.s.scrollOf(4);
        for (int i = 0; i < vl.size(); i++, yy += 9) {
            CompoundTag v = vl.getCompound(i);
            String id = v.getString("people");
            u.image(peopleIcon(id), X, yy, 8, 8, peopleIcon(id).getPath().contains("member_") ? 32 : 16, peopleIcon(id).getPath().contains("member_") ? 32
                  : 16);
            u.fit(Component.translatable("clan.pioneers.ui.diplo.village", peopleName(id, ""), v.getInt("x"), v.getInt("z")), X + 11, yy, W - 50, Ui.TEXT,
                  Ui.SMALL);
            int e = v.getInt("esteem");
            u.right(Component.literal((e > 0 ? "+" : e < 0 ? "−" : "") + Math.abs(e)), X + W, yy, 35, e >= 0 ? Ui.PLUS : Ui.MINUS, Ui.SMALL);
        }
        u.s.endClip(u.g);
    }
}
