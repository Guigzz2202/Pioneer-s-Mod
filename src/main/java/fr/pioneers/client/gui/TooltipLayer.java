package fr.pioneers.client.gui;

import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import javax.annotation.Nullable;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.1 : calque commun des infobulles des ecrans du mod (« les infobulles du Tableau des besoins sont tronquees »). Une infobulle demandee
 * pendant le rendu d'un ecran (au milieu d'une zone qui defile, donc sous un ciseau {@code enableScissor}, ou avant les boutons) n'est PAS
 * dessinee tout de suite : elle est retenue puis dessinee a la fin, apres tout l'ecran ({@code ScreenEvent.Render.Post}, priorite la plus
 * basse), en coordonnees de l'ecran, hors de tout ciseau et au-dessus de tout. Une seule infobulle par image : la derniere demandee.
 * Les lignes longues sont repliees (largeur {@link #width}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class TooltipLayer {

    @Nullable
    private static Consumer<GuiGraphics> pending;
    @Nullable
    private static Screen owner;

    private TooltipLayer() {
    }

    /** Largeur maximale d'une ligne d'infobulle (pixels) : la moitie de l'ecran, entre 120 et 220. */
    public static int width() {
        Minecraft mc = Minecraft.getInstance();
        int screen = mc.getWindow() == null ? 400 : mc.getWindow().getGuiScaledWidth();
        return Math.max(120, Math.min(220, screen / 2));
    }

    private static void set(Consumer<GuiGraphics> draw) {
        pending = draw;
        owner = Minecraft.getInstance().screen;
    }

    /** Lignes deja coupees. */
    public static void lines(Font font, List<? extends FormattedCharSequence> lines, int x, int y) {
        if (lines.isEmpty()) {
            return;
        }
        List<FormattedCharSequence> copy = new ArrayList<>(lines);
        set(g -> g.renderTooltip(font, copy, x, y));
    }

    /** Textes (replies a {@link #width}). */
    public static void components(Font font, List<? extends Component> lines, int x, int y) {
        List<FormattedCharSequence> out = new ArrayList<>();
        int w = width();
        for (Component c : lines) {
            List<FormattedCharSequence> split = font.split(c, w);
            out.addAll(split.isEmpty() ? List.of(FormattedCharSequence.EMPTY) : split);
        }
        lines(font, out, x, y);
    }

    public static void text(Font font, Component line, int x, int y) {
        components(font, List.of(line), x, y);
    }

    /** Infobulle d'un objet (celle du jeu : enchantements, evenements de Forge...). */
    public static void item(Font font, ItemStack stack, int x, int y) {
        if (stack.isEmpty()) {
            return;
        }
        ItemStack copy = stack.copy();
        set(g -> g.renderTooltip(font, copy, x, y));
    }

    /** Dessine tout de suite l'infobulle retenue (a appeler hors de tout ciseau ; sinon la fin du rendu de l'ecran s'en charge). */
    public static void flush(GuiGraphics g) {
        Consumer<GuiGraphics> draw = pending;
        pending = null;
        owner = null;
        if (draw != null) {
            draw.accept(g);
        }
    }

    @SubscribeEvent
    public static void onPre(ScreenEvent.Render.Pre event) {
        //une infobulle d'une image precedente (ou d'un autre ecran) ne reste jamais
        pending = null;
        owner = null;
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onPost(ScreenEvent.Render.Post event) {
        if (pending != null && (owner == null || owner == event.getScreen())) {
            flush(event.getGuiGraphics());
        } else {
            pending = null;
            owner = null;
        }
    }
}
