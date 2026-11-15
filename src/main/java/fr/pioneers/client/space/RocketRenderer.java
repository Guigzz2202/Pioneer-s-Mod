package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.common.PiClientConfig;
import fr.pioneers.space.RocketEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Rendu de la fusee, avec un leger tremblement pendant l'allumage et le decollage.
 */
public class RocketRenderer extends EntityRenderer<RocketEntity> {

    /** Duree pendant laquelle on dessine le premier etage qui retombe (ticks). */
    private static final int FALL_TICKS = 100;
    private final RocketModels models;

    public RocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        this.models = new RocketModels(context::bakeLayer);
        this.shadowRadius = 0.9F;
    }

    @Override
    public void render(@NotNull RocketEntity rocket, float yaw, float partialTick, @NotNull PoseStack poseStack, @NotNull MultiBufferSource buffer, int light) {
        poseStack.pushPose();
        float shake = switch (rocket.getState()) {
            case COUNTDOWN -> rocket.getTimer() > RocketEntity.COUNTDOWN_TICKS - 60 ? 0.02F : 0;
            case LAUNCHING -> 0.03F;
            default -> 0;
        };
        shake *= PiClientConfig.CAMERA_SHAKE.get().floatValue();
        if (shake > 0) {
            float t = rocket.tickCount + partialTick;
            poseStack.translate(Mth.sin(t * 2.7F) * shake, 0, Mth.cos(t * 3.1F) * shake);
        }
        poseStack.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        boolean staged = rocket.isStaged();
        poseStack.pushPose();
        poseStack.scale(-1, -1, 1);
        poseStack.translate(0, -1.501, 0);
        models.render(rocket.getTier(), poseStack, buffer, RenderType::entityCutoutNoCull, light, OverlayTexture.NO_OVERLAY,
              staged ? RocketModels.Stage.UPPER : RocketModels.Stage.ALL);
        poseStack.popPose();
        //Premier etage largue : il ralentit, bascule et retombe pendant que la fusee continue de monter
        if (staged && rocket.getState() == RocketEntity.State.LAUNCHING && PiClientConfig.STAGE_SEPARATION.get()) {
            float dt = rocket.stagingAge(partialTick);
            if (dt < FALL_TICKS) {
                //La fusee monte a 2 blocs/t ; l'etage, lui, perd sa vitesse sous l'effet de la gravite
                float drop = 0.12F * dt + 0.035F * dt * dt;
                float h = (float) rocket.stageHeight();
                poseStack.pushPose();
                poseStack.translate(0.04F * dt, -drop, 0.02F * dt);
                poseStack.translate(0, h * 0.5F, 0);
                poseStack.mulPose(Axis.XP.rotationDegrees(dt * 0.9F));
                poseStack.mulPose(Axis.ZP.rotationDegrees(dt * 0.4F));
                poseStack.translate(0, -h * 0.5F, 0);
                poseStack.scale(-1, -1, 1);
                poseStack.translate(0, -1.501, 0);
                models.render(rocket.getTier(), poseStack, buffer, RenderType::entityCutoutNoCull, light, OverlayTexture.NO_OVERLAY, RocketModels.Stage.LOWER);
                poseStack.popPose();
            }
        }
        poseStack.popPose();
        super.render(rocket, yaw, partialTick, poseStack, buffer, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull RocketEntity rocket) {
        return RocketModels.texture(rocket.getTier());
    }
}
