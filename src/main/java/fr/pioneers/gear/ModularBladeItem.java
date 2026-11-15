package fr.pioneers.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : lame d'energie modulaire. Degats du tier (moitie sans energie), 600 FE par coup porte. Modules : tranchant a plasma (+3 par
 * niveau), vampirisme ({@link GearEvents}), collecteur de butin (Butin I a III).
 */
public class ModularBladeItem extends SwordItem implements ModularGear {

    public static final long ENERGY_PER_HIT = 600;

    private final ModularTier tier;

    public ModularBladeItem(ModularTier tier, Properties properties) {
        super(tier.toolTier(), 3, -2.4F, properties.stacksTo(1).fireResistant().rarity(tier.rarity));
        this.tier = tier;
    }

    @Override
    public ModularTier tier() {
        return tier;
    }

    @Override
    public GearKind kind() {
        return GearKind.BLADE;
    }

    /** Degats totaux de la lame (poing compris). */
    public static float damage(ItemStack stack) {
        if (!(stack.getItem() instanceof ModularBladeItem blade)) {
            return 1;
        }
        float base = blade.tier.bladeDamage + 3 * Modules.level(stack, GearModule.PLASMA_EDGE);
        return Modules.energy(stack) >= ENERGY_PER_HIT ? base : Math.max(2, base / 2);
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

    @Override
    public boolean hurtEnemy(@NotNull ItemStack stack, @NotNull LivingEntity target, @NotNull LivingEntity attacker) {
        if (!attacker.level().isClientSide) {
            Modules.drain(stack, ENERGY_PER_HIT);
        }
        return true;
    }

    @Override
    public int getEnchantmentLevel(ItemStack stack, Enchantment enchantment) {
        int own = super.getEnchantmentLevel(stack, enchantment);
        if (enchantment == Enchantments.MOB_LOOTING) {
            return Math.max(own, Modules.level(stack, GearModule.LOOTING));
        }
        return own;
    }

    @Override
    public Map<Enchantment, Integer> getAllEnchantments(ItemStack stack) {
        Map<Enchantment, Integer> map = new HashMap<>(super.getAllEnchantments(stack));
        int looting = Modules.level(stack, GearModule.LOOTING);
        if (looting > 0) {
            map.put(Enchantments.MOB_LOOTING, looting);
        }
        return map;
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
        return Modules.provider(stack);
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
        Modules.tooltip(stack, tooltip);
    }
}
