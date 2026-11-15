package fr.pioneers.gametest;

import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.farm.fish.PiFish;
import fr.pioneers.world.pet.PetCore;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.wild.MarineAnimal;
import fr.pioneers.world.wild.Pinniped;
import fr.pioneers.world.wild.PiWild9;
import fr.pioneers.world.wild.Seagull;
import fr.pioneers.world.wild.Shark;
import fr.pioneers.world.wild.Whale;
import fr.pioneers.world.wild.Wild9Events;
import fr.pioneers.world.wild.Wild9Rules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.animal.Cod;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 9 (GameTests ; specification « Le monde vivant » §4 « Oceans et mers » et « Cotes et banquise ») : grand requin
 * blanc qui attaque le nageur ; requin-marteau qui n'attaque qu'un joueur qui SAIGNE ; requin-baleine inoffensif ; mouette qui vole UN
 * aliment de la barre d'action (jamais l'objet en main ni l'armure) ; reglage coupe : aucun vol ; phoque qui plonge a l'approche ; baleine
 * qui remonte respirer et souffle ; poissons laches par un joueur jamais chasses, regles de la pisciculture et des aquariums inchangees.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot9Tests {

    private PatchP105Lot9Tests() {
    }

    static <T extends Entity> T add(Gt.Scope s, EntityType<T> type, BlockPos rel) {
        BlockPos abs = s.h.absolutePos(rel);
        T e = type.create(s.level);
        Gt.check(e != null, "creation de " + type);
        e.moveTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5, 0, 0);
        s.level.addFreshEntity(e);
        s.onCleanup(() -> {
            if (!e.isRemoved()) {
                e.discard();
            }
            PetRegistry.get(s.server).remove(e.getUUID());
        });
        return e;
    }

    /** Bassin d'eau dans la structure (bornes comprises, relatives au test). */
    static void water(GameTestHelper h, int x0, int x1, int y0, int y1, int z0, int z1) {
        for (int x = x0; x <= x1; x++) {
            for (int y = y0; y <= y1; y++) {
                for (int z = z0; z <= z1; z++) {
                    h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.WATER.defaultBlockState(), 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------ 1. le grand requin blanc attaque le nageur

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_9_white", timeoutTicks = 400)
    public static void p105_9_grand_blanc_attaque_le_nageur(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        water(h, 1, 30, 1, 8, 1, 30);
        Gt.check(Wild9Rules.sharkAttacks(Wild9Rules.GREAT_WHITE, 16, true, false, 10) && !Wild9Rules.sharkAttacks(Wild9Rules.GREAT_WHITE, 16, false, false, 5)
                 && !Wild9Rules.sharkAttacks(Wild9Rules.GREAT_WHITE, 16, true, false, 20), "regles : le nageur a moins de 16 blocs, jamais hors de l'eau");
        Shark sh = add(s, PiWild9.GREAT_WHITE_SHARK.get(), new BlockPos(8, 3, 15));
        ServerPlayer p = s.player("shkw", "Gaspard", new BlockPos(18, 3, 15));
        float hp = p.getHealth();
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(sh.getTarget() == p, "le requin blanc repere le nageur et le prend en chasse"))
              .thenWaitUntil(() -> Gt.check(sh.strikes > 0 || p.getHealth() < hp, "il mord"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 2. le requin-marteau n'attaque qu'un joueur qui saigne

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_9_hammer", timeoutTicks = 400)
    public static void p105_9_requin_marteau_seulement_si_l_on_saigne(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        water(h, 1, 30, 1, 8, 1, 30);
        Gt.check(Wild9Rules.bleeding(8F, 20F, -1) && Wild9Rules.bleeding(20F, 20F, 40) && !Wild9Rules.bleeding(20F, 20F, -1)
                 && !Wild9Rules.bleeding(20F, 20F, Wild9Rules.BLEED_TICKS + 1), "regles : sous la moitie de sa vie, ou blesse depuis moins de 10 s");
        Gt.check(!Wild9Rules.sharkAttacks(Wild9Rules.HAMMERHEAD, 12, true, false, 4) && Wild9Rules.sharkAttacks(Wild9Rules.HAMMERHEAD, 12, true, true, 4),
              "regles : le requin-marteau seulement si l'on saigne");
        Shark sh = add(s, PiWild9.HAMMERHEAD_SHARK.get(), new BlockPos(10, 3, 15));
        ServerPlayer p = s.player("shkh", "Hortense", new BlockPos(16, 3, 15));
        p.setHealth(p.getMaxHealth());
        h.startSequence()
              .thenIdle(80)
              .thenExecute(() -> Gt.check(sh.getTarget() == null, "joueur en pleine forme : le requin-marteau l'ignore"))
              .thenExecute(() -> Wild9Events.markHurt(p))
              .thenWaitUntil(() -> Gt.check(sh.getTarget() == p, "le joueur saigne : le requin-marteau attaque"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 3. le requin-baleine est inoffensif

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_9_whaleshark", timeoutTicks = 200)
    public static void p105_9_requin_baleine_inoffensif(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        water(h, 1, 30, 1, 8, 1, 30);
        Gt.check(!Wild9Rules.sharkAttacks(Wild9Rules.WHALE_SHARK, 16, true, true, 2), "regles : le requin-baleine n'attaque jamais");
        Shark sh = add(s, PiWild9.WHALE_SHARK.get(), new BlockPos(12, 2, 15));
        ServerPlayer p = s.player("shkb", "Ines", new BlockPos(17, 3, 15));
        Wild9Events.markHurt(p);
        p.setHealth(6F);
        h.startSequence()
              .thenIdle(60)
              .thenExecute(() -> {
                  Gt.check(sh.getTarget() == null && p.getHealth() >= 6F, "meme un joueur qui saigne : il ne l'attaque pas");
                  sh.hurt(s.level.damageSources().playerAttack(p), 1F);
              })
              .thenIdle(40)
              .thenExecute(() -> Gt.check(sh.getTarget() == null && sh.strikes == 0, "frappe, il ne riposte pas (il s'ecarte)"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 4. la mouette vole un aliment de la barre d'action, jamais l'outil ni l'armure

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_9_gull", timeoutTicks = 40)
    public static void p105_9_mouette_vole_un_aliment(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        boolean before = PiCommonConfig.get(PiCommonConfig.FAUNA_SEAGULL_THEFT, true);
        s.onCleanup(() -> PiCommonConfig.FAUNA_SEAGULL_THEFT.set(before));
        PiCommonConfig.FAUNA_SEAGULL_THEFT.set(true);
        Gt.check(Wild9Rules.stealSlot(new boolean[]{true, false, false, false, false, false, false, false, false}, 0, 0.5) == -1,
              "regles : jamais la case tenue en main");
        Gt.check(Wild9Rules.stealSlot(new boolean[]{true, false, true, false, false, false, false, false, false}, 0, 0.9) == 2, "regles : un aliment d'une autre case");
        Seagull g = add(s, PiWild9.SEAGULL.get(), new BlockPos(10, 1, 11));
        g.theftCooldown = 0;
        ServerPlayer p = s.player("gula", "Jules", new BlockPos(10, 1, 12));
        Gt.check(Seagull.theftEnabled(), "vol des mouettes actif par defaut");
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getInventory().items.set(0, new ItemStack(Items.BREAD, 4));               //en main : jamais vole
        p.getInventory().items.set(3, new ItemStack(Items.IRON_SWORD));
        p.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.TURTLE_HELMET));
        p.setItemSlot(EquipmentSlot.OFFHAND, new ItemStack(Items.APPLE, 2));
        Gt.check(!g.trySteal(p), "seulement l'objet en main, l'armure et la main gauche : rien a voler");
        Gt.equal(4, p.getInventory().items.get(0).getCount(), "le pain en main reste");
        p.getInventory().items.set(5, new ItemStack(Items.COOKED_BEEF, 3));
        Gt.check(g.trySteal(p), "un aliment dans la barre d'action : elle le vole");
        Gt.equal(2, p.getInventory().items.get(5).getCount(), "UN seul aliment");
        Gt.equal(4, p.getInventory().items.get(0).getCount(), "le pain en main reste");
        Gt.check(p.getInventory().items.get(3).is(Items.IRON_SWORD) && p.getItemBySlot(EquipmentSlot.HEAD).is(Items.TURTLE_HELMET)
                 && p.getItemBySlot(EquipmentSlot.OFFHAND).getCount() == 2, "ni l'outil, ni l'armure, ni la main gauche");
        Gt.check(g.loot.is(Items.COOKED_BEEF) && g.thefts == 1, "elle le garde au bec");
        Gt.check(!g.trySteal(p), "le bec plein (et le delai), elle ne vole plus");
        Gt.check(!PetCore.game(g), "la mouette n'est pas un gibier du chien de chasse");
        s.done();
    }

    // ------------------------------------------------------------------ 5. reglage coupe : aucun vol

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_9_gulloff", timeoutTicks = 40)
    public static void p105_9_mouette_reglage_coupe(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        boolean before = PiCommonConfig.get(PiCommonConfig.FAUNA_SEAGULL_THEFT, true);
        s.onCleanup(() -> PiCommonConfig.FAUNA_SEAGULL_THEFT.set(before));
        Seagull g = add(s, PiWild9.SEAGULL.get(), new BlockPos(10, 1, 11));
        g.theftCooldown = 0;
        ServerPlayer p = s.player("gulb", "Karine", new BlockPos(10, 1, 12));
        p.getInventory().clearContent();
        p.getInventory().selected = 0;
        p.getInventory().items.set(4, new ItemStack(Items.COOKED_COD, 5));
        PiCommonConfig.FAUNA_SEAGULL_THEFT.set(false);
        Gt.check(!Seagull.theftEnabled() && !g.trySteal(p), "reglage fauna.seagullTheft coupe : aucun vol");
        Gt.equal(5, p.getInventory().items.get(4).getCount(), "le poisson reste");
        s.done();
    }

    // ------------------------------------------------------------------ 6. le phoque plonge a l'approche

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_9_seal", timeoutTicks = 400)
    public static void p105_9_phoque_plonge_a_l_approche(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        //une cote de pierre (x 1..12, jusqu'a la couche 4) au bord d'un bassin (x 13..30, eau jusqu'a la couche 4)
        for (int x = 1; x <= 12; x++) {
            for (int y = 1; y <= 4; y++) {
                for (int z = 1; z <= 30; z++) {
                    h.getLevel().setBlock(h.absolutePos(new BlockPos(x, y, z)), Blocks.STONE.defaultBlockState(), 2);
                }
            }
        }
        water(h, 13, 30, 1, 4, 1, 30);
        Gt.check(Wild9Rules.dives(8, 5, false) && !Wild9Rules.dives(8, 10, false) && !Wild9Rules.dives(8, 5, true), "regles : 8 blocs, 4 accroupi");
        Pinniped seal = add(s, PiWild9.SEAL.get(), new BlockPos(10, 5, 15));
        ServerPlayer p = s.player("sela", "Louis", new BlockPos(1, 5, 15));
        h.startSequence()
              .thenIdle(40)
              .thenExecute(() -> Gt.check(!seal.isInWater(), "a 9 blocs : le phoque reste sur la cote"))
              .thenExecute(() -> s.move(p, new BlockPos(5, 5, 15)))
              .thenWaitUntil(() -> Gt.check(seal.isInWater() && seal.dives > 0, "a 5 blocs : il gagne l'eau et plonge"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 7. la baleine remonte respirer et souffle

    @GameTest(template = Gt.TALL, batch = "pioneers_p105_9_breath", timeoutTicks = 600)
    public static void p105_9_baleine_remonte_respirer(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        water(h, 1, 30, 1, 18, 1, 30);
        Gt.check(Wild9Rules.mustSurface(500, 3000) && !Wild9Rules.mustSurface(2000, 3000) && Wild9Rules.blows(500, 3000)
                 && !Wild9Rules.blows(2990, 3000), "regles : remonte sous 30 % de souffle ; souffle si l'air etait entame");
        Whale w = add(s, PiWild9.HUMPBACK_WHALE.get(), new BlockPos(15, 2, 15));
        h.startSequence()
              .thenIdle(5)
              .thenExecute(() -> {
                  Gt.check(!w.canBreatheUnderwater() && w.getMaxAirSupply() == 3000, "la baleine retient son souffle (3 000 ticks)");
                  w.setAirSupply(200);
              })
              .thenWaitUntil(() -> Gt.check(w.breaths > 0, "elle remonte a la surface et souffle"))
              .thenExecute(() -> Gt.check(w.getAirSupply() == w.getMaxAirSupply() && w.isAlive(), "souffle retrouve, toujours en vie"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 8. pisciculture et aquariums inchanges

    @GameTest(template = Gt.LARGE, batch = "pioneers_p105_9_fish", timeoutTicks = 200)
    public static void p105_9_poissons_laches_jamais_chasses(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        s.notPeaceful();
        water(h, 1, 30, 1, 8, 1, 30);
        Gt.check(PiFish.ZONE == 16 && PiFish.SPAWN_CAP == 6 && PiFish.BREED_CAP == 4 && PiFish.WATER_PER_FISH == 32 && PiFish.BODY_SCAN == 256,
              "plafonds de la pisciculture inchanges");
        Gt.check(Wild9Rules.SEA_ZONE == 2 * PiFish.ZONE, "zone des geants marins = 2 x celle des poissons du mod");
        Gt.check(!Wild9Rules.huntsFish(Wild9Rules.GREAT_WHITE, true, 10, 0) && !Wild9Rules.huntsFish(Wild9Rules.GREAT_WHITE, false, 2, 0)
                 && Wild9Rules.huntsFish(Wild9Rules.GREAT_WHITE, false, 3, 0), "regles : jamais dans un claim, jamais les derniers");
        Shark sh = add(s, PiWild9.GRAY_REEF_SHARK.get(), new BlockPos(15, 3, 15));
        sh.fishHunt = 0;
        Cod[] fish = new Cod[4];
        for (int i = 0; i < 4; i++) {
            fish[i] = add(s, EntityType.COD, new BlockPos(13 + i, 3, 18));
            fish[i].setFromBucket(true);                                              //poissons laches par un joueur (bassin, ferme)
        }
        h.startSequence()
              .thenIdle(60)
              .thenExecute(() -> {
                  Gt.check(sh.getTarget() == null, "des poissons laches par un joueur : jamais chasses");
                  for (Cod c : fish) {
                      Gt.check(c.isAlive(), "les poissons du bassin sont intacts");
                      c.setFromBucket(false);
                  }
                  sh.fishHunt = 0;
              })
              .thenWaitUntil(() -> Gt.check(sh.getTarget() instanceof Cod, "des poissons sauvages : le requin chasse"))
              .thenExecute(s::done);
    }
}
