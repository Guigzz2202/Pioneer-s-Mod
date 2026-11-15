package fr.pioneers.industry.multi;

import fr.pioneers.industry.IndustryMachine;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Un candidat de gabarit pour une machine : parametres (a, b, c), boite englobante, case du controleur, et ses « sondes » (nombre de cases
 * d'enveloppe a la suite depuis le controleur : a gauche, a droite, en haut, en bas, puis vers l'arriere depuis le bas). Les sondes du monde
 * designent le bon candidat sans tout essayer. Les cases (enveloppe puis interieur, relatives au controleur, empaquetees) sont calculees a la
 * demande et gardees.
 * <p>
 * Case empaquetee : x+64 (7 bits) | y+64 (7 bits) << 7 | z+64 (7 bits) << 14 | interieur << 21 | zones (9 bits) << 22 | rapportee << 31.
 * Case rapportee : au-dessus du sommet, la piece signature de la zone TOP (refrigerateur a dilution) depasse du toit. 1.0.4-beta (lot 15) :
 * carcasse au sommet ({@link IndustryMachine#summitCasing}, ancienne cheminee) : meme case, mais case d'enveloppe ordinaire (zone TOP, sans le
 * bit rapportee) : le gabarit garde sa taille.
 * 1.0 : case cadre ({@link StructureFrameBlock}) : bits interieur ET rapportee ensemble ; case tole ({@link SheetMetalBlock}) : les memes plus le
 * premier bit de zone. Elles comptent avec l'enveloppe. 1.0 (lot 3) : cases de reacteur : bits interieur ET rapportee, et le role
 * ({@link IndustryShape#FUEL}...) dans les bits de zone ({@link #special}, {@link #code}) ; combustible et vide sont rangees a l'interieur.
 * 1.0.4-beta (lot 24) : les multiblocs dessines par Guillaume (NBT, {@link GeneratedNbtShapes}) ont UN gabarit fixe tire de leur structure
 * ({@link #nbt}) : roles propres {@link #GLASS_CELL}... ({@link NbtRoles}), memes bits que les cases de reacteur (codes a partir de
 * {@link #NBT_FIRST}).
 */
public final class ShapeTemplate {

    public static final int INTERIOR = 1 << 21, ATTACHED = 1 << 31, FRAME = INTERIOR | ATTACHED, SHEET = FRAME | 1 << 22;
    /**
     * 1.0.4-beta (lot 24) : codes des cases a role propre des GABARITS NBT (structures de Guillaume, {@link GeneratedNbtShapes} ; regles :
     * {@link NbtRoles}) : verre exige, paroi de foyer, bobine de chauffe, rotor, train de forage, pompe a colonne, paroi / vitre / plafond
     * filtrant de salle blanche, module (baie de serveurs), substrat hydroponique, piece exigee (refrigerateur a dilution).
     */
    public static final int NBT_FIRST = 32, GLASS_CELL = 32, FIREBOX = 33, COIL = 34, ROTOR = 35, DRILL = 36, PUMP = 37, CLEANROOM = 38,
          CLEANROOM_GLASS = 39, FILTER = 40, MODULE = 41, SUBSTRATE = 42, PART = 43;
    private static final Map<IndustryMachine, List<ShapeTemplate>> ALL = new EnumMap<>(IndustryMachine.class);

    public final IndustryShape shape;
    public final int a, b, c, sx, sy, sz;
    /** Une piece est rapportee au-dessus du sommet (zone TOP exigee par la machine, ou carcasse au sommet). */
    public final boolean attached;
    /** 1.0.4-beta (lot 15) : la case au-dessus du sommet est une case d'enveloppe (carcasse), pas une piece rapportee. */
    public final boolean summitCasing;
    /** 1.0 : contenant en toles (paroi au-dessus des deux premieres rangees). */
    public final boolean sheet;
    /** Controleur dans le repere canonique. */
    public final int cx, cy, cz;
    /** Sondes : gauche, droite, haut, bas, arriere. */
    public final int[] probes;
    /**
     * 1.0.4-beta (lot 24) : gabarit tire d'un NBT de Guillaume ({@link GeneratedNbtShapes}) : cases et controleur du NBT, aucune piece
     * signature ni carcasse au sommet, interieur jamais rempli de modules ; enveloppe et volume rendus par la validation = ceux d'AVANT
     * ({@link #resultShell}, {@link #resultVolume}) ; {@link #fixedTier} : tier d'une structure sans piece a tier (salle blanche), -1 sinon.
     */
    public final boolean nbt;
    public final int resultShell, resultVolume, fixedTier;
    private int[] cells;
    private int shellCount = -1;

    private ShapeTemplate(IndustryShape shape, int a, int b, int c, boolean attached, boolean summitCasing, boolean sheet) {
        this.nbt = false;
        this.resultShell = -1;
        this.resultVolume = -1;
        this.fixedTier = -1;
        this.shape = shape;
        this.sheet = sheet;
        this.attached = attached;
        this.summitCasing = summitCasing;
        this.a = a;
        this.b = b;
        this.c = c;
        int[] s = shape.bbox(a, b, c);
        sx = s[0];
        sy = s[1];
        sz = s[2];
        int[] ctrl = shape.controller(a, b, c);
        cx = ctrl[0];
        cy = ctrl[1];
        cz = ctrl[2];
        int left = run(cx, cy, cz, -1, 0, 0), right = run(cx, cy, cz, 1, 0, 0), up = run(cx, cy, cz, 0, 1, 0), down = run(cx, cy, cz, 0, -1, 0);
        probes = new int[]{left, right, up, down, run(cx, cy - down, cz, 0, 0, 1)};
    }

    /** 1.0.4-beta (lot 24) : gabarit d'une structure de Guillaume (NBT), cases calculees tout de suite. */
    private ShapeTemplate(IndustryShape shape, GeneratedNbtShapes.Shape s) {
        this.nbt = true;
        this.shape = shape;
        this.sheet = false;
        this.attached = false;
        this.summitCasing = false;
        this.a = 0;
        this.b = 0;
        this.c = 0;
        sx = s.sx();
        sy = s.sy();
        sz = s.sz();
        cx = s.cx();
        cy = s.cy();
        cz = s.cz();
        resultShell = s.legacyShell();
        resultVolume = s.legacyVolume();
        fixedTier = s.fixedTier();
        List<Integer> shell = new ArrayList<>(), inner = new ArrayList<>();
        for (int y = 0; y < sy; y++) {
            for (int z = 0; z < sz; z++) {
                for (int x = 0; x < sx; x++) {
                    char l = s.at(x, y, z);
                    if (l == '.') {
                        continue;
                    }
                    int p = pack(x - cx, y - cy, z - cz);
                    int code = NbtRoles.code(l);
                    switch (l) {
                        case 'K', 'C' -> shell.add(p);
                        case 'S' -> shell.add(p | SHEET);
                        case 'F' -> shell.add(p | FRAME);
                        case 'I' -> inner.add(p | INTERIOR);
                        case 'R', 'D', 'P', 'M', 'U', 'Z' -> inner.add(p | FRAME | code << 22);
                        default -> shell.add(p | FRAME | code << 22);
                    }
                }
            }
        }
        int[] out = new int[shell.size() + inner.size()];
        int i = 0;
        for (int v : shell) {
            out[i++] = v;
        }
        for (int v : inner) {
            out[i++] = v;
        }
        shellCount = shell.size();
        cells = out;
        java.util.Set<Integer> solid = new java.util.HashSet<>();
        for (int v : out) {
            if (!interior(v)) {
                solid.add(v & 0x1FFFFF);
            }
        }
        int left = runNbt(solid, -1, 0, 0), right = runNbt(solid, 1, 0, 0), up = runNbt(solid, 0, 1, 0), down = runNbt(solid, 0, -1, 0);
        int back = 0;
        while (back < 64 && solid.contains(pack(0, -down, back + 1))) {
            back++;
        }
        probes = new int[]{left, right, up, down, back};
    }

    private static int runNbt(java.util.Set<Integer> solid, int dx, int dy, int dz) {
        int n = 0;
        while (n < 64 && solid.contains(pack(dx * (n + 1), dy * (n + 1), dz * (n + 1)))) {
            n++;
        }
        return n;
    }

    private int run(int x, int y, int z, int dx, int dy, int dz) {
        int n = 0;
        while (n < 64) {
            int r = shape.role(a, b, c, x + dx * (n + 1), y + dy * (n + 1), z + dz * (n + 1));
            if (r == IndustryShape.NONE || r == IndustryShape.INTERIOR || r == IndustryShape.EMPTY) {
                break;
            }
            n++;
        }
        return n;
    }

    /**
     * 0.19.22 : la seule taille acceptee pour la machine (demande de Guillaume : plus de petite, grande ni tres grande) : la taille
     * « moyenne » de l'ancien plan ({@link #medium}). Liste vide pour la plateforme sans forme.
     */
    public static List<ShapeTemplate> of(IndustryMachine m) {
        synchronized (ALL) {
            return ALL.computeIfAbsent(m, k -> {
                //1.0.4-beta (lot 24) : la structure de Guillaume (NBT) fait foi ; plate-forme offshore : forme libre gardee (son NBT = le pont
                //propose par le plan)
                GeneratedNbtShapes.Shape s = GeneratedNbtShapes.of(k.key);
                if (s != null && !k.freeform) {
                    return List.of(new ShapeTemplate(k.shape, s));
                }
                ShapeTemplate t = medium(candidates(k));
                return t == null ? List.of() : List.of(t);
            });
        }
    }

    /**
     * Taille moyenne parmi les candidats : le mieux proportionne ({@link #planScore}) de ceux qui atteignent 12 % de l'ecart entre le plus
     * petit et le plus grand (nombre de blocs) ; fusion : 25 % du plus grand volume de plasma. Meme choix que la variante 1 du plan d'avant.
     */
    private static ShapeTemplate medium(List<ShapeTemplate> all) {
        if (all.isEmpty()) {
            return null;
        }
        boolean fusion = all.get(0).shape == IndustryShape.TOK || all.get(0).shape == IndustryShape.HEL;
        ShapeTemplate biggest = null, smallest = null, best = null;
        for (ShapeTemplate t : all) {
            int size = t.shellCount() + t.interiorCount();
            if (biggest == null || size > biggest.shellCount() + biggest.interiorCount()) {
                biggest = t;
            }
            if (smallest == null || size < smallest.shellCount() + smallest.interiorCount()) {
                smallest = t;
            }
        }
        int lo = smallest.shellCount() + smallest.interiorCount(), hi = biggest.shellCount() + biggest.interiorCount();
        int min = fusion ? (int) (biggest.interiorCount() * 0.25) : (int) Math.round(lo + (hi - lo) * 0.12);
        for (ShapeTemplate t : all) {
            int size = fusion ? t.interiorCount() : t.shellCount() + t.interiorCount();
            if (size >= min && (best == null || t.planScore() < best.planScore())) {
                best = t;
            }
        }
        return best != null ? best : biggest;
    }

    /** Toutes les tailles que le gabarit et les bornes de la machine permettraient (du plus grand au plus petit). */
    private static List<ShapeTemplate> candidates(IndustryMachine m) {
        return build(m);
    }

    private static List<ShapeTemplate> build(IndustryMachine m) {
        List<ShapeTemplate> out = new ArrayList<>();
        IndustryShape s = m.shape;
        if (s == IndustryShape.FREEFORM) {
            return out;
        }
        boolean top = m.summitCasing || m.signatures.stream().anyMatch(sig -> sig.zone() == Signature.Zone.TOP);
        for (int a = m.minW; a <= m.maxW; a++) {
            if (a % 2 == 0) {
                continue;
            }
            boolean usesB = s != IndustryShape.DRUM && s != IndustryShape.SPHERE, usesC = s == IndustryShape.BOX || s == IndustryShape.DRUM || s == IndustryShape.HALL || s == IndustryShape.POOL || s == IndustryShape.JACK
                  || s == IndustryShape.LINE || s == IndustryShape.GANTRY || s == IndustryShape.DEPOT;
            for (int b = usesB ? m.minH : 0; b <= (usesB ? m.maxH : 0); b++) {
                for (int c = usesC ? m.minD : 0; c <= (usesC ? m.maxD : 0); c++) {
                    if (s.valid(a, b, c) && (!top || s.summit(a, b, c) != null)) {
                        out.add(new ShapeTemplate(s, a, b, c, top, m.summitCasing, m.sheet()));
                    }
                }
            }
        }
        out.sort((x, y) -> Integer.compare(y.sx * y.sy * y.sz, x.sx * x.sy * x.sz));
        return Collections.unmodifiableList(out);
    }

    /** Les sondes du monde designent-elles ce candidat ? */
    public boolean matches(int[] world) {
        for (int i = 0; i < probes.length; i++) {
            if (probes[i] != world[i]) {
                return false;
            }
        }
        return true;
    }

    public int distance(int[] world) {
        int d = 0;
        for (int i = 0; i < probes.length; i++) {
            d += Math.abs(probes[i] - world[i]);
        }
        return d;
    }

    /** Cases empaquetees : enveloppe (du bas vers le haut) puis interieur. */
    public synchronized int[] cells() {
        if (cells == null) {
            List<Integer> shell = new ArrayList<>(), inner = new ArrayList<>();
            for (int y = 0; y < sy; y++) {
                for (int z = 0; z < sz; z++) {
                    for (int x = 0; x < sx; x++) {
                        int role = shape.role(a, b, c, x, y, z);
                        if (role == IndustryShape.NONE) {
                            continue;
                        }
                        int p = pack(x - cx, y - cy, z - cz);
                        if (role == IndustryShape.SHELL && sheet && y >= 2) {
                            shell.add(p | SHEET);
                        } else if (role == IndustryShape.SHELL || role == IndustryShape.CASING) {
                            shell.add(p | shape.zones(a, b, c, x, y, z) << 22);
                        } else if (role == IndustryShape.FRAME) {
                            shell.add(p | FRAME);
                        } else if (role == IndustryShape.FUEL || role == IndustryShape.EMPTY) {
                            inner.add(p | FRAME | role << 22);
                        } else if (role >= IndustryShape.FUEL) {
                            shell.add(p | FRAME | role << 22);
                        } else {
                            inner.add(p | INTERIOR);
                        }
                    }
                }
            }
            if (attached) {
                int[] top = shape.summit(a, b, c);
                shell.add(pack(top[0] - cx, top[1] + 1 - cy, top[2] - cz) | Signature.Zone.TOP.bit << 22 | (summitCasing ? 0 : ATTACHED));
            }
            int[] out = new int[shell.size() + inner.size()];
            int i = 0;
            for (int v : shell) {
                out[i++] = v;
            }
            for (int v : inner) {
                out[i++] = v;
            }
            shellCount = shell.size();
            cells = out;
        }
        return cells;
    }

    public int shellCount() {
        cells();
        return shellCount;
    }

    public int interiorCount() {
        return cells().length - shellCount();
    }

    public static int pack(int x, int y, int z) {
        return (x + 64) | (y + 64) << 7 | (z + 64) << 14;
    }

    public static int x(int cell) {
        return (cell & 127) - 64;
    }

    public static int y(int cell) {
        return (cell >> 7 & 127) - 64;
    }

    public static int z(int cell) {
        return (cell >> 14 & 127) - 64;
    }

    public static boolean interior(int cell) {
        return (cell & FRAME) == INTERIOR;
    }

    /** 1.0 : case cadre ({@link StructureFrameBlock}). */
    public static boolean frame(int cell) {
        return special(cell) && code(cell) == 0;
    }

    /** 1.0 : case tole ({@link SheetMetalBlock}, ou verre, bloc de paroi ; port : plus au lot 26, de nouveau au lot 30, a la peau de tole). */
    public static boolean sheet(int cell) {
        return special(cell) && code(cell) == 1;
    }

    /** 1.0 : case a role propre (cadre, tole, reacteur). */
    public static boolean special(int cell) {
        return (cell & FRAME) == FRAME;
    }

    /** 1.0 : code d'une case a role propre (0 cadre, 1 tole, sinon role de reacteur {@link IndustryShape#FUEL}...). */
    public static int code(int cell) {
        return cell >>> 22 & 511;
    }

    /** 1.0 (lot 3) : role de reacteur de la case, ou 0. */
    public static int reactor(int cell) {
        return special(cell) && code(cell) >= IndustryShape.FUEL && code(cell) < NBT_FIRST ? code(cell) : 0;
    }

    /** 1.0.4-beta (lot 24) : role propre d'une case de gabarit NBT ({@link #GLASS_CELL}...), ou 0. */
    public static int nbtRole(int cell) {
        return special(cell) && code(cell) >= NBT_FIRST ? code(cell) : 0;
    }

    public static int zones(int cell) {
        return cell >>> 22 & 511;
    }

    public static boolean isAttached(int cell) {
        return (cell & FRAME) == ATTACHED;
    }

    /**
     * 1.0.4-beta (lot 26, Guillaume : « les ports des multiblocks n'ont le droit que de remplacer des casings et non des sheetmetal ou des
     * frames ») ; lot 30 (Guillaume : « on peut autoriser les ports sur les sheetmetals mais les ports doivent prendre une texture connectee
     * avec les sheetmetal ») : un port peut-il etre pose a cette case ? A la place d'une CARCASSE (case d'enveloppe ordinaire), d'une TOLE
     * ({@link #sheet}, le port y prend la peau de la tole : {@link #portOnSheet}) ou d'une paroi de salle blanche d'une structure NBT ; jamais
     * une charpente, un verre exige, un bloc a role propre, l'interieur ou une piece rapportee. Validation ({@code StructureMatcher}), plan
     * projete ({@link IndustrialBlueprint}), banc ({@code tools/ports/PortBench.java}) et controles des lots 26 et 30.
     */
    public static boolean portAllowed(int cell) {
        if (nbtRole(cell) != 0) {
            return nbtRole(cell) == CLEANROOM;
        }
        return (cell & FRAME) == 0 || sheet(cell);
    }

    /** 1.0.4-beta (lot 30) : un port pose a cette case remplace une tole (il en prend la peau, raccordee aux toles voisines). */
    public static boolean portOnSheet(int cell) {
        return sheet(cell);
    }

    /** Score de choix d'un plan : taille, ponderee par l'ecart aux proportions du gabarit. */
    public double planScore() {
        return (shellCount() + interiorCount()) * (1 + 2 * shape.aspectPenalty(a, b, c));
    }

    /** Case canonique (repere du gabarit) d'une case empaquetee. */
    public int canonicalY(int cell) {
        return y(cell) + cy;
    }

    /** 1.9.2 : abscisse canonique (atelier ferroviaire : verres proposes). */
    public int canonicalX(int cell) {
        return x(cell) + cx;
    }

    /** 1.9.2 : profondeur canonique. */
    public int canonicalZ(int cell) {
        return z(cell) + cz;
    }
}
