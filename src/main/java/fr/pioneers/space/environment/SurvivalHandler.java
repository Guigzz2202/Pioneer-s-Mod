package fr.pioneers.space.environment;

import fr.pioneers.Pioneers;
import fr.pioneers.api.PioneersAPI;
import fr.pioneers.api.environment.BodyEnvironment;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.gear.GearEvents;
import fr.pioneers.item.ItemSpaceSuit;
import fr.pioneers.network.PacketSurvivalStatus;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.space.CapsuleEntity;
import fr.pioneers.space.RocketEntity;
import fr.pioneers.tile.TileEntityOxygenDiffuser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Survie sur les astres. Chaque seconde, pour chaque joueur hors de l'atmosphere terrestre :
 * <ol>
 *     <li>dans une piece pressurisee (diffuseur d'oxygene), ou a bord d'une fusee ou d'une capsule : rien a craindre,
 *     et la reserve d'oxygene de la combinaison se remplit ;</li>
 *     <li>sinon il respire la reserve de son plastron (20 mB/s) ; sans casque ou sans oxygene : il suffoque ;</li>
 *     <li>et si sa combinaison n'est pas du tier exige par l'astre (base, thermique, anti-acide), le milieu le blesse.</li>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class SurvivalHandler {

    private SurvivalHandler() {
    }

    public static final ResourceKey<DamageType> VACUUM = ResourceKey.create(Registries.DAMAGE_TYPE, Pioneers.rl("vacuum"));
    public static final ResourceKey<DamageType> HAZARD = ResourceKey.create(Registries.DAMAGE_TYPE, Pioneers.rl("hazard"));
    /** Remplissage de la combinaison dans une piece pressurisee (mB/s). */
    private static final long REFILL_PER_SECOND = 200;

    /** Branche l'API publique sur l'implementation du mod. */
    public static void install() {
        PioneersAPI.setHelper(new PioneersAPI.IHelper() {
            @Override
            public BodyEnvironment environment(Level level) {
                return Environments.get(level);
            }

            @Override
            public boolean isBreathable(Level level, BlockPos pos) {
                return Environments.get(level).breathable() || OxygenRooms.isPressurized(level, pos);
            }

            @Override
            public int suitTier(LivingEntity entity) {
                return ItemSpaceSuit.wornTier(entity);
            }
        });
    }

    @SubscribeEvent
    public static void onReload(AddReloadListenerEvent event) {
        event.addListener(Environments.INSTANCE);
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.player instanceof ServerPlayer player) || player.tickCount % 20 != 0) {
            return;
        }
        BodyEnvironment environment = Environments.get(player.level());
        if (environment.breathable()) {
            if (player.tickCount % 200 == 0) {
                //Hors des astres : on efface l'affichage de survie de temps en temps (changement de dimension)
                send(player, PacketSurvivalStatus.NONE);
            }
            return;
        }
        boolean exempt = player.isCreative() || player.isSpectator();
        boolean sheltered = player.getVehicle() instanceof RocketEntity || player.getVehicle() instanceof CapsuleEntity;
        TileEntityOxygenDiffuser room = OxygenRooms.pressurizedAt(player.level(), BlockPos.containing(player.getEyePosition()));
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        int tier = ItemSpaceSuit.wornTier(player);
        boolean safe = exempt || sheltered || room != null;
        if (room != null) {
            //La combinaison se recharge dans la piece pressurisee
            long missing = ItemSpaceSuit.getOxygenCapacity(chest) - ItemSpaceSuit.getOxygen(chest);
            if (missing > 0 && ItemSpaceSuit.REFILL_ENABLED) {
                long supplied = room.supplyOxygen(Math.min(REFILL_PER_SECOND, missing));
                ItemSpaceSuit.refill(chest, supplied);
            }
        } else if (!safe) {
            boolean helmet = player.getItemBySlot(EquipmentSlot.HEAD).getItem() instanceof ItemSpaceSuit;
            //0.20.15 : le recycleur d'air d'un casque modulaire fait respirer (sur l'energie du casque)
            //1.9 (MAJ RP) : skill Spatial = une respiration sur cinq gratuite au plus ; Sang-froid de l'Astronaute = 10 s d'apnee
            boolean breathing = helmet && (fr.pioneers.core.profile.SkillHooks.oxygenSaved(player, ItemSpaceSuit.getOxygen(chest) > 0)
                                           || ItemSpaceSuit.breathe(chest)) || GearEvents.breathe(player);
            if (breathing) {
                fr.pioneers.core.profile.SkillHooks.breathed(player);
            }
            boolean apnea = !breathing && fr.pioneers.core.profile.SkillHooks.holdBreath(player);
            if (!breathing) {
                if (!apnea && PiCommonConfig.get(PiCommonConfig.VACUUM_DAMAGE, true)) {
                    player.hurt(source(player, VACUUM), 2.0F);
                }
            } else if (tier < environment.suitTier() && PiCommonConfig.get(PiCommonConfig.HAZARD_DAMAGE, true)) {
                player.hurt(source(player, HAZARD), environment.suitTier() - tier >= 2 ? 3.0F : 1.5F);
            }
        }
        long capacity = ItemSpaceSuit.getOxygenCapacity(chest);
        int oxygen = capacity <= 0 ? GearEvents.breathReserve(player) : (int) (1000 * ItemSpaceSuit.getOxygen(chest) / capacity);
        send(player, new PacketSurvivalStatus(true, safe, room != null, environment.suitTier(), tier, oxygen, environment.hazard().ordinal(),
              environment.temperature()));
    }

    @SubscribeEvent
    public static void onChangeDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            send(player, PacketSurvivalStatus.NONE);
        }
    }

    /** Source de degats du mod (vide, milieu hostile...). */
    public static DamageSource damage(Player player, ResourceKey<DamageType> type) {
        return source(player, type);
    }

    private static DamageSource source(Player player, ResourceKey<DamageType> type) {
        return new DamageSource(player.level().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(type));
    }

    private static void send(ServerPlayer player, PacketSurvivalStatus status) {
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), status);
    }
}
