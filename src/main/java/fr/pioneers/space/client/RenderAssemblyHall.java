package fr.pioneers.space.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.tech.industry.client.big.BigMesh;
import fr.pioneers.tech.industry.client.big.RenderBigMachine;
import fr.pioneers.logistics.robotics.client.RobotUnitItemRenderer;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.logistics.robotics.ItemRobotUnit;
import fr.pioneers.space.ItemRocket;
import fr.pioneers.space.RocketTier;
import fr.pioneers.space.assembly.AssemblyHall;
import fr.pioneers.space.assembly.AssemblyHallStructure;
import fr.pioneers.space.tile.TileEntityAssemblyHall;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9.2 (retours de Guillaume) : rendu des multiblocs d'assemblage formes : machinerie d'un seul tenant ({@link AssemblyHallMeshes}, partie
 * fixe en cache par sorte, partie animee a chaque image) et produit en construction (vraie maquette de la fusee ou de l'unite, coupee a la
 * hauteur de l'avancement par {@link ClipBuffers} : il monte du sol vers le haut). Regime adouci comme les machines interieures (monte
 * pendant l'assemblage, retombe a l'arret), avancement lisse entre deux envois du serveur (pas de 5 %).
 */
public class RenderAssemblyHall implements BlockEntityRenderer<TileEntityAssemblyHall> {

    private static final Map<AssemblyHall.Kind, BigMesh> STATIC = new EnumMap<>(AssemblyHall.Kind.class);
    private static final BigMesh DYN = new BigMesh();
    private static final Map<Long, Anim> ANIMS = new HashMap<>();

    private static final class Anim {

        long tick = Long.MIN_VALUE, seen;
        float speed, phase, progress = -1;
    }

    private final RocketModels rockets;

    public RenderAssemblyHall(BlockEntityRendererProvider.Context context) {
        rockets = new RocketModels(context::bakeLayer);
    }

    /** Angle (degres, autour de Y) qui amene +z du maillage sur l'avant du plan. */
    private static float yaw(Direction forward) {
        return switch (forward) {
            case EAST -> 90;
            case NORTH -> 180;
            case WEST -> 270;
            default -> 0;
        };
    }

