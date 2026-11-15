package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.bronze.bench.BenchRecipe.Counted;
import fr.pioneers.tech.industry.Tiers;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import fr.pioneers.tech.kinetics.machines.MachineType;
import fr.pioneers.tech.kinetics.machines.MechanicalRecipe;
import fr.pioneers.tech.materials.GeneratedMaterials;
import fr.pioneers.core.recipes.PiRecipes;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiKinetics;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
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
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.ItemLike;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.3-beta (lot 4, regles de Guillaume du 05/11/2026) : la page « Pieces metalliques ». Une page par piece et par metal (brame, billette,
 * fil machine, ebauche d'engrenage, bloc, plaque, tole, feuille, tige, fil, anneau, ressort, engrenage, tube, grillage, cable tresse) avec
 * TOUTES ses voies, une ligne par voie : le poste (etabli de composants de base, grille, tour a bois, machine du tier), ses entrees (outils
 * de l'etabli et forme d'extrusion compris), le nombre de pieces, le tier. Meme principe que la page unique « Broyage » du lot 3 : la piece
 * d'abord, puis ce qui la fait. Lue sur les recettes CHARGEES (rien de copie ici) : etabli de composants de base, recettes industrielles de
 * toutes les machines (le compacteur du kit compris, par {@link IndustryRecipes#of}), tour a bois, grille. Les copies du poste de faconnage
 * (recettes {@code automation/}) ne sont pas recopiees : la ligne de l'etabli le rappelle.
 */
final class PartsCategory extends PiCategory<PartsCategory.Page> {

    static final RecipeType<Page> TYPE = JeiOrder.create("metal_parts", Page.class);
    /** Formes des pieces (materials/forms.py) ; metaux des etablis (industry/parts_p103.py BENCH_METALS). */
    static final Set<String> FORMS = Set.of("slab", "billet", "wire_rod", "gear_blank", "block", "plate", "sheet", "foil", "rod", "wire",
          "ring", "spring", "gear", "pipe", "wire_mesh", "wire_rope");
    static final Set<String> BENCH_METALS = Set.of("iron", "copper", "bronze", "tin", "brass", "zinc", "lead", "gold", "solder", "steel");
    private static final List<String> ORDER = List.of("slab", "billet", "wire_rod", "gear_blank", "block", "plate", "sheet", "foil", "rod",
          "wire", "ring", "spring", "gear", "pipe", "wire_mesh", "wire_rope");

    /** Une voie : poste (icone + nom), entrees (piles, outils compris), pieces produites, tier (-1 : sans tier), rendement au MKII. */
    record Row(ItemLike station, Component name, List<List<ItemStack>> inputs, int count, int tier, double yieldMk2, Component extra,
               int order) {
    }

    /** Une page : la piece et jusqu'a {@link #ROWS} voies (une piece a plus de voies : pages suivantes). */
    record Page(ItemStack part, String metal, List<Row> rows, int total) {
    }

    private static final int ROWS = 6, ROW_Y = 21, STEP = 19, IN_X = 22, COUNT_X = 102, WIDTH = 176;

    PartsCategory(IGuiHelper helper) {
        super(helper, TYPE, "jei.pioneers.parts", PiBronze.BENCHES.get(BenchType.COMPONENTS).getBlock(), WIDTH, ROW_Y + ROWS * STEP);
        family(JeiDraw.Family.BENCH);
        footerLines(1);
    }

    // ------------------------------------------------------------------ donnees

    static List<Page> recipes(Level level) {
        Map<Item, String> parts = new LinkedHashMap<>();
        List<GeneratedMaterials.Entry> entries = new ArrayList<>(GeneratedMaterials.ENTRIES);
        entries.sort(Comparator.comparing((GeneratedMaterials.Entry e) -> !BENCH_METALS.contains(e.material()))
              .thenComparing(GeneratedMaterials.Entry::material).thenComparingInt(e -> ORDER.indexOf(e.form())));
        for (GeneratedMaterials.Entry e : entries) {
            if (!FORMS.contains(e.form())) {
                continue;
            }
            ResourceLocation id = e.id().contains(":") ? new ResourceLocation(e.id()) : Pioneers.rl(e.id());
            Item it = BuiltInRegistries.ITEM.get(id);
            if (it != null && BuiltInRegistries.ITEM.getKey(it).equals(id)) {
                parts.put(it, e.material());
            }
        }
        Map<Item, List<Row>> rows = new LinkedHashMap<>();
        parts.keySet().forEach(it -> rows.put(it, new ArrayList<>()));
        Map<Item, Set<String>> seen = new LinkedHashMap<>();
        ItemLike bench = PiBronze.BENCHES.get(BenchType.COMPONENTS).getBlock();
        Component twin = Component.translatable("jei.pioneers.parts.bench");
        for (BenchRecipe r : BenchTile.recipes(level, BenchType.COMPONENTS)) {
            List<List<ItemStack>> ins = new ArrayList<>();
            r.tools().forEach(t -> ins.add(List.of(t.getItems())));
            r.ingredients().forEach(c -> ins.add(stacks(c.ingredient(), c.count())));
            add(rows, seen, r.result(), new Row(bench, bench.asItem().getDescription(), ins, r.result().getCount(), -1, 0, twin, 0));
        }
        for (CraftingRecipe r : level.getRecipeManager().getAllRecipesFor(net.minecraft.world.item.crafting.RecipeType.CRAFTING)) {
            ItemStack out = r.getResultItem(level.registryAccess());
            if (!rows.containsKey(out.getItem()) || !"block".equals(formOf(out.getItem()))) {
                continue;
            }
            Map<String, List<ItemStack>> grouped = new LinkedHashMap<>();
            Map<String, Integer> counts = new LinkedHashMap<>();
            for (Ingredient in : r.getIngredients()) {
                if (in.isEmpty() || in.getItems().length == 0) {
                    continue;
                }
                String k = BuiltInRegistries.ITEM.getKey(in.getItems()[0].getItem()).toString();
                grouped.putIfAbsent(k, List.of(in.getItems()));
                counts.merge(k, 1, Integer::sum);
            }
            List<List<ItemStack>> ins = new ArrayList<>();
            grouped.forEach((k, st) -> ins.add(st.stream().map(s -> s.copyWithCount(counts.get(k))).toList()));
            add(rows, seen, out, new Row(net.minecraft.world.level.block.Blocks.CRAFTING_TABLE, Component.translatable("block.minecraft.crafting_table"),
                  ins, out.getCount(), -1, 0, null, 1));
        }
        ItemLike lathe = PiKinetics.machine(MachineType.LATHE);
        for (MechanicalRecipe r : level.getRecipeManager().getAllRecipesFor(PiRecipes.MECHANICAL.get(MechanicalRecipe.Kind.TURNING).get())) {
            if (r.results().isEmpty()) {
                continue;
            }
            ItemStack out = r.results().get(0).stack();
            add(rows, seen, out, new Row(lathe, lathe.asItem().getDescription(), List.of(stacks(r.input(), r.count())), out.getCount(), -1, 0,
                  null, 2));
        }
        for (IndustryMachine m : IndustryMachine.values()) {
            for (IndustrialRecipe r : IndustryRecipes.withoutLow(IndustryRecipes.of(level, m))) {   // 1.0.5-beta (lot 38) : sans les converties
                if (r.results().isEmpty() || r.getId().getPath().startsWith("automation/")) {
                    continue;
                }
                ItemStack out = r.results().get(0).stack();
                if (!rows.containsKey(out.getItem())) {
                    continue;
                }
                List<List<ItemStack>> ins = new ArrayList<>();
                for (Counted c : r.items()) {
                    ins.add(stacks(c.ingredient(), c.count()));
                }
                Component extra = null;
                if (!r.fluids().isEmpty() && !r.fluids().get(0).displayed().isEmpty()) {
                    var f = r.fluids().get(0).displayed().get(0);
                    extra = Component.literal("+ " + r.fluids().get(0).amount() + " mB ").append(f.getDisplayName());
                } else if (!r.gases().isEmpty()) {
                    extra = Component.literal("+ " + r.gases().get(0).amount() + " mB ").append(r.gases().get(0).stack().getGas().getName());
                }
                double y = r.hasTierYield() ? r.tierYield(1) : 0;
                ItemLike icon = IndustryCategory.icon(m);
                add(rows, seen, out, new Row(icon, Component.translatable(m.translationKey()), ins, out.getCount(), r.tier(), y,
                      extra, 10 + r.tier() * 1000 + m.ordinal()));
            }
        }
        List<Page> out = new ArrayList<>();
        rows.forEach((it, list) -> {
            if (list.isEmpty()) {
                return;
            }
            list.sort(Comparator.comparingInt(Row::order));
            for (int i = 0; i < list.size(); i += ROWS) {
                out.add(new Page(new ItemStack(it), parts.get(it), List.copyOf(list.subList(i, Math.min(list.size(), i + ROWS))), list.size()));
            }
        });
        return out;
    }

    private static String formOf(Item it) {
        String id = BuiltInRegistries.ITEM.getKey(it).getPath();
        for (GeneratedMaterials.Entry e : GeneratedMaterials.ENTRIES) {
            if (e.id().equals(id)) {
                return e.form();
            }
        }
        return "";
    }

    private static List<ItemStack> stacks(Ingredient in, int count) {
        List<ItemStack> out = new ArrayList<>();
        for (ItemStack st : in.getItems()) {
            out.add(st.copyWithCount(Math.max(1, count)));
        }
        return out;
    }

    /** Une voie par poste et par entrees (les variantes de refroidissement de la coulee : la premiere seulement). */
    private static void add(Map<Item, List<Row>> rows, Map<Item, Set<String>> seen, ItemStack out, Row row) {
        List<Row> list = rows.get(out.getItem());
        if (list == null) {
            return;
        }
        StringBuilder key = new StringBuilder(BuiltInRegistries.ITEM.getKey(row.station().asItem()).toString());
        for (List<ItemStack> in : row.inputs()) {
            key.append('|').append(in.isEmpty() ? "" : BuiltInRegistries.ITEM.getKey(in.get(0).getItem()) + "*" + in.get(0).getCount());
        }
        if (seen.computeIfAbsent(out.getItem(), k -> new HashSet<>()).add(key.toString())) {
            list.add(row);
        }
    }

    // ------------------------------------------------------------------ page

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, @NotNull Page page, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.OUTPUT, 1, 1).setStandardSlotBackground().addItemStack(page.part());
        for (int i = 0; i < page.rows().size(); i++) {
            Row r = page.rows().get(i);
            int y = ROW_Y + i * STEP;
            builder.addSlot(RecipeIngredientRole.CATALYST, 1, y).setStandardSlotBackground().addItemStack(new ItemStack(r.station()));
            for (int k = 0; k < r.inputs().size() && k < 4; k++) {
                if (!r.inputs().get(k).isEmpty()) {
                    builder.addSlot(RecipeIngredientRole.INPUT, IN_X + k * 19, y).setStandardSlotBackground().addItemStacks(r.inputs().get(k));
                }
            }
        }
    }

    @Override
    protected void drawContent(@NotNull Page page, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        fitted(graphics, page.part().getHoverName(), 22, 2, 1F, TEXT, WIDTH - 24);
        fitted(graphics, Component.translatable("jei.pioneers.parts.ways", page.total()), 22, 11, SMALL, TEXT_LIGHT, WIDTH - 24);
        for (int i = 0; i < page.rows().size(); i++) {
            Row r = page.rows().get(i);
            int y = ROW_Y + i * STEP;
            text(graphics, Component.literal("→ " + r.count()), COUNT_X, y + 1, 1F, TEXT);
            //poste sans tier (etabli, grille, tour a bois) : son nom ; machine : son tier puis son nom (l'icone d'un atelier ne suffit pas)
            if (r.tier() < 0) {
                fitted(graphics, r.name(), COUNT_X + 26, y + 1, SMALL, TEXT_LIGHT, WIDTH - COUNT_X - 27);
            } else {
                Component tier = Component.translatable("industry.pioneers.tier." + Tiers.tier(r.tier()).id());
                fitted(graphics, tier, COUNT_X + 26, y + 1, SMALL, GOOD, WIDTH - COUNT_X - 27);
                fitted(graphics, r.name(), COUNT_X + 26, y + 7, SMALL, TEXT_LIGHT, WIDTH - COUNT_X - 27);
            }
            if (r.yieldMk2() > 1) {
                fitted(graphics, Component.translatable("jei.pioneers.parts.yield",
                      String.format(Locale.ROOT, "%.0f", Math.floor(r.count() * r.yieldMk2() + 1e-6))), COUNT_X, y + 13.5F, 0.6F, WARN, WIDTH - COUNT_X);
            } else if (r.extra() != null && r.tier() >= 0) {
                fitted(graphics, r.extra(), COUNT_X, y + 13.5F, 0.6F, TEXT_LIGHT, WIDTH - COUNT_X);
            }
        }
    }

    @Override
    protected List<Component> contentTooltip(@NotNull Page page, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
        for (int i = 0; i < page.rows().size(); i++) {
            int y = ROW_Y + i * STEP;
            if (mouseX >= COUNT_X && mouseY >= y && mouseY < y + STEP) {
                Row r = page.rows().get(i);
                List<Component> out = new ArrayList<>(List.of(r.name()));
                if (r.extra() != null) {
                    out.add(r.extra());
                }
                return out;
            }
        }
        return List.of();
    }

    @Override
    protected Component headerTitle(Page page) {
        return Component.translatable("jei.pioneers.parts");
    }

    @Override
    protected List<JeiDraw.Chip> chips(Page page) {
        boolean hand = BENCH_METALS.contains(page.metal());
        return List.of(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable(hand ? "jei.pioneers.parts.bench" : "jei.pioneers.parts.machines"),
              hand ? GOOD : TEXT_LIGHT, Component.translatable(hand ? "jei.pioneers.parts.hand_only" : "jei.pioneers.parts.machine_only")));
    }

    /** Catalyseurs : l'etabli de composants de base, l'extrudeuse et la presse electrique (MKI). */
    static List<ItemLike> catalysts() {
        return List.of(PiBronze.BENCHES.get(BenchType.COMPONENTS).getBlock(), IndustryCategory.icon(IndustryMachine.EXTRUDER),
              IndustryCategory.icon(IndustryMachine.PRESS));
    }
}
