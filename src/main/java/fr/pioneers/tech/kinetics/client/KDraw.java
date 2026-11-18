package fr.pioneers.tech.kinetics.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Function;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * Dessin des pieces de l'age mecanique : boites texturees en pixels de bloc (0..16), pivotees a volonte, avec les materiaux
 * {@code textures/block/mechanics/*}. Les faces de plus de 16 px sont decoupees pour garder la texture a l'echelle (pas d'etirement).
 * <p>
 * 0.20.46 (audit) : chemin chaud sans allocation (sommets ecrits en un appel, vecteurs et quaternion de travail, push/pop qui sauvent et
 * restaurent la pose courante au lieu d'en empiler une nouvelle), textures en cache sans verrou, type de rendu fixe (voir {@link #renderType()}).
 * Les vecteurs de travail ne servent que sur le fil qui a cree le premier KDraw (fil de rendu) ; ailleurs, chemin d'origine.
 */
public final class KDraw {

    /** Coordonnees d'une texture dans l'atlas (u0..u1, v0..v1) ; {@code name} sert au banc d'essai des apercus. */
    public record Tex(String name, float u0, float u1, float v0, float v1) {

        public float u(float px) {
            return u0 + (u1 - u0) * px / 16F;
        }

        public float v(float px) {
            return v0 + (v1 - v0) * px / 16F;
        }
    }

    /** Resolution des textures (remplacee par le banc d'essai hors du jeu). */
    public static Function<String, Tex> resolver = name -> {
        TextureAtlasSprite s = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(Pioneers.rl("block/mechanics/" + name));
        return new Tex(name, s.getU0(), s.getU1(), s.getV0(), s.getV1());
    };
    private static final Map<String, Tex> CACHE = new ConcurrentHashMap<>();
    private static final Map<TextureAtlasSprite, Tex> SPRITES = new ConcurrentHashMap<>();
    /** 0.20.46 (audit) : augmente a chaque rechargement des textures (caches de geometrie enregistree). */
    private static volatile int generation;

    /**
     * 0.20.46 (audit) : type de rendu des pieces : {@link Sheets#cutoutBlockSheet()} (tampon fixe : rendu groupe, sans vidage quand un autre
     * type s'intercale). Les faces culees ne changent rien : toutes les boites sont tournees vers l'exterieur (verifie au banc : sens des
     * sommets = normale sur tous les quadrilateres) et les nuanceurs « cutout » et « cutout_no_cull » sont identiques.
     */
    private static RenderType type;

    // 0.20.46 (audit) : objets de travail du fil de rendu
    private static Thread owner;
    private static final Vector4f POS = new Vector4f();
    private static final Vector3f NRM = new Vector3f();
    private static final Quaternionf ROT = new Quaternionf();
    private static Matrix4f[] savedPose = new Matrix4f[0];
    private static Matrix3f[] savedNormal = new Matrix3f[0];
    private static int depth;

    public final PoseStack pose;
    private final VertexConsumer vc;
    public final int light, overlay;
    private float red = 1, green = 1, blue = 1, alpha = 1;
    /** Fil de rendu : objets de travail partages (sinon allocations comme avant). */
    private final boolean shared;
    // Normale de la face en cours (transformee une fois par face)
    private float fnx, fny, fnz;

    public KDraw(PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        this(pose, buffers.getBuffer(renderType()), light, overlay);
    }

    public KDraw(PoseStack pose, VertexConsumer vc, int light, int overlay) {
        this.pose = pose;
        this.vc = vc;
        this.light = light;
        this.overlay = overlay;
        Thread current = Thread.currentThread();
        if (owner == null) {
            owner = current;
        }
        this.shared = owner == current;
    }

    /** Type de rendu des pieces (mis en cache). */
    public static RenderType renderType() {
        RenderType t = type;
        if (t == null) {
            t = type = Sheets.cutoutBlockSheet();
        }
        return t;
    }

    /** Texture d'un materiau ({@code pioneers:block/mechanics/<nom>}), mise en cache jusqu'au prochain rechargement des textures. */
    public static Tex sprite(String name) {
        Tex t = CACHE.get(name);
        if (t == null) {
            t = resolver.apply(name);
            Tex prev = CACHE.putIfAbsent(name, t);
            if (prev != null) {
                t = prev;
            }
        }
        return t;
    }

    /** A appeler quand les textures sont rechargees. */
    public static void clearCache() {
        CACHE.clear();
        SPRITES.clear();
        generation++;
    }

    /** 0.20.46 (audit) : compteur des rechargements de textures (pour invalider une geometrie enregistree). */
    public static int generation() {
        return generation;
    }

    /** Nombre de boites dessinees (gonflement anti-scintillement). */
    private int boxes;
    /** Gonflement de la boite en cours (px), applique aux sommets du bord des faces. */
    private float grow;

    /** Decalage de texture le long des faces (corde ou courroie qui defile), en pixels. */
    public float scroll;

    /** Texture d'un sprite quelconque de l'atlas des blocs (sediment, buche...) ; 0.20.46 (audit) : en cache par sprite. */
    public static Tex of(TextureAtlasSprite s) {
        Tex t = SPRITES.get(s);
        if (t == null) {
            t = new Tex(s.contents().name().toString(), s.getU0(), s.getU1(), s.getV0(), s.getV1());
            SPRITES.put(s, t);
        }
        return t;
    }

    // ------------------------------------------------------------------
    // 1.0.4-beta (lot 27, audit de fluidite) : parties rigides en cache
    // ------------------------------------------------------------------

    /** Partie rigide : cle de l'appelant, rang du gonflement anti-scintillement, teinte et defilement courants. */
    private record RigidKey(Object key, int boxes, float red, float green, float blue, float alpha, float scroll) {
    }

    /** Geometrie enregistree et nombre de boites qu'elle compte (le gonflement des boites suivantes continue comme sans cache). */
    private record Rigid(KRecord record, int boxes) {
    }

    private static final Map<RigidKey, Rigid> RIGID = new HashMap<>();
    private static int rigidGeneration = -1;
    /** Banc d'essai (fluidite) : faux = dessin direct (comparaison des sommets avec et sans le cache). */
    public static boolean rigidCache = true;
    /** Plafond des parties gardees (une par cle, rang de gonflement et teinte : quelques centaines en jeu). */
    private static final int RIGID_MAX = 4096;

    /**
     * 1.0.4-beta (lot 27, audit de fluidite : le dessin des machines mecaniques prenait les trois quarts du temps du rendu des blocs-entites,
     * a refaire a chaque image) : dessine {@code body}, une partie RIGIDE dont la geometrie locale ne depend que de {@code key} (dimensions,
     * materiaux ; jamais l'angle, le temps ni l'etat : ceux-la passent par la pose courante, avant l'appel). Enregistree une fois dans le repere
     * courant (pose identite), elle est ensuite rejouee avec la pose courante ({@link KRecord#replay}) : memes sommets (au plus un millionieme
     * de bloc d'arrondi la ou la partie tourne ses propres sous-reperes : verifie au banc tools/fluidity), sans le calcul des boites, des faces et
     * des decoupes. Le rang du gonflement
     * anti-scintillement ({@code boxes % 11}), la teinte et le defilement font partie de la cle. Hors du fil de rendu, ou pendant un
     * enregistrement (icones) : dessin direct, comme avant.
     */
    public KDraw rigid(Object key, Consumer<KDraw> body) {
        if (!rigidCache || !shared || vc instanceof KRecord) {
            body.accept(this);
            return this;
        }
        if (rigidGeneration != generation) {
            RIGID.clear();
            rigidGeneration = generation;
        }
        RigidKey k = new RigidKey(key, boxes % 11, red, green, blue, alpha, scroll);
        Rigid r = RIGID.get(k);
        if (r == null) {
            KRecord rec = new KRecord();
            KDraw sub = new KDraw(new PoseStack(), rec, KRecord.LIGHT, KRecord.OVERLAY);
            sub.boxes = boxes;
            sub.red = red;
            sub.green = green;
            sub.blue = blue;
            sub.alpha = alpha;
            sub.scroll = scroll;
            body.accept(sub);
            r = new Rigid(rec, sub.boxes - boxes);
            if (RIGID.size() < RIGID_MAX) {
                RIGID.put(k, r);
            }
        }
        r.record().replay(pose, vc, light, overlay);
        boxes += r.boxes();
        return this;
    }

    /** Banc d'essai (fluidite) : vide le cache des parties rigides. */
    public static void clearRigid() {
        RIGID.clear();
    }

    public KDraw tint(int argb) {
        //Meme conversion que l'ancien color(r, g, b, a) : (int) (c / 255F * 255F) == c pour 0..255 (verifie)
        red = (argb >> 16 & 255) / 255F;
        green = (argb >> 8 & 255) / 255F;
        blue = (argb & 255) / 255F;
        alpha = (argb >>> 24) / 255F;
        return this;
    }

    public KDraw push() {
        if (!shared) {
            pose.pushPose();
            return this;
        }
        //0.20.46 (audit) : sauvegarde de la pose courante (meme valeurs que pushPose, sans allouer de nouvelle pose)
        PoseStack.Pose top = pose.last();
        if (depth == savedPose.length) {
            int n = Math.max(8, depth * 2);
            Matrix4f[] p = Arrays.copyOf(savedPose, n);
            Matrix3f[] q = Arrays.copyOf(savedNormal, n);
            for (int i = depth; i < n; i++) {
                p[i] = new Matrix4f();
                q[i] = new Matrix3f();
            }
            savedPose = p;
            savedNormal = q;
        }
        savedPose[depth].set(top.pose());
        savedNormal[depth].set(top.normal());
        depth++;
        return this;
    }

    public KDraw pop() {
        if (!shared) {
            pose.popPose();
            return this;
        }
        depth--;
        PoseStack.Pose top = pose.last();
        top.pose().set(savedPose[depth]);
        top.normal().set(savedNormal[depth]);
        return this;
    }

    /** Rotation de {@code degrees} autour de l'axe donne passant par le point (pixels). */
    public KDraw rotate(Direction.Axis axis, float degrees, float cx, float cy, float cz) {
        pose.translate(cx / 16F, cy / 16F, cz / 16F);
        //Meme quaternion que Axis.XP/YP/ZP.rotationDegrees (degres x (float) pi / 180)
        float rad = degrees * ((float) Math.PI / 180F);
        Quaternionf q = shared ? ROT : new Quaternionf();
        if (axis == Direction.Axis.X) {
            q.rotationX(rad);
        } else if (axis == Direction.Axis.Y) {
            q.rotationY(rad);
        } else {
            q.rotationZ(rad);
        }
        pose.mulPose(q);
        pose.translate(-cx / 16F, -cy / 16F, -cz / 16F);
        return this;
    }

    public KDraw translate(float x, float y, float z) {
        pose.translate(x / 16F, y / 16F, z / 16F);
        return this;
    }

    /** Oriente le repere local pour que son axe Y suive {@code axis} (les pieces sont dessinees verticales). */
    public KDraw align(Direction.Axis axis) {
        if (axis == Direction.Axis.X) {
            rotate(Direction.Axis.Z, -90, 8, 8, 8);
        } else if (axis == Direction.Axis.Z) {
            rotate(Direction.Axis.X, 90, 8, 8, 8);
        }
        return this;
    }

    /** Oriente le repere local pour que son axe +Z (avant) regarde vers {@code facing} (horizontal). */
    public KDraw face(Direction facing) {
        float deg = switch (facing) {
            case SOUTH -> 0;
            case WEST -> 90;
            case NORTH -> 180;
            case EAST -> 270;
            default -> 0;
        };
        rotate(Direction.Axis.Y, -deg, 8, 8, 8);
        if (facing == Direction.UP) {
            rotate(Direction.Axis.X, -90, 8, 8, 8);
        } else if (facing == Direction.DOWN) {
            rotate(Direction.Axis.X, 90, 8, 8, 8);
        }
        return this;
    }

    public KDraw box(float x0, float y0, float z0, float x1, float y1, float z1, String side) {
        Tex s = sprite(side);
        return box(x0, y0, z0, x1, y1, z1, s, s);
    }

    public KDraw box(float x0, float y0, float z0, float x1, float y1, float z1, String side, String ends) {
        Tex s = sprite(side);
        return box(x0, y0, z0, x1, y1, z1, s, ends.equals(side) ? s : sprite(ends));
    }

    // Plans des faces (0.20.46 (audit) : entiers au lieu de caracteres)
    private static final int PY = 0, PZ = 1, PX = 2;

    /** Boite (pixels) : {@code side} sur les quatre flancs, {@code ends} dessus et dessous. */
    public KDraw box(float x0, float y0, float z0, float x1, float y1, float z1, Tex side, Tex ends) {
        //Chaque boite est tres legerement gonflee, d'une valeur differente de la precedente : deux faces de boites voisines ne sont jamais
        //exactement dans le meme plan (sinon elles se battent a l'ecran, « pixels superposes »)
        //(le decoupage en carreaux et les UV restent ceux de la boite exacte : seuls les sommets bougent)
        float e = 0.015F * (1 + boxes++ % 11);
        grow = e;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        //Dessus / dessous (y)
        face(m, n, ends, 0, 1, 0, x0, y1 + e, z0, x1, y1 + e, z1, PY, true);
        face(m, n, ends, 0, -1, 0, x0, y0 - e, z0, x1, y0 - e, z1, PY, false);
        //Nord / sud (z)
        face(m, n, side, 0, 0, -1, x0, y0, z0 - e, x1, y1, z0 - e, PZ, false);
        face(m, n, side, 0, 0, 1, x0, y0, z1 + e, x1, y1, z1 + e, PZ, true);
        //Ouest / est (x)
        face(m, n, side, -1, 0, 0, x0 - e, y0, z0, x0 - e, y1, z1, PX, false);
        face(m, n, side, 1, 0, 0, x1 + e, y0, z0, x1 + e, y1, z1, PX, true);
        grow = 0;
        return this;
    }

    /** Face plane ; decoupee en carreaux de 16 px au plus, UV = coordonnees (comme les modeles vanilla). */
    private void face(Matrix4f m, Matrix3f n, Tex s, float nx, float ny, float nz, float x0, float y0, float z0, float x1, float y1,
          float z1, int plane, boolean positive) {
        //Axes du plan : (a, b)
        float a0, a1, b0, b1;
        switch (plane) {
            case PY -> {
                a0 = x0; a1 = x1; b0 = z0; b1 = z1;
            }
            case PZ -> {
                a0 = x0; a1 = x1; b0 = y0; b1 = y1;
            }
            default -> {
                a0 = z0; a1 = z1; b0 = y0; b1 = y1;
            }
        }
        if (!(a0 < a1 - 1e-4F) || !(b0 < b1 - 1e-4F)) {
            return;                                              //Face vide : aucun carreau (comme les boucles ci-dessous)
        }
        //Normale transformee une fois par face (meme calcul que VertexConsumer.normal(Matrix3f, ...))
        Vector3f nv = shared ? NRM : new Vector3f();
        n.transform(nv.set(nx, ny, nz));
        fnx = nv.x();
        fny = nv.y();
        fnz = nv.z();
        float depthPx = plane == PY ? y0 : plane == PZ ? z0 : x0;
        for (float a = a0; a < a1 - 1e-4F; ) {
            float aEnd = Math.min(a1, (float) Math.floor(a / 16F + 1e-4F) * 16F + 16F);
            for (float b = b0; b < b1 - 1e-4F; ) {
                float bEnd = Math.min(b1, (float) Math.floor((b + scroll) / 16F + 1e-4F) * 16F + 16F - scroll);   //1.2.3 : coupe aux bords du motif DEFILE (corde fluide)
                quad(m, s, plane, positive, depthPx, a, aEnd, b, bEnd,
                      a == a0 ? a - grow : a, aEnd == a1 ? aEnd + grow : aEnd, b == b0 ? b - grow : b, bEnd == b1 ? bEnd + grow : bEnd);
                b = bEnd;
            }
            a = aEnd;
        }
    }

    /**
     * Un carreau. 0.20.46 (audit) : sans tableau ; coins k = 0..3 : (pa0, pb0), (pa1, pb0), (pa1, pb1), (pa0, pb1), UV (u0, vA), (u1, vA),
     * (u1, vB), (u0, vB), parcourus a l'envers selon le sens de la normale (faces vues de l'exterieur) : memes sommets qu'avant.
     */
    private void quad(Matrix4f m, Tex s, int plane, boolean positive, float depthPx, float a0, float a1, float b0, float b1, float pa0,
          float pa1, float pb0, float pb1) {
        float ua0 = mod16(a0), ua1 = ua0 + (a1 - a0), vb0 = mod16(b0 + scroll), vb1 = vb0 + (b1 - b0);
        if (vb1 > 16.001F) {
            vb0 -= vb1 - 16;
            vb1 = 16;
        }
        float u0 = s.u(ua0), u1 = s.u(ua1), vA, vB;
        if (plane == PY) {
            vA = s.v(vb0);
            vB = s.v(vb1);
        } else {
            vA = s.v(16 - vb0);
            vB = s.v(16 - vb1);
        }
        //Ordre des sommets selon le sens de la normale (faces vues de l'exterieur)
        boolean flip = plane == PZ ? !positive : positive;
        if (flip) {
            vertex(m, plane, depthPx, pa0, pb1, u0, vB);
            vertex(m, plane, depthPx, pa1, pb1, u1, vB);
            vertex(m, plane, depthPx, pa1, pb0, u1, vA);
            vertex(m, plane, depthPx, pa0, pb0, u0, vA);
        } else {
            vertex(m, plane, depthPx, pa0, pb0, u0, vA);
            vertex(m, plane, depthPx, pa1, pb0, u1, vA);
            vertex(m, plane, depthPx, pa1, pb1, u1, vB);
            vertex(m, plane, depthPx, pa0, pb1, u0, vB);
        }
    }

    private void vertex(Matrix4f m, int plane, float depthPx, float a, float b, float u, float v) {
        float x, y, z;
        switch (plane) {
            case PY -> {
                x = a; y = depthPx; z = b;
            }
            case PZ -> {
                x = a; y = b; z = depthPx;
            }
            default -> {
                x = depthPx; y = b; z = a;
            }
        }
        //Meme calcul que VertexConsumer.vertex(Matrix4f, x, y, z) ; un seul appel pour tout le sommet (chemin rapide du BufferBuilder)
        Vector4f p = shared ? POS : new Vector4f();
        m.transform(p.set(x / 16F, y / 16F, z / 16F, 1.0F));
        vc.vertex(p.x(), p.y(), p.z(), red, green, blue, alpha, u, v, overlay, light, fnx, fny, fnz);
    }

    private static float mod16(float v) {
        float r = v % 16F;
        return r < 0 ? r + 16F : r;
    }

    /** Prisme octogonal d'axe Y (deux boites, la seconde tournee de 45 degres). */
    public KDraw octa(float cx, float cz, float r, float y0, float y1, String side, String ends) {
        Tex s = sprite(side), e = ends.equals(side) ? s : sprite(ends);
        box(cx - r, y0, cz - r, cx + r, y1, cz + r, s, e);
        push().rotate(Direction.Axis.Y, 45, cx, 8, cz);
        box(cx - r, y0, cz - r, cx + r, y1, cz + r, s, e);
        pop();
        return this;
    }

    /** Segment epais entre deux points (pixels) : corde, bielle, bras. Section carree {@code w}. */
    public KDraw beam(float ax, float ay, float az, float bx, float by, float bz, float w, String tex) {
        float dx = bx - ax, dy = by - ay, dz = bz - az;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1e-3F) {
            return this;
        }
        push();
        pose.translate(ax / 16F, ay / 16F, az / 16F);
        //Aligne l'axe Y local sur (dx, dy, dz) (memes quaternions que Axis.YP / XP.rotation)
        float yaw = (float) Math.atan2(dx, dz);
        float pitch = (float) Math.acos(Math.max(-1, Math.min(1, dy / len)));
        Quaternionf q = shared ? ROT : new Quaternionf();
        pose.mulPose(q.rotationY(yaw));
        pose.mulPose(q.rotationX(pitch));
        Tex t = sprite(tex);
        box(-w / 2, 0, -w / 2, w / 2, len, w / 2, t, t);
        pop();
        return this;
    }
}
