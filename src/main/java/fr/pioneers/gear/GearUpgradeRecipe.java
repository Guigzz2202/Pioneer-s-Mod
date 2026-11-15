package fr.pioneers.gear;

import com.google.gson.JsonObject;
import fr.pioneers.gear.electric.ElectricCharge;
import fr.pioneers.gear.electric.PoweredItem;
import fr.pioneers.registries.PiGear;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.ShapedRecipe;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.15 : amelioration d'un objet modulaire au tier suivant (recette en grille {@code pioneers:gear_upgrade}, meme format qu'une recette
 * a motif) : le resultat garde les modules, leur etat et l'energie de l'objet du tier precedent (celui du milieu de la grille, sinon le
 * premier objet modulaire trouve). Le multi-outil reprend ceux de la pioche. 1.0.3-beta (lot 9) : une piece Graviton faite de la piece
 * Voltaique correspondante en garde l'energie.
 */
public class GearUpgradeRecipe extends ShapedRecipe {

    public GearUpgradeRecipe(ShapedRecipe base) {
        super(base.getId(), base.getGroup(), base.category(), base.getWidth(), base.getHeight(), base.getIngredients(), base.getResultItem(RegistryAccess.EMPTY),
              base.showNotification());
    }

    @NotNull
    @Override
    public ItemStack assemble(@NotNull CraftingContainer grid, @NotNull RegistryAccess access) {
        ItemStack out = super.assemble(grid, access);
        ItemStack from = ItemStack.EMPTY;
        int center = grid.getWidth() * (grid.getHeight() / 2) + grid.getWidth() / 2;
        if (center < grid.getContainerSize() && ModularGear.is(grid.getItem(center))) {
            from = grid.getItem(center);
        } else {
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack s = grid.getItem(i);
                if (ModularGear.is(s) && (from.isEmpty() || s.getItem() instanceof ModularToolItem t && t.kind() == GearKind.PICKAXE)) {
                    from = s;
                }
            }
        }
        if (!from.isEmpty() && from.hasTag()) {
            out.setTag(from.getTag().copy());
            Modules.setEnergy(out, Modules.energy(out));
        } else if (from.isEmpty()) {
            //1.0.3-beta (lot 9) : piece Graviton faite d'une piece Voltaique (sans module) : seule l'energie passe
            for (int i = 0; i < grid.getContainerSize(); i++) {
                ItemStack s = grid.getItem(i);
                if (PoweredItem.is(s) && ElectricCharge.energy(s) > 0) {
                    Modules.setEnergy(out, ElectricCharge.energy(s));
                    break;
                }
            }
        }
        return out;
    }

    @NotNull
    @Override
    public RecipeSerializer<?> getSerializer() {
        return PiGear.UPGRADE_SERIALIZER.get();
    }

    public static final class Serializer implements RecipeSerializer<GearUpgradeRecipe> {

        @NotNull
        @Override
        public GearUpgradeRecipe fromJson(@NotNull ResourceLocation id, @NotNull JsonObject json) {
            return new GearUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromJson(id, json));
        }

        @Override
        public GearUpgradeRecipe fromNetwork(@NotNull ResourceLocation id, @NotNull FriendlyByteBuf buffer) {
            return new GearUpgradeRecipe(RecipeSerializer.SHAPED_RECIPE.fromNetwork(id, buffer));
        }

        @Override
        public void toNetwork(@NotNull FriendlyByteBuf buffer, @NotNull GearUpgradeRecipe recipe) {
            RecipeSerializer.SHAPED_RECIPE.toNetwork(buffer, recipe);
        }
    }
}
