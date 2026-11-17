package fr.pioneers.api.machine;

import fr.pioneers.api.machine.MachineSpec.Form;
import fr.pioneers.api.machine.MachineSpec.Kind;
import fr.pioneers.api.machine.MachineSpec.Power;
import fr.pioneers.api.machine.MachineSpec.Role;
import fr.pioneers.api.machine.MachineSpec.TierMode;
import fr.pioneers.core.side.SideType;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * GENERE par tools/gc/api/gen_api.py d'apres les fiches tools/gc/api/machines.py et tools/gc/api/catalog_adapter.py (catalogue
 * industriel) : NE PAS MODIFIER A LA MAIN. Une fiche par machine (API Machine, lot 1) : 270 fiches, 502 blocs.
 */
public final class GeneratedMachines {

    /** Toutes les fiches, dans l'ordre des declarations (catalogue industriel d'abord). */
    public static final List<MachineSpec> ALL;

    static {
        List<MachineSpec> l = new ArrayList<>(270);
        part0(l);
        part1(l);
        part2(l);
        part3(l);
        part4(l);
        part5(l);
        part6(l);
        part7(l);
        part8(l);
        ALL = List.copyOf(l);
    }

    private GeneratedMachines() {
    }

    /** Lot 7 : postes HORS API (etablis, forge primaire ; exceptions declarees) : age de la pastille de leur infobulle standard. */
    public static final Map<String, String> STATION_AGES = Map.ofEntries(
          Map.entry("acacia_tool_rack", "mechanical"),
          Map.entry("advanced_components_bench", "industrial"),
          Map.entry("agricultural_bench", "mechanical"),
          Map.entry("assembly_bench", "industrial"),
          Map.entry("bamboo_tool_rack", "mechanical"),
          Map.entry("birch_tool_rack", "mechanical"),
          Map.entry("carpenter_bench", "mechanical"),
          Map.entry("cherry_tool_rack", "mechanical"),
          Map.entry("clay_crucible", "mechanical"),
          Map.entry("components_bench", "industrial"),
          Map.entry("coppersmith_bench", "bronze"),
          Map.entry("crimson_tool_rack", "mechanical"),
          Map.entry("dark_oak_tool_rack", "mechanical"),
          Map.entry("hand_bellows", "mechanical"),
          Map.entry("iron_anvil", "bronze"),
          Map.entry("jungle_tool_rack", "mechanical"),
          Map.entry("logistics_bench", "industrial"),
          Map.entry("luthier_bench", "mechanical"),
          Map.entry("mangrove_tool_rack", "mechanical"),
          Map.entry("mason_bench", "mechanical"),
          Map.entry("metalworker_bench", "bronze"),
          Map.entry("molder_bench", "bronze"),
          Map.entry("placed_anvil_mold", "mechanical"),
          Map.entry("placed_mold", "mechanical"),
          Map.entry("potter_bench", "mechanical"),
          Map.entry("quench_basin", "mechanical"),
          Map.entry("redstoner_bench", "industrial"),
          Map.entry("spruce_tool_rack", "mechanical"),
          Map.entry("tanner_bench", "mechanical"),
          Map.entry("tool_rack", "mechanical"),
          Map.entry("toolmaker_bench", "mechanical"),
          Map.entry("transporter_bench", "industrial"),
          Map.entry("warped_tool_rack", "mechanical"));

    private static MachineSpec.PortSpec port(SideType resource, Role role, int count) {
        return new MachineSpec.PortSpec(resource, role, count);
    }

