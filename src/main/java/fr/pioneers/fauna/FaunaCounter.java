package fr.pioneers.fauna;

import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.ClanApi;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.core.SectionPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.Entity;

/**
 * 1.0.5-beta (lot 4, spec §3 « compteurs par zone mis a jour a l'apparition et a la mort, sans balayage ») : compte des betes de la faune
 * par zone, par groupe et par espece.
 * <ul>
 *     <li>+1 a l'entree d'une bete dans le monde (apparition, chargement de son troncon), -1 a sa sortie (mort, dechargement, conversion
 *     en race) : {@link #join} / {@link #leave}, appeles par {@link FaunaEvents} ;</li>
 *     <li>au passage du repeuplement, {@link #reconcile} relit SEULEMENT l'index (jamais le monde) : betes deplacees dans une autre zone,
 *     betes retirees sans evenement de sortie, entrees ou sorties d'un claim ;</li>
 *     <li>une bete DANS UN CLAIM de clan est suivie mais pas comptee : c'est l'elevage d'un joueur, pas la faune sauvage.</li>
 * </ul>
 */
public final class FaunaCounter {

    /** Une bete suivie : zone et espece au dernier compte, comptee ou non (claim). */
    static final class Tracked {

        long zone;
        final FaunaSpecies species;
        boolean counted;

        Tracked(long zone, FaunaSpecies species, boolean counted) {
            this.zone = zone;
            this.species = species;
            this.counted = counted;
        }
    }

    private static final Map<ServerLevel, FaunaCounter> BY_LEVEL = new WeakHashMap<>();

    private final Map<Entity, Tracked> tracked = new IdentityHashMap<>();
    /** zone -> [compte par groupe (5), puis compte par espece]. */
    private final Long2ObjectOpenHashMap<int[]> counts = new Long2ObjectOpenHashMap<>();
    private final String dim;

    private FaunaCounter(ServerLevel level) {
        this.dim = ClanApi.dim(level);
    }

    public static FaunaCounter of(ServerLevel level) {
        return BY_LEVEL.computeIfAbsent(level, FaunaCounter::new);
    }

    public static void forget(ServerLevel level) {
        BY_LEVEL.remove(level);
    }

    private boolean claimed(Entity e) {
        return ClaimIndex.owner(dim, ChunkPos.asLong(SectionPos.blockToSectionCoord(e.getBlockX()), SectionPos.blockToSectionCoord(e.getBlockZ())))
               != null;
    }

    /**
     * 1.0.5-beta (lot 10) : marque des betes d'un TROUPEAU (evenement de chasse, {@code fr.pioneers.hunt.HerdEvents}) dans les donnees
     * persistantes de l'entite : suivies mais jamais comptees (ni le repeuplement ni la reproduction sauvage ne les voient : pas d'inflation).
     */
    public static final String HERD_TAG = "pioneers_herd";

    public static boolean herd(Entity e) {
        return e.getPersistentData().contains(HERD_TAG);
    }

    public void join(Entity e, FaunaSpecies s) {
        if (tracked.containsKey(e)) {
            return;
        }
        Tracked t = new Tracked(FaunaRules.zoneKey(e.getBlockX(), e.getBlockZ()), s, !claimed(e) && !herd(e));
        tracked.put(e, t);
        if (t.counted) {
            add(t, 1);
        }
    }

    public void leave(Entity e) {
        Tracked t = tracked.remove(e);
        if (t != null && t.counted) {
            add(t, -1);
        }
    }

    private void add(Tracked t, int d) {
        int[] c = counts.computeIfAbsent(t.zone, k -> new int[FaunaGroup.COUNT + FaunaRegistry.size()]);
        if (c.length < FaunaGroup.COUNT + FaunaRegistry.size()) {
            c = java.util.Arrays.copyOf(c, FaunaGroup.COUNT + FaunaRegistry.size());
            counts.put(t.zone, c);
        }
        c[t.species.group.ordinal()] = Math.max(0, c[t.species.group.ordinal()] + d);
        int si = FaunaGroup.COUNT + t.species.index();
        c[si] = Math.max(0, c[si] + d);
        if (d < 0 && isEmpty(c)) {
            counts.remove(t.zone);
        }
    }

    private static boolean isEmpty(int[] c) {
        for (int i = 0; i < FaunaGroup.COUNT; i++) {
            if (c[i] != 0) {
                return false;
            }
        }
        return true;
    }

    /** Comptes par groupe de la zone (copie). */
    public int[] groups(long zone) {
        int[] c = counts.get(zone), out = new int[FaunaGroup.COUNT];
        if (c != null) {
            System.arraycopy(c, 0, out, 0, FaunaGroup.COUNT);
        }
        return out;
    }

    /** Compte d'une espece dans la zone. */
    public int species(long zone, FaunaSpecies s) {
        int[] c = counts.get(zone);
        int i = FaunaGroup.COUNT + s.index();
        return c == null || i >= c.length ? 0 : c[i];
    }

    public int tracked() {
        return tracked.size();
    }

    /**
     * Recalage sur l'index seul (passage du repeuplement) : retire les betes disparues sans evenement, deplace celles qui ont change de
     * zone, relit leur claim. Rend le nombre de corrections.
     */
    public int reconcile() {
        int fixes = 0;
        for (Iterator<Map.Entry<Entity, Tracked>> it = tracked.entrySet().iterator(); it.hasNext(); ) {
            Map.Entry<Entity, Tracked> en = it.next();
            Entity e = en.getKey();
            Tracked t = en.getValue();
            if (e.isRemoved()) {
                it.remove();
                if (t.counted) {
                    add(t, -1);
                }
                fixes++;
                continue;
            }
            long zone = FaunaRules.zoneKey(e.getBlockX(), e.getBlockZ());
            boolean counted = !claimed(e) && !herd(e);
            if (zone != t.zone || counted != t.counted) {
                if (t.counted) {
                    add(t, -1);
                }
                t.zone = zone;
                t.counted = counted;
                if (counted) {
                    add(t, 1);
                }
                fixes++;
            }
        }
        return fixes;
    }

    /** Les betes suivies d'une zone (GameTests, commande). */
    public java.util.List<Entity> entities(long zone) {
        java.util.List<Entity> out = new java.util.ArrayList<>();
        for (Map.Entry<Entity, Tracked> en : tracked.entrySet()) {
            if (en.getValue().zone == zone && !en.getKey().isRemoved()) {
                out.add(en.getKey());
            }
        }
        return out;
    }
}
