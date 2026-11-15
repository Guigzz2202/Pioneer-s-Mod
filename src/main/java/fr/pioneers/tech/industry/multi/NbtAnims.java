package fr.pioneers.tech.industry.multi;

import fr.pioneers.api.multiblock.StructureMatcher;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 25) : REPERE DES ANIMATIONS des multiblocs a NBT ({@link GeneratedNbtModels}, tables de {@code T:industry/nbt_models.py}).
 * Partie commune (serveur et client) : passage du repere de la structure (boite canonique du NBT : x a droite en regardant la facade, y en
 * haut, z vers l'interieur ; controleur en (cx, cy, cz)) au monde, d'apres la position du controleur et sa facade (meme convention que
 * {@link StructureMatcher} : avant = -facade, droite = avant tourne d'un quart horaire).
 * <ul>
 *     <li>{@link #view} : vue de la machine interieure ({@link BigInterior.View}) dans la BOITE choisie par la table (transfert des modeles
 *     d'avant : plus la boite des modules), cages = cadres de la structure qui longent la boite (portiques des chaines) ;</li>
 *     <li>{@link #stackedCells} : cases des blocs empilables qui tournent (masques a la formation, dessines par le controleur) ;</li>
 *     <li>{@link #point} / {@link #cell} : un point ou une case de la table dans le monde ; {@link #envelope} : boite de toute la structure.</li>
 * </ul>
 */
public final class NbtAnims {

    private NbtAnims() {
    }

    /** Animations d'un multibloc industriel (cle du catalogue), ou null (pas de NBT ou rien a animer). */
    @Nullable
    public static GeneratedNbtModels.Anim of(String key) {
        return GeneratedNbtModels.of(key);
    }

    /** Case (x, y, z) du repere de la structure (taille et controleur cx, cy, cz) dans le monde. */
    public static BlockPos cell(BlockPos c, Direction front, int cx, int cy, int cz, int x, int y, int z) {
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        return StructureMatcher.set(new BlockPos.MutableBlockPos(), c, forward, right, x - cx, y - cy, z - cz).immutable();
    }

    /** Point (en cases, flottant) du repere de la structure dans le monde : {x, y, z}. */
    public static double[] point(BlockPos c, Direction front, int cx, int cy, int cz, double x, double y, double z) {
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        double rx = x - cx - 0.5, rz = z - cz - 0.5;
        return new double[]{c.getX() + 0.5 + right.getStepX() * rx + forward.getStepX() * rz, c.getY() + (y - cy),
              c.getZ() + 0.5 + right.getStepZ() * rx + forward.getStepZ() * rz};
    }

    /** Boite du monde (coin bas, coin haut, bornes comprises) de toute la structure. */
    public static BlockPos[] envelope(BlockPos c, Direction front, GeneratedNbtShapes.Shape s) {
        return box(c, front, s.cx(), s.cy(), s.cz(), 0, 0, 0, s.sx() - 1, s.sy() - 1, s.sz() - 1);
    }

    /**
     * Boite de rendu du controleur d'une structure de Guillaume animee (machine interieure, colonnes tournantes, manivelles) : toute la
     * structure, un bloc de marge ; null si la machine n'a pas de NBT ou pas d'animation (rendu du controleur, client).
     */
    @Nullable
    public static AABB renderBox(String key, BlockPos c, BlockState state) {
        GeneratedNbtShapes.Shape s = GeneratedNbtShapes.of(key);
        if (s == null || of(key) == null || !state.hasProperty(ControllerBlock.FACING)) {
            return null;
        }
        BlockPos[] env = envelope(c, state.getValue(ControllerBlock.FACING), s);
        return new AABB(env[0].getX(), env[0].getY(), env[0].getZ(), env[1].getX() + 1, env[1].getY() + 1, env[1].getZ() + 1).inflate(1);
    }

    /** Boite du monde (coin bas, coin haut, bornes comprises) d'une boite du repere de la structure. */
    public static BlockPos[] box(BlockPos c, Direction front, int cx, int cy, int cz, int x0, int y0, int z0, int x1, int y1, int z1) {
        BlockPos a = cell(c, front, cx, cy, cz, x0, y0, z0), b = cell(c, front, cx, cy, cz, x1, y1, z1);
        return new BlockPos[]{new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ())),
              new BlockPos(Math.max(a.getX(), b.getX()), Math.max(a.getY(), b.getY()), Math.max(a.getZ(), b.getZ()))};
    }

    /**
     * Vue de la machine interieure d'apres la boite de la table : boite du monde, axe long (x de la structure = la droite du controleur),
     * cages (cases de l'axe ou un CADRE de la structure longe la boite, au niveau du bas : portiques des chaines), profil plein (boite).
     */
    public static BigInterior.View view(BlockPos c, Direction front, GeneratedNbtShapes.Shape s, GeneratedNbtModels.Model m, int tint) {
        BlockPos[] b = box(c, front, s.cx(), s.cy(), s.cz(), m.x0(), m.y0(), m.z0(), m.x1(), m.y1(), m.z1());
        BlockPos min = b[0], max = b[1];
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        boolean alongX = (m.alongX() ? right : forward).getAxis() == Direction.Axis.X;
        int a0 = alongX ? min.getX() : min.getZ(), a1 = alongX ? max.getX() : max.getZ();
        int c0 = alongX ? min.getZ() : min.getX(), c1 = alongX ? max.getZ() : max.getX();
        List<Integer> stands = new ArrayList<>();
        for (int a = a0; a <= a1; a++) {
            BlockPos lo = alongX ? new BlockPos(a, min.getY(), c0 - 1) : new BlockPos(c0 - 1, min.getY(), a);
            BlockPos hi = alongX ? new BlockPos(a, min.getY(), c1 + 1) : new BlockPos(c1 + 1, min.getY(), a);
            if (letter(c, front, s, lo) == 'F' || letter(c, front, s, hi) == 'F') {
                stands.add((a - a0) * 16 + 8);
            }
        }
        int wx = max.getX() - min.getX() + 1, wz = max.getZ() - min.getZ() + 1;
        int[] profile = new int[max.getY() - min.getY() + 1];
        Arrays.fill(profile, Math.min(wx, wz) * 10 + 1);
        return new BigInterior.View(m.kind(), min, max, alongX, stands.stream().mapToInt(Integer::intValue).toArray(), profile, tint);
    }

    /** Lettre du NBT a une case du monde ('.' hors de la boite). */
    public static char letter(BlockPos c, Direction front, GeneratedNbtShapes.Shape s, BlockPos p) {
        int[] r = StructureMatcher.relative(c, front, p);
        int x = r[0] + s.cx(), y = r[1] + s.cy(), z = r[2] + s.cz();
        if (x < 0 || y < 0 || z < 0 || x >= s.sx() || y >= s.sy() || z >= s.sz()) {
            return '.';
        }
        return s.at(x, y, z);
    }

    /**
     * Cases du monde des colonnes de blocs empilables qui tournent (lettres D, P et, depuis la 1.0.4-beta lot 26, R : les rotors poses dans
     * la colonne centrale des turbines).
     */
    public static List<BlockPos> stackedCells(String key, BlockPos c, Direction front) {
        GeneratedNbtModels.Anim a = of(key);
        GeneratedNbtShapes.Shape s = GeneratedNbtShapes.of(key);
        if (a == null || s == null || a.spins().isEmpty()) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>();
        for (GeneratedNbtModels.Spin sp : a.spins()) {
            for (int y = sp.y0(); y <= sp.y1(); y++) {
                out.add(cell(c, front, s.cx(), s.cy(), s.cz(), sp.x(), y, sp.z()));
            }
        }
        return out;
    }
}
