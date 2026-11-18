package fr.pioneers.tech.industry.client;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.tech.industry.Impurity;
import net.minecraft.world.item.Item;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.IItemDecorator;
import net.minecraftforge.client.event.RegisterItemDecorationsEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Purete par symbole (client) : losange en haut a gauche de l'icone d'un objet impur, plein (tres impur) ou en contour (legerement impur) ;
 * ligne d'infobulle. Un objet pur n'a pas de symbole.
 */
public final class ImpurityClient {

    private ImpurityClient() {
    }

    private static final IItemDecorator DECORATOR = (graphics, font, stack, x, y) -> {
        int level = Impurity.of(stack);
        if (level <= 0) {
            return false;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(0, 0, 200);
        int cx = x + 3, cy = y + 3;
        int edge = 0xFF3A1A10, fill = 0xFFE0602A, light = 0xFFFFB070;
        for (int dy = -3; dy <= 3; dy++) {
            int w = 3 - Math.abs(dy);
            for (int dx = -w; dx <= w; dx++) {
                boolean border = Math.abs(dx) == w || Math.abs(dy) == 3;
                int c = border ? edge : level >= 2 ? (dy < 0 ? light : fill) : 0;
                if (c != 0) {
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, c);
                }
            }
        }
        if (level == 1) {
            //Symbole incomplet : seule la moitie basse est pleine
            for (int dy = 1; dy <= 2; dy++) {
                int w = 2 - Math.abs(dy) + 1;
                for (int dx = -w + 1; dx <= w - 1; dx++) {
                    graphics.fill(cx + dx, cy + dy, cx + dx + 1, cy + dy + 1, fill);
                }
            }
        }
        graphics.pose().popPose();
        return false;
    };

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class ModEvents {

        private ModEvents() {
        }

        @SubscribeEvent
        public static void decorations(RegisterItemDecorationsEvent event) {
            for (Item item : ForgeRegistries.ITEMS.getValues()) {
                event.register(item, DECORATOR);
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class GameEvents {

        private GameEvents() {
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            int level = Impurity.of(event.getItemStack());
            if (level > 0) {
                if (level >= 2) {
                    Tooltips.event(event.getToolTip()).warn("tooltip.pioneers.impurity." + level);
                } else {
                    Tooltips.event(event.getToolTip()).note("tooltip.pioneers.impurity." + level);
                }
            }
        }
    }
}
