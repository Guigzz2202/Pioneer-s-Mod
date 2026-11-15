package fr.pioneers.logistics.rail.block;

import fr.pioneers.logistics.rail.track.TrackBlock;
import fr.pioneers.logistics.rail.track.TrackShape;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : blocs poses a cote de la voie (signaux, panneau de gare) : ils commandent les trains qui roulent avec eux a leur droite. Le sens
 * se deduit du cote ou est la voie : la voie a l'ouest d'un signal = trains vers le nord.
 */
public final class TrackSide {

    private TrackSide() {
    }

    /** Pose visee en cliquant sur la voie : un {@link TrackSideItem} la pose depuis le serveur et l'apercu client la montre. */
    private static final ThreadLocal<Spot> PLACING = new ThreadLocal<>();

    /**
     * 0.20.1 : emplacement d'un bloc pose en cliquant sur une voie droite. {@code heading} = sens des trains commandes (ou longes),
     * {@code toTrack} = direction du bloc vers la voie ; {@code free} : la case est libre.
     */
    public record Spot(BlockPos pos, BlockPos track, Direction heading, Direction toTrack, boolean free) {}

    /**
     * Ou poser un bloc de bord de voie en visant {@code track}. Bloc a sens (signal, gare) : sens = celui de la voie le plus proche du
     * regard, le bloc a droite des trains. Bloc sans sens (quai, grue) : du cote vise du rail, sinon du cote du joueur. Maj inverse le sens
     * (bloc a sens) ou le cote. Null si la voie n'est pas une droite a plat.
     */
    @Nullable
    public static Spot spot(BlockGetter level, BlockPos track, Player player, Vec3 hit, boolean directional) {
        BlockState st = level.getBlockState(track);
        if (!(st.getBlock() instanceof TrackBlock tb)) {
            return null;
        }
        TrackShape shape = tb.shape(st);
        if (shape != TrackShape.STRAIGHT_NS && shape != TrackShape.STRAIGHT_EW) {
            return null;
        }
        boolean z = shape == TrackShape.STRAIGHT_NS;
        Vec3 view = player.getViewVector(1);
        boolean flip = player.isShiftKeyDown();
        Direction heading = z ? (view.z < 0 ? Direction.NORTH : Direction.SOUTH) : (view.x < 0 ? Direction.WEST : Direction.EAST);
        Direction side;
        if (directional) {
            if (flip) {
                heading = heading.getOpposite();
            }
            //A droite des trains : la voie est a gauche du bloc (track = pos + heading.ccw)
            side = heading.getClockWise();
        } else {
            double off = z ? hit.x - (track.getX() + 0.5) : hit.z - (track.getZ() + 0.5);
            if (Math.abs(off) < 0.15) {
                off = z ? player.getX() - (track.getX() + 0.5) : player.getZ() - (track.getZ() + 0.5);
            }
            side = z ? (off < 0 ? Direction.WEST : Direction.EAST) : (off < 0 ? Direction.NORTH : Direction.SOUTH);
            if (flip) {
                side = side.getOpposite();
            }
        }
        BlockPos pos = track.relative(side);
        BlockState there = level.getBlockState(pos);
        boolean free = there.canBeReplaced() && !(there.getBlock() instanceof TrackBlock);
        return new Spot(pos, track, heading, side.getOpposite(), free);
    }

    /** Pose en cours (fil du serveur ou du client) : lue par les blocs a la pose. */
    @Nullable
    public static Spot placing() {
        return PLACING.get();
    }

    static void placing(@Nullable Spot spot) {
        if (spot == null) {
            PLACING.remove();
        } else {
            PLACING.set(spot);
        }
    }

    /** Sens commande (direction de marche des trains), d'apres la voie voisine ; le regard departage deux voies. */
    @Nullable
    public static Direction heading(BlockGetter level, BlockPos pos, Direction look) {
        for (Direction toTrack : new Direction[]{look.getCounterClockWise(), look.getClockWise(), look, look.getOpposite()}) {
            if (level.getBlockState(pos.relative(toTrack)).getBlock() instanceof TrackBlock) {
                //Voie en pos + toTrack : le bloc est du cote oppose, a droite des trains qui roulent vers (cote).getCounterClockWise()
                return toTrack.getOpposite().getCounterClockWise();
            }
        }
        return null;
    }

    /** Bloc de voie commande par un bloc pose a cote, roulant vers {@code heading}. */
    public static BlockPos track(BlockPos pos, Direction heading) {
        return pos.relative(heading.getCounterClockWise());
    }
}
