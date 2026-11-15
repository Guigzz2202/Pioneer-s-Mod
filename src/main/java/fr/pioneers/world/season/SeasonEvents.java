package fr.pioneers.world.season;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiCommonConfig;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.world.season.Seasons.Date;
import fr.pioneers.world.season.Seasons.Season;
import java.util.Arrays;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.storage.ServerLevelData;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.0.5-beta (lot 1) : le calendrier cote SERVEUR.
 * <ul>
 *     <li>demarrage : decalage du monde ({@link SeasonData}) et duree de la config -> {@link Seasons#setServer} ;</li>
 *     <li>chaque seconde : duree relue (config), et si le jour, la saison, la duree ou le decalage a change, {@link PacketSeason} a tous ;
 *     a la connexion, au joueur ;</li>
 *     <li>temps : une duree de pluie ou d'orage que le vanilla vient de tirer est mise a l'echelle de la saison
 *     ({@link SeasonWeather#scale}) ; le cycle vanilla et {@code /weather} restent maitres ; foudre en plus dans les orages d'ete ;</li>
 *     <li>commande {@code /pioneer season get|set <saison> [jour]} (operateurs).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SeasonEvents {

    private static long sentKey = Long.MIN_VALUE;
    private static int lastRain = -1, lastThunder = -1;

    private SeasonEvents() {
    }

    public static int configLength() {
        return Seasons.clampLength(PiCommonConfig.get(PiCommonConfig.SEASON_DAYS, Seasons.DEFAULT_LENGTH));
    }

    /** Relit la donnee du monde et la config ; renvoie la date du jour. */
    public static Date refresh(MinecraftServer server) {
        ServerLevel overworld = server.overworld();
        Seasons.setServer(SeasonData.get(overworld).offset(), configLength());
        return Seasons.date(overworld.getDayTime(), Seasons.serverOffset(), Seasons.serverLength());
    }

    public static PacketSeason packet() {
        return new PacketSeason(Seasons.serverOffset(), Seasons.serverLength());
    }

    /** Envoie le calendrier a tous les joueurs. */
    public static void broadcast() {
        PiNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), packet());
    }

    /** Fixe la date du serveur (commande, GameTests) : decalage sauve, envoye a tous. */
    public static void set(MinecraftServer server, Season season, int dayOfSeason) {
        ServerLevel overworld = server.overworld();
        SeasonData data = SeasonData.get(overworld);
        data.setOffset(Seasons.offsetFor(overworld.getDayTime(), data.offset(), configLength(), season, dayOfSeason));
        refresh(server);
        broadcast();
        sentKey = Long.MIN_VALUE;
    }

    /** Remet un decalage connu (GameTests : le calendrier du monde est rendu tel qu'il etait). */
    public static void setOffset(MinecraftServer server, long offset) {
        SeasonData.get(server.overworld()).setOffset(offset);
        refresh(server);
        broadcast();
        sentKey = Long.MIN_VALUE;
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        refresh(event.getServer());
        sentKey = Long.MIN_VALUE;
        lastRain = lastThunder = -1;
    }

    @SubscribeEvent
    public static void onStopped(ServerStoppedEvent event) {
        Seasons.clearServer();
    }

    /** Tags recharges : familles des biomes recalculees ({@link BiomeClimates}), des deux cotes. */
    @SubscribeEvent
    public static void onTags(TagsUpdatedEvent event) {
        BiomeClimates.clear();
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && Seasons.serverReady()) {
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), packet());
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            Date d = refresh(level.getServer());
            long key = (d.day() * 31 + Seasons.serverLength()) * 31 + Seasons.serverOffset();
            if (key != sentKey) {
                sentKey = key;
                broadcast();
            }
        }
        if (PiCommonConfig.get(PiCommonConfig.SEASONAL_WEATHER, true) && Seasons.serverReady()) {
            Date d = Seasons.now(level);
            if (d != null) {
                weather(level, d);
            }
        }
    }

    /** Durees de pluie et d'orage tirees ce tick par le vanilla -> echelle de la saison ; foudre des orages d'ete. */
    private static void weather(ServerLevel level, Date d) {
        if (!(level.getLevelData() instanceof ServerLevelData data)) {
            return;
        }
        Season s = SeasonWeather.dominant(d);
        if (level.getGameRules().getBoolean(GameRules.RULE_WEATHER_CYCLE) && data.getClearWeatherTime() == 0) {
            int rain = data.getRainTime(), thunder = data.getThunderTime();
            if (lastRain == 0 && rain > 0) {
                data.setRainTime(SeasonWeather.scale(rain, data.isRaining() ? SeasonWeather.RAIN_DURATION : SeasonWeather.RAIN_DELAY, s));
            }
            if (lastThunder == 0 && thunder > 0) {
                data.setThunderTime(SeasonWeather.scale(thunder, data.isThundering() ? SeasonWeather.THUNDER_DURATION : SeasonWeather.THUNDER_DELAY, s));
            }
        }
        lastRain = data.getRainTime();
        lastThunder = data.getThunderTime();
        if (s == Season.SUMMER && level.isThundering()) {
            for (ServerPlayer p : level.players()) {
                if (level.random.nextFloat() >= SeasonWeather.SUMMER_STRIKE_CHANCE) {
                    continue;
                }
                int r = SeasonWeather.SUMMER_STRIKE_RADIUS;
                BlockPos at = p.blockPosition().offset(level.random.nextInt(2 * r + 1) - r, 0, level.random.nextInt(2 * r + 1) - r);
                if (!level.hasChunkAt(at)) {
                    continue;
                }
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, at);
                if (level.isRainingAt(top)) {
                    LightningBolt bolt = EntityType.LIGHTNING_BOLT.create(level);
                    if (bolt != null) {
                        bolt.moveTo(Vec3.atBottomCenterOf(top));
                        level.addFreshEntity(bolt);
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ commande

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        String[] ids = Arrays.stream(Season.values()).map(Season::id).toArray(String[]::new);
        event.getDispatcher().register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("season")
                    .then(Commands.literal("get").executes(SeasonEvents::get))
                    .then(Commands.literal("set")
                          .then(Commands.argument("season", StringArgumentType.word())
                                .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids, b))
                                .executes(ctx -> set(ctx, 1))
                                .then(Commands.argument("day", IntegerArgumentType.integer(1, Seasons.MAX_LENGTH))
                                      .executes(ctx -> set(ctx, IntegerArgumentType.getInteger(ctx, "day"))))))));
    }

    private static int get(CommandContext<CommandSourceStack> ctx) {
        Date d = refresh(ctx.getSource().getServer());
        ctx.getSource().sendSuccess(() -> Component.translatable("season.pioneers.cmd.get", SeasonTexts.dateLong(d), d.year(), SeasonTexts.next(d)), false);
        return d.season().ordinal() + 1;
    }

    private static int set(CommandContext<CommandSourceStack> ctx, int day) {
        String id = StringArgumentType.getString(ctx, "season");
        Season season = Season.byId(id);
        if (season == null) {
            ctx.getSource().sendFailure(Component.translatable("season.pioneers.cmd.unknown", id));
            return 0;
        }
        set(ctx.getSource().getServer(), season, day);
        Date d = Seasons.date(ctx.getSource().getServer().overworld().getDayTime(), Seasons.serverOffset(), Seasons.serverLength());
        ctx.getSource().sendSuccess(() -> Component.translatable("season.pioneers.cmd.set", SeasonTexts.dateLong(d)), true);
        return 1;
    }
}
