package fr.pioneers.gametest;

import fr.pioneers.space.giant.GasCollector;
import fr.pioneers.space.mega.GeneratedMegastructures;
import fr.pioneers.space.mining.SpaceDrill;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
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
}
