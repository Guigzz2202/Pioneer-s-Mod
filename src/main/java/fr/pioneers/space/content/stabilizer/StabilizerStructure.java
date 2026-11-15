package fr.pioneers.space.content.stabilizer;

import fr.pioneers.api.multiblock.MultiblockCore;
import fr.pioneers.tech.industry.multi.Structure;
import fr.pioneers.core.Directions;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
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
 * Forme du Stabilisateur spatial (socle) : un globe inscrit dans un cube de 11x11x11, le noyau au centre exact.
 * <ul>
 *     <li>chaque face suit le motif {@link #GRID} : 0 = libre (coins arrondis), 1 = boitier obligatoire (contour), 2 = boitier, verre, port
 *     ou port d'alimentation ;</li>
 *     <li>au centre des 6 faces : un port d'alimentation, avec un emetteur juste derriere ; d'autres ports d'alimentation peuvent
 *     prendre n'importe quelle case de verre ou de port (plus de reserve pour l'allumage) ;</li>
 *     <li>le reste de l'interieur est vide.</li>
 * </ul>
 * Les changements de blocs sont regroupes et traites en fin de tick ; le noyau reverifie aussi chaque seconde.
 */
@Mod.EventBusSubscriber
public final class StabilizerStructure {

    public static final int SIZE = 11, HALF = SIZE / 2;
    public static final byte[][] GRID = {
          {0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0},
          {0, 1, 1, 2, 2, 2, 2, 2, 1, 1, 0},
          {0, 1, 2, 2, 2, 2, 2, 2, 2, 1, 0},
          {1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1},
          {1, 2, 2, 2, 2, 2, 2, 2, 2, 2, 1},
          {0, 1, 2, 2, 2, 2, 2, 2, 2, 1, 0},
          {0, 1, 1, 2, 2, 2, 2, 2, 1, 1, 0},
          {0, 0, 0, 1, 1, 1, 1, 1, 0, 0, 0}
    };
    private static final Map<ResourceKey<Level>, Set<BlockPos>> PENDING = new HashMap<>();

    private StabilizerStructure() {
    }

    public static void queue(ServerLevel level, BlockPos pos) {
        PENDING.computeIfAbsent(level.dimension(), key -> new LinkedHashSet<>()).add(pos.immutable());
    }

