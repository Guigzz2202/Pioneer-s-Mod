package fr.pioneers.logistics.rail.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.industry.client.big.BigMat;
import fr.pioneers.tech.industry.client.big.BigMesh;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.TieredMachineBlock;
import fr.pioneers.logistics.rail.TrackBlocks;
import fr.pioneers.logistics.rail.block.ConductorBlock;
import fr.pioneers.logistics.rail.block.PostKind;
import fr.pioneers.logistics.rail.block.RailPostBlock;
import fr.pioneers.logistics.rail.block.RailSignalBlock;
import fr.pioneers.logistics.rail.block.StationBlock;
import fr.pioneers.logistics.rail.block.SubstationBlock;
import fr.pioneers.logistics.rail.block.Works;
import fr.pioneers.logistics.rail.net.Electrification;
import fr.pioneers.logistics.rail.net.RailNetwork;
import fr.pioneers.logistics.rail.track.TrackBlock;
import fr.pioneers.logistics.rail.track.TrackShape;
import fr.pioneers.logistics.rail.PiRail;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockModelShaper;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.ModelResourceLocation;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.ChunkRenderTypeSet;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.model.BakedModelWrapper;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.client.model.data.ModelProperty;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * 0.20.0 : modeles des blocs du chemin de fer, cuits depuis les maillages Java ({@link TrackModels}, {@link InfraModels}) : un etat = une
 * liste de quads calculee a la premiere demande. Les textures sont les materiaux {@code block/rail/*} de l'atlas des blocs ; UV a
 * l'echelle (1 px = 1/16 de texture, borne a la case de la texture) ; les materiaux lumineux portent leur propre lumiere.
 * Les postes de quai lisent le cote de la voie dans le monde ({@link #getModelData}) pour tourner leur bras vers elle.
 */
public class RailBakedModel extends BakedModelWrapper<BakedModel> {

    private static final ModelProperty<Integer> ARM = new ModelProperty<>();
    /** 0.20.3 : bloc qui porte une pente (son remblai en prend les textures). */
    private static final ModelProperty<BlockState> BELOW = new ModelProperty<>();
    /** Teintes du remblai : indice de teinte du bloc porteur + {@link #TINT_BASE}. */
    private static final int TINT_BASE = 1000;
    private static final Map<String, List<BakedQuad>> CACHE = new ConcurrentHashMap<>();

    @Nullable
    private final BlockState itemState;

    private RailBakedModel(BakedModel original, @Nullable BlockState itemState) {
        super(original);
        this.itemState = itemState;
    }

    public static void reset() {
        CACHE.clear();
    }

    /** Donnees de modele d'un poste de quai dont le bras vise {@code toTrack} (fantome de pose). */
    public static ModelData armData(Direction toTrack) {
        return ModelData.builder().with(ARM, toTrack.get2DDataValue()).build();
    }

    public static List<Block> blocks() {
        List<Block> out = new ArrayList<>();
        PiRail.TRACKS.values().forEach(e -> out.add(e.getBlock()));
        out.add(PiRail.BUFFER_STOP.getBlock());
        out.add(PiRail.BRAKE_TRACK.getBlock());
        out.add(PiRail.BOOSTER_TRACK.getBlock());
        out.add(PiRail.DETECTOR_TRACK.getBlock());
        out.add(PiRail.WEIGHBRIDGE.getBlock());
        out.add(PiRail.UNCOUPLER_TRACK.getBlock());
        out.add(PiRail.LEVEL_CROSSING.getBlock());
        out.add(PiRail.CROSSING_BARRIER.getBlock());
        out.add(PiRail.VIADUCT_PILLAR.getBlock());
        out.add(PiRail.RAILING.getBlock());
        out.add(PiRail.TUNNEL_PORTAL.getBlock());
        out.add(PiRail.PLATFORM_SLAB.getBlock());
        out.add(PiRail.PLATFORM_LAMP.getBlock());
        out.add(PiRail.BLOCK_SIGNAL.getBlock());
        out.add(PiRail.CHAIN_SIGNAL.getBlock());
        out.add(PiRail.STATION.getBlock());
        PiRail.POSTS.values().forEach(list -> list.forEach(e -> out.add(e.getBlock())));
        out.add(PiRail.WATER_CRANE.getBlock());
        out.add(PiRail.CATENARY_POLE.getBlock());
        out.add(PiRail.THIRD_RAIL.getBlock());
        PiRail.SUBSTATIONS.forEach(e -> out.add(e.getBlock()));
        return out;
    }

    public static Block[] trackBlocks() {
        List<Block> out = new ArrayList<>();
        for (Block b : blocks()) {
            if (b instanceof TrackBlock) {
                out.add(b);
            }
        }
        return out.toArray(new Block[0]);
    }

    /** Couleur d'un quad du remblai : celle du bloc porteur pour son propre indice de teinte. */
    public static int tint(BlockState state, @Nullable BlockAndTintGetter level, @Nullable BlockPos pos, int index) {
        if (index < TINT_BASE || level == null || pos == null) {
            return -1;
        }
        BlockPos below = pos.below();
        return Minecraft.getInstance().getBlockColors().getColor(level.getBlockState(below), level, below, index - TINT_BASE);
    }

    /** Enveloppe les modeles des etats et des objets (ModelEvent.ModifyBakingResult). */
    public static void wrap(ModelEvent.ModifyBakingResult event) {
        Map<ResourceLocation, BakedModel> models = event.getModels();
        for (Block block : blocks()) {
            for (BlockState state : block.getStateDefinition().getPossibleStates()) {
                ResourceLocation key = BlockModelShaper.stateToModelLocation(state);
                BakedModel model = models.get(key);
                if (model != null && !(model instanceof RailBakedModel)) {
                    models.put(key, new RailBakedModel(model, null));
                }
            }
            ResourceLocation id = BuiltInRegistries.BLOCK.getKey(block);
            ModelResourceLocation item = new ModelResourceLocation(id, "inventory");
            BakedModel model = models.get(item);
            if (model != null && !(model instanceof RailBakedModel)) {
                models.put(item, new RailBakedModel(model, itemState(block)));
            }
        }
    }

    private static BlockState itemState(Block block) {
        BlockState s = block.defaultBlockState();
        if (block instanceof TrackBlock tb) {
            s = tb.withShape(s, tb.allows(TrackShape.STRAIGHT_EW) ? TrackShape.STRAIGHT_EW : TrackShape.BUFFER_E);
        }
        if (s.hasProperty(MachineBlock.FACING)) {
            s = s.setValue(MachineBlock.FACING, Direction.WEST);
        }
        if (s.hasProperty(RailSignalBlock.FACING)) {
            s = s.setValue(RailSignalBlock.FACING, block instanceof ConductorBlock ? Direction.EAST : Direction.WEST);
        }
        return s;
    }

    @NotNull
    @Override
    public ModelData getModelData(@NotNull BlockAndTintGetter level, @NotNull BlockPos pos, @NotNull BlockState state, @NotNull ModelData data) {
        Block b = state.getBlock();
        if (b instanceof TrackBlock tb && tb.shape(state) != null && tb.shape(state).family() == TrackShape.Family.SLOPE) {
            return data.derive().with(BELOW, level.getBlockState(pos.below())).build();
        }
        if (b instanceof TieredMachineBlock && !(b instanceof SubstationBlock) && RailPostBlock.kindOf(state) != PostKind.PIT
              || b == PiRail.WATER_CRANE.getBlock()) {
            int arm = -1;
            for (Direction d : Direction.Plane.HORIZONTAL) {
                if (level.getBlockState(pos.relative(d)).getBlock() instanceof TrackBlock || level.getBlockState(pos.relative(d).below()).getBlock() instanceof TrackBlock) {
                    arm = d.get2DDataValue();
                    break;
                }
            }
            return data.derive().with(ARM, arm).build();
        }
        return data;
    }

    @NotNull
    @Override
    public ChunkRenderTypeSet getRenderTypes(@NotNull BlockState state, @NotNull RandomSource rand, @NotNull ModelData data) {
        return ChunkRenderTypeSet.of(RenderType.cutout());
    }

    //Objets : BakedModelWrapper renvoie le modele d'origine (vide) pour les passes et la pose a l'ecran : on garde notre enveloppe

    /**
     * 1.6 (« les trains sont laggy dans l'inventaire ») : les gros postes ont un modele d'objet a vue gui PLATE (forge:separate_transforms,
     * icone de T:rail/item_icons.py) : dans l'inventaire on rend ce modele-la (il n'est pas le modele d'origine) ; partout ailleurs, notre
     * maillage cuit.
     */
    @NotNull
    @Override
    public BakedModel applyTransform(@NotNull ItemDisplayContext context, @NotNull PoseStack pose, boolean leftHand) {
        BakedModel resolved = originalModel.applyTransform(context, pose, leftHand);
        return context == ItemDisplayContext.GUI && resolved != originalModel ? resolved : this;
    }

    @NotNull
    @Override
    public List<BakedModel> getRenderPasses(@NotNull ItemStack stack, boolean fabulous) {
        return List.of(this);
    }

    @NotNull
    @Override
    public List<RenderType> getRenderTypes(@NotNull ItemStack stack, boolean fabulous) {
        return List.of(Sheets.cutoutBlockSheet());
    }

    @Override
    public boolean useAmbientOcclusion() {
        return false;
    }

    @Override
    public boolean useAmbientOcclusion(@NotNull BlockState state) {
        return false;
    }

    @Override
    public boolean useAmbientOcclusion(@NotNull BlockState state, @NotNull RenderType renderType) {
        return false;
    }

    @NotNull
    @Override
    public TextureAtlasSprite getParticleIcon(@NotNull ModelData data) {
        return sprite(BigMat.R_STEEL);
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand, @NotNull ModelData data,
          @Nullable RenderType renderType) {
        if (side != null) {
            return List.of();
        }
        BlockState st = state != null ? state : itemState;
        if (st == null) {
            return super.getQuads(state, side, rand, data, renderType);
        }
        Integer arm = data.get(ARM);
        BlockState below = data.get(BELOW);
        String key = st.toString() + "|" + (arm == null ? -1 : arm) + (below == null ? "" : "|" + below);
        Integer a = arm;
        return CACHE.computeIfAbsent(key, k -> {
            List<BakedQuad> quads = bake(RailMesh.deconflict(mesh(st, a == null ? -1 : a)));
            if (st.getBlock() instanceof TrackBlock tb && tb.shape(st) != null && tb.shape(st).family() == TrackShape.Family.SLOPE) {
                quads = new ArrayList<>(quads);
                embankment(quads, tb.shape(st), below == null || below.isAir() ? Blocks.DIRT.defaultBlockState() : below, rand);
            }
            return quads;
        });
    }

    @NotNull
    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side, @NotNull RandomSource rand) {
        return getQuads(state, side, rand, ModelData.EMPTY, null);
    }

    // ------------------------------------------------------------------

    private static BigMesh mesh(BlockState st, int arm) {
        Block b = st.getBlock();
        if (b instanceof TrackBlock tb) {
            TrackShape s = tb.shape(st);
            int route = st.hasProperty(TrackBlocks.ROUTE) ? st.getValue(TrackBlocks.ROUTE) : 0;
            boolean powered = st.hasProperty(TrackBlocks.POWERED) && st.getValue(TrackBlocks.POWERED);
            return TrackModels.build(tb.quality, tb.kind, s == null ? TrackShape.STRAIGHT_NS : s, route, powered).mesh;
        }
        if (b instanceof RailSignalBlock sig) {
            Direction f = st.getValue(RailSignalBlock.FACING);
            int aspect = st.getValue(TrackBlocks.ASPECT);
            return (sig.type == RailNetwork.SignalType.CHAIN ? InfraModels.lightSignal(f, aspect) : InfraModels.semaphore(f, aspect)).mesh;
        }
        if (b instanceof Works.Pillar) {
            return WorksModels.pillar(st.getValue(Works.UP), st.getValue(Works.DOWN)).mesh;
        }
        if (b instanceof Works.Railing) {
            return WorksModels.railing(st.getValue(Works.NORTH), st.getValue(Works.EAST), st.getValue(Works.SOUTH), st.getValue(Works.WEST)).mesh;
        }
        if (b instanceof Works.Portal) {
            return WorksModels.portal(st.getValue(Works.Portal.FACING), st.getValue(Works.PART)).mesh;
        }
        if (b instanceof Works.PlatformSlab) {
            return WorksModels.platformSlab(st.getValue(Works.PlatformSlab.FACING)).mesh;
        }
        if (b instanceof Works.Lamp) {
            return WorksModels.lamp().mesh;
        }
        if (b instanceof Works.Barrier) {
            return WorksModels.barrier(st.getValue(Works.Barrier.FACING), st.getValue(Works.CLOSED), st.getValue(Works.BLINK)).mesh;
        }
        if (b instanceof StationBlock) {
            return InfraModels.station(st.getValue(StationBlock.FACING)).mesh;
        }
        if (b instanceof ConductorBlock cb) {
            Direction to = ConductorBlock.toTrack(st);
            return (cb.kind == Electrification.Kind.POLE ? InfraModels.catenaryPole(0, to) : InfraModels.thirdRail(to)).mesh;
        }
        Direction armDir = arm < 0 ? (st.hasProperty(MachineBlock.FACING) ? st.getValue(MachineBlock.FACING) : null)
              : Direction.from2DDataValue(arm);
        boolean active = st.hasProperty(MachineBlock.ACTIVE) && st.getValue(MachineBlock.ACTIVE);
        if (b instanceof SubstationBlock sub) {
            return InfraModels.substation(sub.tier(), st.getValue(MachineBlock.FACING), active).mesh;
        }
        if (b instanceof RailPostBlock post) {
            int t = post.tier();
            return (switch (post.kind) {
                case ITEM -> InfraModels.itemLoader(t, armDir, active);
                case SILO -> InfraModels.loadingSilo(t, armDir, active);
                case PIT -> InfraModels.unloadingPit(t, active);
                case COALING -> InfraModels.coalingTower(armDir, active);
                case CRANE -> InfraModels.platformCrane(armDir, active);
                case NUCLEAR -> InfraModels.nuclearFuelStation(armDir, active);
                case FLUID -> InfraModels.fluidLoader(t, armDir, active);
                case FUEL -> InfraModels.fuelPump(armDir, active);
                case STEAM -> InfraModels.steamCharger(armDir, active);
                case ENERGY -> InfraModels.energyLoader(t, armDir, active);
                case CHARGER -> InfraModels.chargingPost(armDir, active);
                case DATA -> InfraModels.dataLoader(t, armDir, active);
                case AIR -> InfraModels.airLoader(t, armDir, active);
                case PASSENGER -> InfraModels.passengerPlatform(armDir, active);
            }).mesh;
        }
        if (b instanceof TieredMachineBlock tmb) {
            return InfraModels.itemLoader(tmb.tier(), armDir, active).mesh;
        }
        return InfraModels.waterCrane(armDir, active).mesh;
    }

    // ------------------------------------------------------------------ remblai des pentes

    /** Texture d'une face du bloc porteur (premier quad de cette face ; a defaut sa texture de particules) et son indice de teinte. */
    private record Face(TextureAtlasSprite sprite, int tint) {}

    private static Face face(BlockState below, Direction dir, RandomSource rand) {
        BakedModel model = Minecraft.getInstance().getBlockRenderer().getBlockModel(below);
        for (Direction d : new Direction[]{dir, null}) {
            List<BakedQuad> quads = model.getQuads(below, d, rand, ModelData.EMPTY, null);
            for (BakedQuad q : quads) {
                if (d != null || q.getDirection() == dir) {
                    return new Face(q.getSprite(), q.isTinted() ? TINT_BASE + q.getTintIndex() : -1);
                }
            }
        }
        return new Face(model.getParticleIcon(ModelData.EMPTY), -1);
    }

    /** Remblai plein sous une pente (0.20.3, geometrie : {@link TrackModels#embankment}) aux textures du bloc qui porte la voie. */
    private static void embankment(List<BakedQuad> out, TrackShape shape, BlockState below, RandomSource rand) {
        for (TrackModels.EmbankFace f : TrackModels.embankment(shape)) {
            out.add(quad(f.pts(), f.uv(), face(below, f.face(), rand), f.normal(), f.face()));
        }
    }

    /** Quad en px (4 coins, UV en px de texture), retourne s'il ne regarde pas vers {@code normal}. */
    private static BakedQuad quad(float[][] pts, float[][] uv, Face face, float[] normal, Direction dir) {
        float ax = pts[1][0] - pts[0][0], ay = pts[1][1] - pts[0][1], az = pts[1][2] - pts[0][2];
        float bx = pts[2][0] - pts[0][0], by = pts[2][1] - pts[0][1], bz = pts[2][2] - pts[0][2];
        float cx = ay * bz - az * by, cy = az * bx - ax * bz, cz = ax * by - ay * bx;
        //Coin confondu (moitie basse : flanc triangulaire) : on prend le produit avec le dernier coin
        if (Math.abs(cx) + Math.abs(cy) + Math.abs(cz) < 1e-4) {
            float dx = pts[3][0] - pts[0][0], dy = pts[3][1] - pts[0][1], dz = pts[3][2] - pts[0][2];
            cx = ay * dz - az * dy;
            cy = az * dx - ax * dz;
            cz = ax * dy - ay * dx;
        }
        int[] order = cx * normal[0] + cy * normal[1] + cz * normal[2] >= 0 ? new int[]{0, 1, 2, 3} : new int[]{3, 2, 1, 0};
        int packed = (int) (normal[0] * 127) & 255 | ((int) (normal[1] * 127) & 255) << 8 | ((int) (normal[2] * 127) & 255) << 16;
        int[] data = new int[32];
        for (int k = 0; k < 4; k++) {
            int i = order[k], o = k * 8;
            data[o] = Float.floatToRawIntBits(pts[i][0] / 16F);
            data[o + 1] = Float.floatToRawIntBits(pts[i][1] / 16F);
            data[o + 2] = Float.floatToRawIntBits(pts[i][2] / 16F);
            data[o + 3] = -1;
            data[o + 4] = Float.floatToRawIntBits(face.sprite.getU(Math.max(0, Math.min(16, uv[i][0]))));
            data[o + 5] = Float.floatToRawIntBits(face.sprite.getV(Math.max(0, Math.min(16, uv[i][1]))));
            data[o + 6] = 0;
            data[o + 7] = packed;
        }
        return new BakedQuad(data, face.tint, dir, face.sprite, true);
    }

    private static TextureAtlasSprite sprite(BigMat mat) {
        TextureAtlas atlas = Minecraft.getInstance().getModelManager().getAtlas(InventoryMenu.BLOCK_ATLAS);
        return atlas.getSprite(Pioneers.rl(mat.sprite()));
    }

    private static List<BakedQuad> bake(BigMesh mesh) {
        List<BakedQuad> out = new ArrayList<>(mesh.quads.size());
        Vector3f n = new Vector3f();
        for (BigMesh.Quad q : mesh.quads) {
            TextureAtlasSprite sp = sprite(q.mat);
            //UV a l'echelle, bornes a une case de texture ; decalage selon la position (pas deux faces identiques)
            float fu = Math.max(q.uv[4], q.uv[6]), fv = Math.max(q.uv[3], q.uv[5]);
            float su = Math.min(1, fu), sv = Math.min(1, fv);
            float hash = Math.abs(q.pos[0] * 0.37F + q.pos[1] * 0.71F + q.pos[2] * 0.53F) % 1F;
            float ou = (1 - su) * hash, ov = (1 - sv) * ((hash * 1.7F) % 1F);
            int[] data = new int[32];
            n.set(q.nx, q.ny, q.nz);
            int packed = (int) (n.x * 127) & 255 | ((int) (n.y * 127) & 255) << 8 | ((int) (n.z * 127) & 255) << 16;
            int light = q.glow > 0 ? (q.glow << 4) : 0;
            int a = q.color >>> 24 & 255, r = q.color >> 16 & 255, g = q.color >> 8 & 255, bl = q.color & 255;
            int abgr = a << 24 | bl << 16 | g << 8 | r;
            for (int i = 0; i < 4; i++) {
                float u = fu <= 0 ? 0 : q.uv[i * 2] / fu * su + ou, v = fv <= 0 ? 0 : q.uv[i * 2 + 1] / fv * sv + ov;
                int o = i * 8;
                data[o] = Float.floatToRawIntBits(q.pos[i * 3] / 16F);
                data[o + 1] = Float.floatToRawIntBits(q.pos[i * 3 + 1] / 16F);
                data[o + 2] = Float.floatToRawIntBits(q.pos[i * 3 + 2] / 16F);
                data[o + 3] = abgr;
                data[o + 4] = Float.floatToRawIntBits(sp.getU(u * 16));
                data[o + 5] = Float.floatToRawIntBits(sp.getV(v * 16));
                data[o + 6] = light;
                data[o + 7] = packed;
            }
            Direction dir = Direction.getNearest(q.nx, q.ny, q.nz);
            out.add(new BakedQuad(data, q.color != 0xFFFFFFFF ? -1 : -1, dir, sp, q.glow == 0));
        }
        return out;
    }
}
