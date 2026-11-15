package fr.pioneers.client.robotics;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.robotics.works.WorksPlan;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Apercu d'un plan de travaux dans le vrai monde : les blocs qui vont partir (volume rouge translucide, seulement son enveloppe) et
 * les blocs qui vont etre poses (vrais modeles, en fantome). Tesselle une fois dans des tampons GPU a chaque changement du plan ; compte
 * aussi les materiaux a fournir.
 */
final class PlanPreview implements AutoCloseable {

    /** Au-dela, l'apercu se limite (le plan reste entier). */
    private static final int MAX_REMOVED = 400_000, MAX_GHOSTS = 40_000;

    @Nullable
    private VertexBuffer ghosts, removedFaces, buriedFaces;
    private static BufferBuilder ghostBuilder, faceBuilder, buriedBuilder;
    private int buried;
    private BlockPos origin = BlockPos.ZERO;
    private int removed, placed;
    private final List<Map.Entry<Item, Integer>> materials = new ArrayList<>();
    private boolean truncated;

    int removed() {
        return removed;
    }

    int placed() {
        return placed;
    }

    /** Blocs retires sous la surface. */
    int buried() {
        return buried;
    }

    boolean truncated() {
        return truncated;
    }

    /** Materiaux a apporter (objet, nombre), du plus demande au moins demande. */
    List<Map.Entry<Item, Integer>> materials() {
        return materials;
    }

