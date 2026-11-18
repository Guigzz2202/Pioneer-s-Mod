package fr.pioneers.society.clan.faction;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.pioneers.Pioneers;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Faction;
import fr.pioneers.society.clan.core.ClanDefs;
import fr.pioneers.society.clan.people.Peoples;
import fr.pioneers.society.clan.people.Rallying;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.society.clan.vote.ClanGov;
import fr.pioneers.society.clan.vote.ClanVotes;
import fr.pioneers.society.clan.vote.Proposal;
import fr.pioneers.society.economy.EconomyApi;
import fr.pioneers.society.village.VillageData;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : commandes {@code /faction} (Pacte, signature, entree, depart, exclusion, contribution, depenses, regime, vote,
 * revocation, zones et chunkloading federaux, guerre, paix, secession et peuple, doctrine, ralliement, fiches et annuaires) et
 * {@code /clan admin faction} (niveau d'op 3 : dissoudre, retirer un clan, changer de Representant federal, fixer le tresor, dissoudre un
 * peuple, lire une faction). Chaque commande passe par les memes fonctions verifiees que le reseau.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FactionCommands {

    private static final SuggestionProvider<CommandSourceStack> FACTIONS = (c, b) -> SharedSuggestionProvider.suggest(factionNames(c.getSource()), b);
    private static final SuggestionProvider<CommandSourceStack> PACTS = (c, b) -> SharedSuggestionProvider.suggest(pactNames(c.getSource()), b);
    private static final SuggestionProvider<CommandSourceStack> CLANS = (c, b) -> SharedSuggestionProvider.suggest(clanNames(c.getSource()), b);
    private static final SuggestionProvider<CommandSourceStack> REGIMES = (c, b) -> SharedSuggestionProvider.suggest(ClanDefs.REGIMES, b);
    private static final SuggestionProvider<CommandSourceStack> TINTS = (c, b) -> SharedSuggestionProvider.suggest(
          FactionRules.get().tints().stream().map(FactionRules.Tint::id).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> DOCTRINES = (c, b) -> SharedSuggestionProvider.suggest(
          FactionRules.get().doctrines().keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> VOTES = (c, b) -> SharedSuggestionProvider.suggest(openVotes(c.getSource()), b);

    private FactionCommands() {
    }

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("faction").executes(c -> info(c, null))
              .then(Commands.literal("info").executes(c -> info(c, null))
                    .then(Commands.argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                          .executes(c -> info(c, StringArgumentType.getString(c, "faction")))))
              .then(Commands.literal("list").executes(FactionCommands::list))
              .then(Commands.literal("peoples").executes(FactionCommands::peoples))
              .then(Commands.literal("pact").executes(FactionCommands::pact))
              .then(Commands.literal("sign").then(Commands.argument("pact", StringArgumentType.greedyString()).suggests(PACTS)
                    .executes(FactionCommands::sign)))
              .then(Commands.literal("join").then(Commands.argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                    .executes(FactionCommands::join)))
              .then(Commands.literal("leave").executes(c -> {
                  ServerPlayer p = player(c);
                  Clan clan = clan(p);
                  return clan == null ? fail(c, "clan.pioneers.error.no_clan") : gov(c, FactionGov.clanDecide(p, clan, FactionGov.LEAVE,
                        new CompoundTag(), ""));
              }))
              .then(Commands.literal("admit").then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS)
                    .executes(c -> clanDecision(c, "admit"))))
              .then(Commands.literal("exclude").then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS)
                    .executes(c -> clanDecision(c, "exclude"))))
              .then(Commands.literal("contribution").then(Commands.argument("percent", IntegerArgumentType.integer(0, 100)).executes(c -> {
                  CompoundTag t = new CompoundTag();
                  t.putInt("value", IntegerArgumentType.getInteger(c, "percent"));
                  return federal(c, "contribution", t, IntegerArgumentType.getInteger(c, "percent") + " %");
              })))
              .then(Commands.literal("spend").then(Commands.argument("amount", LongArgumentType.longArg(1))
                    .then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS).executes(FactionCommands::spend))))
              .then(Commands.literal("regime").then(Commands.argument("regime", StringArgumentType.word()).suggests(REGIMES).executes(c -> {
                  CompoundTag t = new CompoundTag();
                  t.putString("regime", StringArgumentType.getString(c, "regime"));
                  return federal(c, "regime", t, "");
              })))
              .then(Commands.literal("vote").executes(FactionCommands::voteList)
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(VOTES)
                          .then(Commands.argument("choice", StringArgumentType.greedyString()).executes(FactionCommands::vote))))
              .then(Commands.literal("recall").executes(c -> {
                  ServerPlayer p = player(c);
                  Faction f = Factions.ofPlayer(p.getServer(), p.getUUID());
                  return f == null ? fail(c, "clan.pioneers.faction.error.none") : gov(c, FactionGov.recall(p, f));
              }))
              .then(Commands.literal("zone")
                    .then(Commands.literal("claim").executes(c -> result(c, FactionZones.claim(player(c)), null)))
                    .then(Commands.literal("unclaim").executes(c -> result(c, FactionZones.unclaim(player(c)), null)))
                    .then(Commands.literal("load").executes(c -> result(c, FactionZones.load(player(c)), null)))
                    .then(Commands.literal("unload").executes(c -> result(c, FactionZones.unload(player(c)), null))))
              .then(Commands.literal("war").then(Commands.argument("target", StringArgumentType.string()).suggests(CLANS_AND_FACTIONS)
                    .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(FactionCommands::war))))
              .then(Commands.literal("ceasefire").then(Commands.argument("target", StringArgumentType.greedyString()).suggests(CLANS_AND_FACTIONS)
                    .executes(c -> peace(c, false))))
              .then(Commands.literal("surrender").then(Commands.argument("target", StringArgumentType.greedyString()).suggests(CLANS_AND_FACTIONS)
                    .executes(c -> peace(c, true))))
              .then(Commands.literal("treaty").then(Commands.argument("action", StringArgumentType.word()).suggests(TREATY_ACTIONS)
                    .then(Commands.argument("kind", StringArgumentType.word()).suggests(TREATY_KINDS)
                          .then(Commands.argument("target", StringArgumentType.greedyString()).suggests(CLANS_AND_FACTIONS)
                                .executes(FactionCommands::treaty)))))
              .then(Commands.literal("clantreaty").then(Commands.argument("action", StringArgumentType.word()).suggests(TREATY_ACTIONS)
                    .then(Commands.argument("kind", StringArgumentType.word()).suggests(TREATY_KINDS)
                          .then(Commands.argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS).executes(FactionCommands::clanTreaty)))))
              .then(Commands.literal("workers").then(Commands.argument("on", com.mojang.brigadier.arguments.BoolArgumentType.bool()).executes(c -> {
                  CompoundTag t = new CompoundTag();
                  t.putBoolean("on", com.mojang.brigadier.arguments.BoolArgumentType.getBool(c, "on"));
                  return federal(c, "workers", t, "");
              })))
              .then(Commands.literal("people")
                    .then(Commands.literal("name").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> draft(c, "name"))))
                    .then(Commands.literal("tint").then(Commands.argument("value", StringArgumentType.word()).suggests(TINTS).executes(c -> draft(c, "tint"))))
                    .then(Commands.literal("doctrine").then(Commands.argument("value", StringArgumentType.word()).suggests(DOCTRINES)
                          .executes(c -> draft(c, "doctrine"))))
                    .then(Commands.literal("motto").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> draft(c, "motto"))))
                    .then(Commands.literal("salute").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> draft(c, "salute"))))
                    .then(Commands.literal("banner").executes(c -> result(c, Peoples.setDraft(player(c), "banner", ""), "clan.pioneers.people.cmd.draft"))))
              .then(Commands.literal("secession").executes(FactionCommands::secession))
              .then(Commands.literal("doctrine").then(Commands.argument("doctrine", StringArgumentType.word()).suggests(DOCTRINES).executes(c -> {
                  CompoundTag t = new CompoundTag();
                  t.putString("doctrine", StringArgumentType.getString(c, "doctrine"));
                  return federal(c, "doctrine", t, "");
              })))
              .then(Commands.literal("rally").executes(FactionCommands::rally)));
        d.register(Commands.literal("clan").then(Commands.literal("admin").requires(s -> s.hasPermission(3)).then(Commands.literal("faction")
              .then(Commands.literal("info").then(Commands.argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                    .executes(c -> info(c, StringArgumentType.getString(c, "faction")))))
              .then(Commands.literal("dissolve").then(Commands.argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                    .executes(FactionCommands::adminDissolve)))
              .then(Commands.literal("remove").then(Commands.argument("faction", StringArgumentType.string()).suggests(FACTIONS)
                    .then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS).executes(FactionCommands::adminRemove))))
              .then(Commands.literal("representative").then(Commands.argument("faction", StringArgumentType.string()).suggests(FACTIONS)
                    .then(Commands.argument("player", StringArgumentType.greedyString()).executes(FactionCommands::adminRepresentative))))
              .then(Commands.literal("treasury").then(Commands.argument("faction", StringArgumentType.string()).suggests(FACTIONS)
                    .then(Commands.argument("amount", LongArgumentType.longArg(0, EconomyApi.MAX)).executes(FactionCommands::adminTreasury))))
              .then(Commands.literal("people_dissolve").then(Commands.argument("faction", StringArgumentType.greedyString()).suggests(FACTIONS)
                    .executes(FactionCommands::adminPeopleDissolve))))));
    }

    private static final SuggestionProvider<CommandSourceStack> TREATY_ACTIONS = (c, b) -> SharedSuggestionProvider.suggest(
          List.of("propose", "accept", "refuse", "break"), b);
    private static final SuggestionProvider<CommandSourceStack> TREATY_KINDS = (c, b) -> SharedSuggestionProvider.suggest(FactionRules.TREATIES, b);

    private static final SuggestionProvider<CommandSourceStack> CLANS_AND_FACTIONS = (c, b) -> {
        List<String> all = new ArrayList<>(factionNames(c.getSource()));
        MinecraftServer server = c.getSource().getServer();
        for (Clan x : ClanData.get(server).clans()) {
            if (!x.solo && !x.name.isBlank()) {
                all.add(x.name.contains(" ") ? "\"" + x.name + "\"" : x.name);
            }
        }
        return SharedSuggestionProvider.suggest(all, b);
    };

    // ------------------------------------------------------------------ aides
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    @Nullable
    private static Clan clan(ServerPlayer p) {
        return ClanData.get(p.getServer()).clanOf(p.getUUID());
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key) {
        c.getSource().sendFailure(Component.translatable(key));
        return 0;
    }

    private static int result(CommandContext<CommandSourceStack> c, @Nullable String err, @Nullable String ok) {
        if (err != null) {
            return fail(c, err);
        }
        if (ok != null) {
            c.getSource().sendSuccess(() -> Component.translatable(ok), false);
        }
        return 1;
    }

    private static int gov(CommandContext<CommandSourceStack> c, ClanGov.Result r) {
        if (!r.ok()) {
            return fail(c, r.error());
        }
        c.getSource().sendSuccess(() -> Component.translatable(r.outcome() == ClanGov.Outcome.PROPOSED ? "clan.pioneers.faction.cmd.proposed"
              : "clan.pioneers.faction.cmd.applied", r.proposal() == null ? "" : r.proposal()), false);
        return 1;
    }

    /** Decision federale engagee par le joueur (sa faction). */
    private static int federal(CommandContext<CommandSourceStack> c, String decision, CompoundTag data, String text) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Faction f = Factions.ofPlayer(p.getServer(), p.getUUID());
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.none");
        }
        return gov(c, FactionGov.decide(p, f, decision, data, text));
    }

    private static List<String> factionNames(CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        for (Faction f : ClanData.get(s.getServer()).factions()) {
            out.add(f.name);
        }
        return out;
    }

    private static List<String> pactNames(CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        for (CompoundTag t : FactionSaved.get(s.getServer()).pacts.values()) {
            out.add(t.getString("name"));
        }
        return out;
    }

    private static List<String> clanNames(CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        for (Clan c : ClanData.get(s.getServer()).clans()) {
            if (!c.solo && !c.name.isBlank()) {
                out.add(c.name);
            }
        }
        return out;
    }

    private static List<String> openVotes(CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        ServerPlayer p = s.getPlayer();
        Faction f = p == null ? null : Factions.ofPlayer(s.getServer(), p.getUUID());
        if (f != null) {
            for (Proposal x : ClanVotes.openOf(f.proposals)) {
                out.add(x.id());
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ fiches
    private static int info(CommandContext<CommandSourceStack> c, @Nullable String name) {
        MinecraftServer server = c.getSource().getServer();
        Faction f;
        if (name == null) {
            ServerPlayer p = c.getSource().getPlayer();
            f = p == null ? null : Factions.ofPlayer(server, p.getUUID());
            if (f == null) {
                return fail(c, "clan.pioneers.faction.error.none");
            }
        } else {
            f = Factions.byName(server, name);
            if (f == null) {
                return fail(c, "clan.pioneers.faction.error.unknown");
            }
        }
        Faction ff = f;
        int level = Factions.level(server, f);
        c.getSource().sendSuccess(() -> Factions.tagged(ff, Component.translatable("clan.pioneers.faction.info.title", ff.name,
              Component.translatable("clan.pioneers.regime." + ff.regime), level)).withStyle(ChatFormatting.BOLD), false);
        if (!f.motto.isEmpty()) {
            c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.quoted", ff.motto).withStyle(ChatFormatting.ITALIC,
                  ChatFormatting.GRAY), false);
        }
        List<String> clans = new ArrayList<>();
        for (Clan x : Factions.clans(server, f)) {
            clans.add(x.name);
        }
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.faction.info.clans", ff.clans.size(), String.join(", ", clans)), false);
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.faction.info.representative", Factions.delegateName(server, ff),
              Factions.members(server, ff)), false);
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.faction.info.treasury", ff.treasury, ff.contribution, Factions.cap(ff),
              FactionZones.zones(ff).size(), FactionZones.budget(server, ff), FactionZones.loaded(ff).size(), FactionZones.slots(server, ff)), false);
        if (f.people != null && !f.people.isEmpty() && Peoples.people(server, f) != null) {
            c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.people.info", Peoples.people(server, ff).displayName(),
                  Component.translatable("clan.pioneers.doctrine." + Peoples.people(server, ff).doctrine()), Rallying.rallied(ff).size()), false);
            if (ff.declineUntil > 0) {
                c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.people.info_decline",
                      Math.max(0, (ff.declineUntil - fr.pioneers.society.clan.core.ClanClock.now()) / FactionRules.DAY)).withStyle(ChatFormatting.RED), false);
            }
        }
        return 1;
    }

    private static int list(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        if (ClanData.get(server).factions().isEmpty()) {
            return fail(c, "clan.pioneers.faction.list.empty");
        }
        for (Faction f : ClanData.get(server).factions()) {
            c.getSource().sendSuccess(() -> Factions.tagged(f, Component.translatable("clan.pioneers.faction.list.line", f.name,
                  Component.translatable("clan.pioneers.regime." + f.regime), Factions.level(server, f), f.clans.size(), Factions.members(server, f))),
                  false);
        }
        return 1;
    }

    private static int peoples(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        CompoundTag d = Peoples.directory(server);
        for (Tag x : d.getList("peoples", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) x;
            Component name = Component.Serializer.fromJson(p.getString("name"));
            Component doctrine = Component.translatable("clan.pioneers.doctrine." + p.getString("doctrine"));
            Component line = p.getBoolean("player")
                  ? Component.translatable("clan.pioneers.people.list.player", name, doctrine, p.getString("faction"), p.getInt("clans"),
                  p.getInt("members"), p.getInt("rallied"))
                  : Component.translatable("clan.pioneers.people.list.villagers", name, doctrine, p.getInt("villages"));
            int rgb = p.getInt("color");
            c.getSource().sendSuccess(() -> Component.literal("■ ").withStyle(s -> s.withColor(rgb)).append(line), false);
        }
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.people.list.max", d.getInt("max")).withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    // ------------------------------------------------------------------ Pacte
    private static int pact(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        for (InteractionHand h : InteractionHand.values()) {
            if (p.getItemInHand(h).is(FactionItems.PACT.get())) {
                FederalPact.use(p, h);
                return 1;
            }
        }
        return fail(c, "clan.pioneers.pact.none_held");
    }

    private static int sign(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        String name = StringArgumentType.getString(c, "pact").trim();
        Clan clan = clan(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        for (CompoundTag t : FactionSaved.get(p.getServer()).pacts.values()) {
            if (t.getString("name").equalsIgnoreCase(name) || t.getString("tag").equalsIgnoreCase(name)) {
                CompoundTag d = new CompoundTag();
                d.putUUID("pact", t.getUUID("id"));
                return gov(c, FactionGov.clanDecide(p, clan, FactionGov.SIGN, d, t.getString("name")));
            }
        }
        return fail(c, "clan.pioneers.pact.unknown");
    }

    private static int join(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = clan(p);
        Faction f = Factions.byName(p.getServer(), StringArgumentType.getString(c, "faction"));
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.unknown");
        }
        CompoundTag d = new CompoundTag();
        d.putUUID("faction", f.id);
        return gov(c, FactionGov.clanDecide(p, clan, FactionGov.JOIN, d, f.name));
    }

    private static int clanDecision(CommandContext<CommandSourceStack> c, String decision) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan target = ClanData.get(p.getServer()).byName(StringArgumentType.getString(c, "clan").trim());
        if (target == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        CompoundTag d = new CompoundTag();
        d.putUUID("clan", target.id);
        return federal(c, decision, d, target.name);
    }

    private static int spend(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan target = ClanData.get(p.getServer()).byName(StringArgumentType.getString(c, "clan").trim());
        if (target == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        CompoundTag d = new CompoundTag();
        d.putUUID("clan", target.id);
        d.putLong("amount", LongArgumentType.getLong(c, "amount"));
        return federal(c, "spend", d, target.name + " : " + d.getLong("amount"));
    }

    // ------------------------------------------------------------------ votes
    private static int voteList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Faction f = Factions.ofPlayer(p.getServer(), p.getUUID());
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.none");
        }
        List<Proposal> open = ClanVotes.openOf(f.proposals);
        if (open.isEmpty()) {
            return fail(c, "clan.pioneers.faction.vote.none");
        }
        for (Proposal x : open) {
            Component type = Component.translatable("clan.pioneers.vote.type." + x.type());
            boolean mine = x.electorate().containsKey(p.getUUID());
            c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.faction.vote.line", x.id(), type, x.text(),
                  Component.translatable(mine ? (x.voted(p.getUUID()) ? "clan.pioneers.faction.vote.done" : "clan.pioneers.faction.vote.yours")
                        : "clan.pioneers.faction.vote.not_yours")), false);
            if (x.election() && mine && !x.voted(p.getUUID())) {
                List<String> names = new ArrayList<>();
                for (UUID u : x.candidates()) {
                    names.add(Factions.character(p.getServer(), u));
                }
                c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.faction.vote.candidates", String.join(", ", names))
                      .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    private static int vote(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Faction f = Factions.ofPlayer(p.getServer(), p.getUUID());
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.none");
        }
        String id = StringArgumentType.getString(c, "id");
        String choice = StringArgumentType.getString(c, "choice").trim();
        Proposal x = ClanVotes.find(f.proposals, id);
        if (x != null && x.election()) {
            //election : le choix est le nom de personnage d'un candidat
            String found = null;
            for (UUID u : x.candidates()) {
                if (Factions.character(p.getServer(), u).equalsIgnoreCase(choice)) {
                    found = u.toString();
                }
            }
            if (found == null) {
                return fail(c, "clan.pioneers.error.vote_choice");
            }
            choice = found;
        } else {
            choice = switch (choice.toLowerCase(java.util.Locale.ROOT)) {
                case "oui", "yes", "pour" -> "yes";
                case "non", "no", "contre" -> "no";
                default -> "abstain";
            };
        }
        return result(c, ClanVotes.vote(p, ClanVotes.Scope.FACTION, f.id, id, choice), "clan.pioneers.faction.vote.cast");
    }

    // ------------------------------------------------------------------ guerre
    private static int war(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        FactionWar.Target t = FactionWar.target(p.getServer(), StringArgumentType.getString(c, "target"));
        if (t == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        String reason = StringArgumentType.getString(c, "reason").trim();
        CompoundTag d = new CompoundTag();
        d.putUUID("target", t.id());
        d.putBoolean("targetFaction", t.faction());
        d.putString("reason", reason);
        return federal(c, "war", d, t.name() + " : " + reason);
    }

    private static int peace(CommandContext<CommandSourceStack> c, boolean surrender) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        FactionWar.Target t = FactionWar.target(p.getServer(), StringArgumentType.getString(c, "target"));
        if (t == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        CompoundTag d = new CompoundTag();
        d.putUUID("target", t.id());
        d.putBoolean("surrender", surrender);
        d.putString("label", surrender ? "surrender" : "ceasefire");
        return federal(c, "peace", d, t.name());
    }

    // ------------------------------------------------------------------ traites
    /** {@code /faction treaty <action> <sorte> <clan ou faction>} : decision federale (ligne « traite » du regime federal). */
    private static int treaty(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        FactionWar.Target t = FactionWar.target(p.getServer(), StringArgumentType.getString(c, "target").replace("\"", "").trim());
        if (t == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        CompoundTag d = new CompoundTag();
        d.putString("action", StringArgumentType.getString(c, "action"));
        d.putString("kind", StringArgumentType.getString(c, "kind"));
        d.putUUID("target", t.id());
        d.putBoolean("targetFaction", t.faction());
        return federal(c, FederalTreaties.FED_TYPE, d, t.name());
    }

    /** {@code /faction clantreaty <action> <sorte> <faction>} : decision du CLAN du joueur (ligne « traite » de son regime). */
    private static int clanTreaty(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = clan(p);
        Faction f = Factions.byName(p.getServer(), StringArgumentType.getString(c, "faction").replace("\"", "").trim());
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.unknown");
        }
        CompoundTag d = new CompoundTag();
        d.putString("action", StringArgumentType.getString(c, "action"));
        d.putString("kind", StringArgumentType.getString(c, "kind"));
        d.putUUID("faction", f.id);
        return gov(c, FactionGov.clanDecide(p, clan, FederalTreaties.CLAN_TYPE, d, f.name));
    }

    // ------------------------------------------------------------------ peuple
    private static int draft(CommandContext<CommandSourceStack> c, String field) throws CommandSyntaxException {
        return result(c, Peoples.setDraft(player(c), field, StringArgumentType.getString(c, "value")), "clan.pioneers.people.cmd.draft");
    }

    private static int secession(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Faction f = Factions.ofPlayer(p.getServer(), p.getUUID());
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.none");
        }
        CompoundTag d = new CompoundTag();
        CompoundTag draft = f.extra.getCompound(Peoples.DRAFT).copy();
        d.put("draft", draft);
        return federal(c, "secession", d, draft.getString("name"));
    }

    private static int rally(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        VillageData.Village v = Rallying.near(p);
        if (v == null) {
            return fail(c, "clan.pioneers.rally.error.no_village");
        }
        CompoundTag d = new CompoundTag();
        d.putLong("village", v.id);
        d.putString("dim", fr.pioneers.society.clan.ClanApi.dim(p.level()));
        return federal(c, "rally", d, v.center.getX() + " " + v.center.getZ());
    }

    // ------------------------------------------------------------------ administration (op 3)
    @Nullable
    private static Faction faction(CommandContext<CommandSourceStack> c) {
        return Factions.byName(c.getSource().getServer(), StringArgumentType.getString(c, "faction"));
    }

    private static int adminDissolve(CommandContext<CommandSourceStack> c) {
        Faction f = faction(c);
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.unknown");
        }
        ClanAudit.write(c.getSource().getServer(), "admin", null, c.getSource().getTextName() + " dissout la faction " + f.name);
        Factions.dissolve(c.getSource().getServer(), f, "clan.pioneers.faction.reason.admin");
        return result(c, null, "clan.pioneers.cmd.done");
    }

    private static int adminRemove(CommandContext<CommandSourceStack> c) {
        Faction f = faction(c);
        Clan clan = ClanData.get(c.getSource().getServer()).byName(StringArgumentType.getString(c, "clan").trim());
        if (f == null || clan == null || !f.clans.contains(clan.id)) {
            return fail(c, "clan.pioneers.faction.error.not_in");
        }
        ClanAudit.write(c.getSource().getServer(), "admin", clan, c.getSource().getTextName() + " retire le clan de la faction " + f.name);
        Factions.remove(c.getSource().getServer(), f, clan, "admin");
        return result(c, null, "clan.pioneers.cmd.done");
    }

    private static int adminRepresentative(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Faction f = faction(c);
        ServerPlayer p = server.getPlayerList().getPlayerByName(StringArgumentType.getString(c, "player").trim());
        if (f == null || p == null || !Factions.member(server, f, p.getUUID())) {
            return fail(c, "clan.pioneers.faction.error.not_member");
        }
        ClanAudit.write(server, "admin", null, c.getSource().getTextName() + " nomme le Representant federal de " + f.name);
        FactionGov.setRepresentative(server, f, p.getUUID(), "clan.pioneers.reason.admin");
        return result(c, null, "clan.pioneers.cmd.done");
    }

    private static int adminTreasury(CommandContext<CommandSourceStack> c) {
        Faction f = faction(c);
        if (f == null) {
            return fail(c, "clan.pioneers.faction.error.unknown");
        }
        long amount = LongArgumentType.getLong(c, "amount");
        ClanAudit.write(c.getSource().getServer(), "admin", null, c.getSource().getTextName() + " fixe le tresor federal de " + f.name + " : "
              + f.treasury + " -> " + amount);
        f.treasury = amount;
        Factions.log(c.getSource().getServer(), f, "clan.pioneers.faction.log.admin_treasury", String.valueOf(amount));
        return result(c, null, "clan.pioneers.cmd.done");
    }

    private static int adminPeopleDissolve(CommandContext<CommandSourceStack> c) {
        Faction f = faction(c);
        if (f == null || f.people == null || f.people.isEmpty()) {
            return fail(c, "clan.pioneers.people.error.not_people");
        }
        ClanAudit.write(c.getSource().getServer(), "admin", null, c.getSource().getTextName() + " dissout le peuple de " + f.name);
        Peoples.dissolve(c.getSource().getServer(), f, "clan.pioneers.faction.reason.admin");
        return result(c, null, "clan.pioneers.cmd.done");
    }
}
