package fr.pioneers.society.clan.war;

import fr.pioneers.society.clan.ClaimIndex;
import fr.pioneers.society.clan.Clan;
import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.society.clan.ClanData;
import fr.pioneers.society.clan.Permission;
import fr.pioneers.society.clan.core.ClanClock;
import fr.pioneers.society.clan.core.ClanMessages;
import fr.pioneers.society.clan.core.ClanRoles;
import fr.pioneers.society.clan.territory.ClanTerritory;
import fr.pioneers.society.clan.treasury.ClanAudit;
import fr.pioneers.core.profile.PioneerApi;
import fr.pioneers.core.profile.RpSounds;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.9 (MAJ RP, bloc 4) : le siege. Un membre de l'agresseur (permission de siege) plante un Drapeau de siege sur un chunk ennemi qui touche
 * le territoire de son camp ou un chunk deja pris dans cette guerre, pendant un creneau du defenseur. La tuile du drapeau compte les joueurs
 * a 8 blocs toutes les 20 ticks ({@link SiegeFlagTile}) ; a 100 %, {@link #capture} fait passer le chunk a l'agresseur (si son budget le
 * permet ; sinon il est tenu le temps des scelles puis rendu libre), conteneurs scelles 24 heures pour l'ancien proprietaire, Sceaux du chunk
 * tombes. Le chunk de la Mairie n'est pris qu'apres la perte de la moitie du territoire ; 3 captures par jour et par guerre.
 */
public final class Siege {

    /** Drapeau plante (memoire, refaite au chargement des tuiles) : chunk "dimension|chunk" -&gt; drapeau. */
    record Flag(String dim, BlockPos pos, UUID war) {
    }

    static final Map<String, Flag> FLAGS = new HashMap<>();
    /** Derniere tentative de plantation par joueur (tick du serveur) : un clic sur un bloc et le clic dans le vide du meme geste. */
    private static final Map<UUID, Integer> LAST_TRY = new HashMap<>();

    private Siege() {
    }

    /** Ce que donne une plantation permise. */
    record Plan(War war, Clan attacker, Clan defender, String dim, long chunk) {
    }

    /** Resultat d'une verification : plan ou cle de refus. */
    record Check(@Nullable Plan plan, @Nullable String error, Object... args) {
    }

    public static boolean isSiegeFlag(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof SiegeFlagItem;
    }

