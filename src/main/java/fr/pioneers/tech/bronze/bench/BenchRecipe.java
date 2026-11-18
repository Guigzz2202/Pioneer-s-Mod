package fr.pioneers.tech.bronze.bench;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.NonNullList;
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
 * Recette d'atelier : un atelier ({@link BenchType}), des outils poses (chacun s'use d'un point, aucun n'est consomme), des ingredients comptes
 * (pris dans les cases de materiaux, toutes confondues), une piece produite. JSON :
 * {@code {"type":"pioneers:bench","bench":"metalworker","tools":[{"tag":...}],"ingredients":[{"ingredient":{...},"count":2}],"result":{...}}}.
 */
public class BenchRecipe implements Recipe<Container> {

    public record Counted(Ingredient ingredient, int count) {
    }

    private final ResourceLocation id;
    private final BenchType bench;
    private final List<Ingredient> tools;
    private final List<Counted> ingredients;
    private final ItemStack result;

    public BenchRecipe(ResourceLocation id, BenchType bench, List<Ingredient> tools, List<Counted> ingredients, ItemStack result) {
        this.id = id;
        this.bench = bench;
        this.tools = List.copyOf(tools);
        this.ingredients = List.copyOf(ingredients);
        this.result = result;
    }

    public BenchType bench() {
        return bench;
    }

    public List<Ingredient> tools() {
        return tools;
    }

    public List<Counted> ingredients() {
        return ingredients;
    }

    public ItemStack result() {
        return result;
    }

    // ------------------------------------------------------------------
    // Verification et fabrication
    // ------------------------------------------------------------------

    /** Index de la case d'outil qui sert chaque outil demande (cases distinctes), ou null s'il en manque un. */
    public int[] matchTools(List<ItemStack> toolSlots) {
        int[] used = new int[tools.size()];
        boolean[] taken = new boolean[toolSlots.size()];
        for (int t = 0; t < tools.size(); t++) {
            used[t] = -1;
            for (int s = 0; s < toolSlots.size(); s++) {
                ItemStack stack = toolSlots.get(s);
                if (!taken[s] && !stack.isEmpty() && tools.get(t).test(stack)) {
                    taken[s] = true;
                    used[t] = s;
                    break;
                }
            }
            if (used[t] < 0) {
                return null;
            }
        }
        return used;
    }

    /** Combien de fois la recette passe avec ces materiaux (sans compter l'usure des outils). */
    public int times(List<ItemStack> materials) {
        int[] left = new int[materials.size()];
        for (int i = 0; i < left.length; i++) {
            left[i] = materials.get(i).getCount();
        }
        int n = 0;
        while (n < 64 && take(materials, left)) {
            n++;
        }
        return n;
    }

    /**
     * 1.3.2 : ingredients du plus precis au plus large (un objet exact avant un tag) ; pour chacun, les cases qu'aucun autre ingredient ne peut
     * prendre d'abord. Avant, un tag large (liens : fibre, ficelle, cuir) pouvait manger le cuir demande ensuite : la recette semblait
     * infaisable selon l'ordre des piles (pince a creuset).
     */
    private List<Counted> ordered() {
        List<Counted> out = new ArrayList<>(ingredients);
        out.sort(Comparator.comparingInt(c -> c.ingredient.getItems().length));
        return out;
    }

