package fr.pioneers.society.clan.client.ui;

import static fr.pioneers.society.clan.client.ui.ClanTab.W;
import static fr.pioneers.society.clan.client.ui.ClanTab.X;
import static fr.pioneers.society.clan.client.ui.ClanTab.Y;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Membres : personnages du clan (le pseudo du compte seulement avec la permission de voir les identites,
 * et jamais hors du clan), insigne et role, presence ; sur un membre choisi : changer de role, exclure (confirmation), designer l'heritier
 * (Empire), verser une prime ; inviter un joueur connecte (designe par son personnage). 1.0.5-beta (lot 20, retour 10) : rangee = insigne
 * a la couleur du role, nom, pastille de presence + un mot ; role a droite (rang en infobulle), Heritier / Conseil en icones ; le compte
 * (avec la permission seulement) passe en infobulle du nom ; actions a icone, une raison en infobulle quand elles sont grisees, Exclure et
 * la prime en deux clics ; vues Membres / Roles en pastilles a icone.
 */
final class MembersFiche extends ClanTab.Fiche {

    static final int LIST_Y = Y + 15, LIST_H = 120, ROW = 20, PANEL_Y = Y + 138, VIEW_W = 58, ACT_W = 68;
    private enum Mode {
        LIST, ROLES, BONUS, RECRUITS
    }

    private static Mode mode = Mode.LIST;
    /** 1.0.4-beta (lot 8) : vue « Roles » ({@link RolesView}) au lieu de la liste. */
    private static boolean rolesView;
    @Nullable
    private static String selected;

    @Override
    String id() {
        return "members";
    }

    @Override
    void enter() {
        ClanUiClient.request("members");
        mode = Mode.LIST;
        selected = null;
        rolesView = false;
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("members");
        if (waiting(u, t)) {
            return;
        }
        CompoundTag sheet = ClanUiClient.sheet("sheet");
        ListTag list = t.getList("list", Tag.TAG_COMPOUND);
        int max = sheet == null ? 0 : sheet.getInt("max");
        boolean solo = ClanTab.status() == ClanTab.Status.SOLO;
        int vx = X + W - (solo ? 0 : 2 * VIEW_W + 2);
        //titre et oeil des identites (la regle des comptes en infobulle)
        int cw = u.fit(Component.translatable("clan.pioneers.ui.members.title_short", list.size(), max), X, Y + 1, vx - X - 22, Ui.INK, 1F);
        boolean ids = t.getBoolean("identities");
        u.icon12(Ui.gui("perm_view_identities"), X + cw + 4, Y - 1, 0xFFFFFF, ids ? 1F : 0.3F);
        u.tip(X + cw + 4, Y - 1, 12, 12, Component.translatable("clan.pioneers.perm.view_identities"), Component.translatable(ids
              ? "clan.pioneers.ui.members.ids_tip_on" : "clan.pioneers.ui.members.ids_tip_off"));
        //1.0.4-beta (lot 8) : deux vues, « Membres » et « Roles » (pas en Solitaire : un seul membre, pas de role a regler)
        if (!solo) {
            u.navChip(vx, Y - 1, VIEW_W, 13, Ui.gui("nav_members"), Component.translatable("clan.pioneers.ui.roles.view_members"), !rolesView, 0,
                  () -> rolesView = false);
            u.navChip(vx + VIEW_W + 2, Y - 1, VIEW_W, 13, Ui.gui("perm_edit_roles"), Component.translatable("clan.pioneers.ui.roles.view_roles"),
                  rolesView, 0, () -> {
                      rolesView = true;
                      mode = Mode.LIST;
                  });
        } else {
            rolesView = false;
        }
        u.separator(X, Y + 12, W);
        if (rolesView) {
            RolesView.draw(u, t);
            return;
        }
        if (mode == Mode.RECRUITS) {
            recruits(u);
        } else if (mode == Mode.ROLES && chosen(list) != null) {
            rolePick(u, t, chosen(list));
        } else {
            list(u, list);
        }
        u.separator(X, PANEL_Y - 3, W);
        panel(u, t, list);
    }

