package fr.pioneers.transport.logistic;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §7.1) : reseaux logistiques de chaque monde serveur.
 * <ul>
 *     <li>graphe reconstruit seulement aux changements (pose, casse, coupure, fiche qui apparait ou disparait, chargement et dechargement de
 *     chunk), en fin de tick, par un parcours des etats des blocs ({@link NetworkGraph}) ; pose d'un cable nu qui ne touche qu'un reseau :
 *     ajout direct, sans parcours ;</li>
 *     <li>un seul tick pour tous les reseaux, dans un budget de {@link #BUDGET_NANOS} par monde (au-dela, les reseaux restants passent au tick
 *     suivant, depart tournant, credit garde : rien n'est perdu) ;</li>
 *     <li>configuration reunie depuis les tiles a fiche (reconciliation {@link NetworkConfig#merge}) et partagee avec elles ;</li>
 *     <li>1.0.5-beta (lot 45, retour 66 de Guillaume : « Le reseau de cable logistique ne doit pas avoir de limite de distance », reponse
 *     « Sites + pause ») : la TOPOLOGIE de tous les cables de la dimension est gardee dans un SavedData ({@link CableIndex},
 *     {@value #INDEX_NAME}) : le parcours lit les vrais etats dans un chunk charge et l'index dans un chunk NON CHARGE (jamais de lecture ni
 *     de chargement force) ; le dechargement d'un chunk ne dissout plus rien : ses machines sont mises EN PAUSE ({@code Attach.paused}) ;
 *     le chargement d'un chunk le releve en entier dans l'index (sections filtrees par {@code maybeHas} ; les mondes d'avant s'indexent
 *     ainsi), reprend ses machines et reunit la configuration de ses tiles a fiche ; les configurations des fiches non chargees d'un reseau
 *     dissous sont gardees en memoire (orphelines) pour la reconstruction suivante ;</li>
 *     <li>1.0.5-beta (lot 45, retour 63 : « Il faut que les cables logistiques se deconnectent quand ils sont parametres en "rien" ») : la
 *     face d'un cable vers une machine DECONNECTEE ({@code Attach.detached}) perd sa fiche (masque de la tile, etat NONE) mais reste une fiche
 *     LATENTE du reseau (index) : la machine reste un raccord ; la file {@code detach} applique ces changements au debut du tick suivant,
 *     sans reconstruire le reseau.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LogisticNetworks {

    /** Budget de tous les reseaux d'un monde par tick (spec §7.1 : 3 ms). */
    public static final long BUDGET_NANOS = 3_000_000L;
    /** 1.0.5-beta (lot 45) : nom du SavedData de la topologie ; chunks charges releves au plus par tick. */
    public static final String INDEX_NAME = "pioneers_logistic_cables";
    public static final int CHUNKS_PER_TICK = 64;

    private LogisticNetworks() {
    }

    private static final class World {

        final Long2ObjectOpenHashMap<LogisticNetwork> byCable = new Long2ObjectOpenHashMap<>();
        final LongLinkedOpenHashSet dirty = new LongLinkedOpenHashSet();
        final List<LogisticNetwork> all = new ArrayList<>();
        final LongLinkedOpenHashSet chunks = new LongLinkedOpenHashSet();
        int start, nextNid = 1;
        /** Temps du dernier tick (tous reseaux) et de la derniere reconstruction, nanosecondes. */
        long lastTick, lastRebuild;
        /** 1.0.5-beta (lot 45) : topologie sauvee de la dimension. */
        final CableIndex index;
        /** 1.0.5-beta (lot 45, retour 63) : cables a fiche dont les faces deconnectees sont a mettre a jour (debut du tick suivant). */
        final LongLinkedOpenHashSet detach = new LongLinkedOpenHashSet();
        /** 1.0.5-beta (lot 45, retour 63) : changement d'etat applique par la file {@code detach} (faces basculees) : pas de reconstruction. */
        final Long2IntOpenHashMap quiet = new Long2IntOpenHashMap();
        /** 1.0.5-beta (lot 45, retour 66) : configuration d'un reseau dissous gardee pour ses fiches non chargees. */
        final Long2ObjectOpenHashMap<NetworkConfig> orphans = new Long2ObjectOpenHashMap<>();
        /** 1.0.5-beta (lot 45) : GameTests seulement : chunks traites comme non charges. */
        final LongOpenHashSet testUnloaded = new LongOpenHashSet();

        World(CableIndex index) {
            this.index = index;
        }
    }

    /** 1.0.5-beta (lot 45, retour 66) : le SavedData de la topologie ({@link CableIndex}). */
    public static final class IndexData extends SavedData {

        final CableIndex index;

        public IndexData() {
            this(new CableIndex());
        }

        IndexData(CableIndex index) {
            this.index = index;
            index.onChange(this::setDirty);
        }

        public static IndexData load(CompoundTag tag) {
            return new IndexData(CableIndex.load(tag));
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            return index.save(tag);
        }
    }

    private static final Map<Level, World> WORLDS = new WeakHashMap<>();

    @Nullable
    private static World world(Level level, boolean create) {
        if (level.isClientSide || !(level instanceof ServerLevel server)) {
            return null;
        }
        World w = WORLDS.get(level);
        if (w == null && create) {
            w = new World(server.getDataStorage().computeIfAbsent(IndexData::load, IndexData::new, INDEX_NAME).index);
            WORLDS.put(level, w);
        }
        return w;
    }

    /** 1.0.5-beta (lot 45) : chunk charge (jamais de chargement force) ; les chunks des GameTests « decharges » comptent comme non charges. */
    private static boolean loaded(Level level, World w, long pos) {
        int cx = BlockPos.getX(pos) >> 4, cz = BlockPos.getZ(pos) >> 4;
        return level.hasChunk(cx, cz) && (w.testUnloaded.isEmpty() || !w.testUnloaded.contains(NetworkGraph.chunk(pos)));
    }

    /** 1.0.5-beta (lot 45) : topologie de la dimension (null cote client ; banc : GameTests). */
    @Nullable
    public static CableIndex index(Level level) {
        World w = world(level, true);
        return w == null ? null : w.index;
    }

    private static int infoOf(LogisticCableBlock block) {
        return block.level.ordinal() | (block.clear ? NetworkGraph.CLEAR : 0);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    /** Reseau d'un cable (null tant qu'il n'est pas construit, ou cote client). */
    @Nullable
    public static LogisticNetwork network(Level level, BlockPos cable) {
        World w = world(level, false);
        return w == null ? null : w.byCable.get(cable.asLong());
    }

    /** Tous les reseaux d'un monde (copie). */
    public static List<LogisticNetwork> networks(Level level) {
        World w = world(level, false);
        return w == null ? List.of() : List.copyOf(w.all);
    }

    /** Les {@code limit} reseaux les plus chers du monde (commande de profilage, onglet Aide du diagramme). */
    public static List<LogisticNetwork> profile(Level level, int limit) {
        List<LogisticNetwork> list = new ArrayList<>(networks(level));
        list.sort(Comparator.comparingDouble(LogisticNetwork::costNanos).reversed());
        return list.subList(0, Math.min(limit, list.size()));
    }

    /** Duree du dernier tick de tous les reseaux du monde (ns). */
    public static long lastTickNanos(Level level) {
        World w = world(level, false);
        return w == null ? 0 : w.lastTick;
    }

    /** Configuration a sauver par une tile : celle du reseau vivant (corps a jour) sinon la sienne. */
    @Nullable
    static NetworkConfig configForSave(Level level, BlockPos pos, @Nullable NetworkConfig own) {
        LogisticNetwork network = network(level, pos);
        if (network == null) {
            return own;
        }
        network.saveBodies();
        return network.config();
    }

    // ------------------------------------------------------------------
    // Evenements des blocs
    // ------------------------------------------------------------------

    /** Cable pose : ajout direct s'il est nu et ne touche qu'un reseau, sinon reconstruction. */
    static void placed(Level level, BlockPos pos, BlockState state) {
        World w = world(level, true);
        if (w == null || !(state.getBlock() instanceof LogisticCableBlock block)) {
            return;
        }
        long p = pos.asLong();
        int plugs = LogisticCableBlock.plugMask(state), linked = LogisticCableBlock.cableMask(state);
        w.index.put(p, infoOf(block), linked, plugs);
        LogisticNetwork only = null;
        boolean direct = plugs == 0 && !block.clear && linked != 0;
        for (Direction d : Direction.values()) {
            if ((linked >> d.ordinal() & 1) == 0) {
                continue;
            }
            LogisticNetwork n = w.byCable.get(BlockPos.offset(p, d));
            if (n == null || only != null && n != only) {
                direct = false;
            }
            only = n == null ? only : n;
        }
        if (direct && only != null && only.level.ordinal() <= block.level.ordinal() && only.addCable(p, block.level.ordinal(), linked)) {
            w.byCable.put(p, only);
            return;
        }
        invalidate(level, pos);
    }

    /** Etat change (fiche, coupure, liaison) : le reseau est reconstruit, sauf une liaison nouvelle vers un cable du meme reseau. */
    static void changed(Level level, BlockPos pos, BlockState old, BlockState state) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        long p = pos.asLong();
        if (state.getBlock() instanceof LogisticCableBlock block) {
            w.index.put(p, infoOf(block), LogisticCableBlock.cableMask(state), LogisticCableBlock.plugMask(state));
        }
        LogisticNetwork network = w.byCable.get(p);
        //1.0.5-beta (lot 45, retour 63) : fiche retiree ou rendue par la file des faces deconnectees : la face reste une fiche (latente) du
        //reseau, rien a reconstruire (seules les faces basculees ont change)
        int toggled = w.quiet.containsKey(p) ? w.quiet.remove(p) : 0;
        if (network != null && toggled != 0 && LogisticCableBlock.cableMask(old) == LogisticCableBlock.cableMask(state)
            && ((LogisticCableBlock.plugMask(old) ^ LogisticCableBlock.plugMask(state)) & ~toggled) == 0) {
            return;
        }
        if (network != null && LogisticCableBlock.plugMask(old) == LogisticCableBlock.plugMask(state)) {
            int before = LogisticCableBlock.cableMask(old), after = LogisticCableBlock.cableMask(state);
            //Seules des liaisons ajoutees vers des cables deja dans ce reseau (pose directe d'un voisin) : rien a refaire
            if ((before & ~after) == 0) {
                boolean same = true;
                for (Direction d : Direction.values()) {
                    if (((after & ~before) >> d.ordinal() & 1) == 0) {
                        continue;
                    }
                    same &= w.byCable.get(BlockPos.offset(p, d)) == network;
                }
                if (same) {
                    network.linkSides(p, after);
                    return;
                }
            }
        }
        invalidate(level, pos);
    }

    /** Cable casse : son reseau est dissous ; les voisins se reconstruiront. */
    static void removed(Level level, BlockPos pos, BlockState state) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        long p = pos.asLong();
        dissolve(level, w, w.byCable.get(p));
        w.byCable.remove(p);
        w.dirty.remove(p);
        w.index.remove(p);
        w.orphans.remove(p);
        w.detach.remove(p);
        int linked = LogisticCableBlock.cableMask(state);
        for (Direction d : Direction.values()) {
            if ((linked >> d.ordinal() & 1) != 0) {
                w.dirty.add(BlockPos.offset(p, d));
            }
        }
    }

    /**
     * Tile a fiche chargee ou creee : son reseau sera (re)construit si elle n'en a pas. 1.0.5-beta (lot 45) : ses faces deconnectees
     * (sauvees dans la tile) passent dans l'index.
     */
    static void loaded(Level level, BlockPos pos, int detached) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        w.index.setDetached(pos.asLong(), detached);
        if (!w.byCable.containsKey(pos.asLong())) {
            w.dirty.add(pos.asLong());
        }
    }

    /**
     * 1.0.5-beta (lot 45, retour 63) : faces deconnectees d'une tile changees par le bloc (machine voisine disparue : la fiche latente part) ;
     * l'index suit, et le reseau est reconstruit si une fiche latente disparait.
     */
    static void detachedChanged(Level level, BlockPos pos, int before, int after) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        w.index.setDetached(pos.asLong(), after);
        if ((before & ~after) != 0 && !w.quiet.containsKey(pos.asLong())) {
            invalidate(level, pos);
        }
    }

    /** 1.0.5-beta (lot 45, retour 63) : reglages du reseau changes : les faces de ses cables a fiche charges seront mises a jour. */
    static void queueDetach(Level level, LogisticNetwork network) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        NetworkGraph g = network.graph;
        for (int i = 0; i < g.size; i++) {
            if (g.plugs[i] != 0 && loaded(level, w, g.cables[i])) {
                w.detach.add(g.cables[i]);
            }
        }
    }

    /** Branchements d'un cable modifies (coupure, fiche, migration) : son reseau est reconstruit. */
    public static void invalidate(Level level, BlockPos pos) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        dissolve(level, w, w.byCable.get(pos.asLong()));
        w.dirty.add(pos.asLong());
    }

    /** Frontiere de claim changee dans ce chunk ({@code ChunkPos.toLong}) : les reseaux qui y passent sont reconstruits. */
    public static void chunkChanged(Level level, long chunk) {
        World w = world(level, false);
        if (w == null) {
            return;
        }
        for (LogisticNetwork network : List.copyOf(w.all)) {
            if (network.chunks().contains(chunk)) {
                dissolve(level, w, network);
            }
        }
    }

    // ------------------------------------------------------------------
    // Reseau numerique (1.11, spec §8.3) : tout reseau logistique MKI ou mieux porte les donnees
    // ------------------------------------------------------------------

    /** Cable logistique qui porte les donnees (MKI ou mieux, pas le supraconducteur). */
    public static boolean isDataCable(BlockState state) {
        return state.getBlock() instanceof LogisticCableBlock cable && cable.level.permits(ChannelKind.DATA);
    }

    /** Reseau logistique du cable {@code cable} s'il porte les donnees (niveau du reseau MKI ou mieux), sinon null. */
    @Nullable
    public static LogisticNetwork digital(Level level, BlockPos cable) {
        if (!level.isLoaded(cable) || !isDataCable(level.getBlockState(cable))) {
            return null;
        }
        LogisticNetwork network = network(level, cable);
        return network != null && network.level.permits(ChannelKind.DATA) ? network : null;
    }

    private static void dissolve(Level level, World w, @Nullable LogisticNetwork network) {
        if (network == null) {
            return;
        }
        network.saveBodies();
        for (int i = 0; i < network.graph.size; i++) {
            long cable = network.graph.cables[i];
            if (w.byCable.get(cable) == network) {
                w.byCable.remove(cable);
                w.dirty.add(cable);
            }
            //1.0.5-beta (lot 45) : fiche dans un chunk non charge : sa tile ne peut pas rendre la configuration a la reconstruction
            if (network.graph.plugs[i] != 0 && !loaded(level, w, cable)) {
                w.orphans.put(cable, network.config());
            }
        }
        w.all.remove(network);
    }

    // ------------------------------------------------------------------
    // Chunks
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level && event.getChunk() instanceof LevelChunk chunk) {
            World w = world(level, true);
            if (w != null) {
                w.chunks.add(chunk.getPos().toLong());
            }
        }
    }

    /**
     * 1.0.5-beta (lot 45, retour 66) : chunk decharge : plus aucune dissolution (la topologie reste dans l'index) ; les machines de ce chunk
     * passent EN PAUSE (rien n'en sort, rien n'y entre jusqu'au rechargement).
     */
    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (!(event.getLevel() instanceof ServerLevel level)) {
            return;
        }
        World w = world(level, false);
        if (w == null) {
            return;
        }
        long chunk = event.getChunk().getPos().toLong();
        w.chunks.remove(chunk);
        pauseChunk(w, chunk, true);
    }

    /** Machines d'un chunk mises en pause ou reprises. */
    private static void pauseChunk(World w, long chunk, boolean paused) {
        for (LogisticNetwork network : w.all) {
            for (LogisticNetwork.Attach a : network.attaches()) {
                if (NetworkGraph.chunk(a.key) == chunk) {
                    network.setPaused(a.key, paused);
                }
            }
        }
    }

    /**
     * 1.0.5-beta (lot 45) : GameTests seulement : traite un chunk comme decharge (index, pause) ou recharge, sans toucher au vrai chunk.
     */
    public static void testUnloaded(ServerLevel level, ChunkPos chunk, boolean unloaded) {
        World w = world(level, true);
        if (w == null) {
            return;
        }
        if (unloaded) {
            if (w.testUnloaded.add(chunk.toLong())) {
                w.chunks.remove(chunk.toLong());
                pauseChunk(w, chunk.toLong(), true);
            }
        } else if (w.testUnloaded.remove(chunk.toLong())) {
            w.chunks.add(chunk.toLong());
        }
    }

    /**
     * 1.0.5-beta (lot 45, retour 66) : chunk charge : (1) RELEVE complet de ses cables dans l'index (sections qui contiennent peut-etre un
     * cable logistique : filtre de la palette {@code maybeHas}) ; les cables nouveaux ou sans reseau sont marques (raccroche les reseaux
     * d'un monde d'avant) ; un cable dont l'enregistrement a change dans un reseau vivant le fait reconstruire ; (2) machines du chunk
     * REPRISES ; (3) configuration des tiles a fiche du chunk REUNIE a celle de leur reseau ; faces deconnectees a jour ; (4) machine jamais
     * vue chargee (index sans fiche) : reseau reconstruit.
     */
    private static void processChunk(ServerLevel level, World w, long chunkKey) {
        ChunkPos cp = new ChunkPos(chunkKey);
        if (!level.hasChunk(cp.x, cp.z) || w.testUnloaded.contains(chunkKey)) {
            return;
        }
        LevelChunk chunk = level.getChunk(cp.x, cp.z);
        Long2IntOpenHashMap found = new Long2IntOpenHashMap();
        LevelChunkSection[] sections = chunk.getSections();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int s = 0; s < sections.length; s++) {
            LevelChunkSection section = sections[s];
            if (section == null || section.hasOnlyAir() || !section.maybeHas(state -> state.getBlock() instanceof LogisticCableBlock)) {
                continue;
            }
            int baseY = chunk.getSectionYFromSectionIndex(s) << 4;
            for (int y = 0; y < 16; y++) {
                for (int z = 0; z < 16; z++) {
                    for (int x = 0; x < 16; x++) {
                        BlockState state = section.getBlockState(x, y, z);
                        if (state.getBlock() instanceof LogisticCableBlock block) {
                            at.set(cp.getMinBlockX() + x, baseY + y, cp.getMinBlockZ() + z);
                            int det = chunk.getBlockEntity(at) instanceof LogisticCableTile tile ? tile.detachedMask() : 0;
                            found.put(at.asLong(), CableIndex.pack(infoOf(block), LogisticCableBlock.cableMask(state), LogisticCableBlock.plugMask(state), det));
                        }
                    }
                }
            }
        }
        for (long pos : w.index.replaceChunk(chunkKey, found)) {
            LogisticNetwork network = w.byCable.get(pos);
            if (network != null) {
                //Topologie gardee differente de la vraie (monde modifie hors du jeu) : reconstruction
                invalidate(level, BlockPos.of(pos));
            }
        }
        for (long pos : found.keySet()) {
            if (!w.byCable.containsKey(pos)) {
                w.dirty.add(pos);
            }
        }
        for (LogisticNetwork network : List.copyOf(w.all)) {
            if (!w.all.contains(network)) {
                continue;
            }
            boolean touches = network.chunks().contains(chunkKey), unknown = false;
            for (LogisticNetwork.Attach a : network.attaches()) {
                if (NetworkGraph.chunk(a.key) == chunkKey) {
                    touches = true;
                    unknown |= a.unknown;
                }
            }
            if (!touches) {
                continue;
            }
            if (unknown) {
                //Machine jamais vue chargee : ses types offerts sont lus a la reconstruction
                dissolve(level, w, network);
                continue;
            }
            wakeChunk(level, w, network, chunkKey);
        }
    }

    /** Machines du chunk reprises ; configuration des tiles a fiche du chunk reunie a celle du reseau ; roles et faces a jour. */
    private static void wakeChunk(ServerLevel level, World w, LogisticNetwork network, long chunkKey) {
        for (LogisticNetwork.Attach a : network.attaches()) {
            if (NetworkGraph.chunk(a.key) == chunkKey) {
                network.setPaused(a.key, false);
            }
        }
        NetworkGraph g = network.graph;
        boolean merged = false;
        List<LogisticCableTile> tiles = new ArrayList<>();
        for (int i = 0; i < g.size; i++) {
            if (g.plugs[i] == 0 || NetworkGraph.chunk(g.cables[i]) != chunkKey) {
                continue;
            }
            w.orphans.remove(g.cables[i]);
            if (level.getBlockEntity(BlockPos.of(g.cables[i])) instanceof LogisticCableTile tile) {
                if (tile.config() != null && tile.config() != network.config()) {
                    merged |= network.mergeConfig(tile.config());
                }
                tiles.add(tile);
            }
        }
        if (merged) {
            pushConfig(level, network);
        }
        for (LogisticCableTile tile : tiles) {
            tile.setConfig(network.config(), merged);
            tile.setRoles(network.roles(tile.getBlockPos().asLong()));
            w.detach.add(tile.getBlockPos().asLong());
        }
    }

    // ------------------------------------------------------------------
    // Tick : reconstructions, reseaux (budget), flux, tiles
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        World w = world(level, false);
        if (w == null) {
            return;
        }
        for (int k = 0; k < CHUNKS_PER_TICK && !w.chunks.isEmpty(); k++) {
            processChunk(level, w, w.chunks.removeFirstLong());
        }
        long t0 = System.nanoTime();
        rebuild(level, w);
        syncDetached(level, w);
        long t1 = System.nanoTime();
        w.lastRebuild = t1 - t0;
        long now = level.getGameTime();
        int n = w.all.size();
        if (n > 0) {
            int first = w.start % n, done = 0;
            for (int k = 0; k < n; k++) {
                w.all.get((first + k) % n).tick(now);
                done++;
                if (System.nanoTime() - t1 > BUDGET_NANOS) {
                    break;
                }
            }
            w.start = (first + done) % n;
        }
        w.lastTick = System.nanoTime() - t1;
        boolean flow = now % 10 == 0, slow = now % 20 == 0, save = now % 1200 == 0;
        for (LogisticNetwork network : w.all) {
            if (network.takeConfigDirty()) {
                pushConfig(level, network);
            }
            if (flow) {
                network.syncFlow((cable, tag) -> {
                    BlockPos pos = BlockPos.of(cable);
                    if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LogisticCableTile tile) {
                        tile.setFlow(tag);
                    }
                });
            }
            if (slow && network.needsPower()) {
                network.powered = powered(level, network);
            }
            if (save && network.hasBodies()) {
                //Corps des canaux de chaleur et de pression : les chunks des fiches seront sauves avec la valeur du moment
                markPlugTiles(level, network);
            }
        }
    }

    /**
     * 1.0.5-beta (lot 45, retour 63) : file des faces deconnectees : pour chaque cable a fiche charge de la file, chaque face vers un raccord
     * deconnecte perd sa fiche (masque de la tile), les autres la retrouvent ; l'etat du bloc suit (sans reconstruction du reseau : la face
     * reste une fiche latente). Hors de toute iteration des reseaux (un setBlock peut en dissoudre un).
     */
    private static void syncDetached(ServerLevel level, World w) {
        if (w.detach.isEmpty()) {
            return;
        }
        long[] queue = w.detach.toLongArray();
        w.detach.clear();
        Direction[] dirs = Direction.values();
        for (long cable : queue) {
            BlockPos pos = BlockPos.of(cable);
            LogisticNetwork network = w.byCable.get(cable);
            if (network == null || !loaded(level, w, cable) || !(level.getBlockEntity(pos) instanceof LogisticCableTile tile)) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!(state.getBlock() instanceof LogisticCableBlock block)) {
                continue;
            }
            int before = tile.detachedMask(), want = before, latent = LogisticCableBlock.plugMask(state) | before;
            for (Direction d : dirs) {
                if ((latent >> d.ordinal() & 1) == 0) {
                    continue;
                }
                LogisticNetwork.Attach a = network.attach(BlockPos.offset(cable, d));
                boolean off = a != null && a.detached();
                want = off ? want | 1 << d.ordinal() : want & ~(1 << d.ordinal());
            }
            if (want == before) {
                continue;
            }
            w.quiet.put(cable, before ^ want);
            tile.setDetachedMask(want);
            BlockState next = block.refresh(level, pos, state);
            if (next != state) {
                level.setBlock(pos, next, Block.UPDATE_ALL);
            }
            w.quiet.remove(cable);
        }
    }

    private static boolean powered(ServerLevel level, LogisticNetwork network) {
        NetworkGraph g = network.graph;
        for (int i = 0; i < g.size; i++) {
            if (g.plugs[i] != 0) {
                BlockPos pos = BlockPos.of(g.cables[i]);
                if (level.isLoaded(pos) && level.hasNeighborSignal(pos)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static void markPlugTiles(ServerLevel level, LogisticNetwork network) {
        NetworkGraph g = network.graph;
        for (int i = 0; i < g.size; i++) {
            if (g.plugs[i] != 0) {
                BlockPos pos = BlockPos.of(g.cables[i]);
                if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LogisticCableTile tile) {
                    tile.setChanged();
                }
            }
        }
    }

    /** Edition : configuration (deja partagee) a sauver dans toutes les tiles a fiche chargees, roles des fiches a jour. */
    static void pushConfig(ServerLevel level, LogisticNetwork network) {
        NetworkGraph g = network.graph;
        for (int i = 0; i < g.size; i++) {
            if (g.plugs[i] == 0) {
                continue;
            }
            BlockPos pos = BlockPos.of(g.cables[i]);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LogisticCableTile tile) {
                tile.setConfig(network.config(), true);
                tile.setRoles(network.roles(g.cables[i]));
            }
        }
        //1.0.5-beta (lot 45, retour 63) : roles changes : faces deconnectees a mettre a jour
        queueDetach(level, network);
    }

    /**
     * Lecteur du monde pour le parcours : etats des blocs charges, frontieres de claim. 1.0.5-beta (lot 45, retour 66) : dans un chunk NON
     * CHARGE, l'index ({@link CableIndex}) ; fiches = fiches de l'etat + faces deconnectees (fiches latentes, retour 63) : {@link IndexedReader}.
     */
    private static NetworkGraph.Reader reader(ServerLevel level, World w) {
        return new IndexedReader(w.index, new IndexedReader.Live() {
            @Override
            public boolean loaded(long pos) {
                return LogisticNetworks.loaded(level, w, pos);
            }

            @Override
            public int info(long pos) {
                BlockState state = level.getBlockState(BlockPos.of(pos));
                return state.getBlock() instanceof LogisticCableBlock block ? infoOf(block) : -1;
            }

            @Override
            public int cableSides(long pos) {
                BlockState state = level.getBlockState(BlockPos.of(pos));
                return state.getBlock() instanceof LogisticCableBlock ? LogisticCableBlock.cableMask(state) : 0;
            }

            @Override
            public int plugSides(long pos) {
                BlockState state = level.getBlockState(BlockPos.of(pos));
                return state.getBlock() instanceof LogisticCableBlock ? LogisticCableBlock.plugMask(state) : 0;
            }

            @Override
            public boolean canCross(long from, long to) {
                return ClanApi.canTransfer(level, BlockPos.of(from), BlockPos.of(to));
            }
        });
    }

    private static void rebuild(ServerLevel level, World w) {
        if (w.dirty.isEmpty()) {
            return;
        }
        NetworkGraph.Reader reader = reader(level, w);
        while (!w.dirty.isEmpty()) {
            long start = w.dirty.removeFirstLong();
            if (w.byCable.containsKey(start)) {
                continue;
            }
            NetworkGraph graph = NetworkGraph.build(reader, start);
            if (graph == null) {
                continue;
            }
            //Les cables trouves sortent d'un eventuel reseau encore vivant (pose directe concurrente) et de la file
            for (int i = 0; i < graph.size; i++) {
                long cable = graph.cables[i];
                LogisticNetwork old = w.byCable.get(cable);
                if (old != null) {
                    dissolve(level, w, old);
                }
                w.dirty.remove(cable);
            }
            if (graph.plugCount == 0) {
                //Un reseau sans aucune fiche n'existe pas (rien a faire) ; ses cables restent sans reseau jusqu'au prochain changement
                continue;
            }
            LogisticNetwork network = build(level, w, graph);
            w.all.add(network);
            for (int i = 0; i < graph.size; i++) {
                w.byCable.put(graph.cables[i], network);
            }
            pushRoles(level, network);
            //1.0.5-beta (lot 45, retour 63) : faces des machines deconnectees (ou reconnectees) a mettre a jour
            queueDetach(level, network);
        }
    }

    /** Raccords, configuration reunie depuis les tiles, reseau. */
    private static LogisticNetwork build(ServerLevel level, World w, NetworkGraph graph) {
        Map<Long, List<long[]>> plugs = new java.util.TreeMap<>();
        List<LogisticCableTile> tiles = new ArrayList<>();
        Map<NetworkConfig, Boolean> configs = new IdentityHashMap<>();
        Direction[] dirs = Direction.values();
        int mask = graph.level.permittedMask();
        for (int i = 0; i < graph.size; i++) {
            int p = graph.plugs[i];
            if (p == 0) {
                continue;
            }
            BlockPos cable = BlockPos.of(graph.cables[i]);
            if (!loaded(level, w, graph.cables[i])) {
                //1.0.5-beta (lot 45) : fiche dans un chunk non charge : sa configuration gardee a la dissolution d'avant (orpheline)
                NetworkConfig orphan = w.orphans.get(graph.cables[i]);
                if (orphan != null) {
                    configs.put(orphan, Boolean.TRUE);
                }
            } else if (level.getBlockEntity(cable) instanceof LogisticCableTile tile) {
                tiles.add(tile);
                if (tile.config() != null) {
                    configs.put(tile.config(), Boolean.TRUE);
                }
            }
            for (int d = 0; d < 6; d++) {
                if ((p >> d & 1) == 0) {
                    continue;
                }
                BlockPos machine = cable.relative(dirs[d]);
                //1.0.5-beta (lot 45) : une machine d'un chunk non charge reste un raccord (en pause)
                if (!ClanApi.canTransfer(level, cable, machine)) {
                    continue;
                }
                plugs.computeIfAbsent(machine.asLong(), k -> new ArrayList<>()).add(new long[]{graph.cables[i], d});
            }
        }
        List<LogisticNetwork.Attach> attaches = new ArrayList<>();
        for (Map.Entry<Long, List<long[]>> e : plugs.entrySet()) {
            List<long[]> list = e.getValue();
            list.sort(Comparator.comparingLong(a -> a[0]));
            BlockPos machine = BlockPos.of(e.getKey());
            Direction touched = dirs[(int) list.get(0)[1]].getOpposite();
            LogisticNetwork.Attach attach;
            if (loaded(level, w, e.getKey())) {
                BlockEntity tile = level.getBlockEntity(machine);
                int kinds = PortCache.kindsOf(tile, mask);
                long group = PortCache.groupOf(level, machine);
                attach = new LogisticNetwork.Attach(e.getKey(), new PortCache(level, machine, touched, kinds, group));
                //1.0.5-beta (lot 45) : fiche de la machine gardee pour quand son chunk ne sera plus charge (diagramme, reconstruction)
                BlockState state = level.getBlockState(machine);
                w.index.putMachine(e.getKey(), new CableIndex.Machine(itemId(state), PortCache.facing(state).ordinal(), kinds, group));
            } else {
                CableIndex.Machine meta = w.index.machine(e.getKey());
                attach = new LogisticNetwork.Attach(e.getKey(), new PortCache(level, machine, touched, meta == null ? 0 : meta.kinds(),
                      meta == null ? e.getKey() : meta.group()));
                attach.paused = true;
                attach.unknown = meta == null;
            }
            for (long[] plug : list) {
                attach.addPlug(plug[0], dirs[(int) plug[1]]);
            }
            attaches.add(attach);
        }
        //Reconciliation : la plus grande revision gagne ; une seule instance partagee par toutes les tiles du reseau
        NetworkConfig merged = new NetworkConfig();
        List<NetworkConfig> sources = new ArrayList<>(configs.keySet());
        for (NetworkConfig source : sources) {
            merged.merge(source);
        }
        //Le resultat est-il different de chaque source ? Seulement alors les chunks sont marques a sauver
        boolean differs = sources.size() > 1;
        for (LogisticCableTile tile : tiles) {
            tile.setConfig(merged, differs || tile.config() == null && !sources.isEmpty());
        }
        LogisticNetwork network = new LogisticNetwork(w.nextNid++, graph, attaches, merged, Accesses.INSTANCE, level.getGameTime());
        if (network.takeConfigDirty()) {
            pushConfig(level, network);
        }
        return network;
    }

    private static void pushRoles(ServerLevel level, LogisticNetwork network) {
        NetworkGraph g = network.graph;
        for (int i = 0; i < g.size; i++) {
            BlockPos pos = BlockPos.of(g.cables[i]);
            if (g.plugs[i] != 0 && level.isLoaded(pos) && level.getBlockEntity(pos) instanceof LogisticCableTile tile) {
                tile.setRoles(network.roles(g.cables[i]));
            }
        }
    }

    /** Identifiant de l'objet du bloc d'une machine (sinon du bloc) : diagramme, fiche de l'index. */
    static String itemId(BlockState state) {
        Item item = state.getBlock().asItem();
        return item == Items.AIR ? BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString() : BuiltInRegistries.ITEM.getKey(item).toString();
    }

    /** 1.0.5-beta (lot 45) : objet du bloc d'une machine : lu dans le monde si son chunk est charge, sinon dans l'index ; vide si inconnu. */
    static String machineItem(ServerLevel level, long key) {
        World w = world(level, true);
        if (w != null && loaded(level, w, key)) {
            return itemId(level.getBlockState(BlockPos.of(key)));
        }
        CableIndex.Machine m = w == null ? null : w.index.machine(key);
        return m == null ? "" : m.item();
    }

    /** 1.0.5-beta (lot 45) : facade d'une machine (index si son chunk n'est pas charge). */
    static int machineFacing(ServerLevel level, long key) {
        World w = world(level, true);
        if (w != null && loaded(level, w, key)) {
            return PortCache.facing(level.getBlockState(BlockPos.of(key))).ordinal();
        }
        CableIndex.Machine m = w == null ? null : w.index.machine(key);
        return m == null ? Direction.NORTH.ordinal() : m.facing();
    }

    // ------------------------------------------------------------------
    // Divers
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        Filter.GENERATION++;
    }

    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            synchronized (WORLDS) {
                WORLDS.remove(level);
            }
        }
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        synchronized (WORLDS) {
            WORLDS.clear();
        }
    }

    /** Commande {@code /pioneer cables profile} (spec §7.1) : les 10 reseaux les plus chers du monde du joueur. */
    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("cables").then(Commands.literal("profile").executes(ctx -> {
                  ServerLevel level = ctx.getSource().getLevel();
                  List<LogisticNetwork> top = profile(level, 10);
                  ctx.getSource().sendSuccess(() -> Component.translatable("logistic.pioneers.profile.header", networks(level).size(),
                        String.format(Locale.ROOT, "%.1f", lastTickNanos(level) / 1000.0)), false);
                  for (LogisticNetwork n : top) {
                      BlockPos at = BlockPos.of(n.graph.cables[0]);
                      ctx.getSource().sendSuccess(() -> Component.translatable("logistic.pioneers.profile.line", n.nid,
                            Component.translatable("logistic.pioneers.level." + n.level.id()), at.getX(), at.getY(), at.getZ(), n.graph.size,
                            n.attaches().size(), String.format(Locale.ROOT, "%.1f", n.costNanos() / 1000.0)), false);
                  }
                  return top.size();
              }))));
    }
}
