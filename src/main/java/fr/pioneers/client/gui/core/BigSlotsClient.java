package fr.pioneers.client.gui.core;

import fr.pioneers.network.PacketBigSlots;
import java.util.Locale;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.inventory.AbstractContainerMenu;

/**
 * 1.12.3 (lot F3) : cote client des piles de plus de 127 objets (cases des buffers d'objets) : reception du paquet {@link PacketBigSlots} et
 * texte abrege de la quantite dans les cases (« 512 », « 1,2k »).
 */
public final class BigSlotsClient {

    private BigSlotsClient() {
    }

    /** Remet les vraies piles dans les cases du menu ouvert (s'il est toujours celui du paquet). */
    public static void apply(PacketBigSlots packet) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        AbstractContainerMenu menu = player.containerMenu;
        if (menu == null || menu.containerId != packet.containerId()) {
            return;
        }
        for (int i = 0; i < packet.slots().length; i++) {
            int index = packet.slots()[i];
            if (index >= 0 && index < menu.slots.size()) {
                menu.slots.get(index).set(packet.stacks()[i].copy());
            }
        }
    }

    /**
     * Quantite abregee pour une case de 16 pixels : jusqu'a 999 telle quelle, puis en milliers avec une decimale sous 10 000 (« 1,2k » en
     * francais, « 1.2k » ailleurs), sans decimale au-dela.
     */
    public static String shortCount(int count) {
        if (count < 1_000) {
            return Integer.toString(count);
        }
        if (count < 10_000) {
            int tenths = count / 100;
            String sep = french() ? "," : ".";
            return tenths % 10 == 0 ? tenths / 10 + "k" : tenths / 10 + sep + tenths % 10 + "k";
        }
        return count / 1_000 + "k";
    }

    private static boolean french() {
        String code = Minecraft.getInstance().getLanguageManager().getSelected();
        return code != null && code.toLowerCase(Locale.ROOT).startsWith("fr");
    }
}
