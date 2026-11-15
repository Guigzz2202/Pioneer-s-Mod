package fr.pioneers.pneumatic.network;

import fr.pioneers.Pioneers;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.registries.PiTriggers;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * Voyages en tube pneumatique : depart (verification du chemin et de l'air, prelevement, capsule), surveillance du trajet (tube casse :
 * le voyageur est pose a la station de depart si le chemin du retour tient, sinon au point sur le plus proche), arrivee (sortie devant la
 * bouche de la station, chuintement), et securite : pas de sortie en route, pas de degats, reprise a l'arrivee en cas de deconnexion.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TubeTravel {

    /** Marque posee sur le joueur pendant un voyage : dimension, depart, arrivee. */
    private static final String TAG = "pioneers_tube";
    private static final String LANG = "message.pioneers.tube.";

    private TubeTravel() {
    }

    // ------------------------------------------------------------------
    // Depart
    // ------------------------------------------------------------------

    /** Le joueur demande a partir de {@code station} vers la station en {@code target}. */
    static void start(ServerPlayer player, TubeStationTile station, BlockPos target) {
        if (player.isPassenger() || player.isSpectator() || !player.isAlive() || player.isSleeping()) {
            refuse(player, "busy");
            return;
        }
        ServerLevel level = player.serverLevel();
        TubeRoutes.Route route = null;
        for (TubeRoutes.Route candidate : TubeRoutes.explore(level, station.getBlockPos())) {
            if (candidate.station().equals(target)) {
                route = candidate;
                break;
            }
        }
        if (route == null || target.equals(station.getBlockPos()) || !(level.getBlockEntity(target) instanceof TubeStationTile arrival)
            || arrival.name().isEmpty()) {
            refuse(player, "unreachable");
            return;
        }
        //1.9 (MAJ RP, bloc 4) : pas de voyage a travers une frontiere de claim refusee (entre les deux stations)
        if (!ClanApi.canTransfer(level, station.getBlockPos(), target)) {
            refuse(player, "clan");
            return;
        }
        int blocks = route.blocks();
        if (station.air().getPressure() < TubeStationTile.MIN_PRESSURE) {
            refuse(player, "no_pressure", String.format("%.1f", TubeStationTile.MIN_PRESSURE));
            return;
        }
        if (!station.canAfford(blocks)) {
            refuse(player, "no_air", (int) Math.ceil(TubeStationTile.cost(blocks)), (int) station.air().getAir());
            return;
        }
        station.pay(blocks);
        TubeCapsuleEntity capsule = TubeCapsuleEntity.create(level, station.getBlockPos(), route);
        level.addFreshEntity(capsule);
        if (!player.startRiding(capsule, true)) {
            capsule.release();
            return;
        }
        CompoundTag tag = new CompoundTag();
        tag.putString("dim", level.dimension().location().toString());
        tag.putLong("origin", station.getBlockPos().asLong());
        tag.putLong("target", target.asLong());
        player.getPersistentData().put(TAG, tag);
        Vec3 at = Vec3.atCenterOf(station.getBlockPos());
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_EXTEND, SoundSource.BLOCKS, 0.6F, 1.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.5F, 0.7F);
        player.displayClientMessage(Component.translatable(LANG + "depart", arrival.name()), true);
    }

    private static void refuse(ServerPlayer player, String reason, Object... args) {
        player.displayClientMessage(Component.translatable(LANG + "refused." + reason, args), true);
    }

    // ------------------------------------------------------------------
    // Trajet
    // ------------------------------------------------------------------

    private static boolean intact(Level level, BlockPos pos) {
        if (!level.hasChunkAt(pos)) {
            //Partie non chargee : on ne peut pas savoir, on fait confiance
            return true;
        }
        BlockState state = level.getBlockState(pos);
        return state.getBlock() instanceof PneumaticTubeBlock || state.getBlock() instanceof TubeStationBlock;
    }

    /** Verifie le chemin (hypertubes encore poses, anciens tubes en place) ; s'il est coupe, secours. */
    static void check(ServerLevel level, TubeCapsuleEntity capsule, ServerPlayer player) {
        HypertubeWorld world = HypertubeWorld.get(level);
        for (int id : capsule.segments()) {
            if (!world.exists(id)) {
                emergency(level, capsule, player);
                return;
            }
        }
        for (BlockPos pos : capsule.blocks()) {
            if (!intact(level, pos)) {
                emergency(level, capsule, player);
                return;
            }
        }
    }

    /** Tube coupe : retour devant la station de depart si elle existe encore, sinon point sur le plus proche. */
    private static void emergency(ServerLevel level, TubeCapsuleEntity capsule, ServerPlayer player) {
        boolean back = level.getBlockEntity(capsule.origin()) instanceof TubeStationTile;
        Vec3 spot = null;
        if (back && level.getBlockEntity(capsule.origin()) instanceof TubeStationTile origin) {
            spot = safeSpot(level, origin.mouth(), 3);
        }
        if (spot == null) {
            back = false;
            spot = safeSpot(level, BlockPos.containing(capsule.position()), 6);
        }
        if (spot == null) {
            BlockPos here = BlockPos.containing(capsule.position());
            spot = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, here));
        }
        capsule.release();
        place(player, level, spot, player.getYRot());
        player.getPersistentData().remove(TAG);
        level.playSound(null, spot.x, spot.y, spot.z, SoundEvents.GLASS_BREAK, SoundSource.BLOCKS, 0.6F, 1.2F);
        player.displayClientMessage(Component.translatable(LANG + (back ? "emergency_back" : "emergency")), true);
    }

    /** Fin du trajet : sortie devant la bouche de la station d'arrivee. */
    static void arrive(ServerLevel level, TubeCapsuleEntity capsule, ServerPlayer player) {
        BlockPos target = capsule.target();
        if (!(level.getBlockEntity(target) instanceof TubeStationTile station)) {
            emergency(level, capsule, player);
            return;
        }
        capsule.release();
        Direction facing = station.getBlockState().getValue(TubeStationBlock.FACING);
        Vec3 spot = safeSpot(level, station.mouth(), 4);
        if (spot == null) {
            spot = Vec3.atBottomCenterOf(level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, station.mouth()));
        }
        float yaw = facing.getAxis().isHorizontal() ? facing.toYRot() : player.getYRot();
        place(player, level, spot, yaw);
        player.getPersistentData().remove(TAG);
        station.light();
        Vec3 at = Vec3.atCenterOf(target);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.7F, 0.6F);
        level.playSound(null, at.x, at.y, at.z, SoundEvents.PISTON_CONTRACT, SoundSource.BLOCKS, 0.5F, 1.5F);
        player.displayClientMessage(Component.translatable(LANG + "arrival", station.name()), true);
        PiTriggers.MECHANICS.trigger(player, "tube_ride");
    }

    private static void place(ServerPlayer player, ServerLevel level, Vec3 spot, float yaw) {
        player.fallDistance = 0;
        player.setDeltaMovement(Vec3.ZERO);
        if (player.level() != level) {
            player.teleportTo(level, spot.x, spot.y, spot.z, yaw, player.getXRot());
        } else {
            player.connection.teleport(spot.x, spot.y, spot.z, yaw, player.getXRot());
        }
    }

    /** Case ou un joueur tient debout (deux cases libres sur un sol), au plus pres de {@code around}. */
    @Nullable
    static Vec3 safeSpot(Level level, BlockPos around, int radius) {
        EntityDimensions size = EntityDimensions.scalable(0.6F, 1.8F);
        for (int r = 0; r <= radius; r++) {
            for (int k = 0; k <= 2 * r; k++) {
                //Hauteurs essayees : meme niveau, puis -1, +1, -2, +2...
                int dy = (k % 2 == 0 ? -1 : 1) * ((k + 1) / 2);
                for (int dx = -r; dx <= r; dx++) {
                    for (int dz = -r; dz <= r; dz++) {
                        if (Math.max(Math.abs(dx), Math.max(Math.abs(dy), Math.abs(dz))) != r) {
                            continue;
                        }
                        BlockPos pos = around.offset(dx, dy, dz);
                        if (!level.hasChunkAt(pos)) {
                            continue;
                        }
                        Vec3 feet = Vec3.atBottomCenterOf(pos);
                        if (level.noCollision(size.makeBoundingBox(feet)) && !level.getBlockState(pos.below()).getCollisionShape(level, pos.below()).isEmpty()
                            && level.getFluidState(pos).isEmpty()) {
                            return feet;
                        }
                    }
                }
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Securite
    // ------------------------------------------------------------------

    /** Pas de sortie en route (Maj) : seule la capsule laisse descendre son passager. Cote serveur uniquement. */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void dismount(EntityMountEvent event) {
        if (event.isDismounting() && !event.getLevel().isClientSide() && event.getEntityBeingMounted() instanceof TubeCapsuleEntity capsule
            && !capsule.releasing && capsule.isAlive()) {
            event.setCanceled(true);
        }
    }

    /** Aucun degat dans une capsule (murs traverses, chute, etc.). */
    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void attack(LivingAttackEvent event) {
        if (event.getEntity().getVehicle() instanceof TubeCapsuleEntity && !event.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            event.setCanceled(true);
        }
    }

    /** Deconnexion en route : le voyageur est depose a la station d'arrivee avant la sauvegarde. */
    @SubscribeEvent
    public static void logout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getVehicle() instanceof TubeCapsuleEntity capsule) {
            capsule.release();
            recover(player);
        }
    }

    /** Voyage interrompu sans deconnexion propre (arret brutal) : reprise a l'arrivee, ou au depart. */
    @SubscribeEvent
    public static void login(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && player.getPersistentData().contains(TAG)) {
            if (player.getVehicle() instanceof TubeCapsuleEntity capsule) {
                capsule.release();
            }
            recover(player);
        }
    }

    /** Mort ou changement de dimension en route : le voyage est abandonne. */
    @SubscribeEvent
    public static void respawn(PlayerEvent.PlayerRespawnEvent event) {
        event.getEntity().getPersistentData().remove(TAG);
    }

    /** Pose le joueur devant la station d'arrivee (a defaut, de depart) de son voyage en cours et efface la marque. */
    private static void recover(ServerPlayer player) {
        CompoundTag tag = player.getPersistentData().getCompound(TAG);
        player.getPersistentData().remove(TAG);
        if (tag.isEmpty() || player.getServer() == null) {
            return;
        }
        ResourceKey<Level> key = ResourceKey.create(Registries.DIMENSION, ResourceLocation.parse(tag.getString("dim")));
        ServerLevel level = player.getServer().getLevel(key);
        if (level == null) {
            return;
        }
        for (String which : new String[]{"target", "origin"}) {
            BlockPos pos = BlockPos.of(tag.getLong(which));
            level.getChunkAt(pos);
            if (level.getBlockEntity(pos) instanceof TubeStationTile station) {
                Vec3 spot = safeSpot(level, station.mouth(), 4);
                if (spot != null) {
                    player.fallDistance = 0;
                    player.teleportTo(level, spot.x, spot.y, spot.z, player.getYRot(), player.getXRot());
                    return;
                }
            }
        }
    }

    /** Le joueur est-il en voyage ? (rendu reduit, etc.) */
    public static boolean riding(Entity entity) {
        return entity.getVehicle() instanceof TubeCapsuleEntity;
    }
}
