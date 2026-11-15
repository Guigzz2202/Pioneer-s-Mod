package fr.pioneers.gametest;

import fr.pioneers.fauna.FaunaCounter;
import fr.pioneers.fauna.FaunaData;
import fr.pioneers.fauna.FaunaGroup;
import fr.pioneers.fauna.FaunaRegistry;
import fr.pioneers.fauna.FaunaRules;
import fr.pioneers.fauna.FaunaSettings;
import fr.pioneers.fauna.FaunaSpawner;
import fr.pioneers.fauna.FaunaZones;
import fr.pioneers.season.SeasonData;
import fr.pioneers.season.SeasonEvents;
import fr.pioneers.season.Seasons.Season;
import fr.pioneers.wild.Bison;
import fr.pioneers.wild.Boar;
import fr.pioneers.wild.Deer;
import fr.pioneers.wild.Hare;
import fr.pioneers.wild.PiWild;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 6 (GameTests ; specification « Le monde vivant » §3, §4, §7) : sanglier qui charge un joueur a moins de 4 blocs
 * (et pas plus loin) ; harde de cerfs qui fuit ensemble quand l'un d'eux est blesse ; troupeau de bisons qui defend celui qu'on attaque (le
 * bisonneau ne charge pas) ; lievre blanc en hiver, fauve l'ete hors de la neige ; bois du cerf absents en hiver, presents l'ete, jamais sur
 * la biche ; reproduction sauvage lente qui remonte un groupe parti de 40 % jusqu'a sa densite de depart sans jamais la depasser (petits
 * pres d'un adulte, loin du joueur).
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot6Tests {

    private PatchP105Lot6Tests() {
    }

    static <T extends Entity> T add(Gt.Scope s, EntityType<T> type, BlockPos abs) {
        T e = type.create(s.level);
        Gt.check(e != null, "creation de " + type);
        e.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(e);
        s.onCleanup(e::discard);
        return e;
    }

    static <T extends WildAnimal> T wild(Gt.Scope s, EntityType<T> type, BlockPos rel, int sex, boolean baby) {
        T e = add(s, type, s.h.absolutePos(rel));
        e.setSex(sex);
        e.setAge(baby ? -24000 : 0);
        return e;
    }

    static void keepCalendar(Gt.Scope s) {
        long offset = SeasonData.get(s.server.overworld()).offset(), time = s.level.getDayTime();
        s.onCleanup(() -> {
            s.level.setDayTime(time);
            SeasonEvents.setOffset(s.server, offset);
        });
    }

    static void grass(GameTestHelper h) {
        for (int x = 1; x <= 18; x++) {
            for (int z = 1; z <= 18; z++) {
                h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    // ------------------------------------------------------------------ 1. le sanglier charge a moins de 4 blocs

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_6_boar", timeoutTicks = 100)
    public static void p105_6_sanglier_charge(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        Boar near = wild(s, PiWild.BOAR.get(), new BlockPos(5, 1, 5), WildRules.MALE, false);
        Boar far = wild(s, PiWild.BOAR.get(), new BlockPos(15, 1, 15), WildRules.FEMALE, false);
        Boar baby = wild(s, PiWild.BOAR.get(), new BlockPos(15, 1, 5), WildRules.MALE, true);
        ServerPlayer p = s.player("boar", "Firmin", new BlockPos(5, 1, 8));
        Gt.check(WildRules.charges(3, 4, false) && !WildRules.charges(5, 4, false) && WildRules.charges(9, 4, true), "regle : moins de 4 blocs ou blesse");
        Gt.check(near.distanceTo(p) < 4, "joueur a 3 blocs du sanglier");
        Gt.check(near.provoke() == p, "a moins de 4 blocs, le sanglier charge le joueur");
        Gt.check(far.distanceTo(p) > 4 && far.provoke() == null, "plus loin, il ne charge pas (il fuit)");
        Gt.check(baby.provoke() == null, "le marcassin ne charge pas");
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(near.charging() && near.action() == WildRules.CHARGE, "le sanglier est en charge"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 2. la harde fuit ensemble

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_6_herd", timeoutTicks = 200)
    public static void p105_6_harde_de_cerfs_fuit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        List<Deer> herd = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            herd.add(wild(s, PiWild.DEER.get(), new BlockPos(12 + i % 2 * 2, 1, 12 + i / 2 * 2), i == 0 ? WildRules.MALE : WildRules.FEMALE, false));
        }
        ServerPlayer p = s.player("deer", "Gaspard", new BlockPos(2, 1, 2));
        p.setShiftKeyDown(true);
        Vec3 from = p.position();
        double[] start = new double[herd.size()];
        for (int i = 0; i < herd.size(); i++) {
            start[i] = herd.get(i).position().distanceTo(from);
        }
        herd.get(0).hurt(s.level.damageSources().playerAttack(p), 1F);
        for (Deer d : herd) {
            Gt.check(d.alarmed(), "toute la harde est alertee quand un cerf est blesse");
        }
        h.startSequence()
              .thenWaitUntil(() -> {
                  for (int i = 0; i < herd.size(); i++) {
                      Gt.check(herd.get(i).position().distanceTo(from) > start[i] + 1.5, "le cerf " + i + " s'eloigne du joueur (aire fermee de 20 blocs)");
                  }
              })
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 3. le troupeau de bisons se defend

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_6_bison", timeoutTicks = 60)
    public static void p105_6_bisons_defendent_le_troupeau(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        List<Bison> herd = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            herd.add(wild(s, PiWild.BISON.get(), new BlockPos(6 + i * 3, 1, 12), i % 2, false));
        }
        Bison calf = wild(s, PiWild.BISON.get(), new BlockPos(9, 1, 16), WildRules.FEMALE, true);
        ServerPlayer p = s.player("bison", "Honorine", new BlockPos(9, 1, 5));
        for (Bison b : herd) {
            Gt.check(b.getTarget() == null, "troupeau paisible au depart");
        }
        herd.get(1).hurt(s.level.damageSources().playerAttack(p), 1F);
        for (Bison b : herd) {
            Gt.check(b.getTarget() == p && b.angry(), "chaque bison adulte defend le troupeau");
        }
        Gt.check(calf.getTarget() == null, "le bisonneau ne charge pas");
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(herd.get(3).action() == WildRules.CHARGE, "les bisons chargent"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 4. lievre blanc en hiver

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_6_hare", timeoutTicks = 40)
    public static void p105_6_lievre_blanc_en_hiver(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        Hare hare = wild(s, PiWild.HARE.get(), new BlockPos(4, 1, 4), WildRules.MALE, false);
        SeasonEvents.set(s.server, Season.WINTER, 5);
        Gt.check(hare.winterCoat(), "lievre blanc en hiver");
        SeasonEvents.set(s.server, Season.SUMMER, 5);
        Gt.equal(hare.snowyHere(), hare.winterCoat(), "l'ete : fauve, sauf dans un biome enneige");
        Gt.check(WildRules.winterCoat(1, true) && !WildRules.winterCoat(1, false) && WildRules.winterCoat(3, false), "regle du pelage");
        s.done();
    }

    // ------------------------------------------------------------------ 5. bois du cerf absents en hiver

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_6_antlers", timeoutTicks = 40)
    public static void p105_6_bois_du_cerf_absents_en_hiver(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        keepCalendar(s);
        Deer stag = wild(s, PiWild.DEER.get(), new BlockPos(3, 1, 3), WildRules.MALE, false);
        Deer hind = wild(s, PiWild.DEER.get(), new BlockPos(6, 1, 6), WildRules.FEMALE, false);
        Deer fawn = wild(s, PiWild.DEER.get(), new BlockPos(3, 1, 6), WildRules.MALE, true);
        int len = fr.pioneers.season.Seasons.serverLength();
        SeasonEvents.set(s.server, Season.SUMMER, len / 2 + 1);
        Gt.check(stag.antlers() > 0.99F, "l'ete : le cerf porte ses bois");
        SeasonEvents.set(s.server, Season.WINTER, len * 3 / 4 + 1);
        Gt.check(stag.antlers() == 0F, "l'hiver (apres la mue) : plus de bois");
        SeasonEvents.set(s.server, Season.SPRING, len / 2 + 1);
        float spring = stag.antlers();
        Gt.check(spring > 0F && spring < 1F, "au printemps : les bois repoussent (" + spring + ")");
        Gt.check(hind.antlers() == 0F && fawn.antlers() == 0F, "la biche et le faon n'ont pas de bois");
        //chaque cerf perd ses bois entre la fin de l'automne et le milieu de l'hiver
        for (double seed = 0; seed <= 1.0; seed += 0.25) {
            Gt.equal(1L, (long) WildRules.antlerGrowth(2, 0.7, seed), "fin d'automne (0,7) : bois encore portes");
            Gt.equal(0L, (long) WildRules.antlerGrowth(3, 0.3, seed), "milieu de l'hiver (0,3) : tombes");
        }
        s.done();
    }

    // ------------------------------------------------------------------ 6. reproduction sauvage lente

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_6_breed", timeoutTicks = 60)
    public static void p105_6_reproduction_sauvage_40_a_100(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerLevel level = s.level;
        grass(h);
        BlockPos a = h.absolutePos(new BlockPos(2, 1, 2)), b = h.absolutePos(new BlockPos(17, 1, 17));
        FaunaSpawner.Area area = new FaunaSpawner.Area(Math.min(a.getX(), b.getX()), Math.min(a.getZ(), b.getZ()), Math.max(a.getX(), b.getX()),
              Math.max(a.getZ(), b.getZ()));
        long zone = FaunaRules.zoneKey(area.x0(), area.z0());
        FaunaData data = FaunaData.get(level);
        int[] before = data.starts(zone);
        boolean legacy = data.legacy(zone);
        s.onCleanup(() -> {
            if (before == null) {
                data.forget(zone);
            } else {
                data.put(zone, before, legacy);
            }
        });
        for (int i = 0; i < 6; i++) {
            Animal cow = add(s, EntityType.COW, h.absolutePos(new BlockPos(4 + i * 2, 1, 9)));
            Gt.check(FaunaRegistry.of(cow) != null, "vache : gibier du socle");
        }
        FaunaCounter counter = FaunaCounter.of(level);
        counter.reconcile();
        int c0 = counter.groups(zone)[FaunaGroup.GAME.ordinal()];
        int start = (int) Math.ceil(c0 / 0.4);
        int[] starts = new int[FaunaGroup.COUNT];
        starts[FaunaGroup.GAME.ordinal()] = start;
        int[] caps = FaunaSettings.DEFAULT.caps().clone();
        caps[FaunaGroup.GAME.ordinal()] = Math.max(caps[FaunaGroup.GAME.ordinal()], start + 8);
        FaunaSettings set = new FaunaSettings(true, FaunaRules.PERIOD, FaunaRules.THRESHOLD, FaunaRules.PLAYER_DISTANCE, FaunaRules.VILLAGE_DISTANCE,
              true, FaunaRules.AI_DISTANCE, caps).withWildBreeding(true, 1.0, FaunaRules.WILD_BREED_DISTANCE);
        double cx = (area.x0() + area.x1()) / 2.0, cz = (area.z0() + area.z1()) / 2.0;
        FaunaSpawner.Viewer far = new FaunaSpawner.Viewer(new Vec3(cx + 100, 80, cz), new Vec3(1, 0, 0));
        Gt.check(c0 >= FaunaRules.THRESHOLD * start, "le groupe part de 40 % (au-dessus du seuil : pas de repeuplement)");
        //un joueur au milieu : aucune naissance pres de lui
        FaunaSpawner.Viewer inside = new FaunaSpawner.Viewer(new Vec3(cx, a.getY() + 1.6, cz), new Vec3(0, 0, 1));
        FaunaZones.Pass p0 = FaunaZones.pass(level, zone, set, new FaunaZones.Limits(List.of(inside), area), starts);
        p0.spawned().forEach(e -> s.onCleanup(e::discard));
        Gt.equal(0L, (long) p0.born()[FaunaGroup.GAME.ordinal()], "joueur a moins de 32 blocs : aucune naissance");
        int count = c0, born = 0;
        for (int pass = 0; pass < 12 && count < start; pass++) {
            FaunaZones.Pass p = FaunaZones.pass(level, zone, set, new FaunaZones.Limits(List.of(far), area), starts);
            p.spawned().forEach(e -> s.onCleanup(e::discard));
            Gt.check(!p.planned().contains(FaunaGroup.GAME), "au-dessus du seuil : pas de repeuplement, seulement des naissances");
            for (Entity e : p.spawned()) {
                Gt.check(e instanceof Animal an && an.isBaby(), "des petits : " + e.getType());
                Gt.check(area.contains(e.getBlockX(), e.getBlockZ()), "pres d'un adulte, dans l'aire");
                Gt.check(e.position().distanceTo(far.eye()) > FaunaRules.WILD_BREED_DISTANCE, "a plus de 32 blocs du joueur");
            }
            born += p.born()[FaunaGroup.GAME.ordinal()];
            Gt.check(p.born()[FaunaGroup.GAME.ordinal()] <= FaunaRules.WILD_BREED_MAX, "au plus 4 naissances par passage");
            counter.reconcile();
            count = counter.groups(zone)[FaunaGroup.GAME.ordinal()];
            Gt.check(count <= start, "jamais au-dela de la densite de depart : " + count + " / " + start);
        }
        Gt.check(born > 0, "des petits sont nes");
        Gt.equal((long) start, (long) count, "le groupe est revenu a 100 % de sa densite de depart");
        //a 100 % : plus aucune naissance
        FaunaZones.Pass full = FaunaZones.pass(level, zone, set, new FaunaZones.Limits(List.of(far), area), starts);
        full.spawned().forEach(e -> s.onCleanup(e::discard));
        Gt.equal(0L, (long) full.born()[FaunaGroup.GAME.ordinal()], "a la densite de depart : plus de naissance");
        s.done();
    }
}
