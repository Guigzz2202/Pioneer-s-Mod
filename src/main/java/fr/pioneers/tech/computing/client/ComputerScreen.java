package fr.pioneers.tech.computing.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.tech.computing.ComputerMenu;
import fr.pioneers.tech.computing.ComputerTile;
import fr.pioneers.tech.computing.DiskFiles;
import fr.pioneers.tech.computing.script.GeneratedPioDoc;
import fr.pioneers.tech.computing.script.Lexer;
import fr.pioneers.core.Texts;
import fr.pioneers.core.network.PacketComputer;
import fr.pioneers.core.network.PacketTileAction;
import fr.pioneers.core.network.PiNetwork;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.glfw.GLFW;

/**
 * Terminal de l'ordinateur (0.17.1) : liste des fichiers des trois lecteurs (c: interne, a: et b: disquettes), editeur de programme
 * (numeros de ligne, coloration PioScript, indentation automatique, selection, copier / couper / coller), boutons Lancer / Arreter /
 * Sauver / Nouveau / Supprimer, console avec saisie (pour {@code input()}), machines liees. Raccourcis : Ctrl+S sauver, F5 ou Ctrl+R lancer,
 * Ctrl+A tout selectionner, Tab 4 espaces. L'editeur garde le texte cote client ; le serveur ne recoit que les sauvegardes.
 * 0.20.41 : aide integree (bouton « ? » ou F1, mot sous le curseur) a la place de l'editeur : categories, recherche, signature, description
 * et exemple de chaque fonction ({@link GeneratedPioDoc}, textes dans la langue) ; clic sur une fonction : l'insere dans le programme.
 */
public class ComputerScreen extends PiMenuScreen<ComputerMenu> {

    private static final int LINE_H = 10, GUTTER = 22, LEFT = 104, CONSOLE_H = 62;
    private static final int C_TEXT = 0xFFDCEFE6, C_KEYWORD = 0xFFC98CFF, C_STRING = 0xFFA8E06A, C_NUMBER = 0xFFFFB45A, C_COMMENT = 0xFF6F8580,
          C_BUILTIN = 0xFF6AD2FF, C_GUTTER = 0xFF4E6260, C_CURSOR = 0xFFFFFFFF, C_SELECT = 0x803F7FD0, C_ERROR = 0xFFFF6A6A, C_INFO = 0xFF9DB7B0,
          C_ECHO = 0xFF7FD0FF;
    private static final Set<String> BUILTINS = builtins();
    private static final int C_SIG = 0xFF6AD2FF, C_EXAMPLE = 0xFFA8E06A, C_NEW = 0xFFFFB45A;

    private final List<StringBuilder> lines = new ArrayList<>(List.of(new StringBuilder()));
    private int row, col, anchorRow = -1, anchorCol, scroll, consoleScroll, hscroll, fileScroll;
    private static final int FILES_TOP = 18;
    private String fileName = "main.py";
    private boolean dirty, renaming, consoleFocus;
    private final StringBuilder consoleInput = new StringBuilder();
    private int blink;
    /** 0.20.41 : aide (a la place de l'editeur) : categorie choisie ("" = toutes), recherche, defilement (px). */
    private boolean helpOpen, helpFocus;
    private String helpCat = "";
    private final StringBuilder helpSearch = new StringBuilder();
    private int helpScroll, helpContent, helpCatScroll;

    /** Noms colores comme fonctions : ceux de base et les modules documentes. */
    private static Set<String> builtins() {
        Set<String> out = new HashSet<>(Set.of("print", "len", "str", "int", "float", "bool", "abs", "min", "max", "round", "sum",
              "sorted", "list", "dict", "range", "type", "chr", "ord", "random", "randint", "sleep", "time", "input", "redstone", "link", "screen", "fs"));
        for (String[] e : GeneratedPioDoc.ENTRIES) {
            if (!e[0].equals("syntax") && !e[1].startsWith("str.") && !e[1].startsWith("list.") && !e[1].startsWith("dict.") && !e[1].startsWith("link.")) {
                out.add(e[1].contains(".") ? e[1].substring(0, e[1].indexOf('.')) : e[1]);
            }
        }
        return Set.copyOf(out);
    }

