package fr.pioneers.logistics.pneumatic.network;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.39 : bout d'un hypertube : position de l'axe du tube, direction de sortie imposee (face d'une station, suite d'un tube deja pose ;
 * null = libre), station raccordee (ou null), troncon prolonge (-1 sinon).
 * <p>
 * 0.20.43 : ancrage physique {@code block} (bloc {@link HypertubeAnchorBlock}, ou null) et son axe {@code axis} : le tube le traverse, il en
 * sort par l'un ou l'autre cote (celui qui regarde vers l'autre bout : {@link #resolvedOut}). Une ancre sans station, ni troncon, ni bloc est
 * une case libre : un ancrage y sera pose avec le tube ({@link #fresh}).
 */
public record HypertubeAnchor(Vec3 pos, @Nullable Vec3 out, @Nullable BlockPos station, int segment, @Nullable BlockPos block, @Nullable Vec3 axis) {

    public HypertubeAnchor(Vec3 pos, @Nullable Vec3 out, @Nullable BlockPos station, int segment) {
        this(pos, out, station, segment, null, null);
    }

    /** Ancrage pose ({@code axis} : son axe, unitaire). */
    public static HypertubeAnchor ofBlock(BlockPos block, Vec3 axis) {
        return new HypertubeAnchor(Vec3.atCenterOf(block), null, null, -1, block.immutable(), axis);
    }

    /** Case libre : un ancrage y sera pose. */
    public boolean fresh() {
        return station == null && segment < 0 && block == null;
    }

    /** Direction de sortie vers {@code other} : imposee, sinon le cote de l'ancrage qui regarde vers l'autre bout, sinon null (libre). */
    @Nullable
    public Vec3 resolvedOut(Vec3 other) {
        if (out != null) {
            return out;
        }
        if (axis != null) {
            return axis.dot(other.subtract(pos)) >= 0 ? axis : axis.scale(-1);
        }
        return null;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("x", pos.x);
        tag.putDouble("y", pos.y);
        tag.putDouble("z", pos.z);
        if (out != null) {
            tag.putDouble("ox", out.x);
            tag.putDouble("oy", out.y);
            tag.putDouble("oz", out.z);
        }
        if (station != null) {
            tag.putLong("station", station.asLong());
        }
        tag.putInt("segment", segment);
        if (block != null && axis != null) {
            tag.putLong("block", block.asLong());
            tag.putDouble("ax", axis.x);
            tag.putDouble("ay", axis.y);
            tag.putDouble("az", axis.z);
        }
        return tag;
    }

    public static HypertubeAnchor load(CompoundTag tag) {
        Vec3 out = tag.contains("ox") ? new Vec3(tag.getDouble("ox"), tag.getDouble("oy"), tag.getDouble("oz")) : null;
        BlockPos block = tag.contains("block") ? BlockPos.of(tag.getLong("block")) : null;
        Vec3 axis = block != null ? new Vec3(tag.getDouble("ax"), tag.getDouble("ay"), tag.getDouble("az")) : null;
        return new HypertubeAnchor(new Vec3(tag.getDouble("x"), tag.getDouble("y"), tag.getDouble("z")), out,
              tag.contains("station") ? BlockPos.of(tag.getLong("station")) : null, tag.contains("segment") ? tag.getInt("segment") : -1, block, axis);
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeDouble(pos.x);
        buf.writeDouble(pos.y);
        buf.writeDouble(pos.z);
        buf.writeBoolean(out != null);
        if (out != null) {
            buf.writeDouble(out.x);
            buf.writeDouble(out.y);
            buf.writeDouble(out.z);
        }
        buf.writeBoolean(station != null);
        if (station != null) {
            buf.writeBlockPos(station);
        }
        buf.writeVarInt(segment + 1);
        buf.writeBoolean(block != null);
        if (block != null) {
            buf.writeBlockPos(block);
        }
    }

    /** Lu du client : l'axe d'un ancrage n'est pas transmis (le serveur le relit dans le monde). */
    public static HypertubeAnchor read(FriendlyByteBuf buf) {
        Vec3 pos = new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble());
        Vec3 out = buf.readBoolean() ? new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()) : null;
        if (out != null) {
            out = out.lengthSqr() < 1e-6 || !Double.isFinite(out.lengthSqr()) ? null : out.normalize();
        }
        BlockPos station = buf.readBoolean() ? buf.readBlockPos() : null;
        int segment = buf.readVarInt() - 1;
        BlockPos block = buf.readBoolean() ? buf.readBlockPos() : null;
        return new HypertubeAnchor(pos, out, station, segment, block, null);
    }
}
