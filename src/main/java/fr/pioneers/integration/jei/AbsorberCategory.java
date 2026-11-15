package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.space.content.absorber.AbsorberControllerTile;
import fr.pioneers.core.gas.PiGasTypes;
import fr.pioneers.core.registries.PiCosmicBlocks;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
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
 * Absorbeur d'energie solaire : une fenetre par etoile (puissance par capteur et pour le plus grand absorbeur) et le caloporteur qui
 * emporte la chaleur (ammoniac des geantes, ressorti surchauffe).
 */
class AbsorberCategory extends PiCategory<Body> {

    /** Capteurs du plus grand absorbeur (13 x 5 x 13 : face superieure hors aretes). */
    static final int MAX_COLLECTORS = 11 * 11;
    private static final int WIDTH = 160, ROW_Y = 12, ARROW_X = 62, NOTES_Y = 36;

    AbsorberCategory(IGuiHelper helper, RecipeType<Body> type) {
        super(helper, type, "jei.pioneers.absorber", PiCosmicBlocks.ABSORBER_COLLECTOR, WIDTH, NOTES_Y + 5 * LINE);
        family(JeiDraw.Family.COSMOS);
    }

    /** Les etoiles ou l'on peut construire un absorbeur, de la plus lumineuse a la plus faible. */
    static List<Body> entries() {
        List<Body> stars = new ArrayList<>();
        for (Body body : Body.ALL) {
            if (body.isStar() && AbsorberControllerTile.luminosity(body) > 0) {
                stars.add(body);
            }
        }
        stars.sort((a, b) -> Double.compare(AbsorberControllerTile.luminosity(b), AbsorberControllerTile.luminosity(a)));
        return stars;
    }

    /** Caloporteur : ammoniac (froid ou chaud), seul gaz qui se rechauffe. */
    private static List<GasStack> coolants(boolean heated) {
        return List.of(GasStack.of(heated ? PiGasTypes.SUPERHEATED_AMMONIA.get() : PiGasTypes.AMMONIA.get(), 1000));
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Body star, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, ROW_Y + 1).setStandardSlotBackground()
              .addItemStack(new ItemStack(PiCosmicBlocks.ABSORBER_COLLECTOR));
        List<GasStack> cold = coolants(false), hot = coolants(true);
        gas(builder, RecipeIngredientRole.INPUT, 30, ROW_Y, 18, 18, cold.get(0), 1000);
        gas(builder, RecipeIngredientRole.OUTPUT, 96, ROW_Y, 18, 18, hot.get(0), 1000);
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Body star, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(60).setPosition(ARROW_X, ROW_Y + 1);
    }

    @Override
    protected void drawContent(@NotNull Body star, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        double lum = AbsorberControllerTile.luminosity(star);
        fitted(graphics, Component.translatable("jei.pioneers.absorber.star", Component.translatable(star.getTranslationKey()),
              String.format("%.2f", lum)), 0, 1, SMALL, TEXT, WIDTH);
        long perCollector = (long) (AbsorberControllerTile.fePerCollector() * lum);
        long max = perCollector * MAX_COLLECTORS;
        double heat = max * AbsorberControllerTile.HEAT_PER_FE;
        //Notes bornees au-dessus de la ligne de recherche
        float y = block(graphics, List.of(new Note(Component.translatable("jei.pioneers.absorber.collector", energy(perCollector)), TEXT),
              new Note(Component.translatable("jei.pioneers.absorber.max", MAX_COLLECTORS, energy(max)), TEXT),
              new Note(Component.translatable("jei.pioneers.absorber.coolant", (long) Math.ceil(heat / AbsorberControllerTile.COOLANT_ENTHALPY)), TEXT_LIGHT),
              new Note(Component.translatable("jei.pioneers.absorber.flare"), TEXT_LIGHT)), 0, NOTES_Y, 0.6F, WIDTH, ch() - LINE - 1);
        research(graphics, ResearchNode.SOLAR_ABSORPTION, Math.min((int) y + 1, ch() - LINE), WIDTH);
    }
}
