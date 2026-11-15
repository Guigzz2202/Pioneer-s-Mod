package fr.pioneers.client.farm.aquarium;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.farm.crustacean.CrustaceanModel;
import fr.pioneers.client.farm.crustacean.CrustaceanRenderer;
import fr.pioneers.client.farm.fish.FishModel;
import fr.pioneers.client.farm.fish.FishRenderer;
import fr.pioneers.common.block.Connections;
import fr.pioneers.farm.aquarium.AquariumBlock;
import fr.pioneers.farm.aquarium.AquariumDecor;
import fr.pioneers.farm.aquarium.AquariumTile;
import fr.pioneers.farm.crustacean.CrustaceanDef;
import fr.pioneers.farm.crustacean.GeneratedCrustaceans;
import fr.pioneers.farm.fish.FishDef;
import fr.pioneers.farm.fish.GeneratedFish;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.0.4-beta (lot 28) : rendu d'une case d'AQUARIUM : l'EAU (volume translucide teinte, faces seulement vers ce qui n'est pas une case pleine
 * du meme bassin : le bassin se lit comme un seul volume ; surface a {@link #SURFACE} px sous une case vide ou l'air) et les HABITANTS (vrais
 * modeles des poissons et des crustaces du mod, robes de leur espece). Aucune entite, aucune IA, aucun paquet : tout derive de l'horloge de
 * l'aquarium et de la graine de l'habitant.
 * <p>
 * 1.0.5-beta (lot 14) : nage {@link AquariumSwim}, eau plus claire vers la surface.
 * <p>
 * 1.0.5-beta (lot 25, Guillaume : « les poissons nagent avec beaucoup de peine ; l'aquarium ne devrait pas avoir de deco par defaut, le joueur
 * compose le sien ») : temps de la nage = {@link AquariumClock} (plus l'heure du monde, remise a l'heure du serveur chaque seconde) ; nage
 * {@link AquariumSwim#pose} (boucle a vitesse constante le long de la courbe, queue qui bat au rythme de la distance parcourue, au-dessus du
 * decor) ; plus aucun decor tire au hasard (sable, gravier, cailloux, plantes, bulles du lot 14) : la case du FOND dessine la COMPOSITION du
 * joueur ({@link AquariumDecor}, {@link AquariumDecorDraw}) ; un aquarium neuf n'a que son eau.
 */
public class AquariumRenderer implements BlockEntityRenderer<AquariumTile> {

    /** Retrait du volume d'eau contre le verre, surface (px). */
    public static final float GAP = 0.6F, SURFACE = 14.5F;
    /** Longueur visible maximale d'un poisson (blocs), d'un crustace. */
    public static final float MAX_LENGTH = 0.55F, MAX_CRAB = 0.42F;
    public static final int WATER = 0x3F76E4;
    private static final ResourceLocation WATER_STILL = new ResourceLocation("block/water_still");

    private final FishModel[] fish;
    private final CrustaceanModel[] crabs;

    public AquariumRenderer(BlockEntityRendererProvider.Context ctx) {
        fish = new FishModel[FishRenderer.LAYERS.length];
        for (int i = 0; i < fish.length; i++) {
            fish[i] = new FishModel(ctx.bakeLayer(FishRenderer.LAYERS[i]), i);
        }
        crabs = new CrustaceanModel[CrustaceanRenderer.LAYERS.length];
        for (int i = 0; i < crabs.length; i++) {
            crabs[i] = new CrustaceanModel(ctx.bakeLayer(CrustaceanRenderer.LAYERS[i]), i);
        }
    }

    @Override
    public boolean shouldRenderOffScreen(AquariumTile tile) {
        return true;
    }

    /** Le voisin de ce cote est une case PLEINE du meme bassin. */
    static boolean wet(Level level, BlockPos pos, BlockState state, Direction d) {
        if (!state.getValue(Connections.property(d))) {
            return false;
        }
        BlockState n = level.getBlockState(pos.relative(d));
        return n.getBlock() instanceof AquariumBlock && n.getValue(AquariumBlock.FILLED);
    }

