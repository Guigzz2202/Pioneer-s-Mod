package fr.pioneers.farm.animal;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * Mangeoires et abreuvoirs charges, par dimension (tenus par leurs tuiles a la charge et au retrait) : les animaux y cherchent a manger et a
 * boire sans balayer le monde.
 */
public final class FarmSites {

    private FarmSites() {
    }

    private static final Map<ResourceKey<Level>, Set<BlockPos>> FEEDERS = new HashMap<>();
    private static final Map<ResourceKey<Level>, Set<BlockPos>> TROUGHS = new HashMap<>();

    public static void add(Level level, BlockPos pos, boolean trough) {
        if (!level.isClientSide) {
            (trough ? TROUGHS : FEEDERS).computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
        }
    }

    public static void remove(Level level, BlockPos pos, boolean trough) {
        if (!level.isClientSide) {
            Set<BlockPos> s = (trough ? TROUGHS : FEEDERS).get(level.dimension());
            if (s != null) {
                s.remove(pos);
            }
        }
    }

    /** Tous ceux du rayon (charges), pour le distributeur automatique. */
    public static List<BlockPos> within(Level level, BlockPos from, int radius, boolean trough) {
        Set<BlockPos> s = (trough ? TROUGHS : FEEDERS).get(level.dimension());
        List<BlockPos> out = new ArrayList<>();
        if (s != null) {
            double r2 = (double) radius * radius;
            for (BlockPos p : s) {
                if (p.distSqr(from) <= r2 && level.isLoaded(p)) {
                    out.add(p);
                }
            }
        }
        return out;
    }

    /**
     * 1.0.5-beta (lot 41, retour 67) : ceux du rayon qui satisfont le test, du plus proche au plus loin (au plus {@code max}) : les candidats
     * d'un animal, essayes un a un par le calcul de chemin ({@link SiteChoice}).
     */
    public static List<BlockPos> sorted(Level level, BlockPos from, int radius, boolean trough, Predicate<BlockPos> ok, int max) {
        Set<BlockPos> s = (trough ? TROUGHS : FEEDERS).get(level.dimension());
        return s == null || s.isEmpty() ? List.of() : SiteChoice.nearestFirst(s, from, radius, p -> level.isLoaded(p) && ok.test(p), max);
    }

    /** Le plus proche dans le rayon qui satisfait le test. */
    @Nullable
    public static BlockPos nearest(Level level, BlockPos from, int radius, boolean trough, Predicate<BlockPos> ok) {
        Set<BlockPos> s = (trough ? TROUGHS : FEEDERS).get(level.dimension());
        if (s == null || s.isEmpty()) {
            return null;
        }
        BlockPos best = null;
        double bd = (double) radius * radius;
        for (BlockPos p : s) {
            double d = p.distSqr(from);
            if (d <= bd && level.isLoaded(p) && ok.test(p)) {
                bd = d;
                best = p;
            }
        }
        return best;
    }
}
