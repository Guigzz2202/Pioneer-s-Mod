package fr.pioneers.society.clan.faction;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 4) : donnee de monde propre aux factions et aux peuples de joueurs (Overworld), a cote de {@code ClanData} : Pactes
 * federaux ouverts (le Pacte en main ne porte que son identifiant : les signatures votees arrivent meme quand le Pacte est range), noms de
 * peuples dissous encore reserves (30 jours), schismes a appliquer a la prochaine connexion des fondateurs hors ligne, demandes d'entree.
 */
public final class FactionSaved extends SavedData {

    public static final String NAME = "pioneers_factions";

    /** Pactes ouverts : identifiant -&gt; brouillon (nom, etiquette, couleur, devise, regime, clan fondateur, ouverture, signataires). */
    public final Map<UUID, CompoundTag> pacts = new LinkedHashMap<>();
    /** Noms de peuples liberes plus tard : nom replie -&gt; fin de reserve (ms). */
    public final Map<String, Long> reserved = new HashMap<>();
    /** Joueurs qui doivent encore payer le prix du schisme (hors ligne a la secession) : joueur -&gt; peuple. */
    public final Map<UUID, String> schisms = new HashMap<>();
    /** Joueurs deja frappes par un schisme (une seule fois, meme s'ils changent de peuple). */
    public final Set<UUID> schismDone = new LinkedHashSet<>();

    public static FactionSaved get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(FactionSaved::load, FactionSaved::new, NAME);
    }

    @Nullable
    public CompoundTag pact(@Nullable UUID id) {
        return id == null ? null : pacts.get(id);
    }

    public void changed() {
        setDirty();
    }

    private static FactionSaved load(CompoundTag tag) {
        FactionSaved d = new FactionSaved();
        for (Tag t : tag.getList("pacts", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            if (c.hasUUID("id")) {
                d.pacts.put(c.getUUID("id"), c);
            }
        }
        CompoundTag r = tag.getCompound("reserved");
        r.getAllKeys().forEach(k -> d.reserved.put(k, r.getLong(k)));
        for (Tag t : tag.getList("schisms", Tag.TAG_COMPOUND)) {
            CompoundTag c = (CompoundTag) t;
            d.schisms.put(c.getUUID("u"), c.getString("p"));
        }
        for (Tag t : tag.getList("schismDone", Tag.TAG_INT_ARRAY)) {
            d.schismDone.add(NbtUtils.loadUUID(t));
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        ListTag p = new ListTag();
        pacts.values().forEach(c -> p.add(c.copy()));
        tag.put("pacts", p);
        CompoundTag r = new CompoundTag();
        reserved.forEach(r::putLong);
        tag.put("reserved", r);
        ListTag s = new ListTag();
        schisms.forEach((u, id) -> {
            CompoundTag c = new CompoundTag();
            c.putUUID("u", u);
            c.putString("p", id);
            s.add(c);
        });
        tag.put("schisms", s);
        ListTag done = new ListTag();
        schismDone.forEach(u -> done.add(NbtUtils.createUUID(u)));
        tag.put("schismDone", done);
        return tag;
    }
}
