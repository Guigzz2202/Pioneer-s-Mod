package fr.pioneers.tech.industry.client.big;

import fr.pioneers.tech.industry.multi.BigInterior.Kind;
import java.util.Arrays;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 0.19.14 : geometrie des machines interieures d'un seul tenant (apercus valides par Guillaume le 25/10/2026). Tout est en px dans le
 * repere canonique de {@link BigMesh} : x de 0 a W en travers, y de 0 a H, z de 0 a L le long de l'axe.
 * <ul>
 *     <li>{@link #build} : partie fixe et pieces tournantes (groupes), calculee une fois par structure ;</li>
 *     <li>{@link #dynamic} : ce qui change a chaque image (bande laminee, bulles, LED, minerai qui tombe), selon {@link Anim}.</li>
 * </ul>
 * Executee telle quelle par le banc d'apercus ({@code tools/bigprev}) : ce qu'on valide sur les images est ce qui est dessine en jeu.
 */
public final class BigMachines {

    /** Etat d'animation : regime 0..1 (montee et descente progressives), phase (unites : 1 par tick a plein regime), bande laminee. */
    public static final class Anim {

        public float speed, phase;
        /** Laminoir : cellules de 4 px occupees par la bande (0 = entree) et avance de la cellule 0 (0..4 px). */
        public boolean[] strip = new boolean[0];
        public float shift;
    }

    /** Vitesse de la bande laminee (px par unite de phase) = vitesse en surface des cylindres de travail. */
    public static final float STRIP_SPEED = 1.2F;
    private static final float DEG = 57.29578F;

    private BigMachines() {
    }

    /** 1.0.4-beta (lot 25) : marge (px) entre le dessin et les parois de sa boite (pas de face confondue avec celle d'un bloc voisin). */
    public static final float FIT_MARGIN = 0.25F;
    /** 1.0.4-beta (lot 25) : echantillons du tour complet (angles des groupes tournants, phases de la partie dynamique). */
    public static final int FIT_ANGLES = 36, FIT_PHASES = 240;
    private static final float FIT_PHASE_STEP = 14.93F;

    /**
     * 1.0.4-beta (lot 25) : boite du dessin {x0, y0, z0, x1, y1, z1} (px, repere canonique) sur un tour complet : partie fixe avec ses groupes
     * tournants a {@link #FIT_ANGLES} angles, partie dynamique a {@link #FIT_PHASES} phases au regime plein (bande engagee).
     */
    public static float[] bounds(Kind kind, float w, float h, float l, int[] stands, int[] prof, int tint, int angles, int phases) {
        float[] b = {Float.MAX_VALUE, Float.MAX_VALUE, Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE, -Float.MAX_VALUE};
        BigMesh mesh = build(kind, w, h, l, stands, prof, tint);
        Vector3f v = new Vector3f();
        for (BigMesh.Quad q : mesh.quads) {
            BigMesh.Group g = mesh.groups.get(q.group);
            int n = q.group == 0 || g.factor() == 0 ? 1 : angles;
            for (int k = 0; k < n; k++) {
                Matrix4f m = n == 1 ? null : BigMesh.rot(g.axis(), g.px(), g.py(), g.pz(), k * 360F / n);
                for (int i = 0; i < 4; i++) {
                    v.set(q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]);
                    if (m != null) {
                        m.transformPosition(v);
                    }
                    grow(b, v.x, v.y, v.z);
                }
            }
        }
        Anim a = new Anim();
        a.speed = 1;
        BigMesh dyn = new BigMesh();
        for (int k = 0; k < phases; k++) {
            a.phase = k * FIT_PHASE_STEP;
            if (kind == Kind.ROLLING) {
                feedStrip(a, l, 0, true);
                Arrays.fill(a.strip, true);
                a.shift = (k * 0.37F) % 4;
            }
            dynamic(dyn.reset(), kind, w, h, l, stands, prof, tint, a);
            for (BigMesh.Quad q : dyn.quads) {
                for (int i = 0; i < 4; i++) {
                    grow(b, q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]);
                }
            }
        }
        return b;
    }

    private static void grow(float[] b, float x, float y, float z) {
        b[0] = Math.min(b[0], x);
        b[1] = Math.min(b[1], y);
        b[2] = Math.min(b[2], z);
        b[3] = Math.max(b[3], x);
        b[4] = Math.max(b[4], y);
        b[5] = Math.max(b[5], z);
    }

    /**
     * 1.0.4-beta (lot 25) : mise a l'echelle qui ramene un dessin de boite {@code b} DANS sa boite (W, H, L) a {@link #FIT_MARGIN} des parois :
     * {sxz, sy, dx, dy, dz} (point dessine = (x sxz + dx, y sy + dy, z sxz + dz)) ; identite s'il y tient deja. Meme echelle en x et en z
     * (sections rondes gardees), hauteur a part : une machine dessinee pour une salle plus haute (portiques des chaines a 2 blocs) est
     * abaissee sans etre retrecie. « Rien ne doit depasser » : machines interieures des structures de Guillaume ({@code GeneratedNbtModels}).
     */
    public static float[] fit(float[] b, float w, float h, float l) {
        float m = FIT_MARGIN;
        float sxz = 1, sy = 1;
        if (b[3] - b[0] > w - 2 * m) {
            sxz = Math.min(sxz, (w - 2 * m) / (b[3] - b[0]));
        }
        if (b[5] - b[2] > l - 2 * m) {
            sxz = Math.min(sxz, (l - 2 * m) / (b[5] - b[2]));
        }
        if (b[4] - b[1] > h - 2 * m) {
            sy = (h - 2 * m) / (b[4] - b[1]);
        }
        float[] s = {sxz, sy, sxz}, size = {w, h, l};
        float[] out = {sxz, sy, 0, 0, 0};
        for (int i = 0; i < 3; i++) {
            float lo = b[i] * s[i], hi = b[i + 3] * s[i];
            out[i + 2] = lo < m ? m - lo : hi > size[i] - m ? size[i] - m - hi : 0;
        }
        return out;
    }

    public static BigMesh build(Kind kind, float w, float h, float l, int[] stands) {
        return build(kind, w, h, l, stands, new int[0], 0xFFFFFF);
    }

    /** 0.19.15 : profil de la vue (rangees en y : 10 x cases + 1 si pleine) et teinte RGB (liquides, charges). */
    public static BigMesh build(Kind kind, float w, float h, float l, int[] stands, int[] prof, int tint) {
        BigMesh m = new BigMesh();
        switch (kind) {
            case TURBINE -> turbine(m, w, h, l);
            case ROLLING -> rolling(m, w, h, l, stands);
            case PHOTOBIO -> photobio(m, w, h, l, prof);
            case CRUSHER, CHIPPER -> crusher(m, w, h, l);
            case FURNACE -> Lot2.furnace(m, w, h, l, prof);
            case HEATER -> Lot2.heater(m, w, h, l, prof);
            case SHAFT -> Lot2.shaft(m, w, h, l, prof);
            case KILN -> Lot2.drum(m, w, h, l, false);
            case MILL -> Lot2.drum(m, w, h, l, true);
            case TANK -> Lot2.tank(m, w, h, l, prof);
            case COLUMN -> Lot2.column(m, w, h, l, prof);
            case PACKED -> Lot2.packed(m, w, h, l, prof);
            case CELL -> Lot2.cell(m, w, h, l);
            case DRILL -> Lot3.drill(m, w, h, l);
            case WELL -> Lot3.well(m, w, h, l);
            case JACK -> Lot3.jack(m, w, h, l);
            case CASCADE -> Lot3.cascade(m, w, h, l, prof);
            case CENTRIFUGE -> Lot3.centrifuge(m, w, h, l, prof);
            case BOILER -> Lot3.boiler(m, w, h, l);
            case SCREW -> Lot3.screwMachine(m, w, h, l);
            case PRESS -> Lot4.press(m, w, h, l, 0);
            case HPRESS -> Lot4.press(m, w, h, l, 1);
            case BRAKE -> Lot4.press(m, w, h, l, 2);
            case PELLET -> Lot4.pellet(m, w, h, l);
            case LATHE, ASSEMBLY, WELDER, PICKPLACE, SAWLINE, MOLDER, SORTER, CASTER, BOTTLER -> Lot4.line(m, line(kind), w, h, l, stands);
            case SIEVE -> Lot5.sifter(m, w, h, l);
            case TABLE -> Lot5.shakingTable(m, w, h, l);
            case FLOTATION -> Lot5.flotation(m, w, h, l);
            case QUENCH -> Lot5.dip(m, Lot5.Dip.QUENCH, w, h, l);
            case GALVANIZE -> Lot5.dip(m, Lot5.Dip.GALVANIZE, w, h, l);
            case ETCH -> Lot5.dip(m, Lot5.Dip.ETCH, w, h, l);
            case CLARIFIER -> Lot5.decanter(m, w, h, l);
            case SETTLER -> Lot5.solvent(m, w, h, l);
            case FISHFARM -> Lot5.aquafarm(m, w, h, l);
            case FUELPOOL -> Lot5.fuelPool(m, w, h, l);
            case WAFERSAW -> Lot6.fab(m, Lot6.Fab.SAW, w, h, l, prof);
            case LITHO -> Lot6.fab(m, Lot6.Fab.LITHO, w, h, l, prof);
            case FAB -> Lot6.fab(m, Lot6.Fab.FAB, w, h, l, prof);
            case LASERSEP -> Lot6.laserSep(m, w, h, l, prof);
            case HYDRO -> Lot6.hydro(m, w, h, l, prof);
            case TREES -> Lot6.trees(m, w, h, l, prof);
            case PULLER -> Lot6.puller(m, w, h, l);
            case PULSED -> Lot6.pulsed(m, w, h, l);
            case DIFFUSION -> Lot6.diffusion(m, w, h, l, prof);
            case GANTRY -> Lot12.gantry(m, w, h, l);
            case MAGSEP -> Lot6.magSep(m, w, h, l, prof);
            case DESALTER -> Lot6.desalter(m, w, h, l);
            case MEMBRANE -> Lot6.membrane(m, w, h, l, prof);
            case AUTOCLAVE -> Lot6.autoclave(m, w, h, l);
            case CYCLONE -> Lot7.cyclone(m, w, h, l, prof);
            case COOLTOWER -> Lot7.cooling(m, w, h, l, prof, stands);
            case CRYOSPHERE -> Lot7.sphere(m, w, h, l, prof);
            case MIXCOLUMN -> Lot7.mixer(m, w, h, l);
            case PLATEHX -> Lot7.plates(m, w, h, l);
            case HOTCELL -> Lot7.hotCell(m, w, h, l);
            case FORGE -> Lot7.forge(m, w, h, l);
            case CHILLER -> Lot7.chiller(m, w, h, l);
            case COKEBATTERY -> Lot8.cokeOven(m, w, h, l);
            case DRYDOCK -> Lot9.drydock(m, w, h, l);
            case GRAVITY -> Lot10.gravity(m, w, h, l);
            case SYNTH -> Lot10.synth(m, w, h, l);
            case DEPOT -> Lot11.depot(m, w, h, l, stands);
        }
        return m;
    }

    public static void dynamic(BigMesh m, Kind kind, float w, float h, float l, Anim a) {
        dynamic(m, kind, w, h, l, new int[0], 0xFFFFFF, a);
    }

    public static void dynamic(BigMesh m, Kind kind, float w, float h, float l, int[] prof, int tint, Anim a) {
        dynamic(m, kind, w, h, l, new int[0], prof, tint, a);
    }

    /** 0.19.18 : chaine d'usinage de la famille (lot 4). */
    private static Lot4.Line line(Kind kind) {
        return switch (kind) {
            case LATHE -> Lot4.Line.LATHE;
            case ASSEMBLY -> Lot4.Line.ASSEMBLY;
            case WELDER -> Lot4.Line.WELD;
            case PICKPLACE -> Lot4.Line.PLACE;
            case SAWLINE -> Lot4.Line.SAW;
            case MOLDER -> Lot4.Line.MOLD;
            case SORTER -> Lot4.Line.SORT;
            case CASTER -> Lot4.Line.CAST;
            default -> Lot4.Line.BOTTLE;
        };
    }

    public static void dynamic(BigMesh m, Kind kind, float w, float h, float l, int[] stands, int[] prof, int tint, Anim a) {
        switch (kind) {
            case ROLLING -> rollingStrip(m, w, l, a);
            case PHOTOBIO -> photobioLife(m, w, h, l, prof, a);
            case CRUSHER -> crusherOre(m, w, h, l, a, BigMat.ORE1, BigMat.ORE2);
            case CHIPPER -> crusherOre(m, w, h, l, a, BigMat.WOOD1, BigMat.WOOD2);
            case FURNACE -> Lot2.furnaceLife(m, w, h, l, prof, a);
            case HEATER -> Lot2.heaterLife(m, w, h, l, prof, a);
            case SHAFT -> Lot2.shaftLife(m, w, h, l, prof, a);
            case KILN -> Lot2.drumLife(m, w, h, l, false, a);
            case MILL -> Lot2.drumLife(m, w, h, l, true, a);
            case TANK -> Lot2.tankLife(m, w, h, l, prof, tint, a);
            case COLUMN -> Lot2.columnLife(m, w, h, l, prof, tint, a);
            case PACKED -> Lot2.packedLife(m, w, h, l, prof, a);
            case CELL -> Lot2.cellLife(m, w, h, l, tint, a);
            case DRILL -> Lot3.drillLife(m, w, h, l, a);
            case WELL -> Lot3.wellLife(m, w, h, l, tint, a);
            case JACK -> Lot3.jackLife(m, w, h, l, a);
            case CASCADE -> Lot3.cascadeLife(m, w, h, l, prof, a);
            case BOILER -> Lot3.boilerLife(m, w, h, l, tint, a);
            case SCREW -> Lot3.screwLife(m, w, h, l, tint, a);
            case PRESS -> Lot4.pressLife(m, w, h, l, 0, tint, a);
            case HPRESS -> Lot4.pressLife(m, w, h, l, 1, tint, a);
            case BRAKE -> Lot4.pressLife(m, w, h, l, 2, tint, a);
            case PELLET -> Lot4.pelletLife(m, w, h, l, tint, a);
            case LATHE, ASSEMBLY, WELDER, PICKPLACE, SAWLINE, MOLDER, SORTER, CASTER, BOTTLER -> Lot4.lineLife(m, line(kind), w, h, l, stands, tint, a);
            case SIEVE -> Lot5.sifterLife(m, w, h, l, tint, a);
            case TABLE -> Lot5.shakingTableLife(m, w, h, l, tint, a);
            case FLOTATION -> Lot5.flotationLife(m, w, h, l, tint, a);
            case QUENCH -> Lot5.dipLife(m, Lot5.Dip.QUENCH, w, h, l, tint, a);
            case GALVANIZE -> Lot5.dipLife(m, Lot5.Dip.GALVANIZE, w, h, l, tint, a);
            case ETCH -> Lot5.dipLife(m, Lot5.Dip.ETCH, w, h, l, tint, a);
            case CLARIFIER -> Lot5.decanterLife(m, w, h, l, tint, a);
            case SETTLER -> Lot5.solventLife(m, w, h, l, tint, a);
            case FISHFARM -> Lot5.aquafarmLife(m, w, h, l, tint, a);
            case FUELPOOL -> Lot5.fuelPoolLife(m, w, h, l, tint, a);
            case WAFERSAW -> Lot6.fabLife(m, Lot6.Fab.SAW, w, h, l, prof, a);
            case LITHO -> Lot6.fabLife(m, Lot6.Fab.LITHO, w, h, l, prof, a);
            case FAB -> Lot6.fabLife(m, Lot6.Fab.FAB, w, h, l, prof, a);
            case LASERSEP -> Lot6.laserSepLife(m, w, h, l, prof, a);
            case HYDRO -> Lot6.hydroLife(m, w, h, l, prof, a);
            case TREES -> Lot6.treesLife(m, w, h, l, prof, a);
            case PULLER -> Lot6.pullerLife(m, w, h, l, a);
            case PULSED -> Lot6.pulsedLife(m, w, h, l, tint, a);
            case DIFFUSION -> Lot6.diffusionLife(m, w, h, l, a);
            case GANTRY -> Lot12.gantryLife(m, w, h, l, a);
            case MAGSEP -> Lot6.magSepLife(m, w, h, l, tint, a);
            case DESALTER -> Lot6.desalterLife(m, w, h, l, tint, a);
            case MEMBRANE -> Lot6.membraneLife(m, w, h, l, prof, tint, a);
            case AUTOCLAVE -> Lot6.autoclaveLife(m, w, h, l, tint, a);
            case CYCLONE -> Lot7.cycloneLife(m, w, h, l, prof, tint, a);
            case COOLTOWER -> Lot7.coolingLife(m, w, h, l, prof, stands, tint, a);
            case CRYOSPHERE -> Lot7.sphereLife(m, w, h, l, prof, tint, a);
            case MIXCOLUMN -> Lot7.mixerLife(m, w, h, l, tint, a);
            case PLATEHX -> Lot7.platesLife(m, w, h, l, a);
            case HOTCELL -> Lot7.hotCellLife(m, w, h, l, a);
            case FORGE -> Lot7.forgeLife(m, w, h, l, a);
            case CHILLER -> Lot7.chillerLife(m, w, h, l, a);
            case COKEBATTERY -> Lot8.cokeOvenLife(m, w, h, l, a);
            case DRYDOCK -> Lot9.drydockLife(m, w, h, l, a);
            case GRAVITY -> Lot10.gravityLife(m, w, h, l, a);
            case SYNTH -> Lot10.synthLife(m, w, h, l, tint, a);
            case DEPOT -> Lot11.depotLife(m, w, h, l, stands, a);
            default -> {
            }
        }
    }

    /** Pseudo-hasard stable 0..1. */
    static float hash(int a, int b) {
        int x = a * 73856093 ^ b * 19349663;
        x ^= x >>> 13;
        x *= 0x5bd1e995;
        x ^= x >>> 15;
        return (x & 0xFFFF) / 65535F;
    }

    // ------------------------------------------------------------------ turbine : rotor a etages

    private static void turbine(BigMesh m, float x1, float y1, float z1) {
        float cx = x1 / 2, cy = y1 / 2;
        float r = Math.min(x1, y1) / 2 - 1.5F;
        //petites turbines (1 bloc de diametre) : moyeu, aubes et frettes a l'echelle
        float k = Math.max(0.4F, Math.min(1, r / 38));
        int nb = Math.max(16, Math.round(36 * Math.min(1, r / 30)));
        int spin = m.spin('z', cx, cy, 0, 12);
        m.cyl('z', cx, cy, Math.max(1.5F, 3.6F * k), 2, z1 - 2, BigMat.CHROME, 10, null, 16);                //arbre
        int n = Math.max(3, (int) (z1 / 26));
        float span = (z1 - 28) / Math.max(1, n - 1);
        for (int i = 0; i < n; i++) {
            float t = i / (float) Math.max(1, n - 1);
            float zs = 14 + i * span;
            float len = r * (0.52F + 0.4F * t);
            m.group(spin);
            m.cyl('z', cx, cy, (6 + 2 * t) * k, zs - 2.6F, zs + 2.6F, BigMat.STEEL_D, 16, null, 16);    //disque
            Matrix4f pitch = BigMesh.rot('y', cx, 0, zs, 38);
            for (int b = 0; b < nb; b++) {
                m.box(cx - 1.9F * k, cy + 5.5F * k, zs - 0.35F, cx + 1.9F * k, cy + len, zs + 0.35F, BigMat.CHROME,
                      BigMesh.chain(pitch, BigMesh.rot('z', cx, cy, zs, b * 360F / nb)), 3);
            }
            m.cyl('z', cx, cy, len + 0.7F * k, zs - 1.5F, zs + 1.5F, BigMat.CHROME, 28, null, 16, false, 0.7F * k);  //frette en bout d'aubes
            if (i < n - 1) {
                //diaphragme fixe entre deux etages
                float zd = zs + span / 2;
                m.fixed().cyl('z', cx, cy, r, zd - 0.8F, zd + 0.8F, BigMat.STEEL_D, 28, null, 16, false, 2.2F * k);
            }
        }
        m.fixed();
        float pw = Math.min(6, x1 * 0.35F);
        for (float zb : new float[]{0.5F, z1 - 4.5F}) {
            m.box(cx - pw, 0, zb, cx + pw, cy - 3 * k, zb + 4, BigMat.STEEL_D, null, 4);                   //paliers
        }
    }

    // ------------------------------------------------------------------ laminoir : cages quarto, table a rouleaux, bande

    /** Marge des cages (montants) : 8 px sur une ligne de 3 blocs, a l'echelle sur une ligne d'un bloc. */
    private static float rollMargin(float x1) {
        return Math.min(8, x1 / 6);
    }

    private static void rolling(BigMesh m, float x1, float y1, float z1, int[] stands) {
        float cx = x1 / 2, mg = rollMargin(x1);
        //table a rouleaux (dessus des rouleaux = dessous de la bande)
        for (float z = 4; z < z1 - 3; z += 5) {
            m.spin('x', 0, 7, z, STRIP_SPEED / 1.8F * DEG);
            m.cyl('x', 7, z, 1.8F, mg, x1 - mg, BigMat.ROLL, 8, null, 3);
        }
        m.fixed();
        for (float xa : new float[]{Math.max(0.5F, mg - 3), x1 - mg}) {
            m.box(xa, 0, 1, xa + Math.min(3, mg), 6.5F, z1 - 1, BigMat.CAST, null, 4);                       //longerons
        }
        //rampes d'arrosage entre les cages
        for (int i = 0; i + 1 < stands.length; i++) {
            float zc = stands[i] + 32;
            if (zc > z1 - 4) {
                continue;
            }
            m.cyl('x', 20, zc, 1, mg + 1, x1 - mg - 1, BigMat.PIPE, 8, null, 3);
            boolean any = false;
            for (int xn = (int) (mg + 6); xn < (int) (x1 - mg - 5); xn += 5) {
                m.box(xn - 0.4F, 16.5F, zc - 0.4F, xn + 0.4F, 19.2F, zc + 0.4F, BigMat.PIPE, null, 2);
                any = true;
            }
            if (!any) {
                m.box(cx - 0.4F, 16.5F, zc - 0.4F, cx + 0.4F, 19.2F, zc + 0.4F, BigMat.PIPE, null, 2);
            }
            for (float xa : new float[]{mg, x1 - mg - 2}) {
                m.box(xa, 0, zc - 1, xa + 2, 21, zc + 1, BigMat.CAST, null, 3);
            }
        }
        //cages quarto
        float motor = Math.min(3.5F, x1 / 8 + 0.5F);
        for (int zi : stands) {
            float zc = zi;
            for (float[] side : new float[][]{{1, mg}, {x1 - mg, x1 - 1}}) {
                float xa = side[0], xb = side[1], xm = (xa + xb) / 2, bolt = Math.min(0.8F, (xb - xa) / 3);
                m.box(xa, 0, zc - 7, xb, 30, zc - 3.5F, BigMat.CAST, null, 4);                              //montants
                m.box(xa, 0, zc + 3.5F, xb, 30, zc + 7, BigMat.CAST, null, 4);
                m.box(xa + 0.5F, 0, zc - 3.5F, xb - 0.5F, 2.5F, zc + 3.5F, BigMat.CAST_L, null, 4);          //traverse basse
                m.box(xa + Math.min(1, (xb - xa) / 4), 2.5F, zc - 3.4F, xb - Math.min(1, (xb - xa) / 4), 27, zc + 3.4F, BigMat.CAST_L, null, 4);  //empoises
                for (float yb : new float[]{4, 25}) {                                                     //boulons des montants
                    m.box(xm - bolt, yb - 0.8F, zc - 7.4F, xm + bolt, yb + 0.8F, zc - 7, BigMat.ROLL, null, 2);
                    m.box(xm - bolt, yb - 0.8F, zc + 7, xm + bolt, yb + 0.8F, zc + 7.4F, BigMat.ROLL, null, 2);
                }
            }
            m.box(1, 28, zc - 7, x1 - 1, 32, zc + 7, BigMat.CAST, null, 4);                                 //chapeau
            m.box(cx - motor, 32, zc - motor, cx + motor, 36.5F, zc + motor, BigMat.MOTOR, null, 4);       //moteur de serrage
            m.box(cx - 1.2F, 36.5F, zc - 1.2F, cx + 1.2F, 38, zc + 1.2F, BigMat.ROLL, null, 2);
            //cylindres : travail (bas, haut) et appui (bas, haut) ; sens : la bande avance vers +z
            float[][] rolls = {{5.9F, 2.8F, 1}, {-0.4F, 3.6F, -1}, {12.6F, 2.8F, -1}, {19.0F, 3.6F, 1}};
            for (int k = 0; k < rolls.length; k++) {
                float yc = rolls[k][0], r = rolls[k][1], sgn = rolls[k][2];
                m.spin('x', 0, yc, zc, sgn * STRIP_SPEED / r * DEG);
                m.cyl('x', yc, zc, r, mg, x1 - mg, r < 3.2F ? BigMat.ROLL : BigMat.DRUM, 14, null, 4);
                if (k == 0 || k == 2) {
                    m.cyl('x', yc, zc, 1.2F, -0.5F, mg, BigMat.ROLL, 8, null, 3);                            //allonge vers l'entrainement
                }
            }
            m.fixed();
        }
    }

    /** Bande : cellules occupees (entree a z = 0), chaude puis refroidie et amincie le long de la ligne ; lueur a chaud. */
    private static void rollingStrip(BigMesh m, float x1, float z1, Anim a) {
        boolean[] cells = a.strip;
        float half = Math.max(1.5F, (x1 - 2 * rollMargin(x1)) / 2 - 5);
        int i = 0;
        while (i < cells.length) {
            if (!cells[i]) {
                i++;
                continue;
            }
            int j = i;
            while (j + 1 < cells.length && cells[j + 1]) {
                j++;
            }
            float za = Math.max(2, i * 4 + a.shift), zb = Math.min(z1 - 2, (j + 1) * 4 + a.shift);
            //morceaux de 16 px au plus : degrade de couleur et d'epaisseur
            for (float z = za; z < zb - 0.01F; z += 16) {
                float e = Math.min(zb, z + 16);
                float t = Math.min(1, Math.max(0, ((z + e) / 2) / z1));
                int r = (int) (224 + (143 - 224) * t), g = (int) (84 + (137 - 84) * t), b = (int) (51 + (132 - 51) * t);
                m.color(0xFF000000 | r << 16 | g << 8 | b).glow(Math.max(0, Math.round(13 * (1 - t * 1.7F))));
                float th = 1.6F - 0.9F * t;
                m.box(x1 / 2 - half, 8.8F, z, x1 / 2 + half, 8.8F + th, e, BigMat.STRIP, null, 4);
            }
            i = j + 1;
        }
        m.color(0xFFFFFFFF).glow(0);
    }

    /** Avance la bande de {@code dist} px ; la cellule d'entree se remplit tant que la machine travaille (debut et fin progressifs). */
    public static void feedStrip(Anim a, float l, float dist, boolean feeding) {
        int n = Math.max(1, (int) Math.ceil(l / 4));
        if (a.strip.length != n) {
            a.strip = new boolean[n];
            a.shift = 0;
        }
        a.shift += dist;
        while (a.shift >= 4) {
            a.shift -= 4;
            System.arraycopy(a.strip, 0, a.strip, 1, n - 1);
            a.strip[0] = feeding;
        }
    }

    // ------------------------------------------------------------------ photobioreacteur : serpentins, potences, panneau LED

    private static float[] rows(float y1) {
        int n = Math.max(1, (int) ((y1 - 10) / 11));
        float[] r = new float[n];
        for (int i = 0; i < n; i++) {
            r[i] = 9 + i * 11;
        }
        return r;
    }

    /**
     * 0.20.31 (Guillaume : « l'interieur du photobioreacteur depasse ») : hauteur libre (px) au-dessus de la colonne {@code x} (repere de la
     * vue, x en travers) sous la voute en gradins de la halle : rangees dont la largeur couvre {@code x} a {@code margin} px pres.
     */
    static float headroom(int[] prof, float x1, float y1, float x, float margin) {
        if (prof.length == 0) {
            return y1;
        }
        float y = 0, off = Math.abs(x - x1 / 2) + margin;
        for (int p : prof) {
            if (off > (p / 10) * 8F) {
                break;
            }
            y += 16;
        }
        return Math.min(y1, y);
    }

    /** Rangees de tubes d'un mur sous sa hauteur libre (au moins une). */
    private static float[] rowsUnder(float y1, float room) {
        float[] all = rows(y1);
        int n = 0;
        while (n < all.length && all[n] + 6 <= room) {
            n++;
        }
        return Arrays.copyOf(all, Math.max(1, n));
    }

    /** Deux murs de tubes : a 17 px des parois dans une grande halle, a 30 % / 70 % dans une petite. */
    private static float[] walls(float x1) {
        return x1 >= 64 ? new float[]{17, x1 - 17} : new float[]{x1 * 0.3F, x1 * 0.7F};
    }

    private static void photobio(BigMesh m, float x1, float y1, float z1, int[] prof) {
        float[] walls = walls(x1);
        float za = 9, zb = z1 - 9;
        float top = 0;
        for (float xw : walls) {
            float room = headroom(prof, x1, y1, xw, 6.5F);
            float[] rows = rowsUnder(y1, room);
            float wtop = rows[rows.length - 1];
            top = Math.max(top, wtop);
            for (int j = 0; j < rows.length; j++) {
                float yr = rows[j];
                m.cyl('z', xw, yr, 3.9F, za, zb, j % 2 == 1 ? BigMat.ALGAE_S : BigMat.ALGAE_D, 12, null, 4);
                m.cyl('z', xw, yr, 4.8F, za, zb, BigMat.GLASS, 14, null, 8, false, 0.3F);
                if (j < rows.length - 1) {
                    //coude en U (serpentin), alterne d'un bout a l'autre
                    float ze = j % 2 == 0 ? zb : za, zs = j % 2 == 0 ? 1 : -1;
                    for (int deg = 0; deg <= 180; deg += 30) {
                        double ang = Math.toRadians(deg);
                        float yy = (float) (yr + 5.5 - 5.5 * Math.cos(ang)), zz = (float) (ze + zs * 5.5 * Math.sin(ang));
                        m.box(xw - 3.6F, yy - 2.6F, zz - 2.6F, xw + 3.6F, yy + 2.6F, zz + 2.6F, BigMat.ALGAE_D, null, 4);
                    }
                }
            }
            //potences porte-tubes en inox
            for (float z = za + 10; z < zb - 6; z += 24) {
                float pole = Math.min(wtop + 5, room - 1);
                m.box(xw - 5.6F, 0, z - 0.8F, xw - 4.8F, pole, z + 0.8F, BigMat.CAST_L, null, 3);
                m.box(xw + 4.8F, 0, z - 0.8F, xw + 5.6F, pole, z + 0.8F, BigMat.CAST_L, null, 3);
                for (float yr : rows) {
                    m.box(xw - 5.6F, yr - 4.9F, z - 0.6F, xw + 5.6F, yr - 4.3F, z + 0.6F, BigMat.CAST_L, null, 3);
                }
            }
        }
        //panneau central (les reglettes LED sont dynamiques) ; degazeur, pompe de recolte
        float xm = (walls[0] + walls[1]) / 2;
        m.box(xm - 2.2F, 0, za + 4, xm + 2.2F, Math.min(top + 6, headroom(prof, x1, y1, xm, 3) - 1), zb - 4, BigMat.CAST, null, 4);
        m.cyl('y', xm, 6, 5.5F, 0, 20, BigMat.CAST_L, 14, null, 4);
        m.cyl('y', xm, 6, 5.6F, 20, 21, BigMat.CAST, 14, null, 4);
        m.box(xm - 3, 0, z1 - 8, xm + 3, 7, z1 - 2, BigMat.MOTOR, null, 4);
    }

    /** LED (eclat = regime) et bulles (plus nombreuses avec le regime) qui suivent le serpentin. */
    private static void photobioLife(BigMesh m, float x1, float y1, float z1, int[] prof, Anim a) {
        float[] walls = walls(x1);
        float[] rows = rowsUnder(y1, Math.max(headroom(prof, x1, y1, walls[0], 6.5F), headroom(prof, x1, y1, walls[1], 6.5F)));
        float za = 9, zb = z1 - 9, xm = (walls[0] + walls[1]) / 2;
        float s = a.speed;
        int lum = (int) (70 + 185 * s);
        m.color(0xFF000000 | lum << 16 | lum << 8 | lum).glow(Math.round(15 * s));
        for (float yr : rows) {
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                m.box(xm + sgn * 2.2F - 0.25F, yr - 1.2F, za + 6, xm + sgn * 2.2F + 0.25F, yr + 1.2F, zb - 6, BigMat.LED, null, 4);
            }
        }
        m.color(0xFFFFFFFF).glow(0);
        for (int wi = 0; wi < walls.length; wi++) {
            float xw = walls[wi];
            float[] wrows = rowsUnder(y1, headroom(prof, x1, y1, xw, 6.5F));
            for (int j = 0; j < wrows.length; j++) {
                float yr = wrows[j];
                for (int k = 0; k < 7; k++) {
                    if (hash(k + 7 * j, wi) > s) {
                        continue;
                    }
                    float f = (float) (((k / 7.0 + a.phase / 360.0 * (j % 2 == 0 ? 1 : -1)) % 1.0 + 1.0) % 1.0);
                    float zz = za + 3 + f * (zb - za - 6);
                    float d = 0.7F + 0.3F * ((k * 37) % 3);
                    m.box(xw - d, yr + 3.9F - 2 * d, zz - d, xw + d, yr + 3.9F, zz + d, BigMat.BUBBLE, null, 2);
                }
            }
        }
    }

    // ------------------------------------------------------------------ concasseur : deux cylindres dentes, tremie, minerai

    private static float crusherR(float x1, float y1) {
        return Math.max(2, Math.min(x1 / 4 - Math.max(0.6F, Math.min(2.5F, x1 / 32)), y1 / 2 - 5));
    }

    /** Echelle des details (dents, paliers) : 1 pour des cylindres de 17,5 px de rayon. */
    private static float crusherK(float r) {
        return Math.max(0.3F, Math.min(1, r / 17.5F));
    }

    private static float crusherZ(float z1) {
        return Math.min(10, z1 * 0.15F);
    }

    private static void crusher(BigMesh m, float x1, float y1, float z1) {
        float cx = x1 / 2, r = crusherR(x1, y1), yc = r + 4, k = crusherK(r);
        float za = crusherZ(z1), zb = z1 - za;
        for (int side = -1; side <= 1; side += 2) {
            float xc = cx + side * (r + 1.8F * k);
            //sens : le dessus des deux cylindres va vers le milieu (pincement)
            int g = m.spin('z', xc, yc, 0, side < 0 ? -4 : 4);
            m.cyl('z', xc, yc, r - 2.4F * k, za, zb, BigMat.DRUM, 20, null, 4);                              //virole
            int ring = 0;
            float step = Math.max(3, 7 * k);
            for (float z = za + 3 * k; z < zb - 2 * k; z += step, ring++) {                                  //couronnes de dents en quinconce
                int nt = 10;
                float off = 180F / nt * (ring % 2);
                for (int i = 0; i < nt; i++) {
                    Matrix4f a = BigMesh.rot('z', xc, yc, 0, off + i * 360F / nt);
                    m.box(xc - 1.9F * k, yc + r - 2.6F * k, z - 1.9F * k, xc + 1.9F * k, yc + r - 0.8F * k, z + 1.9F * k, BigMat.TOOTH, a, 3);
                    m.box(xc - k, yc + r - 0.8F * k, z - k, xc + k, yc + r + 0.4F * k, z + k, BigMat.TOOTH_T, a, 2);
                }
            }
            for (float ze : new float[]{za - 2.5F * k, zb}) {
                m.cyl('z', xc, yc, r - 0.6F * k, ze, ze + 2.5F * k, BigMat.CAST_L, 20, null, 4);               //flasques
            }
            m.cyl('z', xc, yc, Math.max(1, 2.4F * k), 1, z1 - 1, BigMat.ROLL, 10, null, 3);                      //arbre
            //engrenage d'entrainement (bout z = 0, juste avant la flasque)
            float rg = r * 0.62F, gz = za - 2.5F * k;
            m.cyl('z', xc, yc, rg, gz - 2 * k, gz, BigMat.CAST_L, 16, null, 4);
            for (int i = 0; i < 22; i++) {
                m.box(xc - 0.9F * k, yc + rg - 0.6F * k, gz - 2 * k, xc + 0.9F * k, yc + rg + 1.6F * k, gz, BigMat.CAST_L,
                      BigMesh.rot('z', xc, yc, 0, i * 360F / 22), 2);
            }
            m.group(g);
            m.fixed();
            float pw = 5 * k, pd = Math.max(2, 5 * k);
            for (float ze : new float[]{Math.min(1, za * 0.2F), z1 - Math.min(1, za * 0.2F) - pd}) {           //paliers
                m.box(xc - pw, 0, ze, xc + pw, yc - 1 * k, ze + pd, BigMat.CAST, null, 4);
                m.cyl('z', xc, yc, 4.2F * k, ze + 0.5F * k, ze + pd - 0.5F * k, BigMat.CAST, 12, null, 4);
            }
        }
        //1.0.4-beta (lot 26, Guillaume : « les rampes de l'entonnoir flottent ») : tremie = deux parois inclinees ACCROCHEES en haut aux parois
        //de la structure (bord haut de la boite, contre les toles / la charpente) et qui descendent jusqu'a la gorge au-dessus du pincement ;
        //elles courent sur toute la longueur de la boite (les toles des bouts ferment l'entonnoir) ; cornieres d'appui le long du bord haut
        float throatY = yc + r + 3 * k + 1, throat = Math.max(3, r * 0.55F), t = 1.0F, edge = 1.0F;
        for (int side = -1; side <= 1; side += 2) {
            float xt = side < 0 ? edge : x1 - edge, yt = y1 - edge, xb = cx + side * throat, yb = throatY;
            float mx = (xt + xb) / 2, my = (yt + yb) / 2, len = (float) Math.hypot(xt - xb, yt - yb);
            float ang = (float) Math.toDegrees(Math.atan2(yt - yb, xt - xb)) - (side < 0 ? 180 : 0);
            Matrix4f f = BigMesh.rot('z', mx, my, 0, ang);
            m.box(mx - len / 2, my - t / 2, 0.3F, mx + len / 2, my + t / 2, z1 - 0.3F, BigMat.CAST, f, 4);
            //corniere d'appui sur le bord haut de la paroi
            float x0 = side < 0 ? 0.3F : x1 - 2.3F;
            m.box(x0, y1 - 2.3F, 0.3F, x0 + 2, y1 - 0.3F, z1 - 0.3F, BigMat.STEEL_D, null, 2);
            //levre verticale de la gorge (guide le minerai entre les cylindres)
            float xl = xb - (side < 0 ? 0 : t);
            m.box(xl, throatY - 2.5F * k, 0.3F, xl + t, throatY + 0.2F, z1 - 0.3F, BigMat.STEEL_D, null, 2);
        }
        m.box(cx - 7 * k, 0, za, cx + 7 * k, 1.2F, zb, BigMat.RUBBER, null, 4);
    }

    /** Minerai qui tombe de la tremie dans le pincement ; le debit suit le regime. */
    private static void crusherOre(BigMesh m, float x1, float y1, float z1, Anim a, BigMat m1, BigMat m2) {
        float cx = x1 / 2, r = crusherR(x1, y1), yc = r + 4, k = Math.max(0.5F, crusherK(r));
        float za = crusherZ(z1), zb = z1 - za;
        float top = y1 - 2, bottom = yc + 2, span = Math.max(2, top - bottom);
        int count = Math.max(8, (int) ((zb - za) / 2));
        for (int i = 0; i < count; i++) {
            if (hash(i, 3) > a.speed) {
                continue;
            }
            float zz = za + 2 + ((i * 53) % 97) / 97F * (zb - za - 4);
            float yy = top - (float) ((((i * 29) % 31) / 31.0 * span + a.phase * 0.8) % span);
            float xx = cx + ((i * 17) % 11 - 5) * 0.45F * k;
            float s = (0.9F + (i % 3) * 0.5F) * k;
            m.box(xx - s, yy - s, zz - s, xx + s, yy + s, zz + s, i % 2 == 1 ? m1 : m2, null, 3);
        }
    }
}
