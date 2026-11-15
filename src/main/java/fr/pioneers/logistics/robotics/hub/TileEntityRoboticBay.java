package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.core.registries.PiRobotics;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Porte de baie du hub robotique : les unites sortent et rentrent par la. Ses volets s'ouvrent a chaque passage (evenement de bloc,
 * anime cote client).
 */
public class TileEntityRoboticBay extends TileEntityRoboticCasing {

    public static final int OPEN_TICKS = 24;
    /** Client : moment de la derniere ouverture. */
    private long openedAt = Long.MIN_VALUE / 2;

    public TileEntityRoboticBay(BlockPos pos, BlockState state) {
        super(PiRobotics.BAY_TILE.get(), pos, state);
    }

    @Override
    public boolean triggerEvent(int id, int param) {
        if (id == 1) {
            if (level != null && level.isClientSide) {
                long now = level.getGameTime();
                //Deja ouverte : on prolonge l'ouverture sans rejouer l'animation
                openedAt = now - openedAt < OPEN_TICKS ? now - OPEN_TICKS / 3 : now;
            }
            return true;
        }
        return super.triggerEvent(id, param);
    }

    /** Ouverture des volets (0 ferme, 1 ouvert) : ouverture rapide, pause, fermeture. */
    public float getOpenness(float partialTick) {
        if (level == null) {
            return 0;
        }
        float t = level.getGameTime() + partialTick - openedAt;
        if (t < 0 || t > OPEN_TICKS) {
            return 0;
        }
        float open = 5, hold = OPEN_TICKS - 11;
        if (t < open) {
            return t / open;
        } else if (t < hold) {
            return 1;
        }
        return Math.max(0, 1 - (t - hold) / (OPEN_TICKS - hold));
    }

    /** Face exterieure (client et serveur), d'apres les limites du hub ; null si le hub n'est pas forme. */
    @Nullable
    public Direction getOutside() {
        BlockPos min = getMaster();
        if (min == null) {
            return null;
        }
        BlockPos max = min.offset(RoboticHubValidator.WIDTH - 1, RoboticHubValidator.HEIGHT - 1, RoboticHubValidator.WIDTH - 1), pos = worldPosition;
        if (pos.getY() == max.getY()) {
            return Direction.UP;
        } else if (pos.getY() == min.getY()) {
            return Direction.DOWN;
        } else if (pos.getX() == min.getX()) {
            return Direction.WEST;
        } else if (pos.getX() == max.getX()) {
            return Direction.EAST;
        } else if (pos.getZ() == min.getZ()) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }

    @NotNull
    @Override
    public AABB getRenderBoundingBox() {
        return new AABB(worldPosition).inflate(1);
    }
}
