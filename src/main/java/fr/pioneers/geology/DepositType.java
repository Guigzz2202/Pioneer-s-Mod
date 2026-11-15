package fr.pioneers.geology;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * Un type de gisement (fichier {@code data/<ns>/deposits/<id>.json}) : ce que la foreuse en tire, ou il se trouve (biomes ponderes, dimension),
 * le niveau de foreuse requis, sa reserve et son affleurement.
 *
 * <pre>{"result": "pioneers:raw_iron", "material": "iron", "dimension": "minecraft:overworld", "tier": 1, "reserve": [40000, 160000],
 *  "outcrop": "pioneers:magnetite_outcrop", "color": "#3A3A40", "fallback": 0,
 *  "biomes": [{"biomes": "#minecraft:is_mountain", "weight": 10}]}</pre>
 */
public record DepositType(ResourceLocation id, Item result, String material, ResourceKey<Level> dimension, int tier, int reserveMin, int reserveMax,
                          @Nullable Block outcrop, int color, int fallback, List<Weighted> biomes) {

    /** Un biome (ou un tag de biomes) et le poids du gisement dans ce biome. */
    public record Weighted(@Nullable TagKey<Biome> tag, @Nullable ResourceKey<Biome> biome, int weight) {

        boolean matches(Holder<Biome> holder) {
            return tag != null ? holder.is(tag) : biome != null && holder.is(biome);
        }
    }

    /** Poids de ce gisement dans ce biome (0 = absent). */
    public int weight(Holder<Biome> biome) {
        int best = 0;
        for (Weighted w : biomes) {
            if (w.matches(biome)) {
                best = Math.max(best, w.weight);
            }
        }
        return best;
    }

    public String translationKey() {
        return "deposit." + id.getNamespace() + "." + id.getPath();
    }

    @Nullable
    public static DepositType parse(ResourceLocation id, JsonObject json) {
        Item result = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(GsonHelper.getAsString(json, "result")));
        if (result == null || result == Items.AIR) {
            return null;
        }
        String material = GsonHelper.getAsString(json, "material", id.getPath());
        ResourceKey<Level> dimension = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(GsonHelper.getAsString(json, "dimension", "minecraft:overworld")));
        int tier = GsonHelper.getAsInt(json, "tier", 1);
        JsonArray reserve = GsonHelper.getAsJsonArray(json, "reserve", null);
        //1.0.4-beta (lot 4) : filons x2 (reserve par defaut 40 000..160 000 ; les fichiers des generateurs portent deja la reserve doublee)
        int min = reserve == null ? 40_000 : reserve.get(0).getAsInt(), max = reserve == null ? 160_000 : reserve.get(1).getAsInt();
        Block outcrop = json.has("outcrop") ? ForgeRegistries.BLOCKS.getValue(ResourceLocation.parse(GsonHelper.getAsString(json, "outcrop"))) : null;
        int color = Integer.parseInt(GsonHelper.getAsString(json, "color", "#808080").substring(1), 16);
        int fallback = Math.max(0, GsonHelper.getAsInt(json, "fallback", 0));
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
        return new DepositType(id, result, material, dimension, tier, Math.min(min, max), Math.max(min, max), outcrop, color, fallback, List.copyOf(biomes));
    }
}
