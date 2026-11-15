package fr.pioneers.naval;

import fr.pioneers.Pioneers;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.Arrays;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.11 : carte marine d'une dimension, en cases de 2 x 2 blocs (64 cases par chunk). Une case est navigable si ses quatre colonnes ont une
 * surface de fluide navigable libre a la meme hauteur, au moins 2 blocs de fond et rien juste au-dessus. Les mers de lave (tag
 * {@code pioneers:navigable_hot}) sont notees a part : seuls les navires MKIV y passent. Remplie au chargement des chunks, corrigee a chaque
 * bloc pose ou casse sur l'eau.
 * <p>
 * 0.20.27 (Guillaume : « No sea route » a chaque fois) : les eaux jamais chargees ne sont plus une impasse : le planificateur les
 * <b>sonde</b> ({@link SeaSounder} : biomes oceaniques du generateur, sans generer le monde) et les note comme feuilles <b>estimees</b>
 * ({@link Sheet#guess}), remplacees par le vrai releve au premier chargement du chunk. Lue par le fil du planificateur : feuilles dans une
 * table concurrente, jamais modifiees une fois publiees (on en remplace une entiere), et chaque fil lit par son propre {@link Reader}.
 * <p>
 * 0.20.46 (audit) : la carte grossissait sans fin (une feuille de ~240 octets par chunk vu, meme sans eau). Les chunks releves sans eau
 * sont un simple ensemble de cles ({@link #land}, 8 octets chacun, sauve sous {@code chartLand}) ; les estimations sont plafonnees
 * ({@link #GUESS_CAP}, les plus anciennes oubliees : elles se refont a l'identique) ; {@link #edits} compte les vrais changements (la
 * marine n'est plus resauvee sans raison).
 */
public final class WaterChart {

    public static final TagKey<Fluid> NAVIGABLE = TagKey.create(Registries.FLUID, Pioneers.rl("navigable"));
    /** 0.20.28 : format des releves sauvegardes (les releves d'un format plus ancien sont faux : ignores au chargement). */
    public static final int FORMAT = 1;
    public static final TagKey<Fluid> NAVIGABLE_HOT = TagKey.create(Registries.FLUID, Pioneers.rl("navigable_hot"));

    /** Cases d'un chunk : masque navigable, masque lave, hauteur de la surface (y du bloc de fluide du dessus) par case. */
    public static final class Sheet {
        public long nav, hot;
        public final short[] y = new short[64];
        /** 0.20.27 : feuille estimee par sondage (pas encore vue). */
        boolean guess;
    }

    private final Map<Long, Sheet> sheets = new ConcurrentHashMap<>();
    /**
     * 0.20.46 (audit) : chunks releves sans aucune case navigable (terre). Lu aussi par le fil du planificateur : toujours sous son propre
     * verrou. Un chunk n'est jamais a la fois ici et dans {@link #sheets} (sauf un instant pendant un changement, ou {@link #sheets} prime).
     */
    private final LongOpenHashSet land = new LongOpenHashSet();
    /** 0.20.46 (audit) : feuille partagee rendue pour un chunk de {@link #land} (vide : rien de navigable). Jamais modifiee. */
    static final Sheet LAND = new Sheet();
    /** 0.20.46 (audit) : estimations gardees au plus (au-dela, les plus anciennes sont oubliees ; ~240 octets chacune). */
    public static final int GUESS_CAP = 32_768;

    private record Guess(long key, Sheet sheet) {}

    private final ConcurrentLinkedQueue<Guess> guesses = new ConcurrentLinkedQueue<>();
    private final AtomicInteger guessCount = new AtomicInteger();
    /** 0.20.46 (audit) : changements du releve sauvegarde (fil serveur) : la marine n'est remise a sauver que s'il a change. */
    private int edits;
    /** Augmente a chaque changement du vrai releve (les routes en cours se reverifient). */
    public volatile int version;
    /** Lecteur du fil serveur (les autres fils prennent le leur : {@link #reader()}). */
    private final Reader main = new Reader(null);

    public static int cell(double block) {
        return (int) Math.floor(block / 2);
    }

    private static int index(int cx, int cz) {
        return (cx & 7) | (cz & 7) << 3;
    }

    /** Nouveau lecteur (un par fil, ou par calcul) : garde la derniere feuille lue. */
    public Reader reader() {
        return new Reader(null);
    }

    /** 0.20.27 : lecteur qui sonde a la volee les chunks jamais vus ({@code probe} null : non). */
    public Reader reader(@Nullable SeaSounder.Probe probe) {
        return new Reader(probe);
    }

    /**
     * Lecture de la carte avec le cache de la derniere feuille lue (les calculs de route lisent des cases voisines : presque toujours la meme
     * feuille). Un lecteur par fil.
     */
    public final class Reader {

        private long lastKey = Long.MIN_VALUE;
        private Sheet lastSheet;
        @Nullable
        private SeaSounder.Probe probe;
        /** Chunks sondes par ce lecteur. */
        public int sounded;

        Reader(@Nullable SeaSounder.Probe probe) {
            this.probe = probe;
        }

        @Nullable
        Sheet sheet(int cx, int cz) {
            long k = ChunkPos.asLong(cx >> 3, cz >> 3);
            if (k != lastKey) {
                lastSheet = lookup(k);
                lastKey = k;
                if (lastSheet == null && probe != null) {
                    try {
                        Sheet s = probe.estimate(cx >> 3, cz >> 3);
                        if (s != null) {
                            estimate(cx >> 3, cz >> 3, s);
                            //0.20.46 (audit) : l'estimation peut deja etre oubliee (plafond) : on garde celle qu'on vient de faire
                            Sheet now = lookup(k);
                            lastSheet = now != null ? now : s;
                            sounded++;
                        }
                    } catch (RuntimeException e) {
                        //Generateur qui ne se laisse pas sonder hors du fil serveur : eaux inconnues = non navigables
                        probe = null;
                    }
                }
            }
            return lastSheet;
        }

        /** Case navigable (pour un navire MKIV, la lave compte). */
        public boolean navigable(int cx, int cz, boolean heatproof) {
            Sheet s = sheet(cx, cz);
            if (s == null) {
                return false;
            }
            long bit = 1L << index(cx, cz);
            return (s.nav & bit) != 0 && (heatproof || (s.hot & bit) == 0);
        }

        public boolean known(int cx, int cz) {
            return sheet(cx, cz) != null;
        }

        /** Hauteur de la surface (y du bloc de fluide du dessus), ou {@code Integer.MIN_VALUE}. */
        public int surface(int cx, int cz) {
            Sheet s = sheet(cx, cz);
            return s == null || (s.nav & 1L << index(cx, cz)) == 0 ? Integer.MIN_VALUE : s.y[index(cx, cz)];
        }

        /** Feuille (chunk, 8 x 8 cases) entierement navigable a une seule hauteur : cette hauteur, sinon {@code Integer.MIN_VALUE}. */
        public int uniformSurface(int chunkX, int chunkZ, boolean heatproof) {
            Sheet s = sheet(chunkX << 3, chunkZ << 3);
            if (s == null || s.nav != -1L || !heatproof && s.hot != 0) {
                return Integer.MIN_VALUE;
            }
            short y = s.y[0];
            for (short v : s.y) {
                if (v != y) {
                    return Integer.MIN_VALUE;
                }
            }
            return y;
        }

        /** Case libre avec une marge de {@code r} cases tout autour (largeur du navire). */
        public boolean clear(int cx, int cz, int r, boolean heatproof) {
            int y = surface(cx, cz);
            if (y == Integer.MIN_VALUE || !navigable(cx, cz, heatproof)) {
                return false;
            }
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if ((dx != 0 || dz != 0) && (!navigable(cx + dx, cz + dz, heatproof) || surface(cx + dx, cz + dz) != y)) {
                        return false;
                    }
                }
            }
            return true;
        }
    }

    // ------------------------------------------------------------------ lecture (fil serveur)

    public boolean navigable(int cx, int cz, boolean heatproof) {
        return main.navigable(cx, cz, heatproof);
    }

    public boolean known(int cx, int cz) {
        return main.known(cx, cz);
    }

    public boolean hot(int cx, int cz) {
        Sheet s = main.sheet(cx, cz);
        return s != null && (s.hot & 1L << index(cx, cz)) != 0;
    }

    public int surface(int cx, int cz) {
        return main.surface(cx, cz);
    }

    public int uniformSurface(int chunkX, int chunkZ, boolean heatproof) {
        return main.uniformSurface(chunkX, chunkZ, heatproof);
    }

    public boolean clear(int cx, int cz, int r, boolean heatproof) {
        return main.clear(cx, cz, r, heatproof);
    }

    /** Nombre de feuilles (relevees, terre comprise, et estimees). */
    public int size() {
        return sheets.size() + landCount();
    }

    /** 0.20.46 (audit) : chunks releves sans eau. */
    public int landCount() {
        synchronized (land) {
            return land.size();
        }
    }

    /** 0.20.46 (audit) : changements du releve depuis le chargement (fil serveur). */
    public int edits() {
        return edits;
    }

    /** Feuille d'un chunk : releve ou estimation, {@link #LAND} pour un chunk de terre, null si inconnu. */
    @Nullable
    private Sheet lookup(long key) {
        Sheet s = sheets.get(key);
        if (s == null && isLand(key)) {
            return LAND;
        }
        return s;
    }

    private boolean isLand(long key) {
        synchronized (land) {
            return land.contains(key);
        }
    }

    /**
     * 0.20.46 (audit) : publie le releve d'un chunk (fil serveur). Terre : la cle va dans {@link #land} AVANT de quitter {@link #sheets}
     * (un lecteur d'un autre fil voit toujours l'un ou l'autre) ; eau : la feuille entre dans {@link #sheets} avant que la cle quitte
     * {@link #land}.
     */
    private void publish(long key, Sheet s, @Nullable Sheet old) {
        if (s.nav == 0 && s.hot == 0) {
            if (old != LAND) {
                synchronized (land) {
                    land.add(key);
                }
                sheets.remove(key);
            }
        } else {
            sheets.put(key, s);
            if (old == LAND) {
                synchronized (land) {
                    land.remove(key);
                }
            }
        }
        edits++;
        main.lastKey = Long.MIN_VALUE;
    }

    /**
     * 0.20.27 : estimation d'un chunk jamais vu (sans effet si le chunk est deja releve ou estime). 0.20.46 (audit) : au plus
     * {@link #GUESS_CAP} estimations, les plus anciennes oubliees (sondage deterministe : refaite a l'identique au besoin).
     */
    void estimate(int chunkX, int chunkZ, Sheet s) {
        s.guess = true;
        long key = ChunkPos.asLong(chunkX, chunkZ);
        if (sheets.putIfAbsent(key, s) != null) {
            return;
        }
        if (isLand(key)) {
            //Releve « terre » arrive entre-temps (fil serveur) : il prime
            sheets.remove(key, s);
            return;
        }
        guesses.add(new Guess(key, s));
        if (guessCount.incrementAndGet() > GUESS_CAP) {
            Guess old = guesses.poll();
            if (old != null) {
                guessCount.decrementAndGet();
                //Seulement si c'est toujours cette estimation (pas remplacee par un vrai releve)
                sheets.remove(old.key(), old.sheet());
            }
        }
    }

    // ------------------------------------------------------------------ releve

    /** Releve d'un chunk charge. */
    public void scan(LevelChunk chunk) {
        ChunkPos cp = chunk.getPos();
        Sheet s = new Sheet();
        boolean any = false;
        for (int i = 0; i < 64; i++) {
            int lx = (i & 7) * 2, lz = (i >> 3) * 2;
            int y = Integer.MIN_VALUE;
            boolean ok = true, hot = false;
            for (int k = 0; k < 4 && ok; k++) {
                int x = cp.getMinBlockX() + lx + (k & 1), z = cp.getMinBlockZ() + lz + (k >> 1);
                int h = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x & 15, z & 15);
                int top = column(chunk, x, h, z);
                if (top == Integer.MIN_VALUE || y != Integer.MIN_VALUE && top != y) {
                    ok = false;
                } else {
                    y = top;
                    hot |= chunk.getFluidState(new BlockPos(x, top, z)).is(NAVIGABLE_HOT);
                }
            }
            if (ok && y != Integer.MIN_VALUE) {
                s.nav |= 1L << i;
                if (hot) {
                    s.hot |= 1L << i;
                }
                s.y[i] = (short) y;
                any = true;
            }
        }
        long key = cp.toLong();
        Sheet old = lookup(key);
        if (!any) {
            //Chunk connu mais sans eau : le chemin sait que c'est de la terre (0.20.46 (audit) : une cle dans {@link #land}, plus une feuille)
            s.nav = 0;
        }
        if (old == null || old.nav != s.nav || old.hot != s.hot) {
            version++;
        }
        //0.20.46 (audit) : releve identique au precedent : rien a publier ni a resauver
        boolean same = old != null && !old.guess && (old == LAND ? s.nav == 0 && s.hot == 0
                                                                : old.nav == s.nav && old.hot == s.hot && Arrays.equals(old.y, s.y));
        if (!same) {
            publish(key, s, old);
        }
    }

    /**
     * Surface d'une colonne : le bloc du dessus (heightmap) doit etre un fluide navigable de source, le dessous aussi. {@code top} = y du bloc
     * le plus haut : {@code ChunkAccess.getHeight} rend deja ce bloc (contrairement a {@code Level.getHeight}, qui rend la case libre au-dessus).
     * 0.20.28 : avant, on descendait d'un bloc de trop (le bloc d'eau sous la surface, avec de l'eau au-dessus) : TOUTE l'eau relevee etait
     * notee en terre, d'ou les « Aucune route maritime » a la sortie du port.
     */
    private static int column(LevelChunk chunk, int x, int top, int z) {
        BlockPos p = new BlockPos(x, top, z);
        FluidState f = chunk.getFluidState(p);
        if (!navigableFluid(f) || !chunk.getBlockState(p).getCollisionShape(chunk, p).isEmpty()) {
            return Integer.MIN_VALUE;
        }
        BlockPos below = p.below();
        if (!navigableFluid(chunk.getFluidState(below))) {
            return Integer.MIN_VALUE;
        }
        BlockState above = chunk.getBlockState(p.above());
        if (!above.isAir() && !(above.getBlock() instanceof SeaBeaconBlock)) {
            return Integer.MIN_VALUE;
        }
        return top;
    }

    static boolean navigableFluid(FluidState f) {
        return !f.isEmpty() && f.isSource() && (f.is(NAVIGABLE) || f.is(NAVIGABLE_HOT));
    }

    /** Un bloc a change pres de l'eau : on refait la case (et le chunk si besoin). */
    public void blockChanged(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            return;
        }
        if (!known(cell(pos.getX()), cell(pos.getZ()))) {
            return;
        }
        scan(level.getChunkAt(pos));
    }

    // ------------------------------------------------------------------ sauvegarde

    public ListTag save() {
        ListTag list = new ListTag();
        //0.20.46 (audit) : chunks dans l'ordre des cles (voisins cote a cote : le fichier se compresse mieux) ; la terre est sauvee a part
        //({@link #saveLand})
        long[] keys = sheets.keySet().stream().mapToLong(Long::longValue).toArray();
        Arrays.sort(keys);
        for (long key : keys) {
            Sheet s = sheets.get(key);
            if (s == null || s.guess) {
                continue;                                         //0.20.27 : les estimations se refont (rapide) : jamais sauvees
            }
            CompoundTag t = new CompoundTag();
            t.putLong("c", key);
            t.putLong("n", s.nav);
            if (s.hot != 0) {
                t.putLong("h", s.hot);
            }
            if (s.nav != 0) {
                //0.20.27 : surface uniforme (pleine mer, estimations) : une seule valeur
                boolean uniform = true;
                for (int i = 1; i < 64 && uniform; i++) {
                    uniform = s.y[i] == s.y[0];
                }
                if (uniform) {
                    t.putShort("u", s.y[0]);
                } else {
                    int[] ys = new int[64];
                    for (int i = 0; i < 64; i++) {
                        ys[i] = s.y[i];
                    }
                    t.putIntArray("y", ys);
                }
            }
            list.add(t);
        }
        return list;
    }

    /** 0.20.46 (audit) : chunks de terre (cles), sauves sous {@code chartLand} (8 octets par chunk au lieu d'une entree de la liste). */
    public long[] saveLand() {
        long[] keys;
        synchronized (land) {
            keys = land.toLongArray();
        }
        Arrays.sort(keys);                                        //cles voisines cote a cote : le fichier se compresse bien mieux
        return keys;
    }

    public void load(ListTag list) {
        load(list, new long[0]);
    }

    /**
     * Chargement. 0.20.46 (audit) : {@code landKeys} = chunks de terre (nouvelle cle {@code chartLand}) ; les anciennes sauvegardes notaient
     * la terre comme des entrees sans case navigable de la liste {@code chart} : elles passent dans {@link #land} ici.
     */
    public void load(ListTag list, long[] landKeys) {
        sheets.clear();
        guesses.clear();
        guessCount.set(0);
        main.lastKey = Long.MIN_VALUE;
        synchronized (land) {
            land.clear();
            for (long k : landKeys) {
                land.add(k);
            }
        }
        for (Tag tag : list) {
            CompoundTag t = (CompoundTag) tag;
            long nav = t.getLong("n"), hot = t.getLong("h");
            if (nav == 0 && hot == 0) {
                synchronized (land) {
                    land.add(t.getLong("c"));
                }
                continue;
            }
            Sheet s = new Sheet();
            s.nav = nav;
            s.hot = hot;
            if (t.contains("u")) {
                Arrays.fill(s.y, t.getShort("u"));
            } else {
                int[] ys = t.getIntArray("y");
                for (int i = 0; i < Math.min(64, ys.length); i++) {
                    s.y[i] = (short) ys[i];
                }
            }
            sheets.put(t.getLong("c"), s);
            synchronized (land) {
                land.remove(t.getLong("c"));
            }
        }
    }

    /** Banc d'essai : pose une case (navigable ou non) ; la feuille est copiee puis remplacee (lecteurs des autres fils). */
    public void set(int cx, int cz, boolean nav, int y, boolean hot) {
        long key = ChunkPos.asLong(cx >> 3, cz >> 3);
        Sheet old = lookup(key), s = new Sheet();
        if (old != null) {
            s.nav = old.nav;
            s.hot = old.hot;
            System.arraycopy(old.y, 0, s.y, 0, 64);
        }
        long bit = 1L << index(cx, cz);
        s.nav = nav ? s.nav | bit : s.nav & ~bit;
        s.hot = hot ? s.hot | bit : s.hot & ~bit;
        s.y[index(cx, cz)] = (short) y;
        publish(key, s, old);
        version++;
    }

    /** Banc d'essai : oublie un chunk (eaux inconnues). */
    public void forget(int chunkX, int chunkZ) {
        long key = ChunkPos.asLong(chunkX, chunkZ);
        sheets.remove(key);
        synchronized (land) {
            land.remove(key);
        }
        main.lastKey = Long.MIN_VALUE;
    }
}
