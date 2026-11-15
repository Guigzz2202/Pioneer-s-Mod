package fr.pioneers.client.minimap;

import fr.pioneers.Pioneers;
import fr.pioneers.client.PiKeys;
import fr.pioneers.society.clan.client.ui.ClanMapAccess;
import fr.pioneers.society.rp.client.RpClient;
import fr.pioneers.core.profile.RpActions;
import java.nio.file.Path;
import java.util.Locale;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.network.chat.Component;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkStatus;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.ChunkEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/**
 * 1.10.3 : la minimap cote client. Releve des chunks : chaque chunk recu du serveur ({@code ChunkEvent.Load} cote client) est mis en file,
 * avec ses voisins du sud et de l'est (leur ombrage lit sa bordure) ; au plus {@code chunksPerTick} releves par tick (la moitie dans une
 * dimension a plafond), et les 9 chunks autour du joueur sont revus tour a tour (un tous les 5 ticks : ce que le joueur change apparait en
 * moins de 2 s). Une dimension = une carte ({@link MapStore}), un monde ou un serveur = un dossier. Touches, point de mort, abonnement a la
 * carte des clans, instantane des entites.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class MinimapClient {

    static final MinimapState S = new MinimapState();
    private static final MinimapLayers LAYERS = new MinimapLayers();
    private static int ticks;
    /** La carte plein ecran est ouverte (les claims et membres restent demandes). */
    static boolean mapOpen;

    private MinimapClient() {
    }

    // ------------------------------------------------------------------ enregistrements (bus du mod)
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
    public static final class Setup {

        private Setup() {
        }

        @SubscribeEvent
        public static void setup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> RpClient.on(RpActions.CLAN_MATES, ClanMapAccess::receiveMates));
        }

        @SubscribeEvent
        public static void overlays(RegisterGuiOverlaysEvent event) {
            //1.0.4-beta (lot 5) : marqueurs des waypoints projetes a l'ecran (sous la minimap et la barre d'objets : ils ne les cachent pas)
            event.registerBelow(VanillaGuiOverlay.HOTBAR.id(), "waypoints", (gui, g, pt, w, h) -> WaypointHud.render(g, w, h));
            event.registerBelow(VanillaGuiOverlay.DEBUG_TEXT.id(), "minimap", (gui, g, pt, w, h) -> MinimapHud.render(g, w, h, pt));
        }
    }

    // ------------------------------------------------------------------ chunks
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (!(event.getLevel() instanceof ClientLevel) || !(event.getChunk() instanceof LevelChunk chunk)) {
            return;
        }
        ChunkPos p = chunk.getPos();
        S.queue.add(ChunkPos.asLong(p.x, p.z));
        S.queue.add(ChunkPos.asLong(p.x, p.z + 1));
        S.queue.add(ChunkPos.asLong(p.x + 1, p.z));
    }

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        closeAll();
    }

    private static boolean migrated;

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (S.store != null) {
                closeAll();
            }
            return;
        }
        if (mc.level != S.level) {
            switchLevel(mc, mc.level);
        }
        ticks++;
        if (!migrated) {
            //1.12.1 (F1) : nouvelles valeurs par defaut, une fois (fichier charge en partie)
            MinimapConfig.migrate();
            migrated = MinimapConfig.CONFIG_VERSION != null && MinimapConfig.num(MinimapConfig.CONFIG_VERSION, 0) >= MinimapConfig.VERSION;
        }
        MinimapLayers l = LAYERS.read();
        keys(mc);
        scan(mc.level, mc.player.chunkPosition());
        long now = Util.getMillis();
        if (S.store != null) {
            S.store.tick(now, MinimapConfig.num(MinimapConfig.MAX_REGIONS, 96), 48);
        }
        EntityMarkers.tick(mc.level, mc.player, l);
        death(mc);
        boolean wanted = (l.claims || l.mates) && (mapOpen || MinimapHud.visible(mc));
        if (wanted) {
            ClanMapAccess.keep(l.mates);
        } else {
            ClanMapAccess.release();
        }
    }

    // ------------------------------------------------------------------ niveau, dossiers
    private static void switchLevel(Minecraft mc, ClientLevel level) {
        EnvironmentInfo.clear();
        if (S.store != null) {
            S.store.close();
            S.store = null;
        }
        boolean newWorld = S.worldDir == null;
        S.level = level;
        S.queue.clear();
        S.dim = level.dimension().location().toString();
        if (newWorld) {
            S.worldDir = mc.gameDirectory.toPath().resolve("pioneers").resolve("minimap").resolve(worldKey(mc));
            Waypoints.open(S.worldDir);
        }
        boolean disk = MinimapConfig.on(MinimapConfig.DISK_CACHE, true);
        S.store = new MapStore(S.dim, disk ? S.worldDir.resolve(safe(S.dim)) : null);
        ClaimLayer.clear();
        EntityMarkers.clear();
        //chunks deja la (arrivee dans la dimension) : releves en premier, du plus proche au plus loin
        int r = mc.options.getEffectiveRenderDistance() + 1;
        ChunkPos c = mc.player == null ? new ChunkPos(0, 0) : mc.player.chunkPosition();
        for (int d = 0; d <= r; d++) {
            for (int dz = -d; dz <= d; dz++) {
                for (int dx = -d; dx <= d; dx++) {
                    if (Math.max(Math.abs(dx), Math.abs(dz)) == d) {
                        S.queue.add(ChunkPos.asLong(c.x + dx, c.z + dz));
                    }
                }
            }
        }
    }

    static void closeAll() {
        EnvironmentInfo.clear();
        if (S.store != null) {
            S.store.close();
            S.store = null;
        }
        Waypoints.close();
        S.worldDir = null;
        S.level = null;
        S.queue.clear();
        ClaimLayer.clear();
        EntityMarkers.clear();
        ClanMapAccess.clear();
    }

    /** Monde solo : nom du dossier de sauvegarde ; serveur : son adresse. */
    private static String worldKey(Minecraft mc) {
        if (mc.hasSingleplayerServer() && mc.getSingleplayerServer() != null) {
            Path root = mc.getSingleplayerServer().getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
            Path name = root.getFileName();
            return "sp_" + safe(name == null ? "world" : name.toString());
        }
        ServerData sd = mc.getCurrentServer();
        return sd == null ? "unknown" : "mp_" + safe(sd.ip);
    }

    static String safe(String s) {
        StringBuilder b = new StringBuilder();
        for (char ch : s.toLowerCase(Locale.ROOT).toCharArray()) {
            b.append(Character.isLetterOrDigit(ch) || ch == '-' || ch == '.' ? ch : '_');
        }
        return b.length() == 0 ? "_" : b.toString();
    }

    // ------------------------------------------------------------------ releve
    private static void scan(ClientLevel level, ChunkPos here) {
        if (S.store == null) {
            return;
        }
        boolean ceiling = level.dimensionType().hasCeiling();
        if (ceiling && MinimapConfig.pick(MinimapConfig.CEILING, MinimapConfig.Ceiling.IGNORE) == MinimapConfig.Ceiling.HIDE) {
            S.queue.clear();
            return;
        }
        int budget = MinimapConfig.num(MinimapConfig.CHUNKS_PER_TICK, 3);
        if (ceiling) {
            budget = Math.max(1, budget / 2);
        }
        S.scanner.settings(MinimapConfig.on(MinimapConfig.SHADING, true), MinimapConfig.on(MinimapConfig.BIOME_TINT, true),
              MinimapConfig.on(MinimapConfig.WATER_DEPTH, true), LAYERS.cRail, LAYERS.cRoad);
        long now = Util.getMillis();
        int done = 0;
        //le chunk proche revu ce tick (un tous les 5 ticks, tour a tour)
        if (ticks % 5 == 0) {
            int i = S.near++ % 9;
            scanOne(level, here.x + i % 3 - 1, here.z + i / 3 - 1, now);
            done++;
        }
        while (done < budget && !S.queue.isEmpty()) {
            long k = S.queue.removeFirstLong();
            if (scanOne(level, ChunkPos.getX(k), ChunkPos.getZ(k), now)) {
                done++;
            }
        }
    }

    private static boolean scanOne(ClientLevel level, int cx, int cz, long now) {
        LevelChunk chunk = level.getChunkSource().getChunk(cx, cz, ChunkStatus.FULL, false);
        if (chunk == null || chunk.isEmpty() || S.store == null) {
            return false;
        }
        long t0 = System.nanoTime();
        S.scanner.scan(level, chunk);
        MapRegion r = S.store.region(cx >> 4, cz >> 4, true, now);
        ChunkScanner sc = S.scanner;
        r.put(cx & 15, cz & 15, sc.pixels, sc.rail, sc.anyRail, sc.road, sc.anyRoad, sc.found.toArray(new Poi[0]));
        S.scanNanos += System.nanoTime() - t0;
        S.scanned++;
        return true;
    }

    // ------------------------------------------------------------------ touches
    private static void keys(Minecraft mc) {
        while (PiKeys.MINIMAP.consumeClick()) {
            boolean on = !MinimapConfig.on(MinimapConfig.ENABLED, true);
            MinimapConfig.set(MinimapConfig.ENABLED, on);
            MinimapConfig.save();
            mc.player.displayClientMessage(Component.translatable(on ? "minimap.pioneers.shown" : "minimap.pioneers.hidden"), true);
        }
        while (PiKeys.WORLD_MAP.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new WorldMapScreen(null));
            }
        }
        while (PiKeys.WAYPOINT.consumeClick()) {
            if (mc.screen == null && Waypoints.ready()) {
                mc.setScreen(new WaypointEditScreen(null, null, newAt(mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ())));
            }
        }
        while (PiKeys.MINIMAP_ZOOM_IN.consumeClick()) {
            zoom(1);
        }
        while (PiKeys.MINIMAP_ZOOM_OUT.consumeClick()) {
            zoom(-1);
        }
        while (PiKeys.MINIMAP_SETTINGS.consumeClick()) {
            if (mc.screen == null) {
                mc.setScreen(new MinimapSettingsScreen(null));
            }
        }
    }

    static void zoom(int step) {
        int z = Math.max(0, Math.min(MinimapConfig.ZOOMS.length - 1, MinimapConfig.num(MinimapConfig.ZOOM, 3) + step));
        MinimapConfig.set(MinimapConfig.ZOOM, z);
        MinimapConfig.save();
    }

    /** Waypoint neuf a une position (nom « Point n », couleur suivante de la palette). */
    static Waypoint newAt(int x, int y, int z) {
        int n = Waypoints.in(S.dim).size() + 1;
        int color = Waypoints.PALETTE[(n - 1) % Waypoints.PALETTE.length];
        return new Waypoint(Component.translatable("minimap.pioneers.waypoint.default", n).getString(), x, y, z, S.dim, color, 0);
    }

    // ------------------------------------------------------------------ point de mort
    private static void death(Minecraft mc) {
        boolean dead = mc.player.isDeadOrDying();
        if (dead && !S.deathSeen) {
            S.deathSeen = true;
            if (MinimapConfig.on(MinimapConfig.W_DEATHS, true) && Waypoints.ready()) {
                long day = mc.level.getDayTime() / 24000L + 1;
                Waypoint w = new Waypoint(Component.translatable("minimap.pioneers.death_name", day, MinimapHud.clock(mc.level.getDayTime())).getString(),
                      mc.player.getBlockX(), mc.player.getBlockY(), mc.player.getBlockZ(), S.dim, 0xE5484D, 5);
                Waypoints.death(w, MinimapConfig.num(MinimapConfig.W_DEATH_KEEP, 3));
            }
        } else if (!dead) {
            S.deathSeen = false;
        }
    }

    @Nullable
    static MapStore store() {
        return S.store;
    }
}
