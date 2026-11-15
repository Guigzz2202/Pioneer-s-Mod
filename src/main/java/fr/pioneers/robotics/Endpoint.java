package fr.pioneers.robotics;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Point d'acces logistique : un bloc (coffre, machine...), la face par laquelle on y accede (null : l'inventaire entier), et l'endroit
 * ou l'unite se tient pour le faire (par defaut devant la face).
 */
public record Endpoint(BlockPos pos, @Nullable Direction side, @Nullable BlockPos stand) {

    public Endpoint {
        pos = pos.immutable();
        stand = stand == null ? null : stand.immutable();
    }

    public Endpoint(BlockPos pos, Direction side) {
        this(pos, side, null);
    }

    /** Case ou se tient l'unite. */
    public BlockPos standPos() {
        return stand != null ? stand : side != null ? pos.relative(side) : pos.above();
    }

    /** Ou une unite se place pour acceder au bloc. */
    public Vec3 accessPoint() {
        return Vec3.atBottomCenterOf(standPos());
    }

    public Endpoint withSide(@Nullable Direction newSide) {
        return new Endpoint(pos, newSide, stand);
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.put("pos", NbtUtils.writeBlockPos(pos));
        tag.putByte("side", (byte) (side == null ? -1 : side.get3DDataValue()));
        if (stand != null) {
            tag.put("stand", NbtUtils.writeBlockPos(stand));
        }
        return tag;
    }

    public static Endpoint load(CompoundTag tag) {
        int side = tag.contains("side") ? tag.getByte("side") : 1;
        return new Endpoint(NbtUtils.readBlockPos(tag.getCompound("pos")), side < 0 ? null : Direction.from3DDataValue(side),
              tag.contains("stand") ? NbtUtils.readBlockPos(tag.getCompound("stand")) : null);
    }

    @Nullable
    public static Endpoint loadOptional(CompoundTag parent, String key) {
        return parent.contains(key) ? load(parent.getCompound(key)) : null;
    }
}
