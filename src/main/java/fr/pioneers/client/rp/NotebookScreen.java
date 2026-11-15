package fr.pioneers.client.rp;

import fr.pioneers.client.clan.ui.ClanUiClient;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.PioneerDefs;
import fr.pioneers.core.profile.PioneerProfile;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

/**
 * 1.9 (MAJ RP) : le Carnet du Restaurateur (touche J ou l'objet) : onglets Profil, Skills, Codex, puis ceux des autres blocs
 * (Reputation, Bourse, Clan), ajoutes par {@link #register}. Un onglet dont l'interrupteur est coupe n'apparait pas. 1.12.0 (lot E4) :
 * l'onglet Allure est retire (barbe et coiffure : skin du joueur ; habillage : panneau cosmetique de l'inventaire).
 */
public class NotebookScreen extends PioneerScreen {

    /** Un onglet du Carnet (les blocs 3 et 4 ajoutent les leurs dans ce paquet). */
    public interface Tab {

        String id();

        default boolean available() {
            return true;
        }

        void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt);

        default void opened(NotebookScreen s) {
        }
    }

    private static final List<Tab> TABS = new ArrayList<>();
    private String tab;
    private int chapter = 1;

    public static void register(Tab t) {
        TABS.removeIf(x -> x.id().equals(t.id()));
        TABS.add(t);
    }

    static {
        register(new Simple("profile", NotebookScreen::drawProfile));
        register(new Simple("skills", NotebookScreen::drawSkills));
        register(new Simple("codex", NotebookScreen::drawCodex));
        //bloc 3 : Bourse et Reputation
        register(new LedgerTab());
        //1.0.4-beta (lot 7) : contrats entre joueurs et factures, juste apres la Bourse
        //1.0.5-beta (lot 26, retour 14) : l'onglet Contrats devient l'onglet DOCUMENTS (meme place, meme identifiant ; les contrats au temps
        //reel d'avant y sont emboites : client/economy/ContractsTab)
        register(new fr.pioneers.client.economy.DocumentsTab());
        register(new ReputationTab());
    }

    private record Simple(String id, Drawer drawer) implements Tab {

        @Override
        public void draw(NotebookScreen s, GuiGraphics g, int mx, int my, float pt) {
            drawer.draw(s, g, mx, my);
        }
    }

    private interface Drawer {

        void draw(NotebookScreen s, GuiGraphics g, int mx, int my);
    }

    public NotebookScreen(@Nullable String tab) {
        super(Component.translatable("item.pioneers.restorer_notebook"));
        this.tab = tab == null || "allure".equals(tab) ? "profile" : tab; //1.12.0 : l'onglet Allure n'existe plus
    }

    private List<Tab> visible() {
        List<Tab> out = new ArrayList<>();
        for (Tab t : TABS) {
            if (t.available()) {
                out.add(t);
            }
        }
        return out;
    }

    public void open(String id) {
        tab = id;
        scroll[0] = scroll[1] = 0;
        for (net.minecraft.client.gui.components.AbstractWidget w : tabWidgets) {
            removeWidget(w);
        }
        tabWidgets.clear();
        for (Tab t : TABS) {
            if (t.id().equals(id)) {
                t.opened(this);
            }
        }
    }

    /** Champs de saisie d'un onglet (retires quand on change d'onglet), en coordonnees de la page. */
    private final List<net.minecraft.client.gui.components.AbstractWidget> tabWidgets = new ArrayList<>();

    public <T extends net.minecraft.client.gui.components.AbstractWidget> T tabWidget(T w) {
        w.setX(left + w.getX());
        w.setY(top + w.getY());
        tabWidgets.add(w);
        return addRenderableWidget(w);
    }

    public net.minecraft.client.gui.Font font() {
        return font;
    }

    @Override
    protected void init() {
        super.init();
        //1.0.4-beta (lot 8) : l'en-tete du Profil montre le clan et le role (fiche « sheet » demandee a l'ouverture)
        if (PioneerApi.hasFeature("clans")) {
            ClanUiClient.askSheet();
        }
        tabWidgets.clear();
        for (Tab t : TABS) {
            if (t.id().equals(tab)) {
                t.opened(this);
            }
        }
    }

    /** 1.9.1 : barre d'onglets (bord gauche, largeur, haut, hauteur, ecart, marge du libelle). */
    public static final int TABS_X = 10, TABS_W = PAGE_W - 20, TABS_Y = 6, TAB_H = 13, TAB_GAP = 2, TAB_PAD = 4;
    /**
     * 1.0.5-beta (lot 16, retours de Guillaume : « icones explicites ») : chaque onglet porte l'icone de sa notion (style des tuiles
     * {@code stat/*}), {@link #TAB_ICON} px plus {@link #TAB_ICON_GAP} avant le libelle. Onglet inconnu : sans icone.
     */
    public static final int TAB_ICON = 8, TAB_ICON_GAP = 1;
    public static final java.util.Map<String, String> TAB_ICONS = java.util.Map.of("profile", "profile", "skills", "levels", "codex", "codex",
          "market", "shining", "contracts", "contract", "reputation", "reputation", "clan", "clan");

    /**
     * 1.9.1 (retours de Guillaume : « l'onglet Savoir-faire est plus petit que les autres ») : chaque onglet est large de son libelle plus
     * une marge, la place qui reste est partagee a parts egales ; si la barre deborde, UNE echelle commune (0,75 au moins) pour tous les
     * libelles. Avant : largeurs egales, et seul le libelle trop long etait reduit.
     */
    static int[] tabLayout(net.minecraft.client.gui.Font font, List<Component> labels, float[] scaleOut) {
        int n = labels.size();
        int[] w = new int[n];
        int sum = 0;
        for (int i = 0; i < n; i++) {
            w[i] = font.width(labels.get(i));
            sum += w[i];
        }
        int fixed = n * (TAB_PAD + TAB_ICON + TAB_ICON_GAP) + (n - 1) * TAB_GAP;
        float k = Math.max(RpGui.MIN_SCALE, Math.min(1F, (TABS_W - fixed - n) / (float) Math.max(1, sum))); //1.0.5 (lot 16) : - n, arrondis au pixel
        scaleOut[0] = k;
        int used = 0;
        for (int i = 0; i < n; i++) {
            w[i] = (int) Math.ceil(w[i] * k) + TAB_PAD + TAB_ICON + TAB_ICON_GAP;
            used += w[i];
        }
        int extra = Math.max(0, TABS_W - (n - 1) * TAB_GAP - used);
        for (int i = 0; i < n; i++) {
            w[i] += extra / n + (i < extra % n ? 1 : 0);
        }
        return w;
    }

    @Override
    protected void drawContents(GuiGraphics g, int mx, int my, float pt) {
        List<Tab> tabs = visible();
        List<Component> labels = new ArrayList<>();
        for (Tab t : tabs) {
            labels.add(Component.translatable("pioneer.pioneers.tab." + t.id()));
        }
        float[] k = new float[1];
        int[] w = tabLayout(font, labels, k);
        int x = TABS_X;
        for (int i = 0; i < tabs.size(); i++) {
            Tab t = tabs.get(i);
            //pastille sans libelle (dessine apres, decale de l'icone) : meme fond et meme clic que les autres pastilles
            boolean sel = t.id().equals(tab);
            chip(g, mx, my, x, TABS_Y, w[i], TAB_H, Component.empty(), sel, true, k[0], () -> open(t.id()));
            int lw = (int) Math.ceil(font.width(labels.get(i)) * k[0]), cx = x + (w[i] - lw - TAB_ICON - TAB_ICON_GAP) / 2;
            String ic = TAB_ICONS.get(t.id());
            if (ic != null) {
                icon(g, RpGui.stat(ic), cx, TABS_Y + 3, TAB_ICON);
            }
            fit(g, labels.get(i), cx + TAB_ICON + TAB_ICON_GAP, TABS_Y + (TAB_H - 8) / 2, w[i] - TAB_ICON - TAB_ICON_GAP - 4, sel ? INK : INK_LIGHT, k[0]);
            x += w[i] + TAB_GAP;
        }
        for (Tab t : tabs) {
            if (t.id().equals(tab)) {
                t.draw(this, g, mx, my, pt);
            }
        }
    }

    // ------------------------------------------------------------------ Profil (1.0.4-beta, lot 8 : refait)
    /**
     * 1.0.4-beta (lot 8, Guillaume : « ameliorer le design du carnet d'identite : moins verbeux, plus explicite, trop de texte partout et
     * d'infos cachees ») : EN-TETE (portrait, nom, vocation, clan et role ; a droite l'avantage et le defaut, la Brigade), TUILES des chiffres
     * cles (jours, niveaux, Shining, Maitrises, ecrits lus), POINTS FORTS (les trois meilleurs savoir-faire, niveau, barre, XP) et MAITRISES
     * nommees, JOURNAL compact (jour en marge). Les textes longs (phrase de la vocation, descriptions) passent en infobulle. Maquette fidele :
     * {@code T:rp/pioneer_ui_preview.py} (memes constantes).
     */
    public static final int HEAD_X = 10, HEAD_Y = 24, HEAD_W = PAGE_W - 20, HEAD_H = 46, PORT_X = 13, PORT_Y = 26, PORT_W = 34, PORT_H = 42,
          ID_X = 52, TRAIT_X = 232, TILES_Y = 74, STAT_H = 24, STAT_GAP = 3, STRONG_Y = 101, STRONG_ROW = 16, MAST_X = 196, JOURNAL_Y = 162,
          JOURNAL_ROW = 9, DAY_W = 30;
    public static final String[] STATS = {"days", "levels", "shining", "masteries", "codex"};

    private static void drawProfile(NotebookScreen s, GuiGraphics g, int mx, int my) {
        PioneerProfile p = RpClient.profile();
        Minecraft mc = Minecraft.getInstance();
        // en-tete
        g.fill(HEAD_X, HEAD_Y, HEAD_X + HEAD_W, HEAD_Y + HEAD_H, 0x12243660);
        s.frame(g, HEAD_X, HEAD_Y, HEAD_W, HEAD_H, FAINT);
        g.fill(PORT_X, PORT_Y, PORT_X + PORT_W, PORT_Y + PORT_H, 0x14243660);
        s.frame(g, PORT_X, PORT_Y, PORT_W, PORT_H, FAINT);
        if (mc.player != null) {
            int cx = PORT_X + PORT_W / 2;
            float yaw = (float) Math.toDegrees(Math.atan((mx - cx) / 60.0)) * 0.6F;
            s.portrait(g, mc.player, PORT_X, PORT_Y, PORT_W, PORT_H, yaw);
        }
        int idw = TRAIT_X - 6 - ID_X;
        s.fit(g, Component.literal(p.name), ID_X, HEAD_Y + 3, idw, INK);
        PioneerDefs.Vocation v = PioneerDefs.get().vocation(p.vocation);
        if (v != null) {
            s.icon(g, RpGui.emblem(v.id()), ID_X, HEAD_Y + 14, 16);
            int vw = s.fit(g, RpGui.vocationName(v.id()), ID_X + 19, HEAD_Y + 18, idw - 19, INK_LIGHT, 1F);
            s.tooltip(mx, my, ID_X, HEAD_Y + 14, 19 + vw, 16, RpGui.vocationTooltip(v));
        }
        String[] clan = ClanUiClient.mine();
        if (clan != null) {
            int dye = Integer.parseInt(clan[3]);
            Component line = clan[4].isEmpty() ? Component.literal((clan[1].isBlank() ? "" : "[" + clan[1] + "] ") + clan[0] + " · ")
                  .append(roleText(clan[2])) : Component.translatable("pioneer.pioneers.solo");
            s.fit(g, line, ID_X, HEAD_Y + 35, idw, clan[4].isEmpty() ? clanInk(dye) : DIM, RpGui.MIN_SCALE);
        } else if (ClanUiClient.sheetKnown() || !PioneerApi.hasFeature("clans")) {
            s.fit(g, Component.translatable("pioneer.pioneers.no_clan"), ID_X, HEAD_Y + 35, idw, DIM, RpGui.MIN_SCALE);
        }
        int tw = HEAD_X + HEAD_W - 4 - TRAIT_X;
        if (v != null && v.neutral()) {
            s.wrap(g, Component.translatable("pioneer.pioneers.neutral_short"), TRAIT_X, HEAD_Y + 5, tw, DIM, RpGui.MIN_SCALE, 2);
        } else if (v != null) {
            int aw = s.fit(g, Component.literal("+ ").append(Component.translatable("trait.pioneers." + v.advantage().id())), TRAIT_X, HEAD_Y + 5, tw,
                  PLUS, RpGui.MIN_SCALE);
            s.tooltip(mx, my, TRAIT_X, HEAD_Y + 4, aw, 9, Component.translatable("trait.pioneers." + v.advantage().id() + ".desc"));
            int dw = s.fit(g, Component.literal("− ").append(Component.translatable("trait.pioneers." + v.defect().id())), TRAIT_X, HEAD_Y + 15, tw,
                  MINUS, RpGui.MIN_SCALE);
            s.tooltip(mx, my, TRAIT_X, HEAD_Y + 14, dw, 9, Component.translatable("trait.pioneers." + v.defect().id() + ".desc"));
        }
        if (RpClient.brigade > 0) {
            s.fit(g, Component.translatable("pioneer.pioneers.brigade", RpClient.brigade), TRAIT_X, HEAD_Y + 35, tw, PLUS, RpGui.MIN_SCALE);
        }
        // tuiles des chiffres cles
        long days = Math.max(0, (mc.level == null ? 0 : mc.level.getDayTime() / 24000L) - p.createdDay);
        int total = 0;
        for (PioneerDefs.Skill sk : PioneerDefs.get().skills.values()) {
            total += PioneerApi.levelOf(p.xp(sk.id()));
        }
        List<String> stats = new ArrayList<>();
        for (String st : STATS) {
            if (!"shining".equals(st) || PioneerApi.hasFeature("economy")) {
                stats.add(st);
            }
        }
        int n = stats.size(), tileW = (HEAD_W - (n - 1) * STAT_GAP) / n;
        for (int i = 0; i < n; i++) {
            String st = stats.get(i);
            int x = HEAD_X + i * (tileW + STAT_GAP), w = i == n - 1 ? HEAD_X + HEAD_W - x : tileW;
            String value = switch (st) {
                case "days" -> Long.toString(days);
                case "levels" -> Integer.toString(total);
                case "shining" -> LedgerTab.group(p.balance);
                case "masteries" -> Integer.toString(p.masteries.size());
                default -> Integer.toString(p.loreRead.size() + p.notesRead.size());
            };
            tile(s, g, mx, my, x, TILES_Y, w, st, Component.literal(value), Component.translatable("pioneer.pioneers.stat." + st),
                  "codex".equals(st) ? Component.translatable("pioneer.pioneers.stat.codex.tip", p.loreRead.size(), p.notesRead.size())
                        : Component.translatable("pioneer.pioneers.stat." + st + ".tip"));
        }
        // points forts : les trois meilleurs savoir-faire
        int half = MAST_X - 8 - HEAD_X;
        s.fit(g, Component.translatable("pioneer.pioneers.strengths"), HEAD_X, STRONG_Y, half, INK, RpGui.MIN_SCALE);
        s.separator(g, HEAD_X, STRONG_Y + 8, half);
        List<PioneerDefs.Skill> best = new ArrayList<>(PioneerDefs.get().orderedSkills());
        best.removeIf(sk -> !PioneerApi.hasFeature(sk.feature()));
        best.sort((a, b) -> Double.compare(p.xp(b.id()), p.xp(a.id())));
        for (int i = 0; i < 3 && i < best.size(); i++) {
            PioneerDefs.Skill sk = best.get(i);
            int y = STRONG_Y + 10 + i * STRONG_ROW;
            skillLine(s, g, mx, my, sk, p, HEAD_X, y, half, v);
        }
        // Maitrises nommees
        int mw = HEAD_X + HEAD_W - MAST_X;
        s.fit(g, Component.translatable("pioneer.pioneers.masteries_title"), MAST_X, STRONG_Y, mw, INK, RpGui.MIN_SCALE);
        s.separator(g, MAST_X, STRONG_Y + 8, mw);
        List<String> ms = new ArrayList<>(p.masteries);
        if (ms.isEmpty()) {
            s.wrap(g, Component.translatable("pioneer.pioneers.no_mastery"), MAST_X, STRONG_Y + 13, mw, DIM, RpGui.MIN_SCALE, 2);
        }
        for (int i = 0; i < ms.size() && i < 3; i++) {
            int y = STRONG_Y + 10 + i * STRONG_ROW;
            if (i == 2 && ms.size() > 3) {
                s.fit(g, Component.translatable("pioneer.pioneers.more_count", ms.size() - 2), MAST_X + 2, y + 4, mw - 2, DIM, RpGui.MIN_SCALE);
                List<Component> tip = new ArrayList<>();
                for (int k = 2; k < ms.size(); k++) {
                    tip.add(Component.translatable("pioneer.pioneers.mastery_title", Component.translatable("skill.pioneers." + ms.get(k) + ".mastery")));
                }
                s.tooltip(mx, my, MAST_X, y, mw, 14, tip);
                break;
            }
            s.icon(g, RpGui.clasp(ms.get(i)), MAST_X, y, 16);
            s.fit(g, Component.translatable("pioneer.pioneers.mastery_title", Component.translatable("skill.pioneers." + ms.get(i) + ".mastery")),
                  MAST_X + 19, y + 4, mw - 19, TEXT, RpGui.MIN_SCALE);
        }
        // journal compact : jour en marge, derniere ligne en haut
        s.fit(g, Component.translatable("pioneer.pioneers.journal"), HEAD_X, JOURNAL_Y, HEAD_W, INK, RpGui.MIN_SCALE);
        s.separator(g, HEAD_X, JOURNAL_Y + 8, HEAD_W);
        int x = HEAD_X, top = JOURNAL_Y + 11, w = HEAD_W, h = PAGE_H - 6 - top;
        s.scrollArea(0, x, top, w, h, p.journal.size() * JOURNAL_ROW);
        s.beginClip(g, x, top, w, h);
        int yy = top - s.scroll[0];
        for (int i = p.journal.size() - 1; i >= 0; i--) {
            PioneerProfile.JournalLine l = p.journal.get(i);
            if (yy + JOURNAL_ROW > top && yy < top + h) {
                Object[] args = new Object[l.args().size()];
                for (int j = 0; j < args.length; j++) {
                    String a = l.args().get(j);
                    args[j] = a.startsWith("#") ? Component.translatable(a.substring(1)) : a;
                }
                s.fitRight(g, Component.translatable("pioneer.pioneers.day_tag", l.day()), x + DAY_W - 4, yy, DAY_W - 4, DIM, RpGui.MIN_SCALE);
                s.fit(g, Component.translatable(l.key(), args), x + DAY_W, yy, w - DAY_W, TEXT, RpGui.MIN_SCALE);
            }
            yy += JOURNAL_ROW;
        }
        s.endClip(g);
    }

    /** Tuile d'un chiffre cle : icone, valeur en grand, libelle court dessous, explication en infobulle. */
    private static void tile(NotebookScreen s, GuiGraphics g, int mx, int my, int x, int y, int w, String id, Component value, Component label,
          Component tip) {
        g.fill(x, y, x + w, y + STAT_H, 0x10243660);
        s.frame(g, x, y, w, STAT_H, FAINT);
        s.icon(g, RpGui.stat(id), x + 4, y + 4, 16);
        s.fit(g, value, x + 23, y + 3, w - 26, INK);
        s.fit(g, label, x + 23, y + 14, w - 26, DIM, RpGui.MIN_SCALE);
        s.tooltip(mx, my, x, y, w, STAT_H, tip);
    }

    /** Ligne d'un savoir-faire (Points forts) : icone, nom, niveau a droite, barre et XP sous le nom. */
    private static void skillLine(NotebookScreen s, GuiGraphics g, int mx, int my, PioneerDefs.Skill sk, PioneerProfile p, int x, int y, int w,
          @Nullable PioneerDefs.Vocation v) {
        double xp = p.xp(sk.id());
        int lvl = PioneerApi.levelOf(xp);
        s.icon(g, RpGui.skillIcon(sk.id()), x, y, 16);
        int bx = x + 19, right = x + w;
        int lw = s.fitRight(g, Component.translatable("pioneer.pioneers.lvl_short", lvl), right, y, 40, lvl >= PioneerApi.MAX_LEVEL ? GOLD : INK, 1F);
        s.fit(g, RpGui.skillName(sk.id()), bx, y, right - lw - 4 - bx, INK, 1F);
        double lo = PioneerApi.xpFor(lvl), hi = PioneerApi.xpFor(Math.min(PioneerApi.MAX_LEVEL, lvl + 1));
        double frac = lvl >= PioneerApi.MAX_LEVEL ? 1 : (xp - lo) / Math.max(1, hi - lo);
        //1.0.5-beta (lot 19, retour 10 : « moins verbeux, detail en infobulle ») : l'XP chiffree passe dans l'infobulle, la barre prend la place
        s.bar(g, bx, y + 11, right - bx, 4, frac, lvl >= PioneerApi.MAX_LEVEL ? GOLD : INK_LIGHT);
        boolean affine = v != null && v.affine(sk.id());
        List<Component> tip = new ArrayList<>();
        tip.add(RpGui.skillName(sk.id()));
        tip.add(xpTip(lvl, xp, hi));
        if (affine) {
            tip.add(Component.translatable("pioneer.pioneers.affinity").withStyle(st -> st.withColor(0xD9B25A)));
        }
        for (Component c : RpGui.skillEffects(sk, lvl)) {
            tip.add(Component.translatable("pioneer.pioneers.effect_now", c));
        }
        s.tooltip(mx, my, x, y, w, 16, tip);
    }

    /** 1.0.5-beta (lot 19) : ligne d'infobulle « Niveau n · x / y XP » (ou « Niveau 10 · maitrise »). */
    private static Component xpTip(int lvl, double xp, double hi) {
        return (lvl >= PioneerApi.MAX_LEVEL ? Component.translatable("pioneer.pioneers.xp_tip_max", lvl)
              : Component.translatable("pioneer.pioneers.xp_tip", lvl, (int) xp, (int) hi)).withStyle(st -> st.withColor(0xA0A0A0));
    }

    /** Role du clan (nom choisi, ou cle de langue d'un role propose prefixee de « # »). */
    private static Component roleText(String role) {
        return role.startsWith("#") ? Component.translatable(role.substring(1)) : Component.literal(role);
    }

    /** Encre lisible de la couleur d'un clan (teinture 0..15) sur le papier. */
    private static int clanInk(int dye) {
        int v = net.minecraft.world.item.DyeColor.byId(dye & 15).getFireworkColor();
        int r = v >> 16 & 255, gr = v >> 8 & 255, b = v & 255;
        double lum = (0.299 * r + 0.587 * gr + 0.114 * b) / 255;
        if (lum > 0.55) {
            double k = 0.55 / lum;
            r = (int) (r * k);
            gr = (int) (gr * k);
            b = (int) (b * k);
        }
        return 0xFF000000 | r << 16 | gr << 8 | b;
    }

    // ------------------------------------------------------------------ Savoir-faire (1.0.4-beta, lot 8 : refait)
    /**
     * 1.0.4-beta (lot 8) : en-tete (niveaux cumules, derniers gains), puis deux colonnes de huit : icone,
     * nom, niveau en chiffre (plus de « Niveau » seize fois), XP visible « 120 / 450 » et barre ; en bas, le savoir-faire SURVOLE (ou le
     * meilleur) : effet actuel et effet au niveau suivant, ecrits (avant : seulement en infobulle) ; « ×1,5 » au bout de la barre des
     * deux savoir-faire de la vocation (et « Affinite de vocation » dans le detail).
     * 1.0.5-beta (lot 10) : 17 savoir-faire (la Chasse) : 9 rangees de 17 px (au lieu de 8 de 19), la colonne de gauche en a 9 ; le detail ne
     * bouge pas ; trois savoir-faire marques « ×1,5 » pour l'Eclaireur.
     */
    public static final int SKILL_X = 12, SKILL_COL_W = 178, SKILL_COL_STEP = 184, SKILL_Y = 38, SKILL_ROW = 17, SKILL_ROWS = 9, DETAIL_Y = 196;

    private static void drawSkills(NotebookScreen s, GuiGraphics g, int mx, int my) {
        PioneerProfile p = RpClient.profile();
        PioneerDefs.Vocation voc = PioneerDefs.get().vocation(p.vocation);
        List<PioneerDefs.Skill> skills = PioneerDefs.get().orderedSkills();
        int total = 0;
        PioneerDefs.Skill best = null;
        for (PioneerDefs.Skill sk : skills) {
            total += PioneerApi.levelOf(p.xp(sk.id()));
            if (PioneerApi.hasFeature(sk.feature()) && (best == null || p.xp(sk.id()) > p.xp(best.id()))) {
                best = sk;
            }
        }
        // en-tete : niveaux cumules et affinites a gauche, derniers gains a droite
        int hx = SKILL_X;
        hx += s.fit(g, Component.translatable("pioneer.pioneers.levels_total", total), hx, 25, 90, INK) + 8;
        int end = PAGE_W - SKILL_X, gx = end;
        List<PioneerProfile.Gain> gains = new ArrayList<>(p.recent);
        for (int i = gains.size() - 1, n = 0; i >= 0 && n < 6; i--, n++) {
            PioneerProfile.Gain gain = gains.get(i);
            Component amount = Component.literal("+" + gain.amount() + (gain.auto() ? " ⚙" : ""));
            int aw = (int) Math.ceil(s.font.width(amount) * RpGui.MIN_SCALE);
            if (gx - aw - 13 < hx) {
                break;
            }
            s.fit(g, amount, gx - aw, 27, aw + 1, gain.auto() ? INK_LIGHT : TEXT, RpGui.MIN_SCALE);
            g.pose().pushPose();
            g.pose().translate(gx - aw - 12, 24, 0);
            g.pose().scale(0.75F, 0.75F, 1F);
            s.icon(g, RpGui.skillIcon(gain.skill()), 0, 0, 16);
            g.pose().popPose();
            s.tooltip(mx, my, gx - aw - 12, 24, aw + 12, 12, RpGui.skillName(gain.skill()));
            gx -= aw + 17;
        }
        s.separator(g, SKILL_X, 35, PAGE_W - 2 * SKILL_X);
        PioneerDefs.Skill shown = best;
        for (int i = 0; i < skills.size(); i++) {
            PioneerDefs.Skill sk = skills.get(i);
            int col = i / SKILL_ROWS, row = i % SKILL_ROWS;
            int x = SKILL_X + col * SKILL_COL_STEP, y = SKILL_Y + row * SKILL_ROW;
            int right = x + SKILL_COL_W;
            boolean reserved = !PioneerApi.hasFeature(sk.feature());
            boolean over = s.over(mx, my, x, y, SKILL_COL_W, SKILL_ROW - 2);
            if (over && !reserved) {
                shown = sk;
                g.fill(x - 2, y - 1, right + 2, y + SKILL_ROW - 2, 0x12243660);
            }
            double xp = p.xp(sk.id());
            int lvl = PioneerApi.levelOf(xp);
            s.icon(g, RpGui.skillIcon(sk.id()), x, y + 1, 16);
            int color = reserved ? FAINT : INK;
            boolean max = lvl >= PioneerApi.MAX_LEVEL;
            // niveau : chiffre dans une pastille a droite
            Component lv = Component.literal(Integer.toString(lvl));
            g.fill(right - 13, y, right, y + 10, reserved ? 0x10243660 : max ? 0x40A87C2A : 0x24243660);
            s.frame(g, right - 13, y, 13, 10, reserved ? FAINT : max ? GOLD : INK_LIGHT);
            s.fitCentered(g, lv, right - 6, y + 1, 12, reserved ? FAINT : max ? GOLD : INK, 1F);
            boolean affine = voc != null && voc.affine(sk.id());
            double lo = PioneerApi.xpFor(lvl), hi = PioneerApi.xpFor(Math.min(PioneerApi.MAX_LEVEL, lvl + 1));
            //1.0.5-beta (lot 19, retour 10 : « moins verbeux, detail en infobulle ») : plus d'XP chiffree sur la ligne (nom, pastille du niveau,
            //barre) ; l'XP et l'effet actuel passent dans l'infobulle de la ligne ; « reserve » reste ecrit (le savoir-faire est coupe)
            int xw = reserved ? s.fitRight(g, Component.translatable("pioneer.pioneers.reserved_short"), right - 17, y + 2, 56, FAINT, RpGui.MIN_SCALE) : 0;
            s.fit(g, RpGui.skillName(sk.id()), x + 20, y + 1, right - 17 - (xw > 0 ? xw + 4 : 2) - (x + 20), color);
            int bw = right - (x + 20) - (affine ? 22 : 0);
            double frac = max ? 1 : (xp - lo) / Math.max(1, hi - lo);
            s.bar(g, x + 20, y + 12, bw, 4, frac, reserved ? FAINT : max ? GOLD : INK_LIGHT);
            if (affine) {
                s.fitRight(g, Component.literal("×1,5"), right, y + 11, 20, GOLD, RpGui.MIN_SCALE);
                s.tooltip(mx, my, right - 20, y + 10, 20, 8, Component.translatable("pioneer.pioneers.affinity"));
            }
            if (reserved) {
                s.tooltip(mx, my, x, y, SKILL_COL_W, SKILL_ROW - 2, Component.translatable("pioneer.pioneers.reserved"));
            } else {
                List<Component> tip = new ArrayList<>();
                tip.add(RpGui.skillName(sk.id()));
                tip.add(xpTip(lvl, xp, hi));
                if (affine) {
                    tip.add(Component.translatable("pioneer.pioneers.affinity").withStyle(st -> st.withColor(0xD9B25A)));
                }
                s.tooltip(mx, my, x, y, SKILL_COL_W - (affine ? 22 : 0), SKILL_ROW - 2, tip);
            }
        }
        // detail du savoir-faire survole (ou du meilleur) : effets ecrits, plus seulement en infobulle
        s.separator(g, SKILL_X, DETAIL_Y - 3, PAGE_W - 2 * SKILL_X);
        if (shown != null) {
            int lvl = PioneerApi.levelOf(p.xp(shown.id()));
            s.icon(g, RpGui.skillIcon(shown.id()), SKILL_X, DETAIL_Y + 2, 16);
            int x = SKILL_X + 20, w = PAGE_W - SKILL_X - x;
            int nw = s.fit(g, RpGui.skillName(shown.id()), x, DETAIL_Y, w / 2, INK);
            s.fit(g, Component.translatable("pioneer.pioneers.lvl_short", lvl), x + nw + 6, DETAIL_Y + 1, 60, INK_LIGHT, RpGui.MIN_SCALE);
            if (voc != null && voc.affine(shown.id())) {
                s.fitRight(g, Component.translatable("pioneer.pioneers.affinity_short"), PAGE_W - SKILL_X, DETAIL_Y + 1, w / 2 - 70, GOLD, RpGui.MIN_SCALE);
            }
            int y = DETAIL_Y + 10;
            y += s.wrap(g, Component.translatable("pioneer.pioneers.effect_now", join(RpGui.skillEffects(shown, lvl))), x, y, w, TEXT,
                  RpGui.MIN_SCALE, 2);
            Component next = lvl >= PioneerApi.MAX_LEVEL ? Component.translatable("pioneer.pioneers.max_level")
                  : Component.translatable("pioneer.pioneers.next_level", lvl + 1, join(RpGui.skillEffects(shown, lvl + 1)));
            s.fit(g, next, x, y + 1, w, lvl >= PioneerApi.MAX_LEVEL ? GOLD : DIM, RpGui.MIN_SCALE);
        }
    }

    /** Effets joints par « · » (ou « aucun effet »). */
    private static Component join(List<Component> l) {
        if (l.isEmpty()) {
            return Component.translatable("pioneer.pioneers.no_effect");
        }
        MutableComponent out = Component.empty();
        for (int i = 0; i < l.size(); i++) {
            if (i > 0) {
                out.append(" · ");
            }
            out.append(l.get(i));
        }
        return out;
    }

    // ------------------------------------------------------------------ Codex
    private static void drawCodex(NotebookScreen s, GuiGraphics g, int mx, int my) {
        PioneerProfile p = RpClient.profile();
        for (int c = 1; c <= CodexBook.CHAPTERS; c++) {
            int y = 26 + (c - 1) * 15;
            int read, total;
            if (c == CodexBook.NOTES_CHAPTER) {
                total = CodexBook.notes().size();
                read = p.notesRead.size();
            } else if (c == CodexBook.TRADES_CHAPTER) {
                total = CodexBook.chapter(c).size();
                read = total;
            } else {
                List<CodexBook.Entry> es = CodexBook.chapter(c);
                total = es.size();
                read = (int) es.stream().filter(e -> p.loreRead.contains(e.id())).count();
            }
            int cc = c;
            s.chip(g, mx, my, 10, y, 140, 13, Component.literal(c + ". ").append(Component.translatable("codex.pioneers.chapter." + c)), s.chapter == c,
                  true, () -> {
                      s.chapter = cc;
                      s.scroll[1] = 0;
                  });
            s.fit(g, Component.literal(read + "/" + total), 152, y + 3, 24, read == total && total > 0 ? PLUS : DIM, RpGui.MIN_SCALE);
        }
        int x = 178, top = 26, w = PAGE_W - x - 10, h = PAGE_H - top - 10;
        List<String[]> rows = new ArrayList<>();
        if (s.chapter == CodexBook.NOTES_CHAPTER) {
            for (String n : p.notesRead) {
                rows.add(new String[]{n, ""});
            }
        } else {
            for (CodexBook.Entry e : CodexBook.chapter(s.chapter)) {
                boolean known = s.chapter == CodexBook.TRADES_CHAPTER || p.loreRead.contains(e.id());
                rows.add(new String[]{e.id(), known ? "1" : "0"});
            }
        }
        s.scrollArea(1, x, top, w, h, rows.size() * 12);
        s.beginClip(g, x, top, w, h);
        int y = top - s.scroll[1];
        for (String[] r : rows) {
            boolean note = s.chapter == CodexBook.NOTES_CHAPTER;
            boolean known = note || "1".equals(r[1]);
            Component label = note ? Component.literal("« ").append(Component.translatable("codex.pioneers." + r[0])).append(" »")
                  : known ? Component.translatable("codex.pioneers." + r[0] + ".title") : Component.translatable("pioneer.pioneers.unread");
            boolean on = known && s.over(mx, my, x, y, w, 11);
            if (y + 11 > top && y < top + h) { //1.9.1 : titre borne (reduit puis coupe avec infobulle)
                s.fit(g, label, x + 2, y + 1, w - 4, known ? (on ? INK_LIGHT : INK) : FAINT);
            }
            if (known && y + 11 > top && y < top + h) {
                String id = r[0];
                s.click(x, Math.max(y, top), w, 11, () -> Minecraft.getInstance().setScreen(new LoreScreen(s, id)));
            }
            y += 12;
        }
        s.endClip(g);
    }
}
