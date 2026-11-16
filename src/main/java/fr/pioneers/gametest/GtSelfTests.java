package fr.pioneers.gametest;

import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.6 : l'outillage des GameTests lui-meme. Un rappel planifie qui planifie a son tour (runAfterDelay, onEachTick, failIfEver) ajoutait
 * des echeances pendant le parcours de la table de {@code GameTestInfo.tickInternal} et faisait planter le serveur de tests ;
 * {@link GtTickMap} parcourt une copie.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class GtSelfTests {

    private GtSelfTests() {
    }

    /** Planifications imbriquees : onEachTick (une echeance par tick restant) et runAfterDelay depuis des rappels, sur plusieurs etages. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_gt_self", timeoutTicks = 200)
    public static void gt_planification_imbriquee(GameTestHelper h) {
        Gt.begin(h);
        int[] ticks = new int[1];
        int[] chained = new int[1];
        h.runAfterDelay(1, () -> {
            h.onEachTick(() -> ticks[0]++);
            for (int i = 0; i < 40; i++) {
                h.runAfterDelay(1 + i % 5, () -> h.runAfterDelay(1, () -> chained[0]++));
            }
        });
        h.succeedWhen(() -> {
            Gt.check(ticks[0] >= 20, "onEachTick planifie depuis un rappel : " + ticks[0] + " ticks");
            Gt.equal(40, chained[0], "rappels planifies sur deux etages depuis un rappel");
        });
    }
}
