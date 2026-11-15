package fr.pioneers.space.content.stabilizer;

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
import fr.pioneers.core.energy.EnergyBuffer;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.registries.PiStabilizer;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Piece du Stabilisateur spatial (socle) : connait le noyau de sa structure ({@code core}) et sert d'acces a ses reservoirs.
 * <ul>
 *     <li>port : gaz et fluides vers le noyau ; entree (antimatiere ou lave) ou sortie (produit, ejecte vers les voisins) a la cle ;</li>
 *     <li>port d'alimentation : reserve de 200 M FE pour l'emetteur situe derriere (et l'allumage).</li>
 * </ul>
 */
public class StabilizerPartTile extends PiBlockEntity implements Configurable.Holder {

    public static final long POWER_CAPACITY_FE = 200_000_000L;
    private static final int EJECT_INTERVAL = 10;
    private static final long EJECT_RATE = 4_000;

    @Nullable
    private BlockPos core;
    @Nullable
    private final EnergyBuffer energy;
    private final PortConfigurable configurable;
    private LazyOptional<IGasHandler> gases = LazyOptional.of(GasHandler::new);
    private LazyOptional<IFluidHandler> fluids = LazyOptional.of(FluidHandler::new);
    private LazyOptional<IEnergyStorage> energyCap;
    private LazyOptional<Object> mekGases = LazyOptional.of(this::mekanismView);

    public StabilizerPartTile(BlockPos pos, BlockState state) {
        super(PiStabilizer.PART_TILE.get(), pos, state);
        StabilizerPart part = part();
        energy = part == StabilizerPart.POWER_PORT ? EnergyBuffer.input(POWER_CAPACITY_FE, Integer.MAX_VALUE, this::setChanged) : null;
        energyCap = part == StabilizerPart.POWER_PORT ? LazyOptional.of(() -> energy) : LazyOptional.empty();
        configurable = new PortConfigurable(() -> Component.translatable("stabilizer.pioneers.port." + (isOutput() ? "output" : "input")), this::cycleMode);
    }

    public StabilizerPart part() {
        Block block = getBlockState().getBlock();
        if (block instanceof StabilizerPartBlock partBlock) {
            return partBlock.part;
        }
        return block instanceof StabilizerEmitterBlock ? StabilizerPart.EMITTER : StabilizerPart.CASING;
    }

    @Nullable
    public EnergyBuffer getEnergyBuffer() {
        return energy;
    }

    // ------------------------------------------------------------------
    // Structure
    // ------------------------------------------------------------------

    @Nullable
    public BlockPos getCore() {
        return core;
    }

    void setCore(@Nullable BlockPos core) {
        if (core == null ? this.core != null : !core.equals(this.core)) {
            this.core = core;
            setChanged();
        }
    }

    @Nullable
    public StabilizerCoreTile coreTile() {
        //Lot 8a : l'hote se trouve d'abord par le registre commun des hotes (cases de la structure formee, MultiblockPort)
        StabilizerCoreTile host = MultiblockPort.house(level, worldPosition, StabilizerCoreTile.class);
        if (host != null && host.isFormed()) {
            return host;
        }
        if (core == null || level == null || !level.isLoaded(core)) {
            return null;
        }
        return level.getBlockEntity(core) instanceof StabilizerCoreTile tile && tile.isFormed() ? tile : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && core == null) {
            StabilizerStructure.queue(server, worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // Ports
    // ------------------------------------------------------------------

    public boolean isOutput() {
        BlockState state = getBlockState();
        return state.hasProperty(StabilizerPartBlock.ACTIVE) && state.getValue(StabilizerPartBlock.ACTIVE);
    }

    private void cycleMode() {
        if (level != null && part() == StabilizerPart.PORT) {
            level.setBlock(worldPosition, getBlockState().setValue(StabilizerPartBlock.ACTIVE, !isOutput()), Block.UPDATE_ALL);
            resetCapabilities();
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    private void resetCapabilities() {
        gases.invalidate();
        fluids.invalidate();
        mekGases.invalidate();
        gases = LazyOptional.of(GasHandler::new);
        fluids = LazyOptional.of(FluidHandler::new);
        mekGases = LazyOptional.of(this::mekanismView);
    }

    @Nullable
    @Override
    public Configurable getConfigurable(@Nullable Direction side) {
        return part() == StabilizerPart.PORT ? configurable : null;
    }

    /** Port en sortie : pousse le produit vers les voisins qui ne font pas partie de la structure. */
    void serverTick() {
        if (level == null || part() != StabilizerPart.PORT || !isOutput() || (level.getGameTime() + worldPosition.asLong()) % EJECT_INTERVAL != 0) {
            return;
        }
        StabilizerCoreTile tile = coreTile();
        if (tile == null || tile.outputTank.getStack().isEmpty()) {
            return;
        }
        for (Direction direction : Directions.ALL) {
            BlockPos next = worldPosition.relative(direction);
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            if (!level.isLoaded(next) || !ClanApi.canTransfer(level, worldPosition, next) || level.getBlockEntity(next) instanceof StabilizerPartTile) {
                continue;
            }
            IGasHandler target = GasAccess.find(level.getBlockEntity(next), direction.getOpposite());
            if (target == null) {
                continue;
            }
            GasStack offer = tile.outputTank.extract(EJECT_RATE, Action.SIMULATE, Automation.INTERNAL);
            if (offer.isEmpty()) {
                return;
            }
            GasStack rest = target.insertGas(offer, Action.EXECUTE);
            long moved = offer.getAmount() - rest.getAmount();
            if (moved > 0) {
                tile.outputTank.extract(moved, Action.EXECUTE, Automation.INTERNAL);
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        StabilizerPart part = part();
        if (part == StabilizerPart.PORT) {
            if (cap == PioneersAPI.GAS_HANDLER) {
                return gases.cast();
            } else if (cap == ForgeCapabilities.FLUID_HANDLER) {
                return fluids.cast();
            } else if (MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap)) {
                return mekGases.cast();
            }
        } else if (cap == ForgeCapabilities.ENERGY && part == StabilizerPart.POWER_PORT) {
            return energyCap.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        gases.invalidate();
        fluids.invalidate();
        mekGases.invalidate();
        energyCap.invalidate();
    }

    private Object mekanismView() {
        List<IGasTank> tanks = new AbstractList<>() {
            @Override
            public IGasTank get(int index) {
                StabilizerCoreTile tile = coreTile();
                if (tile == null) {
                    throw new IndexOutOfBoundsException(index);
                }
                return isOutput() ? tile.outputTank : tile.antimatterTank;
            }

            @Override
            public int size() {
                return coreTile() == null ? 0 : 1;
            }
        };
        return MekanismGasBridge.expose(tanks, isOutput() ? SideMode.OUTPUT : SideMode.INPUT);
    }

    /** Gaz : entree = antimatiere (regime Singularite), sortie = produit. */
    private class GasHandler implements IGasHandler {

        @Nullable
        private IGasTank tank() {
            StabilizerCoreTile tile = coreTile();
            return tile == null ? null : isOutput() ? tile.outputTank : tile.antimatterTank;
        }

        @Override
        public int getGasTanks() {
            return 1;
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int index) {
            IGasTank tank = tank();
            return tank == null ? GasStack.EMPTY : tank.getStack();
        }

        @Override
        public long getGasTankCapacity(int index) {
            IGasTank tank = tank();
            return tank == null ? 0 : tank.getCapacity();
        }

        @Override
        public boolean isGasValid(int index, @NotNull GasStack stack) {
            IGasTank tank = tank();
            return tank != null && !isOutput() && tank.isValid(stack);
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            IGasTank tank = tank();
            return tank == null || isOutput() ? stack : tank.insert(stack, action, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public GasStack extractGas(long amount, @NotNull Action action) {
            IGasTank tank = tank();
            return tank == null || !isOutput() ? GasStack.EMPTY : tank.extract(amount, action, Automation.EXTERNAL);
        }

        @NotNull
        @Override
        public GasStack extractGas(@NotNull GasStack stack, @NotNull Action action) {
            IGasTank tank = tank();
            return tank == null || !isOutput() || !tank.getStack().isSameGas(stack) ? GasStack.EMPTY : tank.extract(stack.getAmount(), action,
                  Automation.EXTERNAL);
        }
    }

    /** Fluides : la lave du regime Etoile, en entree seulement. */
    private class FluidHandler implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            StabilizerCoreTile tile = coreTile();
            return tile == null ? FluidStack.EMPTY : tile.lavaTank.getFluid();
        }

        @Override
        public int getTankCapacity(int tank) {
            StabilizerCoreTile tile = coreTile();
            return tile == null ? 0 : tile.lavaTank.getCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            StabilizerCoreTile tile = coreTile();
            return tile != null && !isOutput() && tile.lavaTank.isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            StabilizerCoreTile tile = coreTile();
            return tile == null || isOutput() ? 0 : tile.lavaTank.fill(resource, action);
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

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (core != null) {
            tag.put("core", NbtUtils.writeBlockPos(core));
        }
        if (energy != null) {
            tag.put("energy", energy.serializeNBT());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        core = tag.contains("core") ? NbtUtils.readBlockPos(tag.getCompound("core")) : null;
        if (energy != null && tag.contains("energy")) {
            energy.deserializeNBT(tag.getCompound("energy"));
        }
    }
}
