package fr.pioneers.client.bronze;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.bronze.autofoundry.AutoMoldBlock;
import fr.pioneers.bronze.autofoundry.AutoMoldTile;
import fr.pioneers.bronze.autofoundry.AutoMoldUnit;
import fr.pioneers.bronze.foundry.Metals;
import fr.pioneers.client.kinetics.KDraw;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 4) : moule automatique : le metal qui monte dans l'empreinte choisie pendant la coulee ({@link AutoMoldBlock.Form#cavities}),
 * blanc-jaune a la coulee, rouge sombre en figeant, puis couleur du metal (piece prete : elle passe a la sortie, l'empreinte se vide). Meme
 * teinte que le moule pose et le moule d'enclume. {@link #draw} est aussi appele par le banc hors jeu (tools/kinetics, scene « automold »).
 */
public class AutoMoldRenderer implements BlockEntityRenderer<AutoMoldTile> {

    public AutoMoldRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull AutoMoldTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        AutoMoldUnit unit = tile.mold();
        BlockState state = tile.getBlockState();
        if (unit.amount() <= 0 || unit.metal().isEmpty() || !state.hasProperty(AutoMoldBlock.FORM)) {
            return;
        }
        float fill = unit.need() <= 0 ? 1F : Math.min(1F, unit.amount() / (float) unit.need());
        float solid = unit.solid();
        int lit = solid < 0.6F ? LightTexture.pack(Math.max(Math.round(15 * (1 - solid / 0.6F * 0.7F)), LightTexture.block(light)), LightTexture.sky(light)) : light;
        Direction facing = state.hasProperty(BronzeBlock.FACING) ? state.getValue(BronzeBlock.FACING) : Direction.SOUTH;
        draw(new KDraw(pose, buffers, lit, overlay), facing, state.getValue(AutoMoldBlock.FORM), fill, solid, unit.metal());
    }

    /**
     * Le metal dans l'empreinte (repere du bloc, px) : {@code fill} 0..1 de la profondeur, {@code solid} 0 (coulee) .. 1 (fige). Appele aussi
     * par le banc hors jeu.
     */
    public static void draw(KDraw d, Direction facing, AutoMoldBlock.Form form, float fill, float solid, String metal) {
        float floor = form.floor(), top = floor + (form.top() - floor - 0.15F) * fill;
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
        for (float[] c : form.cavities()) {
            d.box(c[0] + 0.02F, floor, c[1] + 0.02F, c[2] - 0.02F, top, c[3] - 0.02F, tex, tex);
        }
        d.tint(0xFFFFFFFF);
        d.pop();
    }

    private static int lerp(int a, int b, float t) {
        return Math.round(a + (b - a) * t);
    }

    /** Icone d'une empreinte (interface) : le moule d'argile de cette forme. */
    public static ItemStack icon(AutoMoldBlock.Form form) {
        return new ItemStack(form.moldItem());
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
