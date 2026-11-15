package fr.pioneers.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.forge.ForgeBlock;
import fr.pioneers.forge.casting.PlacedMoldBlock;
import fr.pioneers.forge.casting.PlacedMoldTile;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * Moule pose : le metal dans l'empreinte ({@link PlacedMoldBlock.Shape#cavities}), blanc-jaune incandescent a la coulee, qui vire au rouge
 * sombre en refroidissant puis prend la couleur du metal (piece prete).
 */
public class PlacedMoldRenderer implements BlockEntityRenderer<PlacedMoldTile> {

    public PlacedMoldRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull PlacedMoldTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        int mb = tile.amount();
        String metal = tile.metal();
        if (mb <= 0 || metal.isEmpty()) {
            return;
        }
        BlockState state = tile.getBlockState();
        if (!state.hasProperty(PlacedMoldBlock.SHAPE)) {
            return;
        }
        PlacedMoldBlock.Shape shape = state.getValue(PlacedMoldBlock.SHAPE);
        Direction facing = state.getValue(ForgeBlock.FACING);
        int capacity = tile.capacity();
        float fill = tile.done() || capacity <= 0 ? 1F : Math.min(1F, mb / (float) capacity);
        float top = shape.floor + (PlacedMoldBlock.TOP - shape.floor - 0.15F) * fill;
        float solid = tile.solid(partial);
        int colour;
        int lit;
        String tex;
        if (solid < 0.6F) {
            float t = solid / 0.6F;
            colour = lerp(255, 200, t) << 16 | lerp(236, 70, t) << 8 | lerp(150, 16, t);
            int glow = Math.round(15 * (1 - t * 0.7F));
            lit = LightTexture.pack(Math.max(glow, LightTexture.block(light)), LightTexture.sky(light));
            tex = "forge_molten";
        } else {
            int c = metal.isEmpty() ? 0x7A6A5A : Metals.colour(metal);
            float t = Math.min(1F, (solid - 0.6F) / 0.4F);
            int r = Math.min(255, (c >> 16 & 255) * 3 / 2), g = Math.min(255, (c >> 8 & 255) * 3 / 2), b = Math.min(255, (c & 255) * 3 / 2);
            colour = lerp(150, r, t) << 16 | lerp(40, g, t) << 8 | lerp(16, b, t);
            lit = light;
            tex = "steel";
        }
        KDraw d = new KDraw(pose, buffers, lit, overlay);
        d.push().face(facing);
        d.tint(0xFF000000 | colour);
        for (float[] c : shape.cavities) {
            d.box(c[0] + 0.02F, shape.floor, c[1] + 0.02F, c[2] - 0.02F, top, c[3] - 0.02F, tex, tex);
        }
        d.tint(0xFFFFFFFF);
        d.pop();
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
