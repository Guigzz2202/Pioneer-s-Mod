package fr.pioneers.tech.energy;

import fr.pioneers.api.environment.BodyEnvironment;
import fr.pioneers.api.machine.unit.RenewableUnit;
import fr.pioneers.core.machine.MachineTile;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.environment.Environments;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9.2 (retours de Guillaume : « un meilleur design, pas seulement la tete ») : la nacelle est un bloc plein, pose en haut d'un mat que le
 * joueur construit lui-meme ; le rotor est un bloc a part ({@link WindRotorBlock}) pose sur sa face avant. Sans rotor, rien ; avec un rotor,
 * la production maximale est celle du tier le plus faible des deux, et le disque a degager est celui du rotor. Eoliennes posees avant la
 * 1.9.2 (sans rotor) : a la premiere mise a jour, le rotor du meme tier est pose devant elles (ou lache en objet si la case est prise).
 * <p>
 * 0.20.45 : eolienne. La nacelle se pose en haut d'un mat (n'importe quels blocs) ; le rotor tourne devant elle (face avant), dans un disque
 * de {@link EnergyKind#WIND_RADIUS} blocs a garder libre (feuilles, murs, autres pales : le rendement tombe vite).
 * Facteur = altitude (10 % au niveau de la mer, plein a 100 blocs au-dessus) x degagement² x vent x air.
 * Vent : rafales lentes (0,55 a 1,15 selon l'heure et le lieu), pluie x1,25, orage x1,6. Air : 1 sur Terre, rien dans le vide ni en
 * station, 0,5 dans le Nether, sinon selon la densite de l'atmosphere (Mars 0,375, Titan 1,5).
 * <p>
 * Interface : 8 altitude x1000, 9 degagement x1000, 10 vent x1000 (meteo et air compris), 11 tier du rotor + 1 (0 : pas de rotor).
 * <p>
 * API Machine (lot 3) : logique ({@link fr.pioneers.api.machine.unit.RenewableUnit}), code repris de l'ancienne {@code WindTurbineTile}.
 */
public class WindLogic extends RenewableUnit {

    private double altitude, clear, wind;
    /** Tier du rotor pose devant (-1 : aucun), releve a chaque mesure. */
    private int rotorTier = -1;
    /** 1.9.2 : eolienne d'une sauvegarde d'avant les rotors separes (pas de cle {@code Rotor192}) : son rotor est rendu a la mise a jour. */
    private boolean legacy;

    public WindLogic(MachineTile.Access machine, int tier) {
        super(machine, tier, EnergyKind.WIND);
    }

    @Override
    public void tick() {
        if (legacy && level() instanceof ServerLevel server) {
            legacy = false;
            giveBackRotor(server);
        }
        super.tick();
    }

    private Direction facing() {
        return machine.tile().facing();
    }

    /** 1.9.2 : pose devant une eolienne d'avant la 1.9.2 le rotor qu'elle portait (meme tier), ou le lache si la case est prise. */
    private void giveBackRotor(ServerLevel level) {
        BlockPos front = pos().relative(facing());
        if (WindRotorBlock.facesAway(level.getBlockState(front), facing())) {
            return;
        }
        BlockState rotor = PiEnergy.ROTORS.get(tier).getBlock().defaultBlockState().setValue(WindRotorBlock.FACING, facing());
        if (level.getBlockState(front).canBeReplaced()) {
            level.setBlock(front, rotor, Block.UPDATE_ALL);
        } else {
            Block.popResource(level, front.relative(facing()), new ItemStack(PiEnergy.ROTORS.get(tier).getBlock()));
        }
        machine.changed();
    }

    /** Tier du rotor pose devant la nacelle (en {@code pos}, tournee vers {@code facing}) et tourne comme elle, ou -1. */
    public static int rotorIn(Level level, BlockPos pos, Direction facing) {
        BlockState state = level.getBlockState(pos.relative(facing));
        return WindRotorBlock.facesAway(state, facing) ? ((WindRotorBlock) state.getBlock()).tier : -1;
    }

    /** Production maximale : celle du tier le plus faible de la nacelle et du rotor. */
    @Override
    public long peak() {
        return kind.peak[rotorTier < 0 ? tier : Math.min(tier, rotorTier)];
    }

    @Override
    protected double survey(ServerLevel level) {
        rotorTier = rotorIn(level, pos(), facing());
        if (rotorTier < 0) {
            altitude = clear = wind = 0;
            return 0;
        }
        altitude = clamp((pos().getY() - level.getSeaLevel()) / 100.0, 0.1, 1);
        clear = clearance(level);
        double gust = 0.85 + 0.2 * Math.sin(level.getGameTime() / 2400.0 * Math.PI * 2 + (pos().asLong() & 255) / 40.0)
                      + 0.1 * Math.sin(level.getGameTime() / 9100.0 * Math.PI * 2);
        double weather = level.isThundering() ? 1.6 : level.isRaining() ? 1.25 : 1;
        wind = gust * weather * air(level);
        return altitude * clear * clear * wind;
    }

    /** Part libre du disque des pales (devant la nacelle, centre sur elle). */
    private double clearance(Level level) {
        Direction front = machine.tile().facing();
        Direction side = front.getClockWise();
        int r = EnergyKind.WIND_RADIUS[Math.max(0, rotorTier)];
        BlockPos center = pos().relative(front);
        int total = 0, free = 0;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (a * a + b * b > r * r + r || a == 0 && b == 0) {
                    continue;                                       //hors du disque, ou le moyeu du rotor lui-meme
                }
                at.set(center.getX() + side.getStepX() * a, center.getY() + b, center.getZ() + side.getStepZ() * a);
                total++;
                BlockState state = level.getBlockState(at);
                if (state.isAir() || state.getCollisionShape(level, at).isEmpty() && state.getFluidState().isEmpty()) {
                    free++;
                }
            }
        }
        return total == 0 ? 0 : free / (double) total;
    }

    /** Densite de l'air de la dimension (Terre = 1). */
    static double air(ServerLevel level) {
        if (level.dimension() == PiDimensions.ORBIT) {
            return 0;
        }
        if (level.dimension() == Level.NETHER) {
            return 0.5;
        }
        BodyEnvironment env = Environments.get(level);
        if (env.breathable()) {
            return 1;
        }
        if (env.hazard() == BodyEnvironment.Hazard.VACUUM || env.nitrogen() <= 0) {
            return 0;
        }
        return clamp(0.25 + 0.25 * env.nitrogen(), 0.3, 1.5);
    }

    @Override
    protected long detail(int index) {
        return switch (index) {
            case 0 -> Math.round(altitude * 1000);
            case 1 -> Math.round(clear * 1000);
            case 2 -> Math.round(wind * 1000);
            default -> rotorTier + 1;
        };
    }

    @Override
    public void save(CompoundTag tag) {
        super.save(tag);
        tag.putBoolean("Rotor192", true);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        legacy = !tag.contains("Rotor192");
    }
}
