package fr.pioneers.tech.nuclear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.tech.compute.ComputeView;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Rendus animes du calcul (0.17.2), dessines par le controleur dans la structure ({@code ControllerTile#computeView()}) :
 * <ul>
 *     <li>centre de donnees : diodes des baies (sur les faces des modules tournees vers les allees) qui clignotent selon la charge, air
 *     chaud qui ondule sous le plafond et panaches au-dessus des baies (selon chaleur x charge) ;</li>
 *     <li>ordinateur quantique : lustre dore du refrigerateur a dilution qui pend du sommet (cinq plateaux etages, tirants, lignes coaxiales,
 *     chambre de melange), givre bleu autour des etages froids, qubits qui scintillent sur la puce, halo froid ; en decoherence (chaleur
 *     haute) : gresillement orange et extinction des qubits.</li>
 * </ul>
 * Les faces des baies sont relevees dans le monde toutes les 2 secondes (cache par controleur). Formules rejouees par
 * {@code tools/gc/nuclear/core_bench.py}.
 */
final class RenderCompute {

    /** Point d'ancrage d'une diode : centre de la face d'une baie, normale horizontale. */
    private record Anchor(float x, float y, float z, int nx, int nz, int seed) {
    }

    private static final class Cache {
        long scanned = Long.MIN_VALUE;
        long seen;
        final List<Anchor> faces = new ArrayList<>();
        final List<float[]> tops = new ArrayList<>();
    }

    private static final Map<Long, Cache> CACHE = new HashMap<>();
    private static final int MAX_FACES = 320, MAX_TOPS = 48;

    private RenderCompute() {
    }

    static void render(ControllerTile tile, ComputeView view, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        BlockPos c = tile.getBlockPos(), min = view.min(), max = view.max();
        float x0 = min.getX() + 1 - c.getX(), y0 = min.getY() + 1 - c.getY(), z0 = min.getZ() + 1 - c.getZ();
        float x1 = max.getX() - c.getX(), y1 = max.getY() - c.getY(), z1 = max.getZ() - c.getZ();
        if (x1 - x0 < 1 || y1 - y0 < 1 || z1 - z0 < 1) {
            return;
        }
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vec3 cp = cam.getPosition();
        Vector3f camera = new Vector3f((float) (cp.x - c.getX()), (float) (cp.y - c.getY()), (float) (cp.z - c.getZ()));
        float cx = (x0 + x1) * 0.5F, cy = (y0 + y1) * 0.5F, cz = (z0 + z1) * 0.5F;
        double dist = Math.sqrt((camera.x - cx) * (camera.x - cx) + (camera.y - cy) * (camera.y - cy) + (camera.z - cz) * (camera.z - cz));
        int lod = dist < 24 ? 2 : dist < 56 ? 1 : 0;
        float t = (float) (RenderClock.time(partialTick) % 240_000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        RenderReactorCore.Frame f = new RenderReactorCore.Frame(x0, y0, z0, x1, y1, z1, t, lod, camera, cam.getLeftVector(), cam.getUpVector(), null);
        Matrix4f m = pose.last().pose();
        ReactorLoopSound.update(c, view.kind() == 2 ? "nuclear.quantum_computer" : "nuclear.data_center", soundLevel(view),
              Vec3.atLowerCornerOf(c).add(cx, cy, cz));
        if (view.kind() == 2) {
            quantum(f, view, m, buffers);
        } else {
            dataCenter(level, c, f, view, m, buffers);
        }
    }

    /** Intensite du son d'ambiance (0 = silence). */
    static float soundLevel(ComputeView view) {
        if (view.units() <= 0) {
            return 0;
        }
        return view.kind() == 2 ? 0.35F + 0.4F * RenderReactorCore.clamp(1 - view.heat()) : 0.25F + 0.75F * RenderReactorCore.clamp(view.load());
    }

    // ------------------------------------------------------------------
    // Centre de donnees
    // ------------------------------------------------------------------

    private static Cache scan(Level level, BlockPos c, ComputeView view) {
        long key = c.asLong();
        Cache cache = CACHE.computeIfAbsent(key, k -> new Cache());
        long now = level.getGameTime();
        cache.seen = now;
        if (now - cache.scanned < 40 && now >= cache.scanned) {
            return cache;
        }
        cache.scanned = now;
        cache.faces.clear();
        cache.tops.clear();
        BlockPos min = view.min(), max = view.max();
        BlockPos.MutableBlockPos p = new BlockPos.MutableBlockPos(), q = new BlockPos.MutableBlockPos();
        outer:
        for (int y = min.getY() + 1; y < max.getY(); y++) {
            for (int x = min.getX() + 1; x < max.getX(); x++) {
                for (int z = min.getZ() + 1; z < max.getZ(); z++) {
                    p.set(x, y, z);
                    if (!level.isLoaded(p) || level.getBlockState(p).isAir()) {
                        continue;
                    }
                    for (Direction d : Direction.Plane.HORIZONTAL) {
                        q.setWithOffset(p, d);
                        if (cache.faces.size() < MAX_FACES && level.getBlockState(q).isAir()) {
                            cache.faces.add(new Anchor(x + 0.5F + d.getStepX() * 0.51F - c.getX(), y + 0.5F - c.getY(),
                                  z + 0.5F + d.getStepZ() * 0.51F - c.getZ(), d.getStepX(), d.getStepZ(), (x * 31 + y * 17 + z * 13) ^ d.ordinal()));
                        }
                    }
                    q.setWithOffset(p, Direction.UP);
                    if (cache.tops.size() < MAX_TOPS && level.getBlockState(q).isAir()) {
                        cache.tops.add(new float[]{x + 0.5F - c.getX(), y + 1.0F - c.getY(), z + 0.5F - c.getZ()});
                    }
                    if (cache.faces.size() >= MAX_FACES && cache.tops.size() >= MAX_TOPS) {
                        break outer;
                    }
                }
            }
        }
        if (CACHE.size() > 64) {
            for (Iterator<Cache> it = CACHE.values().iterator(); it.hasNext(); ) {
                if (now - it.next().seen > 2400) {
                    it.remove();
                }
            }
        }
        return cache;
    }

    private static void dataCenter(Level level, BlockPos c, RenderReactorCore.Frame f, ComputeView view, Matrix4f m, MultiBufferSource buffers) {
        float load = RenderReactorCore.clamp(view.load()), heat = RenderReactorCore.clamp(view.heat());
        float t = f.t();
        if (f.lod() == 0 && load < 0.01F) {
            return;
        }
        Cache cache = scan(level, c, view);
        // --- diodes : petites pastilles a plat sur les faces (2 colonnes x 3 rangees de pres, 2 de loin)
        VertexConsumer b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
        int faces = f.lod() == 2 ? cache.faces.size() : f.lod() == 1 ? Math.min(cache.faces.size(), 120) : 0;
        int per = f.lod() == 2 ? 6 : 2;
        int epoch = (int) Math.floor(t / 2);
        for (int i = 0; i < faces; i++) {
            Anchor a = cache.faces.get(i);
            for (int k = 0; k < per; k++) {
                int seed = a.seed() * 7 + k;
                // clignote d'autant plus souvent que la charge est haute ; quelques diodes ambre (disques) et bleues (reseau)
                double h = CoreDraw.hash(seed, epoch + (int) (CoreDraw.hash(seed, 1) * 9));
                boolean on = h < 0.25 + 0.7 * load;
                if (!on) {
                    continue;
                }
                double kind = CoreDraw.hash(seed, 2);
                float r = kind < 0.75 ? 0.25F : kind < 0.9 ? 1.0F : 0.3F, g = kind < 0.75 ? 1.0F : kind < 0.9 ? 0.65F : 0.6F,
                      bl = kind < 0.75 ? 0.4F : kind < 0.9 ? 0.15F : 1.0F;
                float i0 = 0.55F + 0.45F * (float) h;
                float col = (k % 2) * 0.22F - 0.11F, row = 0.32F - (k / 2) * 0.3F;
                float lx = a.x() + (a.nz() != 0 ? col : 0), lz = a.z() + (a.nx() != 0 ? col : 0), ly = a.y() + row;
                flat(b, m, lx, ly, lz, a.nx(), a.nz(), 0.035F, r * i0, g * i0, bl * i0);
            }
        }
        // --- air chaud : nappes qui ondulent sous le plafond, panaches au-dessus des baies
        float hot = heat * (0.3F + 0.7F * load);
        if (hot < 0.03F || f.lod() == 0) {
            return;
        }
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.HEAT));
        int nx = f.lod() == 2 ? 10 : 6, nz = f.lod() == 2 ? 10 : 6;
        for (int layer = 0; layer < 2; layer++) {
            float y = f.y1() - 0.3F - layer * 0.55F;
            float i = hot * (layer == 0 ? 0.32F : 0.2F);
            for (int ix = 0; ix < nx; ix++) {
                for (int iz = 0; iz < nz; iz++) {
                    float ax = f.x0() + (f.x1() - f.x0()) * ix / nx, bx = f.x0() + (f.x1() - f.x0()) * (ix + 1) / nx;
                    float az = f.z0() + (f.z1() - f.z0()) * iz / nz, bz = f.z0() + (f.z1() - f.z0()) * (iz + 1) / nz;
                    CoreDraw.quad(b, m, new float[][]{wave(ax, y, az, t, layer), wave(bx, y, az, t, layer), wave(bx, y, bz, t, layer),
                          wave(ax, y, bz, t, layer)}, i, 0.45F * i, 0.2F * i, 1, RenderReactorCore.FULL, true);
                }
            }
        }
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
        int puffs = Math.min(cache.tops.size(), f.lod() == 2 ? MAX_TOPS : 16);
        for (int i = 0; i < puffs; i++) {
            float[] top = cache.tops.get(i);
            for (int k = 0; k < 2; k++) {
                float ph = RenderReactorCore.frac(t * (0.006 + 0.004 * CoreDraw.hash(i * 3 + k, 4)) + CoreDraw.hash(i * 3 + k, 5));
                float fade = (float) Math.sin(Math.PI * ph) * hot * 0.35F;
                float s = 0.25F + 0.35F * ph;
                CoreDraw.billboard(b, m, top[0] + (float) (CoreDraw.hash(i, k + 6) - 0.5) * 0.4F, top[1] + ph * 1.6F,
                      top[2] + (float) (CoreDraw.hash(i, k + 8) - 0.5) * 0.4F, s, f.left(), f.up(), fade, 0.5F * fade, 0.25F * fade,
                      RenderReactorCore.FULL);
            }
        }
    }

    /** Point d'une nappe d'air chaud : ondulation lente, texture qui derive. */
    static float[] wave(float x, float y, float z, float t, int layer) {
        float dy = 0.12F * (float) (Math.sin(x * 0.9 + t * 0.05 + layer) * Math.cos(z * 0.7 - t * 0.04));
        return new float[]{x, y + dy, z, x * 0.25F + t * 0.002F * (layer + 1), z * 0.25F - t * 0.0015F};
    }

    /** Pastille plate de demi-taille s sur une face verticale de normale (nx, nz). */
    private static void flat(VertexConsumer b, Matrix4f m, float x, float y, float z, int nx, int nz, float s, float r, float g, float bl) {
        float ux = nz != 0 ? s : 0, uz = nx != 0 ? s : 0;
        CoreDraw.quad(b, m, new float[][]{{x - ux, y - s, z - uz, 0, 1}, {x + ux, y - s, z + uz, 1, 1}, {x + ux, y + s, z + uz, 1, 0},
              {x - ux, y + s, z - uz, 0, 0}}, r, g, bl, 1, RenderReactorCore.FULL, true);
    }

    // ------------------------------------------------------------------
    // Ordinateur quantique : lustre du refrigerateur a dilution
    // ------------------------------------------------------------------

    /** Etages du lustre : (part de la hauteur sous le sommet, rayon relatif) ; le dernier est la chambre de melange. */
    static final float[][] STAGES = {{0.04F, 0.62F}, {0.18F, 0.52F}, {0.32F, 0.43F}, {0.46F, 0.34F}, {0.6F, 0.26F}};

    private static void quantum(RenderReactorCore.Frame f, ComputeView view, Matrix4f m, MultiBufferSource buffers) {
        float load = RenderReactorCore.clamp(view.load()), heat = RenderReactorCore.clamp(view.heat());
        float cold = 1 - heat;
        float t = f.t();
        //1.0.4-beta (lot 25) : structure de Guillaume : le refrigerateur a dilution (vrai bloc) occupe le haut du centre : le lustre pend SOUS
        //lui (un bloc plus bas), dans la salle octogonale (rayons < 2,1 blocs : les vitres d'angle ne sont pas traversees)
        float cx = f.cx(), cz = f.cz(), top = f.y1() - 1, h = (top - f.y0()) * 0.9F;
        float rad = Math.min(f.x1() - f.x0(), f.z1() - f.z0()) * 0.5F;
        int seg = f.lod() == 2 ? 24 : 14;
        int light = LightTexture.pack(14, 12);
        // --- plateaux dores, tirants, lignes coaxiales, chambre de melange (plein)
        VertexConsumer b = buffers.getBuffer(RenderType.entityCutoutNoCull(RenderReactorCore.GOLD));
        float prevY = top, prevR = rad * 0.7F;
        for (int k = 0; k < STAGES.length; k++) {
            float y = top - h * STAGES[k][0], r = rad * STAGES[k][1], th = 0.08F + 0.02F * (STAGES.length - k);
            CoreDraw.cylinder(b, m, cx, cz, r, y - th, y, seg, 3, 0.25F, 0, 1, 0.92F, 0.75F, 1, light);
            CoreDraw.disc(b, m, cx, y, cz, r, seg, r * 2, 0, 1, 0.95F, 0.8F, 1, light);
            CoreDraw.disc(b, m, cx, y - th, cz, r, seg, r * 2, 0, 0.8F, 0.72F, 0.55F, 1, light);
            // tirants entre l'etage du dessus et celui-ci
            int rods = k == 0 ? 0 : 6;
            for (int j = 0; j < rods; j++) {
                double a = 2 * Math.PI * j / rods + k * 0.3;
                float rr = Math.min(prevR, r) * 0.82F;
                float px = cx + (float) Math.cos(a) * rr, pz = cz + (float) Math.sin(a) * rr;
                CoreDraw.box(b, m, px - 0.025F, y, pz - 0.025F, px + 0.025F, prevY - 0.1F, pz + 0.025F, 0.95F, 0.85F, 0.6F, light);
            }
            prevY = y - th;
            prevR = r;
        }
        // lignes coaxiales (fils de cuivre-argent) qui descendent du sommet jusqu'a la puce, legerement en spirale
        if (f.lod() > 0) {
            float bottom = top - h * 0.72F;
            int lines = f.lod() == 2 ? 8 : 4;
            for (int j = 0; j < lines; j++) {
                double a0 = 2 * Math.PI * j / lines;
                int steps = 6;
                for (int s = 0; s < steps; s++) {
                    float ya = top - (top - bottom) * s / steps, yb = top - (top - bottom) * (s + 1) / steps;
                    double a = a0 + s * 0.12;
                    float rr = rad * (0.2F - 0.12F * s / steps);
                    float px = cx + (float) Math.cos(a) * rr, pz = cz + (float) Math.sin(a) * rr;
                    CoreDraw.box(b, m, px - 0.015F, yb, pz - 0.015F, px + 0.015F, ya, pz + 0.015F, 0.85F, 0.8F, 0.78F, light);
                }
            }
        }
        float mixY = top - h * 0.6F;
        float chipY = top - h * 0.75F;
        CoreDraw.cylinder(b, m, cx, cz, rad * 0.12F, chipY, mixY - 0.1F, seg, 2, 0.4F, 0, 1, 0.9F, 0.72F, 1, light);
        CoreDraw.box(b, m, cx - rad * 0.16F, chipY - 0.08F, cz - rad * 0.16F, cx + rad * 0.16F, chipY, cz + rad * 0.16F, 0.25F, 0.3F, 0.4F, light);
        // --- givre bleu autour des etages froids, halo froid sous la puce
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.FROST));
        for (int k = 2; k < STAGES.length; k++) {
            float y = top - h * STAGES[k][0], r = rad * STAGES[k][1] * 1.08F;
            float i = cold * (0.15F + 0.12F * k) * (0.85F + 0.15F * (float) Math.sin(t * 0.07F + k));
            CoreDraw.cylinder(b, m, cx, cz, r, y - 0.35F, y + 0.05F, seg, 2, 1, t * 0.0015F, 0.45F * i, 0.75F * i, i, 1, RenderReactorCore.FULL);
        }
        b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
        float halo = cold * (0.25F + 0.2F * load);
        CoreDraw.billboard(b, m, cx, chipY - 0.1F, cz, rad * 0.8F, f.left(), f.up(), 0.35F * halo, 0.6F * halo, halo, RenderReactorCore.FULL);
        // --- qubits : points qui scintillent sur la puce (eteints en decoherence)
        float q = cold * cold * (0.2F + 0.8F * load);
        if (q > 0.02F && f.lod() > 0) {
            int n = f.lod() == 2 ? 16 : 8;
            for (int j = 0; j < n; j++) {
                float px = cx + (float) ((j % 4) - 1.5) * rad * 0.07F, pz = cz + (float) ((j / 4) - 1.5) * rad * 0.07F;
                float tw = (float) Math.pow(Math.max(0, Math.sin(t * (0.25 + 0.2 * CoreDraw.hash(j, 1)) + 2 * Math.PI * CoreDraw.hash(j, 2))), 3);
                float i = q * (0.3F + 0.7F * tw);
                CoreDraw.billboard(b, m, px, chipY - 0.1F, pz, 0.06F + 0.05F * tw, f.left(), f.up(), 0.55F * i, 0.8F * i, i, RenderReactorCore.FULL);
            }
        }
        // --- decoherence : gresillement orange pres de la puce
        if (heat > 0.6F && f.lod() > 0) {
            b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.ARC));
            int epoch = (int) Math.floor(t / 2);
            int arcs = 1 + (int) ((heat - 0.6F) * 8);
            for (int k = 0; k < arcs; k++) {
                int seed = epoch * 11 + k;
                float[][] pts = new float[5][];
                for (int j = 0; j < 5; j++) {
                    pts[j] = new float[]{cx + (float) (CoreDraw.hash(seed, j) - 0.5) * rad * 0.5F, chipY + (float) (CoreDraw.hash(seed, j + 10) - 0.3)
                          * h * 0.25F, cz + (float) (CoreDraw.hash(seed, j + 20) - 0.5) * rad * 0.5F};
                }
                float i = (heat - 0.5F) * 1.6F;
                CoreDraw.ribbon(b, m, pts, 0.03F, f.camera(), i, 0.55F * i, 0.2F * i, RenderReactorCore.FULL);
            }
        }
    }
}
