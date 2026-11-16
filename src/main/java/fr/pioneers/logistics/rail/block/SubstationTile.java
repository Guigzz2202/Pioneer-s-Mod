package fr.pioneers.logistics.rail.block;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.logistics.rail.net.Electrification;
import fr.pioneers.logistics.rail.PiRail;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.2 : sous-station : recoit l'energie des cables par toutes ses faces et la verse dans la reserve de sa grille (tenue par le reseau
 * ferre, pour que les trains la tirent meme loin d'ici). Reserve 8 M / 64 M / 512 M / 4,096 G FE ; debit vers les trains 8 192 x 4^tier
 * FE/t.
 * <p>
 * Interface : 4 reserve de la sous-station (FE), 5 contenance, 6 energie tiree par les trains a la derniere seconde (FE/t).
 * <p>
 * API Machine (lot 8c) : logique dans {@link SubstationUnit} (fiche {@code migrated}) ; cette tile la porte ({@link #carry}).
 */
public class SubstationTile extends MachineTile {

    /** API Machine (lot 8c) : valeurs nommees de l'unite (memes cles et indices qu'avant). */
    public static final UiValues.Key GRID_STORED = SubstationUnit.GRID_STORED, GRID_CAPACITY = SubstationUnit.GRID_CAPACITY, DRAW = SubstationUnit.DRAW;

    /** API Machine (lot 8c) : la logique (versement dans la grille, debit des trains) est dans l'unite portee. */
    private final SubstationUnit unit;

    public SubstationTile(BlockPos pos, BlockState state) {
        super(PiRail.SUBSTATION_TILE.get(), pos, state, Electrification.rate(TieredMachineBlock.tierOf(state)) * 4,
              (int) Math.min(Integer.MAX_VALUE, Electrification.rate(TieredMachineBlock.tierOf(state)) * 2));
        unit = carry(new SubstationUnit(access()));
    }

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

    @Override
    public String guiTier() {
        return TieredMachineBlock.TIERS[unit.tier()];
    }
}
