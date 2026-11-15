package fr.pioneers.content.absorber;

import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.content.StructureQueue;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Forme de l'absorbeur d'energie solaire (socle) : un caisson creux de 5x3x5 a 13x5x13 (largeur, hauteur, profondeur). Aretes en boitier ;
 * toute la face superieure (hors aretes) en capteurs, et des capteurs nulle part ailleurs ; parois laterales et fond en boitier ou ports ;
 * un seul controleur, dans une paroi laterale ; interieur vide.
 * <p>
 * La verification part du controleur : on longe sa paroi a droite, a gauche, en haut et en bas, puis on mesure la profondeur vers
 * l'interieur. Les changements de blocs sont regroupes en fin de tick ; le controleur reverifie toutes les deux secondes. Les controleurs
 * charges sont connus par dimension (une piece retrouve le sien sans balayer le monde).
 */
@Mod.EventBusSubscriber
public final class AbsorberStructure {

    public static final int MIN_SIDE = 5, MAX_SIDE = 13, MIN_HEIGHT = 3, MAX_HEIGHT = 5;

    /** 0.20.46 (audit) : registre des controleurs et file des changements (code commun : {@link StructureQueue}). */
    private static final StructureQueue<AbsorberControllerTile, AbsorberPartTile> QUEUE = new StructureQueue<>(AbsorberControllerTile.class,
          AbsorberPartTile.class) {

        @Override
        protected BlockPos controllerOf(AbsorberPartTile part) {
            return part.getController();
        }

        @Override
        protected void detach(ServerLevel level, BlockPos pos, AbsorberPartTile part) {
            part.setController(null);
            light(level, pos, false);
        }

        @Override
        protected BlockPos findController(ServerLevel level, BlockPos pos) {
            return AbsorberStructure.findController(level, pos);
        }

        @Override
        protected void validate(ServerLevel level, AbsorberControllerTile controller) {
            AbsorberStructure.validate(level, controller);
        }
    };

    private AbsorberStructure() {
    }

    static void register(ServerLevel level, BlockPos controller) {
        QUEUE.register(level, controller);
    }

    static void unregister(ServerLevel level, BlockPos controller) {
        QUEUE.unregister(level, controller);
    }

    public static void queue(ServerLevel level, BlockPos pos) {
        QUEUE.queue(level, pos);
    }

    /** 0.20.46 (audit) : rien ne passe d'un monde a l'autre (solo : quitter puis ouvrir un autre monde). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        QUEUE.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        QUEUE.tick(level);
    }

    /** Controleur charge dont le caisson (connu ou possible) contient cette position. */
    @Nullable
    static BlockPos findController(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = QUEUE.controllers(level);
        for (BlockPos controller : set) {
            if (!(level.getBlockEntity(controller) instanceof AbsorberControllerTile tile)) {
                continue;
            }
            BlockPos min = tile.getMin(), max = tile.getMax();
            if (min != null && max != null) {
                if (pos.getX() >= min.getX() - 1 && pos.getX() <= max.getX() + 1 && pos.getY() >= min.getY() - 1 && pos.getY() <= max.getY() + 1
                    && pos.getZ() >= min.getZ() - 1 && pos.getZ() <= max.getZ() + 1) {
                    return controller;
                }
            } else if (Math.abs(pos.getX() - controller.getX()) <= MAX_SIDE && Math.abs(pos.getY() - controller.getY()) <= MAX_HEIGHT
                       && Math.abs(pos.getZ() - controller.getZ()) <= MAX_SIDE) {
                return controller;
            }
        }
        return null;
    }

    /** Resultat d'une verification : erreur (cle et position) ou caisson et pieces. */
    public record Result(@Nullable String error, @Nullable BlockPos at, @Nullable BlockPos min, @Nullable BlockPos max, List<BlockPos> members,
                  List<BlockPos> collectors) {

        static Result fail(String key, @Nullable BlockPos at) {
            return new Result(key, at, null, null, List.of(), List.of());
        }

        public boolean valid() {
            return error == null;
        }
    }

    @Nullable
    static AbsorberPart partOf(BlockState state) {
        return state.getBlock() instanceof AbsorberPartBlock block ? block.part : null;
    }

    /** Bloc de paroi laterale ou de fond : boitier, port, ou un controleur. */
    private static boolean isWall(Structure.View level, BlockPos pos) {
        BlockState state = level.getBlockState(pos);
        AbsorberPart part = partOf(state);
        return part != null && part.isWall() || state.getBlock() instanceof AbsorberControllerBlock;
    }

