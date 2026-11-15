package fr.pioneers.client.industry.big;

import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 0.19.14 : maillage d'une machine interieure, en px (16 par bloc) dans le repere canonique de la machine (x en travers, y vers le haut, z
 * le long de l'axe ; origine = coin bas de la boite des modules). Boites et cylindres a facettes, memes faces et memes UV que le banc
 * d'apercus Python ({@code T:industry/big_preview.py} lit ces quads tels quels).
 * <p>
 * Chaque quad appartient a un groupe : 0 = fixe ; les autres tournent autour d'un axe ({@link Group}) d'un angle = phase x facteur.
 * Couleur (teinte ARGB) et lueur (lumiere de bloc minimale 0..15) par quad.
 */
public final class BigMesh {

    /** Groupe tournant : axe ('x', 'y', 'z') passant par (px, py, pz), degres par unite de phase. */
    public record Group(char axis, float px, float py, float pz, float factor) {
    }

    /** Quad : 4 sommets (x, y, z), UV en fractions (u, v) x 4, normale, materiau, groupe, teinte, lueur. */
    public static final class Quad {

        public final float[] pos = new float[12];
        public final float[] uv = new float[8];
        public float nx, ny, nz;
        public BigMat mat;
        public int group, color = 0xFFFFFFFF, glow;
    }

    /** Groupe 0 (fixe). */
    private static final Group FIXED = new Group('y', 0, 0, 0, 0);

    public final List<Quad> quads = new ArrayList<>();
    public final List<Group> groups = new ArrayList<>();
    private int group, color = 0xFFFFFFFF, glow;
    /** 0.20.46 (audit) : quads deja alloues, resservis apres {@link #reset()} (null tant que le maillage n'a jamais ete vide). */
    @Nullable
    private List<Quad> pool;
    private int pooled;
    /** 0.19.18 : decalage en x de tout ce qui suit (voies paralleles des chaines). */
    private float ox;
    /** 0.20.0 : UV a l'echelle (1 px de modele = 1/16 de texture) au lieu d'une texture entiere par face. */
    private boolean metric;

    public BigMesh() {
        groups.add(FIXED);
    }

    /**
     * 0.20.46 (audit) : vide le maillage pour le refaire (partie dynamique redessinee a chaque image) ; les quads deja alloues resservent,
     * sans allocation une fois le maillage a sa taille. Ne jamais garder de reference a un quad d'un maillage qu'on vide.
     */
    public BigMesh reset() {
        if (pool == null) {
            pool = new ArrayList<>(quads);
        }
        quads.clear();
        groups.clear();
        groups.add(FIXED);
        pooled = 0;
        group = 0;
        color = 0xFFFFFFFF;
        glow = 0;
        ox = 0;
        metric = false;
        return this;
    }

    /**
     * 1.9.2 : copie un quad d'un autre maillage (vrai vehicule des Ateliers ferroviaires), deplace par {@code xf} (rotation et translation
     * seulement), dans le groupe courant ; quad repris de la reserve (partie dynamique sans allocation).
     */
    public void copy(Quad src, Matrix4f xf) {
        Quad q = newQuad();
        Vector3f v = new Vector3f();
        for (int i = 0; i < 4; i++) {
            v.set(src.pos[i * 3], src.pos[i * 3 + 1], src.pos[i * 3 + 2]);
            xf.transformPosition(v);
            q.pos[i * 3] = v.x + ox;
            q.pos[i * 3 + 1] = v.y;
            q.pos[i * 3 + 2] = v.z;
        }
        System.arraycopy(src.uv, 0, q.uv, 0, 8);
        v.set(src.nx, src.ny, src.nz);
        xf.transformDirection(v);
        q.nx = v.x;
        q.ny = v.y;
        q.nz = v.z;
        q.mat = src.mat;
        q.group = group;
        q.color = src.color;
        q.glow = Math.max(glow, src.glow);
        quads.add(q);
    }

    /** Nouveau quad (repris de la reserve apres un {@link #reset()}). */
    private Quad newQuad() {
        if (pool == null) {
            return new Quad();
        }
        if (pooled < pool.size()) {
            return pool.get(pooled++);
        }
        Quad q = new Quad();
        pool.add(q);
        pooled++;
        return q;
    }

    /** Nouveau groupe tournant ; les boites suivantes lui appartiennent (jusqu'a {@link #fixed()}). */
    public int spin(char axis, float px, float py, float pz, float factor) {
        groups.add(new Group(axis, px + ox, py, pz, factor));
        group = groups.size() - 1;
        return group;
    }

    /** Decale en x tout ce qui sera ajoute ensuite (0 pour revenir au repere de la machine). */
    public BigMesh shift(float x) {
        ox = x;
        return this;
    }

    public BigMesh fixed() {
        group = 0;
        return this;
    }

    public BigMesh group(int g) {
        group = g;
        return this;
    }

    public BigMesh color(int argb) {
        color = argb;
        return this;
    }

