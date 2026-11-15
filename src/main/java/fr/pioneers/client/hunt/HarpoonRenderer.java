package fr.pioneers.client.hunt;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.hunt.HarpoonEntity;
import fr.pioneers.hunt.HarpoonItem;
import fr.pioneers.hunt.HuntRules;
import fr.pioneers.hunt.PiHunt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * 1.0.5-beta (lot 10) : rendu du HARPON lance (modele de l'objet, pointe dans le sens du vol, comme le trident) et de sa CORDE jusqu'a la main
 * du lanceur (meme calcul de la main que la ligne d'une canne a peche, premiere et troisieme personne) : un ruban tordu de chanvre qui
 * pend quand la corde est lache ({@link #sag}) et se tend au-dela des 3/4 de sa longueur.
 */
public class HarpoonRenderer extends EntityRenderer<HarpoonEntity> {

    /** Couleurs du chanvre (clair, sombre : torsade). */
    public static final int ROPE_LIGHT = 0xB89A68, ROPE_DARK = 0x7A6240;
    public static final int SEGMENTS = 24;
    private final ItemRenderer items;
    private final ItemStack look;

    public HarpoonRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.items = ctx.getItemRenderer();
        this.look = new ItemStack(PiHunt.HARPOON.get());
    }

    /** Fleche de la corde (blocs) pour une portee {@code d} : lache = pend, tendue au-dela des 3/4 de la corde = droite. */
    public static double sag(double d) {
        double slack = Math.max(0, HuntRules.HARPOON_ROPE * 0.75 - d) / (HuntRules.HARPOON_ROPE * 0.75);
        return Math.min(2.5, 0.08 * d + 1.6 * slack);
    }

    @Override
    public void render(@NotNull HarpoonEntity e, float yaw, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        pose.pushPose();
        pose.mulPose(Axis.YP.rotationDegrees(Mth.lerp(pt, e.yRotO, e.getYRot()) - 90.0F));
        pose.mulPose(Axis.ZP.rotationDegrees(Mth.lerp(pt, e.xRotO, e.getXRot()) - 90.0F));
        items.renderStatic(look, ItemDisplayContext.NONE, light, net.minecraft.client.renderer.texture.OverlayTexture.NO_OVERLAY, pose, buffers,
              e.level(), e.getId());
        pose.popPose();
        if (e.getOwner() instanceof Player p) {
            Vec3 hand = hand(p, pt);
            Vec3 here = e.getPosition(pt);
            rope(pose, buffers.getBuffer(RenderType.leash()), hand.subtract(here), light, LightTexture.pack(p.level().getBrightness(
                  net.minecraft.world.level.LightLayer.BLOCK, p.blockPosition()), p.level().getBrightness(net.minecraft.world.level.LightLayer.SKY,
                  p.blockPosition())));
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Main du lanceur (methode de la ligne de peche vanilla). */
    Vec3 hand(Player p, float pt) {
        int side = p.getMainArm() == HumanoidArm.RIGHT ? 1 : -1;
        if (!(p.getMainHandItem().getItem() instanceof HarpoonItem)) {
            side = -side;
        }
        float swing = Mth.sin(Mth.sqrt(p.getAttackAnim(pt)) * (float) Math.PI);
        float body = Mth.lerp(pt, p.yBodyRotO, p.yBodyRot) * ((float) Math.PI / 180F);
        double s = Mth.sin(body), c = Mth.cos(body), off = side * 0.35;
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.getCameraType().isFirstPerson() && p == mc.player) {
            double k = 960.0 / mc.options.fov().get();
            Vec3 v = entityRenderDispatcher.camera.getNearPlane().getPointOnPlane(side * 0.525F, -0.1F).scale(k).yRot(swing * 0.5F).xRot(-swing * 0.7F);
            return new Vec3(Mth.lerp(pt, p.xo, p.getX()) + v.x, Mth.lerp(pt, p.yo, p.getY()) + v.y + p.getEyeHeight(), Mth.lerp(pt, p.zo, p.getZ()) + v.z);
        }
        return new Vec3(Mth.lerp(pt, p.xo, p.getX()) - c * off - s * 0.8, p.yo + p.getEyeHeight() + (p.getY() - p.yo) * pt - 0.45
              + (p.isCrouching() ? -0.1875 : 0), Mth.lerp(pt, p.zo, p.getZ()) - s * off + c * 0.8);
    }

    /** Corde de (0,0,0) a {@code to} : ruban a deux faces croisees (torsade claire / sombre), fleche {@link #sag}. */
    public static void rope(PoseStack pose, VertexConsumer buf, Vec3 to, int lightA, int lightB) {
        Matrix4f m = pose.last().pose();
        double len = to.length(), sagd = sag(len);
        float w = 0.035F;
        for (int pass = 0; pass < 2; pass++) {
            float wx = pass == 0 ? w : 0, wy = pass == 0 ? 0 : w;
            for (int k = 0; k <= SEGMENTS; k++) {
                int i = pass == 0 ? k : SEGMENTS - k;       //second ruban parcouru a rebours : la bande reste continue
                float t = i / (float) SEGMENTS;
                double x = to.x * t, y = to.y * t - sagd * 4 * t * (1 - t), z = to.z * t;
                int col = (i % 2 == 0) ^ (pass == 1) ? ROPE_LIGHT : ROPE_DARK;
                int r = col >> 16 & 255, g = col >> 8 & 255, b = col & 255;
                int light = LightTexture.pack((int) Mth.lerp(t, LightTexture.block(lightA), LightTexture.block(lightB)),
                      (int) Mth.lerp(t, LightTexture.sky(lightA), LightTexture.sky(lightB)));
                buf.vertex(m, (float) x - wx, (float) y + wy, (float) z).color(r, g, b, 255).uv2(light).endVertex();
                buf.vertex(m, (float) x + wx, (float) y - wy, (float) z).color(r, g, b, 255).uv2(light).endVertex();
            }
        }
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public ResourceLocation getTextureLocation(@NotNull HarpoonEntity e) {
        return TextureAtlas.LOCATION_BLOCKS;
    }

    @Override
    public boolean shouldRender(@NotNull HarpoonEntity e, @NotNull net.minecraft.client.renderer.culling.Frustum frustum, double x, double y, double z) {
        return true;
    }
}
