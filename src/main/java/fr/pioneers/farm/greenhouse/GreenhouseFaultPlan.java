package fr.pioneers.farm.greenhouse;

import fr.pioneers.common.projector.Blueprint;
import fr.pioneers.common.projector.ProjectedStructure.Ghost;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 3) : PLAN DES FAUTES d'une serre non formee (Maj + clic droit sur le regulateur) : une vitre de la matiere de la serre
 * en fantome a chaque case fautive (le trou d'une fuite, chaque bloc qui n'est pas un bloc de serre dans la paroi ou le toit). Le rendu des
 * plans montre le trou en vitre transparente et les blocs fautifs en rouge ; en creatif, le regulateur en main bouche tout d'un coup.
 * Cases relatives au regulateur (l'ancre du plan est le regulateur : il n'y figure pas).
 */
public final class GreenhouseFaultPlan implements Blueprint {

    private final Set<BlockPos> offsets;
    private final GreenhouseMaterial material;
    private final Block controller;

    public GreenhouseFaultPlan(Set<BlockPos> offsets, GreenhouseMaterial material, Block controller) {
        this.offsets = Set.copyOf(offsets);
        this.material = material;
        this.controller = controller;
    }

    @Override
    public int variants() {
        return 1;
    }

    @Override
    public Map<BlockPos, Ghost> layout(int variant, BlockPos anchor, Direction forward) {
        Map<BlockPos, Ghost> out = new LinkedHashMap<>();
        Ghost g = new Ghost(PiGreenhouse.glass(material).defaultBlockState(), PiGreenhouse.wallBlocks());
        for (BlockPos o : offsets) {
            out.put(anchor.offset(o), g);
        }
        return out;
    }

    @Override
    public Block controller() {
        return controller;
    }

    @Override
    public String getTranslationKey() {
        return "projector.pioneers.structure.greenhouse_faults";
    }

    @Nullable
    @Override
    public String getVariantKey(int variant) {
        return null;
    }
}
