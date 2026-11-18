package fr.pioneers.logistics.warehouse.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.tech.kinetics.rope.PulleyTile;
import fr.pioneers.tech.kinetics.rope.RopeItem;
import fr.pioneers.logistics.warehouse.LineNode;
import fr.pioneers.logistics.warehouse.WarehouseLines;
import java.util.Objects;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 2, Guillaume : « ameliorer le moyen de connexion entre le comptoir et les cordes ») : AIDE A LA POSE des cordes. Corde en
 * main apres un premier clic : un trait de la gorge de la premiere poulie a celle de la poulie visee, VERT si la corde sera acceptee, ROUGE
 * sinon, avec la raison dans la barre d'action (les memes regles que le serveur : {@link RopeItem#check}, sens de rotation compris, lot 27).
 * Corde en main sur une petite poulie (station), une trappe ou un comptoir : la colonne de descente jusqu'a la station, cerclee de dore.
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class RopePreview {

    /** Dernier message montre (pas de clignotement de la barre d'action) : poulie visee + raison. */
    @Nullable
    private static String shown;

    private RopePreview() {
    }

    @SubscribeEvent
    public static void onHighlight(RenderHighlightEvent.Block event) {
        Minecraft mc = Minecraft.getInstance();
        Player player = mc.player;
        Level level = mc.level;
        if (player == null || level == null) {
            return;
        }
        ItemStack stack = player.getMainHandItem();
        if (!(stack.getItem() instanceof RopeItem)) {
            shown = null;
            return;
        }
        BlockPos target = event.getTarget().getBlockPos();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        VertexConsumer lines = event.getMultiBufferSource().getBuffer(RenderType.lines());
        column(level, target, pose, lines, cam);
        CompoundTag tag = stack.getTag();
        if (tag == null || !tag.contains("from")) {
            shown = null;
            return;
        }
        BlockPos from = BlockPos.of(tag.getLong("from"));
        BlockEntity a = level.getBlockEntity(from), b = level.getBlockEntity(target);
        if (!(b instanceof PulleyTile) || !(a instanceof PulleyTile)) {
            shown = null;
            return;
        }
        Component why = RopeItem.check(level, from, target, player.isShiftKeyDown());
        Vec3 pa = gorge(level, from, target), pb = gorge(level, target, from);
        float r = why == null ? 0.35F : 0.95F, g = why == null ? 0.95F : 0.3F, bl = why == null ? 0.45F : 0.25F;
        line(pose, lines, pa.subtract(cam), pb.subtract(cam), r, g, bl);
        String key = target.asLong() + ":" + (why == null ? "" : why.getString());
        if (!Objects.equals(key, shown)) {
            shown = key;
            mc.gui.setOverlayMessage(why == null ? Component.translatable("warehouse.pioneers.preview.ok", Math.round(Math.sqrt(from.distSqr(target))))
                  .withStyle(ChatFormatting.GREEN) : why.copy().withStyle(ChatFormatting.RED), false);
        }
    }

    /** Point de la gorge de la roue {@code pos} (cote de {@code other}) : hauteur de sa corde. */
    private static Vec3 gorge(Level level, BlockPos pos, BlockPos other) {
        LineNode node = LineNode.at(level, pos);
        double y = node == null ? 0.5 : node.ropeYPx(other) / 16.0;
        return new Vec3(pos.getX() + 0.5, pos.getY() + y, pos.getZ() + 0.5);
    }

    /** Colonne de descente d'une station (petite poulie visee, ou la trappe / le comptoir qu'elle dessert). */
    private static void column(Level level, BlockPos target, PoseStack pose, VertexConsumer lines, Vec3 cam) {
        BlockPos pulley = null;
        if (level.getBlockEntity(target) instanceof PulleyTile p && p.stationWheel()) {
            pulley = target;
        } else {
            PulleyTile w = WarehouseLines.pulleyAbove(level, target);
            pulley = w == null ? null : w.getBlockPos();
        }
        WarehouseLines.Station st = pulley == null ? null : WarehouseLines.station(level, pulley);
        if (st == null || st.gap() == 0) {
            return;
        }
        AABB box = new AABB(st.block().above()).minmax(new AABB(pulley.below())).deflate(0.04).move(-cam.x, -cam.y, -cam.z);
        LevelRenderer.renderLineBox(pose, lines, box, 0.95F, 0.75F, 0.25F, 0.8F);
    }

    private static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float r, float g, float bl) {
        Vec3 n = b.subtract(a).normalize();
        PoseStack.Pose last = pose.last();
        vc.vertex(last.pose(), (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 1F).normal(last.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
        vc.vertex(last.pose(), (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 1F).normal(last.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
