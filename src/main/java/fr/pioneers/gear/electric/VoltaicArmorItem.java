package fr.pioneers.gear.electric;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
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
 * 1.0.3-beta (lot 9) : piece de l'armure electrique Voltaique (age MKI / MKII, avant Graviton ; demande de Guillaume : « un set electrique
 * simple avant Graviton »). Sans module. Protection selon la charge :
 * <ul>
 *     <li>sous tension (au moins 1 FE) : {@link #POWERED} = 3 / 8 / 7 / 3 (21), robustesse 2,5, recul 5 % : au-dessus de l'acier trempe (20,
 *     robustesse 2) et du titane (20, 2,5), sous Graviton avec son blindage ;</li>
 *     <li>a sec : {@link #UNPOWERED} = 3 / 7 / 6 / 2 (18, l'acier), robustesse 1, et aucun effet. Jamais cassee (pas de durabilite).</li>
 * </ul>
 * Effets ({@link ElectricEvents}) : casque = vision de nuit dans le noir ; plastron = blindage sous tension ; jambieres = +10 % de vitesse ;
 * bottes = chute amortie de moitie. 250 000 FE par piece, recharge 2 500 FE/t (buffer d'energie, etabli de modulation).
 * Modele 3D porte : {@code tools/gc/core/armor3d.py} (famille {@code voltaic}).
 */
public class VoltaicArmorItem extends ArmorItem implements PoweredItem {

    public static final long CAPACITY = 250_000;
    public static final int[] POWERED = {3, 8, 7, 3}, UNPOWERED = {3, 7, 6, 2};
    public static final float TOUGHNESS = 2.5F, TOUGHNESS_UNPOWERED = 1.0F, KNOCKBACK = 0.05F;
    /** Jambieres sous tension : +10 % de vitesse de marche. */
    public static final double SPEED_BONUS = 0.10;
    public static final String ID = "voltaic";
    private static final UUID[] ARMOR_UUID = {UUID.fromString("6c1f9b4f-7e6d-4e10-8b54-4a7f1b2d3c01"),
          UUID.fromString("6c1f9b4f-7e6d-4e10-8b54-4a7f1b2d3c02"), UUID.fromString("6c1f9b4f-7e6d-4e10-8b54-4a7f1b2d3c03"),
          UUID.fromString("6c1f9b4f-7e6d-4e10-8b54-4a7f1b2d3c04")};
    private static final UUID SPEED_UUID = UUID.fromString("6c1f9b4f-7e6d-4e10-8b54-4a7f1b2d3c11");

    private final int piece;

    public VoltaicArmorItem(Type type, Properties properties) {
        super(MATERIAL, type, properties.stacksTo(1).rarity(Rarity.UNCOMMON));
        this.piece = switch (type) {
            case HELMET -> 0;
            case CHESTPLATE -> 1;
            case LEGGINGS -> 2;
            case BOOTS -> 3;
        };
    }

    /** 0 casque, 1 plastron, 2 jambieres, 3 bottes. */
    public int piece() {
        return piece;
    }

    @Override
    public long energyCapacity() {
        return CAPACITY;
    }

    /** Sous tension : au moins 1 FE dans la piece. */
    public static boolean powered(ItemStack stack) {
        return stack.getItem() instanceof VoltaicArmorItem && ElectricCharge.energy(stack) > 0;
    }

    /** Protection de la piece selon sa charge. */
    public int defense(ItemStack stack) {
        return (powered(stack) ? POWERED : UNPOWERED)[piece];
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
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != getEquipmentSlot()) {
            return ImmutableMultimap.of();
        }
        boolean on = powered(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        UUID id = ARMOR_UUID[piece];
        b.put(Attributes.ARMOR, new AttributeModifier(id, "Armor modifier", defense(stack), AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, "Armor toughness", on ? TOUGHNESS : TOUGHNESS_UNPOWERED,
              AttributeModifier.Operation.ADDITION));
        if (on) {
            b.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, "Armor knockback resistance", KNOCKBACK, AttributeModifier.Operation.ADDITION));
            if (piece == 2) {
                b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SPEED_UUID, "Voltaic servos", SPEED_BONUS, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        return b.build();
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
        //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : protection sous tension / a sec (piece, ensemble),
        //energie, effet de la piece en atout, tier MKI
        Tooltips tips = Tooltips.of(tooltip);
        GearTips.poweredProtection(tips, POWERED[piece], UNPOWERED[piece], POWERED[0] + POWERED[1] + POWERED[2] + POWERED[3],
              UNPOWERED[0] + UNPOWERED[1] + UNPOWERED[2] + UNPOWERED[3]);
        ElectricCharge.tooltip(stack, tooltip);
        GearTips.rank(tips, GearTips.MK1);
        tips.good("electric.pioneers.armor.effect." + piece);
        if (!powered(stack)) {
            tips.warn("electric.pioneers.armor.flat");
        }
    }

    private static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        @Override
        public int getDurabilityForType(@NotNull Type type) {
            return 0;
        }

        @Override
        public int getDefenseForType(@NotNull Type type) {
            return switch (type) {
                case HELMET -> UNPOWERED[0];
                case CHESTPLATE -> UNPOWERED[1];
                case LEGGINGS -> UNPOWERED[2];
                case BOOTS -> UNPOWERED[3];
            };
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
            return TOUGHNESS_UNPOWERED;
        }

        @Override
        public float getKnockbackResistance() {
            return 0;
        }
    };
}
