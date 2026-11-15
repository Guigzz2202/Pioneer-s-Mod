package fr.pioneers.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.forge.ForgeBlock;
import fr.pioneers.forge.casting.HandBellowsTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import org.jetbrains.annotations.NotNull;

/**
 * Soufflet a main : la planche du dessus (avec sa poignee) articulee pres de la tuyere, et les plis de cuir entre les deux planches. Au repos
 * il est ouvert ({@link #OPEN} degres) ; une bouffee le ferme en 3 ticks puis il se rouvre en 9. Repere : tuyere vers +Z, charniere en
 * (y 2, z 12) ; la planche du bas, la tuyere et la charniere sont dans le modele fixe ({@code tools/gc/forge/casting.py}).
 */
public class HandBellowsRenderer implements BlockEntityRenderer<HandBellowsTile> {

    public static final float OPEN = 18F, CLOSED = 3F;
    private static final float HINGE_Y = 2.5F, HINGE_Z = 12F;

    public HandBellowsRenderer(BlockEntityRendererProvider.Context context) {
    }

    /** Ouverture (degres) d'apres l'age de la derniere bouffee (ticks). */
    public static float angle(float age) {
        if (age < 0 || age >= 12) {
            return OPEN;
        }
        if (age < 3) {
            return OPEN + (CLOSED - OPEN) * (age / 3F);
        }
        float t = (age - 3F) / 9F;
        return CLOSED + (OPEN - CLOSED) * (t * t * (3 - 2 * t));
    }

    @Override
    public void render(@NotNull HandBellowsTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Direction facing = tile.getBlockState().hasProperty(ForgeBlock.FACING) ? tile.getBlockState().getValue(ForgeBlock.FACING) : Direction.SOUTH;
        draw(new KDraw(pose, buffers, light, overlay), facing, angle(tile.puffAge(partial)));
    }

    /** Parties mobiles (aussi pour un banc d'apercu). */
    public static void draw(KDraw d, Direction facing, float angle) {
        d.push().face(facing);
        //Plis de cuir : trois feuilles a des ouvertures intermediaires
        for (int k = 1; k <= 3; k++) {
            d.push().rotate(Direction.Axis.X, angle * k / 4F, 8, HINGE_Y, HINGE_Z);
            float inset = 0.6F + 0.25F * (k % 2);
            d.box(3 + inset, 2.0F, 3.1F + inset, 13 - inset, 2.6F, 11.6F, "leather", "leather");
            d.pop();
        }
        d.push().rotate(Direction.Axis.X, angle, 8, HINGE_Y, HINGE_Z);
        d.box(3, 2.6F, 3, 13, 3.6F, 12, "oak_planks", "oak_planks");
        d.box(7, 2.8F, 0.8F, 9, 3.4F, 3, "oak", "oak_end");
        d.box(6.4F, 2.7F, 0, 9.6F, 3.5F, 0.8F, "oak", "oak_end");
        d.box(6.5F, 3.6F, 6.5F, 9.5F, 3.9F, 8.5F, "leather", "leather");
        d.pop();
        d.pop();
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
