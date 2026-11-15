package fr.pioneers.content.spacesim;

import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.industry.multi.Structure;
import fr.pioneers.content.StructureQueue;
import fr.pioneers.core.Directions;
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
 * Forme du Simulateur spatial ({@link SpaceSimShape}) : verification depuis le controleur (le centre du globe est 13 blocs au-dessus), mise
 * a jour des pieces, bobines allumees ou eteintes. Les changements de blocs sont regroupes en fin de tick ; le controleur reverifie toutes
 * les deux secondes. Les controleurs charges sont connus par dimension (une piece retrouve le sien sans balayer le monde).
 */
@Mod.EventBusSubscriber
public final class SpaceSimStructure {

    /** 0.20.46 (audit) : registre des controleurs et file des changements (code commun : {@link StructureQueue}). */
    private static final StructureQueue<SpaceSimControllerTile, SpaceSimPartTile> QUEUE = new StructureQueue<>(SpaceSimControllerTile.class,
          SpaceSimPartTile.class) {

        @Override
        protected BlockPos controllerOf(SpaceSimPartTile part) {
            return part.getController();
        }

        @Override
        protected void detach(ServerLevel level, BlockPos pos, SpaceSimPartTile part) {
            part.setController(null);
            light(level, pos, false);
        }

        @Override
        protected BlockPos findController(ServerLevel level, BlockPos pos) {
            return SpaceSimStructure.findController(level, pos);
        }

        @Override
        protected void validate(ServerLevel level, SpaceSimControllerTile controller) {
            SpaceSimStructure.validate(level, controller);
        }
    };

    private SpaceSimStructure() {
    }

    /**
     * Centre du globe d'un controleur : il est pose en bas au centre d'une face laterale, sa facade vers l'exterieur ; a defaut, la face
     * dont le projecteur du bas est en place.
     */
    public static BlockPos center(Level level, BlockPos controller, Direction facing) {
        BlockPos guess = controller.subtract(SpaceSimShape.controllerSpot(facing));
        if (partOf(level.getBlockState(guess.below(SpaceSimShape.RADIUS))) == SpaceSimPart.PROJECTOR) {
            return guess;
        }
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos c = controller.subtract(SpaceSimShape.controllerSpot(side));
            if (partOf(level.getBlockState(c.below(SpaceSimShape.RADIUS))) == SpaceSimPart.PROJECTOR) {
                return c;
            }
        }
        return guess;
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

    /** Controleur charge dont la structure peut contenir cette position. */
    @Nullable
    static BlockPos findController(ServerLevel level, BlockPos pos) {
        Set<BlockPos> set = QUEUE.controllers(level);
        double reach = SpaceSimShape.RING_OUTER + 1;
        for (BlockPos controller : set) {
            if (!(level.getBlockEntity(controller) instanceof SpaceSimControllerTile tile)) {
                continue;
            }
            BlockPos c = tile.getCenter();
            int dx = pos.getX() - c.getX(), dy = pos.getY() - c.getY(), dz = pos.getZ() - c.getZ();
            if (Math.abs(dy) <= SpaceSimShape.RADIUS + 1 && dx * dx + dz * dz <= reach * reach) {
                return controller;
            }
        }
        return null;
    }

    /** Resultat d'une verification : erreur (cle et position) ou pieces de la structure. */
    public record Result(@Nullable String error, @Nullable BlockPos at, List<BlockPos> members, List<BlockPos> coils) {

        public boolean valid() {
            return error == null;
        }
    }

    @Nullable
    private static SpaceSimPart partOf(BlockState state) {
        return state.getBlock() instanceof SpaceSimPartBlock block ? block.part : null;
    }

    /** Verifie le globe (monde reel). */
    @Nullable
    static Result check(Level level, BlockPos center, BlockPos controller) {
        return check(Structure.View.of(level), center, controller);
    }

    /** Verifie le globe, ses projecteurs et l'anneau ; null si une partie n'est pas chargee. */
    @Nullable
    public static Result check(Structure.View level, BlockPos center, BlockPos controller) {
        int reach = (int) Math.ceil(SpaceSimShape.RING_OUTER);
        for (int dx = -reach; dx <= reach; dx += 16) {
            for (int dz = -reach; dz <= reach; dz += 16) {
                if (!level.isLoaded(center.offset(dx, 0, dz))) {
                    return null;
                }
            }
        }
        if (!level.isLoaded(center.offset(reach, 0, reach)) || !level.isLoaded(center.offset(-reach, 0, -reach))) {
            return null;
        }
        List<BlockPos> members = new ArrayList<>(), coils = new ArrayList<>();
        for (BlockPos rel : SpaceSimShape.SHELL) {
            BlockPos pos = center.offset(rel);
            SpaceSimPart part = partOf(level.getBlockState(pos));
            if (pos.equals(controller)) {
                if (!(level.getBlockState(pos).getBlock() instanceof SpaceSimControllerBlock)) {
                    return new Result("space_simulator.pioneers.error.wall", pos, List.of(), List.of());
                }
                continue;
            }
            if (SpaceSimShape.isProjector(rel)) {
                if (part != SpaceSimPart.PROJECTOR) {
                    return new Result("space_simulator.pioneers.error.missing_projector", pos, List.of(), List.of());
                }
            } else if (part == null || !part.isShell()) {
                return new Result(part == SpaceSimPart.PROJECTOR ? "space_simulator.pioneers.error.misplaced_projector" : "space_simulator.pioneers.error.wall", pos,
                      List.of(), List.of());
            }
            if (SpaceSimShape.isRib(rel) && !SpaceSimShape.isProjector(rel) && part != SpaceSimPart.CASING) {
                return new Result("space_simulator.pioneers.error.rib", pos, List.of(), List.of());
            }
            members.add(pos);
        }
        for (BlockPos rel : SpaceSimShape.INTERIOR) {
            BlockPos pos = center.offset(rel);
            if (!level.getBlockState(pos).isAir()) {
                return new Result("space_simulator.pioneers.error.inside", pos, List.of(), List.of());
            }
        }
        for (BlockPos rel : SpaceSimShape.COILS) {
            BlockPos pos = center.offset(rel);
            if (partOf(level.getBlockState(pos)) != SpaceSimPart.COIL) {
                return new Result("space_simulator.pioneers.error.missing_coil", pos, List.of(), List.of());
            }
            members.add(pos);
            coils.add(pos);
        }
        return new Result(null, null, members, coils);
    }

