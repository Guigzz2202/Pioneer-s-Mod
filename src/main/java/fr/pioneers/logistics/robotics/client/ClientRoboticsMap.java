package fr.pioneers.logistics.robotics.client;

import com.mojang.blaze3d.platform.NativeImage;
import fr.pioneers.Pioneers;
import fr.pioneers.logistics.rail.client.ClientRailMap;
import fr.pioneers.core.network.PacketRoboticsMap;
import fr.pioneers.core.network.PiNetwork;
import fr.pioneers.logistics.robotics.Endpoint;
import fr.pioneers.logistics.robotics.LogisticResource;
import fr.pioneers.logistics.robotics.RoboticsAlert;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.logistics.robotics.logistics.PointConfig;
import it.unimi.dsi.fastutil.longs.Long2LongOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.MapColor;
import org.jetbrains.annotations.Nullable;

/**
 * Carte robotique, cote client : l'etat du reseau recu du serveur (noeuds, points, unites, alertes, stocks) et le terrain, assemble en
 * textures de regions de 8x8 chunks (128x128 pixels, un pixel par colonne).
 */
public final class ClientRoboticsMap {

    private ClientRoboticsMap() {
    }

    public record Node(BlockPos pos, int coverage, boolean hub, UnitKind kind, int status, int units, int capacity, int active, int docked, int charging,
                       float energy) {}

    public record Network(long id, String label, List<Node> nodes, List<Stock> totals) {

        public boolean covers(double x, double z) {
            for (Node node : nodes) {
                if (Math.abs(x - node.pos().getX() - 0.5) <= node.coverage() + 0.5 && Math.abs(z - node.pos().getZ() - 0.5) <= node.coverage() + 0.5) {
                    return true;
                }
            }
            return false;
        }
    }

    public record Stock(LogisticResource res, long amount) {}

    public record Point(BlockPos pos, Endpoint endpoint, PointConfig config, ItemStack icon, String name) {}

    public record Unit(int id, UnitKind kind, int tier, double x, double y, double z, int phase, int task, int battery, int fill, double tx, double tz,
                       boolean moving) {}

    public record Alert(RoboticsAlert.Type type, BlockPos pos, int age, int returns) {

        public boolean recurring() {
            return returns > 0;
        }
    }

    public static List<Network> networks = List.of();
    public static List<Point> points = List.of();
    public static List<Unit> units = List.of();
    public static List<Alert> alerts = List.of();
    public static long received;
    /** Reseau passif : limite par genre d'unite (RoboticsOrders.PASSIVE_ALL : sans limite) et unites occupees. */
    public static int[] passive = new int[0], passiveBusy = new int[0];
    /** Reparations par les intendants, et blocs a reparer. */
    public static boolean repairs = true;
    public static int damaged;

