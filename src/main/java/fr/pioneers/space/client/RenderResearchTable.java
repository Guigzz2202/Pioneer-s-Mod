package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.client.render.DataPortRenderer;
import fr.pioneers.space.research.client.ClientResearch;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.tile.TileEntityResearchTable;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Laboratoire au travail : l'icone de la recherche en cours flotte et tourne au-dessus de la table,
 * entouree d'un tourbillon de donnees aux couleurs des astres consommes.
 */
public class RenderResearchTable implements BlockEntityRenderer<TileEntityResearchTable> {

    public RenderResearchTable(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityResearchTable tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        DataPortRenderer.render(tile, tile.facing(), "research_desk", pose, buffers, light, overlay);
        boolean working = tile.isClientWorking();
        ResearchNode node = tile.getClientNode() >= 0 ? ResearchNode.byIndex(tile.getClientNode()) : ClientResearch.getActive();
        if (node == null) {
            return;
        }
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        ItemStack icon = node.getIcon();
        Direction facing = tile.facing();
        float facingYaw = switch (facing) {
            case EAST -> -90;
            case SOUTH -> 180;
            case WEST -> 90;
            default -> 0;
        };
        pose.pushPose();
        //Repere du modele oriente nord
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(facingYaw));
        pose.translate(-0.5, 0, -0.5);
        //Icone sur l'ecran du moniteur
        pose.pushPose();
        pose.translate(8 / 16F, 21 / 16F, 10.4F / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        pose.scale(0.32F, 0.32F, 0.02F);
        Minecraft.getInstance().getItemRenderer().renderStatic(icon, ItemDisplayContext.GUI, LightTexture.FULL_BRIGHT,
              OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
        //Avancement de la recherche commune sous l'icone
        int progress = ClientResearch.getProgress(node);
        String text = Math.round(100F * progress / node.units) + " %";
        var font = Minecraft.getInstance().font;
        pose.pushPose();
        pose.translate(8 / 16F, 17.6F / 16F, 10.38F / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(180));
        float textScale = 0.009F;
        pose.scale(textScale, -textScale, textScale);
        font.drawInBatch(text, -font.width(text) / 2F, 0, working ? 0x7CFFE0 : 0x5A8CA0, false, pose.last().pose(), buffers,
              Font.DisplayMode.NORMAL, 0, LightTexture.FULL_BRIGHT);
        pose.popPose();
        //Hologramme au-dessus du projecteur
        pose.pushPose();
        pose.translate(13 / 16F, 1.2 + Mth.sin(time * 0.08F) * 0.03, 4.5F / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(time * (working ? 3F : 0.8F)));
        float scale = working ? 0.3F : 0.22F;
        pose.scale(scale, scale, scale);
        Minecraft.getInstance().getItemRenderer().renderStatic(icon, ItemDisplayContext.FIXED, working ? LightTexture.FULL_BRIGHT : light,
              OverlayTexture.NO_OVERLAY, pose, buffers, level, 0);
        pose.popPose();
        pose.popPose();

        //0.20.46 (audit) : une chance par tick de jeu (et non par image)
        if (working && !Minecraft.getInstance().isPaused() && FxTicks.firstFrame(tile, level) && level.random.nextInt(2) == 0) {
            List<Integer> colors = new ArrayList<>();
            for (ProbeDestination type : node.getUnitCost().keySet()) {
                colors.add(type.color);
            }
            BlockPos pos = tile.getBlockPos();
            int color = colors.get(level.random.nextInt(colors.size()));
            float angle = time * 0.25F + level.random.nextFloat() * Mth.TWO_PI;
            double radius = 0.45 + level.random.nextDouble() * 0.15;
            //Centre du projecteur, tourne selon la facade
            double lx = 13 / 16.0 - 0.5, lz = 4.5 / 16.0 - 0.5;
            double rad = Math.toRadians(-facingYaw);
            double cx = pos.getX() + 0.5 + lx * Math.cos(rad) - lz * Math.sin(rad), cz = pos.getZ() + 0.5 + lx * Math.sin(rad) + lz * Math.cos(rad);
            radius *= 0.35;
            double x = cx + Mth.cos(angle) * radius, z = cz + Mth.sin(angle) * radius;
            Vector3f rgb = new Vector3f((color >> 16 & 0xFF) / 255F, (color >> 8 & 0xFF) / 255F, (color & 0xFF) / 255F);
            //Les particules spiralent vers l'icone
            level.addParticle(new DustParticleOptions(rgb, 0.6F), x, pos.getY() + 0.95 + level.random.nextDouble() * 0.1, z,
                  (cx - x) * 0.08, 0.03, (cz - z) * 0.08);
        }
    }

    @Override
    public int getViewDistance() {
        return 32;
    }
}
