package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.space.content.absorber.AbsorberControllerTile;
import fr.pioneers.core.RenderClock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Rendu de l'absorbeur d'energie solaire en marche (dessine par le controleur) : des faisceaux de lumiere arrivent de l'etoile (la ou
 * elle se tient dans le ciel de la station) et convergent sur les capteurs, qui rayonnent d'une lueur chaude et pulsante.
 */
public class RenderSolarAbsorber implements BlockEntityRenderer<AbsorberControllerTile> {

    /** Direction de l'etoile dans le ciel d'une station stellaire (azimut 200, hauteur 12 degres). */
    private static final float DIR_X, DIR_Y, DIR_Z;

    static {
        double az = Math.toRadians(200), el = Math.toRadians(12);
        DIR_X = (float) (Math.cos(el) * Math.sin(az));
        DIR_Y = (float) Math.sin(el);
        DIR_Z = (float) (-Math.cos(el) * Math.cos(az));
    }

    public RenderSolarAbsorber(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull AbsorberControllerTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        Level level = tile.getLevel();
        BlockPos min = tile.getMin(), max = tile.getMax();
        if (level == null || min == null || max == null || !tile.isFormed() || !tile.isLit()) {
            return;
        }
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float pulse = 0.75F + 0.25F * Mth.sin(time * 0.08F);
        pose.pushPose();
        pose.translate(min.getX() - tile.getBlockPos().getX(), min.getY() - tile.getBlockPos().getY(), min.getZ() - tile.getBlockPos().getZ());
        Matrix4f matrix = pose.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        float w = max.getX() - min.getX() + 1, d = max.getZ() - min.getZ() + 1, top = max.getY() - min.getY() + 1.02F;
        //Lueur sur les capteurs
        float a = 0.22F * pulse;
        buffer.vertex(matrix, 1, top, 1).color(1F, 0.75F, 0.3F, a).endVertex();
        buffer.vertex(matrix, 1, top, d - 1).color(1F, 0.75F, 0.3F, a).endVertex();
        buffer.vertex(matrix, w - 1, top, d - 1).color(1F, 0.75F, 0.3F, a).endVertex();
        buffer.vertex(matrix, w - 1, top, 1).color(1F, 0.75F, 0.3F, a).endVertex();
        //Faisceaux de l'etoile
        int beams = Math.max(2, Math.round(w / 3));
        for (int i = 0; i < beams; i++) {
            for (int j = 0; j < beams; j++) {
                float px = 1.5F + (w - 3) * (i + 0.5F) / beams, pz = 1.5F + (d - 3) * (j + 0.5F) / beams;
                float shimmer = 0.6F + 0.4F * Mth.sin(time * 0.13F + i * 1.7F + j * 2.3F);
                beam(buffer, matrix, px, top, pz, 40, 0.18F, 0.75F * shimmer);
            }
        }
        pose.popPose();
    }

    /** Faisceau : deux plans croises, du point d'impact vers l'etoile, qui s'evanouit au loin. */
    private static void beam(VertexConsumer buffer, Matrix4f matrix, float x, float y, float z, float length, float width, float alpha) {
        float ex = x + DIR_X * length, ey = y + DIR_Y * length, ez = z + DIR_Z * length;
        float[] u = {-DIR_Z, 0, DIR_X};
        float[] v = {0, 1, 0};
        for (float[] axis : new float[][]{u, v}) {
            for (int pass = 0; pass < 2; pass++) {
                float k = pass == 0 ? 1 : -1;
                float ax = axis[0] * width * k, ay = axis[1] * width * k, az = axis[2] * width * k;
                buffer.vertex(matrix, x - ax, y - ay, z - az).color(1F, 0.9F, 0.6F, alpha).endVertex();
                buffer.vertex(matrix, x + ax, y + ay, z + az).color(1F, 0.9F, 0.6F, alpha).endVertex();
                buffer.vertex(matrix, ex + ax * 4, ey + ay * 4, ez + az * 4).color(1F, 0.7F, 0.3F, 0).endVertex();
                buffer.vertex(matrix, ex - ax * 4, ey - ay * 4, ez - az * 4).color(1F, 0.7F, 0.3F, 0).endVertex();
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull AbsorberControllerTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
