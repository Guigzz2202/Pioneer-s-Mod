package fr.pioneers.client.look;

import java.util.ArrayList;
import java.util.List;

/**
 * 1.0.5-beta (lot 14) : PLACE de la fiche au regard a l'ecran, pure (aucune classe de Minecraft ; banc {@code tools/look/CardBench.java}).
 * <p>
 * La fiche part du coin regle ({@link LookHudConfig.Corner}) a l'ecart regle, puis s'ecarte des zones deja occupees par le jeu et par les
 * autres affichages du mod ({@link #vanillaZones} : barre d'objets, coeurs, faim, armure, air, experience, nom de l'objet tenu, main secondaire
 * et indicateur d'attaque ; sous-titres ; icones des effets ; plus la minimap, le Periple et les jauges d'equipement relevees a leur dernier
 * dessin) : parmi les places candidates (le coin, et chaque bord de zone), la plus proche du coin qui ne touche rien et tient dans l'ecran.
 * Si aucune ne convient (ecran minuscule), le coin borne a l'ecran.
 */
public final class CardPlacement {

    private CardPlacement() {
    }

    /** Ecart laisse entre la fiche et une zone (px d'interface). */
    public static final int GAP = 2;
    /** Demi-largeur de la zone de la barre d'objets : 91 (barre) + 29 (main secondaire, indicateur d'attaque). */
    public static final int HOTBAR_HALF = 120;
    /** Hauteur prise en bas au centre par le nom de l'objet tenu et la barre d'action (vanilla : texte a h - 59 / h - 68). */
    public static final int ITEM_NAME_TOP = 74, ITEM_NAME_HALF = 100;
    /** Sous-titres (vanilla : lignes de 10 px au-dessus de h - 35, a droite) : 4 lignes reservees, 200 px de large. */
    public static final int SUBTITLE_W = 200, SUBTITLE_LINES = 4;
    /** Icones des effets (vanilla : 25 px par icone, rangee des bienfaits en y 1, des mefaits en y 27). */
    public static final int EFFECT_STEP = 25, EFFECT_ROW = 26;

    /** Zone occupee {x0, y0, x1, y1} (px d'interface, bornes hautes exclues). */
    public static int[] rect(int x0, int y0, int x1, int y1) {
        return new int[]{x0, y0, x1, y1};
    }

    /**
     * Zones du jeu : bas de l'ecran au centre (barre d'objets et barres empilees au-dessus : {@code barsHeight} = plus grande des hauteurs
     * gauche / droite de {@code ForgeGui}, 39 sans barre), nom de l'objet tenu, sous-titres a droite s'ils sont actives, effets en haut a
     * droite ({@code good} / {@code bad} : nombre d'effets visibles de chaque rangee).
     */
    public static List<int[]> vanillaZones(int screenW, int screenH, int barsHeight, boolean subtitles, int good, int bad) {
        List<int[]> out = new ArrayList<>();
        int cx = screenW / 2;
        out.add(rect(cx - HOTBAR_HALF, screenH - Math.max(39, barsHeight) - 1, cx + HOTBAR_HALF, screenH));
        out.add(rect(cx - ITEM_NAME_HALF, screenH - ITEM_NAME_TOP, cx + ITEM_NAME_HALF, screenH));
        if (subtitles) {
            out.add(rect(screenW - SUBTITLE_W, screenH - 35 - SUBTITLE_LINES * 10, screenW, screenH - 28));
        }
        int n = Math.max(good, bad);
        if (n > 0) {
            out.add(rect(screenW - n * EFFECT_STEP - 1, 0, screenW, bad > 0 ? 2 * EFFECT_ROW : EFFECT_ROW));
        }
        return out;
    }

