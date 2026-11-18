package fr.pioneers.farm.client.dish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.client.FarmClient;
import fr.pioneers.farm.client.StationRenderer;
import fr.pioneers.farm.food.CannedFoodItem;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.farm.food.FarmFoodItem;
import fr.pioneers.farm.food.PlacedDishBlock;
import fr.pioneers.farm.food.PlacedDishTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Plats et boissons servis ({@link PlacedDishBlock}). 1.6 (lot A1) : chaque portion est dessinee EN VOLUME d'apres
 * {@link PlacedFoodMeshes} (le modele de son icone pose sur l'assiette ou la planche du bloc, ou sa bouteille, sa tasse, sa chope, son bol, son
 * bocal), la portion en cours diminue a chaque bouchee ou gorgee ; 1 a 4 portions disposees sur le support, a l'echelle, un peu tournees l'une
 * par rapport a l'autre. Repere : pixels, cote du joueur au nord, tourne selon {@code facing}. Plat inconnu des maillages : l'objet couche,
 * comme avant.
 */
public class PlacedDishRenderer implements BlockEntityRenderer<PlacedDishTile> {

    /** Dispositions de 1 a 4 portions (px depuis le centre) et echelles (recopiees de placed_dish.LAYOUT / SCALE). */
    private static final float[][] LAYOUT = {{0, 0}, {-3.6F, 0, 3.6F, 0}, {0, -3.2F, -2.77F, 1.6F, 2.77F, 1.6F},
                                             {-2.9F, -2.9F, 2.9F, -2.9F, -2.9F, 2.9F, 2.9F, 2.9F}};
    /** Demi-distance entre deux portions voisines (pas de chevauchement) et eloignement maximal du centre. */
    private static final float[] HALF = {8F, 3.6F, 2.77F, 2.9F}, OFF = {0F, 3.6F, 3.2F, 4.1F};
    /** Rayon utile du support : assiette, planche, bloc (les portions restent dessus). */
    private static final float[] EDGE = {6.3F, 5.6F, 7.8F};
    /** Petite rotation de chaque portion (degres) : naturel, et jamais deux faces de deux portions dans le meme plan. */
    private static final float[] JITTER = {0, 7, -5, 4};
    /** Dessus du support (px) : assiette, planche, rien. */
    private static final float[] BASE_TOP = {0.95F, 1.2F, 0F};
    /** Au-dela (blocs), seules les grosses boites sont dessinees. */
    private static final double LOD = 14;

