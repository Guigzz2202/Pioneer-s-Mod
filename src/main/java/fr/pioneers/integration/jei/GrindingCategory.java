package fr.pioneers.integration.jei;

import fr.pioneers.api.machine.tier.GeneratedTierFamilies;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.machines.SteamMachine;
import fr.pioneers.tech.bronze.machines.SteamProcessTile;
import fr.pioneers.tech.bronze.machines.SteamRecipe;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.core.recipes.PiRecipes;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.3-beta (lot 3, regle 4 de Guillaume du 05/11/2026) : la page UNIQUE « Broyage ». Les trois broyeurs sont la meme famille (concasseur a
 * machoires de l'age mecanique, broyeur a vapeur, broyeur MKI a MK4 et son multibloc) : leurs recettes sont reunies (une entree, memes
 * sorties = une seule page) et chaque page dit, en pastilles, quelles machines la font ; catalyseurs : les trois familles (et les tiers).
 * Remplace les categories du concasseur ({@code pioneers:jaw_crushing}), du broyeur a vapeur ({@code pioneers:steam_macerator}) et du broyeur
 * ({@code pioneers:industry_crusher}) : leurs identifiants restent des alias ({@link PiJeiPlugin}) pour les fiches de l'API Machine.
 */
final class GrindingCategory extends PiCategory<GrindingCategory.Grind> {

    static final RecipeType<Grind> TYPE = JeiOrder.create("grinding", Grind.class);
    /** Identifiants des categories reunies ici (alias des fiches de l'API Machine). */
    /** 1.0.6 : famille de tiers des broyeurs (T:gc/api/tier_families.py). */
    static final String FAMILY = "broyage";
    static final List<String> MERGED = List.of("pioneers:jaw_crushing", "pioneers:steam_macerator", "pioneers:industry_crusher");

    /** Familles de broyeurs. */
    enum Source {
        JAW("jaw"), STEAM("steam"), KIT("kit"), MULTI("multi");

        final String key;

        Source(String key) {
            this.key = key;
        }
    }

    record Out(ItemStack stack, float chance) {
    }

    /** Une page : entree, sorties, et ce que chaque famille demande (tours d'arbre, duree a vapeur, bonus de minerai du multibloc). */
    static final class Grind {
        final List<ItemStack> inputs;
        final List<Out> outs;
        final EnumSet<Source> sources = EnumSet.noneOf(Source.class);
        float turns;
        int steamTicks;
        boolean oreBonus;
        /** 1.0.4-beta (lot 11) : recette recue d'une machine absorbee (broyage fin) : fluide d'entree (eau), tier, duree, recette (famille). */
        final List<net.minecraftforge.fluids.FluidStack> fluids = new ArrayList<>();
        int fluidAmount, tier, time;
        @org.jetbrains.annotations.Nullable
        IndustrialRecipe received;
        /** 1.0.5-beta (lot 38) : recette basse de la famille « broyage » (tier minimal global, identifiant, entree, produit) : pastille « Tier min. ». */
        int minTier = -1;
        @org.jetbrains.annotations.Nullable
        net.minecraft.resources.ResourceLocation lowId;
        List<BenchRecipe.Counted> lowIn = List.of();
        ItemStack lowMain = ItemStack.EMPTY;
        /** 1.0.6 : duree de base de la recette basse (ticks au tier minimal : TierRecipes) : energie et duree au broyeur MKI. */
        int lowTime;

        void low(int m, net.minecraft.resources.ResourceLocation id, List<BenchRecipe.Counted> in, ItemStack main, int time) {
            if (minTier < 0 || m < minTier) {
                minTier = m;
                lowId = id;
                lowIn = in;
                lowMain = main;
                lowTime = time;
            }
        }

        Grind(List<ItemStack> inputs, List<Out> outs) {
            this.inputs = inputs;
            this.outs = outs;
        }
    }

    private static final int IN_X = 1, ARROW_X = 24, OUT_X = 52, STEP = 19, COLS = 4, WIDTH = 176;

    GrindingCategory(IGuiHelper helper) {
        //1.0.6 (demande de Guillaume du 16/11/2026) : onglet a l'image du broyeur MKI (plus le pictogramme, ni le concasseur)
        super(helper, TYPE, "jei.pioneers.grinding", IndustryCategory.icon(IndustryMachine.CRUSHER), WIDTH, 18);
        family(JeiDraw.Family.MECHANICAL);
        footerLines(2);
    }

    // ------------------------------------------------------------------ donnees

