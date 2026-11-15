package fr.pioneers.logistics.naval;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

/**
 * 0.20.11 : effets du navire cote client (appele par {@link ShipEntity#tick}, code commun : particules seulement) : fumee de la cheminee
 * (vapeur et diesel), ecume de l'etrave et sillage a l'arriere en marche, eclaboussures des roues a aubes.
 */
public final class NavalClientHooks {

    private NavalClientHooks() {
    }

    static void tick(ShipEntity e) {
        Level level = e.level();
        int tier = e.tier();
        float speed = e.speed();
        boolean working = (e.flags() & 2) != 0;
        double a = Math.toRadians(e.heading());
        double fx = Math.sin(a), fz = Math.cos(a);
        double hl = ShipKind.length(tier) / 2.0;
        var rnd = level.random;
        if (working && rnd.nextInt(3) == 0) {
            //Cheminees (MKI : deux cheminees jumelles au milieu ; MKII : sur la superstructure arriere)
            for (double[] f : ShipKind.funnels(tier)) {
                double x = e.getX() + fx * f[1] + fz * f[0], z = e.getZ() + fz * f[1] - fx * f[0];
                level.addParticle(ParticleTypes.LARGE_SMOKE, x, e.getY() + f[2], z, fx * -0.02, 0.06, fz * -0.02);
            }
        }
        if (speed > 0.04F) {
            //Ecume a l'etrave
            for (int i = 0; i < 2; i++) {
                double side = (rnd.nextDouble() - 0.5) * ShipKind.beam(tier) * 0.6;
                level.addParticle(ParticleTypes.SPLASH, e.getX() + fx * hl + fz * side, e.getY() + 0.05, e.getZ() + fz * hl - fx * side, 0, 0.05, 0);
            }
            //Sillage
            double bx = e.getX() - fx * (hl + 0.3), bz = e.getZ() - fz * (hl + 0.3);
            level.addParticle(ParticleTypes.BUBBLE_POP, bx + (rnd.nextDouble() - 0.5), e.getY() + 0.02, bz + (rnd.nextDouble() - 0.5), 0, 0, 0);
            if (rnd.nextInt(2) == 0) {
                level.addParticle(ParticleTypes.SPLASH, bx, e.getY() + 0.1, bz, -fx * 0.05, 0.08, -fz * 0.05);
            }
            if (tier == 0) {
                //Roues a aubes
                double w = ShipKind.beam(0) / 2.0 + 0.1;
                for (double s : new double[]{-1, 1}) {
                    level.addParticle(ParticleTypes.SPLASH, e.getX() + fz * w * s - fx * 0.06, e.getY() + 0.2, e.getZ() - fx * w * s - fz * 0.06, 0, 0.12,
                          0);
                }
            }
        }
    }
}
