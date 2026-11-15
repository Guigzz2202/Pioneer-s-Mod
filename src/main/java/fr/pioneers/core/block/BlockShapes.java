package fr.pioneers.core.block;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.Direction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Formes de collision des blocs a modele 3D : union de boites, rotation horizontale (forme dessinee facade au nord).
 * Tableau des quatre orientations indexe par {@code facade.ordinal() - 2} (nord, sud, ouest, est), comme {@code MachineBlock.shaped}.
 */
public final class BlockShapes {

    private BlockShapes() {
    }

    /** Les quatre directions horizontales, dans l'ordre des index (nord, sud, ouest, est). */
    public static final Direction[] HORIZONTALS = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    /** Union de formes. */
    public static VoxelShape combine(VoxelShape... shapes) {
        VoxelShape result = Shapes.empty();
        for (VoxelShape shape : shapes) {
            result = Shapes.joinUnoptimized(result, shape, BooleanOp.OR);
        }
        return result.optimize();
    }

    /** Forme tournee pour une facade horizontale (la forme d'origine regarde le nord). */
    public static VoxelShape rotateHorizontal(VoxelShape shape, Direction facing) {
        if (facing == Direction.NORTH || !facing.getAxis().isHorizontal()) {
            return shape;
        }
        List<VoxelShape> boxes = new ArrayList<>();
        for (AABB box : shape.toAabbs()) {
            boxes.add(Shapes.create(rotate(box, facing)));
        }
        return combine(boxes.toArray(VoxelShape[]::new));
    }

    private static AABB rotate(AABB box, Direction facing) {
        return switch (facing) {
            //Demi-tour
            case SOUTH -> new AABB(1 - box.maxX, box.minY, 1 - box.maxZ, 1 - box.minX, box.maxY, 1 - box.minZ);
            //Quart de tour horaire (vu du dessus) : le nord passe a l'est
            case EAST -> new AABB(1 - box.maxZ, box.minY, box.minX, 1 - box.minZ, box.maxY, box.maxX);
            //Quart de tour anti-horaire : le nord passe a l'ouest
            case WEST -> new AABB(box.minZ, box.minY, 1 - box.maxX, box.maxZ, box.maxY, 1 - box.minX);
            default -> box;
        };
    }

    /** Remplit le tableau des quatre orientations a partir de la forme tournee vers le nord. */
    public static void setShape(VoxelShape shape, VoxelShape[] dest) {
        for (Direction facing : HORIZONTALS) {
            dest[facing.ordinal() - 2] = rotateHorizontal(shape, facing);
        }
    }
}
