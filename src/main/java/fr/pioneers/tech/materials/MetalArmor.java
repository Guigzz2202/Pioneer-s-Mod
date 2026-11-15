package fr.pioneers.tech.materials;

import fr.pioneers.Pioneers;
import fr.pioneers.equipment.armor.client.ArmorModels;
import fr.pioneers.core.registry.PiCoreRegistries;
import fr.pioneers.core.tooltip.GearTips;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.tags.ItemTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.level.Level;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Armures de metal forgees (1.0, lot 4) : une armure par metal logique ({@link GeneratedArmor}, ecrit par {@code tools/gc/materials/armor.py}),
 * forgee au poste de metallurgie. Elles remplacent les armures vanilla de metal.
 * <ul>
 *     <li>Or (tres renforce) : reflechit la chaleur et les rayonnements, -15 % de brulures et de dose par piece portee ; les piglins
 *     restent neutres.</li>
 *     <li>Nitinol : alliage a memoire de forme, 1 point de durabilite repare toutes les 10 s.</li>
 * </ul>
 */
public final class MetalArmor {

    private MetalArmor() {
    }

    /** Part de brulure et de dose arretee par chaque piece d'or. */
    public static final float GOLD_SHIELD = 0.15F;
    private static final int REPAIR_EVERY = 200;
    /** Durabilite de base par type (comme Minecraft) : bottes, jambieres, plastron, casque. */
    private static final int[] HEALTH = {13, 15, 16, 11};

    private static final Map<String, RegistryObject<Item>> ITEMS = new LinkedHashMap<>();

    static {
        for (GeneratedArmor.Def def : GeneratedArmor.ARMORS) {
            ArmorMaterial material = material(def);
            for (ArmorItem.Type type : new ArmorItem.Type[]{ArmorItem.Type.HELMET, ArmorItem.Type.CHESTPLATE, ArmorItem.Type.LEGGINGS,
                  ArmorItem.Type.BOOTS}) {
                String id = def.id() + "_" + type.getName();
                ITEMS.put(id, PiCoreRegistries.ITEMS.register(id, () -> new Piece(def, material, type, new Item.Properties())));
            }
        }
    }

    /** Force le chargement de la classe (enregistrement). */
    public static void init() {
    }

    /** Armures (onglet Equipement), dans l'ordre des fiches puis casque, plastron, jambieres, bottes. */
    public static List<RegistryObject<Item>> items() {
        return new ArrayList<>(ITEMS.values());
    }

    private static ArmorMaterial material(GeneratedArmor.Def def) {
        return new ArmorMaterial() {
            @Override
            public int getDurabilityForType(@NotNull ArmorItem.Type type) {
                return HEALTH[type.getSlot().getIndex()] * def.durability();
            }

            @Override
            public int getDefenseForType(@NotNull ArmorItem.Type type) {
                return switch (type) {
                    case HELMET -> def.defense()[0];
                    case CHESTPLATE -> def.defense()[1];
                    case LEGGINGS -> def.defense()[2];
                    case BOOTS -> def.defense()[3];
                };
            }

            @Override
            public int getEnchantmentValue() {
                return def.enchant();
            }

            @NotNull
            @Override
            public SoundEvent getEquipSound() {
                return "gold".equals(def.id()) ? SoundEvents.ARMOR_EQUIP_GOLD : SoundEvents.ARMOR_EQUIP_IRON;
            }

            @NotNull
            @Override
            public Ingredient getRepairIngredient() {
                String repair = def.repair();
                if (repair.startsWith("#")) {
                    return Ingredient.of(ItemTags.create(ResourceLocation.parse(repair.substring(1))));
                }
                Item item = ForgeRegistries.ITEMS.getValue(ResourceLocation.parse(repair));
                return item == null ? Ingredient.EMPTY : Ingredient.of(item);
            }

            @NotNull
            @Override
            public String getName() {
                return Pioneers.MODID + ":" + def.id();
            }

            @Override
            public float getToughness() {
                return def.toughness();
            }

            @Override
            public float getKnockbackResistance() {
                return def.knockback();
            }
        };
    }

    /** Nombre de pieces d'armure d'or portees (0..4). */
    public static int goldPieces(LivingEntity entity) {
        int n = 0;
        for (ItemStack stack : entity.getArmorSlots()) {
            if (stack.getItem() instanceof Piece piece && "gold".equals(piece.def.id())) {
                n++;
            }
        }
        return n;
    }

    /** Facteur de dose de radiation (ou de brulure) apres l'armure d'or : 1 sans or, 0,4 avec les quatre pieces. */
    public static double goldFactor(LivingEntity entity) {
        return 1 - GOLD_SHIELD * goldPieces(entity);
    }

    /** Piece d'armure de metal. */
    public static class Piece extends ArmorItem {

        final GeneratedArmor.Def def;

        public Piece(GeneratedArmor.Def def, ArmorMaterial material, Type type, Properties properties) {
            super(material, type, properties);
            this.def = def;
        }

        /** 0.19.15 : modele 3D porte (tools/gc/core/armor3d.py). */
        @Override
        public void initializeClient(Consumer<IClientItemExtensions> consumer) {
            ArmorModels.extensions(() -> def.id(), consumer);
        }

        @Override
        public String getArmorTexture(ItemStack stack, Entity entity, EquipmentSlot slot, String type) {
            return Pioneers.MODID + ":textures/models/armor/" + def.id() + "_atlas.png";
        }

        @Override
        public boolean makesPiglinsNeutral(ItemStack stack, LivingEntity wearer) {
            return "gold".equals(def.id());
        }

        @Override
        public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
            //1.0.4-beta (lot 28) : format commun des equipements (J:core/tooltip/GearTips) : materiau, protection de la piece et de
            //l'ensemble, tier du metal ; la durabilite vient de l'infobulle commune (ToolProfiles.ToolClientEvents). Avant : « Armure forgee · n points »
            Tooltips tips = Tooltips.of(tooltip);
            GeneratedMaterials.TierDef tier = PiTiers.def(def.id());
            GearTips.material(tips, def.id(), tier == null ? null : tier.profile());
            GearTips.armor(tips, this);
            if (tier != null) {
                GearTips.rank(tips, tier.tier());
            }
            if ("gold".equals(def.id())) {
                tips.note("tooltip.pioneers.armor.gold");
            } else if ("nitinol".equals(def.id())) {
                tips.good("tooltip.pioneers.armor.nitinol");
            }
        }
    }

    @Mod.EventBusSubscriber(modid = Pioneers.MODID)
    public static final class Events {

        private Events() {
        }

        /** L'or reflechit la chaleur : moins de brulures (feu, lave, magma). */
        @SubscribeEvent
        public static void hurt(LivingHurtEvent event) {
            if (event.getSource().is(DamageTypeTags.IS_FIRE)) {
                int n = goldPieces(event.getEntity());
                if (n > 0) {
                    event.setAmount(event.getAmount() * (1 - GOLD_SHIELD * n));
                }
            }
        }

        /** Le nitinol se repare tout seul. */
        @SubscribeEvent
        public static void repair(TickEvent.PlayerTickEvent event) {
            Player player = event.player;
            if (event.phase != TickEvent.Phase.END || player.level().isClientSide || player.tickCount % REPAIR_EVERY != 0) {
                return;
            }
            for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
                ItemStack stack = player.getItemBySlot(slot);
                if (stack.isDamaged() && stack.getItem() instanceof Piece piece && "nitinol".equals(piece.def.id())) {
                    stack.setDamageValue(stack.getDamageValue() - 1);
                }
            }
        }
    }
}
