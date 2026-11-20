package fr.pioneers.gametest;

import fr.pioneers.core.common.projector.ProjectedStructure;
import fr.pioneers.space.giant.GasCollector;
import fr.pioneers.space.mega.GeneratedMegastructures;
import fr.pioneers.space.mining.SpaceDrill;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.IndustrialBlueprint;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.7 « Plus loin et au-dela » : les multiblocs inedits de la MAJ (hors ports des megastructures) : chacun a son controleur, et ceux
 * qui travaillent par recettes (tous sauf la foreuse spatiale et le collecteur de gaz) en ont au moins une.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot13Tests {

    private PatchP107Lot13Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_13_multis", timeoutTicks = 20)
    public static void p107_13_multiblocs_inedits(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(GeneratedMegastructures.MACHINES.length >= 15, "multiblocs inedits : " + GeneratedMegastructures.MACHINES.length);
        for (String key : GeneratedMegastructures.MACHINES) {
            IndustryMachine m = IndustryMachine.byKey(key);
            Gt.check(m != null && PiIndustry.controller(m) != null, "multibloc " + key + " sans controleur");
            if (!SpaceDrill.is(key) && !GasCollector.is(key)) {
                Gt.check(!IndustryRecipes.of(h.getLevel(), m).isEmpty(), "multibloc " + key + " sans recette");
            }
        }
        s.done();
    }

    /**
     * 1.0.7 (« ciel grandiose », retour de Guillaume du 20/11/2026) : les gabarits SCULPTES des multiblocs en amont se forment, l'un apres
     * l'autre, batis d'apres leur plan au tier MKV.
     */
    @GameTest(template = Gt.TALL, batch = "pioneers_p107_13_sculpt", timeoutTicks = 600)
    public static void p107_13_gabarits_sculptes_se_forment(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        List<String> keys = new ArrayList<>();
        for (String key : GeneratedMegastructures.MACHINES) {
            if (!SpaceDrill.is(key) && !GasCollector.is(key)) {
                keys.add(key);
            }
        }
        Gt.check(keys.size() >= 13, "multiblocs sculptes : " + keys.size());
        step(h, s, keys, 0);
    }

    private static void step(GameTestHelper h, Gt.Scope s, List<String> keys, int i) {
        if (i == keys.size()) {
            s.done();
            return;
        }
        IndustryMachine m = IndustryMachine.byKey(keys.get(i));
        ControllerTile tile = PatchP107Lot4Tests.build(h, m, true);
        h.runAfterDelay(5, () -> {
            Gt.check(tile.formed(), "gabarit sculpte " + keys.get(i) + " : la structure ne se forme pas (" + tile.guiValue(5) + ")");
            clear(h, m);
            h.runAfterDelay(2, () -> step(h, s, keys, i + 1));
        });
    }

    /** Vide les cases du plan (meme repere que {@link PatchP107Lot4Tests#build}). */
    private static void clear(GameTestHelper h, IndustryMachine m) {
        Map<BlockPos, ProjectedStructure.Ghost> plan = IndustrialBlueprint.of(m).layout(0, BlockPos.ZERO, Direction.SOUTH);
        int minX = 0, minY = 0, minZ = 0;
        for (BlockPos p : plan.keySet()) {
            minX = Math.min(minX, p.getX());
            minY = Math.min(minY, p.getY());
            minZ = Math.min(minZ, p.getZ());
        }
        BlockPos c = new BlockPos(1 - minX, 1 - minY, 1 - minZ);
        for (BlockPos p : plan.keySet()) {
            h.setBlock(c.offset(p), Blocks.AIR.defaultBlockState());
        }
    }
}
