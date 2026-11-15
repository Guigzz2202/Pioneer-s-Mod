package fr.pioneers.gametest;

import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.tech.bronze.bench.BenchTile;
import fr.pioneers.tech.bronze.bench.BenchType;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.atelier.AtelierTile;
import fr.pioneers.core.registries.PiBronze;
import fr.pioneers.core.registries.PiIndustry;
import fr.pioneers.logistics.transport.CableTier;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.11.1 (lot E1) : les etablis de l'age industriel et leurs machines jumelles.
 * <ul>
 *     <li>l'etabli de composants avances fait le chassis MKI de la nomenclature, l'etabli de montage ne le fait plus ;</li>
 *     <li>l'etabli de pieces logistiques fait la locomotive a vapeur MKI ;</li>
 *     <li>migration : un assembleur verrouille sur une recette partie a l'assembleur de composants perd son verrou proprement, et
 *     l'assembleur de composants accepte ce verrou.</li>
 * </ul>
 * Ces tests ne tournent qu'avec les GameTests de Forge (runGameTestServer).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class BenchesE1Tests {

    private static final BlockPos AT = new BlockPos(2, 2, 2);

    private BenchesE1Tests() {
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath("pioneers", path);
    }

    private static boolean has(List<BenchRecipe> recipes, String id) {
        for (BenchRecipe r : recipes) {
            if (r.getId().equals(rl(id))) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e1_etablis", timeoutTicks = 40)
    public static void etablis_composants_et_logistique(GameTestHelper h) {
        Gt.begin(h);
        h.setBlock(AT, PiBronze.BENCHES.get(BenchType.ADVANCED_COMPONENTS).getBlock().defaultBlockState());
        Gt.check(h.getBlockEntity(AT) instanceof BenchTile, "pas de tuile d'etabli");
        Gt.equal(BenchType.ADVANCED_COMPONENTS, ((BenchTile) h.getBlockEntity(AT)).type(), "sorte d'etabli");
        Gt.check(has(BenchTile.recipes(h.getLevel(), BenchType.ADVANCED_COMPONENTS), "components/bench/rc_machine_frame_basic"),
              "chassis MKI absent de l'etabli de composants avances");
        Gt.check(!has(BenchTile.recipes(h.getLevel(), BenchType.ASSEMBLY), "components/bench/rc_machine_frame_basic"),
              "chassis MKI encore a l'etabli de montage");
        Gt.check(has(BenchTile.recipes(h.getLevel(), BenchType.LOGISTICS), "rail/basic_steam_locomotive"),
              "locomotive a vapeur MKI absente de l'etabli de pieces logistiques");
        h.succeed();
    }

    private static AtelierTile atelier(GameTestHelper h, IndustryMachine machine) {
        Block block = PiIndustry.ATELIERS.get(machine).get(CableTier.BASIC).getBlock();
        BlockState st = block.defaultBlockState();
        if (st.hasProperty(BlockStateProperties.HORIZONTAL_FACING)) {
            st = st.setValue(BlockStateProperties.HORIZONTAL_FACING, Direction.NORTH);
        }
        h.setBlock(AT, st);
        Gt.check(h.getBlockEntity(AT) instanceof AtelierTile, "pas de tuile d'atelier : " + machine.key);
        return (AtelierTile) h.getBlockEntity(AT);
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_e1_verrou", timeoutTicks = 40)
    public static void verrou_parti_ailleurs_efface(GameTestHelper h) {
        Gt.begin(h);
        //1.0.4-beta (lot 14) : l'assembleur de composants est fondu dans l'Assembleur : le roulement y revient (meme id de recette) ; une
        //recette d'une autre machine (la Presse) reste un verrou a effacer
        ResourceLocation moved = rl("industry/press/press_plate_iron");
        AtelierTile assembler = atelier(h, IndustryMachine.ASSEMBLER);
        assembler.recipeLock().set(moved);                                  // verrou d'une recette d'une autre machine
        Gt.check(assembler.dropMissingLock(), "le verrou d'une recette partie ailleurs doit etre efface");
        Gt.check(!assembler.recipeLock().isSet(), "assembleur sans verrou");
        Gt.check(assembler.setLockedRecipe(rl("industry/component_assembler/rc_bearing_1")), "l'Assembleur fait le roulement (ex-assembleur de composants)");
        Gt.check(!assembler.dropMissingLock(), "verrou valide garde");
        h.succeed();
    }
}
