package fr.pioneers.client.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import fr.pioneers.Pioneers;
import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.Util;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;

/**
 * 1.10.3 : carte exploree d'UNE dimension d'un monde (ou serveur) : regions en memoire (au plus {@code maxRegions}, les moins recemment
 * vues sont sauvees puis liberees), lecture et ecriture du disque sur les fils d'entree-sortie du jeu (jamais sur le fil de rendu : une
 * image est copiee avant d'etre ecrite), dossier {@code <jeu>/pioneers/minimap/<monde>/<dimension>}.
 */
final class MapStore {

    final String dim;
    @Nullable
    private final Path dir;
    private final Long2ObjectLinkedOpenHashMap<MapRegion> regions = new Long2ObjectLinkedOpenHashMap<>();
    private int loadsInFlight;
    private long lastSave;

    MapStore(String dim, @Nullable Path dir) {
        this.dim = dim;
        this.dir = dir;
        if (dir != null) {
            try {
                Files.createDirectories(dir);
            } catch (IOException e) {
                Pioneers.LOGGER.warn("Minimap : dossier {} impossible a creer", dir, e);
            }
        }
    }

    private Path file(int rx, int rz, String suffix) {
        return dir.resolve("r." + rx + "." + rz + suffix);
    }

    /** Region deja en memoire, ou null (sans rien charger). */
    @Nullable
    MapRegion peek(int rx, int rz) {
        return regions.get(MapRegion.key(rx, rz));
    }

    /**
     * Region (rx, rz) : en memoire, sinon creee ; si le disque la connait, sa lecture commence ({@code load} faux : seulement si elle existe
     * deja en memoire, null sinon, pour la carte plein ecran qui ne cree pas de region vide).
     */
    @Nullable
    MapRegion region(int rx, int rz, boolean create, long now) {
        long k = MapRegion.key(rx, rz);
        MapRegion r = regions.getAndMoveToLast(k);
        if (r == null) {
            boolean onDisk = dir != null && Files.exists(file(rx, rz, ".png"));
            if (!create && !onDisk) {
                return null;
            }
            if (!create && loadsInFlight >= 4) {
                return null;
            }
            r = new MapRegion(rx, rz);
            regions.putAndMoveToLast(k, r);
            if (onDisk) {
                startLoad(r);
            }
        }
        r.lastUse = now;
        return r;
    }

    private void startLoad(MapRegion r) {
        Path png = file(r.rx, r.rz, ".png"), rail = file(r.rx, r.rz, ".rail.png"), road = file(r.rx, r.rz, ".road.png"),
              poi = file(r.rx, r.rz, ".poi.dat");
        loadsInFlight++;
        r.loading = CompletableFuture.supplyAsync(() -> new MapRegion.Loaded(read(png), read(rail), read(road), readTag(poi)), Util.ioPool());
    }

    @Nullable
    private static NativeImage read(Path p) {
        if (!Files.exists(p)) {
            return null;
        }
        try (InputStream in = Files.newInputStream(p)) {
            return NativeImage.read(in);
        } catch (IOException | RuntimeException e) {
            Pioneers.LOGGER.debug("Minimap : {} illisible", p, e);
            return null;
        }
    }

    @Nullable
    private static CompoundTag readTag(Path p) {
        if (!Files.exists(p)) {
            return null;
        }
        try {
            return NbtIo.readCompressed(p.toFile());
        } catch (IOException | RuntimeException e) {
            Pioneers.LOGGER.debug("Minimap : {} illisible", p, e);
            return null;
        }
    }

    /**
     * Une fois par tick : lectures terminees fusionnees, chunks changes envoyes a la carte graphique ({@code uploads} au plus), regions en
     * trop liberees, sauvegarde periodique (toutes les 60 s, 2 regions par tick au plus).
     */
    void tick(long now, int maxRegions, int uploads) {
        int sent = 0;
        for (MapRegion r : regions.values()) {
            if (r.loading != null && r.loading.isDone()) {
                MapRegion.Loaded d = r.loading.getNow(null);
                r.loading = null;
                loadsInFlight = Math.max(0, loadsInFlight - 1);
                if (d != null) {
                    r.merge(d);
                    d.close();
                }
            }
            if (sent < uploads && r.loading == null && r.pending()) {
                sent += r.upload(uploads - sent);
            }
        }
        while (regions.size() > maxRegions) {
            MapRegion old = regions.get(regions.firstLongKey());
            if (now - old.lastUse < 2000) {
                break;
            }
            regions.removeFirst();
            save(old);
            old.close();
        }
        if (now - lastSave > 60_000) {
            lastSave = now;
            int n = 0;
            for (MapRegion r : regions.values()) {
                if (r.diskDirty && n++ < 8) {
                    save(r);
                }
            }
        }
    }

    /** Copie la region et l'ecrit sur un fil d'entree-sortie. */
    void save(MapRegion r) {
        if (dir == null || !r.diskDirty || r.closed || r.loading != null) {
            return;
        }
        r.diskDirty = false;
        NativeImage t = copy(r.terrain), rail = r.rail == null ? null : copy(r.rail), road = r.road == null ? null : copy(r.road);
        CompoundTag pois = r.anyPoi() ? r.poiTag() : null;
        Path png = file(r.rx, r.rz, ".png"), railP = file(r.rx, r.rz, ".rail.png"), roadP = file(r.rx, r.rz, ".road.png"),
              poiP = file(r.rx, r.rz, ".poi.dat");
        Util.ioPool().execute(() -> {
            try {
                write(t, png);
                write(rail, railP);
                write(road, roadP);
                if (pois != null) {
                    NbtIo.writeCompressed(pois, poiP.toFile());
                }
            } catch (IOException | RuntimeException e) {
                Pioneers.LOGGER.warn("Minimap : region {} {} non sauvee", r.rx, r.rz, e);
            }
        });
    }

    private static void write(@Nullable NativeImage img, Path p) throws IOException {
        if (img == null) {
            return;
        }
        try {
            img.writeToFile(p);
        } finally {
            img.close();
        }
    }

    private static NativeImage copy(NativeImage from) {
        NativeImage c = new NativeImage(from.getWidth(), from.getHeight(), false);
        c.copyFrom(from);
        return c;
    }

    List<MapRegion> loaded() {
        return new ArrayList<>(regions.values());
    }

    int size() {
        return regions.size();
    }

    /** Sauve tout et libere (changement de dimension, deconnexion). */
    void close() {
        for (MapRegion r : regions.values()) {
            save(r);
            r.close();
        }
        regions.clear();
        loadsInFlight = 0;
    }
}
