package fr.pioneers.gear.electric;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.SlotAccess;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ClickAction;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.DistExecutor;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.3-beta (lot 9) : jetpack a hydrogene, porte dans la case du plastron (comme Mekanism) ; sac dorsal 3D ({@code armor3d.py}, famille
 * {@code hydrogen_jetpack}). Reservoir de {@value #HYDROGEN} mB d'hydrogene (buffer de gaz, capsule) ET batterie de {@value #CAPACITY} FE
 * (buffer d'energie). Poussee : saut maintenu en l'air, {@value #THRUST_H2} mB + {@value #THRUST_FE} FE par tick ; mode stationnaire
 * (touche du mode, Z) : en l'air sans sauter, on garde son altitude (accroupi : on descend doucement), {@value #HOVER_H2} mB +
 * {@value #HOVER_FE} FE par tick. Le mouvement se calcule chez le client ({@code client/gear/GearClient}) ; le serveur preleve, crache
 * les flammes et tolere le joueur en l'air pendant la poussee et 3 s apres ({@code GearEvents} : memes garde-fous que les reacteurs
 * dorsaux). Reservoir ou batterie vide : la poussee s'arrete. Protection 2, jamais casse.
 */
public class HydrogenJetpackItem extends ArmorItem implements PoweredItem {

    public static final String ID = "hydrogen_jetpack", HOVER = "Hover";
    public static final long CAPACITY = 500_000, HYDROGEN = 24_000;
    public static final long THRUST_H2 = 8, THRUST_FE = 25, HOVER_H2 = 4, HOVER_FE = 12;
    public static final int ARMOR = 2;

    public HydrogenJetpackItem(Properties properties) {
        super(MATERIAL, Type.CHESTPLATE, properties.stacksTo(1).rarity(Rarity.UNCOMMON));
    }

    @Override
    public long energyCapacity() {
        return CAPACITY;
    }

    @Override
    public long hydrogenCapacity() {
        return HYDROGEN;
    }

    public static boolean is(ItemStack stack) {
        return stack.getItem() instanceof HydrogenJetpackItem;
    }

    /** Jetpack porte (case du plastron) ; vide sinon. */
    public static ItemStack worn(Player player) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        return is(chest) ? chest : ItemStack.EMPTY;
    }

    public static boolean hover(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        return tag != null && tag.getBoolean(HOVER);
    }

    public static void setHover(ItemStack stack, boolean on) {
        if (on) {
            stack.getOrCreateTag().putBoolean(HOVER, true);
        } else if (stack.getTag() != null) {
            stack.getTag().remove(HOVER);
        }
    }

    /** Assez d'hydrogene et d'energie pour un tick de poussee (ou de vol stationnaire). */
    public static boolean fueled(ItemStack stack, boolean hovering) {
        return is(stack) && ElectricCharge.hydrogen(stack) >= (hovering ? HOVER_H2 : THRUST_H2)
               && ElectricCharge.energy(stack) >= (hovering ? HOVER_FE : THRUST_FE);
    }

    /** Un tick de poussee (ou de vol stationnaire) : faux, et rien preleve, si le reservoir ou la batterie ne suffit pas. */
    public static boolean burn(ItemStack stack, boolean hovering) {
        return is(stack) && ElectricCharge.burn(stack, hovering ? HOVER_H2 : THRUST_H2, hovering ? HOVER_FE : THRUST_FE);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> fr.pioneers.client.gear.ElectricClient.armor(ID, consumer));
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return Pioneers.MODID + ":textures/models/armor/" + ID + "_atlas.png";
    }

    @Override
    public ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt) {
        return ElectricCharge.provider(stack);
    }

    @Override
    public boolean overrideOtherStackedOnMe(@NotNull ItemStack self, @NotNull ItemStack other, @NotNull Slot slot, @NotNull ClickAction action,
          @NotNull Player player, @NotNull SlotAccess cursor) {
        return ElectricCharge.capsuleOnto(self, other, action, player, cursor);
    }

    @Override
    public boolean isDamageable(ItemStack stack) {
        return false;
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
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return true;
    }

    /** Barre : l'hydrogene (la batterie dure bien plus longtemps ; elle est dans l'infobulle et le HUD). */
    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        return Math.round(13F * ElectricCharge.hydrogenRatio(stack));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return ElectricCharge.energy(stack) <= 0 ? 0xFF5050 : ElectricCharge.HYDROGEN_BAR;
    }

    @Override
    public boolean shouldCauseReequipAnimation(ItemStack oldStack, ItemStack newStack, boolean slotChanged) {
        return slotChanged || oldStack.getItem() != newStack.getItem();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        GearTips.protection(tips, ARMOR, ARMOR, 0);
        tips.spec("electric.pioneers.jetpack.thrust", THRUST_H2 * 20, THRUST_FE * 20);
        tips.spec("electric.pioneers.jetpack.hover", HOVER_H2 * 20, HOVER_FE * 20);
        ElectricCharge.tooltip(stack, tooltip);
        GearTips.rank(tips, GearTips.MK1);
        tips.state(hover(stack) ? "electric.pioneers.jetpack.mode.hover" : "electric.pioneers.jetpack.mode.normal");
        tips.hint("electric.pioneers.jetpack.hint");
    }

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(@NotNull Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(@NotNull Type type) {
            return ARMOR;
        }

        @Override
        public int getEnchantmentValue() {
            return 0;
        }

        @NotNull
        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_IRON;
        }

        @NotNull
        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.EMPTY;
        }

        @NotNull
        @Override
        public String getName() {
            return Pioneers.MODID + ":" + ID;
        }

        @Override
        public float getToughness() {
            return 0;
        }

        @Override
        public float getKnockbackResistance() {
            return 0;
        }
    };
}
