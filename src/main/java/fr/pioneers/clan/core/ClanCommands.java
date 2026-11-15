package fr.pioneers.clan.core;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.Permission;
import fr.pioneers.clan.treasury.ClanAudit;
import fr.pioneers.clan.treasury.ClanFinance;
import fr.pioneers.clan.vote.ClanGov;
import fr.pioneers.clan.vote.ClanVotes;
import fr.pioneers.clan.vote.Proposal;
import fr.pioneers.core.profile.PioneerApi;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.DyeColor;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 4) : commandes {@code /clan} des fondations (creer, signer, inviter, accepter, refuser, quitter, exclure, role, vote,
 * tresor, regime, heritier, contre-pouvoirs, identite, info) et {@code /clan admin} (niveau d'op 3 : dissoudre, changer de Representant, fixer
 * le niveau, geler le tresor, lire l'audit). Le territoire, la guerre et la diplomatie ajoutent leurs sous-commandes au meme noeud
 * {@code clan} (Brigadier fusionne les noeuds) : aucune exigence n'est posee sur la racine. Chaque commande passe par les memes fonctions
 * verifiees que le reseau.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class ClanCommands {

    private ClanCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> CLANS = (c, b) -> SharedSuggestionProvider.suggest(clanNames(c.getSource()), b);
    private static final SuggestionProvider<CommandSourceStack> MEMBERS = (c, b) -> SharedSuggestionProvider.suggest(memberNames(c.getSource()), b);
    private static final SuggestionProvider<CommandSourceStack> ROLES = (c, b) -> SharedSuggestionProvider.suggest(roleIds(c.getSource()), b);
    private static final SuggestionProvider<CommandSourceStack> PERMS = (c, b) -> SharedSuggestionProvider.suggest(
          java.util.Arrays.stream(Permission.values()).map(Permission::id).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> REGIMES = (c, b) -> SharedSuggestionProvider.suggest(ClanDefs.REGIMES, b);
    private static final SuggestionProvider<CommandSourceStack> TAXES = (c, b) -> SharedSuggestionProvider.suggest(List.of("activity", "dues",
          "market"), b);
    private static final SuggestionProvider<CommandSourceStack> COLORS = (c, b) -> SharedSuggestionProvider.suggest(
          java.util.Arrays.stream(DyeColor.values()).map(DyeColor::getName).toList(), b);
    private static final SuggestionProvider<CommandSourceStack> VOTES = (c, b) -> SharedSuggestionProvider.suggest(openVotes(c.getSource()), b);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("clan").executes(c -> info(c, null))
              .then(Commands.literal("create").executes(ClanCommands::create))
              .then(Commands.literal("sign").then(Commands.argument("founder", EntityArgument.player()).executes(c -> result(c,
                    Charter.signCommand(player(c), EntityArgument.getPlayer(c, "founder"))))))
              .then(Commands.literal("invite").then(Commands.argument("player", EntityArgument.player()).executes(c -> result(c,
                    ClanCore.invite(player(c), EntityArgument.getPlayer(c, "player")), "clan.pioneers.cmd.invited"))))
              .then(Commands.literal("accept").executes(c -> result(c, ClanCore.accept(player(c), null)))
                    .then(Commands.argument("clan", StringArgumentType.greedyString()).executes(c -> result(c, ClanCore.accept(player(c),
                          StringArgumentType.getString(c, "clan"))))))
              .then(Commands.literal("decline").executes(c -> result(c, ClanCore.decline(player(c)) ? null : "clan.pioneers.error.no_invite",
                    "clan.pioneers.cmd.declined")))
              .then(Commands.literal("leave").executes(c -> result(c, ClanCore.leave(player(c)), "clan.pioneers.cmd.left")))
              .then(Commands.literal("kick").then(Commands.argument("member", StringArgumentType.string()).suggests(MEMBERS).executes(ClanCommands::kick)))
              .then(Commands.literal("role")
                    .then(Commands.literal("list").executes(ClanCommands::roleList))
                    .then(Commands.literal("set").then(Commands.argument("member", StringArgumentType.string()).suggests(MEMBERS)
                          .then(Commands.argument("role", StringArgumentType.word()).suggests(ROLES).executes(ClanCommands::roleSet))))
                    .then(Commands.literal("create").then(Commands.argument("id", StringArgumentType.word())
                          .then(Commands.argument("rank", IntegerArgumentType.integer(1, 99)).executes(c -> roleEdit(c, "create"))
                                .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> roleEdit(c, "create"))))))
                    .then(Commands.literal("delete").then(Commands.argument("id", StringArgumentType.word()).suggests(ROLES)
                          .executes(c -> roleEdit(c, "delete"))))
                    .then(Commands.literal("perm").then(Commands.argument("id", StringArgumentType.word()).suggests(ROLES)
                          .then(Commands.argument("perm", StringArgumentType.word()).suggests(PERMS)
                                .then(Commands.argument("on", BoolArgumentType.bool()).executes(c -> roleEdit(c, "perm"))))))
                    .then(Commands.literal("rank").then(Commands.argument("id", StringArgumentType.word()).suggests(ROLES)
                          .then(Commands.argument("rank", IntegerArgumentType.integer(1, 99)).executes(c -> roleEdit(c, "rank")))))
                    .then(Commands.literal("cap").then(Commands.argument("id", StringArgumentType.word()).suggests(ROLES)
                          .then(Commands.argument("cap", LongArgumentType.longArg(-1, 1_000_000_000L)).executes(c -> roleEdit(c, "cap")))))
                    .then(Commands.literal("rename").then(Commands.argument("id", StringArgumentType.word()).suggests(ROLES)
                          .then(Commands.argument("name", StringArgumentType.greedyString()).executes(c -> roleEdit(c, "rename"))))))
              .then(Commands.literal("vote").executes(ClanCommands::voteList)
                    .then(Commands.argument("id", StringArgumentType.word()).suggests(VOTES)
                          .then(Commands.argument("choice", StringArgumentType.greedyString()).executes(ClanCommands::vote))))
              .then(Commands.literal("treasury").executes(ClanCommands::treasury)
                    .then(Commands.literal("donate").then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(c -> result(c,
                          ClanFinance.donate(player(c), LongArgumentType.getLong(c, "amount")), "clan.pioneers.cmd.donated"))))
                    .then(Commands.literal("withdraw").then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(c -> paid(c,
                          ClanFinance.withdraw(player(c), LongArgumentType.getLong(c, "amount"))))))
                    .then(Commands.literal("bonus").then(Commands.argument("member", StringArgumentType.string()).suggests(MEMBERS)
                          .then(Commands.argument("amount", LongArgumentType.longArg(1)).executes(ClanCommands::bonus))))
                    .then(Commands.literal("tax").then(Commands.argument("kind", StringArgumentType.word()).suggests(TAXES)
                          .then(Commands.argument("value", IntegerArgumentType.integer(0, 100)).executes(ClanCommands::tax)))))
              .then(Commands.literal("regime").then(Commands.argument("regime", StringArgumentType.word()).suggests(REGIMES).executes(c -> {
                  ServerPlayer p = player(c);
                  Clan clan = mine(p);
                  return clan == null ? fail(c, "clan.pioneers.error.no_clan") : gov(c, ClanGov.changeRegime(p, clan,
                        StringArgumentType.getString(c, "regime")));
              })))
              .then(Commands.literal("heir").then(Commands.argument("member", StringArgumentType.string()).suggests(MEMBERS).executes(ClanCommands::heir)))
              .then(Commands.literal("recall").executes(c -> counter(c, "recall")))
              .then(Commands.literal("defiance").executes(c -> counter(c, "no_confidence")))
              .then(Commands.literal("revolution").executes(c -> counter(c, "revolution")))
              .then(Commands.literal("identity")
                    .then(Commands.literal("name").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> identity(c, "name"))))
                    .then(Commands.literal("tag").then(Commands.argument("value", StringArgumentType.word()).executes(c -> identity(c, "tag"))))
                    .then(Commands.literal("motto").then(Commands.argument("value", StringArgumentType.greedyString()).executes(c -> identity(c, "motto"))))
                    .then(Commands.literal("color").then(Commands.argument("value", StringArgumentType.word()).suggests(COLORS)
                          .executes(c -> identity(c, "color")))))
              .then(Commands.literal("info").executes(c -> info(c, null))
                    .then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS).executes(c -> info(c,
                          StringArgumentType.getString(c, "clan")))))
              .then(Commands.literal("admin").requires(s -> s.hasPermission(3))
                    .then(Commands.literal("dissolve").then(Commands.argument("clan", StringArgumentType.greedyString()).suggests(CLANS)
                          .executes(ClanCommands::adminDissolve)))
                    .then(Commands.literal("representative").then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
                          .then(Commands.argument("player", StringArgumentType.greedyString()).executes(ClanCommands::adminRepresentative))))
                    .then(Commands.literal("level").then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
                          .then(Commands.argument("level", IntegerArgumentType.integer(1, 100)).executes(ClanCommands::adminLevel))))
                    .then(Commands.literal("freeze").then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
                          .then(Commands.argument("frozen", BoolArgumentType.bool()).executes(ClanCommands::adminFreeze))))
                    .then(Commands.literal("audit").executes(c -> adminAudit(c, 20))
                          .then(Commands.argument("lines", IntegerArgumentType.integer(1, 200)).executes(c -> adminAudit(c,
                                IntegerArgumentType.getInteger(c, "lines")))))));
    }

    // ------------------------------------------------------------------ aides
    private static ServerPlayer player(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        return c.getSource().getPlayerOrException();
    }

    @Nullable
    private static Clan mine(ServerPlayer p) {
        return ClanData.get(p.getServer()).clanOf(p.getUUID());
    }

    private static int result(CommandContext<CommandSourceStack> c, @Nullable String err) {
        return result(c, err, "clan.pioneers.cmd.done");
    }

    private static int result(CommandContext<CommandSourceStack> c, @Nullable String err, String ok) {
        if (err != null) {
            return fail(c, err);
        }
        c.getSource().sendSuccess(() -> Component.translatable(ok), false);
        return 1;
    }

    private static int fail(CommandContext<CommandSourceStack> c, String key) {
        c.getSource().sendFailure(Component.translatable(key));
        return 0;
    }

    private static int gov(CommandContext<CommandSourceStack> c, ClanGov.Result r) {
        if (!r.ok()) {
            return fail(c, r.error());
        }
        c.getSource().sendSuccess(() -> Component.translatable(r.outcome() == ClanGov.Outcome.PROPOSED ? "clan.pioneers.cmd.proposed"
              : "clan.pioneers.cmd.applied"), false);
        return 1;
    }

    private static int paid(CommandContext<CommandSourceStack> c, ClanFinance.Done d) {
        if (d.error() != null) {
            return fail(c, d.error());
        }
        c.getSource().sendSuccess(() -> ClanFinance.describe(d), false);
        return 1;
    }

    /** Membre designe par son nom de personnage ou de compte (celui qui tape le nom le connait deja). */
    @Nullable
    static UUID resolve(MinecraftServer server, Clan c, String name) {
        for (Clan.Member m : c.members.values()) {
            if (name.equalsIgnoreCase(m.account) || name.equalsIgnoreCase(m.character) || name.equalsIgnoreCase(ClanMessages.character(server, c,
                  m.id))) {
                return m.id;
            }
        }
        return null;
    }

    @Nullable
    private static Clan byName(MinecraftServer server, String name) {
        return ClanData.get(server).byName(name.trim());
    }

    private static List<String> clanNames(CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        for (Clan c : ClanData.get(s.getServer()).clans()) {
            if (c.name != null && !c.name.isBlank()) {
                out.add(c.name.contains(" ") ? "\"" + c.name + "\"" : c.name);
            }
        }
        return out;
    }

    private static List<String> memberNames(CommandSourceStack s) {
        List<String> out = new ArrayList<>();
        ServerPlayer p = s.getPlayer();
        Clan c = p == null ? null : mine(p);
        if (c != null) {
            for (UUID u : c.members.keySet()) {
                String n = ClanMessages.character(s.getServer(), c, u);
                out.add(n.contains(" ") ? "\"" + n + "\"" : n);
            }
        }
        return out;
    }

    private static List<String> roleIds(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        Clan c = p == null ? null : mine(p);
        return c == null ? List.of() : new ArrayList<>(c.roles.keySet());
    }

    private static List<String> openVotes(CommandSourceStack s) {
        ServerPlayer p = s.getPlayer();
        Clan c = p == null ? null : mine(p);
        List<String> out = new ArrayList<>();
        if (c != null) {
            ClanVotes.openOf(c.proposals).forEach(x -> out.add(x.id()));
        }
        return out;
    }

    // ------------------------------------------------------------------ joueurs
    private static int create(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        for (InteractionHand h : InteractionHand.values()) {
            if (p.getItemInHand(h).is(ClanItems.CHARTER.get())) {
                Charter.use(p, h);
                return 1;
            }
        }
        return fail(c, "clan.pioneers.cmd.need_charter");
    }

    private static int kick(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        UUID u = resolve(p.getServer(), clan, StringArgumentType.getString(c, "member"));
        if (u == null) {
            return fail(c, "clan.pioneers.error.not_member");
        }
        CompoundTag d = new CompoundTag();
        d.putUUID("target", u);
        return gov(c, ClanGov.decide(p, clan, "kick", d, ""));
    }

    private static int roleList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        for (Clan.Role r : ClanRoles.ordered(clan)) {
            String disp = ClanRoles.displayName(r);
            Component roleName = ClanMessages.arg(disp) instanceof Component comp ? comp : Component.literal(disp);
            MutableComponent line = Component.literal(r.rank + "  ").append(roleName.copy().withStyle(ClanMessages.dye(r.color)))
                  .append(Component.literal("  (" + r.id + ")  ").withStyle(ChatFormatting.DARK_GRAY));
            List<String> perms = new ArrayList<>();
            for (Permission perm : r.perms) {
                perms.add(perm.id());
            }
            line.append(Component.literal(String.join(", ", perms)).withStyle(ChatFormatting.GRAY));
            c.getSource().sendSuccess(() -> line, false);
        }
        return 1;
    }

    private static int roleSet(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        UUID u = resolve(p.getServer(), clan, StringArgumentType.getString(c, "member"));
        if (u == null) {
            return fail(c, "clan.pioneers.error.not_member");
        }
        String role = StringArgumentType.getString(c, "role");
        String err = ClanRoles.assign(clan, p.getUUID(), u, role);
        if (err == null) {
            ClanNetwork.roleChanged(p, clan, u, role);
        }
        return result(c, err);
    }

    private static int roleEdit(CommandContext<CommandSourceStack> c, String op) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        CompoundTag d = new CompoundTag();
        d.putString("op", op);
        d.putString("role", StringArgumentType.getString(c, "id").toLowerCase(Locale.ROOT));
        switch (op) {
            case "create" -> {
                d.putInt("rank", IntegerArgumentType.getInteger(c, "rank"));
                d.putString("name", has(c, "name") ? StringArgumentType.getString(c, "name") : "");
            }
            case "perm" -> {
                d.putString("perm", StringArgumentType.getString(c, "perm"));
                d.putBoolean("on", BoolArgumentType.getBool(c, "on"));
            }
            case "rank" -> d.putInt("rank", IntegerArgumentType.getInteger(c, "rank"));
            case "cap" -> d.putLong("cap", LongArgumentType.getLong(c, "cap"));
            case "rename" -> d.putString("name", StringArgumentType.getString(c, "name"));
            default -> {
            }
        }
        return gov(c, ClanGov.decide(p, clan, "roles", d, ""));
    }

    private static boolean has(CommandContext<CommandSourceStack> c, String arg) {
        try {
            StringArgumentType.getString(c, arg);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static int voteList(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        List<Proposal> open = ClanVotes.openOf(clan.proposals);
        if (open.isEmpty()) {
            return fail(c, "clan.pioneers.cmd.no_votes");
        }
        long now = ClanClock.now();
        for (Proposal x : open) {
            Component line = Component.translatable("clan.pioneers.cmd.vote_line", x.id(), Component.translatable("clan.pioneers.vote.type." + x.type()),
                  x.text(), Math.max(0, (x.closes() - now) / ClanDefs.MINUTE), x.voted(p.getUUID()) ? Component.translatable(
                        "clan.pioneers.cmd.voted") : Component.translatable(x.electorate().containsKey(p.getUUID()) ? "clan.pioneers.cmd.to_vote"
                        : "clan.pioneers.cmd.not_elector"));
            c.getSource().sendSuccess(() -> line, false);
            if (x.election()) {
                List<String> names = new ArrayList<>();
                x.candidates().forEach(u -> names.add(ClanMessages.character(p.getServer(), clan, u)));
                c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.cmd.candidates", String.join(", ", names))
                      .withStyle(ChatFormatting.GRAY), false);
            }
        }
        return 1;
    }

    private static int vote(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        String id = StringArgumentType.getString(c, "id"), choice = StringArgumentType.getString(c, "choice").trim();
        Proposal x = ClanVotes.find(clan.proposals, id);
        if (x != null && x.election()) {
            UUID u = resolve(p.getServer(), clan, choice);
            if (u == null) {
                return fail(c, "clan.pioneers.error.vote_choice");
            }
            choice = u.toString();
        } else {
            choice = choice.toLowerCase(Locale.ROOT);
        }
        return result(c, ClanVotes.vote(p, ClanVotes.Scope.CLAN, clan.id, id, choice), "clan.pioneers.cmd.voted_ok");
    }

    private static int treasury(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        if (!ClanRoles.has(clan, p.getUUID(), Permission.TREASURY_VIEW)) {
            return fail(c, "clan.pioneers.error.permission");
        }
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.cmd.treasury", clan.treasury, clan.activityTax,
              ClanFinance.cap(clan, "activity"), clan.dues, ClanFinance.cap(clan, "dues"), clan.marketTax, ClanFinance.cap(clan, "market")), false);
        if (clan.frozen) {
            c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.error.frozen").withStyle(ChatFormatting.RED), false);
        }
        return 1;
    }

    private static int bonus(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        UUID u = resolve(p.getServer(), clan, StringArgumentType.getString(c, "member"));
        if (u == null) {
            return fail(c, "clan.pioneers.error.not_member");
        }
        return paid(c, ClanFinance.bonus(p, u, LongArgumentType.getLong(c, "amount")));
    }

    private static int tax(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        return gov(c, ClanFinance.setTax(p, clan, StringArgumentType.getString(c, "kind"), IntegerArgumentType.getInteger(c, "value")));
    }

    private static int heir(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        String name = StringArgumentType.getString(c, "member");
        UUID u = "-".equals(name) ? null : resolve(p.getServer(), clan, name);
        if (u == null && !"-".equals(name)) {
            return fail(c, "clan.pioneers.error.not_member");
        }
        return result(c, ClanGov.setHeir(p, clan, u));
    }

    private static int counter(CommandContext<CommandSourceStack> c, String type) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        return clan == null ? fail(c, "clan.pioneers.error.no_clan") : gov(c, ClanGov.counter(p, clan, type));
    }

    private static int identity(CommandContext<CommandSourceStack> c, String field) throws CommandSyntaxException {
        ServerPlayer p = player(c);
        Clan clan = mine(p);
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_clan");
        }
        CompoundTag d = new CompoundTag();
        d.putString("field", field);
        String v = StringArgumentType.getString(c, "value");
        d.putString("value", v);
        if ("color".equals(field)) {
            DyeColor dye = DyeColor.byName(v, null);
            if (dye == null) {
                return fail(c, "clan.pioneers.error.color");
            }
            d.putInt("color", dye.getId());
        }
        return gov(c, ClanGov.decide(p, clan, "identity", d, ""));
    }

    /** Fiche courte : la sienne (complete) ou celle d'un autre clan (publique : jamais un pseudo de compte). */
    private static int info(CommandContext<CommandSourceStack> c, @Nullable String name) {
        MinecraftServer server = c.getSource().getServer();
        ServerPlayer p = c.getSource().getPlayer();
        Clan clan = name == null ? p == null ? null : mine(p) : byName(server, name);
        if (clan == null) {
            return fail(c, name == null ? "clan.pioneers.error.no_clan" : "clan.pioneers.error.no_such_clan");
        }
        long now = ClanClock.now();
        Component title = ClanMessages.tagged(clan, ClanMessages.name(server, clan).copy().withStyle(ClanMessages.dye(clan.color)));
        c.getSource().sendSuccess(() -> title, false);
        if (!clan.motto.isBlank()) {
            c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.quoted", clan.motto).withStyle(ChatFormatting.ITALIC,
                  ChatFormatting.GRAY), false);
        }
        c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.cmd.info", Component.translatable("clan.pioneers.regime."
              + (clan.solo ? ClanDefs.SOLO : clan.regime)), clan.level, clan.members.size(), ClanCore.maxMembers(clan), ClanMessages.character(server,
              clan, clan.representative)), false);
        boolean own = p != null && clan.members.containsKey(p.getUUID());
        if (own) {
            c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.cmd.info_own", ClanLogic.activeCount(clan, now, ClanCore.activeMs()),
                  clan.claimCount(), ClanVotes.openOf(clan.proposals).size()), false);
            if (!clan.pendingRegime.isEmpty()) {
                c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.cmd.info_pending", Component.translatable("clan.pioneers.regime."
                      + clan.pendingRegime), Math.max(0, (clan.regimeAt - now) / ClanDefs.HOUR)), false);
            }
            if (clan.extra.getBoolean(ClanLogic.CHOOSE_REGIME)) {
                c.getSource().sendSuccess(() -> Component.translatable("clan.pioneers.converted").withStyle(ChatFormatting.GOLD), false);
            }
        }
        return 1;
    }

    // ------------------------------------------------------------------ administration
    private static int adminDissolve(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Clan clan = byName(server, StringArgumentType.getString(c, "clan"));
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_such_clan");
        }
        ClanCore.dissolve(server, clan, c.getSource().getTextName());
        return result(c, null, "clan.pioneers.cmd.admin_done");
    }

    private static int adminRepresentative(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Clan clan = byName(server, StringArgumentType.getString(c, "clan"));
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_such_clan");
        }
        UUID u = resolve(server, clan, StringArgumentType.getString(c, "player"));
        if (u == null) {
            return fail(c, "clan.pioneers.error.not_member");
        }
        ClanCore.setRepresentative(server, clan, u, "clan.pioneers.reason.admin");
        ClanAudit.write(server, "admin", clan, "Representant impose par " + c.getSource().getTextName());
        return result(c, null, "clan.pioneers.cmd.admin_done");
    }

    private static int adminLevel(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Clan clan = byName(server, StringArgumentType.getString(c, "clan"));
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_such_clan");
        }
        int max = ClanDefs.get().levels.keySet().stream().mapToInt(Integer::intValue).max().orElse(10);
        int lvl = Math.min(max, IntegerArgumentType.getInteger(c, "level"));
        clan.level = lvl;
        ClanDefs.LevelDef d = ClanDefs.get().level(lvl);
        if (d != null) {
            clan.points = Math.max(clan.points, d.points());
        }
        clan.log(PioneerApi.day(server), "clan.pioneers.log.level", String.valueOf(lvl));
        ClanData.get(server).changed();
        ClanAudit.write(server, "admin", clan, "niveau fixe a " + lvl + " par " + c.getSource().getTextName());
        return result(c, null, "clan.pioneers.cmd.admin_done");
    }

    private static int adminFreeze(CommandContext<CommandSourceStack> c) {
        MinecraftServer server = c.getSource().getServer();
        Clan clan = byName(server, StringArgumentType.getString(c, "clan"));
        if (clan == null) {
            return fail(c, "clan.pioneers.error.no_such_clan");
        }
        clan.frozen = BoolArgumentType.getBool(c, "frozen");
        clan.log(PioneerApi.day(server), clan.frozen ? "clan.pioneers.log.frozen" : "clan.pioneers.log.unfrozen");
        ClanData.get(server).changed();
        ClanAudit.write(server, "admin", clan, (clan.frozen ? "tresor gele" : "tresor degele") + " par " + c.getSource().getTextName());
        return result(c, null, "clan.pioneers.cmd.admin_done");
    }

    private static int adminAudit(CommandContext<CommandSourceStack> c, int n) {
        List<String> lines = ClanAudit.tail(c.getSource().getServer(), n);
        if (lines.isEmpty()) {
            return fail(c, "clan.pioneers.cmd.audit_empty");
        }
        for (String l : lines) {
            c.getSource().sendSuccess(() -> Component.literal(l).withStyle(ChatFormatting.GRAY), false);
        }
        return 1;
    }
}
