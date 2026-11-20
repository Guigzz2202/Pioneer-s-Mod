package fr.pioneers.gametest;

import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem.Sector;
import fr.pioneers.space.environment.GeneratedFarBlocks;
import fr.pioneers.space.farm.SpacePlant;
import fr.pioneers.space.mining.MiningMaterial;
import fr.pioneers.space.mining.MiningVein;
import fr.pioneers.space.mega.Megastructures;
import fr.pioneers.space.module.RocketModule;
import fr.pioneers.tech.industry.IndustryMachine;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.registries.ForgeRegistries;
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
            //hauteur finale (les creusements passent apres la carte de hauteur du bruit, _WG)
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8) + 1;
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
            Gt.check(!level.getBlockState(new BlockPos(8, top - 1, 8)).isAir(), body + " : sol d'air a " + (top - 1));
        }
        for (MiningMaterial m : new MiningMaterial[]{MiningMaterial.PULSARITE, MiningMaterial.MAANENITE, MiningMaterial.HORIZITE}) {
            Gt.check(m.body.sector().far(), m + " : metal d'un systeme lointain");
        }
        s.done();
    }

    /**
     * Retour de Guillaume (20/11) : un monde « complètement vivable avec faune et flore comme sur terre » dans chaque systeme lointain
     * (Fylgja, Van Maanen e, Gaia BH1 e) : air respirable, relief de la Terre (sol a la surface), biomes de la Terre ou vivent des animaux,
     * aucun biome a village ; atteints comme les autres planetes du systeme (portee 14).
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_11_habitable", timeoutTicks = 600)
    public static void p107_11_mondes_habitables(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        net.minecraft.core.Registry<net.minecraft.world.level.biome.Biome> biomes = h.getLevel().registryAccess()
              .registryOrThrow(net.minecraft.core.registries.Registries.BIOME);
        for (Body body : new Body[]{Body.FYLGJA, Body.VAN_MAANEN_E, Body.GAIA_BH1_E}) {
            Gt.check(body.sector().far() && body.requiredTier() == SolarSystem.DARK_DRIVE_LEVEL, body + " : portee 14");
            ServerLevel level = h.getLevel().getServer().getLevel(body.dimension());
            Gt.check(level != null, body + " : dimension absente");
            Gt.check(fr.pioneers.space.environment.Environments.get(level).breathable(), body + " : air irrespirable");
            Gt.check(SolarSystem.fromDimension(level.dimension()) == body, body + " : astre de la dimension");
            boolean animals = false, village = false;
            for (net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> b : level.getChunkSource().getGenerator().getBiomeSource()
                  .possibleBiomes()) {
                animals |= !b.value().getMobSettings().getMobs(net.minecraft.world.entity.MobCategory.CREATURE).isEmpty();
                village |= b.is(net.minecraft.tags.BiomeTags.HAS_VILLAGE_PLAINS) || b.is(net.minecraft.tags.BiomeTags.HAS_PILLAGER_OUTPOST);
                Gt.check(b.unwrapKey().map(k -> k.location().getNamespace().equals("minecraft")).orElse(false), body + " : biome hors Terre");
            }
            Gt.check(animals, body + " : aucun animal");
            Gt.check(!village, body + " : biome a village ou avant-poste");
            ChunkAccess chunk = level.getChunk(0, 0);
            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, 8, 8);
            Gt.check(top > 40, body + " : relief absent (" + top + ")");
        }
        s.done();
    }

    /**
     * Retour de Guillaume (20/11, capture sur Gaia BH1 b) : des blocs de liquide « suspendus » (cubes d'eau dresses dans le vide) sur les
     * planetes lointaines. Aucun bloc source de liquide de la surface (au-dessus de la couche 40) ne touche le vide, sur un cote ou dessous,
     * dans 7 x 7 tronçons generes de chaque planete.
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_11_fluids", timeoutTicks = 1200)
    public static void p107_11_aucun_liquide_suspendu(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        StringBuilder bad = new StringBuilder();
        for (Body body : FAR) {
            ServerLevel level = h.getLevel().getServer().getLevel(body.dimension());
            int count = 0;
            BlockPos first = null;
            BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos(), side = new BlockPos.MutableBlockPos();
            for (int cx = -3; cx <= 3; cx++) {
                for (int cz = -3; cz <= 3; cz++) {
                    ChunkAccess chunk = level.getChunk(cx, cz);
                    for (int dx = 0; dx < 16; dx++) {
                        for (int dz = 0; dz < 16; dz++) {
                            int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, dx, dz);
                            for (int y = top; y >= 40; y--) {
                                pos.set(cx * 16 + dx, y, cz * 16 + dz);
                                net.minecraft.world.level.material.FluidState f = level.getFluidState(pos);
                                if (f.isEmpty() || !f.isSource()) {
                                    continue;
                                }
                                boolean open = false;
                                for (net.minecraft.core.Direction d : new net.minecraft.core.Direction[]{net.minecraft.core.Direction.DOWN,
                                      net.minecraft.core.Direction.NORTH, net.minecraft.core.Direction.SOUTH, net.minecraft.core.Direction.EAST,
                                      net.minecraft.core.Direction.WEST}) {
                                    open |= level.getBlockState(side.setWithOffset(pos, d)).isAir();
                                }
                                if (open) {
                                    count++;
                                    first = first == null ? pos.immutable() : first;
                                }
                            }
                        }
                    }
                }
            }
            if (count > 0) {
                bad.append(body).append(' ').append(count).append(" (").append(first.toShortString()).append(") ");
            }
        }
        Gt.check(bad.length() == 0, "liquides suspendus : " + bad);
        s.done();
    }

    /**
     * Lot 11 (suite) : les megastructures des systemes lointains (Cage du Pulsar sur Poltergeist, Puits de Cristal sur Van Maanen b,
     * Anneau de Penrose sur Gaia BH1 b) : leur astre, leur port, leurs trois composants gates, leur ciel en 3D et le multibloc en amont de
     * chacune. Sortes du ciel : index de T:au_dela/megastructures.py SKY_KINDS (11 cage, 12 well, 13 penrose ; le code commun ne cite pas
     * les classes du client).
     */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p107_11_megas", timeoutTicks = 40)
    public static void p107_11_megastructures_lointaines(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Object[][] want = {{Body.POLTERGEIST, "pulsar_cage", 11, "pulsar_beam_collector"},
                           {Body.VAN_MAANEN_B, "crystal_well", 12, "carbon_crystallizer"},
                           {Body.GAIA_BH1_B, "penrose_ring", 13, "ergosphere_mass_driver"}};
        for (Object[] w : want) {
            int i = Megastructures.byBody((Body) w[0]);
            Gt.check(i >= 0 && w[1].equals(Megastructures.id(i)), w[0] + " : megastructure " + (i < 0 ? "absente" : Megastructures.id(i)));
            Gt.check(IndustryMachine.byKey(Megastructures.port(i)) != null, w[1] + " : port absent");
            Gt.equal(3, Megastructures.gated(i).length, w[1] + " : composants gates");
            for (String g : Megastructures.gated(i)) {
                Gt.check(ForgeRegistries.ITEMS.containsKey(new net.minecraft.resources.ResourceLocation(g)), w[1] + " : composant gate absent " + g);
            }
            Gt.equal((int) (Integer) w[2], Megastructures.skyKind(i), w[1] + " : ciel");
            Gt.check(IndustryMachine.byKey((String) w[3]) != null, w[1] + " : multibloc en amont absent " + w[3]);
        }
        s.done();
    }
}
