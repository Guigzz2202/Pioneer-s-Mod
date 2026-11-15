package fr.pioneers.client.space;

import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.Texts;
import fr.pioneers.data.DataUnits;
import fr.pioneers.space.module.RocketCargo;
import fr.pioneers.space.module.RocketMenu;
import fr.pioneers.space.module.RocketModule;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import org.jetbrains.annotations.NotNull;

/**
 * Interface de la fusee : modules, carburant, soute a objets (avec defilement) et soutes a fluide, gaz et donnees.
 */
public class RocketScreen extends AbstractContainerScreen<RocketMenu> {

    private static final int GAUGE_Y = RocketMenu.CARGO_Y, GAUGE_H = 54, FLUID_X = 176, GAS_X = 186, DATA_X = 196, GAUGE_W = 8;
    private static final int UP_X = 176, DOWN_X = 189, ARROW_Y = 37, ARROW_SIZE = 11;

    public RocketScreen(RocketMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title);
        imageWidth = 208;
        imageHeight = 204;
        inventoryLabelY = RocketMenu.INVENTORY_Y - 11;
    }

    @Override
    protected void renderBg(@NotNull GuiGraphics graphics, float partialTick, int mouseX, int mouseY) {
        int x = leftPos, y = topPos;
        GuiDraw.panel(graphics, x, y, imageWidth, imageHeight);
        List<ItemStack> modules = menu.getModules();
        int moduleSlots = RocketModule.slots(menu.getTier());
        //Modules
        for (int i = 0; i < RocketModule.MAX_SLOTS; i++) {
            int sx = x + RocketMenu.MODULE_X + i * 18, sy = y + RocketMenu.MODULE_Y;
            if (i < moduleSlots) {
                GuiDraw.slot(graphics, sx, sy);
            }
        }
        //Carburant
        int fuel = menu.getValue(RocketMenu.FUEL), capacity = Math.max(1, menu.getValue(RocketMenu.FUEL_CAPACITY));
        GuiDraw.bar(graphics, x + 8, y + 45, 160, 2, fuel / (double) capacity, 0xFF6FC3FF);
        //Soute a objets
        int unlocked = RocketCargo.itemSlots(modules);
        for (Slot slot : menu.slots) {
            if (slot.container instanceof Inventory || slot.index < moduleSlots) {
                if (slot.container instanceof Inventory) {
                    GuiDraw.slot(graphics, x + slot.x, y + slot.y);
                }
                continue;
            }
            int absolute = menu.getScroll() * 9 + slot.getContainerSlot();
            if (absolute < unlocked) {
                GuiDraw.slot(graphics, x + slot.x, y + slot.y);
            } else {
                GuiDraw.locked(graphics, x + slot.x, y + slot.y);
            }
        }
        //Soutes a fluide, gaz et donnees
        long fluidCap = RocketCargo.fluidCapacity(modules), gasCap = RocketCargo.gasCapacity(modules), dataCap = RocketCargo.dataCapacity(modules);
        Fluid fluid = menu.getFluid();
        Gas gas = menu.getGas();
        DataType dataType = menu.getDataType();
        drawGauge(graphics, x + FLUID_X, fluidCap, menu.getValue(RocketMenu.FLUID), fluid == null ? 0x3C8CFF : IClientFluidTypeExtensions.of(fluid).getTintColor() & 0xFFFFFF);
        drawGauge(graphics, x + GAS_X, gasCap, menu.getValue(RocketMenu.GAS), gas == null ? 0xF0E050 : gas.getTint());
        drawGauge(graphics, x + DATA_X, dataCap, menu.getValue(RocketMenu.DATA), dataType == null ? 0x9B6BFF : dataType.getColor());
        //Defilement
        boolean canScroll = menu.maxScroll() > 0;
        drawArrow(graphics, x + UP_X, y + ARROW_Y, "▲", canScroll && menu.getScroll() > 0, mouseX, mouseY);
        drawArrow(graphics, x + DOWN_X, y + ARROW_Y, "▼", canScroll && menu.getScroll() < menu.maxScroll(), mouseX, mouseY);
    }

    private void drawGauge(GuiGraphics graphics, int x, long capacity, int amount, int rgb) {
        if (capacity <= 0) {
            graphics.fill(x, topPos + GAUGE_Y, x + GAUGE_W, topPos + GAUGE_Y + GAUGE_H, 0xFF1A2027);
            return;
        }
        GuiDraw.gauge(graphics, x, topPos + GAUGE_Y, GAUGE_W, GAUGE_H, amount / (double) capacity, rgb);
    }

    private void drawArrow(GuiGraphics graphics, int x, int y, String symbol, boolean active, int mouseX, int mouseY) {
        boolean hover = active && mouseX >= x && mouseX < x + ARROW_SIZE && mouseY >= y && mouseY < y + ARROW_SIZE;
        graphics.fill(x, y, x + ARROW_SIZE, y + ARROW_SIZE, active ? hover ? 0xFF3A4C5C : 0xFF2A3540 : 0xFF1A2027);
        graphics.drawString(font, symbol, x + 2, y + 2, active ? 0xFFE0F5EA : 0xFF59666A, false);
    }

    @Override
    protected void renderLabels(@NotNull GuiGraphics graphics, int mouseX, int mouseY) {
        graphics.drawString(font, title, titleLabelX, titleLabelY, 0xFFE0F5EA, false);
        graphics.drawString(font, playerInventoryTitle, inventoryLabelX, inventoryLabelY, 0xFF8FA3A0, false);
        List<ItemStack> modules = menu.getModules();
        GuiDraw.text(graphics, font, Component.translatable("rocket.pioneers.gui.modules", modules.size(), RocketModule.slots(menu.getTier())),
              8, 25, 0.6F, 0xFF8FA3A0, 34);
        int fuel = menu.getValue(RocketMenu.FUEL), capacity = menu.getValue(RocketMenu.FUEL_CAPACITY);
        GuiDraw.text(graphics, font, Component.translatable("rocket.pioneers.gui.fuel", Texts.format(fuel), Texts.format(capacity)),
              8, 39, 0.55F, 0xFF8FA3A0, 160);
        if (RocketCargo.itemSlots(modules) == 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(0, 0, 260);
            GuiDraw.center(graphics, font, Component.translatable("rocket.pioneers.gui.no_item_bay"), 89, RocketMenu.CARGO_Y + 23, 0.6F, 0xFFC0C8D0, 150);
            graphics.pose().popPose();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        renderBackground(graphics);
        super.render(graphics, mouseX, mouseY, partialTick);
        renderTooltip(graphics, mouseX, mouseY);
        List<Component> tooltip = gaugeTooltip(mouseX - leftPos, mouseY - topPos);
        if (!tooltip.isEmpty()) {
            graphics.renderComponentTooltip(font, tooltip, mouseX, mouseY);
        }
    }

    private List<Component> gaugeTooltip(int mx, int my) {
        List<Component> lines = new ArrayList<>();
        if (my < GAUGE_Y || my >= GAUGE_Y + GAUGE_H) {
            if (mx >= 8 && mx < 42 && my >= RocketMenu.MODULE_Y && my < RocketMenu.MODULE_Y + 18) {
                lines.add(Component.translatable("rocket.pioneers.gui.modules_hint").withStyle(ChatFormatting.GRAY));
            }
            return lines;
        }
        List<ItemStack> modules = menu.getModules();
        if (mx >= FLUID_X && mx < FLUID_X + GAUGE_W) {
            Fluid fluid = menu.getFluid();
            lines.add(Component.translatable("rocket.pioneers.gui.fluid_bay"));
            addContent(lines, fluid == null ? null : fluid.getFluidType().getDescription(), menu.getValue(RocketMenu.FLUID), RocketCargo.fluidCapacity(modules), " mB");
        } else if (mx >= GAS_X && mx < GAS_X + GAUGE_W) {
            Gas gas = menu.getGas();
            lines.add(Component.translatable("rocket.pioneers.gui.gas_bay"));
            addContent(lines, gas == null ? null : gas.getName(), menu.getValue(RocketMenu.GAS), RocketCargo.gasCapacity(modules), " mB");
        } else if (mx >= DATA_X && mx < DATA_X + GAUGE_W) {
            DataType type = menu.getDataType();
            lines.add(Component.translatable("rocket.pioneers.gui.data_bay"));
            long capacity = RocketCargo.dataCapacity(modules);
            if (capacity <= 0) {
                lines.add(Component.translatable("rocket.pioneers.gui.bay_missing").withStyle(ChatFormatting.DARK_GRAY));
            } else {
                if (type != null) {
                    lines.add(type.getName().copy().withStyle(ChatFormatting.GRAY));
                }
                lines.add(Component.translatable("data.pioneers.gauge", DataUnits.format(menu.getValue(RocketMenu.DATA)), DataUnits.format(capacity))
                      .withStyle(ChatFormatting.GRAY));
            }
        }
        return lines;
    }

    private static void addContent(List<Component> lines, Component name, int amount, long capacity, String unit) {
        if (capacity <= 0) {
            lines.add(Component.translatable("rocket.pioneers.gui.bay_missing").withStyle(ChatFormatting.DARK_GRAY));
            return;
        }
        if (name != null) {
            lines.add(name.copy().withStyle(ChatFormatting.GRAY));
        }
        lines.add(Component.literal(Texts.format(amount) + " / " + Texts.format(capacity) + unit).withStyle(ChatFormatting.GRAY));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
        if (my >= ARROW_Y && my < ARROW_Y + ARROW_SIZE) {
            if (mx >= UP_X && mx < UP_X + ARROW_SIZE) {
                scroll(-1);
                return true;
            } else if (mx >= DOWN_X && mx < DOWN_X + ARROW_SIZE) {
                scroll(1);
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int mx = (int) mouseX - leftPos, my = (int) mouseY - topPos;
        if (mx >= RocketMenu.CARGO_X && mx < RocketMenu.CARGO_X + 162 && my >= RocketMenu.CARGO_Y && my < RocketMenu.CARGO_Y + 54) {
            scroll(delta > 0 ? -1 : 1);
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, delta);
    }

    private void scroll(int step) {
        int before = menu.getScroll();
        menu.setClientScroll(before + step);
        if (menu.getScroll() != before && minecraft != null && minecraft.gameMode != null) {
            minecraft.gameMode.handleInventoryButtonClick(menu.containerId, step < 0 ? RocketMenu.BUTTON_SCROLL_UP : RocketMenu.BUTTON_SCROLL_DOWN);
        }
    }
}
