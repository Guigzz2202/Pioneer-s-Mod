package fr.pioneers.society.economy.contract;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
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
 * 1.0.4-beta (lot 7) : registre des contrats (donnee de monde de l'Overworld, {@code pioneers_contracts}) : numeros croissants jamais
 * reutilises, contrats par numero. Les contrats clos et soldes disparaissent {@link ContractRules#KEEP_CLOSED} apres leur fin.
 * <p>
 * 1.0.5-beta (lot 26, onglet Documents) : le registre est PORTE par celui des documents ({@code economy/document/DocumentData},
 * {@code pioneers_documents}) : {@link #get} rend le sien ; les numeros sont ceux des documents (un contrat au temps reel = un document de type
 * contrat au meme numero) ; tout changement salit les documents. L'ancien fichier {@code pioneers_contracts} n'est plus que LU, une fois, pour
 * la migration ({@link #load}, {@link #NAME}) ; il reste sur le disque, intact.
 */
public final class ContractData extends SavedData {

    public static final String NAME = "pioneers_contracts";
    private int next = 1;
    /** 1.0.5-beta (lot 26) : registre des documents qui porte celui-ci (null : fichier d'avant lu pour la migration, ou banc). */
    @Nullable
    private Owner owner;

    /** 1.0.5-beta (lot 26) : ce qui porte le registre (numeros communs, document lie a chaque contrat). */
    public interface Owner {

        int nextId();

        void created(Contract c);

        void removed(int id);

        void dirty();
    }

    /** 1.0.5-beta (lot 26) : rattache le registre a celui des documents. */
    public void attach(Owner owner) {
        this.owner = owner;
    }

    /** 1.0.5-beta (lot 26) : ajoute un contrat deja numerote (migration). */
    public void put(Contract c) {
        contracts.put(c.id, c);
        next = Math.max(next, c.id + 1);
    }

    /** 1.0.5-beta (lot 26) : prochain numero du fichier d'avant (migration). */
    public int nextNumber() {
        return next;
    }

    @Override
    public void setDirty(boolean dirty) {
        super.setDirty(dirty);
        if (dirty && owner != null) {
            owner.dirty();
        }
    }
    private final Map<Integer, Contract> contracts = new LinkedHashMap<>();

    /** 1.0.5-beta (lot 26) : le registre porte par les documents (migre au premier appel). */
    public static ContractData get(MinecraftServer server) {
        return fr.pioneers.society.economy.document.DocumentData.get(server).contracts();
    }

    /** Nouveau contrat (brouillon) au prochain numero (1.0.5-beta lot 26 : numero commun avec les documents, document lie cree). */
    public Contract create() {
        Contract c = new Contract(owner != null ? owner.nextId() : next++);
        next = Math.max(next, c.id + 1);
        contracts.put(c.id, c);
        if (owner != null) {
            owner.created(c);
        }
        setDirty();
        return c;
    }

    @Nullable
    public Contract get(int id) {
        return contracts.get(id);
    }

    public void remove(int id) {
        if (contracts.remove(id) != null) {
            if (owner != null) {
                owner.removed(id);
            }
            setDirty();
        }
    }

    public List<Contract> all() {
        return new ArrayList<>(contracts.values());
    }

    /** Contrats ou le joueur est partie (brouillons : seulement ceux du prestataire), les plus recents d'abord. */
    public List<Contract> of(UUID who) {
        List<Contract> out = new ArrayList<>();
        for (Contract c : contracts.values()) {
            if (who.equals(c.provider) || (who.equals(c.client) && c.state != Contract.State.DRAFT)) {
                out.add(c);
            }
        }
        out.sort(Comparator.comparingInt((Contract c) -> c.id).reversed());
        return out;
    }

    /** Contrats encore ouverts dont le joueur est prestataire. */
    public int openAsProvider(UUID who) {
        int n = 0;
        for (Contract c : contracts.values()) {
            if (who.equals(c.provider) && c.open()) {
                n++;
            }
        }
        return n;
    }

    /** Retire les contrats clos et soldes depuis plus de {@link ContractRules#KEEP_CLOSED}. */
    public void prune(long now) {
        Iterator<Contract> it = contracts.values().iterator();
        while (it.hasNext()) {
            Contract c = it.next();
            long ended = c.stopped > 0 ? c.stopped : c.end;
            if (!c.open() && c.state != Contract.State.DRAFT && now - ended > ContractRules.KEEP_CLOSED) {
                it.remove();
                if (owner != null) {
                    owner.removed(c.id);
                }
                setDirty();
            }
        }
    }

    public static ContractData load(CompoundTag tag) {
        ContractData d = new ContractData();
        d.next = Math.max(1, tag.getInt("next"));
        for (Tag t : tag.getList("c", Tag.TAG_COMPOUND)) {
            Contract c = Contract.load((CompoundTag) t);
            d.contracts.put(c.id, c);
            d.next = Math.max(d.next, c.id + 1);
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("next", next);
        ListTag l = new ListTag();
        for (Contract c : contracts.values()) {
            l.add(c.save());
        }
        tag.put("c", l);
        return tag;
    }
}
