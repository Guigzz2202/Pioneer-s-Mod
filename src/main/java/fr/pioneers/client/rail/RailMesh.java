package fr.pioneers.client.rail;

import fr.pioneers.client.industry.big.BigMat;
import fr.pioneers.client.industry.big.BigMesh;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 0.20.0 : construction des maillages du chemin de fer (voies, vehicules, signaux, postes) dans le style des apercus valides : boites et
 * cylindres a facettes en px, materiaux {@link BigMat} {@code R_*} (lueur propre aux lampes et fenetres de ressource), et une pile de
 * transformations (deplacer, tourner) qui imite {@code move(rotq(...))} des apercus Python.
 */
public final class RailMesh {

    public final BigMesh mesh = new BigMesh().metric(true);
    /** Banc hors jeu (tools/railtest/ZBench) : enregistre le volume de chaque boite pour savoir ce qui est cache. */
    public static boolean RECORD = false;
    /** Volumes enregistres : 16 valeurs de la transformation inverse puis les coins min et max (px). */
    public final List<float[]> volumes = new ArrayList<>();
    private final Deque<Matrix4f> stack = new ArrayDeque<>();
    private Matrix4f current;
    private int group;

    public RailMesh() {
        current = null;
    }

    public RailMesh translate(float x, float y, float z) {
        return push(new Matrix4f().translate(x, y, z));
    }

    /** Rotation (degres, main droite) autour d'un axe passant par (ox, oy, oz). */
    public RailMesh rotate(char axis, float ox, float oy, float oz, float deg) {
        return push(BigMesh.rot(axis, ox, oy, oz, deg));
    }

    /** Ajoute une transformation (appliquee avant celles deja empilees). */
    public RailMesh push(Matrix4f m) {
        stack.push(current == null ? new Matrix4f() : new Matrix4f(current));
        current = current == null ? new Matrix4f(m) : new Matrix4f(current).mul(m);
        return this;
    }

    public RailMesh pop() {
        Matrix4f prev = stack.pop();
        current = stack.isEmpty() && prev.equals(new Matrix4f()) ? null : prev;
        return this;
    }

    /** Groupe tournant (roues) ; {@link #fixed()} pour revenir au fixe. */
    public RailMesh spin(char axis, float px, float py, float pz, float factor) {
        float[] p = {px, py, pz};
        if (current != null) {
            org.joml.Vector3f v = new org.joml.Vector3f(px, py, pz);
            current.transformPosition(v);
            p[0] = v.x;
            p[1] = v.y;
            p[2] = v.z;
        }
        group = mesh.spin(axis, p[0], p[1], p[2], factor);
        return this;
    }

    public RailMesh fixed() {
        group = 0;
        mesh.fixed();
        return this;
    }

