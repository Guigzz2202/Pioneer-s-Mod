package fr.pioneers.world.hunt;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
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
 * 1.0.5-beta (lot 10, specification §6.3) : le HARPON (age du bronze et de la vapeur). Maintenir puis relacher le clic droit le LANCE comme
 * un trident (aussi sous l'eau) ; l'objet reste en main, corde deroulee (marque {@code Thrown}, propriete {@code pioneers:thrown}) ; un
 * clic droit ramene la corde ({@link HarpoonEntity#reel}). Indispensable contre baleines et requins (degats x 2,5 contre la faune marine).
 */
public class HarpoonItem extends Item {

    public static final String THROWN = "Thrown";

    public HarpoonItem(Properties properties) {
        super(properties.stacksTo(1).durability(250));
    }

    @Nullable
    public static UUID thrown(ItemStack stack) {
        return stack.hasTag() && stack.getTag().hasUUID(THROWN) ? stack.getTag().getUUID(THROWN) : null;
    }

    static void clear(ItemStack stack) {
        if (stack.hasTag()) {
            stack.getTag().remove(THROWN);
        }
    }

    /** Le harpon {@code id} est revenu : la marque de l'objet du joueur se leve. */
    public static void recover(Player p, UUID id) {
        for (int i = 0; i < p.getInventory().getContainerSize(); i++) {
            ItemStack s = p.getInventory().getItem(i);
            if (s.getItem() instanceof HarpoonItem && id.equals(thrown(s))) {
                clear(s);
            }
        }
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        UUID id = thrown(stack);
        if (id != null) {
            if (level instanceof ServerLevel sl) {
                Entity e = sl.getEntity(id);
                if (e instanceof HarpoonEntity h && h.isAlive()) {
                    h.reel();
                } else {
                    clear(stack);       //harpon perdu (decharge) : la corde revient seule
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FISHING_BOBBER_RETRIEVE, SoundSource.PLAYERS, 0.8F, 0.8F);
                }
            }
            player.swing(hand);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        player.startUsingItem(hand);
        return InteractionResultHolder.consume(stack);
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int remaining) {
        if (!(entity instanceof Player p) || getUseDuration(stack) - remaining < 10 || thrown(stack) != null) {
            return;
        }
        if (level instanceof ServerLevel sl) {
            HarpoonEntity h = launch(sl, p);
            stack.getOrCreateTag().putUUID(THROWN, h.getUUID());
            stack.hurtAndBreak(1, p, x -> x.broadcastBreakEvent(p.getUsedItemHand()));
        }
    }

    /** Lance un harpon depuis le joueur (aussi pour les GameTests). */
    public static HarpoonEntity launch(ServerLevel level, Player p) {
        HarpoonEntity h = new HarpoonEntity(level, p);
        h.shootFromRotation(p, p.getXRot(), p.getYRot(), 0.0F, HuntRules.HARPOON_SPEED, 0.6F);
        level.addFreshEntity(h);
        level.playSound(null, p.getX(), p.getY(), p.getZ(), p.isUnderWater() ? SoundEvents.TRIDENT_RIPTIDE_1 : SoundEvents.TRIDENT_THROW,
              SoundSource.PLAYERS, 1.0F, 0.8F);
        return h;
    }

    @Override
    public int getUseDuration(@NotNull ItemStack stack) {
        return 72_000;
    }

    @NotNull
    @Override
    public UseAnim getUseAnimation(@NotNull ItemStack stack) {
        return UseAnim.SPEAR;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec(Component.translatable(thrown(stack) != null ? "hunt.pioneers.harpoon.out" : "hunt.pioneers.harpoon.ready"));
        tips.spec(Component.translatable("hunt.pioneers.harpoon.stats", fr.pioneers.core.tooltip.GearTips.number(HuntRules.HARPOON_DAMAGE),
              fr.pioneers.core.tooltip.GearTips.number(HuntRules.HARPOON_DAMAGE * HuntRules.HARPOON_MARINE), HuntRules.HARPOON_ROPE));
        tips.spec(Component.translatable("hunt.pioneers.harpoon.how"));
    }
}
