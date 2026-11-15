package fr.pioneers.transport.logistic;

import it.unimi.dsi.fastutil.longs.Long2IntMap;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 45, retour 66 de Guillaume : « Le réseau de câble logistique ne doit pas avoir de limite de distance », réponse « Sites +
 * pause ») : TOPOLOGIE des câbles logistiques d'une dimension, gardée côté serveur (sauvée par {@link LogisticNetworks} dans un SavedData),
 * pour que le parcours d'un réseau ({@link NetworkGraph#build}) traverse les chunks NON CHARGÉS sans jamais les lire ni les charger.
 * <ul>
 *     <li>un ENREGISTREMENT par câble (position {@code BlockPos.asLong}) : niveau | transparent ({@link NetworkGraph#CLEAR}), côtés reliés à un
 *     câble, côtés à fiche, côtés DÉCONNECTÉS (retour 63 : fiche retirée parce que la machine est réglée sur « Rien » partout ; la face reste une
 *     fiche LATENTE du réseau) ;</li>
 *     <li>une FICHE par machine raccordée (clé du raccord) : objet de son bloc, façade, types offerts, groupe (contrôleur, bloc principal) ;
 *     sert au diagramme et à la reconstruction quand la machine est dans un chunk non chargé.</li>
 * </ul>
 * Tenu à jour à chaque pose, changement d'état et casse d'un câble (toujours dans un chunk chargé) et au chargement de chaque chunk (relevé
 * complet des câbles du chunk : {@link #replaceChunk}). Pur (NBT et fastutil seulement) : le banc hors jeu l'exécute tel quel.
 */
public final class CableIndex {

    /** Champs d'un enregistrement (entier) : info 5 bits, côtés câble 6, fiches 6, déconnectés 6. */
    static final int INFO_BITS = 0x1F, SIDES_SHIFT = 5, PLUGS_SHIFT = 11, DETACHED_SHIFT = 17;
    /** Pas d'enregistrement. */
    public static final int NONE = -1;

    /** Fiche d'une machine raccordée. */
    public record Machine(String item, int facing, int kinds, long group) {
    }

    private final Long2IntOpenHashMap cables = new Long2IntOpenHashMap();
    private final Long2ObjectOpenHashMap<LongOpenHashSet> byChunk = new Long2ObjectOpenHashMap<>();
    private final Long2ObjectOpenHashMap<Machine> machines = new Long2ObjectOpenHashMap<>();
    /** Appele a chaque changement (SavedData a sauver). */
    @Nullable
    private Runnable dirty;
    private long changes;

    public CableIndex() {
        cables.defaultReturnValue(NONE);
    }

    public void onChange(@Nullable Runnable dirty) {
        this.dirty = dirty;
    }

    private void changed() {
        changes++;
        if (dirty != null) {
            dirty.run();
        }
    }

    /** Nombre de changements depuis la creation (banc). */
    public long changes() {
        return changes;
    }

    public int size() {
        return cables.size();
    }

    public int machineCount() {
        return machines.size();
    }

    // ------------------------------------------------------------------
    // Enregistrements
    // ------------------------------------------------------------------

    public static int pack(int info, int sides, int plugs, int detached) {
        return info & INFO_BITS | (sides & 63) << SIDES_SHIFT | (plugs & 63) << PLUGS_SHIFT | (detached & 63) << DETACHED_SHIFT;
    }

    /** Enregistrement d'un cable ({@link #NONE} s'il n'est pas connu). */
    public int record(long pos) {
        return cables.get(pos);
    }

    public boolean contains(long pos) {
        return cables.containsKey(pos);
    }

    /** -1 si inconnu ; sinon niveau | {@link NetworkGraph#CLEAR} (comme {@link NetworkGraph.Reader#info}). */
    public int info(long pos) {
        int r = cables.get(pos);
        return r == NONE ? -1 : r & INFO_BITS;
    }

    public int cableSides(long pos) {
        int r = cables.get(pos);
        return r == NONE ? 0 : r >> SIDES_SHIFT & 63;
    }

    public int plugSides(long pos) {
        int r = cables.get(pos);
        return r == NONE ? 0 : r >> PLUGS_SHIFT & 63;
    }

    /** Retour 63 : cotes dont la fiche est retiree (machine reglee sur « Rien » partout) : fiches LATENTES du reseau. */
    public int detached(long pos) {
        int r = cables.get(pos);
        return r == NONE ? 0 : r >> DETACHED_SHIFT & 63;
    }

    /** Pose ou changement d'etat : niveau, liaisons, fiches (les faces deconnectees sont gardees). */
    public void put(long pos, int info, int sides, int plugs) {
        int before = cables.get(pos);
        int det = before == NONE ? 0 : before >> DETACHED_SHIFT & 63;
        int r = pack(info, sides, plugs, det);
        if (r != before) {
            cables.put(pos, r);
            if (before == NONE) {
                byChunk.computeIfAbsent(NetworkGraph.chunk(pos), k -> new LongOpenHashSet()).add(pos);
            }
            changed();
        }
    }

    /** Retour 63 : faces deconnectees d'un cable connu (sans effet si le cable n'est pas connu). */
    public void setDetached(long pos, int mask) {
        int before = cables.get(pos);
        if (before == NONE) {
            return;
        }
        int r = before & ~(63 << DETACHED_SHIFT) | (mask & 63) << DETACHED_SHIFT;
        if (r != before) {
            cables.put(pos, r);
            changed();
        }
    }

    public void remove(long pos) {
        if (cables.remove(pos) != NONE) {
            long chunk = NetworkGraph.chunk(pos);
            LongOpenHashSet set = byChunk.get(chunk);
            if (set != null) {
                set.remove(pos);
                if (set.isEmpty()) {
                    byChunk.remove(chunk);
                }
            }
            changed();
        }
    }

    /** Cables connus d'un chunk ({@code NetworkGraph.chunk}) ; vide si aucun. */
    public long[] cablesIn(long chunk) {
        LongOpenHashSet set = byChunk.get(chunk);
        return set == null ? new long[0] : set.toLongArray();
    }

    /**
     * Releve complet d'un chunk qui vient d'etre charge : ses enregistrements deviennent {@code found} (position -> enregistrement complet,
     * faces deconnectees comprises, lues dans les tiles) ; ceux qui n'y sont plus sont retires.
     *
     * @return les positions dont l'enregistrement a change (nouvelles, retirees, modifiees)
     */
    public long[] replaceChunk(long chunk, Long2IntMap found) {
        it.unimi.dsi.fastutil.longs.LongArrayList changedAt = new it.unimi.dsi.fastutil.longs.LongArrayList();
        LongOpenHashSet set = byChunk.get(chunk);
        if (set != null) {
            for (LongIterator it = set.iterator(); it.hasNext(); ) {
                long pos = it.nextLong();
                if (!found.containsKey(pos)) {
                    cables.remove(pos);
                    it.remove();
                    changedAt.add(pos);
                }
            }
        }
        for (Long2IntMap.Entry e : found.long2IntEntrySet()) {
            int before = cables.get(e.getLongKey());
            if (before != e.getIntValue()) {
                cables.put(e.getLongKey(), e.getIntValue());
                changedAt.add(e.getLongKey());
            }
        }
        if (!found.isEmpty()) {
            LongOpenHashSet into = byChunk.computeIfAbsent(chunk, k -> new LongOpenHashSet());
            for (long pos : found.keySet()) {
                into.add(pos);
            }
        } else if (set != null && set.isEmpty()) {
            byChunk.remove(chunk);
        }
        if (!changedAt.isEmpty()) {
            changed();
        }
        return changedAt.toLongArray();
    }

    // ------------------------------------------------------------------
    // Fiches des machines
    // ------------------------------------------------------------------

    @Nullable
    public Machine machine(long key) {
        return machines.get(key);
    }

    public void putMachine(long key, Machine m) {
        Machine before = machines.put(key, m);
        if (!m.equals(before)) {
            changed();
        }
    }

    /** Une fiche (ou une fiche latente) d'un cable connu touche-t-elle encore cette machine ? */
    boolean touched(long key) {
        for (Direction d : Direction.values()) {
            long cable = BlockPos.offset(key, d);
            int r = cables.get(cable);
            if (r != NONE && ((r >> PLUGS_SHIFT | r >> DETACHED_SHIFT) >> d.getOpposite().ordinal() & 1) != 0) {
                return true;
            }
        }
        return false;
    }

    /** Retire les fiches des machines qu'aucune fiche ne touche plus (avant la sauvegarde). */
    public int pruneMachines() {
        int before = machines.size();
        machines.long2ObjectEntrySet().removeIf(e -> !touched(e.getLongKey()));
        return before - machines.size();
    }

    // ------------------------------------------------------------------
    // NBT (SavedData)
    // ------------------------------------------------------------------

    public CompoundTag save(CompoundTag tag) {
        pruneMachines();
        long[] pos = new long[cables.size()];
        int[] rec = new int[cables.size()];
        int i = 0;
        for (Long2IntMap.Entry e : cables.long2IntEntrySet()) {
            pos[i] = e.getLongKey();
            rec[i] = e.getIntValue();
            i++;
        }
        tag.putInt("v", 1);
        tag.putLongArray("p", pos);
        tag.putIntArray("r", rec);
        ListTag ms = new ListTag();
        for (Long2ObjectMap.Entry<Machine> e : machines.long2ObjectEntrySet()) {
            CompoundTag m = new CompoundTag();
            m.putLong("k", e.getLongKey());
            m.putString("i", e.getValue().item());
            m.putByte("f", (byte) e.getValue().facing());
            m.putByte("t", (byte) e.getValue().kinds());
            if (e.getValue().group() != e.getLongKey()) {
                m.putLong("g", e.getValue().group());
            }
            ms.add(m);
        }
        tag.put("m", ms);
        return tag;
    }

    public static CableIndex load(CompoundTag tag) {
        CableIndex index = new CableIndex();
        long[] pos = tag.getLongArray("p");
        int[] rec = tag.getIntArray("r");
        for (int i = 0; i < pos.length && i < rec.length; i++) {
            if (rec[i] == NONE) {
                continue;
            }
            index.cables.put(pos[i], rec[i]);
            index.byChunk.computeIfAbsent(NetworkGraph.chunk(pos[i]), k -> new LongOpenHashSet()).add(pos[i]);
        }
        ListTag ms = tag.getList("m", Tag.TAG_COMPOUND);
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag m = ms.getCompound(i);
            long k = m.getLong("k");
            index.machines.put(k, new Machine(m.getString("i"), Math.floorMod(m.getByte("f"), 6), m.getByte("t") & 0x7F,
                  m.contains("g") ? m.getLong("g") : k));
        }
        return index;
    }
}
