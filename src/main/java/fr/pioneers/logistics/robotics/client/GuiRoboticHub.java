package fr.pioneers.logistics.robotics.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.core.PiMenuScreen;
import fr.pioneers.core.network.PacketTileAction;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.RoboticUpgrade;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import fr.pioneers.logistics.robotics.hub.RoboticHubData.Status;
import fr.pioneers.logistics.robotics.hub.RoboticHubData;
import fr.pioneers.logistics.robotics.hub.RoboticHubMenu;
import fr.pioneers.logistics.robotics.hub.TileEntityRoboticCasing;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;

/**
 * Hub robotique (socle, style sombre) : le genre d'unites accueilli (drones, robots, rovers ou tortues), les places des unites (ouvertes
 * selon les modules d'extension ; une unite dehors est voilee), l'ecran d'etat, les ameliorations, le module d'extension, le rappel,
 * l'energie et le tampon. Aucun reglage : les ordres se donnent par la touche O, les zones au planificateur.
 */
public class GuiRoboticHub extends PiMenuScreen<RoboticHubMenu> {

    /** Pastilles du genre d'unites : sous l'ecran d'etat (100..158), sans deborder sur la jauge d'energie. */
    private static final int KIND_X = 100, KIND_Y = 44, KIND_W = 14, KIND_STEP = 15;

    public GuiRoboticHub(RoboticHubMenu menu, Inventory inventory, Component title) {
        super(menu, inventory, title, 176, RoboticHubMenu.HEIGHT);
    }

    private RoboticHubData hub() {
        return menu.hub();
    }

    private void send(int action) {
        if (menu.getTile() != null) {
            PiNetwork.sendToServer(new PacketTileAction(menu.getTile().getBlockPos(), action));
        }
    }

    @Override
    protected void drawBackground(GuiGraphics graphics, int mouseX, int mouseY) {
        RoboticHubData hub = hub();
        //Ecran d'etat
        screen(graphics, 100, 16, 58, 26);
        Status status = menu.status();
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("robotics.pioneers.hub." + UnitKind.values()[Math.floorMod(menu.kind(), UnitKind.values().length)].id()));
        lines.add(Component.translatable("robotics.pioneers.hub.status." + status.name().toLowerCase(Locale.ROOT))
              .withStyle(status == Status.WORKING || status == Status.IDLE ? ChatFormatting.GREEN : ChatFormatting.RED));
        lines.add(Component.translatable("robotics.pioneers.hub.out_places", menu.active(), hub.unitCount(), hub.capacity()));
        lines.add(Component.translatable("robotics.pioneers.hub.range", hub.getRange()));
        screenLines(graphics, 100, 16, 58, 0.5F, lines, SCREEN_TEXT);
        //Genre d'unites accueilli
        UnitKind current = UnitKind.values()[Math.floorMod(menu.kind(), UnitKind.values().length)];
        boolean canChange = hub.unitCount() == 0 && menu.active() == 0;
        for (UnitKind kind : UnitKind.values()) {
            int x = KIND_X + kind.ordinal() * KIND_STEP;
            boolean selected = kind == current;
            boolean hover = over(mouseX, mouseY, x, KIND_Y, KIND_W, 14);
            graphics.fill(x, KIND_Y, x + KIND_W, KIND_Y + 14, selected ? 0xFF2E9E78 : hover ? 0xFF2E3A46 : 0xFF1E262E);
            graphics.renderOutline(x, KIND_Y, KIND_W, 14, selected ? 0xFF7FE6C0 : hover ? 0xFF4A5A68 : 0xFF2C3740);
            graphics.pose().pushPose();
            graphics.pose().translate(x + 1, KIND_Y + 1, 0);
            graphics.pose().scale(0.75F, 0.75F, 1);
            graphics.renderItem(new ItemStack(PiRobotics.UNITS.get(kind).get(UnitTier.T1).get()), 0, 0);
            graphics.pose().popPose();
            if (!selected && canChange) {
                click(x, KIND_Y, KIND_W, 14, () -> send(TileEntityRoboticCasing.ACTION_KIND + kind.ordinal()));
            }
            if (hover) {
                List<Component> tip = new ArrayList<>();
                tip.add(Component.translatable("robotics.pioneers.hub." + kind.id()));
                tip.add(Component.translatable("robotics.pioneers.kind." + kind.id() + ".desc").withStyle(ChatFormatting.GRAY));
                if (!selected) {
                    tip.add(Component.translatable(canChange ? "robotics.pioneers.hub.kind.switch" : "robotics.pioneers.hub.kind.locked")
                          .withStyle(canChange ? ChatFormatting.GREEN : ChatFormatting.RED));
                }
                tooltip(tip);
            }
        }
        //Rappel
        button(graphics, mouseX, mouseY, 140, 78, 20, 18, Component.literal("↩"), true, () -> send(TileEntityRoboticCasing.ACTION_RECALL));
        if (over(mouseX, mouseY, 140, 78, 20, 18)) {
            tooltip(List.of(Component.translatable("robotics.pioneers.hub.recall"),
                  Component.translatable("robotics.pioneers.hub.recall.tooltip").withStyle(ChatFormatting.GRAY)));
        }
        energyBar(graphics, mouseX, mouseY, 163, 16, 8, 74, menu.energy(), menu.capacity(), menu.used());
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.hub.buffer"), RoboticHubData.BUFFER_X, RoboticHubData.BUFFER_Y - 9,
              0.6F, DIM);
    }

    @Override
    protected void drawForeground(GuiGraphics graphics, int mouseX, int mouseY) {
        RoboticHubData hub = hub();
        int capacity = hub.capacity();
        var slots = hub.getSlots();
        for (int i = 0; i < RoboticHubData.UNIT_SLOTS; i++) {
            int x = RoboticHubData.UNITS_X + (i % 5) * 18, y = RoboticHubData.UNITS_Y + (i / 5) * 18;
            if (i >= capacity) {
                GuiDraw.locked(graphics, x, y);
            } else if (!slots.get(i).isEmpty() && menu.isOut(i)) {
                //Unite dehors : case voilee et temoin vert
                graphics.fill(x, y, x + 16, y + 16, 0x90202830);
                graphics.fill(x + 12, y + 1, x + 15, y + 4, 0xFF55E07A);
            }
        }
        //Fantomes des cases vides : ameliorations, module
        RoboticUpgrade[] hints = {RoboticUpgrade.SPEED, RoboticUpgrade.CARGO, RoboticUpgrade.BATTERY, RoboticUpgrade.EFFICIENCY};
        for (int i = 0; i < RoboticHubData.UPGRADE_SLOTS; i++) {
            if (slots.get(RoboticHubData.UNIT_SLOTS + i).isEmpty()) {
                GuiDraw.ghost(graphics, new ItemStack(PiRobotics.UPGRADES.get(hints[i]).get()), RoboticHubData.UPGRADES_X + (i % 2) * 18,
                      RoboticHubData.UPGRADES_Y + (i / 2) * 18);
            }
        }
        if (slots.get(RoboticHubData.UNIT_SLOTS + RoboticHubData.UPGRADE_SLOTS).isEmpty()) {
            GuiDraw.ghost(graphics, new ItemStack(PiRobotics.BAY_MODULE.get()), RoboticHubData.MODULE_X, RoboticHubData.MODULE_Y);
        }
    }
}
