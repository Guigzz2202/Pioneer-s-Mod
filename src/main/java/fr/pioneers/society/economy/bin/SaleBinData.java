package fr.pioneers.society.economy.bin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.10.1 : bacs de vente poses dans une dimension (donnee de monde) : position -> proprietaire et village. Sert a la regle « un bac par
 * joueur et par village » (sinon la limite de la nuit se multiplierait). Une entree dont le bloc a disparu sans prevenir (chunk modifie hors
 * jeu) est retiree a la premiere verification ou son chunk est charge.
 */
public final class SaleBinData extends SavedData {

    public static final String NAME = "pioneers_sale_bins";

    public record Entry(UUID owner, long village) {
    }

    private final Map<Long, Entry> bins = new HashMap<>();

    public static SaleBinData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(SaleBinData::load, SaleBinData::new, NAME);
    }

    /** Bac de ce joueur dans ce village (autre que {@code except}), encore present, ou null. */
    @Nullable
    public BlockPos find(ServerLevel level, UUID owner, long village, @Nullable BlockPos except) {
        for (Map.Entry<Long, Entry> e : Map.copyOf(bins).entrySet()) {
            if (!e.getValue().owner().equals(owner) || e.getValue().village() != village) {
                continue;
            }
            BlockPos pos = BlockPos.of(e.getKey());
            if (pos.equals(except)) {
                continue;
            }
            if (level.isLoaded(pos) && !(level.getBlockState(pos).getBlock() instanceof SaleBinBlock)) {
                bins.remove(e.getKey());
                setDirty();
                continue;
            }
            return pos;
        }
        return null;
    }

    public void put(BlockPos pos, UUID owner, long village) {
        bins.put(pos.asLong(), new Entry(owner, village));
        setDirty();
    }

    public void remove(BlockPos pos) {
        if (bins.remove(pos.asLong()) != null) {
            setDirty();
        }
    }

    public int size() {
        return bins.size();
    }

    private static SaleBinData load(CompoundTag tag) {
        SaleBinData d = new SaleBinData();
        for (Tag t : tag.getList("b", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.bins.put(c.getLong("p"), new Entry(c.getUUID("o"), c.getLong("v")));
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag l = new ListTag();
        bins.forEach((p, e) -> {
            CompoundTag c = new CompoundTag();
            c.putLong("p", p);
            c.putUUID("o", e.owner());
            c.putLong("v", e.village());
            l.add(c);
        });
        tag.put("b", l);
        return tag;
    }
}
