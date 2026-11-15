package fr.pioneers.kinetics.kitchen;

import fr.pioneers.api.machine.StandardLayout;
import fr.pioneers.core.Automation;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.core.inventory.ItemSlots;
import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.recipe.RecipeLockable;
import fr.pioneers.farm.GeneratedFarm;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.kinetics.KineticMachineTile;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidUtil;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 : poste mecanique de la cuisine ({@link KitchenMachineType}). API Machine (lot 8b) : la tile ne fait plus que PORTER son unite de
 * procede ({@link KitchenProcess} : recettes, travail en tours, trop-plein, recette fixee = verrou du selecteur standard) par l'adaptateur
 * cinetique {@link KineticMachineTile} ; elle reste le noeud du reseau de rotation (arbre par les flancs ou par dessous, inertie, limites)
 * et garde ce qui est propre au bloc : faces des objets (entonnoirs et goulottes entrent par le dessus et les cotes, sortent par le dessous
 * et les cotes), conduites (remplissage du reservoir d'entree, vidange du reservoir de sortie), methodes du rendu. 1.0.5-beta (lot 41,
 * retour 60) : plus de seau ni de bouteille au clic droit (paires « Remplir » / « Vider » de l'interface) ni de fiche au regard. Type de tile {@code kitchen_machine}, cles de sauvegarde et ordre des cases inchanges.
 */
public class KitchenMachineTile extends KineticMachineTile implements RecipeLockable {

    public static final int STATUS_IDLE = KitchenProcess.STATUS_IDLE, STATUS_WORKING = KitchenProcess.STATUS_WORKING,
          STATUS_SLOW = KitchenProcess.STATUS_SLOW, STATUS_FULL = KitchenProcess.STATUS_FULL, STATUS_STOPPED = KitchenProcess.STATUS_STOPPED;
    /** Actions d'avant : recette precedente / suivante (automatique compris). */
    public static final int RECIPE_PREV = CUSTOM, RECIPE_NEXT = CUSTOM + 1;

    private final KitchenMachineType type;
    private final KitchenProcess unit;
    private final LazyOptional<IItemHandler> itemsAll, itemsUp, itemsDown;
    private final LazyOptional<IFluidHandler> fluids;

    public KitchenMachineTile(BlockPos pos, BlockState state) {
        super(PiKitchenMachines.TILE.get(), pos, state);
        type = type(state);
        unit = carry(new KitchenProcess(host, this, type));
        //1.0.5-beta (lot 32) : + cases « Remplir » de l'extracteur de miel (contenant vide par le haut, plein par le bas)
        ItemSlots both = new ItemSlots(unit.all(), Automation.EXTERNAL), up = new ItemSlots(unit.upSlots(), Automation.EXTERNAL),
              down = new ItemSlots(unit.downSlots(), Automation.EXTERNAL);
        itemsAll = LazyOptional.of(() -> both);
        itemsUp = LazyOptional.of(() -> up);
        itemsDown = LazyOptional.of(() -> down);
        fluids = LazyOptional.of(this::fluidHandler);
    }

    public static KitchenMachineType type(BlockState state) {
        return state.getBlock() instanceof KitchenMachineBlock block ? block.type : KitchenMachineType.PRESS;
    }

    public KitchenMachineType type() {
        return type;
    }

    public KitchenProcess process() {
        return unit;
    }

    public Direction facing() {
        return getBlockState().getValue(KitchenMachineBlock.FACING);
    }

    public List<ItemSlot> inputs() {
        return unit.itemInputs();
    }

    public List<ItemSlot> outputs() {
        return unit.itemOutputs();
    }

    @Nullable
    public FluidTank tankIn() {
        return unit.tankIn();
    }

    @Nullable
    public FluidTank tankOut() {
        return unit.tankOut();
    }

    public int status() {
        return unit.status();
    }

    @Nullable
    public ResourceLocation locked() {
        return unit.locked();
    }

    /** Recettes des stations de la machine (voir {@link KitchenProcess#recipes}). */
    public static List<KitchenRecipe> recipes(Level level, KitchenMachineType type) {
        return KitchenProcess.recipes(level, type);
    }

    // ------------------------------------------------------------------
    // Reseau (la charge est declaree par l'unite)
    // ------------------------------------------------------------------

    @Override
    public double shaftFactor(Direction face) {
        if (type.vertical) {
            return face == Direction.DOWN ? 1 : 0;
        }
        return face.getAxis() == KitchenMachineBlock.axis(getBlockState()) ? 1 : 0;
    }

    @Override
    public double inertia() {
        return type.inertia;
    }

    @Override
    public double maxTorque() {
        return 12_000;
    }

    @Override
    public double maxSpeed() {
        return type.nominalSpeed * 3;
    }

    /** Actions d'avant (fleches de la recette fixee) : gardees pour les paquets ; l'interface v2 passe par le carre du selecteur. */
    @Override
    public void handleCustom(ServerPlayer player, int action) {
        if (action == RECIPE_PREV || action == RECIPE_NEXT) {
            unit.cycle(action == RECIPE_NEXT);
        }
    }

    void dropContents(Consumer<ItemStack> drop) {
        unit.dropContents(drop);
    }

    // ------------------------------------------------------------------
    // Rendu (client)
    // ------------------------------------------------------------------

    public ItemStack workpiece() {
        return unit.workpiece();
    }

    public int inputCount() {
        return unit.inputCount();
    }

    public float progress(float partial) {
        return unit.progress(partial);
    }

    public boolean working() {
        return unit.status() == STATUS_WORKING;
    }

    /** Recette fixee (client : interface) ; null = automatique. */
    @Nullable
    public KitchenRecipe lockedRecipeObject() {
        return unit.fixedRecipe();
    }

    public FluidStack fluidIn() {
        return unit.tankIn() == null ? FluidStack.EMPTY : unit.tankIn().getFluid();
    }

    public FluidStack fluidOut() {
        return unit.tankOut() == null ? FluidStack.EMPTY : unit.tankOut().getFluid();
    }

    // ------------------------------------------------------------------
    // Interface standard v2
    // ------------------------------------------------------------------

    @Override
    public List<StandardLayout.Part> guiParts() {
        //1.0.5-beta (lot 32) : cases « Remplir » ; lot 41 : aussi « Vider » (controle des chevauchements : tools/check_machine_api.py)
        List<int[]> extraIn = new java.util.ArrayList<>();
        List<ItemSlot> extraOut = new java.util.ArrayList<>();
        if (unit.containerIn() != null && unit.containerOut() != null) {
            extraIn.add(new int[]{unit.containerIn().x, unit.containerIn().y});
            extraOut.add(unit.containerOut());
        }
        if (unit.drainIn() != null && unit.drainOut() != null) {
            extraIn.add(new int[]{unit.drainIn().x, unit.drainIn().y});
            extraOut.add(unit.drainOut());
        }
        List<StandardLayout.Part> parts = new java.util.ArrayList<>(unit.layout.ageParts(true, false, extraIn.toArray(int[][]::new)));
        for (ItemSlot o : extraOut) {
            parts.add(new StandardLayout.Part("slot_out", StandardLayout.slotFrame(o.x, o.y)));
        }
        return parts;
    }

    /**
     * 1.0.5-beta (lot 41, retour 60 de Guillaume : « fin de la bulle d'info, car l'interface est explicite ») : plus de fiche au regard pour
     * les postes mecaniques de la cuisine (vitesse, couple et etat sont dans l'interface).
     */
    @Nullable
    @Override
    public fr.pioneers.farm.FarmCard lookCard(boolean sneaking) {
        return null;
    }

    @Override
    public void describe(List<Component> lines) {
        super.describe(lines);
        lines.add(Component.translatable("kinetics.pioneers.machine.status." + unit.status()));
        KitchenRecipe recipe = unit.recipe();
        if (recipe != null) {
            ItemStack result = level == null ? ItemStack.EMPTY : recipe.getResultItem(level.registryAccess());
            Component what = !result.isEmpty() ? result.getHoverName() : recipe.fluidResult().isEmpty() ? Component.literal("?")
                  : recipe.fluidResult().getDisplayName();
            lines.add(Component.translatable("kitchen_machine.pioneers.making", what, Math.round(progress(0) * 100)));
        }
        FluidTank tankIn = unit.tankIn(), tankOut = unit.tankOut();
        if (tankIn != null && !tankIn.isEmpty()) {
            lines.add(Component.translatable("kitchen_machine.pioneers.tank", tankIn.getFluid().getDisplayName(), tankIn.getFluidAmount()));
        }
        if (tankOut != null && !tankOut.isEmpty()) {
            lines.add(Component.translatable("kitchen_machine.pioneers.tank", tankOut.getFluid().getDisplayName(), tankOut.getFluidAmount()));
        }
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
    public List<FluidStack> presentFluids() {
        return unit.presentFluids();
    }

    @Override
    public Level recipeLevel() {
        return level;
    }

    // ------------------------------------------------------------------
    // Capacites
    // ------------------------------------------------------------------

    private IFluidHandler fluidHandler() {
        return new IFluidHandler() {
            private FluidTank at(int i) {
                FluidTank tankIn = unit.tankIn(), tankOut = unit.tankOut();
                return tankIn != null ? (i == 0 ? tankIn : tankOut) : tankOut;
            }

            @Override
            public int getTanks() {
                return (unit.tankIn() != null ? 1 : 0) + (unit.tankOut() != null ? 1 : 0);
            }

            @Override
            public @NotNull FluidStack getFluidInTank(int i) {
                FluidTank t = at(i);
                return t == null ? FluidStack.EMPTY : t.getFluid();
            }

            @Override
            public int getTankCapacity(int i) {
                FluidTank t = at(i);
                return t == null ? 0 : t.getCapacity();
            }

            @Override
            public boolean isFluidValid(int i, @NotNull FluidStack stack) {
                FluidTank tankIn = unit.tankIn();
                return tankIn != null && at(i) == tankIn && tankIn.isFluidValid(stack);
            }

            @Override
            public int fill(FluidStack resource, FluidAction action) {
                return unit.tankIn() == null ? 0 : unit.tankIn().fill(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(FluidStack resource, FluidAction action) {
                FluidTank tankOut = unit.tankOut();
                return tankOut == null || !resource.isFluidEqual(tankOut.getFluid()) ? FluidStack.EMPTY : tankOut.drain(resource, action);
            }

            @Override
            public @NotNull FluidStack drain(int max, FluidAction action) {
                return unit.tankOut() == null ? FluidStack.EMPTY : unit.tankOut().drain(max, action);
            }
        };
    }

    @NotNull
    @Override
    public <T> LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
        if (cap == ForgeCapabilities.ITEM_HANDLER) {
            return side == Direction.UP ? itemsUp.cast() : side == Direction.DOWN ? itemsDown.cast() : itemsAll.cast();
        }
        if (cap == ForgeCapabilities.FLUID_HANDLER && (unit.tankIn() != null || unit.tankOut() != null)) {
            return fluids.cast();
        }
        return super.getCapability(cap, side);
    }

    @Override
    public void invalidateCaps() {
        super.invalidateCaps();
        itemsAll.invalidate();
        itemsUp.invalidate();
        itemsDown.invalidate();
        fluids.invalidate();
    }
}
