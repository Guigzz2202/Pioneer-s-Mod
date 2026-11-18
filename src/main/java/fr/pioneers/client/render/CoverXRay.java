package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.Pioneers;
import fr.pioneers.core.Wrenches;
import fr.pioneers.core.block.Covers;
import fr.pioneers.tech.kinetics.parts.ShaftBlock;
import fr.pioneers.logistics.transport.logistic.LogisticCableBlock;
import java.util.OptionalDouble;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.4-beta (lot 30, Guillaume : « la cle a molette permet de voir et config les cables ») : tant que le joueur tient une CLE A MOLETTE,
 * les cables logistiques et les arbres CACHES ({@code Covers#COVERED}) a moins de {@link #RADIUS} blocs se voient au travers de leur cache :
 * contour de leur forme nue (coeur, bras, fiches ; arbre) dessine en traits sans test de profondeur (cables : cyan, arbres : ambre). La
 * cle garde son role habituel au clic (cable : couper / retablir le cote vise, Maj + cle : le diagramme). Shaders vanilla seulement ; rien
 * a synchroniser (etat et tuile deja chez le client).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class CoverXRay {

    /** Rayon (blocs) autour du joueur. */
    public static final int RADIUS = 16;

    private CoverXRay() {
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null || !Wrenches.isWrench(player.getMainHandItem()) && !Wrenches.isWrench(player.getOffhandItem())) {
            return;
        }
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        VertexConsumer lines = buffers.getBuffer(Types.LINES);
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        BlockPos center = player.blockPosition();
        int r2 = RADIUS * RADIUS, c0 = (center.getX() - RADIUS) >> 4, c1 = (center.getX() + RADIUS) >> 4, d0 = (center.getZ() - RADIUS) >> 4,
              d1 = (center.getZ() + RADIUS) >> 4;
        boolean any = false;
        for (int cx = c0; cx <= c1; cx++) {
            for (int cz = d0; cz <= d1; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, false);
                if (chunk == null) {
                    continue;
                }
                for (BlockEntity be : chunk.getBlockEntities().values()) {
                    BlockState state = be.getBlockState();
                    BlockPos pos = be.getBlockPos();
                    if (!Covers.covered(state) || pos.distSqr(center) > r2) {
                        continue;
                    }
                    boolean cable = state.getBlock() instanceof LogisticCableBlock;
                    if (!cable && !(state.getBlock() instanceof ShaftBlock)) {
                        continue;
                    }
                    BlockState bare = state.setValue(Covers.COVERED, false).setValue(Covers.FRAMED, false);
                    VoxelShape shape = bare.getShape(level, pos);
                    float red = cable ? 0.35F : 1F, green = cable ? 0.95F : 0.72F, blue = cable ? 1F : 0.25F;
                    shape.forAllBoxes((x0, y0, z0, x1, y1, z1) -> LevelRenderer.renderLineBox(pose, lines, pos.getX() + x0, pos.getY() + y0,
                          pos.getZ() + z0, pos.getX() + x1, pos.getY() + y1, pos.getZ() + z1, red, green, blue, 0.9F));
                    any = true;
                }
            }
        }
        pose.popPose();
        if (any) {
            buffers.endBatch(Types.LINES);
        }
    }

    /** Traits sans test de profondeur (memes etats que les rayons X du planificateur robotique). */
    private static final class Types extends RenderType {

        static final RenderType LINES = create("pioneers_cover_xray_lines", DefaultVertexFormat.POSITION_COLOR_NORMAL, VertexFormat.Mode.LINES, 1024,
              false, false, CompositeState.builder().setShaderState(RENDERTYPE_LINES_SHADER).setLineState(new LineStateShard(OptionalDouble.empty()))
                    .setLayeringState(VIEW_OFFSET_Z_LAYERING).setTransparencyState(TRANSLUCENT_TRANSPARENCY).setWriteMaskState(COLOR_WRITE)
                    .setCullState(NO_CULL).setDepthTestState(NO_DEPTH_TEST).createCompositeState(false));

        private Types() {
            super("pioneers_cover_xray", DefaultVertexFormat.POSITION_COLOR, VertexFormat.Mode.QUADS, 256, false, false, () -> {
            }, () -> {
            });
        }
    }
}
