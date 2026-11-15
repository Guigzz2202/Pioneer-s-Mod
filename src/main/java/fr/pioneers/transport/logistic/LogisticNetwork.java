package fr.pioneers.transport.logistic;

import fr.pioneers.api.heat.IHeatHandler;
import fr.pioneers.api.pressure.IPressureHandler;
import fr.pioneers.core.heat.HeatCapacitor;
import fr.pioneers.core.heat.HeatExchange;
import fr.pioneers.core.pressure.PressureExchange;
import fr.pioneers.core.pressure.PressureVolume;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.objects.Object2BooleanOpenHashMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.IntSupplier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (spec §2 a §4, §7) : un reseau logistique, moteur pur (sans monde) : canaux, points, cadences, attente croissante, repartition,
 * filtres, limites, chaleur et pression a corps commun, flux des cables transparents, edition.
 * <p>
 * Le gestionnaire du monde ({@link LogisticNetworks}) construit le graphe ({@link NetworkGraph}) et les raccords ({@link Attach} : une
 * machine, son {@link Endpoint}, ses fiches), puis appelle {@link #tick} une fois par tick. Le banc hors jeu ({@code tools/cables}) fait la
 * meme chose avec de fausses machines. Aucune allocation par tick hors des insertions effectives ({@link Access#withAmount}).
 * <p>
 * Pas de contenu interne : transit direct source -> destination dans la limite du credit du canal (debit du niveau le plus faible, ou
 * plafond du canal). Une source n'est jamais destination de son propre envoi.
 */
public final class LogisticNetwork {

    /** Acces par type (Minecraft : {@link Accesses} ; banc : faux acces). */
    public interface Toolkit {

        Access<?, ?> access(ChannelKind kind);

        EnergyAccess<?> energy();
    }

    /** Destinataire du flux des cables transparents (Minecraft : tile du cable ; banc : compteur). */
    public interface FlowSink {

        void flow(long cable, CompoundTag flow);
    }

    /**
     * Fournisseur des uid de canaux (aleatoire STRICTEMENT POSITIF ; le banc le rend deterministe). 1.12.1 (F1) : un uid negatif est
     * reserve aux canaux automatiques ({@code -1 - type}) ; avant, la moitie des canaux recevaient un uid negatif et le diagramme les prenait
     * pour des canaux automatiques : Valider, Supprimer, Monter, Dupliquer et les points etaient refuses en silence. Les uid negatifs deja
     * sauves restent valides (voir {@link #isAutoUid}).
     */
    public static IntSupplier UIDS = () -> ThreadLocalRandom.current().nextInt(1, Integer.MAX_VALUE);

    /**
     * 1.12.1 (F1) : l'uid designe-t-il le canal automatique d'un type dans ce reseau ? Vrai seulement si le reseau est en mode auto, que
     * l'uid vaut {@code -1 - type} et qu'aucun canal reel ne porte cet uid (un vieux canal reel peut avoir un uid negatif).
     */
    public boolean isAutoUid(int uid) {
        return uid < 0 && uid >= -ChannelKind.COUNT && isAuto() && config.channel(uid) == null;
    }

    /** Raccord : une machine touchee par au moins une fiche du reseau. */
    public static final class Attach {

        public final long key;
        public final Endpoint endpoint;
        /** Fiches : position du cable et face (ordinal de Direction, du cable vers la machine). */
        public final LongArrayList plugCables = new LongArrayList(1);
        public final List<Direction> plugFaces = new ArrayList<>(1);
        int index;
        /**
         * 1.0.5-beta (lot 45, retour 66) : machine dans un chunk NON CHARGE : en pause (le moteur ne la lit plus : rien n'en sort, rien n'y
         * entre) jusqu'au chargement de son chunk ; le gestionnaire ({@link LogisticNetworks}) la tient a jour.
         */
        public boolean paused;
        /** 1.0.5-beta (lot 45) : machine jamais vue chargee (types offerts inconnus) : le reseau sera reconstruit a son chargement. */
        public boolean unknown;
        /**
         * 1.0.5-beta (lot 45, retour 63) : reseau a canaux et machine reglee sur « Rien » dans TOUS les canaux : DECONNECTEE (fiches retirees
         * par le gestionnaire, plus aucun echange) ; elle reste un raccord (diagramme) pour pouvoir lui redonner un role. Calcule a chaque
         * compilation ({@link #compile}).
         */
        boolean detached;

        public Attach(long key, Endpoint endpoint) {
            this.key = key;
            this.endpoint = endpoint;
        }

        public void addPlug(long cable, Direction face) {
            plugCables.add(cable);
            plugFaces.add(face);
        }

        /** 1.0.5-beta (lot 45, retour 63) : deconnectee (« Rien » partout dans un reseau a canaux). */
        public boolean detached() {
            return detached;
        }
    }

    /** Etat d'un canal (diagramme). */
    public enum State {
        ACTIVE, SUSPENDED, WAITING, OFF
    }

    /** Un point a l'execution : raccord + reglages + compteurs. */
    static final class Slot {

        final Attach attach;
        final PointConfig cfg;
        /** Rang de la meme machine dans les sorties (exclue de son propre envoi), -1 sinon. */
        int selfOut = -1;
        int cursor;
        double rateCredit;
        long rateLast;
        long moved, window;
        double rate;
        long lastIn, lastOut;
        double demand;
        @Nullable
        Object2BooleanOpenHashMap<Object> filterCache;

        Slot(Attach attach, PointConfig cfg) {
            this.attach = attach;
            this.cfg = cfg;
        }
    }

    /** Un canal a l'execution. */
    final class Channel {

        final ChannelDef def;
        final ChannelKind kind;
        final boolean auto;
        State state = State.ACTIVE;
        Slot[] sources = new Slot[0], outs = new Slot[0];
        int[] sourceGroups = {0};
        int[] sourceCursor = {0};
        final Distributor dist = new Distributor();
        double credit, ratePerTick, creditCap;
        long lastRefill, wakeAt, fillReadAt;
        int idle;
        final Object2BooleanOpenHashMap<Object> filterCache = new Object2BooleanOpenHashMap<>();
        int filterGen = Filter.GENERATION;
        @Nullable
        HeatCapacitor heat;
        @Nullable
        PressureVolume air;
        double loss;
        long window, lastMoved;
        double rate;
        @Nullable
        Object lastResource;

        Channel(ChannelDef def, boolean auto) {
            this.def = def;
            this.kind = def.kind;
            this.auto = auto;
        }

        void refill(long now) {
            if (now > lastRefill) {
                credit = Math.min(creditCap, credit + ratePerTick * (now - lastRefill));
                lastRefill = now;
            }
        }

        boolean conditionMet() {
            return switch (def.condition) {
                case ALWAYS -> true;
                case POWERED -> powered;
                case UNPOWERED -> !powered;
            };
        }

        Object2BooleanOpenHashMap<Object> cache() {
            if (filterGen != Filter.GENERATION) {
                filterCache.clear();
                filterGen = Filter.GENERATION;
            }
            return filterCache;
        }

        void moved(long amount, long now) {
            window += amount;
            lastMoved = now;
        }

        void sleep(long now, boolean didSomething) {
            if (didSomething) {
                idle = 0;
                wakeAt = now + kind.cadence;
            } else if (kind.maxIdle <= 0) {
                wakeAt = now + kind.cadence;
            } else {
                idle = Math.min(idle + 1, 10);
                //Petit decalage par canal : les canaux au repos ne se reveillent pas tous au meme tick (pas de pic)
                wakeAt = now + Math.max(kind.cadence, Math.min(kind.maxIdle, 1L << idle)) + (def.uid & 7);
            }
        }

        void wake(long now) {
            idle = 0;
            wakeAt = Math.min(wakeAt, now);
        }
    }

    public final int nid;
    public final NetworkGraph graph;
    /**
     * Chunks traverses (ChunkPos.asLong). 1.0.5-beta (lot 45, retour 66) : le dechargement d'un chunk ne dissout plus le reseau (topologie
     * gardee par {@link CableIndex}) ; les machines de ce chunk sont mises en pause ({@link Attach#paused}).
     */
    private final it.unimi.dsi.fastutil.longs.LongOpenHashSet chunks;
    public final CableLevel level;
    private final Toolkit toolkit;
    private NetworkConfig config;
    private final List<Attach> attaches;
    private final Long2ObjectOpenHashMap<Attach> byKey = new Long2ObjectOpenHashMap<>();
    private Channel[] channels = new Channel[0];
    /** Rang du raccord par (cable, face) : graph index * 6 + face -> rang, -1 sinon. */
    private int[] plugOwner;
    public boolean powered;
    private long now;
    private boolean busy;
    private double costNanos;
    private long ticks;
    /** Derniere empreinte du flux envoye a chaque cable transparent (graph index). */
    private int[] flowHash;
    /** Reglages modifies depuis le dernier passage du gestionnaire (a ecrire dans les tiles). */
    private boolean configDirty;

    public LogisticNetwork(int nid, NetworkGraph graph, List<Attach> attaches, NetworkConfig config, Toolkit toolkit, long now) {
        this.nid = nid;
        this.graph = graph;
        this.level = graph.level;
        this.toolkit = toolkit;
        this.config = config;
        this.attaches = attaches;
        this.now = now;
        for (int i = 0; i < attaches.size(); i++) {
            Attach a = attaches.get(i);
            a.index = i;
            byKey.put(a.key, a);
        }
        plugOwner = new int[graph.size * 6];
        Arrays.fill(plugOwner, -1);
        for (Attach a : attaches) {
            for (int p = 0; p < a.plugCables.size(); p++) {
                int i = graph.index.get(a.plugCables.getLong(p));
                if (i >= 0) {
                    plugOwner[i * 6 + a.plugFaces.get(p).ordinal()] = a.index;
                }
            }
        }
        flowHash = new int[graph.size];
        chunks = graph.chunks();
        configDirty = config.mergePartials(attaches.stream().mapToLong(a -> a.key).toArray(), nextRev());
        compile(null);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    public it.unimi.dsi.fastutil.longs.LongOpenHashSet chunks() {
        return chunks;
    }

    /** Un canal depend-il de la redstone (condition alimente / non alimente) ? */
    public boolean needsPower() {
        for (Channel ch : channels) {
            if (ch.def.condition != ChannelDef.Condition.ALWAYS) {
                return true;
            }
        }
        return false;
    }

    /** Le reseau a-t-il un corps (chaleur, pression) dont la valeur change ? */
    public boolean hasBodies() {
        for (Channel ch : channels) {
            if (ch.heat != null || ch.air != null) {
                return true;
            }
        }
        return false;
    }

    /**
     * Ajout direct d'un cable nu (sans fiche, pas transparent) qui ne touche que ce reseau, d'un niveau egal ou meilleur : sans parcours ni
     * recompilation (spec §7.1 : 20 us). Les corps gardent leur contenance jusqu'a la prochaine reconstruction.
     */
    public boolean addCable(long pos, int levelOrdinal, int linked) {
        CableLevel l = CableLevel.byIndex(levelOrdinal);
        if (l.ordinal() < level.ordinal() || !l.joins(level) || graph.index.get(pos) >= 0) {
            return false;
        }
        graph.append(pos, levelOrdinal, linked);
        if (plugOwner.length < graph.cables.length * 6) {
            int old = plugOwner.length;
            plugOwner = Arrays.copyOf(plugOwner, graph.cables.length * 6);
            Arrays.fill(plugOwner, old, plugOwner.length, -1);
            flowHash = Arrays.copyOf(flowHash, graph.cables.length);
        }
        chunks.add(NetworkGraph.chunk(pos));
        return true;
    }

    /** Liaisons d'un cable du reseau mises a jour (voisin pose directement). */
    public void linkSides(long pos, int linked) {
        int i = graph.index.get(pos);
        if (i >= 0) {
            graph.sides[i] = (byte) linked;
        }
    }

    public NetworkConfig config() {
        return config;
    }

    public List<Attach> attaches() {
        return Collections.unmodifiableList(attaches);
    }

    @Nullable
    public Attach attach(long key) {
        return byKey.get(key);
    }

    /** Cout moyen d'un tick de ce reseau (moyenne glissante, nanosecondes). */
    public double costNanos() {
        return costNanos;
    }

    public boolean isAuto() {
        return config.auto && config.live().isEmpty();
    }

    /** Reglages changes (edition, fusion des canaux partiels) depuis le dernier appel : le gestionnaire les recopie dans les tiles. */
    public boolean takeConfigDirty() {
        boolean d = configDirty;
        configDirty = false;
        return d;
    }

    /** Revision pour une edition : plus grande que toutes celles connues. */
    public long nextRev() {
        return Math.max(config.maxRev() + 1, now << 4);
    }

    /**
     * Roles agreges des fiches d'un cable, pour l'apercu a la cle (spec §6.1) : 2 bits par face (Direction.ordinal) : 1 = la machine donne
     * au cable, 2 = le cable livre a la machine.
     */
    public int roles(long cable) {
        int i = graph.index.get(cable);
        if (i < 0) {
            return 0;
        }
        int mask = 0;
        for (int d = 0; d < 6; d++) {
            int owner = plugOwner[i * 6 + d];
            if (owner < 0) {
                continue;
            }
            int bits = 0;
            for (Channel ch : channels) {
                if (ch.state != State.ACTIVE) {
                    continue;
                }
                for (Slot s : ch.sources) {
                    if (s.attach.index == owner) {
                        bits |= 1;
                    }
                }
                for (Slot s : ch.outs) {
                    if (s.attach.index == owner) {
                        bits |= 2;
                    }
                }
            }
            mask |= bits << (d * 2);
        }
        return mask;
    }

    // ------------------------------------------------------------------
    // Compilation des canaux (reconstruction, edition)
    // ------------------------------------------------------------------

    /** Prepare les canaux a l'execution ; garde credit, attente, corps et mesures des canaux d'avant (meme uid). */
    private void compile(@Nullable Channel[] previous) {
        Int2ObjectOpenHashMap<Channel> old = new Int2ObjectOpenHashMap<>();
        if (previous != null) {
            for (Channel ch : previous) {
                old.put(ch.def.uid, ch);
            }
        }
        List<Channel> list = new ArrayList<>();
        if (isAuto()) {
            for (ChannelKind kind : ChannelKind.all()) {
                int count = 0;
                for (Attach a : attaches) {
                    count += (a.endpoint.kinds() & kind.bit()) != 0 ? 1 : 0;
                }
                if (count >= 2 && level.permits(kind)) {
                    //uid des canaux automatiques : negatifs, un par type (jamais sauves ; corps dans NetworkConfig.autoBody)
                    ChannelDef def = new ChannelDef(-1 - kind.ordinal(), kind);
                    def.body = config.autoBody[kind.ordinal()];
                    Channel ch = new Channel(def, true);
                    List<Slot> slots = new ArrayList<>();
                    for (Attach a : attaches) {
                        if ((a.endpoint.kinds() & kind.bit()) != 0) {
                            slots.add(new Slot(a, PointConfig.AUTO));
                        }
                    }
                    prepare(ch, slots, slots);
                    list.add(ch);
                }
            }
        } else {
            for (ChannelDef def : config.ordered()) {
                if (def.deleted) {
                    continue;
                }
                Channel ch = new Channel(def, false);
                if (!config.isActive(def)) {
                    ch.state = State.WAITING;
                } else if (!level.permits(def.kind)) {
                    ch.state = State.SUSPENDED;
                }
                List<Slot> sources = new ArrayList<>(), outs = new ArrayList<>();
                for (Attach a : attaches) {
                    PointConfig cfg = config.point(a.key, def.uid);
                    if (cfg == null || cfg.role == Role.NONE) {
                        continue;
                    }
                    Slot slot = new Slot(a, cfg);
                    if (def.kind.body()) {
                        sources.add(slot);
                        continue;
                    }
                    if (cfg.role.in()) {
                        sources.add(slot);
                    }
                    if (cfg.role.out()) {
                        outs.add(cfg.role.in() ? new Slot(a, cfg) : slot);
                    }
                }
                prepare(ch, sources, outs);
                list.add(ch);
            }
        }
        int heatChannels = 0, airChannels = 0;
        for (Channel ch : list) {
            heatChannels += ch.kind == ChannelKind.HEAT && ch.state == State.ACTIVE ? 1 : 0;
            airChannels += ch.kind == ChannelKind.PRESSURE && ch.state == State.ACTIVE ? 1 : 0;
        }
        for (Channel ch : list) {
            Channel before = old.get(ch.def.uid);
            if (before == null) {
                //Canal materialise : il reprend le credit et le corps du canal automatique de son type
                Channel auto = old.get(-1 - ch.kind.ordinal());
                before = auto != null && auto.auto ? auto : null;
            }
            long rate = level.rate(ch.kind);
            if (ch.def.cap > 0 && !ch.kind.body()) {
                rate = Math.min(rate, ch.def.cap);
            }
            ch.ratePerTick = ch.kind.perSecond() ? rate / 20.0 : rate;
            ch.creditCap = ch.ratePerTick * Math.max(2, ch.kind.cadence * 2);
            ch.lastRefill = now;
            ch.wakeAt = now;
            if (before != null) {
                ch.credit = Math.min(ch.creditCap, before.credit);
                ch.rate = before.rate;
                ch.lastMoved = before.lastMoved;
                ch.lastResource = before.lastResource;
            }
            if (ch.kind == ChannelKind.HEAT && ch.state == State.ACTIVE) {
                double capacity = graph.sum(l -> l.heatCapacity) / Math.max(1, heatChannels);
                ch.heat = new HeatCapacitor(capacity, level.rate(ChannelKind.HEAT), null);
                double temperature = before != null && before.heat != null ? before.heat.getTemperature() : ch.def.body;
                if (!Double.isNaN(temperature)) {
                    ch.heat.addHeat((temperature - ch.heat.getTemperature()) * ch.heat.getHeatCapacity());
                }
                ch.loss = graph.sum(l -> l.heatLoss) / Math.max(1, heatChannels);
            } else if (ch.kind == ChannelKind.PRESSURE && ch.state == State.ACTIVE) {
                double volume = graph.sum(l -> l.airVolume) / Math.max(1, airChannels);
                ch.air = new PressureVolume(volume, level.maxPressure, level.rate(ChannelKind.PRESSURE), null);
                double pressure = before != null && before.air != null ? before.air.getPressure() : ch.def.body;
                if (!Double.isNaN(pressure) && pressure > 0) {
                    ch.air.insertAir(pressure * ch.air.getVolume(), false);
                }
                ch.loss = graph.sum(l -> l.airLeak) / Math.max(1, airChannels);
            }
            if (before != null) {
                //Mesures par point : reprises par raccord
                for (Slot s : ch.sources) {
                    for (Slot b : before.sources) {
                        if (b.attach.key == s.attach.key) {
                            s.rate = b.rate;
                            s.cursor = b.cursor;
                        }
                    }
                }
                for (Slot s : ch.outs) {
                    for (Slot b : before.outs) {
                        if (b.attach.key == s.attach.key) {
                            s.rate = b.rate;
                        }
                    }
                }
            }
        }
        channels = list.toArray(new Channel[0]);
        markDetached();
    }

    /**
     * 1.0.5-beta (lot 45, retour 63 de Guillaume : « Il faut que les cables logistiques se deconnectent quand ils sont parametres en "rien" ») :
     * en mode auto, tout raccord est relie (« Les deux ») ; sinon un raccord sans aucun point de role autre que « Rien » dans un canal vivant
     * (actif, en attente ou suspendu) est DECONNECTE.
     */
    private void markDetached() {
        boolean auto = isAuto();
        for (Attach a : attaches) {
            boolean connected = auto;
            Int2ObjectOpenHashMap<PointConfig> points = auto ? null : config.points.get(a.key);
            if (points != null) {
                for (it.unimi.dsi.fastutil.ints.Int2ObjectMap.Entry<PointConfig> p : points.int2ObjectEntrySet()) {
                    ChannelDef def = config.channel(p.getIntKey());
                    if (def != null && !def.deleted && p.getValue().role != Role.NONE) {
                        connected = true;
                        break;
                    }
                }
            }
            a.detached = !connected;
        }
    }

    /** 1.0.5-beta (lot 45, retour 63) : le raccord est-il relie (au moins un role, ou mode auto) ? Faux s'il est inconnu. */
    public boolean connected(long key) {
        Attach a = byKey.get(key);
        return a != null && !a.detached;
    }

    /** 1.0.5-beta (lot 45, retour 66) : met un raccord en pause (chunk de la machine decharge) ou le reprend ; vrai si l'etat a change. */
    public boolean setPaused(long key, boolean paused) {
        Attach a = byKey.get(key);
        if (a == null || a.paused == paused) {
            return false;
        }
        a.paused = paused;
        if (!paused) {
            a.endpoint.wake();
            for (Channel ch : channels) {
                ch.wake(now);
            }
        }
        return true;
    }

    /** 1.0.5-beta (lot 45) : raccords en pause (machines dans un chunk non charge). */
    public int pausedCount() {
        int n = 0;
        for (Attach a : attaches) {
            n += a.paused ? 1 : 0;
        }
        return n;
    }

    /** 1.0.5-beta (lot 45, retour 63) : raccords deconnectes. */
    public int detachedCount() {
        int n = 0;
        for (Attach a : attaches) {
            n += a.detached ? 1 : 0;
        }
        return n;
    }

    /**
     * 1.0.5-beta (lot 45, retour 66) : une tile a fiche chargee apres coup (chunk recharge) apporte sa configuration : reunie a celle du reseau
     * (la plus grande revision gagne), canaux recompiles si quelque chose a change.
     *
     * @return vrai si la configuration du reseau a change
     */
    public boolean mergeConfig(NetworkConfig other) {
        if (other == config || !config.merge(other)) {
            return false;
        }
        recompile();
        return true;
    }

    /** Trie sources et sorties par priorite decroissante, prepare la repartition et les exclusions. */
    private void prepare(Channel ch, List<Slot> sources, List<Slot> outs) {
        sources.sort((a, b) -> Integer.compare(b.cfg.priority, a.cfg.priority));
        outs.sort((a, b) -> Integer.compare(b.cfg.priority, a.cfg.priority));
        ch.sources = sources.toArray(new Slot[0]);
        ch.outs = outs.toArray(new Slot[0]);
        int[] prio = new int[ch.outs.length], weights = new int[ch.outs.length];
        for (int i = 0; i < ch.outs.length; i++) {
            prio[i] = ch.outs[i].cfg.priority;
            weights[i] = ch.outs[i].cfg.weight;
        }
        ch.dist.setup(ch.outs.length, prio, weights);
        for (Slot s : ch.sources) {
            for (int i = 0; i < ch.outs.length; i++) {
                if (ch.outs[i].attach == s.attach) {
                    s.selfOut = i;
                }
            }
        }
        //Groupes de sources de meme priorite (tour a tour dans chaque groupe)
        int[] groups = new int[ch.sources.length + 1];
        int n = 0;
        for (int i = 0; i < ch.sources.length; i++) {
            if (i == 0 || ch.sources[i].cfg.priority != ch.sources[i - 1].cfg.priority) {
                groups[n++] = i;
            }
        }
        groups[n] = ch.sources.length;
        ch.sourceGroups = Arrays.copyOf(groups, n + 1);
        ch.sourceCursor = new int[Math.max(1, n)];
        for (int g = 0; g < n; g++) {
            ch.sourceCursor[g] = groups[g];
        }
    }

    // ------------------------------------------------------------------
    // Tick
    // ------------------------------------------------------------------

    /** Un tick du reseau (appele par le gestionnaire, une fois par tick du monde, dans la limite du budget). */
    public void tick(long gameTime) {
        long t0 = System.nanoTime();
        now = gameTime;
        if (!busy) {
            busy = true;
            try {
                for (Channel ch : channels) {
                    if (ch.state != State.ACTIVE || now < ch.wakeAt) {
                        continue;
                    }
                    if (!ch.conditionMet()) {
                        ch.wakeAt = now + 20;
                        continue;
                    }
                    switch (ch.kind) {
                        case HEAT -> heat(ch);
                        case PRESSURE -> pressure(ch);
                        case ENERGY -> energy(ch);
                        default -> transport(ch);
                    }
                }
                if (++ticks % 20 == 0) {
                    roll();
                }
            } finally {
                busy = false;
            }
        }
        long cost = System.nanoTime() - t0;
        costNanos = costNanos == 0 ? cost : costNanos * 0.95 + cost * 0.05;
    }

    /** Mesures glissantes (1 s) et corps sauves dans la configuration. */
    private void roll() {
        for (Channel ch : channels) {
            ch.rate = ch.window;
            ch.window = 0;
            for (Slot s : ch.sources) {
                s.rate = s.window;
                s.window = 0;
            }
            for (Slot s : ch.outs) {
                s.rate = s.window;
                s.window = 0;
            }
        }
        saveBodies();
    }

    // ------------------------------------------------------------------
    // Objets, fluides, gaz, donnees
    // ------------------------------------------------------------------

    /** Contexte de l'insertion en cours (une seule a la fois : le reseau n'est pas reentrant). */
    private final class TransportSink implements Distributor.Sink {

        Channel ch;
        Access<Object, Object> access;
        Object resource;
        long resourceAmount;
        boolean simulate;

        @Override
        public long accept(int index, long amount) {
            Slot o = ch.outs[index];
            //1.0.5-beta (lot 45) : machine en pause (chunk non charge) : rien ne lui est livre
            if (o.attach.paused) {
                return 0;
            }
            Object h = o.attach.endpoint.handler(ch.kind, o.cfg.face);
            if (h == null || !o.cfg.filter.test(access, resource, slotCache(o))) {
                return 0;
            }
            long want = Math.min(amount, rateLeft(ch, o));
            if (o.cfg.fillTo != PointConfig.UNLIMITED && want > 0) {
                want = Math.min(want, o.cfg.fillTo - access.count(h, resource));
            }
            if (want <= 0) {
                return 0;
            }
            Object part = want == resourceAmount ? resource : access.withAmount(resource, want);
            long accepted = access.insert(h, part, simulate);
            if (!simulate && accepted > 0) {
                spendRate(ch, o, accepted);
                o.window += accepted;
                o.lastOut = now;
            }
            return accepted;
        }
    }

    private final TransportSink sink = new TransportSink();

    private static Object2BooleanOpenHashMap<Object> slotCache(Slot s) {
        if (s.cfg.filter.isEmpty()) {
            return null;
        }
        if (s.filterCache == null) {
            s.filterCache = new Object2BooleanOpenHashMap<>();
        }
        return s.filterCache;
    }

    /** Reste du debit max d'un point pour ce passage (illimite : Long.MAX_VALUE). */
    private long rateLeft(Channel ch, Slot s) {
        if (s.cfg.maxRate <= 0) {
            return Long.MAX_VALUE;
        }
        double perTick = ch.kind.perSecond() ? s.cfg.maxRate / 20.0 : s.cfg.maxRate;
        if (now > s.rateLast) {
            s.rateCredit = Math.min(perTick * Math.max(2, ch.kind.cadence * 2), s.rateCredit + perTick * (now - s.rateLast));
            s.rateLast = now;
        }
        return (long) Math.floor(s.rateCredit);
    }

    private static void spendRate(Channel ch, Slot s, long amount) {
        if (s.cfg.maxRate > 0) {
            s.rateCredit -= amount;
        }
    }

    @SuppressWarnings("unchecked")
    private void transport(Channel ch) {
        ch.refill(now);
        long budget = (long) Math.floor(ch.credit);
        if (budget < 1 || ch.outs.length == 0) {
            ch.sleep(now, false);
            return;
        }
        Access<Object, Object> access = (Access<Object, Object>) toolkit.access(ch.kind);
        if (ch.def.mode == Distribution.BALANCED && now >= ch.fillReadAt) {
            readFills(ch, access);
        }
        long moved = 0;
        int[] groups = ch.sourceGroups;
        for (int g = 0; g + 1 < groups.length && budget > 0; g++) {
            int from = groups[g], to = groups[g + 1], len = to - from, start = ch.sourceCursor[g];
            for (int k = 0; k < len && budget > 0; k++) {
                int i = start + k;
                if (i >= to) {
                    i -= len;
                }
                Slot s = ch.sources[i];
                //1.0.5-beta (lot 45) : machine en pause (chunk non charge) : jamais lue
                if (!s.cfg.pull || s.attach.paused) {
                    continue;
                }
                long placed = pull(ch, s, access, Math.min(budget, rateLeft(ch, s)));
                budget -= placed;
                moved += placed;
            }
            ch.sourceCursor[g] = start + 1 >= to ? from : start + 1;
        }
        ch.credit -= moved;
        ch.sleep(now, moved > 0);
    }

    /** Extraction d'une source (une seule operation par passage), repartie aussitot. */
    private long pull(Channel ch, Slot s, Access<Object, Object> access, long max) {
        if (max <= 0) {
            return 0;
        }
        Object h = s.attach.endpoint.handler(ch.kind, s.cfg.face);
        if (h == null) {
            return 0;
        }
        int slots = access.slots(h);
        int tries = 0;
        Object2BooleanOpenHashMap<Object> cache = ch.cache();
        for (int k = 0; k < slots && tries < 8; k++) {
            int slot = (s.cursor + k) % slots;
            Object r = access.peek(h, slot);
            if (access.isEmpty(r)) {
                continue;
            }
            tries++;
            if (!access.physical(level, r) || !ch.def.filter.test(access, r, cache) || !s.cfg.filter.test(access, r, slotCache(s))) {
                continue;
            }
            long want = Math.min(max, access.amount(r));
            if (s.cfg.keep > 0) {
                want = Math.min(want, access.count(h, r) - s.cfg.keep);
            }
            if (want <= 0) {
                continue;
            }
            Object sim = access.extract(h, slot, r, want, true);
            long n = access.amount(sim);
            if (n <= 0) {
                continue;
            }
            long placed = deliver(ch, s, access, sim, n, false);
            if (placed > 0) {
                access.extract(h, slot, r, placed, false);
                s.cursor = slot;
                s.window += placed;
                s.lastIn = now;
                ch.moved(placed, now);
                ch.lastResource = sim;
                return placed;
            }
        }
        s.cursor = slots == 0 ? 0 : (s.cursor + 1) % slots;
        return 0;
    }

    private long deliver(Channel ch, Slot source, Access<Object, Object> access, Object resource, long amount, boolean simulate) {
        sink.ch = ch;
        sink.access = access;
        sink.resource = resource;
        sink.resourceAmount = access.amount(resource);
        sink.simulate = simulate;
        try {
            return ch.dist.distribute(ch.def.mode, amount, source == null ? -1 : source.selfOut, sink);
        } finally {
            sink.resource = null;
            sink.ch = null;
        }
    }

    private void readFills(Channel ch, Access<Object, Object> access) {
        ch.fillReadAt = now + 20;
        for (int i = 0; i < ch.outs.length; i++) {
            Slot o = ch.outs[i];
            Object h = o.attach.paused ? null : o.attach.endpoint.handler(ch.kind, o.cfg.face);
            ch.dist.setFill(i, h == null ? 2 : access.fill(h));
        }
    }

    // ------------------------------------------------------------------
    // Energie : demande lue en simule une fois par tick et par point, jamais d'attente
    // ------------------------------------------------------------------

    private final class EnergySink implements Distributor.Sink {

        Channel ch;
        EnergyAccess<Object> access;
        boolean simulate, demandRead;

        @Override
        public long accept(int index, long amount) {
            Slot o = ch.outs[index];
            if (demandRead) {
                amount = Math.min(amount, (long) o.demand);
            } else {
                amount = Math.min(amount, rateLeft(ch, o));
            }
            if (amount <= 0 || o.attach.paused) {
                return 0;
            }
            Object h = o.attach.endpoint.handler(ChannelKind.ENERGY, o.cfg.face);
            if (h == null) {
                return 0;
            }
            long accepted = access.receive(h, amount, simulate);
            if (!simulate && accepted > 0) {
                o.demand -= accepted;
                spendRate(ch, o, accepted);
                o.window += accepted;
                o.lastOut = now;
            }
            return accepted;
        }
    }

    private final EnergySink energySink = new EnergySink();

    @SuppressWarnings("unchecked")
    private void energy(Channel ch) {
        ch.sleep(now, true);
        ch.refill(now);
        long budget = (long) Math.floor(ch.credit);
        if (budget <= 0 || ch.outs.length == 0 || ch.sources.length == 0) {
            return;
        }
        EnergyAccess<Object> access = (EnergyAccess<Object>) toolkit.energy();
        if (ch.def.mode == Distribution.BALANCED && now >= ch.fillReadAt) {
            ch.fillReadAt = now + 20;
            for (int i = 0; i < ch.outs.length; i++) {
                Object h = ch.outs[i].attach.paused ? null : ch.outs[i].attach.endpoint.handler(ChannelKind.ENERGY, ch.outs[i].cfg.face);
                ch.dist.setFill(i, h == null ? 2 : access.fill(h));
            }
        }
        //Rien a donner (sources vides) : la demande n'est pas lue (reseau au repos : une lecture simulee par source)
        boolean supply = false;
        for (Slot s : ch.sources) {
            Object h = s.cfg.pull && !s.attach.paused ? s.attach.endpoint.handler(ChannelKind.ENERGY, s.cfg.face) : null;
            if (h != null && access.extract(h, 1, true) > 0) {
                supply = true;
                break;
            }
        }
        if (!supply) {
            return;
        }
        //Demande de chaque sortie (simulee, une fois)
        long demand = 0;
        for (Slot o : ch.outs) {
            Object h = o.attach.paused ? null : o.attach.endpoint.handler(ChannelKind.ENERGY, o.cfg.face);
            o.demand = h == null ? 0 : access.receive(h, Math.min(budget, rateLeft(ch, o)), true);
            demand += (long) o.demand;
        }
        if (demand <= 0) {
            return;
        }
        energySink.ch = ch;
        energySink.access = access;
        energySink.simulate = false;
        energySink.demandRead = true;
        long moved = 0;
        long want = Math.min(budget, demand);
        int[] groups = ch.sourceGroups;
        for (int g = 0; g + 1 < groups.length && want > 0; g++) {
            int from = groups[g], to = groups[g + 1], len = to - from, start = ch.sourceCursor[g];
            for (int k = 0; k < len && want > 0; k++) {
                int i = start + k;
                if (i >= to) {
                    i -= len;
                }
                Slot s = ch.sources[i];
                if (!s.cfg.pull || s.attach.paused) {
                    continue;
                }
                Object h = s.attach.endpoint.handler(ChannelKind.ENERGY, s.cfg.face);
                if (h == null) {
                    continue;
                }
                long give = access.extract(h, Math.min(want, rateLeft(ch, s)), true);
                if (give <= 0) {
                    continue;
                }
                long placed = ch.dist.distribute(ch.def.mode, give, s.selfOut, energySink);
                if (placed > 0) {
                    access.extract(h, placed, false);
                    spendRate(ch, s, placed);
                    s.window += placed;
                    s.lastIn = now;
                    want -= placed;
                    moved += placed;
                }
            }
            ch.sourceCursor[g] = start + 1 >= to ? from : start + 1;
        }
        energySink.ch = null;
        ch.credit -= moved;
        if (moved > 0) {
            ch.moved(moved, now);
        }
    }

    // ------------------------------------------------------------------
    // Ejection d'une machine dans une fiche (ItemInlet... de la tile)
    // ------------------------------------------------------------------

    /**
     * Une machine ejecte dans la fiche : distribue tout de suite aux sorties du premier canal du bon type ou ce raccord est Entree ou Les deux
     * (tirer ou non) et dont les filtres laissent passer, dans la limite du credit ; sinon 0.
     *
     * @return quantite acceptee
     */
    @SuppressWarnings("unchecked")
    public long offer(long key, ChannelKind kind, Object resource, boolean simulate) {
        if (busy || kind == ChannelKind.ENERGY || kind.body()) {
            return 0;
        }
        Access<Object, Object> access = (Access<Object, Object>) toolkit.access(kind);
        if (access.isEmpty(resource) || !access.physical(level, resource)) {
            return 0;
        }
        busy = true;
        try {
            for (Channel ch : channels) {
                if (ch.kind != kind || ch.state != State.ACTIVE || !ch.conditionMet()) {
                    continue;
                }
                Slot source = sourceOf(ch, key);
                if (source == null || source.attach.paused || !ch.def.filter.test(access, resource, ch.cache())
                    || !source.cfg.filter.test(access, resource, slotCache(source))) {
                    continue;
                }
                ch.refill(now);
                long amount = Math.min(access.amount(resource), Math.min((long) Math.floor(ch.credit), rateLeft(ch, source)));
                if (amount <= 0) {
                    continue;
                }
                long placed = deliver(ch, source, access, resource, amount, simulate);
                if (placed > 0) {
                    if (!simulate) {
                        ch.credit -= placed;
                        source.window += placed;
                        source.lastIn = now;
                        spendRate(ch, source, placed);
                        ch.moved(placed, now);
                        ch.lastResource = access.withAmount(resource, 1);
                    }
                    return placed;
                }
            }
            return 0;
        } finally {
            busy = false;
        }
    }

    /** Energie poussee par une machine dans sa fiche (EnergyPush, generateurs). */
    @SuppressWarnings("unchecked")
    public long offerEnergy(long key, long amount, boolean simulate) {
        if (busy || amount <= 0) {
            return 0;
        }
        busy = true;
        try {
            EnergyAccess<Object> access = (EnergyAccess<Object>) toolkit.energy();
            for (Channel ch : channels) {
                if (ch.kind != ChannelKind.ENERGY || ch.state != State.ACTIVE || !ch.conditionMet()) {
                    continue;
                }
                Slot source = sourceOf(ch, key);
                if (source == null || source.attach.paused) {
                    continue;
                }
                ch.refill(now);
                long want = Math.min(amount, Math.min((long) Math.floor(ch.credit), rateLeft(ch, source)));
                if (want <= 0) {
                    continue;
                }
                energySink.ch = ch;
                energySink.access = access;
                energySink.simulate = simulate;
                energySink.demandRead = false;
                long placed = ch.dist.distribute(ch.def.mode, want, source.selfOut, energySink);
                energySink.ch = null;
                if (placed > 0) {
                    if (!simulate) {
                        ch.credit -= placed;
                        source.window += placed;
                        source.lastIn = now;
                        spendRate(ch, source, placed);
                        ch.moved(placed, now);
                    }
                    return placed;
                }
            }
            return 0;
        } finally {
            busy = false;
        }
    }

    @Nullable
    private static Slot sourceOf(Channel ch, long key) {
        for (Slot s : ch.sources) {
            if (s.attach.key == key && s.cfg.role.in()) {
                return s;
            }
        }
        return null;
    }

    /** Le raccord peut-il ejecter ce type dans ses fiches (un canal ou il donne) ? Sert aux capacites exposees par la tile. */
    public boolean accepts(long key, ChannelKind kind) {
        for (Channel ch : channels) {
            if (ch.kind == kind && ch.state == State.ACTIVE && sourceOf(ch, key) != null) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Chaleur et pression : corps commun par canal
    // ------------------------------------------------------------------

    private void heat(Channel ch) {
        HeatCapacitor body = ch.heat;
        if (body == null) {
            return;
        }
        double total = 0;
        for (Slot s : ch.sources) {
            Object o = s.attach.paused ? null : s.attach.endpoint.handler(ChannelKind.HEAT, s.cfg.face);
            if (!(o instanceof IHeatHandler machine)) {
                continue;
            }
            double dT = machine.getTemperature() - body.getTemperature();
            //Clapets : entree = de la machine vers le cable seulement ; sortie = du cable vers la machine ; au-dela de la temperature max du
            //niveau, plus rien n'entre
            if (dT > 0 && (!s.cfg.role.in() || body.getTemperature() >= level.maxTemperature) || dT < 0 && !s.cfg.role.out()) {
                continue;
            }
            double flow = HeatExchange.exchange(machine, body);
            if (flow != 0) {
                total += Math.abs(flow);
                if (flow > 0) {
                    s.lastIn = now;
                } else {
                    s.lastOut = now;
                }
                s.window += (long) Math.abs(flow);
            }
        }
        //Pertes a l'air sur tout le temps ecoule depuis le passage precedent (un canal a l'equilibre attend jusqu'a 20 ticks)
        HeatExchange.ambient(body, ch.loss * Math.max(1, now - ch.lastRefill));
        ch.lastRefill = now;
        if (total > 0.5) {
            ch.moved((long) total, now);
        }
        ch.sleep(now, total > 0.01);
    }

    private void pressure(Channel ch) {
        PressureVolume body = ch.air;
        if (body == null) {
            return;
        }
        double total = 0;
        for (Slot s : ch.sources) {
            Object o = s.attach.paused ? null : s.attach.endpoint.handler(ChannelKind.PRESSURE, s.cfg.face);
            if (!(o instanceof IPressureHandler machine)) {
                continue;
            }
            double flow = PressureExchange.exchange(machine, body, s.cfg.role.in(), s.cfg.role.out());
            if (flow != 0) {
                total += Math.abs(flow);
                if (flow > 0) {
                    s.lastIn = now;
                } else {
                    s.lastOut = now;
                }
                s.window += (long) Math.ceil(Math.abs(flow));
            }
        }
        PressureExchange.leak(body, ch.loss * Math.max(1, now - ch.lastRefill));
        ch.lastRefill = now;
        if (total > 0.5) {
            ch.moved((long) total, now);
        }
        ch.sleep(now, total > 0.01);
    }

    // ------------------------------------------------------------------
    // Flux des cables transparents (spec §6.2) : toutes les 10 ticks, seulement vers les transparents, seulement si change
    // ------------------------------------------------------------------

    /**
     * Calcule le flux montre par chaque cable transparent : par type actif (au plus 7) : intensite 1..4, cotes d'arrivee et de depart (sens du
     * flux depuis les sources actives), ressource montree. Envoie seulement les changements.
     */
    @SuppressWarnings("unchecked")
    public void syncFlow(FlowSink sinkOut) {
        if (graph.clear == 0) {
            return;
        }
        int n = graph.size;
        int[][] in = new int[ChannelKind.COUNT][], out = new int[ChannelKind.COUNT][];
        int[] strength = new int[ChannelKind.COUNT];
        Object[] shown = new Object[ChannelKind.COUNT];
        double[] bodyValue = new double[ChannelKind.COUNT];
        for (Channel ch : channels) {
            if (ch.state != State.ACTIVE) {
                continue;
            }
            int k = ch.kind.ordinal();
            boolean active = now - ch.lastMoved <= 20;
            if (ch.heat != null && ch.heat.getTemperature() > 320) {
                active = true;
                bodyValue[k] = Math.max(bodyValue[k], ch.heat.getTemperature());
            }
            if (ch.air != null && ch.air.getPressure() > 0.05) {
                active = true;
                bodyValue[k] = Math.max(bodyValue[k], ch.air.getPressure());
            }
            if (!active) {
                continue;
            }
            double full = ch.ratePerTick * 20;
            double ratio = full <= 0 ? 0 : ch.rate / full;
            strength[k] = Math.max(strength[k], ratio > 0.75 ? 4 : ratio > 0.4 ? 3 : ratio > 0.1 ? 2 : 1);
            if (shown[k] == null && ch.lastResource != null) {
                shown[k] = ch.lastResource;
            }
            if (in[k] == null) {
                in[k] = new int[n];
                out[k] = new int[n];
            }
            directions(ch, in[k], out[k]);
        }
        for (int i = 0; i < n; i++) {
            if ((graph.info[i] & NetworkGraph.CLEAR) == 0) {
                continue;
            }
            ListTag list = new ListTag();
            int hash = 1;
            for (int k = 0; k < ChannelKind.COUNT; k++) {
                if (strength[k] == 0) {
                    continue;
                }
                CompoundTag t = new CompoundTag();
                t.putByte("k", (byte) k);
                t.putByte("s", (byte) strength[k]);
                t.putByte("i", (byte) in[k][i]);
                t.putByte("o", (byte) out[k][i]);
                ChannelKind kind = ChannelKind.byIndex(k);
                if (kind.body()) {
                    t.putFloat("v", (float) (kind == ChannelKind.HEAT ? Math.round(bodyValue[k] / 25) * 25 : Math.round(bodyValue[k] * 4) / 4.0));
                    if (kind == ChannelKind.PRESSURE) {
                        t.putFloat("m", (float) level.maxPressure);
                    }
                } else if (shown[k] != null && kind != ChannelKind.ENERGY) {
                    CompoundTag snap = ((Access<Object, Object>) toolkit.access(kind)).snapshot(shown[k]);
                    if (!snap.isEmpty()) {
                        t.put("r", snap);
                    }
                }
                list.add(t);
                hash = hash * 31 + t.hashCode();
            }
            if (flowHash[i] != hash) {
                flowHash[i] = hash;
                CompoundTag tag = new CompoundTag();
                if (!list.isEmpty()) {
                    tag.put("l", list);
                }
                sinkOut.flow(graph.cables[i], tag);
            }
        }
    }

    /** Sens du flux d'un canal : distance aux sources actives (parcours en largeur sur les cables) ; arrivee = cotes plus proches. */
    private void directions(Channel ch, int[] in, int[] out) {
        int n = graph.size;
        int[] dist = new int[n];
        Arrays.fill(dist, Integer.MAX_VALUE);
        int[] queue = new int[n];
        int head = 0, tail = 0;
        boolean bodies = ch.kind.body();
        long recent = now - 20;
        boolean[] sourceAttach = new boolean[attaches.size()], destAttach = new boolean[attaches.size()];
        for (Slot s : ch.sources) {
            if (bodies || s.lastIn >= recent) {
                sourceAttach[s.attach.index] = true;
            }
            if (bodies || s.lastOut >= recent) {
                destAttach[s.attach.index] = true;
            }
        }
        for (Slot s : ch.outs) {
            if (s.lastOut >= recent) {
                destAttach[s.attach.index] = true;
            }
        }
        for (int i = 0; i < n; i++) {
            for (int d = 0; d < 6; d++) {
                int owner = plugOwner[i * 6 + d];
                if (owner < 0) {
                    continue;
                }
                if (sourceAttach[owner]) {
                    in[i] |= 1 << d;
                    if (dist[i] != 0) {
                        dist[i] = 0;
                        queue[tail++] = i;
                    }
                }
                if (destAttach[owner]) {
                    out[i] |= 1 << d;
                }
            }
        }
        Direction[] dirs = Direction.values();
        while (head < tail) {
            int i = queue[head++];
            for (int d = 0; d < 6; d++) {
                if ((graph.sides[i] >> d & 1) == 0) {
                    continue;
                }
                int j = graph.index.get(BlockPos.offset(graph.cables[i], dirs[d]));
                if (j >= 0 && dist[j] == Integer.MAX_VALUE) {
                    dist[j] = dist[i] + 1;
                    queue[tail++] = j;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (dist[i] == Integer.MAX_VALUE) {
                continue;
            }
            for (int d = 0; d < 6; d++) {
                if ((graph.sides[i] >> d & 1) == 0) {
                    continue;
                }
                int j = graph.index.get(BlockPos.offset(graph.cables[i], dirs[d]));
                if (j < 0 || dist[j] == Integer.MAX_VALUE) {
                    continue;
                }
                if (dist[j] < dist[i]) {
                    in[i] |= 1 << d;
                } else if (dist[j] > dist[i]) {
                    out[i] |= 1 << d;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Edition (diagramme, D2b) et migration (D2c)
    // ------------------------------------------------------------------

    /**
     * Materialise les canaux automatiques en canaux reels (premiere edition, spec §2) : un canal par type present, chaque raccord qui
     * l'offre en « Les deux, sans tirer ». Sans effet si le reseau n'est plus en mode auto.
     */
    public void materialize(long rev) {
        if (!isAuto()) {
            return;
        }
        for (Channel ch : channels) {
            if (!ch.auto) {
                continue;
            }
            ChannelDef def = new ChannelDef(UIDS.getAsInt(), ch.kind);
            def.color = ch.kind.ordinal();
            def.rev = rev;
            def.body = ch.heat != null ? ch.heat.getTemperature() : ch.air != null ? ch.air.getPressure() : Double.NaN;
            config.channels.add(def);
            for (Slot s : ch.sources) {
                PointConfig p = new PointConfig(Role.BOTH, false);
                p.rev = rev;
                config.setPoint(s.attach.key, def.uid, p);
            }
        }
        config.auto = false;
        config.autoRev = rev;
        recompile();
    }

    /** Ajoute ou remplace un canal (materialise d'abord le mode auto). Les points d'un canal dont le type change sont gardes. */
    public void putChannel(ChannelDef def) {
        long rev = nextRev();
        materialize(rev);
        ChannelDef copy = def.copy().clamp();
        copy.deleted = false;
        copy.rev = rev;
        if (copy.uid == 0) {
            copy.uid = UIDS.getAsInt();
        }
        ChannelDef before = config.channel(copy.uid);
        if (before != null && Double.isNaN(copy.body)) {
            copy.body = before.body;
        }
        config.putChannel(copy);
        config.auto = false;
        config.autoRev = Math.max(config.autoRev, rev);
        recompile();
        def.uid = copy.uid;
    }

    public void deleteChannel(int uid) {
        long rev = nextRev();
        materialize(rev);
        config.delete(uid, rev);
        recompile();
    }

    /** Reglages d'un point (raccord, canal). */
    public void putPoint(long key, int uid, PointConfig cfg) {
        long rev = nextRev();
        materialize(rev);
        PointConfig copy = cfg.copy().clamp();
        copy.rev = rev;
        config.setPoint(key, uid, copy);
        recompile();
    }

    /** 1.11 (D2b) : memes reglages pour plusieurs raccords (groupe du diagramme), une seule revision. Raccords inconnus ignores. */
    public int putPoints(long[] keys, int uid, PointConfig cfg) {
        long rev = nextRev();
        materialize(rev);
        int done = 0;
        for (long key : keys) {
            if (byKey.containsKey(key)) {
                PointConfig copy = cfg.copy().clamp();
                copy.rev = rev;
                config.setPoint(key, uid, copy);
                done++;
            }
        }
        recompile();
        return done;
    }

    /**
     * 1.11 (D2b) : deplace un canal d'un rang dans la liste ({@code delta} -1 = vers le haut). Les rangs sont renumerotes 0..n-1 ; seuls les
     * canaux dont le rang change prennent une nouvelle revision.
     */
    public boolean moveChannel(int uid, int delta) {
        long rev = nextRev();
        materialize(rev);
        List<ChannelDef> live = new ArrayList<>();
        for (ChannelDef def : config.ordered()) {
            if (!def.deleted) {
                live.add(def);
            }
        }
        int i = -1;
        for (int k = 0; k < live.size(); k++) {
            if (live.get(k).uid == uid) {
                i = k;
            }
        }
        int j = i + Integer.signum(delta);
        if (i < 0 || j < 0 || j >= live.size()) {
            return false;
        }
        live.add(j, live.remove(i));
        for (int k = 0; k < live.size(); k++) {
            ChannelDef def = live.get(k);
            if (def.order != k) {
                def.order = k;
                def.rev = rev;
            }
        }
        recompile();
        return true;
    }

    /** 1.11 (D2b) : duplique un canal et ses points (nouvel uid, rendu ; 0 si le canal n'existe pas). Nom suivi de « 2 ». */
    public int duplicateChannel(int uid) {
        long rev = nextRev();
        materialize(rev);
        ChannelDef from = config.channel(uid);
        if (from == null || from.deleted) {
            return 0;
        }
        ChannelDef copy = from.copy();
        copy.uid = UIDS.getAsInt();
        copy.rev = rev;
        copy.partial = false;
        copy.origin = "";
        copy.body = Double.NaN;
        copy.order = from.order;
        String base = copy.name.isEmpty() ? "" : copy.name;
        copy.name = base.isEmpty() ? "" : (base.length() > ChannelDef.NAME_MAX - 2 ? base.substring(0, ChannelDef.NAME_MAX - 2) : base) + " 2";
        config.channels.add(copy);
        for (Attach a : attaches) {
            PointConfig p = config.point(a.key, uid);
            if (p != null) {
                PointConfig q = p.copy();
                q.rev = rev;
                config.setPoint(a.key, copy.uid, q);
            }
        }
        recompile();
        return copy.uid;
    }

    /** 1.11 (D2b) : uid reel du canal materialise a la place du canal automatique {@code autoUid} (meme type) ; l'uid tel quel sinon. */
    public int realUid(int autoUid) {
        if (!isAutoUid(autoUid)) {
            return autoUid;
        }
        materialize(nextRev());
        ChannelKind kind = ChannelKind.byIndex(-1 - autoUid);
        for (ChannelDef def : config.ordered()) {
            if (!def.deleted && def.kind == kind) {
                return def.uid;
            }
        }
        return 0;
    }

    /** 1.11 (D2b) : positions des cables du niveau le plus faible quand le reseau melange des niveaux (maillon faible, « Montrer »). */
    public long[] weakCables(int max) {
        if (graph.weakCount() == 0) {
            return new long[0];
        }
        LongArrayList out = new LongArrayList();
        for (int i = 0; i < graph.size && out.size() < max; i++) {
            if ((graph.info[i] & NetworkGraph.LEVEL_MASK) == level.ordinal()) {
                out.add(graph.cables[i]);
            }
        }
        return out.toLongArray();
    }

    /** « Repasser en auto » : efface tous les canaux (tombes) et points. */
    public void resetAuto() {
        long rev = nextRev();
        for (ChannelDef def : config.live()) {
            config.delete(def.uid, rev);
        }
        config.points.clear();
        config.auto = true;
        config.autoRev = rev;
        recompile();
    }

    /** Deplace un noeud du diagramme (disposition partagee). */
    public void moveNode(long node, int x, int y) {
        config.layout.put(node, (long) x << 32 | (y & 0xFFFFFFFFL));
        config.layoutRev = nextRev();
        configDirty = true;
    }

    /** 1.12.1 (F1) : oublie les places deplacees a la main (« Reorganiser ») ; la revision plus recente l'emporte a la reunion. */
    public void clearLayout() {
        config.layout.clear();
        config.layoutRev = nextRev();
        configDirty = true;
    }

    /** 1.0.5-beta (lot 45, retour 66) : « Reorganiser » d'un site : oublie les places de ces noeuds seulement. */
    public void clearLayout(long[] nodes) {
        for (long n : nodes) {
            config.layout.remove(n);
        }
        config.layoutRev = nextRev();
        configDirty = true;
    }

    /** Remplace toute la configuration (coller un reseau entier, D2b) ; revisions renouvelees. */
    public void replaceConfig(NetworkConfig replacement) {
        long rev = nextRev();
        NetworkConfig next = replacement.copy();
        for (ChannelDef def : config.channels) {
            if (next.channel(def.uid) == null) {
                next.channels.add(def.copy());
                next.delete(def.uid, rev);
            }
        }
        for (ChannelDef def : next.channels) {
            def.rev = rev;
        }
        next.points.values().forEach(m -> m.values().forEach(p -> p.rev = rev));
        next.autoRev = rev;
        config = next;
        recompile();
    }

    private void recompile() {
        config.dropDeadPoints();
        compile(channels);
        configDirty = true;
    }

    // ------------------------------------------------------------------
    // Resume pour le diagramme (D2b)
    // ------------------------------------------------------------------

    /** Vue d'un canal : definition (copie), etat, debit mesure (par seconde), auto. */
    public record ChannelView(ChannelDef def, State state, double perSecond, boolean auto, int sources, int outputs, boolean noOutput) {
    }

    /** Vue d'un point : raccord, canal, reglages (copie), debit mesure par seconde. */
    public record PointView(long key, int uid, PointConfig config, double perSecond) {
    }

    /** Vue d'un raccord : cle, fiches, types offerts. */
    public record AttachView(long key, long group, long[] plugCables, Direction[] plugFaces, int kinds, boolean paused, boolean detached) {
    }

    /**
     * Resume du reseau pour le diagramme (lecture seule, copies) : niveau, cables, maillon faible, mode auto, canaux, points, raccords,
     * graphe compresse, cout.
     */
    public record Summary(int nid, CableLevel level, int cables, int weakCount, boolean auto, boolean powered, List<ChannelView> channels,
                          List<PointView> points, List<AttachView> attaches, NetworkGraph.Compressed graph, double costMicros) {
    }

    public Summary summary() {
        List<ChannelView> views = new ArrayList<>();
        List<PointView> points = new ArrayList<>();
        for (Channel ch : channels) {
            views.add(new ChannelView(ch.def.copy(), ch.state, ch.rate, ch.auto, ch.sources.length, ch.outs.length,
                  !ch.kind.body() && ch.outs.length == 0));
            for (Slot s : ch.sources) {
                points.add(new PointView(s.attach.key, ch.def.uid, s.cfg.copy(), s.rate));
            }
            for (Slot s : ch.outs) {
                if (!s.cfg.role.in()) {
                    points.add(new PointView(s.attach.key, ch.def.uid, s.cfg.copy(), s.rate));
                }
            }
        }
        //Channels en attente (au-dela de 16) et suspendus : deja dans la liste ; tombes exclues
        List<AttachView> av = new ArrayList<>();
        for (Attach a : attaches) {
            av.add(new AttachView(a.key, a.endpoint.group(), a.plugCables.toLongArray(), a.plugFaces.toArray(new Direction[0]), a.endpoint.kinds(),
                  a.paused, a.detached));
        }
        NetworkGraph.Compressed compressed = graph.compress((cable, face) -> {
            int i = graph.index.get(cable);
            int owner = i < 0 ? -1 : plugOwner[i * 6 + face.ordinal()];
            return owner < 0 ? Long.MIN_VALUE : attaches.get(owner).key;
        });
        return new Summary(nid, level, graph.size, graph.weakCount(), isAuto(), powered, views, points, av, compressed, costNanos / 1000.0);
    }

    /** Debit mesure d'un canal (par seconde ; diagramme, banc). */
    public double measured(int uid) {
        for (Channel ch : channels) {
            if (ch.def.uid == uid) {
                return ch.rate;
            }
        }
        return 0;
    }

    /** Etat d'un canal (null s'il n'existe pas). */
    @Nullable
    public State state(int uid) {
        for (Channel ch : channels) {
            if (ch.def.uid == uid) {
                return ch.state;
            }
        }
        return null;
    }

    /** Temperature (K) ou pression (bar) du corps d'un canal ; NaN si pas de corps. */
    public double body(int uid) {
        for (Channel ch : channels) {
            if (ch.def.uid == uid) {
                return ch.heat != null ? ch.heat.getTemperature() : ch.air != null ? ch.air.getPressure() : Double.NaN;
            }
        }
        return Double.NaN;
    }

    /** uid des canaux a l'execution (auto compris : negatifs). */
    public int[] channelUids() {
        int[] out = new int[channels.length];
        for (int i = 0; i < channels.length; i++) {
            out[i] = channels[i].def.uid;
        }
        return out;
    }

    /** Copie la valeur des corps dans la configuration (avant une sauvegarde ou une dissolution). */
    public void saveBodies() {
        for (Channel ch : channels) {
            double value = ch.heat != null ? ch.heat.getTemperature() : ch.air != null ? ch.air.getPressure() : Double.NaN;
            if (Double.isNaN(value)) {
                continue;
            }
            if (ch.auto) {
                config.autoBody[ch.kind.ordinal()] = value;
            } else {
                ch.def.body = value;
            }
        }
    }
}
