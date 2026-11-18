package fr.pioneers.logistics.rail.train;

import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.level.Level;

/**
 * 0.20.0 : effets cote client des vehicules (fumee de la cheminee, vapeur des cylindres). Code commun : il n'utilise que l'API des
 * particules du monde, appelee seulement quand le monde est client. 0.20.2 : vapeur sans foyer (cylindres), diesel (echappement noir),
 * gaz (fumee claire, rien a l'hydrogene), electrique (etincelles au pantographe).
 */
public final class RailClientHooks {

    private RailClientHooks() {
    }

    static void tick(RailCarEntity e) {
        Level level = e.level();
        if (!e.working()) {
            return;
        }
        CarType type = e.carType();
        switch (type) {
            case STEAM -> {
                if (level.random.nextInt(2) == 0) {
                    //Cheminee : (0, 18.6, +6.2) px depuis le centre, dans le repere du vehicule
                    puff(e, ParticleTypes.LARGE_SMOKE, 0, 18.6, 6.2, 0.06);
                }
                cylinders(e, 6);
            }
            case FIRELESS -> cylinders(e, 4);
            case DIESEL -> {
                if (level.random.nextInt(2) == 0) {
                    puff(e, ParticleTypes.SMOKE, 0, 15.6, -0.9, 0.05);
                }
            }
            case GAS_LOCO -> {
                //Bit 4 : hydrogene, sans fumee
                if ((e.flags() & 16) == 0 && level.random.nextInt(4) == 0) {
                    puff(e, ParticleTypes.WHITE_ASH, 0, 15.8, -3.2, 0.03);
                }
            }
            case ELECTRIC -> {
                if ((e.flags() & 8) != 0 && level.random.nextInt(40) == 0) {
                    puff(e, ParticleTypes.ELECTRIC_SPARK, 0, 23.2, 0, 0.0);
                }
            }
            default -> {
            }
        }
    }

    /** Particule a (dx, dy, dz) px du centre du vehicule (dz vers l'avant), montant de {@code up}. */
    private static void puff(RailCarEntity e, ParticleOptions particle, double dx, double dy, double dz, double up) {
        Level level = e.level();
        double yaw = Math.toRadians(e.yawR);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double fx = dz / 16, sx = dx / 16;
        double x = e.getX() + sin * fx + cos * sx, y = e.getY() + dy / 16, z = e.getZ() + cos * fx - sin * sx;
        level.addParticle(particle, x, y, z, (level.random.nextDouble() - 0.5) * 0.02, up + level.random.nextDouble() * up * 0.6,
              (level.random.nextDouble() - 0.5) * 0.02);
    }

    /** Vapeur des cylindres, de part et d'autre. */
    private static void cylinders(RailCarEntity e, int odds) {
        Level level = e.level();
        if (level.random.nextInt(odds) != 0) {
            return;
        }
        double yaw = Math.toRadians(e.yawR);
        double sin = Math.sin(yaw), cos = Math.cos(yaw);
        double side = level.random.nextBoolean() ? 0.36 : -0.36;
        double cx = e.getX() + cos * side + sin * (13 / 16.0 - 0.5), cz = e.getZ() - sin * side + cos * (13 / 16.0 - 0.5);
        level.addParticle(ParticleTypes.CLOUD, cx, e.getY() + 0.42, cz, cos * side * 0.08, 0.01, -sin * side * 0.08);
    }
}
