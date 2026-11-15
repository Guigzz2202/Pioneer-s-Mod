package fr.pioneers.recipe;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Recette d'assemblage (data-driven, modifiable par datapack ou KubeJS) :
 * <pre>
 * {
 *   "type": "pioneers:assembly",
 *   "station": "rocket",                       // rocket, rover, module ou equipment
 *   "pattern": [".WW.......", "AWPPPPPP..", ...],  // jusqu'a 5 lignes de 10 cases, '.' ou ' ' = case vide
 *   "key": {"W": {"item": "pioneers:rocket_wing_tier_1"}, "C": {"tag": "forge:circuits/basic"}},
 *   "result": {"item": "pioneers:rocket_tier_1", "count": 1},
 *   "energy": 20000000,                        // FE pour tout l'assemblage
 *   "duration": 400,                           // ticks
 *   "blueprint": "pioneers:rocket_blueprint_tier_2",   // facultatif : plan pose (non consomme)
 *   "research": "blueprint_t2"                 // facultatif : recherche a terminer
 * }
 * </pre>
 * Chaque case du motif attend son ingredient ; tout est consomme a la fin de l'assemblage.
 */
public class AssemblyRecipe implements Recipe<Container> {

    public static final int ROWS = 5, COLUMNS = 10, CELLS = ROWS * COLUMNS;

    private final ResourceLocation id;
    private final AssemblyStation station;
    private final Ingredient[] cells;
    private final ItemStack result;
    private final long energy;
    private final int duration;
    @Nullable
    private final Item blueprint;
    @Nullable
    private final String research;

    public AssemblyRecipe(ResourceLocation id, AssemblyStation station, Ingredient[] cells, ItemStack result, long energy, int duration,
          @Nullable Item blueprint, @Nullable String research) {
        this.id = id;
        this.station = station;
        this.cells = cells;
        this.result = result;
        this.energy = energy;
        this.duration = duration;
        this.blueprint = blueprint;
        this.research = research;
    }

    public AssemblyStation getStation() {
        return station;
    }

    /** Ingredient d'une case (null si la case n'est pas utilisee). */
    @Nullable
    public Ingredient cell(int index) {
        return index >= 0 && index < CELLS ? cells[index] : null;
    }

    public boolean usesSlot(int index) {
        return cell(index) != null;
    }

    public int cellCount() {
        int count = 0;
        for (Ingredient cell : cells) {
            if (cell != null) {
                count++;
            }
        }
        return count;
    }

    public ItemStack getResult() {
        return result.copy();
    }

    public long getEnergy() {
        return energy;
    }

    public int getDuration() {
        return duration;
    }

    @Nullable
    public Item getBlueprint() {
        return blueprint;
    }

