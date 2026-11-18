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
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.forge.PiForge;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.machine.PiMachineKit;
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
     * Une page « Fusion des metaux » : le metal, le groupe, les objets et leur VALEUR (mB : le metal qu'ils valent) ; par objet : part rendue
     * aux postes du feu et du bronze (0 : ils ne le fondent pas ; 0,5 : une piece a l'age du bronze), part rendue au creuset industriel a
     * chaque tier (null : il ne le fond pas) ; duree de la recette du creuset industriel. 1.0.6 : SEUL produit, le metal fondu (le lingot
     * s'obtient en coulant : pages « Lingot d'alliage » et « Coulee »).
     */
    record Melt(String metal, Group group, List<ItemStack> items, List<Integer> mb, List<Double> fire, List<double[]> electric, int electricTime,
                List<Integer> slag) {

        boolean anyFire() {
            return fire.stream().anyMatch(f -> f > 0);
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

    /** 1.0.6 : page de l'alliage FONDU (produit : le metal fondu) ou du LINGOT d'alliage (produit : le lingot). */
    enum Kind {
        LIQUID, SOLID
    }

    /** Une voie de la page du lingot : couler le metal fondu ({@code parts} vide, {@code fluidMb}) ou allier des lingots sans fondre. */
    record Way(Component label, List<Part> parts, int fluidMb, ItemStack out, List<Route> machines, boolean gas) {
    }

    record Alloy(String alloy, Kind kind, List<Part> parts, int ingots, List<Route> routes, boolean gas, List<Way> ways) {
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
        /** mB rendus aux postes du feu et du bronze pour la pile affichee (0 : ils ne la fondent pas). */
        int fire;
        /** Creuset industriel : mB de la recette (x1) et mB rendus a chaque tier (null : il ne la fond pas). */
        int electric;
        int[] yields;
        int time;
        int slag;

        Entry(ItemStack stack, String metal) {
            this.stack = stack;
            this.metal = metal;
        }

        String page(Group g) {
            return metal + "|" + g.name();
        }

        /** Valeur de reference (le metal que vaut la pile : le plus grand des deux postes a x1). */
        int value() {
            return Math.max(fire, electric);
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
            by.computeIfAbsent(item, i -> new Entry(new ItemStack(i), melt.metal())).fire = melt.mb();
        }
        for (IndustrialRecipe r : IndustryRecipes.of(level, IndustryMachine.ELECTRIC_CRUCIBLE)) {
            if (!isMelt(r)) {
                continue;
            }
            String metal = moltenMetal(r.fluidResults().get(0));
            BenchRecipe.Counted in = r.items().get(0);
            for (ItemStack st : in.ingredient().getItems()) {
                Entry e = by.computeIfAbsent(st.getItem(), i -> new Entry(new ItemStack(i, in.count()), metal));
                if (!e.metal.equals(metal)) {
                    continue;
                }
                int n = Math.max(1, in.count());
                e.electric = r.fluidResults().get(0).getAmount() * e.stack.getCount() / n;
                e.yields = new int[4];
                for (int t = 0; t < 4; t++) {
                    e.yields[t] = r.yieldedFluid(t) * e.stack.getCount() / n;
                }
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
                List<Double> fire = new ArrayList<>();
                List<double[]> electric = new ArrayList<>();
                List<Integer> slag = new ArrayList<>();
                int time = 0;
                for (Entry e : rows.subList(from, Math.min(rows.size(), from + PER_PAGE))) {
                    int value = Math.max(1, e.value());
                    items.add(e.stack);
                    mb.add(e.value());
                    fire.add(e.fire / (double) value);
                    double[] f = null;
                    if (e.yields != null) {
                        f = new double[4];
                        for (int t = 0; t < 4; t++) {
                            f[t] = Math.round(e.yields[t] * 100.0 / value) / 100.0;
                        }
                    }
                    electric.add(f);
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

        /**
         * 1.0.6 (Guillaume : « plus simple tout en restant comprehensible ») : les objets (valeur en mB dessous) -> le metal fondu SEUL ;
         * dessous, deux lignes : les postes du feu et du bronze (icones, survol : la chaleur) et le creuset industriel (icones, survol : MKI a
         * MKIV), avec ce qu'ils rendent de la valeur des objets.
         */
        private static final int COLS = 8, STEP = 19, ROW_H = 26, ARROW_X = COLS * STEP + 2, OUT_X = ARROW_X + 26, WIDTH = OUT_X + 18;
        private static final int LINES_Y = 2 * ROW_H + 3, LINE_H = 20, TEXT_X = 3 * STEP + 3;

        MeltCategory(IGuiHelper helper) {
            super(helper, MELTING, "jei.pioneers.metal_melting", PiBronze.CRUCIBLE_FURNACE.getBlock(), WIDTH, LINES_Y + LINE_H + 18);
            family(JeiDraw.Family.FORGE);
            footerLines(2);
        }

        @Override
        protected Component headerTitle(Melt recipe) {
            return Component.translatable("jei.pioneers.metal_melting.title", Metals.name(recipe.metal()),
                  Component.translatable("jei.pioneers.melt.group." + recipe.group().key()));
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
                fluid(builder, RecipeIngredientRole.OUTPUT, OUT_X - 1, 0, 18, ROW_H + 18, fluid, Metals.INGOT, Metals.INGOT);
            }
            int melt = ForgeMetals.meltingC(recipe.metal());
            int y = LINES_Y;
            if (recipe.anyFire()) {
                for (Station s : Station.values()) {
                    Component how = Component.translatable("jei.pioneers.metal_melting." + s.key).copy().append(" : ").append(heat(s, melt));
                    builder.addSlot(RecipeIngredientRole.CATALYST, 1 + s.ordinal() * STEP, y).setStandardSlotBackground()
                          .addItemStack(new ItemStack(s.block()))
                          .addRichTooltipCallback((view, tooltip) -> tooltip.add(how.copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
                }
                y += LINE_H;
            }
            if (recipe.anyElectric()) {
                List<ItemStack> blocks = machines(IndustryMachine.ELECTRIC_CRUCIBLE);
                Component how = Component.translatable("jei.pioneers.metal_melting.electric_how", tiers(recipe));
                for (int i = 0; i < blocks.size() && i < 2; i++) {
                    ItemStack b = i == 0 ? blocks.get(0) : blocks.get(blocks.size() - 1);
                    builder.addSlot(RecipeIngredientRole.CATALYST, 1 + i * STEP, y).setStandardSlotBackground().addItemStack(b)
                          .addRichTooltipCallback((view, tooltip) -> tooltip.add(how.copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
                }
            }
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Melt recipe, @NotNull IFocusGroup focuses) {
            builder.addAnimatedRecipeArrow(!recipe.anyFire() ? Math.max(20, recipe.electricTime()) : 60).setPosition(ARROW_X, 1 + ROW_H / 2);
        }

        @Override
        protected void drawContent(@NotNull Melt recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            for (int i = 0; i < recipe.mb().size(); i++) {
                centered(graphics, Component.literal(recipe.mb().get(i) + ""), 1 + (i % COLS) * STEP + 9, 1 + (i / COLS) * ROW_H + 19, 0.5F, TEXT);
            }
            centered(graphics, Component.literal("mB"), ARROW_X + 11, 1 + ROW_H / 2 + 18, 0.5F, TEXT_LIGHT);
            graphics.fill(0, LINES_Y - 2, cw(), LINES_Y - 1, 0x40FFFFFF);
            int y = LINES_Y + 4;
            if (recipe.anyFire()) {
                double lo = 1, hi = 0;
                int n = 0;
                for (double f : recipe.fire()) {
                    if (f > 0) {
                        lo = Math.min(lo, f);
                        hi = Math.max(hi, f);
                        n++;
                    }
                }
                Component share = share(lo, hi);
                fitted(graphics, Component.translatable("jei.pioneers.metal_melting.fire_line", share), TEXT_X, y, SMALL, lo < 1 ? WARN : GOOD,
                      cw() - TEXT_X);
                if (n < recipe.items().size()) {
                    fitted(graphics, Component.translatable("jei.pioneers.metal_melting.some", n, recipe.items().size()), TEXT_X, y + 8, 0.5F, TEXT_LIGHT,
                          cw() - TEXT_X);
                }
                y += LINE_H;
            }
            if (recipe.anyElectric()) {
                fitted(graphics, Component.translatable("jei.pioneers.metal_melting.electric_line", tiers(recipe)), TEXT_X, y, SMALL, GOOD, cw() - TEXT_X);
                long n = recipe.electric().stream().filter(java.util.Objects::nonNull).count();
                if (n < recipe.items().size()) {
                    fitted(graphics, Component.translatable("jei.pioneers.metal_melting.some", n, recipe.items().size()), TEXT_X, y + 8, 0.5F, TEXT_LIGHT,
                          cw() - TEXT_X);
                }
            }
        }

        /** « tout (×1) », « la moitié (×0,5) » ou « ×0,5 à ×1 ». */
        private static Component share(double lo, double hi) {
            if (Math.abs(hi - lo) < 1e-6) {
                return Math.abs(lo - 1) < 1e-6 ? Component.translatable("jei.pioneers.metal_melting.all")
                       : Math.abs(lo - 0.5) < 1e-6 ? Component.translatable("jei.pioneers.metal_melting.half") : Component.literal("×" + number(lo));
            }
            return Component.literal("×" + number(lo) + " – ×" + number(hi));
        }

        /** Creuset industriel : la part rendue (la meme a tous les tiers) ou « ×1,5 (MKI) → ×3 (MKIV) » (minerai). */
        private static Component tiers(Melt recipe) {
            double lo1 = Double.MAX_VALUE, hi1 = 0, lo4 = Double.MAX_VALUE, hi4 = 0;
            for (double[] e : recipe.electric()) {
                if (e != null) {
                    lo1 = Math.min(lo1, e[0]);
                    hi1 = Math.max(hi1, e[0]);
                    lo4 = Math.min(lo4, e[3]);
                    hi4 = Math.max(hi4, e[3]);
                }
            }
            if (Math.abs(lo1 - lo4) < 1e-6 && Math.abs(hi1 - hi4) < 1e-6) {
                return share(lo1, hi1);
            }
            return Component.translatable("jei.pioneers.metal_melting.by_tier", number(lo1), number(hi4));
        }

        /** Infobulle d'un objet : ce qu'il rend (mB) aux postes du feu et a chaque tier du creuset industriel. */
        private static List<Component> itemLines(Melt recipe, int i) {
            List<Component> out = new ArrayList<>();
            int mb = recipe.mb().get(i);
            double fire = recipe.fire().get(i);
            out.add(Component.translatable("jei.pioneers.metal_melting.item_value", mb).withStyle(net.minecraft.ChatFormatting.GRAY));
            out.add((fire > 0 ? Component.translatable("jei.pioneers.metal_melting.item_fire", Math.round(mb * fire))
                     : Component.translatable("jei.pioneers.metal_melting.item_no_fire2")).withStyle(net.minecraft.ChatFormatting.GRAY));
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
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.FLUID, Component.translatable("jei.pioneers.metal_melting.liquid_chip"), TEXT,
                  Component.translatable("jei.pioneers.metal_melting.liquid_tip")));
            //1.0.5-beta (lot 39) : la fonte au creuset industriel rend un peu de laitier (minerai 10 %, recyclage 5 %)
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
            String metal = ingotMetal(c);
            if (metal == null) {
                return null;
            }
            metals.add(metal);
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

    /**
     * 1.0.6 (Guillaume, 17/11/2026 : « clairement distinguer le liquide du solide, selon l'objet sur lequel le joueur clique ») : les pages
     * des alliages se font sur les VRAIES recettes, en deux sortes :
     * <ul>
     *   <li>{@link Kind#LIQUID} : l'alliage FONDU (seul produit : le metal fondu) : les metaux fondus ensemble au creuset (postes du feu et
     *       du bronze pour le bronze, le laiton et la soudure ; creuset industriel) ;</li>
     *   <li>{@link Kind#SOLID} : le LINGOT d'alliage (seul produit : le lingot) : couler le metal fondu (moule ou matrice a lingot), ou allier
     *       les lingots sans les fondre (four a induction a vapeur puis MKI a MKIV, creuset industriel sous argon).</li>
     * </ul>
     * Le metal fondu n'est produit que par la page « fondue », le lingot que par la page « lingot » : JEI montre l'une ou l'autre selon
     * l'objet clique.
     */
    static List<Alloy> alloys(Level level) {
        Map<String, Liquid> liquid = new LinkedHashMap<>();
        Map<String, List<Way>> direct = new LinkedHashMap<>();
        //1. alliages du bain aux postes du feu et du bronze (Metals.RECIPES)
        for (String alloy : List.of("bronze", "brass", "solder")) {
            Map<String, Integer> recipe = Metals.RECIPES.get(alloy);
            if (recipe == null || molten(alloy) == Fluids.EMPTY) {
                continue;
            }
            List<Part> parts = parts(alloy, recipe.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                  .map(e -> Map.entry(e.getKey(), e.getValue())).toList());
            Liquid l = liquid.computeIfAbsent(alloy, a -> new Liquid(parts));
            l.routes.add(new Route(new ItemStack(PiBronze.CRUCIBLE_FURNACE.getBlock()), Component.translatable("jei.pioneers.alloys.how.furnace",
                  Metals.name(alloy), ratio(parts))));
            l.routes.add(new Route(new ItemStack(PiBronze.AUTO_FOUNDRY.getBlock()), Component.translatable("jei.pioneers.alloys.how.foundry",
                  Metals.name(alloy), ratio(parts))));
            l.routes.add(new Route(new ItemStack(PiForge.CLAY_CRUCIBLE.getBlock()), Component.translatable("jei.pioneers.alloys.how.clay",
                  MoltenLoad.CLAY_CRUCIBLE_MB, clayDose(parts))));
        }
        //2. creuset industriel : metal fondu (page fondue) ou lingot sous argon (page lingot) ; four a induction MK : lingot
        for (IndustryMachine m : ALLOY_MACHINES) {
            List<ItemStack> blocks = machines(m);
            for (IndustrialRecipe r : IndustryRecipes.withoutLow(IndustryRecipes.of(level, m))) {   // 1.0.5-beta (lot 38) : sans les converties
                String alloy = alloyOf(r);
                if (alloy == null || blocks.isEmpty()) {
                    continue;
                }
                List<Map.Entry<String, Integer>> counts = new ArrayList<>();
                for (BenchRecipe.Counted c : r.items()) {
                    counts.add(Map.entry(ingotMetal(c), c.count()));
                }
                boolean gas = !r.gases().isEmpty();
                if (!r.fluidResults().isEmpty()) {
                    Liquid l = liquid.computeIfAbsent(alloy, a -> new Liquid(parts(a, counts)));
                    l.gas |= gas;
                    Component how = Component.translatable(gas ? "jei.pioneers.alloys.how.crucible_gas" : "jei.pioneers.alloys.how.crucible",
                          ratio(l.parts), PiCategory.seconds(r.time()));
                    for (ItemStack b : ends(blocks)) {
                        l.routes.add(new Route(b, how));
                    }
                } else {
                    List<Part> parts = parts(alloy, counts);
                    Component how = Component.translatable(m == IndustryMachine.INDUCTION_FURNACE ? "jei.pioneers.alloys.how.induction_mk"
                          : gas ? "jei.pioneers.alloys.how.vacuum" : "jei.pioneers.alloys.how.crucible_ingot", ratio(parts), PiCategory.seconds(r.time()));
                    List<Route> routes = new ArrayList<>();
                    for (ItemStack b : ends(blocks)) {
                        routes.add(new Route(b, how));
                    }
                    direct.computeIfAbsent(alloy, a -> new ArrayList<>()).add(new Way(Component.translatable(m == IndustryMachine.INDUCTION_FURNACE
                          ? "jei.pioneers.alloys.way.induction" : "jei.pioneers.alloys.way.vacuum"), parts, 0, r.results().get(0).stack().copy(), routes, gas));
                }
            }
        }
        //3. four a induction a vapeur (T1 ; le four MKI a MKIV fait aussi les recettes de sa famille) : lingots -> lingots d'alliage
        ItemLike steam = PiBronze.STEAM_MACHINES.get(fr.pioneers.tech.bronze.machines.SteamMachine.INDUCTION_FURNACE).getBlock();
        for (fr.pioneers.tech.bronze.machines.SteamRecipe s : level.getRecipeManager().getAllRecipesFor(PiBronze.STEAM_PROCESSING.get())) {
            String alloy = s.machine() == fr.pioneers.tech.bronze.machines.SteamMachine.INDUCTION_FURNACE ? steamAlloy(s) : null;
            if (alloy == null) {
                continue;
            }
            List<Map.Entry<String, Integer>> counts = new ArrayList<>();
            for (BenchRecipe.Counted c : s.inputs()) {
                counts.add(Map.entry(ingotMetal(c), c.count()));
            }
            List<Part> parts = parts(alloy, counts);
            Component how = Component.translatable("jei.pioneers.alloys.how.induction2", ratio(parts), s.result().getCount(), PiCategory.seconds(s.time()));
            List<Route> routes = new ArrayList<>(List.of(new Route(new ItemStack(steam), how)));
            for (ItemStack b : ends(machines(IndustryMachine.INDUCTION_FURNACE))) {
                routes.add(new Route(b, how));
            }
            direct.computeIfAbsent(alloy, a -> new ArrayList<>()).add(0, new Way(Component.translatable("jei.pioneers.alloys.way.induction"), parts, 0,
                  s.result().copy(), routes, false));
        }
        //pages : la fondue puis le lingot de chaque alliage
        List<Alloy> out = new ArrayList<>();
        java.util.Set<String> all = new java.util.LinkedHashSet<>(liquid.keySet());
        all.addAll(direct.keySet());
        for (String alloy : all) {
            Liquid l = liquid.get(alloy);
            if (l != null) {
                int total = l.parts.stream().mapToInt(Part::ingots).sum();
                out.add(new Alloy(alloy, Kind.LIQUID, l.parts, total, l.routes, l.gas, List.of()));
            }
            List<Way> ways = new ArrayList<>();
            ItemStack ingot = alloyIngot(alloy, 1);
            List<Route> cast = l == null || ingot.isEmpty() ? List.of() : casting(level, alloy, ingot);
            if (!cast.isEmpty()) {
                ways.add(new Way(Component.translatable("jei.pioneers.alloys.way.cast"), List.of(), Metals.INGOT, ingot, cast, false));
            }
            ways.addAll(direct.getOrDefault(alloy, List.of()));
            if (!ways.isEmpty()) {
                out.add(new Alloy(alloy, Kind.SOLID, List.of(), 0, List.of(), ways.stream().anyMatch(Way::gas), ways.subList(0, Math.min(3, ways.size()))));
            }
        }
        return out;
    }

    /**
     * Metal d'une entree faite de lingots : le premier objet de l'ingredient qui est un lingot connu de la table de fusion (le tag
     * {@code forge:ingots/copper} commence par le lingot de cuivre vanilla, absent de la table : on prend le suivant), sinon le tag
     * {@code forge:ingots/<metal>} de l'objet ; null si ce n'est pas un lingot.
     */
    @Nullable
    static String ingotMetal(BenchRecipe.Counted c) {
        for (ItemStack st : c.ingredient().getItems()) {
            if (ForgeMetals.isIngot(st)) {
                return Metals.melt(st).metal();
            }
        }
        //metaux absents de la table de l'age du bronze (nickel, chrome, titane...) : leur tag forge:ingots/<metal>
        for (ItemStack st : c.ingredient().getItems()) {
            var tag = st.getTags().filter(t -> t.location().getNamespace().equals("forge") && t.location().getPath().startsWith("ingots/"))
                  .findFirst();
            if (tag.isPresent()) {
                return tag.get().location().getPath().substring("ingots/".length());
            }
        }
        return null;
    }

    /** Lingot d'un metal (tag forge, sinon {@code pioneers:<metal>_ingot}) ; vide s'il n'existe pas. */
    static ItemStack alloyIngot(String alloy, int count) {
        ItemStack forge = ForgeMetals.ingot(alloy, count);
        if (!forge.isEmpty()) {
            return forge;
        }
        Item item = BuiltInRegistries.ITEM.get(Pioneers.rl(alloy + "_ingot"));
        return item == Items.AIR ? ItemStack.EMPTY : new ItemStack(item, count);
    }

    /** Fondue d'un alliage en construction : ses parts, ses postes (creusets), gaz protecteur. */
    private static final class Liquid {
        final List<Part> parts;
        final List<Route> routes = new ArrayList<>();
        boolean gas;

        Liquid(List<Part> parts) {
            this.parts = parts;
        }
    }

    /** Parts d'un alliage (metal, lingots) avec les fourchettes de sa composition (sinon la part exacte de la recette). */
    private static List<Part> parts(String alloy, List<Map.Entry<String, Integer>> counts) {
        GeneratedMaterials.Metal metal = Metals.get(alloy);
        int sum = Math.max(1, counts.stream().mapToInt(Map.Entry::getValue).sum());
        List<Part> out = new ArrayList<>();
        for (Map.Entry<String, Integer> e : counts) {
            int pct = (int) Math.round(e.getValue() * 100.0 / sum), min = pct, max = pct;
            if (metal != null) {
                for (GeneratedMaterials.Share share : metal.composition()) {
                    if (share.metal().equals(e.getKey())) {
                        min = (int) Math.round(share.min());
                        max = (int) Math.round(share.max());
                    }
                }
            }
            out.add(new Part(e.getKey(), e.getValue(), min, max));
        }
        return out;
    }

    /** Premier compact (MKI) et controleur du multibloc d'une machine (deux icones suffisent, l'infobulle dit le reste). */
    private static List<ItemStack> ends(List<ItemStack> blocks) {
        if (blocks.size() <= 2) {
            return blocks;
        }
        return List.of(blocks.get(0), blocks.get(blocks.size() - 1));
    }

    /** Alliage d'une recette du four a induction a vapeur : que des lingots d'au moins deux metaux -> lingot d'un autre metal. */
    @Nullable
    private static String steamAlloy(fr.pioneers.tech.bronze.machines.SteamRecipe s) {
        if (s.inputs().size() < 2) {
            return null;
        }
        java.util.Set<String> metals = new java.util.HashSet<>();
        for (BenchRecipe.Counted c : s.inputs()) {
            String metal = ingotMetal(c);
            if (metal == null) {
                return null;
            }
            metals.add(metal);
        }
        String path = BuiltInRegistries.ITEM.getKey(s.result().getItem()).getPath();
        String alloy = path.endsWith("_ingot") ? path.substring(0, path.length() - 6) : null;
        return alloy == null || metals.size() < 2 || metals.contains(alloy) ? null : alloy;
    }

    /**
     * Ou couler le lingot d'un alliage : moule a lingot (postes du bronze : table de coulee, sol, moule automatique) s'il a sa recette de
     * coulee, machine de coulee et matrice a lingot si la machine a la recette.
     */
    private static List<Route> casting(Level level, String alloy, ItemStack ingot) {
        List<Route> out = new ArrayList<>();
        Item mold = PiBronze.MOLDS.containsKey("ingot") ? PiBronze.MOLDS.get("ingot").get() : null;
        boolean bronze = mold != null && level.getRecipeManager().getAllRecipesFor(PiBronze.CASTING.get()).stream()
              .anyMatch(c -> c.mold() == mold && c.metal().equals(alloy) && c.result().is(ingot.getItem()));
        if (bronze) {
            out.add(new Route(new ItemStack(mold), Component.translatable("jei.pioneers.alloys.how.mold", Metals.INGOT)));
            out.add(new Route(new ItemStack(PiBronze.CASTING_TABLE.getBlock()), Component.translatable("jei.pioneers.alloys.how.table")));
        }
        Fluid fluid = molten(alloy);
        boolean machine = IndustryRecipes.of(level, IndustryMachine.CASTING_MACHINE).stream().anyMatch(r -> !r.results().isEmpty()
              && r.results().get(0).stack().is(ingot.getItem()) && r.fluids().stream().anyMatch(f -> f.fluid() == fluid));
        List<ItemStack> blocks = machines(IndustryMachine.CASTING_MACHINE);
        if (machine && !blocks.isEmpty()) {
            Component how = Component.translatable("jei.pioneers.alloys.how.casting_machine");
            out.add(new Route(blocks.get(0), how));
            Item die = BuiltInRegistries.ITEM.get(Pioneers.rl("ingot_die"));
            if (die != Items.AIR) {
                out.add(new Route(new ItemStack(die), how));
            }
        }
        return out;
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

    /**
     * Deux sortes de pages (1.0.6) : l'alliage FONDU (lingots ou minerais fondus ensemble -> metal fondu ; ou les fondre ensemble) et le
     * LINGOT d'alliage (une ligne par voie : couler le metal fondu ; allier les lingots sans les fondre).
     */
    static final class AlloyCategory extends PiCategory<Alloy> {

        private static final int STEP = 19, IN_X = 1, ARROW_X = 3 * STEP + 6, OUT_X = ARROW_X + 28, ROW2 = 24, TEXT_Y = 48, LINE = 8,
              WIDTH = 196, MACH_Y = TEXT_Y + 3 * LINE + 4, LABEL_X = OUT_X + 22, WAY_H = 31, WAY_SLOT = 7, MACH_X = OUT_X + 26;

        AlloyCategory(IGuiHelper helper) {
            super(helper, ALLOYS, "jei.pioneers.alloys", PiBronze.CRUCIBLE_FURNACE.getBlock(), WIDTH, MACH_Y + 18);
            family(JeiDraw.Family.BRONZE);
            footerLines(2);
        }

        @Override
        protected Component headerTitle(Alloy recipe) {
            return Component.translatable(recipe.kind() == Kind.LIQUID ? "jei.pioneers.alloys.title_liquid" : "jei.pioneers.alloys.title_solid",
                  Metals.name(recipe.alloy()));
        }

        @Override
        protected void setContent(@NotNull IRecipeLayoutBuilder builder, Alloy recipe, @NotNull IFocusGroup focuses) {
            if (recipe.kind() == Kind.SOLID) {
                solid(builder, recipe);
                return;
            }
            boolean fire = Metals.RECIPES.containsKey(recipe.alloy());
            for (int i = 0; i < recipe.parts().size() && i < 3; i++) {
                Part p = recipe.parts().get(i);
                ItemStack ingot = alloyIngot(p.metal(), p.ingots());
                if (!ingot.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, IN_X + i * STEP, 1).setStandardSlotBackground().addItemStack(ingot);
                }
                //rangee 2 : les minerais qui valent un lingot chacun aux postes du feu (U sur le minerai de cuivre tombe ici)
                List<ItemStack> ores = fire ? oresOf(p.metal(), p.ingots()) : List.of();
                if (!ores.isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, IN_X + i * STEP, 1 + ROW2).setStandardSlotBackground().addItemStacks(ores);
                }
            }
            Fluid fluid = molten(recipe.alloy());
            long mb = (long) recipe.ingots() * Metals.INGOT;
            fluid(builder, RecipeIngredientRole.OUTPUT, OUT_X - 1, 0, 18, 18, fluid, mb, mb);
            routes(builder, recipe.routes(), 1, MACH_Y, 10);
        }

        /** Page du lingot : une ligne par voie (entrees a droite, fleche, lingot, machines avec leur infobulle). */
        private static void solid(IRecipeLayoutBuilder builder, Alloy recipe) {
            for (int w = 0; w < recipe.ways().size(); w++) {
                Way way = recipe.ways().get(w);
                int y = w * WAY_H + WAY_SLOT;
                if (way.parts().isEmpty()) {
                    fluid(builder, RecipeIngredientRole.INPUT, IN_X + 2 * STEP - 1, y - 1, 18, 18, molten(recipe.alloy()), way.fluidMb(), way.fluidMb());
                } else {
                    int n = Math.min(3, way.parts().size());
                    for (int i = 0; i < n; i++) {
                        Part p = way.parts().get(i);
                        builder.addSlot(RecipeIngredientRole.INPUT, IN_X + (3 - n + i) * STEP, y).setStandardSlotBackground()
                              .addItemStack(alloyIngot(p.metal(), p.ingots()));
                    }
                }
                builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X, y).setStandardSlotBackground().addItemStack(way.out());
                routes(builder, way.machines(), MACH_X, y, 4);
            }
        }

        private static void routes(IRecipeLayoutBuilder builder, List<Route> routes, int x, int y, int max) {
            for (int i = 0; i < routes.size() && i < max; i++) {
                Route r = routes.get(i);
                builder.addSlot(RecipeIngredientRole.CATALYST, x + i * STEP, y).setStandardSlotBackground().addItemStack(r.machine())
                      .addRichTooltipCallback((view, tooltip) -> tooltip.add(r.how().copy().withStyle(net.minecraft.ChatFormatting.GRAY)));
            }
        }

        @Override
        protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Alloy recipe, @NotNull IFocusGroup focuses) {
            if (recipe.kind() == Kind.LIQUID) {
                builder.addAnimatedRecipeArrow(200).setPosition(ARROW_X, 1);
                return;
            }
            for (int w = 0; w < recipe.ways().size(); w++) {
                builder.addAnimatedRecipeArrow(recipe.ways().get(w).parts().isEmpty() ? 100 : 120).setPosition(ARROW_X, w * WAY_H + WAY_SLOT);
            }
        }

        @Override
        protected void drawContent(@NotNull Alloy recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
            if (recipe.kind() == Kind.SOLID) {
                for (int w = 0; w < recipe.ways().size(); w++) {
                    Way way = recipe.ways().get(w);
                    int y = w * WAY_H;
                    fitted(graphics, way.label(), 0, y, 0.5F, TEXT_LIGHT, cw());
                    if (way.parts().isEmpty()) {
                        Component mb = Component.literal(way.fluidMb() + " mB");
                        text(graphics, mb, IN_X + 2 * STEP - 3 - font().width(mb) * 0.5F, y + WAY_SLOT + 6, 0.5F, TEXT_LIGHT);
                    }
                    if (w > 0) {
                        graphics.fill(0, y - 3, cw(), y - 2, 0x40FFFFFF);
                    }
                }
                return;
            }
            boolean fire = Metals.RECIPES.containsKey(recipe.alloy());
            fitted(graphics, Metals.name(recipe.alloy()), LABEL_X, 2, SMALL, TEXT, cw() - LABEL_X);
            text(graphics, Component.literal(recipe.ingots() * Metals.INGOT + " mB"), LABEL_X, 11, 0.5F, GOOD);
            text(graphics, Component.translatable("jei.pioneers.alloys.ingots"), ARROW_X - 2, 19, 0.5F, TEXT_LIGHT);
            if (fire) {
                text(graphics, Component.translatable("jei.pioneers.alloys.from_ore"), ARROW_X - 2, ROW2 + 4, 0.5F, TEXT_LIGHT);
                fitted(graphics, Component.translatable("jei.pioneers.alloys.ore_value"), OUT_X - 1, ROW2 + 5, 0.5F, TEXT_LIGHT, cw() - OUT_X + 1);
                fitted(graphics, Component.translatable("jei.pioneers.alloys.ore_value2"), OUT_X - 1, ROW2 + 12, 0.5F, TEXT_LIGHT, cw() - OUT_X + 1);
            } else {
                fitted(graphics, Component.translatable("jei.pioneers.alloys.ingots_only"), 0, ROW2 + 5, 0.5F, TEXT_LIGHT, cw());
            }
            graphics.fill(0, TEXT_Y - 2, cw(), TEXT_Y - 1, 0x40FFFFFF);
            float y = TEXT_Y;
            for (Part p : recipe.parts()) {
                int pct = (int) Math.round(p.ingots() * 100.0 / Math.max(1, recipe.ingots()));
                String range = p.min() == p.max() ? pct + " %" : pct + " % (" + p.min() + "–" + p.max() + " %)";
                fitted(graphics, Component.translatable("jei.pioneers.alloys.part3", Metals.name(p.metal()), p.ingots(), range), 0, y, 0.5F, TEXT, cw());
                y += LINE;
            }
            text(graphics, Component.translatable("jei.pioneers.alloys.machines"), 0, MACH_Y - 6, 0.5F, TEXT_LIGHT);
        }

        @Override
        protected List<JeiDraw.Chip> chips(Alloy recipe) {
            List<JeiDraw.Chip> out = new ArrayList<>();
            if (recipe.kind() == Kind.LIQUID) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.FLUID, Component.translatable("jei.pioneers.alloys.molten"), TEXT,
                      Component.translatable("jei.pioneers.alloys.molten_tip2"), Component.translatable("jei.pioneers.alloys.to_ingot")));
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.TEMP, Component.translatable("jei.pioneers.chip.temp", Metals.meltingC(recipe.alloy())), TEXT,
                      Component.translatable("jei.pioneers.forge.melts_at", Metals.meltingC(recipe.alloy()))));
            } else {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.alloys.ways"), TEXT,
                      Component.translatable("jei.pioneers.alloys.ways_tip1"), Component.translatable("jei.pioneers.alloys.ways_tip2")));
            }
            if (recipe.gas()) {
                out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.alloys.gas"), TEXT,
                      Component.translatable("jei.pioneers.alloys.gas_tip")));
            }
            return out;
        }
    }
}
