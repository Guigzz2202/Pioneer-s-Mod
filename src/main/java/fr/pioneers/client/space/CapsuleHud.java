package fr.pioneers.client.space;

import fr.pioneers.client.PiKeys;
import fr.pioneers.space.CapsuleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * Tableau de bord de la descente : altitude, vitesses (couleur selon le risque a l'impact), carburant, parachute, pilote
 * automatique, rappel des commandes ; une jauge verticale de vitesse et une alerte « trop rapide » pres du sol.
 */
public final class CapsuleHud {

    private CapsuleHud() {
    }

    public static void render(Minecraft minecraft, GuiGraphics graphics, CapsuleEntity capsule) {
        Font font = minecraft.font;
        int x = 8, y = 8, w = 150;
        float height = capsule.getHeightAboveGround();
        float vs = -capsule.getVerticalSpeed();
        graphics.fill(x - 4, y - 4, x + w, y + 104, 0x90000000);
        graphics.fill(x - 4, y - 4, x + w, y - 3, 0xFF4AA8FF);
        graphics.drawString(font, Component.translatable("capsule.pioneers.hud.title"), x, y, 0x9AD8FF);
        y += 13;
        row(graphics, font, x, y, "altitude", String.format("%d m", Math.round(height)), 0xFFFFFF);
        y += 11;
        int vColor = vs <= CapsuleEntity.PERFECT_SPEED ? 0x60FF80 : vs <= CapsuleEntity.SAFE_SPEED ? 0xC8FF60 : vs <= CapsuleEntity.HARD_SPEED ? 0xFFC040
                                                                                                                                                : 0xFF4040;
        String arrow = vs > 0.02F ? "▼" : vs < -0.02F ? "▲" : "■";
        row(graphics, font, x, y, "vertical", String.format("%s %.1f m/s", arrow, Math.abs(vs) * 20), vColor);
        y += 11;
        row(graphics, font, x, y, "horizontal", String.format("%.1f m/s", capsule.getHorizontalSpeed() * 20), 0xD0D0D0);
        y += 12;
        //Carburant
        float fuel = capsule.getFuel() / (float) capsule.getFuelMax();
        graphics.drawString(font, Component.translatable("capsule.pioneers.hud.fuel"), x, y, 0xA0A0A0);
        int bx = x + 52, bw = w - 60;
        graphics.fill(bx, y + 1, bx + bw, y + 7, 0xFF101418);
        graphics.fill(bx, y + 1, bx + Math.round(bw * fuel), y + 7, fuel > 0.3F ? 0xFF50C8FF : fuel > 0.1F ? 0xFFFFB040 : 0xFFFF4040);
        y += 12;
        //Parachute
        String chute = capsule.isAirless() ? "none" : capsule.isChuteTorn() ? "torn" : capsule.isChuteDeployed() ? "open" : "ready";
        int chuteColor = switch (chute) {
            case "open" -> 0x60FF80;
            case "ready" -> 0xFFFFFF;
            case "torn" -> 0xFF4040;
            default -> 0x707070;
        };
        row(graphics, font, x, y, "chute", Component.translatable("capsule.pioneers.hud.chute." + chute).getString()
                                         + (chute.equals("ready") ? " [" + PiKeys.PARACHUTE.getTranslatedKeyMessage().getString() + "]" : ""), chuteColor);
        y += 11;
        row(graphics, font, x, y, "autopilot", Component.translatable(capsule.isAutopilot() ? "options.on" : "options.off").getString()
                                             + " [" + PiKeys.AUTOPILOT.getTranslatedKeyMessage().getString() + "]",
              capsule.isAutopilot() ? 0x60D0FF : 0xA0A0A0);
        y += 13;
        Component controls = Component.translatable("capsule.pioneers.hud.controls", minecraft.options.keyJump.getTranslatedKeyMessage(),
              minecraft.options.keyUp.getTranslatedKeyMessage(), minecraft.options.keyLeft.getTranslatedKeyMessage(),
              minecraft.options.keyDown.getTranslatedKeyMessage(), minecraft.options.keyRight.getTranslatedKeyMessage());
        var pose = graphics.pose();
        pose.pushPose();
        pose.translate(x, y, 0);
        pose.scale(0.75F, 0.75F, 1);
        graphics.drawString(font, controls, 0, 0, 0x8090A0);
        pose.popPose();
        //Jauge de vitesse verticale (droite) : zones verte / jaune / rouge
        int sw = graphics.guiWidth(), sh = graphics.guiHeight();
        int gx = sw - 22, gh = Math.min(120, sh - 90), gy = (sh - gh) / 2;
        graphics.fill(gx - 1, gy - 1, gx + 9, gy + gh + 1, 0xC0000000);
        float maxV = 2.0F;
        zone(graphics, gx, gy, gh, 0, (float) (CapsuleEntity.SAFE_SPEED / maxV), 0xFF2E8A40);
        zone(graphics, gx, gy, gh, (float) (CapsuleEntity.SAFE_SPEED / maxV), (float) (CapsuleEntity.HARD_SPEED / maxV), 0xFF9A8020);
        zone(graphics, gx, gy, gh, (float) (CapsuleEntity.HARD_SPEED / maxV), 1, 0xFF8A2020);
        int my = gy + Math.round(gh * Mth.clamp(vs / maxV, 0, 1));
        graphics.fill(gx - 4, my - 1, gx + 12, my + 1, 0xFFFFFFFF);
        //Alerte
        if (height < 40 && vs > CapsuleEntity.SAFE_SPEED + 0.15F && (minecraft.level.getGameTime() / 5) % 2 == 0) {
            Component warn = Component.translatable("capsule.pioneers.hud.too_fast");
            graphics.drawCenteredString(font, warn, sw / 2, sh / 2 - 40, 0xFF4040);
        }
    }

    private static void zone(GuiGraphics graphics, int x, int y, int h, float from, float to, int color) {
        graphics.fill(x, y + Math.round(h * from), x + 8, y + Math.round(h * to), color);
    }

    private static void row(GuiGraphics graphics, Font font, int x, int y, String key, String value, int color) {
        graphics.drawString(font, Component.translatable("capsule.pioneers.hud." + key), x, y, 0xA0A0A0);
        graphics.drawString(font, value, x + 70, y, color);
    }
}
