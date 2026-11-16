package fr.pioneers.gametest;

import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticNetwork;
import fr.pioneers.tech.kinetics.KineticNetworks;
import fr.pioneers.tech.kinetics.KineticTile;
import fr.pioneers.tech.kinetics.parts.HandCrankBlock;
import fr.pioneers.tech.kinetics.PiKinetics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 29 (retours de jeu 3e serie, 3 : performance et fluidite mecanique ; GameTests) :
 * <ul>
 *     <li>RESEAU STABLE : une manivelle tournee en continu et 8 arbres en fer : une fois lance, le reseau garde le meme objet (aucune
 *     reconstruction) et n'envoie PLUS RIEN aux clients pendant 260 ticks (avant le lot 29 : un renvoi toutes les 200 ticks, et l'angle client
 *     recalait sur l'heure du monde) ; le dernier arbre tourne a la vitesse de la manivelle (rapport 1) ;</li>
 *     <li>TRANSMISSION INCHANGEE : la manivelle lachee, le reseau ralentit (frottements) et ce vrai changement de vitesse est envoye aux
 *     clients.</li>
 *     <li>FILTRE DES BALAYAGES EXACT (reprise du §2) : {@code ScanFilter} ne dit jamais « rien » quand le balayage case par case aurait trouve
 *     (feu pose, coffre garni), sur des boites de toutes tailles autour du test ; une boite sans feu ni coffre est bien ecartee.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot29Tests {

    private PatchP105Lot29Tests() {
    }

    /** Manivelle tournee vers l'est en (1, 2, 3), 8 arbres en fer vers l'est ; rend la position absolue du dernier arbre. */
    static BlockPos line(GameTestHelper h) {
        BlockPos crank = h.absolutePos(new BlockPos(1, 2, 3));
        h.getLevel().setBlock(crank, PiKinetics.HAND_CRANK.getBlock().defaultBlockState().setValue(HandCrankBlock.FACING, Direction.EAST), 3);
        BlockState shaft = PiKinetics.IRON_SHAFT.getBlock().defaultBlockState().setValue(AxisKineticBlock.AXIS, Direction.Axis.X);
        BlockPos last = crank;
        for (int i = 1; i <= 8; i++) {
            last = crank.east(i);
            h.getLevel().setBlock(last, shaft, 3);
        }
        return last;
    }

    static HandCrankBlock.Tile crank(GameTestHelper h) {
        Gt.check(h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 2, 3))) instanceof HandCrankBlock.Tile, "manivelle posee");
        return (HandCrankBlock.Tile) h.getLevel().getBlockEntity(h.absolutePos(new BlockPos(1, 2, 3)));
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_29_stable", timeoutTicks = 520)
    public static void p105_29_reseau_stable_sans_envoi(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos last = line(h);
        crank(h).crank(2000);
        h.runAfterDelay(200, () -> {
            Gt.check(h.getLevel().getBlockEntity(last) instanceof KineticTile, "dernier arbre pose");
            KineticTile tail = (KineticTile) h.getLevel().getBlockEntity(last);
            KineticNetwork net = KineticNetworks.network(h.getLevel(), last);
            Gt.check(net != null && net.size() == 9, "un reseau de 9 pieces");
            double v = tail.getSpeed();
            long sent = tail.syncedAt();
            Gt.check(v > 1, "le reseau tourne (" + v + " tr/min)");
            Gt.check(Math.abs(v - crank(h).getSpeed()) < 1e-6, "rapport 1 : le dernier arbre tourne a la vitesse de la manivelle");
            h.runAfterDelay(260, () -> {
                Gt.check(KineticNetworks.network(h.getLevel(), last) == net, "aucune reconstruction du reseau en regime stable");
                Gt.check(Math.abs(tail.getSpeed() - v) <= 0.01 * v, "vitesse stable");
                Gt.equal(sent, tail.syncedAt(), "aucun envoi aux clients en 260 ticks de regime stable (instant du dernier envoi)");
                s.done();
            });
        });
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_29_scan", timeoutTicks = 100)
    public static void p105_29_filtre_des_balayages_exact(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        net.minecraft.server.level.ServerLevel level = h.getLevel();
        BlockPos fire = h.absolutePos(new BlockPos(2, 2, 2)), chest = h.absolutePos(new BlockPos(4, 2, 2));
        level.setBlock(fire, net.minecraft.world.level.block.Blocks.CAMPFIRE.defaultBlockState(), 3);
        level.setBlock(chest, net.minecraft.world.level.block.Blocks.CHEST.defaultBlockState(), 3);
        Gt.check(level.getBlockEntity(chest) instanceof net.minecraft.world.level.block.entity.ChestBlockEntity c && c.isEmpty(), "coffre pose");
        ((net.minecraft.world.level.block.entity.ChestBlockEntity) level.getBlockEntity(chest)).setItem(0, new net.minecraft.world.item.ItemStack(
              net.minecraft.world.item.Items.BREAD));
        java.util.function.Predicate<BlockState> lit = fr.pioneers.world.wild.GrayWolf::fire;
        java.util.function.Predicate<net.minecraft.world.level.block.entity.BlockEntity> full =
              t -> t instanceof net.minecraft.world.Container ct && !ct.isEmpty();
        net.minecraft.util.RandomSource rnd = net.minecraft.util.RandomSource.create(29);
        int found = 0;
        for (int i = 0; i < 200; i++) {
            BlockPos a = fire.offset(rnd.nextInt(41) - 20, rnd.nextInt(9) - 4, rnd.nextInt(41) - 20);
            BlockPos b = a.offset(rnd.nextInt(13), rnd.nextInt(7), rnd.nextInt(13));
            boolean any = false, anyTile = false;
            for (BlockPos p : BlockPos.betweenClosed(a, b)) {
                any |= lit.test(level.getBlockState(p));
                anyTile |= full.test(level.getBlockEntity(p));
            }
            found += any ? 1 : 0;
            Gt.check(!any || fr.pioneers.core.block.ScanFilter.mayContain(level, a, b, lit), "feu trouve case par case : le filtre le voit (" + a + ")");
            Gt.check(!anyTile || fr.pioneers.core.block.ScanFilter.mayHaveTile(level, a, b, full), "coffre garni trouve : le filtre le voit (" + a + ")");
        }
        Gt.check(fr.pioneers.core.block.ScanFilter.mayContain(level, fire, fire, lit), "feu de camp allume vu");
        Gt.check(fr.pioneers.core.block.ScanFilter.mayHaveTile(level, chest, chest, full), "coffre garni vu");
        BlockPos sky = fire.above(40);
        Gt.check(!fr.pioneers.core.block.ScanFilter.mayContain(level, sky, sky.offset(4, 2, 4), lit), "ciel sans feu : ecarte");
        Gt.check(!fr.pioneers.core.block.ScanFilter.mayHaveTile(level, sky, sky.offset(4, 2, 4), full), "ciel sans coffre : ecarte");
        Gt.check(found > 0, "des boites contenaient le feu");
        s.done();
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_29_stop", timeoutTicks = 300)
    public static void p105_29_ralentissement_envoye(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos last = line(h);
        HandCrankBlock.Tile crank = crank(h);
        crank.crank(100);
        h.runAfterDelay(80, () -> {
            KineticTile tail = (KineticTile) h.getLevel().getBlockEntity(last);
            Gt.check(tail != null && tail.getSpeed() > 1, "le reseau tourne");
            long sent = tail.syncedAt();
            double v = tail.getSpeed();
            h.succeedWhen(() -> {
                Gt.check(tail.getSpeed() < 0.9 * v, "manivelle lachee : le reseau ralentit");
                Gt.check(tail.syncedAt() > sent, "le ralentissement est envoye aux clients");
                s.done();
            });
        });
    }
}
