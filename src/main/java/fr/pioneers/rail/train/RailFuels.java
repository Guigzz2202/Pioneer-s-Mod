package fr.pioneers.rail.train;

import java.util.Map;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

/**
 * 0.20.2 : carburants des locomotives, a la meme echelle que les generateurs du mod (energie par mB = celle du generateur a combustion,
 * 1 tick de combustion = 1 024 FE). Diesel : carburants liquides ; locomotive a gaz : gaz combustibles (hydrogene x1,5, sans fumee) ;
 * vapeur sans foyer : vapeur (64 FE/mB comme la turbine) ; nucleaire : assemblages (cent fois le charbon), rendus uses.
 */
public final class RailFuels {

    private RailFuels() {
    }

    /** FE par mB des carburants liquides du diesel. */
    public static final Map<String, Integer> LIQUID = Map.of(
          "pioneers:diesel", 8_192,
          "pioneers:renewable_diesel", 8_192,
          "pioneers:biodiesel", 6_144,
          "pioneers:kerosene", 7_168,
          "pioneers:bio_kerosene", 7_168,
          "pioneers:fuel_oil", 5_120,
          "pioneers:lng", 9_830);

    /** FE par mB des gaz de la locomotive a gaz (rendement compris). */
    public static final Map<String, Integer> GAS = Map.of(
          "pioneers:natural_gas", 1_638,
          "pioneers:methane", 3_277,
          "pioneers:propane", 2_185,
          "pioneers:butane", 2_185,
          "pioneers:biogas", 546,
          "pioneers:hydrogen", 1_843);

    public static final String STEAM = "pioneers:steam";
    public static final int STEAM_FE = 64;

    /** Assemblage neuf -> assemblage use (encore chaud) ; energie d'un assemblage (FE). */
    public static final Map<String, String> NUCLEAR_SPENT = Map.of(
          "pioneers:fuel_assembly", "pioneers:spent_fuel_assembly_hot",
          "pioneers:mox_assembly", "pioneers:spent_mox_assembly_hot");
    public static final Map<String, Long> NUCLEAR_FE = Map.of(
          "pioneers:fuel_assembly", 256_000_000L,
          "pioneers:mox_assembly", 384_000_000L);

    public static int liquid(String id) {
        return LIQUID.getOrDefault(id, 0);
    }

    public static int gas(String id) {
        return GAS.getOrDefault(id, 0);
    }

    /** Hydrogene : la locomotive ne fume pas. */
    public static boolean clean(String gasId) {
        return "pioneers:hydrogen".equals(gasId);
    }

    /** Energie d'un assemblage de combustible (0 : pas un combustible de locomotive). */
    public static long nuclear(ItemStack stack) {
        if (stack.isEmpty()) {
            return 0;
        }
        ResourceLocation id = BuiltInRegistries.ITEM.getKey(stack.getItem());
        return NUCLEAR_FE.getOrDefault(id.toString(), 0L);
    }

    /** Ce que la locomotive accepte comme carburant dans sa reserve principale. */
    public static boolean accepts(CarType type, String id) {
        return switch (type) {
            case DIESEL -> liquid(id) > 0;
            case GAS_LOCO -> gas(id) > 0;
            case FIRELESS -> STEAM.equals(id);
            default -> true;
        };
    }
}