    static List<Grind> recipes(Level level) {
        Map<String, Grind> by = new LinkedHashMap<>();
        for (MechanicalRecipe r : level.getRecipeManager().getAllRecipesFor(PiRecipes.MECHANICAL.get(MechanicalRecipe.Kind.JAW_CRUSHING).get())) {
            List<Out> outs = new ArrayList<>();
            r.results().forEach(res -> outs.add(new Out(res.stack(), res.chance())));
            Grind g = merge(by, r.input(), r.count(), outs);
            g.sources.add(Source.JAW);
            g.turns = r.turns();
            //1.0.5-beta (lot 38) : famille « broyage » : le broyeur a vapeur (T1, deux fois plus vite) et le broyeur MKI font aussi la recette
            g.sources.add(Source.STEAM);
            g.sources.add(Source.KIT);
            if (g.steamTicks <= 0) {
                g.steamTicks = Math.max(1, Math.round(r.turns() * 1200F / 30F) >> 1);
            }
            g.low(0, r.getId(), List.of(new BenchRecipe.Counted(r.input(), r.count())), r.results().isEmpty() ? ItemStack.EMPTY : r.results().get(0).stack(),
                  Math.max(1, Math.round(r.turns() * 1200F / GeneratedTierFamilies.NOMINAL_RPM.getOrDefault("jaw_crushing", 30))));
        }
        for (SteamRecipe r : SteamProcessTile.ownRecipes(level, SteamMachine.MACERATOR)) {
            if (r.inputs().size() != 1) {
                continue;
            }
            BenchRecipe.Counted in = r.inputs().get(0);
            List<Out> outs = new ArrayList<>(List.of(new Out(r.result(), 1F)));
            if (!r.byproduct().isEmpty() && r.chance() > 0) {
                outs.add(new Out(r.byproduct(), r.chance()));
            }
            Grind g = merge(by, in.ingredient(), in.count(), outs);
            g.sources.add(Source.STEAM);
            g.sources.add(Source.KIT);           // 1.0.5-beta (lot 38) : le broyeur MKI fait les recettes de sa famille
            g.steamTicks = r.time();
            g.low(r.minTier(), r.getId(), r.inputs(), r.result(), Math.max(1, r.time()));
        }
        //1.0.3-beta (lot 5) : recettes que le broyeur compact sait faire : celles du kit (pioneers:crushing, quel que soit leur dossier) et les
        //recettes industrielles a sa mesure (ex-dechiqueteuse : une entree, une ou deux sorties)
        java.util.Set<net.minecraft.resources.ResourceLocation> kit = new java.util.HashSet<>();
        level.getRecipeManager().getAllRecipesFor(PiRecipes.CRUSHING.get()).forEach(r -> kit.add(r.getId()));
        for (IndustrialRecipe r : IndustryRecipes.withoutLow(IndustryRecipes.of(level, IndustryMachine.CRUSHER))) {   // 1.0.5-beta (lot 38) : sans les converties
            if (r.items().isEmpty() || r.results().isEmpty()) {
                continue;
            }
            List<Out> outs = new ArrayList<>();
            r.results().forEach(res -> outs.add(new Out(res.stack(), res.chance())));
            //1.0.4-beta (lot 11, fusion des machines) : recette recue (ex-broyeur a boulets) : sa page propre (eau, tier, famille), jamais reunie
            Grind g = r.family() != null ? received(by, r, outs) : merge(by, r.items().get(0).ingredient(), r.items().get(0).count(), outs);
            //recettes du broyeur compact (et du multibloc) ; les autres sont celles du multibloc seulement ; lot 11 : le compact fait aussi les
            //recettes recues (voie B de KitProcess)
            boolean compact = kit.contains(r.getId()) || fr.pioneers.api.machine.unit.KitProcess.kitFits(r) || IndustryRecipes.isKitReceived(r);
            g.sources.add(compact ? Source.KIT : Source.MULTI);
            g.oreBonus |= r.ore();
        }
        return new ArrayList<>(by.values());
    }

