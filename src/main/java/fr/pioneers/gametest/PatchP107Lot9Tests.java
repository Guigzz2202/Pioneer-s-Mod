package fr.pioneers.gametest;

import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.mega.MegaGate;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.station.OrbitalStations;
import fr.pioneers.tech.industry.IndustryMachine;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.recipe.IndustryRecipes;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.7 « Plus loin et au-dela », lot 9 (Pluton et le Soleil) :
 * <ul>
 *     <li>le Coeur Froid sur Pluton et l'Essaim du Soleil : ports, trois recettes uniques, composants gates ; la liste de l'Essaim demande
 *     la cible isotopique ultra-pure (Anneau-Rotor) et le miroir actif heliothermique (Diademe) ;</li>
 *     <li>le Soleil n'a pas de surface : l'Essaim se livre et ses ports se batissent dans la station du Soleil (dimension des stations),
 *     jamais dans celle d'un astre qui a sa surface (Mars) ; hors de la station, son port refuse.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot9Tests {

    private PatchP107Lot9Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_9_data", timeoutTicks = 20)
    public static void p107_9_coeur_froid_et_essaim(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        for (Body body : new Body[]{Body.PLUTO, Body.SUN}) {
            int i = Megastructures.byBody(body);
            Gt.check(i >= 0, "aucune megastructure pour " + body);
            IndustryMachine port = IndustryMachine.byKey(Megastructures.port(i));
            Gt.check(port != null && IndustryRecipes.of(h.getLevel(), port).size() >= 3, "recettes du port de " + body);
            Gt.equal(3, Megastructures.gated(i).length, "composants gates de " + body);
        }
        int swarm = Megastructures.byBody(Body.SUN);
        boolean target = false, mirror = false;
        for (int k = 0; k < Megastructures.ingredientCount(swarm); k++) {
            String id = net.minecraftforge.registries.ForgeRegistries.ITEMS.getKey(Megastructures.ingredient(swarm, k)).toString();
            target |= id.equals("pioneers:ultrapure_isotopic_target");
            mirror |= id.equals("pioneers:active_heliothermal_mirror");
        }
        Gt.check(target && mirror, "Essaim : cible isotopique (Anneau-Rotor) et miroir actif (Diademe) exiges");
        s.done();
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_9_station", timeoutTicks = 20)
    public static void p107_9_essaim_dans_la_station_du_soleil(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel orbit = h.getLevel().getServer().getLevel(PiDimensions.ORBIT);
        Gt.check(orbit != null, "dimension des stations absente");
        int swarm = Megastructures.byBody(Body.SUN);
        Gt.equal(swarm, Megastructures.here(orbit, OrbitalStations.origin(Body.SUN)), "station du Soleil : l'Essaim");
        Gt.equal(-1, Megastructures.here(orbit, OrbitalStations.origin(Body.MARS)), "station de Mars : aucune megastructure (Mars a sa surface)");
        Gt.equal(ControllerTile.MEGA_LOCKED, MegaGate.status(h.getLevel(), h.absolutePos(new net.minecraft.core.BlockPos(1, 1, 1)), swarm, null),
              "port de l'Essaim hors de la station du Soleil : refuse");
        s.done();
    }
}
