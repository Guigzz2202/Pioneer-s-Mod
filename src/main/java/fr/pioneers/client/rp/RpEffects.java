package fr.pioneers.client.rp;

import fr.pioneers.Pioneers;
import fr.pioneers.core.profile.PioneerApi;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP) : effets client du pionnier : touche du Carnet, Eblouissement du Mineur-Fondeur (voile blanc de 3 s en sortant d'une galerie
 * en plein jour), nettoyage a la deconnexion. Les reperes visuels des vocations (blocs delabres, flux) sont dans {@link RpHighlights}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class RpEffects {

    private static boolean underground;
    private static long glareUntil, glareStart;

    private RpEffects() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        while (RpClientSetup.NOTEBOOK.consumeClick()) {
            if (mc.screen == null) {
                RpClient.openNotebook(null);
            }
        }
        LocalPlayer p = mc.player;
        if (p == null || p.tickCount % 5 != 0) {
            return;
        }
        boolean sky = p.level().canSeeSky(p.blockPosition());
        boolean under = p.getY() < 40 && !sky;
        double glare = PioneerApi.modifier(p, "miner.glare_seconds");
        if (underground && !under && sky && p.level().isDay() && glare > 0) {
            glareStart = System.currentTimeMillis();
            glareUntil = glareStart + (long) (glare * 1000);
        }
        if (under) {
            underground = true;
        } else if (sky) {
            underground = false;
        }
        RpHighlights.tick(mc);
    }

    static void renderGlare(GuiGraphics g, int w, int h) {
        long now = System.currentTimeMillis();
        if (now >= glareUntil) {
            return;
        }
        double t = (glareUntil - now) / (double) Math.max(1, glareUntil - glareStart);
        int a = (int) (200 * t);
        g.fill(0, 0, w, h, a << 24 | 0xFFF8E8);
    }

    /** 1.0.4-beta (lot 8) : reapparition, changement de dimension : le nouveau joueur local reprend aussitot le profil de l'ancien. */
    @SubscribeEvent
    public static void onClone(ClientPlayerNetworkEvent.Clone event) {
        RpClient.cloned(event.getOldPlayer(), event.getNewPlayer());
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        RpClient.clear();
        PioneerApi.remoteFeatures = null;
        glareUntil = 0;
        underground = false;
    }
}