    /** Ordre des cases pour l'ingredient {@code k} de {@code order} : celles que les ingredients suivants ne peuvent pas prendre d'abord. */
    private static int[] slotOrder(List<Counted> order, int k, List<ItemStack> materials) {
        int[] idx = new int[materials.size()];
        int p = 0;
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < materials.size(); i++) {
                boolean shared = false;
                for (int j = k + 1; j < order.size() && !shared; j++) {
                    shared = order.get(j).ingredient.test(materials.get(i));
                }
                if (shared == (pass == 1)) {
                    idx[p++] = i;
                }
            }
        }
        return idx;
    }

    /** Retire une fois les ingredients de {@code left} (compteurs par case) ; faux s'il en manque. */
    private boolean take(List<ItemStack> materials, int[] left) {
        int[] copy = left.clone();
        List<Counted> order = ordered();
        for (int k = 0; k < order.size(); k++) {
            Counted c = order.get(k);
            int need = c.count;
            for (int i : slotOrder(order, k, materials)) {
                if (need <= 0) {
                    break;
                }
                if (copy[i] > 0 && c.ingredient.test(materials.get(i))) {
                    int n = Math.min(need, copy[i]);
                    copy[i] -= n;
                    need -= n;
                }
            }
            if (need > 0) {
                return false;
            }
        }
        System.arraycopy(copy, 0, left, 0, left.length);
        return true;
    }

    /**
     * Consomme les ingredients dans les piles (reelles), dans le meme ordre que {@link #take}. 1.0.5-beta (lot 21, retour de Guillaume :
     * « les fabrications qui utilisent les seaux de creosote consomment le seau ») : renvoie les RESTES de fabrication
     * ({@code getCraftingRemainingItem} : le seau vide d'un seau de fluide, comme la grille du jeu), alignes sur {@code materials} (vide :
     * rien) ; {@link #settle} les pose.
     */
    public List<ItemStack> consume(List<ItemStack> materials) {
        List<ItemStack> rest = new ArrayList<>();
        for (int i = 0; i < materials.size(); i++) {
            rest.add(ItemStack.EMPTY);
        }
        List<Counted> order = ordered();
        for (int k = 0; k < order.size(); k++) {
            Counted c = order.get(k);
            int need = c.count;
            for (int i : slotOrder(order, k, materials)) {
                if (need <= 0) {
                    break;
                }
                ItemStack stack = materials.get(i);
                if (!stack.isEmpty() && c.ingredient.test(stack)) {
                    int n = Math.min(need, stack.getCount());
                    if (stack.hasCraftingRemainingItem()) {
                        ItemStack r = stack.getCraftingRemainingItem().copy();
                        if (!r.isEmpty()) {
                            r.setCount(r.getCount() * n);
                            ItemStack had = rest.get(i);
                            if (had.isEmpty()) {
                                rest.set(i, r);
                            } else if (ItemStack.isSameItemSameTags(had, r)) {
                                had.grow(r.getCount());
                            }
                        }
                    }
                    stack.shrink(n);
                    need -= n;
                }
            }
        }
        return rest;
    }

    /**
     * 1.0.5-beta (lot 21) : pose les restes de fabrication : dans la case qui s'est videe (comme la grille du jeu : le seau vide reprend la
     * place du seau plein), sinon ils sont renvoyes (a donner au joueur ou a lacher). Modifie {@code materials} ; renvoie ce qui n'a pas ete pose.
     */
    public static List<ItemStack> settle(List<ItemStack> materials, List<ItemStack> rest) {
        List<ItemStack> left = new ArrayList<>();
        for (int i = 0; i < rest.size(); i++) {
            ItemStack r = rest.get(i);
            if (r.isEmpty()) {
                continue;
            }
            if (i < materials.size() && materials.get(i).isEmpty() && r.getCount() <= r.getMaxStackSize()) {
                materials.set(i, r.copy());
            } else {
                left.add(r.copy());
            }
        }
        return left;
    }

    /** 1.0.5-beta (lot 21) : restes de fabrication d'UNE operation (JEI : le seau vide rendu). */
    public List<ItemStack> remainders() {
        List<ItemStack> out = new ArrayList<>();
        for (Counted c : ingredients) {
            for (ItemStack s : c.ingredient.getItems()) {
                if (s.hasCraftingRemainingItem() && !s.getCraftingRemainingItem().isEmpty()) {
                    ItemStack r = s.getCraftingRemainingItem().copy();
                    r.setCount(r.getCount() * c.count);
                    out.add(r);
                    break;
                }
            }
        }
        return out;
    }

    // ------------------------------------------------------------------
    // Recipe
    // ------------------------------------------------------------------

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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        ingredients.forEach(c -> list.add(c.ingredient));
        return list;
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiBronze.BENCH_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiBronze.BENCH.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<BenchRecipe> {

        @NotNull
        @Override
        public BenchRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            BenchType bench = BenchType.byKey(GsonHelper.getAsString(json, "bench"));
            List<Ingredient> tools = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "tools", new com.google.gson.JsonArray())) {
                tools.add(Ingredient.fromJson(e));
            }
            List<Counted> ingredients = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "ingredients")) {
                JsonObject o = e.getAsJsonObject();
                ingredients.add(new Counted(Ingredient.fromJson(GsonHelper.getNonNull(o, "ingredient")), GsonHelper.getAsInt(o, "count", 1)));
            }
            return new BenchRecipe(id, bench, tools, ingredients, ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result")));
        }

        @Override
        public BenchRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            BenchType bench = buffer.readEnum(BenchType.class);
            List<Ingredient> tools = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                tools.add(Ingredient.fromNetwork(buffer));
            }
            List<Counted> ingredients = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                ingredients.add(new Counted(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            }
            return new BenchRecipe(id, bench, tools, ingredients, buffer.readItem());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull BenchRecipe recipe) {
            buffer.writeEnum(recipe.bench);
            buffer.writeVarInt(recipe.tools.size());
            recipe.tools.forEach(t -> t.toNetwork(buffer));
            buffer.writeVarInt(recipe.ingredients.size());
            for (Counted c : recipe.ingredients) {
                c.ingredient.toNetwork(buffer);
                buffer.writeVarInt(c.count);
            }
            buffer.writeItem(recipe.result);
        }
    }
}
