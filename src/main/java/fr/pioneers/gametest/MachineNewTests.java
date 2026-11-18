package fr.pioneers.gametest;

import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 8b, GameTests du brief §6) : une recette de chacune des machines nouvelles creees par le lot.
 * <ul>
 *     <li>four de verrerie : 4 sables + carbonate de sodium -> 6 verres (recette deplacee du four a induction) ;</li>
 *     <li>tour de fibrage : 4 verres -> 4 fibres de verre ;</li>
 *     <li>decoupeuse (plasma) : 3 toles de duralumin -> lame de couteau + 4 pepites (chutes) ;</li>
 *     <li>fraiseuse a commande numerique : 3 plaques d'acier au vanadium + lubrifiant -> matrice a lingot + 9 pepites (copeaux) ;</li>
 *     <li>projection plasma : plaque d'acier + poudres de diamant et de nichrome + argon -> 2 disques diamantes ;</li>
 *     <li>imprimante 3D metal : 5 poudres d'acier au vanadium + argon -> tete de marteau de minage.</li>
 * </ul>
 * Recettes de tools/gc/industry/machines8b_gen.py. Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class MachineNewTests {

    private MachineNewTests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(id.contains(":") ? ResourceLocation.parse(id) : rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    private static Fluid fluid(String id) {
        Fluid f = PiIndustry.fluid(id);
        Gt.check(f != Fluids.EMPTY, "fluide absent : " + id);
        return f;
    }

    /** Machine d'atelier ultime posee en rel, energie pleine, recette verrouillee. */
    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine, BlockPos rel, String recipe) {
        Block block = Gt.compact(machine, CableTier.ULTIMATE);   // 1.0.4-beta (lot 11) : kit compris (machines absorbees)
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(rel, st);
        Gt.check(h.getBlockEntity(rel) instanceof AtelierTile, "pas de tuile d'atelier : " + machine.key);
        AtelierTile t = (AtelierTile) h.getBlockEntity(rel);
        t.getEnergy().setStored(t.getEnergy().getCapacity());
        Gt.check(t.setLockedRecipe(rl(recipe)), "recette " + recipe + " absente de " + machine.key);
        return t;
    }

    private static int count(AtelierTile m, Item it) {
        int n = 0;
        for (ItemSlot s : m.itemOutputs()) {
            if (s.getStack().is(it)) {
                n += s.getCount();
            }
        }
        return n;
    }

    private static void argon(AtelierTile m, long amount) {
        m.gasInputs().get(0).setStack(GasStack.of(PioneersAPI.gas(rl("argon")), amount));
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8b_verrerie", timeoutTicks = 600)
    public static void four_de_verrerie_verre(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.ELECTRIC_CRUCIBLE, new BlockPos(2, 2, 2), "industry/tank_furnace/soda_glass");
        m.itemInputs().get(0).setStack(new ItemStack(Items.SAND, 4));
        m.itemInputs().get(1).setStack(new ItemStack(item("soda_ash"), 1));
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(6, count(m, Items.GLASS), "verre fondu au four de verrerie");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8b_fibrage", timeoutTicks = 600)
    public static void tour_de_fibrage_fibre(GameTestHelper h) {
        Gt.begin(h);
        //1.0.4-beta (lot 14) : la tour de fibrage est fondue dans la Trefileuse (meme recette, meme id)
        AtelierTile m = atelier(h, IndustryMachine.WIREMILL, new BlockPos(2, 2, 2), "industry/fiber_tower/soda_glass_fiber");
        m.itemInputs().get(0).setStack(new ItemStack(Items.GLASS, 4));
        Item fiber = item("glass_fiber");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(4, count(m, fiber), "fibres de verre");
        });
    }

    /** 1.0.4-beta (lot 14) : la decoupeuse est supprimee ; sa voie « 1 bloc -> 9 plaques » (exemple impose de Guillaume) passe au Laminoir. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8b_decoupeuse", timeoutTicks = 800)
    public static void laminoir_bloc_neuf_plaques(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.ROLLING_MILL, new BlockPos(2, 2, 2), "industry/rolling_mill/roll_block_plate_iron");
        m.itemInputs().get(0).setStack(new ItemStack(Items.IRON_BLOCK, 1));
        Item plate = item("iron_plate");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(9, count(m, plate), "9 plaques laminees d'un bloc");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8b_fraiseuse", timeoutTicks = 600)
    public static void fraiseuse_matrice(GameTestHelper h) {
        Gt.begin(h);
        //1.0.4-beta (lot 14) : la fraiseuse est fondue dans le Centre d'usinage
        AtelierTile m = atelier(h, IndustryMachine.MACHINING_CENTER, new BlockPos(2, 2, 2), "industry/cnc_mill/mill_ingot_die");
        m.itemInputs().get(0).setStack(new ItemStack(item("vanadium_steel_plate"), 3));
        m.fluidInputs().get(0).setFluid(new FluidStack(fluid("lubricant"), 1_000));
        Item die = item("ingot_die"), nugget = item("vanadium_steel_nugget");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(1, count(m, die), "matrice a lingot fraisee");
            Gt.equal(9, count(m, nugget), "copeaux en pepites");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8b_projection", timeoutTicks = 800)
    public static void projection_plasma_disques(GameTestHelper h) {
        Gt.begin(h);
        //1.0.4-beta (lot 14) : la projection plasma est fondue dans le Bain chimique
        AtelierTile m = atelier(h, IndustryMachine.CHEMICAL_BATH, new BlockPos(2, 2, 2), "industry/plasma_sprayer/spray_diamond_discs");
        m.itemInputs().get(0).setStack(new ItemStack(item("steel_plate"), 1));
        m.itemInputs().get(1).setStack(new ItemStack(item("diamond_dust"), 1));
        m.itemInputs().get(2).setStack(new ItemStack(item("nichrome_dust"), 1));
        argon(m, 1_000);
        Item disc = item("diamond_disc");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            //1.0.6 : autant que la recette en rend (4 aujourd'hui ; le test d'origine en attendait 2)
            int want = ((fr.pioneers.tech.industry.recipe.IndustrialRecipe) h.getLevel().getRecipeManager()
                  .byKey(rl("industry/plasma_sprayer/spray_diamond_discs")).orElseThrow()).results().get(0).stack().getCount();
            Gt.equal(want, count(m, disc), "disques diamantes projetes");
        });
    }

    /**
     * 1.0.4-beta (lot 14) : l'imprimante 3D metal est supprimee ; l'outil de coupe en carbure (que seule sa voie a la poudre faisait a la vague
     * 77) se fritte au creuset industriel avec la POUDRE de carbure de tungstene. 1.0.5-beta (lot 39) : alliage direct solide -> solide : au
     * four a induction (rendu a lui-meme), plus au creuset.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_lot8b_imprimante", timeoutTicks = 1000)
    public static void outil_carbure_fritte_poudre(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.INDUCTION_FURNACE, new BlockPos(2, 2, 2), "industry/induction_furnace/rc_carbide_cutting_tools");
        m.itemInputs().get(0).setStack(new ItemStack(item("tungsten_carbide_dust"), 2));
        m.itemInputs().get(1).setStack(new ItemStack(item("vanadium_rod"), 2));
        m.itemInputs().get(2).setStack(new ItemStack(item("stellite_plate"), 1));
        Item tool = item("carbide_cutting_tool");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(4, count(m, tool), "outils de coupe en carbure frittes");
        });
    }
}
