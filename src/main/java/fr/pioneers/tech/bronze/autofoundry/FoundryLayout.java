package fr.pioneers.tech.bronze.autofoundry;

import fr.pioneers.tech.bronze.foundry.Metals;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.registries.PiBronze;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.2-beta (lot 4, notes de Guillaume : « un nouveau systeme de forge entierement automatique : multibloc de 1x1x2, 2x2x3 ou 3x3x5 avec un
 * foyer en dessous d'une cuve ») : PLAN de la fonderie automatique, SOURCE UNIQUE de la verification (tile et banc des multiblocs), du plan
 * projete (onglet Structure, Maj + clic droit, page JEI « Multiblocs ») et des GameTests.
 * <p>
 * Repere : le controleur (la porte du foyer) en (0, 0, 0), x vers la droite (sens horaire de la facade), z vers l'avant (la facade du
 * controleur regarde z+ ; la structure est DERRIERE lui, z &lt;= 0), y vers le haut. Une taille = un cote (carre) et une hauteur :
 * <ul>
 *     <li>y 0, le FOYER : le controleur sur la rangee de devant (z 0), les autres cases en {@code fire_brick_firebox} (1.0.4-beta, lot 24 :
 *     grande taille = la fonderie primitive de Guillaume, une TUYERE au milieu de la rangee du fond) ;</li>
 *     <li>y 1 a hauteur - 1, la CUVE : toutes les cases en {@code foundry_vat}.</li>
 * </ul>
 * Les trois tailles fondent les memes metaux (ceux du fourneau a creuset) ; seules la contenance et la vitesse changent ({@link Size}). Le
 * controleur peut etre sur n'importe quelle case de la rangee de devant ({@code offset}) ; le plan projete le met au milieu (a gauche pour
 * la taille 2 x 2). La plus grande taille COMPLETE se forme (une petite fonderie tient dans une grande en chantier).
 */
public final class FoundryLayout {

    /** Tailles : cote, hauteur, contenance (lingots), vitesse de fusion et de coulee (multiplicateur). */
    public enum Size {
        SMALL(1, 2, 9, 1),
        MEDIUM(2, 3, 36, 2),
        LARGE(3, 5, 144, 4);

        public final int side, height, ingots, speed;

        Size(int side, int height, int ingots, int speed) {
            this.side = side;
            this.height = height;
            this.ingots = ingots;
            this.speed = speed;
        }

        /** Contenance de la cuve (mB). */
        public int capacity() {
            return ingots * Metals.INGOT;
        }

        /** Nombre de cases de cuve. */
        public int vats() {
            return side * side * (height - 1);
        }

        public String key() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** Taille d'indice n (0 petite .. 2 grande), bornee. */
        public static Size of(int n) {
            Size[] all = values();
            return all[Math.max(0, Math.min(all.length - 1, n))];
        }
    }

    public enum Kind {
        CONTROLLER, FIREBOX, VAT,
        /** 1.0.4-beta (lot 24) : tuyere de la grande fonderie (forme de Guillaume : au milieu de l'arriere du foyer). */
        TUYERE
    }

    /** Une case du plan (repere du controleur). */
    public record Cell(int x, int y, int z, Kind kind) {
    }

    /**
     * Structure trouvee : taille, decalage du controleur sur sa rangee, cases (le controleur compris), cuves, foyers, cases du dessus ;
     * 1.0.4-beta (lot 24) : tuyeres (grande taille).
     */
    public record Found(Size size, int offset, List<BlockPos> cells, List<BlockPos> vats, List<BlockPos> fireboxes, List<BlockPos> top,
                        List<BlockPos> tuyeres) {
    }

    /**
     * Releve : la structure formee (ou null), sinon la premiere case fausse de la taille la plus proche (le moins de cases fausses ; a
     * egalite, la plus grande) ; {@code unreadable} : une case n'est pas chargee (rien ne change).
     */
    public record Survey(@Nullable Found found, @Nullable BlockPos fault, Size nearest, boolean unreadable) {
    }

    private FoundryLayout() {
    }

    /** Decalage par defaut du controleur sur la rangee de devant (plan projete) : au milieu, a gauche pour un cote pair. */
    public static int defaultOffset(Size size) {
        return (size.side - 1) / 2;
    }

    /**
     * Cases d'une taille, controleur a la colonne {@code offset} (0 .. cote - 1) de la rangee de devant. 1.0.4-beta (lot 24) : la GRANDE taille
     * est la fonderie primitive dessinee par Guillaume (NBT {@code tools/gc/industry/nbt/dev_v1/primitive_foundry.nbt}, {@code HouseNbt
     * auto_foundry}) : la tuyere au milieu de la rangee du fond du foyer (verifie par {@code tools/check_p104_24.py}).
     */
    public static List<Cell> cells(Size size, int offset) {
        List<Cell> out = new ArrayList<>();
        for (int y = 0; y < size.height; y++) {
            for (int z = 0; z > -size.side; z--) {
                for (int i = 0; i < size.side; i++) {
                    int x = i - offset;
                    Kind kind = y > 0 ? Kind.VAT : x == 0 && z == 0 ? Kind.CONTROLLER
                          : size == Size.LARGE && z == -(size.side - 1) && i == (size.side - 1) / 2 ? Kind.TUYERE : Kind.FIREBOX;
                    out.add(new Cell(x, y, z, kind));
                }
            }
        }
        return out;
    }

