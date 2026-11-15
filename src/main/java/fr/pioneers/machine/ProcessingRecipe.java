package fr.pioneers.machine;

import com.google.gson.JsonObject;
import fr.pioneers.recipe.PiRecipes;
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
 * Recette simple d'une machine du kit (broyeur, compresseur) : {@code count} objets d'entree donnent un resultat.
 * <pre>
 * {"type": "pioneers:crushing", "input": {"tag": "forge:ores/iron"}, "count": 1, "result": {"item": "pioneers:iron_dust", "count": 2}}
 * </pre>
 * MAJ Recettes (lot 8a) : le compacteur (compression) peut demander un LIANT, dans sa case a lui :
 * {@code "binder": {"item": "minecraft:clay_ball"}, "binder_count": 1} (bentonite des boulettes, brai des briquettes et des electrodes, chaux
 * des parpaings). Le four electrique utilise les recettes de cuisson vanilla.
 */
public class ProcessingRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final RecipeType<ProcessingRecipe> type;
    private final RecipeSerializer<ProcessingRecipe> serializer;
    private final Ingredient input;
    private final int count;
    private final ItemStack result;
    /** MAJ Recettes (lot 8a) : liant du compacteur ({@link Ingredient#EMPTY} : aucun) et sa quantite. */
    private final Ingredient binder;
    private final int binderCount;

    public ProcessingRecipe(ResourceLocation id, RecipeType<ProcessingRecipe> type, RecipeSerializer<ProcessingRecipe> serializer, Ingredient input, int count,
          ItemStack result) {
        this(id, type, serializer, input, count, result, Ingredient.EMPTY, 0);
    }

    public ProcessingRecipe(ResourceLocation id, RecipeType<ProcessingRecipe> type, RecipeSerializer<ProcessingRecipe> serializer, Ingredient input, int count,
          ItemStack result, Ingredient binder, int binderCount) {
        this.id = id;
        this.type = type;
        this.serializer = serializer;
        this.input = input;
        this.count = Math.max(1, count);
        this.result = result;
        this.binder = binder;
        this.binderCount = binder.isEmpty() ? 0 : Math.max(1, binderCount);
    }

    /** MAJ Recettes (lot 8a) : la recette demande un liant (case du liant du compacteur). */
    public boolean hasBinder() {
        return binderCount > 0;
    }

    public Ingredient getBinder() {
        return binder;
    }

    public int getBinderCount() {
        return binderCount;
    }

    /** L'objet convient comme liant de cette recette (sans regarder la quantite). */
    public boolean acceptsBinder(ItemStack stack) {
        return hasBinder() && !stack.isEmpty() && binder.test(stack);
    }

    public Ingredient getInput() {
        return input;
    }

    public int getCount() {
        return count;
    }

    public ItemStack getResult() {
        return result;
    }

    /** L'objet convient (sans regarder la quantite). */
    public boolean accepts(ItemStack stack) {
        return input.test(stack);
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        ItemStack stack = container.getItem(0);
        return input.test(stack) && stack.getCount() >= count;
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
        return serializer;
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return type;
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    /** Serialiseur commun aux recettes simples ; le type est donne a la creation (broyage ou compression). */
    public static class Serializer implements RecipeSerializer<ProcessingRecipe> {

        private final boolean crushing;

        public Serializer(boolean crushing) {
            this.crushing = crushing;
        }

        private RecipeType<ProcessingRecipe> type() {
            return crushing ? PiRecipes.CRUSHING.get() : PiRecipes.COMPRESSING.get();
        }

        private RecipeSerializer<ProcessingRecipe> self() {
            return crushing ? PiRecipes.CRUSHING_SERIALIZER.get() : PiRecipes.COMPRESSING_SERIALIZER.get();
        }

        @NotNull
        @Override
        public ProcessingRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            Ingredient input = Ingredient.fromJson(GsonHelper.getNonNull(json, "input"));
            int count = GsonHelper.getAsInt(json, "count", 1);
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            Ingredient binder = json.has("binder") ? Ingredient.fromJson(json.get("binder")) : Ingredient.EMPTY;
            return new ProcessingRecipe(id, type(), self(), input, count, result, binder, GsonHelper.getAsInt(json, "binder_count", 1));
        }

        @Override
        public ProcessingRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            Ingredient input = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            ItemStack result = buffer.readItem();
            int binderCount = buffer.readVarInt();
            Ingredient binder = binderCount > 0 ? Ingredient.fromNetwork(buffer) : Ingredient.EMPTY;
            return new ProcessingRecipe(id, type(), self(), input, count, result, binder, binderCount);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull ProcessingRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeVarInt(recipe.count);
            buffer.writeItem(recipe.result);
            buffer.writeVarInt(recipe.binderCount);
            if (recipe.binderCount > 0) {
                recipe.binder.toNetwork(buffer);
            }
        }
    }
}
