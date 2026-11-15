package fr.pioneers.economy.peddler;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 3) : donnee de monde des Colporteurs ({@code pioneers_peddlers}, par dimension) : dernier matin traite, caravanes en
 * cours (blocs poses, Colporteurs), registre des Haltes (blocs poses, deux places et leur retour), totaux d'echange par groupe (XP de
 * caravane). Les blocs poses sont gardes avec leur bloc : seul un bloc encore identique est retire.
 */
public final class PeddlerData extends SavedData {

    public static final String NAME = "pioneers_peddlers";

    /** Un bloc pose par une caravane ou une Halte. */
    public record Laid(BlockPos pos, Block block) {
    }

    /** Une caravane de village : Tableau, fin, debut, blocs poses, Colporteurs ; {@code ended} = retrait en cours (troncons non charges). */
    public static final class Caravan {

        public final long village;
        public final BlockPos board;
        public long until;
        public final long start;
        public final List<Laid> blocks = new ArrayList<>();
        public final List<UUID> peddlers = new ArrayList<>();
        public boolean ended;

        Caravan(long village, BlockPos board, long until, long start) {
            this.village = village;
            this.board = board;
            this.until = until;
            this.start = start;
        }
    }

    /** Une Halte : centre (feu), blocs poses, deux places (Colporteur present, jour de retour apres une mort, absences constatees). */
    public static final class Halt {

        public final long id;
        public final BlockPos center;
        public final List<Laid> blocks = new ArrayList<>();
        public final List<BlockPos> spots = new ArrayList<>();
        public final UUID[] peddlers = new UUID[2];
        public final long[] back = {-1, -1};
        public final int[] missing = {0, 0};

        Halt(long id, BlockPos center) {
            this.id = id;
            this.center = center;
        }
    }

    public long lastDay = -1;
    public long nextHalt = 1;
    public final Map<Long, Caravan> caravans = new LinkedHashMap<>();
    public final List<Halt> halts = new ArrayList<>();
    /** Blocs a retirer dans des troncons qui n'etaient pas charges (caravane finie, Halte retiree). */
    public final List<Laid> leftovers = new ArrayList<>();
    /** Totaux d'echange (jetons) par groupe de Colporteurs et par joueur, pour l'XP de caravane. */
    public final Map<String, Map<UUID, Long>> totals = new HashMap<>();

