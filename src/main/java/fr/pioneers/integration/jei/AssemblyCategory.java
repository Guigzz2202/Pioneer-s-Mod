package fr.pioneers.integration.jei;

import fr.pioneers.recipe.AssemblyRecipe;
import fr.pioneers.recipe.AssemblyStation;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;

/**
 * Assemblage (une categorie par poste) : la grille de 10x5 telle que dans la table (cases vides estompees), le plan requis, le produit,
 * et en bas l'energie, la duree et la recherche necessaire. La fleche donne la liste des materiaux au survol.
 */
class AssemblyCategory extends PiCategory<AssemblyRecipe> {

    private static final int CELL = 18, GRID_W = AssemblyRecipe.COLUMNS * CELL, GRID_H = AssemblyRecipe.ROWS * CELL;
    private static final int RIGHT = GRID_W + 8, OUT_X = RIGHT + 5, OUT_Y = 63, FOOTER = GRID_H + 5;

    AssemblyCategory(IGuiHelper helper, RecipeType<AssemblyRecipe> type, AssemblyStation station, ItemLike icon) {
        super(helper, type, "jei.pioneers.assembly." + station.id(), icon, RIGHT + 26, GRID_H);
        family(JeiDraw.Family.SPACE);
    }

    /** 1.11.3 (charte) : energie totale, recherche requise (verte si faite) en pastilles. */
    @Override
    protected List<JeiDraw.Chip> chips(AssemblyRecipe recipe) {
        List<JeiDraw.Chip> out = new ArrayList<>();
        if (recipe.getEnergy() > 0) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.ENERGY, energy(recipe.getEnergy()), JeiDraw.ENERGY,
                  Component.translatable("jei.pioneers.energy", energy(recipe.getEnergy()))));
        }
        ResearchNode node = recipe.getResearch();
        if (node != null) {
            boolean done = ResearchEffects.has(node);
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.RESEARCH, Component.translatable(node.getTranslationKey()), done ? GOOD : WARN,
                  Component.translatable(done ? "jei.pioneers.research_done" : "jei.pioneers.research", Component.translatable(node.getTranslationKey()))));
        }
        return out;
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, AssemblyRecipe recipe, @NotNull IFocusGroup focuses) {
        for (int row = 0; row < AssemblyRecipe.ROWS; row++) {
            for (int col = 0; col < AssemblyRecipe.COLUMNS; col++) {
                Ingredient cell = recipe.cell(row * AssemblyRecipe.COLUMNS + col);
                if (cell != null) {
                    builder.addSlot(RecipeIngredientRole.INPUT, col * CELL + 1, row * CELL + 1).setStandardSlotBackground().addIngredients(cell);
                }
            }
        }
        if (recipe.getBlueprint() != null) {
            builder.addSlot(RecipeIngredientRole.CATALYST, OUT_X, 5).setStandardSlotBackground().addItemStack(new ItemStack(recipe.getBlueprint()));
        }
        IRecipeSlotBuilder output = builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X, OUT_Y).setStandardSlotBackground();
        output.addItemStack(recipe.getResult());
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull AssemblyRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrowWidget(Math.max(20, recipe.getDuration())).setPosition(RIGHT + 1, 38).setTooltip(materials(recipe));
    }

    /** Liste des materiaux (premier objet de chaque ingredient, regroupe). */
    private static List<FormattedText> materials(AssemblyRecipe recipe) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        Map<String, Component> names = new LinkedHashMap<>();
        for (int i = 0; i < AssemblyRecipe.CELLS; i++) {
            Ingredient cell = recipe.cell(i);
            if (cell == null || cell.getItems().length == 0) {
                continue;
            }
            ItemStack first = cell.getItems()[0];
            String key = first.getItem().builtInRegistryHolder().key().location() + (cell.getItems().length > 1 ? "*" : "");
            counts.merge(key, 1, Integer::sum);
            names.putIfAbsent(key, first.getHoverName());
        }
        List<FormattedText> lines = new ArrayList<>();
        lines.add(Component.translatable("jei.pioneers.materials").withStyle(ChatFormatting.AQUA));
        counts.forEach((key, count) -> lines.add(Component.literal(count + " × ").withStyle(ChatFormatting.WHITE)
              .append(names.get(key).copy().withStyle(ChatFormatting.GRAY))));
        return lines;
    }

    @Override
    protected void drawContent(@NotNull AssemblyRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        //Cases inutilisees : un creux discret, pour garder la forme de la table
        for (int i = 0; i < AssemblyRecipe.CELLS; i++) {
            if (recipe.cell(i) == null) {
                int x = (i % AssemblyRecipe.COLUMNS) * CELL, y = (i / AssemblyRecipe.COLUMNS) * CELL;
                graphics.fill(x + 1, y + 1, x + CELL - 1, y + CELL - 1, 0x18FFFFFF);
            }
        }
        if (recipe.getBlueprint() != null) {
            centeredFitted(graphics, Component.translatable("jei.pioneers.blueprint"), OUT_X + 8, 24, 0.6F, TEXT_LIGHT, 2 * (cw() - OUT_X - 8));
        }
    }
}
