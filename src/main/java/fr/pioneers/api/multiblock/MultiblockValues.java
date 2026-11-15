package fr.pioneers.api.multiblock;

import fr.pioneers.api.machine.UiValues.Key;
import fr.pioneers.api.machine.UiValues;

/**
 * API Multibloc (lot 5) : VALEURS D'INTERFACE NOMMEES d'un controleur de multibloc, aux indices d'avant le lot 5, puis, nouvelles, le
 * DECOMPTE PAR ROLE de l'onglet Structure ({@link StructureMatcher#census}). Lot 6 : les valeurs des coeurs propres ({@link MultiblockLogic} :
 * reacteur a fission 20..41, 58, 59 ; fusion 42..57 ; calcul 61..70) sont nommees a leur tour, a leur place : plus aucun indice brut.
 */
public final class MultiblockValues {

    public static final Key STATUS = Key.of("mb_status"), ERROR = Key.of("mb_error"), PROGRESS = Key.of("mb_progress_x1000"), DURATION = Key.of("mb_duration"),
          BATCH = Key.of("mb_batch"), PARALLEL = Key.of("mb_parallel"), MODULES = Key.of("mb_modules"), TIER = Key.of("mb_tier"),
          TEMPERATURE = Key.of("mb_temperature_x10"), NOMINAL = Key.of("mb_nominal"), DIMENSIONS = Key.of("mb_dimensions"), POWER = Key.of("mb_power"),
          ERROR_AT = Key.of("mb_error_at"), RESERVE = Key.of("mb_reserve"), ERROR_DETAIL = Key.of("mb_error_detail"), HEAT_IN = Key.of("mb_heat_in"),
          HEAT_SAVED = Key.of("mb_heat_saved"), AIR = Key.of("mb_air_x10"), PENALTY = Key.of("mb_penalty"), RECIPE_AIR = Key.of("mb_recipe_air_x10"),
          CHIP = Key.of("mb_chip"), RISERS = Key.of("mb_risers"), TUBES = Key.of("mb_tubes"),
          TUBES_NEEDED = Key.of("mb_tubes_needed"), TIER_NEEDED = Key.of("mb_tier_needed"), PORTS = Key.of("mb_ports"),
          SILO = Key.of("mb_silo");

    /**
     * Lot 6 : reacteur a fission ({@code FissionCore.value}) : puissance thermique J/t, barres demandees (%), barres reelles x10, drapeaux (1
     * arret d'urgence, 2 arret auto, 4 alarme), assemblages, canaux, combustion x1000, T d'arret, T de fusion, fusion du coeur x1000,
     * puissance max, mecanismes de grappe, generation (1..3), sodium x1000, pompes | echangeurs &lt;&lt; 8, bobines | collecteurs &lt;&lt; 8,
     * champ x1000, electricite FE/t, cibles | couvertures &lt;&lt; 8, charge des cibles x1000, refroidissement x1000, chaleur nette ;
     * diagnostic | pilote &lt;&lt; 8 ; consigne du pilote (K).
     */
    public static final Key R_POWER = Key.of("mb_reactor_power"), R_RODS = Key.of("mb_reactor_rods"), R_RODS_ACTUAL = Key.of("mb_reactor_rods_actual_x10"),
          R_FLAGS = Key.of("mb_reactor_flags"), R_FUEL = Key.of("mb_reactor_fuel"), R_CHANNELS = Key.of("mb_reactor_channels"),
          R_BURNUP = Key.of("mb_reactor_burnup_x1000"), R_SCRAM_T = Key.of("mb_reactor_scram_t"), R_MELT_T = Key.of("mb_reactor_melt_t"),
          R_MELT = Key.of("mb_reactor_melt_x1000"), R_MAX_POWER = Key.of("mb_reactor_max_power"), R_DRIVES = Key.of("mb_reactor_drives"),
          R_MARK = Key.of("mb_reactor_mark"), R_SODIUM = Key.of("mb_reactor_sodium_x1000"), R_PUMPS = Key.of("mb_reactor_pumps"),
          R_COILS = Key.of("mb_reactor_coils"), R_FIELD = Key.of("mb_reactor_field_x1000"), R_ELECTRIC = Key.of("mb_reactor_electric"),
          R_CHARGES = Key.of("mb_reactor_charges"), R_CHARGE = Key.of("mb_reactor_charge_x1000"), R_COOLING = Key.of("mb_reactor_cooling_x1000"),
          R_NET = Key.of("mb_reactor_net"), R_WHY = Key.of("mb_reactor_why"), R_SETPOINT = Key.of("mb_reactor_setpoint");
    /**
     * Lot 6 : fusion ({@code FusionCore.value}) : puissance J/t, max, plasma MK x10, drapeaux (1 chauffage, 2 disruption, 4 ignition, 8 arret
     * commande, 16 helium manquant, 32 energie manquante), Q x100 (-1 infini), champ x1000, chauffage FE/t, mark, bobines | injecteurs &lt;&lt; 8
     * | modules &lt;&lt; 16, electricite FE/t, part electrique x1000, surgeneration x1000, densite x1000, ignition MK, pic MK, risque x1000.
     */
    public static final Key F_POWER = Key.of("mb_fusion_power"), F_MAX = Key.of("mb_fusion_max"), F_PLASMA = Key.of("mb_fusion_plasma_x10"),
          F_FLAGS = Key.of("mb_fusion_flags"), F_Q = Key.of("mb_fusion_q_x100"), F_FIELD = Key.of("mb_fusion_field_x1000"),
          F_HEATING = Key.of("mb_fusion_heating"), F_MARK = Key.of("mb_fusion_mark"), F_PARTS = Key.of("mb_fusion_parts"),
          F_ELECTRIC = Key.of("mb_fusion_electric"), F_SHARE = Key.of("mb_fusion_share_x1000"), F_BREEDING = Key.of("mb_fusion_breeding_x1000"),
          F_DENSITY = Key.of("mb_fusion_density_x1000"), F_IGNITION = Key.of("mb_fusion_ignition"), F_PEAK = Key.of("mb_fusion_peak"),
          F_RISK = Key.of("mb_fusion_risk_x1000");
    /**
     * Lot 6 : calcul ({@code ComputeCore.value}) : genre (1 centre de donnees, 2 quantique), offre x10, servi x10, offre pleine x10, charge
     * x1000, bridage / froid x1000, baies | groupes froids &lt;&lt; 8, FE/t, telemetrie Mo/s x10 / temperature mK, alimentation (1 helium 3,
     * 2 helium liquide, 4 energie).
     */
    public static final Key C_KIND = Key.of("mb_compute_kind"), C_CAPACITY = Key.of("mb_compute_capacity_x10"), C_SERVED = Key.of("mb_compute_served_x10"),
          C_FULL = Key.of("mb_compute_full_x10"), C_LOAD = Key.of("mb_compute_load_x1000"), C_FACTOR = Key.of("mb_compute_factor_x1000"),
          C_UNITS = Key.of("mb_compute_units"), C_POWER = Key.of("mb_compute_power"), C_TELEMETRY = Key.of("mb_compute_telemetry"),
          C_SUPPLY = Key.of("mb_compute_supply");

