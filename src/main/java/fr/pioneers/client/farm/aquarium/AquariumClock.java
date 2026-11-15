package fr.pioneers.client.farm.aquarium;

import fr.pioneers.Pioneers;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 25, Guillaume : « Les poissons dans l'aquarium nagent avec beaucoup de peine ») : HORLOGE de la nage de l'aquarium, propre au
 * client. Avant, le rendu prenait {@code level.getGameTime()} : le client l'avance d'un tick a chaque tick, mais le serveur le REMET a son
 * heure toutes les 20 ticks ({@code ClientPacketListener.handleSetTime}) ; un serveur un peu lent (15 a 18 ticks / s : mods lourds,
 * sauvegardes) faisait reculer tous les poissons de 2 a 5 ticks d'un coup, chaque seconde (et avancer d'autant apres un a-coup). Ici : un
 * compteur qui n'avance qu'avec les ticks du client (jamais remis a l'heure, arrete quand le jeu est en pause), plus la fraction de l'image :
 * le temps de la nage est continu et monotone.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class AquariumClock {

    private AquariumClock() {
    }

    private static long ticks;

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (event.phase == TickEvent.Phase.END && mc.level != null && !mc.isPaused()) {
            ticks++;
        }
    }

    /** Temps de la nage (ticks) a l'image courante ({@code partial} : fraction du tick de l'image). */
    public static double time(float partial) {
        return ticks + partial;
    }
}
