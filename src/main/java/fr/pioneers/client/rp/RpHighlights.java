package fr.pioneers.client.rp;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.core.Wrenches;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.SkillHooks;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP) : reperes visuels de deux vocations, calcules cote client une fois par seconde (sections de chunk sautees quand leur palette
 * ne contient aucun bloc cherche) : Oeil de chiffonnier (Recuperateur : les blocs delabres luisent faiblement dans 24 blocs) et Flux a vue
 * (Logisticien, cle en main : cables, voies et tubes surlignes dans 16 blocs).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class RpHighlights {

    private static final List<BlockPos> DERELICT = new ArrayList<>();
    private static final List<BlockPos> FLOW = new ArrayList<>();
    private static final int MAX = 2048;

    private RpHighlights() {
    }

    static void tick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || p.tickCount % 20 != 0) {
            return;
        }
        int glow = (int) PioneerApi.modifier(p, "salvager.glow");
        scan(p.level(), p.blockPosition(), glow, s -> s.is(SkillHooks.DERELICT), DERELICT);
        int flow = (int) PioneerApi.modifier(p, "logistician.flow");
        boolean wrench = Wrenches.isWrench(p.getMainHandItem()) || Wrenches.isWrench(p.getOffhandItem());
        scan(p.level(), p.blockPosition(), wrench ? flow : 0, RpHighlights::isFlow, FLOW);
        //Lueur des blocs delabres : quelques etincelles discretes par seconde
        for (int i = 0; i < Math.min(6, DERELICT.size()); i++) {
            BlockPos b = DERELICT.get(p.getRandom().nextInt(DERELICT.size()));
            p.level().addParticle(ParticleTypes.GLOW, b.getX() + p.getRandom().nextDouble(), b.getY() + 1.02, b.getZ() + p.getRandom().nextDouble(),
                  0, 0.01, 0);
        }
    }

    private static boolean isFlow(BlockState s) {
        return s.getBlock() instanceof fr.pioneers.transport.logistic.LogisticCableBlock
               || s.getBlock() instanceof fr.pioneers.rail.track.TrackBlock;
    }

    private static void scan(Level level, BlockPos center, int radius, Predicate<BlockState> test, List<BlockPos> out) {
        out.clear();
        if (radius <= 0) {
            return;
        }
        int r2 = radius * radius;
        int cx0 = (center.getX() - radius) >> 4, cx1 = (center.getX() + radius) >> 4, cz0 = (center.getZ() - radius) >> 4,
              cz1 = (center.getZ() + radius) >> 4;
        for (int cx = cx0; cx <= cx1; cx++) {
            for (int cz = cz0; cz <= cz1; cz++) {
                if (!level.hasChunk(cx, cz)) {
                    continue;
                }
                LevelChunk chunk = level.getChunk(cx, cz);
                LevelChunkSection[] sections = chunk.getSections();
                for (int i = 0; i < sections.length; i++) {
                    LevelChunkSection sec = sections[i];
                    int sy = SectionPos.sectionToBlockCoord(chunk.getSectionYFromSectionIndex(i));
                    if (sec.hasOnlyAir() || sy + 16 < center.getY() - radius || sy > center.getY() + radius || !sec.maybeHas(test)) {
                        continue;
                    }
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                if (test.test(sec.getBlockState(x, y, z))) {
                                    BlockPos b = new BlockPos((cx << 4) + x, sy + y, (cz << 4) + z);
                                    if (b.distSqr(center) <= r2 && out.size() < MAX) {
                                        out.add(b);
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void render(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || FLOW.isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        PoseStack pose = event.getPoseStack();
        Vec3 cam = event.getCamera().getPosition();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        for (BlockPos b : FLOW) {
            LevelRenderer.renderLineBox(pose, lines, new AABB(b).inflate(0.004), 0.35F, 0.75F, 0.95F, 0.55F);
        }
        pose.popPose();
        buffers.endBatch(RenderType.lines());
    }
}
