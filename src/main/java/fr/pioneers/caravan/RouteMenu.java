package fr.pioneers.caravan;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 10) : ecran de la FEUILLE DE ROUTE tenue en main (aucune case : l'itineraire est lu dans l'objet, que le serveur garde a
 * jour ; les modifications partent par {@link PacketCaravanRoute}). Ferme si l'objet tenu n'est plus une feuille de route.
 */
public class RouteMenu extends AbstractContainerMenu {

    public final InteractionHand hand;
    private final Player player;

    public RouteMenu(int id, Inventory inventory, InteractionHand hand) {
        super(PiCaravan.ROUTE_MENU.get(), id);
        this.hand = hand;
        this.player = inventory.player;
    }

    public static RouteMenu client(int id, Inventory inventory, FriendlyByteBuf buf) {
        return new RouteMenu(id, inventory, buf.readEnum(InteractionHand.class));
    }

    public ItemStack sheet() {
        return player.getItemInHand(hand);
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player p, int index) {
        return ItemStack.EMPTY;
    }

    @Override
    public boolean stillValid(@NotNull Player p) {
        return p.isAlive() && p.getItemInHand(hand).getItem() instanceof RouteSheetItem;
    }
}
