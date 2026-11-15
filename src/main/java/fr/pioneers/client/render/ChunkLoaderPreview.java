package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.tile.TileEntityChunkLoader;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * Apercu des chunkloaders (bouton de leur interface) : la zone chargee dessinee dans le monde, chunk par chunk, avec un voile violet
 * sur son pourtour. Un clic sur le bouton l'affiche ou le masque.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChunkLoaderPreview {

    private ChunkLoaderPreview() {
    }

    private record Key(ResourceKey<Level> dimension, BlockPos pos) {}

    /** Apercus affiches : position du chunkloader -> rayon (en chunks). */
    private static final Map<Key, Integer> SHOWN = new HashMap<>();

    public static boolean isShown(Level level, BlockPos pos) {
        return SHOWN.containsKey(new Key(level.dimension(), pos));
    }

    public static void toggle(Level level, BlockPos pos, int radius) {
        Key key = new Key(level.dimension(), pos.immutable());
        if (SHOWN.remove(key) == null) {
            SHOWN.put(key, radius);
        }
    }

    /** Met a jour le rayon d'un apercu affiche (l'interface est ouverte). */
    public static void update(Level level, BlockPos pos, int radius) {
        SHOWN.computeIfPresent(new Key(level.dimension(), pos), (k, r) -> radius);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || SHOWN.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null) {
            return;
        }
        //Chunkloader casse : son apercu disparait
        SHOWN.keySet().removeIf(key -> key.dimension() == level.dimension() && level.isLoaded(key.pos())
                                       && !(level.getBlockEntity(key.pos()) instanceof TileEntityChunkLoader));
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        double minY = Math.max(level.getMinBuildHeight(), Math.floor(camera.y) - 24), maxY = Math.min(level.getMaxBuildHeight(), Math.floor(camera.y) + 40);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        //Voile sur le pourtour de la zone
        VertexConsumer walls = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        for (Map.Entry<Key, Integer> entry : SHOWN.entrySet()) {
            if (entry.getKey().dimension() != level.dimension()) {
                continue;
            }
            int cx = entry.getKey().pos().getX() >> 4, cz = entry.getKey().pos().getZ() >> 4, r = entry.getValue();
            float x0 = (cx - r) * 16, x1 = (cx + r + 1) * 16, z0 = (cz - r) * 16, z1 = (cz + r + 1) * 16;
            wall(walls, matrix, x0, z0, x1, z0, (float) minY, (float) maxY);
            wall(walls, matrix, x1, z0, x1, z1, (float) minY, (float) maxY);
            wall(walls, matrix, x1, z1, x0, z1, (float) minY, (float) maxY);
            wall(walls, matrix, x0, z1, x0, z0, (float) minY, (float) maxY);
        }
        buffers.endBatch(RenderType.lightning());
        //Grille des chunks charges
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        for (Map.Entry<Key, Integer> entry : SHOWN.entrySet()) {
            if (entry.getKey().dimension() != level.dimension()) {
                continue;
            }
            BlockPos pos = entry.getKey().pos();
            int cx = pos.getX() >> 4, cz = pos.getZ() >> 4, r = entry.getValue();
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    boolean center = dx == 0 && dz == 0;
                    AABB box = new AABB((cx + dx) * 16, minY, (cz + dz) * 16, (cx + dx + 1) * 16, maxY, (cz + dz + 1) * 16).deflate(0.02);
                    LevelRenderer.renderLineBox(pose, lines, box, center ? 0.95F : 0.65F, center ? 0.85F : 0.45F, 1, center ? 0.9F : 0.6F);
                }
            }
            LevelRenderer.renderLineBox(pose, lines, new AABB(pos).inflate(0.05), 1, 1, 1, 1);
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** Pan vertical translucide (deux faces), en degrade du bas vers le haut. */
    private static void wall(VertexConsumer buffer, Matrix4f matrix, float ax, float az, float bx, float bz, float y0, float y1) {
        float r = 0.6F, g = 0.42F, b = 1;
        buffer.vertex(matrix, ax, y0, az).color(r, g, b, 0.22F).endVertex();
        buffer.vertex(matrix, bx, y0, bz).color(r, g, b, 0.22F).endVertex();
        buffer.vertex(matrix, bx, y1, bz).color(r, g, b, 0.0F).endVertex();
        buffer.vertex(matrix, ax, y1, az).color(r, g, b, 0.0F).endVertex();
        buffer.vertex(matrix, ax, y1, az).color(r, g, b, 0.0F).endVertex();
        buffer.vertex(matrix, bx, y1, bz).color(r, g, b, 0.0F).endVertex();
        buffer.vertex(matrix, bx, y0, bz).color(r, g, b, 0.22F).endVertex();
        buffer.vertex(matrix, ax, y0, az).color(r, g, b, 0.22F).endVertex();
    }
}
