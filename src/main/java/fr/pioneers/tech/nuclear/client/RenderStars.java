package fr.pioneers.tech.nuclear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.NbtAnims;
import fr.pioneers.tech.industry.star.GeneratedStars;
import fr.pioneers.tech.industry.star.Stars;
import it.unimi.dsi.fastutil.longs.Long2FloatOpenHashMap;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 1.0.7 (lot 12) : rendu anime des MULTIBLOCS EXTRAORDINAIRES (★), au coeur de leur structure sculptee, vu a travers son verre ; une sorte
 * par extraordinaire (table {@link GeneratedStars}, source T:au_dela/multiblocs.py STARS) :
 * <ul>
 *     <li>plasma (forge heliothermique) : creuset de plasma qui bat, couronne, huit rayons des heliostats qui convergent ;</li>
 *     <li>anvil (presse a enclume de diamant) : point comprime entre les pointes, ondes de choc a chaque cycle ;</li>
 *     <li>ring (accelerateur annulaire) : paquets de particules qui filent dans l'anneau, eclairs de collision aux detecteurs ;</li>
 *     <li>qubits (fonderie) : reseau de qubits qui s'intriquent par vagues, brume froide ;</li>
 *     <li>fusion (coeur aneutronique) : anneau de plasma dans le tore, anneaux de conversion qui s'allument ;</li>
 *     <li>dark (confineur) : voile violet en rotation, poussieres qui spiralent vers le centre, anneau de lentille ;</li>
 *     <li>pulse (forge degeneree) : frappe au rythme du pulsar, rayons des pylones vers l'enclume ;</li>
 *     <li>singularity (injecteur) : disque d'accretion incline, anneau de photons, jets, lests qui plongent depuis les canons.</li>
 * </ul>
 * Rendu additif ({@code eyes} : l'intensite est dans la couleur), une passe par texture (jamais entrelacees) ; au repos une veille faible,
 * en marche l'effet entier (activite lissee). Aucune allocation par image hors de la premiere vue d'un controleur.
 */
final class RenderStars {

    private static final int FULL = RenderReactorCore.FULL;
    /** Activite lissee par controleur (fil du rendu). */
    private static final Long2FloatOpenHashMap LEVEL = new Long2FloatOpenHashMap();
    private static final double[] P = new double[3];

    private RenderStars() {
    }

    static void render(ControllerTile tile, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        GeneratedStars.Star star = Stars.of(tile.machine.key);
        GeneratedNbtShapes.Shape s = GeneratedNbtShapes.of(tile.machine.key);
        BlockState state = tile.getBlockState();
        if (star == null || s == null || !state.hasProperty(ControllerBlock.FACING) || !state.hasProperty(IndustryStyle.TIER)
            || state.getValue(IndustryStyle.TIER) <= 0) {
            return;
        }
        BlockPos c = tile.getBlockPos();
        Direction front = state.getValue(ControllerBlock.FACING);
        boolean active = Stars.forceActive || state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
        long key = c.asLong();
        float act = LEVEL.getOrDefault(key, active ? 1F : 0F);
        act += ((active ? 1F : 0F) - act) * 0.04F;
        LEVEL.put(key, act);
        float t = (float) (RenderClock.time(partialTick) % 240_000.0);
        double[] f = NbtAnims.point(c, front, s.cx(), s.cy(), s.cz(), star.fx(), star.fy(), star.fz());
        float x = (float) (f[0] - c.getX()), y = (float) (f[1] - c.getY()), z = (float) (f[2] - c.getZ());
        Camera cam = Minecraft.getInstance().gameRenderer.getMainCamera();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        Matrix4f m = pose.last().pose();
        float r = (star.accent() >> 16 & 255) / 255F, g = (star.accent() >> 8 & 255) / 255F, b = (star.accent() & 255) / 255F;
        float k = 0.35F + 0.95F * act;
        Ctx ctx = new Ctx(c, front, s, m, buffers, left, up, x, y, z, star.scale(), t, act, k, r, g, b);
        //halo commun : la lueur de l'effet qui eclaire le verre de la structure (plus vive en marche)
        float halo = 0.18F + 0.42F * act;
        CoreDraw.billboard(glow(ctx), m, x, y, z, Math.max(2.5F, star.scale() * 1.6F), left, up, r * halo, g * halo, b * halo, FULL);
        switch (star.render()) {
            case 0 -> plasma(ctx);
            case 1 -> anvil(ctx);
            case 2 -> ring(ctx);
            case 3 -> qubits(ctx);
            case 4 -> fusion(ctx);
            case 5 -> dark(ctx);
            case 6 -> pulse(ctx);
            default -> singularity(ctx);
        }
    }

    /** Etat d'une image d'un extraordinaire. */
    private record Ctx(BlockPos c, Direction front, GeneratedNbtShapes.Shape s, Matrix4f m, MultiBufferSource buffers, Vector3f left, Vector3f up,
                       float x, float y, float z, float scale, float t, float act, float k, float r, float g, float b) {

        /** Point du gabarit (cases) dans le repere du controleur, dans {@link #P}. */
        double[] at(double sx, double sy, double sz) {
            double[] p = NbtAnims.point(c, front, s.cx(), s.cy(), s.cz(), sx, sy, sz);
            P[0] = p[0] - c.getX();
            P[1] = p[1] - c.getY();
            P[2] = p[2] - c.getZ();
            return P;
        }
    }

    private static VertexConsumer glow(Ctx x) {
        return x.buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
    }

    private static float frac(float v) {
        return v - (float) Math.floor(v);
    }

    // ------------------------------------------------------------------ forge a plasma heliothermique
    private static void plasma(Ctx c) {
        float beat = 0.8F + 0.2F * (float) Math.sin(c.t * 0.21F);
        VertexConsumer b = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.PLASMA));
        float i = c.k * beat;
        CoreDraw.sphere(b, c.m, c.x, c.y, c.z, c.scale * 0.85F * beat, 10, 14, 2, -c.t * 0.01F, i, 0.62F * i, 0.25F * i, 1, FULL, true);
        b = glow(c);
        CoreDraw.billboard(b, c.m, c.x, c.y, c.z, c.scale * 2.2F * beat, c.left, c.up, 0.9F * i, 0.45F * i, 0.15F * i, FULL);
        CoreDraw.billboard(b, c.m, c.x, c.y, c.z, c.scale * 0.7F, c.left, c.up, i, 0.95F * i, 0.8F * i, FULL);
        if (c.act > 0.02F) {
            //rayons des huit heliostats (sur la plate-forme, rayon 8,2 autour de l'axe de la tour)
            VertexConsumer a = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.ARC));
            for (int h = 0; h < 8; h++) {
                double ang = h * Math.PI / 4;
                double[] p = c.at(11.5 + 8.2 * Math.cos(ang), 6.0, 11.5 + 8.2 * Math.sin(ang));
                float px = (float) p[0], py = (float) p[1], pz = (float) p[2];
                float w = 0.12F + 0.05F * (float) Math.sin(c.t * 0.3F + h);
                CoreDraw.ribbon(a, c.m, new float[][]{{px, py, pz}, {c.x, c.y, c.z}}, w, cam(c), c.act, 0.8F * c.act, 0.45F * c.act, FULL);
            }
        }
    }

    /** Camera dans le repere du controleur. */
    private static Vector3f cam(Ctx c) {
        net.minecraft.world.phys.Vec3 p = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        return new Vector3f((float) (p.x - c.c.getX()), (float) (p.y - c.c.getY()), (float) (p.z - c.c.getZ()));
    }

    // ------------------------------------------------------------------ presse a enclume de diamant
    private static void anvil(Ctx c) {
        float cycle = frac(c.t / 80F);
        float press = cycle < 0.6F ? cycle / 0.6F : 1 - (cycle - 0.6F) / 0.4F;
        float i = c.k * (0.3F + 0.7F * press * press);
        VertexConsumer b = glow(c);
        CoreDraw.billboard(b, c.m, c.x, c.y, c.z, c.scale * (0.3F + 0.5F * press), c.left, c.up, i, i, i, FULL);
        CoreDraw.billboard(b, c.m, c.x, c.y, c.z, c.scale * 1.6F * press, c.left, c.up, 0.4F * i, 0.6F * i, 0.9F * i, FULL);
        if (c.act > 0.02F && cycle > 0.6F) {
            //onde de choc a la detente
            float w = (cycle - 0.6F) / 0.4F;
            VertexConsumer rg = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.RING));
            float a = c.act * (1 - w);
            CoreDraw.ring(rg, c.m, c.x, c.y, c.z, 1 + 6 * w, 0.25F, 0, 0, 0, 32, 0.6F * a, 0.8F * a, a, FULL);
        }
    }

    // ------------------------------------------------------------------ accelerateur annulaire
    private static void ring(Ctx c) {
        VertexConsumer rg = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.RING));
        float i = c.k;
        CoreDraw.ring(rg, c.m, c.x, c.y, c.z, c.scale, 0.6F, 0, 0, -c.t * 0.05F, 64, 0.3F * i, 0.6F * i, i, FULL);
        VertexConsumer b = glow(c);
        int bunches = c.act > 0.02F ? 6 : 2;
        for (int n = 0; n < bunches; n++) {
            double ang = (n * 2 * Math.PI / bunches) + c.t * (0.02 + 0.13 * c.act) * (n % 2 == 0 ? 1 : -1);
            for (int tail = 0; tail < 5; tail++) {
                double a = ang - tail * 0.06 * (n % 2 == 0 ? 1 : -1);
                float px = c.x + (float) Math.cos(a) * c.scale, pz = c.z + (float) Math.sin(a) * c.scale;
                float f = c.k * (1 - tail / 5F);
                CoreDraw.billboard(b, c.m, px, c.y, pz, 0.7F - tail * 0.1F, c.left, c.up, 0.5F * f, 0.8F * f, f, FULL);
            }
        }
        if (c.act > 0.02F) {
            //eclairs de collision aux quatre detecteurs
            for (int d = 0; d < 4; d++) {
                float flash = (float) Math.pow(Math.max(0, Math.sin(c.t * 0.11F + d * 1.7F)), 24) * c.act;
                if (flash > 0.02F) {
                    double a = d * Math.PI / 2;
                    CoreDraw.billboard(b, c.m, c.x + (float) Math.cos(a) * c.scale, c.y, c.z + (float) Math.sin(a) * c.scale, 1.6F * flash, c.left,
                          c.up, flash, flash, 0.8F * flash, FULL);
                }
            }
        }
    }

    // ------------------------------------------------------------------ fonderie de qubits
    private static void qubits(Ctx c) {
        VertexConsumer b = glow(c);
        for (int ix = -2; ix <= 2; ix++) {
            for (int iz = -2; iz <= 2; iz++) {
                float wave = 0.5F + 0.5F * (float) Math.sin(c.t * 0.15F - (ix + iz) * 0.9F);
                float i = c.k * (0.3F + 0.7F * wave * c.act + 0.2F);
                CoreDraw.billboard(b, c.m, c.x + ix * 0.32F, c.y + 0.6F, c.z + iz * 0.32F, 0.16F + 0.08F * wave, c.left, c.up, 0.5F * i, 0.85F * i, i,
                      FULL);
            }
        }
        //etages du cryostat : halos froids qui descendent en marche
        VertexConsumer fr = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.FROST));
        for (int st = 0; st < 5; st++) {
            float yy = c.y + 2 + st * 3;
            float i = c.k * (0.25F + 0.35F * (float) Math.sin(c.t * 0.05F - st));
            CoreDraw.disc(fr, c.m, c.x, yy, c.z, 2.0F + st * 0.8F, 20, 4, c.t * 0.002F * (st + 1), 0.3F * i, 0.6F * i, 0.9F * i, 1, FULL);
        }
    }

    // ------------------------------------------------------------------ coeur de fusion aneutronique
    private static void fusion(Ctx c) {
        VertexConsumer pl = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.PLASMA));
        float i = c.k;
        for (int band = 0; band < 3; band++) {
            float w = 1.3F - band * 0.3F;
            CoreDraw.ring(pl, c.m, c.x, c.y, c.z, c.scale, w, 0, band * 0.7F, -c.t * (0.03F + band * 0.02F), 64, i, 0.75F * i, 0.35F * i, FULL);
        }
        VertexConsumer rg = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.RING));
        for (int n = 0; n < 4; n++) {
            double a = n * Math.PI / 2 + Math.PI / 4;
            float px = c.x + (float) Math.cos(a) * c.scale, pz = c.z + (float) Math.sin(a) * c.scale;
            float on = c.act * (0.5F + 0.5F * (float) Math.sin(c.t * 0.12F + n * 1.5F));
            //anneau de conversion dresse autour du tube du tore
            CoreDraw.ring(rg, c.m, px, c.y, pz, 3.6F, 0.3F, (float) (Math.PI / 2), (float) (a + Math.PI / 2), c.t * 0.04F, 24, on, 0.9F * on,
                  0.5F * on, FULL);
        }
    }

    // ------------------------------------------------------------------ confineur de matiere noire
    private static void dark(Ctx c) {
        VertexConsumer h = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.HAZE));
        float i = c.k * 0.6F;
        CoreDraw.sphere(h, c.m, c.x, c.y, c.z, c.scale * 0.9F, 10, 16, 3, c.t * 0.004F, 0.45F * i, 0.25F * i, 0.8F * i, 1, FULL, true);
        VertexConsumer b = glow(c);
        int n = c.act > 0.02F ? 40 : 12;
        for (int p = 0; p < n; p++) {
            float u = frac(c.t * 0.004F + (float) CoreDraw.hash(p, 1));
            double a = 2 * Math.PI * CoreDraw.hash(p, 2) + u * 9;
            float rr = c.scale * (1 - u) * 0.95F, yy = (float) (CoreDraw.hash(p, 3) - 0.5) * c.scale * (1 - u);
            float f = c.k * (float) Math.sin(Math.PI * u) * 0.8F;
            CoreDraw.billboard(b, c.m, c.x + (float) Math.cos(a) * rr, c.y + yy, c.z + (float) Math.sin(a) * rr, 0.16F, c.left, c.up, 0.6F * f, 0.4F * f,
                  f, FULL);
        }
        VertexConsumer rg = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.RING));
        CoreDraw.ring(rg, c.m, c.x, c.y, c.z, c.scale * 0.55F, 0.08F, 0.4F, c.t * 0.01F, c.t * 0.02F, 40, 0.8F * c.k, 0.7F * c.k, c.k, FULL);
    }

    // ------------------------------------------------------------------ forge de matiere degeneree
    private static void pulse(Ctx c) {
        //frappe au rythme du pulsar : une periode de 20 ticks en marche
        float phase = frac(c.t / 20F);
        float hit = c.act * (float) Math.pow(1 - phase, 6);
        VertexConsumer b = glow(c);
        CoreDraw.billboard(b, c.m, c.x, c.y, c.z, c.scale * (0.6F + 2.4F * hit), c.left, c.up, 0.5F * c.k + hit, 0.7F * c.k + hit, c.k + hit, FULL);
        if (c.act > 0.02F) {
            VertexConsumer a = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.ARC));
            Vector3f camera = cam(c);
            int[][] tips = {{4, 13, 2}, {16, 13, 2}, {4, 13, 12}, {16, 13, 12}};
            for (int[] tp : tips) {
                double[] p = c.at(tp[0] + 0.5, tp[1] + 0.5, tp[2] + 0.5);
                float w = 0.08F + 0.2F * hit;
                CoreDraw.ribbon(a, c.m, new float[][]{{(float) p[0], (float) p[1], (float) p[2]}, {c.x, c.y, c.z}}, w, camera, 0.5F * c.act + hit,
                      0.8F * c.act + hit, c.act + hit, FULL);
            }
        }
    }

    // ------------------------------------------------------------------ injecteur de singularite
    private static void singularity(Ctx c) {
        VertexConsumer pl = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.PLASMA));
        float i = c.k;
        float tilt = (float) Math.toRadians(25);
        for (int band = 0; band < 4; band++) {
            float rad = c.scale * (0.55F + band * 0.18F);
            float heat = 1 - band / 4F;
            CoreDraw.ring(pl, c.m, c.x, c.y, c.z, rad, 0.4F, tilt, 0, -c.t * (0.05F / (1 + band)), 48, i * heat, 0.55F * i * heat,
                  (0.4F + 0.5F * heat) * i * 0.8F, FULL);
        }
        VertexConsumer b = glow(c);
        CoreDraw.billboard(b, c.m, c.x, c.y, c.z, c.scale * 0.5F, c.left, c.up, 0.9F * i, 0.8F * i, 0.6F * i, FULL);
        if (c.act > 0.02F) {
            //jets aux poles et lests qui plongent depuis les trois canons
            VertexConsumer h = c.buffers.getBuffer(RenderType.eyes(RenderReactorCore.HAZE));
            CoreDraw.cylinder(h, c.m, c.x, c.z, 0.35F, c.y + 1, c.y + 7, 10, 1, 2, -c.t * 0.03F, 0.5F * c.act, 0.4F * c.act, c.act, 1, FULL);
            CoreDraw.cylinder(h, c.m, c.x, c.z, 0.35F, c.y - 7, c.y - 1, 10, 1, 2, c.t * 0.03F, 0.5F * c.act, 0.4F * c.act, c.act, 1, FULL);
            for (int k = 0; k < 3; k++) {
                double a = 2 * Math.PI * k / 3 + Math.PI / 2;
                double[] p = c.at(11.5 + 5.2 * Math.cos(a), 13.5, 11.5 + 5.2 * Math.sin(a));
                float px = (float) p[0], py = (float) p[1], pz = (float) p[2];
                float u = frac(c.t * 0.02F + k / 3F);
                CoreDraw.billboard(b, c.m, px + (c.x - px) * u, py + (c.y - py) * u, pz + (c.z - pz) * u, 0.45F, c.left, c.up, c.act, 0.6F * c.act,
                      0.9F * c.act, FULL);
            }
        }
    }
}