    public BigMesh glow(int level) {
        glow = level;
        return this;
    }

    /** UV a l'echelle des faces (textures materiaux : meme densite partout). */
    public BigMesh metric(boolean on) {
        metric = on;
        return this;
    }

    // ------------------------------------------------------------------ transformations (memes conventions que rot_axis du Python)

    /** Rotation de {@code deg} degres autour de l'axe passant par (ox, oy, oz) (main droite). */
    public static Matrix4f rot(char axis, float ox, float oy, float oz, float deg) {
        float a = (float) Math.toRadians(deg);
        Matrix4f m = new Matrix4f().translate(ox, oy, oz);
        switch (axis) {
            case 'x' -> m.rotateX(a);
            case 'y' -> m.rotateY(a);
            default -> m.rotateZ(a);
        }
        return m.translate(-ox, -oy, -oz);
    }

    /** Applique {@code first} puis {@code then} (chaine du Python). */
    public static Matrix4f chain(@Nullable Matrix4f first, @Nullable Matrix4f then) {
        if (first == null) {
            return then;
        }
        if (then == null) {
            return first;
        }
        return new Matrix4f(then).mul(first);
    }

    // ------------------------------------------------------------------ primitives

    /** Boite de (x0, y0, z0) a (x1, y1, z1), transformee par xf ; chaque face montre un carre uv x uv (px de texture sur 16). */
    public void box(float x0, float y0, float z0, float x1, float y1, float z1, BigMat mat, @Nullable Matrix4f xf, float uv) {
        float[][] corners = {
              //up
              {x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0},
              //down
              {x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, 0, -1, 0},
              //north
              {x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, 0, 0, -1},
              //south
              {x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, 0, 0, 1},
              //west
              {x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, -1, 0, 0},
              //east
              {x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, 1, 0, 0}};
        float f = uv / 16F;
        Vector3f v = new Vector3f();
        for (float[] c : corners) {
            Quad q = newQuad();
            for (int i = 0; i < 4; i++) {
                v.set(c[i * 3], c[i * 3 + 1], c[i * 3 + 2]);
                if (xf != null) {
                    xf.transformPosition(v);
                }
                q.pos[i * 3] = v.x + ox;
                q.pos[i * 3 + 1] = v.y;
                q.pos[i * 3 + 2] = v.z;
            }
            //UV : (0,0) (0,uv) (uv,uv) (uv,0)
            float fu = f, fv = f;
            if (metric) {
                fu = dist(q.pos, 0, 3) / 16F;
                fv = dist(q.pos, 0, 1) / 16F;
            }
            q.uv[0] = 0;
            q.uv[1] = 0;
            q.uv[2] = 0;
            q.uv[3] = fv;
            q.uv[4] = fu;
            q.uv[5] = fv;
            q.uv[6] = fu;
            q.uv[7] = 0;
            v.set(c[12], c[13], c[14]);
            if (xf != null) {
                xf.transformDirection(v);
                v.normalize();
            }
            q.nx = v.x;
            q.ny = v.y;
            q.nz = v.z;
            q.mat = mat;
            q.group = group;
            q.color = color;
            q.glow = glow;
            quads.add(q);
        }
    }

