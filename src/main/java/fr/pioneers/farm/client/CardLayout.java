package fr.pioneers.farm.client;

import fr.pioneers.farm.FarmCard;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.StringSplitter;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 14, retour de Guillaume : « Infobulles dans le monde : plus petites, textes jamais tronques ») : MISE EN PAGE PURE de la
 * fiche au regard ({@link FarmCard}), sans dessin ni classe de rendu : seulement le decoupage des lignes de la police
 * ({@link StringSplitter}, le meme que {@code Font.getSplitter()}). Le dessin ({@link FarmHud#draw}) peint les primitives rendues ici ; le banc
 * {@code tools/look/CardBench.java} appelle la meme methode avec la vraie police du jeu.
 * <p>
 * Regles : lignes compactes ({@link #ROW} px), largeur = celle du texte reel bornee a {@link #MAX_W} et a la place donnee ; AUCUN texte coupe :
 * tout ce qui depasse passe a la ligne (titre a cote de l'icone, textes, libelles dans leur colonne, valeurs a la suite, ou sous le libelle
 * quand la colonne des valeurs serait trop etroite) ; aucun nombre maximal de rangs. Les icones d'objets vont sur deux rangees au plus
 * (« +n » ensuite : un compte, pas un texte coupe).
 */
public final class CardLayout {

    private CardLayout() {
    }

    /** Marges et tailles (px de la fiche, avant l'echelle). */
    public static final int PAD = 3, ICON = 16, TITLE_X = 20, HEAD_MIN = 18, ROW = 9, ITEM_ROW = 17, GAP = 3, BAR_W = 28, MIN_W = 72, MAX_W = 176,
          LABEL_MAX = 64, VALUE_MIN = 36, AFTER_BAR_MIN = 22, INDENT = 6, ITEM_ROWS = 2;
    public static final int C_PANEL = 0xD8101418, C_EDGE = 0xFF262C34, C_TITLE = 0xFFFFFF, C_TEXT = 0xE0E0E0, C_LABEL = 0x9AA4AE, C_HINT = 0x8A929C,
          C_TRACK = 0xFF1E2228;

    /** Texte place : {@code max} = largeur permise (le banc verifie que le texte y tient). */
    public record Text(FormattedText text, int x, int y, int colour, int max) {
    }

    /** Jauge : rail et remplissage 0..1. */
    public record Bar(int x, int y, float fill, int colour) {
    }

    /** Icone d'objet (avec sa petite jauge d'avancement si {@code fill} >= 0). */
    public record Icon(ItemStack stack, int x, int y, float fill) {
    }

    /** Fiche mise en page (coordonnees relatives au coin haut gauche du panneau). */
    public record Layout(int w, int h, int accent, ItemStack icon, List<Text> texts, List<Bar> bars, List<Icon> icons) {
    }

    /**
     * Met la fiche en page. {@code room} = largeur disponible (px de la fiche) : le panneau n'est jamais plus large (ni plus large que
     * {@link #MAX_W}).
     */
    public static Layout layout(StringSplitter splitter, FarmCard card, int room) {
        List<FarmCard.Line> lines = card.lines();
        int maxW = Math.max(40, Math.min(MAX_W, room));
        int labelW = 0;
        for (FarmCard.Line l : lines) {
            if ((l.kind() == FarmCard.Kind.PAIR || l.kind() == FarmCard.Kind.GAUGE) && l.text() != null) {
                labelW = Math.max(labelW, width(splitter, l.text()));
            }
        }
        labelW = Math.min(labelW, LABEL_MAX);
        int valueX = labelW > 0 ? labelW + GAP : 0;
        //Largeur naturelle : celle du texte reel sans retour a la ligne
        int natural = TITLE_X + width(splitter, card.title()) - PAD;
        for (FarmCard.Line l : lines) {
            natural = Math.max(natural, switch (l.kind()) {
                case TEXT, HINT -> l.text() == null ? 0 : width(splitter, l.text());
                case PAIR -> valueX + (l.value() == null ? 0 : width(splitter, l.value()));
                case GAUGE -> valueX + BAR_W + GAP + (l.value() == null ? 0 : width(splitter, l.value()));
                case ITEMS -> Math.min(8, l.items().size()) * 18 - 2;
            });
        }
        int w = Math.min(maxW, Math.max(MIN_W, natural + 2 * PAD));
        int inner = w - 2 * PAD;
        if (labelW > inner / 2) {
            labelW = inner / 2;
            valueX = labelW + GAP;
        }
        List<Text> texts = new ArrayList<>();
        List<Bar> bars = new ArrayList<>();
        List<Icon> icons = new ArrayList<>();
        //En-tete : icone, titre a cote (sur plusieurs rangs s'il le faut)
        int titleRoom = w - TITLE_X - PAD;
        List<FormattedText> title = split(splitter, card.title(), titleRoom);
        int head = Math.max(HEAD_MIN, 3 + title.size() * ROW + 2);
        int ty = title.size() == 1 ? 5 : 3;
        for (FormattedText t : title) {
            texts.add(new Text(t, TITLE_X, ty, C_TITLE, titleRoom));
            ty += ROW;
        }
        int y = head;
        int x = PAD;
        for (FarmCard.Line l : lines) {
            switch (l.kind()) {
                case TEXT, HINT -> {
                    if (l.text() == null) {
                        continue;
                    }
                    int colour = l.kind() == FarmCard.Kind.HINT ? C_HINT : C_TEXT;
                    for (FormattedText t : split(splitter, l.text(), inner)) {
                        texts.add(new Text(t, x, y, colour, inner));
                        y += ROW;
                    }
                }
                case PAIR, GAUGE -> {
                    boolean gauge = l.kind() == FarmCard.Kind.GAUGE;
                    int colX = gauge ? valueX + BAR_W + GAP : valueX;
                    int colRoom = inner - colX;
                    boolean stacked = gauge ? colRoom < AFTER_BAR_MIN : colRoom < VALUE_MIN;
                    List<FormattedText> label = l.text() == null ? List.of() : split(splitter, l.text(), stacked && !gauge ? inner : labelW);
                    int ly = y;
                    for (FormattedText t : label) {
                        texts.add(new Text(t, x, ly, C_LABEL, stacked && !gauge ? inner : labelW));
                        ly += ROW;
                    }
                    int vy = y;
                    if (gauge) {
                        bars.add(new Bar(x + valueX, y, l.bar(), l.colour()));
                        if (stacked) {
                            vy = y + ROW;
                        }
                    } else if (stacked) {
                        vy = ly;
                    }
                    int vx = gauge ? stacked ? x + valueX : x + colX : stacked ? x + INDENT : x + colX;
                    int vRoom = gauge ? stacked ? inner - valueX : colRoom : stacked ? inner - INDENT : colRoom;
                    int colour = gauge ? C_TEXT : l.colour();
                    if (l.value() != null) {
                        for (FormattedText t : split(splitter, l.value(), vRoom)) {
                            texts.add(new Text(t, vx, vy, colour, vRoom));
                            vy += ROW;
                        }
                    }
                    y = Math.max(Math.max(ly, vy), y + ROW);
                }
                case ITEMS -> {
                    int n = l.items().size();
                    if (n == 0) {
                        continue;
                    }
                    int fit = Math.max(1, (inner + 2) / 18);
                    int slots = fit * ITEM_ROWS;
                    int shown = n > slots ? slots - 1 : n;
                    for (int i = 0; i < shown; i++) {
                        float f = i < l.fill().length ? l.fill()[i] : -1;
                        icons.add(new Icon(l.items().get(i), x + i % fit * 18, y + i / fit * ITEM_ROW, f));
                    }
                    int rows = (Math.max(shown, n > slots ? slots : 0) + fit - 1) / fit;
                    if (n > shown) {
                        int i = shown;
                        texts.add(new Text(FormattedText.of("+" + (n - shown)), x + i % fit * 18 + 1, y + i / fit * ITEM_ROW + 5, C_LABEL, 18));
                    }
                    y += rows * ITEM_ROW;
                }
            }
        }
        int h = y == head ? HEAD_MIN + 2 : y + 2;
        return new Layout(w, h, card.accent(), card.icon(), texts, bars, icons);
    }

    /** Largeur d'un texte (comme {@code Font.width}). */
    public static int width(StringSplitter splitter, FormattedText text) {
        return (int) Math.ceil(splitter.stringWidth(text));
    }

    /** Rangs d'un texte dans {@code max} px (retour a la ligne de la police ; au moins un rang). */
    public static List<FormattedText> split(StringSplitter splitter, @Nullable FormattedText text, int max) {
        if (text == null) {
            return List.of();
        }
        List<FormattedText> parts = splitter.splitLines(text, Math.max(1, max), Style.EMPTY);
        return parts.isEmpty() ? List.of(FormattedText.EMPTY) : parts;
    }
}
