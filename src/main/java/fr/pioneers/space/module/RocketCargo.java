package fr.pioneers.space.module;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Soutes d'une fusee : leur capacite vient des modules embarques.
 * <ul>
 *     <li>soute a objets : 9 piles par module ;</li>
 *     <li>soute a fluides : 16 000 mB par module ;</li>
 *     <li>soute a gaz : 16 000 mB par module ;</li>
 *     <li>soute a donnees : 50 000 Mo par module.</li>
 * </ul>
 * La cargaison voyage avec la fusee : dans l'entite, dans l'objet (fusee repliee) et dans les vols logistiques.
 */
public class RocketCargo {

    public static final int MAX_ITEM_SLOTS = 7 * RocketModule.ITEM_SLOTS;
    private static final String NBT_ITEMS = "items", NBT_FLUID = "fluid", NBT_GAS = "gas", NBT_DATA = "data";

    private final NonNullList<ItemStack> items = NonNullList.withSize(MAX_ITEM_SLOTS, ItemStack.EMPTY);
    private FluidStack fluid = FluidStack.EMPTY;
    private GasStack gas = GasStack.EMPTY;
    private DataStack data = DataStack.EMPTY;

    // ------------------------------------------------------------------
    // Capacites selon les modules
    // ------------------------------------------------------------------

    public static int itemSlots(List<ItemStack> modules) {
        return RocketModule.count(modules, RocketModule.CARGO_ITEMS) * RocketModule.ITEM_SLOTS;
    }

    public static long fluidCapacity(List<ItemStack> modules) {
        return RocketModule.count(modules, RocketModule.CARGO_FLUIDS) * RocketModule.FLUID_CAPACITY;
    }

    public static long gasCapacity(List<ItemStack> modules) {
        return RocketModule.count(modules, RocketModule.CARGO_GAS) * RocketModule.GAS_CAPACITY;
    }

    public static long dataCapacity(List<ItemStack> modules) {
        return RocketModule.count(modules, RocketModule.CARGO_DATA) * RocketModule.DATA_CAPACITY;
    }

    /** La cargaison tient-elle dans les soutes de ces modules ? (pour autoriser le retrait d'un module) */
    public boolean fits(List<ItemStack> modules) {
        int slots = itemSlots(modules);
        for (int i = slots; i < MAX_ITEM_SLOTS; i++) {
            if (!items.get(i).isEmpty()) {
                return false;
            }
        }
        return fluid.getAmount() <= fluidCapacity(modules) && gas.getAmount() <= gasCapacity(modules) && data.getAmount() <= dataCapacity(modules);
    }

    /** Remplissage moyen des soutes presentes (0..1). */
    public double fillRatio(List<ItemStack> modules) {
        double total = 0;
        int kinds = 0;
        int slots = itemSlots(modules);
        if (slots > 0) {
            double used = 0;
            for (int i = 0; i < slots; i++) {
                ItemStack stack = items.get(i);
                if (!stack.isEmpty()) {
                    used += stack.getCount() / (double) stack.getMaxStackSize();
                }
            }
            total += Math.min(1, used / slots);
            kinds++;
        }
        long fluidCap = fluidCapacity(modules), gasCap = gasCapacity(modules), dataCap = dataCapacity(modules);
        if (fluidCap > 0) {
            total += Math.min(1, fluid.getAmount() / (double) fluidCap);
            kinds++;
        }
        if (gasCap > 0) {
            total += Math.min(1, gas.getAmount() / (double) gasCap);
            kinds++;
        }
        if (dataCap > 0) {
            total += Math.min(1, data.getAmount() / (double) dataCap);
            kinds++;
        }
        return kinds == 0 ? 0 : total / kinds;
    }

    // ------------------------------------------------------------------
    // Contenu
    // ------------------------------------------------------------------

    public NonNullList<ItemStack> getItems() {
        return items;
    }

    /** Retire un objet de la soute (le premier qui correspond) ; faux s'il n'y en a pas. */
    public boolean removeItem(Predicate<ItemStack> filter) {
        for (ItemStack stack : items) {
            if (!stack.isEmpty() && filter.test(stack)) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }

    public FluidStack getFluid() {
        return fluid;
    }

    public void setFluid(FluidStack fluid) {
        this.fluid = fluid.isEmpty() ? FluidStack.EMPTY : fluid;
    }

    public GasStack getGas() {
        return gas;
    }

    public void setGas(GasStack gas) {
        this.gas = gas.isEmpty() ? GasStack.EMPTY : gas;
    }

    public DataStack getData() {
        return data;
    }

    public void setData(DataStack data) {
        this.data = data.isEmpty() ? DataStack.EMPTY : data;
    }

    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return fluid.isEmpty() && gas.isEmpty() && data.isEmpty();
    }

    public void clear() {
        items.replaceAll(stack -> ItemStack.EMPTY);
        fluid = FluidStack.EMPTY;
        gas = GasStack.EMPTY;
        data = DataStack.EMPTY;
    }

    /** Vide les objets (pour les rendre au monde ou a un inventaire). */
    public void drainItems(Consumer<ItemStack> sink) {
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                sink.accept(items.get(i));
                items.set(i, ItemStack.EMPTY);
            }
        }
    }

    public RocketCargo copy() {
        RocketCargo copy = new RocketCargo();
        copy.load(save());
        return copy;
    }

    // ------------------------------------------------------------------
    // Sauvegarde (meme format dans l'entite, l'objet et les vols logistiques)
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (int i = 0; i < items.size(); i++) {
            if (!items.get(i).isEmpty()) {
                CompoundTag entry = items.get(i).save(new CompoundTag());
                entry.putByte("Slot", (byte) i);
                list.add(entry);
            }
        }
        if (!list.isEmpty()) {
            tag.put(NBT_ITEMS, list);
        }
        if (!fluid.isEmpty()) {
            tag.put(NBT_FLUID, fluid.writeToNBT(new CompoundTag()));
        }
        if (!gas.isEmpty()) {
            tag.put(NBT_GAS, gas.save());
        }
        if (!data.isEmpty()) {
            tag.put(NBT_DATA, data.write(new CompoundTag()));
        }
        return tag;
    }

    public void load(CompoundTag tag) {
        clear();
        ListTag list = tag.getList(NBT_ITEMS, Tag.TAG_COMPOUND);
        int next = 0;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag entry = list.getCompound(i);
            int slot = entry.contains("Slot") ? entry.getByte("Slot") & 255 : next;
            if (slot < MAX_ITEM_SLOTS) {
                items.set(slot, ItemStack.of(entry));
                next = slot + 1;
            }
        }
        fluid = tag.contains(NBT_FLUID) ? FluidStack.loadFluidStackFromNBT(tag.getCompound(NBT_FLUID)) : FluidStack.EMPTY;
        gas = tag.contains(NBT_GAS) ? GasStack.load(tag.getCompound(NBT_GAS)) : GasStack.EMPTY;
        data = tag.contains(NBT_DATA) ? DataStack.read(tag.getCompound(NBT_DATA)) : DataStack.EMPTY;
    }

    public static RocketCargo read(CompoundTag tag) {
        RocketCargo cargo = new RocketCargo();
        cargo.load(tag);
        return cargo;
    }
}
