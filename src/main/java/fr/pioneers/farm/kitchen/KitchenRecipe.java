package fr.pioneers.farm.kitchen;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.farm.PiKitchen;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
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
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Recette d'un poste de cuisine ou de transformation (format commun a tous les postes, voir {@link Station}) :
 * <pre>{ingredients: [{ingredient, count}], fluid: {fluid|tag, amount}, time, strokes, results: [{item, count, chance}],
 *  fluid_result: {fluid, amount}, smoke, source}</pre>
 * Les ingredients sont sans ordre ; un poste a plusieurs cases attend EXACTEMENT les ingredients de la recette (rien de plus).
 * {@code smoke} : sechoir au-dessus d'un feu ; {@code source} : le produit garde l'objet d'origine (NBT {@code Source}, confitures, bocaux).
 */
public class KitchenRecipe implements Recipe<Container> {

    /** Ingredient compte. */
    public record Counted(Ingredient ingredient, int count) {
    }

    /** Fluide demande : un fluide precis ou un tag. */
    public record FluidIn(@Nullable Fluid fluid, @Nullable TagKey<Fluid> tag, int amount) {

        public boolean test(FluidStack stack) {
            if (stack.isEmpty() || stack.getAmount() < amount) {
                return false;
            }
            return fluid != null ? stack.getFluid().isSame(fluid) : tag != null && stack.getFluid().is(tag);
        }

        /** Fluides possibles (JEI). */
        public List<FluidStack> stacks() {
            List<FluidStack> out = new ArrayList<>();
            if (fluid != null) {
                out.add(new FluidStack(fluid, amount));
            } else if (tag != null) {
                for (Fluid f : ForgeRegistries.FLUIDS) {
                    if (f.isSource(f.defaultFluidState()) && f.is(tag)) {
                        out.add(new FluidStack(f, amount));
                    }
                }
            }
            return out;
        }
    }

    /** Produit : objet, chance. */
    public record Result(ItemStack stack, float chance) {
    }

    private final ResourceLocation id;
    private final Station station;
    private final List<Counted> ingredients;
    @Nullable
    private final FluidIn fluid;
    private final int time, strokes;
    private final List<Result> results;
    private final FluidStack fluidResult;
    private final boolean smoke, source;

    public KitchenRecipe(ResourceLocation id, Station station, List<Counted> ingredients, @Nullable FluidIn fluid, int time, int strokes,
          List<Result> results, FluidStack fluidResult, boolean smoke, boolean source) {
        this.id = id;
        this.station = station;
        this.ingredients = ingredients;
        this.fluid = fluid;
        this.time = time;
        this.strokes = strokes;
        this.results = results;
        this.fluidResult = fluidResult;
        this.smoke = smoke;
        this.source = source;
    }

    public Station station() {
        return station;
    }

    public List<Counted> ingredients() {
        return ingredients;
    }

    @Nullable
    public FluidIn fluid() {
        return fluid;
    }

    public int time() {
        return time;
    }

    public int strokes() {
        return strokes;
    }

    public List<Result> results() {
        return results;
    }

    public FluidStack fluidResult() {
        return fluidResult;
    }

    public boolean smoke() {
        return smoke;
    }

    public boolean source() {
        return source;
    }

    /** Une seule entree (planche, poele, four, sechoir) : l'objet convient-il ? */
    public boolean matchesSingle(ItemStack stack) {
        return ingredients.size() == 1 && ingredients.get(0).ingredient().test(stack) && stack.getCount() >= ingredients.get(0).count();
    }

