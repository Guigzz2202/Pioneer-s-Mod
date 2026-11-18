package fr.pioneers.guide.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.client.PiKeys;
import fr.pioneers.guide.client.GuideBook.Chapter;
import fr.pioneers.guide.client.GuideBook.Page;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * Le guide de Pioneer's (1.4.2 : touche {@link PiKeys#GUIDE}, plus d'objet). Fenetre adaptee a l'ecran :
 * <ul>
 *     <li>en-tete : nom, barre de recherche (clic, Ctrl + F ou /), bouton de fermeture ;</li>
 *     <li>sommaire a gauche : chapitres ranges par groupes (commencer, ages, espace, mecaniques, aide), le chapitre ouvert deplie la liste de
 *     ses pages ; molette pour le faire defiler ;</li>
 *     <li>page a droite : fil d'Ariane (chapitre, sous-partie), titre, objets concernes, diagramme, texte qui defile (molette, barre) ;</li>
 *     <li>pied : page precedente / suivante (avec leur titre) et position dans le chapitre ;</li>
 *     <li>recherche : les pages qui contiennent tous les mots (sans accents ni majuscules), avec leur passage, mots en jaune ; un clic ouvre la
 *     page a l'endroit du premier mot trouve, mots surlignes dans le texte.</li>
 * </ul>
 * Refonte du guide (1.0.1-beta) : sommaire en quatre groupes (Bien commencer, Le Periple, Mecaniques, Annexes) ; page speciale
 * « Ou en suis-je ? » ({@code special: progress}) : age en cours, carte cliquable de la prochaine etape, barre de chaque age
 * ({@link GuideProgress}) ; pastille Fait / A faire a cote du titre d'une etape et coches dans le sommaire ; renvois « Voir aussi »
 * cliquables sous le schema ; clic sur une icone (objets, schema) : ses recettes dans JEI, clic droit : ses usages ({@link GuideLinks}).
 * Les zones cliquables de la page sont relevees pendant le rendu ({@code areas}) et lues au clic.
 * Mise en page : constantes ci-dessous ; maquette hors jeu (memes coordonnees) : {@code tools/gc/guide/guide_screen_preview.py}.
 */
public class GuideScreen extends Screen {

    // Fenetre et bandes
    static final int MAX_W = 500, MAX_H = 310, MARGIN = 10, HEAD = 26, FOOT = 22, SIDEBAR = 134;
    // Sommaire : hauteurs des lignes
    static final int GROUP_ROW = 14, CHAPTER_ROW = 15, PAGE_ROW = 11;
    // Page : texte, objets, diagramme
    static final float TEXT_SCALE = 0.85F;
    static final int LINE = 10, ITEM = 20, NODE = 30, GAP = 8, FLOW_ROW = 34;
    // Recherche
    static final int RESULT_H = 36, MAX_RESULTS = 60, SEARCH_W = 168;
    // Couleurs
    static final int C_FRAME = 0xFF2C3A4A, C_PANEL = 0xFF141C26, C_HEAD = 0xFF0C1218, C_SIDE = 0xFF0F151D, C_ACCENT = 0xFF55CCFF,
          C_TITLE = 0xFFFFD27A, C_TEXT = 0xFFD8E0EA, C_DIM = 0xFF7F8EA0, C_CRUMB = 0xFF7FA6C8, C_SELECT = 0xFF1E3346, C_HOVER = 0xFF18242F;
    // Refonte : progression (panneau, coches), renvois, pastilles
    static final int C_OK = 0xFF6BD48A, C_CHIP = 0xFF1C2B3A, C_TRACK = 0xFF1A2430;
    static final float LINK_SCALE = 0.62F;
    static final int LINK_H = 11, LINK_ROW = 14, P_CARD = 38, P_LIST = 76, P_ROW = 13;
    // 1.0.3-beta (lot 2) : reglages de l'affichage a l'ecran (4 rangees cliquables et un apercu de l'ecran)
    static final int HUD_ROW = 17, HUD_SETTINGS_H = 6 * HUD_ROW + 10, HUD_PREVIEW_W = 96, HUD_PREVIEW_H = 56;
    // 1.0.4-beta (lot 29) : bouton « Partie » de l'en-tete (a gauche de la recherche) : ouvre l'ecran de la partie (PartyScreen)
    // fenetre etroite : le bouton n'a plus que son pictogramme (PARTY_ICON_W) et le libelle passe en infobulle
    static final int PARTY_W = 54, PARTY_ICON_W = 18, PARTY_GAP = 6;

    private static int lastChapter, lastPage, lastScroll;
    private static String lastQuery = "";

    private int left, top, w, h;
    private int chapter, page, scroll, sideScroll, resultScroll;
    private EditBox search;
    private String query = "";
    private List<GuideSearch.Hit> hits = List.of();
    /** Mots a surligner dans la page ouverte depuis la recherche (vide : aucun). */
    private List<String> marks = List.of();

    /** Ligne de texte mise en page : ligne a dessiner et paragraphe d'origine. */
    private record Line(FormattedCharSequence text, int paragraph) {}

    private final List<Line> lines = new ArrayList<>();
    private int markedParagraph = -1;

    /** Ligne du sommaire : en-tete de groupe (0), chapitre (1), page du chapitre ouvert (2). */
    private record Row(int kind, int chapter, int page, int y, int height) {}

    /** Zone cliquable relevee au rendu : ouvre la page (chapitre, page) ou, si {@code stack} n'est pas vide, l'objet dans JEI. */
    private record Area(int x0, int y0, int x1, int y1, int chapter, int page, ItemStack stack) {}

    /** Pastille d'un renvoi : position relative au bloc, largeur, libelle, page visee. */
    private record Chip(int x, int y, int width, Component title, int chapter, int page) {}

    private final List<Area> areas = new ArrayList<>();
    private GuideProgress progress = GuideProgress.EMPTY;
    private int ticks, clipTop, clipBottom;
    /** Infobulle de texte (renvoi survole) a dessiner apres tout, ou null. */
    @Nullable
    private Component hoverTip;

    private GuideScreen() {
        super(Component.translatable("guide.pioneers.title"));
    }

    /** Ouvre le guide (au dernier chapitre lu, ou a celui demande). */
    public static void open(@Nullable String chapterId) {
        GuideScreen screen = new GuideScreen();
        screen.chapter = lastChapter;
        screen.page = lastPage;
        screen.scroll = lastScroll;
        screen.query = lastQuery;
        if (chapterId != null) {
            List<Chapter> chapters = GuideBook.chapters();
            for (int i = 0; i < chapters.size(); i++) {
                if (chapters.get(i).id().equals(chapterId)) {
                    screen.chapter = i;
                    screen.page = 0;
                    screen.scroll = 0;
                    screen.query = "";
                }
            }
        }
        Minecraft.getInstance().setScreen(screen);
    }

    /** 1.0.3-beta (lot 2) : ouvre le guide a une page (etape du Periple montree par l'affichage a l'ecran, touche J). */
    public static void openAt(int chapterIndex, int pageIndex) {
        GuideScreen screen = new GuideScreen();
        screen.chapter = chapterIndex;
        screen.page = pageIndex;
        screen.scroll = 0;
        screen.query = "";
        Minecraft.getInstance().setScreen(screen);
    }

    // ------------------------------------------------------------------ mise en page

    @Override
    protected void init() {
        w = Math.min(MAX_W, width - 2 * MARGIN);
        h = Math.min(MAX_H, height - 2 * MARGIN);
        left = (width - w) / 2;
        top = (height - h) / 2;
        List<Chapter> chapters = GuideBook.chapters();
        chapter = chapters.isEmpty() ? 0 : Mth.clamp(chapter, 0, chapters.size() - 1);
        page = chapters.isEmpty() ? 0 : Mth.clamp(page, 0, chapters.get(chapter).pages().size() - 1);
        int sw = Math.min(SEARCH_W, w - SIDEBAR - 60);
        search = new EditBox(font, left + w - sw - 22, top + 9, sw - 18, 10, Component.translatable("guide.pioneers.search"));
        search.setBordered(false);
        search.setMaxLength(64);
        search.setTextColor(0xFFE8EEF4);
        search.setHint(Component.translatable("guide.pioneers.search").withStyle(ChatFormatting.DARK_GRAY));
        search.setValue(query);
        search.setResponder(this::onSearch);
        addRenderableWidget(search);
        hits = query.isBlank() ? List.of() : GuideSearch.search(chapters, query, MAX_RESULTS);
        progress = GuideProgress.read(chapters);
        layout();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        reveal();
    }

