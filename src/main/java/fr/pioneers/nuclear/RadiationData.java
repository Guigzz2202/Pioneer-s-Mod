package fr.pioneers.nuclear;

import it.unimi.dsi.fastutil.longs.Long2ByteMap;
import it.unimi.dsi.fastutil.longs.Long2ByteOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Radiation d'une dimension (0.17.0) : zones contaminees (fusion du coeur : intensite au centre, rayon, demi-vie) et blocs radioactifs
 * (corium, corium refroidi, chateau d'entreposage) ranges par chunk. Debit de dose en un point (Sv/h) :
 * somme des sources, chacune {@code intensite / (1 + d^2 / 16)} dans son rayon. Bon marche : quelques zones, et seulement les chunks voisins.
 */
public final class RadiationData extends SavedData {

    private static final String NAME = "pioneers_radiation";
    /** Blocs radioactifs : intensite (Sv/h) et rayon par sorte. */
    public static final byte CORIUM = 0, COOLED_CORIUM = 1, CASK = 2, RTG = 3, WASTE_PACKAGE = 4;
    private static final double[] BLOCK_RATE = {4.0, 1.0, 0.2, 0.1, 2.0};
    private static final int BLOCK_RADIUS = 12;
    /** Zone eteinte sous cette intensite (Sv/h). */
    private static final double ZONE_END = 0.01;

    /** Zone contaminee (fusion du coeur). */
    public record Zone(double x, double y, double z, double strength, double radius, long start, double halfLife) {

        double strengthAt(long now) {
            return strength * Math.pow(0.5, Math.max(0, now - start) / halfLife);
        }
    }

    private final List<Zone> zones = new ArrayList<>();
    private final Long2ObjectMap<Long2ByteMap> blocks = new Long2ObjectOpenHashMap<>();

    public static RadiationData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RadiationData::load, RadiationData::new, NAME);
    }

    public void addZone(Vec3 center, double strength, double radius, long now, double halfLife) {
        zones.add(new Zone(center.x, center.y, center.z, strength, radius, now, halfLife));
        setDirty();
    }

    public void addBlock(BlockPos pos, byte kind) {
        blocks.computeIfAbsent(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4), k -> new Long2ByteOpenHashMap()).put(pos.asLong(), kind);
        setDirty();
    }

    /** Bloc radioactif enregistre ici ? */
    public boolean hasBlock(BlockPos pos) {
        Long2ByteMap map = blocks.get(ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        return map != null && map.containsKey(pos.asLong());
    }

    public void removeBlock(BlockPos pos) {
        long key = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        Long2ByteMap map = blocks.get(key);
        if (map != null && map.containsKey(pos.asLong())) {
            map.remove(pos.asLong());
            if (map.isEmpty()) {
                blocks.remove(key);
            }
            setDirty();
        }
    }

    /** Debit de dose (Sv/h) en un point. Les zones eteintes sont oubliees au passage. */
    public double rate(Vec3 at, long now) {
        double total = 0;
        boolean expired = false;
        for (Zone zone : zones) {
            double s = zone.strengthAt(now);
            if (s < ZONE_END) {
                expired = true;
                continue;
            }
            double dx = at.x - zone.x, dy = at.y - zone.y, dz = at.z - zone.z;
            double d2 = dx * dx + dy * dy + dz * dz;
            if (d2 <= zone.radius * zone.radius) {
                total += s / (1 + d2 / 16);
            }
        }
        if (expired) {
            zones.removeIf(z -> z.strengthAt(now) < ZONE_END);
            setDirty();
        }
        if (!blocks.isEmpty()) {
            int cx = (int) Math.floor(at.x) >> 4, cz = (int) Math.floor(at.z) >> 4;
            for (int i = -1; i <= 1; i++) {
                for (int j = -1; j <= 1; j++) {
                    Long2ByteMap map = blocks.get(ChunkPos.asLong(cx + i, cz + j));
                    if (map == null) {
                        continue;
                    }
                    for (Long2ByteMap.Entry e : map.long2ByteEntrySet()) {
                        long p = e.getLongKey();
                        double dx = at.x - (BlockPos.getX(p) + 0.5), dy = at.y - (BlockPos.getY(p) + 0.5), dz = at.z - (BlockPos.getZ(p) + 0.5);
                        double d2 = dx * dx + dy * dy + dz * dz;
                        if (d2 <= BLOCK_RADIUS * BLOCK_RADIUS) {
                            total += BLOCK_RATE[Math.max(0, Math.min(BLOCK_RATE.length - 1, e.getByteValue()))] / (1 + d2 / 16);
                        }
                    }
                }
            }
        }
        return total;
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    private static RadiationData load(CompoundTag tag) {
        RadiationData data = new RadiationData();
        for (Tag t : tag.getList("zones", Tag.TAG_COMPOUND)) {
            CompoundTag z = (CompoundTag) t;
            data.zones.add(new Zone(z.getDouble("x"), z.getDouble("y"), z.getDouble("z"), z.getDouble("s"), z.getDouble("r"), z.getLong("t"),
                  Math.max(1, z.getDouble("h"))));
        }
        long[] pos = tag.getLongArray("blocks");
        byte[] kinds = tag.getByteArray("kinds");
        for (int i = 0; i < pos.length && i < kinds.length; i++) {
            BlockPos p = BlockPos.of(pos[i]);
            data.blocks.computeIfAbsent(ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4), k -> new Long2ByteOpenHashMap()).put(pos[i], kinds[i]);
        }
        return data;
    }

    @NotNull
    @Override
    public CompoundTag save(@NotNull CompoundTag tag) {
        ListTag list = new ListTag();
        for (Zone zone : zones) {
            CompoundTag z = new CompoundTag();
            z.putDouble("x", zone.x);
            z.putDouble("y", zone.y);
            z.putDouble("z", zone.z);
            z.putDouble("s", zone.strength);
            z.putDouble("r", zone.radius);
            z.putLong("t", zone.start);
            z.putDouble("h", zone.halfLife);
            list.add(z);
        }
        tag.put("zones", list);
        List<Long> pos = new ArrayList<>();
        List<Byte> kinds = new ArrayList<>();
        for (Long2ByteMap map : blocks.values()) {
            for (Long2ByteMap.Entry e : map.long2ByteEntrySet()) {
                pos.add(e.getLongKey());
                kinds.add(e.getByteValue());
            }
        }
        long[] p = new long[pos.size()];
        byte[] k = new byte[kinds.size()];
        for (int i = 0; i < p.length; i++) {
            p[i] = pos.get(i);
            k[i] = kinds.get(i);
        }
        tag.putLongArray("blocks", p);
        tag.putByteArray("kinds", k);
        return tag;
    }
}
