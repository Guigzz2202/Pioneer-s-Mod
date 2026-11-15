package fr.pioneers.client.kinetics;

import fr.pioneers.Pioneers;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Textures rechargees : le cache des coordonnees de l'atlas est vide. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class KineticClientEvents {

    private KineticClientEvents() {
    }

    @SubscribeEvent
    public static void onStitch(TextureStitchEvent.Post event) {
        if (InventoryMenu.BLOCK_ATLAS.equals(event.getAtlas().location())) {
            KDraw.clearCache();
        }
    }
}
