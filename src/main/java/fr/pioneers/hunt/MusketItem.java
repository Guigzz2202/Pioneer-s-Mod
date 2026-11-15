package fr.pioneers.hunt;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
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
import net.minecraftforge.common.Tags;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 10, specification §6.3, decision 11) : le MOUSQUET (age du bronze et de la vapeur). Chargement : maintenir le clic droit
 * {@link HuntRules#MUSKET_RELOAD} ticks (3 s ; -25 % au niveau 10 de la Chasse) avec une BALLE DE PLOMB ({@code pioneers:musket_ammo}) et une
 * POUDRE A CANON ({@code forge:gunpowder}) dans l'inventaire : animation de la baguette (proprietes d'objet {@code pioneers:reload},
 * {@code pioneers:loaded}, pose de chargement de l'arbalete a la troisieme personne), puis relacher. Charge : un clic droit tire UN coup
 * tres puissant, portee 64, fumee, recul ; jamais sous l'eau. Tir : {@link Firearms} (serveur seul juge).
 */
public class MusketItem extends Item {

    public static final TagKey<Item> AMMO = TagKey.create(Registries.ITEM, Pioneers.rl("musket_ammo"));
    public static final String LOADED = "Loaded";

    public MusketItem(Properties properties) {
        super(properties.stacksTo(1).durability(384));
    }

    public static boolean loaded(ItemStack stack) {
        return stack.hasTag() && stack.getTag().getBoolean(LOADED);
    }

    public static void setLoaded(ItemStack stack, boolean on) {
        if (on) {
            stack.getOrCreateTag().putBoolean(LOADED, true);
        } else if (stack.hasTag()) {
            stack.getTag().remove(LOADED);
        }
    }

    /** Duree de chargement de ce joueur (ticks). */
    public static int reloadTicks(@Nullable Player p) {
        return HuntRules.reload(HuntRules.MUSKET_RELOAD, p == null ? 0 : HuntSkill.reloadBonus(p));
    }

    /** Le joueur a-t-il une balle et une poudre (ou est-il en creatif) ? */
    public static boolean hasCharge(Player p) {
        return p.getAbilities().instabuild || Ammo.find(p, AMMO) >= 0 && Ammo.find(p, Tags.Items.GUNPOWDER) >= 0;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (loaded(stack)) {
            if (Firearms.wet(player)) {
                if (!level.isClientSide) {
                    player.displayClientMessage(Component.translatable("hunt.pioneers.wet"), true);
                    level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 0.8F);
                }
                return InteractionResultHolder.fail(stack);
            }
            if (level instanceof ServerLevel sl && player instanceof ServerPlayer sp) {
                Firearms.fire(sl, sp, HuntRules.MUSKET_DAMAGE, HuntRules.MUSKET_RANGE, HuntRules.spread(HuntRules.MUSKET_SPREAD, HuntSkill.spreadBonus(sp)),
                      SoundEvents.GENERIC_EXPLODE, 2.6F, 1.65F + sl.random.nextFloat() * 0.1F, true);
                stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(hand));
            } else {
                recoil(player, HuntRules.MUSKET_RECOIL);
            }
            setLoaded(stack, false);
            player.getCooldowns().addCooldown(this, 10);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        if (!hasCharge(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("hunt.pioneers.musket.no_ammo"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (Firearms.wet(player)) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("hunt.pioneers.wet"), true);
            }
            return InteractionResultHolder.fail(stack);
        }
        player.startUsingItem(hand);
        if (!level.isClientSide) {
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.CROSSBOW_LOADING_START, SoundSource.PLAYERS, 0.7F, 0.8F);
        }
        return InteractionResultHolder.consume(stack);
    }

    /** Recul : le canon releve la vue du tireur (cote client, la camera est au joueur). */
    static void recoil(Player p, float degrees) {
        p.setXRot(p.getXRot() - degrees);
        p.xRotO = p.getXRot();
    }

    @Override
    public void onUseTick(@NotNull Level level, @NotNull LivingEntity entity, @NotNull ItemStack stack, int remaining) {
        int used = getUseDuration(stack) - remaining;
        int need = reloadTicks(entity instanceof Player p ? p : null);
        if (!level.isClientSide) {
            if (used == need / 3) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_MIDDLE, SoundSource.PLAYERS, 0.6F, 0.7F);
            } else if (used == need) {
                level.playSound(null, entity.getX(), entity.getY(), entity.getZ(), SoundEvents.CROSSBOW_LOADING_END, SoundSource.PLAYERS, 0.8F, 0.75F);
            }
        }
    }

    @Override
    public void releaseUsing(@NotNull ItemStack stack, @NotNull Level level, @NotNull LivingEntity entity, int remaining) {
        if (!(entity instanceof Player p) || level.isClientSide) {
            return;
        }
        int used = getUseDuration(stack) - remaining;
        if (used >= reloadTicks(p) && !loaded(stack) && load(p)) {
            setLoaded(stack, true);
            level.playSound(null, p.getX(), p.getY(), p.getZ(), SoundEvents.CROSSBOW_QUICK_CHARGE_3, SoundSource.PLAYERS, 0.7F, 0.6F);
        }
    }

    /** Consomme une balle et une poudre (rien en creatif). */
    public static boolean load(Player p) {
        if (p.getAbilities().instabuild) {
            return true;
        }
        int ball = Ammo.find(p, AMMO), powder = Ammo.find(p, Tags.Items.GUNPOWDER);
        if (ball < 0 || powder < 0) {
            return false;
        }
        p.getInventory().getItem(ball).shrink(1);
        p.getInventory().getItem(powder).shrink(1);
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
    public boolean useOnRelease(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        //tenue a l'ecran (client seulement) : visee chargee, canon releve pendant le chargement
        consumer.accept(DistExecutor.unsafeCallWhenOn(Dist.CLIENT, () -> fr.pioneers.client.hunt.HuntItems::musket));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec(Component.translatable(loaded(stack) ? "hunt.pioneers.musket.loaded" : "hunt.pioneers.musket.empty"));
        tips.spec(Component.translatable("hunt.pioneers.musket.stats", fr.pioneers.core.tooltip.GearTips.number(HuntRules.MUSKET_DAMAGE),
              HuntRules.MUSKET_RANGE));
        tips.spec(Component.translatable("hunt.pioneers.musket.how"));
    }
}
