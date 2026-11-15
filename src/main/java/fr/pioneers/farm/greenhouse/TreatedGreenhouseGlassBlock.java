package fr.pioneers.farm.greenhouse;

import fr.pioneers.core.common.block.Connections;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** 1.0.5-beta (lot 3) : vitre de serre (treated) ; famille de textures connectees propre a sa matiere. */
public class TreatedGreenhouseGlassBlock extends GreenhouseGlassBlock {

    public static final TagKey<Block> FAMILY = Connections.family("treated_greenhouse_glass");

    public TreatedGreenhouseGlassBlock(Properties properties) {
        super(properties, GreenhouseMaterial.TREATED);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return FAMILY;
    }
}
