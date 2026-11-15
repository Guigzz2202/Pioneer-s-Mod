package fr.pioneers.client.render;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import fr.pioneers.Pioneers;
import fr.pioneers.common.PiClientConfig;
import java.util.Random;
import java.util.UUID;
import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;

/**
 * Rendu du soleil de la Chambre stellaire :
 * <ol>
 *     <li>photosphere opaque : granulation animee (cellules de convection), taches solaires, rotation differentielle,
 *         assombrissement centre-bord (le bord est plus sombre et plus rouge, comme le vrai Soleil) ;</li>
 *     <li>protuberances : boucles de plasma magnetique qui naissent, s'elevent et s'eteignent sur le limbe ;</li>
 *     <li>eruptions : points eblouissants qui flambent a la surface ;</li>
 *     <li>chromosphere et couronne : halos additifs, couronne a filaments en rotation lente ;</li>
 *     <li>champ de force dore et faisceaux des Stabilisateurs ;</li>
 *     <li>distorsion de chaleur (shader) : l'air ondule autour de l'etoile.</li>
 * </ol>
 * Allumage : un point eblouissant grossit jusqu'a l'etoile ; les protuberances apparaissent en fin de formation.
 */
final class SunRenderer {

    private SunRenderer() {
    }

    private static final ResourceLocation SURFACE_TEXTURE = Pioneers.rl("textures/effect/sun_surface.png");
    private static final ResourceLocation CORONA_TEXTURE = Pioneers.rl("textures/effect/sun_corona.png");
    private static final ResourceLocation PROMINENCE_TEXTURE = Pioneers.rl("textures/effect/sun_prominence.png");
    private static final ResourceLocation GLOW_TEXTURE = Pioneers.rl("textures/effect/glow.png");

    /** Rayon de l'etoile au rate 1 et au rate 100 (en blocs). */
    private static final float MIN_RADIUS = 0.35F, MAX_RADIUS = 1.9F;
    private static final int SPHERE_LAT = 24, SPHERE_LON = 48;
    private static final int PROMINENCES = 6;
    private static final float PROMINENCE_PERIOD = 14F;
    private static final int PROMINENCE_SEGMENTS = 24;
    private static final int FLARES = 3;
    private static final float FLARE_PERIOD = 9F;

    static float targetRadius(float rateFraction) {
        return Mth.lerp((float) Math.pow(rateFraction, 0.8), MIN_RADIUS, MAX_RADIUS);
    }

    static void render(UUID id, ChamberEffectRenderer.State state, Camera camera, Matrix4f view, Matrix4f projection, Vec3 center, float time) {
        Vec3 rel = center.subtract(camera.getPosition());
        float rx = (float) rel.x, ry = (float) rel.y, rz = (float) rel.z;
        float radius = state.horizon;
        float intensity = Math.max(state.intensity, 0.2F);
        float field = state.field;
        float centerDistance = (float) rel.length();
        long seed = id.getMostSignificantBits() ^ id.getLeastSignificantBits();

        if (radius > 0.01F) {
            //Tout petit (debut de l'allumage) : un point eblouissant
            float newborn = 1 - Mth.clamp(radius / 0.6F, 0, 1);
            drawPhotosphere(view, rx, ry, rz, radius, time);
            if (state.disk > 0.02F) {
                drawProminences(view, rx, ry, rz, radius, state.disk, time, seed);
                drawFlares(view, camera, rx, ry, rz, radius, state.disk, time, seed);
            }
            drawCorona(view, camera, rx, ry, rz, radius, intensity, time, newborn);
        }
        if (field > 0.01F) {
            ChamberEffectRenderer.drawFieldShell(view, rx, ry, rz, field, time, centerDistance, true, ChamberEffectRenderer.GOLD_FIELD);
            ChamberEffectRenderer.drawFieldShell(view, rx, ry, rz, field, time, centerDistance, false, ChamberEffectRenderer.GOLD_FIELD);
            ChamberEffectRenderer.drawBeams(view, rx, ry, rz, radius * 1.02F, field, time, centerDistance, true, ChamberEffectRenderer.GOLD_BEAM);
            ChamberEffectRenderer.drawBeams(view, rx, ry, rz, radius * 1.02F, field, time, centerDistance, false, ChamberEffectRenderer.GOLD_BEAM);
        }
        if (radius > 0.05F && PiClientConfig.SUN_HEAT_HAZE.get() && ChamberEffectRenderer.hazeShader != null && centerDistance > radius * 2.4F + 0.5F) {
            drawHeatHaze(view, projection, rx, ry, rz, radius, intensity, time);
        }
    }

