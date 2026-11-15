package fr.pioneers.equipment.cosmetic;

import fr.pioneers.Pioneers;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.12.0 (lot E4) : envoi des garde-robes : a la connexion, a la reapparition et au changement de dimension (au joueur et a ceux qui le
 * voient), et au debut du suivi d'un joueur par un autre (le nouveau venu recoit sa garde-robe).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class CosmeticEvents {

    private CosmeticEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            CosmeticNet.sync(p);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            CosmeticNet.sync(p);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer p) {
            CosmeticNet.sync(p);
        }
    }

    @SubscribeEvent
    public static void onStartTracking(PlayerEvent.StartTracking event) {
        if (event.getEntity() instanceof ServerPlayer viewer && event.getTarget() instanceof Player target) {
            CosmeticNet.syncTo(viewer, target);
        }
    }
}
