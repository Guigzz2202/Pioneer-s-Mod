package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.logistics.robotics.hub.TileEntityRoboticBay;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Porte de baie en mouvement : les deux volets coulissent dans le cadre et decouvrent l'interieur eclaire du hub, avec un liseré
 * ambre sur leurs bords. Fermee, la porte est celle du modele du bloc.
 */
public class RenderRoboticBay implements BlockEntityRenderer<TileEntityRoboticBay> {

    private static final ResourceLocation TEXTURE = Pioneers.rl("textures/block/robotics/bay_shutter.png");
    /** Ouverture de la porte sur la face (en 16e de bloc). */
    private static final float D0 = 2 / 16F, D1 = 14 / 16F, MID = 0.5F, EPS = 0.004F;

    public RenderRoboticBay(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityRoboticBay tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        float open = tile.getOpenness(partialTick);
        Direction side = tile.getOutside();
        if (open <= 0 || side == null || side == Direction.DOWN) {
            return;
        }
        //Lissage : debut et fin en douceur
        float t = open * open * (3 - 2 * open);
        VertexConsumer buffer = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = pose.last().pose();
        Matrix3f normal = pose.last().normal();
        Vector3f n = new Vector3f(side.getStepX(), side.getStepY(), side.getStepZ());
        //Interieur (fond sombre eclaire) : la moitie droite de la texture
        quad(buffer, matrix, normal, n, side, D0, D0, D1, D1, 0.5F, 0, 1, 0.5F, LightTexture.FULL_BRIGHT, EPS);
        //Volets : ils rentrent dans le cadre (on montre la partie encore visible)
        float slide = (MID - D0) * t;
        float leftEnd = MID - slide, rightStart = MID + slide;
        if (leftEnd > D0 + 0.001F) {
            float u0 = 0.25F * (slide / (MID - D0));
            quad(buffer, matrix, normal, n, side, D0, D0, leftEnd, D1, u0, 0, 0.25F, 0.5F, light, EPS * 2);
        }
        if (rightStart < D1 - 0.001F) {
            float u1 = 0.5F - 0.25F * (slide / (MID - D0));
            quad(buffer, matrix, normal, n, side, rightStart, D0, D1, D1, 0.25F, 0, u1, 0.5F, light, EPS * 2);
        }
        //Liserés ambres sur les bords des volets (bas de la texture)
        float w = 0.6F / 16;
        quad(buffer, matrix, normal, n, side, leftEnd - w, D0, leftEnd, D1, 0, 0.5F, 0.25F, 1, LightTexture.FULL_BRIGHT, EPS * 3);
        quad(buffer, matrix, normal, n, side, rightStart, D0, rightStart + w, D1, 0, 0.5F, 0.25F, 1, LightTexture.FULL_BRIGHT, EPS * 3);
    }

    /** Rectangle (a0, b0)-(a1, b1) sur la face (a : horizontale, b : verticale), legerement decolle de la face. */
    private static void quad(VertexConsumer buffer, Matrix4f matrix, Matrix3f normal, Vector3f n, Direction side, float a0, float b0, float a1, float b1,
          float u0, float v0, float u1, float v1, int light, float lift) {
        float[][] corners = {{a0, b0, u0, v1}, {a1, b0, u1, v1}, {a1, b1, u1, v0}, {a0, b1, u0, v0}};
        for (float[] c : corners) {
            Vector3f p = point(side, c[0], c[1], lift);
            buffer.vertex(matrix, p.x(), p.y(), p.z()).color(255, 255, 255, 255).uv(c[2], c[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                  .normal(normal, n.x(), n.y(), n.z()).endVertex();
        }
    }

    /** Point (a, b) d'une face du bloc, vu de l'exterieur (a vers la droite, b vers le haut). */
    private static Vector3f point(Direction side, float a, float b, float lift) {
        return switch (side) {
            case NORTH -> new Vector3f(1 - a, b, -lift);
            case SOUTH -> new Vector3f(a, b, 1 + lift);
            case WEST -> new Vector3f(-lift, b, a);
            case EAST -> new Vector3f(1 + lift, b, 1 - a);
            case UP -> new Vector3f(a, 1 + lift, 1 - b);
            case DOWN -> new Vector3f(a, -lift, b);
        };
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityRoboticBay tile) {
        return false;
    }
}
