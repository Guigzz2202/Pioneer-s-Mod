package fr.pioneers.building.home.bag;

import com.google.gson.JsonObject;
import fr.pioneers.building.home.PiHome;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 : amelioration du sac a dos en cuir simple en sac a dos en cuir tanne (recette en grille {@code pioneers:bag_upgrade},
 * meme format qu'une recette a motif, meme mecanisme que {@code GearUpgradeRecipe}) : le resultat garde TOUT le NBT du sac du milieu de la
 * grille (sinon du premier sac trouve), donc son contenu ; ses 16 cases deviennent les 16 premieres des 32.
 * <p>
 * 1.0.5-beta (lot 31, retour 42) : champ facultatif {@code tier} (1 ou 2) : NIVEAU d'un sac de rangement (graines, pousses, poissons) : la
 * recette ne vaut que pour un sac du niveau juste en dessous ; le resultat est le meme sac, contenu garde, au niveau {@code tier}
 * ({@link BagLedger#TIER} : plus de cases, plus d'objets par case).
 */
public class BagUpgradeRecipe extends ShapedRecipe {

    /** Niveau donne (sac de rangement), -1 : amelioration du sac a dos (sans niveau). */
    public final int tier;

    public BagUpgradeRecipe(ShapedRecipe base) {
        this(base, -1);
    }

    public BagUpgradeRecipe(ShapedRecipe base, int tier) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(), withTier(base.getResultItem(RegistryAccess.EMPTY), tier),
              base.showNotification());
        this.tier = tier;
    }

    /** Resultat affiche (JEI) : le sac au niveau donne. */
    static ItemStack withTier(ItemStack out, int tier) {
        if (tier > 0 && out.getItem() instanceof BagItem b && b.kind.storage()) {
            ItemStack s = out.copy();
            BagLedger l = BagStore.read(s, b.kind);
            l.tier = tier;
            BagStore.write(s, l);
            return s;
        }
        return out;
    }

    /** Le sac de la grille (celui du milieu, sinon le premier). */
    static ItemStack bagIn(CraftingContainer grid) {
        int center = grid.getWidth() * (grid.getHeight() / 2) + grid.getWidth() / 2;
        if (center < grid.getContainerSize() && grid.getItem(center).getItem() instanceof BagItem) {
            return grid.getItem(center);
        }
        for (int i = 0; i < grid.getContainerSize(); i++) {
            if (grid.getItem(i).getItem() instanceof BagItem) {
                return grid.getItem(i);
            }
        }
        return ItemStack.EMPTY;
    }

    @Override
    public boolean matches(@NotNull CraftingContainer grid, @NotNull net.minecraft.world.level.Level level) {
        if (!super.matches(grid, level)) {
            return false;
        }
        if (tier <= 0) {
            return true;
        }
        ItemStack bag = bagIn(grid);
        return bag.getItem() instanceof BagItem b && b.kind.storage() && BagStore.read(bag, b.kind).tier == tier - 1;
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull CraftingContainer grid, @NotNull RegistryAccess access) {
        ItemStack out = super.assemble(grid, access);
        ItemStack from = bagIn(grid);
        out.setTag(!from.isEmpty() && from.hasTag() ? from.getTag().copy() : null);
        if (tier > 0 && out.getItem() instanceof BagItem b && b.kind.storage()) {
            //registre lu (ancien sac migre) puis niveau donne : le contenu garde toutes ses cases
            BagLedger l = BagStore.read(out, b.kind);
            l.tier = tier;
            BagStore.write(out, l);
        }
        return out;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiHome.BAG_UPGRADE.get();
    }

    public static final class Serializer implements RecipeSerializer<BagUpgradeRecipe> {

        @NotNull
        @Override
        public BagUpgradeRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            return new BagUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json), json.has("tier") ? json.get("tier").getAsInt() : -1);
        }

        @Override
        public BagUpgradeRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            ShapedRecipe base = RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer);
            return new BagUpgradeRecipe(base, buffer.readVarInt() - 1);
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull BagUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
            buffer.writeVarInt(recipe.tier + 1);
        }
    }
}
