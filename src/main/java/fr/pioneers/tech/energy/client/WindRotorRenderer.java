package fr.pioneers.tech.energy.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.tech.kinetics.client.KDraw;
import fr.pioneers.tech.energy.EnergyKind;
import fr.pioneers.tech.energy.WindRotorTile;
import fr.pioneers.tech.energy.WindTurbineTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.45 : rotor anime de l'eolienne. 1.9.2 (retours de Guillaume) : dessine par le bloc du rotor lui-meme (pose devant la nacelle) ;
 * l'angle vient de la nacelle de derriere, sans nacelle le rotor reste immobile.
 */
public class WindRotorRenderer implements BlockEntityRenderer<WindRotorTile> {

    public WindRotorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull WindRotorTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        Direction facing = tile.getBlockState().getValue(HorizontalDirectionalBlock.FACING);
        WindTurbineTile nacelle = tile.nacelle();
        float angle = nacelle == null ? 15 : nacelle.rotorAngle(partial);
        int tier = tile.tier();
        KDraw d = new KDraw(pose, buffers, light, overlay);
        d.push().face(facing);
        EnergyVisuals.windRotor(d, angle, EnergyKind.WIND_BLADE[tier], tier);
        d.pop();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull WindRotorTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
