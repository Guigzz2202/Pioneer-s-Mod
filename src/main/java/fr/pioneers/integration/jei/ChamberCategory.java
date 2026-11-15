package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
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
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Chambres stabilisees (trou noir, etoile captive) : ce qu'elles consomment et produisent pour une unite de produit, ce qu'il faut a
 * l'allumage, puis le debit et l'energie selon le reglage du rate.
 */
class ChamberCategory extends PiCategory<ChamberCategory.Entry> {

    /** Une production de chambre : gaz ou fluide en entree, objet d'allumage eventuel, gaz produit, et ses conditions. */
    record Entry(@Nullable GasStack gasInput, @Nullable FluidStack fluidInput, @Nullable ItemStack ignition, GasStack output, List<Component> notes) {
    }

    private static final int WIDTH = 150, GAUGE_W = 18, GAUGE_H = 40, IGNITION_X = 32, ARROW_X = 62, OUT_X = 100, NOTES_Y = GAUGE_H + 10;

    ChamberCategory(IGuiHelper helper, RecipeType<Entry> type, String titleKey, ItemLike icon) {
        super(helper, type, titleKey, icon, WIDTH, NOTES_Y + 5 * LINE);
        family(JeiDraw.Family.COSMOS);
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Entry entry, @NotNull IFocusGroup focuses) {
        if (entry.gasInput() != null) {
            gas(builder, RecipeIngredientRole.INPUT, 1, 0, GAUGE_W, GAUGE_H, entry.gasInput(), entry.gasInput().getAmount());
        } else if (entry.fluidInput() != null) {
            FluidStack fluid = entry.fluidInput();
            fluid(builder, RecipeIngredientRole.INPUT, 1, 0, GAUGE_W, GAUGE_H, fluid.getFluid(), fluid.getAmount(), fluid.getAmount());
        }
        if (entry.ignition() != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, IGNITION_X, 12).setStandardSlotBackground().addItemStack(entry.ignition());
        }
        gas(builder, RecipeIngredientRole.OUTPUT, OUT_X, 0, GAUGE_W, GAUGE_H, entry.output(), entry.output().getAmount());
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Entry entry, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(80).setPosition(ARROW_X, 12);
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        long in = entry.gasInput() != null ? entry.gasInput().getAmount() : entry.fluidInput() != null ? entry.fluidInput().getAmount() : 0;
        centeredFitted(graphics, shortMb(in), 1 + GAUGE_W / 2F, GAUGE_H + 2, 0.5F, TEXT, GAUGE_W + 2);
        centeredFitted(graphics, shortMb(entry.output().getAmount()), OUT_X + GAUGE_W / 2F, GAUGE_H + 2, 0.5F, TEXT, GAUGE_W + 2);
        if (entry.ignition() != null) {
            centeredFitted(graphics, Component.translatable("jei.pioneers.ignition"), IGNITION_X + 8, 31, 0.5F, TEXT_LIGHT, 2 * (ARROW_X - IGNITION_X - 10));
        }
        block(graphics, entry.notes().stream().map(note -> new Note(note, TEXT)).toList(), 0, NOTES_Y, 0.6F, WIDTH, ch());
    }
}
