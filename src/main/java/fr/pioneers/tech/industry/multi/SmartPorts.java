package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Action;
import fr.pioneers.core.Automation;
import fr.pioneers.core.Directions;
import fr.pioneers.core.gas.BasicGasTank;
import fr.pioneers.core.gas.GasAccess;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IoTank;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.fluids.capability.templates.FluidTank;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.28 : ports intelligents des multiblocs industriels (demande de Guillaume : « on ne devrait pas dire au port dans quoi il insere ou ce
 * qu'il extrait ; il faut seulement lui dire entree ou sortie ; le port doit etre tres intelligent sur les ressources recues et envoyees,
 * avec une ejection automatique de base »). Plus de case ni de reservoir vise : le port range et ejecte tout seul.
 * <p>
 * <b>Entree</b> (ce qui arrive des cables, tuyaux, entonnoirs) :
 * <ul>
 *     <li>objets ({@link Items}) : seulement ce qu'une recette de la machine prend ; d'abord sur la pile du meme objet ; une case vide
 *     seulement si aucune pile de cet objet n'a encore de place, et en gardant assez de cases libres pour les autres ingredients de ses
 *     recettes (un ingredient ne remplit jamais toutes les cases) ;</li>
 *     <li>fluides ({@link Fluids}) : un reservoir par fluide (jamais deux reservoirs du meme fluide, qui bloqueraient le second ingredient) ;
 *     le caloporteur (eau, caloporteur, azote liquide) va d'abord au reservoir de refroidissement tant qu'il est a moins de moitie sur une
 *     machine qui chauffe, puis aux recettes, puis encore au refroidissement ;</li>
 *     <li>gaz ({@link Gases}) : un reservoir par gaz.</li>
 * </ul>
 * <b>Sortie</b> : ejection automatique toutes les 10 ticks vers chaque voisin hors de la structure ({@link #eject}) : chaque case, chaque
 * reservoir, essayes un par un (un voisin qui refuse un produit recoit quand meme les autres), voisins pris a tour de role.
 */
public final class SmartPorts {

    private SmartPorts() {
    }

    // ------------------------------------------------------------------ entree : objets

    /** Cases d'entree vues par un port d'entree. */
    public static final class Items implements IItemHandler {

        private final List<ItemSlot> slots;
        /** Ingredients-objets distincts des recettes ou entre l'objet (au moins 1). */
        private final ToIntFunction<ItemStack> kinds;

        public Items(List<ItemSlot> slots, ToIntFunction<ItemStack> kinds) {
            this.slots = slots;
            this.kinds = kinds;
        }

        @Override
        public int getSlots() {
            return slots.size();
        }

        @NotNull
        @Override
        public ItemStack getStackInSlot(int slot) {
            return slot >= 0 && slot < slots.size() ? slots.get(slot).getStack() : ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
            if (stack.isEmpty() || slot < 0 || slot >= slots.size()) {
                return stack;
            }
            ItemSlot target = slots.get(slot);
            if (!target.isEmpty()) {
                //0.20.46 (audit) : case d'un autre objet refusee avant le filtre ; insert() verifie la place puis le filtre
                return ItemHandlerHelper.canItemStacksStack(target.getStack(), stack) ? target.insert(stack, Action.get(!simulate), Automation.EXTERNAL)
                                                                                       : stack;
            }
            if (!target.isItemValid(stack)) {
                return stack;
            }
            return allowNewSlot(stack) ? target.insert(stack, Action.get(!simulate), Automation.EXTERNAL) : stack;
        }

        /**
         * Une case vide peut-elle recevoir cet objet ? 0.20.46 (audit) : meme regle, sans liste ni flux (appele a chaque essai d'insertion
         * dans une case vide) : sortes presentes comptees en place, l'objet offert a part.
         */
        boolean allowNewSlot(ItemStack stack) {
            int empty = 0, others = 0;
            int n = slots.size();
            for (int i = 0; i < n; i++) {
                ItemSlot s = slots.get(i);
                if (s.isEmpty()) {
                    empty++;
                    continue;
                }
                ItemStack held = s.getStack();
                if (ItemHandlerHelper.canItemStacksStack(held, stack)) {
                    if (held.getCount() < s.getLimit(held)) {
                        //Une pile de cet objet a encore de la place : elle d'abord
                        return false;
                    }
                    continue;
                }
                //Autre sorte : comptee une fois (a sa premiere case)
                boolean seen = false;
                for (int j = 0; j < i && !seen; j++) {
                    ItemSlot o = slots.get(j);
                    seen = !o.isEmpty() && ItemHandlerHelper.canItemStacksStack(o.getStack(), held);
                }
                if (!seen) {
                    others++;
                }
            }
            if (empty == 0) {
                return false;
            }
            int reserve = Math.max(0, kinds.applyAsInt(stack) - 1 - others);
            return empty - 1 >= reserve;
        }

        @NotNull
        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return slot >= 0 && slot < slots.size() ? slots.get(slot).getLimit(ItemStack.EMPTY) : 0;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            return slot >= 0 && slot < slots.size() && slots.get(slot).isItemValid(stack);
        }
    }

    // ------------------------------------------------------------------ entree : fluides

    /** Reservoirs d'entree (recettes) et reservoir de refroidissement vus par un port d'entree. */
    public static final class Fluids implements IFluidHandler {

        private final List<? extends FluidTank> recipe;
        private final FluidTank coolant;
        private final Predicate<FluidStack> isCoolant;
        /** La machine chauffe : le refroidissement passe d'abord tant qu'il est a moins de moitie. */
        private final BooleanSupplier heats;

        public Fluids(List<? extends FluidTank> recipe, FluidTank coolant, Predicate<FluidStack> isCoolant, BooleanSupplier heats) {
            this.recipe = recipe;
            this.coolant = coolant;
            this.isCoolant = isCoolant;
            this.heats = heats;
        }

        private FluidTank tank(int i) {
            return i < recipe.size() ? recipe.get(i) : coolant;
        }

        @Override
        public int getTanks() {
            return recipe.size() + 1;
        }

        @NotNull
        @Override
        public FluidStack getFluidInTank(int tank) {
            return tank >= 0 && tank < getTanks() ? tank(tank).getFluid() : FluidStack.EMPTY;
        }

        @Override
        public int getTankCapacity(int tank) {
            return tank >= 0 && tank < getTanks() ? tank(tank).getCapacity() : 0;
        }

        @Override
        public boolean isFluidValid(int tank, @NotNull FluidStack stack) {
            return tank >= 0 && tank < getTanks() && tank(tank).isFluidValid(stack);
        }

        @Override
        public int fill(FluidStack resource, FluidAction action) {
            if (resource.isEmpty()) {
                return 0;
            }
            int total = resource.getAmount();
            boolean cool = isCoolant.test(resource) && coolant.isFluidValid(resource) && (coolant.isEmpty() || coolant.getFluid().isFluidEqual(resource));
            int coolRoom = cool ? coolant.getCapacity() - coolant.getFluidAmount() : 0;
            //Refroidissement d'abord, jusqu'a la moitie, sur une machine qui chauffe
            int first = cool && heats.getAsBoolean() ? Math.max(0, Math.min(total, Math.min(coolRoom, coolant.getCapacity() / 2 - coolant.getFluidAmount())))
                                                     : 0;
            FluidTank recipeTank = recipeTank(resource);
            int toRecipe = recipeTank == null ? 0 : recipeTank.fill(new FluidStack(resource, total - first), FluidAction.SIMULATE);
            int last = Math.min(total - first - toRecipe, coolRoom - first);
            int coolTotal = first + Math.max(0, last);
            if (action.execute()) {
                if (toRecipe > 0) {
                    recipeTank.fill(new FluidStack(resource, toRecipe), FluidAction.EXECUTE);
                }
                if (coolTotal > 0) {
                    coolant.fill(new FluidStack(resource, coolTotal), FluidAction.EXECUTE);
                }
            }
            return toRecipe + coolTotal;
        }

        /** Le reservoir de recette qui tient deja ce fluide, sinon un vide qui l'accepte (jamais deux reservoirs du meme fluide). */
        private FluidTank recipeTank(FluidStack resource) {
            for (FluidTank t : recipe) {
                if (!t.isEmpty() && t.getFluid().isFluidEqual(resource)) {
                    return t;
                }
            }
            for (FluidTank t : recipe) {
                if (t.isEmpty() && t.isFluidValid(resource)) {
                    return t;
                }
            }
            return null;
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

    // ------------------------------------------------------------------ entree : gaz

    /** Reservoirs de gaz d'entree : un reservoir par gaz. */
    public static final class Gases implements IGasHandler {

        private final List<BasicGasTank> tanks;

        public Gases(List<BasicGasTank> tanks) {
            this.tanks = tanks;
        }

        @Override
        public int getGasTanks() {
            return tanks.size();
        }

        @NotNull
        @Override
        public GasStack getGasInTank(int tank) {
            return tank >= 0 && tank < tanks.size() ? tanks.get(tank).getStack() : GasStack.EMPTY;
        }

        @Override
        public long getGasTankCapacity(int tank) {
            return tank >= 0 && tank < tanks.size() ? tanks.get(tank).getCapacity() : 0;
        }

        @Override
        public boolean isGasValid(int tank, @NotNull GasStack stack) {
            return tank >= 0 && tank < tanks.size() && tanks.get(tank).isValid(stack);
        }

        @NotNull
        @Override
        public GasStack insertGas(@NotNull GasStack stack, @NotNull Action action) {
            if (stack.isEmpty()) {
                return stack;
            }
            for (BasicGasTank t : tanks) {
                if (!t.isEmpty() && t.getStack().isSameGas(stack)) {
                    return t.insert(stack, action, Automation.EXTERNAL);
                }
            }
            for (BasicGasTank t : tanks) {
                if (t.isEmpty() && t.isValid(stack)) {
                    return t.insert(stack, action, Automation.EXTERNAL);
                }
            }
            return stack;
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

    // ------------------------------------------------------------------ sortie : ejection automatique

    /** Ce qu'un port de sortie ejecte. */
    record Outputs(List<ItemSlot> items, List<? extends FluidTank> fluids, List<BasicGasTank> gases) {}

    /**
     * Ejection d'un port de sortie vers ses voisins hors de la structure : chaque case et chaque reservoir, essayes un par un ; le voisin de
     * depart tourne d'un appel a l'autre (partage entre plusieurs machines voisines).
     */
    static void eject(Level level, BlockPos port, Structure.Result structure, int kind, Outputs out, int turn) {
        List<Direction> sides = new ArrayList<>(6);
        for (int i = 0; i < 6; i++) {
            Direction d = Directions.ALL[(i + turn) % 6];
            BlockPos n = port.relative(d);
            //1.9 (MAJ RP, bloc 4) : le port n'ejecte rien a travers une frontiere de claim refusee
            if (!structure.isPart(n) && level.isLoaded(n) && ClanApi.canTransfer(level, port, n)) {
                sides.add(d);
            }
        }
        if (sides.isEmpty()) {
            return;
        }
        for (Direction d : sides) {
            BlockEntity be = level.getBlockEntity(port.relative(d));
            if (be == null) {
                continue;
            }
            Direction face = d.getOpposite();
            switch (kind) {
                case 0 -> be.getCapability(ForgeCapabilities.ITEM_HANDLER, face).ifPresent(h -> items(h, out.items()));
                case 1 -> be.getCapability(ForgeCapabilities.FLUID_HANDLER, face).ifPresent(h -> fluids(h, out.fluids()));
                default -> {
                    IGasHandler g = GasAccess.find(be, face);
                    if (g != null) {
                        gases(g, out.gases());
                    }
                }
            }
        }
    }

    /** Jusqu'a une pile par case de sortie et par voisin. */
    private static void items(IItemHandler target, List<ItemSlot> slots) {
        for (ItemSlot s : slots) {
            if (s.isEmpty()) {
                continue;
            }
            ItemStack offer = s.extract(64, Action.SIMULATE, Automation.INTERNAL);
            if (offer.isEmpty()) {
                continue;
            }
            ItemStack rest = ItemHandlerHelper.insertItemStacked(target, offer, true);
            int n = offer.getCount() - rest.getCount();
            if (n <= 0) {
                continue;
            }
            ItemStack taken = s.extract(n, Action.EXECUTE, Automation.INTERNAL);
            ItemStack left = ItemHandlerHelper.insertItemStacked(target, taken, false);
            if (!left.isEmpty()) {
                s.insert(left, Action.EXECUTE, Automation.INTERNAL);
            }
        }
    }

    /** Jusqu'a 64 000 mB par reservoir de sortie et par voisin. */
    private static void fluids(IFluidHandler target, List<? extends FluidTank> tanks) {
        for (FluidTank t : tanks) {
            if (t.isEmpty()) {
                continue;
            }
            FluidStack offer = new FluidStack(t.getFluid(), Math.min(64_000, t.getFluidAmount()));
            int n = target.fill(offer, IFluidHandler.FluidAction.SIMULATE);
            if (n <= 0) {
                continue;
            }
            FluidStack taken = t instanceof IoTank io ? io.drainInternal(n, IFluidHandler.FluidAction.EXECUTE)
                                                     : t.drain(n, IFluidHandler.FluidAction.EXECUTE);
            int placed = target.fill(taken, IFluidHandler.FluidAction.EXECUTE);
            if (placed < taken.getAmount()) {
                FluidStack left = new FluidStack(taken, taken.getAmount() - placed);
                if (t instanceof IoTank io) {
                    io.fillInternal(left, IFluidHandler.FluidAction.EXECUTE);
                } else {
                    t.fill(left, IFluidHandler.FluidAction.EXECUTE);
                }
            }
        }
    }

    /** Jusqu'a 128 000 mB par reservoir de sortie et par voisin. */
    private static void gases(IGasHandler target, List<BasicGasTank> tanks) {
        for (BasicGasTank t : tanks) {
            if (t.isEmpty()) {
                continue;
            }
            GasStack offer = t.extract(128_000, Action.SIMULATE, Automation.INTERNAL);
            if (offer.isEmpty()) {
                continue;
            }
            GasStack rest = target.insertGas(offer, Action.SIMULATE);
            long n = offer.getAmount() - rest.getAmount();
            if (n <= 0) {
                continue;
            }
            GasStack taken = t.extract(n, Action.EXECUTE, Automation.INTERNAL);
            GasStack left = target.insertGas(taken, Action.EXECUTE);
            if (!left.isEmpty()) {
                t.insert(left, Action.EXECUTE, Automation.INTERNAL);
            }
        }
    }
}
