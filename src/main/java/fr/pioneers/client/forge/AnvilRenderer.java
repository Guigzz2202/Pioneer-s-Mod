package fr.pioneers.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.forge.HeatedMetal;
import fr.pioneers.forge.SmithingGrid;
import fr.pioneers.forge.smithing.AnvilBlock;
import fr.pioneers.forge.smithing.AnvilGeometry;
import fr.pioneers.forge.smithing.AnvilTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Piece posee sur l'enclume : les pixels de la grille en relief ({@link AnvilGeometry}), a la couleur du metal melee a son incandescence, et
 * lumineux quand ils rougeoient ; une piece finie est posee a plat. Rangees fusionnees en bandes (au plus ~40 boites), aucune allocation.
 */
public class AnvilRenderer implements BlockEntityRenderer<AnvilTile> {

    private static final int VIEW = 48;

    public AnvilRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(@NotNull AnvilTile tile, @NotNull Vec3 camera) {
        return Vec3.atCenterOf(tile.getBlockPos()).closerThan(camera, VIEW);
    }

    @Override
    public void render(@NotNull AnvilTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        ItemStack piece = tile.piece();
        if (piece.isEmpty()) {
            return;
        }
        Direction facing = tile.getBlockState().hasProperty(AnvilBlock.FACING) ? tile.getBlockState().getValue(AnvilBlock.FACING) : Direction.SOUTH;
        SmithingGrid grid = tile.shownGrid();
        float temp = HeatedMetal.temperatureNow(piece, tile.getLevel());
        int glow = ForgeMetals.glow(temp) >>> 24;
        int lit = LightTexture.pack(Math.max(glow * 15 / 255, LightTexture.block(light)), LightTexture.sky(light));
        if (grid == null) {
            //Piece finie : posee a plat sur la table
            pose.pushPose();
            pose.translate(0.5, AnvilGeometry.TOP / 16.0 + 0.02, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(90));
            pose.scale(0.55F, 0.55F, 0.55F);
            //1.0.5-beta (lot 15) : la piece chaude elle-meme (HeatedMetalItemRenderer : vraie piece, teintee par sa chaleur), plus la piece froide
            ItemStack shown = HeatedMetal.is(piece) && !HeatedMetal.shape(piece).isEmpty() ? piece : tile.shownShape();
            Minecraft.getInstance().getItemRenderer().renderStatic(shown, ItemDisplayContext.FIXED, lit, overlay, pose, buffers,
                  tile.getLevel(), 0);
            pose.popPose();
            return;
        }
        int rgb = ForgeMetals.tint(HeatedMetal.metal(piece), temp);
        KDraw d = new KDraw(pose, buffers, lit, overlay);
        d.push().face(facing);
        d.tint(0xFF000000 | rgb);
        float c = AnvilGeometry.CELL, y0 = AnvilGeometry.TOP, y1 = AnvilGeometry.TOP + AnvilGeometry.RELIEF;
        for (int z = 0; z < SmithingGrid.SIZE; z++) {
            int x = 0;
            while (x < SmithingGrid.SIZE) {
                if (!grid.get(x, z)) {
                    x++;
                    continue;
                }
                int x1 = x;
                while (x1 < SmithingGrid.SIZE && grid.get(x1, z)) {
                    x1++;
                }
                d.box(AnvilGeometry.X0 + x * c, y0, AnvilGeometry.Z0 + z * c, AnvilGeometry.X0 + x1 * c, y1, AnvilGeometry.Z0 + (z + 1) * c, "forge_work",
                      "forge_work");
                x = x1;
            }
        }
        d.tint(0xFFFFFFFF);
        d.pop();
    }
}