    @Override
    public void render(@NotNull TileEntityAssemblyHall tile, float partialTick, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light,
          int overlay) {
        Level level = tile.getLevel();
        if (level == null || !tile.structureReady()) {
            return;
        }
        AssemblyHall.Kind kind = tile.kind();
        BlockState state = tile.getBlockState();
        Direction facing = state.hasProperty(MachineBlock.FACING) ? state.getValue(MachineBlock.FACING) : Direction.NORTH;
        Direction forward = AssemblyHallStructure.forward(facing);
        BlockPos origin = AssemblyHallStructure.origin(kind, tile.getBlockPos(), forward);
        BlockPos c = tile.getBlockPos();
        // --- etat anime
        long now = RenderClock.clientTicks();                  //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        Anim a = ANIMS.computeIfAbsent(c.asLong(), k -> new Anim());
        boolean active = tile.getActive();
        boolean building = tile.shouldShowProduct() && !tile.isFinishedClient();
        float target = tile.isFinishedClient() ? 1 : tile.getRenderProgress();
        if (a.tick == Long.MIN_VALUE || now - a.seen > 100) {
            a.speed = active ? 1 : 0;
            a.progress = target;
            a.tick = now;
        }
        a.seen = now;
        long steps = Math.min(200, Math.max(0, now - a.tick));
        for (long i = 0; i < steps; i++) {
            a.speed = active ? Math.min(1, a.speed + 1 / 40F) : Math.max(0, a.speed - 1 / 60F);
            a.phase = (a.phase + a.speed) % 36_000F;
            //avance vers la valeur recue, jamais en arriere pendant un assemblage (recommencement : saut)
            a.progress = target < a.progress - 0.2F ? target : a.progress + Math.max(-0.02F, Math.min(0.006F, target - a.progress));
        }
        a.tick = now;
        if (ANIMS.size() > 64) {
            ANIMS.values().removeIf(x -> now - x.seen > 1200);
        }
        AssemblyHallMeshes.State s = new AssemblyHallMeshes.State();
        s.phase = a.phase + a.speed * partialTick;
        s.speed = a.speed;
        s.progress = a.progress;
        s.building = building;
        s.finished = tile.isFinishedClient();
        ItemStack product = tile.shouldShowProduct() ? tile.getRenderedProduct() : ItemStack.EMPTY;
        s.product = !product.isEmpty();
        float rocketScale = 1;
        RocketTier tier = null;
        if (product.getItem() instanceof ItemRocket rocket) {
            tier = rocket.getTier();
            rocketScale = AssemblyHallMeshes.rocketScale(tier.height);
            s.productHeight = tier.height * 16 * rocketScale;
            s.productRadius = tier.width * 8 * rocketScale;
        } else if (product.getItem() instanceof ItemRobotUnit unit) {
            s.productHeight = AssemblyHallMeshes.unitHeight(unit.kind);
            s.productRadius = AssemblyHallMeshes.unitRadius(unit.kind);
        } else if (!product.isEmpty()) {
            s.productHeight = kind == AssemblyHall.Kind.ROCKET ? 40 : 14;
            s.productRadius = kind == AssemblyHall.Kind.ROCKET ? 18 : 8;
        }
        // --- repere du maillage : centre du sol interieur, +z vers le fond
        pose.pushPose();
        pose.translate(origin.getX() - c.getX() + 0.5, origin.getY() + 1 - c.getY(), origin.getZ() - c.getZ() + 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(yaw(forward)));
        int lt = light(level, origin);
        pose.pushPose();
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        BigMesh fixed = STATIC.computeIfAbsent(kind, AssemblyHallMeshes::build);
        RenderBigMachine.drawMesh(fixed, pose, buffers, lt);
        BigMesh dyn = DYN.reset();
        AssemblyHallMeshes.dynamic(dyn, kind, s);
        RenderBigMachine.drawMesh(dyn, pose, buffers, lt);
        pose.popPose();
        // --- produit, coupe a la hauteur de l'avancement (repere en blocs, y vers le haut)
        if (s.product) {
            float cut = AssemblyHallMeshes.cut(kind, s) / 16F;
            ClipBuffers clip = new ClipBuffers(buffers, pose.last().pose(), cut);
            float time = now + partialTick;
            if (tier != null) {
                pose.pushPose();
                pose.scale(-rocketScale, -rocketScale, rocketScale);
                pose.translate(0, -1.501, 0);
                rockets.render(tier, pose, clip, RenderType::entityCutoutNoCull, lt, OverlayTexture.NO_OVERLAY);
                pose.popPose();
            } else if (product.getItem() instanceof ItemRobotUnit unit) {
                pose.pushPose();
                pose.translate(0, AssemblyHallMeshes.PAD_TOP / 16F, AssemblyHallMeshes.PAD_Z / 16F);
                pose.mulPose(Axis.YP.rotationDegrees(s.finished ? 0 : time * 0.4F));
                RobotUnitItemRenderer.renderUnit(unit.kind, unit.tier, time, s.finished, pose, clip, lt);
                pose.popPose();
            } else {
                pose.pushPose();
                float k = kind == AssemblyHall.Kind.ROCKET ? 2.5F : 0.9F;
                pose.translate(0, s.productHeight / 32F + (kind == AssemblyHall.Kind.ROBOTICS ? AssemblyHallMeshes.PAD_TOP / 16F : 0),
                      kind == AssemblyHall.Kind.ROBOTICS ? AssemblyHallMeshes.PAD_Z / 16F : 0);
                pose.mulPose(Axis.YP.rotationDegrees(time * 0.6F));
                pose.scale(k, k, k);
                Minecraft.getInstance().getItemRenderer().renderStatic(product, ItemDisplayContext.FIXED, lt, OverlayTexture.NO_OVERLAY, pose, clip,
                      level, 0);
                pose.popPose();
            }
        }
        pose.popPose();
    }

    /** Lumiere de l'interieur (au milieu, a mi-hauteur), au moins 6 : le hall est eclaire. */
    private static int light(Level level, BlockPos origin) {
        int lc = LevelRenderer.getLightColor(level, origin.above(3));
        return LightTexture.pack(Math.max(6, LightTexture.block(lc)), LightTexture.sky(lc));
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TileEntityAssemblyHall tile) {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 128;
    }
}