    public static PeddlerData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(PeddlerData::load, PeddlerData::new, NAME);
    }

    @Nullable
    public Halt halt(long id) {
        for (Halt h : halts) {
            if (h.id == id) {
                return h;
            }
        }
        return null;
    }

    public Halt addHalt(BlockPos center) {
        Halt h = new Halt(nextHalt++, center);
        halts.add(h);
        setDirty();
        return h;
    }

    public Caravan addCaravan(long village, BlockPos board, long until, long start) {
        Caravan c = new Caravan(village, board, until, start);
        caravans.put(village, c);
        setDirty();
        return c;
    }

    /** Ajoute {@code total} jetons au cumul du joueur dans ce groupe ; renvoie le nouveau cumul. */
    public long addTotal(String group, UUID player, long total) {
        long now = totals.computeIfAbsent(group, k -> new HashMap<>()).merge(player, total, Long::sum);
        setDirty();
        return now;
    }

    public void forget(String group) {
        if (totals.remove(group) != null) {
            setDirty();
        }
    }

    /** Oublie les totaux des Haltes d'un jour passe (cle {@code h:<id>:<jour>}). */
    public void forgetHaltTotals(long today) {
        Iterator<String> it = totals.keySet().iterator();
        while (it.hasNext()) {
            String k = it.next();
            if (k.startsWith("h:") && !k.endsWith(":" + today)) {
                it.remove();
                setDirty();
            }
        }
    }

    // ------------------------------------------------------------------ NBT
    private static ListTag saveBlocks(List<Laid> blocks) {
        ListTag l = new ListTag();
        for (Laid b : blocks) {
            CompoundTag t = new CompoundTag();
            t.putLong("p", b.pos().asLong());
            t.putString("b", BuiltInRegistries.BLOCK.getKey(b.block()).toString());
            l.add(t);
        }
        return l;
    }

    private static void loadBlocks(ListTag l, List<Laid> into) {
        for (int i = 0; i < l.size(); i++) {
            CompoundTag t = l.getCompound(i);
            Block b = BuiltInRegistries.BLOCK.get(ResourceLocation.tryParse(t.getString("b")));
            if (b != Blocks.AIR) {
                into.add(new Laid(BlockPos.of(t.getLong("p")), b));
            }
        }
    }

    private static ListTag saveUuids(List<UUID> ids) {
        ListTag l = new ListTag();
        for (UUID u : ids) {
            CompoundTag t = new CompoundTag();
            t.putUUID("u", u);
            l.add(t);
        }
        return l;
    }

    private static void loadUuids(ListTag l, List<UUID> into) {
        for (int i = 0; i < l.size(); i++) {
            into.add(l.getCompound(i).getUUID("u"));
        }
    }

    public static PeddlerData load(CompoundTag tag) {
        PeddlerData d = new PeddlerData();
        d.lastDay = tag.getLong("lastDay");
        d.nextHalt = Math.max(1, tag.getLong("nextHalt"));
        ListTag cl = tag.getList("caravans", Tag.TAG_COMPOUND);
        for (int i = 0; i < cl.size(); i++) {
            CompoundTag t = cl.getCompound(i);
            Caravan c = new Caravan(t.getLong("village"), BlockPos.of(t.getLong("board")), t.getLong("until"), t.getLong("start"));
            c.ended = t.getBoolean("ended");
            loadBlocks(t.getList("blocks", Tag.TAG_COMPOUND), c.blocks);
            loadUuids(t.getList("peddlers", Tag.TAG_COMPOUND), c.peddlers);
            d.caravans.put(c.village, c);
        }
        ListTag hl = tag.getList("halts", Tag.TAG_COMPOUND);
        for (int i = 0; i < hl.size(); i++) {
            CompoundTag t = hl.getCompound(i);
            Halt h = new Halt(t.getLong("id"), BlockPos.of(t.getLong("center")));
            loadBlocks(t.getList("blocks", Tag.TAG_COMPOUND), h.blocks);
            for (long p : t.getLongArray("spots")) {
                h.spots.add(BlockPos.of(p));
            }
            for (int s = 0; s < 2; s++) {
                if (t.hasUUID("p" + s)) {
                    h.peddlers[s] = t.getUUID("p" + s);
                }
                h.back[s] = t.contains("back" + s) ? t.getLong("back" + s) : -1;
            }
            d.halts.add(h);
        }
        loadBlocks(tag.getList("leftovers", Tag.TAG_COMPOUND), d.leftovers);
        CompoundTag tt = tag.getCompound("totals");
        for (String g : tt.getAllKeys()) {
            CompoundTag per = tt.getCompound(g);
            Map<UUID, Long> m = new HashMap<>();
            for (String u : per.getAllKeys()) {
                try {
                    m.put(UUID.fromString(u), per.getLong(u));
                } catch (IllegalArgumentException ignored) {
                    //cle illisible : ignoree
                }
            }
            d.totals.put(g, m);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putLong("lastDay", lastDay);
        tag.putLong("nextHalt", nextHalt);
        ListTag cl = new ListTag();
        for (Caravan c : caravans.values()) {
            CompoundTag t = new CompoundTag();
            t.putLong("village", c.village);
            t.putLong("board", c.board.asLong());
            t.putLong("until", c.until);
            t.putLong("start", c.start);
            t.putBoolean("ended", c.ended);
            t.put("blocks", saveBlocks(c.blocks));
            t.put("peddlers", saveUuids(c.peddlers));
            cl.add(t);
        }
        tag.put("caravans", cl);
        ListTag hl = new ListTag();
        for (Halt h : halts) {
            CompoundTag t = new CompoundTag();
            t.putLong("id", h.id);
            t.putLong("center", h.center.asLong());
            t.put("blocks", saveBlocks(h.blocks));
            t.putLongArray("spots", h.spots.stream().mapToLong(BlockPos::asLong).toArray());
            for (int s = 0; s < 2; s++) {
                if (h.peddlers[s] != null) {
                    t.putUUID("p" + s, h.peddlers[s]);
                }
                t.putLong("back" + s, h.back[s]);
            }
            hl.add(t);
        }
        tag.put("halts", hl);
        tag.put("leftovers", saveBlocks(leftovers));
        CompoundTag tt = new CompoundTag();
        totals.forEach((g, m) -> {
            CompoundTag per = new CompoundTag();
            m.forEach((u, v) -> per.putLong(u.toString(), v));
            tt.put(g, per);
        });
        tag.put("totals", tt);
        return tag;
    }
}
