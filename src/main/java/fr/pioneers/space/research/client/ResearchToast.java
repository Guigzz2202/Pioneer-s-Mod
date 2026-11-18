package fr.pioneers.space.research.client;

import fr.pioneers.space.research.ResearchNode;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.NotNull;

/**
 * Notification en haut a droite : "Recherche terminee" ou "Nouveau monde decouvert !".
 */
public class ResearchToast implements Toast {

    private final ResearchNode node;

    public ResearchToast(ResearchNode node) {
        this.node = node;
    }

    @NotNull
    @Override
    public Visibility render(@NotNull GuiGraphics graphics, @NotNull ToastComponent component, long time) {
        int accent = node.isDiscovery() ? 0xFF55CCFF : 0xFFFFC040;
        graphics.fill(0, 0, width(), height(), 0xF00A0F18);
        graphics.fill(0, 0, width(), 1, accent);
        graphics.fill(0, height() - 1, width(), height(), 0x6090A8C8);
        //Balayage lumineux a l'apparition
        float sweep = Mth.clamp(time / 600F, 0, 1);
        if (sweep < 1) {
            int x = (int) (sweep * width());
            graphics.fill(Math.max(0, x - 12), 1, x, height() - 1, (accent & 0x00FFFFFF) | 0x40000000);
        }
        graphics.renderItem(node.getIcon(), 8, 8);
        Component title = Component.translatable(node.isDiscovery() ? "research.pioneers.toast.discovery" : "research.pioneers.toast.done");
        graphics.drawString(component.getMinecraft().font, title, 30, 7, accent, false);
        graphics.drawString(component.getMinecraft().font, Component.translatable(node.getTranslationKey()), 30, 18, 0xFFFFFF, false);
        return time >= 6000 ? Visibility.HIDE : Visibility.SHOW;
    }
}
