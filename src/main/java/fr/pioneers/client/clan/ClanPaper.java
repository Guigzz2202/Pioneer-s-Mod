package fr.pioneers.client.clan;

import fr.pioneers.client.gui.TextFit;
import fr.pioneers.client.rp.ParchmentScreen;
import java.util.function.IntConsumer;
import javax.annotation.Nullable;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.DyeColor;

/**
 * 1.9.1 (retours de Guillaume) : outils communs des ecrans de papier du clan (Charte, Pacte, Sceau) : bouton dont le libelle tient dans
 * son cadre, palette des 16 teintures sur une rangee, infobulles des textes coupes. Les ecrans gardent leur disposition ; ces outils ne font
 * que borner le texte (voir {@link TextFit}).
 */
public final class ClanPaper {

    /** Palette : 16 cases de {@code SWATCH} px au pas de {@code PITCH} (160 px de large, 10 de haut). */
    public static final int SWATCH = 8, PITCH = 10, PALETTE_W = 16 * PITCH;

    private ClanPaper() {
    }

    /** Infobulles des textes coupes, posees dans les zones de survol de l'ecran (coordonnees de la page). */
    public static TextFit.Tips tips(ParchmentScreen s, int mx, int my) {
        return (x, y, w, h, lines) -> s.tooltip(mx, my, x, y, w, h, lines);
    }

    /** Bouton a l'encre, libelle centre et borne a la largeur du bouton (resserre puis coupe, infobulle). */
    public static void button(ParchmentScreen s, Font font, GuiGraphics g, int mx, int my, int x, int y, int w, int h, Component label,
          boolean enabled, @Nullable Runnable action) {
        boolean on = enabled && s.over(mx, my, x, y, w, h);
        g.fill(x, y, x + w, y + h, on ? 0x40243660 : 0x18243660);
        s.frame(g, x, y, w, h, enabled ? ParchmentScreen.INK : ParchmentScreen.FAINT);
        TextFit.center(g, font, label, x + 2, y + (h - 8) / 2F, w - 4, enabled ? ParchmentScreen.INK : ParchmentScreen.FAINT, 1F, tips(s, mx, my));
        if (enabled && action != null) {
            s.click(x, y, w, h, action);
        }
    }

    /** Palette des 16 teintures sur une rangee a partir de (x, y) ; {@code pick} recoit la teinture cliquee. */
    public static void palette(ParchmentScreen s, GuiGraphics g, int mx, int my, int x0, int y0, int selected, IntConsumer pick) {
        for (int i = 0; i < 16; i++) {
            int x = x0 + 1 + i * PITCH, y = y0 + 1;
            int rgb = DyeColor.byId(i).getFireworkColor();
            g.fill(x, y, x + SWATCH, y + SWATCH, 0xFF000000 | rgb);
            s.frame(g, x - 1, y - 1, SWATCH + 2, SWATCH + 2, selected == i ? ParchmentScreen.INK : ParchmentScreen.FAINT);
            int ii = i;
            s.click(x - 1, y - 1, PITCH, SWATCH + 2, () -> pick.accept(ii));
            s.tooltip(mx, my, x - 1, y - 1, PITCH, SWATCH + 2, Component.translatable("color.minecraft." + DyeColor.byId(i).getName()));
        }
        if (selected >= 0 && selected < 16) {
            int x = x0 + 1 + selected * PITCH, y = y0 + 1;
            s.frame(g, x - 2, y - 2, SWATCH + 4, SWATCH + 4, ParchmentScreen.INK);
        }
    }
}
