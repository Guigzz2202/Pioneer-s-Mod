package fr.pioneers.society.clan.stats;

import java.util.LinkedHashMap;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;

/**
 * 1.9 (MAJ RP, bloc 4) : les statistiques d'un joueur pour UN jour de jeu (anneau de 30 jours dans {@link ClanStatsData}) : valeur produite
 * par skill (centiemes de jeton), creatures hostiles, Sentinelles et joueurs ennemis tues, morts, jetons verses au tresor, temps de jeu (s).
 */
public final class StatsBucket {

    public long day = -1;
    public final Map<String, Long> produced = new LinkedHashMap<>();
    public long total;
    public int hostiles, sentinels, enemies, deaths;
    public long contributed, played;
    /** 1.9 (MAJ RP, bloc 4) : plus haut potentiel de frappe calcule ce jour-la (courbe de potentiel de l'onglet Clan). */
    public int potential;

    public void reset(long day) {
        this.day = day;
        produced.clear();
        total = 0;
        hostiles = sentinels = enemies = deaths = 0;
        contributed = played = 0;
        potential = 0;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putLong("d", day);
        CompoundTag p = new CompoundTag();
        produced.forEach(p::putLong);
        t.put("p", p);
        t.putLong("t", total);
        t.putInt("h", hostiles);
        t.putInt("s", sentinels);
        t.putInt("e", enemies);
        t.putInt("x", deaths);
        t.putLong("c", contributed);
        t.putLong("pl", played);
        t.putInt("pt", potential);
        return t;
    }

    public static StatsBucket load(CompoundTag t) {
        StatsBucket b = new StatsBucket();
        b.day = t.getLong("d");
        CompoundTag p = t.getCompound("p");
        p.getAllKeys().forEach(k -> b.produced.put(k, p.getLong(k)));
        b.total = t.getLong("t");
        b.hostiles = t.getInt("h");
        b.sentinels = t.getInt("s");
        b.enemies = t.getInt("e");
        b.deaths = t.getInt("x");
        b.contributed = t.getLong("c");
        b.played = t.getLong("pl");
        b.potential = t.getInt("pt");
        return b;
    }
}
