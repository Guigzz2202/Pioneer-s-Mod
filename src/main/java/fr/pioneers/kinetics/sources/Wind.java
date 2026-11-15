package fr.pioneers.kinetics.sources;

import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.environment.Environments;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;

/**
 * Vent de chaque astre (m/s) et densite de l'air (kg/m3), avec rafales lentes, meteo et altitude. La Lune n'a pas de vent ; Mars en a
 * beaucoup mais dans un air si fin qu'un moulin y tourne a peine ; Titan, air epais et vent faible ; les geantes, tempetes permanentes.
 */
public final class Wind {

    private Wind() {
    }

    /** Vent moyen de l'astre (m/s). */
    public static double base(Level level) {
        if (level.dimension() == Level.OVERWORLD) {
            return 7;
        }
        if (level.dimension() == Level.NETHER) {
            return 2.5;
        }
        if (level.dimension() == Level.END || level.dimension() == PiDimensions.ORBIT) {
            return 0;
        }
        SolarSystem.Body body = SolarSystem.fromDimension(level.dimension());
        if (body == null) {
            return 6;
        }
        return switch (body) {
            case MARS -> 12;
            case VENUS -> 1.5;
            case TITAN -> 1.2;
            case TRITON, PLUTO -> 4;
            case JUPITER, SATURN, URANUS, NEPTUNE -> 30;
            case TOLIMAN_B -> 8;
            case PROXIMA_B -> 14;
            case TRAPPIST_1D, TRAPPIST_1E -> 6;
            case TRAPPIST_1F -> 9;
            default -> 0;
        };
    }

    /** Densite de l'air (kg/m3) : 1,2 sur Terre. */
    public static double density(Level level) {
        if (level.dimension() == Level.END) {
            return 0.2;
        }
        return 1.2 * Environments.atmosphereDensity(level.dimension());
    }

    /** Rafales : facteur lent (0,6 .. 1,4), le meme pour tout le monde (memes rafales sur deux moulins voisins). */
    public static double gust(Level level) {
        double t = level.getGameTime();
        return 1 + 0.22 * Math.sin(t / 611.0) + 0.12 * Math.sin(t / 173.0 + 1.3) + 0.06 * Math.sin(t / 47.0 + 0.4);
    }

    /** Altitude : x0,3 au niveau de la mer, x1 a Y 120, x1,3 au-dela de Y 160. */
    public static double altitude(Level level, BlockPos pos) {
        double sea = level.getSeaLevel();
        return Math.max(0.3, Math.min(1.3, 0.3 + 0.7 * (pos.getY() - sea) / (120.0 - sea)));
    }

    /** Vent au point (m/s). */
    public static double at(Level level, BlockPos pos) {
        double weather = level.isThundering() ? 1.6 : level.isRaining() ? 1.2 : 1;
        return base(level) * gust(level) * altitude(level, pos) * weather;
    }
}
