package fr.pioneers.client.kinetics;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.industry.atelier.DynamoBlock;
import fr.pioneers.kinetics.AxisKineticBlock;
import fr.pioneers.kinetics.KineticBlockItem;
import fr.pioneers.kinetics.KineticPlacement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 0.20.42 : apercu des aides a la pose ({@link KineticPlacement}) : arbre ou engrenage en main, la case ou la piece ira (bout de la ligne
 * d'arbres, diagonale d'engrenement) est cerclee d'un trait dore, en plus du contour du bloc vise.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class KineticPlacementPreview {

    private KineticPlacementPreview() {
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (stack.getItem() instanceof BlockItem blockItem && blockItem.getBlock() instanceof DynamoBlock dynamo) {
            dynamo(event, player, stack, dynamo);
            return;
        }
        if (!(stack.getItem() instanceof KineticBlockItem item)) {
            return;
        }
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, event.getTarget());
        BlockPlaceContext helped = KineticPlacement.help(item.getBlock(), context);
        if (helped == null) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        AABB box = new AABB(helped.getClickedPos()).inflate(0.002).move(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, event.getMultiBufferSource().getBuffer(RenderType.lines()), box, 0.95F, 0.75F, 0.25F, 0.9F);
    }

    /**
     * 1.12.4 (lot F4) : dynamo en main : la case de pose cerclee de dore, l'axe de l'arbre (trait clair qui traverse la case) et la facade
     * (boite a bornes : petit cadre orange sur la face ou elle sera), d'apres le meme calcul que la pose ({@link DynamoBlock#placement}).
     */
    private static void dynamo(RenderHighlightEvent.Block event, Player player, ItemStack stack, DynamoBlock block) {
        BlockPlaceContext context = new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, event.getTarget());
        if (!context.canPlace()) {
            return;
        }
        BlockState state = DynamoBlock.placement(block, context);
        BlockPos pos = context.getClickedPos();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        AABB cell = new AABB(pos).inflate(0.002).move(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, lines, cell, 0.95F, 0.75F, 0.25F, 0.9F);
        Direction.Axis axis = AxisKineticBlock.axis(state);
        double ax = axis == Direction.Axis.X ? 0.62 : 0.06, ay = axis == Direction.Axis.Y ? 0.62 : 0.06, az = axis == Direction.Axis.Z ? 0.62 : 0.06;
        Vec3 c = Vec3.atCenterOf(pos).subtract(cam);
        LevelRenderer.renderLineBox(pose, lines, new AABB(c.x - ax, c.y - ay, c.z - az, c.x + ax, c.y + ay, c.z + az), 0.85F, 0.9F, 0.95F, 1F);
        Direction f = DynamoBlock.facing(state);
        Vec3 face = c.add(f.getStepX() * 0.5, f.getStepY() * 0.5, f.getStepZ() * 0.5);
        double hx = f.getAxis() == Direction.Axis.X ? 0.03 : 0.22, hy = f.getAxis() == Direction.Axis.Y ? 0.03 : 0.22, hz = f.getAxis() == Direction.Axis.Z ? 0.03 : 0.22;
        LevelRenderer.renderLineBox(pose, lines, new AABB(face.x - hx, face.y - hy, face.z - hz, face.x + hx, face.y + hy, face.z + hz), 1F, 0.55F, 0.15F, 1F);
    }
}
