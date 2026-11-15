package fr.pioneers.clan;

import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : donnee de monde des clans et des factions (Overworld), avec numero de version. Index joueur -> clan, delais de
 * ralliement apres un depart (3 jours reels). L'index des claims ({@link ClaimIndex}) se reconstruit depuis ces donnees au chargement.
 */
public final class ClanData extends SavedData {

    public static final String NAME = "pioneers_clans";
    public static final int VERSION = 1;

    private final Map<UUID, Clan> clans = new LinkedHashMap<>();
    private final Map<UUID, Faction> factions = new LinkedHashMap<>();
    private final Map<UUID, UUID> byPlayer = new HashMap<>();
    /** Joueur -> pas d'autre clan avant (ms). */
    public final Map<UUID, Long> cooldown = new HashMap<>();
    /** Guerres en cours et recentes (gerees par {@link Wars}) : attaquant, defenseur, dates, captures, treve. */
    public final ListTag wars = new ListTag();

    public static ClanData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ClanData::load, ClanData::new, NAME);
    }

    public Collection<Clan> clans() {
        return clans.values();
    }

    public Collection<Faction> factions() {
        return factions.values();
    }

    @Nullable
    public Clan clan(@Nullable UUID id) {
        return id == null ? null : clans.get(id);
    }

    @Nullable
    public Faction faction(@Nullable UUID id) {
        return id == null ? null : factions.get(id);
    }

    @Nullable
    public Clan clanOf(UUID player) {
        UUID id = byPlayer.get(player);
        return id == null ? null : clans.get(id);
    }

    public void put(Clan c) {
        clans.put(c.id, c);
        reindex(c);
        setDirty();
    }

    public void put(Faction f) {
        factions.put(f.id, f);
        setDirty();
    }

    public void remove(Clan c) {
        clans.remove(c.id);
        byPlayer.values().removeIf(id -> id.equals(c.id));
        setDirty();
    }

    public void remove(Faction f) {
        factions.remove(f.id);
        setDirty();
    }

    /** Recalcule l'index des joueurs d'un clan (apres une entree ou un depart). */
    public void reindex(Clan c) {
        byPlayer.values().removeIf(id -> id.equals(c.id));
        for (UUID m : c.members.keySet()) {
            byPlayer.put(m, c.id);
        }
        setDirty();
    }

    public Clan byName(String name) {
        for (Clan c : clans.values()) {
            if (c.name.equalsIgnoreCase(name)) {
                return c;
            }
        }
        return null;
    }

    public void changed() {
        setDirty();
    }

    private static ClanData load(CompoundTag tag) {
        ClanData d = new ClanData();
        for (Tag t : tag.getList("clans", Tag.TAG_COMPOUND)) {
            Clan c = Clan.load((CompoundTag) t);
            d.clans.put(c.id, c);
            for (UUID m : c.members.keySet()) {
                d.byPlayer.put(m, c.id);
            }
        }
        for (Tag t : tag.getList("factions", Tag.TAG_COMPOUND)) {
            Faction f = Faction.load((CompoundTag) t);
            d.factions.put(f.id, f);
        }
        d.wars.addAll(tag.getList("wars", Tag.TAG_COMPOUND));
        CompoundTag cd = tag.getCompound("cooldown");
        for (String k : cd.getAllKeys()) {
            d.cooldown.put(UUID.fromString(k), cd.getLong(k));
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("version", VERSION);
        ListTag l = new ListTag();
        clans.values().forEach(c -> l.add(c.save()));
        tag.put("clans", l);
        ListTag f = new ListTag();
        factions.values().forEach(x -> f.add(x.save()));
        tag.put("factions", f);
        tag.put("wars", wars.copy());
        CompoundTag cd = new CompoundTag();
        cooldown.forEach((u, t) -> cd.putLong(u.toString(), t));
        tag.put("cooldown", cd);
        return tag;
    }
}
