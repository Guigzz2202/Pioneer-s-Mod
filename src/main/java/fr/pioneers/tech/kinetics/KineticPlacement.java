package fr.pioneers.tech.kinetics;

import fr.pioneers.core.Directions;
import fr.pioneers.tech.kinetics.parts.CogBlock;
import fr.pioneers.tech.kinetics.parts.ShaftBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.42 : aides a la pose des arbres et des engrenages (Maj : pose brute, sans aide).
 * <ul>
 *   <li>Arbre tenu, clic sur le flanc d'un arbre : la ligne d'arbres s'allonge a son bout, du cote ou l'on regarde.</li>
 *   <li>Engrenage tenu, clic sur la jante d'un engrenage de l'autre taille : le nouveau se pose en diagonale, dans le meme plan, la ou il
 *       engrene (du cote de la jante ou l'on a clique).</li>
 *   <li>Axe choisi a la pose : celui qui raccorde le plus de voisins (bouts d'arbre tournes vers la case, engrenages qui engreneraient), a
 *       egalite celui de la face cliquee.</li>
 * </ul>
 */
public final class KineticPlacement {

    /** Longueur maximale de ligne d'arbres parcourue pour trouver son bout. */
    private static final int MAX_LINE = 32;

    private KineticPlacement() {
    }

    /** Bloc reellement clique (la case visee si elle est remplacable, sinon sa voisine derriere la face). */
    static BlockPos clicked(BlockPlaceContext context) {
        return context.replacingClickedOnBlock() ? context.getClickedPos() : context.getClickedPos().relative(context.getClickedFace().getOpposite());
    }

    /** Contexte de pose redirige par une aide, ou null (pose normale). */
    @Nullable
    public static BlockPlaceContext help(Block placing, BlockPlaceContext context) {
        //1.0.4-beta (lot 2) : poulie de station sur le dessus d'un comptoir ou d'une trappe (Maj + clic : le comptoir a une interface) : dans
        //la colonne, a la hauteur de la ligne voisine (descente au filin), sinon dessus comme avant ; lot 27 : la petite poulie (plus de
        //poulie d'entrepot)
        if (placing instanceof fr.pioneers.tech.kinetics.rope.PulleyBlock pulley && pulley.size == 1 && context.getClickedFace() == Direction.UP) {
            BlockPos below = clicked(context);
            BlockEntity be = context.getLevel().getBlockEntity(below);
            if (be instanceof fr.pioneers.logistics.warehouse.CounterTile || be instanceof fr.pioneers.logistics.warehouse.CrateHatchTile) {
                BlockPos at = fr.pioneers.logistics.warehouse.WarehouseLines.placeAbove(context.getLevel(), below);
                return at == null ? null : BlockPlaceContext.at(context, at, Direction.UP);
            }
        }
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return null;
        }
        Level level = context.getLevel();
        Direction face = context.getClickedFace();
        BlockPos clicked = clicked(context);
        BlockState state = level.getBlockState(clicked);
        if (placing instanceof ShaftBlock && state.getBlock() instanceof ShaftBlock && face.getAxis() != AxisKineticBlock.axis(state)) {
            return extendLine(context, clicked, AxisKineticBlock.axis(state));
        }
        if (placing instanceof CogBlock cog && state.getBlock() instanceof CogBlock other && other.large != cog.large
            && face.getAxis() != AxisKineticBlock.axis(state)) {
            return diagonal(context, clicked, face, AxisKineticBlock.axis(state));
        }
        return null;
    }

    private static BlockPlaceContext extendLine(BlockPlaceContext context, BlockPos start, Direction.Axis axis) {
        Vec3 look = context.getPlayer() != null ? context.getPlayer().getLookAngle() : Vec3.ZERO;
        double along = axis.choose(look.x, look.y, look.z);
        Direction dir = Direction.fromAxisAndDirection(axis, along >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        Level level = context.getLevel();
        BlockPos p = start;
        for (int i = 0; i < MAX_LINE; i++) {
            p = p.relative(dir);
            if (!level.isInWorldBounds(p)) {
                return null;
            }
            BlockState st = level.getBlockState(p);
            if (st.getBlock() instanceof ShaftBlock && AxisKineticBlock.axis(st) == axis) {
                continue;
            }
            BlockPlaceContext at = BlockPlaceContext.at(context, p, dir);
            return st.canBeReplaced(at) ? at : null;
        }
        return null;
    }

    private static BlockPlaceContext diagonal(BlockPlaceContext context, BlockPos cog, Direction rim, Direction.Axis axis) {
        //Troisieme axe : ni celui de l'engrenage, ni celui de la face ; le cote est donne par le point clique sur la jante
        Direction.Axis third = Direction.Axis.X;
        for (Direction.Axis a : Direction.Axis.values()) {
            if (a != axis && a != rim.getAxis()) {
                third = a;
            }
        }
        Vec3 hit = context.getClickLocation().subtract(Vec3.atCenterOf(cog));
        double side = third.choose(hit.x, hit.y, hit.z);
        Direction first = Direction.fromAxisAndDirection(third, side >= 0 ? Direction.AxisDirection.POSITIVE : Direction.AxisDirection.NEGATIVE);
        for (Direction e : new Direction[]{first, first.getOpposite()}) {
            BlockPos target = cog.relative(rim).relative(e);
            BlockPlaceContext at = BlockPlaceContext.at(context, target, rim);
            if (context.getLevel().isInWorldBounds(target) && context.getLevel().getBlockState(target).canBeReplaced(at)) {
                return at;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Choix de l'axe
    // ------------------------------------------------------------------

    /** Axe de pose : Maj = axe du regard ; sinon l'axe qui raccorde le plus de voisins, a egalite celui de la face cliquee. */
    public static Direction.Axis axis(Block placing, BlockPlaceContext context) {
        Player player = context.getPlayer();
        if (player != null && player.isShiftKeyDown()) {
            return context.getNearestLookingDirection().getAxis();
        }
        Direction.Axis preferred = context.getClickedFace().getAxis();
        Direction.Axis best = preferred;
        double bestScore = 0;
        for (Direction.Axis a : Direction.Axis.values()) {
            double score = score(placing, context.getLevel(), context.getClickedPos(), a) + (a == preferred ? 0.5 : 0);
            if (score > bestScore + 1e-9) {
                bestScore = score;
                best = a;
            }
        }
        return best;
    }

    /** Nombre de raccords que ferait la piece posee en {@code pos} avec l'axe {@code axis}. */
    public static int score(Block placing, Level level, BlockPos pos, Direction.Axis axis) {
        int n = 0;
        for (Direction.AxisDirection sign : Direction.AxisDirection.values()) {
            Direction face = Direction.fromAxisAndDirection(axis, sign);
            if (level.getBlockEntity(pos.relative(face)) instanceof KineticNode node && node.shaftFactor(face.getOpposite()) != 0) {
                n++;
            }
        }
        if (placing instanceof CogBlock cog) {
            for (Direction d : Directions.ALL) {
                if (d.getAxis() == axis) {
                    continue;
                }
                CogBlock near = cogAt(level, pos.relative(d), axis);
                if (!cog.large && near != null && !near.large) {
                    n++;                                                     //pignon contre pignon
                }
                for (Direction e : Directions.ALL) {
                    if (e.getAxis() == axis || e.getAxis() == d.getAxis() || e.ordinal() < d.ordinal()) {
                        continue;
                    }
                    CogBlock diag = cogAt(level, pos.relative(d).relative(e), axis);
                    if (diag != null && diag.large != cog.large) {
                        n++;                                                 //rouet et pignon en diagonale
                    }
                }
            }
        }
        return n;
    }

    @Nullable
    private static CogBlock cogAt(Level level, BlockPos pos, Direction.Axis axis) {
        BlockState state = level.getBlockState(pos);
        BlockEntity tile = level.getBlockEntity(pos);
        return state.getBlock() instanceof CogBlock cog && tile != null && AxisKineticBlock.axis(state) == axis ? cog : null;
    }
}
