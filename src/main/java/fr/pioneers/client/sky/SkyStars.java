package fr.pioneers.client.sky;

import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexBuffer;
import com.mojang.blaze3d.vertex.VertexFormat;
import java.util.Random;

/**
 * Le fond du ciel : les vraies etoiles brillantes (constellations reconnaissables : Orion, la Grande Ourse, Cassiopee,
 * le Scorpion, la Croix du Sud, les Pleiades...), des milliers d'etoiles faibles et la Voie lactee, a sa vraie place.
 * Tout est exprime dans le repere "de base" (Soleil a la longitude 0) ; le rendu tourne ensuite le tout de la
 * longitude du Soleil, si bien que les constellations restent fixes pendant que le Soleil et les planetes y defilent.
 * Egalement : les nuees d'asteroides de la ceinture principale.
 */
public final class SkyStars {

    private SkyStars() {
    }

    /** Ascension droite (h), declinaison (deg), magnitude, type spectral (B, A, F, G, K, M). */
    private static final Object[][] BRIGHT = {
          {6.752, -16.72, -1.46, 'A'}, {6.399, -52.70, -0.74, 'F'}, {14.261, 19.18, -0.05, 'K'}, {14.660, -60.83, -0.27, 'G'},
          {18.616, 38.78, 0.03, 'A'}, {5.278, 46.00, 0.08, 'G'}, {5.242, -8.20, 0.13, 'B'}, {7.655, 5.22, 0.34, 'F'},
          {1.629, -57.24, 0.46, 'B'}, {5.919, 7.41, 0.50, 'M'}, {14.064, -60.37, 0.61, 'B'}, {19.846, 8.87, 0.77, 'A'},
          {12.443, -63.10, 0.77, 'B'}, {4.599, 16.51, 0.85, 'K'}, {16.490, -26.43, 0.96, 'M'}, {13.420, -11.16, 0.97, 'B'},
          {7.755, 28.03, 1.14, 'K'}, {22.961, -29.62, 1.16, 'A'}, {20.690, 45.28, 1.25, 'A'}, {12.795, -59.69, 1.25, 'B'},
          {10.140, 11.97, 1.35, 'B'}, {6.977, -28.97, 1.50, 'B'}, {7.577, 31.89, 1.58, 'A'}, {17.560, -37.10, 1.62, 'B'},
          {12.519, -57.11, 1.63, 'M'}, {5.419, 6.35, 1.64, 'B'}, {5.438, 28.61, 1.65, 'B'}, {9.220, -69.72, 1.67, 'A'},
          {5.604, -1.20, 1.69, 'B'}, {5.679, -1.94, 1.77, 'B'}, {5.533, -0.30, 2.23, 'B'}, {5.796, -9.67, 2.06, 'B'},
          {5.585, 9.93, 3.39, 'B'}, {12.900, 55.96, 1.77, 'A'}, {11.062, 61.75, 1.79, 'K'}, {11.031, 56.38, 2.37, 'A'},
          {11.897, 53.69, 2.44, 'A'}, {12.257, 57.03, 3.31, 'A'}, {13.399, 54.93, 2.23, 'A'}, {13.792, 49.31, 1.86, 'B'},
          {2.530, 89.26, 1.98, 'F'}, {3.405, 49.86, 1.79, 'F'}, {7.140, -26.39, 1.83, 'F'}, {17.622, -43.00, 1.86, 'F'},
          {18.403, -34.38, 1.85, 'B'}, {8.375, -59.51, 1.86, 'K'}, {5.992, 44.95, 1.90, 'A'}, {16.811, -69.03, 1.91, 'K'},
          {6.628, 16.40, 1.93, 'A'}, {20.427, -56.74, 1.94, 'B'}, {6.378, -17.96, 1.98, 'B'}, {9.460, -8.66, 1.98, 'K'},
          {2.120, 23.46, 2.00, 'K'}, {0.726, -17.99, 2.04, 'K'}, {18.921, -26.30, 2.05, 'B'}, {14.111, -36.37, 2.06, 'K'},
          {0.140, 29.09, 2.06, 'B'}, {1.162, 35.62, 2.05, 'M'}, {14.845, 74.16, 2.08, 'K'}, {17.582, 12.56, 2.08, 'A'},
          {3.136, 40.96, 2.10, 'B'}, {2.065, 42.33, 2.10, 'K'}, {11.818, 14.57, 2.14, 'A'}, {0.675, 56.54, 2.24, 'K'},
          {0.153, 59.15, 2.28, 'F'}, {0.945, 60.72, 2.15, 'B'}, {1.430, 60.24, 2.68, 'A'}, {1.907, 63.67, 3.37, 'B'},
          {20.370, 40.26, 2.23, 'F'}, {19.512, 27.96, 3.05, 'K'}, {20.770, 33.97, 2.48, 'K'}, {19.750, 45.13, 2.87, 'B'},
          {17.943, 51.49, 2.24, 'K'}, {21.736, 9.88, 2.39, 'K'}, {23.079, 15.21, 2.48, 'B'}, {23.063, 28.08, 2.42, 'M'},
          {0.221, 15.18, 2.83, 'B'}, {16.006, -22.62, 2.29, 'B'}, {16.091, -19.81, 2.62, 'B'}, {10.333, 19.84, 2.08, 'K'},
          {11.235, 20.52, 2.56, 'A'}, {3.038, 4.09, 2.54, 'M'}, {3.791, 24.11, 2.87, 'B'}, {3.819, 24.05, 3.62, 'B'},
          {3.747, 24.11, 3.70, 'B'}, {3.763, 24.37, 3.87, 'B'}, {3.772, 23.95, 4.18, 'B'}, {3.754, 24.47, 4.30, 'B'},
          {4.477, 19.18, 3.53, 'K'}, {4.478, 15.87, 3.40, 'A'}, {4.330, 15.63, 3.65, 'K'}, {4.382, 17.54, 3.76, 'K'},
          {16.836, -34.29, 2.29, 'K'}, {16.864, -38.05, 3.00, 'B'}, {16.909, -42.36, 3.60, 'K'}, {17.202, -43.24, 3.33, 'F'},
          {17.793, -40.13, 3.00, 'F'}, {17.708, -39.03, 2.39, 'B'}, {16.598, -28.22, 2.82, 'B'}, {16.353, -25.59, 2.89, 'B'},
          {15.981, -26.11, 2.89, 'B'}, {10.122, 16.76, 3.50, 'A'}, {9.764, 23.77, 2.97, 'G'}, {11.237, 15.43, 3.33, 'A'},
          {18.835, 33.36, 3.52, 'B'}, {18.982, 32.69, 3.25, 'B'}, {19.771, 10.61, 2.72, 'K'}, {19.922, 6.41, 3.71, 'G'},
          {12.252, -58.75, 2.79, 'B'}, {6.732, 25.13, 3.06, 'G'}, {6.383, 22.51, 2.87, 'M'}};

