package fr.pioneers.client.rp;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerProfile;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

/**
 * 1.9 (MAJ RP, bloc 3) : onglet Reputation du Carnet. Les donnees viennent du serveur (paquet REPUTATION_VIEW demande a l'ouverture) ; les
 * points eux-memes sont dans le profil synchronise.
 * <p>
 * 1.0.5-beta (lot 16, retours de Guillaume : « rappeler roles et attraits des clans presents, et un bout de lore sur leur origine sans dire
 * pourquoi ») : trois vues a pastilles. PEUPLES : la liste a gauche (embleme, nom, palier, points, jauge), la FICHE du peuple choisi a droite :
 * palier et jauge, role (une ligne ou deux), fragment d'origine (voix de la bible du lore, sans le pourquoi), « Ce qui lui plait » / « Ce qui
 * lui deplait » = les actes REELS du tableau {@code data/pioneers/reputation/acts.json} tels que ce peuple les juge (envoyes par le serveur,
 * {@code ReputationApi.judged}), et son commerce (doctrine). CHANGEMENTS et VILLAGES : les deux listes d'avant, en pleine page (rien de perdu).
 * Maquette fidele : {@code T:rp/pioneer_ui_preview.py} ({@code notebook_reputation}, memes constantes).
 */
final class ReputationTab implements NotebookScreen.Tab {

    private static final int PLUS = 0xFF2E7D32, MINUS = 0xFFB23A2E;

    /** Mise en page (coordonnees de la page) : pastilles des vues, liste des peuples, fiche. */
    public static final int VIEW_Y = 24, VIEW_H = 13, VIEW_PAD = 14, VIEW_GAP = 3, LIST_X = 10, LIST_W = 114, LIST_Y = 42, ROW = 31,
          DET_X = 132, DET_W = 242, ROLE_Y = 74, ORIGIN_Y = 96, ORIGIN_LINES = 3, LIKES_Y = 130, ACT_ROW = 10, ACT_ROWS = 5, TRADE_Y = 199,
          HELP_Y = 219, ICON = 10, LOG_ROW = 11;

    /** 1.9 (MAJ RP, bloc 4) : {@code icon} = peuple modele (un peuple de joueurs n'a pas de texture propre). 1.0.5 (lot 16) : doctrine, actes. */
    record People(String id, int color, Component name, String tier, String icon, String doctrine, boolean player, List<String> acts,
                  List<int[]> judged) {
    }

    /** 1.9 (MAJ RP, bloc 4) : {@code icon} et {@code name} : village rallie a un peuple de joueurs. */
    record Village(String people, int affinity, int distance, String direction, int count, String icon, Component name) {
    }

    private static final List<People> PEOPLES = new ArrayList<>();
    private static final List<Village> VILLAGES = new ArrayList<>();
    private static final List<int[]> TIERS = new ArrayList<>();
    private static final List<String> TIER_IDS = new ArrayList<>();
    private static final String[] VIEWS = {"peoples", "changes", "villages"};
    private static String view = "peoples";
    private static String chosen = "";
    private static int cap = 30;

    @Override
    public String id() {
        return "reputation";
    }

    @Override
    public void opened(NotebookScreen s) {
        RpNet.toServer(RpActions.REPUTATION_REQUEST, new CompoundTag());
    }

