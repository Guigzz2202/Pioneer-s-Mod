package fr.pioneers.bronze.machines;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import fr.pioneers.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.registries.PiBronze;
import java.util.ArrayList;
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
 * Recette d'une machine a vapeur : une ou deux entrees comptees (dans n'importe quel ordre), un produit, un sous-produit facultatif avec sa
 * chance, une duree (ticks ; 3 mB de vapeur par tick, 1.0.2-beta lot 3), de l'eau (melangeur, champ {@code water} en mB, 1.0.3-beta lot 11b),
 * 1.0.5-beta (lot 38) : un tier global minimal ({@code min_tier}, 1 par defaut). JSON : {@code {"type":"pioneers:steam_processing","machine":"macerator","inputs":[{"ingredient":{...},
 * "count":1}],"result":{...},"byproduct":{"item":...,"count":1},"chance":0.25,"time":200}}.
 */
public class SteamRecipe implements Recipe<Container> {

    private final ResourceLocation id;
    private final SteamMachine machine;
    private final List<Counted> inputs;
    private final ItemStack result, byproduct;
    private final float chance;
    private final int time;
    /** 1.0.3-beta (lot 11b) : eau prise au reservoir du melangeur a vapeur (mB ; 0 = aucune). */
    private final int water;
    /** 1.0.5-beta (lot 38) : tier global minimal de la recette (champ {@code min_tier}, 1 par defaut : machine a vapeur). */
    private final int minTier;

    public SteamRecipe(ResourceLocation id, SteamMachine machine, List<Counted> inputs, ItemStack result, ItemStack byproduct, float chance, int time) {
        this(id, machine, inputs, result, byproduct, chance, time, 0);
    }

    public SteamRecipe(ResourceLocation id, SteamMachine machine, List<Counted> inputs, ItemStack result, ItemStack byproduct, float chance, int time,
          int water) {
        this(id, machine, inputs, result, byproduct, chance, time, water, 1);
    }

    public SteamRecipe(ResourceLocation id, SteamMachine machine, List<Counted> inputs, ItemStack result, ItemStack byproduct, float chance, int time,
          int water, int minTier) {
        this.minTier = Math.max(0, Math.min(6, minTier));
        this.water = Math.max(0, water);
        this.id = id;
        this.machine = machine;
        this.inputs = List.copyOf(inputs);
        this.result = result;
        this.byproduct = byproduct;
        this.chance = chance;
        this.time = time;
    }

    public SteamMachine machine() {
        return machine;
    }

    public List<Counted> inputs() {
        return inputs;
    }

    public ItemStack result() {
        return result;
    }

    public ItemStack byproduct() {
        return byproduct;
    }

    public float chance() {
        return chance;
    }

    public int time() {
        return time;
    }

    /** Eau demandee (mB) : melangeur a vapeur seulement. */
    public int water() {
        return water;
    }

    /** 1.0.5-beta (lot 38) : tier global minimal (1 : machine a vapeur ; la famille la fait aussi aux tiers au-dessus). */
    public int minTier() {
        return minTier;
    }

    /**
     * Case utilisee par chaque entree (cases distinctes, quantite suffisante), ou null si la recette ne s'applique pas a ces piles.
     */
    public int[] match(List<ItemStack> slots) {
        int[] used = new int[inputs.size()];
        boolean[] taken = new boolean[slots.size()];
        for (int i = 0; i < inputs.size(); i++) {
            used[i] = -1;
            for (int s = 0; s < slots.size(); s++) {
                ItemStack stack = slots.get(s);
                if (!taken[s] && !stack.isEmpty() && inputs.get(i).ingredient().test(stack) && stack.getCount() >= inputs.get(i).count()) {
                    taken[s] = true;
                    used[i] = s;
                    break;
                }
            }
            if (used[i] < 0) {
                return null;
            }
        }
        return used;
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
    public NonNullList<Ingredient> getIngredients() {
        NonNullList<Ingredient> list = NonNullList.create();
        inputs.forEach(c -> list.add(c.ingredient()));
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
        return PiBronze.STEAM_PROCESSING_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiBronze.STEAM_PROCESSING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<SteamRecipe> {

        @NotNull
        @Override
        public SteamRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            SteamMachine machine = SteamMachine.byKey(GsonHelper.getAsString(json, "machine"));
            List<Counted> inputs = new ArrayList<>();
            for (JsonElement e : GsonHelper.getAsJsonArray(json, "inputs")) {
                JsonObject o = e.getAsJsonObject();
                inputs.add(new Counted(Ingredient.fromJson(GsonHelper.getNonNull(o, "ingredient")), GsonHelper.getAsInt(o, "count", 1)));
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            ItemStack byproduct = json.has("byproduct") ? ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "byproduct")) : ItemStack.EMPTY;
            return new SteamRecipe(id, machine, inputs, result, byproduct, GsonHelper.getAsFloat(json, "chance", 1F), GsonHelper.getAsInt(json, "time", 200),
                  GsonHelper.getAsInt(json, "water", 0), GsonHelper.getAsInt(json, "min_tier", 1));
        }

        @Override
        public SteamRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            SteamMachine machine = buffer.readEnum(SteamMachine.class);
            List<Counted> inputs = new ArrayList<>();
            for (int i = buffer.readVarInt(); i > 0; i--) {
                inputs.add(new Counted(Ingredient.fromNetwork(buffer), buffer.readVarInt()));
            }
            return new SteamRecipe(id, machine, inputs, buffer.readItem(), buffer.readItem(), buffer.readFloat(), buffer.readVarInt(),
                  buffer.readVarInt(), buffer.readVarInt());
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull SteamRecipe recipe) {
            buffer.writeEnum(recipe.machine);
            buffer.writeVarInt(recipe.inputs.size());
            for (Counted c : recipe.inputs) {
                c.ingredient().toNetwork(buffer);
                buffer.writeVarInt(c.count());
            }
            buffer.writeItem(recipe.result);
            buffer.writeItem(recipe.byproduct);
            buffer.writeFloat(recipe.chance);
            buffer.writeVarInt(recipe.time);
            buffer.writeVarInt(recipe.water);
            buffer.writeVarInt(recipe.minTier);
        }
    }
}
