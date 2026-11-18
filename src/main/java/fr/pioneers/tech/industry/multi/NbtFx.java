package fr.pioneers.tech.industry.multi;

import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

/**
 * 1.0.4-beta (lot 25) : PARTICULES des multiblocs en marche (fumees de cheminee, vapeur, etincelles, flammes, givre, poussiere), aux points
 * de {@link GeneratedNbtModels} ({@code T:industry/nbt_models.py FX / HOUSE_FX}). Appele cote client seulement (rendu du controleur, ou
 * {@code animateTick} des controleurs de l'age du bronze) ; particules vanilla, jamais de paquet reseau ; le reglage « Particules » du joueur
 * est applique par le jeu ({@code Level.addParticle}). Aucune geometrie : rien ne depasse de la structure (les particules partent de ses
 * bouches et s'elevent, comme la fumee d'une cheminee).
 */
public final class NbtFx {

    private NbtFx() {
    }

    /** Particules par tick a plein regime (moyenne) ; la bouffee (PUFF) part par salves. */
    public static float rate(GeneratedNbtModels.Fx.Kind kind) {
        return switch (kind) {
            case SMOKE -> 0.45F;
            case SOOT -> 0.7F;
            case FLAME -> 0.5F;
            case SPARK -> 0.6F;
            case EMBERS -> 0.12F;
            case STEAM -> 0.35F;
            case PUFF -> 1 / 70F;
            case FROST -> 0.4F;
            case DUST -> 0.35F;
        };
    }

    /** Emet {@code n} particules de la sorte au point (x, y, z) du monde, en marche. */
    public static void emit(Level level, GeneratedNbtModels.Fx.Kind kind, double x, double y, double z, RandomSource r, int n) {
        for (int i = 0; i < n; i++) {
            double ox = (r.nextDouble() - 0.5) * 0.4, oz = (r.nextDouble() - 0.5) * 0.4;
            switch (kind) {
                case SMOKE -> level.addParticle(ParticleTypes.LARGE_SMOKE, x + ox, y + 0.05, z + oz, ox * 0.02, 0.05 + r.nextDouble() * 0.03, oz * 0.02);
                case SOOT -> {
                    level.addParticle(ParticleTypes.LARGE_SMOKE, x + ox, y + 0.05, z + oz, 0, 0.07 + r.nextDouble() * 0.03, 0);
                    level.addParticle(ParticleTypes.SMOKE, x - ox, y + 0.1, z - oz, 0, 0.05, 0);
                }
                case FLAME -> level.addParticle(ParticleTypes.FLAME, x + ox * 0.6, y + 0.02, z + oz * 0.6, 0, 0.02 + r.nextDouble() * 0.02, 0);
                case SPARK -> level.addParticle(ParticleTypes.ELECTRIC_SPARK, x + ox, y, z + oz, ox * 0.4, 0.05 + r.nextDouble() * 0.1, oz * 0.4);
                case EMBERS -> level.addParticle(ParticleTypes.LAVA, x + ox * 0.5, y + 0.05, z + oz * 0.5, 0, 0, 0);
                case STEAM -> level.addParticle(ParticleTypes.CLOUD, x + ox * 0.6, y + 0.05, z + oz * 0.6, ox * 0.01, 0.04 + r.nextDouble() * 0.03, oz * 0.01);
                case PUFF -> {
                    //bouffee d'une soupape : une salve de vapeur blanche
                    for (int k = 0; k < 6; k++) {
                        double a = r.nextDouble() * Math.PI * 2;
                        level.addParticle(ParticleTypes.CLOUD, x + Math.cos(a) * 0.1, y + 0.05, z + Math.sin(a) * 0.1, Math.cos(a) * 0.03,
                              0.08 + r.nextDouble() * 0.04, Math.sin(a) * 0.03);
                    }
                }
                case FROST -> level.addParticle(ParticleTypes.SNOWFLAKE, x + ox * 2, y - r.nextDouble() * 0.5, z + oz * 2, 0, -0.02, 0);
                case DUST -> level.addParticle(dust(), x + ox * 1.5, y + 0.05, z + oz * 1.5, 0, 0.02, 0);
            }
        }
    }

    private static ParticleOptions dust() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.GRAVEL.defaultBlockState());
    }

    /**
     * Emission d'un tick : pour chaque point, un nombre de particules tire de la moyenne {@link #rate} x regime ({@code speed} 0..1).
     * Points deja passes dans le monde par l'appelant ({@code xyz} : x, y, z a la suite).
     */
    public static void tick(Level level, GeneratedNbtModels.Fx.Kind kind, double x, double y, double z, float speed, RandomSource r) {
        if (speed < 0.05F) {
            return;
        }
        float rate = rate(kind) * speed;
        int n = (int) rate + (r.nextFloat() < rate - (int) rate ? 1 : 0);
        if (n > 0) {
            emit(level, kind, x, y, z, r, n);
        }
    }
}
