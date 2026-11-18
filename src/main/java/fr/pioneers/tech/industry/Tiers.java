package fr.pioneers.tech.industry;

import fr.pioneers.api.machine.PowerRules;
import fr.pioneers.logistics.transport.CableTier;

/**
 * Regles des tiers de l'age industriel (decisions de Guillaume, 23/10/2026) : 1 024 FE/t au tier basique, x4 par tier pour x2 de vitesse.
 * Multiblocs : plafond de parallelisme 4 / 8 / 16 / 32 (1.10.1, proposition de Guillaume ; avant 4 / 16 / 64 / 256), 80 % du temps et 75 % de l'energie par operation, temperature nominale des
 * carcasses 800 / 1 100 / 1 500 / 2 100 K.
 */
public final class Tiers {

    /** API Machine (lot 1) : les regles sont dans {@link PowerRules} (seule source Java) ; ces noms restent pour le code d'avant. */
    public static final long BASE_POWER = PowerRules.BASE_POWER;
    /** Multibloc : duree d'une operation (fraction de celle d'une machine d'atelier). */
    public static final double INDUSTRIAL_TIME = PowerRules.INDUSTRIAL_TIME;
    /** Multibloc : energie par operation (fraction). */
    public static final double INDUSTRIAL_ENERGY = PowerRules.INDUSTRIAL_ENERGY;
    /** Multibloc generateur : bonus de rendement. */
    public static final double INDUSTRIAL_YIELD = PowerRules.INDUSTRIAL_YIELD;
    /**
     * 1.10.1 (Guillaume : « la multiplication par 256 est beaucoup trop elevee ; ma proposition : 4 -> 8 -> 16 -> 32 ») : x2 par tier, comme la
     * vitesse ; la puissance d'un multibloc suit (x8 par tier au lieu de x16). La plate-forme offshore garde 2 operations par colonne
     * ({@code Structure.PER_MODULE}) : 2 / 4 / 8 / 16 colonnes au plus.
     */

    private Tiers() {
    }

    public static int index(CableTier tier) {
        return tier.ordinal();
    }

    /** Consommation d'une machine du tier (FE/t). */
    public static long power(int tier) {
        return PowerRules.power(tier);
    }

    public static long power(CableTier tier) {
        return power(tier.ordinal());
    }

    /** Multiplicateur de vitesse d'un tier par rapport au tier de la recette (x2 par tier d'ecart). */
    public static int speed(int machineTier, int recipeTier) {
        return PowerRules.speed(machineTier, recipeTier);
    }

    /** Duree d'une operation (ticks) dans une machine du tier. */
    public static int ticks(int recipeTime, int machineTier, int recipeTier, boolean industrial) {
        return ticks(recipeTime, machineTier, recipeTier, industrial, null);
    }

    /**
     * 1.0.6 : duree d'une operation avec le PLANCHER des durees ({@link PowerRules#floorTicks} : au moins 1 s au MKIV, 2 s ou 4 s pour les
     * recettes MKIII et MKIV, x 2 par tier d'avant) ; jamais au-dessus de la duree de base ; machines de {@link PowerRules#NO_FLOOR} sans plancher.
     */
    public static int ticks(int recipeTime, int machineTier, int recipeTier, boolean industrial, @org.jetbrains.annotations.Nullable IndustryMachine machine) {
        double t = recipeTime / (double) speed(machineTier, recipeTier);
        if (machine == null || !PowerRules.NO_FLOOR.contains(machine.key)) {
            t = Math.max(t, Math.min(recipeTime, PowerRules.floorTicks(machineTier + 2, recipeTier)));
        }
        if (industrial) {
            t *= INDUSTRIAL_TIME;
        }
        return Math.max(1, (int) Math.round(t));
    }

    public static int parallelCap(int tier) {
        return PowerRules.parallelCap(tier);
    }

    public static int nominal(int tier) {
        return PowerRules.nominal(tier);
    }

    /** Rendement des traitements de minerai : x1 basique, x1,25 avance, x1,5 elite, x2 ultime (sur le x2 deja donne par la recette). */
    public static double oreBonus(int tier) {
        return switch (Math.max(0, Math.min(3, tier))) {
            case 1 -> 1.25;
            case 2 -> 1.5;
            case 3 -> 2.0;
            default -> 1.0;
        };
    }

    public static CableTier tier(int index) {
        return CableTier.values()[Math.max(0, Math.min(CableTier.values().length - 1, index))];
    }
}
