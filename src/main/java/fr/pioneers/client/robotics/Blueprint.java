package fr.pioneers.client.robotics;

import fr.pioneers.robotics.RoboticOrder;
import fr.pioneers.transport.legacy.LegacyCableBlock;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderGetter;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Plan (copie d'une construction) : blocs relatifs a son coin bas (l'air efface la case au collage ; une case absente ne touche a
 * rien), reglages des points logistiques et zones de travail relatifs, taille et profondeur sous le sol. Un plan nomme peut etre range
 * dans la bibliotheque ({@link BlueprintLibrary}) ; {@code id} est alors son fichier.
 */
public record Blueprint(@Nullable String id, String name, Map<BlockPos, BlockState> blocks, Map<BlockPos, CompoundTag> configs, List<RoboticOrder> zones,
                        int sx, int sy, int sz, int depth) {

    public static final int VERSION = 1;

    public Blueprint withName(@Nullable String id, String name) {
        return new Blueprint(id, name, blocks, configs, zones, sx, sy, sz, depth);
    }

    public Blueprint withBlocks(Map<BlockPos, BlockState> blocks) {
        return new Blueprint(id, name, blocks, configs, zones, sx, sy, sz, depth);
    }

    public Blueprint withExtras(Map<BlockPos, CompoundTag> configs, List<RoboticOrder> zones) {
        return new Blueprint(id, name, blocks, configs, zones, sx, sy, sz, depth);
    }

    /** Blocs pleins (hors air). */
    public int solidCount() {
        int count = 0;
        for (BlockState state : blocks.values()) {
            if (!state.isAir()) {
                count++;
            }
        }
        return count;
    }

    /** Materiaux : objet -> nombre de blocs (les plus nombreux d'abord). */
    public List<Map.Entry<Item, Integer>> materials() {
        Map<Item, Integer> counts = new HashMap<>();
        for (BlockState state : blocks.values()) {
            Item item = state.getBlock().asItem();
            if (!state.isAir() && item != Items.AIR) {
                counts.merge(item, 1, Integer::sum);
            }
        }
        List<Map.Entry<Item, Integer>> list = new ArrayList<>(counts.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return list;
    }

    /** Sans ces cases (gomme). */
    public Blueprint erase(Collection<BlockPos> cells) {
        Map<BlockPos, BlockState> kept = new LinkedHashMap<>(blocks);
        Map<BlockPos, CompoundTag> keptConfigs = new LinkedHashMap<>(configs);
        for (BlockPos pos : cells) {
            kept.remove(pos);
            keptConfigs.remove(pos);
        }
        return new Blueprint(id, name, kept, keptConfigs, zones, sx, sy, sz, depth);
    }

    /** Sans les blocs de ce type. */
    public Blueprint without(Block block) {
        Map<BlockPos, BlockState> kept = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue().getBlock() != block) {
                kept.put(entry.getKey(), entry.getValue());
            }
        }
        Map<BlockPos, CompoundTag> keptConfigs = new LinkedHashMap<>(configs);
        keptConfigs.keySet().removeIf(pos -> !kept.containsKey(pos) || kept.get(pos).isAir());
        return new Blueprint(id, name, kept, keptConfigs, zones, sx, sy, sz, depth);
    }

    /** Les blocs d'un type remplaces par un autre (meme orientation quand c'est possible). */
    public Blueprint replace(Block from, Block to) {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockState state = entry.getValue();
            if (state.getBlock() == from) {
                BlockState swapped = to.defaultBlockState();
                for (var property : state.getProperties()) {
                    swapped = copy(state, swapped, property);
                }
                state = swapped;
            }
            out.put(entry.getKey(), state);
        }
        return withBlocks(out);
    }

    private static <T extends Comparable<T>> BlockState copy(BlockState from, BlockState to, Property<T> property) {
        return to.hasProperty(property) ? to.setValue(property, from.getValue(property)) : to;
    }

    /** L'air explicite retire : le collage ne videra plus rien, il ne fera que poser. */
    public Blueprint withoutAir() {
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (!entry.getValue().isAir()) {
                out.put(entry.getKey(), entry.getValue());
            }
        }
        return withBlocks(out);
    }

    /** Rogne le plan aux blocs pleins (les bords vides ou d'air disparaissent) ; la profondeur sous le sol suit. */
    public Blueprint trimmed() {
        int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, z0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE, z1 = Integer.MIN_VALUE;
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            if (entry.getValue().isAir()) {
                continue;
            }
            BlockPos p = entry.getKey();
            x0 = Math.min(x0, p.getX());
            y0 = Math.min(y0, p.getY());
            z0 = Math.min(z0, p.getZ());
            x1 = Math.max(x1, p.getX());
            y1 = Math.max(y1, p.getY());
            z1 = Math.max(z1, p.getZ());
        }
        if (x0 == Integer.MAX_VALUE) {
            return this;
        }
        BlockPos shift = new BlockPos(x0, y0, z0);
        Map<BlockPos, BlockState> out = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            BlockPos p = entry.getKey();
            if (p.getX() >= x0 && p.getX() <= x1 && p.getY() >= y0 && p.getY() <= y1 && p.getZ() >= z0 && p.getZ() <= z1) {
                out.put(p.subtract(shift), entry.getValue());
            }
        }
        Map<BlockPos, CompoundTag> outConfigs = new LinkedHashMap<>();
        for (Map.Entry<BlockPos, CompoundTag> entry : configs.entrySet()) {
            if (out.containsKey(entry.getKey().subtract(shift))) {
                outConfigs.put(entry.getKey().subtract(shift), entry.getValue());
            }
        }
        List<RoboticOrder> outZones = new ArrayList<>();
        for (RoboticOrder zone : zones) {
            RoboticOrder copy = zone.copy();
            copy.min = zone.min.subtract(shift);
            copy.max = zone.max.subtract(shift);
            outZones.add(copy);
        }
        return new Blueprint(id, name, out, outConfigs, outZones, x1 - x0 + 1, y1 - y0 + 1, z1 - z0 + 1, depth + y0);
    }

    // ------------------------------------------------------------------
    // Sauvegarde : palette d'etats + une case par position
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putInt("version", VERSION);
        tag.putString("name", name);
        tag.putInt("sx", sx);
        tag.putInt("sy", sy);
        tag.putInt("sz", sz);
        tag.putInt("depth", depth);
        Map<BlockState, Integer> palette = new LinkedHashMap<>();
        int[] cells = new int[sx * sy * sz];
        Arrays.fill(cells, -1);
        for (Map.Entry<BlockPos, BlockState> entry : blocks.entrySet()) {
            int index = index(entry.getKey());
            if (index >= 0) {
                cells[index] = palette.computeIfAbsent(entry.getValue(), k -> palette.size());
            }
        }
        ListTag states = new ListTag();
        for (BlockState state : palette.keySet()) {
            states.add(NbtUtils.writeBlockState(state));
        }
        tag.put("palette", states);
        tag.putIntArray("cells", cells);
        ListTag configList = new ListTag();
        for (Map.Entry<BlockPos, CompoundTag> entry : configs.entrySet()) {
            CompoundTag c = new CompoundTag();
            c.put("pos", NbtUtils.writeBlockPos(entry.getKey()));
            c.put("config", entry.getValue());
            configList.add(c);
        }
        tag.put("configs", configList);
        ListTag zoneList = new ListTag();
        for (RoboticOrder zone : zones) {
            zoneList.add(zone.save(false));
        }
        tag.put("zones", zoneList);
        return tag;
    }

    private int index(BlockPos p) {
        if (p.getX() < 0 || p.getY() < 0 || p.getZ() < 0 || p.getX() >= sx || p.getY() >= sy || p.getZ() >= sz) {
            return -1;
        }
        return (p.getY() * sz + p.getZ()) * sx + p.getX();
    }

    public static Blueprint load(@Nullable String id, CompoundTag tag, HolderGetter<Block> blocks) {
        int sx = Math.max(1, tag.getInt("sx")), sy = Math.max(1, tag.getInt("sy")), sz = Math.max(1, tag.getInt("sz"));
        List<BlockState> palette = new ArrayList<>();
        for (Tag entry : tag.getList("palette", Tag.TAG_COMPOUND)) {
            palette.add(LegacyCableBlock.upgrade(NbtUtils.readBlockState(blocks, (CompoundTag) entry)));   //1.11 : ancien cable -> cable logistique
        }
        int[] cells = tag.getIntArray("cells");
        Map<BlockPos, BlockState> map = new LinkedHashMap<>();
        for (int i = 0; i < cells.length && i < sx * sy * sz; i++) {
            if (cells[i] >= 0 && cells[i] < palette.size()) {
                int x = i % sx, z = (i / sx) % sz, y = i / (sx * sz);
                map.put(new BlockPos(x, y, z), palette.get(cells[i]));
            }
        }
        Map<BlockPos, CompoundTag> configs = new LinkedHashMap<>();
        for (Tag entry : tag.getList("configs", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) entry;
            configs.put(NbtUtils.readBlockPos(c.getCompound("pos")), c.getCompound("config"));
        }
        List<RoboticOrder> zones = new ArrayList<>();
        for (Tag entry : tag.getList("zones", Tag.TAG_COMPOUND)) {
            RoboticOrder zone = RoboticOrder.load((CompoundTag) entry);
            if (zone.min != null && zone.max != null) {
                zones.add(zone);
            }
        }
        return new Blueprint(id, tag.getString("name"), map, configs, zones, sx, sy, sz, tag.getInt("depth"));
    }
}
