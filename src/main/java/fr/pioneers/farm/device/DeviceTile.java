package fr.pioneers.farm.device;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import fr.pioneers.farm.FarmCard;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.NonNullList;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.Containers;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Base des blocs de la ferme animee : cases d'objets, reservoir d'eau facultatif, synchro client (etat complet a chaque changement), capacites
 * (entonnoirs : regles par case et par face ; tuyaux : eau), fiche au regard. 1.0.5-beta (lot 41, retour 60) : les appareils a interface
 * ({@link FarmDeviceTile}) n'ont plus de fiche au regard ; seul l'epouvantail la garde ({@link ScarecrowTile}, decor sans contenu) ; les
 * faces reglees de l'interface bornent les capacites ({@link #sideMode}).
 */
public abstract class DeviceTile extends PiBlockEntity {

    protected final NonNullList<ItemStack> items;
    @Nullable
    protected final FluidTank tank;

    protected DeviceTile(BlockPos pos, BlockState state, int slots, int tankCapacity) {
        super(PiFarmDevices.DEVICE_TILE.get(), pos, state);
        this.items = NonNullList.withSize(slots, ItemStack.EMPTY);
        this.tank = tankCapacity > 0 ? new FluidTank(tankCapacity, f -> f.getFluid().isSame(Fluids.WATER)) {
            @Override
            protected void onContentsChanged() {
                tankChanged();
            }
        } : null;
    }

    public DeviceBlock.Kind kind() {
        return getBlockState().getBlock() instanceof DeviceBlock b ? b.kind() : DeviceBlock.Kind.COMPOST_BIN;
    }

    public Direction facing() {
        BlockState s = getBlockState();
        return s.hasProperty(DeviceBlock.FACING) ? s.getValue(DeviceBlock.FACING) : Direction.NORTH;
    }

    public NonNullList<ItemStack> items() {
        return items;
    }

    @Nullable
    public FluidTank tank() {
        return tank;
    }

    // ------------------------------------------------------------------ vie

    public void serverTick() {
    }

    public void clientTick() {
    }

    /** Particules d'ambiance (client, {@code animateTick}). */
    public void particles(RandomSource random) {
    }

    /** Reservoir change : synchro au plus une fois par seconde (les tuyaux remplissent chaque tick). */
    private long lastTankSync;

    protected void tankChanged() {
        if (level != null && level.getGameTime() - lastTankSync >= 20) {
            lastTankSync = level.getGameTime();
            sync();
        } else {
            setChanged();
        }
    }

    protected void sync() {
        setChanged();
        if (level != null && !level.isClientSide) {
            level.sendBlockUpdated(worldPosition, getBlockState(), getBlockState(), 2);
        }
    }

    // ------------------------------------------------------------------ outils

    protected static void say(Player player, String key, Object... args) {
        player.displayClientMessage(Component.translatable("farm.pioneers.device." + key, args).withStyle(ChatFormatting.GOLD), true);
    }

