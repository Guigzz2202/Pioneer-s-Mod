package fr.pioneers.gametest;

import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem.Sector;
import fr.pioneers.space.environment.GeneratedFarBlocks;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.mining.MiningVein;
import fr.pioneers.space.module.RocketModule;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * 1.0.7 « Plus loin et au-dela », lot 11 : les trois systemes lointains (pulsar Lich, etoile de Van Maanen, trou noir Gaia BH1) : neuf
 * planetes a dimension (chaque monde charge et genere son premier tronçon, avec un sol), atteintes seulement a la portee 14 (fusee de
 * tier 13 et propulseur a matiere noire), un filon et deux plantes par planete, un metal par systeme.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP107Lot11Tests {

    private static final Body[] FAR = {Body.DRAUGR, Body.POLTERGEIST, Body.PHOBETOR, Body.VAN_MAANEN_B, Body.VAN_MAANEN_C, Body.VAN_MAANEN_D,
                                       Body.GAIA_BH1_B, Body.GAIA_BH1_C, Body.GAIA_BH1_D};

    private PatchP107Lot11Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_11_far", timeoutTicks = 400)
    public static void p107_11_systemes_lointains(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(GeneratedFarBlocks.NATURAL.size() >= 27, "blocs naturels des planetes lointaines : " + GeneratedFarBlocks.NATURAL.size());
        Gt.check(RocketModule.DARK_MATTER_DRIVE.maxStack == 1, "propulseur a matiere noire");
        for (Sector sector : new Sector[]{Sector.LICH, Sector.VAN_MAANEN, Sector.GAIA_BH1}) {
            Gt.check(sector.far() && sector.requiredTier == SolarSystem.DARK_DRIVE_LEVEL, sector + " : systeme lointain, portee 14");
            Gt.check(sector.root().isStar(), sector + " : astre central");
        }
        for (Body body : FAR) {
            Gt.check(body.sector().far() && body.requiredTier() == SolarSystem.DARK_DRIVE_LEVEL, body + " : portee 14");
            //depuis TRAPPIST-1e : rien a la portee 13 (fusee seule), atteint a la portee 14 (propulseur)
            Gt.check(!body.isReachableFrom(fr.pioneers.space.PiDimensions.TRAPPIST_1E, 0, 13), body + " : atteint sans propulseur");
            Gt.check(body.isReachableFrom(fr.pioneers.space.PiDimensions.TRAPPIST_1E, 0, SolarSystem.DARK_DRIVE_LEVEL), body + " : hors de portee");
            ServerLevel level = h.getLevel().getServer().getLevel(body.dimension());
            Gt.check(level != null, body + " : dimension absente");
            ChunkAccess chunk = level.getChunk(0, 0);
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE_WG, 8, 8);
            Gt.check(top > level.getMinBuildHeight() + 1, body + " : premier tronçon sans sol (" + top + ")");
            Gt.check(SolarSystem.fromDimension(level.dimension()) == body, body + " : astre de la dimension");
            int plants = 0;
            for (SpacePlant plant : SpacePlant.values()) {
                plants += plant.planet.name().equals(body.name()) ? 1 : 0;
            }
            Gt.equal(2, plants, body + " : plantes");
            boolean vein = false;
            for (MiningVein v : MiningVein.values()) {
                vein |= v.body.name().equals(body.name());
            }
            Gt.check(vein, body + " : aucun filon");
            Gt.check(!level.getBlockState(new BlockPos(8, top - 1, 8)).isAir(), body + " : sol d'air");
        }
        for (MiningMaterial m : new MiningMaterial[]{MiningMaterial.PULSARITE, MiningMaterial.MAANENITE, MiningMaterial.HORIZITE}) {
            Gt.check(m.body.sector().far(), m + " : metal d'un systeme lointain");
        }
        s.done();
    }
}
