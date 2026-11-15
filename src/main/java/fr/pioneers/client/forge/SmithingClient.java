package fr.pioneers.client.forge;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.forge.smithing.HandForged;
import fr.pioneers.registries.PiForge;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Forge primaire, equipe "smithing", cote client : rendus du foyer et des enclumes, ouverture de l'ecran de forge, mention "Forge main"
 * dans les infobulles.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class SmithingClient {

    private SmithingClient() {
    }

    @SubscribeEvent
    public static void renderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerBlockEntityRenderer(PiForge.ANVIL_TILE.get(), AnvilRenderer::new);
        event.registerBlockEntityRenderer(PiForge.FORGE_HEARTH_TILE.get(), HearthRenderer::new);
        event.registerBlockEntityRenderer(PiForge.QUENCH_BASIN_TILE.get(), QuenchBasinRenderer::new);
    }

    /** Ouvre l'ecran de forge de l'enclume en {@code pos} (appele par {@code AnvilTile.use} cote client). */
    public static void open(BlockPos pos) {
        Minecraft.getInstance().setScreen(new SmithingScreen(pos));
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class TooltipEvents {

        private TooltipEvents() {
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            if (HandForged.is(event.getItemStack())) {
                Tooltips.event(event.getToolTip()).note("tooltip.pioneers.hand_forged");
            }
        }
    }
}
