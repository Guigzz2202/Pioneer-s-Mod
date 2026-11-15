package fr.pioneers.client.bronze;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.bronze.foundry.CastingTableTile;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.bronze.foundry.MoldItem;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.forge.casting.PlacedMoldBlock;
import fr.pioneers.registries.PiForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/** Table de coulee : le moule pose (1.2.0 : vrai modele, metal dans les empreintes), le metal en fusion qui rougeoie puis prend sa couleur en figeant, la piece finie posee dessus. */
public class CastingTableRenderer implements BlockEntityRenderer<CastingTableTile> {

    public CastingTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Dessus du plateau (px) : le moule pose dessus comme un moule au sol. */
    private static final float TABLE_TOP = 14F;

    @Override
    public void render(@NotNull CastingTableTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        ItemStack mold = tile.moldSlot().getStack(), out = tile.outputSlot().getStack();
        PlacedMoldBlock.Shape shape = mold.getItem() instanceof MoldItem m ? PlacedMoldBlock.Shape.of(m.shape) : null;
        if (shape != null) {
            //1.2.0 : le vrai moule (meme modele que pose au sol), et le metal SEULEMENT dans ses empreintes
            pose.pushPose();
            pose.translate(0, TABLE_TOP / 16F, 0);
            BlockState state = PiForge.PLACED_MOLD.get().defaultBlockState().setValue(PlacedMoldBlock.SHAPE, shape)
                  .setValue(PlacedMoldBlock.FACING, Direction.SOUTH);
            Minecraft.getInstance().getBlockRenderer().renderSingleBlock(state, pose, buffers, light, overlay, ModelData.EMPTY, null);
            pose.popPose();
        } else if (!mold.isEmpty()) {
            flat(mold, 14.05F, 0.75F, tile, pose, buffers, light, overlay);
        }
        if (tile.amount() > 0 && shape != null) {
            float solid = tile.solid();
            int metal = tile.metal().isEmpty() ? 0x7A6A5A : Metals.colour(tile.metal());
            int colour;
            String tex;
            int lit = light;
            if (solid < 0.55F) {
                float t = solid / 0.55F;
                colour = 255 << 16 | lerp(236, 70, t) << 8 | lerp(150, 16, t);
                int glow = Math.round(15 * (1 - t * 0.7F));
                lit = LightTexture.pack(Math.max(glow, LightTexture.block(light)), LightTexture.sky(light));
                tex = "forge_molten";
            } else {
                colour = Math.min(255, (metal >> 16 & 255) * 3 / 2) << 16 | Math.min(255, (metal >> 8 & 255) * 3 / 2) << 8
                      | Math.min(255, (metal & 255) * 3 / 2);
                tex = "steel";
            }
            float top = TABLE_TOP + PlacedMoldBlock.TOP - 0.15F;
            KDraw d = new KDraw(pose, buffers, lit, overlay);
            d.tint(0xFF000000 | colour);
            for (float[] c : shape.cavities) {
                d.box(c[0] + 0.02F, TABLE_TOP + shape.floor, c[1] + 0.02F, c[2] - 0.02F, top, c[3] - 0.02F, tex, tex);
            }
            d.tint(0xFFFFFFFF);
        } else if (tile.amount() <= 0 && !out.isEmpty()) {
            flat(out, TABLE_TOP + (shape != null ? PlacedMoldBlock.TOP : 0) + 0.9F, 0.6F, tile, pose, buffers, light, overlay);
        }
    }

    private static void flat(ItemStack stack, float y, float scale, CastingTableTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        pose.pushPose();
        pose.translate(0.5, y / 16.0, 0.5);
        pose.mulPose(Axis.XP.rotationDegrees(90));
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(stack, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), 0);
        pose.popPose();
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }
}
