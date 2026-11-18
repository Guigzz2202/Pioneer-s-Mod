package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector4f;

/**
 * 1.9.2 (retours de Guillaume) : produit « en cours de montage » : tout ce qui est dessine par ces tampons est coupe a une hauteur. Les
 * sommets sont ramenes dans un repere de reference (matrice donnee : y vers le haut), leur hauteur bornee a la coupe, puis renvoyes ; un
 * quad entierement au-dessus de la coupe n'est pas dessine (sinon des faces aplaties dans le plan de coupe scintilleraient). Le plan de
 * coupe est cache par la plate-forme de travail (hall) ou le plan de balayage (baie). Quads seulement (types de rendu des entites).
 */
public final class ClipBuffers implements MultiBufferSource {

    private final MultiBufferSource delegate;
    private final Matrix4f toRef, fromRef;
    private final float cut;
    private final Map<RenderType, Clip> clips = new HashMap<>();

    /** {@code reference} : matrice de pose du repere de reference ; {@code cut} : hauteur de coupe dans ce repere. */
    public ClipBuffers(MultiBufferSource delegate, Matrix4f reference, float cut) {
        this.delegate = delegate;
        this.fromRef = new Matrix4f(reference);
        this.toRef = new Matrix4f(reference).invert();
        this.cut = cut;
    }

    @NotNull
    @Override
    public VertexConsumer getBuffer(@NotNull RenderType type) {
        Clip clip = clips.get(type);
        VertexConsumer target = delegate.getBuffer(type);
        if (clip == null || clip.target != target) {
            clip = new Clip(target);
            clips.put(type, clip);
        }
        return clip;
    }

    /** Coupe d'un point (x, y, z) du repere de vue : vrai s'il etait au-dessus ; {@code out} recoit le point borne. */
    boolean clamp(float x, float y, float z, float[] out) {
        Vector4f v = toRef.transform(new Vector4f(x, y, z, 1));
        boolean above = v.y > cut;
        if (above) {
            v.y = cut;
        }
        fromRef.transform(v);
        out[0] = v.x;
        out[1] = v.y;
        out[2] = v.z;
        return above;
    }

    private final class Clip implements VertexConsumer {

        final VertexConsumer target;
        private final float[][] pos = new float[4][3];
        private final int[][] ints = new int[4][7];
        private final float[][] floats = new float[4][5];
        private final boolean[] above = new boolean[4];
        private int n;
        //sommet en cours
        private float x, y, z, u, v, nx, ny, nz;
        private int r = 255, g = 255, b = 255, a = 255, overlay, light;

        Clip(VertexConsumer target) {
            this.target = target;
        }

        @NotNull
        @Override
        public VertexConsumer vertex(double vx, double vy, double vz) {
            x = (float) vx;
            y = (float) vy;
            z = (float) vz;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer color(int red, int green, int blue, int alpha) {
            r = red;
            g = green;
            b = blue;
            a = alpha;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer uv(float tu, float tv) {
            u = tu;
            v = tv;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer overlayCoords(int ou, int ov) {
            overlay = ou & 0xFFFF | ov << 16;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer uv2(int lu, int lv) {
            light = lu & 0xFFFF | lv << 16;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer normal(float fx, float fy, float fz) {
            nx = fx;
            ny = fy;
            nz = fz;
            return this;
        }

        @Override
        public void endVertex() {
            above[n] = clamp(x, y, z, pos[n]);
            int[] i = ints[n];
            i[0] = r;
            i[1] = g;
            i[2] = b;
            i[3] = a;
            i[4] = overlay;
            i[5] = light;
            float[] f = floats[n];
            f[0] = u;
            f[1] = v;
            f[2] = nx;
            f[3] = ny;
            f[4] = nz;
            if (++n == 4) {
                n = 0;
                if (above[0] && above[1] && above[2] && above[3]) {
                    return;
                }
                for (int k = 0; k < 4; k++) {
                    float[] p = pos[k];
                    int[] q = ints[k];
                    float[] w = floats[k];
                    target.vertex(p[0], p[1], p[2], q[0] / 255F, q[1] / 255F, q[2] / 255F, q[3] / 255F, w[0], w[1], q[4], q[5], w[2], w[3], w[4]);
                }
            }
        }

        @Override
        public void defaultColor(int red, int green, int blue, int alpha) {
            target.defaultColor(red, green, blue, alpha);
        }

        @Override
        public void unsetDefaultColor() {
            target.unsetDefaultColor();
        }
    }
}
