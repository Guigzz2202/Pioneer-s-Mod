package fr.pioneers.world.ruins.client.sentinel;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.world.ruins.sentinel.SentinelEntity;
import fr.pioneers.world.ruins.sentinel.SentinelRole;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.MobRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 1.9 (MAJ RP, bloc 1) : rendu d'une Sentinelle. Texture du tier ({@code entity/sentinel/<role>_mk<n>.png}), voyants sur une couche
 * lumineuse a part ({@code _glow}, {@link RenderType#eyes}) : eteints au repos, orange en alerte et combat (sequence d'allumage au
 * reveil, gresillement quand elle est etourdie), bleu pale en Veille ; surchauffe du Contremaitre ; bulle de bouclier du Reparateur
 * ({@link RenderType#energySwirl}) ; rayon du laser de la Tourelle MKIII pendant sa charge. Pas de culbute a la mort (effondrement dans
 * la maquette).
 */
public class SentinelRenderer extends MobRenderer<SentinelEntity, SentinelModel> {

    private static final ResourceLocation SHIELD = Pioneers.rl("textures/entity/sentinel/shield.png");
    private static final ResourceLocation HEAT = Pioneers.rl("textures/entity/sentinel/foreman_heat.png");
    private final SentinelRole role;
    private final ResourceLocation[] textures = new ResourceLocation[3];
    private final ResourceLocation[] glows = new ResourceLocation[3];

    public SentinelRenderer(EntityRendererProvider.Context context, SentinelRole role) {
        super(context, new SentinelModel(context.bakeLayer(SentinelClient.layer(role)), role), shadow(role));
        this.role = role;
        for (int t = 1; t <= 3; t++) {
            textures[t - 1] = Pioneers.rl("textures/entity/sentinel/" + role.id() + "_mk" + t + ".png");
            glows[t - 1] = Pioneers.rl("textures/entity/sentinel/" + role.id() + "_mk" + t + "_glow.png");
        }
        addLayer(new GlowLayer(this));
        addLayer(new ShieldLayer(this));
    }

    private static float shadow(SentinelRole role) {
        return switch (role) {
            case GUARDIAN -> 0.7F;
            case PATROLLER -> 0.3F;
            case TURRET -> 0.6F;
            case REPAIRER -> 0.45F;
            case FOREMAN -> 1.1F;
        };
    }

    private static int index(SentinelEntity e) {
        return Mth.clamp(e.tier(), 1, 3) - 1;
    }

    @Override
    public @NotNull ResourceLocation getTextureLocation(@NotNull SentinelEntity e) {
        return textures[index(e)];
    }

    @Override
    protected float getFlipDegrees(@NotNull SentinelEntity e) {
        return 0;
    }

    @Override
    public void render(@NotNull SentinelEntity e, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        super.render(e, yaw, partialTick, pose, buffers, light);
        if (role == SentinelRole.TURRET && e.flag(SentinelEntity.FLAG_WORK) && e.isAlive()) {
            Entity target = e.level().getEntity(e.aux());
            if (target != null) {
                drawLaser(e, target, partialTick, pose, buffers);
            }
        }
    }

    /** Puissance des voyants (0 = eteints) : allumage clignotant au reveil, gresillement quand elle est etourdie. */
    static float power(SentinelEntity e) {
        if (e.flag(SentinelEntity.FLAG_STUN)) {
            return ((e.tickCount * 7 + e.getId()) % 5) < 2 ? 0.15F : 0.85F;
        }
        if (e.deathTime > 0) {
            return Math.max(0, 1 - e.deathTime / 12F) * (e.tickCount % 3 == 0 ? 0.3F : 1F);
        }
        int light = e.light();
        if (light == SentinelEntity.LIGHT_OFF) {
            return 0;
        }
        if (light == SentinelEntity.LIGHT_ALERT) {
            int since = e.tickCount - e.clientWake;
            if (since >= 0 && since < 14) {
                return since % 4 < 2 ? 0.1F : 1F;
            }
        }
        return 1;
    }

    /** Rayon du laser : deux rubans croises du canon a la cible, plus epais et plus vif a mesure que la charge monte. */
    private void drawLaser(SentinelEntity e, Entity target, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Vec3 origin = e.getPosition(partialTick);
        float headYaw = Mth.rotLerp(partialTick, e.yHeadRotO, e.yHeadRot);
        float headPitch = Mth.lerp(partialTick, e.xRotO, e.getXRot());
        Vec3 dir = Vec3.directionFromRotation(headPitch, headYaw);
        Vec3 from = new Vec3(0, 15 / 16.0, 0).add(dir.scale(0.75));
        Vec3 to = target.getPosition(partialTick).add(0, target.getBbHeight() * 0.6, 0).subtract(origin);
        float k = Math.min(1, e.workTicks / 30F);
        float time = e.tickCount + partialTick;
        float pulse = 0.75F + 0.25F * Mth.sin(time * 1.3F);
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        Vec3 d = to.subtract(from);
        Vec3 side1 = d.cross(new Vec3(0, 1, 0));
        if (side1.lengthSqr() < 1e-4) {
            side1 = new Vec3(1, 0, 0);
        }
        double w = 0.012 + 0.05 * k;
        side1 = side1.normalize().scale(w);
        Vec3 side2 = d.cross(side1).normalize().scale(w);
        for (Vec3 side : new Vec3[]{side1, side2}) {
            quad(buffer, matrix, from, to, side.scale(2.5), 1.0F, 0.3F, 0.08F, (0.12F + 0.25F * k) * pulse);
            quad(buffer, matrix, from, to, side, 1.0F, 0.85F, 0.6F, (0.3F + 0.6F * k) * pulse);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 side, float r, float g, float bl, float alpha) {
        vertex(buffer, matrix, a.add(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.add(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, a.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, a.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.subtract(side), r, g, bl, alpha);
        vertex(buffer, matrix, b.add(side), r, g, bl, alpha);
        vertex(buffer, matrix, a.add(side), r, g, bl, alpha);
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, Vec3 p, float r, float g, float b, float a) {
        buffer.vertex(matrix, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).endVertex();
    }

    /** Voyants : couche lumineuse blanche teintee (orange, bleu pale), surchauffe du Contremaitre en rouge pulse. */
    private class GlowLayer extends RenderLayer<SentinelEntity, SentinelModel> {

        GlowLayer(RenderLayerParent<SentinelEntity, SentinelModel> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull SentinelEntity e, float limbSwing,
              float limbAmount, float partialTick, float age, float yaw, float pitch) {
            float p = power(e);
            if (p > 0) {
                boolean blue = e.light() == SentinelEntity.LIGHT_STANDBY && !e.flag(SentinelEntity.FLAG_STUN);
                float r = blue ? 0.55F : 1.0F, g = blue ? 0.78F : 0.55F, b = blue ? 1.0F : 0.16F;
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(glows[index(e)])), LightTexture.FULL_BRIGHT,
                      OverlayTexture.NO_OVERLAY, r * p, g * p, b * p, 1);
            }
            if (e.flag(SentinelEntity.FLAG_HEAT)) {
                float h = 0.6F + 0.4F * Mth.sin(age * 0.35F);
                getParentModel().renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(HEAT)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY,
                      h, 0.28F * h, 0.08F * h, 1);
            }
        }
    }

    /** Bulle de bouclier : le modele un peu gonfle, en hexagones qui defilent. */
    private static class ShieldLayer extends RenderLayer<SentinelEntity, SentinelModel> {

        ShieldLayer(RenderLayerParent<SentinelEntity, SentinelModel> parent) {
            super(parent);
        }

        @Override
        public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull SentinelEntity e, float limbSwing,
              float limbAmount, float partialTick, float age, float yaw, float pitch) {
            if (!e.flag(SentinelEntity.FLAG_SHIELD)) {
                return;
            }
            float f = age * 0.01F;
            VertexConsumer vc = buffers.getBuffer(RenderType.energySwirl(SHIELD, f % 1.0F, (f * 0.7F) % 1.0F));
            pose.pushPose();
            pose.scale(1.12F, 1.06F, 1.12F);
            getParentModel().renderToBuffer(pose, vc, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.35F, 0.65F, 1.0F, 1.0F);
            pose.popPose();
        }
    }
}
