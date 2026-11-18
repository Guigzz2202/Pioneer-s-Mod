package fr.pioneers.tech.bronze.autofoundry;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4, Guillaume : « le metal coule vers le PREMIER MOULE LIBRE (le plus proche par les conduits), puis le suivant, comme une
 * chaine ») : RESEAU DES CONDUITS d'une fonderie. Parcours en largeur depuis les conduits colles a une cuve (sources), le long des conduits
 * raccordes ; chaque conduit au-dessus d'un moule automatique propose ce moule. Ordre = distance par les conduits (puis l'ordre des cuves et
 * des cotes nord, est, sud, ouest : toujours le meme) ; le premier moule libre pour le metal recoit la coulee, le chemin des conduits jusqu'a
 * lui est celui ou le metal coule ({@link Target#path}, du premier conduit au bec).
 */
public final class ChannelNetwork {

    /** Au plus ce nombre de conduits parcourus par fonderie. */
    public static final int MAX_CHANNELS = 256;
    private static final Direction[] SIDES = {Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST};

    /** Moule choisi, chemin des conduits (du premier, colle a la cuve, a celui du bec), quantite qu'il demande. */
    public record Target(BlockPos mold, List<BlockPos> path, int wants) {
    }

    /** Moules du reseau dans l'ordre de la chaine, et combien sont libres pour ce metal (vide : aucun metal). */
    public record Census(int molds, int free) {
    }

    private ChannelNetwork() {
    }

    /** Parcours : conduits atteints dans l'ordre, avec leur predecesseur (null : source). */
    private static Map<BlockPos, BlockPos> walk(Level level, Collection<BlockPos> vats, List<BlockPos> order) {
        Map<BlockPos, BlockPos> parent = new HashMap<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        for (BlockPos vat : vats) {
            for (Direction d : SIDES) {
                BlockPos c = vat.relative(d);
                if (!parent.containsKey(c) && level.isLoaded(c) && level.getBlockState(c).getBlock() instanceof CastingChannelBlock) {
                    parent.put(c, null);
                    queue.add(c);
                }
            }
        }
        while (!queue.isEmpty() && order.size() < MAX_CHANNELS) {
            BlockPos c = queue.poll();
            order.add(c);
            for (Direction d : SIDES) {
                BlockPos n = c.relative(d);
                if (!parent.containsKey(n) && level.isLoaded(n) && level.getBlockState(n).getBlock() instanceof CastingChannelBlock) {
                    parent.put(n, c);
                    queue.add(n);
                }
            }
        }
        return parent;
    }

    /**
     * Premier moule libre pour {@code metal} par les conduits partant des cuves {@code vats} de la fonderie en {@code foundry}, ou null. Le
     * metal passe de la fonderie au moule : un moule sur le terrain d'un autre clan qui le refuse ({@code ClanApi.canTransfer}) est saute.
     */
    @Nullable
    public static Target first(Level level, BlockPos foundry, Collection<BlockPos> vats, String metal) {
        List<BlockPos> order = new ArrayList<>();
        Map<BlockPos, BlockPos> parent = walk(level, vats, order);
        for (BlockPos c : order) {
            if (level.getBlockEntity(c.below()) instanceof AutoMoldTile mold && fr.pioneers.society.clan.ClanApi.canTransfer(level, foundry, c.below())) {
                int wants = mold.mold().wants(metal);
                if (wants > 0) {
                    List<BlockPos> path = new ArrayList<>();
                    for (BlockPos p = c; p != null; p = parent.get(p)) {
                        path.add(0, p);
                    }
                    return new Target(c.below(), List.copyOf(path), wants);
                }
            }
        }
        return null;
    }

    /** Chemin des conduits jusqu'au moule en {@code mold} (le conduit de son bec en dernier), ou null s'il n'est plus atteint. */
    @Nullable
    public static List<BlockPos> pathTo(Level level, Collection<BlockPos> vats, BlockPos mold) {
        List<BlockPos> order = new ArrayList<>();
        Map<BlockPos, BlockPos> parent = walk(level, vats, order);
        BlockPos spout = mold.above();
        if (!parent.containsKey(spout) || !order.contains(spout)) {
            return null;
        }
        List<BlockPos> path = new ArrayList<>();
        for (BlockPos p = spout; p != null; p = parent.get(p)) {
            path.add(0, p);
        }
        return List.copyOf(path);
    }

    /** Moules atteints et moules libres pour {@code metal} (interface). */
    public static Census census(Level level, Collection<BlockPos> vats, String metal) {
        List<BlockPos> order = new ArrayList<>();
        walk(level, vats, order);
        int molds = 0, free = 0;
        for (BlockPos c : order) {
            if (level.getBlockEntity(c.below()) instanceof AutoMoldTile mold) {
                molds++;
                if (!metal.isEmpty() && mold.mold().accepts(metal)) {
                    free++;
                }
            }
        }
        return new Census(molds, free);
    }
}
