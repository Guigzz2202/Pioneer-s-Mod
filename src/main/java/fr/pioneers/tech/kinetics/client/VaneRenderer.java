package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tech.kinetics.sources.WeatherVaneBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.jetbrains.annotations.NotNull;

/** Girouette-anemometre : fleche dans le vent, coupelles a la vitesse du vent. */
public class VaneRenderer implements BlockEntityRenderer<WeatherVaneBlock.Tile> {

    public VaneRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull WeatherVaneBlock.Tile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (tile.getLevel() == null) {
            return;
        }
        //1.0.5-beta (lot 29) : heure serveur estimee (RenderClock) : plus de recul chaque seconde sur un serveur lent
        double t = RenderClock.time(partial);
        float direction = (float) WeatherVaneBlock.Tile.direction(t);
        float spin = (float) (tile.cups.known() ? tile.cups.angle(t) % 360 : t * tile.wind * 6 % 360);
        KineticVisuals.vane(new KDraw(pose, buffers, light, overlay), direction, spin);
    }
}
