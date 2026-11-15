package fr.pioneers.kinetics;

import net.minecraftforge.common.ForgeConfigSpec;

/** Reglages de l'age mecanique (fichier config/pioneers-mechanics.toml). */
public final class KineticsConfig {

    private KineticsConfig() {
    }

    public enum Breakage {
        ON, WARN, OFF
    }

    public static final ForgeConfigSpec SPEC;
    public static final ForgeConfigSpec.EnumValue<Breakage> BREAKAGE;
    public static final ForgeConfigSpec.DoubleValue SOURCE_POWER;
    public static final ForgeConfigSpec.DoubleValue MACHINE_SPEED;
    public static final ForgeConfigSpec.IntValue ROPE_RANGE;
    public static final ForgeConfigSpec.BooleanValue VANILLA_ORES;
    public static final ForgeConfigSpec.BooleanValue FURNACE_CHARCOAL;
    public static final ForgeConfigSpec.DoubleValue CHARCOAL_SPEED;
    public static final ForgeConfigSpec.BooleanValue ANIMAL_FOOD;
    public static final ForgeConfigSpec.EnumValue<Breakage> BOILER_EXPLOSION;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.comment("Force rotative : arbres, engrenages, cordes, sources et machines mecaniques.").push("kinetics");
        BREAKAGE = b.comment("Casse des pieces trop chargees : ON (casse), WARN (avertissement seul), OFF.").defineEnum("breakage", Breakage.ON);
        SOURCE_POWER = b.comment("Multiplicateur du couple des sources (manivelle, manege, roues, moulins, machine a balancier).")
              .defineInRange("sourcePower", 1.0, 0.1, 10.0);
        MACHINE_SPEED = b.comment("Multiplicateur de la cadence des machines mecaniques.").defineInRange("machineSpeed", 1.0, 0.1, 10.0);
        ROPE_RANGE = b.comment("Portee maximale d'une corde ou d'une courroie entre deux poulies (blocs).").defineInRange("ropeRange", 24, 4, 64);
        ANIMAL_FOOD = b.comment("Les animaux du manege doivent etre nourris (auge).").define("animalFood", true);
        b.pop();
        b.comment("Monde de l'age mecanique.").push("world");
        VANILLA_ORES = b.comment("Garde la generation des minerais dans la roche, toutes dimensions (false : aucun minerai ; tamisage puis gisements et foreuses).")
              .define("vanillaOres", false);
        FURNACE_CHARCOAL = b.comment("Le four vanilla cuit le charbon de bois (false : il faut la fosse a charbon).").define("furnaceCharcoal", false);
        CHARCOAL_SPEED = b.comment("Multiplicateur de la vitesse de combustion des fosses a charbon.").defineInRange("charcoalPitSpeed", 1.0, 0.1, 20.0);
        b.pop();
        //1.0.2-beta (lot 3) : plus de pression ; le seul danger de la chaudiere est de chauffer sans eau
        b.comment("Age du bronze : vapeur.").push("bronze");
        BOILER_EXPLOSION = b.comment("Chaudiere chauffee sans eau : ON (elle explose), WARN (avertissement, puis le feu s'eteint), OFF (rien).")
              .defineEnum("boilerExplosion", Breakage.ON);
        b.pop();
        SPEC = b.build();
    }

    private static <T> T get(ForgeConfigSpec.ConfigValue<T> value, T fallback) {
        try {
            return value.get();
        } catch (IllegalStateException e) {
            return fallback;
        }
    }

    public static Breakage breakage() {
        return get(BREAKAGE, Breakage.ON);
    }

    public static double sourcePower() {
        return get(SOURCE_POWER, 1.0);
    }

    public static double machineSpeed() {
        return get(MACHINE_SPEED, 1.0);
    }

    public static int ropeRange() {
        return get(ROPE_RANGE, 24);
    }

    public static boolean vanillaOres() {
        return get(VANILLA_ORES, false);
    }

    public static boolean furnaceCharcoal() {
        return get(FURNACE_CHARCOAL, false);
    }

    public static double charcoalSpeed() {
        return get(CHARCOAL_SPEED, 1.0);
    }

    public static Breakage boilerExplosion() {
        return get(BOILER_EXPLOSION, Breakage.ON);
    }

    public static boolean animalFood() {
        return get(ANIMAL_FOOD, true);
    }
}
