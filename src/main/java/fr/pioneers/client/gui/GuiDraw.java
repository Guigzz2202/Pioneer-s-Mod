package fr.pioneers.client.gui;

import com.mojang.blaze3d.vertex.PoseStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.world.item.ItemStack;

/**
 * Petits outils de dessin des interfaces (style sombre du mod) : texte a l'echelle (aligne a gauche, a droite ou centre, tronque si trop long),
 * barres de progression et objets « fantomes » dans les cases vides.
 */
public final class GuiDraw {

    private GuiDraw() {
    }

    public static int color(ChatFormatting format) {
        Integer c = format.getColor();
        return 0xFF000000 | (c == null ? 0xFFFFFF : c);
    }

    /** Texte a l'echelle {@code scale}, coupe avec « ... » s'il depasse {@code maxWidth} (en pixels d'interface, 0 = sans limite). */
    public static void text(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color, float maxWidth) {
        Component shown = text;
        //D'abord on resserre le texte (jusqu'a 70 %, centre en hauteur) ; on ne coupe qu'en dernier recours
        int width = font.width(text);
        float fit = fitScale(width, scale, maxWidth);
        y += (scale - fit) * 4;
        scale = fit;
        String cut = cutText(text.getString(), width, scale, maxWidth, font::width);
        if (cut != null) {
            shown = Component.literal(cut).withStyle(text.getStyle());
        }
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(scale, scale, 1);
        graphics.drawString(font, shown, 0, 0, color, false);
        pose.popPose();
    }

    /**
     * 1.0.5-beta (lot 48) : echelle d'un texte de largeur {@code textWidth} (pixels de la police) dessine a {@code scale} dans {@code maxWidth}
     * (0 = sans limite) : resserre jusqu'a 70 % de {@code scale}. PUR (sans police) : le banc de l'API le mesure avec la vraie police
     * (tools/api/TitleBench).
     */
    public static float fitScale(float textWidth, float scale, float maxWidth) {
        if (maxWidth > 0 && textWidth * scale > maxWidth) {
            return Math.max(scale * 0.7F, Math.min(scale, maxWidth / Math.max(1, textWidth)));
        }
        return scale;
    }

    /**
     * 1.0.5-beta (lot 48) : texte coupe avec « … » s'il ne tient toujours pas a l'echelle {@code scale} (deja resserree), sinon null. PUR :
     * {@code width} = largeur d'une chaine dans la police (le jeu : {@code Font::width} ; le banc de l'API : la vraie police du jar).
     */
    public static String cutText(String plain, float textWidth, float scale, float maxWidth, java.util.function.ToIntFunction<String> width) {
        if (maxWidth <= 0 || textWidth * scale <= maxWidth) {
            return null;
        }
        while (plain.length() > 1 && width.applyAsInt(plain + "…") * scale > maxWidth) {
            plain = plain.substring(0, plain.length() - 1);
        }
        return plain + "…";
    }

    /**
     * Texte sur plusieurs lignes (coupe aux mots) dans {@code maxWidth}, au plus {@code maxLines} : la derniere ligne reprend tout le reste
     * (reduite puis coupee avec « ... »). Renvoie le nombre de lignes dessinees.
     */
    public static int wrapped(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color, float maxWidth, int maxLines,
          float lineHeight) {
        List<String> lines = new ArrayList<>();
        font.getSplitter().splitLines(text, (int) (maxWidth / scale), Style.EMPTY).forEach(line -> lines.add(line.getString()));
        int drawn = Math.min(lines.size(), maxLines);
        for (int i = 0; i < drawn; i++) {
            String line = i == drawn - 1 ? String.join(" ", lines.subList(i, lines.size())) : lines.get(i);
            text(graphics, font, Component.literal(line.trim()).withStyle(text.getStyle()), x, y + i * lineHeight, scale, color, maxWidth);
        }
        return drawn;
    }

    public static void text(GuiGraphics graphics, Font font, Component text, float x, float y, float scale, int color) {
        text(graphics, font, text, x, y, scale, color, 0);
    }

    public static void right(GuiGraphics graphics, Font font, Component text, float right, float y, float scale, int color) {
        text(graphics, font, text, right - font.width(text) * scale, y, scale, color, 0);
    }

    public static void center(GuiGraphics graphics, Font font, Component text, float center, float y, float scale, int color, float maxWidth) {
        float w = Math.min(font.width(text) * scale, maxWidth > 0 ? maxWidth : Float.MAX_VALUE);
        text(graphics, font, text, center - w / 2, y, scale, color, maxWidth);
    }

    /** Couleur RGB assombrie (facteur 0..1). */
    public static int darken(int rgb, float factor) {
        int r = Math.round((rgb >> 16 & 255) * factor), g = Math.round((rgb >> 8 & 255) * factor), b = Math.round((rgb & 255) * factor);
        return r << 16 | g << 8 | b;
    }

    /** Barre horizontale : fond sombre et remplissage. */
    public static void bar(GuiGraphics graphics, int x, int y, int width, int height, double fraction, int fill) {
        graphics.fill(x, y, x + width, y + height, 0xFF14181C);
        int w = (int) Math.round(width * Math.max(0, Math.min(1, fraction)));
        if (w > 0) {
            graphics.fill(x, y, x + w, y + height, fill);
            graphics.fill(x, y, x + w, y + 1, 0x40FFFFFF);
        }
    }

    /** Objet pale dans une case vide (x, y : coin de l'objet, 16x16), pour montrer ce qu'elle attend. */
    public static void ghost(GuiGraphics graphics, ItemStack stack, int x, int y) {
        graphics.renderFakeItem(stack, x, y);
        PoseStack pose = graphics.pose();
        pose.pushPose();
        pose.translate(0, 0, 250);
        graphics.fill(x, y, x + 16, y + 16, 0xC80C1015);
        pose.popPose();
    }

    /** Panneau d'interface (1.0 : style sombre commun a toutes les interfaces du mod, celui de {@code PiMenuScreen}). */
    public static void panel(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF141A20);
        graphics.renderOutline(x, y, width, height, 0xFF2E3A44);
        graphics.fill(x, y, x + width, y + 1, 0xFF3FD0A0);
    }

    /** Emplacement en creux (x, y : coin de l'objet). */
    public static void slot(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF2C3640);
        graphics.fill(x, y, x + 16, y + 16, 0xFF0E1419);
    }

    /** Ecran interieur sombre. */
    public static void screen(GuiGraphics graphics, int x, int y, int width, int height) {
        graphics.fill(x, y, x + width, y + height, 0xFF0B1016);
        graphics.renderOutline(x, y, width, height, 0xFF2E3A44);
    }

    /** Jauge verticale coloree (fluide, gaz, donnees) avec cadre. */
    public static void gauge(GuiGraphics graphics, int x, int y, int width, int height, double fraction, int rgb) {
        screen(graphics, x, y, width, height);
        int inner = height - 2;
        int filled = (int) Math.round(inner * Math.max(0, Math.min(1, fraction)));
        if (filled > 0) {
            graphics.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - 1, 0xFF000000 | rgb);
            graphics.fill(x + 1, y + height - 1 - filled, x + width - 1, y + height - filled, 0x60FFFFFF);
        }
    }

    /** Case condamnee (hachures sombres). */
    public static void locked(GuiGraphics graphics, int x, int y) {
        graphics.fill(x - 1, y - 1, x + 17, y + 17, 0xFF1A2027);
        for (int i = 0; i < 18; i += 4) {
            graphics.fill(x - 1 + i, y - 1, x + Math.min(17, i + 1), y + 17, 0xFF262E36);
        }
    }
}
