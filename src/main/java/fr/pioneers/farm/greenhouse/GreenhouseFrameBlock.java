package fr.pioneers.farm.greenhouse;

import net.minecraft.world.level.block.RotatedPillarBlock;

/**
 * 1.0.5-beta (lot 3) : CADRE DE SERRE : poteau ou angle opaque (bois traite cercle de fer, ou fer rivete) ; se pose dans l'axe voulu comme
 * une buche. Compte comme paroi de serre.
 */
public class GreenhouseFrameBlock extends RotatedPillarBlock implements GreenhouseWall {

    private final GreenhouseMaterial material;

    public GreenhouseFrameBlock(Properties properties, GreenhouseMaterial material) {
        super(properties);
        this.material = material;
    }

    @Override
    public GreenhouseMaterial greenhouseMaterial() {
        return material;
    }
}
