package fr.pioneers.clan.vote;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;

/**
 * 1.9 (MAJ RP, bloc 4) : une proposition, rangee telle quelle (NBT) dans {@code Clan.proposals} ou {@code Faction.proposals}. Corps electoral
 * fige a l'ouverture (electeur, groupe = clan de l'electeur, poids). Vote PUBLIC : {@code ballots} electeur -&gt; choix. Vote SECRET
 * (elections) : {@code voters} (qui a vote, sans son choix) et {@code tally} (choix -&gt; voix) separes : rien ne relie un electeur a son
 * bulletin. Choix : « yes », « no », « abstain », ou l'identifiant d'un candidat.
 */
public final class Proposal {

    public static final String OPEN = "open", PASSED = "passed", REJECTED = "rejected", CANCELLED = "cancelled", FAILED = "failed";

    public final CompoundTag tag;

    public Proposal(CompoundTag tag) {
        this.tag = tag;
    }

    public String id() {
        return tag.getString("id");
    }

    public String type() {
        return tag.getString("type");
    }

    public ClanVotes.Scope scope() {
        return "faction".equals(tag.getString("scope")) ? ClanVotes.Scope.FACTION : ClanVotes.Scope.CLAN;
    }

    public String status() {
        return tag.getString("status");
    }

    public boolean open() {
        return OPEN.equals(status());
    }

    public long closes() {
        return tag.getLong("closes");
    }

    public long opened() {
        return tag.getLong("opened");
    }

    public boolean secret() {
        return tag.getBoolean("secret");
    }

    public boolean election() {
        return tag.getBoolean("election");
    }

    public int seats() {
        return Math.max(1, tag.getInt("seats"));
    }

    public CompoundTag data() {
        return tag.getCompound("data");
    }

    public String text() {
        return tag.getString("text");
    }

    @Nullable
    public UUID author() {
        return tag.hasUUID("author") ? tag.getUUID("author") : null;
    }

    /** Corps electoral : electeur -&gt; poids. */
    public Map<UUID, Double> electorate() {
        Map<UUID, Double> out = new LinkedHashMap<>();
        for (Tag t : tag.getList("elect", Tag.TAG_COMPOUND)) {
            CompoundTag e = (CompoundTag) t;
            out.put(e.getUUID("u"), e.contains("w") ? e.getDouble("w") : 1.0);
        }
        return out;
    }

    /** Candidats d'une election, dans l'ordre d'anciennete (departage des egalites). */
    public List<UUID> candidates() {
        List<UUID> out = new ArrayList<>();
        for (Tag t : tag.getList("cands", Tag.TAG_INT_ARRAY)) {
            out.add(NbtUtils.loadUUID(t));
        }
        return out;
    }

    public boolean voted(UUID voter) {
        if (secret()) {
            for (Tag t : tag.getList("voters", Tag.TAG_INT_ARRAY)) {
                if (NbtUtils.loadUUID(t).equals(voter)) {
                    return true;
                }
            }
            return false;
        }
        return tag.getCompound("ballots").contains(voter.toString());
    }

    /** Depose un bulletin (deja verifie : electeur, pas encore vote, choix valide). */
    public void cast(UUID voter, String choice, double weight) {
        if (secret()) {
            ListTag v = tag.getList("voters", Tag.TAG_INT_ARRAY);
            v.add(NbtUtils.createUUID(voter));
            tag.put("voters", v);
            CompoundTag t = tag.getCompound("tally");
            t.putDouble(choice, t.getDouble(choice) + weight);
            tag.put("tally", t);
            tag.putInt("count", tag.getInt("count") + 1);
        } else {
            CompoundTag b = tag.getCompound("ballots");
            b.putString(voter.toString(), choice);
            tag.put("ballots", b);
        }
    }

    /** Totaux ponderes par choix. */
    public Map<String, Double> tally() {
        Map<String, Double> out = new LinkedHashMap<>();
        if (secret()) {
            CompoundTag t = tag.getCompound("tally");
            for (String k : t.getAllKeys()) {
                out.put(k, t.getDouble(k));
            }
            return out;
        }
        Map<UUID, Double> w = electorate();
        CompoundTag b = tag.getCompound("ballots");
        for (String k : b.getAllKeys()) {
            double weight = w.getOrDefault(UUID.fromString(k), 1.0);
            out.merge(b.getString(k), weight, Double::sum);
        }
        return out;
    }

    /** Nombre de bulletins deposes. */
    public int ballots() {
        return secret() ? tag.getInt("count") : tag.getCompound("ballots").size();
    }

    /** Voix ponderees de ceux qui n'ont pas encore vote. */
    public double remaining() {
        double r = 0;
        for (Map.Entry<UUID, Double> e : electorate().entrySet()) {
            if (!voted(e.getKey())) {
                r += e.getValue();
            }
        }
        return r;
    }

    public void close(String status, long now) {
        tag.putString("status", status);
        tag.putLong("closed", now);
    }
}
