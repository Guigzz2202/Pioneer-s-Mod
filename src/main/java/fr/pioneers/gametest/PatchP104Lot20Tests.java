package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.industry.recipe.IndustrialRecipe;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 20, plan « montee en difficulte lineaire », § 8) : GameTests des COMPACTS ETALES DANS LEUR AGE, des MACHINES COMPLEXES
 * MK3 / MK4 et de la regle « pas de composant avance independant ».
 * <ul>
 *     <li>machine SIMPLE au MKIII / MKIV : pieces de metier du tier d'avant + une piece du tier (sa commande de puissance) ;</li>
 *     <li>machines MOYENNES : une piece du milieu d'age (reacteur chimique MKII au zirconium, galvanoplastie a la pompe du tier) ;</li>
 *     <li>machines COMPLEXES : graveur et placeuse a la pompe a vide, placeuse MKIV a la carte mere photonique ; automate, ordinateur,
 *     piscine, cellule blindee, cascade, ordinateur quantique, noyau d'usine, tokamak faits de machines et de pieces de fin d'age ;</li>
 *     <li>composants corriges (R3) : condensateur electrolytique au joint, relais reed a la bobine emaillee, connecteur dore au connecteur
 *     en laiton, tete de lecture a l'inductance a ferrite.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot20Tests {

    private PatchP104Lot20Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(id.contains(":") ? ResourceLocation.parse(id) : rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static BenchRecipe bench(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof BenchRecipe, "recette d'etabli absente : " + path);
        return (BenchRecipe) r;
    }

    private static IndustrialRecipe machine(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof IndustrialRecipe, "recette industrielle absente : " + path);
        return (IndustrialRecipe) r;
    }

    private static boolean takes(BenchRecipe r, String id) {
        ItemStack s = new ItemStack(item(id));
        for (BenchRecipe.Counted c : r.ingredients()) {
            if (c.ingredient().test(s)) {
                return true;
            }
        }
        return false;
    }

    private static boolean takes(IndustrialRecipe r, String id) {
        ItemStack s = new ItemStack(item(id));
        List<BenchRecipe.Counted> ins = r.items();
        for (BenchRecipe.Counted c : ins) {
            if (c.ingredient().test(s)) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_20_simple", timeoutTicks = 20)
    public static void p104_20_compacts_simples(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe c3 = bench(h, "machines/elite_crusher");
        Gt.check(takes(c3, "machine_hull_elite") && takes(c3, "advanced_electric_motor") && takes(c3, "variable_frequency_drive")
              && !takes(c3, "elite_electric_motor") && !takes(c3, "elite_bearing"), "broyeur MKIII : pieces MK2 + variateur");
        BenchRecipe c4 = bench(h, "machines/ultimate_crusher");
        Gt.check(takes(c4, "elite_electric_motor") && takes(c4, "superconducting_transformer") && !takes(c4, "ultimate_electric_motor"),
                 "broyeur MKIV : pieces MK3 + transformateur supraconducteur");
        BenchRecipe b3 = bench(h, "industry/crafting/elite_bender");
        Gt.check(takes(b3, "advanced_piston") && takes(b3, "variable_frequency_drive") && !takes(b3, "elite_piston"), "cintreuse MKIII");
        BenchRecipe w4 = bench(h, "industry/crafting/ultimate_wiremill");
        Gt.check(takes(w4, "tungsten_ring") && takes(w4, "superconducting_transformer"), "trefileuse MKIV : filiere en tungstene gardee");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_20_medium", timeoutTicks = 20)
    public static void p104_20_compacts_moyens_et_complexes(GameTestHelper h) {
        Gt.begin(h);
        Gt.check(takes(bench(h, "industry/crafting/advanced_chemical_reactor"), "zirconium_plate"), "reacteur chimique MKII : revetement en zirconium");
        Gt.check(takes(bench(h, "industry/crafting/elite_chemical_bath"), "elite_centrifugal_pump"), "bain chimique MKIII : pompe MK3");
        Gt.check(takes(bench(h, "industry/crafting/advanced_electrolyzer"), "advanced_centrifugal_pump")
              && takes(bench(h, "industry/crafting/advanced_electrolyzer"), "constantan_ring"), "electrolyseur MKII : pompe, shunt garde");
        Gt.check(takes(bench(h, "industry/crafting/advanced_photolithograph"), "advanced_centrifugal_pump"), "graveur MKII : pompe a vide");
        BenchRecipe p4 = bench(h, "industry/crafting/ultimate_pick_and_place");
        Gt.check(takes(p4, "photonic_motherboard") && takes(p4, "ultimate_centrifugal_pump") && takes(p4, "gold_spring"), "placeuse MKIV");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_20_complex", timeoutTicks = 20)
    public static void p104_20_machines_complexes(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe plc = bench(h, "computing/plc");
        Gt.check(takes(plc, "relay_cabinet") && takes(plc, "absolute_encoder_servo") && takes(plc, "elite_electric_motor"), "automate");
        Gt.check(takes(bench(h, "computing/computer"), "plc"), "ordinateur : reprend l'automate");
        BenchRecipe pool = bench(h, "industry/crafting/spent_fuel_pool");
        Gt.check(takes(pool, "elite_centrifugal_pump") && takes(pool, "elite_heat_exchanger"), "piscine refroidie");
        BenchRecipe cell = bench(h, "industry/crafting/industrial_hot_cell");
        Gt.check(takes(cell, "servo_motor") && takes(cell, "sapphire_glass") && takes(cell, "elite_electric_motor"), "cellule blindee");
        BenchRecipe cas = bench(h, "industry/crafting/centrifuge_cascade");
        Gt.check(takes(cas, "elite_centrifuge") && takes(cas, "compressor_unit"), "cascade : groupe compresseur");
        BenchRecipe qc = bench(h, "industry/crafting/quantum_computer");
        Gt.check(takes(qc, "computer") && takes(qc, "cryostat") && takes(qc, "ultimate_centrifugal_pump"), "ordinateur quantique refroidi");
        BenchRecipe core = bench(h, "compute/factory_ai_core");
        Gt.check(takes(core, "ai_lab") && takes(core, "quantum_computer"), "noyau d'usine : laboratoire d'IA + ordinateur quantique");
        Gt.check(takes(bench(h, "industry/crafting/tokamak"), "ultimate_centrifugal_pump"), "tokamak : pompe a vide MK4");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_20_r3", timeoutTicks = 20)
    public static void p104_20_composants_non_independants(GameTestHelper h) {
        Gt.begin(h);
        Gt.check(takes(machine(h, "industry/component_assembler/rc_electrolytic_capacitors"), "gasket"), "condensateur electrolytique : joint");
        Gt.check(takes(machine(h, "industry/component_assembler/rc_reed_relays"), "enameled_coil"), "relais reed : bobine emaillee");
        Gt.check(takes(machine(h, "industry/component_assembler/rc_gold_connectors"), "brass_connector"), "connecteur dore : connecteur en laiton");
        Gt.check(takes(machine(h, "industry/pick_and_place/rc_heads_read_write"), "ferrite_inductor"), "tete de lecture : inductance a ferrite");
        h.succeed();
    }
}
