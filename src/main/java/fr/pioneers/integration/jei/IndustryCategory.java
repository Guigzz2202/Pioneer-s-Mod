package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.core.Texts;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.industry.GeneratedFusions;
import fr.pioneers.tech.industry.GeneratorOutput;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.multi.Cleanroom;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.Remanents;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.machine.PiMachineKit;
import fr.pioneers.logistics.transport.CableTier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * Categorie JEI d'une machine de l'age industriel (machine d'atelier et multibloc ensemble) : entrees (objets, fluides, gaz), sorties avec
 * leurs chances, energie produite pour les generateurs ; en bas : tier minimal, duree, consommation, « multibloc seulement ».
 * 1.0.4-beta (lot 32, Guillaume : « Garder, masquer partout ») : page d'une machine de SALLE BLANCHE ({@link Cleanroom}) : aucun tier
 * (badge « Salle blanche », pas de pastille MK), duree et FE/t reels de la machine, operations a la fois.
 */
class IndustryCategory extends PiCategory<IndustrialRecipe> {

    private static final int W = 176, H = 56, GAUGE_H = 40;
    private final IndustryMachine machine;
    /** 1.0.4-beta (lot 32) : page d'une machine de salle blanche (sans tier) : la seule machine de la page en est une. */
    private final boolean cleanroom;

    IndustryCategory(IGuiHelper helper, RecipeType<IndustrialRecipe> type, IndustryMachine machine, ItemLike icon) {
        this(helper, type, machine, icon, false);
    }

    /**
     * {@code cleanroomPage} : page propre au multibloc de salle blanche d'une machine qui a aussi des compactes (graveur optique : la page
     * commune garde la pastille MK des compactes). Une machine de salle blanche sans compacte (usine de semi-conducteurs) a d'office sa page
     * sans tier.
     */
    IndustryCategory(IGuiHelper helper, RecipeType<IndustrialRecipe> type, IndustryMachine machine, ItemLike icon, boolean cleanroomPage) {
        super(helper, type, cleanroomPage || machine.atelier == IndustryMachine.Atelier.NONE ? "block.pioneers." + machine.multiId()
              : machine.translationKey(), icon, W, H);
        this.machine = machine;
        this.cleanroom = Cleanroom.is(machine) && (cleanroomPage || machine.atelier == IndustryMachine.Atelier.NONE);
        footerLines(2);
    }

    /** Duree affichee d'une operation (ticks) : celle de la salle blanche, sinon celle de la recette (machine de son tier minimal). */
    private int shownTime(IndustrialRecipe recipe) {
        return cleanroom ? Cleanroom.ticks(machine, recipe.time(), recipe.tier()) : recipe.time();
    }

    /**
     * Disposition des jauges : largeur de jauge et nombre de rangees choisis pour que tout tienne dans la largeur de la categorie
     * (une rangee de jauges de 40 px si possible, sinon deux rangees de demi-jauges, de plus en plus etroites).
     */
    private record Layout(int gw, int rows) {

        int step() {
            return gw + 2;
        }

        int gh() {
            return rows == 1 ? GAUGE_H : (GAUGE_H - 2) / 2;
        }

        /** Largeur occupee par un groupe de {@code n} jauges (espace de 2 px compris apres la derniere). */
        int group(int n) {
            return (rows == 1 ? n : (n + 1) / 2) * step();
        }

        int gx(int k) {
            return (rows == 1 ? k : k / 2) * step();
        }

        int gy(int k) {
            return rows == 1 ? 0 : (k % 2) * (gh() + 2);
        }
    }

    private static final Layout[] LAYOUTS = {new Layout(10, 1), new Layout(8, 1), new Layout(10, 2), new Layout(8, 2), new Layout(6, 2)};

    private static int itemsWidth(int n, int maxCols) {
        return n == 0 ? 0 : Math.min(maxCols, n) * 18 + 2;
    }

    private static Layout layout(IndustrialRecipe recipe) {
        for (Layout l : LAYOUTS) {
            if (width(recipe, l) - 2 <= W) {
                return l;
            }
        }
        return LAYOUTS[LAYOUTS.length - 1];
    }

    private static int width(IndustrialRecipe recipe, Layout l) {
        return arrowX(recipe, l) + ARROW_SPACE + itemsWidth(outputs(recipe), 2) + l.group(recipe.fluidResults().size() + recipe.gasResults().size());
    }

    /** Cases de sortie : objets, plus une case qui fait defiler le tirage pondere (recycleur) ; lot 44 : sans l'outil rendu (case Outil). */
    private static int outputs(IndustrialRecipe recipe) {
        Remanents.Tool t = recipe.tool();
        return recipe.results().size() + (recipe.draws() != null ? 1 : 0) - (t != null && t.returned() ? 1 : 0);
    }

    /** Cases d'entree : objets puis donnees ; lot 44 : sans l'outil (sa case est sous la fleche). */
    private static int inputs(IndustrialRecipe recipe) {
        return recipe.items().size() + recipe.data().size() - (recipe.tool() != null ? 1 : 0);
    }

    /** Place reservee a la fleche (22 px) et a ses marges entre les entrees et les sorties. */
    private static final int ARROW_SPACE = 26;

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, IndustrialRecipe recipe, @NotNull IFocusGroup focuses) {
        Layout l = layout(recipe);
        int x = 0;
        int cols = inputs(recipe) > 9 ? 4 : Math.min(3, Math.max(1, inputs(recipe)));
        Remanents.Tool tool = recipe.tool();
        int placed = 0;
        for (int i = 0; i < recipe.items().size(); i++) {
            Counted c = recipe.items().get(i);
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack st : c.ingredient().getItems()) {
                stacks.add(st.copyWithCount(c.count()));
            }
            if (tool != null && tool.input() == i) {
                //1.0.5-beta (lot 44, retour 62) : l'outil dans sa case, sous la fleche (comme dans la machine)
                toolSlot(builder, arrowX(recipe, l) + 5, 35, stacks, tool.returned(), tool.keep(), tool.wear());
                continue;
            }
            builder.addSlot(RecipeIngredientRole.INPUT, x + 1 + (placed % cols) * 18, 1 + (placed / cols) * 18).setStandardSlotBackground().addItemStacks(stacks);
            placed++;
        }
        //0.20.34 : donnees (synthetiseur) a la suite des objets
        for (int j = 0; j < recipe.data().size(); j++) {
            int i = placed + j;
            data(builder, RecipeIngredientRole.INPUT, x + 1 + (i % cols) * 18, 1 + (i / cols) * 18, recipe.data().get(j));
        }
        x += itemsWidth(inputs(recipe), cols);
        int k = 0;
        for (IndustrialRecipe.FluidIn in : recipe.fluids()) {
            List<FluidStack> shown = in.displayed();
            builder.addSlot(RecipeIngredientRole.INPUT, x + l.gx(k) + 1, l.gy(k) + 1).setFluidRenderer(Math.max(1, in.amount()), false, l.gw() - 2, l.gh() - 2)
                  .addIngredients(mezz.jei.api.forge.ForgeTypes.FLUID_STACK, shown).setBackground(new Frame(l.gw(), l.gh()), -1, -1);
            k++;
        }
        for (IndustrialRecipe.GasAmount in : recipe.gases()) {
            GasStack stack = in.stack();
            if (!stack.isEmpty()) {
                gas(builder, RecipeIngredientRole.INPUT, x + l.gx(k), l.gy(k), l.gw(), l.gh(), stack, stack.getAmount());
            }
            k++;
        }
        x += l.group(k) + ARROW_SPACE;
        int ocols = Math.min(2, Math.max(1, outputs(recipe)));
        IndustrialRecipe.Draws draws = recipe.draws();
        if (draws != null) {
            //1.9 (MAJ RP) : tirage pondere du recycleur, une case qui fait defiler les lots (chance de chaque lot dans l'infobulle)
            int i = outputs(recipe) - 1;
            java.util.List<net.minecraft.world.item.ItemStack> stacks = new java.util.ArrayList<>();
            for (IndustrialRecipe.Draw d : draws.pool()) {
                stacks.add(d.item().copyWithCount(d.max()));
            }
            int total = Math.max(1, draws.total());
            builder.addSlot(RecipeIngredientRole.OUTPUT, x + 1 + (i % ocols) * 18, 1 + (i / ocols) * 18).setStandardSlotBackground().addItemStacks(stacks)
                  .addRichTooltipCallback((view, tooltip) -> view.getDisplayedIngredient(mezz.jei.api.constants.VanillaTypes.ITEM_STACK).ifPresent(shown -> {
                      for (IndustrialRecipe.Draw d : draws.pool()) {
                          if (net.minecraft.world.item.ItemStack.isSameItem(d.item(), shown)) {
                              tooltip.add(Component.translatable("jei.pioneers.recycler.draw", Math.round(d.weight() * 1000.0 / total) / 10.0, d.min(),
                                    d.max()));
                          }
                      }
                      tooltip.add(Component.translatable("jei.pioneers.recycler.rolls", draws.min(), draws.max()));
                  }));
        }
        int place = 0;
        for (int i = 0; i < recipe.results().size(); i++) {
            IndustrialRecipe.Result r = recipe.results().get(i);
            if (tool != null && tool.result() == i) {
                continue;                                                 // lot 44 : l'outil rendu reste dans sa case (infobulle de la case Outil)
            }
            int at = place++;
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x + 1 + (at % ocols) * 18, 1 + (at / ocols) * 18).setStandardSlotBackground().addItemStack(r.stack());
            if (r.chance() < 1F) {
                chance(slot, r.chance());
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.mechanical.chance", Math.round(r.chance() * 100))));
            }
            if (i == 0 && recipe.ore() && !cleanroom) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.industry.ore_bonus")));
            }
            if (i == 0 && recipe.impurity() > 0 && !cleanroom) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.industry.impurity", recipe.impurity())));
            }
        }
        x += itemsWidth(outputs(recipe), 2);
        k = 0;
        for (FluidStack out : recipe.fluidResults()) {
            var slot = builder.addSlot(RecipeIngredientRole.OUTPUT, x + l.gx(k) + 1, l.gy(k) + 1)
                  .setFluidRenderer(Math.max(1, recipe.hasTierYield() && k == 0 && (recipe.results().isEmpty() || recipe.yieldOnFluid()) ? recipe.yieldedFluid(3) : out.getAmount()),
                        false, l.gw() - 2, l.gh() - 2)
                  .addFluidStack(out.getFluid(), out.getAmount()).setBackground(new Frame(l.gw(), l.gh()), -1, -1);
            if (k == 0 && recipe.hasTierYield() && recipe.results().isEmpty() && !cleanroom) {
                //1.0.3-beta (lot 3) : quantite produite a chaque tier (tier_yield)
                slot.addRichTooltipCallback((view, tooltip) -> yieldLines(recipe).forEach(tooltip::add));
            }
            k++;
        }
        for (IndustrialRecipe.GasAmount out : recipe.gasResults()) {
            GasStack stack = out.stack();
            if (!stack.isEmpty()) {
                gas(builder, RecipeIngredientRole.OUTPUT, x + l.gx(k), l.gy(k), l.gw(), l.gh(), stack, stack.getAmount());
            }
            k++;
        }
    }

    private static int arrowX(IndustrialRecipe recipe, Layout l) {
        return itemsWidth(inputs(recipe), inputs(recipe) > 9 ? 4 : 3) + l.group(recipe.fluids().size() + recipe.gases().size());
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull IndustrialRecipe recipe, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(Math.max(20, shownTime(recipe))).setPosition(arrowX(recipe, layout(recipe)) + 2, 12);
    }

    /** 1.11.3 : badge du tier minimal (watermark MK en bas a droite, meme dessin qu'avant, dessine par la charte). */
    @Override
    protected JeiDraw.Badge badge(IndustrialRecipe recipe) {
        //1.0.4-beta (lot 32) : salle blanche : badge « Salle blanche », jamais de tier
        return cleanroom ? JeiDraw.Badge.of(JeiDraw.Family.CLEANROOM) : JeiDraw.Badge.tier(recipe.tier());
    }

    /**
     * 1.11.3 (charte) : pied de page en pastilles. Ligne 1 : energie (FE/t x duree = total au tier minimal ; generateurs : FE produits, detail par
     * tier au survol ; forge : temperature). Ligne 2 : conditions (pression, climat, multibloc seul, entrees pures, affinage).
     */
    /** 1.0.3-beta (lot 3) : une ligne par tier : « MKI ×1,5 : 216 mB » (rendement du produit principal, champ tier_yield). */
    static List<Component> yieldLines(IndustrialRecipe recipe) {
        List<Component> out = new ArrayList<>();
        boolean fluid = (recipe.results().isEmpty() || recipe.yieldOnFluid()) && !recipe.fluidResults().isEmpty();
        for (int t = Math.max(0, recipe.tier()); t < 4; t++) {
            String mult = MeltingCategories.number(recipe.tierYield(t));
            String amount = fluid ? recipe.yieldedFluid(t) + " mB" : MeltingCategories.number(recipe.results().get(0).stack().getCount() * recipe.tierYield(t));
            out.add(Component.translatable("jei.pioneers.industry.tier_yield.line", Component.translatable("industry.pioneers.tier." + Tiers.tier(t).id()),
                  mult, amount).withStyle(ChatFormatting.GREEN));
        }
        return out;
    }

    @Override
    protected List<JeiDraw.Chip> chips(IndustrialRecipe recipe) {
        List<JeiDraw.Chip> out = new ArrayList<>();
        //1.0.6 (demande de Guillaume du 16/11/2026) : plus de pastille de famille de recettes (familyChip n'est plus montree)
        if (recipe.hasTierYield() && !cleanroom) {
            //1.0.3-beta (lot 3, regle 9) : multiplicateur du tier en pastille (fonte du minerai au creuset electrique, lixiviation acide)
            List<Component> tip = new ArrayList<>(List.of(Component.translatable("jei.pioneers.industry.tier_yield.tip")));
            tip.addAll(yieldLines(recipe));
            out.add(new JeiDraw.Chip(JeiDraw.Pic.YIELD, Component.translatable("jei.pioneers.chip.tier_yield",
                  MeltingCategories.number(recipe.tierYield(recipe.tier())), MeltingCategories.number(recipe.tierYield(3))), JeiDraw.GOOD, tip));
        }
        Component tier = Component.translatable("industry.pioneers.tier." + Tiers.tier(recipe.tier()).id());
        String time = String.format(Locale.ROOT, "%.1f s", shownTime(recipe) / 20.0);
        if (cleanroom && recipe.energy() <= 0 && recipe.temperature() <= 0) {
            //1.0.4-beta (lot 32) : salle blanche : FE/t d'une operation et duree REELLES de la machine (son niveau fixe ne s'affiche pas)
            long power = Cleanroom.opPower(machine);
            List<Component> tip = new ArrayList<>(List.of(Component.translatable("jei.pioneers.cleanroom.line", time)));
            Cleanroom.lines(machine).forEach(c -> tip.add(c.copy().withStyle(ChatFormatting.GRAY)));
            out.add(new JeiDraw.Chip(JeiDraw.Pic.ENERGY, Component.translatable("jei.pioneers.chip.energy", Texts.grouped(power), time,
                  Texts.grouped(power * Math.max(1, shownTime(recipe)))), JeiDraw.ENERGY, tip));
        } else if (cleanroom && recipe.energy() > 0) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.ENERGY, Component.translatable("jei.pioneers.chip.energy_op", Texts.energy(recipe.energy()), time),
                  JeiDraw.ENERGY, Component.translatable("jei.pioneers.cleanroom.energy_total", time, Texts.energy(recipe.energy()))));
        } else if (recipe.energy() > 0 && !recipe.machine().isGenerator()) {
            //0.20.34 : energie totale par operation (compresseur gravitationnel, synthetiseur)
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.ENERGY, Component.translatable("jei.pioneers.chip.energy_op", Texts.energy(recipe.energy()), time),
                  JeiDraw.ENERGY, Component.translatable("jei.pioneers.industry.energy_total", tier, time, Texts.energy(recipe.energy()))));
        } else if (recipe.energy() > 0) {
            //1.11.2 (lot E2) : FE/t et FE au total (GeneratorOutput) ; multibloc seul : rendement x1,25 compte ; detail par tier au survol
            boolean multi = recipe.machine().atelier == IndustryMachine.Atelier.NONE;
            Component text = Component.translatable("jei.pioneers.chip.energy_out", Texts.grouped(GeneratorOutput.perTick(recipe, recipe.tier(), multi)),
                  String.format(Locale.ROOT, "%.1f s", GeneratorOutput.duration(recipe.time(), recipe.tier()) / 20.0),
                  Texts.grouped(GeneratorOutput.total(recipe, multi)));
            out.add(new JeiDraw.Chip(JeiDraw.Pic.ENERGY, text, JeiDraw.GOOD, generatorTiers(recipe)));
        } else if (recipe.temperature() > 0) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp", recipe.temperature()), JeiDraw.LIGHT,
                  Component.translatable("jei.pioneers.industry.forge", recipe.temperature(), time)));
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.TIME, Component.literal(time), JeiDraw.LIGHT));
        } else {
            long power = Tiers.power(recipe.tier());
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.ENERGY, Component.translatable("jei.pioneers.chip.energy", Texts.grouped(power), time,
                        Texts.grouped(power * Math.max(1, recipe.time()))), JeiDraw.ENERGY,
                  Component.translatable("jei.pioneers.industry.line", tier, time, Texts.energy(power)),
                  Component.translatable("jei.pioneers.chip.energy_tip").withStyle(ChatFormatting.GRAY)));
        }
        if (recipe.pressure() > 0) {
            //Pression de procede (port de pression) : sinon le lot tourne a 50 %
            String bar = String.format(Locale.ROOT, "%.0f", recipe.pressure());
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.PRESSURE, Component.translatable("jei.pioneers.chip.pressure", bar, recipe.air()), JeiDraw.COLD,
                  Component.translatable("jei.pioneers.industry.pressure", bar, recipe.air())));
        } else if (recipe.machine().style == IndustryStyle.LIVING) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.CLIMATE, Component.translatable("jei.pioneers.chip.climate", (int) ControllerTile.FARM_MIN,
                  (int) ControllerTile.FARM_MAX), JeiDraw.GOOD, Component.translatable("jei.pioneers.industry.climate", (int) ControllerTile.FARM_MIN,
                  (int) ControllerTile.FARM_MAX)));
        }
        if (!cleanroom && !recipe.machine().isGenerator()) {
            //1.0.6 : tiers de machine qui font la recette (MK minimal a MKIV ; T1 si la recette est ouverte a la machine a vapeur, lot 47)
            String family = fr.pioneers.tech.industry.recipe.IndustryRecipes.tierFamily(recipe.machine());
            int min = TierChips.MKI + Math.max(0, recipe.tier());
            if (fr.pioneers.api.machine.tier.GeneratedTierFamilies.OPEN_MKI.containsKey(recipe.getId().toString())) {
                min = 1;
            }
            out.add(TierChips.tiers(min, TierChips.MAX, family, null, List.of(), ItemStack.EMPTY));
        }
        if (recipe.industrialOnly()) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.WARN, Component.translatable("jei.pioneers.chip.multi_only"), JeiDraw.WARN,
                  Component.translatable("jei.pioneers.industry.multi_only")));
        }
        if (recipe.pure()) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.WARN, Component.translatable("jei.pioneers.chip.pure"), JeiDraw.WARN,
                  Component.translatable("jei.pioneers.industry.pure")));
        } else if (recipe.impureOnly()) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.chip.refine"), JeiDraw.GOOD,
                  Component.translatable("jei.pioneers.industry.refine")));
        }
        return out;
    }

    /**
     * 1.0.4-beta (lot 11, fusion des machines) : pastille de la FAMILLE d'une recette d'une machine de base qui en a absorbe d'autres (Four
     * industriel : cuisson, oxyder, reduire, fritter, traiter, doper...) ; au survol, l'ancienne machine d'ou vient la recette. Null pour les
     * autres machines.
     */
    @org.jetbrains.annotations.Nullable
    static JeiDraw.Chip familyChip(IndustrialRecipe recipe) {
        if (!GeneratedFusions.BASE.containsValue(recipe.machine().key)) {
            return null;
        }
        String fam = recipe.family() != null ? recipe.family() : recipe.machine().key;
        Component name = Component.translatable("jei.pioneers.family." + fam);
        List<Component> tip = new ArrayList<>(List.of(Component.translatable("jei.pioneers.family", name)));
        if (recipe.family() != null) {
            tip.add(Component.translatable("jei.pioneers.family.tip", Component.translatable("jei.pioneers.family.former." + fam))
                  .withStyle(ChatFormatting.GRAY));
        }
        return new JeiDraw.Chip(JeiDraw.Pic.INFO, name, JeiDraw.LIGHT, tip);
    }

    /** 1.0.4-beta (lot 11) : rang de la famille d'une recette (recettes propres d'abord, puis l'ordre de la table des fusions). */
    static int familyRank(IndustrialRecipe recipe) {
        if (recipe.family() == null) {
            return 0;
        }
        int i = 1;
        for (String k : new java.util.TreeSet<>(GeneratedFusions.BASE.keySet())) {
            if (k.equals(recipe.family())) {
                return i;
            }
            i++;
        }
        return i;
    }

    /** 1.11.2 : detail d'une recette de generateur : FE/t et FE au total a chaque tier, machine d'atelier et multibloc (infobulle de la pastille). */
    private static List<Component> generatorTiers(IndustrialRecipe recipe) {
        List<Component> out = new ArrayList<>();
        out.add(Component.translatable("jei.pioneers.fe.title").withStyle(ChatFormatting.YELLOW));
        if (recipe.machine().atelier != IndustryMachine.Atelier.NONE) {
            for (int t = recipe.tier(); t < 4; t++) {
                out.add(Component.translatable("energy.pioneers.fe.kit_tier", Tooltips.tierName(t), Texts.grouped(GeneratorOutput.perTick(recipe, t, false)),
                      Texts.grouped(GeneratorOutput.total(recipe, false))).withStyle(ChatFormatting.GRAY));
            }
        }
        for (int t = recipe.tier(); t < 4; t++) {
            out.add(Component.translatable("energy.pioneers.fe.multi_tier", Tooltips.tierName(t), Texts.grouped(GeneratorOutput.perTick(recipe, t, true)),
                  Texts.grouped(GeneratorOutput.total(recipe, true))).withStyle(ChatFormatting.GRAY));
        }
        out.add(Component.translatable("jei.pioneers.fe.parallel").withStyle(ChatFormatting.DARK_GRAY));
        return out;
    }

    /** Badge du tier minimal d'une recette (1.11.3 : dessine par la charte, {@link JeiDraw#badge}). */
    static void tierBadge(GuiGraphics graphics, int x, int y, int tier) {
        JeiDraw.badge(graphics, x, y, JeiDraw.Badge.tier(tier));
    }

    /** Icone de la categorie : la machine d'atelier basique, sinon le four a coke, sinon le controleur (1.0.2-beta : le marteau-pilon industriel). */
    static ItemLike icon(IndustryMachine machine) {
        if (PiIndustry.ATELIERS.containsKey(machine)) {
            return PiIndustry.ATELIERS.get(machine).get(CableTier.BASIC).getBlock();
        }
        if (machine == IndustryMachine.COKE_OVEN) {
            return PiIndustry.COKE_OVEN.getBlock();
        }
        if (machine.atelier == IndustryMachine.Atelier.KIT) {
            return PiMachineKit.MACHINES.get(kit(machine)).get(CableTier.BASIC).getBlock();
        }
        return PiIndustry.controller(machine).getBlock();
    }

    static MachineKind kit(IndustryMachine machine) {
        return switch (machine) {
            case FURNACE -> MachineKind.FURNACE;
            case COMPRESSOR -> MachineKind.COMPRESSOR;
            default -> MachineKind.CRUSHER;
        };
    }
}
