package fr.pioneers.space.giant;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.equipment.gear.GearEvents;
import fr.pioneers.space.item.ItemSpaceSuit;
import fr.pioneers.core.registries.PiEntityTypes;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.environment.SurvivalHandler;
import fr.pioneers.space.event.SpaceEventType;
import fr.pioneers.space.event.SpaceEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingFallEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Geantes gazeuses, cote serveur :
 * <ul>
 *     <li>apparition des poches de gaz autour des joueurs (selon la couche et la planete) ;</li>
 *     <li>combinaison atmospherique : vol plane et propulse (pas de degats de chute ; le vol lui-meme est gere par le client) ;</li>
 *     <li>pression ecrasante sous la couche des orages, et eclairs dans les orages.</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class GiantEvents {

    private GiantEvents() {
    }

    /** Altitudes de la pression ecrasante (degats meme en combinaison atmospherique). */
    public static final int CRUSH_Y = 60, DEEP_CRUSH_Y = 25;
    /** Nombre de poches visees autour d'un joueur. */
    private static final String NBT_FLIGHT = "pioneers_suit_flight";

    /** Vol en combinaison atmospherique possible : geante gazeuse et combinaison complete de tier 4. */
    public static boolean canSuitFly(Player player) {
        return PiDimensions.isGasGiant(player.level()) && ItemSpaceSuit.wornTier(player) >= 4 && !player.isSpectator();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        boolean fly = canSuitFly(player);
        //Le serveur tolere le vol propulse (sinon : expulsion « le vol n'est pas autorise ») ; le client empeche le vol libre
        CompoundTag data = player.getPersistentData();
        if (fly && !player.getAbilities().mayfly) {
            player.getAbilities().mayfly = true;
            player.onUpdateAbilities();
            data.putBoolean(NBT_FLIGHT, true);
        } else if (!fly && data.getBoolean(NBT_FLIGHT)) {
            data.remove(NBT_FLIGHT);
            if (!player.isCreative() && !GearEvents.allowsAir(player)) {
                player.getAbilities().mayfly = false;
                player.getAbilities().flying = false;
                player.onUpdateAbilities();
            }
        }
        if (fly && player.getAbilities().flying && !player.isCreative() && !GearEvents.grantsFlight(player)) {
            player.getAbilities().flying = false;
            player.onUpdateAbilities();
        }
        if (!PiDimensions.isGasGiant(player.level())) {
            return;
        }
        player.fallDistance = fly ? 0 : player.fallDistance;
        ServerLevel level = player.serverLevel();
        if (player.tickCount % 20 == 0 && !player.isCreative() && !player.isSpectator() && PiCommonConfig.get(PiCommonConfig.GIANT_CRUSH, true)) {
            if (player.getY() < DEEP_CRUSH_Y) {
                player.hurt(SurvivalHandler.damage(player, SurvivalHandler.HAZARD), 6);
            } else if (player.getY() < CRUSH_Y) {
                player.hurt(SurvivalHandler.damage(player, SurvivalHandler.HAZARD), 2);
                player.displayClientMessage(Component.translatable("giant.pioneers.crushing").withStyle(ChatFormatting.RED), true);
            }
        }
        //Orages : eclairs autour du joueur dans la couche profonde
        RandomSource random = player.getRandom();
        boolean superstorm = SpaceEvents.active(level, SpaceEventType.GIANT_STORM) != null;
        if (PiCommonConfig.get(PiCommonConfig.GIANT_LIGHTNING, true) && (superstorm || PocketKind.Layer.at(player.getY()) == PocketKind.Layer.STORM)
            && random.nextInt(superstorm ? 30 : 140) == 0) {
            LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
            if (bolt != null) {
                double angle = random.nextDouble() * Math.PI * 2, distance = 12 + random.nextDouble() * 40;
                bolt.moveTo(player.getX() + Math.cos(angle) * distance, player.getY() - 10 + random.nextDouble() * 20, player.getZ() + Math.sin(angle) * distance);
                bolt.setVisualOnly(true);
                level.addFreshEntity(bolt);
            }
        }
        if (player.tickCount % 40 == 0) {
            populate(level, player.position(), PiCommonConfig.get(PiCommonConfig.POCKETS_AROUND_PLAYER, 14), 20, 90);
        }
    }

    /** Fait apparaitre des poches autour d'un point jusqu'a en compter {@code target} dans le rayon {@code max}. */
    public static void populate(ServerLevel level, Vec3 center, int target, double min, double max) {
        SolarSystem.Body body = SolarSystem.fromDimension(level.dimension());
        if (body == null || !body.isGasGiant()) {
            return;
        }
        AABB box = new AABB(center, center).inflate(max, level.getHeight(), max);
        int count = level.getEntitiesOfClass(GasPocketEntity.class, box).size();
        if (count >= target) {
            return;
        }
        RandomSource random = level.random;
        //Couche : brume 35 %, pont 40 %, orages 25 %
        float r = random.nextFloat();
        PocketKind.Layer layer = r < 0.35F ? PocketKind.Layer.HAZE : r < 0.75F ? PocketKind.Layer.DECK : PocketKind.Layer.STORM;
        double angle = random.nextDouble() * Math.PI * 2, distance = min + random.nextDouble() * (max - min);
        double x = center.x + Math.cos(angle) * distance, z = center.z + Math.sin(angle) * distance;
        double y = layer.minY + random.nextDouble() * (layer.maxY - layer.minY);
        GasPocketEntity pocket = PiEntityTypes.GAS_POCKET.get().create(level);
        if (pocket != null) {
            boolean superstorm = SpaceEvents.active(level, SpaceEventType.GIANT_STORM) != null;
            boolean rich = random.nextFloat() < (layer == PocketKind.Layer.STORM ? 0.15F : 0.05F) * (superstorm ? 3 : 1);
            pocket.setup(PocketKind.roll(body, layer, random), rich);
            pocket.moveTo(x, y, z, random.nextFloat() * 360, 0);
            level.addFreshEntity(pocket);
        }
    }

    @SubscribeEvent
    public static void onFall(LivingFallEvent event) {
        if (event.getEntity() instanceof Player player && canSuitFly(player)) {
            event.setCanceled(true);
        }
    }
}
