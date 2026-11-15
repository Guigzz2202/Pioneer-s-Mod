package fr.pioneers.logistics.transport.logistic;

import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §3.5) : filtre d'un canal (9 entrees) ou d'un point (5 entrees), liste blanche ou noire. Une entree est une cle texte :
 * <ul>
 *     <li>{@code minecraft:iron_ingot} : un objet, un fluide, un gaz ou un type de donnees selon le canal (option « ignorer les NBT » par
 *     entree : {@link Entry#nbt} nul = ignores) ;</li>
 *     <li>{@code #forge:ingots/iron} : un tag (d'objet ou de fluide selon le canal) ;</li>
 *     <li>{@code @pioneers} : tout un mod.</li>
 * </ul>
 * Filtre vide = tout passe. Immuable (une edition remplace le filtre) ; le test est mis en cache par ressource dans le canal
 * ({@link #test}), cache vide a l'edition et au rechargement des tags ({@link #GENERATION}).
 */
public final class Filter {

    public static final int CHANNEL_SIZE = 9, POINT_SIZE = 5;
    public static final Filter EMPTY = new Filter(false, List.of());
    /** Augmente a chaque rechargement des tags : tous les caches de filtres sont perimes. */
    public static volatile int GENERATION;

    /** Une entree : cle et, pour un objet exact compare avec ses NBT, l'etiquette attendue (nulle : NBT ignores). */
    public record Entry(String key, @Nullable CompoundTag nbt) {

        public boolean isTag() {
            return key.startsWith("#");
        }

        public boolean isMod() {
            return key.startsWith("@");
        }

        /** Identifiant sans prefixe ({@code #} ou {@code @}). */
        public String bare() {
            return isTag() || isMod() ? key.substring(1) : key;
        }
    }

    public final boolean blacklist;
    public final List<Entry> entries;
    private final boolean exactNbt;

    public Filter(boolean blacklist, List<Entry> entries) {
        this.blacklist = blacklist;
        this.entries = Collections.unmodifiableList(new ArrayList<>(entries));
        boolean nbt = false;
        for (Entry entry : entries) {
            nbt |= entry.nbt != null;
        }
        exactNbt = nbt;
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Une des entrees compare-t-elle les NBT (le cache par ressource ne suffit plus) ? */
    public boolean exactNbt() {
        return exactNbt;
    }

    /**
     * La ressource passe-t-elle ? Cache par cle de ressource ({@link Access#filterKey}) tant qu'aucune entree ne compare les NBT.
     */
    public <R> boolean test(Access<?, R> access, R resource, @Nullable Object2BooleanOpenHashMap<Object> cache) {
        if (entries.isEmpty()) {
            return true;
        }
        Object key = cache == null || exactNbt ? null : access.filterKey(resource);
        if (key != null) {
            //getOrDefault sans boite : valeur absente = 2 etats possibles, d'ou containsKey
            if (cache.containsKey(key)) {
                return cache.getBoolean(key);
            }
        }
        boolean any = false;
        for (Entry entry : entries) {
            if (access.matches(entry, resource)) {
                any = true;
                break;
            }
        }
        boolean pass = any != blacklist;
        if (key != null) {
            cache.put(key, pass);
        }
        return pass;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        if (blacklist) {
            tag.putBoolean("b", true);
        }
        ListTag list = new ListTag();
        for (Entry entry : entries) {
            CompoundTag e = new CompoundTag();
            e.putString("k", entry.key);
            if (entry.nbt != null) {
                e.put("n", entry.nbt.copy());
            }
            list.add(e);
        }
        tag.put("e", list);
        return tag;
    }

    public static Filter load(CompoundTag tag, int max) {
        if (tag.isEmpty()) {
            return EMPTY;
        }
        List<Entry> entries = new ArrayList<>();
        ListTag list = tag.getList("e", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size() && entries.size() < max; i++) {
            CompoundTag e = list.getCompound(i);
            String key = e.getString("k");
            if (!key.isEmpty() && key.length() <= 128) {
                entries.add(new Entry(key, e.contains("n", Tag.TAG_COMPOUND) ? e.getCompound("n") : null));
            }
        }
        boolean black = tag.getBoolean("b");
        return entries.isEmpty() && !black ? EMPTY : new Filter(black, entries);
    }

    /** Memes reglages (groupement des machines identiques dans le diagramme). */
    public boolean same(Filter other) {
        return blacklist == other.blacklist && entries.equals(other.entries);
    }
}