    /** 1.0.4-beta (lot 11) : page d'une recette recue (cle = son identifiant : pas de fusion avec une autre page). */
    private static Grind received(Map<String, Grind> by, IndustrialRecipe r, List<Out> outs) {
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack st : r.items().get(0).ingredient().getItems()) {
            stacks.add(st.copyWithCount(r.items().get(0).count()));
        }
        Grind g = by.computeIfAbsent("recipe:" + r.getId(), k -> new Grind(stacks, outs));
        g.received = r;
        g.tier = r.tier();
        g.time = r.time();
        if (!r.fluids().isEmpty()) {
            g.fluids.addAll(r.fluids().get(0).displayed());
            g.fluidAmount = r.fluids().get(0).amount();
        }
        return g;
    }

    private static Grind merge(Map<String, Grind> by, Ingredient input, int count, List<Out> outs) {
        TreeSet<String> ids = new TreeSet<>();
        List<ItemStack> stacks = new ArrayList<>();
        for (ItemStack st : input.getItems()) {
            ids.add(BuiltInRegistries.ITEM.getKey(st.getItem()).toString());
            stacks.add(st.copyWithCount(count));
        }
        StringBuilder key = new StringBuilder(String.join(",", ids)).append('*').append(count).append("->");
        for (Out o : outs) {
            key.append(BuiltInRegistries.ITEM.getKey(o.stack().getItem())).append('*').append(o.stack().getCount())
                  .append('@').append(Math.round(o.chance() * 100)).append(';');
        }
        return by.computeIfAbsent(key.toString(), k -> new Grind(stacks, outs));
    }

    // ------------------------------------------------------------------ page

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Grind recipe, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.INPUT, IN_X, 1).setStandardSlotBackground().addItemStacks(recipe.inputs);
        int dx = shift(recipe);
        if (!recipe.fluids.isEmpty()) {
            //1.0.4-beta (lot 11) : eau du broyage fin (ex-broyeur a boulets), jauge entre l'entree et la fleche
            builder.addSlot(RecipeIngredientRole.INPUT, IN_X + STEP + 1, 2).setFluidRenderer(Math.max(1, recipe.fluidAmount), false, 14, 14)
                  .addIngredients(mezz.jei.api.forge.ForgeTypes.FLUID_STACK, recipe.fluids).setBackground(new Frame(16, 16), -1, -1);
        }
        for (int i = 0; i < recipe.outs.size() && i < COLS; i++) {
            Out o = recipe.outs.get(i);
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + dx + i * STEP, 1).setStandardSlotBackground().addItemStack(o.stack());
            if (o.chance() < 1F) {
                chance(slot, o.chance());
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.mechanical.chance",
                      String.format(Locale.ROOT, "%.0f", o.chance() * 100)).withStyle(ChatFormatting.GOLD)));
            }
            if (i == 0 && recipe.oreBonus) {
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.industry.ore_bonus")));
            }
        }
    }

    /** 1.0.4-beta (lot 11) : decalage de la fleche et des sorties quand la page a une jauge de fluide (broyage fin a l'eau). */
    private static int shift(Grind recipe) {
        return recipe.fluids.isEmpty() ? 0 : STEP;
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Grind recipe, @NotNull IFocusGroup focuses) {
        int ticks = recipe.received != null ? recipe.time : recipe.steamTicks > 0 ? recipe.steamTicks : 160;
        builder.addAnimatedRecipeArrow(Math.max(20, ticks)).setPosition(ARROW_X + shift(recipe), 1);
    }

    @Override
    protected JeiDraw.Badge badge(Grind recipe) {
        if (recipe.sources.contains(Source.JAW)) {
            return JeiDraw.Badge.of(JeiDraw.Family.MECHANICAL);
        }
        return recipe.sources.contains(Source.STEAM) ? JeiDraw.Badge.of(JeiDraw.Family.STEAM) : JeiDraw.Badge.tier(recipe.tier);
    }

    @Override
    protected List<JeiDraw.Chip> chips(Grind recipe) {
        List<JeiDraw.Chip> out = new ArrayList<>();
        //1.0.6 (demande de Guillaume du 16/11/2026) : plus de pastille de famille ni de tiers (le filigrane suffit) ; energie et duree au broyeur MKI
        if (recipe.lowId != null) {
            JeiDraw.Chip energy = TierChips.electric(FAMILY, recipe.minTier, recipe.lowTime);
            if (energy != null) {
                out.add(energy);
            }
            JeiDraw.Chip yield = TierChips.yieldOf(recipe.minTier, recipe.lowId, recipe.lowIn, recipe.lowMain);
            if (yield != null) {
                out.add(yield);
            }
        }
        for (Source s : recipe.sources) {
            Component tip = switch (s) {
                case JAW -> Component.translatable("jei.pioneers.grinding.jaw.tip", String.format(Locale.ROOT, "%.0f", recipe.turns));
                case STEAM -> Component.translatable("jei.pioneers.grinding.steam.tip", String.format(Locale.ROOT, "%.1f", recipe.steamTicks / 20.0));
                case KIT -> Component.translatable("jei.pioneers.grinding.kit.tip");
                case MULTI -> Component.translatable("jei.pioneers.grinding.multi.tip");
            };
            JeiDraw.Pic pic = switch (s) {
                case JAW -> JeiDraw.Pic.TURNS;
                case STEAM -> JeiDraw.Pic.STEAM;
                default -> JeiDraw.Pic.ENERGY;
            };
            out.add(JeiDraw.Chip.of(pic, Component.translatable("jei.pioneers.grinding." + s.key), s == Source.MULTI ? WARN : TEXT, tip));
        }
        return out;
    }
}