    /** Le premier ingredient accepte-t-il cet objet (entree d'un poste a une case) ? */
    public boolean accepts(ItemStack stack) {
        for (Counted c : ingredients) {
            if (c.ingredient().test(stack)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Les objets donnes forment-ils EXACTEMENT les ingredients (chaque objet sert un ingredient, quantites egales, rien en trop) ? Recherche
     * d'affectation par retour arriere (au plus 6 ingredients).
     */
    public boolean matchesExact(List<ItemStack> contents) {
        List<ItemStack> units = new ArrayList<>();
        int total = 0;
        for (ItemStack s : contents) {
            if (!s.isEmpty()) {
                units.add(s);
                total += s.getCount();
            }
        }
        int need = 0;
        for (Counted c : ingredients) {
            need += c.count();
        }
        if (total != need) {
            return false;
        }
        int[] left = new int[ingredients.size()];
        for (int i = 0; i < left.length; i++) {
            left[i] = ingredients.get(i).count();
        }
        return assign(units, 0, left);
    }

    private boolean assign(List<ItemStack> units, int index, int[] left) {
        if (index == units.size()) {
            for (int l : left) {
                if (l != 0) {
                    return false;
                }
            }
            return true;
        }
        ItemStack stack = units.get(index);
        for (int i = 0; i < left.length; i++) {
            if (left[i] >= stack.getCount() && ingredients.get(i).ingredient().test(stack)) {
                left[i] -= stack.getCount();
                if (assign(units, index + 1, left)) {
                    return true;
                }
                left[i] += stack.getCount();
            }
        }
        return false;
    }

    /** Les objets donnes peuvent-ils encore mener a cette recette (aucun objet etranger, quantites non depassees) ? */
    public boolean couldBecome(List<ItemStack> contents) {
        int[] left = new int[ingredients.size()];
        for (int i = 0; i < left.length; i++) {
            left[i] = ingredients.get(i).count();
        }
        outer:
        for (ItemStack s : contents) {
            if (s.isEmpty()) {
                continue;
            }
            int n = s.getCount();
            for (int i = 0; i < left.length && n > 0; i++) {
                if (left[i] > 0 && ingredients.get(i).ingredient().test(s)) {
                    int take = Math.min(left[i], n);
                    left[i] -= take;
                    n -= take;
                }
            }
            if (n > 0) {
                return false;
            }
            continue outer;
        }
        return true;
    }

    public boolean fluidOk(FluidStack stack) {
        return fluid == null || fluid.test(stack);
    }

    /** Produits tires (chances), avec le NBT {@code Source} si demande. */
    public List<ItemStack> roll(RandomSource random, @Nullable ItemStack sourceItem) {
        List<ItemStack> out = new ArrayList<>();
        for (Result r : results) {
            if (r.chance() >= 1 || random.nextFloat() < r.chance()) {
                ItemStack s = r.stack().copy();
                if (source && sourceItem != null && !sourceItem.isEmpty()) {
                    s.getOrCreateTag().putString("Source", ForgeRegistries.ITEMS.getKey(sourceItem.getItem()).toString());
                }
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        List<ItemStack> list = new ArrayList<>();
        for (int i = 0; i < container.getContainerSize(); i++) {
            list.add(container.getItem(i));
        }
        return matchesExact(list);
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess access) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack().copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    @NotNull
    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        return results.isEmpty() ? ItemStack.EMPTY : results.get(0).stack();
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiKitchen.SERIALIZERS.get(station).get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiKitchen.TYPES.get(station).get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<KitchenRecipe> {

        private final Station station;

        public Serializer(Station station) {
            this.station = station;
        }

        @NotNull
        @Override
        public KitchenRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            List<Counted> ingredients = new ArrayList<>();
            if (json.has("ingredients")) {
                for (JsonElement e : GsonHelper.getAsJsonArray(json, "ingredients")) {
                    JsonObject o = e.getAsJsonObject();
                    ingredients.add(new Counted(Ingredient.fromJson(o.get("ingredient")), GsonHelper.getAsInt(o, "count", 1)));
                }
            }
            FluidIn fluid = null;
            if (json.has("fluid")) {
                JsonObject f = GsonHelper.getAsJsonObject(json, "fluid");
                int amount = GsonHelper.getAsInt(f, "amount", 1000);
                if (f.has("tag")) {
                    fluid = new FluidIn(null, TagKey.create(Registries.FLUID, ResourceLocation.parse(GsonHelper.getAsString(f, "tag"))), amount);
                } else {
                    fluid = new FluidIn(ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(GsonHelper.getAsString(f, "fluid"))), null, amount);
                }
            }
            List<Result> results = new ArrayList<>();
            if (json.has("results")) {
                JsonArray arr = GsonHelper.getAsJsonArray(json, "results");
                for (JsonElement e : arr) {
                    JsonObject o = e.getAsJsonObject();
                    results.add(new Result(ShapedRecipe.itemStackFromJson(o), GsonHelper.getAsFloat(o, "chance", 1F)));
                }
            }
            FluidStack fluidResult = FluidStack.EMPTY;
            if (json.has("fluid_result")) {
                JsonObject f = GsonHelper.getAsJsonObject(json, "fluid_result");
                Fluid fl = ForgeRegistries.FLUIDS.getValue(ResourceLocation.parse(GsonHelper.getAsString(f, "fluid")));
                if (fl != null && fl != Fluids.EMPTY) {
                    fluidResult = new FluidStack(fl, GsonHelper.getAsInt(f, "amount", 1000));
                }
            }
            return new KitchenRecipe(id, station, ingredients, fluid, GsonHelper.getAsInt(json, "time", 200), GsonHelper.getAsInt(json, "strokes", 0),
                  results, fluidResult, GsonHelper.getAsBoolean(json, "smoke", false), GsonHelper.getAsBoolean(json, "source", false));
        }

        @Override
        public KitchenRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            int n = buffer.readVarInt();
            List<Counted> ingredients = new ArrayList<>();
            for (int i = 0; i < n; i++) {
                ingredients.add(new Counted(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            }
            FluidIn fluid = null;
            int kind = buffer.readByte();
            if (kind == 1) {
                fluid = new FluidIn(ForgeRegistries.FLUIDS.getValue(buffer.readResourceLocation()), null, buffer.readVarInt());
            } else if (kind == 2) {
                fluid = new FluidIn(null, TagKey.create(Registries.FLUID, buffer.readResourceLocation()), buffer.readVarInt());
            }
            int time = buffer.readVarInt(), strokes = buffer.readVarInt();
            int r = buffer.readVarInt();
            List<Result> results = new ArrayList<>();
            for (int i = 0; i < r; i++) {
                results.add(new Result(buffer.readItem(), buffer.readFloat()));
            }
            FluidStack fluidResult = FluidStack.readFromPacket(buffer);
            return new KitchenRecipe(id, station, ingredients, fluid, time, strokes, results, fluidResult, buffer.readBoolean(), buffer.readBoolean());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull KitchenRecipe recipe) {
            buffer.writeVarInt(recipe.ingredients.size());
            for (Counted c : recipe.ingredients) {
                c.ingredient().toNetwork(buffer);
                buffer.writeVarInt(c.count());
            }
            if (recipe.fluid == null) {
                buffer.writeByte(0);
            } else if (recipe.fluid.fluid() != null) {
                buffer.writeByte(1);
                buffer.writeResourceLocation(ForgeRegistries.FLUIDS.getKey(recipe.fluid.fluid()));
                buffer.writeVarInt(recipe.fluid.amount());
            } else {
                buffer.writeByte(2);
                buffer.writeResourceLocation(recipe.fluid.tag().location());
                buffer.writeVarInt(recipe.fluid.amount());
            }
            buffer.writeVarInt(recipe.time);
            buffer.writeVarInt(recipe.strokes);
            buffer.writeVarInt(recipe.results.size());
            for (Result r : recipe.results) {
                buffer.writeItem(r.stack());
                buffer.writeFloat(r.chance());
            }
            recipe.fluidResult.writeToPacket(buffer);
            buffer.writeBoolean(recipe.smoke);
            buffer.writeBoolean(recipe.source);
        }
    }
}
