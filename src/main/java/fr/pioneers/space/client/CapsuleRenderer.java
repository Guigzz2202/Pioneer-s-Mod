package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.space.CapsuleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Rendu de la capsule et de son parachute (deploiement anime, balancement).
 */
public class CapsuleRenderer extends EntityRenderer<CapsuleEntity> {

    private static final ResourceLocation TEXTURE = Pioneers.rl("textures/entity/capsule.png");
    private static final ResourceLocation CHUTE_TEXTURE = Pioneers.rl("textures/entity/rocket_parachute.png");
    private final CapsuleModel model;
    private final ParachuteModel parachute;

    public CapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        model = new CapsuleModel(context.bakeLayer(CapsuleModel.LAYER));
        parachute = new ParachuteModel(context.bakeLayer(ParachuteModel.LAYER));
        shadowRadius = 0.8F;
    }

    @Override
    public void render(@NotNull CapsuleEntity capsule, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light) {
        pose.pushPose();
        float t = capsule.getTimer() + partialTick;
        if (capsule.isChuteDeployed()) {
            //La capsule se balance sous la voilure
            pose.mulPose(Axis.ZP.rotationDegrees(Mth.sin(t * 0.05F) * 4));
            pose.mulPose(Axis.XP.rotationDegrees(Mth.cos(t * 0.04F) * 3));
        }
        pose.mulPose(Axis.YP.rotationDegrees(180.0F - yaw));
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        model.renderToBuffer(pose, buffer.getBuffer(model.renderType(TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        if (capsule.isChuteDeployed()) {
            float open = Mth.clamp(t / 20F, 0, 1);
            float eased = 1 - (1 - open) * (1 - open);
            pose.pushPose();
            pose.translate(0, (24 - CapsuleModel.HEIGHT) / 16F, 0);
            pose.scale(0.15F + 0.85F * eased, 0.4F + 0.6F * eased, 0.15F + 0.85F * eased);
            parachute.renderToBuffer(pose, buffer.getBuffer(RenderType.entityCutoutNoCull(CHUTE_TEXTURE)), light, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
            pose.popPose();
        }
        pose.popPose();
        super.render(capsule, yaw, partialTick, pose, buffer, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull CapsuleEntity capsule) {
        return TEXTURE;
    }
}
