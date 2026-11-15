package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.core.RenderClock;
import fr.pioneers.space.RocketTier;
import fr.pioneers.tile.TileEntityLaunchPad;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;
import org.joml.Vector3f;

/**
 * Complexe de lancement en reception : la fusee logistique en escale (elle n'est pas une entite), qui tremble au compte
 * a rebours du retour, et un anneau holographique a la couleur de la cargaison pendant le dechargement.
 */
public class RenderLaunchPad implements BlockEntityRenderer<TileEntityLaunchPad> {

    private final RocketModels rockets;

    public RenderLaunchPad(BlockEntityRendererProvider.Context context) {
        rockets = new RocketModels(context::bakeLayer);
    }

    @Override
    public void render(@NotNull TileEntityLaunchPad tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        int visitor = tile.getClientVisitor();
        if (visitor < 0 || tile.getLevel() == null) {
            return;
        }
        var level = tile.getLevel();
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        var pos = tile.getBlockPos();
        //0.20.46 (audit) : particules une fois par tick de jeu (et non par image)
        boolean fx = FxTicks.firstFrame(tile, level);
        pose.pushPose();
        pose.translate(0.5, 1, 0.5);
        if (tile.isClientCountdown()) {
            pose.translate(Mth.sin(time * 2.7F) * 0.02F, 0, Mth.cos(time * 3.1F) * 0.02F);
            for (int i = 0; fx && i < 3; i++) {
                double ox = (level.random.nextDouble() - 0.5) * 0.6, oz = (level.random.nextDouble() - 0.5) * 0.6;
                level.addParticle(ParticleTypes.FLAME, pos.getX() + 0.5 + ox, pos.getY() + 1.05, pos.getZ() + 0.5 + oz, ox * 0.3, 0.01, oz * 0.3);
            }
        }
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        rockets.render(RocketTier.byIndex(visitor), pose, buffers, RenderType::entityCutoutNoCull, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
        //Anneau holographique pendant le dechargement
        if (fx && tile.isClientBusy() && level.random.nextInt(2) == 0) {
            int c = tile.getClientCargoColor();
            float angle = time * 0.25F;
            Vector3f color = new Vector3f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F);
            for (int i = 0; i < 2; i++) {
                float a = angle + i * Mth.PI;
                level.addParticle(new DustParticleOptions(color, 0.9F), pos.getX() + 0.5 + Mth.cos(a) * 1.3, pos.getY() + 3.5 + Mth.sin(time * 0.05F) * 0.8,
                      pos.getZ() + 0.5 + Mth.sin(a) * 1.3, 0, 0, 0);
            }
        }
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityLaunchPad tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
