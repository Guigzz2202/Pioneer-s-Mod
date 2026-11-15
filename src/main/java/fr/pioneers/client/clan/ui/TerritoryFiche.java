package fr.pioneers.client.clan.ui;

import static fr.pioneers.client.clan.ui.ClanMapModel.ALLY;
import static fr.pioneers.client.clan.ui.ClanMapModel.CAPTURED;
import static fr.pioneers.client.clan.ui.ClanMapModel.DECLINE;
import static fr.pioneers.client.clan.ui.ClanMapModel.ENEMY;
import static fr.pioneers.client.clan.ui.ClanMapModel.FEDERAL;
import static fr.pioneers.client.clan.ui.ClanMapModel.FOG;
import static fr.pioneers.client.clan.ui.ClanMapModel.FREE;
import static fr.pioneers.client.clan.ui.ClanMapModel.HALL;
import static fr.pioneers.client.clan.ui.ClanMapModel.LOADED;
import static fr.pioneers.client.clan.ui.ClanMapModel.NEUTRAL;
import static fr.pioneers.client.clan.ui.ClanMapModel.OUTPOST;
import static fr.pioneers.client.clan.ui.ClanMapModel.OWN;
import static fr.pioneers.client.clan.ui.ClanMapModel.PLOT;
import static fr.pioneers.client.clan.ui.ClanMapModel.RALLIED;
import static fr.pioneers.client.clan.ui.ClanMapModel.SIEGE;
import static fr.pioneers.client.clan.ui.ClanMapModel.TOWER;
import static fr.pioneers.client.clan.ui.ClanMapModel.ZONE_SHIFT;
import static fr.pioneers.client.clan.ui.ClanTab.W;
import static fr.pioneers.client.clan.ui.ClanTab.X;
import static fr.pioneers.client.clan.ui.ClanTab.Y;