    public ComputerScreen(ComputerMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 440, 270);
        inventoryLabelY = -1000;
        titleLabelY = -1000;
    }

    @Override
    protected void init() {
        //Le terminal suit la taille de la fenetre (de 280 x 180 a 480 x 300) : tout se place d'apres imageWidth / imageHeight
        imageWidth = Math.max(280, Math.min(width - 8, 480));
        imageHeight = Math.max(180, Math.min(height - 8, 300));
        super.init();
        ComputerTile t = tile();
        if (t != null && !t.program().isEmpty() && lines.size() == 1 && lines.get(0).length() == 0) {
            open(t.program());
        }
    }

    private ComputerTile tile() {
        return menu.getTile() instanceof ComputerTile t ? t : null;
    }

    // ------------------------------------------------------------------
    // Reseau
    // ------------------------------------------------------------------

    /** Contenu d'un fichier recu du serveur : charge dans l'editeur si ce terminal est ouvert. */
    /** 0.20.46 (audit) : console, fichiers et liaisons de l'ordinateur (arrivent avant ou pendant l'ouverture de l'interface). */
    public static void receiveState(BlockPos pos, CompoundTag tag) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null && minecraft.level.getBlockEntity(pos) instanceof ComputerTile t) {
            t.readViewer(tag);
        }
    }

    public static void receive(BlockPos pos, String path, String text) {
        if (Minecraft.getInstance().screen instanceof ComputerScreen s && s.tile() != null && s.tile().getBlockPos().equals(pos)) {
            s.load(path, text);
        }
    }

    private void load(String path, String text) {
        lines.clear();
        for (String l : text.split("\n", -1)) {
            lines.add(new StringBuilder(l));
        }
        if (lines.isEmpty()) {
            lines.add(new StringBuilder());
        }
        fileName = path;
        row = col = scroll = hscroll = 0;
        anchorRow = -1;
        dirty = false;
    }

    private void open(String path) {
        send(PacketComputer.OPEN, path, "");
    }

    private void send(int kind, String path, String text) {
        ComputerTile t = tile();
        if (t == null) {
            return;
        }
        if (kind != PacketComputer.SAVE) {
            PiNetwork.sendToServer(new PacketComputer.Action(t.getBlockPos(), kind, path, text, 0, 1));
            return;
        }
        int parts = Math.max(1, (text.length() + PacketComputer.CHUNK - 1) / PacketComputer.CHUNK);
        for (int i = 0; i < parts; i++) {
            String chunk = text.substring(i * PacketComputer.CHUNK, Math.min(text.length(), (i + 1) * PacketComputer.CHUNK));
            PiNetwork.sendToServer(new PacketComputer.Action(t.getBlockPos(), kind, path, chunk, i, parts));
        }
    }

    private void action(int action) {
        ComputerTile t = tile();
        if (t != null) {
            PiNetwork.sendToServer(new PacketTileAction(t.getBlockPos(), action));
        }
    }

    private String text() {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            if (i > 0) {
                b.append('\n');
            }
            b.append(lines.get(i));
        }
        return b.toString();
    }

    private boolean validPath() {
        return DiskFiles.validName(ComputerTile.fileName(fileName)) && ComputerTile.drive(fileName) >= 0;
    }

    private void save() {
        if (!validPath()) {
            renaming = true;
            return;
        }
        String t = text();
        if (t.length() > PacketComputer.MAX_TEXT) {
            return;
        }
        send(PacketComputer.SAVE, fileName, t);
        dirty = false;
    }

    private void run() {
        save();
        if (validPath()) {
            send(PacketComputer.RUN, fileName, "");
        }
    }

    // ------------------------------------------------------------------
    // Dessin
    // ------------------------------------------------------------------

    private int editorTop() {
        return 34;
    }

    private int editorBottom() {
        return imageHeight - CONSOLE_H - 18;
    }

    /** Bas de la liste des fichiers (au-dessus des liaisons). */
    private int filesBottom() {
        return imageHeight - 78;
    }

    /** Une ligne de la liste des fichiers entierement visible (on ne clique que sur ce qu'on voit). */
    private boolean inFiles(int y, int h) {
        return y >= FILES_TOP - 2 && y + h <= filesBottom() + 1;
    }

    private int visibleLines() {
        return Math.max(1, (editorBottom() - editorTop() - 4) / LINE_H);
    }

    @Override
    protected void drawBackground(@NotNull GuiGraphics g, int mx, int my) {
        ComputerTile t = tile();
        if (t == null) {
            return;
        }
        blink++;
        int w = imageWidth, h = imageHeight;
        //Barre du haut : titre, etat, energie (l'etat prend la place laissee par l'energie, sans la chevaucher)
        GuiDraw.text(g, font, Component.translatable("block.pioneers.computer"), 6, 6, 1F, TEXT, LEFT - 12);
        int status = t.status();
        int sc = switch (status) {
            case ComputerTile.RUNNING -> ACCENT;
            case ComputerTile.SLEEPING, ComputerTile.INPUT -> 0xFF6AD2FF;
            case ComputerTile.NO_ENERGY, ComputerTile.PAUSED -> 0xFFE0A030;
            default -> DIM;
        };
        Component energy = Component.literal(Texts.energy(t.energyStored()) + " / " + Texts.energy(t.energyCapacity()));
        float energyW = font.width(energy) * 0.7F;
        GuiDraw.right(g, font, energy, w - 6, 7, 0.7F, DIM);
        g.fill(LEFT, 7, LEFT + 6, 13, sc);
        GuiDraw.text(g, font, Component.translatable("computing.pioneers.computer.status." + status), LEFT + 10, 6, 0.8F, sc,
              Math.max(20, w - 6 - energyW - 8 - (LEFT + 10)));
        //Colonne de gauche : lecteurs et fichiers (liste qui defile a la molette)
        int fb = filesBottom();
        g.enableScissor(leftPos, topPos + FILES_TOP - 2, leftPos + LEFT - 4, topPos + fb + 1);
        int y = FILES_TOP - fileScroll;
        for (int d : new int[]{2, 0, 1}) {
            y = drive(g, mx, my, t, d, y);
        }
        g.disableScissor();
        int content = y + fileScroll - FILES_TOP, room = fb - FILES_TOP;
        if (content > room) {
            fileScroll = Math.min(fileScroll, content - room);
            int bh = Math.max(8, room * room / content);
            int by = FILES_TOP + (room - bh) * fileScroll / Math.max(1, content - room);
            g.fill(LEFT - 5, by, LEFT - 3, by + bh, 0xFF3A5A50);
        } else {
            fileScroll = 0;
        }
        //Liaisons
        int ly = h - 64;
        label(g, Component.translatable("computing.pioneers.gui.links"), 6, ly - 10);
        for (int i = 0; i < ComputerTile.LINKS; i++) {
            int yy = ly + i * 12;
            screen(g, 6, yy, LEFT - 12, 11);
            boolean linked = t.clientLinked(i);
            GuiDraw.text(g, font, Component.literal((i + 1) + " ").append(linked ? t.linkName(i) : Component.translatable("computing.pioneers.gui.free_link")),
                  9, yy + 2, 0.6F, linked ? SCREEN_TEXT : 0xFF59666A, LEFT - 30);
            if (linked) {
                int link = i;
                button(g, mx, my, LEFT - 16, yy + 1, 9, 9, Component.literal("×"), true, () -> action(ComputerTile.UNLINK + link));
            }
        }
        //Nom du fichier et boutons : le nom prend ce que les quatre boutons laissent (0.20.41 : bouton d'aide « ? » au bout)
        button(g, mx, my, w - 6 - 14, 18, 14, 13, Component.translatable("pioscript.help.button"), true, () -> toggleHelp(""));
        int x0 = LEFT, avail = w - 6 - 16 - LEFT;
        int nameW = Math.max(60, Math.min(170, avail - 4 - 4 * 46 - 6));
        int bw = (avail - nameW - 4 - 6) / 4;
        screen(g, x0, 18, nameW, 13);
        String shown = (dirty ? "* " : "") + fileName;
        GuiDraw.text(g, font, Component.literal(shown), x0 + 3, 21, 0.8F, renaming ? 0xFFFFE080 : TEXT, nameW - 6);
        if (renaming && blink / 10 % 2 == 0) {
            int cx = x0 + 3 + (int) Math.min(nameW - 5, font.width(shown) * 0.8F);
            g.fill(cx, 20, cx + 1, 29, C_CURSOR);
        }
        click(x0, 18, nameW, 13, () -> {
            renaming = true;
            consoleFocus = false;
        });
        int bx = x0 + nameW + 4;
        button(g, mx, my, bx, 18, bw, 13, Component.translatable("computing.pioneers.computer.run"), true, this::run);
        button(g, mx, my, bx + (bw + 2), 18, bw, 13, Component.translatable("computing.pioneers.computer.stop"), t.status() != ComputerTile.IDLE,
              () -> action(ComputerTile.STOP));
        button(g, mx, my, bx + 2 * (bw + 2), 18, bw, 13, Component.translatable("computing.pioneers.computer.save"), true, this::save);
        button(g, mx, my, bx + 3 * (bw + 2), 18, bw, 13, Component.translatable("computing.pioneers.computer.new"), true, () -> {
            load("nouveau.py", "");
            renaming = true;
        });
        //Editeur (ou l'aide)
        if (helpOpen) {
            help(g, mx, my);
        } else {
            editor(g, mx, my);
        }
        //Console
        console(g, mx, my, t);
    }

    private void label(GuiGraphics g, Component text, int x, int y) {
        GuiDraw.text(g, font, text, x, y, 0.7F, DIM, LEFT - 10);
    }

    private int drive(GuiGraphics g, int mx, int my, ComputerTile t, int d, int y) {
        String letter = d == 2 ? "c" : d == 0 ? "a" : "b";
        boolean present = d == 2 || t.hasDisk(d);
        int cap = d == 2 ? DiskFiles.INTERNAL_CAPACITY : DiskFiles.DISK_CAPACITY;
        Component head = Component.translatable("computing.pioneers.computer.drive." + letter);
        label(g, head, 6, y);
        if (d < 2 && present && inFiles(y - 2, 9)) {
            button(g, mx, my, LEFT - 22, y - 2, 14, 9, Component.literal("⏏"), true, () -> action(d == 0 ? ComputerTile.EJECT_A : ComputerTile.EJECT_B));
        }
        y += 9;
        if (!present) {
            GuiDraw.text(g, font, Component.translatable("computing.pioneers.computer.no_disk"), 9, y, 0.6F, 0xFF59666A, LEFT - 14);
            return y + 11;
        }
        GuiDraw.bar(g, 6, y, LEFT - 14, 2, t.clientUsed(d) / (double) cap, ACCENT);
        y += 4;
        for (String f : t.clientFiles(d)) {
            String path = d == 2 ? f : letter + ":" + f;
            boolean current = path.equals(fileName);
            boolean visible = inFiles(y, 9);
            boolean over = visible && over(mx, my, 6, y, LEFT - 12, 9);
            if (current || over) {
                g.fill(6, y, LEFT - 8, y + 9, current ? 0xFF1E3A34 : 0xFF16262A);
            }
            GuiDraw.text(g, font, Component.literal(f), 9, y + 1, 0.65F, current ? ACCENT : TEXT, LEFT - 30);
            if (visible) {
                click(6, y, LEFT - 26, 9, () -> open(path));
                button(g, mx, my, LEFT - 18, y, 9, 9, Component.literal("×"), true, () -> send(PacketComputer.DELETE, path, ""));
            }
            y += 10;
        }
        return y + 4;
    }

    private void editor(GuiGraphics g, int mx, int my) {
        int x0 = LEFT, y0 = editorTop(), x1 = imageWidth - 6, y1 = editorBottom();
        g.fill(x0, y0, x1, y1, 0xFF0A1014);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, consoleFocus || renaming ? EDGE : 0xFF3A5A50);
        g.fill(x0 + 1, y0 + 1, x0 + GUTTER, y1 - 1, 0xFF0F171B);
        int vis = visibleLines();
        clampScroll();
        //Defilement horizontal : le curseur reste visible sur les longues lignes
        int textX = x0 + GUTTER + 3, room = x1 - 5 - textX;
        int cursorPx = font.width(lines.get(row).substring(0, Math.min(col, lines.get(row).length())));
        if (cursorPx - hscroll > room) {
            hscroll = cursorPx - room + 24;
        } else if (cursorPx < hscroll) {
            hscroll = Math.max(0, cursorPx - 24);
        }
        int[] sel = selection();
        for (int i = 0; i < vis && scroll + i < lines.size(); i++) {
            int li = scroll + i;
            String n = String.valueOf(li + 1);
            g.drawString(font, n, x0 + GUTTER - 3 - font.width(n), y0 + 3 + i * LINE_H, li == row ? TEXT : C_GUTTER, false);
        }
        g.enableScissor(leftPos + x0 + GUTTER + 1, topPos + y0 + 1, leftPos + x1 - 1, topPos + y1 - 1);
        for (int i = 0; i < vis && scroll + i < lines.size(); i++) {
            int li = scroll + i;
            int y = y0 + 3 + i * LINE_H;
            String line = lines.get(li).toString();
            int tx = textX - hscroll;
            //Selection
            if (sel != null && li >= sel[0] && li <= sel[2]) {
                int a = li == sel[0] ? sel[1] : 0, b = li == sel[2] ? sel[3] : line.length();
                int sx = tx + font.width(line.substring(0, Math.min(a, line.length())));
                int ex = tx + font.width(line.substring(0, Math.min(b, line.length()))) + (li < sel[2] ? 4 : 0);
                g.fill(sx, y - 1, ex, y + 9, C_SELECT);
            }
            colored(g, line, tx, y);
            if (li == row && !renaming && !consoleFocus && blink / 10 % 2 == 0) {
                int cx = tx + font.width(line.substring(0, Math.min(col, line.length())));
                g.fill(cx, y - 1, cx + 1, y + 9, C_CURSOR);
            }
        }
        g.disableScissor();
        //Barre de defilement
        if (lines.size() > vis) {
            int h = y1 - y0 - 2;
            int bh = Math.max(8, h * vis / lines.size());
            int by = y0 + 1 + (h - bh) * scroll / Math.max(1, lines.size() - vis);
            g.fill(x1 - 3, by, x1 - 1, by + bh, 0xFF3A5A50);
        }
        //Position du curseur, entre l'editeur et la console (a gauche du bouton Effacer)
        GuiDraw.right(g, font, Component.literal((row + 1) + ":" + (col + 1) + "  " + lines.size()), x1 - 40, y1 + 4, 0.6F, DIM);
    }

    /** Coloration d'une ligne : commentaires, chaines, nombres, mots-cles, fonctions de base. */
    private void colored(GuiGraphics g, String line, int x, int y) {
        int i = 0, n = line.length();
        while (i < n) {
            char c = line.charAt(i);
            int j = i + 1, color = C_TEXT;
            if (c == '#') {
                j = n;
                color = C_COMMENT;
            } else if (c == '"' || c == '\'') {
                while (j < n && line.charAt(j) != c) {
                    j += line.charAt(j) == '\\' ? 2 : 1;
                }
                j = Math.min(n, j + 1);
                color = C_STRING;
            } else if (Character.isDigit(c)) {
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '.')) {
                    j++;
                }
                color = C_NUMBER;
            } else if (Character.isLetter(c) || c == '_') {
                while (j < n && (Character.isLetterOrDigit(line.charAt(j)) || line.charAt(j) == '_')) {
                    j++;
                }
                String word = line.substring(i, j);
                if ((word.equals("f") || word.equals("F")) && j < n && (line.charAt(j) == '"' || line.charAt(j) == '\'')) {
                    char q = line.charAt(j);
                    j++;
                    while (j < n && line.charAt(j) != q) {
                        j++;
                    }
                    j = Math.min(n, j + 1);
                    color = C_STRING;
                } else {
                    color = Lexer.KEYWORDS.contains(word) ? C_KEYWORD : BUILTINS.contains(word) ? C_BUILTIN : C_TEXT;
                }
            }
            String part = line.substring(i, j);
            g.drawString(font, part, x, y, color, false);
            x += font.width(part);
            i = j;
        }
    }

    // ------------------------------------------------------------------
    // Aide (0.20.41)
    // ------------------------------------------------------------------

    private void toggleHelp(String query) {
        helpOpen = !helpOpen || !query.isEmpty();
        helpSearch.setLength(0);
        helpSearch.append(query);
        helpCat = "";
        helpScroll = 0;
        helpFocus = helpOpen;
        renaming = false;
        consoleFocus = false;
    }

    /** Mot sous le curseur de l'editeur (lettres, chiffres, _ et .). */
    private String wordAtCursor() {
        String l = lines.get(row).toString();
        int a = Math.min(col, l.length()), b = a;
        while (a > 0 && (Character.isLetterOrDigit(l.charAt(a - 1)) || l.charAt(a - 1) == '_' || l.charAt(a - 1) == '.')) {
            a--;
        }
        while (b < l.length() && (Character.isLetterOrDigit(l.charAt(b)) || l.charAt(b) == '_' || l.charAt(b) == '.')) {
            b++;
        }
        String w = l.substring(a, b);
        while (w.startsWith(".")) {
            w = w.substring(1);
        }
        return w;
    }

    /** Recherche sans accents ni majuscules (« meteo » trouve « météo »). */
    private static String fold(String s) {
        return java.text.Normalizer.normalize(s, java.text.Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    private List<String[]> helpEntries() {
        String q = fold(helpSearch.toString().trim());
        List<String[]> out = new ArrayList<>();
        for (String[] e : GeneratedPioDoc.ENTRIES) {
            if (!helpCat.isEmpty() && !e[0].equals(helpCat)) {
                continue;
            }
            if (!q.isEmpty()) {
                String hay = fold(e[1] + " " + I18n.get("pioscript.doc." + e[1] + ".sig") + " "
                      + I18n.get("pioscript.doc." + e[1] + ".desc"));
                if (!hay.contains(q)) {
                    continue;
                }
            }
            out.add(e);
        }
        return out;
    }

    private void help(GuiGraphics g, int mx, int my) {
        int x0 = LEFT, y0 = editorTop(), x1 = imageWidth - 6, y1 = editorBottom();
        g.fill(x0, y0, x1, y1, 0xFF0A1014);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, EDGE);
        //Recherche et titre
        int sw = Math.min(130, (x1 - x0) / 3);
        screen(g, x0 + 3, y0 + 3, sw, 11);
        Component q = helpSearch.length() == 0 && !helpFocus ? Component.translatable("pioscript.help.search") : Component.literal(helpSearch.toString());
        GuiDraw.text(g, font, q, x0 + 6, y0 + 5, 0.7F, helpSearch.length() == 0 ? DIM : TEXT, sw - 6);
        if (helpFocus && blink / 10 % 2 == 0) {
            int cx = x0 + 6 + (int) (font.width(helpSearch.toString()) * 0.7F);
            g.fill(cx, y0 + 4, cx + 1, y0 + 13, C_CURSOR);
        }
        click(x0 + 3, y0 + 3, sw, 11, () -> helpFocus = true);
        GuiDraw.wrapped(g, font, Component.translatable("pioscript.help.hint"), x0 + sw + 8, y0 + 3, 0.55F, DIM, x1 - x0 - sw - 12, 2, 6);
        //Categories
        int cw = Math.min(92, (x1 - x0) / 4), ty = y0 + 18;
        List<String> cats = new ArrayList<>();
        cats.add("");
        cats.addAll(List.of(GeneratedPioDoc.CATEGORIES));
        //Liste des categories : defile a la molette quand le terminal est petit
        int fits = Math.max(1, (y1 - 2 - ty) / 9);
        helpCatScroll = Math.max(0, Math.min(helpCatScroll, cats.size() - fits));
        int cy = ty;
        for (String c : cats.subList(helpCatScroll, cats.size())) {
            if (cy + 8 > y1 - 2) {
                break;
            }
            boolean sel = c.equals(helpCat);
            boolean over = over(mx, my, x0 + 2, cy, cw, 8);
            if (sel || over) {
                g.fill(x0 + 2, cy, x0 + 2 + cw, cy + 8, sel ? 0xFF1E3A34 : 0xFF16262A);
            }
            Component name = c.isEmpty() ? Component.translatable("pioscript.help.all") : Component.translatable("pioscript.cat." + c);
            GuiDraw.text(g, font, name, x0 + 4, cy + 1, 0.6F, sel ? ACCENT : TEXT, cw - 4);
            String cat = c;
            click(x0 + 2, cy, cw, 8, () -> {
                helpCat = cat;
                helpScroll = 0;
            });
            cy += 9;
        }
        //Entrees
        int ex0 = x0 + cw + 6, ew = x1 - 4 - ex0;
        List<String[]> entries = helpEntries();
        g.enableScissor(leftPos + ex0, topPos + ty, leftPos + x1 - 2, topPos + y1 - 2);
        int y = ty - helpScroll;
        if (entries.isEmpty()) {
            GuiDraw.text(g, font, Component.translatable("pioscript.help.none"), ex0, y, 0.7F, DIM, ew);
        }
        for (String[] e : entries) {
            Component sig = Component.translatable("pioscript.doc." + e[1] + ".sig");
            boolean visible = y + 8 > ty && y < y1 - 2;
            boolean over = visible && over(mx, my, ex0, y, ew, 8);
            if (over) {
                g.fill(ex0 - 1, y - 1, ex0 + ew, y + 8, 0xFF16262A);
            }
            GuiDraw.text(g, font, sig, ex0, y, 0.72F, over ? 0xFFFFFFFF : C_SIG, ew - 30);
            if (e[4].equals("1")) {
                GuiDraw.right(g, font, Component.translatable("pioscript.help.new"), ex0 + ew - 1, y + 1, 0.5F, C_NEW);
            }
            if (visible && !e[2].isEmpty()) {
                String ins = e[2];
                click(ex0, Math.max(y, ty), ew, Math.min(8, y1 - 2 - Math.max(y, ty)), () -> {
                    helpOpen = false;
                    helpFocus = false;
                    insert(ins);
                    clampScroll();
                });
            }
            y += 9;
            y += 6 * GuiDraw.wrapped(g, font, Component.translatable("pioscript.doc." + e[1] + ".desc"), ex0 + 4, y, 0.6F, TEXT, ew - 6, 8, 6);
            if (!e[3].isEmpty()) {
                GuiDraw.text(g, font, Component.literal(e[3]), ex0 + 4, y, 0.6F, C_EXAMPLE, ew - 6);
                y += 7;
            }
            y += 4;
        }
        g.disableScissor();
        helpContent = y + helpScroll - ty;
        int room = y1 - 2 - ty;
        if (helpContent > room) {
            helpScroll = Math.min(helpScroll, helpContent - room);
            int bh = Math.max(8, room * room / helpContent);
            int by = ty + (room - bh) * helpScroll / Math.max(1, helpContent - room);
            g.fill(x1 - 3, by, x1 - 1, by + bh, 0xFF3A5A50);
        } else {
            helpScroll = 0;
        }
    }

    private void console(GuiGraphics g, int mx, int my, ComputerTile t) {
        int x0 = LEFT, y0 = imageHeight - CONSOLE_H - 4, x1 = imageWidth - 6, y1 = imageHeight - 18;
        g.fill(x0, y0, x1, y1, 0xFF050A08);
        g.renderOutline(x0, y0, x1 - x0, y1 - y0, EDGE);
        List<ComputerTile.ConsoleLine> out = t.console();
        int rows = (y1 - y0 - 4) / 8;
        int first = Math.max(0, out.size() - rows - consoleScroll);
        for (int i = 0; i < rows && first + i < out.size(); i++) {
            ComputerTile.ConsoleLine l = out.get(first + i);
            int color = switch (l.kind()) {
                case ComputerTile.ConsoleLine.ERROR -> C_ERROR;
                case ComputerTile.ConsoleLine.INFO -> C_INFO;
                case ComputerTile.ConsoleLine.ECHO -> C_ECHO;
                default -> SCREEN_TEXT;
            };
            GuiDraw.text(g, font, l.component(), x0 + 3, y0 + 2 + i * 8, 0.75F, color, x1 - x0 - 8);
        }
        button(g, mx, my, x1 - 34, y0 - 12, 34, 10, Component.translatable("computing.pioneers.computer.clear"), true, () -> action(ComputerTile.CLEAR));
        //Saisie
        int iy = imageHeight - 16;
        screen(g, x0, iy, x1 - x0, 12);
        boolean waiting = t.status() == ComputerTile.INPUT;
        Component prompt = consoleInput.length() == 0 && !consoleFocus
                           ? Component.translatable(waiting ? "computing.pioneers.computer.input_waiting" : "computing.pioneers.computer.input_hint")
                           : Component.literal("> " + consoleInput);
        GuiDraw.text(g, font, prompt, x0 + 3, iy + 3, 0.75F, waiting ? 0xFF6AD2FF : consoleFocus ? TEXT : DIM, x1 - x0 - 8);
        if (consoleFocus && blink / 10 % 2 == 0) {
            int cx = x0 + 3 + (int) (font.width("> " + consoleInput) * 0.75F);
            g.fill(cx, iy + 2, cx + 1, iy + 10, C_CURSOR);
        }
        click(x0, iy, x1 - x0, 12, () -> {
            consoleFocus = true;
            renaming = false;
        });
    }

    // ------------------------------------------------------------------
    // Edition
    // ------------------------------------------------------------------

    private void clampScroll() {
        int vis = visibleLines();
        if (row < scroll) {
            scroll = row;
        } else if (row >= scroll + vis) {
            scroll = row - vis + 1;
        }
        scroll = Math.max(0, Math.min(scroll, Math.max(0, lines.size() - vis)));
    }

    /** Selection ordonnee {ligne debut, colonne debut, ligne fin, colonne fin}, ou null. */
    private int[] selection() {
        if (anchorRow < 0 || anchorRow == row && anchorCol == col) {
            return null;
        }
        boolean before = anchorRow < row || anchorRow == row && anchorCol < col;
        return before ? new int[]{anchorRow, anchorCol, row, col} : new int[]{row, col, anchorRow, anchorCol};
    }

    private String selectedText() {
        int[] s = selection();
        if (s == null) {
            return "";
        }
        StringBuilder b = new StringBuilder();
        for (int i = s[0]; i <= s[2]; i++) {
            String l = lines.get(i).toString();
            int a = i == s[0] ? Math.min(s[1], l.length()) : 0, e = i == s[2] ? Math.min(s[3], l.length()) : l.length();
            b.append(l, a, e);
            if (i < s[2]) {
                b.append('\n');
            }
        }
        return b.toString();
    }

    /** Efface la selection ; vrai s'il y en avait une. */
    private boolean deleteSelection() {
        int[] s = selection();
        anchorRow = -1;
        if (s == null) {
            return false;
        }
        String head = lines.get(s[0]).substring(0, Math.min(s[1], lines.get(s[0]).length()));
        String tail = lines.get(s[2]).substring(Math.min(s[3], lines.get(s[2]).length()));
        for (int i = s[2]; i > s[0]; i--) {
            lines.remove(i);
        }
        lines.set(s[0], new StringBuilder(head).append(tail));
        row = s[0];
        col = head.length();
        dirty = true;
        return true;
    }

    private void insert(String text) {
        deleteSelection();
        String[] parts = text.replace("\r", "").replace("\t", "    ").split("\n", -1);
        if (text().length() + text.length() > PacketComputer.MAX_TEXT) {
            return;
        }
        StringBuilder line = lines.get(row);
        String tail = line.substring(Math.min(col, line.length()));
        line.setLength(Math.min(col, line.length()));
        line.append(parts[0]);
        for (int i = 1; i < parts.length; i++) {
            row++;
            lines.add(row, new StringBuilder(parts[i]));
        }
        col = lines.get(row).length();
        lines.get(row).append(tail);
        dirty = true;
    }

    private void newline() {
        deleteSelection();
        StringBuilder line = lines.get(row);
        String before = line.substring(0, Math.min(col, line.length()));
        String after = line.substring(Math.min(col, line.length()));
        line.setLength(before.length());
        int indent = 0;
        while (indent < before.length() && before.charAt(indent) == ' ') {
            indent++;
        }
        if (before.stripTrailing().endsWith(":")) {
            indent += 4;
        }
        row++;
        lines.add(row, new StringBuilder(" ".repeat(indent)).append(after.stripLeading()));
        col = indent;
        dirty = true;
    }

    private void backspace() {
        if (deleteSelection()) {
            return;
        }
        StringBuilder line = lines.get(row);
        if (col > 0) {
            int n = 1;
            //Dans l'indentation : on retire 4 espaces
            if (line.substring(0, col).isBlank() && col % 4 == 0) {
                n = 4;
            }
            line.delete(col - n, col);
            col -= n;
        } else if (row > 0) {
            StringBuilder prev = lines.get(row - 1);
            col = prev.length();
            prev.append(line);
            lines.remove(row);
            row--;
        }
        dirty = true;
    }

    private void delete() {
        if (deleteSelection()) {
            return;
        }
        StringBuilder line = lines.get(row);
        if (col < line.length()) {
            line.deleteCharAt(col);
        } else if (row < lines.size() - 1) {
            line.append(lines.remove(row + 1));
        }
        dirty = true;
    }

    private void move(int dRow, int dCol, boolean select) {
        if (select && anchorRow < 0) {
            anchorRow = row;
            anchorCol = col;
        } else if (!select) {
            anchorRow = -1;
        }
        if (dRow != 0) {
            row = Math.max(0, Math.min(lines.size() - 1, row + dRow));
            col = Math.min(col, lines.get(row).length());
        }
        if (dCol < 0) {
            if (col > 0) {
                col--;
            } else if (row > 0) {
                row--;
                col = lines.get(row).length();
            }
        } else if (dCol > 0) {
            if (col < lines.get(row).length()) {
                col++;
            } else if (row < lines.size() - 1) {
                row++;
                col = 0;
            }
        }
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (key == GLFW.GLFW_KEY_F1) {
            toggleHelp(helpOpen ? "" : wordAtCursor());
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE && helpOpen) {
            helpOpen = false;
            helpFocus = false;
            return true;
        }
        if (key == GLFW.GLFW_KEY_ESCAPE) {
            if (renaming || consoleFocus) {
                renaming = false;
                consoleFocus = false;
                return true;
            }
            return super.keyPressed(key, scan, modifiers);
        }
        boolean ctrl = Screen.hasControlDown(), shift = Screen.hasShiftDown();
        if (ctrl && key == GLFW.GLFW_KEY_S) {
            save();
            return true;
        }
        if (key == GLFW.GLFW_KEY_F5 || ctrl && key == GLFW.GLFW_KEY_R) {
            run();
            return true;
        }
        if (helpOpen && !consoleFocus && !renaming) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && helpSearch.length() > 0) {
                helpSearch.setLength(helpSearch.length() - 1);
                helpScroll = 0;
            }
            return true;
        }
        if (renaming) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && !fileName.isEmpty()) {
                fileName = fileName.substring(0, fileName.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                renaming = false;
                dirty = true;
            }
            return true;
        }
        if (consoleFocus) {
            if (key == GLFW.GLFW_KEY_BACKSPACE && consoleInput.length() > 0) {
                consoleInput.setLength(consoleInput.length() - 1);
            } else if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                send(PacketComputer.INPUT, "", consoleInput.toString());
                consoleInput.setLength(0);
            }
            return true;
        }
        switch (key) {
            case GLFW.GLFW_KEY_ENTER, GLFW.GLFW_KEY_KP_ENTER -> newline();
            case GLFW.GLFW_KEY_BACKSPACE -> backspace();
            case GLFW.GLFW_KEY_DELETE -> delete();
            case GLFW.GLFW_KEY_TAB -> insert("    ");
            case GLFW.GLFW_KEY_LEFT -> move(0, -1, shift);
            case GLFW.GLFW_KEY_RIGHT -> move(0, 1, shift);
            case GLFW.GLFW_KEY_UP -> move(-1, 0, shift);
            case GLFW.GLFW_KEY_DOWN -> move(1, 0, shift);
            case GLFW.GLFW_KEY_PAGE_UP -> move(-visibleLines(), 0, shift);
            case GLFW.GLFW_KEY_PAGE_DOWN -> move(visibleLines(), 0, shift);
            case GLFW.GLFW_KEY_HOME -> {
                move(0, 0, shift);
                col = 0;
            }
            case GLFW.GLFW_KEY_END -> {
                move(0, 0, shift);
                col = lines.get(row).length();
            }
            default -> {
                if (ctrl && key == GLFW.GLFW_KEY_A) {
                    anchorRow = 0;
                    anchorCol = 0;
                    row = lines.size() - 1;
                    col = lines.get(row).length();
                } else if (ctrl && key == GLFW.GLFW_KEY_C) {
                    String s = selectedText();
                    if (!s.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(s);
                    }
                } else if (ctrl && key == GLFW.GLFW_KEY_X) {
                    String s = selectedText();
                    if (!s.isEmpty()) {
                        Minecraft.getInstance().keyboardHandler.setClipboard(s);
                        deleteSelection();
                    }
                } else if (ctrl && key == GLFW.GLFW_KEY_V) {
                    insert(Minecraft.getInstance().keyboardHandler.getClipboard());
                } else {
                    //Les autres touches (inventaire, etc.) ne ferment pas le terminal
                    return true;
                }
            }
        }
        clampScroll();
        return true;
    }

    @Override
    public boolean charTyped(char c, int modifiers) {
        if (c < ' ' || c == '\u007f' || c == '§') {
            return false;
        }
        if (helpOpen && !consoleFocus && !renaming) {
            if (helpSearch.length() < 40) {
                helpSearch.append(c);
                helpScroll = 0;
            }
            return true;
        }
        if (renaming) {
            if (fileName.length() < 34 && (Character.isLetterOrDigit(c) || c == '_' || c == '.' || c == '-' || c == ':')) {
                fileName += c;
            }
            return true;
        }
        if (consoleFocus) {
            if (consoleInput.length() < 200) {
                consoleInput.append(c);
            }
            return true;
        }
        insert(String.valueOf(c));
        clampScroll();
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
        if (helpOpen) {
            //L'aide prend les clics de la zone de l'editeur (categories, recherche, insertion)
            consoleFocus = false;
            renaming = false;
            if (!over(mx, my, LEFT, editorTop(), imageWidth - 6 - LEFT, editorBottom() - editorTop())) {
                helpFocus = false;
            }
            return super.mouseClicked(mouseX, mouseY, button);
        }
        if (over(mx, my, LEFT, editorTop(), imageWidth - 6 - LEFT, editorBottom() - editorTop())) {
            renaming = false;
            consoleFocus = false;
            boolean shift = Screen.hasShiftDown();
            if (shift && anchorRow < 0) {
                anchorRow = row;
                anchorCol = col;
            }
            placeCursor(mx, my);
            if (!shift) {
                //Ancre au point clique : un glisser selectionne
                anchorRow = row;
                anchorCol = col;
            }
            return true;
        }
        consoleFocus = false;
        renaming = false;
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
        if (anchorRow >= 0 && over(mx, my, LEFT - 20, editorTop() - 20, imageWidth, editorBottom() - editorTop() + 40)) {
            placeCursor(mx, my);
            clampScroll();
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    private void placeCursor(int mx, int my) {
        int li = scroll + (my - editorTop() - 3) / LINE_H;
        row = Math.max(0, Math.min(lines.size() - 1, li));
        String line = lines.get(row).toString();
        int x = mx - (LEFT + GUTTER + 3) + hscroll;
        int c = 0;
        while (c < line.length() && font.width(line.substring(0, c + 1)) - font.width(line.substring(c, c + 1)) / 2 < x) {
            c++;
        }
        col = c;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
        if (mx < LEFT - 2 && my >= FILES_TOP && my < filesBottom()) {
            fileScroll = (int) Math.max(0, fileScroll - delta * 10);
            return true;
        }
        if (my > imageHeight - CONSOLE_H - 6) {
            ComputerTile t = tile();
            int size = t == null ? 0 : t.console().size();
            consoleScroll = (int) Math.max(0, Math.min(Math.max(0, size - 6), consoleScroll + delta * 2));
            return true;
        }
        if (helpOpen) {
            if (mx < LEFT + Math.min(92, (imageWidth - 6 - LEFT) / 4) + 4) {
                helpCatScroll = (int) Math.max(0, helpCatScroll - delta);
            } else {
                helpScroll = (int) Math.max(0, Math.min(Math.max(0, helpContent), helpScroll - delta * 18));
            }
            return true;
        }
        scroll = (int) Math.max(0, Math.min(Math.max(0, lines.size() - visibleLines()), scroll - delta * 3));
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
