package fr.pioneers.farm.leisure;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.5 : cigare allume. Il se consume en {@link #BURN} ticks (8 minutes), meme dans la poche ; chaque bouffee en coute {@link #PUFF_COST} de
 * plus. Maintenir le clic droit pour tirer, relacher pour souffler (accroupi : un rond de fumee). Pose sur un cendrier, il se consume deux fois
 * moins vite. NBT : {@code End} (heure du monde ou il s'eteint) : rien n'est ecrit a chaque tick. Purement cosmetique.
 */
public class LitCigarItem extends Item {

    public static final int BURN = 9600, PUFF_COST = 200, PUFF_MIN = 10;

    public LitCigarItem(Properties properties) {
        super(properties);
    }

    public static ItemStack make(long end) {
        ItemStack stack = new ItemStack(PiLeisure.LIT_CIGAR.get());
        stack.getOrCreateTag().putLong("End", end);
        return stack;
    }

    /** Ticks restants (le cigare sans heure de fin, pris dans l'onglet creatif, est neuf). */
    public static int remaining(ItemStack stack, long now) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("End")) {
            return BURN;
        }
        return (int) Math.max(0, Math.min(BURN, tag.getLong("End") - now));
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(player.getItemInHand(hand));
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.TOOT_HORN;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72000;
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        if (!level.isClientSide && used == 3) {
            level.playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), PiLeisure.PIPE_PUFF.get(), SoundSource.PLAYERS, 0.45F,
                  1.05F + level.random.nextFloat() * 0.2F);
        }
        if (level.isClientSide && used > 4) {
            Smoking.wisp(level, entity, stack, 6);
        }
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        if (level.isClientSide || used < PUFF_MIN || !(entity instanceof Player player)) {
            return;
        }
        Smoking.exhale((ServerLevel) level, player, Math.min(1F, used / 40F));
        long now = level.getGameTime();
        stack.getOrCreateTag().putLong("End", now + remaining(stack, now) - PUFF_COST);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        long now = Smoking.now(level);
        if (level.isClientSide) {
            if (!(entity instanceof LivingEntity l && l.isUsingItem())) {
                Smoking.wisp(level, entity, stack, 7);
            }
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("End")) {
            tag.putLong("End", now + BURN);
        } else if (tag.getLong("End") <= now) {
            stack.shrink(1);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.2F, 1.6F);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("leisure.pioneers.cigar.out"), true);
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Math.round(13F * remaining(stack, Smoking.clientNow) / BURN);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return 0xE07030;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
        long now = level != null ? level.getGameTime() : Smoking.clientNow;
        tips.state("leisure.pioneers.cigar.left", StringUtil.formatTickDuration(remaining(stack, now)));
        tips.hint("leisure.pioneers.cigar.help");
    }
}
