package fr.pioneers.api.machine;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : VALEURS D'INTERFACE NOMMEES. La tile declare ses valeurs synchronisees par une cle typee ({@link Key}) ; l'indice de
 * chaque cle est attribue a la construction (meme code des deux cotes : le menu du client est construit sur la tile du client, du meme
 * bloc, qui declare les memes cles dans le meme ordre). L'ecran lit {@code menu.value(cle)} au lieu d'un indice ecrit en dur ({@code v(4)}).
 * <p>
 * Les quatre valeurs communes de {@code MachineTile} gardent leur place (0 energie, 1 reserve, 2 consommation FE/t x100, 3 commande
 * redstone) : c'est la convention de toutes les interfaces standard, aussi celle des tiles qui n'ont pas encore de valeurs nommees
 * ({@link Key#legacy}).
 */
public final class UiValues {

    /** Cle d'une valeur ; {@code legacy} : indice de la valeur chez une tile sans valeurs nommees (-1 : aucun). */
    public record Key(String name, int legacy) {

        public static Key of(String name) {
            return new Key(name, -1);
        }
    }

    /** Valeurs communes (toutes les interfaces standard). */
    public static final Key ENERGY = new Key("energy", 0), CAPACITY = new Key("capacity", 1), USAGE = new Key("usage_x100", 2),
          REDSTONE = new Key("redstone", 3);
    /** Valeurs d'une machine a procede ({@code ProcessUnit}). */
    public static final Key PROGRESS = Key.of("progress"), DURATION = Key.of("duration"), STATUS = Key.of("status"),
          PRODUCTION = Key.of("production"), TIER_NEEDED = Key.of("tier_needed");
    /**
     * Lot 3 : valeurs d'un buffer ({@code StorageUnit}) : objets (cases occupees, cases) ; fluide, gaz, energie (quantite, contenance, debit
     * net x100) ; chaleur (temperature x100, capacite thermique, debit x100) ; air (air x100, air maximal x100, debit x100, pression x1000,
     * pression maximale x1000). Memes indices qu'avant le lot 3.
     */
    public static final Key SLOTS_USED = Key.of("slots_used"), SLOTS = Key.of("slots"), STORED = Key.of("stored"),
          STORED_MAX = Key.of("stored_max"), FLOW = Key.of("flow_x100"), TEMPERATURE = Key.of("temperature_x100"),
          HEAT_CAPACITY = Key.of("heat_capacity"), AIR = Key.of("air_x100"), AIR_MAX = Key.of("air_max_x100"),
          PRESSURE = Key.of("pressure_x1000"), PRESSURE_MAX = Key.of("pressure_max_x1000");
    /**
     * Lot 3 : valeurs d'un generateur ({@code GeneratorUnit}) : genre, production moyenne FE/t x100, production maximale, facteur x1000,
     * mesures propres ({@link #detail}) ; turbine compacte : vapeur, vapeur detendue, etat ; echappement : rejet, ciel.
     */
    public static final Key GEN_KIND = Key.of("gen_kind"), OUTPUT = Key.of("output_x100"), PEAK = Key.of("peak"), FACTOR = Key.of("factor_x1000"),
          STEAM = Key.of("steam"), STEAM_MAX = Key.of("steam_max"), EXHAUST = Key.of("exhaust"), EXHAUST_MAX = Key.of("exhaust_max"),
          STEAM_USE = Key.of("steam_use_x100"), STATE = Key.of("state"), VENTED = Key.of("vented_x100"), SKY = Key.of("sky");
    /** 1.0.4-beta (lot 5) : producteur plein qui continue (1 = une partie de la production est perdue : « Surplus perdu »). */
    public static final Key SURPLUS = Key.of("surplus");
    /**
     * 1.0.5-beta (lot 38) : machines de traitement a vapeur (T1 de leur famille) : rendement de la recette en cours x1000 (R(1)/R(m) sur une
     * recette a rendement, 1000 sinon), trop-plein en attente (objets).
     */
    public static final Key YIELD_X1000 = Key.of("yield_x1000"), OVERFLOW = Key.of("overflow");
    /** Lot 3 : radiateur et compresseur d'air : reglages et mesures (memes indices qu'avant). */
    public static final Key POWER_SETTING = Key.of("power_setting"), TARGET = Key.of("target"), DELIVERED = Key.of("delivered_x100"),
          PRODUCED = Key.of("produced_x100"), MAX_TEMPERATURE = Key.of("max_temperature"), TARGET_PRESSURE = Key.of("target_x1000");

    /**
     * Lot 7 : machines de l'age mecanique et du bronze (energie motrice rotation, vapeur, feu) : vitesse x100, couple x10, avancement x1000,
     * cadence en % de la nominale, sorte de machine, puissance du reseau (W), tours de la recette x100 ; dynamo : puissance mecanique (W),
     * adaptation x1000, excitation, puissance, vitesse et couple nominaux, sortie ; feu : temperature, combustion ; vapeur : vapeur prise.
     */
    public static final Key SPEED = Key.of("speed_x100"), TORQUE = Key.of("torque_x10"), PROGRESS_X1000 = Key.of("progress_x1000"),
          RATE = Key.of("rate_pct"), MACHINE_TYPE = Key.of("machine_type"), NET_POWER = Key.of("net_power"), TURNS = Key.of("turns_x100"),
          MECHANICAL = Key.of("mechanical_w"), MATCH = Key.of("match_x1000"), EXCITATION = Key.of("excitation"), RATED = Key.of("rated"),
          MAX_SPEED = Key.of("max_speed"), RATED_TORQUE = Key.of("rated_torque"), OUTPUT_OK = Key.of("output_ok"),
          HEAT = Key.of("heat"), BURN = Key.of("burn"), BURN_MAX = Key.of("burn_max"), RECIPE_INDEX = Key.of("recipe_index"),
          FLUID = Key.of("fluid"), FLUID_MAX = Key.of("fluid_max"), BLAST = Key.of("blast_x1000");

    /**
     * Lot 8b : dernieres machines de l'age mecanique et du bronze : poste de cuisine (liquide de sortie), machine a balancier (chaudiere en K
     * x10, eau, contenance, a sec), machine a vapeur (pression utile x1000, humidite x1000), fourneau a creuset (temperature accessible x10, metal fondu mB, coulee auto),
     * fusion d'une case du creuset / cuisson d'une place du four ({@link #melt}, {@link #firing}). Memes indices qu'avant le lot 8b. 1.0.4-beta (lot 27) : injecteur
     * et condenseur retires (leurs valeurs propres aussi : debit d'eau, chaleur, refroidisseurs, condenseur de la machine a vapeur).
     */
    public static final Key FLUID_OUT = Key.of("fluid_out"), BOILER = Key.of("boiler_k_x10"), WATER = Key.of("water"), WATER_MAX = Key.of("water_max"),
          DRY = Key.of("dry"), USEFUL = Key.of("useful_x1000"), WET = Key.of("wet_x1000"), WORKING = Key.of("working"),
          REACH = Key.of("reach_x10"), MOLTEN = Key.of("molten_mb"), AUTO = Key.of("auto");

    /**
     * Lot 8b : corps propres en vraie v2 : recherche visee, consommation propre (FE/t), unite de recherche, attente, rang du plan, vitesse x100,
     * destination, sondes, debit, cable branche, en ecoute, cellules en production, cout par cellule.
     */
    public static final Key RESEARCH = Key.of("research"), FE_PER_TICK = Key.of("fe_per_tick"), UNIT = Key.of("unit"), WAITING = Key.of("waiting"),
          PRINT = Key.of("print"), SPEED_X100 = Key.of("speed_rate_x100"), DESTINATION = Key.of("destination"), PROBES = Key.of("probes"),
          DATA_RATE = Key.of("data_rate"), LINKED = Key.of("linked"), LISTENING = Key.of("listening"), CELLS = Key.of("cells"),
          CELL_COST = Key.of("cell_cost");

    /**
     * 1.0.2-beta : pieces de transmission a curseur (boite de vitesses, embrayage, inverseur, regulateur a boules) : vitesse de sortie x100,
     * position du curseur du rapport (0 = x1/4 .. 4 = x4), sorte de piece (0 boite, 1 embrayage, 2 inverseur), signal redstone recu ;
     * regulateur : vitesse visee (tr/min), frein serre.
     */
    public static final Key OUT_SPEED = Key.of("out_speed_x100"), RATIO = Key.of("ratio_slider"), TRANSMISSION = Key.of("transmission"),
          POWERED = Key.of("powered"), TARGET_RPM = Key.of("target_rpm"), LIMITING = Key.of("limiting");

    /**
     * 1.0.2-beta (lot 2) : foyer de forge a l'interface v2 : limites de chargement (curseurs : lingots et charbon gardes), temperature de la
     * piece dans la braise x10 et temperature de travail de son metal, avancement de la chauffe d'un lopin d'acier x1000.
     */
    public static final Key INGOT_LIMIT = Key.of("ingot_limit"), FUEL_LIMIT = Key.of("fuel_limit"), PIECE_HEAT = Key.of("piece_c_x10"),
          PIECE_WORK = Key.of("piece_work_c"), BLOOM = Key.of("bloom_x1000");

    /** Lot 8b : sorte de donnees du reservoir n (identifiant de type, -1 vide). */
    public static Key dataType(int n) {
        return Key.of("data_type_" + n);
    }

    /** Lot 8b : contenu du reservoir de donnees n. */
    public static Key dataStored(int n) {
        return Key.of("data_stored_" + n);
    }

    /** Lot 8b : valeur propre n d'une machine a corps propre (sondes par destination, decompte...). */
    public static Key item(String family, int n) {
        return Key.of(family + "_" + n);
    }

    /** Lot 8b : fusion de la case n (0..3) du fourneau a creuset (x1000). */
    public static Key melt(int n) {
        return Key.of("melt_" + n);
    }

    /** Lot 8b : cuisson de la place n (0..3) du four de potier (x1000, -1 : rien a cuire). */
    public static Key firing(int n) {
        return Key.of("firing_" + n);
    }

    /** Lot 3 : mesure propre n (0..3) d'un generateur (soleil, vent, chute...). */
    public static Key detail(int n) {
        return Key.of("detail_" + n);
    }

    private final List<Key> keys;
    private final List<LongSupplier> values;
    private final Map<String, Integer> index = new HashMap<>();

    private UiValues(List<Key> keys, List<LongSupplier> values) {
        this.keys = List.copyOf(keys);
        this.values = List.copyOf(values);
        for (int i = 0; i < this.keys.size(); i++) {
            index.put(this.keys.get(i).name(), i);
        }
    }

    public static Builder builder() {
        return new Builder();
    }

    public int size() {
        return keys.size();
    }

    /** Valeur d'indice {@code i} (cote serveur). */
    public long get(int i) {
        return i >= 0 && i < values.size() ? values.get(i).getAsLong() : 0;
    }

    /** Indice de la cle, ou -1 si la machine ne la declare pas. */
    public int index(Key key) {
        Integer i = index.get(key.name());
        return i == null ? -1 : i;
    }

    public List<Key> keys() {
        return keys;
    }

    /** Declaration des valeurs (dans l'ordre : l'indice de chaque cle est sa place). */
    public static final class Builder {

        private final List<Key> keys = new ArrayList<>();
        private final List<LongSupplier> values = new ArrayList<>();

        public Builder add(Key key, LongSupplier value) {
            for (Key k : keys) {
                if (k.name().equals(key.name())) {
                    throw new IllegalStateException("valeur d'interface declaree deux fois : " + key.name());
                }
            }
            keys.add(key);
            values.add(value);
            return this;
        }

        /** Valeur sans nom (tile pas encore migree : ses indices restent ceux de ses commentaires). */
        public Builder legacy(int index, LongSupplier value) {
            return add(new Key("#" + index, index), value);
        }

        public UiValues build() {
            return new UiValues(keys, values);
        }
    }

    /** Indice d'une cle chez une machine (valeurs nommees si elle en a, sinon l'indice historique de la cle). */
    public static int indexOf(@Nullable UiValues values, Key key) {
        return values != null ? values.index(key) : key.legacy();
    }
}
