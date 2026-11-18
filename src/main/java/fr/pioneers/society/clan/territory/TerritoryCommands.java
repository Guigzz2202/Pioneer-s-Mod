package fr.pioneers.society.clan.territory;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.core.profile.PioneerApi;
import java.util.UUID;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : sous-commandes du territoire, ajoutees au noeud {@code /clan} (Brigadier fusionne les noeuds de meme nom) :
 * {@code claim}, {@code unclaim}, {@code zone members|open|private <rang>}, {@code load}, {@code unload}, {@code borders} ; et pour
 * l'administration (niveau 3) {@code /clan admin claim <clan>} et {@code /clan admin unclaim} sur le chunk ou se tient l'operateur.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class TerritoryCommands {

    private static final SuggestionProvider<CommandSourceStack> CLANS = (c, b) -> SharedSuggestionProvider.suggest(
          ClanData.get(c.getSource().getServer()).clans().stream().map(x -> x.name).filter(n -> n != null && !n.isBlank()).toList(), b);

    private TerritoryCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("clan")
              .then(Commands.literal("claim").executes(c -> run(c, ClanTerritory::claim)))
              .then(Commands.literal("unclaim").executes(c -> run(c, ClanTerritory::unclaim)))
              .then(Commands.literal("zone")
                    .then(Commands.literal("members").executes(c -> run(c, p -> ClanTerritory.zone(p, Clan.Zone.MEMBERS, 0))))
                    .then(Commands.literal("open").executes(c -> run(c, p -> ClanTerritory.zone(p, Clan.Zone.OPEN, 0))))
                    .then(Commands.literal("private").then(Commands.argument("rank", IntegerArgumentType.integer(1, 100))
                          .executes(c -> run(c, p -> ClanTerritory.zone(p, Clan.Zone.PRIVATE, IntegerArgumentType.getInteger(c, "rank")))))))
              .then(Commands.literal("load").executes(c -> run(c, ClanChunkloading::load)))
              .then(Commands.literal("unload").executes(c -> run(c, ClanChunkloading::unload)))
              //1.9 (MAJ RP, lot 19) : permission de transit
              .then(Commands.literal("transit")
                    .then(Commands.literal("grant").then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS)
                          .executes(c -> run(c, p -> ClanTransit.set(p, StringArgumentType.getString(c, "clan"), true)))))
                    .then(Commands.literal("revoke").then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS)
                          .executes(c -> run(c, p -> ClanTransit.set(p, StringArgumentType.getString(c, "clan"), false)))))
                    .then(Commands.literal("list").executes(c -> run(c, ClanTransit::list))))
              .then(Commands.literal("borders").executes(c -> run(c, p -> {
                  //1.9.2 (retours de Guillaume) : liseré ponctuel de quelques secondes, plus de bordures permanentes
                  TerritoryNet.flash(p);
                  ClanMessages.tell(p, "clan.pioneers.territory.borders_flash");
              })))
              .then(Commands.literal("admin").requires(s -> s.hasPermission(3))
                    .then(Commands.literal("claim").then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS)
                          .executes(TerritoryCommands::adminClaim)))
                    .then(Commands.literal("unclaim").executes(TerritoryCommands::adminUnclaim))));
    }

    private static int run(CommandContext<CommandSourceStack> c, java.util.function.Consumer<ServerPlayer> action) throws CommandSyntaxException {
        action.accept(c.getSource().getPlayerOrException());
        return 1;
    }

    /** Pose un claim pour un clan, sans les regles (outil d'administration ; inscrit au journal du clan). */
    private static int adminClaim(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MinecraftServer server = c.getSource().getServer();
        Clan clan = ClanData.get(server).byName(StringArgumentType.getString(c, "clan"));
        String dim = ClanApi.dim(p.level());
        long chunk = p.chunkPosition().toLong();
        if (clan == null) {
            c.getSource().sendFailure(Component.translatable("clan.pioneers.admin.unknown_clan"));
            return 0;
        }
        UUID owner = ClaimIndex.owner(dim, chunk);
        if (owner != null) {
            c.getSource().sendFailure(Component.translatable("clan.pioneers.territory.refused.taken", ""));
            return 0;
        }
        ClanTerritory.put(server, clan, dim, chunk, new Clan.Claim());
        clan.log(PioneerApi.day(server), "clan.pioneers.log.admin_claim", ClanTerritory.coords(chunk));
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.admin.claimed", clan.name, ClanTerritory.coords(chunk)), true);
        return 1;
    }

    /** Retire le claim du chunk, quel que soit son clan. */
    private static int adminUnclaim(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = c.getSource().getPlayerOrException();
        MinecraftServer server = c.getSource().getServer();
        String dim = ClanApi.dim(p.level());
        long chunk = p.chunkPosition().toLong();
        UUID owner = ClaimIndex.owner(dim, chunk);
        Clan clan = owner == null ? null : ClanData.get(server).clan(owner);
        if (owner == null) {
            c.getSource().sendFailure(Component.translatable("clan.pioneers.admin.not_claimed"));
            return 0;
        }
        if (clan != null) {
            ClanTerritory.drop(server, clan, dim, chunk, true);
            clan.log(PioneerApi.day(server), "clan.pioneers.log.admin_unclaim", ClanTerritory.coords(chunk));
        } else {
            //chunk d'un clan dissous en attente : libere tout de suite
            TerritorySaved saved = TerritorySaved.get(server);
            saved.releases.removeIf(r -> r.dim().equals(dim) && r.chunk() == chunk);
            saved.changed();
            ClaimIndex.set(dim, chunk, null);
            ClanTerritory.changed(server, dim, chunk);
        }
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.admin.unclaimed", ClanTerritory.coords(chunk)), true);
        return 1;
    }
}
