package fr.pioneers.tech.industry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.common.Tags;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 46, retour 72 de Guillaume : « une machine qui genere les differents types de roches » ; reponse : « echantillon + eau + lave
 * jamais consommee ») : regles des ECHANTILLONS du generateur de roches (et du bloc a eau), communes au serveur (filtre de la case,
 * production), a l'interface (message de refus) et a JEI. Donnees : {@link GeneratedLot46} (source unique {@code tools/gc/core/p105_lot46.py}).
 * <ul>
 *     <li>une roche de {@link GeneratedLot46#ROCK_TIER} est un echantillon, permis a partir de son tier (0 = MKI .. 3 = MKIV) ;</li>
 *     <li>tout le reste est refuse avec sa raison ({@link #refusal}) : minerai (tag {@code forge:ores} ou id {@code *_ore}), roche a valeur de
 *     ressource ({@link GeneratedLot46#REFUSED}), objet qui n'est pas une roche de la liste ;</li>
 *     <li>le produit est la roche de l'echantillon lui-meme (une a la fois, sans NBT) ;</li>
 *     <li>eau et lave : chacune dans un reservoir d'entree, au moins {@link GeneratedLot46#SAMPLE_FLUID} mB, jamais prelevees.</li>
 * </ul>
 */
public final class RockSamples {

    /** Raisons de refus (cles {@code gui.pioneers.sample.why.<raison>}). */
    public static final String ORE = "ore", RESOURCE = "resource", NOT_ROCK = "not_rock", TIER = "tier";

    private static final List<List<Item>> BY_TIER = new ArrayList<>();

    private RockSamples() {
    }

    /** Tier minimal (0 = MKI .. 3 = MKIV) de la roche, ou -1 si ce n'est pas un echantillon permis. */
    public static int minTier(ItemStack stack) {
        if (stack.isEmpty()) {
            return -1;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        return id == null ? -1 : minTier(id.toString());
    }

    /** Tier minimal d'un identifiant de roche, ou -1. */
    public static int minTier(String id) {
        Integer t = GeneratedLot46.ROCK_TIER.get(id);
        return t == null ? -1 : t;
    }

    /** Filtre de la case d'echantillon : une roche de la liste, quel que soit le tier (un tier trop bas le dit a l'ecran). */
    public static boolean accepts(ItemStack stack) {
        return minTier(stack) >= 0;
    }

    /**
     * Raison du refus de l'objet comme echantillon d'un generateur de tier {@code tier} (0..3), ou null s'il est bon. {@link #TIER} : roche de
     * la liste, mais d'un tier plus haut.
     */
    @Nullable
    public static String refusal(ItemStack stack, int tier) {
        if (stack.isEmpty()) {
            return null;
        }
        int t = minTier(stack);
        if (t >= 0) {
            return t > tier ? TIER : null;
        }
        ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
        String key = id == null ? "" : id.toString();
        if (stack.is(Tags.Items.ORES) || key.endsWith("_ore") || key.contains("_ore_")) {
            return ORE;
        }
        return GeneratedLot46.REFUSED.containsKey(key) ? RESOURCE : NOT_ROCK;
    }

    /** Le produit d'un echantillon : la meme roche, une a la fois (vide si ce n'est pas un echantillon). */
    public static ItemStack product(ItemStack sample) {
        return accepts(sample) ? new ItemStack(sample.getItem()) : ItemStack.EMPTY;
    }

    /** Eau (tag {@code minecraft:water}) ? */
    public static boolean water(FluidStack stack) {
        return !stack.isEmpty() && (stack.getFluid().isSame(Fluids.WATER) || stack.getFluid().is(FluidTags.WATER));
    }

    /** Lave (tag {@code minecraft:lava}) ? */
    public static boolean lava(FluidStack stack) {
        return !stack.isEmpty() && (stack.getFluid().isSame(Fluids.LAVA) || stack.getFluid().is(FluidTags.LAVA));
    }

    /** Fluide accepte par un reservoir du generateur de roches : eau ou lave. */
    public static boolean fluid(FluidStack stack) {
        return water(stack) || lava(stack);
    }

    /** Les deux reservoirs tiennent-ils au moins {@link GeneratedLot46#SAMPLE_FLUID} mB d'eau ET de lave (aucun n'est preleve) ? */
    public static boolean fluidsReady(List<? extends net.minecraftforge.fluids.capability.templates.FluidTank> tanks) {
        boolean w = false, l = false;
        for (var t : tanks) {
            FluidStack f = t.getFluid();
            if (f.getAmount() >= GeneratedLot46.SAMPLE_FLUID) {
                w |= water(f);
                l |= lava(f);
            }
        }
        return w && l;
    }

    /** Roches permises a ce tier (0..3), dans l'ordre de la liste (resolues une fois les registres prets). */
    public static List<Item> items(int tier) {
        synchronized (BY_TIER) {
            if (BY_TIER.isEmpty()) {
                List<List<Item>> all = new ArrayList<>();
                for (int t = 0; t < 4; t++) {
                    all.add(new ArrayList<>());
                }
                for (String id : GeneratedLot46.ROCKS) {
                    Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(id));
                    if (item == null || item == Items.AIR || !(item instanceof BlockItem)) {
                        return List.of();                                  // registres pas encore prets : rien n'est garde
                    }
                    for (int t = GeneratedLot46.ROCK_TIER.get(id); t < 4; t++) {
                        all.get(t).add(item);
                    }
                }
                all.forEach(l -> BY_TIER.add(Collections.unmodifiableList(l)));
            }
            return BY_TIER.get(Math.max(0, Math.min(3, tier)));
        }
    }
}
