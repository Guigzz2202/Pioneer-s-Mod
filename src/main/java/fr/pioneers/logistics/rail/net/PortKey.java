package fr.pioneers.logistics.rail.net;

import fr.pioneers.logistics.rail.track.TrackPath;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;

/**
 * 0.20.0 : point de raccord entre deux blocs de voie, sous une forme unique : face nord ou ouest d'un bloc (la face sud d'un bloc est la face
 * nord de son voisin), demi-hauteur 0 ou 1. Deux bouts de chemin au meme point se raccordent s'ils se font face.
 */
public record PortKey(long block, int bits) {

    public static PortKey of(BlockPos anchor, TrackPath.End end) {
        return of(anchor.getX() + end.dx(), anchor.getY() + end.dy(), anchor.getZ() + end.dz(), end.face(), end.half(), end.lane());
    }

    public static PortKey of(int x, int y, int z, Direction face, int half) {
        return of(x, y, z, face, half, 0);
    }

    /** 0.20.6 : {@code lane} : decalage le long de la face (0 milieu, +1 / -1 : point propre aux moities d'un raccord diagonal). */
    public static PortKey of(int x, int y, int z, Direction face, int half, int lane) {
        int axis;
        switch (face) {
            case SOUTH -> {
                z++;
                axis = 0;
            }
            case EAST -> {
                x++;
                axis = 1;
            }
            case WEST -> axis = 1;
            default -> axis = 0;
        }
        int code = lane > 0 ? 1 : lane < 0 ? 2 : 0;
        return new PortKey(BlockPos.asLong(x, y, z), axis | half << 1 | code << 2);
    }

    /** Meme point au milieu de la face (sans decalage) : sert a savoir si une voisine vise une face. */
    public PortKey face() {
        return new PortKey(block, bits & 3);
    }

    private float laneOffset() {
        int code = (bits >> 2) & 3;
        return code == 1 ? TrackPath.LANE / 16F : code == 2 ? -TrackPath.LANE / 16F : 0;
    }

    /** Position du point (px du monde, en blocs). */
    public double x() {
        return BlockPos.getX(block) + ((bits & 1) == 0 ? 0.5 + laneOffset() : 0);
    }

    public double y() {
        return BlockPos.getY(block) + ((bits >> 1) & 1) * 0.5;
    }

    public double z() {
        return BlockPos.getZ(block) + ((bits & 1) == 0 ? 0 : 0.5 + laneOffset());
    }
}
