package fr.pioneers.world.wild;

import fr.pioneers.Pioneers;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 9) : un joueur SAIGNE-t-il ? (requins marteau et gris : {@link Wild9Rules#bleeding}). Le jeu ne garde pas l'instant de la
 * derniere blessure d'un joueur : on le note ici a chaque coup recu (temps du monde, serveur), on l'oublie a la deconnexion.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Wild9Events {

    private Wild9Events() {
    }

    static final Map<UUID, Long> HURT = new ConcurrentHashMap<>();

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void hurt(LivingHurtEvent event) {
        if (event.getEntity() instanceof Player p && !p.level().isClientSide && !event.isCanceled() && event.getAmount() > 0) {
            HURT.put(p.getUUID(), p.level().getGameTime());
        }
    }

    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        HURT.remove(event.getEntity().getUUID());
    }

    /** Ticks depuis la derniere blessure du joueur (-1 : aucune connue). */
    public static long ticksSinceHurt(Player p) {
        Long t = HURT.get(p.getUUID());
        return t == null ? -1 : Math.max(0, p.level().getGameTime() - t);
    }

    /** GameTests : marque le joueur comme blesse a l'instant. */
    public static void markHurt(Player p) {
        HURT.put(p.getUUID(), p.level().getGameTime());
    }
}