    @Nullable
    private static CompoundTag chosen(ListTag list) {
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getString("token").equals(selected)) {
                return list.getCompound(i);
            }
        }
        return null;
    }

    /**
     * 1.0.5-beta (lot 20) : choix du nouveau role d'un membre, a la place de la liste : roles de rang inferieur au sien (sauf Representant),
     * insigne + nom, deux colonnes (un nom de role va jusqu'a 24 signes).
     */
    private static void rolePick(Ui u, CompoundTag t, CompoundTag m) {
        String token = m.getString("token");
        int myRank = t.getInt("myRank");
        u.icon12(Ui.gui("perm_promote"), X, LIST_Y);
        u.fit(Component.translatable("clan.pioneers.ui.members.role_for", m.getString("char")), X + Ui.GAP, LIST_Y + 2, W - Ui.GAP, Ui.INK, 1F);
        ListTag roles = t.getList("roles", Tag.TAG_COMPOUND);
        int cw = (W - 2) / 2, k = 0;
        for (int i = 0; i < roles.size() && k < 14; i++) {
            CompoundTag r = roles.getCompound(i);
            if (r.getInt("rank") >= myRank || "representative".equals(r.getString("id"))) {
                continue;
            }
            String role = r.getString("id");
            boolean current = role.equals(m.getString("role"));
            int cx = X + (k % 2) * (cw + 2), cy = LIST_Y + 16 + (k / 2) * 14;
            u.navChip(cx, cy, cw, 13, Ui.gui("rank_" + SheetFiche.insignia(role, r.getInt("rank"))), Ui.txt(r.getString("name")), current, 0, () -> {
                if (!current) {
                    CompoundTag a = new CompoundTag();
                    a.putString("member", token);
                    a.putString("role", role);
                    ClanUiClient.act("assign", "members", a);
                    mode = Mode.LIST;
                }
            });
            k++;
        }
    }

    /** Presence : « En ligne », « Il y a 2 j » ou « Absent 9 j » (inactif). */
    static Component presence(CompoundTag m, long now) {
        if (m.getBoolean("online")) {
            return Component.translatable("clan.pioneers.ui.members.online_short");
        }
        return Component.translatable(m.getBoolean("active") ? "clan.pioneers.ui.members.seen_short" : "clan.pioneers.ui.members.away_short",
              Ui.duration(now - m.getLong("seen")));
    }

    private static void list(Ui u, ListTag list) {
        long now = ClanUiClient.now();
        boolean solo = ClanTab.status() == ClanTab.Status.SOLO;
        u.s.scrollArea(2, X, LIST_Y, W, LIST_H, list.size() * ROW);
        u.s.beginClip(u.g, X, LIST_Y, W, LIST_H);
        int y = LIST_Y - u.s.scrollOf(2);
        boolean inside = u.over(X, LIST_Y, W, LIST_H);
        for (int i = 0; i < list.size(); i++, y += ROW) {
            if (y + ROW <= LIST_Y || y >= LIST_Y + LIST_H) {
                continue;
            }
            CompoundTag m = list.getCompound(i);
            String token = m.getString("token");
            boolean sel = token.equals(selected);
            if (sel) {
                u.fill(X, y, X + W, y + ROW - 1, 0x30243660);
            } else if (inside && u.over(X, y, W, ROW - 1)) {
                u.fill(X, y, X + W, y + ROW - 1, 0x14243660);
            }
            String insignia = solo ? "solo" : SheetFiche.insignia(m.getString("role"), m.getInt("rank"));
            u.tinted(Ui.gui("rank_" + insignia), X + 2, y + 2, 16, 16, 16, 16, solo ? 0xFFFFFF : Ui.tint(m.getInt("roleColor")), 1F);
            Component name = Component.literal(m.getString("char"));
            if (m.getBoolean("me")) {
                name = Component.translatable("clan.pioneers.ui.members.me", name);
            }
            int nw = u.fit(name, X + 22, y + 2, 128, Ui.INK, 1F);
            if (m.contains("account") && inside) {
                u.tip(X + 22, y + 2, nw, 8, name, Component.translatable("clan.pioneers.ui.members.account_tip", m.getString("account")));
            }
            boolean online = m.getBoolean("online");
            u.dot(online, X + 22, y + 13);
            u.fit(presence(m, now), X + 30, y + 12, 120, online ? Ui.PLUS : m.getBoolean("active") ? Ui.DIM : Ui.FAINT, Ui.SMALL);
            //role a droite, rang en infobulle ; marques en icones
            Component rn = Ui.txt(m.getString("roleName"));
            float rs = u.width(rn, 1F) * 0.75F <= W - 188 ? 1F : Ui.SMALL;
            int rw = u.fit(rn, X + 156, y + (rs < 1F ? 7 : 6), W - 156 - 32, Ui.dyeInk(m.getInt("roleColor")), rs);
            if (inside) {
                u.tip(X + 156, y + 6, rw, 8, Ui.txt(m.getString("roleName")), Component.translatable("clan.pioneers.ui.fact.rank_tip", m.getInt("rank")));
            }
            int mx = X + W - 14;
            if (m.getBoolean("council")) {
                u.icon12(Ui.gui("ico_council"), mx, y + 4);
                if (inside) {
                    u.tip(mx, y + 4, 12, 12, Component.translatable("clan.pioneers.ui.members.council_tip"));
                }
                mx -= 14;
            }
            if (m.getBoolean("heir")) {
                u.icon12(Ui.gui("ico_heir"), mx, y + 4);
                if (inside) {
                    u.tip(mx, y + 4, 12, 12, Component.translatable("clan.pioneers.ui.members.heir_tip"));
                }
            }
            int cy = Math.max(y, LIST_Y), ch = Math.min(y + ROW, LIST_Y + LIST_H) - cy;
            if (ch > 0) {
                u.click(X, cy, W, ch, () -> {
                    selected = token.equals(selected) ? null : token;
                    mode = Mode.LIST;
                });
            }
        }
        u.s.endClip(u.g);
    }

    private static void recruits(Ui u) {
        CompoundTag r = ClanUiClient.sheet("recruits");
        u.icon12(Ui.gui("ico_online"), X, LIST_Y);
        u.fit(Component.translatable("clan.pioneers.ui.members.recruits_short"), X + Ui.GAP, LIST_Y + 2, W - 126 - Ui.GAP, Ui.INK, 1F);
        u.tip(X, LIST_Y, W - 126, 12, Component.translatable("clan.pioneers.ui.members.recruits_short"), Component.translatable(
              "clan.pioneers.ui.members.recruits_tip"));
        if (r == null) {
            u.fit(Component.translatable("clan.pioneers.ui.loading"), X, LIST_Y + 16, W, Ui.DIM, Ui.SMALL);
            return;
        }
        ListTag all = r.getList("list", Tag.TAG_COMPOUND);
        if (all.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.members.no_recruit"), X, LIST_Y + 16, W, Ui.DIM, Ui.SMALL);
            return;
        }
        //1.0.5-beta (lot 19, retour 10 : « un selecteur de joueur au lieu de taper les pseudos ») : la liste des personnages connectes se FILTRE
        //(champ en haut a droite, comme le selecteur de joueur) et se trie : les plus proches d'abord, ailleurs a la fin. Les personnages
        //restent designes par leur nom de pionnier (le compte ne sort pas du clan : permission « Voir les identites »)
        EditBox find = ClanTab.textBox(u, "recruits", X + W - 120, LIST_Y - 1, 120, Component.translatable("clan.pioneers.ui.members.recruit_filter"),
              32);
        String q = find.getValue().trim().toLowerCase(Locale.ROOT);
        List<CompoundTag> l = new ArrayList<>();
        for (int i = 0; i < all.size(); i++) {
            CompoundTag e = all.getCompound(i);
            if (q.isEmpty() || e.getString("char").toLowerCase(Locale.ROOT).contains(q)) {
                l.add(e);
            }
        }
        l.sort(Comparator.comparingInt((CompoundTag e) -> e.getInt("dist") < 0 ? Integer.MAX_VALUE : e.getInt("dist"))
              .thenComparing(e -> e.getString("char").toLowerCase(Locale.ROOT)));
        int top = LIST_Y + 16, h = (LIST_Y + LIST_H - top) / 15 * 15;
        if (l.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.members.recruit_none"), X, top, W, Ui.DIM, Ui.SMALL);
            return;
        }
        u.s.scrollArea(2, X, top, W, h, l.size() * 15);
        u.s.beginClip(u.g, X, top, W, h);
        int y = top - u.s.scrollOf(2);
        for (int i = 0; i < l.size(); i++, y += 15) {
            CompoundTag e = l.get(i);
            String n = e.getString("char");
            u.fit(n.isEmpty() ? Component.translatable("clan.pioneers.ui.members.unnamed") : Component.literal(n), X + 2, y + 3, 130, Ui.INK, 1F);
            int d = e.getInt("dist");
            u.icon12(Ui.gui("ico_place"), X + 136, y + 1);
            u.fit(d < 0 ? Component.translatable("clan.pioneers.ui.members.elsewhere_short") : Component.translatable("clan.pioneers.ui.members.dist_short",
                  d), X + 150, y + 4, W - 150 - 84, Ui.DIM, Ui.SMALL);
            boolean busy = e.getBoolean("busy");
            CompoundTag a = new CompoundTag();
            a.putString("token", e.getString("token"));
            if (y >= top && y + 13 <= top + h) {
                u.iconButton(X + W - 80, y, 78, 13, Ui.gui("ico_invite"), Component.translatable(busy ? "clan.pioneers.ui.members.busy"
                      : "clan.pioneers.ui.members.invite_one"), !busy, () -> ClanUiClient.act("invite_char", "members", a));
            }
        }
        u.s.endClip(u.g);
    }

    private static void panel(Ui u, CompoundTag t, ListTag list) {
        if (mode == Mode.RECRUITS) {
            u.icon12(Ui.gui("perm_view_identities"), X, PANEL_Y + 1);
            u.wrap(Component.translatable("clan.pioneers.ui.members.recruits_hint"), X + Ui.GAP, PANEL_Y, W - 84 - Ui.GAP, Ui.DIM, Ui.SMALL, 3);
            u.button(X + W - 78, PANEL_Y, 78, 14, Component.translatable("clan.pioneers.ui.back"), true, () -> mode = Mode.LIST);
            return;
        }
        CompoundTag m = null;
        for (int i = 0; i < list.size(); i++) {
            if (list.getCompound(i).getString("token").equals(selected)) {
                m = list.getCompound(i);
            }
        }
        if (m == null || m.getBoolean("me")) {
            u.fit(Component.translatable("clan.pioneers.ui.members.pick_short"), X, PANEL_Y + 3, W - 106, Ui.DIM, Ui.SMALL);
            u.iconButton(X + W - 100, PANEL_Y, 100, 14, Ui.gui("ico_invite"), Component.translatable("clan.pioneers.ui.members.invite_short"),
                  ClanTab.may("invite"), () -> {
                      mode = Mode.RECRUITS;
                      u.s.scrollTo(2, 0);
                      ClanUiClient.request("recruits");
                  }, Component.translatable("clan.pioneers.ui.members.need_perm", Component.translatable("clan.pioneers.perm.invite")));
            return;
        }
        String token = m.getString("token");
        int myRank = t.getInt("myRank");
        boolean higher = myRank > m.getInt("rank");
        //le membre choisi : insigne, nom, role
        String ins = SheetFiche.insignia(m.getString("role"), m.getInt("rank"));
        u.icon12(Ui.gui("rank_" + ins), X, PANEL_Y - 1, Ui.tint(m.getInt("roleColor")), 1F);
        u.fit(Component.translatable("clan.pioneers.ui.members.chosen", m.getString("char"), Ui.txt(m.getString("roleName"))), X + Ui.GAP, PANEL_Y, W - Ui.GAP,
              Ui.INK, 1F);
        int by = PANEL_Y + 12;
        CompoundTag sheet = ClanUiClient.sheet("sheet");
        boolean empire = sheet != null && "empire".equals(sheet.getString("regime"));
        Component rank = Component.translatable("clan.pioneers.ui.members.need_rank");
        //changer de role
        boolean promote = ClanTab.may("promote");
        u.iconButton(X, by, ACT_W, 14, Ui.gui("perm_promote"), Component.translatable("clan.pioneers.ui.members.role_short"), promote && higher,
              () -> mode = mode == Mode.ROLES ? Mode.LIST : Mode.ROLES, promote ? rank : Component.translatable("clan.pioneers.ui.members.need_perm",
                    Component.translatable("clan.pioneers.perm.promote")));
        //exclure : deux clics
        boolean kick = ClanTab.may("kick");
        u.confirm("kick:" + token, X + ACT_W + 3, by, ACT_W, 14, Ui.gui("perm_kick"), Component.translatable("clan.pioneers.ui.members.kick_short"),
              kick && higher, () -> {
                  CompoundTag a = new CompoundTag();
                  a.putString("member", token);
                  a.putString("text", "");
                  ClanUiClient.act("kick", "members", a);
              }, kick ? rank : Component.translatable("clan.pioneers.ui.members.need_perm", Component.translatable("clan.pioneers.perm.kick")));
        //prime
        boolean bonuses = ClanTab.may("bonuses");
        u.iconButton(X + 2 * (ACT_W + 3), by, ACT_W, 14, Ui.gui("perm_bonuses"), Component.translatable("clan.pioneers.ui.members.bonus_short"), bonuses,
              () -> mode = mode == Mode.BONUS ? Mode.LIST : Mode.BONUS, Component.translatable("clan.pioneers.ui.members.need_perm",
                    Component.translatable("clan.pioneers.perm.bonuses")));
        //heritier (Empire seulement)
        if (empire) {
            boolean heir = ClanTab.isRep() && !m.getBoolean("heir");
            u.iconButton(X + 3 * (ACT_W + 3), by, W - 3 * (ACT_W + 3), 14, Ui.gui("ico_heir"), Component.translatable("clan.pioneers.ui.members.heir_short"),
                  heir, () -> {
                      CompoundTag a = new CompoundTag();
                      a.putString("member", token);
                      ClanUiClient.act("heir", "members", a);
                  }, Component.translatable("clan.pioneers.ui.members.need_rep"));
        }
        int py = by + 16;
        if (mode == Mode.BONUS) {
            EditBox amount = ClanTab.numberBox(u, "bonus:" + token, X, py, 80, Component.translatable("clan.pioneers.ui.amount"));
            long n = ClanTab.parse(amount);
            u.confirm("bonus:" + token, X + 86, py, 120, 14, Ui.gui("ico_coin"), Component.translatable("clan.pioneers.ui.members.pay_bonus"), n > 0,
                  () -> {
                      CompoundTag a = new CompoundTag();
                      a.putString("member", token);
                      a.putLong("amount", n);
                      ClanUiClient.act("bonus", "members", a);
                      mode = Mode.LIST;
                  });
        }
    }
}