    /** Nombre de blocs de paroi a la suite dans une direction (au plus {@code max}). */
    private static int run(Structure.View level, BlockPos from, Direction direction, int max) {
        int count = 0;
        BlockPos pos = from;
        while (count < max) {
            pos = pos.relative(direction);
            if (!isWall(level, pos)) {
                break;
            }
            count++;
        }
        return count;
    }

    /** Verifie le caisson (monde reel). */
    @Nullable
    static Result check(Level level, BlockPos controller, Direction facing) {
        return check(Structure.View.of(level), controller, facing);
    }

    /** Verifie le caisson autour du controleur ; null si une partie n'est pas chargee. */
    @Nullable
    public static Result check(Structure.View level, BlockPos controller, Direction facing) {
        int r = MAX_SIDE + 1;
        if (!level.isLoaded(controller.offset(r, 0, r)) || !level.isLoaded(controller.offset(-r, 0, -r)) || !level.isLoaded(controller.offset(r, 0, -r))
            || !level.isLoaded(controller.offset(-r, 0, r))) {
            return null;
        }
        //La facade du controleur regarde l'exterieur : l'interieur est derriere. A defaut, le premier cote qui donne sur un vide.
        List<Direction> inwards = new ArrayList<>();
        inwards.add(facing.getOpposite());
        for (Direction side : Direction.Plane.HORIZONTAL) {
            if (!inwards.contains(side)) {
                inwards.add(side);
            }
        }
        Result first = null;
        for (Direction inward : inwards) {
            if (!level.getBlockState(controller.relative(inward)).isAir()) {
                continue;
            }
            Result result = checkInward(level, controller, inward);
            if (result.valid()) {
                return result;
            }
            if (first == null) {
                first = result;
            }
        }
        return first != null ? first : Result.fail("solar_absorber.pioneers.error.controller", controller);
    }

