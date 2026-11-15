package fr.pioneers.client.robotics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.robotics.UnitKind;
import fr.pioneers.robotics.UnitTier;
import fr.pioneers.robotics.hub.RoboticHubData;
import fr.pioneers.robotics.hub.TileEntityRoboticCasing;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix4f;

/**
 * Hub robotique (cube de 3) :
 * <ul>
 *     <li>sur le toit, une piste d'appontage a bandes de danger et balisage lumineux, des pylones aux angles, un mat a antenne parabolique
 *     qui tourne et un gyrophare qui s'allume quand des unites sont dehors ;</li>
 *     <li>au coeur, visible par le verre, le berceau de charge : les unites au repos tournent lentement au-dessus d'anneaux qui pulsent ;</li>
 *     <li>des rampes lumineuses sur les quatre aretes verticales, qui defilent quand le hub travaille.</li>
 * </ul>
 */
public class RenderRoboticHub implements BlockEntityRenderer<TileEntityRoboticCasing> {

    private static final ResourceLocation TOP = Pioneers.rl("textures/entity/robotics/hub_top_mk1.png");
    private static final ResourceLocation TOP_GLOW = Pioneers.rl("textures/entity/robotics/hub_top_glow.png");

    private final ModelPart roof, dish, beacon;

    public RenderRoboticHub(BlockEntityRendererProvider.Context context) {
        roof = context.bakeLayer(RobotUnitModels.HUB_TOP);
        dish = roof.getChild("mast").getChild("dish");
        beacon = roof.getChild("beacon");
    }

    @Override
    public void render(@NotNull TileEntityRoboticCasing tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlayLight) {
        Level level = tile.getLevel();
        //Seul le maitre (coin bas) d'un hub forme dessine le hub
        RoboticHubData hub = tile.isMaster() ? tile.getHub() : null;
        if (level == null || hub == null || hub.getBounds() == null) {
            return;
        }
        BlockPos min = hub.getMinPos(), origin = tile.getBlockPos();
        float time = (float) RenderClock.time(partialTick);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        boolean working = hub.isClientWorking();
        pose.pushPose();
        pose.translate(min.getX() - origin.getX(), min.getY() - origin.getY(), min.getZ() - origin.getZ());
        renderRoof(level, min, time, working, pose, buffers);
        renderCradle(level, min, hub, time, pose, buffers);
        renderEdges(time, working, pose, buffers);
        pose.popPose();
    }

