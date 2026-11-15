package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanTab.X;

import fr.pioneers.clan.Permission;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.0.4-beta (lot 8, Guillaume : « ajouter plus de roles, il manque les perms semi admin, notamment le budget et le territoire ») : vue
 * « Roles » de la fiche Membres. 1.0.5-beta (lot 20, retour 10 : « afficher clairement les roles du clan et leurs permissions ») : la
 * matrice a 26 lignes x 9 colonnes est remplacee par deux colonnes simples : a gauche les ROLES (insigne a la couleur du role, nom, nombre de
 * membres qui l'ont), a droite le role choisi : qui l'a (noms de personnage), ses droits « x / y », puis ses PERMISSIONS rangees par famille
 * (case + icone + libelle court, le nom entier, la description et la delegation en infobulle). Une case se coche ou se decoche d'un clic
 * quand on peut modifier les roles (Representant, ou permission « Modifier les roles »), seulement pour un role de rang INFERIEUR au sien et,
 * hors Representant, pour une permission qu'on a soi-meme (le serveur revoit tout : {@code ClanRoles.check} ; meme action
 * {@code role_edit} op {@code perm} qu'avant). Le Representant a tout (cases pleines, figees). Marque « R » : permission que le regime
 * donne au Representant, DELEGUEE quand on la coche. Maquette fidele : {@code T:rp/clans_ui_preview.py} (memes constantes ; aussi lues par
 * {@code T:rp/lot8_ui_preview.py}).
 */
final class RolesView {

    /** Rangee de role (HEAD_H), liste des roles, colonne de droite, rangees de permissions (ROW_H), zone qui defile (ROWS_H), case. */
    static final int LIST_W = 92, HEAD_H = 19, ROLES_Y = 41, ROLES_H = 190, RX = 192, RW = 184, PERMS_Y = 81, ROW_H = 12, ROWS_H = 144, BOX = 9,
          SB = 3, SCROLL = 3, LIST_SCROLL = 6;
    static final String[] FAMILIES = {"members", "territory", "treasury", "government", "communication"};
    @Nullable
    private static String role;

    private RolesView() {
    }

    static String family(String perm) {
        for (Permission p : Permission.values()) {
            if (p.id().equals(perm)) {
                return p.family;
            }
        }
        return "";
    }

    /** Toutes les permissions, famille par famille (ordre de {@link #FAMILIES}), dans l'ordre de l'enum. */
    static List<String> ordered() {
        List<String> out = new ArrayList<>();
        for (String f : FAMILIES) {
            for (Permission p : Permission.values()) {
                if (p.family.equals(f)) {
                    out.add(p.id());
                }
            }
        }
        return out;
    }

    /** Rangees de la colonne des permissions : « #famille » puis ses permissions deux par deux. */
    static List<String[]> rows() {
        List<String[]> out = new ArrayList<>();
        for (String f : FAMILIES) {
            out.add(new String[]{"#" + f});
            List<String> ps = new ArrayList<>();
            for (Permission p : Permission.values()) {
                if (p.family.equals(f)) {
                    ps.add(p.id());
                }
            }
            for (int i = 0; i < ps.size(); i += 2) {
                out.add(i + 1 < ps.size() ? new String[]{ps.get(i), ps.get(i + 1)} : new String[]{ps.get(i)});
            }
        }
        return out;
    }

    static boolean has(CompoundTag r, String perm) {
        if ("representative".equals(r.getString("id"))) {
            return true;
        }
        ListTag ps = r.getList("perms", Tag.TAG_STRING);
        for (int i = 0; i < ps.size(); i++) {
            if (perm.equals(ps.getString(i))) {
                return true;
            }
        }
        return false;
    }

    /** Noms de personnage des membres qui ont ce role (jamais un compte). */
    static List<String> holders(CompoundTag t, String id) {
        List<String> out = new ArrayList<>();
        ListTag l = t.getList("list", Tag.TAG_COMPOUND);
        for (int i = 0; i < l.size(); i++) {
            if (id.equals(l.getCompound(i).getString("role"))) {
                out.add(l.getCompound(i).getString("char"));
            }
        }
        return out;
    }

    static void draw(Ui u, CompoundTag t) {
        ListTag roles = t.getList("roles", Tag.TAG_COMPOUND);
        List<String> reserved = Ui.strings(t.getList("reserved", Tag.TAG_STRING));
        int myRank = t.getInt("myRank");
        boolean rep = ClanTab.isRep(), editor = rep || ClanTab.may("edit_roles");
        int n = roles.size();
        if (role == null || !contains(roles, role)) {
            role = n > 1 ? roles.getCompound(1).getString("id") : n == 1 ? roles.getCompound(0).getString("id") : null;
        }
        //roles, du plus haut au plus bas
        u.s.scrollArea(LIST_SCROLL, X, ROLES_Y, LIST_W, ROLES_H, n * HEAD_H);
        u.s.beginClip(u.g, X, ROLES_Y, LIST_W, ROLES_H);
        int y = ROLES_Y - u.s.scrollOf(LIST_SCROLL);
        CompoundTag chosen = null;
        for (int i = 0; i < n; i++, y += HEAD_H) {
            CompoundTag r = roles.getCompound(i);
            String id = r.getString("id");
            boolean sel = id.equals(role);
            if (sel) {
                chosen = r;
            }
            if (y + HEAD_H <= ROLES_Y || y >= ROLES_Y + ROLES_H) {
                continue;
            }
            boolean over = u.over(X, Math.max(y, ROLES_Y), LIST_W, HEAD_H - 1) && u.over(X, ROLES_Y, LIST_W, ROLES_H);
            u.fill(X, y, X + LIST_W, y + HEAD_H - 1, sel ? 0x40243660 : over ? 0x18243660 : 0x0A243660);
            if (sel) {
                u.fill(X, y, X + 2, y + HEAD_H - 1, Ui.INK);
            }
            String ins = SheetFiche.insignia(id, r.getInt("rank"));
            u.tinted(Ui.gui("rank_" + ins), X + 3, y + 1, 16, 16, 16, 16, Ui.tint(r.getInt("color")), 1F);
            Component nm = Ui.txt(r.getString("name"));
            Component count = Component.translatable("clan.pioneers.ui.roles.holders", holders(t, id).size());
            if (u.width(nm, 1F) * 0.75F <= LIST_W - 23) {
                u.fit(nm, X + 21, y + 1, LIST_W - 23, Ui.dyeInk(r.getInt("color")), 1F);
                u.fit(count, X + 21, y + 11, LIST_W - 23, Ui.DIM, Ui.SMALL);
            } else {
                //nom long (jusqu'a 24 signes) : replie en petit sur deux lignes, le nombre de membres en infobulle
                u.wrap(nm, X + 21, y + 1, LIST_W - 23, Ui.dyeInk(r.getInt("color")), Ui.SMALL, 2);
                if (u.over(X, ROLES_Y, LIST_W, ROLES_H)) {
                    u.tip(X, y, LIST_W, HEAD_H - 1, nm, count);
                }
            }
            int cy = Math.max(y, ROLES_Y), ch = Math.min(y + HEAD_H, ROLES_Y + ROLES_H) - cy;
            if (ch > 0) {
                u.click(X, cy, LIST_W, ch, () -> role = id);
            }
        }
        u.s.endClip(u.g);
        if (chosen != null) {
            panel(u, t, chosen, reserved, editor, rep, myRank);
        }
    }

    /** Case 9 x 9 : cochee = aplat d'encre et coche de papier ; non modifiable = encre claire. */
    static void box(Ui u, int x, int y, boolean on, boolean enabled, boolean hover) {
        int ink = enabled ? Ui.INK : Ui.INK_LIGHT;
        u.fill(x, y, x + BOX, y + BOX, on ? ink : hover ? 0x40243660 : 0x14243660);
        u.frame(x, y, BOX, BOX, on ? ink : enabled ? Ui.INK_LIGHT : Ui.FAINT);
        if (on) {
            int[][] pts = {{2, 4}, {3, 5}, {4, 6}, {5, 5}, {6, 4}, {7, 3}, {3, 4}, {4, 5}};
            for (int[] p : pts) {
                u.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, Ui.PAPER);
            }
        }
    }

    private static void panel(Ui u, CompoundTag t, CompoundTag r, List<String> reserved, boolean editor, boolean rep, int myRank) {
        String id = r.getString("id");
        //en-tete : insigne, nom ; rang, plafond de retrait et description en infobulle
        String ins = SheetFiche.insignia(id, r.getInt("rank"));
        u.tinted(Ui.gui("rank_" + ins), RX, ROLES_Y, 16, 16, 16, 16, Ui.tint(r.getInt("color")), 1F);
        u.fit(Ui.txt(r.getString("name")), RX + 20, ROLES_Y + 1, RW - 20, Ui.dyeInk(r.getInt("color")), 1F);
        long cap = r.getLong("cap");
        Component capText = cap < 0 ? Component.translatable("clan.pioneers.ui.roles.cap_none") : Component.translatable("clan.pioneers.ui.roles.cap",
              Ui.group(cap));
        Component desc = isDefault(id) && r.getString("name").startsWith("#") ? Component.translatable("clan.pioneers.role." + id + ".desc")
              : Component.translatable("clan.pioneers.ui.roles.custom");
        u.tip(RX, ROLES_Y, RW, 10, Ui.txt(r.getString("name")), Component.translatable("clan.pioneers.ui.roles.rank_tip", r.getInt("rank"), capText), desc);
        List<String> who = holders(t, id);
        Component line = who.isEmpty() ? Component.translatable("clan.pioneers.ui.roles.nobody") : Component.translatable("clan.pioneers.ui.roles.who",
              String.join(", ", who));
        u.wrap(line, RX + 20, ROLES_Y + 11, RW - 20, Ui.TEXT, Ui.SMALL, 2);
        u.separator(RX, PERMS_Y - 12, RW);
        //droits : compteur et ce qu'on peut faire ici
        List<String> all = ordered();
        int count = 0;
        for (String p : all) {
            count += has(r, p) ? 1 : 0;
        }
        boolean editable = editor && !"representative".equals(id) && r.getInt("rank") < myRank;
        Component hint = !editor ? Component.translatable("clan.pioneers.ui.roles.read_only") : !editable ? Component.translatable(
              "clan.pioneers.ui.roles.above") : Component.translatable("clan.pioneers.ui.roles.edit_tip");
        u.fit(Component.translatable("clan.pioneers.ui.roles.rights", count, all.size()), RX, PERMS_Y - 9, RW, Ui.INK, Ui.SMALL);
        u.tip(RX, PERMS_Y - 9, RW, 7, hint);
        //permissions, par famille, deux colonnes
        List<String[]> rows = rows();
        int cw = (RW - SB) / 2;
        u.s.scrollArea(SCROLL, RX, PERMS_Y, RW, ROWS_H, rows.size() * ROW_H);
        u.s.beginClip(u.g, RX, PERMS_Y, RW, ROWS_H);
        int y = PERMS_Y - u.s.scrollOf(SCROLL);
        for (String[] row : rows) {
            boolean visible = y + ROW_H > PERMS_Y && y < PERMS_Y + ROWS_H;
            if (!visible) {
                y += ROW_H;
                continue;
            }
            if (row[0].startsWith("#")) {
                u.fill(RX, y, RX + RW - SB, y + ROW_H - 1, 0x16243660);
                u.fit(Component.translatable("clan.pioneers.perm_family." + row[0].substring(1)), RX + 2, y + 3, RW - SB - 4, Ui.INK, Ui.SMALL);
                y += ROW_H;
                continue;
            }
            for (int c = 0; c < row.length; c++) {
                String perm = row[c];
                int cx = RX + c * cw;
                boolean on = has(r, perm);
                boolean can = editable && (rep || ClanTab.may(perm));
                boolean inside = u.over(RX, PERMS_Y, RW, ROWS_H);
                box(u, cx, y + 1, on, can, can && inside && u.over(cx, y + 1, BOX, BOX));
                u.icon12(Ui.gui("perm_" + perm), cx + 11, y, 0xFFFFFF, on ? 1F : 0.4F);
                boolean res = reserved.contains(perm);
                int lw = cw - 26 - (res ? 7 : 0);
                u.fit(Component.translatable("clan.pioneers.perm_short." + perm), cx + 25, y + 3, lw, on ? Ui.TEXT : Ui.DIM, Ui.SMALL);
                if (res) {
                    u.fit(Component.translatable("clan.pioneers.ui.roles.reserved_mark"), cx + cw - 7, y + 3, 6, Ui.GOLD, Ui.SMALL);
                }
                if (inside && u.over(cx, y, cw, ROW_H)) {
                    if (res) {
                        u.tip(cx, y, cw, ROW_H, Component.translatable("clan.pioneers.perm." + perm), Component.translatable("clan.pioneers.perm." + perm
                                                                                                                            + ".desc"),
                              Component.translatable("clan.pioneers.ui.roles.delegated"));
                    } else {
                        u.tip(cx, y, cw, ROW_H, Component.translatable("clan.pioneers.perm." + perm), Component.translatable("clan.pioneers.perm." + perm
                                                                                                                            + ".desc"));
                    }
                }
                if (can && y >= PERMS_Y && y + ROW_H <= PERMS_Y + ROWS_H) {
                    u.click(cx, y, cw, ROW_H, () -> {
                        CompoundTag d = new CompoundTag();
                        d.putString("op", "perm");
                        d.putString("role", id);
                        d.putString("perm", perm);
                        d.putBoolean("on", !on);
                        CompoundTag a = new CompoundTag();
                        a.put("data", d);
                        a.putString("text", "");
                        ClanUiClient.act("role_edit", "members", a);
                    });
                }
            }
            y += ROW_H;
        }
        u.s.endClip(u.g);
    }

    private static boolean isDefault(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "representative", "lieutenant", "steward", "surveyor", "guard", "recruiter", "artisan", "member", "recruit" -> true;
            default -> false;
        };
    }

    private static boolean contains(ListTag roles, String id) {
        for (int i = 0; i < roles.size(); i++) {
            if (roles.getCompound(i).getString("id").equals(id)) {
                return true;
            }
        }
        return false;
    }
}
