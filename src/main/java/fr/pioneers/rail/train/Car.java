package fr.pioneers.rail.train;

import fr.pioneers.robotics.ItemRobotUnit;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraftforge.common.ForgeHooks;
import net.minecraftforge.items.ItemStackHandler;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.0 : un vehicule d'un train : sorte, tier, sens (retourne : son avant regarde l'arriere du train), cargaison (objets, eau) et chaleur
 * en reserve (combustible deja brule, en FE). 0.20.2 : reserves de fluide, de gaz, d'energie, de donnees et d'air (ids en texte).
 */
public final class Car {

    public final UUID id;
    public final CarType type;
    public final int tier;
    /** Retourne par rapport au train (son avant vers la queue). */
    public boolean flipped;
    public final Items items;
    public int water;
    /** FE restants du combustible en cours (locomotives a combustible). */
    public long heat;
    //0.20.2 : autres reserves (ids en texte : le moteur ne touche pas aux registres)
    /** Fluide (citerne, carburant du diesel) : id du fluide et quantite (mB). */
    public String fluid = "";
    public int fluidAmount;
    /** Gaz (citerne a gaz, locomotive a gaz, vapeur de la locomotive sans foyer). */
    public String gas = "";
    public int gasAmount;
    /** Energie (wagon-batterie, batterie de l'electrique et du maglev), FE. */
    public long energy;
    /** Donnees (wagon-serveur) : type et quantite (Mo). */
    public String dataType = "";
    public long data;
    /** Air comprime (L). */
    public int air;
    /** Locomotive sous tension a ce tick (pantographe leve). */
    public boolean electrified;
    /** Changement de contenu depuis le dernier tick (conditions d'inactivite). */
    public boolean touched;

    public Car(UUID id, CarType type, int tier) {
        this.id = id;
        this.type = type;
        this.tier = Math.max(0, Math.min(type.tiers - 1, tier));
        this.items = new Items(this);
    }

    public Car(CarType type, int tier) {
        this(UUID.randomUUID(), type, tier);
    }

    /** Reserve d'objets : combustible seul (vapeur), une seule sorte (tremie). */
    public static final class Items extends ItemStackHandler {

        private final Car car;

        Items(Car car) {
            super(Math.max(0, car.type.slots(car.tier)));
            this.car = car;
        }

        @Override
        public boolean isItemValid(int slot, @NotNull ItemStack stack) {
            if (car.type == CarType.STEAM || car.type == CarType.TENDER) {
                return ForgeHooks.getBurnTime(stack, RecipeType.SMELTING) > 0;
            }
            if (car.type == CarType.NUCLEAR) {
                //Case 0 : combustible neuf ; case 1 : assemblages uses (seule la locomotive les y range)
                return slot == 0 && RailFuels.nuclear(stack) > 0;
            }
            if (car.type == CarType.FLAT) {
                return stack.getItem() instanceof BlockItem;
            }
            if (car.type == CarType.ROBOT_VAN) {
                return stack.getItem() instanceof ItemRobotUnit;
            }
            if (car.type == CarType.HOPPER) {
                for (int i = 0; i < getSlots(); i++) {
                    ItemStack other = getStackInSlot(i);
                    if (!other.isEmpty() && i != slot && !ItemStack.isSameItemSameTags(other, stack)) {
                        return false;
                    }
                }
                return true;
            }
            return true;
        }

        @Override
        public int getSlotLimit(int slot) {
            return car.type == CarType.FLAT ? 1 : car.type == CarType.NUCLEAR ? 16 : super.getSlotLimit(slot);
        }

        @Override
        protected void onContentsChanged(int slot) {
            car.touched = true;
        }
    }

    // ------------------------------------------------------------------ reserves 0.20.2

    public int fluidCapacity() {
        return type.fluidCapacity(tier);
    }

    public int gasCapacity() {
        return type.gasCapacity(tier);
    }

    public long energyCapacity() {
        return type.energyCapacity(tier);
    }

    public long dataCapacity() {
        return type.dataCapacity(tier);
    }

    public int airCapacity() {
        return type.airCapacity(tier);
    }

