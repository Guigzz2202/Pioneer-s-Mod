package fr.pioneers.world.fauna;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 4) : memoire COMPACTE de la faune d'un monde (Overworld, fichier {@code data/pioneers_fauna.dat}) :
 * <ul>
 *     <li>par zone vue : un long = 5 densites de depart sur 12 bits + drapeaux ({@link FaunaRules#packZone} ; {@link FaunaRules#FLAG_LEGACY} :
 *     zone d'un monde existant, densite estimee) ;</li>
 *     <li>par zone pas encore vue : nombre de troncons generes depuis la 1.0.5 (zone neuve si au moins la moitie) ;</li>
 *     <li>heure du prochain passage du repeuplement.</li>
 * </ul>
 * Rien n'est ecrit pour une zone que personne n'a encore entierement chargee.
 */
public class FaunaData extends SavedData {

    public static final String NAME = "pioneers_fauna";
    private final Long2LongOpenHashMap zones = new Long2LongOpenHashMap();
    private final Long2IntOpenHashMap fresh = new Long2IntOpenHashMap();
    private long nextPass = Long.MIN_VALUE;

    public static FaunaData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FaunaData::load, FaunaData::new, NAME);
    }

    public boolean known(long zone) {
        return zones.containsKey(zone);
    }

    /** Densites de depart de la zone (null : pas encore vue). */
    @Nullable
    public int[] starts(long zone) {
        return zones.containsKey(zone) ? FaunaRules.starts(zones.get(zone)) : null;
    }

    public boolean legacy(long zone) {
        return zones.containsKey(zone) && (FaunaRules.flags(zones.get(zone)) & FaunaRules.FLAG_LEGACY) != 0;
    }

    public void put(long zone, int[] starts, boolean legacy) {
        zones.put(zone, FaunaRules.packZone(starts, legacy ? FaunaRules.FLAG_LEGACY : 0));
        fresh.remove(zone);
        setDirty();
    }

    /** Oublie la zone (commande de diagnostic) : elle sera estimee a nouveau au prochain passage, comme une zone deja exploree. */
    public void forget(long zone) {
        boolean had = zones.containsKey(zone) || fresh.containsKey(zone);
        zones.remove(zone);
        fresh.remove(zone);
        if (had) {
            setDirty();
        }
    }

    /** Un troncon de la zone vient d'etre genere (peuple a la generation). */
    public void freshChunk(long zone) {
        if (!zones.containsKey(zone)) {
            fresh.addTo(zone, 1);
            setDirty();
        }
    }

    public int fresh(long zone) {
        return fresh.get(zone);
    }

    public int zoneCount() {
        return zones.size();
    }

    public long nextPass() {
        return nextPass;
    }

    public void setNextPass(long t) {
        if (t != nextPass) {
            nextPass = t;
            setDirty();
        }
    }

    private static FaunaData load(CompoundTag tag) {
        FaunaData d = new FaunaData();
        long[] k = tag.getLongArray("zones"), v = tag.getLongArray("starts");
        for (int i = 0; i < Math.min(k.length, v.length); i++) {
            d.zones.put(k[i], v[i]);
        }
        long[] fk = tag.getLongArray("fresh");
        int[] fv = tag.getIntArray("fresh_n");
        for (int i = 0; i < Math.min(fk.length, fv.length); i++) {
            d.fresh.put(fk[i], fv[i]);
        }
        d.nextPass = tag.contains("next") ? tag.getLong("next") : Long.MIN_VALUE;
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        long[] k = zones.keySet().toLongArray(), v = new long[k.length];
        for (int i = 0; i < k.length; i++) {
            v[i] = zones.get(k[i]);
        }
        tag.putLongArray("zones", k);
        tag.putLongArray("starts", v);
        long[] fk = fresh.keySet().toLongArray();
        int[] fv = new int[fk.length];
        for (int i = 0; i < fk.length; i++) {
            fv[i] = fresh.get(fk[i]);
        }
        tag.putLongArray("fresh", fk);
        tag.putIntArray("fresh_n", fv);
        tag.putLong("next", nextPass);
        tag.putInt("version", 1);
        return tag;
    }
}
