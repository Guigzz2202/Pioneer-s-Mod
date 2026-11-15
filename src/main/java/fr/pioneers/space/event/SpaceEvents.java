package fr.pioneers.space.event;

import fr.pioneers.Pioneers;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.item.ItemSpaceSuit;
import fr.pioneers.network.PacketSpaceEvent;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.registries.PiEntityTypes;
import fr.pioneers.registries.PiSounds;
import fr.pioneers.space.PiDimensions;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.SolarSystem.Sector;
import fr.pioneers.space.SolarSystem;
import fr.pioneers.space.environment.Environments;
import fr.pioneers.space.environment.OxygenRooms;
import fr.pioneers.space.environment.SurvivalHandler;
import fr.pioneers.space.research.ResearchEffects;
import fr.pioneers.tile.TileEntityForceField;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import org.jetbrains.annotations.Nullable;

/**
 * Evenements spatiaux en cours (sauvegardes avec le monde) : un evenement local par dimension, et une eruption par systeme stellaire.
 * Chaque seconde, avec des joueurs presents, un evenement peut commencer ; ses effets s'appliquent tant qu'il dure.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public class SpaceEvents extends SavedData {

    private static final String NAME = "pioneers_space_events";
    /** Calme minimal entre deux evenements d'une meme dimension (ticks, reglable dans la configuration). */
    private static long cooldown() {
        return PiCommonConfig.get(PiCommonConfig.EVENT_COOLDOWN_MINUTES, 40) * 1200L;
    }

    private final Map<String, SpaceEvent> local = new HashMap<>();
    private final Map<Sector, SpaceEvent> flares = new EnumMap<>(Sector.class);
    private final Map<String, Long> calmUntil = new HashMap<>();

    /** Cle de sauvegarde d'une dimension (mise en cache : appelee a chaque tick pour chaque monde). */
    private static final Map<ResourceKey<Level>, String> KEYS = new ConcurrentHashMap<>();

    private static String key(Level level) {
        return KEYS.computeIfAbsent(level.dimension(), dimension -> dimension.location().toString());
    }

        public static SpaceEvents get(MinecraftServer server) {
        return server.overworld().getDataStorage().computeIfAbsent(SpaceEvents::load, SpaceEvents::new, NAME);
    }

    // ------------------------------------------------------------------
    // Consultation
    // ------------------------------------------------------------------

    /** Astre ou se trouve un point d'une dimension (stations orbitales comprises), null si inconnu (Terre comprise : EARTH). */
    @Nullable
    public static Body bodyAt(Level level, double x) {
        return level.dimension() == Level.OVERWORLD ? Body.EARTH : SolarSystem.origin(level.dimension(), x);
    }

    @Nullable
    public SpaceEvent local(Level level) {
        SpaceEvent event = local.get(key(level));
        return event != null && event.end() > level.getGameTime() ? event : null;
    }

    /** Evenement local d'un type, en cours dans cette dimension. */
    @Nullable
    public static SpaceEvent active(Level level, SpaceEventType type) {
        if (!(level instanceof ServerLevel server)) {
            return null;
        }
        SpaceEvent event = get(server.getServer()).local(level);
        return event != null && event.type() == type ? event : null;
    }

    /** Eruption en cours dans le systeme de cet astre. */
    @Nullable
    public SpaceEvent flare(@Nullable Body body, long now) {
        if (body == null) {
            return null;
        }
        SpaceEvent event = flares.get(body.sector());
        return event != null && event.end() > now ? event : null;
    }

    /** Eruption en cours la ou se trouve ce point (pour l'absorbeur solaire, en station). */
    @Nullable
    public static SpaceEvent flareAt(ServerLevel level, double x) {
        return get(level.getServer()).flare(bodyAt(level, x), level.getGameTime());
    }

    // ------------------------------------------------------------------
    // Demarrage / arret
    // ------------------------------------------------------------------

    public void start(ServerLevel level, SpaceEventType type, int duration, float intensity) {
        long now = level.getGameTime();
        SpaceEvent event = new SpaceEvent(type, now, now + duration, intensity);
        if (type == SpaceEventType.SOLAR_FLARE) {
            Body body = bodyAt(level, 0);
            Sector sector = body == null ? Sector.SOL : body.sector();
            flares.put(sector, event);
            for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
                Body at = bodyAt(player.level(), player.getX());
                if (at != null && at.sector() == sector) {
                    announce(player, type, true);
                    sync(player);
                }
            }
        } else {
            local.put(key(level), event);
            for (ServerPlayer player : level.players()) {
                announce(player, type, true);
                sync(player);
            }
        }
        calmUntil.put(key(level), now + duration + cooldown());
        setDirty();
    }

    public void stop(ServerLevel level) {
        local.remove(key(level));
        Body body = bodyAt(level, 0);
        if (body != null) {
            flares.remove(body.sector());
        }
        setDirty();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            sync(player);
        }
    }

    private static void announce(ServerPlayer player, SpaceEventType type, boolean start) {
        player.sendSystemMessage(Component.translatable(type.getTranslationKey() + (start ? ".start" : ".end"))
              .withStyle(style -> style.withColor(type.color)));
        if (start) {
            player.playNotifySound(PiSounds.EVENT_ALARM.get(), SoundSource.AMBIENT, 0.9F, type == SpaceEventType.AURORA ? 1.5F : 1.0F);
        }
    }

    /** Envoie au joueur l'etat des evenements la ou il se trouve. */
    public static void sync(ServerPlayer player) {
        SpaceEvents events = get(player.server);
        long now = player.level().getGameTime();
        SpaceEvent localEvent = events.local(player.level());
        SpaceEvent flare = events.flare(bodyAt(player.level(), player.getX()), now);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketSpaceEvent(
              localEvent == null ? -1 : localEvent.type().ordinal(), localEvent == null ? 0 : localEvent.remaining(now),
              localEvent == null ? 0 : localEvent.intensity(), flare == null ? 0 : flare.remaining(now), flare == null ? 0 : flare.intensity()));
    }

    // ------------------------------------------------------------------
    // Deroulement
    // ------------------------------------------------------------------

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        MinecraftServer server = event.getServer();
        SpaceEvents events = get(server);
        long tick = server.getTickCount();
        for (ServerLevel level : server.getAllLevels()) {
            if (level.players().isEmpty()) {
                continue;
            }
            SpaceEvent current = events.local(level);
            if (current != null && current.type() == SpaceEventType.METEOR_SHOWER) {
                meteors(level, current);
            }
            if (tick % 20 != 0) {
                continue;
            }
            String key = key(level);
            //Fin d'un evenement local
            SpaceEvent stored = events.local.get(key);
            if (stored != null && stored.end() <= level.getGameTime()) {
                events.local.remove(key);
                events.setDirty();
                for (ServerPlayer player : level.players()) {
                    announce(player, stored.type(), false);
                    sync(player);
                }
            }
            Body body = bodyAt(level, 0);
            //Debut d'un evenement
            if (PiCommonConfig.get(PiCommonConfig.EVENTS_ENABLED, true) && events.local(level) == null
                && level.getGameTime() >= events.calmUntil.getOrDefault(key, 0L) && level.dimension() != PiDimensions.ORBIT) {
                RandomSource random = level.random;
                //Test une fois par seconde : la chance « par minute » est repartie sur 60 tests
                float chance = (float) PiCommonConfig.get(PiCommonConfig.EVENT_CHANCE, 0.5) / 60F;
                float danger = difficultyFactor(level);
                for (SpaceEventType type : SpaceEventType.values()) {
                    float typeChance = type.chancePerMinute() * chance * (type.isHarmful() ? danger : 1);
                    if (type.enabled() && type.canAffect(body) && random.nextFloat() < typeChance) {
                        if (type == SpaceEventType.SOLAR_FLARE && events.flare(body, level.getGameTime()) != null) {
                            continue;
                        }
                        int duration = type.minDuration + random.nextInt(type.maxDuration - type.minDuration + 1);
                        events.start(level, type, duration, 0.6F + random.nextFloat() * 0.8F);
                        break;
                    }
                }
            }
            //Effets chaque seconde
            SpaceEvent here = events.local(level);
            boolean aurora = here != null && here.type() == SpaceEventType.AURORA;
            for (ServerPlayer player : level.players()) {
                SpaceEvent flare = events.flare(bodyAt(level, player.getX()), level.getGameTime());
                if (flare != null) {
                    radiation(player, flare);
                    fires(level, player, flare);
                }
                auroraLuck(player, aurora);
            }
        }
        //Fin des eruptions
        if (tick % 20 == 0) {
            long now = server.overworld().getGameTime();
            events.flares.entrySet().removeIf(entry -> {
                if (entry.getValue().end() <= now) {
                    for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                        Body at = bodyAt(player.level(), player.getX());
                        if (at != null && at.sector() == entry.getKey()) {
                            announce(player, SpaceEventType.SOLAR_FLARE, false);
                            sync(player);
                        }
                    }
                    events.setDirty();
                    return true;
                }
                return false;
            });
        }
    }

    /**
     * Facteur de danger selon la difficulte locale (comme les monstres) : la plus forte parmi les joueurs de la dimension.
     * Paisible ou zone fraichement exploree en facile : un quart des evenements dangereux ; difficile et zone longtemps habitee : pres du double.
     */
    private static float difficultyFactor(ServerLevel level) {
        if (!PiCommonConfig.get(PiCommonConfig.EVENT_DIFFICULTY_SCALING, true)) {
            return 1;
        }
        float max = 0;
        for (ServerPlayer player : level.players()) {
            max = Math.max(max, level.getCurrentDifficultyAt(player.blockPosition()).getEffectiveDifficulty());
        }
        return Mth.clamp(0.25F + max * 0.25F, 0.25F, 2F);
    }

    /**
     * Incendies des eruptions : sur un astre qui a une atmosphere (hors Terre, protegee par sa magnetosphere), le rayonnement enflamme
     * des blocs combustibles a ciel ouvert autour des joueurs. Les champs de force protegent ce qu'ils couvrent.
     */
    private static void fires(ServerLevel level, ServerPlayer player, SpaceEvent flare) {
        Body body = bodyAt(level, player.getX());
        if (body == null || body == Body.EARTH || !PiCommonConfig.get(PiCommonConfig.FLARE_FIRES, true)
            || Environments.atmosphereDensity(level.dimension()) < 0.05 || !level.getGameRules().getBoolean(GameRules.RULE_DOFIRETICK)) {
            return;
        }
        RandomSource random = level.random;
        float strength = exposure(body) * flare.intensity();
        int attempts = Math.max(1, Math.round(strength * 2));
        for (int i = 0; i < attempts; i++) {
            if (random.nextFloat() > 0.3F) {
                continue;
            }
            int x = player.getBlockX() + random.nextInt(81) - 40, z = player.getBlockZ() + random.nextInt(81) - 40;
            BlockPos top = new BlockPos(x, level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z), z);
            BlockPos ground = top.below();
            if (!level.isLoaded(top) || !level.getBlockState(top).isAir() || !level.canSeeSky(top)
                || !level.getBlockState(ground).isFlammable(level, ground, Direction.UP)
                || TileEntityForceField.protects(level, Vec3.atCenterOf(top))) {
                continue;
            }
            level.setBlockAndUpdate(top, BaseFireBlock.getState(level, top));
        }
    }

    /** Exposition d'un astre au rayonnement des eruptions de son etoile. */
    public static float exposure(@Nullable Body body) {
        if (body == null) {
            return 0;
        }
        return switch (body) {
            case MERCURY -> 2.0F;
            case VENUS -> 1.4F;
            case EARTH -> 0.3F;
            case MOON -> 1.0F;
            case MARS, PHOBOS, DEIMOS -> 0.8F;
            case PROXIMA_B, PROXIMA_D -> 2.2F;
            case TOLIMAN_B -> 0.6F;
            case TRAPPIST_1B, TRAPPIST_1C, TRAPPIST_1D -> 1.4F;
            case TRAPPIST_1E, TRAPPIST_1F -> 1.0F;
            default -> body.sector() == Sector.SOL && body.getPrimary().orbit > 6 ? 0.25F : 0.6F;
        };
    }

    private static void radiation(ServerPlayer player, SpaceEvent flare) {
        if (player.isCreative() || player.isSpectator() || player.getVehicle() != null) {
            return;
        }
        BlockPos head = BlockPos.containing(player.getEyePosition());
        if (!player.level().canSeeSky(head) || OxygenRooms.isPressurized(player.level(), head) || TileEntityForceField.protects(player.level(), player.getEyePosition())) {
            return;
        }
        Body body = bodyAt(player.level(), player.getX());
        float dose = exposure(body) * flare.intensity() * ResearchEffects.flareDamageMultiplier()
                     * (float) PiCommonConfig.get(PiCommonConfig.FLARE_DAMAGE, 1.0);
        int tier = ItemSpaceSuit.wornTier(player);
        if (tier >= 3) {
            dose -= 1.6F;
        } else if (tier >= 1) {
            dose -= 0.4F;
        }
        if (body == Body.EARTH) {
            //La magnetosphere terrestre protege : seulement un spectacle
            return;
        }
        if (dose > 0.2F) {
            player.hurt(SurvivalHandler.damage(player, SurvivalHandler.HAZARD), Math.min(6, dose * 1.5F));
            if (player.tickCount % 100 < 20) {
                player.displayClientMessage(Component.translatable("event.pioneers.solar_flare.exposed").withStyle(ChatFormatting.GOLD), true);
            }
        }
    }

    /** 1.6 : identifiant du bonus de chance des aurores (modificateur passager, jamais sauvegarde). */
    private static final UUID AURORA_LUCK = UUID.fromString("7d3f5b1e-6a2c-4f0e-9b8d-31a6c4e2f905");

    /**
     * 1.6 (Guillaume : « les aurores boreales devraient donner 1 de luck supplementaire au joueur ») : sous une aurore, a ciel ouvert, le
     * joueur a +1 de chance (attribut {@code generic.luck}, qui s'ajoute aux autres : effet Chance, poissons legendaires...) ; retire des
     * qu'il passe a couvert, change de dimension ou que l'aurore finit (verifie chaque seconde).
     */
    public static void auroraLuck(ServerPlayer player, boolean aurora) {
        AttributeInstance luck = player.getAttribute(Attributes.LUCK);
        if (luck == null) {
            return;
        }
        boolean on = aurora && player.level().canSeeSky(BlockPos.containing(player.getEyePosition()));
        boolean has = luck.getModifier(AURORA_LUCK) != null;
        if (on && !has) {
            luck.addTransientModifier(new AttributeModifier(AURORA_LUCK, "Aurora luck", 1, AttributeModifier.Operation.ADDITION));
        } else if (!on && has) {
            luck.removeModifier(AURORA_LUCK);
        }
    }

    /** Pluie de meteores : des meteores tombent regulierement autour des joueurs. */
    private static void meteors(ServerLevel level, SpaceEvent event) {
        RandomSource random = level.random;
        for (ServerPlayer player : level.players()) {
            if (random.nextFloat() > 0.008F * event.intensity() * PiCommonConfig.get(PiCommonConfig.METEOR_DENSITY, 1.0)) {
                continue;
            }
            double angle = random.nextDouble() * Math.PI * 2, distance = 8 + random.nextDouble() * 64;
            double tx = player.getX() + Math.cos(angle) * distance, tz = player.getZ() + Math.sin(angle) * distance;
            int ground = level.getHeight(Heightmap.Types.MOTION_BLOCKING, (int) Math.floor(tx), (int) Math.floor(tz));
            MeteorEntity meteor = PiEntityTypes.METEOR.get().create(level);
            if (meteor == null) {
                continue;
            }
            double sx = tx + (random.nextDouble() - 0.5) * 120, sz = tz + (random.nextDouble() - 0.5) * 120;
            double sy = Math.min(level.getMaxBuildHeight() - 2, ground + 140);
            meteor.moveTo(sx, sy, sz, 0, 0);
            Vec3 velocity = new Vec3(tx - sx, ground - sy, tz - sz).normalize().scale(1.6 + random.nextDouble() * 0.8);
            meteor.setDeltaMovement(velocity);
            meteor.setup(random.nextInt(4), 0.8F + random.nextFloat() * 0.8F);
            level.addFreshEntity(meteor);
        }
    }

    /** Arret du serveur (monde solo quitte) : on oublie les champs de force enregistres. */
    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        TileEntityForceField.clearRegistry();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    @SubscribeEvent
    public static void onDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            sync(player);
        }
    }

    // ------------------------------------------------------------------
    // Sauvegarde
    // ------------------------------------------------------------------

    private static SpaceEvents load(CompoundTag tag) {
        SpaceEvents events = new SpaceEvents();
        CompoundTag localTag = tag.getCompound("local");
        for (String key : localTag.getAllKeys()) {
            SpaceEvent event = SpaceEvent.load(localTag.getCompound(key));
            if (event != null) {
                events.local.put(key, event);
            }
        }
        CompoundTag flareTag = tag.getCompound("flares");
        for (String key : flareTag.getAllKeys()) {
            try {
                SpaceEvent event = SpaceEvent.load(flareTag.getCompound(key));
                if (event != null) {
                    events.flares.put(Sector.valueOf(key), event);
                }
            } catch (IllegalArgumentException ignored) {
                //Systeme disparu
            }
        }
        CompoundTag calm = tag.getCompound("calm");
        for (String key : calm.getAllKeys()) {
            events.calmUntil.put(key, calm.getLong(key));
        }
        return events;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        CompoundTag localTag = new CompoundTag();
        local.forEach((key, event) -> localTag.put(key, event.save()));
        tag.put("local", localTag);
        CompoundTag flareTag = new CompoundTag();
        flares.forEach((sector, event) -> flareTag.put(sector.name(), event.save()));
        tag.put("flares", flareTag);
        CompoundTag calm = new CompoundTag();
        calmUntil.forEach(calm::putLong);
        tag.put("calm", calm);
        return tag;
    }
}
