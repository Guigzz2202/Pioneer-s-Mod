package fr.pioneers.client.render;

import fr.pioneers.Pioneers;
import fr.pioneers.common.projector.Projections;
import fr.pioneers.core.tooltip.Tooltips;
import net.minecraft.world.item.BlockItem;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Infobulle des controleurs de multiblocs : comment afficher leur plan. */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ProjectionTooltip {

    private ProjectionTooltip() {
    }

    @SubscribeEvent
    public static void onTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof BlockItem item && !Projections.options(item.getBlock()).isEmpty()) {
            Tooltips.event(event.getToolTip()).hint("projection.pioneers.tooltip");
        }
    }
}
