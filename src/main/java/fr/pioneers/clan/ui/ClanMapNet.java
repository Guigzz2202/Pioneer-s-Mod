package fr.pioneers.clan.ui;

import fr.pioneers.clan.ClaimIndex;
import fr.pioneers.clan.Clan;
import fr.pioneers.clan.ClanApi;
import fr.pioneers.clan.ClanData;
import fr.pioneers.clan.core.ClanClock;
import fr.pioneers.clan.core.ClanMessages;
import fr.pioneers.clan.seal.ClanSeals;
import fr.pioneers.clan.territory.ClanChunkloading;
import fr.pioneers.clan.territory.ClanTerritory;
import fr.pioneers.clan.war.Siege;
import fr.pioneers.core.profile.RpActions;
import fr.pioneers.core.profile.RpNet;
import fr.pioneers.network.PacketRate;
import fr.pioneers.village.VillageData;
import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import javax.annotation.Nullable;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ChunkPos;

/**
 * 1.9 (MAJ RP, bloc 4, lot 5) : carte strategique de l'onglet Clan. Le joueur qui regarde la fiche Territoire s'abonne ({@code open}, repete
 * toutes les 5 secondes par le client tant que la carte est visible ; l'abonnement tombe apres {@link #KEEPALIVE_MS}). Il recoit d'abord la
 * grille complete des {@link #N} x {@link #N} chunks autour de lui ({@code full}), puis, une fois par seconde et seulement si quelque chose a
 * change, les cases qui different de ce qu'il a deja ({@code idx}/{@code val}, coordonnees absolues : un deplacement n'envoie que la bande
 * qui entre). Une case = un entier (voir les constantes) ; les proprietaires sont des indices d'une palette de clans (nom, etiquette,
 * couleur), jamais un nom de joueur. Brouillard : un chunk qui n'est ni au clan, ni a un allie, ni dans une region exploree par le joueur,
 * ses compagnons ou ses allies, n'est pas montre. Actions ({@code act}) : claim, unclaim, zone, load, unload sur une case de la grille,
 * revues par les fonctions du territoire.
 */
public final class ClanMapNet {

    /** Rayon et cote de la grille (chunks). */
    public static final int R = 15, N = 2 * R + 1;
    /** Sortes de case (bits 0-3). */
    public static final int FOG = 0, FREE = 1, OWN = 2, ALLY = 3, NEUTRAL = 4, ENEMY = 5, PLOT = 6;
    /** Marques (bits 4-12), zone d'un chunk du clan (bits 13-14), indice de palette (bits 16-23). */
    public static final int OUTPOST = 1 << 4, DECLINE = 1 << 5, CAPTURED = 1 << 6, SIEGE = 1 << 7, LOADED = 1 << 8, FEDERAL = 1 << 9,
          RALLIED = 1 << 10, HALL = 1 << 11, TOWER = 1 << 12, ZONE_SHIFT = 13, OWNER_SHIFT = 16;
    static final long KEEPALIVE_MS = 12_000L;
    private static final int PALETTE_MAX = 120;

    /** Abonne : derniere grille envoyee, palette, centre. */
    private static final class Sub {

        String dim = "";
        int cx, cz;
        int[] cells;
        final Map<UUID, Integer> palette = new LinkedHashMap<>();
        long seen;
        boolean full = true;
        /** 1.10.3 (minimap) : la position des membres du clan est demandee ({@link ClanMates}). */
        boolean mates;
    }

    private static int seconds;

    private static final Map<UUID, Sub> SUBS = new HashMap<>();

    private ClanMapNet() {
    }

    static void request(ServerPlayer p, CompoundTag t) {
        if (!ClanApi.enabled()) {
            return;
        }
        String op = t.getString("op");
        long key = (op.hashCode() * 31L + t.getInt("x")) * 31L + t.getInt("z");
        if (op.startsWith("area")) {
            //1.9.2 : une zone = un rectangle (les deux coins comptent dans la cle)
            key = (key * 31L + t.getInt("x1")) * 31L + t.getInt("z1");
        }
        if (!PacketRate.allowKeyed(p, PacketRate.CLAN_MAP, key)) {
            return;
        }
        switch (op) {
            case "open" -> {
                Sub s = SUBS.get(p.getUUID());
                if (s == null || t.getBoolean("fresh")) {
                    boolean mates = s != null && s.mates;
                    s = new Sub();
                    s.mates = mates;
                    SUBS.put(p.getUUID(), s);
                }
                if (t.contains("mates")) {
                    //1.10.3 : seule la minimap envoie ce drapeau (la fiche Territoire le laisse tel quel)
                    s.mates = t.getBoolean("mates");
                }
                s.seen = ClanClock.now();
                send(p, s);
            }
            case "close" -> SUBS.remove(p.getUUID());
            case "act" -> act(p, t);
            case "area", "area_apply" -> area(p, t, "area_apply".equals(op));
            default -> {
            }
        }
    }