    private float dist(float[] p, int i, int j) {
        float dx = p[i * 3] - p[j * 3], dy = p[i * 3 + 1] - p[j * 3 + 1], dz = p[i * 3 + 2] - p[j * 3 + 2];
        return (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    public void cyl(char axis, float cx, float cy, float r, float a0, float a1, BigMat mat, int sides, @Nullable Matrix4f xf, float uv) {
        cyl(axis, cx, cy, r, a0, a1, mat, sides, xf, uv, true, 0.8F);
    }

    /**
     * Cylindre a facettes : {@code sides} boites tangentes d'epaisseur {@code thick}, plus un noyau plein (fill). Axe 'z' : centre (cx, cy)
     * en (x, y) ; 'x' : centre (cx, cy) en (y, z) ; 'y' : centre (cx, cy) en (x, z) ; de a0 a a1 le long de l'axe.
     */
    public void cyl(char axis, float cx, float cy, float r, float a0, float a1, BigMat mat, int sides, @Nullable Matrix4f xf, float uv, boolean fill,
          float thick) {
        if (mat.translucent()) {
            //0.20.31 (Guillaume : « la partie bleue de la tour de refroidissement glitch ») : en translucide, les boites tangentes et le noyau se
            //recouvraient (faces confondues qui scintillaient) : une seule peau, et deux couvercles si le cylindre est plein
            shell(axis, cx, cy, r, a0, a1, mat, sides, xf, uv, fill);
            return;
        }
        float w = (float) (2 * r * Math.tan(Math.PI / sides) + 0.05);
        for (int i = 0; i < sides; i++) {
            float ang = i * 360F / sides;
            Matrix4f g;
            switch (axis) {
                case 'z' -> {
                    g = rot('z', cx, cy, 0, ang);
                    box(cx - w / 2, cy + r - thick, a0, cx + w / 2, cy + r, a1, mat, chain(g, xf), uv);
                }
                case 'y' -> {
                    g = rot('y', cx, 0, cy, ang);
                    box(cx - w / 2, a0, cy + r - thick, cx + w / 2, a1, cy + r, mat, chain(g, xf), uv);
                }
                default -> {
                    g = rot('x', 0, cx, cy, ang);
                    box(a0, cx + r - thick, cy - w / 2, a1, cx + r, cy + w / 2, mat, chain(g, xf), uv);
                }
            }
        }
        if (!fill) {
            return;
        }
        float k = r * 0.72F;
        switch (axis) {
            case 'z' -> box(cx - k, cy - k, a0, cx + k, cy + k, a1, mat, xf, uv);
            case 'y' -> box(cx - k, a0, cy - k, cx + k, a1, cy + k, mat, xf, uv);
            default -> box(a0, cx - k, cy - k, a1, cx + k, cy + k, mat, xf, uv);
        }
    }

    /** Point du cylindre : angle {@code ang} (rad), rayon {@code r}, position {@code a} le long de l'axe. */
    private static float[] around(char axis, float cx, float cy, float r, double ang, float a) {
        float u = cx + r * (float) Math.cos(ang), v = cy + r * (float) Math.sin(ang);
        return switch (axis) {
            case 'z' -> new float[]{u, v, a};
            case 'y' -> new float[]{u, a, v};
            default -> new float[]{a, u, v};
        };
    }

    /** 0.20.31 : cylindre translucide sans recouvrement : une face par facette, couvercles en eventail (fill). */
    private void shell(char axis, float cx, float cy, float r, float a0, float a1, BigMat mat, int sides, @Nullable Matrix4f xf, float uv, boolean fill) {
        float rr = (float) (r / Math.cos(Math.PI / sides));
        for (int i = 0; i < sides; i++) {
            double t0 = 2 * Math.PI * i / sides, t1 = 2 * Math.PI * (i + 1) / sides, tm = (t0 + t1) / 2;
            float[] n = around(axis, 0, 0, 1, tm, 0);
            face(mat, xf, uv, n, around(axis, cx, cy, rr, t0, a1), around(axis, cx, cy, rr, t0, a0), around(axis, cx, cy, rr, t1, a0),
                 around(axis, cx, cy, rr, t1, a1));
            if (fill) {
                float[] c0 = around(axis, cx, cy, 0, 0, a0), c1 = around(axis, cx, cy, 0, 0, a1);
                float[] down = axisVector(axis, -1), up = axisVector(axis, 1);
                face(mat, xf, uv, down, c0, around(axis, cx, cy, rr, t1, a0), around(axis, cx, cy, rr, t0, a0), around(axis, cx, cy, rr, t0, a0));
                face(mat, xf, uv, up, c1, around(axis, cx, cy, rr, t0, a1), around(axis, cx, cy, rr, t1, a1), around(axis, cx, cy, rr, t1, a1));
            }
        }
    }

    private static float[] axisVector(char axis, float sign) {
        return switch (axis) {
            case 'z' -> new float[]{0, 0, sign};
            case 'y' -> new float[]{0, sign, 0};
            default -> new float[]{sign, 0, 0};
        };
    }

    /** Une face (quatre coins, le dernier peut repeter le troisieme : triangle), memes UV que {@link #box}. */
    private void face(BigMat mat, @Nullable Matrix4f xf, float uv, float[] normal, float[]... pts) {
        Quad q = newQuad();
        Vector3f v = new Vector3f();
        for (int i = 0; i < 4; i++) {
            v.set(pts[i][0], pts[i][1], pts[i][2]);
            if (xf != null) {
                xf.transformPosition(v);
            }
            q.pos[i * 3] = v.x + ox;
            q.pos[i * 3 + 1] = v.y;
            q.pos[i * 3 + 2] = v.z;
        }
        float fu = uv / 16F, fv = uv / 16F;
        if (metric) {
            fu = dist(q.pos, 0, 3) / 16F;
            fv = dist(q.pos, 0, 1) / 16F;
        }
        //(0,0) (0,uv) (uv,uv) (uv,0) ; les zeros sont ecrits : un quad repris de la reserve garde ses anciennes valeurs
        q.uv[0] = 0;
        q.uv[1] = 0;
        q.uv[2] = 0;
        q.uv[3] = fv;
        q.uv[4] = fu;
        q.uv[5] = fv;
        q.uv[6] = fu;
        q.uv[7] = 0;
        v.set(normal[0], normal[1], normal[2]);
        if (xf != null) {
            xf.transformDirection(v);
            v.normalize();
        }
        q.nx = v.x;
        q.ny = v.y;
        q.nz = v.z;
        q.mat = mat;
        q.group = group;
        q.color = color;
        q.glow = glow;
        quads.add(q);
    }
}
