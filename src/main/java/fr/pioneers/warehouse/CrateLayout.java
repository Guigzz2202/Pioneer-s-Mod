package fr.pioneers.warehouse;

import fr.pioneers.industry.multi.Structure;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 1, Guillaume : « des caisses de stockage qui sont des multiblocs en bois traite et acier ; les 3 tailles acceptables sont
 * 3x3x3, 3x5x3, 3x7x3 ») : PLAN de la caisse d'entrepot, SOURCE UNIQUE de la verification (trappe, banc des multiblocs, GameTests), du plan
 * projete et de la peinture des pieces (etat {@code part} de chaque bloc : sa place dans la grande caisse).
 * <p>
 * Repere de la caisse : u en travers (0..2), v en hauteur (0..2), w en longueur (0..n-1). Caisse selon Z : u vers +X, w vers +Z. Caisse
 * selon X : u vers +Z, w vers -X (meme repere que le modele de l'axe Z tourne de 90 degres par l'etat de bloc, {@code y = 90}). Pieces :
 * <ul>
 *     <li>les ARETES (au moins deux bords de la boite) : {@code warehouse_crate_frame} (cadre d'acier) ;</li>
 *     <li>les FACES (un seul bord) : {@code warehouse_crate_panel} (panneau de bois traite) ou {@code warehouse_crate_port} (port) ; la
 *     TRAPPE ({@code warehouse_crate_hatch}, le coeur qui garde le contenu) sur la ligne du milieu du dessus, une seule ;</li>
 *     <li>l'INTERIEUR (u = 1, v = 1, hors des bouts) : vide.</li>
 * </ul>
 * La plus grande caisse complete autour de la trappe se forme (une caisse courte tient dans une longue en chantier).
 */
public final class CrateLayout {

    /** Types d'objets d'une caisse, quelle que soit sa longueur (demande de Guillaume, 07/11 : 256 par caisse). */
    public static final int TYPES = 256;

    /** Longueurs admises : 3, 5 ou 7 blocs ; contenance : {@link #TYPES} types d'objets quelle que soit la longueur, piles par type. */
    public enum Length {
        SHORT(3, 32),
        MEDIUM(5, 64),
        LONG(7, 128);

        public final int blocks;
        /** Piles de chaque type (x la taille de pile de l'objet). */
        public final int piles;

        Length(int blocks, int piles) {
            this.blocks = blocks;
            this.piles = piles;
        }

        /** Nombre de types d'objets : {@link CrateLayout#TYPES} pour toutes les longueurs (1.0.4-beta : 256, au lieu d'un par bloc). */
        public int types() {
            return TYPES;
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        public static Length of(int n) {
            Length[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, n))];
        }

        /** Taille affichee « 3 x n x 3 ». */
        public String size() {
            return "3 × " + blocks + " × 3";
        }
    }

    public enum Kind {
        FRAME, PANEL, HATCH, PORT, INSIDE
    }

    /** Roles de longueur (modeles) : bout de depart, voisin d'un bout, milieu courant, centre, bout d'arrivee. */
    public static final int W_START = 0, W_NEAR = 1, W_MID = 2, W_CENTER = 3, W_END = 4, W_ROLES = 5;
    /** Nombre de valeurs de l'etat {@code part} : 0 (piece libre) puis 1 + (wr * 9 + v * 3 + u). */
    public static final int PARTS = 1 + W_ROLES * 9;

    /** Caisse trouvee : longueur, axe, origine (case u = v = w = 0), trappe, cases (trappe comprise), ports. */
    public record Found(Length length, Direction.Axis axis, BlockPos origin, BlockPos hatch, List<BlockPos> cells, List<BlockPos> ports) {

        /** Place de la case {@code p} (u, v, w) dans cette caisse, ou null si elle n'en est pas. */
        @Nullable
        public int[] local(BlockPos p) {
            int dx = p.getX() - origin.getX(), dy = p.getY() - origin.getY(), dz = p.getZ() - origin.getZ();
            int u = axis == Direction.Axis.Z ? dx : dz, w = axis == Direction.Axis.Z ? dz : -dx;
            if (u < 0 || u > 2 || dy < 0 || dy > 2 || w < 0 || w >= length.blocks) {
                return null;
            }
            return new int[]{u, dy, w};
        }

        /** Etat {@code part} peint de la case {@code p} (0 si elle n'est pas dans la caisse). */
        public int part(BlockPos p) {
            int[] l = local(p);
            return l == null ? 0 : partIndex(l[0], l[1], wRole(l[2], length.blocks));
        }
    }

    /**
     * Releve : la caisse formee (ou null), sinon la premiere case fausse de la caisse la plus proche (le moins de cases fausses ; a egalite,
     * la plus longue) ; {@code unreadable} : une case n'est pas chargee (rien ne change).
     */
    public record Survey(@Nullable Found found, @Nullable BlockPos fault, Length nearest, boolean unreadable) {
    }

    private CrateLayout() {
    }

    /** Role de longueur de la case w d'une caisse de n blocs. */
    public static int wRole(int w, int n) {
        if (w <= 0) {
            return W_START;
        }
        if (w >= n - 1) {
            return W_END;
        }
        if (w == (n - 1) / 2) {
            return W_CENTER;
        }
        return w == 1 || w == n - 2 ? W_NEAR : W_MID;
    }

    public static int partIndex(int u, int v, int wr) {
        return 1 + wr * 9 + v * 3 + u;
    }

    /** (u, v, role de longueur) d'un etat {@code part} (1 .. PARTS - 1). */
    public static int[] decode(int part) {
        int i = Math.max(0, part - 1);
        return new int[]{i % 3, (i / 3) % 3, i / 9};
    }

    /** Nombre de bords de la boite touches par une case (0 : interieur). */
    public static int borders(int u, int v, int wr) {
        return (u != 1 ? 1 : 0) + (v != 1 ? 1 : 0) + (wr == W_START || wr == W_END ? 1 : 0);
    }

    /** Sorte attendue d'une case (la trappe : seulement la case de trappe choisie). */
    public static Kind kind(int u, int v, int w, int n) {
        int b = borders(u, v, wRole(w, n));
        return b == 0 ? Kind.INSIDE : b >= 2 ? Kind.FRAME : Kind.PANEL;
    }

    /** Position dans le monde de la case (u, v, w) d'une caisse d'origine {@code origin}. */
    public static BlockPos at(BlockPos origin, Direction.Axis axis, int u, int v, int w) {
        return axis == Direction.Axis.Z ? origin.offset(u, v, w) : origin.offset(-w, v, u);
    }

    /** Origine d'une caisse dont la trappe est la case (1, 2, k). */
    public static BlockPos originFor(BlockPos hatch, Direction.Axis axis, int k) {
        return axis == Direction.Axis.Z ? hatch.offset(-1, -2, -k) : hatch.offset(k, -2, -1);
    }

    /** Une case du plan (repere de la caisse) et sa sorte. */
    public record Cell(int u, int v, int w, Kind kind) {
    }

    /** Cases d'une caisse de cette longueur, trappe au centre du dessus (plan projete, banc, GameTests). */
    public static List<Cell> cells(Length length) {
        List<Cell> out = new ArrayList<>();
        int n = length.blocks, center = (n - 1) / 2;
        for (int w = 0; w < n; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    Kind k = kind(u, v, w, n);
                    if (u == 1 && v == 2 && w == center) {
                        k = Kind.HATCH;
                    }
                    out.add(new Cell(u, v, w, k));
                }
            }
        }
        return out;
    }

    public static Block block(Kind kind) {
        return switch (kind) {
            case FRAME -> PiWarehouse.CRATE_FRAME.getBlock();
            case PANEL -> PiWarehouse.CRATE_PANEL.getBlock();
            case PORT -> PiWarehouse.CRATE_PORT.getBlock();
            case HATCH -> PiWarehouse.CRATE_HATCH.getBlock();
            case INSIDE -> net.minecraft.world.level.block.Blocks.AIR;
        };
    }

    /** La case convient-elle ? (faces : panneau ou port ; trappe : seulement celle du releve). */
    private static boolean fits(BlockState s, Kind k, boolean hatchCell) {
        if (hatchCell) {
            return s.is(PiWarehouse.CRATE_HATCH.getBlock());
        }
        return switch (k) {
            case INSIDE -> s.isAir();
            case FRAME -> s.is(PiWarehouse.CRATE_FRAME.getBlock());
            case PANEL, PORT -> s.is(PiWarehouse.CRATE_PANEL.getBlock()) || s.is(PiWarehouse.CRATE_PORT.getBlock());
            case HATCH -> s.is(PiWarehouse.CRATE_HATCH.getBlock());
        };
    }

    /** Cases fausses (et la premiere) d'une caisse ; null : une case n'est pas chargee. */
    @Nullable
    private static int[] bad(Structure.View view, BlockPos origin, Direction.Axis axis, Length length, BlockPos hatch, BlockPos[] first) {
        int bad = 0, n = length.blocks;
        for (int w = 0; w < n; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    BlockPos p = at(origin, axis, u, v, w);
                    if (!view.loaded(p)) {
                        return null;
                    }
                    if (!fits(view.getBlockState(p), kind(u, v, w, n), p.equals(hatch))) {
                        if (bad++ == 0) {
                            first[0] = p;
                        }
                    }
                }
            }
        }
        return new int[]{bad};
    }

    /** Premiere case fausse d'une caisse (null : complete ou illisible). Banc et GameTests. */
    @Nullable
    public static BlockPos fault(Structure.View view, BlockPos hatch, Direction.Axis axis, Length length) {
        BlockPos[] first = new BlockPos[1];
        int[] b = bad(view, originFor(hatch, axis, (length.blocks - 1) / 2), axis, length, hatch, first);
        return b == null || b[0] == 0 ? null : first[0];
    }

    /**
     * Releve de la caisse d'une trappe : la plus longue caisse complete (axes Z puis X, toutes les places de la trappe sur la ligne du
     * milieu du dessus), sinon la faute de la caisse la plus proche. Meme code en jeu, au banc et aux GameTests.
     */
    public static Survey survey(Structure.View view, BlockPos hatch) {
        Length nearest = Length.SHORT;
        BlockPos fault = null;
        int best = Integer.MAX_VALUE;
        Length[] all = Length.values();
        for (int i = all.length - 1; i >= 0; i--) {
            Length length = all[i];
            int n = length.blocks;
            for (Direction.Axis axis : new Direction.Axis[]{Direction.Axis.Z, Direction.Axis.X}) {
                for (int j = 0; j < n - 2; j++) {
                    //Du centre vers les bouts : la trappe au milieu d'abord (plan projete)
                    int k = 1 + Math.floorMod((n - 1) / 2 - 1 + (j % 2 == 0 ? j / 2 : -(j + 1) / 2), n - 2);
                    BlockPos origin = originFor(hatch, axis, k);
                    BlockPos[] first = new BlockPos[1];
                    int[] b = bad(view, origin, axis, length, hatch, first);
                    if (b == null) {
                        return new Survey(null, null, length, true);
                    }
                    if (b[0] == 0) {
                        return new Survey(found(view, origin, axis, length, hatch), null, length, false);
                    }
                    if (b[0] < best) {
                        best = b[0];
                        fault = first[0];
                        nearest = length;
                    }
                }
            }
        }
        return new Survey(null, fault, nearest, false);
    }

    private static Found found(Structure.View view, BlockPos origin, Direction.Axis axis, Length length, BlockPos hatch) {
        List<BlockPos> cells = new ArrayList<>(), ports = new ArrayList<>();
        for (int w = 0; w < length.blocks; w++) {
            for (int v = 0; v < 3; v++) {
                for (int u = 0; u < 3; u++) {
                    if (kind(u, v, w, length.blocks) == Kind.INSIDE) {
                        continue;
                    }
                    BlockPos p = at(origin, axis, u, v, w).immutable();
                    cells.add(p);
                    if (view.getBlockState(p).is(PiWarehouse.CRATE_PORT.getBlock())) {
                        ports.add(p);
                    }
                }
            }
        }
        return new Found(length, axis, origin.immutable(), hatch.immutable(), List.copyOf(cells), List.copyOf(ports));
    }
}