    /** Action sur une case de la grille : meme dimension, a {@link #R} chunks au plus du joueur. */
    private static void act(ServerPlayer p, CompoundTag t) {
        ServerLevel level = p.serverLevel();
        int x = t.getInt("x"), z = t.getInt("z");
        ChunkPos here = p.chunkPosition();
        if (Math.max(Math.abs(x - here.x), Math.abs(z - here.z)) > R) {
            ClanMessages.refuse(p, "clan.pioneers.ui.map.too_far");
            return;
        }
        long chunk = ChunkPos.asLong(x, z);
        switch (t.getString("act")) {
            case "claim" -> ClanTerritory.claim(p, level, chunk);
            case "unclaim" -> ClanTerritory.unclaim(p, level, chunk);
            case "load" -> ClanChunkloading.load(p, level, chunk);
            case "unload" -> ClanChunkloading.unload(p, level, chunk);
            case "zone" -> {
                Clan.Zone zone;
                try {
                    zone = Clan.Zone.valueOf(t.getString("zone").toUpperCase(Locale.ROOT));
                } catch (IllegalArgumentException e) {
                    return;
                }
                ClanTerritory.zone(p, level, chunk, zone, t.getInt("rank"));
            }
            default -> {
                return;
            }
        }
        Sub s = SUBS.get(p.getUUID());
        if (s != null) {
            send(p, s);
        }
    }

    /**
     * 1.9.2 (retours de Guillaume : « drag and drop la zone que je souhaite claim ») : zone tracee a la souris sur la carte, coins
     * ({@code x}, {@code z}) et ({@code x1}, {@code z1}), mode {@code unclaim}. {@code area} : apercu (rien ne change) ; {@code area_apply} :
     * le serveur refait tout le plan et applique en une fois. Un seul paquet par geste ; la reponse part dans {@code CLAN_MAP} ({@code area}).
     */
    private static void area(ServerPlayer p, CompoundTag t, boolean apply) {
        ServerLevel level = p.serverLevel();
        int x0 = t.getInt("x"), z0 = t.getInt("z"), x1 = t.getInt("x1"), z1 = t.getInt("z1");
        ChunkPos here = p.chunkPosition();
        CompoundTag res;
        if (Math.max(Math.max(Math.abs(x0 - here.x), Math.abs(z0 - here.z)), Math.max(Math.abs(x1 - here.x), Math.abs(z1 - here.z))) > R) {
            res = new CompoundTag();
            res.putString("error", "clan.pioneers.ui.map.too_far");
        } else {
            res = ClanTerritory.area(p, level, x0, z0, x1, z1, !t.getBoolean("unclaim"), apply);
        }
        res.putBoolean("apply", apply);
        CompoundTag msg = new CompoundTag();
        msg.put("area", res);
        RpNet.send(p, RpActions.CLAN_MAP, msg);
        Sub s = SUBS.get(p.getUUID());
        if (apply && s != null) {
            send(p, s);
        }
    }

    /** Une fois par seconde : grilles des abonnes, envoyees seulement si elles ont change ; abonnements perimes retires. */
    static void tick(MinecraftServer server) {
        if (SUBS.isEmpty()) {
            return;
        }
        long now = ClanClock.now();
        SUBS.entrySet().removeIf(e -> now - e.getValue().seen > KEEPALIVE_MS || server.getPlayerList().getPlayer(e.getKey()) == null);
        Map<String, Long2IntOpenHashMap> marks = new HashMap<>();
        boolean matesNow = ++seconds % ClanMates.PERIOD_S == 0;
        for (Map.Entry<UUID, Sub> e : new ArrayList<>(SUBS.entrySet())) {
            ServerPlayer p = server.getPlayerList().getPlayer(e.getKey());
            if (p != null) {
                send(p, e.getValue(), marks);
                if (matesNow && e.getValue().mates) {
                    RpNet.send(p, RpActions.CLAN_MATES, ClanMates.tag(server, p));
                }
            }
        }
    }

    static void forget(UUID player) {
        SUBS.remove(player);
    }

    static void clear() {
        SUBS.clear();
        seconds = 0;
    }

    /** 1.10.3 (GameTests) : le joueur est-il abonne, et avec les membres du clan ? */
    public static boolean watchesMates(UUID player) {
        Sub s = SUBS.get(player);
        return s != null && s.mates;
    }

    private static void send(ServerPlayer p, Sub s) {
        send(p, s, new HashMap<>());
    }

