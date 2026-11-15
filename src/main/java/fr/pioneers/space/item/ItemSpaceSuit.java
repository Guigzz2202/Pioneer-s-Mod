package fr.pioneers.space.item;

import fr.pioneers.Pioneers;
import fr.pioneers.api.environment.ISpaceSuitItem;
import fr.pioneers.equipment.armor.client.ArmorModels;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Combinaison spatiale (casque, plastron, jambieres, bottes), en quatre tiers debloques par la recherche :
 * <ol>
 *     <li>combinaison de base : le vide (Lune) ;</li>
 *     <li>combinaison thermique : chaleur et grand froid (Mars, Phobos, Deimos) ;</li>
 *     <li>combinaison anti-acide : acides et radiations (Venus et tout le reste du systeme) ;</li>
 *     <li>scaphandre atmospherique : la pression ecrasante et les vents des geantes gazeuses, ou ses propulseurs permettent de voler.</li>
 * </ol>
 * Le plastron porte la reserve d'oxygene ({@code mekanism:oxygen}), remplie par les reservoirs chimiques de Mekanism,
 * le diffuseur d'oxygene ou automatiquement dans une piece pressurisee.
 */
public class ItemSpaceSuit extends ArmorItem implements ISpaceSuitItem {

    public enum Tier implements ArmorMaterial {
        BASIC("space_suit", 1, 15, new int[]{1, 2, 3, 1}, 0, 12_000, Rarity.UNCOMMON),
        THERMAL("thermal_suit", 2, 25, new int[]{2, 4, 5, 2}, 1, 18_000, Rarity.RARE),
        ACID("acid_suit", 3, 35, new int[]{3, 6, 7, 3}, 2, 24_000, Rarity.EPIC),
        /** 0.10 : scaphandre atmospherique, pour descendre dans les geantes gazeuses (pression, vents, propulseurs dorsaux). */
        ATMOSPHERIC("atmospheric_suit", 4, 45, new int[]{3, 7, 8, 3}, 3, 36_000, Rarity.EPIC);

        private static final int[] DURABILITY = {13, 15, 16, 11};

        public final String id;
        public final int level;
        private final int durability;
        /** Protection : bottes, jambieres, plastron, casque. */
        private final int[] defense;
        private final float toughness;
        /** Reserve d'oxygene du plastron (mB). */
        public final long oxygen;
        public final Rarity rarity;

        Tier(String id, int level, int durability, int[] defense, float toughness, long oxygen, Rarity rarity) {
            this.id = id;
            this.level = level;
            this.durability = durability;
            this.defense = defense;
            this.toughness = toughness;
            this.oxygen = oxygen;
            this.rarity = rarity;
        }

        public static Tier byLevel(int level) {
            return values()[Mth.clamp(level, 1, values().length) - 1];
        }

        @Override
        public int getDurabilityForType(@NotNull Type type) {
            return DURABILITY[type.getSlot().getIndex()] * durability;
        }

        @Override
        public int getDefenseForType(@NotNull Type type) {
            return defense[type.getSlot().getIndex()];
        }

        @Override
        public int getEnchantmentValue() {
            return 10;
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
            return Pioneers.MODID + ":" + id;
        }

        @Override
        public float getToughness() {
            return toughness;
        }

        @Override
        public float getKnockbackResistance() {
            return 0;
        }
    }

    /** Oxygene consomme par seconde hors d'un air respirable (mB). */
    public static final long OXYGEN_PER_SECOND = 20;

    private final Tier tier;

    public ItemSpaceSuit(Tier tier, Type type) {
        super(tier, type, new Properties().stacksTo(1).rarity(tier.rarity));
        this.tier = tier;
    }

    public Tier getTier() {
        return tier;
    }

    @Override
    public int getSuitTier(ItemStack stack) {
        return tier.level;
    }

