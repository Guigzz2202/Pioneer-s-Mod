package fr.pioneers.space;

import fr.pioneers.Pioneers;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Empeche de descendre d'une fusee en plein vol ou d'une capsule en descente.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class RocketEvents {

    private RocketEvents() {
    }

    @SubscribeEvent
    public static void onMount(EntityMountEvent event) {
        if (!event.isDismounting()) {
            return;
        }
        if (event.getEntityBeingMounted() instanceof RocketEntity rocket && rocket.isFlying() && !rocket.isTransferring()) {
            event.setCanceled(true);
        }
        //Capsule en descente : on reste a bord jusqu'au contact (Sneak sert a piloter)
        if (event.getEntityBeingMounted() instanceof CapsuleEntity capsule && !capsule.hasLanded() && capsule.isAlive()
            && event.getEntityMounting() instanceof Player player && player.isAlive()) {
            event.setCanceled(true);
        }
    }
}
