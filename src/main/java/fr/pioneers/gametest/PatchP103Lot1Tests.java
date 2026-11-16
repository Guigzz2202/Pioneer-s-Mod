package fr.pioneers.gametest;

import fr.pioneers.society.economy.EconomyBlocks;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.crow.CrowSpawner;
import fr.pioneers.farm.fish.PiFish;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.3-beta, lot 1 (GameTests) : corbeaux un peu plus rares (chance x 0,7), caisse de Colporteur qui ne masque plus ses voisins,
 * plafonds de reproduction des poissons (zone et etendue d'eau).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP103Lot1Tests {

    private PatchP103Lot1Tests() {
    }

    private static boolean near(float a, float b) {
        return Math.abs(a - b) < 1e-5F;
    }

    // ================================================================== corbeaux
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_crows", timeoutTicks = 20)
    public static void p103_corbeaux_plus_rares(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(400, CrowSpawner.PERIOD, "passage toutes les 20 s (inchange)");
        Gt.check(near(0.21F, CrowSpawner.chance(0)), "chance de base 0,21 (0,3 en 1.0.2) : " + CrowSpawner.chance(0));
        for (int crops : new int[]{6, 20, 40, 60}) {
            float old = Math.min(0.75F, 0.3F + 0.006F * crops), now = CrowSpawner.chance(crops);
            Gt.check(near(old * 0.7F, now), crops + " cultures : 30 % de moins qu'en 1.0.2 (" + old + " -> " + now + ")");
        }
        Gt.check(near(0.525F, CrowSpawner.chance(500)), "plafond 0,525 (0,75 en 1.0.2)");
        Gt.check(CrowSpawner.chance(60) > CrowSpawner.chance(6), "un grand champ attire toujours plus");
        s.done();
    }

    /** 1.0.3-beta (correctif, Guillaume : « un quota, 1 corbeau toutes les 20 cases de plantation ») : quota de corbeaux par champ. */
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_crow_quota", timeoutTicks = 20)
    public static void p103_corbeaux_quota(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.equal(0, CrowSpawner.quota(19), "moins de 20 plantations : aucun corbeau");
        Gt.equal(1, CrowSpawner.quota(20), "20 plantations : 1 corbeau");
        Gt.equal(3, CrowSpawner.quota(79), "79 plantations : 3 corbeaux");
        Gt.equal((long) CrowSpawner.MAX_NEAR, CrowSpawner.quota(10_000), "plafond absolu");
        s.done();
    }

    // ================================================================== caisse du Colporteur
    @GameTest(template = Gt.SMALL, batch = "pioneers_p103_crate", timeoutTicks = 20)
    public static void p103_caisse_sans_trou(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        BlockPos at = new BlockPos(4, 2, 4);
        h.setBlock(at.below(), Blocks.STONE.defaultBlockState());
        h.setBlock(at.east(), Blocks.STONE.defaultBlockState());
        h.setBlock(at, EconomyBlocks.PEDDLER_CRATE.getBlock().defaultBlockState());
        BlockState st = h.getBlockState(at);
        Gt.check(!st.canOcclude(), "caisse : ne masque plus les faces de ses voisins (noOcclusion)");
        BlockPos abs = h.absolutePos(at);
        AABB box = st.getShape(h.getLevel(), abs, CollisionContext.empty()).bounds();
        Gt.check(box.minX > 0 && box.maxX < 1 && box.minZ > 0 && box.maxZ < 1 && box.maxY == 1, "caisse : forme = couvercle et montants : " + box);
        // le sol sous la caisse et le mur voisin gardent leur face tournee vers elle (avant : cachees, d'ou les trous autour de la caisse)
        BlockPos below = abs.below(), wall = abs.east();
        Gt.check(Block.shouldRenderFace(h.getLevel().getBlockState(below), h.getLevel(), below, Direction.UP, abs),
              "le dessus du sol sous la caisse est dessine");
        Gt.check(Block.shouldRenderFace(h.getLevel().getBlockState(wall), h.getLevel(), wall, Direction.WEST, abs),
              "la face du mur tournee vers la caisse est dessinee");
        s.done();
    }

    // ================================================================== reproduction des poissons
    /** Bassin de pierre : eau de (x0, y0, z0) a (x1, y1, z1) inclus (coordonnees du gabarit). */
    private static void basin(GameTestHelper h, int x0, int y0, int z0, int x1, int y1, int z1) {
        for (int x = x0 - 1; x <= x1 + 1; x++) {
            for (int z = z0 - 1; z <= z1 + 1; z++) {
                for (int y = y0 - 1; y <= y1; y++) {
                    boolean inside = x >= x0 && x <= x1 && z >= z0 && z <= z1 && y >= y0;
                    h.setBlock(new BlockPos(x, y, z), inside ? Blocks.WATER.defaultBlockState() : Blocks.STONE.defaultBlockState());
                }
            }
        }
    }

    private static PiFish fish(GameTestHelper h, int x, int y, int z) {
        PiFish f = h.spawn(PiFishing.speciesType(0), new BlockPos(x, y, z));
        f.setNoAi(true);
        return f;
    }

    @GameTest(template = Gt.BORDER, batch = "pioneers_p103_fish", timeoutTicks = 40)
    public static void p103_poissons_plafonds(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        Gt.check(PiFish.SPAWN_CAP < 9 && PiFish.BREED_CAP < PiFish.SPAWN_CAP, "plafonds de zone plus bas qu'en 1.0.2 (9) : "
                                                                              + PiFish.SPAWN_CAP + " / " + PiFish.BREED_CAP);
        Gt.equal(48000, PiFish.ROE_CHANCE, "ponte deux fois plus rare (1 sur 48 000 ticks au lieu de 24 000)");
        Gt.equal(1, PiFish.bodyCap(20, PiFish.BREED_CAP), "mare de 20 blocs : 1 poisson");
        Gt.equal(2, PiFish.bodyCap(64, PiFish.BREED_CAP), "bassin de 64 blocs : 2 poissons");
        Gt.equal(PiFish.BREED_CAP, PiFish.bodyCap(5000, PiFish.BREED_CAP), "grande etendue : plafond de zone");
        // mare de 4 x 2 x 4 = 32 blocs d'eau (1 poisson), et un lac de 14 x 2 x 10 = 280 blocs a cote
        basin(h, 1, 2, 1, 4, 3, 4);
        basin(h, 5, 2, 7, 18, 3, 16);
        h.runAfterDelay(2, () -> {
            BlockPos pond = h.absolutePos(new BlockPos(2, 2, 2)), lake = h.absolutePos(new BlockPos(10, 2, 12));
            Gt.equal(32, PiFish.waterBody(h.getLevel(), pond, PiFish.BODY_SCAN).size(), "mare : 32 blocs d'eau relies");
            Gt.check(PiFish.waterBody(h.getLevel(), lake, PiFish.BODY_SCAN).size() >= PiFish.BODY_SCAN, "lac : grande etendue");
            Gt.check(!PiFish.crowded(h.getLevel(), pond, PiFish.BREED_CAP), "mare vide : un poisson peut y naitre");
            fish(h, 2, 2, 2);
            Gt.later(h, 2, () -> {
                Gt.check(PiFish.crowded(h.getLevel(), pond, PiFish.BREED_CAP), "mare de 32 blocs : pleine avec 1 poisson (avant : 9)");
                Gt.check(PiFish.crowded(h.getLevel(), pond, PiFish.SPAWN_CAP), "mare : plus d'apparition naturelle non plus");
                Gt.check(!PiFish.crowded(h.getLevel(), lake, PiFish.BREED_CAP), "le lac voisin n'est pas compte dans la mare");
                for (int i = 0; i < PiFish.BREED_CAP; i++) {
                    fish(h, 8 + i, 2, 12);
                }
                Gt.later(h, 2, () -> {
                    Gt.check(PiFish.crowded(h.getLevel(), lake, PiFish.BREED_CAP), "lac : plus de ponte ni d'eclosion a 4 poissons");
                    Gt.check(PiFish.crowded(h.getLevel(), lake, PiFish.BREED_CAP) && !PiFish.crowded(h.getLevel(), lake, PiFish.SPAWN_CAP + 10),
                          "le plafond de zone vient du nombre de poissons, pas de la taille du lac");
                    s.done();
                });
            });
        });
    }
}
