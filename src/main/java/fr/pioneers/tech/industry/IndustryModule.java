// FICHIER GENERE par tools/gc/industry/gen_java.py depuis catalog.py : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.tech.industry;

/**
 * Metiers des multiblocs (anciens modules de travail). 1.0.4-beta (lot 23) : seuls les modules a bloc ({@link #block}) existent encore comme
 * blocs (la baie de serveurs) ; la colonne montante de la plate-forme offshore est faite de pompes a colonne (tag
 * {@code pioneers:multiblock/module/riser_module}) ; les autres metiers n'ont plus de bloc (interieur vide).
 */
public enum IndustryModule {
    GRINDING("grinding", false),
    SEPARATION("separation", false),
    HEATING("heating", false),
    PRESSING("pressing", false),
    ROLLING("rolling", false),
    ELECTRODE("electrode", false),
    VESSEL("vessel", false),
    MACHINING("machining", false),
    ROTOR("rotor", false),
    TRAY("tray", false),
    COOLING("cooling", false),
    BOILER("boiler", false),
    DRILLING("drilling", false),
    REACTOR("reactor", false),
    MEMBRANE("membrane", false),
    CLEANROOM("cleanroom", false),
    PUMP("pump", false),
    RISER("riser", false),
    GROWING("growing", false),
    AQUATIC("aquatic", false),
    GAS_CENTRIFUGE("gas_centrifuge", false),
    FUEL_RACK("fuel_rack", false),
    HOT_CELL("hot_cell", false),
    SERVER_RACK("server_rack", true),
    QUBIT_ARRAY("qubit_array", false),
    PROPELLANT_SPHERE("propellant_sphere", false),
    GRAVITON_COIL("graviton_coil", false),
    SYNTHESIS_MATRIX("synthesis_matrix", false);

    public final String key;
    /** 1.0.4-beta (lot 23) : le module a-t-il encore un bloc (enregistre dans PiIndustry.MODULES) ? */
    public final boolean block;

    IndustryModule(String key, boolean block) {
        this.key = key;
        this.block = block;
    }

    public String id() {
        return key + "_module";
    }
}
