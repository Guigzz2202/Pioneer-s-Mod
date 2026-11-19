package fr.pioneers.space.mega;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import fr.pioneers.Pioneers;
import fr.pioneers.core.network.PiNetwork;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * 1.0.7 (« Plus loin et au-dela », lot 4) : synchronisation des megastructures et commande d'operateur.
 * <ul>
 *     <li>connexion : l'etat au joueur ;</li>
 *     <li>changement (livraison, ouverture) : l'etat a tous, au plus une fois par seconde (jamais a chaque tick, meme quand un cable
 *     livre a chaque tick) ; deblocage : tout de suite, avec un message a tous ;</li>
 *     <li>{@code /pioneer mega list|unlock <id>|reset <id>} (operateurs : essais et serveurs).</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class MegaEvents {

    /** Cadence d'envoi des changements (ticks). */
    static final int SYNC_PERIOD = 20;
    private static boolean dirty, now;
    private static int clock;

    private MegaEvents() {
    }

    static void markDirty() {
        dirty = true;
    }

    /** Megastructure debloquee : envoi immediat et message a tous les joueurs. */
    static void unlocked(MinecraftServer server, int i) {
        now = true;
        dirty = true;
        server.getPlayerList().broadcastSystemMessage(Component.translatable("mega.pioneers." + Megastructures.id(i) + ".unlocked"), false);
    }

    /** Envoie l'etat a tous les joueurs. */
    public static void broadcast(MinecraftServer server) {
        dirty = false;
        now = false;
        PiNetwork.CHANNEL.send(PacketDistributor.ALL.noArg(), MegaWorld.get(server).packet());
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), MegaWorld.get(player.server).packet());
        }
    }

    @SubscribeEvent
    public static void onStarted(ServerStartedEvent event) {
        dirty = false;
        now = false;
        clock = 0;
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        clock++;
        if (dirty && (now || clock >= SYNC_PERIOD)) {
            clock = 0;
            broadcast(event.getServer());
        } else if (clock >= SYNC_PERIOD) {
            clock = 0;
        }
    }

    // ------------------------------------------------------------------ commande

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent event) {
        String[] ids = new String[Megastructures.COUNT];
        for (int i = 0; i < ids.length; i++) {
            ids[i] = Megastructures.id(i);
        }
        event.getDispatcher().register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("mega")
                    .then(Commands.literal("list").executes(MegaEvents::list))
                    .then(Commands.literal("unlock").then(Commands.argument("id", StringArgumentType.word())
                          .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids, b)).executes(ctx -> change(ctx, true))))
                    .then(Commands.literal("reset").then(Commands.argument("id", StringArgumentType.word())
                          .suggests((ctx, b) -> SharedSuggestionProvider.suggest(ids, b)).executes(ctx -> change(ctx, false))))));
    }

    private static int list(CommandContext<CommandSourceStack> ctx) {
        MegaWorld w = MegaWorld.get(ctx.getSource().getServer());
        for (int i = 0; i < Megastructures.COUNT; i++) {
            int n = i;
            ctx.getSource().sendSuccess(() -> Component.translatable(w.unlocked(n) ? "mega.pioneers.cmd.unlocked" : "mega.pioneers.cmd.site",
                  Megastructures.name(n), Megastructures.id(n), w.deliveredTotal(n), Megastructures.total(n)), false);
        }
        return Megastructures.COUNT;
    }

    private static int change(CommandContext<CommandSourceStack> ctx, boolean unlock) {
        String id = StringArgumentType.getString(ctx, "id");
        int i = -1;
        for (int n = 0; n < Megastructures.COUNT; n++) {
            if (Megastructures.id(n).equals(id)) {
                i = n;
            }
        }
        if (i < 0) {
            ctx.getSource().sendFailure(Component.translatable("mega.pioneers.cmd.unknown", id));
            return 0;
        }
        MinecraftServer server = ctx.getSource().getServer();
        MegaWorld w = MegaWorld.get(server);
        if (unlock) {
            w.unlock(server, i);
        } else {
            w.reset(i);
            now = true;
        }
        int n = i;
        ctx.getSource().sendSuccess(() -> Component.translatable(unlock ? "mega.pioneers.cmd.unlock" : "mega.pioneers.cmd.reset",
              Megastructures.name(n)), true);
        return 1;
    }
}
