package fr.pioneers.tech.materials;

import fr.pioneers.Pioneers;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.crustacean.Crustacean;
import fr.pioneers.farm.crustacean.PiCrustaceans;
import java.util.UUID;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.GlowSquid;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractFish;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.entity.animal.Chicken;
import net.minecraft.world.entity.animal.Cow;
import net.minecraft.world.entity.animal.Parrot;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.entity.animal.Sheep;
import net.minecraft.world.entity.animal.Squid;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.axolotl.Axolotl;
import net.minecraft.world.entity.animal.frog.Frog;
import net.minecraft.world.entity.animal.goat.Goat;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.ItemAttributeModifierEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.ItemTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 (Guillaume : « le couteau fait un peu de degats, plus de degats sur les animaux passifs et a une proba de donner plus de loots
 * secondaires ») : les couteaux (tag {@code pioneers:tools/knives}, outils d'atelier de tous les metaux) deviennent une petite arme.
 * <ul>
 * <li>Main principale : degats modestes selon le materiau (1 + bonus du materiau / 2, soit 2,4 pour le silex a 5 pour l'osmiridium, poing
 * compris) et deux coups par seconde ; meme identifiants que les armes vanilla pour que l'infobulle affiche les valeurs totales.</li>
 * <li>Animaux paisibles (animaux non hostiles, poissons et autres animaux aquatiques, crustaces) : degats x 1,75.</li>
 * <li>Animal tue au couteau : chance (20 % + 8 % par tier, 20 a 60 %) d'un butin secondaire de plus selon l'espece (cuir, plumes, laine de
 * sa couleur ou ficelle s'il est tondu, peau ou patte de lapin, encre, os, poudre d'os pour un poisson, coquillage pour un crustace) ; un
 * second tirage peut en donner un autre.</li>
 * <li>Usure : un point par coup porte.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class Knives {

    private Knives() {
    }

    public static final TagKey<Item> KNIVES = ItemTags.create(Pioneers.rl("tools/knives"));
    /** Identifiants des modificateurs de base des armes vanilla (Item.BASE_ATTACK_DAMAGE_UUID / BASE_ATTACK_SPEED_UUID). */
    private static final UUID DAMAGE_ID = UUID.fromString("CB3F55D3-645C-4F38-A497-9C13A33DB5CF");
    private static final UUID SPEED_ID = UUID.fromString("FA233E1C-4180-4865-B01B-BCCE9785ACA3");
    /** Multiplicateur des degats contre les animaux paisibles. */
    public static final float PASSIVE_BONUS = 1.75F;

    /** Degats ajoutes au poing (1) par un couteau de ce materiau. */
    public static float damage(@Nullable GeneratedMaterials.TierDef def) {
        return 1.0F + 0.5F * (def == null ? 0.8F : def.damage());
    }

    /** Chance d'un butin secondaire de plus (selon le tier du materiau). */
    public static float lootChance(@Nullable GeneratedMaterials.TierDef def) {
        return 0.2F + 0.08F * (def == null ? 0 : def.tier());
    }

    /** Animal paisible : animal non hostile, animal aquatique (poissons, calmars...), crustace. */
    public static boolean passive(LivingEntity e) {
        return !(e instanceof Enemy) && (e instanceof Animal || e instanceof WaterAnimal || e instanceof Crustacean);
    }

    @SubscribeEvent
    public static void attributes(ItemAttributeModifierEvent event) {
        if (event.getSlotType() != EquipmentSlot.MAINHAND || !event.getItemStack().is(KNIVES)) {
            return;
        }
        GeneratedMaterials.TierDef def = ToolProfiles.of(event.getItemStack().getItem());
        event.addModifier(Attributes.ATTACK_DAMAGE, new AttributeModifier(DAMAGE_ID, "Knife modifier", damage(def), AttributeModifier.Operation.ADDITION));
        event.addModifier(Attributes.ATTACK_SPEED, new AttributeModifier(SPEED_ID, "Knife modifier", -2.0 + (def == null ? 0 : def.swing()),
              AttributeModifier.Operation.ADDITION));
    }

    /** Coup de couteau : plus fort contre un animal paisible ; le couteau s'use d'un point. */
    @SubscribeEvent
    public static void hurt(LivingHurtEvent event) {
        if (!(event.getSource().getDirectEntity() instanceof Player player) || event.getSource().getEntity() != player || player.level().isClientSide) {
            return;
        }
        ItemStack knife = player.getMainHandItem();
        if (!knife.is(KNIVES)) {
            return;
        }
        if (passive(event.getEntity())) {
            event.setAmount(event.getAmount() * PASSIVE_BONUS);
        }
        if (!player.getAbilities().instabuild) {
            knife.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(EquipmentSlot.MAINHAND));
        }
    }

    /** Animal tue au couteau : chance de butin secondaire en plus. */
    @SubscribeEvent
    public static void drops(LivingDropsEvent event) {
        LivingEntity e = event.getEntity();
        if (e.isBaby() || !passive(e) || !(event.getSource().getEntity() instanceof Player player) || event.getSource().getDirectEntity() != player) {
            return;
        }
        ItemStack knife = player.getMainHandItem();
        if (!knife.is(KNIVES)) {
            return;
        }
        float chance = lootChance(ToolProfiles.of(knife.getItem()));
        RandomSource random = e.getRandom();
        for (int roll = 0; roll < 2 && random.nextFloat() < chance; roll++) {
            ItemStack extra = secondary(e, random);
            if (extra.isEmpty()) {
                return;
            }
            ItemEntity item = new ItemEntity(e.level(), e.getX(), e.getY() + 0.3, e.getZ(), extra);
            item.setDefaultPickUpDelay();
            event.getDrops().add(item);
        }
    }

    /** Butin secondaire selon l'espece (vide pour les toutes petites betes). */
    static ItemStack secondary(LivingEntity e, RandomSource random) {
        if (e instanceof Sheep sheep) {
            if (sheep.isSheared()) {
                return new ItemStack(Items.STRING);
            }
            Item wool = BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:" + sheep.getColor().getName() + "_wool"));
            return new ItemStack(wool == Items.AIR ? Items.WHITE_WOOL : wool);
        }
        if (e instanceof Cow || e instanceof AbstractHorse || e instanceof Goat) {
            return new ItemStack(Items.LEATHER);
        }
        if (e instanceof Chicken || e instanceof Parrot) {
            return new ItemStack(Items.FEATHER);
        }
        if (e instanceof Rabbit) {
            return new ItemStack(random.nextInt(4) == 0 ? Items.RABBIT_FOOT : Items.RABBIT_HIDE);
        }
        if (e instanceof GlowSquid) {
            return new ItemStack(Items.GLOW_INK_SAC);
        }
        if (e instanceof Squid) {
            return new ItemStack(Items.INK_SAC);
        }
        if (e instanceof AbstractFish) {
            return new ItemStack(Items.BONE_MEAL);
        }
        if (e instanceof Crustacean) {
            return new ItemStack(PiCrustaceans.SEASHELL.get());
        }
        if (e instanceof Bee || e instanceof Frog || e instanceof Axolotl) {
            return ItemStack.EMPTY;
        }
        return e instanceof Animal ? new ItemStack(Items.BONE) : ItemStack.EMPTY;
    }

    /** Infobulle : effets du couteau (avant les valeurs d'attaque). */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class ClientEvents {

        private ClientEvents() {
        }

        @SubscribeEvent
        public static void tooltip(ItemTooltipEvent event) {
            ItemStack stack = event.getItemStack();
            if (!stack.is(KNIVES)) {
                return;
            }
            GeneratedMaterials.TierDef def = ToolProfiles.of(stack.getItem());
            Tooltips tips = Tooltips.event(event.getToolTip());
            tips.good("tooltip.pioneers.knife.passive", Math.round((PASSIVE_BONUS - 1) * 100));
            tips.good("tooltip.pioneers.knife.loot", Math.round(lootChance(def) * 100));
        }
    }
}