    @Override
    public void tick() {
        super.tick();
        //Progres relus chaque seconde : une etape faite pendant que le guide est ouvert se coche
        if (++ticks % 20 == 0) {
            progress = GuideProgress.read(GuideBook.chapters());
        }
    }

    /** Fait defiler le sommaire pour montrer le chapitre ouvert. */
    private void reveal() {
        List<Row> rows = rows();
        for (Row row : rows) {
            if (row.kind() == 1 && row.chapter() == chapter) {
                int visible = sideBottom() - sideTop();
                if (row.y() < sideScroll || row.y() + CHAPTER_ROW > sideScroll + visible) {
                    sideScroll = Mth.clamp(row.y() - 2 * CHAPTER_ROW, 0, sideMax(rows));
                }
                return;
            }
        }
    }

    /** Zone de la page : x, largeur (texte), haut et bas de la partie qui defile. */
    private int contentX() {
        return left + SIDEBAR + 12;
    }

    private int contentW() {
        return w - SIDEBAR - 12 - 14;
    }

    private int bodyTop() {
        return top + HEAD + 30;
    }

    private int bodyBottom() {
        return top + h - FOOT - 4;
    }

    @Nullable
    private Page current() {
        List<Chapter> chapters = GuideBook.chapters();
        if (chapters.isEmpty() || chapters.get(chapter).pages().isEmpty()) {
            return null;
        }
        return chapters.get(chapter).pages().get(page);
    }

    /** Coupe le texte de la page en lignes (mots de la recherche surlignes). */
    private void layout() {
        lines.clear();
        markedParagraph = -1;
        Page source = current();
        if (source == null) {
            return;
        }
        int width = (int) (contentW() / TEXT_SCALE);
        String[] paragraphs = source.text().split("\n", -1);
        for (int p = 0; p < paragraphs.length; p++) {
            String paragraph = paragraphs[p];
            if (paragraph.isEmpty()) {
                lines.add(new Line(FormattedCharSequence.EMPTY, p));
                continue;
            }
            if (!marks.isEmpty() && markedParagraph < 0 && GuideSearch.contains(paragraph, marks)) {
                markedParagraph = p;
            }
            String shown = marks.isEmpty() ? paragraph : GuideSearch.highlight(paragraph, marks);
            for (FormattedCharSequence line : font.split(Component.literal(shown), width)) {
                lines.add(new Line(line, p));
            }
        }
    }

    /** Hauteur du panneau de progression, des objets, du diagramme et des renvois au-dessus du texte. */
    private int headerHeight(Page p) {
        return (isProgress(p) ? progressHeight() : 0) + (isHud(p) ? HUD_SETTINGS_H : 0) + (p.items().isEmpty() ? 0 : ITEM + 6)
              + (p.flow().isEmpty() ? 0 : p.flow().size() * FLOW_ROW + 6) + linksHeight(linkChips(p));
    }

    private static boolean isProgress(Page p) {
        return "progress".equals(p.special());
    }

    /** 1.0.3-beta (lot 2) : page « Affichage a l'ecran » (reglages de {@link JourneyHud}). */
    private static boolean isHud(Page p) {
        return "hud".equals(p.special());
    }

    private int progressHeight() {
        return progress.ages().isEmpty() ? 0 : P_LIST + 10 + progress.ages().size() * P_ROW + 6;
    }

    /** Pastilles des renvois de la page, rangees en lignes apres « Voir aussi : » (renvois vers une page inconnue ignores). */
    private List<Chip> linkChips(Page p) {
        if (p.links().isEmpty() || font == null) {
            return List.of();
        }
        List<Chapter> chapters = GuideBook.chapters();
        int cw = contentW();
        int lw = (int) (font.width(Component.translatable("guide.pioneers.links")) * LINK_SCALE) + 6;
        List<Chip> out = new ArrayList<>();
        int cx = lw, cy = 0;
        for (GuideBook.Link link : p.links()) {
            int c = GuideBook.indexOf(chapters, link.chapter());
            if (c < 0 || link.page() < 0 || link.page() >= chapters.get(c).pages().size()) {
                continue;
            }
            Component title = fit(chapters.get(c).pages().get(link.page()).title(), (cw - lw - 12) / LINK_SCALE);
            int width = (int) (font.width(title) * LINK_SCALE) + 10;
            if (cx > lw && cx + width > cw) {
                cx = lw;
                cy += LINK_ROW;
            }
            out.add(new Chip(cx, cy, width, title, c, link.page()));
            cx += width + 4;
        }
        return out;
    }

    private static int linksHeight(List<Chip> chips) {
        return chips.isEmpty() ? 0 : chips.get(chips.size() - 1).y() + LINK_ROW + 4;
    }

    private int contentHeight() {
        Page p = current();
        return p == null ? 0 : headerHeight(p) + (int) Math.ceil(lines.size() * LINE * TEXT_SCALE) + 6;
    }

    private int maxScroll() {
        return Math.max(0, contentHeight() - (bodyBottom() - bodyTop()));
    }

    private boolean searching() {
        return !query.isBlank();
    }

    private void onSearch(String text) {
        query = text;
        lastQuery = text;
        resultScroll = 0;
        hits = searching() ? GuideSearch.search(GuideBook.chapters(), query, MAX_RESULTS) : List.of();
    }

    // ------------------------------------------------------------------ navigation

    private void openPage(int chapterIndex, int pageIndex, List<String> terms) {
        List<Chapter> chapters = GuideBook.chapters();
        if (chapters.isEmpty()) {
            return;
        }
        chapter = Mth.clamp(chapterIndex, 0, chapters.size() - 1);
        page = Mth.clamp(pageIndex, 0, Math.max(0, chapters.get(chapter).pages().size() - 1));
        marks = terms;
        layout();
        scroll = 0;
        if (markedParagraph >= 0) {
            //Fait venir le premier passage trouve en haut de la zone de texte
            for (int i = 0; i < lines.size(); i++) {
                if (lines.get(i).paragraph() == markedParagraph) {
                    scroll = Mth.clamp(headerHeight(current()) + (int) (i * LINE * TEXT_SCALE) - 6, 0, maxScroll());
                    break;
                }
            }
        }
        reveal();
        remember();
        sound();
    }

    private void leaveSearch() {
        search.setValue("");
        search.setFocused(false);
        setFocused(null);
    }

    private void turn(int step) {
        List<Chapter> chapters = GuideBook.chapters();
        if (chapters.isEmpty()) {
            return;
        }
        int[] target = neighbour(step);
        if (target != null) {
            openPage(target[0], target[1], List.of());
        }
    }

    /** Page voisine (chapitre, page) dans l'ordre du guide, ou null au bout. */
    @Nullable
    private int[] neighbour(int step) {
        List<Chapter> chapters = GuideBook.chapters();
        int c = chapter, p = page + step;
        while (c >= 0 && c < chapters.size()) {
            int n = chapters.get(c).pages().size();
            if (p >= 0 && p < n) {
                return new int[]{c, p};
            }
            c += step;
            if (c < 0 || c >= chapters.size()) {
                return null;
            }
            p = step > 0 ? 0 : chapters.get(c).pages().size() - 1;
        }
        return null;
    }

    private void selectChapter(int index) {
        List<Chapter> chapters = GuideBook.chapters();
        if (index >= 0 && index < chapters.size() && (index != chapter || page != 0)) {
            openPage(index, 0, List.of());
        }
    }

    private void remember() {
        lastChapter = chapter;
        lastPage = page;
        lastScroll = scroll;
    }

