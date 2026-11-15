package fr.pioneers.client.guide;

import fr.pioneers.common.PiClientConfig;
import net.minecraftforge.common.ForgeConfigSpec;

/**
 * 1.0.3-beta (lot 2, decision de Guillaume : « la prochaine etape du Periple s'affiche automatiquement a l'ecran, par defaut en haut au
 * milieu ; reglages DANS le guide, sauves cote client ») : reglages de l'affichage a l'ecran ({@link JourneyHud}), section
 * {@code journey_hud} du fichier {@code config/pioneers-client.toml}. Classe sans classe client de Minecraft (construite par
 * {@code common/PiClientConfig}, comme la minimap). Modifies depuis la page « Affichage a l'ecran » du guide ({@code GuideScreen}).
 */
public final class JourneyHudConfig {

    /** Position de l'affichage : en haut au milieu (defaut) ou un coin. */
    public enum Position {
        TOP_CENTER, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT
    }

    /** Taille : echelle de l'affichage. */
    public enum Size {
        SMALL(0.75F), NORMAL(1F), LARGE(1.25F);

        public final float scale;

        Size(float scale) {
            this.scale = scale;
        }
    }

    public static final Position DEFAULT_POSITION = Position.TOP_CENTER;
    public static final int MAX_STEPS = 3;

    public static ForgeConfigSpec.BooleanValue ENABLED;
    public static ForgeConfigSpec.EnumValue<Position> POSITION;
    public static ForgeConfigSpec.IntValue STEPS;
    public static ForgeConfigSpec.EnumValue<Size> SIZE;

    private JourneyHudConfig() {
    }

    public static void build(ForgeConfigSpec.Builder b) {
        b.push("journey_hud");
        ENABLED = b.comment("Affiche la prochaine etape du Periple (guide) a l'ecran.").define("enabled", true);
        POSITION = b.comment("Position : TOP_CENTER (haut milieu), TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT.").defineEnum("position", DEFAULT_POSITION);
        STEPS = b.comment("Nombre d'etapes affichees (1 a 3).").defineInRange("steps", 1, 1, MAX_STEPS);
        SIZE = b.comment("Taille : SMALL, NORMAL, LARGE.").defineEnum("size", Size.NORMAL);
        b.pop();
    }

    // ------------------------------------------------------------------ lecture sure (fichier pas encore charge : valeurs par defaut)
    public static boolean enabled() {
        try {
            return ENABLED == null || ENABLED.get();
        } catch (IllegalStateException e) {
            return true;
        }
    }

    public static Position position() {
        try {
            return POSITION == null ? DEFAULT_POSITION : POSITION.get();
        } catch (IllegalStateException e) {
            return DEFAULT_POSITION;
        }
    }

    public static int steps() {
        try {
            return STEPS == null ? 1 : STEPS.get();
        } catch (IllegalStateException e) {
            return 1;
        }
    }

    public static Size size() {
        try {
            return SIZE == null ? Size.NORMAL : SIZE.get();
        } catch (IllegalStateException e) {
            return Size.NORMAL;
        }
    }

    // ------------------------------------------------------------------ reglages du guide (clic : valeur suivante), sauves tout de suite
    public static void toggle() {
        set(ENABLED, !enabled());
    }

    public static void nextPosition() {
        Position[] all = Position.values();
        set(POSITION, all[(position().ordinal() + 1) % all.length]);
    }

    public static void nextSteps() {
        set(STEPS, steps() % MAX_STEPS + 1);
    }

    public static void nextSize() {
        Size[] all = Size.values();
        set(SIZE, all[(size().ordinal() + 1) % all.length]);
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
