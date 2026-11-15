package fr.pioneers.materials.area;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.farm.crop.CropTopBlock;
import fr.pioneers.farm.crop.FarmCropBlock;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CropBlock;
import net.minecraft.world.level.block.NetherWartBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.gameevent.GameEvent;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.4.1 : zones des outils de zone ({@link AreaToolItem}), communes au serveur (casse) et au client (apercu, fissures).
 * <ul>
 *     <li>Marteau de minage, excavateur : les 8 voisins du bloc casse dans le plan de la face visee, s'ils se minent avec l'outil, ne sont pas
 *     beaucoup plus durs que lui (dur. &le; 2 x + 1) et n'ont pas de contenu (coffres, machines) ;</li>
 *     <li>faux : casser ou cliquer une culture mure recolte et replante toutes les cultures mures du carre (cultures genetiques comprises) ;
 *     casser de l'herbe ou des fleurs fauche le carre ; casser du feuillage taille le cube ; clic droit sur la terre : labour du carre.</li>
 * </ul>
 * Accroupi : un seul bloc. Les blocs supplementaires passent par {@code destroyBlock} du joueur (protections, enchantements, usure) ; un
 * drapeau evite la recursion. Config {@code tools.areaTools}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class AreaTools {

    private static boolean working;

    private AreaTools() {
    }

    public static boolean enabled() {
        return PiCommonConfig.get(PiCommonConfig.AREA_TOOLS, true);
    }

    // ------------------------------------------------------------------ zones

    /** Face visee par le joueur sur le bloc {@code center} (sinon le dessus). */
    public static Direction face(Player player, BlockPos center) {
        Vec3 eye = player.getEyePosition();
        Vec3 end = eye.add(player.getViewVector(1F).scale(player.getBlockReach() + 1));
        BlockHitResult hit = player.level().clip(new ClipContext(eye, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        return hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(center) ? hit.getDirection() : Direction.UP;
    }

    /** Blocs casses EN PLUS du bloc vise par un marteau de minage ou un excavateur (vide : un seul bloc). */
    public static List<BlockPos> digTargets(Level level, Player player, ItemStack stack, BlockPos center, BlockState state, Direction face) {
        List<BlockPos> out = new ArrayList<>();
        if (!(stack.getItem() instanceof AreaToolItem tool) || tool.kind() == AreaToolItem.Kind.SCYTHE || player.isShiftKeyDown() || !enabled()
            || !tool.correct(state)) {
            return out;
        }
        float hardness = state.getDestroySpeed(level, center);
        if (hardness < 0) {
            return out;
        }
        int r = tool.radius();
        for (int a = -r; a <= r; a++) {
            for (int b = -r; b <= r; b++) {
                if (a == 0 && b == 0) {
                    continue;
                }
                BlockPos pos = switch (face.getAxis()) {
                    case X -> center.offset(0, a, b);
                    case Y -> center.offset(a, 0, b);
                    default -> center.offset(a, b, 0);
                };
                BlockState other = level.getBlockState(pos);
                float h = other.getDestroySpeed(level, pos);
                if (!other.isAir() && h >= 0 && h <= hardness * 2 + 1 && tool.correct(other) && level.getBlockEntity(pos) == null) {
                    out.add(pos);
                }
            }
        }
        return out;
    }

    /** Ce que fait la faux sur ce bloc. */
    public enum Scythe {
        NONE, HARVEST, MOW, PRUNE
    }

    public static Scythe scytheMode(BlockState state) {
        if (isRipe(state)) {
            return Scythe.HARVEST;
        }
        if (state.is(BlockTags.LEAVES)) {
            return Scythe.PRUNE;
        }
        return isGrass(state) ? Scythe.MOW : Scythe.NONE;
    }

    /** Culture mure recoltable par la faux (cultures vanilla et genetiques, verrues). */
    public static boolean isRipe(BlockState state) {
        if (state.getBlock() instanceof CropBlock crop) {
            return crop.isMaxAge(state);
        }
        return state.getBlock() instanceof NetherWartBlock && state.getValue(NetherWartBlock.AGE) >= 3;
    }

    /** Herbe, fougeres, fleurs : vegetation basse qui se casse d'un coup et se remplace. */
    public static boolean isGrass(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty() || state.getBlock() instanceof CropBlock) {
            return false;
        }
        return state.canBeReplaced() && state.is(BlockTags.REPLACEABLE_BY_TREES) || state.is(BlockTags.SMALL_FLOWERS)
               || state.is(BlockTags.TALL_FLOWERS);
    }

    /** Blocs touches par la faux autour de {@code center} (centre compris) ; tranche y-1..y+1 (cube pour le feuillage). */
    public static List<BlockPos> scytheTargets(Level level, Player player, ItemStack stack, BlockPos center, Scythe mode) {
        List<BlockPos> out = new ArrayList<>();
        if (!(stack.getItem() instanceof AreaToolItem tool) || tool.kind() != AreaToolItem.Kind.SCYTHE || mode == Scythe.NONE || !enabled()) {
            return out;
        }
        if (player.isShiftKeyDown()) {
            if (mode == Scythe.HARVEST) {
                out.add(center);
            }
            return out;
        }
        int r = tool.radius(), ry = mode == Scythe.PRUNE ? r : 1;
        for (int dy = -ry; dy <= ry; dy++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    BlockPos pos = center.offset(dx, dy, dz);
                    BlockState state = level.getBlockState(pos);
                    boolean hit = switch (mode) {
                        case HARVEST -> isRipe(state);
                        case PRUNE -> state.is(BlockTags.LEAVES) && level.getBlockEntity(pos) == null;
                        case MOW -> isGrass(state);
                        default -> false;
                    };
                    if (hit) {
                        out.add(pos);
                    }
                }
            }
        }
        out.sort((p, q) -> Integer.compare(p.distManhattan(center), q.distManhattan(center)));
        return out;
    }

    /** Clic droit d'excavateur (chemins) ou de faux (labour) : le carre de cote 2r+1 autour du bloc vise, au meme niveau. */
    public static InteractionResult reshape(UseOnContext context, ToolAction action, int r) {
        Level level = context.getLevel();
        Player player = context.getPlayer();
        BlockPos center = context.getClickedPos();
        if (context.getClickedFace() == Direction.DOWN || player == null) {
            return InteractionResult.PASS;
        }
        List<BlockPos> targets = reshapeTargets(context, action, player.isShiftKeyDown() || !enabled() ? 0 : r);
        if (targets.isEmpty()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            ItemStack stack = context.getItemInHand();
            InteractionHand hand = context.getHand();
            for (BlockPos pos : targets) {
                if (stack.isEmpty() || !level.mayInteract(player, pos)) {
                    break;
                }
                BlockState state = level.getBlockState(pos);
                BlockState next = state.getToolModifiedState(at(context, pos), action, false);
                if (next == null) {
                    continue;
                }
                level.setBlock(pos, next, 11);
                level.gameEvent(GameEvent.BLOCK_CHANGE, pos, GameEvent.Context.of(player, next));
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            }
            level.playSound(null, center, action == ToolActions.HOE_TILL ? SoundEvents.HOE_TILL : SoundEvents.SHOVEL_FLATTEN, SoundSource.BLOCKS,
                  1F, 1F);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    /** Blocs transformables du carre (le bloc vise d'abord) : air au-dessus et transformation connue. */
    public static List<BlockPos> reshapeTargets(UseOnContext context, ToolAction action, int r) {
        Level level = context.getLevel();
        BlockPos center = context.getClickedPos();
        List<BlockPos> out = new ArrayList<>();
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                BlockPos pos = center.offset(dx, 0, dz);
                if (!level.getBlockState(pos.above()).isAir()) {
                    continue;
                }
                if (level.getBlockState(pos).getToolModifiedState(at(context, pos), action, true) != null) {
                    out.add(pos);
                }
            }
        }
        out.sort((p, q) -> Integer.compare(p.distManhattan(center), q.distManhattan(center)));
        return out;
    }

    private static UseOnContext at(UseOnContext context, BlockPos pos) {
        return new UseOnContext(context.getLevel(), context.getPlayer(), context.getHand(), context.getItemInHand(),
              new BlockHitResult(Vec3.atCenterOf(pos).add(0, 0.5, 0), Direction.UP, pos, false));
    }

    // ------------------------------------------------------------------ evenements (serveur)

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (working || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player)) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof AreaToolItem tool)) {
            return;
        }
        ServerLevel level = player.serverLevel();
        BlockPos center = event.getPos();
        BlockState state = event.getState();
        if (tool.kind() == AreaToolItem.Kind.SCYTHE) {
            if (state.getBlock() instanceof CropTopBlock) {
                center = center.below();
                state = level.getBlockState(center);
            }
            Scythe mode = scytheMode(state);
            if (mode == Scythe.HARVEST) {
                //La culture visee repart aussi : pas de casse, une recolte
                event.setCanceled(true);
                harvest(level, player, InteractionHand.MAIN_HAND, stack, scytheTargets(level, player, stack, center, mode));
                return;
            }
            List<BlockPos> extra = scytheTargets(level, player, stack, center, mode);
            extra.remove(center);
            destroy(player, stack, extra);
            return;
        }
        destroy(player, stack, digTargets(level, player, stack, center, state, face(player, center)));
    }

    /** Clic droit de la faux sur une culture mure : recolte du carre (avant la recolte a la main de la culture). */
    @SubscribeEvent
    public static void onRightClick(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        if (!(stack.getItem() instanceof AreaToolItem tool) || tool.kind() != AreaToolItem.Kind.SCYTHE) {
            return;
        }
        Level level = event.getLevel();
        BlockPos center = event.getPos();
        BlockState state = level.getBlockState(center);
        if (state.getBlock() instanceof CropTopBlock) {
            center = center.below();
            state = level.getBlockState(center);
        }
        if (!isRipe(state)) {
            return;
        }
        if (level instanceof ServerLevel server && event.getEntity() instanceof ServerPlayer player) {
            harvest(server, player, event.getHand(), stack, scytheTargets(server, player, stack, center, Scythe.HARVEST));
        }
        event.setCanceled(true);
        event.setCancellationResult(InteractionResult.sidedSuccess(level.isClientSide));
    }

    private static void destroy(ServerPlayer player, ItemStack stack, List<BlockPos> targets) {
        if (targets.isEmpty()) {
            return;
        }
        Level level = player.level();
        working = true;
        try {
            for (BlockPos pos : targets) {
                if (player.getMainHandItem() != stack || stack.isEmpty() || !level.mayInteract(player, pos)) {
                    break;
                }
                player.gameMode.destroyBlock(pos);
            }
        } finally {
            working = false;
        }
    }

    /** Recolte et replante (graine prise dans la recolte ; sans graine, la culture est simplement recoltee). */
    private static void harvest(ServerLevel level, ServerPlayer player, InteractionHand hand, ItemStack stack, List<BlockPos> targets) {
        int done = 0;
        for (BlockPos pos : targets) {
            if (stack.isEmpty() || !level.mayInteract(player, pos)) {
                break;
            }
            BlockState state = level.getBlockState(pos);
            if (!isRipe(state)) {
                continue;
            }
            List<ItemStack> drops;
            if (state.getBlock() instanceof FarmCropBlock farm) {
                drops = farm.harvest(level, pos, state, true);
            } else {
                drops = new ArrayList<>(Block.getDrops(state, level, pos, null, player, stack));
                ItemStack seed = state.getBlock().getCloneItemStack(level, pos, state);
                boolean planted = false;
                for (ItemStack drop : drops) {
                    if (!seed.isEmpty() && ItemStack.isSameItem(drop, seed)) {
                        drop.shrink(1);
                        planted = true;
                        break;
                    }
                }
                BlockState fresh = state.getBlock() instanceof CropBlock crop ? crop.getStateForAge(0) : state.setValue(NetherWartBlock.AGE, 0);
                if (planted) {
                    level.setBlock(pos, fresh, 3);
                } else {
                    level.removeBlock(pos, false);
                }
            }
            for (ItemStack drop : drops) {
                if (!drop.isEmpty()) {
                    Block.popResource(level, pos, drop);
                }
            }
            level.levelEvent(2001, pos, Block.getId(state));
            level.gameEvent(player, GameEvent.BLOCK_CHANGE, pos);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            done++;
        }
        if (done > 0) {
            level.playSound(null, player.blockPosition(), SoundEvents.CROP_BREAK, SoundSource.PLAYERS, 1F, 0.9F + level.random.nextFloat() * 0.2F);
        }
    }
}
