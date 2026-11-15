package fr.pioneers.client.farm;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.core.RenderClock;
import fr.pioneers.farm.food.FarmFoodItem;
import fr.pioneers.farm.kitchen.StationBlock;
import fr.pioneers.farm.kitchen.StationTile;
import fr.pioneers.farm.kitchen.StationTiles;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Ce qui bouge ou change sur les postes de cuisine : aliments poses (planche, poele, four, sechoir, bocal), liquide des cuves (niveau,
 * couleur du fluide), ingredients qui tournent dans la marmite et le saladier, plateau et vis des presses, levier de la sertisseuse. Repere :
 * pixels du modele, facade au nord, tourne selon {@code facing} (reperes recopies de {@code tools/gc/farm2/art_stations.py}).
 */
public class StationRenderer implements BlockEntityRenderer<StationTile> {

    private static final float COS22 = (float) Math.cos(Math.PI / 8);

    public StationRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRenderOffScreen(StationTile tile) {
        return tile.kind() == StationBlock.Kind.FRUIT_PRESS || tile.kind() == StationBlock.Kind.CHEESE_PRESS;
    }

    @Override
    public void render(StationTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        float time = (float) RenderClock.time(partial);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float since = time - tile.lastStroke;
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(tile.facing())));
        pose.translate(-0.5, 0, -0.5);
        Ctx c = new Ctx(pose, buffers, light, overlay, level, tile, time, since);
        switch (tile.kind()) {
            case CUTTING_BOARD -> cuttingBoard(c);
            case COOKING_POT -> cookingPot(c);
            case FRYING_PAN -> pan(c);
            case BREAD_OVEN -> oven(c);
            case MIXING_BOWL -> mixingBowl(c);
            case DRYING_RACK -> dryingRack(c);
            case PRESERVES_JAR -> jar(c);
            //1.6 (lot A2) : grands postes de la brasserie et fumoir (repere : case principale, le poste s'etend vers -x, +y, +z)
            case FERMENTING_VAT, BREW_KETTLE, COPPER_STILL, AGING_BARREL, SMOKEHOUSE -> BigStationRenderer.render(tile, pose, buffers, light, overlay,
                  level, time);
            case FRUIT_PRESS -> fruitPress(c);
            case CHEESE_PRESS -> cheesePress(c);
            case CAN_SEALER -> sealer(c);
            case BUTTER_CHURN -> churn(c);
            case FEEDER -> feeder(c);
            case WATER_TROUGH -> trough(c);
            case HONEY_EXTRACTOR -> extractor(c);
            default -> {
            }
        }
        pose.popPose();
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Contexte d'une image. */
    private record Ctx(PoseStack pose, MultiBufferSource buffers, int light, int overlay, Level level, StationTile tile, float time, float since) {

        NonNullList<ItemStack> items() {
            return tile.items();
        }
    }

    // ------------------------------------------------------------------ outils

    /** Objet pose (pixels) : a plat (couche) ou debout, tourne de {@code yaw}, a l'echelle {@code scale} (1 = un bloc). */
    private static void item(Ctx c, ItemStack stack, float x, float y, float z, float scale, float yaw, boolean flat, int seed) {
        if (stack.isEmpty()) {
            return;
        }
        PoseStack pose = c.pose;
        pose.pushPose();
        pose.translate(x / 16F, y / 16F, z / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        if (flat) {
            pose.mulPose(Axis.XP.rotationDegrees(90));
        }
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, c.light, OverlayTexture.NO_OVERLAY, pose, c.buffers,
              c.level, seed);
        pose.popPose();
    }

    /** Surface d'un liquide : octogone de rayon inscrit {@code r} (pixels) a la hauteur {@code y}. */
    private static void surface(Ctx c, FluidStack fluid, float cx, float cz, float r, float y, float alpha) {
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid);
        float red = (tint >> 16 & 255) / 255F, green = (tint >> 8 & 255) / 255F, blue = (tint & 255) / 255F;
        float a = Math.min(1F, ((tint >>> 24) == 0 ? 1F : (tint >>> 24) / 255F) * alpha + 0.25F);
        VertexConsumer vc = c.buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        Matrix4f m = c.pose.last().pose();
        Matrix3f n = c.pose.last().normal();
        float rr = r / COS22;
        for (int k = 0; k < 8; k++) {
            double a0 = Math.toRadians(22.5 + 45 * k), a1 = Math.toRadians(22.5 + 45 * (k + 1));
            float x0 = cx + rr * (float) Math.cos(a0), z0 = cz + rr * (float) Math.sin(a0);
            float x1 = cx + rr * (float) Math.cos(a1), z1 = cz + rr * (float) Math.sin(a1);
            vertex(vc, m, n, cx, y, cz, sp, red, green, blue, a, c.light);
            vertex(vc, m, n, x1, y, z1, sp, red, green, blue, a, c.light);
            vertex(vc, m, n, x0, y, z0, sp, red, green, blue, a, c.light);
            vertex(vc, m, n, x0, y, z0, sp, red, green, blue, a, c.light);
        }
    }

    private static void vertex(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, TextureAtlasSprite sp, float r, float g, float b,
          float a, int light) {
        vc.vertex(m, x / 16F, y / 16F, z / 16F).color(r, g, b, a).uv(sp.getU(Mth.clamp(x, 0, 16)), sp.getV(Mth.clamp(z, 0, 16)))
              .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, 0, 1, 0).endVertex();
    }

    private static KDraw.Tex tex(String name) {
        return KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Pioneers.rl("block/farm/kitchen/" + name)));
    }

    /** Prisme octogonal d'axe y (rayon inscrit r, pixels). */
    private static void octa(KDraw d, float cx, float cz, float r, float y0, float y1, KDraw.Tex side, KDraw.Tex ends) {
        float h = r * (float) Math.tan(Math.PI / 8);
        d.box(cx - r, y0, cz - h, cx + r, y1, cz + h, side, ends);
        d.box(cx - h, y0 + 0.01F, cz - r, cx + h, y1 - 0.01F, cz + r, side, ends);
        for (float ang : new float[]{45, -45}) {
            d.push().rotate(Direction.Axis.Y, ang, cx, 0, cz);
            d.box(cx - r, y0 + 0.02F, cz - h, cx + r, y1 - 0.02F, cz + h, side, ends);
            d.pop();
        }
    }

    /** Geste recent : 0..1..0 sur {@code length} ticks (0 en dehors). */
    private static float pulse(float since, float length) {
        return since >= 0 && since < length ? Mth.sin((float) Math.PI * since / length) : 0;
    }

    private static int seed(StationTile tile, int i) {
        return (int) tile.getBlockPos().asLong() + i * 31;
    }

    @Nullable
    private static FluidStack shown(StationTile tile) {
        FluidTank out = tile.outTank(), in = tile.tank();
        if (out != null && !out.isEmpty()) {
            return out.getFluid();
        }
        return in != null && !in.isEmpty() ? in.getFluid() : null;
    }

    private static float level(StationTile tile) {
        FluidTank out = tile.outTank(), in = tile.tank();
        FluidTank t = out != null && !out.isEmpty() ? out : in;
        return t == null || t.isEmpty() ? 0 : t.getFluidAmount() / (float) t.getCapacity();
    }

    // ------------------------------------------------------------------ postes

    private static void cuttingBoard(Ctx c) {
        ItemStack s = c.items().get(0);
        float hop = pulse(c.since, 5) * 0.8F;
        float squash = 1F - pulse(c.since, 5) * 0.12F;
        //1.6 : pose sur la planche (dessus y 1,5), plus a moitie dedans
        item(c, s, 7.25F, onTop(1.5F, 0.55F, s) + hop, 8F, 0.55F * squash, 15, true, seed(c.tile, 0));
    }

    // ------------------------------------------------------------------ 1.6 (lot A1) : reperes recopies de art_stations.py
    /** Poele ({@code PAN}) : centre, fond, rayon interieur. */
    private static final float PAN_X = 8F, PAN_Z = 9.2F, PAN_FLOOR = 0.7F, PAN_INNER = 5.6F;
    /** Pressoir ({@code PRESS}) : fond et rebord de la maie, interieur du rebord, interieur du panier. */
    private static final float PRESS_FLOOR = 1.5F, PRESS_RIM = 3.2F, PRESS_IN0 = 2.0F, PRESS_IN1 = 14.0F, PRESS_BASKET_IN = 4.0F;
    /** Bocal ({@code JAR}) : charniere du couvercle, rayon et hauteurs du contenu. */
    private static final float JAR_HINGE_Y = 10.7F, JAR_HINGE_Z = 12.0F, JAR_LIQ_R = 3.3F, JAR_FLOOR = 0.85F, JAR_FULL = 8.1F;
    /** Mangeoire : interieur de l'auge. */
    private static final float FEED_X0 = 1F, FEED_X1 = 15F, FEED_Z0 = 4F, FEED_Z1 = 12F, FEED_FLOOR = 4F;
    /** Couvercle du bocal (modele en plus, enregistre par {@code PlacedDishRenderer.Setup}). */
    public static final ResourceLocation JAR_LID = Pioneers.rl("block/farm/kitchen/preserves_jar_lid");

    /**
     * Hauteur du centre d'un objet couche pose sur une surface : jamais dans la surface (objet plat : 1 px d'epaisseur a l'echelle ; bloc :
     * cube de 8 px a l'echelle, comme l'affichage « fixed » des blocs).
     */
    private static float onTop(float surface, float scale, ItemStack stack) {
        return surface + (stack.getItem() instanceof BlockItem ? 4F : 0.5F) * scale + 0.04F;
    }

    /**
     * Poele : les aliments restent dans le diametre interieur (5,6 px) quel que soit leur nombre ; echelle selon le nombre, chacun a sa
     * hauteur (jamais deux faces dans le meme plan) ; la friture les fait trembler sur place.
     */
    private static final float[][] PAN_SPOTS = {{0, 0}, {-2.6F, 0, 2.6F, 0}, {0, -2.9F, 2.51F, 1.45F, -2.51F, 1.45F},
                                                {-2.1F, -2.1F, 2.1F, -2.1F, -2.1F, 2.1F, 2.1F, 2.1F}};
    private static final float[] PAN_SCALE = {0.5F, 0.36F, 0.31F, 0.29F};

    private static void pan(Ctx c) {
        int from = c.items().size() - 4, n = 0;
        for (int i = 0; i < 4; i++) {
            if (!c.items().get(from + i).isEmpty()) {
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        float s = PAN_SCALE[n - 1];
        float[] spots = PAN_SPOTS[n - 1];
        boolean fry = c.tile.working();
        int k = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack st = c.items().get(from + i);
            if (st.isEmpty()) {
                continue;
            }
            float hop = fry ? (Mth.sin(c.time * 1.9F + i * 2.3F) * 0.5F + 0.5F) * 0.05F : 0;
            float wobble = fry ? Mth.sin(c.time * 1.3F + i * 1.7F) * 3F : 0;
            item(c, st, PAN_X + spots[k * 2], onTop(PAN_FLOOR, s, st) + k * 0.07F + hop, PAN_Z + spots[k * 2 + 1], s, i * 37 + 10 + wobble, true,
                  seed(c.tile, i));
            k++;
        }
    }

    private static void cookingPot(Ctx c) {
        float lvl = level(c.tile);
        FluidStack fluid = shown(c.tile);
        float surf = 1.4F + 7.6F * Math.max(lvl, 0.0F);
        boolean work = c.tile.working();
        float spin = work ? c.time * 1.6F : 0;
        int n = 0;
        for (int i = 0; i < 6; i++) {
            if (!c.items().get(i).isEmpty()) {
                n++;
            }
        }
        int k = 0;
        float base = fluid != null ? surf - 0.6F : 1.6F;
        for (int i = 0; i < 6; i++) {
            ItemStack s = c.items().get(i);
            if (s.isEmpty()) {
                continue;
            }
            float ang = (float) Math.toRadians(spin + k * 360F / Math.max(1, n));
            float rad = n == 1 ? 0 : 2.6F;
            float bob = work ? Mth.sin(c.time * 0.25F + k * 1.7F) * 0.35F : 0;
            item(c, s, 8 + rad * Mth.cos(ang), base + bob + k * 0.15F, 8 + rad * Mth.sin(ang), 0.36F, -spin - k * 60, true, seed(c.tile, i));
            k++;
        }
        ItemStack out = c.items().get(6);
        if (!out.isEmpty()) {
            float bob = Mth.sin(c.time * 0.08F) * 0.3F;
            item(c, out, 8, 10.6F + bob, 8, 0.5F, c.time * 1.2F, false, seed(c.tile, 6));
        }
        if (fluid != null) {
            surface(c, fluid, 8, 8, 4.8F, surf + (work ? Mth.sin(c.time * 0.3F) * 0.08F : 0), 0.85F);
        }
    }

    private static void oven(Ctx c) {
        //Sole a y 4 ; quatre places dans la chambre (x 2,5..13,5, z 2,5..11 : le foyer est derriere), chacune a sa hauteur (1.6)
        for (int i = 0; i < 4; i++) {
            ItemStack s = c.items().get(1 + i);
            float x = i % 2 == 0 ? 5.3F : 10.7F, z = i < 2 ? 5.0F : 8.6F;
            item(c, s, x, onTop(4.0F, 0.3F, s) + i * 0.05F, z, 0.3F, i * 23, true, seed(c.tile, i));
        }
    }

    private static void mixingBowl(Ctx c) {
        FluidStack fluid = shown(c.tile);
        //Chaque coup de fouet fait tourner le contenu d'un quart de tour (amorti)
        float turn = c.tile.progress() * 90F + (c.since >= 0 && c.since < 12 ? -90F * (1 - easeOut(c.since / 12F)) : 0);
        int n = 0;
        for (int i = 0; i < 6; i++) {
            if (!c.items().get(i).isEmpty()) {
                n++;
            }
        }
        int k = 0;
        for (int i = 0; i < 6; i++) {
            ItemStack s = c.items().get(i);
            if (s.isEmpty()) {
                continue;
            }
            float ang = (float) Math.toRadians(turn + k * 360F / Math.max(1, n));
            float rad = n == 1 ? 0 : 2.0F;
            item(c, s, 8 + rad * Mth.cos(ang), 2.0F + k * 0.25F, 8 + rad * Mth.sin(ang), 0.34F, -turn - k * 50, true, seed(c.tile, i));
            k++;
        }
        ItemStack out = c.items().get(6);
        if (!out.isEmpty()) {
            item(c, out, 8, 3.0F, 8, 0.5F, 20, true, seed(c.tile, 6));
        }
        if (fluid != null) {
            surface(c, fluid, 8, 8, 4.4F, 2.2F + 2.6F * level(c.tile), 0.8F);
        }
    }

    private static float easeOut(float t) {
        t = Mth.clamp(t, 0, 1);
        return 1 - (1 - t) * (1 - t) * (1 - t);
    }

    /**
     * 1.6 (lot A1) : sechoir a plusieurs objets : ils sont repartis le long de la barre selon leur nombre, alternativement devant et
     * derriere elle et chacun dans son propre plan (z different) : deux objets voisins ne se coupent plus et ne scintillent plus, meme en
     * se balancant ; echelle selon le nombre.
     */
    private static final float[][] RACK_X = {{8}, {5.5F, 10.5F}, {4, 8, 12}, {3.2F, 6.4F, 9.6F, 12.8F}};
    private static final float[] RACK_SCALE = {0.5F, 0.42F, 0.38F, 0.36F};
    /** Barre du sechoir : dessous (pivot des ficelles) et milieu en z. */
    private static final float RACK_HOOK_Y = 13F, RACK_Z = 8F;

    private static void dryingRack(Ctx c) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            if (!c.items().get(i).isEmpty()) {
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        float s = RACK_SCALE[n - 1];
        KDraw.Tex rope = tex("rope");
        int k = 0;
        for (int i = 0; i < 4; i++) {
            ItemStack st = c.items().get(i);
            if (st.isEmpty()) {
                continue;
            }
            float x = RACK_X[n - 1][k];
            float z = RACK_Z + (k % 2 == 0 ? -1 : 1) * (1.25F + 0.3F * (k / 2));
            float sway = Mth.sin(c.time * 0.045F + i * 1.3F) * 5F;
            float drop = 2.6F + 8F * s + 0.2F;
            PoseStack pose = c.pose;
            pose.pushPose();
            pose.translate(x / 16F, RACK_HOOK_Y / 16F, z / 16F);
            pose.mulPose(Axis.ZP.rotationDegrees(sway));
            //ficelle : de la barre (passee par-dessus) jusqu'au haut de l'objet
            KDraw d = new KDraw(pose, c.buffers, c.light, c.overlay);
            d.box(-0.22F, -2.6F, -0.22F, 0.22F, 1.1F, 0.22F, rope, rope);
            item(c, st, 0, -drop, 0, s, 0, false, seed(c.tile, i));
            pose.popPose();
            k++;
        }
    }

    /**
     * 1.6 (lot A1) : bocal : sirop, saumure ou lait translucide a l'interieur (niveau = quantite d'ingredients, puis plein quand la conserve
     * est prete ; la couleur fonce pendant la cuisson et prend celle du produit), fruits ou legumes qui flottent dedans, couvercle de verre a
     * etrier ouvert tant que le bocal n'est pas ferme. Liquide et objets dans le meme tampon translucide (tries ensemble : les fruits restent
     * visibles a travers le sirop).
     */
    private static void jar(Ctx c) {
        ItemStack out = c.items().get(4);
        boolean sealed = c.tile instanceof StationTiles.Jar j && j.sealed();
        float level;
        int colour;
        float alpha;
        ItemStack[] floaters = new ItemStack[6];
        int nf = 0;
        if (!out.isEmpty()) {
            level = 1F;
            String id = BuiltInRegistries.ITEM.getKey(out.getItem()).getPath();
            Item src = FarmFoodItem.source(out);
            int sc = src == null ? -1 : FarmClient.colour(src);
            int own = FarmClient.colour(out.getItem());
            if (id.equals("pickles")) {
                colour = mixRgb(sc == -1 ? 0xB4C878 : sc, 0xE2D696, 0.65F);
                alpha = 0.6F;
                if (src != null) {
                    ItemStack one = new ItemStack(src);
                    for (int i = 0; i < 4; i++) {
                        floaters[nf++] = one;
                    }
                }
            } else if (id.equals("jam")) {
                colour = mulRgb(sc == -1 ? (own == -1 ? 0xC02840 : own) : sc, 0.82F);
                alpha = 0.86F;
            } else {
                colour = own == -1 ? 0xE8E4D8 : own;
                alpha = 0.94F;
            }
        } else {
            int total = 0, r = 0, g = 0, b = 0, w = 0;
            for (int i = 0; i < 4; i++) {
                ItemStack s = c.items().get(i);
                if (s.isEmpty()) {
                    continue;
                }
                total += s.getCount();
                int col = s.is(Items.MILK_BUCKET) ? 0xF4F2EA : FarmClient.colour(s.getItem());
                if (col != -1) {
                    r += (col >> 16 & 255) * s.getCount();
                    g += (col >> 8 & 255) * s.getCount();
                    b += (col & 255) * s.getCount();
                    w += s.getCount();
                }
                String path = BuiltInRegistries.ITEM.getKey(s.getItem()).getPath();
                boolean solid = !(path.contains("sugar") || path.contains("salt") || path.contains("vinegar") || path.contains("bucket"));
                for (int j = 0; solid && j < Math.min(2, s.getCount()) && nf < floaters.length; j++) {
                    floaters[nf++] = s;
                }
            }
            if (total == 0) {
                drawJarLid(c, sealed);
                return;
            }
            level = Math.min(1F, total / 5F);
            int base = w == 0 ? 0xD8A050 : (r / w) << 16 | (g / w) << 8 | (b / w);
            //Cru : sirop clair ; il fonce et s'epaissit pendant la cuisson
            float f = sealed ? c.tile.fraction() : 0;
            colour = mixRgb(mixRgb(base, 0xF0E6C8, 0.45F), mulRgb(base, 0.8F), f);
            alpha = 0.5F + 0.3F * f;
        }
        float top = JAR_FLOOR + (JAR_FULL - JAR_FLOOR) * level;
        //Meme tampon que les objets dessines par ItemRenderer (translucentCullBlockSheet) : tries ensemble
        VertexConsumer vc = c.buffers.getBuffer(Sheets.translucentCullBlockSheet());
        prism(vc, c.pose, 8, 8, JAR_LIQ_R, JAR_FLOOR, top, sprite(c, "jar_liquid"), colour, alpha, c.light);
        //Fruits et legumes en suspension (lent mouvement), toujours dans le diametre du bocal
        for (int k = 0; k < nf; k++) {
            float ang = k * 2.4F + c.time * 0.01F;
            float rad = k == 0 ? 0.3F : 1.1F;
            float y = JAR_FLOOR + 1.6F + (top - JAR_FLOOR - 2.6F) * (k + 0.5F) / Math.max(1, nf) + Mth.sin(c.time * 0.05F + k) * 0.15F;
            item(c, floaters[k], 8 + Mth.cos(ang) * rad, Math.min(y, top - 1.0F), 8 + Mth.sin(ang) * rad, 0.24F, k * 53F + c.time * 0.2F, false,
                  seed(c.tile, k));
        }
        drawJarLid(c, sealed || !out.isEmpty());
    }

    /** Couvercle du bocal : ferme (0 deg) ou ouvert vers l'arriere autour de la charniere (x). */
    private static void drawJarLid(Ctx c, boolean closed) {
        BakedModel lid = Minecraft.getInstance().getModelManager().getModel(JAR_LID);
        PoseStack pose = c.pose;
        pose.pushPose();
        if (!closed) {
            pose.translate(0, JAR_HINGE_Y / 16F, JAR_HINGE_Z / 16F);
            pose.mulPose(Axis.XP.rotationDegrees(105));
            pose.translate(0, -JAR_HINGE_Y / 16F, -JAR_HINGE_Z / 16F);
        }
        Minecraft.getInstance().getBlockRenderer().getModelRenderer().renderModel(pose.last(),
              c.buffers.getBuffer(Sheets.translucentCullBlockSheet()), null, lid, 1F, 1F, 1F, c.light, c.overlay,
              ModelData.EMPTY, RenderType.translucent());
        pose.popPose();
    }

    private static int mixRgb(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
    }

    private static int mulRgb(int a, float k) {
        return Math.min(255, (int) ((a >> 16 & 255) * k)) << 16 | Math.min(255, (int) ((a >> 8 & 255) * k)) << 8 | Math.min(255, (int) ((a & 255) * k));
    }

    /** Sprite de la cuisine (mis en cache jusqu'au prochain rechargement des textures). */
    private static final Map<String, TextureAtlasSprite> SPRITES = new HashMap<>();
    private static int spritesGen = -1;

    private static TextureAtlasSprite sprite(Ctx c, String name) {
        if (spritesGen != KDraw.generation()) {
            SPRITES.clear();
            spritesGen = KDraw.generation();
        }
        return SPRITES.computeIfAbsent(name, n -> Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
              .apply(Pioneers.rl(n.contains("/") ? n : "block/farm/kitchen/" + n)));
    }

    /**
     * Prisme octogonal translucide ferme (flancs + dessus), faces tournees vers l'exterieur (le tampon elimine les faces arriere : aucune face
     * interieure visible a travers le liquide). Rayon inscrit {@code r}, pixels.
     */
    private static void prism(VertexConsumer vc, PoseStack pose, float cx, float cz, float r, float y0, float y1, TextureAtlasSprite sp, int rgb,
          float a, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float red = (rgb >> 16 & 255) / 255F, green = (rgb >> 8 & 255) / 255F, blue = (rgb & 255) / 255F;
        float rr = r / COS22;
        float[] xs = new float[9], zs = new float[9];
        for (int k = 0; k <= 8; k++) {
            double ang = Math.toRadians(22.5 + 45 * k);
            xs[k] = cx + rr * (float) Math.cos(ang);
            zs[k] = cz + rr * (float) Math.sin(ang);
        }
        for (int k = 0; k < 8; k++) {
            //Flanc k (sens des aiguilles vu de dessus = exterieur, comme les faces vanilla)
            float nx = (float) Math.cos(Math.toRadians(45 * (k + 1))), nz = (float) Math.sin(Math.toRadians(45 * (k + 1)));
            float u0 = sp.getU(0), u1 = sp.getU(Math.min(16F, rr * 0.765F));
            put(vc, m, n, xs[k], y1, zs[k], sp.getU(0), sp.getV(16 - Mth.clamp(y1, 0, 16)), red, green, blue, a, light, nx, 0, nz);
            put(vc, m, n, xs[k + 1], y1, zs[k + 1], u1, sp.getV(16 - Mth.clamp(y1, 0, 16)), red, green, blue, a, light, nx, 0, nz);
            put(vc, m, n, xs[k + 1], y0, zs[k + 1], u1, sp.getV(16 - Mth.clamp(y0, 0, 16)), red, green, blue, a, light, nx, 0, nz);
            put(vc, m, n, xs[k], y0, zs[k], u0, sp.getV(16 - Mth.clamp(y0, 0, 16)), red, green, blue, a, light, nx, 0, nz);
            //Dessus : eventail depuis le centre
            put(vc, m, n, cx, y1, cz, sp.getU(Mth.clamp(cx, 0, 16)), sp.getV(Mth.clamp(cz, 0, 16)), red, green, blue, a, light, 0, 1, 0);
            put(vc, m, n, xs[k + 1], y1, zs[k + 1], sp.getU(Mth.clamp(xs[k + 1], 0, 16)), sp.getV(Mth.clamp(zs[k + 1], 0, 16)), red, green, blue, a,
                  light, 0, 1, 0);
            put(vc, m, n, xs[k], y1, zs[k], sp.getU(Mth.clamp(xs[k], 0, 16)), sp.getV(Mth.clamp(zs[k], 0, 16)), red, green, blue, a, light, 0, 1, 0);
            put(vc, m, n, xs[k], y1, zs[k], sp.getU(Mth.clamp(xs[k], 0, 16)), sp.getV(Mth.clamp(zs[k], 0, 16)), red, green, blue, a, light, 0, 1, 0);
        }
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float z, float u, float v, float r, float g, float b, float a,
          int light, float nx, float ny, float nz) {
        vc.vertex(m, x / 16F, y / 16F, z / 16F).color(r, g, b, a).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light).normal(n, nx, ny, nz)
              .endVertex();
    }

    /** Cuve, chaudron : liquide qui monte avec le niveau, frisson a la surface pendant le travail. */
    private static void vessel(Ctx c, float cx, float cz, float r, float y0, float y1, boolean working) {
        FluidStack fluid = shown(c.tile);
        if (fluid == null) {
            return;
        }
        float lvl = level(c.tile);
        float y = y0 + (y1 - y0) * lvl + (working ? Mth.sin(c.time * 0.21F) * 0.06F : 0);
        surface(c, fluid, cx, cz, r, y, 0.9F);
    }

    /** Pressoir : fruits dans le panier, plateau et vis qui descendent avec les tours, croisillon qui tourne. */
    private static void fruitPress(Ctx c) {
        float f = c.tile.need() > 0 ? Mth.clamp(c.tile.progress() / (float) c.tile.need(), 0, 1) : 0;
        float stroke = pulse(c.since, 8);
        ItemStack s = c.items().get(0);
        int layers = Math.min(5, (s.getCount() + 3) / 4);
        float squash = 1F - 0.55F * f;
        //1.6 : fruits dans le panier (interieur 4 px de rayon), sur le fond de la maie
        float fruit0 = onTop(PRESS_FLOOR, 0.38F, s);
        for (int i = 0; i < layers; i++) {
            item(c, s, 8 + (i % 2 == 0 ? -0.5F : 0.5F), fruit0 + i * 1.2F * squash, 8 + (i % 3 - 1) * 0.5F, 0.38F, i * 55, true, seed(c.tile, i));
        }
        float plateY = 10.4F - 6.4F * f - stroke * 0.5F;
        if (layers > 0) {
            plateY = Math.min(plateY, fruit0 + layers * 1.2F * squash + 0.6F);
        }
        KDraw d = new KDraw(c.pose, c.buffers, c.light, c.overlay);
        KDraw.Tex wood = tex("wood_light"), end = tex("wood_end"), screw = tex("screw"), iron = tex("iron");
        octa(d, 8, 8, 3.9F, plateY, plateY + 1.2F, wood, end);
        float spin = c.tile.progress() * 45F + stroke * 30F;
        d.push().rotate(Direction.Axis.Y, spin, 8, 0, 8);
        d.box(7.3F, plateY + 1.2F, 7.3F, 8.7F, 18.6F, 8.7F, screw, iron);
        d.box(3.0F, 17.4F, 7.5F, 13.0F, 18.2F, 8.5F, iron, iron);
        d.box(2.6F, 17.0F, 7.3F, 3.4F, 18.6F, 8.7F, tex("wood_dark"), tex("wood_dark"));
        d.box(12.6F, 17.0F, 7.3F, 13.4F, 18.6F, 8.7F, tex("wood_dark"), tex("wood_dark"));
        d.pop();
        FluidStack juice = c.tile.outTank() == null ? FluidStack.EMPTY : c.tile.outTank().getFluid();
        if (!juice.isEmpty()) {
            //1.6 : le jus s'etale dans la maie, toujours a l'interieur du rebord et sous son bord (il ne deborde plus du pressoir)
            float lvl = c.tile.outTank().getFluidAmount() / (float) c.tile.outTank().getCapacity();
            float y = PRESS_FLOOR + 0.12F + lvl * (PRESS_RIM - 0.35F - PRESS_FLOOR - 0.12F);
            rect(c, juice, PRESS_IN0 + 0.02F, PRESS_IN0 + 0.02F, PRESS_IN1 - 0.02F, PRESS_IN1 - 0.02F, y, 0.88F);
        }
    }

    private static void cheesePress(Ctx c) {
        float f = c.tile.need() > 0 ? Mth.clamp(c.tile.progress() / (float) c.tile.need(), 0, 1) : 0;
        float stroke = pulse(c.since, 8);
        ItemStack curd = c.items().get(0), wheel = c.items().get(1);
        if (!wheel.isEmpty()) {
            //1.6 : la meule moulee dans le moule (meme textures que la meule posee), plus un objet debout qui traversait le socle
            String id = BuiltInRegistries.ITEM.getKey(wheel.getItem()).getPath();
            if (id.endsWith("cheese_wheel")) {
                KDraw w = new KDraw(c.pose, c.buffers, c.light, c.overlay);
                octa(w, 8, 8, 2.85F, 1.5F, 4.3F, KDraw.of(sprite(c, "block/farm/food/" + id + "_rind_0")),
                      KDraw.of(sprite(c, "block/farm/food/" + id + "_top_0")));
            } else {
                item(c, wheel, 8, onTop(1.5F, 0.36F, wheel), 8, 0.36F, 0, true, seed(c.tile, 1));
            }
        } else {
            for (int i = 0; i < Math.min(4, curd.getCount()); i++) {
                item(c, curd, 8 + (i % 2 == 0 ? -0.4F : 0.4F), onTop(1.5F, 0.3F, curd) + i * 0.9F * (1F - 0.5F * f), 8, 0.3F, i * 70, true, seed(c.tile, i));
            }
        }
        float plateY = 6.2F - 3.0F * f - stroke * 0.4F;
        KDraw d = new KDraw(c.pose, c.buffers, c.light, c.overlay);
        KDraw.Tex wood = tex("wood_light"), end = tex("wood_end"), screw = tex("screw"), iron = tex("iron");
        octa(d, 8, 8, 2.9F, plateY, plateY + 0.9F, wood, end);
        float spin = c.tile.progress() * 40F + stroke * 25F;
        d.push().rotate(Direction.Axis.Y, spin, 8, 0, 8);
        d.box(7.4F, plateY + 0.9F, 7.4F, 8.6F, 16.4F, 8.6F, screw, iron);
        d.box(4.0F, 15.6F, 7.6F, 12.0F, 16.2F, 8.4F, iron, iron);
        d.pop();
    }

    /**
     * Mangeoire (1.6 : plus rien ne deborde) : un tas par case remplie, repartis le long de l'auge en quinconce, echelle selon le nombre de
     * tas, tous dans l'interieur de l'auge (x 1..15, z 4..12, rotation de +-10 deg comprise) ; un tas = 1 a 3 couches selon la quantite,
     * chaque couche a sa hauteur.
     */
    private static void feeder(Ctx c) {
        int n = 0;
        for (int i = 0; i < 9; i++) {
            if (!c.items().get(i).isEmpty()) {
                n++;
            }
        }
        if (n == 0) {
            return;
        }
        float s = n <= 2 ? 0.42F : n <= 4 ? 0.4F : n <= 6 ? 0.34F : 0.3F;
        float h = 8F * s * 1.16F;
        float x0 = FEED_X0 + h + 0.1F, x1 = FEED_X1 - h - 0.1F;
        float dz = n == 1 ? 0 : Math.max(0, Math.min(1.6F, (FEED_Z1 - FEED_Z0) / 2F - h - 0.1F));
        int k = 0;
        for (int i = 0; i < 9; i++) {
            ItemStack st = c.items().get(i);
            if (st.isEmpty()) {
                continue;
            }
            float x = n == 1 ? 8F : x0 + (x1 - x0) * k / (n - 1);
            float z = 8F + (k % 2 == 0 ? -dz : dz);
            int layers = Math.min(3, (st.getCount() + 20) / 21);
            for (int l = 0; l < layers; l++) {
                float jx = l == 0 ? 0 : (l == 1 ? 0.15F : -0.15F);
                float yaw = (k * 7 + l * 13) % 21 - 10;
                item(c, st, x + jx, onTop(FEED_FLOOR, s, st) + k * 0.05F + l * 0.45F, z, s, yaw + (l % 2) * 90, true, seed(c.tile, k * 3 + l));
            }
            k++;
        }
    }

    /** Abreuvoir : niveau de l'eau. */
    private static void trough(Ctx c) {
        FluidStack fluid = shown(c.tile);
        if (fluid != null) {
            rect(c, fluid, 1.5F, 3.5F, 14.5F, 12.5F, 1.2F + 4.5F * level(c.tile), 0.85F);
        }
    }

    /** Surface rectangulaire d'un liquide (pixels). */
    private static void rect(Ctx c, FluidStack fluid, float x0, float z0, float x1, float z1, float y, float alpha) {
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        int tint = ext.getTintColor(fluid.getFluid().defaultFluidState(), c.level, c.tile.getBlockPos());
        float red = (tint >> 16 & 255) / 255F, green = (tint >> 8 & 255) / 255F, blue = (tint & 255) / 255F;
        VertexConsumer vc = c.buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
        Matrix4f m = c.pose.last().pose();
        Matrix3f n = c.pose.last().normal();
        vertex(vc, m, n, x0, y, z0, sp, red, green, blue, alpha, c.light);
        vertex(vc, m, n, x0, y, z1, sp, red, green, blue, alpha, c.light);
        vertex(vc, m, n, x1, y, z1, sp, red, green, blue, alpha, c.light);
        vertex(vc, m, n, x1, y, z0, sp, red, green, blue, alpha, c.light);
    }

    /** Baratte : le batteur plonge a chaque coup. */
    private static void churn(Ctx c) {
        float dip = pulse(c.since, 9) * 4.5F;
        KDraw d = new KDraw(c.pose, c.buffers, c.light, c.overlay);
        KDraw.Tex wood = tex("wood_light"), end = tex("wood_end"), dark = tex("wood_dark");
        d.box(7.5F, 11.0F - dip, 7.5F, 8.5F, 21.0F - dip, 8.5F, wood, end);
        d.box(6.4F, 20.6F - dip, 7.3F, 9.6F, 21.8F - dip, 8.7F, dark, dark);
    }

    /**
     * Extracteur de miel (reperes de {@code art_stations.honey_extractor}) : miel au fond de la cuve, cage a cadres qui tourne a chaque tour de
     * manivelle (un quart de tour par geste, elan amorti), manivelle sur l'arbre du boitier (x 11,2, y 14,3).
     */
    private static void extractor(Ctx c) {
        FluidStack honey = c.tile.outTank() == null ? FluidStack.EMPTY : c.tile.outTank().getFluid();
        if (!honey.isEmpty()) {
            float lvl = c.tile.outTank().getFluidAmount() / (float) c.tile.outTank().getCapacity();
            surface(c, honey, 8, 8, 5.3F, 3.6F + lvl * 2.6F, 1F);
        }
        float k = Mth.clamp(c.since / 14F, 0, 1);
        float ease = 1 - (1 - k) * (1 - k);
        float spin = (c.tile.strokes() - 1 + ease) * 90F;
        KDraw d = new KDraw(c.pose, c.buffers, c.light, c.overlay);
        KDraw.Tex steel = tex("steel"), wood = tex("wood_light"), iron = tex("iron");
        ItemStack frames = c.items().get(0);
        KDraw.Tex fill = frames.isEmpty() ? wood : KDraw.of(Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS)
              .apply(Pioneers.rl("block/farm/device/comb")));
        d.push().rotate(Direction.Axis.Y, spin * 2F, 8, 0, 8);
        d.box(7.6F, 3.6F, 7.6F, 8.4F, 13.4F, 8.4F, steel, steel);
        for (int i = 0; i < 2; i++) {
            d.push().rotate(Direction.Axis.Y, i * 180F, 8, 0, 8);
            d.box(8.8F, 5.2F, 7.6F, 12.6F, 5.6F, 8.4F, iron, iron);
            d.box(8.8F, 11.4F, 7.6F, 12.6F, 11.8F, 8.4F, iron, iron);
            if (!frames.isEmpty() || c.tile.strokes() > 0) {
                d.box(9.0F, 5.6F, 7.75F, 12.4F, 11.4F, 8.25F, fill, wood);
            }
            d.pop();
        }
        d.pop();
        d.push().rotate(Direction.Axis.X, spin * 4F, 0, 14.3F, 8F);
        d.box(11.2F, 14.0F, 7.7F, 11.8F, 14.6F, 11.0F, iron, iron);
        d.box(11.8F, 13.9F, 10.4F, 13.4F, 14.7F, 11.2F, tex("wood_dark"), tex("wood_dark"));
        d.pop();
    }

    private static void sealer(Ctx c) {
        ItemStack can = c.items().get(0), dish = c.items().get(1), out = c.items().get(2);
        float stroke = pulse(c.since, 10);
        //Boite sur le plateau, plat en attente au-dessus, conserves finies a gauche
        //1.6 : poses sur le plateau (dessus y 2,2) et sur le socle (dessus y 1,5), plus a moitie dedans
        item(c, can, 8, onTop(2.2F, 0.42F, can), 7.6F, 0.42F, 0, true, seed(c.tile, 0));
        item(c, dish, 4.2F, onTop(1.5F, 0.32F, dish), 11.2F, 0.32F, 25, true, seed(c.tile, 1));
        for (int i = 0; i < Math.min(3, out.getCount()); i++) {
            item(c, out, 3.8F, onTop(1.5F, 0.36F, out) + i * 0.55F, 4.4F, 0.36F, -20 + i * 8, true, seed(c.tile, 2 + i));
        }
        KDraw d = new KDraw(c.pose, c.buffers, c.light, c.overlay);
        KDraw.Tex steel = tex("steel"), iron = tex("iron"), wood = tex("wood_dark");
        //Le mandrin descend pendant le geste
        float drop = stroke * 2.6F;
        d.box(7.4F, 4.6F - drop, 7.0F, 8.6F, 7.6F, 8.2F, steel, steel);
        //Levier : repos a -35 deg, abaisse de 70 deg pendant le geste
        float ang = -35F + 70F * stroke;
        d.push().rotate(Direction.Axis.X, ang, 10.5F, 10F, 10F);
        d.box(10.9F, 9.5F, 3.0F, 11.7F, 10.5F, 10.2F, iron, iron);
        d.box(10.7F, 9.3F, 1.6F, 11.9F, 10.7F, 3.4F, wood, wood);
        d.pop();
    }
}
