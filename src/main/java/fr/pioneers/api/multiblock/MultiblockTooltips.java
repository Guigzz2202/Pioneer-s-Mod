package fr.pioneers.api.multiblock;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.multi.CasingBlock;
import fr.pioneers.industry.multi.ConcreteShellBlock;
import fr.pioneers.industry.multi.ControllerBlock;
import fr.pioneers.industry.multi.IndustrialGlassBlock;
import fr.pioneers.industry.multi.ModuleBlock;
import fr.pioneers.industry.multi.PartBlock;
import fr.pioneers.industry.multi.PortBlock;
import fr.pioneers.industry.multi.ShapeTemplate;
import fr.pioneers.industry.multi.SheetMetalBlock;
import fr.pioneers.industry.multi.Signature;
import fr.pioneers.industry.multi.StructureFrameBlock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.block.Block;

/**
 * API Multibloc (lot 5) : INFOBULLES COMMUNES des multiblocs, tirees des fiches et des gabarits (code commun : le banc des registres
 * l'appelle hors du jeu) :
 * <ul>
 *     <li>CONTROLEUR (sous Maj, apres les lignes de sa fiche machine) : taille et nombre de blocs du plan, « plan : Maj + clic droit ou
 *     onglet Structure » ;</li>
 *     <li>PIECES (carcasses, verre, toles, cadres, coque en beton, pieces signature, modules) : leur role, « fixe le tier » pour les pieces a
 *     tier, nombre de multiblocs qui les utilisent ;</li>
 *     <li>PORTS : role et regle commune (prete au controleur forme qui le contient ; sens a la cle).</li>
 * </ul>
 */
public final class MultiblockTooltips {

    private static final String K = "tooltip.pioneers.multiblock.";
    private static Map<String, Integer> usage;

    private MultiblockTooltips() {
    }

    /** Lignes du controleur (dans les details sous Maj de l'infobulle standard). */
    public static void controller(Tooltips t, Block block) {
        if (!(block instanceof ControllerBlock controller)) {
            return;
        }
        int[] s = SpecBlueprint.size(controller.machine);
        if (s[3] > 0 && !controller.machine.freeform) {
            t.spec(K + "size", s[0], s[1], s[2], s[3]);
        }
        t.hint(K + "plan");
    }

    /** Infobulle d'une piece ou d'un port de multibloc ; faux si le bloc n'en est pas un. */
    public static boolean piece(Block block, List<Component> lines) {
        String role = role(block);
        if (role == null) {
            return false;
        }
        Tooltips t = Tooltips.event(lines);
        t.note(K + "role", Component.translatable(K + "role." + role));
        if (block instanceof PortBlock) {
            t.hint(K + "port_rule");
            return true;
        }
        int tier = Role.tierOf(block.defaultBlockState());
        if (block instanceof CasingBlock || block instanceof SheetMetalBlock || block instanceof StructureFrameBlock || block instanceof IndustrialGlassBlock) {
            t.hint(K + "sets_tier", Tooltips.tierName(tier));
        }
        int n = usedBy(role, block);
        if (n > 0) {
            t.hint(K + "used_by", n);
        }
        return true;
    }

    /** Role d'un bloc de multibloc (cle de langue), ou null. */
    static String role(Block block) {
        if (block instanceof PortBlock) {
            return "port";
        }
        if (block instanceof CasingBlock c) {
            return c.scorched() ? null : "casing";
        }
        if (block instanceof IndustrialGlassBlock) {
            return "glass";
        }
        if (block instanceof SheetMetalBlock) {
            return "sheet";
        }
        if (block instanceof StructureFrameBlock) {
            return "frame";
        }
        if (block instanceof ConcreteShellBlock) {
            return "concrete";
        }
        if (block instanceof PartBlock) {
            return "part";
        }
        if (block instanceof ModuleBlock) {
            return "module";
        }
        return null;
    }

    /** Nombre de multiblocs industriels dont le gabarit a des cases de ce role (module : de cette sorte ; piece : exigee ou de paroi). */
    static synchronized int usedBy(String role, Block block) {
        if (usage == null) {
            Map<String, Integer> u = new HashMap<>();
            for (IndustryMachine m : IndustryMachine.values()) {
                if (!m.industrial) {
                    continue;
                }
                u.merge("casing", 1, Integer::sum);
                u.merge("glass", 1, Integer::sum);
                if (m.module != null) {
                    u.merge("module/" + m.module.id(), 1, Integer::sum);
                }
                for (Signature s : m.signatures) {
                    u.merge("part/" + s.part().id(), 1, Integer::sum);
                }
                boolean sheet = false, frame = false;
                for (ShapeTemplate t : m.freeform ? List.<ShapeTemplate>of() : ShapeTemplate.of(m).subList(0, 1)) {
                    for (int cell : t.cells()) {
                        sheet |= ShapeTemplate.sheet(cell);
                        frame |= ShapeTemplate.frame(cell);
                    }
                }
                if (sheet) {
                    u.merge("sheet", 1, Integer::sum);
                }
                if (frame) {
                    u.merge("frame", 1, Integer::sum);
                }
                if (m.shape == fr.pioneers.industry.multi.IndustryShape.HYPERBOLOID) {
                    u.merge("concrete", 1, Integer::sum);
                }
            }
            usage = u;
        }
        if (block instanceof ModuleBlock mod) {
            return usage.getOrDefault("module/" + mod.module.id(), 0);
        }
        if (block instanceof PartBlock part) {
            return usage.getOrDefault("part/" + part.kind.id(), 0);
        }
        return usage.getOrDefault(role, 0);
    }
}
