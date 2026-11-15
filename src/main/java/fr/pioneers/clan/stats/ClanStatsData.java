package fr.pioneers.clan.stats;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : donnee de monde des statistiques des joueurs (Overworld) : un anneau de {@code days} jours de jeu par joueur
 * ({@link StatsBucket}) et le dernier potentiel de frappe calcule (a la connexion et au changement d'equipement), avec sa vocation et le
 * niveau moyen des skills (fiche Membres). Rien n'est calcule par tick.
 */
public final class ClanStatsData extends SavedData {

    public static final String NAME = "pioneers_clan_stats";
    public static final int VERSION = 1;

    /** Statistiques d'un joueur. */
    public static final class Player {

        public final StatsBucket[] ring;
        /** Dernier potentiel (et ses parts), moment du calcul (ms), vocation, niveau moyen des skills (x10). */
        public int potential, combat, weapon, armor10, avgSkill10;
        public long potentialAt;
        public String vocation = "";

        Player(int days) {
            ring = new StatsBucket[Math.max(1, days)];
            for (int i = 0; i < ring.length; i++) {
                ring[i] = new StatsBucket();
            }
        }

        /** Le seau du jour (vide et remis a ce jour s'il portait un jour plus ancien). */
        public StatsBucket today(long day) {
            StatsBucket b = ring[(int) Math.floorMod(day, (long) ring.length)];
            if (b.day != day) {
                b.reset(day);
            }
            return b;
        }

        /** Le seau d'un jour passe s'il est encore dans l'anneau, sinon null. */
        public StatsBucket at(long day) {
            StatsBucket b = ring[(int) Math.floorMod(day, (long) ring.length)];
            return b.day == day ? b : null;
        }

        CompoundTag save() {
            CompoundTag t = new CompoundTag();
            ListTag l = new ListTag();
            for (StatsBucket b : ring) {
                if (b.day >= 0) {
                    l.add(b.save());
                }
            }
            t.put("ring", l);
            t.putInt("pot", potential);
            t.putInt("c", combat);
            t.putInt("w", weapon);
            t.putInt("a", armor10);
            t.putInt("s", avgSkill10);
            t.putLong("at", potentialAt);
            t.putString("voc", vocation);
            return t;
        }

        static Player load(CompoundTag t, int days) {
            Player p = new Player(days);
            for (Tag x : t.getList("ring", Tag.TAG_COMPOUND)) {
                StatsBucket b = StatsBucket.load((CompoundTag) x);
                if (b.day >= 0) {
                    p.ring[(int) Math.floorMod(b.day, (long) p.ring.length)] = b;
                }
            }
            p.potential = t.getInt("pot");
            p.combat = t.getInt("c");
            p.weapon = t.getInt("w");
            p.armor10 = t.getInt("a");
            p.avgSkill10 = t.getInt("s");
            p.potentialAt = t.getLong("at");
            p.vocation = t.getString("voc");
            return p;
        }
    }

    private final Map<UUID, Player> players = new HashMap<>();
    /** Journal de chaque clan deja lu pour les contributions (heure de la derniere ligne comptee, ms). */
    public final Map<UUID, Long> scanned = new HashMap<>();

    public static ClanStatsData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(ClanStatsData::load, ClanStatsData::new, NAME);
    }

    public Player of(UUID player) {
        return players.computeIfAbsent(player, k -> new Player(StatsRules.get().days()));
    }

    public Player peek(UUID player) {
        return players.get(player);
    }

    public void changed() {
        setDirty();
    }

    private static ClanStatsData load(CompoundTag tag) {
        ClanStatsData d = new ClanStatsData();
        int days = StatsRules.get().days();
        CompoundTag p = tag.getCompound("players");
        for (String k : p.getAllKeys()) {
            try {
                d.players.put(UUID.fromString(k), Player.load(p.getCompound(k), days));
            } catch (IllegalArgumentException ignored) {
                //cle illisible : ignoree
            }
        }
        CompoundTag sc = tag.getCompound("scanned");
        for (String k : sc.getAllKeys()) {
            try {
                d.scanned.put(UUID.fromString(k), sc.getLong(k));
            } catch (IllegalArgumentException ignored) {
                //cle illisible : ignoree
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("version", VERSION);
        CompoundTag p = new CompoundTag();
        players.forEach((u, s) -> p.put(u.toString(), s.save()));
        tag.put("players", p);
        CompoundTag sc = new CompoundTag();
        scanned.forEach((u, t) -> sc.putLong(u.toString(), t));
        tag.put("scanned", sc);
        return tag;
    }
}
