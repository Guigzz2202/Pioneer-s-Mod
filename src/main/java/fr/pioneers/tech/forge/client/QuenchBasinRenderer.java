package fr.pioneers.tech.forge.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.automation.QuenchBasinTile;
import fr.pioneers.tech.forge.smithing.QuenchBasinBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * 1.1.1 : bac de trempe : la piece qui trempe est couchee au fond de l'eau (lumineuse tant qu'elle est chaude, elle s'eteint au fil de la
 * trempe), la piece finie attend a cote, a reprendre main vide.
 */
public class QuenchBasinRenderer implements BlockEntityRenderer<QuenchBasinTile> {

    private static final int VIEW = 32;

    public QuenchBasinRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public boolean shouldRender(@NotNull QuenchBasinTile tile, @NotNull Vec3 camera) {
        return Vec3.atCenterOf(tile.getBlockPos()).closerThan(camera, VIEW);
    }

    @Override
    public void render(@NotNull QuenchBasinTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        ItemStack in = tile.inputPiece(), out = tile.outputPiece();
        if (in.isEmpty() && out.isEmpty()) {
            return;
        }
        if (!in.isEmpty()) {
            //incandescence : temperature de la piece, eteinte progressivement pendant la trempe
            float temp = HeatedMetal.temperatureNow(in, tile.getLevel());
            float left = 1F - Math.min(1F, (tile.progress() + partial) / QuenchBasinTile.QUENCH_TICKS);
            int glow = (int) ((ForgeMetals.glow(temp) >>> 24) * left);
            int lit = LightTexture.pack(Math.max(glow * 15 / 255, LightTexture.block(light)), LightTexture.sky(light));
            draw(in, out.isEmpty() ? 8F : 6F, 1.6F, 25F, pose, buffers, lit, tile);
        }
        if (!out.isEmpty()) {
            draw(out, in.isEmpty() ? 8F : 10.5F, 1.4F, -20F, pose, buffers, light, tile);
        }
    }

    /** Piece couchee a plat sur le fond du bac (y en px), decalee en x. */
    private static void draw(ItemStack stack, float x, float y, float yaw, PoseStack pose, MultiBufferSource buffers, int light, QuenchBasinTile tile) {
        pose.pushPose();
        pose.translate(0.5F, y / 16F, 0.5F);
        if (tile.getBlockState().hasProperty(QuenchBasinBlock.FACING)) {
            pose.mulPose(Axis.YP.rotationDegrees(-tile.getBlockState().getValue(QuenchBasinBlock.FACING).toYRot()));
        }
        pose.translate((x - 8F) / 16F, 0F, 0F);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(90F));
        pose.scale(0.5F, 0.5F, 0.5F);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, OverlayTexture.NO_OVERLAY, pose, buffers,
              tile.getLevel(), 0);
        pose.popPose();
    }
}