    public static String pieceId(Type type) {
        return switch (type) {
            case HELMET -> "helmet";
            case CHESTPLATE -> "chestplate";
            case LEGGINGS -> "leggings";
            case BOOTS -> "boots";
        };
    }

    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
        return Pioneers.MODID + ":textures/models/armor/" + tier.id + "_atlas.png";
    }

    /** 0.19.15 : modele 3D porte (tools/gc/core/armor3d.py). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        ArmorModels.extensions(() -> tier.id, consumer);
    }

    // ------------------------------------------------------------------
    // Reserve d'oxygene (plastron)
    // ------------------------------------------------------------------

    public boolean hasTank() {
        return getType() == Type.CHESTPLATE;
    }

    /** Cle NBT de la reserve d'oxygene (mB) du plastron. */
    private static final String OXYGEN_TAG = "oxygen";

    /**
     * Remplissage de la combinaison : coupe TEMPORAIREMENT (emancipation de Mekanism, voir docs/recettes-a-revoir.md). La reserve se vide
     * en respirant et ne se recharge plus, ni dans une piece pressurisee ni par un tuyau ; on respire toujours normalement dans une piece.
     */
    public static final boolean REFILL_ENABLED = false;

    public static long getOxygen(ItemStack stack) {
        if (!(stack.getItem() instanceof ItemSpaceSuit suit) || !suit.hasTank()) {
            return 0;
        }
        CompoundTag tag = stack.getTag();
        return tag == null ? 0 : Math.max(0, Math.min(tag.getLong(OXYGEN_TAG), suit.tier.oxygen));
    }

    private static void setOxygen(ItemStack stack, long amount) {
        stack.getOrCreateTag().putLong(OXYGEN_TAG, Math.max(0, amount));
    }

    public static long getOxygenCapacity(ItemStack stack) {
        return stack.getItem() instanceof ItemSpaceSuit suit && suit.hasTank() ? suit.tier.oxygen : 0;
    }

    /** Respire : retire l'oxygene d'une seconde. Faux si la reserve est vide. */
    public static boolean breathe(ItemStack stack) {
        if (getOxygenCapacity(stack) <= 0) {
            return false;
        }
        long use = Math.round(OXYGEN_PER_SECOND * PiCommonConfig.get(PiCommonConfig.OXYGEN_CONSUMPTION, 1.0));
        if (use <= 0) {
            return true;
        }
        long oxygen = getOxygen(stack);
        if (oxygen <= 0) {
            return false;
        }
        setOxygen(stack, oxygen - use);
        return true;
    }

    /** Remplit la reserve (piece pressurisee, diffuseur). Retourne la quantite acceptee (0 tant que le remplissage est coupe). */
    public static long refill(ItemStack stack, long amount) {
        long room = getOxygenCapacity(stack) - getOxygen(stack);
        if (!REFILL_ENABLED || room <= 0 || amount <= 0) {
            return 0;
        }
        long accepted = Math.min(room, amount);
        setOxygen(stack, getOxygen(stack) + accepted);
        return accepted;
    }

    @Override
    public boolean isBarVisible(@NotNull ItemStack stack) {
        return hasTank() || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(@NotNull ItemStack stack) {
        if (!hasTank()) {
            return super.getBarWidth(stack);
        }
        return Math.round(13F * getOxygen(stack) / Math.max(1, getOxygenCapacity(stack)));
    }

    @Override
    public int getBarColor(@NotNull ItemStack stack) {
        return hasTank() ? 0x6FD8FF : super.getBarColor(stack);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : milieux supportes, protection, oxygene (reservoir),
        //puis « Tier : niveau n (nom) » dans la couleur du rang (echelle propre aux combinaisons : niveau 1 vert ... 4 bordeaux)
        Tooltips tips = Tooltips.of(tooltip);
        String key = tier.name().toLowerCase(Locale.ROOT);
        tips.stat("tooltip.pioneers.gear.suit_env", Component.translatable("tooltip.pioneers.gear.suit_env." + key));
        GearTips.armor(tips, this);
        if (hasTank()) {
            GearTips.tank(tips, "tooltip.pioneers.gear.oxygen", getOxygen(stack), tier.oxygen, "mb");
        }
        GearTips.named(tips, GearTips.inRank(Component.translatable("tooltip.pioneers.gear.suit_level", tier.level,
              Component.translatable("tooltip.pioneers.gear.suit_name." + key)), GearTips.MK1 + tier.level - 1));
        tips.hint("suit.pioneers.set_hint");
    }

    /** Tier de la combinaison complete portee (le plus faible des quatre pieces ; 0 si une piece manque). */
    public static int wornTier(LivingEntity entity) {
        int tier = Integer.MAX_VALUE;
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack stack = entity.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ISpaceSuitItem suit)) {
                return 0;
            }
            tier = Math.min(tier, suit.getSuitTier(stack));
        }
        return tier;
    }
}
