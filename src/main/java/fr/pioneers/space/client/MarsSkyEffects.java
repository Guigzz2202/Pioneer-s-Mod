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
import fr.pioneers.space.sky.client.Celestial.Body;
import fr.pioneers.space.sky.client.CelestialSkyRenderer;
import fr.pioneers.space.sky.client.SkyScene;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.FogRenderer;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ciel de Mars : ciel couleur caramel (couleur du biome), couchers de soleil bleutes, pas de nuages ni de pluie.
 * Pas de Lune vanilla : a la place, Phobos (petit disque qui file d'ouest en est, deux fois par jour, et passe parfois
 * devant le Soleil), Deimos (une etoile brillante), la Terre et Venus en etoiles du matin ou du soir, Jupiter...
 */
public class MarsSkyEffects extends DimensionSpecialEffects {

    private final float[] sunset = new float[4];
    private final SkyScene.Observer observer = SkyScene.observer(Body.MARS);
    private final CelestialSkyRenderer.Style style = new CelestialSkyRenderer.Style();
    private VertexBuffer skyBuffer, darkBuffer;

    public MarsSkyEffects() {
        super(Float.NaN, true, SkyType.NORMAL, false, false);
        style.atmosphere = true;
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return color.multiply(brightness * 0.94 + 0.06, brightness * 0.94 + 0.06, brightness * 0.91 + 0.09);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    /** Sur Mars, le coucher de soleil est bleu. */
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        float cos = (float) Math.cos(timeOfDay * Math.PI * 2);
        if (cos >= -0.4F && cos <= 0.4F) {
            float t = cos / 0.4F * 0.5F + 0.5F;
            float alpha = 1 - (1 - (float) Math.sin(t * Math.PI)) * 0.99F;
            sunset[0] = 0.45F + t * 0.1F;
            sunset[1] = 0.6F + t * 0.1F;
            sunset[2] = 0.95F;
            sunset[3] = alpha * alpha * 0.8F;
            return sunset;
        }
        return null;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projection) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }

    /** Meme deroulement que le ciel vanilla (voute, coucher de soleil, astres, horizon sombre), sans la Lune. */
    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        setupFog.run();
        if (isFoggy) {
            return true;
        }
        FogType fluid = camera.getFluidInCamera();
        if (fluid == FogType.POWDER_SNOW || fluid == FogType.LAVA || blocksSky(camera)) {
            return true;
        }
        if (skyBuffer == null) {
            skyBuffer = skyDisc(16);
            darkBuffer = skyDisc(-16);
        }
        Vec3 color = level.getSkyColor(camera.getPosition(), partialTick);
        float r = (float) color.x, g = (float) color.y, b = (float) color.z;
        FogRenderer.levelFogColor();
        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(r, g, b, 1);
        skyBuffer.bind();
        skyBuffer.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionShader());
        VertexBuffer.unbind();
        RenderSystem.enableBlend();

        float[] sunrise = getSunriseColor(level.getTimeOfDay(partialTick), partialTick);
        if (sunrise != null) {
            RenderSystem.setShader(GameRenderer::getPositionColorShader);
            RenderSystem.setShaderColor(1, 1, 1, 1);
            poseStack.pushPose();
            poseStack.mulPose(Axis.XP.rotationDegrees(90.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(Mth.sin(level.getSunAngle(partialTick)) < 0.0F ? 180.0F : 0.0F));
            poseStack.mulPose(Axis.ZP.rotationDegrees(90.0F));
            Matrix4f matrix = poseStack.last().pose();
            BufferBuilder builder = Tesselator.getInstance().getBuilder();
            builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION_COLOR);
            builder.vertex(matrix, 0.0F, 100.0F, 0.0F).color(sunrise[0], sunrise[1], sunrise[2], sunrise[3]).endVertex();
            for (int j = 0; j <= 16; ++j) {
                float angle = (float) j * ((float) Math.PI * 2F) / 16.0F;
                float sin = Mth.sin(angle), cos = Mth.cos(angle);
                builder.vertex(matrix, sin * 120.0F, cos * 120.0F, -cos * 40.0F * sunrise[3]).color(sunrise[0], sunrise[1], sunrise[2], 0.0F).endVertex();
            }
            BufferUploader.drawWithShader(builder.end());
            poseStack.popPose();
        }

        style.starBrightness = level.getStarBrightness(partialTick);
        style.rain = level.getRainLevel(partialTick);
        style.skyR = r;
        style.skyG = g;
        style.skyB = b;
        SkyScene scene = SkyScene.compute(observer, CelestialSkyRenderer.days(level), level.getTimeOfDay(partialTick) * 360.0F);
        CelestialSkyRenderer.render(scene, level, partialTick, poseStack, projection, style);
        setupFog.run();

        RenderSystem.depthMask(false);
        RenderSystem.setShaderColor(0, 0, 0, 1);
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player != null && minecraft.player.getEyePosition(partialTick).y - level.getLevelData().getHorizonHeight(level) < 0.0D) {
            poseStack.pushPose();
            poseStack.translate(0.0F, 12.0F, 0.0F);
            darkBuffer.bind();
            darkBuffer.drawWithShader(poseStack.last().pose(), projection, GameRenderer.getPositionShader());
            VertexBuffer.unbind();
            poseStack.popPose();
        }
        RenderSystem.setShaderColor(1, 1, 1, 1);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE_MINUS_SRC_ALPHA);
        RenderSystem.depthMask(true);
        return true;
    }

    private static boolean blocksSky(Camera camera) {
        return camera.getEntity() instanceof LivingEntity living && (living.hasEffect(MobEffects.BLINDNESS) || living.hasEffect(MobEffects.DARKNESS));
    }

    /** Voute du ciel (identique au disque vanilla). */
    private static VertexBuffer skyDisc(float y) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        float f = Math.signum(y) * 512.0F;
        builder.begin(VertexFormat.Mode.TRIANGLE_FAN, DefaultVertexFormat.POSITION);
        builder.vertex(0.0D, y, 0.0D).endVertex();
        for (int i = -180; i <= 180; i += 45) {
            builder.vertex(f * Mth.cos((float) i * ((float) Math.PI / 180F)), y, 512.0F * Mth.sin((float) i * ((float) Math.PI / 180F))).endVertex();
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }
}
