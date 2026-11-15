package fr.pioneers.nature;

import fr.pioneers.Pioneers;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.3-beta (lot 11, Guillaume : « il faut que les feuilles disparaissent beaucoup plus vite quand l'arbre est abattu ») : DEPERISSEMENT
 * RAPIDE des feuilles orphelines. Apres un abattage ({@link TreeFeller}) ou un tronc casse a la main, une vague part des troncs retires :
 * {@link #DELAY} ticks d'attente (le jeu recalcule la distance des feuilles au tronc, une case par tick, jusqu'a 7), puis de proche en
 * proche (parcours en largeur dans le feuillage, {@link #RADIUS} blocs au plus d'un tronc retire), au plus {@link #RATE} feuilles par tick
 * et {@link #READS} cases lues par tick (aucun lag) : chaque feuille NATURELLE sans tronc a moins de 7 cases (distance 7, comme le
 * depérissement vanilla) tombe avec son butin normal ({@code dropResources} : pousses, batons, pommes, fruits) et ses particules.
 * Les feuilles posees par un joueur ({@code persistent}) et celles encore portees par un autre arbre ne bougent jamais. Rien n'est sauve :
 * si le serveur s'arrete, le depérissement vanilla finit le travail.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class LeafDecay {

    /** Attente avant la vague (ticks), feuilles retirees par tick, cases lues par tick, rayon autour d'un tronc retire (blocs). */
    public static final int DELAY = 10, RATE = 8, READS = 96, RADIUS = 8;
    private static final Map<ServerLevel, List<Job>> JOBS = new WeakHashMap<>();

    private LeafDecay() {
    }

    /** Une vague : troncs retires, file du parcours (dans l'ordre de proximite), cases deja vues. */
    static final class Job {

        final List<BlockPos> origins;
        final ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        final Set<BlockPos> seen = new HashSet<>();
        /** Feuilles naturelles encore portees au premier passage : revues une fois {@link #DELAY} ticks plus tard (distance pas encore a jour). */
        final ArrayDeque<BlockPos> retry = new ArrayDeque<>();
        int wait = DELAY;
        boolean started, retried;

        Job(Collection<BlockPos> origins) {
            this.origins = new ArrayList<>(origins);
        }

        boolean near(BlockPos p) {
            for (BlockPos o : origins) {
                if (Math.abs(p.getX() - o.getX()) <= RADIUS && Math.abs(p.getY() - o.getY()) <= RADIUS && Math.abs(p.getZ() - o.getZ()) <= RADIUS) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Feuille naturelle (pas posee par un joueur) ? */
    public static boolean natural(BlockState s) {
        return s.is(BlockTags.LEAVES) && !(s.hasProperty(LeavesBlock.PERSISTENT) && s.getValue(LeavesBlock.PERSISTENT));
    }

    /** Feuille naturelle sans tronc a moins de 7 cases (meme regle que le depérissement vanilla, {@code LeavesBlock.decaying}) ? */
    public static boolean orphan(BlockState s) {
        return natural(s) && s.hasProperty(LeavesBlock.DISTANCE) && s.getValue(LeavesBlock.DISTANCE) >= LeavesBlock.DECAY_DISTANCE;
    }

    /** Programme une vague autour des troncs retires {@code origins}. */
    public static void schedule(ServerLevel level, Collection<BlockPos> origins) {
        if (!origins.isEmpty()) {
            JOBS.computeIfAbsent(level, l -> new ArrayList<>()).add(new Job(origins));
        }
    }

    /** Vagues en cours dans ce monde (GameTests). */
    public static int pending(ServerLevel level) {
        List<Job> jobs = JOBS.get(level);
        return jobs == null ? 0 : jobs.size();
    }

    /** Tronc casse a la main (hors abattage : {@link TreeFeller} programme sa propre vague, une seule pour tout l'arbre). */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (!event.isCanceled() && !TreeFeller.felling() && event.getLevel() instanceof ServerLevel level && event.getState().is(BlockTags.LOGS)) {
            schedule(level, List.of(event.getPos().immutable()));
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        List<Job> jobs = JOBS.get(level);
        if (jobs == null || jobs.isEmpty()) {
            return;
        }
        int removed = 0, reads = 0;
        for (Iterator<Job> it = jobs.iterator(); it.hasNext() && removed < RATE && reads < READS; ) {
            Job job = it.next();
            if (job.wait-- > 0) {
                continue;
            }
            if (!job.started) {
                job.started = true;
                for (BlockPos o : job.origins) {
                    job.seen.add(o);
                }
                for (BlockPos o : job.origins) {
                    enqueueAround(job, o);
                }
            }
            while (!job.queue.isEmpty() && removed < RATE && reads < READS) {
                BlockPos p = job.queue.poll();
                reads++;
                if (!level.isLoaded(p)) {
                    continue;
                }
                BlockState s = level.getBlockState(p);
                if (!natural(s)) {
                    continue;
                }
                if (orphan(s)) {
                    Block.dropResources(s, level, p);
                    level.levelEvent(2001, p, Block.getId(s));
                    level.removeBlock(p, false);
                    removed++;
                } else if (!job.retried) {
                    job.retry.add(p);
                }
                if (!job.retried) {
                    enqueueAround(job, p);
                }
            }
            if (job.queue.isEmpty()) {
                if (!job.retried && !job.retry.isEmpty()) {
                    job.retried = true;
                    job.queue.addAll(job.retry);
                    job.retry.clear();
                    job.wait = DELAY;
                } else {
                    it.remove();
                }
            }
        }
    }

    /** Voisins (6 faces et diagonales du plan horizontal) a parcourir ensuite, dans le rayon des troncs retires. */
    private static void enqueueAround(Job job, BlockPos p) {
        for (Direction d : Direction.values()) {
            add(job, p.relative(d));
        }
        for (int dx = -1; dx <= 1; dx += 2) {
            for (int dz = -1; dz <= 1; dz += 2) {
                add(job, p.offset(dx, 0, dz));
            }
        }
    }

    private static void add(Job job, BlockPos q) {
        if (job.near(q) && job.seen.add(q)) {
            job.queue.add(q.immutable());
        }
    }
}
