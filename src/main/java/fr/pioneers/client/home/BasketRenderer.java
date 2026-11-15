package fr.pioneers.client.home;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.home.basket.BasketHeap;
import fr.pioneers.home.basket.BasketTile;
import java.util.List;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.entity.ItemRenderer;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.3-beta (lot 11b) : contenu visible du panier : l'objet lui-meme, couche a plat en tas ({@link BasketHeap} : places fixes couche par
 * couche), autant de places que le palier de remplissage ({@link BasketTile#level()} : 1 a 6). Vue FIXED de l'objet (les objets de bloc,
 * comme la citrouille, restent des cubes).
 * <p>
 * 1.0.3-beta (lot 12, Guillaume : « largement plus dense ») : tas du format du panier ({@link BasketHeap#of} : demi-hauteur ou haut) ;
 * objets plats en couches serrees qui se chevauchent, objets de bloc (modele 3D : {@code isGui3d}) en cubes reduits debout ; seules les
 * dernieres places montrees sont dessinees ({@link BasketHeap.Heap#first} : plafond de 56 / 72 objets, les couches du fond etant cachees).
 * <p>
 * 1.0.3-beta (lot 14, Guillaume : « texture du contenu assez sale ; plein = a ras bord, hauteur proportionnelle ») : seule la SURFACE est
 * dessinee ({@link BasketHeap#surface}) a la hauteur du remplissage exact ; les paliers ({@link BasketTile#level()}) ne servent plus au rendu.
 */
public class BasketRenderer implements BlockEntityRenderer<BasketTile> {

    private final ItemRenderer items;

    public BasketRenderer(BlockEntityRendererProvider.Context context) {
        this.items = context.getItemRenderer();
    }

    @Override
    public void render(@NotNull BasketTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        ItemStack kind = tile.kind();
        if (kind.isEmpty() || tile.count() <= 0) {
            return;
        }
        //1.0.3-beta (lot 14) : surface du tas a la hauteur du remplissage exact (BasketHeap.surface : 13 objets au plus, quarts de tour, sous-couches
        //sans plan commun) ; plus de tas complet de 56 / 72 sprites inclines qui se melangeaient
        var model = items.getModel(kind, tile.getLevel(), null, 0);
        boolean blocky = model.isGui3d();
        //1.0.3-beta (correctif) : lit de la texture de l'objet sous la surface (BasketHeap.bed) : plus de fond de panier visible
        bed(model.getParticleIcon(), BasketHeap.bed(tile.tall(), blocky, tile.count(), tile.capacity()), pose, buffers, light, overlay);
        List<BasketHeap.Place> places = BasketHeap.surface(tile.tall(), blocky, tile.count(), tile.capacity());
        float s = (float) (blocky ? BasketHeap.BLOCK_SCALE : BasketHeap.TOP_SCALE);
        for (int i = 0; i < places.size(); i++) {
            BasketHeap.Place p = places.get(i);
            pose.pushPose();
            pose.translate(p.x() / 16.0, p.y() / 16.0, p.z() / 16.0);
            pose.mulPose(Axis.YP.rotationDegrees((float) p.yaw()));
            if (!blocky) {
                pose.mulPose(Axis.XP.rotationDegrees(90F + (float) p.tilt()));
            }
            pose.scale(s, s, s);
            items.renderStatic(kind, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), (int) tile.getBlockPos().asLong() + i);
            pose.popPose();
        }
    }

    /** Carreaux du lit, a plat (normale vers le haut), texture de l'objet (atlas des blocs, comme les objets). */
    private static void bed(TextureAtlasSprite sprite, List<BasketHeap.BedTile> tiles, PoseStack pose, MultiBufferSource buffers, int light,
          int overlay) {
        if (tiles.isEmpty()) {
            return;
        }
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (BasketHeap.BedTile b : tiles) {
            float y = (float) (b.y() / 16);
            double[][] corners = {{b.x0(), b.z0(), b.fu0(), b.fv0()}, {b.x0(), b.z1(), b.fu0(), b.fv1()}, {b.x1(), b.z1(), b.fu1(), b.fv1()},
                  {b.x1(), b.z0(), b.fu1(), b.fv0()}};
            int c = Math.round(255 * b.shade());
            for (double[] k : corners) {
                double fu = k[2], fv = k[3], u, v;
                switch (b.rot()) {
                    case 1 -> { u = fv; v = 1 - fu; }
                    case 2 -> { u = 1 - fu; v = 1 - fv; }
                    case 3 -> { u = 1 - fv; v = fu; }
                    default -> { u = fu; v = fv; }
                }
                vc.vertex(m, (float) (k[0] / 16), y, (float) (k[1] / 16)).color(c, c, c, 255)
                      .uv(sprite.getU(u * 16), sprite.getV(v * 16)).overlayCoords(overlay).uv2(light).normal(n, 0, 1, 0).endVertex();
            }
        }
    }

    @Override
    public int getViewDistance() {
        return 48;
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull BasketTile tile) {
        return false;
    }
}
