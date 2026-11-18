package fr.pioneers.tech.industry.client.big;

import fr.pioneers.logistics.rail.client.CarModels;
import fr.pioneers.logistics.rail.client.RailMesh;
import fr.pioneers.logistics.rail.client.TrackModels;
import fr.pioneers.logistics.rail.net.Piece;
import fr.pioneers.logistics.rail.track.TrackQuality;
import fr.pioneers.logistics.rail.track.TrackShape;
import fr.pioneers.logistics.rail.train.CarType;
import java.util.HashMap;
import java.util.Map;
import org.joml.Matrix4f;

/**
 * 1.9.2 (retours de Guillaume : « structure assez laide et beaucoup trop longue ») : interieur des Ateliers ferroviaires, une halle de
 * depot (gabarit {@code IndustryShape.DEPOT}). Repere canonique : x en travers (largeur w), z le long de la halle, porte en z = 0 (la
 * vue est tournee de 180 degres si la porte est au bout) ; les modules forment le sol (y 0..16) ; au-dessus, la halle libre jusqu'a
 * l'egout ({@code stands[0]}) et au faite ({@code stands[1]}).
 * <ul>
 *     <li>Fixe : sol de beton, voie d'entree (vraies pieces de voie) qui passe sur le parvis et sous la porte, fosse de visite sous la voie,
 *     voie de garage avec deux vrais vehicules, etabli, armoire, roues de rechange, toles, futs, chemins de roulement du pont, lampes.</li>
 *     <li>Anime (cycle de {@link #CYCLE}) : le rideau de la porte s'ouvre, le vehicule fini sort (coupe au bord du parvis), un chassis
 *     nu entre et s'arrete sur la fosse, le rideau se ferme ; le pont roulant apporte deux fois un element de caisse (les vrais maillages
 *     de {@link CarModels} apparaissent par le bas a chaque pose), etincelles de soudure. Un vehicule et un tier differents a chaque cycle.</li>
 * </ul>
 */
final class Lot11 {

    /** Duree d'un cycle (unites de phase : ticks a plein regime ; 36 000 est un multiple). */
    static final float CYCLE = 1200;
    /** Vehicules construits tour a tour. */
    private static final CarType[] BUILT = {CarType.STEAM, CarType.DIESEL, CarType.BOXCAR, CarType.TANK, CarType.ELECTRIC, CarType.PASSENGER,
          CarType.HOPPER, CarType.GAS_LOCO, CarType.NUCLEAR, CarType.FLAT};
    /** Fosse de visite (x) et sa longueur (z), centre de la voie, place de travail (centre du vehicule). */
    private static final float PIT0 = 53, PIT1 = 59, PITZ0 = 16, PITZ1 = 112, TRACK = 56, WORK = 64;
    /** Bord exterieur du parvis (le vehicule qui sort y est coupe) et rideau (dans le plan interieur du mur de facade). */
    private static final float APRON = -32, DOOR0 = 32, DOOR1 = 80, DOOR_TOP = 64;
    /** Pont roulant : dessus des chemins de roulement, magasin des elements de caisse (x, z). */
    private static final float RUNWAY = 62, STOCK_X = 94, STOCK_Z = 122;
    /** Maillages des vehicules (fixes, sans scintillement) et hauteur de leur plus haut point. */
    private static final Map<Integer, Car> CARS = new HashMap<>();

    private record Car(BigMesh mesh, float[] top, float height) {
    }

    private Lot11() {
    }

    private static Car car(CarType type, int tier) {
        return CARS.computeIfAbsent(type.ordinal() * 8 + tier, k -> {
            BigMesh m = RailMesh.deconflict(CarModels.build(type, tier, 0.6F, 0x6A6A6A).mesh);
            float[] top = new float[m.quads.size()];
            float h = 0;
            for (int i = 0; i < top.length; i++) {
                BigMesh.Quad q = m.quads.get(i);
                top[i] = Math.max(Math.max(q.pos[1], q.pos[4]), Math.max(q.pos[7], q.pos[10]));
                h = Math.max(h, top[i]);
            }
            return new Car(m, top, h);
        });
    }

