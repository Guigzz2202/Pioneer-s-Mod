package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.space.content.spacesim.SpaceSimControllerTile;
import fr.pioneers.space.content.universe.GalaxyType;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.registries.PiItems;
import fr.pioneers.space.simulation.PiSpaceSim;
import fr.pioneers.space.research.ResearchNode;
import java.util.List;
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
 * Simulateur d'univers : une fenetre par galaxie (plasma consomme par cycle, produits, produit bonus et sa chance, energie par tick,
 * duree du cycle) et, en note, ce que coute le Big Bang.
 */
class UniverseCategory extends PiCategory<GalaxyType> {

    private static final int WIDTH = 160, ROW_Y = 12, ARROW_X = 46, OUT_X = 76, BONUS_X = 140, NOTES_Y = 46;

    UniverseCategory(IGuiHelper helper, RecipeType<GalaxyType> type) {
        super(helper, type, "jei.pioneers.universe", PiSpaceSim.CONTROLLER, WIDTH, NOTES_Y + 4 * LINE);
        family(JeiDraw.Family.COSMOS);
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, GalaxyType galaxy, @NotNull IFocusGroup focuses) {
        long plasma = galaxy.plasmaPerTick * galaxy.cycleTicks();
        gas(builder, RecipeIngredientRole.INPUT, 0, ROW_Y, 18, 18, GasStack.of(PiGasTypes.STELLAR_PLASMA.get(), plasma), plasma);
        builder.addSlot(RecipeIngredientRole.CATALYST, 22, ROW_Y + 1).setStandardSlotBackground()
              .addItemStack(new ItemStack(PiItems.VOID_SINGULARITY_CELL.get()));
        List<ItemStack> outputs = galaxy.outputs();
        for (int i = 0; i < outputs.size(); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + i * 20 + 1, ROW_Y + 1).setStandardSlotBackground().addItemStack(outputs.get(i));
        }
        builder.addSlot(RecipeIngredientRole.OUTPUT, BONUS_X + 1, ROW_Y + 1).setStandardSlotBackground().addItemStack(galaxy.bonus());
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull GalaxyType galaxy, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(Math.min(galaxy.cycleTicks(), 400)).setPosition(ARROW_X, ROW_Y + 1);
    }

    @Override
    protected void drawContent(@NotNull GalaxyType galaxy, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        fitted(graphics, Component.translatable(galaxy.getTranslationKey()), 0, 1, SMALL, 0xFF000000 | lighten(galaxy.color), BONUS_X - 4);
        centeredFitted(graphics, Component.literal(Math.round(galaxy.bonusChance() * 100) + " %"), BONUS_X + 9, ROW_Y + 21, 0.6F, TEXT_LIGHT, 18);
        centeredFitted(graphics, Component.translatable("jei.pioneers.universe.bonus"), BONUS_X + 9, 3, 0.6F, TEXT_LIGHT, 2 * (WIDTH - BONUS_X - 9));
        //Sous la rangee (les pourcentages descendent jusqu'a ROW_Y + 26), a gauche de la colonne bonus
        float y = block(graphics, List.of(new Note(Component.translatable("jei.pioneers.universe.per_tick", energy(galaxy.energyPerTick()),
              galaxy.plasmaPerTick), TEXT)), 0, ROW_Y + 22, 0.6F, BONUS_X - 4, NOTES_Y - 1);
        y = Math.max(y, NOTES_Y);
        y = block(graphics, List.of(new Note(Component.translatable("jei.pioneers.universe.ignition", energy(SpaceSimControllerTile.GALAXY_IGNITION_FE),
              mb(SpaceSimControllerTile.GALAXY_IGNITION_PLASMA)), TEXT_LIGHT)), 0, y, 0.6F, WIDTH, ch() - 2 * LINE - 1);
        int footerY = Math.min((int) y + 1, ch() - 2 * LINE);
        footer(graphics, footerY, 0, galaxy.cycleTicks(), WIDTH);
        research(graphics, ResearchNode.UNIVERSE_SIMULATION, footerY + LINE, WIDTH);
    }

    /** Couleur de la galaxie, eclaircie pour rester lisible sur le fond sombre de la charte (1.11.3 ; avant : assombrie sur le gris de JEI). */
    private static int lighten(int color) {
        int r = (color >> 16 & 0xFF), g = (color >> 8 & 0xFF), b = (color & 0xFF);
        return (r + 255) / 2 << 16 | (g + 255) / 2 << 8 | (b + 255) / 2;
    }
}
