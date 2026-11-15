package fr.pioneers.client.farm.leisure;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 1.5 : fumee de pipe et de cigare. Rond de fumee ({@code ring}) : part droit devant, ralentit, s'elargit et s'efface en 3 s ; volute : petite
 * bouffee grise qui monte, s'etale et se dissipe (textures de fumee de Minecraft, de la plus dense a la plus legere).
 */
public class SmokeRingParticle extends TextureSheetParticle {

    private final boolean ring;
    private final float base;
    private final SpriteSet sprites;

    protected SmokeRingParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, boolean ring) {
        super(level, x, y, z);
        this.ring = ring;
        this.sprites = sprites;
        xd = dx;
        yd = dy;
        zd = dz;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        lifetime = ring ? 50 + random.nextInt(15) : 28 + random.nextInt(24);
        base = ring ? 0.16F : 0.05F + random.nextFloat() * 0.04F;
        quadSize = base;
        float g = ring ? 0.94F : 0.82F + random.nextFloat() * 0.1F;
        setColor(g, g, g);
        alpha = ring ? 0.85F : 0.55F;
        if (ring) {
            pickSprite(sprites);
        } else {
            setSpriteFromAge(sprites);
        }
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = age / (float) lifetime;
        if (ring) {
            xd *= 0.93;
            yd = yd * 0.93 + 0.0012;
            zd *= 0.93;
            quadSize = base * (1 + 3.5F * (float) Math.sqrt(t));
            alpha = 0.85F * (1 - t * t);
        } else {
            xd *= 0.9;
            zd *= 0.9;
            yd = yd * 0.9 + 0.0025;
            quadSize = base * (1 + 2.5F * t);
            alpha = 0.55F * (1 - t);
            setSpriteFromAge(sprites);
        }
        move(xd, yd, zd);
    }

    @NotNull
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    public record RingProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx,
              double dy, double dz) {
            return new SmokeRingParticle(level, x, y, z, dx, dy, dz, sprites, true);
        }
    }

    public record WispProvider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx,
              double dy, double dz) {
            return new SmokeRingParticle(level, x, y, z, dx, dy, dz, sprites, false);
        }
    }
}
