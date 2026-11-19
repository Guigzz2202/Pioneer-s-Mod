package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.giant.GasCollector;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.MkvCoreSlot;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import java.util.function.Function;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.Level;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.7 « Plus loin et au-dela », lot 7 (Jupiter et le collecteur de gaz) :
 * <ul>
 *     <li>la Cite des Nuages sur Jupiter, son port et ses recettes uniques ; la fabrication du collecteur de gaz demande la membrane de
 *     captage (Cite des Nuages), celle du coeur MKV chimique le catalyseur exotique ;</li>
 *     <li>le collecteur a un melange de gaz pour chaque geante (Jupiter, Saturne, Uranus, Neptune), aucun ailleurs ;</li>
 *     <li>pose en structure MKV dans une geante, il capte (hydrogene et gaz de la geante) ; hors d'une geante : rien ;</li>
 *     <li>le melangeur en structure MKV passe au MKV avec le coeur chimique, pas avec celui d'un autre domaine.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot7Tests {

    private PatchP107Lot7Tests() {
    }

    private static boolean craftNeeds(GameTestHelper h, String recipe, String item) {
        Recipe<?> craft = h.getLevel().getRecipeManager().byKey(new ResourceLocation("pioneers", recipe)).orElse(null);
        Gt.check(craft != null, "fabrication absente : " + recipe);
        for (var ing : craft.getIngredients()) {
            for (var st : ing.getItems()) {
                if (ForgeRegistries.ITEMS.getKey(st.getItem()).toString().equals(item)) {
                    return true;
                }
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_7_data", timeoutTicks = 20)
    public static void p107_7_cite_et_collecteur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int city = Megastructures.byBody(Body.JUPITER);
        Gt.check(city >= 0, "Cite des Nuages sur Jupiter");
        IndustryMachine port = IndustryMachine.byKey(Megastructures.port(city));
        Gt.check(port != null && IndustryRecipes.of(h.getLevel(), port).size() >= 3, "recettes de la Cite des Nuages");
        IndustryMachine collector = IndustryMachine.byKey(GasCollector.KEY);
        Gt.check(collector != null && PiIndustry.controller(collector) != null, "collecteur de gaz");
        Gt.check(collector.gasesOut == 3 && !collector.itemDrill(), "collecteur : trois reservoirs de gaz, aucune case d'objet");
        Gt.check(craftNeeds(h, "industry/crafting/gas_collector", "pioneers:capture_membrane"), "collecteur : membrane de captage exigee");
        Gt.check(craftNeeds(h, "au_dela/cores/chemistry_mkv_core", "pioneers:exotic_catalyst"), "coeur chimique : catalyseur exotique exige");
        for (Body giant : new Body[]{Body.JUPITER, Body.SATURN, Body.URANUS, Body.NEPTUNE}) {
            int row = GasCollector.row(giant);
            Gt.check(row >= 0, "collecteur : aucun melange pour " + giant);
            for (var gas : GasCollector.gases(row)) {
                Gt.check(gas != null && !gas.isEmptyType(), "collecteur : gaz inconnu pour " + giant);
            }
        }
        Gt.equal(-1, GasCollector.row(Body.EARTH), "collecteur : un melange sur la Terre");
        Gt.equal(-1, GasCollector.row(Body.MARS), "collecteur : un melange sur Mars");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_7_collect", timeoutTicks = 1200)
    public static void p107_7_collecteur_capte_dans_jupiter(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Function<Level, Body> old = Megastructures.bodyOf;
        s.onCleanup(() -> Megastructures.bodyOf = old);
        ControllerTile tile = PatchP107Lot4Tests.build(h, IndustryMachine.byKey(GasCollector.KEY), true);
        Megastructures.bodyOf = l -> Body.MARS;
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "collecteur : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            tile.getEnergy().setStored(tile.getEnergy().getCapacity());
            h.runAfterDelay(20, () -> {
                Gt.equal(ControllerTile.NO_DEPOSIT, MkvCoreSlot.status(tile), "hors d'une geante : rien a capter");
                Megastructures.bodyOf = l -> Body.JUPITER;
                h.onEachTick(() -> tile.getEnergy().setStored(tile.getEnergy().getCapacity()));
                h.succeedWhen(() -> {
                    var tanks = tile.gasOutputs();
                    Gt.check(tanks.size() >= 3, "collecteur : " + tanks.size() + " reservoir(s) de gaz");
                    GasStack hydrogen = tanks.get(0).getStack();
                    GasStack ammonia = tanks.get(1).getStack();
                    Gt.check(!hydrogen.isEmpty() && !ammonia.isEmpty(), "collecteur : rien capte dans Jupiter (etat " + MkvCoreSlot.status(tile) + ")");
                    Gt.check(hydrogen.getAmount() > ammonia.getAmount(), "collecteur : l'hydrogene domine dans Jupiter");
                    Megastructures.bodyOf = old;
                    s.done();
                });
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p107_7_core", timeoutTicks = 200)
    public static void p107_7_coeur_chimique_du_melangeur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ControllerTile tile = PatchP107Lot4Tests.build(h, IndustryMachine.byKey("mixer"), true);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "melangeur MKV : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            Gt.equal(3, tile.tier(), "structure MKV sans coeur : reste MKIV");
            Gt.check(tile.mkvCore.install(stack("pioneers:forming_mkv_core")), "pose du coeur de mise en forme");
            Gt.equal(3, tile.tier(), "coeur d'un autre domaine : reste MKIV");
            tile.mkvCore.take();
            Gt.check(tile.mkvCore.install(stack("pioneers:chemistry_mkv_core")), "pose du coeur chimique");
            Gt.equal(4, tile.tier(), "structure MKV et coeur chimique : MKV");
            s.done();
        });
    }

    private static ItemStack stack(String id) {
        return new ItemStack(ForgeRegistries.ITEMS.getValue(new ResourceLocation(id)));
    }
}
