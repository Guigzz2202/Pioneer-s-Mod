package fr.pioneers.farm.animal;

import fr.pioneers.farm.kitchen.StationTiles;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ItemParticleOption;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.FluidTags;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.pathfinder.Path;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Un animal domestique qui a faim ou soif va a la mangeoire ou a l'abreuvoir le plus proche (16 blocs), ou boire a l'eau du monde (8 blocs) ;
 * arrive, il mange ou boit (particules, son), puis reprend sa vie.
 * <p>
 * 1.0.5-beta (lot 41, retour 67 : « mes animaux focus les autres abreuvoirs et se laisse deperir au lieu de boire dans le leur ») : la cible
 * n'est plus simplement la plus proche : les candidats, du plus proche au plus loin, sont essayes par le vrai calcul de chemin
 * ({@link SiteChoice}) ; seul un chemin dont le bout est a portee ({@link #inReach}, la regle qui fait manger et boire) est suivi. Une cible
 * injoignable (l'abreuvoir de l'enclos voisin, derriere la cloture) est ecartee 30 s et la suivante est essayee ; en route, une bete
 * arretee loin de sa cible recalcule au plus {@link SiteChoice#MAX_REPATHS} fois puis l'ecarte (jamais bloquee contre une cloture). Au plus
 * {@link SiteChoice#PATHS_PER_DECISION} calculs de chemin par decision, une decision toutes les 4 a 8 s (compte a rebours ; jamais
 * {@code tickCount % n}).
 */
public class NeedGoal extends Goal {

    static final float THRESHOLD = 65;
    /** Candidats essayes au plus par sorte de lieu (mangeoires, abreuvoirs, eau du monde). */
    static final int CANDIDATES = 3;
    private final Animal mob;
    private final AnimalSpecies species;
    private final SiteChoice choice = new SiteChoice();
    @Nullable
    private BlockPos target;
    @Nullable
    private Path path;
    /** 0 mangeoire, 1 abreuvoir, 2 eau du monde. */
    private int kind;
    private int cooldown, timeout, idle, repaths;
    private boolean done;

    public NeedGoal(Animal mob, AnimalSpecies species) {
        this.mob = mob;
        this.species = species;
        setFlags(EnumSet.of(Flag.MOVE, Flag.LOOK));
        cooldown = mob.getRandom().nextInt(60);
    }

    /** Les choix de lieu de la bete (cibles ecartees ; GameTests). */
    public SiteChoice choice() {
        return choice;
    }

    /**
     * La regle de portee : une bete debout en (x, y, z), de largeur {@code width}, mange ou boit a la cible (centre du bas de la case a
     * 1,4 bloc + la demi-largeur, a moins de 1,6 bloc de hauteur). Sert au choix (bout du chemin) et a l'arrivee.
     */
    public static boolean inReach(BlockPos target, double x, double y, double z, float width) {
        double dx = target.getX() + 0.5 - x, dz = target.getZ() + 0.5 - z;
        double reach = 1.4 + width / 2;
        return dx * dx + dz * dz <= reach * reach && Math.abs(target.getY() - y) < 1.6;
    }

    @Override
    public boolean canUse() {
        if (--cooldown > 0 || mob.isLeashed() && mob.getLeashHolder() instanceof Player) {
            return false;
        }
        cooldown = 40 + mob.getRandom().nextInt(40);
        AnimalData d = PiAnimals.get(mob);
        if (d == null || !d.domestic) {
            return false;
        }
        boolean hungry = d.hunger < THRESHOLD, thirsty = d.thirst < THRESHOLD;
        if (!hungry && !thirsty) {
            return false;
        }
        //1.0.5-beta (lot 41) : un chemin ne se calcule qu'au sol ou dans l'eau (comme PathNavigation.canUpdatePath) : sinon, bientot
        if (!mob.onGround() && !mob.isInWater()) {
            cooldown = 10;
            return false;
        }
        target = null;
        path = null;
        Level level = mob.level();
        BlockPos at = mob.blockPosition();
        long now = level.getGameTime();
        choice.begin();
        boolean thirstFirst = thirsty && (!hungry || d.thirst <= d.hunger);
        SiteChoice.Choice c = thirstFirst ? drink(level, at, now) : null;
        if (c == null && hungry) {
            c = choice.choose(FarmSites.sorted(level, at, 16, false, p -> !choice.forgotten(p, now)
                  && level.getBlockEntity(p) instanceof StationTiles.Feeder f && f.hasFor(species), CANDIDATES), now, 0, this::pathTo, this::reaches);
            kind = 0;
        }
        if (c == null && thirsty && !thirstFirst) {
            c = drink(level, at, now);
        }
        if (c == null) {
            return false;
        }
        target = c.target();
        path = c.path();
        return true;
    }

    /** Abreuvoirs, puis eau du monde ; {@link #kind} = la sorte essayee en dernier. */
    @Nullable
    private SiteChoice.Choice drink(Level level, BlockPos at, long now) {
        kind = 1;
        SiteChoice.Choice c = choice.choose(FarmSites.sorted(level, at, 16, true, p -> !choice.forgotten(p, now)
              && level.getBlockEntity(p) instanceof StationTiles.Trough t && t.hasWater(250), CANDIDATES), now, 0, this::pathTo, this::reaches);
        if (c == null && choice.budget() > 0) {
            kind = 2;
            c = choice.choose(water(level, at, now), now, SiteChoice.WATER_RADIUS, this::pathTo, this::reaches);
        }
        return c;
    }

    /** Le vrai calcul de chemin du jeu vers un lieu (precision 1). */
    @Nullable
    private Path pathTo(BlockPos to) {
        return mob.getNavigation().createPath(to, 1);
    }

    private boolean reaches(BlockPos to, double x, double y, double z) {
        return inReach(to, x, y, z, mob.getBbWidth());
    }

    /** Eau du monde (8 blocs) : les sources a l'air libre non ecartees, de la plus proche a la plus loin (au plus {@link #CANDIDATES}). */
    private List<BlockPos> water(Level level, BlockPos at, long now) {
        //1.0.5-beta (lot 29) : palettes des sections d'abord (exact) : aucune source d'eau possible, rien a lire
        if (!fr.pioneers.core.block.ScanFilter.mayContain(level, at.offset(-8, -2, -8), at.offset(8, 1, 8),
              s -> s.getFluidState().is(FluidTags.WATER) && s.getFluidState().isSource())) {
            return List.of();
        }
        List<BlockPos> found = new ArrayList<>();
        for (BlockPos p : BlockPos.betweenClosed(at.offset(-8, -2, -8), at.offset(8, 1, 8))) {
            if (level.isLoaded(p) && level.getFluidState(p).is(FluidTags.WATER) && level.getFluidState(p).isSource() && level.getBlockState(p.above()).isAir()
                && !choice.forgotten(p, now)) {
                found.add(p.immutable());
            }
        }
        found.sort(Comparator.comparingDouble(p -> p.distSqr(at)));
        return found.size() > CANDIDATES ? found.subList(0, CANDIDATES) : found;
    }

    @Override
    public void start() {
        done = false;
        timeout = 240;
        idle = 0;
        repaths = 0;
        if (path != null) {
            mob.getNavigation().moveTo(path, 1.0);                         //le chemin du choix : aucun nouveau calcul
        }
    }

    @Override
    public boolean canContinueToUse() {
        if (done || target == null) {
            return false;
        }
        if (--timeout <= 0) {
            giveUp();                                                      //trop long sans arriver : ecartee, une autre au prochain choix
            return false;
        }
        return true;
    }

    @Override
    public void stop() {
        target = null;
        path = null;
        mob.getNavigation().stop();
    }

    /** Cible ecartee (injoignable en route ou trop longue a atteindre) : la bete en essaiera une autre. */
    private void giveUp() {
        if (target != null) {
            choice.forget(target, mob.level().getGameTime(), kind == 2 ? SiteChoice.WATER_RADIUS : 0);
        }
        done = true;
    }

    @Override
    public void tick() {
        if (target == null || done) {
            return;
        }
        Vec3 c = Vec3.atBottomCenterOf(target);
        mob.getLookControl().setLookAt(c.x, c.y + 0.4, c.z);
        if (inReach(target, mob.getX(), mob.getY(), mob.getZ(), mob.getBbWidth())) {
            consume();
            done = true;
            return;
        }
        //arretee avant d'y etre : au plus MAX_REPATHS nouveaux calculs, espaces (20 appels), puis la cible est ecartee
        if (mob.getNavigation().isDone() && ++idle >= 20) {
            idle = 0;
            choice.begin();
            Path p = ++repaths > SiteChoice.MAX_REPATHS ? null : choice.attempt(target, this::pathTo, this::reaches);
            if (p == null) {
                giveUp();
                return;
            }
            mob.getNavigation().moveTo(p, 1.0);
        }
    }

    private void consume() {
        AnimalData d = PiAnimals.get(mob);
        if (d == null || !(mob.level() instanceof ServerLevel level)) {
            return;
        }
        Vec3 head = mob.position().add(mob.getLookAngle().scale(mob.getBbWidth() * 0.6)).add(0, mob.getBbHeight() * 0.6, 0);
        if (kind == 0 && level.getBlockEntity(target) instanceof StationTiles.Feeder f) {
            ItemStack[] eaten = {ItemStack.EMPTY};
            int v = f.eat(species, eaten);
            if (v > 0) {
                d.hunger = Math.min(100, d.hunger + v);
                level.sendParticles(new ItemParticleOption(ParticleTypes.ITEM, eaten[0]), head.x, head.y, head.z, 8, 0.15, 0.1, 0.15, 0.05);
                level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_EAT, mob.getSoundSource(), 0.8F, 0.8F + mob.getRandom().nextFloat() * 0.3F);
                if (mob instanceof Sheep sheep && sheep.isSheared() && mob.getRandom().nextInt(3) == 0) {
                    sheep.ate();
                }
            }
        } else if (kind == 1 && level.getBlockEntity(target) instanceof StationTiles.Trough t) {
            if (t.drink(250)) {
                d.thirst = Math.min(100, d.thirst + 50);
                drinkFx(level, head);
            }
        } else if (kind == 2) {
            d.thirst = 100;
            drinkFx(level, head);
        }
        PiAnimals.sync(mob, d, true);
    }

    private void drinkFx(ServerLevel level, Vec3 head) {
        level.sendParticles(ParticleTypes.SPLASH, head.x, head.y - 0.3, head.z, 10, 0.2, 0.05, 0.2, 0.02);
        level.playSound(null, mob.blockPosition(), SoundEvents.GENERIC_DRINK, mob.getSoundSource(), 0.7F, 0.9F);
    }
}
