package fr.pioneers.client.minimap;

import com.mojang.blaze3d.platform.NativeImage;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import javax.annotation.Nullable;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

/**
 * 1.10.3 : une region de 256 x 256 blocs (16 x 16 chunks) de la minimap : image du terrain (un pixel par colonne, alpha 0 = inconnu), deux
 * calques facultatifs (voies ferrees, routes robotiques ; crees au premier pixel), lieux remarquables par chunk. Les textures sont
 * envoyees a la carte graphique PAR CHUNK (sous-rectangle 16 x 16) quand un chunk change : rien ne se recalcule a l'image.
 */
final class MapRegion {

    static final int SIZE = 256;

    final int rx, rz;
    final NativeImage terrain;
    @Nullable
    NativeImage rail, road;
    @Nullable
    DynamicTexture terrainTex, railTex, roadTex;
    /** Chunks a envoyer (bit = cz * 16 + cx). */
    final long[] dirty = new long[4];
    /** Chunks releves pendant la session (le chargement du disque ne les ecrase pas). */
    final long[] scanned = new long[4];
    final Poi[][] pois = new Poi[256][];
    boolean diskDirty;
    long lastUse;
    /** Lecture du disque en cours. */
    @Nullable
    CompletableFuture<Loaded> loading;
    boolean closed;

    /** Ce que contient le disque pour la region (images a fermer apres la fusion). */
    record Loaded(@Nullable NativeImage terrain, @Nullable NativeImage rail, @Nullable NativeImage road, @Nullable CompoundTag pois) {

        void close() {
            if (terrain != null) {
                terrain.close();
            }
            if (rail != null) {
                rail.close();
            }
            if (road != null) {
                road.close();
            }
        }
    }

    MapRegion(int rx, int rz) {
        this.rx = rx;
        this.rz = rz;
        terrain = new NativeImage(SIZE, SIZE, true);
    }

    static long key(int rx, int rz) {
        return (long) rx << 32 | (rz & 0xFFFFFFFFL);
    }

    private static boolean bit(long[] set, int i) {
        return (set[i >> 6] >>> (i & 63) & 1L) != 0;
    }

    private static void mark(long[] set, int i) {
        set[i >> 6] |= 1L << (i & 63);
    }

