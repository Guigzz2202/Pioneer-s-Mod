package fr.pioneers.tech.forge.client;

import fr.pioneers.Pioneers;
import fr.pioneers.tech.forge.ForgeMetals;
import fr.pioneers.tech.forge.HeatedMetal;
import fr.pioneers.tech.forge.PiForge;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ModelEvent;
import net.minecraftforge.client.event.RegisterColorHandlersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge primaire, cote client (fondation) : teinte du metal chauffe selon son metal et sa temperature (couche 0). Les rendus, ecrans et
 * paquets des ingenieurs « casting », « smithing » et « automation » s'enregistrent dans LEURS propres classes client (voir CONTRACT.md),
 * pas ici.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ForgeClientSetup {

    private ForgeClientSetup() {
    }

    /** 1.0.5-beta (lot 15) : modele de repli du metal chauffe (la billette d'avant), dessine par {@link HeatedMetalItemRenderer}. */
    @SubscribeEvent
    public static void hotMetalModels(ModelEvent.RegisterAdditional event) {
        event.register(HeatedMetalItemRenderer.BILLET);
    }

    @SubscribeEvent
    public static void registerItemColors(RegisterColorHandlersEvent.Item event) {
        event.register((stack, tintIndex) -> {
            if (tintIndex != 0) {
                return 0xFFFFFFFF;
            }
            var level = Minecraft.getInstance().level;
            return 0xFF000000 | ForgeMetals.tint(HeatedMetal.metal(stack), HeatedMetal.temperatureNow(stack, level));
        }, PiForge.HEATED_METAL.get());
    }
}
