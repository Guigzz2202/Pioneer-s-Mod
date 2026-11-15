package fr.pioneers.industry.multi;

import fr.pioneers.compute.ComputeCore;
import fr.pioneers.core.Texts;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.Tiers;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 32, decision de Guillaume du 09/11 : « Garder, masquer partout ») : AFFICHAGE des machines de salle blanche (graveur
 * optique industriel, usine de semi-conducteurs, centre de donnees, ordinateur quantique : structure sans piece a tier,
 * {@link GeneratedNbtShapes.Shape#cleanroom()}). Leur tier FIXE ({@link GeneratedNbtShapes.Shape#fixedTier()}, T:industry/nbt_shapes.py
 * FIXED_TIER) regle toujours le jeu (parallele, puissance, vitesse, chaleur, recettes permises, GOPS et FE) mais ne s'affiche JAMAIS :
 * guide, JEI, infobulles et interface montrent ses EFFETS, calcules ici avec les memes regles que le jeu ({@link Tiers}, {@code MultiProcess},
 * {@link ComputeCore}). Code commun (infobulles, JEI, bancs). Controle : tools/check_p104_32.py.
 */
public final class Cleanroom {

    private Cleanroom() {
    }

    /** Machine de salle blanche (structure a parois de salle blanche et tier fixe) ? */
    public static boolean is(@Nullable IndustryMachine machine) {
        return level(machine) >= 0;
    }

    /** Niveau fixe (interne, jamais affiche) d'une machine de salle blanche, -1 pour toute autre machine. */
    public static int level(@Nullable IndustryMachine machine) {
        GeneratedNbtShapes.Shape s = machine == null ? null : GeneratedNbtShapes.of(machine.key);
        return s != null && s.cleanroom() ? s.fixedTier() : -1;
    }

    /** Machine de salle blanche dont le controleur a cet identifiant ({@code pioneers:semiconductor_fab} ou le chemin seul), ou null. */
    @Nullable
    public static IndustryMachine byController(String id) {
        if (byController == null) {
            Map<String, IndustryMachine> map = new HashMap<>();
            for (IndustryMachine m : IndustryMachine.values()) {
                if (m.industrial && is(m)) {
                    map.put(m.multiId(), m);
                }
            }
            byController = map;
        }
        return byController.get(id.indexOf(':') >= 0 ? id.substring(id.indexOf(':') + 1) : id);
    }

    private static Map<String, IndustryMachine> byController;

    /** Operations a la fois (meme regle que {@code ControllerTile#parallel}). */
    public static int parallel(IndustryMachine machine) {
        return Tiers.parallelCap(level(machine));
    }

    /** FE/t d'UNE operation (meme regle que {@code MultiProcess#opPower(1)}). */
    public static long opPower(IndustryMachine machine) {
        return Math.round(Tiers.power(level(machine)) * Tiers.INDUSTRIAL_ENERGY / Tiers.INDUSTRIAL_TIME);
    }

    /** Duree d'une operation (ticks) d'une recette dans cette salle blanche (meme regle que {@code MultiProcess}). */
    public static int ticks(IndustryMachine machine, int recipeTime, int recipeTier) {
        return Tiers.ticks(recipeTime, level(machine), recipeTier, true);
    }

    /** La salle blanche peut-elle faire une recette de ce niveau ? (statut « hors de portee » sinon). */
    public static boolean reachable(IndustryMachine machine, int recipeTier) {
        return recipeTier <= level(machine);
    }

    /** Centre de donnees : GOPS d'une baie a pleine charge ({@link ComputeCore} : 64 x 2^niveau). */
    public static long rackGops(IndustryMachine machine) {
        return Math.round(ComputeCore.RACK_GOPS * (1 << level(machine)));
    }

    /** Centre de donnees : FE/t d'une baie a pleine charge ({@link ComputeCore} : 512 x 4^niveau ; un dixieme a vide). */
    public static long rackPower(IndustryMachine machine) {
        return ComputeCore.RACK_FE << 2 * level(machine);
    }

    /** Ordinateur quantique : QOPS d'une matrice ({@link ComputeCore} : 1 x 2^niveau). */
    public static long matrixQops(IndustryMachine machine) {
        return Math.round(ComputeCore.MATRIX_QOPS * (1 << level(machine)));
    }

    /** Ordinateur quantique : FE/t d'une matrice ({@link ComputeCore} : 256 x 4^niveau). */
    public static long matrixPower(IndustryMachine machine) {
        return ComputeCore.MATRIX_FE << 2 * level(machine);
    }

    /**
     * Ce qu'il faut savoir de la machine, en vraies valeurs et sans tier (infobulle du controleur, page « Multiblocs » de JEI) : operations a
     * la fois et energie d'une operation (graveur, usine), ou le rendement d'une baie (centre de donnees) ou d'une matrice (quantique).
     */
    public static List<Component> lines(IndustryMachine machine) {
        List<Component> out = new ArrayList<>();
        if (!is(machine)) {
            return out;
        }
        switch (machine.kind) {
            case COMPUTE -> out.add(Component.translatable("compute.pioneers.gui.rule_racks_cleanroom", Texts.grouped(rackGops(machine)),
                  Texts.grouped(rackPower(machine))));
            case QUANTUM -> out.add(Component.translatable("compute.pioneers.gui.rule_quantum_cleanroom", Texts.grouped(matrixQops(machine)),
                  Texts.grouped(matrixPower(machine))));
            default -> {
                out.add(Component.translatable("cleanroom.pioneers.parallel", parallel(machine)));
                out.add(Component.translatable("cleanroom.pioneers.op_power", Texts.grouped(opPower(machine))));
            }
        }
        return out;
    }
}
