package fr.pioneers.gear.electric;

import fr.pioneers.Pioneers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.3-beta (lot 9) : effets de l'armure Voltaique (cote serveur, toutes les {@value #PERIOD} ticks, couts x {@value #PERIOD}) :
 * <ul>
 *     <li>casque : vision de nuit quand il fait sombre a hauteur des yeux (lumiere &lt; {@value #DARK}), {@value #NIGHT_VISION_FE} FE/t ;</li>
 *     <li>plastron : blindage sous tension (la protection haute de {@link VoltaicArmorItem}), {@value #CHEST_FE} FE/t ;</li>
 *     <li>jambieres : +10 % de vitesse (modificateur de l'objet) ; {@value #LEGS_FE} FE/t en marchant ;</li>
 *     <li>bottes : la moitie des degats de chute evitee, {@value #FALL_FE} FE par point evite.</li>
 * </ul>
 * A sec, rien ne marche et la piece revient a la protection de l'acier. Le jetpack et le lance-flammes ont leur logique a part
 * ({@code GearEvents}, {@link FlamethrowerItem}).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ElectricEvents {

    private ElectricEvents() {
    }

    public static final int PERIOD = 10, DARK = 8;
    public static final long NIGHT_VISION_FE = 5, CHEST_FE = 1, LEGS_FE = 3, FALL_FE = 100;
    private static final String WALK_TAG = "pioneers_voltaic_walk";

    @SubscribeEvent
    public static void playerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % PERIOD != 0) {
            return;
        }
        tick(player);
    }

    /** Un passage (toutes les {@value #PERIOD} ticks) : energie prelevee et effets ; public pour les GameTests. */
    public static void tick(ServerPlayer player) {
        ItemStack head = player.getItemBySlot(EquipmentSlot.HEAD), chest = player.getItemBySlot(EquipmentSlot.CHEST);
        ItemStack legs = player.getItemBySlot(EquipmentSlot.LEGS);
        CompoundTag data = player.getPersistentData();
        if (!(legs.getItem() instanceof VoltaicArmorItem)) {
            data.remove(WALK_TAG);
        }
        if (head.getItem() instanceof VoltaicArmorItem && VoltaicArmorItem.powered(head)) {
            BlockPos eye = BlockPos.containing(player.getEyePosition());
            if (player.level().getMaxLocalRawBrightness(eye) < DARK && ElectricCharge.use(head, NIGHT_VISION_FE * PERIOD)) {
                player.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 260, 0, true, false, false));
            }
        }
        if (chest.getItem() instanceof VoltaicArmorItem) {
            ElectricCharge.drain(chest, CHEST_FE * PERIOD);
        }
        if (legs.getItem() instanceof VoltaicArmorItem) {
            float walked = data.contains(WALK_TAG) ? Math.max(0, Math.min(20, player.walkDist - data.getFloat(WALK_TAG))) : 0;
            data.putFloat(WALK_TAG, player.walkDist);
            if (walked > 0.05F) {
                ElectricCharge.drain(legs, LEGS_FE * PERIOD);
            }
        }
    }

    /** Bottes : la moitie des degats de chute evitee tant que l'energie suit ({@value #FALL_FE} FE par point evite). */
    @SubscribeEvent
    public static void fall(LivingFallEvent event) {
        if (!(event.getEntity() instanceof Player player) || player.level().isClientSide) {
            return;
        }
        ItemStack feet = player.getItemBySlot(EquipmentSlot.FEET);
        if (!(feet.getItem() instanceof VoltaicArmorItem) || !VoltaicArmorItem.powered(feet)) {
            return;
        }
        event.setDamageMultiplier(event.getDamageMultiplier() * fallFactor(feet, event.getDistance(), event.getDamageMultiplier()));
    }

    /** Facteur des degats de chute (1 : rien evite) et energie prelevee ; public pour les GameTests. */
    public static float fallFactor(ItemStack feet, float distance, float multiplier) {
        float points = Math.max(0, distance - 3) * multiplier;
        if (points <= 0) {
            return 1;
        }
        float wanted = points * 0.5F;
        float saved = Math.min(wanted, ElectricCharge.energy(feet) / (float) FALL_FE);
        ElectricCharge.drain(feet, (long) Math.ceil(saved) * FALL_FE);
        return (points - saved) / points;
    }
}