    // ------------------------------------------------------------------ plantation
    static Check plan(ServerPlayer p, ServerLevel level, BlockPos pos) {
        MinecraftServer server = p.getServer();
        if (!ClanWar.enabled()) {
            return new Check(null, "clan.pioneers.war.disabled");
        }
        ClanData data = ClanData.get(server);
        Clan mine = data.clanOf(p.getUUID());
        if (mine == null || mine.solo) {
            return new Check(null, "clan.pioneers.siege.no_clan");
        }
        if (!ClanRoles.has(mine, p.getUUID(), Permission.SIEGE)) {
            return new Check(null, "clan.pioneers.error.permission");
        }
        String dim = ClanApi.dim(level);
        long chunk = ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4);
        UUID ownerId = ClaimIndex.owner(dim, chunk);
        Clan owner = ownerId == null ? null : data.clan(ownerId);
        if (owner == null || owner.id.equals(mine.id)) {
            return new Check(null, "clan.pioneers.siege.not_enemy");
        }
        long now = ClanClock.now();
        War war = ClanWar.openAgainst(server, mine, owner, now);
        if (war == null) {
            return new Check(null, ClanWar.live(server, mine, owner) != null ? "clan.pioneers.siege.notice" : "clan.pioneers.siege.no_war");
        }
        WarRules.Values v = WarRules.get();
        if (!ClanWar.inWindow(owner, now)) {
            int[] w = ClanWar.windows(owner, now);
            return new Check(null, "clan.pioneers.siege.window", owner.name, ClanWar.hour(w[0]), ClanWar.hour(w.length > 1 ? w[1] : w[0]));
        }
        if (FLAGS.containsKey(War.chunkKey(dim, chunk))) {
            return new Check(null, "clan.pioneers.siege.flag_here");
        }
        if (!touches(server, war, dim, chunk)) {
            return new Check(null, "clan.pioneers.siege.not_adjacent");
        }
        long day = ClanWar.realDay(now);
        if (war.capturesOn(day) + flagsOf(war.id()) >= v.capturesPerDay()) {
            return new Check(null, "clan.pioneers.siege.daily", v.capturesPerDay());
        }
        long hall = townHallChunk(owner, dim);
        if (hall == chunk && !WarRules.townHallOpen(war.bSize(), war.lost(), v.townHallShare())) {
            return new Check(null, "clan.pioneers.siege.town_hall", (int) Math.ceil(war.bSize() * v.townHallShare()) - war.lost());
        }
        return new Check(new Plan(war, mine, owner, dim, chunk), null);
    }

    /** Le chunk touche-t-il (cote ou coin) le territoire du camp attaquant, ou un chunk deja pris dans cette guerre ? */
    static boolean touches(MinecraftServer server, War war, String dim, long chunk) {
        ClanData data = ClanData.get(server);
        int cx = ChunkPos.getX(chunk), cz = ChunkPos.getZ(chunk);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) {
                    continue;
                }
                long n = ChunkPos.asLong(cx + dx, cz + dz);
                if (war.taken(War.chunkKey(dim, n))) {
                    return true;
                }
                UUID o = ClaimIndex.owner(dim, n);
                Clan c = o == null ? null : data.clan(o);
                if (c != null && war.attacker(c)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Chunk de la Mairie du clan dans cette dimension (fonction {@code town_hall}, cle "dimension|position"), ou {@link Long#MIN_VALUE}. */
    static long townHallChunk(Clan c, String dim) {
        for (Map.Entry<String, String> e : c.functions.entrySet()) {
            if (!"town_hall".equals(e.getValue())) {
                continue;
            }
            BlockPos p = functionPos(e.getKey(), dim);
            if (p != null) {
                return ChunkPos.asLong(p.getX() >> 4, p.getZ() >> 4);
            }
        }
        return Long.MIN_VALUE;
    }

    /** Position d'une cle de fonction "dimension|position" de cette dimension (position en {@code BlockPos.asLong}). */
    @Nullable
    static BlockPos functionPos(String key, String dim) {
        int bar = key.lastIndexOf('|');
        if (bar <= 0 || !key.substring(0, bar).equals(dim)) {
            return null;
        }
        try {
            return BlockPos.of(Long.parseLong(key.substring(bar + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /**
     * Plante le drapeau (cote serveur). Le bloc est pose hors de la capture des instantanes de Forge : la protection du territoire ennemi ne
     * l'annule pas (c'est la seule pose permise chez l'ennemi, et seulement apres toutes les verifications ci-dessus).
     */
    static boolean plant(ServerPlayer p, ServerLevel level, BlockPos pos, ItemStack stack) {
        LAST_TRY.put(p.getUUID(), p.getServer().getTickCount());
        if (!level.getBlockState(pos).canBeReplaced() || !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)
            || !level.getBlockState(pos.above()).canBeReplaced()) {
            refuse(p, "clan.pioneers.siege.no_room");
            return false;
        }
        Check c = plan(p, level, pos);
        if (c.plan() == null) {
            refuse(p, c.error(), c.args());
            return false;
        }
        Plan plan = c.plan();
        BlockState state = WarBlocks.SIEGE_FLAG.getBlock().defaultBlockState().setValue(SiegeFlagBlock.FACING, p.getDirection().getOpposite());
        boolean capture = level.captureBlockSnapshots;
        level.captureBlockSnapshots = false;
        boolean placed = level.setBlock(pos, state, Block.UPDATE_ALL);
        level.captureBlockSnapshots = capture;
        if (!placed || !(level.getBlockEntity(pos) instanceof SiegeFlagTile tile)) {
            refuse(p, "clan.pioneers.siege.no_room");
            return false;
        }
        tile.start(plan, p.getUUID());
        if (!p.getAbilities().instabuild) {
            stack.shrink(1);
        }
        MinecraftServer server = p.getServer();
        String coords = pos.getX() + " " + pos.getY() + " " + pos.getZ();
        long day = PioneerApi.day(server);
        Component msg = Component.translatable("clan.pioneers.siege.opened", plan.attacker().name, plan.defender().name, coords);
        for (Clan x : ClanWar.side(server, plan.war().a(), plan.war().federalA())) {
            x.log(day, "clan.pioneers.log.siege_opened", plan.defender().name, coords);
            ClanWar.tellClan(server, x, msg, RpSounds.SIEGE_HORN.get());
        }
        for (Clan x : ClanWar.side(server, plan.war().b(), plan.war().federalB())) {
            x.log(day, "clan.pioneers.log.siege_opened", plan.defender().name, coords);
            ClanWar.tellClan(server, x, msg, RpSounds.SIEGE_HORN.get());
        }
        ClanAudit.write(server, "war", plan.attacker(), "drapeau de siege plante chez " + plan.defender().name + " en " + plan.dim() + " " + coords);
        return true;
    }

    /** Le joueur vient-il deja d'essayer de planter (clic sur un bloc puis clic dans le vide du meme geste, deux ticks au plus) ? */
    static boolean triedThisTick(ServerPlayer p) {
        Integer t = LAST_TRY.get(p.getUUID());
        return t != null && p.getServer().getTickCount() - t <= 2;
    }

    // ------------------------------------------------------------------ registre des drapeaux plantes
    static void register(String dim, BlockPos pos, UUID war) {
        FLAGS.put(War.chunkKey(dim, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4)), new Flag(dim, pos.immutable(), war));
    }

    static void unregister(String dim, BlockPos pos) {
        String k = War.chunkKey(dim, ChunkPos.asLong(pos.getX() >> 4, pos.getZ() >> 4));
        Flag f = FLAGS.get(k);
        if (f != null && f.pos().equals(pos)) {
            FLAGS.remove(k);
        }
    }

    /** Chunks qui portent un drapeau de siege (cles "dimension|chunk") : carte strategique, etat "siege en cours". */
    public static java.util.Set<String> flaggedChunks() {
        return java.util.Collections.unmodifiableSet(FLAGS.keySet());
    }

    /** 1.9 (MAJ RP, bloc 4) : position du drapeau plante sur un chunk ("dimension|chunk"), ou null (fiche Diplomatie : avancement). */
    @Nullable
    public static BlockPos flagAt(String chunkKey) {
        Flag f = FLAGS.get(chunkKey);
        return f == null ? null : f.pos();
    }

    /** Un drapeau de siege flotte-t-il sur ce chunk ? */
    public static boolean besieged(String dim, long chunk) {
        return FLAGS.containsKey(War.chunkKey(dim, chunk));
    }

    static int flagsOf(UUID war) {
        int n = 0;
        for (Flag f : FLAGS.values()) {
            if (f.war().equals(war)) {
                n++;
            }
        }
        return n;
    }

    /** Fin de guerre : tous ses drapeaux tombent (chunks charges ; les autres tombent a leur prochain controle). */
    public static void dropFlagsOf(MinecraftServer server, UUID war, String reasonKey) {
        for (Flag f : new ArrayList<>(FLAGS.values())) {
            if (!f.war().equals(war)) {
                continue;
            }
            ServerLevel level = ClanTerritory.level(server, f.dim());
            if (level != null && level.isLoaded(f.pos()) && level.getBlockEntity(f.pos()) instanceof SiegeFlagTile tile) {
                tile.fall(reasonKey);
            } else {
                FLAGS.remove(War.chunkKey(f.dim(), ChunkPos.asLong(f.pos().getX() >> 4, f.pos().getZ() >> 4)));
            }
        }
    }

    public static void clear() {
        FLAGS.clear();
        LAST_TRY.clear();
    }

    // ------------------------------------------------------------------ capture
    /**
     * Le chunk passe a l'agresseur : retire au defenseur (designations de Sceau tombees, emplacement de chunkloading libere), donne au clan
     * qui l'a pris si son budget le permet, sinon tenu le temps des scelles puis libere ; conteneurs scelles 24 heures pour l'ancien
     * proprietaire. Index et donnees mis a jour par le territoire ({@code ClanTerritory.put/drop}).
     */
    static void capture(MinecraftServer server, War war, Clan taker, Clan owner, String dim, long chunk, BlockPos flag) {
        WarRules.Values v = WarRules.get();
        long now = ClanClock.now();
        ClanTerritory.drop(server, owner, dim, chunk, true);
        Clan.Claim claim = new Clan.Claim();
        claim.sealedUntil = WarRules.sealUntil(v, now);
        claim.sealedFor = owner.id;
        boolean fits = taker.claimCount() + 1 <= ClanTerritory.budget(taker, now);
        //1.9 (MAJ RP, bloc 4) : guerre federale : hors budget, le chunk va a la reserve des zones federales de la faction (s'il y a la place)
        boolean reserve = !fits && fr.pioneers.society.clan.faction.FactionZones.reserve(server, taker, war.a(), war.federalA(), dim, chunk);
        if (reserve) {
            claim.federal = true;
            fits = true;
        }
        if (!fits) {
            claim.declineUntil = claim.sealedUntil;
            ListTag free = war.tag.getList("free", Tag.TAG_COMPOUND);
            CompoundTag f = new CompoundTag();
            f.putUUID("clan", taker.id);
            f.putString("dim", dim);
            f.putLong("chunk", chunk);
            f.putLong("at", claim.sealedUntil);
            free.add(f);
            war.tag.put("free", free);
        }
        ClanTerritory.put(server, taker, dim, chunk, claim);
        war.countCapture(ClanWar.realDay(now), now, War.chunkKey(dim, chunk));
        long day = PioneerApi.day(server);
        String coords = ClanTerritory.coords(chunk);
        long sealHours = Math.round(v.sealMs() / (double) WarRules.HOUR);
        Component up = Component.translatable(fits ? "clan.pioneers.siege.captured" : "clan.pioneers.siege.captured_over", taker.name, coords,
              owner.name, sealHours);
        Component down = Component.translatable("clan.pioneers.siege.lost", owner.name, coords, taker.name, sealHours);
        for (Clan x : ClanWar.side(server, war.a(), war.federalA())) {
            x.log(day, "clan.pioneers.log.captured", coords, owner.name);
            ClanWar.tellClan(server, x, up, RpSounds.CAPTURE_UP.get());
        }
        for (Clan x : ClanWar.side(server, war.b(), war.federalB())) {
            x.log(day, "clan.pioneers.log.lost", coords, taker.name);
            ClanWar.tellClan(server, x, down, RpSounds.CAPTURE_DOWN.get());
        }
        ClanAudit.write(server, "war", taker, "chunk " + dim + " " + coords + " pris a " + owner.name + (reserve ? " (reserve federale)" : fits ? ""
                                              : " (hors budget, libere apres les scelles)")
                                              + " ; drapeau en " + flag.toShortString());
        ClanData.get(server).changed();
    }

    static void refuse(ServerPlayer p, @Nullable String key, Object... args) {
        p.displayClientMessage(Component.translatable(key == null ? "clan.pioneers.error.failed" : key, args).withStyle(ChatFormatting.RED), true);
    }

    /** Message aux deux camps d'une guerre. */
    static void tellBoth(MinecraftServer server, War war, Component msg) {
        for (Clan x : ClanWar.side(server, war.a(), war.federalA())) {
            ClanMessages.members(server, x, null, ClanMessages.tagged(x, msg));
        }
        for (Clan x : ClanWar.side(server, war.b(), war.federalB())) {
            ClanMessages.members(server, x, null, ClanMessages.tagged(x, msg));
        }
    }
}