    private void sound() {
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.BOOK_PAGE_TURN, 1.0F));
    }

    // ------------------------------------------------------------------ sommaire

    private List<Row> rows() {
        List<Row> out = new ArrayList<>();
        List<Chapter> chapters = GuideBook.chapters();
        String group = null;
        int y = 0;
        for (int c = 0; c < chapters.size(); c++) {
            Chapter entry = chapters.get(c);
            if (!entry.group().equals(group)) {
                group = entry.group();
                out.add(new Row(0, c, -1, y, GROUP_ROW));
                y += GROUP_ROW;
            }
            out.add(new Row(1, c, -1, y, CHAPTER_ROW));
            y += CHAPTER_ROW;
            if (c == chapter && !searching()) {
                for (int p = 0; p < entry.pages().size(); p++) {
                    out.add(new Row(2, c, p, y, PAGE_ROW));
                    y += PAGE_ROW;
                }
                y += 3;
            }
        }
        return out;
    }

    private int sideTop() {
        return top + HEAD + 4;
    }

    private int sideBottom() {
        return top + h - 4;
    }

    private int sideMax(List<Row> rows) {
        int total = rows.isEmpty() ? 0 : rows.get(rows.size() - 1).y() + rows.get(rows.size() - 1).height();
        return Math.max(0, total - (sideBottom() - sideTop()));
    }

    // ------------------------------------------------------------------ rendu

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        areas.clear();
        hoverTip = null;
        renderBackground(graphics);
        //Fenetre : ombre, cadre, en-tete, sommaire, pied
        graphics.fill(left - 1 + 3, top - 1 + 3, left + w + 1 + 3, top + h + 1 + 3, 0x60000000);
        graphics.fill(left - 1, top - 1, left + w + 1, top + h + 1, C_FRAME);
        graphics.fill(left, top, left + w, top + h, C_PANEL);
        graphics.fill(left, top, left + w, top + HEAD, C_HEAD);
        graphics.fill(left, top + HEAD - 1, left + w, top + HEAD, C_ACCENT);
        graphics.fill(left, top + HEAD, left + SIDEBAR, top + h, C_SIDE);
        graphics.fill(left + SIDEBAR, top + HEAD, left + SIDEBAR + 1, top + h, C_FRAME);
        graphics.fill(left + SIDEBAR + 1, top + h - FOOT, left + w, top + h, C_HEAD);
        graphics.fill(left + SIDEBAR + 1, top + h - FOOT, left + w, top + h - FOOT + 1, C_FRAME);
        ItemStack hovered = ItemStack.EMPTY;
        drawHeader(graphics, mouseX, mouseY);
        drawSidebar(graphics, mouseX, mouseY);
        if (GuideBook.chapters().isEmpty()) {
            text(graphics, Component.translatable("guide.pioneers.empty"), contentX(), top + HEAD + 12, C_TEXT, 0.85F);
        } else if (searching()) {
            drawResults(graphics, mouseX, mouseY);
        } else {
            hovered = drawPage(graphics, mouseX, mouseY);
            drawFooter(graphics, mouseX, mouseY);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        if (!hovered.isEmpty()) {
            //Infobulle de l'objet, plus le rappel du clic vers JEI quand JEI est la
            List<Component> tip = new ArrayList<>(Screen.getTooltipFromItem(Minecraft.getInstance(), hovered));
            if (GuideLinks.jeiAvailable()) {
                tip.add(Component.translatable("guide.pioneers.jei_hint").withStyle(ChatFormatting.DARK_GRAY));
            }
            graphics.renderTooltip(font, tip, hovered.getTooltipImage(), mouseX, mouseY);
        } else if (hoverTip != null) {
            graphics.renderTooltip(font, hoverTip, mouseX, mouseY);
        }
    }

    /**
     * 1.0.4-beta (lot 29) : bouton « Partie » : x, y, largeur (a gauche du cadre de la recherche) ; pictogramme seul quand le libelle
     * toucherait le nom et le sous-titre du guide (fenetre etroite).
     */
    private int[] partyButton() {
        int sx = search.getX() - 16;
        int title = left + 28 + Math.round(Math.max(font.width("Pioneer's"), font.width(Component.translatable("guide.pioneers.subtitle")) * 0.6F));
        int wide = title + 4 <= sx - PARTY_GAP - PARTY_W ? PARTY_W : PARTY_ICON_W;
        return new int[]{sx - PARTY_GAP - wide, top + 5, wide};
    }

    private void drawHeader(GuiGraphics graphics, int mouseX, int mouseY) {
        //Logo : livre ouvert dessine, puis le nom
        int bx = left + 8, by = top + 8;
        graphics.fill(bx, by, bx + 7, by + 10, 0xFF2E6E9E);
        graphics.fill(bx + 8, by, bx + 15, by + 10, 0xFF2E6E9E);
        graphics.fill(bx + 1, by + 1, bx + 7, by + 9, 0xFFE8E2D0);
        graphics.fill(bx + 8, by + 1, bx + 14, by + 9, 0xFFE8E2D0);
        for (int k = 0; k < 3; k++) {
            graphics.fill(bx + 2, by + 3 + 2 * k, bx + 6, by + 4 + 2 * k, 0xFF9AA4AE);
            graphics.fill(bx + 9, by + 3 + 2 * k, bx + 13, by + 4 + 2 * k, 0xFF9AA4AE);
        }
        text(graphics, Component.literal("Pioneer's"), left + 28, top + 6, C_ACCENT, 1F);
        text(graphics, Component.translatable("guide.pioneers.subtitle"), left + 28, top + 16, C_DIM, 0.6F);
        //Barre de recherche
        int sx = search.getX() - 16, sy = top + 5, sx2 = search.getX() + search.getWidth() + 4;
        boolean focused = search.isFocused();
        graphics.fill(sx - 1, sy - 1, sx2 + 1, sy + 17, focused ? C_ACCENT : C_FRAME);
        graphics.fill(sx, sy, sx2, sy + 16, 0xFF080C11);
        //Loupe
        int lx = sx + 4, ly = sy + 4;
        graphics.fill(lx + 1, ly, lx + 5, ly + 1, C_DIM);
        graphics.fill(lx + 1, ly + 5, lx + 5, ly + 6, C_DIM);
        graphics.fill(lx, ly + 1, lx + 1, ly + 5, C_DIM);
        graphics.fill(lx + 5, ly + 1, lx + 6, ly + 5, C_DIM);
        graphics.fill(lx + 5, ly + 6, lx + 7, ly + 7, C_DIM);
        graphics.fill(lx + 6, ly + 7, lx + 8, ly + 8, C_DIM);
        if (!search.getValue().isEmpty()) {
            boolean hover = mouseX >= sx2 - 12 && mouseX < sx2 && mouseY >= sy && mouseY < sy + 16;
            graphics.drawString(font, "x", sx2 - 9, sy + 4, hover ? 0xFFFFFFFF : C_DIM, false);
        }
        //1.0.4-beta (lot 29) : bouton « Partie »
        int[] pb = partyButton();
        boolean overParty = mouseX >= pb[0] && mouseX < pb[0] + pb[2] && mouseY >= pb[1] && mouseY < pb[1] + 16;
        graphics.fill(pb[0], pb[1], pb[0] + pb[2], pb[1] + 16, overParty ? C_SELECT : C_CHIP);
        graphics.fill(pb[0], pb[1] + 15, pb[0] + pb[2], pb[1] + 16, C_ACCENT);
        PartyScreen.PartyIcon.draw(graphics, pb[0] + 4, pb[1] + 2, C_ACCENT);
        if (pb[2] == PARTY_W) {
            text(graphics, Component.translatable("party.pioneers.button"), pb[0] + 18, pb[1] + 5, overParty ? 0xFFFFFFFF : C_TEXT, 0.75F);
        } else if (overParty) {
            hoverTip = Component.translatable("party.pioneers.button");
        }
        //Fermer
        int cx = left + w - 18, cy = top + 5;
        boolean hover = mouseX >= cx && mouseX < cx + 14 && mouseY >= cy && mouseY < cy + 16;
        graphics.fill(cx, cy, cx + 14, cy + 16, hover ? 0xFF6A2A2A : 0xFF1A222C);
        graphics.drawString(font, "x", cx + 4, cy + 4, hover ? 0xFFFFFFFF : 0xFFB8C4D0, false);
    }

    private void drawSidebar(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Chapter> chapters = GuideBook.chapters();
        List<Row> rows = rows();
        sideScroll = Mth.clamp(sideScroll, 0, sideMax(rows));
        int y0 = sideTop(), y1 = sideBottom();
        graphics.enableScissor(left, y0, left + SIDEBAR, y1);
        boolean inside = mouseX >= left && mouseX < left + SIDEBAR && mouseY >= y0 && mouseY < y1;
        for (Row row : rows) {
            int y = y0 + row.y() - sideScroll;
            if (y + row.height() < y0 || y > y1) {
                continue;
            }
            boolean hover = inside && mouseY >= y && mouseY < y + row.height();
            Chapter entry = chapters.get(row.chapter());
            switch (row.kind()) {
                case 0 -> {
                    Component label = Component.translatable("guide.pioneers.group." + entry.group());
                    text(graphics, label, left + 7, y + 5, 0xFF6F8296, 0.6F);
                    int lw = (int) (font.width(label) * 0.6F);
                    graphics.fill(left + 11 + lw, y + 8, left + SIDEBAR - 7, y + 9, 0xFF22303E);
                }
                case 1 -> {
                    boolean open = row.chapter() == chapter && !searching();
                    if (open) {
                        graphics.fill(left + 1, y, left + SIDEBAR, y + row.height(), C_SELECT);
                        graphics.fill(left + 1, y, left + 3, y + row.height(), C_ACCENT);
                    } else if (hover) {
                        graphics.fill(left + 1, y, left + SIDEBAR, y + row.height(), C_HOVER);
                    }
                    icon(graphics, entry.icon(), left + 7, y + 2, 0.65F);
                    //Age du Periple : coche quand toutes ses etapes sont faites, sinon le compte des etapes faites
                    GuideProgress.Age age = progress.age(row.chapter());
                    int room = 0;
                    if (age != null && age.total() > 0) {
                        room = 16;
                        if (age.complete()) {
                            check(graphics, left + SIDEBAR - 14, y + 4, C_OK);
                        } else if (age.done() > 0) {
                            String count = age.done() + "/" + age.total();
                            text(graphics, Component.literal(count), left + SIDEBAR - 6 - font.width(count) * 0.55F, y + 5, C_DIM, 0.55F);
                        }
                    }
                    text(graphics, fit(entry.shortTitle(), (SIDEBAR - 26 - room) / 0.75F), left + 21, y + 4, open ? 0xFFFFFFFF : 0xFFB8C4D0, 0.75F);
                }
                default -> {
                    boolean current = row.page() == page;
                    if (hover && !current) {
                        graphics.fill(left + 12, y, left + SIDEBAR - 2, y + row.height(), C_HOVER);
                    }
                    graphics.fill(left + 13, y, left + 14, y + row.height(), current ? C_ACCENT : 0xFF2A3846);
                    if (current) {
                        graphics.fill(left + 12, y + 4, left + 15, y + 7, C_ACCENT);
                    }
                    Page p = entry.pages().get(row.page());
                    boolean tracked = GuideProgress.tracked(entry, p);
                    if (tracked) {
                        if (progress.isDone(p)) {
                            check(graphics, left + SIDEBAR - 14, y + 2, C_OK);
                        } else {
                            box(graphics, left + SIDEBAR - 12, y + 3, 5, 0xFF3A4A5C);
                        }
                    }
                    text(graphics, fit(p.title(), (SIDEBAR - 24 - (tracked ? 12 : 0)) / 0.62F), left + 19, y + 3, current ? 0xFFFFFFFF : 0xFF8E9CAA,
                          0.62F);
                }
            }
        }
        graphics.disableScissor();
        //Barre de defilement du sommaire
        int max = sideMax(rows);
        if (max > 0) {
            int track = y1 - y0, bar = Math.max(16, track * track / (track + max)), at = (track - bar) * sideScroll / max;
            graphics.fill(left + SIDEBAR - 3, y0 + at, left + SIDEBAR - 1, y0 + at + bar, 0xFF3A4A5C);
        }
    }

    /** La page ouverte ; renvoie l'objet survole. */
    private ItemStack drawPage(GuiGraphics graphics, int mouseX, int mouseY) {
        Page p = current();
        if (p == null) {
            return ItemStack.EMPTY;
        }
        Chapter entry = GuideBook.chapters().get(chapter);
        int x = contentX(), cw = contentW();
        //Fil d'Ariane et titre
        String crumb = entry.title() + (p.section().isEmpty() ? "" : "  ›  " + p.section());
        text(graphics, fit(crumb, cw / 0.62F), x, top + HEAD + 6, C_CRUMB, 0.62F);
        int badge = GuideProgress.tracked(entry, p) ? drawBadge(graphics, p, x + cw, top + HEAD + 14) + 6 : 0;
        text(graphics, fit(p.title(), cw - badge), x, top + HEAD + 15, C_TITLE, 1F);
        graphics.fill(x, top + HEAD + 26, x + cw, top + HEAD + 27, C_FRAME);
        //Corps qui defile
        int y0 = bodyTop(), y1 = bodyBottom();
        scroll = Mth.clamp(scroll, 0, maxScroll());
        ItemStack hovered = ItemStack.EMPTY;
        boolean inside = mouseX >= x && mouseX < x + cw && mouseY >= y0 && mouseY < y1;
        int mx = inside ? mouseX : -1, my = inside ? mouseY : -1;
        clipTop = y0;
        clipBottom = y1;
        graphics.enableScissor(x - 4, y0, x + cw + 2, y1);
        int y = y0 - scroll;
        if (isProgress(p)) {
            drawProgress(graphics, x, y, cw, mx, my);
            y += progressHeight();
        }
        if (isHud(p)) {
            drawHudSettings(graphics, x, y, cw, mx, my);
            y += HUD_SETTINGS_H;
        }
        if (!p.items().isEmpty()) {
            int ix = x;
            for (ItemStack stack : p.items()) {
                if (ix + 18 > x + cw) {
                    break;
                }
                graphics.fill(ix - 1, y - 1, ix + 19, y + 19, 0xFF2A3846);
                graphics.fill(ix, y, ix + 18, y + 18, 0xFF1A2430);
                graphics.renderItem(stack, ix + 1, y + 1);
                area(ix, y, ix + 18, y + 18, -1, -1, stack);
                if (mx >= ix && mx < ix + 18 && my >= y && my < y + 18) {
                    hovered = stack;
                }
                ix += ITEM + 2;
            }
            y += ITEM + 6;
        }
        if (!p.flow().isEmpty()) {
            ItemStack hit = drawFlow(graphics, p, x, y + 2, cw, mx, my);
            if (!hit.isEmpty()) {
                hovered = hit;
            }
            y += p.flow().size() * FLOW_ROW + 6;
        }
        List<Chip> chips = linkChips(p);
        if (!chips.isEmpty()) {
            drawLinks(graphics, chips, x, y, mx, my);
            y += linksHeight(chips);
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(TEXT_SCALE, TEXT_SCALE, 1);
        int ly = 0;
        for (Line line : lines) {
            float screenY = y + ly * TEXT_SCALE;
            if (screenY + LINE > y0 && screenY < y1) {
                if (line.paragraph() == markedParagraph && !marks.isEmpty()) {
                    graphics.fill(-4, ly - 1, -2, ly + LINE - 1, 0xFFE8C840);           //repere du passage trouve
                }
                graphics.drawString(font, line.text(), 0, ly, C_TEXT, false);
            }
            ly += LINE;
        }
        pose.popPose();
        graphics.disableScissor();
        //Barre de defilement de la page, et fondu en bas quand il reste du texte
        int max = maxScroll();
        if (max > 0) {
            int track = y1 - y0, bar = Math.max(16, track * track / (track + max)), at = (track - bar) * scroll / max;
            graphics.fill(left + w - 8, y0, left + w - 5, y1, 0xFF1A2430);
            graphics.fill(left + w - 8, y0 + at, left + w - 5, y0 + at + bar, 0xFF4A6278);
            if (scroll < max) {
                graphics.fillGradient(x - 2, y1 - 8, x + cw + 2, y1, 0x00141C26, 0xFF141C26);
            }
        }
        return hovered;
    }

    private void drawFooter(GuiGraphics graphics, int mouseX, int mouseY) {
        Chapter entry = GuideBook.chapters().get(chapter);
        int fy = top + h - FOOT + 4, x0 = left + SIDEBAR + 8, x1 = left + w - 8;
        int bw = (x1 - x0 - 60) / 2;
        int[] prev = neighbour(-1), next = neighbour(1);
        footerButton(graphics, prev, x0, fy, bw, true, mouseX, mouseY);
        footerButton(graphics, next, x1 - bw, fy, bw, false, mouseX, mouseY);
        Component counter = Component.translatable("guide.pioneers.page", page + 1, entry.pages().size());
        float cw = font.width(counter) * 0.7F;
        text(graphics, counter, (x0 + x1) / 2F - cw / 2, fy + 5, C_DIM, 0.7F);
    }

    private void footerButton(GuiGraphics graphics, @Nullable int[] target, int x, int y, int width, boolean back, int mouseX, int mouseY) {
        boolean active = target != null, hover = active && mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + 14;
        graphics.fill(x, y, x + width, y + 14, !active ? 0xFF121920 : hover ? 0xFF2E5A80 : 0xFF1C2B3A);
        if (!active) {
            return;
        }
        String arrow = back ? "◀" : "▶";
        Page p = GuideBook.chapters().get(target[0]).pages().get(target[1]);
        Component label = fit(p.title(), (width - 16) / 0.7F);
        float lw = font.width(label) * 0.7F;
        if (back) {
            graphics.drawString(font, arrow, x + 4, y + 3, 0xFFFFFFFF, false);
            text(graphics, label, x + 14, y + 4, 0xFFD0DAE4, 0.7F);
        } else {
            graphics.drawString(font, arrow, x + width - 4 - font.width(arrow), y + 3, 0xFFFFFFFF, false);
            text(graphics, label, x + width - 14 - lw, y + 4, 0xFFD0DAE4, 0.7F);
        }
    }

    private void drawResults(GuiGraphics graphics, int mouseX, int mouseY) {
        List<Chapter> chapters = GuideBook.chapters();
        int x = contentX(), cw = contentW();
        Component count = hits.isEmpty() ? Component.translatable("guide.pioneers.no_results", query)
                                         : Component.translatable("guide.pioneers.results", hits.size(), query);
        text(graphics, fit(count.getString(), cw), x, top + HEAD + 9, hits.isEmpty() ? 0xFFE08A6A : C_TITLE, 1F);
        graphics.fill(x, top + HEAD + 22, x + cw, top + HEAD + 23, C_FRAME);
        int y0 = top + HEAD + 26, y1 = top + h - 6;
        int max = Math.max(0, hits.size() * RESULT_H - (y1 - y0));
        resultScroll = Mth.clamp(resultScroll, 0, max);
        List<String> terms = GuideSearch.terms(query);
        graphics.enableScissor(x - 2, y0, x + cw + 2, y1);
        for (int i = 0; i < hits.size(); i++) {
            int y = y0 + i * RESULT_H - resultScroll;
            if (y + RESULT_H < y0 || y > y1) {
                continue;
            }
            GuideSearch.Hit hit = hits.get(i);
            Chapter entry = chapters.get(hit.chapter());
            Page p = entry.pages().get(hit.page());
            boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= Math.max(y, y0) && mouseY < Math.min(y + RESULT_H - 3, y1);
            graphics.fill(x - 2, y, x + cw + 2, y + RESULT_H - 3, hover ? 0xFF1E3346 : 0xFF18222D);
            graphics.fill(x - 2, y, x, y + RESULT_H - 3, hover ? C_ACCENT : 0xFF2C3A4A);
            PoseStack pose = graphics.pose();
            pose.pushPose();
            pose.translate(x + 3, y + 3, 0);
            pose.scale(0.6F, 0.6F, 1);
            graphics.renderItem(entry.icon(), 0, 0);
            pose.popPose();
            String head = GuideSearch.highlight(p.title(), terms);
            text(graphics, Component.literal(head), x + 16, y + 3, C_TITLE, 0.75F);
            float hw = font.width(GuideSearch.strip(p.title())) * 0.75F;
            text(graphics, fit("  ·  " + entry.shortTitle(), (cw - 20 - hw) / 0.6F), x + 16 + hw, y + 4, C_CRUMB, 0.6F);
            List<FormattedCharSequence> snippet = font.split(Component.literal(hit.snippet()), (int) ((cw - 8) / 0.62F));
            for (int l = 0; l < Math.min(2, snippet.size()); l++) {
                pose.pushPose();
                pose.translate(x + 4, y + 13 + l * 7, 0);
                pose.scale(0.62F, 0.62F, 1);
                graphics.drawString(font, snippet.get(l), 0, 0, 0xFFA8B4C0, false);
                pose.popPose();
            }
        }
        graphics.disableScissor();
        if (max > 0) {
            int track = y1 - y0, bar = Math.max(16, track * track / (track + max)), at = (track - bar) * resultScroll / max;
            graphics.fill(left + w - 8, y0, left + w - 5, y1, 0xFF1A2430);
            graphics.fill(left + w - 8, y0 + at, left + w - 5, y0 + at + bar, 0xFF4A6278);
        }
    }

    /** Diagramme : rangees de noeuds (icone + legende) relies par des fleches ; renvoie l'objet survole. */
    private ItemStack drawFlow(GuiGraphics graphics, Page page, int x0, int y0, int width, int mouseX, int mouseY) {
        ItemStack hovered = ItemStack.EMPTY;
        int y = y0;
        for (int r = 0; r < page.flow().size(); r++) {
            List<GuideBook.Node> row = page.flow().get(r);
            int n = row.size();
            int step = n <= 1 ? NODE + GAP : Math.min(NODE + GAP + 14, (width - NODE) / (n - 1));
            int total = NODE + (n - 1) * step;
            int x = x0 + Math.max(0, (width - total) / 2);
            if (r > 0 && row.get(0).next()) {
                graphics.drawCenteredString(font, "↓", x0 + width / 2, y - 9, C_ACCENT);
            }
            for (int i = 0; i < n; i++) {
                GuideBook.Node node = row.get(i);
                int nx = x + i * step;
                int frame = node.key() ? C_TITLE : C_FRAME;
                if (!node.stack().isEmpty()) {
                    int bx = nx + (NODE - 18) / 2;
                    graphics.fill(bx - 1, y - 1, bx + 19, y + 19, frame);
                    graphics.fill(bx, y, bx + 18, y + 18, 0xFF1C2733);
                    graphics.renderItem(node.stack(), bx + 1, y + 1);
                    area(bx, y, bx + 18, y + 18, -1, -1, node.stack());
                    if (mouseX >= bx && mouseX < bx + 18 && mouseY >= y && mouseY < y + 18) {
                        hovered = node.stack();
                    }
                } else {
                    graphics.fill(nx - 1, y + 2, nx + NODE + 1, y + 16, frame);
                    graphics.fill(nx, y + 3, nx + NODE, y + 15, 0xFF22364A);
                    List<FormattedCharSequence> chip = font.split(Component.literal(node.label()), (int) (NODE / 0.5F));
                    for (int l = 0; l < Math.min(2, chip.size()); l++) {
                        float lw = font.width(chip.get(l)) * 0.5F;
                        PoseStack pose = graphics.pose();
                        pose.pushPose();
                        pose.translate(nx + (NODE - lw) / 2, y + (chip.size() > 1 ? 4 : 7) + l * 5, 0);
                        pose.scale(0.5F, 0.5F, 1);
                        graphics.drawString(font, chip.get(l), 0, 0, 0xFFFFFFFF, false);
                        pose.popPose();
                    }
                }
                if (!node.stack().isEmpty() && !node.label().isEmpty()) {
                    List<FormattedCharSequence> label = font.split(Component.literal(node.label()), (int) ((step - 2) / 0.5F));
                    for (int l = 0; l < Math.min(2, label.size()); l++) {
                        float lw = font.width(label.get(l)) * 0.5F;
                        PoseStack pose = graphics.pose();
                        pose.pushPose();
                        pose.translate(nx + (NODE - lw) / 2, y + 20 + l * 5, 0);
                        pose.scale(0.5F, 0.5F, 1);
                        graphics.drawString(font, label.get(l), 0, 0, 0xFFB8C4D0, false);
                        pose.popPose();
                    }
                }
                if (i + 1 < n) {
                    int ax = nx + NODE + 1, aw = step - NODE - 2;
                    graphics.fill(ax, y + 9, ax + Math.max(1, aw - 3), y + 10, C_ACCENT);
                    graphics.fill(ax + aw - 4, y + 7, ax + aw - 3, y + 12, C_ACCENT);
                    graphics.fill(ax + aw - 3, y + 8, ax + aw - 2, y + 11, C_ACCENT);
                    graphics.fill(ax + aw - 2, y + 9, ax + aw - 1, y + 10, C_ACCENT);
                }
            }
            y += FLOW_ROW;
        }
        return hovered;
    }

    /** Panneau de la page « Ou en suis-je ? » : age en cours et sa barre, carte de la prochaine etape, barre de chaque age. */
    private void drawProgress(GuiGraphics graphics, int x, int y, int cw, int mouseX, int mouseY) {
        List<GuideProgress.Age> ages = progress.ages();
        if (ages.isEmpty()) {
            return;
        }
        List<Chapter> chapters = GuideBook.chapters();
        GuideProgress.Age age = ages.get(progress.current());
        Chapter chapter = chapters.get(age.chapter());
        text(graphics, Component.translatable("guide.pioneers.progress.current"), x, y, C_DIM, 0.6F);
        if (age.total() > 0) {
            Component count = Component.translatable("guide.pioneers.progress.steps", age.done(), age.total());
            text(graphics, count, x + cw - font.width(count) * 0.6F, y, C_DIM, 0.6F);
        }
        graphics.renderItem(chapter.icon(), x, y + 8);
        text(graphics, fit(chapter.title(), cw - 24), x + 20, y + 12, C_TITLE, 1F);
        graphics.fill(x, y + 28, x + cw, y + 32, C_TRACK);
        if (age.total() > 0) {
            graphics.fill(x, y + 28, x + cw * age.done() / age.total(), y + 32, C_ACCENT);
        }
        //Carte de la prochaine etape (cadre d'or comme les etapes cles des schemas)
        Chapter target = chapters.get(progress.nextChapter());
        Page next = target.pages().get(Mth.clamp(progress.nextPage(), 0, target.pages().size() - 1));
        int cy = y + P_CARD;
        boolean hover = mouseX >= x && mouseX < x + cw && mouseY >= cy && mouseY < cy + 30;
        graphics.fill(x, cy, x + cw, cy + 30, C_TITLE);
        graphics.fill(x + 1, cy + 1, x + cw - 1, cy + 29, hover ? 0xFF24405A : C_CHIP);
        graphics.fill(x + 6, cy + 6, x + 24, cy + 24, 0xFF2A3846);
        graphics.fill(x + 7, cy + 7, x + 23, cy + 23, C_TRACK);
        graphics.renderItem(next.items().isEmpty() ? target.icon() : next.items().get(0), x + 7, cy + 7);
        text(graphics, fit(Component.translatable("guide.pioneers.progress.next").getString() + "  ·  " + target.shortTitle(), (cw - 46) / 0.6F),
              x + 30, cy + 6, C_DIM, 0.6F);
        text(graphics, fit(next.title(), (cw - 46) / 0.85F), x + 30, cy + 15, 0xFFFFFFFF, 0.85F);
        graphics.drawString(font, "▶", x + cw - 12, cy + 11, C_ACCENT, false);
        area(x, cy, x + cw, cy + 30, progress.nextChapter(), progress.nextPage(), ItemStack.EMPTY);
        //Les ages
        int ly = y + P_LIST;
        Component label = Component.translatable("guide.pioneers.progress.journey");
        text(graphics, label, x, ly, C_DIM, 0.6F);
        graphics.fill(x + (int) (font.width(label) * 0.6F) + 4, ly + 3, x + cw, ly + 4, 0xFF22303E);
        int bx0 = x + cw * 55 / 100, bx1 = x + cw - 34;
        for (int i = 0; i < ages.size(); i++) {
            GuideProgress.Age row = ages.get(i);
            Chapter entry = chapters.get(row.chapter());
            int ry = ly + 10 + i * P_ROW;
            boolean current = i == progress.current();
            boolean rowHover = mouseX >= x - 2 && mouseX < x + cw + 2 && mouseY >= ry - 1 && mouseY < ry + 12;
            if (current) {
                graphics.fill(x - 2, ry - 1, x + cw + 2, ry + 12, C_SELECT);
            } else if (rowHover) {
                graphics.fill(x - 2, ry - 1, x + cw + 2, ry + 12, C_HOVER);
            }
            icon(graphics, entry.icon(), x, ry, 0.65F);
            int color = current ? 0xFFFFFFFF : row.complete() ? C_OK : i < progress.current() ? 0xFFB8C4D0 : C_DIM;
            text(graphics, fit(entry.shortTitle(), (bx0 - x - 18) / 0.7F), x + 14, ry + 3, color, 0.7F);
            graphics.fill(bx0, ry + 4, bx1, ry + 7, C_TRACK);
            if (row.total() > 0) {
                int bar = row.complete() ? C_OK : current ? C_ACCENT : 0xFF4A6278;
                graphics.fill(bx0, ry + 4, bx0 + (bx1 - bx0) * row.done() / row.total(), ry + 7, bar);
                String count = row.done() + "/" + row.total();
                text(graphics, Component.literal(count), x + cw - 12 - font.width(count) * 0.62F, ry + 3, row.complete() ? C_OK : C_DIM, 0.62F);
                if (row.complete()) {
                    check(graphics, x + cw - 8, ry + 2, C_OK);
                }
            } else {
                text(graphics, Component.literal("—"), x + cw - 12 - font.width("—") * 0.62F, ry + 3, C_DIM, 0.62F);
            }
            area(x - 2, ry - 1, x + cw + 2, ry + 12, row.chapter(), 0, ItemStack.EMPTY);
        }
    }

    /**
     * 1.0.3-beta (lot 2) : reglages de l'affichage a l'ecran : quatre rangees (libelle, valeur cliquable : valeur suivante au clic, sauvee
     * tout de suite) et, a droite, l'ecran en petit avec la place et le nombre d'etapes de l'affichage.
     */
    private void drawHudSettings(GuiGraphics graphics, int x, int y, int cw, int mouseX, int mouseY) {
        //1.0.5-beta (lot 14) : deux rangees de plus pour la fiche au regard (coin, taille)
        Component[] labels = {Component.translatable("guide.pioneers.hud.show"), Component.translatable("guide.pioneers.hud.position"),
              Component.translatable("guide.pioneers.hud.steps"), Component.translatable("guide.pioneers.hud.size"),
              Component.translatable("guide.pioneers.hud.card_corner"), Component.translatable("guide.pioneers.hud.card_size")};
        boolean on = JourneyHudConfig.enabled();
        Component[] values = {Component.translatable(on ? "guide.pioneers.hud.on" : "guide.pioneers.hud.off"),
              Component.translatable("guide.pioneers.hud.position." + JourneyHudConfig.position().name().toLowerCase(Locale.ROOT)),
              Component.literal(String.valueOf(JourneyHudConfig.steps())),
              Component.translatable("guide.pioneers.hud.size." + JourneyHudConfig.size().name().toLowerCase(Locale.ROOT)),
              Component.translatable("guide.pioneers.hud.position." + fr.pioneers.client.look.LookHudConfig.corner().name().toLowerCase(Locale.ROOT)),
              Component.literal(fr.pioneers.client.look.LookHudConfig.scale() + " %")};
        int px = x + cw - HUD_PREVIEW_W, left = px - x - 8, vx = x + Math.min(96, left / 2);
        for (int i = 0; i < labels.length; i++) {
            int ry = y + i * HUD_ROW;
            text(graphics, labels[i], x, ry + 5, C_DIM, 0.75F);
            int vw = Math.max(46, (int) (font.width(values[i]) * 0.75F) + 22), vx1 = Math.min(vx + vw, px - 6);
            boolean hover = mouseX >= vx && mouseX < vx1 && mouseY >= ry && mouseY < ry + HUD_ROW - 3;
            graphics.fill(vx, ry, vx1, ry + HUD_ROW - 3, i == 0 && !on ? 0xFF3A2A2A : C_FRAME);
            graphics.fill(vx + 1, ry + 1, vx1 - 1, ry + HUD_ROW - 4, hover ? 0xFF24405A : C_CHIP);
            text(graphics, fit(values[i].getString(), (vx1 - vx - 14) / 0.75F), vx + 5, ry + 4, i == 0 ? (on ? C_OK : 0xFFE08A7A) : 0xFFFFFFFF, 0.75F);
            graphics.drawString(font, "▶", vx1 - 9, ry + 3, C_ACCENT, false);
            area(vx, ry, vx1, ry + HUD_ROW - 3, -2, i, ItemStack.EMPTY);
        }
        //Apercu : l'ecran de jeu en petit et la place de l'affichage
        int py = y + 2;
        graphics.fill(px, py, px + HUD_PREVIEW_W, py + HUD_PREVIEW_H, 0xFF3C4E3A);
        graphics.fill(px + 1, py + 1, px + HUD_PREVIEW_W - 1, py + HUD_PREVIEW_H - 1, 0xFF5E7C4E);
        graphics.fill(px + 1, py + 1, px + HUD_PREVIEW_W - 1, py + 22, 0xFF7FA6C8);                    //ciel
        graphics.fill(px + HUD_PREVIEW_W / 2 - 22, py + HUD_PREVIEW_H - 6, px + HUD_PREVIEW_W / 2 + 22, py + HUD_PREVIEW_H - 2, 0xFF22262A); //barre
        if (on) {
            float k = JourneyHudConfig.size().scale;
            int bw = (int) (34 * k), bh = (int) ((5 + 5 * JourneyHudConfig.steps()) * k);
            int bx, by;
            switch (JourneyHudConfig.position()) {
                case TOP_LEFT -> { bx = px + 3; by = py + 3; }
                case TOP_RIGHT -> { bx = px + HUD_PREVIEW_W - 3 - bw; by = py + 3; }
                case BOTTOM_LEFT -> { bx = px + 3; by = py + HUD_PREVIEW_H - 8 - bh; }
                case BOTTOM_RIGHT -> { bx = px + HUD_PREVIEW_W - 3 - bw; by = py + HUD_PREVIEW_H - 8 - bh; }
                default -> { bx = px + (HUD_PREVIEW_W - bw) / 2; by = py + 3; }
            }
            graphics.fill(bx, by, bx + bw, by + bh, 0xE0141C26);
            graphics.fill(bx, by, bx + 1, by + bh, C_TITLE);
            for (int r = 0; r < JourneyHudConfig.steps(); r++) {
                int ly = by + 3 + (int) (r * 5 * k);
                graphics.fill(bx + 3, ly, bx + 6, ly + 3, r == 0 ? C_TITLE : 0xFF4A6278);
                graphics.fill(bx + 8, ly + 1, bx + bw - 3, ly + 2, r == 0 ? 0xFFFFFFFF : 0xFF7F8EA0);
            }
        }
        //1.0.5-beta (lot 14) : place de la fiche au regard (ambre)
        fr.pioneers.client.look.LookHudConfig.Corner lc = fr.pioneers.client.look.LookHudConfig.corner();
        int cw2 = Math.max(10, Math.round(22 * fr.pioneers.client.look.LookHudConfig.scale() / 100F)), ch2 = Math.max(6, cw2 * 2 / 3);
        int cx2 = lc.right() ? px + HUD_PREVIEW_W - 3 - cw2 : px + 3, cy2 = lc.bottom() ? py + HUD_PREVIEW_H - 3 - ch2 : py + 3;
        graphics.fill(cx2, cy2, cx2 + cw2, cy2 + ch2, 0xE0101418);
        graphics.fill(cx2, cy2, cx2 + cw2, cy2 + 1, 0xFFF0B040);
        graphics.fill(cx2 + 2, cy2 + 3, cx2 + cw2 - 2, cy2 + 4, 0xFFE0E0E0);
        text(graphics, Component.translatable("guide.pioneers.hud.preview"), px, py + HUD_PREVIEW_H + 2, C_DIM, 0.55F);
    }

    /** Pastille Fait / A faire d'une etape du Periple, calee a droite sur {@code right} ; renvoie sa largeur. */
    private int drawBadge(GuiGraphics graphics, Page p, int right, int y) {
        boolean ok = progress.isDone(p);
        Component label = Component.translatable(ok ? "guide.pioneers.step.done" : "guide.pioneers.step.todo");
        int width = (int) (font.width(label) * 0.62F) + 16, x = right - width;
        graphics.fill(x, y, right, y + 11, ok ? 0xFF1E3A26 : C_TRACK);
        if (ok) {
            check(graphics, x + 3, y + 2, C_OK);
        } else {
            box(graphics, x + 4, y + 3, 5, C_DIM);
        }
        text(graphics, label, x + 13, y + 3, ok ? C_OK : C_DIM, 0.62F);
        return width;
    }

    /** Renvois « Voir aussi : » : une pastille cliquable par page visee (infobulle : chapitre et page). */
    private void drawLinks(GuiGraphics graphics, List<Chip> chips, int x, int y, int mouseX, int mouseY) {
        List<Chapter> chapters = GuideBook.chapters();
        text(graphics, Component.translatable("guide.pioneers.links"), x, y + 3, C_DIM, LINK_SCALE);
        for (Chip chip : chips) {
            int cx = x + chip.x(), cy = y + chip.y();
            boolean hover = mouseX >= cx && mouseX < cx + chip.width() && mouseY >= cy && mouseY < cy + LINK_H;
            graphics.fill(cx, cy, cx + chip.width(), cy + LINK_H, hover ? 0xFF2E5A80 : C_CHIP);
            graphics.fill(cx, cy, cx + 2, cy + LINK_H, C_ACCENT);
            text(graphics, chip.title(), cx + 6, cy + 3, 0xFFD0DAE4, LINK_SCALE);
            area(cx, cy, cx + chip.width(), cy + LINK_H, chip.chapter(), chip.page(), ItemStack.EMPTY);
            if (hover) {
                Chapter target = chapters.get(chip.chapter());
                hoverTip = Component.literal(target.title() + "  ›  " + target.pages().get(chip.page()).title());
            }
        }
    }

    /** Releve une zone cliquable du corps de la page (rognee a la partie visible ; ignoree si elle est cachee). */
    private void area(int x0, int y0, int x1, int y1, int chapterIndex, int pageIndex, ItemStack stack) {
        int top0 = Math.max(y0, clipTop), bottom = Math.min(y1, clipBottom);
        if (bottom > top0) {
            areas.add(new Area(x0, top0, x1, bottom, chapterIndex, pageIndex, stack));
        }
    }

    /** Icone d'objet reduite (sommaire, liste des ages). */
    private static void icon(GuiGraphics graphics, ItemStack stack, int x, int y, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.renderItem(stack, 0, 0);
        pose.popPose();
    }

    /** Coche dessinee (8 x 7 px) : pas de dependance a un glyphe de la police. */
    private static void check(GuiGraphics graphics, int x, int y, int color) {
        int[] dy = {3, 4, 5, 4, 3, 2, 1, 0};
        for (int dx = 0; dx < dy.length; dx++) {
            graphics.fill(x + dx, y + dy[dx], x + dx + 1, y + dy[dx] + 2, color);
        }
    }

    /** Case vide (n x n px) d'une etape pas encore faite. */
    private static void box(GuiGraphics graphics, int x, int y, int n, int color) {
        graphics.fill(x, y, x + n, y + 1, color);
        graphics.fill(x, y + n - 1, x + n, y + n, color);
        graphics.fill(x, y, x + 1, y + n, color);
        graphics.fill(x + n - 1, y, x + n, y + n, color);
    }

    private Component fit(String text, float maxWidth) {
        String plain = text;
        if (font.width(plain) > maxWidth) {
            while (plain.length() > 1 && font.width(plain + "…") > maxWidth) {
                plain = plain.substring(0, plain.length() - 1);
            }
            plain += "…";
        }
        return Component.literal(plain);
    }

    private void text(GuiGraphics graphics, Component text, float x, float y, int color, float scale) {
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, text, 0, 0, color, false);
        pose.popPose();
    }

    // ------------------------------------------------------------------ souris et clavier

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //Zones de la page relevees au dernier rendu : icones (JEI : clic = recettes, clic droit = usages), renvois, progression
        if (!searching()) {
            for (Area area : areas) {
                if (mouseX >= area.x0() && mouseX < area.x1() && mouseY >= area.y0() && mouseY < area.y1()) {
                    if (search.isFocused()) {
                        search.setFocused(false);
                        setFocused(null);
                    }
                    if (area.chapter() == -2) {                    //1.0.3-beta (lot 2) : reglage de l'affichage a l'ecran
                        if (button == 0) {
                            switch (area.page()) {
                                case 0 -> JourneyHudConfig.toggle();
                                case 1 -> JourneyHudConfig.nextPosition();
                                case 2 -> JourneyHudConfig.nextSteps();
                                case 3 -> JourneyHudConfig.nextSize();
                                case 4 -> fr.pioneers.client.look.LookHudConfig.nextCorner();
                                default -> fr.pioneers.client.look.LookHudConfig.nextScale();
                            }
                            Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.UI_BUTTON_CLICK, 1F));
                        }
                        return true;
                    }
                    if (!area.stack().isEmpty()) {
                        if ((button == 0 || button == 1) && GuideLinks.showInJei(area.stack(), button == 1)) {
                            remember();
                            return true;
                        }
                    } else if (button == 0) {
                        openPage(area.chapter(), area.page(), List.of());
                        return true;
                    }
                    break;
                }
            }
        }
        if (button != 0) {
            return super.mouseClicked(mouseX, mouseY, button);
        }
        //1.0.4-beta (lot 29) : partie du guide
        int[] pb = partyButton();
        if (mouseX >= pb[0] && mouseX < pb[0] + pb[2] && mouseY >= pb[1] && mouseY < pb[1] + 16) {
            remember();
            Minecraft.getInstance().setScreen(new PartyScreen(this));
            return true;
        }
        //Fermer, effacer la recherche
        int cx = left + w - 18, cy = top + 5;
        if (mouseX >= cx && mouseX < cx + 14 && mouseY >= cy && mouseY < cy + 16) {
            onClose();
            return true;
        }
        int sx2 = search.getX() + search.getWidth() + 4, sy = top + 5;
        if (!search.getValue().isEmpty() && mouseX >= sx2 - 12 && mouseX < sx2 && mouseY >= sy && mouseY < sy + 16) {
            leaveSearch();
            return true;
        }
        if (mouseX >= search.getX() - 16 && mouseX < sx2 && mouseY >= sy && mouseY < sy + 16) {
            setFocused(search);
            search.setFocused(true);
            return super.mouseClicked(mouseX, mouseY, button);
        }
        //Sommaire
        int y0 = sideTop();
        if (mouseX >= left && mouseX < left + SIDEBAR && mouseY >= y0 && mouseY < sideBottom()) {
            for (Row row : rows()) {
                int y = y0 + row.y() - sideScroll;
                if (mouseY >= y && mouseY < y + row.height()) {
                    if (row.kind() == 1) {
                        if (searching()) {
                            leaveSearch();
                        }
                        selectChapter(row.chapter());
                    } else if (row.kind() == 2) {
                        openPage(row.chapter(), row.page(), List.of());
                    }
                    return true;
                }
            }
            return true;
        }
        //Resultats de recherche
        if (searching()) {
            int x = contentX(), cw = contentW(), ry0 = top + HEAD + 26;
            if (mouseX >= x && mouseX < x + cw && mouseY >= ry0 && mouseY < top + h - 6) {
                int i = (int) ((mouseY - ry0 + resultScroll) / RESULT_H);
                if (i >= 0 && i < hits.size()) {
                    List<String> terms = GuideSearch.terms(query);
                    GuideSearch.Hit hit = hits.get(i);
                    leaveSearch();
                    openPage(hit.chapter(), hit.page(), terms);
                    return true;
                }
            }
        } else {
            //Pied : page precedente / suivante
            int fy = top + h - FOOT + 4, x0 = left + SIDEBAR + 8, x1 = left + w - 8, bw = (x1 - x0 - 60) / 2;
            if (mouseY >= fy && mouseY < fy + 14) {
                if (mouseX >= x0 && mouseX < x0 + bw) {
                    turn(-1);
                    return true;
                } else if (mouseX >= x1 - bw && mouseX < x1) {
                    turn(1);
                    return true;
                }
            }
        }
        if (search.isFocused()) {
            search.setFocused(false);
            setFocused(null);
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int step = (int) Math.round(-delta * 3 * LINE * TEXT_SCALE);
        if (mouseX < left + SIDEBAR) {
            sideScroll = Mth.clamp(sideScroll + (int) Math.round(-delta * 3 * PAGE_ROW), 0, sideMax(rows()));
        } else if (searching()) {
            resultScroll += (int) Math.round(-delta * RESULT_H);
        } else {
            scroll = Mth.clamp(scroll + step, 0, maxScroll());
            lastScroll = scroll;
        }
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        boolean ctrl = (modifiers & GLFW.GLFW_MOD_CONTROL) != 0;
        if (search.isFocused()) {
            if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
                if (search.getValue().isEmpty()) {
                    search.setFocused(false);
                    setFocused(null);
                } else {
                    search.setValue("");
                }
                return true;
            }
            if ((keyCode == GLFW.GLFW_KEY_ENTER || keyCode == GLFW.GLFW_KEY_KP_ENTER) && !hits.isEmpty()) {
                List<String> terms = GuideSearch.terms(query);
                GuideSearch.Hit hit = hits.get(0);
                leaveSearch();
                openPage(hit.chapter(), hit.page(), terms);
                return true;
            }
            return search.keyPressed(keyCode, scanCode, modifiers) || search.canConsumeInput() || super.keyPressed(keyCode, scanCode, modifiers);
        }
        if ((ctrl && keyCode == GLFW.GLFW_KEY_F) || keyCode == GLFW.GLFW_KEY_SLASH) {
            setFocused(search);
            search.setFocused(true);
            return true;
        }
        if (keyCode == GLFW.GLFW_KEY_ESCAPE && searching()) {
            leaveSearch();
            return true;
        }
        if (PiKeys.GUIDE.matches(keyCode, scanCode)) {
            onClose();
            return true;
        }
        switch (keyCode) {
            case GLFW.GLFW_KEY_RIGHT -> turn(1);
            case GLFW.GLFW_KEY_LEFT -> turn(-1);
            case GLFW.GLFW_KEY_DOWN -> selectChapter(Math.min(GuideBook.chapters().size() - 1, chapter + 1));
            case GLFW.GLFW_KEY_UP -> selectChapter(Math.max(0, chapter - 1));
            case GLFW.GLFW_KEY_PAGE_DOWN -> scroll = Mth.clamp(scroll + (bodyBottom() - bodyTop()) - 20, 0, maxScroll());
            case GLFW.GLFW_KEY_PAGE_UP -> scroll = Mth.clamp(scroll - (bodyBottom() - bodyTop()) + 20, 0, maxScroll());
            case GLFW.GLFW_KEY_HOME -> scroll = 0;
            case GLFW.GLFW_KEY_END -> scroll = maxScroll();
            default -> {
                return super.keyPressed(keyCode, scanCode, modifiers);
            }
        }
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    @Override
    public void onClose() {
        remember();
        super.onClose();
    }
}
