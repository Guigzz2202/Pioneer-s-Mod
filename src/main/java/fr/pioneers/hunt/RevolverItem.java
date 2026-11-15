package fr.pioneers.hunt;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.UseAnim;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.3, decision 11) : le REVOLVER (age industriel, MKI). Barillet de {@link HuntRules#REVOLVER_ROUNDS}
 * coups tires avec des CARTOUCHES ({@code pioneers:revolver_ammo}) ; RECHARGEMENT BALLE PAR BALLE : maintenir le clic droit accroupi (ou
 * barillet vide) : une cartouche toutes les {@link HuntRules#REVOLVER_LOAD} ticks (-25 % au niveau 10 de la Chasse) jusqu'a 6 ou plus de
 * cartouches ; clic droit = un coup, moins fort que le mousquet mais 8 ticks entre deux coups. Jamais sous l'eau. Tir : {@link Firearms}.
 */
public class RevolverItem extends Item {

    public static final TagKey<Item> AMMO = TagKey.create(Registries.ITEM, Pioneers.rl("revolver_ammo"));
    public static final String ROUNDS = "Rounds";

    public RevolverItem(Properties properties) {
        super(properties.stacksTo(1).durability(768));
    }

    public static int rounds(ItemStack stack) {
        return stack.hasTag() ? Math.max(0, Math.min(HuntRules.REVOLVER_ROUNDS, stack.getTag().getInt(ROUNDS))) : 0;
    }

    public static void setRounds(ItemStack stack, int n) {
        stack.getOrCreateTag().putInt(ROUNDS, Math.max(0, Math.min(HuntRules.REVOLVER_ROUNDS, n)));
    }

    public static int loadTicks(@Nullable Player p) {
        return HuntRules.reload(HuntRules.REVOLVER_LOAD, p == null ? 0 : HuntSkill.reloadBonus(p));
    }

    static boolean hasCartridge(Player p) {
        return p.getAbilities().instabuild || Ammo.find(p, AMMO) >= 0;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int n = rounds(stack);
        if (player.isShiftKeyDown() || n == 0) {
            if (n < HuntRules.REVOLVER_ROUNDS && hasCartridge(player)) {
                player.startUsingItem(hand);
                if (!level.isClientSide) {
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.5F, 1.6F);
                }
                return InteractionResultHolder.consume(stack);
            }
            if (n == 0) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("hunt.pioneers.revolver.no_ammo"), true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.8F);
                }
                return InteractionResultHolder.fail(stack);
            }
        }
        if (Firearms.wet(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("hunt.pioneers.wet"), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.5F, 1.2F);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
            Firearms.fire(sl, sp, HuntRules.REVOLVER_DAMAGE, HuntRules.REVOLVER_RANGE,
                  HuntRules.spread(HuntRules.REVOLVER_SPREAD, HuntSkill.spreadBonus(sp)), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, 2.2F,
                  0.55F + sl.random.nextFloat() * 0.1F, false);
            stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
        } else {
            MusketItem.recoil(player, HuntRules.REVOLVER_RECOIL);
        }
        setRounds(stack, n - 1);
        player.getCooldowns().addCooldown(this, HuntRules.REVOLVER_COOLDOWN);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Une cartouche toutes les {@link #loadTicks} ticks, jusqu'a 6 ou plus de cartouches. */
    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        if (level.isClientSide || !(entity instanceof Player p)) {
            return;
        }
        int used = getUseDuration(stack) - remaining;
        int step = loadTicks(p);
        if (used > 0 && used % step == 0) {
            if (rounds(stack) < HuntRules.REVOLVER_ROUNDS && insert(p)) {
                setRounds(stack, rounds(stack) + 1);
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.5F, 1.9F);
            }
            if (rounds(stack) >= HuntRules.REVOLVER_ROUNDS || !hasCartridge(p)) {
                p.releaseUsingItem();
                level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_QUICK_CHARGE_1, SoundSource.PLAYERS, 0.5F, 1.7F);
            }
        }
    }

    static boolean insert(Player p) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        int i = Ammo.find(p, AMMO);
        if (i < 0) {
            return false;
        }
        p.getInventory().getItem(i).shrink(1);
        return true;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72_000;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.CROSSBOW;
    }

    @Override
    public void initializeClient(java.util.function.Consumer<IClientItemExtensions> consumer) {
        //tenue a l'ecran (client seulement) : visee chargee, barillet ouvert vers le joueur pendant le rechargement
        consumer.accept(DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> fr.pioneers.client.hunt.HuntItems::revolver));
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec(Component.translatable("hunt.pioneers.revolver.rounds", rounds(stack), HuntRules.REVOLVER_ROUNDS));
        tips.spec(Component.translatable("hunt.pioneers.revolver.stats", fr.pioneers.core.tooltip.GearTips.number(HuntRules.REVOLVER_DAMAGE),
              HuntRules.REVOLVER_RANGE));
        tips.spec(Component.translatable("hunt.pioneers.revolver.how"));
    }
}