import fr.pioneers.client.minimap.TerrainTiles;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : fiche Territoire et carte strategique : 31 x 31 chunks autour du joueur, vus par une fenetre de 23 x 23
 * cases de 8 pixels (la taille des tuiles) que des fleches deplacent sur la grille ; une tuile
 * {@code chunk_<etat>} par case (le gris des tuiles « clan » teinte a la couleur du clan concerne, comme le dit le manifeste des visuels),
 * frontieres entre proprietaires, Mairies et Tours de guet, position du joueur ; legende (signe + nom) ; survol = proprietaire (nom du clan,
 * jamais un joueur) et etats ; sur la case choisie : reclamer, abandonner, zone, charger ou liberer, selon les permissions.
 * <p>
 * 1.9.2 (retours de Guillaume : « drag and drop la zone que je souhaite claim ») : cliquer-glisser trace un rectangle de chunks (Maj ou
 * le bouton de mode : rendre). Au relachement, UN paquet d'apercu ; le serveur repond par un recapitulatif (chunks acceptes, cout,
 * budget restant, refus par raison, verdict de chaque case) affiche sur la carte ; « Confirmer » envoie UN paquet d'application, que le
 * serveur revoit entierement. Un simple clic choisit toujours une case.
 * <p>
 * 1.0.3-beta (lot 16, Guillaume : « acheter des chunk claim avec l'argent du clan » ; « des permissions pour les chunks claims : etrangers,
 * clans allies, neutres, ennemis ») : deux vues, choisies par deux pastilles en tete de la colonne de droite : « Carte » (ci-dessus) et
 * « Reglages » : a gauche, les permissions des visiteurs de la zone choisie (Membres, Ouverte, Privee) en grille categories x groupes
 * d'actions a cases ; a droite, l'achat de claims au tresor (budget, achetes, prix du prochain et des 5 suivants, tresor, « Acheter » et
 * « x5 » a confirmer d'un second clic). Lecture seule sans la permission. Maquette fidele : {@code T:rp/clans_ui_preview.py}
 * ({@code fiche_territory_settings}, memes constantes).
 */
final class TerritoryFiche extends ClanTab.Fiche {

    /** Cases de 8 pixels (taille des tuiles : jamais reduites) ; fenetre de 23 x 23 cases qui se deplace sur la grille de 31 x 31. */
    static final int CELL = 8, VIEW = 23, MAP = VIEW * CELL, PX = X + MAP + 6, PAN = 4;
    /**
     * 1.0.4-beta (lot 8) : zooms (pixels par chunk : la fenetre montre 31, 23, 16 ou 12 chunks de la grille de 31 x 31), opacite des claims
     * sur le terrain, fond jamais vu (papier sombre), brouillard du serveur (proprietaires inconnus), grille fine, boutons de zoom.
     */
    static final int[] ZOOMS = {6, 8, 12, 16};
    static final float CLAIM_ALPHA = 0.55F;
    static final int UNSEEN = 0xFFCDC1A2, HAZE = 0xB0D9CDB0, GRID = 0x30243660, ZOOM_B = 10, HALO = 0xB0101418;
    private static int zoom = 1;
    /** 1.0.4-beta (lot 8) : mode « claims charges d'office » (fiche du serveur) : pas de tuile « charge », pas de ligne de legende. */

    static int cell() {
        return ZOOMS[zoom];
    }

    static int view() {
        return (MAP + cell() - 1) / cell();
    }
    /** Tuiles grises que le jeu teint a la couleur du clan (manifeste {@code tools/gc/rp/clans_visuals.json}, tint « clan »). */
    static final Set<String> TINTED = Set.of("own", "ally", "enemy");
    static final String[] LEGEND = {"fog", "free", "own", "ally", "neutral", "enemy", "plot", "outpost", "decline", "captured", "siege", "loaded",
          "federal", "rallied", "hall", "tower"};
    private static final String[] ZONES = {"members", "private", "open"};
    /** Case choisie (coordonnees absolues du chunk) ; {@link Integer#MIN_VALUE} : aucune. */
    private static int selX = Integer.MIN_VALUE, selZ;
    private static long watched;
    private static int panX, panZ;
    /** 1.9.2 : zone tracee (coins absolus), mode « rendre » (bouton ; Maj l'inverse au debut du geste), attente et recapitulatif recu. */
    private static boolean dragging, unclaimMode, dragUnclaim, waiting;
    private static int dragX0, dragZ0, dragX1, dragZ1, areaSeen;
    @Nullable
    private static CompoundTag recap;
    /** 1.0.3-beta (lot 16) : vue Reglages, zone affichee (0 Membres, 1 Ouverte, 2 Privee), achat a confirmer (quantite, fin en ms). */
    static final int VIEW_H = 13, ZP_LABEL_W = 56, ZP_COL = 16, ZP_ROW = 16, ZP_HEAD_Y = Y + 44, ZP_ROWS_Y = Y + 62, CONFIRM_MS = 4000;
    static final String[] VISITORS = {"ally", "neutral", "stranger", "enemy"};
    static final String[] GROUPS = {"interact", "containers", "machines", "vehicles", "build", "crops", "animals", "hunt"};
    /** Pastille de chaque categorie : la tuile de la carte et sa teinte (etranger = tuile Solitaire). */
    static final String[] VISITOR_TILES = {"ally", "neutral", "plot", "enemy"};
    static final int[] VISITOR_DYES = {5, 1, 8, 14};
    private static boolean settings;
    private static int zoneTab, confirmN;
    private static long confirmUntil;

    @Override
    String id() {
        return "territory";
    }

    @Override
    void enter() {
        ClanUiClient.mapWatch(true, true);
        watched = System.currentTimeMillis();
        ClanUiClient.request("territory");
        selX = Integer.MIN_VALUE;
        panX = panZ = 0;
        dragging = waiting = false;
        confirmN = 0;
        recap = null;
        areaSeen = ClanMapModel.areaVersion;
    }

    @Override
    void leave() {
        //1.10.3 : la minimap garde l'abonnement a la carte tant qu'elle montre les claims
        if (!ClanMapAccess.wanted()) {
            ClanUiClient.mapWatch(false, false);
        }
    }

    @Override
    void draw(Ui u) {
        long now = System.currentTimeMillis();
        if (now - watched > 5000) {
            watched = now;
            ClanUiClient.mapWatch(true, false);
        }
        areaReply();
        CompoundTag t = ClanUiClient.sheet("territory");
        CompoundTag sheet = ClanUiClient.sheet("sheet");
        int myColor = sheet == null ? 11 : sheet.getInt("color");
        if (settings && t != null && t.getBoolean("clan")) {
            permissions(u, t);
            purchase(u, t);
            viewChips(u, t);
            return;
        }
        map(u, myColor);
        side(u, t, myColor);
        actions(u, t);
    }

    // ------------------------------------------------------------------ 1.0.3-beta (lot 16) : vues Carte et Reglages
    /** Deux pastilles en tete de la colonne de droite (seulement pour un clan : l'achat et les permissions en demandent un). */
    private static void viewChips(Ui u, @Nullable CompoundTag t) {
        if (t == null || !t.getBoolean("clan")) {
            return;
        }
        int x = PX, w = X + W - PX, cw = (w - 2) / 2;
        u.navChip(x, Y, cw, VIEW_H, null, Component.translatable("clan.pioneers.ui.territory.view.map"), !settings, 0,
              () -> settings = false);
        u.navChip(x + cw + 2, Y, w - cw - 2, VIEW_H, null, Component.translatable("clan.pioneers.ui.territory.view.settings"), settings, 0,
              () -> {
            settings = true;
            confirmN = 0;
        });
        u.tip(x + cw + 2, Y, w - cw - 2, VIEW_H, Component.translatable("clan.pioneers.ui.territory.view.settings.tip"));
    }

    /** Masque des groupes permis a la categorie {@code v} dans la zone {@code zone} (0 Membres, 1 Ouverte ; Privee : 0). */
    static int mask(int[] zperm, int zone, int v) {
        return zone > 1 || zperm.length != 8 ? 0 : zperm[zone * 4 + v];
    }

    /** Envoie les 4 masques de la zone (et les applique tout de suite a la fiche gardee : le serveur la renvoie a jour). */
    private static void sendZone(CompoundTag t, int zone, int[] masks) {
        int[] all = t.getIntArray("zperm");
        if (all.length == 8) {
            System.arraycopy(masks, 0, all, zone * 4, 4);
            t.putIntArray("zperm", all);
        }
        CompoundTag a = new CompoundTag();
        a.putString("zone", zone == 0 ? "members" : "open");
        a.putIntArray("masks", masks);
        ClanUiClient.act("zone_perms", "territory", a);
    }

    /** Case a cocher de 10 x 10 : cochee = aplat d'encre et coche de papier ; lecture seule = encre claire. */
    static void box(Ui u, int x, int y, boolean on, boolean enabled, boolean hover) {
        int ink = enabled ? Ui.INK : Ui.INK_LIGHT;
        u.fill(x, y, x + 10, y + 10, on ? ink : hover ? 0x40243660 : 0x14243660);
        u.frame(x, y, 10, 10, on ? ink : enabled ? Ui.INK_LIGHT : Ui.FAINT);
        if (on) {
            int[][] pts = {{2, 5}, {3, 6}, {4, 7}, {5, 6}, {6, 5}, {7, 4}, {8, 3}, {3, 5}, {4, 6}};
            for (int[] p : pts) {
                u.fill(x + p[0], y + p[1], x + p[0] + 1, y + p[1] + 1, Ui.PAPER);
            }
        }
    }

    /** Icone d'un groupe d'actions : celles des permissions de role pour Construire, Conteneurs, Machines ; zperm_* sinon. */
    static String groupIcon(String g) {
        return switch (g) {
            case "containers" -> "perm_containers";
            case "machines" -> "perm_machines";
            case "build" -> "perm_build";
            default -> "zperm_" + g;
        };
    }

    /** Panneau des permissions des visiteurs (gauche, largeur de la carte). */
    private static void permissions(Ui u, CompoundTag t) {
        int[] zperm = t.getIntArray("zperm");
        boolean editable = t.getBoolean("p_zones");
        u.fit(Component.translatable("clan.pioneers.ui.zperm.title"), X, Y, MAP, Ui.INK, 1F);
        int zw = (MAP - 4) / 3;
        String[] zones = {"members", "open", "private"};
        for (int i = 0; i < 3; i++) {
            int zi = i;
            u.chip(X + i * (zw + 2), Y + 11, i < 2 ? zw : MAP - 2 * (zw + 2), 13, Component.translatable("clan.pioneers.zone." + zones[i]), zoneTab == i,
                  true, () -> zoneTab = zi);
        }
        u.wrap(Component.translatable("clan.pioneers.ui.zperm.desc." + zones[zoneTab]), X, Y + 27, MAP, Ui.DIM, Ui.SMALL, 2);
        boolean live = editable && zoneTab < 2 && zperm.length == 8;
        int[] masks = new int[4];
        for (int v = 0; v < 4; v++) {
            masks[v] = mask(zperm, zoneTab, v);
        }
        int hoverV = -1, hoverG = -1;
        if (u.over(X + ZP_LABEL_W, ZP_ROWS_Y, GROUPS.length * ZP_COL, 4 * ZP_ROW)) {
            hoverG = (u.mx - X - ZP_LABEL_W) / ZP_COL;
            hoverV = (u.my - ZP_ROWS_Y) / ZP_ROW;
        }
        //en-tetes de colonnes : icone du groupe (infobulle : nom et detail ; clic : toute la colonne)
        for (int gi = 0; gi < GROUPS.length; gi++) {
            int gx = X + ZP_LABEL_W + gi * ZP_COL;
            boolean colHover = u.over(gx, ZP_HEAD_Y, ZP_COL, 16);
            if (hoverG == gi || colHover) {
                u.fill(gx, ZP_HEAD_Y - 1, gx + ZP_COL, ZP_ROWS_Y + 4 * ZP_ROW, 0x14243660);
            }
            u.image(Ui.gui(groupIcon(GROUPS[gi])), gx, ZP_HEAD_Y, 16, 16, 16, 16);
            u.tip(gx, ZP_HEAD_Y, ZP_COL, 16, Component.translatable("clan.pioneers.zone_perm.group." + GROUPS[gi]),
                  Component.translatable("clan.pioneers.zone_perm.group." + GROUPS[gi] + ".desc"));
            if (live) {
                int bit = 1 << gi;
                u.click(gx, ZP_HEAD_Y, ZP_COL, 16, () -> {
                    boolean all = true;
                    for (int m : masks) {
                        all &= (m & bit) != 0;
                    }
                    int[] next = masks.clone();
                    for (int v = 0; v < 4; v++) {
                        next[v] = all ? next[v] & ~bit : next[v] | bit;
                    }
                    sendZone(t, zoneTab, next);
                });
            }
        }
        u.fit(Component.translatable("clan.pioneers.ui.zperm.visitors"), X, ZP_HEAD_Y + 8, ZP_LABEL_W - 2, Ui.INK_LIGHT, Ui.SMALL);
        for (int v = 0; v < 4; v++) {
            int ry = ZP_ROWS_Y + v * ZP_ROW, vi = v;
            u.separator(X, ry, MAP);
            drawTile(u, VISITOR_TILES[v], X + 1, ry + 4, CELL, VISITOR_DYES[v]);
            u.fit(Component.translatable("clan.pioneers.zone_perm.visitor." + VISITORS[v]), X + 12, ry + 5, ZP_LABEL_W - 14, Ui.TEXT, Ui.SMALL);
            u.tip(X, ry, ZP_LABEL_W, ZP_ROW, Component.translatable("clan.pioneers.zone_perm.visitor." + VISITORS[v]),
                  Component.translatable("clan.pioneers.zone_perm.visitor." + VISITORS[v] + ".desc"));
            if (live) {
                u.click(X, ry, ZP_LABEL_W, ZP_ROW, () -> {
                    int[] next = masks.clone();
                    next[vi] = next[vi] == fr.pioneers.clan.territory.ZonePerms.ALL ? 0 : fr.pioneers.clan.territory.ZonePerms.ALL;
                    sendZone(t, zoneTab, next);
                });
            }
            for (int gi = 0; gi < GROUPS.length; gi++) {
                boolean on = (masks[v] >> gi & 1) != 0;
                int cx = X + ZP_LABEL_W + gi * ZP_COL, bit = 1 << gi;
                box(u, cx + 3, ry + 3, on, live, hoverV == v && hoverG == gi && live);
                if (live) {
                    u.click(cx, ry, ZP_COL, ZP_ROW, () -> {
                        int[] next = masks.clone();
                        next[vi] ^= bit;
                        sendZone(t, zoneTab, next);
                    });
                }
            }
        }
        u.separator(X, ZP_ROWS_Y + 4 * ZP_ROW, MAP);
        int iy = ZP_ROWS_Y + 4 * ZP_ROW + 4;
        if (hoverV >= 0 && hoverV < 4 && hoverG >= 0 && hoverG < GROUPS.length) {
            boolean on = (masks[hoverV] >> hoverG & 1) != 0;
            u.fit(Component.translatable("clan.pioneers.ui.zperm.cell", Component.translatable("clan.pioneers.zone_perm.visitor." + VISITORS[hoverV]),
                  Component.translatable("clan.pioneers.zone_perm.group." + GROUPS[hoverG]),
                  Component.translatable(on ? "clan.pioneers.ui.zperm.on" : "clan.pioneers.ui.zperm.off")), X, iy, MAP, on ? Ui.PLUS : Ui.MINUS, Ui.SMALL);
            u.wrap(Component.translatable("clan.pioneers.zone_perm.group." + GROUPS[hoverG] + ".desc"), X, iy + 8, MAP, Ui.DIM, Ui.SMALL, 2);
        } else if (zoneTab == 2) {
            u.wrap(Component.translatable("clan.pioneers.ui.zperm.private"), X, iy, MAP, Ui.DIM, Ui.SMALL, 3);
        } else if (!editable) {
            u.wrap(Component.translatable("clan.pioneers.ui.zperm.readonly", Component.translatable("clan.pioneers.perm.zones")), X, iy, MAP, Ui.MINUS,
                  Ui.SMALL, 3);
        } else {
            u.wrap(Component.translatable("clan.pioneers.ui.zperm.hint"), X, iy, MAP, Ui.DIM, Ui.SMALL, 3);
        }
        int by = iy + 26;
        if (live) {
            int bw = (MAP - 2) / 2;
            u.button(X, by, bw, 13, Component.translatable("clan.pioneers.ui.zperm.close_all"), true, () -> sendZone(t, zoneTab, new int[4]));
            u.button(X + bw + 2, by, MAP - bw - 2, 13, Component.translatable("clan.pioneers.ui.zperm.defaults"), true, () -> {
                CompoundTag a = new CompoundTag();
                a.putString("zone", zoneTab == 0 ? "members" : "open");
                a.putBoolean("reset", true);
                ClanUiClient.act("zone_perms", "territory", a);
            });
            u.tip(X + bw + 2, by, MAP - bw - 2, 13, Component.translatable("clan.pioneers.ui.zperm.defaults.tip"));
        }
        u.wrap(Component.translatable("clan.pioneers.ui.zperm.note"), X, by + 17, MAP, Ui.DIM, Ui.SMALL, 4);
    }

    /** Achat de claims au tresor (colonne de droite de la vue Reglages). */
    private static void purchase(Ui u, CompoundTag t) {
        int x = PX, w = X + W - PX, y = Y + VIEW_H + 3;
        u.fit(Component.translatable("clan.pioneers.ui.buy.title"), x, y, w, Ui.INK, Ui.SMALL);
        y += 9;
        y += u.wrap(Component.translatable("clan.pioneers.ui.buy.unit"), x, y, w, Ui.DIM, Ui.SMALL, 2) + 2;
        int bought = t.getInt("bought"), cap = t.getInt("buyMax"), free = t.getInt("free");
        long[] prices = t.getLongArray("prices");
        boolean capped = bought >= cap;
        int n5 = Math.max(0, Math.min(5, cap - bought));
        long next = prices.length > 0 ? prices[0] : 0, five = 0;
        for (int i = 0; i < n5 && i < prices.length; i++) {
            five += prices[i];
        }
        boolean known = t.contains("treasury");
        long treasury = t.getLong("treasury");
        List<Component> tip = new ArrayList<>();
        tip.add(Component.translatable("clan.pioneers.ui.buy.prices"));
        for (int i = 0; i < prices.length && i < n5; i++) {
            tip.add(Component.translatable("clan.pioneers.ui.buy.price_line", bought + 1 + i, Ui.group(prices[i])));
        }
        u.row(Component.translatable("clan.pioneers.ui.buy.budget"), Component.literal(t.getInt("claims") + " / " + t.getInt("budget")), x, y, w,
              t.getInt("claims") > t.getInt("budget") ? Ui.MINUS : Ui.TEXT);
        y += 9;
        u.row(Component.translatable("clan.pioneers.ui.buy.free"), Component.literal(Integer.toString(free)), x, y, w, Ui.TEXT);
        y += 9;
        u.row(Component.translatable("clan.pioneers.ui.buy.bought"), Component.literal(bought + " / " + cap), x, y, w, capped ? Ui.MINUS : Ui.TEXT);
        y += 9;
        u.row(Component.translatable("clan.pioneers.ui.buy.next"), Component.literal(capped ? "—" : Ui.group(next)), x, y, w, Ui.TEXT);
        if (!capped) {
            u.tip(x, y, w, 8, tip);
        }
        y += 9;
        u.row(Component.translatable("clan.pioneers.ui.buy.five"), Component.literal(capped ? "—" : Ui.group(five)), x, y, w, Ui.TEXT);
        if (!capped) {
            u.tip(x, y, w, 8, tip);
        }
        y += 9;
        boolean shortOne = known && treasury < next;
        u.row(Component.translatable("clan.pioneers.ui.buy.treasury"), known ? Component.literal(Ui.group(treasury))
              : Component.translatable("clan.pioneers.ui.buy.hidden"), x, y, w, shortOne ? Ui.MINUS : Ui.TEXT);
        y += 9;
        //raison du refus (dans l'ordre des controles du serveur) ; le serveur revoit tout
        Component why = null;
        if (!t.getBoolean("economy")) {
            why = Component.translatable("clan.pioneers.ui.buy.no_economy");
        } else if (t.getBoolean("frozen")) {
            why = Component.translatable("clan.pioneers.ui.buy.frozen");
        } else if (!t.getBoolean("p_buy")) {
            why = Component.translatable("clan.pioneers.ui.buy.no_right", Component.translatable("clan.pioneers.perm.claim"),
                  Component.translatable("clan.pioneers.perm.withdraw"));
        } else if (capped) {
            why = Component.translatable("clan.pioneers.ui.buy.cap", cap);
        } else if (shortOne) {
            why = Component.translatable("clan.pioneers.ui.buy.short", Ui.group(next - treasury));
        }
        long now = System.currentTimeMillis();
        if (confirmN > 0 && now > confirmUntil) {
            confirmN = 0;
        }
        boolean can1 = why == null, can5 = can1 && n5 > 1 && (!known || treasury >= five);
        int bw = w - 30, seen = bought;
        Component one = confirmN == 1 ? Component.translatable("clan.pioneers.ui.buy.confirm", Ui.group(next)) : Component.translatable("clan.pioneers.ui.buy.one");
        u.button(x, y + 1, bw, 13, one, can1 && confirmN != 5, () -> buy(1, seen));
        Component many = confirmN == 5 ? Component.translatable("clan.pioneers.ui.buy.confirm", Ui.group(five)) : Component.translatable("clan.pioneers.ui.buy.many");
        int n = n5;
        u.button(x + bw + 2, y + 1, w - bw - 2, 13, many, can5 && confirmN != 1, () -> buy(n, seen));
        if (can5 && confirmN != 1) {
            u.tip(x + bw + 2, y + 1, w - bw - 2, 13, tip);
        }
        y += 18;
        if (confirmN > 0) {
            y += u.wrap(Component.translatable("clan.pioneers.ui.buy.confirm_tip"), x, y, w, Ui.INK, Ui.SMALL, 3) + 3;
        } else if (why != null) {
            y += u.wrap(Component.literal("− ").append(why), x, y, w, Ui.MINUS, Ui.SMALL, 3) + 3;
        }
        //1.0.5-beta (lot 13) : prix fixe d'un claim achete
        u.wrap(Component.translatable("clan.pioneers.ui.buy.fixed", Ui.group(t.getLong("price"))), x, y, w, Ui.DIM, Ui.SMALL, 11);
    }

    /** Premier clic : a confirmer (4 s) ; second clic sur le meme bouton : la demande part (le serveur recalcule le prix). */
    private static void buy(int n, int seen) {
        int key = n == 1 ? 1 : 5;
        if (confirmN != key) {
            confirmN = key;
            confirmUntil = System.currentTimeMillis() + CONFIRM_MS;
            return;
        }
        confirmN = 0;
        CompoundTag a = new CompoundTag();
        a.putInt("n", n);
        a.putInt("seen", seen);
        ClanUiClient.act("claim_buy", "territory", a);
    }

    // ------------------------------------------------------------------ carte
    /** Etat montre par la tuile d'une case (le plus important). */
    static String tile(int cell) {
        int k = ClanMapModel.kind(cell);
        if (k == FOG) {
            return "fog";
        }
        if ((cell & SIEGE) != 0) {
            return "siege";
        }
        if ((cell & CAPTURED) != 0) {
            return "captured";
        }
        if ((cell & DECLINE) != 0) {
            return "decline";
        }
        if ((cell & LOADED) != 0) {
            return "loaded";
        }
        if ((cell & OUTPOST) != 0) {
            return "outpost";
        }
        if ((cell & FEDERAL) != 0) {
            return "federal";
        }
        if ((cell & RALLIED) != 0) {
            return "rallied";
        }
        return switch (k) {
            case OWN -> "own";
            case ALLY -> "ally";
            case ENEMY -> "enemy";
            case NEUTRAL -> "neutral";
            case PLOT -> "plot";
            default -> "free";
        };
    }

    /** Dessine une tuile d'etat a {@code size} pixels ({@code dye} : couleur du clan pour les tuiles teintees). */
    static void drawTile(Ui u, String tile, int x, int y, int size, int dye) {
        drawTile(u, tile, x, y, size, dye, 1F);
    }

    /** 1.0.4-beta (lot 8) : tuile a l'opacite {@code alpha} (sur la carte, le terrain se voit dessous). */
    static void drawTile(Ui u, String tile, int x, int y, int size, int dye, float alpha) {
        switch (tile) {
            case "fog" -> u.fill(x, y, x + size, y + size, 0x24243660);
            case "neutral" -> {
                //autre clan : aplat a sa couleur, sans signe de relation (ni cadre, ni hachure, ni damier)
                u.fill(x, y, x + size, y + size, Math.round(0xA0 * alpha) << 24 | Ui.dye(dye));
            }
            case "hall" -> {
                u.fill(x, y, x + size, y + size, 0x10243660);
                hall(u, x, y, size);
            }
            case "tower" -> {
                u.fill(x, y, x + size, y + size, 0x10243660);
                tower(u, x, y, size);
            }
            default -> {
                if (TINTED.contains(tile)) {
                    u.tinted(Ui.gui("chunk_" + tile), x, y, size, size, 8, 8, Ui.tint(dye), alpha);
                } else if (alpha < 1F) {
                    u.tinted(Ui.gui("chunk_" + tile), x, y, size, size, 8, 8, 0xFFFFFF, alpha);
                } else {
                    u.image(Ui.gui("chunk_" + tile), x, y, size, size, 8, 8);
                }
            }
        }
    }

    /** Marque de Mairie : maison a l'encre (toit et corps). */
    static void hall(Ui u, int x, int y, int size) {
        int c = x + size / 2;
        u.fill(c - 1, y + 1, c + 1, y + 2, Ui.INK);
        u.fill(c - 2, y + 2, c + 2, y + 3, Ui.INK);
        u.fill(c - 2, y + 3, c + 2, y + size - 1, Ui.INK);
        u.fill(c - 1, y + size - 3, c, y + size - 1, Ui.PAPER);
    }

    /** Marque de Tour de guet : fut fin et creneau. */
    static void tower(Ui u, int x, int y, int size) {
        int c = x + size / 2;
        u.fill(c - 2, y + 1, c + 2, y + 2, Ui.INK);
        u.fill(c - 1, y + 2, c + 1, y + size - 1, Ui.INK);
    }

    /** Case de la grille (0..n-1) sous la case (vx, vz) de la fenetre. */
    private static int gridX(int vx) {
        return vx + (ClanMapModel.n - view()) / 2 + panX;
    }

    private static int gridZ(int vz) {
        return vz + (ClanMapModel.n - view()) / 2 + panZ;
    }

    private static void map(Ui u, int myColor) {
        u.frame(X - 1, Y - 1, MAP + 2, MAP + 2, Ui.INK_LIGHT);
        if (!ClanMapModel.ready()) {
            u.fill(X, Y, X + MAP, Y + MAP, 0x14243660);
            u.center(Component.translatable("clan.pioneers.ui.loading"), X + MAP / 2, Y + MAP / 2 - 4, MAP - 8, Ui.DIM, 1F);
            return;
        }
        int cell = cell(), view = view();
        int margin = Math.max(0, (ClanMapModel.n - view) / 2);
        panX = Math.max(-margin, Math.min(margin, panX));
        panZ = Math.max(-margin, Math.min(margin, panZ));
        u.s.beginClip(u.g, X, Y, MAP, MAP);
        //1.0.4-beta (lot 8) : le TERRAIN releve par la minimap (memes tuiles), sur un fond de papier sombre la ou rien n'a ete vu
        u.fill(X, Y, X + MAP, Y + MAP, UNSEEN);
        int ax0 = ClanMapModel.cx - ClanMapModel.r + gridX(0), az0 = ClanMapModel.cz - ClanMapModel.r + gridZ(0);
        TerrainTiles.draw(u.g, ClanMapModel.dim, ax0 * 16.0, az0 * 16.0, 16.0 / cell, X, Y, view * cell, view * cell, 1F);
        //claims par-dessus, a demi transparents (le terrain reste lisible) ; brouillard du serveur (proprietaires inconnus)
        for (int vz = 0; vz < view; vz++) {
            for (int vx = 0; vx < view; vx++) {
                int c = ClanMapModel.at(gridX(vx), gridZ(vz));
                int px = X + vx * cell, py = Y + vz * cell;
                int k = ClanMapModel.kind(c);
                if (k == FOG) {
                    u.fill(px, py, px + cell, py + cell, HAZE);
                } else if (k != FREE) {
                    ClanMapModel.Owner o = ClanMapModel.owner(c);
                    int dye = k == OWN ? myColor : o == null ? 8 : o.color();
                    drawTile(u, tile(c), px, py, cell, dye, CLAIM_ALPHA);
                }
                if ((c & HALL) != 0) {
                    hall(u, px + (cell - 8) / 2, py + (cell - 8) / 2, 8);
                } else if ((c & TOWER) != 0) {
                    tower(u, px + (cell - 8) / 2, py + (cell - 8) / 2, 8);
                }
            }
        }
        //grille fine des chunks
        for (int i = 1; i < view; i++) {
            u.fill(X + i * cell, Y, X + i * cell + 1, Y + MAP, GRID);
            u.fill(X, Y + i * cell, X + MAP, Y + i * cell + 1, GRID);
        }
        //frontieres : un trait a la COULEUR du clan (celui de gauche ou du haut s'il y en a un) entre deux proprietaires differents
        for (int vz = 0; vz < view; vz++) {
            for (int vx = 0; vx < view; vx++) {
                int ca = ClanMapModel.at(gridX(vx), gridZ(vz));
                int a = owner(ca);
                if (vx + 1 < view) {
                    int cb = ClanMapModel.at(gridX(vx + 1), gridZ(vz));
                    if (a != owner(cb)) {
                        int px = X + (vx + 1) * cell, py = Y + vz * cell;
                        u.fill(px - 1, py, px + 2, py + cell, HALO);
                        u.fill(px, py, px + 1, py + cell, borderColor(a != 0 ? ca : cb, myColor));
                    }
                }
                if (vz + 1 < view) {
                    int cb = ClanMapModel.at(gridX(vx), gridZ(vz + 1));
                    if (a != owner(cb)) {
                        int px = X + vx * cell, py = Y + (vz + 1) * cell;
                        u.fill(px, py - 1, px + cell, py + 2, HALO);
                        u.fill(px, py, px + cell, py + 1, borderColor(a != 0 ? ca : cb, myColor));
                    }
                }
            }
        }
        //joueur : point dore a sa place dans son chunk
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            int vx = viewX(mc.player.chunkPosition().x), vz = viewZ(mc.player.chunkPosition().z);
            if (vx >= 0 && vz >= 0 && vx < view && vz < view) {
                int ppx = X + vx * cell + (int) ((mc.player.getX() - mc.player.chunkPosition().getMinBlockX()) * cell / 16);
                int ppz = Y + vz * cell + (int) ((mc.player.getZ() - mc.player.chunkPosition().getMinBlockZ()) * cell / 16);
                u.fill(ppx - 2, ppz - 2, ppx + 2, ppz + 2, Ui.INK);
                u.fill(ppx - 1, ppz - 1, ppx + 1, ppz + 1, 0xFFFFD24A);
            }
        }
        //case choisie, case survolee
        if (selX != Integer.MIN_VALUE) {
            int vx = viewX(selX), vz = viewZ(selZ);
            if (vx >= 0 && vz >= 0 && vx < view && vz < view) {
                u.frame(X + vx * cell - 1, Y + vz * cell - 1, cell + 2, cell + 2, Ui.GOLD);
            }
        }
        boolean overMap = u.over(X, Y, MAP, MAP) && !u.over(X + 1, Y + 1, 2 * ZOOM_B + 2, ZOOM_B);
        if (overMap) {
            int vx = Math.min(view - 1, (u.mx - X) / cell), vz = Math.min(view - 1, (u.my - Y) / cell);
            u.frame(X + vx * cell, Y + vz * cell, cell, cell, Ui.INK);
        }
        u.s.endClip(u.g);
        if (overMap) {
            int vx = Math.min(view - 1, (u.mx - X) / cell), vz = Math.min(view - 1, (u.my - Y) / cell);
            int ax = ClanMapModel.cx - ClanMapModel.r + gridX(vx), az = ClanMapModel.cz - ClanMapModel.r + gridZ(vz);
            int cw = Math.min(cell, X + MAP - (X + vx * cell)), ch = Math.min(cell, Y + MAP - (Y + vz * cell));
            u.tip(X + vx * cell, Y + vz * cell, cw, ch, describe(ClanMapModel.at(gridX(vx), gridZ(vz)), ax, az));
            if (recap == null && !waiting) {
                //1.9.2 : l'appui commence un trace ; un relachement sur la meme case reste un simple choix de case
                u.click(X + vx * cell, Y + vz * cell, cw, ch, () -> {
                    dragging = true;
                    dragUnclaim = unclaimMode != net.minecraft.client.gui.screens.Screen.hasShiftDown();
                    dragX0 = dragX1 = ax;
                    dragZ0 = dragZ1 = az;
                });
            }
        }
        drag(u);
        //deplacement de la fenetre sur la grille de 31 x 31 (fleches au milieu des bords)
        if (margin > 0) {
            int step = Math.max(1, view / 5);
            arrow(u, X + MAP / 2 - 5, Y + 1, 0, panZ > -margin, () -> panZ = Math.max(-margin, panZ - step));
            arrow(u, X + MAP / 2 - 5, Y + MAP - 11, 1, panZ < margin, () -> panZ = Math.min(margin, panZ + step));
            arrow(u, X + 1, Y + MAP / 2 - 5, 2, panX > -margin, () -> panX = Math.max(-margin, panX - step));
            arrow(u, X + MAP - 11, Y + MAP / 2 - 5, 3, panX < margin, () -> panX = Math.min(margin, panX + step));
        }
        if (panX != 0 || panZ != 0) {
            arrow(u, X + MAP - 11, Y + 1, 4, true, () -> panX = panZ = 0);
            u.tip(X + MAP - 11, Y + 1, 10, 10, Component.translatable("clan.pioneers.ui.map.center"));
        }
        //1.0.4-beta (lot 8) : zoom (6, 8, 12 ou 16 pixels par chunk) en haut a gauche
        zoomButton(u, X + 1, Y + 1, false, zoom > 0, () -> zoom--);
        zoomButton(u, X + 2 + ZOOM_B, Y + 1, true, zoom < ZOOMS.length - 1, () -> zoom++);
        if (recap != null || waiting) {
            areaPanel(u);
        }
    }

    /** 1.0.4-beta (lot 8) : bouton de zoom (papier, signe moins ou plus a l'encre). */
    private static void zoomButton(Ui u, int x, int y, boolean plus, boolean enabled, Runnable r) {
        u.fill(x, y, x + ZOOM_B, y + ZOOM_B, enabled && u.over(x, y, ZOOM_B, ZOOM_B) ? 0xF0E2D2B0 : 0xD0EFE3C8);
        u.frame(x, y, ZOOM_B, ZOOM_B, enabled ? Ui.INK : Ui.FAINT);
        int c = enabled ? Ui.INK : Ui.FAINT;
        u.fill(x + 2, y + 4, x + ZOOM_B - 2, y + 6, c);
        if (plus) {
            u.fill(x + 4, y + 2, x + 6, y + ZOOM_B - 2, c);
        }
        u.tip(x, y, ZOOM_B, ZOOM_B, Component.translatable(plus ? "clan.pioneers.ui.map.zoom_in" : "clan.pioneers.ui.map.zoom_out"));
        if (enabled) {
            u.click(x, y, ZOOM_B, ZOOM_B, r);
        }
    }

    // ------------------------------------------------------------------ 1.9.2 : zone tracee a la souris
    /** Coordonnee de fenetre (0..VIEW-1) d'un chunk absolu en x (ou z avec {@code z}). */
    private static int viewX(int ax) {
        return ax - ClanMapModel.cx + ClanMapModel.r - (ClanMapModel.n - view()) / 2 - panX;
    }

    private static int viewZ(int az) {
        return az - ClanMapModel.cz + ClanMapModel.r - (ClanMapModel.n - view()) / 2 - panZ;
    }

    /** Suit le geste : le coin oppose suit la souris (borne a la fenetre) ; au relachement, choix de case ou apercu de la zone. */
    private static void drag(Ui u) {
        if (!dragging) {
            return;
        }
        int cell = cell(), view = view();
        int vx = Math.max(0, Math.min(view - 1, Math.floorDiv(u.mx - X, cell))), vz = Math.max(0, Math.min(view - 1, Math.floorDiv(u.my - Y, cell)));
        dragX1 = ClanMapModel.cx - ClanMapModel.r + gridX(vx);
        dragZ1 = ClanMapModel.cz - ClanMapModel.r + gridZ(vz);
        int w = Math.abs(dragX1 - dragX0) + 1, h = Math.abs(dragZ1 - dragZ0) + 1;
        boolean big = w * h > fr.pioneers.clan.territory.TerritoryRules.AREA_MAX;
        int color = big ? Ui.MINUS : dragUnclaim ? Ui.MINUS : Ui.GOLD;
        rect(u, dragX0, dragZ0, dragX1, dragZ1, color, (color & 0xFFFFFF) | 0x38000000);
        //etiquette : largeur x hauteur = chunks (ou « trop grand »)
        Component label = big ? Component.translatable("clan.pioneers.ui.area.too_big", fr.pioneers.clan.territory.TerritoryRules.AREA_MAX)
              : Component.translatable("clan.pioneers.ui.area.size", w, h, w * h);
        int lw = Math.min(MAP - 4, u.width(label, Ui.SMALL) + 6);
        int lx = Math.max(X + 2, Math.min(X + MAP - lw - 2, u.mx + 6)), ly = Math.max(Y + 2, Math.min(Y + MAP - 12, u.my - 12));
        u.fill(lx, ly, lx + lw, ly + 10, 0xF0EFE3C8);
        u.frame(lx, ly, lw, 10, color);
        u.fit(label, lx + 3, ly + 2, lw - 6, big ? Ui.MINUS : Ui.TEXT, Ui.SMALL);
        long win = Minecraft.getInstance().getWindow().getWindow();
        if (org.lwjgl.glfw.GLFW.glfwGetMouseButton(win, org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_LEFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS) {
            return;
        }
        dragging = false;
        if (dragX0 == dragX1 && dragZ0 == dragZ1) {
            selX = dragX0;
            selZ = dragZ0;
            return;
        }
        selX = Integer.MIN_VALUE;
        if (!big) {
            ClanUiClient.mapArea(dragX0, dragZ0, dragX1, dragZ1, dragUnclaim, false);
            waiting = true;
            recap = null;
        }
    }

    /** Rectangle de chunks absolus (coins quelconques) sur la fenetre : aplat {@code fillColor} et cadre {@code color}, coupe a la carte. */
    private static void rect(Ui u, int x0, int z0, int x1, int z1, int color, int fillColor) {
        int cell = cell(), view = view();
        int a = Math.max(0, viewX(Math.min(x0, x1))), b = Math.min(view - 1, viewX(Math.max(x0, x1)));
        int c = Math.max(0, viewZ(Math.min(z0, z1))), d = Math.min(view - 1, viewZ(Math.max(z0, z1)));
        if (a > b || c > d) {
            return;
        }
        int x1p = Math.min(X + MAP, X + (b + 1) * cell), y1p = Math.min(Y + MAP, Y + (d + 1) * cell);
        u.fill(X + a * cell, Y + c * cell, x1p, y1p, fillColor);
        u.frame(X + a * cell, Y + c * cell, x1p - (X + a * cell), y1p - (Y + c * cell), color);
    }

    /** Reponse du serveur a une zone : apercu (recapitulatif a confirmer) ou application (fiches rafraichies). */
    private static void areaReply() {
        if (ClanMapModel.areaVersion == areaSeen) {
            return;
        }
        areaSeen = ClanMapModel.areaVersion;
        CompoundTag a = ClanMapModel.area;
        if (!waiting || a == null) {
            return;
        }
        waiting = false;
        if (a.getBoolean("done")) {
            recap = null;
            ClanUiClient.request("territory");
            ClanUiClient.request("sheet");
        } else {
            recap = a;
        }
    }

    /** Verdict de chaque case du rectangle sur la carte, puis le recapitulatif (ou l'attente) au-dessus de la carte. */
    private static void areaPanel(Ui u) {
        //la carte ne reagit plus sous le recapitulatif
        u.click(X, Y, MAP, MAP, () -> {
        });
        if (waiting) {
            rect(u, dragX0, dragZ0, dragX1, dragZ1, dragUnclaim ? Ui.MINUS : Ui.GOLD, 0x30A87C2A);
            Component c = Component.translatable("clan.pioneers.ui.area.waiting");
            int pw = Math.min(MAP - 8, u.width(c, Ui.SMALL) + 12), px = X + (MAP - pw) / 2, py = Y + MAP / 2 - 7;
            u.fill(px, py, px + pw, py + 14, 0xF4EFE3C8);
            u.frame(px, py, pw, 14, Ui.INK);
            u.center(c, px + pw / 2, py + 4, pw - 8, Ui.DIM, Ui.SMALL);
            return;
        }
        CompoundTag a = recap;
        boolean claim = a.getBoolean("claim");
        int x0 = a.getInt("x0"), z0 = a.getInt("z0"), x1 = a.getInt("x1"), z1 = a.getInt("z1");
        byte[] cells = a.getByteArray("cells");
        int w = x1 - x0 + 1;
        int cell = cell(), view = view();
        u.s.beginClip(u.g, X, Y, MAP, MAP);
        for (int i = 0; i < cells.length; i++) {
            int vx = viewX(x0 + i % w), vz = viewZ(z0 + i / w);
            if (vx < 0 || vz < 0 || vx >= view || vz >= view) {
                continue;
            }
            int px = X + vx * cell, py = Y + vz * cell;
            if (cells[i] == 0) {
                u.fill(px, py, px + cell, py + cell, claim ? 0x702F6B30 : 0x709A2E25);
            } else if (cells[i] >= 2) {
                //refuse : croix a l'encre rouge
                for (int k = 1; k < cell - 1; k++) {
                    u.fill(px + k, py + k, px + k + 1, py + k + 1, 0xC09A2E25);
                    u.fill(px + cell - 1 - k, py + k, px + cell - k, py + k + 1, 0xC09A2E25);
                }
            }
        }
        u.s.endClip(u.g);
        rect(u, x0, z0, x1, z1, claim ? Ui.GOLD : Ui.MINUS, 0);
        List<Component[]> rows = new ArrayList<>();
        List<Component> why = new ArrayList<>();
        String error = a.getString("error");
        int ok = a.getInt("ok");
        if (error.isEmpty()) {
            rows.add(new Component[]{Component.translatable(claim ? "clan.pioneers.ui.area.ok_claim" : "clan.pioneers.ui.area.ok_unclaim"),
                  Component.literal(Integer.toString(ok))});
            rows.add(new Component[]{Component.translatable(claim ? "clan.pioneers.ui.area.cost" : "clan.pioneers.ui.area.refund"),
                  Component.translatable("clan.pioneers.ui.area.cost_value", a.getInt("cost"))});
            rows.add(new Component[]{Component.translatable("clan.pioneers.ui.area.left"),
                  Component.translatable("clan.pioneers.ui.area.left_value", a.getInt("left"), a.getInt("budget"))});
            if (a.getInt("skipped") > 0) {
                rows.add(new Component[]{Component.translatable(claim ? "clan.pioneers.ui.area.skipped_claim" : "clan.pioneers.ui.area.skipped_unclaim"),
                      Component.literal(Integer.toString(a.getInt("skipped")))});
            }
            ListTag r = a.getList("refused", Tag.TAG_COMPOUND);
            for (int i = 0; i < r.size(); i++) {
                CompoundTag e = r.getCompound(i);
                why.add(Component.translatable("clan.pioneers.ui.area.why_line", e.getInt("n"),
                      Component.translatable("clan.pioneers.ui.area.why." + e.getString("r"), e.getString("arg"))));
            }
        }
        int pw = 172, px = X + (MAP - pw) / 2;
        int ph = 14 + (error.isEmpty() ? rows.size() * 9 + (why.isEmpty() ? 0 : 10 + Math.min(5, why.size()) * 9) + (ok == 0 ? 9 : 0) : 20) + 19;
        int py = Y + Math.max(4, (MAP - ph) / 2);
        u.fill(px + 2, py + 2, px + pw + 2, py + ph + 2, 0x40000000);
        u.fill(px, py, px + pw, py + ph, 0xF6EFE3C8);
        u.frame(px, py, pw, ph, Ui.INK);
        int y = py + 4;
        u.fit(Component.translatable(claim ? "clan.pioneers.ui.area.title_claim" : "clan.pioneers.ui.area.title_unclaim"), px + 5, y, pw - 10, Ui.INK, 1F);
        y += 10;
        if (!error.isEmpty()) {
            u.wrap(Component.translatable(error, fr.pioneers.clan.territory.TerritoryRules.AREA_MAX), px + 5, y, pw - 10, Ui.MINUS, Ui.SMALL, 2);
            y += 20;
        } else {
            for (Component[] row : rows) {
                u.row(row[0], row[1], px + 5, y, pw - 10, Ui.TEXT);
                y += 9;
            }
            if (!why.isEmpty()) {
                u.separator(px + 5, y + 1, pw - 10);
                y += 3;
                u.fit(Component.translatable("clan.pioneers.ui.area.refused"), px + 5, y, pw - 10, Ui.MINUS, Ui.SMALL);
                y += 7;
                for (int i = 0; i < why.size() && i < 5; i++) {
                    u.fit(why.get(i), px + 5, y + 2, pw - 10, Ui.TEXT, Ui.SMALL);
                    y += 9;
                }
            }
            if (ok == 0) {
                u.fit(Component.translatable("clan.pioneers.ui.area.nothing"), px + 5, y, pw - 10, Ui.DIM, Ui.SMALL);
                y += 9;
            }
        }
        int by = py + ph - 17, bw = (pw - 14) / 2;
        int fx0 = x0, fz0 = z0, fx1 = x1, fz1 = z1;
        if (error.isEmpty() && ok > 0) {
            u.button(px + 5, by, bw, 13, Component.translatable("clan.pioneers.ui.area.confirm", ok), true, () -> {
                ClanUiClient.mapArea(fx0, fz0, fx1, fz1, !claim, true);
                dragX0 = fx0;
                dragZ0 = fz0;
                dragX1 = fx1;
                dragZ1 = fz1;
                dragUnclaim = !claim;
                waiting = true;
                recap = null;
            });
            u.button(px + pw - 5 - bw, by, bw, 13, Component.translatable("clan.pioneers.ui.area.cancel"), true, () -> recap = null);
        } else {
            u.button(px + (pw - bw) / 2, by, bw, 13, Component.translatable("clan.pioneers.ui.area.close"), true, () -> recap = null);
        }
    }

    /** Petit bouton de deplacement de la carte (papier, fleche a l'encre dessinee pixel par pixel). */
    private static void arrow(Ui u, int x, int y, int dir, boolean enabled, Runnable r) {
        u.fill(x, y, x + 10, y + 10, enabled && u.over(x, y, 10, 10) ? 0xF0E2D2B0 : 0xD0EFE3C8);
        u.frame(x, y, 10, 10, enabled ? Ui.INK : Ui.FAINT);
        int c = enabled ? Ui.INK : Ui.FAINT;
        for (int i = 0; i < 3; i++) {
            switch (dir) {
                case 0 -> u.fill(x + 4 - i, y + 3 + i, x + 6 + i, y + 4 + i, c);
                case 1 -> u.fill(x + 4 - i, y + 6 - i, x + 6 + i, y + 7 - i, c);
                case 2 -> u.fill(x + 3 + i, y + 4 - i, x + 4 + i, y + 6 + i, c);
                case 3 -> u.fill(x + 6 - i, y + 4 - i, x + 7 - i, y + 6 + i, c);
                default -> u.fill(x + 4, y + 4, x + 6, y + 6, c);
            }
        }
        if (enabled) {
            u.click(x, y, 10, 10, r);
        }
    }

    /** 1.0.4-beta (lot 8) : couleur (eclaircie) du clan de la case, sur un lisere sombre {@link #HALO} : frontieres lisibles sur le terrain. */
    private static int borderColor(int cell, int myColor) {
        int k = ClanMapModel.kind(cell);
        if (k == FOG || k == FREE) {
            return 0xE0243660;
        }
        ClanMapModel.Owner o = ClanMapModel.owner(cell);
        return 0xFF000000 | Ui.tint(k == OWN ? myColor : o == null ? 8 : o.color());
    }

    private static int owner(int cell) {
        int k = ClanMapModel.kind(cell);
        return k == FOG || k == FREE ? 0 : (cell >>> ClanMapModel.OWNER_SHIFT & 255) * 16 + k;
    }

    /** Infobulle d'une case : coordonnees, proprietaire (clan), relation, etats, zone. */
    static List<Component> describe(int cell, int ax, int az) {
        List<Component> l = new ArrayList<>();
        l.add(Component.translatable("clan.pioneers.ui.map.chunk", ax, az, ax * 16, az * 16)); //1.0.4-beta (lot 8) : chunk et blocs
        int k = ClanMapModel.kind(cell);
        if (k == FOG) {
            l.add(Component.translatable("clan.pioneers.ui.legend.fog"));
            return l;
        }
        ClanMapModel.Owner o = ClanMapModel.owner(cell);
        if (o != null) {
            Component name = Ui.clanName(o.name(), o.soloOf());
            l.add(o.tag().isBlank() ? name : Component.literal("[" + o.tag() + "] ").append(name));
        }
        String base = switch (k) {
            case OWN -> "own";
            case ALLY -> "ally";
            case ENEMY -> "enemy";
            case NEUTRAL -> "neutral";
            case PLOT -> "plot";
            default -> "free";
        };
        l.add(Component.translatable("clan.pioneers.ui.legend." + base));
        int[] flags = {OUTPOST, DECLINE, CAPTURED, SIEGE, LOADED, FEDERAL, RALLIED, HALL, TOWER};
        String[] names = {"outpost", "decline", "captured", "siege", "loaded", "federal", "rallied", "hall", "tower"};
        for (int i = 0; i < flags.length; i++) {
            if ((cell & flags[i]) != 0) {
                l.add(Component.literal("• ").append(Component.translatable("clan.pioneers.ui.legend." + names[i])));
            }
        }
        if (k == OWN) {
            l.add(Component.translatable("clan.pioneers.ui.map.zone", Component.translatable("clan.pioneers.zone." + ZONES[((cell >> ZONE_SHIFT) & 3) % 3])));
        }
        return l;
    }

    // ------------------------------------------------------------------ colonne de droite : budget et legende
    private static void side(Ui u, @Nullable CompoundTag t, int myColor) {
        int x = PX, w = X + W - PX, y = Y;
        if (t != null && t.getBoolean("clan")) {
            //1.0.3-beta (lot 16) : pastilles Carte / Reglages en tete ; le budget dit sa part achetee en infobulle
            viewChips(u, t);
            y = Y + VIEW_H + 3;
            //1.0.5-beta (lot 20, retour 10) : quatre faits a icone en carre (valeur seule, libelle et detail en infobulle)
            int cw = (w - 2) / 2;
            cell(u, "ico_chunk", Component.literal(t.getInt("claims") + " / " + t.getInt("budget")), x, y, cw, t.getInt("claims") > t.getInt("budget")
                  ? Ui.MINUS : Ui.TEXT, Component.translatable("clan.pioneers.ui.territory.claims"), Component.translatable(
                  "clan.pioneers.ui.territory.claims_tip", t.getInt("free"), t.getInt("bought")));
            cell(u, "ico_place", Component.literal(t.getInt("outposts") + " / " + t.getInt("maxOutposts")), x + cw + 2, y, w - cw - 2, Ui.TEXT,
                  Component.translatable("clan.pioneers.ui.territory.outposts"));
            //1.0.5-beta (lot 13) : plus de claims charges d'office ; emplacements payes a la pose (prix du prochain) + cout du jour
            //1.0.5-beta (lot 20) : les chunks en attente passent dans l'infobulle
            Component loaded = Component.literal(t.getInt("loaded") + " / " + t.getInt("slots"));
            cell(u, "perm_chunkload", loaded, x, y + Ui.FACT, cw, Ui.TEXT, Component.translatable("clan.pioneers.ui.territory.loaded"),
                  Component.translatable("clan.pioneers.ui.territory.slot_price_tip", Ui.group(t.getLong("nextPrice"))),
                  Component.translatable("clan.pioneers.ui.territory.loaded_tip", t.getLong("cost"), Ui.group(t.getLong("daily")), t.getInt("global"),
                        t.getInt("globalMax")), Component.translatable("clan.pioneers.ui.territory.queued_tip", t.getInt("queued")));
            ListTag fns = t.getList("functions", Tag.TAG_COMPOUND);
            List<Component> tip = new ArrayList<>();
            tip.add(Component.translatable("clan.pioneers.ui.territory.functions"));
            for (int i = 0; i < fns.size(); i++) {
                CompoundTag f = fns.getCompound(i);
                tip.add(Component.translatable("clan.pioneers.ui.territory.function_line", Component.translatable("clan.pioneers.function."
                      + f.getString("fn")), f.getInt("x"), f.getInt("y"), f.getInt("z")));
            }
            cell(u, "perm_seals", Component.literal(Integer.toString(fns.size())), x + cw + 2, y + Ui.FACT, w - cw - 2, Ui.TEXT, tip.toArray(new Component[0]));
            y += 2 * Ui.FACT;
        } else if (t != null) {
            y += u.wrap(Component.translatable("clan.pioneers.ui.territory.no_clan", t.getInt("budget")), x, y, w, Ui.DIM, Ui.SMALL, 4);
        }
        y = Math.max(y + 4, Y + 40);
        u.separator(x, y - 3, w);
        u.fit(Component.translatable("clan.pioneers.ui.legend"), x, y, w, Ui.INK, Ui.SMALL);
        y += 9;
        for (String id : LEGEND) {
            int dye = "own".equals(id) ? myColor : "ally".equals(id) ? 5 : "enemy".equals(id) ? 14 : "neutral".equals(id) ? 1 : 8;
            drawTile(u, id, x, y, CELL, dye);
            u.fit(Component.translatable("clan.pioneers.ui.legend." + id), x + CELL + 3, y + 1, w - CELL - 3, Ui.TEXT, Ui.SMALL);
            u.tip(x, y, w, 8, Component.translatable("clan.pioneers.ui.legend." + id), Component.translatable("clan.pioneers.ui.legend." + id + ".desc"));
            y += 9;
        }
    }

    /** 1.0.5-beta (lot 20) : fait a icone de la colonne de droite (valeur en petit), libelle et detail en infobulle. */
    private static void cell(Ui u, String icon, Component value, int x, int y, int w, int color, Component... tip) {
        u.icon12(Ui.gui(icon), x, y);
        u.fit(value, x + Ui.GAP - 1, y + 3, w - Ui.GAP + 1, color, Ui.SMALL);
        u.tip(x, y, w, Ui.ICON, tip);
    }

    /** Alerte du territoire (chunkloading suspendu ou bientot arrete, chunks en declin), ou null. */
    @Nullable
    private static Component warning(@Nullable CompoundTag t) {
        if (t == null || !t.getBoolean("clan")) {
            return null;
        }
        if (t.getBoolean("suspended")) {
            return Component.translatable("clan.pioneers.ui.territory.suspended");
        }
        if (t.getLong("stopAt") > 0) {
            return Component.translatable("clan.pioneers.ui.territory.stop_at", Ui.duration(t.getLong("stopAt") - ClanUiClient.now()));
        }
        if (t.getInt("declining") > 0) {
            return Component.translatable("clan.pioneers.ui.territory.declining", t.getInt("declining"));
        }
        return null;
    }

    // ------------------------------------------------------------------ actions sur la case choisie
    private static void actions(Ui u, @Nullable CompoundTag t) {
        int y = Y + MAP + 1;
        if (selX == Integer.MIN_VALUE || !ClanMapModel.ready()) {
            Component warn = warning(t);
            if (warn != null) {
                u.fit(Component.literal("− ").append(warn), X, y, MAP, Ui.MINUS, Ui.SMALL);
                y += 9;
            }
            //1.9.2 : un clic choisit une case, un glisse trace une zone ; mode du trace (Maj inverse le mode pendant le geste)
            u.fit(Component.translatable("clan.pioneers.ui.area.hint"), X, y, MAP, Ui.DIM, Ui.SMALL);
            if (warn == null && ClanMapModel.ready()) {
                int cw = (MAP - 2) / 2;
                u.chip(X, y + 8, cw, 13, Component.translatable("clan.pioneers.ui.area.mode_claim"), !unclaimMode, true, () -> unclaimMode = false);
                u.chip(X + cw + 2, y + 8, MAP - cw - 2, 13, Component.translatable("clan.pioneers.ui.area.mode_unclaim"), unclaimMode, true,
                      () -> unclaimMode = true);
            }
            return;
        }
        int gx = selX - ClanMapModel.cx + ClanMapModel.r, gz = selZ - ClanMapModel.cz + ClanMapModel.r;
        int cell = ClanMapModel.at(gx, gz);
        int k = ClanMapModel.kind(cell);
        List<Component> d = describe(cell, selX, selZ);
        Component info = d.get(0);
        for (int i = 1; i < d.size() && i < 3; i++) {
            info = info.copy().append(" · ").append(d.get(i));
        }
        u.fit(info, X, y, MAP, Ui.TEXT, Ui.SMALL);
        //1.0.5-beta (lot 13) : plus de claims charges d'office : toujours le bouton Charger / Liberer
        boolean slots = true;
        int by = y + 8, bw = slots ? (MAP - 6) / 4 : (MAP - 4) / 3;
        boolean inside = gx >= 0 && gz >= 0 && gx < ClanMapModel.n && gz < ClanMapModel.n;
        boolean claim = t != null && t.getBoolean("p_claim"), zones = t != null && t.getBoolean("p_zones"), load = t != null && t.getBoolean("p_chunkload");
        int ax = selX, az = selZ;
        u.button(X, by, bw, 13, Component.translatable("clan.pioneers.ui.map.claim"), inside && claim && k == FREE, () -> {
            ClanUiClient.mapAct("claim", ax, az, null);
            ClanUiClient.request("territory");
            ClanUiClient.request("sheet");
        });
        //1.0.5-beta (lot 20) : rendre un claim est irreversible (il a ete paye) : deux clics
        u.confirm("unclaim:" + ax + ":" + az, X + bw + 2, by, bw, 13, null, Component.translatable("clan.pioneers.ui.map.unclaim"),
              inside && claim && k == OWN, () -> {
                  ClanUiClient.mapAct("unclaim", ax, az, null);
                  ClanUiClient.request("territory");
              });
        int zone = ((cell >> ZONE_SHIFT) & 3) % 3;
        String next = ZONES[(zone + 1) % 3];
        u.button(X + 2 * (bw + 2), by, slots ? bw : MAP - 2 * (bw + 2), 13, Component.translatable("clan.pioneers.ui.map.zone_button"), inside && zones && k == OWN, () -> {
            CompoundTag e = new CompoundTag();
            e.putString("zone", next);
            e.putInt("rank", t == null ? 0 : t.getInt("myRank"));
            ClanUiClient.mapAct("zone", ax, az, e);
        });
        if (k == OWN) {
            u.tip(X + 2 * (bw + 2), by, bw, 13, Component.translatable("clan.pioneers.ui.map.zone_next", Component.translatable("clan.pioneers.zone." + next)));
        }
        if (!slots) {
            return;
        }
        boolean loaded = (cell & LOADED) != 0;
        u.button(X + 3 * (bw + 2), by, MAP - 3 * (bw + 2), 13, Component.translatable(loaded ? "clan.pioneers.ui.map.unload" : "clan.pioneers.ui.map.load"),
              inside && load && k == OWN, () -> {
                  ClanUiClient.mapAct(loaded ? "unload" : "load", ax, az, null);
                  ClanUiClient.request("territory");
              });
    }
}
