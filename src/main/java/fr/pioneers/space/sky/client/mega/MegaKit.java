package fr.pioneers.space.sky.client.mega;

/**
 * 1.0.7 (« ciel grandiose ») : briques des megastructures en 3D (calcul pur, repere du sol, camera a l'origine) : faces eclairees par le
 * Soleil (ombre de l'astre selon la hauteur reelle, brume de l'atmosphere), pieces (caissons orientes, poutres, cylindres, anneaux) et
 * lueurs additives (feux, faisceaux, couronnes). Les faces tournees vers l'arriere sont ecartees ici (la carte graphique ne trie rien dans
 * le ciel) ; aucune allocation.
 */
public final class MegaKit {

    private MegaKit() {
    }

    // ------------------------------------------------------------------ faces pleines

    /**
     * Une face pleine eclairee : couleur de base (0..1) x (ambiante + Soleil visible depuis {@code km} de hauteur reelle), reflet
     * speculaire {@code spec} (0 = mat), brume selon la hauteur dans le ciel. Ecartee si elle tourne le dos a la camera.
     */
    public static void face(MegaBuffer b, MegaView v, double km, double x0, double y0, double z0, double x1, double y1, double z1, double x2,
          double y2, double z2, double x3, double y3, double z3, double nx, double ny, double nz, double r, double g, double bl, double spec) {
        double cx = (x0 + x1 + x2 + x3) / 4, cy = (y0 + y1 + y2 + y3) / 4, cz = (z0 + z1 + z2 + z3) / 4;
        if (nx * cx + ny * cy + nz * cz >= 0) {
            return;
        }
        double vis = v.litOverride >= 0 ? v.litOverride : v.sunVisibility(km);
        double nl = nx * v.lx + ny * v.ly + nz * v.lz;
        double diff = Math.max(0, nl) * vis;
        double lr = v.ambient + diff * v.sunR, lg = v.ambient + diff * v.sunG, lb = v.ambient + diff * v.sunB;
        if (spec > 0 && nl > 0) {
            //reflet vers l'observateur : direction reflechie du Soleil . direction de la camera (-c)
            double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
            double rx = 2 * nl * nx - v.lx, ry = 2 * nl * ny - v.ly, rz = 2 * nl * nz - v.lz;
            double s = Math.max(0, -(rx * cx + ry * cy + rz * cz) / cl);
            s = Math.pow(s, 24) * spec * vis;
            lr += s * v.sunR;
            lg += s * v.sunG;
            lb += s * v.sunB;
        }
        double fr = r * lr, fg = g * lg, fb = bl * lb;
        if (v.haze > 0) {
            double e = cy / Math.sqrt(cx * cx + cy * cy + cz * cz);
            double h = v.haze * (1 - MegaView.smooth(-0.05, 0.55, e));
            fr = fr * (1 - h) + v.hazeR * h;
            fg = fg * (1 - h) + v.hazeG * h;
            fb = fb * (1 - h) + v.hazeB * h;
        }
        b.solid(x0, y0, z0, x1, y1, z1, x2, y2, z2, x3, y3, z3, clamp(fr), clamp(fg), clamp(fb), 1);
    }

    /**
     * Caisson oriente : centre, trois demi-axes (u, v, w : longueurs = demi-cotes), couleur de base. Ses faces visibles seulement.
     */
    public static void box(MegaBuffer b, MegaView v, double km, double cx, double cy, double cz, double ux, double uy, double uz, double vx,
          double vy, double vz, double wx, double wy, double wz, double r, double g, double bl, double spec) {
        side(b, v, km, cx, cy, cz, ux, uy, uz, vx, vy, vz, wx, wy, wz, r, g, bl, spec);
        side(b, v, km, cx, cy, cz, -ux, -uy, -uz, wx, wy, wz, vx, vy, vz, r, g, bl, spec);
        side(b, v, km, cx, cy, cz, vx, vy, vz, wx, wy, wz, ux, uy, uz, r, g, bl, spec);
        side(b, v, km, cx, cy, cz, -vx, -vy, -vz, ux, uy, uz, wx, wy, wz, r, g, bl, spec);
        side(b, v, km, cx, cy, cz, wx, wy, wz, ux, uy, uz, vx, vy, vz, r, g, bl, spec);
        side(b, v, km, cx, cy, cz, -wx, -wy, -wz, vx, vy, vz, ux, uy, uz, r, g, bl, spec);
    }

    /** Face {@code +n} d'un caisson : centre de la face = c + n, cotes selon a et b. */
    private static void side(MegaBuffer b, MegaView v, double km, double cx, double cy, double cz, double nx, double ny, double nz, double ax,
          double ay, double az, double bx, double by, double bz, double r, double g, double bl, double spec) {
        double fx = cx + nx, fy = cy + ny, fz = cz + nz;
        double l = Math.sqrt(nx * nx + ny * ny + nz * nz);
        face(b, v, km, fx - ax - bx, fy - ay - by, fz - az - bz, fx + ax - bx, fy + ay - by, fz + az - bz, fx + ax + bx, fy + ay + by, fz + az + bz,
              fx - ax + bx, fy - ay + by, fz - az + bz, nx / l, ny / l, nz / l, r, g, bl, spec);
    }

