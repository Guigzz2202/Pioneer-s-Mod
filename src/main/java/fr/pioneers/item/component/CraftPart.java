package fr.pioneers.item.component;

import fr.pioneers.space.mining.MiningMaterial;
import java.util.Locale;

/**
 * Composants des machines mobiles : robot agricole (lunarium) et rover minier (meteoritite).
 */
public enum CraftPart {
    ROBOT_CHASSIS(MiningMaterial.LUNARIUM),
    ROBOT_ARM(MiningMaterial.LUNARIUM),
    ROBOT_WHEEL(MiningMaterial.LUNARIUM),
    ROBOT_CORE(MiningMaterial.LUNARIUM),
    ROVER_CHASSIS(MiningMaterial.METEORITITE),
    ROVER_WHEEL(MiningMaterial.METEORITITE),
    ROVER_DRILL_ARM(MiningMaterial.METEORITITE),
    ROVER_CONTROL_UNIT(MiningMaterial.METEORITITE);

    public final MiningMaterial material;

    CraftPart(MiningMaterial material) {
        this.material = material;
    }

    public String id() {
        return name().toLowerCase(Locale.ROOT);
    }
}
