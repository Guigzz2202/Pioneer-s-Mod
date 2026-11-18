package fr.pioneers.logistics.rail;

import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.track.TrackBlock;
import fr.pioneers.logistics.rail.track.TrackShape;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.0 : proprietes d'etat communes aux blocs du chemin de fer, et lecture d'une piece depuis un etat de bloc (utile des deux cotes : la
 * pose automatique tourne aussi sur le client).
 */
public final class TrackBlocks {

    /** Toutes les formes de voie posables par les blocs de voie (sans les butoirs). */
    public static final EnumProperty<TrackShape> SHAPE = EnumProperty.create("shape", TrackShape.class, s -> s.family() != TrackShape.Family.BUFFER);
    /** Butoirs. */
    public static final EnumProperty<TrackShape> BUFFER_SHAPE = EnumProperty.create("shape", TrackShape.class, s -> s.family() == TrackShape.Family.BUFFER);
    /** Rails fonctionnels (droits). */
    public static final EnumProperty<TrackShape> STRAIGHT_SHAPE = EnumProperty.create("shape", TrackShape.class,
          s -> s == TrackShape.STRAIGHT_NS || s == TrackShape.STRAIGHT_EW);
    /** Chemin choisi d'un aiguillage (lames). */
    public static final IntegerProperty ROUTE = IntegerProperty.create("route", 0, 2);
    /** Rail detecteur occupe. */
    public static final BooleanProperty POWERED = BooleanProperty.create("powered");
    /** Aspect d'un signal : 0 vert, 1 orange, 2 rouge, 3 bleu. */
    public static final IntegerProperty ASPECT = IntegerProperty.create("aspect", 0, 3);

    private TrackBlocks() {
    }

    /** Piece decrite par un etat de bloc de voie (null pour un autre bloc). */
    @Nullable
    public static Piece pieceOf(BlockState state, BlockPos pos) {
        if (!(state.getBlock() instanceof TrackBlock block)) {
            return null;
        }
        TrackShape shape = block.shape(state);
        if (shape == null) {
            return null;
        }
        int route = state.hasProperty(ROUTE) ? state.getValue(ROUTE) : 0;
        return new Piece(pos, shape, block.kind, block.quality, route);
    }

    public static List<TrackShape> all(EnumProperty<TrackShape> property) {
        return new ArrayList<>(property.getPossibleValues());
    }
}
