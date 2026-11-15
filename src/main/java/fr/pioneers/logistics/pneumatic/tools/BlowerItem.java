package fr.pioneers.logistics.pneumatic.tools;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;

/**
 * Souffleur : tant qu'on tient le clic droit, un jet d'air degage dans un cone de {@value #RANGE} blocs les feuilles, la neige en couche, les
 * herbes, fleurs, lianes et toiles (leurs objets tombent) et repousse objets et creatures. {@value #PER_BLOCK} litres par bloc degage,
 * {@value #PER_PULSE} par bouffee.
 */
public class BlowerItem extends AirToolItem {

    public static final double CAPACITY = 1500, PER_BLOCK = 3, PER_PULSE = 2;
    private static final int RANGE = 6, PERIOD = 4, MAX_PER_PULSE = 8;
    private static final double CONE = 0.9;

    public BlowerItem(Properties properties) {
        super(CAPACITY, properties);
    }

    @Override
    protected double perUse() {
        return PER_BLOCK;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!has(stack, PER_PULSE)) {
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72_000;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.NONE;
    }

    /** Ce que le souffle emporte. */
    public static boolean blowable(BlockState state) {
        if (state.isAir() || !state.getFluidState().isEmpty()) {
            return false;
        }
        return state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.COBWEB) || state.is(Blocks.SNOW) || state.canBeReplaced();
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity user, @NotNull ItemStack stack, int remaining) {
        if (!(level instanceof ServerLevel server) || remaining % PERIOD != 0) {
            return;
        }
        if (!use(stack, PER_PULSE)) {
            user.stopUsingItem();
            return;
        }
        Vec3 eye = user.getEyePosition(), view = user.getViewVector(1F);
        server.sendParticles(ParticleTypes.CLOUD, eye.x + view.x * 1.2, eye.y + view.y * 1.2 - 0.2, eye.z + view.z * 1.2, 3, view.x * 0.4, view.y * 0.4,
              view.z * 0.4, 0.15);
        server.playSound(null, user.blockPosition(), SoundEvents.ELYTRA_FLYING, SoundSource.PLAYERS, 0.05F, 1.8F);
        int cleared = 0;
        BlockPos base = BlockPos.containing(eye);
        for (BlockPos pos : BlockPos.betweenClosed(base.offset(-RANGE, -RANGE, -RANGE), base.offset(RANGE, RANGE, RANGE))) {
            if (cleared >= MAX_PER_PULSE || !has(stack, PER_BLOCK)) {
                break;
            }
            Vec3 to = Vec3.atCenterOf(pos).subtract(eye);
            double dist = to.length();
            if (dist > RANGE || dist < 0.5 || to.normalize().dot(view) < CONE) {
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (!blowable(state) || (user instanceof Player p && !level.mayInteract(p, pos))) {
                continue;
            }
            level.destroyBlock(pos, true, user);
            use(stack, PER_BLOCK);
            cleared++;
        }
        AABB zone = user.getBoundingBox().expandTowards(view.scale(RANGE)).inflate(1.5);
        for (Entity entity : level.getEntities(user, zone)) {
            Vec3 to = entity.position().subtract(eye);
            if (to.length() > RANGE || to.normalize().dot(view) < CONE - 0.1) {
                continue;
            }
            if (entity instanceof ItemEntity || entity instanceof LivingEntity) {
                entity.push(view.x * 0.35, 0.08 + view.y * 0.2, view.z * 0.35);
                entity.hurtMarked = true;
            }
        }
    }
}
