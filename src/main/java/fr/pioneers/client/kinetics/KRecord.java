package fr.pioneers.client.kinetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.Arrays;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 0.20.46 (audit) : geometrie de {@link KDraw} enregistree une fois dans le repere local (pose identite), puis rejouee avec la pose courante :
 * icones des pieces dans l'inventaire, JEI et l'onglet creatif (le dessin ne change pas d'une image a l'autre). Les sommets enregistres avec la
 * lumiere {@link #LIGHT} / la superposition {@link #OVERLAY} prennent celles du rejeu.
 */
public final class KRecord implements VertexConsumer {

    /** Lumiere et superposition a passer au KDraw qui enregistre (remplacees au rejeu). */
    public static final int LIGHT = 0x7EADBEEF, OVERLAY = 0x7EADBEEF;
    private static final int STRIDE = 14;

    private float[] data = new float[STRIDE * 64];
    private int[] ints = new int[2 * 64];
    private int count;
    // Sommet en cours (chemin des appels separes)
    private final float[] cur = new float[STRIDE];
    private int curOverlay, curLight;

    // Objets de travail du rejeu (fil de rendu)
    private static final Vector4f POS = new Vector4f();
    private static final Vector3f NRM = new Vector3f();

    public int vertices() {
        return count;
    }

    @Override
    public void vertex(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, int overlay, int light, float nx,
          float ny, float nz) {
        add(x, y, z, red, green, blue, alpha, u, v, nx, ny, nz, overlay, light);
    }

    private void add(float x, float y, float z, float red, float green, float blue, float alpha, float u, float v, float nx, float ny, float nz,
          int overlay, int light) {
        if ((count + 1) * STRIDE > data.length) {
            data = Arrays.copyOf(data, data.length * 2);
            ints = Arrays.copyOf(ints, ints.length * 2);
        }
        int i = count * STRIDE;
        data[i] = x;
        data[i + 1] = y;
        data[i + 2] = z;
        data[i + 3] = red;
        data[i + 4] = green;
        data[i + 5] = blue;
        data[i + 6] = alpha;
        data[i + 7] = u;
        data[i + 8] = v;
        data[i + 9] = nx;
        data[i + 10] = ny;
        data[i + 11] = nz;
        ints[count * 2] = overlay;
        ints[count * 2 + 1] = light;
        count++;
    }

    /** Rejoue la geometrie avec la pose courante (memes calculs que les appels {@code vertex(Matrix4f, ...)} / {@code normal(Matrix3f, ...)}). */
    public void replay(PoseStack pose, VertexConsumer vc, int light, int overlay) {
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float lnx = Float.NaN, lny = Float.NaN, lnz = Float.NaN, tnx = 0, tny = 0, tnz = 0;
        for (int k = 0; k < count; k++) {
            int i = k * STRIDE;
            float nx = data[i + 9], ny = data[i + 10], nz = data[i + 11];
            if (nx != lnx || ny != lny || nz != lnz) {
                n.transform(NRM.set(nx, ny, nz));
                tnx = NRM.x();
                tny = NRM.y();
                tnz = NRM.z();
                lnx = nx;
                lny = ny;
                lnz = nz;
            }
            m.transform(POS.set(data[i], data[i + 1], data[i + 2], 1.0F));
            int o = ints[k * 2], l = ints[k * 2 + 1];
            vc.vertex(POS.x(), POS.y(), POS.z(), data[i + 3], data[i + 4], data[i + 5], data[i + 6], data[i + 7], data[i + 8],
                  o == OVERLAY ? overlay : o, l == LIGHT ? light : l, tnx, tny, tnz);
        }
    }

    // ------------------------------------------------------------------
    // Appels separes (si un dessin ne passe pas par l'appel groupe)
    // ------------------------------------------------------------------

    @NotNull
    @Override
    public VertexConsumer vertex(double x, double y, double z) {
        Arrays.fill(cur, 0);
        cur[0] = (float) x;
        cur[1] = (float) y;
        cur[2] = (float) z;
        cur[3] = cur[4] = cur[5] = cur[6] = 1;
        curOverlay = OVERLAY;
        curLight = LIGHT;
        return this;
    }

    @NotNull
    @Override
    public VertexConsumer color(int r, int g, int b, int a) {
        cur[3] = r / 255F;
        cur[4] = g / 255F;
        cur[5] = b / 255F;
        cur[6] = a / 255F;
        return this;
    }

    @NotNull
    @Override
    public VertexConsumer uv(float u, float v) {
        cur[7] = u;
        cur[8] = v;
        return this;
    }

    @NotNull
    @Override
    public VertexConsumer overlayCoords(int u, int v) {
        curOverlay = u & 65535 | v << 16;
        return this;
    }

    @NotNull
    @Override
    public VertexConsumer uv2(int u, int v) {
        curLight = u & 65535 | v << 16;
        return this;
    }

    @NotNull
    @Override
    public VertexConsumer normal(float x, float y, float z) {
        cur[9] = x;
        cur[10] = y;
        cur[11] = z;
        return this;
    }

    @Override
    public void endVertex() {
        add(cur[0], cur[1], cur[2], cur[3], cur[4], cur[5], cur[6], cur[7], cur[8], cur[9], cur[10], cur[11], curOverlay, curLight);
    }

    @Override
    public void defaultColor(int r, int g, int b, int a) {
    }

    @Override
    public void unsetDefaultColor() {
    }
}
