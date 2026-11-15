package fr.pioneers.integration.jei;

import fr.pioneers.client.gui.core.GuiMachine;
import fr.pioneers.core.machine.MachineMenu;
import java.util.List;
import java.util.Optional;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * MAJ Recettes (lot 7) : le selecteur de recette dans JEI.
 * <ul>
 *     <li>bouton « + » d'une recette, interface d'une machine ouverte : verrouille cette recette sur la machine (si elle la fait ; sinon
 *     pas de bouton) ;</li>
 *     <li>objet glisse de la liste de JEI sur la case « Recette » : verrouille la premiere recette de la machine qui le fait ;</li>
 *     <li>page d'information « Le selecteur de recette » sur chaque machine qui en a un.</li>
 * </ul>
 * 1.0.5-beta (lot 17) : la meme poignee sert le COMPTOIR D'ENTREPOT ({@link WarehouseJei} : ingredients de la recette au panier) ; le meme
 * glisser sert les regles de l'INTERFACE DE SORTIE.
 */
@OnlyIn(Dist.CLIENT)
public final class RecipeLockJei {

    private RecipeLockJei() {
    }

    /** Bouton « + » : un seul gestionnaire pour toutes les categories (recettes industrielles, mecaniques, du kit). */
    static final class Transfer implements IUniversalRecipeTransferHandler<MachineMenu> {

        private final IRecipeTransferHandlerHelper helper;

        Transfer(IRecipeTransferHandlerHelper helper) {
            this.helper = helper;
        }

        @NotNull
        @Override
        public Class<? extends MachineMenu> getContainerClass() {
            return MachineMenu.class;
        }

        @NotNull
        @Override
        public Optional<MenuType<MachineMenu>> getMenuType() {
            return Optional.empty();
        }

        @Nullable
        @Override
        public IRecipeTransferError transferRecipe(@NotNull MachineMenu menu, @NotNull Object recipe, @NotNull IRecipeSlotsView slots, @NotNull Player player,
              boolean maxTransfer, boolean doTransfer) {
            //1.0.5-beta (lot 17) : COMPTOIR D'ENTREPOT ouvert : « + » = les ingredients de la recette au panier (JEI ne garde qu'une poignee
            //universelle par menu : celle-ci passe la main)
            if (menu.getTile() instanceof fr.pioneers.warehouse.CounterTile counter && counter.role() == fr.pioneers.warehouse.CounterTile.Role.COUNTER) {
                return WarehouseJei.transfer(counter, menu.value(fr.pioneers.warehouse.CounterUnit.DIAG), slots, maxTransfer, doTransfer, helper);
            }
            ResourceLocation id = recipe instanceof Recipe<?> r ? r.getId() : null;
            if (id == null || !(Minecraft.getInstance().screen instanceof GuiMachine screen) || !screen.canLockRecipe(id)) {
                //Pas une recette de cette machine : pas de bouton
                return helper.createInternalError();
            }
            if (doTransfer) {
                screen.lockRecipe(id);
                //1.0.5-beta (lot 44, retour 62) : l'outil de la recette (matrice, forme d'extrusion, catalyseur...) va dans sa case Outil
                screen.placeTool(recipe, maxTransfer);
            }
            return null;
        }
    }

    /** Glisser un objet de JEI sur la case « Recette ». */
    static final class Ghost implements IGhostIngredientHandler<GuiMachine> {

        @NotNull
        @Override
        public <I> List<Target<I>> getTargetsTyped(@NotNull GuiMachine screen, @NotNull ITypedIngredient<I> ingredient, boolean doStart) {
            if (ingredient.getType() != VanillaTypes.ITEM_STACK) {
                return List.of();
            }
            List<Target<I>> targets = new java.util.ArrayList<>();
            //1.0.5-beta (lot 17) : interface de sortie d'entrepot : objet glisse sur ses regles = « garder une pile » de cet objet
            Rect2i keep = screen.warehouseKeepArea();
            if (keep != null) {
                targets.add(new Target<>() {
                    @NotNull
                    @Override
                    public Rect2i getArea() {
                        return keep;
                    }

                    @Override
                    public void accept(@NotNull I value) {
                        if (value instanceof ItemStack stack) {
                            screen.warehouseKeepDropped(stack);
                        }
                    }
                });
            }
            Rect2i area = screen.recipeSlotArea();
            if (area == null) {
                return targets;
            }
            targets.add(new Target<>() {
                @NotNull
                @Override
                public Rect2i getArea() {
                    return area;
                }

                @Override
                public void accept(@NotNull I value) {
                    if (value instanceof ItemStack stack) {
                        screen.lockRecipeMaking(stack);
                    }
                }
            });
            //API Machine (lot 2) : objet glisse sur la grille du carre du selecteur : recherche par cet objet
            Rect2i grid = screen.squareGridArea();
            if (grid != null) {
                targets.add(new Target<>() {
                    @NotNull
                    @Override
                    public Rect2i getArea() {
                        return grid;
                    }

                    @Override
                    public void accept(@NotNull I value) {
                        if (value instanceof ItemStack stack) {
                            screen.searchRecipesFor(stack);
                        }
                    }
                });
            }
            return targets;
        }

        @Override
        public void onComplete() {
        }
    }
}
