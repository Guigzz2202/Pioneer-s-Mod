package fr.pioneers.integration.jei;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.PiKitchen;
import fr.pioneers.farm.kitchen.HoneyFill;
import fr.pioneers.farm.kitchen.KitchenRecipe;
import fr.pioneers.farm.kitchen.Station;
import fr.pioneers.farm.kitchen.StationBlock;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.forge.ForgeTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import mezz.jei.api.registration.IRecipeCatalystRegistration;
import mezz.jei.api.registration.IRecipeCategoryRegistration;
import mezz.jei.api.registration.IRecipeRegistration;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeManager;
import net.minecraftforge.fluids.FluidStack;
import org.jetbrains.annotations.NotNull;

/**
 * Recettes des postes de cuisine et de transformation (une categorie par poste) : ingredients comptes (sans ordre), liquide d'entree, fleche,
 * produits (chance) et liquide produit ; pied de page : duree ou nombre de gestes, chaleur requise, fumage, nom d'origine garde.
 */
class KitchenCategory extends PiCategory<KitchenRecipe> {

    static final Map<Station, RecipeType<KitchenRecipe>> TYPES = new EnumMap<>(Station.class);

    static {
        for (Station s : Station.values()) {
            TYPES.put(s, JeiOrder.create("kitchen_" + s.id(), KitchenRecipe.class));
        }
    }

    private static final int STEP = 19, FLUID_X = 60, ARROW_X = 80, OUT_X = 108, H = 44;
    /**
     * 1.0.5-beta (lot 32, retour 41) : page de l'EXTRACTEUR DE MIEL : cadre plein -> les 3 produits (cire, rayon avec sa chance, cadre vide) et
     * le miel de la cuve, puis la rangee de la case « Remplir » : fiole vide -> bouteille de miel, seau -> seau de miel ({@code HoneyFill}).
     */
    static final int EX_H = 66, EX_ARROW_X = 26, EX_OUT_X = 56, EX_FLUID_X = 116, EX_ROW2_Y = 46, EX_BOTTLE_X = 1, EX_HONEY_BOTTLE_X = 49,
          EX_BUCKET_X = 79, EX_HONEY_BUCKET_X = 127, EX_SMALL_ARROW = 22;
    private final Station station;

    KitchenCategory(IGuiHelper helper, Station station) {
        super(helper, TYPES.get(station), "jei.pioneers.kitchen." + station.id(), PiKitchen.STATIONS.get(icon(station)).get(), 170,
              station == Station.EXTRACTING ? EX_H : H);
        this.station = station;
        family(JeiDraw.Family.KITCHEN);
    }

    /** Poste principal d'une recette. */
    static StationBlock.Kind icon(Station s) {
        for (StationBlock.Kind k : StationBlock.Kind.values()) {
            if (k.station() == s) {
                return k;
            }
        }
        return StationBlock.Kind.CUTTING_BOARD;
    }