    // ------------------------------------------------------------------
    // Photosphere
    // ------------------------------------------------------------------

    private static void drawPhotosphere(Matrix4f view, float rx, float ry, float rz, float radius, float time) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, SURFACE_TEXTURE);
        //Passe opaque : ecrit la profondeur, ce qui cache correctement ce qui est derriere l'etoile
        RenderSystem.disableBlend();
        RenderSystem.enableDepthTest();
        RenderSystem.depthFunc(GL11.GL_LEQUAL);
        RenderSystem.depthMask(true);
        RenderSystem.disableCull();
        sphere(view, rx, ry, rz, radius, time * 0.012F, 1F, 1F, false);
        //Passe additive : la granulation "bout" (meme texture, autre echelle, rotation inverse)
        ChamberEffectRenderer.beginAdditive();
        sphere(view, rx, ry, rz, radius * 1.004F, -time * 0.02F + 0.37F, 2F, 0.28F, true);
    }

    /**
     * Sphere texturee. La rotation est differentielle (l'equateur tourne plus vite que les poles, comme le Soleil).
     * La couleur applique l'assombrissement centre-bord : centre blanc-jaune, bord plus sombre et rouge-orange.
     */
    private static void sphere(Matrix4f view, float rx, float ry, float rz, float radius, float rotation, float uvScale, float alpha, boolean boil) {
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        float[][] quad = new float[4][9];
        for (int lat = 0; lat < SPHERE_LAT; lat++) {
            for (int lon = 0; lon < SPHERE_LON; lon++) {
                sphereVertex(quad[0], rx, ry, rz, radius, lat, lon, rotation, uvScale, alpha, boil);
                sphereVertex(quad[1], rx, ry, rz, radius, lat + 1, lon, rotation, uvScale, alpha, boil);
                sphereVertex(quad[2], rx, ry, rz, radius, lat + 1, lon + 1, rotation, uvScale, alpha, boil);
                sphereVertex(quad[3], rx, ry, rz, radius, lat, lon + 1, rotation, uvScale, alpha, boil);
                for (float[] v : quad) {
                    builder.vertex(view, v[0], v[1], v[2]).color(v[3], v[4], v[5], v[6]).uv(v[7], v[8]).endVertex();
                }
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    private static void sphereVertex(float[] out, float rx, float ry, float rz, float radius, int lat, int lon, float rotation, float uvScale, float alpha,
          boolean boil) {
        float theta = lat / (float) SPHERE_LAT * Mth.PI, phi = lon / (float) SPHERE_LON * Mth.TWO_PI;
        float nx = Mth.sin(theta) * Mth.cos(phi), ny = Mth.cos(theta), nz = Mth.sin(theta) * Mth.sin(phi);
        float px = rx + nx * radius, py = ry + ny * radius, pz = rz + nz * radius;
        float len = Mth.sqrt(px * px + py * py + pz * pz);
        //mu = cosinus entre la normale et la direction de la camera : 1 au centre du disque, 0 sur le bord
        float mu = len > 0 ? Mth.clamp(-(nx * px + ny * py + nz * pz) / len, 0, 1) : 1;
        float limb = 0.38F + 0.62F * (float) Math.sqrt(mu);
        //Rotation differentielle : plus rapide a l'equateur
        float latitude = Math.abs(Mth.cos(theta));
        float spin = rotation * (1F - 0.3F * latitude * latitude);
        out[0] = px;
        out[1] = py;
        out[2] = pz;
        if (boil) {
            out[3] = 1F;
            out[4] = 0.75F;
            out[5] = 0.35F;
            out[6] = alpha * limb;
        } else {
            out[3] = limb;
            out[4] = limb * (0.72F + 0.28F * mu);
            out[5] = limb * (0.45F + 0.55F * mu);
            out[6] = 1F;
        }
        out[7] = (lon / (float) SPHERE_LON + spin) * uvScale;
        out[8] = lat / (float) SPHERE_LAT * uvScale;
    }

    // ------------------------------------------------------------------
    // Protuberances et eruptions
    // ------------------------------------------------------------------

    /** Boucles de plasma : chaque protuberance vit {@link #PROMINENCE_PERIOD} secondes, puis renait ailleurs. */
    private static void drawProminences(Matrix4f view, float rx, float ry, float rz, float radius, float fade, float time, long seed) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, PROMINENCE_TEXTURE);
        ChamberEffectRenderer.beginAdditive();
        BufferBuilder builder = Tesselator.getInstance().getBuilder();
        builder.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        Vector3f a = new Vector3f(), b = new Vector3f(), p = new Vector3f(), prev = new Vector3f(), tangent = new Vector3f(), toCam = new Vector3f(),
              side = new Vector3f(), prevSide = new Vector3f();
        for (int i = 0; i < PROMINENCES; i++) {
            float local = time / PROMINENCE_PERIOD + i / (float) PROMINENCES;
            long cycle = (long) Math.floor(local);
            float life = local - cycle;
            Random random = new Random(seed * 31 + i * 7919L + cycle * 104729L);
            //Pied de la boucle : deux points de la surface de part et d'autre d'un centre aleatoire
            Vector3f mid = randomUnit(random);
            Vector3f axis = randomUnit(random).cross(mid).normalize();
            float halfAngle = 0.15F + random.nextFloat() * 0.25F;
            a.set(mid).rotate(new Quaternionf().rotateAxis(-halfAngle, axis));
            b.set(mid).rotate(new Quaternionf().rotateAxis(halfAngle, axis));
            //La boucle s'eleve puis retombe
            float rise = Mth.sin(life * Mth.PI);
            float height = radius * (0.25F + random.nextFloat() * 0.35F) * (0.4F + 0.6F * rise);
            float alpha = fade * rise * 0.9F;
            float width = radius * (0.05F + random.nextFloat() * 0.04F);
            float scroll = time * 0.6F + i;
            boolean first = true;
            for (int s = 0; s <= PROMINENCE_SEGMENTS; s++) {
                float t = s / (float) PROMINENCE_SEGMENTS;
                p.set(a).lerp(b, t).normalize().mul(radius + height * Mth.sin(t * Mth.PI) + radius * 0.01F).add(rx, ry, rz);
                if (!first) {
                    tangent.set(p).sub(prev);
                    toCam.set(prev).add(p).mul(-0.5F).normalize();
                    float taper = 0.55F + 0.45F * Mth.sin(t * Mth.PI);
                    side.set(tangent).cross(toCam).normalize().mul(width * taper);
                    if (s == 1) {
                        prevSide.set(side);
                    }
                    float hot = 0.6F + 0.4F * Mth.sin(t * Mth.PI);
                    float v0 = (s - 1) / (float) PROMINENCE_SEGMENTS * 2 + scroll, v1 = t * 2 + scroll;
                    builder.vertex(view, prev.x - prevSide.x, prev.y - prevSide.y, prev.z - prevSide.z).color(1F, 0.45F * hot + 0.15F, 0.12F, alpha).uv(0, v0).endVertex();
                    builder.vertex(view, prev.x + prevSide.x, prev.y + prevSide.y, prev.z + prevSide.z).color(1F, 0.45F * hot + 0.15F, 0.12F, alpha).uv(1, v0).endVertex();
                    builder.vertex(view, p.x + side.x, p.y + side.y, p.z + side.z).color(1F, 0.45F * hot + 0.15F, 0.12F, alpha).uv(1, v1).endVertex();
                    builder.vertex(view, p.x - side.x, p.y - side.y, p.z - side.z).color(1F, 0.45F * hot + 0.15F, 0.12F, alpha).uv(0, v1).endVertex();
                    prevSide.set(side);
                }
                prev.set(p);
                first = false;
            }
        }
        BufferUploader.drawWithShader(builder.end());
    }

    /** Eruptions : de petits points eblouissants qui s'allument puis s'eteignent a la surface. */
    private static void drawFlares(Matrix4f view, Camera camera, float rx, float ry, float rz, float radius, float fade, float time, long seed) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        ChamberEffectRenderer.beginAdditive();
        for (int i = 0; i < FLARES; i++) {
            float local = time / FLARE_PERIOD + i / (float) FLARES;
            long cycle = (long) Math.floor(local);
            float life = local - cycle;
            Random random = new Random(seed * 17 + i * 6151L + cycle * 15485863L);
            Vector3f dir = randomUnit(random);
            //Flambee rapide puis lente extinction
            float flash = life < 0.08F ? life / 0.08F : (float) Math.pow(1 - (life - 0.08F) / 0.92F, 3);
            float size = radius * (0.12F + 0.1F * random.nextFloat()) * (0.6F + 0.8F * flash);
            ChamberEffectRenderer.billboard(view, camera, rx + dir.x * radius * 1.01F, ry + dir.y * radius * 1.01F, rz + dir.z * radius * 1.01F,
                  size, 1F, 0.95F, 0.8F, fade * flash);
        }
    }

    private static Vector3f randomUnit(Random random) {
        float z = random.nextFloat() * 2 - 1;
        float angle = random.nextFloat() * Mth.TWO_PI;
        float r = Mth.sqrt(1 - z * z);
        return new Vector3f(r * Mth.cos(angle), z, r * Mth.sin(angle));
    }

    // ------------------------------------------------------------------
    // Couronne
    // ------------------------------------------------------------------

    private static void drawCorona(Matrix4f view, Camera camera, float rx, float ry, float rz, float radius, float intensity, float time, float newborn) {
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        ChamberEffectRenderer.beginAdditive();
        float pulse = 0.92F + 0.08F * Mth.sin(time * 1.3F);
        //Chromosphere : liseré chaud collé au bord
        RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
        ChamberEffectRenderer.billboard(view, camera, rx, ry, rz, radius * 1.65F, 1F, 0.62F, 0.25F, 1F * pulse);
        //Couronne interne, blanche et chaude
        ChamberEffectRenderer.billboard(view, camera, rx, ry, rz, radius * 2.4F, 1F, 0.82F, 0.5F, 0.75F * intensity * pulse);
        //Couronne externe a filaments, qui tourne lentement
        RenderSystem.setShaderTexture(0, CORONA_TEXTURE);
        rotatedBillboard(view, camera, rx, ry, rz, Math.min(radius * 3.1F, 3.4F), time * 0.04F, 1F, 0.55F, 0.18F, 0.9F * intensity);
        rotatedBillboard(view, camera, rx, ry, rz, Math.min(radius * 2.6F, 3.2F), -time * 0.025F + 1.3F, 1F, 0.75F, 0.35F, 0.6F * intensity);
        //Naissance : un point eblouissant
        if (newborn > 0.01F) {
            RenderSystem.setShaderTexture(0, GLOW_TEXTURE);
            ChamberEffectRenderer.billboard(view, camera, rx, ry, rz, 0.6F + radius, 1F, 1F, 0.95F, newborn);
        }
    }

    private static void rotatedBillboard(Matrix4f view, Camera camera, float rx, float ry, float rz, float size, float angle, float r, float g, float b, float a) {
        Quaternionf rotation = new Quaternionf(camera.rotation()).rotateZ(angle);
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
    // Distorsion de chaleur
    // ------------------------------------------------------------------

    private static void drawHeatHaze(Matrix4f view, Matrix4f projection, float rx, float ry, float rz, float radius, float intensity, float time) {
        Vector4f viewPos = view.transform(new Vector4f(rx, ry, rz, 1));
        float depth = -viewPos.z;
        if (depth < 0.5F) {
            return;
        }
        Vector4f clip = projection.transform(new Vector4f(viewPos));
        if (clip.w <= 0) {
            return;
        }
        float ndcX = clip.x / clip.w, ndcY = clip.y / clip.w, ndcZ = clip.z / clip.w;
        float focal = projection.m11();
        float sunUv = radius * focal / depth * 0.5F;
        float hazeUv = Math.min(radius * 2.4F, 3.4F) * focal / depth * 0.5F;
        if (sunUv < 0.001F) {
            return;
        }
        RenderTarget main = ChamberEffectRenderer.captureScene();
        float aspect = main.width / (float) main.height;
        var shader = ChamberEffectRenderer.hazeShader;
        shader.safeGetUniform("LensScreenSize").set((float) main.width, (float) main.height);
        shader.safeGetUniform("CenterScreen").set(ndcX * 0.5F + 0.5F, ndcY * 0.5F + 0.5F);
        shader.safeGetUniform("SunRadius").set(sunUv);
        shader.safeGetUniform("HazeRadius").set(hazeUv);
        shader.safeGetUniform("Strength").set(0.6F + 0.6F * intensity);
        shader.safeGetUniform("Time").set(time);
        ChamberEffectRenderer.drawScreenQuad(shader, ndcX, ndcY, ndcZ, hazeUv, aspect);
    }
}
