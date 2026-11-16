package fr.pioneers.gametest;

import fr.pioneers.core.inventory.ItemSlot;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.core.registries.PiIndustry;
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
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * MAJ Recettes (lot 9a, oubli 1 de l'audit de couverture) : une recette de chacune des machines des formes du lot 4a (la rectifieuse est
 * couverte par le reconditionnement d'un objectif, MachineRefitTests).
 * <ul>
 *     <li>frappeuse a froid : fil machine d'acier -> 8 boulons d'acier (8 tetes, sans chute) ;</li>
 *     <li>enrouleuse a ressorts : 2 fils de laiton -> 1 ressort de laiton ;</li>
 *     <li>tailleuse d'engrenages : ebauche d'engrenage en bronze -> engrenage + 4 pepites (copeaux) ;</li>
 *     <li>toronneuse : 3 fils de cuivre -> 3 cables tresses de cuivre ;</li>
 *     <li>marteau d'estampage : 9 lingots de vanadium -> billette forgee.</li>
 * </ul>
 * Recettes de tools/gc/industry/forms_gen.py. Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class FormMachineTests {

    private FormMachineTests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static Item item(String id) {
        Item it = ForgeRegistries.ITEMS.getValue(rl(id));
        Gt.check(it != null && it != Items.AIR, "objet absent : " + id);
        return it;
    }

    /** Machine d'atelier ultime posee en rel, energie pleine, recette verrouillee. */
    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine, BlockPos rel, String recipe) {
        Block block = PiIndustry.ATELIERS.get(machine).get(CableTier.ULTIMATE).getBlock();
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

    //1.0.4-beta (lot 14, fusion des machines) : frappeuse -> Marteau-pilon, enrouleuse -> Cintreuse, tailleuse -> Centre d'usinage,
    //toronneuse -> Trefileuse : memes recettes (memes ids), au compact MKIV de la base
    @GameTest(template = Gt.SMALL, batch = "pioneers_lot4a_frappeuse", timeoutTicks = 600)
    public static void frappeuse_boulons(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.POWER_HAMMER, new BlockPos(2, 2, 2), "industry/cold_header/head_bolt_steel");
        m.itemInputs().get(0).setStack(new ItemStack(item("steel_wire_rod"), 1));
        Item bolt = item("steel_bolt");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(8, count(m, bolt), "boulons frappes");
            Gt.check(m.itemInputs().get(0).isEmpty(), "fil machine consomme");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot4a_enrouleuse", timeoutTicks = 600)
    public static void enrouleuse_ressorts(GameTestHelper h) {
        Gt.begin(h);
        //1.0.6 : la recette actuelle enroule 2 tiges (2 x 72 mB = 144 mB, le ressort) ; plus de recette depuis le fil
        AtelierTile m = atelier(h, IndustryMachine.BENDER, new BlockPos(2, 2, 2), "industry/spring_coiler/coil_rod_spring_brass");
        m.itemInputs().get(0).setStack(new ItemStack(item("brass_rod"), 4));
        Item spring = item("brass_spring");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(2, count(m, spring), "ressorts enroules");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot4a_tailleuse", timeoutTicks = 600)
    public static void tailleuse_engrenage_et_copeaux(GameTestHelper h) {
        Gt.begin(h);
        //1.0.6 : la taille d'une ebauche d'engrenage est la recette hob_gear (cut_gear part de la plaque)
        AtelierTile m = atelier(h, IndustryMachine.MACHINING_CENTER, new BlockPos(2, 2, 2), "industry/gear_hobber/hob_gear_bronze");
        m.itemInputs().get(0).setStack(new ItemStack(item("bronze_gear_blank"), 1));
        //la taille se fait au lubrifiant (50 mB)
        m.fluidInputs().get(0).setFluid(new net.minecraftforge.fluids.FluidStack(net.minecraftforge.registries.ForgeRegistries.FLUIDS
              .getValue(net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("pioneers", "lubricant")), 1_000));
        Item gear = item("bronze_gear"), nugget = item("bronze_nugget");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(1, count(m, gear), "engrenage taille");
            Gt.equal(4, count(m, nugget), "copeaux en pepites");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot4a_toronneuse", timeoutTicks = 600)
    public static void toronneuse_cable_tresse(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.WIREMILL, new BlockPos(2, 2, 2), "industry/wire_strander/strand_rope_copper");
        m.itemInputs().get(0).setStack(new ItemStack(item("copper_wire"), 3));
        Item rope = item("copper_wire_rope");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(3, count(m, rope), "cables tresses de cuivre");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_lot4a_marteau", timeoutTicks = 1200)
    public static void marteau_billette_forgee(GameTestHelper h) {
        Gt.begin(h);
        AtelierTile m = atelier(h, IndustryMachine.POWER_HAMMER, new BlockPos(2, 2, 2), "industry/power_hammer/forge_billet_vanadium");
        m.itemInputs().get(0).setStack(new ItemStack(item("vanadium_ingot"), 9));
        Item billet = item("vanadium_billet");
        h.succeedWhen(() -> {
            m.getEnergy().setStored(m.getEnergy().getCapacity());
            Gt.equal(1, count(m, billet), "billette forgee au marteau d'estampage");
        });
    }
}
