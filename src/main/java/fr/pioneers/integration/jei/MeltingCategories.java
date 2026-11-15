package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.autofoundry.FoundryUnit;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.foundry.CrucibleFurnaceTile;
import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.forge.ForgeHeat;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.MoltenLoad;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.industry.recipe.OreYields;
import fr.pioneers.tech.machine.MachineKind;
import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiForge;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.core.registries.PiMachineKit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * JEI de la FONDERIE.
 * <ul>
 *   <li>« Rendements du minerai » (1.0.3 lot 3) : le tableau des voies (four, creuset, broyeur, lixiviation) x tiers, calcule sur les vraies
 *       recettes ({@link OreYields}) ;</li>
 *   <li>1.0.3-beta lot 6 (Guillaume : « unifier les systemes de forge de JEI car ils suivent tous la meme regle ; le but est d'eviter les 100
 *       pages JEI pour rien ») : UNE categorie « Fusion des metaux » ({@link #MELTING}) a la place des pages par poste (creuset en argile
 *       {@code pioneers:forge_crucible}, fourneau a creuset {@code pioneers:melt_crucible_furnace}, fonderie automatique
 *       {@code pioneers:melt_auto_foundry}) et des fontes du creuset electrique (recettes {@code melt_*} / {@code recycle_*} : une entree, un
 *       metal fondu, retirees de sa page industrielle) : une page par metal, par groupe (minerai, metal, recyclage) et par profil de rendement
 *       (memes machines, memes multiplicateurs), 16 objets au plus ; petit tableau « machine -> rendement » (postes du feu x1 avec leur
 *       chaleur, creuset electrique MKI a MKIV lu sur ses recettes) ; toutes les machines de fusion en catalyseurs ;</li>
 *   <li>1.0.3-beta lot 6 (Guillaume : « indiquer clairement comment faire du bronze a partir de minerai ; valable pour tous les alliages ») :
 *       « Alliages » : une page par alliage fondu ou allie en machine (bronze, laiton, soudure par {@link Metals#RECIPES} ; ceux des recettes
 *       industrielles a lingots de plusieurs metaux : babbitt, duralumin, electrum, zircaloy, invar, constantan, nichrome, alliages du four
 *       sous vide...) : lingots ET minerais qui les donnent (U sur le minerai), proportions et fourchettes, metal fondu et lingots en sortie (R
 *       sur le metal fondu), les machines qui font le melange (cases de catalyseur, infobulle = comment faire).</li>
 * </ul>
 */
final class MeltingCategories {

    static final RecipeType<Yield> YIELDS = JeiOrder.create("ore_yields", Yield.class);
    static final RecipeType<Melt> MELTING = JeiOrder.create("metal_melting", Melt.class);
    static final RecipeType<Alloy> ALLOYS = JeiOrder.create("alloys", Alloy.class);
    /** Identifiants des categories reunies dans « Fusion des metaux » (lot 6 de la 1.0.3). */
    static final List<String> MERGED = List.of("pioneers:forge_crucible", "pioneers:melt_crucible_furnace", "pioneers:melt_auto_foundry");

    /** Postes de fusion de l'age du feu et du bronze (x1, table GeneratedMaterials.MELTS). */
    enum Station {
        CLAY("clay"), FURNACE("furnace"), FOUNDRY("foundry");

        final String key;

        Station(String key) {
            this.key = key;
        }

        ItemLike block() {
            return switch (this) {
                case CLAY -> PiForge.CLAY_CRUCIBLE.getBlock();
                case FURNACE -> PiBronze.CRUCIBLE_FURNACE.getBlock();
                case FOUNDRY -> PiBronze.AUTO_FOUNDRY.getBlock();
            };
        }
    }

    /** Groupes d'objets d'une page : minerai, metal (lingot, pepite, poudre, bloc), recyclage (pieces : 50 %). */
    enum Group {
        ORE, METAL, RECYCLE;

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    /**
     * Une page « Fusion des metaux » : le metal, le groupe, les objets et leur valeur de base (mB a x1) ; par objet : les postes du feu le
     * fondent-ils, rendement du creuset electrique par tier (null : il ne le fond pas) ; duree de la recette du creuset electrique.
     */
    record Melt(String metal, Group group, List<ItemStack> items, List<Integer> mb, List<Boolean> fire, List<double[]> electric, int electricTime,
                List<Integer> slag) {

        boolean anyFire() {
            return fire.contains(Boolean.TRUE);
        }

        boolean anyElectric() {
            return electric.stream().anyMatch(java.util.Objects::nonNull);
        }
    }

    record Yield(String metal, double[][] table) {
    }

    record Part(String metal, int ingots, int min, int max) {
    }

    /** Une facon de faire l'alliage : la machine (son bloc), et la cle de langue qui dit comment (avec ses arguments). */
    record Route(ItemStack machine, Component how) {
    }

    record Alloy(String alloy, List<Part> parts, int ingots, List<Route> routes, boolean gas) {
    }

    private MeltingCategories() {
    }

    // ------------------------------------------------------------------ enregistrement (appele par PiJeiPlugin)

    static void categories(IRecipeCategoryRegistration registration, IGuiHelper helper) {
        registration.addRecipeCategories(new YieldCategory(helper), new MeltCategory(helper), new AlloyCategory(helper));
    }

    static void recipes(IRecipeRegistration registration, Level level) {
        List<Yield> yields = new ArrayList<>();
        for (String m : OreYields.METALS) {
            yields.add(new Yield(m, OreYields.table(level, m)));
        }
        registration.addRecipes(YIELDS, yields);
        registration.addRecipes(MELTING, melts(level));
        registration.addRecipes(ALLOYS, alloys(level));
    }

    static void catalysts(IRecipeCatalystRegistration registration) {
        for (MachineKind kind : List.of(MachineKind.FURNACE, MachineKind.CRUSHER)) {
            PiMachineKit.MACHINES.get(kind).values().forEach(b -> registration.addRecipeCatalyst(new ItemStack(b.getBlock()), YIELDS));
        }
        //1.0.4-beta (lot 13) : la lixiviation est a l'extracteur (cuve de lixiviation fondue)
        for (IndustryMachine m : List.of(IndustryMachine.ELECTRIC_CRUCIBLE, IndustryMachine.EXTRACTOR, IndustryMachine.ELECTROLYZER)) {
            var tiers = PiIndustry.ATELIERS.get(m);
            if (tiers != null) {
                tiers.values().forEach(b -> registration.addRecipeCatalyst(new ItemStack(b.getBlock()), YIELDS));
            }
        }
        //Fusion des metaux : TOUTES les machines de fusion (postes du feu et du bronze, creuset electrique compact et multibloc)
        registration.addRecipeCatalysts(MELTING, PiForge.CLAY_CRUCIBLE.getBlock(), Blocks.CAMPFIRE, PiForge.HAND_BELLOWS.getBlock(),
              PiBronze.CRUCIBLE_FURNACE.getBlock(), PiBronze.AUTO_FOUNDRY.getBlock());
        for (ItemStack s : machines(IndustryMachine.ELECTRIC_CRUCIBLE)) {
            registration.addRecipeCatalyst(s, MELTING);
        }
        registration.addRecipeCatalysts(ALLOYS, PiBronze.CRUCIBLE_FURNACE.getBlock(), PiBronze.AUTO_FOUNDRY.getBlock(), PiForge.CLAY_CRUCIBLE.getBlock(),
              PiBronze.STEAM_MACHINES.get(fr.pioneers.tech.bronze.machines.SteamMachine.INDUCTION_FURNACE).getBlock());
        //1.0.4-beta (lot 11) : plus de fondeur d'alliages a vapeur (fourneau a creuset) ; four sous vide fondu dans le Creuset industriel ;
        //1.0.5-beta (lot 39) : le four a induction redevient une machine (alliages directs, vapeur T1 puis MKI a MKIV)
        for (IndustryMachine m : ALLOY_MACHINES) {
            for (ItemStack s : machines(m)) {
                registration.addRecipeCatalyst(s, ALLOYS);
            }
        }
    }

    /** Machines industrielles qui allient des lingots (recettes a plusieurs metaux : {@link #alloyOf}). */
    private static final List<IndustryMachine> ALLOY_MACHINES = List.of(IndustryMachine.ELECTRIC_CRUCIBLE, IndustryMachine.INDUCTION_FURNACE);

    /** Blocs d'une machine industrielle : compacts MKI a MKIV puis controleur du multibloc. */
    private static List<ItemStack> machines(IndustryMachine m) {
        List<ItemStack> out = new ArrayList<>();
        var tiers = PiIndustry.ATELIERS.get(m);
        if (tiers != null) {
            tiers.values().forEach(b -> out.add(new ItemStack(b.getBlock())));
        }
        var c = PiIndustry.controller(m);
        if (c != null) {
            out.add(new ItemStack(c.getBlock()));
        }
        return out;
    }

    // ------------------------------------------------------------------ Fusion des metaux : donnees

    private static final int PER_PAGE = 16;

    /**
     * Fonte du creuset electrique reunie ici : une entree d'objets, aucun fluide ni gaz en entree, un seul produit = un metal fondu
     * ({@code pioneers:molten_<metal>}), aucun objet en sortie. Retiree de la page industrielle du creuset electrique (PiJeiPlugin).
     */
    static boolean isMelt(IndustrialRecipe r) {
        return r.machine() == IndustryMachine.ELECTRIC_CRUCIBLE && r.items().size() == 1 && r.fluids().isEmpty() && r.gases().isEmpty()
               && byproductsOnly(r) && r.gasResults().isEmpty() && r.fluidResults().size() == 1 && moltenMetal(r.fluidResults().get(0)) != null;
    }

    /** 1.0.5-beta (lot 39) : la fonte au creuset peut sortir un sous-produit tire au sort (laitier) ; jamais un objet sur. */
    private static boolean byproductsOnly(IndustrialRecipe r) {
        for (IndustrialRecipe.Result res : r.results()) {
            if (res.chance() >= 1F) {
                return false;
            }
        }
        return true;
    }

    /** Laitier (ou autre sous-produit) de la fonte au creuset electrique : « 1 fois sur n » du plus probable ; 0 sans sous-produit. */
    static int byproductOneIn(IndustrialRecipe r) {
        float best = 0;
        for (IndustrialRecipe.Result res : r.results()) {
            best = Math.max(best, res.chance());
        }
        return best <= 0 ? 0 : Math.round(1F / best);
    }

    @Nullable
    private static String moltenMetal(FluidStack fluid) {
        ResourceLocation id = BuiltInRegistries.FLUID.getKey(fluid.getFluid());
        return id.getNamespace().equals(Pioneers.MODID) && id.getPath().startsWith("molten_") ? id.getPath().substring(7) : null;
    }

    static Fluid molten(String metal) {
        Fluid f = PiIndustry.fluid("molten_" + metal);
        return f == null ? Fluids.EMPTY : f;
    }

    private static final class Entry {
        final ItemStack stack;
        final String metal;
        int base;
        boolean fire;
        double[] electric;
        int time;
        int slag;

        Entry(ItemStack stack, String metal, int base) {
            this.stack = stack;
            this.metal = metal;
            this.base = base;
        }

        String page(Group g) {
            return metal + "|" + g.name();
        }
    }

    static List<Melt> melts(Level level) {
        Map<Item, Entry> by = new LinkedHashMap<>();
        for (GeneratedMaterials.Melt melt : GeneratedMaterials.MELTS) {
            if (!ForgeMetals.crucible(melt.metal())) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(melt.item()));
            if (item == Items.AIR) {
                continue;
            }
            Entry e = by.computeIfAbsent(item, i -> new Entry(new ItemStack(i), melt.metal(), melt.mb()));
            e.fire = true;
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.ELECTRIC_CRUCIBLE)) {
            if (!isMelt(r)) {
                continue;
            }
            String metal = moltenMetal(r.fluidResults().get(0));
            int amount = r.fluidResults().get(0).getAmount();
            BenchRecipe.Counted in = r.items().get(0);
            for (ItemStack st : in.ingredient().getItems()) {
                Entry e = by.computeIfAbsent(st.getItem(), i -> new Entry(new ItemStack(i, in.count()), metal, amount / Math.max(1, in.count())));
                if (!e.metal.equals(metal)) {
                    continue;
                }
                double[] mult = new double[4];
                for (int t = 0; t < mult.length; t++) {
                    mult[t] = Math.round(r.yieldedFluid(t) * 100.0 / Math.max(1, e.base * in.count())) / 100.0;
                }
                e.electric = mult;
                e.time = r.time();
                e.slag = byproductOneIn(r);
            }
        }
        //pages : groupe, puis metal (ordre de la table) ; 16 objets au plus (le detail par objet est dans son infobulle)
        Map<String, List<Entry>> pages = new LinkedHashMap<>();
        for (Group g : Group.values()) {
            for (Entry e : by.values()) {
                if (group(e.stack) == g) {
                    pages.computeIfAbsent(e.page(g), k -> new ArrayList<>()).add(e);
                }
            }
        }
        List<Melt> out = new ArrayList<>();
        for (Map.Entry<String, List<Entry>> p : pages.entrySet()) {
            List<Entry> rows = p.getValue();
            Group g = Group.valueOf(p.getKey().split("\\|")[1]);
            for (int from = 0; from < rows.size(); from += PER_PAGE) {
                List<ItemStack> items = new ArrayList<>();
                List<Integer> mb = new ArrayList<>();
                List<Boolean> fire = new ArrayList<>();
                List<double[]> electric = new ArrayList<>();
                List<Integer> slag = new ArrayList<>();
                int time = 0;
                for (Entry e : rows.subList(from, Math.min(rows.size(), from + PER_PAGE))) {
                    items.add(e.stack);
                    mb.add(e.base * e.stack.getCount());
                    fire.add(e.fire);
                    electric.add(e.electric);
                    slag.add(e.slag);
                    time = Math.max(time, e.time);
                }
                out.add(new Melt(rows.get(0).metal, g, items, mb, fire, electric, time, slag));
            }
        }
        return out;
    }

    static Group group(ItemStack stack) {
        String path = BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath();
        if (stack.is(tag("raw_materials")) || stack.is(tag("crushed_ores")) || path.endsWith("_fragment") || path.equals("gold_flake")
            || path.startsWith("raw_") && path.endsWith("_block")) {
            return Group.ORE;
        }
        if (stack.is(tag("ingots")) || stack.is(tag("nuggets")) || stack.is(tag("dusts")) || stack.is(tag("storage_blocks"))) {
            return Group.METAL;
        }
        return Group.RECYCLE;
    }

    private static net.minecraft.tags.TagKey<Item> tag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", path));
    }

    /** Un nombre lisible : entier tel quel, sinon une ou deux decimales (virgule en francais). */
    static String number(double v) {
        if (Double.isNaN(v)) {
            return "—";
        }
        String s = Math.abs(v - Math.rint(v)) < 1e-6 ? String.valueOf((long) Math.rint(v))
                   : Math.abs(v * 10 - Math.rint(v * 10)) < 1e-6 ? String.format(Locale.ROOT, "%.1f", v) : String.format(Locale.ROOT, "%.2f", v);
        String lang = Minecraft.getInstance().options.languageCode;
        return lang != null && lang.startsWith("fr") ? s.replace('.', ',') : s;
    }

    /** Chaleur d'un poste du feu pour ce metal : texte court (feu de camp, soufflet, air demande) et sa couleur. */
    private static Component heat(Station station, int melt) {
        if (station == Station.CLAY) {
            boolean air = melt > ForgeHeat.CAMPFIRE_C + ForgeHeat.CHARCOAL_BONUS_C;
            return Component.translatable(air ? "jei.pioneers.chip.bellows" : "jei.pioneers.chip.campfire");
        }
        int need = airNeeded(station, melt);
        return need <= 0 ? Component.translatable("jei.pioneers.chip.no_air") : Component.translatable("jei.pioneers.chip.air", Math.min(100, need) + " %");
    }

    private static int airNeeded(Station station, int melt) {
        int base = station == Station.FURNACE ? CrucibleFurnaceTile.BASE_C : FoundryUnit.BASE_C;
        int perAir = station == Station.FURNACE ? CrucibleFurnaceTile.AIR_C : FoundryUnit.AIR_C;
        int charcoal = 30;
        return (int) Math.ceil(Math.max(0, (melt - base - charcoal) * 100.0 / perAir));
    }

    private static boolean heatWarn(Station station, int melt) {
        return station == Station.CLAY ? melt > ForgeHeat.CAMPFIRE_C + ForgeHeat.CHARCOAL_BONUS_C : airNeeded(station, melt) > 0;
    }

    // ------------------------------------------------------------------ « Rendements du minerai »

    static final class YieldCategory extends PiCategory<Yield> {

        private static final int TOP = 22, ROW = 10, LABEL_W = 100, COL0 = 104, COL = 23;
        private static final String[] ROW_KEYS = {"jei.pioneers.ore_yields.row0", "jei.pioneers.ore_yields.row1", "jei.pioneers.ore_yields.row2",
              "jei.pioneers.ore_yields.row3", "jei.pioneers.ore_yields.row4"};

        YieldCategory(IGuiHelper helper) {
            super(helper, YIELDS, "jei.pioneers.ore_yields", PiForge.CLAY_CRUCIBLE.getBlock(), COL0 + 4 * COL, TOP + ROW * (OreYields.ROWS + 1));
            family(JeiDraw.Family.FORGE);
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Yield recipe, @NotNull IFocusGroup focuses) {
            ItemStack raw = OreYields.stack("raw_" + recipe.metal());
            ItemStack crushed = OreYields.stack("crushed_" + recipe.metal());
            if (!raw.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 1, 1).setStandardSlotBackground().addItemStack(raw);
            }
            if (!crushed.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, 20, 1).setStandardSlotBackground().addItemStack(crushed);
            }
            ItemStack ingot = ForgeMetals.ingot(recipe.metal(), 1);
            if (!ingot.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, 63, 1).setStandardSlotBackground().addItemStack(ingot);
            }
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Yield recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(80).setPosition(38, 1);
        }

        @Override
        protected void drawContent(@NotNull Yield recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            fitted(graphics, Metals.name(recipe.metal()), 84, 6, SMALL, TEXT, cw() - 84);
            text(graphics, Component.translatable("jei.pioneers.ore_yields.per_ore"), 0, TOP, SMALL, TEXT_LIGHT);
            for (int t = 0; t < OreYields.TIERS; t++) {
                centered(graphics, Component.translatable("industry.pioneers.tier." + Tiers.tier(t).id()), COL0 + t * COL + COL / 2F, TOP, SMALL,
                      Tiers.tier(t).color | 0xFF000000);
            }
            for (int r = 0; r < OreYields.ROWS; r++) {
                float y = TOP + ROW * (r + 1);
                fitted(graphics, Component.translatable(ROW_KEYS[r]), 0, y, SMALL, TEXT, LABEL_W);
                for (int t = 0; t < OreYields.TIERS; t++) {
                    double v = recipe.table()[r][t];
                    centered(graphics, Component.literal(number(v)), COL0 + t * COL + COL / 2F, y, SMALL, Double.isNaN(v) ? TEXT_LIGHT : r == 4 ? GOOD : TEXT);
                }
            }
        }

        @Override
        protected List<JeiDraw.Chip> chips(Yield recipe) {
            return List.of(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.ore_yields.chip"), TEXT,
                        Component.translatable("jei.pioneers.ore_yields.tip1"), Component.translatable("jei.pioneers.ore_yields.tip2"),
                        Component.translatable("jei.pioneers.ore_yields.tip3")),
                  JeiDraw.Chip.of(JeiDraw.Pic.FLUID, Component.translatable("jei.pioneers.chip.ingot_mb"), JeiDraw.COLD,
                        Component.translatable("jei.pioneers.ore_yields.tip_mb")));
        }
    }

    // ------------------------------------------------------------------ « Fusion des metaux »

    static final class MeltCategory extends PiCategory<Melt> {

        private static final String[] ROMAN = {"I", "II", "III", "IV"};
        private static final int COLS = 8, STEP = 19, ROW_H = 26, ARROW_X = COLS * STEP + 2, OUT_X = ARROW_X + 26, WIDTH = OUT_X + 18;
        /** Petit tableau sous les objets : une ligne par machine (postes du feu, creuset electrique), colonnes MKI a MKIV. */
        private static final int TABLE_Y = 2 * ROW_H + 2, LINE_H = 9, NAME_W = 64, COL0 = NAME_W + 4, COL = (WIDTH - NAME_W - 4) / 4;

        MeltCategory(IGuiHelper helper) {
            super(helper, MELTING, "jei.pioneers.metal_melting", PiBronze.CRUCIBLE_FURNACE.getBlock(), WIDTH, TABLE_Y + 4 * LINE_H + 2);
            family(JeiDraw.Family.FORGE);
            footerLines(2);
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Melt recipe, @NotNull IFocusGroup focuses) {
            for (int i = 0; i < recipe.items().size(); i++) {
                int k = i;
                builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % COLS) * STEP, 1 + (i / COLS) * ROW_H).setStandardSlotBackground()
                      .addItemStack(recipe.items().get(i))
                      .addRichTooltipCallback((view, tooltip) -> itemLines(recipe, k).forEach(tooltip::add));
            }
            Fluid fluid = molten(recipe.metal());
            if (fluid != Fluids.EMPTY) {
                fluid(builder, RecipeIngredientRole.OUTPUT, OUT_X - 1, 0, 18, 18, fluid, Metals.INGOT, Metals.INGOT);
            }
            ItemStack ingot = ForgeMetals.ingot(recipe.metal(), 1);
            if (!ingot.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X, 1 + ROW_H).setStandardSlotBackground().addItemStack(ingot);
            }
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Melt recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(!recipe.anyFire() ? Math.max(20, recipe.electricTime()) : 60).setPosition(ARROW_X, 1);
        }

        @Override
        protected void drawContent(@NotNull Melt recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            for (int i = 0; i < recipe.mb().size(); i++) {
                centered(graphics, Component.literal(recipe.mb().get(i) + ""), 1 + (i % COLS) * STEP + 9, 1 + (i / COLS) * ROW_H + 19, 0.5F,
                      recipe.group() == Group.RECYCLE ? WARN : TEXT);
            }
            centered(graphics, Component.literal("mB ×1"), ARROW_X + 11, 21, 0.5F, TEXT_LIGHT);
            fitted(graphics, Metals.name(recipe.metal()), ARROW_X, 31, 0.5F, TEXT, OUT_X - ARROW_X - 1);
            fitted(graphics, Component.translatable("jei.pioneers.melt.group." + recipe.group().key()), ARROW_X, 39, 0.5F, TEXT_LIGHT, OUT_X - ARROW_X - 1);
            //tableau : machine -> rendement (x par rapport a la valeur de base de chaque objet)
            graphics.fill(0, TABLE_Y - 1, cw(), TABLE_Y, 0x40FFFFFF);
            int melt = ForgeMetals.meltingC(recipe.metal());
            int y = TABLE_Y + 1;
            long fires = recipe.fire().stream().filter(b -> b).count();
            for (Station s : Station.values()) {
                fitted(graphics, Component.translatable("jei.pioneers.metal_melting." + s.key), 0, y, 0.5F, TEXT, NAME_W);
                if (fires > 0) {
                    String share = fires == recipe.items().size() ? "×1" : "×1 (" + fires + "/" + recipe.items().size() + ")";
                    text(graphics, Component.literal(share), COL0, y, 0.5F, GOOD);
                    int w = font().width(share) / 2 + 4;
                    fitted(graphics, heat(s, melt), COL0 + w, y, 0.5F, heatWarn(s, melt) ? WARN : TEXT_LIGHT, cw() - COL0 - w);
                } else {
                    fitted(graphics, Component.translatable("jei.pioneers.metal_melting.none"), COL0, y, 0.5F, TEXT_LIGHT, cw() - COL0);
                }
                y += LINE_H;
            }
            fitted(graphics, Component.translatable("jei.pioneers.metal_melting.electric"), 0, y, 0.5F, TEXT, NAME_W);
            for (int t = 0; t < 4; t++) {
                double lo = Double.MAX_VALUE, hi = 0;
                for (double[] e : recipe.electric()) {
                    if (e != null) {
                        lo = Math.min(lo, e[t]);
                        hi = Math.max(hi, e[t]);
                    }
                }
                //colonnes MKI a MKIV : chiffre romain a la couleur du tier (place comptee : la cellule fait 33 px)
                Component cell = hi == 0 ? Component.literal("—") : Component.literal(ROMAN[t] + " ×" + number(lo) + (hi > lo + 1e-6 ? "–" + number(hi) : ""));
                fitted(graphics, cell, COL0 + t * COL, y, 0.5F, hi == 0 ? TEXT_LIGHT : Tiers.tier(t).color | 0xFF000000, COL - 1);
            }
        }

        /** Infobulle d'un objet : ou il fond et ce qu'il rend (mB) a chaque machine et chaque tier. */
        private static List<Component> itemLines(Melt recipe, int i) {
            List<Component> out = new ArrayList<>();
            int mb = recipe.mb().get(i);
            out.add(Component.translatable(recipe.fire().get(i) ? "jei.pioneers.metal_melting.item_fire" : "jei.pioneers.metal_melting.item_no_fire", mb)
                  .withStyle(net.minecraft.ChatFormatting.GRAY));
            double[] e = recipe.electric().get(i);
            if (e != null) {
                net.minecraft.network.chat.MutableComponent c = Component.translatable("jei.pioneers.metal_melting.item_electric").copy();
                for (int t = 0; t < 4; t++) {
                    c.append(" ").append(Component.translatable("industry.pioneers.tier." + Tiers.tier(t).id())).append(" " + Math.round(mb * e[t]) + " mB");
                }
                out.add(c.withStyle(net.minecraft.ChatFormatting.GRAY));
            } else {
                out.add(Component.translatable("jei.pioneers.metal_melting.item_no_electric").withStyle(net.minecraft.ChatFormatting.DARK_GRAY));
            }
            if (recipe.slag().get(i) > 0) {
                out.add(Component.translatable("jei.pioneers.metal_melting.item_slag", recipe.slag().get(i)).withStyle(net.minecraft.ChatFormatting.GRAY));
            }
            return out;
        }

        @Override
        protected List<JeiDraw.Chip> chips(Melt recipe) {
            List<JeiDraw.Chip> out = new ArrayList<>();
            int melt = ForgeMetals.meltingC(recipe.metal());
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp", melt), TEXT,
                  Component.translatable("jei.pioneers.forge.melts_at", melt)));
            Component groupTip = Component.translatable("jei.pioneers.melt.group." + recipe.group().key() + ".tip");
            out.add(JeiDraw.Chip.of(recipe.group() == Group.RECYCLE ? JeiDraw.Pic.CHANCE : JeiDraw.Pic.INFO,
                  Component.translatable("jei.pioneers.melt.chip." + recipe.group().key()), recipe.group() == Group.RECYCLE ? WARN : TEXT, groupTip));
            if (recipe.anyFire()) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.HEAT, Component.translatable("jei.pioneers.metal_melting.fire_chip"), TEXT,
                      Component.translatable("jei.pioneers.melt.air_tip", CrucibleFurnaceTile.BASE_C, CrucibleFurnaceTile.AIR_C),
                      Component.translatable("jei.pioneers.metal_melting.clay_tip", MoltenLoad.CLAY_CRUCIBLE_MB)));
            }
            if (recipe.anyElectric()) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.TIME, Component.translatable("jei.pioneers.metal_melting.electric_chip", seconds(recipe.electricTime())),
                      TEXT, Component.translatable("jei.pioneers.metal_melting.electric_tip")));
            }
            //1.0.5-beta (lot 39) : la fonte au creuset electrique rend un peu de laitier (minerai 10 %, recyclage 5 %)
            int slag = recipe.slag().stream().mapToInt(Integer::intValue).filter(n -> n > 0).min().orElse(0);
            if (slag > 0) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.CHANCE, Component.translatable("jei.pioneers.metal_melting.slag_chip", slag), TEXT_LIGHT,
                      Component.translatable("jei.pioneers.metal_melting.slag_tip")));
            }
            return out;
        }
    }

    // ------------------------------------------------------------------ alliages : donnees

    /**
     * Recette industrielle qui ALLIE des lingots (au moins deux metaux differents, que des lingots, un seul produit : le metal fondu ou le
     * lingot de l'alliage, qui n'est pas en entree) : la cle de l'alliage, sinon null.
     */
    @Nullable
    static String alloyOf(IndustrialRecipe r) {
        if (r.items().size() < 2 || !r.fluids().isEmpty() || r.results().size() + r.fluidResults().size() != 1) {
            return null;
        }
        java.util.Set<String> metals = new java.util.HashSet<>();
        for (BenchRecipe.Counted c : r.items()) {
            ItemStack[] st = c.ingredient().getItems();
            if (st.length == 0 || !ForgeMetals.isIngot(st[0])) {
                return null;
            }
            metals.add(Metals.melt(st[0]).metal());
        }
        String alloy;
        if (!r.fluidResults().isEmpty()) {
            alloy = moltenMetal(r.fluidResults().get(0));
        } else {
            String path = BuiltInRegistries.ITEM.getKey(r.results().get(0).stack().getItem()).getPath();
            alloy = path.endsWith("_ingot") ? path.substring(0, path.length() - 6) : null;
        }
        return alloy == null || metals.size() < 2 || metals.contains(alloy) ? null : alloy;
    }

    static List<Alloy> alloys(Level level) {
        Map<String, Alloy> out = new LinkedHashMap<>();
        //1. alliages du bain (Metals.RECIPES) : postes du feu (1.0.4-beta lot 11 : plus de fondeur a vapeur)
        for (String alloy : List.of("bronze", "brass", "solder")) {
            Map<String, Integer> recipe = Metals.RECIPES.get(alloy);
            if (recipe == null) {
                continue;
            }
            GeneratedMaterials.Metal metal = Metals.get(alloy);
            List<Part> parts = new ArrayList<>();
            int total = 0;
            for (Map.Entry<String, Integer> e : recipe.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue()).toList()) {
                int min = 0, max = 100;
                if (metal != null) {
                    for (GeneratedMaterials.Share share : metal.composition()) {
                        if (share.metal().equals(e.getKey())) {
                            min = (int) Math.round(share.min());
                            max = (int) Math.round(share.max());
                        }
                    }
                }
                parts.add(new Part(e.getKey(), e.getValue(), min, max));
                total += e.getValue();
            }
            List<Route> routes = new ArrayList<>();
            routes.add(new Route(new ItemStack(PiBronze.CRUCIBLE_FURNACE.getBlock()), Component.translatable("jei.pioneers.alloys.how.furnace",
                  Metals.name(alloy), ratio(parts))));
            routes.add(new Route(new ItemStack(PiBronze.AUTO_FOUNDRY.getBlock()), Component.translatable("jei.pioneers.alloys.how.foundry",
                  Metals.name(alloy), ratio(parts))));
            routes.add(new Route(new ItemStack(PiForge.CLAY_CRUCIBLE.getBlock()), Component.translatable("jei.pioneers.alloys.how.clay",
                  MoltenLoad.CLAY_CRUCIBLE_MB, clayDose(parts))));
            //1.0.5-beta (lot 39, retour 51) : alliage direct au four a induction a vapeur (T1 ; MKI a MKIV par la famille)
            routes.add(new Route(new ItemStack(PiBronze.STEAM_MACHINES.get(fr.pioneers.tech.bronze.machines.SteamMachine.INDUCTION_FURNACE).getBlock()),
                  Component.translatable("jei.pioneers.alloys.how.induction", ratio(parts))));
            out.put(alloy, new Alloy(alloy, parts, total, routes, false));
        }
        //2. alliages des machines industrielles (lingots de plusieurs metaux -> metal fondu ou lingot)
        for (IndustryMachine m : ALLOY_MACHINES) {
            for (IndustrialRecipe r : IndustryRecipes.withoutLow(IndustryRecipes.of(level, m))) {   // 1.0.5-beta (lot 38) : sans les converties
                String alloy = alloyOf(r);
                if (alloy == null) {
                    continue;
                }
                List<ItemStack> blocks = machines(m);
                ItemStack machine = blocks.isEmpty() ? ItemStack.EMPTY : blocks.get(0);
                Alloy known = out.get(alloy);
                List<Part> parts = new ArrayList<>();
                int total = 0;
                if (known == null) {
                    int sum = r.items().stream().mapToInt(BenchRecipe.Counted::count).sum();
                    for (BenchRecipe.Counted c : r.items()) {
                        int pct = (int) Math.round(c.count() * 100.0 / sum);
                        parts.add(new Part(Metals.melt(c.ingredient().getItems()[0]).metal(), c.count(), pct, pct));
                        total += c.count();
                    }
                } else {
                    parts = known.parts();
                    total = known.ingots();
                }
                Component how = Component.translatable(r.gases().isEmpty() ? "jei.pioneers.alloys.how.machine" : "jei.pioneers.alloys.how.machine_gas",
                      ratio(parts), PiCategory.seconds(r.time()));
                List<Route> routes = new ArrayList<>(known == null ? List.of() : known.routes());
                if (!machine.isEmpty()) {
                    routes.add(new Route(machine, how));
                }
                out.put(alloy, new Alloy(alloy, parts, total, routes, known != null && known.gas() || !r.gases().isEmpty()));
            }
        }
        return new ArrayList<>(out.values());
    }

    /** « 3 cuivre + 1 etain » (texte), pour les infobulles des machines. */
    private static Component ratio(List<Part> parts) {
        net.minecraft.network.chat.MutableComponent c = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                c.append(" + ");
            }
            c.append(parts.get(i).ingots() + " ").append(Metals.name(parts.get(i).metal()));
        }
        return c;
    }

    /**
     * Creuset en argile ({@link MoltenLoad#CLAY_CRUCIBLE_MB} mB) : la plus grosse dose qui tient, en fragments (36 mB) si chaque metal en a
     * un, sinon en pepites (16 mB) : « 6 fragment de malachite + 2 fragment de cassiterite ».
     */
    private static Component clayDose(List<Part> parts) {
        int sum = parts.stream().mapToInt(Part::ingots).sum();
        List<ItemStack> fragments = new ArrayList<>();
        for (Part p : parts) {
            ItemStack f = fragmentOf(p.metal());
            if (f.isEmpty()) {
                fragments = null;
                break;
            }
            fragments.add(f);
        }
        int unit = fragments != null ? 36 : 16;
        int k = Math.max(1, MoltenLoad.CLAY_CRUCIBLE_MB / unit / sum);
        net.minecraft.network.chat.MutableComponent c = Component.empty();
        for (int i = 0; i < parts.size(); i++) {
            if (i > 0) {
                c.append(" + ");
            }
            ItemStack shown = fragments != null ? fragments.get(i) : ForgeMetals.nugget(parts.get(i).metal(), 1);
            c.append(parts.get(i).ingots() * k + " ").append(shown.isEmpty() ? Metals.name(parts.get(i).metal()) : shown.getHoverName());
        }
        return c;
    }

    private static ItemStack fragmentOf(String metal) {
        for (GeneratedMaterials.Melt melt : GeneratedMaterials.MELTS) {
            if (melt.metal().equals(metal) && melt.item().endsWith("_fragment") && melt.mb() == 36) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(melt.item()));
                if (item != Items.AIR) {
                    return new ItemStack(item);
                }
            }
        }
        return ItemStack.EMPTY;
    }

    /** Minerais (et poudres) qui valent un lingot de ce metal aux postes du feu (144 mB a x1) : minerai brut, concasse, poudre de minerai. */
    static List<ItemStack> oresOf(String metal, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (GeneratedMaterials.Melt melt : GeneratedMaterials.MELTS) {
            if (!melt.metal().equals(metal) || melt.mb() != Metals.INGOT) {
                continue;
            }
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(melt.item()));
            if (item != Items.AIR && group(new ItemStack(item)) == Group.ORE) {
                out.add(new ItemStack(item, count));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ « Alliages »

    static final class AlloyCategory extends PiCategory<Alloy> {

        private static final int STEP = 19, IN_X = 1, ARROW_X = 3 * STEP + 6, OUT_X = ARROW_X + 28, ROW2 = 24, TEXT_Y = 48, LINE = 8,
              WIDTH = 196, MACH_Y = TEXT_Y + 3 * LINE + 4, LABEL_X = OUT_X + 42;

        AlloyCategory(IGuiHelper helper) {
            super(helper, ALLOYS, "jei.pioneers.alloys", PiBronze.CRUCIBLE_FURNACE.getBlock(), WIDTH, MACH_Y + 18);
            family(JeiDraw.Family.BRONZE);
            footerLines(2);
        }

        @Override
        protected Component headerTitle(Alloy recipe) {
            return Component.translatable("jei.pioneers.alloys.title", Metals.name(recipe.alloy()));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Alloy recipe, @NotNull IFocusGroup focuses) {
            for (int i = 0; i < recipe.parts().size() && i < 3; i++) {
                Part p = recipe.parts().get(i);
                ItemStack ingot = ForgeMetals.ingot(p.metal(), p.ingots());
                if (!ingot.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, IN_X + i * STEP, 1).setStandardSlotBackground().addItemStack(ingot);
                }
                //rangee 2 : les minerais qui valent un lingot chacun aux postes du feu (U sur le minerai de cuivre tombe ici)
                List<ItemStack> ores = oresOf(p.metal(), p.ingots());
                if (!ores.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, IN_X + i * STEP, 1 + ROW2).setStandardSlotBackground().addItemStacks(ores);
                }
            }
            Fluid fluid = molten(recipe.alloy());
            if (fluid != Fluids.EMPTY) {
                fluid(builder, RecipeIngredientRole.OUTPUT, OUT_X - 1, 0, 18, 18, fluid, (long) recipe.ingots() * Metals.INGOT, (long) recipe.ingots() * Metals.INGOT);
            }
            ItemStack ingot = alloyIngot(recipe.alloy(), recipe.ingots());
            if (!ingot.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + 20, 1).setStandardSlotBackground().addItemStack(ingot);
            }
            //les machines qui font le melange (infobulle : comment)
            for (int i = 0; i < recipe.routes().size() && i < 10; i++) {
                Route r = recipe.routes().get(i);
                builder.addSlot(RecipeIngredientRole.CATALYST, 1 + i * STEP, MACH_Y).setStandardSlotBackground().addItemStack(r.machine())
                      .addRichTooltipCallback((view, tooltip) -> tooltip.add(r.how().copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
            }
        }

        private static ItemStack alloyIngot(String alloy, int count) {
            ItemStack forge = ForgeMetals.ingot(alloy, count);
            if (!forge.isEmpty()) {
                return forge;
            }
            Item item = BuiltInRegistries.ITEM.get(Pioneers.rl(alloy + "_ingot"));
            return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Alloy recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(200).setPosition(ARROW_X, 1);
        }

        @Override
        protected void drawContent(@NotNull Alloy recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            text(graphics, Component.translatable("jei.pioneers.alloys.ingots"), ARROW_X - 2, 19, 0.5F, TEXT_LIGHT);
            text(graphics, Component.translatable("jei.pioneers.alloys.from_ore"), ARROW_X - 2, ROW2, 0.5F, TEXT_LIGHT);
            JeiDraw.still(graphics, ARROW_X + 11, ROW2 + 12);
            fitted(graphics, Component.translatable("jei.pioneers.alloys.ore_value"), OUT_X - 1, ROW2 + 5, 0.5F, TEXT_LIGHT, cw() - OUT_X + 1);
            fitted(graphics, Component.translatable("jei.pioneers.alloys.ore_value2"), OUT_X - 1, ROW2 + 12, 0.5F, TEXT_LIGHT, cw() - OUT_X + 1);
            centered(graphics, Component.literal(recipe.ingots() * Metals.INGOT + " mB"), OUT_X + 8, 19, 0.5F, GOOD);
            fitted(graphics, Metals.name(recipe.alloy()), LABEL_X, 3, SMALL, TEXT, cw() - LABEL_X);
            graphics.fill(0, TEXT_Y - 2, cw(), TEXT_Y - 1, 0x40FFFFFF);
            float y = TEXT_Y;
            for (Part p : recipe.parts()) {
                String range = p.min() == p.max() ? p.min() + " %" : p.min() + "–" + p.max() + " %";
                fitted(graphics, Component.translatable("jei.pioneers.alloys.part2", Metals.name(p.metal()), p.ingots(), p.ingots() * Metals.INGOT, range), 0, y,
                      0.5F, TEXT, cw());
                y += LINE;
            }
            text(graphics, Component.translatable("jei.pioneers.alloys.machines"), 0, MACH_Y - 6, 0.5F, TEXT_LIGHT);
        }

        @Override
        protected List<JeiDraw.Chip> chips(Alloy recipe) {
            List<JeiDraw.Chip> out = new ArrayList<>();
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.FLUID, Component.translatable("jei.pioneers.alloys.molten"), TEXT,
                  Component.translatable("jei.pioneers.alloys.molten_tip2")));
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.WARN, Component.translatable("jei.pioneers.alloys.no_powder"), WARN,
                  Component.translatable("jei.pioneers.alloys.no_powder_tip")));
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp", Metals.meltingC(recipe.alloy())), TEXT,
                  Component.translatable("jei.pioneers.forge.melts_at", Metals.meltingC(recipe.alloy()))));
            if (recipe.gas()) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.alloys.gas"), TEXT,
                      Component.translatable("jei.pioneers.alloys.gas_tip")));
            }
            return out;
        }
    }
}
