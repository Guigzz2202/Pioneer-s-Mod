package fr.pioneers.rail.block;

import fr.pioneers.rail.track.TrackBlock;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.8 : formes (selection et collision) des objets de gare, calculees d'apres leur vrai maillage ({@link RailShapeData}, genere par
 * {@code tools/railtest/ShapeFit.java}) et tournees vers la voie ou la facade.
 */
public final class RailShapes {

    private static final Map<String, VoxelShape> CACHE = new HashMap<>();

    private RailShapes() {
    }

    /** Forme du maillage {@code key} tourne vers {@code facing} (null : nord). */
    public static VoxelShape get(String key, @Nullable Direction facing) {
        int q = facing == null || facing.getAxis().isVertical() ? 0 : Math.floorMod(facing.get2DDataValue() - Direction.NORTH.get2DDataValue(), 4);
        String k = key + "|" + q;
        synchronized (CACHE) {
            VoxelShape s = CACHE.get(k);
            if (s == null) {
                s = build(key, q);
                CACHE.put(k, s);
            }
            return s;
        }
    }

    private static VoxelShape build(String key, int q) {
        double[][] boxes = RailShapeData.BOXES.get(key);
        if (boxes == null || boxes.length == 0) {
            return Block.box(2, 0, 2, 14, 16, 14);
        }
        VoxelShape out = Shapes.empty();
        for (double[] b : boxes) {
            double x0 = b[0], z0 = b[2], x1 = b[3], z1 = b[5];
            for (int i = 0; i < q; i++) {
                double nx0 = 16 - z1, nx1 = 16 - z0;
                z0 = x0;
                z1 = x1;
                x0 = nx0;
                x1 = nx1;
            }
            out = Shapes.or(out, Block.box(x0, b[1], z0, x1, Math.min(24, b[4]), z1));
        }
        return out.optimize();
    }

    /** Face ou se trouve la voie voisine (meme regle que le bras dessine du poste), ou null. */
    @Nullable
    public static Direction track(BlockGetter level, BlockPos pos) {
        for (Direction d : Direction.Plane.HORIZONTAL) {
            if (level.getBlockState(pos.relative(d)).getBlock() instanceof TrackBlock || level.getBlockState(pos.relative(d).below()).getBlock() instanceof TrackBlock) {
                return d;
            }
        }
        return null;
    }

    /** Contour (selection) : une seule boite, l'objet entier dans le bloc ; la collision reste detaillee ({@link #get}). */
    public static VoxelShape outline(String key, @Nullable Direction facing) {
        return get(key + "#b", facing);
    }

    @Nullable
    public static Direction postFacing(BlockGetter level, BlockPos pos, Direction facing) {
        Direction arm = track(level, pos);
        return arm != null ? arm : facing;
    }

    /** Forme d'un poste ou de la grue a eau, d'apres la voie voisine. */
    public static VoxelShape post(String key, BlockGetter level, BlockPos pos, Direction facing) {
        Direction arm = track(level, pos);
        //Sans voie, le poste est dessine vers sa facade (comme RailBakedModel)
        return get(key, arm != null ? arm : facing);
    }
}
