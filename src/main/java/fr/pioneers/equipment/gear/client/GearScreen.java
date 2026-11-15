package fr.pioneers.equipment.gear.client;

import fr.pioneers.client.PiKeys;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.client.gui.TooltipLayer;
import fr.pioneers.core.Texts;
import fr.pioneers.equipment.gear.GearModule;
import fr.pioneers.equipment.gear.ModularGear;
import fr.pioneers.equipment.gear.Modules;
import fr.pioneers.core.network.PacketGear;
import fr.pioneers.core.network.PiNetwork;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * 0.20.15 : ecran des modules (touche G) : chaque objet modulaire porte (casque, plastron, jambieres, bottes, main) avec sa reserve
 * d'energie, et ses modules en pastilles cliquables : marche / arret sans passer par l'etabli.
 */
public class GearScreen extends Screen {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.MAINHAND};
    private static final int W = 300, CHIP_W = 72, CHIP_H = 13, PER_ROW = 4;

    private record Chip(int x, int y, EquipmentSlot slot, GearModule module) {
    }

    private final List<Chip> chips = new ArrayList<>();

    public GearScreen() {
        super(Component.translatable("gear.pioneers.screen.title"));
    }

    public static boolean anyGear(Player player) {
        for (EquipmentSlot slot : SLOTS) {
            if (ModularGear.is(player.getItemBySlot(slot))) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private int height(Player player) {
        int h = 24;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (ModularGear.is(stack)) {
                int n = Modules.list(stack).size();
                h += 16 + Math.max(1, (n + PER_ROW - 1) / PER_ROW) * (CHIP_H + 2) + 4;
            }
        }
        return h;
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float partial) {
        renderBackground(g);
        Player player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        chips.clear();
        int h = height(player);
        int x0 = (width - W) / 2, y0 = Math.max(4, (height - h) / 2);
        g.fill(x0 - 1, y0 - 1, x0 + W + 1, y0 + h + 1, 0xFF2E3A44);
        g.fill(x0, y0, x0 + W, y0 + h, 0xF0141A20);
        GuiDraw.text(g, font, title, x0 + 8, y0 + 7, 1.0F, 0xFFE0F5EA, W - 16);
        int y = y0 + 22;
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ModularGear gear)) {
                continue;
            }
            int color = 0xFF000000 | gear.tier().color;
            g.renderItem(stack, x0 + 6, y - 2);
            GuiDraw.text(g, font, stack.getHoverName(), x0 + 26, y + 1, 0.8F, color, W - 120);
            Component energy = Component.literal(Texts.energy(Modules.energy(stack)) + " / " + Texts.energy(Modules.capacity(stack)));
            GuiDraw.right(g, font, energy, x0 + W - 8, y + 1, 0.7F, Modules.energy(stack) > 0 ? 0xFF8CF5C6 : 0xFFFF6A5A);
            g.fill(x0 + 26, y + 9, x0 + W - 8, y + 11, 0xFF05080B);
            g.fill(x0 + 26, y + 9, x0 + 26 + Math.round((W - 34) * Modules.ratio(stack)), y + 11, color);
            y += 16;
            List<GearModule> list = Modules.list(stack);
            if (list.isEmpty()) {
                GuiDraw.text(g, font, Component.translatable("gear.pioneers.no_modules"), x0 + 26, y + 2, 0.7F, 0xFF8FA3A0, W - 40);
                y += CHIP_H + 2;
            }
            for (int i = 0; i < list.size(); i++) {
                GearModule m = list.get(i);
                int cx = x0 + 26 + (i % PER_ROW) * (CHIP_W + 2), cy = y + (i / PER_ROW) * (CHIP_H + 2);
                boolean off = Modules.isOff(stack, m);
                boolean hover = mx >= cx && mx < cx + CHIP_W && my >= cy && my < cy + CHIP_H;
                g.fill(cx, cy, cx + CHIP_W, cy + CHIP_H, off ? (hover ? 0xFF4A2A2A : 0xFF2E1C1C) : hover ? 0xFF2E5A44 : 0xFF1C3A2C);
                g.fill(cx, cy, cx + 2, cy + CHIP_H, off ? 0xFFB04040 : color);
                Component label = m.displayName().copy().append(" " + Modules.roman(Modules.installed(stack, m)));
                GuiDraw.text(g, font, label, cx + 5, cy + 3, 0.65F, off ? 0xFF8FA3A0 : 0xFFE0F5EA, CHIP_W - 8);
                chips.add(new Chip(cx, cy, slot, m));
                if (hover) {
                    TooltipLayer.components(font, List.of(m.displayName(), m.effect(), Component.translatable(off ? "gear.pioneers.screen.turn_on"
                          : "gear.pioneers.screen.turn_off")), mx, my);
                }
            }
            y += Math.max(0, (list.size() + PER_ROW - 1) / PER_ROW) * (CHIP_H + 2) + 4;
        }
        super.render(g, mx, my, partial);
    }

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        for (Chip c : chips) {
            if (mx >= c.x && mx < c.x + CHIP_W && my >= c.y && my < c.y + CHIP_H) {
                PiNetwork.CHANNEL.sendToServer(PacketGear.toggle(c.slot, c.module));
                Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(
                      SoundEvents.UI_BUTTON_CLICK, 1.0F));
                return true;
            }
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean keyPressed(int key, int scan, int modifiers) {
        if (PiKeys.GEAR.matches(key, scan)) {
            onClose();
            return true;
        }
        return super.keyPressed(key, scan, modifiers);
    }
}