    /** Place {x, y} du coin haut gauche d'une fiche de {@code w} x {@code h} px d'interface. */
    public static int[] place(int screenW, int screenH, int w, int h, LookHudConfig.Corner corner, int offX, int offY, List<int[]> zones) {
        int ax = corner.right() ? screenW - offX - w : offX;
        int ay = corner.bottom() ? screenH - offY - h : offY;
        ax = Math.max(0, Math.min(screenW - w, ax));
        ay = Math.max(0, Math.min(screenH - h, ay));
        List<Integer> xs = new ArrayList<>(List.of(ax)), ys = new ArrayList<>(List.of(ay));
        for (int[] z : zones) {
            xs.add(z[0] - GAP - w);
            xs.add(z[2] + GAP);
            ys.add(z[1] - GAP - h);
            ys.add(z[3] + GAP);
        }
        int[] best = null;
        long bestD = Long.MAX_VALUE;
        for (int x : xs) {
            for (int y : ys) {
                if (x < 0 || y < 0 || x + w > screenW || y + h > screenH || hits(x, y, w, h, zones)) {
                    continue;
                }
                long d = (long) (x - ax) * (x - ax) + (long) (y - ay) * (y - ay);
                if (d < bestD) {
                    bestD = d;
                    best = new int[]{x, y};
                }
            }
        }
        return best != null ? best : new int[]{ax, ay};
    }

    /**
     * 1.0.5-beta (lot 26, retour 16 : « point d'ancrage immuable ») : hauteur de reference de la fiche (px d'interface) pour choisir son
     * point d'ancrage : la place est cherchee pour une fiche de largeur maximale ({@code CardLayout.MAX_W} a l'echelle) et de cette hauteur,
     * JAMAIS pour la fiche du moment ; la fiche reelle part ensuite de ce point (coin du reglage) et grandit vers le cote oppose.
     */
    public static final int REF_H = 80;

    /**
     * 1.0.5-beta (lot 26) : POINT D'ANCRAGE {x, y} de la fiche (coin regle : en bas a droite, le coin bas droit) ; il ne depend que de
     * l'ecran, du reglage, de la largeur de reference {@code refW} et des zones, jamais du contenu de la fiche. Meme recherche que
     * {@link #place} sur la boite de reference, puis son coin du cote de l'ancre.
     */
    public static int[] anchor(int screenW, int screenH, int refW, int refH, LookHudConfig.Corner corner, int offX, int offY, List<int[]> zones) {
        int w = Math.max(1, Math.min(refW, screenW)), h = Math.max(1, Math.min(refH, screenH));
        int[] at = place(screenW, screenH, w, h, corner, offX, offY, zones);
        return new int[]{corner.right() ? at[0] + w : at[0], corner.bottom() ? at[1] + h : at[1]};
    }

    /** 1.0.5-beta (lot 26) : position commune d'ancrage d'un coin de la fiche. */
    public static HudAnchor anchorOf(LookHudConfig.Corner corner) {
        return HudAnchor.corner(corner.right(), corner.bottom());
    }

    /**
     * 1.0.5-beta (lot 26) : point d'ancrage TENU tant que la fiche reste affichee. Les zones des autres affichages (Periple qui apparait,
     * effets, barres...) ne le deplacent pas pendant qu'une fiche est a l'ecran : il n'est recalcule que si la fiche a disparu plus de
     * {@link #HOLD_MS} ou si l'ecran ou le reglage changent ({@code key}). Etat client, sans classe de Minecraft.
     */
    public static final class Latch {

        public static final long HOLD_MS = 300;
        private long key = Long.MIN_VALUE, at = Long.MIN_VALUE / 2;
        private int[] point;

        /** Point tenu pour la cle {@code key} a l'instant {@code now} (ms), sinon {@code fresh} (alors tenu). */
        public int[] hold(long key, long now, int[] fresh) {
            if (point == null || key != this.key || now - at > HOLD_MS) {
                point = fresh.clone();
                this.key = key;
            }
            at = now;
            return point.clone();
        }
    }

    /** La fiche posee en (x, y) touche-t-elle une zone ? */
    public static boolean hits(int x, int y, int w, int h, List<int[]> zones) {
        for (int[] z : zones) {
            if (x < z[2] && z[0] < x + w && y < z[3] && z[1] < y + h) {
                return true;
            }
        }
        return false;
    }
}
