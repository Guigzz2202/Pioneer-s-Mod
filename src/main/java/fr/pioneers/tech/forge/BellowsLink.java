package fr.pioneers.tech.forge;

import fr.pioneers.tech.forge.casting.HandBellowsTile;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 26, Guillaume : « rendre la connexion des soufflets plus smart quand on les pose a cote d'un bloc eligible ») : ORIENTATION
 * AUTOMATIQUE des soufflets (soufflet a main, soufflet mecanique) vers ce qu'ils peuvent alimenter, sans rien changer a leur mecanique :
 * <ul>
 *     <li>a la pose : le bloc VISE (celui contre lequel on pose) s'il est eligible ; sinon le SEUL voisin eligible ; s'il y en a plusieurs,
 *     celui que le joueur regarde ; sinon l'orientation habituelle ;</li>
 *     <li>quand un bloc eligible apparait a cote d'un soufflet qui n'alimente rien (pose, feu allume...), le soufflet se tourne vers lui ;
 *     le soufflet mecanique seulement s'il garde l'axe de son arbre ou si aucun arbre n'y est branche (rien ne casse dans la transmission).</li>
 * </ul>
 * Eligible = ce qu'il alimente deja quand il est tourne vers lui : un recepteur d'air ({@link ForgeHeat#airReceiver} : tuyere, haut fourneau,
 * fonderie, creuset, foyer de forge, soufflet a main...), un feu de camp ou une source de chaleur de la forge primaire (soufflet a main), un
 * four vanilla (soufflet mecanique).
 */
public final class BellowsLink {

    private BellowsLink() {
    }

    /** Le soufflet ({@code mechanical} : mecanique, sinon a main) alimente-t-il ce qu'il y a en {@code target} ? */
    public static boolean eligible(Level level, BlockPos target, boolean mechanical) {
        if (!level.isLoaded(target)) {
            return false;
        }
        BlockState state = level.getBlockState(target);
        BlockEntity be = state.hasBlockEntity() ? level.getBlockEntity(target) : null;
        if (mechanical) {
            return ForgeHeat.airReceiver(level, target) != null || be instanceof AbstractFurnaceBlockEntity;
        }
        if (be instanceof HandBellowsTile) {
            return false;
        }
        return state.getBlock() instanceof CampfireBlock || be instanceof ForgeHeat.HeatSource || ForgeHeat.airReceiver(level, target) != null;
    }

    /**
     * Orientation a la pose (direction vers le bloc alimente) : bloc vise, puis seul voisin eligible, puis regard s'il est eligible, sinon
     * {@code fallback}.
     */
    public static Direction placement(BlockPlaceContext ctx, boolean mechanical, Direction fallback) {
        Level level = ctx.getLevel();
        BlockPos pos = ctx.getClickedPos();
        Direction clicked = ctx.getClickedFace();
        if (clicked.getAxis().isHorizontal() && eligible(level, pos.relative(clicked.getOpposite()), mechanical)) {
            return clicked.getOpposite();
        }
        Direction only = null;
        int count = 0;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (eligible(level, pos.relative(d), mechanical)) {
                only = d;
                count++;
            }
        }
        if (count == 1) {
            return only;
        }
        Direction look = ctx.getHorizontalDirection();
        if (count > 1 && eligible(level, pos.relative(look), mechanical)) {
            return look;
        }
        return fallback;
    }

    /**
     * Un voisin a change ({@code from}) : nouvelle orientation si le soufflet n'alimente rien et que ce voisin est eligible, sinon null.
     * {@code keepAxis} : seules les orientations de meme axe que l'actuelle sont permises (soufflet mecanique branche a un arbre).
     */
    @Nullable
    public static Direction relink(Level level, BlockPos pos, Direction current, BlockPos from, boolean mechanical, boolean keepAxis) {
        if (level.isClientSide || eligible(level, pos.relative(current), mechanical)) {
            return null;
        }
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (pos.relative(d).equals(from) && d != current && (!keepAxis || d.getAxis() == current.getAxis()) && eligible(level, from, mechanical)) {
                return d;
            }
        }
        return null;
    }
}
