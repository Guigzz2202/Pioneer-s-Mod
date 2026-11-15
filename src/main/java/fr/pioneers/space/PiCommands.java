package fr.pioneers.space;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.exceptions.SimpleCommandExceptionType;
import fr.pioneers.Pioneers;
import fr.pioneers.space.SolarSystem.Body;
import fr.pioneers.space.event.SpaceEventType;
import fr.pioneers.space.event.SpaceEvents;
import fr.pioneers.space.giant.Aerostats;
import fr.pioneers.space.research.ProbeDestination;
import fr.pioneers.space.research.ProbeNetwork;
import fr.pioneers.space.research.ResearchNode;
import fr.pioneers.space.research.ResearchState;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Commandes d'administration (niveau 2, operateurs / triche) :
 * <pre>
 *  /pioneers research unlockall              toutes les recherches terminees
 *  /pioneers research reset                  tout recommencer
 *  /pioneers research complete &lt;recherche&gt;   terminer une recherche
 *  /pioneers tp &lt;astre&gt; [joueurs]            se teleporter a la surface d'un astre
 *  /pioneers probes &lt;destination&gt; &lt;nombre&gt;   fixer le nombre de sondes en orbite
 *  /pioneers event start &lt;type&gt; [secondes]   declencher un evenement spatial ici
 *  /pioneers event stop                     arreter les evenements ici
 * </pre>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PiCommands {

    private PiCommands() {
    }

    private static final SimpleCommandExceptionType UNKNOWN_BODY = new SimpleCommandExceptionType(
          Component.translatable("command.pioneers.unknown_body"));
    private static final SimpleCommandExceptionType UNKNOWN_NODE = new SimpleCommandExceptionType(
          Component.translatable("command.pioneers.unknown_research"));
    private static final SimpleCommandExceptionType UNKNOWN_DESTINATION = new SimpleCommandExceptionType(
          Component.translatable("command.pioneers.unknown_destination"));

    @SubscribeEvent
    public static void onRegister(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }

    private static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal(Pioneers.MODID)
              .requires(source -> source.hasPermission(2))
              .then(Commands.literal("research")
                    .then(Commands.literal("unlockall").executes(context -> {
                        ResearchState.get(context.getSource().getServer()).completeAll(context.getSource().getServer());
                        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.research.unlocked", ResearchNode.values().length), true);
                        return ResearchNode.values().length;
                    }))
                    .then(Commands.literal("reset").executes(context -> {
                        ResearchState.get(context.getSource().getServer()).reset(context.getSource().getServer());
                        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.research.reset"), true);
                        return 1;
                    }))
                    .then(Commands.literal("complete")
                          .then(Commands.argument("research", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                      Arrays.stream(ResearchNode.values()).map(node -> node.name().toLowerCase(Locale.ROOT)), builder))
                                .executes(PiCommands::completeResearch))))
              .then(Commands.literal("tp")
                    .then(Commands.argument("body", StringArgumentType.word())
                          .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Body.ALL.stream().filter(body -> body.dimension() != null).map(body -> body.name().toLowerCase(Locale.ROOT)), builder))
                          .executes(context -> teleport(context, List.of(context.getSource().getPlayerOrException())))
                          .then(Commands.argument("targets", EntityArgument.players())
                                .executes(context -> teleport(context, EntityArgument.getPlayers(context, "targets"))))))
              .then(Commands.literal("probes")
                    .then(Commands.argument("destination", StringArgumentType.word())
                          .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                Arrays.stream(ProbeDestination.values()).map(destination -> destination.name().toLowerCase(Locale.ROOT)), builder))
                          .then(Commands.argument("count", IntegerArgumentType.integer(0, 10_000))
                                .executes(PiCommands::setProbes))))
              .then(Commands.literal("event")
                    .then(Commands.literal("start")
                          .then(Commands.argument("type", StringArgumentType.word())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggest(
                                      Arrays.stream(SpaceEventType.values()).map(SpaceEventType::id), builder))
                                .executes(context -> startEvent(context, -1))
                                .then(Commands.argument("seconds", IntegerArgumentType.integer(5, 7200))
                                      .executes(context -> startEvent(context, IntegerArgumentType.getInteger(context, "seconds"))))))
                    .then(Commands.literal("stop").executes(context -> {
                        SpaceEvents.get(context.getSource().getServer()).stop(context.getSource().getLevel());
                        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.event.stopped"), true);
                        return 1;
                    }))));
    }

    private static final SimpleCommandExceptionType UNKNOWN_EVENT = new SimpleCommandExceptionType(
          Component.translatable("command.pioneers.unknown_event"));

    private static int startEvent(CommandContext<CommandSourceStack> context, int seconds) throws CommandSyntaxException {
        SpaceEventType type = SpaceEventType.byId(StringArgumentType.getString(context, "type").toLowerCase(Locale.ROOT));
        if (type == null) {
            throw UNKNOWN_EVENT.create();
        }
        int duration = seconds > 0 ? seconds * 20 : (type.minDuration + type.maxDuration) / 2;
        SpaceEvents.get(context.getSource().getServer()).start(context.getSource().getLevel(), type, duration, 1.0F);
        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.event.started", Component.translatable(type.getTranslationKey()),
              duration / 20), true);
        return 1;
    }

    private static int completeResearch(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ResearchNode node;
        try {
            node = ResearchNode.valueOf(StringArgumentType.getString(context, "research").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_NODE.create();
        }
        ResearchState.get(context.getSource().getServer()).forceComplete(context.getSource().getServer(), node, false);
        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.research.completed",
              Component.translatable(node.getTranslationKey())), true);
        return 1;
    }

    private static int setProbes(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ProbeDestination destination;
        try {
            destination = ProbeDestination.valueOf(StringArgumentType.getString(context, "destination").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_DESTINATION.create();
        }
        int count = IntegerArgumentType.getInteger(context, "count");
        ProbeNetwork.get(context.getSource().getServer()).setProbes(destination, count);
        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.probes",
              Component.translatable(destination.getTranslationKey()), count), true);
        return count;
    }

    private static int teleport(CommandContext<CommandSourceStack> context, Collection<ServerPlayer> players) throws CommandSyntaxException {
        Body body;
        try {
            body = Body.valueOf(StringArgumentType.getString(context, "body").toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw UNKNOWN_BODY.create();
        }
        if (body.dimension() == null) {
            throw UNKNOWN_BODY.create();
        }
        ServerLevel level = context.getSource().getServer().getLevel(body.dimension());
        if (level == null) {
            throw UNKNOWN_BODY.create();
        }
        for (ServerPlayer player : players) {
            int x = player.getBlockX(), z = player.getBlockZ();
            if (body.isGasGiant()) {
                //Pas de sol : on arrive sur un aerostat
                BlockPos deck = Aerostats.ensureNear(level, x, z);
                player.teleportTo(level, deck.getX() + 0.5, deck.getY() + 1.1, deck.getZ() + 0.5, player.getYRot(), player.getXRot());
                player.fallDistance = 0;
                continue;
            }
            //Charge (et genere) le chunk d'arrivee pour trouver la surface
            level.getChunk(x >> 4, z >> 4);
            int y = level.getHeight(Heightmap.Types.MOTION_BLOCKING, x, z);
            if (y <= level.getMinBuildHeight() + 1) {
                y = 100;
            }
            BlockPos pos = new BlockPos(x, y, z);
            player.teleportTo(level, pos.getX() + 0.5, pos.getY() + 0.1, pos.getZ() + 0.5, player.getYRot(), player.getXRot());
            player.fallDistance = 0;
        }
        Component name = Component.translatable(body.getTranslationKey());
        context.getSource().sendSuccess(() -> Component.translatable("command.pioneers.tp", players.size(), name), true);
        return players.size();
    }
}
