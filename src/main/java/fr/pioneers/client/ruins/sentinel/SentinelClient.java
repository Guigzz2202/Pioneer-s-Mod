package fr.pioneers.client.ruins.sentinel;

import fr.pioneers.Pioneers;
import fr.pioneers.ruins.sentinel.SentinelRole;
import fr.pioneers.ruins.sentinel.SentinelTypes;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 1.9 (MAJ RP, bloc 1) : client des Sentinelles (rendus et couches de maquettes des cinq roles). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SentinelClient {

    private SentinelClient() {
    }

    public static ModelLayerLocation layer(SentinelRole role) {
        return new ModelLayerLocation(Pioneers.rl(role.entityId()), "main");
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        for (SentinelRole role : SentinelRole.values()) {
            event.registerEntityRenderer(SentinelTypes.type(role), context -> new SentinelRenderer(context, role));
        }
    }

    @SubscribeEvent
    public static void layers(EntityRenderersEvent.RegisterLayerDefinitions event) {
        for (SentinelRole role : SentinelRole.values()) {
            event.registerLayerDefinition(layer(role), () -> SentinelModels.create(role.id()));
        }
    }
}
