package fr.pioneers.industry.multi;

import fr.pioneers.api.multiblock.Role;
import fr.pioneers.api.multiblock.StructureMatcher;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.Tiers;
import fr.pioneers.industry.construction.CleanroomGlassBlock;
import fr.pioneers.industry.construction.CleanroomPanelBlock;
import fr.pioneers.industry.construction.FireboxWallBlock;
import fr.pioneers.industry.construction.HeatingCoilBlock;
import fr.pioneers.industry.construction.PiConstruction;
import fr.pioneers.industry.construction.StackedColumnBlock;
import fr.pioneers.industry.construction.TwinConnectedBlock;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.transport.CableTier;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 24) : ROLES PROPRES DES GABARITS NBT (structures de Guillaume, {@link GeneratedNbtShapes}) : les cases que le gabarit
 * d'avant n'avait pas (verre exige, paroi de foyer, bobine de chauffe, blocs empilables, salle blanche, baie de serveurs, substrat, piece
 * exigee). Codes {@link ShapeTemplate#GLASS_CELL}... (bits de zone d'une case a role propre, a partir de {@link ShapeTemplate#NBT_FIRST}).
 * Chaque role accepte TOUS LES TIERS de sa sorte (le plus faible fixe le tier du multibloc, comme une carcasse) ; la paroi de salle blanche
 * accepte aussi un port (ports des machines de salle blanche). Meme regle pour la validation ({@code StructureMatcher}), le decompte de
 * l'onglet Structure, le plan projete ({@link IndustrialBlueprint}) et le banc des multiblocs.
 */
public final class NbtRoles {

    private NbtRoles() {
    }

    /** Le bloc convient-il au role ? (classe du bloc, ou tag du role : piece passive au tier MKI) */
    public static boolean fits(int code, BlockState state, IndustryMachine machine) {
        Block b = state.getBlock();
        return switch (code) {
            case ShapeTemplate.GLASS_CELL -> Role.GLASS.accepts(state);
            case ShapeTemplate.FIREBOX -> b instanceof FireboxWallBlock || Role.FIREBOX.tagOnly(state);
            case ShapeTemplate.COIL -> b instanceof HeatingCoilBlock || Role.COIL.tagOnly(state);
            case ShapeTemplate.ROTOR -> stacked(b, "turbine_rotor");
            case ShapeTemplate.DRILL -> stacked(b, "drill_string");
            case ShapeTemplate.PUMP -> stacked(b, "column_pump");
            case ShapeTemplate.CLEANROOM -> b instanceof CleanroomPanelBlock || b instanceof PortBlock || Role.CLEANROOM.tagOnly(state);
            case ShapeTemplate.CLEANROOM_GLASS -> b instanceof CleanroomGlassBlock;
            case ShapeTemplate.FILTER -> b == PiConstruction.CLEANROOM_FILTER.getBlock();
            case ShapeTemplate.MODULE -> b instanceof ModuleBlock m && (machine.module == null || m.module == machine.module)
                                         || machine.module != null && Role.module(machine.module).tagOnly(state);
            case ShapeTemplate.SUBSTRATE -> b == PiBlocks.HYDROPONIC_SUBSTRATE.getBlock() || Role.SUBSTRATE.tagOnly(state);
            case ShapeTemplate.PART -> b instanceof PartBlock p && p.kind == PartBlock.Kind.DILUTION_REFRIGERATOR;
            default -> false;
        };
    }

    /** Bloc empilable MKI a MKIV de la famille (le train de forage en bronze, sans tier MK, n'entre pas dans un multibloc industriel). */
    private static boolean stacked(Block b, String family) {
        return b instanceof StackedColumnBlock c && c.family.equals(family) && c.tier != null;
    }

    /** Tier (0 MKI .. 3 MKIV) porte par le bloc a cette case, ou -1 si le role n'a pas de tier (salle blanche, module, substrat, piece). */
    public static int tier(int code, BlockState state) {
        Block b = state.getBlock();
        return switch (code) {
            case ShapeTemplate.GLASS_CELL -> Role.tierOf(state);
            case ShapeTemplate.FIREBOX, ShapeTemplate.COIL -> b instanceof TwinConnectedBlock t ? t.tier.ordinal() : 0;
            case ShapeTemplate.ROTOR, ShapeTemplate.DRILL, ShapeTemplate.PUMP -> b instanceof StackedColumnBlock c && c.tier != null ? c.tier.ordinal() : 0;
            default -> -1;
        };
    }

    /** Bloc conseille par le plan (tier MKI). */
    public static Block ghost(int code, IndustryMachine machine) {
        return switch (code) {
            case ShapeTemplate.GLASS_CELL -> PiIndustry.GLASS.getBlock();
            case ShapeTemplate.FIREBOX -> PiConstruction.FIREBOXES.get(CableTier.BASIC).getBlock();
            case ShapeTemplate.COIL -> PiConstruction.HEATING_COILS.get(CableTier.BASIC).getBlock();
            case ShapeTemplate.ROTOR -> PiConstruction.STACKED.get("turbine_rotor").get(CableTier.BASIC).getBlock();
            case ShapeTemplate.DRILL -> PiConstruction.STACKED.get("drill_string").get(CableTier.BASIC).getBlock();
            case ShapeTemplate.PUMP -> PiConstruction.STACKED.get("column_pump").get(CableTier.BASIC).getBlock();
            case ShapeTemplate.CLEANROOM -> PiConstruction.CLEANROOM_CASING.getBlock();
            case ShapeTemplate.CLEANROOM_GLASS -> PiConstruction.CLEANROOM_GLASS.getBlock();
            case ShapeTemplate.FILTER -> PiConstruction.CLEANROOM_FILTER.getBlock();
            case ShapeTemplate.MODULE -> machine.module != null && PiIndustry.MODULES.containsKey(machine.module) ? PiIndustry.MODULES.get(machine.module).getBlock()
                  : PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
            case ShapeTemplate.SUBSTRATE -> PiBlocks.HYDROPONIC_SUBSTRATE.getBlock();
            case ShapeTemplate.PART -> PiIndustry.PARTS.get(PartBlock.Kind.DILUTION_REFRIGERATOR).getBlock();
            default -> PiIndustry.CASINGS.get(CableTier.BASIC).getBlock();
        };
    }

    /** Autres blocs acceptes par le plan a cette case (tous les tiers ; paroi de salle blanche : les ports). */
    public static Set<Block> accepted(int code) {
        Set<Block> out = new HashSet<>();
        switch (code) {
            case ShapeTemplate.GLASS_CELL -> PiIndustry.GLASSES.values().forEach(g -> out.add(g.getBlock()));
            case ShapeTemplate.FIREBOX -> PiConstruction.FIREBOXES.values().forEach(g -> out.add(g.getBlock()));
            case ShapeTemplate.COIL -> PiConstruction.HEATING_COILS.values().forEach(g -> out.add(g.getBlock()));
            case ShapeTemplate.ROTOR -> PiConstruction.STACKED.get("turbine_rotor").values().forEach(g -> out.add(g.getBlock()));
            case ShapeTemplate.DRILL -> PiConstruction.STACKED.get("drill_string").values().forEach(g -> out.add(g.getBlock()));
            case ShapeTemplate.PUMP -> PiConstruction.STACKED.get("column_pump").values().forEach(g -> out.add(g.getBlock()));
            case ShapeTemplate.CLEANROOM -> {
                PiIndustry.PORTS.values().forEach(p -> out.add(p.getBlock()));
                out.add(PiConstruction.CLEANROOM_CASING.getBlock());
            }
            default -> {
            }
        }
        return out;
    }

    /** Cle de langue du bloc attendu (erreur commune). */
    public static String expectedKey(int code) {
        return switch (code) {
            case ShapeTemplate.GLASS_CELL -> "block.pioneers.industrial_glass";
            case ShapeTemplate.FIREBOX -> "block.pioneers.firebox_basic";
            case ShapeTemplate.COIL -> "block.pioneers.heating_coil_basic";
            case ShapeTemplate.ROTOR -> "block.pioneers.turbine_rotor_basic";
            case ShapeTemplate.DRILL -> "block.pioneers.drill_string_basic";
            case ShapeTemplate.PUMP -> "block.pioneers.column_pump_basic";
            case ShapeTemplate.CLEANROOM -> "block.pioneers.cleanroom_casing";
            case ShapeTemplate.CLEANROOM_GLASS -> "block.pioneers.cleanroom_glass";
            case ShapeTemplate.FILTER -> "block.pioneers.cleanroom_filter";
            case ShapeTemplate.MODULE -> "block.pioneers.server_rack_module";
            case ShapeTemplate.SUBSTRATE -> "block.pioneers.hydroponic_substrate";
            case ShapeTemplate.PART -> "block.pioneers.industrial_dilution_refrigerator";
            default -> "block.pioneers.industrial_casing_basic";
        };
    }

    /** Role ({@link Role#name()}) d'une case de ce code (erreur commune). */
    public static String roleName(int code) {
        return switch (code) {
            case ShapeTemplate.GLASS_CELL -> Role.GLASS.name();
            case ShapeTemplate.FIREBOX -> Role.FIREBOX.name();
            case ShapeTemplate.COIL -> Role.COIL.name();
            case ShapeTemplate.ROTOR, ShapeTemplate.DRILL, ShapeTemplate.PUMP -> Role.STACKED.name();
            case ShapeTemplate.CLEANROOM, ShapeTemplate.CLEANROOM_GLASS, ShapeTemplate.FILTER -> Role.CLEANROOM.name();
            case ShapeTemplate.MODULE -> Role.MODULE.name();
            case ShapeTemplate.SUBSTRATE -> Role.SUBSTRATE.name();
            case ShapeTemplate.PART -> Role.PART.name();
            default -> Role.CASING.name();
        };
    }

    /** Categorie du decompte de l'onglet Structure ({@link StructureMatcher#SHELL}...). */
    public static int category(int code) {
        return switch (code) {
            case ShapeTemplate.ROTOR, ShapeTemplate.DRILL, ShapeTemplate.PUMP, ShapeTemplate.MODULE, ShapeTemplate.SUBSTRATE -> StructureMatcher.INTERIOR;
            case ShapeTemplate.PART -> StructureMatcher.PARTS;
            default -> StructureMatcher.SHELL;
        };
    }

    /** 1.0.4-beta (lot 26) : habillage d'interface des machines de salle blanche (textures {@code gui/machine/*_cleanroom}, claires). */
    public static final String CLEANROOM_GUI = "cleanroom";

    /**
     * Habillage de l'interface d'un controleur industriel ({@code ControllerTile#guiTier}) : 1.0.4-beta (lot 26, Guillaume : « l'interface
     * doit etre blanche, on imagine un "tier" special ») : machine de salle blanche ({@link GeneratedNbtShapes.Shape#cleanroom}) =
     * {@link #CLEANROOM_GUI} ; sinon l'id du tier ; cote client ({@code clientState} non nul), la structure n'est pas connue : le tier peint
     * sur le controleur (0 = libre, 1..4).
     */
    public static String guiTier(String key, int tier, @Nullable BlockState clientState) {
        GeneratedNbtShapes.Shape shape = GeneratedNbtShapes.of(key);
        if (shape != null && shape.cleanroom()) {
            return CLEANROOM_GUI;
        }
        int t = tier;
        if (clientState != null && clientState.hasProperty(IndustryStyle.TIER) && clientState.getValue(IndustryStyle.TIER) > 0) {
            t = Math.min(3, clientState.getValue(IndustryStyle.TIER) - 1);
        }
        return Tiers.tier(t).id();
    }

    /** Lettre de la table generee -> code de case (0 : enveloppe ordinaire, carcasse). */
    static int code(char letter) {
        return switch (letter) {
            case 'G' -> ShapeTemplate.GLASS_CELL;
            case 'B' -> ShapeTemplate.FIREBOX;
            case 'H' -> ShapeTemplate.COIL;
            case 'R' -> ShapeTemplate.ROTOR;
            case 'D' -> ShapeTemplate.DRILL;
            case 'P' -> ShapeTemplate.PUMP;
            case 'W' -> ShapeTemplate.CLEANROOM;
            case 'V' -> ShapeTemplate.CLEANROOM_GLASS;
            case 'L' -> ShapeTemplate.FILTER;
            case 'M' -> ShapeTemplate.MODULE;
            case 'U' -> ShapeTemplate.SUBSTRATE;
            case 'Z' -> ShapeTemplate.PART;
            default -> 0;
        };
    }
}
