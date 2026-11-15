package fr.pioneers.client.season;

import fr.pioneers.Pioneers;
import fr.pioneers.season.SeasonWeather;
import fr.pioneers.season.Seasons;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.util.Mth;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 1) : saisons cote CLIENT : brouillard du matin d'automne (fond de ciel plus pale, {@link SeasonWeather#autumnMist} ; aucun
 * effet de jeu), calendrier oublie a la deconnexion. Lot 2 : la pluie et la neige ne sont plus dessinees ici (plus d'effets de l'Overworld
 * remplaces) : le rendu vanilla suit la vraie precipitation ({@code fr.pioneers.weather.Precipitations}, mixin {@code LevelRendererMixin}).
 */
public final class SeasonClient {

    private SeasonClient() {
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ForgeEvents {

        private ForgeEvents() {
        }

        @SubscribeEvent
        public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
            Seasons.clearClient();
        }

        /** Brume d'automne : la couleur du brouillard (et donc du fond de ciel) palit le matin ; en premier, avant les tempetes spatiales. */
        @SubscribeEvent(priority = EventPriority.HIGH)
        public static void onFogColor(ViewportEvent.ComputeFogColor event) {
            ClientLevel level = Minecraft.getInstance().level;
            if (level == null || event.getCamera().getFluidInCamera() != FogType.NONE) {
                return;
            }
            Seasons.Date date = Seasons.now(level);
            if (date == null) {
                return;
            }
            float k = SeasonWeather.autumnMist(date, level.getDayTime());
            if (k > 0) {
                float[] c = SeasonWeather.MIST_COLOUR;
                float light = Mth.clamp(level.getSkyDarken(1.0F), 0.35F, 1.0F);          //clarte du ciel (0,2 la nuit, 1 le jour)
                event.setRed(Mth.lerp(k, event.getRed(), c[0] * light));
                event.setGreen(Mth.lerp(k, event.getGreen(), c[1] * light));
                event.setBlue(Mth.lerp(k, event.getBlue(), c[2] * light));
            }
        }
    }
}
