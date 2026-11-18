package fr.pioneers.api.multiblock;

import fr.pioneers.core.common.projector.Blueprint;
import fr.pioneers.core.common.projector.BlueprintBill;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.IndustrialBlueprint;
import java.util.List;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * API Multibloc (lot 5) : PLAN TIRE DE LA FORME de la fiche (plus aucun plan ecrit a la main pour un multibloc du moteur commun) : un
 * gabarit ({@link MultiblockSpec.Shape#TEMPLATE}) donne ses cases et leurs roles ({@code ShapeTemplate}), le plan conseille les blocs de
 * chaque role ({@link IndustrialBlueprint}) ; la forme libre propose des ponts. LA meme source sert a la projection (Maj + clic droit sur
 * le controleur, bouton « Afficher le plan » de l'onglet Structure), a la page JEI « Multiblocs » et a sa nomenclature ({@link BlueprintBill}),
 * au banc des multiblocs (chaque plan doit se former) et au decompte par role de l'onglet Structure ({@link StructureMatcher#census}).
 * Les multiblocs maison gardent leurs plans (lot 6).
 */
public final class SpecBlueprint {

    private static final java.util.Map<IndustryMachine, int[]> SIZES = new java.util.EnumMap<>(IndustryMachine.class);

    private SpecBlueprint() {
    }

    /**
     * Taille du plan (premiere variante) : {longueur, largeur, hauteur, blocs a poser} ; memes nombres que l'en-tete de la page JEI
     * « Multiblocs » (cases du plan qui portent un bloc, controleur compris).
     */
    public static synchronized int[] size(IndustryMachine machine) {
        return SIZES.computeIfAbsent(machine, m -> {
            Blueprint b = IndustrialBlueprint.of(m);
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE,
                  maxZ = Integer.MIN_VALUE, n = 0;
            for (var e : b.layout(0, net.minecraft.core.BlockPos.ZERO, net.minecraft.core.Direction.NORTH).entrySet()) {
                if (e.getValue().state() == null) {
                    continue;
                }
                var p = e.getKey();
                minX = Math.min(minX, p.getX());
                minY = Math.min(minY, p.getY());
                minZ = Math.min(minZ, p.getZ());
                maxX = Math.max(maxX, p.getX());
                maxY = Math.max(maxY, p.getY());
                maxZ = Math.max(maxZ, p.getZ());
                n++;
            }
            if (n == 0) {
                return new int[]{0, 0, 0, 0};
            }
            int sx = maxX - minX + 1, sz = maxZ - minZ + 1;
            return new int[]{Math.max(sx, sz), Math.min(sx, sz), maxY - minY + 1, n};
        });
    }

    /** Fournisseur des projections : les plans du controleur {@code block} tires de sa fiche (vide hors du moteur commun industriel). */
    public static List<Blueprint> forBlock(Block block) {
        Blueprint b = of(block);
        return b == null ? List.of() : List.of(b);
    }

    /** Plan du multibloc dont {@code block} est le controleur, ou null. */
    @Nullable
    public static Blueprint of(Block block) {
        if (!(block instanceof ControllerBlock controller)) {
            return null;
        }
        MultiblockSpec spec = MultiblockRegistry.of(controller.machine);
        return spec == null ? null : of(spec, controller.machine);
    }

    /** Plan d'une fiche (gabarit ou forme libre d'une machine du catalogue industriel). */
    @Nullable
    public static Blueprint of(MultiblockSpec spec, IndustryMachine machine) {
        return switch (spec.shape()) {
            case TEMPLATE, FREEFORM -> IndustrialBlueprint.of(machine);
            default -> null;
        };
    }
}
