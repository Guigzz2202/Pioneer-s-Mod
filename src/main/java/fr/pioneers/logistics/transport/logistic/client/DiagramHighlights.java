package fr.pioneers.logistics.transport.logistic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.11 (D2b, spec §1.2) : bouton « Montrer » du maillon faible : les cables du niveau le plus faible du reseau sont cercles d'orange dans le
 * monde pendant 10 s (cadre qui pulse, vu a travers les blocs : visibles meme enfouis). Positions recues dans l'instantane (64 au plus).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class DiagramHighlights {

    private static final long DURATION = 10_000;
    private static long[] cables = new long[0];
    private static long until;

    private DiagramHighlights() {
    }

    static void show(long[] positions) {
        cables = positions.clone();
        until = System.currentTimeMillis() + DURATION;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || cables.length == 0) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now > until) {
            cables = new long[0];
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer vc = buffers.getBuffer(RenderType.lines());
        float pulse = 0.6F + 0.4F * Mth.sin(now / 150F);
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (long packed : cables) {
            BlockPos pos = BlockPos.of(packed);
            if (pos.distToCenterSqr(cam) > 96 * 96) {
                continue;
            }
            LevelRenderer.renderLineBox(pose, vc, new AABB(pos).inflate(0.02), 1.0F, 0.62F * pulse, 0.1F, 1.0F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
