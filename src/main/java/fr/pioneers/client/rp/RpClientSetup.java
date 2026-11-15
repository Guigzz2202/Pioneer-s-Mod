package fr.pioneers.client.rp;

import com.mojang.blaze3d.platform.InputConstants;
import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.core.profile.RpItems;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.client.settings.KeyConflictContext;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.lwjgl.glfw.GLFW;

/** 1.9 (MAJ RP) : enregistrements client (touche du Carnet, bandeau des gains et messages, garde-robe : voir client/cosmetic). */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class RpClientSetup {

    /** Carnet du Restaurateur : J (K ouvre deja l'arbre de recherche). */
    public static final KeyMapping NOTEBOOK = new KeyMapping("key.pioneers.notebook", KeyConflictContext.IN_GAME, InputConstants.Type.KEYSYM,
          GLFW.GLFW_KEY_J, PiKeys.CATEGORY);

    private RpClientSetup() {
    }

    /** Fermoir du Carnet selon les Maitrises du joueur local : 0 sans, 0,5 laiton (des la premiere), 1 or (huit et plus). */
    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> ItemProperties.register(RpItems.NOTEBOOK.get(), Pioneers.rl("clasps"), (stack, level, entity, seed) -> {
            int n = RpClient.profile().masteries.size();
            return n >= 8 ? 1F : n > 0 ? 0.5F : 0F;
        }));
    }

    @SubscribeEvent
    public static void keys(RegisterKeyMappingsEvent event) {
        event.register(NOTEBOOK);
    }

    @SubscribeEvent
    public static void overlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.HOTBAR.id(), "rp_toasts", (gui, g, pt, w, h) -> {
            Toasts.render(g, w, h);
            RpEffects.renderGlare(g, w, h);
        });
    }
}
