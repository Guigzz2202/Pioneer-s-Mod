package fr.pioneers.gear;

import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : fusil a plasma modulaire : clic droit = un tir d'energie ({@link EnergyBoltEntity}) droit et sans chute, 2 000 FE par tir
 * (+50 % par niveau de surcharge). Modules : surcharge (+35 % de degats par niveau), salve (un tir de plus par niveau, en eventail),
 * cadence rapide (-25 % d'attente par niveau). Tenu a deux mains comme une arbalete.
 */
public class ModularRifleItem extends Item implements ModularGear {

    public static final long ENERGY_PER_SHOT = 2_000;
    /** Attente entre deux tirs (ticks), avant la cadence rapide. */
    public static final int COOLDOWN = 12;

    private final ModularTier tier;

    public ModularRifleItem(ModularTier tier, Properties properties) {
        super(properties.stacksTo(1).fireResistant().rarity(tier.rarity));
        this.tier = tier;
    }

    @Override
    public ModularTier tier() {
        return tier;
    }

    @Override
    public GearKind kind() {
        return GearKind.RIFLE;
    }

    public static long shotCost(ItemStack stack) {
        return ENERGY_PER_SHOT + ENERGY_PER_SHOT * Modules.level(stack, GearModule.OVERCHARGE) / 2;
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        int bolts = 1 + Modules.level(stack, GearModule.VOLLEY);
        long cost = shotCost(stack) * bolts;
        if (!player.isCreative() && Modules.energy(stack) < cost) {
            if (!level.isClientSide) {
                player.displayClientMessage(Component.translatable("gear.pioneers.rifle.empty"), true);
                level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.DISPENSER_FAIL, SoundSource.PLAYERS, 0.6F, 1.4F);
            }
            return InteractionResultHolder.fail(stack);
        }
        if (!level.isClientSide) {
            float damage = tier.rifleDamage * (1 + 0.35F * Modules.level(stack, GearModule.OVERCHARGE));
            for (int k = 0; k < bolts; k++) {
                float yaw = bolts == 1 ? 0 : (k - (bolts - 1) / 2.0F) * 6.0F;
                EnergyBoltEntity bolt = new EnergyBoltEntity(level, player, tier, damage);
                bolt.shootFromRotation(player, player.getXRot(), player.getYRot() + yaw, 0.0F, 3.2F, 0.4F);
                level.addFreshEntity(bolt);
            }
            if (!player.isCreative()) {
                Modules.drain(stack, cost);
            }
            level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F,
                  1.5F + tier.ordinal() * 0.12F);
        }
        int rapid = Modules.level(stack, GearModule.RAPID_FIRE);
        player.getCooldowns().addCooldown(this, Math.max(3, Math.round(COOLDOWN / (1 + 0.25F * rapid))));
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    @Override
    public boolean onEntitySwing(ItemStack stack, LivingEntity entity) {
        return false;
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return Modules.provider(stack);
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Modules.barWidth(stack);
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return Modules.barColor(stack);
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        Modules.tooltip(stack, tooltip);
        float damage = tier.rifleDamage * (1 + 0.35F * Modules.level(stack, GearModule.OVERCHARGE));
        tips.spec(Component.translatable("gear.pioneers.rifle.damage", GearTips.number(damage), 1 + Modules.level(stack, GearModule.VOLLEY)));
    }
}
