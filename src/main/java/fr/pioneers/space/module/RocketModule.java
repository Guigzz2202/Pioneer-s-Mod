package fr.pioneers.space.module;

import fr.pioneers.space.RocketTier;
import fr.pioneers.space.cargo.CargoType;
import fr.pioneers.space.research.ResearchNode;
import java.util.List;
import java.util.Locale;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/**
 * Modules de fusee : la fusee embarque entre 3 (tier 1) et 7 (tier 10) modules, installes directement dans la fusee
 * (accroupi + clic droit). Ce sont eux qui decident de ce qu'elle peut faire :
 * <ul>
 *     <li><b>Habitacle</b> : vol habite (un seul) ;</li>
 *     <li><b>Sonde</b> : chaque module met une sonde en orbite (le tier de la sonde suit celui de la fusee) ;</li>
 *     <li><b>Soutes</b> (objets, fluides, gaz, donnees) : debloquent le stockage de la fusee (vols habites et routes logistiques) ;</li>
 *     <li><b>Ameliorations</b> : efficacite (hydrogene), vitesse (duree des vols), bouclier (echecs de lancement).</li>
 * </ul>
 */
public enum RocketModule {
    PASSENGER(0x6FB0FF, 1, null),
    PROBE(0xF0C040, 7, null),
    CARGO_ITEMS(CargoType.ITEMS.color, 7, CargoType.ITEMS),
    CARGO_FLUIDS(CargoType.FLUIDS.color, 7, CargoType.FLUIDS),
    CARGO_GAS(CargoType.GAS.color, 7, CargoType.GAS),
    CARGO_DATA(CargoType.DATA.color, 7, CargoType.DATA),
    EFFICIENCY(0x7CE070, 3, null),
    SPEED(0x50D8F0, 3, null),
    SHIELD(0xE07050, 3, null),
    /**
     * 1.0.7 (lot 11) : propulseur a matiere noire : sur une fusee de tier 13, ouvre les trois systemes lointains (coque interstellaire de
     * l'Ascenseur de Mars, moteur et capsule de matiere noire de l'Observatoire du Halo).
     */
    DARK_MATTER_DRIVE(0x8A5CFF, 1, null);

    /** Emplacements de modules d'une fusee de tier 13 (le maximum). */
    public static final int MAX_SLOTS = 9;
    /** Capacite d'une soute : 9 piles d'objets, 16 000 mB de fluide ou de gaz, 50 000 Mo de donnees. */
    public static final int ITEM_SLOTS = 9;
    public static final long FLUID_CAPACITY = 16_000, GAS_CAPACITY = 16_000, DATA_CAPACITY = 50_000;
    /** Effets par module (cumulatifs, plafonnes par {@link #maxStack}). */
    public static final double EFFICIENCY_FACTOR = 0.85, SPEED_FACTOR = 0.8, SHIELD_FACTOR = 0.6;

    public final int color;
    /** Nombre maximal de modules de ce type qui comptent sur une fusee. */
    public final int maxStack;
    @Nullable
    public final CargoType cargo;

    RocketModule(int color, int maxStack, @Nullable CargoType cargo) {
        this.color = color;
        this.maxStack = maxStack;
        this.cargo = cargo;
    }

    public String id() {
        return "rocket_module_" + name().toLowerCase(Locale.ROOT);
    }

    public String getTranslationKey() {
        return "item.pioneers." + id();
    }

    public boolean isCargo() {
        return cargo != null;
    }

    /** Recherche necessaire pour assembler ce module (null = aucune ; les soutes demandent le plan logistique). */
    @Nullable
    public ResearchNode requiredResearch() {
        return switch (this) {
            case EFFICIENCY -> ResearchNode.CRYO_TANKS;
            case SPEED -> ResearchNode.ION_PROPULSION;
            case SHIELD -> ResearchNode.THERMAL_SHIELDING;
            default -> null;
        };
    }

    /** Emplacements de modules d'une fusee : 3, 3, 4, 4, 5, 5, 6, 6, 7, 7, puis 8, 8, 9. */
    public static int slots(RocketTier tier) {
        return 3 + (tier.level - 1) / 2;
    }

    /** Tier des sondes qu'une fusee peut mettre en orbite (1 a 5). */
    public static int probeTier(RocketTier tier) {
        return switch (tier.level) {
            case 1 -> 1;
            case 2, 3 -> 2;
            case 4, 5 -> 3;
            case 6, 7 -> 4;
            default -> 5;
        };
    }

    @Nullable
    public static RocketModule of(ItemStack stack) {
        return stack.getItem() instanceof ItemRocketModule module ? module.getModule() : null;
    }

    /** Nombre de modules d'un type (plafonne). */
    public static int count(List<ItemStack> modules, RocketModule type) {
        int count = 0;
        for (ItemStack stack : modules) {
            if (of(stack) == type) {
                count += stack.getCount();
            }
        }
        return Math.min(count, type.maxStack);
    }

    public static boolean hasCargo(List<ItemStack> modules) {
        for (ItemStack stack : modules) {
            RocketModule module = of(stack);
            if (module != null && module.isCargo()) {
                return true;
            }
        }
        return false;
    }

    public static double fuelMultiplier(List<ItemStack> modules) {
        return Math.pow(EFFICIENCY_FACTOR, count(modules, EFFICIENCY));
    }

    public static double timeMultiplier(List<ItemStack> modules) {
        return Math.pow(SPEED_FACTOR, count(modules, SPEED));
    }

    public static double failureMultiplier(List<ItemStack> modules) {
        return Math.pow(SHIELD_FACTOR, count(modules, SHIELD));
    }

    /** Soute dominante (pour la couleur des vols sur la carte). */
    public static CargoType dominantCargo(List<ItemStack> modules) {
        int[] counts = new int[CargoType.values().length];
        for (ItemStack stack : modules) {
            RocketModule module = of(stack);
            if (module != null && module.cargo != null) {
                counts[module.cargo.ordinal()] += stack.getCount();
            }
        }
        int best = 0;
        for (int i = 1; i < counts.length; i++) {
            if (counts[i] > counts[best]) {
                best = i;
            }
        }
        return CargoType.byIndex(best);
    }

    public static RocketModule byIndex(int index) {
        RocketModule[] values = values();
        return values[Math.floorMod(index, values.length)];
    }
}
