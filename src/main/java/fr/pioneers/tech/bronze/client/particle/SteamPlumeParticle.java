package fr.pioneers.tech.bronze.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.26 : bouffee du panache d'une tour de refroidissement (demande de Guillaume : « qu'elle crache beaucoup plus de vapeur ») : grosse
 * bouffee blanche (textures de grosse fumee de Minecraft), qui sort vite du col, ralentit, s'elargit, derive avec le vent et se dissipe
 * en 7 a 12 s. Le vent tourne lentement avec l'heure du monde (toutes les tours penchent du meme cote).
 */
public class SteamPlumeParticle extends TextureSheetParticle {

    private final float base;
    private final double windX, windZ;

    protected SteamPlumeParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites) {
        super(level, x, y, z);
        xd = dx;
        yd = dy;
        zd = dz;
        lifetime = 140 + random.nextInt(100);
        base = 1.3F + random.nextFloat() * 0.9F;
        quadSize = base;
        hasPhysics = false;
        gravity = 0;
        friction = 1;
        float g = 0.9F + random.nextFloat() * 0.08F;
        setColor(g, g, Math.min(1, g + 0.03F));
        alpha = 0;
        roll = oRoll = random.nextFloat() * 6.28F;
        double wind = level.getGameTime() / 24000.0 * Math.PI * 2;
        windX = Math.cos(wind) * 0.0008;
        windZ = Math.sin(wind) * 0.0008;
        pickSprite(sprites);
    }

    @Override
    public void tick() {
        xo = x;
        yo = y;
        zo = z;
        oRoll = roll;
        if (age++ >= lifetime) {
            remove();
            return;
        }
        float t = age / (float) lifetime;
        xd = xd * 0.985 + windX;
        zd = zd * 0.985 + windZ;
        yd = Math.max(0.03, yd * 0.99);
        roll += 0.004F;
        move(xd, yd, zd);
        quadSize = base * (1 + 2.2F * t);
        alpha = t < 0.06F ? t / 0.06F * 0.8F : 0.8F * (float) Math.pow(1 - (t - 0.06F) / 0.94F, 1.5);
    }

    @NotNull
    @Override
    public ParticleRenderType getRenderType() {
        return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT;
    }

    /** Fabrique (une texture de grosse fumee au hasard). */
    public record Provider(SpriteSet sprites) implements ParticleProvider<SimpleParticleType> {

        @Override
        public Particle createParticle(@NotNull SimpleParticleType type, @NotNull ClientLevel level, double x, double y, double z, double dx,
              double dy, double dz) {
            return new SteamPlumeParticle(level, x, y, z, dx, dy, dz, sprites);
        }
    }
}