    /** Position dans le monde d'une case, pour un controleur en {@code controller} dont la facade regarde {@code forward}. */
    public static BlockPos at(BlockPos controller, Direction forward, int x, int y, int z) {
        Direction right = forward.getClockWise();
        return controller.offset(right.getStepX() * x + forward.getStepX() * z, y, right.getStepZ() * x + forward.getStepZ() * z);
    }

    /** Bloc attendu d'une case (le controleur : celui de la fonderie). */
    public static Block block(Kind kind) {
        return switch (kind) {
            case CONTROLLER -> PiBronze.AUTO_FOUNDRY.getBlock();
            case FIREBOX -> PiBronze.FIRE_BRICK_FIREBOX.getBlock();
            case VAT -> PiBronze.FOUNDRY_VAT.getBlock();
            case TUYERE -> PiBronze.TUYERE.getBlock();
        };
    }

    /** Nombre de cases fausses (hors controleur) et la premiere, pour une taille et un decalage ; null si une case n'est pas chargee. */
    @Nullable
    private static int[] bad(Structure.View view, BlockPos controller, Direction forward, Size size, int offset, BlockPos[] first) {
        int bad = 0;
        for (Cell c : cells(size, offset)) {
            if (c.kind() == Kind.CONTROLLER) {
                continue;
            }
            BlockPos p = at(controller, forward, c.x(), c.y(), c.z());
            if (!view.loaded(p)) {
                return null;
            }
            BlockState s = view.getBlockState(p);
            if (!s.is(block(c.kind()))) {
                if (bad++ == 0) {
                    first[0] = p;
                }
            }
        }
        return new int[]{bad};
    }

    /** Premiere case fausse d'une taille et d'un decalage (null : complete ou illisible). Banc et GameTests. */
    @Nullable
    public static BlockPos fault(Structure.View view, BlockPos controller, Direction forward, Size size, int offset) {
        BlockPos[] first = new BlockPos[1];
        int[] b = bad(view, controller, forward, size, offset, first);
        return b == null || b[0] == 0 ? null : first[0];
    }

    /**
     * Releve de la fonderie d'un controleur en {@code controller} tourne vers {@code forward} : la plus grande taille complete (tous les
     * decalages de la rangee de devant essayes), sinon la faute de la taille la plus proche. Meme code en jeu, au banc et aux GameTests.
     */
    public static Survey survey(Structure.View view, BlockPos controller, Direction forward) {
        Size nearest = Size.SMALL;
        BlockPos fault = null;
        int best = Integer.MAX_VALUE;
        Size[] sizes = Size.values();
        for (int k = sizes.length - 1; k >= 0; k--) {
            Size size = sizes[k];
            for (int n = 0; n < size.side; n++) {
                int offset = (defaultOffset(size) + n) % size.side;
                BlockPos[] first = new BlockPos[1];
                int[] b = bad(view, controller, forward, size, offset, first);
                if (b == null) {
                    return new Survey(null, null, size, true);
                }
                if (b[0] == 0) {
                    return new Survey(found(controller, forward, size, offset), null, size, false);
                }
                if (b[0] < best) {
                    best = b[0];
                    fault = first[0];
                    nearest = size;
                }
            }
        }
        return new Survey(null, fault, nearest, false);
    }

    private static Found found(BlockPos controller, Direction forward, Size size, int offset) {
        List<BlockPos> cells = new ArrayList<>(), vats = new ArrayList<>(), fireboxes = new ArrayList<>(), top = new ArrayList<>(), tuyeres = new ArrayList<>();
        cells.add(controller.immutable());
        for (Cell c : cells(size, offset)) {
            if (c.kind() == Kind.CONTROLLER) {
                continue;
            }
            BlockPos p = at(controller, forward, c.x(), c.y(), c.z());
            cells.add(p);
            if (c.kind() == Kind.VAT) {
                vats.add(p);
                if (c.y() == size.height - 1) {
                    top.add(p);
                }
            } else if (c.kind() == Kind.TUYERE) {
                tuyeres.add(p);
            } else {
                fireboxes.add(p);
            }
        }
        return new Found(size, offset, List.copyOf(cells), List.copyOf(vats), List.copyOf(fireboxes), List.copyOf(top), List.copyOf(tuyeres));
    }
}