    /** Ajoute du fluide (meme sorte ou reservoir vide) ; renvoie la quantite prise. */
    public int fillFluid(String id, int amount, boolean simulate) {
        if (type.locomotive && !RailFuels.accepts(type, id)) {
            return 0;
        }
        if (amount <= 0 || id.isEmpty() || !fluid.isEmpty() && !fluid.equals(id) && fluidAmount > 0) {
            return 0;
        }
        int n = Math.min(amount, fluidCapacity() - fluidAmount);
        if (n > 0 && !simulate) {
            fluid = id;
            fluidAmount += n;
            touched = true;
        }
        return Math.max(0, n);
    }

    public int drainFluid(int amount, boolean simulate) {
        int n = Math.min(amount, fluidAmount);
        if (n > 0 && !simulate) {
            fluidAmount -= n;
            if (fluidAmount == 0) {
                fluid = "";
            }
            touched = true;
        }
        return Math.max(0, n);
    }

    public int fillGas(String id, int amount, boolean simulate) {
        if (type.locomotive && !RailFuels.accepts(type, id)) {
            return 0;
        }
        if (amount <= 0 || id.isEmpty() || !gas.isEmpty() && !gas.equals(id) && gasAmount > 0) {
            return 0;
        }
        int n = Math.min(amount, gasCapacity() - gasAmount);
        if (n > 0 && !simulate) {
            gas = id;
            gasAmount += n;
            touched = true;
        }
        return Math.max(0, n);
    }

    public int drainGas(int amount, boolean simulate) {
        int n = Math.min(amount, gasAmount);
        if (n > 0 && !simulate) {
            gasAmount -= n;
            if (gasAmount == 0) {
                gas = "";
            }
            touched = true;
        }
        return Math.max(0, n);
    }

    public long fillData(String type, long amount, boolean simulate) {
        if (amount <= 0 || type.isEmpty() || !dataType.isEmpty() && !dataType.equals(type) && data > 0) {
            return 0;
        }
        long n = Math.min(amount, dataCapacity() - data);
        if (n > 0 && !simulate) {
            dataType = type;
            data += n;
            touched = true;
        }
        return Math.max(0, n);
    }

    public long drainData(long amount, boolean simulate) {
        long n = Math.min(amount, data);
        if (n > 0 && !simulate) {
            data -= n;
            if (data == 0) {
                dataType = "";
            }
            touched = true;
        }
        return Math.max(0, n);
    }

    /** Quantite et contenance de la reserve principale (cargaison, ou ce que brule la locomotive), pour les jauges et l'horaire. */
    public long storeAmount() {
        return switch (type.store) {
            case FLUID -> fluidAmount;
            case GAS -> gasAmount;
            case ENERGY -> energy;
            case DATA -> data;
            case AIR -> air;
            default -> 0;
        };
    }

    public long storeCapacity() {
        return switch (type.store) {
            case FLUID -> fluidCapacity();
            case GAS -> gasCapacity();
            case ENERGY -> energyCapacity();
            case DATA -> dataCapacity();
            case AIR -> airCapacity();
            default -> 0;
        };
    }

    /** Contenu de la reserve principale (id du fluide, du gaz, du type de donnees ; vide sinon). */
    public String storeContent() {
        return switch (type.store) {
            case FLUID -> fluid;
            case GAS -> gas;
            case DATA -> dataType;
            default -> "";
        };
    }

    /** Wagon dont la cargaison n'est pas en cases (citernes, batterie, serveur, air). */
    public boolean bulkStore() {
        return !type.locomotive && storeCapacity() > 0;
    }

    public int waterCapacity() {
        return type.water(tier);
    }

    /** Remplissage de la cargaison 0..1 (objets : piles pleines ; tender : eau). */
    public float fill() {
        int n = items.getSlots();
        if (type == CarType.TENDER || type == CarType.STEAM) {
            int cap = waterCapacity();
            return cap <= 0 ? 0 : water / (float) cap;
        }
        long cap = storeCapacity();
        if (cap > 0) {
            return storeAmount() / (float) cap;
        }
        if (n == 0) {
            return 0;
        }
        float sum = 0;
        for (int i = 0; i < n; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (!s.isEmpty()) {
                sum += s.getCount() / (float) Math.min(s.getMaxStackSize(), items.getSlotLimit(i));
            }
        }
        return sum / n;
    }

