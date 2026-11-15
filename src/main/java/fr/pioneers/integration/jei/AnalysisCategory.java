package fr.pioneers.integration.jei;

import fr.pioneers.api.data.DataStack;
import fr.pioneers.data.DataUnits;
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
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Machines a donnees (broyeur d'asteroides, spectrometre, sequenceur, analyseur de biomes) : l'echantillon, les donnees produites (vrai
 * ingredient JEI), la cadence et l'energie, et la recherche que ces donnees font avancer.
 */
class AnalysisCategory extends PiCategory<AnalysisCategory.Entry> {

    /**
     * Une production : les objets acceptes (consommes ou non), les donnees obtenues, la cadence (texte), l'energie par tick (FE) et la
     * recherche nourrie.
     */
    record Entry(List<ItemStack> inputs, boolean consumed, DataStack output, Component rate, long energyPerTickFE, @Nullable Component feeds) {
    }

    private static final int WIDTH = 150, ROW_Y = 4, ARROW_X = 40, OUT_X = 80, FOOTER = 30;

    AnalysisCategory(IGuiHelper helper, RecipeType<Entry> type, String titleKey, ItemLike icon) {
        super(helper, type, titleKey, icon, WIDTH, FOOTER + 3 * LINE);
        family(JeiDraw.Family.DATA);
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Entry entry, @NotNull IFocusGroup focuses) {
        builder.addSlot(entry.consumed() ? RecipeIngredientRole.INPUT : RecipeIngredientRole.CATALYST, 12, ROW_Y + 1).setStandardSlotBackground()
              .addItemStacks(entry.inputs());
        data(builder, RecipeIngredientRole.OUTPUT, OUT_X + 5, ROW_Y + 1, entry.output());
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Entry entry, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(40).setPosition(ARROW_X, ROW_Y + 1);
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        if (!entry.consumed()) {
            centeredFitted(graphics, Component.translatable("jei.pioneers.not_consumed"), 20, ROW_Y + 20, 0.5F, TEXT_LIGHT, ARROW_X - 2);
        }
        fitted(graphics, DataUnits.format(entry.output().getAmount()), OUT_X + 30, ROW_Y + 6, SMALL, TEXT, WIDTH - OUT_X - 30);
        fitted(graphics, entry.rate(), 0, FOOTER, SMALL, TEXT, WIDTH);
        fitted(graphics, Component.translatable("jei.pioneers.energy", Component.translatable("jei.pioneers.per_tick",
              energy(entry.energyPerTickFE()))), 0, FOOTER + LINE, SMALL, TEXT, WIDTH);
        if (entry.feeds() != null) {
            fitted(graphics, Component.translatable("jei.pioneers.feeds", entry.feeds()), 0,
                  FOOTER + 2 * LINE, SMALL, TEXT_LIGHT, WIDTH);
        }
    }
}
