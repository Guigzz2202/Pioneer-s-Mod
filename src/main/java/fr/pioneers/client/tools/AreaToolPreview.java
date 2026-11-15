package fr.pioneers.client.tools;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.materials.area.AreaToolItem;
import fr.pioneers.materials.area.AreaTools;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 1.4.1 : apercu des outils de zone ({@link AreaToolItem}) : contour des blocs supplementaires de la zone (marteau de minage, excavateur,
 * faux : cultures mures, herbe, feuillage, terre a labourer ou a aplanir) et fissures de casse sur toute la zone pendant le minage.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class AreaToolPreview {

    /** Identifiants des fissures de la zone (distincts de ceux des entites). */
    private static final int CRACK_ID = -0x41524541;
    private static final List<BlockPos> CRACKED = new ArrayList<>();

    private AreaToolPreview() {
    }

    /** Blocs de la zone EN PLUS du bloc vise, ou rien. */
    private static List<BlockPos> zone(Minecraft mc, Player player, BlockHitResult hit) {
        ItemStack stack = player.getMainHandItem();
        ClientLevel level = mc.level;
        if (level == null || !(stack.getItem() instanceof AreaToolItem tool)) {
            return List.of();
        }
        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (tool.kind() != AreaToolItem.Kind.SCYTHE) {
            List<BlockPos> dig = AreaTools.digTargets(level, player, stack, pos, state, hit.getDirection());
            if (!dig.isEmpty() || tool.kind() != AreaToolItem.Kind.EXCAVATOR || player.isShiftKeyDown()) {
                return dig;
            }
            return reshape(player, stack, hit, ToolActions.SHOVEL_FLATTEN, tool.radius());
        }
        AreaTools.Scythe mode = AreaTools.scytheMode(state);
        if (mode == AreaTools.Scythe.NONE) {
            return player.isShiftKeyDown() ? List.of() : reshape(player, stack, hit, ToolActions.HOE_TILL, tool.radius());
        }
        List<BlockPos> out = new ArrayList<>(AreaTools.scytheTargets(level, player, stack, pos, mode));
        out.remove(pos);
        return out;
    }

    private static List<BlockPos> reshape(Player player, ItemStack stack, BlockHitResult hit, ToolAction action, int r) {
        if (hit.getDirection() == Direction.DOWN || !AreaTools.enabled()) {
            return List.of();
        }
        List<BlockPos> out = new ArrayList<>(AreaTools.reshapeTargets(new UseOnContext(player, InteractionHand.MAIN_HAND, hit), action, r));
        out.remove(hit.getBlockPos());
        return out;
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        if (player == null || mc.level == null || !(player.getMainHandItem().getItem() instanceof AreaToolItem)) {
            return;
        }
        List<BlockPos> zone = zone(mc, player, event.getTarget());
        if (zone.isEmpty()) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        CollisionContext context = CollisionContext.of(player);
        for (BlockPos pos : zone) {
            VoxelShape shape = mc.level.getBlockState(pos).getShape(mc.level, pos, context);
            AABB box = (shape.isEmpty() ? new AABB(0, 0, 0, 1, 1, 1) : shape.bounds()).move(pos).inflate(0.002).move(-cam.x, -cam.y, -cam.z);
            LevelRenderer.renderLineBox(pose, lines, box, 0F, 0F, 0F, 0.4F);
        }
    }

    /** Fissures : la progression de casse du bloc vise est reportee sur les autres blocs de la zone. */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        List<BlockPos> zone = List.of();
        int stage = -1;
        if (mc.player != null && mc.level != null && mc.gameMode != null && mc.gameMode.isDestroying()
            && mc.player.getMainHandItem().getItem() instanceof AreaToolItem tool && tool.kind() != AreaToolItem.Kind.SCYTHE
            && mc.hitResult instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            stage = mc.gameMode.getDestroyStage();
            zone = AreaTools.digTargets(mc.level, mc.player, mc.player.getMainHandItem(), hit.getBlockPos(), mc.level.getBlockState(hit.getBlockPos()),
                  hit.getDirection());
        }
        //Meme identifiant = remplace la fissure precedente ; au-dela de la nouvelle zone, les anciennes sont retirees
        int n = stage < 0 ? 0 : zone.size();
        for (int i = 0; i < Math.max(n, CRACKED.size()); i++) {
            if (i < n) {
                mc.levelRenderer.destroyBlockProgress(CRACK_ID - i, zone.get(i), stage);
            } else {
                mc.levelRenderer.destroyBlockProgress(CRACK_ID - i, CRACKED.get(i), -1);
            }
        }
        CRACKED.clear();
        CRACKED.addAll(zone.subList(0, n));
    }
}
