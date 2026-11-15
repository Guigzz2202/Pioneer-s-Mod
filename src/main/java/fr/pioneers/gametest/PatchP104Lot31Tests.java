package fr.pioneers.gametest;

import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.NbtRoles;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.4-beta (lot 31, GameTests) : retour de Guillaume du 09/11 12 h 12 (« l'interface de la salle blanche n'embarque pas de tier »). Les
 * quatre machines de salle blanche (graveur optique, usine de semi-conducteurs, centre de donnees, ordinateur quantique) ouvrent
 * l'habillage {@code cleanroom} (interface claire et sans tier, cote client) quel que soit le tier ; une machine ordinaire garde l'habillage
 * de son tier. Le niveau FIXE de la salle blanche reste celui du gameplay (vitesse, parallele : inchange au lot 31).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP104Lot31Tests {

    private PatchP104Lot31Tests() {
    }

    /** Cles des quatre structures de salle blanche (gabarits a parois W). */
    private static final String[] CLEANROOMS = {"photolithograph", "semiconductor_fab", "data_center", "quantum_computer"};

    @GameTest(template = Gt.SMALL, batch = "pioneers_p104_31_skins", timeoutTicks = 40)
    public static void p104_31_habillage_salle_blanche_sans_tier(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (String key : CLEANROOMS) {
            GeneratedNbtShapes.Shape shape = GeneratedNbtShapes.of(key);
            Gt.check(shape != null && shape.cleanroom(), key + " : pas une structure de salle blanche");
            Gt.check(shape.fixedTier() >= 0, key + " : salle blanche sans niveau fixe");
            for (int tier = 0; tier < 4; tier++) {
                //Quel que soit le tier (y compris celui peint cote client), l'habillage reste la salle blanche : aucune couleur de tier
                Gt.equal(NbtRoles.CLEANROOM_GUI, NbtRoles.guiTier(key, tier, null), key + " : habillage au tier " + tier);
            }
        }
        Gt.equal("advanced", NbtRoles.guiTier("crusher", 1, null), "broyeur MKII : habillage du tier");
        Gt.equal("ultimate", NbtRoles.guiTier("crusher", 3, null), "broyeur MKIV : habillage du tier");
        s.done();
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p104_31_engraver", timeoutTicks = 200)
    public static void p104_31_graveur_salle_blanche_niveau_fixe(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.PHOTOLITHOGRAPH);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "graveur optique : le plan ne se forme pas");
            Gt.equal(NbtRoles.CLEANROOM_GUI, tile.guiTier(), "graveur optique : habillage de salle blanche");
            //Gameplay inchange au lot 31 : le niveau fixe de la salle blanche regle toujours le parallele (question posee a Guillaume)
            Gt.equal(GeneratedNbtShapes.of("photolithograph").fixedTier(), tile.tier(), "graveur optique : niveau fixe de la salle blanche");
            s.done();
        });
    }
}
