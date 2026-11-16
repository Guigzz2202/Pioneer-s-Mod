package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.bronze.bench.BenchRecipe;
import fr.pioneers.core.common.block.Connections;
import fr.pioneers.core.registry.BlockEntry;
import fr.pioneers.tech.industry.construction.FireboxWallBlock;
import fr.pioneers.tech.industry.construction.TwinConnectedBlock;
import fr.pioneers.tech.industry.construction.PiConstruction;
import fr.pioneers.tech.industry.multi.CasingBlock;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.DoorBlock;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoorHingeSide;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 21) : GameTests des BLOCS DE CONSTRUCTION DE MULTIBLOC (parois de foyer et bobines de chauffe MKI a MKIV, salle blanche).
 * <ul>
 *     <li>chacun des 12 blocs se pose (foyer oriente, bobine couchee), donne son butin (lui-meme) et se casse ; aucun n'est une carcasse
 *     (pas d'integration aux multiblocs) ; sa recette d'etabli est chargee ;</li>
 *     <li>la porte de salle blanche : deux moities, butin de la moitie basse seule, s'ouvre et se ferme (clic : type ouvrable a la main ;
 *     {@code setOpen}) et par la redstone ;</li>
 *     <li>parois et vitres de salle blanche voisines se connectent entre elles, pas l'une a l'autre.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot21Tests {

    private PatchP104Lot21Tests() {
    }

    private static boolean dropsItself(ServerLevel level, BlockState state, BlockPos abs) {
        List<ItemStack> drops = Block.getDrops(state, level, abs, null);
        return drops.size() == 1 && drops.get(0).is(state.getBlock().asItem()) && drops.get(0).getCount() == 1;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104u_blocks", timeoutTicks = 40)
    public static void p104_21_blocs_poses_casses(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        List<BlockEntry<? extends Block>> all = PiConstruction.all();
        //1.0.4-beta (lot 23) : la porte de salle blanche est retiree : 11 blocs (4 parois de foyer, 4 bobines, paroi, vitre, filtre)
        Gt.equal(11, all.size(), "11 blocs de construction");
        int k = 0;
        for (BlockEntry<? extends Block> e : all) {
            Block block = e.getBlock();
            String id = net.minecraftforge.registries.ForgeRegistries.BLOCKS.getKey(block).getPath();
            Gt.check(!(block instanceof CasingBlock), "pas une carcasse (aucune integration) : " + id);
            Recipe<?> r = level.getRecipeManager().byKey(Pioneers.rl("building_blocks/" + id)).orElse(null);
            Gt.check(r instanceof BenchRecipe, "recette d'etabli chargee : " + id);
            Gt.check(r.getResultItem(level.registryAccess()).is(block.asItem()), "la recette rend le bloc : " + id);
            if (block instanceof DoorBlock) {
                continue;
            }
            BlockPos rel = new BlockPos(1 + (k % 4) * 2, 1, 1 + (k / 4) * 2);
            k++;
            BlockState st = block.defaultBlockState();
            Gt.check(!st.hasProperty(BlockStateProperties.HORIZONTAL_FACING) && !st.hasProperty(BlockStateProperties.AXIS),
                  "lot 21c : aucune orientation : " + id);
            h.setBlock(rel, st);
            BlockPos abs = h.absolutePos(rel);
            BlockState placed = level.getBlockState(abs);
            Gt.check(placed.getBlock() == block, "pose : " + id);
            if (block instanceof FireboxWallBlock) {
                //1.0.4-beta (lot 23) : eteint par defaut, la lueur seulement allume (le controleur peint LIT quand le multibloc travaille)
                Gt.equal(0, placed.getLightEmission(level, abs), "foyer eteint par defaut : " + id);
                Gt.equal(PiConstruction.FIREBOX_LIGHT, placed.setValue(FireboxWallBlock.LIT, true).getLightEmission(level, abs), "lueur du foyer allume : " + id);
            }
            if (block instanceof TwinConnectedBlock twin) {
                Gt.check(placed.is(twin.family()), "lot 21c : famille connectee : " + id);
            }
            Gt.check(placed.requiresCorrectToolForDrops(), "outil requis : " + id);
            Gt.check(dropsItself(level, placed, abs), "butin : le bloc lui-meme : " + id);
            level.destroyBlock(abs, false);
            Gt.check(level.getBlockState(abs).isAir(), "casse : " + id);
        }
        Gt.equal(PiConstruction.FILTER_LIGHT, PiConstruction.CLEANROOM_FILTER.getBlock().defaultBlockState().getLightEmission(),
              "le plafond filtrant eclaire");
        s.done();
    }

    //1.0.4-beta (lot 23) : plus de porte de salle blanche (demande de Guillaume) : son test est retire (remap -> paroi : PatchP104Lot23Tests)

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104u_connect", timeoutTicks = 40)
    public static void p104_21_salle_blanche_connectee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = h.getLevel();
        BlockPos a = new BlockPos(2, 1, 2);
        h.setBlock(a, PiConstruction.CLEANROOM_CASING.getBlock().defaultBlockState());
        h.setBlock(a.east(), PiConstruction.CLEANROOM_CASING.getBlock().defaultBlockState());
        h.setBlock(a.west(), PiConstruction.CLEANROOM_GLASS.getBlock().defaultBlockState());
        BlockState st = level.getBlockState(h.absolutePos(a));
        Gt.check(st.getValue(Connections.property(Direction.EAST)), "deux parois voisines : connectees");
        Gt.check(!st.getValue(Connections.property(Direction.WEST)), "paroi contre vitre : cadre garde");
        BlockPos g = new BlockPos(2, 1, 5);
        h.setBlock(g, PiConstruction.CLEANROOM_GLASS.getBlock().defaultBlockState());
        h.setBlock(g.above(), PiConstruction.CLEANROOM_GLASS.getBlock().defaultBlockState());
        Gt.check(level.getBlockState(h.absolutePos(g)).getValue(Connections.property(Direction.UP)), "deux vitres voisines : une seule baie");
        s.done();
    }
}
