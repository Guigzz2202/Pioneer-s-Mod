package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
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
 * Categorie JEI « explicative » commune : combustibles, ressources sauvages (herbes, saignees), chaleur, air comprime. Une entree montre ce qui
 * entre (objets, fluide ou gaz), ce qui sort, un titre et un bloc de texte qui dit comment s'y prendre.
 */
class GuideCategory extends PiCategory<GuideCategory.Entry> {

    static final int W = 176, H = 84, SLOT = 18, TOP = 0;

    /**
     * Une entree : emplacements d'entree (chacun : une liste d'alternatives qui defilent), fluide ou gaz d'entree facultatifs, objets de
     * sortie, titre et lignes d'explication.
     */
    record Entry(List<List<ItemStack>> inputs, @Nullable FluidStack fluid, @Nullable GasStack gas, List<ItemStack> outputs, Component title,
                 List<Component> lines) {

        static Entry of(List<List<ItemStack>> inputs, List<ItemStack> outputs, Component title, List<Component> lines) {
            return new Entry(inputs, null, null, outputs, title, lines);
        }
    }

    GuideCategory(IGuiHelper helper, RecipeType<Entry> type, String titleKey, ItemLike icon) {
        super(helper, type, titleKey, icon, W, H);
        family(JeiDraw.Family.GUIDE);
    }

    /** 1.11.3 : le titre de l'entree passe dans l'en-tete de la charte. */
    @Override
    protected Component headerTitle(Entry entry) {
        return entry.title();
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Entry entry, @NotNull IFocusGroup focuses) {
        int x = 0;
        for (List<ItemStack> slot : entry.inputs()) {
            if (x > 4 * SLOT) {
                break;
            }
            builder.addSlot(RecipeIngredientRole.INPUT, x + 1, TOP + 1).addItemStacks(slot).setStandardSlotBackground();
            x += SLOT;
        }
        if (entry.fluid() != null && !entry.fluid().isEmpty()) {
            fluid(builder, RecipeIngredientRole.INPUT, x, TOP, SLOT, SLOT, entry.fluid().getFluid(), entry.fluid().getAmount(), entry.fluid().getAmount());
        }
        if (entry.gas() != null && !entry.gas().isEmpty()) {
            gas(builder, RecipeIngredientRole.INPUT, x, TOP, SLOT, SLOT, entry.gas(), entry.gas().getAmount());
        }
        int ox = W - SLOT * Math.min(4, entry.outputs().size());
        for (int i = 0; i < Math.min(4, entry.outputs().size()); i++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, ox + i * SLOT + 1, TOP + 1).addItemStack(entry.outputs().get(i)).setStandardSlotBackground();
        }
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        if (!entry.outputs().isEmpty()) {
            int left = Math.min(5, entry.inputs().size() + (entry.fluid() != null || entry.gas() != null ? 1 : 0)) * SLOT;
            int right = W - SLOT * Math.min(4, entry.outputs().size());
            JeiDraw.still(graphics, (left + right) / 2F, TOP + 5 + 4);
        }
        List<Note> notes = new ArrayList<>();
        for (Component line : entry.lines()) {
            notes.add(new Note(line, TEXT));
        }
        fr.pioneers.client.gui.GuiDraw.screen(graphics, 0, TOP + SLOT + 3, W, H - (TOP + SLOT + 3));
        block(graphics, notes, 3, TOP + SLOT + 3 + 2.5F, SMALL, W - 6, H - 1);
    }
}
