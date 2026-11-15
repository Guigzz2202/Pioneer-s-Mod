package fr.pioneers.world.pet;

import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.PortalInfo;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.util.ITeleporter;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.5, decision 8) : le RAPPEL (touche G). Serveur seul juge : le client ne demande que « tous » ou
 * « celui-ci » ({@link PacketPetRecall}) ; delai entre deux rappels d'un joueur (reglage {@code pets.recallCooldownSeconds}).
 * <ul>
 *   <li>Chaque animal dont le joueur est le maitre ({@link PetRegistry}) est TELEPORTE pres de lui, sur un sol sur, sans limite de distance
 *   et d'une dimension a l'autre.</li>
 *   <li>Animal dont le troncon n'est pas charge : son troncon (derniere position connue) est charge par un ticket le temps du rappel
 *   ({@link PiPets#RECALL_TICKET}, {@link PetRules#LOAD_WAIT} ticks au plus), puis il est teleporte des que ses entites sont chargees.</li>
 *   <li>Choix faits : un animal ASSIS est rappele et se releve (le maitre l'appelle) ; un chien en CHASSE cesse de chasser ; tout animal
 *   rappele passe en mode Suivre ; une monture MONTEE PAR UN AUTRE JOUEUR n'est pas rappelee (on ne fait pas tomber un joueur) ; une laisse
 *   est detachee ; une bete d'une caravane en route n'est pas rappelee.</li>
 * </ul>
 * 1.0.5-beta (lot 33, retour 45) : la liste de la roue est POUSSEE au client sans demande ({@link PetListSync}) : a la connexion, au
 * changement de dimension, a la reapparition et quand un de ses animaux change ({@link PetRegistry}) ; les demandes trop rapprochees sont
 * servies des que le delai le permet ({@link #lists}). Le client la garde en cache : la roue s'ouvre pleine, sans attendre de reponse.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PetRecall {

    private PetRecall() {
    }

    private record Pending(UUID player, UUID pet, ResourceKey<Level> dim, ChunkPos chunk, long until) {
    }

    private static final Map<UUID, Long> LAST = new HashMap<>();
    private static final List<Pending> PENDING = new ArrayList<>();
    /** Rappels reussis depuis le demarrage (GameTests). */
    public static int recalled;

    public static int cooldownTicks() {
        return 20 * PiCommonConfig.get(PiCommonConfig.PET_RECALL_COOLDOWN, PetRules.RECALL_COOLDOWN);
    }

    /** Rappel demande par un joueur : tous ses animaux (only null) ou un seul. */
    public static void request(ServerPlayer p, @Nullable UUID only) {
        long now = p.server.overworld().getGameTime();
        Long last = LAST.get(p.getUUID());
        int cd = cooldownTicks();
        if (PetRules.waiting(last, now, cd) && !p.isCreative()) {
            p.displayClientMessage(Component.translatable("pet.pioneers.recall.wait", (cd - (now - last) + 19) / 20).withStyle(ChatFormatting.GOLD), true);
            return;
        }
        List<PetRegistry.Entry> list = new ArrayList<>(PetRegistry.get(p.server).list(p.getUUID()));
        if (only != null) {
            list.removeIf(e -> !e.id().equals(only));
        }
        if (list.isEmpty()) {
            p.displayClientMessage(Component.translatable("pet.pioneers.recall.none").withStyle(ChatFormatting.GRAY), true);
            return;
        }
        LAST.put(p.getUUID(), now);
        int done = 0, waiting = 0;
        for (PetRegistry.Entry e : list) {
            Entity found = find(p.server, e.id());
            if (found != null) {
                done += bring(p, found) ? 1 : 0;
            } else if (load(p, e)) {
                waiting++;
            }
        }
        p.displayClientMessage(Component.translatable(waiting > 0 ? "pet.pioneers.recall.done_wait" : "pet.pioneers.recall.done", done, waiting)
              .withStyle(ChatFormatting.GREEN), true);
    }

    @Nullable
    public static Entity find(MinecraftServer server, UUID id) {
        for (ServerLevel l : server.getAllLevels()) {
            Entity e = l.getEntity(id);
            if (e != null && e.isAlive()) {
                return e;
            }
        }
        return null;
    }

    /** Charge le troncon de la derniere position connue (ticket a duree limitee) ; vrai si l'attente commence. */
    static boolean load(ServerPlayer p, PetRegistry.Entry e) {
        ServerLevel level = p.server.getLevel(ResourceKey.create(Registries.DIMENSION, e.dim()));
        if (level == null) {
            p.displayClientMessage(Component.translatable("pet.pioneers.recall.lost", label(e)).withStyle(ChatFormatting.RED), false);
            return false;
        }
        ChunkPos cp = new ChunkPos(BlockPos.containing(e.x(), e.y(), e.z()));
        level.getChunkSource().addRegionTicket(PiPets.RECALL_TICKET, cp, 2, cp);
        PENDING.add(new Pending(p.getUUID(), e.id(), level.dimension(), cp, p.server.overworld().getGameTime() + PetRules.LOAD_WAIT));
        return true;
    }

    static Component label(PetRegistry.Entry e) {
        return e.name().isEmpty() ? Component.translatable("entity." + e.type().replace(':', '.')) : Component.literal(e.name());
    }

    @SubscribeEvent
    public static void tick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PENDING.isEmpty()) {
            return;
        }
        MinecraftServer server = event.getServer();
        long now = server.overworld().getGameTime();
        for (Iterator<Pending> it = PENDING.iterator(); it.hasNext(); ) {
            Pending pd = it.next();
            ServerPlayer p = server.getPlayerList().getPlayer(pd.player());
            ServerLevel level = server.getLevel(pd.dim());
            Entity e = find(server, pd.pet());
            boolean over = e != null || now >= pd.until() || p == null;
            if (e != null && p != null) {
                bring(p, e);
            } else if (over && p != null) {
                PetRegistry.Entry en = PetRegistry.get(server).find(pd.pet());
                p.displayClientMessage(Component.translatable("pet.pioneers.recall.lost", en == null ? Component.literal("?") : label(en))
                      .withStyle(ChatFormatting.RED), false);
            }
            if (over) {
                if (level != null) {
                    level.getChunkSource().removeRegionTicket(PiPets.RECALL_TICKET, pd.chunk(), 2, pd.chunk());
                }
                it.remove();
            }
        }
    }

    /** Joueurs a qui la liste de la roue est due ce tick (reutilisee : aucune allocation par tick). */
    private static final List<UUID> DUE = new ArrayList<>();

    /** 1.0.5-beta (lot 33) : envois dus de la liste de la roue (demandes trop rapprochees, changements, connexion) ; rien si la file est vide. */
    @SubscribeEvent
    public static void lists(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || PetListSync.SERVER.idle()) {
            return;
        }
        MinecraftServer server = event.getServer();
        PetListSync.SERVER.due(server.overworld().getGameTime(), DUE);
        for (UUID id : DUE) {
            ServerPlayer p = server.getPlayerList().getPlayer(id);
            if (p != null) {
                PacketPetRecall.send(p);
            }
        }
        DUE.clear();
    }

    @SubscribeEvent
    public static void joined(PlayerEvent.PlayerLoggedInEvent event) {
        PetListSync.SERVER.owe(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void travelled(PlayerEvent.PlayerChangedDimensionEvent event) {
        PetListSync.SERVER.owe(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void respawned(PlayerEvent.PlayerRespawnEvent event) {
        PetListSync.SERVER.owe(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void left(PlayerEvent.PlayerLoggedOutEvent event) {
        PetListSync.SERVER.forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void stopping(ServerStoppingEvent event) {
        PENDING.clear();
        LAST.clear();
        PacketPetRecall.clear();      //1.0.5-beta (lot 12) : delais de la liste et des avertissements remis a zero avec le serveur
        PetMaster.clearWarnings();
    }

    /** Amene l'animal pres du joueur (meme dimension : teleportation ; autre dimension : changement de dimension). */
    public static boolean bring(ServerPlayer p, Entity e) {
        Entity rider = e.getControllingPassenger();
        if (rider instanceof Player other && other != p) {
            p.displayClientMessage(Component.translatable("pet.pioneers.recall.ridden", e.getDisplayName(), other.getDisplayName())
                  .withStyle(ChatFormatting.GOLD), false);
            return false;
        }
        if (e instanceof Mob m && m.getLeashHolder() instanceof Mob) {
            p.displayClientMessage(Component.translatable("pet.pioneers.recall.caravan", e.getDisplayName()).withStyle(ChatFormatting.GOLD), false);
            return false;
        }
        e.ejectPassengers();
        e.stopRiding();
        if (e instanceof Mob m && m.isLeashed()) {
            m.dropLeash(true, true);
        }
        if (e instanceof Pet pet) {
            if (pet.mode() == PetMode.HUNT) {
                HuntEvents.endHunt(pet.self());
            }
            pet.setMode(PetMode.FOLLOW);
            pet.setRest(Pet.AWAKE);
        }
        if (e instanceof TamableAnimal t) {
            t.setOrderedToSit(false);
            t.setInSittingPose(false);
        }
        ServerLevel dest = p.serverLevel();
        Vec3 at = safeSpot(dest, p, e.getType().getDimensions());
        Entity moved = e;
        if (e.level() != dest) {
            moved = e.changeDimension(dest, new ITeleporter() {
                @Override
                public Entity placeEntity(Entity entity, ServerLevel current, ServerLevel target, float yaw, Function<Boolean, Entity> reposition) {
                    return reposition.apply(false);
                }

                @Override
                public PortalInfo getPortalInfo(Entity entity, ServerLevel target, Function<ServerLevel, PortalInfo> fallback) {
                    return new PortalInfo(at, Vec3.ZERO, entity.getYRot(), entity.getXRot());
                }

                @Override
                public boolean playTeleportSound(ServerPlayer player, ServerLevel source, ServerLevel target) {
                    return false;
                }
            });
            if (moved == null) {
                return false;
            }
        } else {
            moved.teleportTo(at.x, at.y, at.z);
        }
        moved.fallDistance = 0;
        if (moved instanceof Mob m) {
            m.getNavigation().stop();
        }
        if (moved instanceof Pet pet) {
            pet.setMode(PetMode.FOLLOW);
        }
        dest.sendParticles(ParticleTypes.CLOUD, at.x, at.y + 0.4, at.z, 10, 0.3, 0.3, 0.3, 0.02);
        dest.playSound(null, at.x, at.y, at.z, SoundEvents.CHORUS_FRUIT_TELEPORT, SoundSource.NEUTRAL, 0.4F, 1.3F);
        PetRegistry.get(p.server).update(moved);
        recalled++;
        return true;
    }

    /** Sol sur pres du joueur : bloc solide dessous, aucun liquide, place libre pour la boite de l'animal ; a defaut, la place du joueur. */
    public static Vec3 safeSpot(ServerLevel level, Player p, EntityDimensions dims) {
        BlockPos base = p.blockPosition();
        for (int r = 1; r <= 4; r++) {
            for (int dx = -r; dx <= r; dx++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) != r) {
                        continue;
                    }
                    for (int dy : new int[]{0, 1, -1, 2, -2}) {
                        BlockPos pos = base.offset(dx, dy, dz);
                        if (safe(level, pos, dims)) {
                            return new Vec3(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
                        }
                    }
                }
            }
        }
        return p.position();
    }

    static boolean safe(ServerLevel level, BlockPos pos, EntityDimensions dims) {
        BlockPos below = pos.below();
        if (!level.isLoaded(pos) || !level.getBlockState(below).isFaceSturdy(level, below, Direction.UP) || !level.getFluidState(pos).isEmpty()
            || !level.getFluidState(below).isEmpty()) {
            return false;
        }
        AABB box = dims.makeBoundingBox(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(box) && !level.containsAnyLiquid(box);
    }
}
