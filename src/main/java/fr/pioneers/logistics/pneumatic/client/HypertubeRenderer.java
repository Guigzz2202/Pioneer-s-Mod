package fr.pioneers.logistics.pneumatic.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.logistics.pneumatic.network.HypertubeCurve;
import fr.pioneers.logistics.pneumatic.network.HypertubeEntity;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * 0.20.39 : rendu d'un troncon d'hypertube : tube rond qui suit la courbe (anneaux de {@link #SIDES} cotes, reperes transportes le long de
 * l'axe pour ne jamais vriller), cerces a chaque bloc de longueur et brides aux deux bouts. Maillage calcule une fois par vue (repere de
 * l'entite), lumiere relue par bloc a chaque image.
 * <p>
 * 0.20.43 (Guillaume : « tube jaune legerement orange, vitres bleu turquoise tres transparentes pour voir pendant le trajet ») : coque peinte
 * jaune-orange sur le bas du tube (le plancher, cote du monde : {@link #FLOOR}), grande verriere turquoise presque transparente sur le reste,
 * cerces orange plus soutenu ; tout est visible de l'interieur (double face).
 * <p>
 * 0.20.46 (audit) : sommets precalcules une fois par maillage (grille de la paroi en floats, reperes des cerces), plus aucun Vec3 ni cos/sin a
 * l'image ; lumiere relue toutes les {@link #LIGHT_TICKS} ticks ; niveau de detail par troncon d'environ 8 blocs : au-dela de {@link #LOD_NEAR}
 * blocs, 8 cotes et un anneau sur deux ; au-dela de {@link #LOD_FAR}, plus de cerces.
 */
public class HypertubeRenderer extends EntityRenderer<HypertubeEntity> {

    static final ResourceLocation GLASS = Pioneers.rl("textures/block/pneumatic/hypertube_glass.png");
    static final ResourceLocation SHELL = Pioneers.rl("textures/block/pneumatic/hypertube_shell.png");
    static final ResourceLocation RIB = Pioneers.rl("textures/block/pneumatic/hypertube_rib.png");
    static final int SIDES = 16;
    /** Cotes dont la direction descend sous ce seuil (composante verticale) : coque peinte (plancher) ; au-dessus : verriere. */
    static final double FLOOR = -0.38;
    /** 0.20.43 (Guillaume : « enleve la partie basse orange ») : plus de plancher peint, verriere sur tout le tour. */
    static final boolean FLOOR_ON = false;
    /** Teinte de la verriere (turquoise) ; l'opacite vient de la texture (tres transparente). */
    //0.20.45 : turquoise pale (l'ancien 70/226/212 virait au vert sur plusieurs epaisseurs)
    static final int GLASS_R = 70, GLASS_G = 214, GLASS_B = 226;
    /** Bague : demi-largeur et surepaisseur ; bride : longueur et surepaisseur (blocs). */
    private static final double BAND_HALF = 0.05, BAND_OUT = 0.03, FLANGE_LEN = 0.12, FLANGE_OUT = 0.06;
    /** 0.20.46 (audit) : distances (blocs, du bord du troncon) : au-dela de NEAR, detail reduit ; au-dela de FAR, plus de cerces. */
    static final double LOD_NEAR = 48, LOD_FAR = 96;
    /** 0.20.46 (audit) : anneaux par troncon de detail (pair : le detail reduit garde les anneaux pairs, communs aux deux niveaux). */
    static final int SECTION = 32;
    /** 0.20.46 (audit) : la lumiere du tube est relue toutes les 20 ticks (et non plus a chaque image). */
    static final int LIGHT_TICKS = 20;
    /** 0.20.46 (audit) : cos/sin precalcules (16 cotes, et 8 pour le detail reduit) ; memes formules que l'ancien calcul. */
    static final Trig FULL = new Trig(SIDES), LOW = new Trig(SIDES / 2);
    /** Vecteurs de travail (fil de rendu seulement). */
    private static final Vector4f P = new Vector4f();
    private static final Vector3f N = new Vector3f();

    public HypertubeRenderer(EntityRendererProvider.Context context) {
        super(context);
        shadowRadius = 0;
    }

    /** 0.20.46 (audit) : types de rendu mis en cache (classe a part : le banc hors du jeu ne les charge pas). */
    private static final class Types {

        static final RenderType SHELL = RenderType.entityCutoutNoCull(HypertubeRenderer.SHELL);
        static final RenderType RIB = RenderType.entityCutoutNoCull(HypertubeRenderer.RIB);
        static final RenderType GLASS = RenderType.entityTranslucentCull(HypertubeRenderer.GLASS);
    }

    /** 0.20.46 (audit) : cos/sin des cotes et de leurs milieux pour un nombre de cotes. */
    static final class Trig {

        final int sides;
        final double[] cos, sin, cmid, smid;
        final float[] u;

        Trig(int sides) {
            this.sides = sides;
            cos = new double[sides + 1];
            sin = new double[sides + 1];
            u = new float[sides + 1];
            cmid = new double[sides];
            smid = new double[sides];
            for (int q = 0; q <= sides; q++) {
                double a = 2 * Math.PI * q / sides;
                cos[q] = Math.cos(a);
                sin[q] = Math.sin(a);
                u[q] = (float) q / sides;
            }
            for (int q = 0; q < sides; q++) {
                double a0 = 2 * Math.PI * q / sides, a1 = 2 * Math.PI * (q + 1) / sides, a = (a0 + a1) / 2;
                cmid[q] = Math.cos(a);
                smid[q] = Math.sin(a);
            }
        }
    }

    /** 0.20.46 (audit) : cerce ou bride precalculee : reperes de ses deux faces (centre, n, b, tangente : 12 doubles), rayons, anneau de lumiere. */
    record Band(double[] f0, double[] f1, double inner, double outer, int light) {
    }

    /** Anneaux de l'axe dans le repere de l'entite : centre, reperes (n, b), abscisse curviligne. */
    static final class Mesh {

        final Vec3[] center, n, b;
        final double[] s;
        final double length;
        final BlockPos[] lightAt;
        /** 0.20.46 (audit) : origine du repere (position de l'entite a la creation du maillage). */
        final Vec3 origin;
        /** 0.20.46 (audit) : grille de la paroi, par anneau et par cote (SIDES + 1 colonnes) : position puis normale (6 floats). */
        final float[] grid;
        /** 0.20.46 (audit) : teinte de verriere de chaque point de la grille (0xRRGGBB). */
        final int[] tint;
        /** 0.20.46 (audit) : abscisse de chaque anneau modulo 1 (coordonnee v de la texture). */
        final double[] vAt;
        /** 0.20.46 (audit) : troncons de detail : nombre, sphere englobante (x, y, z, rayon), premiere cerce de chacun. */
        final int sections;
        final double[] sphere;
        final Band[] bands;
        final int[] bandFrom;
        /** 0.20.46 (audit) : lumiere par anneau, relue toutes les {@link #LIGHT_TICKS} ticks (client). */
        @Nullable
        int[] lights;
        long lightTime;

        Mesh(HypertubeCurve c, Vec3 origin) {
            this.origin = origin;
            List<Vec3> pts = c.points();
            int k = pts.size();
            center = new Vec3[k];
            n = new Vec3[k];
            b = new Vec3[k];
            s = new double[k];
            lightAt = new BlockPos[k];
            length = c.length();
            Vec3 prevN = null;
            for (int i = 0; i < k; i++) {
                Vec3 p = pts.get(i);
                center[i] = p.subtract(origin);
                s[i] = i == 0 ? 0 : s[i - 1] + p.distanceTo(pts.get(i - 1));
                lightAt[i] = BlockPos.containing(p);
                Vec3 t = pts.get(Math.min(k - 1, i + 1)).subtract(pts.get(Math.max(0, i - 1)));
                t = t.lengthSqr() < 1e-12 ? c.ta : t.normalize();
                Vec3 nn;
                if (prevN == null) {
                    Vec3 up = Math.abs(t.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
                    nn = up.subtract(t.scale(t.dot(up))).normalize();
                } else {
                    nn = prevN.subtract(t.scale(t.dot(prevN)));
                    nn = nn.lengthSqr() < 1e-12 ? prevN : nn.normalize();
                }
                n[i] = nn;
                b[i] = t.cross(nn).normalize();
                prevN = nn;
            }
            //0.20.46 (audit) : paroi precalculee (memes calculs que l'ancien sommet par sommet)
            double r = HypertubeCurve.RADIUS;
            int cols = SIDES + 1;
            grid = new float[k * cols * 6];
            tint = new int[k * cols];
            vAt = new double[k];
            for (int i = 0; i < k; i++) {
                vAt[i] = s[i] % 1.0;
                for (int q = 0; q < cols; q++) {
                    double cos = FULL.cos[q], sin = FULL.sin[q];
                    Vec3 dir = n[i].scale(cos).add(b[i].scale(sin));
                    Vec3 p = center[i].add(dir.scale(r));
                    int g = i * cols + q, o = g * 6;
                    grid[o] = (float) p.x;
                    grid[o + 1] = (float) p.y;
                    grid[o + 2] = (float) p.z;
                    grid[o + 3] = (float) dir.x;
                    grid[o + 4] = (float) dir.y;
                    grid[o + 5] = (float) dir.z;
                    //Verriere turquoise, un peu plus claire vers le haut (reflet du ciel)
                    double kk = 0.85 + 0.15 * Math.max(0, dir.y);
                    int cr = (int) (GLASS_R * kk + 255 * (1 - kk)), cg = (int) (GLASS_G * kk + 255 * (1 - kk)), cb = (int) (GLASS_B * kk + 255 * (1 - kk));
                    tint[g] = cr << 16 | cg << 8 | cb;
                }
            }
            //Troncons de detail et leur sphere englobante
            sections = Math.max(1, (k - 1 + SECTION - 1) / SECTION);
            sphere = new double[sections * 4];
            for (int sec = 0; sec < sections; sec++) {
                int from = Math.min(k - 1, sec * SECTION), to = Math.min(k - 1, from + SECTION);
                double x0 = Double.MAX_VALUE, y0 = Double.MAX_VALUE, z0 = Double.MAX_VALUE, x1 = -Double.MAX_VALUE, y1 = -Double.MAX_VALUE, z1 = -Double.MAX_VALUE;
                for (int i = from; i <= to; i++) {
                    Vec3 p = center[i];
                    x0 = Math.min(x0, p.x);
                    y0 = Math.min(y0, p.y);
                    z0 = Math.min(z0, p.z);
                    x1 = Math.max(x1, p.x);
                    y1 = Math.max(y1, p.y);
                    z1 = Math.max(z1, p.z);
                }
                sphere[sec * 4] = (x0 + x1) / 2;
                sphere[sec * 4 + 1] = (y0 + y1) / 2;
                sphere[sec * 4 + 2] = (z0 + z1) / 2;
                sphere[sec * 4 + 3] = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0)) / 2 + r + FLANGE_OUT;
            }
            //Cerces (une par bloc de longueur) et brides aux bouts, rangees par troncon
            int count = (int) Math.floor(length);
            bands = new Band[count + 2];
            int[] secOf = new int[count + 2];
            bands[0] = band(0, FLANGE_LEN, r + FLANGE_OUT, 0);
            for (int j = 1; j <= count; j++) {
                double at = j * length / (count + 1);
                int li = lightIndex(at);
                bands[j] = band(at - BAND_HALF, at + BAND_HALF, r + BAND_OUT, li);
                secOf[j] = Math.min(sections - 1, Math.max(0, li - 1) / SECTION);
            }
            bands[count + 1] = band(length - FLANGE_LEN, length, r + FLANGE_OUT, k - 1);
            secOf[count + 1] = sections - 1;
            bandFrom = new int[sections + 1];
            for (int sec = 0, j = 0; sec <= sections; sec++) {
                while (j < bands.length && secOf[j] < sec) {
                    j++;
                }
                bandFrom[sec] = j;
            }
            bandFrom[sections] = bands.length;
        }

        private Band band(double from, double to, double outer, int light) {
            return new Band(flat(frame(Math.max(0, from))), flat(frame(Math.min(length, to))), HypertubeCurve.RADIUS - 0.01, outer, light);
        }

        /** Premier anneau a l'abscisse {@code at} ou au-dela (lumiere d'une cerce). */
        private int lightIndex(double at) {
            for (int i = 0; i < s.length; i++) {
                if (s[i] >= at) {
                    return i;
                }
            }
            return s.length - 1;
        }

        /** Repere a l'abscisse {@code at} (interpolation entre les anneaux voisins) : centre, n, b, tangente. */
        Vec3[] frame(double at) {
            int i = 0;
            while (i + 2 < s.length && s[i + 1] < at) {
                i++;
            }
            int j = Math.min(s.length - 1, i + 1);
            double span = s[j] - s[i];
            double f = span <= 1e-9 ? 0 : Math.max(0, Math.min(1, (at - s[i]) / span));
            Vec3 c = center[i].lerp(center[j], f);
            Vec3 nn = n[i].lerp(n[j], f).normalize();
            Vec3 t = j == i ? b[i].cross(n[i]) : center[j].subtract(center[i]).normalize();
            Vec3 bb = t.cross(nn).normalize();
            return new Vec3[]{c, nn, bb, t};
        }

        /** 0.20.46 (audit) : niveau de detail de chaque troncon vu de {@code (x, y, z)} (repere du maillage) : 0 plein, 1 reduit, 2 sans cerces. */
        byte[] lod(double x, double y, double z) {
            byte[] out = new byte[sections];
            for (int sec = 0; sec < sections; sec++) {
                double dx = sphere[sec * 4] - x, dy = sphere[sec * 4 + 1] - y, dz = sphere[sec * 4 + 2] - z;
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz) - sphere[sec * 4 + 3];
                out[sec] = (byte) (d < LOD_NEAR ? 0 : d < LOD_FAR ? 1 : 2);
            }
            return out;
        }
    }

    private static double[] flat(Vec3[] f) {
        return new double[]{f[0].x, f[0].y, f[0].z, f[1].x, f[1].y, f[1].z, f[2].x, f[2].y, f[2].z, f[3].x, f[3].y, f[3].z};
    }

    /** 0.20.46 (audit) : lumiere de chaque anneau (un releve par bloc traverse). */
    static int[] lights(Level level, Mesh mesh) {
        int k = mesh.lightAt.length;
        int[] lights = new int[k];
        int last = -1;
        BlockPos lastPos = null;
        for (int i = 0; i < k; i++) {
            if (!mesh.lightAt[i].equals(lastPos)) {
                lastPos = mesh.lightAt[i];
                BlockPos at = level.getBlockState(lastPos).isSolidRender(level, lastPos) ? lastPos.above() : lastPos;
                last = LevelRenderer.getLightColor(level, at);
            }
            lights[i] = last;
        }
        return lights;
    }

    @Override
    public void render(@NotNull HypertubeEntity e, float yaw, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light) {
        HypertubeCurve curve = e.curve();
        if (curve == null) {
            return;
        }
        Mesh mesh;
        if (e.clientMesh instanceof Mesh cached) {
            mesh = cached;
        } else {
            mesh = new Mesh(curve, e.position());
            e.clientMesh = mesh;
        }
        Level level = e.level();
        //0.20.46 (audit) : lumiere relue toutes les 20 ticks, decalee d'un tube a l'autre (pas tous la meme image)
        long now = level.getGameTime();
        int[] lights = mesh.lights;
        if (lights == null || now - mesh.lightTime >= LIGHT_TICKS || now < mesh.lightTime) {
            mesh.lightTime = lights == null ? now - Math.floorMod(e.getId(), LIGHT_TICKS) : now;
            lights = lights(level, mesh);
            mesh.lights = lights;
        }
        Vec3 cam = entityRenderDispatcher.camera.getPosition();
        byte[] lod = mesh.lod(cam.x - mesh.origin.x, cam.y - mesh.origin.y, cam.z - mesh.origin.z);
        //0.20.45 : un tampon a la fois. Les types d'entite partagent le meme tampon dans le rendu immediat : demander les trois d'avance
        //faisait ecrire la verriere dans le dernier (cerces, opaque) -> tube vert-olive et pas transparent
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        if (FLOOR_ON) {
            drawTube(mesh, lights, lod, m, nm, buffers.getBuffer(Types.SHELL), true);
        }
        //0.20.46 : la verriere est dessinee APRES l'eau (passe translucide du monde, GlassPass) : dessinee avec les entites, elle ecrivait
        //sa profondeur avant l'eau, qui n'etait plus dessinee derriere elle (on voyait le fond comme a sec)
        GlassPass.queue(new Matrix4f(m), new Matrix3f(nm), mesh, lights, lod);
        boolean ribs = false;
        for (byte l : lod) {
            ribs |= l < 2;
        }
        if (ribs) {
            drawRibs(mesh, lights, lod, m, nm, buffers.getBuffer(Types.RIB));
        }
        super.render(e, yaw, partialTick, pose, buffers, light);
    }

    /** Maillage complet (verriere, coque, cerces, brides) dans les trois tampons ; aussi utilise par le banc d'apercu (detail plein). */
    static void draw(Mesh mesh, int[] lights, Matrix4f m, Matrix3f nm, VertexConsumer glass, VertexConsumer shell, VertexConsumer rib) {
        if (FLOOR_ON) {
            drawTube(mesh, lights, null, m, nm, shell, true);
        }
        drawTube(mesh, lights, null, m, nm, glass, false);
        drawRibs(mesh, lights, null, m, nm, rib);
    }

    /**
     * Paroi du tube : la coque peinte du bas ({@code floorPart}) ou la verriere (le reste). 0.20.46 (audit) : sommets lus dans la grille
     * precalculee ; troncons a detail reduit ({@code lod} non nul) : un cote sur deux et un anneau sur deux.
     */
    static void drawTube(Mesh mesh, int[] lights, @Nullable byte[] lod, Matrix4f m, Matrix3f nm, VertexConsumer vc, boolean floorPart) {
        int k = lights.length, cols = SIDES + 1;
        float[] g = mesh.grid;
        for (int sec = 0; sec < mesh.sections; sec++) {
            int step = lod != null && lod[sec] > 0 ? 2 : 1;
            int from = sec * SECTION, to = Math.min(k - 1, from + SECTION);
            for (int i = from; i < to; ) {
                int j = Math.min(to, i + step);
                double v0 = mesh.vAt[i];
                float fv0 = (float) v0, fv1 = (float) (v0 + (mesh.s[j] - mesh.s[i]));
                int li = lights[i], lj = lights[j];
                for (int q = 0; q < SIDES; q += step) {
                    int q1 = q + step;
                    boolean floor = FLOOR_ON && floor(mesh, i, j, q, q1);
                    if (floor != floorPart) {
                        continue;
                    }
                    float u0 = FULL.u[q], u1 = FULL.u[q1];
                    gridVertex(vc, m, nm, mesh, g, i * cols + q, u0, fv0, li, !floor);
                    gridVertex(vc, m, nm, mesh, g, j * cols + q, u0, fv1, lj, !floor);
                    gridVertex(vc, m, nm, mesh, g, j * cols + q1, u1, fv1, lj, !floor);
                    gridVertex(vc, m, nm, mesh, g, i * cols + q1, u1, fv0, li, !floor);
                }
                i = j;
            }
        }
    }

    /** Plancher : cote qui regarde vers le bas (moyenne des deux anneaux). Seulement si {@link #FLOOR_ON}. */
    private static boolean floor(Mesh mesh, int i, int j, int q, int q1) {
        double a0 = 2 * Math.PI * q / SIDES, a1 = 2 * Math.PI * q1 / SIDES, am = (a0 + a1) / 2;
        double down = (mesh.n[i].scale(Math.cos(am)).add(mesh.b[i].scale(Math.sin(am))).y
                       + mesh.n[j].scale(Math.cos(am)).add(mesh.b[j].scale(Math.sin(am))).y) / 2;
        return down < FLOOR;
    }

    private static void gridVertex(VertexConsumer vc, Matrix4f m, Matrix3f nm, Mesh mesh, float[] g, int gi, float u, float v, int light, boolean glass) {
        int o = gi * 6;
        emit(vc, m, nm, g[o], g[o + 1], g[o + 2], glass ? mesh.tint[gi] : 0xFFFFFF, u, v, light, g[o + 3], g[o + 4], g[o + 5]);
    }

    /** Cerces et brides (opaques). 0.20.46 (audit) : reperes precalcules ; 8 cotes en detail reduit, rien au-dela de {@link #LOD_FAR}. */
    static void drawRibs(Mesh mesh, int[] lights, @Nullable byte[] lod, Matrix4f m, Matrix3f nm, VertexConsumer rib) {
        for (int sec = 0; sec < mesh.sections; sec++) {
            int level = lod == null ? 0 : lod[sec];
            if (level >= 2) {
                continue;
            }
            Trig t = level == 0 ? FULL : LOW;
            for (int j = mesh.bandFrom[sec]; j < mesh.bandFrom[sec + 1]; j++) {
                Band band = mesh.bands[j];
                ring(rib, m, nm, band.f0(), band.f1(), band.inner(), band.outer(), t, lights[band.light()]);
            }
        }
    }

    /** Bague pleine entre deux abscisses : face exterieure et deux couronnes jusqu'au verre (ancrages). */
    static void ring(VertexConsumer vc, Matrix4f m, Matrix3f nm, Mesh mesh, double from, double to, double inner, double outer, int light) {
        ring(vc, m, nm, flat(mesh.frame(Math.max(0, from))), flat(mesh.frame(Math.min(mesh.length, to))), inner, outer, FULL, light);
    }

    /** 0.20.46 (audit) : bague d'apres deux reperes plats (centre, n, b, tangente), cos/sin en table ; memes sommets que l'ancien calcul. */
    static void ring(VertexConsumer vc, Matrix4f m, Matrix3f nm, double[] f0, double[] f1, double inner, double outer, Trig t, int light) {
        for (int q = 0; q < t.sides; q++) {
            float u0 = t.u[q], u1 = t.u[q + 1];
            double co0 = t.cos[q] * outer, so0 = t.sin[q] * outer, co1 = t.cos[q + 1] * outer, so1 = t.sin[q + 1] * outer;
            double ci0 = t.cos[q] * inner, si0 = t.sin[q] * inner, ci1 = t.cos[q + 1] * inner, si1 = t.sin[q + 1] * inner;
            double cm = t.cmid[q], sm = t.smid[q];
            //Face exterieure (normale radiale au milieu du cote)
            float rx = (float) (f0[3] * cm + f0[6] * sm), ry = (float) (f0[4] * cm + f0[7] * sm), rz = (float) (f0[5] * cm + f0[8] * sm);
            at(vc, m, nm, f0, co0, so0, u0, 0.3F, light, rx, ry, rz);
            at(vc, m, nm, f1, co0, so0, u0, 0.7F, light, rx, ry, rz);
            at(vc, m, nm, f1, co1, so1, u1, 0.7F, light, rx, ry, rz);
            at(vc, m, nm, f0, co1, so1, u1, 0.3F, light, rx, ry, rz);
            //Couronne du debut (normale : -tangente)
            float ax = (float) -f0[9], ay = (float) -f0[10], az = (float) -f0[11];
            at(vc, m, nm, f0, ci0, si0, u0, 0.1F, light, ax, ay, az);
            at(vc, m, nm, f0, co0, so0, u0, 0.3F, light, ax, ay, az);
            at(vc, m, nm, f0, co1, so1, u1, 0.3F, light, ax, ay, az);
            at(vc, m, nm, f0, ci1, si1, u1, 0.1F, light, ax, ay, az);
            //Couronne de la fin (normale : tangente)
            float bx = (float) f1[9], by = (float) f1[10], bz = (float) f1[11];
            at(vc, m, nm, f1, ci1, si1, u0, 0.7F, light, bx, by, bz);
            at(vc, m, nm, f1, co1, so1, u0, 0.9F, light, bx, by, bz);
            at(vc, m, nm, f1, co0, so0, u1, 0.9F, light, bx, by, bz);
            at(vc, m, nm, f1, ci0, si0, u1, 0.7F, light, bx, by, bz);
        }
    }

    /** Point du repere plat {@code f} : centre + n * (cos r) + b * (sin r) (meme ordre de calcul que Vec3). */
    private static void at(VertexConsumer vc, Matrix4f m, Matrix3f nm, double[] f, double cr, double sr, float u, float v, int light, float nx, float ny,
          float nz) {
        emit(vc, m, nm, (float) (f[0] + f[3] * cr + f[6] * sr), (float) (f[1] + f[4] * cr + f[7] * sr), (float) (f[2] + f[5] * cr + f[8] * sr), 0xFFFFFF,
              u, v, light, nx, ny, nz);
    }

    static void quad(VertexConsumer vc, Matrix4f m, Matrix3f nm, int light, Vec3 a, Vec3 b, Vec3 c, Vec3 d, Vec3 normal, float u0, float u1,
          float v0, float v1) {
        put(vc, m, nm, light, a, normal, u0, v0);
        put(vc, m, nm, light, b, normal, u0, v1);
        put(vc, m, nm, light, c, normal, u1, v1);
        put(vc, m, nm, light, d, normal, u1, v0);
    }

    private static void put(VertexConsumer vc, Matrix4f m, Matrix3f nm, int light, Vec3 p, Vec3 n, float u, float v) {
        emit(vc, m, nm, (float) p.x, (float) p.y, (float) p.z, 0xFFFFFF, u, v, light, (float) n.x, (float) n.y, (float) n.z);
    }

    /** 0.20.46 (audit) : un sommet, transforme comme {@code vertex(Matrix4f, ...)} / {@code normal(Matrix3f, ...)} mais sans allocation. */
    static void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, float x, float y, float z, int rgb, float u, float v, int light, float nx, float ny,
          float nz) {
        Vector4f p = m.transform(P.set(x, y, z, 1.0F));
        Vector3f n = nm.transform(N.set(nx, ny, nz));
        vc.vertex(p.x(), p.y(), p.z()).color(rgb >> 16 & 255, rgb >> 8 & 255, rgb & 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
              .normal(n.x(), n.y(), n.z()).endVertex();
    }

    @NotNull
    @Override
    public ResourceLocation getTextureLocation(@NotNull HypertubeEntity e) {
        return GLASS;
    }

    /**
     * 0.20.46 (audit) : maillage enregistre une fois (repere local, sans transformation) puis rejoue a chaque image avec la pose et la lumiere
     * du moment : sert aux ancrages. Couleur gardee (alpha toujours plein), lumiere et recouvrement ignores a l'enregistrement.
     */
    static final class Baked implements VertexConsumer {

        private float[] f = new float[8 * 64];
        private int[] rgb = new int[64];
        private int count;
        private float x, y, z, u, v, nx, ny, nz;
        private int color = 0xFFFFFF;

        @NotNull
        @Override
        public VertexConsumer vertex(double px, double py, double pz) {
            x = (float) px;
            y = (float) py;
            z = (float) pz;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer color(int r, int g, int b, int a) {
            color = (r & 255) << 16 | (g & 255) << 8 | b & 255;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer uv(float pu, float pv) {
            u = pu;
            v = pv;
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer overlayCoords(int pu, int pv) {
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer uv2(int pu, int pv) {
            return this;
        }

        @NotNull
        @Override
        public VertexConsumer normal(float px, float py, float pz) {
            nx = px;
            ny = py;
            nz = pz;
            return this;
        }

        @Override
        public void endVertex() {
            if (count == rgb.length) {
                rgb = Arrays.copyOf(rgb, count * 2);
                f = Arrays.copyOf(f, count * 16);
            }
            int o = count * 8;
            f[o] = x;
            f[o + 1] = y;
            f[o + 2] = z;
            f[o + 3] = u;
            f[o + 4] = v;
            f[o + 5] = nx;
            f[o + 6] = ny;
            f[o + 7] = nz;
            rgb[count++] = color;
        }

        @Override
        public void defaultColor(int r, int g, int b, int a) {
        }

        @Override
        public void unsetDefaultColor() {
        }

        /** Fige le maillage (tableaux a la bonne taille). */
        Baked trim() {
            f = Arrays.copyOf(f, count * 8);
            rgb = Arrays.copyOf(rgb, count);
            return this;
        }

        /** Rejoue les sommets avec la pose {@code m}/{@code nm} et la lumiere {@code light}. */
        void emit(VertexConsumer vc, Matrix4f m, Matrix3f nm, int light) {
            for (int i = 0; i < count; i++) {
                int o = i * 8;
                HypertubeRenderer.emit(vc, m, nm, f[o], f[o + 1], f[o + 2], rgb[i], f[o + 3], f[o + 4], light, f[o + 5], f[o + 6], f[o + 7]);
            }
        }
    }

    /** 0.20.46 : verrieres en attente, dessinees a la fin de la passe translucide du monde (apres l'eau, avec test de profondeur). */
    @Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
    public static final class GlassPass {

        private record Job(Matrix4f m, Matrix3f nm, Mesh mesh, int[] lights, byte[] lod) {
        }

        private static final List<Job> JOBS = new ArrayList<>();

        private GlassPass() {
        }

        static void queue(Matrix4f m, Matrix3f nm, Mesh mesh, int[] lights, byte[] lod) {
            JOBS.add(new Job(m, nm, mesh, lights, lod));
        }

        /** 0.20.46 (audit) : oublie les verrieres en attente (deconnexion). */
        static void clear() {
            JOBS.clear();
        }

        @SubscribeEvent
        public static void stage(RenderLevelStageEvent event) {
            if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_SKY) {
                JOBS.clear();
                return;
            }
            if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || JOBS.isEmpty()) {
                return;
            }
            MultiBufferSource.BufferSource source = Minecraft.getInstance().renderBuffers().bufferSource();
            RenderType type = Types.GLASS;
            VertexConsumer vc = source.getBuffer(type);
            for (Job j : JOBS) {
                drawTube(j.mesh, j.lights, j.lod, j.m, j.nm, vc, false);
            }
            source.endBatch(type);
            JOBS.clear();
        }
    }
}
