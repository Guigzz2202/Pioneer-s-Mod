package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.kinetics.machines.MechanicalMachineTile;
import fr.pioneers.tech.kinetics.machines.MeshItem;
import fr.pioneers.tech.mechanics.HandSieveBlock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

/**
 * Objets poses sur les machines : buche qui avance dans la scie, grain dans la tremie de la meule, pierres
 * entre les machoires ; sediment et tamis du tamis a main et du tamis a secousses (dessines avec la texture du bloc verse).
 */
public final class MachineItems {

    private MachineItems() {
    }

    static float faceDegrees(Direction facing) {
        return switch (facing) {
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
    }

    static void draw(MechanicalMachineTile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        ItemStack work = tile.workpiece();
        MachineType type = tile.type();
        float progress = tile.progress();
        if (type == MachineType.SIFTER) {
            KDraw d = new KDraw(pose, buffers, light, overlay);
            d.push().face(tile.facing());
            float shake = (float) Math.sin(Math.toRadians(tile.angle(0))) * 1.6F;
            d.translate(0, 0, shake);
            //1.0.2-beta : le sediment est etale sur la toile du caisson (MachineVisuals.sifter)
            sediment(d, work, MachineVisuals.SIFTER_MESH_Y, 3.4F, 2.9F, 12.6F, 13.1F);
            d.pop();
            return;
        }
        if (work.isEmpty() || type == MachineType.LATHE || type == MachineType.BELLOWS || type == MachineType.NORIA) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-faceDegrees(tile.facing())));
        pose.translate(-0.5, 0, -0.5);
        switch (type) {
            case SAW -> {
                pose.translate(0.5, 0.97, 0.1 + 0.8 * progress);
                pose.scale(0.55F, 0.55F, 0.55F);
            }
            case MILLSTONE -> {
                pose.translate(0.5, 0.96, 0.5);
                pose.mulPose(Axis.XP.rotationDegrees(90));
                pose.scale(0.3F, 0.3F, 0.3F);
            }
            case JAW_CRUSHER -> {
                //1.0.2-beta : la pierre descend dans le V, entre la machoire fixe (arriere) et la machoire mobile
                pose.translate(0.5, 0.86 - 0.4 * progress, 0.34 + 0.04 * progress);
                pose.scale(0.32F, 0.32F, 0.32F);
            }
            default -> {
            }
        }
        Minecraft.getInstance().getItemRenderer().renderStatic(work, ItemDisplayContext.FIXED, light, overlay, pose, buffers, tile.getLevel(), 0);
        pose.popPose();
    }

    /** Couche de sediment (texture du bloc verse) dont l'epaisseur suit la quantite. */
    static void sediment(KDraw d, ItemStack stack, float y, float x0, float z0, float x1, float z1) {
        if (stack.isEmpty() || !(stack.getItem() instanceof BlockItem block)) {
            return;
        }
        TextureAtlasSprite sprite = Minecraft.getInstance().getBlockRenderer().getBlockModel(block.getBlock().defaultBlockState()).getParticleIcon();
        KDraw.Tex tex = KDraw.of(sprite);
        float h = 0.4F + Math.min(1, stack.getCount() / 16F) * 2.2F;
        d.box(x0, y, z0, x1, y + h, z1, tex, tex);
    }

    static void handSieve(HandSieveBlock.Tile tile, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        KDraw d = new KDraw(pose, buffers, light, overlay);
        if (tile.mesh.getItem() instanceof MeshItem mesh) {
            String tex = mesh.tier >= 2 ? "copper" : "rope";
            for (int k = 0; k < 6; k++) {
                d.box(2.5F + k * 2.0F, 10.0F, 2.5F, 3.1F + k * 2.0F, 10.4F, 13.5F, tex, tex);
                d.box(2.5F, 10.05F, 2.5F + k * 2.0F, 13.5F, 10.35F, 3.1F + k * 2.0F, tex, tex);
            }
        }
        sediment(d, tile.sediment, 10.4F, 2.6F, 2.6F, 13.4F, 13.4F);
    }
}
