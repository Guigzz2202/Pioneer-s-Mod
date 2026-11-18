package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.multiblock.MultiblockLogic;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.logistics.LogisticPoint;
import fr.pioneers.logistics.robotics.logistics.LogisticsRegistry;
import fr.pioneers.logistics.robotics.logistics.MarkerRole;
import fr.pioneers.logistics.robotics.logistics.PointConfig;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.37 : colonnes montantes de la plate-forme offshore (demande de Guillaume : « le joueur n'a pas a descendre lui-meme les tuyaux, c'est
 * la plate-forme qui en demande et qui descend ses tuyaux »).
 * <ul>
 *     <li>chaque colonne montante du pont (module {@code riser}, de l'eau dessous jusqu'au fond) a une profondeur cible (blocs d'eau) et une
 *     longueur deja descendue ; la plate-forme descend ses tubes elle-meme, un bloc toutes les {@link #STEP} ticks par colonne, et
 *     consomme un {@code production_tubing} par bloc ;</li>
 *     <li>seules les colonnes arrivees au fond produisent (2 operations chacune) ;</li>
 *     <li>tubes : stock interne rempli par les ports d'objets (entonnoirs, tuyaux, unites robotiques) ; la plate-forme est un point
 *     logistique DEMANDEUR (sur un de ses ports d'objets) tant qu'il lui manque des tubes : le reseau robotique la ravitaille seul ;</li>
 *     <li>colonne retiree : ses tubes reviennent au stock ; client : longueurs et etat pour le rendu ({@code RenderOffshore}).</li>
 * </ul>
 */
public final class OffshoreRisers implements MultiblockLogic {

    /** Objet consomme : un tube de production par bloc descendu. */
    public static final String TUBE = "production_tubing";
    /** Ticks pour descendre un bloc de tube. */
    public static final int STEP = 6;
    /** Tubes gardes en stock au plus au-dela du besoin (rien de plus n'entre). */
    public static final int SPARE = 16;

    public static final class Riser {

        public final BlockPos pos;
        public int target, lowered;

        public Riser(BlockPos pos, int target, int lowered) {
            this.pos = pos;
            this.target = target;
            this.lowered = lowered;
        }

        public boolean complete() {
            return lowered >= target && target > 0;
        }
    }

    private final ControllerTile tile;
    private final Map<BlockPos, Riser> risers = new LinkedHashMap<>();
    private int stock;
    /** Dernier pas de descente (client : interpolation du troncon qui descend). */
    private long lastStep;
    private boolean lowering;
    @Nullable
    private TubeRequest request;

    OffshoreRisers(ControllerTile tile) {
        this.tile = tile;
    }

    // ------------------------------------------------------------------
    // Serveur
    // ------------------------------------------------------------------

    /** Profondeur d'eau sous la colonne jusqu'au fond (0 si elle n'atteint pas un fond solide). */
    public static int depth(Level level, BlockPos module) {
        BlockPos.MutableBlockPos p = module.mutable();
        int water = 0;
        for (int i = 0; i < FreeformStructure.MAX_DEPTH; i++) {
            p.move(Direction.DOWN);
            if (p.getY() < level.getMinBuildHeight() || !level.isLoaded(p)) {
                return 0;
            }
            BlockState state = level.getBlockState(p);
            if (state.getFluidState().is(FluidTags.WATER)) {
                water++;
                continue;
            }
            return water >= FreeformStructure.MIN_WATER && state.isFaceSturdy(level, p, Direction.UP) ? water : 0;
        }
        return 0;
    }

    /** Structure (re)validee : colonnes nouvelles, retirees (tubes rendus au stock), profondeurs recalculees. */
    void onStructure(Level level, List<BlockPos> wet) {
        Map<BlockPos, Riser> next = new LinkedHashMap<>();
        boolean changed = false;
        for (BlockPos p : wet) {
            Riser old = risers.remove(p);
            int target = depth(level, p);
            Riser r = old != null ? old : new Riser(p.immutable(), target, 0);
            changed |= old == null || r.target != target;
            r.target = target;
            if (r.lowered > target) {
                stock += r.lowered - target;
                r.lowered = target;
            }
            next.put(r.pos, r);
        }
        for (Riser gone : risers.values()) {
            stock += gone.lowered;
            changed = true;
        }
        risers.clear();
        risers.putAll(next);
        if (changed) {
            tile.offshoreChanged();
        }
    }

    /** Structure defaite : tout le tube remonte (rendu au stock). */
    void onUnformed() {
        for (Riser r : risers.values()) {
            stock += r.lowered;
        }
        if (!risers.isEmpty()) {
            risers.clear();
            tile.offshoreChanged();
        }
    }

    /** Descente des tubes ; vrai si quelque chose a change (synchro). */
    boolean tick(Level level) {
        long now = level.getGameTime();
        boolean any = false;
        if (now % STEP == 0) {
            for (Riser r : risers.values()) {
                if (r.lowered < r.target && stock > 0) {
                    r.lowered++;
                    stock--;
                    any = true;
                }
            }
            if (any) {
                lastStep = now;
            }
            if (any != lowering) {
                lowering = any;
                tile.offshoreChanged();
            } else if (any) {
                tile.offshoreChanged();
            }
        }
        if (now % 40 == 7) {
            updateRequest(level);
        }
        return any;
    }

    /** Colonnes arrivees au fond (celles qui produisent). */
    public List<BlockPos> complete() {
        List<BlockPos> out = new ArrayList<>();
        for (Riser r : risers.values()) {
            if (r.complete()) {
                out.add(r.pos);
            }
        }
        return out;
    }

    public int count() {
        return risers.size();
    }

    /** Tubes encore a descendre (toutes colonnes). */
    public int remaining() {
        int n = 0;
        for (Riser r : risers.values()) {
            n += Math.max(0, r.target - r.lowered);
        }
        return n;
    }

    /** Tubes demandes : ce qui manque au stock pour finir toutes les colonnes. */
    public int needed() {
        return Math.max(0, remaining() - stock);
    }

    public int stock() {
        return stock;
    }

    // ------------------------------------------------------------------
    // Tubes : entree par les ports d'objets
    // ------------------------------------------------------------------

    public static boolean isTube(ItemStack stack) {
        return stack.is(PiIndustry.item(TUBE));
    }

    /** Une case virtuelle : le stock de tubes (jusqu'a 64 affiches) ; n'accepte que des tubes, tant qu'il en manque. */
    public IItemHandler handler() {
        return new IItemHandler() {
            @Override
            public int getSlots() {
                return 1;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return stock <= 0 ? ItemStack.EMPTY : new ItemStack(PiIndustry.item(TUBE), Math.min(64, stock));
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                if (!isTube(stack)) {
                    return stack;
                }
                int room = Math.max(0, remaining() + SPARE - stock);
                int moved = Math.min(room, stack.getCount());
                if (moved <= 0) {
                    return stack;
                }
                if (!simulate) {
                    stock += moved;
                    tile.offshoreChanged();
                }
                ItemStack rest = stack.copy();
                rest.shrink(moved);
                return rest;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                return ItemStack.EMPTY;
            }

            @Override
            public int getSlotLimit(int slot) {
                return 64;
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return isTube(stack);
            }
        };
    }

    // ------------------------------------------------------------------
    // Demande au reseau robotique
    // ------------------------------------------------------------------

    private void updateRequest(Level level) {
        Structure.Result s = tile.structureResult();
        BlockPos port = needed() > 0 && s != null ? itemPort(level, s) : null;
        if (request != null && (port == null || !request.port.equals(port))) {
            LogisticsRegistry.get(level).removePoint(request);
            request = null;
        }
        if (port != null && request == null) {
            request = new TubeRequest(this, level, port, accessSide(level, port, s));
            LogisticsRegistry.get(level).addPoint(request);
        }
        if (request != null) {
            request.refresh();
        }
    }

    /** Controleur casse : tous les tubes (stock et colonnes) reviennent en objets ; renvoie leur nombre. */
    int recover() {
        int n = stock;
        for (Riser r : risers.values()) {
            n += r.lowered;
        }
        stock = 0;
        risers.clear();
        return n;
    }

    /** Retire le point logistique (controleur casse ou decharge). */
    void dropRequest(Level level) {
        if (request != null) {
            LogisticsRegistry.get(level).removePoint(request);
            request = null;
        }
    }

    /** Premier port d'objets de la structure qui a une face libre (l'unite s'y tient). */
    @Nullable
    private static BlockPos itemPort(Level level, Structure.Result s) {
        for (BlockPos p : s.ports()) {
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof PortBlock port && port.type == PortBlock.Type.ITEM
                && accessSide(level, p, s) != null) {
                return p;
            }
        }
        return null;
    }

    @Nullable
    private static Direction accessSide(Level level, BlockPos port, @Nullable Structure.Result s) {
        for (Direction d : new Direction[]{Direction.UP, Direction.NORTH, Direction.SOUTH, Direction.EAST, Direction.WEST}) {
            BlockPos n = port.relative(d);
            if ((s == null || s.members() == null || !s.members().contains(n)) && level.getBlockState(n).getCollisionShape(level, n).isEmpty()) {
                return d;
            }
        }
        return null;
    }

    /** Point logistique demandeur de tubes de production, sur un port d'objets de la plate-forme. */
    private static final class TubeRequest implements LogisticPoint {

        private final OffshoreRisers owner;
        private final Level level;
        final BlockPos port;
        private final Direction side;
        private final PointConfig config = new PointConfig();
        private boolean removed;

        TubeRequest(OffshoreRisers owner, Level level, BlockPos port, @Nullable Direction side) {
            this.owner = owner;
            this.level = level;
            this.port = port.immutable();
            this.side = side == null ? Direction.UP : side;
            config.role = MarkerRole.REQUESTER;
            config.priority = 1;
            refresh();
        }

        void refresh() {
            config.entries.clear();
            int want = Math.min(64, owner.remaining() + SPARE);
            if (owner.needed() > 0 && want > 0) {
                config.entries.add(new PointConfig.Entry(LogisticResource.of(new ItemStack(PiIndustry.item(TUBE))), want));
            }
        }

        @Override
        public Level getPointLevel() {
            return level;
        }

        @Override
        public BlockPos getPointPos() {
            return port;
        }

        @Override
        public PointConfig getConfig() {
            return config;
        }

        @Override
        public void setConfig(PointConfig config) {
            //Reglee par la plate-forme elle-meme
        }

        @Override
        public Endpoint endpoint() {
            return new Endpoint(port, side, port.relative(side));
        }

        @Override
        public boolean isPointRemoved() {
            return removed || owner.request != this || owner.tile.isRemoved();
        }

        @Override
        public Component displayName() {
            return Component.translatable("block.pioneers.offshore_platform");
        }

        @Override
        public ItemStack displayIcon() {
            return new ItemStack(PiIndustry.item(TUBE));
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde et synchro
    // ------------------------------------------------------------------

    @Override
    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("stock", stock);
        ListTag list = new ListTag();
        for (Riser r : risers.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", r.pos.asLong());
            t.putInt("t", r.target);
            t.putInt("l", r.lowered);
            list.add(t);
        }
        tag.put("risers", list);
        return tag;
    }

    @Override
    public void load(CompoundTag tag) {
        stock = tag.getInt("stock");
        risers.clear();
        for (Tag e : tag.getList("risers", Tag.TAG_COMPOUND)) {
            CompoundTag t = (CompoundTag) e;
            BlockPos p = BlockPos.of(t.getLong("p"));
            risers.put(p, new Riser(p, t.getInt("t"), t.getInt("l")));
        }
    }

    /** Vue client : colonnes (position, cible, descendu), stock, besoin, descente en cours et son dernier pas. */
    CompoundTag writeView() {
        CompoundTag tag = save();
        tag.putInt("need", needed());
        tag.putBoolean("lowering", lowering);
        tag.putLong("step", lastStep);
        return tag;
    }

    /** Lecture client. */
    public record View(List<Riser> risers, int stock, int need, boolean lowering, long step) {

        public static View read(CompoundTag tag) {
            List<Riser> list = new ArrayList<>();
            for (Tag e : tag.getList("risers", Tag.TAG_COMPOUND)) {
                CompoundTag t = (CompoundTag) e;
                list.add(new Riser(BlockPos.of(t.getLong("p")), t.getInt("t"), t.getInt("l")));
            }
            return new View(List.copyOf(list), tag.getInt("stock"), tag.getInt("need"), tag.getBoolean("lowering"), tag.getLong("step"));
        }
    }

    // ------------------------------------------------------------------
    // API Multibloc (lot 6) : logique de la plate-forme declaree par la fiche (MultiblockLogic) ; le procede commun (puits) tourne
    // ------------------------------------------------------------------

    @Override
    public String saveKey() {
        return "offshore";
    }

    @Override
    public void onStructure(Structure.Result result, Level level) {
        onStructure(level, result.risers());
    }

    @Override
    public void onUnformed(Level level) {
        onUnformed();
    }

    @Override
    public boolean drivesWork() {
        return false;
    }

    @Override
    public boolean heats() {
        return false;
    }

    @Override
    public void structureTick(Level level) {
        //0.20.37 : la plate-forme descend elle-meme ses tubes
        tick(level);
    }

    @Override
    public IItemHandler inputItems() {
        //Les ports d'objets recoivent les tubes de production
        return handler();
    }

    /** Valeurs d'interface (indices d'avant) : 72 colonnes au fond | colonnes << 16, 73 tubes en stock, 74 tubes demandes. */
    @Override
    public long value(int index) {
        return switch (index) {
            case 72 -> complete().size() | (long) count() << 16;
            case 73 -> stock();
            case 74 -> needed();
            default -> 0;
        };
    }

    @Override
    public void writeClient(CompoundTag tag, boolean formed) {
        tag.put("offshoreView", writeView());
    }

    @Override
    public void chunkUnloaded(Level level) {
        dropRequest(level);
    }

    @Override
    public void removed(Level level, boolean unloading) {
        dropRequest(level);
        if (!unloading) {
            //0.20.37 : controleur casse : les tubes de production remontent et tombent
            int n = recover();
            net.minecraft.world.item.Item tube = PiIndustry.item(TUBE);
            BlockPos at = tile.getBlockPos();
            while (n > 0) {
                int k = Math.min(64, n);
                net.minecraft.world.Containers.dropItemStack(level, at.getX() + 0.5, at.getY() + 1, at.getZ() + 0.5, new ItemStack(tube, k));
                n -= k;
            }
        }
    }
}
