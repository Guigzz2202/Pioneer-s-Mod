package fr.pioneers.gametest;

import fr.pioneers.world.pet.PetDog;
import fr.pioneers.world.pet.PetListSync;
import fr.pioneers.world.pet.PetMaster;
import fr.pioneers.world.pet.PetRegistry;
import fr.pioneers.world.pet.PiPets;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/**
 * Patch 1.0.5-beta, lot 33 (retours de jeu 4e serie, 3 : armes, roue G, sons ; GameTests) :
 * <ul>
 *     <li>LISTE DE LA ROUE (retour 45) : une demande de liste a moins d'une seconde de la precedente n'est plus jetee mais DUE et servie par le
 *     tick du serveur ({@code PetRecall.lists}) ; un animal apprivoise, renomme ou retire rend la liste due a son maitre (poussee sans
 *     demande) ; un simple deplacement ne pousse rien.</li>
 * </ul>
 */
@GameTestHolder("pioneers")
@PrefixGameTestTemplate(false)
public final class PatchP105Lot33Tests {

    private PatchP105Lot33Tests() {
    }

    @GameTest(template = Gt.SMALL, batch = "pioneers_p105_33_wheel", timeoutTicks = 120)
    public static void p105_33_liste_roue_due_et_poussee(GameTestHelper h) {
        Gt.Scope s = Gt.begin(h);
        ServerPlayer p = s.player("p33w", "Ulysse", new BlockPos(2, 1, 2));
        UUID me = p.getUUID();
        PetListSync sync = PetListSync.SERVER;
        long now = s.server.overworld().getGameTime();
        sync.forget(me);
        Gt.check(sync.ask(me, now), "premiere demande servie tout de suite");
        Gt.check(!sync.ask(me, now + 1), "demande trop rapprochee : pas d'envoi immediat");
        Gt.check(sync.isOwed(me), "demande trop rapprochee : DUE (avant : jetee, roue vide)");
        //animal apprivoise : la liste est poussee a son maitre
        PetDog d = PiPets.DOGS.get(2).get().create(s.level);
        Gt.check(d != null, "chien cree");
        BlockPos at = h.absolutePos(new BlockPos(3, 1, 3));
        d.moveTo(at.getX() + 0.5, at.getY(), at.getZ() + 0.5, 0, 0);
        UUID id = d.getUUID();
        s.onCleanup(() -> {
            PetRegistry.get(s.server).remove(id);
            sync.forget(me);
        });
        sync.forget(me);
        PetMaster.setMaster(d, p);
        Gt.check(sync.isOwed(me), "chien apprivoise : liste due au maitre");
        sync.forget(me);
        d.moveTo(d.getX() + 5, d.getY(), d.getZ(), 0, 0);
        PetRegistry.get(s.server).update(d);
        Gt.check(!sync.isOwed(me), "simple deplacement : rien a pousser");
        d.setCustomName(Component.literal("Ulysse le chien"));
        PetRegistry.get(s.server).update(d);
        Gt.check(sync.isOwed(me), "chien renomme : liste due");
        //le tick du serveur sert les envois dus des que le delai le permet
        sync.forget(me);
        long t0 = s.server.overworld().getGameTime();
        Gt.check(sync.ask(me, t0), "demande servie");
        Gt.check(!sync.ask(me, t0) && sync.isOwed(me), "seconde demande due");
        h.runAfterDelay(PetListSync.GAP + 5, () -> {
            Gt.check(!sync.isOwed(me), "envoi du servi par le tick du serveur (PetRecall.lists)");
            PetRegistry.get(s.server).remove(id);
            Gt.check(sync.isOwed(me), "animal retire : liste due");
            h.succeed();
        });
    }
}
