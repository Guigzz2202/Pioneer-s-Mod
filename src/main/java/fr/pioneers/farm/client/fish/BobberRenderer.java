package fr.pioneers.farm.client.fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.fish.PiBobber;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Bouchon et fil (d'apres le rendu vanilla) : le fil pend quand il est mou et se tend en ligne droite avec la tension, et rougit quand elle
 * approche de la rupture ; le bouchon plonge a la touche et saute avec le poisson. 1.6 : le fil part du bout de la canne qui suit le geste
 * du lancer ({@link RodClient#tipOffset}), se deroule a demi tendu pendant le vol puis pend, l'affaissement passant de l'un a l'autre en
 * douceur ; 24 segments. 1.0.2-beta : l'attache est le bout de la canne RELEVE au rendu ({@link RodClient#firstPersonTip},
 * {@link RodTipLayer}) : le fil ne quitte plus la canne pendant le lancer.
 */
public class BobberRenderer extends EntityRenderer<PiBobber> {

    private static final ResourceLocation TEXTURE = Pioneers.rl("textures/entity/fish/bobber.png");
    private static final RenderType TYPE = RenderType.entityCutout(TEXTURE);
    private static final int SEGMENTS = 24;
    /** Affaissement du fil pendant le vol (1 = pendant, 0 = droit). */
    private static final float FLIGHT_SAG = 0.45F;

    public BobberRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
    }

    @Override
    public void render(PiBobber bobber, float yaw, float partial, PoseStack pose, MultiBufferSource buffers, int light) {
        Player player = bobber.player();
        if (player == null) {
            return;
        }
        pose.pushPose();
        pose.pushPose();
        pose.scale(0.5F, 0.5F, 0.5F);
        pose.mulPose(entityRenderDispatcher.cameraOrientation());
        pose.mulPose(Axis.YP.rotationDegrees(180.0F));
        PoseStack.Pose p = pose.last();
        Matrix4f m = p.pose();
        Matrix3f n = p.normal();
        VertexConsumer vc = buffers.getBuffer(TYPE);
        vertex(vc, m, n, light, 0, 0, 0, 1);
        vertex(vc, m, n, light, 1, 0, 1, 1);
        vertex(vc, m, n, light, 1, 1, 1, 0);
        vertex(vc, m, n, light, 0, 1, 0, 0);
        pose.popPose();
        //1.6 : le fil part du bout de la canne, qui suit le geste du lancer (RodClient) ; affaissement lisse d'un etat a l'autre
        int side = PiBobber.rodSide(player);
        float swing = Mth.sin(Mth.sqrt(player.getAttackAnim(partial)) * (float) Math.PI);
        float body = Mth.lerp(partial, player.yBodyRotO, player.yBodyRot) * ((float) Math.PI / 180F);
        double sin = Mth.sin(body), cos = Mth.cos(body), off = side * 0.35;
        double hx, hy, hz;
        float eye;
        boolean firstPerson = (entityRenderDispatcher.options == null || entityRenderDispatcher.options.getCameraType().isFirstPerson())
                              && player == Minecraft.getInstance().player;
        //1.0.2-beta : bout de la canne RELEVE la ou elle est dessinee (RodClient : main en 1re personne, RodTipLayer en 3e) ; l'estimation
        //d'avant ne sert plus que de repli (canne pas encore dessinee)
        float[] seen = firstPerson ? RodClient.firstPersonTip() : null;
        Vec3 seenWorld = firstPerson ? null : RodClient.thirdPersonTip(player);
        if (firstPerson && seen != null) {
            double fov = 960.0 / entityRenderDispatcher.options.fov().get();
            Vec3 v = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(seen[0], seen[1]).scale(fov);
            hx = Mth.lerp(partial, player.xo, player.getX()) + v.x;
            hy = Mth.lerp(partial, player.yo, player.getY()) + v.y;
            hz = Mth.lerp(partial, player.zo, player.getZ()) + v.z;
            eye = player.getEyeHeight();
        } else if (!firstPerson && seenWorld != null) {
            hx = seenWorld.x;
            hy = seenWorld.y;
            hz = seenWorld.z;
            eye = 0;
        } else if (firstPerson) {
            float[] tip = RodClient.tipOffset(player, partial, true);
            double fov = 960.0 / entityRenderDispatcher.options.fov().get();
            Vec3 v = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525F + tip[0], -0.1F + tip[1]).scale(fov).yRot(swing * 0.5F)
                  .xRot(-swing * 0.7F);
            hx = Mth.lerp(partial, player.xo, player.getX()) + v.x;
            hy = Mth.lerp(partial, player.yo, player.getY()) + v.y;
            hz = Mth.lerp(partial, player.zo, player.getZ()) + v.z;
            eye = player.getEyeHeight();
        } else {
            float[] tip = RodClient.tipOffset(player, partial, false);
            double ahead = 0.8 + tip[0];
            hx = Mth.lerp(partial, player.xo, player.getX()) - cos * off - sin * ahead;
            hy = player.yo + player.getEyeHeight() + (player.getY() - player.yo) * partial - 0.45 + tip[1];
            hz = Mth.lerp(partial, player.zo, player.getZ()) - sin * off + cos * ahead;
            eye = player.isCrouching() ? -0.1875F : 0;
        }
        double bx = Mth.lerp(partial, bobber.xo, bobber.getX()), by = Mth.lerp(partial, bobber.yo, bobber.getY()) + 0.25,
              bz = Mth.lerp(partial, bobber.zo, bobber.getZ());
        float dx = (float) (hx - bx), dy = (float) (hy - by) + eye, dz = (float) (hz - bz);
        int state = bobber.state();
        float tension = state == PiBobber.FIGHT ? bobber.tension() : 0;
        //En vol le fil se deroule a demi tendu ; a l'eau il pend ; au combat il se tend avec la tension
        float sagWanted = state == PiBobber.FLYING ? FLIGHT_SAG : 1 - tension;
        bobber.shownSag = bobber.shownSag < 0 ? sagWanted : bobber.shownSag + (sagWanted - bobber.shownSag) * 0.12F;
        float sag = Mth.clamp(bobber.shownSag, 0, 1);
        VertexConsumer line = buffers.getBuffer(RenderType.lineStrip());
        PoseStack.Pose lp = pose.last();
        int r = (int) (40 + 200 * Mth.clamp((tension - 0.6F) / 0.4F, 0, 1)), g = 40, b2 = 40;
        for (int k = 0; k <= SEGMENTS; k++) {
            string(dx, dy, dz, line, lp, k / (float) SEGMENTS, (k + 1) / (float) SEGMENTS, sag, r, g, b2);
        }
        pose.popPose();
        super.render(bobber, yaw, partial, pose, buffers, light);
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, float x, int y, int u, int v) {
        vc.vertex(m, x - 0.5F, y - 0.5F, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0)
              .endVertex();
    }

    /** Segment du fil : chainette (fil mou, {@code sag} = 1) melangee a la droite (fil tendu, 0). */
    private static void string(float x, float y, float z, VertexConsumer vc, PoseStack.Pose p, float t, float t2, float sag, int r, int g, int b) {
        float x0 = x * t, y0 = y * ((t * t + t) * 0.5F * sag + t * (1 - sag)) + 0.25F * sag, z0 = z * t;
        float x1 = x * t2 - x0, y1 = y * ((t2 * t2 + t2) * 0.5F * sag + t2 * (1 - sag)) + 0.25F * sag - y0, z1 = z * t2 - z0;
        float len = Mth.sqrt(x1 * x1 + y1 * y1 + z1 * z1);
        if (len < 1e-5F) {
            len = 1;
        }
        vc.vertex(p.pose(), x0, y0, z0).color(r, g, b, 255).normal(p.normal(), x1 / len, y1 / len, z1 / len).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(PiBobber bobber) {
        return TEXTURE;
    }
}
