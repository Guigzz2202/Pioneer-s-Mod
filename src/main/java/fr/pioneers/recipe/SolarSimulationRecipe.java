package fr.pioneers.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Production d'une planete simulee dans le Simulateur du systeme solaire, a chaque cycle.
 * <pre>
 * {"type": "pioneers:solar_simulation",
 *  "planet": {"item": "pioneers:simulated_mars"},
 *  "duration": 600,
 *  "outputs": [{"item": "pioneers:marsium_ingot", "count": 8}, {"item": "minecraft:red_sand", "count": 32}]}
 * </pre>
 * La planete n'est pas consommee.
 */
public class SolarSimulationRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Ingredient planet;
    private final List<ItemStack> outputs;
    private final int duration;

    public SolarSimulationRecipe(ResourceLocation id, Ingredient planet, List<ItemStack> outputs, int duration) {
        this.id = id;
        this.planet = planet;
        this.outputs = outputs;
        this.duration = duration;
    }

    public Ingredient getPlanet() {
        return planet;
    }

    public List<ItemStack> getOutputs() {
        return outputs;
    }

    public int getDuration() {
        return duration;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return false;
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess access) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
    }

    @NotNull
    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        return outputs.isEmpty() ? ItemStack.EMPTY : outputs.get(0);
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiRecipes.SOLAR_SIMULATION_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiRecipes.SOLAR_SIMULATION.get();
    }

    public static class Serializer implements RecipeSerializer<SolarSimulationRecipe> {

        @NotNull
        @Override
        public SolarSimulationRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            JsonArray array = GsonHelper.getAsJsonArray(json, "outputs");
            List<ItemStack> outputs = new ArrayList<>();
            for (int i = 0; i < array.size(); i++) {
                outputs.add(ShapedRecipe.itemStackFromJson(GsonHelper.convertToJsonObject(array.get(i), "outputs[" + i + "]")));
            }
            return new SolarSimulationRecipe(id, Ingredient.fromJson(json.get("planet"), false), outputs,
                  Math.max(20, GsonHelper.getAsInt(json, "duration", 600)));
        }

        @Override
        public SolarSimulationRecipe fromNetwork(@NotNull ResourceLocation id, FriendlyByteBuf buffer) {
            Ingredient planet = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            List<ItemStack> outputs = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                outputs.add(buffer.readItem());
            }
            return new SolarSimulationRecipe(id, planet, outputs, buffer.readVarInt());
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, SolarSimulationRecipe recipe) {
            recipe.planet.toNetwork(buffer);
            buffer.writeVarInt(recipe.outputs.size());
            for (ItemStack stack : recipe.outputs) {
                buffer.writeItem(stack);
            }
            buffer.writeVarInt(recipe.duration);
        }
    }
}