    /** 0.20.46 (audit) : rien ne passe d'un monde a l'autre (solo : quitter puis ouvrir un autre monde). */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        PENDING.clear();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        Set<BlockPos> pending = PENDING.remove(level.dimension());
        if (pending == null) {
            return;
        }
        Set<BlockPos> done = new HashSet<>();
        for (BlockPos pos : pending) {
            if (!level.isLoaded(pos)) {
                continue;
            }
            if (level.getBlockEntity(pos) instanceof StabilizerCoreTile core) {
                if (done.add(pos)) {
                    validateCore(level, core);
                }
                continue;
            }
            BlockPos known = level.getBlockEntity(pos) instanceof StabilizerPartTile part ? part.getCore() : null;
            if (known != null && level.isLoaded(known) && level.getBlockEntity(known) instanceof StabilizerCoreTile core) {
                if (done.add(known)) {
                    validateCore(level, core);
                }
                continue;
            }
            if (level.getBlockEntity(pos) instanceof StabilizerPartTile part && known != null) {
                //Noyau disparu : la piece est libre
                part.setCore(null);
                paint(level, pos, StabilizerLook.NONE);
            }
            BlockPos core = findCore(level, pos);
            if (core != null && done.add(core) && level.getBlockEntity(core) instanceof StabilizerCoreTile tile) {
                validateCore(level, tile);
            } else if (core == null) {
                BlockPos controller = partOf(level.getBlockState(pos)) == StabilizerPart.CONTROLLER ? pos : findController(level, pos);
                if (controller != null && done.add(controller)) {
                    if (level.getBlockEntity(controller) instanceof StabilizerControllerTile tile) {
                        tile.requestCheck();
                    } else {
                        checkController(level, controller);
                    }
                }
            }
        }
    }

    /** Noyau le plus proche dont le globe peut contenir cette position. */
    @Nullable
    static BlockPos findCore(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int dx = -HALF; dx <= HALF; dx++) {
            for (int dy = -HALF; dy <= HALF; dy++) {
                for (int dz = -HALF; dz <= HALF; dz++) {
                    at.set(pos.getX() + dx, pos.getY() + dy, pos.getZ() + dz);
                    if (level.isLoaded(at) && level.getBlockState(at).getBlock() instanceof StabilizerCoreBlock) {
                        return at.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Resultat d'une verification : erreur (cle de langue et position) ou ports d'alimentation de la structure. */
    public record Result(@Nullable String error, @Nullable BlockPos at, List<BlockPos> powerPorts, List<BlockPos> members) {

        public boolean valid() {
            return error == null;
        }
    }

    private static Result fail(String key, @Nullable BlockPos at) {
        return new Result(key, at, List.of(), List.of());
    }

    @Nullable
    private static StabilizerPart partOf(BlockState state) {
        Block block = state.getBlock();
        if (block instanceof StabilizerPartBlock part) {
            return part.part;
        }
        return block instanceof StabilizerEmitterBlock ? StabilizerPart.EMITTER : null;
    }

    /** Verifie le globe centre sur le noyau (monde reel). */
    @Nullable
    static Result validate(Level level, BlockPos center) {
        return validate(Structure.View.of(level), center);
    }

    /** Verifie le globe centre sur le noyau ; null si une partie n'est pas chargee. */
    @Nullable
    public static Result validate(Structure.View level, BlockPos center) {
        if (!level.isLoaded(center.offset(-HALF, -HALF, -HALF)) || !level.isLoaded(center.offset(HALF, HALF, HALF))
            || !level.isLoaded(center.offset(-HALF, 0, HALF)) || !level.isLoaded(center.offset(HALF, 0, -HALF))) {
            return null;
        }
        Set<BlockPos> sideCenters = new HashSet<>();
        for (Direction side : Directions.ALL) {
            sideCenters.add(center.relative(side, HALF));
        }
        List<BlockPos> members = new ArrayList<>();
        List<BlockPos> powerPorts = new ArrayList<>();
        BlockPos controller = null;
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = 0; x < SIZE; x++) {
            for (int y = 0; y < SIZE; y++) {
                for (int z = 0; z < SIZE; z++) {
                    at.set(center.getX() - HALF + x, center.getY() - HALF + y, center.getZ() - HALF + z);
                    BlockState state = level.getBlockState(at);
                    StabilizerPart part = partOf(state);
                    BlockPos pos = at.immutable();
                    boolean wallX = x == 0 || x == SIZE - 1, wallY = y == 0 || y == SIZE - 1, wallZ = z == 0 || z == SIZE - 1;
                    if (wallX || wallY || wallZ) {
                        int grid = wallX ? GRID[y][z] : wallY ? GRID[x][z] : GRID[x][y];
                        if (grid == 0) {
                            continue;
                        }
                        if (grid == 1 ? part == null || !part.isFrame() : part == null || !part.isWall()) {
                            return fail("stabilizer.pioneers.error.wall", pos);
                        }
                        //Ports d'alimentation : obligatoires au centre des 6 faces, en plus autant qu'on veut dans la paroi
                        if (sideCenters.contains(pos) && part != StabilizerPart.POWER_PORT) {
                            return fail("stabilizer.pioneers.error.missing_power_port", pos);
                        }
                        if (part == StabilizerPart.POWER_PORT) {
                            powerPorts.add(pos);
                        }
                        //Un seul controleur, en bas au centre d'une des 4 faces laterales
                        if (part == StabilizerPart.CONTROLLER) {
                            if (controller != null || !controllerSpot(center, pos)) {
                                return fail("stabilizer.pioneers.error.misplaced_controller", pos);
                            }
                            controller = pos;
                        }
                        members.add(pos);
                        continue;
                    }
                    if (pos.equals(center)) {
                        continue;
                    }
                    int ex = Math.abs(pos.getX() - center.getX()), ey = Math.abs(pos.getY() - center.getY()), ez = Math.abs(pos.getZ() - center.getZ());
                    //Un emetteur juste derriere chacun des 6 ports d'alimentation (centres des faces)
                    boolean emitterSpot = ex + ey + ez == HALF - 1 && (ex == HALF - 1 || ey == HALF - 1 || ez == HALF - 1);
                    if (emitterSpot) {
                        if (part != StabilizerPart.EMITTER) {
                            return fail("stabilizer.pioneers.error.missing_emitter", pos);
                        }
                        members.add(pos);
                    } else if (!state.isAir()) {
                        return fail(part == StabilizerPart.EMITTER ? "stabilizer.pioneers.error.misplaced_emitter" : "stabilizer.pioneers.error.inside", pos);
                    }
                }
            }
        }
        if (controller == null) {
            return fail("stabilizer.pioneers.error.missing_controller", center.below(HALF - 1).north(HALF));
        }
        return new Result(null, null, powerPorts, members);
    }

    /** Places du controleur : en bas au centre des 4 faces laterales (la ou le motif accepte le verre). */
    static boolean controllerSpot(BlockPos center, BlockPos pos) {
        int dx = pos.getX() - center.getX(), dy = pos.getY() - center.getY(), dz = pos.getZ() - center.getZ();
        return dy == -(HALF - 1) && (Math.abs(dx) == HALF && dz == 0 || Math.abs(dz) == HALF && dx == 0);
    }

    /** Centre du globe d'un controleur : celui qui porte deja un noyau, sinon celui dont le port du bas est en place. */
    static BlockPos controllerCenter(Level level, BlockPos controller) {
        BlockPos fallback = null;
        for (Direction side : Direction.Plane.HORIZONTAL) {
            BlockPos center = controller.relative(side, HALF).above(HALF - 1);
            if (level.getBlockState(center).getBlock() instanceof StabilizerCoreBlock) {
                return center;
            }
            if (fallback == null && partOf(level.getBlockState(center.below(HALF))) == StabilizerPart.POWER_PORT) {
                fallback = center;
            }
        }
        return fallback != null ? fallback : controller.relative(Direction.NORTH, HALF).above(HALF - 1);
    }

    /** Verification depuis un controleur sans noyau : il s'allume quand la structure est prete a recevoir un noyau. */
    @Nullable
    static Result checkController(ServerLevel level, BlockPos controller) {
        BlockPos center = controllerCenter(level, controller);
        if (level.getBlockEntity(center) instanceof StabilizerCoreTile core) {
            validateCore(level, core);
            return validate(level, center);
        }
        Result result = validate(level, center);
        if (result != null) {
            lit(level, controller, result.valid());
        }
        return result;
    }

    /** Lot 8a : voyant du controleur sans noyau (crochets de son cycle commun). */
    static void lightController(ServerLevel level, BlockPos controller, boolean on) {
        lit(level, controller, on);
    }

    /** Voyant du controleur (propriete {@code enabled}). */
    private static void lit(Level level, BlockPos pos, boolean on) {
        BlockState state = level.getBlockState(pos);
        if (partOf(state) == StabilizerPart.CONTROLLER && state.getValue(StabilizerPartBlock.ACTIVE) != on) {
            level.setBlock(pos, state.setValue(StabilizerPartBlock.ACTIVE, on), Block.UPDATE_CLIENTS);
        }
    }

    /** Controleur le plus proche dont le globe peut contenir cette position (structure encore sans noyau). */
    @Nullable
    static BlockPos findController(Level level, BlockPos pos) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        int reach = SIZE - 1;
        for (int dx = -reach; dx <= reach; dx++) {
            for (int dy = -1; dy <= reach; dy++) {
                for (int dz = -reach; dz <= reach; dz++) {
                    at.set(pos.getX() + dx, pos.getY() - dy, pos.getZ() + dz);
                    if (level.isLoaded(at) && partOf(level.getBlockState(at)) == StabilizerPart.CONTROLLER) {
                        return at.immutable();
                    }
                }
            }
        }
        return null;
    }

    /** Reverifie le globe d'un noyau et met a jour ses pieces (lot 8a : par son cycle de vie commun, {@code MultiblockCore.House}). */
    static void validateCore(ServerLevel level, StabilizerCoreTile core) {
        core.house().checkNow();
    }

    /** Lot 8a : releve du globe autour de {@code center} pour le cycle commun (validation seule). */
    @Nullable
    static MultiblockCore.House.Survey survey(Level level, BlockPos center) {
        Result result = validate(level, center);
        if (result == null) {
            return null;
        }
        return result.valid() ? MultiblockCore.House.Survey.ok(result.members(), result) : MultiblockCore.House.Survey.fail(result.error(), result.at(), result);
    }

    /** Lot 8a : crochet de formation du noyau : pieces peintes du regime et rattachees, emetteurs tournes vers le centre la premiere fois. */
    static void attach(ServerLevel level, StabilizerCoreTile core, Result result, boolean first) {
        BlockPos center = core.getBlockPos();
        StabilizerLook look = StabilizerLook.of(core.getRegime());
        for (BlockPos pos : result.members()) {
            paint(level, pos, look);
            lit(level, pos, true);
            if (level.getBlockEntity(pos) instanceof StabilizerPartTile part) {
                part.setCore(center);
            }
        }
        if (first) {
            //Les emetteurs se tournent vers le centre
            for (Direction side : Directions.ALL) {
                BlockPos emitter = center.relative(side, HALF - 1);
                BlockState state = level.getBlockState(emitter);
                if (state.hasProperty(StabilizerEmitterBlock.FACING) && state.getValue(StabilizerEmitterBlock.FACING) != side.getOpposite()) {
                    level.setBlock(emitter, state.setValue(StabilizerEmitterBlock.FACING, side.getOpposite()), Block.UPDATE_CLIENTS);
                }
            }
        }
        core.setStructure(true, result.powerPorts());
    }

    /** Defait le globe d'un noyau : ses pieces redeviennent libres. */
    static void release(ServerLevel level, BlockPos center) {
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int x = -HALF; x <= HALF; x++) {
            for (int y = -HALF; y <= HALF; y++) {
                for (int z = -HALF; z <= HALF; z++) {
                    at.set(center.getX() + x, center.getY() + y, center.getZ() + z);
                    if (level.isLoaded(at) && level.getBlockEntity(at) instanceof StabilizerPartTile part && center.equals(part.getCore())) {
                        part.setCore(null);
                        paint(level, at.immutable(), StabilizerLook.NONE);
                        lit(level, at.immutable(), false);
                    }
                }
            }
        }
    }

    /** Peint une piece de la teinte du regime (neutre quand elle est libre) ; sans mise a jour des voisins, rien n'est reverifie. */
    private static void paint(Level level, BlockPos pos, StabilizerLook look) {
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(StabilizerLook.PROPERTY) && state.getValue(StabilizerLook.PROPERTY) != look) {
            level.setBlock(pos, state.setValue(StabilizerLook.PROPERTY, look), Block.UPDATE_CLIENTS);
        }
    }

    /** Clic droit sur une piece : recharge du noyau (noyau en main), interface du noyau, sinon la raison de l'echec. */
    static void interact(ServerLevel level, StabilizerPartTile part, ServerPlayer player, InteractionHand hand) {
        StabilizerCoreTile core = part.coreTile();
        ItemStack held = player.getItemInHand(hand);
        boolean controller = partOf(part.getBlockState()) == StabilizerPart.CONTROLLER;
        StabilizerCoreBlock fresh = held.getItem() instanceof BlockItem item && item.getBlock() instanceof StabilizerCoreBlock block ? block : null;
        if (fresh != null && !controller) {
            player.displayClientMessage(Component.translatable("stabilizer.pioneers.controller.use").withStyle(ChatFormatting.GOLD), true);
            return;
        }
        if (controller) {
            BlockPos center = controllerCenter(level, part.getBlockPos());
            if (core == null && level.getBlockEntity(center) instanceof StabilizerCoreTile found) {
                core = found;
            }
            if (fresh != null) {
                if (core != null) {
                    reload(level, core, fresh, held, player);
                } else {
                    insertCore(level, center, fresh, held, player);
                }
                return;
            }
            if (core == null) {
                Result result = checkController(level, part.getBlockPos());
                if (result != null) {
                    player.displayClientMessage(result.valid() ? Component.translatable("stabilizer.pioneers.controller.ready").withStyle(ChatFormatting.GREEN)
                                                               : errorText(result.error() + (result.at() == null ? "" : "|" + result.at().getX() + " "
                                                                                                                         + result.at().getY() + " " + result.at().getZ())), true);
                }
                return;
            }
        }
        if (core == null) {
            BlockPos found = part.getCore() != null ? part.getCore() : findCore(level, part.getBlockPos());
            core = found != null && level.getBlockEntity(found) instanceof StabilizerCoreTile tile ? tile : null;
            if (core != null && !core.isFormed()) {
                validateCore(level, core);
            }
        }
        if (core != null && core.isFormed()) {
            NetworkHooks.openScreen(player, core, core.getBlockPos());
        } else if (core == null) {
            player.displayClientMessage(Component.translatable("stabilizer.pioneers.error.no_core").withStyle(ChatFormatting.RED), true);
        } else {
            player.displayClientMessage(errorText(core.getError()), true);
        }
    }

    /** Depuis le controleur : le noyau en main est pose au centre de la chambre (structure valide, centre vide). */
    private static void insertCore(ServerLevel level, BlockPos center, StabilizerCoreBlock fresh, ItemStack held, ServerPlayer player) {
        Result result = validate(level, center);
        if (result == null) {
            return;
        }
        if (!result.valid()) {
            BlockPos at = result.at();
            player.displayClientMessage(errorText(result.error() + (at == null ? "" : "|" + at.getX() + " " + at.getY() + " " + at.getZ())), true);
            return;
        }
        if (!level.getBlockState(center).isAir()) {
            player.displayClientMessage(Component.translatable("stabilizer.pioneers.error.inside").withStyle(ChatFormatting.RED), true);
            return;
        }
        level.setBlock(center, fresh.defaultBlockState(), Block.UPDATE_ALL);
        level.levelEvent(2001, center, Block.getId(fresh.defaultBlockState()));
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable("stabilizer.pioneers.core.loaded", fresh.getName()).withStyle(ChatFormatting.AQUA), true);
    }

    /** Pose un nouveau noyau a la place du noyau consomme (l'astre doit etre eteint) ; un autre noyau change le regime. */
    private static void reload(ServerLevel level, StabilizerCoreTile core, StabilizerCoreBlock fresh, ItemStack held,
          ServerPlayer player) {
        if (!core.isSpent() || core.getState() != StabilizerState.OFF) {
            player.displayClientMessage(Component.translatable(core.isSpent() ? "stabilizer.pioneers.core.busy" : "stabilizer.pioneers.core.present")
                  .withStyle(ChatFormatting.GOLD), true);
            return;
        }
        BlockPos pos = core.getBlockPos();
        BlockState state = core.getBlockState();
        if (state.getBlock() == fresh) {
            level.setBlock(pos, state.setValue(StabilizerCoreBlock.SPENT, false), Block.UPDATE_ALL);
        } else {
            //Autre regime : les reservoirs et reglages du noyau suivent
            CompoundTag saved = core.saveWithoutMetadata();
            level.setBlock(pos, fresh.defaultBlockState().setValue(StabilizerCoreBlock.FACING, state.getValue(StabilizerCoreBlock.FACING)), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof StabilizerCoreTile replaced) {
                replaced.load(saved);
                replaced.setChanged();
                validateCore(level, replaced);
            }
        }
        if (!player.getAbilities().instabuild) {
            held.shrink(1);
        }
        player.displayClientMessage(Component.translatable("stabilizer.pioneers.core.loaded", fresh.getName()).withStyle(ChatFormatting.AQUA), true);
    }

    /** Le controleur pose lui-meme un noyau au centre vide d'une chambre valide. */
    static void placeCore(ServerLevel level, BlockPos center, StabilizerCoreBlock fresh) {
        level.setBlock(center, fresh.defaultBlockState(), Block.UPDATE_ALL);
        level.levelEvent(2001, center, Block.getId(fresh.defaultBlockState()));
    }

    /** Le controleur remplace un noyau consomme (astre eteint) ; false si le noyau en place est encore actif. */
    static boolean swapCore(ServerLevel level, StabilizerCoreTile core, StabilizerCoreBlock fresh) {
        if (!core.isSpent() || core.getState() != StabilizerState.OFF) {
            return false;
        }
        BlockPos pos = core.getBlockPos();
        BlockState state = core.getBlockState();
        if (state.getBlock() == fresh) {
            level.setBlock(pos, state.setValue(StabilizerCoreBlock.SPENT, false), Block.UPDATE_ALL);
        } else {
            CompoundTag saved = core.saveWithoutMetadata();
            level.setBlock(pos, fresh.defaultBlockState().setValue(StabilizerCoreBlock.FACING, state.getValue(StabilizerCoreBlock.FACING)), Block.UPDATE_ALL);
            if (level.getBlockEntity(pos) instanceof StabilizerCoreTile replaced) {
                replaced.load(saved);
                replaced.setChanged();
                validateCore(level, replaced);
            }
        }
        level.levelEvent(2001, pos, Block.getId(fresh.defaultBlockState()));
        return true;
    }

    /** Texte d'une erreur de forme ({@code cle|x y z}). */
    public static Component errorText(String error) {
        if (error.isEmpty()) {
            return Component.translatable("stabilizer.pioneers.error.wall").withStyle(ChatFormatting.RED);
        }
        String[] parts = error.split("\\|", 2);
        Component text = Component.translatable(parts[0]).withStyle(ChatFormatting.RED);
        return parts.length > 1 ? Component.empty().append(text).append(Component.literal(" (" + parts[1] + ")").withStyle(ChatFormatting.GRAY)) : text;
    }
}