    static boolean heated(Station s) {
        return s == Station.COOKING || s == Station.FRYING || s == Station.BREWING || s == Station.DISTILLING;
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, KitchenRecipe recipe, @NotNull IFocusGroup focuses) {
        if (station == Station.EXTRACTING) {
            extracting(builder, recipe);
            return;
        }
        List<KitchenRecipe.Counted> ins = recipe.ingredients();
        int n = Math.min(6, ins.size());
        int cols = n <= 3 ? n : 3, rows = (n + 2) / 3;
        int y0 = (H - rows * STEP) / 2 + 1;
        for (int i = 0; i < n; i++) {
            KitchenRecipe.Counted c = ins.get(i);
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack s : c.ingredient().getItems()) {
                stacks.add(s.copyWithCount(c.count()));
            }
            builder.addSlot(RecipeIngredientRole.INPUT, 1 + (i % 3) * STEP, y0 + (i / 3) * STEP).setStandardSlotBackground().addItemStacks(stacks);
        }
        if (recipe.fluid() != null) {
            builder.addSlot(RecipeIngredientRole.INPUT, FLUID_X + 1, 4).setFluidRenderer(Math.max(1000, recipe.fluid().amount()), false, 12, 34)
                  .addIngredients(ForgeTypes.FLUID_STACK, recipe.fluid().stacks()).setBackground(new Frame(14, 36), -1, -1);
        }
        int k = 0;
        for (KitchenRecipe.Result r : recipe.results()) {
            if (k >= 3) {
                break;
            }
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + k * STEP, (H - 18) / 2 + 1).setStandardSlotBackground()
                  .addItemStack(r.stack());
            if (r.chance() < 1) {
                float ch = r.chance();
                chance(slot, ch);
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.mechanical.chance",
                      String.format(Locale.ROOT, "%.0f", ch * 100)).withStyle(ChatFormatting.GOLD)));
            }
            k++;
        }
        FluidStack out = recipe.fluidResult();
        if (!out.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, OUT_X + k * STEP + 1, 4).setFluidRenderer(Math.max(1000, out.getAmount()), false, 12, 34)
                  .addIngredient(ForgeTypes.FLUID_STACK, out).setBackground(new Frame(14, 36), -1, -1);
        }
    }

    /** 1.0.5-beta (lot 32) : page de l'extracteur de miel (voir {@link #EX_H}). */
    private void extracting(IRecipeLayoutBuilder builder, KitchenRecipe recipe) {
        List<KitchenRecipe.Counted> ins = recipe.ingredients();
        if (!ins.isEmpty()) {
            KitchenRecipe.Counted c = ins.get(0);
            List<ItemStack> stacks = new ArrayList<>();
            for (ItemStack s : c.ingredient().getItems()) {
                stacks.add(s.copyWithCount(c.count()));
            }
            builder.addSlot(RecipeIngredientRole.INPUT, 1, 10).setStandardSlotBackground().addItemStacks(stacks);
        }
        int k = 0;
        for (KitchenRecipe.Result r : recipe.results()) {
            if (k >= 3) {
                break;
            }
            IRecipeSlotBuilder slot = builder.addSlot(RecipeIngredientRole.OUTPUT, EX_OUT_X + k * STEP, 10).setStandardSlotBackground()
                  .addItemStack(r.stack());
            if (r.chance() < 1) {
                float ch = r.chance();
                chance(slot, ch);
                slot.addRichTooltipCallback((view, tooltip) -> tooltip.add(Component.translatable("jei.pioneers.mechanical.chance",
                      String.format(Locale.ROOT, "%.0f", ch * 100)).withStyle(ChatFormatting.GOLD)));
            }
            k++;
        }
        FluidStack out = recipe.fluidResult();
        if (!out.isEmpty()) {
            builder.addSlot(RecipeIngredientRole.OUTPUT, EX_FLUID_X + 1, 1).setFluidRenderer(Math.max(1000, out.getAmount()), false, 12, 34)
                  .addIngredient(ForgeTypes.FLUID_STACK, out).setBackground(new Frame(14, 36), -1, -1);
            //Rangee « Remplir » : contenant vide -> plein (memes regles que la case des extracteurs : HoneyFill)
            ItemStack bottle = new ItemStack(Items.GLASS_BOTTLE), bucket = new ItemStack(Items.BUCKET);
            ItemStack honeyBottle = HoneyFill.filled(bottle, out), honeyBucket = HoneyFill.filled(bucket, out);
            if (!honeyBottle.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, EX_BOTTLE_X, EX_ROW2_Y).setStandardSlotBackground().addItemStack(bottle);
                builder.addSlot(RecipeIngredientRole.OUTPUT, EX_HONEY_BOTTLE_X, EX_ROW2_Y).setStandardSlotBackground().addItemStack(honeyBottle);
            }
            if (!honeyBucket.isEmpty()) {
                builder.addSlot(RecipeIngredientRole.INPUT, EX_BUCKET_X, EX_ROW2_Y).setStandardSlotBackground().addItemStack(bucket);
                builder.addSlot(RecipeIngredientRole.OUTPUT, EX_HONEY_BUCKET_X, EX_ROW2_Y).setStandardSlotBackground().addItemStack(honeyBucket);
            }
        }
    }

    /** 1.0.5-beta (lot 32) : quantites prises par contenant, au-dessus des fleches de la rangee « Remplir » (page de l'extracteur). */
    @Override
    protected void drawContent(@NotNull KitchenRecipe recipe, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX,
          double mouseY) {
        if (station != Station.EXTRACTING || recipe.fluidResult().isEmpty()) {
            return;
        }
        centered(graphics, Component.literal(HoneyFill.BOTTLE_MB + " mB"), EX_BOTTLE_X + EX_SMALL_ARROW + 12, EX_ROW2_Y - 5, 0.55F, TEXT_LIGHT);
        centered(graphics, Component.literal(HoneyFill.BUCKET_MB + " mB"), EX_BUCKET_X + EX_SMALL_ARROW + 12, EX_ROW2_Y - 5, 0.55F, TEXT_LIGHT);
    }

    @Override
    protected List<Component> contentTooltip(@NotNull KitchenRecipe recipe, @NotNull IRecipeSlotsView view, double mouseX, double mouseY) {
        if (station == Station.EXTRACTING && mouseY >= EX_ROW2_Y - 4 && mouseY < EX_ROW2_Y + 18 && mouseX < 150) {
            return List.of(Component.translatable("jei.pioneers.kitchen.bottling", HoneyFill.BOTTLE_MB, HoneyFill.BUCKET_MB),
                  Component.translatable("jei.pioneers.kitchen.container").withStyle(ChatFormatting.GRAY));
        }
        return List.of();
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull KitchenRecipe recipe, @NotNull IFocusGroup focuses) {
        if (station == Station.EXTRACTING) {
            builder.addRecipeArrow().setPosition(EX_ARROW_X, 10);
            if (!recipe.fluidResult().isEmpty()) {
                builder.addRecipeArrow().setPosition(EX_BOTTLE_X + EX_SMALL_ARROW, EX_ROW2_Y);
                builder.addRecipeArrow().setPosition(EX_BUCKET_X + EX_SMALL_ARROW, EX_ROW2_Y);
            }
            return;
        }
        //1.11.3 : recette a gestes : fleche immobile (le nombre de gestes est dans le pied de page), sinon la duree
        if (recipe.strokes() > 0 && station.manual) {
            builder.addRecipeArrow().setPosition(ARROW_X, (H - 17) / 2);
        } else {
            builder.addAnimatedRecipeArrow(Math.max(20, Math.min(400, recipe.time()))).setPosition(ARROW_X, (H - 17) / 2);
        }
    }

    /** 1.11.3 (charte) : gestes ou duree, chaleur, fumage / feu de camp, nom d'origine garde, en pastilles. */
    @Override
    protected List<JeiDraw.Chip> chips(KitchenRecipe recipe) {
        List<JeiDraw.Chip> out = new ArrayList<>();
        if (recipe.strokes() > 0 && station.manual) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.TURNS, Component.translatable("jei.pioneers.kitchen.strokes", recipe.strokes()), JeiDraw.LIGHT));
        } else {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.TIME, seconds(recipe.time()), JeiDraw.LIGHT,
                  Component.translatable("jei.pioneers.kitchen.time", seconds(recipe.time()))));
        }
        if (heated(station)) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.HEAT, Component.translatable("jei.pioneers.kitchen.heat"), JeiDraw.WARN));
        }
        if (recipe.smoke()) {
            //1.6 (lot A2) : au sechoir, « smoke » = recette qui exige le sechoir pose sur un feu de camp allume
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.HEAT, Component.translatable(station == Station.DRYING ? "jei.pioneers.kitchen.fire"
                  : "jei.pioneers.kitchen.smoke"), JeiDraw.WARN));
        } else if (station == Station.DRYING) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.HEAT, Component.translatable("jei.pioneers.kitchen.fire_faster"), JeiDraw.DIM));
        }
        if (recipe.source()) {
            out.add(JeiDraw.Chip.of(JeiDraw.Pic.INFO, Component.translatable("jei.pioneers.kitchen.source"), JeiDraw.DIM));
        }
        return out;
    }

    // ------------------------------------------------------------------ enregistrement

    static void categories(IRecipeCategoryRegistration registration, IGuiHelper helper) {
        for (Station s : Station.values()) {
            registration.addRecipeCategories(new KitchenCategory(helper, s));
        }
    }

    static void recipes(IRecipeRegistration registration, RecipeManager manager) {
        for (Station s : Station.values()) {
            registration.addRecipes(TYPES.get(s), manager.getAllRecipesFor(PiKitchen.TYPES.get(s).get()));
        }
    }

    static void catalysts(IRecipeCatalystRegistration registration) {
        for (StationBlock.Kind k : StationBlock.Kind.values()) {
            Station s = k.station();
            if (s != null) {
                registration.addRecipeCatalyst(new ItemStack(PiKitchen.STATIONS.get(k).get()), TYPES.get(s));
            }
        }
        ItemStack stove = new ItemStack(PiKitchen.STATIONS.get(StationBlock.Kind.KITCHEN_STOVE).get());
        for (Station s : Station.values()) {
            if (heated(s)) {
                registration.addRecipeCatalyst(stove, TYPES.get(s));
            }
        }
        //1.6 (lot A2) : le fumoir du mod rejoue les cuissons du fumoir de Minecraft (retire), sechoir sur un feu de camp
        registration.addRecipeCatalyst(new ItemStack(PiKitchen.STATIONS.get(StationBlock.Kind.SMOKEHOUSE).get()), RecipeTypes.SMOKING);
        registration.addRecipeCatalyst(new ItemStack(Items.CAMPFIRE), TYPES.get(Station.DRYING));
    }
}
