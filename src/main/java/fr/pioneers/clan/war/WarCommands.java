package fr.pioneers.clan.war;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.core.ClanClock;
import java.util.ArrayList;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

/**
 * 1.9 (MAJ RP, bloc 4) : commandes de la diplomatie et de la guerre, sous {@code /clan} (Brigadier fusionne ce noeud avec celui des
 * fondations et du territoire) : {@code treaty propose|accept|refuse|break|list}, {@code war declare|list}, {@code ceasefire},
 * {@code surrender}, {@code windows} ; administration {@code /clan admin war end|truce} (operateur de niveau 3). Tout est verifie cote
 * serveur ; les decisions suivent le regime du clan.
 */
public final class WarCommands {

    private static final SuggestionProvider<CommandSourceStack> CLANS = (ctx, b) -> {
        List<String> names = new ArrayList<>();
        for (Clan c : ClanData.get(ctx.getSource().getServer()).clans()) {
            if (!c.solo && c.name != null && !c.name.isBlank()) {
                names.add(c.name.contains(" ") ? "\"" + c.name + "\"" : c.name);
            }
        }
        return SharedSuggestionProvider.suggest(names, b);
    };
    private static final SuggestionProvider<CommandSourceStack> KINDS = (ctx, b) -> SharedSuggestionProvider.suggest(ClanDiplomacy.KINDS, b);

