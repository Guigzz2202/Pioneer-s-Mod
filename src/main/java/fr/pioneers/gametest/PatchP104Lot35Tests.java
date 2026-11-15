package fr.pioneers.gametest;

import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.aquarium.AquariumBlock;
import fr.pioneers.farm.aquarium.AquariumTile;
import fr.pioneers.farm.aquarium.Aquariums;
import fr.pioneers.farm.aquarium.PiAquarium;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.4-beta, lot 35 (GameTests ; Guillaume : « Dans les aquarium on devrait pouvoir clic droit avec le poisson pour le mettre
 * directement sans utiliser le seau d'eau de poisson (A condition que l'aquarium contienne de l'eau) ») : poisson cru EN MAIN sur un aquarium
 * ({@link Aquariums#use}) : avec de l'eau dans le bassin, le poisson y est mis (case pleine la plus proche si la case visee est seche) et un
 * objet consomme ; bassin sans eau : refus, rien consomme ; bassin plein : refus, rien consomme ; poisson cuit : geste inchange (PASS) ; le
 * seau de poisson marche toujours. (Les pattes arriere des chevaux, autre point du lot, sont controlees hors jeu par le banc du rendu :
 * tools/farmtest/HorseRenderBench.java.)
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot35Tests {

    private PatchP104Lot35Tests() {
    }

    /** Deux aquariums empiles (bas, haut) ; {@code wet} : la case du bas pleine d'eau. */
    private static BlockPos[] tank(GameTestHelper h, boolean wet) {
        BlockPos lo = h.absolutePos(new BlockPos(4, 2, 4)), hi = lo.above();
        BlockState glass = PiAquarium.AQUARIUM.getBlock().defaultBlockState();
        h.getLevel().setBlock(lo, glass.setValue(AquariumBlock.FILLED, wet), 3);
        h.getLevel().setBlock(hi, glass, 3);
        return new BlockPos[]{lo, hi};
    }

    private static InteractionResult click(GameTestHelper h, Player p, BlockPos at, ItemStack held) {
        p.setItemInHand(InteractionHand.MAIN_HAND, held);
        return Aquariums.use(h.getLevel().getBlockState(at), h.getLevel(), at, p, InteractionHand.MAIN_HAND);
    }

    private static long fish(GameTestHelper h, BlockPos... cells) {
        long n = 0;
        for (BlockPos c : cells) {
            n += ((AquariumTile) h.getLevel().getBlockEntity(c)).count(false);
        }
        return n;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_35_main", timeoutTicks = 40)
    public static void p104_35_poisson_a_la_main(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos[] t = tank(h, true);
        Player p = h.makeMockPlayer();
        ItemStack cod = new ItemStack(Items.COD, 3);
        Gt.check(click(h, p, t[1], cod).consumesAction(), "morue crue en main : geste de l'aquarium");
        Gt.equal(1L, fish(h, t[0]), "la morue rejoint la case d'eau du bassin (la case visee est seche)");
        Gt.equal(2L, (long) p.getMainHandItem().getCount(), "une morue consommee");
        ItemStack mod = new ItemStack(PiFishing.FISH_ITEMS.values().iterator().next().get());
        mod.getOrCreateTag().putInt("Size", 1000);
        click(h, p, t[0], mod);
        Gt.equal(2L, fish(h, t[0]), "poisson du mod ajoute dans la case visee");
        Gt.check(p.getMainHandItem().isEmpty(), "le poisson du mod est consomme");
        AquariumTile tile = (AquariumTile) h.getLevel().getBlockEntity(t[0]);
        Gt.check(Math.abs(tile.dwellers().get(1).size() - 1.2F) < 1E-4F, "taille gardee (grand poisson : echelle 1,2)");
        //poisson cuit : rien (geste ordinaire), seau de poisson : toujours bon
        Gt.check(click(h, p, t[0], new ItemStack(Items.COOKED_COD, 2)) == InteractionResult.PASS, "poisson cuit : geste inchange");
        Gt.equal(2L, (long) p.getMainHandItem().getCount(), "poisson cuit non consomme");
        click(h, p, t[0], new ItemStack(Items.SALMON_BUCKET));
        Gt.equal(3L, fish(h, t[0]), "seau de saumon : toujours accepte");
        Gt.check(p.getMainHandItem().is(Items.BUCKET), "le seau revient vide");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_35_dry", timeoutTicks = 40)
    public static void p104_35_sans_eau(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos[] t = tank(h, false);
        Player p = h.makeMockPlayer();
        click(h, p, t[1], new ItemStack(Items.COD, 3));
        Gt.equal(0L, fish(h, t[0], t[1]), "bassin sans eau : aucun poisson");
        Gt.equal(3L, (long) p.getMainHandItem().getCount(), "bassin sans eau : rien consomme");
        click(h, p, t[1], new ItemStack(Items.COD_BUCKET));
        Gt.check(p.getMainHandItem().is(Items.COD_BUCKET), "seau de poisson garde aussi");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_35_full", timeoutTicks = 40)
    public static void p104_35_bassin_plein(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos[] t = tank(h, true);
        AquariumTile tile = (AquariumTile) h.getLevel().getBlockEntity(t[0]);
        for (int i = 0; i < AquariumTile.FISH_MAX; i++) {
            tile.add(false, 0, 1F);
        }
        Player p = h.makeMockPlayer();
        click(h, p, t[0], new ItemStack(Items.COD, 3));
        Gt.equal((long) AquariumTile.FISH_MAX, fish(h, t[0], t[1]), "bassin plein : pas un poisson de plus");
        Gt.equal(3L, (long) p.getMainHandItem().getCount(), "bassin plein : rien consomme");
        s.done();
    }
}
