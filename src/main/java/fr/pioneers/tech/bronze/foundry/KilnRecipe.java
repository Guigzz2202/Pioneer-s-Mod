package fr.pioneers.tech.bronze.foundry;

import com.google.gson.JsonObject;
import fr.pioneers.core.registries.PiBronze;
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

/** Cuisson au four de potier : une piece crue, la temperature a tenir (degres C), la duree (ticks) a cette temperature, la piece cuite. */
public class KilnRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Ingredient input;
    private final int temperature, time;
    private final ItemStack result;

    public KilnRecipe(ResourceLocation id, Ingredient input, int temperature, int time, ItemStack result) {
        this.id = id;
        this.input = input;
        this.temperature = temperature;
        this.time = time;
        this.result = result;
    }

    public Ingredient input() {
        return input;
    }

    public int temperature() {
        return temperature;
    }

    public int time() {
        return time;
    }

    public ItemStack result() {
        return result;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return input.test(container.getItem(0));
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess access) {
        return result.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @NotNull
    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        return result;
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiBronze.KILN_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiBronze.KILN.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<KilnRecipe> {

        @NotNull
        @Override
        public KilnRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            return new KilnRecipe(id, Ingredient.fromJson(GsonHelper.getNonNull(json, "input")), GsonHelper.getAsInt(json, "temperature", 700),
                  GsonHelper.getAsInt(json, "time", 200), ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
        }

        @Override
        public KilnRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            return new KilnRecipe(id, Ingredient.fromNetwork(buffer), buffer.readVarInt(), buffer.readVarInt(), buffer.readItem());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull KilnRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeVarInt(recipe.temperature);
            buffer.writeVarInt(recipe.time);
            buffer.writeItem(recipe.result);
        }
    }
}
