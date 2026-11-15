package fr.pioneers.farm.client.dish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.farm.food.DishShelfTile;
import fr.pioneers.farm.food.ShelfJoints;
import fr.pioneers.farm.food.WallShelfBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.8 : etagere a plats ({@link DishShelfBlock}) : les quatre plats ou boissons en ligne sur la planche, dessines en volume comme les plats
 * servis ({@link PlacedDishRenderer#drawServing}), chacun reduit a sa place (4 px), un peu tourne pour ne pas paraitre aligne au cordeau.
 * 1.0.3-beta (lot 12) : TOUTES les planches ({@link DishShelfBlock#boards} : planche basse et, au sommet d'une pile au sol, planche haute ;
 * une seule pour la murale {@link WallShelfBlock}), quatre places chacune.
 */
public class DishShelfRenderer implements BlockEntityRenderer<DishShelfTile> {

    /** Rayon d'une place (px) : quatre places sur une planche de 16 px. */
    private static final float FIT = 1.95F;
    private static final float[] JITTER = {4, -6, 3, -3, -5, 2, -2, 5};
    /** Echelle maximale : boissons et bocaux a la moitie de leur taille, pour rester a l'echelle des plats (reduits a 4 px). */
    private static final float MAX_SCALE = 0.5F;

    public DishShelfRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public int getViewDistance() {
        return 40;
    }

    @Override
    public void render(DishShelfTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        BlockState state = tile.getBlockState();
        if (!(state.getBlock() instanceof DishShelfBlock) && !(state.getBlock() instanceof WallShelfBlock)) {
            return;
        }
        Direction facing = state.getValue(DishShelfBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-switch (facing) {
            case EAST -> 90;
            case SOUTH -> 180;
            case WEST -> 270;
            default -> 0;
        }));
        pose.translate(-0.5, 0, -0.5);
        for (ShelfJoints.Board board : DishShelfBlock.boards(state)) {
            float top = board.top(), mid = board.mid();
            for (int i = 0; i < DishShelfBlock.SLOTS; i++) {
                ItemStack stack = tile.get(board.first() + i);
                if (stack.isEmpty()) {
                    continue;
                }
                pose.pushPose();
                pose.translate((2 + 4 * i) / 16F - 0.5, (top + 0.02F) / 16F, mid / 16F - 0.5);
                pose.translate(0.5, 0, 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(JITTER[(i + board.first()) % JITTER.length]));
                pose.translate(-0.5, 0, -0.5);
                PlacedDishRenderer.drawServing(stack, pose, buffers, light, overlay, FIT, MAX_SCALE, tile.getLevel(),
                      (int) tile.getBlockPos().asLong() + board.first() + i);
                pose.popPose();
            }
        }
        pose.popPose();
    }
}
