package fr.pioneers.logistics.rail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.core.Wrenches;
import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.block.RailSignalBlock;
import fr.pioneers.logistics.rail.block.StationBlock;
import fr.pioneers.logistics.rail.block.TrackSide;
import fr.pioneers.logistics.rail.block.TrackSideItem;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.logistics.rail.track.TrackBlock;
import fr.pioneers.logistics.rail.train.RailCarItem;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 0.20.1 : sens des voies, visible quand on tient un objet du chemin de fer (voie, vehicule, signal, gare, clé) : une fleche sur la voie
 * devant chaque signal (jaune : canton, bleu : entree) et chaque gare (verte) montre le sens des trains qu'ils commandent. Avec un objet de
 * bord de voie ({@link TrackSideItem}) visant une voie droite : fantome du bloc a sa future place, cadre vert (libre) ou rouge, et fleche
 * blanche du sens choisi (regard ; Maj : inverse) ; pour un quai ou une grue, la fleche montre le cote ou il sera pose.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TrackSidePreview {

    private static final int RADIUS = 24, HEIGHT = 6;
    private static final int C_BLOCK = 0xFFD24A, C_CHAIN = 0x4AC8FF, C_STATION = 0x3FD0A0, C_PICK = 0xFFFFFF, C_BAD = 0xFF4A3A;

    private record Marker(BlockPos track, Direction heading, int color) {}

    private static final List<Marker> MARKERS = new ArrayList<>();
    private static long scannedAt = Long.MIN_VALUE;
    private static BlockPos scannedFrom = BlockPos.ZERO;

    private TrackSidePreview() {
    }

    /** Objet qui fait voir le sens des voies. */
    private static boolean railItem(ItemStack stack) {
        return stack.getItem() instanceof TrackSideItem || stack.getItem() instanceof RailCarItem
              || stack.getItem() instanceof BlockItem bi && bi.getBlock() instanceof TrackBlock || Wrenches.isWrench(stack);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null) {
            return;
        }
        ItemStack held = player.getMainHandItem();
        if (!railItem(held)) {
            held = player.getOffhandItem();
            if (!railItem(held)) {
                return;
            }
        }
        long now = level.getGameTime();
        if (now - scannedAt >= 10 || !player.blockPosition().closerThan(scannedFrom, 4)) {
            scan(level, player.blockPosition());
            scannedAt = now;
            scannedFrom = player.blockPosition();
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer quads = buffers.getBuffer(RenderType.debugQuads());
        for (Marker m : MARKERS) {
            arrow(pose, quads, m.track, m.heading, m.color, 0.6F, 0, 0.2F, 0.7F);
        }
        //Pose visee
        TrackSide.Spot spot = null;
        boolean straight = true;
        TrackSideItem item = held.getItem() instanceof TrackSideItem t ? t : null;
        BlockPos aimed = null;
        if (item != null && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK
              && level.getBlockState(hit.getBlockPos()).getBlock() instanceof TrackBlock) {
            aimed = hit.getBlockPos();
            spot = item.spot(level, aimed, player, hit.getLocation());
            straight = spot != null;
            if (spot != null && spot.toTrack().getAxis().isHorizontal()) {
                arrow(pose, quads, spot.track(), item.directional ? spot.heading() : spot.toTrack().getOpposite(),
                      spot.free() ? C_PICK : C_BAD, 0.85F, 0.004F, 0, 1);
            }
        }
        buffers.endBatch(RenderType.debugQuads());
        if (aimed != null) {
            VertexConsumer lines = buffers.getBuffer(RenderType.lines());
            if (!straight) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(aimed).deflate(0.02), 1, 0.3F, 0.25F, 0.9F);
            } else {
                AABB box = new AABB(spot.pos()).deflate(0.02);
                if (spot.free()) {
                    LevelRenderer.renderLineBox(pose, lines, box, 0.4F, 1, 0.7F, 0.9F);
                } else {
                    LevelRenderer.renderLineBox(pose, lines, box, 1, 0.3F, 0.25F, 0.9F);
                }
            }
            buffers.endBatch(RenderType.lines());
            if (straight && spot.free()) {
                ghost(mc, pose, buffers, item, spot);
            }
        }
        pose.popPose();
    }

    /** Fantome du bloc a sa future place (meme orientation que la pose). */
    private static void ghost(Minecraft mc, PoseStack pose, MultiBufferSource.BufferSource buffers, TrackSideItem item, TrackSide.Spot spot) {
        BlockState state = item.getBlock().defaultBlockState();
        if (state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && spot.toTrack().getAxis().isHorizontal()) {
            state = state.setValue(BlockStateProperties.HORIZONTAL_FACING, item.directional ? spot.heading() : spot.toTrack().getOpposite());
        }
        if (state.hasProperty(TrackBlocks.ASPECT)) {
            state = state.setValue(TrackBlocks.ASPECT, 0);
        }
        BlockRenderDispatcher dispatcher = mc.getBlockRenderer();
        VertexConsumer vc = new AlphaVertexConsumer(buffers.getBuffer(Sheets.translucentCullBlockSheet()), 0.55F);
        ModelData data = item.directional || !spot.toTrack().getAxis().isHorizontal() ? ModelData.EMPTY : RailBakedModel.armData(spot.toTrack());
        pose.pushPose();
        pose.translate(spot.pos().getX(), spot.pos().getY(), spot.pos().getZ());
        dispatcher.getModelRenderer().renderModel(pose.last(), vc, state, dispatcher.getBlockModel(state), 0.85F, 1, 0.95F, LightTexture.FULL_BRIGHT,
              OverlayTexture.NO_OVERLAY, data, null);
        pose.popPose();
        buffers.endBatch(Sheets.translucentCullBlockSheet());
    }

    /** Fleche a plat sur le dessus des rails, vers {@code heading} (repere du bloc de voie). */
    /**
     * @param shift decalage vers la droite du sens (blocs) : deux signaux opposes sur la meme voie restent lisibles
     * @param size  echelle de la fleche
     */
    private static void arrow(PoseStack pose, VertexConsumer vc, BlockPos track, Direction heading, int rgb, float alpha, float lift, float shift,
          float size) {
        if (alpha <= 0) {
            return;
        }
        int r = rgb >> 16 & 255, g = rgb >> 8 & 255, b = rgb & 255, a = (int) (alpha * 255);
        Matrix4f m = pose.last().pose();
        float y = track.getY() + 4.1F / 16 + lift;
        //Repere local : avance = -z (nord) ; tourne selon le sens
        float[][] shaft = {{-0.07F, 0.9F}, {0.07F, 0.9F}, {0.07F, 0.42F}, {-0.07F, 0.42F}};
        float[][] head = {{-0.26F, 0.44F}, {0.26F, 0.44F}, {0, 0.08F}, {0, 0.08F}};
        for (float[][] poly : new float[][][]{shaft, head}) {
            for (float[] p : poly) {
                float lx = p[0] * size + shift, lz = (p[1] - 0.5F) * size;
                float wx, wz;
                switch (heading) {
                    case SOUTH -> {
                        wx = -lx;
                        wz = -lz;
                    }
                    case EAST -> {
                        wx = -lz;
                        wz = lx;
                    }
                    case WEST -> {
                        wx = lz;
                        wz = -lx;
                    }
                    default -> {
                        wx = lx;
                        wz = lz;
                    }
                }
                vc.vertex(m, track.getX() + 0.5F + wx, y, track.getZ() + 0.5F + wz).color(r, g, b, a).endVertex();
            }
        }
    }

    /** Signaux et gares autour du joueur. */
    private static void scan(ClientLevel level, BlockPos center) {
        MARKERS.clear();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos();
        for (int dx = -RADIUS; dx <= RADIUS; dx++) {
            for (int dz = -RADIUS; dz <= RADIUS; dz++) {
                for (int dy = -HEIGHT; dy <= HEIGHT; dy++) {
                    p.set(center.getX() + dx, center.getY() + dy, center.getZ() + dz);
                    BlockState st = level.getBlockState(p);
                    int color;
                    if (st.getBlock() instanceof RailSignalBlock sig) {
                        color = sig.type == RailNetwork.SignalType.CHAIN ? C_CHAIN : C_BLOCK;
                    } else if (st.getBlock() instanceof StationBlock) {
                        color = C_STATION;
                    } else {
                        continue;
                    }
                    Direction heading = st.getValue(BlockStateProperties.HORIZONTAL_FACING);
                    BlockPos track = TrackSide.track(p, heading);
                    if (level.getBlockState(track).getBlock() instanceof TrackBlock) {
                        MARKERS.add(new Marker(track.immutable(), heading, color));
                    }
                }
            }
        }
    }

    /** Ecrit les sommets du modele avec une transparence imposee. */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            delegate.color(red, green, blue, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float a, float u, float v, int overlay, int light,
              float nx, float ny, float nz) {
            delegate.vertex(x, y, z, red, green, blue, a * alpha, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int a) {
            delegate.defaultColor(red, green, blue, (int) (a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