    public PlacedDishRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 40;
    }

    @Override
    public void render(PlacedDishTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack dish = tile.dish();
        if (dish.isEmpty()) {
            return;
        }
        int n = Math.min(PlacedDishBlock.MAX, dish.getCount());
        Direction facing = tile.getBlockState().hasProperty(PlacedDishBlock.FACING) ? tile.getBlockState().getValue(PlacedDishBlock.FACING)
                                                                                   : Direction.NORTH;
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(dish.getItem());
        PlacedFoodMeshes.Food food = id.getNamespace().equals(Pioneers.MODID) ? PlacedFoodMeshes.food(id.getPath()) : null;
        if (food == null) {
            fallback(tile, dish, n, facing, pose, buffers, light, overlay);
            return;
        }
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        boolean far = cam.distanceToSqr(tile.getBlockPos().getCenter()) > LOD * LOD;
        int dyn = contentColour(dish);
        VertexConsumer vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        int b = Math.max(0, Math.min(2, food.base()));
        float base = BASE_TOP[b];
        float r = Math.max(0.5F, food.radius());
        float k = Math.min(1F, Math.min(HALF[n - 1] / r, (EDGE[b] - OFF[n - 1]) / r));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        for (int j = 0; j < n; j++) {
            //La derniere portion posee est celle qu'on mange
            int taken = j == n - 1 ? tile.bites() : 0;
            PlacedFoodMeshes.Mesh mesh = PlacedFoodMeshes.mesh(id.getPath(), food, taken);
            float dx = LAYOUT[n - 1][j * 2], dz = LAYOUT[n - 1][j * 2 + 1];
            pose.pushPose();
            pose.translate((8 + dx) / 16F, (base + j * 0.013F) / 16F, (8 + dz) / 16F);
            if (n > 1) {
                pose.mulPose(Axis.YP.rotationDegrees(JITTER[j]));
            }
            pose.scale(k, k, k);
            pose.translate(-0.5, -base / 16F, -0.5);
            emit(vc, pose, mesh, far ? mesh.lod() : mesh.quads(), dyn, light);
            pose.popPose();
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

    /** Couleur du contenu (bocaux : recolte d'origine ; conserve : plat ; sinon couleur du plat). */
    static int contentColour(ItemStack stack) {
        Item src = stack.getItem() instanceof CannedFoodItem ? CannedFoodItem.dish(stack) : FarmFoodItem.source(stack);
        int c = src == null ? -1 : FarmClient.colour(src);
        if (c == -1) {
            c = FarmClient.colour(stack.getItem());
        }
        return c == -1 ? 0xC8A060 : c;
    }

    static void emit(VertexConsumer vc, PoseStack pose, PlacedFoodMeshes.Mesh mesh, int quads, int dyn, int light) {
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        int[][] pal = PlacedFoodMeshes.palette();
        float[] v = mesh.verts(), nr = mesh.normals();
        int[] mats = mesh.mats();
        for (int q = 0; q < quads; q++) {
            int mat = mats[q];
            float r, g, b, a;
            if (mat < 0 || mat >= pal.length) {
                r = g = b = a = 1;
            } else {
                int[] p = pal[mat];
                a = p[3] / 255F;
                if (p[0] < 0) {
                    float kk = p[1] / 100F / 0.94F;
                    r = Math.min(1F, (dyn >> 16 & 255) / 255F * kk);
                    g = Math.min(1F, (dyn >> 8 & 255) / 255F * kk);
                    b = Math.min(1F, (dyn & 255) / 255F * kk);
                } else {
                    //Les grains sont un peu plus sombres que blanc (moyenne 0,94) : on compense
                    r = Math.min(1F, p[0] / 255F / 0.94F);
                    g = Math.min(1F, p[1] / 255F / 0.94F);
                    b = Math.min(1F, p[2] / 255F / 0.94F);
                }
            }
            float nx = nr[q * 3], ny = nr[q * 3 + 1], nz = nr[q * 3 + 2];
            int o = q * 20;
            for (int i = 0; i < 4; i++, o += 5) {
                vc.vertex(m, v[o], v[o + 1], v[o + 2]).color(r, g, b, a).uv(v[o + 3], v[o + 4]).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                      .normal(nm, nx, ny, nz).endVertex();
            }
        }
    }

    /**
     * 1.8 (etageres a plats) : une portion entiere, son support compris (assiette ou planche, a l'echelle), centree sur le milieu du bloc
     * courant et reduite pour tenir dans un rayon {@code fit} (px), au plus a l'echelle {@code maxScale}. Objet sans maillage : l'objet
     * debout, a l'echelle.
     */
    public static void drawServing(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, int overlay, float fit, float maxScale,
          @Nullable Level level, int seed) {
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        PlacedFoodMeshes.Food food = id.getNamespace().equals(Pioneers.MODID) ? PlacedFoodMeshes.food(id.getPath()) : null;
        if (food == null && PlacedFoodRenderer.drawOne(stack, pose, buffers, light, fit, maxScale)) {
            return;                                                            //1.9.2 : ingredient, recolte, vaisselle (une unite du tas)
        }
        if (food == null) {
            pose.pushPose();
            float s = fit / 8F;
            pose.translate(0.5, fit / 16F, 0.5);
            pose.scale(s, s, s);
            Minecraft.getInstance().getItemRenderer().renderStatic(stack.copyWithCount(1), ItemDisplayContext.FIXED, light, overlay, pose, buffers, level,
                  seed);
            pose.popPose();
            return;
        }
        int b = Math.max(0, Math.min(2, food.base()));
        float support = b == 0 ? 7F : b == 1 ? 7.7F : 0F;
        float k = Math.min(maxScale, fit / Math.max(0.5F, Math.max(support, food.radius())));
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.scale(k, k, k);
        pose.translate(-0.5, 0, -0.5);
        if (b != 2) {
            BlockState st = PlacedDishBlock.BLOCK.get().defaultBlockState().setValue(PlacedDishBlock.BASE, PlacedDishBlock.Base.values()[b]);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(st, pose, buffers, light, overlay, ModelData.EMPTY, null);
        }
        PlacedFoodMeshes.Mesh mesh = PlacedFoodMeshes.mesh(id.getPath(), food, 0);
        emit(buffers.getBuffer(Sheets.translucentCullBlockSheet()), pose, mesh, mesh.quads(), contentColour(stack), light);
        pose.popPose();
    }

    /** Ancien rendu (objet couche sur l'assiette) pour un plat sans maillage. */
    private static void fallback(PlacedDishTile tile, ItemStack dish, int n, Direction facing, PoseStack pose, MultiBufferSource buffers, int light,
          int overlay) {
        float yaw = facing.toYRot();
        float scale = n == 1 ? 0.5F : 0.34F;
        ItemStack one = dish.copyWithCount(1);
        for (int i = 0; i < n; i++) {
            float sx = n == 1 ? 0 : (i % 2 == 0 ? -0.15F : 0.15F), sz = n <= 2 ? 0 : (i < 2 ? -0.15F : 0.15F);
            pose.pushPose();
            pose.translate(0.5, 0.09 + i * 0.004, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-yaw));
            pose.translate(sx, 0, sz);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(one, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(),
                  (int) tile.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerBlockEntityRenderer(PlacedDishBlock.TILE.get(), PlacedDishRenderer::new);
            event.registerBlockEntityRenderer(DishShelfBlock.TILE.get(), DishShelfRenderer::new);          //1.8 : etageres a plats
            event.registerBlockEntityRenderer(fr.pioneers.farm.food.PlacedFoodBlock.TILE.get(), PlacedFoodRenderer::new);   //1.9.2 : tas
        }

        /** 1.6 (lot A1) : couvercle du bocal de cuisine (dessine ouvert ou ferme par StationRenderer). */
        @SubscribeEvent
        public static void models(ModelEvent.RegisterAdditional event) {
            event.register(StationRenderer.JAR_LID);
        }
    }
}
