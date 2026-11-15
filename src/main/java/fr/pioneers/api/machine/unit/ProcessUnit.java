package fr.pioneers.api.machine.unit;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.api.machine.UiValues;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.core.side.SideType;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import org.jetbrains.annotations.Nullable;

/**
 * API Machine (lot 2) : UNITE DE PROCEDE d'une machine compacte (recette, avancement, selecteur, sorties), sortie de l'ancienne
 * {@code AtelierTile} et du kit ({@code ProcessingTile}). La machine ({@code AtelierTile}, sur {@link MachineTile}) ne fait que la porter :
 * l'unite declare ses cases et reservoirs (positions de {@link StandardLayout}), tourne a chaque tick, sauve ses cles, publie ses valeurs
 * d'interface nommees ({@link UiValues#PROGRESS}...) et tient le verrou de recette ({@link RecipeLockable}).
 * <p>
 * Deux procedes : {@link IndustryProcess} (recettes industrielles des ateliers : machines, puits et pompes, generateur a combustion) et
 * {@link KitProcess} (broyeur, four electrique, compacteur du kit). Les cles de sauvegarde et l'ordre des cases sont ceux d'avant le lot 2
 * (instantanes {@code tools/api/snapshots}).
 * <p>
 * Lot 4 : raccrochee a {@link MachineUnit} (meme base que les generateurs : {@code Access}, {@code tick}, valeurs, sauvegarde, client).
 */
public abstract class ProcessUnit extends MachineUnit implements RecipeLockable {

    /** Etats de l'ecran (cles de langue {@code industry.pioneers.status.<n>}). */
    public static final int IDLE = 0, WORKING = 1, NO_ENERGY = 2, FULL = 3, NO_RECIPE = 4, TIER = 5, NO_DEPOSIT = 6, EXHAUSTED = 7, NO_FLUID = 8;

    public final CableTier tier;
    public final StandardLayout layout;
    protected final List<ItemSlot> inputs = new ArrayList<>(), outputs = new ArrayList<>();
    protected final List<FluidTank> fluidIn = new ArrayList<>(), fluidOut = new ArrayList<>();
    protected final List<BasicGasTank> gasIn = new ArrayList<>(), gasOut = new ArrayList<>();
    protected final RecipeLock lock = new RecipeLock();
    protected int progress, duration = 1, status;
    /** Tier demande par la recette bloquee (-1 : aucun). */
    protected int tierNeeded = -1;

    protected ProcessUnit(UnitHost machine, CableTier tier, StandardLayout.Counts counts) {
        super(machine);
        this.tier = tier;
        this.layout = StandardLayout.of(counts);
    }

    /** Un tick de travail (serveur). */
    @Override
    public abstract void tick();

    /** La machine produit-elle de l'energie (faces d'energie en sortie) ? */
    public boolean producesEnergy() {
        return false;
    }

    /** Ressources ejectables (interrupteur du panneau des faces). */
    public SideType[] ejectTypes() {
        return new SideType[]{SideType.ITEMS, SideType.FLUIDS, SideType.GASES};
    }

    /** Production electrique en cours (FE/t), pour l'interface. */
    public long production() {
        return 0;
    }

    /** Etat de la machine ({@link #IDLE}, {@link #WORKING}...). */
    public int status() {
        return status;
    }

    // ------------------------------------------------------------------
    // Sauvegarde, client, interface
    // ------------------------------------------------------------------

    /** Sauvegarde ancienne a remettre au format d'aujourd'hui avant la lecture (migration des cases) ; par defaut telle quelle. */
    public CompoundTag migrate(CompoundTag tag) {
        return tag;
    }

    @Override
    public abstract void save(CompoundTag tag);

    @Override
    public abstract void load(CompoundTag tag);

    @Override
    public void writeClient(CompoundTag tag) {
        lock.writeClient(tag);
    }

    @Override
    public void readClient(CompoundTag tag) {
        lock.readClient(tag);
    }

    /** Valeurs d'interface (apres les 4 communes) : avancement, duree, etat, production, tier demande ; l'ordre est celui d'avant le lot 2. */
    @Override
    public void declareValues(UiValues.Builder values) {
        values.add(UiValues.PROGRESS, () -> progress).add(UiValues.DURATION, () -> duration).add(UiValues.STATUS, () -> status)
              .add(UiValues.PRODUCTION, this::production).add(UiValues.TIER_NEEDED, () -> status == TIER ? tierNeeded : -1);
    }

    // ------------------------------------------------------------------
    // Cases et reservoirs
    // ------------------------------------------------------------------

    public List<ItemSlot> itemInputs() {
        return inputs;
    }

    public List<ItemSlot> itemOutputs() {
        return outputs;
    }

    public List<FluidTank> fluidInputs() {
        return fluidIn;
    }

    public List<FluidTank> fluidOutputs() {
        return fluidOut;
    }

    public List<BasicGasTank> gasInputs() {
        return gasIn;
    }

    public List<BasicGasTank> gasOutputs() {
        return gasOut;
    }

    /** 1.0.5-beta (lot 44) : la case « Outil » des remanents (null : la machine n'en a pas). */
    @Nullable
    public ItemSlot toolSlot() {
        return null;
    }

    /** 1.0.5-beta (lot 44) : la case Outil en liste ({@code RecipeMatch.Buffers#toolSlots}) ; vide sans case Outil. */
    public List<ItemSlot> toolSlots() {
        return List.of();
    }

    /** Toutes les entrees sont vides ? */
    protected boolean inputsEmpty() {
        for (ItemSlot slot : inputs) {
            if (!slot.isEmpty()) {
                return false;
            }
        }
        for (FluidTank tank : fluidIn) {
            if (!tank.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    // ------------------------------------------------------------------
    // Selecteur de recette
    // ------------------------------------------------------------------

    @Override
    public RecipeLock recipeLock() {
        return lock;
    }

    @Override
    public int[] recipeSlot() {
        return new int[]{layout.recipeX, layout.recipeY};
    }

    @Override
    public int recipeTier() {
        return tier.ordinal();
    }

    @Override
    public List<ItemStack> presentItems() {
        List<ItemStack> out = new ArrayList<>();
        for (ItemSlot slot : inputs) {
            out.add(slot.getStack());
        }
        return out;
    }

    @Override
    public List<FluidStack> presentFluids() {
        List<FluidStack> out = new ArrayList<>();
        for (FluidTank t : fluidIn) {
            out.add(t.getFluid());
        }
        return out;
    }

    @Override
    public List<GasStack> presentGases() {
        List<GasStack> out = new ArrayList<>();
        for (BasicGasTank t : gasIn) {
            out.add(t.getStack());
        }
        return out;
    }

    @Nullable
    @Override
    public Level recipeLevel() {
        return machine.level();
    }

    /** La machine a-t-elle un selecteur (des recettes a choisir) ? Meme regle qu'avant le lot 2 : une liste de recettes non vide. */
    public boolean hasSelector() {
        return !recipeChoices().isEmpty();
    }
}
