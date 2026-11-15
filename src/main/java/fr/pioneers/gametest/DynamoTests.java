package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.gas.Gas;
import fr.pioneers.api.gas.GasStack;
import fr.pioneers.industry.GeneratorOutput;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.atelier.DynamoBlock;
import fr.pioneers.industry.atelier.DynamoTile;
import fr.pioneers.industry.recipe.IndustrialRecipe;
import fr.pioneers.industry.recipe.IndustryRecipes;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.parts.HandCrankBlock;
import fr.pioneers.registries.PiIndustry;
import fr.pioneers.registries.PiKinetics;
import fr.pioneers.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import org.jetbrains.annotations.Nullable;

/**
 * 1.12.4 (lot F4, audit de l'energie) :
 * <ul>
 *     <li>sortie de la dynamo : une manivelle (1.0.2-beta : le barillet a ressort est retire) la fait tourner ; elle stocke de l'energie et rend 1 FE par joule (FE/t affiches =
 *     puissance mecanique / 20) ;</li>
 *     <li>orientation : la facade reste perpendiculaire a l'axe quand on tourne le bloc ; un etat d'avant (facade sur l'axe) prend la facade
 *     par defaut ;</li>
 *     <li>coherence de {@link GeneratorOutput} : rendement par mB generateur a combustion &lt; turbine a gaz &lt; cycle combine.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class DynamoTests {

    private DynamoTests() {
    }

    /** 1.0.2-beta : le barillet a ressort est retire : la dynamo est entrainee par une manivelle tournee a chaque tick. */
    private static void crank(GameTestHelper h, BlockPos at) {
        h.setBlock(at, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, Direction.EAST));
        Gt.check(h.getBlockEntity(at) instanceof HandCrankBlock.Tile, "manivelle");
        HandCrankBlock.Tile tile = (HandCrankBlock.Tile) h.getBlockEntity(at);
        h.onEachTick(() -> tile.crank(10));
    }

    private static BlockState dynamo() {
        return PiIndustry.DYNAMOS.get(CableTier.BASIC).getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.X)
              .setValue(DynamoBlock.FACING, Direction.UP);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_f4_dynamo_sortie", timeoutTicks = 200)
    public static void dynamo_sortie(GameTestHelper h) {
        Gt.begin(h);
        BlockPos spring = new BlockPos(2, 1, 4), dyn = new BlockPos(3, 1, 4);
        crank(h, spring);
        h.setBlock(dyn, dynamo());
        h.succeedWhen(() -> {
            Gt.check(h.getBlockEntity(dyn) instanceof DynamoTile, "dynamo");
            DynamoTile d = (DynamoTile) h.getBlockEntity(dyn);
            double fe = d.guiValue(2) / 100.0, watts = d.guiValue(5);
            Gt.check(d.guiValue(0) > 0, "la dynamo n'a rien stocke");
            Gt.check(watts > 50, "puissance mecanique trop faible : " + watts + " W");
            Gt.check(Math.abs(fe - watts / 20.0 * DynamoTile.FE_PER_JOULE) <= 1 + watts * 0.01, "1 J = 1 FE : " + fe + " FE/t pour " + watts + " W");
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_f4_dynamo_orientation", timeoutTicks = 20)
    public static void dynamo_orientation(GameTestHelper h) {
        Gt.begin(h);
        BlockState state = dynamo();
        for (Rotation r : Rotation.values()) {
            BlockState turned = state.rotate(r);
            Gt.check(DynamoBlock.facing(turned).getAxis() != AxisKineticBlock.axis(turned), "facade sur l'axe apres " + r);
        }
        BlockState vertical = state.setValue(AxisKineticBlock.AXIS, Direction.Axis.Y).setValue(DynamoBlock.FACING, Direction.NORTH);
        Gt.equal(Direction.EAST, DynamoBlock.facing(vertical.rotate(Rotation.CLOCKWISE_90)), "axe vertical tourne");
        BlockState legacy = state.setValue(AxisKineticBlock.AXIS, Direction.Axis.Y).setValue(DynamoBlock.FACING, Direction.UP);
        Gt.equal(Direction.NORTH, DynamoBlock.facing(legacy), "etat d'avant 1.12.4 (facade par defaut)");
        h.succeed();
    }

    @Nullable
    private static IndustrialRecipe recipeFor(Level level, IndustryMachine m, Gas gas) {
        for (IndustrialRecipe r : IndustryRecipes.of(level, m)) {
            if (r.energy() > 0 && r.gases().size() == 1 && r.gases().get(0).test(GasStack.of(gas, 1))) {
                return r;
            }
        }
        return null;
    }

    /**
     * Audit F4 : la turbine a gaz n'est pas dominee (5 fois plus compacte) : le multibloc rend plus par mB que le compact. 1.0.4-beta (lot 13) :
     * plus de centrale a cycle combine (le joueur enchaine turbine a gaz, chaudiere et turbine a vapeur) : plus aucune recette ne la vise.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_f4_turbines", timeoutTicks = 20)
    public static void cycle_combine_rendement(GameTestHelper h) {
        Gt.begin(h);
        Gas gas = PioneersAPI.gas(Pioneers.rl("natural_gas"));
        Gt.check(gas != null, "gaz naturel");
        IndustrialRecipe turbine = recipeFor(h.getLevel(), IndustryMachine.GAS_TURBINE, gas);
        //1.0.3-beta (lot 5) : plus de gaz au generateur a combustion : la turbine a gaz compacte (x1) en tient la place
        Gt.check(turbine != null, "recette de la turbine au gaz naturel");
        double perMbTurbine = GeneratorOutput.perUnit(turbine, turbine.gases().get(0).amount(), true);
        double perMbCompact = GeneratorOutput.perUnit(turbine, turbine.gases().get(0).amount(), false);
        Gt.check(perMbCompact < perMbTurbine, "rendement : turbine compacte < turbine a gaz multibloc");
        Gt.check(IndustryMachine.byKey("combined_cycle_plant") == null, "centrale a cycle combine supprimee (1.0.4-beta lot 13)");
        h.succeed();
    }
}
