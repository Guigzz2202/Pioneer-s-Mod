package fr.pioneers.logistics.rail.block;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.logistics.rail.PiRail;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.0 : grue a eau : remplit l'eau de la locomotive a vapeur ou du tender arrete a cote (250 mB/t). Sa reserve (16 000 mB) se remplit
 * par les conduites et les cables a fluides, ou toute seule si elle est posee sur une source d'eau (100 mB/t).
 * <p>
 * Interface : 4 vehicule servi (0/1), 5 eau versee depuis la pose (mB).
 * <p>
 * API Machine (lot 8c) : logique dans {@link WaterCraneUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class WaterCraneTile extends MachineTile {

    public static final int RATE = WaterCraneUnit.RATE, SPRING = WaterCraneUnit.SPRING;
    /** API Machine (lot 4) : valeurs NOMMEES (lot 8c : declarees par l'unite, memes cles et indices). */
    public static final UiValues.Key SERVING = WaterCraneUnit.SERVING, POURED = WaterCraneUnit.POURED;

    /** API Machine (lot 8c) : la logique (reserve, source, versement) est dans l'unite portee. */
    private final WaterCraneUnit unit;

    public WaterCraneTile(BlockPos pos, BlockState state) {
        super(PiRail.WATER_CRANE_TILE.get(), pos, state, 0, 0);
        unit = carry(new WaterCraneUnit(access()));
    }

    /** Toutes les faces ouvertes aux cables et coffres des la pose. */
    @Override
    protected SideMode initialMode() {
        return SideMode.INPUT;
    }

    @Override
    protected void tick() {
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.postParts(1, false, false);
    }
}
