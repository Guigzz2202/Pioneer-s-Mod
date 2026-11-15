package fr.pioneers.core;

import net.minecraft.core.Direction;

/**
 * Face d'une machine vue de sa facade (avant, arriere, gauche, droite, dessus, dessous), independante de son orientation dans le monde.
 */
public enum RelativeSide {
    FRONT, LEFT, RIGHT, BACK, TOP, BOTTOM;

    /** Face du monde correspondante pour une machine tournee vers {@code facing}. */
    public Direction toAbsolute(Direction facing) {
        return switch (this) {
            case FRONT -> facing;
            case BACK -> facing.getOpposite();
            //Memes conventions que Mekanism (les reglages de faces deja sauvegardes restent justes)
            case LEFT -> facing.getAxis() == Direction.Axis.Y ? Direction.EAST : facing.getClockWise();
            case RIGHT -> facing.getAxis() == Direction.Axis.Y ? Direction.WEST : facing.getCounterClockWise();
            case TOP -> facing == Direction.DOWN ? Direction.NORTH : facing == Direction.UP ? Direction.SOUTH : Direction.UP;
            case BOTTOM -> facing == Direction.DOWN ? Direction.SOUTH : facing == Direction.UP ? Direction.NORTH : Direction.DOWN;
        };
    }

    /** Face relative d'une face du monde pour une machine tournee vers {@code facing}. */
    public static RelativeSide fromDirections(Direction facing, Direction side) {
        for (RelativeSide relative : values()) {
            if (relative.toAbsolute(facing) == side) {
                return relative;
            }
        }
        return FRONT;
    }

    private static final RelativeSide[] VALUES = values();

    public static RelativeSide byIndex(int index) {
        return VALUES[Math.floorMod(index, VALUES.length)];
    }
}
