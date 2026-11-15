package fr.pioneers.farm.client.dish;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.kinetics.client.KDraw;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 (lot A1) : maillages des plats et boissons servis, lus dans {@code assets/pioneers/farm/placed_food.json} (ecrit par
 * {@code tools/gc/farm2/placed_dish.py} : boites en px, materiaux de la palette, mode d'entame de chaque boite). Chaque etape d'entame
 * (bouchees prises) est construite une fois puis gardee : sommets deja tournes, gonfles (deux boites ne sont jamais dans le meme plan),
 * UV dans l'atlas ; on ne refait que la transformation a chaque image. Recharge avec les textures ({@link KDraw#generation()}).
 */
public final class PlacedFoodMeshes {

    private PlacedFoodMeshes() {
    }

    private static final ResourceLocation FILE = Pioneers.rl("farm/placed_food.json");
    /** Une boite : 10 nombres (+ 5 si tournee : axe 0/1/2, angle, origine). */
    private static final int ROW = 10;

    /**
     * Un plat : bouchees, support, rayon au sol (px depuis le centre), coupe (axe 0 = x, 2 = z, de lo a hi), boites. 1.9.2 (tas,
     * {@code PlacedFoodRenderer}) : dispositions par nombre ({@code lay[n - 1]} = dx, dy, dz, lacet par unite ; null pour un plat),
     * contenant commun dessine une fois ({@code vessel}, cle « #... »), contenant a niveau ({@code fill} : un seul maillage, bouchees =
     * capacite, etape = capacite - nombre).
     */
    public record Food(int bites, int base, float radius, float height, int axis, float lo, float hi, float[][] boxes, @Nullable float[][] lay,
                       @Nullable String vessel, boolean fill) {

        public Food(int bites, int base, float radius, float height, int axis, float lo, float hi, float[][] boxes) {
            this(bites, base, radius, height, axis, lo, hi, boxes, null, null, false);
        }
    }

    /**
     * Maillage d'une etape : par quadrilatere, materiau ; par sommet x, y, z (blocs), u, v (atlas) ; normale par quadrilatere. Les
     * {@code lod} premiers quadrilateres sont ceux des grosses boites (rendu de loin).
     */
    public record Mesh(int quads, int lod, int[] mats, float[] verts, float[] normals) {
    }

    private static Map<String, Food> foods;
    /** Palette : r, g, b, a, grain ; r < 0 : couleur du contenu x (g / 100). */
    private static int[][] palette = new int[0][];
    /** Maillages construits : par plat, un par etape d'entame. */
    private static final Map<String, Mesh[]> MESHES = new HashMap<>();
    private static int generation = Integer.MIN_VALUE;
    private static final TextureAtlasSprite[] GRAIN = new TextureAtlasSprite[4];

    public static int[][] palette() {
        return palette;
    }

    @Nullable
    public static Food food(String id) {
        ensure();
        return foods.get(id);
    }

    private static void ensure() {
        if (generation != KDraw.generation() || foods == null) {
            generation = KDraw.generation();
            MESHES.clear();
            for (int i = 0; i < GRAIN.length; i++) {
                GRAIN[i] = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Pioneers.rl("block/farm/dish/grain_" + i));
            }
            //Relu a chaque rechargement des ressources (paquets de ressources)
            load();
        }
    }

    private static void load() {
        Map<String, Food> out = new HashMap<>();
        try {
            Optional<Resource> res = Minecraft.getInstance().getResourceManager().getResource(FILE);
            if (res.isPresent()) {
                try (Reader r = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                    JsonObject root = JsonParser.parseReader(r).getAsJsonObject();
                    JsonArray pal = root.getAsJsonArray("palette");
                    int[][] p = new int[pal.size()][];
                    for (int i = 0; i < p.length; i++) {
                        JsonArray e = pal.get(i).getAsJsonArray();
                        p[i] = new int[]{e.get(0).getAsInt(), e.get(1).getAsInt(), e.get(2).getAsInt(), e.get(3).getAsInt(), e.get(4).getAsInt()};
                    }
                    palette = p;
                    for (Map.Entry<String, JsonElement> en : root.getAsJsonObject("foods").entrySet()) {
                        JsonObject f = en.getValue().getAsJsonObject();
                        JsonArray bx = f.getAsJsonArray("boxes");
                        float[][] boxes = new float[bx.size()][];
                        for (int i = 0; i < boxes.length; i++) {
                            JsonArray row = bx.get(i).getAsJsonArray();
                            float[] b = new float[row.size()];
                            for (int k = 0; k < b.length; k++) {
                                b[k] = row.get(k).getAsFloat();
                            }
                            boxes[i] = b;
                        }
                        //1.9.2 : dispositions des tas (une liste par nombre d'unites, 4 nombres par unite)
                        float[][] lay = null;
                        if (f.has("lay")) {
                            JsonArray la = f.getAsJsonArray("lay");
                            lay = new float[la.size()][];
                            for (int n = 0; n < lay.length; n++) {
                                JsonArray units = la.get(n).getAsJsonArray();
                                float[] flat = new float[units.size() * 4];
                                for (int u = 0; u < units.size(); u++) {
                                    JsonArray p4 = units.get(u).getAsJsonArray();
                                    for (int k = 0; k < 4; k++) {
                                        flat[u * 4 + k] = p4.get(k).getAsFloat();
                                    }
                                }
                                lay[n] = flat;
                            }
                        }
                        out.put(en.getKey(), new Food(f.get("b").getAsInt(), f.get("base").getAsInt(), f.get("r").getAsFloat(), f.get("h").getAsFloat(),
                              f.has("ax") ? f.get("ax").getAsInt() : -1, f.has("lo") ? f.get("lo").getAsFloat() : 0,
                              f.has("hi") ? f.get("hi").getAsFloat() : 0, boxes, lay, f.has("vessel") ? f.get("vessel").getAsString() : null,
                              f.has("fill")));
                    }
                }
            }
        } catch (Exception e) {
            Pioneers.LOGGER.error("Plats servis : lecture de {} impossible", FILE, e);
        }
        foods = out;
    }

    /** Maillage de l'etape {@code taken} (bouchees prises) d'un plat. */
    public static Mesh mesh(String id, Food food, int taken) {
        Mesh[] all = MESHES.computeIfAbsent(id, k -> new Mesh[Math.max(1, food.bites())]);
        int t = Math.max(0, Math.min(all.length - 1, taken));
        Mesh m = all[t];
        if (m == null) {
            m = build(food, food.bites() <= 0 ? 0 : t / (float) food.bites());
            all[t] = m;
        }
        return m;
    }

    // Coins d'une face (ordre vanilla FaceInfo : faces vues de l'exterieur) ; bits : 4 = x max, 2 = y max, 1 = z max
    private static final int[][] FACE = {
          {1, 0, 4, 5},      //bas : (x0,y0,z1) (x0,y0,z0) (x1,y0,z0) (x1,y0,z1)
          {2, 3, 7, 6},      //haut : (x0,y1,z0) (x0,y1,z1) (x1,y1,z1) (x1,y1,z0)
          {6, 4, 0, 2},      //nord : (x1,y1,z0) (x1,y0,z0) (x0,y0,z0) (x0,y1,z0)
          {3, 1, 5, 7},      //sud
          {2, 0, 1, 3},      //ouest
          {7, 5, 4, 6},      //est
    };
    private static final float[][] NORMAL = {{0, -1, 0}, {0, 1, 0}, {0, 0, -1}, {0, 0, 1}, {-1, 0, 0}, {1, 0, 0}};

    private static Mesh build(Food food, float f) {
        List<float[]> big = new ArrayList<>(), small = new ArrayList<>();
        List<Integer> bigM = new ArrayList<>(), smallM = new ArrayList<>();
        float[][] boxes = food.boxes();
        for (int i = 0; i < boxes.length; i++) {
            float[] b = boxes[i];
            int mode = (int) b[8];
            float p = b[9];
            float x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
            boolean rot = b.length > ROW;
            switch (mode) {
                case 1 -> {
                    //Coupee depuis le cote du joueur (nord) ou l'ouest
                    if (food.axis() >= 0 && f > 0) {
                        float cut = food.lo() + (food.hi() - food.lo()) * f;
                        float lo = food.axis() == 0 ? x0 : z0, hi = food.axis() == 0 ? x1 : z1;
                        if (rot ? (lo + hi) / 2 < cut : hi <= cut + 0.05F) {
                            continue;
                        }
                        if (!rot && lo < cut) {
                            if (food.axis() == 0) {
                                x0 = cut;
                            } else {
                                z0 = cut;
                            }
                        }
                    }
                }
                case 2 -> {
                    if (f >= p) {
                        continue;
                    }
                }
                case 3 -> {
                    y0 -= p * f;
                    y1 -= p * f;
                }
                case 4 -> y1 = Math.max(y0 + 0.05F, y1 - p * f);
                case 5 -> {
                    if (f <= 0) {
                        continue;
                    }
                }
                case 6 -> {
                    if (f > 0) {
                        continue;
                    }
                }
                default -> {
                }
            }
            //Gonflement propre a chaque boite : deux faces de boites differentes ne sont jamais dans le meme plan
            float e = 0.008F * (1 + i % 7);
            x0 -= e;
            y0 -= e;
            z0 -= e;
            x1 += e;
            y1 += e;
            z1 += e;
            boolean large = Math.max(x1 - x0, Math.max(y1 - y0, z1 - z0)) >= 1.2F;
            float[][] c = new float[8][];
            for (int k = 0; k < 8; k++) {
                c[k] = new float[]{(k & 4) != 0 ? x1 : x0, (k & 2) != 0 ? y1 : y0, (k & 1) != 0 ? z1 : z0};
            }
            float[][] n = NORMAL;
            if (rot) {
                int ax = (int) b[10];
                float ang = (float) Math.toRadians(b[11]);
                float ox = b[12], oy = b[13], oz = b[14];
                for (float[] q : c) {
                    rotate(q, ax, ang, ox, oy, oz);
                }
                n = new float[6][];
                for (int k = 0; k < 6; k++) {
                    float[] v = NORMAL[k].clone();
                    rotate(v, ax, ang, 0, 0, 0);
                    n[k] = v;
                }
            }
            int side = (int) b[6], top = (int) b[7];
            for (int face = 0; face < 6; face++) {
                int mat = face == 1 ? top : side;
                int grain = mat >= 0 && mat < palette.length ? palette[mat][4] : 0;
                TextureAtlasSprite sp = GRAIN[Math.max(0, Math.min(3, grain))];
                float[] quad = new float[23];
                //UV : coordonnees de la face non tournee (regle des modeles vanilla), ramenees dans le motif de 16 px
                float ua, ub, va, vb;
                switch (face) {
                    case 0, 1 -> {
                        ua = x0; ub = x1; va = z0; vb = z1;
                    }
                    case 2, 3 -> {
                        ua = x0; ub = x1; va = 16 - y1; vb = 16 - y0;
                    }
                    default -> {
                        ua = z0; ub = z1; va = 16 - y1; vb = 16 - y0;
                    }
                }
                float du = (float) Math.floor(Math.min(ua, ub) / 16F) * 16F, dv = (float) Math.floor(Math.min(va, vb) / 16F) * 16F;
                ua -= du;
                ub -= du;
                va -= dv;
                vb -= dv;
                int[] idx = FACE[face];
                for (int v = 0; v < 4; v++) {
                    float[] pt = c[idx[v]];
                    float[] raw = {(idx[v] & 4) != 0 ? x1 : x0, (idx[v] & 2) != 0 ? y1 : y0, (idx[v] & 1) != 0 ? z1 : z0};
                    float u, w;
                    switch (face) {
                        case 0, 1 -> {
                            u = raw[0] - du;
                            w = raw[2] - dv;
                        }
                        case 2, 3 -> {
                            u = raw[0] - du;
                            w = 16 - raw[1] - dv;
                        }
                        default -> {
                            u = raw[2] - du;
                            w = 16 - raw[1] - dv;
                        }
                    }
                    quad[v * 5] = pt[0] / 16F;
                    quad[v * 5 + 1] = pt[1] / 16F;
                    quad[v * 5 + 2] = pt[2] / 16F;
                    quad[v * 5 + 3] = sp.getU(Math.max(0, Math.min(16, u)));
                    quad[v * 5 + 4] = sp.getV(Math.max(0, Math.min(16, w)));
                }
                quad[20] = n[face][0];
                quad[21] = n[face][1];
                quad[22] = n[face][2];
                (large ? big : small).add(quad);
                (large ? bigM : smallM).add(mat);
            }
        }
        int q = big.size() + small.size();
        int[] mats = new int[q];
        float[] verts = new float[q * 20], normals = new float[q * 3];
        int k = 0;
        for (int pass = 0; pass < 2; pass++) {
            List<float[]> list = pass == 0 ? big : small;
            List<Integer> ml = pass == 0 ? bigM : smallM;
            for (int i = 0; i < list.size(); i++, k++) {
                float[] quad = list.get(i);
                System.arraycopy(quad, 0, verts, k * 20, 20);
                System.arraycopy(quad, 20, normals, k * 3, 3);
                mats[k] = ml.get(i);
            }
        }
        return new Mesh(q, big.size(), mats, verts, normals);
    }

    /** Rotation d'element (meme regle que les modeles vanilla et que tools/gc/farm2/placed_dish.erot). */
    private static void rotate(float[] p, int axis, float a, float ox, float oy, float oz) {
        float x = p[0] - ox, y = p[1] - oy, z = p[2] - oz;
        float c = (float) Math.cos(a), s = (float) Math.sin(a);
        switch (axis) {
            case 1 -> {
                float nx = x * c + z * s, nz = -x * s + z * c;
                x = nx;
                z = nz;
            }
            case 0 -> {
                float ny = y * c - z * s, nz = y * s + z * c;
                y = ny;
                z = nz;
            }
            default -> {
                float nx = x * c - y * s, ny = x * s + y * c;
                x = nx;
                y = ny;
            }
        }
        p[0] = x + ox;
        p[1] = y + oy;
        p[2] = z + oz;
    }
}
