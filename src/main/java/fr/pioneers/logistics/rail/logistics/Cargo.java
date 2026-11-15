package fr.pioneers.logistics.rail.logistics;

import fr.pioneers.logistics.rail.train.Car;
import fr.pioneers.logistics.rail.train.CarType;
import fr.pioneers.logistics.rail.train.Schedule;
import java.util.Locale;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.5 : une ressource transportee par le rail (objet, fluide, gaz, energie, donnees, air comprime), sans objet du jeu : le moteur et le
 * banc d'essai s'en servent sans registres. {@code id} : objet, fluide, gaz ou type de donnees ; null pour l'energie et l'air (ou « toute la
 * famille »).
 */
public record Cargo(Kind kind, @Nullable ResourceLocation id) {

    public enum Kind {
        ITEM, FLUID, GAS, ENERGY, DATA, AIR;

        public static Kind byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public static final Cargo ENERGY = new Cargo(Kind.ENERGY, null), AIR = new Cargo(Kind.AIR, null);

    public static Cargo item(ResourceLocation id) {
        return new Cargo(Kind.ITEM, id);
    }

    public static Cargo fluid(ResourceLocation id) {
        return new Cargo(Kind.FLUID, id);
    }

    public static Cargo gas(ResourceLocation id) {
        return new Cargo(Kind.GAS, id);
    }

    /** Toute ressource de la famille (id null) ? */
    public boolean any() {
        return id == null;
    }

    public boolean covers(Cargo other) {
        return kind == other.kind && (id == null || id.equals(other.id));
    }

    /** Jeton de nom de gare (objet, fluide, gaz). */
    public String token() {
        return id == null ? "" : Schedule.token(kind == Kind.FLUID ? Schedule.FLUID : kind == Kind.GAS ? Schedule.GAS : Schedule.ITEM, id);
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putByte("k", (byte) kind.ordinal());
        if (id != null) {
            t.putString("id", id.toString());
        }
        return t;
    }

    public static Cargo load(CompoundTag t) {
        Kind k = Kind.byId(t.getByte("k"));
        ResourceLocation id = t.contains("id") ? ResourceLocation.tryParse(t.getString("id")) : null;
        return new Cargo(k, k == Kind.ENERGY || k == Kind.AIR ? null : id);
    }

    @Override
    public String toString() {
        return kind.name().toLowerCase(Locale.ROOT) + (id == null ? "" : ":" + id);
    }

    // ------------------------------------------------------------------ dans un vehicule

    /** Quantite de cette ressource dans le vehicule. */
    public long in(Car c) {
        return switch (kind) {
            case ITEM -> {
                long n = 0;
                for (int i = 0; i < c.items.getSlots(); i++) {
                    ItemStack s = c.items.getStackInSlot(i);
                    if (!s.isEmpty() && (id == null || id.equals(itemId(s)))) {
                        n += s.getCount();
                    }
                }
                yield c.type.locomotive || c.type == CarType.TENDER ? 0 : n;
            }
            case FLUID -> c.type == CarType.TANK && matches(c.fluid) ? c.fluidAmount : 0;
            case GAS -> c.type == CarType.GAS_TANK && matches(c.gas) ? c.gasAmount : 0;
            case ENERGY -> c.type == CarType.BATTERY ? c.energy : 0;
            case DATA -> c.type == CarType.SERVER && matches(c.dataType) ? c.data : 0;
            case AIR -> c.type == CarType.AIR ? c.air : 0;
        };
    }

    /** Place pour cette ressource dans le vehicule (objets : 64 par case libre ou de la meme sorte). */
    public long room(Car c) {
        return switch (kind) {
            case ITEM -> {
                if (c.type.locomotive || c.type == CarType.TENDER || c.type == CarType.PASSENGER || c.type == CarType.FLAT || c.type == CarType.ROBOT_VAN) {
                    yield 0;
                }
                long n = 0;
                for (int i = 0; i < c.items.getSlots(); i++) {
                    ItemStack s = c.items.getStackInSlot(i);
                    if (s.isEmpty()) {
                        n += 64;
                    } else if (id != null && id.equals(itemId(s))) {
                        n += Math.max(0, s.getMaxStackSize() - s.getCount());
                    }
                }
                yield n;
            }
            case FLUID -> c.type == CarType.TANK && (c.fluidAmount == 0 || matches(c.fluid)) ? c.fluidCapacity() - c.fluidAmount : 0;
            case GAS -> c.type == CarType.GAS_TANK && (c.gasAmount == 0 || matches(c.gas)) ? c.gasCapacity() - c.gasAmount : 0;
            case ENERGY -> c.type == CarType.BATTERY ? c.energyCapacity() - c.energy : 0;
            case DATA -> c.type == CarType.SERVER && (c.data == 0 || matches(c.dataType)) ? c.dataCapacity() - c.data : 0;
            case AIR -> c.type == CarType.AIR ? c.airCapacity() - c.air : 0;
        };
    }

    private boolean matches(String content) {
        return id == null || id.toString().equals(content);
    }

    @Nullable
    public static ResourceLocation itemId(ItemStack s) {
        return s.isEmpty() ? null : BuiltInRegistries.ITEM.getKey(s.getItem());
    }
}
