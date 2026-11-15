package fr.pioneers.gametest;

import fr.pioneers.compute.ComputeCore;
import fr.pioneers.industry.IndustryMachine;
import fr.pioneers.industry.Tiers;
import fr.pioneers.industry.multi.Cleanroom;
import fr.pioneers.industry.multi.ControllerTile;
import fr.pioneers.industry.multi.GeneratedNbtShapes;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 32, GameTests) : decision de Guillaume du 09/11 (« Garder, masquer partout ») : le niveau fixe des quatre machines de
 * salle blanche reste le gameplay (parallele, puissance, vitesse, GOPS / QOPS) ; ce qui s'affiche ({@link Cleanroom}) en donne les VRAIES
 * valeurs, calculees comme le jeu, sans aucun tier.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot32Tests {

    private PatchP104Lot32Tests() {
    }

    private static final IndustryMachine[] CLEANROOMS = {IndustryMachine.PHOTOLITHOGRAPH, IndustryMachine.SEMICONDUCTOR_FAB, IndustryMachine.DATA_CENTER,
                                                         IndustryMachine.QUANTUM_COMPUTER};

    /** Vrai si une ligne (ou un de ses arguments) parle d'un tier : cle de tier, « MKI » a « MKIV ». */
    private static boolean tiered(Component c) {
        if (c.getContents() instanceof TranslatableContents t) {
            if (t.getKey().contains("tier")) {
                return true;
            }
            for (Object a : t.getArgs()) {
                if (a instanceof Component ac ? tiered(ac) : String.valueOf(a).matches(".*\\bMK(I{1,3}|IV)\\b.*")) {
                    return true;
                }
            }
        }
        for (Component s : c.getSiblings()) {
            if (tiered(s)) {
                return true;
            }
        }
        return false;
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_32_values", timeoutTicks = 40)
    public static void p104_32_valeurs_reelles_sans_tier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (IndustryMachine m : CLEANROOMS) {
            int level = GeneratedNbtShapes.of(m.key).fixedTier();
            Gt.check(Cleanroom.is(m), m.key + " : pas une salle blanche");
            Gt.equal(level, Cleanroom.level(m), m.key + " : niveau fixe");
            Gt.equal(Tiers.parallelCap(level), Cleanroom.parallel(m), m.key + " : operations a la fois = jeu");
            Gt.equal(Math.round(Tiers.power(level) * Tiers.INDUSTRIAL_ENERGY / Tiers.INDUSTRIAL_TIME), Cleanroom.opPower(m), m.key + " : FE/t = jeu");
            Gt.equal(Tiers.ticks(200, level, 0, true), Cleanroom.ticks(m, 200, 0), m.key + " : duree = jeu");
            List<Component> lines = Cleanroom.lines(m);
            Gt.check(!lines.isEmpty(), m.key + " : aucune valeur affichee");
            for (Component c : lines) {
                Gt.check(!tiered(c), m.key + " : valeur affichee avec un tier");
            }
            Gt.equal(m, Cleanroom.byController("pioneers:" + m.multiId()), m.key + " : controleur");
        }
        Gt.equal(Math.round(ComputeCore.RACK_GOPS * (1 << Cleanroom.level(IndustryMachine.DATA_CENTER))), Cleanroom.rackGops(IndustryMachine.DATA_CENTER),
              "centre de donnees : GOPS d'une baie = ComputeCore");
        Gt.equal(ComputeCore.MATRIX_FE << 2 * Cleanroom.level(IndustryMachine.QUANTUM_COMPUTER), Cleanroom.matrixPower(IndustryMachine.QUANTUM_COMPUTER),
              "ordinateur quantique : FE/t d'une matrice = ComputeCore");
        Gt.check(!Cleanroom.is(IndustryMachine.CRUSHER) && Cleanroom.lines(IndustryMachine.CRUSHER).isEmpty(), "broyeur : pas une salle blanche");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_32_engraver", timeoutTicks = 200)
    public static void p104_32_graveur_bati_capacite_fixe(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.PHOTOLITHOGRAPH);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "graveur optique : le plan ne se forme pas");
            //Gameplay inchange : niveau fixe et parallele de la structure = ce que montrent le guide, JEI et l'infobulle
            Gt.equal(Cleanroom.level(IndustryMachine.PHOTOLITHOGRAPH), tile.tier(), "graveur : niveau fixe");
            Gt.equal(Cleanroom.parallel(IndustryMachine.PHOTOLITHOGRAPH), tile.parallel(), "graveur : operations a la fois");
            s.done();
        });
    }
}
