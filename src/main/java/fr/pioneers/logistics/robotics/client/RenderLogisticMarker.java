package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.core.RenderClock;
import fr.pioneers.logistics.robotics.logistics.BlockLogisticMarker;
import fr.pioneers.logistics.robotics.logistics.TileEntityLogisticMarker;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Balise logistique : halo de la couleur de son role qui pulse autour de la lanterne.
 */
public class RenderLogisticMarker implements BlockEntityRenderer<TileEntityLogisticMarker> {

    public RenderLogisticMarker(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityLogisticMarker tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        float time = (float) RenderClock.time(partialTick) + (tile.getBlockPos().asLong() & 63);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float pulse = 0.5F + 0.5F * Mth.sin(time * 0.12F);
        int color = tile.getRole().color;
        float r = (color >> 16 & 255) / 255F, g = (color >> 8 & 255) / 255F, b = (color & 255) / 255F;
        Direction facing = tile.getBlockState().getValue(BlockLogisticMarker.FACING);
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(facing.getRotation());
        //Repere : la balise "monte" vers +y depuis -0,5
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        float s = 0.16F + 0.03F * pulse, y0 = -0.5F + 0.11F, y1 = -0.5F + 0.36F + 0.03F * pulse;
        float a = 0.18F + 0.22F * pulse;
        box(buffer, matrix, -s, y0, -s, s, y1, s, r, g, b, a);
        pose.popPose();
    }

    private static void box(VertexConsumer buffer, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b,
          float a) {
        //Quatre cotes et le dessus, en degrade vers le haut
        float[][] sides = {{x0, z0, x1, z0}, {x1, z0, x1, z1}, {x1, z1, x0, z1}, {x0, z1, x0, z0}};
        for (float[] side : sides) {
            buffer.vertex(m, side[0], y0, side[1]).color(r, g, b, a).endVertex();
            buffer.vertex(m, side[2], y0, side[3]).color(r, g, b, a).endVertex();
            buffer.vertex(m, side[2], y1, side[3]).color(r, g, b, 0).endVertex();
            buffer.vertex(m, side[0], y1, side[1]).color(r, g, b, 0).endVertex();
            buffer.vertex(m, side[0], y1, side[1]).color(r, g, b, 0).endVertex();
            buffer.vertex(m, side[2], y1, side[3]).color(r, g, b, 0).endVertex();
            buffer.vertex(m, side[2], y0, side[3]).color(r, g, b, a).endVertex();
            buffer.vertex(m, side[0], y0, side[1]).color(r, g, b, a).endVertex();
        }
    }
}
