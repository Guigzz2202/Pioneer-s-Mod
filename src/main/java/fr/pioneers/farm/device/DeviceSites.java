package fr.pioneers.farm.device;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;

/**
 * Blocs de la ferme animee charges, par dimension (tenus par leurs tuiles a la charge et au retrait) : epouvantails (corbeaux, pietinement),
 * pondoirs (ponte des poules) ; 1.0.5-beta (lot 7) : ruches (ours), pondoirs a oiseaux. Evite de balayer le monde.
 */
public final class DeviceSites {

    private DeviceSites() {
    }

    public enum Kind {
        SCARECROW, NEST,
        /** 1.0.5-beta (lot 7) : ruches du mod (l'ours les cherche), pondoirs a oiseaux. */
        APIARY, BIRD_HOUSE
    }

    private static final Map<Kind, Map<ResourceKey<Level>, Set<BlockPos>>> SITES = new EnumMap<>(Kind.class);

    static {
        for (Kind k : Kind.values()) {
            SITES.put(k, new HashMap<>());
        }
    }

    public static void add(Level level, BlockPos pos, Kind kind) {
        if (!level.isClientSide) {
            SITES.get(kind).computeIfAbsent(level.dimension(), k -> new HashSet<>()).add(pos.immutable());
        }
    }

    public static void remove(Level level, BlockPos pos, Kind kind) {
        if (!level.isClientSide) {
            Set<BlockPos> s = SITES.get(kind).get(level.dimension());
            if (s != null) {
                s.remove(pos);
            }
        }
    }

    /** Positions a moins de {@code radius} blocs (distance horizontale, 8 blocs en hauteur), chargees. */
    public static List<BlockPos> near(Level level, double x, double y, double z, double radius, Kind kind) {
        Set<BlockPos> s = SITES.get(kind).get(level.dimension());
        List<BlockPos> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        double r2 = radius * radius;
        for (BlockPos p : s) {
            double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= r2 && Math.abs(p.getY() - y) <= 8 && level.isLoaded(p)) {
                out.add(p);
            }
        }
        return out;
    }

    /** Un epouvantail a moins de {@code radius} blocs ? */
    public static boolean scared(Level level, double x, double y, double z, double radius) {
        Set<BlockPos> s = SITES.get(Kind.SCARECROW).get(level.dimension());
        if (s == null || s.isEmpty()) {
            return false;
        }
        double r2 = radius * radius;
        for (BlockPos p : s) {
            double dx = p.getX() + 0.5 - x, dz = p.getZ() + 0.5 - z;
            if (dx * dx + dz * dz <= r2 && Math.abs(p.getY() - y) <= 10) {
                return true;
            }
        }
        return false;
    }
}
