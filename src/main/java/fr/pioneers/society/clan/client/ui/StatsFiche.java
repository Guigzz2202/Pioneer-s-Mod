package fr.pioneers.society.clan.client.ui;

import static fr.pioneers.society.clan.client.ui.ClanTab.H;
import static fr.pioneers.society.clan.client.ui.ClanTab.W;
import static fr.pioneers.society.clan.client.ui.ClanTab.X;
import static fr.pioneers.society.clan.client.ui.ClanTab.Y;

import fr.pioneers.society.rp.client.RpGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Statistiques. Courbes de production (jetons-equivalent par jour, 30 jours) et de potentiel de frappe
 * (membres presents), DESSINEES PAR LE JEU (aucune texture) ; detail (combats, contributions, production par savoir-faire, statistiques
 * personnelles) ; membres (production sur 7 jours, potentiel) ; stock des Entrepots. Sans la permission de voir les statistiques, ou sans
 * clan : les statistiques personnelles seulement. 1.0.5-beta (lot 20, retour 10) : vues en pastilles a icone, presence des membres en
 * pastille.
 */
final class StatsFiche extends ClanTab.Fiche {

    private static final String[] VIEWS = {"curves", "detail", "roster", "stock"};
    private static final String[] VIEW_ICONS = {"nav_stats", "ico_level", "nav_members", "ico_crate"};
    private static String view = "curves";

    @Override
    String id() {
        return "stats";
    }

    @Override
    void enter() {
        ClanUiClient.request("stats");
    }

    @Override
    void draw(Ui u) {
        CompoundTag t = ClanUiClient.sheet("stats");
        if (waiting(u, t)) {
            return;
        }
        CompoundTag s = t.getCompound("stats");
        if (!t.getBoolean("clan") || s.isEmpty()) {
            title(u, Component.translatable("clan.pioneers.ui.stats.personal"), Y);
            if (t.getBoolean("clan")) {
                u.fit(Component.translatable("clan.pioneers.ui.stats.no_perm"), X, Y + 14, W, Ui.DIM, Ui.SMALL);
            }
            personal(u, t.getCompound("me"), X, Y + 26, W);
            return;
        }
        //1.0.5-beta (lot 20) : vues en pastilles a icone
        int cw = (W - 9) / 4;
        for (int i = 0; i < VIEWS.length; i++) {
            String v = VIEWS[i];
            u.navChip(X + i * (cw + 3), Y, cw, 13, Ui.gui(VIEW_ICONS[i]), Component.translatable("clan.pioneers.ui.stats.view." + v), v.equals(view), 0,
                  () -> view = v);
        }
        switch (view) {
            case "detail" -> detail(u, t, s);
            case "roster" -> roster(u, t);
            case "stock" -> stock(u, s);
            default -> curves(u, s);
        }
    }

    // ------------------------------------------------------------------ courbes
    private static void curves(Ui u, CompoundTag s) {
        long[] prod = s.getLongArray("curve");
        long[] pot = s.getLongArray("potCurve");
        int y = Y + 18;
        u.fit(Component.translatable("clan.pioneers.ui.stats.production"), X, y, 150, Ui.INK, Ui.SMALL);
        Component sum = Component.translatable("clan.pioneers.ui.stats.prod_sum", Ui.group(s.getLong("prod7") / 100), Ui.group(s.getLong("prod30") / 100));
        int sw = Math.min(u.width(sum, Ui.SMALL), W - 154);
        u.fit(sum, X + W - sw, y, sw, Ui.TEXT, Ui.SMALL);
        chart(u, X, y + 9, W, 66, prod, 100.0, Ui.INK_LIGHT);
        y += 94;
        u.fit(Component.translatable("clan.pioneers.ui.stats.potential_curve"), X, y, 150, Ui.INK, Ui.SMALL);
        Component now = Component.translatable("clan.pioneers.ui.stats.pot_now", Ui.group(s.getInt("potential")));
        int nw = Math.min(u.width(now, Ui.SMALL), W - 154);
        u.fit(now, X + W - nw, y, nw, Ui.TEXT, Ui.SMALL);
        chart(u, X, y + 9, W, 66, pot, 1.0, Ui.MINUS);
        y += 94;
        Component rank = s.getInt("ranked") > 0 ? Component.translatable("clan.pioneers.ui.stats.rank", s.getInt("rank"), s.getInt("ranked"))
              : Component.translatable("clan.pioneers.ui.stats.unranked");
        u.fit(rank, X, Math.min(y, Y + H - 7), W, Ui.DIM, Ui.SMALL);
    }

