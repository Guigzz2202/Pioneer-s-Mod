package fr.pioneers.space.client;

import fr.pioneers.Pioneers;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.giant.GiantEvents;
import fr.pioneers.space.giant.PocketKind;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Geantes gazeuses, cote client : vol en combinaison atmospherique (planer, propulseurs dorsaux avec Saut, pique avec Sneak,
 * meilleure manoeuvrabilite en l'air) et altimetre des couches (brume, pont nuageux, orages, pression ecrasante).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class GiantClient {

    private GiantClient() {
    }

    /** Vitesse de plane, de montee maximale et de pique. */
    private static final double GLIDE = -0.1, CLIMB = 0.42, DIVE = -0.9;

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof LocalPlayer player) || player.isPassenger()) {
            return;
        }
        if (GiantEvents.canSuitFly(player) && player.getAbilities().flying && !player.isCreative()) {
            //Pas de vol libre : seulement les propulseurs de la combinaison
            player.getAbilities().flying = false;
        }
        if (!GiantEvents.canSuitFly(player) || player.getAbilities().flying || player.onGround() || player.isInWater()) {
            return;
        }
        Vec3 motion = player.getDeltaMovement();
        double y = motion.y;
        boolean jump = player.input.jumping, sneak = player.input.shiftKeyDown;
        if (jump) {
            y = Math.min(y + 0.09, CLIMB);
            if (player.tickCount % 2 == 0) {
                Vec3 back = Vec3.directionFromRotation(0, player.getYRot()).scale(-0.35);
                player.level().addParticle(ParticleTypes.CLOUD, player.getX() + back.x, player.getY() + 0.9, player.getZ() + back.z, 0, -0.15, 0);
                player.level().addParticle(ParticleTypes.SMALL_FLAME, player.getX() + back.x, player.getY() + 0.8, player.getZ() + back.z, 0, -0.2, 0);
            }
        } else if (sneak) {
            y = Math.max(y - 0.04, DIVE);
        } else if (y < GLIDE) {
            y += (GLIDE - y) * 0.35;
        }
        //Manoeuvrabilite en vol
        float forward = player.input.forwardImpulse, strafe = player.input.leftImpulse;
        double mx = motion.x, mz = motion.z;
        if (forward != 0 || strafe != 0) {
            float yaw = player.getYRot() * Mth.DEG_TO_RAD;
            double sin = Mth.sin(yaw), cos = Mth.cos(yaw);
            mx += (strafe * cos - forward * sin) * 0.022;
            mz += (forward * cos + strafe * sin) * 0.022;
        }
        double h = Math.sqrt(mx * mx + mz * mz);
        if (h > 0.55) {
            mx *= 0.55 / h;
            mz *= 0.55 / h;
        }
        player.setDeltaMovement(mx, y, mz);
        player.fallDistance = 0;
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !PiDimensions.isGasGiant(player.level()) || player.isPassenger() || minecraft.options.hideGui) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        int sw = graphics.guiWidth(), sh = graphics.guiHeight();
        //Altimetre des couches (bord droit)
        int x = sw - 18, top = sh / 2 - 70, height = 140;
        float maxY = 384;
        graphics.fill(x - 1, top - 1, x + 9, top + height + 1, 0xA0000000);
        layer(graphics, x, top, height, maxY, PocketKind.Layer.HAZE.minY, maxY, 0xFFB8C8D8);
        layer(graphics, x, top, height, maxY, PocketKind.Layer.DECK.minY, PocketKind.Layer.HAZE.minY, 0xFFC89A60);
        layer(graphics, x, top, height, maxY, PocketKind.Layer.STORM.minY, PocketKind.Layer.DECK.minY, 0xFF505878);
        layer(graphics, x, top, height, maxY, GiantEvents.CRUSH_Y, PocketKind.Layer.STORM.minY, 0xFF282838);
        layer(graphics, x, top, height, maxY, 0, GiantEvents.CRUSH_Y, 0xFF601818);
        int my = top + Math.round(height * (1 - Mth.clamp((float) player.getY() / maxY, 0, 1)));
        graphics.fill(x - 4, my - 1, x + 12, my + 1, 0xFFFFFFFF);
        PocketKind.Layer layer = PocketKind.Layer.at(player.getY());
        Component label = player.getY() < GiantEvents.CRUSH_Y ? Component.translatable("giant.pioneers.layer.crush")
                                                              : Component.translatable(layer.getTranslationKey());
        //1.0.5-beta (lot 26, retour 16) : etiquette calee par son bord droit (utilitaire commun d'ancrage)
        graphics.drawString(minecraft.font, label, fr.pioneers.client.look.HudLayouts.gaugeLabel(x - 6, minecraft.font.width(label)), my - 4, player.getY() < GiantEvents.CRUSH_Y ? 0xFF5050 : 0xE0E0E0);
        if (!GiantEvents.canSuitFly(player) && !player.isCreative()) {
            graphics.drawCenteredString(minecraft.font, Component.translatable("giant.pioneers.no_suit"), sw / 2, 30, 0xFF6060);
        }
    }

    private static void layer(GuiGraphics graphics, int x, int top, int height, float maxY, float from, float to, int color) {
        int y0 = top + Math.round(height * (1 - to / maxY)), y1 = top + Math.round(height * (1 - from / maxY));
        graphics.fill(x, y0, x + 8, y1, color);
    }
}
