package fr.pioneers.space.content.absorber;

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
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.registries.PiCosmicBlocks;
import java.util.AbstractList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.IEnergyStorage;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Piece de l'absorbeur d'energie solaire (socle) : connait le controleur de son caisson. Le port donne acces au controleur :
 * <ul>
 *     <li>energie : toujours en sortie (extraction, et poussee vers les voisins qui ne font pas partie du caisson) ;</li>
 *     <li>caloporteur : froid en entree, ou chaud en sortie (cle) ; en sortie, il est pousse vers les voisins.</li>
 * </ul>
 */
public class AbsorberPartTile extends PiBlockEntity implements Configurable.Holder {

    private static final int EJECT_INTERVAL = 5;
    private static final long GAS_RATE = 16_000;

    @Nullable
    private BlockPos controller;
    private final PortConfigurable configurable;
    private LazyOptional<IGasHandler> gases = LazyOptional.of(GasHandler::new);
    private LazyOptional<IEnergyStorage> energy = LazyOptional.of(EnergyHandler::new);
    private LazyOptional<Object> mekGases = LazyOptional.of(this::mekanismView);

    public AbsorberPartTile(BlockPos pos, BlockState state) {
        super(PiCosmicBlocks.ABSORBER_PART_TILE.get(), pos, state);
        configurable = new PortConfigurable(() -> Component.translatable("solar_absorber.pioneers.port." + (isOutput() ? "output" : "input")), this::cycleMode);
    }

    public AbsorberPart part() {
        return getBlockState().getBlock() instanceof AbsorberPartBlock block ? block.part : AbsorberPart.CASING;
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
    public AbsorberControllerTile controllerTile() {
        //Lot 8a : l'hote se trouve d'abord par le registre commun des hotes (cases de la structure formee, MultiblockPort)
        AbsorberControllerTile host = MultiblockPort.house(level, worldPosition, AbsorberControllerTile.class);
        if (host != null && host.isFormed()) {
            return host;
        }
        if (controller == null || level == null || !level.isLoaded(controller)) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof AbsorberControllerTile tile && tile.isFormed() ? tile : null;
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel server && controller == null) {
            AbsorberStructure.queue(server, worldPosition);
        }
    }

    // ------------------------------------------------------------------
    // Port
    // ------------------------------------------------------------------

    /** Port en sortie du caloporteur chaud (sinon entree du caloporteur froid). */
    public boolean isOutput() {
        return part() == AbsorberPart.PORT && getBlockState().getValue(AbsorberPartBlock.ACTIVE);
    }

    private void cycleMode() {
        if (level != null && part() == AbsorberPart.PORT) {
            level.setBlock(worldPosition, getBlockState().setValue(AbsorberPartBlock.ACTIVE, !isOutput()), Block.UPDATE_ALL);
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
        mekGases = LazyOptional.of(this::mekanismView);
    }

    @Nullable
    @Override
    public Configurable getConfigurable(@Nullable Direction side) {
        return part() == AbsorberPart.PORT ? configurable : null;
    }

    @Nullable
    private IGasTank tank() {
        AbsorberControllerTile tile = controllerTile();
        return tile == null ? null : isOutput() ? tile.heatedTank : tile.coolantTank;
    }

    /** Pousse l'energie (et le caloporteur chaud en sortie) vers les voisins qui ne font pas partie du caisson. */
    void serverTick() {
        if (level == null || (level.getGameTime() + worldPosition.asLong()) % EJECT_INTERVAL != 0) {
            return;
        }
        AbsorberControllerTile tile = controllerTile();
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
            if (neighbor == null || neighbor instanceof AbsorberPartTile || neighbor instanceof AbsorberControllerTile) {
                continue;
            }
            IEnergyStorage target = neighbor.getCapability(ForgeCapabilities.ENERGY, direction.getOpposite()).orElse(null);
            if (target != null && target.canReceive() && tile.getEnergy().getStored() > 0) {
                long budget = Math.min(Integer.MAX_VALUE, tile.getEnergy().getStored());
                int given = target.receiveEnergy((int) budget, false);
                tile.getEnergy().extract(given, false);
            }
            if (isOutput() && !tile.heatedTank.getStack().isEmpty()) {
                IGasHandler gas = GasAccess.find(neighbor, direction.getOpposite());
                if (gas != null) {
                    GasStack offer = tile.heatedTank.extract(GAS_RATE * EJECT_INTERVAL, Action.SIMULATE, Automation.INTERNAL);
                    GasStack rest = gas.insertGas(offer, Action.EXECUTE);
                    long moved = offer.getAmount() - rest.getAmount();
                    if (moved > 0) {
                        tile.heatedTank.extract(moved, Action.EXECUTE, Automation.INTERNAL);
                    }
                }
            }
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (part() == AbsorberPart.PORT) {
            if (cap == ForgeCapabilities.ENERGY) {
                return energy.cast();
            } else if (cap == PioneersAPI.GAS_HANDLER) {
                return gases.cast();
            } else if (MekanismCompat.LOADED && MekanismGasBridge.isMekanismGasCapability(cap)) {
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
        mekGases.invalidate();
    }

    private Object mekanismView() {
        List<IGasTank> tanks = new AbstractList<>() {
            @Override
            public IGasTank get(int index) {
                IGasTank tank = tank();
                if (tank == null) {
                    throw new IndexOutOfBoundsException(index);
                }
                return tank;
            }

            @Override
            public int size() {
                return tank() == null ? 0 : 1;
            }
        };
        return MekanismGasBridge.expose(tanks, isOutput() ? SideMode.OUTPUT : SideMode.INPUT);
    }

    /** Caloporteur : froid en entree, chaud en sortie. */
    private class GasHandler implements IGasHandler {

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

    /** Energie : la reserve du controleur, en sortie seulement. */
    private class EnergyHandler implements IEnergyStorage {

        @Override
        public int receiveEnergy(int maxReceive, boolean simulate) {
            return 0;
        }

        @Override
        public int extractEnergy(int maxExtract, boolean simulate) {
            AbsorberControllerTile tile = controllerTile();
            return tile == null ? 0 : (int) tile.getEnergy().extract(maxExtract, simulate);
        }

        @Override
        public int getEnergyStored() {
            AbsorberControllerTile tile = controllerTile();
            return tile == null ? 0 : tile.getEnergy().getEnergyStored();
        }

        @Override
        public int getMaxEnergyStored() {
            AbsorberControllerTile tile = controllerTile();
            return tile == null ? 0 : tile.getEnergy().getMaxEnergyStored();
        }

        @Override
        public boolean canExtract() {
            return controllerTile() != null;
        }

        @Override
        public boolean canReceive() {
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
