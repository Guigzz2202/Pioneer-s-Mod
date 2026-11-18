package fr.pioneers.space.tile;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.recipes.AssemblyStation;
import fr.pioneers.space.tile.base.TileEntityAssemblyBase;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;

/**
 * Table d'assemblage specialisee d'un bloc : rover et robots, modules de fusee, ou equipements (combinaisons, support de vie).
 * Meme grille et memes regles que la table des fusees ; le produit en cours se dessine en hologramme au-dessus du plateau.
 */
public class TileEntityAssemblyTable extends TileEntityAssemblyBase {

    public static final long ENERGY_STORAGE_FE = 8_000_000L;

    private final AssemblyStation station;

    public TileEntityAssemblyTable(BlockEntityType<?> type, BlockPos pos, BlockState state, AssemblyStation station) {
        super(type, pos, state, ENERGY_STORAGE_FE);
        this.station = station;
    }

    @Override
    public AssemblyStation getStation() {
        return station;
    }

    @Override
    protected String guiName() {
        return station.id() + "_assembly_table";
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition, worldPosition.offset(1, 2, 1));
    }

    /** API Machine (lot 4) : cadre de l'interface standard v2 (inventaire DANS le cadre, onglets a droite) autour du corps propre d'avant. */
    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

}
