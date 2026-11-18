package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.mechanics.HandSieveBlock;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import org.jetbrains.annotations.NotNull;

/** Tamis a main : maille du tamis pose et sediment verse. */
public class HandSieveRenderer implements BlockEntityRenderer<HandSieveBlock.Tile> {

    public HandSieveRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull HandSieveBlock.Tile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        MachineItems.handSieve(tile, pose, buffers, light, overlay);
    }
}