    private static void part0(List<MachineSpec> l) {
        l.add(new MachineSpec("crusher", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_crusher", "advanced_crusher", "elite_crusher", "ultimate_crusher"),
              List.of("kit_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:crusher", true, List.of("pioneers:industry_crusher"),
              "industrial_crusher", null, "standard", false, "kit", "api.machine.unit.KitProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_crusher", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_crusher"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:crusher", true, List.of("pioneers:industry_crusher"),
              null, "crusher", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("furnace", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_furnace", "advanced_furnace", "elite_furnace", "ultimate_furnace"),
              List.of("kit_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:furnace", true, List.of("pioneers:industry_furnace", "minecraft:furnace"),
              "industrial_furnace", null, "standard", false, "kit", "api.machine.unit.KitProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_furnace", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_furnace"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:furnace", true, List.of("pioneers:industry_furnace", "minecraft:furnace"),
              null, "furnace", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("compressor", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_compressor", "advanced_compressor", "elite_compressor", "ultimate_compressor"),
              List.of("kit_machine"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:compressor", true, List.of("pioneers:industry_compressor"),
              "industrial_compressor", null, "standard", false, "kit", "api.machine.unit.KitProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_compressor", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_compressor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:compressor", true, List.of("pioneers:industry_compressor"),
              null, "compressor", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("ore_separator", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_ore_separator", "advanced_ore_separator", "elite_ore_separator", "ultimate_ore_separator"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 4), port(SideType.FLUIDS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:ore_separator", true, List.of("pioneers:industry_ore_separator"),
              "industrial_ore_separator", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_ore_separator", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_ore_separator"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 4), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:ore_separator", true, List.of("pioneers:industry_ore_separator"),
              null, "ore_separator", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("dryer", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_dryer", "advanced_dryer", "elite_dryer", "ultimate_dryer"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:dryer", true, List.of("pioneers:industry_dryer"),
              "industrial_dryer", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_dryer", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_dryer"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:dryer", true, List.of("pioneers:industry_dryer"),
              null, "dryer", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("electric_crucible", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_electric_crucible", "advanced_electric_crucible", "elite_electric_crucible", "ultimate_electric_crucible"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:electric_crucible", true, List.of("pioneers:industry_electric_crucible"),
              "industrial_electric_crucible", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_electric_crucible", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_electric_crucible"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:electric_crucible", true, List.of("pioneers:industry_electric_crucible"),
              null, "electric_crucible", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("casting_machine", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_casting_machine", "advanced_casting_machine", "elite_casting_machine", "ultimate_casting_machine"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:casting_machine", true, List.of("pioneers:industry_casting_machine"),
              "industrial_casting_machine", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_casting_machine", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_casting_machine"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:casting_machine", true, List.of("pioneers:industry_casting_machine"),
              null, "casting_machine", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("centrifuge", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_centrifuge", "advanced_centrifuge", "elite_centrifuge", "ultimate_centrifuge"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:centrifuge", true, List.of("pioneers:industry_centrifuge"),
              "industrial_centrifuge", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_centrifuge", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_centrifuge"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:centrifuge", true, List.of("pioneers:industry_centrifuge"),
              null, "centrifuge", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("electrolyzer", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_electrolyzer", "advanced_electrolyzer", "elite_electrolyzer", "ultimate_electrolyzer"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 4), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 4), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:electrolyzer", true, List.of("pioneers:industry_electrolyzer"),
              "industrial_electrolyzer", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_electrolyzer", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_electrolyzer"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 5), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 5), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:electrolyzer", true, List.of("pioneers:industry_electrolyzer"),
              null, "electrolyzer", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("chemical_bath", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_chemical_bath", "advanced_chemical_bath", "elite_chemical_bath", "ultimate_chemical_bath"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:chemical_bath", true, List.of("pioneers:industry_chemical_bath"),
              "industrial_chemical_bath", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_chemical_bath", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_chemical_bath"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:chemical_bath", true, List.of("pioneers:industry_chemical_bath"),
              null, "chemical_bath", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("mixer", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_mixer", "advanced_mixer", "elite_mixer", "ultimate_mixer"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:mixer", true, List.of("pioneers:industry_mixer"),
              "industrial_mixer", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_mixer", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_mixer"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:mixer", true, List.of("pioneers:industry_mixer"),
              null, "mixer", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("chemical_reactor", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_chemical_reactor", "advanced_chemical_reactor", "elite_chemical_reactor", "ultimate_chemical_reactor"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 4), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 4), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:chemical_reactor", true, List.of("pioneers:industry_chemical_reactor"),
              "industrial_chemical_reactor", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_chemical_reactor", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_chemical_reactor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 4), port(SideType.FLUIDS, Role.OUT, 5), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 5), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:chemical_reactor", true, List.of("pioneers:industry_chemical_reactor"),
              null, "chemical_reactor", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("crystallizer", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_crystallizer", "advanced_crystallizer", "elite_crystallizer", "ultimate_crystallizer"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.GASES, Role.IN, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:crystallizer", true, List.of("pioneers:industry_crystallizer"),
              "industrial_crystallizer", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_crystallizer", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_crystallizer"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:crystallizer", true, List.of("pioneers:industry_crystallizer"),
              null, "crystallizer", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("press", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_press", "advanced_press", "elite_press", "ultimate_press"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:press", true, List.of("pioneers:industry_press"),
              "industrial_press", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_press", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_press"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:press", true, List.of("pioneers:industry_press"),
              null, "press", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("rolling_mill", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_rolling_mill", "advanced_rolling_mill", "elite_rolling_mill", "ultimate_rolling_mill"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:rolling_mill", true, List.of("pioneers:industry_rolling_mill"),
              "industrial_rolling_mill", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_rolling_mill", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_rolling_mill"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:rolling_mill", true, List.of("pioneers:industry_rolling_mill"),
              null, "rolling_mill", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
    }

    private static void part1(List<MachineSpec> l) {
        l.add(new MachineSpec("wiremill", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_wiremill", "advanced_wiremill", "elite_wiremill", "ultimate_wiremill"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.GASES, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:wiremill", true, List.of("pioneers:industry_wiremill"),
              "industrial_wiremill", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_wiremill", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_wiremill"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:wiremill", true, List.of("pioneers:industry_wiremill"),
              null, "wiremill", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("machining_center", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_machining_center", "advanced_machining_center", "elite_machining_center", "ultimate_machining_center"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:machining_center", true, List.of("pioneers:industry_machining_center"),
              "industrial_machining_center", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_machining_center", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_machining_center"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:machining_center", true, List.of("pioneers:industry_machining_center"),
              null, "machining_center", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("extruder", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_extruder", "advanced_extruder", "elite_extruder", "ultimate_extruder"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:extruder", true, List.of("pioneers:industry_extruder"),
              "industrial_extruder", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_extruder", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_extruder"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:extruder", true, List.of("pioneers:industry_extruder"),
              null, "extruder", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("bender", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_bender", "advanced_bender", "elite_bender", "ultimate_bender"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.GASES, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:bender", true, List.of("pioneers:industry_bender"),
              "industrial_bender", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_bender", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_bender"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:bender", true, List.of("pioneers:industry_bender"),
              null, "bender", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("assembler", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_assembler", "advanced_assembler", "elite_assembler", "ultimate_assembler"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 9), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 2), port(SideType.GASES, Role.IN, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:assembler", true, List.of("pioneers:industry_assembler"),
              "industrial_assembler", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_assembler", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_assembler"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 9), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:assembler", true, List.of("pioneers:industry_assembler"),
              null, "assembler", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("solid_combustion_generator", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_solid_combustion_generator", "advanced_solid_combustion_generator", "elite_solid_combustion_generator", "ultimate_solid_combustion_generator"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ENERGY, Role.OUT, 1)), true, true, "industrial:solid_combustion_generator", true, List.of("pioneers:industry_solid_combustion_generator", "pioneers:fuels"),
              "industrial_solid_combustion_generator", null, "atelier", false, "combustion", "api.machine.unit.FuelGenerator", true, 3, -1));
        l.add(new MachineSpec("industrial_solid_combustion_generator", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_OUT,
              List.of("industrial_solid_combustion_generator"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.OUT, 1)), false, true, "industrial:solid_combustion_generator", true, List.of("pioneers:industry_solid_combustion_generator", "pioneers:fuels"),
              null, "solid_combustion_generator", "controller", false, "controller_generator", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("dynamo", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_dynamo", "advanced_dynamo", "elite_dynamo", "ultimate_dynamo"),
              List.of("industry_dynamo"), List.of(port(SideType.ENERGY, Role.OUT, 1)), false, true, null, false, List.of("pioneers:prod_kinetic"),
              null, null, "dynamo", false, "dynamo", "industry.atelier.DynamoUnit", true, 7, -1));
        l.add(new MachineSpec("blast_furnace", "bronze", Kind.PROCESS, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.FUEL,
              List.of("blast_furnace"),
              List.of("blast_furnace"), List.of(), false, false, "blast", false, List.of(),
              "industrial_blast_furnace", null, "house", true, "none", "bronze.blast.BlastFurnaceTile", true, 8, -1));
        l.add(new MachineSpec("industrial_blast_furnace", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_blast_furnace"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:blast_furnace", true, List.of("pioneers:industry_blast_furnace"),
              null, "blast_furnace", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("hot_blast_stove", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("hot_blast_stove"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:hot_blast_stove", true, List.of("pioneers:industry_hot_blast_stove"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("coke_oven", "bronze", Kind.PROCESS, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.FUEL,
              List.of("coke_oven"),
              List.of("coke_oven"), List.of(), false, false, "industrial:coke_oven", true, List.of("pioneers:industry_coke_oven"),
              "industrial_coke_oven", null, "house", true, "none", "industry.bronze.CokeOvenTile", true, 7, -1));
        l.add(new MachineSpec("industrial_coke_oven", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_coke_oven"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:coke_oven", true, List.of("pioneers:industry_coke_oven"),
              null, "coke_oven", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("converter", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("converter"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:converter", true, List.of("pioneers:industry_converter"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("arc_furnace", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("arc_furnace"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:arc_furnace", true, List.of("pioneers:industry_arc_furnace"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("bayer_digester", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("bayer_digester"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:bayer_digester", true, List.of("pioneers:industry_bayer_digester"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("kroll_reactor", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("kroll_reactor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 4), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:kroll_reactor", true, List.of("pioneers:industry_kroll_reactor"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("boiler", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("boiler"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:boiler", true, List.of("pioneers:industry_boiler", "pioneers:fuels"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("steam_turbine", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_OUT,
              List.of("steam_turbine"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.OUT, 1)), false, true, "industrial:steam_turbine", true, List.of("pioneers:industry_steam_turbine", "pioneers:fuels"),
              null, null, "controller", false, "controller_generator", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("cooling_tower", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("cooling_tower"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:cooling_tower", true, List.of("pioneers:industry_cooling_tower"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("drill", "industry", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("drill"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "drill", false, List.of(),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("industrial_pumpjack", "industry", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_pumpjack"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "well", false, List.of(),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("industrial_fluid_pump", "industry", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_fluid_pump"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "pump", false, List.of("pioneers:prod_kinetic"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("gas_separator", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_gas_separator", "advanced_gas_separator", "elite_gas_separator", "ultimate_gas_separator"),
              List.of("industry_atelier"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 6), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 6), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:gas_separator", true, List.of("pioneers:industry_gas_separator"),
              "industrial_gas_separator", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_gas_separator", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_gas_separator"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 7), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 7), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:gas_separator", true, List.of("pioneers:industry_gas_separator"),
              null, "gas_separator", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
    }

    private static void part2(List<MachineSpec> l) {
        l.add(new MachineSpec("industrial_cracker", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_cracker"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 7), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 7), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:cracker", true, List.of("pioneers:industry_cracker"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("autoclave", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_autoclave", "advanced_autoclave", "elite_autoclave", "ultimate_autoclave"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:autoclave", true, List.of("pioneers:industry_autoclave"),
              "industrial_autoclave", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_autoclave", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_autoclave"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:autoclave", true, List.of("pioneers:industry_autoclave"),
              null, "autoclave", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("fermenter", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_fermenter", "advanced_fermenter", "elite_fermenter", "ultimate_fermenter"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:fermenter", true, List.of("pioneers:industry_fermenter"),
              "industrial_fermenter", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_fermenter", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_fermenter"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 4), port(SideType.GASES, Role.OUT, 4), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:fermenter", true, List.of("pioneers:industry_fermenter"),
              null, "fermenter", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("photolithograph", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_photolithograph", "advanced_photolithograph", "elite_photolithograph", "ultimate_photolithograph"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 2), port(SideType.GASES, Role.IN, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:photolithograph", true, List.of("pioneers:industry_photolithograph"),
              "industrial_photolithograph", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_photolithograph", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_photolithograph"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:photolithograph", true, List.of("pioneers:cleanroom_photolithograph"),
              null, "photolithograph", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("pick_and_place", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_pick_and_place", "advanced_pick_and_place", "elite_pick_and_place", "ultimate_pick_and_place"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 10), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:pick_and_place", true, List.of("pioneers:industry_pick_and_place"),
              "industrial_pick_and_place", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_pick_and_place", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_pick_and_place"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 10), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:pick_and_place", true, List.of("pioneers:industry_pick_and_place"),
              null, "pick_and_place", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("bottler", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_bottler", "advanced_bottler", "elite_bottler", "ultimate_bottler"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:bottler", true, List.of("pioneers:industry_bottler"),
              "industrial_bottler", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_bottler", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_bottler"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:bottler", true, List.of("pioneers:industry_bottler"),
              null, "bottler", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("cryogenic_unit", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_cryogenic_unit", "advanced_cryogenic_unit", "elite_cryogenic_unit", "ultimate_cryogenic_unit"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:cryogenic_unit", true, List.of("pioneers:industry_cryogenic_unit"),
              "industrial_cryogenic_unit", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_cryogenic_unit", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_cryogenic_unit"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:cryogenic_unit", true, List.of("pioneers:industry_cryogenic_unit"),
              null, "cryogenic_unit", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("offshore_platform", "industry", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("offshore_platform"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "well", false, List.of(),
              null, null, "offshore", false, "controller", "industry.multi.OffshoreRisers", true, 6, -1));
        l.add(new MachineSpec("distillation_column", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("distillation_column"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 7), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:distillation_column", true, List.of("pioneers:industry_distillation_column"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("semiconductor_fab", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("semiconductor_fab"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 4), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:semiconductor_fab", true, List.of("pioneers:industry_semiconductor_fab"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("compact_gas_turbine", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_gas_turbine", "advanced_gas_turbine", "elite_gas_turbine", "ultimate_gas_turbine"),
              List.of("industry_atelier"), List.of(port(SideType.GASES, Role.IN, 2), port(SideType.ENERGY, Role.OUT, 1)), true, true, "industrial:gas_turbine", true, List.of("pioneers:industry_gas_turbine", "pioneers:fuels"),
              "gas_turbine", null, "atelier", false, "fuel_generator", "api.machine.unit.FuelGenerator", true, 3, -1));
        l.add(new MachineSpec("gas_turbine", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_OUT,
              List.of("gas_turbine"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.OUT, 1)), false, true, "industrial:gas_turbine", true, List.of("pioneers:industry_gas_turbine", "pioneers:fuels"),
              null, "compact_gas_turbine", "controller", false, "controller_generator", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("storage_tank", "industry", Kind.BUFFER, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("storage_tank"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "tank", false, List.of(),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("gas_holder", "industry", Kind.BUFFER, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("gas_holder"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "tank", false, List.of(),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("extractor", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_extractor", "advanced_extractor", "elite_extractor", "ultimate_extractor"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:extractor", true, List.of("pioneers:industry_extractor"),
              "industrial_extractor", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_extractor", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_extractor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 4), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:extractor", true, List.of("pioneers:industry_extractor"),
              null, "extractor", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("sawmill", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_sawmill", "advanced_sawmill", "elite_sawmill", "ultimate_sawmill"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:sawmill", true, List.of("pioneers:industry_sawmill"),
              "industrial_sawmill", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_sawmill", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_sawmill"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:sawmill", true, List.of("pioneers:industry_sawmill"),
              null, "sawmill", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("industrial_pyrolysis_oven", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_pyrolysis_oven"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 4), port(SideType.GASES, Role.OUT, 4), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:pyrolysis_oven", true, List.of("pioneers:industry_pyrolysis_oven"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("food_processor", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_food_processor", "advanced_food_processor", "elite_food_processor", "ultimate_food_processor"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:food_processor", true, List.of("pioneers:industry_food_processor"),
              "industrial_food_processor", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_food_processor", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_food_processor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:food_processor", true, List.of("pioneers:industry_food_processor"),
              null, "food_processor", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("hydroponic_farm", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("hydroponic_farm"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:hydroponic_farm", true, List.of("pioneers:industry_hydroponic_farm"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("tree_plantation", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("tree_plantation"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 4), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:tree_plantation", true, List.of("pioneers:industry_tree_plantation"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("aquafarm", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("aquafarm"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:aquafarm", true, List.of("pioneers:industry_aquafarm"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
    }

    private static void part3(List<MachineSpec> l) {
        l.add(new MachineSpec("centrifuge_cascade", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("centrifuge_cascade"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:centrifuge_cascade", true, List.of("pioneers:industry_centrifuge_cascade"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("fission_reactor", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("fission_reactor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:fission_reactor", true, List.of("pioneers:industry_fission_reactor", "pioneers:reactors"),
              null, null, "reactor", true, "controller", "nuclear.FissionCore", true, 6, -1));
        l.add(new MachineSpec("spent_fuel_pool", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("spent_fuel_pool"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:spent_fuel_pool", true, List.of("pioneers:industry_spent_fuel_pool"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("industrial_hot_cell", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_hot_cell"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 4), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 4), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:hot_cell", true, List.of("pioneers:industry_hot_cell"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("fast_reactor", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("fast_reactor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:fast_reactor", true, List.of("pioneers:industry_fast_reactor", "pioneers:reactors"),
              null, null, "reactor", true, "controller", "nuclear.FissionCore", true, 6, -1));
        l.add(new MachineSpec("fragment_reactor", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("fragment_reactor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:fragment_reactor", true, List.of("pioneers:industry_fragment_reactor", "pioneers:reactors"),
              null, null, "reactor", true, "controller", "nuclear.FissionCore", true, 6, -1));
        l.add(new MachineSpec("tokamak", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("tokamak"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:tokamak", true, List.of("pioneers:industry_tokamak", "pioneers:reactors"),
              null, null, "fusion", true, "controller", "fusion.FusionCore", true, 6, -1));
        l.add(new MachineSpec("stellarator", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("stellarator"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:stellarator", true, List.of("pioneers:industry_stellarator", "pioneers:reactors"),
              null, null, "fusion", true, "controller", "fusion.FusionCore", true, 6, -1));
        l.add(new MachineSpec("data_center", "industry", Kind.SERVICE, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("data_center"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:data_center", true, List.of("pioneers:industry_data_center"),
              null, null, "compute", true, "controller", "compute.ComputeCore", true, 6, -1));
        l.add(new MachineSpec("quantum_computer", "industry", Kind.SERVICE, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("quantum_computer"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:quantum_computer", true, List.of("pioneers:industry_quantum_computer"),
              null, null, "compute", true, "controller", "compute.ComputeCore", true, 6, -1));
        l.add(new MachineSpec("propellant_plant", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_propellant_plant", "advanced_propellant_plant", "elite_propellant_plant", "ultimate_propellant_plant"),
              List.of("industry_atelier"), List.of(port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 3), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:propellant_plant", true, List.of("pioneers:industry_propellant_plant"),
              "industrial_propellant_plant", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_propellant_plant", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_propellant_plant"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 4), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:propellant_plant", true, List.of("pioneers:industry_propellant_plant"),
              null, "propellant_plant", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("gravity_compressor", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("gravity_compressor"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:gravity_compressor", true, List.of("pioneers:industry_gravity_compressor"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("synthesizer", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("synthesizer"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 3), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:synthesizer", true, List.of("pioneers:industry_synthesizer"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("rail_works", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("rail_works"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:rail_works", true, List.of("pioneers:industry_rail_works"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("shipyard", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("shipyard"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:shipyard", true, List.of("pioneers:industry_shipyard"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("recycler", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_recycler", "advanced_recycler", "elite_recycler", "ultimate_recycler"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 6), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:recycler", true, List.of("pioneers:industry_recycler"),
              "industrial_recycler", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_recycler", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_recycler"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 6), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:recycler", true, List.of("pioneers:industry_recycler"),
              null, "recycler", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("power_hammer", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_power_hammer", "advanced_power_hammer", "elite_power_hammer", "ultimate_power_hammer"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 2), port(SideType.GASES, Role.IN, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:power_hammer", true, List.of("pioneers:industry_power_hammer"),
              "industrial_power_hammer", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_power_hammer", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_power_hammer"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 3), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:power_hammer", true, List.of("pioneers:industry_power_hammer"),
              null, "power_hammer", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("liquid_combustion_generator", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_liquid_combustion_generator", "advanced_liquid_combustion_generator", "elite_liquid_combustion_generator", "ultimate_liquid_combustion_generator"),
              List.of("industry_atelier"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.ENERGY, Role.OUT, 1)), true, true, "industrial:liquid_combustion_generator", true, List.of("pioneers:industry_liquid_combustion_generator", "pioneers:fuels"),
              "industrial_liquid_combustion_generator", null, "atelier", false, "fuel_generator", "api.machine.unit.FuelGenerator", true, 3, -1));
        l.add(new MachineSpec("industrial_liquid_combustion_generator", "energy", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_OUT,
              List.of("industrial_liquid_combustion_generator"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.OUT, 1)), false, true, "industrial:liquid_combustion_generator", true, List.of("pioneers:industry_liquid_combustion_generator", "pioneers:fuels"),
              null, "liquid_combustion_generator", "controller", false, "controller_generator", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("distillery", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_distillery", "advanced_distillery", "elite_distillery", "ultimate_distillery"),
              List.of("industry_atelier"), List.of(port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 2), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:distillery", true, List.of("pioneers:industry_distillery"),
              null, null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("exhaust_stack", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("exhaust_stack"),
              List.of("industry_controller"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:exhaust_stack", true, List.of("pioneers:industry_exhaust_stack"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("item_silo", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("item_silo"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:item_silo", true, List.of("pioneers:industry_item_silo"),
              null, null, "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("induction_furnace", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_induction_furnace", "advanced_induction_furnace", "elite_induction_furnace", "ultimate_induction_furnace"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:induction_furnace", true, List.of("pioneers:industry_induction_furnace"),
              null, null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("water_source", "industry", Kind.EXTRACTOR, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_water_source", "advanced_water_source", "elite_water_source", "ultimate_water_source"),
              List.of("industry_atelier"), List.of(port(SideType.FLUIDS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "water", false, List.of("pioneers:prod_water"),
              null, null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("rock_generator", "industry", Kind.EXTRACTOR, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_rock_generator", "advanced_rock_generator", "elite_rock_generator", "ultimate_rock_generator"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "rocks", false, List.of("pioneers:prod_rocks"),
              null, null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("reduction_furnace", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_reduction_furnace", "advanced_reduction_furnace", "elite_reduction_furnace", "ultimate_reduction_furnace"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.OUT, 2), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:reduction_furnace", true, List.of("pioneers:industry_reduction_furnace"),
              "industrial_reduction_furnace", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_reduction_furnace", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_reduction_furnace"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 3), port(SideType.GASES, Role.IN, 1), port(SideType.GASES, Role.OUT, 3), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:reduction_furnace", true, List.of("pioneers:industry_reduction_furnace"),
              null, "reduction_furnace", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
    }

    private static void part4(List<MachineSpec> l) {
        l.add(new MachineSpec("gear_hobber", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_gear_hobber", "advanced_gear_hobber", "elite_gear_hobber", "ultimate_gear_hobber"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:gear_hobber", true, List.of("pioneers:industry_gear_hobber"),
              "industrial_gear_hobber", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_gear_hobber", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_gear_hobber"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 2), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:gear_hobber", true, List.of("pioneers:industry_gear_hobber"),
              null, "gear_hobber", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("joinery", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_joinery", "advanced_joinery", "elite_joinery", "ultimate_joinery"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:joinery", true, List.of("pioneers:industry_joinery"),
              "industrial_joinery", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_joinery", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_joinery"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 6), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:joinery", true, List.of("pioneers:industry_joinery"),
              null, "joinery", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("tailoring_workshop", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_tailoring_workshop", "advanced_tailoring_workshop", "elite_tailoring_workshop", "ultimate_tailoring_workshop"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:tailoring_workshop", true, List.of("pioneers:industry_tailoring_workshop"),
              "industrial_tailoring_workshop", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_tailoring_workshop", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_tailoring_workshop"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:tailoring_workshop", true, List.of("pioneers:industry_tailoring_workshop"),
              null, "tailoring_workshop", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("packaging_machine", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_packaging_machine", "advanced_packaging_machine", "elite_packaging_machine", "ultimate_packaging_machine"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:packaging_machine", true, List.of("pioneers:industry_packaging_machine"),
              "industrial_packaging_machine", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_packaging_machine", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_packaging_machine"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:packaging_machine", true, List.of("pioneers:industry_packaging_machine"),
              null, "packaging_machine", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("sheet_metal_shop", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_sheet_metal_shop", "advanced_sheet_metal_shop", "elite_sheet_metal_shop", "ultimate_sheet_metal_shop"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:sheet_metal_shop", true, List.of("pioneers:industry_sheet_metal_shop"),
              "industrial_sheet_metal_shop", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_sheet_metal_shop", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_sheet_metal_shop"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:sheet_metal_shop", true, List.of("pioneers:industry_sheet_metal_shop"),
              null, "sheet_metal_shop", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("glassworks", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_glassworks", "advanced_glassworks", "elite_glassworks", "ultimate_glassworks"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:glassworks", true, List.of("pioneers:industry_glassworks"),
              "industrial_glassworks", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_glassworks", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_glassworks"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:glassworks", true, List.of("pioneers:industry_glassworks"),
              null, "glassworks", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("electromechanical_bench", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_electromechanical_bench", "advanced_electromechanical_bench", "elite_electromechanical_bench", "ultimate_electromechanical_bench"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:electromechanical_bench", true, List.of("pioneers:industry_electromechanical_bench"),
              "industrial_electromechanical_bench", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_electromechanical_bench", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_electromechanical_bench"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:electromechanical_bench", true, List.of("pioneers:industry_electromechanical_bench"),
              null, "electromechanical_bench", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("stoneworks", "industry", Kind.PROCESS, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_stoneworks", "advanced_stoneworks", "elite_stoneworks", "ultimate_stoneworks"),
              List.of("industry_atelier"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, "industrial:stoneworks", true, List.of("pioneers:industry_stoneworks", "minecraft:stonecutting"),
              "industrial_stoneworks", null, "standard", false, "industry", "api.machine.unit.IndustryProcess", true, 2, -1));
        l.add(new MachineSpec("industrial_stoneworks", "industry", Kind.PROCESS, Form.MULTIBLOCK, TierMode.STRUCTURE, null, Power.FE_IN,
              List.of("industrial_stoneworks"),
              List.of("industry_controller"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, "industrial:stoneworks", true, List.of("pioneers:industry_stoneworks", "minecraft:stonecutting"),
              null, "stoneworks", "controller", false, "controller", "industry.multi.MultiProcess", true, 5, -1));
        l.add(new MachineSpec("solar_panel", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_solar_panel", "advanced_solar_panel", "elite_solar_panel", "ultimate_solar_panel"),
              List.of("solar_panel"), List.of(port(SideType.ENERGY, Role.OUT, 1)), false, true, null, false, List.of("pioneers:prod_renewable"),
              null, null, "generator", false, "renewable", "energy.SolarLogic", true, 3, -1));
        l.add(new MachineSpec("wind_turbine", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_wind_turbine", "advanced_wind_turbine", "elite_wind_turbine", "ultimate_wind_turbine"),
              List.of("wind_turbine"), List.of(port(SideType.ENERGY, Role.OUT, 1)), false, true, null, false, List.of("pioneers:prod_renewable"),
              null, null, "generator", false, "renewable", "energy.WindLogic", true, 3, -1));
        l.add(new MachineSpec("hydro_turbine", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_hydro_turbine", "advanced_hydro_turbine", "elite_hydro_turbine", "ultimate_hydro_turbine"),
              List.of("hydro_turbine"), List.of(port(SideType.ENERGY, Role.OUT, 1)), false, true, null, false, List.of("pioneers:prod_renewable"),
              null, null, "generator", false, "renewable", "energy.HydroLogic", true, 3, -1));
        l.add(new MachineSpec("geothermal_plant", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_geothermal_plant", "advanced_geothermal_plant", "elite_geothermal_plant", "ultimate_geothermal_plant"),
              List.of("geothermal_plant"), List.of(port(SideType.FLUIDS, Role.IN, 1), port(SideType.ENERGY, Role.OUT, 1)), false, true, null, false, List.of("pioneers:prod_renewable"),
              null, null, "generator", false, "renewable", "energy.GeothermalLogic", true, 3, -1));
        l.add(new MachineSpec("microbial_fuel_cell", "energy", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_microbial_fuel_cell", "advanced_microbial_fuel_cell", "elite_microbial_fuel_cell", "ultimate_microbial_fuel_cell"),
              List.of("microbial_fuel_cell"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ENERGY, Role.OUT, 1)), false, true, null, false, List.of("pioneers:prod_renewable"),
              null, null, "generator", false, "renewable", "energy.MicrobialLogic", true, 3, -1));
        l.add(new MachineSpec("item_buffer", "logistics", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_item_buffer", "advanced_item_buffer", "elite_item_buffer", "ultimate_item_buffer", "creative_item_buffer"),
              List.of("buffer"), List.of(port(SideType.ITEMS, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "item_buffer", false, "buffer", "api.machine.unit.StorageUnit", true, 3, -1));
        l.add(new MachineSpec("fluid_buffer", "logistics", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_fluid_buffer", "advanced_fluid_buffer", "elite_fluid_buffer", "ultimate_fluid_buffer", "creative_fluid_buffer"),
              List.of("buffer"), List.of(port(SideType.FLUIDS, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "buffer", false, "buffer", "api.machine.unit.StorageUnit", true, 3, -1));
        l.add(new MachineSpec("gas_buffer", "logistics", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_gas_buffer", "advanced_gas_buffer", "elite_gas_buffer", "ultimate_gas_buffer", "creative_gas_buffer"),
              List.of("buffer"), List.of(port(SideType.GASES, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "buffer", false, "buffer", "api.machine.unit.StorageUnit", true, 3, -1));
        l.add(new MachineSpec("heat_buffer", "logistics", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_heat_buffer", "advanced_heat_buffer", "elite_heat_buffer", "ultimate_heat_buffer", "creative_heat_buffer"),
              List.of("buffer"), List.of(port(SideType.HEAT, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "buffer", false, "buffer", "api.machine.unit.StorageUnit", true, 3, -1));
        l.add(new MachineSpec("energy_buffer", "logistics", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_energy_buffer", "advanced_energy_buffer", "elite_energy_buffer", "ultimate_energy_buffer", "creative_energy_buffer"),
              List.of("buffer"), List.of(port(SideType.ENERGY, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "buffer", false, "buffer", "api.machine.unit.StorageUnit", true, 3, -1));
        l.add(new MachineSpec("pressure_buffer", "logistics", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_pressure_buffer", "advanced_pressure_buffer", "elite_pressure_buffer", "ultimate_pressure_buffer", "creative_pressure_buffer"),
              List.of("buffer"), List.of(port(SideType.PRESSURE, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "buffer", false, "buffer", "api.machine.unit.StorageUnit", true, 3, -1));
        l.add(new MachineSpec("data_bank", "computing", Kind.BUFFER, Form.COMPACT, TierMode.MK_CREATIVE, null, Power.NONE,
              List.of("basic_data_bank", "advanced_data_bank", "elite_data_bank", "ultimate_data_bank", "creative_data_bank"),
              List.of("basic_data_bank", "advanced_data_bank", "elite_data_bank", "ultimate_data_bank", "creative_data_bank"), List.of(port(SideType.DATA, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "data_bank", false, "none", "api.machine.unit.DataBankUnit", true, 4, -1));
        l.add(new MachineSpec("chunk_loader", "space", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_chunk_loader", "advanced_chunk_loader", "elite_chunk_loader", "ultimate_chunk_loader"),
              List.of("chunk_loader"), List.of(port(SideType.ENERGY, Role.IN, 1)), true, true, null, false, List.of(),
              null, null, "space", false, "none", "tile.ChunkLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("force_field_generator", "space", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_force_field_generator", "advanced_force_field_generator", "elite_force_field_generator", "ultimate_force_field_generator"),
              List.of("force_field_generator"), List.of(port(SideType.ENERGY, Role.IN, 1)), true, true, null, false, List.of(),
              null, null, "space", false, "none", "tile.ForceFieldUnit", true, 8, -1));
    }

    private static void part5(List<MachineSpec> l) {
        l.add(new MachineSpec("oxygen_diffuser", "space", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_oxygen_diffuser", "advanced_oxygen_diffuser", "elite_oxygen_diffuser", "ultimate_oxygen_diffuser"),
              List.of("oxygen_diffuser"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, null, false, List.of(),
              null, null, "space", false, "none", "tile.OxygenDiffuserUnit", true, 8, -1));
        l.add(new MachineSpec("lightning_collector", "space", Kind.GENERATOR, Form.COMPACT, TierMode.MK, null, Power.FE_OUT,
              List.of("basic_lightning_collector", "advanced_lightning_collector", "elite_lightning_collector", "ultimate_lightning_collector"),
              List.of("lightning_collector"), List.of(port(SideType.ENERGY, Role.OUT, 1)), true, true, null, false, List.of(),
              null, null, "space", false, "none", "tile.LightningUnit", true, 8, -1));
        l.add(new MachineSpec("asteroid_crusher", "science", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("asteroid_crusher"),
              List.of("asteroid_crusher"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.OUT, 1)), false, true, null, false, List.of("pioneers:asteroid_analysis"),
              null, null, "science", false, "none", "tile.AnalysisUnit", true, 8, -1));
        l.add(new MachineSpec("geo_spectrometer", "science", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("geo_spectrometer"),
              List.of("geo_spectrometer"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.OUT, 1)), false, true, null, false, List.of("pioneers:geology_analysis"),
              null, null, "science", false, "none", "tile.AnalysisUnit", true, 8, -1));
        l.add(new MachineSpec("bio_sequencer", "science", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("bio_sequencer"),
              List.of("bio_sequencer"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.OUT, 1)), false, true, null, false, List.of("pioneers:biology_analysis"),
              null, null, "science", false, "none", "tile.AnalysisUnit", true, 8, -1));
        l.add(new MachineSpec("biome_analyzer", "science", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("biome_analyzer"),
              List.of("biome_analyzer"), List.of(port(SideType.ITEMS, Role.IN, 8), port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.OUT, 1)), false, true, null, false, List.of("pioneers:biome_analysis"),
              null, null, "science", false, "none", "tile.BiomeAnalysisUnit", true, 8, -1));
        l.add(new MachineSpec("research_table", "science", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("research_table"),
              List.of("research_table"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "research", true, "none", "tile.ResearchUnit", true, 8, -1));
        l.add(new MachineSpec("data_antenna", "science", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("data_antenna"),
              List.of("data_antenna"), List.of(port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "science", false, "none", "tile.AntennaUnit", true, 8, -1));
        l.add(new MachineSpec("equipment_assembly_table", "works", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("equipment_assembly_table"),
              List.of("equipment_assembly_table"), List.of(port(SideType.ITEMS, Role.BOTH, 53), port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "works", false, "none", "tile.TileEntityAssemblyTable", false, 4, -1));
        l.add(new MachineSpec("module_assembly_table", "works", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("module_assembly_table"),
              List.of("module_assembly_table"), List.of(port(SideType.ITEMS, Role.BOTH, 53), port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "works", false, "none", "tile.TileEntityAssemblyTable", false, 4, -1));
        l.add(new MachineSpec("rocket_integration_hall", "works", Kind.PROCESS, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("rocket_integration_hall"),
              List.of("rocket_integration_hall"), List.of(port(SideType.ITEMS, Role.BOTH, 53)), false, false, null, false, List.of(),
              null, null, "house_works", true, "none", "tile.TileEntityAssemblyHall", true, 8, -1));
        l.add(new MachineSpec("robotic_assembly_bay", "works", Kind.PROCESS, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("robotic_assembly_bay"),
              List.of("robotic_assembly_bay"), List.of(port(SideType.ITEMS, Role.BOTH, 53)), false, false, null, false, List.of(),
              null, null, "house_works", true, "none", "tile.TileEntityAssemblyHall", true, 8, -1));
        l.add(new MachineSpec("launch_pad_core", "space", Kind.SERVICE, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("launch_pad_core"),
              List.of("launch_pad_core"), List.of(port(SideType.ITEMS, Role.BOTH, 22), port(SideType.FLUIDS, Role.IN, 1)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "tile.TileEntityLaunchPad", true, 8, -1));
        l.add(new MachineSpec("greenhouse_controller", "space", Kind.PROCESS, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("greenhouse_controller"),
              List.of("greenhouse_controller"), List.of(port(SideType.ITEMS, Role.BOTH, 20)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "tile.TileEntityGreenhouse", true, 8, -1));
        l.add(new MachineSpec("mining_outpost_controller", "space", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("mining_outpost_controller"),
              List.of("mining_outpost_controller"), List.of(port(SideType.ITEMS, Role.BOTH, 24)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "tile.TileEntityMiningOutpost", true, 8, -1));
        l.add(new MachineSpec("drone_bay", "space", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("drone_bay"),
              List.of("drone_bay"), List.of(port(SideType.ITEMS, Role.BOTH, 5), port(SideType.GASES, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "house", true, "none", "tile.TileEntityDroneBay", true, 8, -1));
        l.add(new MachineSpec("solar_absorber_controller", "cosmic", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_OUT,
              List.of("solar_absorber_controller"),
              List.of("solar_absorber_controller"), List.of(), false, false, null, false, List.of(),
              null, null, "house", true, "none", "content.absorber.AbsorberControllerTile", true, 8, -1));
        l.add(new MachineSpec("space_simulator_controller", "cosmic", Kind.PROCESS, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("space_simulator_controller"),
              List.of("space_simulator_controller"), List.of(port(SideType.ITEMS, Role.BOTH, 20)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "content.spacesim.SpaceSimControllerTile", true, 8, -1));
        l.add(new MachineSpec("space_stabilizer_controller", "cosmic", Kind.PROCESS, Form.MULTIBLOCK, TierMode.SINGLE, null, Power.FE_IN,
              List.of("space_stabilizer_controller"),
              List.of("space_stabilizer_controller"), List.of(port(SideType.ITEMS, Role.IN, 1)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "content.stabilizer.StabilizerControllerTile", true, 8, -1));
        l.add(new MachineSpec("computer", "computing", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("computer"),
              List.of("computer"), List.of(port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "computer", true, "none", "computing.ComputerTile", false, 4, -1));
        l.add(new MachineSpec("plc", "computing", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("plc"),
              List.of("logic_controller"), List.of(port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "computer", true, "none", "computing.LogicTile", false, 4, -1));
        l.add(new MachineSpec("relay_cabinet", "computing", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("relay_cabinet"),
              List.of("logic_controller"), List.of(port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "computer", true, "none", "computing.LogicTile", false, 4, -1));
        l.add(new MachineSpec("network_terminal", "computing", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.DATA,
              List.of("network_terminal"),
              List.of("network_terminal"), List.of(), false, false, null, false, List.of(),
              null, null, "network", false, "none", "computing.NetworkTerminalTile", false, 4, -1));
        l.add(new MachineSpec("ai_lab", "computing", Kind.PROCESS, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("ai_lab"),
              List.of("ai_lab"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.ENERGY, Role.IN, 1), port(SideType.DATA, Role.IN, 1)), true, true, null, false, List.of(),
              null, null, "ai", false, "none", "compute.AiLabTile", false, 4, -1));
        l.add(new MachineSpec("factory_ai_core", "computing", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("factory_ai_core"),
              List.of("factory_ai_core"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ENERGY, Role.IN, 1)), true, true, null, false, List.of(),
              null, null, "ai", false, "none", "compute.FactoryAiCoreTile", false, 4, -1));
        l.add(new MachineSpec("modulation_bench", "gear", Kind.STATION, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("modulation_bench"),
              List.of("modulation_bench"), List.of(port(SideType.ITEMS, Role.BOTH, 2), port(SideType.ENERGY, Role.IN, 1)), true, true, null, false, List.of(),
              null, null, "gear", false, "none", "gear.ModulationUnit", true, 8, -1));
        l.add(new MachineSpec("electric_heater", "transport", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("electric_heater"),
              List.of("electric_heater"), List.of(port(SideType.ENERGY, Role.IN, 1), port(SideType.HEAT, Role.OUT, 1)), false, true, null, false, List.of(),
              null, null, "heater", false, "electric_heater", "transport.heat.ElectricHeaterTile", false, 4, -1));
        l.add(new MachineSpec("air_compressor", "transport", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.FE_IN,
              List.of("air_compressor"),
              List.of("air_compressor"), List.of(port(SideType.ENERGY, Role.IN, 1), port(SideType.PRESSURE, Role.OUT, 1)), false, true, null, false, List.of(),
              null, null, "compressor", false, "air_compressor", "transport.pressure.AirCompressorTile", false, 4, -1));
        l.add(new MachineSpec("air_loader", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_air_loader", "advanced_air_loader", "elite_air_loader", "ultimate_air_loader"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("data_loader", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_data_loader", "advanced_data_loader", "elite_data_loader", "ultimate_data_loader"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
    }

    private static void part6(List<MachineSpec> l) {
        l.add(new MachineSpec("energy_loader", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_energy_loader", "advanced_energy_loader", "elite_energy_loader", "ultimate_energy_loader"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("fluid_loader", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_fluid_loader", "advanced_fluid_loader", "elite_fluid_loader", "ultimate_fluid_loader"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("charging_post", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("charging_post"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("fuel_pump", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("fuel_pump"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("passenger_platform", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("passenger_platform"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("steam_charger", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("steam_charger"),
              List.of("rail_post"), List.of(), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.RailPostUnit", true, 8, -1));
        l.add(new MachineSpec("item_loader", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_item_loader", "advanced_item_loader", "elite_item_loader", "ultimate_item_loader"),
              List.of("item_loader"), List.of(port(SideType.ITEMS, Role.BOTH, 9)), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.ItemLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("loading_silo", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_loading_silo", "advanced_loading_silo", "elite_loading_silo", "ultimate_loading_silo"),
              List.of("item_loader"), List.of(port(SideType.ITEMS, Role.BOTH, 9)), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.ItemLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("unloading_pit", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.NONE,
              List.of("basic_unloading_pit", "advanced_unloading_pit", "elite_unloading_pit", "ultimate_unloading_pit"),
              List.of("item_loader"), List.of(port(SideType.ITEMS, Role.BOTH, 9)), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.ItemLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("coaling_tower", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("coaling_tower"),
              List.of("item_loader"), List.of(port(SideType.ITEMS, Role.BOTH, 9)), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.ItemLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("nuclear_fuel_station", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("nuclear_fuel_station"),
              List.of("item_loader"), List.of(port(SideType.ITEMS, Role.BOTH, 9)), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.ItemLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("platform_crane", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("platform_crane"),
              List.of("item_loader"), List.of(port(SideType.ITEMS, Role.BOTH, 9)), false, false, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.ItemLoaderUnit", true, 8, -1));
        l.add(new MachineSpec("substation", "rail", Kind.SERVICE, Form.COMPACT, TierMode.MK, null, Power.FE_IN,
              List.of("basic_substation", "advanced_substation", "elite_substation", "ultimate_substation"),
              List.of("substation"), List.of(port(SideType.ENERGY, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.SubstationUnit", true, 8, -1));
        l.add(new MachineSpec("water_crane", "rail", Kind.SERVICE, Form.COMPACT, TierMode.SINGLE, null, Power.NONE,
              List.of("water_crane"),
              List.of("water_crane"), List.of(port(SideType.FLUIDS, Role.OUT, 1)), false, true, null, false, List.of(),
              null, null, "rail", true, "none", "rail.block.WaterCraneUnit", true, 8, -1));
        l.add(new MachineSpec("harbor", "naval", Kind.SERVICE, Form.MULTIBLOCK, TierMode.MK, null, Power.FE_IN,
              List.of("basic_harbor", "advanced_harbor", "elite_harbor", "ultimate_harbor"),
              List.of("harbor"), List.of(port(SideType.ITEMS, Role.BOTH, 9), port(SideType.FLUIDS, Role.BOTH, 1), port(SideType.GASES, Role.BOTH, 1), port(SideType.ENERGY, Role.BOTH, 1), port(SideType.DATA, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "harbor", true, "none", "naval.HarborTile", true, 8, -1));
        l.add(new MachineSpec("millstone", "mechanical", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("millstone"),
              List.of("mechanical_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, false, "mechanical:milling", true, List.of("pioneers:industry_crusher"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("jaw_crusher", "mechanical", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("jaw_crusher"),
              List.of("mechanical_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, false, "mechanical:jaw_crushing", true, List.of("pioneers:industry_crusher"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("mechanical_sifter", "mechanical", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_sifter"),
              List.of("mechanical_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 7)), false, false, "mechanical:sifting", false, List.of("pioneers:industry_ore_separator"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("mechanical_saw", "mechanical", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_saw"),
              List.of("mechanical_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, false, "mechanical:sawing", true, List.of("pioneers:industry_sawmill"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("wood_lathe", "mechanical", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("wood_lathe"),
              List.of("mechanical_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, false, "mechanical:turning", true, List.of("pioneers:industry_machining_center", "pioneers:industry_bender", "pioneers:industry_extruder"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("mechanical_drill", "mechanical", Kind.EXTRACTOR, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_drill"),
              List.of("mechanical_machine"), List.of(port(SideType.ITEMS, Role.OUT, 1)), false, false, null, false, List.of("pioneers:prod_kinetic"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("noria", "mechanical", Kind.EXTRACTOR, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("noria"),
              List.of("mechanical_machine"), List.of(port(SideType.FLUIDS, Role.OUT, 1)), false, false, null, false, List.of("pioneers:prod_kinetic"),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("bellows", "mechanical", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("bellows"),
              List.of("mechanical_machine"), List.of(), false, false, null, false, List.of(),
              null, null, "mechanical", false, "none", "kinetics.machines.MechanicalProcess", true, 7, -1));
        l.add(new MachineSpec("mechanical_chopper", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_chopper"),
              List.of("kitchen_machine"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, false, "kitchen:chopper", true, List.of(),
              null, null, "kitchen", false, "none", "kinetics.kitchen.KitchenProcess", true, 8, -1));
        l.add(new MachineSpec("mechanical_churn", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_churn"),
              List.of("kitchen_machine"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2)), false, false, "kitchen:churn", true, List.of(),
              null, null, "kitchen", false, "none", "kinetics.kitchen.KitchenProcess", true, 8, -1));
        l.add(new MachineSpec("mechanical_honey_extractor", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_honey_extractor"),
              List.of("kitchen_machine"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 4)), false, false, "kitchen:honey_extractor", true, List.of(),
              null, null, "kitchen", false, "none", "kinetics.kitchen.KitchenProcess", true, 8, -1));
        l.add(new MachineSpec("mechanical_kneader", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_kneader"),
              List.of("kitchen_machine"), List.of(port(SideType.ITEMS, Role.IN, 7), port(SideType.ITEMS, Role.OUT, 2)), false, false, "kitchen:kneader", true, List.of(),
              null, null, "kitchen", false, "none", "kinetics.kitchen.KitchenProcess", true, 8, -1));
        l.add(new MachineSpec("mechanical_press", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("mechanical_press"),
              List.of("kitchen_machine"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2)), false, false, "kitchen:press", true, List.of(),
              null, null, "kitchen", false, "none", "kinetics.kitchen.KitchenProcess", true, 8, -1));
        l.add(new MachineSpec("beam_engine", "mechanical", Kind.GENERATOR, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_OUT,
              List.of("beam_engine"),
              List.of("beam_engine"), List.of(port(SideType.ITEMS, Role.IN, 3)), false, false, null, false, List.of(),
              null, null, "beam_engine", false, "none", "kinetics.sources.BeamEngineUnit", true, 8, -1));
        l.add(new MachineSpec("gearshift", "mechanical", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("gearshift"),
              List.of("inline_box"), List.of(), false, false, null, false, List.of(),
              null, null, "transmission", false, "none", "kinetics.parts.TransmissionUnit", true, 8, -1));
    }

    private static void part7(List<MachineSpec> l) {
        l.add(new MachineSpec("clutch", "mechanical", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("clutch"),
              List.of("inline_box"), List.of(), false, false, null, false, List.of(),
              null, null, "transmission", false, "none", "kinetics.parts.TransmissionUnit", true, 8, -1));
        l.add(new MachineSpec("reverser", "mechanical", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("reverser"),
              List.of("inline_box"), List.of(), false, false, null, false, List.of(),
              null, null, "transmission", false, "none", "kinetics.parts.TransmissionUnit", true, 8, -1));
        l.add(new MachineSpec("governor", "mechanical", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.KINETIC_IN,
              List.of("governor"),
              List.of("governor"), List.of(), false, false, null, false, List.of(),
              null, null, "governor", false, "none", "kinetics.parts.GovernorUnit", true, 8, -1));
        l.add(new MachineSpec("steam_compressor", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_compressor"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:compression", true, List.of("pioneers:industry_compressor"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_extractor", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_extractor"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:extraction", true, List.of("pioneers:industry_extractor"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_macerator", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_macerator"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:broyage", true, List.of("pioneers:industry_crusher"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_furnace", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_furnace"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:cuisson", true, List.of("pioneers:industry_furnace", "minecraft:furnace"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_wiremill", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_wiremill"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:trefilage", true, List.of("pioneers:industry_wiremill"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_rolling_mill", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_rolling_mill"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:laminage", true, List.of("pioneers:industry_rolling_mill"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_saw", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_saw"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:sciage", true, List.of("pioneers:industry_sawmill", "pioneers:industry_stoneworks"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_mixer", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_mixer"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.FLUIDS, Role.IN, 1), port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:melange", true, List.of("pioneers:industry_mixer"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_sifter", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_sifter"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 6)), false, true, "family:tamisage", true, List.of("pioneers:industry_ore_separator"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_lathe", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_lathe"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:tournage", true, List.of("pioneers:industry_machining_center", "pioneers:industry_bender", "pioneers:industry_extruder", "pioneers:industry_gear_hobber"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_press", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_press"),
              List.of("steam_press"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2)), false, true, "family:presse", true, List.of("pioneers:industry_press"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_induction_furnace", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.TIER, "bronze", Power.STEAM_IN,
              List.of("steam_induction_furnace"),
              List.of("steam_process"), List.of(port(SideType.GASES, Role.IN, 1), port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 1)), false, true, "family:induction", true, List.of("pioneers:industry_induction_furnace"),
              null, null, "standard", false, "none", "bronze.machines.SteamProcess", true, 7, 1));
        l.add(new MachineSpec("steam_blower", "bronze", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "bronze", Power.STEAM_IN,
              List.of("steam_blower"),
              List.of("steam_blower"), List.of(port(SideType.GASES, Role.IN, 1)), false, false, null, false, List.of(),
              null, null, "steam_blower", false, "none", "bronze.machines.SteamBlowerUnit", true, 7, -1));
        l.add(new MachineSpec("steam_engine", "bronze", Kind.GENERATOR, Form.COMPACT, TierMode.AGE, "bronze", Power.KINETIC_OUT,
              List.of("steam_engine"),
              List.of("steam_engine"), List.of(port(SideType.GASES, Role.IN, 1)), false, false, null, false, List.of(),
              null, null, "steam_engine", false, "none", "bronze.steam.SteamEngineUnit", true, 8, -1));
        l.add(new MachineSpec("firebox", "bronze", Kind.GENERATOR, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.FUEL,
              List.of("firebox"),
              List.of("firebox"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.GASES, Role.OUT, 1)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "bronze.steam.FireboxTile", true, 8, -1));
        l.add(new MachineSpec("steam_drill", "bronze", Kind.EXTRACTOR, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.STEAM_IN,
              List.of("steam_drill"),
              List.of("steam_drill"), List.of(port(SideType.ITEMS, Role.OUT, 9)), false, false, null, false, List.of(),
              null, null, "house", true, "none", "geology.drill.SteamDrillTile", true, 8, -1));
        l.add(new MachineSpec("crucible_furnace", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "bronze", Power.FUEL,
              List.of("crucible_furnace"),
              List.of("crucible_furnace"), List.of(port(SideType.ITEMS, Role.IN, 5)), false, false, "alloy:crucible", true, List.of("pioneers:casting"),
              null, null, "crucible", false, "none", "bronze.foundry.CrucibleUnit", true, 8, -1));
        l.add(new MachineSpec("forge_hearth", "bronze", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.FUEL,
              List.of("forge_hearth"),
              List.of("forge_hearth"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1)), false, false, null, false, List.of("pioneers:forge_hearth"),
              null, null, "hearth", false, "none", "forge.smithing.HearthUnit", true, 8, -1));
        l.add(new MachineSpec("casting_table", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("casting_table"),
              List.of("casting_table"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1)), false, false, null, false, List.of("pioneers:casting"),
              null, null, "casting", false, "none", "bronze.foundry.CastingUnit", true, 8, -1));
        l.add(new MachineSpec("auto_foundry", "bronze", Kind.PROCESS, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.FUEL,
              List.of("auto_foundry"),
              List.of("auto_foundry"), List.of(port(SideType.ITEMS, Role.IN, 4)), false, false, "alloy:crucible", true, List.of("pioneers:casting"),
              null, null, "house", true, "none", "bronze.autofoundry.AutoFoundryTile", true, 8, -1));
        l.add(new MachineSpec("auto_mold", "bronze", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("auto_mold"),
              List.of("auto_mold"), List.of(port(SideType.ITEMS, Role.OUT, 1)), false, false, null, false, List.of("pioneers:casting"),
              null, null, "auto_mold", false, "none", "bronze.autofoundry.AutoMoldUnit", true, 8, -1));
        l.add(new MachineSpec("warehouse_counter", "logistics", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("warehouse_counter"),
              List.of("warehouse_counter"), List.of(port(SideType.ITEMS, Role.BOTH, 18)), false, false, null, false, List.of(),
              null, null, "warehouse", false, "none", "warehouse.CounterUnit", true, 8, -1));
        l.add(new MachineSpec("warehouse_inlet", "logistics", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("warehouse_inlet"),
              List.of("warehouse_inlet"), List.of(port(SideType.ITEMS, Role.IN, 9), port(SideType.ITEMS, Role.IN, 1)), false, false, null, false, List.of(),
              null, null, "warehouse_io", false, "none", "warehouse.CounterUnit", true, 8, -1));
        l.add(new MachineSpec("warehouse_outlet", "logistics", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("warehouse_outlet"),
              List.of("warehouse_outlet"), List.of(port(SideType.ITEMS, Role.OUT, 9)), false, false, null, false, List.of(),
              null, null, "warehouse_io", false, "none", "warehouse.CounterUnit", true, 8, -1));
        l.add(new MachineSpec("bag_interface", "logistics", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("bag_interface"),
              List.of("bag_interface"), List.of(port(SideType.ITEMS, Role.BOTH, 1)), false, false, null, false, List.of(),
              null, null, "bag_interface", false, "none", "home.bag.BagInterfaceTile", true, 8, -1));
        l.add(new MachineSpec("pottery_kiln", "bronze", Kind.PROCESS, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.FUEL,
              List.of("pottery_kiln"),
              List.of("pottery_kiln"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 4)), false, false, "kiln", false, List.of("pioneers:kiln"),
              null, null, "house", true, "none", "bronze.foundry.KilnTile", true, 8, -1));
        l.add(new MachineSpec("greenhouse_regulator", "kitchen", Kind.SERVICE, Form.MULTIBLOCK, TierMode.AGE, "bronze", Power.NONE,
              List.of("greenhouse_regulator"),
              List.of("greenhouse_regulator"), List.of(), false, false, null, false, List.of(),
              null, null, "house", true, "none", "farm.greenhouse.GreenhouseRegulatorTile", true, 8, -1));
    }

    private static void part8(List<MachineSpec> l) {
        l.add(new MachineSpec("cutting_board", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("cutting_board"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2)), false, true, "station:cutting", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("kitchen_stove", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.FUEL,
              List.of("kitchen_stove"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("cooking_pot", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("cooking_pot"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 8), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 1)), false, true, "station:cooking", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("frying_pan", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("frying_pan"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.BOTH, 4)), false, true, "station:frying", false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("bread_oven", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.FUEL,
              List.of("bread_oven"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.BOTH, 4)), false, true, "station:baking", false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("mixing_bowl", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("mixing_bowl"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 7), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1)), false, true, "station:mixing", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("drying_rack", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("drying_rack"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.BOTH, 4)), false, true, "station:drying", false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("preserves_jar", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("preserves_jar"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 1)), false, true, "station:preserving", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("fermenting_vat", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("fermenting_vat"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1)), false, true, "station:fermenting", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("aging_barrel", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("aging_barrel"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.BOTH, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("brew_kettle", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("brew_kettle"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1)), false, true, "station:brewing", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("copper_still", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("copper_still"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 4), port(SideType.ITEMS, Role.OUT, 3), port(SideType.FLUIDS, Role.IN, 1), port(SideType.FLUIDS, Role.OUT, 1)), false, true, "station:distilling", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("fruit_press", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("fruit_press"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.OUT, 1)), false, true, "station:pressing", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("cheese_press", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("cheese_press"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1)), false, true, "station:cheese_pressing", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("can_sealer", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("can_sealer"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("butter_churn", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("butter_churn"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 7), port(SideType.ITEMS, Role.OUT, 2), port(SideType.FLUIDS, Role.IN, 1)), false, true, "station:churning", true, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("feeder", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("feeder"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 9)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("water_trough", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("water_trough"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.StationTiles", true, 8, -1));
        l.add(new MachineSpec("honey_extractor", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("honey_extractor"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 2), port(SideType.ITEMS, Role.OUT, 4), port(SideType.FLUIDS, Role.OUT, 1)), false, true, "station:extracting", false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.HoneyExtractorTile", true, 8, -1));
        l.add(new MachineSpec("smokehouse", "kitchen", Kind.PROCESS, Form.COMPACT, TierMode.AGE, "mechanical", Power.FUEL,
              List.of("smokehouse"),
              List.of("kitchen_station"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.BOTH, 4)), false, true, "station:smoking", false, List.of(),
              null, null, "farm", false, "none", "farm.kitchen.SmokehouseTile", true, 8, -1));
        l.add(new MachineSpec("bronze_sprinkler", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("bronze_sprinkler"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.BOTH, 2), port(SideType.FLUIDS, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.SprinklerTile", true, 8, -1));
        l.add(new MachineSpec("steel_sprinkler", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("steel_sprinkler"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.BOTH, 3), port(SideType.FLUIDS, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.SprinklerTile", true, 8, -1));
        l.add(new MachineSpec("compost_bin", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("compost_bin"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.IN, 1), port(SideType.ITEMS, Role.OUT, 2)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.CompostBinTile", true, 8, -1));
        l.add(new MachineSpec("weather_station", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("weather_station"),
              List.of("farm_device"), List.of(), false, false, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.WeatherStationTile", true, 8, -1));
        l.add(new MachineSpec("apiary", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("apiary"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.BOTH, 4)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.ApiaryTile", true, 8, -1));
        l.add(new MachineSpec("nest_box", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("nest_box"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.OUT, 4)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.NestBoxTile", true, 8, -1));
        l.add(new MachineSpec("incubator", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("incubator"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.BOTH, 6)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.IncubatorTile", true, 8, -1));
        l.add(new MachineSpec("auto_feeder", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("auto_feeder"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.IN, 10), port(SideType.ITEMS, Role.OUT, 1), port(SideType.FLUIDS, Role.IN, 1)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.AutoFeederTile", true, 8, -1));
        l.add(new MachineSpec("rabbit_hutch", "kitchen", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "mechanical", Power.NONE,
              List.of("rabbit_hutch"),
              List.of("farm_device"), List.of(port(SideType.ITEMS, Role.IN, 3), port(SideType.ITEMS, Role.OUT, 6)), false, true, null, false, List.of(),
              null, null, "farm", false, "none", "farm.device.HutchTile", true, 8, -1));
        l.add(new MachineSpec("caravan_relay", "logistics", Kind.SERVICE, Form.COMPACT, TierMode.AGE, "bronze", Power.NONE,
              List.of("caravan_relay"),
              List.of("caravan_relay"), List.of(port(SideType.ITEMS, Role.IN, 4)), false, false, null, false, List.of(),
              null, null, "caravan_relay", false, "none", "caravan.RelayUnit", true, 8, -1));
    }
}