    private WarCommands() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("clan")
              .then(Commands.literal("treaty")
                    .then(Commands.literal("list").executes(WarCommands::treatyList))
                    .then(treaty("propose")).then(treaty("accept")).then(treaty("refuse")).then(treaty("break")))
              .then(Commands.literal("war")
                    .then(Commands.literal("list").executes(WarCommands::warList))
                    .then(Commands.literal("declare").then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
                          .then(Commands.argument("reason", StringArgumentType.greedyString()).executes(WarCommands::declare)))))
              .then(Commands.literal("ceasefire").then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
                    .executes(c -> other(c, "ceasefire"))))
              .then(Commands.literal("surrender").then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
                    .executes(c -> other(c, "surrender"))))
              .then(Commands.literal("windows").executes(WarCommands::windows)
                    .then(Commands.argument("first", IntegerArgumentType.integer(0, 23))
                          .then(Commands.argument("second", IntegerArgumentType.integer(0, 23)).executes(WarCommands::setWindows))))
              .then(Commands.literal("admin").requires(s -> s.hasPermission(3))
                    .then(Commands.literal("war")
                          .then(Commands.literal("end").then(Commands.argument("a", StringArgumentType.string()).suggests(CLANS)
                                .then(Commands.argument("b", StringArgumentType.string()).suggests(CLANS).executes(c -> admin(c, true)))))
                          .then(Commands.literal("truce").then(Commands.argument("a", StringArgumentType.string()).suggests(CLANS)
                                .then(Commands.argument("b", StringArgumentType.string()).suggests(CLANS).executes(c -> admin(c, false))))))));
    }

    private static com.mojang.brigadier.builder.LiteralArgumentBuilder<CommandSourceStack> treaty(String verb) {
        return Commands.literal(verb).then(Commands.argument("clan", StringArgumentType.string()).suggests(CLANS)
              .then(Commands.argument("kind", StringArgumentType.word()).suggests(KINDS).executes(c -> treaty(c, verb))));
    }

    // ------------------------------------------------------------------ aides
    @Nullable
    private static Clan mine(ServerPlayer p) {
        Clan c = ClanData.get(p.getServer()).clanOf(p.getUUID());
        if (c == null) {
            fail(p, "clan.pioneers.error.no_clan");
        }
        return c;
    }

    @Nullable
    private static Clan named(ServerPlayer p, MinecraftServer server, String name) {
        Clan c = ClanData.get(server).byName(name);
        if (c == null) {
            fail(p, "clan.pioneers.war.unknown_clan", name);
        }
        return c;
    }

    private static void fail(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args).withStyle(ChatFormatting.RED));
    }

    // ------------------------------------------------------------------ traites
    private static int treaty(CommandContext<CommandSourceStack> ctx, String verb) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        Clan other = clan == null ? null : named(p, p.getServer(), StringArgumentType.getString(ctx, "clan"));
        String kind = StringArgumentType.getString(ctx, "kind");
        if (clan == null || other == null) {
            return 0;
        }
        if (!ClanDiplomacy.KINDS.contains(kind)) {
            fail(p, "clan.pioneers.treaty.unknown");
            return 0;
        }
        String err = null;
        switch (verb) {
            case "propose" -> err = ClanDiplomacy.propose(p, clan, other, kind);
            case "refuse" -> err = ClanDiplomacy.refuse(p, clan, other, kind);
            case "accept" -> {
                err = ClanDiplomacy.checkAccept(p.getServer(), clan, acceptData(other, kind));
                if (err == null) {
                    ClanDiplomacy.accept(p, clan, other, kind);
                }
            }
            default -> {
                CompoundTag d = acceptData(other, kind);
                d.putString("action", WarDecisions.BREAK);
                err = ClanDiplomacy.checkBreak(p.getServer(), clan, d);
                if (err == null) {
                    ClanDiplomacy.breakTreaty(p, clan, other, kind);
                }
            }
        }
        if (err != null) {
            fail(p, err);
            return 0;
        }
        return 1;
    }

    private static CompoundTag acceptData(Clan other, String kind) {
        CompoundTag d = new CompoundTag();
        d.putString("action", WarDecisions.ACCEPT);
        d.putUUID("other", other.id);
        d.putString("kind", kind);
        return d;
    }

    private static int treatyList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        if (clan != null) {
            ClanDiplomacy.list(p, clan);
        }
        return clan == null ? 0 : 1;
    }

    // ------------------------------------------------------------------ guerre
    private static int declare(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        Clan other = clan == null ? null : named(p, p.getServer(), StringArgumentType.getString(ctx, "clan"));
        if (clan == null || other == null) {
            return 0;
        }
        ClanWar.declare(p, clan, other, StringArgumentType.getString(ctx, "reason"));
        return 1;
    }

    private static int other(CommandContext<CommandSourceStack> ctx, String what) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        Clan other = clan == null ? null : named(p, p.getServer(), StringArgumentType.getString(ctx, "clan"));
        if (clan == null || other == null) {
            return 0;
        }
        if ("ceasefire".equals(what)) {
            ClanWar.ceasefire(p, clan, other);
        } else {
            ClanWar.surrender(p, clan, other);
        }
        return 1;
    }

    private static int warList(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        if (clan == null) {
            return 0;
        }
        ListTag l = ClanWar.view(p.getServer(), clan);
        if (l.isEmpty()) {
            p.sendSystemMessage(Component.translatable("clan.pioneers.war.list_empty").withStyle(ChatFormatting.GRAY));
            return 1;
        }
        p.sendSystemMessage(Component.translatable("clan.pioneers.war.list_head").withStyle(ChatFormatting.GOLD));
        long now = ClanClock.now();
        for (int i = 0; i < l.size(); i++) {
            CompoundTag w = l.getCompound(i);
            String state = w.getString("state");
            long until = "notice".equals(state) ? w.getLong("start") : "truce".equals(state) ? w.getLong("truce") : 0;
            long hours = until > 0 ? Math.max(1, (until - now + WarRules.HOUR - 1) / WarRules.HOUR) : 0;
            p.sendSystemMessage(Component.translatable("clan.pioneers.war.line." + state, Component.translatable(w.getBoolean("attacker")
                  ? "clan.pioneers.war.side.attacker" : "clan.pioneers.war.side.defender"), w.getString("other"), w.getInt("captures"), hours));
        }
        return 1;
    }

    // ------------------------------------------------------------------ creneaux
    private static int windows(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        if (clan == null) {
            return 0;
        }
        long now = ClanClock.now();
        int h = WarRules.get().windowHours();
        int[] w = ClanWar.windows(clan, now);
        p.sendSystemMessage(Component.translatable("clan.pioneers.war.windows.show", ClanWar.hour(w[0]), ClanWar.hour(w[0] + h),
              ClanWar.hour(w.length > 1 ? w[1] : w[0]), ClanWar.hour((w.length > 1 ? w[1] : w[0]) + h),
              Component.translatable(ClanWar.inWindow(clan, now) ? "clan.pioneers.war.windows.open" : "clan.pioneers.war.windows.closed")));
        if (clan.pendingWindows != null && clan.windowsFrom > now) {
            int[] q = clan.pendingWindows;
            long days = Math.max(1, (clan.windowsFrom - now + WarRules.DAY - 1) / WarRules.DAY);
            p.sendSystemMessage(Component.translatable("clan.pioneers.war.windows.pending", ClanWar.hour(q[0]), ClanWar.hour(q.length > 1 ? q[1] : q[0]),
                  days).withStyle(ChatFormatting.GRAY));
        }
        return 1;
    }

    private static int setWindows(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        Clan clan = mine(p);
        if (clan == null) {
            return 0;
        }
        String err = ClanWar.setWindows(p, clan, IntegerArgumentType.getInteger(ctx, "first"), IntegerArgumentType.getInteger(ctx, "second"));
        if (err != null) {
            fail(p, err);
            return 0;
        }
        return 1;
    }

    // ------------------------------------------------------------------ administration
    private static int admin(CommandContext<CommandSourceStack> ctx, boolean end) {
        CommandSourceStack s = ctx.getSource();
        ClanData data = ClanData.get(s.getServer());
        Clan a = data.byName(StringArgumentType.getString(ctx, "a")), b = data.byName(StringArgumentType.getString(ctx, "b"));
        if (a == null || b == null) {
            s.sendFailure(Component.translatable("clan.pioneers.war.unknown_clan", a == null ? StringArgumentType.getString(ctx, "a")
                  : StringArgumentType.getString(ctx, "b")));
            return 0;
        }
        boolean ok = end ? ClanWar.adminEnd(s.getServer(), a, b) : ClanWar.adminLiftTruce(s.getServer(), a, b);
        if (!ok) {
            s.sendFailure(Component.translatable(end ? "clan.pioneers.war.none" : "clan.pioneers.war.no_truce"));
            return 0;
        }
        s.sendSuccess(() -> Component.translatable(end ? "clan.pioneers.war.admin_ended" : "clan.pioneers.war.admin_truce", a.name, b.name), true);
        return 1;
    }
}
