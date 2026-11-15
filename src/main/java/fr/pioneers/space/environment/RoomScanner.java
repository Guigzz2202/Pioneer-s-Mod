package fr.pioneers.space.environment;

import fr.pioneers.core.Directions;
import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Detection des pieces closes : remplissage depuis un point, a travers tout ce qui laisse passer l'air.
 * <ul>
 *     <li>Un bloc plein (pierre, verre, murs de la base, sas ferme) arrete l'air ;</li>
 *     <li>une porte, une trappe, une dalle, une vitre fine laissent passer l'air : <b>une porte seule ne suffit pas</b>,
 *     il faut des sas (portes etanches) ;</li>
 *     <li>la piece fuit si le remplissage depasse le volume maximal ou atteint une zone non chargee.</li>
 * </ul>
 */
public final class RoomScanner {

    private RoomScanner() {
    }

    /** Resultat du balayage : cellules d'air, etancheite, feuillage en bordure. */
    public record Room(LongSet cells, boolean sealed, int leaves) {

        public static final Room EMPTY = new Room(new LongOpenHashSet(), false, 0);

        public boolean contains(BlockPos pos) {
            return cells.contains(pos.asLong());
        }

        public int volume() {
            return cells.size();
        }
    }

    /** L'air peut-il occuper ce bloc ? */
    public static boolean isAirPassable(Level level, BlockPos pos, BlockState state) {
        if (state.isAir()) {
            return true;
        }
        if (state.getBlock() instanceof AirlockDoorBlock) {
            return state.getValue(AirlockDoorBlock.OPEN);
        }
        return !state.isCollisionShapeFullBlock(level, pos);
    }

    /**
     * Balaye la piece qui touche {@code origin} (le bloc lui-meme est exclu : c'est la machine).
     *
     * @param maxVolume volume au-dela duquel la piece est consideree comme ouverte.
     */
    public static Room scan(Level level, BlockPos origin, int maxVolume) {
        LongOpenHashSet cells = new LongOpenHashSet();
        LongOpenHashSet leafSeen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (Direction direction : Directions.ALL) {
            BlockPos start = origin.relative(direction);
            if (!level.isLoaded(start)) {
                return new Room(cells, false, 0);
            }
            if (isAirPassable(level, start, level.getBlockState(start)) && cells.add(start.asLong())) {
                queue.enqueue(start.asLong());
            }
        }
        int leaves = 0;
        boolean sealed = true;
        while (!queue.isEmpty()) {
            long packed = queue.dequeueLong();
            for (Direction direction : Directions.ALL) {
                cursor.set(packed).move(direction);
                long next = cursor.asLong();
                if (cells.contains(next) || next == origin.asLong()) {
                    continue;
                }
                if (level.isOutsideBuildHeight(cursor) || !level.isLoaded(cursor)) {
                    sealed = false;
                    continue;
                }
                BlockState state = level.getBlockState(cursor);
                if (isAirPassable(level, cursor, state)) {
                    if (cells.size() >= maxVolume) {
                        sealed = false;
                        continue;
                    }
                    cells.add(next);
                    queue.enqueue(next);
                } else if (state.is(BlockTags.LEAVES) && leafSeen.add(next)) {
                    leaves++;
                }
            }
            if (!sealed && cells.size() >= maxVolume) {
                break;
            }
        }
        return new Room(cells, sealed, leaves);
    }
}
