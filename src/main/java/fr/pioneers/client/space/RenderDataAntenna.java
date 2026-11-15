package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.render.DataPortRenderer;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tile.TileEntityDataAntenna;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;

/**
 * Parabole de l'antenne : elle balaie lentement le ciel quand l'antenne ecoute, et s'oriente vers une direction propre
 * a chaque destination (on voit de loin sur quoi une antenne est accordee).
 */
public class RenderDataAntenna implements BlockEntityRenderer<TileEntityDataAntenna> {

    public static final ResourceLocation DISH = Pioneers.rl("block/data_antenna/dish");
    public static final ResourceLocation MAST_TOP = Pioneers.rl("block/data_antenna/mast_top");

    public RenderDataAntenna(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityDataAntenna tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        DataPortRenderer.render(tile, tile.facing(), "data_antenna", pose, buffers, light, overlay);
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        int target = tile.getDestination().ordinal();
        //Direction de base selon la destination, balayage leger quand l'antenne ecoute
        float yaw = target * 137 + (tile.isClientActive() ? Mth.sin(time * 0.02F) * 25 : 0);
        float tilt = 18 + (target % 6) * 7 + (tile.isClientActive() ? Mth.sin(time * 0.033F) * 6 : 0);
        var renderer = Minecraft.getInstance().getBlockRenderer().getModelRenderer();
        //Haut du mat (troisieme bloc)
        pose.pushPose();
        pose.translate(0, 2, 0);
        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, Minecraft.getInstance().getModelManager().getModel(MAST_TOP),
              1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
        //Parabole : pivot au sommet du mat
        pose.pushPose();
        pose.translate(0.5, 46 / 16F, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw));
        pose.mulPose(Axis.XP.rotationDegrees(tilt));
        pose.translate(-0.5, -0.5, -0.5);
        BakedModel model = Minecraft.getInstance().getModelManager().getModel(DISH);
        renderer.renderModel(pose.last(), buffers.getBuffer(RenderType.cutout()), null, model, 1, 1, 1, light, overlay, ModelData.EMPTY, RenderType.cutout());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityDataAntenna tile) {
        return true;
    }
}
