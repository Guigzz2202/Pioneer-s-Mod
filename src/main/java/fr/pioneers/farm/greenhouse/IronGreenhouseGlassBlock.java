package fr.pioneers.farm.greenhouse;

import fr.pioneers.core.common.block.Connections;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;

/** 1.0.5-beta (lot 3) : vitre de serre (iron) ; famille de textures connectees propre a sa matiere. */
public class IronGreenhouseGlassBlock extends GreenhouseGlassBlock {

    public static final TagKey<Block> FAMILY = Connections.family("iron_greenhouse_glass");

    public IronGreenhouseGlassBlock(Properties properties) {
        super(properties, GreenhouseMaterial.IRON);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return FAMILY;
    }
}