    /**
     * Courbe dessinee par le jeu : cadre, lignes de repere (0, moitie, maximum arrondi), aire sous la courbe, trait d'un pixel, point du
     * dernier jour ; reperes de jours dessous. {@code div} : diviseur d'affichage (centiemes de jeton -&gt; jetons).
     */
    static void chart(Ui u, int x, int y, int w, int h, long[] values, double div, int color) {
        int left = 24, px = x + left, pw = w - left - 1, ph = h - 9, py = y;
        u.fill(px, py, px + pw, py + ph, 0x0C243660);
        u.frame(px, py, pw, ph, Ui.FAINT);
        double max = 0;
        for (long v : values) {
            max = Math.max(max, v / div);
        }
        double top = nice(max);
        for (int k = 0; k <= 2; k++) {
            int gy = py + ph - 1 - (int) Math.round((ph - 1) * k / 2.0);
            if (k > 0 && k < 2) {
                for (int gx = px + 1; gx < px + pw - 1; gx += 3) {
                    u.fill(gx, gy, gx + 1, gy + 1, 0x40243660);
                }
            }
            Component lab = Component.literal(Ui.group(Math.round(top * k / 2.0)));
            u.right(lab, px - 2, gy - 2, left - 2, Ui.DIM, 0.5F);
        }
        int n = values.length;
        if (n >= 2 && top > 0) {
            int[] xs = new int[n], ys = new int[n];
            for (int i = 0; i < n; i++) {
                xs[i] = px + 1 + (int) Math.round((pw - 3) * i / (double) (n - 1));
                ys[i] = py + ph - 2 - (int) Math.round((ph - 3) * Math.min(1, values[i] / div / top));
            }
            int area = (color & 0x00FFFFFF) | 0x30000000;
            for (int i = 0; i < n - 1; i++) {
                for (int cx = xs[i]; cx < xs[i + 1]; cx++) {
                    double f = (cx - xs[i]) / (double) Math.max(1, xs[i + 1] - xs[i]);
                    int cy = (int) Math.round(ys[i] + (ys[i + 1] - ys[i]) * f);
                    u.fill(cx, cy, cx + 1, py + ph - 1, area);
                }
            }
            for (int i = 0; i < n - 1; i++) {
                line(u, xs[i], ys[i], xs[i + 1], ys[i + 1], color);
            }
            u.fill(xs[n - 1] - 1, ys[n - 1] - 1, xs[n - 1] + 2, ys[n - 1] + 2, Ui.INK);
        } else {
            u.center(Component.translatable("clan.pioneers.ui.stats.empty"), px + pw / 2, py + ph / 2 - 3, pw - 4, Ui.DIM, Ui.SMALL);
        }
        int ly = py + ph + 2;
        //1.9.1 : trois reperes, chacun dans son tiers du cadre
        int third = pw / 3;
        u.fit(Component.translatable("clan.pioneers.ui.stats.days_ago", Math.max(0, n - 1)), px, ly, third - 2, Ui.DIM, 0.5F);
        u.center(Component.translatable("clan.pioneers.ui.stats.days_ago", Math.max(0, (n - 1) / 2)), px + pw / 2, ly, third - 2, Ui.DIM, 0.5F);
        u.right(Component.translatable("clan.pioneers.ui.stats.today"), px + pw, ly, third - 2, Ui.DIM, 0.5F);
    }