    /** Calcule la grille du joueur et envoie la grille complete ou les seules differences. */
    private static void send(ServerPlayer p, Sub s, Map<String, Long2IntOpenHashMap> marksByDim) {
        MinecraftServer server = p.getServer();
        String dim = ClanApi.dim(p.level());
        ChunkPos c = p.chunkPosition();
        Long2IntOpenHashMap marks = marksByDim.computeIfAbsent(dim, d -> marks(server, d));
        if (!dim.equals(s.dim) || s.palette.size() >= PALETTE_MAX) {
            s.full = true;
        }
        if (s.full) {
            s.palette.clear();
        }
        ListTag newPalette = new ListTag();
        int[] cells = compute(p, dim, c.x, c.z, s, marks, newPalette);
        CompoundTag t = new CompoundTag();
        t.putInt("cx", c.x);
        t.putInt("cz", c.z);
        t.putInt("r", R);
        if (s.full || s.cells == null) {
            t.putBoolean("full", true);
            t.putString("dim", dim);
            t.putIntArray("cells", cells);
        } else {
            List<Integer> idx = new ArrayList<>(), val = new ArrayList<>();
            for (int i = 0; i < cells.length; i++) {
                int ax = c.x - R + i % N, az = c.z - R + i / N;
                int ox = ax - (s.cx - R), oz = az - (s.cz - R);
                int old = ox >= 0 && oz >= 0 && ox < N && oz < N ? s.cells[oz * N + ox] : -1;
                if (old != cells[i]) {
                    idx.add(i);
                    val.add(cells[i]);
                }
            }
            if (idx.isEmpty() && newPalette.isEmpty() && s.cx == c.x && s.cz == c.z) {
                return;
            }
            t.putIntArray("idx", idx.stream().mapToInt(Integer::intValue).toArray());
            t.putIntArray("val", val.stream().mapToInt(Integer::intValue).toArray());
        }
        if (!newPalette.isEmpty()) {
            t.put("palette", newPalette);
        }
        s.full = false;
        s.dim = dim;
        s.cx = c.x;
        s.cz = c.z;
        s.cells = cells;
        RpNet.send(p, RpActions.CLAN_MAP, t);
    }

    /** Mairies et Tours de guet (publiques), villages ralliés d'une dimension : chunk -&gt; marques. */
    private static Long2IntOpenHashMap marks(MinecraftServer server, String dim) {
        Long2IntOpenHashMap m = new Long2IntOpenHashMap();
        for (Clan clan : ClanData.get(server).clans()) {
            for (Map.Entry<String, String> f : clan.functions.entrySet()) {
                int flag = ClanSeals.TOWN_HALL.equals(f.getValue()) ? HALL : ClanSeals.WATCHTOWER.equals(f.getValue()) ? TOWER : 0;
                if (flag == 0 || !ClanSeals.dimOf(f.getKey()).equals(dim)) {
                    continue;
                }
                BlockPos b = ClanSeals.posOf(f.getKey());
                long k = ChunkPos.asLong(b.getX() >> 4, b.getZ() >> 4);
                m.put(k, m.get(k) | flag);
            }
        }
        ServerLevel level = ClanTerritory.level(server, dim);
        if (level != null) {
            for (VillageData.Village v : VillageData.get(level).all()) {
                if (v.previousPeople != null && !v.previousPeople.isEmpty()) {
                    long k = ChunkPos.asLong(v.center.getX() >> 4, v.center.getZ() >> 4);
                    m.put(k, m.get(k) | RALLIED);
                }
            }
        }
        return m;
    }

