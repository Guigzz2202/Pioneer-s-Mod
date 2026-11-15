package fr.pioneers.gametest;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.farm.PiFarm;
import fr.pioneers.farm.PiFishing;
import fr.pioneers.farm.crop.FarmCropBlock;
import fr.pioneers.farm.crow.Crow;
import fr.pioneers.farm.device.PiFarmDevices;
import fr.pioneers.farm.device.ScarecrowBlock;
import fr.pioneers.farm.fish.PiFish;
import fr.pioneers.world.fauna.FaunaCounter;
import fr.pioneers.world.fauna.FaunaData;
import fr.pioneers.world.fauna.FaunaEvents;
import fr.pioneers.world.fauna.FaunaGroup;
import fr.pioneers.world.fauna.FaunaPools;
import fr.pioneers.world.fauna.FaunaRegistry;
import fr.pioneers.world.fauna.FaunaRules;
import fr.pioneers.world.fauna.FaunaSettings;
import fr.pioneers.world.fauna.FaunaSpawner;
import fr.pioneers.world.fauna.FaunaSpecies;
import fr.pioneers.world.fauna.FaunaZones;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.ForgeEventFactory;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 4 (GameTests ; specification « Le monde vivant » §3, §8, decision 15) : animaux passifs et corbeaux persistants
 * (disparition refusee, apparition naturelle coupee ; monstres et chauves-souris intacts) ; groupe sous 25 % -> repeuplement a plus de 64
 * blocs et hors de la vue d'un joueur (rien pres de lui) ; aucun repeuplement dans un claim ni pres d'un village (sauf espece « pres des
 * constructions ») ; poissons vanilla persistants, poissons du mod sous leurs plafonds ; le corbeau pille une culture puis fuit
 * l'epouvantail.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot4Tests {

    private PatchP105Lot4Tests() {
    }

    static Event.Result despawn(ServerLevel level, Mob mob) {
        return ForgeEventFactory.canEntityDespawn(mob, level);
    }

    static <T extends Entity> T add(Gt.Scope s, EntityType<T> type, BlockPos abs) {
        T e = type.create(s.level);
        Gt.check(e != null, "creation de " + type);
        e.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(e);
        s.onCleanup(e::discard);
        return e;
    }

    /** Remet la memoire de la zone comme avant le test. */
    static void keepZone(Gt.Scope s, long zone) {
        FaunaData data = FaunaData.get(s.level);
        int[] before = data.starts(zone);
        boolean legacy = data.legacy(zone);
        s.onCleanup(() -> {
            if (before == null) {
                data.forget(zone);
            } else {
                data.put(zone, before, legacy);
            }
        });
    }

    /** Herbe sur le sol du gabarit (x, z de 2 a 17) ; rend l'aire absolue. */
    static FaunaSpawner.Area meadow(GameTestHelper h) {
        for (int x = 2; x <= 17; x++) {
            for (int z = 2; z <= 17; z++) {
                h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
        BlockPos a = h.absolutePos(new BlockPos(2, 1, 2)), b = h.absolutePos(new BlockPos(17, 1, 17));
        return new FaunaSpawner.Area(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
              Math.max(a.getZ(), b.getZ()));
    }

    /** Reglages du test : plafond du gibier au-dessus de ce qui est deja compte dans la zone, depart pour que le gibier soit sous 25 %. */
    static FaunaSettings settings(int counted) {
        int[] caps = FaunaSettings.DEFAULT.caps().clone();
        caps[FaunaGroup.GAME.ordinal()] = Math.max(caps[FaunaGroup.GAME.ordinal()], 4 * counted + 64);
        return new FaunaSettings(true, FaunaRules.PERIOD, FaunaRules.THRESHOLD, FaunaRules.PLAYER_DISTANCE, FaunaRules.VILLAGE_DISTANCE, true,
              FaunaRules.AI_DISTANCE, caps);
    }

    static int[] gameStart(int counted) {
        int[] st = new int[FaunaGroup.COUNT];
        st[FaunaGroup.GAME.ordinal()] = 4 * counted + 40;
        return st;
    }

    static FaunaSpawner.Viewer farViewer(FaunaSpawner.Area a) {
        double cx = (a.x0() + a.x1()) / 2.0, cz = (a.z0() + a.z1()) / 2.0;
        return new FaunaSpawner.Viewer(new Vec3(cx + 100, 80, cz), new Vec3(1, 0, 0));
    }

    // ------------------------------------------------------------------ 1. persistance

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_4_persist", timeoutTicks = 40)
    public static void p105_4_animal_passif_persistant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        Gt.check(FaunaSettings.current().enabled(), "faune persistante active (reglage par defaut)");
        Mob cow = add(s, EntityType.COW, h.absolutePos(new BlockPos(4, 1, 4)));
        Mob sheep = add(s, EntityType.SHEEP, h.absolutePos(new BlockPos(6, 1, 4)));
        Mob wolf = add(s, EntityType.WOLF, h.absolutePos(new BlockPos(8, 1, 4)));
        Crow crow = add(s, PiFarmDevices.CROW.get(), h.absolutePos(new BlockPos(10, 1, 4)));
        Mob zombie = add(s, EntityType.ZOMBIE, h.absolutePos(new BlockPos(4, 1, 10)));
        Mob bat = add(s, EntityType.BAT, h.absolutePos(new BlockPos(6, 3, 10)));
        for (Mob m : new Mob[]{cow, sheep, wolf, crow}) {
            Gt.equal(Event.Result.DENY, despawn(level, m), m.getType() + " : la disparition est refusee");
            Gt.check(FaunaRegistry.of(m) != null, m.getType() + " : espece de la faune");
        }
        Gt.check(!crow.removeWhenFarAway(500), "le corbeau ne disparait plus au loin");
        Gt.equal(Event.Result.DEFAULT, despawn(level, zombie), "monstre : disparition vanilla");
        Gt.equal(Event.Result.DEFAULT, despawn(level, bat), "chauve-souris : disparition vanilla (choix du lot)");
        Gt.check(FaunaEvents.blocked(EntityType.COW, MobSpawnType.NATURAL, true), "vache : plus d'apparition naturelle");
        Gt.check(FaunaEvents.blocked(EntityType.SHEEP, MobSpawnType.CHUNK_GENERATION, true), "mouton : generation vanilla remplacee (Overworld)");
        Gt.check(!FaunaEvents.blocked(EntityType.SHEEP, MobSpawnType.CHUNK_GENERATION, false), "autres dimensions : generation vanilla");
        Gt.check(!FaunaEvents.blocked(EntityType.COW, MobSpawnType.SPAWN_EGG, true) && !FaunaEvents.blocked(EntityType.COW, MobSpawnType.BREEDING,
              true), "oeufs et reproduction intacts");
        Gt.check(!FaunaEvents.blocked(EntityType.ZOMBIE, MobSpawnType.NATURAL, true) && !FaunaEvents.blocked(EntityType.BAT, MobSpawnType.NATURAL, true),
              "monstres et chauves-souris : apparition naturelle intacte");
        BlockPos grass = h.absolutePos(new BlockPos(12, 0, 12));
        level.setBlock(grass, Blocks.GRASS_BLOCK.defaultBlockState(), 3);
        Gt.check(!net.minecraft.world.entity.SpawnPlacements.checkSpawnRules(EntityType.COW, level, MobSpawnType.NATURAL, grass.above(), level.random),
              "regles du jeu : la vache ne nait plus au hasard (evenement refuse)");
        long zone = FaunaRules.zoneKey(crow.getBlockX(), crow.getBlockZ());
        Gt.check(FaunaCounter.of(level).species(zone, FaunaRegistry.byId("pioneers:crow")) >= 1, "le corbeau est compte dans sa zone");
        Gt.check(FaunaCounter.of(level).groups(zone)[FaunaGroup.GAME.ordinal()] >= 2, "vache et mouton comptes (gibier)");
        s.done();
    }

    // ------------------------------------------------------------------ 2. repeuplement

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_4_repop", timeoutTicks = 60)
    public static void p105_4_repeuplement_hors_vue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        FaunaSpawner.Area area = meadow(h);
        long zone = FaunaRules.zoneKey(area.x0(), area.z0());
        keepZone(s, zone);
        FaunaCounter.of(level).reconcile();
        int counted = FaunaCounter.of(level).groups(zone)[FaunaGroup.GAME.ordinal()];
        BlockPos mid = h.absolutePos(new BlockPos(9, 1, 9));
        Gt.check(!FaunaPools.of(level.getBiome(mid), FaunaGroup.GAME).isEmpty(), "biome du test sans gibier : " + level.getBiome(mid).unwrapKey());
        //un joueur au milieu : rien ne nait pres de lui (moins de 64 blocs)
        FaunaSpawner.Viewer inside = new FaunaSpawner.Viewer(Vec3.atCenterOf(mid).add(0, 1.2, 0), new Vec3(0, 0, 1));
        FaunaZones.Pass p0 = FaunaZones.pass(level, zone, settings(counted), new FaunaZones.Limits(List.of(inside), area), gameStart(counted));
        Gt.check(p0.planned().contains(FaunaGroup.GAME), "gibier sous 25 % : prevu au passage");
        Gt.equal(0L, (long) p0.spawned().size(), "joueur a moins de 64 blocs : aucune apparition");
        //un joueur a 100 blocs qui regarde ailleurs : un petit groupe apparait
        FaunaSpawner.Viewer far = farViewer(area);
        FaunaZones.Pass p = FaunaZones.pass(level, zone, settings(counted), new FaunaZones.Limits(List.of(far), area), gameStart(counted));
        for (Entity e : p.spawned()) {
            s.onCleanup(e::discard);
        }
        Gt.check(!p.spawned().isEmpty(), "groupe sous 25 % : un petit groupe reapparait");
        Gt.check(p.spawned().size() <= 6, "un PETIT groupe : " + p.spawned().size());
        for (Entity e : p.spawned()) {
            Gt.check(FaunaRegistry.of(e) != null && FaunaRegistry.of(e).group == FaunaGroup.GAME, "bete du gibier : " + e.getType());
            Gt.check(e.position().distanceTo(far.eye()) > FaunaRules.PLAYER_DISTANCE, "a plus de 64 blocs du joueur");
            Gt.check(!FaunaSpawner.visible(level, far, e.position().add(0, 0.8, 0), 256), "hors de la vue du joueur");
            Gt.check(area.contains(e.getBlockX(), e.getBlockZ()), "dans l'aire");
            if (e instanceof Mob m) {
                Gt.equal(Event.Result.DENY, despawn(level, m), "la bete reapparue est persistante");
            }
        }
        //le meme joueur qui regarde l'aire, sans obstacle : le cone de vue l'interdit
        FaunaSpawner.Viewer looking = new FaunaSpawner.Viewer(far.eye(), Vec3.atCenterOf(mid).subtract(far.eye()).normalize());
        Gt.check(FaunaSpawner.visible(level, looking, Vec3.atCenterOf(mid).add(0, 4, 0), 256) || !level.canSeeSky(mid),
              "un point de l'aire, a ciel ouvert, est vu par un joueur qui le regarde");
        s.done();
    }

    // ------------------------------------------------------------------ 3. claims et villages

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_4_claim", timeoutTicks = 60)
    public static void p105_4_pas_dans_un_claim(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        FaunaSpawner.Area area = meadow(h);
        long zone = FaunaRules.zoneKey(area.x0(), area.z0());
        keepZone(s, zone);
        FaunaCounter.of(level).reconcile();
        int counted = FaunaCounter.of(level).groups(zone)[FaunaGroup.GAME.ordinal()];
        String dim = ClanApi.dim(level);
        UUID clan = UUID.nameUUIDFromBytes("p105_4_claim".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        List<Long> chunks = new ArrayList<>();
        for (int cx = area.x0() >> 4; cx <= area.x1() >> 4; cx++) {
            for (int cz = area.z0() >> 4; cz <= area.z1() >> 4; cz++) {
                long c = ChunkPos.asLong(cx, cz);
                if (ClaimIndex.owner(dim, c) == null) {
                    ClaimIndex.set(dim, c, clan);
                    chunks.add(c);
                }
            }
        }
        s.onCleanup(() -> chunks.forEach(c -> ClaimIndex.set(dim, c, null)));
        FaunaSpawner.Viewer far = farViewer(area);
        FaunaZones.Pass p = FaunaZones.pass(level, zone, settings(counted), new FaunaZones.Limits(List.of(far), area), gameStart(counted));
        p.spawned().forEach(e -> s.onCleanup(e::discard));
        Gt.check(p.planned().contains(FaunaGroup.GAME), "gibier sous 25 % : prevu");
        Gt.equal(0L, (long) p.spawned().size(), "aire dans un claim : aucun repeuplement");
        BlockPos mid = h.absolutePos(new BlockPos(9, 1, 9));
        FaunaSpecies near = FaunaSpecies.of("pioneers:test_rat", FaunaGroup.GAME).nearBuildings();
        FaunaSpawner.Limits lim = new FaunaSpawner.Limits(List.of(far), FaunaRules.PLAYER_DISTANCE, FaunaRules.VILLAGE_DISTANCE, true, 256);
        Gt.check(!FaunaSpawner.allowed(level, mid, FaunaRegistry.byId("minecraft:sheep"), lim), "claim : le mouton n'y revient pas");
        Gt.check(FaunaSpawner.allowed(level, mid, near, lim), "espece « pres des constructions » (rats, pigeons) : permise dans un claim");
        //village : une cloche (lieu de rassemblement) a 20 blocs
        chunks.forEach(c -> ClaimIndex.set(dim, c, null));
        chunks.clear();
        BlockPos bell = h.absolutePos(new BlockPos(1, 1, 1));
        level.setBlock(bell, Blocks.BELL.defaultBlockState(), 3);
        s.onCleanup(() -> level.setBlock(bell, Blocks.AIR.defaultBlockState(), 3));
        Gt.check(FaunaSpawner.nearVillage(level, mid, FaunaRules.VILLAGE_DISTANCE), "une cloche a moins de 48 blocs = un village");
        Gt.check(!FaunaSpawner.allowed(level, mid, FaunaRegistry.byId("minecraft:sheep"), lim), "pres d'un village : pas de repeuplement");
        Gt.check(FaunaSpawner.allowed(level, mid, near, lim), "espece « pres des constructions » : permise pres d'un village");
        s.done();
    }

    // ------------------------------------------------------------------ 4. poissons

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_4_fish", timeoutTicks = 60)
    public static void p105_4_poisson_vanilla_persistant(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        for (int x = 3; x <= 12; x++) {
            for (int z = 3; z <= 12; z++) {
                for (int y = 1; y <= 4; y++) {
                    level.setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.WATER.defaultBlockState(), 3);
                }
            }
        }
        Mob cod = add(s, EntityType.COD, h.absolutePos(new BlockPos(5, 2, 5)));
        Mob salmon = add(s, EntityType.SALMON, h.absolutePos(new BlockPos(7, 2, 5)));
        Mob squid = add(s, EntityType.SQUID, h.absolutePos(new BlockPos(9, 2, 5)));
        PiFish fish = add(s, PiFishing.FISH.get(), h.absolutePos(new BlockPos(5, 2, 9)));
        for (Mob m : new Mob[]{cod, salmon, squid, fish}) {
            Gt.equal(Event.Result.DENY, despawn(level, m), m.getType() + " : persistant");
        }
        Gt.check(!fish.removeWhenFarAway(500), "poisson du mod : ne disparait plus au loin");
        Gt.equal(FaunaGroup.FISH, FaunaRegistry.of(cod).group, "morue : groupe des poissons");
        Gt.check(FaunaRegistry.of(cod).isPersistOnly(), "morue : persistante, jamais remise (le mod a ses especes)");
        Gt.check(FaunaEvents.blocked(EntityType.COD, MobSpawnType.NATURAL, true) && FaunaEvents.blocked(PiFishing.FISH.get(), MobSpawnType.NATURAL, true),
              "plus d'apparition naturelle des poissons");
        //plafonds unifies : le repeuplement passe par la regle du poisson (plafond de zone de 16 blocs et d'etendue d'eau)
        FaunaSpecies sp = FaunaRegistry.byId("pioneers:fish");
        FaunaPools.Entry e = new FaunaPools.Entry(sp, PiFishing.FISH.get(), FaunaSpecies.Where.WATER, 1, 1, 1);
        BlockPos spot = h.absolutePos(new BlockPos(10, 2, 10));
        for (int i = 0; i < PiFish.SPAWN_CAP; i++) {
            add(s, PiFishing.FISH.get(), h.absolutePos(new BlockPos(4 + i, 3, 11)));
        }
        Gt.check(!FaunaSpawner.fits(level, e, spot, MobSpawnType.NATURAL, level.random), "bassin plein (plafond PiFish) : pas de repeuplement");
        s.done();
    }

    // ------------------------------------------------------------------ 5. corbeau

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_4_crow", timeoutTicks = 2400)
    public static void p105_4_corbeau_pille_et_fuit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        FarmCropBlock block = null;
        for (var ro : PiFarm.CROPS.values()) {
            if (!ro.get().def().tall()) {
                block = ro.get();
                break;
            }
        }
        Gt.check(block != null, "une culture basse");
        FarmCropBlock crop = block;
        BlockPos soil = h.absolutePos(new BlockPos(9, 0, 9)), at = soil.above();
        level.setBlock(soil, Blocks.FARMLAND.defaultBlockState().setValue(FarmBlock.MOISTURE, 7), 3);
        level.setBlock(at, crop.getStateForAge(crop.getMaxAge()), 3);
        int age0 = crop.getMaxAge();
        Crow crow = add(s, PiFarmDevices.CROW.get(), h.absolutePos(new BlockPos(6, 1, 9)));
        Gt.equal(Event.Result.DENY, despawn(level, crow), "corbeau persistant");
        h.startSequence()
              .thenWaitUntil(() -> {
                  BlockState st = level.getBlockState(at);
                  Gt.check(!st.is(crop) || crop.getAge(st) < age0, "le corbeau n'a pas encore pille la culture");
              })
              .thenExecute(() -> {
                  BlockPos sc = h.absolutePos(new BlockPos(12, 1, 9));
                  BlockState lower = PiFarmDevices.SCARECROW.get().defaultBlockState();
                  level.setBlock(sc, lower, 3);
                  level.setBlock(sc.above(), lower.setValue(ScarecrowBlock.HALF, DoubleBlockHalf.UPPER), 3);
                  s.onCleanup(() -> {
                      level.setBlock(sc.above(), Blocks.AIR.defaultBlockState(), 3);
                      level.setBlock(sc, Blocks.AIR.defaultBlockState(), 3);
                  });
              })
              .thenWaitUntil(() -> {
                  BlockPos sc = h.absolutePos(new BlockPos(12, 1, 9));
                  Gt.check(crow.spooked() || crow.distanceToSqr(Vec3.atCenterOf(sc)) > ScarecrowBlock.SCARE_RADIUS * ScarecrowBlock.SCARE_RADIUS,
                        "le corbeau ne fuit pas l'epouvantail");
              })
              .thenExecute(() -> Gt.check(!crow.isRemoved(), "le corbeau est toujours la (persistant)"))
              .thenExecute(s::done);
    }
}