    private static float smooth(float u) {
        u = Math.max(0, Math.min(1, u));
        return u * u * (3 - 2 * u);
    }

    private static float span(float u, float a, float b) {
        return Math.max(0, Math.min(1, (u - a) / (b - a)));
    }

    // ------------------------------------------------------------------ fixe

    static void depot(BigMesh m, float w, float h, float l, int[] stands) {
        int start = m.quads.size();
        float eave = stands.length > 0 ? stands[0] : 96, ridge = stands.length > 1 ? stands[1] : 128;
        //Sol de beton autour de la fosse, fosse (fond, parois, lampes, marches)
        m.box(0, 0, 0, PIT0, h, l, BigMat.R_SLAB, null, 16);
        m.box(PIT1, 0, 0, w, h, l, BigMat.R_SLAB, null, 16);
        m.box(PIT0, 0, 0, PIT1, h, PITZ0, BigMat.R_SLAB, null, 16);
        m.box(PIT0, 0, PITZ1, PIT1, h, l, BigMat.R_SLAB, null, 16);
        m.box(PIT0, 0, PITZ0, PIT1, 2, PITZ1, BigMat.R_CONCRETE, null, 8);
        for (float z = PITZ0 + 12; z < PITZ1 - 4; z += 16) {
            m.glow(14);
            m.box(PIT0 - 0.01F, 8, z - 1, PIT0 + 0.4F, 9.2F, z + 1, BigMat.R_LAMP, null, 2);
            m.box(PIT1 - 0.4F, 8, z - 1, PIT1 + 0.01F, 9.2F, z + 1, BigMat.R_LAMP, null, 2);
            m.glow(0);
        }
        for (int k = 0; k < 3; k++) {
            float y = 2 + k * 4.5F;
            m.box(PIT0 + 0.4F, 2, PITZ1 - 3 - k * 3, PIT1 - 0.4F, y + 4.5F, PITZ1 - k * 3, BigMat.R_CONCRETE, null, 8);
        }
        //Bandes de securite le long de la fosse et des voies
        m.box(PIT0 - 3.2F, h, PITZ0, PIT0 - 1.2F, h + 0.15F, PITZ1, BigMat.R_HAZARD, null, 4);
        m.box(PIT1 + 1.2F, h, PITZ0, PIT1 + 3.2F, h + 0.15F, PITZ1, BigMat.R_HAZARD, null, 4);
        //Voie d'entree : vraies pieces de voie sur le parvis, sous la porte et dans la halle ; sur la fosse, rails sur les levres
        RailMesh straight = TrackModels.build(TrackQuality.ADVANCED, Piece.Kind.TRACK, TrackShape.STRAIGHT_NS, 0, false);
        RailMesh buffer = TrackModels.build(TrackQuality.ADVANCED, Piece.Kind.BUFFER, TrackShape.STRAIGHT_NS, 0, false);
        for (float z = APRON; z < l; z += 16) {
            if (z >= PITZ0 && z < PITZ1) {
                continue;
            }
            RailMesh.append(m, (z + 16 >= l ? buffer : straight).mesh, new Matrix4f().translate(TRACK - 8, h, z));
        }
        for (float x : new float[]{TRACK - TrackModels.GAUGE, TRACK + TrackModels.GAUGE}) {
            m.box(x - 0.5F, h, PITZ0, x + 0.5F, h + TrackModels.RT - 0.8F, PITZ1, BigMat.R_RAIL_FOOT, null, 4);
            m.box(x - 0.7F, h + TrackModels.RT - 0.8F, PITZ0, x + 0.7F, h + TrackModels.RT, PITZ1, BigMat.R_RAIL, null, 4);
        }
        //Voie de garage (a gauche) : deux vehicules finis
        for (float z = 16; z < l - 16; z += 16) {
            RailMesh.append(m, (z == 16 || z + 16 >= l - 16 ? buffer : straight).mesh, z == 16
                  ? new Matrix4f().translate(20 + 8, h, z + 16).rotateY((float) Math.PI) : new Matrix4f().translate(20 - 8, h, z));
        }
        Matrix4f park = new Matrix4f().translate(20 - 8, h, 58);
        for (BigMesh.Quad q : car(CarType.DIESEL, 1).mesh.quads) {
            m.copy(q, park);
        }
        park = new Matrix4f().translate(20 - 8, h, 75);
        for (BigMesh.Quad q : car(CarType.BOXCAR, 0).mesh.quads) {
            m.copy(q, park);
        }
        //Etabli, etau, armoire a outils, roues de rechange, toles, futs, bouteilles de soudure
        m.box(w - 12, h + 10, 22, w - 0.5F, h + 11.4F, 50, BigMat.R_OAK, null, 8);
        for (float[] p : new float[][]{{w - 11.5F, 23}, {w - 1.5F, 23}, {w - 11.5F, 48}, {w - 1.5F, 48}}) {
            m.box(p[0], h, p[1], p[0] + 1, h + 10, p[1] + 1, BigMat.R_DARK, null, 4);
        }
        m.box(w - 11.4F, h + 2, 23.2F, w - 0.6F, h + 2.6F, 48.8F, BigMat.R_DARK, null, 8);
        m.box(w - 11, h + 11.4F, 26, w - 8, h + 14, 29, BigMat.R_STEEL, null, 4);
        m.box(w - 10, h + 11.4F, 38, w - 6, h + 12.4F, 46, BigMat.R_STEEL_L, null, 4);
        m.box(w - 8, h, 4, w - 0.5F, h + 26, 16, BigMat.R_RED, null, 8);
        for (float y = 4; y < 26; y += 5.5F) {
            m.box(w - 8.25F, h + y, 5, w - 8, h + y + 0.5F, 15, BigMat.R_DARK, null, 2);
        }
        for (float z = 66; z <= 90; z += 8) {
            m.box(84, h, z - 1.5F, 100, h + 1, z + 1.5F, BigMat.R_DARK, null, 4);
            m.cyl('x', h + 3.4F, z, 2.4F, 85, 86.2F, BigMat.R_WHEEL, 14, null, 4);
            m.cyl('x', h + 3.4F, z, 2.4F, 97.8F, 99, BigMat.R_WHEEL, 14, null, 4);
            m.cyl('x', h + 3.4F, z, 0.45F, 86.2F, 97.8F, BigMat.R_STEEL, 6, null, 4);
        }
        for (int k = 0; k < 5; k++) {
            float o = (k % 2) * 0.8F;
            m.box(STOCK_X - 8 + o, h + k * 1.2F, STOCK_Z - 10 + o, STOCK_X + 8 + o, h + k * 1.2F + 1.1F, STOCK_Z + 8 + o, BigMat.R_STEEL, null, 8);
        }
        for (float z : new float[]{100, 105, 110}) {
            m.cyl('y', w - 4, z, 2.2F, h, h + 7, z == 105 ? BigMat.R_DARK : BigMat.R_RED, 10, null, 4);
        }
        for (float x : new float[]{69, 71.6F}) {
            m.cyl('y', x, 40, 1.1F, h + 1, h + 11, BigMat.R_RED, 8, null, 4);
            m.cyl('y', x, 40, 0.5F, h + 11, h + 12.2F, BigMat.R_CHROME, 6, null, 2);
        }
        m.box(67.5F, h, 38, 73.2F, h + 1, 42, BigMat.R_DARK, null, 4);
        //Chemins de roulement du pont (sur consoles), le long des deux murs
        for (float x0 : new float[]{0, w - 3}) {
            m.box(x0, RUNWAY - 6, 0, x0 + 3, RUNWAY - 0.8F, l, BigMat.R_FRAME, null, 8);
            m.box(x0 + 0.9F, RUNWAY - 0.8F, 0, x0 + 2.1F, RUNWAY, l, BigMat.R_RAIL, null, 4);
            for (float z = 8; z < l; z += 32) {
                float in = x0 == 0 ? 0 : w - 2;
                m.box(in, RUNWAY - 12, z - 1, in + 2, RUNWAY - 6, z + 1, BigMat.R_DARK, null, 4);
            }
        }
        //Lampes suspendues sous le lanterneau
        for (float x : new float[]{40, 72}) {
            for (float z : new float[]{36, 92}) {
                float top = Math.min(ridge - 18, eave + 20);
                m.box(x - 0.2F, top - 14, z - 0.2F, x + 0.2F, top, z + 0.2F, BigMat.R_DARK, null, 2);
                m.box(x - 3, top - 16, z - 3, x + 3, top - 14, z + 3, BigMat.R_DARK, null, 4);
                m.glow(15);
                m.box(x - 2.2F, top - 16.6F, z - 2.2F, x + 2.2F, top - 16, z + 2.2F, BigMat.R_LAMP, null, 2);
                m.glow(0);
            }
        }
        RailMesh.deconflict(m);
        if (stands.length > 2 && stands[2] == 1) {
            turn(m, start, w, l);
        }
    }

