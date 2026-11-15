package fr.pioneers.space.client;

import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.guide.client.GuideScreen;
import fr.pioneers.space.research.client.TechTreeScreen;
import fr.pioneers.core.common.PiClientConfig;
import fr.pioneers.core.common.PiLang;
import fr.pioneers.core.network.PacketCapsuleInput;
import fr.pioneers.core.network.PacketRocketLaunch;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.space.CapsuleEntity;
import fr.pioneers.space.RocketEntity;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Cote client de la fusee : touche Saut (decollage / carte), ouverture automatique de la carte en orbite,
 * jauge d'altitude et informations du passager.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class RocketClientEvents {

    private RocketClientEvents() {
    }

    private static boolean jumpWasDown;
    /** Rocket deja en orbite pour laquelle la carte s'est ouverte automatiquement. */
    private static int mapShownFor = -1;
    /** Fusee en transit pour laquelle la cinematique s'est ouverte. */
    private static int transitShownFor = -1;
    /** Capsule pour laquelle le son des propulseurs tourne. */
    private static int thrusterSoundFor = -1;

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        while (PiKeys.SOLAR_MAP.consumeClick()) {
            if (minecraft.screen == null) {
                if (minecraft.player.getVehicle() instanceof RocketEntity rocket && rocket.getState() == RocketEntity.State.ORBIT) {
                    DestinationScreen.open(rocket);
                } else if (minecraft.player.getVehicle() instanceof RocketEntity rocket && rocket.getState() == RocketEntity.State.TRANSIT) {
                    TransitScreen.open(rocket);
                } else {
                    minecraft.setScreen(new DestinationScreen(minecraft.player.level().dimension(), minecraft.player.getX(), null));
                }
            }
        }
        while (PiKeys.RESEARCH.consumeClick()) {
            if (minecraft.screen == null) {
                minecraft.setScreen(new TechTreeScreen(null, null));
            }
        }
        while (PiKeys.GUIDE.consumeClick()) {
            if (minecraft.screen == null) {
                GuideScreen.open(null);
            }
        }
        capsuleControls(minecraft);
        if (minecraft.player.getVehicle() instanceof RocketEntity rocket) {
            //En orbite, la carte reste ouverte tant qu'aucune destination n'est choisie (rouverte apres le menu pause, l'inventaire...)
            if (rocket.getState() == RocketEntity.State.ORBIT && minecraft.screen == null
                && System.currentTimeMillis() - DestinationScreen.departedAt > 2000) {
                mapShownFor = rocket.getId();
                DestinationScreen.open(rocket);
            }
            if (rocket.getState() == RocketEntity.State.TRANSIT && transitShownFor != rocket.getId() && PiClientConfig.TRANSIT_SCREEN.get()
                && (minecraft.screen == null || minecraft.screen instanceof DestinationScreen)) {
                transitShownFor = rocket.getId();
                TransitScreen.open(rocket);
            }
            if (minecraft.screen == null) {
                boolean down = minecraft.options.keyJump.isDown();
                if (down && !jumpWasDown) {
                    if (rocket.getState() == RocketEntity.State.IDLE) {
                        PiNetwork.sendToServer(new PacketRocketLaunch());
                    } else if (rocket.getState() == RocketEntity.State.ORBIT) {
                        DestinationScreen.open(rocket);
                    }
                }
                jumpWasDown = down;
            } else {
                jumpWasDown = false;
            }
        } else {
            jumpWasDown = false;
        }
    }

    @SubscribeEvent
    public static void onOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.HOTBAR.type()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        if (minecraft.player.getVehicle() instanceof RocketEntity rocket) {
            renderRocketHud(minecraft, graphics, rocket);
        } else if (minecraft.player.getVehicle() instanceof CapsuleEntity capsule && !capsule.hasLanded()) {
            CapsuleHud.render(minecraft, graphics, capsule);
        }
    }

    /** Commandes de la capsule : envoyees au serveur a chaque tick de la descente. */
    private static void capsuleControls(Minecraft minecraft) {
        boolean chute = false, autopilot = false;
        while (PiKeys.PARACHUTE.consumeClick()) {
            chute = true;
        }
        while (PiKeys.AUTOPILOT.consumeClick()) {
            autopilot = true;
        }
        if (!(minecraft.player.getVehicle() instanceof CapsuleEntity capsule) || capsule.hasLanded()) {
            thrusterSoundFor = -1;
            return;
        }
        if (thrusterSoundFor != capsule.getId()) {
            thrusterSoundFor = capsule.getId();
            minecraft.getSoundManager().play(new CapsuleThrusterSound(capsule));
        }
        boolean free = minecraft.screen == null;
        var input = minecraft.player.input;
        float forward = free ? input.forwardImpulse : 0, strafe = free ? input.leftImpulse : 0;
        boolean thrust = free && minecraft.options.keyJump.isDown();
        PiNetwork.sendToServer(new PacketCapsuleInput(forward, strafe, thrust, chute, autopilot));
    }

    private static void renderRocketHud(Minecraft minecraft, GuiGraphics graphics, RocketEntity rocket) {
        int x = 8, y = 8;
        int fuel = rocket.getFuel();
        if (!rocket.isFlying() || rocket.getState() == RocketEntity.State.COUNTDOWN) {
            int capacity = rocket.getFuelCapacity();
            ChatFormatting fuelColor = fuel >= capacity ? ChatFormatting.GREEN : ChatFormatting.GOLD;
            graphics.drawString(minecraft.font, PiLang.ROCKET_HUD_FUEL.translateColored(fuelColor, fuel, capacity), x, y, 0xFFFFFF);
            int width = 100;
            graphics.fill(x, y + 12, x + width + 2, y + 18, 0xAA000000);
            graphics.fill(x + 1, y + 13, x + 1 + width * fuel / capacity, y + 17, fuel >= capacity ? 0xFF55FF55 : 0xFFFFAA00);
        }
        Component status = switch (rocket.getState()) {
            case IDLE -> PiLang.ROCKET_HUD_LAUNCH.translateColored(ChatFormatting.GRAY, minecraft.options.keyJump.getTranslatedKeyMessage());
            case COUNTDOWN -> PiLang.ROCKET_HUD_COUNTDOWN.translateColored(ChatFormatting.YELLOW,
                  RocketEntity.countdownSeconds(rocket.getTimer()));
            case LAUNCHING -> PiLang.ROCKET_LIFTOFF.translateColored(ChatFormatting.GOLD);
            case ORBIT -> PiLang.ROCKET_HUD_CHOOSE.translateColored(ChatFormatting.AQUA, minecraft.options.keyJump.getTranslatedKeyMessage());
            case TRANSIT -> Component.translatable("transit.pioneers.hud", Math.round(rocket.getTransitProgress(0) * 100),
                  PiKeys.SOLAR_MAP.getTranslatedKeyMessage()).withStyle(ChatFormatting.AQUA);
        };
        graphics.drawString(minecraft.font, status, x, y + 24, 0xFFFFFF);

        //Jauge d'altitude (droite de l'ecran) pendant la montee
        if (rocket.getState() == RocketEntity.State.LAUNCHING || rocket.getState() == RocketEntity.State.ORBIT
            || rocket.getState() == RocketEntity.State.TRANSIT) {
            int screenW = graphics.guiWidth(), screenH = graphics.guiHeight();
            int barH = Math.min(140, screenH - 80), barX = screenW - 26, barY = (screenH - barH) / 2;
            float fraction = rocket.getAltitudeFraction();
            graphics.fill(barX - 1, barY - 1, barX + 11, barY + barH + 1, 0xCC000000);
            //Degrade : sol (bleu clair) -> espace (violet sombre)
            for (int i = 0; i < barH; i++) {
                float t = 1 - i / (float) barH;
                int r = (int) Mth.lerp(t, 90, 30), g = (int) Mth.lerp(t, 170, 20), b = (int) Mth.lerp(t, 255, 80);
                int alpha = i >= barH * (1 - fraction) ? 0xFF : 0x40;
                graphics.fill(barX, barY + i, barX + 10, barY + i + 1, alpha << 24 | r << 16 | g << 8 | b);
            }
            int markY = barY + (int) (barH * (1 - fraction));
            graphics.fill(barX - 4, markY - 1, barX + 14, markY + 1, 0xFFFFFFFF);
            int km = (int) (fraction * RocketEntity.ORBIT_ALTITUDE_KM);
            Component label = PiLang.ROCKET_HUD_ALTITUDE.translate(km);
            //1.0.5-beta (lot 26, retour 16) : etiquettes calees par leur bord droit quand les kilometres changent (utilitaire commun d'ancrage)
            graphics.drawString(minecraft.font, label, fr.pioneers.client.look.HudLayouts.gaugeLabel(barX - 6, minecraft.font.width(label)), markY - 4, 0xFFFFFF);
            graphics.drawString(minecraft.font, PiLang.ROCKET_ORBIT.translate(),
                  fr.pioneers.client.look.HudLayouts.gaugeLabel(barX - 6, minecraft.font.width(PiLang.ROCKET_ORBIT.translate())), barY - 4,
                  fraction >= 1 ? 0x55FFFF : 0x777777);
        }
    }
}
