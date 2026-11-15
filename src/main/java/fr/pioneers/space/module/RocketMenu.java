package fr.pioneers.space.module;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.core.registries.PiMenus;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.space.RocketTier;
import java.util.ArrayList;
import java.util.List;
import java.util.function.IntSupplier;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistry;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Interface d'une fusee (accroupi + clic droit) : emplacements de modules, soute a objets (9 cases par module de soute,
 * trois rangees visibles avec defilement) et jauges des soutes a fluide, gaz et donnees.
 * <p>
 * Un module ne peut pas etre retire si sa soute contient encore quelque chose.
 */
public class RocketMenu extends AbstractContainerMenu {

    public static final int MODULE_X = 44, MODULE_Y = 20, CARGO_X = 8, CARGO_Y = 50, VISIBLE_ROWS = 3, INVENTORY_Y = 122;
    public static final int BUTTON_SCROLL_UP = 0, BUTTON_SCROLL_DOWN = 1;
    /** Valeurs synchronisees (chacune sur deux emplacements de 16 bits). */
    public static final int FUEL = 0, FUEL_CAPACITY = 1, FLUID = 2, FLUID_ID = 3, GAS = 4, GAS_ID = 5, DATA = 6, DATA_ID = 7, SCROLL = 8, VALUES = 9;

    private final RocketTier tier;
    @Nullable
    private final RocketEntity rocket;
    private final Container modules;
    private final NonNullList<ItemStack> cargoItems;
    private final ScrolledContainer cargoView;
    private final PackedData data;
    private int scroll;

    /** Cote client. */
    public RocketMenu(int id, Inventory inventory, FriendlyByteBuf buffer) {
        this(id, inventory, readTier(buffer));
    }

    private static RocketTier readTier(FriendlyByteBuf buffer) {
        buffer.readVarInt();
        return RocketTier.byIndex(buffer.readVarInt());
    }

    private RocketMenu(int id, Inventory inventory, RocketTier tier) {
        this(id, inventory, tier, null, new ListContainer(NonNullList.withSize(RocketModule.MAX_SLOTS, ItemStack.EMPTY)),
              NonNullList.withSize(RocketCargo.MAX_ITEM_SLOTS, ItemStack.EMPTY), new PackedData(VALUES));
    }

    /** Cote serveur. */
    public RocketMenu(int id, Inventory inventory, RocketEntity rocket) {
        this(id, inventory, rocket.getTier(), rocket, new ListContainer(rocket.getModuleSlots()), rocket.getCargo().getItems(), null);
    }

