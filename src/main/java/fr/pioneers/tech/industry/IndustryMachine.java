// FICHIER GENERE par tools/gc/industry/gen_java.py depuis catalog.py : NE PAS MODIFIER A LA MAIN.
package fr.pioneers.tech.industry;

import java.util.Locale;
import org.jetbrains.annotations.Nullable;

/**
 * Machines de l'age industriel : machine d'atelier (4 tiers, {@code <tier>_<key>}) et/ou multibloc industriel (controleur
 * {@link #multiId()}). Cases et reservoirs : objets, fluides et gaz en entree et en sortie. Source des recettes, genre, chaleur par
 * operation (J/t au tier basique, x2 par tier ; negatif : la machine refroidit), couleur, gabarit du multibloc (min puis max : largeur,
 * hauteur, profondeur).
 */
public enum IndustryMachine {
    CRUSHER("crusher", Atelier.KIT, true, null, 1, 2, 0, 0, 0, 0, Source.CRUSHING, Kind.PROCESS, 60, 0xAA7846, new int[]{7, 3, 11, 7, 3, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    FURNACE("furnace", Atelier.KIT, true, null, 1, 1, 0, 0, 0, 0, Source.SMELTING, Kind.PROCESS, 120, 0xE66E28, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    COMPRESSOR("compressor", Atelier.KIT, true, null, 2, 1, 0, 0, 0, 0, Source.COMPRESSING, Kind.PROCESS, 50, 0x78AAC8, new int[]{5, 6, 3, 5, 6, 3}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.GANTRY, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    ORE_SEPARATOR("ore_separator", Atelier.NEW, true, null, 2, 4, 1, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0xBEAA5A, new int[]{7, 3, 11, 7, 3, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    DRYER("dryer", Atelier.NEW, true, null, 1, 3, 1, 1, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 80, 0xDCBE78, new int[]{7, 0, 15, 7, 0, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    ELECTRIC_CRUCIBLE("electric_crucible", Atelier.NEW, true, null, 3, 2, 0, 1, 1, 1, Source.INDUSTRIAL, Kind.PROCESS, 200, 0xFF8C1E, new int[]{5, 4, 0, 5, 4, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    CASTING_MACHINE("casting_machine", Atelier.NEW, true, null, 2, 3, 2, 1, 0, 1, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xC86E3C, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    CENTRIFUGE("centrifuge", Atelier.NEW, true, null, 1, 2, 2, 2, 2, 2, Source.INDUSTRIAL, Kind.PROCESS, 50, 0xB4C8DC, new int[]{5, 8, 0, 5, 8, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    ELECTROLYZER("electrolyzer", Atelier.NEW, true, null, 3, 2, 2, 4, 2, 4, Source.INDUSTRIAL, Kind.PROCESS, 90, 0x64C8FF, new int[]{7, 3, 11, 7, 3, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{}),
    CHEMICAL_BATH("chemical_bath", Atelier.NEW, true, null, 6, 1, 1, 1, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 90, 0xAABEC8, new int[]{7, 3, 11, 7, 3, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    MIXER("mixer", Atelier.NEW, true, null, 4, 1, 2, 1, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0x96C878, new int[]{7, 6, 0, 7, 6, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TWIN, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    CHEMICAL_REACTOR("chemical_reactor", Atelier.NEW, true, null, 2, 2, 3, 4, 3, 4, Source.INDUSTRIAL, Kind.PROCESS, 110, 0x78DC8C, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    CRYSTALLIZER("crystallizer", Atelier.NEW, true, null, 2, 1, 2, 0, 2, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xC878DC, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    PRESS("press", Atelier.NEW, true, null, 2, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0x6E82A0, new int[]{7, 7, 5, 7, 7, 5}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.GANTRY, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    ROLLING_MILL("rolling_mill", Atelier.NEW, true, null, 2, 1, 1, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0x8C96AA, new int[]{5, 4, 21, 5, 4, 21}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    WIREMILL("wiremill", Atelier.NEW, true, null, 3, 1, 1, 0, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0xD28C50, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    MACHINING_CENTER("machining_center", Atelier.NEW, true, null, 3, 2, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0x5AB4DC, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    EXTRUDER("extruder", Atelier.NEW, true, null, 1, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 70, 0xB4966E, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    BENDER("bender", Atelier.NEW, true, null, 1, 2, 0, 0, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0xA0AA78, new int[]{5, 6, 5, 5, 6, 5}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.GANTRY, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    ASSEMBLER("assembler", Atelier.NEW, true, null, 9, 2, 2, 0, 2, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0x78C8B4, new int[]{5, 4, 13, 5, 4, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    SOLID_COMBUSTION_GENERATOR("solid_combustion_generator", Atelier.NEW, true, null, 1, 0, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.GENERATOR, 160, 0xE6A03C, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    DYNAMO("dynamo", Atelier.NEW, false, null, 0, 0, 0, 0, 0, 0, Source.KINETIC, Kind.GENERATOR, 0, 0xC8AA3C, new int[]{3, 3, 3, 11, 11, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NEUTRAL, new String[]{}),
    BLAST_FURNACE("blast_furnace", Atelier.BRONZE, true, null, 3, 2, 0, 0, 1, 1, Source.INDUSTRIAL, Kind.PROCESS, 320, 0xC85028, new int[]{9, 16, 0, 9, 16, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BELLY, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    HOT_BLAST_STOVE("hot_blast_stove", Atelier.NONE, true, null, 0, 0, 0, 0, 1, 1, Source.INDUSTRIAL, Kind.PROCESS, 250, 0xB45A3C, new int[]{7, 10, 0, 7, 10, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    COKE_OVEN("coke_oven", Atelier.BRONZE, true, null, 1, 1, 0, 1, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 200, 0x785A46, new int[]{9, 7, 11, 9, 7, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    CONVERTER("converter", Atelier.NONE, true, null, 2, 2, 0, 0, 1, 1, Source.INDUSTRIAL, Kind.PROCESS, 300, 0xE67828, new int[]{7, 10, 0, 7, 10, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BELLY, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    ARC_FURNACE("arc_furnace", Atelier.NONE, true, null, 3, 2, 0, 0, 1, 1, Source.INDUSTRIAL, Kind.PROCESS, 360, 0x96C8FF, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{"casing:top:1"}),
    BAYER_DIGESTER("bayer_digester", Atelier.NONE, true, null, 1, 1, 1, 2, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 120, 0xC86446, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    KROLL_REACTOR("kroll_reactor", Atelier.NONE, true, null, 2, 2, 3, 0, 3, 0, Source.INDUSTRIAL, Kind.PROCESS, 260, 0xB4AAC8, new int[]{5, 8, 0, 5, 8, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    BOILER("boiler", Atelier.NONE, true, null, 1, 1, 2, 2, 2, 2, Source.INDUSTRIAL, Kind.PROCESS, 0, 0xC8C8D2, new int[]{7, 0, 15, 7, 0, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    STEAM_TURBINE("steam_turbine", Atelier.NONE, true, null, 0, 0, 2, 2, 2, 2, Source.INDUSTRIAL, Kind.GENERATOR, 0, 0xAABED2, new int[]{7, 0, 15, 7, 0, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    COOLING_TOWER("cooling_tower", Atelier.NONE, true, null, 0, 0, 1, 1, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, -400, 0xAAC8DC, new int[]{15, 24, 0, 15, 24, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HYPERBOLOID, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    DRILL("drill", Atelier.NONE, true, null, 0, 0, 1, 0, 0, 0, Source.DRILL, Kind.DRILL, 120, 0xDCB43C, new int[]{9, 21, 0, 9, 21, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.RIG, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    PUMPJACK("pumpjack", Atelier.NONE, true, null, 0, 0, 1, 1, 0, 2, Source.WELL, Kind.WELL, 40, 0x3C3C42, new int[]{3, 7, 11, 3, 7, 11}, false, false, new String[]{"oil", "brine", "gas"},
          fr.pioneers.tech.industry.multi.IndustryShape.JACK, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    FLUID_PUMP("fluid_pump", Atelier.NONE, true, null, 0, 0, 0, 1, 0, 0, Source.PUMP, Kind.PUMP, 20, 0x4682C8, new int[]{7, 15, 0, 7, 15, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.RIG, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    GAS_SEPARATOR("gas_separator", Atelier.NEW, true, null, 0, 0, 2, 6, 2, 6, Source.INDUSTRIAL, Kind.PROCESS, 30, 0x5AB4AA, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    CRACKER("cracker", Atelier.NONE, true, null, 0, 2, 2, 6, 2, 6, Source.INDUSTRIAL, Kind.PROCESS, 220, 0xC85A32, new int[]{7, 8, 7, 7, 8, 7}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    AUTOCLAVE("autoclave", Atelier.NEW, true, null, 2, 1, 2, 2, 2, 2, Source.INDUSTRIAL, Kind.PROCESS, 160, 0x78788C, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    FERMENTER("fermenter", Atelier.NEW, true, null, 2, 1, 1, 3, 0, 3, Source.INDUSTRIAL, Kind.PROCESS, 10, 0xAA965A, new int[]{5, 12, 0, 5, 12, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    PHOTOLITHOGRAPH("photolithograph", Atelier.NEW, true, null, 3, 2, 2, 0, 2, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xC8B4FF, new int[]{9, 4, 13, 9, 4, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{}),
    PICK_AND_PLACE("pick_and_place", Atelier.NEW, true, null, 10, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0x50C878, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{}),
    BOTTLER("bottler", Atelier.NEW, true, null, 1, 1, 1, 1, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 10, 0x6EBEE6, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    CRYOGENIC_UNIT("cryogenic_unit", Atelier.NEW, true, null, 0, 1, 2, 2, 2, 2, Source.INDUSTRIAL, Kind.PROCESS, 80, 0x96A0AA, new int[]{5, 0, 9, 5, 0, 9}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    OFFSHORE_PLATFORM("offshore_platform", Atelier.NONE, true, IndustryModule.RISER, 0, 0, 1, 1, 0, 2, Source.WELL, Kind.WELL, 120, 0xE6C83C, new int[]{3, 3, 3, 11, 11, 11}, false, true, new String[]{"oil", "gas"},
          fr.pioneers.tech.industry.multi.IndustryShape.FREEFORM, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    DISTILLATION_COLUMN("distillation_column", Atelier.NONE, true, null, 0, 1, 2, 6, 2, 2, Source.INDUSTRIAL, Kind.PROCESS, 200, 0x6EAA78, new int[]{7, 26, 0, 7, 26, 0}, true, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    SEMICONDUCTOR_FAB("semiconductor_fab", Atelier.NONE, true, null, 4, 1, 3, 0, 3, 0, Source.INDUSTRIAL, Kind.PROCESS, 120, 0xC8BEFF, new int[]{11, 5, 15, 11, 5, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{}),
    GAS_TURBINE("gas_turbine", Atelier.NEW, true, null, 0, 0, 0, 0, 2, 0, Source.INDUSTRIAL, Kind.GENERATOR, 0, 0xD28C50, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{"casing:top:1"}),
    STORAGE_TANK("storage_tank", Atelier.NONE, true, null, 0, 0, 1, 1, 0, 0, Source.TANK, Kind.TANK, 0, 0x788CA0, new int[]{7, 10, 0, 7, 10, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    GAS_HOLDER("gas_holder", Atelier.NONE, true, null, 0, 0, 0, 0, 1, 1, Source.TANK, Kind.TANK, 0, 0x96AA78, new int[]{9, 7, 0, 9, 7, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    EXTRACTOR("extractor", Atelier.NEW, true, null, 2, 2, 3, 2, 3, 2, Source.INDUSTRIAL, Kind.PROCESS, 30, 0x78AA46, new int[]{5, 0, 9, 5, 0, 9}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    SAWMILL("sawmill", Atelier.NEW, true, null, 1, 2, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0xBE8C50, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    PYROLYSIS_OVEN("pyrolysis_oven", Atelier.NONE, true, null, 1, 1, 0, 3, 0, 3, Source.INDUSTRIAL, Kind.PROCESS, 180, 0xBE5A32, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    FOOD_PROCESSOR("food_processor", Atelier.NEW, true, null, 6, 3, 1, 1, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0xE28C40, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    HYDROPONIC_FARM("hydroponic_farm", Atelier.NONE, true, null, 1, 3, 2, 2, 2, 2, Source.INDUSTRIAL, Kind.PROCESS, 0, 0x6EC85A, new int[]{11, 5, 15, 11, 5, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.LIVING, new String[]{}),
    TREE_PLANTATION("tree_plantation", Atelier.NONE, true, null, 1, 4, 1, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 0, 0x5A8C3C, new int[]{11, 7, 15, 11, 7, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.LIVING, new String[]{}),
    AQUAFARM("aquafarm", Atelier.NONE, true, null, 2, 2, 2, 0, 2, 0, Source.INDUSTRIAL, Kind.PROCESS, 0, 0x4696D2, new int[]{9, 3, 15, 9, 3, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.LIVING, new String[]{}),
    CENTRIFUGE_CASCADE("centrifuge_cascade", Atelier.NONE, true, null, 0, 0, 0, 0, 1, 2, Source.INDUSTRIAL, Kind.PROCESS, 20, 0x78DCA0, new int[]{11, 5, 15, 11, 5, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    FISSION_REACTOR("fission_reactor", Atelier.NONE, true, null, 1, 1, 1, 1, 0, 0, Source.INDUSTRIAL, Kind.REACTOR, 0, 0x5AAAFF, new int[]{9, 8, 0, 9, 8, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.PWR, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    SPENT_FUEL_POOL("spent_fuel_pool", Atelier.NONE, true, null, 1, 1, 1, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0x50A0F0, new int[]{9, 5, 13, 9, 5, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    HOT_CELL("hot_cell", Atelier.NONE, true, null, 3, 3, 2, 3, 2, 3, Source.INDUSTRIAL, Kind.PROCESS, 30, 0xF0C83C, new int[]{7, 6, 7, 7, 6, 7}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    FAST_REACTOR("fast_reactor", Atelier.NONE, true, null, 2, 2, 2, 1, 0, 0, Source.INDUSTRIAL, Kind.REACTOR, 0, 0xFF963C, new int[]{11, 8, 0, 11, 8, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.SFR, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    FRAGMENT_REACTOR("fragment_reactor", Atelier.NONE, true, null, 1, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.REACTOR, 0, 0xAA6EFF, new int[]{9, 0, 0, 9, 0, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.FFR, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    TOKAMAK("tokamak", Atelier.NONE, true, null, 1, 0, 1, 0, 2, 2, Source.INDUSTRIAL, Kind.FUSION, 0, 0xFF78DC, new int[]{11, 0, 0, 11, 0, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOK, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    STELLARATOR("stellarator", Atelier.NONE, true, null, 0, 0, 1, 0, 2, 1, Source.INDUSTRIAL, Kind.FUSION, 0, 0x78E6FF, new int[]{11, 0, 0, 11, 0, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HEL, fr.pioneers.tech.industry.multi.IndustryStyle.NUCLEAR, new String[]{}),
    DATA_CENTER("data_center", Atelier.NONE, true, IndustryModule.SERVER_RACK, 0, 0, 1, 1, 0, 0, Source.INDUSTRIAL, Kind.COMPUTE, 0, 0x50DCA0, new int[]{9, 6, 11, 9, 6, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.DIGITAL, new String[]{}),
    QUANTUM_COMPUTER("quantum_computer", Atelier.NONE, true, null, 0, 0, 1, 1, 1, 0, Source.INDUSTRIAL, Kind.QUANTUM, 0, 0xE6C878, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.DIGITAL, new String[]{"dilution_refrigerator:top:1"}),
    PROPELLANT_PLANT("propellant_plant", Atelier.NEW, true, null, 0, 0, 3, 1, 3, 0, Source.INDUSTRIAL, Kind.PROCESS, 10, 0x78BEFF, new int[]{9, 0, 0, 9, 0, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.SPHERE, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    GRAVITY_COMPRESSOR("gravity_compressor", Atelier.NONE, true, null, 2, 1, 0, 0, 3, 0, Source.INDUSTRIAL, Kind.PROCESS, 200, 0xFF8A1F, new int[]{9, 0, 0, 9, 0, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.SPHERE, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{}),
    SYNTHESIZER("synthesizer", Atelier.NONE, true, null, 1, 1, 0, 0, 3, 0, Source.INDUSTRIAL, Kind.PROCESS, 120, 0x6C7CFF, new int[]{7, 5, 0, 7, 5, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.DIGITAL, new String[]{}),
    RAIL_WORKS("rail_works", Atelier.NONE, true, null, 6, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xB07040, new int[]{11, 4, 12, 11, 4, 12}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DEPOT, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    SHIPYARD("shipyard", Atelier.NONE, true, null, 6, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0x4080B0, new int[]{9, 4, 17, 9, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    RECYCLER("recycler", Atelier.NEW, true, null, 1, 6, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 50, 0xC48440, new int[]{7, 3, 11, 7, 3, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.POOL, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    POWER_HAMMER("power_hammer", Atelier.NEW, true, null, 1, 2, 2, 0, 2, 0, Source.INDUSTRIAL, Kind.PROCESS, 160, 0xE27036, new int[]{7, 7, 5, 7, 7, 5}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.GANTRY, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    LIQUID_COMBUSTION_GENERATOR("liquid_combustion_generator", Atelier.NEW, true, null, 0, 0, 1, 0, 0, 0, Source.INDUSTRIAL, Kind.GENERATOR, 160, 0x789650, new int[]{5, 0, 13, 5, 0, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DRUM, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    DISTILLERY("distillery", Atelier.NEW, false, null, 0, 0, 2, 1, 2, 1, Source.INDUSTRIAL, Kind.PROCESS, 90, 0xB87034, new int[]{3, 3, 3, 11, 11, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NEUTRAL, new String[]{}),
    EXHAUST_STACK("exhaust_stack", Atelier.NONE, true, null, 0, 0, 0, 0, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 0, 0x966048, new int[]{3, 9, 0, 3, 9, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    ITEM_SILO("item_silo", Atelier.NONE, true, null, 1, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 0, 0xC4B078, new int[]{5, 7, 0, 5, 7, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.TOWER, fr.pioneers.tech.industry.multi.IndustryStyle.FLUID, new String[]{}),
    INDUCTION_FURNACE("induction_furnace", Atelier.NEW, false, null, 3, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 180, 0xF0963C, new int[]{3, 3, 3, 11, 11, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NEUTRAL, new String[]{}),
    WATER_SOURCE("water_source", Atelier.NEW, false, null, 0, 0, 0, 1, 0, 0, Source.WATER, Kind.WATER, 0, 0x4696E1, new int[]{3, 3, 3, 11, 11, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NEUTRAL, new String[]{}),
    ROCK_GENERATOR("rock_generator", Atelier.NEW, false, null, 0, 1, 2, 0, 0, 0, Source.ROCKS, Kind.ROCKS, 0, 0xCD642D, new int[]{3, 3, 3, 11, 11, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NEUTRAL, new String[]{}),
    REDUCTION_FURNACE("reduction_furnace", Atelier.NEW, true, null, 3, 2, 0, 2, 1, 2, Source.INDUSTRIAL, Kind.PROCESS, 200, 0xD25A28, new int[]{7, 12, 0, 7, 12, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BELLY, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    GEAR_HOBBER("gear_hobber", Atelier.NEW, true, null, 1, 2, 1, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0xD6A840, new int[]{5, 4, 17, 5, 4, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    JOINERY("joinery", Atelier.NEW, true, null, 6, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 40, 0xB27A42, new int[]{5, 4, 13, 5, 4, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    TAILORING_WORKSHOP("tailoring_workshop", Atelier.NEW, true, null, 4, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0xBE5484, new int[]{5, 4, 13, 5, 4, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    PACKAGING_MACHINE("packaging_machine", Atelier.NEW, true, null, 2, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0x76AC46, new int[]{5, 4, 13, 5, 4, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.LIVING, new String[]{}),
    SHEET_METAL_SHOP("sheet_metal_shop", Atelier.NEW, true, null, 4, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xCE7646, new int[]{7, 7, 5, 7, 7, 5}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.GANTRY, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    GLASSWORKS("glassworks", Atelier.NEW, true, null, 4, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 120, 0x68B8CC, new int[]{3, 2, 0, 9, 10, 0}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.DOME, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{"casing:top:1"}),
    ELECTROMECHANICAL_BENCH("electromechanical_bench", Atelier.NEW, true, null, 4, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 30, 0xD64238, new int[]{5, 4, 13, 5, 4, 13}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.LINE, fr.pioneers.tech.industry.multi.IndustryStyle.ELECTRIC, new String[]{}),
    STONEWORKS("stoneworks", Atelier.NEW, true, null, 3, 1, 0, 0, 0, 0, Source.STONECUTTING, Kind.PROCESS, 60, 0x9C9892, new int[]{7, 7, 5, 7, 7, 5}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.GANTRY, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    DRIVE_MOTOR("drive_motor", Atelier.NEW, false, null, 0, 0, 0, 0, 0, 0, Source.KINETIC, Kind.GENERATOR, 0, 0x468CD7, new int[]{3, 3, 3, 11, 11, 11}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.BOX, fr.pioneers.tech.industry.multi.IndustryStyle.NEUTRAL, new String[]{}),
    MARS_ELEVATOR_PORT("mars_elevator_port", Atelier.NONE, true, null, 4, 1, 0, 0, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 0, 0xC8603A, new int[]{15, 5, 15, 15, 5, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{}),
    MERCURY_DIADEM_PORT("mercury_diadem_port", Atelier.NONE, true, null, 4, 1, 0, 0, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 0, 0xDEA848, new int[]{15, 5, 15, 15, 5, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    SOLAR_CONCENTRATOR("solar_concentrator", Atelier.NONE, true, null, 3, 1, 0, 0, 0, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xFFBE46, new int[]{15, 6, 17, 15, 6, 17}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.THERMAL, new String[]{}),
    ISOSTATIC_PRESS("isostatic_press", Atelier.NONE, true, null, 2, 1, 0, 0, 1, 0, Source.INDUSTRIAL, Kind.PROCESS, 60, 0xBE543C, new int[]{15, 5, 15, 15, 5, 15}, false, false, new String[]{},
          fr.pioneers.tech.industry.multi.IndustryShape.HALL, fr.pioneers.tech.industry.multi.IndustryStyle.MECHANICAL, new String[]{});

    /** Ou est la machine d'atelier : nouvelle (generee), kit existant, machine d'avant l'electricite, ou aucune (multibloc seulement). */
    public enum Atelier { NEW, KIT, BRONZE, NONE }

    /** Recettes lues par la machine (1.0.5-beta lot 46 : WATER, ROCKS = aucune recette, production calculee : GeneratedLot46 ; 1.0.6 :
     *  STONECUTTING = recettes du tailleur de pierre converties, Taillerie de pierre). */
    public enum Source { INDUSTRIAL, CRUSHING, COMPRESSING, SMELTING, FUEL, KINETIC, DRILL, WELL, PUMP, TANK, WATER, ROCKS, STONECUTTING }

    /**
     * Genre : process (recettes), generateur, foreuse (gisements solides), puits (gisements fluides), pompe (fluides du monde), reservoir ;
     * 1.0.5-beta (lot 46, retour 72) : WATER (bloc a eau : eau sans entree), ROCKS (generateur de roches : echantillon, eau et lave jamais
     * prelevees).
     */
    public enum Kind { PROCESS, GENERATOR, DRILL, WELL, PUMP, TANK, REACTOR, FUSION, COMPUTE, QUANTUM, WATER, ROCKS }

    public final String key;
    public final Atelier atelier;
    public final boolean industrial;
    @Nullable
    public final IndustryModule module;
    public final int itemsIn, itemsOut, fluidsIn, fluidsOut, gasesIn, gasesOut;
    public final Source source;
    public final Kind kind;
    public final int heat, accent;
    public final int minW, minH, minD, maxW, maxH, maxD;
    /** Colonne : chaque fluide produit sort par l'etage de ports de sa hauteur (le premier en haut). */
    public final boolean fractions;
    /** Multibloc sans forme imposee (pieces reliees). */
    public final boolean freeform;
    /** Puits : categories de gisements fluides exploitees (oil, gas, brine). */
    public final java.util.List<String> wells;
    /** Gabarit du multibloc (0.16.1) et son habillage. */
    public final fr.pioneers.tech.industry.multi.IndustryShape shape;
    public final fr.pioneers.tech.industry.multi.IndustryStyle style;
    /** Pieces signature exigees par le gabarit. */
    public final java.util.List<fr.pioneers.tech.industry.multi.Signature> signatures;
    /**
     * 1.0.4-beta (lot 15) : une carcasse industrielle est rapportee au-dessus du sommet (la place de l'ancienne cheminee, {@code "casing:top:1"}) :
     * le gabarit garde sa taille, la case compte comme enveloppe (carcasse du tier, port ou verre).
     */
    public final boolean summitCasing;

    IndustryMachine(String key, Atelier atelier, boolean industrial, @Nullable IndustryModule module, int itemsIn, int itemsOut, int fluidsIn,
          int fluidsOut, int gasesIn, int gasesOut, Source source, Kind kind, int heat, int accent, int[] shape, boolean fractions, boolean freeform,
          String[] wells, fr.pioneers.tech.industry.multi.IndustryShape shapeKind, fr.pioneers.tech.industry.multi.IndustryStyle style, String[] parts) {
        this.shape = shapeKind;
        this.style = style;
        this.signatures = fr.pioneers.tech.industry.multi.Signature.parse(parts);
        this.summitCasing = java.util.Arrays.asList(parts).contains("casing:top:1");
        this.fractions = fractions;
        this.freeform = freeform;
        this.wells = java.util.List.of(wells);
        this.key = key;
        this.atelier = atelier;
        this.industrial = industrial;
        this.module = module;
        this.itemsIn = itemsIn;
        this.itemsOut = itemsOut;
        this.fluidsIn = fluidsIn;
        this.fluidsOut = fluidsOut;
        this.gasesIn = gasesIn;
        this.gasesOut = gasesOut;
        this.source = source;
        this.kind = kind;
        this.heat = heat;
        this.accent = accent;
        minW = shape[0];
        minH = shape[1];
        minD = shape[2];
        maxW = shape[3];
        maxH = shape[4];
        maxD = shape[5];
    }

    /**
     * 1.0.3-beta (lot 5) : controleurs qui gardent un id hors de la regle (multiblocs passes « multibloc seul » : leur id {@code industrial_<cle>} ;
     * turbine a gaz devenue compacte : {@code gas_turbine}).
     */
    private static final java.util.Map<String, String> MULTI_IDS = java.util.Map.ofEntries(
          java.util.Map.entry("pumpjack", "industrial_pumpjack"),
          java.util.Map.entry("fluid_pump", "industrial_fluid_pump"),
          java.util.Map.entry("cracker", "industrial_cracker"),
          java.util.Map.entry("gas_turbine", "gas_turbine"),
          java.util.Map.entry("pyrolysis_oven", "industrial_pyrolysis_oven"),
          java.util.Map.entry("hot_cell", "industrial_hot_cell"));

    /** Id du controleur du multibloc. */
    public String multiId() {
        String fixed = MULTI_IDS.get(key);
        if (fixed != null) {
            return fixed;
        }
        return atelier == Atelier.NONE ? key : "industrial_" + key;
    }

    /** Id de la machine d'atelier d'un tier (kit : ses propres ids). */
    public String atelierId(fr.pioneers.logistics.transport.CableTier tier) {
        return tier.id() + "_" + key;
    }

    public boolean isGenerator() {
        return kind == Kind.GENERATOR;
    }

    @Nullable
    public static IndustryMachine byKey(String key) {
        for (IndustryMachine m : values()) {
            if (m.key.equals(key)) {
                return m;
            }
        }
        return null;
    }

    public String translationKey() {
        return "industry.pioneers.machine." + name().toLowerCase(Locale.ROOT);
    }

    /** 1.0 : contenants en toles (cuves, reservoirs, bacs) : paroi en toles au-dessus des deux premieres rangees de carcasses. */
    private static final java.util.Set<String> SHEET = java.util.Set.of("crusher", "ore_separator", "electrolyzer", "chemical_bath", "mixer", "chemical_reactor", "bayer_digester", "cooling_tower", "autoclave", "fermenter", "storage_tank", "gas_holder", "food_processor", "fission_reactor", "fast_reactor", "item_silo");

    public boolean sheet() {
        return SHEET.contains(key);
    }

    /**
     * MAJ Recettes (lot 9a) : version des cases et reservoirs ecrite dans le NBT des machines ({@code ioVersion}) ; une machine sauvee sans
     * cette marque vient d'un monde d'avant la MAJ et ses cases sont remises en place par {@link IoMigration}.
     */
    public static final int IO_VERSION = 12;

    /**
     * Anciennes io (objets, fluides, gaz : entree puis sortie) des machines dont l'io a change, la plus ancienne d'abord : {version des cases
     * jusqu'a laquelle (exclue) cette io vaut, io...} (tools/gc/industry/io_legacy.py versions : 1 = io de la 1.9.2, 2 = d'avant les
     * generateurs separes de la 1.0.3-beta lot 5, 3 = d'avant la fusion des machines de la 1.0.4-beta lot 11).
     */
    private static final java.util.Map<String, int[][]> LEGACY_IO = java.util.Map.ofEntries(
          java.util.Map.entry("crusher", new int[][]{{3, 1, 2, 0, 0, 0, 0}, {9, 1, 2, 1, 0, 0, 0}}),
          java.util.Map.entry("furnace", new int[][]{{3, 1, 1, 0, 0, 0, 0}, {9, 3, 2, 1, 1, 1, 1}}),
          java.util.Map.entry("compressor", new int[][]{{1, 1, 1, 0, 0, 0, 0}}),
          java.util.Map.entry("ore_separator", new int[][]{{3, 1, 3, 0, 0, 0, 0}}),
          java.util.Map.entry("electric_crucible", new int[][]{{1, 1, 0, 0, 1, 0, 0}, {3, 3, 2, 0, 1, 1, 1}, {8, 4, 2, 0, 1, 1, 1}}),
          java.util.Map.entry("casting_machine", new int[][]{{1, 1, 2, 1, 0, 0, 0}, {6, 1, 3, 2, 1, 0, 1}}),
          java.util.Map.entry("centrifuge", new int[][]{{5, 1, 2, 1, 1, 1, 2}, {10, 1, 2, 2, 3, 1, 2}}),
          java.util.Map.entry("electrolyzer", new int[][]{{1, 2, 2, 1, 0, 0, 2}, {4, 2, 2, 1, 1, 0, 2}, {10, 3, 2, 1, 2, 2, 2}}),
          java.util.Map.entry("chemical_bath", new int[][]{{1, 2, 1, 0, 0, 0, 0}, {6, 3, 2, 1, 0, 0, 0}, {10, 6, 2, 1, 1, 1, 0}}),
          java.util.Map.entry("chemical_reactor", new int[][]{{4, 2, 2, 3, 2, 2, 1}, {10, 2, 3, 3, 3, 3, 2}}),
          java.util.Map.entry("crystallizer", new int[][]{{10, 2, 1, 1, 0, 1, 0}}),
          java.util.Map.entry("press", new int[][]{{10, 2, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("rolling_mill", new int[][]{{1, 1, 1, 0, 0, 0, 0}, {6, 1, 1, 1, 0, 0, 0}, {10, 2, 2, 1, 0, 0, 0}}),
          java.util.Map.entry("wiremill", new int[][]{{6, 1, 1, 0, 0, 0, 0}}),
          java.util.Map.entry("machining_center", new int[][]{{6, 1, 2, 0, 0, 0, 0}, {9, 3, 2, 1, 0, 0, 0}}),
          java.util.Map.entry("extruder", new int[][]{{10, 2, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("bender", new int[][]{{6, 1, 1, 0, 0, 0, 0}, {10, 1, 1, 1, 0, 0, 0}}),
          java.util.Map.entry("assembler", new int[][]{{6, 9, 1, 1, 0, 0, 0}, {7, 9, 2, 1, 0, 1, 0}, {10, 9, 2, 2, 0, 1, 0}}),
          java.util.Map.entry("solid_combustion_generator", new int[][]{{2, 1, 0, 1, 0, 1, 0}}),
          java.util.Map.entry("hot_blast_stove", new int[][]{{10, 0, 0, 0, 0, 2, 1}}),
          java.util.Map.entry("coke_oven", new int[][]{{3, 1, 1, 0, 1, 0, 1}, {10, 1, 2, 0, 2, 0, 1}}),
          java.util.Map.entry("converter", new int[][]{{1, 2, 2, 0, 0, 1, 1}, {10, 2, 3, 0, 0, 1, 1}}),
          java.util.Map.entry("kroll_reactor", new int[][]{{1, 1, 2, 0, 0, 2, 0}, {10, 2, 2, 1, 0, 2, 0}}),
          java.util.Map.entry("boiler", new int[][]{{10, 1, 1, 2, 1, 1, 1}}),
          java.util.Map.entry("steam_turbine", new int[][]{{1, 0, 0, 0, 0, 1, 1}, {10, 0, 0, 1, 1, 1, 1}}),
          java.util.Map.entry("pumpjack", new int[][]{{5, 0, 0, 1, 1, 0, 1}}),
          java.util.Map.entry("gas_separator", new int[][]{{5, 0, 0, 0, 0, 1, 2}, {10, 1, 1, 1, 3, 1, 3}}),
          java.util.Map.entry("cracker", new int[][]{{4, 1, 1, 1, 1, 1, 1}, {10, 1, 3, 1, 3, 2, 4}}),
          java.util.Map.entry("autoclave", new int[][]{{10, 2, 1, 1, 1, 1, 1}}),
          java.util.Map.entry("fermenter", new int[][]{{10, 2, 1, 1, 2, 0, 1}}),
          java.util.Map.entry("photolithograph", new int[][]{{1, 3, 2, 1, 0, 0, 0}, {10, 3, 2, 1, 0, 1, 0}, {11, 3, 2, 1, 0, 0, 0}}),
          java.util.Map.entry("pick_and_place", new int[][]{{10, 12, 1, 0, 0, 0, 0}}),
          java.util.Map.entry("bottler", new int[][]{{10, 1, 1, 1, 1, 1, 0}}),
          java.util.Map.entry("cryogenic_unit", new int[][]{{5, 0, 0, 0, 1, 1, 1}, {10, 0, 1, 2, 1, 1, 1}}),
          java.util.Map.entry("distillation_column", new int[][]{{4, 0, 1, 1, 6, 0, 1}, {10, 0, 1, 1, 6, 1, 2}}),
          java.util.Map.entry("semiconductor_fab", new int[][]{{10, 4, 2, 2, 0, 2, 0}}),
          java.util.Map.entry("gas_turbine", new int[][]{{2, 0, 0, 1, 0, 2, 0}}),
          java.util.Map.entry("extractor", new int[][]{{1, 1, 2, 0, 0, 0, 0}, {5, 2, 2, 1, 2, 1, 1}, {10, 2, 2, 2, 2, 1, 1}}),
          java.util.Map.entry("sawmill", new int[][]{{6, 1, 2, 0, 0, 0, 0}, {9, 1, 2, 1, 0, 0, 0}}),
          java.util.Map.entry("pyrolysis_oven", new int[][]{{10, 1, 2, 0, 2, 0, 1}}),
          java.util.Map.entry("hydroponic_farm", new int[][]{{5, 2, 3, 1, 0, 1, 0}, {10, 2, 4, 1, 1, 1, 1}}),
          java.util.Map.entry("tree_plantation", new int[][]{{10, 2, 4, 1, 0, 0, 0}}),
          java.util.Map.entry("aquafarm", new int[][]{{10, 2, 3, 1, 0, 1, 0}}),
          java.util.Map.entry("hot_cell", new int[][]{{5, 2, 3, 1, 1, 1, 2}, {10, 3, 3, 2, 3, 1, 2}}),
          java.util.Map.entry("propellant_plant", new int[][]{{10, 0, 0, 2, 1, 2, 0}}),
          java.util.Map.entry("power_hammer", new int[][]{{6, 2, 2, 0, 0, 0, 0}, {10, 2, 2, 1, 0, 1, 0}}),
          java.util.Map.entry("distillery", new int[][]{{10, 0, 0, 1, 1, 1, 1}}),
          java.util.Map.entry("induction_furnace", new int[][]{{3, 4, 1, 0, 0, 1, 0}}),
          java.util.Map.entry("reduction_furnace", new int[][]{{3, 3, 2, 0, 0, 1, 1}, {10, 3, 2, 0, 1, 1, 1}}),
          java.util.Map.entry("gear_hobber", new int[][]{{6, 2, 2, 1, 0, 0, 0}}),
          java.util.Map.entry("joinery", new int[][]{{10, 6, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("tailoring_workshop", new int[][]{{10, 6, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("packaging_machine", new int[][]{{10, 2, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("sheet_metal_shop", new int[][]{{10, 6, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("glassworks", new int[][]{{10, 4, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("electromechanical_bench", new int[][]{{10, 6, 2, 0, 0, 0, 0}}),
          java.util.Map.entry("stoneworks", new int[][]{{10, 4, 2, 0, 0, 0, 0}}));

    /** io de la machine dans un NBT de cette version des cases ; null si c'est l'io actuelle. */
    @Nullable
    public int[] legacyIo(int nbtVersion) {
        int[][] chain = LEGACY_IO.get(key);
        if (chain != null) {
            for (int[] row : chain) {
                if (nbtVersion < row[0]) {
                    return java.util.Arrays.copyOfRange(row, 1, 7);
                }
            }
        }
        return null;
    }

    /** io de la machine juste avant la version actuelle des cases (la plus recente des anciennes) ; null si elle n'a pas change. */
    @Nullable
    public int[] legacyIo() {
        int[][] chain = LEGACY_IO.get(key);
        return chain == null ? null : java.util.Arrays.copyOfRange(chain[chain.length - 1], 1, 7);
    }

    /** Un NBT de version des cases inferieure a celle-ci a encore une ancienne io ({@link #legacyIo(int)}) ; 0 : rien a migrer. */
    public int legacyIoVersion() {
        int[][] chain = LEGACY_IO.get(key);
        return chain == null ? 0 : chain[chain.length - 1][0];
    }

    /** MAJ Recettes (lot 9a) : etape d'une voie de metallurgie (recettes processes/...) : action d'XP du proprietaire. */
    public static final String ROUTE_XP = "smithing.process";

    /** Action d'XP des machines nouvelles et refondues de la MAJ (tools/gc/industry/machine_skills.py). */
    private static final java.util.Map<String, String> XP_ACTION = java.util.Map.ofEntries(
          java.util.Map.entry("casting_machine", "smithing.cast"),
          java.util.Map.entry("chemical_bath", "smithing.treat"),
          java.util.Map.entry("cnc_mill", "engineering.machining"),
          java.util.Map.entry("cold_header", "smithing.shape"),
          java.util.Map.entry("component_assembler", "engineering.machining"),
          java.util.Map.entry("compressor", "smithing.shape"),
          java.util.Map.entry("continuous_caster", "smithing.cast"),
          java.util.Map.entry("extractor", "farming.extract"),
          java.util.Map.entry("fiber_tower", "building.glass"),
          java.util.Map.entry("forming_station", "smithing.shape"),
          java.util.Map.entry("gear_hobber", "engineering.machining"),
          java.util.Map.entry("grinder", "engineering.machining"),
          java.util.Map.entry("logistics_assembler", "engineering.machining"),
          java.util.Map.entry("photolithograph", "engineering.machining"),
          java.util.Map.entry("plasma_sprayer", "engineering.machining"),
          java.util.Map.entry("power_hammer", "smithing.shape"),
          java.util.Map.entry("quench_tank", "smithing.treat"),
          java.util.Map.entry("spring_coiler", "smithing.shape"),
          java.util.Map.entry("tank_furnace", "building.glass"),
          java.util.Map.entry("wire_strander", "smithing.shape"));

    /** Action d'XP (xp_sources.json) apprise par le proprietaire quand la machine finit une recette ; null : regle generale. */
    @Nullable
    public String xpAction() {
        return XP_ACTION.get(key);
    }

    /** 1.0.4-beta (lot 11) : action d'XP d'une cle de machine, absorbee comprise (famille d'une recette recue) ; null : aucune. */
    @Nullable
    public static String xpActionOf(String key) {
        return XP_ACTION.get(key);
    }
}
