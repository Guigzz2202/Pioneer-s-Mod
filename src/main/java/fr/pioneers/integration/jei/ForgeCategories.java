package fr.pioneers.integration.jei;

import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.forge.HeatedMetal;
import fr.pioneers.forge.SmithingGrid;
import fr.pioneers.forge.SmithingRecipe;
import fr.pioneers.forge.automation.QuenchBasinTile;
import fr.pioneers.forge.smithing.HearthUnit;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.registries.PiBronze;
import fr.pioneers.registries.PiForge;
import java.util.ArrayList;
import java.util.List;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * Categories JEI de la forge primaire (0.21, specification §6) : feu de camp + creuset (fusion par metal), coulee en moule pose, foyer de
 * forge (lingot -> lingot chauffe), enclume (forme dessinee 16 x 16), trempe, martinet (forge au gabarit). Tout est calcule depuis les
 * donnees du jeu (table de fusion, {@link ForgeMetals}, recettes {@code pioneers:casting} et {@code pioneers:smithing}) : aucun chiffre en dur.
 */
final class ForgeCategories {

    //1.0.3-beta (lot 6) : plus de types « forge_crucible » (fonte : MeltingCategories.MELTING) ni « forge_mold » (coulee en moule pose :
    //reunie a la page « Coulee », PiJeiPlugin.CASTING, avec la pince, le creuset en argile et le moule d'enclume en catalyseurs)
    static final RecipeType<Heat> HEARTH = JeiOrder.create("forge_hearth", Heat.class);
    static final RecipeType<SmithingRecipe> ANVIL = JeiOrder.create("forge_anvil", SmithingRecipe.class);
    static final RecipeType<Quench> QUENCH = JeiOrder.create("forge_quench", Quench.class);

    private ForgeCategories() {
    }

    /** Chauffe au foyer : lingot -> lingot chauffe a la temperature de travail. */
    record Heat(String metal, ItemStack ingot, ItemStack heated) {
    }

    /** Trempe : piece chaude -> piece finie. */
    record Quench(List<ItemStack> hot, List<ItemStack> cold) {
    }

    // ------------------------------------------------------------------ enregistrement (appele par PiJeiPlugin)

    static void categories(IRecipeCategoryRegistration registration, IGuiHelper helper) {
        //1.0.3-beta (lot 6) : fonte et coulee reunies (MeltingCategories.MELTING, PiJeiPlugin.CASTING)
        registration.addRecipeCategories(new HearthCategory(helper),
              new SmithingCategory(helper, ANVIL, "jei.pioneers.forge.anvil", PiForge.IRON_ANVIL.getBlock(), true),
              new QuenchCategory(helper));
    }

