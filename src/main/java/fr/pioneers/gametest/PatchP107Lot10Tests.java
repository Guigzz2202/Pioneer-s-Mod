package fr.pioneers.gametest;

import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.station.OrbitalStations;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.PiIndustry;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.7 « Plus loin et au-dela », lot 10 (la station de matiere noire) :
 * <ul>
 *     <li>l'Observatoire du Halo : megastructure de STATION (station de la Terre), jamais a la surface de la Terre ; port, trois recettes
 *     uniques, composants gates (carte de densite, capsule, moteur a matiere noire) ;</li>
 *     <li>les multiblocs de la station, en amont : detecteur de halo (reseaux de capteurs) et lentille gravitationnelle (pieges a matiere
 *     noire), avec leurs recettes.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot10Tests {

    private PatchP107Lot10Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_10_data", timeoutTicks = 20)
    public static void p107_10_observatoire_du_halo(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        int halo = Megastructures.byBody(Body.EARTH);
        Gt.check(halo >= 0 && Megastructures.station(halo), "Observatoire du Halo : megastructure de station de la Terre");
        IndustryMachine port = IndustryMachine.byKey(Megastructures.port(halo));
        Gt.check(port != null && IndustryRecipes.of(h.getLevel(), port).size() >= 3, "recettes de l'Observatoire du Halo");
        Gt.equal(3, Megastructures.gated(halo).length, "composants gates de l'Observatoire");
        for (String key : new String[]{"halo_detector", "gravitational_lens"}) {
            IndustryMachine m = IndustryMachine.byKey(key);
            Gt.check(m != null && PiIndustry.controller(m) != null && !IndustryRecipes.of(h.getLevel(), m).isEmpty(), "multibloc " + key);
        }
        //surface de la Terre : rien ; station de la Terre : l'Observatoire
        Gt.equal(-1, Megastructures.here(h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1))), "surface de la Terre : rien");
        ServerLevel orbit = h.getLevel().getServer().getLevel(PiDimensions.ORBIT);
        Gt.check(orbit != null, "dimension des stations absente");
        Gt.equal(halo, Megastructures.here(orbit, OrbitalStations.origin(Body.EARTH)), "station de la Terre : l'Observatoire du Halo");
        s.done();
    }
}
