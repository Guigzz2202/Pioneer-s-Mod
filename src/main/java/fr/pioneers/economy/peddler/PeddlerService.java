package fr.pioneers.economy.peddler;

import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import fr.pioneers.Pioneers;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.rep.ClanReputation;
import fr.pioneers.clan.seal.ClanSeals;
import fr.pioneers.common.PiCommonConfig;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpConfig;
import fr.pioneers.core.profile.RpSounds;
import fr.pioneers.economy.EconomyApi;
import fr.pioneers.economy.EconomyBlocks;
import fr.pioneers.economy.Values;
import fr.pioneers.reputation.ReputationApi;
import fr.pioneers.ruins.RuinApi;
import fr.pioneers.village.NeedsBoardBlock;
import fr.pioneers.village.VillageBlocks;
import fr.pioneers.village.VillageData;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.StructureTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.animal.horse.TraderLlama;
import net.minecraft.world.entity.npc.WanderingTrader;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.9 (MAJ RP, bloc 3) : le service qui choisit ou paraissent les Colporteurs. Un seul compteur espace (toutes les 100 ticks) :
 * <ul>
 * <li><b>route</b> : chaque matin de jeu, pour chaque joueur de l'Overworld non banni et sans Colporteur de route pres de lui, 20 % de
 * chances ; au sol, sur, a 24-40 blocs, de preference a moins de 48 blocs d'une ancienne Voie (tag {@code pioneers:ancient_ways}), jamais
 * dans une ruine ni sur l'eau ; clochettes des lamas ;</li>
 * <li><b>marchand errant</b> : un marchand errant neuf est remplace au tick suivant par un Colporteur, ses lamas passent a sa laisse
 * (reglage {@code peddlerReplacesVanilla}) ;</li>
 * <li><b>caravanes</b> : {@link #startCaravan} et {@link #endCaravan} pour le village (place de marche, deux Colporteurs) ;</li>
 * <li><b>Haltes</b> : un camp permanent sur une ancienne Voie, une par 1 500 blocs environ, deux Colporteurs qui reviennent 20 jours
 * apres une mort.</li>
 * </ul>
 * Tuer un Colporteur coute la reputation de l'acte {@code kill_peddler} et un bannissement de 20 jours (donnee persistante du joueur) :
 * plus de Colporteur de route, aucune caravane a moins de 500 blocs de lui. Les chunks non charges sont ignores.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID)
public final class PeddlerService {

    /** Blocs d'ancienne Voie (rails du mod, chemins de terre, quais). */
    public static final TagKey<Block> WAYS = TagKey.create(Registries.BLOCK, Pioneers.rl("ancient_ways"));
    /** Cle de la donnee persistante du joueur : jour de fin du bannissement (exclu). */
    public static final String BAN_KEY = "pioneers_peddler_ban";
    /** Rayon ou un banni empeche le remplacement d'un marchand errant. */
    static final int BAN_ROUTE_RADIUS = 96;

    private record Pending(ResourceKey<Level> dim, UUID id, long time) {
    }

    private static final List<Pending> PENDING = new ArrayList<>();
    private static final Map<UUID, Long> HALT_COOLDOWN = new HashMap<>();

    private PeddlerService() {
    }

    /** Appele par {@code EconomyBootstrap.init} : les evenements sont abonnes par l'annotation. */
    public static void init() {
    }

    // ------------------------------------------------------------------ reglages (data/pioneers/economy/partners.json, sinon la spec)
    public static double routeChance() {
        return Values.setting("peddler_route_chance", PeddlerRules.ROUTE_CHANCE);
    }

    public static int stayDays() {
        return (int) Values.setting("peddler_stay_days", PeddlerRules.STAY_DAYS);
    }

    public static long stayTicks() {
        return stayDays() * 24_000L;
    }

    public static int banDays() {
        return (int) Values.setting("peddler_ban_days", PeddlerRules.BAN_DAYS);
    }

    public static int banRadius() {
        return (int) Values.setting("peddler_ban_radius", PeddlerRules.BAN_RADIUS);
    }

    public static int haltSpacing() {
        return (int) Values.setting("peddler_halt_spacing", PeddlerRules.HALT_SPACING);
    }

    public static int haltRespawnDays() {
        return (int) Values.setting("peddler_halt_respawn_days", PeddlerRules.HALT_RESPAWN_DAYS);
    }

    public static long caravanTotal() {
        return (long) Values.setting("peddler_caravan_total", PeddlerRules.CARAVAN_TOTAL);
    }

    public static long actTotal() {
        return (long) Values.setting("peddler_act_total", PeddlerRules.ACT_TOTAL);
    }

    // ------------------------------------------------------------------ bannissement
    /** Jour (exclu) de fin du bannissement du joueur, 0 s'il n'y en a pas. */
    public static long banUntil(Player player) {
        return player.getPersistentData().getCompound(Player.PERSISTED_NBT_TAG).getLong(BAN_KEY);
    }

