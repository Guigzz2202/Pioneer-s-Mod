package fr.pioneers.gear.electric;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 9) : matraque electrique Voltaique. Sous tension : {@value #DAMAGE} points de degats et une decharge (lenteur II et
 * faiblesse 2 s, etincelles) pour {@value #PER_HIT} FE par coup ; a sec : {@value #DAMAGE_FLAT} points, comme un baton. Jamais cassee.
 */
public class VoltaicBatonItem extends Item implements PoweredItem {

    public static final long CAPACITY = 250_000, PER_HIT = 250;
    public static final float DAMAGE = 7, DAMAGE_FLAT = 3;
    public static final int STUN_TICKS = 40;

    public VoltaicBatonItem(Properties properties) {
        super(properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public long energyCapacity() {
        return CAPACITY;
    }

    /** Degats totaux (poing compris). */
    public static float damage(ItemStack stack) {
        return ElectricCharge.energy(stack) >= PER_HIT ? DAMAGE : DAMAGE_FLAT;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", damage(stack) - 1,
              AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", -2.2, AttributeModifier.Operation.ADDITION));
        return b.build();
    }

    /** Decharge : effets et etincelles si l'energie suit. */
    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        if (!attacker.level().isClientSide && ElectricCharge.use(stack, PER_HIT)) {
            target.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, STUN_TICKS, 1), attacker);
            target.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, STUN_TICKS, 0), attacker);
            if (attacker.level() instanceof ServerLevel server) {
                server.sendParticles(ParticleTypes.ELECTRIC_SPARK, target.getX(), target.getY(0.6), target.getZ(), 14, 0.3, 0.4, 0.3, 0.25);
            }
            attacker.level().playSound(null, target.blockPosition(), SoundEvents.BEE_STING, SoundSource.PLAYERS, 0.7F, 1.8F);
        }
        return true;
    }

    @Override
    public boolean canAttackBlock(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player) {
        return !player.isCreative();
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBookEnchantable(ItemStack stack, ItemStack book) {
        return false;
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ElectricCharge.provider(stack);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Math.round(13F * ElectricCharge.ratio(stack));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return ElectricCharge.ENERGY_BAR;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("electric.pioneers.baton.use", (int) DAMAGE, PER_HIT, (int) DAMAGE_FLAT);
        ElectricCharge.tooltip(stack, tooltip);
        GearTips.rank(tips, GearTips.MK1);
    }
}
