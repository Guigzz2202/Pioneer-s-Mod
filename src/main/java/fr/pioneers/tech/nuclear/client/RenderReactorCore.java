package fr.pioneers.tech.nuclear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.industry.client.big.NbtAnimations;
import fr.pioneers.tech.industry.client.big.RenderBigMachine;
import fr.pioneers.tech.industry.client.big.RenderOffshore;
import fr.pioneers.tech.compute.ComputeView;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tech.industry.multi.BigInterior;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.OffshoreRisers;
import fr.pioneers.tech.nuclear.ReactorView;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * Coeur anime des reacteurs (0.17.1), dessine DANS la structure par le controleur et visible a travers le verre et les hublots :
 * <ul>
 *     <li>MKI : voile Tcherenkov bleu qui pulse avec la puissance, lueur au fond de la cuve, bulles qui montent, eau translucide (1.0 : les
 *     barres et les canaux sont de vrais blocs) ;</li>
 *     <li>MKII : bain de sodium argente qui ondule (niveau = charge de sodium), convection orange qui monte du coeur, etincelles a la
 *     surface, dessous du bouchon tournant et barres plongees ;</li>
 *     <li>MKIII : plasma poussiereux violet et cyan en couches qui tournent, poussieres en orbites inclinees, anneaux magnetiques qui
 *     tournent, arcs electriques vers les bobines de l'equateur et le collecteur du sommet ; clignotement rouge quand le champ tombe ;</li>
 *     <li>0.17.2 : fusion (tokamak, stellarator) dans {@link RenderFusion} ; centre de donnees et ordinateur quantique ({@code computeView})
 *     dans {@link RenderCompute}.</li>
 * </ul>
 * Rendu additif ({@code eyes} : l'intensite est dans la couleur), translucide emissif (sodium) et plein (barres) ; une passe par type de
 * rendu, jamais entrelacees. Niveau de detail selon la distance : pas de particules au-dela de 56 blocs. Formules rejouees hors jeu par
 * {@code tools/gc/nuclear/core_bench.py}.
 */
public class RenderReactorCore implements BlockEntityRenderer<ControllerTile> {

    static final ResourceLocation GLOW = fx("glow"), HAZE = fx("haze"), SODIUM = fx("sodium"), ROD = fx("rod"), PLATE = fx("plate"),
          PLASMA = fx("plasma"), PLASMA_BODY = fx("plasma_body"), RING = fx("ring"), ARC = fx("arc"), GOLD = fx("gold"), FROST = fx("frost"), FILAMENT = fx("filament"),
          HEAT = fx("heat"), WATER = fx("water"), CORIUM = fx("corium");
    static final int FULL = LightTexture.FULL_BRIGHT;

    static ResourceLocation fx(String name) {
        return Pioneers.rl("textures/block/nuclear/fx/" + name + ".png");
    }

    public RenderReactorCore(BlockEntityRendererProvider.Context context) {
    }

    /** Bornes interieures de la structure, en coordonnees du controleur, et l'etat commun a une image. */
    record Frame(float x0, float y0, float z0, float x1, float y1, float z1, float t, int lod, Vector3f camera, Vector3f left, Vector3f up,
                         ReactorView view) {

        float cx() {
            return (x0 + x1) * 0.5F;
        }

        float cy() {
            return (y0 + y1) * 0.5F;
        }

        float cz() {
            return (z0 + z1) * 0.5F;
        }
    }

    @Override
    public void render(@NotNull ControllerTile tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        ReactorView view = tile.reactorView();
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        BigInterior.View big = tile.bigView();
        if (big != null) {
            //0.19.14 : machine interieure d'un seul tenant (turbine, laminoir, photobioreacteur, concasseur...)
            RenderBigMachine.render(tile, big, partialTick, pose, buffers);
        }
        //1.0.4-beta (lot 25) : structures de Guillaume : colonnes qui tournent, manivelles, particules en marche ; ecran du controleur en marche
        NbtAnimations.render(tile, partialTick, pose, buffers);
        //1.0.7 (lot 12) : multibloc extraordinaire (★) : son effet anime au coeur de la structure
        RenderStars.render(tile, partialTick, pose, buffers);
        ControllerScreens.render(tile, partialTick, pose, buffers);
        OffshoreRisers.View offshore = tile.offshoreView();
        if (offshore != null) {
            //0.20.37 : plate-forme offshore : colonnes, tubes descendus, tetes de puits
            RenderOffshore.render(tile, offshore, partialTick, pose, buffers);
        }
        if (view == null) {
            //0.17.2 : centre de donnees et ordinateur quantique
            ComputeView cv = tile.computeView();
            if (cv != null) {
                RenderCompute.render(tile, cv, partialTick, pose, buffers);
            }
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
        float time = (float) (RenderClock.time(partialTick) % 240_000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        Frame f = new Frame(x0, y0, z0, x1, y1, z1, time, lod, camera, cam.getLeftVector(), cam.getUpVector(), view);
        ReactorLoopSound.update(tile.getBlockPos(), view, Vec3.atLowerCornerOf(c).add(cx, cy, cz));
        Matrix4f m = pose.last().pose();
        switch (view.mark()) {
            case 2 -> mk2(f, m, buffers);
            case 3 -> mk3(f, m, buffers);
            case 4 -> RenderFusion.tokamak(f, m, buffers, tile.getBlockPos());
            case 5 -> RenderFusion.stellarator(f, m, buffers, tile.getBlockPos());
            default -> mk1(f, m, buffers);
        }
    }

    // ------------------------------------------------------------------
    // MKI : eau pressurisee
    // ------------------------------------------------------------------

    private static void mk1(Frame f, Matrix4f m, MultiBufferSource buffers) {
        ReactorView v = f.view;
        float p = clamp(v.power()), rods = clamp(v.rods()), heat = Math.max(0, v.heat());
        float h = f.y1 - f.y0, rad = inscribed(f);
        float cx = f.cx(), cz = f.cz(), t = f.t;
        //1.0 (lot 3) : les barres et les canaux sont de vrais blocs (ReactorBlock) ; on ne dessine plus que la lumiere et l'eau
        VertexConsumer b;
        // --- voile Tcherenkov (3 cylindres emboites qui defilent vers le haut) et lueur du fond
        b = buffers.getBuffer(RenderType.eyes(HAZE));
        float[] share = {1.0F, 0.6F, 0.35F}, radius = {0.33F, 0.62F, 0.92F};
        int seg = f.lod == 2 ? 20 : 12;
        float hot = clamp((heat - 0.9F) * 4);
        for (int k = 0; k < 3; k++) {
            float i = (0.12F + 0.88F * p) * (0.75F + 0.25F * (float) Math.sin(t * 0.12F + k * 2.1F)) * share[k];
            float r = mix(0.18F, 0.6F, hot) * i, g = mix(0.48F, 0.5F, hot) * i, bl = i;
            CoreDraw.cylinder(b, m, cx, cz, rad * radius[k], f.y0, f.y0 + h * 0.78F, seg, 2, 1.5F, -t * 0.004F * (1 + k), r, g, bl, 1, FULL);
        }
        b = buffers.getBuffer(RenderType.eyes(GLOW));
        float g0 = 0.2F + 0.8F * p;
        CoreDraw.disc(b, m, cx, f.y0 + 0.03F, cz, rad, seg, rad * 2, 0, 0.25F * g0, 0.6F * g0, g0, 1, FULL);
        // --- bulles qui montent le long des canaux
        if (f.lod > 0 && p > 0.02F) {
            int count = Math.round((f.lod == 2 ? 48 : 20) * Math.min(1, 0.3F + p));
            for (int i = 0; i < count; i++) {
                double speed = 0.004 + 0.006 * CoreDraw.hash(i, 1);
                float ph = frac(t * speed + CoreDraw.hash(i, 2));
                double ang = 2 * Math.PI * CoreDraw.hash(i, 3) + t * 0.01;
                float rr = rad * 0.9F * (float) Math.sqrt(CoreDraw.hash(i, 4));
                float s = 0.04F + 0.05F * (float) CoreDraw.hash(i, 5);
                float fade = (float) Math.sin(Math.PI * ph) * (0.4F + 0.6F * p);
                CoreDraw.billboard(b, m, cx + (float) Math.cos(ang) * rr, f.y0 + ph * h * 0.8F, cz + (float) Math.sin(ang) * rr, s, f.left, f.up,
                      0.55F * fade, 0.8F * fade, fade, FULL);
            }
        }
        corium(f, m, buffers, rad);
        // --- 1.0 (lot 3) : l'eau du circuit primaire, translucide (niveau = eau chargee), vue a travers le verre de la cuve
        float wl = f.y0 + h * clamp(v.coolant());
        if (wl > f.y0 + 0.05F) {
            b = buffers.getBuffer(RenderType.entityTranslucent(WATER));
            CoreDraw.cylinder(b, m, cx, cz, rad, f.y0, wl, seg, 4, (wl - f.y0) / 2, -t * 0.002F, 0.55F, 0.75F, 1.0F, 0.32F, LightTexture.FULL_BRIGHT);
            CoreDraw.disc(b, m, cx, wl, cz, rad, seg, rad, t * 0.0004F, 0.6F, 0.8F, 1.0F, 0.42F, LightTexture.FULL_BRIGHT);
        }
    }

    /** 1.0 (lot 3) : fusion du coeur : le corium (magma translucide qui coule) monte au fond de la cuve avec la surchauffe. */
    /**
     * 1.0 (0.19.15) : rayon du bain (eau, sodium, corium) inscrit dans l'interieur rond en escalier de la cuve : la demi-largeur moins
     * 0,15 debordait dans les toles et le verre a certaines tailles (plans translucides en « lame » sur les cotes).
     */
    static float inscribed(Frame f) {
        return Math.min(f.x1 - f.x0, f.z1 - f.z0) * 0.42F - 0.05F;
    }

    private static void corium(Frame f, Matrix4f m, MultiBufferSource buffers, float rad) {
        float k = clamp((f.view.heat() - 1.12F) / 0.13F);
        if (k <= 0) {
            return;
        }
        float h = f.y1 - f.y0, top = f.y0 + 0.1F + 0.4F * h * k;
        int seg = f.lod == 2 ? 24 : 16;
        VertexConsumer b = buffers.getBuffer(RenderType.entityTranslucentEmissive(CORIUM));
        float pulse = 0.85F + 0.15F * (float) Math.sin(f.t * 0.2F);
        CoreDraw.cylinder(b, m, f.cx(), f.cz(), rad, f.y0, top, seg, 4, (top - f.y0) / 2, -f.t * 0.006F, pulse, 0.6F * pulse, 0.3F, 0.82F, FULL);
        CoreDraw.disc(b, m, f.cx(), top, f.cz(), rad, seg, rad, f.t * 0.001F, pulse, 0.55F * pulse, 0.25F, 0.88F, FULL);
    }

    // ------------------------------------------------------------------
    // MKII : neutrons rapides, bain de sodium
    // ------------------------------------------------------------------

    private static void mk2(Frame f, Matrix4f m, MultiBufferSource buffers) {
        ReactorView v = f.view;
        float p = clamp(v.power()), rods = clamp(v.rods()), na = clamp(v.coolant());
        float h = f.y1 - f.y0, rad = inscribed(f);
        float cx = f.cx(), cz = f.cz(), t = f.t;
        float level = f.y0 + h * (0.3F + 0.45F * na);
        int seg = f.lod == 2 ? 24 : 16;
        //1.0 (lot 3) : barres, pompes et echangeurs sont de vrais blocs dans la dalle ; on dessine la lumiere du coeur et le sodium
        VertexConsumer b;
        // --- lueur orange du coeur sous le sodium (colonne qui ondoie, nappes superposees)
        b = buffers.getBuffer(RenderType.eyes(HAZE));
        float pulse = 0.8F + 0.2F * (float) Math.sin(t * 0.07F);
        float i0 = (0.1F + 0.8F * p) * pulse;
        CoreDraw.cylinder(b, m, cx, cz, rad * 0.4F, f.y0, f.y0 + (level - f.y0) * 0.8F, seg, 2, 1, -t * 0.003F, i0, 0.5F * i0, 0.12F * i0, 1, FULL);
        CoreDraw.cylinder(b, m, cx, cz, rad * 0.75F, f.y0, level - 0.05F, seg, 3, 1, -t * 0.002F, 0.45F * i0, 0.2F * i0, 0.05F * i0, 1, FULL);
        b = buffers.getBuffer(RenderType.eyes(GLOW));
        for (int k = 0; k < 5; k++) {
            float y = f.y0 + (level - f.y0) * (k + 0.5F) / 5;
            float i = (0.06F + 0.4F * p) * (1 - k * 0.12F);
            CoreDraw.disc(b, m, cx, y, cz, rad * (0.95F - 0.12F * k), seg, rad * 2 * (0.95F - 0.12F * k), 0, i, 0.42F * i, 0.08F * i, 1, FULL);
        }
        // --- convection : panaches qui montent du coeur ; etincelles a la surface
        if (f.lod > 0) {
            int count = Math.round((f.lod == 2 ? 40 : 16) * p);
            for (int i = 0; i < count; i++) {
                float ph = frac(t * (0.003 + 0.004 * CoreDraw.hash(i, 1)) + CoreDraw.hash(i, 2));
                double ang = 2 * Math.PI * CoreDraw.hash(i, 3) + t * 0.004;
                float rr = rad * 0.7F * (float) Math.sqrt(CoreDraw.hash(i, 4));
                float s = (0.08F + 0.1F * (float) CoreDraw.hash(i, 5)) * (0.6F + ph);
                float fade = (float) Math.sin(Math.PI * ph) * 0.6F;
                CoreDraw.billboard(b, m, cx + (float) Math.cos(ang) * rr, f.y0 + (level - f.y0) * ph, cz + (float) Math.sin(ang) * rr, s, f.left, f.up,
                      fade, 0.55F * fade, 0.15F * fade, FULL);
            }
            int sparks = f.lod == 2 ? 12 : 5;
            for (int i = 0; i < sparks; i++) {
                float tw = (float) Math.pow(Math.max(0, Math.sin(t * 0.3 + 2 * Math.PI * CoreDraw.hash(i, 6))), 8) * (0.3F + 0.7F * p);
                double ang = 2 * Math.PI * CoreDraw.hash(i, 7);
                float rr = rad * 0.9F * (float) Math.sqrt(CoreDraw.hash(i, 8));
                CoreDraw.billboard(b, m, cx + (float) Math.cos(ang) * rr, level + 0.05F, cz + (float) Math.sin(ang) * rr, 0.06F, f.left, f.up, tw,
                      0.85F * tw, 0.6F * tw, FULL);
            }
        }
        corium(f, m, buffers, rad);
        // --- surface du sodium : maillage polaire qui ondule (translucide emissif) ; 1.0 : et le bain lui-meme, translucide
        b = buffers.getBuffer(RenderType.entityTranslucentEmissive(SODIUM));
        //0.19.15 : bain plus dense (on le distinguait a peine) : metal liquide argente, un peu plus sombre que la surface
        CoreDraw.cylinder(b, m, cx, cz, rad, f.y0, level, seg, 4, (level - f.y0) / 2, -t * 0.001F, 0.66F, 0.68F, 0.72F, 0.55F, FULL);
        int rings = f.lod == 0 ? 3 : 6;
        float amp = 0.06F * (0.4F + p);
        float sr = 0.8F + 0.35F * p, sg = 0.78F + 0.05F * p, sb = 0.75F - 0.25F * p;
        for (int ri = 0; ri < rings; ri++) {
            float r0 = rad * ri / rings, r1 = rad * (ri + 1) / rings;
            for (int j = 0; j < seg; j++) {
                double a0 = 2 * Math.PI * j / seg, a1 = 2 * Math.PI * (j + 1) / seg;
                CoreDraw.quad(b, m, new float[][]{surface(cx, level, cz, r0, a0, amp, t), surface(cx, level, cz, r1, a0, amp, t),
                      surface(cx, level, cz, r1, a1, amp, t), surface(cx, level, cz, r0, a1, amp, t)}, sr, sg, sb, 0.88F, FULL, true);
            }
        }
    }

    /** Point de la surface du sodium (rayon, angle) : hauteur ondulee, texture plane qui derive. */
    static float[] surface(float cx, float level, float cz, float r, double a, float amp, float t) {
        float x = cx + (float) Math.cos(a) * r, z = cz + (float) Math.sin(a) * r;
        float y = level + amp * (float) (Math.sin(3 * a + t * 0.08) * Math.sin(r * 3.0 - t * 0.15));
        return new float[]{x, y, z, (x - cx) / 3 + t * 0.0008F, (z - cz) / 3};
    }

    // ------------------------------------------------------------------
    // MKIII : fragments de fission, plasma poussiereux
    // ------------------------------------------------------------------

    private static void mk3(Frame f, Matrix4f m, MultiBufferSource buffers) {
        ReactorView v = f.view;
        float p = clamp(v.power()), field = clamp(v.coolant());
        float cx = f.cx(), cy = f.cy(), cz = f.cz(), t = f.t;
        //1.0 (lot 3) : la sphere est entre deux dalles : son rayon est la demi-hauteur interieure moins l'ecart aux dalles
        float big = (f.y1 - f.y0) * 0.5F - 1.2F;
        boolean disp = field < 0.3F && p > 0.1F;
        float flick = disp ? 0.5F + 0.5F * (float) Math.sin(t * 1.7F) : 1;
        float rc = big * (0.2F + 0.08F * p) * (1 + 0.04F * (float) Math.sin(t * 0.21F));
        // --- couches de plasma (tournent en sens alternes)
        VertexConsumer b = buffers.getBuffer(RenderType.eyes(PLASMA));
        float[][] col = {{1.0F, 0.75F, 1.0F}, {0.6F, 0.35F, 1.0F}, {0.2F, 0.75F, 1.0F}};
        float[] share = {0.9F, 0.5F, 0.28F}, scale = {1, 1.45F, 2.1F};
        int lat = f.lod == 2 ? 10 : 6, lon = f.lod == 2 ? 20 : 12;
        for (int k = 0; k < 3; k++) {
            float i = (0.25F + 0.75F * p) * share[k];
            float r = col[k][0] * i, g = col[k][1] * i, bl = col[k][2] * i;
            if (disp) {
                r = mix(r, 1.0F * i, 0.7F) * flick;
                g = mix(g, 0.2F * i, 0.7F) * flick;
                bl = mix(bl, 0.15F * i, 0.7F) * flick;
            }
            CoreDraw.sphere(b, m, cx, cy, cz, rc * scale[k], lat, lon, 2, t * 0.002F * (k + 1) * (k % 2 == 0 ? 1 : -1), r, g, bl, 1, FULL, true);
        }
        // --- anneaux magnetiques et bande des bobines a l'equateur
        b = buffers.getBuffer(RenderType.eyes(RING));
        float ri = (0.15F + 0.85F * field) * (0.6F + 0.4F * p);
        float[] rr = {0.86F, 0.7F, 0.56F}, tilt = {0, 0.6F, -0.6F}, yawSpeed = {1, -1.3F, 1.7F};
        int rseg = f.lod == 2 ? 48 : 24;
        for (int k = 0; k < 3; k++) {
            CoreDraw.ring(b, m, cx, cy, cz, big * rr[k], 0.07F, tilt[k], t * 0.004F * yawSpeed[k] + k, t * 0.02F * (k % 2 == 0 ? 1 : -1), rseg,
                  0.3F * ri, 0.9F * ri, ri, FULL);
        }
        float bi = 0.1F + 0.6F * field;
        CoreDraw.ring(b, m, cx, cy, cz, big * 0.97F, 0.1F, 0, 0, t * 0.05F, rseg, 0.5F * bi, 0.85F * bi, bi, FULL);
        // --- poussieres en orbites inclinees, lueur du coeur
        b = buffers.getBuffer(RenderType.eyes(GLOW));
        float ci = 0.3F + 0.7F * p;
        CoreDraw.billboard(b, m, cx, cy, cz, rc * 3.2F, f.left, f.up, 0.6F * ci * flick, 0.4F * ci * (disp ? 0.3F : 1), ci * (disp ? 0.3F : 1), FULL);
        if (f.lod > 0) {
            int count = Math.round((f.lod == 2 ? 420 : 160) * (0.35F + 0.65F * p));
            for (int i = 0; i < count; i++) {
                float[] d = dust(i, rc, big, t);
                double h8 = CoreDraw.hash(i, 8);
                float br = (0.35F + 0.65F * p) * (0.6F + 0.4F * (float) Math.sin(t * 0.4 + 2 * Math.PI * CoreDraw.hash(i, 9)))
                           * (1 - 0.5F * (d[3] - rc) / big);
                float r = h8 < 0.5 ? 0.75F : h8 < 0.85 ? 0.35F : 1.0F, g = h8 < 0.5 ? 0.45F : h8 < 0.85 ? 0.85F : 0.95F, bl = 1.0F;
                if (disp) {
                    r = 1.0F;
                    g = 0.25F;
                    bl = 0.2F;
                    br *= flick;
                }
                float s = 0.05F + 0.09F * (float) CoreDraw.hash(i, 7);
                CoreDraw.billboard(b, m, cx + d[0], cy + d[1], cz + d[2], s, f.left, f.up, r * br, g * br, bl * br, FULL);
            }
        }
        // --- arcs vers les bobines de l'equateur et le collecteur du sommet (changent toutes les 3 ticks)
        if (f.lod > 0 && p > 0.05F) {
            b = buffers.getBuffer(RenderType.eyes(ARC));
            int arcs = 2 + (int) (3 * p);
            int epoch = (int) Math.floor(t / 3);
            for (int k = 0; k < arcs; k++) {
                float[][] pts = arc(epoch * 7 + k, cx, cy, cz, rc, big);
                double hk = CoreDraw.hash(epoch * 7 + k, 3);
                float i = (0.6F + 0.4F * (float) hk) * (field > 0.2F ? 1 : 0.5F);
                CoreDraw.ribbon(b, m, pts, 0.06F * (1 + p), f.camera, 0.75F * i, 0.85F * i, i, FULL);
                CoreDraw.ribbon(b, m, pts, 0.02F, f.camera, i, i, i, FULL);
            }
        }
    }

    /** Position d'une poussiere (x, y, z relatifs au centre, et son rayon d'orbite en [3]). */
    static float[] dust(int i, float rc, float big, float t) {
        float a = rc * 1.3F + (big * 0.8F - rc * 1.3F) * (float) Math.pow(CoreDraw.hash(i, 1), 0.8);
        float inc = (float) (CoreDraw.hash(i, 2) - 0.5) * 1.4F;
        double node = 2 * Math.PI * CoreDraw.hash(i, 3);
        double w = (0.006 + 0.02 * CoreDraw.hash(i, 4)) * Math.sqrt(big / Math.max(a, 0.5F));
        double ang = w * t + 2 * Math.PI * CoreDraw.hash(i, 5);
        float breathe = a * (1 + 0.06F * (float) Math.sin(t * 0.05 + 2 * Math.PI * CoreDraw.hash(i, 6)));
        double x = Math.cos(ang) * breathe, z = Math.sin(ang) * breathe;
        double y = -z * Math.sin(inc);
        z = z * Math.cos(inc);
        double rx = x * Math.cos(node) - z * Math.sin(node), rz = x * Math.sin(node) + z * Math.cos(node);
        return new float[]{(float) rx, (float) y, (float) rz, a};
    }

    /** Polyligne d'un arc : du bord du coeur vers une bobine de l'equateur (3 fois sur 4) ou vers le collecteur du sommet. */
    static float[][] arc(int seed, float cx, float cy, float cz, float rc, float big) {
        float ex, ey, ez;
        if (CoreDraw.hash(seed, 1) < 0.75) {
            double a = Math.floor(CoreDraw.hash(seed, 2) * 8) * Math.PI / 4;
            ex = cx + (float) Math.cos(a) * big * 0.95F;
            ey = cy;
            ez = cz + (float) Math.sin(a) * big * 0.95F;
        } else {
            ex = cx;
            ey = cy + big * 0.95F;
            ez = cz;
        }
        float dx = ex - cx, dy = ey - cy, dz = ez - cz;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        float sx = cx + dx / len * rc, sy = cy + dy / len * rc, sz = cz + dz / len * rc;
        float[][] pts = new float[9][];
        for (int j = 0; j < 9; j++) {
            float k = j / 8F;
            float env = (float) Math.sin(Math.PI * k) * 0.35F;
            pts[j] = new float[]{sx + (ex - sx) * k + (float) (CoreDraw.hash(seed, 10 + j) - 0.5) * env,
                  sy + (ey - sy) * k + (float) (CoreDraw.hash(seed, 30 + j) - 0.5) * env,
                  sz + (ez - sz) * k + (float) (CoreDraw.hash(seed, 50 + j) - 0.5) * env};
        }
        return pts;
    }

    // ------------------------------------------------------------------

    static float clamp(float x) {
        return Math.max(0, Math.min(1, x));
    }

    static float mix(float a, float b, float k) {
        return a + (b - a) * k;
    }

    static float frac(double x) {
        return (float) (x - Math.floor(x));
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull ControllerTile tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
