package fr.pioneers.society.rp.client;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

/**
 * 1.9 (MAJ RP) : messages discrets du Carnet (niveau gagne, Maitrise, plafond du jour, palier d'affinite...) en haut de l'ecran, sur une
 * bande de papier, 4 secondes chacun ; et gains d'XP / de jetons au-dessus de la barre de vie ({@link RpClient#POPUPS}).
 */
public final class Toasts {

    /** 1.0.5-beta (lot 26) : {@code top} = haut du bandeau, fixe pour toute la vie du message (pris a sa premiere image). */
    private record Toast(Component text, long born, int top) {
    }

    private static final Deque<Toast> QUEUE = new ArrayDeque<>();
    private static final long LIFE = 4000, POP_LIFE = 2200;

    /** 1.0.3-beta (lot 2) : bas du bandeau du haut dessine a ce rendu (0 : aucun), lu par l'affichage du Periple qui se place dessous. */
    private static int topBottom;

    private Toasts() {
    }

    /** Bas (px d'interface) du bandeau du haut au dernier rendu, 0 s'il n'y en a pas. */
    public static int topBottom() {
        return topBottom;
    }

    public static void show(Component c) {
        QUEUE.addLast(new Toast(c, 0, 0));
        while (QUEUE.size() > 4) {
            QUEUE.pollFirst();
        }
    }

    static void render(GuiGraphics g, int width, int height) {
        Minecraft mc = Minecraft.getInstance();
        Font font = mc.font;
        long now = System.currentTimeMillis();
        topBottom = 0;
        Toast t = QUEUE.peekFirst();
        if (t != null) {
            if (t.born() == 0) {
                QUEUE.pollFirst();
                //1.0.5-beta (lot 26) : le bandeau se pose sous le Periple s'il est en haut au centre (plus l'inverse : le Periple ne saute plus)
                t = new Toast(t.text(), now, Math.max(6, fr.pioneers.guide.client.JourneyHud.topCenterBottom() + 2));
                QUEUE.addFirst(t);
            }
            long age = now - t.born();
            if (age > LIFE) {
                QUEUE.pollFirst();
            } else {
                //1.9.1 (audit des ecrans du bloc 2) : un message plus large que l'ecran est replie sur plusieurs lignes (bande plus haute)
                int max = Math.max(80, Math.min(320, width - 24));
                List<FormattedCharSequence> lines = font.split(t.text(), max);
                int tw = 0;
                for (FormattedCharSequence l : lines) {
                    tw = Math.max(tw, font.width(l));
                }
                int w = tw + 16, h = 7 + lines.size() * 10;
                //1.0.5-beta (lot 26, retour 16) : milieu du bord haut fixe (HudLayouts.toast), la largeur varie des deux cotes
                int[] tb = fr.pioneers.client.look.HudLayouts.toast(width, height, w, h, t.top());
                int x = tb[0], y = tb[1];
                w = tb[2] - tb[0];
                h = tb[3] - tb[1];
                int alpha = (int) (255 * Math.min(1, Math.min(age / 250.0, (LIFE - age) / 400.0)));
                if (alpha > 8) {
                    topBottom = y + h;
                    int a = alpha << 24;
                    g.fill(x, y, x + w, y + h, (alpha * 0xE0 / 255) << 24 | 0xEFE3C8);
                    g.fill(x, y + h - 1, x + w, y + h, a | 0x24365E);
                    for (int i = 0; i < lines.size(); i++) {
                        FormattedCharSequence l = lines.get(i);
                        g.drawString(font, l, fr.pioneers.client.look.HudLayouts.popupLeft(width, font.width(l)), y + 4 + i * 10, a | 0x24365E, false);
                    }
                }
            }
        }
        int y = height - 59;
        for (RpClient.Popup p : RpClient.POPUPS.toArray(new RpClient.Popup[0])) {
            long age = now - p.born();
            if (age > POP_LIFE) {
                RpClient.POPUPS.remove(p);
                continue;
            }
            int alpha = (int) (255 * Math.min(1, (POP_LIFE - age) / 600.0));
            if (alpha > 8) {
                int color = alpha << 24 | (p.auto() ? 0xA9B7D6 : 0xE8D9A8);
                String s = p.text();
                if (font.width(s) > width - 16) { //1.9.1 : jamais hors de l'ecran
                    s = RpGui.ellipsize(font, s, width - 16);
                }
                g.drawString(font, s, fr.pioneers.client.look.HudLayouts.popupLeft(width, font.width(s)), y - (int) (age / 220), color, true);
            }
            y -= 10;
        }
    }
}
