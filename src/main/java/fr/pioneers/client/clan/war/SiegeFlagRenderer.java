package fr.pioneers.client.clan.war;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.clan.war.SiegeFlagBlock;
import fr.pioneers.clan.war.SiegeFlagTile;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BannerRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.resources.model.ModelBakery;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : rendu de la banniere du Drapeau de siege. La hampe, le pied et le fleuron sont le modele fixe du bloc (dessine
 * facade au SUD, tourne par {@code FACING}) ; la tuile ajoute la banniere de l'agresseur (drap et traverse du modele de banniere de
 * Minecraft, a sa teinture et aux motifs de la banniere de sa Mairie), hissee le long de la hampe de {@link #LOW} a {@link #HIGH} selon
 * l'avancement de la capture et flottant au vent. L'avancement affiche rejoint en douceur celui du serveur (envoye au plus toutes les 5 s).
 */
public class SiegeFlagRenderer implements BlockEntityRenderer<SiegeFlagTile> {

    /** Echelle de la banniere (drap 20 x 40 du modele : 7,2 x 14,4 px). */
    static final float SCALE = 0.36F;
    /** Haut de la traverse (blocs) a 0 % et a 100 % : le drap part au-dessus du lest (4,6 px) et monte jusqu'a la poulie (29,4 px). */
    static final float LOW = 19F / 16F, HIGH = 29.4F / 16F;
    /** Decalage devant la hampe (2 px de section) : la traverse touche la hampe, le drap passe devant le taquet et la poulie. */
    static final float FRONT = 1.37F / 16F;

    private final ModelPart flag, bar;

    public SiegeFlagRenderer(BlockEntityRendererProvider.Context ctx) {
        ModelPart root = ctx.bakeLayer(ModelLayers.BANNER);
        flag = root.getChild("flag");
        bar = root.getChild("bar");
    }

    @Override
    public void render(@NotNull SiegeFlagTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
                       int overlay) {
        float target = (float) Mth.clamp(tile.progress(), 0, 1);
        if (tile.shown < 0) {
            tile.shown = target;
        }
        tile.shown += (target - tile.shown) * 0.06F;
        BlockState state = tile.getBlockState();
        Direction facing = state.hasProperty(SiegeFlagBlock.FACING) ? state.getValue(SiegeFlagBlock.FACING) : Direction.SOUTH;
        float h = LOW + (HIGH - LOW) * tile.shown;
        pose.pushPose();
        pose.translate(0.5F, h, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.translate(0, 0, FRONT);
        pose.scale(SCALE, -SCALE, -SCALE);
        //traverse : boite y -32..-30 du modele, ramenee sous le point de hissage
        bar.y = 32F;
        VertexConsumer vc = ModelBakery.BANNER_BASE.buffer(buffers, RenderType::entitySolid);
        bar.render(pose, vc, light, overlay);
        //drap : flotte comme une banniere de Minecraft, un peu plus vite quand il vient d'etre hisse
        BlockPos p = tile.getBlockPos();
        long time = tile.getLevel() == null ? 0 : tile.getLevel().getGameTime();
        float f = ((float) Math.floorMod(p.getX() * 7L + p.getY() * 9L + p.getZ() * 13L + time, 100L) + partialTick) / 100.0F;
        float gust = tile.holding() ? 0.016F : 0.01F;
        flag.xRot = (-0.0125F + gust * Mth.cos(Mth.TWO_PI * f)) * Mth.PI;
        flag.y = 0F;
        BannerRenderer.renderPatterns(pose, buffers, light, overlay, flag, ModelBakery.BANNER_BASE, true, tile.patterns());
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull SiegeFlagTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
