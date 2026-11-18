package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.ItemZonePlanner;
import fr.pioneers.logistics.robotics.RoboticOrder;
import fr.pioneers.logistics.robotics.logistics.BlockLogisticChest;
import fr.pioneers.logistics.robotics.logistics.BlockLogisticMarker;
import fr.pioneers.logistics.robotics.logistics.LogisticPoint;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Aides visuelles de la robotique dans le monde, avec le planificateur de zone ou une balise en main (ou pendant une visee) :
 * <ul>
 *     <li>les zones de travail (cadres de la couleur de leur tache) et la zone en cours de trace ;</li>
 *     <li>les ordres de transport (ruban anime de la source vers la cible) ;</li>
 *     <li>la face d'acces de chaque balise, de la couleur de son role ;</li>
 *     <li>pendant une visee, la face pointee.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoboticsOverlay {

    private RoboticsOverlay() {
    }

    /** Le joueur tient-il un outil de la robotique ? */
    public static boolean holdingTool(LocalPlayer player) {
        return isTool(player.getMainHandItem()) || isTool(player.getOffhandItem());
    }

    private static boolean isTool(ItemStack stack) {
        return stack.getItem() instanceof ItemZonePlanner || stack.getItem() instanceof BlockItem block
                                                             && (block.getBlock() instanceof BlockLogisticMarker || block.getBlock() instanceof BlockLogisticChest);
    }

    @Nullable
    private static ItemStack planner(LocalPlayer player) {
        if (player.getMainHandItem().getItem() instanceof ItemZonePlanner) {
            return player.getMainHandItem();
        }
        return player.getOffhandItem().getItem() instanceof ItemZonePlanner ? player.getOffhandItem() : null;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        LocalPlayer player = minecraft.player;
        if (level == null || player == null) {
            return;
        }
        //Planificateur 3D en vue libre : il dessine la zone dans le vrai monde
        if (minecraft.screen instanceof FreeCamScreen planner && planner.isActive()) {
            planner.renderWorld(event.getPoseStack(), event.getCamera().getPosition());
            return;
        }
        boolean picking = ClientRobotics.isPicking();
        if (!picking && !holdingTool(player)) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        float time = (float) RenderClock.time(event.getPartialTick());   //1.0.5-beta (lot 29) : horloge de rendu continue
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        BlockPos near = player.blockPosition();
        //Zones
        for (RoboticOrder order : ClientRobotics.orders()) {
            if (order.type == RoboticOrder.Type.ZONE && order.min != null && order.max != null && order.anchor().distSqr(near) < 160 * 160) {
                int color = order.task.color;
                float alpha = order.paused ? 0.35F : 0.85F;
                LevelRenderer.renderLineBox(pose, lines, new AABB(order.min, order.max.offset(1, 1, 1)).inflate(0.02), (color >> 16 & 255) / 255F,
                      (color >> 8 & 255) / 255F, (color & 255) / 255F, alpha);
            } else if (order.type == RoboticOrder.Type.TRANSFER) {
                if (order.source != null && order.source.pos().distSqr(near) < 128 * 128) {
                    face(pose, lines, order.source, 0.3F, 0.85F, 1);
                }
                if (order.target != null && order.target.pos().distSqr(near) < 128 * 128) {
                    face(pose, lines, order.target, 1, 0.6F, 0.2F);
                }
            }
        }
        //Faces d'acces des points logistiques
        for (LogisticPoint point : LogisticsRegistry.get(level).allPoints()) {
            if (point.isPointRemoved() || point.getPointPos().distSqr(near) > 64 * 64) {
                continue;
            }
            int color = point.getConfig().role.color;
            face(pose, lines, point.endpoint(), (color >> 16 & 255) / 255F, (color >> 8 & 255) / 255F, (color & 255) / 255F);
        }
        //Zone en cours de trace, et visee
        BlockHitResult hit = minecraft.hitResult instanceof BlockHitResult block && block.getType() == HitResult.Type.BLOCK ? block : null;
        ItemStack planner = planner(player);
        BlockPos corner = planner == null ? null : ItemZonePlanner.corner(planner);
        if (corner != null) {
            BlockPos other = hit != null ? hit.getBlockPos() : corner;
            AABB box = new AABB(Math.min(corner.getX(), other.getX()), Math.min(corner.getY(), other.getY()), Math.min(corner.getZ(), other.getZ()),
                  Math.max(corner.getX(), other.getX()) + 1, Math.max(corner.getY(), other.getY()) + 1, Math.max(corner.getZ(), other.getZ()) + 1);
            float blink = 0.6F + 0.4F * (float) Math.sin(time * 0.25);
            LevelRenderer.renderLineBox(pose, lines, box.inflate(0.03), 0.35F, 1, 0.75F, blink);
            LevelRenderer.renderLineBox(pose, lines, new AABB(corner).inflate(0.05), 1, 1, 1, 1);
        }
        if (picking && hit != null) {
            face(pose, lines, new Endpoint(hit.getBlockPos(), hit.getDirection()), 1, 1, 1);
            LevelRenderer.renderLineBox(pose, lines, new AABB(hit.getBlockPos()).inflate(0.01), 1, 1, 1, 0.5F);
        }
        buffers.endBatch(RenderType.lines());
        //Rubans des transports
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (RoboticOrder order : ClientRobotics.orders()) {
            if (order.type == RoboticOrder.Type.TRANSFER && order.source != null && order.target != null && order.target.pos().distSqr(near) < 128 * 128) {
                arrow(glow, matrix, face(order.source), face(order.target), time, order.paused);
            }
        }
        buffers.endBatch(RenderType.lightning());
        pose.popPose();
    }

    private static Vec3 face(Endpoint endpoint) {
        if (endpoint.side() == null) {
            return Vec3.atCenterOf(endpoint.pos()).add(0, 0.55, 0);
        }
        return Vec3.atCenterOf(endpoint.pos()).add(Vec3.atLowerCornerOf(endpoint.side().getNormal()).scale(0.55));
    }

    /** Cadre sur la face d'un bloc (ou autour du bloc entier si l'acces est par toutes les faces). */
    private static void face(PoseStack pose, VertexConsumer lines, Endpoint endpoint, float r, float g, float b) {
        Direction side = endpoint.side();
        if (side == null) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(endpoint.pos()).inflate(0.03), r, g, b, 0.8F);
            return;
        }
        Vec3 n = Vec3.atLowerCornerOf(side.getNormal());
        AABB box = new AABB(endpoint.pos()).deflate(0.06).move(n.scale(0.5)).contract(n.x * 0.88, n.y * 0.88, n.z * 0.88);
        LevelRenderer.renderLineBox(pose, lines, box, r, g, b, 1);
    }

    /** Ruban de la source a la destination, avec des tirets qui avancent. */
    private static void arrow(VertexConsumer buffer, Matrix4f matrix, Vec3 a, Vec3 b, float time, boolean paused) {
        Vec3 dir = b.subtract(a);
        double length = dir.length();
        if (length < 0.1) {
            return;
        }
        Vec3 side = dir.cross(new Vec3(0, 1, 0));
        side = side.lengthSqr() < 1e-4 ? new Vec3(0.06, 0, 0) : side.normalize().scale(0.06);
        int dashes = (int) Math.max(2, length * 2);
        float offset = paused ? 0 : (time * 0.08F) % 1;
        for (int i = 0; i < dashes; i++) {
            double t0 = (i + offset) / dashes, t1 = Math.min(1, t0 + 0.5 / dashes);
            Vec3 p0 = a.add(dir.scale(t0)), p1 = a.add(dir.scale(t1));
            float alpha = (float) ((paused ? 0.15 : 0.35) + 0.5 * t0);
            quad(buffer, matrix, p0, p1, side, alpha);
            quad(buffer, matrix, p0, p1, new Vec3(0, 0.06, 0), alpha);
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f matrix, Vec3 a, Vec3 b, Vec3 side, float alpha) {
        float r = 0.35F, g = 0.85F, bl = 1;
        Vec3[] q = {a.add(side), b.add(side), b.subtract(side), a.subtract(side)};
        for (int k = 0; k < 4; k++) {
            buffer.vertex(matrix, (float) q[k].x, (float) q[k].y, (float) q[k].z).color(r, g, bl, alpha).endVertex();
        }
        for (int k = 3; k >= 0; k--) {
            buffer.vertex(matrix, (float) q[k].x, (float) q[k].y, (float) q[k].z).color(r, g, bl, alpha).endVertex();
        }
    }
}
