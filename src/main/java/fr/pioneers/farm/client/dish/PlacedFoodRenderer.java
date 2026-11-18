package fr.pioneers.farm.client.dish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.food.PlacedFoodBlock;
import fr.pioneers.farm.food.PlacedFoodTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.9.2 (retours de Guillaume : « Tous les plats et ingredients doivent etre plaçables dans le monde ») : rendu des tas
 * ({@link PlacedFoodBlock}). Une entree « @ns:id » de {@link PlacedFoodMeshes} (ecrite par {@code tools/gc/farm2/placed_items.py}) :
 * le maillage EN VOLUME d'une unite (le modele de son icone) pose a chacune des places de la disposition pour n unites (tas en pyramide,
 * rangee couchee, pile, grille, panier...), le contenant commun dessine une fois (panier d'osier, nid de paille), ou, pour un sac, un pot,
 * un bol, un seul maillage dont le contenu baisse. Repere : pixels, cote du joueur au nord, tourne selon {@code facing}. Objet sans
 * maillage : l'objet couche, comme les plats inconnus.
 */
public class PlacedFoodRenderer implements BlockEntityRenderer<PlacedFoodTile> {

    /** Au-dela (blocs), seules les grosses boites sont dessinees. */
    private static final double LOD = 14;

    public PlacedFoodRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 40;
    }

    @Nullable
    static PlacedFoodMeshes.Food pile(ItemStack stack) {
        return stack.isEmpty() ? null : PlacedFoodMeshes.food("@" + BuiltInRegistries.ITEM.getKey(stack.getItem()));
    }

    @Override
    public void render(PlacedFoodTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack stack = tile.pile();
        if (stack.isEmpty()) {
            return;
        }
        Direction facing = tile.getBlockState().hasProperty(PlacedFoodBlock.FACING) ? tile.getBlockState().getValue(PlacedFoodBlock.FACING)
                                                                                   : Direction.NORTH;
        PlacedFoodMeshes.Food food = pile(stack);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-yRot(facing)));
        pose.translate(-0.5, 0, -0.5);
        if (food == null || food.lay() == null) {
            fallback(stack, tile.count(), pose, buffers, light, overlay, (int) tile.getBlockPos().asLong());
        } else {
            Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
            boolean far = cam.distanceToSqr(tile.getBlockPos().getCenter()) > LOD * LOD;
            draw(stack, food, tile.count(), pose, buffers.getBuffer(Sheets.translucentCullBlockSheet()), light, far);
        }
        pose.popPose();
    }

    /** Le tas de {@code count} unites, dans le repere du bloc (px / 16). */
    private static void draw(ItemStack stack, PlacedFoodMeshes.Food food, int count, PoseStack pose, VertexConsumer vc, int light, boolean far) {
        String key = "@" + BuiltInRegistries.ITEM.getKey(stack.getItem());
        int dyn = PlacedDishRenderer.contentColour(stack);
        int n = Math.max(1, Math.min(food.lay().length, count));
        if (food.vessel() != null) {
            PlacedFoodMeshes.Food v = PlacedFoodMeshes.food(food.vessel());
            if (v != null) {
                PlacedFoodMeshes.Mesh vm = PlacedFoodMeshes.mesh(food.vessel(), v, 0);
                PlacedDishRenderer.emit(vc, pose, vm, far ? vm.lod() : vm.quads(), dyn, light);
            }
        }
        if (food.fill()) {
            //Sac, pot, bol : un seul maillage, le contenu baisse (etape = capacite - nombre)
            PlacedFoodMeshes.Mesh m = PlacedFoodMeshes.mesh(key, food, food.bites() - n);
            PlacedDishRenderer.emit(vc, pose, m, far ? m.lod() : m.quads(), dyn, light);
            return;
        }
        PlacedFoodMeshes.Mesh m = PlacedFoodMeshes.mesh(key, food, 0);
        float[] at = food.lay()[n - 1];
        for (int u = 0; u + 3 < at.length; u += 4) {
            pose.pushPose();
            pose.translate((8 + at[u]) / 16F, at[u + 1] / 16F, (8 + at[u + 2]) / 16F);
            if (at[u + 3] != 0) {
                pose.mulPose(Axis.YP.rotationDegrees(at[u + 3]));
            }
            pose.translate(-0.5, 0, -0.5);
            PlacedDishRenderer.emit(vc, pose, m, far ? m.lod() : m.quads(), dyn, light);
            pose.popPose();
        }
    }

    /**
     * Etageres (1.8, {@code DishShelfRenderer}) : une unite de cet objet centree dans le bloc courant, reduite pour tenir dans un rayon
     * {@code fit} (px), au plus a l'echelle {@code maxScale} ; sac, pot, bol : a moitie pleins. Faux si l'objet ne se pose pas en tas.
     */
    public static boolean drawOne(ItemStack stack, PoseStack pose, MultiBufferSource buffers, int light, float fit, float maxScale) {
        PlacedFoodMeshes.Food food = pile(stack);
        if (food == null || food.lay() == null) {
            return false;
        }
        float r = Math.max(0.5F, food.vessel() != null ? 6.5F : food.radius());
        float k = Math.min(maxScale * 1.4F, fit / r);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.scale(k, k, k);
        pose.translate(-0.5, 0, -0.5);
        int n = food.fill() ? Math.max(1, food.lay().length / 2) : 1;
        draw(stack, food, n, pose, buffers.getBuffer(Sheets.translucentCullBlockSheet()), light, false);
        pose.popPose();
        return true;
    }

    private static float yRot(Direction facing) {
        return switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    /** Objet sans maillage (table et maillages desaccordes) : l'objet couche, une a quatre fois. */
    private static void fallback(ItemStack stack, int count, PoseStack pose, MultiBufferSource buffers, int light, int overlay, int seed) {
        int n = Math.min(4, count);
        float scale = n == 1 ? 0.5F : 0.34F;
        ItemStack one = stack.copyWithCount(1);
        for (int i = 0; i < n; i++) {
            float sx = n == 1 ? 0 : (i % 2 == 0 ? -0.15F : 0.15F), sz = n <= 2 ? 0 : (i < 2 ? -0.15F : 0.15F);
            pose.pushPose();
            pose.translate(0.5 + sx, 0.03 + i * 0.004, 0.5 + sz);
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(scale, scale, scale);
            Minecraft.getInstance().getItemRenderer().renderStatic(one, ItemDisplayContext.FIXED, light, overlay, pose, buffers, null, seed + i);
            pose.popPose();
        }
    }

    /** Infobulle : comment poser l'objet en tas. */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class TooltipEvents {

        private TooltipEvents() {
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            if (PlacedFoodBlock.placeable(event.getItemStack())) {
                Tooltips.event(event.getToolTip()).hint("farm.pioneers.food.place_pile", PlacedFoodBlock.capacity(event.getItemStack()));
            }
        }
    }
}
