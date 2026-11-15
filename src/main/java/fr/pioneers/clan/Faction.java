package fr.pioneers.clan;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.9 (MAJ RP, bloc 4) : une faction, federation de 2 a 8 clans (Pacte federal) : regime federal, Delegues (les Representants), tresor
 * federal, contribution, zones federales, niveau 1..5, propositions de portee FACTION (meme moteur de votes que les clans), peuple de
 * joueurs apres une secession (identifiant dans le registre des peuples).
 */
public final class Faction {

    public final UUID id;
    public String name = "", tag = "", motto = "";
    public int color;
    public String regime = "council";
    public long founded;
    public final List<UUID> clans = new ArrayList<>();
    @Nullable
    public UUID founder, representative;
    public long treasury;
    /** Contribution federale (% des recettes de chaque clan, plafond du regime federal). */
    public int contribution;
    public long rotateAt;
    public final ListTag proposals = new ListTag();
    public final Map<String, Long> refused = new HashMap<>();
    public final Map<UUID, CompoundTag> treaties = new HashMap<>();
    /** Peuple de joueurs ne de la secession ("" : aucun), declin (fin en ms, 0 = pas de declin). */
    public String people = "";
    public long declineUntil;
    public long doctrineChanged;
    /** Ralliement en cours : village, fin (jour de jeu) ; dernier ralliement (jour de jeu). */
    public long rallyVillage, rallyUntil = -1, lastRally = -1;
    public final ListTag journal = new ListTag();
    public CompoundTag extra = new CompoundTag();

    public Faction(UUID id) {
        this.id = id;
    }

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putUUID("id", id);
        t.putString("name", name);
        t.putString("tag", tag);
        t.putString("motto", motto);
        t.putInt("color", color);
        t.putString("regime", regime);
        t.putLong("founded", founded);
        t.put("clans", Clan.uuids(clans));
        if (founder != null) {
            t.putUUID("founder", founder);
        }
        if (representative != null) {
            t.putUUID("rep", representative);
        }
        t.putLong("treasury", treasury);
        t.putInt("contribution", contribution);
        t.putLong("rotate", rotateAt);
        t.put("proposals", proposals.copy());
        CompoundTag rf = new CompoundTag();
        refused.forEach(rf::putLong);
        t.put("refused", rf);
        ListTag tr = new ListTag();
        treaties.forEach((u, c) -> {
            CompoundTag x = c.copy();
            x.putUUID("with", u);
            tr.add(x);
        });
        t.put("treaties", tr);
        t.putString("people", people);
        t.putLong("decline", declineUntil);
        t.putLong("doctrineChanged", doctrineChanged);
        t.putLong("rallyVillage", rallyVillage);
        t.putLong("rallyUntil", rallyUntil);
        t.putLong("lastRally", lastRally);
        t.put("journal", journal.copy());
        t.put("extra", extra.copy());
        return t;
    }

    public static Faction load(CompoundTag t) {
        Faction f = new Faction(t.getUUID("id"));
        f.name = t.getString("name");
        f.tag = t.getString("tag");
        f.motto = t.getString("motto");
        f.color = t.getInt("color");
        f.regime = t.getString("regime");
        f.founded = t.getLong("founded");
        f.clans.addAll(Clan.readUuids(t.getList("clans", Tag.TAG_INT_ARRAY)));
        f.founder = t.hasUUID("founder") ? t.getUUID("founder") : null;
        f.representative = t.hasUUID("rep") ? t.getUUID("rep") : null;
        f.treasury = Math.max(0, t.getLong("treasury"));
        f.contribution = t.getInt("contribution");
        f.rotateAt = t.getLong("rotate");
        f.proposals.addAll(t.getList("proposals", Tag.TAG_COMPOUND));
        CompoundTag rf = t.getCompound("refused");
        rf.getAllKeys().forEach(k -> f.refused.put(k, rf.getLong(k)));
        for (Tag x : t.getList("treaties", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            f.treaties.put(c.getUUID("with"), c);
        }
        f.people = t.getString("people");
        f.declineUntil = t.getLong("decline");
        f.doctrineChanged = t.getLong("doctrineChanged");
        f.rallyVillage = t.getLong("rallyVillage");
        f.rallyUntil = t.contains("rallyUntil") ? t.getLong("rallyUntil") : -1;
        f.lastRally = t.contains("lastRally") ? t.getLong("lastRally") : -1;
        f.journal.addAll(t.getList("journal", Tag.TAG_COMPOUND));
        f.extra = t.getCompound("extra");
        return f;
    }
}
