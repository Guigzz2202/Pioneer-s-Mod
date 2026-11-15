package fr.pioneers.economy.stall;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : registre des etals et comptoirs de chaque joueur dans le monde (donnee de monde, Overworld), mis a jour a la pose et
 * a la casse, pour les limites {@code stalls} / {@code counters} par joueur et par monde. Une entree dont le bloc n'est plus un etal du meme
 * proprietaire est oubliee quand on compte (seulement si son troncon est charge : on ne charge jamais de troncon pour compter).
 */
public final class StallRegistry extends SavedData {

    private static final String NAME = "pioneers_stalls";

    /** Un etal : dimension, position, comptoir ou non. */
    public record Place(String dim, long pos, boolean counter) {
    }

    private final Map<UUID, List<Place>> byOwner = new HashMap<>();

    public static StallRegistry get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(StallRegistry::load, StallRegistry::new, NAME);
    }

    private static String dim(Level level) {
        return level.dimension().location().toString();
    }

    public void add(UUID owner, Level level, BlockPos pos, boolean counter) {
        remove(level, pos);
        byOwner.computeIfAbsent(owner, k -> new ArrayList<>()).add(new Place(dim(level), pos.asLong(), counter));
        setDirty();
    }

    public void remove(Level level, BlockPos pos) {
        String d = dim(level);
        long p = pos.asLong();
        for (Iterator<List<Place>> it = byOwner.values().iterator(); it.hasNext(); ) {
            List<Place> l = it.next();
            if (l.removeIf(x -> x.pos() == p && x.dim().equals(d))) {
                setDirty();
            }
            if (l.isEmpty()) {
                it.remove();
            }
        }
    }

    /** Etals (ou comptoirs) de ce joueur, apres avoir oublie les entrees perimees des troncons charges. */
    public int count(MinecraftServer server, UUID owner, boolean counter) {
        List<Place> l = byOwner.get(owner);
        if (l == null) {
            return 0;
        }
        int n = 0;
        for (Iterator<Place> it = l.iterator(); it.hasNext(); ) {
            Place x = it.next();
            ServerLevel level = server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(x.dim())));
            BlockPos pos = BlockPos.of(x.pos());
            if (level != null && level.isLoaded(pos)) {
                if (!(level.getBlockEntity(pos) instanceof StallTile t) || !owner.equals(t.owner())) {
                    it.remove();
                    setDirty();
                    continue;
                }
            }
            if (x.counter() == counter) {
                n++;
            }
        }
        return n;
    }

    private static StallRegistry load(CompoundTag tag) {
        StallRegistry r = new StallRegistry();
        for (Tag t : tag.getList("owners", Tag.TAG_COMPOUND)) {
            CompoundTag o = (CompoundTag) t;
            List<Place> l = new ArrayList<>();
            for (Tag p : o.getList("places", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) p;
                l.add(new Place(c.getString("d"), c.getLong("p"), c.getBoolean("c")));
            }
            if (!l.isEmpty()) {
                r.byOwner.put(o.getUUID("u"), l);
            }
        }
        return r;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag owners = new ListTag();
        byOwner.forEach((u, l) -> {
            CompoundTag o = new CompoundTag();
            o.putUUID("u", u);
            ListTag places = new ListTag();
            for (Place x : l) {
                CompoundTag c = new CompoundTag();
                c.putString("d", x.dim());
                c.putLong("p", x.pos());
                c.putBoolean("c", x.counter());
                places.add(c);
            }
            o.put("places", places);
            owners.add(o);
        });
        tag.put("owners", owners);
        return tag;
    }
}
