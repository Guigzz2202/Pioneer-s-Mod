package fr.pioneers.gametest;

import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.BigInterior;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtModels;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.6 (Guillaume, 17/11/2026 : les structures de la Decoupeuse et du Calcinateur faites dans « Nouveau monde », animees ; « la decoupeuse
 * est censee representer un bras d'imprimante 3D qui decoupe en gros ») :
 * <ul>
 *     <li>la Decoupeuse se forme sur sa structure NBT (5 x 5 x 5, charpente de cadres) et dessine son PORTIQUE DE DECOUPE dans le volume de
 *     travail ouvert sous la charpente (3 x 3 x 3 cases d'air, aucune traversee de bloc) ;</li>
 *     <li>le Calcinateur se forme sur sa structure NBT (four tubulaire 3 x 3 x 5) et ses bobines de chauffe (lettre « H ») rougeoient en
 *     marche (lueur du controleur, J:tech/industry/client/big/NbtAnimations).</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP106AnimationsTests {

    private PatchP106AnimationsTests() {
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p106_decoupeuse_portique", timeoutTicks = 200)
    public static void p106_decoupeuse_structure_nbt_et_portique(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        GeneratedNbtShapes.Shape shape = GeneratedNbtShapes.of("gear_hobber");
        Gt.check(shape != null && shape.sx() == 5 && shape.sy() == 5 && shape.sz() == 5, "Decoupeuse : structure NBT de 5 x 5 x 5");
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.GEAR_HOBBER);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "Decoupeuse : la structure de Guillaume ne se forme pas (" + tile.guiValue(5) + ")");
            BigInterior.View v = tile.bigView();
            Gt.check(v != null && v.kind() == BigInterior.Kind.GANTRY, "Decoupeuse : pas de portique de decoupe dessine");
            int cells = 0;
            for (BlockPos p : BlockPos.betweenClosed(v.min(), v.max())) {
                Gt.check(h.getLevel().getBlockState(p).isAir(), "Decoupeuse : le portique traverse un bloc en " + p);
                cells++;
            }
            Gt.equal(27, cells, "Decoupeuse : volume de travail de 3 x 3 x 3");
            s.done();
        });
    }

    @GameTest(template = Gt.TALL, batch = "pioneers_p106_calcinateur_bobines", timeoutTicks = 200)
    public static void p106_calcinateur_structure_nbt_et_bobines(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        GeneratedNbtShapes.Shape shape = GeneratedNbtShapes.of("reduction_furnace");
        Gt.check(shape != null, "Calcinateur : pas de structure NBT");
        Gt.equal("H", GeneratedNbtModels.glow("reduction_furnace"), "Calcinateur : les bobines de chauffe rougeoient");
        int coils = 0;
        for (int y = 0; y < shape.sy(); y++) {
            for (int z = 0; z < shape.sz(); z++) {
                for (int x = 0; x < shape.sx(); x++) {
                    coils += shape.at(x, y, z) == 'H' ? 1 : 0;
                }
            }
        }
        Gt.check(coils > 0, "Calcinateur : aucune bobine de chauffe dans la structure");
        BlockPos c = MultiblockApiTests.build(h, IndustryMachine.REDUCTION_FURNACE);
        ControllerTile tile = MultiblockApiTests.controller(h, c);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "Calcinateur : la structure de Guillaume ne se forme pas (" + tile.guiValue(5) + ")");
            s.done();
        });
    }
}
