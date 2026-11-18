package fr.pioneers.space.sky.client;

import fr.pioneers.Pioneers;
import fr.pioneers.space.sky.client.Celestial.Body;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.material.FogType;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Sur Terre, le ciel vanilla garde son Soleil et sa Lune ; on y ajoute les planetes a leur vraie place
 * (Venus etoile du berger, Jupiter, Mars, Saturne...), visibles des que la nuit tombe.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkyEvents {

    private SkyEvents() {
    }

    private static final CelestialSkyRenderer.Style STYLE = new CelestialSkyRenderer.Style();

    static {
        STYLE.atmosphere = true;
        STYLE.drawSun = false;
        STYLE.drawStars = false;
        STYLE.pointsOnly = true;
    }

    /** Aucune intemperie sur les astres : la pluie et l'orage de la Terre ne s'y propagent pas (ciel, eclairage, sons). */
    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level != null && level.dimension().location().getNamespace().equals(Pioneers.MODID)) {
            level.setRainLevel(0);
            level.setThunderLevel(0);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_SKY) {
            return;
        }
        ClientLevel level = Minecraft.getInstance().level;
        if (level == null || level.dimension() != Level.OVERWORLD || event.getCamera().getFluidInCamera() != FogType.NONE) {
            return;
        }
        float partialTick = event.getPartialTick();
        STYLE.starBrightness = level.getStarBrightness(partialTick);
        STYLE.rain = level.getRainLevel(partialTick);
        if (STYLE.starBrightness <= 0.01F || STYLE.rain >= 0.99F) {
            return;
        }
        SkyScene scene = SkyScene.compute(SkyScene.observer(Body.EARTH), CelestialSkyRenderer.days(level), level.getTimeOfDay(partialTick) * 360.0F);
        CelestialSkyRenderer.render(scene, level, partialTick, event.getPoseStack(), event.getProjectionMatrix(), STYLE);
    }
}
