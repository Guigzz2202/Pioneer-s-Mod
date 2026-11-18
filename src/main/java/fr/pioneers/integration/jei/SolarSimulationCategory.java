package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.space.content.spacesim.SpaceSimControllerTile;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.recipes.SolarSimulationRecipe;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.research.ResearchNode;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Simulateur du systeme solaire : ce que produit une planete simulee a chaque cycle (elle ne s'use pas). A gauche ce qu'il faut au
 * simulateur : la planete, le coeur stellaire (soleil, garde tant qu'il brille) et le plasma stellaire consomme en continu.
 */
class SolarSimulationCategory extends PiCategory<SolarSimulationRecipe> {

    private static final int COLUMNS = 2, ARROW_X = 80, ARROW_Y = 21, OUT_X = 110, LABEL_X = 21, FOOTER = 62;

    SolarSimulationCategory(IGuiHelper helper, RecipeType<SolarSimulationRecipe> type) {
        super(helper, type, "jei.pioneers.solar_simulation", PiSpaceSim.CONTROLLER, 150, FOOTER + 3 * LINE);
        family(JeiDraw.Family.COSMOS);
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, SolarSimulationRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 1).setStandardSlotBackground().addIngredients(recipe.getPlanet());
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, 21).setStandardSlotBackground().addItemStack(new ItemStack(PiItems.STELLAR_CORE.get()));
        gas(builder, RecipeIngredientRole.INPUT, 0, 40, 18, 18, GasStack.of(PiGasTypes.STELLAR_PLASMA.get(), SpaceSimControllerTile.SOLAR_UPKEEP_PLASMA),
              SpaceSimControllerTile.SOLAR_UPKEEP_PLASMA);
        int count = recipe.getOutputs().size();
        int rows = (count + COLUMNS - 1) / COLUMNS;
        int top = Math.max(0, ARROW_Y + 8 - rows * 9);
        int i = 0;
        for (ItemStack output : recipe.getOutputs()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + (i % COLUMNS) * 18 + 1, top + (i / COLUMNS) * 18 + 1).setStandardSlotBackground()
                  .addItemStack(output);
            i++;
        }
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull SolarSimulationRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(Math.max(20, recipe.getDuration())).setPosition(ARROW_X, ARROW_Y);
    }

    @Override
    protected void drawContent(@NotNull SolarSimulationRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX,
          double mouseY) {
        int max = ARROW_X - LABEL_X - 2;
        fitted(graphics, Component.translatable("jei.pioneers.solar_simulation.planet"), LABEL_X, 5, 0.6F, TEXT_LIGHT, max);
        fitted(graphics, Component.translatable("jei.pioneers.solar_simulation.core"), LABEL_X, 25, 0.6F, TEXT_LIGHT, max);
        fitted(graphics, Component.translatable("jei.pioneers.per_tick", mb(SpaceSimControllerTile.SOLAR_UPKEEP_PLASMA)), LABEL_X, 46, 0.6F,
              TEXT_LIGHT, max);
        fitted(graphics, Component.translatable("jei.pioneers.solar_simulation.cycle", seconds(recipe.getDuration())), 0, FOOTER, SMALL,
              TEXT, cw());
        fitted(graphics, Component.translatable("jei.pioneers.solar_simulation.upkeep",
              Component.translatable("jei.pioneers.per_tick", energy(SpaceSimControllerTile.SOLAR_UPKEEP_FE))), 0, FOOTER + LINE,
              SMALL, TEXT, cw());
        research(graphics, ResearchNode.SOLAR_SIMULATION, FOOTER + 2 * LINE, cw());
    }
}
