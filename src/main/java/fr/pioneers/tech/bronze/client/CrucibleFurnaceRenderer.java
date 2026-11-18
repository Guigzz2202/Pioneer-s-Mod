package fr.pioneers.tech.bronze.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.kinetics.client.KDraw;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.jetbrains.annotations.NotNull;

/** Fourneau a creuset : la surface du metal dans le creuset (incandescente quand il coule, de sa couleur quand il est fige). */
public class CrucibleFurnaceRenderer implements BlockEntityRenderer<CrucibleFurnaceTile> {

    public CrucibleFurnaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull CrucibleFurnaceTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        int total = tile.total();
        if (total <= 0) {
            return;
        }
        boolean hot = tile.molten();
        float level = 11.8F + 3.4F * Math.min(1, total / (float) CrucibleFurnaceTile.CAPACITY);
        KDraw d = new KDraw(pose, buffers, hot ? LightTexture.FULL_BRIGHT : light, overlay);
        if (hot) {
            d.tint(0xFFFFD27A);
            d.box(5.0F, level - 0.2F, 5.0F, 11.0F, level, 11.0F, "molten", "molten");
        } else {
            String result = tile.result();
            int c = result.isEmpty() ? 0x7A6A5A : Metals.colour(result);
            int r = Math.min(255, (c >> 16 & 255) * 3 / 2), g = Math.min(255, (c >> 8 & 255) * 3 / 2), b = Math.min(255, (c & 255) * 3 / 2);
            d.tint(0xFF000000 | r << 16 | g << 8 | b);
            d.box(5.0F, level - 0.2F, 5.0F, 11.0F, level, 11.0F, "steel", "steel");
        }
        d.tint(0xFFFFFFFF);
    }
}
