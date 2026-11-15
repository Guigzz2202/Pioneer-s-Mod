package fr.pioneers.gametest;

import fr.pioneers.bronze.bench.BenchRecipe;
import fr.pioneers.farm.food.DishShelfBlock;
import fr.pioneers.farm.food.DishShelfTile;
import fr.pioneers.farm.food.ShelfJoints;
import fr.pioneers.farm.food.WallShelfBlock;
import fr.pioneers.home.HomeWoods;
import fr.pioneers.home.PiHome;
import fr.pioneers.home.basket.BasketHeap;
import fr.pioneers.home.basket.BasketTile;
import fr.pioneers.registries.PiBronze;
import java.util.HashSet;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 12 (GameTests) : etageres murale / au sol, paniers en deux hauteurs.
 * <ul>
 *   <li>etageres : une murale par bois (nouvel id, meme tuile) et recettes d'etabli chargees ; murale tenue par son mur seulement (tombe
 *   sans lui), rangee liee de cote ; au sol : planche haute seulement au sommet, ses objets passent sur la planche du dessus a l'empilement ;
 *   murale d'avant le lot 12 (dish_shelf wall=true) convertie en murale du meme bois avec son contenu ;</li>
 *   <li>paniers : demi-hauteur 4 piles / 6 paliers (id d'origine), haut 8 piles / 10 paliers ; tas : paliers croissants, plafond de dessin,
 *   plein = toutes les places.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot12Tests {

    private PatchP103Lot12Tests() {
    }

    private static void connect(GameTestHelper h, Gt.Scope s, BlockPos p) {
        BlockState st = h.getBlockState(p);
        h.setBlock(p, st.getBlock() instanceof WallShelfBlock ? WallShelfBlock.connect(st, s.level, h.absolutePos(p))
              : DishShelfBlock.connect(st, s.level, h.absolutePos(p)));
    }

    // ================================================================== etageres
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_12_shelves", timeoutTicks = 20)
    public static void p103_12_etageres_murale_et_au_sol(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(HomeWoods.ALL.size(), (long) DishShelfBlock.WALL_VARIANTS.size(), "une murale par bois");
        Set<Item> made = new HashSet<>();
        for (BenchRecipe r : s.level.getRecipeManager().getAllRecipesFor(PiBronze.BENCH.get())) {
            made.add(r.result().getItem());
        }
        for (var e : DishShelfBlock.WALL_VARIANTS.values()) {
            Gt.check(DishShelfBlock.TILE.get().isValid(e.getBlock().defaultBlockState()), "tuile commune : " + e.getBlock());
            Gt.check(made.contains(e.getBlock().asItem()), "recette d'etabli chargee : " + e.getBlock());
        }
        //murale : rangee de 3 contre un mur (au sud), facade nord
        BlockState wall = DishShelfBlock.WALL_VARIANTS.get("spruce").getBlock().defaultBlockState().setValue(WallShelfBlock.FACING, Direction.NORTH);
        BlockPos w0 = new BlockPos(1, 2, 1);
        for (int i = 0; i < 3; i++) {
            h.setBlock(w0.east(i).south(), Blocks.STONE.defaultBlockState());
            h.setBlock(w0.east(i), wall);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (int i = 0; i < 3; i++) {
                connect(h, s, w0.east(i));
            }
        }
        BlockState mid = h.getBlockState(w0.east());
        Gt.check(mid.getValue(WallShelfBlock.LEFT) && mid.getValue(WallShelfBlock.RIGHT) && !mid.getValue(WallShelfBlock.MID),
              "rangee de 3 : la murale du milieu est liee des deux cotes, sans equerre");
        Gt.check(!h.getBlockState(w0).getValue(WallShelfBlock.LEFT) && h.getBlockState(w0).getValue(WallShelfBlock.RIGHT), "bout de rangee : equerre");
        Gt.check(!wall.canSurvive(s.level, h.absolutePos(w0.above(2))), "murale sans mur : ne tient pas");
        h.setBlock(w0.east(2).south(), Blocks.AIR.defaultBlockState());
        Gt.check(h.getBlockState(w0.east(2)).isAir(), "mur retire : la murale tombe");
        //au sol : seule = planche haute ; empilee = la planche haute disparait, ses objets passent sur la planche du dessus
        BlockState floor = DishShelfBlock.VARIANTS.get("birch").getBlock().defaultBlockState();
        BlockPos a = new BlockPos(5, 2, 5);
        h.setBlock(a.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(a, floor);
        connect(h, s, a);
        Gt.equal(2, (long) DishShelfBlock.boards(h.getBlockState(a)).size(), "seule : planche basse et planche haute");
        DishShelfTile low = (DishShelfTile) h.getBlockEntity(a);
        low.set(ShelfJoints.PER_BOARD + 1, new ItemStack(Items.BREAD));
        low.set(0, new ItemStack(Items.APPLE));
        h.setBlock(a.above(), floor);
        connect(h, s, a.above());
        connect(h, s, a);
        Gt.check(h.getBlockState(a).getValue(DishShelfBlock.UP) && h.getBlockState(a.above()).getValue(DishShelfBlock.DOWN), "pile : up / down");
        Gt.equal(1, (long) DishShelfBlock.boards(h.getBlockState(a)).size(), "empilee : plus que la planche basse");
        h.getBlockState(a).tick(s.level, h.absolutePos(a), s.level.random);
        DishShelfTile up = (DishShelfTile) h.getBlockEntity(a.above());
        Gt.check(up.get(1).is(Items.BREAD) && low.get(ShelfJoints.PER_BOARD + 1).isEmpty(), "l'objet de la planche haute passe sur celle du dessus");
        Gt.check(low.get(0).is(Items.APPLE), "la planche basse garde ses objets");
        //murale d'avant le lot 12 : convertie, meme bois, meme contenu
        BlockPos o = new BlockPos(5, 2, 1);
        h.setBlock(o.south(), Blocks.STONE.defaultBlockState());
        h.setBlock(o, DishShelfBlock.VARIANTS.get("cherry").getBlock().defaultBlockState().setValue(DishShelfBlock.WALL, true)
              .setValue(DishShelfBlock.FACING, Direction.NORTH));
        ((DishShelfTile) h.getBlockEntity(o)).set(2, new ItemStack(Items.COOKIE));
        h.getBlockState(o).tick(s.level, h.absolutePos(o), s.level.random);
        BlockState conv = h.getBlockState(o);
        Gt.check(conv.getBlock() == DishShelfBlock.WALL_VARIANTS.get("cherry").getBlock() && conv.getValue(WallShelfBlock.FACING) == Direction.NORTH,
              "ancienne murale -> murale du meme bois, meme facade : " + conv);
        Gt.check(((DishShelfTile) h.getBlockEntity(o)).get(2).is(Items.COOKIE), "contenu garde a la conversion");
        s.done();
    }

    // ================================================================== paniers
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_12_baskets", timeoutTicks = 20)
    public static void p103_12_paniers_deux_hauteurs(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos lo = new BlockPos(2, 2, 2), hi = new BlockPos(5, 2, 2);
        h.setBlock(lo, PiHome.BASKET.getBlock().defaultBlockState());
        h.setBlock(hi, PiHome.TALL_BASKET.getBlock().defaultBlockState());
        BasketTile a = (BasketTile) h.getBlockEntity(lo), b = (BasketTile) h.getBlockEntity(hi);
        Gt.check(!a.tall() && b.tall(), "deux formats, une tuile");
Gt.equal(8, a.insert(new ItemStack(Items.POTATO, 64), 600, false) + a.insert(new ItemStack(Items.POTATO, 64), 600, false), "demi-hauteur : 8 objets");
        int took = 0;
        for (int i = 0; i < 10; i++) {
            took += b.insert(new ItemStack(Items.POTATO, 64), 64, false);
        }
        Gt.equal(16, took, "haut : 16 objets");
        Gt.equal(BasketTile.LEVELS, a.level(), "demi-hauteur plein : palier 6");
        Gt.equal(BasketTile.TALL_LEVELS, b.level(), "haut plein : palier 10");
        b.extract(2, false);
        Gt.equal(9, b.level(), "haut : 14 / 16 -> palier 9");
        for (boolean tall : new boolean[]{false, true}) {
            for (boolean blocky : new boolean[]{false, true}) {
                BasketHeap.Heap heap = BasketHeap.of(tall, blocky);
                Gt.equal(tall ? BasketTile.TALL_LEVELS : BasketTile.LEVELS, heap.levels(), "paliers du tas");
                int prev = 0;
                for (int l = 1; l <= heap.levels(); l++) {
                    Gt.check(heap.shown(l) > prev, "le tas grandit a chaque palier");
                    Gt.check(heap.shown(l) - heap.first(l) <= heap.cap(), "plafond de dessin");
                    prev = heap.shown(l);
                }
                Gt.equal(heap.places().size(), (long) heap.shown(heap.levels()), "plein : toutes les places");
                for (BasketHeap.Place p : heap.places()) {
                    Gt.check(p.x() > 2 && p.x() < 14 && p.z() > 2 && p.z() < 14 && p.y() < (tall ? 15 : 10), "objet dans le panier : " + p);
                }
            }
        }
        Gt.check(BasketHeap.of(false, false).places().size() > 3 * 39, "demi-hauteur : bien plus dense qu'au lot 11b (39 places)");
        s.done();
    }
}