    /** Porte au bout de l'axe : demi-tour autour du milieu de la boite (meme sens des faces). */
    private static void turn(BigMesh m, int from, float w, float l) {
        for (int i = from; i < m.quads.size(); i++) {
            BigMesh.Quad q = m.quads.get(i);
            for (int k = 0; k < 4; k++) {
                q.pos[k * 3] = w - q.pos[k * 3];
                q.pos[k * 3 + 2] = l - q.pos[k * 3 + 2];
            }
            q.nx = -q.nx;
            q.nz = -q.nz;
        }
    }

    // ------------------------------------------------------------------ anime

    static void depotLife(BigMesh m, float w, float h, float l, int[] stands, BigMachines.Anim a) {
        int start = m.quads.size();
        float cycle = (float) Math.floor(a.phase / CYCLE), u = (a.phase - cycle * CYCLE) / CYCLE;
        int n = (int) cycle;
        //Rideau : ouvert pendant la sortie et l'entree
        float open = smooth(span(u, 0, 0.06F)) * (1 - smooth(span(u, 0.34F, 0.40F)));
        shutter(m, h, open);
        //Vehicule : le precedent (fini) sort, puis le nouveau chassis entre et se construit
        boolean leaving = u < 0.20F;
        int k = leaving ? n - 1 : n;
        CarType type = BUILT[Math.floorMod(k, BUILT.length)];
        int tier = Math.floorMod(k / BUILT.length + k, 4);
        Car car = car(type, tier);
        float z, reveal;
        float chassis = TrackModels.RT + 6.6F;
        float t1 = 0.40F + 0.27F * 0.60F, t2 = 0.67F + 0.27F * 0.60F;
        if (leaving) {
            z = WORK - (WORK - APRON + 10) * smooth(span(u, 0.06F, 0.20F));
            reveal = 999;
        } else {
            z = APRON - 10 + (WORK - APRON + 10) * smooth(span(u, 0.20F, 0.34F));
            float grow = car.height - chassis;
            reveal = chassis + grow * (0.12F * span(u, 0.40F, t1) + (u >= t1 ? 0.38F : 0) + 0.12F * span(u, t1, t2) + (u >= t2 ? 0.38F : 0));
        }
        Matrix4f xf = new Matrix4f().translate(TRACK, h, z).rotateY((float) Math.PI).translate(-8, 0, -8);
        for (int i = 0; i < car.top.length; i++) {
            if (car.top[i] > reveal + 0.01F) {
                continue;
            }
            BigMesh.Quad q = car.mesh.quads.get(i);
            float zmax = Math.max(Math.max(q.pos[2], q.pos[5]), Math.max(q.pos[8], q.pos[11]));
            //Coupe au bord du parvis (vehicule tourne : z dans la halle = z + 8 - z du modele)
            if (z + 8 - zmax < APRON) {
                continue;
            }
            m.copy(q, xf);
        }
        //Pont roulant : deux voyages pendant la construction (magasin -> vehicule, pose, retour)
        crane(m, w, h, u, car, leaving);
        //Etincelles de soudure apres chaque pose
        if (!leaving && u > 0.40F && u < 0.94F && a.speed > 0.2F) {
            sparks(m, h, reveal, a.phase);
        }
        if (stands.length > 2 && stands[2] == 1) {
            turn(m, start, w, l);
        }
    }

