package fr.pioneers.world.nature;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import net.minecraft.core.Holder;
import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.4 : l'eau n'est plus infinie (demande de Guillaume) : deux sources ne creent plus de nouvelle source, sauf dans les oceans et les rivieres.
 * L'eau se puise donc a la noria, aux pompes et aux seaux depuis une vraie etendue d'eau. Reglage {@code world.finiteWater}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FiniteWater {

    private FiniteWater() {
    }

    @SubscribeEvent
    public static void onSource(BlockEvent.CreateFluidSourceEvent event) {
        if (!event.getState().getFluidState().is(FluidTags.WATER) || !PiCommonConfig.get(PiCommonConfig.FINITE_WATER, true)) {
            return;
        }
        Holder<Biome> biome = event.getLevel().getBiome(event.getPos());
        if (!biome.is(BiomeTags.IS_OCEAN) && !biome.is(BiomeTags.IS_DEEP_OCEAN) && !biome.is(BiomeTags.IS_RIVER)) {
            event.setResult(Event.Result.DENY);
        }
    }
}
