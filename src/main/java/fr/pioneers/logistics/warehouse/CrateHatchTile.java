package fr.pioneers.logistics.warehouse;

import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.tech.bronze.BronzeEvents;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1) : TRAPPE de la caisse d'entrepot, son COEUR : multibloc maison sur l'API Multibloc (cycle de vie commun
 * {@link MultiblockCore.House} : verification periodique et a la demande, revalidation au chargement, registre des hotes pour les pieces,
 * erreur commune quoi / ou / attendu), plan tire de {@link CrateLayout} (3 longueurs, axe X ou Z, la plus longue complete se forme). Elle
 * GARDE LE CONTENU ({@link CrateStorage}, cle {@code storage}) : casser une autre piece defait la caisse sans rien perdre (ports fermes) ;
 * casser la trappe donne la trappe avec son contenu (table de butin : {@code BlockEntityTag.storage}), qui revient a la pose. La poulie
 * d'entrepot posee SUR la trappe en fait une station du reseau (les colis descendent par la trappe).
 */
public class CrateHatchTile extends PiBlockEntity implements MultiblockCore.House.Holder, MultiblockPort {

    /** Cle de l'erreur commune de la caisse (quoi). */
    public static final String ERROR = "gui.pioneers.multiblock.house.warehouse_crate";
    private final CrateStorage storage = new CrateStorage();
    private final LazyOptional<IItemHandler> items = LazyOptional.of(storage::handler);
    @Nullable
    private CrateLayout.Found found;
    private final Set<BlockPos> painted = new HashSet<>();
    private boolean clientDirty, comparatorDirty;
    private long lastSync;
    /** Client : longueur formee (-1 : non formee), resume du contenu. */
    private int clientLength = -1;
    private long clientTotal;
    private int clientTypes, clientMax;
    private float clientFill;
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
            return Direction.NORTH;
        }

        @Override
        public MultiblockCore.House.Survey survey() {
            if (level == null) {
                return null;
            }
            CrateLayout.Survey s = CrateLayout.survey(Structure.View.of(level), worldPosition);
            if (s.unreadable()) {
                return null;
            }
            if (s.found() == null) {
                return MultiblockCore.House.Survey.fail(ERROR, s.fault(), s);
            }
            return MultiblockCore.House.Survey.ok(s.found().cells(), s);
        }

        @Override
        public void formed(MultiblockCore.House.Survey survey, boolean first) {
            CrateLayout.Found f = survey.data() instanceof CrateLayout.Survey s ? s.found() : null;
            boolean changed = f == null || found == null || f.length() != found.length() || f.axis() != found.axis() || !f.origin().equals(found.origin())
                              || !f.ports().equals(found.ports());
            found = f;
            if (f != null) {
                storage.limits(f.length());
            }
            if (first && level instanceof ServerLevel server) {
                BronzeEvents.award(server, worldPosition, "warehouse_crate");
            }
            if (first || changed) {
                repaint();
                WarehouseNetwork.changed();
                markClient();
            }
        }

        @Override
        public void unformed(MultiblockCore.House.Survey survey, boolean broken) {
            found = null;
            if (broken) {
                if (!survey.removal()) {
                    repaint();
                }
                WarehouseNetwork.changed();
                markClient();
            }
        }

        @Override
        public void houseChanged() {
            markClient();
        }
    }, 20);

    public CrateHatchTile(BlockPos pos, BlockState state) {
        super(PiWarehouse.HATCH_TILE.get(), pos, state);
        storage.listener(() -> {
            setChanged();
            clientDirty = true;
            comparatorDirty = true;
            WarehouseNetwork.contentsChanged();
        });
    }

    public CrateStorage storage() {
        return storage;
    }

    public LazyOptional<IItemHandler> itemCapability() {
        return items;
    }

    @Override
    public MultiblockCore.House house() {
        return house;
    }

    @Override
    public void requestCheck() {
        house.requestCheck();
    }

    public boolean formed() {
        return level != null && level.isClientSide ? clientLength >= 0 : house.formed() && found != null;
    }

    /** Longueur de la caisse formee (courte si non formee). */
    public CrateLayout.Length length() {
        if (level != null && level.isClientSide) {
            return CrateLayout.Length.of(Math.max(0, clientLength));
        }
        return found == null ? CrateLayout.Length.SHORT : found.length();
    }

    @Nullable
    public CrateLayout.Found found() {
        return found;
    }

    /** Poulie d'entrepot qui dessert la trappe (station du reseau) : posee dessus, ou plus haut au-dessus d'une colonne vide (lot 2). */
    @Nullable
    public PulleyTile station() {
        return WarehouseLines.pulleyAbove(level, worldPosition);
    }

    // ------------------------------------------------------------------ client (resume)

    public long clientTotal() {
        return level != null && level.isClientSide ? clientTotal : storage.total();
    }

    public int clientTypes() {
        return level != null && level.isClientSide ? clientTypes : storage.types();
    }

    public int clientMaxTypes() {
        return level != null && level.isClientSide ? clientMax : storage.maxTypes();
    }

    public float clientFill() {
        return level != null && level.isClientSide ? clientFill : (float) storage.fill();
    }

    /** Peint les pieces (place dans la grande caisse) ; depeint celles qui ne sont plus dans la caisse. */
    private void repaint() {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        Set<BlockPos> now = new HashSet<>();
        if (found != null) {
            for (BlockPos p : found.cells()) {
                paint(server, p, found.part(p), found.axis());
                now.add(p);
            }
        }
        for (BlockPos p : painted) {
            if (!now.contains(p) && server.isLoaded(p)) {
                paint(server, p, 0, Direction.Axis.Z);
            }
        }
        if (found == null && server.isLoaded(worldPosition)) {
            paint(server, worldPosition, 0, Direction.Axis.Z);
        }
        painted.clear();
        painted.addAll(now);
    }

    private static void paint(ServerLevel level, BlockPos pos, int part, Direction.Axis axis) {
        BlockState s = level.getBlockState(pos);
        if (!(s.getBlock() instanceof CrateBlock)) {
            return;
        }
        BlockState want = s.setValue(CrateBlock.PART, part).setValue(CrateBlock.AXIS, axis);
        if (want != s) {
            level.setBlock(pos, want, Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------ tick

    public void serverTick(ServerLevel level) {
        house.tick();
        long now = level.getGameTime();
        if (comparatorDirty && now % 10 == 0) {
            comparatorDirty = false;
            level.updateNeighbourForOutputSignal(worldPosition, getBlockState().getBlock());
            if (found != null) {
                for (BlockPos p : found.ports()) {
                    level.updateNeighbourForOutputSignal(p, level.getBlockState(p).getBlock());
                }
            }
        }
        if (clientDirty && now - lastSync >= 20) {
            clientDirty = false;
            lastSync = now;
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    private void markClient() {
        clientDirty = true;
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        house.load();
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        house.removed(true);
        WarehouseNetwork.changed();
    }

    /** Juste avant la disparition de la trappe (casse) : caisse defaite, pieces depeintes (le contenu part dans l'objet). */
    public void broken() {
        house.removed(false);
        if (level instanceof ServerLevel) {
            found = null;
            repaint();
        }
        WarehouseNetwork.changed();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        //Aucun acces direct : seuls les ports pretent le contenu (la trappe est le chemin des colis)
        return super.getCapability(cap, side);
    }

    @Nullable
    @Override
    public Object portHandler(fr.pioneers.tech.industry.multi.PortBlock.Type type, boolean output, BlockPos port) {
        return house.formed() && type == fr.pioneers.tech.industry.multi.PortBlock.Type.ITEM ? storage.handler() : null;
    }

    // ------------------------------------------------------------------ sauvegarde et client

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        tag.put("storage", storage.save());
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        storage.load(tag.getCompound("storage"));
        if (tag.contains("client")) {
            readClient(tag.getCompound("client"));
        }
    }

    private CompoundTag writeClient() {
        CompoundTag c = new CompoundTag();
        house.write(c);
        c.putInt("len", formed() ? length().ordinal() : -1);
        c.putLong("total", storage.total());
        c.putInt("types", storage.types());
        c.putInt("max", storage.maxTypes());
        c.putFloat("fill", (float) storage.fill());
        return c;
    }

    private void readClient(CompoundTag c) {
        house.read(c);
        clientLength = c.contains("len") ? c.getInt("len") : -1;
        clientTotal = c.getLong("total");
        clientTypes = c.getInt("types");
        clientMax = c.getInt("max");
        clientFill = c.getFloat("fill");
    }

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = super.getUpdateTag();
        tag.put("client", writeClient());
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        if (tag.contains("client")) {
            readClient(tag.getCompound("client"));
        }
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this, t -> {
            CompoundTag tag = new CompoundTag();
            tag.put("client", writeClient());
            return tag;
        });
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        if (packet.getTag() != null) {
            handleUpdateTag(packet.getTag());
        }
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }
}
