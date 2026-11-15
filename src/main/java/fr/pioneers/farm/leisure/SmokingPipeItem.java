package fr.pioneers.farm.leisure;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
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
 * 1.5 : pipe (epi de mais, bruyere, terre). Clic droit : la bourrer (un tabac a pipe : {@link #FILL} bouffees), l'allumer (allumettes ou
 * briquet, main gauche ou inventaire), puis maintenir pour tirer et relacher pour souffler (accroupi : un rond de fumee). Elle s'eteint apres
 * {@link #IDLE_OUT} ticks sans bouffee. NBT : {@code Fill} (bouffees restantes), {@code LitUntil} (heure du monde ou elle s'eteint). Le modele
 * allume est choisi par la propriete {@code pioneers:lit}. Purement cosmetique.
 */
public class SmokingPipeItem extends Item {

    public static final int FILL = 8, IDLE_OUT = 1200, PUFF_MIN = 10;

    public SmokingPipeItem(Properties properties) {
        super(properties);
    }

    public static int fill(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : tag.getInt("Fill");
    }

    public static boolean lit(ItemStack stack, long now) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getInt("Fill") > 0 && tag.getLong("LitUntil") > now;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        long now = Smoking.now(level);
        if (fill(stack) == 0) {
            ItemStack tobacco = Smoking.find(player, PiLeisure.PIPE_TOBACCO);
            if (!level.isClientSide) {
                if (tobacco.isEmpty()) {
                    player.displayClientMessage(Component.translatable("leisure.pioneers.pipe.no_tobacco"), true);
                } else {
                    if (!player.getAbilities().instabuild) {
                        tobacco.shrink(1);
                    }
                    stack.getOrCreateTag().putInt("Fill", FILL);
                    stack.getOrCreateTag().remove("LitUntil");
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), PiLeisure.PIPE_FILL.get(), SoundSource.PLAYERS, 0.7F, 1F);
                    player.displayClientMessage(Component.translatable("leisure.pioneers.pipe.filled", FILL), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!lit(stack, now)) {
            if (!level.isClientSide) {
                if (Smoking.strike(player, player.getX(), player.getEyeY(), player.getZ())) {
                    stack.getOrCreateTag().putLong("LitUntil", now + IDLE_OUT);
                    player.displayClientMessage(Component.translatable("leisure.pioneers.pipe.lit"), true);
                } else {
                    player.displayClientMessage(Component.translatable("leisure.pioneers.pipe.no_light"), true);
                }
            }
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
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
            level.playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), PiLeisure.PIPE_PUFF.get(), SoundSource.PLAYERS, 0.5F,
                  0.9F + level.random.nextFloat() * 0.2F);
        }
        if (level.isClientSide && used > 4) {
            Smoking.wisp(level, entity, stack, 6);
        }
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int timeLeft) {
        int used = getUseDuration(stack) - timeLeft;
        long now = level.getGameTime();
        if (level.isClientSide || used < PUFF_MIN || !(entity instanceof Player player) || !lit(stack, now)) {
            return;
        }
        Smoking.exhale((ServerLevel) level, player, Math.min(1F, used / 40F));
        CompoundTag tag = stack.getOrCreateTag();
        int fill = tag.getInt("Fill") - 1;
        tag.putInt("Fill", Math.max(0, fill));
        if (fill <= 0) {
            tag.remove("LitUntil");
        } else {
            tag.putLong("LitUntil", now + IDLE_OUT);
        }
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        long now = Smoking.now(level);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("LitUntil")) {
            return;
        }
        if (level.isClientSide) {
            if (lit(stack, now) && !(entity instanceof LivingEntity l && l.isUsingItem())) {
                Smoking.wisp(level, entity, stack, 9);
            }
        } else if (tag.getLong("LitUntil") <= now) {
            tag.remove("LitUntil");
            if (entity instanceof Player player && (selected || player.getOffhandItem() == stack)) {
                player.displayClientMessage(Component.translatable("leisure.pioneers.pipe.out"), true);
            }
        }
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
        int fill = fill(stack);
        long now = level != null ? level.getGameTime() : Smoking.clientNow;
        Component state = fill == 0 ? Component.translatable("leisure.pioneers.pipe.state.empty")
              : Component.translatable(lit(stack, now) ? "leisure.pioneers.pipe.state.lit" : "leisure.pioneers.pipe.state.filled", fill);
        tips.state(state);
        tips.hint("leisure.pioneers.pipe.help");
    }
}