    /** Toit : piste, pylones, antenne qui tourne, gyrophare. */
    private void renderRoof(Level level, BlockPos min, float time, boolean working, PoseStack pose, MultiBufferSource buffers) {
        int roofLight = LevelRenderer.getLightColor(level, min.offset(1, 3, 1));
        pose.pushPose();
        pose.translate(1.5, 3, 1.5);
        pose.scale(-1, -1, 1);
        pose.translate(0, -1.501, 0);
        dish.yRot = time * (working ? 0.06F : 0.015F);
        beacon.yRot = time * 0.3F;
        roof.render(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(TOP)), roofLight, OverlayTexture.NO_OVERLAY);
        //Balisage : plein quand des unites sont dehors, en veille sinon
        float glow = working ? 0.75F + 0.25F * Mth.sin(time * 0.5F) : 0.35F;
        roof.render(pose, buffers.getBuffer(RenderType.eyes(TOP_GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, glow, glow, glow, 1);
        pose.popPose();
        if (working) {
            //Gyrophare : faisceaux tournants au-dessus du balisage
            VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
            Matrix4f matrix = pose.last().pose();
            //Le gyrophare est sur le bord du toit (modele en miroir : x = -19,5 px -> +1,22 bloc)
            float cx = 1.5F + 1.22F, cz = 1.5F, top = 3.38F, angle = time * 0.3F;
            for (int k = 0; k < 2; k++) {
                float a = angle + k * Mth.PI;
                float dx = Mth.cos(a) * 1.6F, dz = Mth.sin(a) * 1.6F;
                float x1 = cx + dx - dz * 0.35F, z1 = cz + dz + dx * 0.35F, x2 = cx + dx + dz * 0.35F, z2 = cz + dz - dx * 0.35F;
                fan(buffer, matrix, cx, top, cz, x1, z1, x2, z2);
            }
        }
    }

    private static void fan(VertexConsumer buffer, Matrix4f matrix, float cx, float y, float cz, float x1, float z1, float x2, float z2) {
        buffer.vertex(matrix, cx, y, cz).color(1F, 0.62F, 0.12F, 0.65F).endVertex();
        buffer.vertex(matrix, x1, y, z1).color(1F, 0.62F, 0.12F, 0).endVertex();
        buffer.vertex(matrix, x2, y, z2).color(1F, 0.62F, 0.12F, 0).endVertex();
        buffer.vertex(matrix, cx, y, cz).color(1F, 0.62F, 0.12F, 0.65F).endVertex();
        buffer.vertex(matrix, cx, y, cz).color(1F, 0.62F, 0.12F, 0.65F).endVertex();
        buffer.vertex(matrix, x2, y, z2).color(1F, 0.62F, 0.12F, 0).endVertex();
        buffer.vertex(matrix, x1, y, z1).color(1F, 0.62F, 0.12F, 0).endVertex();
        buffer.vertex(matrix, cx, y, cz).color(1F, 0.62F, 0.12F, 0.65F).endVertex();
    }

    /** Coeur du hub : jusqu'a quatre unites au repos, en rond au-dessus de leurs anneaux de charge. */
    private void renderCradle(Level level, BlockPos min, RoboticHubData hub, float time, PoseStack pose, MultiBufferSource buffers) {
        UnitKind kind = hub.getKind();
        byte[] docked = hub.getClientDocked();
        int innerLight = Math.max(LevelRenderer.getLightColor(level, min.offset(1, 1, 1)), LightTexture.pack(10, 10));
        float scale = switch (kind) {
            case DRONE -> 0.32F;
            case ROBOT -> 0.3F;
            case ROVER -> 0.2F;
            case TURTLE -> 0.26F;
        };
        //Deux passes : tous les anneaux (tampon « lightning ») puis les unites (autres tampons) ; entrelacer les deux tampons
        //invalide le premier (« Not filled all elements of the vertex »)
        int[] slots = new int[4];
        int shown = 0;
        for (int i = 0; i < docked.length && shown < 4; i++) {
            if (docked[i] >= 0) {
                slots[shown++] = i;
            }
        }
        float spin = time * 0.6F;
        VertexConsumer glow = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        //Socle du berceau
        ring(glow, matrix, 1.5F, 1.02F, 1.5F, 0.46F, 0.4F, 0.15F, 0.6F, 0.5F);
        for (int n = 0; n < shown; n++) {
            int i = slots[n];
            float a = (n * 90 + spin) * Mth.DEG_TO_RAD;
            float x = 1.5F + Mth.cos(a) * 0.24F, z = 1.5F + Mth.sin(a) * 0.24F;
            float pulse = 0.5F + 0.3F * Mth.sin(time * 0.12F + i);
            ring(glow, matrix, x, 1.03F, z, 0.13F, 0.09F, 0.25F * pulse, 0.95F * pulse, 0.75F * pulse);
        }
        for (int n = 0; n < shown; n++) {
            int i = slots[n];
            float a = (n * 90 + spin) * Mth.DEG_TO_RAD;
            float x = 1.5F + Mth.cos(a) * 0.24F, z = 1.5F + Mth.sin(a) * 0.24F;
            pose.pushPose();
            pose.translate(x, 1.04 + (kind == UnitKind.DRONE ? 0.08 + 0.03 * Mth.sin(time * 0.15F + i) : 0), z);
            pose.mulPose(Axis.YP.rotationDegrees(-n * 90 - spin + 90));
            pose.scale(scale, scale, scale);
            RobotUnitItemRenderer.renderUnit(kind, UnitTier.byIndex(docked[i]), time * 0.15F + i * 13, false, pose, buffers, innerLight);
            pose.popPose();
        }
    }

    /** Rampes lumineuses des aretes verticales : un point lumineux qui monte quand le hub travaille. */
    private static void renderEdges(float time, boolean working, PoseStack pose, MultiBufferSource buffers) {
        VertexConsumer buffer = buffers.getBuffer(RenderType.lightning());
        Matrix4f matrix = pose.last().pose();
        float[][] corners = {{0, 0}, {3, 0}, {3, 3}, {0, 3}};
        Direction[][] outs = {{Direction.NORTH, Direction.WEST}, {Direction.NORTH, Direction.EAST}, {Direction.SOUTH, Direction.EAST},
                              {Direction.SOUTH, Direction.WEST}};
        for (int c = 0; c < 4; c++) {
            float x = corners[c][0], z = corners[c][1];
            for (Direction out : outs[c]) {
                float ox = out.getStepX() * 0.003F, oz = out.getStepZ() * 0.003F;
                //Bande le long de l'arete, sur chacune des deux faces
                float along = out.getAxis() == Direction.Axis.Z ? (x == 0 ? 1 : -1) : 0, alongZ = out.getAxis() == Direction.Axis.X ? (z == 0 ? 1 : -1) : 0;
                float x0 = x + ox + along * 0.12F, z0 = z + oz + alongZ * 0.12F, x1 = x + ox + along * 0.2F, z1 = z + oz + alongZ * 0.2F;
                for (int s = 0; s < 12; s++) {
                    float y0 = 0.15F + s * 0.23F, y1 = y0 + 0.16F;
                    float phase = working ? (float) Math.max(0, Math.cos((s * 0.23F - (time * 0.08F) % 3.2F) * 3)) : 0.25F;
                    float k = 0.15F + 0.85F * phase;
                    quad(buffer, matrix, x0, y0, z0, x1, y1, z1, 0.25F * k, 0.95F * k, 0.75F * k, 0.9F * k);
                }
            }
        }
    }

    private static void quad(VertexConsumer buffer, Matrix4f m, float x0, float y0, float z0, float x1, float y1, float z1, float r, float g, float b,
          float a) {
        buffer.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x1, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x0, y1, z0).color(r, g, b, a).endVertex();
        buffer.vertex(m, x1, y1, z1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x1, y0, z1).color(r, g, b, a).endVertex();
        buffer.vertex(m, x0, y0, z0).color(r, g, b, a).endVertex();
    }

