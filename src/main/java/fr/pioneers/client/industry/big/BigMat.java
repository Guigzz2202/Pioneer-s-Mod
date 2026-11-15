package fr.pioneers.client.industry.big;

/**
 * 0.19.14 : materiaux des machines interieures : une texture 32x32 chacun ({@code textures/block/industry/<path>.png}, generees par
 * {@code T:industry/big_textures.py} sauf l'acier des turbines, repris des pieces). GLASS et LIQUID (teinte par sommet) sont translucides ; les autres sont pleins (sans
 * elimination des faces arriere).
 */
public enum BigMat {
    CAST("big/cast"),
    CAST_L("big/cast_l"),
    ROLL("big/roll"),
    DRUM("big/drum"),
    TOOTH("big/tooth"),
    TOOTH_T("big/tooth_t"),
    ORE1("big/ore1"),
    ORE2("big/ore2"),
    WOOD1("big/wood1"),
    WOOD2("big/wood2"),
    PIPE("big/pipe"),
    LED("big/led"),
    ALGAE_S("big/algae_s"),
    ALGAE_D("big/algae_d"),
    BUBBLE("big/bubble"),
    RUBBER("big/rubber"),
    MOTOR("big/motor"),
    STRIP("big/strip"),
    CHROME("mat/chrome"),
    STEEL_D("part/v2/steel_dark"),
    GLASS("big/glass"),
    //0.19.15 (lot 2)
    REFR_L("big/refr_l"),
    REFR_D("big/refr_d"),
    MOLTEN("big/molten"),
    SLAG("big/slag"),
    COKE("big/coke"),
    LIQUID("big/liquid"),
    FROTH("big/froth"),
    BALL("big/ball"),
    CAT("big/cat"),
    GRATE("big/grate"),
    ANODE("big/anode"),
    CATHODE("big/cathode"),
    FLAME("big/flame"),
    FLAME_C("big/flame_c"),
    GLOWROD("big/glowrod"),
    GLOWBED("big/glowbed"),
    PAINT_R("big/paint_r"),
    PAINT_O("big/paint_o"),
    PCB("big/pcb"),
    //0.20.0 : chemin de fer (textures/block/rail/)
    R_STEEL("steel", true),
    R_STEEL_L("steel_l", true),
    R_DARK("dark", true),
    R_FRAME("frame", true),
    R_RAIL("rail", true),
    R_RAIL_FOOT("rail_foot", true),
    R_OAK("oak", true),
    R_TIE("tie", true),
    R_CONCRETE("concrete", true),
    R_INOX("inox", true),
    R_LUNAR("lunar", true),
    R_BALLAST("ballast", true),
    R_BALLAST_D("ballast_d", true),
    R_GRAVEL("gravel", true),
    R_EARTH("earth", true),
    R_BRASS("brass", true),
    R_COPPER("copper", true),
    R_BLACK("black", true),
    R_BOILER("boiler", true),
    R_RED("red", true),
    R_WHEEL("wheel", true),
    R_CHROME("chrome", true),
    R_GLASS_D("glass_d", true),
    R_LAMP("lamp", true),
    R_LAMP_R("lamp_r", true),
    R_LAMP_G("lamp_g", true),
    R_LAMP_O("lamp_o", true),
    R_LAMP_B("lamp_b", true),
    R_LAMP_OFF("lamp_off", true),
    R_COAL("coal", true),
    R_BULK("bulk", true),
    R_GRILL("grill", true),
    R_GLOW_CYAN("glow_cyan", true),
    R_GLOW_BLUE("glow_blue", true),
    R_WHITE("white", true),
    R_PLANKS("planks", true),
    R_SCREEN("screen", true),
    R_STRIPE("stripe", true),
    R_HAZARD("hazard", true),
    R_LEAD("lead", true),
    //0.20.2 : dalles du quai voyageurs
    R_SLAB("slab", true),
    R_TIER1("tier1", true),
    R_TIER2("tier2", true),
    R_TIER3("tier3", true),
    R_TIER4("tier4", true),
    R_LIVERY1("livery1", true),
    R_LIVERY2("livery2", true),
    R_LIVERY3("livery3", true),
    R_LIVERY4("livery4", true),
    R_FAM_ITEM("fam_item", true),
    R_FAM_FLUID("fam_fluid", true),
    R_FAM_GAS("fam_gas", true),
    R_FAM_ENERGY("fam_energy", true),
    R_FAM_DATA("fam_data", true),
    R_FAM_PRESSURE("fam_pressure", true),
    R_FAM_HEAT("fam_heat", true);

    /** Chemin sous {@code textures/block/industry/} (ou {@code textures/block/rail/} pour le chemin de fer). */
    public final String path;
    public final boolean rail;

    BigMat(String path) {
        this(path, false);
    }

    BigMat(String path, boolean rail) {
        this.path = path;
        this.rail = rail;
    }

    /** Texture (dans l'atlas des blocs) : {@code block/industry/<path>} ou {@code block/rail/<path>}. */
    public String sprite() {
        return (rail ? "block/rail/" : "block/industry/") + path;
    }

    /** Lueur propre du materiau (lampes, fenetres de ressource). */
    public int glow() {
        if (!rail) {
            return 0;
        }
        return path.startsWith("lamp") && !path.equals("lamp_off") || path.startsWith("glow") ? 15 : path.startsWith("fam_") ? 11 : path.equals("screen") ? 7 : 0;
    }

    public boolean translucent() {
        return this == GLASS || this == LIQUID;
    }
}