    /** Trait d'un pixel (Bresenham). */
    private static void line(Ui u, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), dy = -Math.abs(y1 - y0), sx = x0 < x1 ? 1 : -1, sy = y0 < y1 ? 1 : -1, err = dx + dy;
        while (true) {
            u.fill(x0, y0, x0 + 1, y0 + 1, color);
            if (x0 == x1 && y0 == y1) {
                break;
            }
            int e2 = 2 * err;
            if (e2 >= dy) {
                err += dy;
                x0 += sx;
            }
            if (e2 <= dx) {
                err += dx;
                y0 += sy;
            }
        }
    }

    /** Maximum arrondi de l'axe (1, 2 ou 5 x 10^k). */
    static double nice(double max) {
        if (max <= 0) {
            return 0;
        }
        double p = Math.pow(10, Math.floor(Math.log10(max)));
        for (double m : new double[]{1, 2, 5, 10}) {
            if (m * p >= max) {
                return m * p;
            }
        }
        return 10 * p;
    }

    // ------------------------------------------------------------------ detail
    private static void detail(Ui u, CompoundTag t, CompoundTag s) {
        int y = Y + 18, cw = 136;
        u.fit(Component.translatable("clan.pioneers.ui.stats.week"), X, y, cw, Ui.INK, Ui.SMALL);
        y += 9;
        Object[][] rows = {{"hostiles", s.getInt("hostiles7")}, {"sentinels", s.getInt("sentinels7")}, {"enemies", s.getInt("enemies7")},
              {"deaths", s.getInt("deaths7")}, {"contributed", Ui.group(s.getLong("contributed7"))}, {"active", s.getInt("active")},
              {"vocations", s.getInt("vocations")}, {"potential", Ui.group(s.getInt("potential"))}};
        for (Object[] r : rows) {
            u.row(Component.translatable("clan.pioneers.ui.stats." + r[0]), Component.literal(String.valueOf(r[1])), X, y, cw, Ui.TEXT);
            y += 9;
        }
        if (t.contains("treasury")) {
            y += 3;
            u.row(Component.translatable("clan.pioneers.ui.stats.income"), Component.literal("+ " + Ui.group(t.getLong("income7"))), X, y, cw, Ui.PLUS);
            y += 9;
            u.row(Component.translatable("clan.pioneers.ui.stats.spent"), Component.literal("− " + Ui.group(t.getLong("spent7"))), X, y, cw, Ui.MINUS);
            y += 9;
        }
        y += 4;
        u.fit(Component.translatable("clan.pioneers.ui.stats.me"), X, y, cw, Ui.INK, Ui.SMALL);
        personal(u, t.getCompound("me"), X, y + 9, cw);
        //production par savoir-faire (30 jours)
        int rx = X + 146, rw = W - 146;
        u.fit(Component.translatable("clan.pioneers.ui.stats.by_skill"), rx, Y + 18, rw, Ui.INK, Ui.SMALL);
        CompoundTag sk = s.getCompound("skills");
        List<String> keys = new ArrayList<>(sk.getAllKeys());
        keys.sort(Comparator.comparingLong(sk::getLong).reversed());
        long top = keys.isEmpty() ? 1 : Math.max(1, sk.getLong(keys.get(0)));
        int yy = Y + 28;
        for (int i = 0; i < keys.size() && yy <= Y + H - 14; i++, yy += 14) {
            String k = keys.get(i);
            u.image(RpGui.skillIcon(k), rx, yy, 12, 12, 16, 16);
            //1.9.1 : la valeur (12 345 deborde de 29 px) est mesuree d'abord, le nom du savoir-faire prend le reste
            Component val = Component.literal(Ui.group(sk.getLong(k) / 100));
            int vw = u.right(val, rx + rw, yy, 60, Ui.TEXT, Ui.SMALL);
            u.fit(RpGui.skillName(k), rx + 15, yy, rw - 15 - vw - 4, Ui.TEXT, Ui.SMALL);
            u.bar(rx + 15, yy + 7, rw - 15, 3, sk.getLong(k) / (double) top, Ui.INK_LIGHT);
        }
        if (keys.isEmpty()) {
            u.fit(Component.translatable("clan.pioneers.ui.stats.empty"), rx, yy, rw, Ui.DIM, Ui.SMALL);
        }
    }

    /** Statistiques personnelles (7 jours) : production, contribution, combats, potentiel et ses parts. */
    private static void personal(Ui u, CompoundTag me, int x, int y, int w) {
        Object[][] rows = {{"produced", Ui.group(me.getLong("produced") / 100)}, {"contributed", Ui.group(me.getLong("contributed"))},
              {"played", Ui.duration(me.getLong("played") * 1000L).getString()}, {"hostiles", me.getInt("hostiles")},
              {"enemies", me.getInt("enemies")}, {"deaths", me.getInt("deaths")}, {"potential", me.getInt("potential")},
              {"parts", Component.translatable("clan.pioneers.ui.stats.parts_value", me.getInt("combat"), me.getInt("weapon"),
                    String.format(java.util.Locale.ROOT, "%.1f", me.getInt("armor10") / 10.0)).getString()}};
        for (Object[] r : rows) {
            if (y > Y + H - 7) {
                break;
            }
            u.row(Component.translatable("clan.pioneers.ui.stats." + r[0]), Component.literal(String.valueOf(r[1])), x, y, w, Ui.TEXT);
            y += 9;
        }
    }

    // ------------------------------------------------------------------ membres
    private static void roster(Ui u, CompoundTag t) {
        ListTag l = t.getList("roster", Tag.TAG_COMPOUND);
        List<CompoundTag> rows = new ArrayList<>();
        for (int i = 0; i < l.size(); i++) {
            rows.add(l.getCompound(i));
        }
        rows.sort(Comparator.comparingLong((CompoundTag c) -> -c.getLong("prod7")));
        int y = Y + 18;
        int[] cols = {X, X + 108, X + 180, X + 232};
        String[] heads = {"character", "vocation", "prod7", "potential"};
        for (int i = 0; i < heads.length; i++) {
            u.fit(Component.translatable("clan.pioneers.ui.stats.col." + heads[i]), cols[i], y, (i < 3 ? cols[i + 1] : X + W) - cols[i] - 4, Ui.DIM,
                  Ui.SMALL);
        }
        u.separator(X, y + 8, W);
        int top = y + 11, h = Y + H - top;
        u.s.scrollArea(7, X, top, W, h, rows.size() * 11);
        u.s.beginClip(u.g, X, top, W, h);
        int yy = top - u.s.scrollOf(7);
        for (CompoundTag c : rows) {
            //1.0.5-beta (lot 20) : presence en pastille (plus seulement une couleur d'encre)
            u.dot(c.getBoolean("online"), cols[0], yy + 1);
            u.fit(Component.literal(c.getString("character")), cols[0] + 8, yy, cols[1] - cols[0] - 12, c.getBoolean("online") ? Ui.INK : Ui.TEXT, Ui.SMALL);
            String voc = c.getString("vocation");
            u.fit(voc.isEmpty() ? Component.literal("—") : RpGui.vocationName(voc), cols[1], yy, cols[2] - cols[1] - 4, Ui.TEXT, Ui.SMALL);
            u.fit(Component.literal(c.contains("prod7") ? Ui.group(c.getLong("prod7") / 100) : "—"), cols[2], yy, cols[3] - cols[2] - 4, Ui.TEXT, Ui.SMALL);
            u.fit(Component.literal(c.contains("potential") ? Ui.group(c.getInt("potential")) : "—"), cols[3], yy, X + W - cols[3], Ui.TEXT, Ui.SMALL);
            yy += 11;
        }
        u.s.endClip(u.g);
    }

    // ------------------------------------------------------------------ entrepots
    private static void stock(Ui u, CompoundTag s) {
        ListTag l = s.getList("warehouses", Tag.TAG_COMPOUND);
        int y = Y + 18;
        if (l.isEmpty()) {
            u.wrap(Component.translatable("clan.pioneers.ui.stats.no_stock"), X, y, W, Ui.DIM, Ui.SMALL, 3);
            return;
        }
        u.fit(Component.translatable("clan.pioneers.ui.stats.stock_loaded", l.getCompound(0).getInt("loaded")), X, y, W, Ui.INK, Ui.SMALL);
        int k = 0;
        for (int i = 0; i < l.size(); i++) {
            CompoundTag e = l.getCompound(i);
            if (!e.contains("item")) {
                continue;
            }
            Item it = BuiltInRegistries.ITEM.get(ResourceLocation.tryParse(e.getString("item")));
            if (it == Items.AIR) {
                continue;
            }
            int ix = X + (k % 6) * 47, iy = y + 12 + (k / 6) * 26;
            u.fill(ix, iy, ix + 44, iy + 22, 0x10243660);
            u.frame(ix, iy, 44, 22, Ui.FAINT);
            ItemStack st = new ItemStack(it);
            u.item(st, ix + 3, iy + 3, 1F);
            u.fit(Component.literal(Ui.group(e.getLong("count"))), ix + 21, iy + 8, 21, Ui.TEXT, Ui.SMALL);
            u.tip(ix, iy, 44, 22, st.getHoverName());
            k++;
        }
    }
}
