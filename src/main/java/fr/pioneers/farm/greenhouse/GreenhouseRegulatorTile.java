package fr.pioneers.farm.greenhouse;

import fr.pioneers.Pioneers;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.side.SideConfig;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.tree.FruitSaplingBlock;
import fr.pioneers.industry.multi.PortBlock;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BambooStalkBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.StemBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.IPlantable;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 3, specification « Le monde vivant » §2.2, decision 5) : REGULATEUR DE SERRE, controleur du multibloc « serre » (piece
 * scellee de forme libre). Il porte le CYCLE DE VIE COMMUN des multiblocs maison ({@link MultiblockCore.House} : revalidation au chargement,
 * erreur commune quoi / ou / attendu, registre des hotes), avec :
 * <ul>
 *   <li>le releve PUR {@link GreenhouseScan} lu sur le monde ({@link #kind}) ;</li>
 *   <li>une relecture a la pose ou au retrait d'un bloc de la paroi (cases inscrites au registre des hotes, et zone de veille de
 *   {@link GreenhouseRegistry} : sol, interieur, et autour d'une serre qui n'est pas formee), au plus une fois par seconde pour ce qui se
 *   passe a l'interieur, et sinon UNE FOIS PAR MINUTE ({@link #PERIOD}) ;</li>
 *   <li>l'inscription de l'interieur au registre des serres ({@link GreenhouseRegistry}, serveur et client) : les cultures le consultent
 *   sans recalcul ({@code Climate.greenhouse}) ;</li>
 *   <li>les fautes (trou d'une fuite, blocs fautifs) envoyees au client : interface, fiche au regard, plan des fautes au Maj + clic droit
 *   ({@link GreenhouseFaultPlan}).</li>
 * </ul>
 * Aucune energie, aucune case : l'interface (vue maison, corps {@code GreenhouseViews}) ne fait que montrer l'etat de la serre.
 */
public class GreenhouseRegulatorTile extends MachineTile implements MultiblockCore.House.Holder, MultiblockPort,
      fr.pioneers.common.projector.Projections.Dynamic {

    /** Relecture periodique : une fois par minute. */
    public static final int PERIOD = 1200;
    /** Ecart minimal entre deux relectures demandees par ce qui se passe dans la zone de veille. */
    public static final int EVENT_GAP = 20;
    /** Tag des blocs que les packs de donnees ajoutent a la paroi de serre. */
    public static final TagKey<Block> WALL_TAG = BlockTags.create(Pioneers.rl("greenhouse_wall"));

    /** Lot 3 : valeurs nommees de l'interface. */
    public static final UiValues.Key FORMED = UiValues.Key.of("gh_formed"), VOLUME = UiValues.Key.of("gh_volume"), CROPS = UiValues.Key.of("gh_crops"),
          SIZE_X = UiValues.Key.of("gh_size_x"), SIZE_Y = UiValues.Key.of("gh_size_y"), SIZE_Z = UiValues.Key.of("gh_size_z"),
          STATUS = UiValues.Key.of("gh_status"), AT_X = UiValues.Key.of("gh_at_x"), AT_Y = UiValues.Key.of("gh_at_y"),
          AT_Z = UiValues.Key.of("gh_at_z"), HAS_AT = UiValues.Key.of("gh_has_at"), EXACT = UiValues.Key.of("gh_exact"),
          FAULTS = UiValues.Key.of("gh_faults");

    private final MultiblockCore.House house = new MultiblockCore.House(new MultiblockCore.House.Host() {
        @Override
        public Level level() {
            return level;
        }

        @Override
        public BlockPos pos() {
            return worldPosition;
        }

        @Override
        public Direction front() {
            return facing();
        }

        @Nullable
        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            lastCheck = level.getGameTime();
            GreenhouseScan.Result r = GreenhouseScan.survey(grid, worldPosition, facing());
            if (r.status() == GreenhouseScan.Status.UNLOADED) {
                return null;
            }
            return r.formed() ? MultiblockCore.House.Survey.ok(r.walls(), r)
                  : MultiblockCore.House.Survey.fail(r.status().key(), r.at(), r);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            accept((GreenhouseScan.Result) survey.data());
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            if (survey.removal()) {
                return;
            }
            accept((GreenhouseScan.Result) survey.data());
        }

        @Override
        public void houseChanged() {
            setChanged();
            markSync();
        }

        @Nullable
        @Override
        public String expected(MultiblockCore.House.Survey survey) {
            GreenhouseScan.Result r = (GreenhouseScan.Result) survey.data();
            if (r == null || r.status() != GreenhouseScan.Status.LEAK && r.status() != GreenhouseScan.Status.WRONG_BLOCK) {
                return null;
            }
            return PiGreenhouse.glass(material).getDescriptionId();
        }
    }, PERIOD);

    /** Le monde vu par le releve. */
    private final GreenhouseScan.Grid grid = new GreenhouseScan.Grid() {
        @Override
        public GreenhouseScan.Kind kind(BlockPos pos) {
            return GreenhouseRegulatorTile.this.kind(pos);
        }

        @Nullable
        @Override
        public Direction facing(BlockPos pos) {
            return facingOf(pos);
        }
    };
    private GreenhouseScan.Status status = GreenhouseScan.Status.NO_ROOM;
    private int volume, crops;
    private int[] size = new int[3];
    @Nullable
    private BlockPos at;
    private boolean exact;
    private List<BlockPos> faults = List.of();
    /** Bloc a la case fautive (id), « minecraft:air » pour un trou. */
    private String faultBlock = "";
    private GreenhouseMaterial material = GreenhouseMaterial.TREATED;
    /** Interieur forme, pour le client (coin, taille, bits). */
    private int[] box = new int[6];
    private long[] bits = new long[0];
    private long lastCheck = Long.MIN_VALUE / 2;
    /** Plan des fautes en cache (meme objet tant que les fautes ne changent pas : la projection le reconnait d'un clic a l'autre). */
    @Nullable
    private GreenhouseFaultPlan plan;
    private boolean pending;

    public GreenhouseRegulatorTile(BlockPos pos, BlockState state) {
        super(PiGreenhouse.REGULATOR_TILE.get(), pos, state, 0, 0);
    }

    // ------------------------------------------------------------------ releve sur le monde

    /** Contenu d'une case pour le releve (voir {@link GreenhouseScan}). */
    GreenhouseScan.Kind kind(BlockPos pos) {
        if (level == null || !level.isLoaded(pos)) {
            return GreenhouseScan.Kind.UNLOADED;
        }
        return classify(level.getBlockState(pos));
    }

    /** Classement d'un etat de bloc (commun au jeu, au banc et aux GameTests). */
    public static GreenhouseScan.Kind classify(BlockState state) {
        Block b = state.getBlock();
        if (b instanceof GreenhouseRegulatorBlock) {
            return GreenhouseScan.Kind.REGULATOR;
        }
        if (b instanceof GreenhouseWall || is(state, WALL_TAG)) {
            return GreenhouseScan.Kind.WALL;
        }
        if (b instanceof DoorBlock || is(state, BlockTags.DOORS)) {
            return GreenhouseScan.Kind.DOOR;
        }
        return passable(state) ? GreenhouseScan.Kind.OPEN : GreenhouseScan.Kind.SOLID;
    }

    /**
     * Ce qui ne bouche pas : air, fluides, tout bloc dont la forme de collision n'est pas un cube plein (dalles, escaliers, vitres fines,
     * barrieres, terre labouree : une dalle dans un mur est un TROU), plantes (cultures, troncs et feuilles d'un arbre de la serre, bambou...).
     */
    public static boolean passable(BlockState state) {
        if (state.getBlock() instanceof net.minecraft.world.level.block.FarmBlock || state.getBlock() instanceof net.minecraft.world.level.block.DirtPathBlock) {
            return false;                    //terre labouree, chemin : un sol (15 px), pas un trou
        }
        if (state.isAir() || !state.isCollisionShapeFullBlock(net.minecraft.world.level.EmptyBlockGetter.INSTANCE, BlockPos.ZERO)) {
            return true;
        }
        Block b = state.getBlock();
        return is(state, BlockTags.LEAVES) || is(state, BlockTags.LOGS) || b instanceof IPlantable || b instanceof BambooStalkBlock
               || b instanceof FarmCropBlock;
    }

    /** Tag du bloc ; faux si les tags ne sont pas lies (banc hors jeu). */
    private static boolean is(BlockState state, TagKey<Block> tag) {
        try {
            return state.is(tag);
        } catch (IllegalStateException e) {
            return false;
        }
    }

    /** Une culture (comptee dans l'interface) ? */
    public static boolean isCrop(BlockState state) {
        Block b = state.getBlock();
        return b instanceof FarmCropBlock || b instanceof CropBlock || b instanceof StemBlock || b instanceof FruitSaplingBlock;
    }

    @Nullable
    private Direction facingOf(BlockPos pos) {
        BlockState s = level == null ? null : level.getBlockState(pos);
        return s != null && s.hasProperty(MachineBlock.FACING) ? s.getValue(MachineBlock.FACING) : null;
    }

    /** Applique un releve : etat, interieur au registre des serres, fautes ; voyant du bloc. */
    private void accept(@Nullable GreenhouseScan.Result r) {
        if (r == null || level == null) {
            return;
        }
        status = r.status();
        volume = r.volume();
        size = r.size();
        at = r.at();
        exact = r.exact();
        faults = new ArrayList<>(r.faults());
        plan = null;
        if (r.status() == GreenhouseScan.Status.LEAK && r.hole() != null) {
            faults = List.of(r.hole());
        } else if (r.status() == GreenhouseScan.Status.SHARED && r.other() != null) {
            faults = List.of();
        }
        faultBlock = at == null ? "" : String.valueOf(BuiltInRegistries.BLOCK.getKey(level.getBlockState(at).getBlock()));
        material = majority(r.walls());
        if (r.formed()) {
            int c = 0;
            for (BlockPos p : r.interior()) {
                if (isCrop(level.getBlockState(p))) {
                    c++;
                }
            }
            crops = c;
            GreenhouseRegistry.putFormed(level, worldPosition, r.interior(), r.min(), r.max());
            GreenhouseRegistry.Entry e = GreenhouseRegistry.get(level, worldPosition);
            box = e == null ? new int[6] : e.box();
            bits = e == null ? new long[0] : e.bits();
        } else {
            crops = 0;
            GreenhouseRegistry.putUnformed(level, worldPosition);
            box = new int[6];
            bits = new long[0];
        }
        BlockState state = getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != r.formed()) {
            level.setBlock(worldPosition, state.setValue(MachineBlock.ACTIVE, r.formed()), Block.UPDATE_CLIENTS);
        }
        setChanged();
        markSync();
    }

    /** Matiere la plus presente dans la paroi (vitre conseillee par le plan des fautes). */
    private GreenhouseMaterial majority(Set<BlockPos> walls) {
        int iron = 0, treated = 0;
        for (BlockPos p : walls) {
            if (level.getBlockState(p).getBlock() instanceof GreenhouseWall w && !(level.getBlockState(p).getBlock() instanceof GreenhouseRegulatorBlock)) {
                if (w.greenhouseMaterial() == GreenhouseMaterial.IRON) {
                    iron++;
                } else {
                    treated++;
                }
            }
        }
        return iron > treated ? GreenhouseMaterial.IRON : GreenhouseMaterial.TREATED;
    }

    // ------------------------------------------------------------------ cycle

    @Override
    protected void tick() {
        if (pending && level.getGameTime() - lastCheck >= EVENT_GAP) {
            pending = false;
            house.requestCheck();
        }
        house.tick();
    }

    /** Un bloc a change dans la zone de veille (registre des serres) : relire au plus tot, au plus une fois par seconde. */
    public void noticeChange() {
        pending = true;
    }

    /** Le joueur ouvre l'interface ou regarde la fiche : relire si le dernier releve date de plus d'une seconde. */
    public void refresh() {
        if (level != null && level.getGameTime() - lastCheck >= EVENT_GAP) {
            house.requestCheck();
        }
    }

    /** Releve immediat (GameTests, pose du regulateur) ; vrai si la serre est formee. */
    public boolean checkNow() {
        return house.checkNow();
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    /** Contrat de port commun : la serre ne prete aucune capacite (ni energie, ni objets). */
    @Nullable
    @Override
    public Object portHandler(PortBlock.Type type, boolean output, BlockPos port) {
        return null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
        if (level != null && !level.isClientSide) {
            GreenhouseRegistry.putUnformed(level, worldPosition);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        house.removed(true);
        if (level != null) {
            GreenhouseRegistry.remove(level, worldPosition);
        }
    }

    // ------------------------------------------------------------------ lecture (interface, fiche, plan, GameTests)

    public GreenhouseScan.Status status() {
        return status;
    }

    public boolean formed() {
        return status == GreenhouseScan.Status.FORMED;
    }

    public int volume() {
        return volume;
    }

    public int crops() {
        return crops;
    }

    /** Dimensions de l'interieur {x, y, z}. */
    public int[] size() {
        return size.clone();
    }

    @Nullable
    public BlockPos at() {
        return at;
    }

    public boolean exact() {
        return exact;
    }

    public List<BlockPos> faults() {
        return faults;
    }

    /** Nom (cle de langue) du bloc a la case fautive, ou null. */
    @Nullable
    public String faultBlockKey() {
        if (faultBlock.isEmpty()) {
            return null;
        }
        ResourceLocation id = ResourceLocation.tryParse(faultBlock);
        Block b = id == null ? Blocks.AIR : BuiltInRegistries.BLOCK.get(id);
        return b.getDescriptionId();
    }

    public GreenhouseMaterial material() {
        return material;
    }

    // ------------------------------------------------------------------ interface standard (vue maison)

    @Nullable
    @Override
    public SideConfig getSideConfig() {
        return null;
    }

    @Override
    protected List<ItemSlot> automationSlots() {
        return List.of();
    }

    @Override
    public int guiHeight() {
        return StandardLayout.ZONE;
    }

    @Override
    public int guiInventory() {
        return StandardLayout.INVENTORY;
    }

    @Override
    public int guiWidth() {
        return StandardLayout.WIDTH;
    }

    @Override
    public List<StandardLayout.Part> guiParts() {
        return StandardLayout.body(guiWidth(), guiHeight());
    }

    @Override
    public String guiTier() {
        return "steel";
    }

    @Override
    protected boolean namedValues() {
        return true;
    }

    @Override
    protected void declareValues(UiValues.Builder values) {
        values.add(FORMED, () -> formed() ? 1 : 0).add(VOLUME, () -> volume).add(CROPS, () -> crops).add(SIZE_X, () -> size[0])
              .add(SIZE_Y, () -> size[1]).add(SIZE_Z, () -> size[2]).add(STATUS, () -> status.ordinal())
              .add(AT_X, () -> at == null ? 0 : at.getX()).add(AT_Y, () -> at == null ? 0 : at.getY()).add(AT_Z, () -> at == null ? 0 : at.getZ())
              .add(HAS_AT, () -> at == null ? 0 : 1).add(EXACT, () -> exact ? 1 : 0).add(FAULTS, () -> faults.size());
    }

    // ------------------------------------------------------------------ client

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        house.write(tag);
        CompoundTag g = new CompoundTag();
        g.putInt("status", status.ordinal());
        g.putInt("volume", volume);
        g.putInt("crops", crops);
        g.putIntArray("size", size);
        if (at != null) {
            g.putLong("at", at.asLong());
        }
        g.putBoolean("exact", exact);
        g.putString("block", faultBlock);
        g.putString("material", material.id());
        long[] f = new long[faults.size()];
        for (int i = 0; i < f.length; i++) {
            f[i] = faults.get(i).asLong();
        }
        g.putLongArray("faults", f);
        g.putIntArray("box", box);
        g.putLongArray("bits", bits);
        tag.put("greenhouse", g);
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        house.read(tag);
        if (!tag.contains("greenhouse")) {
            return;
        }
        CompoundTag g = tag.getCompound("greenhouse");
        GreenhouseScan.Status[] all = GreenhouseScan.Status.values();
        status = all[Math.max(0, Math.min(all.length - 1, g.getInt("status")))];
        volume = g.getInt("volume");
        crops = g.getInt("crops");
        int[] s = g.getIntArray("size");
        size = s.length == 3 ? s : new int[3];
        at = g.contains("at") ? BlockPos.of(g.getLong("at")) : null;
        exact = g.getBoolean("exact");
        faultBlock = g.getString("block");
        material = "iron".equals(g.getString("material")) ? GreenhouseMaterial.IRON : GreenhouseMaterial.TREATED;
        List<BlockPos> f = new ArrayList<>();
        for (long l : g.getLongArray("faults")) {
            f.add(BlockPos.of(l));
        }
        if (!f.equals(faults)) {
            plan = null;
        }
        faults = f;
        box = g.getIntArray("box");
        bits = g.getLongArray("bits");
        if (level != null) {
            GreenhouseRegistry.putClient(level, worldPosition, box, bits);
        }
    }

    /** Maj + clic droit : le plan des fautes d'abord (serre non formee qui en a), puis l'exemple de serre. */
    @Override
    public List<fr.pioneers.common.projector.Blueprint> projectionPlans() {
        if (formed() || faults.isEmpty()) {
            return List.of();
        }
        if (plan == null) {
            plan = new GreenhouseFaultPlan(faultOffsets(), material, getBlockState().getBlock());
        }
        return List.of(plan);
    }

    /** Cases distinctes du plan des fautes (relatives au regulateur). */
    public Set<BlockPos> faultOffsets() {
        Set<BlockPos> out = new HashSet<>();
        for (BlockPos p : faults) {
            out.add(p.subtract(worldPosition));
        }
        return out;
    }
}