    /** Atlas des blocs (le dessin du decor est commun avec le banc). */
    static final AquariumDecorDraw.Atlas ATLAS = tex -> {
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(new ResourceLocation(tex));
        return new float[]{sp.getU0(), sp.getV0(), sp.getU1(), sp.getV1()};
    };

    @Override
    public void render(AquariumTile tile, float partial, PoseStack pose, MultiBufferSource buffers, int light, int overlay) {
        Level level = tile.getLevel();
        if (level == null) {
            return;
        }
        BlockState state = tile.getBlockState();
        if (!(state.getBlock() instanceof AquariumBlock)) {
            return;
        }
        BlockPos pos = tile.getBlockPos();
        state = Connections.actual(state, level, pos, AquariumBlock.FAMILY);
        boolean below = level.getBlockState(pos.below()).getBlock() instanceof AquariumBlock;
        double time = AquariumClock.time(partial);
        //1.0.5-beta (lot 25) : la composition du joueur, dessinee par la case du fond (une seule passe, avant tout autre tampon)
        if (!below && tile.decorCount() > 0) {
            decor(tile, level, pos, state, time, pose, buffers, light);
        }
        if (state.getValue(AquariumBlock.FILLED)) {
            water(pose, buffers, light, level, pos, state);
        }
        int[] cells = null;
        Scene scene = null;
        int i = 0;
        for (AquariumTile.Dweller d : tile.dwellers()) {
            if (d.crustacean()) {
                crab(d, i, time, level, pos, pose, buffers, light);
            } else if (state.getValue(AquariumBlock.FILLED)) {
                if (cells == null) {
                    cells = AquariumSwim.cells((dx, dy, dz) -> wetAt(level, pos.offset(dx, dy, dz)));
                    scene = scene(level, pos, cells);
                }
                swim(d, time, cells, scene, pose, buffers, light);
            }
            i++;
        }
    }

    // ------------------------------------------------------------------ eau