    protected void sound(SoundEvent sound, float volume, float pitch) {
        if (level != null) {
            level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, volume, pitch);
        }
    }

    protected void itemBurst(ItemStack stack, double y, int n) {
        if (level instanceof ServerLevel server && !stack.isEmpty()) {
            server.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, stack), worldPosition.getX() + 0.5, worldPosition.getY() + y,
                  worldPosition.getZ() + 0.5, n, 0.15, 0.05, 0.15, 0.06);
        }
    }

    protected static void give(Player player, ItemStack stack) {
        //1.0.5-beta (lot 31, retour 42) : graines, pousses, poissons rendus par un appareil : dans un sac adapte d'abord
        fr.pioneers.building.home.bag.BagEvents.give(player, stack);
    }

    /** Range une pile dans les cases [from, to) (empile d'abord) ; rend le reste. */
    protected ItemStack store(ItemStack stack, int from, int to, int limit) {
        ItemStack rest = stack.copy();
        for (int pass = 0; pass < 2 && !rest.isEmpty(); pass++) {
            for (int i = from; i < to && !rest.isEmpty(); i++) {
                ItemStack cur = items.get(i);
                int max = Math.min(limit, rest.getMaxStackSize());
                if (pass == 0 && !cur.isEmpty() && ItemStack.isSameItemSameTags(cur, rest) && cur.getCount() < max) {
                    int n = Math.min(rest.getCount(), max - cur.getCount());
                    cur.grow(n);
                    rest.shrink(n);
                } else if (pass == 1 && cur.isEmpty()) {
                    int n = Math.min(rest.getCount(), max);
                    items.set(i, rest.copyWithCount(n));
                    rest.shrink(n);
                }
            }
        }
        return rest;
    }

    public void dropContents() {
        if (level != null && !level.isClientSide) {
            Containers.dropContents(level, worldPosition, items);
        }
    }

    /** Rendu : les parties animees debordent (tetes d'arroseur, abeilles, mat de la station meteo). */
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1.6, 0.5, 1.6).expandTowards(0, 1.5, 0);
    }

    // ------------------------------------------------------------------ fiche

    @Nullable
    public FarmCard card(boolean sneaking) {
        FarmCard card = FarmCard.of(new ItemStack(getBlockState().getBlock()), getBlockState().getBlock().getName(), accent());
        describe(card, sneaking);
        if (tank != null) {
            float r = tank.getFluidAmount() / (float) tank.getCapacity();
            card.add(FarmCard.Line.gauge(FarmCard.label("water"), r, FarmCard.millibuckets(tank.getFluidAmount()), r < 0.1F ? FarmCard.BAD : 0x3C78DC));
        }
        return card;
    }

    protected int accent() {
        return 0xC8A050;
    }

    protected void describe(FarmCard card, boolean sneaking) {
    }

    // ------------------------------------------------------------------ automatisation

    /** Une pile peut-elle entrer dans la case par la face (null : sans face) ? */
    protected boolean canInsert(int slot, ItemStack stack, @Nullable Direction side) {
        return false;
    }

    /** La case se vide-t-elle par la face ? */
    protected boolean canExtract(int slot, @Nullable Direction side) {
        return false;
    }

    protected int slotLimit(int slot) {
        return 64;
    }

    protected void itemsChanged() {
        sync();
    }

    /** 1.0.5-beta (lot 41) : reglage de la face pour une ressource (interface : {@link FarmDeviceTile}) ; toutes en entree-sortie par defaut. */
    protected SideMode sideMode(SideType type, @Nullable Direction side) {
        return SideMode.IO;
    }

    private final LazyOptional<IItemHandler>[] itemCaps = sidedCaps();

    @SuppressWarnings("unchecked")
    private LazyOptional<IItemHandler>[] sidedCaps() {
        LazyOptional<IItemHandler>[] caps = new LazyOptional[7];
        for (int f = 0; f < 7; f++) {
            Direction side = f < 6 ? Direction.from3DDataValue(f) : null;
            caps[f] = LazyOptional.of(() -> new Handler(side));
        }
        return caps;
    }

    private final class Handler implements IItemHandler {

        @Nullable
        private final Direction side;

        Handler(@Nullable Direction side) {
            this.side = side;
        }

        @Override
        public int getSlots() {
            return items.size();
        }

        @Override
        public @NotNull ItemStack getStackInSlot(int slot) {
            return items.get(slot);
        }

        @Override
        public @NotNull ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || !canInsert(slot, stack, side) || side != null && !sideMode(SideType.ITEMS, side).canInsert()) {
                return stack;
            }
            ItemStack cur = items.get(slot);
            int max = Math.min(slotLimit(slot), stack.getMaxStackSize());
            if (!cur.isEmpty() && !ItemStack.isSameItemSameTags(cur, stack)) {
                return stack;
            }
            int n = Math.min(stack.getCount(), max - cur.getCount());
            if (n <= 0) {
                return stack;
            }
            if (!simulate) {
                if (cur.isEmpty()) {
                    items.set(slot, stack.copyWithCount(n));
                } else {
                    cur.grow(n);
                }
                itemsChanged();
            }
            return stack.copyWithCount(stack.getCount() - n);
        }

        @Override
        public @NotNull ItemStack extractItem(int slot, int amount, boolean simulate) {
            ItemStack cur = items.get(slot);
            if (cur.isEmpty() || amount <= 0 || !canExtract(slot, side) || side != null && !sideMode(SideType.ITEMS, side).canExtract()) {
                return ItemStack.EMPTY;
            }
            int n = Math.min(amount, cur.getCount());
            ItemStack out = cur.copyWithCount(n);
            if (!simulate) {
                cur.shrink(n);
                itemsChanged();
            }
            return out;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return canInsert(slot, stack, side) && (side == null || sideMode(SideType.ITEMS, side).canInsert());
        }
    }

    private LazyOptional<IFluidHandler> fluidCap;

    @Override
    public @NotNull <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER && !items.isEmpty()) {
            if (side != null && sideMode(SideType.ITEMS, side) == SideMode.NONE) {
                return LazyOptional.empty();
            }
            return itemCaps[side == null ? 6 : side.get3DDataValue()].cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER && tank != null) {
            //1.0.5-beta (lot 41) : face des liquides fermee (interface) : pas de tuyau ; sinon la cuve d'eau (entree, comme avant)
            if (side != null && sideMode(SideType.FLUIDS, side) == SideMode.NONE) {
                return LazyOptional.empty();
            }
            if (fluidCap == null) {
                fluidCap = LazyOptional.of(() -> tank);
            }
            return fluidCap.cast();
        }
        return super.getCapability(cap, side);
    }

    /** 1.0.5-beta (lot 41) : faces changees : les voisins relisent les capacites. */
    protected void invalidateSided() {
        for (int f = 0; f < 7; f++) {
            itemCaps[f].invalidate();
            Direction side = f < 6 ? Direction.from3DDataValue(f) : null;
            itemCaps[f] = LazyOptional.of(() -> new Handler(side));
        }
        if (fluidCap != null) {
            fluidCap.invalidate();
            fluidCap = null;
        }
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        for (LazyOptional<IItemHandler> c : itemCaps) {
            c.invalidate();
        }
        if (fluidCap != null) {
            fluidCap.invalidate();
        }
    }

    // ------------------------------------------------------------------ sauvegarde et synchro

    protected void saveExtra(CompoundTag tag) {
    }

    protected void loadExtra(CompoundTag tag) {
    }

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        if (!items.isEmpty()) {
            ContainerHelper.saveAllItems(tag, items, true);
        }
        if (tank != null) {
            tag.put("tank", tank.writeToNBT(new CompoundTag()));
        }
        saveExtra(tag);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        if (!items.isEmpty()) {
            for (int i = 0; i < items.size(); i++) {
                items.set(i, ItemStack.EMPTY);
            }
            ContainerHelper.loadAllItems(tag, items);
        }
        if (tank != null) {
            tank.setFluid(FluidStack.EMPTY);
            tank.readFromNBT(tag.getCompound("tank"));
        }
        loadExtra(tag);
    }

    @Override
    public CompoundTag getUpdateTag() {
        return saveWithoutMetadata();
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket() {
        return ClientboundBlockEntityDataPacket.create(this);
    }
}
