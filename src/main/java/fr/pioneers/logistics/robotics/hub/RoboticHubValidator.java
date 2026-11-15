package fr.pioneers.logistics.robotics.hub;

import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.Pioneers;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/**
 * Validation du hub robotique (socle, sans Mekanism) : un cube de 3 blocs. Les huit coins en boitiers ; ailleurs, boitiers, ports, portes
 * de baie ou verre (tag {@code pioneers:robotic_hub_glass}) ; le centre vide (le berceau des unites). Au moins une porte de baie,
 * sur un cote (ou sur le toit, pour les drones).
 */
public final class RoboticHubValidator {

    public static final int WIDTH = 3, HEIGHT = 3;
    /** Verre accepte dans la paroi (verre de Forge, verre structurel de Mekanism...). */
    public static final TagKey<Block> GLASS = TagKey.create(Registries.BLOCK, Pioneers.rl("robotic_hub_glass"));

    private RoboticHubValidator() {
    }

    /** Resultat : portes de baie si la structure est bonne, sinon le probleme (et ou). */
    public record Result(boolean valid, List<RoboticHubData.Bay> bays, @Nullable Component error) {

        static Result fail(Component error) {
            return new Result(false, List.of(), error);
        }
    }

    /** Les huit coins sont-ils des boitiers ? (pre-test rapide d'un cube candidat) */
    public static boolean corners(Level level, BlockPos min) {
        for (int i = 0; i < 8; i++) {
            BlockPos pos = min.offset((i & 1) * (WIDTH - 1), (i >> 1 & 1) * (HEIGHT - 1), (i >> 2 & 1) * (WIDTH - 1));
            if (!level.isLoaded(pos) || part(level.getBlockState(pos)) != HubPart.CASING) {
                return false;
            }
        }
        return true;
    }

    /** Verifie le cube de coin bas {@code min} (monde reel). */
    @Nullable
    public static Result validate(Level level, BlockPos min) {
        return validate(Structure.View.of(level), min);
    }

    /** Verifie le cube de coin bas {@code min} ; null si une partie n'est pas chargee (on ne conclut rien). */
    @Nullable
    public static Result validate(Structure.View level, BlockPos min) {
        List<RoboticHubData.Bay> bays = new ArrayList<>();
        BlockPos max = min.offset(WIDTH - 1, HEIGHT - 1, WIDTH - 1);
        for (int x = 0; x < WIDTH; x++) {
            for (int y = 0; y < HEIGHT; y++) {
                for (int z = 0; z < WIDTH; z++) {
                    BlockPos pos = min.offset(x, y, z);
                    if (!level.isLoaded(pos)) {
                        return null;
                    }
                    BlockState state = level.getBlockState(pos);
                    int extremes = (x == 0 || x == WIDTH - 1 ? 1 : 0) + (y == 0 || y == HEIGHT - 1 ? 1 : 0) + (z == 0 || z == WIDTH - 1 ? 1 : 0);
                    HubPart part = part(state);
                    if (extremes == 0) {
                        if (!state.isAir()) {
                            return Result.fail(Component.translatable("robotic_hub.pioneers.formation.center", text(pos)));
                        }
                    } else if (extremes == 3) {
                        if (part != HubPart.CASING) {
                            return Result.fail(Component.translatable("robotic_hub.pioneers.formation.corner", text(pos)));
                        }
                    } else if (part == null && !state.is(GLASS)) {
                        return Result.fail(Component.translatable("robotic_hub.pioneers.formation.invalid", text(pos)));
                    }
                    if (part != null && level.getBlockEntity(pos) instanceof TileEntityRoboticCasing tile && tile.getMaster() != null
                        && !tile.getMaster().equals(min) && tile.getHub() != null) {
                        //Piece deja prise par un autre hub
                        return Result.fail(Component.translatable("robotic_hub.pioneers.formation.taken", text(pos)));
                    }
                    if (part == HubPart.BAY) {
                        Direction side = outside(pos, min, max);
                        if (side == Direction.DOWN) {
                            return Result.fail(Component.translatable("robotic_hub.pioneers.formation.bay_side", text(pos)));
                        }
                        bays.add(new RoboticHubData.Bay(pos.immutable(), side));
                    }
                }
            }
        }
        if (bays.isEmpty()) {
            return Result.fail(Component.translatable("robotic_hub.pioneers.formation.no_bay"));
        }
        return new Result(true, bays, null);
    }

    @Nullable
    public static HubPart part(BlockState state) {
        return state.getBlock() instanceof BlockRoboticHubPart block ? block.part : null;
    }

    private static Component text(BlockPos pos) {
        return Component.literal(pos.getX() + ", " + pos.getY() + ", " + pos.getZ());
    }

    /** Face exterieure d'un bloc de la coque (les cotes d'abord : le toit et le sol ne comptent qu'au centre). */
    static Direction outside(BlockPos pos, BlockPos min, BlockPos max) {
        boolean side = pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
        if (!side) {
            return pos.getY() == max.getY() ? Direction.UP : Direction.DOWN;
        } else if (pos.getX() == min.getX()) {
            return Direction.WEST;
        } else if (pos.getX() == max.getX()) {
            return Direction.EAST;
        } else if (pos.getZ() == min.getZ()) {
            return Direction.NORTH;
        }
        return Direction.SOUTH;
    }
}
