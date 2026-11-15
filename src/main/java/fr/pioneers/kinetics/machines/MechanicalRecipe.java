package fr.pioneers.kinetics.machines;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.recipe.PiRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.RandomSource;
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
 * Recette d'une machine mecanique : le travail se compte en TOURS de l'arbre de la machine (un coup de marteau = un tour, une passe de tamis =
 * un tour...). Plus la machine tourne vite, plus elle va vite. Resultats a probabilite ({@code chance}) ; le tamisage beneficie de la maille.
 * <pre>
 * {"type": "pioneers:milling", "input": {"tag": "forge:crushed_ores/iron"}, "count": 1, "turns": 4,
 *  "results": [{"item": "pioneers:iron_dust"}, {"item": "pioneers:iron_dust", "chance": 0.1}]}
 * </pre>
 * Tamisage : {@code "mesh": 1} = maille minimale (1 fibre, 2 cuivre, 3 bronze, 4 acier) d'un resultat.
 */
public class MechanicalRecipe implements Recipe<Container> {

    public enum Kind {
        MILLING, SIFTING, JAW_CRUSHING, SAWING, TURNING;

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public record Result(ItemStack stack, float chance, int mesh) {
    }

    private final ResourceLocation id;
    private final Kind kind;
    private final Ingredient input;
    private final int count;
    private final float turns;
    private final List<Result> results;

    public MechanicalRecipe(ResourceLocation id, Kind kind, Ingredient input, int count, float turns, List<Result> results) {
        this.id = id;
        this.kind = kind;
        this.input = input;
        this.count = Math.max(1, count);
        this.turns = Math.max(0.25F, turns);
        this.results = results;
    }

    public Kind kind() {
        return kind;
    }

    public Ingredient input() {
        return input;
    }

    public int count() {
        return count;
    }

    public float turns() {
        return turns;
    }

    public List<Result> results() {
        return results;
    }

    public boolean accepts(ItemStack stack) {
        return input.test(stack);
    }

    /** Tirage des resultats (maille : multiplicateur des chances et second tirage). */
    public List<ItemStack> roll(RandomSource random, int mesh, float chanceBonus, float secondRoll) {
        List<ItemStack> out = new ArrayList<>();
        for (Result result : results) {
            if (result.mesh > mesh) {
                continue;
            }
            float chance = result.chance >= 1 ? 1 : Math.min(1, result.chance * chanceBonus);
            int rolls = result.chance < 1 && random.nextFloat() < secondRoll ? 2 : 1;
            for (int i = 0; i < rolls; i++) {
                if (random.nextFloat() < chance) {
                    out.add(result.stack.copy());
                }
            }
        }
        return out;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        ItemStack stack = container.getItem(0);
        return input.test(stack) && stack.getCount() >= count;
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess access) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack.copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @NotNull
    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack;
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiRecipes.MECHANICAL_SERIALIZERS.get(kind).get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiRecipes.MECHANICAL.get(kind).get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<MechanicalRecipe> {

        private final Kind kind;

        public Serializer(Kind kind) {
            this.kind = kind;
        }

        @NotNull
        @Override
        public MechanicalRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            Ingredient input = Ingredient.fromJson(GsonHelper.getNonNull(json, "input"));
            int count = GsonHelper.getAsInt(json, "count", 1);
            float turns = GsonHelper.getAsFloat(json, "turns", 4);
            List<Result> results = new ArrayList<>();
            for (JsonElement element : GsonHelper.getAsJsonArray(json, "results")) {
                JsonObject o = element.getAsJsonObject();
                results.add(new Result(ShapedRecipe.itemStackFromJson(o), GsonHelper.getAsFloat(o, "chance", 1), GsonHelper.getAsInt(o, "mesh", 0)));
            }
            return new MechanicalRecipe(id, kind, input, count, turns, results);
        }

        @Override
        public MechanicalRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            Ingredient input = Ingredient.fromNetwork(buffer);
            int count = buffer.readVarInt();
            float turns = buffer.readFloat();
            int n = buffer.readVarInt();
            List<Result> results = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                results.add(new Result(buffer.readItem(), buffer.readFloat(), buffer.readVarInt()));
            }
            return new MechanicalRecipe(id, kind, input, count, turns, results);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull MechanicalRecipe recipe) {
            recipe.input.toNetwork(buffer);
            buffer.writeVarInt(recipe.count);
            buffer.writeFloat(recipe.turns);
            buffer.writeVarInt(recipe.results.size());
            for (Result result : recipe.results) {
                buffer.writeItem(result.stack);
                buffer.writeFloat(result.chance);
                buffer.writeVarInt(result.mesh);
            }
        }
    }
}