    /** Reverifie la structure d'un controleur (lot 8a : par son cycle de vie commun, {@code MultiblockCore.House}). */
    static void validate(ServerLevel level, SpaceSimControllerTile controller) {
        controller.house().checkNow();
    }

    /** Lot 8a : releve du globe pour le cycle commun (validation seule, aucune ecriture). */
    @Nullable
    static MultiblockCore.House.Survey survey(ServerLevel level, SpaceSimControllerTile controller) {
        BlockPos center = controller.resolveCenter();
        Result result = check(level, center, controller.getBlockPos());
        if (result == null) {
            return null;
        }
        List<BlockPos> cells = new ArrayList<>(result.members());
        cells.addAll(result.coils());
        return result.valid() ? MultiblockCore.House.Survey.ok(cells, result) : MultiblockCore.House.Survey.fail(result.error(), result.at(), result);
    }

    /** Lot 8a : crochet de formation : pieces rattachees au controleur, projecteurs tournes vers le centre a la premiere formation. */
    static void attach(ServerLevel level, SpaceSimControllerTile controller, Result result, boolean first) {
        BlockPos center = controller.getCenter();
        for (BlockPos pos : result.members()) {
            if (level.getBlockEntity(pos) instanceof SpaceSimPartTile part) {
                part.setController(controller.getBlockPos());
            }
        }
        if (first) {
            //Les projecteurs se tournent vers le centre
            for (Direction side : Directions.ALL) {
                BlockPos projector = center.relative(side, SpaceSimShape.RADIUS);
                BlockState state = level.getBlockState(projector);
                if (state.hasProperty(SpaceSimPartBlock.FACING) && state.getValue(SpaceSimPartBlock.FACING) != side.getOpposite()) {
                    level.setBlock(projector, state.setValue(SpaceSimPartBlock.FACING, side.getOpposite()), Block.UPDATE_CLIENTS);
                }
            }
        }
        controller.setStructure(true, result.coils());
    }

    /** Les pieces d'un controleur redeviennent libres (bobines eteintes). */
    static void release(ServerLevel level, SpaceSimControllerTile controller) {
        BlockPos center = controller.getCenter();
        List<BlockPos> all = new ArrayList<>(SpaceSimShape.SHELL);
        all.addAll(SpaceSimShape.COILS);
        for (BlockPos rel : all) {
            BlockPos pos = center.offset(rel);
            if (level.isLoaded(pos) && level.getBlockEntity(pos) instanceof SpaceSimPartTile part && controller.getBlockPos().equals(part.getController())) {
                part.setController(null);
                light(level, pos, false);
            }
        }
    }

    /** Allume ou eteint une bobine (elle reste visible : le torrent d'energie l'englobe). */
    static void light(Level level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (state.getBlock() instanceof SpaceSimPartBlock block && block.part == SpaceSimPart.COIL && state.getValue(SpaceSimPartBlock.ACTIVE) != on) {
            level.setBlock(pos, state.setValue(SpaceSimPartBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    /** Clic droit sur une piece : interface du controleur, sinon la raison de l'echec. */
    static void interact(ServerLevel level, @Nullable BlockPos known, BlockPos pos, ServerPlayer player) {
        BlockPos found = known != null ? known : findController(level, pos);
        SpaceSimControllerTile controller = found != null && level.isLoaded(found) && level.getBlockEntity(found) instanceof SpaceSimControllerTile tile ? tile : null;
        if (controller == null) {
            player.displayClientMessage(Component.translatable("space_simulator.pioneers.error.no_controller").withStyle(ChatFormatting.RED), true);
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
            return Component.translatable("space_simulator.pioneers.error.wall").withStyle(ChatFormatting.RED);
        }
        String[] parts = error.split("\\|", 2);
        Component text = Component.translatable(parts[0]).withStyle(ChatFormatting.RED);
        return parts.length > 1 ? Component.empty().append(text).append(Component.literal(" (" + parts[1] + ")").withStyle(ChatFormatting.GRAY)) : text;
    }
}
