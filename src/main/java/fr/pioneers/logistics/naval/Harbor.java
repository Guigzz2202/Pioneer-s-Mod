package fr.pioneers.logistics.naval;

import fr.pioneers.logistics.rail.logistics.StationLogistics;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : un port vu par la marine (inscrit par sa capitainerie, garde meme quand son chunk n'est pas charge). Le poste d'amarrage est le
 * long du quai, du cote de l'eau ({@link #facing}) ; 0.20.20 : le navire entre dans le bassin en U, dans l'axe de la capitainerie ({@link #cx}, {@link #cz}) ; {@link #quay} = longueur des bras.
 */
public final class Harbor {

    public enum Berth {
        /** Poste libre et praticable. */
        OK,
        /** Pas d'eau navigable devant la facade. */
        NO_WATER,
        /** L'eau est trop etroite ou pas assez profonde pour le plus grand navire accepte. */
        TOO_SMALL,
        /** Pas encore verifie (chunk jamais charge). */
        UNKNOWN,
        /** 0.20.14 : quai trop court pour le plus petit navire (dalles de quai de part et d'autre de la capitainerie). */
        SHORT_QUAY,
        /** 0.20.20 : pas de bassin en U autour de la capitainerie (fond incomplet, bras absents ou pas a la meme distance). */
        NOT_U;

        public static Berth byId(int i) {
            return values()[Math.max(0, Math.min(values().length - 1, i))];
        }
    }

    public final long pos;
    public Direction facing;
    public int tier;
    public String name = "";
    /** Navires en route ou a quai au plus (-1 : sans limite). */
    public int limit = -1;
    public StationLogistics logistics = new StationLogistics();
    /** Hauteur de la surface de l'eau du poste (y du bloc de fluide du dessus). */
    public int waterY;
    public Berth berth = Berth.UNKNOWN;
    /** Plus grand tier que le poste peut recevoir (eau et tier du port). */
    public int maxTier = -1;
    /** 0.20.14 : milieu du quai (blocs, centre des cases) : le navire s'amarre centre sur le quai, pas sur la capitainerie. */
    public double cx = Double.NaN, cz = Double.NaN;
    /** Longueur du quai (blocs, capitainerie comprise). */
    public int quay = 1;
    /** Navire au poste (amarre ou en train de s'y ranger). */
    @Nullable
    public UUID occupant;

    public Harbor(long pos, Direction facing, int tier) {
        this.pos = pos;
        this.facing = facing;
        this.tier = tier;
    }

    public BlockPos blockPos() {
        return BlockPos.of(pos);
    }

    public boolean accepts(int shipTier) {
        return berth == Berth.OK && shipTier <= Math.min(tier, maxTier);
    }

    /** 0.20.20 : distance de la capitainerie (fond du U) au centre du navire amarre dans le bassin (blocs). */
    public static double offset(int shipTier) {
        return 1 + ShipKind.length(shipTier) / 2.0;
    }

    /** Milieu du quai (x, z), ou la capitainerie si le quai n'est pas encore releve. */
    public double[] quayCenter() {
        BlockPos p = blockPos();
        return Double.isNaN(cx) ? new double[]{p.getX() + 0.5, p.getZ() + 0.5} : new double[]{cx, cz};
    }

    /** Pose d'amarrage : dans le bassin en U, centre sur l'axe de la capitainerie, cap dans l'axe (sens le plus proche de {@code heading}). */
    public double[] berthPose(int shipTier, float heading) {
        double[] q = quayCenter();
        double cx = q[0] + facing.getStepX() * offset(shipTier), cz = q[1] + facing.getStepZ() * offset(shipTier);
        Direction along = facing;
        float h1 = yawOf(along), h2 = yawOf(along.getOpposite());
        float h = Math.abs(wrap(heading - h1)) <= Math.abs(wrap(heading - h2)) ? h1 : h2;
        return new double[]{cx, cz, h};
    }

    /** Mouillage : au large de l'entree du bassin, dans son axe (le navire y entre et en sort en ligne droite). */
    public double[] anchorage(int shipTier) {
        double[] q = quayCenter();
        double d = Math.max(quay, ShipKind.length(shipTier) + 1) + ShipKind.length(shipTier) / 2.0 + 3;
        return new double[]{q[0] + facing.getStepX() * d, q[1] + facing.getStepZ() * d};
    }

    /** Cap d'une direction (avant = (sin, cos)). */
    public static float yawOf(Direction d) {
        return (float) Math.toDegrees(Math.atan2(d.getStepX(), d.getStepZ()));
    }

    public static float wrap(float a) {
        a %= 360;
        if (a >= 180) {
            a -= 360;
        }
        if (a < -180) {
            a += 360;
        }
        return a;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("pos", pos);
        t.putByte("facing", (byte) facing.get3DDataValue());
        t.putByte("tier", (byte) tier);
        t.putString("name", name);
        t.putInt("limit", limit);
        t.put("logi", logistics.save());
        t.putInt("waterY", waterY);
        t.putByte("berth", (byte) berth.ordinal());
        t.putByte("maxTier", (byte) maxTier);
        if (occupant != null) {
            t.putUUID("occupant", occupant);
        }
        if (!Double.isNaN(cx)) {
            t.putDouble("cx", cx);
            t.putDouble("cz", cz);
        }
        t.putInt("quay", quay);
        return t;
    }

    public static Harbor load(CompoundTag t) {
        Direction f = Direction.from3DDataValue(t.getByte("facing"));
        Harbor h = new Harbor(t.getLong("pos"), f.getAxis().isHorizontal() ? f : Direction.NORTH, t.getByte("tier"));
        h.name = t.getString("name");
        h.limit = t.getInt("limit");
        h.logistics = StationLogistics.load(t.getCompound("logi"));
        h.waterY = t.getInt("waterY");
        h.berth = Berth.byId(t.getByte("berth"));
        h.maxTier = t.getByte("maxTier");
        h.occupant = t.hasUUID("occupant") ? t.getUUID("occupant") : null;
        if (t.contains("cx")) {
            h.cx = t.getDouble("cx");
            h.cz = t.getDouble("cz");
        }
        h.quay = t.contains("quay") ? t.getInt("quay") : 1;
        return h;
    }
}