    private static int[] compute(ServerPlayer p, String dim, int cx, int cz, Sub s, Long2IntOpenHashMap marks, ListTag newPalette) {
        MinecraftServer server = p.getServer();
        ClanData data = ClanData.get(server);
        Clan mine = data.clanOf(p.getUUID());
        long now = ClanClock.now();
        //qui partage sa carte : le joueur, ses compagnons, les membres des clans allies
        ClanExplored explored = ClanExplored.get(server);
        List<LongOpenHashSet> views = new ArrayList<>();
        addView(views, explored.of(p.getUUID(), dim));
        Map<UUID, ClanApi.Relation> rel = new HashMap<>();
        if (mine != null) {
            for (UUID u : mine.members.keySet()) {
                if (!u.equals(p.getUUID())) {
                    addView(views, explored.of(u, dim));
                }
            }
            for (Clan o : data.clans()) {
                if (o != mine && ClanApi.relation(server, mine, o) == ClanApi.Relation.ALLY) {
                    for (UUID u : o.members.keySet()) {
                        addView(views, explored.of(u, dim));
                    }
                    addView(views, towerView(o, dim));
                }
            }
            addView(views, towerView(mine, dim));
        }
        int[] out = new int[N * N];
        int i = 0;
        for (int dz = -R; dz <= R; dz++) {
            for (int dx = -R; dx <= R; dx++, i++) {
                int x = cx + dx, z = cz + dz;
                long chunk = ChunkPos.asLong(x, z);
                UUID owner = ClaimIndex.owner(dim, chunk);
                Clan o = owner == null ? null : data.clan(owner);
                ClanApi.Relation r = o == null ? null : rel.computeIfAbsent(o.id, k -> ClanApi.relation(server, mine, o));
                boolean shared = r == ClanApi.Relation.SAME || r == ClanApi.Relation.ALLY;
                if (!shared && !seen(views, ClanExplored.region(x, z))) {
                    out[i] = FOG;
                    continue;
                }
                int cell;
                if (o == null) {
                    cell = FREE;
                } else {
                    cell = switch (r) {
                        case SAME -> OWN;
                        case ALLY -> ALLY;
                        case WAR -> ENEMY;
                        default -> o.solo ? PLOT : NEUTRAL;
                    };
                    Clan.Claim claim = o.claim(dim, chunk);
                    if (claim != null) {
                        if (claim.federal) {
                            cell |= FEDERAL;
                        }
                        if (claim.sealedUntil > now) {
                            cell |= CAPTURED;
                        }
                        if (r == ClanApi.Relation.SAME) {
                            if (claim.outpost) {
                                cell |= OUTPOST;
                            }
                            if (claim.declineUntil > 0) {
                                cell |= DECLINE;
                            }
                            if (ClanChunkloading.isLoaded(o, dim, chunk)) { //1.0.4-beta (lot 8) : claims charges
                                cell |= LOADED;
                            }
                            cell |= (claim.zone.ordinal() & 3) << ZONE_SHIFT;
                        }
                    }
                    cell |= paletteIndex(server, s, o, newPalette) << OWNER_SHIFT;
                }
                if (Siege.besieged(dim, chunk)) {
                    cell |= SIEGE;
                }
                cell |= marks.get(chunk);
                out[i] = cell;
            }
        }
        return out;
    }

    private static void addView(List<LongOpenHashSet> views, @Nullable LongOpenHashSet set) {
        if (set != null && !set.isEmpty()) {
            views.add(set);
        }
    }

    /**
     * 1.9 (MAJ RP, lot 19) : les Tours de guet revelent la carte alentour (fonction « watchtower », rayon des donnees) pour leur clan et
     * ses allies, comme si quelqu'un y avait explore.
     */
    @javax.annotation.Nullable
    private static LongOpenHashSet towerView(Clan c, String dim) {
        fr.pioneers.clan.territory.TerritoryDefs.Function f = fr.pioneers.clan.territory.TerritoryDefs.get().function(ClanSeals.WATCHTOWER);
        if (f == null || f.radius() <= 0 || !c.functions.containsValue(ClanSeals.WATCHTOWER)) {
            return null;
        }
        LongOpenHashSet out = new LongOpenHashSet();
        int rc = (f.radius() + 15) >> 4;
        for (String key : ClanSeals.all(c, ClanSeals.WATCHTOWER)) {
            if (!dim.equals(ClanSeals.dimOf(key))) {
                continue;
            }
            net.minecraft.core.BlockPos at = ClanSeals.posOf(key);
            if (at == null) {
                continue;
            }
            int tx = at.getX() >> 4, tz = at.getZ() >> 4;
            for (int dz = -rc; dz <= rc; dz++) {
                for (int dx = -rc; dx <= rc; dx++) {
                    if (dx * dx + dz * dz <= rc * rc) {
                        out.add(ClanExplored.region(tx + dx, tz + dz));
                    }
                }
            }
        }
        return out;
    }

    private static boolean seen(List<LongOpenHashSet> views, long region) {
        for (LongOpenHashSet v : views) {
            if (v.contains(region)) {
                return true;
            }
        }
        return false;
    }

    /** Indice de palette d'un clan (ajoute a la palette envoyee s'il est nouveau) ; 0 si la palette est pleine. */
    private static int paletteIndex(MinecraftServer server, Sub s, Clan o, ListTag newPalette) {
        Integer idx = s.palette.get(o.id);
        if (idx != null) {
            return idx;
        }
        if (s.palette.size() >= PALETTE_MAX) {
            return 0;
        }
        idx = s.palette.size() + 1;
        s.palette.put(o.id, idx);
        CompoundTag e = new CompoundTag();
        e.putInt("i", idx);
        boolean named = o.name != null && !o.name.isBlank();
        e.putString("name", named ? o.name : "");
        e.putString("soloOf", named ? "" : ClanMessages.character(server, o, o.representative));
        e.putString("tag", o.tag == null ? "" : o.tag);
        e.putInt("color", o.color & 15);
        newPalette.add(e);
        return idx;
    }
}
