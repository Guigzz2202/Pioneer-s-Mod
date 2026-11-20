package fr.pioneers.integration.jei;

import fr.pioneers.space.mega.Megastructures;
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
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.7 (lot 12) : page JEI des MEGASTRUCTURES (brief §9, lot 12) : une page par megastructure, son astre, la liste du chantier (14 objets
 * au plus, deux rangees de 7, en nombre), puis ce qu'elle ouvre : son port (catalyseur) et les composants gates qu'elle seule fabrique (ses
 * recettes uniques, dans la categorie du port). Donnees : J:space/mega/Megastructures (T:au_dela/megastructures.py).
 */
class MegastructureCategory extends PiCategory<Integer> {

    private static final int WIDTH = 176, LIST_Y = 12, OUT_Y = 58, NOTES_Y = 80;

    MegastructureCategory(IGuiHelper helper, RecipeType<Integer> type) {
        super(helper, type, "jei.pioneers.megastructure", icon(), WIDTH, NOTES_Y + 3 * LINE);
        family(JeiDraw.Family.COSMOS);
    }

    private static Item icon() {
        return Megastructures.COUNT > 0 ? item(Megastructures.port(0)) : Items.BEACON;
    }

    static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(id.contains(":") ? new ResourceLocation(id) : new ResourceLocation("pioneers", id));
        return it == null ? Items.BARRIER : it;
    }

    /** Toutes les megastructures, dans l'ordre de la progression. */
    static List<Integer> entries() {
        List<Integer> out = new ArrayList<>();
        for (int i = 0; i < Megastructures.COUNT; i++) {
            out.add(i);
        }
        return out;
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Integer i, @NotNull IFocusGroup focuses) {
        int n = Megastructures.ingredientCount(i);
        for (int k = 0; k < n && k < 14; k++) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (k % 7) * 18, LIST_Y + 1 + (k / 7) * 18).setStandardSlotBackground()
                  .addItemStack(Megastructures.ingredientStack(i, k).copyWithCount(Megastructures.needed(i, k)));
        }
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, OUT_Y + 1).setStandardSlotBackground().addItemStack(new ItemStack(item(Megastructures.port(i))));
        String[] gated = Megastructures.gated(i);
        for (int k = 0; k < gated.length && k < 5; k++) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, 70 + k * 20, OUT_Y + 1).setOutputSlotBackground().addItemStack(new ItemStack(item(gated[k])));
        }
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Integer i, @NotNull IFocusGroup focuses) {
        builder.addRecipeArrow().setPosition(40, OUT_Y + 1);
    }

    @Override
    protected void drawContent(@NotNull Integer i, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        fitted(graphics, Component.translatable("jei.pioneers.megastructure.title", Megastructures.name(i),
              Component.translatable(Megastructures.body(i).getTranslationKey())), 0, 1, SMALL, TEXT, WIDTH);
        fitted(graphics, Component.translatable("jei.pioneers.megastructure.list"), 130, LIST_Y + 4, SMALL, TEXT_LIGHT, WIDTH - 130);
        block(graphics, List.of(new Note(Component.translatable("jei.pioneers.megastructure.port"), TEXT_LIGHT),
              new Note(Component.translatable("jei.pioneers.megastructure.gated"), TEXT_LIGHT)), 0, NOTES_Y, 0.6F, WIDTH, ch() - 1);
    }
}
