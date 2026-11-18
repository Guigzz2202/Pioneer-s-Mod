package fr.pioneers.tech.industry.client.big;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.multi.BigInterior;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.NbtAnims;
import fr.pioneers.core.registries.PiParticles;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.ParticleStatus;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.19.14 : rendu d'une machine interieure d'un seul tenant ({@link BigInterior}), appele par le rendu du controleur.
 * <ul>
 *     <li>Maillage ({@link BigMachines#build}) calcule une fois par structure (signature de la vue), ranges par materiau puis par groupe ;</li>
 *     <li>regime : vise 1 quand le controleur est ACTIVE (il le reste 1 s apres la derniere operation), 0 sinon ; monte et descend
 *     lineairement en {@link BigInterior.Kind#up}/{@link BigInterior.Kind#down} ticks (jamais de tout ou rien) ; a la premiere image, le
 *     regime part directement de sa cible (une machine deja en marche quand on arrive ne redemarre pas) ;</li>
 *     <li>phase : integree tick par tick (+ fraction d'image) au regime lisse ; la bande du laminoir avance d'autant (entree remplie tant
 *     que la machine travaille : elle s'engage puis sort progressivement) ;</li>
 *     <li>lumiere : la plus forte des cases exterieures voisines (devant le controleur, au-dessus de la structure) ; lueur par quad.</li>
 * </ul>
 * Une passe par materiau (jamais deux tampons entrelaces) ; verre en dernier.
 */
public final class RenderBigMachine {

    private static final class Entry {

        long signature = Long.MIN_VALUE, tick = Long.MIN_VALUE, seen, lightTick = Long.MIN_VALUE;
        int light;
        final BigMachines.Anim anim = new BigMachines.Anim();
        final Map<BigMat, List<List<BigMesh.Quad>>> buckets = new EnumMap<>(BigMat.class);
        List<BigMesh.Group> groups = List.of();
        /** 1.0.4-beta (lot 25) : mise a l'echelle dans la boite ({@link BigMachines#fit}) ; null : dessin tel quel (gabarit d'avant). */
        float[] fit;
    }

    private static final Map<Long, Entry> CACHE = new HashMap<>();
    private static final Map<BigMat, ResourceLocation> TEXTURES = new EnumMap<>(BigMat.class);
    /** 0.20.46 (audit) : copie unique de {@code BigMat.values()} (chaque appel clone le tableau). */
    private static final BigMat[] MATS = BigMat.values();
    /** 0.20.46 (audit) : niveau de detail selon la distance de la camera a la boite des modules : sans partie dynamique au-dela de 40 blocs,
     * sans machine interieure du tout au-dela de 64 (le regime et le panache de la tour continuent). */
    private static final double DYNAMIC_RANGE = 96, INTERIOR_RANGE = 96; //la partie « dynamique » porte aussi des pieces de structure (chevalet, liquide, bande) : pas de coupure visible avant la distance de rendu
    /** 0.20.46 (audit) : objets de travail reutilises d'une image a l'autre (fil de rendu seulement) : partie dynamique, image d'animation,
     * quads dynamiques par materiau, matrices des groupes. */
    private static final BigMesh DYN = new BigMesh();
    private static final BigMachines.Anim FRAME = new BigMachines.Anim();
    @SuppressWarnings("unchecked")
    private static final List<BigMesh.Quad>[] BY_MAT = new List[MATS.length];
    private static Matrix4f[] mats = new Matrix4f[0];
    private static Matrix3f[] norms = new Matrix3f[0];
    private static final BlockPos.MutableBlockPos PROBE = new BlockPos.MutableBlockPos();
    private static final Direction[] HORIZONTAL = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};

    static {
        for (BigMat m : BigMat.values()) {
            TEXTURES.put(m, Pioneers.rl("textures/" + m.sprite() + ".png"));
        }
        for (int i = 0; i < BY_MAT.length; i++) {
            BY_MAT[i] = new ArrayList<>();
        }
    }

    private RenderBigMachine() {
    }

    public static void render(ControllerTile tile, BigInterior.View view, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        BigInterior.Kind kind = view.kind();
        float w = view.width(), h = view.height(), l = view.length();
        Entry e = entry(tile.getBlockPos(), RenderClock.clientTicks());   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        if (e.signature != view.signature()) {
            rebuild(e, BigMachines.build(kind, w, h, l, view.stands(), view.profile(), view.tint()));
            e.signature = view.signature();
            //1.0.4-beta (lot 25) : structure de Guillaume : la machine est ramenee DANS sa boite (rien ne depasse), une fois par vue
            e.fit = NbtAnims.of(tile.machine.key) == null ? null : BigMachines.fit(BigMachines.bounds(kind, w, h, l, view.stands(),
                  view.profile(), view.tint(), BigMachines.FIT_ANGLES, BigMachines.FIT_PHASES), w, h, l);
        }
        // --- regime et phase
        BlockState state = tile.getBlockState();
        boolean active = state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
        BigMachines.Anim a = e.anim;
        long now = RenderClock.clientTicks();
        if (e.tick == Long.MIN_VALUE) {
            a.speed = active ? 1 : 0;
            if (kind == BigInterior.Kind.ROLLING && active) {
                //Machine deja en marche : bande deja engagee sur toute la ligne
                BigMachines.feedStrip(a, l, 0, true);
                Arrays.fill(a.strip, true);
            }
            e.tick = now;
        }
        long steps = Math.min(200, Math.max(0, now - e.tick));
        for (long i = 0; i < steps; i++) {
            float target = active ? 1 : 0;
            a.speed = target > a.speed ? Math.min(target, a.speed + 1F / kind.up) : Math.max(target, a.speed - 1F / kind.down);
            float v = ease(a.speed);
            a.phase = (a.phase + v) % 36_000F;
            if (kind == BigInterior.Kind.ROLLING) {
                BigMachines.feedStrip(a, l, v * BigMachines.STRIP_SPEED, active);
            }
            if (kind == BigInterior.Kind.COOLTOWER && i == steps - 1) {
                plume(level, view, v);
            }
        }
        e.tick = now;
        //0.20.46 (audit) : niveau de detail selon la distance (point de la boite des modules le plus proche de la camera)
        BlockPos min = view.min(), max = view.max();
        Vec3 cam = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        double dx = Math.max(0, Math.max(min.getX() - cam.x, cam.x - (max.getX() + 1)));
        double dy = Math.max(0, Math.max(min.getY() - cam.y, cam.y - (max.getY() + 1)));
        double dz = Math.max(0, Math.max(min.getZ() - cam.z, cam.z - (max.getZ() + 1)));
        double dist2 = dx * dx + dy * dy + dz * dz;
        if (dist2 > INTERIOR_RANGE * INTERIOR_RANGE) {
            return;
        }
        float phase = a.phase + ease(a.speed) * partialTick;
        // --- partie dynamique de cette image (objets reutilises)
        BigMesh dyn = DYN.reset();
        if (dist2 <= DYNAMIC_RANGE * DYNAMIC_RANGE) {
            BigMachines.Anim frame = FRAME;
            frame.speed = a.speed;
            frame.phase = phase;
            frame.strip = a.strip;
            frame.shift = a.shift + (kind == BigInterior.Kind.ROLLING ? Math.min(3.99F - a.shift, ease(a.speed) * BigMachines.STRIP_SPEED * partialTick) : 0);
            BigMachines.dynamic(dyn, kind, w, h, l, view.stands(), view.profile(), view.tint(), frame);
        }
        // --- repere : coin bas des modules, px, axe long sur z
        BlockPos c = tile.getBlockPos();
        pose.pushPose();
        pose.translate(min.getX() - c.getX(), min.getY() - c.getY(), min.getZ() - c.getZ());
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        if (view.alongX()) {
            //canonique (x, y, z) -> monde (z, y, W - x) : rotation de 90 degres autour de Y
            pose.translate(0, 0, w);
            pose.mulPose(Axis.YP.rotationDegrees(90));
        }
        float[] fit = e.fit;
        if (fit != null && (fit[0] != 1 || fit[1] != 1 || fit[2] != 0 || fit[3] != 0 || fit[4] != 0)) {
            pose.translate(fit[2], fit[3], fit[4]);
            pose.scale(fit[0], fit[1], fit[0]);
        }
        if (e.lightTick != now) {
            //0.20.46 (audit) : lumiere relue une fois par tick
            e.light = light(level, tile, view);
            e.lightTick = now;
        }
        int light = e.light;
        int groupCount = e.groups.size();
        if (mats.length < groupCount) {
            Matrix4f[] m2 = Arrays.copyOf(mats, groupCount);
            Matrix3f[] n2 = Arrays.copyOf(norms, groupCount);
            for (int g = mats.length; g < groupCount; g++) {
                m2[g] = new Matrix4f();
                n2[g] = new Matrix3f();
            }
            mats = m2;
            norms = n2;
        }
        for (int g = 0; g < groupCount; g++) {
            BigMesh.Group gr = e.groups.get(g);
            pose.pushPose();
            if (g > 0 && gr.factor() != 0) {
                float ang = phase * gr.factor();
                pose.translate(gr.px(), gr.py(), gr.pz());
                pose.mulPose(switch (gr.axis()) {
                    case 'x' -> Axis.XP.rotationDegrees(ang);
                    case 'y' -> Axis.YP.rotationDegrees(ang);
                    default -> Axis.ZP.rotationDegrees(ang);
                });
                pose.translate(-gr.px(), -gr.py(), -gr.pz());
            }
            mats[g].set(pose.last().pose());
            norms[g].set(pose.last().normal());
            pose.popPose();
        }
        List<BigMesh.Quad>[] dynamic = BY_MAT;
        for (List<BigMesh.Quad> list : dynamic) {
            list.clear();
        }
        for (BigMesh.Quad q : dyn.quads) {
            dynamic[q.mat.ordinal()].add(q);
        }
        for (int pass = 0; pass < 2; pass++) {
            for (BigMat mat : MATS) {
                if (mat.translucent() != (pass == 1)) {
                    continue;
                }
                List<List<BigMesh.Quad>> byGroup = e.buckets.get(mat);
                List<BigMesh.Quad> extra = dynamic[mat.ordinal()];
                if (byGroup == null && extra.isEmpty()) {
                    continue;
                }
                ResourceLocation tex = TEXTURES.get(mat);
                VertexConsumer vc = buffers.getBuffer(mat.translucent() ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex));
                if (byGroup != null) {
                    for (int g = 0; g < byGroup.size(); g++) {
                        Matrix4f mg = mats[g];
                        Matrix3f ng = norms[g];
                        for (BigMesh.Quad q : byGroup.get(g)) {
                            emit(vc, mg, ng, q, light);
                        }
                    }
                }
                for (BigMesh.Quad q : extra) {
                    emit(vc, mats[0], norms[0], q, light);
                }
            }
        }
        pose.popPose();
    }

    /**
     * 0.20.26 : panache de la tour de refroidissement : jusqu'a 3 grosses bouffees par tick sorties du col (selon le regime et le reglage
     * des particules), visibles de loin. Appele une fois par tick de jeu, quand le controleur est dessine.
     */
    private static void plume(Level level, BigInterior.View view, float speed) {
        if (speed < 0.05F || view.stands().length == 0) {
            return;
        }
        ParticleStatus status = Minecraft.getInstance().options.particles().get();
        float rate = 3 * speed * (status == ParticleStatus.ALL ? 1 : status == ParticleStatus.DECREASED ? 0.5F : 0.2F);
        RandomSource rnd = level.random;
        int n = (int) rate + (rnd.nextFloat() < rate - (int) rate ? 1 : 0);
        BlockPos min = view.min(), max = view.max();
        double cx = (min.getX() + max.getX() + 1) / 2.0, cz = (min.getZ() + max.getZ() + 1) / 2.0;
        double top = min.getY() + view.stands()[0] / 16.0, rTop = (max.getX() - min.getX() + 2) / 2.0 * 0.66;
        for (int k = 0; k < n; k++) {
            double an = rnd.nextDouble() * Math.PI * 2, f = Math.sqrt(rnd.nextDouble());
            double ox = Math.cos(an) * f, oz = Math.sin(an) * f;
            level.addAlwaysVisibleParticle(PiParticles.STEAM_PLUME.get(), true, cx + ox * rTop * 0.8, top - 1.5, cz + oz * rTop * 0.8,
                  ox * 0.02, 0.16 + rnd.nextDouble() * 0.08, oz * 0.02);
        }
    }

    /**
     * 0.20.37 : dessine un maillage dynamique (sans groupes tournants) dans le repere courant (px), une passe par materiau, translucides en
     * dernier. Utilise par les rendus hors machine interieure (colonnes de la plate-forme offshore).
     */
    public static void drawMesh(BigMesh mesh, PoseStack pose, MultiBufferSource buffers, int light) {
        //0.20.46 (audit) : listes par materiau reutilisees (meme ordre que l'EnumMap d'avant : ordinal)
        List<BigMesh.Quad>[] byMat = BY_MAT;
        for (List<BigMesh.Quad> list : byMat) {
            list.clear();
        }
        for (BigMesh.Quad q : mesh.quads) {
            byMat[q.mat.ordinal()].add(q);
        }
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        for (int pass = 0; pass < 2; pass++) {
            for (BigMat mat : MATS) {
                List<BigMesh.Quad> quads = byMat[mat.ordinal()];
                if (quads.isEmpty() || mat.translucent() != (pass == 1)) {
                    continue;
                }
                ResourceLocation tex = TEXTURES.get(mat);
                VertexConsumer vc = buffers.getBuffer(mat.translucent() ? RenderType.entityTranslucent(tex) : RenderType.entityCutoutNoCull(tex));
                for (BigMesh.Quad q : quads) {
                    emit(vc, m, n, q, light);
                }
            }
        }
        for (List<BigMesh.Quad> list : byMat) {
            list.clear();
        }
    }

    /** Montee en regime adoucie (depart et arrivee en douceur). */
    private static float ease(float s) {
        return s * s * (3 - 2 * s);
    }

    private static void emit(VertexConsumer vc, Matrix4f m, Matrix3f n, BigMesh.Quad q, int light) {
        int lt = light;
        if (q.glow > 0) {
            int block = Math.max(LightTexture.block(light), q.glow);
            lt = LightTexture.pack(block, LightTexture.sky(light));
        }
        int a = q.color >>> 24 & 255, r = q.color >> 16 & 255, g = q.color >> 8 & 255, b = q.color & 255;
        for (int i = 0; i < 4; i++) {
            vc.vertex(m, q.pos[i * 3], q.pos[i * 3 + 1], q.pos[i * 3 + 2]).color(r, g, b, a).uv(q.uv[i * 2], q.uv[i * 2 + 1])
                  .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(lt).normal(n, q.nx, q.ny, q.nz).endVertex();
        }
    }

    /**
     * Lumiere : la plus forte des cases libres voisines de la structure (on voit la machine depuis l'exterieur, par le verre).
     * 0.20.46 (audit) : memes six cases, sans allocation (position de travail unique).
     */
    private static int light(Level level, ControllerTile tile, BigInterior.View view) {
        BlockPos c = tile.getBlockPos();
        int block = 0, sky = 0;
        BlockPos.MutableBlockPos p = PROBE;
        BlockState st = tile.getBlockState();
        if (st.hasProperty(MachineBlock.FACING)) {
            int lc = LevelRenderer.getLightColor(level, p.setWithOffset(c, st.getValue(MachineBlock.FACING)));
            block = Math.max(block, LightTexture.block(lc));
            sky = Math.max(sky, LightTexture.sky(lc));
        }
        BlockPos min = view.min(), max = view.max();
        int mx = (min.getX() + max.getX()) / 2, mz = (min.getZ() + max.getZ()) / 2;
        int lc = LevelRenderer.getLightColor(level, p.set(mx, max.getY() + 2, mz));
        block = Math.max(block, LightTexture.block(lc));
        sky = Math.max(sky, LightTexture.sky(lc));
        int my = (min.getY() + max.getY()) / 2, reach = Math.max(max.getX() - min.getX(), max.getZ() - min.getZ()) / 2 + 2;
        for (Direction d : HORIZONTAL) {
            lc = LevelRenderer.getLightColor(level, p.set(mx + d.getStepX() * reach, my, mz + d.getStepZ() * reach));
            block = Math.max(block, LightTexture.block(lc));
            sky = Math.max(sky, LightTexture.sky(lc));
        }
        return LightTexture.pack(Math.max(block, 4), sky);
    }

    private static void rebuild(Entry e, BigMesh mesh) {
        e.buckets.clear();
        e.groups = List.copyOf(mesh.groups);
        for (BigMesh.Quad q : mesh.quads) {
            List<List<BigMesh.Quad>> byGroup = e.buckets.computeIfAbsent(q.mat, k -> new ArrayList<>());
            while (byGroup.size() <= q.group) {
                byGroup.add(new ArrayList<>());
            }
            byGroup.get(q.group).add(q);
        }
    }

    private static Entry entry(BlockPos pos, long now) {
        if (CACHE.size() > 64) {
            //Menage : controleurs plus dessines depuis une minute
            Iterator<Entry> it = CACHE.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().seen > 1200) {
                    it.remove();
                }
            }
        }
        Entry e = CACHE.computeIfAbsent(pos.asLong(), k -> new Entry());
        if (now - e.seen > 100) {
            //Revu apres une absence : on repart de la cible (pas de rattrapage)
            e.tick = Long.MIN_VALUE;
        }
        e.seen = now;
        return e;
    }
}
