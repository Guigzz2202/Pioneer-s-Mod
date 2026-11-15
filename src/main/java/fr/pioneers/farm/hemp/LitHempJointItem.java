package fr.pioneers.farm.hemp;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.leisure.PiLeisure;
import fr.pioneers.farm.leisure.Smoking;
import java.util.List;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.StringUtil;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
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
 * 1.0.4-beta (lot 6) : joint de chanvre ALLUME. Meme geste que le cigare ({@code LitCigarItem}) : maintenir le clic droit pour tirer (fumee
 * qui monte), relacher pour souffler (accroupi : un rond de fumee) ; il se consume en {@link #BURN} ticks (2 minutes), meme dans la poche, et
 * chaque bouffee en coute {@link #PUFF_COST}. Une bouffee d'au moins {@link #PUFF_MIN} ticks donne des EFFETS MODERES ({@link #effects}) :
 * fringale (Faim I, 30 s), pas lourds (Lenteur I, 8 s), detente (Regeneration I, 5 s) ; puis le joint se repose {@link #COOLDOWN} ticks
 * (temps de recharge de l'objet : pas de nouvelle bouffee). Aucune dependance. NBT : {@code End} (heure du monde de la fin).
 */
public class LitHempJointItem extends Item {

    public static final int BURN = 2400, PUFF_COST = 400, PUFF_MIN = 10, COOLDOWN = 400;
    public static final int HUNGER_TICKS = 600, SLOW_TICKS = 160, REGEN_TICKS = 100;

    public LitHempJointItem(Properties properties) {
        super(properties);
    }

    public static ItemStack make(long end) {
        ItemStack stack = new ItemStack(PiHemp.LIT_HEMP_JOINT.get());
        stack.getOrCreateTag().putLong("End", end);
        return stack;
    }

    /** Ticks restants (le joint pris dans l'onglet creatif, sans heure de fin, est neuf). */
    public static int remaining(ItemStack stack, long now) {
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("End")) {
            return BURN;
        }
        return (int) Math.max(0, Math.min(BURN, tag.getLong("End") - now));
    }

    /** Effets d'une bouffee (moderes, courts ; aucun n'empile au-dela de sa duree). */
    public static void effects(LivingEntity entity) {
        entity.addEffect(new MobEffectInstance(MobEffects.HUNGER, HUNGER_TICKS, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, SLOW_TICKS, 0));
        entity.addEffect(new MobEffectInstance(MobEffects.REGENERATION, REGEN_TICKS, 0));
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
            level.playSound(null, entity.getX(), entity.getEyeY(), entity.getZ(), PiLeisure.PIPE_PUFF.get(), SoundSource.PLAYERS, 0.4F,
                  1.15F + level.random.nextFloat() * 0.2F);
        }
        if (level.isClientSide && used > 4) {
            Smoking.wisp(level, entity, stack, 5);
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
        effects(player);
        player.getCooldowns().addCooldown(this, COOLDOWN);
    }

    @Override
    public void inventoryTick(@NotNull ItemStack stack, @NotNull Level level, @NotNull Entity entity, int slot, boolean selected) {
        long now = Smoking.now(level);
        if (level.isClientSide) {
            if (!(entity instanceof LivingEntity l && l.isUsingItem())) {
                Smoking.wisp(level, entity, stack, 6);
            }
            return;
        }
        CompoundTag tag = stack.getOrCreateTag();
        if (!tag.contains("End")) {
            tag.putLong("End", now + BURN);
        } else if (tag.getLong("End") <= now) {
            stack.shrink(1);
            level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.15F, 1.8F);
            if (entity instanceof Player player) {
                player.displayClientMessage(Component.translatable("hemp.pioneers.joint.out"), true);
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
        tips.hint("hemp.pioneers.joint.help");
    }
}
