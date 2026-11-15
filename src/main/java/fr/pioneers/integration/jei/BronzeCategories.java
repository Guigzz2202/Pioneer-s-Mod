package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.tech.bronze.foundry.CastingRecipe;
import fr.pioneers.tech.bronze.foundry.KilnRecipe;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.core.vanilla.PiVanillaMaterials;
import fr.pioneers.world.geology.DepositType;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.core.registries.PiBronze;
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
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import org.jetbrains.annotations.NotNull;

/** Categories JEI de l'age du bronze : ateliers, four de potier, coulee, haut fourneau, gisements. */
final class BronzeCategories {

    private BronzeCategories() {
    }

    // ------------------------------------------------------------------ ateliers

    static class Bench extends PiCategory<BenchRecipe> {

        Bench(IGuiHelper helper, RecipeType<BenchRecipe> type, BenchType bench) {
            super(helper, type, "block.pioneers." + bench.id(), PiBronze.BENCHES.get(bench).getBlock(), 150, 56);
            this.bench = bench;
            family(JeiDraw.Family.BENCH);
        }

        private final BenchType bench;

        /** 1.11.3 : badge « Etabli » a la couleur de l'etabli. */
        @Override
        protected JeiDraw.Badge badge(BenchRecipe recipe) {
            return new JeiDraw.Badge(Component.translatable("jei.pioneers.badge.bench"), bench.accent);
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, BenchRecipe recipe, @NotNull IFocusGroup focuses) {
            for (int i = 0; i < recipe.tools().size(); i++) {
                builder.addSlot(RecipeIngredientRole.CATALYST, 1 + i * 18, 1).setStandardSlotBackground().addIngredients(recipe.tools().get(i));
            }
            for (int i = 0; i < recipe.ingredients().size(); i++) {
                BenchRecipe.Counted c = recipe.ingredients().get(i);
                List<ItemStack> stacks = new ArrayList<>();
                for (ItemStack s : c.ingredient().getItems()) {
                    ItemStack copy = s.copy();
                    copy.setCount(c.count());
                    stacks.add(copy);
                }
                //Jusqu'a 5 par rangee (la derniere case finit a x = 90, avant la fleche en 96) ; deux rangees sous les outils au-dela
                int n = recipe.ingredients().size();
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 5) * 18, (n > 5 ? 20 : 22) + (i / 5) * 18)
                      .setStandardSlotBackground().addItemStacks(stacks);
            }
            //1.0.5-beta (lot 21) : reste de fabrication (le seau vide d'un seau de fluide) montre sous le produit
            List<ItemStack> rest = recipe.remainders();
            if (rest.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 128, 22).setStandardSlotBackground().addItemStack(recipe.result());
            } else {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 128, 13).setStandardSlotBackground().addItemStack(recipe.result());
                builder.addSlot(RecipeIngredientRole.OUTPUT, 128, 35).setStandardSlotBackground().addItemStacks(rest)
                      .addTooltipCallback((view, tips) -> tips.add(net.minecraft.network.chat.Component.translatable("jei.pioneers.bench.remainder")
                            .withStyle(net.minecraft.ChatFormatting.GRAY)));
            }
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull BenchRecipe recipe, @NotNull IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(96, 22);
        }

        @Override
        protected void drawContent(@NotNull BenchRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            if (!recipe.tools().isEmpty()) {
                int x = 1 + recipe.tools().size() * 18 + 3;
                if (x < cw() - 10) {
                    fitted(graphics, Component.translatable("jei.pioneers.bench.tools_wear"), x, 6, SMALL, TEXT_LIGHT, cw() - x);
                }
            }
        }
    }

    // ------------------------------------------------------------------ four de potier

    static class Kiln extends PiCategory<KilnRecipe> {

        Kiln(IGuiHelper helper, RecipeType<KilnRecipe> type) {
            super(helper, type, "block.pioneers.pottery_kiln", PiBronze.POTTERY_KILN.getBlock(), 120, 18);
            family(JeiDraw.Family.BRONZE);
        }

        @Override
        protected List<JeiDraw.Chip> chips(KilnRecipe recipe) {
            return List.of(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp", recipe.temperature()), JeiDraw.LIGHT,
                  Component.translatable("jei.pioneers.kiln", recipe.temperature(), seconds(recipe.time()))));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, KilnRecipe recipe, @NotNull IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addIngredients(recipe.input());
            builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 1).setStandardSlotBackground().addItemStack(recipe.result());
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull KilnRecipe recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(Math.max(20, recipe.time())).setPosition(26, 1);
        }

        @Override
        protected void drawContent(@NotNull KilnRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        }
    }

    // ------------------------------------------------------------------ coulee

    static class Casting extends PiCategory<CastingRecipe> {

        /**
         * 1.0.3-beta (lot 6, JEI des forges unifie) : LA page « Coulee » : table de coulee, moule automatique (fourneau a creuset, fonderie) ET
         * moule pose rempli a la pince depuis le creuset en argile (ex-page « Coulee en moule » {@code pioneers:forge_mold}, qui repetait les
         * memes recettes) ; une pastille dit ou chaque coulee se fait.
         */
        Casting(IGuiHelper helper, RecipeType<CastingRecipe> type) {
            super(helper, type, "jei.pioneers.casting_all", PiBronze.CASTING_TABLE.getBlock(), 120, 18);
            family(JeiDraw.Family.BRONZE);
            footerLines(2);
        }

        /** La coulee se fait-elle au moule pose (pince + creuset en argile : 2 lingots au plus, ou le moule d'enclume en plusieurs fois) ? */
        static boolean byTongs(CastingRecipe recipe) {
            return fr.pioneers.tech.forge.ForgeMetals.crucible(recipe.metal())
                   && (recipe.amount() <= fr.pioneers.tech.forge.MoltenLoad.CLAY_CRUCIBLE_MB || recipe.mold() == PiBronze.ANVIL_MOLD.get());
        }

        @Override
        protected List<JeiDraw.Chip> chips(CastingRecipe recipe) {
            Component metal = Metals.nameOrMixed(recipe.metal());
            List<JeiDraw.Chip> out = new ArrayList<>();
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.FLUID, Component.translatable("jei.pioneers.casting", recipe.amount(), metal), JeiDraw.LIGHT));
            if (recipe.mold() != PiBronze.ANVIL_MOLD.get()) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.casting_all.table"), JeiDraw.LIGHT,
                      Component.translatable("jei.pioneers.casting_all.table_tip")));
            }
            if (byTongs(recipe)) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.casting_all.tongs"), JeiDraw.LIGHT,
                      Component.translatable("jei.pioneers.casting_all.tongs_tip", fr.pioneers.tech.forge.MoltenLoad.CLAY_CRUCIBLE_MB)));
            }
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.COLD, Component.translatable("jei.pioneers.forge.cool", recipe.coolTicks() / 20), JeiDraw.DIM));
            return out;
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, CastingRecipe recipe, @NotNull IFocusGroup focuses) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 1, 1).setStandardSlotBackground().addItemStack(new ItemStack(recipe.mold()));
            List<ItemStack> metal = new ArrayList<>();
            for (GeneratedMaterials.Melt melt : GeneratedMaterials.MELTS) {
                if (melt.metal().equals(recipe.metal()) && melt.mb() == Metals.INGOT) {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(melt.item()));
                    if (item != Items.AIR) {
                        metal.add(new ItemStack(item, Math.max(1, recipe.amount() / Metals.INGOT)));
                    }
                }
            }
            if (!metal.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 20, 1).setStandardSlotBackground().addItemStacks(metal);
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 74, 1).setStandardSlotBackground().addItemStack(recipe.result());
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull CastingRecipe recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(Math.max(20, recipe.coolTicks())).setPosition(44, 1);
        }

        @Override
        protected void drawContent(@NotNull CastingRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        }
    }

    // ------------------------------------------------------------------ haut fourneau

    record Blast(List<ItemStack> iron) {
    }

    static class BlastCategory extends PiCategory<Blast> {

        BlastCategory(IGuiHelper helper, RecipeType<Blast> type) {
            super(helper, type, "block.pioneers.blast_furnace", PiBronze.BLAST_FURNACE.getBlock(), 150, 66);
            family(JeiDraw.Family.BRONZE);
        }

        @Override
        protected List<JeiDraw.Chip> chips(Blast recipe) {
            return List.of(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp_min", 1500), JeiDraw.LIGHT),
                  JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.chip.multiblock_page"), JeiDraw.DIM));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Blast recipe, @NotNull IFocusGroup focuses) {
            //1.0.4-beta (lot 9) : les trois cases du four (fer, carbone, fondant), chacune avec son role en infobulle
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStacks(recipe.iron())
                  .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("gui.pioneers.blast.slot.iron")));
            builder.addSlot(RecipeIngredientRole.INPUT, 19, 1).setStandardSlotBackground().addItemStacks(List.of(new ItemStack(PiVanillaMaterials.CHARCOAL.get()),
                  new ItemStack(PiVanillaMaterials.COAL.get()), new ItemStack(PiVanillaMaterials.CHARCOAL_BLOCK.asItem()),
                  new ItemStack(PiVanillaMaterials.COAL_BLOCK.asItem())))
                  .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("gui.pioneers.blast.slot.carbon")));
            builder.addSlot(RecipeIngredientRole.INPUT, 37, 1).setStandardSlotBackground().addItemStack(new ItemStack(PiBronze.QUICKLIME.get()))
                  .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("gui.pioneers.blast.slot.flux")));
            Item steel = BuiltInRegistries.ITEM.get(Pioneers.rl("steel_ingot"));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 94, 1).setStandardSlotBackground().addItemStack(new ItemStack(steel));
            builder.addSlot(RecipeIngredientRole.OUTPUT, 114, 1).setStandardSlotBackground().addItemStack(new ItemStack(PiBronze.SLAG.get()));
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Blast recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(200).setPosition(62, 1);
        }

        @Override
        protected void drawContent(@NotNull Blast recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            fr.pioneers.client.gui.GuiDraw.screen(graphics, 0, 21, cw(), ch() - 21);
            block(graphics, List.of(new Note(Component.translatable("jei.pioneers.blast"), TEXT)), 3, 23.5F, SMALL, cw() - 6, ch() - 1);
        }
    }

    static List<Blast> blast() {
        List<ItemStack> iron = new ArrayList<>();
        for (String id : new String[]{"pioneers:raw_iron", "pioneers:crushed_iron", "pioneers:iron_ingot", "pioneers:magnetite_fragment",
              "pioneers:hematite_fragment"}) {
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id));
            if (item != Items.AIR) {
                iron.add(new ItemStack(item, id.contains("fragment") ? 4 : 1));
            }
        }
        return List.of(new Blast(iron));
    }

    // ------------------------------------------------------------------ gisements

    static class Deposit extends PiCategory<DepositType> {

        Deposit(IGuiHelper helper, RecipeType<DepositType> type) {
            super(helper, type, "jei.pioneers.deposit", PiBronze.STEAM_DRILL.getBlock(), 150, 96);   //1.0.3-beta : place pour les biomes
            family(JeiDraw.Family.WORLD);
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, DepositType recipe, @NotNull IFocusGroup focuses) {
            if (recipe.outcrop() != null) {
                builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(new ItemStack(recipe.outcrop()));
            }
            builder.addSlot(RecipeIngredientRole.OUTPUT, 60, 1).setStandardSlotBackground().addItemStack(new ItemStack(recipe.result()));
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull DepositType recipe, @NotNull IFocusGroup focuses) {
            builder.addRecipeArrow().setPosition(28, 1);
        }

        /** 1.0.3-beta (correctif) : survol de la liste : tous les biomes, un par ligne. */
        @Override
        protected List<Component> contentTooltip(@NotNull DepositType recipe, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
            return mouseY >= 22 ? JeiBiomes.tooltip(recipe, recipe.biomes()) : List.of();
        }

        @Override
        protected void drawContent(@NotNull DepositType recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            fitted(graphics, Component.translatable(recipe.translationKey()), 82, 2, SMALL, TEXT, cw() - 82);
            fitted(graphics, Component.translatable("jei.pioneers.deposit.tier", recipe.tier()), 82, 11, SMALL, TEXT_LIGHT, cw() - 82);
            //1.0.3-beta (correctif) : noms exacts des biomes dans la langue du jeu (tags deplies), astre traduit ; liste complete au survol
            block(graphics, List.of(new Note(Component.translatable("jei.pioneers.deposit.where", JeiBiomes.joined(JeiBiomes.names(recipe, recipe.biomes())),
                  JeiBiomes.dimension(recipe.dimension())), TEXT)), 0, 22, SMALL, cw(), ch() - 1);
        }
    }

    // ------------------------------------------------------------------ machines a vapeur

    static class Steam extends PiCategory<SteamRecipe> {

        Steam(IGuiHelper helper, RecipeType<SteamRecipe> type, SteamMachine machine) {
            //1.0.3-beta (lot 11b) : le melangeur a 4 entrees et l'eau : page plus large
            //1.0.5-beta (lot 44) : machine a case Outil (presse a vapeur) : une rangee de plus, l'outil sous la fleche
            super(helper, type, "block.pioneers." + machine.id(), PiBronze.STEAM_MACHINES.get(machine).getBlock(), machine == SteamMachine.MIXER ? 160 : 130,
                  Remanents.hasToolSlot(Remanents.STEAM + machine.key()) ? 38 : 18);
            family(JeiDraw.Family.STEAM);
        }

        /** 1.0.5-beta (lot 44) : entree outil de la recette (lue dans la case Outil de la machine), ou -1. */
        private static int tool(SteamRecipe recipe) {
            return Remanents.toolInput(Remanents.STEAM + recipe.machine().key(), recipe.inputs());
        }

        /** 1.0.5-beta (lot 44) : le sous-produit est-il l'outil rendu ? */
        private static boolean toolBack(SteamRecipe recipe, int tool) {
            return tool >= 0 && !recipe.byproduct().isEmpty() && recipe.inputs().get(tool).ingredient().test(recipe.byproduct());
        }

        @Override
        protected List<JeiDraw.Chip> chips(SteamRecipe recipe) {
            //1.0.5-beta (lot 38) : + « Tier min. » de la recette (les machines MKI de la famille la font aussi)
            return List.of(JeiDraw.Chip.of(JeiDraw.Pic.STEAM, Component.translatable("jei.pioneers.chip.steam"), JeiDraw.COLD,
                  Component.translatable("jei.pioneers.steam_machine", seconds(recipe.time()),
                        fr.pioneers.core.Texts.format(Math.round(recipe.time() * recipe.machine().steamUse())))),
                  TierChips.minTier(recipe.minTier(), recipe.getId(), recipe.inputs(), recipe.result()));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, SteamRecipe recipe, @NotNull IFocusGroup focuses) {
            int tool = tool(recipe), shown = 0;
            boolean back = toolBack(recipe, tool);
            for (int i = 0; i < recipe.inputs().size(); i++) {
                BenchRecipe.Counted c = recipe.inputs().get(i);
                List<ItemStack> stacks = new ArrayList<>();
                for (ItemStack st : c.ingredient().getItems()) {
                    ItemStack copy = st.copy();
                    copy.setCount(c.count());
                    stacks.add(copy);
                }
                if (i == tool) {
                    //1.0.5-beta (lot 44, retour 62) : l'outil (matrice) dans sa case, sous la fleche
                    toolSlot(builder, steamArrow(recipe) + 3, 21, stacks, back, back ? recipe.chance() : 0F, false);
                    continue;
                }
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + shown * 18, 1).setStandardSlotBackground().addItemStacks(stacks);
                shown++;
            }
            if (recipe.water() > 0) {
                fluid(builder, RecipeIngredientRole.INPUT, shown * 18, 0, 18, 18, net.minecraft.world.level.material.Fluids.WATER,
                      recipe.water(), recipe.water());
            }
            int out = steamArrow(recipe) + 30;
            builder.addSlot(RecipeIngredientRole.OUTPUT, out, 1).setStandardSlotBackground().addItemStack(recipe.result());
            if (!recipe.byproduct().isEmpty() && !back) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, out + 22, 1).setStandardSlotBackground().addItemStack(recipe.byproduct())
                      .setOverlay(new JeiDraw.Mark(JeiDraw.percent(recipe.chance()), true), 0, 0)
                      .addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.mechanical.chance",
                            Math.round(recipe.chance() * 100))));
            }
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull SteamRecipe recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(Math.max(20, recipe.time())).setPosition(steamArrow(recipe), 1);
        }

        /** Fleche en x = 40, decalee a droite des entrees s'il y en a plus de deux. */
        private static int steamArrow(SteamRecipe recipe) {
            return Math.max(40, (recipe.inputs().size() - (tool(recipe) >= 0 ? 1 : 0) + (recipe.water() > 0 ? 1 : 0)) * 18 + 4);
        }

        @Override
        protected void drawContent(@NotNull SteamRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX,
              double mouseY) {
        }
    }
}
