package fr.pioneers.logistics.transport.logistic;

import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.IntToDoubleFunction;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2b, spec §5.4) : ce que le serveur envoie au diagramme (paquet {@code Snapshot}) et ce que l'ecran lit. Pur (NBT seulement) : construit
 * par le serveur depuis {@link LogisticNetwork.Summary}, relu tel quel par le client et par le banc hors jeu.
 * <p>
 * Contenu : identite du reseau, niveau, cables, maillon faible (nombre et positions, 64 au plus), mode auto, redstone, droit d'edition, cout ;
 * canaux (definition, etat, debit, corps) ; raccords (objet a dessiner, facade, fiches, types offerts, patron du cube par type) ; points
 * (reglages, debit) ; graphe compresse (noeuds, liens sans longueur) ; disposition partagee. Rien d'autre que le reseau du cable clique (pas de
 * fuite d'informations). Les debits seuls sont rafraichis par {@link #stats} / {@link #applyStats}, une fois par seconde.
 */
public final class DiagramSnapshot {

    /** Positions du maillon faible envoyees au plus (bouton « Montrer »). */
    public static final int WEAK_MAX = 64;
    /** Pas de machine visee. */
    public static final long NO_FOCUS = Long.MIN_VALUE;

    /** Un canal tel que le diagramme le montre. */
    public static final class Channel {

        public ChannelDef def;
        public LogisticNetwork.State state = LogisticNetwork.State.ACTIVE;
        /** Debit mesure : par seconde (objets) ou par seconde de ticks cumules (autres : unites par seconde, l'ecran divise par 20). */
        public double rate;
        public boolean auto;
        public int sources, outputs;
        public boolean noOutput;
        /** Temperature (K) ou pression (bar) du corps ; NaN sinon. */
        public double body = Double.NaN;

        public int uid() {
            return def.uid;
        }
    }

    /** Un raccord (machine touchee par une fiche). */
    public static final class Machine {

        public long key, group;
        /** Objet a dessiner (id de l'objet du bloc) ; objet du groupe (controleur, bloc principal) si {@code group != key}. */
        public String item = "", groupItem = "";
        /** Facade (ordinal de {@link Direction}) : faces relatives du patron du cube. */
        public int facing = Direction.NORTH.ordinal();
        public long[] plugCables = new long[0];
        /** Face de chaque fiche, du cable vers la machine (ordinal de {@link Direction}). */
        public byte[] plugFaces = new byte[0];
        public int kinds;
        /** Patron du cube par type : 2 bits par face relative (1 insertion, 2 extraction, 3 les deux, 0 fermee). */
        public final int[] faces = new int[ChannelKind.COUNT];
        /** 1.0.5-beta (lot 45, retour 66) : machine dans un chunk non charge (en pause, grisee). */
        public boolean paused;
        /** 1.0.5-beta (lot 45, retour 63) : machine deconnectee (« Rien » dans tous les canaux : fiche retiree, grisee). */
        public boolean detached;

        /** Face de la machine touchee par la premiere fiche (vue de la machine), ordinal de {@link Direction}. */
        public int touchedFace() {
            return plugFaces.length == 0 ? Direction.DOWN.ordinal() : Direction.from3DDataValue(plugFaces[0]).getOpposite().ordinal();
        }
    }

    /** Un point (raccord x canal) : reglages et debit mesure. */
    public static final class Point {

        public long key;
        public int uid;
        public PointConfig config;
        public double rate;
    }

    public int nid;
    public CableLevel level = CableLevel.BASIC;
    public int cables, weakCount;
    public long[] weak = new long[0];
    public boolean auto, powered, canEdit;
    public double costMicros;
    /** Cable clique (position) et machine visee par Maj + clic sur une fiche ({@link #NO_FOCUS} sinon). */
    public long cable, focus = NO_FOCUS;
    public final List<Channel> channels = new ArrayList<>();
    public final List<Machine> machines = new ArrayList<>();
    public final Long2ObjectOpenHashMap<Int2ObjectOpenHashMap<Point>> points = new Long2ObjectOpenHashMap<>();
    public long[] nodeIds = new long[0];
    public boolean[] nodeMachine = new boolean[0];
    public long[] linkA = new long[0], linkB = new long[0];
    /** 1.0.5-beta (lot 45, retour 66) : longueur de chaque lien (cables de la chaine ; 0 = fiche) : longueur de cable entre deux sites. */
    public int[] linkLength = new int[0];
    public final Long2LongOpenHashMap layout = new Long2LongOpenHashMap();

