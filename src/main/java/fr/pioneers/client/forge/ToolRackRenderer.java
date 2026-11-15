package fr.pioneers.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.forge.ForgeBlock;
import fr.pioneers.forge.smithing.ToolRackBlock;
import fr.pioneers.forge.smithing.ToolRackLayout;
import fr.pioneers.forge.smithing.ToolRackTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.2-beta (lot 2) : porte-outils : les trois outils DEBOUT dans le ratelier, manche en bas entre les deux traverses, legerement penches
 * contre la traverse arriere ; rendu des vrais objets (modeles 3D des outils compris), vue « posee » ({@link ItemDisplayContext#FIXED}).
 * <p>
 * 1.0.3-beta (lot 11, Guillaume : « tourner de 90 deg les outils pour les avoir de face et non plus de profil ; quand on les place, ils doivent
 * etre face a nous ») : outils DE FACE (dans le plan du ratelier, plus de quart de tour en y), modele du ratelier retourne (facade au sud
 * pour de vrai, T:home/racks.py) : profondeur {@link #Z} 8,8 px (16 - 7,2), penchés vers la traverse haute (+8 deg) ; outils un peu plus
 * petits et decales en profondeur : les tetes voisines se chevauchent sans scintiller.
 * <p>
 * 1.0.3-beta (lot 14, Guillaume : « les outils sont assez mal affiches ») : places de {@link ToolRackLayout} (pur) : outil penche vers le
 * FOND jusqu'a son appui (il penchait vers le joueur), echelle selon la largeur reelle de l'outil (mesuree sur son modele cuit), outil du
 * milieu plus bas (tetes jamais cote a cote) ; porte-outils MURAL ({@link ToolRackBlock#WALL}) : outils suspendus aux chevilles.
 */
public class ToolRackRenderer implements BlockEntityRenderer<ToolRackTile> {

    /** 1.0.3-beta (lot 14) : encombrement de chaque outil dans le repere debout ({@link ToolRackLayout}), mesure une fois sur son modele cuit. */
    private static final Map<Item, float[]> BOUNDS = new HashMap<>();

    public ToolRackRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull ToolRackTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        BlockState state = tile.getBlockState();
        Direction facing = state.hasProperty(ForgeBlock.FACING) ? state.getValue(ForgeBlock.FACING) : Direction.SOUTH;
        boolean wall = state.hasProperty(ToolRackBlock.WALL) && state.getValue(ToolRackBlock.WALL);
        for (int i = 0; i < ToolRackTile.SLOTS; i++) {
            ItemStack tool = tile.tool(i);
            if (tool.isEmpty()) {
                continue;
            }
            ToolRackLayout.Place p = ToolRackLayout.place(wall, i, bounds(tool, tile));
            pose.pushPose();
            pose.translate(0.5, 0, 0.5);
            pose.mulPose(Axis.YP.rotationDegrees(-faceDegrees(facing)));
            pose.translate(-0.5, 0, -0.5);
            pose.translate(p.x() / 16F, p.y() / 16F, p.z() / 16F);
            pose.mulPose(Axis.XP.rotationDegrees(p.lean()));
            pose.scale(p.scale(), p.scale(), p.scale());
            //Un outil est dessine en diagonale ; la vue FIXED le retourne (y 180) : un huitieme de tour le met debout, tete en haut, de face
            pose.mulPose(Axis.ZP.rotationDegrees(ToolRackLayout.ZTURN));
            Minecraft.getInstance().getItemRenderer().renderStatic(tool, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), i);
            pose.popPose();
        }
    }

    /** Encombrement (px : minX, maxX, minY, maxY) de l'outil debout : sommets de son modele cuit apres la vue FIXED et {@code ZTURN}. */
    private static float[] bounds(ItemStack tool, ToolRackTile tile) {
        return BOUNDS.computeIfAbsent(tool.getItem(), item -> {
            BakedModel model = Minecraft.getInstance().getItemRenderer().getModel(tool, tile.getLevel(), null, 0);
            PoseStack ps = new PoseStack();
            ps.mulPose(Axis.ZP.rotationDegrees(ToolRackLayout.ZTURN));
            model = model.applyTransform(ItemDisplayContext.FIXED, ps, false);
            ps.translate(-0.5F, -0.5F, -0.5F);
            Matrix4f m = ps.last().pose();
            float[] b = {Float.MAX_VALUE, -Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE};
            RandomSource random = RandomSource.create(42);
            List<BakedQuad> quads = new ArrayList<>(model.getQuads(null, null, random));
            for (Direction d : Direction.values()) {
                quads.addAll(model.getQuads(null, d, random));
            }
            Vector4f v = new Vector4f();
            for (BakedQuad q : quads) {
                int[] data = q.getVertices();
                int stride = data.length / 4;
                for (int k = 0; k < 4; k++) {
                    v.set(Float.intBitsToFloat(data[k * stride]), Float.intBitsToFloat(data[k * stride + 1]), Float.intBitsToFloat(data[k * stride + 2]), 1);
                    m.transform(v);
                    b[0] = Math.min(b[0], v.x() * 16);
                    b[1] = Math.max(b[1], v.x() * 16);
                    b[2] = Math.min(b[2], v.y() * 16);
                    b[3] = Math.max(b[3], v.y() * 16);
                }
            }
            return quads.isEmpty() ? new float[]{-4, 4, -8, 8} : b;
        });
    }

    /** Rotation (degres) d'un modele dessine facade au sud vers {@code facing} (meme regle que les etats generes). */
    static float faceDegrees(Direction facing) {
        return switch (facing) {
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
    }
}
