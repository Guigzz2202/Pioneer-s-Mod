package fr.pioneers.space;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.api.gas.IGasTank;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.common.PortMode;
import fr.pioneers.common.port.PortConfigurable;
import fr.pioneers.compat.mekanism.MekanismCompat;
import fr.pioneers.compat.mekanism.MekanismGasBridge;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Configurable;
import fr.pioneers.core.Directions;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.registries.PiBlocks;
import fr.pioneers.registries.PiTileEntityTypes;
import fr.pioneers.tile.TileEntityLaunchPad;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Port du complexe de lancement : relaie objets, fluides, gaz et donnees vers le cœur du complexe auquel il appartient.
 */
public class LaunchPortTile extends PiBlockEntity implements Configurable.Holder {

    private static final int LINK_INTERVAL = 20, EJECT_INTERVAL = 10;

    @Nullable
    private BlockPos core;
    private long linkedAt = -1000;
    private LazyOptional<IItemHandler> items = LazyOptional.of(ItemHandler::new);
    private LazyOptional<IFluidHandler> fluids = LazyOptional.of(FluidHandler::new);
    private LazyOptional<IGasHandler> gases = LazyOptional.of(GasHandler::new);
    /** Vue Mekanism du quai a gaz (tuyaux de Mekanism), selon le sens du port. */
    private LazyOptional<Object> mekGases = LazyOptional.of(this::mekanismView);
    private LazyOptional<IDataHandler> data = LazyOptional.of(DataHandler::new);
    private final PortConfigurable configurable = (new PortConfigurable(
          () -> Component.translatable(getMode().isOutput() ? "port.pioneers.direction.output" : "port.pioneers.direction.input")
                .withStyle(getMode().isOutput() ? ChatFormatting.GOLD : ChatFormatting.AQUA), this::cycleMode));

    public LaunchPortTile(BlockPos pos, BlockState state) {
        super(PiTileEntityTypes.LAUNCH_PORT.get(), pos, state);
    }

    public PortMode getMode() {
        return getBlockState().getValue(LaunchPortBlock.MODE);
    }

