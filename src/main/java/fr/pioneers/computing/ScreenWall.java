package fr.pioneers.computing;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 26, Guillaume : « l'ecran de supervision est tres buggue », PJ : assemblage en L dessine comme deux ecrans, cadres
 * dedoubles) : DECOUPAGE D'UN ASSEMBLAGE D'ECRANS EN ECRANS RECTANGULAIRES. Les dalles de meme orientation qui se touchent dans le plan du
 * mur forment un assemblage ; il est decoupe en rectangles, LE PLUS GRAND D'ABORD (surface, puis le plus large, puis le plus bas, puis le
 * plus a gauche ; {@link ScreenBlock#MAX_W} x {@link ScreenBlock#MAX_H} au plus) : chaque rectangle est UN ecran (cadre autour, contenu
 * dedans, liaison portee par son bloc en bas a gauche). Une forme en L de trois dalles = un ecran de 2 x 1 et un ecran de 1 x 1. Meme calcul
 * cote serveur (bloc maitre, raccords du modele) et client (rendu). Repere du mur : u vers la droite du spectateur qui regarde l'ecran,
 * v vers le haut.
 */
public final class ScreenWall {

    /** Assemblage au plus de cette taille (dalles) : au-dela, le reste est ignore (forme gardee raisonnable). */
    static final int CAP = 256;
    /** Ecran de 6 x 4 dalles au plus (aussi {@code ScreenBlock.MAX_W / MAX_H}). */
    public static final int MAX_W = 6, MAX_H = 4;

    /** Gauche du spectateur qui regarde l'ecran (l'ecran regarde vers {@code facing}). */
    public static Direction left(Direction facing) {
        return facing.getClockWise();
    }

    /** Droite du spectateur. */
    public static Direction right(Direction facing) {
        return facing.getCounterClockWise();
    }

    private ScreenWall() {
    }

    /** Ecran rectangulaire : bloc maitre (en bas a gauche vu de face), largeur (vers la droite du spectateur), hauteur. */
    public record Rect(BlockPos master, int w, int h) {

        /** La case (u, v) du rectangle contient-elle {@code p} (meme orientation) ? */
        public boolean contains(BlockPos p, Direction facing) {
            Direction right = right(facing);
            int du = (p.getX() - master.getX()) * right.getStepX() + (p.getZ() - master.getZ()) * right.getStepZ();
            int dv = p.getY() - master.getY();
            int side = (p.getX() - master.getX()) * facing.getStepX() + (p.getZ() - master.getZ()) * facing.getStepZ();
            return side == 0 && du >= 0 && du < w && dv >= 0 && dv < h;
        }
    }

    private static boolean screen(BlockGetter level, BlockPos p, Direction f, @Nullable BlockPos extra) {
        if (p.equals(extra)) {
            return true;
        }
        BlockState s = level.getBlockState(p);
        return s.getBlock() instanceof ScreenBlock && s.getValue(ScreenBlock.FACING) == f;
    }

    /** Dalles de l'assemblage de {@code pos} (meme orientation, voisines dans le plan) ; {@code extra} : case comptee comme dalle (pose). */
    public static List<BlockPos> members(BlockGetter level, BlockPos pos, Direction f, @Nullable BlockPos extra) {
        Direction left = left(f), right = right(f);
        List<BlockPos> out = new ArrayList<>();
        if (!screen(level, pos, f, extra)) {
            return out;
        }
        Set<BlockPos> seen = new HashSet<>();
        ArrayDeque<BlockPos> todo = new ArrayDeque<>();
        todo.add(pos.immutable());
        seen.add(pos.immutable());
        while (!todo.isEmpty() && out.size() < CAP) {
            BlockPos p = todo.poll();
            out.add(p);
            for (BlockPos q : new BlockPos[]{p.relative(left), p.relative(right), p.above(), p.below()}) {
                if (!seen.contains(q) && screen(level, q, f, extra)) {
                    seen.add(q);
                    todo.add(q);
                }
            }
        }
        return out;
    }

    /** Decoupage de l'assemblage de {@code pos} : dalle -> son ecran rectangulaire (vide si {@code pos} n'est pas une dalle). */
    public static Map<BlockPos, Rect> partition(BlockGetter level, BlockPos pos, Direction f, @Nullable BlockPos extra) {
        return partition(members(level, pos, f, extra), f);
    }

    /** Decoupage d'un ensemble de dalles coplanaires de meme orientation (le plus grand rectangle d'abord). */
    public static Map<BlockPos, Rect> partition(List<BlockPos> cells, Direction f) {
        Map<BlockPos, Rect> out = new HashMap<>();
        if (cells.isEmpty()) {
            return out;
        }
        Direction right = right(f);
        BlockPos o = cells.get(0);
        Map<Long, BlockPos> free = new HashMap<>();
        for (BlockPos p : cells) {
            free.put(key(u(p, o, right), p.getY() - o.getY()), p);
        }
        while (!free.isEmpty()) {
            int bu = 0, bv = 0, bw = 0, bh = 0;
            for (long k : free.keySet()) {
                int u = (int) (k >> 32), v = (int) k;
                for (int w = 1; w <= MAX_W && free.containsKey(key(u + w - 1, v)); w++) {
                    int h = 1;
                    while (h < MAX_H && row(free, u, v + h, w)) {
                        h++;
                    }
                    if (better(w, h, u, v, bw, bh, bu, bv)) {
                        bu = u;
                        bv = v;
                        bw = w;
                        bh = h;
                    }
                }
            }
            Rect r = new Rect(free.get(key(bu, bv)), bw, bh);
            for (int du = 0; du < bw; du++) {
                for (int dv = 0; dv < bh; dv++) {
                    out.put(free.remove(key(bu + du, bv + dv)), r);
                }
            }
        }
        return out;
    }

    /** Le rectangle (w, h) en (u, v) bat-il le meilleur jusqu'ici ? Surface, puis largeur, puis plus bas, puis plus a gauche. */
    static boolean better(int w, int h, int u, int v, int bw, int bh, int bu, int bv) {
        if (bw == 0) {
            return true;
        }
        if (w * h != bw * bh) {
            return w * h > bw * bh;
        }
        if (w != bw) {
            return w > bw;
        }
        if (v != bv) {
            return v < bv;
        }
        return u < bu;
    }

    private static boolean row(Map<Long, BlockPos> free, int u, int v, int w) {
        for (int du = 0; du < w; du++) {
            if (!free.containsKey(key(u + du, v))) {
                return false;
            }
        }
        return true;
    }

    private static int u(BlockPos p, BlockPos o, Direction right) {
        return (p.getX() - o.getX()) * right.getStepX() + (p.getZ() - o.getZ()) * right.getStepZ();
    }

    private static long key(int u, int v) {
        return (long) u << 32 | v & 0xFFFFFFFFL;
    }

    /** Ecran rectangulaire de la dalle {@code pos} (null si ce n'est pas une dalle). */
    @Nullable
    public static Rect of(BlockGetter level, BlockPos pos, Direction f) {
        return partition(level, pos, f, null).get(pos);
    }
}
