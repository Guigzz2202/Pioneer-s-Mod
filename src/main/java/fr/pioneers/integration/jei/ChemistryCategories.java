package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.world.geology.DepositType;
import fr.pioneers.world.geology.fluid.FluidDepositType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.chemistry.PiChemistry;
import fr.pioneers.tech.industry.PiIndustry;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import org.jetbrains.annotations.NotNull;

/** Categories JEI de l'age de la chimie : gisements fluides (ce que les puits en tirent, ou les trouver). */
final class ChemistryCategories {

    private ChemistryCategories() {
    }

    /** Machines qui exploitent les gisements fluides : puits d'atelier et multiblocs (le sismographe les trouve). */
    static void catalysts(IRecipeCatalystRegistration registration, RecipeType<FluidDepositType> type) {
        registration.addRecipeCatalyst(new ItemStack(PiChemistry.SEISMOGRAPH.get()), type);
        for (IndustryMachine m : IndustryMachine.values()) {
            if (m.kind != IndustryMachine.Kind.WELL) {
                continue;
            }
            List<ItemLike> blocks = new ArrayList<>();
            if (PiIndustry.ATELIERS.containsKey(m)) {
                PiIndustry.ATELIERS.get(m).values().forEach(e -> blocks.add(e.getBlock()));
            }
            if (m.industrial) {
                blocks.add(PiIndustry.controller(m).getBlock());
            }
            for (ItemLike b : blocks) {
                registration.addRecipeCatalyst(new ItemStack(b), type);
            }
        }
    }

    static class FluidDeposit extends PiCategory<FluidDepositType> {

        private static final int W = 160, H = 96;   //1.0.3-beta : place pour les biomes

        FluidDeposit(IGuiHelper helper, RecipeType<FluidDepositType> type) {
            super(helper, type, "jei.pioneers.fluid_deposit", PiChemistry.SEISMOGRAPH.get(), W, H);
        family(JeiDraw.Family.WORLD);
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, FluidDepositType recipe, @NotNull IFocusGroup focuses) {
            int x = 0;
            if (recipe.isGas()) {
                GasStack gas = recipe.gasStack(1000);
                if (!gas.isEmpty()) {
                    gas(builder, RecipeIngredientRole.OUTPUT, x, 0, 10, 40, gas, 1000);
                }
            } else if (recipe.fluid() != null) {
                fluid(builder, RecipeIngredientRole.OUTPUT, x, 0, 10, 40, recipe.fluid(), 1000, 1000);
            }
            x += 14;
            GasStack by = recipe.byproductStack(Math.max(1, Math.round(1000 * recipe.share())));
            if (!by.isEmpty()) {
                gas(builder, RecipeIngredientRole.OUTPUT, x, 0, 10, 40, by, 1000);
            }
        }

        /** 1.0.3-beta (correctif) : survol de la liste : tous les biomes, un par ligne. */
        @Override
        protected List<Component> contentTooltip(@NotNull FluidDepositType recipe, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
            return mouseY >= 44 ? JeiBiomes.tooltip(recipe, recipe.biomes()) : List.of();
        }

        @Override
        protected void drawContent(@NotNull FluidDepositType recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            int x = 30;
            fitted(graphics, Component.translatable(recipe.translationKey()), x, 1, SMALL, TEXT, W - x);
            fitted(graphics, Component.translatable("jei.pioneers.fluid_deposit.reserve", format(recipe.reserveMin()), format(recipe.reserveMax())), x, 11,
                  SMALL, TEXT_LIGHT, W - x);
            fitted(graphics, Component.translatable("jei.pioneers.fluid_deposit.rate", recipe.rate()), x, 20, SMALL, TEXT_LIGHT, W - x);
            if (recipe.byproduct() != null) {
                fitted(graphics, Component.translatable("jei.pioneers.fluid_deposit.share", Math.round(recipe.share() * 100)), x, 29, SMALL, TEXT_LIGHT,
                      W - x);
            }
            //1.0.3-beta (correctif) : noms exacts des biomes dans la langue du jeu (tags deplies), astre traduit ; liste complete au survol
            block(graphics, List.of(new Note(Component.translatable("jei.pioneers.deposit.where", JeiBiomes.joined(JeiBiomes.names(recipe, recipe.biomes())),
                  JeiBiomes.dimension(recipe.dimension())), TEXT)), 0, 44, SMALL, W, H);
        }

        /** Reserve en buckets (B), arrondie. */
        private static String format(long mb) {
            double b = mb / 1000.0;
            return b >= 1000 ? String.format(Locale.ROOT, "%.1f k", b / 1000) : String.format(Locale.ROOT, "%.0f", b);
        }
    }
}