    @Nullable
    public ResearchNode getResearch() {
        if (research == null) {
            return null;
        }
        try {
            return ResearchNode.valueOf(research.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    public boolean isResearched() {
        ResearchNode node = getResearch();
        return node == null || ResearchEffects.has(node);
    }

    public boolean acceptsBlueprint(ItemStack stack) {
        return blueprint == null || stack.is(blueprint);
    }

    // ------------------------------------------------------------------
    // Recipe (non utilise par la table de craft)
    // ------------------------------------------------------------------

    @Override
    public boolean matches(@NotNull Container container, @NotNull Level level) {
        return false;
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull Container container, @NotNull RegistryAccess access) {
        return getResult();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return false;
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

    @Override
    public boolean isSpecial() {
        return true;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiRecipes.ASSEMBLY_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiRecipes.ASSEMBLY.get();
    }

    // ------------------------------------------------------------------
    // Serialisation
    // ------------------------------------------------------------------

    public static class Serializer implements RecipeSerializer<AssemblyRecipe> {

        @NotNull
        @Override
        public AssemblyRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            String stationId = GsonHelper.getAsString(json, "station");
            AssemblyStation station = AssemblyStation.byId(stationId);
            if (station == null) {
                throw new JsonParseException("Poste d'assemblage inconnu : " + stationId);
            }
            Map<Character, Ingredient> key = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : GsonHelper.getAsJsonObject(json, "key").entrySet()) {
                if (entry.getKey().length() != 1 || entry.getKey().equals(".") || entry.getKey().equals(" ")) {
                    throw new JsonParseException("Symbole de motif invalide : '" + entry.getKey() + "'");
                }
                key.put(entry.getKey().charAt(0), Ingredient.fromJson(entry.getValue(), false));
            }
            JsonArray pattern = GsonHelper.getAsJsonArray(json, "pattern");
            if (pattern.size() > ROWS) {
                throw new JsonParseException("Motif trop haut (" + ROWS + " lignes au plus)");
            }
            Ingredient[] cells = new Ingredient[CELLS];
            for (int row = 0; row < pattern.size(); row++) {
                String line = GsonHelper.convertToString(pattern.get(row), "pattern[" + row + "]");
                if (line.length() > COLUMNS) {
                    throw new JsonParseException("Ligne de motif trop longue (" + COLUMNS + " cases au plus) : " + line);
                }
                for (int col = 0; col < line.length(); col++) {
                    char c = line.charAt(col);
                    if (c == '.' || c == ' ') {
                        continue;
                    }
                    Ingredient ingredient = key.get(c);
                    if (ingredient == null) {
                        throw new JsonParseException("Symbole absent de la cle : '" + c + "'");
                    }
                    cells[row * COLUMNS + col] = ingredient;
                }
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));
            long energy = GsonHelper.getAsLong(json, "energy", 1_000_000L);
            int duration = Math.max(1, GsonHelper.getAsInt(json, "duration", 200));
            Item blueprint = null;
            if (json.has("blueprint")) {
                ResourceLocation blueprintId = ResourceLocation.parse(GsonHelper.getAsString(json, "blueprint"));
                blueprint = ForgeRegistries.ITEMS.getValue(blueprintId);
                if (blueprint == null) {
                    throw new JsonParseException("Plan inconnu : " + blueprintId);
                }
            }
            String research = json.has("research") ? GsonHelper.getAsString(json, "research") : null;
            return new AssemblyRecipe(id, station, cells, result, energy, duration, blueprint, research);
        }

        @Override
        public AssemblyRecipe fromNetwork(@NotNull ResourceLocation id, FriendlyByteBuf buffer) {
            AssemblyStation station = buffer.readEnum(AssemblyStation.class);
            Ingredient[] cells = new Ingredient[CELLS];
            for (int i = 0; i < CELLS; i++) {
                if (buffer.readBoolean()) {
                    cells[i] = Ingredient.fromNetwork(buffer);
                }
            }
            ItemStack result = buffer.readItem();
            long energy = buffer.readVarLong();
            int duration = buffer.readVarInt();
            Item blueprint = buffer.readBoolean() ? buffer.readById(BuiltInRegistries.ITEM) : null;
            String research = buffer.readBoolean() ? buffer.readUtf() : null;
            return new AssemblyRecipe(id, station, cells, result, energy, duration, blueprint, research);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buffer, AssemblyRecipe recipe) {
            buffer.writeEnum(recipe.station);
            for (Ingredient cell : recipe.cells) {
                buffer.writeBoolean(cell != null);
                if (cell != null) {
                    cell.toNetwork(buffer);
                }
            }
            buffer.writeItem(recipe.result);
            buffer.writeVarLong(recipe.energy);
            buffer.writeVarInt(recipe.duration);
            buffer.writeBoolean(recipe.blueprint != null);
            if (recipe.blueprint != null) {
                buffer.writeId(BuiltInRegistries.ITEM, recipe.blueprint);
            }
            buffer.writeBoolean(recipe.research != null);
            if (recipe.research != null) {
                buffer.writeUtf(recipe.research);
            }
        }
    }
}
