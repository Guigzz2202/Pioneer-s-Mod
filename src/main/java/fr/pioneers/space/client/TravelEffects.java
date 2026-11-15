package fr.pioneers.space.client;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.space.sky.client.SkyStars;
import fr.pioneers.core.common.PiClientConfig;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.CapsuleEntity;
import fr.pioneers.space.RocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Effets de camera du voyage : tremblement au decollage (et a la rentree atmospherique de la capsule), champ de vision qui s'elargit
 * sous l'acceleration, ciel qui passe du bleu au noir etoile pendant la montee, terrain visible de plus loin en altitude.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class TravelEffects {

    private TravelEffects() {
    }

    private static VertexBuffer stars;

    /** Altitude de la montee (0 au sol, 1 en orbite), lissee. */
    private static float ascent(float partialTick) {
        Entity vehicle = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getVehicle();
        if (vehicle instanceof RocketEntity rocket) {
            return switch (rocket.getState()) {
                case LAUNCHING -> {
                    float t = Mth.clamp((rocket.getTimer() + partialTick) / RocketEntity.ASCENT_TICKS, 0, 1);
                    yield t * t * (3 - 2 * t);
                }
                case ORBIT, TRANSIT -> 1;
                default -> 0;
            };
        }
        return 0;
    }

    /** Intensite du tremblement (0 a 1). */
    private static float shake(float partialTick) {
        Entity vehicle = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getVehicle();
        if (vehicle instanceof RocketEntity rocket) {
            float timer = rocket.getTimer() + partialTick;
            return switch (rocket.getState()) {
                case COUNTDOWN -> timer > RocketEntity.COUNTDOWN_TICKS - 60 ? 0.25F * (timer - RocketEntity.COUNTDOWN_TICKS + 60) / 60F : 0;
                case LAUNCHING -> {
                    float s = Mth.clamp(1.1F - timer / RocketEntity.ASCENT_TICKS, 0, 1);
                    float staging = Math.abs(timer - RocketEntity.STAGING_TICK) < 10 ? 1 - Math.abs(timer - RocketEntity.STAGING_TICK) / 10 : 0;
                    yield Math.max(s, staging);
                }
                default -> 0;
            };
        }
        if (vehicle instanceof CapsuleEntity capsule && !capsule.hasLanded()) {
            float vs = -capsule.getVerticalSpeed();
            float reentry = capsule.isAirless() ? 0 : Mth.clamp((vs - 0.9F) / 1.2F, 0, 0.7F);
            float thrust = capsule.isMainThrusting() ? 0.18F : 0;
            return Math.max(reentry, thrust);
        }
        return 0;
    }

    @SubscribeEvent
    public static void onCamera(ViewportEvent.ComputeCameraAngles event) {
        float s = shake((float) event.getPartialTick()) * PiClientConfig.CAMERA_SHAKE.get().floatValue();
        if (s <= 0.001F) {
            return;
        }
        float t = (float) RenderClock.time((float) event.getPartialTick());   //1.0.5-beta (lot 29) : horloge de rendu continue
        float amp = s * 1.6F;
        event.setPitch(event.getPitch() + (Mth.sin(t * 1.9F) + Mth.sin(t * 3.7F) * 0.5F) * amp);
        event.setYaw(event.getYaw() + Mth.sin(t * 2.3F + 1) * amp * 0.6F);
        event.setRoll(event.getRoll() + Mth.sin(t * 1.3F + 2) * amp * 0.8F);
    }

    @SubscribeEvent
    public static void onFov(ViewportEvent.ComputeFov event) {
        Entity vehicle = Minecraft.getInstance().player == null ? null : Minecraft.getInstance().player.getVehicle();
        if (vehicle instanceof RocketEntity rocket && rocket.getState() == RocketEntity.State.LAUNCHING) {
            float t = Mth.clamp((rocket.getTimer() + (float) event.getPartialTick()) / RocketEntity.ASCENT_TICKS, 0, 1);
            float boost = Mth.sin(Mth.clamp(t * 3, 0, 1) * Mth.HALF_PI) * (1 - t * 0.7F);
            event.setFOV(event.getFOV() * (1 + 0.14F * boost));
        }
    }

    @SubscribeEvent
    public static void onFogColor(ViewportEvent.ComputeFogColor event) {
        float a = ascent((float) event.getPartialTick());
        if (a > 0) {
            float k = 1 - a * 0.97F;
            event.setRed(event.getRed() * k);
            event.setGreen(event.getGreen() * k);
            event.setBlue(event.getBlue() * k + 0.01F * a);
        }
    }

    @SubscribeEvent
    public static void onFog(ViewportEvent.RenderFog event) {
        float a = ascent((float) event.getPartialTick());
        if (a > 0.05F) {
            //En altitude, le sol reste visible jusqu'a l'horizon
            event.setFarPlaneDistance(Math.max(event.getFarPlaneDistance(), event.getFarPlaneDistance() * (1 + a * 2)));
            event.setNearPlaneDistance(event.getNearPlaneDistance() + a * 60);
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onRender(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        float a = ascent(event.getPartialTick());
        if (a <= 0.01F) {
            return;
        }
        PoseStack pose = event.getPoseStack();
        Matrix4f matrix = pose.last().pose();
        RenderSystem.depthMask(false);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableCull();
        RenderSystem.setShader(GameRenderer::getPositionColorShader);
        //Voile noir sur le ciel
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        float d = 80, alpha = Math.min(1, a * 1.05F);
        float[][] faces = {
              {-d, -d, -d, d, -d, -d, d, d, -d, -d, d, -d}, {-d, -d, d, -d, d, d, d, d, d, d, -d, d},
              {-d, -d, -d, -d, d, -d, -d, d, d, -d, -d, d}, {d, -d, -d, d, -d, d, d, d, d, d, d, -d},
              {-d, d, -d, d, d, -d, d, d, d, -d, d, d}, {-d, -d, -d, -d, -d, d, d, -d, d, d, -d, -d}};
        for (float[] f : faces) {
            for (int i = 0; i < 4; i++) {
                builder.vertex(matrix, f[i * 3], f[i * 3 + 1], f[i * 3 + 2]).color(0.004F, 0.006F, 0.02F, alpha).endVertex();
            }
        }
        BufferUploader.drawWithShader(builder.end());
        //Etoiles qui apparaissent
        if (stars == null) {
            stars = SkyStars.buildStars();
        }
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-90.0F));
        pose.mulPose(Axis.XP.rotationDegrees(30));
        RenderSystem.setShaderColor(1, 1, 1, Mth.clamp((a - 0.3F) / 0.6F, 0, 1));
        stars.bind();
        stars.drawWithShader(pose.last().pose(), event.getProjectionMatrix(), GameRenderer.getPositionColorShader());
        VertexBuffer.unbind();
        RenderSystem.setShaderColor(1, 1, 1, 1);
        pose.popPose();
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
    }
}
