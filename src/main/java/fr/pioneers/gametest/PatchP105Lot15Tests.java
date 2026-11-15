package fr.pioneers.gametest;

import fr.pioneers.Pioneers;
import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.api.multiblock.HouseShapes;
import fr.pioneers.bronze.HouseControllerBlock;
import fr.pioneers.bronze.blast.BlastFurnaceTile;
import fr.pioneers.bronze.blast.FireBricksBlock;
import fr.pioneers.forge.ForgeMetals;
import fr.pioneers.forge.HeatedMetal;
import fr.pioneers.forge.SmithingGrid;
import fr.pioneers.industry.bronze.CokeOvenTile;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticNetwork;
import fr.pioneers.kinetics.KineticNetworks;
import fr.pioneers.kinetics.rope.LinkType;
import fr.pioneers.kinetics.rope.PulleyTile;
import fr.pioneers.kinetics.rope.RopeItem;
import fr.pioneers.kinetics.rope.RopeRules;
import fr.pioneers.registries.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 15 (retours de jeu 3 : age primitif et mecanique, GameTests) : POULIES HORS PLAN (decalees de 1 bloc le long de
 * leur axe : corde acceptee, meme reseau, meme rapport ; 3 blocs ou plus de la moitie de l'ecart : refusees, {@code RopeRules}) ; FOUR A COKE
 * FORME (controleur {@code formed}, briques peintes : cheminee au centre de la voute, cornieres, porte ; tuyere otee : controleur et briques
 * redeviennent libres) ; HAUT FOURNEAU FORME (controleur {@code formed}) ; PIECE CHAUDE qui garde son objet vise (lingot chauffe -> lingot,
 * piece a tremper -> la piece, forge sans recette -> rien : billette).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot15Tests {

    private PatchP105Lot15Tests() {
    }

    private static String key(Component c) {
        return c != null && c.getContents() instanceof TranslatableContents t ? t.getKey() : String.valueOf(c);
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_15_pulleys", timeoutTicks = 40)
    public static void p105_15_poulies_hors_plan(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(RopeRules.offsetAllowed(0, 3) && RopeRules.offsetAllowed(1, 6) && RopeRules.offsetAllowed(2, 4), "decalages permis");
        Gt.check(!RopeRules.offsetAllowed(3, 10) && !RopeRules.offsetAllowed(2, 3) && !RopeRules.offsetAllowed(1, 0), "decalages refuses");
        BlockState pulley = PiKinetics.PULLEY.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.X);
        //a et b : axe X, b decale de 1 bloc le long de l'axe et de 6 blocs dans le plan
        BlockPos a = h.absolutePos(new BlockPos(4, 3, 4)), b = h.absolutePos(new BlockPos(5, 3, 10));
        BlockPos far = h.absolutePos(new BlockPos(7, 3, 14)), steep = h.absolutePos(new BlockPos(6, 3, 7));
        for (BlockPos p : new BlockPos[]{a, b, far, steep}) {
            h.getLevel().setBlock(p, pulley, 3);
        }
        Gt.check(RopeItem.check(h.getLevel(), a, b) == null, "poulies decalees de 1 bloc le long de l'axe : corde acceptee : " + RopeItem.check(h.getLevel(), a, b));
        Gt.equal("kinetics.pioneers.rope.plane", key(RopeItem.check(h.getLevel(), a, far)), "decalage de 3 blocs refuse");
        Gt.equal("kinetics.pioneers.rope.plane", key(RopeItem.check(h.getLevel(), a, steep)), "decalage de 2 blocs pour 3 d'ecart refuse");
        PulleyTile ta = (PulleyTile) h.getLevel().getBlockEntity(a), tb = (PulleyTile) h.getLevel().getBlockEntity(b);
        ta.connect(b, LinkType.FIBER_ROPE, false, true);
        tb.connect(a, LinkType.FIBER_ROPE, false, false);
        h.runAfterDelay(3, () -> {
            KineticNetwork na = KineticNetworks.network(h.getLevel(), a), nb = KineticNetworks.network(h.getLevel(), b);
            Gt.check(na != null && na == nb, "la corde oblique relie les deux poulies dans un meme reseau (la rotation passe)");
            double ra = na.effectiveRatio(ta), rb = na.effectiveRatio(tb);
            Gt.check(Math.abs(ra) > 1e-9 && Math.abs(rb / ra - 1.0) < 1e-6, "meme rapport qu'une corde dans le plan (poulies de meme taille : 1) : " + rb / ra);
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_15_coke", timeoutTicks = 200)
    public static void p105_15_four_a_coke_forme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApi6Tests.buildHouse(h, HouseShapes.COKE_OVEN, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            BlockEntity be = h.getBlockEntity(c);
            Gt.check(be instanceof CokeOvenTile, "pas de four a coke en " + c);
            CokeOvenTile oven = (CokeOvenTile) be;
            BlockPos ctrl = h.absolutePos(c);
            Gt.check(oven.formed(), "le four a coke construit au plan ne se forme pas");
            Gt.check(h.getLevel().getBlockState(ctrl).getValue(HouseControllerBlock.FORMED), "controleur forme : habillage des foyers voisins");
            int top = 0;
            for (HouseNbt.Cell cell : HouseNbt.cells(CokeOvenTile.SHAPE)) {
                if (cell.letter() == 'B') {
                    top = Math.max(top, cell.y());
                }
            }
            BlockPos chimney = HouseNbt.at(ctrl, oven.facing(), 0, top, 1);
            Gt.equal(FireBricksBlock.Part.COKE_CHIMNEY, h.getLevel().getBlockState(chimney).getValue(FireBricksBlock.PART), "cheminee au centre de la voute");
            BlockPos corner = HouseNbt.at(ctrl, oven.facing(), 1, 1, 0);
            //1.0.5-beta (lot 23) : angle ORIENTE (montant a l'arete exterieure seulement : CokeOvenTile.cornerOf)
            Gt.equal(CokeOvenTile.cornerOf(1, 0, oven.facing()), h.getLevel().getBlockState(corner).getValue(FireBricksBlock.PART), "angle a cornieres");
            BlockPos door = HouseNbt.at(ctrl, oven.facing(), 0, 1, 0);
            Gt.equal(FireBricksBlock.Part.COKE_DOOR, h.getLevel().getBlockState(door).getValue(FireBricksBlock.PART), "porte de defournement");
            HouseNbt.Cell t = HouseNbt.first(CokeOvenTile.SHAPE, 'T');
            h.getLevel().destroyBlock(HouseNbt.at(ctrl, oven.facing(), t), false);
            oven.requestCheck();
            h.runAfterDelay(4, () -> {
                Gt.check(!oven.formed(), "four a coke forme sans sa tuyere");
                Gt.check(!h.getLevel().getBlockState(ctrl).getValue(HouseControllerBlock.FORMED), "controleur redevenu non forme");
                Gt.equal(FireBricksBlock.Part.NONE, h.getLevel().getBlockState(chimney).getValue(FireBricksBlock.PART), "brique redevenue libre");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_15_blast", timeoutTicks = 200)
    public static void p105_15_haut_fourneau_forme(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApi6Tests.buildHouse(h, HouseShapes.BLAST_FURNACE, Direction.SOUTH);
        h.runAfterDelay(60, () -> {
            Gt.check(h.getBlockEntity(c) instanceof BlastFurnaceTile, "pas de haut fourneau en " + c);
            Gt.check(h.getLevel().getBlockState(h.absolutePos(c)).getValue(HouseControllerBlock.FORMED), "controleur du haut fourneau forme");
            s.done();
        });
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_15_hot", timeoutTicks = 20)
    public static void p105_15_piece_chaude_objet_vise(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ItemStack ingot = HeatedMetal.create("iron", ForgeMetals.INGOT_MB, 1000, h.getLevel());
        ItemStack look = HeatedMetal.lookOf(ingot, h.getLevel());
        Gt.check(ItemStack.isSameItem(look, ForgeMetals.ingot("iron", 1)), "lingot chauffe : apparence du lingot de fer, pas une billette : " + look);
        Item head = BuiltInRegistries.ITEM.get(Pioneers.rl("iron_pickaxe_head"));
        Gt.check(head != Items.AIR, "tete de pioche en fer");
        ItemStack part = ingot.copy();
        HeatedMetal.setShape(part, new ItemStack(head));
        Gt.check(HeatedMetal.lookOf(part, h.getLevel()).is(head), "piece a tremper : apparence de la piece (tete de pioche)");
        ItemStack work = ingot.copy();
        HeatedMetal.setWork(work, SmithingGrid.of(new long[4]), null);
        Gt.check(HeatedMetal.lookOf(work, h.getLevel()).isEmpty(), "forge sans recette choisie : pas d'objet vise (billette)");
        Gt.equal(1L, (long) HeatedMetal.lookOf(ingot.copyWithCount(3), h.getLevel()).getCount(), "une seule piece dessinee");
        s.done();
    }
}