    private static final double OBLIQUITY = Math.toRadians(23.4393);

    private static float[] spectral(char type) {
        return switch (type) {
            case 'B' -> new float[]{0.72F, 0.82F, 1.0F};
            case 'A' -> new float[]{0.9F, 0.93F, 1.0F};
            case 'F' -> new float[]{1.0F, 1.0F, 0.94F};
            case 'G' -> new float[]{1.0F, 0.94F, 0.8F};
            case 'K' -> new float[]{1.0F, 0.8F, 0.58F};
            default -> new float[]{1.0F, 0.64F, 0.48F};
        };
    }

    /** Equatorial (RA h, Dec deg) vers le repere de base. */
    private static double[] fromEquatorial(double raHours, double decDeg) {
        double ra = Math.toRadians(raHours * 15), dec = Math.toRadians(decDeg);
        double sinBeta = Math.sin(dec) * Math.cos(OBLIQUITY) - Math.cos(dec) * Math.sin(OBLIQUITY) * Math.sin(ra);
        double lambda = Math.atan2(Math.sin(ra) * Math.cos(OBLIQUITY) + Math.tan(dec) * Math.sin(OBLIQUITY), Math.cos(ra));
        double beta = Math.asin(sinBeta);
        double[] out = new double[3];
        SkyScene.toBase(Math.cos(beta) * Math.cos(lambda), Math.cos(beta) * Math.sin(lambda), Math.sin(beta), out);
        return out;
    }