    /** Reconstruit l'apercu du plan (thread de rendu). */
    void build(WorksPlan plan) {
        close();
        removed = placed = buried = 0;
        truncated = false;
        materials.clear();
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        AABB bounds = plan.bounds();
        if (level == null || bounds == null) {
            return;
        }
        origin = BlockPos.containing(bounds.minX, bounds.minY, bounds.minZ);
        //1. Ce qui part, ce qui est pose
        LongOpenHashSet gone = new LongOpenHashSet();
        Long2ObjectOpenHashMap<BlockState> ghostStates = new Long2ObjectOpenHashMap<>();
        Map<Item, Integer> counts = new HashMap<>();
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        for (WorksPlan.Op op : plan.ops) {
            if (op.min == null || op.max == null) {
                continue;
            }
            if (op.kind == WorksPlan.Kind.CLEAR) {
                for (int x = op.min.getX(); x <= op.max.getX(); x++) {
                    for (int z = op.min.getZ(); z <= op.max.getZ(); z++) {
                        for (int y = op.min.getY(); y <= op.max.getY(); y++) {
                            pos.set(x, y, z);
                            consider(level, plan, pos, gone, ghostStates);
                        }
                    }
                }
            } else {
                for (BlockPos at : op.blocks.keySet()) {
                    consider(level, plan, at, gone, ghostStates);
                }
            }
            if (gone.size() > MAX_REMOVED || ghostStates.size() > MAX_GHOSTS) {
                truncated = true;
                break;
            }
        }
        removed = gone.size();
        placed = ghostStates.size();
        for (Long2ObjectOpenHashMap.Entry<BlockState> entry : ghostStates.long2ObjectEntrySet()) {
            BlockPos at = BlockPos.of(entry.getLongKey());
            ItemStack need = WorksPlan.requiredItem(level, at, entry.getValue(), level.getBlockState(at));
            if (!need.isEmpty()) {
                counts.merge(need.getItem(), 1, Integer::sum);
            }
        }
        materials.addAll(counts.entrySet());
        materials.sort((a, b) -> b.getValue() - a.getValue());
        //2. Enveloppe rouge de ce qui part
        if (faceBuilder == null) {
            faceBuilder = new BufferBuilder(1 << 16);
        }
        if (faceBuilder.building()) {
            faceBuilder.discard();
        }
        faceBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        //Ce qui part sous la surface : en orange, visible a travers le terrain
        if (buriedBuilder == null) {
            buriedBuilder = new BufferBuilder(1 << 16);
        }
        if (buriedBuilder.building()) {
            buriedBuilder.discard();
        }
        buriedBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap grounds = new it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap();
        BlockPos.MutableBlockPos next = new BlockPos.MutableBlockPos();
        for (long key : gone) {
            int x = BlockPos.getX(key), y = BlockPos.getY(key), z = BlockPos.getZ(key);
            long column = ((long) x << 32) ^ (z & 0xFFFFFFFFL);
            int ground = grounds.computeIfAbsent(column, k -> level.hasChunk(x >> 4, z >> 4) ? FreeCamScreen.surface(level, x, z) : Integer.MIN_VALUE);
            boolean under = ground != Integer.MIN_VALUE && y <= ground;
            if (under) {
                buried++;
            }
            for (Direction side : Direction.values()) {
                next.set(x + side.getStepX(), y + side.getStepY(), z + side.getStepZ());
                if (!gone.contains(next.asLong())) {
                    face(under ? buriedBuilder : faceBuilder, x - origin.getX(), y - origin.getY(), z - origin.getZ(), side, under);
                }
            }
        }
        removedFaces = upload(faceBuilder.endOrDiscardIfEmpty());
        buriedFaces = upload(buriedBuilder.endOrDiscardIfEmpty());
        //3. Fantomes des blocs poses (le plan applique sert de voisinage : faces cachees, connexions)
        if (!ghostStates.isEmpty()) {
            if (ghostBuilder == null) {
                ghostBuilder = new BufferBuilder(1 << 18);
            }
            if (ghostBuilder.building()) {
                ghostBuilder.discard();
            }
            ghostBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
            View view = new View(level, plan);
            BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
            RandomSource random = RandomSource.create();
            PoseStack pose = new PoseStack();
            Ghost consumer = new Ghost(ghostBuilder);
            for (Long2ObjectOpenHashMap.Entry<BlockState> entry : ghostStates.long2ObjectEntrySet()) {
                BlockPos at = BlockPos.of(entry.getLongKey());
                BlockState state = entry.getValue();
                if (state.getRenderShape() != RenderShape.MODEL) {
                    continue;
                }
                BakedModel model = dispatcher.getBlockModel(state);
                random.setSeed(state.getSeed(at));
                pose.pushPose();
                pose.translate(at.getX() - origin.getX(), at.getY() - origin.getY(), at.getZ() - origin.getZ());
                for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                    try {
                        dispatcher.renderBatched(state, at, view, pose, consumer, true, random, ModelData.EMPTY, type);
                    } catch (RuntimeException ignored) {
                        //Un modele capricieux ne doit pas casser l'apercu
                    }
                }
                pose.popPose();
            }
            ghosts = upload(ghostBuilder.endOrDiscardIfEmpty());
        }
    }

    /**
     * Fantomes d'un plan a coller, en coordonnees relatives (coin bas = 0) : tesselles une fois par rotation, puis dessines a la position
     * voulue avec {@link #renderAt} (le fantome suit la souris sans rien recalculer).
     */
    void buildBlueprint(Map<BlockPos, BlockState> blocks) {
        close();
        origin = BlockPos.ZERO;
        removed = placed = buried = 0;
        Minecraft minecraft = Minecraft.getInstance();
        ClientLevel level = minecraft.level;
        if (level == null || blocks.isEmpty()) {
            return;
        }
        if (ghostBuilder == null) {
            ghostBuilder = new BufferBuilder(1 << 18);
        }
        if (ghostBuilder.building()) {
            ghostBuilder.discard();
        }
        ghostBuilder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.BLOCK);
        BlockAndTintGetter view = new MapView(level, blocks);
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        RandomSource random = RandomSource.create();
        PoseStack pose = new PoseStack();
        Ghost consumer = new Ghost(ghostBuilder);
        int count = 0;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockState state = entry.getValue();
            if (state.isAir() || state.getRenderShape() != RenderShape.MODEL || count++ > MAX_GHOSTS) {
                continue;
            }
            BlockPos at = entry.getKey();
            BakedModel model = dispatcher.getBlockModel(state);
            random.setSeed(state.getSeed(at));
            pose.pushPose();
            pose.translate(at.getX(), at.getY(), at.getZ());
            for (RenderType type : model.getRenderTypes(state, random, ModelData.EMPTY)) {
                try {
                    dispatcher.renderBatched(state, at, view, pose, consumer, true, random, ModelData.EMPTY, type);
                } catch (RuntimeException ignored) {
                    //Un modele capricieux ne doit pas casser l'apercu
                }
            }
            pose.popPose();
        }
        placed = count;
        ghosts = upload(ghostBuilder.endOrDiscardIfEmpty());
    }

    /** Dessine les fantomes d'un plan a coller, coin bas en {@code at}. */
    void renderAt(PoseStack pose, BlockPos at) {
        origin = at;
        render(pose);
    }

    /** Le plan a coller seul (de l'air autour) : faces cachees entre ses propres blocs. */
    private record MapView(ClientLevel level, Map<BlockPos, BlockState> blocks) implements BlockAndTintGetter {

        @Override
        public float getShade(@NotNull Direction side, boolean shade) {
            return level.getShade(side, shade);
        }

        @NotNull
        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(@NotNull BlockPos pos) {
            return null;
        }

        @NotNull
        @Override
        public BlockState getBlockState(@NotNull BlockPos pos) {
            return blocks.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @NotNull
        @Override
        public FluidState getFluidState(@NotNull BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }

    /** Une position touchee par le plan : part-elle, est-elle posee ? */
    private static void consider(ClientLevel level, WorksPlan plan, BlockPos pos, LongOpenHashSet gone, Long2ObjectOpenHashMap<BlockState> ghosts) {
        long key = pos.asLong();
        if (gone.contains(key) || ghosts.containsKey(key)) {
            return;
        }
        BlockState target = plan.target(pos);
        if (target == null) {
            return;
        }
        BlockState current = level.getBlockState(pos);
        if (WorksPlan.matches(current, target)) {
            return;
        }
        boolean solid = !current.isAir() && !(current.getBlock() instanceof LiquidBlock) && !current.is(Blocks.BEDROCK);
        if (solid && (target.isAir() || !current.canBeReplaced()) && !WorksPlan.convertible(current, target)) {
            gone.add(key);
        }
        if (!target.isAir() && !(target.getBlock() instanceof LiquidBlock)) {
            ghosts.put(key, target);
        }
    }

    @Nullable
    private static VertexBuffer upload(@Nullable BufferBuilder.RenderedBuffer rendered) {
        if (rendered == null) {
            return null;
        }
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(rendered);
        VertexBuffer.unbind();
        return buffer;
    }

    private static void face(BufferBuilder b, float x, float y, float z, Direction side, boolean under) {
        float e = 0.004F;
        float x0 = x - e, y0 = y - e, z0 = z - e, x1 = x + 1 + e, y1 = y + 1 + e, z1 = z + 1 + e;
        float shade = switch (side) {
            case UP -> 1F;
            case DOWN -> 0.55F;
            case NORTH, SOUTH -> 0.8F;
            default -> 0.68F;
        };
        float r = 1F * shade, g = (under ? 0.62F : 0.27F) * shade, bl = (under ? 0.12F : 0.22F) * shade, a = under ? 0.16F : 0.32F;
        switch (side) {
            case UP -> quad(b, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl, a);
            case DOWN -> quad(b, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, r, g, bl, a);
            case NORTH -> quad(b, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, r, g, bl, a);
            case SOUTH -> quad(b, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, r, g, bl, a);
            case WEST -> quad(b, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, r, g, bl, a);
            case EAST -> quad(b, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, r, g, bl, a);
        }
    }

    private static void quad(BufferBuilder b, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy, float cz, float dx, float dy,
          float dz, float r, float g, float bl, float a) {
        b.vertex(ax, ay, az).color(r, g, bl, a).endVertex();
        b.vertex(bx, by, bz).color(r, g, bl, a).endVertex();
        b.vertex(cx, cy, cz).color(r, g, bl, a).endVertex();
        b.vertex(dx, dy, dz).color(r, g, bl, a).endVertex();
    }

    /** Dessine l'apercu dans le monde ({@code pose} : deja recale sur la camera). */
    void render(PoseStack pose) {
        if (ghosts == null && removedFaces == null && buriedFaces == null) {
            return;
        }
        Matrix4f modelView = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(pose.last().pose()).translate(origin.getX(), origin.getY(), origin.getZ());
        Matrix4f projection = RenderSystem.getProjectionMatrix();
        if (removedFaces != null) {
            RenderType type = RenderType.debugQuads();
            type.setupRenderState();
            RenderSystem.depthMask(false);
            draw(removedFaces, modelView, projection);
            RenderSystem.depthMask(true);
            type.clearRenderState();
        }
        if (buriedFaces != null) {
            //Sous terre : a travers le terrain
            RenderType type = XRayTypes.QUADS;
            type.setupRenderState();
            RenderSystem.depthMask(false);
            draw(buriedFaces, modelView, projection);
            RenderSystem.depthMask(true);
            type.clearRenderState();
        }
        if (ghosts != null) {
            RenderType type = RenderType.translucent();
            type.setupRenderState();
            draw(ghosts, modelView, projection);
            type.clearRenderState();
        }
    }

    private static void draw(VertexBuffer buffer, Matrix4f modelView, Matrix4f projection) {
        ShaderInstance shader = RenderSystem.getShader();
        if (shader == null) {
            return;
        }
        if (shader.CHUNK_OFFSET != null) {
            shader.CHUNK_OFFSET.set(0F, 0F, 0F);
        }
        buffer.bind();
        buffer.drawWithShader(modelView, projection, shader);
        VertexBuffer.unbind();
    }

    @Override
    public void close() {
        if (ghosts != null) {
            ghosts.close();
            ghosts = null;
        }
        if (removedFaces != null) {
            removedFaces.close();
            removedFaces = null;
        }
        if (buriedFaces != null) {
            buriedFaces.close();
            buriedFaces = null;
        }
    }

    /** Le monde une fois le plan applique (voisinage des fantomes). */
    private static final class View implements BlockAndTintGetter {

        private final ClientLevel level;
        private final WorksPlan plan;

        View(ClientLevel level, WorksPlan plan) {
            this.level = level;
            this.plan = plan;
        }

        @Override
        public float getShade(@NotNull Direction side, boolean shade) {
            return level.getShade(side, shade);
        }

        @NotNull
        @Override
        public LevelLightEngine getLightEngine() {
            return level.getLightEngine();
        }

        @Override
        public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver resolver) {
            return level.getBlockTint(pos, resolver);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(@NotNull BlockPos pos) {
            return null;
        }

        @NotNull
        @Override
        public BlockState getBlockState(@NotNull BlockPos pos) {
            return plan.after(level, pos);
        }

        @NotNull
        @Override
        public FluidState getFluidState(@NotNull BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return level.getHeight();
        }

        @Override
        public int getMinBuildHeight() {
            return level.getMinBuildHeight();
        }
    }

    /** Ecriture des fantomes : translucides, legerement bleutes, eclairage plein. */
    private record Ghost(VertexConsumer delegate) implements VertexConsumer {

        @NotNull
        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            delegate.color((int) (red * 0.8F), (int) (green * 0.9F), blue, 150);
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(240, 240);
            return this;
        }

        @NotNull
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
        public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light,
              float normalX, float normalY, float normalZ) {
            delegate.vertex(x, y, z, red * 0.8F, green * 0.9F, blue, 0.6F, u, v, overlay, 0xF000F0, normalX, normalY, normalZ);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            delegate.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
