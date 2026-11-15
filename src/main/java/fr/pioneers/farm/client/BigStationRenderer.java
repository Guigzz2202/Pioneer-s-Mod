package fr.pioneers.farm.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.farm.kitchen.StationTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.6 (lot A2) : ce qui bouge sur les grands postes (appele par {@link StationRenderer}, pose deja tournee selon {@code facing} autour de la
 * case principale ; pixels du modele, le poste s'etend vers -x, +y et +z). Reperes recopies de {@code tools/gc/farm2/art_big_stations.py}
 * (GEOM) et mimes par son apercu (art_big_stations_preview.py) :
 * <ul>
 *     <li>cuve 2 x 2 : liquide (r 13,3, centre 0 / 16, y 2 a 14), mousse qui derive et bulles qui crevent pendant la fermentation, fourquet
 *     appuye au bord qui se balance ;</li>
 *     <li>chaudron : mout (r 6,2, y 1,4 a 12,4) qui bout, fourquet qui tourne en remuant ;</li>
 *     <li>alambic : eau de la cuve du serpentin (r 5,3 en -8 / 9, y 13,6), spires de cuivre sous l'eau, flacon qui se remplit de l'eau-de-vie
 *     (couleur du fluide de sortie), goutte qui tombe du bec ;</li>
 *     <li>fut : marques de craie sur la tete (une par dixieme du vieillissement, par paquets de cinq) ;</li>
 *     <li>fumoir : pieces pendues aux quatre crochets (x 3,5 / 6,5 / 9,5 / 12,5 ; y 24,8 ; z 7 et 9 en alternance) qui se balancent dans
 *     la fumee.</li>
 * </ul>
 */
public final class BigStationRenderer {

    private static final float COS22 = (float) Math.cos(Math.PI / 8);
    private static final float[] HOOKS = {3.5F, 6.5F, 9.5F, 12.5F};

    private BigStationRenderer() {
    }

    static void render(StationTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Level level, float time) {
        switch (tile.kind()) {
            case FERMENTING_VAT -> vat(tile, pose, buffers, light, overlay, time);
            case BREW_KETTLE -> kettle(tile, pose, buffers, light, overlay, time);
            case COPPER_STILL -> still(tile, pose, buffers, light, overlay, time);
            case AGING_BARREL -> barrel(tile, pose, buffers, light, overlay);
            case SMOKEHOUSE -> smokehouse(tile, pose, buffers, light, overlay, level, time);
            default -> {
            }
        }
    }

    // ------------------------------------------------------------------ outils

    private static KDraw.Tex tex(String name) {
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Pioneers.rl("block/farm/kitchen/" + name)));
    }

    private static KDraw.Tex fluidTex(FluidStack fluid) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid)));
    }

    private static int tint(FluidStack fluid) {
        return 0xFF000000 | IClientFluidTypeExtensions.of(fluid.getFluid()).getTintColor(fluid);
    }

    /** Fluide montre : la sortie si elle n'est pas vide, sinon l'entree. */
    @Nullable
    private static FluidStack shown(StationTile tile) {
        FluidTank out = tile.outTank(), in = tile.tank();
        if (out != null && !out.isEmpty()) {
            return out.getFluid();
        }
        return in != null && !in.isEmpty() ? in.getFluid() : null;
    }

    private static float fill(@Nullable FluidTank t) {
        return t == null || t.isEmpty() ? 0 : Math.min(1F, t.getFluidAmount() / (float) t.getCapacity());
    }

    private static float fill(StationTile tile) {
        FluidTank out = tile.outTank();
        return fill(out != null && !out.isEmpty() ? out : tile.tank());
    }

    /** Surface octogonale d'un liquide (rayon inscrit r, pixels), texture etalee sur l'octogone (centre quelconque, meme hors de la case). */
    private static void surface(PoseStack pose, MultiBufferSource buffers, int light, FluidStack fluid, float cx, float cz, float r, float y, float alpha) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int t = ext.getTintColor(fluid);
        float red = (t >> 16 & 255) / 255F, green = (t >> 8 & 255) / 255F, blue = (t & 255) / 255F;
        float a = Math.min(1F, ((t >>> 24) == 0 ? 1F : (t >>> 24) / 255F) * alpha + 0.1F);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float rr = r / COS22;
        for (int k = 0; k < 8; k++) {
            double a0 = Math.toRadians(22.5 + 45 * k), a1 = Math.toRadians(22.5 + 45 * (k + 1));
            float x0 = rr * (float) Math.cos(a0), z0 = rr * (float) Math.sin(a0);
            float x1 = rr * (float) Math.cos(a1), z1 = rr * (float) Math.sin(a1);
            vertex(vc, m, n, cx, cz, 0, 0, y, sp, rr, red, green, blue, a, light);
            vertex(vc, m, n, cx, cz, x1, z1, y, sp, rr, red, green, blue, a, light);
            vertex(vc, m, n, cx, cz, x0, z0, y, sp, rr, red, green, blue, a, light);
            vertex(vc, m, n, cx, cz, x0, z0, y, sp, rr, red, green, blue, a, light);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float cx, float cz, float dx, float dz, float y, TextureAtlasSprite sp, float rr,
          float r, float g, float b, float a, int light) {
        vc.vertex(m, (cx + dx) / 16F, y / 16F, (cz + dz) / 16F).color(r, g, b, a).uv(sp.getU(8 + dx / rr * 7.5F), sp.getV(8 + dz / rr * 7.5F))
              .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    /** Bulles qui gonflent puis crevent a la surface (n bulles, rayon de la zone {@code spread}). */
    private static void bubbles(KDraw d, KDraw.Tex t, float cx, float cz, float y, float spread, int n, float time, float speed) {
        for (int k = 0; k < n; k++) {
            float phase = time * speed + k * 0.37F;
            int cycle = Mth.floor(phase);
            float ph = phase - cycle;
            float ang = (float) Math.toRadians((cycle * 97 + k * 61) % 360);
            float dist = spread * (((cycle * 13 + k * 7) % 10) / 10F);
            float x = cx + dist * Mth.cos(ang), z = cz + dist * Mth.sin(ang);
            float s = 0.2F + ph * 0.6F;
            d.box(x - s, y - 0.1F, z - s, x + s, y + s * 1.1F, z + s, t, t);
        }
    }

    private static void item(PoseStack pose, MultiBufferSource buffers, int light, Level level, ItemStack stack, float x, float y, float z, float scale,
          float yaw, int seed) {
        pose.pushPose();
        pose.translate(x / 16F, y / 16F, z / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers, level,
              seed);
        pose.popPose();
    }

    // ------------------------------------------------------------------ postes

    /** Cuve de fermentation 2 x 2. */
    private static void vat(StationTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        boolean work = tile.working();
        FluidStack fluid = shown(tile);
        if (fluid != null) {
            float y = 2.0F + 12.0F * fill(tile) + (work ? Mth.sin(time * 0.13F) * 0.05F : 0);
            if (work) {
                KDraw d = new KDraw(pose, buffers, light, overlay);
                KDraw.Tex foam = tex("foam");
                for (int k = 0; k < 9; k++) {
                    float a = (float) Math.toRadians(time * 0.35F + k * 51);
                    float dist = 3 + (k * 37) % 9;
                    float s = 0.5F + (k % 3) * 0.3F + Mth.sin(time * 0.05F + k) * 0.12F;
                    float x = dist * Mth.cos(a), z = 16 + dist * Mth.sin(a);
                    d.box(x - s, y - 0.05F, z - s, x + s, y + 0.25F, z + s, foam, foam);
                }
                bubbles(d, foam, 0, 16, y, 11, 6, time, 0.035F);
            }
            surface(pose, buffers, light, fluid, 0, 16, 13.3F, y, 0.95F);
        }
        //Fourquet appuye au bord gauche, qui se balance pendant la fermentation
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex oak = KDraw.sprite("oak"), end = KDraw.sprite("oak_end");
        float rock = work ? Mth.sin(time * 0.06F) * 4F : 0;
        d.push().rotate(Direction.Axis.Z, 35 + rock, -4, 3, 18);
        d.box(-4.45F, 3, 17.55F, -3.55F, 20, 18.45F, oak, end);
        d.box(-5.4F, 3, 17.8F, -2.6F, 7.5F, 18.2F, oak, end);
        d.pop();
    }

    /** Chaudron de brassage 1 x 2. */
    private static void kettle(StationTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        boolean work = tile.working();
        FluidStack fluid = shown(tile);
        float y = 1.4F;
        if (fluid != null) {
            y = 1.4F + 11.0F * fill(tile) + (work ? Mth.sin(time * 0.4F) * 0.08F : 0);
            if (work) {
                bubbles(new KDraw(pose, buffers, light, overlay), tex("foam"), 8, 8, y, 4.5F, 7, time, 0.07F);
            }
            surface(pose, buffers, light, fluid, 8, 8, 6.2F, y, 0.95F);
        }
        //Fourquet qui tourne en remuant (un tour en 12 s)
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex oak = KDraw.sprite("oak"), end = KDraw.sprite("oak_end");
        float spin = work ? time * 1.5F : 25;
        d.push().rotate(Direction.Axis.Y, spin, 8, 0, 8);
        d.push().rotate(Direction.Axis.Z, -15, 11, 2, 8);
        d.box(10.55F, 2, 7.55F, 11.45F, 17, 8.45F, oak, end);
        d.box(10.0F, 2, 7.8F, 12.0F, 5.5F, 8.2F, oak, end);
        d.pop();
        d.pop();
    }

    /** Alambic 2 x 2 : serpentin dans sa cuve d'eau, flacon et goutte au bec. */
    private static void still(StationTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float time) {
        boolean work = tile.working();
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex copper = KDraw.sprite("copper");
        for (float cy : new float[]{6.6F, 8.6F, 10.6F, 12.6F}) {
            coil(d, copper, -8, 9, 3.4F, cy);
        }
        d.box(-6.2F, 12.15F, 7.55F, -4.6F, 13.05F, 8.45F, copper, copper);           //raccord du col de cygne
        //Flacon : l'eau-de-vie (fluide de sortie) monte
        FluidTank out = tile.outTank();
        FluidStack spirit = out == null || out.isEmpty() ? null : out.getFluid();
        if (spirit != null) {
            float level = fill(out);
            d.tint(tint(spirit));
            d.box(-9.0F, 0.45F, 0.85F, -7.0F, 0.45F + 1.85F * Math.max(0.08F, level), 2.75F, fluidTex(spirit), fluidTex(spirit));
            d.tint(0xFFFFFFFF);
        }
        //Goutte qui tombe du bec dans le flacon
        if (work) {
            FluidStack drop = spirit != null ? spirit : new FluidStack(Fluids.WATER, 1000);
            float ph = (time % 14F) / 14F;
            float yy = 4.35F - ph * 1.3F;
            d.tint(tint(drop));
            d.box(-8.18F, yy - 0.35F, 1.87F, -7.82F, yy, 2.23F, fluidTex(drop), fluidTex(drop));
            d.tint(0xFFFFFFFF);
        }
        surface(pose, buffers, light, new FluidStack(Fluids.WATER, 1000), -8, 9, 5.3F, 13.6F + (work ? Mth.sin(time * 0.2F) * 0.05F : 0), 0.75F);
    }

    /** Une spire du serpentin : 8 troncons (comme art_big_stations_preview.coil_turn). */
    private static void coil(KDraw d, KDraw.Tex t, float cx, float cz, float r, float y) {
        float h = (2 * r * (float) Math.tan(Math.PI / 8) + 0.6F) / 2;
        d.box(cx - h, y - 0.45F, cz - r - 0.45F, cx + h, y + 0.45F, cz - r + 0.45F, t, t);
        d.box(cx - h, y - 0.44F, cz + r - 0.45F, cx + h, y + 0.44F, cz + r + 0.45F, t, t);
        for (float x : new float[]{cx - r, cx + r}) {
            d.box(x - 0.45F, y - 0.43F, cz - h, x + 0.45F, y + 0.43F, cz + h, t, t);
            for (float ang : new float[]{45, -45}) {
                d.push().rotate(Direction.Axis.Y, ang, cx, y, cz);
                d.box(x - 0.45F, y - 0.42F, cz - h, x + 0.45F, y + 0.42F, cz + h, t, t);
                d.pop();
            }
        }
    }

    /**
     * Fut : marques de craie sur la tete, une par dixieme du vieillissement (paquets de cinq, le cinquieme en travers), de part et d'autre du
     * robinet ; reperes {@code barrel_head} / {@code barrel_tally} de art_big_stations.GEOM (1.6.2 : barrique 1 x 2).
     */
    private static void barrel(StationTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        int marks = tile.working() ? Mth.floor(tile.fraction() * 10) : 0;
        if (marks <= 0) {
            return;
        }
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KDraw.Tex chalk = tex("chalk");
        float hz = 1.65F, y0 = 6.4F, y1 = 9.2F;
        float[] group = {3.4F, 9.4F};
        for (int i = 0; i < Math.min(10, marks); i++) {
            int g = i / 5, k = i % 5;
            float x = group[g] + k * 0.8F;
            if (k == 4) {
                float cx = x - 1.6F, cy = (y0 + y1) / 2;
                d.push().rotate(Direction.Axis.Z, 22.5F, cx, cy, hz);
                d.box(cx - 0.18F, y0 - 0.3F, hz - 0.08F, cx + 0.18F, y1 + 0.3F, hz - 0.03F, chalk, chalk);
                d.pop();
            } else {
                d.box(x - 0.18F, y0, hz - 0.08F, x + 0.18F, y1, hz - 0.03F, chalk, chalk);
            }
        }
    }

    /** Fumoir : pieces pendues aux crochets (cases 1 a 4). */
    private static void smokehouse(StationTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay, Level level, float time) {
        boolean work = tile.working();
        for (int i = 0; i < 4; i++) {
            ItemStack s = tile.items().get(1 + i);
            if (s.isEmpty()) {
                continue;
            }
            float sway = work ? Mth.sin(time * 0.05F + i * 1.3F) * 3F : 0;
            pose.pushPose();
            //Deux rangs (z 7 et 9) pour que les pieces voisines ne se recouvrent pas
            pose.translate(HOOKS[i] / 16F, 24.8F / 16F, (i % 2 == 0 ? 7F : 9F) / 16F);
            pose.mulPose(Axis.ZP.rotationDegrees(sway));
            KDraw d = new KDraw(pose, buffers, light, overlay);
            d.box(-0.2F, -1.4F, -0.2F, 0.2F, 0, 0.2F, tex("rope"), tex("rope"));
            item(pose, buffers, light, level, s, 0, -4.4F, 0, 0.38F, i % 2 == 0 ? 25 : -25, (int) tile.getBlockPos().asLong() + i * 31);
            pose.popPose();
        }
    }
}
