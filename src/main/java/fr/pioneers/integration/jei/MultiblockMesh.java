package fr.pioneers.integration.jei;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.common.projector.BlueprintBill;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.color.block.BlockColors;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.ColorResolver;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.lighting.LevelLightEngine;
import net.minecraft.world.level.material.FluidState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.11.3 (lot E3) : maillage d'un plan de multibloc pour l'apercu 3D de la page « Multiblocs » de JEI. Construit UNE FOIS par plan (cache
 * {@link #of}) a partir des vrais modeles des blocs (corps connecte des ports et controleurs compris : leur {@code getModelData} lit les voisins
 * dans le plan) ; a chaque image on ne fait que pousser les sommets deja calcules dans le tampon (rien n'est recalcule).
 * <p>
 * Faces cachees retirees a la construction ({@link Block#shouldRenderFace}) ; celles cachees seulement par la couche du dessus ou du dessous sont
 * gardees a part pour la vue par couche. Au plus {@link #MAX_BLOCKS} blocs et {@link #MAX_QUADS} faces : au-dela le reste n'est pas dessine.
 */
@OnlyIn(Dist.CLIENT)
public final class MultiblockMesh {

    static final int MAX_BLOCKS = 12_000, MAX_QUADS = 60_000;
    /** Floats par face : 4 sommets x (x, y, z, u, v), normale (3), couleur ARGB, drapeau « vue par couche seulement ». */
    private static final int STRIDE = 4 * 5 + 3 + 2;
    private static final Map<String, MultiblockMesh> CACHE = new ConcurrentHashMap<>();

    final int sx, sy, sz;
    /** Par couche (y), faces opaques / decoupees et faces translucides. */
    private final float[][] solid, translucent;
    private final int[] solidN, translucentN;
    final int quads;
    final boolean truncated;

    private MultiblockMesh(int sx, int sy, int sz, float[][] solid, int[] solidN, float[][] translucent, int[] translucentN, int quads, boolean truncated) {
        this.sx = sx;
        this.sy = sy;
        this.sz = sz;
        this.solid = solid;
        this.solidN = solidN;
        this.translucent = translucent;
        this.translucentN = translucentN;
        this.quads = quads;
        this.truncated = truncated;
    }

    /** Maillage du plan (construit a la premiere demande, puis garde). */
    static MultiblockMesh of(String key, BlueprintBill bill) {
        MultiblockMesh m = CACHE.get(key);
        if (m == null) {
            m = build(bill);
            CACHE.put(key, m);
        }
        return m;
    }

    /** Vide le cache (rechargement des ressources). */
    static void clear() {
        CACHE.clear();
    }

    // ------------------------------------------------------------------ monde factice du plan

    /** Monde reduit aux cases du plan : sert aux modeles connectes, a l'occlusion des faces et aux teintes. */
    private record PlanLevel(Map<BlockPos, BlockState> cells) implements BlockAndTintGetter {

        @Override
        public float getShade(@NotNull Direction direction, boolean shade) {
            return 1F;
        }

        @NotNull
        @Override
        public LevelLightEngine getLightEngine() {
            throw new UnsupportedOperationException();
        }

        @Override
        public int getBrightness(@NotNull LightLayer layer, @NotNull BlockPos pos) {
            return 15;
        }

        @Override
        public int getRawBrightness(@NotNull BlockPos pos, int darkening) {
            return 15;
        }

        @Override
        public boolean canSeeSky(@NotNull BlockPos pos) {
            return true;
        }

        @Override
        public int getBlockTint(@NotNull BlockPos pos, @NotNull ColorResolver resolver) {
            var level = Minecraft.getInstance().level;
            return level == null ? -1 : level.getBlockTint(Minecraft.getInstance().player == null ? BlockPos.ZERO
                  : Minecraft.getInstance().player.blockPosition(), resolver);
        }

        @Nullable
        @Override
        public BlockEntity getBlockEntity(@NotNull BlockPos pos) {
            return null;
        }

        @NotNull
        @Override
        public BlockState getBlockState(@NotNull BlockPos pos) {
            return cells.getOrDefault(pos, Blocks.AIR.defaultBlockState());
        }

        @NotNull
        @Override
        public FluidState getFluidState(@NotNull BlockPos pos) {
            return getBlockState(pos).getFluidState();
        }

        @Override
        public int getHeight() {
            return 512;
        }

        @Override
        public int getMinBuildHeight() {
            return -256;
        }
    }

    // ------------------------------------------------------------------ construction

    private static final class Buf {

        float[] data = new float[STRIDE * 64];
        int n;

        void add(float[] quad) {
            if ((n + 1) * STRIDE > data.length) {
                data = Arrays.copyOf(data, data.length * 2);
            }
            System.arraycopy(quad, 0, data, n * STRIDE, STRIDE);
            n++;
        }
    }

    private static MultiblockMesh build(BlueprintBill bill) {
        int sy = Math.max(1, bill.sizeY());
        Buf[] solid = new Buf[sy], trans = new Buf[sy];
        for (int y = 0; y < sy; y++) {
            solid[y] = new Buf();
            trans[y] = new Buf();
        }
        PlanLevel level = new PlanLevel(bill.cells());
        BlockRenderDispatcher dispatcher = Minecraft.getInstance().getBlockRenderer();
        BlockColors colors = Minecraft.getInstance().getBlockColors();
        RandomSource random = RandomSource.create();
        int blocks = 0, quads = 0;
        boolean truncated = false;
        float[] q = new float[STRIDE];
        for (Map.Entry<BlockPos, BlockState> e : bill.cells().entrySet()) {
            BlockPos pos = e.getKey();
            BlockState state = e.getValue();
            if (state.getRenderShape() != RenderShape.MODEL) {
                continue;
            }
            if (++blocks > MAX_BLOCKS || quads > MAX_QUADS) {
                truncated = true;
                break;
            }
            BakedModel model = dispatcher.getBlockModel(state);
            ModelData data;
            try {
                data = model.getModelData(level, pos, state, ModelData.EMPTY);
            } catch (RuntimeException ex) {
                data = ModelData.EMPTY;
            }
            random.setSeed(42L);
            for (RenderType rt : model.getRenderTypes(state, random, data)) {
                boolean see = rt == RenderType.translucent();
                for (int d = -1; d < 6; d++) {
                    Direction dir = d < 0 ? null : Direction.from3DDataValue(d);
                    boolean layerOnly = false;
                    if (dir != null && !Block.shouldRenderFace(state, level, pos, dir, pos.relative(dir))) {
                        if (dir.getAxis() != Direction.Axis.Y) {
                            continue;
                        }
                        //Cachee par la couche voisine : gardee pour la vue par couche
                        layerOnly = true;
                    }
                    random.setSeed(42L);
                    List<BakedQuad> list;
                    try {
                        list = model.getQuads(state, dir, random, data, rt);
                    } catch (RuntimeException ex) {
                        continue;
                    }
                    for (BakedQuad quad : list) {
                        int tint = -1;
                        if (quad.isTinted()) {
                            try {
                                tint = colors.getColor(state, level, pos, quad.getTintIndex());
                            } catch (RuntimeException ex) {
                                tint = -1;
                            }
                        }
                        pack(q, quad, pos, tint, layerOnly);
                        (see ? trans : solid)[pos.getY()].add(q);
                        quads++;
                    }
                }
            }
        }
        float[][] s = new float[sy][], t = new float[sy][];
        int[] sn = new int[sy], tn = new int[sy];
        for (int y = 0; y < sy; y++) {
            s[y] = Arrays.copyOf(solid[y].data, solid[y].n * STRIDE);
            sn[y] = solid[y].n;
            t[y] = Arrays.copyOf(trans[y].data, trans[y].n * STRIDE);
            tn[y] = trans[y].n;
        }
        return new MultiblockMesh(bill.sizeX(), sy, bill.sizeZ(), s, sn, t, tn, quads, truncated);
    }

    /** Recopie une face cuite (format BLOCK : 8 entiers par sommet) dans le repere du plan. */
    private static void pack(float[] q, BakedQuad quad, BlockPos pos, int tint, boolean layerOnly) {
        int[] v = quad.getVertices();
        int step = v.length / 4;
        for (int i = 0; i < 4; i++) {
            int o = i * step, w = i * 5;
            q[w] = Float.intBitsToFloat(v[o]) + pos.getX();
            q[w + 1] = Float.intBitsToFloat(v[o + 1]) + pos.getY();
            q[w + 2] = Float.intBitsToFloat(v[o + 2]) + pos.getZ();
            q[w + 3] = Float.intBitsToFloat(v[o + 4]);
            q[w + 4] = Float.intBitsToFloat(v[o + 5]);
        }
        Direction face = quad.getDirection();
        q[20] = face.getStepX();
        q[21] = face.getStepY();
        q[22] = face.getStepZ();
        //Couleur du sommet (ABGR) multipliee par la teinte ; l'ombrage vient de l'eclairage des objets (normales), comme les blocs de l'inventaire
        int abgr = v[3];
        float shade = 1F;
        float r = (abgr & 255) / 255F, g = (abgr >> 8 & 255) / 255F, b = (abgr >> 16 & 255) / 255F;
        if (tint != -1) {
            r *= (tint >> 16 & 255) / 255F;
            g *= (tint >> 8 & 255) / 255F;
            b *= (tint & 255) / 255F;
        }
        int argb = 255 << 24 | Math.round(r * shade * 255) << 16 | Math.round(g * shade * 255) << 8 | Math.round(b * shade * 255);
        q[23] = Float.intBitsToFloat(argb);
        q[24] = layerOnly ? 1F : 0F;
    }

    // ------------------------------------------------------------------ dessin

    /**
     * Pousse les faces dans le tampon (repere courant : une unite = un bloc, plan centre). {@code layer} &lt; 0 : tout le plan (faces internes
     * aux couches retirees) ; sinon la seule couche {@code layer}, faces du dessus et du dessous comprises.
     */
    void render(PoseStack pose, MultiBufferSource.BufferSource buffers, int layer) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        VertexConsumer vc = buffers.getBuffer(Sheets.cutoutBlockSheet());
        emit(vc, m, n, solid, solidN, layer);
        buffers.endBatch(Sheets.cutoutBlockSheet());
        vc = buffers.getBuffer(Sheets.translucentCullBlockSheet());
        emit(vc, m, n, translucent, translucentN, layer);
        buffers.endBatch(Sheets.translucentCullBlockSheet());
    }

    private void emit(VertexConsumer vc, Matrix4f m, Matrix3f n, float[][] layers, int[] counts, int layer) {
        int from = layer < 0 ? 0 : Math.min(layer, layers.length - 1), to = layer < 0 ? layers.length - 1 : from;
        for (int y = from; y <= to; y++) {
            float[] d = layers[y];
            for (int k = 0; k < counts[y]; k++) {
                int o = k * STRIDE;
                if (layer < 0 && d[o + 24] != 0F) {
                    continue;
                }
                int argb = Float.floatToRawIntBits(d[o + 23]);
                int r = argb >> 16 & 255, g = argb >> 8 & 255, b = argb & 255;
                for (int i = 0; i < 4; i++) {
                    int w = o + i * 5;
                    vc.vertex(m, d[w], d[w + 1], d[w + 2]).color(r, g, b, 255).uv(d[w + 3], d[w + 4]).overlayCoords(OverlayTexture.NO_OVERLAY)
                          .uv2(LightTexture.FULL_BRIGHT).normal(n, d[o + 20], d[o + 21], d[o + 22]).endVertex();
                }
            }
        }
    }
}
