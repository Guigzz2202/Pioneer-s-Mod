package fr.pioneers.client.home;

import fr.pioneers.Pioneers;
import fr.pioneers.home.bag.BagItem;
import fr.pioneers.home.bag.PacketBag;
import fr.pioneers.home.bag.StorageBagMenu;
import fr.pioneers.network.PiNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.gui.screens.inventory.CreativeModeInventoryScreen;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.inventory.Slot;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 31, retour 42) : DEPOT RAPIDE dans un conteneur OUVERT : dans l'ecran d'un coffre, d'un panier, d'une caisse… (pas
 * l'inventaire du joueur ni l'ecran d'un sac), Maj + clic droit sur un sac de rangement de son inventaire = tout son contenu va dans le
 * conteneur (le serveur ne retire du sac que ce qui y entre : {@code BagDeposit.intoOpenMenu}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class BagClientEvents {

    private BagClientEvents() {
    }

    @SubscribeEvent
    public static void dumpClick(ScreenEvent.MouseButtonPressed.Pre event) {
        if (event.getButton() != 1 || !Screen.hasShiftDown() || !(event.getScreen() instanceof AbstractContainerScreen<?> screen)
            || screen instanceof InventoryScreen || screen instanceof CreativeModeInventoryScreen || screen.getMenu() instanceof StorageBagMenu) {
            return;
        }
        Slot slot = screen.getSlotUnderMouse();
        Minecraft mc = Minecraft.getInstance();
        if (slot == null || mc.player == null || slot.container != mc.player.getInventory() || !(slot.getItem().getItem() instanceof BagItem b)
            || !b.kind.storage() || !screen.getMenu().getCarried().isEmpty()) {
            return;
        }
        PiNetwork.sendToServer(new PacketBag(StorageBagMenu.DUMP_OPEN, -1, "", slot.index));
        event.setCanceled(true);
    }
}
