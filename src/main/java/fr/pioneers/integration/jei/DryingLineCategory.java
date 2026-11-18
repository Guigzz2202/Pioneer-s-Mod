package fr.pioneers.integration.jei;

import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.building.home.PiHome;
import fr.pioneers.building.home.line.Clothesline;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
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
import net.minecraft.world.item.crafting.RecipeManager;
import org.jetbrains.annotations.NotNull;

/**
 * Patch 1.0.3-beta, lot 7 : categorie JEI transverse « Sechage » (pictogramme dessine {@code drying_line}, T:icons/draw_jei.py) : ce qui
 * seche ou se fume sur la corde a linge ({@link Clothesline}) : chaque recette du sechoir et du fumoir a un ingredient, avec les durees sur
 * le fil (ombre / soleil, fumage sur un feu de camp), et les conditions (pluie, climat). Catalyseurs : crochet a corde et cordes.
 */
class DryingLineCategory extends PiCategory<DryingLineCategory.Entry> {

    static final RecipeType<Entry> TYPE = JeiOrder.create("drying_line", Entry.class);
    static final int W = 150, H = 62, SLOT = 18;

    /** Une recette du sechoir (smoke : seulement sur un feu) ou du fumoir. */
    record Entry(KitchenRecipe recipe, boolean smoking) {
    }

    DryingLineCategory(IGuiHelper helper) {
        super(helper, TYPE, "jei.pioneers.drying_line", PiHome.ROPE_HOOK.getBlock(), W, H);
        family(JeiDraw.Family.KITCHEN);
    }

    static List<Entry> recipes(RecipeManager manager) {
        List<Entry> out = new ArrayList<>();
        for (Station s : new Station[]{Station.DRYING, Station.SMOKING}) {
            List<KitchenRecipe> list = new ArrayList<>(manager.getAllRecipesFor(PiKitchen.TYPES.get(s).get()));
            list.sort(Comparator.comparing(r -> r.getId().toString()));
            for (KitchenRecipe r : list) {
                if (r.ingredients().size() == 1 && r.ingredients().get(0).count() == 1 && r.fluid() == null && !r.results().isEmpty()) {
                    out.add(new Entry(r, s == Station.SMOKING));
                }
            }
        }
        return out;
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, @NotNull Entry e, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).addItemStacks(Arrays.asList(e.recipe.ingredients().get(0).ingredient().getItems()))
              .setStandardSlotBackground();
        builder.addSlot(RecipeIngredientRole.CATALYST, SLOT + 5, 1).addItemStack(new ItemStack(PiHome.ROPE_HOOK.getBlock())).setStandardSlotBackground();
        ItemStack out = e.recipe.results().get(0).stack().copy();
        builder.addSlot(RecipeIngredientRole.OUTPUT, W - SLOT + 1, 1).addItemStack(out).setStandardSlotBackground();
    }

    private static Component minutes(double ticks) {
        double m = ticks / 1200.0;
        return Component.translatable("jei.pioneers.drying_line.minutes", m >= 10 ? String.valueOf(Math.round(m)) : String.format(java.util.Locale.ROOT, "%.1f", m));
    }

    @Override
    protected void drawContent(@NotNull Entry e, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics g, double mouseX, double mouseY) {
        JeiDraw.still(g, (2 * SLOT + 5 + W - SLOT) / 2F, 5);
        int t = Clothesline.lineTime(e.recipe);
        List<Note> notes = new ArrayList<>();
        if (e.smoking) {
            notes.add(new Note(Component.translatable("jei.pioneers.drying_line.smoke", minutes(t)), TEXT));
        } else if (e.recipe.smoke()) {
            notes.add(new Note(Component.translatable("jei.pioneers.drying_line.need_fire", minutes(t / 2.0)), WARN));
        } else {
            notes.add(new Note(Component.translatable("jei.pioneers.drying_line.dry", minutes(t), minutes(t / 1.5)), TEXT));
            notes.add(new Note(Component.translatable("jei.pioneers.drying_line.rain"), TEXT_LIGHT));
        }
        notes.add(new Note(Component.translatable("jei.pioneers.drying_line.rack"), TEXT_LIGHT));
        block(g, notes, 1, SLOT + 4, SMALL, W - 2, H - 1);
    }
}