    /** Indices d'avant le lot 5 (valeur {@code machineValue(i)}) et leur nom (lot 6 : tous nommes). */
    public static final Key[] BY_INDEX = new Key[76];

    static {
        Key[] named = {STATUS, ERROR, PROGRESS, DURATION, BATCH, PARALLEL, MODULES, TIER, TEMPERATURE, NOMINAL, DIMENSIONS, POWER, ERROR_AT, RESERVE,
              ERROR_DETAIL, HEAT_IN, HEAT_SAVED, AIR, PENALTY, RECIPE_AIR};
        System.arraycopy(named, 0, BY_INDEX, 0, named.length);
        Key[] reactor = {R_POWER, R_RODS, R_RODS_ACTUAL, R_FLAGS, R_FUEL, R_CHANNELS, R_BURNUP, R_SCRAM_T, R_MELT_T, R_MELT, R_MAX_POWER, R_DRIVES,
              R_MARK, R_SODIUM, R_PUMPS, R_COILS, R_FIELD, R_ELECTRIC, R_CHARGES, R_CHARGE, R_COOLING, R_NET};
        System.arraycopy(reactor, 0, BY_INDEX, 20, reactor.length);
        Key[] fusion = {F_POWER, F_MAX, F_PLASMA, F_FLAGS, F_Q, F_FIELD, F_HEATING, F_MARK, F_PARTS, F_ELECTRIC, F_SHARE, F_BREEDING, F_DENSITY,
              F_IGNITION, F_PEAK, F_RISK};
        System.arraycopy(fusion, 0, BY_INDEX, 42, fusion.length);
        BY_INDEX[58] = R_WHY;
        BY_INDEX[59] = R_SETPOINT;
        BY_INDEX[60] = CHIP;
        Key[] compute = {C_KIND, C_CAPACITY, C_SERVED, C_FULL, C_LOAD, C_FACTOR, C_UNITS, C_POWER, C_TELEMETRY, C_SUPPLY};
        System.arraycopy(compute, 0, BY_INDEX, 61, compute.length);
        //1.0.4-beta (lot 15) : 71 (blocs de paroi fonctionnels) retire ; lot 22 : 71 = contenu du silo a objets (SiloCore : objets | volume << 40)
        BY_INDEX[71] = SILO;
        BY_INDEX[72] = RISERS;
        BY_INDEX[73] = TUBES;
        BY_INDEX[74] = TUBES_NEEDED;
        BY_INDEX[75] = TIER_NEEDED;
    }

    /** Lot 5 : decompte par role ({@link StructureMatcher#SHELL}... : bonnes | total &lt;&lt; 16). */
    public static Key census(int category) {
        return Key.of("mb_census_" + category);
    }

    private MultiblockValues() {
    }

    /** Lecture d'une valeur du decompte : (bonnes, total). */
    public static int[] split(long packed) {
        return new int[]{(int) (packed & 0xFFFF), (int) (packed >> 16 & 0xFFFF)};
    }

    /** Les cles de l'onglet Structure (apres les 76 valeurs d'avant). */
    public static void declareCensus(UiValues.Builder b, java.util.function.IntToLongFunction census) {
        for (int i = 0; i < StructureMatcher.CATEGORIES; i++) {
            int k = i;
            b.add(census(i), () -> census.applyAsLong(k));
        }
        b.add(PORTS, () -> census.applyAsLong(StructureMatcher.CATEGORIES));
    }
}
