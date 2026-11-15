package fr.pioneers.transport.logistic;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.LongPredicate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §3.2) : configuration d'un reseau logistique : canaux (vivants + tombes), drapeau {@code auto}, points par raccord, disposition
 * du diagramme. Une copie vit dans chaque tile de cable a fiche (sauvee avec les seuls points des raccords que la tile touche) : un morceau
 * de reseau recharge plus tard se remet a jour par {@link #merge} (canaux reunis par uid, la plus grande revision gagne, une tombe plus
 * recente supprime ; points d'un meme raccord : la plus grande revision gagne).
 * <p>
 * Pure (sans Minecraft hors NBT) : testee par le banc hors jeu.
 */
public final class NetworkConfig {

    /** Canaux vivants actifs au plus ; au-dela, les plus anciens attendent (spec §2, debordement). */
    public static final int MAX_CHANNELS = 16;
    public static final int MAX_TOMBS = 32;

    public final List<ChannelDef> channels = new ArrayList<>();
    /** Reseau jamais edite : canaux implicites (comportement des anciens cables). */
    public boolean auto = true;
    /** Revision du drapeau auto (la plus grande gagne). */
    public long autoRev;
    /** Points : cle du raccord -> uid du canal -> reglages. */
    public final Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<PointConfig>> points = new Long2ObjectOpenHashMap<>();
    /** Disposition du diagramme : identifiant de noeud -> (x << 32 | y) ; partagee, la plus recente gagne en bloc. */
    public final Long2LongOpenHashMap layout = new Long2LongOpenHashMap();
    public long layoutRev;
    /** Corps des canaux automatiques (temperature, pression) par type : gardes d'une reconstruction a l'autre (NaN : jamais mesure). */
    public final double[] autoBody = new double[ChannelKind.COUNT];

    public NetworkConfig() {
        java.util.Arrays.fill(autoBody, Double.NaN);
    }

    public NetworkConfig copy() {
        NetworkConfig c = new NetworkConfig();
        for (ChannelDef def : channels) {
            c.channels.add(def.copy());
        }
        c.auto = auto;
        c.autoRev = autoRev;
        for (Long2ObjectMap.Entry<Int2ObjectOpenHashMap<PointConfig>> e : points.long2ObjectEntrySet()) {
            Int2ObjectOpenHashMap<PointConfig> map = new Int2ObjectOpenHashMap<>();
            for (Int2ObjectMap.Entry<PointConfig> p : e.getValue().int2ObjectEntrySet()) {
                map.put(p.getIntKey(), p.getValue().copy());
            }
            c.points.put(e.getLongKey(), map);
        }
        c.layout.putAll(layout);
        c.layoutRev = layoutRev;
        System.arraycopy(autoBody, 0, c.autoBody, 0, autoBody.length);
        return c;
    }

    @Nullable
    public ChannelDef channel(int uid) {
        for (ChannelDef def : channels) {
            if (def.uid == uid) {
                return def;
            }
        }
        return null;
    }

    /** Canaux vivants (sans les tombes), dans l'ordre de la liste. */
    public List<ChannelDef> live() {
        List<ChannelDef> out = new ArrayList<>();
        for (ChannelDef def : channels) {
            if (!def.deleted) {
                out.add(def);
            }
        }
        return out;
    }

    /** 1.11 (D2b) : canaux (tombes comprises) dans l'ordre d'affichage : rang {@link ChannelDef#order}, puis ordre de la liste. */
    public List<ChannelDef> ordered() {
        List<ChannelDef> out = new ArrayList<>(channels);
        out.sort(Comparator.comparingInt(d -> d.order));
        return out;
    }

    /** Vrai si le canal est actif ; faux s'il attend (au-dela des 16 plus recents). */
    public boolean isActive(ChannelDef def) {
        if (def.deleted) {
            return false;
        }
        int newer = 0;
        for (ChannelDef other : channels) {
            if (!other.deleted && other != def && (other.rev > def.rev || other.rev == def.rev && other.uid > def.uid)) {
                newer++;
            }
        }
        return newer < MAX_CHANNELS;
    }

    @Nullable
    public PointConfig point(long key, int uid) {
        Int2ObjectOpenHashMap<PointConfig> map = points.get(key);
        return map == null ? null : map.get(uid);
    }

    public void setPoint(long key, int uid, PointConfig config) {
        points.computeIfAbsent(key, k -> new Int2ObjectOpenHashMap<>()).put(uid, config);
    }

    /**
     * 1.11 (D2c) : migration : ajoute le canal {@code def} s'il manque (copie) et, si {@code point} n'est pas nul, le point du raccord
     * {@code key} ; le drapeau auto est retire. Pur : utilise par {@link LogisticApi#seed} et par le banc de la migration.
     */
    public void seed(ChannelDef def, long key, @Nullable PointConfig point, long rev) {
        if (channel(def.uid) == null) {
            ChannelDef copy = def.copy().clamp();
            copy.rev = Math.max(copy.rev, rev);
            channels.add(copy);
        }
        if (point != null) {
            PointConfig p = point.copy().clamp();
            p.rev = Math.max(p.rev, rev);
            setPoint(key, def.uid, p);
        }
        auto = false;
        autoRev = Math.max(autoRev, rev);
    }

    /** Plus grande revision connue (canaux, points, drapeau auto, disposition) : base des revisions suivantes. */
    public long maxRev() {
        long max = Math.max(autoRev, layoutRev);
        for (ChannelDef def : channels) {
            max = Math.max(max, def.rev);
        }
        for (Int2ObjectOpenHashMap<PointConfig> map : points.values()) {
            for (PointConfig p : map.values()) {
                max = Math.max(max, p.rev);
            }
        }
        return max;
    }

    /** Ajoute ou remplace un canal (meme uid). */
    public void putChannel(ChannelDef def) {
        for (int i = 0; i < channels.size(); i++) {
            if (channels.get(i).uid == def.uid) {
                channels.set(i, def);
                return;
            }
        }
        channels.add(def);
    }

    /** Transforme un canal en tombe et retire ses points. */
    public void delete(int uid, long rev) {
        ChannelDef def = channel(uid);
        if (def == null) {
            def = new ChannelDef(uid, ChannelKind.ITEM);
            channels.add(def);
        }
        def.deleted = true;
        def.rev = rev;
        for (Int2ObjectOpenHashMap<PointConfig> map : points.values()) {
            map.remove(uid);
        }
        pruneTombs();
    }

    /** Garde au plus {@link #MAX_TOMBS} tombes (les plus recentes). */
    public void pruneTombs() {
        int tombs = 0;
        for (ChannelDef def : channels) {
            tombs += def.deleted ? 1 : 0;
        }
        if (tombs <= MAX_TOMBS) {
            return;
        }
        List<ChannelDef> dead = new ArrayList<>();
        for (ChannelDef def : channels) {
            if (def.deleted) {
                dead.add(def);
            }
        }
        dead.sort(Comparator.comparingLong(d -> d.rev));
        for (int i = 0; i < tombs - MAX_TOMBS; i++) {
            channels.remove(dead.get(i));
        }
    }

    /**
     * Reconciliation : integre {@code other} dans cette configuration (spec §3.2). Canaux par uid : la plus grande revision gagne (tombe
     * comprise) ; points par (raccord, uid) : la plus grande revision gagne ; drapeau auto et disposition : la plus grande revision gagne.
     * Les points des canaux morts sont retires.
     *
     * @return vrai si quelque chose a change
     */
    public boolean merge(NetworkConfig other) {
        boolean changed = false;
        for (ChannelDef theirs : other.channels) {
            ChannelDef mine = channel(theirs.uid);
            if (mine == null) {
                channels.add(theirs.copy());
                changed = true;
            } else if (theirs.rev > mine.rev) {
                channels.set(channels.indexOf(mine), theirs.copy());
                changed = true;
            }
        }
        if (other.autoRev > autoRev) {
            auto = other.auto;
            autoRev = other.autoRev;
            changed = true;
        }
        for (Long2ObjectMap.Entry<Int2ObjectOpenHashMap<PointConfig>> e : other.points.long2ObjectEntrySet()) {
            for (Int2ObjectMap.Entry<PointConfig> p : e.getValue().int2ObjectEntrySet()) {
                PointConfig mine = point(e.getLongKey(), p.getIntKey());
                if (mine == null || p.getValue().rev > mine.rev) {
                    setPoint(e.getLongKey(), p.getIntKey(), p.getValue().copy());
                    changed = true;
                }
            }
        }
        for (int k = 0; k < autoBody.length; k++) {
            if (Double.isNaN(autoBody[k]) && !Double.isNaN(other.autoBody[k])) {
                autoBody[k] = other.autoBody[k];
            }
        }
        if (other.layoutRev > layoutRev) {
            layout.clear();
            layout.putAll(other.layout);
            layoutRev = other.layoutRev;
            changed = true;
        }
        if (changed) {
            dropDeadPoints();
            pruneTombs();
        }
        return changed;
    }

    /**
     * Migration (spec §8.1) : deux canaux {@code partial} du meme type et de la meme origine qui se rencontrent dans un reseau sont fusionnes
     * (le plus petit uid garde ; points des autres repris quand le raccord n'en a pas ; les autres deviennent des tombes).
     *
     * @return vrai si quelque chose a change
     */
    public boolean mergePartials(long[] keys, long rev) {
        boolean changed = false;
        List<ChannelDef> live = live();
        for (ChannelDef a : live) {
            if (!a.partial || a.origin.isEmpty() || a.deleted) {
                continue;
            }
            for (ChannelDef b : live) {
                if (b == a || b.deleted || !b.partial || b.kind != a.kind || !b.origin.equals(a.origin) || b.uid < a.uid) {
                    continue;
                }
                for (long key : keys) {
                    PointConfig theirs = point(key, b.uid);
                    if (theirs != null && point(key, a.uid) == null) {
                        PointConfig moved = theirs.copy();
                        moved.rev = rev;
                        setPoint(key, a.uid, moved);
                    }
                }
                a.rev = Math.max(a.rev, rev);
                delete(b.uid, rev);
                changed = true;
            }
        }
        return changed;
    }

    /** Retire les points des canaux morts ou inconnus. */
    public void dropDeadPoints() {
        for (Int2ObjectOpenHashMap<PointConfig> map : points.values()) {
            map.int2ObjectEntrySet().removeIf(p -> {
                ChannelDef def = channel(p.getIntKey());
                return def == null || def.deleted;
            });
        }
        points.long2ObjectEntrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /** Sauvegarde ; seuls les points des raccords acceptes par {@code keys} (tile : ceux qu'elle touche ; null = tous). */
    public CompoundTag save(@Nullable LongPredicate keys) {
        CompoundTag tag = new CompoundTag();
        ListTag list = new ListTag();
        for (ChannelDef def : channels) {
            list.add(def.save());
        }
        tag.put("c", list);
        tag.putBoolean("a", auto);
        tag.putLong("av", autoRev);
        ListTag pts = new ListTag();
        for (Long2ObjectMap.Entry<Int2ObjectOpenHashMap<PointConfig>> e : points.long2ObjectEntrySet()) {
            if (keys != null && !keys.test(e.getLongKey())) {
                continue;
            }
            for (Int2ObjectMap.Entry<PointConfig> p : e.getValue().int2ObjectEntrySet()) {
                CompoundTag t = p.getValue().save();
                t.putLong("K", e.getLongKey());
                t.putInt("U", p.getIntKey());
                pts.add(t);
            }
        }
        if (!pts.isEmpty()) {
            tag.put("p", pts);
        }
        if (!layout.isEmpty()) {
            long[] packed = new long[layout.size() * 2];
            int i = 0;
            for (Long2LongMap.Entry e : layout.long2LongEntrySet()) {
                packed[i++] = e.getLongKey();
                packed[i++] = e.getLongValue();
            }
            tag.putLongArray("l", packed);
        }
        if (layoutRev != 0) {
            tag.putLong("lv", layoutRev);
        }
        ListTag bodies = new ListTag();
        for (int k = 0; k < autoBody.length; k++) {
            if (!Double.isNaN(autoBody[k])) {
                CompoundTag b = new CompoundTag();
                b.putByte("k", (byte) k);
                b.putDouble("v", autoBody[k]);
                bodies.add(b);
            }
        }
        if (!bodies.isEmpty()) {
            tag.put("ab", bodies);
        }
        return tag;
    }

    public static NetworkConfig load(CompoundTag tag) {
        NetworkConfig c = new NetworkConfig();
        ListTag list = tag.getList("c", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            ChannelDef def = ChannelDef.load(list.getCompound(i));
            if (def.uid != 0 && c.channel(def.uid) == null) {
                c.channels.add(def);
            }
        }
        c.auto = !tag.contains("a") || tag.getBoolean("a");
        c.autoRev = tag.getLong("av");
        ListTag pts = tag.getList("p", Tag.TAG_COMPOUND);
        for (int i = 0; i < pts.size(); i++) {
            CompoundTag t = pts.getCompound(i);
            c.setPoint(t.getLong("K"), t.getInt("U"), PointConfig.load(t));
        }
        long[] packed = tag.getLongArray("l");
        for (int i = 0; i + 1 < packed.length; i += 2) {
            c.layout.put(packed[i], packed[i + 1]);
        }
        c.layoutRev = tag.getLong("lv");
        ListTag bodies = tag.getList("ab", Tag.TAG_COMPOUND);
        for (int i = 0; i < bodies.size(); i++) {
            int k = bodies.getCompound(i).getByte("k");
            if (k >= 0 && k < c.autoBody.length) {
                c.autoBody[k] = bodies.getCompound(i).getDouble("v");
            }
        }
        return c;
    }
}
