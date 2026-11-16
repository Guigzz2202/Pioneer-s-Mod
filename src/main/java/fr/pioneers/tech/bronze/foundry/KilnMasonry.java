package fr.pioneers.tech.bronze.foundry;

import fr.pioneers.api.multiblock.HouseNbt;
import fr.pioneers.tech.bronze.foundry.KilnMasonryBlock.Form;
import fr.pioneers.tech.bronze.foundry.KilnMasonryBlock.Role;
import fr.pioneers.tech.bronze.PiBronze;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SlabBlock;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.Half;
import net.minecraft.world.level.block.state.properties.SlabType;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 23) : passage de la maconnerie vanilla du four de potier a sa maconnerie finie ({@link KilnMasonryBlock}) et retour.
 * Regles PURES (banc, GameTests) : {@link #roleOf} (role d'une case du plan), {@link #dressed} / {@link #vanilla} (memes forme, sens et
 * moitie, aller-retour exact pour tout escalier ou dalle de briques), {@link #stepOf} (palier de chauffe du four).
 */
public final class KilnMasonry {

    private KilnMasonry() {
    }

    /** Palier de chauffe du four de potier a {@code celsius} (900 au plus : 0 froid sous 300, 1 tiede, 2 chaud des 700). PUR. */
    public static int stepOf(double celsius) {
        return celsius < 300 ? 0 : celsius < 700 ? 1 : 2;
    }

    /**
     * Role de la case {@code c} du plan {@code key} (lettres r / _) : rangee du bas = socle ; colonne de la brique la plus haute = cheminee
     * (la plus haute : haut de cheminee, a conduit ouvert) ; le reste = corps. PUR ; meme regle que {@code T:core/p105_lot23.py kiln_role}.
     */
    public static Role roleOf(String key, HouseNbt.Cell c) {
        int minY = Integer.MAX_VALUE, topY = Integer.MIN_VALUE, tx = 0, tz = 0;
        for (HouseNbt.Cell o : HouseNbt.cells(key)) {
            if (!masonry(o.letter())) {
                continue;
            }
            minY = Math.min(minY, o.y());
            if (o.letter() == 'r' && o.y() > topY) {
                topY = o.y();
                tx = o.x();
                tz = o.z();
            }
        }
        if (c.y() == minY) {
            return Role.BASE;
        }
        if (c.letter() == 'r' && c.x() == tx && c.z() == tz) {
            return c.y() == topY ? Role.CHIMNEY_TOP : Role.CHIMNEY;
        }
        return Role.BODY;
    }

    public static boolean masonry(char letter) {
        return letter == 'r' || letter == '/' || letter == '_';
    }

    /** Etat fini du bloc vanilla {@code s} (meme forme), ou null si ce n'est pas de la maconnerie de briques. PUR. */
    @Nullable
    public static BlockState dressed(BlockState s, Role role, int heat) {
        BlockState base = PiBronze.KILN_MASONRY.get().defaultBlockState().setValue(KilnMasonryBlock.ROLE, role)
              .setValue(KilnMasonryBlock.HEAT, Math.max(0, Math.min(fr.pioneers.tech.bronze.HeatPalette.MAX, heat)));
        if (s.is(Blocks.BRICKS)) {
            return base.setValue(KilnMasonryBlock.FORM, Form.BRICKS);
        }
        if (s.is(Blocks.BRICK_STAIRS)) {
            return base.setValue(KilnMasonryBlock.FORM, s.getValue(StairBlock.HALF) == Half.TOP ? Form.STAIR_TOP : Form.STAIR_BOTTOM)
                  .setValue(KilnMasonryBlock.FACING, s.getValue(StairBlock.FACING));
        }
        if (s.is(Blocks.BRICK_SLAB)) {
            SlabType t = s.getValue(SlabBlock.TYPE);
            return base.setValue(KilnMasonryBlock.FORM, t == SlabType.TOP ? Form.SLAB_TOP : t == SlabType.DOUBLE ? Form.SLAB_DOUBLE : Form.SLAB_BOTTOM);
        }
        if (s.getBlock() instanceof KilnMasonryBlock) {
            return s.setValue(KilnMasonryBlock.ROLE, role).setValue(KilnMasonryBlock.HEAT, base.getValue(KilnMasonryBlock.HEAT));
        }
        return null;
    }

    /** Bloc vanilla d'origine d'un etat fini (les autres etats rendus tels quels). PUR. */
    public static BlockState vanilla(BlockState s) {
        if (!(s.getBlock() instanceof KilnMasonryBlock)) {
            return s;
        }
        Form f = s.getValue(KilnMasonryBlock.FORM);
        return switch (f) {
            case STAIR_BOTTOM, STAIR_TOP -> Blocks.BRICK_STAIRS.defaultBlockState().setValue(StairBlock.FACING, s.getValue(KilnMasonryBlock.FACING))
                  .setValue(StairBlock.HALF, f == Form.STAIR_TOP ? Half.TOP : Half.BOTTOM);
            case SLAB_BOTTOM -> Blocks.BRICK_SLAB.defaultBlockState();
            case SLAB_TOP -> Blocks.BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.TOP);
            case SLAB_DOUBLE -> Blocks.BRICK_SLAB.defaultBlockState().setValue(SlabBlock.TYPE, SlabType.DOUBLE);
            default -> Blocks.BRICKS.defaultBlockState();
        };
    }

    /** La lettre du plan accepte-t-elle ce bloc fini ? (r : briques ; / : escalier ; _ : dalle). */
    public static boolean fits(char letter, BlockState s) {
        if (!(s.getBlock() instanceof KilnMasonryBlock)) {
            return false;
        }
        Form f = s.getValue(KilnMasonryBlock.FORM);
        return switch (letter) {
            case 'r' -> f == Form.BRICKS;
            case '/' -> f.stair();
            case '_' -> f.slab();
            default -> false;
        };
    }

    /** Habille la structure formee (roles, palier {@code heat}) ; deja habillee : seul le palier change. Meme tick pour tous les blocs. */
    public static void dress(Level level, BlockPos controller, Direction front, int heat) {
        for (HouseNbt.Cell c : HouseNbt.cells(KilnTile.SHAPE)) {
            if (!masonry(c.letter())) {
                continue;
            }
            BlockPos p = HouseNbt.at(controller, front, c);
            if (!level.isLoaded(p)) {
                continue;
            }
            BlockState s = level.getBlockState(p);
            BlockState want = dressed(s, roleOf(KilnTile.SHAPE, c), heat);
            if (want != null && want != s) {
                level.setBlock(p, want, Block.UPDATE_CLIENTS);
            }
        }
    }

    /** Rend les blocs d'origine (structure defaite, bouche cassee) ; les escaliers retrouvent leur forme d'apres leurs voisins. */
    public static void undress(Level level, BlockPos controller, Direction front) {
        for (HouseNbt.Cell c : HouseNbt.cells(KilnTile.SHAPE)) {
            if (!masonry(c.letter())) {
                continue;
            }
            BlockPos p = HouseNbt.at(controller, front, c);
            if (level.isLoaded(p) && level.getBlockState(p).getBlock() instanceof KilnMasonryBlock) {
                BlockState back = vanilla(level.getBlockState(p));
                level.setBlock(p, Block.updateFromNeighbourShapes(back, level, p), Block.UPDATE_ALL);
            }
        }
    }

    /** Un four de potier forme (a trois blocs au plus) compte-t-il cette case dans sa structure ? */
    public static boolean claimed(Level level, BlockPos pos) {
        for (BlockPos p : BlockPos.betweenClosed(pos.offset(-3, -3, -3), pos.offset(3, 3, 3))) {
            if (level.isLoaded(p) && level.getBlockEntity(p) instanceof KilnTile kiln && kiln.formed()) {
                List<BlockPos> members = HouseNbt.members(kiln.getBlockPos(), kiln.facing(), KilnTile.SHAPE);
                if (members.contains(pos)) {
                    return true;
                }
            }
        }
        return false;
    }
}