    /** Informations du monde sur un raccord (serveur : bloc, facade, capacites ; banc : faux monde). */
    public interface MachineInfo {

        String item(long key);

        int facing(long key);

        int faces(long key, ChannelKind kind);
    }

    // ------------------------------------------------------------------
    // Construction (serveur)
    // ------------------------------------------------------------------

    public static DiagramSnapshot of(LogisticNetwork network, MachineInfo info, long cable, long focus, boolean canEdit) {
        LogisticNetwork.Summary s = network.summary();
        DiagramSnapshot d = new DiagramSnapshot();
        d.nid = s.nid();
        d.level = s.level();
        d.cables = s.cables();
        d.weakCount = s.weakCount();
        d.weak = network.weakCables(WEAK_MAX);
        d.auto = s.auto();
        d.powered = s.powered();
        d.canEdit = canEdit;
        d.costMicros = s.costMicros();
        d.cable = cable;
        d.focus = focus;
        for (LogisticNetwork.ChannelView v : s.channels()) {
            Channel c = new Channel();
            c.def = v.def();
            c.state = v.state();
            c.rate = v.perSecond();
            c.auto = v.auto();
            c.sources = v.sources();
            c.outputs = v.outputs();
            c.noOutput = v.noOutput();
            c.body = network.body(v.def().uid);
            d.channels.add(c);
        }
        for (LogisticNetwork.AttachView a : s.attaches()) {
            Machine m = new Machine();
            m.key = a.key();
            m.group = a.group();
            m.item = info.item(a.key());
            m.groupItem = a.group() == a.key() ? "" : info.item(a.group());
            m.facing = info.facing(a.key());
            m.plugCables = a.plugCables();
            m.plugFaces = new byte[a.plugFaces().length];
            for (int i = 0; i < m.plugFaces.length; i++) {
                m.plugFaces[i] = (byte) a.plugFaces()[i].ordinal();
            }
            m.kinds = a.kinds();
            m.paused = a.paused();
            m.detached = a.detached();
            for (ChannelKind kind : ChannelKind.all()) {
                if ((m.kinds & kind.bit()) != 0) {
                    m.faces[kind.ordinal()] = info.faces(a.key(), kind);
                }
            }
            d.machines.add(m);
        }
        for (LogisticNetwork.PointView p : s.points()) {
            Point pt = new Point();
            pt.key = p.key();
            pt.uid = p.uid();
            pt.config = p.config();
            pt.rate = p.perSecond();
            d.putPoint(pt);
        }
        List<NetworkGraph.Node> nodes = s.graph().nodes();
        d.nodeIds = new long[nodes.size()];
        d.nodeMachine = new boolean[nodes.size()];
        for (int i = 0; i < nodes.size(); i++) {
            d.nodeIds[i] = nodes.get(i).id();
            d.nodeMachine[i] = nodes.get(i).machine();
        }
        List<NetworkGraph.Link> links = s.graph().links();
        d.linkA = new long[links.size()];
        d.linkB = new long[links.size()];
        d.linkLength = new int[links.size()];
        for (int i = 0; i < links.size(); i++) {
            d.linkA[i] = links.get(i).a();
            d.linkB[i] = links.get(i).b();
            d.linkLength[i] = links.get(i).length();
        }
        d.layout.putAll(network.config().layout);
        return d;
    }

    private void putPoint(Point p) {
        points.computeIfAbsent(p.key, k -> new Int2ObjectOpenHashMap<>()).put(p.uid, p);
    }

    // ------------------------------------------------------------------
    // Lecture
    // ------------------------------------------------------------------

    @Nullable
    public Channel channel(int uid) {
        for (Channel c : channels) {
            if (c.def.uid == uid) {
                return c;
            }
        }
        return null;
    }

    @Nullable
    public Machine machine(long key) {
        for (Machine m : machines) {
            if (m.key == key) {
                return m;
            }
        }
        return null;
    }

    /** Point d'un raccord dans un canal (null : rien de regle, role « Rien »). */
    @Nullable
    public Point point(long key, int uid) {
        Int2ObjectOpenHashMap<Point> map = points.get(key);
        return map == null ? null : map.get(uid);
    }

    /** Reglages d'un point (copie ; « Rien » par defaut). */
    public PointConfig config(long key, int uid) {
        Point p = point(key, uid);
        return p == null ? new PointConfig() : p.config.copy();
    }

    public Role role(long key, int uid) {
        Point p = point(key, uid);
        return p == null ? Role.NONE : p.config.role;
    }

    /** Canaux vivants (non automatiques compris), dans l'ordre. */
    public int liveCount() {
        return channels.size();
    }

