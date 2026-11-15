package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.content.stabilizer.Regime;
import fr.pioneers.content.stabilizer.StabilizerCoreTile;
import fr.pioneers.content.stabilizer.StabilizerState;
import java.util.UUID;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Noyau du Stabilisateur spatial : ne dessine rien lui-meme, il signale a {@link ChamberEffectRenderer} l'astre a dessiner au centre du globe
 * (trou noir ou etoile selon le regime) avec son etat.
 */
public class RenderStabilizerCore implements BlockEntityRenderer<StabilizerCoreTile> {

    public RenderStabilizerCore(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull StabilizerCoreTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (!tile.isFormed()) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(("stabilizer" + tile.getBlockPos().asLong()).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        ChamberEffectRenderer.Kind kind = tile.getRegime() == Regime.STAR ? ChamberEffectRenderer.Kind.SUN : ChamberEffectRenderer.Kind.BLACK_HOLE;
        ChamberEffectRenderer.queue(id, kind, Vec3.atCenterOf(tile.getBlockPos()), tile.getState() != StabilizerState.OFF,
              tile.getFormation(), tile.getRate());
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull StabilizerCoreTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
