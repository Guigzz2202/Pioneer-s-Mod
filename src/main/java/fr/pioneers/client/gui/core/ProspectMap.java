package fr.pioneers.client.gui.core;

import fr.pioneers.core.RenderClock;
import fr.pioneers.space.mining.Deposit;
import fr.pioneers.space.mining.DrillHead;
import fr.pioneers.tile.TileEntityMiningOutpost;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/** Radar de l'avant-poste (porte de l'ancien GuiProspectMap) : base, balayage, gisements, rover. Repere relatif a l'interface. */
final class ProspectMap {

    private ProspectMap() {
    }

    static void draw(GuiMachine gui, GuiGraphics graphics, int mouseX, int mouseY, TileEntityMiningOutpost tile, int x, int y, int size) {
        int x0 = x, y0 = y, x1 = x0 + size, y1 = y0 + size, width = size, height = size;
        gui.screenAt(graphics, x0, y0, size, size);
        graphics.fill(x0 + 2, y0 + 2, x1 - 2, y1 - 2, 0xFF061009);
        float cx = x0 + width / 2F, cy = y0 + height / 2F;
        float radius = width / 2F - 4;
        //Anneaux et axes
        for (int ring = 1; ring <= 3; ring++) {
            circle(graphics, cx, cy, radius * ring / 3F, 0xFF12361F);
        }
        graphics.fill(Math.round(cx), y0 + 3, Math.round(cx) + 1, y1 - 3, 0xFF0E2A18);
        graphics.fill(x0 + 3, Math.round(cy), x1 - 3, Math.round(cy) + 1, 0xFF0E2A18);
        //Balayage
        float time = Minecraft.getInstance().level == null ? 0 : (float) RenderClock.time(Minecraft.getInstance().getFrameTime());   //1.0.5-beta (lot 29) : horloge de rendu continue
        boolean surveying = tile.getClientMode() == TileEntityMiningOutpost.Mode.SURVEYING;
        float sweep = time * (surveying ? 0.25F : 0.05F);
        for (int k = 0; k < 14; k++) {
            float a = sweep - k * 0.045F;
            int alpha = 150 - k * 10;
            line(graphics, cx, cy, cx + Mth.cos(a) * radius, cy + Mth.sin(a) * radius, alpha << 24 | 0x40FF80);
        }
        //Base : carre et porte
        int bx = Math.round(cx), by = Math.round(cy);
        graphics.fill(bx - 2, by - 2, bx + 3, by + 3, 0xFFE87C1E);
        var door = tile.getClientDoor();
        graphics.fill(bx + door.getStepX() * 3, by + door.getStepZ() * 3, bx + door.getStepX() * 3 + 1, by + door.getStepZ() * 3 + 1, 0xFFFFE080);
        //Gisements
        float s = (size / 2F - 4) / Math.max(8, tile.getSurveyRadius());
        int mx = mouseX, my = mouseY, hovered = -1;
        List<Deposit> deposits = tile.getClientDeposits();
        DrillHead head = tile.getDrillHead();
        var center = tile.getClientCenter();
        if (center != null) {
            for (int i = 0; i < deposits.size(); i++) {
                Deposit deposit = deposits.get(i);
                if (deposit.isDepleted()) {
                    continue;
                }
                int ddx = deposit.pos.getX() - center.getX(), ddz = deposit.pos.getZ() - center.getZ();
                if (ddx * ddx + ddz * ddz > (tile.getSurveyRadius() + 0.5) * (tile.getSurveyRadius() + 0.5)) {
                    //Hors de la portee reglee
                    continue;
                }
                float px = cx + ddx * s, py = cy + ddz * s;
                int r = 1 + Math.round(2 * deposit.remaining / (float) Math.max(1, deposit.total));
                int ix = Math.round(px), iy = Math.round(py);
                boolean filtered = tile.getFilterVein() != null && tile.getFilterVein() != deposit.vein;
                int color = 0xFF000000 | (filtered ? dim(deposit.vein.color) : deposit.vein.color);
                if (i == tile.getClientTarget()) {
                    int pulse = 3 + Math.round((time * 0.2F) % 3);
                    outline(graphics, ix - pulse, iy - pulse, ix + pulse + 1, iy + pulse + 1, 0xFFFFFFFF);
                }
                graphics.fill(ix - r, iy - r, ix + r + 1, iy + r + 1, color);
                if (head == null || head.tier < deposit.vein.hardness) {
                    graphics.fill(ix - r - 1, iy, ix + r + 2, iy + 1, 0xFFFF3030);
                }
                if (Math.abs(mx - ix) <= r + 1 && Math.abs(my - iy) <= r + 1) {
                    hovered = i;
                }
            }
        }
        //Rover
        if (tile.hasClientRover()) {
            double[] pose = tile.getRoverPose(time);
            int rx = Math.round(cx + (float) pose[0] * s), ry = Math.round(cy + (float) pose[1] * s);
            graphics.fill(rx - 1, ry - 1, rx + 2, ry + 2, 0xFF000000);
            graphics.fill(rx, ry, rx + 1, ry + 1, (time % 10) < 5 ? 0xFFFFFFFF : 0xFF60E0FF);
        }
        if (surveying) {
            Component text = Component.translatable("mining.pioneers.map.surveying");
            float sc = 0.5F;
            var pose = graphics.pose();
            pose.pushPose();
            pose.translate(cx - Minecraft.getInstance().font.width(text) * sc / 2, y1 - 9, 0);
            pose.scale(sc, sc, 1);
            graphics.drawString(Minecraft.getInstance().font, text, 0, 0, 0xFF60FF90, false);
            pose.popPose();
        }
        if (hovered >= 0 && center != null) {
            Deposit deposit = deposits.get(hovered);
            List<Component> lines = new ArrayList<>();
            lines.add(Component.translatable(deposit.vein.getTranslationKey()).withStyle(style -> style.withColor(deposit.vein.color)));
            lines.add(Component.translatable("mining.pioneers.map.remaining", deposit.remaining, deposit.total).withStyle(ChatFormatting.GRAY));
            int distance = (int) Math.round(Math.sqrt(deposit.pos.distSqr(center)));
            lines.add(Component.translatable("mining.pioneers.map.distance", distance).withStyle(ChatFormatting.GRAY));
            boolean ok = head != null && head.tier >= deposit.vein.hardness;
            lines.add(Component.translatable("mining.pioneers.map.hardness", deposit.vein.hardness)
                  .withStyle(ok ? ChatFormatting.GREEN : ChatFormatting.RED));
            lines.add(Component.translatable("mining.pioneers.map.yield", Component.translatable(deposit.vein.getPrimary().getDescriptionId()),
                  Component.translatable(deposit.vein.getByproduct().getDescriptionId())).withStyle(ChatFormatting.DARK_AQUA));
            gui.tooltip(lines);
        }
    }

    private static int dim(int color) {
        return (color >> 16 & 255) / 3 << 16 | (color >> 8 & 255) / 3 << 8 | (color & 255) / 3;
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
