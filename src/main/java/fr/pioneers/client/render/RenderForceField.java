package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.tile.TileEntityForceField;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Bulle du champ de force : sphere d'hexagones verts « laser » (sphere-cube : les hexagones gardent la meme taille partout), plus
 * lumineuse sur les bords, balayee par une onde qui monte, et un eclair blanc avec une onde circulaire a l'endroit d'un impact.
 */
public class RenderForceField implements BlockEntityRenderer<TileEntityForceField> {

    private static final ResourceLocation HEX = Pioneers.rl("textures/effect/force_field_hex.png");

    /** Subdivisions par face du cube. */
    private static final int GRID = 14;
    private static final float R = 0.25F, G = 1.0F, B = 0.55F;
    private static final int FLASH_TICKS = 30, SCAN_TICKS = 90;
    /** Les 5 faces du cube projetees (la face du dessous est sous terre) : normale, axe u, axe v. */
    private static final int[][][] FACES = {
          {{0, 1, 0}, {1, 0, 0}, {0, 0, 1}},
          {{1, 0, 0}, {0, 0, 1}, {0, 1, 0}},
          {{-1, 0, 0}, {0, 0, -1}, {0, 1, 0}},
          {{0, 0, 1}, {-1, 0, 0}, {0, 1, 0}},
          {{0, 0, -1}, {1, 0, 0}, {0, 1, 0}},
    };

    /** Maillage unitaire de la sphere-cube, calcule une fois : quads de 4 sommets {x, y, z, u, v} (u, v dans [0, 1] par face). */
    private static final float[][][] MESH = buildMesh();

    private static float[][][] buildMesh() {
        List<float[][]> quads = new ArrayList<>();
        float[][] p = new float[(GRID + 1) * (GRID + 1)][];
        for (int[][] face : FACES) {
            for (int i = 0; i <= GRID; i++) {
                for (int j = 0; j <= GRID; j++) {
                    float a = -1 + 2F * i / GRID, b = -1 + 2F * j / GRID;
                    float x = face[0][0] + a * face[1][0] + b * face[2][0];
                    float y = face[0][1] + a * face[1][1] + b * face[2][1];
                    float z = face[0][2] + a * face[1][2] + b * face[2][2];
                    float len = (float) Math.sqrt(x * x + y * y + z * z);
                    p[i * (GRID + 1) + j] = new float[]{x / len, y / len, z / len, (a + 1) * 0.5F, (b + 1) * 0.5F};
                }
            }
            for (int i = 0; i < GRID; i++) {
                for (int j = 0; j < GRID; j++) {
                    float[] v0 = p[i * (GRID + 1) + j], v1 = p[(i + 1) * (GRID + 1) + j], v2 = p[(i + 1) * (GRID + 1) + j + 1], v3 = p[i * (GRID + 1) + j + 1];
                    //Le bas de la bulle est sous terre : on ne le garde pas
                    if (v0[1] < -0.3F && v1[1] < -0.3F && v2[1] < -0.3F && v3[1] < -0.3F) {
                        continue;
                    }
                    quads.add(new float[][]{v0, v1, v2, v3});
                }
            }
        }
        return quads.toArray(new float[0][][]);
    }

    public RenderForceField(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public void render(@NotNull TileEntityForceField tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        Level level = tile.getLevel();
        //Etat : bloc actif (synchronise par le monde) ou paquet de mise a jour du generateur
        if (level == null || !tile.isVisible() || !(tile.getActive() || tile.isClientShielding())) {
            return;
        }
        float radius = tile.getClientRadius() - 0.05F;
        double clock = RenderClock.time(partialTick);           //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        float time = (float) clock;
        float age = (float) (clock - tile.getLastImpactTime());
        float flash = age >= 0 && age < FLASH_TICKS ? 1 - age / FLASH_TICKS : 0;
        Vec3 impact = tile.getLastImpactDir();
        Vec3 center = Vec3.atCenterOf(tile.getBlockPos());
        Vec3 camera = Minecraft.getInstance().gameRenderer.getMainCamera().getPosition();
        //Repetitions de la texture par face : des hexagones d'environ 1,5 bloc quel que soit le rayon
        float repeat = Math.max(1, Math.round(radius / 4F));
        float scan = (time % SCAN_TICKS) / SCAN_TICKS * 2.4F - 1.2F;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        Matrix4f matrix = pose.last().pose();
        VertexConsumer buffer = buffers.getBuffer(RenderType.eyes(HEX));
        float[] color = new float[16];
        for (float[][] quad : MESH) {
            for (int k = 0; k < 4; k++) {
                shade(quad[k], radius, center, camera, scan, flash, impact, color, k * 4);
            }
            //Les deux faces : visible de l'exterieur comme de l'interieur
            for (int k = 0; k < 4; k++) {
                vertex(buffer, matrix, quad[k], radius, repeat, color, k * 4);
            }
            for (int k = 3; k >= 0; k--) {
                vertex(buffer, matrix, quad[k], radius, repeat, color, k * 4);
            }
        }
        pose.popPose();
    }

    /** Couleur d'un sommet (ecrite dans {@code out} a partir de {@code offset} : r, g, b deja multiplies par l'intensite). */
    private static void shade(float[] n, float radius, Vec3 center, Vec3 camera, float scan, float flash, Vec3 impact, float[] out, int offset) {
        //Bords plus lumineux (on regarde la bulle en rasant)
        double px = center.x + n[0] * radius - camera.x, py = center.y + n[1] * radius - camera.y, pz = center.z + n[2] * radius - camera.z;
        double len = Math.sqrt(px * px + py * py + pz * pz);
        float facing = len < 1e-3 ? 0 : (float) Math.abs((px * n[0] + py * n[1] + pz * n[2]) / len);
        float rim = 1 - facing;
        float intensity = 0.07F + 0.33F * rim * rim;
        //Onde de balayage qui monte le long de la bulle
        float d = n[1] - scan;
        intensity += 0.22F * (float) Math.exp(-d * d / 0.004F);
        float r = R, g = G, b = B;
        if (flash > 0) {
            double dot = n[0] * impact.x + n[1] * impact.y + n[2] * impact.z;
            double angle = Math.acos(Math.max(-1, Math.min(1, dot)));
            float spot = (float) Math.pow(Math.max(0, dot), 12) * flash;
            float ring = (float) Math.max(0, 1 - Math.abs(angle - (1 - flash) * 1.4) * 5) * flash;
            float hit = Math.min(1, spot + ring * 0.8F);
            intensity += 1.2F * hit;
            r += (1 - r) * hit;
            g += (1 - g) * hit;
            b += (1 - b) * hit;
        }
        //Rendu additif : l'intensite module directement la couleur
        float k = Math.min(1, intensity);
        out[offset] = r * k;
        out[offset + 1] = g * k;
        out[offset + 2] = b * k;
    }

    private static void vertex(VertexConsumer buffer, Matrix4f matrix, float[] n, float radius, float repeat, float[] color, int offset) {
        buffer.vertex(matrix, n[0] * radius, n[1] * radius, n[2] * radius).color(color[offset], color[offset + 1], color[offset + 2], 1)
              .uv(n[3] * repeat, n[4] * repeat).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT).normal(n[0], n[1], n[2]).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityForceField tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 160;
    }
}
