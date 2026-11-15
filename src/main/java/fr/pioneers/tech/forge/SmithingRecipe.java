package fr.pioneers.tech.forge;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import fr.pioneers.core.registries.PiForge;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import net.minecraft.core.RegistryAccess;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Forge a l'enclume ({@code pioneers:smithing}) : une forme 16 x 16 a obtenir dans un metal chauffe.
 * <pre>
 * {
 *   "type": "pioneers:smithing",
 *   "metal": "iron" | ["copper", "bronze"] | "*",      (* = tout metal forgeable a la main)
 *   "anvil_tier": 0,                                    (0 pierre, 1 bronze, 2 fer ; minimum, le metal peut exiger plus)
 *   "ingots": 1,                                        (lingots a poser ; defaut 1)
 *   "pattern": ["................", ... 16 lignes],     ('#' = matiere)
 *   "result": {"item": "pioneers:{metal}_pickaxe_head", "count": 1}   ({metal} remplace par le metal de la piece)
 * }
 * </pre>
 * Le resultat de la forge est un metal chauffe portant {@code shape} = la piece ({@link #forge}) ; la trempe la donne.
 */
public class SmithingRecipe implements Recipe<Container> {

    public static final String ANY = "*";
    public static final String METAL_TOKEN = "{metal}";

    private final ResourceLocation id;
    private final List<String> metals;
    private final int anvilTier;
    private final int ingots;
    private final List<String> pattern;
    private final SmithingGrid target;
    private final String resultItem;
    private final int resultCount;
    @Nullable
    private final CompoundTag resultTag;

    public SmithingRecipe(ResourceLocation id, List<String> metals, int anvilTier, int ingots, List<String> pattern, String resultItem,
          int resultCount, @Nullable CompoundTag resultTag) {
        this.id = id;
        this.metals = List.copyOf(metals);
        this.anvilTier = anvilTier;
        this.ingots = ingots;
        this.pattern = List.copyOf(pattern);
        this.target = SmithingGrid.parse(pattern);
        this.resultItem = resultItem;
        this.resultCount = resultCount;
        this.resultTag = resultTag;
    }

    /** Metaux acceptes ; contient {@link #ANY} pour tout metal forgeable a la main. */
    public List<String> metals() {
        return metals;
    }

    public boolean acceptsMetal(String metal) {
        if (metal.isEmpty()) {
            return false;
        }
        return metals.contains(metal) || metals.contains(ANY) && ForgeMetals.forgeable(metal);
    }

    /** Enclume demandee par la recette seule. */
    public int anvilTier() {
        return anvilTier;
    }

    /** Enclume necessaire pour forger ce metal avec cette recette (la plus exigeante des deux). */
    public int anvilTierFor(String metal) {
        return Math.max(anvilTier, ForgeMetals.anvilTier(metal));
    }

    public int ingots() {
        return ingots;
    }

    /** Matiere posee au depart (mB). */
    public int amount() {
        return ingots * ForgeMetals.INGOT_MB;
    }

    public List<String> pattern() {
        return pattern;
    }

    /** Forme a obtenir (copie). */
    public SmithingGrid target() {
        return target.copy();
    }

    /** Pixels de la forme. */
    public int pixels() {
        return target.count();
    }

    /** Id du resultat tel qu'ecrit (avec {@link #METAL_TOKEN} eventuel). */
    public String resultTemplate() {
        return resultItem;
    }

    /** Piece finie pour ce metal ; vide si l'objet n'existe pas. */
    public ItemStack result(String metal) {
        ResourceLocation rl = ResourceLocation.tryParse(resultItem.replace(METAL_TOKEN, metal));
        Item item = rl == null ? Items.AIR : BuiltInRegistries.ITEM.get(rl);
        if (item == Items.AIR) {
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, resultCount);
        if (resultTag != null) {
            stack.setTag(resultTag.copy());
        }
        return stack;
    }

    /** Vrai si la grille recouvre exactement la forme. */
    public boolean matches(SmithingGrid grid) {
        return grid.matches(target);
    }

    /**
     * Fin de forge (minijeu reussi, marteau-pilon) : copie du metal chauffe sans forge en cours, avec {@code shape} = la piece et la quantite
     * de matiere de la forme. Vide si le metal n'est pas accepte. La temperature est gardee.
     */
    public ItemStack forge(ItemStack heated) {
        String metal = HeatedMetal.metal(heated);
        ItemStack part = acceptsMetal(metal) ? result(metal) : ItemStack.EMPTY;
        if (part.isEmpty()) {
            return ItemStack.EMPTY;
        }
        ItemStack out = heated.copyWithCount(1);
        HeatedMetal.clearWork(out);
        HeatedMetal.setShape(out, part);
        HeatedMetal.setAmount(out, pixels() * ForgeMetals.INGOT_MB / ForgeMetals.PIXELS_PER_INGOT);
        return out;
    }

    // ------------------------------------------------------------------
    // Recherche
    // ------------------------------------------------------------------

    /** Toutes les recettes, triees par id (meme ordre client et serveur). */
    public static List<SmithingRecipe> all(Level level) {
        List<SmithingRecipe> out = new ArrayList<>(level.getRecipeManager().getAllRecipesFor(PiForge.SMITHING.get()));
        out.sort(Comparator.comparing(r -> r.getId().toString()));
        return out;
    }

    /** Recettes possibles pour ce metal sur une enclume de ce tier, triees par id. */
    public static List<SmithingRecipe> forMetal(Level level, String metal, int anvil) {
        List<SmithingRecipe> out = new ArrayList<>();
        for (SmithingRecipe r : all(level)) {
            if (r.acceptsMetal(metal) && r.anvilTierFor(metal) <= anvil) {
                out.add(r);
            }
        }
        return out;
    }

    @Nullable
    public static SmithingRecipe byId(Level level, @Nullable ResourceLocation id) {
        if (id == null) {
            return null;
        }
        return level.getRecipeManager().byKey(id).filter(r -> r instanceof SmithingRecipe).map(r -> (SmithingRecipe) r).orElse(null);
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
        return getResultItem(access).copy();
    }

    @Override
    public boolean canCraftInDimensions(int width, int height) {
        return true;
    }

    /** Resultat pour le premier metal nomme (fer si {@link #ANY}) : pour l'affichage seulement. */
    @NotNull
    @Override
    public ItemStack getResultItem(@NotNull RegistryAccess access) {
        String metal = metals.isEmpty() || ANY.equals(metals.get(0)) ? "iron" : metals.get(0);
        return result(metal);
    }

    @NotNull
    @Override
    public ResourceLocation getId() {
        return id;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiForge.SMITHING_SERIALIZER.get();
    }

    @NotNull
    @Override
    public RecipeType<?> getType() {
        return PiForge.SMITHING.get();
    }

    @Override
    public boolean isSpecial() {
        return true;
    }

    public static class Serializer implements RecipeSerializer<SmithingRecipe> {

        @NotNull
        @Override
        public SmithingRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            List<String> metals = new ArrayList<>();
            JsonElement m = json.get("metal");
            if (m == null) {
                metals.add(ANY);
            } else if (m.isJsonArray()) {
                for (JsonElement e : m.getAsJsonArray()) {
                    metals.add(e.getAsString());
                }
            } else {
                metals.add(m.getAsString());
            }
            JsonArray rows = GsonHelper.getAsJsonArray(json, "pattern");
            if (rows.size() != SmithingGrid.SIZE) {
                throw new JsonParseException("pioneers:smithing " + id + " : le motif doit avoir 16 lignes");
            }
            List<String> pattern = new ArrayList<>();
            for (JsonElement e : rows) {
                pattern.add(e.getAsString());
            }
            JsonObject result = GsonHelper.getAsJsonObject(json, "result");
            CompoundTag nbt = null;
            if (result.has("nbt")) {
                try {
                    nbt = TagParser.parseTag(GsonHelper.getAsString(result, "nbt"));
                } catch (Exception e) {
                    throw new JsonParseException("pioneers:smithing " + id + " : nbt invalide", e);
                }
            }
            return new SmithingRecipe(id, metals, GsonHelper.getAsInt(json, "anvil_tier", 0), GsonHelper.getAsInt(json, "ingots", 1), pattern,
                  GsonHelper.getAsString(result, "item"), GsonHelper.getAsInt(result, "count", 1), nbt);
        }

        @Override
        public SmithingRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            List<String> metals = buffer.readList(FriendlyByteBuf::readUtf);
            int anvil = buffer.readVarInt();
            int ingots = buffer.readVarInt();
            List<String> pattern = buffer.readList(FriendlyByteBuf::readUtf);
            String item = buffer.readUtf();
            int count = buffer.readVarInt();
            CompoundTag nbt = buffer.readNbt();
            return new SmithingRecipe(id, metals, anvil, ingots, pattern, item, count, nbt);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull SmithingRecipe recipe) {
            buffer.writeCollection(recipe.metals, FriendlyByteBuf::writeUtf);
            buffer.writeVarInt(recipe.anvilTier);
            buffer.writeVarInt(recipe.ingots);
            buffer.writeCollection(recipe.pattern, FriendlyByteBuf::writeUtf);
            buffer.writeUtf(recipe.resultItem);
            buffer.writeVarInt(recipe.resultCount);
            buffer.writeNbt(recipe.resultTag);
        }
    }
}
