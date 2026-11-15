package fr.pioneers.logistics.robotics.client;

import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.core.registries.PiRobotics;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.UnitTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

/**
 * Affectation, commune aux ordres et aux zones, en trois lignes compactes : quelles unites (Auto ou un genre, en icones), quel hub
 * (fleches), et combien au plus.
 */
final class AssignControls {

    private AssignControls() {
    }

    /** Dessine les controles dans une colonne de largeur {@code w} ; renvoie le y suivant. */
    static int draw(RoboticsScreen screen, GuiGraphics graphics, int mouseX, int mouseY, int x, int y, int w, RoboticOrder draft) {
        var font = Minecraft.getInstance().font;
        int labelW = 34, cx = x + labelW;
        //Genre : Auto puis les quatre familles
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.units"), x, y + 4, 0.65F, RoboticsScreen.D_DIM, labelW - 2);
        int autoW = 30;
        screen.chip(graphics, mouseX, mouseY, cx, y, autoW, 16, Component.translatable("robotics.pioneers.assign.auto"), draft.kind < 0, 0x8A6FD0,
              () -> {
                  draft.kind = -1;
                  draft.hub = null;
              });
        screen.tooltip(mouseX, mouseY, cx, y, autoW, 16, Component.translatable("robotics.pioneers.assign.auto"),
              Component.translatable("robotics.pioneers.assign.auto.tooltip").withStyle(ChatFormatting.GRAY));
        int size = Math.min(18, (w - labelW - autoW - 2) / UnitKind.values().length - 1);
        for (UnitKind kind : UnitKind.values()) {
            int kx = cx + autoW + 2 + kind.ordinal() * (size + 1);
            screen.iconChip(graphics, mouseX, mouseY, kx, y, size, 16, new ItemStack(PiRobotics.UNITS.get(kind).get(UnitTier.T1).get()),
                  draft.kind == kind.ordinal(), 0x8A6FD0, () -> {
                      draft.kind = kind.ordinal();
                      ClientRobotics.HubInfo hub = ClientRobotics.hub(draft.hub);
                      if (hub != null && hub.kind() != kind) {
                          draft.hub = null;
                      }
                  });
            screen.tooltip(mouseX, mouseY, kx, y, size, 16, Component.translatable("robotics.pioneers.assign." + kind.id()),
                  Component.translatable("robotics.pioneers.kind." + kind.id() + ".desc").withStyle(ChatFormatting.GRAY));
        }
        //Hub precis
        y += 20;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.hub"), x, y + 3, 0.65F, RoboticsScreen.D_DIM, labelW - 2);
        List<ClientRobotics.HubInfo> hubs = new ArrayList<>();
        for (ClientRobotics.HubInfo hub : ClientRobotics.hubs()) {
            if (draft.kind < 0 || hub.kind().ordinal() == draft.kind) {
                hubs.add(hub);
            }
        }
        int index = -1;
        for (int i = 0; i < hubs.size(); i++) {
            if (hubs.get(i).center().equals(draft.hub)) {
                index = i;
            }
        }
        int current = index;
        int fieldW = w - labelW - 26;
        screen.button(graphics, mouseX, mouseY, cx, y, 12, 13, Component.literal("◀"), !hubs.isEmpty(), () -> select(draft, hubs, current - 1));
        graphics.fill(cx + 13, y, cx + 13 + fieldW, y + 13, 0xFF0E1419);
        ClientRobotics.HubInfo shown = current < 0 ? null : hubs.get(current);
        Component name = shown == null ? Component.translatable("robotics.pioneers.edit.hub.any")
                                       : Component.translatable("robotics.pioneers.edit.hub.entry",
                                             Component.translatable("robotics.pioneers.hub." + shown.kind().id()), shown.center().toShortString());
        GuiDraw.center(graphics, font, name, cx + 13 + fieldW / 2F, y + 3.5F, 0.62F, shown == null ? RoboticsScreen.D_DIM : RoboticsScreen.D_TEXT,
              fieldW - 4);
        screen.button(graphics, mouseX, mouseY, cx + 14 + fieldW, y, 12, 13, Component.literal("▶"), !hubs.isEmpty(),
              () -> select(draft, hubs, current + 1));
        //Unites au plus
        y += 17;
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.max_units"), x, y + 3, 0.65F, RoboticsScreen.D_DIM, labelW - 2);
        screen.stepper(graphics, mouseX, mouseY, cx, y, 44, draft.maxUnits, 1, RoboticOrder.MAX_UNITS, 1, value -> draft.maxUnits = value);
        GuiDraw.text(graphics, font, Component.translatable("robotics.pioneers.edit.max_units.short"), cx + 48, y + 3, 0.6F, RoboticsScreen.D_FAINT,
              w - labelW - 50);
        screen.tooltip(mouseX, mouseY, x, y, w, 12, Component.translatable("robotics.pioneers.edit.max_units.tooltip"));
        return y + 16;
    }

    private static void select(RoboticOrder draft, List<ClientRobotics.HubInfo> hubs, int index) {
        if (hubs.isEmpty()) {
            draft.hub = null;
            return;
        }
        //-1 : n'importe quel hub
        int wrapped = Math.floorMod(index + 1, hubs.size() + 1) - 1;
        draft.hub = wrapped < 0 ? null : hubs.get(wrapped).center();
        if (wrapped >= 0) {
            draft.kind = hubs.get(wrapped).kind().ordinal();
        }
    }
}
