package fr.pioneers.space.giant.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.space.giant.GasPocketEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Poche de gaz : deux coquilles lumineuses qui ondulent (couleur du contenu), un coeur plus dense, et pour les poches de cristaux
 * (hydrogene metallique, diamants de pluie) des eclats qui tournent a l'interieur. Les poches designees ont un anneau clignotant.
 */
public class GasPocketRenderer extends EntityRenderer<GasPocketEntity> {

    private static final int LON = 16, LAT = 10;

    public GasPocketRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    @Override
    public void render(@NotNull GasPocketEntity pocket, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffer, int light) {
        float time = pocket.tickCount + partialTick + pocket.getId() * 7;
        float radius = pocket.getRadius();
        int c = pocket.getKind().color;
        float r = (c >> 16 & 255) / 255F, g = (c >> 8 & 255) / 255F, b = (c & 255) / 255F;
        //Apparition et dissipation en fondu
        float life = Math.min(1, pocket.getAge() / 40F) * Mth.clamp((GasPocketEntity.LIFETIME - pocket.getAge()) / 200F, 0, 1);
        pose.pushPose();
        pose.translate(0, radius, 0);
        pose.mulPose(Axis.YP.rotationDegrees(time * 0.6F));
        VertexConsumer consumer = buffer.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        shell(consumer, matrix, radius, time, r, g, b, 0.16F * life, 0.06F);
        shell(consumer, matrix, radius * 0.62F, time * 1.7F, Math.min(1, r * 1.2F), Math.min(1, g * 1.2F), Math.min(1, b * 1.2F), 0.22F * life, 0.1F);
        shell(consumer, matrix, radius * 0.3F, time * 2.3F, 1, 1, 1, 0.25F * life, 0.15F);
        if (pocket.isMarked()) {
            float pulse = 0.5F + 0.5F * Mth.sin(time * 0.3F);
            ring(consumer, matrix, radius * 1.15F, 0.3F + 0.5F * pulse);
        }
        pose.popPose();
        //Eclats de cristaux
        Item item = pocket.getKind().item();
        if (item != null) {
            ItemStack stack = new ItemStack(item);
            int count = Math.min(6, 2 + pocket.getAmount() / 3);
            for (int i = 0; i < count; i++) {
                pose.pushPose();
                float a = time * 0.02F + i * Mth.TWO_PI / count;
                pose.translate(Mth.cos(a) * radius * 0.5F, radius + Mth.sin(time * 0.05F + i) * radius * 0.3F, Mth.sin(a) * radius * 0.5F);
                pose.mulPose(Axis.YP.rotationDegrees(time * 3 + i * 40));
                pose.scale(0.6F, 0.6F, 0.6F);
                Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.GROUND, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                      pose, buffer, pocket.level(), pocket.getId() + i);
                pose.popPose();
            }
        }
        super.render(pocket, yaw, partialTick, pose, buffer, light);
    }

    /** Coquille spherique ondulante (quads de couleur, rendu additif). */
    private static void shell(VertexConsumer consumer, Matrix4f matrix, float radius, float time, float r, float g, float b, float alpha, float wobble) {
        for (int i = 0; i < LAT; i++) {
            float t0 = Mth.PI * i / LAT - Mth.HALF_PI, t1 = Mth.PI * (i + 1) / LAT - Mth.HALF_PI;
            for (int j = 0; j < LON; j++) {
                float p0 = Mth.TWO_PI * j / LON, p1 = Mth.TWO_PI * (j + 1) / LON;
                vertex(consumer, matrix, radius, t0, p0, time, wobble, r, g, b, alpha);
                vertex(consumer, matrix, radius, t0, p1, time, wobble, r, g, b, alpha);
                vertex(consumer, matrix, radius, t1, p1, time, wobble, r, g, b, alpha);
                vertex(consumer, matrix, radius, t1, p0, time, wobble, r, g, b, alpha);
            }
        }
    }

    private static void vertex(VertexConsumer consumer, Matrix4f matrix, float radius, float lat, float lon, float time, float wobble, float r, float g, float b,
          float alpha) {
        float k = radius * (1 + wobble * Mth.sin(lon * 3 + time * 0.08F) * Mth.cos(lat * 2 + time * 0.05F));
        float x = Mth.cos(lat) * Mth.cos(lon) * k, y = Mth.sin(lat) * k, z = Mth.cos(lat) * Mth.sin(lon) * k;
        //Bords plus lumineux que le centre de la face visible
        float edge = 0.6F + 0.4F * Math.abs(Mth.sin(lat));
        consumer.vertex(matrix, x, y, z).color(r, g, b, alpha * edge).endVertex();
    }

    /** Anneau de designation. */
    private static void ring(VertexConsumer consumer, Matrix4f matrix, float radius, float alpha) {
        int n = 32;
        for (int i = 0; i < n; i++) {
            float a0 = Mth.TWO_PI * i / n, a1 = Mth.TWO_PI * (i + 1) / n;
            float w = 0.12F;
            consumer.vertex(matrix, Mth.cos(a0) * radius, -w, Mth.sin(a0) * radius).color(0.5F, 1, 1, alpha).endVertex();
            consumer.vertex(matrix, Mth.cos(a1) * radius, -w, Mth.sin(a1) * radius).color(0.5F, 1, 1, alpha).endVertex();
            consumer.vertex(matrix, Mth.cos(a1) * radius, w, Mth.sin(a1) * radius).color(0.5F, 1, 1, alpha).endVertex();
            consumer.vertex(matrix, Mth.cos(a0) * radius, w, Mth.sin(a0) * radius).color(0.5F, 1, 1, alpha).endVertex();
        }
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull GasPocketEntity pocket) {
        return TextureAtlas.LOCATION_BLOCKS;
    }
}