    /** Signature des reglages d'un raccord dans tous les canaux (machines identiques groupees : memes reglages partout). */
    public String settings(long key) {
        StringBuilder b = new StringBuilder();
        for (Channel c : channels) {
            Point p = point(key, c.def.uid);
            b.append(c.def.uid).append(':');
            if (p != null && p.config.role != Role.NONE) {
                CompoundTag t = p.config.save();
                t.remove("v");
                b.append(t);
            }
            b.append(';');
        }
        return b.toString();
    }

    // ------------------------------------------------------------------
    // NBT
    // ------------------------------------------------------------------

    public CompoundTag save() {
        CompoundTag t = new CompoundTag();
        t.putInt("nid", nid);
        t.putByte("lv", (byte) level.ordinal());
        t.putInt("cab", cables);
        t.putInt("wk", weakCount);
        t.putLongArray("wp", weak);
        t.putBoolean("auto", auto);
        t.putBoolean("pow", powered);
        t.putBoolean("edit", canEdit);
        t.putDouble("cost", costMicros);
        t.putLong("cable", cable);
        t.putLong("focus", focus);
        ListTag ch = new ListTag();
        for (Channel c : channels) {
            CompoundTag e = new CompoundTag();
            e.put("d", c.def.save());
            //Les canaux auto ont un uid negatif : ChannelDef.save le garde
            e.putByte("st", (byte) c.state.ordinal());
            e.putDouble("r", c.rate);
            e.putBoolean("a", c.auto);
            e.putShort("s", (short) Math.min(Short.MAX_VALUE, c.sources));
            e.putShort("o", (short) Math.min(Short.MAX_VALUE, c.outputs));
            e.putBoolean("no", c.noOutput);
            if (!Double.isNaN(c.body)) {
                e.putDouble("b", c.body);
            }
            ch.add(e);
        }
        t.put("ch", ch);
        ListTag ms = new ListTag();
        for (Machine m : machines) {
            CompoundTag e = new CompoundTag();
            e.putLong("k", m.key);
            if (m.group != m.key) {
                e.putLong("g", m.group);
                e.putString("gi", m.groupItem);
            }
            e.putString("i", m.item);
            e.putByte("f", (byte) m.facing);
            e.putLongArray("pc", m.plugCables);
            e.putByteArray("pf", m.plugFaces);
            e.putByte("kd", (byte) m.kinds);
            e.putIntArray("fc", m.faces);
            if (m.paused) {
                e.putBoolean("ps", true);
            }
            if (m.detached) {
                e.putBoolean("dt", true);
            }
            ms.add(e);
        }
        t.put("m", ms);
        ListTag pts = new ListTag();
        for (Int2ObjectOpenHashMap<Point> map : points.values()) {
            for (Point p : map.values()) {
                CompoundTag e = p.config.save();
                e.putLong("K", p.key);
                e.putInt("U", p.uid);
                e.putFloat("R", (float) p.rate);
                pts.add(e);
            }
        }
        t.put("p", pts);
        t.putLongArray("ni", nodeIds);
        byte[] nm = new byte[nodeMachine.length];
        for (int i = 0; i < nm.length; i++) {
            nm[i] = (byte) (nodeMachine[i] ? 1 : 0);
        }
        t.putByteArray("nm", nm);
        t.putLongArray("la", linkA);
        t.putLongArray("lb", linkB);
        t.putIntArray("ll", linkLength);
        long[] lay = new long[layout.size() * 2];
        int i = 0;
        for (Long2LongOpenHashMap.Entry e : layout.long2LongEntrySet()) {
            lay[i++] = e.getLongKey();
            lay[i++] = e.getLongValue();
        }
        t.putLongArray("lay", lay);
        return t;
    }