    static void recipes(IRecipeRegistration registration, Level level) {
        List<Heat> heats = new ArrayList<>();
        for (String metal : ForgeMetals.forgeableMetals()) {
            ItemStack ingot = ForgeMetals.ingot(metal, 1);
            if (!ingot.isEmpty()) {
                heats.add(new Heat(metal, ingot, HeatedMetal.create(metal, ForgeMetals.INGOT_MB, ForgeMetals.workingC(metal), null)));
            }
        }
        //1.0.4-beta (lot 14) : le marteau-pilon industriel (ex-forge) est fondu dans le Marteau-pilon ; ses recettes de lopin que fait le foyer
        //(loupe d'acier -> lopin chaud : HearthUnit.hearthRecipe, entree qui n'est pas un lingot) sont montrees ici
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.POWER_HAMMER)) {
            if (HearthUnit.hearthRecipe(r)) {
                ItemStack[] in = r.items().get(0).ingredient().getItems();
                if (in.length > 0 && !ForgeMetals.isIngot(in[0])) {
                    heats.add(new Heat("steel", in[0].copyWithCount(1), r.results().get(0).stack().copy()));
                }
            }
        }
        registration.addRecipes(HEARTH, heats);
        List<SmithingRecipe> smithing = SmithingRecipe.all(level);
        registration.addRecipes(ANVIL, smithing);
        registration.addRecipes(QUENCH, quenches(smithing));
    }

    static void catalysts(IRecipeCatalystRegistration registration) {
        registration.addRecipeCatalysts(PiJeiPlugin.CASTING, PiForge.CRUCIBLE_TONGS.get(), PiForge.CLAY_CRUCIBLE.getBlock(), PiBronze.ANVIL_MOLD.get());
        //1.0.5-beta (lot 13) : le foyer de forge est catalyseur de HEARTH par sa fiche de l'API Machine (PiJeiPlugin.apiCatalysts)
        registration.addRecipeCatalysts(ANVIL, PiForge.IRON_ANVIL.getBlock());
        registration.addRecipeCatalysts(QUENCH, PiForge.QUENCH_BASIN.getBlock(), Items.CAULDRON, Items.WATER_BUCKET);
    }

    // ------------------------------------------------------------------ donnees

    /** Metaux acceptes par la recette et forgeables sur une enclume de ce tier (avec leur lingot). */
    static List<String> metals(SmithingRecipe r, int anvil) {
        List<String> out = new ArrayList<>();
        for (String metal : ForgeMetals.forgeableMetals()) {
            if (r.acceptsMetal(metal) && r.anvilTierFor(metal) <= anvil && !r.result(metal).isEmpty() && !ForgeMetals.ingot(metal, 1).isEmpty()) {
                out.add(metal);
            }
        }
        return out;
    }

    private static List<Quench> quenches(List<SmithingRecipe> smithing) {
        List<Quench> out = new ArrayList<>();
        //lingot chauffe brut -> lingot
        List<ItemStack> hot = new ArrayList<>(), cold = new ArrayList<>();
        for (String metal : ForgeMetals.forgeableMetals()) {
            ItemStack ingot = ForgeMetals.ingot(metal, 1);
            if (!ingot.isEmpty()) {
                hot.add(HeatedMetal.create(metal, ForgeMetals.INGOT_MB, ForgeMetals.workingC(metal), null));
                cold.add(ingot);
            }
        }
        out.add(new Quench(hot, cold));
        for (SmithingRecipe r : smithing) {
            List<ItemStack> h = new ArrayList<>(), c = new ArrayList<>();
            for (String metal : metals(r, ForgeMetals.ANVIL_IRON)) {
                ItemStack part = r.forge(HeatedMetal.create(metal, r.amount(), ForgeMetals.workingC(metal), null));
                if (!part.isEmpty()) {
                    h.add(part);
                    c.add(r.result(metal));
                }
            }
            if (!h.isEmpty()) {
                out.add(new Quench(h, c));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ categories

    /** Foyer de forge : lingot -> lingot chauffe a la temperature de travail, sans fondre. */
    static final class HearthCategory extends PiCategory<Heat> {

        HearthCategory(IGuiHelper helper) {
            super(helper, HEARTH, "jei.pioneers.forge.hearth", PiForge.FORGE_HEARTH.getBlock(), 130, 18);
            family(JeiDraw.Family.FORGE);
        }

        @Override
        protected List<JeiDraw.Chip> chips(Heat recipe) {
            int c = ForgeMetals.workingC(recipe.metal());
            return List.of(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp", c), JeiDraw.LIGHT,
                  Component.translatable("jei.pioneers.forge.working", c)));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Heat recipe, @NotNull IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(recipe.ingot());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 1).setStandardSlotBackground().addItemStack(recipe.heated());
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Heat recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(80).setPosition(28, 1);
        }

        @Override
        protected void drawContent(@NotNull Heat recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        }
    }

    /**
     * Enclume (minijeu) ou martinet (gabarit) : lingots du metal, forme dessinee (16 x 16, 2 px par case), piece. Les listes d'entree, de
     * sortie et d'enclume ont la meme longueur : JEI les fait tourner ensemble (meme metal).
     */
    static final class SmithingCategory extends PiCategory<SmithingRecipe> {

        private static final int PX = 2, GX = 44, GY = 1;
        private final boolean anvil;

        SmithingCategory(IGuiHelper helper, RecipeType<SmithingRecipe> type, String title, ItemLike icon, boolean anvil) {
            super(helper, type, title, icon, 150, 52);
            this.anvil = anvil;
            family(JeiDraw.Family.FORGE);
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, SmithingRecipe recipe, @NotNull IFocusGroup focuses) {
            List<String> metals = metals(recipe, ForgeMetals.ANVIL_IRON);
            List<ItemStack> in = new ArrayList<>(), out = new ArrayList<>(), anvils = new ArrayList<>();
            for (String metal : metals) {
                in.add(ForgeMetals.ingot(metal, recipe.ingots()));
                out.add(recipe.result(metal));
                //1.0.2-beta (lot 5) : une seule enclume (en fer) pour tous les metaux forgeables a la main
                anvils.add(new ItemStack(PiForge.IRON_ANVIL.getBlock()));
            }
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 9).setStandardSlotBackground().addItemStacks(in);
            builder.addSlot(RecipeIngredientRole.OUTPUT, 104, 9).setStandardSlotBackground().addItemStacks(out);
            if (anvil) {
                builder.addSlot(RecipeIngredientRole.CATALYST, 1, 30).setStandardSlotBackground().addItemStacks(anvils);
            }
        }

        @Override
        protected void drawContent(@NotNull SmithingRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            SmithingGrid target = recipe.target();
            int size = SmithingGrid.SIZE * PX;
            JeiDraw.gaugeFrame(graphics, GX - 1, GY - 1, size + 2, size + 2);
            for (int z = 0; z < SmithingGrid.SIZE; z++) {
                for (int x = 0; x < SmithingGrid.SIZE; x++) {
                    if (target.get(x, z)) {
                        graphics.fill(GX + x * PX, GY + z * PX, GX + x * PX + PX, GY + z * PX + PX, 0xFFFF9A3C);
                    }
                }
            }
            fitted(graphics, Component.translatable("jei.pioneers.forge.ingots", recipe.ingots(), recipe.pixels()), 82, 30, SMALL, TEXT, 68);
            fitted(graphics, Component.translatable(anvil ? "jei.pioneers.forge.working_hint" : "jei.pioneers.forge.trip"), 0, 44, SMALL, TEXT_LIGHT,
                  cw());
        }
    }

    /** Trempe : piece chaude -> piece finie (eau : bloc, chaudron, bac de trempe). */
    static final class QuenchCategory extends PiCategory<Quench> {

        QuenchCategory(IGuiHelper helper) {
            super(helper, QUENCH, "jei.pioneers.forge.quench", PiForge.QUENCH_BASIN.getBlock(), 130, 18);
            family(JeiDraw.Family.FORGE);
        }

        @Override
        protected List<JeiDraw.Chip> chips(Quench recipe) {
            return List.of(JeiDraw.Chip.of(JeiDraw.Pic.FLUID, Component.translatable("jei.pioneers.forge.amount", QuenchBasinTile.WATER_PER_QUENCH), JeiDraw.COLD,
                  Component.translatable("jei.pioneers.forge.water")));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Quench recipe, @NotNull IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(recipe.hot());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 1).setStandardSlotBackground().addItemStacks(recipe.cold());
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Quench recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(20).setPosition(28, 1);
        }

        @Override
        protected void drawContent(@NotNull Quench recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        }
    }
}