    private RocketMenu(int id, Inventory inventory, RocketTier tier, @Nullable RocketEntity rocket, Container modules, NonNullList<ItemStack> cargoItems,
          @Nullable PackedData clientData) {
        super(PiMenus.ROCKET.get(), id);
        this.tier = tier;
        this.rocket = rocket;
        this.modules = modules;
        this.cargoItems = cargoItems;
        this.cargoView = new ScrolledContainer(cargoItems, () -> scroll);
        this.data = clientData != null ? clientData : serverData(rocket);
        int slots = RocketModule.slots(tier);
        for (int i = 0; i < slots; i++) {
            addSlot(new ModuleSlot(modules, i, MODULE_X + i * 18, MODULE_Y));
        }
        for (int i = 0; i < VISIBLE_ROWS * 9; i++) {
            addSlot(new CargoSlot(cargoView, i, CARGO_X + (i % 9) * 18, CARGO_Y + (i / 9) * 18));
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, INVENTORY_Y + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, INVENTORY_Y + 58));
        }
        addDataSlots(data);
    }

    /** Valeurs lues sur la fusee (cote serveur). */
    private PackedData serverData(RocketEntity rocket) {
        PackedData packed = new PackedData(VALUES);
        RocketCargo cargo = rocket.getCargo();
        packed.bind(FUEL, rocket::getFuel);
        packed.bind(FUEL_CAPACITY, rocket::getFuelCapacity);
        packed.bind(FLUID, () -> cargo.getFluid().getAmount());
        packed.bind(FLUID_ID, () -> cargo.getFluid().isEmpty() ? 0 : BuiltInRegistries.FLUID.getId(cargo.getFluid().getFluid()) + 1);
        packed.bind(GAS, () -> (int) cargo.getGas().getAmount());
        packed.bind(GAS_ID, () -> cargo.getGas().isEmpty() ? 0 : gasRegistry().getID(cargo.getGas().getGas()) + 1);
        packed.bind(DATA, () -> (int) cargo.getData().getAmount());
        packed.bind(DATA_ID, () -> cargo.getData().isEmpty() ? 0 : dataRegistry().getID(cargo.getData().getType()) + 1);
        packed.bind(SCROLL, () -> scroll);
        return packed;
    }

    private static ForgeRegistry<Gas> gasRegistry() {
        return (ForgeRegistry<Gas>) PioneersAPI.gasRegistry();
    }

    private static ForgeRegistry<DataType> dataRegistry() {
        return (ForgeRegistry<DataType>) PioneersAPI.dataTypeRegistry();
    }

    // ------------------------------------------------------------------
    // Lecture (interface)
    // ------------------------------------------------------------------

    public RocketTier getTier() {
        return tier;
    }

    public int getValue(int index) {
        return data.value(index);
    }

    @Nullable
    public Fluid getFluid() {
        int id = getValue(FLUID_ID);
        return id <= 0 ? null : BuiltInRegistries.FLUID.byId(id - 1);
    }

    @Nullable
    public Gas getGas() {
        int id = getValue(GAS_ID);
        return id <= 0 ? null : gasRegistry().getValue(id - 1);
    }

    @Nullable
    public DataType getDataType() {
        int id = getValue(DATA_ID);
        return id <= 0 ? null : dataRegistry().getValue(id - 1);
    }

    /** Modules installes (vus par ce menu). */
    public List<ItemStack> getModules() {
        List<ItemStack> list = new ArrayList<>();
        int slots = RocketModule.slots(tier);
        for (int i = 0; i < slots; i++) {
            if (!modules.getItem(i).isEmpty()) {
                list.add(modules.getItem(i));
            }
        }
        return list;
    }

    public int getScroll() {
        return scroll;
    }

    public int maxScroll() {
        int rows = (RocketCargo.itemSlots(getModules()) + 8) / 9;
        return Math.max(0, rows - VISIBLE_ROWS);
    }

    /** Un module peut-il etre retire sans perdre de cargaison ? */
    private boolean canRemove(int index) {
        List<ItemStack> remaining = new ArrayList<>();
        int slots = RocketModule.slots(tier);
        for (int i = 0; i < slots; i++) {
            if (i != index && !modules.getItem(i).isEmpty()) {
                remaining.add(modules.getItem(i));
            }
        }
        int itemSlots = RocketCargo.itemSlots(remaining);
        for (int i = itemSlots; i < cargoItems.size(); i++) {
            if (!cargoItems.get(i).isEmpty()) {
                return false;
            }
        }
        return getValue(FLUID) <= RocketCargo.fluidCapacity(remaining) && getValue(GAS) <= RocketCargo.gasCapacity(remaining)
               && getValue(DATA) <= RocketCargo.dataCapacity(remaining);
    }

    // ------------------------------------------------------------------
    // Interactions
    // ------------------------------------------------------------------

    @Override
    public boolean clickMenuButton(@NotNull Player player, int id) {
        int next = id == BUTTON_SCROLL_UP ? scroll - 1 : id == BUTTON_SCROLL_DOWN ? scroll + 1 : scroll;
        next = Math.max(0, Math.min(maxScroll(), next));
        if (next != scroll) {
            scroll = next;
            return true;
        }
        return false;
    }

    /** Cote client : defilement immediat (le serveur suit via {@link #clickMenuButton}). */
    public void setClientScroll(int value) {
        scroll = Math.max(0, Math.min(maxScroll(), value));
    }

    @NotNull
    @Override
    public ItemStack quickMoveStack(@NotNull Player player, int index) {
        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack copy = stack.copy();
        int moduleCount = RocketModule.slots(tier);
        int cargoEnd = moduleCount + VISIBLE_ROWS * 9;
        if (index < cargoEnd) {
            if (!moveItemStackTo(stack, cargoEnd, slots.size(), true)) {
                return ItemStack.EMPTY;
            }
        } else if (RocketModule.of(stack) != null) {
            if (!moveItemStackTo(stack, 0, moduleCount, false) && !moveItemStackTo(stack, moduleCount, cargoEnd, false)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(stack, moduleCount, cargoEnd, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return rocket == null || rocket.canPlayerConfigure(player);
    }

    // ------------------------------------------------------------------
    // Emplacements
    // ------------------------------------------------------------------

    private class ModuleSlot extends Slot {

        ModuleSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return RocketModule.of(stack) != null;
        }

        @Override
        public int getMaxStackSize() {
            return 1;
        }

        @Override
        public boolean mayPickup(@NotNull Player player) {
            return canRemove(getContainerSlot());
        }
    }

    private class CargoSlot extends Slot {

        CargoSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean isActive() {
            return scroll * 9 + getContainerSlot() < RocketCargo.itemSlots(getModules());
        }

        @Override
        public boolean mayPlace(@NotNull ItemStack stack) {
            return isActive();
        }
    }

    /** Liste d'objets vue comme un inventaire. */
    private static class ListContainer implements Container {

        private final NonNullList<ItemStack> items;

        ListContainer(NonNullList<ItemStack> items) {
            this.items = items;
        }

        @Override
        public int getContainerSize() {
            return items.size();
        }

        @Override
        public boolean isEmpty() {
            return items.stream().allMatch(ItemStack::isEmpty);
        }

        @NotNull
        @Override
        public ItemStack getItem(int slot) {
            return slot >= 0 && slot < items.size() ? items.get(slot) : ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack removeItem(int slot, int amount) {
            return ContainerHelper.removeItem(items, slot, amount);
        }

        @NotNull
        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return ContainerHelper.takeItem(items, slot);
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
            if (slot >= 0 && slot < items.size()) {
                items.set(slot, stack);
            }
        }

        @Override
        public void setChanged() {
        }

        @Override
        public boolean stillValid(@NotNull Player player) {
            return true;
        }

        @Override
        public void clearContent() {
            items.replaceAll(stack -> ItemStack.EMPTY);
        }
    }

    /** Fenetre de trois rangees sur la soute a objets, decalee par le defilement. */
    private static class ScrolledContainer extends ListContainer {

        private final NonNullList<ItemStack> base;
        private final IntSupplier scroll;

        ScrolledContainer(NonNullList<ItemStack> base, IntSupplier scroll) {
            super(base);
            this.base = base;
            this.scroll = scroll;
        }

        private int map(int slot) {
            return scroll.getAsInt() * 9 + slot;
        }

        @Override
        public int getContainerSize() {
            return VISIBLE_ROWS * 9;
        }

        @NotNull
        @Override
        public ItemStack getItem(int slot) {
            int index = map(slot);
            return index < base.size() ? base.get(index) : ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack removeItem(int slot, int amount) {
            return map(slot) < base.size() ? ContainerHelper.removeItem(base, map(slot), amount) : ItemStack.EMPTY;
        }

        @NotNull
        @Override
        public ItemStack removeItemNoUpdate(int slot) {
            return map(slot) < base.size() ? ContainerHelper.takeItem(base, map(slot)) : ItemStack.EMPTY;
        }

        @Override
        public void setItem(int slot, @NotNull ItemStack stack) {
            if (map(slot) < base.size()) {
                base.set(map(slot), stack);
            }
        }
    }

    /**
     * Valeurs entieres synchronisees sur deux emplacements de 16 bits chacune (les emplacements de donnees de Minecraft
     * sont transmis sur 16 bits).
     */
    public static class PackedData implements ContainerData {

        private final int[] values;
        private final IntSupplier[] sources;

        PackedData(int count) {
            values = new int[count * 2];
            sources = new IntSupplier[count];
        }

        void bind(int index, IntSupplier source) {
            sources[index] = source;
        }

        public int value(int index) {
            if (sources[index] != null) {
                return sources[index].getAsInt();
            }
            return (values[index * 2] & 0xFFFF) | (values[index * 2 + 1] & 0xFFFF) << 16;
        }

        @Override
        public int get(int slot) {
            int index = slot / 2;
            int value = sources[index] != null ? sources[index].getAsInt() : value(index);
            return slot % 2 == 0 ? value & 0xFFFF : value >>> 16 & 0xFFFF;
        }

        @Override
        public void set(int slot, int value) {
            values[slot] = value;
        }

        @Override
        public int getCount() {
            return values.length;
        }
    }
}