    /** Anneau lumineux horizontal (plot de charge). */
    private static void ring(VertexConsumer buffer, Matrix4f matrix, float cx, float y, float cz, float outer, float inner, float r, float g, float b) {
        int segments = 20;
        for (int s = 0; s < segments; s++) {
            float a0 = s * Mth.TWO_PI / segments, a1 = (s + 1) * Mth.TWO_PI / segments;
            float c0 = Mth.cos(a0), s0 = Mth.sin(a0), c1 = Mth.cos(a1), s1 = Mth.sin(a1);
            buffer.vertex(matrix, cx + c0 * inner, y, cz + s0 * inner).color(r, g, b, 1).endVertex();
            buffer.vertex(matrix, cx + c1 * inner, y, cz + s1 * inner).color(r, g, b, 1).endVertex();
            buffer.vertex(matrix, cx + c1 * outer, y, cz + s1 * outer).color(r, g, b, 0.2F).endVertex();
            buffer.vertex(matrix, cx + c0 * outer, y, cz + s0 * outer).color(r, g, b, 0.2F).endVertex();
            buffer.vertex(matrix, cx + c0 * outer, y, cz + s0 * outer).color(r, g, b, 0.2F).endVertex();
            buffer.vertex(matrix, cx + c1 * outer, y, cz + s1 * outer).color(r, g, b, 0.2F).endVertex();
            buffer.vertex(matrix, cx + c1 * inner, y, cz + s1 * inner).color(r, g, b, 1).endVertex();
            buffer.vertex(matrix, cx + c0 * inner, y, cz + s0 * inner).color(r, g, b, 1).endVertex();
        }
    }

    /** Le hub deborde de son bloc (tout le cube et la coupole). */
    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityRoboticCasing tile) {
        return true;
    }
}
