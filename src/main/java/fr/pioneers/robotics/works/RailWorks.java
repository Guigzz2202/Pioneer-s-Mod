package fr.pioneers.robotics.works;

import fr.pioneers.rail.track.TrackBlock;
import fr.pioneers.rail.track.TrackPlacement;
import java.lang.reflect.Proxy;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 0.20.6 : chantier d'une voie ferree tracee par {@link RailPlanner} : les formes sont calculees par la vraie pose automatique
 * ({@link TrackPlacement}) sur un monde virtuel (les voies existantes du monde comprises, pour se raccorder), puis le chantier ajoute ce qui
 * porte la voie (remblai bas, tablier et piliers de viaduc) et degage 3 blocs au-dessus (tranchee ou tunnel).
 */
public final class RailWorks {

    /** Hauteur degagee au-dessus de la voie. */
    public static final int HEADROOM = 3;
    /** Un pilier toutes les PILLAR_EVERY cases de viaduc. */
    public static final int PILLAR_EVERY = 4;

    private RailWorks() {
    }

    /** Pose virtuelle des cases dans l'ordre : etats finals des voies (les voies existantes modifiees comprises). */
    public static Map<BlockPos, BlockState> lay(BlockGetter world, List<BlockPos> cells, TrackBlock block) {
        Map<BlockPos, BlockState> placed = new HashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        LevelAccessor level = (LevelAccessor) Proxy.newProxyInstance(RailWorks.class.getClassLoader(), new Class<?>[]{LevelAccessor.class}, (proxy, m, a) -> {
            switch (m.getName()) {
                case "getBlockState" -> {
                    BlockPos p = (BlockPos) a[0];
                    BlockState s = placed.get(p);
                    if (s != null) {
                        return s;
                    }
                    BlockState w = world.getBlockState(p);
                    return w.getBlock() instanceof TrackBlock ? w : air;
                }
                case "setBlock" -> {
                    placed.put(((BlockPos) a[0]).immutable(), (BlockState) a[1]);
                    return true;
                }
                case "hashCode" -> {
                    return System.identityHashCode(proxy);
                }
                case "equals" -> {
                    return proxy == a[0];
                }
                case "toString" -> {
                    return "RailWorks virtual level";
                }
                default -> {
                    Class<?> r = m.getReturnType();
                    return r == boolean.class ? false : r == int.class ? 0 : r == long.class ? 0L : r == float.class ? 0F : r == double.class ? 0D : null;
                }
            }
        });
        for (int i = 0; i < cells.size(); i++) {
            BlockPos c = cells.get(i);
            //Tournee vers la case d'avant (la pose automatique courbe celle-ci si besoin) ; la premiere vers la suivante
            BlockPos o = i > 0 ? cells.get(i - 1) : cells.size() > 1 ? cells.get(1) : c.north();
            int dx = o.getX() - c.getX(), dz = o.getZ() - c.getZ();
            Direction look = Math.abs(dx) >= Math.abs(dz) ? (dx >= 0 ? Direction.EAST : Direction.WEST) : (dz >= 0 ? Direction.SOUTH : Direction.NORTH);
            placed.put(c, TrackPlacement.placementState(block, level, c, look, null));
            TrackPlacement.afterPlace(level, c, null);
        }
        //Seulement les voies (le monde virtuel ne connait qu'elles)
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (BlockPos c : cells) {
            out.put(c, placed.get(c));
        }
        placed.forEach((p, s) -> {
            if (!out.containsKey(p) && s.getBlock() instanceof TrackBlock && !s.equals(world.getBlockState(p))) {
                out.put(p, s);
            }
        });
        return out;
    }

    /**
     * Chantier complet : voies, degagement au-dessus, et dessous : remblai (2 blocs au plus au-dessus du sol), sinon tablier sous chaque case
     * et pilier jusqu'au sol toutes les {@link #PILLAR_EVERY} cases.
     */
    public static Map<BlockPos, BlockState> blocks(BlockGetter world, List<BlockPos> cells, Map<BlockPos, BlockState> tracks, BlockState fill, BlockState deck,
          BlockState pillar, int minY) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (BlockPos c : cells) {
            for (int k = 1; k <= HEADROOM; k++) {
                BlockPos up = c.above(k);
                if (!tracks.containsKey(up)) {
                    out.putIfAbsent(up, air);
                }
            }
        }
        int i = 0;
        for (BlockPos c : cells) {
            int depth = 0;
            BlockPos.MutableBlockPos down = c.below().mutable();
            while (depth < 48 && down.getY() > minY && open(world.getBlockState(down))) {
                depth++;
                down.move(0, -1, 0);
            }
            BlockPos under = c.below();
            if (depth == 0) {
                i++;
                continue;
            }
            if (depth <= 2) {
                for (int k = 1; k <= depth; k++) {
                    out.put(c.below(k), fill);
                }
            } else {
                out.put(under, deck);
                if (i % PILLAR_EVERY == 0) {
                    for (int k = 2; k <= depth; k++) {
                        out.put(c.below(k), pillar);
                    }
                }
            }
            i++;
        }
        out.putAll(tracks);
        return out;
    }

    private static boolean open(BlockState s) {
        return s.isAir() || s.canBeReplaced() || s.is(BlockTags.LEAVES) || !s.getFluidState().isEmpty();
    }
}
