package fr.pioneers.logistics.robotics;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataStack;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import java.util.Objects;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

/**
 * Une ressource precise (ou « n'importe laquelle » de sa famille) : un objet (avec ses donnees), un fluide, un gaz, l'energie ou un
 * type de donnees. Immuable ; sert de cle (stocks du reseau, demandes).
 */
public final class LogisticResource {

    public final Resource type;
    private final ItemStack item;
    private final FluidStack fluid;
    @Nullable
    private final Gas gas;
    @Nullable
    private final DataType data;
    /** Tag d'objets (« tous les rondins ») ; la ressource est alors generique. */
    @Nullable
    private final TagKey<Item> tag;

    private LogisticResource(Resource type, ItemStack item, FluidStack fluid, @Nullable Gas gas, @Nullable DataType data) {
        this(type, item, fluid, gas, data, null);
    }

    private LogisticResource(Resource type, ItemStack item, FluidStack fluid, @Nullable Gas gas, @Nullable DataType data, @Nullable TagKey<Item> tag) {
        this.type = type;
        this.item = item;
        this.fluid = fluid;
        this.gas = gas;
        this.data = data;
        this.tag = tag;
    }

    /** N'importe quelle ressource de la famille. */
    public static LogisticResource any(Resource type) {
        return new LogisticResource(type, ItemStack.EMPTY, FluidStack.EMPTY, null, null);
    }

    /** Tous les objets d'un tag. */
    public static LogisticResource tag(TagKey<Item> tag) {
        return new LogisticResource(Resource.ITEMS, ItemStack.EMPTY, FluidStack.EMPTY, null, null, tag);
    }

    public static LogisticResource of(ItemStack stack) {
        return stack.isEmpty() ? any(Resource.ITEMS) : new LogisticResource(Resource.ITEMS, stack.copyWithCount(1), FluidStack.EMPTY, null, null);
    }

    public static LogisticResource of(FluidStack stack) {
        return stack.isEmpty() ? any(Resource.FLUIDS) : new LogisticResource(Resource.FLUIDS, ItemStack.EMPTY, new FluidStack(stack, 1), null, null);
    }

    public static LogisticResource of(Gas gas) {
        return gas == null || gas.isEmptyType() ? any(Resource.GAS) : new LogisticResource(Resource.GAS, ItemStack.EMPTY, FluidStack.EMPTY, gas, null);
    }

    public static LogisticResource of(DataType data) {
        return data == null || data.isEmptyType() ? any(Resource.DATA) : new LogisticResource(Resource.DATA, ItemStack.EMPTY, FluidStack.EMPTY, null, data);
    }

    public static final LogisticResource ENERGY = any(Resource.ENERGY);
    public static final LogisticResource PRESSURE = any(Resource.PRESSURE);

    /** Ressource generique (famille entiere ou tag), pas une ressource precise ? L'energie est toujours « precise ». */
    public boolean isAny() {
        return switch (type) {
            case ITEMS -> item.isEmpty();
            case FLUIDS -> fluid.isEmpty();
            case GAS -> gas == null;
            case DATA -> data == null;
            case ENERGY, PRESSURE -> false;
        };
    }

    /** Famille entiere (ni ressource precise, ni tag). */
    public boolean isFamily() {
        return isAny() && tag == null;
    }

    @Nullable
    public TagKey<Item> tagKey() {
        return tag;
    }

    /** Cette ressource (precise, tag ou famille) englobe-t-elle {@code other} (precise) ? */
    public boolean covers(LogisticResource other) {
        if (other.type != type) {
            return false;
        }
        if (other.isAny()) {
            //Recouvrement entre generiques : meme tag, ou une famille entiere, ou un objet precis du tag
            if (isFamily()) {
                return true;
            }
            if (other.tag == null) {
                return false;
            }
            return Objects.equals(tag, other.tag) || !item.isEmpty() && item.is(other.tag);
        }
        if (tag != null) {
            return !other.item.isEmpty() && other.item.is(tag);
        }
        return isAny() || equals(other);
    }

    public ItemStack item() {
        return item;
    }

    public FluidStack fluid() {
        return fluid;
    }

    @Nullable
    public Gas gas() {
        return gas;
    }

    @Nullable
    public DataType data() {
        return data;
    }

    public boolean matches(ItemStack stack) {
        if (type != Resource.ITEMS || stack.isEmpty()) {
            return false;
        }
        if (tag != null) {
            return stack.is(tag);
        }
        return item.isEmpty() || ItemStack.isSameItemSameTags(item, stack);
    }

    public boolean matches(FluidStack stack) {
        return type == Resource.FLUIDS && !stack.isEmpty() && (fluid.isEmpty() || fluid.isFluidEqual(stack));
    }

    public boolean matches(GasStack stack) {
        return type == Resource.GAS && !stack.isEmpty() && (gas == null || stack.getGas().equals(gas));
    }

    public boolean matches(DataStack stack) {
        return type == Resource.DATA && !stack.isEmpty() && (data == null || stack.isTypeEqual(data));
    }