    /** Rideau metallique de la porte (dans le plan interieur du mur), tambour au-dessus. */
    private static void shutter(BigMesh m, float h, float open) {
        float bottom = h + (DOOR_TOP - h) * open;
        if (bottom < DOOR_TOP - 1) {
            //Barre basse (bandes de securite) puis lames jusqu'au linteau
            m.box(DOOR0 + 0.05F, bottom, -1.5F, DOOR1 - 0.05F, bottom + 0.8F, 0.1F, BigMat.R_HAZARD, null, 4);
            for (float y = bottom + 0.8F; y < DOOR_TOP - 0.01F; y += 2) {
                m.box(DOOR0 + 0.05F, y, -1.2F, DOOR1 - 0.05F, Math.min(DOOR_TOP, y + 2), -0.2F, (int) ((y - bottom) / 2) % 2 == 0
                      ? BigMat.R_STEEL_L : BigMat.R_STEEL, null, 4);
            }
        }
        m.cyl('x', DOOR_TOP + 3, 3.5F, 3, DOOR0 + 1, DOOR1 - 1, BigMat.R_STEEL, 12, null, 4);
        m.box(DOOR0 + 0.5F, DOOR_TOP + 2, 0.5F, DOOR0 + 1, DOOR_TOP + 4, 6.5F, BigMat.R_DARK, null, 2);
        m.box(DOOR1 - 1, DOOR_TOP + 2, 0.5F, DOOR1 - 0.5F, DOOR_TOP + 4, 6.5F, BigMat.R_DARK, null, 2);
    }

