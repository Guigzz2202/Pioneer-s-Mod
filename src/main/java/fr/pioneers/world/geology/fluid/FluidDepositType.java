package fr.pioneers.world.geology.fluid;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.world.geology.DepositType.Weighted;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Un type de gisement fluide (fichier {@code data/<ns>/fluid_deposits/<id>.json}) : petrole, gaz ou saumure. Ce que le puits en tire (un
 * fluide, ou un gaz), un gaz associe et sa part, ou il se trouve (biomes ponderes, aucun repli : seulement la ou un biome le permet), sa
 * reserve (mB) et son debit de base (mB par tick au tier basique, a pleine pression).
 *
 * <pre>{"category": "oil", "fluid": "pioneers:raw_oil", "byproduct": "pioneers:natural_gas", "share": 0.1, "reserve": [4000000, 16000000],
 *  "rate": 40, "dimension": "minecraft:overworld", "biomes": [{"biomes": "#forge:is_desert", "weight": 8}]}</pre>
 */
public record FluidDepositType(ResourceLocation id, String category, @Nullable Fluid fluid, @Nullable ResourceLocation gas,
                               @Nullable ResourceLocation byproduct, float share, ResourceKey<Level> dimension, long reserveMin, long reserveMax, int rate,
                               List<Weighted> biomes) {

    public int weight(Holder<Biome> biome) {
        int best = 0;
        for (Weighted w : biomes) {
            boolean match = w.tag() != null ? biome.is(w.tag()) : w.biome() != null && biome.is(w.biome());
            if (match) {
                best = Math.max(best, w.weight());
            }
        }
        return best;
    }

    public boolean isGas() {
        return fluid == null;
    }

    public FluidStack fluidStack(int amount) {
        return fluid == null || amount <= 0 ? FluidStack.EMPTY : new FluidStack(fluid, amount);
    }

    public GasStack gasStack(long amount) {
        return gasOf(gas, amount);
    }

    public GasStack byproductStack(long amount) {
        return gasOf(byproduct, amount);
    }

    private static GasStack gasOf(@Nullable ResourceLocation id, long amount) {
        if (id == null || amount <= 0) {
            return GasStack.EMPTY;
        }
        Gas g = PioneersAPI.gas(id);
        return g == null || g.isEmptyType() ? GasStack.EMPTY : GasStack.of(g, amount);
    }

    public String translationKey() {
        return "fluid_deposit." + id.getNamespace() + "." + id.getPath();
    }

    @Nullable
    public static FluidDepositType parse(ResourceLocation id, JsonObject json) {
        String category = GsonHelper.getAsString(json, "category", "oil");
        Fluid fluid = null;
        ResourceLocation gas = null;
        if (json.has("fluid")) {
            fluid = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(GsonHelper.getAsString(json, "fluid")));
            if (fluid == null || fluid == Fluids.EMPTY) {
                return null;
            }
        } else if (json.has("gas")) {
            gas = ResourceLocation.parse(GsonHelper.getAsString(json, "gas"));
        } else {
            return null;
        }
        ResourceLocation byproduct = json.has("byproduct") ? ResourceLocation.parse(GsonHelper.getAsString(json, "byproduct")) : null;
        float share = GsonHelper.getAsFloat(json, "share", 0F);
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(GsonHelper.getAsString(json, "dimension", "minecraft:overworld")));
        JsonArray reserve = GsonHelper.getAsJsonArray(json, "reserve", null);
        long min = reserve == null ? 4_000_000 : reserve.get(0).getAsLong(), max = reserve == null ? 16_000_000 : reserve.get(1).getAsLong();
        int rate = Math.max(1, GsonHelper.getAsInt(json, "rate", 40));
        List<Weighted> biomes = new ArrayList<>();
        for (JsonElement e : GsonHelper.getAsJsonArray(json, "biomes", new JsonArray())) {
            JsonObject o = e.getAsJsonObject();
            String b = GsonHelper.getAsString(o, "biomes");
            int weight = Math.max(0, GsonHelper.getAsInt(o, "weight", 10));
            if (b.startsWith("#")) {
                biomes.add(new Weighted(TagKey.create(Registries.BIOME, ResourceLocation.parse(b.substring(1))), null, weight));
            } else {
                biomes.add(new Weighted(null, ResourceKey.create(Registries.BIOME, ResourceLocation.parse(b)), weight));
            }
        }
        return new FluidDepositType(id, category, fluid, gas, byproduct, share, dimension, Math.min(min, max), Math.max(min, max), rate,
              List.copyOf(biomes));
    }
}
