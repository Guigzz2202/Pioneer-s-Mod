package fr.pioneers.core.common.projector;

import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.Nullable;

/** Plan projetable d'un multibloc (structures connues ou multiblocs industriels) : variantes, plan dans le monde, controleur, noms. */
public interface Blueprint {

    int variants();

    Map<BlockPos, ProjectedStructure.Ghost> layout(int variant, BlockPos anchor, Direction forward);

    Block controller();

    String getTranslationKey();

    @Nullable
    String getVariantKey(int variant);
}
