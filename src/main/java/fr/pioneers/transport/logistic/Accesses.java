package fr.pioneers.transport.logistic;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.IDataHandler;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.api.gas.IGasHandler;
import fr.pioneers.core.Action;
import fr.pioneers.industry.IndustryContent;
import java.util.Objects;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.energy.IEnergyStorage;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.capability.IFluidHandler;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 : acces Minecraft du moteur logistique (capacites Forge et du socle). Contraintes physiques : temperature des fluides (Forge),
 * corrosifs et cryogeniques ({@link #needsLining}, repris de l'ancien {@code CableNetwork}). Ne jamais charger cette classe dans un banc hors jeu
 * (registres).
 */
public final class Accesses implements LogisticNetwork.Toolkit {

    public static final Accesses INSTANCE = new Accesses();

    private Accesses() {
    }

    /** Corrosif ou cryogenique (sous {@code IndustryContent.CRYOGENIC_BELOW}) : gaine PTFE requise (MKII et mieux). */
    public static boolean needsLining(Object resource) {
        if (resource instanceof FluidStack fluid && !fluid.isEmpty()) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(fluid.getFluid());
            return id != null && IndustryContent.CORROSIVE_FLUIDS.contains(id.toString())
                   || fluid.getFluid().getFluidType().getTemperature(fluid) < IndustryContent.CRYOGENIC_BELOW;
        }
        if (resource instanceof GasStack gas && !gas.isEmpty()) {
            ResourceLocation id = gas.getGas().getRegistryName();
            return id != null && IndustryContent.CORROSIVE_GASES.contains(id.toString());
        }
        return false;
    }

    /** Fluide de plus de 1 200 degres C (metal fondu) : niveau MKI ou mieux (le bronze s'arrete a sa propre fonte). */
    public static boolean molten(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().getFluidType().getTemperature(fluid) > CableLevel.BRONZE.maxTemperature;
    }

    /** Fluide trop chaud pour un cable en bois (plus de 400 K). */
    public static boolean tooHot(FluidStack fluid) {
        return !fluid.isEmpty() && fluid.getFluid().getFluidType().getTemperature(fluid) > CableLevel.WOODEN.maxTemperature;
    }

    @Override
    public Access<?, ?> access(ChannelKind kind) {
        return switch (kind) {
            case ITEM -> ITEMS;
            case FLUID -> FLUIDS;
            case GAS -> GASES;
            case DATA -> DATA;
            default -> throw new IllegalArgumentException("pas d'acces a ressource pour " + kind);
        };
    }

    @Override
    public EnergyAccess<?> energy() {
        return ENERGY;
    }

    @Nullable
    private static ResourceLocation rl(String key) {
        return ResourceLocation.tryParse(key);
    }

    public static final Access<IItemHandler, ItemStack> ITEMS = new Access<>() {
        @Override
        public ChannelKind kind() {
            return ChannelKind.ITEM;
        }

        @Override
        public int slots(IItemHandler handler) {
            return handler.getSlots();
        }

        @Override
        public ItemStack peek(IItemHandler handler, int slot) {
            return handler.getStackInSlot(slot);
        }

        @Override
        public long capacity(IItemHandler handler, int slot) {
            //1.12.3 (lot F3) : une case de buffer garde jusqu'a 8 piles (creatif : 64) ; borne pour les gestionnaires qui annoncent l'infini
            return Math.min(handler.getSlotLimit(slot), 64 * 64);
        }

        @Override
        public ItemStack extract(IItemHandler handler, int slot, ItemStack like, long max, boolean simulate) {
            return handler.extractItem(slot, (int) Math.min(like.getMaxStackSize(), max), simulate);
        }

        @Override
        public long insert(IItemHandler handler, ItemStack resource, boolean simulate) {
            ItemStack rest = ItemHandlerHelper.insertItemStacked(handler, resource.copy(), simulate);
            return resource.getCount() - rest.getCount();
        }

        @Override
        public long amount(ItemStack resource) {
            return resource.getCount();
        }

        @Override
        public ItemStack withAmount(ItemStack resource, long amount) {
            return amount <= 0 ? ItemStack.EMPTY : resource.copyWithCount((int) Math.min(Integer.MAX_VALUE, amount));
        }

        @Override
        public boolean same(ItemStack a, ItemStack b) {
            return ItemStack.isSameItemSameTags(a, b);
        }

        @Override
        public boolean isEmpty(ItemStack resource) {
            return resource.isEmpty();
        }

        @Override
        public Object filterKey(ItemStack resource) {
            return resource.getItem();
        }

        @Override
        public boolean matches(Filter.Entry entry, ItemStack resource) {
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(resource.getItem());
            if (entry.isMod()) {
                return id != null && id.getNamespace().equals(entry.bare());
            }
            if (entry.isTag()) {
                ResourceLocation tag = rl(entry.bare());
                return tag != null && resource.is(TagKey.create(Registries.ITEM, tag));
            }
            return id != null && id.toString().equals(entry.key()) && (entry.nbt() == null || Objects.equals(entry.nbt(), resource.getTag()));
        }

        @Override
        public CompoundTag snapshot(ItemStack resource) {
            return resource.isEmpty() ? new CompoundTag() : resource.copyWithCount(1).save(new CompoundTag());
        }
    };

    public static final Access<IFluidHandler, FluidStack> FLUIDS = new Access<>() {
        @Override
        public ChannelKind kind() {
            return ChannelKind.FLUID;
        }

        @Override
        public int slots(IFluidHandler handler) {
            return handler.getTanks();
        }

        @Override
        public FluidStack peek(IFluidHandler handler, int slot) {
            return handler.getFluidInTank(slot);
        }

        @Override
        public long capacity(IFluidHandler handler, int slot) {
            return handler.getTankCapacity(slot);
        }

        @Override
        public FluidStack extract(IFluidHandler handler, int slot, FluidStack like, long max, boolean simulate) {
            return handler.drain(new FluidStack(like, (int) Math.min(Integer.MAX_VALUE, max)),
                  simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public long insert(IFluidHandler handler, FluidStack resource, boolean simulate) {
            return handler.fill(resource.copy(), simulate ? IFluidHandler.FluidAction.SIMULATE : IFluidHandler.FluidAction.EXECUTE);
        }

        @Override
        public long amount(FluidStack resource) {
            return resource.getAmount();
        }

        @Override
        public FluidStack withAmount(FluidStack resource, long amount) {
            return amount <= 0 ? FluidStack.EMPTY : new FluidStack(resource, (int) Math.min(Integer.MAX_VALUE, amount));
        }

        @Override
        public boolean same(FluidStack a, FluidStack b) {
            return a.isFluidEqual(b);
        }

        @Override
        public boolean isEmpty(FluidStack resource) {
            return resource.isEmpty();
        }

        @Override
        public Object filterKey(FluidStack resource) {
            return resource.getFluid();
        }

        @Override
        public boolean matches(Filter.Entry entry, FluidStack resource) {
            ResourceLocation id = ForgeRegistries.FLUIDS.getKey(resource.getFluid());
            if (entry.isMod()) {
                return id != null && id.getNamespace().equals(entry.bare());
            }
            if (entry.isTag()) {
                ResourceLocation tag = rl(entry.bare());
                return tag != null && resource.getFluid().is(TagKey.create(Registries.FLUID, tag));
            }
            return id != null && id.toString().equals(entry.key()) && (entry.nbt() == null || Objects.equals(entry.nbt(), resource.getTag()));
        }

        @Override
        public double temperature(FluidStack resource) {
            return resource.getFluid().getFluidType().getTemperature(resource);
        }

        @Override
        public boolean lining(FluidStack resource) {
            return needsLining(resource);
        }

        @Override
        public CompoundTag snapshot(FluidStack resource) {
            return resource.isEmpty() ? new CompoundTag() : new FluidStack(resource, 1000).writeToNBT(new CompoundTag());
        }
    };

    public static final Access<IGasHandler, GasStack> GASES = new Access<>() {
        @Override
        public ChannelKind kind() {
            return ChannelKind.GAS;
        }

        @Override
        public int slots(IGasHandler handler) {
            return handler.getGasTanks();
        }

        @Override
        public GasStack peek(IGasHandler handler, int slot) {
            return handler.getGasInTank(slot);
        }

        @Override
        public long capacity(IGasHandler handler, int slot) {
            return handler.getGasTankCapacity(slot);
        }

        @Override
        public GasStack extract(IGasHandler handler, int slot, GasStack like, long max, boolean simulate) {
            return handler.extractGas(like.copyWithAmount(max), Action.get(!simulate));
        }

        @Override
        public long insert(IGasHandler handler, GasStack resource, boolean simulate) {
            return resource.getAmount() - handler.insertGas(resource.copy(), Action.get(!simulate)).getAmount();
        }

        @Override
        public long amount(GasStack resource) {
            return resource.getAmount();
        }

        @Override
        public GasStack withAmount(GasStack resource, long amount) {
            return resource.copyWithAmount(amount);
        }

        @Override
        public boolean same(GasStack a, GasStack b) {
            return a.isSameGas(b);
        }

        @Override
        public boolean isEmpty(GasStack resource) {
            return resource.isEmpty();
        }

        @Override
        public Object filterKey(GasStack resource) {
            return resource.getGas().getRegistryName();
        }

        @Override
        public boolean matches(Filter.Entry entry, GasStack resource) {
            ResourceLocation id = resource.getGas().getRegistryName();
            if (id == null || entry.isTag()) {
                return false;
            }
            return entry.isMod() ? id.getNamespace().equals(entry.bare()) : id.toString().equals(entry.key());
        }

        @Override
        public boolean lining(GasStack resource) {
            return needsLining(resource);
        }

        @Override
        public CompoundTag snapshot(GasStack resource) {
            return resource.isEmpty() ? new CompoundTag() : resource.copyWithAmount(1).save();
        }
    };

    public static final Access<IDataHandler, DataStack> DATA = new Access<>() {
        @Override
        public ChannelKind kind() {
            return ChannelKind.DATA;
        }

        @Override
        public int slots(IDataHandler handler) {
            return handler.getDataTanks();
        }

        @Override
        public DataStack peek(IDataHandler handler, int slot) {
            return handler.getDataInTank(slot);
        }

        @Override
        public long capacity(IDataHandler handler, int slot) {
            return handler.getDataTankCapacity(slot);
        }

        @Override
        public DataStack extract(IDataHandler handler, int slot, DataStack like, long max, boolean simulate) {
            return handler.extractData(like.copyWithAmount(max), Action.get(!simulate));
        }

        @Override
        public long insert(IDataHandler handler, DataStack resource, boolean simulate) {
            return resource.getAmount() - handler.insertData(resource.copy(), Action.get(!simulate)).getAmount();
        }

        @Override
        public long amount(DataStack resource) {
            return resource.getAmount();
        }

        @Override
        public DataStack withAmount(DataStack resource, long amount) {
            return resource.copyWithAmount(amount);
        }

        @Override
        public boolean same(DataStack a, DataStack b) {
            return a.isTypeEqual(b);
        }

        @Override
        public boolean isEmpty(DataStack resource) {
            return resource.isEmpty();
        }

        @Override
        public Object filterKey(DataStack resource) {
            return resource.getType();
        }

        @Override
        public boolean matches(Filter.Entry entry, DataStack resource) {
            ResourceLocation id = resource.getType().getRegistryName();
            if (id == null || entry.isTag()) {
                return false;
            }
            return entry.isMod() ? id.getNamespace().equals(entry.bare()) : id.toString().equals(entry.key());
        }

        @Override
        public CompoundTag snapshot(DataStack resource) {
            return resource.isEmpty() ? new CompoundTag() : resource.copyWithAmount(1).write(new CompoundTag());
        }
    };

    public static final EnergyAccess<IEnergyStorage> ENERGY = new EnergyAccess<>() {
        @Override
        public long extract(IEnergyStorage handler, long max, boolean simulate) {
            return handler.canExtract() ? handler.extractEnergy((int) Math.min(Integer.MAX_VALUE, max), simulate) : 0;
        }

        @Override
        public long receive(IEnergyStorage handler, long max, boolean simulate) {
            return handler.canReceive() ? handler.receiveEnergy((int) Math.min(Integer.MAX_VALUE, max), simulate) : 0;
        }

        @Override
        public double fill(IEnergyStorage handler) {
            int max = handler.getMaxEnergyStored();
            return max <= 0 ? 0 : (double) handler.getEnergyStored() / max;
        }
    };
}
