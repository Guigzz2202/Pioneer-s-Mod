package fr.pioneers.logistics.robotics.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.RoboticUpgrade;
import fr.pioneers.logistics.robotics.logistics.RoboticRelayMenu;
import fr.pioneers.logistics.robotics.logistics.TileEntityRoboticRelay;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Relais robotique (socle, style sombre) : etend le reseau logistique (couverture) et recharge les unites qui passent. Deux ameliorations :
 * portee et recharge ; une case de batterie.
 */
public class GuiRoboticRelay extends PiMenuScreen<RoboticRelayMenu> {

    public GuiRoboticRelay(RoboticRelayMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, 166);
    }

    @Override
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        screen(graphics, 8, 16, 98, 58);
        screenLines(graphics, 8, 16, 98, 0.55F, List.of(
              Component.translatable("robotics.pioneers.relay.coverage", menu.coverage() * 2 + 1),
              Component.translatable("robotics.pioneers.relay.charging", menu.charging()),
              Component.translatable("robotics.pioneers.relay.speed", Math.round(100 * (1 + 0.6 * menu.cards(RoboticUpgrade.CHARGING)))),
              Component.translatable("robotics.pioneers.relay.hint")), SCREEN_TEXT);
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.relay.upgrades"), TileEntityRoboticRelay.UPGRADES_X,
              TileEntityRoboticRelay.UPGRADES_Y - 9, 0.6F, DIM, TileEntityRoboticRelay.BATTERY_X - 2 - TileEntityRoboticRelay.UPGRADES_X);
        energyBar(graphics, mouseX, mouseY, 164, 15, 8, 60, menu.energy(), menu.capacity(), menu.used());
    }

    @Override
    protected void drawForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        if (menu.getTile() == null) {
            return;
        }
        RoboticUpgrade[] hints = {RoboticUpgrade.RANGE, RoboticUpgrade.CHARGING};
        for (int i = 0; i < hints.length; i++) {
            if (menu.getTile().getSlots().get(i).isEmpty()) {
                GuiDraw.ghost(graphics, new ItemStack(PiRobotics.UPGRADES.get(hints[i]).get()), TileEntityRoboticRelay.UPGRADES_X + i * 18,
                      TileEntityRoboticRelay.UPGRADES_Y);
            }
        }
    }
}
