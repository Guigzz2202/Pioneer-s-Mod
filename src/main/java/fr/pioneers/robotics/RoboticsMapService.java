package fr.pioneers.robotics;

import fr.pioneers.Pioneers;
import fr.pioneers.network.PacketRoboticsMap;
import fr.pioneers.network.PiNetwork;
import fr.pioneers.rail.RailMapData;
import fr.pioneers.robotics.hub.RoboticHubData;
import fr.pioneers.robotics.logistics.LogisticNetwork;
import fr.pioneers.robotics.logistics.LogisticPoint;
import fr.pioneers.robotics.logistics.LogisticsRegistry;
import fr.pioneers.robotics.logistics.NetworkNode;
import fr.pioneers.robotics.logistics.TileEntityRoboticRelay;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

/**
 * Carte robotique, cote serveur : les joueurs qui ont la carte ouverte recoivent l'etat du reseau autour d'eux deux fois par seconde
 * (noeuds et couverture, points, unites en direct, alertes, ordres, stocks), et les tuiles du terrain (couleur et hauteur de chaque
 * colonne) qu'ils demandent.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RoboticsMapService {

    private RoboticsMapService() {
    }

    /** Rayon (blocs) autour du joueur couvert par l'etat envoye. */
    public static final int VIEW = 384;
    private static final Map<UUID, Long> WATCHERS = new HashMap<>();
    /** 0.20.46 (audit) : empreinte des voies du rail deja envoyees a chaque joueur (renvoyees seulement si elles changent). */
    private static final Map<UUID, Long> TRACKS_SENT = new HashMap<>();
    /** 0.20.46 (audit) : tuiles du terrain demandees et pas encore envoyees, par joueur (au plus {@link #TILE_QUEUE}). */
    private static final Map<UUID, LongLinkedOpenHashSet> TILE_REQUESTS = new HashMap<>();
    /** Tuiles calculees par tick et par joueur (640 par seconde), et file maximale. */
    private static final int TILES_PER_TICK = 32, TILE_QUEUE = 4096;
    /** Plafonds des listes de l'etat (bien au-dela d'une base ordinaire ; protegent le reseau d'un monde demesure). */
    private static final int MAX_POINTS = 2048, MAX_UNITS = 1024, MAX_ALERTS = 256, MAX_BUSY = 20_000, MAX_DELIVERIES = 256;

    /** Carte ouverte (ou toujours ouverte) ; {@code sendNow} : l'etat part tout de suite. */
    public static void watch(ServerPlayer player, boolean sendNow) {
        if (!WATCHERS.containsKey(player.getUUID())) {
            //Nouvelle ouverture : le client repart d'une carte du rail vide, les voies doivent repartir
            TRACKS_SENT.remove(player.getUUID());
        }
        WATCHERS.put(player.getUUID(), player.serverLevel().getGameTime());
        if (sendNow) {
            send(player, true);
        }
    }

    /** Carte fermee (les tuiles deja demandees partent quand meme : le client ne les redemande pas avant 30 s). */
    public static void unwatch(ServerPlayer player) {
        WATCHERS.remove(player.getUUID());
        TRACKS_SENT.remove(player.getUUID());
    }

    /** 0.20.46 (audit) : joueur deconnecte, serveur arrete. */
    public static void forget(UUID player) {
        WATCHERS.remove(player);
        TRACKS_SENT.remove(player);
        TILE_REQUESTS.remove(player);
    }

    public static void forgetAll() {
        WATCHERS.clear();
        TRACKS_SENT.clear();
        TILE_REQUESTS.clear();
    }

    /** Tuiles voulues par la carte d'un joueur : mises en file, calculees par petits lots a chaque tick. */
    public static void queueTiles(ServerPlayer player, long[] chunks) {
        LongLinkedOpenHashSet queue = TILE_REQUESTS.computeIfAbsent(player.getUUID(), k -> new LongLinkedOpenHashSet());
        for (long chunk : chunks) {
            if (queue.size() >= TILE_QUEUE) {
                break;
            }
            queue.add(chunk);
        }
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !(event.level instanceof ServerLevel level)) {
            return;
        }
        if (!TILE_REQUESTS.isEmpty()) {
            sendTiles(level);
        }
        if (WATCHERS.isEmpty()) {
            return;
        }
        long now = level.getGameTime();
        if (now % 10 != 3) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            Long since = WATCHERS.get(player.getUUID());
            if (since == null) {
                continue;
            }
            //La carte se signale toutes les 2 s ; au-dela de 10 s sans nouvelles, on arrete
            if (now - since > 200) {
                unwatch(player);
                continue;
            }
            //0.20.5 : les voies du rail toutes les 2 s (le reste de l'etat deux fois par seconde)
            send(player, now % 40 == 3);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        forget(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        forgetAll();
    }

    /** Un lot de tuiles par joueur de cette dimension qui en attend. */
    private static void sendTiles(ServerLevel level) {
        for (ServerPlayer player : level.players()) {
            LongLinkedOpenHashSet queue = TILE_REQUESTS.get(player.getUUID());
            if (queue == null) {
                continue;
            }
            List<PacketRoboticsMap.Tile> tiles = new ArrayList<>();
            for (int i = 0; i < TILES_PER_TICK && !queue.isEmpty(); i++) {
                long chunk = queue.removeFirstLong();
                int cx = (int) chunk, cz = (int) (chunk >> 32);
                //Pas plus loin que la vue de la carte
                if (Math.abs((cx << 4) - player.getBlockX()) > VIEW + 64 || Math.abs((cz << 4) - player.getBlockZ()) > VIEW + 64) {
                    continue;
                }
                PacketRoboticsMap.Tile tile = tile(level, cx, cz);
                if (tile != null) {
                    tiles.add(tile);
                }
            }
            if (queue.isEmpty()) {
                TILE_REQUESTS.remove(player.getUUID());
            }
            if (!tiles.isEmpty()) {
                PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRoboticsMap.Tiles(tiles));
            }
        }
    }

    public static void keepAlive(ServerPlayer player) {
        WATCHERS.computeIfPresent(player.getUUID(), (k, v) -> player.serverLevel().getGameTime());
    }

    private static void send(ServerPlayer player, boolean tracks) {
        CompoundTag tag = snapshot(player);
        CompoundTag rail = RailMapData.snapshot(player, VIEW, tracks);
        trimRail(rail, player);
        if (tracks) {
            //0.20.46 (audit) : les voies (jusqu'a 20 000 ancres) ne repartent que si elles ont change depuis le dernier envoi
            long hash = 31L * Arrays.hashCode(rail.getLongArray("tracks")) + Arrays.hashCode(rail.getIntArray("trackInfo"));
            Long sent = TRACKS_SENT.put(player.getUUID(), hash);
            if (sent != null && sent == hash) {
                rail.remove("tracks");
                rail.remove("trackInfo");
            }
        }
        tag.put("rail", rail);
        PiNetwork.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new PacketRoboticsMap.State(tag));
    }

    /**
     * 0.20.46 (audit) : occupation des voies et livraisons du repartiteur reduites a ce que la carte peut montrer autour du joueur
     * (le releve du rail les donne pour toute la dimension), listes plafonnees.
     */
    private static void trimRail(CompoundTag rail, ServerPlayer player) {
        int reach = VIEW + 128;
        long[] busy = rail.getLongArray("busy");
        LongArrayList kept = new LongArrayList();
        for (long anchor : busy) {
            if (kept.size() < MAX_BUSY && Math.abs(BlockPos.getX(anchor) - player.getBlockX()) <= reach
                && Math.abs(BlockPos.getZ(anchor) - player.getBlockZ()) <= reach) {
                kept.add(anchor);
            }
        }
        if (kept.size() != busy.length) {
            rail.putLongArray("busy", kept.toLongArray());
        }
        ListTag deliveries = rail.getList("deliveries", Tag.TAG_COMPOUND);
        if (!deliveries.isEmpty()) {
            ListTag near = new ListTag();
            int far = 2 * VIEW;
            for (int i = 0; i < deliveries.size() && near.size() < MAX_DELIVERIES; i++) {
                CompoundTag d = deliveries.getCompound(i);
                long from = d.getLong("from"), to = d.getLong("to");
                if (Math.abs(BlockPos.getX(from) - player.getBlockX()) <= far && Math.abs(BlockPos.getZ(from) - player.getBlockZ()) <= far
                    || Math.abs(BlockPos.getX(to) - player.getBlockX()) <= far && Math.abs(BlockPos.getZ(to) - player.getBlockZ()) <= far) {
                    near.add(d);
                }
            }
            rail.put("deliveries", near);
        }
    }

    // ------------------------------------------------------------------
    // Etat du reseau
    // ------------------------------------------------------------------

    private static boolean near(BlockPos pos, ServerPlayer player) {
        return Math.abs(pos.getX() - player.getBlockX()) <= VIEW && Math.abs(pos.getZ() - player.getBlockZ()) <= VIEW;
    }

    public static CompoundTag snapshot(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        long now = level.getGameTime();
        LogisticsRegistry registry = LogisticsRegistry.get(level);
        CompoundTag tag = new CompoundTag();
        //Reseaux : noeuds, couverture, stocks
        ListTag networks = new ListTag();
        for (LogisticNetwork network : registry.networks(now)) {
            boolean visible = network.nodes.stream().anyMatch(node -> near(node.nodeCenter(), player));
            if (!visible) {
                continue;
            }
            CompoundTag n = new CompoundTag();
            n.putLong("id", network.id);
            n.putString("label", network.label());
            ListTag nodes = new ListTag();
            for (NetworkNode node : network.nodes) {
                CompoundTag e = new CompoundTag();
                e.put("pos", NbtUtils.writeBlockPos(node.nodeCenter()));
                e.putShort("cov", (short) node.coverage());
                if (node instanceof RoboticHubData hub) {
                    e.putByte("type", (byte) 0);
                    e.putByte("kind", (byte) hub.getKind().ordinal());
                    e.putByte("status", (byte) hub.getStatus().ordinal());
                    e.putByte("units", (byte) hub.unitCount());
                    e.putByte("capacity", (byte) hub.capacity());
                    e.putByte("active", (byte) hub.getActive());
                    e.putByte("docked", (byte) hub.availableUnits().size());

                } else if (node instanceof TileEntityRoboticRelay relay) {
                    e.putByte("type", (byte) 1);
                    e.putByte("charging", (byte) relay.getCharging());
                    e.putFloat("energy", (float) relay.getEnergyContainer().ratio());
                }
                nodes.add(e);
            }
            n.put("nodes", nodes);
            ListTag totals = new ListTag();
            List<Map.Entry<LogisticResource, Long>> entries = new ArrayList<>(network.totals.entrySet());
            entries.sort(Comparator.comparingLong((Map.Entry<LogisticResource, Long> e) -> -e.getValue()));
            for (int i = 0; i < Math.min(400, entries.size()); i++) {
                CompoundTag t = entries.get(i).getKey().save();
                t.putLong("n", entries.get(i).getValue());
                totals.add(t);
            }
            n.put("totals", totals);
            networks.add(n);
        }
        tag.put("networks", networks);
        //Points logistiques
        ListTag points = new ListTag();
        for (LogisticPoint point : registry.allPoints()) {
            if (point.isPointRemoved() || !near(point.getPointPos(), player)) {
                continue;
            }
            if (points.size() >= MAX_POINTS) {
                break;
            }
            CompoundTag p = new CompoundTag();
            p.put("pos", NbtUtils.writeBlockPos(point.getPointPos()));
            p.put("ep", point.endpoint().save());
            p.put("cfg", point.getConfig().save());
            p.put("icon", point.displayIcon().save(new CompoundTag()));
            p.putString("name", point.displayName().getString());
            points.add(p);
        }
        tag.put("points", points);
        //Unites en direct
        ListTag units = new ListTag();
        AABB box = new AABB(player.getX() - VIEW, level.getMinBuildHeight(), player.getZ() - VIEW, player.getX() + VIEW, level.getMaxBuildHeight(),
              player.getZ() + VIEW);
        for (RobotUnitEntity unit : level.getEntitiesOfClass(RobotUnitEntity.class, box)) {
            if (units.size() >= MAX_UNITS) {
                break;
            }
            CompoundTag u = new CompoundTag();
            u.putInt("id", unit.getId());
            u.putByte("kind", (byte) unit.getKind().ordinal());
            u.putByte("tier", (byte) unit.getTier().ordinal());
            u.putFloat("x", (float) unit.getX());
            u.putFloat("y", (float) unit.getY());
            u.putFloat("z", (float) unit.getZ());
            u.putByte("phase", (byte) unit.getPhase().ordinal());
            u.putByte("task", (byte) unit.getTask().ordinal());
            u.putByte("bat", (byte) Math.round(unit.batteryRatio() * 100));
            u.putByte("fill", (byte) Math.round(unit.getCargo().fill() * 100));
            Vec3 target = unit.getMoveTarget();
            if (target != null) {
                u.putFloat("tx", (float) target.x);
                u.putFloat("tz", (float) target.z);
            }
            units.add(u);
        }
        tag.put("units", units);
        //Alertes
        ListTag alerts = new ListTag();
        for (RoboticsAlert alert : RoboticsScheduler.alerts(level)) {
            if (near(alert.pos(), player) && alerts.size() < MAX_ALERTS) {
                CompoundTag a = new CompoundTag();
                a.putByte("type", (byte) alert.type().ordinal());
                a.put("pos", NbtUtils.writeBlockPos(alert.pos()));
                a.putInt("age", (int) Math.min(Integer.MAX_VALUE, (level.getGameTime() - alert.first()) / 20));
                a.putShort("returns", (short) Math.min(Short.MAX_VALUE, alert.returns()));
                alerts.add(a);
            }
        }
        tag.put("alerts", alerts);
        //Ordres
        ListTag orders = new ListTag();
        for (RoboticOrder order : RoboticsOrders.get(level).all()) {
            orders.add(order.save(true));
        }
        tag.put("orders", orders);
        //Reseau passif : limite et unites occupees par genre
        int[] busy = new int[UnitKind.values().length];
        for (UnitKind kind : UnitKind.values()) {
            busy[kind.ordinal()] = RoboticsScheduler.passiveBusy(level, kind);
        }
        tag.putIntArray("passive", RoboticsOrders.get(level).passiveLimits());
        tag.putIntArray("passiveBusy", busy);
        tag.putBoolean("repairs", RoboticsOrders.get(level).repairs());
        tag.putInt("damaged", RobotRepairs.get(level).size());
        return tag;
    }

    // ------------------------------------------------------------------
    // Tuiles du terrain
    // ------------------------------------------------------------------

    /** Couleur (index de carte Mekanism/vanilla avec ombrage) et hauteur de chaque colonne d'un chunk charge, ou null. */
    public static PacketRoboticsMap.Tile tile(ServerLevel level, int cx, int cz) {
        if (!level.hasChunk(cx, cz)) {
            return null;
        }
        LevelChunk chunk = level.getChunk(cx, cz);
        byte[] colors = new byte[256];
        short[] heights = new short[256];
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        int[] north = new int[16];
        for (int x = 0; x < 16; x++) {
            int wx = (cx << 4) + x, wz = (cz << 4) - 1;
            north[x] = level.hasChunk(wx >> 4, wz >> 4) ? level.getHeight(Heightmap.Types.WORLD_SURFACE, wx, wz) - 1 : Integer.MIN_VALUE;
        }
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                int wx = (cx << 4) + x, wz = (cz << 4) + z;
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, x, z);
                pos.set(wx, top, wz);
                BlockState state = chunk.getBlockState(pos);
                int y = top;
                //Sous l'eau : la couleur de l'eau, plus sombre avec la profondeur
                int depth = 0;
                while (!state.getFluidState().isEmpty() && depth < 12 && y > level.getMinBuildHeight()) {
                    depth++;
                    y--;
                    pos.setY(y);
                    state = chunk.getBlockState(pos);
                }
                MapColor color;
                MapColor.Brightness brightness;
                if (depth > 0) {
                    color = MapColor.WATER;
                    brightness = depth > 6 ? MapColor.Brightness.LOW : depth > 2 ? MapColor.Brightness.NORMAL : MapColor.Brightness.HIGH;
                } else {
                    pos.setY(top);
                    state = chunk.getBlockState(pos);
                    color = state.getMapColor(level, pos);
                    int above = north[x];
                    brightness = above == Integer.MIN_VALUE || above == top ? MapColor.Brightness.NORMAL
                                                                           : top > above ? MapColor.Brightness.HIGH : MapColor.Brightness.LOW;
                }
                if (color == MapColor.NONE) {
                    color = MapColor.STONE;
                }
                colors[z * 16 + x] = (byte) color.getPackedId(brightness);
                heights[z * 16 + x] = (short) top;
                north[x] = top;
            }
        }
        return new PacketRoboticsMap.Tile(cx, cz, colors, heights);
    }
}
