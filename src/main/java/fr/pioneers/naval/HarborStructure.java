package fr.pioneers.naval;

import fr.pioneers.industry.multi.Structure;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.23 : validation du port, comme les autres multiblocs : on compare le monde au plan {@link HarborLayout} du tier de la capitainerie,
 * case par case, et on rend la premiere case fautive. Le bassin s'ouvre derriere la facade de la capitainerie.
 * <ul>
 *     <li>chaque case QUAY : dalle de quai ou equipement de quai ({@link #quayPart}) ; 0.20.25 : sur {@link HarborLayout#HEIGHT} blocs de haut
 *     (le mur du quai descend dans l'eau), sous la capitainerie aussi ;</li>
 *     <li>chaque case WATER : eau navigable a la surface et sur {@link HarborLayout#WATER_DEPTH} blocs, rien de solide au-dessus. La
 *     surface est a la hauteur de la capitainerie (quai pose dans l'eau) ou un bloc dessous (quai pose sur l'eau). Eau brulante
 *     (lave des mondes chauds) : capitainerie MKIV seulement.</li>
 * </ul>
 */
public final class HarborStructure {

    /** Piece de quai presente (pour relier les equipements meme si le U n'est pas complet). */
    public record Part(BlockPos pos, HarborLayout.Cell cell) {}

    /**
     * Resultat : etat du poste ({@code OK}, {@code NOT_U} = piece de quai manquante, {@code NO_WATER} = case du bassin sans eau),
     * premiere case fautive, hauteur de la surface de l'eau, pieces presentes.
     */
    public record Result(Harbor.Berth berth, @Nullable BlockPos error, int waterY, List<Part> parts) {

        public boolean formed() {
            return berth == Harbor.Berth.OK;
        }
    }

    private HarborStructure() {
    }

    /** Validation du plan (monde reel). */
    public static Result validate(Level level, BlockPos controller, Direction basin, int tier) {
        return validate(Structure.View.of(level), controller, basin, tier);
    }

    /** Dalle de quai ou equipement de quai. */
    public static boolean quayPart(BlockState st) {
        return st.getBlock() instanceof QuayBlock || st.getBlock() instanceof HarborEquipmentBlock;
    }

    public static Result validate(Structure.View level, BlockPos controller, Direction basin, int tier) {
        List<Part> parts = new ArrayList<>();
        BlockPos error = null;
        Harbor.Berth berth = Harbor.Berth.OK;
        for (HarborLayout.Cell cell : HarborLayout.cells(tier)) {
            if (cell.role() == HarborLayout.Role.WATER) {
                continue;
            }
            BlockPos top = HarborLayout.at(controller, basin, cell.x(), cell.z());
            //0.20.25 : murs de quai de HarborLayout.HEIGHT blocs (sous la capitainerie aussi)
            for (int dy = 0; dy < HarborLayout.HEIGHT; dy++) {
                if (dy == 0 && cell.role() == HarborLayout.Role.CONTROLLER) {
                    continue;
                }
                BlockPos p = top.below(dy);
                if (!level.isLoaded(p)) {
                    return new Result(Harbor.Berth.UNKNOWN, p, controller.getY() - 1, parts);
                }
                if (quayPart(level.getBlockState(p))) {
                    if (dy == 0) {
                        parts.add(new Part(p, cell));
                    }
                } else if (error == null) {
                    error = p;
                    berth = Harbor.Berth.NOT_U;
                }
            }
        }
        BlockPos first = HarborLayout.at(controller, basin, 0, 1);
        int waterY = WaterChart.navigableFluid(level.getFluidState(first)) ? controller.getY() : controller.getY() - 1;
        if (error == null) {
            for (HarborLayout.Cell cell : HarborLayout.cells(tier)) {
                if (cell.role() != HarborLayout.Role.WATER) {
                    continue;
                }
                BlockPos c = HarborLayout.at(controller, basin, cell.x(), cell.z());
                BlockPos p = new BlockPos(c.getX(), waterY, c.getZ());
                if (!level.isLoaded(p)) {
                    return new Result(Harbor.Berth.UNKNOWN, p, waterY, parts);
                }
                if (!water(level, p) || tier < 3 && level.getFluidState(p).is(WaterChart.NAVIGABLE_HOT)) {
                    error = p;
                    berth = Harbor.Berth.NO_WATER;
                    break;
                }
            }
        }
        return new Result(berth, error, waterY, parts);
    }

    /** Colonne d'eau navigable sous {@code surface} (profondeur exigee) et rien de solide au-dessus. */
    private static boolean water(Structure.View level, BlockPos surface) {
        for (int d = 0; d < HarborLayout.WATER_DEPTH; d++) {
            if (!WaterChart.navigableFluid(level.getFluidState(surface.below(d)))) {
                return false;
            }
        }
        BlockState above = level.getBlockState(surface.above());
        return above.isAir() || above.canBeReplaced() && above.getFluidState().isEmpty();
    }
}