    private static Result checkInward(Structure.View level, BlockPos controller, Direction inward) {
        Direction right = inward.getClockWise();
        int toRight = run(level, controller, right, MAX_SIDE), toLeft = run(level, controller, right.getOpposite(), MAX_SIDE);
        int up = run(level, controller, Direction.UP, MAX_HEIGHT), down = run(level, controller, Direction.DOWN, MAX_HEIGHT);
        BlockPos corner = controller.relative(right.getOpposite(), toLeft).below(down);
        int width = toLeft + toRight + 1, height = up + down + 1;
        int depth = run(level, corner, inward, MAX_SIDE) + 1;
        if (width < MIN_SIDE || depth < MIN_SIDE || height < MIN_HEIGHT || width > MAX_SIDE || depth > MAX_SIDE || height > MAX_HEIGHT) {
            return Result.fail("solar_absorber.pioneers.error.size", controller);
        }
        BlockPos other = corner.relative(right, width - 1).relative(inward, depth - 1).above(height - 1);
        BlockPos min = new BlockPos(Math.min(corner.getX(), other.getX()), corner.getY(), Math.min(corner.getZ(), other.getZ()));
        BlockPos max = new BlockPos(Math.max(corner.getX(), other.getX()), other.getY(), Math.max(corner.getZ(), other.getZ()));
        List<BlockPos> members = new ArrayList<>(), collectors = new ArrayList<>();
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            boolean bx = pos.getX() == min.getX() || pos.getX() == max.getX(), bz = pos.getZ() == min.getZ() || pos.getZ() == max.getZ();
            boolean top = pos.getY() == max.getY(), bottom = pos.getY() == min.getY();
            int faces = (bx ? 1 : 0) + (bz ? 1 : 0) + (top || bottom ? 1 : 0);
            BlockState state = level.getBlockState(pos);
            AbsorberPart part = partOf(state);
            boolean isController = state.getBlock() instanceof AbsorberControllerBlock;
            BlockPos at = pos.immutable();
            if (faces == 0) {
                if (!state.isAir()) {
                    return Result.fail("solar_absorber.pioneers.error.inside", at);
                }
                continue;
            }
            if (isController) {
                if (!at.equals(controller)) {
                    return Result.fail("solar_absorber.pioneers.error.two_controllers", at);
                }
                if (faces > 1 || top || bottom) {
                    return Result.fail("solar_absorber.pioneers.error.controller", at);
                }
                continue;
            }
            if (faces >= 2) {
                if (part != AbsorberPart.CASING) {
                    return Result.fail("solar_absorber.pioneers.error.edge", at);
                }
            } else if (top) {
                if (part != AbsorberPart.COLLECTOR) {
                    return Result.fail("solar_absorber.pioneers.formation.top", at);
                }
                collectors.add(at);
            } else if (part == AbsorberPart.COLLECTOR) {
                return Result.fail("solar_absorber.pioneers.formation.misplaced_collector", at);
            } else if (part == null) {
                return Result.fail("solar_absorber.pioneers.error.wall", at);
            }
            members.add(at);
        }
        return new Result(null, null, min, max, members, collectors);
    }

    /** Reverifie le caisson d'un controleur et met a jour ses pieces. */
    static void validate(ServerLevel level, AbsorberControllerTile controller) {
        controller.house().checkNow();
    }

    /** Lot 8a : releve du caisson pour le cycle commun (validation seule). */
    @Nullable
    static MultiblockCore.House.Survey survey(ServerLevel level, AbsorberControllerTile controller) {
        Result result = check(level, controller.getBlockPos(), controller.facing());
        if (result == null) {
            return null;
        }
        return result.valid() ? MultiblockCore.House.Survey.ok(result.members(), result) : MultiblockCore.House.Survey.fail(result.error(), result.at(), result);
    }

    /** Lot 8a : crochet de formation : caisson deplace = anciennes pieces liberees, pieces rattachees, bornes et capteurs. */
    static void attach(ServerLevel level, AbsorberControllerTile controller, Result result) {
        if (controller.getMin() != null && (!controller.getMin().equals(result.min()) || !controller.getMax().equals(result.max()))) {
            release(level, controller);
        }
        for (BlockPos pos : result.members()) {
            if (level.getBlockEntity(pos) instanceof AbsorberPartTile part) {
                part.setController(controller.getBlockPos());
            }
        }
        controller.setStructure(true, result.min(), result.max(), result.collectors().size());
    }

    /** Les pieces d'un controleur redeviennent libres (capteurs eteints). */
    static void release(ServerLevel level, AbsorberControllerTile controller) {
        BlockPos min = controller.getMin(), max = controller.getMax();
        if (min == null || max == null) {
            return;
        }
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof AbsorberPartTile part && controller.getBlockPos().equals(part.getController())) {
                part.setController(null);
                light(level, pos, false);
            }
        }
    }

    /** Capteurs illumines (absorbeur en marche) ou eteints. */
    static void lightCollectors(Level level, BlockPos min, BlockPos max, boolean on) {
        for (BlockPos pos : BlockPos.betweenClosed(min.offset(1, max.getY() - min.getY(), 1), max.offset(-1, 0, -1))) {
            if (level.isLoaded(pos)) {
                light(level, pos, on);
            }
        }
    }

    static void light(Level level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof AbsorberPartBlock block && block.part == AbsorberPart.COLLECTOR && state.getValue(AbsorberPartBlock.ACTIVE) != on) {
            level.setBlock(pos, state.setValue(AbsorberPartBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    /** Clic droit sur une piece : interface du controleur, sinon la raison de l'echec. */
    static void interact(ServerLevel level, @Nullable BlockPos known, BlockPos pos, ServerPlayer player) {
        BlockPos found = known != null ? known : findController(level, pos);
        AbsorberControllerTile controller = found != null && level.isLoaded(found) && level.getBlockEntity(found) instanceof AbsorberControllerTile tile ? tile : null;
        if (controller == null) {
            player.displayClientMessage(Component.translatable("solar_absorber.pioneers.error.no_controller").withStyle(ChatFormatting.RED), true);
            return;
        }
        if (!controller.isFormed()) {
            validate(level, controller);
        }
        if (controller.isFormed()) {
            NetworkHooks.openScreen(player, controller, controller.getBlockPos());
        } else {
            player.displayClientMessage(errorText(controller.getError()), true);
        }
    }

    /** Texte d'une erreur de forme ({@code cle|x y z}). */
    public static Component errorText(String error) {
        if (error.isEmpty()) {
            return Component.translatable("solar_absorber.pioneers.error.wall").withStyle(ChatFormatting.RED);
        }
        String[] parts = error.split("\\|", 2);
        Component text = Component.translatable(parts[0]).withStyle(ChatFormatting.RED);
        return parts.length > 1 ? Component.empty().append(text).append(Component.literal(" (" + parts[1] + ")").withStyle(ChatFormatting.GRAY)) : text;
    }
}
