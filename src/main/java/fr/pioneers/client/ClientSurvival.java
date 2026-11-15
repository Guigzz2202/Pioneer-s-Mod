package fr.pioneers.client;

import fr.pioneers.Pioneers;
import fr.pioneers.api.environment.BodyEnvironment.Hazard;
import fr.pioneers.client.gui.GuiDraw;
import fr.pioneers.network.PacketSurvivalStatus;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Affichage de survie (au-dessus de la barre de faim) : reserve d'oxygene, combinaison exigee, abri.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClientSurvival {

    private ClientSurvival() {
    }

    private static PacketSurvivalStatus status = PacketSurvivalStatus.NONE;
    private static long receivedAt;

    public static void update(PacketSurvivalStatus packet) {
        status = packet;
        Minecraft minecraft = Minecraft.getInstance();
        receivedAt = minecraft.level == null ? 0 : minecraft.level.getGameTime();
    }

    @SubscribeEvent
    public static void registerOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.AIR_LEVEL.id(), "survival", ClientSurvival::render);
    }

    private static void render(ForgeGui gui, GuiGraphics graphics, float partialTick, int width, int height) {
        Minecraft minecraft = Minecraft.getInstance();
        if (!status.active() || minecraft.player == null || minecraft.level == null || minecraft.options.hideGui
            || minecraft.level.getGameTime() - receivedAt > 60 || minecraft.player.isCreative() || minecraft.player.isSpectator()) {
            return;
        }
        Font font = minecraft.font;
        int right = width / 2 + 91;
        int y = height - gui.rightHeight - 1;
        //Reserve d'oxygene : dix capsules bleues
        int oxygen = status.oxygen();
        if (oxygen >= 0) {
            float fraction = oxygen / 1000F;
            for (int i = 0; i < 10; i++) {
                int x = right - 8 - i * 8;
                float part = Mth.clamp(fraction * 10 - i, 0, 1);
                graphics.fill(x, y + 1, x + 7, y + 8, 0xFF16202A);
                if (part > 0) {
                    int h = Math.max(1, Math.round(6 * part));
                    graphics.fill(x + 1, y + 8 - h, x + 6, y + 7, fraction < 0.2F && minecraft.level.getGameTime() % 20 < 10 ? 0xFFFF5050 : 0xFF6FD8FF);
                }
            }
            GuiDraw.text(graphics, font, Component.literal("O₂"), right - 92, y + 1, 0.6F, 0xFF9FE8FF);
            gui.rightHeight += 10;
        }
        //Messages (au-dessus de la barre d'experience)
        Component line = null;
        int color = 0xFFFFFFFF;
        if (status.pressurized()) {
            line = Component.translatable("survival.pioneers.pressurized");
            color = GuiDraw.color(ChatFormatting.AQUA);
        } else if (!status.safe()) {
            if (oxygen <= 0) {
                line = Component.translatable(oxygen < 0 ? "survival.pioneers.no_suit" : "survival.pioneers.no_oxygen");
                color = GuiDraw.color(ChatFormatting.RED);
            } else if (status.suitTier() < status.requiredTier()) {
                Hazard hazard = Hazard.values()[Mth.clamp(status.hazard(), 0, Hazard.values().length - 1)];
                line = Component.translatable("survival.pioneers.suit_required",
                      Component.translatable("suit.pioneers.short." + status.requiredTier()), hazard.getName());
                color = GuiDraw.color(ChatFormatting.GOLD);
            }
        }
        if (line != null) {
            //1.0.5-beta (lot 26, retour 16) : milieu de la ligne fixe (utilitaire commun d'ancrage)
            int[] at = fr.pioneers.client.look.HudLayouts.survivalLine(width, height);
            GuiDraw.center(graphics, font, line, at[0], at[1], 0.8F, color, 300);
        }
    }
}
