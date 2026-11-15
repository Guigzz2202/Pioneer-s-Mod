package fr.pioneers.nature;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.3.2 : une hache (tout outil qui sait {@code axe_dig} : haches du mod, vanilla, multi-outil) abat l'arbre entier. Les troncs relies au bloc
 * casse (26 voisins, jamais en dessous de lui) tombent d'un coup s'ils portent un feuillage naturel (feuilles non posees) : une maison en
 * rondins ne s'ecroule pas. Chaque tronc use l'outil d'un point ; l'abattage s'arrete si l'outil casse. Accroupi : un seul bloc. Les feuilles
 * pourrissent ensuite d'elles-memes, en quelques secondes depuis la 1.0.3-beta ({@link LeafDecay}). Config {@code tools.treeFelling} /
 * {@code tools.treeFellingMax}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class TreeFeller {

    /** Feuilles naturelles minimales autour des troncs pour reconnaitre un arbre. */
    private static final int MIN_LEAVES = 4;

    private static boolean felling;

    private TreeFeller() {
    }

    /** Abattage en cours (les troncs casses par l'abattage ne programment pas chacun leur vague de {@link LeafDecay}). */
    public static boolean felling() {
        return felling;
    }

    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onBreak(BlockEvent.BreakEvent event) {
        if (felling || event.isCanceled() || !(event.getPlayer() instanceof ServerPlayer player) || player.isShiftKeyDown()
            || !event.getState().is(BlockTags.LOGS) || !PiCommonConfig.get(PiCommonConfig.TREE_FELLING, true)) {
            return;
        }
        ItemStack tool = player.getMainHandItem();
        if (tool.isEmpty() || !tool.canPerformAction(ToolActions.AXE_DIG)) {
            return;
        }
        Level level = player.level();
        List<BlockPos> logs = tree(level, event.getPos(), PiCommonConfig.get(PiCommonConfig.TREE_FELLING_MAX, 192));
        if (logs.isEmpty()) {
            return;
        }
        felling = true;
        List<BlockPos> felled = new ArrayList<>();
        felled.add(event.getPos().immutable());
        try {
            for (BlockPos pos : logs) {
                if (player.getMainHandItem() != tool || tool.isEmpty() || !level.mayInteract(player, pos)) {
                    break;
                }
                if (player.gameMode.destroyBlock(pos)) {
                    felled.add(pos.immutable());
                }
            }
        } finally {
            felling = false;
        }
        //1.0.3-beta (lot 11) : les feuilles de l'arbre abattu tombent en quelques secondes (une vague depuis tous les troncs retires)
        if (level instanceof net.minecraft.server.level.ServerLevel server) {
            LeafDecay.schedule(server, felled);
        }
    }

    /** Troncs a abattre en plus du bloc casse (du plus proche au plus loin), ou rien si ce n'est pas un arbre naturel. */
    static List<BlockPos> tree(Level level, BlockPos origin, int max) {
        Set<BlockPos> seen = new HashSet<>();
        Set<BlockPos> leaves = new HashSet<>();
        List<BlockPos> logs = new ArrayList<>();
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        seen.add(origin);
        queue.add(origin);
        while (!queue.isEmpty()) {
            BlockPos at = queue.poll();
            for (int dx = -1; dx <= 1; dx++) {
                for (int dy = -1; dy <= 1; dy++) {
                    for (int dz = -1; dz <= 1; dz++) {
                        BlockPos p = at.offset(dx, dy, dz);
                        if (p.getY() < origin.getY() || !seen.add(p) || !level.isLoaded(p)) {
                            continue;
                        }
                        BlockState state = level.getBlockState(p);
                        if (state.is(BlockTags.LOGS)) {
                            if (level.getBlockEntity(p) == null && state.getDestroySpeed(level, p) >= 0 && logs.size() < max) {
                                logs.add(p);
                                queue.add(p);
                            }
                        } else if (state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))) {
                            leaves.add(p);
                        }
                    }
                }
            }
        }
        if (leaves.size() < MIN_LEAVES) {
            return List.of();
        }
        logs.sort(Comparator.comparingDouble(p -> p.distSqr(origin)));
        return logs;
    }
}
