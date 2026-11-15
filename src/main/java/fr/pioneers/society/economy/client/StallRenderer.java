package fr.pioneers.society.economy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.society.economy.stall.StallBlock;
import fr.pioneers.society.economy.stall.StallTile;
import java.util.List;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : rendu d'un etal ou d'un comptoir (modele dessine facade au NORD, tourne par {@code FACING}) : les objets des
 * annonces actives poses en miniature sur le plateau (3 sur l'etal a y 13 px, 6 sur le comptoir a y 14 px ; objets plats couches, blocs
 * debout) et le nom du pionnier proprietaire ecrit petit sur la planchette de la facade (x 3..13 ; y 3..7 etal, 4..8 comptoir ; z 0,5).
 * Positions fixees avec le modele (contrat de rendu des visuels).
 */
public class StallRenderer implements BlockEntityRenderer<StallTile> {

    /** Centres des objets (px) : etal puis comptoir. */
    private static final float[][] STALL = {{4, 13, 6}, {8, 13, 6}, {12, 13, 6}};
    private static final float[][] COUNTER = {{3.5F, 14, 4}, {8, 14, 4}, {12.5F, 14, 4}, {3.5F, 14, 8.5F}, {8, 14, 8.5F}, {12.5F, 14, 8.5F}};
    /** Taille d'un objet pose (px). */
    private static final float STALL_SIZE = 3.6F, COUNTER_SIZE = 4.0F;
    /** Planchette du nom (px) : bords gauche et droit, haut et bas (etal / comptoir), plan de la facade. */
    private static final float PLATE_X0 = 3, PLATE_X1 = 13, PLATE_Z = 0.5F;
    private static final float[] PLATE_Y0 = {3, 4}, PLATE_Y1 = {7, 8};
    private static final int INK = 0x2A1B0E;

    private final Font font;
    private final ItemRenderer items;

    public StallRenderer(BlockEntityRendererProvider.Context ctx) {
        font = ctx.getFont();
        items = ctx.getItemRenderer();
    }

    @Override
    public void render(@NotNull StallTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        BlockState state = tile.getBlockState();
        Direction facing = state.hasProperty(StallBlock.FACING) ? state.getValue(StallBlock.FACING) : Direction.NORTH;
        boolean counter = tile.counter();
        pose.pushPose();
        //repere du modele : facade au nord, tourne comme les variantes de l'etat (y 90 = est)
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180 - facing.toYRot()));
        pose.translate(-0.5, 0, -0.5);
        List<ItemStack> shown = tile.shown();
        float[][] at = counter ? COUNTER : STALL;
        float size = counter ? COUNTER_SIZE : STALL_SIZE;
        for (int i = 0; i < shown.size() && i < at.length; i++) {
            drawItem(tile, shown.get(i), at[i], size, pose, buffers, light);
        }
        drawName(tile.ownerName(), counter ? 1 : 0, pose, buffers, light);
        pose.popPose();
    }

    private void drawItem(StallTile tile, ItemStack stack, float[] at, float size, PoseStack pose, MultiBufferSource buffers, int light) {
        if (stack.isEmpty()) {
            return;
        }
        BakedModel model = items.getModel(stack, tile.getLevel(), null, 0);
        pose.pushPose();
        pose.translate(at[0] / 16F, at[1] / 16F, at[2] / 16F);
        if (model.isGui3d()) {
            //bloc : debout sur le plateau (l'affichage « fixe » d'un bloc fait 8 px)
            pose.translate(0, size / 32F + 0.002F, 0);
            float k = size / 8F;
            pose.scale(k, k, k);
        } else {
            //objet plat : couche face en haut, le haut de l'icone vers le fond de l'etal (lisible par le client)
            //leve de 0,2 px : la face du dessous de l'objet (epaisseur 1/16 a l'echelle) ne traverse pas le plateau
            pose.translate(0, 0.012F, 0);
            pose.mulPose(Axis.YP.rotationDegrees(180));
            pose.mulPose(Axis.XP.rotationDegrees(-90));
            float k = size / 16F;
            pose.scale(k, k, k);
        }
        items.render(stack, ItemDisplayContext.FIXED, false, pose, buffers, light, OverlayTexture.NO_OVERLAY, model);
        pose.popPose();
    }

    /** Nom ecrit sur la planchette, tourne vers le client (au nord), reduit pour tenir dans la planchette. */
    private void drawName(String name, int kind, PoseStack pose, MultiBufferSource buffers, int light) {
        if (name == null || name.isBlank()) {
            return;
        }
        FormattedCharSequence text = Component.literal(name).getVisualOrderText();
        int w = Math.max(1, font.width(text));
        float maxW = (PLATE_X1 - PLATE_X0 - 1.0F) / 16F, maxH = (PLATE_Y1[kind] - PLATE_Y0[kind] - 1.2F) / 16F;
        float k = Math.min(maxW / w, maxH / 8F);
        pose.pushPose();
        pose.translate((PLATE_X0 + PLATE_X1) / 32F, (PLATE_Y0[kind] + PLATE_Y1[kind]) / 32F, (PLATE_Z - 0.03F) / 16F);
        //texte : x vers la droite du lecteur (ouest du modele), y vers le bas, face tournee vers le nord
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(k, -k, k);
        font.drawInBatch(text, -w / 2F, -4F, INK, false, pose.last().pose(), buffers, Font.DisplayMode.NORMAL, 0, light);
        pose.popPose();
    }
}
