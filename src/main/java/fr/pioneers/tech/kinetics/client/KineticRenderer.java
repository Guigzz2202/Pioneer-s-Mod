package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.core.block.Covers;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.kitchen.KitchenMachineTile;
import fr.pioneers.tech.kinetics.machines.MechanicalMachineTile;
import fr.pioneers.tech.kinetics.parts.CogBlock;
import fr.pioneers.tech.kinetics.parts.GaugeBlock;
import fr.pioneers.tech.kinetics.parts.GearboxBlock;
import fr.pioneers.tech.kinetics.parts.GovernorBlock;
import fr.pioneers.tech.kinetics.parts.HandCrankBlock;
import fr.pioneers.tech.kinetics.parts.InlineBlock;
import fr.pioneers.tech.kinetics.parts.ShaftBlock;
import fr.pioneers.tech.kinetics.sources.HorseMillTile;
import fr.pioneers.tech.kinetics.sources.WaterWheelTile;
import fr.pioneers.tech.kinetics.sources.WindmillTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/** Rendu des pieces tournantes du reseau de force rotative (voir {@link KineticVisuals}). */
public class KineticRenderer<T extends KineticTile> implements BlockEntityRenderer<T> {

    /** 0.20.46 (audit) : distances de rendu (blocs) : petites pieces, pieces ordinaires et machines, rotors et grandes roues. */
    public static final int SMALL = 96, NORMAL = 96, LARGE = 128; //arbres et petites pieces n'ont pas de modele de bloc : pas de coupure avant 96

    public KineticRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull T tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        KDraw d = new KDraw(pose, buffers, light, overlay);
        KineticVisuals.partial = partial;
        KineticVisuals.draw(d, tile.getBlockState(), tile, tile.angle(partial), tile.getBlockPos());
        KineticExtras.draw(d, tile, partial);
        if (tile instanceof MechanicalMachineTile machine) {
            MachineItems.draw(machine, pose, buffers, light, overlay);
        } else if (tile instanceof KitchenMachineTile kitchen) {
            KitchenVisuals.items(kitchen, pose, buffers, light, overlay, partial);
        }
    }

    /**
     * 0.20.46 (audit) : rendu hors de la section seulement pour les pieces qui debordent de leur bloc (roues, ailes, cordes, grand rouet...) ;
     * un arbre d'un bloc suit sa section (frustum et occlusion des sections, comme les autres blocs-entites).
     */
    @Override
    public boolean shouldRenderOffScreen(@NotNull T tile) {
        return tile.renderReach() > 0;
    }

    @Override
    public int getViewDistance() {
        return LARGE;
    }

    /** 0.20.46 (audit) : distance de rendu selon la taille de la piece (meme test que celui par defaut, sans allocation). */
    @Override
    public boolean shouldRender(@NotNull T tile, @NotNull Vec3 camera) {
        if (Covers.covered(tile.getBlockState())) {
            return false;                                                //1.0.4-beta (lot 27) : arbre cache, dessine comme son bloc de cache
        }
        BlockPos pos = tile.getBlockPos();
        double dx = pos.getX() + 0.5 - camera.x, dy = pos.getY() + 0.5 - camera.y, dz = pos.getZ() + 0.5 - camera.z;
        double d = viewDistance(tile);
        return dx * dx + dy * dy + dz * dz < d * d;
    }

    /** Distance de rendu d'une piece : {@link #SMALL} pour les petites pieces d'un bloc, {@link #LARGE} pour les rotors et roues. */
    public static int viewDistance(KineticTile tile) {
        if (tile instanceof WindmillTile || tile instanceof WaterWheelTile || tile instanceof HorseMillTile) {
            return LARGE;
        }
        Block block = tile.getBlockState().getBlock();
        if (block instanceof ShaftBlock || block instanceof CogBlock cog && !cog.large || block instanceof GearboxBlock || block instanceof InlineBlock
            || block instanceof GaugeBlock || block instanceof GovernorBlock || block instanceof HandCrankBlock) {
            return SMALL;
        }
        return NORMAL;
    }
}
