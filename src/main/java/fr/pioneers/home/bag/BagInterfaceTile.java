package fr.pioneers.home.bag;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.bronze.BronzeTile;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.home.PiHome;
import fr.pioneers.warehouse.PiWarehouse;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 45, retour 65 de Guillaume : « Plutot un bloc qui fasse office d'interface : On pose le sac dans le slot et l'interface lit
 * le contenu ensuite on peut lier des cables logistiques pour en faire ce qu'on veut ») : INTERFACE DE SAC.
 * <ul>
 *     <li>une CASE DE SAC (sacs a dos et sacs de rangement), seulement pour le joueur dans l'interface : le sac lui-meme n'est jamais pris ni
 *     pose par un entonnoir, un cable ou un tuyau ;</li>
 *     <li>sur ses six faces, le CONTENU du sac ({@link BagView}) : un cable logistique s'y branche comme sur un coffre (fiche, canaux,
 *     filtres), un entonnoir y range ou y prend ; tout est lu et ecrit dans le NBT du sac (une seule source de verite) ; sans sac, l'inventaire
 *     a 0 case mais reste expose (la fiche du cable ne saute pas) ;</li>
 *     <li>casse : le sac tombe avec tout son contenu (dans son NBT).</li>
 * </ul>
 * Interface standard v2 (vue {@code bag_interface} : {@code J:client/gui/core/BagInterfaceViews}) : case du sac a gauche, contenu du sac a
 * droite (lecture seule), ligne d'aide. Ressources : {@code T:core/p105_lot45.py}.
 */
public class BagInterfaceTile extends BronzeTile {

    /** Geometrie de la vue (memes cartes que l'entree d'entrepot). */
    public static final int BAG_X = 31, BAG_Y = 52, GRID_COLS = 6, GRID_ROWS = 6, CELL = 18;
    public static final StandardLayout.Rect BAG_CARD = new StandardLayout.Rect(8, StandardLayout.TOP - 2, 62, 11),
          BAG_STATUS = new StandardLayout.Rect(8, 91, 62, 53),
          BAG_SIDE = new StandardLayout.Rect(76, StandardLayout.TOP - 2, StandardLayout.WIDTH - 8 - 76, 124),
          BAG_LINE = new StandardLayout.Rect(6, 145, StandardLayout.WIDTH - 12, 5);
    /** Premiere case de la grille du contenu (dans la carte de droite). */
    public static final int GRID_X = BAG_SIDE.x() + 3, GRID_Y = BAG_SIDE.y() + 14;

    private final ItemSlot bag;
    private final BagView view;
    private final LazyOptional<IItemHandler> contents;

    public BagInterfaceTile(BlockPos pos, BlockState state) {
        super(PiWarehouse.BAG_INTERFACE_TILE.get(), pos, state);
        bag = slot(bagSlot(this::changed, BAG_X, BAG_Y));
        view = new BagView(bag::getStack, this::changed);
        contents = LazyOptional.of(() -> view);
    }

    /**
     * Case de sac commune (interface de sac, entree d'entrepot) : un sac a la fois, seulement par le joueur (jamais par l'automatisation),
     * fantome du sac a dos.
     */
    public static ItemSlot bagSlot(Runnable changed, int x, int y) {
        return new ItemSlot(() -> 1, s -> s.getItem() instanceof BagItem, (s, a) -> a == Automation.MANUAL, (s, a) -> a == Automation.MANUAL,
              changed::run, x, y).ghost(PiHome.BAGS.get(BagKind.LEATHER_BACKPACK).get());
    }

    public ItemSlot bagSlot() {
        return bag;
    }

    /** Contenu du sac (vue d'inventaire du NBT). */
    public BagView contents() {
        return view;
    }

    public ItemStack bag() {
        return bag.getStack();
    }

    @Override
    protected boolean v2() {
        return true;
    }

    /** Six faces : le CONTENU du sac (jamais la case du sac). */
    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return contents.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        contents.invalidate();
    }

    // ------------------------------------------------------------------ interface standard v2

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public String guiTier() {
        return "bench";
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return List.of(new StandardLayout.Part("card", BAG_CARD), new StandardLayout.Part("card", BAG_STATUS), new StandardLayout.Part("card", BAG_SIDE),
              new StandardLayout.Part("card", BAG_LINE));
    }
}
