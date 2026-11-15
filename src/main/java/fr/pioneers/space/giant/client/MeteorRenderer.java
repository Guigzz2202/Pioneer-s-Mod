package fr.pioneers.space.giant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.space.event.MeteorEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * Meteore : la meteorite qui tourne sur elle-meme, chauffee a blanc (pleine lumiere) ; la trainee est faite de particules.
 */
public class MeteorRenderer extends EntityRenderer<MeteorEntity> {

    public MeteorRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public void render(@NotNull MeteorEntity meteor, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light) {
        float time = meteor.tickCount + partialTick;
        pose.pushPose();
        float size = 1.2F * meteor.getSize();
        pose.scale(size, size, size);
        pose.mulPose(Axis.XP.rotationDegrees(time * 12));
        pose.mulPose(Axis.YP.rotationDegrees(time * 7));
        pose.translate(-0.5, -0.5, -0.5);
        Minecraft.getInstance().getBlockRenderer().renderSingleBlock(meteor.getBlock(), pose, buffer, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        super.render(meteor, yaw, partialTick, pose, buffer, light);
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull MeteorEntity meteor) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