    /** Poutre de section carree entre deux points (demi-largeur {@code hw}). */
    public static void beam(MegaBuffer b, MegaView v, double km, double x0, double y0, double z0, double x1, double y1, double z1, double hw,
          double r, double g, double bl, double spec) {
        double ax = (x1 - x0) / 2, ay = (y1 - y0) / 2, az = (z1 - z0) / 2;
        double al = Math.sqrt(ax * ax + ay * ay + az * az);
        if (al < 1e-9) {
            return;
        }
        //u : perpendiculaire a l'axe (d'abord dans le plan horizontal), w = axe x u
        double ux = -az, uy = 0, uz = ax;
        double ul = Math.sqrt(ux * ux + uz * uz);
        if (ul < 1e-6 * al) {
            ux = 1;
            uz = 0;
            ul = 1;
        }
        ux = ux / ul * hw;
        uz = uz / ul * hw;
        double wx = ay * uz - az * uy, wy = az * ux - ax * uz, wz = ax * uy - ay * ux;
        double wl = Math.sqrt(wx * wx + wy * wy + wz * wz);
        wx = wx / wl * hw;
        wy = wy / wl * hw;
        wz = wz / wl * hw;
        box(b, v, km, x0 + ax, y0 + ay, z0 + az, ax, ay, az, ux, uy, uz, wx, wy, wz, r, g, bl, spec);
    }

    /**
     * Cylindre (ou prisme) d'axe vertical : centre de la base, rayon, hauteur, {@code n} pans ; couvercles haut et bas en pans triangulaires
     * (quadrilateres degeneres).
     */
    public static void cylinderY(MegaBuffer b, MegaView v, double km, double cx, double cy, double cz, double radius, double height, int n,
          double r, double g, double bl, double spec) {
        for (int k = 0; k < n; k++) {
            double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n, am = (a0 + a1) / 2;
            double x0 = cx + radius * Math.cos(a0), z0 = cz + radius * Math.sin(a0), x1 = cx + radius * Math.cos(a1), z1 = cz + radius * Math.sin(a1);
            face(b, v, km, x0, cy, z0, x1, cy, z1, x1, cy + height, z1, x0, cy + height, z0, Math.cos(am), 0, Math.sin(am), r, g, bl, spec);
            face(b, v, km, cx, cy + height, cz, cx, cy + height, cz, x1, cy + height, z1, x0, cy + height, z0, 0, 1, 0, r, g, bl, spec);
            face(b, v, km, cx, cy, cz, cx, cy, cz, x0, cy, z0, x1, cy, z1, 0, -1, 0, r, g, bl, spec);
        }
    }

    /** Panneau mince (deux faces) : centre, demi-axes a et b (dans son plan), normale unitaire n. */
    public static void panel(MegaBuffer b, MegaView v, double km, double cx, double cy, double cz, double ax, double ay, double az, double bx,
          double by, double bz, double nx, double ny, double nz, double r, double g, double bl, double spec) {
        face(b, v, km, cx - ax - bx, cy - ay - by, cz - az - bz, cx + ax - bx, cy + ay - by, cz + az - bz, cx + ax + bx, cy + ay + by,
              cz + az + bz, cx - ax + bx, cy - ay + by, cz - az + bz, nx, ny, nz, r, g, bl, spec);
        face(b, v, km, cx - ax - bx, cy - ay - by, cz - az - bz, cx + ax - bx, cy + ay - by, cz + az - bz, cx + ax + bx, cy + ay + by,
              cz + az + bz, cx - ax + bx, cy - ay + by, cz - az + bz, -nx, -ny, -nz, r * 0.55, g * 0.55, bl * 0.55, 0);
    }

    // ------------------------------------------------------------------ lueurs

    /** Lueur ronde (carre tourne vers la camera) de demi-taille {@code s}. */
    public static void light(MegaBuffer b, double x, double y, double z, double s, double r, double g, double bl, double a) {
        double l = Math.sqrt(x * x + y * y + z * z);
        double dx = x / l, dy = y / l, dz = z / l;
        //droite = d x haut, haut' = droite x d
        double rx = -dz, rz = dx;
        double rl = Math.sqrt(rx * rx + rz * rz);
        if (rl < 1e-6) {
            rx = 1;
            rz = 0;
            rl = 1;
        }
        rx /= rl;
        rz /= rl;
        double ux = -rz * dy * s, uy = (rz * dx - rx * dz) * s, uz = rx * dy * s;
        rx *= s;
        rz *= s;
        b.dot(x - rx - ux, y - uy, z - rz - uz, x + rx - ux, y - uy, z + rz - uz, x + rx + ux, y + uy, z + rz + uz, x - rx + ux, y + uy,
              z - rz + uz, (float) r, (float) g, (float) bl, (float) clamp(a));
    }