    static void setBan(Player player, long untilDay) {
        CompoundTag root = player.getPersistentData();
        CompoundTag pers = root.getCompound(Player.PERSISTED_NBT_TAG);
        if (untilDay <= 0) {
            pers.remove(BAN_KEY);
        } else {
            pers.putLong(BAN_KEY, untilDay);
        }
        root.put(Player.PERSISTED_NBT_TAG, pers);
    }

    /** Le joueur a-t-il tue un Colporteur il y a moins de 20 jours de jeu (plus de Colporteur de route pour lui) ? */
    public static boolean banned(ServerPlayer player) {
        return PeddlerRules.banActive(banUntil(player), PioneerApi.day(player.getServer()));
    }

    /** Un joueur banni se trouve-t-il a moins de 500 blocs (horizontaux) de ce point ? Aucune caravane ne s'y arrete alors. */
    public static boolean bannedNear(ServerLevel level, BlockPos pos) {
        return bannedWithin(level, pos, banRadius());
    }

    static boolean bannedWithin(ServerLevel level, BlockPos pos, int radius) {
        for (ServerPlayer p : level.players()) {
            if (banned(p) && PeddlerRules.within(p.getX() - pos.getX() - 0.5, p.getZ() - pos.getZ() - 0.5, radius)) {
                return true;
            }
        }
        return false;
    }

    // ------------------------------------------------------------------ apparition
    /** Fait paraitre un Colporteur et ses deux lamas (ancrage = sa position). */
    @Nullable
    static PeddlerEntity spawn(ServerLevel level, BlockPos pos, BlockPos anchor, PeddlerEntity.Role role, long group, long since, long leaveAt,
          @Nullable UUID target) {
        PeddlerEntity e = PeddlerTypes.PEDDLER.get().create(level);
        if (e == null) {
            return null;
        }
        e.moveTo(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5, level.random.nextFloat() * 360.0F, 0.0F);
        e.setup(role, group, anchor, since, leaveAt, target);
        if (!level.addFreshEntity(e)) {
            return null;
        }
        spawnLlamas(level, e);
        return e;
    }

    /** Deux lamas de marchand vanilla en laisse a cote du Colporteur. */
    static void spawnLlamas(ServerLevel level, PeddlerEntity e) {
        for (int i = 0; i < 2; i++) {
            TraderLlama l = EntityType.TRADER_LLAMA.create(level);
            if (l == null) {
                continue;
            }
            double a = level.random.nextDouble() * Math.PI * 2;
            double x = e.getX() + Math.cos(a) * 1.6, z = e.getZ() + Math.sin(a) * 1.6;
            if (!level.noCollision(l, l.getType().getAABB(x, e.getY(), z))) {
                x = e.getX();
                z = e.getZ();
            }
            l.moveTo(x, e.getY(), z, level.random.nextFloat() * 360.0F, 0.0F);
            l.finalizeSpawn(level, level.getCurrentDifficultyAt(BlockPos.containing(x, e.getY(), z)), MobSpawnType.EVENT, null, null);
            if (level.addFreshEntity(l)) {
                l.setLeashedTo(e, true);
                e.addLlama(l.getUUID());
            }
        }
    }

    /** Colporteur de route pres du joueur : 24-40 blocs, au sol, pres d'une ancienne Voie si possible ; clochettes des lamas. */
    @Nullable
    public static PeddlerEntity spawnRoute(ServerLevel level, ServerPlayer player) {
        return spawnNear(level, player.getBlockX(), player.getBlockZ(), player.getUUID());
    }

    /** Colporteur de route pres d'un point (joueur, ou Marche d'un clan : cible nulle). */
    @Nullable
    static PeddlerEntity spawnNear(ServerLevel level, int px, int pz, @Nullable UUID target) {
        LevelTerrain t = new LevelTerrain(level);
        List<int[]> ways = PeddlerRules.scanWays(t, px, pz, PeddlerRules.ROUTE_MAX + PeddlerRules.WAY_NEAR);
        int[] s = PeddlerRules.routeSpot(t, px, pz, ways, new Random(level.random.nextLong()), 32);
        if (s == null) {
            return null;
        }
        BlockPos pos = new BlockPos(s[0], s[1], s[2]);
        if (avoided(level, pos)) {
            return null;
        }
        long now = level.getGameTime();
        PeddlerEntity e = spawn(level, pos, pos, PeddlerEntity.Role.ROUTE, 0, now, now + stayTicks(), target);
        if (e != null) {
            level.playSound(null, pos, RpSounds.LLAMA_BELLS.get(), SoundSource.NEUTRAL, 2.0F, 1.0F);
        }
        return e;
    }

