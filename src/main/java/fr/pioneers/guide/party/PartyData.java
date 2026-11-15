package fr.pioneers.guide.party;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 29) : les PARTIES du guide (donnee de monde de l'Overworld, {@code pioneers_parties}) : membres (UUID et nom), etapes du
 * Periple partagees (ids de progres {@code pioneers:journey/*} : l'union de tout ce que les membres ont fait depuis qu'ils sont ensemble), et
 * les invitations en cours (une par joueur invite). Aucun lien avec les clans.
 */
public final class PartyData extends SavedData {

    public static final String NAME = "pioneers_parties";
    private int next = 1;
    private final Map<Integer, Party> parties = new LinkedHashMap<>();
    private final Map<UUID, Invite> invites = new HashMap<>();

    /** Une partie : membres dans l'ordre d'arrivee, etapes partagees. */
    public static final class Party {

        public final int id;
        public final LinkedHashMap<UUID, String> members = new LinkedHashMap<>();
        public final LinkedHashSet<String> steps = new LinkedHashSet<>();
        public long created;

        Party(int id) {
            this.id = id;
        }
    }

    /** Invitation : qui invite (nom affiche), quand (ms reelles). */
    public record Invite(UUID from, String fromName, long at) {
    }

    public static PartyData get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(PartyData::load, PartyData::new, NAME);
    }

    @Nullable
    public Party partyOf(UUID player) {
        for (Party p : parties.values()) {
            if (p.members.containsKey(player)) {
                return p;
            }
        }
        return null;
    }

    public Party create(long now) {
        Party p = new Party(next++);
        p.created = now;
        parties.put(p.id, p);
        setDirty();
        return p;
    }

    /** Ajoute un membre (et ses etapes : union). */
    public void join(Party p, UUID who, String name, Set<String> steps) {
        p.members.put(who, name);
        p.steps.addAll(steps);
        setDirty();
    }

    /** Retire un membre ; la partie est dissoute quand il en reste un seul (ou aucun). Rend vrai si elle est dissoute. */
    public boolean leave(UUID who) {
        Party p = partyOf(who);
        if (p == null) {
            return false;
        }
        p.members.remove(who);
        setDirty();
        if (p.members.size() <= 1) {
            parties.remove(p.id);
            return true;
        }
        return false;
    }

    @Nullable
    public Invite inviteOf(UUID invitee) {
        return invites.get(invitee);
    }

    public void invite(UUID invitee, Invite inv) {
        invites.put(invitee, inv);
        setDirty();
    }

    @Nullable
    public Invite takeInvite(UUID invitee) {
        Invite i = invites.remove(invitee);
        if (i != null) {
            setDirty();
        }
        return i;
    }

    public java.util.Collection<Party> parties() {
        return parties.values();
    }

    /** Retire une partie entiere (commande d'administration ; GameTests). */
    public void remove(Party p) {
        parties.remove(p.id);
        setDirty();
    }

    // ------------------------------------------------------------------ NBT
    public static PartyData load(CompoundTag tag) {
        PartyData d = new PartyData();
        d.next = Math.max(1, tag.getInt("next"));
        for (Tag x : tag.getList("parties", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            Party p = new Party(c.getInt("id"));
            p.created = c.getLong("created");
            for (Tag y : c.getList("members", Tag.TAG_COMPOUND)) {
                CompoundTag m = (CompoundTag) y;
                if (m.hasUUID("u")) {
                    p.members.put(m.getUUID("u"), m.getString("n"));
                }
            }
            for (Tag y : c.getList("steps", Tag.TAG_STRING)) {
                p.steps.add(y.getAsString());
            }
            if (p.members.size() > 1) {
                d.parties.put(p.id, p);
                d.next = Math.max(d.next, p.id + 1);
            }
        }
        for (Tag x : tag.getList("invites", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) x;
            if (c.hasUUID("to") && c.hasUUID("from")) {
                d.invites.put(c.getUUID("to"), new Invite(c.getUUID("from"), c.getString("n"), c.getLong("at")));
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("next", next);
        ListTag list = new ListTag();
        for (Party p : parties.values()) {
            CompoundTag c = new CompoundTag();
            c.putInt("id", p.id);
            c.putLong("created", p.created);
            ListTag ms = new ListTag();
            p.members.forEach((u, n) -> {
                CompoundTag m = new CompoundTag();
                m.putUUID("u", u);
                m.putString("n", n);
                ms.add(m);
            });
            c.put("members", ms);
            ListTag st = new ListTag();
            for (String s : p.steps) {
                st.add(StringTag.valueOf(s));
            }
            c.put("steps", st);
            list.add(c);
        }
        tag.put("parties", list);
        ListTag inv = new ListTag();
        invites.forEach((to, i) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("to", to);
            c.putUUID("from", i.from());
            c.putString("n", i.fromName());
            c.putLong("at", i.at());
            inv.add(c);
        });
        tag.put("invites", inv);
        return tag;
    }
}
