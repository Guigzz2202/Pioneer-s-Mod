package fr.pioneers.logistics.naval.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.tech.industry.client.big.BigMat;
import fr.pioneers.tech.industry.client.big.BigMesh;
import fr.pioneers.logistics.rail.client.RailMesh;
import fr.pioneers.logistics.naval.HarborEquipment;
import fr.pioneers.logistics.naval.HarborEquipmentTile;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.20.14 : rendu des equipements de quai : la machine ({@link HarborModels}) au-dessus de la dalle, tournee vers l'eau, animee d'apres
 * l'etat recopie par la capitainerie (deploiement interpole, phase de travail).
 * <p>
 * 0.20.46 (audit) : au repos (pas de transfert, deploiement arrive) le maillage ne change plus : il est garde en cache, deja range par
 * materiau, au lieu d'etre refait a chaque image.
 */
public class HarborEquipmentRenderer implements BlockEntityRenderer<HarborEquipmentTile> {

    /** Pose au repos : tout ce qui determine le maillage. */
    private record Key(HarborEquipment kind, int tier, float deploy, float phase, float reach, float drop, boolean lit) {}

    private static final Map<Key, Map<BigMat, List<BigMesh.Quad>>> IDLE = new HashMap<>();

    public HarborEquipmentRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    @Override
    public void render(@NotNull HarborEquipmentTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        float deploy = Mth.lerp(partial, tile.deployO, tile.deploy);
        float phase = tile.working ? Mth.lerp(partial, tile.phaseO, tile.phase) : 0;
        boolean lit = tile.getLevel() != null && tile.getLevel().isNight();
        Map<BigMat, List<BigMesh.Quad>> byMat;
        if (tile.deployO == tile.deploy && (!tile.working || tile.phaseO == tile.phase)) {
            //0.20.46 (audit) : pose immobile : maillage en cache (memes valeurs qu'en le refaisant)
            Key key = new Key(tile.kind(), tile.tier, deploy, phase, tile.reach, tile.drop, lit);
            byMat = IDLE.get(key);
            if (byMat == null) {
                if (IDLE.size() > 256) {
                    IDLE.clear();
                }
                byMat = group(HarborModels.build(tile.kind(), tile.tier, deploy, phase, tile.reach, tile.drop, lit).mesh);
                IDLE.put(key, byMat);
            }
        } else {
            RailMesh mesh = HarborModels.build(tile.kind(), tile.tier, deploy, phase, tile.reach, tile.drop, lit);
            byMat = group(mesh.mesh);
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw(tile.water())));
        pose.translate(-0.5, 0, -0.5);
        draw(pose, buffers, light, byMat);
        pose.popPose();
    }

    /** Angle qui tourne le +z du repere local vers le cote de l'eau. */
    static float yaw(Direction water) {
        return switch (water) {
            case EAST -> 90;
            case NORTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Dessine un maillage en px (une passe par materiau), dans le repere courant. */
    static void draw(PoseStack pose, MultiBufferSource buffers, int light, BigMesh mesh) {
        draw(pose, buffers, light, group(mesh));
    }

    /** Faces rangees par materiau (ordre des materiaux, puis ordre du maillage). */
    static Map<BigMat, List<BigMesh.Quad>> group(BigMesh mesh) {
        Map<BigMat, List<BigMesh.Quad>> byMat = new EnumMap<>(BigMat.class);
        for (BigMesh.Quad q : mesh.quads) {
            byMat.computeIfAbsent(q.mat, k -> new ArrayList<>()).add(q);
        }
        return byMat;
    }

    static void draw(PoseStack pose, MultiBufferSource buffers, int light, Map<BigMat, List<BigMesh.Quad>> byMat) {
        pose.pushPose();
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (Map.Entry<BigMat, List<BigMesh.Quad>> e : byMat.entrySet()) {
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(ShipRenderer.texture(e.getKey())));
            for (BigMesh.Quad q : e.getValue()) {
                int lt = light;
                if (q.glow > 0) {
                    lt = LightTexture.pack(Math.max(LightTexture.block(light), q.glow), LightTexture.sky(light));
                }
                int a = q.color >>> 24 & 255, r = q.color >> 16 & 255, g = q.color >> 8 & 255, b = q.color & 255;
                for (int i = 0; i < 4; i++) {
                    vc.vertex(m, q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]).color(r, g, b, a).uv(q.uv[i * 2], q.uv[i * 2 + 1])
                          .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lt).normal(n, q.nx, q.ny, q.nz).endVertex();
                }
            }
        }
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull HarborEquipmentTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }

}
