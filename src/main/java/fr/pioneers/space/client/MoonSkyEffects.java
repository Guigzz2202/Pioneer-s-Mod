package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.space.sky.client.Celestial.Body;
import fr.pioneers.space.sky.client.CelestialSkyRenderer;
import fr.pioneers.space.sky.client.SkyScene;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ciel des astres sans atmosphere (Lune, Mercure, Phobos, Deimos, ceinture, lunes des geantes, Pluton, Charon) :
 * noir en permanence, etoiles et Voie lactee toujours visibles, et tout ce qu'on verrait vraiment depuis cet astre
 * (voir {@link SkyScene}) : le Soleil a sa taille apparente, les planetes, la planete hote immobile avec ses phases...
 */
public class MoonSkyEffects extends DimensionSpecialEffects {

    private final SkyScene.Observer observer;
    private final CelestialSkyRenderer.Style style = new CelestialSkyRenderer.Style();

    public MoonSkyEffects(Body body) {
        super(Float.NaN, true, SkyType.NONE, false, false);
        this.observer = SkyScene.observer(body);
    }

    @Override
    public Vec3 getBrightnessDependentFogColor(Vec3 color, float brightness) {
        return new Vec3(0.02, 0.02, 0.03);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return false;
    }

    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderClouds(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, double camX, double camY, double camZ, Matrix4f projection) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(ClientLevel level, int ticks, float partialTick, LightTexture lightTexture, double camX, double camY, double camZ) {
        return true;
    }

    @Override
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projection, boolean isFoggy, Runnable setupFog) {
        FogType fluid = camera.getFluidInCamera();
        if (fluid != FogType.LAVA && fluid != FogType.POWDER_SNOW) {
            SkyScene scene = SkyScene.compute(observer, CelestialSkyRenderer.days(level), level.getTimeOfDay(partialTick) * 360.0F,
              fr.pioneers.space.mega.client.MegaClient.unlockedMask());
            CelestialSkyRenderer.render(scene, level, partialTick, poseStack, projection, style);
        }
        setupFog.run();
        return true;
    }
}
