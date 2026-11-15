package fr.pioneers.client.gui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.SharedConstants;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractScrollWidget;
import net.minecraft.client.gui.components.MultilineTextField;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 16, retours de Guillaume : « page de contrat : pouvoir rediger une vraie lettre ») : zone de texte MULTILIGNE a l'encre sur
 * le papier du Carnet. Toute l'edition est celle du champ multiligne de Minecraft ({@link MultilineTextField} : retour a la ligne automatique
 * et touche Entree, curseur, fleches, Debut / Fin, Maj pour selectionner, Ctrl+A / C / X / V, souris pour placer le curseur et selectionner
 * en glissant) ; seuls le dessin (papier, encre, lignes reglees, curseur et selection a l'encre) et la borne de lignes sont d'ici. Le
 * {@code MultiLineEditBox} de Minecraft ecrit en gris clair sur fond noir : illisible sur le parchemin.
 */
public class LetterEditor extends AbstractScrollWidget {

    /** Encre, encre pale (texte d'attente), fond du papier, lignes reglees, selection, curseur. */
    public static final int INK = 0xFF3A2E22, HINT = 0xFF9A8A70, PAPER = 0xFFF6EDD6, RULE = 0x22243660, SELECT = 0x5A4A6FB0, CARET = 0xFF24365E;
    public static final int LINE_H = 9;
    private final Font font;
    private final Component placeholder;
    private final MultilineTextField field;
    private final int maxLines;

    public LetterEditor(Font font, int x, int y, int w, int h, Component placeholder, int maxChars, int maxLines) {
        super(x, y, w, h, placeholder);
        this.font = font;
        this.placeholder = placeholder;
        this.maxLines = maxLines;
        this.field = new MultilineTextField(font, w - totalInnerPadding());
        this.field.setCharacterLimit(maxChars);
        this.field.setCursorListener(this::scrollToCursor);
    }

    public void setValueListener(Consumer<String> listener) {
        field.setValueListener(listener);
    }

    public void setValue(String value) {
        field.setValue(value == null ? "" : value);
    }

    public String getValue() {
        return field.value();
    }

    public int length() {
        return field.value().length();
    }

    /** Lignes ecrites (retours a la ligne voulus) : le serveur en borne le nombre. */
    public int lines() {
        return field.value().split("\n", -1).length;
    }

    @Override
    public void updateWidgetNarration(@NotNull NarrationElementOutput out) {
        out.add(NarratedElementType.TITLE, Component.translatable("gui.narrate.editBox", getMessage(), getValue()));
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (super.mouseClicked(mx, my, button)) {
            return true;
        }
        if (withinContentAreaPoint(mx, my) && button == 0) {
            field.setSelecting(Screen.hasShiftDown());
            seek(mx, my);
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(double mx, double my, int button, double dx, double dy) {
        if (super.mouseDragged(mx, my, button, dx, dy)) {
            return true;
        }
        if (withinContentAreaPoint(mx, my) && button == 0) {
            field.setSelecting(true);
            seek(mx, my);
            field.setSelecting(Screen.hasShiftDown());
            return true;
        }
        return false;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (!isFocused()) {
            return false;
        }
        //Entree : refusee au-dela de la borne de lignes (le reste de l'edition est celui de Minecraft)
        if ((key == 257 || key == 335) && lines() >= maxLines) {
            return true;
        }
        if (field.keyPressed(key)) {
            return true;
        }
        //Echap et les touches non gerees remontent a l'ecran ; les autres sont gardees (le Carnet ne se ferme pas sur E en ecrivant)
        return key != 256;
    }

    @Override
    public boolean charTyped(char c, int mods) {
        if (visible && isFocused() && SharedConstants.isAllowedChatCharacter(c)) {
            field.insertText(Character.toString(c));
            return true;
        }
        return false;
    }

    @Override
    protected void renderBackground(@NotNull GuiGraphics g) {
        int x = getX(), y = getY(), w = width, h = height;
        g.fill(x, y, x + w, y + h, isFocused() ? 0xFF24365E : 0xFF9A8A70);
        g.fill(x + 1, y + 1, x + w - 1, y + h - 1, PAPER);
    }

    @Override
    protected void renderContents(@NotNull GuiGraphics g, int mx, int my, float pt) {
        String s = field.value();
        int x0 = getX() + innerPadding(), y0 = getY() + innerPadding();
        //lignes reglees (papier a lettres)
        int lines = Math.max(field.getLineCount(), (height - totalInnerPadding()) / LINE_H + 2);
        for (int i = 0; i < lines; i++) {
            int ly = y0 + i * LINE_H + LINE_H - 1;
            g.fill(x0, ly, getX() + width - innerPadding(), ly + 1, RULE);
        }
        if (s.isEmpty() && !isFocused()) {
            g.drawWordWrap(font, placeholder, x0, y0, width - totalInnerPadding(), HINT);
            return;
        }
        int cursor = field.cursor();
        boolean blink = isFocused() && net.minecraft.Util.getMillis() / 300 % 2 == 0;
        boolean inside = cursor < s.length();
        int cx = 0, cy = 0, y = y0;
        for (int[] line : ranges()) {
            boolean shown = withinContentAreaTopBottom(y, y + LINE_H);
            if (blink && inside && cursor >= line[0] && cursor <= line[1]) {
                if (shown) {
                    cx = g.drawString(font, s.substring(line[0], cursor), x0, y, INK, false) - 1;
                    g.fill(cx, y - 1, cx + 1, y + LINE_H, CARET);
                    g.drawString(font, s.substring(cursor, line[1]), cx, y, INK, false);
                }
            } else {
                if (shown) {
                    cx = g.drawString(font, s.substring(line[0], line[1]), x0, y, INK, false) - 1;
                }
                cy = y;
            }
            y += LINE_H;
        }
        if (blink && !inside && withinContentAreaTopBottom(cy, cy + LINE_H)) {
            g.drawString(font, "_", cx, cy, CARET, false);
        }
        if (field.hasSelection()) {
            int[] sel = selection(s, cursor, field.getSelectedText());
            y = y0;
            for (int[] line : ranges()) {
                if (sel[0] > line[1]) {
                    y += LINE_H;
                    continue;
                }
                if (line[0] > sel[1]) {
                    break;
                }
                if (withinContentAreaTopBottom(y, y + LINE_H)) {
                    int a = font.width(s.substring(line[0], Math.max(sel[0], line[0])));
                    int b = sel[1] > line[1] ? width - 2 * innerPadding() : font.width(s.substring(line[0], sel[1]));
                    g.fill(x0 + a, y, x0 + b, y + LINE_H, SELECT);
                }
                y += LINE_H;
            }
        }
    }

    @Override
    protected int getInnerHeight() {
        return LINE_H * field.getLineCount();
    }

    @Override
    protected boolean scrollbarVisible() {
        return field.getLineCount() * LINE_H > height - totalInnerPadding();
    }

    @Override
    protected double scrollRate() {
        return LINE_H / 2.0;
    }

    /**
     * Debut et fin de chaque ligne affichee (meme repli que {@link MultilineTextField} : coupure aux mots dans la largeur, ligne vide finale
     * apres un retour a la ligne) ; le type de ligne de Minecraft est protege, on la recalcule ici.
     */
    List<int[]> ranges() {
        String s = field.value();
        List<int[]> out = new ArrayList<>();
        font.getSplitter().splitLines(s, width - totalInnerPadding(), Style.EMPTY, true, (st, b, e) -> out.add(new int[]{b, e}));
        if (s.isEmpty() || s.charAt(s.length() - 1) == '\n') {
            out.add(new int[]{s.length(), s.length()});
        }
        return out;
    }

    /** Bornes de la selection : le curseur est a l'un des bouts du texte selectionne. */
    static int[] selection(String s, int cursor, String selected) {
        int n = selected.length();
        if (cursor + n <= s.length() && s.startsWith(selected, cursor)) {
            return new int[]{cursor, cursor + n};
        }
        return new int[]{Math.max(0, cursor - n), cursor};
    }

    private void scrollToCursor() {
        List<int[]> lines = ranges();
        int at = 0;
        for (int i = 0; i < lines.size(); i++) {
            if (field.cursor() >= lines.get(i)[0] && field.cursor() <= lines.get(i)[1]) {
                at = i;
            }
        }
        double d = scrollAmount();
        int first = (int) (d / LINE_H), last = (int) ((d + height - totalInnerPadding()) / LINE_H) - 1;
        if (at < first) {
            d = at * LINE_H;
        } else if (at > last) {
            d = (at + 1) * LINE_H - height + totalInnerPadding();
        }
        setScrollAmount(d);
    }

    private void seek(double mx, double my) {
        field.seekCursorToPoint(mx - getX() - innerPadding(), my - getY() - innerPadding() + scrollAmount());
    }
}
