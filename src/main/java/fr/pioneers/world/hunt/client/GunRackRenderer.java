package fr.pioneers.world.hunt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.world.hunt.GunRackBlock;
import fr.pioneers.world.hunt.GunRackTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 10) : rendu du PORTE-FUSIL garni : chaque arme couchee sur ses taquets, rang par rang ({@link GunRackBlock#ROWS}), a plat
 * contre la planche, canon a droite vu de face ; vue « fixed » du modele de l'arme (la meme que dans un cadre). {@link #place} est le seul
 * calcul de position (le banc de la planche le reprend).
 */
public class GunRackRenderer implements BlockEntityRenderer<GunRackTile> {

    /** Echelle d'une arme sur le porte-fusil, distance au mur (px) du plan de l'arme. */
    public static final float SCALE = 0.62F, FROM_WALL = 3.2F;
    private final ItemRenderer items;

    public GunRackRenderer(BlockEntityRendererProvider.Context ctx) {
        this.items = ctx.getItemRenderer();
    }

    /** Pose de l'arme du rang {@code row} : repere du bloc tourne vers {@code facing} (centre du bloc), puis rang et echelle. */
    public static void place(PoseStack pose, Direction facing, int row) {
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        pose.translate(0, GunRackBlock.ROWS[row] / 16.0, 0.5 - FROM_WALL / 16.0);
        pose.scale(SCALE, SCALE, SCALE);
    }

    @Override
    public void render(@NotNull GunRackTile tile, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Direction facing = tile.getBlockState().getValue(GunRackBlock.FACING);
        for (int i = 0; i < GunRackTile.SLOTS; i++) {
            ItemStack s = tile.get(i);
            if (s.isEmpty()) {
                continue;
            }
            pose.pushPose();
            place(pose, facing, i);
            items.renderStatic(s, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), (int) tile.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }
}
