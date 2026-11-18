package fr.pioneers.client.model;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.block.Connections;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.PartBlock;
import fr.pioneers.tech.industry.multi.SheetMetalBlock;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.space.assembly.PiWorksMachines;
import fr.pioneers.space.assembly.AssemblyHallControllerBlock;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0 : corps connecte des ports et des controleurs industriels. Les carcasses ont six booleens de connexion dans leur etat ; les ports et
 * controleurs n'en ont pas (trop d'etats avec leurs autres proprietes). Leur corps est donc dessine ici, quart de face par quart de face, avec
 * les modeles des carcasses ({@code design/ct/<tier>_<metier>/<face>_<qh><qv>_<cadre|h|v|inner>}) : le cadre (et sa bordure de couleur) ne
 * court que le long des cotes sans voisin de la famille {@code connects/industrial}, comme sur les carcasses. Les voisins sont lus au moment
 * ou le chunk est construit ({@link #getModelData}) : rien a synchroniser. Avant formation (tier 0), cadre complet de la carcasse basique.
 * <p>0.19.13 : aussi les blocs metier ({@link PartBlock}) : leur corps est la carcasse, et leur face exterieure ({@code FACING}) n'en garde qu'un
 * bord de {@link #HOLE} autour de l'ouverture ou loge leur niche ({@link #ring}). La cheminee et le refrigerateur a dilution, poses seuls
 * au-dessus du sommet (moins de deux voisins de la famille), n'ont pas de corps.
 * <p>1.10.1 (Guillaume : « par defaut les ports et controleurs sont verts alors qu'on ne sait meme pas quel tier on va build ; remplacer par du
 * gris fonce le contour et revoir la texture connectee du controleur ») : avant formation (tier peint 0), cadre complet GRIS FONCE
 * ({@code design/ct/unformed}, plus de cadre vert du tier basique) ; la facade des controleurs industriels est dessinee elle aussi par le corps
 * connecte, decoupee autour de la niche de l'ecran ({@link #NICHE}) : son cadre suit les carcasses voisines au lieu d'un acier uni.
 * <p>1.0.4-beta (lot 30) : un port pose a la place d'une tole ({@code Connections.ON_SHEET}, peint a la formation) prend le modele de la
 * tole voisine ({@link #sheetOf}) au lieu des quarts de carcasse ; ses plaques restent dessus.
 */
public class IndustrialBodyModel extends BakedModelWrapper<BakedModel> {

    private static final ModelProperty<Integer> MASK = new ModelProperty<>();
    /** 1.0.4-beta (lot 30) : port a la peau de tole : la tole dont il prend le modele (la plus frequente de ses voisines). */
    private static final ModelProperty<BlockState> SHEET = new ModelProperty<>();
    /** 1.0.6 : port d'une salle blanche (voisin d'une paroi ou d'un controleur de salle blanche) : corps blanc de la paroi. */
    private static final ModelProperty<Boolean> CLEAN = new ModelProperty<>();
    private static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};
    private static final String[] VARIANTS = {"frame", "h", "v", "inner"};
    /** Pour chaque face : voisins qui decident des quarts (horizontal bas / haut, vertical bas / haut), comme tools/gc/industry/design.py. */
    private static final Map<Direction, Direction[][]> AXES = Map.of(
          Direction.NORTH, new Direction[][]{{Direction.WEST, Direction.EAST}, {Direction.DOWN, Direction.UP}},
          Direction.SOUTH, new Direction[][]{{Direction.WEST, Direction.EAST}, {Direction.DOWN, Direction.UP}},
          Direction.WEST, new Direction[][]{{Direction.NORTH, Direction.SOUTH}, {Direction.DOWN, Direction.UP}},
          Direction.EAST, new Direction[][]{{Direction.NORTH, Direction.SOUTH}, {Direction.DOWN, Direction.UP}},
          Direction.DOWN, new Direction[][]{{Direction.WEST, Direction.EAST}, {Direction.NORTH, Direction.SOUTH}},
          Direction.UP, new Direction[][]{{Direction.WEST, Direction.EAST}, {Direction.NORTH, Direction.SOUTH}});
    private static final Map<ResourceLocation, BakedModel> QUARTERS = new ConcurrentHashMap<>();

    /** 0.19.13 : bord de carcasse autour de l'ouverture d'un bloc metier (meme valeur que A de tools/gc/industry/parts_v2.py). */
    public static final float HOLE = 2F / 16F;

    /** 1.10.1 : dossier des quarts avant formation (contour gris fonce). */
    public static final String UNFORMED = "unformed";
    /**
     * 1.10.1 : ouverture de la niche d'un controleur industriel dans sa facade (h0, h1, v0, v1 en fractions de bloc), comme le cadre de
     * {@code controller_model} de tools/gc/industry/visuals.py (x 2,6..13,4, y 3,6..13,0) et multiblock_preview.NICHE.
     */
    public static final float[] NICHE = {2.6F / 16F, 13.4F / 16F, 3.6F / 16F, 13.0F / 16F};
    private static final float[] PART_HOLE = {HOLE, 1 - HOLE, HOLE, 1 - HOLE};

    /** Controleur : sa facade (FACING) garde sa niche ; le corps n'y est pas dessine (multiblocs d'assemblage) ou l'entoure (niche). */
    private final boolean skipFront;
    /** 1.10.1 : controleur industriel : facade = corps connecte decoupe autour de la niche. */
    private final boolean niche;
    @Nullable
    private final String fixedStyle;
    @Nullable
    private final PartBlock.Kind part;
    /** 1.0.6 : port industriel (il prend le corps de la salle blanche quand il en borde une paroi). */
    private final boolean port;

    private IndustrialBodyModel(BakedModel original, boolean skipFront, boolean niche, @Nullable String fixedStyle, @Nullable PartBlock.Kind part,
          boolean port) {
        super(original);
        this.skipFront = skipFront;
        this.niche = niche;
        this.fixedStyle = fixedStyle;
        this.part = part;
        this.port = port;
    }

    private static ResourceLocation quarter(String folder, Direction face, int qh, int qv, int variant) {
        if (CLEANROOM.equals(folder)) {
            //1.0.4-beta (lot 26) : controleurs de salle blanche : quarts de la paroi de salle blanche (T:industry/building_blocks.py)
            return Pioneers.rl("block/industry/building/ct/cleanroom_casing/" + face.getName() + "_" + qh + qv + "_" + VARIANTS[variant]);
        }
        return Pioneers.rl("block/industry/design/ct/" + folder + "/" + face.getName() + "_" + qh + qv + "_" + VARIANTS[variant]);
    }

    /**
     * 1.0.4-beta (lot 26, Guillaume : « les controleurs pour les salles blanches doivent etre blancs ») : dossier des quarts des controleurs
     * des machines de salle blanche (structure de Guillaume faite de parois de salle blanche : {@code GeneratedNbtShapes.Shape#cleanroom}) :
     * le corps est celui de la paroi de salle blanche, raccorde a ses voisines (famille {@code connects/cleanroom}), forme ou non.
     */
    public static final String CLEANROOM = "cleanroom";

    /** 1.0.4-beta (lot 26) : le controleur de cette machine est-il un controleur de salle blanche (corps blanc, interface claire) ? */
    public static boolean cleanroom(Block block) {
        return block instanceof ControllerBlock c && GeneratedNbtShapes.of(c.machine.key) != null && GeneratedNbtShapes.of(c.machine.key).cleanroom();
    }

    private static List<String> folders() {
        List<String> out = new ArrayList<>();
        out.add(UNFORMED);
        out.add(CLEANROOM);
        for (String tier : TIERS) {
            for (IndustryStyle style : IndustryStyle.values()) {
                String f = tier + "_" + style.getSerializedName();
                if (!out.contains(f)) {
                    out.add(f);
                }
            }
        }
        return out;
    }

    /** Modeles des quarts a cuire (ModelEvent.RegisterAdditional). */
    public static void register(Consumer<ResourceLocation> register) {
        for (String folder : folders()) {
            for (Direction face : Direction.values()) {
                for (int qh = 0; qh < 2; qh++) {
                    for (int qv = 0; qv < 2; qv++) {
                        for (int v = 0; v < 4; v++) {
                            register.accept(quarter(folder, face, qh, qv, v));
                        }
                    }
                }
            }
        }
    }

    /** Enveloppe les modeles des etats des ports et des controleurs (ModelEvent.ModifyBakingResult). */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        QUARTERS.clear();
        Map<ResourceLocation, BakedModel> models = event.getModels();
        List<Block> blocks = new ArrayList<>();
        PiIndustry.PORTS.values().forEach(e -> blocks.add(e.getBlock()));
        PiIndustry.CONTROLLERS.values().forEach(e -> blocks.add(e.getBlock()));
        PiIndustry.PARTS.values().forEach(e -> blocks.add(e.getBlock()));
        //1.9.2 : controleurs des multiblocs d'assemblage (meme corps connecte, metier fixe)
        PiWorksMachines.HALLS.values().forEach(e -> blocks.add(e.getBlock()));
        java.util.Set<Block> ports = new java.util.HashSet<>();
        PiIndustry.PORTS.values().forEach(e -> ports.add(e.getBlock()));
        for (Block block : blocks) {
            String style = block instanceof ControllerBlock c ? c.machine.style.getSerializedName()
                           : block instanceof AssemblyHallControllerBlock h ? h.kind.style.getSerializedName() : null;
            PartBlock.Kind kind = block instanceof PartBlock p ? p.kind : null;
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(key);
                if (model != null && !(model instanceof IndustrialBodyModel)) {
                    models.put(key, new IndustrialBodyModel(model, block instanceof AssemblyHallControllerBlock, block instanceof ControllerBlock,
                          cleanroom(block) ? CLEANROOM : style, kind, ports.contains(block)));
                }
            }
        }
    }

    /** Nouveau chargement des ressources : les quarts seront relus. */
    public static void reset() {
        QUARTERS.clear();
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData modelData) {
        int mask = 0;
        //1.0.6 (Guillaume, 18/11/2026 : « corrige les textures connectees de la salle blanche au niveau des ports ») : un port qui borde une
        //paroi ou un controleur de salle blanche prend le corps blanc et se raccorde a la famille de la salle blanche (ports compris)
        boolean clean = port && cleanNeighbor(level, pos);
        //1.0.4-beta (lot 26) : controleur de salle blanche : raccorde aux parois de salle blanche
        var family = CLEANROOM.equals(fixedStyle) || clean ? Connections.CLEANROOM : Connections.INDUSTRIAL;
        for (Direction d : Direction.values()) {
            if (Connections.member(level.getBlockState(pos.relative(d)), family)) {
                mask |= 1 << d.ordinal();
            }
        }
        ModelData.Builder out = super.getModelData(level, pos, state, modelData).derive().with(MASK, mask).with(CLEAN, clean);
        if (onSheet(state)) {
            out.with(SHEET, sheetOf(level, pos, state));
        }
        return out.build();
    }

    /** 1.0.6 : une voisine est une paroi de salle blanche ou un controleur de salle blanche (pas seulement un autre port). */
    private static boolean cleanNeighbor(BlockAndTintGetter level, BlockPos pos) {
        for (Direction d : Direction.values()) {
            BlockState n = level.getBlockState(pos.relative(d));
            if (Connections.member(n, Connections.CLEANROOM) && !(n.getBlock() instanceof fr.pioneers.tech.industry.multi.PortBlock)) {
                return true;
            }
        }
        return false;
    }

    private static boolean onSheet(@Nullable BlockState state) {
        return state != null && state.hasProperty(Connections.ON_SHEET) && state.getValue(Connections.ON_SHEET);
    }

    /**
     * 1.0.4-beta (lot 30) : tole dont un port pose a la place d'une tole prend la peau : la plus frequente parmi ses six voisines (a egalite,
     * le plus faible tier : la paroi d'un contenant est faite d'une seule tole), sinon celle du tier peint du multibloc.
     */
    static BlockState sheetOf(BlockAndTintGetter level, BlockPos pos, BlockState state) {
        int[] count = new int[CableTier.values().length];
        for (Direction d : Direction.values()) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof SheetMetalBlock sheet) {
                count[Math.max(0, Math.min(count.length - 1, sheet.tier))]++;
            }
        }
        int best = -1;
        for (int i = 0; i < count.length; i++) {
            if (count[i] > 0 && (best < 0 || count[i] > count[best])) {
                best = i;
            }
        }
        if (best < 0) {
            int painted = state.hasProperty(IndustryStyle.TIER) ? state.getValue(IndustryStyle.TIER) : 1;
            best = Math.max(0, Math.min(count.length - 1, painted - 1));
        }
        return PiIndustry.SHEETS.get(CableTier.values()[best]).getBlock().defaultBlockState();
    }

    @NotNull
    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return ChunkRenderTypeSet.union(super.getRenderTypes(state, rand, data), ChunkRenderTypeSet.of(RenderType.solid()));
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
          @Nullable RenderType renderType) {
        List<BakedQuad> base = super.getQuads(state, side, rand, data, renderType);
        if (state == null || side == null || renderType != null && renderType != RenderType.solid()) {
            return base;
        }
        if (skipFront && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == side) {
            return base;
        }
        if (onSheet(state)) {
            //1.0.4-beta (lot 30) : port pose a la place d'une tole : son corps est la tole voisine (meme modele, meme calepinage : il se
            //raccorde aux toles comme elles entre elles), ses plaques (connecteur, couleur du sens) par-dessus
            BlockState sheet = data.get(SHEET);
            if (sheet == null) {
                sheet = PiIndustry.SHEETS.get(CableTier.BASIC).getBlock().defaultBlockState();
            }
            List<BakedQuad> out = new ArrayList<>(base);
            out.addAll(Minecraft.getInstance().getBlockRenderer().getBlockModel(sheet).getQuads(sheet, side, rand, ModelData.EMPTY, null));
            return out;
        }
        Integer m0 = data.get(MASK);
        float[] ring = null;
        if (niche && state.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && state.getValue(BlockStateProperties.HORIZONTAL_FACING) == side) {
            ring = NICHE;
        }
        if (part != null) {
            //Bloc metier seul au-dessus du toit (cheminee, refrigerateur a dilution) : pas de corps
            if (!part.niche() && (m0 == null || Integer.bitCount(m0) < 2)) {
                return base;
            }
            if (part.niche() && state.hasProperty(BlockStateProperties.FACING) && state.getValue(BlockStateProperties.FACING) == side) {
                ring = PART_HOLE;
            }
        }
        int tier = state.hasProperty(IndustryStyle.TIER) ? state.getValue(IndustryStyle.TIER) : 0;
        String style = fixedStyle != null ? fixedStyle : state.hasProperty(IndustryStyle.STYLE) ? state.getValue(IndustryStyle.STYLE).getSerializedName()
                                                                                                 : "neutral";
        String folder = tier <= 0 ? UNFORMED : TIERS[Math.min(4, tier) - 1] + "_" + style.toLowerCase(Locale.ROOT);
        if (CLEANROOM.equals(fixedStyle) || Boolean.TRUE.equals(data.get(CLEAN))) {
            //1.0.4-beta (lot 26) : blanc, forme ou non (les quarts se raccordent aux parois de salle blanche voisines) ; 1.0.6 : ports aussi
            folder = CLEANROOM;
            tier = Math.max(1, tier);
        }
        int mask = m0 == null ? 0 : m0;
        Direction[][] axes = AXES.get(side);
        List<BakedQuad> out = new ArrayList<>(base);
        for (int qh = 0; qh < 2; qh++) {
            for (int qv = 0; qv < 2; qv++) {
                boolean h = tier > 0 && (mask & 1 << axes[0][qh].ordinal()) != 0;
                boolean v = tier > 0 && (mask & 1 << axes[1][qv].ordinal()) != 0;
                BakedModel q = model(quarter(folder, side, qh, qv, h ? v ? 3 : 1 : v ? 2 : 0));
                if (q != null) {
                    if (ring != null) {
                        for (BakedQuad quad : q.getQuads(null, side, rand, ModelData.EMPTY, null)) {
                            ring(quad, side, ring, out);
                        }
                    } else {
                        out.addAll(q.getQuads(null, side, rand, ModelData.EMPTY, null));
                    }
                }
            }
        }
        return out;
    }

    /**
     * 0.19.13 : partie d'un quart de face hors de l'ouverture {@code hole} (h0, h1, v0, v1 ; [HOLE, 1 - HOLE]^2 pour un bloc metier, la
     * niche pour un controleur) : jusqu'a quatre rectangles, positions et uv interpoles entre les coins du quart (meme ordre des sommets :
     * meme normale). Banc Python : multiblock_preview._clip.
     */
    private static void ring(BakedQuad quad, Direction side, float[] hole, List<BakedQuad> out) {
        int[] data = quad.getVertices();
        int stride = data.length / 4;
        int ha = side.getAxis() == Direction.Axis.X ? 2 : 0, va = side.getAxis() == Direction.Axis.Y ? 2 : 1;
        float[] h = new float[4], v = new float[4];
        float h0 = Float.MAX_VALUE, h1 = -Float.MAX_VALUE, v0 = Float.MAX_VALUE, v1 = -Float.MAX_VALUE;
        for (int i = 0; i < 4; i++) {
            h[i] = Float.intBitsToFloat(data[i * stride + ha]);
            v[i] = Float.intBitsToFloat(data[i * stride + va]);
            h0 = Math.min(h0, h[i]);
            h1 = Math.max(h1, h[i]);
            v0 = Math.min(v0, v[i]);
            v1 = Math.max(v1, v[i]);
        }
        if (h1 - h0 < 1e-5F || v1 - v0 < 1e-5F) {
            out.add(quad);
            return;
        }
        float hm = (h0 + h1) / 2, vm = (v0 + v1) / 2;
        int[][] corner = new int[2][2];
        boolean[] sh = new boolean[4], sv = new boolean[4];
        for (int i = 0; i < 4; i++) {
            sh[i] = h[i] > hm;
            sv[i] = v[i] > vm;
            corner[sh[i] ? 1 : 0][sv[i] ? 1 : 0] = i;
        }
        float lo = hole[0], hi = hole[1], vlo = hole[2], vhi = hole[3];
        List<float[]> rects = new ArrayList<>(4);
        if (h0 < lo) {
            rects.add(new float[]{h0, Math.min(h1, lo), v0, v1});
        }
        if (h1 > hi) {
            rects.add(new float[]{Math.max(h0, hi), h1, v0, v1});
        }
        float mh0 = Math.max(h0, lo), mh1 = Math.min(h1, hi);
        if (mh0 < mh1) {
            if (v0 < vlo) {
                rects.add(new float[]{mh0, mh1, v0, Math.min(v1, vlo)});
            }
            if (v1 > vhi) {
                rects.add(new float[]{mh0, mh1, Math.max(v0, vhi), v1});
            }
        }
        for (float[] r : rects) {
            if (r[1] - r[0] < 1e-5F || r[3] - r[2] < 1e-5F) {
                continue;
            }
            int[] nd = data.clone();
            for (int i = 0; i < 4; i++) {
                float s = ((sh[i] ? r[1] : r[0]) - h0) / (h1 - h0), t = ((sv[i] ? r[3] : r[2]) - v0) / (v1 - v0);
                for (int k : new int[]{0, 1, 2, 4, 5}) {
                    float a = at(data, corner[0][0], stride, k) * (1 - s) + at(data, corner[1][0], stride, k) * s;
                    float b = at(data, corner[0][1], stride, k) * (1 - s) + at(data, corner[1][1], stride, k) * s;
                    nd[i * stride + k] = Float.floatToRawIntBits(a * (1 - t) + b * t);
                }
            }
            out.add(new BakedQuad(nd, quad.getTintIndex(), quad.getDirection(), quad.getSprite(), quad.isShade()));
        }
    }

    private static float at(int[] data, int vertex, int stride, int k) {
        return Float.intBitsToFloat(data[vertex * stride + k]);
    }

    @Nullable
    private static BakedModel model(ResourceLocation rl) {
        BakedModel cached = QUARTERS.get(rl);
        if (cached == null) {
            cached = Minecraft.getInstance().getModelManager().getModel(rl);
            if (cached == Minecraft.getInstance().getModelManager().getMissingModel()) {
                cached = null;
            } else {
                QUARTERS.put(rl, cached);
            }
        }
        return cached;
    }
}