    public static void receive(CompoundTag tag) {
        List<Network> nets = new ArrayList<>();
        for (Tag t : tag.getList("networks", Tag.TAG_COMPOUND)) {
            CompoundTag n = (CompoundTag) t;
            List<Node> nodes = new ArrayList<>();
            for (Tag e : n.getList("nodes", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                nodes.add(new Node(NbtUtils.readBlockPos(c.getCompound("pos")), c.getShort("cov"), c.getByte("type") == 0,
                      UnitKind.values()[Math.max(0, Math.min(UnitKind.values().length - 1, c.getByte("kind")))], c.getByte("status"), c.getByte("units"), c.getByte("capacity"),
                      c.getByte("active"), c.getByte("docked"), c.getByte("charging"), c.getFloat("energy")));
            }
            List<Stock> totals = new ArrayList<>();
            for (Tag e : n.getList("totals", Tag.TAG_COMPOUND)) {
                CompoundTag c = (CompoundTag) e;
                totals.add(new Stock(LogisticResource.load(c), c.getLong("n")));
            }
            nets.add(new Network(n.getLong("id"), n.getString("label"), nodes, totals));
        }
        networks = nets;
        List<Point> pts = new ArrayList<>();
        for (Tag t : tag.getList("points", Tag.TAG_COMPOUND)) {
            CompoundTag p = (CompoundTag) t;
            pts.add(new Point(NbtUtils.readBlockPos(p.getCompound("pos")), Endpoint.load(p.getCompound("ep")), PointConfig.load(p.getCompound("cfg")),
                  ItemStack.of(p.getCompound("icon")), p.getString("name")));
        }
        points = pts;
        List<Unit> us = new ArrayList<>();
        for (Tag t : tag.getList("units", Tag.TAG_COMPOUND)) {
            CompoundTag u = (CompoundTag) t;
            us.add(new Unit(u.getInt("id"), UnitKind.values()[Math.max(0, Math.min(UnitKind.values().length - 1, u.getByte("kind")))], u.getByte("tier"), u.getFloat("x"),
                  u.getFloat("y"), u.getFloat("z"), u.getByte("phase"), u.getByte("task"), u.getByte("bat"), u.getByte("fill"),
                  u.contains("tx") ? u.getFloat("tx") : Double.NaN, u.contains("tz") ? u.getFloat("tz") : Double.NaN, u.contains("tx")));
        }
        units = us;
        List<Alert> as = new ArrayList<>();
        for (Tag t : tag.getList("alerts", Tag.TAG_COMPOUND)) {
            CompoundTag a = (CompoundTag) t;
            RoboticsAlert.Type[] types = RoboticsAlert.Type.values();
            as.add(new Alert(types[Math.max(0, Math.min(types.length - 1, a.getByte("type")))], NbtUtils.readBlockPos(a.getCompound("pos")), a.getInt("age"),
                  a.getShort("returns")));
        }
        alerts = as;
        passive = tag.getIntArray("passive");
        passiveBusy = tag.getIntArray("passiveBusy");
        repairs = !tag.contains("repairs") || tag.getBoolean("repairs");
        damaged = tag.getInt("damaged");
        //Les ordres passent par la copie commune
        CompoundTag orders = new CompoundTag();
        orders.put("orders", tag.getList("orders", Tag.TAG_COMPOUND));
        orders.put("hubs", hubs());
        ClientRobotics.receive(orders);
        //0.20.5 : chemin de fer
        ClientRailMap.receive(tag.getCompound("rail"));
        received = System.currentTimeMillis();
    }

    /** Hubs au format des menus d'ordres. */
    private static ListTag hubs() {
        ListTag list = new ListTag();
        for (Network network : networks) {
            for (Node node : network.nodes()) {
                if (node.hub()) {
                    CompoundTag entry = new CompoundTag();
                    entry.put("center", NbtUtils.writeBlockPos(node.pos()));
                    entry.putByte("kind", (byte) node.kind().ordinal());
                    entry.putByte("units", (byte) node.units());
                    entry.putByte("capacity", (byte) node.capacity());
                    entry.putByte("active", (byte) node.active());
                    entry.putShort("range", (short) node.coverage());
                    entry.putByte("status", (byte) node.status());
                    list.add(entry);
                }
            }
        }
        return list;
    }

    @Nullable
    public static Network networkAt(double x, double z) {
        for (Network network : networks) {
            if (network.covers(x, z)) {
                return network;
            }
        }
        return null;
    }

    @Nullable
    public static Point point(BlockPos pos) {
        for (Point point : points) {
            if (point.pos().equals(pos)) {
                return point;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------
    // Terrain
    // ------------------------------------------------------------------

    private static final class Region {

        final DynamicTexture texture = new DynamicTexture(128, 128, true);
        final ResourceLocation id;
        final short[] heights = new short[128 * 128];
        boolean dirty;

        Region(long key) {
            id = Minecraft.getInstance().getTextureManager().register(Pioneers.MODID + "_robomap_" + Long.toHexString(key), texture);
            NativeImage image = texture.getPixels();
            if (image != null) {
                image.fillRect(0, 0, 128, 128, 0);
            }
            texture.upload();
        }
    }

    /** Regions connues, de la moins recemment utilisee a la plus recente (0.20.46 (audit) : au plus {@link #MAX_REGIONS}). */
    private static final Long2ObjectLinkedOpenHashMap<Region> REGIONS = new Long2ObjectLinkedOpenHashMap<>();
    /** Chunk -> moment ou il a ete demande (ms). */
    private static final Long2LongOpenHashMap REQUESTED = new Long2LongOpenHashMap();
    private static final int REFRESH_MS = 30_000;
    /** 256 regions de 128 x 128 : bien plus que la vue de la carte (environ 8 x 8 regions), 16 Mo de textures au plus. */
    private static final int MAX_REGIONS = 256;

    /** Region de ce bloc de chunks, creee si besoin ; la plus ancienne est liberee au-dela de {@link #MAX_REGIONS}. */
    private static Region regionFor(long key) {
        Region region = REGIONS.getAndMoveToLast(key);
        if (region != null) {
            return region;
        }
        while (REGIONS.size() >= MAX_REGIONS) {
            long old = REGIONS.firstLongKey();
            Region gone = REGIONS.removeFirst();
            Minecraft.getInstance().getTextureManager().release(gone.id);
            //Ses chunks pourront etre redemandes des qu'ils reviennent dans la vue
            int rx = (int) old, rz = (int) (old >> 32);
            for (int cz = rz * 8; cz < rz * 8 + 8; cz++) {
                for (int cx = rx * 8; cx < rx * 8 + 8; cx++) {
                    REQUESTED.remove(key(cx, cz));
                }
            }
        }
        region = new Region(key);
        REGIONS.putAndMoveToLast(key, region);
        return region;
    }

    private static long key(int x, int z) {
        return ((long) z << 32) | (x & 0xFFFFFFFFL);
    }

    public static void receiveTiles(List<PacketRoboticsMap.Tile> tiles) {
        for (PacketRoboticsMap.Tile tile : tiles) {
            int rx = Math.floorDiv(tile.cx(), 8), rz = Math.floorDiv(tile.cz(), 8);
            long key = key(rx, rz);
            Region region = regionFor(key);
            NativeImage image = region.texture.getPixels();
            if (image == null) {
                continue;
            }
            int ox = Math.floorMod(tile.cx(), 8) * 16, oz = Math.floorMod(tile.cz(), 8) * 16;
            for (int z = 0; z < 16; z++) {
                for (int x = 0; x < 16; x++) {
                    int color = MapColor.getColorFromPackedId(tile.colors()[z * 16 + x] & 0xFF);
                    image.setPixelRGBA(ox + x, oz + z, color);
                    region.heights[(oz + z) * 128 + ox + x] = tile.heights()[z * 16 + x];
                }
            }
            region.dirty = true;
        }
        tilesVersion++;
    }

    /** Texture de la region (et la televerse si elle a change), ou null si rien n'est connu. */
    @Nullable
    public static ResourceLocation region(int rx, int rz) {
        Region region = REGIONS.getAndMoveToLast(key(rx, rz));
        if (region == null) {
            return null;
        }
        if (region.dirty) {
            region.dirty = false;
            region.texture.upload();
        }
        return region.id;
    }

    /** Couleur connue d'une colonne (ABGR, alpha nul si inconnue). */
    public static int color(int x, int z) {
        Region region = REGIONS.get(key(Math.floorDiv(x, 128), Math.floorDiv(z, 128)));
        NativeImage image = region == null ? null : region.texture.getPixels();
        return image == null ? 0 : image.getPixelRGBA(Math.floorMod(x, 128), Math.floorMod(z, 128));
    }

    /** Augmente a chaque reception de tuiles (le planificateur 3D se met alors a jour). */
    public static int tilesVersion;

    /** Hauteur connue d'une colonne (pour les zones), ou Integer.MIN_VALUE. */
    public static int height(int x, int z) {
        Region region = REGIONS.get(key(Math.floorDiv(x, 128), Math.floorDiv(z, 128)));
        if (region == null) {
            return Integer.MIN_VALUE;
        }
        short h = region.heights[Math.floorMod(z, 128) * 128 + Math.floorMod(x, 128)];
        return h == 0 ? Integer.MIN_VALUE : h;
    }

    /** Demande au serveur les tuiles visibles manquantes (ou trop vieilles). */
    public static void requestTiles(int cx0, int cz0, int cx1, int cz1) {
        long now = System.currentTimeMillis();
        LongArrayList wanted = new LongArrayList();
        for (int cz = cz0; cz <= cz1 && wanted.size() < 256; cz++) {
            for (int cx = cx0; cx <= cx1 && wanted.size() < 256; cx++) {
                long key = key(cx, cz);
                long at = REQUESTED.getOrDefault(key, 0L);
                if (now - at > REFRESH_MS) {
                    REQUESTED.put(key, now);
                    wanted.add(key);
                }
            }
        }
        if (!wanted.isEmpty()) {
            PiNetwork.sendToServer(new PacketRoboticsMap.TileRequest(wanted.toLongArray()));
        }
        //0.20.46 (audit) : les demandes perimees ne s'accumulent pas
        if (REQUESTED.size() > 16_384) {
            REQUESTED.long2LongEntrySet().removeIf(entry -> now - entry.getLongValue() > REFRESH_MS);
        }
    }

    public static void clear() {
        for (Region region : REGIONS.values()) {
            Minecraft.getInstance().getTextureManager().release(region.id);
        }
        REGIONS.clear();
        REQUESTED.clear();
        networks = List.of();
        points = List.of();
        units = List.of();
        alerts = List.of();
        ClientRailMap.clear();
    }
}
