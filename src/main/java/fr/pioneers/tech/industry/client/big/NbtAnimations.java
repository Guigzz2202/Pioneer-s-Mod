package fr.pioneers.tech.industry.client.big;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.tech.industry.construction.StackedColumnBlock;
import fr.pioneers.tech.industry.multi.ControllerBlock;
import fr.pioneers.tech.industry.multi.ControllerTile;
import fr.pioneers.tech.industry.multi.GeneratedNbtModels;
import fr.pioneers.tech.industry.multi.GeneratedNbtShapes;
import fr.pioneers.tech.industry.multi.IndustryStyle;
import fr.pioneers.tech.industry.multi.NbtAnims;
import fr.pioneers.tech.industry.multi.NbtFx;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import fr.pioneers.api.multiblock.StructureMatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.data.ModelData;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 25) : ANIMATIONS des multiblocs a structure de Guillaume, dessinees par le controleur (client seulement, rien n'est envoye
 * par le reseau : l'etat de marche est celui du bloc du controleur, la formation son tier peint), d'apres {@link GeneratedNbtModels} :
 * <ul>
 *     <li>colonnes qui TOURNENT : train de forage de la foreuse et pompes a colonne de la pompe a fluide (blocs du monde masques a la
 *     formation, {@link StackedColumnBlock#HIDDEN}, dessines ici avec leur propre modele, tournes autour de leur axe), rotors des turbines
 *     (1.0.4-beta lot 26 : les rotors de 2,5 blocs POSES dans la colonne centrale, sur toute la hauteur de l'interieur 3 x 3) ;</li>
 *     <li>pieces ajoutees dans des cases libres : manivelles a contrepoids du chevalet de pompage ;</li>
 *     <li>particules en marche ({@link NbtFx}) : fumees, vapeur, etincelles, givre... aux bouches de la structure.</li>
 * </ul>
 * Regime progressif comme les machines interieures ({@link RenderBigMachine}) : montee et descente lineaires, adoucies ; une machine deja en
 * marche quand on arrive ne redemarre pas. Niveau de detail : pas de particules au-dela de 48 blocs.
 */
public final class NbtAnimations {

    private static final class Regime {
        long tick = Long.MIN_VALUE, seen;
        float speed, phase;
    }

    private static final Map<Long, Regime> CACHE = new HashMap<>();
    private static final double FX_RANGE = 48;
    private static final Map<Integer, BigMesh> CRANKS = new HashMap<>();

    private NbtAnimations() {
    }

    /** Animations du controleur industriel (rien si la machine n'a pas de structure de Guillaume animee ou si elle n'est pas formee). */
    public static void render(ControllerTile tile, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = tile.getLevel();
        GeneratedNbtModels.Anim anim = NbtAnims.of(tile.machine.key);
        GeneratedNbtShapes.Shape s = GeneratedNbtShapes.of(tile.machine.key);
        BlockState state = tile.getBlockState();
        if (level == null || anim == null || s == null || !state.hasProperty(ControllerBlock.FACING) || !state.hasProperty(IndustryStyle.TIER)) {
            return;
        }
        int painted = state.getValue(IndustryStyle.TIER);
        if (painted <= 0) {
            //structure non formee (habillage neutre) : rien a animer
            return;
        }
        boolean active = state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
        draw(level, tile.getBlockPos(), state.getValue(ControllerBlock.FACING), s.cx(), s.cy(), s.cz(), anim, active, painted - 1, partialTick, pose,
              buffers);
        //1.0.6 : blocs qui rougeoient en marche (bobines de chauffe du Calcinateur)
        String glow = GeneratedNbtModels.glow(tile.machine.key);
        if (glow != null) {
            Regime r = regime(tile.getBlockPos(), RenderClock.clientTicks());
            float k = ease(r.speed);
            if (k > 0.01F) {
                glow(tile.getBlockPos(), state.getValue(ControllerBlock.FACING), s, glow, k, r.phase + partialTick * ease(r.speed), pose, buffers);
            }
        }
        //1.0.4-beta (lot 26) : vraies plantes de la recette en cours (ferme hydroponique, plantation forestiere)
        GrowRender.draw(tile, level, tile.getBlockPos(), state.getValue(ControllerBlock.FACING), s, active, partialTick, pose, buffers);
    }

    /**
     * Dessine les animations d'une structure (industrielle ou maison) : repere du controleur {@code c} (facade {@code front}, controleur
     * en (cx, cy, cz) de la boite), tier MK de la structure, en marche ou non.
     */
    public static void draw(Level level, BlockPos c, Direction front, int cx, int cy, int cz, GeneratedNbtModels.Anim anim, boolean active, int tier,
          float partialTick, PoseStack pose, MultiBufferSource buffers) {
        long now = RenderClock.clientTicks();                  //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        Regime r = regime(c, now);
        float[] ud = upDown(anim);
        if (r.tick == Long.MIN_VALUE) {
            r.speed = active ? 1 : 0;
            r.tick = now;
        }
        long steps = Math.min(200, Math.max(0, now - r.tick));
        Minecraft mc = Minecraft.getInstance();
        double cam2 = mc.gameRenderer.getMainCamera().getPosition().distanceToSqr(c.getX() + 0.5, c.getY() + 0.5, c.getZ() + 0.5);
        for (long i = 0; i < steps; i++) {
            float target = active ? 1 : 0;
            r.speed = target > r.speed ? Math.min(target, r.speed + 1F / ud[0]) : Math.max(target, r.speed - 1F / ud[1]);
            r.phase = (r.phase + ease(r.speed)) % 72_000F;
            if (i == steps - 1 && cam2 < FX_RANGE * FX_RANGE) {
                for (GeneratedNbtModels.Fx fx : anim.fx()) {
                    double[] p = NbtAnims.point(c, front, cx, cy, cz, fx.x(), fx.y(), fx.z());
                    NbtFx.tick(level, fx.kind(), p[0], p[1], p[2], ease(r.speed), level.random);
                }
            }
        }
        r.tick = now;
        float phase = r.phase + ease(r.speed) * partialTick;
        // --- colonnes tournantes
        for (GeneratedNbtModels.Spin sp : anim.spins()) {
            float[] sv = GeneratedNbtModels.SPIN_SPEED.getOrDefault(sp.letter(), new float[]{0.5F, 60, 100, 1});
            float angle = phase * sv[0] * 18F % 360F, k = sv.length > 3 ? sv[3] : 1;
            for (int y = sp.y0(); y <= sp.y1(); y++) {
                BlockPos p = NbtAnims.cell(c, front, cx, cy, cz, sp.x(), y, sp.z());
                BlockState st = spinState(level, p, sp, y, tier);
                if (st == null) {
                    continue;
                }
                pose.pushPose();
                pose.translate(p.getX() - c.getX() + 0.5, p.getY() - c.getY(), p.getZ() - c.getZ() + 0.5);
                pose.mulPose(Axis.YP.rotationDegrees(angle));
                if (k != 1) {
                    //balayage ramene dans la case (coins de la spire du train de forage)
                    pose.scale(k, 1, k);
                }
                pose.translate(-0.5, 0, -0.5);
                mc.getBlockRenderer().renderSingleBlock(st, pose, buffers, LevelRenderer.getLightColor(level, p), OverlayTexture.NO_OVERLAY,
                      ModelData.EMPTY, null);
                pose.popPose();
            }
        }
        // --- pieces (manivelles) : axe = la droite du controleur, centre (px) du repere de la structure
        Direction forward = front.getOpposite(), right = forward.getClockWise();
        for (GeneratedNbtModels.Part part : anim.parts()) {
            float[] pv = GeneratedNbtModels.PART_SPEED.getOrDefault(part.kind(), new float[]{0.4F, 80, 120});
            float angle = phase * pv[0] * 18F % 360F;
            double[] p = NbtAnims.point(c, front, cx, cy, cz, (part.x0() + part.x1()) / 32.0, part.cy() / 16.0, part.cz() / 16.0);
            pose.pushPose();
            pose.translate(p[0] - c.getX(), p[1] - c.getY(), p[2] - c.getZ());
            //repere local : x = axe de la manivelle (droite du controleur), y en haut
            pose.mulPose(Axis.YP.rotationDegrees(270 - right.toYRot()));
            pose.mulPose(Axis.XP.rotationDegrees(angle));
            pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
            RenderBigMachine.drawMesh(crank(part.x1() - part.x0(), part.r()), pose, buffers, LevelRenderer.getLightColor(level,
                  BlockPos.containing(p[0], p[1], p[2])));
            pose.popPose();
        }
    }

    /** 1.0.6 : lueur des bobines de chauffe (intensite dans le RGB, fond noir : RenderType.eyes est additif). */
    private static final ResourceLocation COIL_GLOW = Pioneers.rl("textures/block/industry/fx/coil_glow.png");
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final String SOLID = "KCSFGBHRDPWVLMUZI";

    /**
     * 1.0.6 : les blocs des lettres {@code letters} de la structure ROUGEOIENT en marche : un voile additif (orange, qui pulse lentement,
     * decale d'un bloc a l'autre) sur chacune de leurs faces visibles (voisine hors de la structure ou case libre), a 0,002 bloc devant la
     * face. Faces calculees une fois par structure ({@link #GLOW_FACES}, rien d'alloue a chaque image) ; un seul tampon, en passe a part.
     */
    static void glow(BlockPos c, Direction front, GeneratedNbtShapes.Shape s, String letters, float k, float phase, PoseStack pose,
          MultiBufferSource buffers) {
        float[] faces = glowFaces(c, front, s, letters);
        if (faces.length == 0) {
            return;
        }
        VertexConsumer b = buffers.getBuffer(RenderType.eyes(COIL_GLOW));
        Matrix4f m = pose.last().pose();
        Matrix3f nm = pose.last().normal();
        for (int f = 0; f < faces.length; f += 5) {
            float i = k * (0.72F + 0.28F * (float) Math.sin(phase * 0.06F + faces[f + 4]));
            face(b, m, nm, DIRECTIONS[(int) faces[f + 3]], faces[f], faces[f + 1], faces[f + 2], 0.002F, i, 0.42F * i, 0.1F * i);
        }
    }

    private static final Direction[] DIRECTIONS = Direction.values();
    /** Faces visibles des blocs qui rougeoient, par controleur et facade : {ox, oy, oz, direction, decalage de phase} x n. */
    private static final Map<Long, float[]> GLOW_FACES = new HashMap<>();

    private static float[] glowFaces(BlockPos c, Direction front, GeneratedNbtShapes.Shape s, String letters) {
        long key = c.asLong() * 31 + front.ordinal();
        float[] got = GLOW_FACES.get(key);
        if (got != null) {
            return got;
        }
        if (GLOW_FACES.size() > 64) {
            GLOW_FACES.clear();
        }
        java.util.List<Float> out = new java.util.ArrayList<>();
        for (int y = 0; y < s.sy(); y++) {
            for (int z = 0; z < s.sz(); z++) {
                for (int x = 0; x < s.sx(); x++) {
                    if (letters.indexOf(s.at(x, y, z)) < 0) {
                        continue;
                    }
                    BlockPos p = NbtAnims.cell(c, front, s.cx(), s.cy(), s.cz(), x, y, z);
                    for (Direction d : DIRECTIONS) {
                        int[] q = StructureMatcher.relative(c, front, p.relative(d));
                        int qx = q[0] + s.cx(), qy = q[1] + s.cy(), qz = q[2] + s.cz();
                        boolean inside = qx >= 0 && qy >= 0 && qz >= 0 && qx < s.sx() && qy < s.sy() && qz < s.sz();
                        if (inside && SOLID.indexOf(s.at(qx, qy, qz)) >= 0) {
                            continue;                                   //face cachee par un bloc de la structure (ou l'interieur)
                        }
                        out.add((float) (p.getX() - c.getX()));
                        out.add((float) (p.getY() - c.getY()));
                        out.add((float) (p.getZ() - c.getZ()));
                        out.add((float) d.ordinal());
                        out.add((x + 2 * y + 3 * z) * 0.9F);
                    }
                }
            }
        }
        float[] arr = new float[out.size()];
        for (int i = 0; i < arr.length; i++) {
            arr[i] = out.get(i);
        }
        GLOW_FACES.put(key, arr);
        return arr;
    }

    /** Une face du cube (ox, oy, oz) poussee de {@code e} vers l'exterieur, UV 0..1 (sans allocation). */
    private static void face(VertexConsumer b, Matrix4f m, Matrix3f nm, Direction d, float ox, float oy, float oz, float e, float r, float g,
          float bl) {
        float x0 = ox - e, y0 = oy - e, z0 = oz - e, x1 = ox + 1 + e, y1 = oy + 1 + e, z1 = oz + 1 + e;
        switch (d) {
            case UP -> quad(b, m, nm, d, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, r, g, bl);
            case DOWN -> quad(b, m, nm, d, x0, y0, z1, x0, y0, z0, x1, y0, z0, x1, y0, z1, r, g, bl);
            case NORTH -> quad(b, m, nm, d, x1, y1, z0, x1, y0, z0, x0, y0, z0, x0, y1, z0, r, g, bl);
            case SOUTH -> quad(b, m, nm, d, x0, y1, z1, x0, y0, z1, x1, y0, z1, x1, y1, z1, r, g, bl);
            case WEST -> quad(b, m, nm, d, x0, y1, z0, x0, y0, z0, x0, y0, z1, x0, y1, z1, r, g, bl);
            case EAST -> quad(b, m, nm, d, x1, y1, z1, x1, y0, z1, x1, y0, z0, x1, y1, z0, r, g, bl);
        }
    }

    private static void quad(VertexConsumer b, Matrix4f m, Matrix3f nm, Direction d, float ax, float ay, float az, float bx, float by, float bz,
          float cx, float cy, float cz, float dx, float dy, float dz, float r, float g, float bl) {
        vertex(b, m, nm, d, ax, ay, az, 0, 0, r, g, bl);
        vertex(b, m, nm, d, bx, by, bz, 0, 1, r, g, bl);
        vertex(b, m, nm, d, cx, cy, cz, 1, 1, r, g, bl);
        vertex(b, m, nm, d, dx, dy, dz, 1, 0, r, g, bl);
    }

    private static void vertex(VertexConsumer b, Matrix4f m, Matrix3f nm, Direction d, float x, float y, float z, float u, float v, float r,
          float g, float bl) {
        b.vertex(m, x, y, z).color(r, g, bl, 1F).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(FULL_BRIGHT)
              .normal(nm, d.getStepX(), d.getStepY(), d.getStepZ()).endVertex();
    }

    /**
     * Bloc dessine d'une case de colonne : bloc empilable masque du monde (D, P ; 1.0.4-beta lot 26 : R aussi, les rotors poses dans la
     * colonne centrale des turbines, plus de rotor virtuel) ; null : rien a dessiner.
     */
    @Nullable
    private static BlockState spinState(Level level, BlockPos p, GeneratedNbtModels.Spin sp, int y, int tier) {
        BlockState st = level.getBlockState(p);
        if (!(st.getBlock() instanceof StackedColumnBlock) || !st.getValue(StackedColumnBlock.HIDDEN)) {
            //bloc pas (encore) masque : il est dessine par le monde, immobile
            return null;
        }
        return st.setValue(StackedColumnBlock.HIDDEN, false);
    }

    /**
     * Manivelle a contrepoids (px, repere local : axe x centre, epaisseur {@code thick}, centre en y = z = 0, tout dans le rayon {@code r}) :
     * bras, contrepoids, moyeu, maneton.
     */
    static BigMesh crank(float thick, float r) {
        return CRANKS.computeIfAbsent(Math.round(thick * 100) * 10_000 + Math.round(r * 100), k -> {
            BigMesh m = new BigMesh();
            float h = thick / 2;
            //bras plat (le long de y, du moyeu au maneton)
            m.box(-h, -2.5F, -2.5F, h, r - 1.5F, 2.5F, BigMat.STEEL_D, null, 4);
            //contrepoids : bloc epais oppose au maneton (dans le rayon)
            m.box(-h, -r * 0.92F, -r * 0.38F, h, -r * 0.35F, r * 0.38F, BigMat.CAST, null, 4);
            m.box(-h + 0.3F, -r * 0.7F, -r * 0.62F, h - 0.3F, -r * 0.36F, r * 0.62F, BigMat.CAST, null, 4);
            //moyeu et maneton
            m.cyl('x', 0, 0, 3.2F, -h - 0.5F, h + 0.5F, BigMat.CHROME, 12, null, 8);
            m.cyl('x', r - 3.5F, 0, 1.6F, -h - 0.8F, h + 0.8F, BigMat.CHROME, 10, null, 8);
            return m;
        });
    }

    /** Montee et descente en regime (ticks) : les plus lentes des colonnes et des pieces de la structure (60 / 100 sans elles). */
    private static float[] upDown(GeneratedNbtModels.Anim anim) {
        float up = 60, down = 100;
        for (GeneratedNbtModels.Spin sp : anim.spins()) {
            float[] v = GeneratedNbtModels.SPIN_SPEED.get(sp.letter());
            if (v != null) {
                up = Math.max(up, v[1]);
                down = Math.max(down, v[2]);
            }
        }
        for (GeneratedNbtModels.Part p : anim.parts()) {
            float[] v = GeneratedNbtModels.PART_SPEED.get(p.kind());
            if (v != null) {
                up = Math.max(up, v[1]);
                down = Math.max(down, v[2]);
            }
        }
        return new float[]{up, down};
    }

    private static float ease(float s) {
        return s * s * (3 - 2 * s);
    }

    private static Regime regime(BlockPos pos, long now) {
        if (CACHE.size() > 64) {
            Iterator<Regime> it = CACHE.values().iterator();
            while (it.hasNext()) {
                if (now - it.next().seen > 1200) {
                    it.remove();
                }
            }
        }
        Regime r = CACHE.computeIfAbsent(pos.asLong(), k -> new Regime());
        if (now - r.seen > 100) {
            r.tick = Long.MIN_VALUE;
        }
        r.seen = now;
        return r;
    }
}
