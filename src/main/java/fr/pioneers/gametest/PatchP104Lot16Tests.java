package fr.pioneers.gametest;

import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.bronze.bench.BenchTile;
import fr.pioneers.bronze.bench.BenchType;
import java.util.List;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 16, demande de Guillaume du 07/11/2026) : GameTests du TABLEAU DES TIERS, des MACHINES DE BASE MKI a MKIV et du CIRCUIT A
 * TUBES AVANCE (recettes : T:balance/hull_data.py, T:balance/r1_data.py, T:components/refonte/plans.py).
 * <ul>
 *     <li>chaque machine de base se fabrique a l'etabli de montage : chassis + circuit + plaques et tiges du metal secondaire du tier ;</li>
 *     <li>une machine MKI (broyeur compact, controleur du broyeur industriel) se fabrique avec la machine de base MKI, sans chassis ni vis ;</li>
 *     <li>le circuit a tubes se fait aux etablis avec du bronze, du cuivre et de l'acier : relais sans argent, resistance au charbon de bois,
 *     carte perforee en carton ;</li>
 *     <li>R1 : la machine MKI ne prend pas la courroie en caoutchouc (MKII) mais la courroie de cuir ; le dome de vapeur plus de laiton.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot16Tests {

    private static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};
    private static final String[] SECONDARY = {"brass", "electrum", "chrome", "osmiridium"};

    private PatchP104Lot16Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static ItemStack stack(String id) {
        Item item = ForgeRegistries.ITEMS.getValue(rl(id));
        Gt.check(item != null && item != Items.AIR, "objet absent : pioneers:" + id);
        return new ItemStack(item);
    }

    @Nullable
    private static BenchRecipe find(GameTestHelper h, BenchType bench, String id) {
        List<BenchRecipe> all = BenchTile.recipes(h.getLevel(), bench);
        for (BenchRecipe r : all) {
            if (r.getId().equals(rl(id))) {
                return r;
            }
        }
        return null;
    }

    /** Vrai si une entree de la recette accepte l'objet (quantite au moins n). */
    private static boolean takes(BenchRecipe r, String id, int n) {
        ItemStack s = stack(id);
        for (BenchRecipe.Counted c : r.ingredients()) {
            if (c.ingredient().test(s) && c.count() >= n) {
                return true;
            }
        }
        return false;
    }

    private static boolean takesAnyPrefix(BenchRecipe r, String prefix) {
        for (BenchRecipe.Counted c : r.ingredients()) {
            for (ItemStack s : c.ingredient().getItems()) {
                ResourceLocation k = ForgeRegistries.ITEMS.getKey(s.getItem());
                if (k != null && k.getPath().startsWith(prefix)) {
                    return true;
                }
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_16_hull", timeoutTicks = 40)
    public static void p104_16_machine_de_base_se_fabrique(GameTestHelper h) {
        Gt.begin(h);
        for (int t = 0; t < 4; t++) {
            String hull = "machine_hull_" + TIERS[t];
            BenchRecipe r = find(h, BenchType.ASSEMBLY, "machine_hulls/" + hull);
            Gt.check(r != null, hull + " : pas de recette a l'etabli de montage");
            Gt.check(r.result().is(stack(hull).getItem()), hull + " : resultat");
            Gt.check(takes(r, "machine_frame_" + TIERS[t], 1), hull + " : chassis du tier");
            Gt.check(takes(r, "circuit_mk" + (t + 1), 1), hull + " : circuit du tier");
            Gt.check(takes(r, SECONDARY[t] + "_plate", 2), hull + " : 2 plaques de " + SECONDARY[t]);
            Gt.check(takes(r, SECONDARY[t] + "_rod", 2), hull + " : 2 tiges de " + SECONDARY[t]);
            Gt.equal(4, r.ingredients().size(), hull + " : 4 entrees");
        }
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_16_mk1", timeoutTicks = 40)
    public static void p104_16_machine_mk1_avec_machine_de_base(GameTestHelper h) {
        Gt.begin(h);
        for (String id : new String[]{"machines/basic_crusher", "industry/crafting/basic_dynamo", "industry/crafting/industrial_crusher"}) {
            BenchRecipe r = find(h, BenchType.ASSEMBLY, id);
            Gt.check(r != null, id + " : pas de recette a l'etabli de montage");
            Gt.check(takes(r, "machine_hull_basic", 1), id + " : machine de base MKI");
            Gt.check(!takesAnyPrefix(r, "machine_frame_"), id + " : encore un chassis");
            Gt.check(!takes(r, "steel_screw", 1), id + " : encore des vis (la machine de base les porte)");
            Gt.check(r.ingredients().size() >= 3 && r.ingredients().size() <= 5, id + " : machine de base + 2 a 4 pieces");
        }
        BenchRecipe mk2 = find(h, BenchType.ASSEMBLY, "machines/advanced_crusher");
        Gt.check(mk2 != null && takes(mk2, "machine_hull_advanced", 1), "broyeur MKII : machine de base MKII");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_16_circuit", timeoutTicks = 40)
    public static void p104_16_circuit_a_tubes_a_l_etabli(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe relay = find(h, BenchType.REDSTONER, "components/bench/rc_relay_bench");
        Gt.check(relay != null, "relais : pas de recette a l'etabli du redstonier");
        Gt.check(takes(relay, "copper_foil", 1) && takes(relay, "porcelain_insulator", 1), "relais : feuille de cuivre et porcelaine");
        Gt.check(!takes(relay, "silver_foil", 1), "relais a l'etabli : encore de l'argent (machines MK1)");
        BenchRecipe res = find(h, BenchType.REDSTONER, "components/bench/rc_carbon_resistor_bench");
        Gt.check(res != null && takes(res, "charcoal", 1) && !takes(res, "coke", 1), "resistance au carbone : charbon de bois a l'etabli");
        BenchRecipe board = find(h, BenchType.CARPENTER, "tiers/perforated_board_paper");
        Gt.check(board != null && board.result().is(stack("perforated_board").getItem()), "carte perforee en carton : etabli du menuisier");
        BenchRecipe circuit = find(h, BenchType.ADVANCED_COMPONENTS, "components/bench/rc_circuit_mk1_bench");
        Gt.check(circuit != null && takes(circuit, "relay", 1) && takes(circuit, "perforated_board", 1), "circuit a tubes a l'etabli de composants avances");
        h.succeed();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_16_r1", timeoutTicks = 40)
    public static void p104_16_regle_r1_corrigee(GameTestHelper h) {
        Gt.begin(h);
        BenchRecipe comp = find(h, BenchType.ASSEMBLY, "machines/basic_compressor");
        Gt.check(comp != null && takes(comp, "leather_belt", 1) && !takes(comp, "rubber_belt", 1), "compacteur MKI : courroie de cuir (le caoutchouc est MK2)");
        BenchRecipe dome = find(h, BenchType.COPPERSMITH, "bronze/bench/steam_dome");
        Gt.check(dome != null && !takes(dome, "brass_plate", 1), "dome de vapeur : plus de laiton (MK1)");
        BenchRecipe valve = find(h, BenchType.COPPERSMITH, "bronze/bench/safety_valve");
        Gt.check(valve != null && takes(valve, "bronze_plate", 1) && !takes(valve, "brass_plate", 1), "surete de chaudiere : bronze (le laiton est MK1)");
        h.succeed();
    }
}