    public static DiagramSnapshot load(CompoundTag t) {
        DiagramSnapshot d = new DiagramSnapshot();
        d.nid = t.getInt("nid");
        d.level = CableLevel.byIndex(t.getByte("lv"));
        d.cables = t.getInt("cab");
        d.weakCount = t.getInt("wk");
        d.weak = t.getLongArray("wp");
        d.auto = t.getBoolean("auto");
        d.powered = t.getBoolean("pow");
        d.canEdit = t.getBoolean("edit");
        d.costMicros = t.getDouble("cost");
        d.cable = t.getLong("cable");
        d.focus = t.contains("focus") ? t.getLong("focus") : NO_FOCUS;
        ListTag ch = t.getList("ch", Tag.TAG_COMPOUND);
        LogisticNetwork.State[] states = LogisticNetwork.State.values();
        for (int i = 0; i < ch.size(); i++) {
            CompoundTag e = ch.getCompound(i);
            Channel c = new Channel();
            c.def = ChannelDef.load(e.getCompound("d"));
            c.state = states[Math.floorMod(e.getByte("st"), states.length)];
            c.rate = e.getDouble("r");
            c.auto = e.getBoolean("a");
            c.sources = e.getShort("s");
            c.outputs = e.getShort("o");
            c.noOutput = e.getBoolean("no");
            c.body = e.contains("b") ? e.getDouble("b") : Double.NaN;
            d.channels.add(c);
        }
        ListTag ms = t.getList("m", Tag.TAG_COMPOUND);
        for (int i = 0; i < ms.size(); i++) {
            CompoundTag e = ms.getCompound(i);
            Machine m = new Machine();
            m.key = e.getLong("k");
            m.group = e.contains("g") ? e.getLong("g") : m.key;
            m.groupItem = e.getString("gi");
            m.item = e.getString("i");
            m.facing = Math.floorMod(e.getByte("f"), 6);
            m.plugCables = e.getLongArray("pc");
            m.plugFaces = e.getByteArray("pf");
            m.kinds = e.getByte("kd") & 0x7F;
            int[] fc = e.getIntArray("fc");
            System.arraycopy(fc, 0, m.faces, 0, Math.min(fc.length, m.faces.length));
            m.paused = e.getBoolean("ps");
            m.detached = e.getBoolean("dt");
            d.machines.add(m);
        }
        ListTag pts = t.getList("p", Tag.TAG_COMPOUND);
        for (int i = 0; i < pts.size(); i++) {
            CompoundTag e = pts.getCompound(i);
            Point p = new Point();
            p.key = e.getLong("K");
            p.uid = e.getInt("U");
            p.config = PointConfig.load(e);
            p.rate = e.getFloat("R");
            d.putPoint(p);
        }
        d.nodeIds = t.getLongArray("ni");
        byte[] nm = t.getByteArray("nm");
        d.nodeMachine = new boolean[d.nodeIds.length];
        for (int i = 0; i < d.nodeMachine.length && i < nm.length; i++) {
            d.nodeMachine[i] = nm[i] != 0;
        }
        d.linkA = t.getLongArray("la");
        d.linkB = t.getLongArray("lb");
        if (d.linkB.length != d.linkA.length) {
            d.linkA = d.linkB = new long[0];
        }
        d.linkLength = t.getIntArray("ll");
        if (d.linkLength.length != d.linkA.length) {
            d.linkLength = new int[d.linkA.length];
        }
        long[] lay = t.getLongArray("lay");
        for (int i = 0; i + 1 < lay.length; i += 2) {
            d.layout.put(lay[i], lay[i + 1]);
        }
        return d;
    }

    // ------------------------------------------------------------------
    // Debits (paquet Stats, une fois par seconde)
    // ------------------------------------------------------------------

    /** Debits, etats et corps des canaux, debits des points (moyennes glissantes du moteur). */
    public static CompoundTag stats(LogisticNetwork network, IntToDoubleFunction body) {
        LogisticNetwork.Summary s = network.summary();
        int n = s.channels().size();
        int[] uids = new int[n];
        int[] rates = new int[n], bodies = new int[n];
        byte[] states = new byte[n];
        for (int i = 0; i < n; i++) {
            LogisticNetwork.ChannelView v = s.channels().get(i);
            uids[i] = v.def().uid;
            rates[i] = Float.floatToIntBits((float) v.perSecond());
            bodies[i] = Float.floatToIntBits((float) body.applyAsDouble(v.def().uid));
            states[i] = (byte) v.state().ordinal();
        }
        int m = s.points().size();
        long[] keys = new long[m];
        int[] puids = new int[m], prates = new int[m];
        for (int i = 0; i < m; i++) {
            LogisticNetwork.PointView p = s.points().get(i);
            keys[i] = p.key();
            puids[i] = p.uid();
            prates[i] = Float.floatToIntBits((float) p.perSecond());
        }
        CompoundTag t = new CompoundTag();
        t.putInt("nid", s.nid());
        t.putIntArray("u", uids);
        t.putIntArray("r", rates);
        t.putIntArray("b", bodies);
        t.putByteArray("s", states);
        t.putLongArray("pk", keys);
        t.putIntArray("pu", puids);
        t.putIntArray("pr", prates);
        t.putBoolean("pow", s.powered());
        t.putDouble("cost", s.costMicros());
        return t;
    }

