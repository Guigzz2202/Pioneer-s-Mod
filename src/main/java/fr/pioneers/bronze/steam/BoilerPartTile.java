package fr.pioneers.bronze.steam;

import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.registries.PiBronze;
import java.util.Objects;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Piece de chaudiere : connait son foyer (pose par la formation). Dome : noeud de vapeur passif (la reserve du foyer : on y puise, on ne la
 * remplit jamais). Corps : accepte l'eau par toutes ses faces.
 */
public class BoilerPartTile extends PiBlockEntity implements SteamNode {

    @Nullable
    private BlockPos controller;
    private final LazyOptional<IFluidHandler> water = LazyOptional.of(() -> new Water());

    public BoilerPartTile(BlockPos pos, BlockState state) {
        super(PiBronze.BOILER_PART_TILE.get(), pos, state);
    }

    public BoilerPart part() {
        return getBlockState().getBlock() instanceof BoilerPartBlock block ? block.part : BoilerPart.COPPER_SHELL;
    }

    @Nullable
    public FireboxTile controller() {
        //Lot 8a : foyer trouve par le registre commun des hotes (cases de la chaudiere, MultiblockPort)
        FireboxTile host = fr.pioneers.api.multiblock.MultiblockPort.house(level, worldPosition, FireboxTile.class);
        if (host != null && host.owns(worldPosition)) {
            return host;
        }
        if (controller == null || level == null || !level.isLoaded(controller)) {
            return null;
        }
        return level.getBlockEntity(controller) instanceof FireboxTile firebox && firebox.owns(worldPosition) ? firebox : null;
    }

    @Nullable
    public BlockPos controllerPos() {
        return controller;
    }

    void setController(@Nullable BlockPos pos) {
        if (Objects.equals(pos, controller)) {
            return;
        }
        controller = pos;
        setChanged();
        //Les tuyaux voisins d'un dome se (de)raccordent
        if (level != null && part() == BoilerPart.STEAM_DOME) {
            level.updateNeighborsAt(worldPosition, getBlockState().getBlock());
        }
    }

    @Override
    public SteamVolume steam(Direction side) {
        if (part() != BoilerPart.STEAM_DOME || side == Direction.DOWN) {
            return null;
        }
        FireboxTile firebox = controller();
        return firebox == null ? null : firebox.steamVolume();
    }

    @Override
    public double conductance(Direction side) {
        return 2 * SteamRules.FACE_FLOW;
    }

    @Override
    public boolean active() {
        return false;
    }

    @Override
    public boolean steamSource() {
        return true;
    }

    @Override
    public BlockPos nodePos() {
        return worldPosition;
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && part().shell()) {
            return water.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        water.invalidate();
    }

    @Override
    protected void saveAdditional(@NotNull CompoundTag tag) {
        super.saveAdditional(tag);
        if (controller != null) {
            tag.putLong("controller", controller.asLong());
        }
    }

    @Override
    public void load(@NotNull CompoundTag tag) {
        super.load(tag);
        controller = tag.contains("controller") ? BlockPos.of(tag.getLong("controller")) : null;
    }

    /** L'eau entre dans la chaudiere (pas de sortie). */
    private final class Water implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            FireboxTile firebox = controller();
            return firebox == null ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, (int) firebox.water());
        }

        @Override
        public int getTankCapacity(int tank) {
            FireboxTile firebox = controller();
            return firebox == null ? 0 : firebox.waterCapacity();
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return stack.getFluid().isSame(Fluids.WATER);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            FireboxTile firebox = controller();
            if (firebox == null || !isFluidValid(0, resource)) {
                return 0;
            }
            return firebox.fillWater(resource.getAmount(), action.simulate());
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
}
