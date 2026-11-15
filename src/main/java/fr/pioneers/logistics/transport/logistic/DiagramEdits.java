package fr.pioneers.logistics.transport.logistic;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.11 (D2b, spec §5.4) : operations d'edition du diagramme envoyees par le client ({@code Edit}), verifiees et appliquees au reseau. Pur
 * (reseau + NBT) : le banc hors jeu l'execute tel quel ; la session serveur ({@link DiagramService}) fournit les droits et le type des machines
 * puis ecrit la configuration dans les tiles.
 * <p>
 * Une operation = un {@link CompoundTag} {@code {op: "...", ...}} :
 * <ul>
 *     <li>{@code ch} : creer (uid 0) ou remplacer un canal ({@code d} = {@link ChannelDef}) ; un uid negatif (canal automatique) materialise
 *     d'abord le mode auto ;</li>
 *     <li>{@code del}, {@code dup}, {@code mv} ({@code d} = -1 / +1) : supprimer, dupliquer (points compris), reordonner un canal ;</li>
 *     <li>{@code pt} : reglages {@code c} d'un point pour les raccords {@code k} (une machine, ou un groupe : « Appliquer au groupe ») ;</li>
 *     <li>{@code node} : deplacer un noeud ({@code i}, {@code x}, {@code y}) ou oublier toutes les places ({@code clear}, 1.12.1 ; 1.0.5-beta
 *     lot 45 : {@code clear} + {@code ia} = seulement ces noeuds, « Reorganiser » d'un site) ; {@code auto} : repasser en auto ; {@code paste} : coller un
 *     reseau entier ({@code t} = {@link DiagramSnapshot#template}).</li>
 * </ul>
 * Bornes : nom 24 caracteres sans controle, 16 canaux vivants pour en creer un, filtres 9 / 5 entrees (lus par {@link Filter#load}), 256
 * raccords par operation, disposition 4 096 noeuds, coordonnees +-1 000 000.
 */
public final class DiagramEdits {

    public static final int MAX_KEYS = 256, MAX_LAYOUT = 4096, MAX_COORD = 1_000_000;

    /** Ce que la session sait du monde : droit de modifier une machine, type d'une machine (objet de son bloc). */
    public interface Context {

        boolean canEditPoint(long key);

        String machineType(long key);
    }

    public enum Result {
        /** Applique. */
        OK,
        /** Refuse : droits (claim) sur une machine. */
        DENIED,
        /** Refuse : operation mal formee, canal inconnu, trop de canaux. */
        INVALID
    }

    /** Resultat : etat et uid du canal cree ou touche (selection de l'ecran). */
    public record Outcome(Result result, int uid) {

        static final Outcome INVALID = new Outcome(Result.INVALID, 0);
        static final Outcome DENIED = new Outcome(Result.DENIED, 0);
    }

    private DiagramEdits() {
    }

    public static Outcome apply(LogisticNetwork net, CompoundTag op, Context ctx) {
        return switch (op.getString("op")) {
            case "ch" -> channel(net, op.getCompound("d"));
            case "del" -> {
                int uid = live(net, op.getInt("u"));
                if (uid == 0) {
                    yield Outcome.INVALID;
                }
                net.deleteChannel(uid);
                yield new Outcome(Result.OK, 0);
            }
            case "dup" -> {
                int uid = live(net, op.getInt("u"));
                if (uid == 0 || liveCount(net) >= NetworkConfig.MAX_CHANNELS) {
                    yield Outcome.INVALID;
                }
                yield new Outcome(Result.OK, net.duplicateChannel(uid));
            }
            case "mv" -> {
                int uid = live(net, op.getInt("u"));
                int d = Integer.signum(op.getInt("d"));
                yield uid != 0 && d != 0 && net.moveChannel(uid, d) ? new Outcome(Result.OK, uid) : Outcome.INVALID;
            }
            case "pt" -> points(net, op, ctx);
            case "node" -> node(net, op);
            case "auto" -> {
                if (!allMachines(net, ctx)) {
                    yield Outcome.DENIED;
                }
                net.resetAuto();
                yield new Outcome(Result.OK, 0);
            }
            case "paste" -> paste(net, op.getCompound("t"), ctx);
            default -> Outcome.INVALID;
        };
    }

    /** Texte libre recu d'un client : longueur bornee, sans caracteres de controle ni code de format. */
    public static String sanitize(String text, int max) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < text.length() && b.length() < max; i++) {
            char c = text.charAt(i);
            if (c >= ' ' && c != '\u007f' && c != '§') {
                b.append(c);
            }
        }
        return b.toString().trim();
    }

    private static int liveCount(LogisticNetwork net) {
        return net.config().live().size();
    }

    /**
     * uid d'un canal vivant (un uid de canal automatique est materialise) ; 0 si inconnu. 1.12.1 (F1) : le canal reel est cherche D'ABORD
     * (les canaux crees avant la 1.12.1 ont un uid negatif une fois sur deux : ils etaient pris pour des canaux automatiques et toute edition
     * etait refusee) ; seul un uid {@code -1 - type} d'un reseau en mode auto est un canal automatique.
     */
    static int live(LogisticNetwork net, int uid) {
        if (uid == 0) {
            return 0;
        }
        if (net.isAutoUid(uid)) {
            return net.realUid(uid);
        }
        ChannelDef def = net.config().channel(uid);
        return def == null || def.deleted ? 0 : uid;
    }

    private static Outcome channel(LogisticNetwork net, CompoundTag tag) {
        if (tag.isEmpty()) {
            return Outcome.INVALID;
        }
        ChannelDef def = ChannelDef.load(tag);
        def.name = sanitize(def.name, ChannelDef.NAME_MAX);
        def.partial = false;
        def.origin = "";
        def.body = Double.NaN;
        if (def.uid == 0) {
            if (liveCount(net) >= NetworkConfig.MAX_CHANNELS && !net.isAuto()) {
                return Outcome.INVALID;
            }
            int max = -1;
            for (ChannelDef d : net.config().live()) {
                max = Math.max(max, d.order);
            }
            def.order = max + 1;
        } else {
            int uid = live(net, def.uid);
            if (uid == 0) {
                return Outcome.INVALID;
            }
            def.uid = uid;
            ChannelDef before = net.config().channel(uid);
            def.order = before.order;
        }
        net.putChannel(def);
        return new Outcome(Result.OK, def.uid);
    }

    private static Outcome points(LogisticNetwork net, CompoundTag op, Context ctx) {
        long[] keys = op.getLongArray("k");
        if (keys.length == 0 || keys.length > MAX_KEYS || !op.contains("c", Tag.TAG_COMPOUND)) {
            return Outcome.INVALID;
        }
        LongArrayList allowed = new LongArrayList();
        boolean denied = false;
        for (long key : keys) {
            if (net.attach(key) == null) {
                continue;
            }
            if (!ctx.canEditPoint(key)) {
                denied = true;
                continue;
            }
            if (!allowed.contains(key)) {
                allowed.add(key);
            }
        }
        if (allowed.isEmpty()) {
            return denied ? Outcome.DENIED : Outcome.INVALID;
        }
        int uid = live(net, op.getInt("u"));
        if (uid == 0) {
            return Outcome.INVALID;
        }
        PointConfig cfg = PointConfig.load(op.getCompound("c"));
        net.putPoints(allowed.toLongArray(), uid, cfg);
        return new Outcome(denied ? Result.DENIED : Result.OK, uid);
    }

    /** Deplace un ou plusieurs noeuds ({@code i x y}, ou tableaux {@code ia xa ya} de 256 au plus : premiere disposition gardee). */
    private static Outcome node(LogisticNetwork net, CompoundTag op) {
        if (op.getBoolean("clear")) {
            //1.12.1 (F1) : « Reorganiser » oublie les places deplacees a la main (disposition partagee) ; 1.0.5-beta (lot 45, retour 66) :
            //« Reorganiser » d'un SITE : seulement les noeuds de ce site (ia, 4 096 au plus)
            if (op.contains("ia")) {
                long[] ids = op.getLongArray("ia");
                if (ids.length == 0 || ids.length > MAX_LAYOUT) {
                    return Outcome.INVALID;
                }
                net.clearLayout(ids);
                return new Outcome(Result.OK, 0);
            }
            net.clearLayout();
            return new Outcome(Result.OK, 0);
        }
        long[] ids = op.contains("ia") ? op.getLongArray("ia") : new long[]{op.getLong("i")};
        int[] xs = op.contains("xa") ? op.getIntArray("xa") : new int[]{op.getInt("x")};
        int[] ys = op.contains("ya") ? op.getIntArray("ya") : new int[]{op.getInt("y")};
        if (ids.length == 0 || ids.length > MAX_KEYS || xs.length != ids.length || ys.length != ids.length) {
            return Outcome.INVALID;
        }
        int done = 0;
        for (int i = 0; i < ids.length; i++) {
            long id = ids[i];
            if (Math.abs(xs[i]) > MAX_COORD || Math.abs(ys[i]) > MAX_COORD) {
                continue;
            }
            boolean known = net.attach(id) != null || net.graph.index.containsKey(id);
            if (!known || !net.config().layout.containsKey(id) && net.config().layout.size() >= MAX_LAYOUT) {
                continue;
            }
            net.moveNode(id, xs[i], ys[i]);
            done++;
        }
        return done == 0 ? Outcome.INVALID : new Outcome(Result.OK, 0);
    }

    private static boolean allMachines(LogisticNetwork net, Context ctx) {
        for (LogisticNetwork.Attach a : net.attaches()) {
            if (!ctx.canEditPoint(a.key)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Coller un reseau entier : canaux recrees (nouveaux uid, dans l'ordre du modele) et reglages appliques a chaque machine du meme type.
     * Toute la configuration est remplacee : il faut le droit de modifier chaque machine du reseau. La disposition du schema est gardee.
     */
    private static Outcome paste(LogisticNetwork net, CompoundTag template, Context ctx) {
        ListTag channels = template.getList("c", Tag.TAG_COMPOUND);
        if (channels.isEmpty() || channels.size() > NetworkConfig.MAX_CHANNELS) {
            return Outcome.INVALID;
        }
        if (!allMachines(net, ctx)) {
            return Outcome.DENIED;
        }
        NetworkConfig next = new NetworkConfig();
        next.auto = false;
        List<Integer> uids = new ArrayList<>();
        for (int i = 0; i < channels.size(); i++) {
            ChannelDef def = ChannelDef.load(channels.getCompound(i));
            def.uid = LogisticNetwork.UIDS.getAsInt();
            def.name = sanitize(def.name, ChannelDef.NAME_MAX);
            def.deleted = false;
            def.partial = false;
            def.origin = "";
            def.body = Double.NaN;
            def.order = i;
            next.channels.add(def);
            uids.add(def.uid);
        }
        ListTag types = template.getList("m", Tag.TAG_COMPOUND);
        for (LogisticNetwork.Attach a : net.attaches()) {
            String type = ctx.machineType(a.key);
            for (int t = 0; t < types.size() && t < 256; t++) {
                CompoundTag entry = types.getCompound(t);
                if (!entry.getString("t").equals(type)) {
                    continue;
                }
                ListTag ps = entry.getList("p", Tag.TAG_COMPOUND);
                for (int k = 0; k < ps.size() && k < NetworkConfig.MAX_CHANNELS; k++) {
                    CompoundTag p = ps.getCompound(k);
                    int index = p.getByte("i");
                    if (index >= 0 && index < uids.size()) {
                        next.setPoint(a.key, uids.get(index), PointConfig.load(p));
                    }
                }
                break;
            }
        }
        next.layout.putAll(net.config().layout);
        next.layoutRev = net.config().layoutRev;
        net.replaceConfig(next);
        return new Outcome(Result.OK, uids.get(0));
    }
}
