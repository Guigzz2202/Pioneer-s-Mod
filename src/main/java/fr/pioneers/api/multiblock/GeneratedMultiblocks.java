package fr.pioneers.api.multiblock;

import fr.pioneers.api.multiblock.MultiblockSpec.Shape;
import fr.pioneers.api.multiblock.MultiblockSpec.TierRule;
import java.util.ArrayList;
import java.util.List;

/**
 * GENERE par tools/gc/api/gen_api.py d'apres les fiches tools/gc/api/multiblocks.py (catalogue industriel par adaptateur, multiblocs
 * maison) : NE PAS MODIFIER A LA MAIN. Une fiche par multibloc (API Multibloc, lot 5) : 94 fiches, 94 sur le moteur commun.
 */
public final class GeneratedMultiblocks {

    /** Toutes les fiches, dans l'ordre des declarations (catalogue industriel d'abord). */
    public static final List<MultiblockSpec> ALL;

    static {
        List<MultiblockSpec> l = new ArrayList<>(94);
        part0(l);
        part1(l);
        part2(l);
        part3(l);
        ALL = List.copyOf(l);
    }

    private GeneratedMultiblocks() {
    }

    private static MultiblockSpec.RoleSpec role(String name, String tag, int min) {
        return new MultiblockSpec.RoleSpec(name, tag, min);
    }