    static void onServer(CompoundTag t) {
        PEOPLES.clear();
        for (Tag x : t.getList("peoples", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            List<String> acts = new ArrayList<>();
            List<int[]> judged = new ArrayList<>();
            for (Tag a : c.getList("acts", Tag.TAG_COMPOUND)) {
                int[] r = ((CompoundTag) a).getIntArray("r");
                if (r.length == 4) {
                    acts.add(((CompoundTag) a).getString("a"));
                    judged.add(r);
                }
            }
            PEOPLES.add(new People(c.getString("id"), c.getInt("color"), Component.Serializer.fromJson(c.getString("name")), c.getString("tier"),
                  c.contains("icon") ? c.getString("icon") : c.getString("id"), c.getString("doctrine"), c.getBoolean("player"), acts, judged));
        }
        cap = t.contains("cap") ? t.getInt("cap") : 30;
        VILLAGES.clear();
        for (Tag x : t.getList("villages", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            String pe = c.getString("people");
            VILLAGES.add(new Village(pe, c.getInt("affinity"), c.getInt("distance"), c.getString("dir"), c.getInt("count"),
                  c.contains("icon") ? c.getString("icon") : pe, c.contains("villageName") ? Component.Serializer.fromJson(c.getString("villageName"))
                  : c.contains("peopleName") ? Component.Serializer.fromJson(c.getString("peopleName"))
                  : Component.translatable("people.pioneers." + pe)));
        }
        TIERS.clear();
        TIER_IDS.clear();
        ListTag tiers = t.getList("tiers", Tag.TAG_COMPOUND);
        for (int i = 0; i < tiers.size(); i++) {
            TIER_IDS.add(tiers.getCompound(i).getString("id"));
            TIERS.add(new int[]{tiers.getCompound(i).getInt("max")});
        }
    }

    private static String tierOf(int v) {
        for (int i = 0; i < TIERS.size(); i++) {
            if (v <= TIERS.get(i)[0]) {
                return TIER_IDS.get(i);
            }
        }
        return TIER_IDS.isEmpty() ? "unknown" : TIER_IDS.get(TIER_IDS.size() - 1);
    }

    static ResourceLocation emblem(String icon) {
        return Pioneers.rl("textures/gui/village/people_" + icon + ".png");
    }

    static String signed(int v) {
        return (v > 0 ? "+" : v < 0 ? "−" : "") + Math.abs(v);
    }

    /** Textes d'un peuple : ceux de son id, ou ceux du peuple modele (doctrine) pour un peuple de joueurs. */
    static String textKey(People pe, String part) {
        return "reputation.pioneers.people." + (pe.player() ? pe.icon() : pe.id()) + "." + part;
    }

    @Override
    public void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
        PioneerProfile p = RpClient.profile();
        if (PEOPLES.isEmpty()) {
            s.fit(g, Component.translatable("reputation.pioneers.loading"), 18, 34, 348, ParchmentScreen.DIM);
            return;
        }
        //pastilles des trois vues (largeur du libelle + marge)
        int x = LIST_X;
        for (String v : VIEWS) {
            Component label = switch (v) {
                case "changes" -> Component.translatable("reputation.pioneers.view.changes", Math.min(p.repLog.size(), ReputationLog.MAX));
                case "villages" -> Component.translatable("reputation.pioneers.view.villages", VILLAGES.size());
                default -> Component.translatable("reputation.pioneers.view.peoples", PEOPLES.size());
            };
            int w = s.font().width(label) + VIEW_PAD;
            s.chip(g, mx, my, x, VIEW_Y, w, VIEW_H, label, v.equals(view), true, () -> {
                view = v;
                s.scrollTo(5, 0);
            });
            x += w + VIEW_GAP;
        }
        switch (view) {
            case "changes" -> drawChanges(s, g, mx, my, p);
            case "villages" -> drawVillages(s, g, mx, my);
            default -> drawPeoples(s, g, mx, my, p);
        }
    }

    // ------------------------------------------------------------------ vue Peuples : liste + fiche
    private static void drawPeoples(NotebookScreen s, GuiGraphics g, int mx, int my, PioneerProfile p) {
        People sel = PEOPLES.get(0);
        for (People pe : PEOPLES) {
            if (pe.id().equals(chosen)) {
                sel = pe;
            }
        }
        int h = ParchmentScreen.PAGE_H - 6 - LIST_Y;
        s.scrollArea(5, LIST_X, LIST_Y, LIST_W, h, PEOPLES.size() * ROW);
        s.beginClip(g, LIST_X, LIST_Y, LIST_W, h);
        int y = LIST_Y - s.scrollOf(5);
        for (People pe : PEOPLES) {
            if (y + ROW > LIST_Y && y < LIST_Y + h) {
                peopleRow(s, g, mx, my, pe, p.reputation(pe.id()), y, pe == sel);
            }
            y += ROW;
        }
        s.endClip(g);
        detail(s, g, mx, my, sel, p.reputation(sel.id()));
    }

    private static void peopleRow(NotebookScreen s, GuiGraphics g, int mx, int my, People pe, int v, int y, boolean selected) {
        int col = 0xFF000000 | pe.color(), x = LIST_X, w = LIST_W;
        String tier = tierOf(v);
        g.fill(x, y, x + w, y + ROW - 3, (col & 0x00FFFFFF) | (selected ? 0x44000000 : 0x1C000000));
        g.fill(x, y, x + 3, y + ROW - 3, col);
        if (selected) {
            s.frame(g, x, y, w, ROW - 3, ParchmentScreen.INK);
        }
        s.icon(g, emblem(pe.icon()), x + 6, y + 4, 16);
        s.fit(g, pe.name(), x + 26, y + 4, w - 30, ParchmentScreen.INK);
        int vw = s.fitRight(g, Component.literal(signed(v)), x + w - 4, y + 15, 30, v >= 0 ? PLUS : MINUS, 0.75F);
        s.fit(g, Component.translatable("reputation.pioneers.tier." + tier), x + 26, y + 15, w - 30 - vw - 4, ParchmentScreen.TEXT, 0.75F);
        gauge(g, s, x + 6, y + 23, w - 12, 3, v);
        s.tooltip(mx, my, x, y, w, ROW - 3, Component.translatable("reputation.pioneers.tier." + tier).append(" · " + signed(v)),
              Component.translatable("reputation.pioneers.tier_help." + tier));
        if (s.over(mx, my, x, y, w, ROW - 3) && my >= LIST_Y) {
            s.click(x, Math.max(y, LIST_Y), w, ROW - 3, () -> chosen = pe.id());
        }
    }

    /** Jauge centree sur 0 (-300..+300). */
    private static void gauge(GuiGraphics g, NotebookScreen s, int x, int y, int w, int h, int v) {
        g.fill(x, y, x + w, y + h, 0x30243660);
        int mid = x + w / 2;
        int end = mid + (int) Math.round(w / 2.0 * Math.max(-300, Math.min(300, v)) / 300.0);
        g.fill(Math.min(mid, end), y, Math.max(mid, end), y + h, v >= 0 ? PLUS : MINUS);
        g.fill(mid, y - 1, mid + 1, y + h + 1, ParchmentScreen.INK);
    }

    private static void detail(NotebookScreen s, GuiGraphics g, int mx, int my, People pe, int v) {
        String tier = tierOf(v);
        int x = DET_X, w = DET_W, right = x + w;
        s.icon(g, emblem(pe.icon()), x, LIST_Y, 16);
        s.icon(g, Pioneers.rl("textures/gui/village/rep_" + tier + ".png"), right - 16, LIST_Y, 16);
        Component tierLine = Component.translatable("reputation.pioneers.tier." + tier).append(" · " + signed(v));
        int tw = s.fitRight(g, tierLine, right - 20, LIST_Y + 4, 90, v >= 0 ? PLUS : MINUS, 1F);
        s.fit(g, pe.name(), x + 20, LIST_Y + 4, w - 20 - tw - 26, ParchmentScreen.INK, 1F);
        s.tooltip(mx, my, right - 20 - tw, LIST_Y, tw + 20, 16, Component.translatable("reputation.pioneers.tier_help." + tier));
        //jauge avec les seuils des paliers
        int gy = LIST_Y + 21;
        gauge(g, s, x, gy, w, 5, v);
        s.frame(g, x, gy, w, 5, ParchmentScreen.INK_LIGHT);
        for (int i = 0; i + 1 < TIERS.size(); i++) {
            int tx = x + w / 2 + (int) Math.round(w / 2.0 * Math.max(-300, Math.min(300, TIERS.get(i)[0])) / 300.0);
            g.fill(tx, gy + 5, tx + 1, gy + 7, ParchmentScreen.INK_LIGHT);
        }
        //role et origine
        Component role = pe.player() ? Component.translatable("reputation.pioneers.people.player.role", Component.translatable("people.pioneers." + pe.icon()))
              : Component.translatable(textKey(pe, "role"));
        s.icon(g, RpGui.stat("role"), x, ROLE_Y, ICON);
        s.tooltip(mx, my, x, ROLE_Y, ICON, ICON, Component.translatable("reputation.pioneers.role"));
        s.wrap(g, role, x + ICON + 4, ROLE_Y + 1, w - ICON - 4, ParchmentScreen.TEXT, 1F, 2);
        Component origin = Component.translatable(pe.player() ? "reputation.pioneers.people.player.origin" : textKey(pe, "origin"))
              .withStyle(st -> st.withItalic(true));
        s.icon(g, RpGui.stat("origin"), x, ORIGIN_Y, ICON);
        s.tooltip(mx, my, x, ORIGIN_Y, ICON, ICON, Component.translatable("reputation.pioneers.origin"));
        s.wrap(g, origin, x + ICON + 4, ORIGIN_Y + 1, w - ICON - 4, ParchmentScreen.DIM, 1F, ORIGIN_LINES);
        //ce qui lui plait / deplait (actes reels)
        int cw = (w - 8) / 2;
        acts(s, g, mx, my, pe, true, x, cw);
        acts(s, g, mx, my, pe, false, x + cw + 8, cw);
        //commerce et effet du palier
        s.icon(g, RpGui.stat("trade"), x, TRADE_Y, ICON);
        s.tooltip(mx, my, x, TRADE_Y, ICON, ICON, Component.translatable("reputation.pioneers.trade"));
        s.wrap(g, Component.translatable(textKey(pe, "trade")), x + ICON + 4, TRADE_Y + 1, w - ICON - 4, ParchmentScreen.TEXT, 0.75F, 2);
        s.icon(g, Pioneers.rl("textures/gui/village/rep_" + tier + ".png"), x, HELP_Y, ICON);
        int hh = s.wrap(g, Component.translatable("reputation.pioneers.tier_help." + tier), x + ICON + 4, HELP_Y + 1, w - ICON - 4,
              ParchmentScreen.DIM, 0.75F, 2);
        if (v < 0) {
            s.tooltip(mx, my, x, HELP_Y, w, Math.max(ICON, hh), Component.translatable("reputation.pioneers.reconcile"));
        }
    }

    /** Colonne « Ce qui lui plait » ({@code likes}) ou « Ce qui lui deplait » : icone, titre, actes et points (detail en infobulle). */
    private static void acts(NotebookScreen s, GuiGraphics g, int mx, int my, People pe, boolean likes, int x, int w) {
        s.icon(g, RpGui.stat(likes ? "likes" : "dislikes"), x, LIKES_Y, ICON);
        s.fit(g, Component.translatable(likes ? "reputation.pioneers.likes" : "reputation.pioneers.dislikes"), x + ICON + 4, LIKES_Y + 1, w - ICON - 4,
              ParchmentScreen.INK, 1F);
        if (likes) {
            s.tooltip(mx, my, x, LIKES_Y, w, ICON, Component.translatable("reputation.pioneers.likes.tip", cap));
        } else {
            s.tooltip(mx, my, x, LIKES_Y, w, ICON, Component.translatable("reputation.pioneers.dislikes.tip"));
        }
        s.separator(g, x, LIKES_Y + 11, w);
        List<Integer> rows = new ArrayList<>();
        for (int i = 0; i < pe.judged().size(); i++) {
            int v = shown(pe.judged().get(i));
            if (likes ? v > 0 : v < 0) {
                rows.add(i);
            }
        }
        int y = LIKES_Y + 14;
        if (rows.isEmpty()) {
            s.fit(g, Component.translatable("reputation.pioneers.acts_none"), x, y, w, ParchmentScreen.DIM, 0.75F);
            return;
        }
        for (int k = 0; k < rows.size(); k++) {
            int i = rows.get(k);
            int[] r = pe.judged().get(i);
            String act = pe.acts().get(i);
            if (k == ACT_ROWS - 1 && rows.size() > ACT_ROWS) {
                List<Component> tip = new ArrayList<>();
                for (int j = k; j < rows.size(); j++) {
                    tip.add(actLabel(pe.acts().get(rows.get(j))).append(" " + signed(shown(pe.judged().get(rows.get(j))))));
                }
                s.fit(g, Component.translatable("pioneer.pioneers.more_count", rows.size() - k), x, y, w, ParchmentScreen.DIM, 0.75F);
                s.tooltip(mx, my, x, y - 1, w, ACT_ROW, tip);
                break;
            }
            int v = shown(r);
            int aw = s.fitRight(g, Component.literal(signed(v)), x + w, y, 26, v > 0 ? PLUS : MINUS, 0.75F);
            s.fit(g, actLabel(act), x, y, w - aw - 4, ParchmentScreen.TEXT, 0.75F);
            List<Component> tip = new ArrayList<>();
            tip.add(actLabel(act).append(" : " + signed(v)));
            if (r[0] != 0 && r[1] != 0) {
                tip.add(Component.translatable("reputation.pioneers.act.own", signed(r[0] + r[1])));
            }
            if (r[2] > 0) {
                tip.add(Component.translatable("reputation.pioneers.act.daily", r[2]));
            }
            if (r[3] != 0) {
                tip.add(Component.translatable("reputation.pioneers.act.uncapped"));
            }
            s.tooltip(mx, my, x, y - 1, w, ACT_ROW, tip);
            y += ACT_ROW;
        }
    }

    /** Meme regle que {@code ReputationApi.shown} : points du peuple, ou la part « chez lui » quand l'acte ne vise que les siens. */
    static int shown(int[] r) {
        return r[0] != 0 ? r[0] : r[1];
    }

    private static net.minecraft.network.chat.MutableComponent actLabel(String act) {
        return Component.translatable("reputation.pioneers.act." + act);
    }

    // ------------------------------------------------------------------ vue Changements : journal en pleine page
    private static void drawChanges(NotebookScreen s, GuiGraphics g, int mx, int my, PioneerProfile p) {
        List<CompoundTag> log = new ArrayList<>(p.repLog);
        //1.0.5-beta (lot 19) : rangees ENTIERES (plus de derniere ligne coupee) ; la barre de defilement dit qu'il y a une suite
        int x = LIST_X, w = ParchmentScreen.PAGE_W - 2 * LIST_X, top = LIST_Y, h = (ParchmentScreen.PAGE_H - 6 - top) / LOG_ROW * LOG_ROW;
        if (log.isEmpty()) {
            s.wrap(g, Component.translatable("reputation.pioneers.no_change"), x + 4, top + 4, w - 8, ParchmentScreen.DIM, 1F, 3);
            return;
        }
        int dw = 0, nw = 0;
        for (CompoundTag c : log) {
            dw = Math.max(dw, s.fitWidth(Component.translatable("pioneer.pioneers.day_short", c.getLong("d")), 44, 0.75F));
            nw = Math.max(nw, s.fitWidth(nameOf(c.getString("p")), 96, 0.75F));
        }
        s.scrollArea(5, x, top, w, h, log.size() * LOG_ROW);
        s.beginClip(g, x, top, w, h);
        int y = top - s.scrollOf(5);
        for (int i = log.size() - 1; i >= 0; i--) {
            CompoundTag c = log.get(i);
            if (y + LOG_ROW > top && y < top + h) {
                int v = c.getInt("v");
                s.fit(g, Component.translatable("pioneer.pioneers.day_short", c.getLong("d")), x + 2, y + 1, dw, ParchmentScreen.DIM, 0.75F);
                int ix = x + 2 + dw + 4;
                s.icon(g, emblem(iconOf(c.getString("p"))), ix, y, 8);
                s.fit(g, nameOf(c.getString("p")), ix + 11, y + 1, nw, ParchmentScreen.INK_LIGHT, 0.75F);
                int aw = s.fitRight(g, Component.literal(signed(v)), x + w - 4, y + 1, 30, v > 0 ? PLUS : MINUS, 0.75F);
                int cx = ix + 11 + nw + 6;
                s.fit(g, Component.translatable("reputation.pioneers.cause." + c.getString("c")), cx, y + 1, x + w - 4 - aw - 6 - cx, ParchmentScreen.TEXT,
                      0.75F);
                s.separator(g, x, y + LOG_ROW - 1, w);
            }
            y += LOG_ROW;
        }
        s.endClip(g);
    }

    private static Component nameOf(String id) {
        for (People pe : PEOPLES) {
            if (pe.id().equals(id)) {
                return pe.name();
            }
        }
        return Component.translatable("people.pioneers." + id);
    }

    private static String iconOf(String id) {
        for (People pe : PEOPLES) {
            if (pe.id().equals(id)) {
                return pe.icon();
            }
        }
        return id;
    }

    // ------------------------------------------------------------------ vue Villages
    private static void drawVillages(NotebookScreen s, GuiGraphics g, int mx, int my) {
        int row = 2 * LOG_ROW;
        //1.0.5-beta (lot 19, relecture : « derniere ligne coupee en bas ») : rangees ENTIERES + barre de defilement
        int x = LIST_X, w = ParchmentScreen.PAGE_W - 2 * LIST_X, top = LIST_Y, h = (ParchmentScreen.PAGE_H - 6 - top) / row * row;
        if (VILLAGES.isEmpty()) {
            s.wrap(g, Component.translatable("reputation.pioneers.no_village"), x + 4, top + 4, w - 8, ParchmentScreen.DIM, 1F, 3);
            return;
        }
        s.scrollArea(5, x, top, w, h, VILLAGES.size() * row);
        s.beginClip(g, x, top, w, h);
        int y = top - s.scrollOf(5);
        for (Village v : VILLAGES) {
            if (y + row > top && y < top + h) {
                s.icon(g, emblem(v.icon()), x + 2, y + 3, 16);
                int aw = s.fitRight(g, Component.literal(signed(v.affinity())), x + w - 4, y + 2, 30, v.affinity() >= 0 ? PLUS : MINUS, 1F);
                s.tooltip(mx, my, x + w - 4 - aw, y + 1, aw, 9, Component.translatable("reputation.pioneers.affinity.tip"));
                s.fit(g, v.name(), x + 22, y + 2, w - 22 - aw - 10, ParchmentScreen.INK, 1F);
                s.fit(g, Component.translatable("reputation.pioneers.village_where", v.distance(),
                      Component.translatable("reputation.pioneers.dir." + v.direction()), v.count()), x + 22, y + 12, w - 26, ParchmentScreen.DIM, 0.75F);
                s.separator(g, x, y + row - 1, w);
            }
            y += row;
        }
        s.endClip(g);
    }

    /** Taille du journal de reputation garde par le serveur ({@code ReputationApi.LOG}). */
    static final class ReputationLog {

        static final int MAX = 30;

        private ReputationLog() {
        }
    }
}
