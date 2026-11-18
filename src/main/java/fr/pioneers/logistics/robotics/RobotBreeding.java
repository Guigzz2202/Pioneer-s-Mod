package fr.pioneers.logistics.robotics;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.BabyEntitySpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Reproduction par les robots : les animaux nourris par une unite ne donnent pas d'experience (sinon l'elevage robotise serait une
 * ferme a experience). L'experience apparait juste apres la naissance, a la position du parent : on la retire.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class RobotBreeding {

    /** Marque posee sur un animal nourri par un robot (moment du repas). */
    public static final String TAG = "pioneers_robot_fed";
    private static final long FED_TICKS = 6000;

    private record Birth(Object level, Vec3 at, long time) {}

    private static final List<Birth> BIRTHS = new ArrayList<>();

    private RobotBreeding() {
    }

    private static boolean robotFed(Mob mob) {
        return mob != null && mob.getPersistentData().contains(TAG) && mob.level().getGameTime() - mob.getPersistentData().getLong(TAG) < FED_TICKS;
    }

    @SubscribeEvent
    public static void onBirth(BabyEntitySpawnEvent event) {
        Mob a = event.getParentA(), b = event.getParentB();
        if (a != null && !a.level().isClientSide && (robotFed(a) || robotFed(b))) {
            long now = a.level().getGameTime();
            BIRTHS.removeIf(birth -> now - birth.time() > 2);
            BIRTHS.add(new Birth(a.level(), a.position(), now));
        }
    }

    /** 0.20.46 (audit) : les naissances gardent leur monde : rien ne doit survivre a l'arret du serveur. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        BIRTHS.clear();
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof ExperienceOrb orb) || BIRTHS.isEmpty() || event.getLevel().isClientSide) {
            return;
        }
        long now = event.getLevel().getGameTime();
        for (Birth birth : BIRTHS) {
            if (birth.level() == event.getLevel() && now - birth.time() <= 1 && birth.at().distanceToSqr(orb.position()) < 4) {
                event.setCanceled(true);
                return;
            }
        }
    }
}
