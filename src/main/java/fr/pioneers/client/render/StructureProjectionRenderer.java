package fr.pioneers.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.common.projector.Blueprint;
import fr.pioneers.common.projector.ProjectedStructure.Ghost;
import fr.pioneers.common.projector.Projections;
import fr.pioneers.industry.multi.PortBlock;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.model.data.ModelData;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Plan d'un multibloc projete depuis son controleur ({@link Projections}, Maj + clic droit) : les blocs manquants en transparence, les blocs genants
 * (mauvais bloc, ou bloc la ou il faut du vide) en rouge, et le contour de la structure. 1.0.4-beta (lot 26) : petits cadres orange au
 * milieu des faces des cases ou un port est PERMIS (a la place d'une carcasse) : sur les cases encore a poser, et sur toutes quand le
 * joueur tient un port.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class StructureProjectionRenderer {

    private StructureProjectionRenderer() {
    }

    private static final float ALPHA = 0.45F;
    private static final double MAX_DISTANCE = 96;

    private record Key(Blueprint structure, int variant, BlockPos anchor, Direction facing) {}

    private static Key cachedKey;
    private static Map<BlockPos, Ghost> cachedLayout;
    private static AABB cachedBounds;
    /** 1.0.4-beta (lot 26) : cases du plan ou un port est permis (le fantome accepte un port industriel : carcasse, paroi de salle blanche). */
    private static Set<BlockPos> cachedPorts = Set.of();
    /** 1.0.4-beta (lot 26) : couleur du reperage des cases a port (orange des ports de sortie). */
    private static final float PORT_R = 1F, PORT_G = 0.62F, PORT_B = 0.15F;

    @SubscribeEvent
    public static void onLogout(ClientPlayerNetworkEvent.LoggingOut event) {
        Projections.clearClient();
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        ClientLevel level = minecraft.level;
        if (player == null || level == null) {
            return;
        }
        Projections.Active active = Projections.client();
        if (active == null || active.dimension() != level.dimension()) {
            return;
        }
        BlockPos anchor = active.anchor();
        if (anchor.distToCenterSqr(player.position()) > MAX_DISTANCE * MAX_DISTANCE) {
            return;
        }
        //Controleur casse : rien n'est dessine (l'etat reste aligne sur celui du serveur)
        if (level.isLoaded(active.controller()) && level.getBlockState(active.controller()).getBlock() != active.structure().controller()) {
            return;
        }
        Key key = new Key(active.structure(), active.variant(), anchor, active.forward());
        if (!Objects.equals(key, cachedKey)) {
            cachedKey = key;
            cachedLayout = active.layout();
            cachedBounds = bounds(cachedLayout);
            cachedPorts = portCells(cachedLayout);
        }
        //1.0.4-beta (lot 26) : un port en main : toutes les cases ou il peut aller sont reperees (sinon : seulement celles encore a poser)
        boolean portInHand = player.getMainHandItem().getItem() instanceof BlockItem bi && bi.getBlock() instanceof PortBlock
                             || player.getOffhandItem().getItem() instanceof BlockItem bo && bo.getBlock() instanceof PortBlock;
        Vec3 camera = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = minecraft.renderBuffers().bufferSource();
        BlockRenderDispatcher dispatcher = minecraft.getBlockRenderer();
        VertexConsumer ghosts = new AlphaVertexConsumer(buffers.getBuffer(Sheets.translucentCullBlockSheet()), ALPHA);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        pose.pushPose();
        pose.translate(-camera.x, -camera.y, -camera.z);
        for (Map.Entry<BlockPos, Ghost> entry : cachedLayout.entrySet()) {
            BlockPos pos = entry.getKey();
            Ghost ghost = entry.getValue();
            BlockState current = level.getBlockState(pos);
            boolean portHere = cachedPorts.contains(pos) && !(current.getBlock() instanceof PortBlock);
            if (ghost.isSatisfiedBy(current)) {
                if (portInHand && portHere) {
                    portMark(pose, lines, pos);
                }
                continue;
            }
            if (portHere) {
                portMark(pose, lines, pos);
            }
            if (ghost.state() == null) {
                //Il faut du vide ici
                LevelRenderer.renderLineBox(pose, lines, new AABB(pos).deflate(0.02), 1, 0.25F, 0.25F, 0.9F);
                continue;
            }
            boolean blocked = !current.isAir() && !current.canBeReplaced();
            if (ghost.state().getRenderShape() != RenderShape.MODEL) {
                LevelRenderer.renderLineBox(pose, lines, new AABB(pos).deflate(0.1), 0.4F, 0.85F, 1, 0.8F);
                continue;
            }
            pose.pushPose();
            pose.translate(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5);
            pose.scale(0.92F, 0.92F, 0.92F);
            pose.translate(-0.5, -0.5, -0.5);
            float green = blocked ? 0.3F : 1, blue = blocked ? 0.3F : 1;
            dispatcher.getModelRenderer().renderModel(pose.last(), ghosts, ghost.state(), dispatcher.getBlockModel(ghost.state()), 1, green, blue,
                  LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, ModelData.EMPTY, null);
            pose.popPose();
        }
        if (cachedBounds != null) {
            LevelRenderer.renderLineBox(pose, lines, cachedBounds, 0.3F, 0.8F, 1, 0.6F);
        }
        pose.popPose();
        buffers.endBatch(Sheets.translucentCullBlockSheet());
        buffers.endBatch(RenderType.lines());
    }

    /** 1.0.4-beta (lot 26) : cases dont le fantome accepte un port industriel (un port ne remplace qu'une carcasse ; lot 30 : ou une tole). */
    private static Set<BlockPos> portCells(Map<BlockPos, Ghost> layout) {
        Set<BlockPos> out = new HashSet<>();
        for (Map.Entry<BlockPos, Ghost> e : layout.entrySet()) {
            Ghost g = e.getValue();
            if (g.state() != null && g.state().getBlock() instanceof PortBlock || g.accepted().stream().anyMatch(b -> b instanceof PortBlock)) {
                out.add(e.getKey());
            }
        }
        return out;
    }

    /** 1.0.4-beta (lot 26) : repere « un port peut aller ici » : petit cadre orange au milieu de chaque face de la case. */
    private static void portMark(PoseStack pose, VertexConsumer lines, BlockPos pos) {
        double c = 0.5, h = 0.16, e = 0.515;
        double x = pos.getX(), y = pos.getY(), z = pos.getZ();
        for (Direction d : Direction.values()) {
            double ox = x + c + d.getStepX() * e, oy = y + c + d.getStepY() * e, oz = z + c + d.getStepZ() * e;
            double hx = d.getAxis() == Direction.Axis.X ? 0 : h, hy = d.getAxis() == Direction.Axis.Y ? 0 : h, hz = d.getAxis() == Direction.Axis.Z ? 0 : h;
            LevelRenderer.renderLineBox(pose, lines, ox - hx, oy - hy, oz - hz, ox + hx, oy + hy, oz + hz, PORT_R, PORT_G, PORT_B, 0.95F);
        }
    }

    private static AABB bounds(Map<BlockPos, Ghost> layout) {
        AABB box = null;
        for (BlockPos pos : layout.keySet()) {
            AABB cell = new AABB(pos);
            box = box == null ? cell : box.minmax(cell);
        }
        return box;
    }

    /** Ecrit les sommets du modele avec une transparence imposee. */
    private record AlphaVertexConsumer(VertexConsumer delegate, float alpha) implements VertexConsumer {

        @Override
        public VertexConsumer vertex(double x, double y, double z) {
            delegate.vertex(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer color(int red, int green, int blue, int a) {
            delegate.color(red, green, blue, (int) (a * alpha));
            return this;
        }

        @Override
        public VertexConsumer uv(float u, float v) {
            delegate.uv(u, v);
            return this;
        }

        @Override
        public VertexConsumer overlayCoords(int u, int v) {
            delegate.overlayCoords(u, v);
            return this;
        }

        @Override
        public VertexConsumer uv2(int u, int v) {
            delegate.uv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer normal(float x, float y, float z) {
            delegate.normal(x, y, z);
            return this;
        }

        @Override
        public void endVertex() {
            delegate.endVertex();
        }

        @Override
        public void vertex(float x, float y, float z, float red, float green, float blue, float a, float u, float v, int overlay, int light,
              float nx, float ny, float nz) {
            delegate.vertex(x, y, z, red, green, blue, a * alpha, u, v, overlay, light, nx, ny, nz);
        }

        @Override
        public void defaultColor(int red, int green, int blue, int a) {
            delegate.defaultColor(red, green, blue, (int) (a * alpha));
        }

        @Override
        public void unsetDefaultColor() {
            delegate.unsetDefaultColor();
        }
    }
}