    private static void part0(List<MultiblockSpec> l) {
        l.add(new MultiblockSpec("industrial_crusher", "industrial_crusher", List.of("industrial_crusher"), "crusher", Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_furnace", "industrial_furnace", List.of("industrial_furnace"), "furnace", Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_compressor", "industrial_compressor", List.of("industrial_compressor"), "compressor", Shape.TEMPLATE, "gantry",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_ore_separator", "industrial_ore_separator", List.of("industrial_ore_separator"), "ore_separator", Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_dryer", "industrial_dryer", List.of("industrial_dryer"), "dryer", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_electric_crucible", "industrial_electric_crucible", List.of("industrial_electric_crucible"), "electric_crucible", Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_casting_machine", "industrial_casting_machine", List.of("industrial_casting_machine"), "casting_machine", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_centrifuge", "industrial_centrifuge", List.of("industrial_centrifuge"), "centrifuge", Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_electrolyzer", "industrial_electrolyzer", List.of("industrial_electrolyzer"), "electrolyzer", Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "ELECTRIC", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_chemical_bath", "industrial_chemical_bath", List.of("industrial_chemical_bath"), "chemical_bath", Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_mixer", "industrial_mixer", List.of("industrial_mixer"), "mixer", Shape.TEMPLATE, "twin",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_chemical_reactor", "industrial_chemical_reactor", List.of("industrial_chemical_reactor"), "chemical_reactor", Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_crystallizer", "industrial_crystallizer", List.of("industrial_crystallizer"), "crystallizer", Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_press", "industrial_press", List.of("industrial_press"), "press", Shape.TEMPLATE, "gantry",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_rolling_mill", "industrial_rolling_mill", List.of("industrial_rolling_mill"), "rolling_mill", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_wiremill", "industrial_wiremill", List.of("industrial_wiremill"), "wiremill", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_machining_center", "industrial_machining_center", List.of("industrial_machining_center"), "machining_center", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_extruder", "industrial_extruder", List.of("industrial_extruder"), "extruder", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_bender", "industrial_bender", List.of("industrial_bender"), "bender", Shape.TEMPLATE, "gantry",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_assembler", "industrial_assembler", List.of("industrial_assembler"), "assembler", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_solid_combustion_generator", "industrial_solid_combustion_generator", List.of("industrial_solid_combustion_generator"), "solid_combustion_generator", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "generator", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_blast_furnace", "industrial_blast_furnace", List.of("industrial_blast_furnace"), "blast_furnace", Shape.TEMPLATE, "belly",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("hot_blast_stove", "hot_blast_stove", List.of("hot_blast_stove"), null, Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_coke_oven", "industrial_coke_oven", List.of("industrial_coke_oven"), "coke_oven", Shape.TEMPLATE, "box",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("converter", "converter", List.of("converter"), null, Shape.TEMPLATE, "belly",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("arc_furnace", "arc_furnace", List.of("arc_furnace"), null, Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "ELECTRIC", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("bayer_digester", "bayer_digester", List.of("bayer_digester"), null, Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("kroll_reactor", "kroll_reactor", List.of("kroll_reactor"), null, Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("boiler", "boiler", List.of("boiler"), null, Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("steam_turbine", "steam_turbine", List.of("steam_turbine"), null, Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "generator", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
    }

    private static void part1(List<MultiblockSpec> l) {
        l.add(new MultiblockSpec("cooling_tower", "cooling_tower", List.of("cooling_tower"), null, Shape.TEMPLATE, "hyperboloid",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("concrete", "pioneers:multiblock/concrete", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("drill", "drill", List.of("drill"), null, Shape.TEMPLATE, "rig",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "drill", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_pumpjack", "industrial_pumpjack", List.of("industrial_pumpjack"), null, Shape.TEMPLATE, "jack",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "well", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_fluid_pump", "industrial_fluid_pump", List.of("industrial_fluid_pump"), null, Shape.TEMPLATE, "rig",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "pump", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_gas_separator", "industrial_gas_separator", List.of("industrial_gas_separator"), "gas_separator", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_cracker", "industrial_cracker", List.of("industrial_cracker"), "cracker", Shape.TEMPLATE, "box",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_autoclave", "industrial_autoclave", List.of("industrial_autoclave"), "autoclave", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_fermenter", "industrial_fermenter", List.of("industrial_fermenter"), "fermenter", Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_photolithograph", "industrial_photolithograph", List.of("industrial_photolithograph"), "photolithograph", Shape.TEMPLATE, "hall",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "ELECTRIC", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_pick_and_place", "industrial_pick_and_place", List.of("industrial_pick_and_place"), "pick_and_place", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "ELECTRIC", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_bottler", "industrial_bottler", List.of("industrial_bottler"), "bottler", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_cryogenic_unit", "industrial_cryogenic_unit", List.of("industrial_cryogenic_unit"), "cryogenic_unit", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("offshore_platform", "offshore_platform", List.of("offshore_platform"), null, Shape.FREEFORM, "freeform",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("module", "pioneers:multiblock/module/riser_module", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "offshore", true, "MECHANICAL", null, "offshore", "industry.multi.OffshoreRisers", true, 6));
        l.add(new MultiblockSpec("distillation_column", "distillation_column", List.of("distillation_column"), null, Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("semiconductor_fab", "semiconductor_fab", List.of("semiconductor_fab"), null, Shape.TEMPLATE, "hall",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "ELECTRIC", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("gas_turbine", "gas_turbine", List.of("gas_turbine"), "compact_gas_turbine", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "generator", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("storage_tank", "storage_tank", List.of("storage_tank"), null, Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "storage", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("gas_holder", "gas_holder", List.of("gas_holder"), null, Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "storage", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_extractor", "industrial_extractor", List.of("industrial_extractor"), "extractor", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_sawmill", "industrial_sawmill", List.of("industrial_sawmill"), "sawmill", Shape.TEMPLATE, "line",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_pyrolysis_oven", "industrial_pyrolysis_oven", List.of("industrial_pyrolysis_oven"), null, Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_food_processor", "industrial_food_processor", List.of("industrial_food_processor"), "food_processor", Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "FLUID", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("hydroponic_farm", "hydroponic_farm", List.of("hydroponic_farm"), null, Shape.TEMPLATE, "hall",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "LIVING", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("tree_plantation", "tree_plantation", List.of("tree_plantation"), null, Shape.TEMPLATE, "hall",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "LIVING", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("aquafarm", "aquafarm", List.of("aquafarm"), null, Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "LIVING", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("centrifuge_cascade", "centrifuge_cascade", List.of("centrifuge_cascade"), null, Shape.TEMPLATE, "hall",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "NUCLEAR", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("fission_reactor", "fission_reactor", List.of("fission_reactor"), null, Shape.TEMPLATE, "pwr",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("reactor", "pioneers:multiblock/reactor", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "reactor", true, "NUCLEAR", null, "reactor", "nuclear.FissionCore", true, 6));
        l.add(new MultiblockSpec("spent_fuel_pool", "spent_fuel_pool", List.of("spent_fuel_pool"), null, Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "NUCLEAR", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_hot_cell", "industrial_hot_cell", List.of("industrial_hot_cell"), null, Shape.TEMPLATE, "box",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "NUCLEAR", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("fast_reactor", "fast_reactor", List.of("fast_reactor"), null, Shape.TEMPLATE, "sfr",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("reactor", "pioneers:multiblock/reactor", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "reactor", true, "NUCLEAR", null, "reactor", "nuclear.FissionCore", true, 6));
    }

    private static void part2(List<MultiblockSpec> l) {
        l.add(new MultiblockSpec("fragment_reactor", "fragment_reactor", List.of("fragment_reactor"), null, Shape.TEMPLATE, "ffr",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("reactor", "pioneers:multiblock/reactor", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "reactor", true, "NUCLEAR", null, "reactor", "nuclear.FissionCore", true, 6));
        l.add(new MultiblockSpec("tokamak", "tokamak", List.of("tokamak"), null, Shape.TEMPLATE, "tok",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("reactor", "pioneers:multiblock/reactor", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "fusion", true, "NUCLEAR", null, "fusion", "fusion.FusionCore", true, 6));
        l.add(new MultiblockSpec("stellarator", "stellarator", List.of("stellarator"), null, Shape.TEMPLATE, "hel",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("reactor", "pioneers:multiblock/reactor", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "fusion", true, "NUCLEAR", null, "fusion", "fusion.FusionCore", true, 6));
        l.add(new MultiblockSpec("data_center", "data_center", List.of("data_center"), null, Shape.TEMPLATE, "box",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0), role("module", "pioneers:multiblock/module/server_rack_module", 1)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "compute", true, "DIGITAL", null, "compute", "compute.ComputeCore", true, 6));
        l.add(new MultiblockSpec("quantum_computer", "quantum_computer", List.of("quantum_computer"), null, Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 1), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "compute", true, "DIGITAL", null, "compute", "compute.ComputeCore", true, 6));
        l.add(new MultiblockSpec("industrial_propellant_plant", "industrial_propellant_plant", List.of("industrial_propellant_plant"), "propellant_plant", Shape.TEMPLATE, "sphere",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("gravity_compressor", "gravity_compressor", List.of("gravity_compressor"), null, Shape.TEMPLATE, "sphere",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "ELECTRIC", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("synthesizer", "synthesizer", List.of("synthesizer"), null, Shape.TEMPLATE, "dome",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "DIGITAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("rail_works", "rail_works", List.of("rail_works"), null, Shape.TEMPLATE, "depot",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("shipyard", "shipyard", List.of("shipyard"), null, Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_recycler", "industrial_recycler", List.of("industrial_recycler"), "recycler", Shape.TEMPLATE, "pool",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_power_hammer", "industrial_power_hammer", List.of("industrial_power_hammer"), "power_hammer", Shape.TEMPLATE, "gantry",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "MECHANICAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("industrial_liquid_combustion_generator", "industrial_liquid_combustion_generator", List.of("industrial_liquid_combustion_generator"), "liquid_combustion_generator", Shape.TEMPLATE, "drum",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "generator", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("exhaust_stack", "exhaust_stack", List.of("exhaust_stack"), null, Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "batch", true, "THERMAL", null, "controller", "industry.multi.ControllerTile", true, 5));
        l.add(new MultiblockSpec("item_silo", "item_silo", List.of("item_silo"), null, Shape.TEMPLATE, "tower",
              List.of(role("controller", "pioneers:multiblock/controller", 1), role("casing", "pioneers:multiblock/casing", 1), role("glass", "pioneers:multiblock/glass", 0), role("port", "pioneers:multiblock/port", 0), role("part", "pioneers:multiblock/part", 0), role("frame", "pioneers:multiblock/frame", 0), role("sheet", "pioneers:multiblock/sheet", 0)),
              TierRule.WEAKEST, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "silo", true, "FLUID", null, "controller", "industry.multi.SiloCore", true, 6));
        l.add(new MultiblockSpec("space_stabilizer_controller", "space_stabilizer_controller", List.of("space_stabilizer_controller"), null, Shape.PARAMETRIC, "stabilizer_chambers",
              List.of(role("wall", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "cosmic", "content.stabilizer.StabilizerControllerTile", true, 8));
        l.add(new MultiblockSpec("space_simulator_controller", "space_simulator_controller", List.of("space_simulator_controller"), null, Shape.FIXED, "SpaceSimShape",
              List.of(role("wall", null, 0), role("port", null, 0), role("core", null, 0)),
              TierRule.CORE, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "cosmic", "content.spacesim.SpaceSimControllerTile", true, 8));
        l.add(new MultiblockSpec("solar_absorber_controller", "solar_absorber_controller", List.of("solar_absorber_controller"), null, Shape.PARAMETRIC, "absorber_sizes",
              List.of(role("wall", null, 0), role("port", null, 0), role("panel", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "cosmic", "content.absorber.AbsorberControllerTile", true, 8));
        l.add(new MultiblockSpec("greenhouse_controller", "greenhouse_controller", List.of("greenhouse_controller"), null, Shape.FIXED, "BaseLayout",
              List.of(role("wall", null, 0), role("glass", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "base", "tile.TileEntityGreenhouse", true, 8));
        l.add(new MultiblockSpec("mining_outpost_controller", "mining_outpost_controller", List.of("mining_outpost_controller"), null, Shape.FIXED, "BaseLayout",
              List.of(role("wall", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "base", "tile.TileEntityMiningOutpost", true, 8));
        l.add(new MultiblockSpec("drone_bay", "drone_bay", List.of("drone_bay"), null, Shape.FIXED, "BaseLayout",
              List.of(role("wall", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "base", "tile.TileEntityDroneBay", true, 8));
        l.add(new MultiblockSpec("launch_pad_core", "launch_pad_core", List.of("launch_pad_core"), null, Shape.PARAMETRIC, "launch_complex",
              List.of(role("pad", null, 0), role("tower", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "base", "tile.TileEntityLaunchPad", true, 8));
        l.add(new MultiblockSpec("rocket_integration_hall", "rocket_integration_hall", List.of("rocket_integration_hall"), null, Shape.FIXED, "AssemblyHallLayout",
              List.of(role("wall", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "works", "tile.TileEntityAssemblyHall", true, 8));
        l.add(new MultiblockSpec("robotic_assembly_bay", "robotic_assembly_bay", List.of("robotic_assembly_bay"), null, Shape.FIXED, "AssemblyHallLayout",
              List.of(role("wall", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "works", "tile.TileEntityAssemblyHall", true, 8));
        l.add(new MultiblockSpec("harbor", "harbor", List.of("basic_harbor", "advanced_harbor", "elite_harbor", "ultimate_harbor"), null, Shape.FIXED, "HarborLayout",
              List.of(role("quay", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "harbor", "naval.HarborTile", true, 8));
        l.add(new MultiblockSpec("firebox", "firebox", List.of("firebox"), null, Shape.FIXED, "HouseNbt",
              List.of(role("firebox", null, 0), role("body", null, 0), role("dome", null, 0), role("chimney", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "steam", "bronze.steam.FireboxTile", true, 8));
        l.add(new MultiblockSpec("blast_furnace", "blast_furnace", List.of("blast_furnace"), null, Shape.FIXED, "BlastLayout",
              List.of(role("brick", null, 0), role("firebox", null, 0), role("tuyere", null, 0), role("bellows", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "bronze", "bronze.blast.BlastFurnaceTile", true, 8));
        l.add(new MultiblockSpec("auto_foundry", "auto_foundry", List.of("auto_foundry"), null, Shape.PARAMETRIC, "FoundryLayout",
              List.of(role("firebox", null, 0), role("vat", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "bronze", "bronze.autofoundry.AutoFoundryTile", true, 8));
        l.add(new MultiblockSpec("steam_drill", "steam_drill", List.of("steam_drill"), null, Shape.FIXED, "HouseNbt",
              List.of(role("base", null, 0), role("frame", null, 0), role("drill_string", null, 0), role("crown", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "steam", "geology.drill.SteamDrillTile", true, 8));
        l.add(new MultiblockSpec("pottery_kiln", "pottery_kiln", List.of("pottery_kiln"), null, Shape.FIXED, "HouseNbt",
              List.of(role("brick", null, 0), role("stairs", null, 0), role("slab", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "bronze", "bronze.foundry.KilnTile", true, 8));
    }

    private static void part3(List<MultiblockSpec> l) {
        l.add(new MultiblockSpec("coke_oven", "coke_oven", List.of("coke_oven"), null, Shape.FIXED, "HouseNbt",
              List.of(role("fire_brick", null, 0), role("firebox", null, 0), role("tuyere", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "bronze", "industry.bronze.CokeOvenTile", true, 8));
        l.add(new MultiblockSpec("warehouse_crate", null, List.of("warehouse_crate_hatch"), null, Shape.PARAMETRIC, "CrateLayout",
              List.of(role("frame", null, 0), role("panel", null, 0), role("port", null, 0)),
              TierRule.FIXED, List.of("ITEM"), "logic", false, null, null, "warehouse_crate", "warehouse.CrateHatchTile", true, 8));
        l.add(new MultiblockSpec("greenhouse_regulator", "greenhouse_regulator", List.of("greenhouse_regulator"), null, Shape.FREEFORM, "GreenhouseScan",
              List.of(role("glass", null, 0), role("frame", null, 0), role("vent", null, 0), role("door", null, 0)),
              TierRule.FIXED, List.of(), "logic", false, null, null, "house", "farm.greenhouse.GreenhouseRegulatorTile", true, 8));
        l.add(new MultiblockSpec("robotic_hub", null, List.of("robotic_port"), null, Shape.FIXED, "RoboticHubValidator",
              List.of(role("casing", null, 0), role("port", null, 0), role("bay", null, 0), role("glass", null, 0)),
              TierRule.FIXED, List.of("ITEM", "FLUID", "GAS", "ENERGY", "HEAT", "PRESSURE", "DATA"), "logic", false, null, null, "robotic_hub", "robotics.hub.TileEntityRoboticCasing", true, 8));
    }
}
