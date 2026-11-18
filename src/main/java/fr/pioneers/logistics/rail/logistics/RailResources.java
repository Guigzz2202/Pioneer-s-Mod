package fr.pioneers.logistics.rail.logistics;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.data.DataType;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.Resource;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/** 0.20.5 : passage entre les ressources du rail ({@link Cargo}, sans registres) et celles du jeu (robots, noms, icones). */
public final class RailResources {

    private RailResources() {
    }

    /** Ressource du reseau robotique ; null si l'id n'existe plus. */
    @Nullable
    public static LogisticResource toLogistic(Cargo c) {
        ResourceLocation id = c.id();
        return switch (c.kind()) {
            case ENERGY -> LogisticResource.ENERGY;
            case AIR -> LogisticResource.PRESSURE;
            case ITEM -> {
                if (id == null) {
                    yield LogisticResource.any(Resource.ITEMS);
                }
                Item item = BuiltInRegistries.ITEM.get(id);
                yield item == Items.AIR ? null : LogisticResource.of(new ItemStack(item));
            }
            case FLUID -> {
                if (id == null) {
                    yield LogisticResource.any(Resource.FLUIDS);
                }
                Fluid f = ForgeRegistries.FLUIDS.getValue(id);
                yield f == null || f == Fluids.EMPTY ? null : LogisticResource.of(new FluidStack(f, 1));
            }
            case GAS -> {
                if (id == null) {
                    yield LogisticResource.any(Resource.GAS);
                }
                Gas g = PioneersAPI.gas(id);
                yield g == null ? null : LogisticResource.of(g);
            }
            case DATA -> {
                if (id == null) {
                    yield LogisticResource.any(Resource.DATA);
                }
                DataType t = PioneersAPI.dataTypeRegistry().getValue(id);
                yield t == null ? null : LogisticResource.of(t);
            }
        };
    }

    /** Ressource du rail d'une ressource robotique (objet, fluide, gaz, energie, donnees, air) ; null pour un tag. */
    @Nullable
    public static Cargo fromLogistic(LogisticResource r) {
        if (r.tagKey() != null) {
            return null;
        }
        return switch (r.type) {
            case ITEMS -> r.isAny() ? null : Cargo.item(BuiltInRegistries.ITEM.getKey(r.item().getItem()));
            case FLUIDS -> r.isAny() ? null : Cargo.fluid(ForgeRegistries.FLUIDS.getKey(r.fluid().getFluid()));
            case GAS -> r.isAny() || r.gas() == null ? null : Cargo.gas(r.gas().getRegistryName());
            case ENERGY -> Cargo.ENERGY;
            case DATA -> r.isAny() || r.data() == null ? null : new Cargo(Cargo.Kind.DATA, r.data().getRegistryName());
            case PRESSURE -> Cargo.AIR;
        };
    }

    /** Nom affiche. */
    public static Component name(Cargo c) {
        LogisticResource r = toLogistic(c);
        if (r != null) {
            return r.name();
        }
        return Component.literal(c.id() == null ? c.kind().name() : c.id().toString());
    }

    public static ItemStack icon(Cargo c) {
        LogisticResource r = toLogistic(c);
        return r == null ? new ItemStack(Items.BARRIER) : r.icon();
    }
}