    /**
     * 0.20.28 : le port ne connait que son sens (demande de Guillaume : « dire seulement au port s'il est entree ou sortie ») : en entree il
     * prend objets, fluide, gaz et donnees ; en sortie il ejecte tout ce que le quai a recu (anciens modes par sorte : lus comme leur sens).
     */
    private void cycleMode() {
        if (level != null) {
            PortMode next = getMode().isOutput() ? PortMode.ITEM_INPUT : PortMode.ITEM_OUTPUT;
            level.setBlock(worldPosition, getBlockState().setValue(LaunchPortBlock.MODE, next), Block.UPDATE_ALL);
            //Les tuyaux et cables voisins doivent redemander la capacite du nouveau type
            resetCapabilities();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private void resetCapabilities() {
        items.invalidate();
        fluids.invalidate();
        gases.invalidate();
        mekGases.invalidate();
        data.invalidate();
        mekGases = LazyOptional.of(this::mekanismView);
        items = LazyOptional.of(ItemHandler::new);
        fluids = LazyOptional.of(FluidHandler::new);
        gases = LazyOptional.of(GasHandler::new);
        data = LazyOptional.of(DataHandler::new);
    }

    @Nullable
    private TileEntityLaunchPad getCore() {
        if (level == null) {
            return null;
        }
        //Lot 8a : hote trouve par le registre commun des hotes (cases du complexe forme, MultiblockPort)
        TileEntityLaunchPad host = fr.pioneers.api.multiblock.MultiblockPort.house(level, worldPosition, TileEntityLaunchPad.class);
        if (host != null && host.isFormed()) {
            return host;
        }
        long now = level.getGameTime();
        if (core == null || now - linkedAt > LINK_INTERVAL) {
            core = LaunchPadStructure.findCore(level, worldPosition);
            linkedAt = now;
        }
        return core != null && level.getBlockEntity(core) instanceof TileEntityLaunchPad pad && pad.isFormed() ? pad : null;
    }

    /** Sortie : ejection du quai dans les blocs voisins (hors complexe). */
    public void tickServer() {
        PortMode mode = getMode();
        if (level == null || !mode.isOutput() || (level.getGameTime() + worldPosition.asLong()) % EJECT_INTERVAL != 0) {
            return;
        }
        TileEntityLaunchPad pad = getCore();
        if (pad == null) {
            return;
        }
        for (Direction direction : Directions.ALL) {
            if (direction == Direction.UP) {
                continue;
            }
            BlockPos target = worldPosition.relative(direction);
            BlockState state = level.getBlockState(target);
            if (isComplexBlock(state)) {
                continue;
            }
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            BlockEntity tile = ClanApi.canTransfer(level, worldPosition, target) ? level.getBlockEntity(target) : null;
            if (tile == null) {
                continue;
            }
            Direction side = direction.getOpposite();
            //Seul le contenu de ce port sort
            tile.getCapability(ForgeCapabilities.ITEM_HANDLER, side).ifPresent(handler -> {
                for (ItemSlot slot : pad.getDockSlots()) {
                    if (!slot.isEmpty()) {
                        ItemStack stack = slot.extract(64, Action.SIMULATE, Automation.EXTERNAL);
                        ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, stack, false);
                        int moved = stack.getCount() - rest.getCount();
                        if (moved > 0) {
                            slot.extract(moved, Action.EXECUTE, Automation.EXTERNAL);
                        }
                    }
                }
            });
            tile.getCapability(ForgeCapabilities.FLUID_HANDLER, side).ifPresent(handler -> {
                FluidStack offer = pad.isReceiver() ? pad.fluidCargo.drain(4_000, IFluidHandler.FluidAction.SIMULATE) : FluidStack.EMPTY;
                if (!offer.isEmpty()) {
                    int filled = handler.fill(offer, IFluidHandler.FluidAction.EXECUTE);
                    if (filled > 0) {
                        pad.fluidCargo.drain(filled, IFluidHandler.FluidAction.EXECUTE);
                    }
                }
            });
            IGasHandler gases = GasAccess.find(tile, side);
            if (gases != null) {
                GasStack offer = pad.gasCargo.extract(4_000, Action.SIMULATE, Automation.EXTERNAL);
                if (!offer.isEmpty()) {
                    GasStack rest = gases.insertGas(offer, Action.EXECUTE);
                    long moved = offer.getAmount() - rest.getAmount();
                    if (moved > 0) {
                        pad.gasCargo.extract(moved, Action.EXECUTE, Automation.EXTERNAL);
                    }
                }
            }
            tile.getCapability(PioneersAPI.DATA_HANDLER, side).ifPresent(handler -> {
                DataStack offer = pad.getDataCargo().extract(10_000, Action.SIMULATE, Automation.EXTERNAL);
                if (!offer.isEmpty()) {
                    DataStack rest = handler.insertData(offer, Action.EXECUTE);
                    long moved = offer.getAmount() - rest.getAmount();
                    if (moved > 0) {
                        pad.getDataCargo().extract(moved, Action.EXECUTE, Automation.EXTERNAL);
                    }
                }
            });
        }
    }

    private static boolean isComplexBlock(BlockState state) {
        return state.getBlock() == PiBlocks.LAUNCH_PAD.getBlock() || state.getBlock() == PiBlocks.LAUNCH_PORT.getBlock()
               || LaunchPadStructure.isCore(state) || LaunchTowerBlock.is(state);
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        //0.20.28 : toutes les sortes a la fois (le sens seul est regle)
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return items.cast();
        } else if (cap == ForgeCapabilities.FLUID_HANDLER) {
            return fluids.cast();
        } else if (cap == PioneersAPI.GAS_HANDLER) {
            return gases.cast();
        } else if (MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap)) {
            return mekGases.cast();
        } else if (cap == PioneersAPI.DATA_HANDLER) {
            return data.cast();
        } else
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        items.invalidate();
        fluids.invalidate();
        gases.invalidate();
        mekGases.invalidate();
        data.invalidate();
    }

    private Object mekanismView() {
        List<IGasTank> tanks = new AbstractList<>() {
            @Override
            public IGasTank get(int index) {
                TileEntityLaunchPad pad = getCore();
                if (pad == null) {
                    throw new IndexOutOfBoundsException(index);
                }
                return pad.gasCargo;
            }

            @Override
            public int size() {
                return getCore() == null ? 0 : 1;
            }
        };
        return MekanismGasBridge.expose(tanks, input() ? SideMode.INPUT : SideMode.OUTPUT);
    }

    private boolean input() {
        return !getMode().isOutput();
    }

    // ------------------------------------------------------------------
    // Objets : la case de la fusee repliee puis les 18 cases du quai
    // ------------------------------------------------------------------

    private class ItemHandler implements IItemHandler {

        @Nullable
        private ItemSlot slot(int index) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null) {
                return null;
            }
            if (index == 0) {
                return pad.getRocketSlot();
            }
            List<ItemSlot> dock = pad.getDockSlots();
            return index - 1 < dock.size() ? dock.get(index - 1) : null;
        }

        @Override
        public int getSlots() {
            return 1 + TileEntityLaunchPad.CARGO_SLOTS;
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int index) {
            ItemSlot slot = slot(index);
            return slot == null ? ItemStack.EMPTY : slot.getStack();
        }

        @NotNull
        @Override
        public ItemStack insertItem(int index, @NotNull ItemStack stack, boolean simulate) {
            ItemSlot slot = slot(index);
            if (slot == null || !input()) {
                return stack;
            }
            return slot.insert(stack, simulate ? Action.SIMULATE : Action.EXECUTE, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public ItemStack extractItem(int index, int amount, boolean simulate) {
            ItemSlot slot = slot(index);
            if (slot == null || input() || index == 0) {
                return ItemStack.EMPTY;
            }
            return slot.extract(amount, simulate ? Action.SIMULATE : Action.EXECUTE, Automation.EXTERNAL);
        }

        @Override
        public int getSlotLimit(int index) {
            return 64;
        }

        @Override
        public boolean isItemValid(int index, @NotNull ItemStack stack) {
            ItemSlot slot = slot(index);
            return slot != null && input() && slot.isItemValid(stack);
        }
    }

    // ------------------------------------------------------------------
    // Fluides : hydrogene vers le reservoir de carburant, le reste vers le quai
    // ------------------------------------------------------------------

    private class FluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 2;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null) {
                return FluidStack.EMPTY;
            }
            return tank == 0 ? pad.fuelTank.getFluid() : pad.fluidCargo.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null) {
                return 0;
            }
            return tank == 0 ? pad.fuelTank.getCapacity() : pad.fluidCargo.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return input() && (tank == 0) == TileEntityLaunchPad.isFuel(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || !input() || resource.isEmpty()) {
                return 0;
            }
            //Le carburant remplit d'abord le reservoir de la fusee
            if (TileEntityLaunchPad.isFuel(resource)) {
                int filled = pad.fuelTank.fill(resource, action);
                if (filled > 0 || !pad.isLoadingMission()) {
                    return filled;
                }
            }
            return pad.isLoadingMission() ? pad.fluidCargo.fill(resource, action) : 0;
        }

        @NotNull
        @Override
        public FluidStack drain(FluidStack resource, FluidAction action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || input() || !pad.isReceiver() || resource.isEmpty() || !resource.isFluidEqual(pad.fluidCargo.getFluid())) {
                return FluidStack.EMPTY;
            }
            return pad.fluidCargo.drain(resource.getAmount(), action);
        }

        @NotNull
        @Override
        public FluidStack drain(int maxDrain, FluidAction action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || input() || !pad.isReceiver()) {
                return FluidStack.EMPTY;
            }
            return pad.fluidCargo.drain(maxDrain, action);
        }
    }

    // ------------------------------------------------------------------
    // Gaz
    // ------------------------------------------------------------------

    private class GasHandler implements IGasHandler {

        @Override
        public int getGasTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int tank) {
            TileEntityLaunchPad pad = getCore();
            return pad == null ? GasStack.EMPTY : pad.gasCargo.getStack();
        }

        @Override
        public long getGasTankCapacity(int tank) {
            TileEntityLaunchPad pad = getCore();
            return pad == null ? 0 : pad.gasCargo.getCapacity();
        }

        @Override
        public boolean isGasValid(int tank, @NotNull GasStack stack) {
            TileEntityLaunchPad pad = getCore();
            return pad != null && input() && pad.gasCargo.isValid(stack);
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || !input()) {
                return stack;
            }
            return pad.gasCargo.insert(stack, action, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public GasStack extractGas(long amount, @NotNull Action action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || input()) {
                return GasStack.EMPTY;
            }
            return pad.gasCargo.extract(amount, action, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || input() || !pad.gasCargo.getStack().isSameGas(stack)) {
                return GasStack.EMPTY;
            }
            return pad.gasCargo.extract(stack.getAmount(), action, Automation.EXTERNAL);
        }
    }

    // ------------------------------------------------------------------
    // Donnees
    // ------------------------------------------------------------------

    private class DataHandler implements IDataHandler {

        @Override
        public int getDataTanks() {
            return 1;
        }

        @NotNull
        @Override
        public DataStack getDataInTank(int tank) {
            TileEntityLaunchPad pad = getCore();
            return pad == null ? DataStack.EMPTY : pad.getDataCargo().getStack();
        }

        @Override
        public long getDataTankCapacity(int tank) {
            TileEntityLaunchPad pad = getCore();
            return pad == null ? 0 : pad.getDataCargo().getCapacity();
        }

        @Override
        public boolean isDataValid(int tank, @NotNull DataStack stack) {
            TileEntityLaunchPad pad = getCore();
            return pad != null && input() && pad.getDataCargo().isValid(stack);
        }

        @NotNull
        @Override
        public DataStack insertData(@NotNull DataStack stack, @NotNull Action action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || !input()) {
                return stack;
            }
            return pad.getDataCargo().insert(stack, action, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public DataStack extractData(long amount, @NotNull Action action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || input()) {
                return DataStack.EMPTY;
            }
            return pad.getDataCargo().extract(amount, action, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public DataStack extractData(@NotNull DataStack stack, @NotNull Action action) {
            TileEntityLaunchPad pad = getCore();
            if (pad == null || input() || !pad.getDataCargo().getStack().isTypeEqual(stack)) {
                return DataStack.EMPTY;
            }
            return pad.getDataCargo().extract(stack.getAmount(), action, Automation.EXTERNAL);
        }
    }

    @Override
    public Configurable getConfigurable(@Nullable Direction side) {
        return configurable;
    }
}
