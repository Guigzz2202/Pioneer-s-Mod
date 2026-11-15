package fr.pioneers.client.wild;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.wild.BananaPeel;
import fr.pioneers.wild.GeneratedWild8;
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
 * 1.0.5-beta (lot 8) : rendu de la PEAU DE BANANE jetee par le singe : une etoile de trois lanieres jaunes (texture
 * {@code textures/entity/wild/banana_peel.png}, peinte par T:wild/wild8_art.py), qui tournoie en vol, se pose a plat au sol et pâlit avant
 * de disparaitre. Double face, sans ombre.
 */
public class BananaPeelRenderer extends EntityRenderer<BananaPeel> {

    static final ResourceLocation TEX = Pioneers.rl("textures/entity/wild/banana_peel.png");
    static final float HALF = 0.22F;

    public BananaPeelRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        shadowRadius = 0F;
    }

    @Override
    public void render(@NotNull BananaPeel e, float yaw, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        float age = e.tickCount + pt;
        pose.pushPose();
        pose.translate(0, 0.02, 0);
        pose.mulPose(Axis.YP.rotationDegrees(e.getYRot()));
        if (!e.onGround()) {
            pose.mulPose(Axis.XP.rotationDegrees(age * 38F));        //elle tournoie en vol
            pose.mulPose(Axis.ZP.rotationDegrees(age * 23F));
        }
        float fade = Mth.clamp((GeneratedWild8.PEEL_LIFE - age) / 15F, 0F, 1F);
        int alpha = (int) (255 * fade);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(TEX));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        quad(vc, m, n, light, alpha, 1F);
        quad(vc, m, n, light, alpha, -1F);
        pose.popPose();
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Un carre horizontal (face du dessus ou du dessous selon {@code up}). */
    static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, int light, int alpha, float up) {
        float[][] p = {{-HALF, -HALF, 0, 0}, {-HALF, HALF, 0, 1}, {HALF, HALF, 1, 1}, {HALF, -HALF, 1, 0}};
        for (int i = 0; i < 4; i++) {
            float[] v = p[up > 0 ? i : 3 - i];
            vc.vertex(m, v[0], 0, v[1]).color(255, 255, 255, alpha).uv(v[2], v[3]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
              .normal(n, 0, up, 0).endVertex();
        }
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull BananaPeel e) {
        return TEX;
    }
}