    private static double starSize(double mag) {
        return Math.max(0.04, Math.min(0.22, 0.04 + 0.022 * (6.5 - mag)));
    }

    private static float starAlpha(double mag) {
        return (float) Math.max(0.22, Math.min(1, 0.22 + 0.13 * (6.5 - mag)));
    }

    /** Etoiles brillantes, etoiles faibles et Voie lactee. */
    public static VertexBuffer buildStars() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Random random = new Random(20261013L);
        //Voie lactee (dessinee en premier, sous les etoiles)
        double[] pole = SkyScene.unitFromLonLat(180.02, 29.81);
        double[] center = SkyScene.unitFromLonLat(266.84, -5.54);
        double d = dot(center, pole);
        for (int i = 0; i < 3; i++) {
            center[i] -= d * pole[i];
        }
        normalize(center);
        double[] side = cross(pole, center);
        int placed = 0;
        while (placed < 12000) {
            double l = (random.nextDouble() * 2 - 1) * Math.PI;
            double ld = Math.toDegrees(l);
            double weight = 0.3 + Math.exp(-(ld / 38) * (ld / 38)) + 0.25 * Math.exp(-((Math.abs(ld) - 100) / 30) * ((Math.abs(ld) - 100) / 30));
            if (random.nextDouble() * 1.6 > weight) {
                continue;
            }
            double sigma = 3.2 + 4.5 * Math.exp(-(ld / 25) * (ld / 25));
            double b = random.nextGaussian() * sigma;
            //Grand Rift : bande de poussiere sombre au milieu
            if (Math.abs(b) < 1.4 && ld > -70 && ld < 45 && random.nextDouble() < 0.65) {
                continue;
            }
            double br = Math.toRadians(b);
            double[] v = new double[3];
            for (int k = 0; k < 3; k++) {
                v[k] = Math.cos(br) * (Math.cos(l) * center[k] + Math.sin(l) * side[k]) + Math.sin(br) * pole[k];
            }
            double[] base = new double[3];
            SkyScene.toBase(v[0], v[1], v[2], base);
            float warm = (float) Math.exp(-(ld / 30) * (ld / 30));
            float a = 0.07F + random.nextFloat() * 0.16F + warm * 0.08F;
            star(builder, random, base, 0.03 + random.nextDouble() * 0.025, 0.82F + warm * 0.18F, 0.86F + warm * 0.06F, 1.0F - warm * 0.22F, a);
            placed++;
        }
        //Etoiles faibles
        for (int i = 0; i < 4200; i++) {
            double[] v = randomUnit(random);
            double mag = 6.5 - 5.2 * Math.pow(random.nextDouble(), 2.4);
            float tint = random.nextFloat();
            float r = tint < 0.2F ? 0.8F : tint > 0.85F ? 1.0F : 0.95F;
            float g = tint > 0.85F ? 0.85F : 0.95F;
            float bl = tint < 0.2F ? 1.0F : tint > 0.85F ? 0.7F : 0.95F;
            star(builder, random, v, starSize(mag), r, g, bl, starAlpha(mag));
        }
        //Etoiles brillantes reelles
        for (Object[] s : BRIGHT) {
            double[] v = fromEquatorial((Double) s[0], (Double) s[1]);
            double mag = (Double) s[2];
            float[] c = spectral((Character) s[3]);
            star(builder, random, v, starSize(mag), c[0], c[1], c[2], starAlpha(mag));
        }
        return upload(builder);
    }

    /**
     * Nuee d'asteroides de la ceinture : une coquille de points proches du plan de l'ecliptique.
     *
     * @param spread dispersion en latitude (degres)
     */
    public static VertexBuffer buildAsteroidShell(long seed, int count, double spread, double minSize, double maxSize) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        Random random = new Random(seed);
        double[] base = new double[3];
        for (int i = 0; i < count; i++) {
            double lon = random.nextDouble() * Math.PI * 2;
            double lat = Math.toRadians(random.nextGaussian() * spread);
            SkyScene.toBase(Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat), base);
            float g = 0.62F + random.nextFloat() * 0.3F;
            float a = 0.25F + random.nextFloat() * 0.65F;
            double size = minSize + (maxSize - minSize) * Math.pow(random.nextDouble(), 3);
            star(builder, random, base, size, g * 1.02F, g * 0.96F, g * 0.88F, a);
        }
        return upload(builder);
    }

    /** Lueur diffuse de la ceinture : bande douce le long de l'ecliptique (rayon 100). */
    public static VertexBuffer buildBeltGlow() {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR);
        int seg = 96;
        double[] lats = {-16, -7, 0, 7, 16};
        float[] alphas = {0, 0.07F, 0.14F, 0.07F, 0};
        double[] p = new double[3];
        for (int i = 0; i < seg; i++) {
            double l0 = Math.PI * 2 * i / seg, l1 = Math.PI * 2 * (i + 1) / seg;
            for (int j = 0; j < lats.length - 1; j++) {
                double b0 = Math.toRadians(lats[j]), b1 = Math.toRadians(lats[j + 1]);
                vertex(builder, l0, b0, alphas[j], p);
                vertex(builder, l1, b0, alphas[j], p);
                vertex(builder, l1, b1, alphas[j + 1], p);
                vertex(builder, l0, b1, alphas[j + 1], p);
            }
        }
        return upload(builder);
    }

    private static void vertex(BufferBuilder builder, double lon, double lat, float alpha, double[] p) {
        SkyScene.toBase(Math.cos(lat) * Math.cos(lon), Math.cos(lat) * Math.sin(lon), Math.sin(lat), p);
        builder.vertex(p[0] * 100, p[1] * 100, p[2] * 100).color(0.86F, 0.78F, 0.66F, alpha).endVertex();
    }

    private static VertexBuffer upload(BufferBuilder builder) {
        VertexBuffer buffer = new VertexBuffer(VertexBuffer.Usage.STATIC);
        buffer.bind();
        buffer.upload(builder.end());
        VertexBuffer.unbind();
        return buffer;
    }

    /** Petit carre (a la maniere des etoiles vanilla) a 100 blocs, taille en degres. */
    private static void star(BufferBuilder builder, Random random, double[] v, double sizeDeg, float r, float g, float b, float a) {
        double size = 100 * Math.tan(Math.toRadians(sizeDeg));
        double x = v[0], y = v[1], z = v[2];
        double px = x * 100, py = y * 100, pz = z * 100;
        double yaw = Math.atan2(x, z);
        double sinYaw = Math.sin(yaw), cosYaw = Math.cos(yaw);
        double pitch = Math.atan2(Math.sqrt(x * x + z * z), y);
        double sinPitch = Math.sin(pitch), cosPitch = Math.cos(pitch);
        double roll = random.nextDouble() * Math.PI * 2;
        double sinRoll = Math.sin(roll), cosRoll = Math.cos(roll);
        for (int corner = 0; corner < 4; ++corner) {
            double ca = (double) ((corner & 2) - 1) * size;
            double cb = (double) ((corner + 1 & 2) - 1) * size;
            double c = ca * cosRoll - cb * sinRoll;
            double dd = cb * cosRoll + ca * sinRoll;
            double e = c * sinPitch;
            double f = -c * cosPitch;
            double gg = f * sinYaw - dd * cosYaw;
            double h = dd * sinYaw + f * cosYaw;
            builder.vertex(px + gg, py + e, pz + h).color(r, g, b, a).endVertex();
        }
    }

    private static double[] randomUnit(Random random) {
        while (true) {
            double x = random.nextDouble() * 2 - 1, y = random.nextDouble() * 2 - 1, z = random.nextDouble() * 2 - 1;
            double l = x * x + y * y + z * z;
            if (l < 1 && l > 0.01) {
                l = Math.sqrt(l);
                return new double[]{x / l, y / l, z / l};
            }
        }
    }

    private static double dot(double[] a, double[] b) {
        return a[0] * b[0] + a[1] * b[1] + a[2] * b[2];
    }

    private static double[] cross(double[] a, double[] b) {
        return new double[]{a[1] * b[2] - a[2] * b[1], a[2] * b[0] - a[0] * b[2], a[0] * b[1] - a[1] * b[0]};
    }

    private static void normalize(double[] v) {
        double l = Math.sqrt(dot(v, v));
        v[0] /= l;
        v[1] /= l;
        v[2] /= l;
    }
}
