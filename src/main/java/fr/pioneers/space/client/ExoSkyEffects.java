package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.space.sky.client.ExoSky.Obj;
import fr.pioneers.space.sky.client.ExoSky;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.material.FogType;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;

/**
 * Ciels des exoplanetes et des geantes gazeuses (voir {@link ExoSky}).
 * <ul>
 *     <li>Toliman b : deux soleils (Rigil Kentaurus et Toliman) dans un ciel turquoise epais ;</li>
 *     <li>Proxima b : Proxima immobile, enorme et rouge, posee sur l'horizon (rotation synchrone), le couple Alpha au loin ;</li>
 *     <li>Proxima d : Proxima geante, dans un ciel noir ;</li>
 *     <li>TRAPPIST-1 : le soleil rouge fixe et les planetes soeurs, immenses dans le ciel ;</li>
 *     <li>geantes gazeuses : ciel voile, soleil diffus, pas d'etoiles.</li>
 * </ul>
 */
public class ExoSkyEffects extends DimensionSpecialEffects {

    private final ExoSky sky;
    private final boolean foggy;

    public ExoSkyEffects(ExoSky sky, boolean foggy) {
        super(Float.NaN, true, SkyType.NONE, false, false);
        this.sky = sky;
        this.foggy = foggy;
    }

    public static ExoSkyEffects tolimanB() {
        return new ExoSkyEffects(new ExoSky(true,
              Obj.moving("centauri_a", 0.9, true, 0xFFF0C8, 0, 0),
              Obj.moving("centauri_b", 0.35, true, 0xFFB060, 38, 12).halo(9),
              Obj.fixed("trappist_1h", 0.12, false, 0xFFFFFF, 140, 62)), false);
    }

    public static ExoSkyEffects proximaB() {
        return new ExoSkyEffects(new ExoSky(true,
              Obj.fixed("proxima", 3.2, true, 0xFF5030, 262, 7).halo(5),
              Obj.moving("centauri_a", 0.14, true, 0xFFF0C8, 160, 30).halo(10),
              Obj.moving("centauri_b", 0.1, true, 0xFFB060, 163, 31).halo(10),
              Obj.fixed("proxima_d", 0.35, false, 0xFFFFFF, 230, 24)).dayStars(0.35F), false);
    }

    public static ExoSkyEffects proximaD() {
        return new ExoSkyEffects(new ExoSky(false,
              Obj.moving("proxima", 5.5, true, 0xFF5030, 0, 0).halo(4),
              Obj.moving("proxima_b", 0.5, false, 0xFFFFFF, 110, -15),
              Obj.moving("centauri_a", 0.12, true, 0xFFF0C8, 200, 20).halo(10)), false);
    }

    /** Un monde de TRAPPIST-1 : le soleil fixe a la hauteur donnee, et les planetes voisines (nom, rayon, azimut, hauteur). */
    public static ExoSkyEffects trappist(double sunElevation, boolean atmosphere, Object... neighbors) {
        ExoSky sky = new ExoSky(atmosphere, Obj.fixed("trappist_1", 2.6, true, 0xFF4A30, 270, sunElevation).halo(5)).dayStars(0.45F);
        for (int i = 0; i < neighbors.length; i += 4) {
            sky.objects.add(Obj.fixed((String) neighbors[i], ((Number) neighbors[i + 1]).doubleValue(), false, 0xFFFFFF,
                  ((Number) neighbors[i + 2]).doubleValue(), ((Number) neighbors[i + 3]).doubleValue()));
        }
        return new ExoSkyEffects(sky, false);
    }

    public static ExoSkyEffects gasGiant() {
        return new ExoSkyEffects(new ExoSky(true, Obj.moving("sun", 0.6, true, 0xFFE8C0, 0, 10).halo(10)).veiled(), true);
    }

    @NotNull
    @Override
    public Vec3 getBrightnessDependentFogColor(@NotNull Vec3 color, float brightness) {
        return color.multiply(brightness * 0.94F + 0.06F, brightness * 0.94F + 0.06F, brightness * 0.91F + 0.09F);
    }

    @Override
    public boolean isFoggyAt(int x, int y) {
        return foggy;
    }

    @Nullable
    @Override
    public float[] getSunriseColor(float timeOfDay, float partialTicks) {
        return null;
    }

    @Override
    public boolean renderClouds(@NotNull ClientLevel level, int ticks, float partialTick, @NotNull PoseStack poseStack, double camX, double camY, double camZ,
          @NotNull Matrix4f projection) {
        return true;
    }

    @Override
    public boolean renderSnowAndRain(@NotNull ClientLevel level, int ticks, float partialTick, @NotNull LightTexture lightTexture, double camX, double camY,
          double camZ) {
        return true;
    }

    @Override
    public boolean renderSky(@NotNull ClientLevel level, int ticks, float partialTick, @NotNull PoseStack poseStack, @NotNull Camera camera,
          @NotNull Matrix4f projection, boolean isFoggy, @NotNull Runnable setupFog) {
        FogType fluid = camera.getFluidInCamera();
        if (fluid != FogType.LAVA && fluid != FogType.POWDER_SNOW) {
            sky.render(level, partialTick, poseStack, projection, camera.getPosition());
            //1.0.7 (lots 7 et 8) : megastructures des geantes dans leur ciel (feux, anneaux, aurore, colonne), une fois debloquees
            fr.pioneers.space.mega.client.GiantMegaSky.render(level, partialTick, poseStack);
        }
        setupFog.run();
        return true;
    }
}
