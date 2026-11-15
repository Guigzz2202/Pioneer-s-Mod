package fr.pioneers.client.gui.core;

import fr.pioneers.core.RenderClock;
import fr.pioneers.space.giant.GasPocketEntity;
import fr.pioneers.space.giant.HarvesterDroneEntity;
import fr.pioneers.space.giant.PocketKind;
import fr.pioneers.space.tile.TileEntityDroneBay;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;

/** Radar de la baie de drones (porte de l'ancien GuiDroneBay) : poches dans le rayon d'action et drones en vol. Repere de l'interface. */
final class DroneRadar {

    private DroneRadar() {
    }

    static void draw(GuiMachine gui, GuiGraphics graphics, int mouseX, int mouseY, TileEntityDroneBay tile, long filter, int x, int y, int size) {
        {
            int x0 = x, y0 = y, x1 = x0 + size, y1 = y0 + size, width = size, height = size;
            gui.screenAt(graphics, x0, y0, size, size);
            graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFF060C16);
            float cx = x0 + width / 2F, cy = y0 + height / 2F, radius = width / 2F - 4;
            for (int ring = 1; ring <= 3; ring++) {
                circle(graphics, cx, cy, radius * ring / 3F, 0xFF16304A);
            }
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.level == null) {
                return;
            }
            float time = (float) RenderClock.time(minecraft.getFrameTime());   //1.0.5-beta (lot 29) : horloge de rendu continue
            for (int k = 0; k < 12; k++) {
                float a = time * 0.06F - k * 0.05F;
                line(graphics, cx, cy, cx + Mth.cos(a) * radius, cy + Mth.sin(a) * radius, (140 - k * 11) << 24 | 0x40A0FF);
            }
            float scale = radius / TileEntityDroneBay.range();
            int mx = mouseX, my = mouseY;
            GasPocketEntity hovered = null;
            AABB box = new AABB(tile.getBlockPos()).inflate(TileEntityDroneBay.range(), 400, TileEntityDroneBay.range());
            for (GasPocketEntity pocket : minecraft.level.getEntitiesOfClass(GasPocketEntity.class, box)) {
                float px = cx + (float) (pocket.getX() - tile.getBlockPos().getX() - 0.5) * scale;
                float py = cy + (float) (pocket.getZ() - tile.getBlockPos().getZ() - 0.5) * scale;
                if (Math.hypot(px - cx, py - cy) > radius) {
                    continue;
                }
                int r = 1 + Math.round(2 * Mth.sqrt(pocket.getAmount() / (float) pocket.getCapacity()));
                int ix = Math.round(px), iy = Math.round(py);
                int color = 0xFF000000 | (TileEntityDroneBay.accepts(filter, pocket.getKind()) ? pocket.getKind().color : 0x404850);
                if (pocket.isMarked()) {
                    int pulse = 3 + Math.round((time * 0.2F) % 3);
                    outline(graphics, ix - pulse, iy - pulse, ix + pulse + 1, iy + pulse + 1, 0xFF80FFFF);
                }
                graphics.fill(ix - r, iy - r, ix + r + 1, iy + r + 1, color);
                if (pocket.isInStorm()) {
                    graphics.fill(ix - r - 1, iy - r - 1, ix - r, iy - r, 0xFFFFE060);
                    graphics.fill(ix + r + 1, iy + r + 1, ix + r + 2, iy + r + 2, 0xFFFFE060);
                }
                if (pocket.isRich() && ((int) time / 6) % 2 == 0) {
                    graphics.fill(ix, iy - r - 2, ix + 1, iy - r - 1, 0xFFFFFFFF);
                }
                if (Math.abs(mx - ix) <= r + 1 && Math.abs(my - iy) <= r + 1) {
                    hovered = pocket;
                }
            }
            for (HarvesterDroneEntity drone : minecraft.level.getEntitiesOfClass(HarvesterDroneEntity.class, box)) {
                int ix = Math.round(cx + (float) (drone.getX() - tile.getBlockPos().getX() - 0.5) * scale);
                int iy = Math.round(cy + (float) (drone.getZ() - tile.getBlockPos().getZ() - 0.5) * scale);
                graphics.fill(ix - 1, iy - 1, ix + 2, iy + 2, 0xFF000000);
                graphics.fill(ix, iy, ix + 1, iy + 1, (time % 8) < 4 ? 0xFFFFFFFF : drone.isArmored() ? 0xFFFFC040 : 0xFF60E0FF);
            }
            int bx = Math.round(cx), by = Math.round(cy);
            graphics.fill(bx - 2, by - 2, bx + 3, by + 3, 0xFFE87C1E);
                    if (hovered != null) {
                GasPocketEntity pocket = hovered;
            List<Component> lines = new ArrayList<>();
                lines.add(Component.translatable(pocket.getKind().getTranslationKey()).withStyle(style -> style.withColor(pocket.getKind().color)));
                lines.add(pocket.describeAmount().copy().withStyle(ChatFormatting.GRAY));
                lines.add(Component.translatable("drone_bay.pioneers.altitude", (int) pocket.getY(),
                      Component.translatable(PocketKind.Layer.at(pocket.getY()).getTranslationKey())).withStyle(ChatFormatting.GRAY));
                if (pocket.isRich()) {
                    lines.add(Component.translatable("pocket.pioneers.rich").withStyle(ChatFormatting.GOLD));
                }
                if (pocket.isInStorm()) {
                    lines.add(Component.translatable("pocket.pioneers.storm").withStyle(ChatFormatting.YELLOW));
                }
                if (pocket.isMarked()) {
                    lines.add(Component.translatable("pocket.pioneers.marked_tooltip").withStyle(ChatFormatting.AQUA));
                }
                gui.tooltip(lines);
            }
        }
    }

    private static void outline(GuiGraphics graphics, int x0, int y0, int x1, int y1, int color) {
        graphics.fill(x0, y0, x1, y0 + 1, color);
        graphics.fill(x0, y1 - 1, x1, y1, color);
        graphics.fill(x0, y0, x0 + 1, y1, color);
        graphics.fill(x1 - 1, y0, x1, y1, color);
    }

    private static void circle(GuiGraphics graphics, float cx, float cy, float r, int color) {
        int steps = Math.max(16, Math.round(r * 5));
        for (int i = 0; i < steps; i++) {
            float a = (float) (i * Math.PI * 2 / steps);
            int x = Math.round(cx + Mth.cos(a) * r), y = Math.round(cy + Mth.sin(a) * r);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }

    private static void line(GuiGraphics graphics, float x0, float y0, float x1, float y1, int color) {
        int steps = Math.round(Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0)));
        for (int i = 0; i <= steps; i++) {
            float t = steps == 0 ? 0 : i / (float) steps;
            int x = Math.round(x0 + (x1 - x0) * t), y = Math.round(y0 + (y1 - y0) * t);
            graphics.fill(x, y, x + 1, y + 1, color);
        }
    }
    
}
