package fr.pioneers.gear;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import fr.pioneers.Pioneers;
import fr.pioneers.api.environment.ISpaceSuitItem;
import fr.pioneers.client.armor.ArmorModels;
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
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ForgeMod;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 0.20.15 : piece d'armure modulaire. Protection et robustesse du tier (plus le blindage composite), modele 3D porte et couche lumineuse
 * ({@code tools/gc/core/armor3d.py}), energie FE, modules par emplacement. Compte comme une combinaison spatiale du tier
 * (Graviton 1 ... Singularite 4) : avec le recycleur d'air au casque, on respire dans le vide.
 * <p>
 * Effets des modules : {@link GearEvents}. Incassable (l'energie remplace la durabilite).
 */
public class ModularArmorItem extends ArmorItem implements ModularGear, ISpaceSuitItem {

    private static final UUID[] ARMOR_UUID = {UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b01"),
          UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b02"), UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b03"),
          UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b04")};
    private static final UUID SERVO_UUID = UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b11");
    private static final UUID STEP_UUID = UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b12");
    private static final UUID SWIM_UUID = UUID.fromString("5b0e8a3e-6d5c-4d0f-9a43-3f6f0a1c2b13");

    private final ModularTier tier;
    private final GearKind kind;

    public ModularArmorItem(ModularTier tier, Type type, Properties properties) {
        super(material(tier), type, properties.stacksTo(1).fireResistant().rarity(tier.rarity));
        this.tier = tier;
        this.kind = switch (type) {
            case HELMET -> GearKind.HELMET;
            case CHESTPLATE -> GearKind.CHESTPLATE;
            case LEGGINGS -> GearKind.LEGGINGS;
            case BOOTS -> GearKind.BOOTS;
        };
    }

    @Override
    public ModularTier tier() {
        return tier;
    }

    @Override
    public GearKind kind() {
        return kind;
    }

    @Override
    public int getSuitTier(ItemStack stack) {
        return tier.ordinal() + 1;
    }

    /** Id de l'armure (modele, atlas, couche lumineuse) : celui du tier. */
    public String armorId() {
        return tier.id();
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        ArmorModels.extensions(() -> tier.id(), consumer);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return Pioneers.MODID + ":textures/models/armor/" + tier.id() + "_atlas.png";
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

    /** Protection du tier + blindage ; vitesse (servomoteurs), marche (assistance) et nage (hydropropulseurs) aux jambieres. */
    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != getEquipmentSlot()) {
            return ImmutableMultimap.of();
        }
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        UUID id = ARMOR_UUID[kind.piece()];
        int plating = Modules.level(stack, GearModule.PLATING);
        b.put(Attributes.ARMOR, new AttributeModifier(id, "Armor modifier", tier.defense(kind.piece()) + plating, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ARMOR_TOUGHNESS, new AttributeModifier(id, "Armor toughness", tier.toughness + plating * 0.5F,
              AttributeModifier.Operation.ADDITION));
        if (tier.knockback > 0) {
            b.put(Attributes.KNOCKBACK_RESISTANCE, new AttributeModifier(id, "Armor knockback resistance", tier.knockback,
                  AttributeModifier.Operation.ADDITION));
        }
        if (kind == GearKind.LEGGINGS) {
            int servo = Modules.level(stack, GearModule.SERVO);
            if (servo > 0) {
                b.put(Attributes.MOVEMENT_SPEED, new AttributeModifier(SERVO_UUID, "Servo motors", 0.15 * servo, AttributeModifier.Operation.MULTIPLY_BASE));
            }
            if (Modules.level(stack, GearModule.STEP) > 0) {
                b.put(ForgeMod.STEP_HEIGHT_ADDITION.get(), new AttributeModifier(STEP_UUID, "Step assist", 0.5, AttributeModifier.Operation.ADDITION));
            }
            int hydro = Modules.level(stack, GearModule.HYDRO);
            if (hydro > 0) {
                b.put(ForgeMod.SWIM_SPEED.get(), new AttributeModifier(SWIM_UUID, "Hydro-thrusters", 0.6 * hydro, AttributeModifier.Operation.MULTIPLY_BASE));
            }
        }
        return b.build();
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        //1.0.4-beta (lot 28) : protection au format commun des equipements, puis les lignes communes du modulaire
        GearTips.armor(Tooltips.of(tooltip), this);
        Modules.tooltip(stack, tooltip);
    }

    private static ArmorMaterial material(ModularTier tier) {
        return new ArmorMaterial() {
            @Override
            public int getDurabilityForType(@NotNull Type type) {
                return 0;
            }

            @Override
            public int getDefenseForType(@NotNull Type type) {
                return switch (type) {
                    case HELMET -> tier.defense(0);
                    case CHESTPLATE -> tier.defense(1);
                    case LEGGINGS -> tier.defense(2);
                    case BOOTS -> tier.defense(3);
                };
            }

            @Override
            public int getEnchantmentValue() {
                return 0;
            }

            @NotNull
            @Override
            public SoundEvent getEquipSound() {
                return SoundEvents.ARMOR_EQUIP_NETHERITE;
            }

            @NotNull
            @Override
            public Ingredient getRepairIngredient() {
                return Ingredient.EMPTY;
            }

            @NotNull
            @Override
            public String getName() {
                return Pioneers.MODID + ":" + tier.id();
            }

            @Override
            public float getToughness() {
                return tier.toughness;
            }

            @Override
            public float getKnockbackResistance() {
                return tier.knockback;
            }
        };
    }
}
