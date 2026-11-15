package fr.pioneers.world.ruins;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.AABB;

/**
 * 1.9 (MAJ RP, bloc 1) : registre des ruines d'une dimension (donnees de monde {@code pioneers_ruins}). Un site est cree par le Controleur
 * delabre a son premier chargement (la generation du monde ne touche pas aux donnees sauvees : elle tourne hors du fil principal). Index par
 * chunk pour {@link #siteAt}. Le projet de restauration d'un site est range ici (il avance meme chunk decharge).
 */
public class RuinSites extends SavedData {

    public static final String NAME = "pioneers_ruins";

    public enum State {
        /** Ruine intacte (on peut la demonter ou la restaurer). */
        INTACT,
        /** Demontage entame (des blocs delabres ont ete casses). */
        DISMANTLED,
        /** Chantier de restauration en cours : blocs proteges. */
        RESTORING,
        /** Remise en service. */
        RESTORED
    }

    public static final class Site {
        public final long id;
        public final int tier;
        public final String machine;
        public final BlockPos min, max;
        public State state = State.INTACT;
        public long standbyUntil = -1;
        public int code;
        public int wrong;
        public long lockedUntil = -1;
        public int broken;
        /** Projet de restauration (null s'il n'y en a pas). */
        @Nullable
        public CompoundTag project;
        public final Map<UUID, Long> contributors = new LinkedHashMap<>();

        Site(long id, int tier, String machine, BlockPos min, BlockPos max) {
            this.id = id;
            this.tier = tier;
            this.machine = machine;
            this.min = min;
            this.max = max;
        }

        public BlockPos controller() {
            return BlockPos.of(id);
        }

        public AABB box(double margin) {
            return new AABB(min.getX() - margin, min.getY() - margin, min.getZ() - margin, max.getX() + 1 + margin, max.getY() + 1 + margin,
                  max.getZ() + 1 + margin);
        }

        public boolean contains(BlockPos pos, int margin) {
            return pos.getX() >= min.getX() - margin && pos.getX() <= max.getX() + margin && pos.getY() >= min.getY() - margin
                   && pos.getY() <= max.getY() + margin && pos.getZ() >= min.getZ() - margin && pos.getZ() <= max.getZ() + margin;
        }

        public boolean standby(long now) {
            return now < standbyUntil;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            t.putLong("id", id);
            t.putInt("tier", tier);
            t.putString("machine", machine);
            t.putLong("min", min.asLong());
            t.putLong("max", max.asLong());
            t.putString("state", state.name());
            t.putLong("standby", standbyUntil);
            t.putInt("code", code);
            t.putInt("wrong", wrong);
            t.putLong("locked", lockedUntil);
            t.putInt("broken", broken);
            if (project != null) {
                t.put("project", project);
            }
            ListTag c = new ListTag();
            contributors.forEach((u, n) -> {
                CompoundTag e = new CompoundTag();
                e.putUUID("u", u);
                e.putLong("n", n);
                c.add(e);
            });
            t.put("contrib", c);
            return t;
        }

        static Site load(CompoundTag t) {
            Site s = new Site(t.getLong("id"), t.getInt("tier"), t.getString("machine"), BlockPos.of(t.getLong("min")), BlockPos.of(t.getLong("max")));
            try {
                s.state = State.valueOf(t.getString("state"));
            } catch (IllegalArgumentException ex) {
                s.state = State.INTACT;
            }
            s.standbyUntil = t.getLong("standby");
            s.code = t.getInt("code");
            s.wrong = t.getInt("wrong");
            s.lockedUntil = t.getLong("locked");
            s.broken = t.getInt("broken");
            s.project = t.contains("project") ? t.getCompound("project") : null;
            for (Tag e : t.getList("contrib", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                s.contributors.put(c.getUUID("u"), c.getLong("n"));
            }
            return s;
        }
    }

    private final Map<Long, Site> sites = new HashMap<>();
    private final Map<Long, List<Long>> byChunk = new HashMap<>();

    public static RuinSites get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(RuinSites::load, RuinSites::new, NAME);
    }

    private static RuinSites load(CompoundTag tag) {
        RuinSites r = new RuinSites();
        for (Tag t : tag.getList("sites", Tag.TAG_COMPOUND)) {
            r.put(Site.load((CompoundTag) t));
        }
        return r;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag l = new ListTag();
        sites.values().forEach(s -> l.add(s.save()));
        tag.put("sites", l);
        return tag;
    }

    private void put(Site s) {
        sites.put(s.id, s);
        for (int cx = s.min.getX() >> 4; cx <= s.max.getX() >> 4; cx++) {
            for (int cz = s.min.getZ() >> 4; cz <= s.max.getZ() >> 4; cz++) {
                byChunk.computeIfAbsent(ChunkPos.asLong(cx, cz), k -> new ArrayList<>()).add(s.id);
            }
        }
    }

    /** Site de ce controleur, cree au besoin (code de console tire de la graine du monde et de la position). */
    public Site ensure(ServerLevel level, long id, int tier, String machine, BlockPos min, BlockPos max) {
        Site s = sites.get(id);
        if (s == null) {
            s = new Site(id, tier, machine, min, max);
            long h = level.getSeed() * 31 + id * 0x9E3779B97F4A7C15L;
            s.code = (int) Math.floorMod(h ^ (h >>> 29), 10000L);
            put(s);
            setDirty();
        }
        return s;
    }

    @Nullable
    public Site site(long id) {
        return sites.get(id);
    }

    /** Site qui contient cette position (marge en blocs autour de sa boite). */
    @Nullable
    public Site siteAt(BlockPos pos, int margin) {
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                List<Long> ids = byChunk.get(ChunkPos.asLong((pos.getX() >> 4) + dx, (pos.getZ() >> 4) + dz));
                if (ids == null) {
                    continue;
                }
                for (long id : ids) {
                    Site s = sites.get(id);
                    if (s != null && s.contains(pos, margin)) {
                        return s;
                    }
                }
            }
        }
        return null;
    }

    public java.util.Collection<Site> all() {
        return sites.values();
    }

    public void changed() {
        setDirty();
    }
}
