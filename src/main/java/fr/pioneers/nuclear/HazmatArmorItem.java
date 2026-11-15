package fr.pioneers.nuclear;

import fr.pioneers.Pioneers;
import fr.pioneers.client.armor.ArmorModels;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Combinaison anti-radiation (0.17.0) : plomb, caoutchouc, visiere. Les quatre pieces portees arretent 95 % de la dose. */
public class HazmatArmorItem extends ArmorItem {

    /** Part de la dose qui passe une combinaison complete. */
    public static final double LEAK = 0.05;

    public static final ArmorMaterial MATERIAL = new ArmorMaterial() {
        private final int[] durability = {13, 15, 16, 11};
        private final int[] defense = {1, 2, 3, 1};

        @Override
        public int getDurabilityForType(@NotNull Type type) {
            return durability[type.getSlot().getIndex()] * 12;
        }

        @Override
        public int getDefenseForType(@NotNull Type type) {
            return defense[type.getSlot().getIndex()];
        }

        @Override
        public int getEnchantmentValue() {
            return 6;
        }

        @NotNull
        @Override
        public SoundEvent getEquipSound() {
            return SoundEvents.ARMOR_EQUIP_LEATHER;
        }

        @NotNull
        @Override
        public Ingredient getRepairIngredient() {
            return Ingredient.of(ItemTags.create(ResourceLocation.fromNamespaceAndPath("forge", "plates/lead")));
        }

        @NotNull
        @Override
        public String getName() {
            return Pioneers.MODID + ":hazmat";
        }

        @Override
        public float getToughness() {
            return 0;
        }

        @Override
        public float getKnockbackResistance() {
            return 0.05F;
        }
    };

    public HazmatArmorItem(Type type, Properties properties) {
        super(MATERIAL, type, properties);
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return Pioneers.MODID + ":textures/models/armor/hazmat_atlas.png";
    }

    /** 0.19.15 : modele 3D porte (tools/gc/core/armor3d.py). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        ArmorModels.extensions(() -> "hazmat", consumer);
    }

    /** Les quatre pieces sont-elles portees ? */
    public static boolean fullSet(LivingEntity entity) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            if (!(entity.getItemBySlot(slot).getItem() instanceof HazmatArmorItem)) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : protection, effet (atout), tier MKI (fait a l'age MKI)
        Tooltips tips = Tooltips.of(tooltip);
        GearTips.armor(tips, this);
        tips.good("tooltip.pioneers.hazmat");
        GearTips.rank(tips, GearTips.MK1);
    }
}
