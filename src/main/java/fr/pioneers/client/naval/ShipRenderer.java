package fr.pioneers.client.naval;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.client.industry.big.BigMesh;
import fr.pioneers.client.rail.RailMesh;
import fr.pioneers.naval.ShipEntity;
import fr.pioneers.naval.ShipKind;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.20.11 : rendu des navires : maillage {@link ShipModels} en cache par sorte, tier, remplissage (par huitiemes) et feux ; roues a aubes et
 * helices tournees selon le compteur. Houle : leger tangage et roulis, plus marques a l'arret et pour les petits tiers. Une passe par
 * materiau.
 */
public class ShipRenderer extends EntityRenderer<ShipEntity> {

    /** 0.20.46 (audit) : cle entiere (sorte, tier, huitiemes, feux) au lieu d'un texte refait a chaque image. */
    private static final Int2ObjectOpenHashMap<Cached> CACHE = new Int2ObjectOpenHashMap<>();
    private static final Map<BigMat, ResourceLocation> TEXTURES = new EnumMap<>(BigMat.class);

    private record Cached(Map<BigMat, List<List<BigMesh.Quad>>> buckets, List<BigMesh.Group> groups) {}

    public ShipRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0;
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull ShipEntity entity) {
        return texture(BigMat.R_STEEL);
    }

    static ResourceLocation texture(BigMat m) {
        return TEXTURES.computeIfAbsent(m, k -> Pioneers.rl("textures/" + k.sprite() + ".png"));
    }

    @Override
    public void render(@NotNull ShipEntity e, float yaw, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        float h = Mth.lerp(partial, e.yawO, e.yawR), odo = Mth.lerp(partial, e.odoO, e.odoR);
        float time = e.tickCount + partial;
        int tier = e.tier();
        float calm = (e.flags() & 4) != 0 ? 0.35F : 1F;
        float amp = (1.2F - tier * 0.25F) * calm;
        pose.pushPose();
        pose.translate(0, Math.sin(time * 0.07F) * 0.03F * amp, 0);
        pose.mulPose(Axis.YP.rotationDegrees(h));
        pose.mulPose(Axis.ZP.rotationDegrees((float) Math.sin(time * 0.05F + e.getId()) * 1.6F * amp));
        pose.mulPose(Axis.XP.rotationDegrees((float) Math.sin(time * 0.037F + e.getId() * 0.7F) * 0.7F * amp));
        draw(pose, buffers, light, e.kind(), tier, e.fill(), e.flags(), odo);
        pose.popPose();
        super.render(e, yaw, partial, pose, buffers, light);
    }

    /** Dessine un navire (repere du maillage : centre a la ligne de flottaison, avant vers +z), aussi pour l'objet. */
    public static void draw(PoseStack pose, MultiBufferSource buffers, int light, ShipKind kind, int tier, float fill, int flags, float odo) {
        int bucket = Math.round(Math.max(0, Math.min(1, fill)) * 8);
        int key = kind.ordinal() << 16 | (tier & 0xFF) << 8 | bucket << 1 | flags & 1;
        Cached c = CACHE.get(key);
        if (c == null) {
            if (CACHE.size() > 128) {
                CACHE.clear();
            }
            c = cache(RailMesh.deconflict(ShipModels.build(kind, tier, bucket / 8F, flags & 1).mesh));
            CACHE.put(key, c);
        }
        pose.pushPose();
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        Matrix4f[] mats = new Matrix4f[c.groups.size()];
        Matrix3f[] norms = new Matrix3f[c.groups.size()];
        for (int g = 0; g < c.groups.size(); g++) {
            BigMesh.Group gr = c.groups.get(g);
            pose.pushPose();
            if (g > 0 && gr.factor() != 0) {
                float ang = odo * gr.factor();
                pose.translate(gr.px(), gr.py(), gr.pz());
                pose.mulPose(switch (gr.axis()) {
                    case 'x' -> Axis.XP.rotationDegrees(ang);
                    case 'y' -> Axis.YP.rotationDegrees(ang);
                    default -> Axis.ZP.rotationDegrees(ang);
                });
                pose.translate(-gr.px(), -gr.py(), -gr.pz());
            }
            mats[g] = new Matrix4f(pose.last().pose());
            norms[g] = new Matrix3f(pose.last().normal());
            pose.popPose();
        }
        for (BigMat mat : BigMat.values()) {
            List<List<BigMesh.Quad>> byGroup = c.buckets.get(mat);
            if (byGroup == null) {
                continue;
            }
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(texture(mat)));
            for (int g = 0; g < byGroup.size(); g++) {
                for (BigMesh.Quad q : byGroup.get(g)) {
                    emit(vc, mats[g], norms[g], q, light);
                }
            }
        }
        pose.popPose();
    }

    private static Cached cache(BigMesh mesh) {
        Map<BigMat, List<List<BigMesh.Quad>>> buckets = new EnumMap<>(BigMat.class);
        for (BigMesh.Quad q : mesh.quads) {
            List<List<BigMesh.Quad>> byGroup = buckets.computeIfAbsent(q.mat, k -> new ArrayList<>());
            while (byGroup.size() <= q.group) {
                byGroup.add(new ArrayList<>());
            }
            byGroup.get(q.group).add(q);
        }
        return new Cached(buckets, List.copyOf(mesh.groups));
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f n, BigMesh.Quad q, int light) {
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

    public static void reset() {
        CACHE.clear();
    }
}
