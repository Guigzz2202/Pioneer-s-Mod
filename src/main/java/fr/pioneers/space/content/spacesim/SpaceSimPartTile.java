package fr.pioneers.space.content.spacesim;

import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.gas.IGasTank;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.common.port.PortConfigurable;
import fr.pioneers.core.compat.mekanism.MekanismCompat;
import fr.pioneers.core.compat.mekanism.MekanismGasBridge;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Configurable;
import fr.pioneers.core.Directions;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.space.simulation.PiSpaceSim;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Piece du Simulateur spatial : connait le controleur de sa structure. Le port donne acces au controleur : energie et plasma stellaire en
 * entree, ou produits en sortie (extraction par tuyau et ejection vers les voisins), reglage a la cle.
 */
public class SpaceSimPartTile extends PiBlockEntity implements Configurable.Holder {

    private static final int EJECT_INTERVAL = 10;

    @Nullable
    private BlockPos controller;
    private final PortConfigurable configurable;
    private LazyOptional<IGasHandler> gases = LazyOptional.of(GasHandler::new);
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(EnergyHandler::new);
    private LazyOptional<IItemHandler> items = LazyOptional.of(ItemHandler::new);
    private LazyOptional<Object> mekGases = LazyOptional.of(this::mekanismView);

    public SpaceSimPartTile(BlockPos pos, BlockState state) {
        super(PiSpaceSim.PART_TILE.get(), pos, state);
        configurable = new PortConfigurable(() -> Component.translatable("space_simulator.pioneers.port." + (isOutput() ? "output" : "input")), this::cycleMode);
    }

    public SpaceSimPart part() {
        return getBlockState().getBlock() instanceof SpaceSimPartBlock block ? block.part : SpaceSimPart.CASING;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Nullable
    public BlockPos getController() {
        return controller;
    }

    void setController(@Nullable BlockPos controller) {
        if (controller == null ? this.controller != null : !controller.equals(this.controller)) {
            this.controller = controller;
            setChanged();
        }
    }

    @Nullable
    public SpaceSimControllerTile controllerTile() {
        //Lot 8a : l'hote se trouve d'abord par le registre commun des hotes (cases de la structure formee, MultiblockPort)
        SpaceSimControllerTile host = MultiblockPort.house(level, worldPosition, SpaceSimControllerTile.class);
        if (host != null && host.isFormed()) {
            return host;
        }
        if (controller == null || level == null || !level.isLoaded(controller)) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof SpaceSimControllerTile tile && tile.isFormed() ? tile : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && controller == null) {
            SpaceSimStructure.queue(server, worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // Port
    // ------------------------------------------------------------------

    public boolean isOutput() {
        BlockState state = getBlockState();
        return part() == SpaceSimPart.PORT && state.getValue(SpaceSimPartBlock.ACTIVE);
    }

    private void cycleMode() {
        if (level != null && part() == SpaceSimPart.PORT) {
            level.setBlock(worldPosition, getBlockState().setValue(SpaceSimPartBlock.ACTIVE, !isOutput()), Block.UPDATE_ALL);
            invalidateCaps();
            reviveCaps();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public void reviveCaps() {
        super.reviveCaps();
        gases = LazyOptional.of(GasHandler::new);
        energy = LazyOptional.of(EnergyHandler::new);
        items = LazyOptional.of(ItemHandler::new);
        mekGases = LazyOptional.of(this::mekanismView);
    }

    @Nullable
    @Override
    public Configurable getConfigurable(@Nullable Direction side) {
        return part() == SpaceSimPart.PORT ? configurable : null;
    }

    /** Port en sortie : pousse les produits vers les inventaires voisins qui ne font pas partie de la structure. */
    void serverTick() {
        if (level == null || !isOutput() || (level.getGameTime() + worldPosition.asLong()) % EJECT_INTERVAL != 0) {
            return;
        }
        SpaceSimControllerTile tile = controllerTile();
        if (tile == null) {
            return;
        }
        for (Direction direction : Directions.ALL) {
            BlockPos next = worldPosition.relative(direction);
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            if (!level.isLoaded(next) || !ClanApi.canTransfer(level, worldPosition, next)) {
                continue;
            }
            BlockEntity neighbor = level.getBlockEntity(next);
            if (neighbor == null || neighbor instanceof SpaceSimPartTile || neighbor instanceof SpaceSimControllerTile) {
                continue;
            }
            IItemHandler target = neighbor.getCapability(ForgeCapabilities.ITEM_HANDLER, direction.getOpposite()).orElse(null);
            if (target != null) {
                tile.ejectInto(target);
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (part() == SpaceSimPart.PORT) {
            if (cap == ForgeCapabilities.ENERGY && !isOutput()) {
                return energy.cast();
            } else if (cap == PioneersAPI.GAS_HANDLER && !isOutput()) {
                return gases.cast();
            } else if (cap == ForgeCapabilities.ITEM_HANDLER && isOutput()) {
                return items.cast();
            } else if (MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap) && !isOutput()) {
                return mekGases.cast();
            }
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        gases.invalidate();
        energy.invalidate();
        items.invalidate();
        mekGases.invalidate();
    }

    private Object mekanismView() {
        List<IGasTank> tanks = new AbstractList<>() {
            @Override
            public IGasTank get(int index) {
                SpaceSimControllerTile tile = controllerTile();
                if (tile == null) {
                    throw new IndexOutOfBoundsException(index);
                }
                return tile.plasmaTank;
            }

            @Override
            public int size() {
                return controllerTile() == null ? 0 : 1;
            }
        };
        return MekanismGasBridge.expose(tanks, SideMode.INPUT);
    }

    /** Plasma stellaire, en entree. */
    private class GasHandler implements IGasHandler {

        @Override
        public int getGasTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int index) {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? GasStack.EMPTY : tile.plasmaTank.getStack();
        }

        @Override
        public long getGasTankCapacity(int index) {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? 0 : tile.plasmaTank.getCapacity();
        }

        @Override
        public boolean isGasValid(int index, @NotNull GasStack stack) {
            SpaceSimControllerTile tile = controllerTile();
            return tile != null && tile.plasmaTank.isValid(stack);
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? stack : tile.plasmaTank.insert(stack, action, Automation.EXTERNAL);
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

    /** Energie : directement dans la reserve du controleur. */
    private class EnergyHandler implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? 0 : tile.getEnergy().receiveEnergy(maxReceive, simulate);
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            return 0;
        }

        @Override
        public int getEnergyStored() {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? 0 : tile.getEnergy().getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? 0 : tile.getEnergy().getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return false;
        }

        @Override
        public boolean canReceive() {
            return controllerTile() != null;
        }
    }

    /** Produits : les cases de sortie du controleur, en extraction seulement. */
    private class ItemHandler implements IItemHandler {

        @Nullable
        private IItemHandler outputs() {
            SpaceSimControllerTile tile = controllerTile();
            return tile == null ? null : tile.outputHandler();
        }

        @Override
        public int getSlots() {
            IItemHandler outputs = outputs();
            return outputs == null ? 0 : outputs.getSlots();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            IItemHandler outputs = outputs();
            return outputs == null ? ItemStack.EMPTY : outputs.getStackInSlot(slot);
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            return stack;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            IItemHandler outputs = outputs();
            return outputs == null ? ItemStack.EMPTY : outputs.extractItem(slot, amount, simulate);
        }

        @Override
        public int getSlotLimit(int slot) {
            return 64;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return false;
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) {
            tag.put("controller", NbtUtils.writeBlockPos(controller));
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("controller") ? NbtUtils.readBlockPos(tag.getCompound("controller")) : null;
    }
}
