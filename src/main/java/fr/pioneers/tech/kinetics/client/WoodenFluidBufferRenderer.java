package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.mechanics.WoodenFluidBufferTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.client.extensions.common.IClientFluidTypeExtensions;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 2) : buffer de fluide en bois : le liquide dans les quatre fenetres de jauge (une par face, entre les cercles de fer :
 * meme geometrie que {@code T:core/p102_lot2.py WINDOW}), a la hauteur du remplissage, texture et teinte du liquide.
 */
public class WoodenFluidBufferRenderer implements BlockEntityRenderer<WoodenFluidBufferTile> {

    /** Fenetre (px) : de y0 a y1 au plus, largeur centree w, profondeur d (depuis la face exterieure des douelles a 1 px du bord). */
    public static final float Y0 = 3.5F, Y1 = 12.5F, HALF = 1.5F, OUT = 1.35F, IN = 2.4F;

    public WoodenFluidBufferRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull WoodenFluidBufferTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        FluidStack fluid = tile.tank().getFluid();
        if (fluid.isEmpty()) {
            return;
        }
        IClientFluidTypeExtensions ext = IClientFluidTypeExtensions.of(fluid.getFluid());
        TextureAtlasSprite sprite = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(ext.getStillTexture(fluid));
        KDraw.Tex tex = KDraw.of(sprite);
        draw(new KDraw(pose, buffers, light, overlay), tile.fill(), tex, ext.getTintColor(fluid));
    }

    /** Le liquide dans les quatre fenetres ({@code fill} 0..1) ; appele aussi par le banc hors jeu (tools/kinetics, scene « woodbuffer »). */
    public static void draw(KDraw d, float fill, KDraw.Tex tex, int tint) {
        float top = Y0 + (Y1 - Y0) * Math.min(1, fill);
        d.tint(0xFF000000 | tint & 0xFFFFFF);
        d.box(8 - HALF, Y0, OUT, 8 + HALF, top, IN, tex, tex);
        d.box(8 - HALF, Y0, 16 - IN, 8 + HALF, top, 16 - OUT, tex, tex);
        d.box(OUT, Y0, 8 - HALF, IN, top, 8 + HALF, tex, tex);
        d.box(16 - IN, Y0, 8 - HALF, 16 - OUT, top, 8 + HALF, tex, tex);
        d.tint(0xFFFFFFFF);
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
