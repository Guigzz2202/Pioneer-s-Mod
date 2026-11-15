package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.space.client.FxTicks;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.tile.TileEntityAssemblyTable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Tables d'assemblage d'un bloc : le produit se materialise en hologramme au-dessus du plateau pendant l'assemblage
 * (il grandit avec l'avancement), puis reste pose tant qu'il attend dans la sortie.
 */
public class RenderAssemblyTable implements BlockEntityRenderer<TileEntityAssemblyTable> {

    public RenderAssemblyTable(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityAssemblyTable tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        ItemStack product = tile.getRenderedProduct();
        if (level == null || product.isEmpty() || !tile.shouldShowProduct()) {
            return;
        }
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        boolean finished = tile.isFinishedClient();
        float shown = finished ? 1 : Math.max(0.2F, tile.getRenderProgress());
        pose.pushPose();
        //1.9.2 : la table des rovers est devenue la baie d'assemblage robotique (RenderAssemblyHall) : il ne reste que les tables d'un bloc
        pose.translate(0.5, 0.95 + (finished ? 0 : Math.sin(time * 0.1) * 0.02), 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(time * (finished ? 0.6F : 2.5F)));
        float scale = 0.55F * shown;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(product, ItemDisplayContext.FIXED, finished ? light : LightTexture.FULL_BRIGHT,
              overlay, pose, buffers, level, 0);
        pose.popPose();
        //0.20.46 (audit) : une chance par tick de jeu (et non par image)
        if (!finished && tile.getActive() && FxTicks.firstFrame(tile, level) && level.random.nextInt(3) == 0) {
            double spread = 0.5;
            double x = tile.getBlockPos().getX() + 0.5 + (level.random.nextDouble() - 0.5) * spread;
            double z = tile.getBlockPos().getZ() + 0.5 + (level.random.nextDouble() - 0.5) * spread;
            level.addParticle(ParticleTypes.ELECTRIC_SPARK, x, tile.getBlockPos().getY() + 0.8, z, 0, 0.02, 0);
        }
    }
}