    /** Ruban lumineux tourne vers la camera entre deux points (demi-largeur {@code hw}). */
    public static void ray(MegaBuffer b, double x0, double y0, double z0, double x1, double y1, double z1, double hw, double r, double g,
          double bl, double a) {
        double ax = x1 - x0, ay = y1 - y0, az = z1 - z0;
        double mx = (x0 + x1) / 2, my = (y0 + y1) / 2, mz = (z0 + z1) / 2;
        //cote = axe x milieu (perpendiculaire a la visee)
        double sx = ay * mz - az * my, sy = az * mx - ax * mz, sz = ax * my - ay * mx;
        double sl = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (sl < 1e-9) {
            return;
        }
        sx = sx / sl * hw;
        sy = sy / sl * hw;
        sz = sz / sl * hw;
        b.line(x0 - sx, y0 - sy, z0 - sz, x0 + sx, y0 + sy, z0 + sz, x1 + sx, y1 + sy, z1 + sz, x1 - sx, y1 - sy, z1 - sz, (float) r, (float) g,
              (float) bl, (float) clamp(a));
    }

    /**
     * Cone lumineux (faisceau de projecteur, colonne de chaleur) : ruban qui s'elargit de {@code hw0} a {@code hw1} et s'estompe de
     * {@code a0} a {@code a1}, en {@code n} troncons.
     */
    public static void cone(MegaBuffer b, double x0, double y0, double z0, double x1, double y1, double z1, double hw0, double hw1, double r,
          double g, double bl, double a0, double a1, int n) {
        for (int k = 0; k < n; k++) {
            double u0 = (double) k / n, u1 = (double) (k + 1) / n;
            double sx = x0 + (x1 - x0) * u0, sy = y0 + (y1 - y0) * u0, sz = z0 + (z1 - z0) * u0;
            double ex = x0 + (x1 - x0) * u1, ey = y0 + (y1 - y0) * u1, ez = z0 + (z1 - z0) * u1;
            double ax = ex - sx, ay = ey - sy, az = ez - sz;
            double mx = (sx + ex) / 2, my = (sy + ey) / 2, mz = (sz + ez) / 2;
            double cx = ay * mz - az * my, cy = az * mx - ax * mz, cz = ax * my - ay * mx;
            double cl = Math.sqrt(cx * cx + cy * cy + cz * cz);
            if (cl < 1e-9) {
                return;
            }
            cx /= cl;
            cy /= cl;
            cz /= cl;
            double w0 = hw0 + (hw1 - hw0) * u0, w1 = hw0 + (hw1 - hw0) * u1;
            double a = a0 + (a1 - a0) * (u0 + u1) / 2;
            b.line(sx - cx * w0, sy - cy * w0, sz - cz * w0, sx + cx * w0, sy + cy * w0, sz + cz * w0, ex + cx * w1, ey + cy * w1, ez + cz * w1,
                  ex - cx * w1, ey - cy * w1, ez - cz * w1, (float) r, (float) g, (float) bl, (float) clamp(a));
        }
    }

    /** Couronne lumineuse horizontale (centre, rayon, demi-epaisseur), en {@code n} rubans. */
    public static void ringY(MegaBuffer b, double cx, double cy, double cz, double radius, double hw, int n, double r, double g, double bl,
          double a) {
        for (int k = 0; k < n; k++) {
            double a0 = 2 * Math.PI * k / n, a1 = 2 * Math.PI * (k + 1) / n;
            ray(b, cx + radius * Math.cos(a0), cy, cz + radius * Math.sin(a0), cx + radius * Math.cos(a1), cy, cz + radius * Math.sin(a1), hw, r,
                  g, bl, a);
        }
    }

    // ------------------------------------------------------------------ outils

    public static float clamp(double v) {
        return (float) Math.max(0, Math.min(1, v));
    }

    public static double frac(double v) {
        return v - Math.floor(v);
    }

    /** Clignotement : 1 pendant {@code on} ticks sur {@code period}, decale de {@code phase}. */
    public static double blink(double ticks, double period, double on, double phase) {
        return (ticks + phase) % period < on ? 1 : 0;
    }

    /** Bruit fixe 0..1 (fenetres allumees ou non, rythmes decales). */
    public static double hash(int a, int c) {
        int h = a * 73856093 ^ c * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return (h & 0xFFFF) / 65535.0;
    }

    public static double red(int rgb) {
        return (rgb >> 16 & 255) / 255.0;
    }

    public static double green(int rgb) {
        return (rgb >> 8 & 255) / 255.0;
    }

    public static double blue(int rgb) {
        return (rgb & 255) / 255.0;
    }
}
