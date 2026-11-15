package fr.pioneers.logistics.rail.client;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.core.Texts;
import fr.pioneers.logistics.rail.logistics.Cargo;
import fr.pioneers.logistics.rail.logistics.RailResources;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluid;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 0.20.5 : textes du rail cote client : noms de gare dont les jetons de ressource ({@code [i:id]}, {@code [f:id]}, {@code [g:id]}) sont
 * montres par leur nom traduit, unites et quantites des ressources.
 */
public final class RailText {

    private static final Pattern TOKEN = Pattern.compile("\\[([ifg]):([a-z0-9_.\\-]+:[a-z0-9_./\\-]+)]");

    private RailText() {
    }

    /** Nom de gare a l'ecran (jetons traduits). */
    public static Component station(String name) {
        Matcher m = TOKEN.matcher(name);
        MutableComponent out = Component.empty();
        int last = 0;
        boolean any = false;
        while (m.find()) {
            any = true;
            out.append(name.substring(last, m.start()));
            ResourceLocation id = ResourceLocation.tryParse(m.group(2));
            out.append(Component.literal("‹").append(resource(m.group(1).charAt(0), id)).append("›"));
            last = m.end();
        }
        if (!any) {
            return Component.literal(name);
        }
        out.append(name.substring(last));
        return out;
    }

    private static Component resource(char kind, ResourceLocation id) {
        if (id == null) {
            return Component.literal("?");
        }
        switch (kind) {
            case 'f' -> {
                Fluid f = ForgeRegistries.FLUIDS.getValue(id);
                return f == null ? Component.literal(id.getPath()) : f.getFluidType().getDescription();
            }
            case 'g' -> {
                Gas g = PioneersAPI.gas(id);
                return g == null ? Component.literal(id.getPath()) : g.getName();
            }
            default -> {
                Item item = BuiltInRegistries.ITEM.get(id);
                return item == Items.AIR ? Component.literal(id.getPath()) : new ItemStack(item).getHoverName();
            }
        }
    }

    /** Unite d'une ressource (apres la quantite). */
    public static String unit(Cargo.Kind kind) {
        return switch (kind) {
            case FLUID, GAS -> " mB";
            case ENERGY -> " FE";
            case DATA -> " Mo";
            case AIR -> " L";
            default -> "";
        };
    }

    /** Quantite lisible (12 k, 3,4 M). */
    public static String amount(long n) {
        if (n >= 10_000_000_000L) {
            return n / 1_000_000_000L + " G";
        }
        if (n >= 10_000_000) {
            return n / 1_000_000 + " M";
        }
        if (n >= 100_000) {
            return n / 1000 + " k";
        }
        return Texts.grouped(n);
    }

    public static Component name(Cargo c) {
        return RailResources.name(c);
    }

    public static ItemStack icon(Cargo c) {
        return RailResources.icon(c);
    }
}
