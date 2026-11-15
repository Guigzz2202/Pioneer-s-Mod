package fr.pioneers.gametest;

import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.bronze.bench.BenchTile;
import fr.pioneers.bronze.bench.BenchType;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.registries.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Patch 1.0.5-beta, lot 34 (retours de jeu 4e serie, 4 : etablis manuels ; retour 48 « joint en cuir dans l'etabli du charpentier alors
 * qu'il s'agit de cuir » ; GameTests) :
 * <ul>
 *     <li>le JOINT EN CUIR se fabrique a l'etabli du TANNEUR (couteau + cuir tanne -> 2 joints, vraie fabrication par le menu de l'etabli)
 *     et n'est plus dans la liste du charpentier ;</li>
 *     <li>les recettes deplacees sont lues par le serveur a leur etabli d'arrivee : farine a l'etabli agricole, seau au chaudronnier,
 *     selle au tanneur, revolver au metallier (et plus a leur etabli d'avant).</li>
 * </ul>
 * Regles et deplacements : {@code tools/recettes/benches_rules.json} ; audit {@code tools/recettes/audit_benches.py}.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot34Tests {

    private PatchP105Lot34Tests() {
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(new ResourceLocation("pioneers", id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static int indexOf(List<BenchRecipe> recipes, String id) {
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).getId().equals(new ResourceLocation("pioneers", id))) {
                return i;
            }
        }
        return -1;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_34_gasket", timeoutTicks = 40)
    public static void p105_34_joint_en_cuir_au_tanneur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer player = s.player("gt34cuir", "Tanneuse", new BlockPos(2, 2, 2));
        BlockPos at = new BlockPos(4, 2, 4);
        h.setBlock(at, PiBronze.BENCHES.get(BenchType.TANNER).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(at) instanceof BenchTile, "pas d'etabli du tanneur");
        BenchTile bench = (BenchTile) h.getBlockEntity(at);
        Gt.equal(BenchType.TANNER, bench.type(), "sorte d'etabli");
        String id = "components/bench/rc_leather_gasket";
        Gt.check(indexOf(BenchTile.recipes(h.getLevel(), BenchType.CARPENTER), id) < 0, "le joint en cuir est encore au charpentier");
        List<BenchRecipe> recipes = BenchTile.recipes(h.getLevel(), BenchType.TANNER);
        int index = indexOf(recipes, id);
        Gt.check(index >= 0, "joint en cuir absent de l'etabli du tanneur");
        BenchRecipe r = recipes.get(index);
        Gt.check(r.result().is(item("leather_gasket")) && r.result().getCount() == 2, "le tanneur donne 2 joints en cuir");
        Gt.equal(1, r.tools().size(), "un seul outil (le couteau)");
        List<ItemSlot> slots = bench.guiSlots();
        slots.get(0).setStack(r.tools().get(0).getItems()[0].copy());
        slots.get(BenchType.TOOLS).setStack(new ItemStack(item("tanned_leather"), 3));
        bench.handleCustom(player, BenchTile.SELECT + index);
        bench.handleCustom(player, BenchTile.CRAFT);
        Gt.check(bench.outputSlot().getStack().is(item("leather_gasket")), "joint en cuir fabrique au tanneur");
        Gt.equal(2, bench.outputSlot().getCount(), "2 joints");
        Gt.equal(2, slots.get(BenchType.TOOLS).getCount(), "un cuir tanne consomme");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_34_moves", timeoutTicks = 20)
    public static void p105_34_recettes_a_leur_etabli(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        String[][] moves = {
            {"bronze/bench/flour", "MASON", "AGRICULTURAL"},
            {"vanilla/bucket", "METALWORKER", "COPPERSMITH"},
            {"vanilla/saddle", "TRANSPORTER", "TANNER"},
            {"hunt/revolver", "COMPONENTS", "METALWORKER"},
            {"bronze/bench/canvas", "CARPENTER", "TANNER"},
            {"components/bench/rc_glass_lens_bench", "MASON", "POTTER"},
        };
        for (String[] m : moves) {
            Gt.check(indexOf(BenchTile.recipes(h.getLevel(), BenchType.valueOf(m[1])), m[0]) < 0, m[0] + " encore a l'etabli " + m[1]);
            Gt.check(indexOf(BenchTile.recipes(h.getLevel(), BenchType.valueOf(m[2])), m[0]) >= 0, m[0] + " absent de l'etabli " + m[2]);
        }
        s.done();
    }
}
