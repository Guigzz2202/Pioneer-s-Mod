package fr.pioneers.industry.multi;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0 : cadre de structure (acier, aluminium, titane, tungstene : un par tier). Charpente ajouree en tiges des multiblocs « construits »
 * (pieds des foreuses et des puits, chevalet et balancier de la pompe) ; donne son tier au multibloc comme une carcasse (le plus faible compte).
 * Modele : tools/gc/industry/frames.py.
 */
public class StructureFrameBlock extends Block {

    public final int tier;

    public StructureFrameBlock(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    // 0.20.46 : charpente ajouree : laisse passer toute la lumiere (ciel compris) et n'assombrit pas ses voisins (sinon tiges noires)
    @Override
    public boolean propagatesSkylightDown(BlockState state, BlockGetter level, BlockPos pos) {
        return true;
    }

    @Override
    public int getLightBlock(BlockState state, BlockGetter level, BlockPos pos) {
        return 0;
    }

    @Override
    public float getShadeBrightness(BlockState state, BlockGetter level, BlockPos pos) {
        return 1.0F;
    }

    @Override
    public boolean useShapeForLightOcclusion(BlockState state) {
        return false;
    }
}
