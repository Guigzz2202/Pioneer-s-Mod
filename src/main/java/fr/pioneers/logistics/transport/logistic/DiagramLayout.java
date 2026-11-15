package fr.pioneers.logistics.transport.logistic;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Predicate;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2b, spec §5.2) : schema simplifie du diagramme, calcule par le client a partir du graphe compresse ({@link DiagramSnapshot}). Pur et
 * deterministe (meme reseau = meme schema) : teste par le banc hors jeu.
 * <ol>
 *     <li><b>Groupes</b> : machines du meme objet, raccordees aux memes jonctions et aux reglages identiques dans tous les canaux = un noeud
 *     « xN » ; ports d'un meme multibloc aux reglages identiques = un noeud (cle du groupe = controleur). Un groupe deplie ({@code unfolded})
 *     redevient des noeuds seuls.</li>
 *     <li><b>Simplification</b> : jonctions de degre 0, 1 ou 2 retirees (un cable de 500 blocs ou un coude = un trait).</li>
 *     <li><b>Disposition spatiale</b> (1.12.1, F1, demande de Guillaume : « le coffre a droite sera reellement a droite dans le schema ») :
 *     chaque noeud est place d'apres sa position dans le monde vue par le joueur a l'ouverture ({@link View} : plan horizontal tourne selon
 *     son cap ; a droite du joueur = a droite du schema, devant = en haut ; la hauteur departage), distances comprimees (l'ordre relatif est
 *     garde, un cable de 500 blocs ne fait pas 500 pixels), puis cadres ecartes (libelles compris).</li>
 *     <li><b>Reorganiser</b> ({@code arrange}, bouton de l'ecran et depliage d'un groupe) : rangees tirees de la disposition spatiale, ordre
 *     dans chaque rangee par balayages du barycentre des voisins (melange avec la place spatiale pour rester coherent), echanges de voisins
 *     tant que les croisements de liens diminuent, alignement sur une grille. Nombre de passes borne par la taille (calcule au clic, jamais a
 *     chaque image).</li>
 * </ol>
 * Les noeuds deplaces par un joueur ({@link DiagramSnapshot#layout}) gardent leur place, les autres s'ecartent. Pur et deterministe (memes
 * entrees, meme schema). Coordonnees en pixels d'interface a l'echelle 1 (centre du cadre du noeud). Liens dessines en « Z » orthogonal
 * ({@link #route}) : vertical, horizontal a mi-hauteur, vertical.
 * <p>
 * 1.0.5-beta (lot 45, retour 66 de Guillaume : « Le reseau de cable logistique ne doit pas avoir de limite de distance », reponse « Sites +
 * pause ») : SITES. Les machines sont groupees de proche en proche ({@value #SITE_GAP} blocs au plus entre deux machines d'un meme site) ;
 * chaque jonction rejoint le site de la machine la plus proche. Un seul site : schema identique a avant. Plusieurs : chaque site est dessine
 * comme aujourd'hui (disposition spatiale, « Reorganiser ») dans son CADRE ; les cadres sont places cote a cote dans l'ordre vu par le joueur
 * (a sa gauche = a gauche, {@value #SITES_PER_ROW} par rangee, les plus loin devant en haut) ; les liens entre deux sites sont regroupes en un
 * TRAIT ({@link SiteLink}) qui porte la VRAIE distance (a vol d'oiseau, en blocs, entre les deux bouts du cable) et la longueur du cable. Les
 * places deplacees a la main sont relatives a leur site.
 */
public final class DiagramLayout {

    /** Ecart minimal entre deux centres (cadre 28 px + libelle de 82 px dessous) : horizontal, vertical. */
    public static final int SEP_X = 88, SEP_Y = 50;
    /** 1.12.1 (F1) : pixels par bloc de la disposition spatiale avant compression (un ecart d'un bloc = la moitie d'un cadre). */
    static final double PX_X = SEP_X / 2.0, PX_Y = SEP_Y / 2.0;
    /** 1.12.1 (F1) : pas de la grille de « Reorganiser » (demi-cadre) et ecart entre deux rangees. */
    public static final int GRID_X = SEP_X / 2, ROW_PITCH = SEP_Y + 12;
    /**
     * 1.0.5-beta (lot 45, retour 66) : SEUIL des sites : deux machines a {@value} blocs ou moins (distance dans le monde) sont du meme site
     * (de proche en proche) ; sites par rangee ; ecart entre deux cadres ; marge et en-tete d'un cadre ; ecart des couloirs des liens.
     */
    public static final int SITE_GAP = 48, SITES_PER_ROW = 4, SITE_SEP = 96, SITE_PAD = 10, SITE_HEAD = 16, LANE = 12;
    /** Etendue d'un noeud autour de son centre (cadre 28 px, libelle de 84 px sur deux lignes dessous) : gauche / droite, haut, bas. */
    static final int BOX_HALF_W = 44, BOX_TOP = 16, BOX_BOTTOM = 46;

    /**
     * 1.12.1 (F1) : point de vue du joueur a l'ouverture du diagramme (position des yeux, cap en degres comme Minecraft : 0 = vers le sud,
     * 90 = vers l'ouest). Sans point de vue : nord en haut, est a droite (comme une carte).
     */
    public record View(double x, double y, double z, float yaw) {

        /** Vecteur « devant » (x, z) et « a droite » (x, z) du joueur. */
        double[] axes() {
            double r = Math.toRadians(yaw);
            double fx = -Math.sin(r), fz = Math.cos(r);
            return new double[]{fx, fz, -fz, fx};
        }
    }

    /** Un noeud du schema : machine (ou groupe de machines) ou jonction. */
    public static final class Box {

        public final long id;
        public final boolean machine;
        /** Raccords representes (cles triees ; une seule pour une machine seule, aucune pour une jonction). */
        public final long[] members;
        public final String item;
        public double x, y;
        /** Place gardee (deplacee par un joueur). */
        public boolean fixed;
        /** 1.12.1 (F1) : position dans le monde (centre des machines du groupe, ou cable de la jonction). */
        public double wx, wy, wz;
        final List<Box> adj = new ArrayList<>();
        /** 1.0.5-beta (lot 45) : voisins du meme site (disposition d'un site). */
        List<Box> localAdj = adj;
        int index;
        /** 1.0.5-beta (lot 45) : site du noeud ; machines representees en pause (chunk non charge) ; toutes deconnectees (retour 63). */
        public Site site;
        public int paused;
        public boolean detached;

        Box(long id, boolean machine, long[] members, String item) {
            this.id = id;
            this.machine = machine;
            this.members = members;
            this.item = item;
        }

        public boolean group() {
            return members.length > 1;
        }

        /** Position dans le monde : centre des machines representees, ou cable de la jonction. */
        void world() {
            long[] keys = members.length > 0 ? members : new long[]{id};
            double sx = 0, sy = 0, sz = 0;
            for (long k : keys) {
                sx += unpackX(k);
                sy += unpackY(k);
                sz += unpackZ(k);
            }
            wx = sx / keys.length + 0.5;
            wy = sy / keys.length + 0.5;
            wz = sz / keys.length + 0.5;
        }

        public List<Box> neighbours() {
            return adj;
        }
    }

    /**
     * Un lien entre deux noeuds. 1.0.5-beta (lot 45) : {@code length} = cables de la chaine (0 inconnu ou fiche) ; un lien entre deux sites
     * ({@link #between}) est dessine par le trait de son {@link SiteLink}.
     */
    public record Edge(Box a, Box b, int length) {

        public Edge(Box a, Box b) {
            this(a, b, 0);
        }

        public boolean between() {
            return a.site != b.site;
        }
    }

    /**
     * 1.0.5-beta (lot 45, retour 66) : un SITE (grappe de machines proches) : noeuds, decalage de ses places locales ({@code ox, oy}), cadre
     * ({@code x0..x1, y0..y1}, en-tete compris), position moyenne de ses machines dans le monde, nombre de machines (en pause, deconnectees),
     * lettre stable (ordre des positions dans le monde), site du cable clique ({@code here}).
     */
    public static final class Site {

        public final List<Box> boxes = new ArrayList<>();
        public long id = Long.MAX_VALUE;
        public int letter;
        public double ox, oy, x0, y0, x1, y1;
        public double wx, wy, wz;
        public int machines, paused, detached;
        public boolean here;
        /** Rangee et colonne du cadre (placement des traits). */
        public int row, col;

        public double centerX() {
            return (x0 + x1) / 2;
        }

        /** Lettre affichee : A, B... Z, puis AA, AB... */
        public String name() {
            int n = letter;
            StringBuilder b = new StringBuilder();
            do {
                b.insert(0, (char) ('A' + n % 26));
                n = n / 26 - 1;
            } while (n >= 0);
            return b.toString();
        }
    }

    /**
     * 1.0.5-beta (lot 45, retour 66) : TRAIT entre deux sites (tous leurs liens) : distance a vol d'oiseau entre les deux bouts du cable (la
     * plus courte des liens, en blocs), longueur de cable (la plus courte, 0 si inconnue) ; trace orthogonal ({@code route} : x0, y0, x1, y1,
     * ...) entre les points d'attache des deux cadres ; point du libelle.
     */
    public static final class SiteLink {

        public final Site a, b;
        public final List<Edge> edges = new ArrayList<>();
        public double distance = Double.MAX_VALUE;
        public int cable;
        public double[] route = new double[0];
        public double labelX, labelY;
        /** Points d'attache sur les cadres de {@code a} et {@code b}. */
        public double ax, ay, bx, by;

        SiteLink(Site a, Site b) {
            this.a = a;
            this.b = b;
        }
    }

    public final List<Box> boxes = new ArrayList<>();
    public final List<Edge> edges = new ArrayList<>();
    /** 1.0.5-beta (lot 45) : sites (un seul : pas de cadre) et traits entre eux. */
    public final List<Site> sites = new ArrayList<>();
    public final List<SiteLink> siteLinks = new ArrayList<>();
    private final Long2ObjectOpenHashMap<Box> byMember = new Long2ObjectOpenHashMap<>();

    private DiagramLayout() {
    }

    /** Noeud qui represente un raccord (ou une jonction gardee), null s'il n'est pas dessine. */
    public Box boxOf(long id) {
        return byMember.get(id);
    }

    public static DiagramLayout build(DiagramSnapshot snap, LongSet unfolded) {
        return build(snap, unfolded, null, false);
    }

    public static DiagramLayout build(DiagramSnapshot snap, LongSet unfolded, @Nullable View view, boolean arrange) {
        return build(snap, unfolded, view, arrange, it.unimi.dsi.fastutil.longs.LongSets.EMPTY_SET);
    }

    /**
     * 1.12.1 (F1) : schema vu depuis {@code view} (null : nord en haut) ; {@code arrange} : disposition « Reorganiser » (moins de
     * croisements, grille), sinon disposition spatiale. 1.0.5-beta (lot 45) : {@code arrangedSites} : sites (identifiant {@link Site#id})
     * reorganises seuls.
     */
    public static DiagramLayout build(DiagramSnapshot snap, LongSet unfolded, @Nullable View view, boolean arrange, LongSet arrangedSites) {
        DiagramLayout out = new DiagramLayout();
        //Voisins de chaque noeud du graphe compresse (ordre stable)
        Long2ObjectLinkedOpenHashMap<LongOpenHashSet> adj = new Long2ObjectLinkedOpenHashMap<>();
        for (long id : snap.nodeIds) {
            adj.put(id, new LongOpenHashSet());
        }
        for (int i = 0; i < snap.linkA.length; i++) {
            long a = snap.linkA[i], b = snap.linkB[i];
            if (a == b) {
                continue;
            }
            adj.computeIfAbsent(a, k -> new LongOpenHashSet()).add(b);
            adj.computeIfAbsent(b, k -> new LongOpenHashSet()).add(a);
        }
        //1. Groupes de machines
        Map<String, List<DiagramSnapshot.Machine>> groups = new TreeMap<>();
        List<DiagramSnapshot.Machine> machines = new ArrayList<>(snap.machines);
        machines.sort(Comparator.comparingLong(m -> m.key));
        for (DiagramSnapshot.Machine m : machines) {
            String settings = snap.settings(m.key);
            String key;
            if (m.group != m.key) {
                key = "G" + m.group + "|" + settings;
            } else {
                long[] nb = neighbours(adj, m.key);
                key = "M" + m.item + "|" + Arrays.toString(nb) + "|" + settings;
            }
            groups.computeIfAbsent(key, k -> new ArrayList<>()).add(m);
        }
        Long2ObjectOpenHashMap<Box> boxOfNode = new Long2ObjectOpenHashMap<>();
        List<Box> made = new ArrayList<>();
        for (List<DiagramSnapshot.Machine> list : groups.values()) {
            boolean fold = list.size() > 1;
            for (DiagramSnapshot.Machine m : list) {
                fold &= !unfolded.contains(m.key);
            }
            if (fold) {
                long[] members = new long[list.size()];
                for (int i = 0; i < members.length; i++) {
                    members[i] = list.get(i).key;
                }
                DiagramSnapshot.Machine first = list.get(0);
                String item = first.group != first.key && !first.groupItem.isEmpty() ? first.groupItem : first.item;
                Box box = new Box(members[0], true, members, item);
                made.add(box);
                for (long k : members) {
                    boxOfNode.put(k, box);
                }
            } else {
                for (DiagramSnapshot.Machine m : list) {
                    Box box = new Box(m.key, true, new long[]{m.key}, m.item);
                    made.add(box);
                    boxOfNode.put(m.key, box);
                }
            }
        }
        for (int i = 0; i < snap.nodeIds.length; i++) {
            long id = snap.nodeIds[i];
            if (!snap.nodeMachine[i] && !boxOfNode.containsKey(id)) {
                Box box = new Box(id, false, new long[0], "");
                made.add(box);
                boxOfNode.put(id, box);
            }
        }
        made.sort(Comparator.<Box>comparingInt(b -> b.machine ? 0 : 1).thenComparingLong(b -> b.id));
        //Liens entre noeuds du schema (sans doublon ni boucle) ; 1.0.5-beta (lot 45) : longueur de cable (la plus courte)
        Map<Box, LinkedHashMap<Long, Box>> links = new LinkedHashMap<>();
        Map<Box, Map<Long, Integer>> lengths = new java.util.HashMap<>();
        for (Box b : made) {
            links.put(b, new LinkedHashMap<>());
            lengths.put(b, new java.util.HashMap<>());
        }
        for (int i = 0; i < snap.linkA.length; i++) {
            Box a = boxOfNode.get(snap.linkA[i]), b = boxOfNode.get(snap.linkB[i]);
            if (a == null || b == null || a == b) {
                continue;
            }
            int len = i < snap.linkLength.length ? snap.linkLength[i] : 0;
            links.get(a).put(b.id, b);
            links.get(b).put(a.id, a);
            lengths.get(a).merge(b.id, len, Math::min);
            lengths.get(b).merge(a.id, len, Math::min);
        }
        //2. Simplification des jonctions de degre <= 2
        boolean changed = true;
        while (changed && links.size() > 1) {
            changed = false;
            for (Box j : new ArrayList<>(links.keySet())) {
                if (j.machine || !links.containsKey(j)) {
                    continue;
                }
                LinkedHashMap<Long, Box> n = links.get(j);
                if (n.size() > 2 || links.size() <= 1) {
                    continue;
                }
                List<Box> nb = new ArrayList<>(n.values());
                for (Box o : nb) {
                    links.get(o).remove(j.id);
                }
                if (nb.size() == 2) {
                    int len = lengths.get(j).getOrDefault(nb.get(0).id, 0) + lengths.get(j).getOrDefault(nb.get(1).id, 0);
                    links.get(nb.get(0)).put(nb.get(1).id, nb.get(1));
                    links.get(nb.get(1)).put(nb.get(0).id, nb.get(0));
                    lengths.get(nb.get(0)).merge(nb.get(1).id, len, Math::min);
                    lengths.get(nb.get(1)).merge(nb.get(0).id, len, Math::min);
                }
                links.remove(j);
                changed = true;
            }
        }
        for (Map.Entry<Box, LinkedHashMap<Long, Box>> e : links.entrySet()) {
            Box b = e.getKey();
            b.index = out.boxes.size();
            out.boxes.add(b);
            for (long m : b.members) {
                out.byMember.put(m, b);
            }
            if (!b.machine) {
                out.byMember.put(b.id, b);
            }
        }
        for (Box b : out.boxes) {
            List<Box> nb = new ArrayList<>(links.get(b).values());
            nb.sort(Comparator.comparingLong(o -> o.id));
            b.adj.addAll(nb);
            for (Box o : nb) {
                if (b.index < o.index) {
                    out.edges.add(new Edge(b, o, lengths.get(b).getOrDefault(o.id, 0)));
                }
            }
        }
        for (Box b : out.boxes) {
            b.world();
        }
        out.flags(snap);
        out.makeSites(snap);
        if (out.sites.size() <= 1) {
            out.place(snap, view, arrange || !out.sites.isEmpty() && arrangedSites.contains(out.sites.get(0).id));
            for (Site site : out.sites) {
                out.frame(site);
            }
        } else {
            out.placeSites(snap, view, arrange, arrangedSites);
        }
        return out;
    }

    /** 1.0.5-beta (lot 45) : machines en pause (chunk non charge) et deconnectees (« Rien » partout) de chaque noeud. */
    private void flags(DiagramSnapshot snap) {
        Long2ObjectOpenHashMap<DiagramSnapshot.Machine> byKey = new Long2ObjectOpenHashMap<>();
        for (DiagramSnapshot.Machine m : snap.machines) {
            byKey.put(m.key, m);
        }
        for (Box b : boxes) {
            boolean all = b.members.length > 0;
            for (long k : b.members) {
                DiagramSnapshot.Machine m = byKey.get(k);
                b.paused += m != null && m.paused ? 1 : 0;
                all &= m != null && m.detached;
            }
            b.detached = all;
        }
    }

    // ------------------------------------------------------------------
    // 1.0.5-beta (lot 45, retour 66) : sites
    // ------------------------------------------------------------------

    private static double dist(Box a, Box b) {
        double dx = a.wx - b.wx, dy = a.wy - b.wy, dz = a.wz - b.wz;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /**
     * Sites : machines reliees de proche en proche a {@link #SITE_GAP} blocs au plus ; chaque jonction au site de la machine la plus proche
     * (aucune machine : un seul site). Lettres dans l'ordre des positions dans le monde (x puis z : stable quel que soit le point de vue).
     */
    private void makeSites(DiagramSnapshot snap) {
        List<Box> machines = new ArrayList<>();
        for (Box b : boxes) {
            if (b.machine) {
                machines.add(b);
            }
        }
        int m = machines.size();
        int[] parent = new int[m];
        for (int i = 0; i < m; i++) {
            parent[i] = i;
        }
        for (int i = 0; i < m; i++) {
            for (int j = i + 1; j < m; j++) {
                if (dist(machines.get(i), machines.get(j)) <= SITE_GAP) {
                    int ri = find(parent, i), rj = find(parent, j);
                    if (ri != rj) {
                        parent[ri] = rj;
                    }
                }
            }
        }
        Map<Integer, Site> byRoot = new TreeMap<>();
        for (int i = 0; i < m; i++) {
            Site site = byRoot.computeIfAbsent(find(parent, i), k -> new Site());
            Box b = machines.get(i);
            b.site = site;
            site.boxes.add(b);
            site.id = Math.min(site.id, b.members.length > 0 ? b.members[0] : b.id);
        }
        if (byRoot.isEmpty()) {
            Site only = new Site();
            byRoot.put(0, only);
        }
        List<Site> list = new ArrayList<>(byRoot.values());
        for (Box b : boxes) {
            if (b.site != null) {
                continue;
            }
            Site best = list.get(0);
            double bd = Double.MAX_VALUE;
            for (Box o : machines) {
                double d = dist(b, o);
                if (d < bd) {
                    bd = d;
                    best = o.site;
                }
            }
            b.site = best;
            best.boxes.add(b);
            if (best.id == Long.MAX_VALUE) {
                best.id = b.id;
            }
        }
        for (Site site : list) {
            int n = 0;
            for (Box b : site.boxes) {
                if (b.machine) {
                    site.wx += b.wx * b.members.length;
                    site.wy += b.wy * b.members.length;
                    site.wz += b.wz * b.members.length;
                    n += b.members.length;
                    site.machines += b.members.length;
                    site.paused += b.paused;
                    site.detached += b.detached ? b.members.length : 0;
                }
            }
            if (n > 0) {
                site.wx /= n;
                site.wy /= n;
                site.wz /= n;
            } else if (!site.boxes.isEmpty()) {
                site.wx = site.boxes.get(0).wx;
                site.wy = site.boxes.get(0).wy;
                site.wz = site.boxes.get(0).wz;
            }
        }
        list.sort(Comparator.<Site>comparingDouble(s -> s.wx).thenComparingDouble(s -> s.wz).thenComparingLong(s -> s.id));
        for (int i = 0; i < list.size(); i++) {
            list.get(i).letter = i;
        }
        sites.addAll(list);
        //Site du cable clique (le joueur est la)
        if (snap.cable != 0 || snap.nodeIds.length > 0) {
            double cx = unpackX(snap.cable) + 0.5, cy = unpackY(snap.cable) + 0.5, cz = unpackZ(snap.cable) + 0.5;
            Site best = null;
            double bd = Double.MAX_VALUE;
            for (Box b : boxes) {
                double dx = b.wx - cx, dy = b.wy - cy, dz = b.wz - cz;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d < bd) {
                    bd = d;
                    best = b.site;
                }
            }
            if (best != null && bd <= SITE_GAP && sites.size() > 1) {
                best.here = true;
            }
        }
        //Voisins du meme site (disposition de chaque site)
        for (Box b : boxes) {
            List<Box> local = new ArrayList<>();
            for (Box o : b.adj) {
                if (o.site == b.site) {
                    local.add(o);
                }
            }
            b.localAdj = local.size() == b.adj.size() ? b.adj : local;
        }
    }

    private static int find(int[] parent, int i) {
        while (parent[i] != i) {
            parent[i] = parent[parent[i]];
            i = parent[i];
        }
        return i;
    }

    /** Cadre d'un site autour de ses noeuds (places absolues), en-tete compris. */
    private void frame(Site site) {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (Box b : site.boxes) {
            x0 = Math.min(x0, b.x - BOX_HALF_W);
            x1 = Math.max(x1, b.x + BOX_HALF_W);
            y0 = Math.min(y0, b.y - BOX_TOP);
            y1 = Math.max(y1, b.y + BOX_BOTTOM);
        }
        if (site.boxes.isEmpty()) {
            x0 = y0 = -BOX_HALF_W;
            x1 = y1 = BOX_HALF_W;
        }
        site.x0 = x0 - SITE_PAD;
        site.x1 = x1 + SITE_PAD;
        site.y0 = y0 - SITE_PAD - SITE_HEAD;
        site.y1 = y1 + SITE_PAD;
    }

    /**
     * Plusieurs sites : chaque site dispose ses noeuds seul (spatial, places a la main relatives au site, « Reorganiser » de tous ou de ce
     * site), puis les cadres sont ranges cote a cote dans l'ordre vu par le joueur ; les traits entre sites sont traces en dernier.
     */
    private void placeSites(DiagramSnapshot snap, @Nullable View view, boolean arrangeAll, LongSet arrangedSites) {
        List<Edge> all = new ArrayList<>(edges);
        List<Box> allBoxes = new ArrayList<>(boxes);
        for (Site site : sites) {
            boxes.clear();
            edges.clear();
            boxes.addAll(site.boxes);
            for (int i = 0; i < boxes.size(); i++) {
                boxes.get(i).index = i;
            }
            for (Edge e : all) {
                if (e.a().site == site && e.b().site == site) {
                    edges.add(e);
                }
            }
            place(snap, view, arrangeAll || arrangedSites.contains(site.id));
            frame(site);
        }
        boxes.clear();
        boxes.addAll(allBoxes);
        edges.clear();
        edges.addAll(all);
        for (int i = 0; i < boxes.size(); i++) {
            boxes.get(i).index = i;
        }
        //Ordre vu par le joueur : devant = en haut (rangees de SITES_PER_ROW), a droite = a droite
        double cx = 0, cz = 0;
        for (Site site : sites) {
            cx += site.wx;
            cz += site.wz;
        }
        View v = view != null ? view : new View(cx / sites.size(), 0, cz / sites.size(), 180F);
        double[] ax = v.axes();
        Map<Site, double[]> seen = new java.util.HashMap<>();
        for (Site site : sites) {
            double dx = site.wx - v.x(), dz = site.wz - v.z();
            seen.put(site, new double[]{dx * ax[2] + dz * ax[3], dx * ax[0] + dz * ax[1]});
        }
        List<Site> order = new ArrayList<>(sites);
        order.sort(Comparator.<Site>comparingDouble(s -> -seen.get(s)[1]).thenComparingLong(s -> s.id));
        int rows = (order.size() + SITES_PER_ROW - 1) / SITES_PER_ROW;
        double y = 0;
        for (int r = 0; r < rows; r++) {
            List<Site> row = new ArrayList<>(order.subList(r * SITES_PER_ROW, Math.min(order.size(), (r + 1) * SITES_PER_ROW)));
            row.sort(Comparator.<Site>comparingDouble(s -> seen.get(s)[0]).thenComparingLong(s -> s.id));
            double x = 0, height = 0;
            for (int c = 0; c < row.size(); c++) {
                Site site = row.get(c);
                site.row = r;
                site.col = c;
                double w = site.x1 - site.x0, h = site.y1 - site.y0;
                site.ox = x - site.x0;
                site.oy = y - site.y0;
                x += w + SITE_SEP;
                height = Math.max(height, h);
            }
            y += height + SITE_SEP + LANE * 3;
        }
        for (Site site : sites) {
            for (Box b : site.boxes) {
                b.x += site.ox;
                b.y += site.oy;
            }
            site.x0 += site.ox;
            site.x1 += site.ox;
            site.y0 += site.oy;
            site.y1 += site.oy;
        }
        //Centre de l'ensemble en 0 (places absolues ; les places locales restent celles du site)
        double[] bnd = bounds();
        double mx = Math.round((bnd[0] + bnd[2]) / 2), my = Math.round((bnd[1] + bnd[3]) / 2);
        for (Site site : sites) {
            site.ox -= mx;
            site.oy -= my;
            site.x0 -= mx;
            site.x1 -= mx;
            site.y0 -= my;
            site.y1 -= my;
            for (Box b : site.boxes) {
                b.x -= mx;
                b.y -= my;
            }
        }
        links();
    }

    /**
     * Traits entre sites : un par paire de sites relies (tous leurs liens) ; distance a vol d'oiseau entre les deux bouts de chaque lien
     * (la plus courte), longueur de cable (la plus courte connue). Deux cadres voisins d'une meme rangee : trait horizontal entre leurs bords ;
     * sinon par un couloir sous la rangee (un couloir par trait).
     */
    private void links() {
        Map<Long, SiteLink> byPair = new java.util.LinkedHashMap<>();
        for (Edge e : edges) {
            if (!e.between()) {
                continue;
            }
            Site a = e.a().site, b = e.b().site;
            if (a.letter > b.letter) {
                Site t = a;
                a = b;
                b = t;
            }
            long key = (long) a.letter << 32 | b.letter;
            Site fa = a, fb = b;
            SiteLink link = byPair.computeIfAbsent(key, k -> new SiteLink(fa, fb));
            link.edges.add(e);
            link.distance = Math.min(link.distance, dist(e.a(), e.b()));
            if (e.length() > 0) {
                link.cable = link.cable == 0 ? e.length() : Math.min(link.cable, e.length());
            }
        }
        Map<Integer, Integer> lanes = new java.util.HashMap<>();
        for (SiteLink link : byPair.values()) {
            Site a = link.a, b = link.b;
            if (a.col > b.col && a.row == b.row || a.row > b.row) {
                a = link.b;
                b = link.a;
            }
            if (a.row == b.row && b.col == a.col + 1) {
                double top = Math.max(a.y0, b.y0) + SITE_HEAD + 8, bottom = Math.min(a.y1, b.y1) - 8;
                double yy = Math.round(Math.min(Math.max(top, (top + bottom) / 2), Math.max(top, bottom)));
                link.ax = a.x1;
                link.ay = yy;
                link.bx = b.x0;
                link.by = yy;
                link.route = new double[]{a.x1, yy, b.x0, yy};
                link.labelX = (a.x1 + b.x0) / 2;
                link.labelY = yy;
            } else {
                int lane = lanes.merge(a.row, 1, Integer::sum);
                double rowBottom = -Double.MAX_VALUE;
                for (Site s : sites) {
                    if (s.row == a.row) {
                        rowBottom = Math.max(rowBottom, s.y1);
                    }
                }
                double ly = Math.round(rowBottom + LANE * lane);
                double xa = Math.round(a.centerX() + (lane - 1) % 3 * 8 - 8), xb = Math.round(b.centerX() + (lane - 1) % 3 * 8 - 8);
                link.ax = xa;
                link.ay = a.y1;
                if (a.row == b.row) {
                    link.bx = xb;
                    link.by = b.y1;
                    link.route = new double[]{xa, a.y1, xa, ly, xb, ly, xb, b.y1};
                } else {
                    link.bx = xb;
                    link.by = b.y0;
                    link.route = new double[]{xa, a.y1, xa, ly, xb, ly, xb, b.y0};
                }
                link.labelX = (xa + xb) / 2;
                link.labelY = ly;
            }
            siteLinks.add(link);
        }
    }

    /** 1.0.5-beta (lot 45) : place locale (relative a son site) d'un noeud : disposition partagee ({@code node}). */
    public static long localX(Box b) {
        return Math.round(b.x - (b.site == null ? 0 : b.site.ox));
    }

    public static long localY(Box b) {
        return Math.round(b.y - (b.site == null ? 0 : b.site.oy));
    }

    /** 1.0.5-beta (lot 45) : site d'identifiant {@code id}, ou null. */
    @Nullable
    public Site site(long id) {
        for (Site s : sites) {
            if (s.id == id) {
                return s;
            }
        }
        return null;
    }

    private static long[] neighbours(Long2ObjectLinkedOpenHashMap<LongOpenHashSet> adj, long id) {
        LongOpenHashSet set = adj.get(id);
        long[] out = set == null ? new long[0] : set.toLongArray();
        Arrays.sort(out);
        return out;
    }

    // ------------------------------------------------------------------
    // Disposition
    // ------------------------------------------------------------------

    // Positions empaquetees comme BlockPos.asLong (26 bits x, 12 bits y, 26 bits z) : pur, sans Minecraft
    static int unpackX(long packed) {
        return (int) (packed << 64 - 38 - 26 >> 64 - 26);
    }

    static int unpackY(long packed) {
        return (int) (packed << 64 - 12 >> 64 - 12);
    }

    static int unpackZ(long packed) {
        return (int) (packed << 64 - 12 - 26 >> 64 - 26);
    }

    private void place(DiagramSnapshot snap, @Nullable View view, boolean arrange) {
        int n = boxes.size();
        if (n == 0) {
            return;
        }
        spatial(view);
        //Places gardees (deplacees par un joueur)
        for (Box b : boxes) {
            long packed = snap.layout.getOrDefault(b.id, Long.MIN_VALUE);
            if (packed != Long.MIN_VALUE) {
                b.x = (int) (packed >> 32);
                b.y = (int) packed;
                b.fixed = true;
            }
        }
        if (arrange) {
            arrange();
        }
        separate();
        if (overlaps() > 0) {
            legalize();
        }
        for (Box b : boxes) {
            b.x = Math.round(b.x);
            b.y = Math.round(b.y);
        }
    }

    /**
     * 1.12.1 (F1) : disposition spatiale. Projection sur le plan horizontal tourne selon le cap du joueur (a droite = x croissant, devant =
     * y decroissant, plus haut dans le monde = un peu plus haut), puis compression de chaque axe : les ecarts gardent leur ordre mais ne
     * depassent pas un cadre (un bloc d'ecart = un demi-cadre).
     */
    private void spatial(@Nullable View view) {
        int n = boxes.size();
        double cx = 0, cy = 0, cz = 0;
        for (Box b : boxes) {
            cx += b.wx;
            cy += b.wy;
            cz += b.wz;
        }
        View v = view != null ? view : new View(cx / n, cy / n, cz / n, 180F);
        double[] ax = v.axes();
        double[] u = new double[n], w = new double[n];
        for (int i = 0; i < n; i++) {
            Box b = boxes.get(i);
            double dx = b.wx - v.x(), dz = b.wz - v.z();
            u[i] = dx * ax[2] + dz * ax[3];
            //Devant = en haut ; la hauteur departage (au-dessus = un peu plus haut)
            w[i] = -(dx * ax[0] + dz * ax[1]) - (b.wy - v.y()) * 0.6;
        }
        double[] xs = compress(u, PX_X, SEP_X), ys = compress(w, PX_Y, SEP_Y);
        for (int i = 0; i < n; i++) {
            boxes.get(i).x = xs[i];
            boxes.get(i).y = ys[i];
        }
    }

    /** Positions comprimees d'un axe : meme ordre, ecart = min(plafond, ecart en blocs x pixels par bloc), centrees sur 0. */
    private static double[] compress(double[] v, double perBlock, double cap) {
        int n = v.length;
        Integer[] order = new Integer[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
        }
        Arrays.sort(order, Comparator.comparingDouble(i -> v[i]));
        double[] out = new double[n];
        double pos = 0;
        for (int k = 0; k < n; k++) {
            if (k > 0) {
                double d = v[order[k]] - v[order[k - 1]];
                pos += d < 0.01 ? 0 : Math.min(cap, d * perBlock);
            }
            out[order[k]] = pos;
        }
        double mid = pos / 2;
        for (int i = 0; i < n; i++) {
            out[i] -= mid;
        }
        return out;
    }

    /**
     * 1.12.1 (F1) : « Reorganiser ». Rangees = groupes de y spatiaux proches ; dans chaque rangee, ordre par balayages du barycentre des
     * voisins (75 %) melange a la place spatiale (25 %, pour que la droite reste a droite), puis echanges de voisins tant que les croisements
     * diminuent ; places alignees sur une grille de {@link #GRID_X}, rangees espacees de {@link #ROW_PITCH}. On garde la meilleure passe
     * (moins de croisements, puis liens les plus courts). Passes bornees par la taille du schema.
     */
    private void arrange() {
        List<Box> free = new ArrayList<>();
        for (Box b : boxes) {
            if (!b.fixed) {
                free.add(b);
            }
        }
        if (free.size() < 2) {
            return;
        }
        int n = boxes.size();
        double[] spatialX = new double[n];
        for (Box b : boxes) {
            spatialX[b.index] = b.x;
        }
        //Rangees : y spatiaux a moins de 12 px
        free.sort(Comparator.<Box>comparingDouble(b -> b.y).thenComparingDouble(b -> b.x).thenComparingLong(b -> b.id));
        List<List<Box>> rows = new ArrayList<>();
        double rowY = Double.NaN;
        for (Box b : free) {
            if (rows.isEmpty() || b.y - rowY >= 12) {
                rows.add(new ArrayList<>());
                rowY = b.y;
            }
            rows.get(rows.size() - 1).add(b);
        }
        int[] rowOf = new int[n];
        Arrays.fill(rowOf, -1);
        for (int r = 0; r < rows.size(); r++) {
            for (Box b : rows.get(r)) {
                rowOf[b.index] = r;
                b.y = (r - (rows.size() - 1) / 2.0) * ROW_PITCH;
            }
            rows.get(r).sort(Comparator.<Box>comparingDouble(b -> b.x).thenComparingLong(b -> b.id));
            pack(rows.get(r), null);
        }
        int sweeps = n <= 200 ? 16 : n <= 600 ? 6 : 2;
        double[] bestX = new double[n];
        long bestScore = score();
        save(bestX);
        double[] key = new double[n];
        for (int it = 0; it < sweeps; it++) {
            boolean down = it % 2 == 0;
            for (int k = 0; k < rows.size(); k++) {
                List<Box> row = rows.get(down ? k : rows.size() - 1 - k);
                for (Box b : row) {
                    double sum = 0;
                    int count = 0;
                    for (Box o : b.localAdj) {
                        sum += o.x;
                        count++;
                    }
                    key[b.index] = count == 0 ? b.x : 0.75 * (sum / count) + 0.25 * spatialX[b.index];
                }
                row.sort(Comparator.<Box>comparingDouble(b -> key[b.index]).thenComparingLong(b -> b.id));
                pack(row, key);
            }
            long sc = score();
            if (sc < bestScore) {
                bestScore = sc;
                save(bestX);
            }
        }
        restore(bestX);
        //Echanges de voisins dans une rangee tant que les croisements diminuent (petits schemas)
        if (edges.size() <= 220) {
            for (int pass = 0; pass < 4; pass++) {
                boolean improved = false;
                for (List<Box> row : rows) {
                    for (int i = 0; i + 1 < row.size(); i++) {
                        Box a = row.get(i), b = row.get(i + 1);
                        long before = score();
                        double ax = a.x, bx = b.x;
                        a.x = bx;
                        b.x = ax;
                        if (score() < before) {
                            row.set(i, b);
                            row.set(i + 1, a);
                            improved = true;
                        } else {
                            a.x = ax;
                            b.x = bx;
                        }
                    }
                }
                if (!improved) {
                    break;
                }
            }
        }
        //Centre du schema en 0 (les rangees gardent leur alignement sur la grille)
        double mid = 0;
        for (Box b : free) {
            mid += b.x;
        }
        mid = Math.round(mid / free.size() / GRID_X) * GRID_X;
        for (Box b : free) {
            b.x -= mid;
        }
    }

    /** Places d'une rangee dans l'ordre de la liste : au plus pres de la cle (ou de la place actuelle), ecart >= un cadre, sur la grille. */
    private static void pack(List<Box> row, @Nullable double[] key) {
        int m = row.size();
        double[] want = new double[m], fwd = new double[m], back = new double[m];
        for (int i = 0; i < m; i++) {
            want[i] = key == null ? row.get(i).x : key[row.get(i).index];
        }
        for (int i = 0; i < m; i++) {
            fwd[i] = i == 0 ? want[i] : Math.max(want[i], fwd[i - 1] + SEP_X);
        }
        for (int i = m - 1; i >= 0; i--) {
            back[i] = i == m - 1 ? want[i] : Math.min(want[i], back[i + 1] - SEP_X);
        }
        double prev = -Double.MAX_VALUE;
        for (int i = 0; i < m; i++) {
            double x = Math.round((fwd[i] + back[i]) / 2 / GRID_X) * (double) GRID_X;
            x = Math.max(x, prev + SEP_X);
            row.get(i).x = x;
            prev = x;
        }
    }

    private void save(double[] xs) {
        for (Box b : boxes) {
            xs[b.index] = b.x;
        }
    }

    private void restore(double[] xs) {
        for (Box b : boxes) {
            b.x = xs[b.index];
        }
    }

    /** Note d'une disposition : croisements d'abord, puis longueur totale des liens. */
    private long score() {
        long length = 0;
        for (Edge e : edges) {
            length += (long) (Math.abs(e.a.x - e.b.x) + Math.abs(e.a.y - e.b.y));
        }
        return crossings() * 1_000_000L + Math.min(999_999, length / 4);
    }

    /**
     * Trace d'un lien tel que l'ecran le dessine : vertical depuis {@code a} jusqu'a mi-hauteur, horizontal, vertical jusqu'a {@code b} ;
     * horizontal seul si les deux noeuds sont a la meme hauteur. Rend les points (x0, y0, x1, y1, ...).
     */
    public static double[] route(Box a, Box b) {
        double ax = Math.round(a.x), ay = Math.round(a.y), bx = Math.round(b.x), by = Math.round(b.y);
        if (ay == by || ax == bx) {
            return new double[]{ax, ay, bx, by};
        }
        double mid = Math.round((ay + by) / 2);
        return new double[]{ax, ay, ax, mid, bx, mid, bx, by};
    }

    /** 1.12.1 (F1) : nombre de croisements (et de superpositions) entre les traces des liens (liens qui partagent un noeud exclus). */
    public int crossings() {
        int m = edges.size();
        double[][] routes = new double[m][];
        for (int i = 0; i < m; i++) {
            routes[i] = route(edges.get(i).a, edges.get(i).b);
        }
        int count = 0;
        for (int i = 0; i < m; i++) {
            Edge e = edges.get(i);
            for (int j = i + 1; j < m; j++) {
                Edge f = edges.get(j);
                if (e.a == f.a || e.a == f.b || e.b == f.a || e.b == f.b) {
                    continue;
                }
                count += crossings(routes[i], routes[j]);
            }
        }
        return count;
    }

    private static int crossings(double[] p, double[] q) {
        int c = 0;
        for (int i = 0; i + 3 < p.length; i += 2) {
            for (int j = 0; j + 3 < q.length; j += 2) {
                c += cross(p[i], p[i + 1], p[i + 2], p[i + 3], q[j], q[j + 1], q[j + 2], q[j + 3]) ? 1 : 0;
            }
        }
        return c;
    }

    /**
     * Deux segments orthogonaux se genent-ils : un horizontal et un vertical qui se coupent strictement, ou deux segments de meme sens
     * superposes sur une longueur (deux liens confondus se lisent comme un noeud).
     */
    private static boolean cross(double x0, double y0, double x1, double y1, double u0, double v0, double u1, double v1) {
        boolean h1 = y0 == y1, h2 = v0 == v1;
        if (h1 == h2) {
            if (h1) {
                return y0 == v0 && Math.min(Math.max(x0, x1), Math.max(u0, u1)) - Math.max(Math.min(x0, x1), Math.min(u0, u1)) > 1;
            }
            return x0 == u0 && Math.min(Math.max(y0, y1), Math.max(v0, v1)) - Math.max(Math.min(y0, y1), Math.min(v0, v1)) > 1;
        }
        if (!h1) {
            return cross(u0, v0, u1, v1, x0, y0, x1, y1);
        }
        //(x0..x1, y0) horizontal ; (u0, v0..v1) vertical
        return u0 > Math.min(x0, x1) && u0 < Math.max(x0, x1) && y0 > Math.min(v0, v1) && y0 < Math.max(v0, v1);
    }

    /**
     * Dernier recours (schemas tres denses) : chaque noeud libre va sur la case libre la plus proche d'une grille d'un cadre de cote,
     * dans l'ordre (haut, gauche) : plus aucun chevauchement, l'ordre general est garde.
     */
    private void legalize() {
        List<Box> fixed = new ArrayList<>(), free = new ArrayList<>();
        for (Box b : boxes) {
            (b.fixed ? fixed : free).add(b);
        }
        free.sort(Comparator.<Box>comparingDouble(b -> b.y).thenComparingDouble(b -> b.x).thenComparingLong(b -> b.id));
        LongOpenHashSet taken = new LongOpenHashSet();
        for (Box b : free) {
            int gx = (int) Math.round(b.x / SEP_X), gy = (int) Math.round(b.y / SEP_Y);
            search:
            for (int r = 0; ; r++) {
                for (int dy = -r; dy <= r; dy++) {
                    for (int dx = -r; dx <= r; dx++) {
                        if (Math.max(Math.abs(dx), Math.abs(dy)) != r) {
                            continue;
                        }
                        int cx = gx + dx, cy = gy + dy;
                        long cell = (long) cx << 32 | (cy & 0xFFFFFFFFL);
                        if (taken.contains(cell) || blocked(fixed, cx * (double) SEP_X, cy * (double) SEP_Y)) {
                            continue;
                        }
                        taken.add(cell);
                        b.x = cx * (double) SEP_X;
                        b.y = cy * (double) SEP_Y;
                        break search;
                    }
                }
            }
        }
    }

    private static boolean blocked(List<Box> fixed, double x, double y) {
        for (Box f : fixed) {
            if (Math.abs(f.x - x) < SEP_X && Math.abs(f.y - y) < SEP_Y) {
                return true;
            }
        }
        return false;
    }

    /** Separe les cadres qui se chevauchent (libelles compris) : poussee le long de l'axe le moins engage. */
    private void separate() {
        int n = boxes.size();
        int passes = (int) Math.max(4, Math.min(400, 4.0e7 / Math.max(1, (double) n * n)));
        for (int pass = 0; pass < passes; pass++) {
            boolean moved = false;
            for (int i = 0; i < n; i++) {
                Box a = boxes.get(i);
                for (int j = i + 1; j < n; j++) {
                    Box b = boxes.get(j);
                    double vx = b.x - a.x, vy = b.y - a.y;
                    double px = SEP_X - Math.abs(vx), py = SEP_Y - Math.abs(vy);
                    if (px <= 0 || py <= 0 || a.fixed && b.fixed) {
                        continue;
                    }
                    moved = true;
                    double share = a.fixed || b.fixed ? 1 : 0.5;
                    if (px * (SEP_Y / (double) SEP_X) < py) {
                        double s = (vx == 0 ? (a.id < b.id ? 1 : -1) : Math.signum(vx)) * (px + 0.5) * share;
                        if (!a.fixed) {
                            a.x -= s;
                        }
                        if (!b.fixed) {
                            b.x += s;
                        }
                    } else {
                        double s = (vy == 0 ? (a.id < b.id ? 1 : -1) : Math.signum(vy)) * (py + 0.5) * share;
                        if (!a.fixed) {
                            a.y -= s;
                        }
                        if (!b.fixed) {
                            b.y += s;
                        }
                    }
                }
            }
            if (!moved) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------
    // Requetes de l'ecran
    // ------------------------------------------------------------------

    /** Nombre de chevauchements restants (banc). */
    public int overlaps() {
        int count = 0;
        for (int i = 0; i < boxes.size(); i++) {
            for (int j = i + 1; j < boxes.size(); j++) {
                Box a = boxes.get(i), b = boxes.get(j);
                if (Math.abs(a.x - b.x) < SEP_X && Math.abs(a.y - b.y) < SEP_Y) {
                    count++;
                }
            }
        }
        return count;
    }

    /** Bornes (x0, y0, x1, y1) des centres des noeuds ; 1.0.5-beta (lot 45) : plusieurs sites : cadres et couloirs compris. */
    public double[] bounds() {
        double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE;
        for (Box b : boxes) {
            x0 = Math.min(x0, b.x);
            y0 = Math.min(y0, b.y);
            x1 = Math.max(x1, b.x);
            y1 = Math.max(y1, b.y);
        }
        if (sites.size() > 1) {
            //Cadres (moins l'etendue d'un noeud, que l'ecran ajoute au cadrage) et couloirs des traits
            for (Site s : sites) {
                x0 = Math.min(x0, s.x0 + BOX_HALF_W);
                x1 = Math.max(x1, s.x1 - BOX_HALF_W);
                y0 = Math.min(y0, s.y0 + BOX_TOP);
                y1 = Math.max(y1, s.y1 - BOX_BOTTOM);
            }
            for (SiteLink l : siteLinks) {
                for (int i = 1; i < l.route.length; i += 2) {
                    y1 = Math.max(y1, l.route[i] - BOX_BOTTOM + 12);
                }
            }
        }
        return boxes.isEmpty() ? new double[]{0, 0, 0, 0} : new double[]{x0, y0, x1, y1};
    }

    /** 1.0.5-beta (lot 45, banc) : cadres de sites qui se chevauchent. */
    public int siteOverlaps() {
        int n = 0;
        for (int i = 0; i < sites.size(); i++) {
            for (int j = i + 1; j < sites.size(); j++) {
                Site a = sites.get(i), b = sites.get(j);
                if (a.x0 < b.x1 && b.x0 < a.x1 && a.y0 < b.y1 && b.y0 < a.y1) {
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * Liens parcourus par un canal (tronçons colores du diagramme) : arbre qui relie les noeuds qui y participent (chemins depuis le premier
     * participant, en largeur). Rend un drapeau par lien de {@link #edges}.
     */
    public boolean[] used(Predicate<Box> participant) {
        boolean[] out = new boolean[edges.size()];
        List<Box> parts = new ArrayList<>();
        for (Box b : boxes) {
            if (participant.test(b)) {
                parts.add(b);
            }
        }
        if (parts.size() < 2) {
            return out;
        }
        int n = boxes.size();
        int[] parent = new int[n];
        Arrays.fill(parent, -2);
        ArrayDeque<Box> q = new ArrayDeque<>();
        parent[parts.get(0).index] = -1;
        q.add(parts.get(0));
        while (!q.isEmpty()) {
            Box b = q.poll();
            for (Box c : b.adj) {
                if (parent[c.index] == -2) {
                    parent[c.index] = b.index;
                    q.add(c);
                }
            }
        }
        Map<Long, Integer> edgeIndex = new java.util.HashMap<>();
        for (int i = 0; i < edges.size(); i++) {
            Edge e = edges.get(i);
            edgeIndex.put(pair(e.a.index, e.b.index), i);
        }
        for (int k = 1; k < parts.size(); k++) {
            int c = parts.get(k).index;
            while (c >= 0 && parent[c] >= 0) {
                Integer e = edgeIndex.get(pair(c, parent[c]));
                if (e != null) {
                    if (out[e]) {
                        break;
                    }
                    out[e] = true;
                }
                c = parent[c];
            }
        }
        return out;
    }

    private static long pair(int a, int b) {
        return (long) Math.min(a, b) << 32 | Math.max(a, b);
    }
}
