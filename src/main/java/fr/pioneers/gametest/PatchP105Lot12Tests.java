package fr.pioneers.gametest;

import fr.pioneers.hunt.HarpoonEntity;
import fr.pioneers.hunt.HarpoonItem;
import fr.pioneers.hunt.PiHunt;
import fr.pioneers.pet.PetDog;
import fr.pioneers.pet.PetMaster;
import fr.pioneers.pet.PetRules;
import fr.pioneers.pet.PiPets;
import fr.pioneers.wild.Deer;
import fr.pioneers.wild.PiWild;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 12 (verification finale, GameTests des corrections) : le harpon ne s'accroche qu'a une BETE vraiment touchee
 * (jamais a un joueur, jamais a un animal protege par son maitre : il n'est ni ralenti ni traine) ; delais du rappel et de la liste de la
 * roue jamais bloques par une heure venue d'un autre monde.
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot12Tests {

    private PatchP105Lot12Tests() {
    }

    static void aim(ServerPlayer p, Entity target) {
        Vec3 to = target.position().add(0, target.getBbHeight() * 0.5, 0).subtract(p.getEyePosition());
        p.setYRot((float) (Math.toDegrees(Math.atan2(-to.x, to.z))));
        p.setXRot((float) (-Math.toDegrees(Math.atan2(to.y, Math.hypot(to.x, to.z)))));
    }

    // ------------------------------------------------------------------ 1. harpon : un animal protege par son maitre n'est pas accroche

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_12_harpoon_pet", timeoutTicks = 120)
    public static void p105_12_harpon_animal_protege(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer a = s.player("h12a", "Achab", new BlockPos(3, 1, 10));
        ServerPlayer b = s.player("h12b", "Berthe", new BlockPos(3, 1, 4));
        PetDog d = PatchP105Lot10Tests.add(s, PiPets.DOGS.get(0).get(), new BlockPos(9, 1, 10));
        d.setNoAi(true);
        d.tame(b);
        PetMaster.setMaster(d, b);
        float before = d.getHealth();
        aim(a, d);
        ItemStack stack = new ItemStack(PiHunt.HARPOON.get());
        a.setItemInHand(InteractionHand.MAIN_HAND, stack);
        HarpoonEntity hp = HarpoonItem.launch(s.level, a);
        stack.getOrCreateTag().putUUID(HarpoonItem.THROWN, hp.getUUID());
        s.onCleanup(hp::discard);
        h.startSequence()
              .thenIdle(40)
              .thenExecute(() -> Gt.check(hp.attached() != d, "le harpon ne s'accroche pas au chien d'un autre joueur (protege par son maitre)"))
              .thenExecute(() -> Gt.check(d.getHealth() >= before, "le chien protege n'est pas blesse"))
              .thenExecute(() -> Gt.check(!d.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN), "ni ralenti ni traine"))
              .thenExecute(s::done);
    }

    // ------------------------------------------------------------------ 2. regles : jamais un joueur ; delais robustes

    @GameTest(template = Gt.BORDER, batch = "pioneers_p105_12_rules", timeoutTicks = 20)
    public static void p105_12_regles_harpon_et_delais(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("h12c", "Cesar", new BlockPos(3, 1, 3));
        Deer deer = PatchP105Lot10Tests.add(s, PiWild.DEER.get(), new BlockPos(8, 1, 8));
        Gt.check(!HarpoonEntity.anchors(p, true), "le harpon ne retient jamais un joueur, meme touche");
        Gt.check(HarpoonEntity.anchors(deer, true), "il retient une bete touchee");
        Gt.check(!HarpoonEntity.anchors(deer, false), "jamais une bete que le coup n'a pas touchee (protection, invulnerable)");
        Gt.check(!PetRules.waiting(null, 5, 20), "aucune demande notee : rien n'attend");
        Gt.check(PetRules.waiting(100L, 110, 20), "demande toute recente : on attend");
        Gt.check(!PetRules.waiting(100L, 120, 20), "delai ecoule");
        Gt.check(!PetRules.waiting(1_000_000L, 500, 20), "heure d'un autre monde (dans le futur) : jamais bloque");
        s.done();
    }
}