    public RailMesh box(float x0, float y0, float z0, float x1, float y1, float z1, BigMat mat) {
        record(x0, y0, z0, x1, y1, z1);
        mesh.group(group).glow(mat.glow()).color(0xFFFFFFFF);
        mesh.box(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), mat, current, 16);
        return this;
    }

    private void record(float x0, float y0, float z0, float x1, float y1, float z1) {
        if (!RECORD) {
            return;
        }
        float[] v = new float[22];
        (current == null ? new Matrix4f() : new Matrix4f(current).invert()).get(v);
        v[16] = Math.min(x0, x1);
        v[17] = Math.min(y0, y1);
        v[18] = Math.min(z0, z1);
        v[19] = Math.max(x0, x1);
        v[20] = Math.max(y0, y1);
        v[21] = Math.max(z0, z1);
        volumes.add(v);
    }

    /** Boite teintee (tas de vrac de la tremie). */
    public RailMesh tinted(float x0, float y0, float z0, float x1, float y1, float z1, BigMat mat, int argb) {
        record(x0, y0, z0, x1, y1, z1);
        mesh.group(group).glow(mat.glow()).color(argb);
        mesh.box(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), mat, current, 16);
        mesh.color(0xFFFFFFFF);
        return this;
    }

    public RailMesh cyl(char axis, float cx, float cy, float r, float a0, float a1, BigMat mat, int sides) {
        return cyl(axis, cx, cy, r, a0, a1, mat, sides, true, 0.8F);
    }

    public RailMesh cyl(char axis, float cx, float cy, float r, float a0, float a1, BigMat mat, int sides, boolean fill, float thick) {
        //0.20.8 : volume du cylindre (boite englobante) pour les bancs (ZBench, formes de collision)
        switch (axis) {
            case 'y' -> record(cx - r, a0, cy - r, cx + r, a1, cy + r);
            case 'z' -> record(cx - r, cy - r, a0, cx + r, cy + r, a1);
            default -> record(a0, cx - r, cy - r, a1, cx + r, cy + r);
        }
        mesh.group(group).glow(mat.glow()).color(0xFFFFFFFF);
        mesh.cyl(axis, cx, cy, r, Math.min(a0, a1), Math.max(a0, a1), mat, sides, current, 16, fill, thick);
        return this;
    }

    /**
     * Barre entre deux points (section carree {@code w} px), dans le plan vertical d'un axe : utile pour les bielles, les bras, les
     * pantographes. Les points sont en (y, z) a x fixe ({@code xa} a {@code xb} : epaisseur en x).
     */
    public RailMesh rodYZ(float xa, float xb, float ya, float za, float yb, float zb, float w, BigMat mat) {
        float len = (float) Math.hypot(zb - za, yb - ya);
        float ang = (float) Math.toDegrees(Math.atan2(yb - ya, zb - za));
        translate(0, ya, za);
        rotate('x', 0, 0, 0, -ang);
        box(xa, -w / 2, 0, xb, w / 2, len, mat);
        pop();
        pop();
        return this;
    }

    // ------------------------------------------------------------------ pixels superposes (0.20.4)

    /** Ecart (px) dont avance une face posee apres une autre, dans le meme plan et tournee du meme cote, qu'elle recouvre. */
    public static final float NUDGE = 0.017F;

    /**
     * Supprime les « pixels superposes » d'un maillage : deux faces dans le meme plan, tournees du meme cote et qui se
     * recouvrent scintillent en jeu ; la face posee apres avance de {@link #NUDGE} px (par niveau d'empilement), comme
     * {@code tools/zfight.py} pour les modeles JSON. Faces regroupees par plan : cout quasi lineaire.
     */
    public static BigMesh deconflict(BigMesh mesh) {
        //Une face avancee peut tomber dans le plan d'une autre : on recommence tant qu'il en reste (quelques passes au plus)
        for (int pass = 0; pass < 24 && deconflictPass(mesh); pass++) {
        }
        return mesh;
    }

    private static boolean deconflictPass(BigMesh mesh) {
        List<BigMesh.Quad> qs = mesh.quads;
        int n = qs.size();
        float[][] nrm = new float[n][];
        float[] plane = new float[n];
        int[] level = new int[n];
        Map<Long, List<Integer>> buckets = new HashMap<>();
        boolean any = false;
        for (int i = 0; i < n; i++) {
            BigMesh.Quad q = qs.get(i);
            float l = (float) Math.sqrt(q.nx * q.nx + q.ny * q.ny + q.nz * q.nz);
            if (l < 1e-6) {
                continue;
            }
            float[] nn = {q.nx / l, q.ny / l, q.nz / l};
            nrm[i] = nn;
            plane[i] = nn[0] * q.pos[0] + nn[1] * q.pos[1] + nn[2] * q.pos[2];
            //Tous groupes confondus : une face perpendiculaire a l'axe d'une roue reste dans son plan en tournant
            long dir = (Math.round(nn[0] * 100) + 128) | (Math.round(nn[1] * 100) + 128L) << 8 | (Math.round(nn[2] * 100) + 128L) << 16;
            long slot = Math.round(plane[i] / 0.01);
            int lvl = 0;
            for (long k = slot - 1; k <= slot + 1; k++) {
                List<Integer> list = buckets.get(dir ^ k * 0x9E3779B97F4A7C15L);
                if (list == null) {
                    continue;
                }
                for (int j : list) {
                    float dot = nn[0] * nrm[j][0] + nn[1] * nrm[j][1] + nn[2] * nrm[j][2];
                    if (dot > 0.9999F && Math.abs(plane[j] - plane[i]) < 0.006F && overlap(q, qs.get(j), nn) > 0.02F) {
                        lvl = Math.max(lvl, level[j] + 1);
                    }
                }
            }
            level[i] = lvl;
            any |= lvl > 0;
            buckets.computeIfAbsent(dir ^ slot * 0x9E3779B97F4A7C15L, k -> new ArrayList<>()).add(i);
        }
        if (any) {
            for (int i = 0; i < n; i++) {
                if (level[i] > 0) {
                    BigMesh.Quad q = qs.get(i);
                    float o = NUDGE * level[i];
                    for (int v = 0; v < 4; v++) {
                        q.pos[v * 3] += nrm[i][0] * o;
                        q.pos[v * 3 + 1] += nrm[i][1] * o;
                        q.pos[v * 3 + 2] += nrm[i][2] * o;
                    }
                }
            }
        }
        return any;
    }

    /** Aire (px2) du recouvrement de deux quads convexes d'un meme plan de normale n. */
    public static float overlap(BigMesh.Quad a, BigMesh.Quad b, float[] n) {
        float[] u = Math.abs(n[0]) < 0.9F ? cross(n, new float[]{1, 0, 0}) : cross(n, new float[]{0, 1, 0});
        float lu = (float) Math.sqrt(u[0] * u[0] + u[1] * u[1] + u[2] * u[2]);
        u[0] /= lu;
        u[1] /= lu;
        u[2] /= lu;
        float[] w = cross(n, u);
        List<float[]> clip = proj(a, u, w), win = proj(b, u, w);
        if (clip.size() < 3 || win.size() < 3) {
            return 0;
        }
        ccw(clip);
        ccw(win);
        for (int i = 0; i < win.size() && !clip.isEmpty(); i++) {
            float[] c = win.get(i), d = win.get((i + 1) % win.size());
            List<float[]> next = new ArrayList<>();
            for (int k = 0; k < clip.size(); k++) {
                float[] p = clip.get(k), q = clip.get((k + 1) % clip.size());
                float sp = side(c, d, p), sq = side(c, d, q);
                boolean pin = sp >= -1e-5, qin = sq >= -1e-5;
                if (pin) {
                    next.add(p);
                }
                if (pin != qin) {
                    float t = sp / (sp - sq);
                    next.add(new float[]{p[0] + (q[0] - p[0]) * t, p[1] + (q[1] - p[1]) * t});
                }
            }
            clip = next;
        }
        return Math.abs(area(clip));
    }

    private static List<float[]> proj(BigMesh.Quad q, float[] u, float[] w) {
        List<float[]> out = new ArrayList<>();
        for (int i = 0; i < 4; i++) {
            float x = q.pos[i * 3], y = q.pos[i * 3 + 1], z = q.pos[i * 3 + 2];
            float[] p = {x * u[0] + y * u[1] + z * u[2], x * w[0] + y * w[1] + z * w[2]};
            if (out.isEmpty() || Math.abs(out.get(out.size() - 1)[0] - p[0]) + Math.abs(out.get(out.size() - 1)[1] - p[1]) > 1e-5) {
                out.add(p);
            }
        }
        if (out.size() > 1 && Math.abs(out.get(0)[0] - out.get(out.size() - 1)[0]) + Math.abs(out.get(0)[1] - out.get(out.size() - 1)[1]) <= 1e-5) {
            out.remove(out.size() - 1);
        }
        return out;
    }

    private static void ccw(List<float[]> poly) {
        if (area(poly) < 0) {
            Collections.reverse(poly);
        }
    }

    private static float area(List<float[]> p) {
        float s = 0;
        for (int i = 0; i < p.size(); i++) {
            float[] a = p.get(i), b = p.get((i + 1) % p.size());
            s += a[0] * b[1] - b[0] * a[1];
        }
        return s / 2;
    }

    private static float side(float[] a, float[] b, float[] p) {
        return (b[0] - a[0]) * (p[1] - a[1]) - (b[1] - a[1]) * (p[0] - a[0]);
    }

    private static float[] cross(float[] a, float[] b) {
        return new float[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    /**
     * Copie les faces de {@code src} dans {@code dst}, deplacees par {@code xf} (groupe 0). 1.9.2 : reprise de l'ancienne
     * TableModels.append (plaques tournantes supprimees) ; sert aux machines interieures qui montrent de vrais vehicules (cale seche,
     * Ateliers ferroviaires) et aux apercus.
     */
    public static void append(BigMesh dst, BigMesh src, Matrix4f xf) {
        Matrix3f n = new Matrix3f();
        xf.normal(n);
        Vector3f v = new Vector3f();
        for (BigMesh.Quad q : src.quads) {
            BigMesh.Quad c = new BigMesh.Quad();
            for (int i = 0; i < 4; i++) {
                xf.transformPosition(q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2], v);
                c.pos[i * 3] = v.x;
                c.pos[i * 3 + 1] = v.y;
                c.pos[i * 3 + 2] = v.z;
            }
            System.arraycopy(q.uv, 0, c.uv, 0, 8);
            n.transform(q.nx, q.ny, q.nz, v);
            c.nx = v.x;
            c.ny = v.y;
            c.nz = v.z;
            c.mat = q.mat;
            c.group = 0;
            c.color = q.color;
            c.glow = q.glow;
            dst.quads.add(c);
        }
    }
}
