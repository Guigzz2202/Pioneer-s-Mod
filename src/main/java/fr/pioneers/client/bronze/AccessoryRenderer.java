package fr.pioneers.client.bronze;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.bronze.steam.AccessoryBlock;
import fr.pioneers.bronze.steam.AccessoryTile;
import fr.pioneers.bronze.steam.SteamAccessory;
import fr.pioneers.client.kinetics.KDraw;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Manometre (aiguille) et tube de niveau (colonne d'eau) ; les autres organes n'ont rien d'anime. */
public class AccessoryRenderer implements BlockEntityRenderer<AccessoryTile> {

    public AccessoryRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static boolean animated(SteamAccessory kind) {
        return kind == SteamAccessory.PRESSURE_GAUGE || kind == SteamAccessory.WATER_GAUGE;
    }

    /** 0.20.46 (audit) : soupapes, sifflets... n'ont rien a dessiner : ecartes avant la lecture de la lumiere et l'appel du rendu. */
    @Override
    public boolean shouldRender(@NotNull AccessoryTile tile, @NotNull Vec3 camera) {
        return animated(tile.kind()) && BlockEntityRenderer.super.shouldRender(tile, camera);
    }

    @Override
    public void render(@NotNull AccessoryTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        SteamAccessory kind = tile.kind();
        if (!animated(kind)) {
            return;
        }
        Direction facing = tile.getBlockState().getValue(AccessoryBlock.FACING);
        KDraw d = new KDraw(pose, buffers, light, overlay);
        if (kind == SteamAccessory.PRESSURE_GAUGE) {
            d.push().face(facing).rotate(Direction.Axis.X, 90, 8, 8, 8);
            SteamVisuals.needle(d, tile.clientFill());
            d.pop();
        } else {
            if (facing.getAxis() == Direction.Axis.Y) {
                d.push();
                if (facing == Direction.DOWN) {
                    d.rotate(Direction.Axis.X, 180, 8, 8, 8);
                }
                SteamVisuals.waterTop(d, tile.clientWater());
                d.pop();
            } else {
                d.push().face(facing);
                SteamVisuals.waterSide(d, tile.clientWater());
                d.pop();
            }
        }
    }
}
