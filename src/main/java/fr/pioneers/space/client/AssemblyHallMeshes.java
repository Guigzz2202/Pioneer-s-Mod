package fr.pioneers.space.client;

import fr.pioneers.tech.industry.client.big.BigMat;
import fr.pioneers.tech.industry.client.big.BigMesh;
import fr.pioneers.logistics.robotics.UnitKind;
import fr.pioneers.space.assembly.AssemblyHall;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 1.9.2 (retours de Guillaume) : machinerie animee des multiblocs d'assemblage, d'un seul tenant (comme les machines interieures
 * {@code client/industry/big}), dessinee par {@link RenderAssemblyHall} et par le banc {@code tools/bigprev/AssemblyPreview}.
 * <p>
 * Repere : px, origine au centre du sol interieur (dessus de la dalle), x en travers, y vers le haut, z vers le fond (+z = loin de la
 * facade). L'interieur fait {@link AssemblyHall.Kind#innerWidth()} x {@link AssemblyHall.Kind#innerHeight()} x
 * {@link AssemblyHall.Kind#innerDepth()}.
 * <ul>
 *     <li>Baie robotique : convoyeur qui amene les pieces du fond, borne de recharge (bloc) ou l'unite se monte, quatre bras articules
 *     sur socles (les deux du fond prennent les pieces au bout du convoyeur), portique de controle qui balaie l'unite au laser, etageres
 *     de pieces, pupitre ;</li>
 *     <li>Hall des fusees : fusee debout sur les dalles de lancement (bloc), tour de service rouge en treillis avec ascenseur et bras
 *     d'acces qui se deploient vers la partie deja montee, plate-forme de travail en deux moities qui monte avec l'assemblage (soudure
 *     tout autour), deux ponts roulants : l'un apporte les troncons de fuselage depuis l'aire de depot.</li>
 * </ul>
 */
public final class AssemblyHallMeshes {

    private AssemblyHallMeshes() {
    }

    /** Etat d'une image : phase (unites/tick, integree au regime), regime 0..1, avancement 0..1, assemblage en cours / produit pret. */
    public static final class State {

        public float phase, speed, progress;
        public boolean building, finished, product;
        /** Gabarit du produit (px) : hauteur et rayon. */
        public float productHeight, productRadius = 12;
    }

    // ------------------------------------------------------------------ produit

    /** Hall : hauteur maximale d'une fusee dans le hall (px) : la plus grande y est reduite, les petites gardent l'echelle 1. */
    public static final float ROCKET_MAX = 168;

    /** Echelle de la fusee de {@code heightBlocks} de haut. */
    public static float rocketScale(int heightBlocks) {
        return Math.min(1, ROCKET_MAX / (heightBlocks * 16F));
    }

    /** Baie : centre (px) de la borne de recharge, ou se monte l'unite, et hauteur de son plateau. */
    public static final float PAD_Z = -16, PAD_TOP = 3;

    /** Hauteur d'une unite robotique (px) : memes valeurs que son objet ({@code RobotUnitItemRenderer}). */
    public static float unitHeight(UnitKind kind) {
        return switch (kind) {
            case DRONE -> 9;
            case ROBOT -> 26;
            case ROVER -> 18;
            case TURTLE -> 11;
        };
    }

    /** Rayon d'une unite robotique (px), pour placer bras et anneau de balayage. */
    public static float unitRadius(UnitKind kind) {
        return switch (kind) {
            case DRONE -> 10;
            case ROBOT -> 7;
            case ROVER -> 14;
            case TURTLE -> 10;
        };
    }

    /** Hauteur de coupe (px, repere du maillage) : jusqu'ou le produit est monte. */
    public static float cut(AssemblyHall.Kind kind, State s) {
        float base = kind == AssemblyHall.Kind.ROBOTICS ? PAD_TOP : 0;
        if (s.finished) {
            return base + s.productHeight + 64;
        }
        return base + Math.max(0.04F, s.progress) * s.productHeight;
    }

    // ------------------------------------------------------------------ outils

    private static float clamp01(float v) {
        return Math.max(0, Math.min(1, v));
    }

    private static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3 - 2 * t);
    }

    private static float hash(int a, int b) {
        int x = a * 73856093 ^ b * 19349663;
        x ^= x >>> 13;
        x *= 0x5bd1e995;
        x ^= x >>> 15;
        return (x & 0xFFFF) / 65535F;
    }

    private static void box(BigMesh m, float x0, float y0, float z0, float x1, float y1, float z1, BigMat mat) {
        m.box(Math.min(x0, x1), Math.min(y0, y1), Math.min(z0, z1), Math.max(x0, x1), Math.max(y0, y1), Math.max(z0, z1), mat, null, 16);
    }

    /** Poutre de section t x t de p0 a p1. */
    private static void beam(BigMesh m, float x0, float y0, float z0, float x1, float y1, float z1, float t, BigMat mat) {
        float dx = x1 - x0, dy = y1 - y0, dz = z1 - z0;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 0.01F) {
            return;
        }
        Quaternionf q = new Quaternionf().rotationTo(0, 1, 0, dx / len, dy / len, dz / len);
        Matrix4f xf = new Matrix4f().translate(x0, y0, z0).rotate(q);
        m.box(-t / 2, 0, -t / 2, t / 2, len, t / 2, mat, xf, 16);
    }

    private static void lamp(BigMesh m, float x0, float y0, float z0, float x1, float y1, float z1, BigMat mat) {
        m.glow(15);
        box(m, x0, y0, z0, x1, y1, z1, mat);
        m.glow(0);
    }

    /**
     * Bras articule pose au sol : embase en (bx, by, bz), outil en (tx, ty, tz) ; segments a1 et a2 (px). Cinematique a deux segments,
     * coude vers le haut ; pince au bout, piece portee si {@code part} non nul.
     */
    private static void arm(BigMesh m, float bx, float by, float bz, float tx, float ty, float tz, float a1, float a2, BigMat paint,
          @Nullable BigMat part) {
        float yaw = (float) Math.toDegrees(Math.atan2(tx - bx, tz - bz));
        Matrix4f turret = BigMesh.rot('y', bx, 0, bz, yaw);
        m.box(bx - 3.5F, by - 2, bz - 3.5F, bx + 3.5F, by, bz + 3.5F, BigMat.STEEL_D, turret, 16);                //couronne
        m.box(bx - 2.5F, by, bz - 2.5F, bx + 2.5F, by + 4, bz + 2.5F, paint, turret, 16);                         //tourelle
        Vector3f sh = new Vector3f(bx, by + 4, bz), wr = new Vector3f(tx, ty + 3, tz);
        Vector3f d = new Vector3f(wr).sub(sh);
        float len = Math.max(2, Math.min(d.length(), a1 + a2 - 0.3F));
        Vector3f u = d.lengthSquared() < 1e-4 ? new Vector3f(0, 1, 0) : new Vector3f(d).normalize();
        wr.set(sh).add(new Vector3f(u).mul(len));
        float x = (len * len + a1 * a1 - a2 * a2) / (2 * len), hh = (float) Math.sqrt(Math.max(0, a1 * a1 - x * x));
        Vector3f up = new Vector3f(0, 1, 0);
        Vector3f nn = new Vector3f(up).sub(new Vector3f(u).mul(up.dot(u)));
        if (nn.lengthSquared() < 1e-4) {
            nn.set((float) Math.sin(Math.toRadians(yaw)), 0, (float) Math.cos(Math.toRadians(yaw)));
        }
        nn.normalize();
        Vector3f el = new Vector3f(sh).add(new Vector3f(u).mul(x)).add(nn.mul(hh));
        beam(m, sh.x, sh.y, sh.z, el.x, el.y, el.z, 3, paint);                                                   //bras
        beam(m, el.x, el.y, el.z, wr.x, wr.y, wr.z, 2.4F, paint);                                                //avant-bras
        box(m, el.x - 1.8F, el.y - 1.8F, el.z - 1.8F, el.x + 1.8F, el.y + 1.8F, el.z + 1.8F, BigMat.STEEL_D);   //coude
        box(m, sh.x - 2, sh.y - 2, sh.z - 2, sh.x + 2, sh.y + 2, sh.z + 2, BigMat.STEEL_D);                     //epaule
        beam(m, wr.x, wr.y, wr.z, tx, ty, tz, 1.4F, BigMat.CHROME);                                              //poignet
        box(m, tx - 1.6F, ty - 0.5F, tz - 1.6F, tx + 1.6F, ty + 0.5F, tz + 1.6F, BigMat.STEEL_D);              //pince
        if (part != null) {
            box(m, tx - 2, ty - 4, tz - 2, tx + 2, ty - 0.5F, tz + 2, part);
        }
    }

    // ------------------------------------------------------------------ partie fixe

    /** Partie fixe (faite une fois par sorte). */
    public static BigMesh build(AssemblyHall.Kind kind) {
        BigMesh m = new BigMesh();
        m.metric(true);
        if (kind == AssemblyHall.Kind.ROBOTICS) {
            bay(m, kind.innerWidth(), kind.innerHeight(), kind.innerDepth());
        } else {
            hall(m, kind.innerWidth(), kind.innerHeight(), kind.innerDepth());
        }
        return m;
    }

    /** Partie animee de l'image (ajoutee a {@code m}, deja vide). */
    public static void dynamic(BigMesh m, AssemblyHall.Kind kind, State s) {
        m.metric(true);
        if (kind == AssemblyHall.Kind.ROBOTICS) {
            bayLife(m, kind.innerWidth(), kind.innerHeight(), kind.innerDepth(), s);
        } else {
            hallLife(m, kind.innerWidth(), kind.innerHeight(), kind.innerDepth(), s);
        }
    }

    // ================================================================== baie d'assemblage robotique

    /** Socles des quatre bras : x, z (les deux premiers devant la borne, les deux autres pres du bout du convoyeur). */
    private static final float[][] ARMS = {{-21, -30}, {21, -30}, {-21, -2}, {21, -2}};
    private static final float BELT_Y = 10, BELT_END = -4, ARM_BASE = 9;

    private static void bay(BigMesh m, float w, float h, float l) {
        float hw = w / 2, hl = l / 2;
        //Convoyeur : du mur du fond jusqu'a la borne
        for (float x : new float[]{-7, 6}) {
            box(m, x, BELT_Y - 3, BELT_END - 2, x + 1, BELT_Y + 1.5F, hl, BigMat.STEEL_D);                          //longerons
        }
        box(m, -6, BELT_Y - 1, BELT_END, 6, BELT_Y, hl, BigMat.RUBBER);                                              //bande
        box(m, -6, BELT_Y - 3, BELT_END + 1, 6, BELT_Y - 1, hl, BigMat.R_DARK);
        for (float z = BELT_END + 4; z < hl; z += 14) {
            for (float x : new float[]{-6.5F, 5.5F}) {
                box(m, x, 0, z - 0.8F, x + 1, BELT_Y - 3, z + 0.8F, BigMat.STEEL_D);                                //pieds
            }
        }
        m.cyl('x', BELT_Y - 1.5F, BELT_END, 1.6F, -6.5F, 6.5F, BigMat.CHROME, 8, null, 16);                          //rouleau de tete
        box(m, -7, BELT_Y - 2, BELT_END - 3, 7, BELT_Y + 2, BELT_END - 2, BigMat.R_HAZARD);                          //butee
        //Trappe d'arrivee dans le mur du fond
        box(m, -9, BELT_Y - 4, hl - 2, 9, BELT_Y + 12, hl, BigMat.R_DARK);
        box(m, -9, BELT_Y + 12, hl - 3, 9, BELT_Y + 14, hl, BigMat.R_HAZARD);
        //Socles des bras et marquages au sol
        for (float[] a : ARMS) {
            box(m, a[0] - 4, 0, a[1] - 4, a[0] + 4, ARM_BASE - 2, a[1] + 4, BigMat.MOTOR);
            box(m, a[0] - 5, 0, a[1] - 5, a[0] + 5, 1.5F, a[1] + 5, BigMat.STEEL_D);
            box(m, a[0] - 7, 0.02F, a[1] - 7, a[0] + 7, 0.3F, a[1] + 7, BigMat.R_HAZARD);
        }
        //Borne : marquage au sol autour
        box(m, -11, 0.02F, PAD_Z - 11, 11, 0.25F, PAD_Z + 11, BigMat.R_STRIPE);
        box(m, -9, 0.03F, PAD_Z - 9, 9, 0.3F, PAD_Z + 9, BigMat.R_SLAB);
        //Etageres a pieces le long des flancs (fond de la baie)
        for (float sx : new float[]{-1, 1}) {
            float x0 = sx * (hw - 9), x1 = sx * (hw - 0.5F);
            for (float z : new float[]{10, 30, 50}) {
                box(m, x0, 0, z - 0.6F, x0 + sx * 1.2F, h - 18, z + 0.6F, BigMat.STEEL_D);
                box(m, x1 - sx * 1.2F, 0, z - 0.6F, x1, h - 18, z + 0.6F, BigMat.STEEL_D);
            }
            for (float y : new float[]{12, 26, 40}) {
                box(m, x0 + sx * 0.3F, y - 1, 9, x1 - sx * 0.3F, y, 51, BigMat.R_GRILL);
                for (int k = 0; k < 4; k++) {
                    float z = 12 + k * 10, bh = 5 + 3 * hash(k, (int) y);
                    BigMat bin = (k + (int) y / 14) % 3 == 0 ? BigMat.PAINT_O : (k + (int) y / 14) % 3 == 1 ? BigMat.PIPE : BigMat.CAST_L;
                    box(m, x0 + sx * 1.5F, y, z, x1 - sx * 1.5F, y + bh, z + 7, bin);
                }
            }
        }
        //Pupitre de commande a l'avant gauche
        box(m, -hw + 2, 0, -hl + 4, -hw + 16, 10, -hl + 12, BigMat.STEEL_D);
        box(m, -hw + 2, 10, -hl + 6, -hw + 16, 13, -hl + 12, BigMat.R_DARK);
        lamp(m, -hw + 3, 13, -hl + 9, -hw + 15, 20, -hl + 10, BigMat.R_SCREEN);
        //Rails du portique sous le toit
        for (float sx : new float[]{-1, 1}) {
            box(m, sx * (hw - 6), h - 6, -hl, sx * (hw - 2), h - 3, hl, BigMat.STEEL_D);
            box(m, sx * (hw - 5), h - 3, -hl, sx * (hw - 3), h - 2.5F, hl, BigMat.CHROME);
            for (float z = -hl + 8; z < hl; z += 24) {
                box(m, sx * (hw - 2), h - 10, z - 1, sx * hw, h - 3, z + 1, BigMat.STEEL_D);                     //consoles
            }
        }
        //Eclairage : reglettes sous les rails du portique
        for (float sx : new float[]{-1, 1}) {
            for (float z = -hl + 16; z < hl - 8; z += 32) {
                lamp(m, sx * (hw - 5.5F), h - 6.6F, z - 6, sx * (hw - 2.5F), h - 6.1F, z + 6, BigMat.R_LAMP);
            }
        }
    }

    private static void bayLife(BigMesh m, float w, float h, float l, State s) {
        float hw = w / 2, hl = l / 2;
        boolean work = s.building && s.speed > 0.02F;
        float t = s.phase;
        //Pieces sur le convoyeur (avancent pendant le travail)
        if (s.building) {
            float run = hl - BELT_END;
            for (int i = 0; i < 4; i++) {
                float z = hl - ((t * 0.35F + i * run / 4) % run);
                if (z < BELT_END + 2 || z > hl - 3) {
                    continue;
                }
                BigMat mat = i % 3 == 0 ? BigMat.PAINT_O : i % 3 == 1 ? BigMat.PCB : BigMat.CAST_L;
                box(m, -3, BELT_Y, z - 3, 3, BELT_Y + 3 + (i % 2), z + 3, mat);
            }
        }
        float top = cut(AssemblyHall.Kind.ROBOTICS, s);
        float unitTop = PAD_TOP + Math.max(8, s.productHeight);
        float r = Math.max(6, s.productRadius);
        //Bras : repos (replies) a l'arret ; au travail : les deux de l'avant soudent sur l'unite, les deux du fond vont chercher les pieces
        for (int i = 0; i < ARMS.length; i++) {
            float bx = ARMS[i][0], bz = ARMS[i][1], side = Math.signum(bx);
            float tx, ty, tz;
            BigMat part = null;
            if (!s.building) {
                tx = bx - side * 6;
                ty = ARM_BASE + 10;
                tz = bz;
            } else {
                float c = (t * (work ? 1 : 0.0F) * 0.012F + i * 0.37F) % 1F;
                //point de la surface de l'unite tourne vers le bras ; hauteur de travail sous la coupe
                float dl = (float) Math.hypot(bx, bz - PAD_Z);
                float ux = bx / dl * (r + 2), uz = PAD_Z + (bz - PAD_Z) / dl * (r + 2);
                float hy = PAD_TOP + (Math.min(top, unitTop) - PAD_TOP) * (0.3F + 0.55F * hash(i, (int) (t / 90)));
                if (i >= 2) {
                    //fond : prise au bout du convoyeur (0..0.4), transport (0.4..0.6), pose sur l'unite (0.6..0.85), retour
                    float px = side * 2, pz = BELT_END + 3, py = BELT_Y + 4;
                    float k;
                    if (c < 0.35F) {
                        k = 0;
                    } else if (c < 0.55F) {
                        k = smooth((c - 0.35F) / 0.2F);
                    } else if (c < 0.8F) {
                        k = 1;
                    } else {
                        k = 1 - smooth((c - 0.8F) / 0.2F);
                    }
                    float lift = (float) Math.sin(Math.PI * Math.min(1, Math.max(0, (c - 0.35F) / 0.2F))) * 8;
                    tx = px + (ux - px) * k;
                    tz = pz + (uz - pz) * k;
                    ty = py + (hy - py) * k + (c > 0.35F && c < 0.55F ? lift : 0) + (c < 0.3F ? 2 * (float) Math.sin(c * 20) : 0);
                    part = c > 0.15F && c < 0.7F ? (i == 2 ? BigMat.PAINT_O : BigMat.PCB) : null;
                } else {
                    //avant : passes de soudure sur l'unite
                    float sw = (float) Math.sin(t * 0.05F + i * 2.1F);
                    tx = ux + side * 1 + sw * 2;
                    tz = uz + (float) Math.cos(t * 0.04F + i) * 3;
                    ty = hy + sw * 3;
                }
                if (work && i < 2 && hash(i, (int) (t / 2)) < 0.6F) {
                    m.glow(15);
                    float d = 0.5F + hash(i + 7, (int) t) * 0.6F;
                    box(m, tx - d, ty - d, tz - d, tx + d, ty + d, tz + d, BigMat.FLAME_C);
                    m.glow(0);
                }
            }
            arm(m, bx, ARM_BASE, bz, tx, ty, tz, 15, 14, i < 2 ? BigMat.PAINT_O : BigMat.PAINT_R, part);
        }
        //Portique de controle : va et vient au repos, se place au-dessus de l'unite pendant l'assemblage et la balaie au laser
        float gz = s.building ? PAD_Z + (float) Math.sin(t * 0.02F) * 3 : PAD_Z + 30 * (float) Math.sin(t * 0.004F);
        box(m, -hw + 7, h - 9, gz - 2.5F, hw - 7, h - 6, gz + 2.5F, BigMat.PAINT_O);
        for (float sx : new float[]{-1, 1}) {
            box(m, sx * (hw - 7), h - 9.5F, gz - 3.5F, sx * (hw - 1), h - 4, gz + 3.5F, BigMat.MOTOR);
        }
        box(m, -3, h - 15, gz - 3, 3, h - 9, gz + 3, BigMat.STEEL_D);
        lamp(m, -1.5F, h - 16, gz - 1.5F, 1.5F, h - 15, gz + 1.5F, s.building ? BigMat.R_GLOW_CYAN : BigMat.R_LAMP_OFF);
        if (s.building) {
            //Rayon et anneau de balayage a la hauteur de coupe
            float ry = Math.min(top, h - 18);
            m.glow(15);
            box(m, -0.3F, ry, gz - 0.3F, 0.3F, h - 16, gz + 0.3F, BigMat.R_GLOW_CYAN);
            float a = r + 2.5F;
            box(m, -a, ry - 0.3F, PAD_Z - a, a, ry + 0.3F, PAD_Z - a + 0.6F, BigMat.R_GLOW_CYAN);
            box(m, -a, ry - 0.3F, PAD_Z + a - 0.6F, a, ry + 0.3F, PAD_Z + a, BigMat.R_GLOW_CYAN);
            box(m, -a, ry - 0.3F, PAD_Z - a + 0.6F, -a + 0.6F, ry + 0.3F, PAD_Z + a - 0.6F, BigMat.R_GLOW_CYAN);
            box(m, a - 0.6F, ry - 0.3F, PAD_Z - a + 0.6F, a, ry + 0.3F, PAD_Z + a - 0.6F, BigMat.R_GLOW_CYAN);
            //Plan d'impression (translucide) qui cache la coupe
            m.color(0x9050E0FF);
            box(m, -a + 0.6F, ry - 0.05F, PAD_Z - a + 0.6F, a - 0.6F, ry + 0.05F, PAD_Z + a - 0.6F, BigMat.GLASS);
            m.color(0xFFFFFFFF);
            m.glow(0);
        }
        //Voyants des socles
        for (int i = 0; i < ARMS.length; i++) {
            lamp(m, ARMS[i][0] - 1, 3, ARMS[i][1] - 4.3F, ARMS[i][0] + 1, 4.5F, ARMS[i][1] - 4,
                  work ? BigMat.R_LAMP_G : s.finished ? BigMat.R_LAMP_B : BigMat.R_LAMP_O);
        }
    }

    // ================================================================== hall d'integration des fusees

    /** Tour de service (fond du hall) : demi-cote et centre en z ; face avant (vers la fusee) en TOWER_Z - TOWER_HALF. */
    private static final float TOWER_HALF = 7, TOWER_Z = 40, CRANE_Y = 186, RUNWAY_X = 50;
    /** Bras d'acces : hauteurs (px). */
    private static final float[] ACCESS = {44, 92, 140};
    /** Aire de depot des troncons (avant gauche). */
    private static final float DEPOT_X = -38, DEPOT_Z = -40;

    private static void hall(BigMesh m, float w, float h, float l) {
        float hw = w / 2, hl = l / 2;
        float tx0 = -TOWER_HALF, tx1 = TOWER_HALF, tz0 = TOWER_Z - TOWER_HALF, tz1 = TOWER_Z + TOWER_HALF, top = h - 14;
        //Marquage autour des dalles de lancement
        for (int k = 0; k < 4; k++) {
            Matrix4f r = BigMesh.rot('y', 0, 0, 0, 90 * k);
            m.box(-27, 0.02F, -27, 24, 0.3F, -24, BigMat.R_HAZARD, r, 16);                       //en moulinet : pas de recouvrement aux angles
        }
        //Tour de service : quatre montants, ceintures tous les 16 px, croix de Saint-Andre sur trois faces
        for (float x : new float[]{tx0, tx1}) {
            for (float z : new float[]{tz0, tz1}) {
                box(m, x - 1.2F, 2, z - 1.2F, x + 1.2F, top, z + 1.2F, BigMat.PAINT_R);
            }
        }
        box(m, tx0 - 2, 0, tz0 - 2, tx1 + 2, 2, tz1 + 2, BigMat.STEEL_D);
        for (float y = 16; y <= top; y += 16) {
            beam(m, tx0, y, tz0, tx1, y, tz0, 1.4F, BigMat.PAINT_R);
            beam(m, tx0, y, tz1, tx1, y, tz1, 1.4F, BigMat.PAINT_R);
            beam(m, tx0, y, tz0, tx0, y, tz1, 1.4F, BigMat.PAINT_R);
            beam(m, tx1, y, tz0, tx1, y, tz1, 1.4F, BigMat.PAINT_R);
            float y0 = y - 16;
            //croix : la seconde diagonale un peu plus fine (jamais deux faces dans le meme plan au croisement)
            beam(m, tx0, y0, tz1, tx1, y, tz1, 0.9F, BigMat.PAINT_R);
            beam(m, tx1, y0, tz1, tx0, y, tz1, 0.7F, BigMat.PAINT_R);
            beam(m, tx0, y0, tz0, tx0, y, tz1, 0.9F, BigMat.PAINT_R);
            beam(m, tx0, y0, tz1, tx0, y, tz0, 0.7F, BigMat.PAINT_R);
            beam(m, tx1, y0, tz0, tx1, y, tz1, 0.9F, BigMat.PAINT_R);
            beam(m, tx1, y0, tz1, tx1, y, tz0, 0.7F, BigMat.PAINT_R);
        }
        box(m, tx0 - 3, top, tz0 - 3, tx1 + 3, top + 2, tz1 + 3, BigMat.R_GRILL);                                  //plancher du sommet
        //Rail de la plate-forme de travail sur la face avant de la tour
        for (float x : new float[]{-4, 4}) {
            box(m, x - 0.8F, 2, tz0 - 2.4F, x + 0.8F, top, tz0 - 1.2F, BigMat.CHROME);
        }
        //Ombilicaux le long de la tour
        for (float x : new float[]{tx1 + 2.5F, tx1 + 5}) {
            m.cyl('y', x, tz1 - 2, 1, 2, top - 6, BigMat.PIPE, 6, null, 16);
        }
        //Pivots des bras d'acces
        for (float y : ACCESS) {
            box(m, -3.5F, y - 3, tz0 - 4, 3.5F, y + 5, tz0 - 1, BigMat.STEEL_D);
        }
        //Chemins de roulement des ponts roulants, sur consoles
        for (float sx : new float[]{-1, 1}) {
            float x = sx * RUNWAY_X;
            box(m, x - 2.5F, CRANE_Y - 4, -hl, x + 2.5F, CRANE_Y, hl, BigMat.STEEL_D);
            box(m, x - 1, CRANE_Y, -hl, x + 1, CRANE_Y + 1, hl, BigMat.CHROME);
            for (float z = -hl + 8; z < hl; z += 24) {
                box(m, sx * (RUNWAY_X + 2.5F), CRANE_Y - 12, z - 1.5F, sx * hw, CRANE_Y, z + 1.5F, BigMat.STEEL_D);
                beam(m, sx * hw, CRANE_Y - 22, z, sx * (RUNWAY_X + 1), CRANE_Y - 4, z, 1.6F, BigMat.STEEL_D);
            }
        }
        //Aire de depot des troncons : berceau et marquage
        box(m, DEPOT_X - 10, 0.02F, DEPOT_Z - 10, DEPOT_X + 10, 0.3F, DEPOT_Z + 10, BigMat.R_STRIPE);
        box(m, DEPOT_X - 8, 0.03F, DEPOT_Z - 8, DEPOT_X + 8, 0.35F, DEPOT_Z + 8, BigMat.R_SLAB);
        for (float z : new float[]{-5, 5}) {
            box(m, DEPOT_X - 7, 0.35F, DEPOT_Z + z - 1.5F, DEPOT_X + 7, 4, DEPOT_Z + z + 1.5F, BigMat.PAINT_O);
        }
        //Troncons en attente, couches sur l'aire de depot (le long des parois)
        for (int k = 0; k < 2; k++) {
            float z = -40 + k * 14;
            m.cyl('x', 6, z, 5, hw - 27, hw - 3, BigMat.CAST_L, 12, null, 16);
            m.cyl('x', 6, z, 5.2F, hw - 16, hw - 13, BigMat.PAINT_R, 12, null, 16);
            box(m, hw - 26, 0, z - 4, hw - 23, 2, z + 4, BigMat.STEEL_D);
            box(m, hw - 7, 0, z - 4, hw - 4, 2, z + 4, BigMat.STEEL_D);
        }
        //Projecteurs aux angles
        for (float sx : new float[]{-1, 1}) {
            for (float sz : new float[]{-1, 1}) {
                for (float y : new float[]{64, 128}) {
                    box(m, sx * (hw - 3), y, sz * (hl - 3) - 2, sx * hw, y + 4, sz * (hl - 3) + 2, BigMat.STEEL_D);
                    lamp(m, sx * (hw - 3.5F), y + 0.5F, sz * (hl - 3) - 1.5F, sx * (hw - 3), y + 3.5F, sz * (hl - 3) + 1.5F, BigMat.R_LAMP);
                }
            }
        }
    }

    private static void hallLife(BigMesh m, float w, float h, float l, State s) {
        float hw = w / 2, hl = l / 2;
        float t = s.phase;
        boolean work = s.building && s.speed > 0.02F;
        float r = Math.max(10, s.productRadius);
        float cutY = cut(AssemblyHall.Kind.ROCKET, s);
        float top = h - 14, tz0 = TOWER_Z - TOWER_HALF;
        //--- Plate-forme de travail : deux moities qui se ferment autour de la fusee a la hauteur de coupe ; ouvertes et basses sinon
        float deckY, open;
        if (s.building) {
            deckY = Math.min(top - 8, cutY);
            open = 0;
        } else if (s.finished) {
            deckY = Math.min(top - 8, s.productHeight * 0.55F);
            open = 1;
        } else {
            deckY = 24;
            open = 1;
        }
        //moitie ouverte : ecartee au-dela des propulseurs d'appoint (rayon x 1,6), sans toucher les parois
        float gap = open * (r * 1.6F + 6), a = Math.max(10, Math.min(Math.min(36, r + 10), w / 2 - 2 - gap));
        for (float sx : new float[]{-1, 1}) {
            float x0 = sx * gap, x1 = sx * (gap + a);
            box(m, x0, deckY - 3, -a, x1, deckY - 1, a, BigMat.STEEL_D);
            box(m, x0, deckY - 1, -a, x1, deckY - 0.2F, a, BigMat.CAST_L);
            box(m, x0, deckY - 0.2F, -a, x1, deckY + 0.2F, a, BigMat.GRATE);
            box(m, x1 - sx * 1.5F, deckY + 0.2F, -a, x1, deckY + 0.6F, a, BigMat.R_HAZARD);
            //garde-corps
            for (float z : new float[]{-a, a}) {
                beam(m, x0 + sx * 1, deckY, z, x0 + sx * 1, deckY + 9, z, 0.8F, BigMat.R_STRIPE);
                beam(m, x1, deckY, z, x1, deckY + 9, z, 0.8F, BigMat.R_STRIPE);
                beam(m, x0 + sx * 1, deckY + 9, z, x1, deckY + 9, z, 0.6F, BigMat.R_STRIPE);
            }
            beam(m, x1, deckY + 9, -a, x1, deckY + 9, a, 0.5F, BigMat.R_STRIPE);
            //bras du chariot vers la tour
            beam(m, sx * (gap + 3), deckY - 2, a, sx * 3.5F, deckY - 2, tz0 - 2.5F, 2, BigMat.STEEL_D);
            //robot de soudure sur chaque moitie
            if (s.building) {
                float bx = sx * (r + 6), bz = -a * 0.5F * sx;
                float ang = t * 0.03F + (sx > 0 ? 0 : 3.1F);
                float txx = (float) Math.cos(ang) * (r + 0.5F), tzz = (float) Math.sin(ang) * (r + 0.5F);
                if (txx * sx < 0) {
                    txx = -txx;
                }
                arm(m, bx, deckY + 2, bz, txx, deckY - 3, tzz, 9, 8, BigMat.PAINT_O, null);
            }
        }
        //chariot sur la tour
        box(m, -6, deckY - 6, tz0 - 4.5F, 6, deckY + 2, tz0 - 1.5F, BigMat.MOTOR);
        //--- Soudure tout autour de la coupe
        if (work) {
            m.glow(15);
            for (int i = 0; i < 10; i++) {
                if (hash(i, (int) (t / 2)) > 0.65F) {
                    continue;
                }
                double ang = 2 * Math.PI * hash(i, (int) (t / 7) + 3);
                float sx = (float) Math.cos(ang) * (r + 0.6F), sz = (float) Math.sin(ang) * (r + 0.6F), sy = cutY - 1.5F - hash(i, 11) * 3;
                float d = 0.5F + hash(i, (int) t) * 0.7F;
                box(m, sx - d, sy - d, sz - d, sx + d, sy + d, sz + d, BigMat.FLAME_C);
            }
            m.glow(0);
        }
        //--- Bras d'acces : deployes vers la partie montee de la fusee
        float reach = tz0 - 4 - r;
        for (int i = 0; i < ACCESS.length; i++) {
            float y = ACCESS[i];
            float ext = s.product ? smooth((cutY - y - 8) / 24F) : 0;
            float ang = 90 * (1 - ext);
            Matrix4f rr = BigMesh.rot('y', 0, 0, tz0 - 2.5F, -ang);
            m.box(-3, y - 2, tz0 - 2.5F - reach, 3, y + 2, tz0 - 2.5F, BigMat.PAINT_R, rr, 16);
            m.box(-4.5F, y - 3, tz0 - 3F - reach, 4.5F, y + 5, tz0 - 2.5F - reach + 6, BigMat.R_WHITE, rr, 16);    //salle blanche
            m.box(-2, y + 2, tz0 - 2.5F - reach + 6, 2, y + 2.6F, tz0 - 2.5F, BigMat.R_GRILL, rr, 16);
        }
        //--- Ascenseur de la tour
        float ey = 4 + (top - 16) * (0.5F - 0.5F * (float) Math.cos(t * 0.006F));
        box(m, -5, ey, TOWER_Z - 5, 5, ey + 10, TOWER_Z + 5, BigMat.CHROME);
        lamp(m, -1, ey + 10, TOWER_Z - 1, 1, ey + 11, TOWER_Z + 1, BigMat.R_LAMP_O);
        //--- Pont roulant A : troncons de fuselage de l'aire de depot jusqu'au sommet de la fusee
        float cz, cx, hookY;
        boolean carry = false;
        float segH = 14, rr2 = r * 0.92F, stackY = Math.min(cutY, s.productHeight) + 18;
        if (s.building) {
            float c = t * 0.0035F % 1F;
            if (c < 0.2F) {
                //descente sur l'aire de depot et prise
                cz = DEPOT_Z;
                cx = DEPOT_X;
                float k = c < 0.1F ? smooth(c / 0.1F) : 1 - smooth((c - 0.1F) / 0.1F);
                hookY = CRANE_Y - 10 - (CRANE_Y - 10 - (4 + segH + 4)) * k;
                carry = c > 0.1F;
            } else if (c < 0.45F) {
                float k = smooth((c - 0.2F) / 0.25F);
                cz = DEPOT_Z + (0 - DEPOT_Z) * k;
                cx = DEPOT_X + (0 - DEPOT_X) * k;
                hookY = CRANE_Y - 10;
                carry = true;
            } else if (c < 0.7F) {
                cz = 0;
                cx = 0;
                float k = c < 0.575F ? smooth((c - 0.45F) / 0.125F) : 1 - smooth((c - 0.575F) / 0.125F);
                float low = Math.min(CRANE_Y - 12, stackY + segH + 4);
                hookY = CRANE_Y - 10 - (CRANE_Y - 10 - low) * k;
                carry = c < 0.575F;
            } else {
                float k = smooth((c - 0.7F) / 0.3F);
                cz = DEPOT_Z * k;
                cx = DEPOT_X * k;
                hookY = CRANE_Y - 10;
            }
        } else {
            cz = -hl + 14;
            cx = DEPOT_X;
            hookY = CRANE_Y - 10;
        }
        crane(m, hw, cx, cz, hookY, BigMat.PAINT_O);
        if (carry) {
            float sy = hookY - 4 - segH;
            for (float sx : new float[]{-1, 1}) {
                beam(m, cx, hookY - 1, cz, cx + sx * rr2 * 0.8F, sy + segH, cz, 0.4F, BigMat.R_BLACK);                   //elingues
            }
            m.cyl('y', cx, cz, rr2, sy, sy + segH, BigMat.CAST_L, 14, null, 16);
            m.cyl('y', cx, cz, rr2 + 0.2F, sy + segH * 0.4F, sy + segH * 0.6F, BigMat.PAINT_R, 14, null, 16);
        }
        //--- Pont roulant B : au-dessus de la tour, va et vient lentement pendant l'assemblage
        float bz = s.building ? 16 + 6 * (float) Math.sin(t * 0.004F) : 20;
        crane(m, hw, 18 + 6 * (float) Math.sin(t * 0.003F), bz, CRANE_Y - 18, BigMat.PAINT_O);
    }

    /** Pont roulant : poutre en travers (x), sommiers sur les chemins de roulement, chariot en (cx, cz), crochet a hookY. */
    private static void crane(BigMesh m, float hw, float cx, float cz, float hookY, BigMat paint) {
        box(m, -RUNWAY_X - 3, CRANE_Y + 1, cz - 5, -RUNWAY_X + 3, CRANE_Y + 5, cz + 5, BigMat.MOTOR);
        box(m, RUNWAY_X - 3, CRANE_Y + 1, cz - 5, RUNWAY_X + 3, CRANE_Y + 5, cz + 5, BigMat.MOTOR);
        box(m, -RUNWAY_X, CRANE_Y + 2, cz - 3.5F, RUNWAY_X, CRANE_Y + 7, cz - 1.5F, paint);
        box(m, -RUNWAY_X, CRANE_Y + 2, cz + 1.5F, RUNWAY_X, CRANE_Y + 7, cz + 3.5F, paint);
        box(m, -RUNWAY_X + 4, CRANE_Y + 7, cz - 3.5F, RUNWAY_X - 4, CRANE_Y + 7.6F, cz + 3.5F, BigMat.R_HAZARD);
        box(m, cx - 4, CRANE_Y - 1, cz - 4, cx + 4, CRANE_Y + 9, cz + 4, BigMat.STEEL_D);                           //chariot
        lamp(m, cx - 1, CRANE_Y - 1.5F, cz - 1, cx + 1, CRANE_Y - 1, cz + 1, BigMat.R_LAMP_O);
        for (float dx : new float[]{-1.5F, 1.5F}) {
            box(m, cx + dx - 0.3F, hookY, cz - 0.3F, cx + dx + 0.3F, CRANE_Y - 1, cz + 0.3F, BigMat.R_BLACK);       //cables
        }
        box(m, cx - 2.5F, hookY - 3, cz - 2, cx + 2.5F, hookY, cz + 2, BigMat.R_HAZARD);                            //moufle
        box(m, cx - 0.6F, hookY - 5, cz - 0.6F, cx + 0.6F, hookY - 3, cz + 0.6F, BigMat.CHROME);                    //crochet
    }
}
