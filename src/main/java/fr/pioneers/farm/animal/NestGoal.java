package fr.pioneers.farm.animal;

import fr.pioneers.farm.device.DeviceSites;
import fr.pioneers.farm.device.NestBoxTile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.level.pathfinder.Path;
import org.jetbrains.annotations.Nullable;

/**
 * Poule qui a envie de pondre ({@link AnimalData#laying}) : elle va au pondoir le plus proche qui a de la place ({@link #RANGE} blocs) et y
 * pond ({@link AnimalEvents#layEgg}). Sans pondoir accessible, elle finit par pondre par terre ({@link AnimalEvents#LAY_PATIENCE}).
 * <p>
 * 1.0.5-beta (lot 41, retour 67) : meme choix que les mangeoires et abreuvoirs ({@link SiteChoice}) : pondoirs du plus proche au plus loin,
 * essayes par le vrai calcul de chemin, seul un chemin dont le bout est a portee ({@link #inReach}) est suivi ; le pondoir de l'enclos voisin
 * est ecarte 30 s ; en route, au plus {@link SiteChoice#MAX_REPATHS} nouveaux calculs puis ecarte.
 */
public class NestGoal extends Goal {

    public static final int RANGE = 12;
    private final Chicken hen;
    private final SiteChoice choice = new SiteChoice();
    @Nullable
    private BlockPos nest;
    @Nullable
    private Path path;
    private int time, repaths;

    public NestGoal(Chicken hen) {
        this.hen = hen;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** La regle de portee du pondoir : la poule y pond a moins de 1,3 bloc du centre, a moins de 1,6 bloc de hauteur. */
    public static boolean inReach(BlockPos nest, double x, double y, double z) {
        double dx = x - (nest.getX() + 0.5), dz = z - (nest.getZ() + 0.5);
        return dx * dx + dz * dz < 1.7 && Math.abs(y - nest.getY()) < 1.6;
    }

    @Override
    public boolean canUse() {
        //1.4.2 : tirage au lieu de « tickCount % 20 » (canUse n'est evalue qu'un tick sur deux, selon la parite de l'identifiant)
        if (hen.isBaby() || hen.getRandom().nextInt(10) != 0) {
            return false;
        }
        AnimalData d = PiAnimals.get(hen);
        if (d == null || !d.laying || d.male || !hen.onGround() && !hen.isInWater()) {
            return false;
        }
        long now = hen.level().getGameTime();
        choice.begin();
        SiteChoice.Choice c = choice.choose(candidates(now), now, 0, to -> hen.getNavigation().createPath(to, 1),
              (to, x, y, z) -> inReach(to, x, y, z));
        nest = c == null ? null : c.target();
        path = c == null ? null : c.path();
        return nest != null;
    }

    /** Pondoirs a place du rayon, non ecartes, du plus proche au plus loin. */
    private List<BlockPos> candidates(long now) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos p : DeviceSites.near(hen.level(), hen.getX(), hen.getY(), hen.getZ(), RANGE, DeviceSites.Kind.NEST)) {
            if (!choice.forgotten(p, now) && hen.level().getBlockEntity(p) instanceof NestBoxTile box && box.room()) {
                out.add(p);
            }
        }
        out.sort(Comparator.comparingDouble(p -> p.distToCenterSqr(hen.position())));
        return out;
    }

    @Override
    public void start() {
        time = 0;
        repaths = 0;
        if (path != null) {
            hen.getNavigation().moveTo(path, 1.1);
        }
    }

    @Override
    public boolean canContinueToUse() {
        AnimalData d = PiAnimals.get(hen);
        return nest != null && d != null && d.laying && time < 600 && hen.level().isLoaded(nest) && hen.level().getBlockEntity(nest) instanceof NestBoxTile box && box.room();
    }

    @Override
    public void tick() {
        time++;
        if (nest == null) {
            return;                                                        //1.0.5-beta (lot 21) : pondu (nest = null) : plus rien a faire
        }
        if (inReach(nest, hen.getX(), hen.getY(), hen.getZ())) {
            AnimalData d = PiAnimals.get(hen);
            if (d != null && hen.level().getBlockEntity(nest) instanceof NestBoxTile box && hen.level() instanceof ServerLevel sl) {
                AnimalEvents.layEgg(hen, d, box);
                sl.sendParticles(ParticleTypes.HAPPY_VILLAGER, nest.getX() + 0.5, nest.getY() + 0.8, nest.getZ() + 0.5, 3, 0.2, 0.1, 0.2, 0);
            }
            nest = null;
            return;
        }
        if (time % 20 == 0 && hen.getNavigation().isDone()) {
            //1.0.5-beta (lot 41) : arretee loin du pondoir : au plus MAX_REPATHS nouveaux calculs, puis il est ecarte
            choice.begin();
            Path p = ++repaths > SiteChoice.MAX_REPATHS ? null : choice.attempt(nest, to -> hen.getNavigation().createPath(to, 1),
                  (to, x, y, z) -> inReach(to, x, y, z));
            if (p == null) {
                choice.forget(nest, hen.level().getGameTime(), 0);
                nest = null;
                return;
            }
            hen.getNavigation().moveTo(p, 1.1);
        }
    }

    @Override
    public void stop() {
        nest = null;
        path = null;
        hen.getNavigation().stop();
    }
}
