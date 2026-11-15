package fr.pioneers.integration.jei;

import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.registries.PiBaseMachines;
import fr.pioneers.registries.PiItems;
import fr.pioneers.registries.PiRobotics;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.giant.PocketKind.Layer;
import fr.pioneers.space.giant.PocketKind;
import fr.pioneers.space.research.ResearchNode;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.gui.widgets.IRecipeExtrasBuilder;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocusGroup;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.RecipeType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * Recolte des geantes gazeuses : pour chaque planete et chaque couche, ce que contiennent les poches (avec leur chance) et ce qu'un drone
 * en rapporte par voyage. Dans la couche des orages, il faut le blindage.
 */
class DroneHarvestCategory extends PiCategory<DroneHarvestCategory.Entry> {

    /** Une couche d'une geante : contenus possibles et leur probabilite. */
    record Entry(Body body, Layer layer, List<Chance> kinds) {

        boolean storm() {
            return layer == Layer.STORM;
        }
    }

    record Chance(PocketKind kind, float chance) {
    }

    private static final int WIDTH = 160, ROW_Y = 12, ARROW_X = 42, OUT_X = 70, NOTES_Y = 44;

    DroneHarvestCategory(IGuiHelper helper, RecipeType<Entry> type) {
        super(helper, type, "jei.pioneers.drone_harvest", PiBaseMachines.DRONE_BAY, WIDTH, NOTES_Y + 4 * LINE);
        family(JeiDraw.Family.SPACE);
    }

    /** Toutes les couches des quatre geantes, chances calculees a partir du tirage des poches. */
    static List<Entry> entries() {
        List<Entry> entries = new ArrayList<>();
        for (Body body : Body.ALL) {
            if (!body.isGasGiant()) {
                continue;
            }
            for (Layer layer : Layer.values()) {
                Map<PocketKind, Integer> counts = new EnumMap<>(PocketKind.class);
                int steps = 1000;
                for (int i = 0; i < steps; i++) {
                    counts.merge(PocketKind.roll(body, layer, (i + 0.5F) / steps), 1, Integer::sum);
                }
                List<Chance> kinds = new ArrayList<>();
                counts.entrySet().stream().sorted((a, b) -> b.getValue() - a.getValue())
                      .forEach(e -> kinds.add(new Chance(e.getKey(), e.getValue() / (float) steps)));
                entries.add(new Entry(body, layer, kinds));
            }
        }
        return entries;
    }

    /** Pas entre deux sorties : 22 px, resserre (sans descendre sous 18) pour que toutes tiennent dans la largeur. */
    private static int step(Entry entry) {
        int n = entry.kinds().size();
        return n <= 1 ? 22 : Math.max(18, Math.min(22, (WIDTH - OUT_X - 18) / (n - 1)));
    }

    @Override
    protected void setContent(@NotNull IRecipeLayoutBuilder builder, Entry entry, @NotNull IFocusGroup focuses) {
        builder.addSlot(RecipeIngredientRole.CATALYST, 1, ROW_Y + 1).setStandardSlotBackground()
              .addItemStacks(PiRobotics.UNITS.get(UnitKind.DRONE).values().stream().map(unit -> new ItemStack(unit.get())).toList());
        if (entry.storm()) {
            builder.addSlot(RecipeIngredientRole.CATALYST, 21, ROW_Y + 1).setStandardSlotBackground()
                  .addItemStack(new ItemStack(PiItems.DRONE_ARMOR_PLATING.get()));
        }
        boolean armored = entry.storm();
        for (int i = 0; i < entry.kinds().size(); i++) {
            PocketKind kind = entry.kinds().get(i).kind();
            int x = OUT_X + i * step(entry);
            int amount = kind.tripCapacity(armored);
            Gas shown = kind.isGas() ? kind.gas() : null;
            if (shown != null && !shown.isEmptyType()) {
                gas(builder, RecipeIngredientRole.OUTPUT, x, ROW_Y, 18, 18, GasStack.of(shown, amount), amount);
            } else if (kind.isGas()) {
                continue;
            } else {
                builder.addSlot(RecipeIngredientRole.OUTPUT, x + 1, ROW_Y + 1).setStandardSlotBackground().addItemStack(new ItemStack(kind.item(), amount));
            }
        }
    }

    @Override
    protected void extras(@NotNull IRecipeExtrasBuilder builder, @NotNull Entry entry, @NotNull IFocusGroup focuses) {
        builder.addAnimatedRecipeArrow(100).setPosition(ARROW_X, ROW_Y + 1);
    }

    @Override
    protected void drawContent(@NotNull Entry entry, @NotNull IRecipeSlotsView view, @NotNull GuiGraphics graphics, double mouseX, double mouseY) {
        Component title = Component.translatable("jei.pioneers.drone_harvest.title", Component.translatable(entry.body().getTranslationKey()),
              Component.translatable(entry.layer().getTranslationKey()), entry.layer().minY, entry.layer().maxY);
        fitted(graphics, title, 0, 1, SMALL, TEXT, WIDTH);
        for (int i = 0; i < entry.kinds().size(); i++) {
            PocketKind kind = entry.kinds().get(i).kind();
            if (kind.isGas() && (kind.gas() == null || kind.gas().isEmptyType())) {
                //Pas de jauge pour ce contenu (voir setRecipe) : pas de pourcentage orphelin
                continue;
            }
            int percent = Math.round(entry.kinds().get(i).chance() * 100);
            centeredFitted(graphics, Component.literal(percent + " %"), OUT_X + i * step(entry) + 9, ROW_Y + 21, 0.6F, TEXT_LIGHT, 18);
        }
        float y = block(graphics, List.of(new Note(Component.translatable("jei.pioneers.drone_harvest.trip"), TEXT),
              new Note(Component.translatable(entry.storm() ? "jei.pioneers.drone_harvest.storm" : "jei.pioneers.drone_harvest.rich", entry.storm() ? 15 : 5),
                    entry.storm() ? WARN : TEXT_LIGHT)), 0, NOTES_Y, 0.6F, WIDTH, ch() - LINE - 1);
        research(graphics, entry.storm() ? ResearchNode.STORM_DRONES : ResearchNode.GAS_HARVESTING, Math.min((int) y + 1, ch() - LINE), WIDTH);
    }
}
