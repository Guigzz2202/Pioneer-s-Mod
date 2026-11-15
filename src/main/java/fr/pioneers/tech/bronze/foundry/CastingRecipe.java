package fr.pioneers.tech.bronze.foundry;

import com.google.gson.JsonObject;
import fr.pioneers.core.registries.PiBronze;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Coulee : un moule, un metal (ou alliage) en fusion, une quantite (mB) ; la piece une fois figee. Champ facultatif {@code cool} (forge
 * primaire 0.21) : duree de refroidissement dans le moule, en ticks (sinon {@code 40 + amount / 4}). Servi aussi par le moule pose
 * ({@code pioneers:placed_mold}) et la table de coulee.
 */
public class CastingRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final Item mold;
    private final String metal;
    private final int amount;
    private final ItemStack result;
    private final int cool;

    public CastingRecipe(ResourceLocation id, Item mold, String metal, int amount, ItemStack result) {
        this(id, mold, metal, amount, result, 0);
    }

    /** @param cool refroidissement en ticks (0 = duree par defaut) */
    public CastingRecipe(ResourceLocation id, Item mold, String metal, int amount, ItemStack result, int cool) {
        this.id = id;
        this.mold = mold;
        this.metal = metal;
        this.amount = amount;
        this.result = result;
        this.cool = cool;
    }

    public Item mold() {
        return mold;
    }

    public String metal() {
        return metal;
    }

    public int amount() {
        return amount;
    }

    public ItemStack result() {
        return result;
    }

    /** Duree de refroidissement (ticks) : plus de metal, plus long ; les metaux a haut point de fusion figent plus vite. */
    public int coolTicks() {
        return cool > 0 ? cool : 40 + amount / 4;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return false;
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
        return PiBronze.CASTING_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiBronze.CASTING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<CastingRecipe> {

        @NotNull
        @Override
        public CastingRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            Item mold = BuiltInRegistries.ITEM.get(ResourceLocation.parse(GsonHelper.getAsString(json, "mold")));
            return new CastingRecipe(id, mold, GsonHelper.getAsString(json, "metal"), GsonHelper.getAsInt(json, "amount", Metals.INGOT),
                  ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")), GsonHelper.getAsInt(json, "cool", 0));
        }

        @Override
        public CastingRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            Item mold = BuiltInRegistries.ITEM.byId(buffer.readVarInt());
            return new CastingRecipe(id, mold, buffer.readUtf(), buffer.readVarInt(), buffer.readItem(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull CastingRecipe recipe) {
            buffer.writeVarInt(BuiltInRegistries.ITEM.getId(recipe.mold));
            buffer.writeUtf(recipe.metal);
            buffer.writeVarInt(recipe.amount);
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.cool);
        }
    }
}
