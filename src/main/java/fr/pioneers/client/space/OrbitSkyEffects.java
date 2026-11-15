package fr.pioneers.client.space;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.client.sky.Celestial;
import fr.pioneers.client.sky.ExoSky;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.station.OrbitalStations;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.DimensionSpecialEffects;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Ciel de la dimension orbitale : celui de l'astre survole par la station ou l'on se trouve (sa region se deduit de la position).
 * Pour une planete, on emprunte le ciel de sa lune la plus proche, ou elle domine l'horizon.
 */
public class OrbitSkyEffects extends DimensionSpecialEffects {

    private final Map<Celestial.Body, MoonSkyEffects> skies = new EnumMap<>(Celestial.Body.class);
    /** Stations des etoiles et des exoplanetes : ciel d'orbite construit pour l'occasion. */
    private final Map<SolarSystem.Body, ExoSkyEffects> exoSkies = new EnumMap<>(SolarSystem.Body.class);

    public OrbitSkyEffects() {
        super(Float.NaN, true, SkyType.NONE, false, false);
    }

    private DimensionSpecialEffects sky(double x) {
        SolarSystem.Body body = OrbitalStations.bodyAt(x);
        if (body != null && (body.isStar() || body.sector() != SolarSystem.Sector.SOL)) {
            return exoSkies.computeIfAbsent(body, OrbitSkyEffects::exoOrbit);
        }
        Celestial.Body view = view(body);
        return skies.computeIfAbsent(view, MoonSkyEffects::new);
    }

    /**
     * Orbite d'une etoile : elle remplit une bonne part du ciel, a cote de la station. Orbite d'une exoplanete : la planete immense
     * sous la station, et son etoile.
     */
    private static ExoSkyEffects exoOrbit(SolarSystem.Body body) {
        String name = body.name().toLowerCase(Locale.ROOT);
        if (body.isStar()) {
            return new ExoSkyEffects(new ExoSky(false,
                  ExoSky.Obj.fixed(name, 16, true, body.color, 200, 12).halo(2.4)).dayStars(1), false);
        }
        SolarSystem.Body star = body.getParent() == null ? SolarSystem.Body.SUN : body.getParent();
        while (star.getParent() != null && !star.isStar()) {
            star = star.getParent();
        }
        return new ExoSkyEffects(new ExoSky(false,
              ExoSky.Obj.moving(star.name().toLowerCase(Locale.ROOT), 1.2, true, star.color, 0, 0).halo(5),
              ExoSky.Obj.fixed(name, 22, false, 0xFFFFFF, 30, -34)).dayStars(1), false);
    }

    /** Observateur du ciel : l'astre lui-meme s'il a un ciel, sinon une lune proche de la planete. */
    private static Celestial.Body view(SolarSystem.Body body) {
        if (body == null) {
            return Celestial.Body.MOON;
        }
        return switch (body) {
            case EARTH -> Celestial.Body.MOON;
            case MARS -> Celestial.Body.PHOBOS;
            case JUPITER -> Celestial.Body.IO;
            case SATURN -> Celestial.Body.ENCELADUS;
            case URANUS -> Celestial.Body.MIRANDA;
            case NEPTUNE -> Celestial.Body.TRITON;
            default -> {
                try {
                    yield Celestial.Body.valueOf(body.name());
                } catch (IllegalArgumentException e) {
                    yield Celestial.Body.MOON;
                }
            }
        };
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
    public boolean renderSky(ClientLevel level, int ticks, float partialTick, PoseStack poseStack, Camera camera, Matrix4f projection, boolean isFoggy,
          Runnable setupFog) {
        return sky(camera.getPosition().x).renderSky(level, ticks, partialTick, poseStack, camera, projection, isFoggy, setupFog);
    }
}
