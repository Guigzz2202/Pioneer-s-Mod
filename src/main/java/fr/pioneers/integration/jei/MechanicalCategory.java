package fr.pioneers.integration.jei;

import fr.pioneers.kinetics.machines.MachineType;
import fr.pioneers.kinetics.machines.MechanicalRecipe;
import fr.pioneers.registries.PiKinetics;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Recettes « en tours » de l'age mecanique (meule, tamis, concasseur, scie, tour, martinet) : entree, fleche, sorties avec leur chance et le
 * tamis minimal ; pied de page : tours d'arbre et duree au regime nominal de la machine.
 */
class MechanicalCategory extends PiCategory<MechanicalRecipe> {

    private static final int IN_X = 1, IN_Y = 20, ARROW_X = 26, OUT_X = 58, COLS = 5, STEP = 19, ROWS = 3;
    private final MachineType machine;

    MechanicalCategory(IGuiHelper helper, RecipeType<MechanicalRecipe> type, MachineType machine) {
        super(helper, type, "block.pioneers." + machine.id(), PiKinetics.machine(machine), OUT_X + COLS * STEP, ROWS * STEP);
        this.machine = machine;
        family(JeiDraw.Family.MECHANICAL);
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, MechanicalRecipe recipe, @NotNull IFocusGroup focuses) {
        List<ItemStack> inputs = new ArrayList<>();
        for (ItemStack stack : recipe.input().getItems()) {
            ItemStack copy = stack.copy();
            copy.setCount(recipe.count());
            inputs.add(copy);
        }
        builder.addSlot(RecipeIngredientRole.INPUT, IN_X, IN_Y).setStandardSlotBackground().addItemStacks(inputs);
        List<MechanicalRecipe.Result> results = recipe.results();
        int rows = Math.max(1, (results.size() + COLS - 1) / COLS);
        int y0 = IN_Y - (rows - 1) * STEP / 2;
        for (int i = 0; i < results.size() && i < COLS * ROWS; i++) {
            MechanicalRecipe.Result result = results.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + (i % COLS) * STEP, Math.max(1, y0 + (i / COLS) * STEP))
                  .setStandardSlotBackground().addItemStack(result.stack());
            chance(slot, result.chance());
            slot.addRichTooltipCallback((view, tooltip) -> {
                if (result.chance() < 1) {
                    tooltip.add(Component.translatable("jei.pioneers.mechanical.chance", String.format(Locale.ROOT, "%.0f", result.chance() * 100))
                          .withStyle(ChatFormatting.GOLD));
                }
                if (result.mesh() > 1) {
                    tooltip.add(Component.translatable("jei.pioneers.mechanical.mesh", result.mesh()).withStyle(ChatFormatting.AQUA));
                }
            });
        }
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull MechanicalRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(Math.max(20, ticks(recipe))).setPosition(ARROW_X, IN_Y);
    }

    private int ticks(MechanicalRecipe recipe) {
        return (int) Math.round(recipe.turns() / (machine.nominalSpeed / 60.0) * 20);
    }

    /** 1.11.3 (charte) : tours d'arbre, duree au regime nominal, regime et couple en pastilles. */
    @Override
    protected List<JeiDraw.Chip> chips(MechanicalRecipe recipe) {
        Component full = Component.translatable("jei.pioneers.mechanical.turns", String.format(Locale.ROOT, "%.0f", recipe.turns()),
              seconds(ticks(recipe)), Math.round(machine.nominalSpeed), Math.round(machine.torque));
        List<JeiDraw.Chip> out = new ArrayList<>(List.of(JeiDraw.Chip.of(JeiDraw.Pic.TURNS, Component.translatable("jei.pioneers.chip.turns",
                    String.format(Locale.ROOT, "%.0f", recipe.turns())), JeiDraw.LIGHT, full),
              JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.chip.rpm", Math.round(machine.nominalSpeed), Math.round(machine.torque)),
                    JeiDraw.DIM, full)));
        //1.0.5-beta (lot 38) : recette de tier 0 de sa famille (les machines a vapeur et MKI de la famille la font aussi)
        if (fr.pioneers.api.machine.tier.GeneratedTierFamilies.FAMILY_OF.containsKey(machine.id())) {
            ItemStack main = recipe.results().isEmpty() ? ItemStack.EMPTY : recipe.results().get(0).stack();
            out.add(TierChips.minTier(0, recipe.getId(), List.of(new fr.pioneers.bronze.bench.BenchRecipe.Counted(recipe.input(), recipe.count())), main));
        }
        return out;
    }
}