    /** Pont roulant : poutre sur les chemins de roulement, chariot, cable, crochet et element de caisse. */
    private static void crane(BigMesh m, float w, float h, float u, Car car, boolean leaving) {
        float bz = STOCK_Z, tx = STOCK_X, hook = RUNWAY - 6;
        boolean load = false;
        float vtop = h + car.height + 1;
        if (!leaving && u >= 0.40F && u < 0.94F) {
            float t = ((u - 0.40F) % 0.27F) / 0.27F;
            float stock = h + 6.5F;
            if (t < 0.10F) {
                hook = RUNWAY - 6 - (RUNWAY - 6 - stock - 6) * smooth(t / 0.10F);
            } else if (t < 0.18F) {
                load = true;
                hook = stock + 6 + (RUNWAY - 12 - stock) * smooth((t - 0.10F) / 0.08F);
            } else if (t < 0.45F) {
                load = true;
                float s = smooth((t - 0.18F) / 0.27F);
                bz = STOCK_Z + (WORK - STOCK_Z) * s;
                tx = STOCK_X + (TRACK - STOCK_X) * s;
                hook = RUNWAY - 6;
            } else if (t < 0.60F) {
                load = true;
                bz = WORK;
                tx = TRACK;
                hook = RUNWAY - 6 - (RUNWAY - 6 - vtop - 6) * smooth((t - 0.45F) / 0.15F);
            } else if (t < 0.70F) {
                bz = WORK;
                tx = TRACK;
                hook = vtop + 6 + (RUNWAY - 12 - vtop) * smooth((t - 0.60F) / 0.10F);
            } else {
                float s = smooth((t - 0.70F) / 0.30F);
                bz = WORK + (STOCK_Z - WORK) * s;
                tx = TRACK + (STOCK_X - TRACK) * s;
                hook = RUNWAY - 6;
            }
        }
        //Poutre et sommiers (roulent sur les rails des chemins de roulement)
        m.box(3, RUNWAY + 1, bz - 3, w - 3, RUNWAY + 7, bz + 3, BigMat.PAINT_O, null, 8);
        for (float x0 : new float[]{0, w - 6}) {
            m.box(x0, RUNWAY + 0.5F, bz - 7, x0 + 6, RUNWAY + 5, bz + 7, BigMat.PAINT_O, null, 8);
            for (float dz : new float[]{-5, 5}) {
                m.cyl('x', RUNWAY + 1.2F, bz + dz, 1.2F, x0 + 1, x0 + 2.2F, BigMat.R_DARK, 8, null, 2);
            }
        }
        m.box(3, RUNWAY + 7, bz - 0.4F, w - 3, RUNWAY + 7.4F, bz + 0.4F, BigMat.R_HAZARD, null, 4);
        //Chariot, treuil, cable, crochet
        m.box(tx - 5, RUNWAY + 7.4F, bz - 4, tx + 5, RUNWAY + 11, bz + 4, BigMat.R_DARK, null, 8);
        m.cyl('z', tx, RUNWAY + 9.2F, 1.5F, bz - 5.5F, bz + 5.5F, BigMat.R_STEEL, 10, null, 4);
        m.box(tx - 0.3F, hook + 3, bz - 0.3F, tx + 0.3F, RUNWAY + 1, bz + 0.3F, BigMat.R_DARK, null, 2);
        m.box(tx - 1.6F, hook, bz - 1.6F, tx + 1.6F, hook + 3, bz + 1.6F, BigMat.PAINT_O, null, 4);
        if (load) {
            //Element de caisse a la livree du vehicule, elingues
            float y1 = hook - 1.5F, y0 = y1 - 5;
            m.box(tx - 5.5F, y0, bz - 6.5F, tx + 5.5F, y1, bz + 6.5F, liveryOf(car), null, 8);
            for (float[] d : new float[][]{{-5, -6}, {5, -6}, {-5, 6}, {5, 6}}) {
                m.box(tx + d[0] * 0.5F - 0.15F, y1, bz + d[1] * 0.5F - 0.15F, tx + d[0] * 0.5F + 0.15F, hook, bz + d[1] * 0.5F + 0.15F,
                      BigMat.R_DARK, null, 2);
            }
        }
        //Gyrophare du chariot (allume quand le pont roule)
        m.glow(load ? 15 : 0);
        m.box(tx + 3, RUNWAY + 11, bz - 1, tx + 4.4F, RUNWAY + 12.4F, bz + 0.4F, load ? BigMat.R_LAMP_O : BigMat.R_LAMP_OFF, null, 2);
        m.glow(0);
    }