    // ------------------------------------------------------------------
    // Affichage
    // ------------------------------------------------------------------

    public Component name() {
        return switch (type) {
            case ITEMS -> tag != null ? Component.literal("#" + tag.location()) : item.isEmpty() ? Component.translatable("robotics.pioneers.resource.any_items")
                                                                                     : item.getHoverName();
            case FLUIDS -> fluid.isEmpty() ? Component.translatable("robotics.pioneers.resource.any_fluids") : fluid.getDisplayName();
            case GAS -> gas == null ? Component.translatable("robotics.pioneers.resource.any_gas") : gas.getName();
            case DATA -> data == null ? Component.translatable("robotics.pioneers.resource.any_data") : data.getName();
            case ENERGY -> Component.translatable("robotics.pioneers.resource.energy");
            case PRESSURE -> Component.translatable("robotics.pioneers.resource.pressure");
        };
    }

    /** Icone (objet) : l'objet, le seau du fluide, ou l'icone de la famille. */
    public ItemStack icon() {
        if (type == Resource.ITEMS && !item.isEmpty()) {
            return item;
        }
        if (tag != null) {
            //Les objets du tag defilent (un par seconde) : on voit tout de suite que c'est une famille
            var items = BuiltInRegistries.ITEM.getTag(tag);
            if (items.isPresent() && items.get().size() > 0) {
                int index = (int) ((System.currentTimeMillis() / 1000) % items.get().size());
                return new ItemStack(items.get().get(index).value());
            }
        }
        if (type == Resource.FLUIDS && !fluid.isEmpty() && fluid.getFluid().getBucket() != Items.AIR) {
            return new ItemStack(fluid.getFluid().getBucket());
        }
        return new ItemStack(type.icon);
    }

    /** Couleur (fluides, gaz, donnees) pour les jauges ; blanc sinon. */
    public int color() {
        return switch (type) {
            case GAS -> gas == null ? 0xC0C0C0 : gas.getTint() & 0xFFFFFF;
            case DATA -> data == null ? 0x55CCFF : data.getColor();
            case ENERGY -> 0xFF4040;
            case PRESSURE -> 0x9FB8C8;
            case FLUIDS -> 0x3F76E4;
            default -> 0xFFFFFF;
        };
    }

    // ------------------------------------------------------------------
    // Sauvegarde, egalite
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putByte("type", (byte) type.ordinal());
        switch (type) {
            case ITEMS -> {
                if (!item.isEmpty()) {
                    tag.put("item", item.save(new CompoundTag()));
                } else if (this.tag != null) {
                    tag.putString("tag", this.tag.location().toString());
                }
            }
            case FLUIDS -> {
                if (!fluid.isEmpty()) {
                    tag.put("fluid", fluid.writeToNBT(new CompoundTag()));
                }
            }
            case GAS -> {
                if (gas != null) {
                    tag.putString("gas", gas.getRegistryName().toString());
                }
            }
            case DATA -> {
                if (data != null) {
                    tag.putString("data", data.getRegistryName().toString());
                }
            }
            default -> {
            }
        }
        return tag;
    }

    public static LogisticResource load(CompoundTag tag) {
        Resource type = Resource.byIndex(tag.getByte("type"));
        return switch (type) {
            case ITEMS -> tag.contains("item") ? of(ItemStack.of(tag.getCompound("item")))
                                               : tag.contains("tag") ? tag(ItemTags.create(ResourceLocation.parse(tag.getString("tag")))) : any(type);
            case FLUIDS -> tag.contains("fluid") ? of(FluidStack.loadFluidStackFromNBT(tag.getCompound("fluid"))) : any(type);
            case GAS -> {
                Gas gas = tag.contains("gas") ? PioneersAPI.gas(ResourceLocation.parse(tag.getString("gas"))) : null;
                yield of(gas);
            }
            case DATA -> {
                DataType data = tag.contains("data") ? PioneersAPI.dataTypeRegistry().getValue(ResourceLocation.parse(tag.getString("data"))) : null;
                yield of(data);
            }
            case ENERGY -> ENERGY;
            case PRESSURE -> PRESSURE;
        };
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof LogisticResource other) || other.type != type) {
            return false;
        }
        return switch (type) {
            case ITEMS -> ItemStack.isSameItemSameTags(item, other.item) && Objects.equals(tag, other.tag);
            case FLUIDS -> fluid.isFluidEqual(other.fluid);
            case GAS -> Objects.equals(gas, other.gas);
            case DATA -> data == other.data;
            case ENERGY, PRESSURE -> true;
        };
    }

    @Override
    public int hashCode() {
        return switch (type) {
            case ITEMS -> 31 * item.getItem().hashCode() + Objects.hashCode(item.getTag()) + 7 * Objects.hashCode(tag);
            case FLUIDS -> 37 * fluid.getFluid().hashCode() + Objects.hashCode(fluid.getTag());
            case GAS -> 41 * Objects.hashCode(gas);
            case DATA -> 43 * Objects.hashCode(data);
            case ENERGY -> 47;
            case PRESSURE -> 53;
        };
    }
}
