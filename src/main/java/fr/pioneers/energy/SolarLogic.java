package fr.pioneers.energy;

import fr.pioneers.api.environment.BodyEnvironment;
import fr.pioneers.api.machine.unit.RenewableUnit;
import fr.pioneers.core.Directions;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.environment.Environments;
import fr.pioneers.space.event.SpaceEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.45 : panneau solaire. Facteur = soleil (cosinus de sa hauteur, nul la nuit) x meteo (pluie 0,35, orage 0,2) x lumiere de l'astre
 * (distance a l'etoile : Mercure x4 au plus, Mars 0,43, Jupiter 0,04... ; sans atmosphere x1,3, nuages de Venus et brume de Titan x0,1) ;
 * ciel ouvert exige. En station orbitale, le soleil est presque toujours la (0,9). Les panneaux qui se touchent egalisent leurs reserves :
 * un seul cable suffit pour tout un champ.
 * <p>
 * Interface : 8 soleil x1000, 9 meteo x1000, 10 lumiere de l'astre x1000, 11 ciel ouvert (0/1).
 * <p>
 * API Machine (lot 3) : logique du panneau ({@link RenewableUnit}), code repris de l'ancienne {@code SolarPanelTile}.
 */
public class SolarLogic extends RenewableUnit {

    private double sun, weather, light;
    private boolean sky;

    public SolarLogic(MachineTile.Access machine, int tier) {
        super(machine, tier, EnergyKind.SOLAR);
    }

    @Override
    protected double survey(ServerLevel level) {
        BlockPos above = pos().above();
        boolean orbit = level.dimension() == PiDimensions.ORBIT;
        sky = level.dimensionType().hasSkyLight() && level.canSeeSky(above);
        if (orbit) {
            sun = 0.9;
        } else if (!level.dimensionType().hasSkyLight()) {
            sun = 0;
        } else {
            sun = clamp(Math.cos(level.getSunAngle(1.0F)) * 1.15, 0, 1);
        }
        weather = level.isRainingAt(above) ? level.isThundering() ? 0.2 : 0.35 : 1;
        light = irradiance(level, pos().getX());
        return sky ? sun * weather * light : 0;
    }

    /** Lumiere recue sur l'astre ou se trouve le panneau, relative a la Terre. */
    public static double irradiance(ServerLevel level, double x) {
        Body body = SpaceEvents.bodyAt(level, x);
        double flux = flux(body);
        BodyEnvironment env = Environments.get(level);
        double air = env.hazard() == BodyEnvironment.Hazard.VACUUM ? 1.3 : 1;
        if (body == Body.VENUS || body == Body.TITAN) {
            air = 0.1;
        }
        return Math.min(4, flux * air);
    }

    /** Flux de l'etoile a la distance de l'astre (Terre = 1) ; les lunes prennent celui de leur planete. */
    static double flux(@Nullable Body body) {
        if (body == null) {
            return 1;
        }
        return switch (body) {
            case MERCURY -> 4;
            case VENUS -> 1.9;
            case MARS, PHOBOS, DEIMOS -> 0.43;
            case ASTEROID_BELT, CERES, VESTA, PALLAS, HYGIEA -> 0.13;
            case JUPITER, IO, EUROPA, GANYMEDE, CALLISTO -> 0.037;
            case SATURN, ENCELADUS, TITAN -> 0.011;
            case URANUS, MIRANDA, TITANIA -> 0.0027;
            case NEPTUNE, TRITON -> 0.0011;
            case PLUTO, CHARON -> 0.0006;
            case PROXIMA_B -> 0.65;
            case PROXIMA_D -> 1.2;
            case TRAPPIST_1B -> 4;
            case TRAPPIST_1C -> 2.2;
            case TRAPPIST_1D -> 1.1;
            case TRAPPIST_1E -> 0.66;
            case TRAPPIST_1F -> 0.38;
            case TRAPPIST_1G -> 0.26;
            case TRAPPIST_1H -> 0.13;
            default -> 1;
        };
    }

    /** Egalise les reserves avec les panneaux voisins (l'energie coule vers celui qui touche un cable et se vide). */
    @Override
    protected void share(ServerLevel level) {
        for (Direction d : Directions.ALL) {
            BlockPos at = pos().relative(d);
            if (!level.isLoaded(at)) {
                continue;
            }
            BlockEntity other = level.getBlockEntity(at);
            if (other instanceof SolarPanelTile panel) {
                long mine = machine.energy().getStored(), theirs = panel.getEnergy().getStored();
                if (mine > theirs + 1) {
                    long moved = panel.getEnergy().insert((mine - theirs) / 2, false);
                    machine.energy().extract(moved, false);
                }
            }
        }
    }

    @Override
    protected long detail(int index) {
        return switch (index) {
            case 0 -> Math.round(sun * 1000);
            case 1 -> Math.round(weather * 1000);
            case 2 -> Math.round(light * 1000);
            default -> sky ? 1 : 0;
        };
    }
}