    private static void water(PoseStack pose, MultiBufferSource buffers, int light, Level level, BlockPos pos, BlockState state) {
        TextureAtlasSprite sp = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS).apply(WATER_STILL);
        float x0 = wet(level, pos, state, Direction.WEST) ? 0 : GAP, x1 = wet(level, pos, state, Direction.EAST) ? 16 : 16 - GAP;
        float z0 = wet(level, pos, state, Direction.NORTH) ? 0 : GAP, z1 = wet(level, pos, state, Direction.SOUTH) ? 16 : 16 - GAP;
        boolean wetUp = wet(level, pos, state, Direction.UP), wetDown = wet(level, pos, state, Direction.DOWN);
        //1.0.5-beta (lot 25) : plus de lit de sable : l'eau descend jusqu'au fond de verre (le sol compose est dedans)
        float y0 = wetDown ? 0 : GAP, y1 = wetUp ? 16 : SURFACE;
        VertexConsumer vc = buffers.getBuffer(RenderType.entityTranslucentCull(InventoryMenu.BLOCK_ATLAS));
        int r = (WATER >> 16) & 255, g = (WATER >> 8) & 255, b = WATER & 255, a = 140;
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        float u0 = sp.getU0(), u1 = sp.getU1(), v0 = sp.getV0(), v1 = sp.getV1();
        if (!wetUp) {
            AquariumDecorDraw.quad(vc, m, n, x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0, 0, 1, 0, u0, v0, u1, v1, Math.min(255, r + 60),
                  Math.min(255, g + 60), Math.min(255, b + 30), a - 20, light);
        }
        if (!wetDown) {
            AquariumDecorDraw.quad(vc, m, n, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, 0, -1, 0, u0, v0, u1, v1, r, g, b, a, light);
        }
        if (!wet(level, pos, state, Direction.NORTH)) {
            quadG(vc, m, n, x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0, 0, 0, -1, u0, v0, u1, v1, r, g, b, a, light, top(y1));
        }
        if (!wet(level, pos, state, Direction.SOUTH)) {
            quadG(vc, m, n, x1, y0, z1, x1, y1, z1, x0, y1, z1, x0, y0, z1, 0, 0, 1, u0, v0, u1, v1, r, g, b, a, light, top(y1));
        }
        if (!wet(level, pos, state, Direction.WEST)) {
            quadG(vc, m, n, x0, y0, z1, x0, y1, z1, x0, y1, z0, x0, y0, z0, -1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, top(y1));
        }
        if (!wet(level, pos, state, Direction.EAST)) {
            quadG(vc, m, n, x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1, 1, 0, 0, u0, v0, u1, v1, r, g, b, a, light, top(y1));
        }
    }

    /** 1.0.5-beta (lot 14) : eau plus claire vers la surface (eclaircissement des sommets du haut d'une face laterale, 0..48). */
    static int top(float y1) {
        return y1 >= SURFACE - 0.01F ? 48 : 24;
    }

    /** Face laterale d'eau : sommets b et c (en haut) eclaircis de {@code lift}. */
    private static void quadG(VertexConsumer vc, Matrix4f m, Matrix3f n, float ax, float ay, float az, float bx, float by, float bz, float cx, float cy,
          float cz, float dx, float dy, float dz, float nx, float ny, float nz, float u0, float v0, float u1, float v1, int r, int g, int b, int a, int light,
          int lift) {
        int tr = Math.min(255, r + lift), tg = Math.min(255, g + lift), tb = Math.min(255, b + lift / 2);
        AquariumDecorDraw.vertex(vc, m, n, ax, ay, az, u0, v1, nx, ny, nz, r, g, b, a, light);
        AquariumDecorDraw.vertex(vc, m, n, bx, by, bz, u0, v0, nx, ny, nz, tr, tg, tb, a, light);
        AquariumDecorDraw.vertex(vc, m, n, cx, cy, cz, u1, v0, nx, ny, nz, tr, tg, tb, a, light);
        AquariumDecorDraw.vertex(vc, m, n, dx, dy, dz, u1, v1, nx, ny, nz, r, g, b, a, light);
    }

    // ------------------------------------------------------------------ composition du fond (1.0.5-beta lot 25)

    /** Cases d'aquarium empilees au-dessus du fond {@code floor} (lui compris), au plus 64. */
    static int columnCells(Level level, BlockPos floor) {
        int n = 1;
        while (n < 64 && level.getBlockState(floor.above(n)).getBlock() instanceof AquariumBlock) {
            n++;
        }
        return n;
    }

    /** Colonne d'eau au-dessus d'un sol (px) : de son dessus a la surface de la case la plus haute de la colonne (hauteur du varech). */
    public static float columnWater(int cellsHigh) {
        return (cellsHigh - 1) * 16F + SURFACE - (GAP + AquariumDecor.GROUND_H);
    }

    /** Surface de l'eau au-dessus du fond (px, repere de la case du fond) : case pleine la plus haute de la colonne ; sans eau, le fond. */
    static float surfaceOver(Level level, BlockPos floor, int cellsHigh) {
        for (int k = cellsHigh - 1; k >= 0; k--) {
            BlockState s = level.getBlockState(floor.above(k));
            if (s.getBlock() instanceof AquariumBlock && s.getValue(AquariumBlock.FILLED)) {
                return k * 16F + SURFACE;
            }
        }
        return GAP;
    }

    private static void decor(AquariumTile tile, Level level, BlockPos pos, BlockState state, double time, PoseStack pose, MultiBufferSource buffers,
          int light) {
        Direction[] sides = {Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST};
        boolean[] glass = new boolean[4];
        AquariumDecor.Kind[] ground = new AquariumDecor.Kind[4], piece = new AquariumDecor.Kind[4], ng = new AquariumDecor.Kind[8];
        for (int i = 0; i < 4; i++) {
            glass[i] = !state.getValue(Connections.property(sides[i]));
            ground[i] = tile.ground(i);
            piece[i] = tile.piece(i);
        }
        //sols des emplacements voisins (flancs du sol seulement vers le vide) : nord 0-1, sud 2-3, ouest 4-5, est 6-7
        int[][] facing = {{2, 3}, {0, 1}, {1, 3}, {0, 2}};
        for (int i = 0; i < 4; i++) {
            if (!glass[i] && level.getBlockEntity(pos.relative(sides[i])) instanceof AquariumTile t
                && !(level.getBlockState(pos.relative(sides[i]).below()).getBlock() instanceof AquariumBlock)) {
                ng[2 * i] = t.ground(facing[i][0]);
                ng[2 * i + 1] = t.ground(facing[i][1]);
            }
        }
        int high = columnCells(level, pos);
        AquariumDecorDraw.Cell cell = new AquariumDecorDraw.Cell(ground, piece, glass, ng, GAP, columnWater(high), surfaceOver(level, pos, high),
              AquariumTile.legacySeed(pos));
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS));
        AquariumDecorDraw.draw(pose, vc, ATLAS, cell, time, light);
        //bulles du coffre englouti (case pleine)
        if (state.getValue(AquariumBlock.FILLED)) {
            VertexConsumer tv = buffers.getBuffer(RenderType.entityTranslucent(InventoryMenu.BLOCK_ATLAS));
            float[] w = ATLAS.uv("minecraft:block/water_still");
            float u = (w[0] + w[2]) / 2, v = (w[1] + w[3]) / 2;
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            for (int s = 0; s < AquariumDecor.SLOTS; s++) {
                if (piece[s] == null || !"chest".equals(piece[s].key())) {
                    continue;
                }
                float top = surfaceOver(level, pos, high) - AquariumDecorDraw.groundTop(ground[s], GAP) - 0.8F;
                for (float[] b : AquariumDecorDraw.bubbles(AquariumTile.legacySeed(pos) + s, top, time)) {
                    float x = AquariumDecor.slotX(s) + b[0], y = AquariumDecorDraw.groundTop(ground[s], GAP) + b[1], z = AquariumDecor.slotZ(s) + b[2],
                          r = b[3];
                    AquariumDecorDraw.quad(tv, m, n, x - r, y - r, z, x - r, y + r, z, x + r, y + r, z, x + r, y - r, z, 0, 0, -1, u, v, u, v, 230, 245, 255, 150,
                          light);
                    AquariumDecorDraw.quad(tv, m, n, x, y - r, z + r, x, y + r, z + r, x, y + r, z - r, x, y - r, z - r, -1, 0, 0, u, v, u, v, 230, 245, 255, 150,
                          light);
                }
            }
        }
    }

    /** Fond de la case la plus basse d'un pave de nage (px) : sol compose, ou rien si une case d'aquarium est dessous. */
    static float floorOf(Level level, BlockPos p) {
        return level.getBlockState(p.below()).getBlock() instanceof AquariumBlock ? 0F : GAP + AquariumDecor.GROUND_H;
    }

    /** Case pleine du meme bassin (pour le pave de nage). */
    static boolean wetAt(Level level, BlockPos p) {
        BlockState n = level.getBlockState(p);
        return n.getBlock() instanceof AquariumBlock && n.getValue(AquariumBlock.FILLED);
    }

    /** Ce que voient les poissons d'une case : fond, surface, decor du pave (repere de la case), sommet du decor. */
    public record Scene(float floor, float top, AquariumSwim.Obstacle[] obstacles, float decorTop) {
    }

    /**
     * Fond d'une colonne du pave vu depuis la case du poisson : decalage vertical {@code dy} (cases) de la case du fond, hauteur de la colonne
     * d'aquariums {@code high} (cases), sols et elements de ses emplacements.
     */
    public record Column(int dy, int high, AquariumDecor.Kind[] ground, AquariumDecor.Kind[] piece) {
    }

    /** Colonne du pave en (dx, dz) (null : pas de case du fond, ou rien de pose). */
    public interface Columns {

        Column at(int dx, int dz);
    }

    /** Monde -> colonnes (le fond de chaque colonne sous la rangee la plus basse du pave). */
    static Scene scene(Level level, BlockPos pos, int[] cells) {
        BlockPos low = pos.offset(0, cells[2], 0), high = pos.offset(0, cells[3], 0);
        float top = wetAt(level, high.above()) ? 16F : SURFACE, floor = floorOf(level, low);
        return scene(cells, floor, top, (dx, dz) -> {
            BlockPos f = pos.offset(dx, cells[2], dz);
            for (int k = 0; k < 64 && level.getBlockState(f.below()).getBlock() instanceof AquariumBlock; k++) {
                f = f.below();
            }
            if (!(level.getBlockEntity(f) instanceof AquariumTile t) || t.decorCount() == 0) {
                return null;
            }
            AquariumDecor.Kind[] g = new AquariumDecor.Kind[AquariumDecor.SLOTS], p = new AquariumDecor.Kind[AquariumDecor.SLOTS];
            for (int s = 0; s < AquariumDecor.SLOTS; s++) {
                g[s] = t.ground(s);
                p[s] = t.piece(s);
            }
            return new Column(f.getY() - pos.getY(), columnCells(level, f), g, p);
        });
    }

    /**
     * Decor des colonnes du pave {@code cells} dans le repere de la case du poisson (PUR : le banc {@code tools/aquarium/SwimBench.java}
     * l'appelle) : un obstacle par element pose (pas le nenuphar, a la surface) : centre de l'emplacement, rayon de la sorte, sommet = dessus du
     * sol + hauteur de l'element ({@link AquariumDecor#height} dans la colonne d'eau).
     */
    public static Scene scene(int[] cells, float floor, float top, Columns columns) {
        List<AquariumSwim.Obstacle> obs = new ArrayList<>();
        float decorTop = -1e9F;
        for (int dx = cells[0]; dx <= cells[1]; dx++) {
            for (int dz = cells[4]; dz <= cells[5]; dz++) {
                Column c = columns.at(dx, dz);
                if (c == null) {
                    continue;
                }
                float water = columnWater(c.high()), dy = c.dy() * 16F;
                for (int s = 0; s < AquariumDecor.SLOTS; s++) {
                    AquariumDecor.Kind k = c.piece()[s];
                    if (k == null || k.floating()) {
                        continue;
                    }
                    float topY = dy + AquariumDecorDraw.groundTop(c.ground()[s], GAP) + AquariumDecor.height(k, water);
                    obs.add(new AquariumSwim.Obstacle(dx * 16F + AquariumDecor.slotX(s), dz * 16F + AquariumDecor.slotZ(s), k.radius(), topY));
                    decorTop = Math.max(decorTop, topY);
                }
            }
        }
        return new Scene(floor, top, obs.toArray(new AquariumSwim.Obstacle[0]), decorTop);
    }

    /** Pose d'un poisson dans sa scene (PUR, = {@link #swim} ; le banc l'appelle) : {fit k, demi-longueur, demi-hauteur} et la pose. */
    public static AquariumSwim.Pose fishPose(FishDef def, float size, int seed, double time, int[] cells, Scene sc, float[] out) {
        float length = def.length() * size;                                 //1.0.5-beta (lot 28) : longueur visible (avant : largeur de la boite)
        float k = AquariumSwim.fit(length, def.height() * size, cells, GAP, sc.floor(), sc.top(), maxLength(def), sc.decorTop());
        float half = length * k * 8F, halfH = def.height() * size * k * 8F;
        float[] box = AquariumSwim.box(cells, GAP, sc.floor(), sc.top(), half, halfH);
        float v = AquariumSwim.speed(seed, box, 2 * half);
        if (out != null) {
            out[0] = k;
            out[1] = half;
            out[2] = halfH;
            out[3] = v;
        }
        return AquariumSwim.pose(seed, time, box, v, half, halfH, 2 * half, sc.obstacles());
    }

    // ------------------------------------------------------------------ habitants

    /** Graine -> nombre dans [0, 1) (deterministe, meme valeur a chaque image). */
    static float rnd(int seed, int k) {
        return AquariumSwim.rnd(seed, k);
    }

    /**
     * 1.0.5-beta (lot 25) : nage dans le pave d'eau, au-dessus du decor ({@link AquariumSwim#pose}) ; taille visible bornee par le pave et le
     * decor ({@link AquariumSwim#fit}) ; queue au rythme de la distance parcourue, amplitude selon l'allure.
     */
    private void swim(AquariumTile.Dweller d, double time, int[] cells, Scene sc, PoseStack pose, MultiBufferSource buffers, int light) {
        int s = Mth.clamp(d.species(), 0, GeneratedFish.SPECIES.length - 1);
        FishDef def = GeneratedFish.SPECIES[s];
        float[] fit = new float[4];
        AquariumSwim.Pose p = fishPose(def, d.size(), d.seed(), time, cells, sc, fit);
        float k = fit[0];
        float scale = def.size() * d.size() * k;
        pose.pushPose();
        pose.translate(p.x() / 16F, p.y() / 16F, p.z() / 16F);
        pose.mulPose(Axis.YP.rotation(p.yaw()));
        pose.mulPose(Axis.XP.rotation(-p.pitch()));
        pose.translate(0F, -def.height() * d.size() * k / 2F, 0F);
        pose.scale(-scale, -scale, scale);
        pose.translate(0F, -1.501F, 0F);
        FishModel m = fish[s];
        m.swim(p.tail(), true, p.amp());
        ResourceLocation tex = Pioneers.rl("textures/entity/fish/" + def.id() + ".png");
        m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
    }

    /**
     * Longueur visible maximale (blocs) : {@link #MAX_LENGTH} x 1,6 pour un poisson ; 1.0.5-beta (lot 25) : un requin (taille reelle d'au moins
     * 1 m) garde jusqu'a 2,2 blocs dans un grand bassin.
     */
    public static float maxLength(FishDef def) {
        return fr.pioneers.farm.aquarium.AquariumRules.shark(def) ? 2.2F : MAX_LENGTH * 1.6F;
    }

    private void crab(AquariumTile.Dweller d, int index, double time, Level level, BlockPos pos, PoseStack pose, MultiBufferSource buffers, int light) {
        int s = Mth.clamp(d.species(), 0, GeneratedCrustaceans.SPECIES.length - 1);
        CrustaceanDef def = GeneratedCrustaceans.SPECIES[s];
        float scale = Math.min(MAX_CRAB, def.size() * d.size() * 0.6F);
        //marche lente d'un bord a l'autre, arrets ; les crabes vont de cote ; 1.0.5-beta (lot 25) : sur le sol compose, par-dessus le decor
        float t = (float) (time * 0.012 + 6.28 * rnd(d.seed(), 1));
        float x = 8F + 3.5F * Mth.sin(t), z = index % 2 == 0 ? 6F : 10.5F;
        float speed = Math.abs(Mth.cos(t));
        float heading = Mth.cos(t) >= 0 ? 1F : -1F;
        float floor = GAP;
        AquariumSwim.Obstacle[] obs = AquariumSwim.NONE;
        if (!(level.getBlockState(pos.below()).getBlock() instanceof AquariumBlock) && level.getBlockEntity(pos) instanceof AquariumTile tile) {
            floor = AquariumDecorDraw.groundTop(tile.ground(AquariumDecor.slotAt(x, z)), GAP);
            float water = columnWater(columnCells(level, pos));
            List<AquariumSwim.Obstacle> l = new ArrayList<>();
            for (int k = 0; k < AquariumDecor.SLOTS; k++) {
                AquariumDecor.Kind pk = tile.piece(k);
                if (pk != null && !pk.floating()) {
                    l.add(new AquariumSwim.Obstacle(AquariumDecor.slotX(k), AquariumDecor.slotZ(k), pk.radius() * 0.7F,
                          AquariumDecorDraw.groundTop(tile.ground(k), GAP) + Math.min(4F, AquariumDecor.height(pk, water)) - AquariumSwim.DECOR_CLEAR));
                }
            }
            obs = l.toArray(new AquariumSwim.Obstacle[0]);
        }
        float y = (float) AquariumSwim.lift(floor, x, z, 0.5F, 0F, obs);
        pose.pushPose();
        pose.translate(x / 16F, y / 16F, z / 16F);
        pose.mulPose(Axis.YP.rotationDegrees(def.sideways() ? 180F + 25F * rnd(d.seed(), 2) : heading > 0 ? -90F : 90F));
        pose.scale(-scale, -scale, scale);
        pose.translate(0F, -1.501F, 0F);
        CrustaceanModel m = crabs[Mth.clamp(def.shape(), 0, crabs.length - 1)];
        m.pose((float) time * 0.35F, speed * 0.6F, (float) time);
        ResourceLocation tex = Pioneers.rl("textures/entity/crustacean/" + def.id() + ".png");
        m.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(tex)), light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
        pose.popPose();
    }
}