    /** Le matin : un tirage par joueur de l'Overworld (20 %), au plus un Colporteur de route par joueur et par jour. */
    static void morning(ServerLevel level) {
        if (!EconomyApi.enabled()) {
            return;
        }
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.isSpectator() || banned(p) || routeNear(level, p)) {
                continue;
            }
            if (level.random.nextDouble() < routeChance()) {
                spawnRoute(level, p);
            }
        }
        marketStops(level);
    }

    /**
     * 1.9 (MAJ RP, bloc 4) : arrets au Marche des clans. Selon le palier du clan chez les Colporteurs (Estime : un jour sur cinq, Allie : un
     * sur trois ; donnees clans/reputation.json), un Colporteur de route vient pres du premier Marche du clan, si son chunk est charge et
     * qu'aucun Colporteur n'y est deja.
     */
    static void marketStops(ServerLevel level) {
        MinecraftServer server = level.getServer();
        if (server == null || !ClanApi.enabled()) {
            return;
        }
        long day = PioneerApi.day(server);
        String dim = ClanApi.dim(level);
        for (Clan c : List.copyOf(ClanData.get(server).clans())) {
            if (c.solo || c.members.isEmpty() || !ClanReputation.peddlerStopsToday(server, c, day)) {
                continue;
            }
            String key = ClanSeals.first(c, ClanSeals.MARKET);
            BlockPos at = key == null || !dim.equals(ClanSeals.dimOf(key)) ? null : ClanSeals.posOf(key);
            if (at == null || !level.isLoaded(at)
                  || !level.getEntitiesOfClass(PeddlerEntity.class, new AABB(at).inflate(48), PeddlerEntity::isAlive).isEmpty()) {
                continue;
            }
            spawnNear(level, at.getX(), at.getZ(), null);
        }
    }

    /** 1.9 (MAJ RP, bloc 4) : un clan Hostile chez les Colporteurs : aucun Colporteur ne s'arrete pres de son Marche. */
    static boolean avoided(ServerLevel level, BlockPos pos) {
        if (level.getServer() == null || !ClanApi.enabled()) {
            return false;
        }
        Clan c = ClanApi.owner(level.getServer(), level, pos);
        return c != null && ClanReputation.peddlersAvoid(c) && ClanSeals.near(c, ClanApi.dim(level), pos, ClanSeals.MARKET);
    }

    /** Un Colporteur de route est-il deja la (charge, a moins de 128 blocs, ou venu pour ce joueur) ? */
    static boolean routeNear(ServerLevel level, ServerPlayer p) {
        return !level.getEntitiesOfClass(PeddlerEntity.class, p.getBoundingBox().inflate(128),
              e -> e.role() == PeddlerEntity.Role.ROUTE && e.isAlive()).isEmpty();
    }

    // ------------------------------------------------------------------ cycle de vie (appele par l'entite)
    /** Le Colporteur part (fin de route, de caravane, Halte retiree). */
    static void departed(ServerLevel level, PeddlerEntity e) {
        PeddlerData d = PeddlerData.get(level);
        if (e.role() == PeddlerEntity.Role.ROUTE) {
            d.forget(e.tradeGroup());
        } else if (e.role() == PeddlerEntity.Role.CARAVAN) {
            PeddlerData.Caravan c = d.caravans.get(e.group());
            if (c != null && c.peddlers.remove(e.getUUID())) {
                d.setDirty();
            }
        }
    }

    /** Le Colporteur est mort ; {@code killer} = le joueur qui l'a tue (reputation, bannissement). */
    static void killed(ServerLevel level, PeddlerEntity e, @Nullable ServerPlayer killer) {
        long day = PioneerApi.day(level);
        if (killer != null && !killer.isSpectator()) {
            ReputationApi.applyAct(killer, "kill_peddler", null, null);
            setBan(killer, Math.max(banUntil(killer), day + banDays()));
            killer.displayClientMessage(Component.translatable("peddler.pioneers.banned", String.valueOf(banDays())), false);
        }
        PeddlerData d = PeddlerData.get(level);
        switch (e.role()) {
            case ROUTE -> d.forget(e.tradeGroup());
            case CARAVAN -> {
                PeddlerData.Caravan c = d.caravans.get(e.group());
                if (c != null && c.peddlers.remove(e.getUUID())) {
                    d.setDirty();
                }
            }
            case HALT -> {
                PeddlerData.Halt h = d.halt(e.group());
                if (h != null) {
                    for (int s = 0; s < 2; s++) {
                        if (e.getUUID().equals(h.peddlers[s])) {
                            h.peddlers[s] = null;
                            h.back[s] = day + haltRespawnDays();
                            h.missing[s] = 0;
                            d.setDirty();
                        }
                    }
                }
            }
        }
    }

    /** La caravane du village est-elle toujours la (meme debut, pas finie) ? */
    public static boolean caravanActive(ServerLevel level, long villageId, long since) {
        PeddlerData.Caravan c = PeddlerData.get(level).caravans.get(villageId);
        return c != null && !c.ended && c.start == since && level.getGameTime() < c.until;
    }

    /** La Halte existe-t-elle et garde-t-elle ce Colporteur ? */
    static boolean haltKeeps(ServerLevel level, long haltId, UUID peddler) {
        PeddlerData.Halt h = PeddlerData.get(level).halt(haltId);
        return h != null && (peddler.equals(h.peddlers[0]) || peddler.equals(h.peddlers[1]));
    }

    // ------------------------------------------------------------------ compteur espace
    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!PENDING.isEmpty()) {
            replacePending(level);
        }
        long now = level.getGameTime();
        if (now % 100 != 0) {
            return;
        }
        PeddlerData d = PeddlerData.get(level);
        upkeepCaravans(level, d);
        if (level.dimension() != Level.OVERWORLD) {
            return;
        }
        long day = PioneerApi.day(level);
        if (day != d.lastDay) {
            boolean first = d.lastDay < 0;
            d.lastDay = day;
            d.forgetHaltTotals(day);
            d.setDirty();
            if (!first) {
                morning(level);
            }
        }
        upkeepHalts(level, d, day);
        if (now % 1200 == 0) {
            seekHalts(level, d);
        }
    }

    /** Fin du serveur : files et attentes oubliees (un autre monde peut suivre dans la meme partie). */
    @SubscribeEvent
    public static void onStopping(ServerStoppingEvent event) {
        PENDING.clear();
        HALT_COOLDOWN.clear();
    }

    // ------------------------------------------------------------------ remplacement du marchand errant
    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.loadedFromDisk() || !(event.getLevel() instanceof ServerLevel level) || event.getEntity().getType() != EntityType.WANDERING_TRADER) {
            return;
        }
        if (!PiCommonConfig.get(RpConfig.PEDDLER_REPLACE_VANILLA, true) || !EconomyApi.enabled()) {
            return;
        }
        PENDING.add(new Pending(level.dimension(), event.getEntity().getUUID(), level.getGameTime()));
    }

    /** Au tick suivant (ses lamas sont alors la) : le marchand errant neuf devient un Colporteur au meme endroit, lamas compris. */
    static void replacePending(ServerLevel level) {
        Iterator<Pending> it = PENDING.iterator();
        while (it.hasNext()) {
            Pending p = it.next();
            if (p.dim() != level.dimension()) {
                if (Math.abs(level.getGameTime() - p.time()) > 200) {
                    it.remove();
                }
                continue;
            }
            it.remove();
            if (!(level.getEntity(p.id()) instanceof WanderingTrader trader) || !trader.isAlive()) {
                continue;
            }
            List<TraderLlama> llamas = level.getEntitiesOfClass(TraderLlama.class, trader.getBoundingBox().inflate(16),
                  l -> l.getLeashHolder() == trader);
            if (bannedWithin(level, trader.blockPosition(), BAN_ROUTE_RADIUS)) {
                for (TraderLlama l : llamas) {
                    l.dropLeash(true, false);
                    l.discard();
                }
                trader.discard();
                continue;
            }
            BlockPos pos = trader.blockPosition();
            long now = level.getGameTime();
            PeddlerEntity e = PeddlerTypes.PEDDLER.get().create(level);
            if (e == null) {
                continue;
            }
            Player near = level.getNearestPlayer(trader, 64);
            e.moveTo(trader.getX(), trader.getY(), trader.getZ(), trader.getYRot(), 0.0F);
            e.setup(PeddlerEntity.Role.ROUTE, 0, pos, now, now + stayTicks(), near != null ? near.getUUID() : null);
            if (!level.addFreshEntity(e)) {
                continue;
            }
            for (TraderLlama l : llamas) {
                l.setLeashedTo(e, true);
                e.addLlama(l.getUUID());
            }
            if (llamas.isEmpty()) {
                spawnLlamas(level, e);
            }
            trader.discard();
        }
    }

    // ------------------------------------------------------------------ caravanes
    /**
     * Installe une caravane pres du Tableau des besoins {@code board} du village {@code villageId} jusqu'a {@code untilGameTime} (temps de
     * jeu) : une petite place de marche (une tente en blocs vanilla, des caisses, sur des blocs libres ou remplacables seulement) et deux
     * Colporteurs ; clochettes des lamas. Refuse (faux) si l'economie est coupee, si un joueur banni est a moins de 500 blocs, si le
     * Tableau n'est pas charge ou si la fin est passee. Une caravane deja la est prolongee.
     */
    public static boolean startCaravan(ServerLevel level, BlockPos board, long villageId, long untilGameTime) {
        long now = level.getGameTime();
        if (!EconomyApi.enabled() || untilGameTime <= now || !level.isLoaded(board) || bannedNear(level, board)) {
            return false;
        }
        PeddlerData d = PeddlerData.get(level);
        PeddlerData.Caravan old = d.caravans.get(villageId);
        if (old != null && !old.ended) {
            old.until = Math.max(old.until, untilGameTime);
            d.setDirty();
            return true;
        }
        if (old != null) {
            endCaravan(level, villageId);
        }
        LevelTerrain t = new LevelTerrain(level);
        Random rnd = new Random(level.random.nextLong());
        List<PeddlerRules.Piece> plan = PeddlerRules.caravanPlan();
        PeddlerRules.Site site = PeddlerRules.findSite(t, plan, board.getX(), board.getZ(), 3, 10, board.getX(), board.getZ(), rnd, 64, null);
        PeddlerData.Caravan c = d.addCaravan(villageId, board.immutable(), untilGameTime, now);
        List<BlockPos> spots = new ArrayList<>();
        BlockPos anchor = board;
        if (site != null) {
            anchor = new BlockPos(site.x(), site.y(), site.z());
            spots.addAll(build(level, PeddlerRules.place(plan, site.x(), site.y(), site.z(), site.q()), c.blocks));
        }
        for (int i = 0; spots.size() < 2 && i < 24; i++) {
            int x = board.getX() + rnd.nextInt(9) - 4, z = board.getZ() + rnd.nextInt(9) - 4;
            int y = PeddlerRules.standable(t, x, z);
            if (y != PeddlerRules.NONE) {
                spots.add(new BlockPos(x, y, z));
            }
        }
        if (spots.isEmpty()) {
            spots.add(board.above());
        }
        for (int i = 0; i < 2; i++) {
            PeddlerEntity e = spawn(level, spots.get(i % spots.size()), anchor, PeddlerEntity.Role.CARAVAN, villageId, now, untilGameTime, null);
            if (e != null) {
                c.peddlers.add(e.getUUID());
            }
        }
        level.playSound(null, board, RpSounds.LLAMA_BELLS.get(), SoundSource.NEUTRAL, 3.0F, 0.95F);
        d.setDirty();
        return true;
    }

    /**
     * Fin de la caravane du village : tentes et caisses retirees (seulement les blocs encore identiques ; ceux des troncons non charges le
     * seront a leur chargement), Colporteurs repartis ("Bon chemin."). Sans effet s'il n'y a pas de caravane.
     */
    public static void endCaravan(ServerLevel level, long villageId) {
        PeddlerData d = PeddlerData.get(level);
        PeddlerData.Caravan c = d.caravans.remove(villageId);
        if (c == null) {
            return;
        }
        c.ended = true;
        unbuild(level, c.blocks, d);
        for (UUID u : c.peddlers) {
            if (level.getEntity(u) instanceof PeddlerEntity p && p.isAlive()) {
                p.depart(level);
            }
        }
        d.forget("c:" + c.village + ":" + c.start);
        d.setDirty();
    }

    /** Une caravane est-elle en cours pour ce village ? */
    public static boolean hasCaravan(ServerLevel level, long villageId) {
        PeddlerData.Caravan c = PeddlerData.get(level).caravans.get(villageId);
        return c != null && !c.ended && level.getGameTime() < c.until;
    }

    /** Caravanes arrivees a leur terme (le village n'a pas appele {@link #endCaravan}) ; blocs restants des troncons enfin charges. */
    static void upkeepCaravans(ServerLevel level, PeddlerData d) {
        long now = level.getGameTime();
        for (PeddlerData.Caravan c : List.copyOf(d.caravans.values())) {
            if (now >= c.until) {
                endCaravan(level, c.village);
            }
        }
        if (!d.leftovers.isEmpty()) {
            List<PeddlerData.Laid> still = new ArrayList<>();
            for (PeddlerData.Laid b : d.leftovers) {
                if (level.isLoaded(b.pos())) {
                    if (level.getBlockState(b.pos()).is(b.block())) {
                        level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 3);
                    }
                } else {
                    still.add(b);
                }
            }
            if (still.size() != d.leftovers.size()) {
                d.leftovers.clear();
                d.leftovers.addAll(still);
                d.setDirty();
            }
        }
    }

    // ------------------------------------------------------------------ Haltes
    /** Toutes les minutes : un joueur pres d'une ancienne Voie, sans Halte a moins de 1 500 blocs, en fait poser une (hors de sa vue). */
    static void seekHalts(ServerLevel level, PeddlerData d) {
        if (!EconomyApi.enabled()) {
            return;
        }
        long now = level.getGameTime();
        HALT_COOLDOWN.values().removeIf(t -> t < now);
        for (ServerPlayer p : List.copyOf(level.players())) {
            if (p.isSpectator() || banned(p) || HALT_COOLDOWN.containsKey(p.getUUID())) {
                continue;
            }
            if (!PeddlerRules.haltNeeded(haltCenters(d), p.getBlockX(), p.getBlockZ(), haltSpacing())) {
                continue;
            }
            if (buildHalt(level, d, p) == null) {
                HALT_COOLDOWN.put(p.getUUID(), now + 6_000);
            }
        }
    }

    static List<long[]> haltCenters(PeddlerData d) {
        List<long[]> out = new ArrayList<>();
        for (PeddlerData.Halt h : d.halts) {
            out.add(new long[]{h.center.getX(), h.center.getZ()});
        }
        return out;
    }

    /**
     * Pose une Halte pres d'une ancienne Voie vue autour du joueur (32 blocs) : terrain plat et libre a 7-20 blocs de la Voie, hors de la
     * vue du joueur si possible, jamais sur lui ; trois tentes, un feu de camp, des caisses, un Tableau des besoins ; deux Colporteurs.
     */
    @Nullable
    public static PeddlerData.Halt buildHalt(ServerLevel level, PeddlerData d, ServerPlayer player) {
        LevelTerrain t = new LevelTerrain(level);
        List<int[]> ways = PeddlerRules.scanWays(t, player.getBlockX(), player.getBlockZ(), PeddlerRules.WAY_SCAN);
        if (ways.isEmpty()) {
            return null;
        }
        Random rnd = new Random(level.random.nextLong());
        Vec3 look = player.getLookAngle();
        double[] eye = {player.getX(), player.getZ(), look.x, look.z};
        List<PeddlerRules.Piece> plan = PeddlerRules.haltPlan();
        for (int k = 0; k < 6; k++) {
            int[] w = ways.get(rnd.nextInt(ways.size()));
            PeddlerRules.Site s = PeddlerRules.findSite(t, plan, w[0], w[1], 7, 20, w[0], w[1], rnd, 40, eye);
            if (s == null || PeddlerRules.within(s.x() - player.getX(), s.z() - player.getZ(), 8)) {
                continue;
            }
            BlockPos center = new BlockPos(s.x(), s.y(), s.z());
            if (nearVillage(level, center)) {
                return null;
            }
            PeddlerData.Halt h = d.addHalt(center);
            List<BlockPos> spots = build(level, PeddlerRules.place(plan, s.x(), s.y(), s.z(), s.q()), h.blocks);
            h.spots.addAll(spots);
            long now = level.getGameTime();
            for (int i = 0; i < 2 && i < spots.size(); i++) {
                PeddlerEntity e = spawn(level, spots.get(i), center, PeddlerEntity.Role.HALT, h.id, now, -1, null);
                h.peddlers[i] = e != null ? e.getUUID() : null;
            }
            level.playSound(null, center, RpSounds.LLAMA_BELLS.get(), SoundSource.NEUTRAL, 2.0F, 0.9F);
            d.setDirty();
            return h;
        }
        return null;
    }

    /**
     * Le point est-il dans un village (registre des villages : cloche a moins de 128 blocs ; ou structure de village de Minecraft qui touche
     * ce troncon) ? Une Halte sert un joueur qui n'a pas encore de village a proximite : jamais dans un village.
     */
    static boolean nearVillage(ServerLevel level, BlockPos pos) {
        if (VillageData.get(level).nearest(pos, 128) != null) {
            return true;
        }
        Registry<Structure> reg = level.registryAccess().registryOrThrow(Registries.STRUCTURE);
        for (Structure st : level.structureManager().getAllStructuresAt(pos).keySet()) {
            if (reg.getResourceKey(st).flatMap(reg::getHolder).map(h -> h.is(StructureTags.VILLAGE)).orElse(false)) {
                return true;
            }
        }
        return false;
    }

    /** Retire une Halte : blocs encore identiques, Colporteurs (ils repartent quand la Halte n'existe plus). */
    public static boolean removeHalt(ServerLevel level, long haltId) {
        PeddlerData d = PeddlerData.get(level);
        PeddlerData.Halt h = d.halt(haltId);
        if (h == null) {
            return false;
        }
        d.halts.remove(h);
        unbuild(level, h.blocks, d);
        for (UUID u : h.peddlers) {
            if (u != null && level.getEntity(u) instanceof PeddlerEntity p && p.isAlive()) {
                p.depart(level);
            }
        }
        d.setDirty();
        return true;
    }

    /** Halte la plus proche a moins de {@code radius} blocs, ou null. */
    @Nullable
    public static PeddlerData.Halt nearestHalt(ServerLevel level, BlockPos pos, double radius) {
        PeddlerData.Halt best = null;
        double bd = radius * radius;
        for (PeddlerData.Halt h : PeddlerData.get(level).halts) {
            double dx = h.center.getX() - pos.getX(), dz = h.center.getZ() - pos.getZ(), dd = dx * dx + dz * dz;
            if (dd <= bd) {
                bd = dd;
                best = h;
            }
        }
        return best;
    }

    /**
     * Haltes pres d'un joueur (48 blocs, centre charge) : une place vide revient quand son jour est arrive ; un Colporteur introuvable
     * trois comptes de suite avec un joueur a 24 blocs est considere comme parti (il revient aussitot).
     */
    static void upkeepHalts(ServerLevel level, PeddlerData d, long day) {
        if (d.halts.isEmpty() || level.players().isEmpty() || !EconomyApi.enabled()) {
            return;
        }
        for (PeddlerData.Halt h : d.halts) {
            if (!level.isLoaded(h.center) || h.spots.isEmpty()) {
                continue;
            }
            double best = Double.MAX_VALUE;
            for (ServerPlayer p : level.players()) {
                double dx = p.getX() - h.center.getX(), dz = p.getZ() - h.center.getZ();
                best = Math.min(best, dx * dx + dz * dz);
            }
            if (best > 48 * 48) {
                continue;
            }
            for (int s = 0; s < 2; s++) {
                UUID u = h.peddlers[s];
                if (u == null) {
                    if (h.back[s] <= day) {
                        BlockPos at = h.spots.get(s % h.spots.size());
                        PeddlerEntity e = spawn(level, at, h.center, PeddlerEntity.Role.HALT, h.id, level.getGameTime(), -1, null);
                        if (e != null) {
                            h.peddlers[s] = e.getUUID();
                            h.back[s] = -1;
                            h.missing[s] = 0;
                            d.setDirty();
                        }
                    }
                } else if (best <= 24 * 24) {
                    Entity e = level.getEntity(u);
                    if (e == null || !e.isAlive()) {
                        if (++h.missing[s] >= 3) {
                            h.peddlers[s] = null;
                            h.back[s] = day;
                            h.missing[s] = 0;
                            d.setDirty();
                        }
                    } else {
                        h.missing[s] = 0;
                    }
                }
            }
        }
    }

    // ------------------------------------------------------------------ blocs
    /** Pose les pieces du plan (cases libres seulement, verifiees a nouveau) ; renvoie les places des Colporteurs. */
    static List<BlockPos> build(ServerLevel level, List<PeddlerRules.Placed> pieces, List<PeddlerData.Laid> laid) {
        List<BlockPos> spots = new ArrayList<>();
        for (PeddlerRules.Placed p : pieces) {
            BlockPos pos = new BlockPos(p.x(), p.y(), p.z());
            if (p.part() == PeddlerRules.Part.PEDDLER) {
                spots.add(pos);
                continue;
            }
            if (!p.part().placed()) {
                continue;
            }
            BlockState cur = level.getBlockState(pos);
            if (!(cur.isAir() || cur.canBeReplaced()) || !cur.getFluidState().isEmpty() || cur.hasBlockEntity()) {
                continue;
            }
            BlockState st = state(p.part(), Direction.from2DDataValue(p.facing() == 0 ? 2 : p.facing() == 1 ? 3 : p.facing() == 2 ? 0 : 1));
            if (level.setBlock(pos, st, 3)) {
                laid.add(new PeddlerData.Laid(pos, st.getBlock()));
            }
        }
        return spots;
    }

    /** Bloc vanilla (ou du mod) d'une piece, tourne vers {@code facing}. */
    static BlockState state(PeddlerRules.Part part, Direction facing) {
        return switch (part) {
            case CANVAS -> Blocks.WHITE_WOOL.defaultBlockState();
            case RIDGE -> Blocks.CYAN_WOOL.defaultBlockState();
            case FIRE -> Blocks.CAMPFIRE.defaultBlockState().setValue(CampfireBlock.LIT, true);
            case CRATE -> EconomyBlocks.PEDDLER_CRATE.getBlock().defaultBlockState().setValue(EconomyBlocks.CrateBlock.FACING, facing);
            case BOARD -> VillageBlocks.NEEDS_BOARD.getBlock().defaultBlockState().setValue(NeedsBoardBlock.FACING, facing);
            default -> Blocks.AIR.defaultBlockState();
        };
    }

    /** Retire les blocs poses encore identiques ; ceux des troncons non charges attendent dans {@code leftovers}. */
    static void unbuild(ServerLevel level, List<PeddlerData.Laid> blocks, PeddlerData d) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            PeddlerData.Laid b = blocks.get(i);
            if (!level.isLoaded(b.pos())) {
                d.leftovers.add(b);
            } else if (level.getBlockState(b.pos()).is(b.block())) {
                level.setBlock(b.pos(), Blocks.AIR.defaultBlockState(), 3);
            }
        }
        blocks.clear();
        d.setDirty();
    }

    // ------------------------------------------------------------------ terrain du monde (troncons charges seulement)
    /** Vue du monde pour {@link PeddlerRules} : un troncon non charge est inconnu (jamais charge pour un choix d'emplacement). */
    static final class LevelTerrain implements PeddlerRules.Terrain {

        private final ServerLevel level;
        private final BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();

        LevelTerrain(ServerLevel level) {
            this.level = level;
        }

        @Nullable
        private LevelChunk chunk(int x, int z) {
            return level.getChunkSource().getChunkNow(x >> 4, z >> 4);
        }

        @Override
        public int feet(int x, int z) {
            LevelChunk c = chunk(x, z);
            if (c == null) {
                return PeddlerRules.NONE;
            }
            //ChunkAccess.getHeight rend le bloc le plus haut : les pieds sont juste au-dessus
            return c.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, x & 15, z & 15) + 1;
        }

        @Override
        public boolean free(int x, int y, int z) {
            LevelChunk c = chunk(x, z);
            if (c == null || y < level.getMinBuildHeight() || y >= level.getMaxBuildHeight()) {
                return false;
            }
            BlockState s = c.getBlockState(m.set(x, y, z));
            return (s.isAir() || s.canBeReplaced()) && s.getFluidState().isEmpty() && !s.hasBlockEntity();
        }

        @Override
        public boolean wet(int x, int z) {
            LevelChunk c = chunk(x, z);
            int y = feet(x, z);
            if (c == null || y == PeddlerRules.NONE || y - 1 < level.getMinBuildHeight()) {
                return true;
            }
            BlockState g = c.getBlockState(m.set(x, y - 1, z));
            return !g.getFluidState().isEmpty() || g.getCollisionShape(level, m).isEmpty() || g.is(BlockTags.ICE) || g.is(BlockTags.LEAVES);
        }

        @Override
        public boolean ruin(int x, int z) {
            int y = feet(x, z);
            return RuinApi.siteAt(level, new BlockPos(x, y == PeddlerRules.NONE ? level.getSeaLevel() : y, z)) != null;
        }

        @Override
        public boolean way(int x, int z) {
            LevelChunk c = chunk(x, z);
            int y = feet(x, z);
            if (c == null || y == PeddlerRules.NONE) {
                return false;
            }
            return c.getBlockState(m.set(x, y - 1, z)).is(WAYS) || c.getBlockState(m.set(x, y, z)).is(WAYS);
        }
    }

    // ------------------------------------------------------------------ commandes d'operateur (essais en jeu)
    @SubscribeEvent
    public static void commands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("pioneer").requires(s -> s.hasPermission(2))
              .then(Commands.literal("peddler")
                    .then(Commands.literal("route").executes(PeddlerService::cmdRoute))
                    .then(Commands.literal("halt").executes(PeddlerService::cmdHalt))
                    .then(Commands.literal("removehalt").executes(PeddlerService::cmdRemoveHalt))
                    .then(Commands.literal("caravan").executes(PeddlerService::cmdCaravan))
                    .then(Commands.literal("endcaravans").executes(PeddlerService::cmdEnd))
                    .then(Commands.literal("unban").executes(PeddlerService::cmdUnban))));
    }

    private static int cmdRoute(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PeddlerEntity e = spawnRoute(p.serverLevel(), p);
        if (e == null) {
            ctx.getSource().sendFailure(Component.translatable("peddler.pioneers.cmd.route_none"));
            return 0;
        }
        BlockPos b = e.blockPosition();
        ctx.getSource().sendSuccess(() -> Component.translatable("peddler.pioneers.cmd.route", b.toShortString()), false);
        return 1;
    }

    private static int cmdHalt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PeddlerData.Halt h = buildHalt(p.serverLevel(), PeddlerData.get(p.serverLevel()), p);
        if (h == null) {
            ctx.getSource().sendFailure(Component.translatable("peddler.pioneers.cmd.halt_none", String.valueOf(PeddlerRules.WAY_SCAN)));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("peddler.pioneers.cmd.halt", h.center.toShortString()), false);
        return 1;
    }

    private static int cmdRemoveHalt(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        PeddlerData.Halt h = nearestHalt(p.serverLevel(), p.blockPosition(), 64);
        if (h == null || !removeHalt(p.serverLevel(), h.id)) {
            ctx.getSource().sendFailure(Component.translatable("peddler.pioneers.cmd.no_halt", "64"));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("peddler.pioneers.cmd.halt_removed", h.center.toShortString()), false);
        return 1;
    }

    /** Caravane d'essai a la position du joueur jusqu'au lendemain (identifiant de village negatif, jamais celui d'un vrai village). */
    private static int cmdCaravan(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        long id = -1 - (p.blockPosition().asLong() & 0x3FFF_FFFF_FFFF_FFFFL);
        if (!startCaravan(level, p.blockPosition(), id, level.getGameTime() + 24_000L)) {
            ctx.getSource().sendFailure(Component.translatable("peddler.pioneers.cmd.caravan_none", String.valueOf(banRadius())));
            return 0;
        }
        ctx.getSource().sendSuccess(() -> Component.translatable("peddler.pioneers.cmd.caravan"), false);
        return 1;
    }

    /** Retire les caravanes dont le Tableau est a moins de 64 blocs. */
    private static int cmdEnd(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        ServerLevel level = p.serverLevel();
        int n = 0;
        for (PeddlerData.Caravan c : List.copyOf(PeddlerData.get(level).caravans.values())) {
            if (c.board.closerThan(p.blockPosition(), 64)) {
                endCaravan(level, c.village);
                n++;
            }
        }
        int count = n;
        ctx.getSource().sendSuccess(() -> Component.translatable("peddler.pioneers.cmd.ended", String.valueOf(count)), false);
        return n;
    }

    private static int cmdUnban(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        ServerPlayer p = ctx.getSource().getPlayerOrException();
        setBan(p, 0);
        ctx.getSource().sendSuccess(() -> Component.translatable("peddler.pioneers.cmd.unban", p.getDisplayName()), false);
        return 1;
    }
}
