package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.core.Wrenches;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import fr.pioneers.logistics.transport.logistic.LogisticCableTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.11 (spec §6.1) : apercu des roles a la cle. Cle en main, sur chaque fiche de cable logistique a 16 blocs au plus, une petite fleche par
 * role agrege (lu dans le resume synchronise de la tile, 2 bits par face) : rouge entrante (la machine donne au cable), bleue sortante (le cable
 * livre a la machine), violette les deux. Les fiches ne portent plus de couleur de role : un raccord a des roles differents selon le canal.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CablePlugOverlay {

    private static final double RANGE_SQR = 16 * 16;

    private CablePlugOverlay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || LogisticCableTile.CLIENT_PLUGS.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null
            || !Wrenches.isWrench(player.getItemInHand(InteractionHand.MAIN_HAND)) && !Wrenches.isWrench(player.getItemInHand(InteractionHand.OFF_HAND))) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (LogisticCableTile tile : LogisticCableTile.CLIENT_PLUGS) {
            if (tile.isRemoved() || tile.getLevel() != mc.level) {
                continue;
            }
            BlockPos pos = tile.getBlockPos();
            if (pos.distToCenterSqr(cam) > RANGE_SQR) {
                continue;
            }
            BlockState state = tile.getBlockState();
            int roles = tile.roles();
            for (Direction side : Direction.values()) {
                int bits = roles >> (side.ordinal() * 2) & 3;
                if (bits == 0 || !(state.getBlock() instanceof LogisticCableBlock) || LogisticCableBlock.link(state, side) != LogisticCableBlock.Link.PLUG) {
                    continue;
                }
                float r = bits == 1 ? 0.95F : bits == 2 ? 0.25F : 0.7F, g = bits == 1 ? 0.25F : bits == 2 ? 0.45F : 0.3F, b = bits == 1 ? 0.25F : 0.95F;
                arrow(vc, m, n, pos, side, bits, r, g, b);
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    /** Fleche le long du bras de la fiche, a cote de la collerette : vers le cable (entrante), vers la machine (sortante), ou les deux. */
    private static void arrow(VertexConsumer vc, Matrix4f m, Matrix3f n, BlockPos pos, Direction side, int bits, float r, float g, float b) {
        double cx = pos.getX() + 0.5, cy = pos.getY() + 0.5, cz = pos.getZ() + 0.5;
        //Decalee du bras (au-dessus, ou vers le nord pour un bras vertical) pour rester visible
        Direction offset = side.getAxis() == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
        double ox = offset.getStepX() * 0.45, oy = offset.getStepY() * 0.45, oz = offset.getStepZ() * 0.45;
        double sx = side.getStepX(), sy = side.getStepY(), sz = side.getStepZ();
        Vec3 inner = new Vec3(cx + sx * 0.2 + ox, cy + sy * 0.2 + oy, cz + sz * 0.2 + oz);
        Vec3 outer = new Vec3(cx + sx * 0.48 + ox, cy + sy * 0.48 + oy, cz + sz * 0.48 + oz);
        line(vc, m, n, inner, outer, r, g, b);
        if ((bits & 1) != 0) {
            head(vc, m, n, inner, outer, offset, r, g, b);
        }
        if ((bits & 2) != 0) {
            head(vc, m, n, outer, inner, offset, r, g, b);
        }
    }

    /** Pointe de fleche en {@code tip}, venant de {@code from}. */
    private static void head(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 tip, Vec3 from, Direction offset, float r, float g, float b) {
        Vec3 back = from.subtract(tip).normalize().scale(0.08);
        Vec3 wing = new Vec3(offset.getStepX(), offset.getStepY(), offset.getStepZ()).scale(0.06);
        line(vc, m, n, tip, tip.add(back).add(wing), r, g, b);
        line(vc, m, n, tip, tip.add(back).subtract(wing), r, g, b);
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 c, float r, float g, float b) {
        Vec3 d = c.subtract(a).normalize();
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, b, 1).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
        vc.vertex(m, (float) c.x, (float) c.y, (float) c.z).color(r, g, b, 1).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
    }
}
