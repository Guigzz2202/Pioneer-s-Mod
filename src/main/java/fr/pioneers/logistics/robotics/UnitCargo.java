package fr.pioneers.logistics.robotics;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.gas.GasStack;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * Soute d'une unite : quelques piles d'objets, un reservoir unique (fluide, gaz ou donnees, un seul a la fois) et une cellule d'energie
 * a livrer (distincte de la batterie de l'unite), et une bouteille d'air comprime (litres d'air).
 */
public class UnitCargo {

    public final int stacks;
    public final int tank;
    public final long energyCapacity;
    /** Bouteille d'air : deux litres d'air par mB de reservoir. */
    public final long airCapacity;
    public final List<ItemStack> items = new ArrayList<>();
    public FluidStack fluid = FluidStack.EMPTY;
    public GasStack gas = GasStack.EMPTY;
    public DataStack data = DataStack.EMPTY;
    public long energy;
    public long air;

    public UnitCargo(int stacks, int tank) {
        this(stacks, tank, tank * 100L);
    }

    public UnitCargo(int stacks, int tank, long energyCapacity) {
        this.stacks = stacks;
        this.tank = tank;
        this.energyCapacity = energyCapacity;
        this.airCapacity = tank * 2L;
    }

    public boolean isEmpty() {
        return items.stream().allMatch(ItemStack::isEmpty) && fluid.isEmpty() && gas.isEmpty() && data.isEmpty() && energy <= 0 && air <= 0;
    }

    public boolean hasItems() {
        return items.stream().anyMatch(stack -> !stack.isEmpty());
    }

    public boolean tankEmpty() {
        return fluid.isEmpty() && gas.isEmpty() && data.isEmpty();
    }

    /** Remplissage de la soute en objets (0 a 1, par emplacements). */
    public double slotFill() {
        if (stacks <= 0) {
            return 1;
        }
        long used = items.stream().filter(stack -> !stack.isEmpty()).count();
        return used / (double) stacks;
    }

    public boolean isFull() {
        if (items.size() < stacks) {
            return false;
        }
        for (ItemStack stack : items) {
            if (stack.getCount() < stack.getMaxStackSize()) {
                return false;
            }
        }
        return true;
    }

    /** Tout ce butin tiendrait-il dans la soute ? (simulation) */
    public boolean canFit(List<ItemStack> drops) {
        if (drops.isEmpty()) {
            return true;
        }
        UnitCargo copy = new UnitCargo(stacks, tank, energyCapacity);
        for (ItemStack held : items) {
            copy.items.add(held.copy());
        }
        for (ItemStack drop : drops) {
            if (!copy.insert(drop).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Objets encore emportables (piles libres comprises). */
    public int itemRoom(ItemStack like) {
        int room = (stacks - items.size()) * like.getMaxStackSize();
        for (ItemStack held : items) {
            if (ItemStack.isSameItemSameTags(held, like)) {
                room += held.getMaxStackSize() - held.getCount();
            }
        }
        return Math.max(0, room);
    }

    /** Remplissage (0 a 1) pour le rendu et l'interface. */
    public float fill() {
        int count = 0;
        for (ItemStack stack : items) {
            count += stack.getCount();
        }
        float solid = count / (float) Math.max(1, stacks * 64);
        long liquid = Math.max(fluid.getAmount(), Math.max(gas.getAmount(), data.getAmount()));
        float power = Math.max(energy / (float) Math.max(1, energyCapacity), air / (float) Math.max(1, airCapacity));
        return Math.min(1, Math.max(Math.max(solid, power), liquid / (float) Math.max(1, tank)));
    }

    /** Ajoute une pile ; renvoie le reste. */
    public ItemStack insert(ItemStack stack) {
        ItemStack rest = stack.copy();
        for (ItemStack held : items) {
            if (rest.isEmpty()) {
                break;
            }
            if (ItemStack.isSameItemSameTags(held, rest)) {
                int move = Math.min(rest.getCount(), held.getMaxStackSize() - held.getCount());
                held.grow(move);
                rest.shrink(move);
            }
        }
        while (!rest.isEmpty() && items.size() < stacks) {
            int move = Math.min(rest.getCount(), rest.getMaxStackSize());
            items.add(rest.split(move));
        }
        return rest;
    }

    /** Premiere pile (affichage). */
    public ItemStack displayStack() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return stack;
            }
        }
        return ItemStack.EMPTY;
    }

    public void clearItems() {
        items.removeIf(ItemStack::isEmpty);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                list.add(stack.save(new CompoundTag()));
            }
        }
        tag.put("items", list);
        if (!fluid.isEmpty()) {
            tag.put("fluid", fluid.writeToNBT(new CompoundTag()));
        }
        if (!gas.isEmpty()) {
            tag.put("gas", gas.save());
        }
        if (!data.isEmpty()) {
            tag.put("data", data.write(new CompoundTag()));
        }
        tag.putLong("energy", energy);
        tag.putLong("air", air);
        return tag;
    }

    public void load(CompoundTag tag) {
        items.clear();
        for (Tag entry : tag.getList("items", Tag.TAG_COMPOUND)) {
            ItemStack stack = ItemStack.of((CompoundTag) entry);
            if (!stack.isEmpty()) {
                items.add(stack);
            }
        }
        fluid = tag.contains("fluid") ? FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid")) : FluidStack.EMPTY;
        gas = tag.contains("gas") ? GasStack.load(tag.getCompound("gas")) : GasStack.EMPTY;
        data = tag.contains("data") ? DataStack.read(tag.getCompound("data")) : DataStack.EMPTY;
        energy = tag.getLong("energy");
        air = tag.getLong("air");
    }
}
