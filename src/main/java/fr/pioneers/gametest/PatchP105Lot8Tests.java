package fr.pioneers.gametest;

import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.pet.PetCore;
import fr.pioneers.pet.PetRegistry;
import fr.pioneers.wild.Elephant;
import fr.pioneers.wild.Giraffe;
import fr.pioneers.wild.Lion;
import fr.pioneers.wild.Monkey;
import fr.pioneers.wild.PiWild8;
import fr.pioneers.wild.Rhino;
import fr.pioneers.wild.Tiger;
import fr.pioneers.wild.Wild8Rules;
import fr.pioneers.wild.WildRules;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 8 (GameTests ; specification « Le monde vivant » §4 « Savane » et « Jungle et foret tropicale ») : lion couche qui
 * n'attaque qu'un joueur reste a moins de 6 blocs ; rhinoceros qui charge a vue sous 8 blocs (pas plus loin) ; elephants qui defendent un
 * petit (tout le troupeau) ; singe qui vole une banane tenue mais rien d'autre (et pas si le reglage est coupe) ; tigre qui attaque a vue
 * (affut, bond, morsure) ; girafe paisible qui rue quand on la frappe.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot8Tests {

    private PatchP105Lot8Tests() {
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

    static void grass(GameTestHelper h) {
        for (int x = 1; x <= 18; x++) {
            for (int z = 1; z <= 18; z++) {
                h.getLevel().setBlock(h.absolutePos(new BlockPos(x, 0, z)), Blocks.GRASS_BLOCK.defaultBlockState(), 3);
            }
        }
    }

    static void moveTo(GameTestHelper h, ServerPlayer p, BlockPos rel) {
        BlockPos abs = h.absolutePos(rel);
        p.teleportTo(abs.getX() + 0.5, abs.getY(), abs.getZ() + 0.5);
    }

    // ------------------------------------------------------------------ 1. le lion couche n'attaque qu'a moins de 6 blocs

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_8_lion", timeoutTicks = 400)
    public static void p105_8_lion_couche_attaque_sous_6(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        s.notPeaceful();
        Gt.check(Wild8Rules.lionAttacks(4, Wild8Rules.LION_PATIENCE) && !Wild8Rules.lionAttacks(4, Wild8Rules.LION_PATIENCE - 1)
                 && !Wild8Rules.lionAttacks(7, 1000), "regles : seulement un joueur RESTE a moins de 6 blocs");
        Lion l = add(s, PiWild8.LION.get(), new BlockPos(10, 1, 4));
        l.setSex(WildRules.MALE);
        l.setAction(WildRules.REST);
        ServerPlayer p = s.player("liona", "Aurore", new BlockPos(10, 1, 12));
        h.startSequence()
              .thenIdle(100)
              .thenExecute(() -> Gt.check(l.getTarget() == null && l.near() == 0, "a 8 blocs, le lion ne bouge pas"))
              .thenExecute(() -> moveTo(h, p, new BlockPos(10, 1, 7)))
              .thenIdle(10)
              .thenExecute(() -> Gt.check(l.getTarget() == null, "il ne bondit pas tout de suite : il faut rester"))
              .thenWaitUntil(() -> Gt.check(l.getTarget() == p, "le joueur reste a moins de 6 blocs : le lion attaque"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 2. le rhinoceros charge a vue sous 8 blocs

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_8_rhino", timeoutTicks = 300)
    public static void p105_8_rhinoceros_charge_sous_8(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        s.notPeaceful();
        Gt.check(Wild8Rules.rhinoCharges(7, true, 0) && !Wild8Rules.rhinoCharges(9, true, 0) && !Wild8Rules.rhinoCharges(5, false, 0)
                 && !Wild8Rules.rhinoCharges(5, true, 10), "regles : a vue, sous 8 blocs, hors du calme");
        Rhino r = add(s, PiWild8.RHINO.get(), new BlockPos(4, 1, 10));
        ServerPlayer p = s.player("rhina", "Bastien", new BlockPos(17, 1, 10));
        h.startSequence()
              .thenIdle(40)
              .thenExecute(() -> Gt.check(r.getTarget() == null, "a 13 blocs : pas de charge"))
              .thenExecute(() -> moveTo(h, p, new BlockPos((int) Math.floor(r.getX()) - h.absolutePos(BlockPos.ZERO).getX() + 6, 1, 10)))
              .thenWaitUntil(() -> Gt.check(r.getTarget() == p && r.action() == WildRules.CHARGE, "a 6 blocs, en vue : il charge"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 3. les elephants defendent un petit

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_8_elephant", timeoutTicks = 200)
    public static void p105_8_elephants_defendent_petit(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        s.notPeaceful();
        Elephant calf = add(s, PiWild8.ELEPHANT.get(), new BlockPos(10, 1, 6));
        calf.setAge(-24000);
        Elephant a = add(s, PiWild8.ELEPHANT.get(), new BlockPos(5, 1, 4)), b = add(s, PiWild8.ELEPHANT.get(), new BlockPos(15, 1, 4));
        ServerPlayer p = s.player("elepa", "Clement", new BlockPos(10, 1, 17));
        Gt.check(Wild8Rules.elephantDefends(3, false, false) && !Wild8Rules.elephantDefends(9, false, false) && Wild8Rules.elephantDefends(30, true, false),
              "regles : petit approche a moins de 5 blocs, ou attaque");
        h.startSequence()
              .thenIdle(30)
              .thenExecute(() -> Gt.check(a.getTarget() == null && b.getTarget() == null, "loin du petit : le troupeau reste calme"))
              .thenExecute(() -> moveTo(h, p, new BlockPos((int) Math.floor(calf.getX()) - h.absolutePos(BlockPos.ZERO).getX(), 1,
                    (int) Math.floor(calf.getZ()) - h.absolutePos(BlockPos.ZERO).getZ() + 3)))
              .thenWaitUntil(() -> Gt.check(a.getTarget() == p && b.getTarget() == p && a.angry() && b.angry(),
                    "trop pres du petit : tout le troupeau charge"))
              .thenExecute(() -> {
                  Gt.check(calf.getTarget() == null, "le petit, lui, ne charge pas");
                  calf.hurt(s.level.damageSources().playerAttack(p), 1F);
                  Gt.check(a.getTarget() == p && b.getTarget() == p, "le petit attaque : le troupeau le defend");
                  Gt.equal(2, calf.defend(p), "tout le troupeau (les deux adultes)");
              })
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 4. le singe vole une banane, rien d'autre

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_8_monkey", timeoutTicks = 40)
    public static void p105_8_singe_vole_banane_rien_d_autre(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        boolean before = PiCommonConfig.get(PiCommonConfig.FAUNA_MONKEY_THEFT, true);
        s.onCleanup(() -> PiCommonConfig.FAUNA_MONKEY_THEFT.set(before));
        PiCommonConfig.FAUNA_MONKEY_THEFT.set(true);
        Monkey m = add(s, PiWild8.MONKEY.get(), new BlockPos(10, 1, 10));
        m.setBanana(false);
        ServerPlayer p = s.player("monka", "Delphine", new BlockPos(10, 1, 12));
        Gt.check(Monkey.theftEnabled(), "vol des singes actif par defaut");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE, 5));
        p.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.BREAD, 3));
        Gt.check(!m.trySteal(p), "une pomme, du pain : rien ne l'interesse");
        Gt.equal(5, p.getMainHandItem().getCount(), "la pomme reste");
        Gt.equal(3, p.getOffhandItem().getCount(), "le pain reste");
        Gt.check(!m.hasBanana(), "il n'a rien pris");
        p.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(PiWild8.banana(), 3));
        Gt.check(m.trySteal(p), "une banane en main : il la vole");
        Gt.equal(2, p.getMainHandItem().getCount(), "UNE seule banane");
        Gt.check(m.hasBanana(), "il la tient");
        Gt.check(!m.trySteal(p), "les mains pleines, il ne vole plus");
        Gt.check(!PetCore.game(m), "le singe n'est pas un gibier pour le chien de chasse");
        //reglage coupe : aucun vol
        Monkey m2 = add(s, PiWild8.MONKEY.get(), new BlockPos(11, 1, 11));
        m2.setBanana(false);
        PiCommonConfig.FAUNA_MONKEY_THEFT.set(false);
        Gt.check(!Monkey.theftEnabled() && !m2.trySteal(p), "reglage fauna.monkeyTheft coupe : aucun vol");
        Gt.equal(2, p.getMainHandItem().getCount(), "les bananes restent");
        s.done();
    }

    // ------------------------------------------------------------------ 5. le tigre attaque a vue

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_8_tiger", timeoutTicks = 400)
    public static void p105_8_tigre_attaque_a_vue(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        s.notPeaceful();
        Gt.check(Wild8Rules.tigerHunts(15, true, false) && !Wild8Rules.tigerHunts(15, false, false) && !Wild8Rules.tigerHunts(15, true, true),
              "regles : a vue, jamais s'il a peur du feu");
        Gt.check(Wild8Rules.tigerStep(10, true, 0) == 0 && Wild8Rules.tigerStep(4, true, 0) == 1 && Wild8Rules.tigerStep(1, true, 0) == 2,
              "affut au loin, bond a 5 blocs, corps a corps tout pres");
        Tiger t = add(s, PiWild8.TIGER.get(), new BlockPos(4, 1, 10));
        ServerPlayer p = s.player("tigra", "Etienne", new BlockPos(16, 1, 10));
        float hp = p.getHealth();
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(t.getTarget() == p, "le tigre voit le joueur et le chasse"))
              .thenWaitUntil(() -> Gt.check(t.strikes > 0 || p.getHealth() < hp, "il approche a l'affut, bondit et frappe"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 6. la girafe, paisible, rue si on la frappe

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_8_giraffe", timeoutTicks = 100)
    public static void p105_8_girafe_rue_si_frappee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        grass(h);
        s.notPeaceful();
        Giraffe g = add(s, PiWild8.GIRAFFE.get(), new BlockPos(10, 1, 10));
        ServerPlayer p = s.player("gira", "Fanny", new BlockPos(10, 1, 12));
        Gt.check(Wild8Rules.giraffeKicks(2, 0, false) && !Wild8Rules.giraffeKicks(6, 0, false) && !Wild8Rules.giraffeKicks(2, 0, true),
              "regles : ruade de pres, pas le girafon");
        Gt.check(g.getTarget() == null, "paisible : elle ne vise personne");
        float hp = p.getHealth();
        g.hurt(s.level.damageSources().playerAttack(p), 1F);
        Gt.check(g.kicking() && g.action() == Wild8Rules.KICK, "frappee, elle rue");
        h.startSequence()
              .thenWaitUntil(() -> Gt.check(g.kicksLanded > 0 || p.getHealth() < hp, "le coup de sabot touche l'agresseur"))
              .thenExecute(() -> Gt.check(g.getTarget() == null, "puis elle ne poursuit pas : elle s'enfuit"))
              .thenExecute(s::done);
    }
}
