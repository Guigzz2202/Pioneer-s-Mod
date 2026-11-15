package fr.pioneers.logistics.rail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.logistics.rail.block.ConductorBlock;
import fr.pioneers.logistics.rail.net.Electrification;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.20.2 : fils de la catenaire entre deux poteaux : chaque poteau porte le fil au-dessus de l'axe de la voie voisine ; le fil va au poteau
 * suivant sur la meme ligne (meme axe, jusqu'a 16 blocs), avec son porteur qui pend un peu plus haut et ses pendules. Poteaux releves autour
 * du joueur toutes les secondes.
 * <p>
 * 0.20.46 (audit) : releve par sections de chunk (sautees si leur palette n'a aucun conducteur).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CatenaryRenderer {

    private static final int RADIUS = 40, HEIGHT = 6, SPAN = 16;

    /** Point de fil d'un poteau : axe de la voie (x ou z) et coordonnees du point. */
    private record Anchor(boolean alongZ, double x, double y, double z) {}

    private record Span(Vec3 a, Vec3 b) {}

    private static final List<Span> SPANS = new ArrayList<>();
    private static long scannedAt = Long.MIN_VALUE;
    private static BlockPos scannedFrom = BlockPos.ZERO;

    /** 0.20.46 (audit) : une section peut-elle contenir un poteau (palette) ? */
    private static final Predicate<BlockState> MAY_HOLD_POLE = st -> st.getBlock() instanceof ConductorBlock;

    private CatenaryRenderer() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SOLID_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        long now = level.getGameTime();
        //0.20.46 (audit) : aussi si l'heure recule (autre dimension) : les fils de l'ancien monde ne restent pas affiches
        if (now - scannedAt >= 20 || now < scannedAt || !player.blockPosition().closerThan(scannedFrom, 8)) {
            scan(level, player.blockPosition());
            scannedAt = now;
            scannedFrom = player.blockPosition();
        }
        if (SPANS.isEmpty()) {
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
        for (Span s : SPANS) {
            //Fil de contact (droit), porteur (chainette au-dessus) et pendules
            line(vc, m, n, s.a, s.b, 0.16F, 0.14F, 0.12F);
            int steps = 8;
            Vec3 prev = null;
            double rise = 0.22;
            for (int i = 0; i <= steps; i++) {
                double k = i / (double) steps;
                double sag = 0.16 * (1 - Math.pow(2 * k - 1, 2));
                Vec3 p = s.a.lerp(s.b, k).add(0, rise + 0.16 - sag, 0);
                if (prev != null) {
                    line(vc, m, n, prev, p, 0.22F, 0.2F, 0.18F);
                }
                if (i > 0 && i < steps) {
                    line(vc, m, n, s.a.lerp(s.b, k), p, 0.35F, 0.33F, 0.3F);
                }
                prev = p;
            }
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 d = b.subtract(a).normalize();
        vc.vertex(m, (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 1).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
        vc.vertex(m, (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 1).normal(n, (float) d.x, (float) d.y, (float) d.z).endVertex();
    }

    /**
     * Releve des poteaux autour de {@code center}. 0.20.46 (audit) : parcours par sections de chunk chargees ; une section vide ou dont la
     * palette ne contient aucun conducteur est sautee (avant : 85 000 blocs lus chaque seconde, dans toutes les dimensions). Memes poteaux,
     * dans le meme ordre (x, puis z, puis y) que l'ancien balayage.
     */
    private static void scan(ClientLevel level, BlockPos center) {
        SPANS.clear();
        List<Anchor> anchors = new ArrayList<>();
        List<long[]> found = new ArrayList<>();
        int x0 = center.getX() - RADIUS, x1 = center.getX() + RADIUS, z0 = center.getZ() - RADIUS, z1 = center.getZ() + RADIUS;
        int y0 = center.getY() - HEIGHT, y1 = center.getY() + HEIGHT;
        int sy0 = Math.max(level.getMinSection(), SectionPos.blockToSectionCoord(y0)), sy1 = Math.min(level.getMaxSection() - 1, SectionPos.blockToSectionCoord(y1));
        for (int cx = SectionPos.blockToSectionCoord(x0); cx <= SectionPos.blockToSectionCoord(x1); cx++) {
            for (int cz = SectionPos.blockToSectionCoord(z0); cz <= SectionPos.blockToSectionCoord(z1); cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (int sy = sy0; sy <= sy1; sy++) {
                    LevelChunkSection section = chunk.getSection(chunk.getSectionIndexFromSectionY(sy));
                    if (section.hasOnlyAir() || !section.maybeHas(MAY_HOLD_POLE)) {
                        continue;
                    }
                    int bx = SectionPos.sectionToBlockCoord(cx), by = SectionPos.sectionToBlockCoord(sy), bz = SectionPos.sectionToBlockCoord(cz);
                    for (int y = Math.max(y0, by); y <= Math.min(y1, by + 15); y++) {
                        for (int z = Math.max(z0, bz); z <= Math.min(z1, bz + 15); z++) {
                            for (int x = Math.max(x0, bx); x <= Math.min(x1, bx + 15); x++) {
                                BlockState st = section.getBlockState(x & 15, y & 15, z & 15);
                                if (st.getBlock() instanceof ConductorBlock cb && cb.kind == Electrification.Kind.POLE) {
                                    Direction to = ConductorBlock.toTrack(st);
                                    double ax = x + 0.5 + to.getStepX(), az = z + 0.5 + to.getStepZ();
                                    found.add(new long[]{x, z, y, anchors.size()});
                                    anchors.add(new Anchor(to.getAxis() == Direction.Axis.X, ax, y + (InfraModels.WIRE_Y + 0.15) / 16.0, az));
                                }
                            }
                        }
                    }
                }
            }
        }
        //Ordre de l'ancien balayage (x, z, y croissants) : a egalite de distance, le meme poteau est retenu
        if (anchors.size() > 1) {
            found.sort((a, b) -> a[0] != b[0] ? Long.compare(a[0], b[0]) : a[1] != b[1] ? Long.compare(a[1], b[1]) : Long.compare(a[2], b[2]));
            List<Anchor> sorted = new ArrayList<>(anchors.size());
            for (long[] f : found) {
                sorted.add(anchors.get((int) f[3]));
            }
            anchors = sorted;
        }
        //Chaque point de fil se relie au plus proche qui le suit sur la meme ligne (meme axe de voie)
        for (Anchor a : anchors) {
            Anchor best = null;
            double bestD = Double.MAX_VALUE;
            for (Anchor b : anchors) {
                if (b == a || b.alongZ != a.alongZ || Math.abs(b.y - a.y) > 1.01) {
                    continue;
                }
                double along = a.alongZ ? b.z - a.z : b.x - a.x;
                double across = a.alongZ ? Math.abs(b.x - a.x) : Math.abs(b.z - a.z);
                if (along > 0.5 && along <= SPAN && across < 0.01 && along < bestD) {
                    best = b;
                    bestD = along;
                }
            }
            if (best != null) {
                SPANS.add(new Span(new Vec3(a.x, a.y, a.z), new Vec3(best.x, best.y, best.z)));
            }
        }
    }
}
