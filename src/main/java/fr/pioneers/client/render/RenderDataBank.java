package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.tile.TileEntityDataBank;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Banque de donnees : prises sur les faces ouvertes et colonne lumineuse en facade, a la couleur et au niveau des donnees stockees.
 */
public class RenderDataBank implements BlockEntityRenderer<TileEntityDataBank> {

    /** Fenetre de la jauge sur la facade (pixels du modele oriente nord). */
    private static final float X0 = 6, X1 = 10, Y0 = 3, Y1 = 13, Z = -0.02F;

    public RenderDataBank(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityDataBank tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        DataPortRenderer.render(tile, tile.facing(), "data_bank", pose, buffers, light, overlay);
        int level = tile.getClientLevel();
        if (level <= 0) {
            return;
        }
        int rgb = tile.getClientColor();
        float r = (rgb >> 16 & 255) / 255F, g = (rgb >> 8 & 255) / 255F, b = (rgb & 255) / 255F;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(DataPortRenderer.yaw(tile.facing())));
        pose.translate(-0.5, 0, -0.5);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer consumer = buffers.getBuffer(RenderType.lightning());
        float top = Y0 + (Y1 - Y0) * level / 16F;
        float x0 = X0 / 16F, x1 = X1 / 16F, y0 = Y0 / 16F, y1 = top / 16F, z = Z / 16F;
        //Face nord (facade), vue de l'exterieur
        consumer.vertex(matrix, x1, y0, z).color(r, g, b, 0.85F).endVertex();
        consumer.vertex(matrix, x0, y0, z).color(r, g, b, 0.85F).endVertex();
        consumer.vertex(matrix, x0, y1, z).color(r, g, b, 0.85F).endVertex();
        consumer.vertex(matrix, x1, y1, z).color(r, g, b, 0.85F).endVertex();
        pose.popPose();
    }
}
