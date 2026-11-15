package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.space.LaunchVisualEntity;
import fr.pioneers.space.RocketTier;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Rendu des fusees "spectacle" (lancements automatiques : sondes et routes logistiques).
 */
public class LaunchVisualRenderer extends EntityRenderer<LaunchVisualEntity> {

    private final RocketModels rockets;

    public LaunchVisualRenderer(EntityRendererProvider.Context context) {
        super(context);
        rockets = new RocketModels(context::bakeLayer);
        shadowRadius = 0.8F;
    }

    @Override
    public void render(@NotNull LaunchVisualEntity entity, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light) {
        pose.pushPose();
        float t = entity.tickCount + partialTick;
        float shake = entity.isDescending() ? 0.015F : 0.03F;
        pose.translate(Mth.sin(t * 2.7F) * shake, 0, Mth.cos(t * 3.1F) * shake);
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        rockets.render(RocketTier.byIndex(entity.getKind()), pose, buffer, RenderType::entityCutoutNoCull, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(entity, yaw, partialTick, pose, buffer, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull LaunchVisualEntity entity) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(@NotNull LaunchVisualEntity entity, @NotNull Frustum frustum, double x, double y, double z) {
        return true;
    }
}
