package fr.pioneers.client.look;

import fr.pioneers.common.PiClientConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.0.5-beta (lot 14, retour de Guillaume : « Infobulles dans le monde : en bas a droite (configurable), plus petites, textes jamais
 * tronques ») : reglages de la FICHE AU REGARD ({@code client/farm/FarmHud}), section {@code look_card} du fichier
 * {@code config/pioneers-client.toml}. Classe sans classe client de Minecraft (construite par {@code common/PiClientConfig}, comme la
 * minimap et l'affichage du Periple). Coin et taille se reglent aussi dans la page « Affichage a l'ecran » du guide ({@code GuideScreen}).
 * <ul>
 *     <li>{@code corner} : coin de l'ecran (defaut : en bas a droite) ;</li>
 *     <li>{@code offsetX} / {@code offsetY} : ecart au bord de l'ecran (pixels d'interface) ;</li>
 *     <li>{@code scale} : taille en % (defaut {@link #DEFAULT_SCALE}) ; la fiche n'est jamais dessinee sous un pixel d'ecran par pixel de la
 *     police ({@link #effective}).</li>
 * </ul>
 */
public final class LookHudConfig {

    /** Coin de l'ecran ou se pose la fiche. */
    public enum Corner {
        TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT;

        public boolean right() {
            return this == TOP_RIGHT || this == BOTTOM_RIGHT;
        }

        public boolean bottom() {
            return this == BOTTOM_LEFT || this == BOTTOM_RIGHT;
        }
    }

    public static final Corner DEFAULT_CORNER = Corner.BOTTOM_RIGHT;
    /** Ecart au bord par defaut (pixels d'interface) et taille par defaut (%). */
    public static final int DEFAULT_OFFSET = 4, DEFAULT_SCALE = 75, MIN_SCALE = 50, MAX_SCALE = 150, MAX_OFFSET = 400;
    /** Tailles proposees par le guide (clic : la suivante). */
    public static final int[] SCALES = {60, 75, 90, 100, 125};

    public static ForgeConfigSpec.EnumValue<Corner> CORNER;
    public static ForgeConfigSpec.IntValue OFFSET_X;
    public static ForgeConfigSpec.IntValue OFFSET_Y;
    public static ForgeConfigSpec.IntValue SCALE;

    private LookHudConfig() {
    }

    public static void build(ForgeConfigSpec.Builder b) {
        b.push("look_card");
        CORNER = b.comment("Coin de la fiche au regard : TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT (defaut).").defineEnum("corner", DEFAULT_CORNER);
        OFFSET_X = b.comment("Ecart horizontal au bord de l'ecran (pixels d'interface).").defineInRange("offsetX", DEFAULT_OFFSET, 0, MAX_OFFSET);
        OFFSET_Y = b.comment("Ecart vertical au bord de l'ecran (pixels d'interface).").defineInRange("offsetY", DEFAULT_OFFSET, 0, MAX_OFFSET);
        SCALE = b.comment("Taille de la fiche en % (50 a 150 ; jamais sous un pixel d'ecran par pixel de la police).")
              .defineInRange("scale", DEFAULT_SCALE, MIN_SCALE, MAX_SCALE);
        b.pop();
    }

    // ------------------------------------------------------------------ lecture sure (fichier pas encore charge : valeurs par defaut)
    public static Corner corner() {
        try {
            return CORNER == null ? DEFAULT_CORNER : CORNER.get();
        } catch (IllegalStateException e) {
            return DEFAULT_CORNER;
        }
    }

    public static int offsetX() {
        return num(OFFSET_X, DEFAULT_OFFSET);
    }

    public static int offsetY() {
        return num(OFFSET_Y, DEFAULT_OFFSET);
    }

    public static int scale() {
        return num(SCALE, DEFAULT_SCALE);
    }

    private static int num(ForgeConfigSpec.IntValue v, int fallback) {
        try {
            return v == null ? fallback : v.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    /**
     * Echelle de dessin (pure) : la taille reglee, ramenee au demi-pixel d'ecran pres (texte net) et jamais sous un pixel d'ecran par pixel
     * de la police ({@code guiScale} = echelle d'interface du jeu, 1 a 4 ou plus).
     */
    public static float effective(int percent, double guiScale) {
        double g = Math.max(1, guiScale);
        double phys = Math.round(percent / 100.0 * g * 2) / 2.0;
        return (float) (Math.max(1, phys) / g);
    }

    // ------------------------------------------------------------------ reglages du guide (clic : valeur suivante), sauves tout de suite
    public static void nextCorner() {
        Corner[] all = Corner.values();
        //ordre de parcours : bas droite -> bas gauche -> haut gauche -> haut droite
        Corner c = switch (corner()) {
            case BOTTOM_RIGHT -> Corner.BOTTOM_LEFT;
            case BOTTOM_LEFT -> Corner.TOP_LEFT;
            case TOP_LEFT -> Corner.TOP_RIGHT;
            case TOP_RIGHT -> all[3];
        };
        set(CORNER, c);
    }

    public static void nextScale() {
        int now = scale(), next = SCALES[0];
        for (int s : SCALES) {
            if (s > now) {
                next = s;
                break;
            }
        }
        set(SCALE, next);
    }

    private static <T> void set(ForgeConfigSpec.ConfigValue<T> v, T value) {
        if (v == null) {
            return;
        }
        try {
            v.set(value);
            PiClientConfig.SPEC.save();
        } catch (IllegalStateException ignored) {
            //fichier pas encore charge : rien a faire
        }
    }
}