    /** Matiere de l'element de caisse : la plus frequente du vehicule au-dessus du chassis (sa livree). */
    private static BigMat liveryOf(Car car) {
        Map<BigMat, Integer> count = new HashMap<>();
        BigMat best = BigMat.R_LIVERY1;
        int most = 0;
        for (int i = 0; i < car.top.length; i++) {
            BigMat mat = car.mesh.quads.get(i).mat;
            if (car.top[i] > car.height * 0.5F && mat.name().startsWith("R_LIVERY")) {
                int c = count.merge(mat, 1, Integer::sum);
                if (c > most) {
                    most = c;
                    best = mat;
                }
            }
        }
        return best;
    }

    /** Etincelles de soudure sur le vehicule, a la hauteur ou il se construit. */
    private static void sparks(BigMesh m, float h, float reveal, float phase) {
        int f = (int) (phase * 0.5F);
        m.glow(15);
        for (int i = 0; i < 6; i++) {
            int r = (f * 31 + i * 977) * 1103515245 + 12345;
            float side = (r >> 8 & 1) == 0 ? -1 : 1;
            float x = TRACK + side * (5.5F + (r >> 9 & 3) * 0.6F), y = h + Math.max(4, reveal - 1 - (r >> 11 & 3)), z = WORK - 6 + (r >> 13 & 15) * 0.8F;
            float s = 0.25F + (r >> 17 & 3) * 0.1F;
            m.box(x - s, y - s, z - s, x + s, y + s, z + s, (r >> 19 & 1) == 0 ? BigMat.R_LAMP_O : BigMat.R_WHITE, null, 1);
        }
        m.glow(0);
    }
}
