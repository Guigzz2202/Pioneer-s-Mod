package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.tech.bronze.BronzeTile;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.registries.PiBronze;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Table de coulee : un moule, puis le metal verse par le fourneau a creuset voisin ; il rougeoie, fige, et la piece attend dans la sortie.
 * Rendu : le moule, la surface du metal (couleur du metal, lueur qui s'eteint en figeant), la piece finie. API Machine (lot 8b) : la tile ne
 * fait plus que PORTER son unite ({@link CastingUnit}) ; type {@code casting_table}, cles et ordre des cases inchanges ; interface v2
 * (figeage du metal a la place de l'energie motrice, moule, fleche, piece, ecran).
 */
public class CastingTableTile extends BronzeTile {

    private final CastingUnit unit;

    public CastingTableTile(BlockPos pos, BlockState state) {
        super(PiBronze.CASTING_TABLE_TILE.get(), pos, state);
        unit = carry(new CastingUnit(host()));
    }

    public CastingUnit casting() {
        return unit;
    }

    public ItemSlot moldSlot() {
        return unit.mold;
    }

    public ItemSlot outputSlot() {
        return unit.output;
    }

    public String metal() {
        return unit.metal();
    }

    public int amount() {
        return unit.amount();
    }

    /** 0 en fusion -> 1 fige. */
    public float solid() {
        return unit.solid();
    }

    public boolean busy() {
        return unit.busy();
    }

    /** Recette de coulee pour ce moule et ce metal (ou null). */
    @Nullable
    public CastingRecipe recipeFor(String metalId) {
        return unit.recipeFor(metalId);
    }

    /** Le fourneau verse : {@code mix} = composition versee (pour le lingot impur). */
    public void fill(CastingRecipe recipe, Map<String, Integer> mix) {
        unit.fill(recipe, mix);
    }

    @Override
    public void serverTick(ServerLevel level) {
        unit.tick();
        //1.0.2-beta : une table posee avant le lot 2 se raccorde au premier tick (ensuite : changements des voisins)
        if (!checkedLink) {
            checkedLink = true;
            BlockState now = CastingTableBlock.linked(getBlockState(), level, worldPosition);
            if (now != getBlockState()) {
                level.setBlock(worldPosition, now, Block.UPDATE_CLIENTS);
            }
        }
        flushClient(level, 5);
    }

    private boolean checkedLink;

    @Override
    public void clientTick() {
        unit.clientTick();
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return unit.layout.ageParts(false, false);
    }
}
