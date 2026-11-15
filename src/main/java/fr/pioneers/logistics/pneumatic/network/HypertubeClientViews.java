package fr.pioneers.logistics.pneumatic.network;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.39 : troncons d'hypertube connus du client (vues chargees) : visee d'un tube (cassure, prolongement depuis un bout) et apercu de la
 * pose. Sans classe client : chargeable des deux cotes.
 */
public final class HypertubeClientViews {

    private static final Map<Integer, HypertubeEntity> VIEWS = new ConcurrentHashMap<>();

    private HypertubeClientViews() {
    }

    static void add(HypertubeEntity e) {
        VIEWS.put(e.segmentId(), e);
    }

    static void remove(HypertubeEntity e) {
        VIEWS.remove(e.segmentId(), e);
    }

    public static Collection<HypertubeEntity> all() {
        return Collections.unmodifiableCollection(VIEWS.values());
    }

    /**
     * 0.20.46 (audit) : oublie les vues du monde client {@code level} (toutes si null) : au changement de dimension ou a la deconnexion,
     * l'ancien monde ne retire pas ses entites une a une et restait tenu par cette table.
     */
    public static void clear(@Nullable Level level) {
        if (level == null) {
            VIEWS.clear();
        } else {
            VIEWS.values().removeIf(e -> e.level() == level);
        }
    }

    /** Troncons vus dans {@code level}, pour {@link HypertubeItem#ends}. 0.20.46 (audit) : seulement ceux de ce monde. */
    public static List<HypertubeItem.Seen> seen(Level level) {
        List<HypertubeItem.Seen> out = new ArrayList<>();
        for (HypertubeEntity e : VIEWS.values()) {
            if (e.curve() != null && !e.isRemoved() && e.level() == level) {
                out.add(new HypertubeItem.Seen(e.segmentId(), e.curve(), e.stationA(), e.stationB()));
            }
        }
        return out;
    }

    /** Troncon de {@code level} vise par un rayon (a moins de {@code tolerance} blocs de son axe), le plus proche ; null sinon. */
    @Nullable
    public static HypertubeEntity aimed(Level level, Vec3 origin, Vec3 dir, double reach, double tolerance) {
        HypertubeEntity best = null;
        double bestT = Double.MAX_VALUE;
        double[] hit = new double[1];
        for (HypertubeEntity e : VIEWS.values()) {
            HypertubeCurve c = e.curve();
            if (c == null || e.isRemoved() || e.level() != level) {
                continue;
            }
            if (c.rayDistance(origin, dir, reach, hit) <= tolerance && hit[0] < bestT) {
                bestT = hit[0];
                best = e;
            }
        }
        return best;
    }
}
