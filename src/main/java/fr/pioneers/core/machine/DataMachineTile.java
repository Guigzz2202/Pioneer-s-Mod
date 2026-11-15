package fr.pioneers.core.machine;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataSideMode;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.data.IDataConfigurable;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.data.IDataTank;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.RelativeSide;
import fr.pioneers.core.side.SideMode;
import fr.pioneers.core.side.SideType;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.ForgeRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Machine du socle qui manipule des donnees (remplace {@code TileEntityDataMachine} de Mekanism) : reservoirs de donnees, canal de faces
 * {@link SideType#DATA}, capacite {@link PioneersAPI#DATA_HANDLER} face par face, ejection par les faces en SORTIE (cable optique ou
 * machine accolee).
 * <p>
 * Les sous-classes declarent leurs reservoirs avec {@link #dataTank} dans leur constructeur, puis reglent les faces par defaut avec
 * {@link #dataSide}.
 */
public abstract class DataMachineTile extends MachineTile implements IDataConfigurable {

    private final List<IDataTank> dataTanks = new ArrayList<>();
    private final Map<Direction, LazyOptional<IDataHandler>> dataCaps = new EnumMap<>(Direction.class);
    @Nullable
    private LazyOptional<IDataHandler> internalData;
    private long ejectRate;
    private boolean dataInput = true, dataOutput = true;

    protected DataMachineTile(BlockEntityType<?> type, BlockPos pos, BlockState state, long energyCapacity, int maxReceive) {
        super(type, pos, state, energyCapacity, maxReceive);
        sides.addType(SideType.DATA);
    }

    // ------------------------------------------------------------------
    // Declaration
    // ------------------------------------------------------------------

    protected <T extends IDataTank> T dataTank(T tank) {
        dataTanks.add(tank);
        return tank;
    }

    /** Ejection automatique par les faces en sortie (Mo par tick). */
    protected void dataEject(long ratePerTick) {
        ejectRate = ratePerTick;
        if (ratePerTick > 0) {
            sides.ejects(SideType.DATA);
            sides.setEjectChannel(sides.types().indexOf(SideType.DATA), true);
        }
    }

    /** Sens possibles pour les donnees (une machine qui ne fait qu'en produire n'en recoit pas). */
    protected void dataDirections(boolean input, boolean output) {
        dataInput = input;
        dataOutput = output;
    }

    /** Reglage par defaut de faces pour les donnees. */
    protected void dataSide(SideMode mode, RelativeSide... relativeSides) {
        for (RelativeSide side : relativeSides) {
            sides.set(SideType.DATA, side, mode);
        }
    }

    public List<IDataTank> getDataTanks() {
        return dataTanks;
    }

    @Override
    protected boolean canEject() {
        return true;
    }

    // ------------------------------------------------------------------
    // Fonctionnement
    // ------------------------------------------------------------------

    @Override
    public void tickServer() {
        super.tickServer();
        if (level != null && ejectRate > 0 && sides.isEject(SideType.DATA)) {
            for (Direction side : Directions.ALL) {
                if (sides.mode(SideType.DATA, side) == SideMode.OUTPUT) {
                    eject(side);
                }
            }
        }
    }

    private void eject(Direction side) {
        BlockPos next = worldPosition.relative(side);
        //1.9 (MAJ RP, bloc 4) : les donnees ne passent pas une frontiere de claim refusee
        if (!level.isLoaded(next) || !ClanApi.canTransfer(level, worldPosition, next)) {
            return;
        }
        long budget = ejectRate;
        //1.11 : un cable logistique expose la capacite de donnees sur sa fiche (le reseau distribue tout de suite)
        BlockEntity neighbor = level.getBlockEntity(next);
        IDataHandler handler = neighbor == null ? null : neighbor.getCapability(PioneersAPI.DATA_HANDLER, side.getOpposite()).resolve().orElse(null);
        if (handler == null) {
            return;
        }
        for (IDataTank tank : dataTanks) {
            if (budget <= 0) {
                break;
            }
            DataStack available = tank.extract(budget, Action.SIMULATE, Automation.INTERNAL);
            if (!available.isEmpty()) {
                long sent = available.getAmount() - handler.insertData(available, Action.EXECUTE).getAmount();
                if (sent > 0) {
                    tank.extract(sent, Action.EXECUTE, Automation.INTERNAL);
                    budget -= sent;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Faces de donnees (configurateur, cables, apercu)
    // ------------------------------------------------------------------

    public static DataSideMode toData(SideMode mode) {
        return switch (mode) {
            case NONE -> DataSideMode.NONE;
            case INPUT -> DataSideMode.INPUT;
            case OUTPUT -> DataSideMode.OUTPUT;
            case IO -> DataSideMode.INPUT_OUTPUT;
        };
    }

    public static SideMode fromData(DataSideMode mode) {
        return switch (mode) {
            case NONE -> SideMode.NONE;
            case INPUT -> SideMode.INPUT;
            case OUTPUT -> SideMode.OUTPUT;
            case INPUT_OUTPUT -> SideMode.IO;
        };
    }

    @Override
    public DataSideMode getDataMode(Direction side) {
        return toData(sides.mode(SideType.DATA, side));
    }

    @Override
    public boolean setDataMode(Direction side, DataSideMode mode) {
        RelativeSide relative = RelativeSide.fromDirections(facing(), side);
        if (sides.isLocked(relative) || !getSupportedDataModes().contains(mode) || getDataMode(side) == mode) {
            return false;
        }
        sides.set(SideType.DATA, relative, fromData(mode));
        return getDataMode(side) == mode;
    }

    @Override
    public List<DataSideMode> getSupportedDataModes() {
        List<DataSideMode> modes = new ArrayList<>();
        modes.add(DataSideMode.NONE);
        if (dataInput) {
            modes.add(DataSideMode.INPUT);
        }
        if (dataOutput) {
            modes.add(DataSideMode.OUTPUT);
        }
        if (dataInput && dataOutput) {
            modes.add(DataSideMode.INPUT_OUTPUT);
        }
        return modes;
    }

    @Override
    public boolean isDataSideConfigurable(Direction side) {
        return !sides.isLocked(RelativeSide.fromDirections(facing(), side));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == PioneersAPI.DATA_HANDLER && !dataTanks.isEmpty()) {
            if (side == null) {
                if (internalData == null) {
                    IDataHandler handler = new Handler(null);
                    internalData = LazyOptional.of(() -> handler);
                }
                return internalData.cast();
            }
            if (sides.mode(SideType.DATA, side) == SideMode.NONE) {
                return LazyOptional.empty();
            }
            return dataCaps.computeIfAbsent(side, d -> {
                IDataHandler handler = new Handler(d);
                return LazyOptional.of(() -> handler);
            }).cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    protected void sidesChanged() {
        super.sidesChanged();
        dataCaps.values().forEach(LazyOptional::invalidate);
        dataCaps.clear();
        markSync();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        dataCaps.values().forEach(LazyOptional::invalidate);
        dataCaps.clear();
        if (internalData != null) {
            internalData.invalidate();
            internalData = null;
        }
    }

    /** Reservoirs vus par une face (null : acces interne) ; le reglage est relu a chaque appel. */
    private final class Handler implements IDataHandler {

        @Nullable
        private final Direction side;

        Handler(@Nullable Direction side) {
            this.side = side;
        }

        private Automation automation() {
            return side == null ? Automation.INTERNAL : Automation.EXTERNAL;
        }

        private SideMode mode() {
            return side == null ? SideMode.IO : sides.mode(SideType.DATA, side);
        }

        @Override
        public int getDataTanks() {
            return dataTanks.size();
        }

        @NotNull
        @Override
        public DataStack getDataInTank(int index) {
            return index >= 0 && index < dataTanks.size() ? dataTanks.get(index).getStack() : DataStack.EMPTY;
        }

        @Override
        public long getDataTankCapacity(int index) {
            return index >= 0 && index < dataTanks.size() ? dataTanks.get(index).getCapacity() : 0;
        }

        @Override
        public boolean isDataValid(int index, @NotNull DataStack stack) {
            return index >= 0 && index < dataTanks.size() && dataTanks.get(index).isValid(stack);
        }

        @NotNull
        @Override
        public DataStack insertData(@NotNull DataStack stack, @NotNull Action action) {
            if (stack.isEmpty() || !mode().canInsert()) {
                return stack;
            }
            DataStack rest = stack;
            for (IDataTank tank : dataTanks) {
                rest = tank.insert(rest, action, automation());
                if (rest.isEmpty()) {
                    break;
                }
            }
            return rest;
        }

        @NotNull
        @Override
        public DataStack extractData(long amount, @NotNull Action action) {
            if (amount <= 0 || !mode().canExtract()) {
                return DataStack.EMPTY;
            }
            for (IDataTank tank : dataTanks) {
                DataStack out = tank.extract(amount, action, automation());
                if (!out.isEmpty()) {
                    return out;
                }
            }
            return DataStack.EMPTY;
        }

        @NotNull
        @Override
        public DataStack extractData(@NotNull DataStack stack, @NotNull Action action) {
            if (stack.isEmpty() || !mode().canExtract()) {
                return DataStack.EMPTY;
            }
            for (IDataTank tank : dataTanks) {
                if (tank.getStack().isTypeEqual(stack)) {
                    return tank.extract(stack.getAmount(), action, automation());
                }
            }
            return DataStack.EMPTY;
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde, reprise, synchronisation
    // ------------------------------------------------------------------

    private void writeData(CompoundTag tag) {
        ListTag list = new ListTag();
        dataTanks.forEach(tank -> list.add(tank.serializeNBT()));
        tag.put("dataTanks", list);
    }

    private void readData(CompoundTag tag) {
        if (tag.contains("dataTanks", Tag.TAG_LIST)) {
            ListTag list = tag.getList("dataTanks", Tag.TAG_COMPOUND);
            for (int i = 0; i < dataTanks.size(); i++) {
                dataTanks.get(i).deserializeNBT(i < list.size() ? list.getCompound(i) : new CompoundTag());
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        writeData(tag);
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        if (tag.getBoolean("pioneers")) {
            readData(tag);
        }
    }

    @Override
    protected void writeClient(CompoundTag tag) {
        super.writeClient(tag);
        writeData(tag);
        tag.put("dataConfig", sides.save());
    }

    @Override
    protected void readClient(CompoundTag tag) {
        super.readClient(tag);
        readData(tag);
        if (tag.contains("dataConfig", Tag.TAG_COMPOUND)) {
            sides.load(tag.getCompound("dataConfig"));
        }
    }

    /** Identifiant numerique d'un type de donnees (-1 : vide) pour les valeurs d'interface. */
    @SuppressWarnings("unchecked")
    public static long typeId(@Nullable DataType type) {
        return type == null ? -1 : ((ForgeRegistry<DataType>) PioneersAPI.dataTypeRegistry()).getID(type);
    }

    @Nullable
    @SuppressWarnings("unchecked")
    public static DataType typeById(long id) {
        return id < 0 ? null : ((ForgeRegistry<DataType>) PioneersAPI.dataTypeRegistry()).getValue((int) id);
    }
}
