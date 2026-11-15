package fr.pioneers.kinetics.machines;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.kinetics.KineticMachineTile;
import fr.pioneers.registries.PiKinetics;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Machine mecanique (meule, tamis, concasseur, scie, tour, perceuse, noria, soufflet ; 1.0.2-beta : le martinet est retire). API Machine
 * (lot 7) : la tile ne fait plus que PORTER son unite de procede ({@link MechanicalProcess} : recettes, avancement en tours, selecteur,
 * soufflet, noria, perceuse) par l'adaptateur cinetique {@link KineticMachineTile} ; elle reste le noeud du reseau de rotation (arbre par les
 * flancs, inertie, limites) et garde ses methodes pour le rendu, le bloc, JEI et les GameTests. Type de tile {@code mechanical_machine},
 * cles de sauvegarde et ordre des cases inchanges.
 */
public class MechanicalMachineTile extends KineticMachineTile implements RecipeLockable {

    public static final int STATUS_IDLE = MechanicalProcess.STATUS_IDLE, STATUS_WORKING = MechanicalProcess.STATUS_WORKING,
          STATUS_SLOW = MechanicalProcess.STATUS_SLOW, STATUS_FULL = MechanicalProcess.STATUS_FULL, STATUS_STOPPED = MechanicalProcess.STATUS_STOPPED;

    private final MechanicalProcess unit;
    private final LazyOptional<IFluidHandler> waterOut;

    public MechanicalMachineTile(BlockPos pos, BlockState state) {
        super(PiKinetics.MACHINE_TILE.get(), pos, state);
        unit = carry(new MechanicalProcess(host, this, type(state)));
        waterOut = LazyOptional.of(() -> new NoriaOutlet(unit));
    }

    public static MachineType type(BlockState state) {
        return state.getBlock() instanceof MechanicalMachineBlock block ? block.type : MachineType.MILLSTONE;
    }

    public MachineType type() {
        return type(getBlockState());
    }

    public MechanicalProcess process() {
        return unit;
    }

    public Direction facing() {
        return getBlockState().getValue(MechanicalMachineBlock.FACING);
    }

    public float progress() {
        return unit.progressRatio();
    }

    public int status() {
        return unit.status();
    }

    public List<ItemSlot> inputs() {
        return unit.itemInputs();
    }

    /** Objet en cours de travail (affiche par le rendu : buche sur la scie, piece sur le tour...). */
    public ItemStack workpiece() {
        return unit.workpiece();
    }

    void onBroken() {
        unit.onBroken();
    }

    void dropContents(Consumer<ItemStack> drop) {
        unit.dropContents(drop);
    }

    // ------------------------------------------------------------------
    // Reseau (la charge est declaree par l'unite)
    // ------------------------------------------------------------------

    @Override
    public double shaftFactor(Direction face) {
        return face.getAxis() == MechanicalMachineBlock.axis(getBlockState()) ? 1 : 0;
    }

    @Override
    public double inertia() {
        return type().inertia;
    }

    @Override
    public double maxTorque() {
        return 12_000;
    }

    @Override
    public double maxSpeed() {
        return type().nominalSpeed * 3;
    }

    /**
     * 1.0.4-beta (lot 27, audit de fluidite) : les donnees propres de la machine (etat, avancement par pas de 10 %, piece travaillee) partent
     * deja quand elles changent ({@code MechanicalProcess.clientDirty}, changement d'etat : {@code syncNow}) et le client n'en extrapole rien
     * avec la vitesse : plus de paquet de bloc par machine a chaque synchronisation de la rotation (elle part dans le paquet du reseau). Banc
     * tools/fluidity : 2 a 3 fois moins de paquets de bloc au demarrage d'une installation.
     */
    @Override
    protected boolean ownClientData() {
        return false;
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public List<StandardLayout.Part> guiParts() {
        MachineType type = type();
        boolean recipe = !recipeSource().isEmpty();
        return unit.layout.ageParts(recipe, false);
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("kinetics.pioneers.machine.status." + unit.status()));
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.FLUID_HANDLER && side != Direction.DOWN && type() == MachineType.NORIA) {
            return waterOut.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        waterOut.invalidate();
    }

    // ------------------------------------------------------------------
    // Selecteur de recette (porte par l'unite)
    // ------------------------------------------------------------------

    @Override
    public String recipeSource() {
        return unit.recipeSource();
    }

    @Override
    public RecipeLock recipeLock() {
        return unit.recipeLock();
    }

    @Override
    public void recipeLockChanged() {
        unit.recipeLockChanged();
    }

    @Override
    public int[] recipeSlot() {
        return unit.recipeSlot();
    }

    @Override
    public int recipeTier() {
        return 0;
    }

    @Override
    public List<ItemStack> presentItems() {
        return unit.presentItems();
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    /** Pour JEI et le guide : conteneur d'essai. */
    static SimpleContainer probe(ItemStack stack) {
        return new SimpleContainer(stack);
    }

    /** Noria : reserve d'eau offerte aux conduites et reservoirs voisins (ils s'y raccordent). */
    private record NoriaOutlet(MechanicalProcess unit) implements IFluidHandler {

        @Override
        public int getTanks() {
            return 1;
        }

        @Override
        public @NotNull FluidStack getFluidInTank(int tank) {
            return unit.water() <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, unit.water());
        }

        @Override
        public int getTankCapacity(int tank) {
            return 2_000;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return false;
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            return 0;
        }

        @Override
        public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
            return resource.getFluid().isSame(Fluids.WATER) ? drain(resource.getAmount(), action) : FluidStack.EMPTY;
        }

        @Override
        public @NotNull FluidStack drain(int maxDrain, FluidAction action) {
            int n = unit.drainWater(maxDrain, action.execute());
            return n <= 0 ? FluidStack.EMPTY : new FluidStack(Fluids.WATER, n);
        }
    }
}
