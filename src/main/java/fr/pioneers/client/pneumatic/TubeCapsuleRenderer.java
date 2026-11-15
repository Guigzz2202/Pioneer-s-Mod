package fr.pioneers.client.pneumatic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.pneumatic.network.TubeCapsuleEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/**
 * Cabine vitree des tubes pneumatiques : modele de bloc {@code block/pneumatic/capsule_cabin} (bagues de laiton, verre, banquette),
 * oriente dans le sens de la marche. Le voyageur, assis dedans, est dessine reduit ({@link ClientPneumatic}).
 */
public class TubeCapsuleRenderer extends EntityRenderer<TubeCapsuleEntity> {

    public static final ResourceLocation CABIN = Pioneers.rl("block/pneumatic/capsule_cabin");

    public TubeCapsuleRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(@NotNull TubeCapsuleEntity capsule, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(-capsule.getViewYRot(partialTick)));
        //0.20.39 : pente des hypertubes courbes (autour du centre de la cabine)
        pose.translate(0, 0.5, 0);
        pose.mulPose(Axis.XP.rotationDegrees(capsule.getViewXRot(partialTick)));
        pose.translate(0, -0.5, 0);
        pose.translate(-0.5, 0, -0.5);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        var model = Minecraft.getInstance().getModelManager().getModel(CABIN);
        renderer.renderModel(pose.last(), buffers.getBuffer(Sheets.translucentCullBlockSheet()), null, model, 1, 1, 1, light, OverlayTexture.NO_OVERLAY,
              ModelData.EMPTY, RenderType.translucent());
        pose.popPose();
        super.render(capsule, yaw, partialTick, pose, buffers, light);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(@NotNull TubeCapsuleEntity capsule) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
