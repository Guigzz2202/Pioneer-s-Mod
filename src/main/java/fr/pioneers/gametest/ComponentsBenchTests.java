package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.bronze.PiBronze;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 10) : l'atelier de composants basiques et la tole a la presse.
 * <ul>
 *     <li>atelier : marteau d'acier + 2 lingots d'acier -> 1 plaque d'acier (voie a la main, a perte), marteau use d'un point au plus ;</li>
 *     <li>presse hydraulique : 1 plaque d'acier + matrice de presse -> 2 toles d'acier (exact ; matrice rendue a 98 %).</li>
 * </ul>
 * Recettes de tools/gc/industry/forms_variants.py. Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class ComponentsBenchTests {

    private static final BlockPos AT = new BlockPos(2, 2, 2);

    private ComponentsBenchTests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot10_atelier", timeoutTicks = 100)
    public static void atelier_composants_plaque_a_la_main(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer player = s.player("gtbench", "Forgeronne", new BlockPos(4, 2, 4));
        h.setBlock(AT, PiBronze.BENCHES.get(BenchType.COMPONENTS).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(AT) instanceof BenchTile, "pas de tuile d'atelier");
        BenchTile bench = (BenchTile) h.getBlockEntity(AT);
        Gt.equal(BenchType.COMPONENTS, bench.type(), "sorte d'atelier");
        List<ItemSlot> slots = bench.guiSlots();
        ItemStack hammer = new ItemStack(item("steel_hammer"));
        slots.get(0).setStack(hammer.copy());
        slots.get(BenchType.TOOLS).setStack(new ItemStack(item("steel_ingot"), 2));
        List<BenchRecipe> recipes = BenchTile.recipes(h.getLevel(), BenchType.COMPONENTS);
        int index = -1;
        for (int i = 0; i < recipes.size(); i++) {
            if (recipes.get(i).getId().equals(rl("components_bench/steel_plate_from_ingots"))) {
                index = i;
            }
        }
        Gt.check(index >= 0, "recette components_bench/steel_plate_from_ingots absente de l'atelier");
        bench.handleCustom(player, BenchTile.SELECT + index);
        bench.handleCustom(player, BenchTile.CRAFT);
        Gt.check(bench.outputSlot().getStack().is(item("steel_plate")), "plaque d'acier fabriquee");
        Gt.equal(1, bench.outputSlot().getCount(), "une plaque pour deux lingots");
        Gt.check(slots.get(BenchType.TOOLS).isEmpty(), "lingots consommes");
        ItemStack used = slots.get(0).getStack();
        Gt.check(used.is(hammer.getItem()) && used.getDamageValue() <= 1, "marteau use d'un point au plus");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot10_presse", timeoutTicks = 600)
    public static void presse_tole_une_plaque_deux_toles(GameTestHelper h) {
        Gt.begin(h);
        Block block = PiIndustry.ATELIERS.get(IndustryMachine.PRESS).get(CableTier.ULTIMATE).getBlock();
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(AT, st);
        Gt.check(h.getBlockEntity(AT) instanceof AtelierTile, "pas de tuile d'atelier : presse hydraulique");
        AtelierTile m = (AtelierTile) h.getBlockEntity(AT);
        m.getEnergy().setStored(m.getEnergy().getCapacity());
        Gt.check(m.setLockedRecipe(rl("industry/press/press_sheet_steel")), "recette press_sheet_steel absente de la presse");
        m.itemInputs().get(0).setStack(new ItemStack(item("steel_plate"), 1));
        Gt.tool(m, new ItemStack(item("press_die"), 1));   // 1.0.5-beta (lot 44) : la matrice dans la case Outil
        Item sheet = item("steel_sheet");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            int n = 0;
            for (ItemSlot slot : m.itemOutputs()) {
                if (slot.getStack().is(sheet)) {
                    n += slot.getCount();
                }
            }
            //1.0.6 : depuis le lot 4 du 1.0.3-beta (T:industry/parts_p103.py), le coup de presse rend UNE tole par plaque (voie rapide et peu
            //couteuse en energie) ; les deux toles par plaque sont celles de la calandre (sheet_<metal>)
            Gt.equal(1, n, "une tole pour une plaque a la presse");
            Gt.check(m.itemInputs().get(0).isEmpty(), "plaque consommee");
        });
    }
}