    public boolean isFull() {
        if (bulkStore()) {
            return storeAmount() >= storeCapacity();
        }
        int n = items.getSlots();
        if (n == 0 || type.locomotive || type == CarType.TENDER) {
            return true;
        }
        for (int i = 0; i < n; i++) {
            ItemStack s = items.getStackInSlot(i);
            if (s.isEmpty() || s.getCount() < Math.min(s.getMaxStackSize(), items.getSlotLimit(i))) {
                return false;
            }
        }
        return true;
    }

    public boolean isEmpty() {
        if (bulkStore()) {
            return storeAmount() <= 0;
        }
        if (type.locomotive || type == CarType.TENDER) {
            return true;
        }
        for (int i = 0; i < items.getSlots(); i++) {
            if (!items.getStackInSlot(i).isEmpty()) {
                return false;
            }
        }
        return true;
    }

    /** Masse actuelle (a vide + charge). */
    public float mass() {
        return type.mass() + type.loadMass(tier) * fill();
    }

    /** Wagon de marchandises (compte pour la charge tiree). */
    public boolean isCargo() {
        return !type.locomotive;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putUUID("id", id);
        tag.putByte("type", (byte) type.ordinal());
        tag.putByte("tier", (byte) tier);
        tag.putBoolean("flip", flipped);
        if (items.getSlots() > 0) {
            tag.put("items", items.serializeNBT());
        }
        tag.putInt("water", water);
        tag.putLong("heat", heat);
        if (fluidAmount > 0) {
            tag.putString("fluid", fluid);
            tag.putInt("fluidAmount", fluidAmount);
        }
        if (gasAmount > 0) {
            tag.putString("gas", gas);
            tag.putInt("gasAmount", gasAmount);
        }
        if (energy > 0) {
            tag.putLong("energy", energy);
        }
        if (data > 0) {
            tag.putString("dataType", dataType);
            tag.putLong("data", data);
        }
        if (air > 0) {
            tag.putInt("air", air);
        }
        return tag;
    }

    public static Car load(CompoundTag tag) {
        Car car = new Car(tag.hasUUID("id") ? tag.getUUID("id") : UUID.randomUUID(), CarType.byId(tag.getByte("type")), tag.getByte("tier"));
        car.flipped = tag.getBoolean("flip");
        if (tag.contains("items")) {
            CompoundTag items = tag.getCompound("items");
            items.putInt("Size", car.items.getSlots());
            car.items.deserializeNBT(items);
        }
        car.loadStores(tag);
        car.touched = false;
        return car;
    }

    /** Reserves hors cases (eau, chaleur, fluide, gaz, energie, donnees, air) : aussi gardees dans l'objet d'un vehicule ramasse. */
    public CompoundTag saveStores() {
        CompoundTag tag = save();
        tag.remove("id");
        tag.remove("items");
        tag.remove("type");
        tag.remove("tier");
        tag.remove("flip");
        return tag;
    }

    /** Le vehicule a-t-il une reserve hors cases non vide ? */
    public boolean hasStores() {
        return water > 0 || heat > 0 || fluidAmount > 0 || gasAmount > 0 || energy > 0 || data > 0 || air > 0;
    }

    public void loadStores(CompoundTag tag) {
        Car car = this;
        car.water = Math.min(car.waterCapacity(), tag.getInt("water"));
        car.heat = tag.getLong("heat");
        car.fluidAmount = Math.min(car.fluidCapacity(), tag.getInt("fluidAmount"));
        car.fluid = car.fluidAmount > 0 ? tag.getString("fluid") : "";
        car.gasAmount = Math.min(car.gasCapacity(), tag.getInt("gasAmount"));
        car.gas = car.gasAmount > 0 ? tag.getString("gas") : "";
        car.energy = Math.min(car.energyCapacity(), tag.getLong("energy"));
        car.data = Math.min(car.dataCapacity(), tag.getLong("data"));
        car.dataType = car.data > 0 ? tag.getString("dataType") : "";
        car.air = Math.min(car.airCapacity(), tag.getInt("air"));
    }
}