    /** Ecrit un chunk releve (coordonnees du chunk dans la region 0..15). */
    void put(int lcx, int lcz, int[] pixels, int[] railPx, boolean anyRail, int[] roadPx, boolean anyRoad, Poi[] found) {
        int x0 = lcx * 16, z0 = lcz * 16;
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                terrain.setPixelRGBA(x0 + x, z0 + z, pixels[z * 16 + x]);
            }
        }
        if (anyRail || rail != null) {
            if (rail == null) {
                rail = new NativeImage(SIZE, SIZE, true);
            }
            copy(rail, railPx, x0, z0);
        }
        if (anyRoad || road != null) {
            if (road == null) {
                road = new NativeImage(SIZE, SIZE, true);
            }
            copy(road, roadPx, x0, z0);
        }
        int i = lcz * 16 + lcx;
        pois[i] = found.length == 0 ? null : found;
        mark(dirty, i);
        mark(scanned, i);
        diskDirty = true;
    }

    private static void copy(NativeImage img, int[] px, int x0, int z0) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                img.setPixelRGBA(x0 + x, z0 + z, px[z * 16 + x]);
            }
        }
    }

    boolean known(int lcx, int lcz) {
        return bit(scanned, lcz * 16 + lcx);
    }

    /** Fusion de ce que le disque contenait, pour les chunks pas encore releves pendant la session. */
    void merge(Loaded d) {
        for (int i = 0; i < 256; i++) {
            if (bit(scanned, i)) {
                continue;
            }
            int x0 = (i & 15) * 16, z0 = (i >> 4) * 16;
            if (d.terrain() != null && d.terrain().getWidth() == SIZE && d.terrain().getHeight() == SIZE) {
                copyRect(d.terrain(), terrain, x0, z0);
            }
            if (d.rail() != null && d.rail().getWidth() == SIZE) {
                if (rail == null) {
                    rail = new NativeImage(SIZE, SIZE, true);
                }
                copyRect(d.rail(), rail, x0, z0);
            }
            if (d.road() != null && d.road().getWidth() == SIZE) {
                if (road == null) {
                    road = new NativeImage(SIZE, SIZE, true);
                }
                copyRect(d.road(), road, x0, z0);
            }
            mark(dirty, i);
        }
        if (d.pois() != null) {
            for (String k : d.pois().getAllKeys()) {
                int i;
                try {
                    i = Integer.parseInt(k);
                } catch (NumberFormatException e) {
                    continue;
                }
                if (i < 0 || i >= 256 || bit(scanned, i)) {
                    continue;
                }
                ListTag list = d.pois().getList(k, Tag.TAG_COMPOUND);
                Poi[] arr = new Poi[list.size()];
                for (int j = 0; j < arr.length; j++) {
                    arr[j] = Poi.load(list.getCompound(j));
                }
                pois[i] = arr.length == 0 ? null : arr;
            }
        }
    }

    private static void copyRect(NativeImage from, NativeImage to, int x0, int z0) {
        for (int z = 0; z < 16; z++) {
            for (int x = 0; x < 16; x++) {
                to.setPixelRGBA(x0 + x, z0 + z, from.getPixelRGBA(x0 + x, z0 + z));
            }
        }
    }

    /** Envoie a la carte graphique au plus {@code budget} chunks changes ; renvoie le nombre envoye. Fil de rendu. */
    int upload(int budget) {
        if (closed) {
            return 0;
        }
        int sent = 0;
        if (terrainTex == null) {
            //premiere texture : tout part d'un coup (constructeur de DynamicTexture)
            terrainTex = new DynamicTexture(terrain);
            if (rail != null) {
                railTex = new DynamicTexture(rail);
            }
            if (road != null) {
                roadTex = new DynamicTexture(road);
            }
            Arrays.fill(dirty, 0L);
            return 4;
        }
        if (rail != null && railTex == null) {
            railTex = new DynamicTexture(rail);
        }
        if (road != null && roadTex == null) {
            roadTex = new DynamicTexture(road);
        }
        for (int w = 0; w < 4 && sent < budget; w++) {
            while (dirty[w] != 0 && sent < budget) {
                int b = Long.numberOfTrailingZeros(dirty[w]);
                dirty[w] &= ~(1L << b);
                int i = w * 64 + b, x0 = (i & 15) * 16, z0 = (i >> 4) * 16;
                sub(terrainTex, terrain, x0, z0);
                if (railTex != null && rail != null) {
                    sub(railTex, rail, x0, z0);
                }
                if (roadTex != null && road != null) {
                    sub(roadTex, road, x0, z0);
                }
                sent++;
            }
        }
        return sent;
    }

    private static void sub(DynamicTexture tex, NativeImage img, int x0, int z0) {
        tex.bind();
        img.upload(0, x0, z0, x0, z0, 16, 16, false, false, false, false);
    }

    boolean pending() {
        return terrainTex == null || (dirty[0] | dirty[1] | dirty[2] | dirty[3]) != 0;
    }

    /** Lieux de la region (tous chunks). */
    void collect(List<Poi> out) {
        for (Poi[] arr : pois) {
            if (arr != null) {
                for (Poi p : arr) {
                    out.add(p);
                }
            }
        }
    }

    CompoundTag poiTag() {
        CompoundTag t = new CompoundTag();
        for (int i = 0; i < 256; i++) {
            if (pois[i] != null) {
                ListTag list = new ListTag();
                for (Poi p : pois[i]) {
                    list.add(p.save());
                }
                t.put(Integer.toString(i), list);
            }
        }
        return t;
    }

    boolean anyPoi() {
        for (Poi[] arr : pois) {
            if (arr != null) {
                return true;
            }
        }
        return false;
    }

    /** Libere les images et textures (DynamicTexture ferme aussi son image). */
    void close() {
        closed = true;
        if (terrainTex != null) {
            terrainTex.close();
        } else {
            terrain.close();
        }
        if (railTex != null) {
            railTex.close();
        } else if (rail != null) {
            rail.close();
        }
        if (roadTex != null) {
            roadTex.close();
        } else if (road != null) {
            road.close();
        }
        CompletableFuture<Loaded> l = loading;
        if (l != null) {
            l.thenAccept(Loaded::close);
        }
    }
}
