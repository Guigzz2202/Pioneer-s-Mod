package fr.pioneers.logistics.rail.net;

import fr.pioneers.logistics.rail.track.TrackPath;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.logistics.rail.track.TrackShape;
import java.util.List;
import net.minecraft.core.BlockPos;

/**
 * 0.20.0 : piece de voie connue du reseau (copie logique du bloc, pour que les trains roulent aussi dans les chunks non charges).
 * {@code route} : chemin choisi d'un aiguillage (lames), pose par les trains qui le reservent.
 */
public final class Piece {

    /** Comportement de la piece. */
    public enum Kind {
        TRACK, BUFFER, BRAKE, BOOSTER, DETECTOR,
        /** 0.20.5 : pont-bascule, rail de detelage. */
        WEIGHBRIDGE, UNCOUPLER,
        /** 0.20.6 : passage a niveau (planches entre les rails). */
        LEVEL_CROSSING;

        public static Kind byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public final BlockPos anchor;
    public final TrackShape shape;
    public final Kind kind;
    public final TrackQuality quality;
    public int route;
    /** Section (canton) calculee par le reseau. */
    int section = -1;

    public Piece(BlockPos anchor, TrackShape shape, Kind kind, TrackQuality quality, int route) {
        this.anchor = anchor.immutable();
        this.shape = shape;
        this.kind = kind;
        this.quality = quality;
        this.route = route;
    }

    public long key() {
        return anchor.asLong();
    }

    public List<TrackPath> paths() {
        return shape.paths();
    }

    public TrackPath path(int i) {
        List<TrackPath> paths = shape.paths();
        return paths.get(Math.max(0, Math.min(paths.size() - 1, i)));
    }

    public int section() {
        return section;
    }

    /** Vitesse maximale d'un chemin de la piece (px/tick). */
    public float limit(int path) {
        float l = quality.maxSpeed();
        float p = path(path).limit;
        return p > 0 ? Math.min(l, p) : l;
    }

    public boolean same(Piece other) {
        return other != null && other.shape == shape && other.kind == kind && other.quality == quality;
    }
}
