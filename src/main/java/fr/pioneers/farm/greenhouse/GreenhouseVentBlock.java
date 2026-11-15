package fr.pioneers.farm.greenhouse;

import fr.pioneers.Pioneers;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.TrapDoorBlock;
import net.minecraft.world.level.block.state.properties.BlockSetType;

/**
 * 1.0.5-beta (lot 3) : TRAPPE VITREE D'AERATION : une trappe (clic droit, redstone) dont le panneau est une vitre de serre. Ouverte ou
 * fermee, elle COMPTE COMME PAROI (la serre reste formee : l'aeration ne change pas le climat interieur, toujours ideal). Les deux
 * matieres s'ouvrent a la main.
 */
public class GreenhouseVentBlock extends TrapDoorBlock implements GreenhouseWall {

    public static final BlockSetType TREATED_SET = BlockSetType.register(new BlockSetType(Pioneers.MODID + ":treated_greenhouse", true,
          SoundType.WOOD, SoundEvents.WOODEN_DOOR_CLOSE, SoundEvents.WOODEN_DOOR_OPEN, SoundEvents.WOODEN_TRAPDOOR_CLOSE,
          SoundEvents.WOODEN_TRAPDOOR_OPEN, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_OFF, SoundEvents.WOODEN_PRESSURE_PLATE_CLICK_ON,
          SoundEvents.WOODEN_BUTTON_CLICK_OFF, SoundEvents.WOODEN_BUTTON_CLICK_ON));
    public static final BlockSetType IRON_SET = BlockSetType.register(new BlockSetType(Pioneers.MODID + ":iron_greenhouse", true,
          SoundType.METAL, SoundEvents.IRON_DOOR_CLOSE, SoundEvents.IRON_DOOR_OPEN, SoundEvents.IRON_TRAPDOOR_CLOSE,
          SoundEvents.IRON_TRAPDOOR_OPEN, SoundEvents.METAL_PRESSURE_PLATE_CLICK_OFF, SoundEvents.METAL_PRESSURE_PLATE_CLICK_ON,
          SoundEvents.STONE_BUTTON_CLICK_OFF, SoundEvents.STONE_BUTTON_CLICK_ON));

    private final GreenhouseMaterial material;

    public GreenhouseVentBlock(Properties properties, GreenhouseMaterial material) {
        super(properties, material == GreenhouseMaterial.IRON ? IRON_SET : TREATED_SET);
        this.material = material;
    }

    @Override
    public GreenhouseMaterial greenhouseMaterial() {
        return material;
    }
}
