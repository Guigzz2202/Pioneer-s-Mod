package fr.pioneers.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.core.common.PiClientConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.Util;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

/**
 * Dessine les astres des chambres (trou noir, soleil) et leur champ de force.
 * <p>
 * Trou noir :, apres les blocs opaques et les blocs a rendu special, avant les blocs translucides :
 * <ol>
 *     <li>halo, moitie arriere du disque d'accretion, de la bulle de champ de force et des faisceaux des Stabilisateurs ;</li>
 *     <li>copie de l'image de la scene ;</li>
 *     <li>lentille gravitationnelle (shader) : l'image est deformee autour du trou noir, l'horizon est noir,
 *         l'arriere du disque apparait courbe au-dessus et en dessous de l'ombre, avec un anneau de photons ;</li>
 *     <li>moitie avant du disque, de la bulle et des faisceaux, qui passent devant l'ombre.</li>
 * </ol>
 * Sequence d'allumage : les faisceaux jaillissent des Stabilisateurs, la bulle apparait et le trou noir grossit en meme temps ;
 * le disque d'accretion se forme en dernier. A l'effondrement, tout implose rapidement.
 * <ol>
 * </ol>
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ChamberEffectRenderer {

    /** Type d'astre au centre de la chambre. */
    public enum Kind {
        BLACK_HOLE,
        SUN,
        /** Soleil du Simulateur du systeme solaire : sans champ de force ni faisceaux de Stabilisateurs. */
        SIMULATED_SUN
    }

    private ChamberEffectRenderer() {
    }

    private static final ResourceLocation DISK_TEXTURE = Pioneers.rl("textures/effect/accretion_disk.png");
    private static final ResourceLocation GLOW_TEXTURE = Pioneers.rl("textures/effect/glow.png");
    private static final ResourceLocation HORIZON_TEXTURE = Pioneers.rl("textures/effect/horizon.png");
    private static final ResourceLocation FIELD_TEXTURE = Pioneers.rl("textures/effect/force_field.png");
    private static final ResourceLocation BEAM_TEXTURE = Pioneers.rl("textures/effect/beam.png");

    /** Rayon maximal de la zone deformee (chambre interieure de 9 blocs, Stabilisateurs a 3,5 blocs du centre). */
    private static final float MAX_LENS_RADIUS = 3.5F;
    /** Rayon maximal du disque d'accretion, a l'interieur de la bulle de champ de force. */
    private static final float MAX_DISK_RADIUS = 2.95F;
    /** Rayon de la bulle de champ de force. */
    private static final float FIELD_RADIUS = 3.2F;
    /** Distance du centre a la pointe des emetteurs des Stabilisateurs. */
    private static final float EMITTER_TIP = 3.56F;
    private static final int FIELD_LAT = 16, FIELD_LON = 32;
    /** Rayon de l'horizon au rate 1 et au rate 100 (en blocs). */
    private static final float MIN_HORIZON = 0.2F, MAX_HORIZON = 1.1F;
    /** En dessous de cette taille, un trou noir eteint n'est plus dessine. */
    private static final float VISIBLE_HORIZON = 0.01F;
    private static final int DISK_SEGMENTS = 96;
    private static final int DISK_BANDS = 6;
    /** Inclinaison du disque, pour qu'on le voie de biais comme dans Interstellar. */
    private static final Quaternionf DISK_TILT = new Quaternionf().rotationXYZ((float) Math.toRadians(14), 0, (float) Math.toRadians(-7));

    /**
     * @param active    trou noir en formation ou stable
     * @param formation avancement de la formation (0 a 1)
     */
    private record Request(UUID id, Kind kind, Vec3 center, boolean active, float formation, int rate) {}

    /** Etat lisse d'un trou noir (pour les transitions douces a l'allumage / extinction). */
    static class State {

        float intensity;
        float horizon;
        float disk;
        float field;
        long lastSeen;
    }

    private static final List<Request> QUEUE = new ArrayList<>();
    private static final Map<UUID, State> STATES = new HashMap<>();
    private static ShaderInstance lensShader;
    static ShaderInstance hazeShader;
    private static RenderTarget capture;
    private static long lastFrameTime = Util.getMillis();

    public static void setLensShader(ShaderInstance shader) {
        lensShader = shader;
    }

    public static void setHazeShader(ShaderInstance shader) {
        hazeShader = shader;
    }

    /** Appele par le rendu du bloc maitre pour chaque trou noir visible. */
    public static void queue(UUID id, Kind kind, Vec3 center, boolean active, float formation, int rate) {
        for (Request request : QUEUE) {
            if (request.id().equals(id)) {
                return;
            }
        }
        QUEUE.add(new Request(id, kind, center, active, formation, rate));
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        //Apres les blocs a rendu special mais AVANT les blocs translucides (verre) : le verre structurel ecrit sa profondeur
        //meme la ou il est transparent, il cacherait donc entierement le trou noir s'il etait deja dessine.
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_BLOCK_ENTITIES) {
            return;
        }
        long now = Util.getMillis();
        float dt = Math.min(0.1F, (now - lastFrameTime) / 1000F);
        lastFrameTime = now;
        if (QUEUE.isEmpty()) {
            cleanupStates(now);
            return;
        }
        try {
            Camera camera = event.getCamera();
            //On reconstruit la vue exactement comme GameRenderer.renderLevel (a l'etape AFTER_LEVEL, Forge fournit une PoseStack vide).
            PoseStack viewStack = new PoseStack();
            viewStack.mulPose(Axis.XP.rotationDegrees(camera.getXRot()));
            viewStack.mulPose(Axis.YP.rotationDegrees(camera.getYRot() + 180.0F));
            Matrix4f view = new Matrix4f(viewStack.last().pose());
            Matrix4f projection = new Matrix4f(event.getProjectionMatrix());
            float time = (now % 3_600_000L) / 1000F;
            //Les plus lointains d'abord, pour que les plus proches se dessinent par-dessus
            Vec3 camPos = camera.getPosition();
            QUEUE.sort((a, b) -> Double.compare(b.center().distanceToSqr(camPos), a.center().distanceToSqr(camPos)));
            for (Request request : QUEUE) {
                State state = STATES.computeIfAbsent(request.id(), id -> new State());
                state.lastSeen = now;
                //Taille proportionnelle au rate : de 0,2 bloc (rate 1) a 1,1 bloc (rate 100) de rayon d'horizon
                float rateFraction = Mth.clamp((request.rate() - 1) / 99F, 0, 1);
                float formation = request.active() ? request.formation() : 0;
                //Le trou noir grossit avec la formation ; le disque se forme en dernier ; le champ un peu en avance
                float targetHorizon = request.kind() != Kind.BLACK_HOLE
                                      ? SunRenderer.targetRadius(rateFraction) * (float) Math.pow(formation, 0.7)
                                      : Mth.lerp((float) Math.pow(rateFraction, 0.8), MIN_HORIZON, MAX_HORIZON) * formation;
                float targetIntensity = request.active() ? (0.45F + 0.55F * rateFraction) * formation : 0;
                float targetDisk = formation * formation;
                float targetField = request.active() && request.kind() != Kind.SIMULATED_SUN ? Math.min(1, formation * 1.4F + 0.05F) : 0;
                //Formation douce, effondrement rapide (implosion)
                float smoothing = Math.min(1, dt * (request.active() ? 2F : 5F));
                state.horizon += (targetHorizon - state.horizon) * smoothing;
                state.intensity += (targetIntensity - state.intensity) * smoothing;
                state.disk += (targetDisk - state.disk) * smoothing;
                state.field += (targetField - state.field) * smoothing;
                if (request.active() || state.horizon > VISIBLE_HORIZON || state.field > 0.01F) {
                    if (request.kind() != Kind.BLACK_HOLE) {
                        SunRenderer.render(request.id(), state, camera, view, projection, request.center(), time);
                    } else {
                        renderBlackHole(request, state, camera, view, projection, time);
                    }
                }
            }
        } finally {
            QUEUE.clear();
            restoreState();
            cleanupStates(now);
        }
    }

    private static void cleanupStates(long now) {
        Iterator<State> it = STATES.values().iterator();
        while (it.hasNext()) {
            if (now - it.next().lastSeen > 5_000) {
                it.remove();
            }
        }
    }

    private static void renderBlackHole(Request request, State state, Camera camera, Matrix4f view, Matrix4f projection, float time) {
        Vec3 rel = request.center().subtract(camera.getPosition());
        float rx = (float) rel.x, ry = (float) rel.y, rz = (float) rel.z;
        float intensity = state.intensity;
        float horizon = state.horizon;
        //Tout grandit avec l'horizon : disque et zone deformee
        float growth = Mth.clamp((horizon - MIN_HORIZON) / (MAX_HORIZON - MIN_HORIZON), 0, 1);
        float diskInner = horizon * 1.9F;
        float diskOuter = Math.min(MAX_DISK_RADIUS, diskInner + 0.6F + growth);
        float lensRadius = Math.min(MAX_LENS_RADIUS, horizon * 3.5F + 0.4F);
        boolean drawDisk = state.disk > 0.01F && PiClientConfig.ACCRETION_DISK.get();
        float centerDistance = (float) rel.length();

        float field = state.field;
        boolean drawField = field > 0.01F;

        //1. Halo + arrieres du disque, de la bulle et des faisceaux (seront deformes par la lentille)
        if (horizon > VISIBLE_HORIZON) {
            drawGlow(view, camera, rx, ry, rz, horizon * 3.2F, 0.18F + 0.25F * intensity);
        }
        if (drawDisk) {
            drawDisk(view, rx, ry, rz, diskInner, diskOuter, state.disk, time, centerDistance, true);
        }
        if (drawField) {
            drawFieldShell(view, rx, ry, rz, field, time, centerDistance, true, VIOLET_FIELD);
            drawBeams(view, rx, ry, rz, horizon * 1.1F, field, time, centerDistance, true, VIOLET_BEAM);
        }
        if (horizon <= VISIBLE_HORIZON) {
            //Champ seul (debut de l'allumage ou fin de l'effondrement) : pas encore de trou noir a dessiner
            if (drawField) {
                drawFieldShell(view, rx, ry, rz, field, time, centerDistance, false, VIOLET_FIELD);
                drawBeams(view, rx, ry, rz, horizon * 1.1F, field, time, centerDistance, false, VIOLET_BEAM);
            }
            return;
        }

        //2-3. Lentille gravitationnelle
        boolean lensed = PiClientConfig.GRAVITATIONAL_LENSING.get() && lensShader != null && centerDistance > lensRadius + 0.5F
                         && drawLens(view, projection, rx, ry, rz, horizon, lensRadius, intensity, time);
        if (!lensed) {
            //Sans shader : simple disque noir face a la camera
            drawHorizonFallback(view, camera, rx, ry, rz, horizon);
        }

        //4. Avants du disque, de la bulle et des faisceaux, par-dessus l'ombre
        if (drawDisk) {
            drawDisk(view, rx, ry, rz, diskInner, diskOuter, state.disk, time, centerDistance, false);
        }
        if (drawField) {
            drawFieldShell(view, rx, ry, rz, field, time, centerDistance, false, VIOLET_FIELD);
            drawBeams(view, rx, ry, rz, horizon * 1.1F, field, time, centerDistance, false, VIOLET_BEAM);
        }
    }

    // ------------------------------------------------------------------
    // Lentille
    // ------------------------------------------------------------------

    private static boolean drawLens(Matrix4f view, Matrix4f projection, float rx, float ry, float rz, float horizon, float lensRadius, float intensity,
          float time) {
        Vector4f viewPos = view.transform(new Vector4f(rx, ry, rz, 1));
        float depth = -viewPos.z;
        if (depth < 0.5F) {
            return false;
        }
        Vector4f clip = projection.transform(new Vector4f(viewPos));
        if (clip.w <= 0) {
            return false;
        }
        float ndcX = clip.x / clip.w, ndcY = clip.y / clip.w, ndcZ = clip.z / clip.w;
        float focal = projection.m11();
        float horizonUv = horizon * focal / depth * 0.5F;
        float lensUv = lensRadius * focal / depth * 0.5F;
        if (horizonUv < 0.0005F) {
            return false;
        }

        RenderTarget main = captureScene();
        float aspect = main.width / (float) main.height;
        lensShader.safeGetUniform("LensScreenSize").set((float) main.width, (float) main.height);
        lensShader.safeGetUniform("CenterScreen").set(ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F);
        lensShader.safeGetUniform("HorizonRadius").set(horizonUv);
        lensShader.safeGetUniform("LensRadius").set(lensUv);
        lensShader.safeGetUniform("Strength").set(1.5F + 0.6F * intensity);
        lensShader.safeGetUniform("Glow").set(0.35F + 0.65F * intensity);
        lensShader.safeGetUniform("Time").set(time);
        drawScreenQuad(lensShader, ndcX, ndcY, ndcZ, lensUv, aspect);
        return true;
    }

    /** Copie l'image actuelle de la scene dans {@link #capture} (lue ensuite par les shaders) et rend la cible principale. */
    static RenderTarget captureScene() {
        RenderTarget main = Minecraft.getInstance().getMainRenderTarget();
        if (capture == null || capture.width != main.width || capture.height != main.height) {
            if (capture == null) {
                capture = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            } else {
                capture.resize(main.width, main.height, Minecraft.ON_OSX);
            }
            capture.setFilterMode(GL11.GL_LINEAR);
        }
        //Copie de l'image actuelle de la scene
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, capture.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, capture.width, capture.height, GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
        return main;
    }

    /** Dessine, a la profondeur du centre de l'astre, un carre a l'ecran couvrant un rayon {@code radiusUv} (en hauteur d'ecran). */
    static void drawScreenQuad(ShaderInstance shader, float ndcX, float ndcY, float ndcZ, float radiusUv, float aspect) {
        RenderSystem.setShader(() -> shader);
        RenderSystem.setShaderTexture(0, capture.getColorTextureId());
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        float ex = radiusUv * 2 / aspect, ey = radiusUv * 2;
        float z = Mth.clamp(ndcZ, -1, 1);
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION);
        builder.vertex(ndcX - ex, ndcY - ey, z).endVertex();
        builder.vertex(ndcX + ex, ndcY - ey, z).endVertex();
        builder.vertex(ndcX + ex, ndcY + ey, z).endVertex();
        builder.vertex(ndcX - ex, ndcY + ey, z).endVertex();
        BufferUploader.drawWithShader(builder.end());
    }

    // ------------------------------------------------------------------
    // Disque d'accretion
    // ------------------------------------------------------------------

    private static void drawDisk(Matrix4f view, float rx, float ry, float rz, float inner, float outer, float fade, float time, float centerDistance, boolean backHalf) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, DISK_TEXTURE);
        beginAdditive();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        Vector3f p = new Vector3f();
        Vector3f velocity = new Vector3f();
        float[][] cache = new float[4][9];
        for (int band = 0; band < DISK_BANDS; band++) {
            float r0 = Mth.lerp(band / (float) DISK_BANDS, inner, outer);
            float r1 = Mth.lerp((band + 1) / (float) DISK_BANDS, inner, outer);
            //Rotation differentielle (Kepler) : l'interieur tourne plus vite
            float rMid = (r0 + r1) / 2;
            float spin = time * 0.35F * (float) Math.pow(inner / rMid, 1.5);
            for (int seg = 0; seg < DISK_SEGMENTS; seg++) {
                float a0 = seg / (float) DISK_SEGMENTS * Mth.TWO_PI;
                float a1 = (seg + 1) / (float) DISK_SEGMENTS * Mth.TWO_PI;
                //Moitie arriere ou avant ? (distance du milieu du segment a la camera)
                float aMid = (a0 + a1) / 2;
                p.set(rMid * Mth.cos(aMid), 0, rMid * Mth.sin(aMid)).rotate(DISK_TILT).add(rx, ry, rz);
                boolean isBack = p.length() > centerDistance;
                if (isBack != backHalf) {
                    continue;
                }
                fillDiskVertex(cache[0], p, velocity, rx, ry, rz, r0, a0, inner, outer, spin, fade);
                fillDiskVertex(cache[1], p, velocity, rx, ry, rz, r1, a0, inner, outer, spin, fade);
                fillDiskVertex(cache[2], p, velocity, rx, ry, rz, r1, a1, inner, outer, spin, fade);
                fillDiskVertex(cache[3], p, velocity, rx, ry, rz, r0, a1, inner, outer, spin, fade);
                for (float[] v : cache) {
                    builder.vertex(view, v[0], v[1], v[2]).color(v[3], v[4], v[5], v[6]).uv(v[7], v[8]).endVertex();
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void fillDiskVertex(float[] out, Vector3f p, Vector3f velocity, float rx, float ry, float rz, float r, float angle,
          float inner, float outer, float spin, float fade) {
        float cos = Mth.cos(angle), sin = Mth.sin(angle);
        p.set(r * cos, 0, r * sin).rotate(DISK_TILT).add(rx, ry, rz);
        //Effet Doppler relativiste : le cote qui vient vers nous est plus brillant
        velocity.set(-sin, 0, cos).rotate(DISK_TILT);
        float toCamX = -p.x, toCamY = -p.y, toCamZ = -p.z;
        float len = Mth.sqrt(toCamX * toCamX + toCamY * toCamY + toCamZ * toCamZ);
        float doppler = len > 0 ? (velocity.x * toCamX + velocity.y * toCamY + velocity.z * toCamZ) / len : 0;
        float beaming = Mth.clamp(1 + 0.8F * doppler, 0.25F, 1.9F);

        float t = (r - inner) / (outer - inner);
        //Bords adoucis
        float alpha = smooth(0, 0.12F, t) * (1 - smooth(0.55F, 1, t));
        //Couleur : blanc tres chaud au centre -> orange -> violet sur l'exterieur
        float red, green, blue;
        if (t < 0.35F) {
            float k = t / 0.35F;
            red = 1;
            green = Mth.lerp(k, 0.95F, 0.6F);
            blue = Mth.lerp(k, 0.85F, 0.2F);
        } else {
            float k = (t - 0.35F) / 0.65F;
            red = Mth.lerp(k, 1, 0.5F);
            green = Mth.lerp(k, 0.6F, 0.12F);
            blue = Mth.lerp(k, 0.2F, 0.8F);
        }
        out[0] = p.x;
        out[1] = p.y;
        out[2] = p.z;
        out[3] = red;
        out[4] = green;
        out[5] = blue;
        out[6] = Mth.clamp(alpha * beaming * fade * 0.95F, 0, 1);
        out[7] = angle / Mth.TWO_PI * 2 + spin;
        out[8] = t;
    }

    static float smooth(float edge0, float edge1, float x) {
        float t = Mth.clamp((x - edge0) / (edge1 - edge0), 0, 1);
        return t * t * (3 - 2 * t);
    }

    // ------------------------------------------------------------------
    // Champ de force
    // ------------------------------------------------------------------

    /** Bulle hexagonale translucide, plus visible sur les bords (effet de Fresnel), qui ondule lentement. */
    /** Couleurs du champ de force : {r, g, b} de la bulle, puis {r, g, b} du pied et {r, g, b} de la pointe des faisceaux. */
    static final float[] VIOLET_FIELD = {0.62F, 0.38F, 1F};
    static final float[] VIOLET_BEAM = {0.8F, 0.55F, 1F, 0.95F, 0.85F, 1F};
    static final float[] GOLD_FIELD = {1F, 0.72F, 0.28F};
    static final float[] GOLD_BEAM = {1F, 0.7F, 0.3F, 1F, 0.95F, 0.8F};

    static void drawFieldShell(Matrix4f view, float rx, float ry, float rz, float field, float time, float centerDistance, boolean backHalf, float[] color) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, FIELD_TEXTURE);
        beginAdditive();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        //Pendant la formation, la bulle se resserre legerement autour du trou noir
        float radius = FIELD_RADIUS * (1.08F - 0.08F * field);
        float scroll = time * 0.03F;
        float pulse = 0.85F + 0.15F * Mth.sin(time * 2.2F);
        float[][] quad = new float[4][9];
        for (int lat = 0; lat < FIELD_LAT; lat++) {
            for (int lon = 0; lon < FIELD_LON; lon++) {
                float midTheta = (lat + 0.5F) / FIELD_LAT * Mth.PI, midPhi = (lon + 0.5F) / FIELD_LON * Mth.TWO_PI;
                float mx = rx + radius * Mth.sin(midTheta) * Mth.cos(midPhi), my = ry + radius * Mth.cos(midTheta), mz = rz + radius * Mth.sin(midTheta) * Mth.sin(midPhi);
                boolean isBack = Mth.sqrt(mx * mx + my * my + mz * mz) > centerDistance;
                if (isBack != backHalf) {
                    continue;
                }
                fieldVertex(quad[0], rx, ry, rz, radius, lat, lon, field, scroll, pulse, color);
                fieldVertex(quad[1], rx, ry, rz, radius, lat + 1, lon, field, scroll, pulse, color);
                fieldVertex(quad[2], rx, ry, rz, radius, lat + 1, lon + 1, field, scroll, pulse, color);
                fieldVertex(quad[3], rx, ry, rz, radius, lat, lon + 1, field, scroll, pulse, color);
                for (float[] v : quad) {
                    builder.vertex(view, v[0], v[1], v[2]).color(v[3], v[4], v[5], v[6]).uv(v[7], v[8]).endVertex();
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void fieldVertex(float[] out, float rx, float ry, float rz, float radius, int lat, int lon, float field, float scroll, float pulse, float[] color) {
        float theta = lat / (float) FIELD_LAT * Mth.PI, phi = lon / (float) FIELD_LON * Mth.TWO_PI;
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        float px = rx + nx * radius, py = ry + ny * radius, pz = rz + nz * radius;
        float len = Mth.sqrt(px * px + py * py + pz * pz);
        float facing = len > 0 ? Math.abs((nx * px + ny * py + nz * pz) / len) : 1;
        float fresnel = (float) Math.pow(1 - facing, 2.5);
        out[0] = px;
        out[1] = py;
        out[2] = pz;
        out[3] = color[0];
        out[4] = color[1];
        out[5] = color[2];
        out[6] = Mth.clamp(field * pulse * (0.06F + 0.6F * fresnel), 0, 1);
        out[7] = lon / (float) FIELD_LON * 8;
        out[8] = lat / (float) FIELD_LAT * 4 + scroll;
    }

    /** Faisceaux des 4 Stabilisateurs (faces laterales) vers le trou noir. Pendant la formation, ils jaillissent des emetteurs vers le centre. */
    /** @param endRadius distance au centre ou s'arretent les faisceaux (surface de l'astre) */
    static void drawBeams(Matrix4f view, float rx, float ry, float rz, float endRadius, float field, float time, float centerDistance, boolean backHalf, float[] color) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, BEAM_TEXTURE);
        beginAdditive();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        endRadius = Math.max(endRadius, 0.15F);
        float length = (EMITTER_TIP - endRadius) * Math.min(1, field * 1.2F);
        Vector3f start = new Vector3f(), end = new Vector3f(), toCam = new Vector3f(), side = new Vector3f();
        int index = 0;
        //Six faisceaux : un par emetteur (4 cotes, dessus, dessous)
        for (Direction direction : Direction.values()) {
            float dx = direction.getStepX(), dy = direction.getStepY(), dz = direction.getStepZ();
            start.set(rx + dx * EMITTER_TIP, ry + dy * EMITTER_TIP, rz + dz * EMITTER_TIP);
            end.set(start.x - dx * length, start.y - dy * length, start.z - dz * length);
            float midX = (start.x + end.x) / 2, midY = (start.y + end.y) / 2, midZ = (start.z + end.z) / 2;
            boolean isBack = Mth.sqrt(midX * midX + midY * midY + midZ * midZ) > centerDistance;
            if (isBack != backHalf) {
                index++;
                continue;
            }
            //Ruban toujours tourne vers la camera
            toCam.set(-midX, -midY, -midZ).normalize();
            side.set(dx, dy, dz).cross(toCam);
            if (side.lengthSquared() < 1.0E-6F) {
                side.set(1, 0, 0);
            }
            float flicker = 0.8F + 0.2F * Mth.sin(time * 9F + index * 1.7F);
            float width = 0.07F + 0.05F * field;
            side.normalize().mul(width);
            float alpha = Mth.clamp(field * flicker, 0, 1);
            float scroll = time * 2F;
            builder.vertex(view, start.x - side.x, start.y - side.y, start.z - side.z).color(color[0], color[1], color[2], alpha).uv(0, scroll).endVertex();
            builder.vertex(view, start.x + side.x, start.y + side.y, start.z + side.z).color(color[0], color[1], color[2], alpha).uv(1, scroll).endVertex();
            builder.vertex(view, end.x + side.x, end.y + side.y, end.z + side.z).color(color[3], color[4], color[5], alpha).uv(1, scroll + length).endVertex();
            builder.vertex(view, end.x - side.x, end.y - side.y, end.z - side.z).color(color[3], color[4], color[5], alpha).uv(0, scroll + length).endVertex();
            index++;
        }
        BufferUploader.drawWithShader(builder.end());
    }

    // ------------------------------------------------------------------
    // Billboards (halo, horizon sans shader)
    // ------------------------------------------------------------------

    private static void drawGlow(Matrix4f view, Camera camera, float rx, float ry, float rz, float size, float alpha) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        beginAdditive();
        billboard(view, camera, rx, ry, rz, size, 0.55F, 0.25F, 1F, alpha);
    }

    private static void drawHorizonFallback(Matrix4f view, Camera camera, float rx, float ry, float rz, float horizon) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, HORIZON_TEXTURE);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        billboard(view, camera, rx, ry, rz, horizon, 0, 0, 0, 1);
    }

    static void billboard(Matrix4f view, Camera camera, float rx, float ry, float rz, float size, float r, float g, float b, float a) {
        Quaternionf rotation = camera.rotation();
        Vector3f[] corners = {new Vector3f(-1, -1, 0), new Vector3f(1, -1, 0), new Vector3f(1, 1, 0), new Vector3f(-1, 1, 0)};
        float[][] uvs = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        for (int i = 0; i < 4; i++) {
            Vector3f c = corners[i].mul(size).rotate(rotation).add(rx, ry, rz);
            builder.vertex(view, c.x, c.y, c.z).color(r, g, b, a).uv(uvs[i][0], uvs[i][1]).endVertex();
        }
        BufferUploader.drawWithShader(builder.end());
    }

    // ------------------------------------------------------------------
    // Etat OpenGL
    // ------------------------------------------------------------------

    static void beginAdditive() {
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
    }

    private static void restoreState() {
        RenderSystem.defaultBlendFunc();
        RenderSystem.disableBlend();
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
        RenderSystem.enableDepthTest();
        RenderSystem.setShaderColor(1, 1, 1, 1);
    }
}
