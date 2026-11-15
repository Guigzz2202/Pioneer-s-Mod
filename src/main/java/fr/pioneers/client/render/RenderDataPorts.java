package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.core.machine.MachineTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.NotNull;

/**
 * Rendu des seules prises de donnees (machines sans autre animation).
 */
public class RenderDataPorts<T extends BlockEntity> implements BlockEntityRenderer<T> {

    private final String base;

    public RenderDataPorts(String base) {
        this.base = base;
    }

    @Override
    public void render(@NotNull T tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (tile instanceof MachineTile machine) {
            DataPortRenderer.render(tile, machine.facing(), base, pose, buffers, light, overlay);
        }
    }
}
