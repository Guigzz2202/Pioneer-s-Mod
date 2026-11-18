package fr.pioneers.world.fauna;

import com.mojang.brigadier.context.CommandContext;
import fr.pioneers.Pioneers;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TagsUpdatedEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.0.5-beta (lot 4) : branchements de la faune persistante.
 * <ul>
 *     <li>persistance : {@code MobSpawnEvent.AllowDespawn} refuse la disparition de toute bete inscrite (vanilla et mod, poissons compris) ;</li>
 *     <li>plus d'apparition continue au hasard : {@code SpawnPlacementCheck} refuse l'apparition NATURELLE des especes inscrites partout, et
 *     celle de la GENERATION dans l'Overworld (notre peuplement la remplace, budget partage) ; monstres et chauves-souris intacts ;</li>
 *     <li>compteurs : entree / sortie des betes ({@link FaunaCounter}) ;</li>
 *     <li>troncons neufs, passages ({@link FaunaZones}) ; commande {@code /pioneer fauna zone|map|pass|forget} (operateurs).</li>
 * </ul>
 * Mondes existants (§8) : rien n'est retire ; les betes deja la sont simplement comptees et ne disparaissent plus.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class FaunaEvents {

    private FaunaEvents() {
    }

    // ------------------------------------------------------------------ persistance, apparitions

    @SubscribeEvent
    public static void despawn(MobSpawnEvent.AllowDespawn event) {
        if (FaunaSettings.current().enabled() && FaunaRegistry.of(event.getEntity()) != null) {
            event.setResult(Event.Result.DENY);
        }
    }

    /** Vrai si l'apparition de ce type, pour cette raison, est coupee (le peuplement et le repeuplement la remplacent). */
    public static boolean blocked(net.minecraft.world.entity.EntityType<?> type, MobSpawnType reason, boolean overworld) {
        if (!FaunaSettings.current().enabled() || FaunaSpawner.ours || FaunaRegistry.of(type) == null) {
            return false;
        }
        return reason == MobSpawnType.NATURAL || reason == MobSpawnType.CHUNK_GENERATION && overworld;
    }

    @SubscribeEvent
    public static void spawnCheck(MobSpawnEvent.SpawnPlacementCheck event) {
        if (blocked(event.getEntityType(), event.getSpawnType(), event.getLevel().getLevel().dimension() == Level.OVERWORLD)) {
            event.setResult(Event.Result.DENY);
        }
    }

    // ------------------------------------------------------------------ compteurs

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void join(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && !event.isCanceled()) {
            Entity e = event.getEntity();
            FaunaSpecies s = FaunaRegistry.of(e);
            if (s != null) {
                FaunaCounter.of(level).join(e, s);
            }
        }
    }

    @SubscribeEvent
    public static void leave(EntityLeaveLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FaunaCounter.of(level).leave(event.getEntity());
        }
    }

    @SubscribeEvent
    public static void unload(LevelEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            FaunaCounter.forget(level);
        }
    }

    // ------------------------------------------------------------------ generation et passages

    @SubscribeEvent
    public static void chunk(ChunkEvent.Load event) {
        if (event.isNewChunk() && event.getLevel() instanceof ServerLevel level && level.dimension() == Level.OVERWORLD
            && FaunaSettings.current().enabled()) {
            FaunaZones.chunkGenerated(level, event.getChunk().getPos());
        }
    }

    @SubscribeEvent
    public static void tick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level) || level.dimension() != Level.OVERWORLD) {
            return;
        }
        if (level.getGameTime() % 20 == 0) {
            FaunaSettings.refresh();
        }
        FaunaSettings s = FaunaSettings.current();
        if (s.enabled()) {
            FaunaZones.tick(level, s);
        }
    }

    @SubscribeEvent
    public static void tags(TagsUpdatedEvent event) {
        FaunaPools.clear();
    }

    @SubscribeEvent
    public static void stopped(ServerStoppedEvent event) {
        FaunaZones.clear();
        FaunaPools.clear();
    }

    // ------------------------------------------------------------------ commande de diagnostic

    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("fauna")
                    .then(Commands.literal("zone").executes(FaunaEvents::zone))
                    .then(Commands.literal("map").executes(FaunaEvents::map))
                    .then(Commands.literal("pass").executes(FaunaEvents::pass))
                    .then(Commands.literal("forget").executes(FaunaEvents::forget))));
    }

    private static long here(CommandContext<CommandSourceStack> ctx) {
        BlockPos p = BlockPos.containing(ctx.getSource().getPosition());
        return FaunaRules.zoneKey(p.getX(), p.getZ());
    }

    private static boolean off(CommandContext<CommandSourceStack> ctx) {
        if (!FaunaSettings.current().enabled()) {
            ctx.getSource().sendFailure(Component.translatable("fauna.pioneers.cmd.off"));
            return true;
        }
        return false;
    }

    private static int zone(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        long z = here(ctx);
        FaunaData data = FaunaData.get(level);
        FaunaSettings s = FaunaSettings.current();
        int x0 = FaunaRules.origin(FaunaRules.zoneX(z)), z0 = FaunaRules.origin(FaunaRules.zoneZ(z));
        String state = !data.known(z) ? "unseen" : data.legacy(z) ? "legacy" : "new";
        MutableComponent head = Component.translatable("fauna.pioneers.cmd.head", FaunaRules.zoneX(z) + ", " + FaunaRules.zoneZ(z),
              x0 + ", " + z0, (x0 + FaunaRules.ZONE_SIZE - 1) + ", " + (z0 + FaunaRules.ZONE_SIZE - 1),
              Component.translatable("fauna.pioneers.cmd.state." + state));
        if (!FaunaZones.fullyLoaded(level, z)) {
            head.append(Component.translatable("fauna.pioneers.cmd.partial"));
        }
        ctx.getSource().sendSuccess(() -> head, false);
        int[] counts = FaunaCounter.of(level).groups(z), starts = data.starts(z);
        int season = FaunaZones.season(level);
        for (FaunaGroup g : FaunaGroup.ALL) {
            int i = g.ordinal(), start = starts == null ? -1 : starts[i], target = starts == null ? -1 : FaunaRules.target(start, g, season);
            boolean below = starts != null && FaunaRules.needs(counts[i], target, s.threshold());
            MutableComponent line = Component.translatable("fauna.pioneers.cmd.group", Component.translatable(g.key()), counts[i],
                  start < 0 ? "?" : String.valueOf(start), target < 0 ? "?" : String.valueOf(target), s.cap(g));
            if (below) {
                line.append(Component.translatable("fauna.pioneers.cmd.below").withStyle(ChatFormatting.GOLD));
            }
            ctx.getSource().sendSuccess(() -> line, false);
        }
        long next = Math.max(0, data.nextPass() - level.getGameTime()) / 20;
        ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.next", next, FaunaCounter.of(level).tracked(), FaunaAi.skipped),
              false);
        long[] last = FaunaZones.last(z);
        if (last != null) {
            long placed = 0;
            for (int i = 1; i < last.length; i++) {
                placed += last[i];
            }
            long ago = (level.getGameTime() - last[0]) / 20, n = placed;
            ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.last", ago, n), false);
        }
        return 1;
    }

    /** Carte 9 x 9 zones autour de soi : gris = pas vue, vert = tout va bien, orange = un groupe sous le seuil, X = soi. */
    private static int map(CommandContext<CommandSourceStack> ctx) {
        ServerLevel level = ctx.getSource().getLevel();
        long me = here(ctx);
        FaunaData data = FaunaData.get(level);
        FaunaSettings s = FaunaSettings.current();
        int season = FaunaZones.season(level);
        ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.map"), false);
        for (int dz = -4; dz <= 4; dz++) {
            MutableComponent row = Component.literal("");
            for (int dx = -4; dx <= 4; dx++) {
                long z = FaunaRules.pack(FaunaRules.zoneX(me) + dx, FaunaRules.zoneZ(me) + dz);
                int[] starts = data.starts(z);
                ChatFormatting colour = ChatFormatting.DARK_GRAY;
                String c = "□";
                if (starts != null) {
                    int[] counts = FaunaCounter.of(level).groups(z);
                    colour = ChatFormatting.GREEN;
                    c = data.legacy(z) ? "▣" : "■";
                    for (FaunaGroup g : FaunaGroup.ALL) {
                        if (FaunaRules.needs(counts[g.ordinal()], FaunaRules.target(starts[g.ordinal()], g, season), s.threshold())) {
                            colour = ChatFormatting.GOLD;
                        }
                    }
                }
                if (z == me) {
                    c = "X";
                    colour = ChatFormatting.WHITE;
                }
                row.append(Component.literal(c + " ").withStyle(colour));
            }
            ctx.getSource().sendSuccess(() -> row, false);
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.legend"), false);
        return 1;
    }

    private static int pass(CommandContext<CommandSourceStack> ctx) {
        if (off(ctx)) {
            return 0;
        }
        ServerLevel level = ctx.getSource().getLevel();
        long z = here(ctx);
        FaunaCounter.of(level).reconcile();
        FaunaZones.Pass p = FaunaZones.pass(level, z, FaunaSettings.current(), FaunaZones.Limits.REAL, null);
        StringBuilder sb = new StringBuilder();
        int total = 0;
        for (FaunaGroup g : FaunaGroup.ALL) {
            total += p.placed()[g.ordinal()];
        }
        List<FaunaGroup> plan = p.planned();
        for (FaunaGroup g : plan) {
            sb.append(sb.length() == 0 ? "" : ", ").append(g.id()).append(' ').append(p.placed()[g.ordinal()]);
        }
        int n = total;
        String what = sb.length() == 0 ? "-" : sb.toString();
        ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.pass", n, what), true);
        //1.0.5-beta (lot 6) : naissances de la reproduction sauvage lente
        StringBuilder bb = new StringBuilder();
        int born = 0;
        for (FaunaGroup g : FaunaGroup.ALL) {
            if (p.born()[g.ordinal()] > 0) {
                born += p.born()[g.ordinal()];
                bb.append(bb.length() == 0 ? "" : ", ").append(g.id()).append(' ').append(p.born()[g.ordinal()]);
            }
        }
        int b = born;
        String bw = bb.length() == 0 ? "-" : bb.toString();
        ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.born", b, bw), true);
        return n + born;
    }

    private static int forget(CommandContext<CommandSourceStack> ctx) {
        FaunaData.get(ctx.getSource().getLevel()).forget(here(ctx));
        ctx.getSource().sendSuccess(() -> Component.translatable("fauna.pioneers.cmd.forget"), true);
        return 1;
    }

    /** Pour les GameTests : zone d'un troncon. */
    public static long zoneOf(ChunkPos c) {
        return FaunaRules.zoneOfChunk(c.x, c.z);
    }
}
