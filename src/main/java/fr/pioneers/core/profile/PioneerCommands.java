package fr.pioneers.core.profile;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import fr.pioneers.Pioneers;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP) : commandes d'operateur (niveau 2) du profil de pionnier : {@code /pioneer profile <joueur> show|reset},
 * {@code /pioneer vocation <joueur> set <id>} (sans cout ni delai), {@code /pioneer skill <joueur> set <skill> <niveau>},
 * {@code /pioneer skill <joueur> add <skill> <xp>}.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PioneerCommands {

    private PioneerCommands() {
    }

    private static final SuggestionProvider<CommandSourceStack> SKILLS = (c, b) -> SharedSuggestionProvider.suggest(PioneerDefs.get().skills.keySet(), b);
    private static final SuggestionProvider<CommandSourceStack> VOCATIONS = (c, b) -> SharedSuggestionProvider.suggest(
          PioneerDefs.get().vocations.keySet(), b);

    @SubscribeEvent
    public static void register(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> d = event.getDispatcher();
        d.register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("profile").then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("show").executes(PioneerCommands::show))
                    .then(Commands.literal("reset").executes(PioneerCommands::reset))))
              .then(Commands.literal("vocation").then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("set").then(Commands.argument("id", StringArgumentType.word()).suggests(VOCATIONS)
                          .executes(PioneerCommands::vocation)))))
              .then(Commands.literal("skill").then(Commands.argument("player", EntityArgument.player())
                    .then(Commands.literal("set").then(Commands.argument("skill", StringArgumentType.word()).suggests(SKILLS)
                          .then(Commands.argument("level", IntegerArgumentType.integer(0, PioneerApi.MAX_LEVEL)).executes(c -> skill(c, true)))))
                    .then(Commands.literal("add").then(Commands.argument("skill", StringArgumentType.word()).suggests(SKILLS)
                          .then(Commands.argument("xp", DoubleArgumentType.doubleArg(-1_000_000, 1_000_000)).executes(c -> skill(c, false))))))));
    }

    private static int show(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        PioneerProfile p = PioneerCap.get(player);
        c.getSource().sendSuccess(() -> Component.translatable("pioneer.pioneers.cmd.show", player.getDisplayName(), p.name,
              Component.translatable(p.vocation.isEmpty() ? "pioneer.pioneers.unknown_vocation" : "vocation.pioneers." + p.vocation),
              Component.translatable(p.initiated ? "gui.yes" : "gui.no")), false);
        for (PioneerDefs.Skill s : PioneerDefs.get().orderedSkills()) {
            double xp = p.xp(s.id());
            if (xp > 0) {
                c.getSource().sendSuccess(() -> Component.literal("  ").append(Component.translatable("skill.pioneers." + s.id()))
                      .append(" : " + PioneerApi.levelOf(xp) + " (" + Math.round(xp) + " XP)"), false);
            }
        }
        return 1;
    }

    private static int reset(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        PioneerCap.get(player).copyFrom(new PioneerProfile());
        PioneerCap.get(player).name = player.getGameProfile().getName();
        ProfileSync.full(player);
        ProfileSync.identity(player);
        ProfileEvents.openInitiation(player);
        c.getSource().sendSuccess(() -> Component.translatable("pioneer.pioneers.cmd.reset", player.getDisplayName()), true);
        return 1;
    }

    private static int vocation(CommandContext<CommandSourceStack> c) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        String id = StringArgumentType.getString(c, "id");
        if (PioneerDefs.get().vocation(id) == null) {
            c.getSource().sendFailure(Component.translatable("pioneer.pioneers.cmd.unknown", id));
            return 0;
        }
        PioneerProfile p = PioneerCap.get(player);
        String before = p.vocation;
        p.vocation = id;
        p.journal(PioneerApi.day(player.getServer()), "pioneer.pioneers.journal.reconversion", "#vocation.pioneers." + id);
        p.touch();
        MinecraftForge.EVENT_BUS.post(new VocationChangedEvent(player, before, id));
        ProfileSync.full(player);
        ProfileSync.identity(player);
        c.getSource().sendSuccess(() -> Component.translatable("pioneer.pioneers.cmd.vocation", player.getDisplayName(),
              Component.translatable("vocation.pioneers." + id)), true);
        return 1;
    }

    private static int skill(CommandContext<CommandSourceStack> c, boolean set) throws CommandSyntaxException {
        ServerPlayer player = EntityArgument.getPlayer(c, "player");
        String skill = StringArgumentType.getString(c, "skill");
        if (!PioneerDefs.get().skills.containsKey(skill)) {
            c.getSource().sendFailure(Component.translatable("pioneer.pioneers.cmd.unknown", skill));
            return 0;
        }
        PioneerProfile p = PioneerCap.get(player);
        double xp = set ? PioneerApi.xpFor(IntegerArgumentType.getInteger(c, "level")) : Math.max(0, p.xp(skill) + DoubleArgumentType.getDouble(c, "xp"));
        p.xp.put(skill, xp);
        if (PioneerApi.levelOf(xp) >= PioneerApi.MAX_LEVEL) {
            p.masteries.add(skill);
        } else {
            p.masteries.remove(skill);
        }
        p.touch();
        ProfileSync.full(player);
        c.getSource().sendSuccess(() -> Component.translatable("pioneer.pioneers.cmd.skill", player.getDisplayName(),
              Component.translatable("skill.pioneers." + skill), PioneerApi.levelOf(xp)), true);
        return 1;
    }

}
