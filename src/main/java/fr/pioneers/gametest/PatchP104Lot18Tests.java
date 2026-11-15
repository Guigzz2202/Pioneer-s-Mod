package fr.pioneers.gametest;

import fr.pioneers.api.gas.GasStack;
import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.atelier.AtelierTile;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 1.0.4-beta (lot 18, plan « montee en difficulte lineaire » L4 et L5) : GameTests des LIGNEES MECANIQUES et des MACHINES SELON LEUR
 * COMPLEXITE (recettes : T:components/refonte/plans.py pour les composants, T:balance/difficulte_data.py pour les machines statiques).
 * <ul>
 *     <li>moteurs, roulements, trains, verins, pompes, rotors, echangeurs : la version MKn contient la MKn-1 (une pour une) ;</li>
 *     <li>capteur inductif sur bobine emaillee, sonde a oxygene avec un joint en PTFE ;</li>
 *     <li>matrice de presse et foret de base en acier (la voie au vanadium, a l'assembleur, rend plus) ; trefileuse et extrudeuse MKI sans
 *     acier au vanadium ni nickel ; cristalliseur a echangeur ;</li>
 *     <li>machines complexes faites des machines simples de leur domaine (cascade, usine de semi-conducteurs MKII, cellule blindee MKIII,
 *     automate, centre de donnees, laboratoire d'IA, noyau d'usine, ordinateur quantique).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot18Tests {

    private PatchP104Lot18Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(id.contains(":") ? ResourceLocation.parse(id) : rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static IndustrialRecipe recipe(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof IndustrialRecipe, "recette industrielle absente : " + path);
        return (IndustrialRecipe) r;
    }

    private static BenchRecipe bench(GameTestHelper h, String path) {
        Recipe<?> r = h.getLevel().getRecipeManager().byKey(rl(path)).orElse(null);
        Gt.check(r instanceof BenchRecipe, "recette d'etabli absente : " + path);
        return (BenchRecipe) r;
    }

    /** Vrai si une entree d'objet de la recette accepte l'objet avec au moins n exemplaires. */
    private static boolean takes(List<BenchRecipe.Counted> ins, String id, int n) {
        ItemStack s = new ItemStack(item(id));
        for (BenchRecipe.Counted c : ins) {
            if (c.ingredient().test(s) && c.count() >= n) {
                return true;
            }
        }
        return false;
    }

    private static boolean takes(IndustrialRecipe r, String id, int n) {
        return takes(r.items(), id, n);
    }

    private static boolean takes(BenchRecipe r, String id, int n) {
        return takes(r.ingredients(), id, n);
    }

    /** La lignee : la recette de 'next' prend 'prev' autant de fois qu'elle rend de 'next' (une pour une). */
    private static void lineage(GameTestHelper h, String path, String prev, String next) {
        IndustrialRecipe r = recipe(h, path);
        Gt.check(r.results().get(0).stack().is(item(next)), path + " : resultat " + next);
        int n = r.results().get(0).stack().getCount();
        Gt.check(takes(r, prev, n), next + " : contient " + n + " " + prev + " (une pour une)");
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_18_motors", timeoutTicks = 20)
    public static void p104_18_lignee_des_moteurs(GameTestHelper h) {
        Gt.begin(h);
        lineage(h, "industry/component_assembler/rc_advanced_electric_motor", "electric_motor", "advanced_electric_motor");
        lineage(h, "industry/component_assembler/rc_elite_electric_motor", "advanced_electric_motor", "elite_electric_motor");
        lineage(h, "industry/component_assembler/rc_ultimate_electric_motor", "elite_electric_motor", "ultimate_electric_motor");
        for (String m : new String[]{"advanced", "elite"}) {
            IndustrialRecipe r = recipe(h, "industry/component_assembler/rc_" + m + "_electric_motor");
            Gt.check(!takes(r, "motor_stator", 1) && !takes(r, "motor_rotor", 1), m + " : stator et rotor viennent du moteur d'avant");
        }
        Gt.check(!takes(recipe(h, "industry/component_assembler/rc_ultimate_electric_motor"), "ceramic_composite_panel", 1),
              "moteur supraconducteur : la carcasse vient du moteur MK3");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_18_lineages", timeoutTicks = 20)
    public static void p104_18_lignees_mecaniques(GameTestHelper h) {
        Gt.begin(h);
        String[][] fam = {
            {"bearing", "advanced_bearing", "elite_bearing", "ultimate_bearing"},
            {"planetary_gear", "advanced_planetary_gear", "elite_planetary_gear", "ultimate_planetary_gear"},
            {"piston", "advanced_piston", "elite_piston", "ultimate_piston"},
            {"centrifugal_pump", "advanced_centrifugal_pump", "elite_centrifugal_pump", "ultimate_centrifugal_pump"},
            {"bladed_rotor", "advanced_bladed_rotor", "elite_bladed_rotor", "ultimate_bladed_rotor"},
            {"heat_exchanger", "advanced_heat_exchanger", "elite_heat_exchanger", "ultimate_heat_exchanger"},
        };
        for (String[] f : fam) {
            for (int i = 1; i < f.length; i++) {
                lineage(h, "industry/component_assembler/rc_" + f[i], f[i - 1], f[i]);
            }
        }
        Gt.check(!takes(recipe(h, "industry/component_assembler/rc_advanced_piston"), "piston_ring", 1), "verin hydraulique : segments du verin MK1");
        Gt.check(takes(recipe(h, "industry/component_assembler/rc_inductive_sensor"), "enameled_coil", 1), "capteur inductif : bobine emaillee");
        Gt.check(takes(recipe(h, "industry/component_assembler/rc_oxygen_sensors"), "ptfe_gasket", 1), "sonde a oxygene : joint en PTFE");
        h.succeed();
    }

    /** Compact de la machine au tier donne, recette verrouillee, entrees x2, energie pleine (comme le lot 17). */
    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine, CableTier tier, BlockPos rel, IndustrialRecipe r) {
        Block block = Gt.compact(machine, tier);
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier : " + machine.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(r.getId()), "recette " + r.getId() + " absente du selecteur de " + machine.key);
        for (int i = 0; i < r.items().size(); i++) {
            ItemStack[] stacks = r.items().get(i).ingredient().getItems();
            Gt.check(stacks.length > 0 && i < t.itemInputs().size(), machine.key + " : entree " + i + " de " + r.getId());
            t.itemInputs().get(i).setStack(stacks[0].copyWithCount(Math.min(64, r.items().get(i).count() * 2)));
        }
        for (int i = 0; i < r.fluids().size(); i++) {
            List<FluidStack> f = r.fluids().get(i).displayed();
            Gt.check(!f.isEmpty() && i < t.fluidInputs().size(), machine.key + " : pas de reservoir de fluide " + i + " pour " + r.getId());
            t.fluidInputs().get(i).setFluid(new FluidStack(f.get(0).getFluid(), r.fluids().get(i).amount() * 2));
        }
        for (int i = 0; i < r.gases().size(); i++) {
            GasStack g = r.gases().get(i).stack();
            Gt.check(!g.isEmpty() && i < t.gasInputs().size(), machine.key + " : pas de reservoir de gaz pour " + r.getId());
            t.gasInputs().get(i).setStack(GasStack.of(g.getGas(), g.getAmount() * 2));
        }
        return t;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_18_motor_run", timeoutTicks = 800)
    public static void p104_18_moteur_asynchrone_a_l_assembleur(GameTestHelper h) {
        Gt.begin(h);
        IndustrialRecipe r = recipe(h, "industry/component_assembler/rc_advanced_electric_motor");
        Gt.equal(0, r.tier(), "moteur asynchrone : Assembleur MK1");
        AtelierTile t = atelier(h, IndustryMachine.ASSEMBLER, CableTier.BASIC, new BlockPos(2, 2, 2), r);
        Item out = item("advanced_electric_motor");
        h.succeedWhen(() -> {
            t.getEnergy().setStored(t.getEnergy().getCapacity());
            int n = 0;
            for (ItemSlot s : t.itemOutputs()) {
                if (s.getStack().is(out)) {
                    n += s.getStack().getCount();
                }
            }
            Gt.check(n > 0, "moteur asynchrone : rien produit encore (moteur MK1 rebobine)");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_18_simple", timeoutTicks = 20)
    public static void p104_18_machines_simples_du_mk1(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe die = bench(h, "components/bench/rc_press_die_bench");
        Gt.check(!takes(die, "vanadium_steel_plate", 1), "matrice de presse de base : sans acier au vanadium");
        BenchRecipe drill = bench(h, "components/bench/rc_drill_bit_bench");
        Gt.check(!takes(drill, "vanadium_steel_rod", 1) && takes(drill, "steel_rod", 1), "foret de base : tige d'acier");
        IndustrialRecipe dies = recipe(h, "industry/component_assembler/rc_press_dies");
        Gt.check(takes(dies, "vanadium_steel_plate", 1) && dies.results().get(0).stack().getCount() > die.result().getCount(),
              "matrice de presse au vanadium : voie superieure, meilleur rendement");
        IndustrialRecipe bits = recipe(h, "industry/component_assembler/rc_drill_bits");
        Gt.check(bits.results().get(0).stack().getCount() > drill.result().getCount(), "foret au vanadium : meilleur rendement");
        BenchRecipe wm = bench(h, "industry/crafting/basic_wiremill");
        Gt.check(!takes(wm, "vanadium_steel_ring", 1) && takes(wm, "steel_ring", 1), "trefileuse MKI : filiere en acier");
        BenchRecipe ex = bench(h, "industry/crafting/basic_extruder");
        Gt.check(!takes(ex, "nickel_ring", 1) && takes(ex, "steel_ring", 1), "extrudeuse MKI : bague en acier");
        BenchRecipe cr = bench(h, "industry/crafting/basic_crystallizer");
        Gt.check(!takes(cr, "cathode_ray_tube", 1) && takes(cr, "heat_exchanger", 1), "cristalliseur MKI : echangeur a tubes");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_18_complex", timeoutTicks = 20)
    public static void p104_18_machines_complexes(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe c = bench(h, "industry/crafting/centrifuge_cascade");
        Gt.check(takes(c, "elite_centrifuge", 2) && takes(c, "elite_electric_motor", 1) && !takes(c, "bearing", 1),
              "cascade : 2 centrifugeuses MKIII + moteur MK3");
        BenchRecipe fab = bench(h, "industry/crafting/semiconductor_fab");
        Gt.check(takes(fab, "machine_hull_advanced", 1) && takes(fab, "advanced_photolithograph", 1) && takes(fab, "basic_pick_and_place", 1),
              "usine de semi-conducteurs : machine de base MKII + graveur MKII + placeuse");
        BenchRecipe hot = bench(h, "industry/crafting/industrial_hot_cell");
        Gt.check(takes(hot, "machine_hull_elite", 1) && takes(hot, "servo_motor", 2), "cellule blindee : machine de base MKIII + servomoteurs");
        Gt.check(takes(bench(h, "computing/plc"), "relay_cabinet", 1), "automate : armoire a relais");
        Gt.check(takes(bench(h, "industry/crafting/data_center"), "computer", 2), "centre de donnees : 2 ordinateurs");
        Gt.check(takes(bench(h, "compute/ai_lab"), "computer", 1), "laboratoire d'IA : un ordinateur");
        Gt.check(takes(bench(h, "compute/factory_ai_core"), "ai_lab", 1), "noyau d'usine : un laboratoire d'IA");
        Gt.check(takes(bench(h, "industry/crafting/quantum_computer"), "computer", 1), "ordinateur quantique : ordinateur de pilotage");
        h.succeed();
    }
}
