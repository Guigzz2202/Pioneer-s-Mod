package fr.pioneers.client.look;

import java.util.Locale;

/**
 * 1.0.5-beta (lot 26, retour 16 de Guillaume : « Pour les tooltips, il faut un point d'ancrage immuable selon la position : si la position du
 * tooltip est en bas a droite alors son coin en bas a droite ne bouge jamais de position, la taille varie sur les autres points ») :
 * utilitaire COMMUN d'ancrage de tout ce que le mod pose a l'ecran (fiche au regard, minimap, Periple, bandeaux, messages, jauges...).
 * Classe pure (aucune classe de Minecraft : banc {@code tools/look/AnchorBench.java}).
 * <p>
 * Chaque position a un POINT D'ANCRAGE : un coin (haut gauche, haut droite, bas gauche, bas droite), le milieu d'un bord (haut centre, bas
 * centre, centre gauche, centre droite) ou le centre de l'ecran. Le point ne depend QUE de l'ecran (taille a l'echelle de l'interface) et des
 * reglages (ecarts) : jamais du contenu. La boite (largeur, hauteur) part du point et grandit vers le cote oppose : en bas a droite, le coin bas
 * droit reste fixe au pixel pres et la boite grandit vers le haut et vers la gauche ; centree sur un axe, son pixel du milieu
 * ({@code x + w / 2}) reste sur le point. Une boite trop grande est ECRETEE contre le bord de l'ecran OPPOSE a l'ancre seulement (le cote de
 * l'ancre ne bouge jamais) ; centree, elle est ecretee des deux cotes a la fois (le milieu reste fixe).
 */
public enum HudAnchor {
    TOP_LEFT(0, 0), TOP_CENTER(1, 0), TOP_RIGHT(2, 0),
    CENTER_LEFT(0, 1), CENTER(1, 1), CENTER_RIGHT(2, 1),
    BOTTOM_LEFT(0, 2), BOTTOM_CENTER(1, 2), BOTTOM_RIGHT(2, 2);

    /** Axe horizontal : 0 = bord gauche, 1 = milieu, 2 = bord droit ; axe vertical : 0 = haut, 1 = milieu, 2 = bas. */
    public final int h, v;

    HudAnchor(int h, int v) {
        this.h = h;
        this.v = v;
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Coin d'apres ses deux cotes. */
    public static HudAnchor corner(boolean right, boolean bottom) {
        return bottom ? (right ? BOTTOM_RIGHT : BOTTOM_LEFT) : (right ? TOP_RIGHT : TOP_LEFT);
    }

    /** Position d'apres son nom ({@code BOTTOM_RIGHT}, {@code top_center}...), {@code fallback} si inconnue. */
    public static HudAnchor named(String name, HudAnchor fallback) {
        if (name != null) {
            for (HudAnchor a : values()) {
                if (a.name().equalsIgnoreCase(name)) {
                    return a;
                }
            }
        }
        return fallback;
    }

    // ------------------------------------------------------------------ point d'ancrage (ecran et reglages seulement)

    /** Abscisse du point : {@code off} du bord gauche, milieu de l'ecran (l'ecart ne compte pas), ou {@code off} du bord droit ; dans l'ecran. */
    public int pointX(int screenW, int off) {
        int x = h == 0 ? off : h == 2 ? screenW - off : screenW / 2;
        return Math.max(0, Math.min(screenW, x));
    }

    /** Ordonnee du point (meme regle). */
    public int pointY(int screenH, int off) {
        int y = v == 0 ? off : v == 2 ? screenH - off : screenH / 2;
        return Math.max(0, Math.min(screenH, y));
    }

    /** Point d'ancrage {x, y} (px d'interface). */
    public int[] point(int screenW, int screenH, int offX, int offY) {
        return new int[]{pointX(screenW, offX), pointY(screenH, offY)};
    }

    // ------------------------------------------------------------------ boite accrochee au point

    /** Bord gauche d'une boite de largeur {@code w} accrochee en {@code ax}. */
    public int left(int ax, int w) {
        return h == 0 ? ax : h == 2 ? ax - w : ax - w / 2;
    }

    /** Bord haut d'une boite de hauteur {@code hh} accrochee en {@code ay}. */
    public int top(int ay, int hh) {
        return v == 0 ? ay : v == 2 ? ay - hh : ay - hh / 2;
    }

    /** Place disponible {largeur, hauteur} depuis le point vers le cote oppose (centre : deux fois la plus petite demi-place). */
    public int[] room(int ax, int ay, int screenW, int screenH) {
        int rw = h == 0 ? screenW - ax : h == 2 ? ax : 2 * Math.min(ax, screenW - ax);
        int rh = v == 0 ? screenH - ay : v == 2 ? ay : 2 * Math.min(ay, screenH - ay);
        return new int[]{Math.max(0, rw), Math.max(0, rh)};
    }

    /**
     * Boite {x0, y0, x1, y1} de {@code w} x {@code hh} accrochee au point ({@code ax}, {@code ay}), ecretee contre le bord OPPOSE a l'ancre
     * seulement (le cote de l'ancre ne bouge jamais ; centree : les deux bords, le milieu reste fixe).
     */
    public int[] box(int ax, int ay, int w, int hh, int screenW, int screenH) {
        int[] room = room(ax, ay, screenW, screenH);
        int cw = Math.min(Math.max(0, w), room[0]), ch = Math.min(Math.max(0, hh), room[1]);
        //centree et ecretee : on garde la parite de la boite d'origine pour que x + w / 2 reste sur le point
        if (h == 1 && cw < w && ((cw ^ w) & 1) != 0) {
            cw = Math.max(0, cw - 1);
        }
        if (v == 1 && ch < hh && ((ch ^ hh) & 1) != 0) {
            ch = Math.max(0, ch - 1);
        }
        int x0 = left(ax, cw), y0 = top(ay, ch);
        return new int[]{x0, y0, x0 + cw, y0 + ch};
    }

    /** Boite {x0, y0, x1, y1} accrochee au point d'ancrage de l'ecran ({@link #point}). */
    public int[] place(int w, int hh, int screenW, int screenH, int offX, int offY) {
        int[] p = point(screenW, screenH, offX, offY);
        return box(p[0], p[1], w, hh, screenW, screenH);
    }

    /** Point d'ancrage d'une boite deja posee (banc et controles : il doit etre le meme quelle que soit la taille du contenu). */
    public int[] pointOf(int x0, int y0, int x1, int y1) {
        int x = h == 0 ? x0 : h == 2 ? x1 : x0 + (x1 - x0) / 2;
        int y = v == 0 ? y0 : v == 2 ? y1 : y0 + (y1 - y0) / 2;
        return new int[]{x, y};
    }
}
