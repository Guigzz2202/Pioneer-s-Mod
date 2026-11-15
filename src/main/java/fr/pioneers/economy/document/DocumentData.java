package fr.pioneers.economy.document;

import fr.pioneers.economy.contract.Contract;
import fr.pioneers.economy.contract.ContractData;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Predicate;
import javax.annotation.Nullable;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.level.storage.DimensionDataStorage;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 26) : registre des DOCUMENTS (donnee de monde de l'Overworld, {@code pioneers_documents}) : numeros croissants jamais
 * reutilises, documents par numero, et le registre des contrats au temps reel ({@link ContractData}, porte ici, numeros communs : un contrat
 * au temps reel est le document de type contrat de meme numero, {@link Document#timed}).
 * <p>
 * MIGRATION (une fois par monde, {@link #migrate}) : au premier appel, l'ancien fichier {@code pioneers_contracts} (s'il existe) est lu ; ses
 * contrats passent ici tels quels et chacun recoit son document ({@link DocumentMigration#fromContract}) ; le prochain numero reprend le
 * sien. L'ancien fichier reste sur le disque, intact. Les documents clos disparaissent {@link DocumentRules#KEEP_DAYS} jours de jeu apres
 * leur cloture.
 */
public final class DocumentData extends net.minecraft.world.level.saveddata.SavedData implements ContractData.Owner {

    public static final String NAME = "pioneers_documents";
    private int next = 1;
    /** Vrai une fois l'ancien registre des contrats repris (ou constate absent). */
    private boolean migrated;
    private final Map<Integer, Document> docs = new LinkedHashMap<>();
    private ContractData contracts = new ContractData();

    public DocumentData() {
        contracts.attach(this);
    }

    public static DocumentData get(MinecraftServer server) {
        DimensionDataStorage st = server.overworld().getDataStorage();
        DocumentData d = st.computeIfAbsent(DocumentData::load, DocumentData::new, NAME);
        if (!d.migrated) {
            d.migrate(st.get(ContractData::load, ContractData.NAME));
        }
        return d;
    }

    /** Reprend l'ancien registre des contrats ({@code null} : il n'y en avait pas). Sans effet la deuxieme fois. */
    public void migrate(@Nullable ContractData legacy) {
        if (migrated) {
            return;
        }
        migrated = true;
        if (legacy != null) {
            //copie par le NBT : l'objet d'avant, en cache dans le stockage, n'est jamais modifie ni resauve
            ContractData copy = ContractData.load(legacy.save(new CompoundTag()));
            for (Contract c : copy.all()) {
                contracts.put(c);
                next = Math.max(next, c.id + 1);
                if (!docs.containsKey(c.id)) {
                    docs.put(c.id, DocumentMigration.fromContract(c));
                }
            }
            next = Math.max(next, copy.nextNumber());
        }
        setDirty();
    }

    public ContractData contracts() {
        return contracts;
    }

    public boolean migrated() {
        return migrated;
    }

    // ------------------------------------------------------------------ ContractData.Owner
    @Override
    public int nextId() {
        setDirty();
        return next++;
    }

    @Override
    public void created(Contract c) {
        Document d = DocumentMigration.fromContract(c);
        d.history.clear();
        docs.put(c.id, d);
        setDirty();
    }

    @Override
    public void removed(int id) {
        Document d = docs.get(id);
        if (d != null && d.timed == id) {
            docs.remove(id);
            setDirty();
        }
    }

    @Override
    public void dirty() {
        setDirty();
    }

    // ------------------------------------------------------------------ documents
    /** Nouveau document (brouillon) au prochain numero. */
    public Document create() {
        Document d = new Document(nextId());
        docs.put(d.id, d);
        setDirty();
        return d;
    }

    @Nullable
    public Document get(int id) {
        Document d = docs.get(id);
        if (d != null && d.timed > 0) {
            Contract c = contracts.get(d.timed);
            if (c != null) {
                DocumentMigration.sync(d, c);
            }
        }
        return d;
    }

    public void remove(int id) {
        if (docs.remove(id) != null) {
            setDirty();
        }
    }

    public List<Document> all() {
        return new ArrayList<>(docs.values());
    }

    /** Documents vus par un joueur ({@code sees}), les plus recents d'abord ; ceux des contrats au temps reel suivent leur contrat. */
    public List<Document> visible(Predicate<Document> sees) {
        List<Document> out = new ArrayList<>();
        for (Document d : docs.values()) {
            if (d.timed > 0) {
                Contract c = contracts.get(d.timed);
                if (c != null) {
                    DocumentMigration.sync(d, c);
                }
            }
            if (sees.test(d)) {
                out.add(d);
            }
        }
        out.sort(Comparator.comparingInt((Document d) -> d.id).reversed());
        return out;
    }

    /** Documents encore ouverts dont le joueur est l'auteur (contrats au temps reel compris). */
    public int openAsAuthor(UUID who) {
        int n = 0;
        for (Document d : docs.values()) {
            if (d.author.is(who) && !d.status.closed()) {
                n++;
            }
        }
        return n;
    }

    /** Retire les documents clos depuis plus de {@link DocumentRules#KEEP_DAYS} jours de jeu (ceux des contrats au temps reel : avec leur contrat). */
    public void prune(long today) {
        Iterator<Document> it = docs.values().iterator();
        while (it.hasNext()) {
            Document d = it.next();
            if (d.timed == 0 && d.status.closed() && d.closed >= 0 && today - d.closed > DocumentRules.KEEP_DAYS) {
                it.remove();
                setDirty();
            }
        }
    }

    // ------------------------------------------------------------------ NBT
    public static DocumentData load(CompoundTag tag) {
        DocumentData d = new DocumentData();
        d.next = Math.max(1, tag.getInt("next"));
        d.migrated = tag.getBoolean("migrated");
        for (Tag t : tag.getList("docs", Tag.TAG_COMPOUND)) {
            Document doc = Document.load((CompoundTag) t);
            d.docs.put(doc.id, doc);
            d.next = Math.max(d.next, doc.id + 1);
        }
        d.contracts = ContractData.load(tag.getCompound("contracts"));
        d.contracts.attach(d);
        for (Contract c : d.contracts.all()) {
            d.next = Math.max(d.next, c.id + 1);
            if (!d.docs.containsKey(c.id)) {
                d.docs.put(c.id, DocumentMigration.fromContract(c));
            }
        }
        return d;
    }

    @Override
    public @NotNull CompoundTag save(@NotNull CompoundTag tag) {
        tag.putInt("next", next);
        tag.putBoolean("migrated", migrated);
        ListTag l = new ListTag();
        for (Document d : docs.values()) {
            l.add(d.save());
        }
        tag.put("docs", l);
        tag.put("contracts", contracts.save(new CompoundTag()));
        return tag;
    }
}
