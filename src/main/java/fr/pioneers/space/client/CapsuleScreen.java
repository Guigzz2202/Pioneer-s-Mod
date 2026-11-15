package fr.pioneers.space.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.common.PiLang;
import fr.pioneers.space.CapsuleMenu;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import org.jetbrains.annotations.NotNull;

/**
 * Soute de la capsule (style sombre du mod : panneau, emplacements en creux).
 */
public class CapsuleScreen extends AbstractContainerScreen<CapsuleMenu> {

    public CapsuleScreen(CapsuleMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 176;
        imageHeight = 148;
        inventoryLabelY = 55;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        //Panneau
        GuiDraw.panel(graphics, x, y, imageWidth, imageHeight);
        //Emplacement de la soute, mis en valeur
        graphics.fill(x + CapsuleMenu.SLOT_X - 4, y + CapsuleMenu.SLOT_Y - 4, x + CapsuleMenu.SLOT_X + 20, y + CapsuleMenu.SLOT_Y + 20, 0xFF1E3A34);
        graphics.renderOutline(x + CapsuleMenu.SLOT_X - 4, y + CapsuleMenu.SLOT_Y - 4, 24, 24, 0xFF3FD0A0);
        for (Slot slot : menu.slots) {
            GuiDraw.slot(graphics, x + slot.x, y + slot.y);
        }
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE0F5EA, false);
        GuiDraw.center(graphics, font, PiLang.CAPSULE_HINT.translate(), imageWidth / 2F, 48, 0.65F, 0xFF8FA3A0, imageWidth - 16);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFFE0F5EA, false);
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
    }
}
