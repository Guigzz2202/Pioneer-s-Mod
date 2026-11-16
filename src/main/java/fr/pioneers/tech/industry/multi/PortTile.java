package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.multiblock.MultiblockPort;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.tech.compute.ComputeCore;
import fr.pioneers.core.Action;
import fr.pioneers.core.Directions;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.Transfer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc-entite d'un port : il prete les reservoirs et cases de son controleur (retrouve par {@link Structures}) aux voisins. Le travail
 * (poussee des sorties, echanges de vapeur) est fait par le controleur.
 * <p>
 * 0.20.28 : le port ne connait que son sens (entree ou sortie) : rangement intelligent a l'entree, ejection automatique a la sortie
 * ({@link SmartPorts}). Plus de case ni de reservoir vise.
 */
public class PortTile extends PiBlockEntity {

    public final PortBlock.Type type;
    /**
     * Port de donnees : une prise sans reservoir. Le calcul et la telemetrie passent par les cables qui touchent la structure
     * ({@link ComputeCore}) ; la capacite fait seulement que les cables s'y branchent (prise visible).
     */
    /** 0.20.34 : prise de donnees : vers les reservoirs de donnees du controleur, sinon la simple prise. */
    private final IDataHandler dataInlet = new IDataHandler() {
        @Override
        public int getDataTanks() {
            ControllerTile c = controller();
            return c == null ? 0 : c.dataInputs().size();
        }

        @NotNull
        @Override
        public DataStack getDataInTank(int tank) {
            ControllerTile c = controller();
            return c == null || tank >= c.dataInputs().size() ? DataStack.EMPTY : c.dataInputs().get(tank).getStack();
        }

        @Override
        public long getDataTankCapacity(int tank) {
            ControllerTile c = controller();
            return c == null || tank >= c.dataInputs().size() ? 0 : c.dataInputs().get(tank).getCapacity();
        }

        @Override
        public boolean isDataValid(int tank, @NotNull DataStack stack) {
            ControllerTile c = controller();
            return c != null && tank < c.dataInputs().size() && c.dataInputs().get(tank).isValid(stack);
        }

        @NotNull
        @Override
        public DataStack insertData(@NotNull DataStack stack, @NotNull Action action) {
            ControllerTile c = controller();
            return c == null ? stack : c.insertData(stack, action);
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
    };
    /** Port de chaleur deja passe au sens explicite (0.16.1) : les anciens ports, qui evacuaient, sont remis en sortie une fois. */
    private boolean heatMode;
    /** 0.20.28 : voisin de depart de l'ejection (tourne a chaque poussee). */
    private int turn;
    private LazyOptional<?> cap = LazyOptional.empty();
    @Nullable
    private Capability<?> capType;

    public PortTile(BlockPos pos, BlockState state) {
        super(PiIndustry.PORT_TILE.get(), pos, state);
        type = state.getBlock() instanceof PortBlock port ? port.type : PortBlock.Type.ITEM;
    }

    public boolean isOutput() {
        BlockState state = getBlockState();
        return type.directional() && state.hasProperty(PortBlock.OUTPUT) && state.getValue(PortBlock.OUTPUT);
    }

    @Nullable
    ControllerTile controller() {
        if (level == null) {
            return null;
        }
        BlockPos pos = Structures.controllerAt(level, worldPosition);
        return pos != null && level.getBlockEntity(pos) instanceof ControllerTile tile && tile.formed() ? tile : null;
    }

    /** La structure ou le sens ont change : les capacites pretees sont refaites. */
    public void refresh() {
        cap.invalidate();
        cap = LazyOptional.empty();
        capType = null;
        if (level != null && !level.isClientSide) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> wanted, @Nullable Direction side) {
        Capability<?> mine = switch (type) {
            case ITEM -> ForgeCapabilities.ITEM_HANDLER;
            case FLUID -> ForgeCapabilities.FLUID_HANDLER;
            case GAS -> PioneersAPI.GAS_HANDLER;
            case ENERGY -> ForgeCapabilities.ENERGY;
            case HEAT -> PioneersAPI.HEAT_HANDLER;
            case PRESSURE -> PioneersAPI.PRESSURE_HANDLER;
            case DATA -> PioneersAPI.DATA_HANDLER;
        };
        if (mine == null || wanted != mine) {
            return super.getCapability(wanted, side);
        }
        if (type == PortBlock.Type.DATA) {
            //Prise toujours presente : les cables s'y branchent avant meme la formation ; 0.20.34 : elle remplit les reservoirs de donnees du
            //controleur qui en a (synthetiseur)
            return LazyOptional.of(() -> dataInlet).cast();
        }
        if (capType != mine || !cap.isPresent()) {
            //Lot 5 : contrat de port commun (MultiblockPort) : le controleur forme qui contient le port, sinon l'hote hors catalogue
            //(1.9.2 : halls d'assemblage)
            MultiblockPort host = MultiblockPort.find(level, worldPosition);
            Object handler = host == null ? null : host.portHandler(type, isOutput(), worldPosition);
            if (handler == null) {
                return LazyOptional.empty();
            }
            cap = LazyOptional.of(() -> handler);
            capType = mine;
        }
        return cap.cast();
    }

    /** Sens choisi a la cle : plus de remise en sortie automatique. */
    void markHeatMode() {
        heatMode = true;
        setChanged();
    }

    /**
     * Ancien port de chaleur (avant 0.16.1, il conduisait dans les deux sens) : mis une fois dans le sens qui garde son role, en sortie
     * (evacuation), sauf sur une machine qui refroidit (tour de refroidissement : en entree) ; appele par le controleur.
     */
    void migrateHeat(boolean wantOutput) {
        if (type != PortBlock.Type.HEAT || heatMode || level == null || level.isClientSide) {
            return;
        }
        heatMode = true;
        BlockState state = getBlockState();
        if (state.hasProperty(PortBlock.OUTPUT) && state.getValue(PortBlock.OUTPUT) != wantOutput) {
            level.setBlock(worldPosition, state.setValue(PortBlock.OUTPUT, wantOutput), Block.UPDATE_CLIENTS);
        }
        setChanged();
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        cap.invalidate();
    }

    // ------------------------------------------------------------------
    // Poussee des sorties (appelee par le controleur toutes les 10 ticks)
    // ------------------------------------------------------------------

    void push(ControllerTile controller, Structure.Result structure) {
        turn = (turn + 1) % 6;
        switch (type) {
            case ITEM -> SmartPorts.eject(level, worldPosition, structure, 0, controller.outputsFor(worldPosition), turn);
            case FLUID -> SmartPorts.eject(level, worldPosition, structure, 1, controller.outputsFor(worldPosition), turn);
            case GAS -> SmartPorts.eject(level, worldPosition, structure, 2, controller.outputsFor(worldPosition), turn);
            case ENERGY -> pushVia(Transfer.ENERGY, controller.energyHandler(true), controller.refillEnergy(), Long.MAX_VALUE / 4, structure);
            default -> {
            }
        }
    }

    /** {@code refund} : chemin interne pour remettre ce que le voisin n'a finalement pas pris (les sorties refusent l'exterieur). */
    private <H, R> void pushVia(Transfer<H, R> transfer, H own, H refund, long max, Structure.Result structure) {
        for (Direction direction : Directions.ALL) {
            BlockPos neighbor = worldPosition.relative(direction);
            //1.9 (MAJ RP, bloc 4) : rien ne sort a travers une frontiere de claim refusee
            if (structure.isPart(neighbor) || !level.isLoaded(neighbor) || !ClanApi.canTransfer(level, worldPosition, neighbor)) {
                continue;
            }
            BlockEntity tile = level.getBlockEntity(neighbor);
            H target = tile == null ? null : transfer.find(tile, direction.getOpposite());
            if (target == null) {
                continue;
            }
            R offered = transfer.extract(own, max, true);
            if (transfer.isEmpty(offered)) {
                return;
            }
            long accepted = transfer.insert(target, offered, true);
            if (accepted <= 0) {
                continue;
            }
            R taken = transfer.extractLike(own, offered, accepted, false);
            long placed = transfer.insert(target, taken, false);
            long left = transfer.amount(taken) - placed;
            if (left > 0) {
                transfer.insert(refund, transfer.withAmount(taken, left), false);
            }
        }
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (type == PortBlock.Type.HEAT) {
            tag.putBoolean("heatMode", heatMode);
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        heatMode = tag.getBoolean("heatMode");
    }
}