    /** Applique un paquet Stats (canaux et points inconnus ignores). */
    public void applyStats(CompoundTag t) {
        int[] u = t.getIntArray("u"), r = t.getIntArray("r"), b = t.getIntArray("b");
        byte[] s = t.getByteArray("s");
        LogisticNetwork.State[] states = LogisticNetwork.State.values();
        for (int i = 0; i < u.length && i < r.length; i++) {
            Channel c = channel(u[i]);
            if (c != null) {
                c.rate = Float.intBitsToFloat(r[i]);
                if (i < b.length) {
                    c.body = Float.intBitsToFloat(b[i]);
                }
                if (i < s.length) {
                    c.state = states[Math.floorMod(s[i], states.length)];
                }
            }
        }
        long[] pk = t.getLongArray("pk");
        int[] pu = t.getIntArray("pu"), pr = t.getIntArray("pr");
        for (int i = 0; i < pk.length && i < pu.length && i < pr.length; i++) {
            Point p = point(pk[i], pu[i]);
            if (p != null) {
                p.rate = Float.intBitsToFloat(pr[i]);
            }
        }
        powered = t.getBoolean("pow");
        costMicros = t.getDouble("cost");
    }

    // ------------------------------------------------------------------
    // Copier / coller (spec §5.3)
    // ------------------------------------------------------------------

    /**
     * Configuration du reseau entier, rangee par type de machine (objet du bloc) : canaux (sans uid : recrees au collage) et, pour chaque type,
     * les reglages de la premiere machine de ce type (cle la plus petite). Les canaux automatiques sont copies comme des canaux reels.
     */
    public CompoundTag template() {
        CompoundTag t = new CompoundTag();
        ListTag ch = new ListTag();
        for (Channel c : channels) {
            ChannelDef def = c.def.copy();
            def.rev = 0;
            def.body = Double.NaN;
            def.partial = false;
            def.origin = "";
            ch.add(def.save());
        }
        t.put("c", ch);
        Map<String, Machine> byType = new TreeMap<>();
        for (Machine m : machines) {
            Machine before = byType.get(m.item);
            if (before == null || m.key < before.key) {
                byType.put(m.item, m);
            }
        }
        ListTag types = new ListTag();
        for (Map.Entry<String, Machine> e : byType.entrySet()) {
            ListTag ps = new ListTag();
            for (int i = 0; i < channels.size(); i++) {
                Point p = point(e.getValue().key, channels.get(i).def.uid);
                if (p != null && p.config.role != Role.NONE) {
                    CompoundTag pt = p.config.save();
                    pt.remove("v");
                    pt.putByte("i", (byte) i);
                    ps.add(pt);
                }
            }
            if (!ps.isEmpty()) {
                CompoundTag m = new CompoundTag();
                m.putString("t", e.getKey());
                m.put("p", ps);
                types.add(m);
            }
        }
        t.put("m", types);
        return t;
    }

    /** Reglages d'une machine dans tous les canaux (presse-papiers d'un point) : uid, nom et type de chaque canal pour retrouver le canal. */
    public CompoundTag pointCopy(long key) {
        ListTag list = new ListTag();
        for (Channel c : channels) {
            Point p = point(key, c.def.uid);
            CompoundTag e = p == null ? new PointConfig().save() : p.config.save();
            e.remove("v");
            e.putInt("u", c.def.uid);
            e.putString("n", c.def.name);
            e.putByte("k", (byte) c.def.kind.ordinal());
            list.add(e);
        }
        CompoundTag t = new CompoundTag();
        t.put("p", list);
        return t;
    }

    /** Reglages copies pour un canal de ce reseau : meme uid, sinon meme nom et meme type ; null si rien ne correspond. */
    @Nullable
    public static PointConfig pastedFor(CompoundTag copy, Channel target) {
        ListTag list = copy.getList("p", Tag.TAG_COMPOUND);
        CompoundTag byName = null;
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            if (e.getInt("u") == target.def.uid) {
                return PointConfig.load(e);
            }
            if (byName == null && e.getByte("k") == target.def.kind.ordinal() && e.getString("n").equals(target.def.name)) {
                byName = e;
            }
        }
        return byName == null ? null : PointConfig.load(byName);
    }

    /** Liste d'identifiants de types (objets) presents dans un modele copie (aide a l'affichage). */
    public static List<String> templateTypes(CompoundTag template) {
        List<String> out = new ArrayList<>();
        ListTag types = template.getList("m", Tag.TAG_COMPOUND);
        for (int i = 0; i < types.size(); i++) {
            out.add(types.getCompound(i).getString("t"));
        }
        return out;
    }
}
