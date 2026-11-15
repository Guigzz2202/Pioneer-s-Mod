package fr.pioneers.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.forge.ForgeBlock;
import fr.pioneers.forge.casting.AnvilMoldBlock;
import fr.pioneers.forge.casting.AnvilMoldTile;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 2) : moule d'enclume pose : le metal monte dans l'empreinte ({@link AnvilMoldBlock.Cavity#BOXES}) coulee apres coulee, blanc-jaune
 * a la coulee, rouge sombre en refroidissant, puis couleur du metal (metal partiel fige entre deux coulees, enclume prete a la fin). Meme
 * teinte que le moule pose ({@link PlacedMoldRenderer}).
 */
public class AnvilMoldRenderer implements BlockEntityRenderer<AnvilMoldTile> {

    public AnvilMoldRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull AnvilMoldTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        int mb = tile.amount();
        String metal = tile.metal();
        if (mb <= 0 || metal.isEmpty()) {
            return;
        }
        BlockState state = tile.getBlockState();
        Direction facing = state.hasProperty(ForgeBlock.FACING) ? state.getValue(ForgeBlock.FACING) : Direction.SOUTH;
        int capacity = tile.capacity();
        float fill = tile.done() || capacity <= 0 ? 1F : Math.min(1F, mb / (float) capacity);
        float solid = tile.solid(partial);
        int lit = solid < 0.6F ? LightTexture.pack(Math.max(Math.round(15 * (1 - solid / 0.6F * 0.7F)), LightTexture.block(light)), LightTexture.sky(light)) : light;
        draw(new KDraw(pose, buffers, lit, overlay), facing, fill, solid, metal);
    }

    /**
     * Le metal dans l'empreinte (repere du bloc, en px) : {@code fill} 0..1 de la hauteur de l'empreinte, {@code solid} 0 (coulee) .. 1
     * (fige). Appele aussi par le banc hors jeu (tools/kinetics, scene « anvilmold »).
     */
    public static void draw(KDraw d, Direction facing, float fill, float solid, String metal) {
        float top = AnvilMoldBlock.FLOOR + (AnvilMoldBlock.TOP - AnvilMoldBlock.FLOOR) * fill;
        int colour;
        String tex;
        if (solid < 0.6F) {
            float t = solid / 0.6F;
            colour = lerp(255, 200, t) << 16 | lerp(236, 70, t) << 8 | lerp(150, 16, t);
            tex = "forge_molten";
        } else {
            int c = Metals.colour(metal);
            float t = Math.min(1F, (solid - 0.6F) / 0.4F);
            int r = Math.min(255, (c >> 16 & 255) * 3 / 2), g = Math.min(255, (c >> 8 & 255) * 3 / 2), b = Math.min(255, (c & 255) * 3 / 2);
            colour = lerp(150, r, t) << 16 | lerp(40, g, t) << 8 | lerp(16, b, t);
            tex = "steel";
        }
        d.push().face(facing);
        d.tint(0xFF000000 | colour);
        for (float[] c : AnvilMoldBlock.Cavity.BOXES) {
            d.box(c[0] + 0.02F, AnvilMoldBlock.FLOOR, c[1] + 0.02F, c[2] - 0.02F, top, c[3] - 0.02F, tex, tex);
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
