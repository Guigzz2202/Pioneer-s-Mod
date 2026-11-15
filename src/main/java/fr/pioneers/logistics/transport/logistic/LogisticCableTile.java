package fr.pioneers.logistics.transport.logistic;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.block.Covers;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.registries.PiTransport;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §3.2, §3.3) : tile d'un cable logistique a fiche, transparent ou coupe (jamais sur un cable nu).
 * <ul>
 *     <li>{@code cut} : 6 bits (coupure a la cle) ;</li>
 *     <li>configuration du reseau : la meme instance que celle du reseau tant qu'il vit (une edition est donc deja « ecrite » ; le
 *     gestionnaire marque seulement le chunk a sauver) ; sauvee avec les seuls points des raccords que la tile touche ;</li>
 *     <li>fiches : capacites d'entree (objets, fluides, gaz, energie, donnees) sur chaque face {@code PLUG} : une machine qui ejecte dans la
 *     fiche voit son offre distribuee tout de suite par le reseau ({@link LogisticNetwork#offer}) ;</li>
 *     <li>client : {@code roles} (2 bits x 6, apercu a la cle) et {@code flow} (cables transparents) ; jamais la configuration ;</li>
 *     <li>1.0.4-beta (lot 27) : la cache ({@link Covers}, sauvee et envoyee au client) ; un cable cache a toujours sa tile ;</li>
 *     <li>1.0.5-beta (lot 45, retour 63) : {@code det} : 6 bits, faces DECONNECTEES (machine reglee sur « Rien » dans tous les canaux d'un
 *     reseau a canaux) : la fiche est retiree de l'etat du bloc (NONE) mais la face reste une fiche latente du reseau ; sauve et envoye au
 *     client (meme calcul des branchements des deux cotes).</li>
 * </ul>
 */
public class LogisticCableTile extends PiBlockEntity implements Covers.Coverable {

    /** Client : tiles a fiche chargees (apercu des roles a la cle), sans lecture du monde. */
    public static final Set<LogisticCableTile> CLIENT_PLUGS = ConcurrentHashMap.newKeySet();

    private int cut;
    /** 1.0.5-beta (lot 45, retour 63) : faces deconnectees (fiches latentes). */
    private int detached;
    @Nullable
    private NetworkConfig config;
    private int roles;
    private CompoundTag flow = new CompoundTag();
    @Nullable
    private BlockState cover;
    /** Capacites d'entree par (face, type) : jamais recreees tant que la fiche reste (les machines gardent leur LazyOptional). */
    private final LazyOptional<?>[] inlets = new LazyOptional<?>[6 * ChannelKind.COUNT];
    @Nullable
    private Object renderCache;
    @Nullable
    private AABB renderBox;

    public LogisticCableTile(BlockPos pos, BlockState state) {
        super(PiTransport.LOGISTIC_CABLE_TILE.get(), pos, state);
    }

    @Nullable
    public LogisticCableBlock cable() {
        return getBlockState().getBlock() instanceof LogisticCableBlock block ? block : null;
    }

    public boolean isCut(Direction side) {
        return (cut >> side.ordinal() & 1) != 0;
    }

    public void setCut(Direction side, boolean value) {
        int next = value ? cut | 1 << side.ordinal() : cut & ~(1 << side.ordinal());
        if (next != cut) {
            cut = next;
            setChanged();
        }
    }

    public int cutMask() {
        return cut;
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 45, retour 63) : faces deconnectees
    // ------------------------------------------------------------------

    public boolean isDetached(Direction side) {
        return (detached >> side.ordinal() & 1) != 0;
    }

    public int detachedMask() {
        return detached;
    }

    /** Gestionnaire du reseau : faces deconnectees (l'appelant recalcule l'etat du bloc ; l'index suit, sans reconstruction). */
    public void setDetachedMask(int mask) {
        mask &= 63;
        if (mask != detached) {
            int before = detached;
            detached = mask;
            setChanged();
            sync();
            if (level != null && !level.isClientSide) {
                LogisticNetworks.detachedChanged(level, worldPosition, before, detached);
            }
        }
    }

    /** Bloc : une face deconnectee ne touche plus de machine (machine cassee, coupure a la cle) : sa fiche latente part. */
    public void clearDetached(Direction side) {
        if (isDetached(side)) {
            setDetachedMask(detached & ~(1 << side.ordinal()));
        }
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 27) : cache
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState cover() {
        return cover;
    }

    @Override
    public void setCover(@Nullable BlockState cover) {
        if (this.cover != cover) {
            this.cover = cover;
            setChanged();
            sync();
        }
    }

    @NotNull
    @Override
    public ModelData getModelData() {
        return cover == null ? ModelData.EMPTY : ModelData.builder().with(Covers.COVER, cover).build();
    }

    // ------------------------------------------------------------------
    // Configuration (serveur)
    // ------------------------------------------------------------------

    @Nullable
    public NetworkConfig config() {
        return config;
    }

    /** Le gestionnaire donne la configuration (partagee) du reseau ; {@code changed} : marquer le chunk a sauver. */
    public void setConfig(@Nullable NetworkConfig config, boolean changed) {
        this.config = config;
        if (changed) {
            setChanged();
        }
    }

    /** Cles des raccords touches par cette tile (machines devant ses fiches). */
    public boolean touches(long key) {
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof LogisticCableBlock)) {
            return false;
        }
        for (Direction d : Direction.values()) {
            //1.0.5-beta (lot 45, retour 63) : une face deconnectee garde les reglages de sa machine (« Rien » partout, filtres...)
            if ((LogisticCableBlock.link(state, d) == LogisticCableBlock.Link.PLUG || isDetached(d)) && worldPosition.relative(d).asLong() == key) {
                return true;
            }
        }
        return false;
    }

    public void setRoles(int roles) {
        if (this.roles != roles) {
            this.roles = roles;
            sync();
        }
    }

    public int roles() {
        return roles;
    }

    // ------------------------------------------------------------------
    // Flux (cables transparents)
    // ------------------------------------------------------------------

    public CompoundTag getFlow() {
        return flow;
    }

    /** Serveur : nouveau flux a montrer (seulement les changements, le reseau compare deja). */
    public void setFlow(CompoundTag flow) {
        this.flow = flow;
        sync();
    }

    @Nullable
    public Object renderCache() {
        return renderCache;
    }

    public void setRenderCache(@Nullable Object cache) {
        renderCache = cache;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        if (renderBox == null) {
            renderBox = new AABB(worldPosition).inflate(0.125);
        }
        return renderBox;
    }

    private void sync() {
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), Block.UPDATE_CLIENTS);
        }
    }

    // ------------------------------------------------------------------
    // Cycle de vie
    // ------------------------------------------------------------------

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null) {
            return;
        }
        if (level.isClientSide) {
            if (LogisticCableBlock.plugMask(getBlockState()) != 0) {
                CLIENT_PLUGS.add(this);
            }
        } else {
            LogisticNetworks.loaded(level, worldPosition, detached);
        }
    }

    @Override
    public void setRemoved() {
        super.setRemoved();
        CLIENT_PLUGS.remove(this);
    }

    @Override
    public void onChunkUnloaded() {
        super.onChunkUnloaded();
        CLIENT_PLUGS.remove(this);
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (cut != 0) {
            tag.putByte("cut", (byte) cut);
        }
        if (detached != 0) {
            tag.putByte("det", (byte) detached);
        }
        NetworkConfig current = level == null || level.isClientSide ? config : LogisticNetworks.configForSave(level, worldPosition, config);
        if (current != null) {
            tag.put("cfg", current.save(this::touches));
        }
        Covers.save(tag, cover);
        tag.putByte("v", (byte) 1);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        cut = tag.getByte("cut") & 63;
        detached = tag.getByte("det") & 63;
        config = tag.contains("cfg") ? NetworkConfig.load(tag.getCompound("cfg")) : null;
        cover = Covers.load(tag);
    }

    // ------------------------------------------------------------------
    // Synchro client : roles agreges et flux, jamais la configuration
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public CompoundTag getUpdateTag() {
        CompoundTag tag = new CompoundTag();
        tag.putShort("roles", (short) roles);
        if (detached != 0) {
            tag.putByte("det", (byte) detached);
        }
        if (!flow.isEmpty()) {
            tag.put("flow", flow);
        }
        Covers.save(tag, cover);
        return tag;
    }

    @Override
    public void handleUpdateTag(CompoundTag tag) {
        readClient(tag);
    }

    @Override
    public void onDataPacket(Connection net, ClientboundBlockEntityDataPacket packet) {
        CompoundTag tag = packet.getTag();
        if (tag != null) {
            readClient(tag);
        }
    }

    private void readClient(CompoundTag tag) {
        roles = tag.getShort("roles") & 0xFFF;
        detached = tag.getByte("det") & 63;
        flow = tag.getCompound("flow");
        BlockState received = Covers.load(tag);
        if (received != cover) {
            cover = received;
            Covers.changedOnClient(this);
        }
        if (level != null && level.isClientSide && LogisticCableBlock.plugMask(getBlockState()) != 0) {
            CLIENT_PLUGS.add(this);
        }
    }

    @Override
    public ClientboundBlockEntityDataPacket getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    // ------------------------------------------------------------------
    // Fiches : capacites d'entree (ejection des machines)
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
        LogisticCableBlock cable = cable();
        if (cable == null || side == null || LogisticCableBlock.link(getBlockState(), side) != LogisticCableBlock.Link.PLUG) {
            return super.getCapability(capability, side);
        }
        ChannelKind kind = capability == ForgeCapabilities.ITEM_HANDLER ? ChannelKind.ITEM
              : capability == ForgeCapabilities.FLUID_HANDLER ? ChannelKind.FLUID
              : capability == PioneersAPI.GAS_HANDLER ? ChannelKind.GAS
              : capability == ForgeCapabilities.ENERGY ? ChannelKind.ENERGY
              : capability == PioneersAPI.DATA_HANDLER ? ChannelKind.DATA : null;
        //Frontiere de claim : la machine d'en face doit pouvoir echanger avec ce cable
        if (kind == null || !cable.level.permits(kind) || level != null && !ClanApi.canTransfer(level, worldPosition, worldPosition.relative(side))) {
            return super.getCapability(capability, side);
        }
        int index = side.ordinal() * ChannelKind.COUNT + kind.ordinal();
        LazyOptional<?> existing = inlets[index];
        if (existing == null || !existing.isPresent()) {
            long key = worldPosition.relative(side).asLong();
            existing = LazyOptional.of(() -> inlet(kind, key));
            inlets[index] = existing;
        }
        return existing.cast();
    }

    private Object inlet(ChannelKind kind, long key) {
        return switch (kind) {
            case ITEM -> new ItemInlet(key);
            case FLUID -> new FluidInlet(key);
            case GAS -> new GasInlet(key);
            case ENERGY -> new EnergyInlet(key);
            default -> new DataInlet(key);
        };
    }

    /** Une fiche disparait : ses capacites d'entree sont invalidees (les machines lachent leur LazyOptional). */
    @Override
    @SuppressWarnings("deprecation")
    public void setBlockState(@NotNull BlockState state) {
        super.setBlockState(state);
        if (!(state.getBlock() instanceof LogisticCableBlock)) {
            return;
        }
        for (Direction side : Direction.values()) {
            if (LogisticCableBlock.link(state, side) == LogisticCableBlock.Link.PLUG) {
                continue;
            }
            for (int k = 0; k < ChannelKind.COUNT; k++) {
                int index = side.ordinal() * ChannelKind.COUNT + k;
                if (inlets[index] != null) {
                    inlets[index].invalidate();
                    inlets[index] = null;
                }
            }
        }
        if (level != null && level.isClientSide) {
            if (LogisticCableBlock.plugMask(state) != 0) {
                CLIENT_PLUGS.add(this);
            } else {
                CLIENT_PLUGS.remove(this);
            }
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (int i = 0; i < inlets.length; i++) {
            if (inlets[i] != null) {
                inlets[i].invalidate();
                inlets[i] = null;
            }
        }
    }

    @Nullable
    private LogisticNetwork network() {
        return level == null || level.isClientSide ? null : LogisticNetworks.network(level, worldPosition);
    }

    private long offer(long key, ChannelKind kind, Object resource, boolean simulate) {
        LogisticNetwork network = network();
        return network == null ? 0 : network.offer(key, kind, resource, simulate);
    }

    private final class ItemInlet implements IItemHandler {

        private final long key;

        ItemInlet(long key) {
            this.key = key;
        }

        @Override
        public int getSlots() {
            return 1;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty()) {
                return stack;
            }
            long placed = offer(key, ChannelKind.ITEM, stack, simulate);
            return placed <= 0 ? stack : stack.copyWithCount(stack.getCount() - (int) placed);
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
            return true;
        }
    }

    private final class FluidInlet implements IFluidHandler {

        private final long key;

        FluidInlet(long key) {
            this.key = key;
        }

        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return true;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return resource.isEmpty() ? 0 : (int) offer(key, ChannelKind.FLUID, resource, action.simulate());
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            return FluidStack.EMPTY;
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            return FluidStack.EMPTY;
        }
    }

    private final class GasInlet implements IGasHandler {

        private final long key;

        GasInlet(long key) {
            this.key = key;
        }

        @Override
        public int getGasTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int tank) {
            return GasStack.EMPTY;
        }

        @Override
        public long getGasTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isGasValid(int tank, @NotNull GasStack stack) {
            return true;
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            if (stack.isEmpty()) {
                return stack;
            }
            long placed = offer(key, ChannelKind.GAS, stack, action.simulate());
            return placed <= 0 ? stack : stack.copyWithAmount(stack.getAmount() - placed);
        }

        @NotNull
        @Override
        public GasStack extractGas(long amount, @NotNull Action action) {
            return GasStack.EMPTY;
        }

        @NotNull
        @Override
        public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
            return GasStack.EMPTY;
        }
    }

    private final class EnergyInlet implements IEnergyStorage {

        private final long key;

        EnergyInlet(long key) {
            this.key = key;
        }

        @Override
        public int receiveEnergy(int amount, boolean simulate) {
            LogisticNetwork network = network();
            return network == null ? 0 : (int) network.offerEnergy(key, amount, simulate);
        }

        @Override
        public int extractEnergy(int amount, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            return 0;
        }

        @Override
        public int getMaxEnergyStored() {
            return Integer.MAX_VALUE;
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return true;
        }
    }

    private final class DataInlet implements IDataHandler {

        private final long key;

        DataInlet(long key) {
            this.key = key;
        }

        @Override
        public int getDataTanks() {
            return 1;
        }

        @NotNull
        @Override
        public DataStack getDataInTank(int tank) {
            return DataStack.EMPTY;
        }

        @Override
        public long getDataTankCapacity(int tank) {
            return 1000;
        }

        @Override
        public boolean isDataValid(int tank, @NotNull DataStack stack) {
            return true;
        }

        @NotNull
        @Override
        public DataStack insertData(@NotNull DataStack stack, @NotNull Action action) {
            if (stack.isEmpty()) {
                return stack;
            }
            long placed = offer(key, ChannelKind.DATA, stack, action.simulate());
            return placed <= 0 ? stack : stack.copyWithAmount(stack.getAmount() - placed);
        }

        @NotNull
        @Override
        public DataStack extractData(long amount, @NotNull Action action) {
            return DataStack.EMPTY;
        }

        @NotNull
        @Override
        public DataStack extractData(@NotNull DataStack stack, @NotNull Action action) {
            return DataStack.EMPTY;
        }
    }
}
