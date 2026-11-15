package fr.pioneers.client.pneumatic;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.pneumatic.network.HypertubeAnchor;
import fr.pioneers.pneumatic.network.HypertubeClientViews;
import fr.pioneers.pneumatic.network.HypertubeCurve;
import fr.pioneers.pneumatic.network.HypertubeEntity;
import fr.pioneers.pneumatic.network.HypertubeItem;
import fr.pioneers.pneumatic.network.PneumaticNet;
import java.util.List;
import java.util.Locale;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.LevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.20.39 : l'hypertube cote client (comme les voies de Create) : avec l'objet en main, clic droit = ancre visee (bout d'un tube, face d'une
 * station, case) envoyee au serveur ({@code PneumaticNet.HyperClick}) ; Maj + clic droit = annuler ; clic gauche sur un tube (objet ou cle en
 * main) = le demonter. Apercu : la courbe du depart a l'ancre visee (verte : posable ; rouge : obstacle, trop long ou trop serre), l'ancre
 * visee, le tube vise surligne ; la barre d'action dit le nombre de tubes ou le probleme (memes regles que le serveur).
 * <p>
 * 1.0.4-beta (lot 27, Guillaume : « les hypertubes doivent toujours avoir une hitbox pour les detruire ») : plus d'ancrage ; le tube vise
 * (plus proche que le bloc ou l'entite visee) est TOUJOURS surligne et un clic gauche le demonte, quoi qu'on tienne en main (main vide
 * comprise) : aucun tube indestructible ni invisible au viseur, y compris ceux poses depuis un ancrage d'avant (bout libre).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HypertubeClient {

    @Nullable
    private static HypertubeAnchor start, target;
    @Nullable
    private static HypertubeCurve curve;
    private static boolean ok;
    /** Trace mis en cache : refait quand le depart ou la cible changent, ou toutes les 10 ticks (le monde a pu changer). */
    @Nullable
    private static String planKey;
    @Nullable
    private static HypertubeItem.Plan plan;
    @Nullable
    private static HypertubeEntity aimedTube;

    private HypertubeClient() {
    }

    /** Tube vise par le regard, s'il est plus proche que le bloc vise. */
    @Nullable
    private static HypertubeEntity aimedTube(Minecraft mc, LocalPlayer player) {
        Vec3 eye = player.getEyePosition();
        double reach = HypertubeItem.REACH;
        if (mc.hitResult != null && mc.hitResult.getType() != HitResult.Type.MISS) {
            reach = Math.min(reach, mc.hitResult.getLocation().distanceTo(eye) + 0.3);
        }
        return HypertubeClientViews.aimed(player.level(), eye, player.getViewVector(1), reach, HypertubeCurve.RADIUS + 0.1);
    }

    @SubscribeEvent
    public static void onClick(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.screen != null) {
            return;
        }
        ItemStack held = HypertubeItem.held(player);
        if (event.isUseItem() && !held.isEmpty()) {
            boolean cancel = player.isShiftKeyDown();
            HypertubeAnchor anchor = cancel ? null : HypertubeItem.aim(level, player, HypertubeItem.ends(HypertubeClientViews.seen(level)));
            PneumaticNet.CHANNEL.sendToServer(new PneumaticNet.HyperClick(anchor, cancel));
            event.setCanceled(true);
            event.setSwingHand(true);
            return;
        }
        if (event.isAttack()) {
            HypertubeEntity tube = aimedTube(mc, player);
            if (tube != null) {
                PneumaticNet.CHANNEL.sendToServer(new PneumaticNet.HyperBreak(tube.segmentId()));
                event.setCanceled(true);
                event.setSwingHand(true);
            }
        }
    }

    /** Apercu recalcule chaque tick, message de la barre d'action 4 fois par seconde. */
    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        ItemStack held = player == null || level == null ? ItemStack.EMPTY : HypertubeItem.held(player);
        aimedTube = player != null && !player.isSpectator() ? aimedTube(mc, player) : null;
        if (held.isEmpty()) {
            start = target = null;
            curve = null;
            return;
        }
        start = HypertubeItem.start(held, level);
        target = HypertubeItem.aim(level, player, HypertubeItem.ends(HypertubeClientViews.seen(level)));
        Component problem = null;
        curve = null;
        if (start != null && target != null) {
            String key = key(start) + "|" + key(target);
            if (plan == null || !key.equals(planKey) || level.getGameTime() % 10 == 0) {
                plan = HypertubeItem.plan(level, start, target);
                planKey = key;
            }
            curve = plan.curve();
            problem = plan.problem();
        } else {
            plan = null;
            planKey = null;
        }
        ok = curve != null && problem == null;
        if (level.getGameTime() % 5 != 0) {
            return;
        }
        Component msg;
        if (start == null) {
            msg = Component.translatable(target == null ? "message.pioneers.hypertube.first" : target.station() != null ? "message.pioneers.hypertube.first_station"
                  : target.segment() >= 0 ? "message.pioneers.hypertube.first_end" : "message.pioneers.hypertube.first_cell").withStyle(ChatFormatting.GRAY);
        } else if (curve == null) {
            msg = Component.translatable("message.pioneers.hypertube.aim").withStyle(ChatFormatting.AQUA);
        } else if (problem != null) {
            msg = problem.copy().withStyle(ChatFormatting.RED);
        } else {
            int need = curve.tubes(), have = HypertubeItem.count(player, held);
            boolean creative = player.getAbilities().instabuild;
            boolean enough = have >= need || creative;
            msg = Component.translatable("message.pioneers.hypertube.preview", need, have).withStyle(enough ? ChatFormatting.GREEN : ChatFormatting.GOLD);
            if (curve.deformed()) {
                msg = msg.copy().append(Component.translatable("message.pioneers.hypertube.bent").withStyle(ChatFormatting.AQUA));
            }
        }
        mc.gui.setOverlayMessage(msg, false);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || start == null && target == null && aimedTube == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        pose.pushPose();
        pose.translate(-cam.x, -cam.y, -cam.z);
        VertexConsumer lines = buffers.getBuffer(RenderType.lines());
        if (start != null) {
            marker(pose, lines, start, 1, 0.85F, 0.2F);
        }
        if (target != null) {
            marker(pose, lines, target, 0.3F, 0.9F, 1);
        }
        HypertubeCurve c = curve;
        if (c != null) {
            float r = ok ? 0.3F : 1, g = ok ? 1 : 0.3F, b = ok ? 0.45F : 0.25F;
            List<Vec3> pts = c.points();
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            double rad = HypertubeCurve.RADIUS;
            //Axe et quatre generatrices du tube
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 p = pts.get(i), q = pts.get(i + 1);
                line(lines, m, nm, p, q, r, g, b, 1);
            }
            Vec3 up = new Vec3(0, 1, 0);
            for (int k = 0; k < 4; k++) {
                for (int i = 0; i + 1 < pts.size(); i += 1) {
                    Vec3 p = pts.get(i), q = pts.get(i + 1);
                    Vec3 t = q.subtract(p).normalize();
                    Vec3 n = Math.abs(t.y) > 0.95 ? new Vec3(1, 0, 0) : up.subtract(t.scale(t.y)).normalize();
                    Vec3 bn = t.cross(n);
                    double a = Math.PI / 2 * k;
                    Vec3 o = n.scale(Math.cos(a) * rad).add(bn.scale(Math.sin(a) * rad));
                    line(lines, m, nm, p.add(o), q.add(o), r, g, b, 0.55F);
                }
            }
        }
        HypertubeEntity tube = aimedTube;
        if (tube != null && tube.curve() != null) {
            List<Vec3> pts = tube.curve().points();
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            for (int i = 0; i + 1 < pts.size(); i++) {
                Vec3 lift = new Vec3(0, HypertubeCurve.RADIUS + 0.05, 0);
                line(lines, m, nm, pts.get(i).add(lift), pts.get(i + 1).add(lift), 1, 0.6F, 0.2F, 1);
                line(lines, m, nm, pts.get(i).subtract(lift), pts.get(i + 1).subtract(lift), 1, 0.6F, 0.2F, 1);
            }
        }
        buffers.endBatch(RenderType.lines());
        pose.popPose();
    }

    /** 0.20.46 (audit) : monde client decharge (changement de dimension) : ses vues et la visee sont oubliees. */
    @SubscribeEvent
    public static void onLevelUnload(LevelEvent.Unload event) {
        if (event.getLevel().isClientSide() && event.getLevel() instanceof Level level) {
            HypertubeClientViews.clear(level);
            reset();
        }
    }

    /** 0.20.46 (audit) : deconnexion : plus aucune vue ni visee gardee. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        HypertubeClientViews.clear(null);
        HypertubeRenderer.GlassPass.clear();
        reset();
    }

    private static void reset() {
        start = target = null;
        curve = null;
        plan = null;
        planKey = null;
        aimedTube = null;
        ok = false;
    }

    /** Cle d'une ancre pour le cache du trace. */
    private static String key(HypertubeAnchor a) {
        Vec3 o = a.out() != null ? a.out() : a.axis() != null ? a.axis() : Vec3.ZERO;
        return String.format(Locale.ROOT, "%.2f,%.2f,%.2f;%.3f,%.3f,%.3f;%d", a.pos().x, a.pos().y, a.pos().z, o.x, o.y, o.z, a.segment());
    }

    private static void marker(PoseStack pose, VertexConsumer lines, HypertubeAnchor a, float r, float g, float b) {
        double s = 0.22;
        LevelRenderer.renderLineBox(pose, lines, new AABB(a.pos(), a.pos()).inflate(s), r, g, b, 1);
        if (a.fresh()) {
            //Case libre : un ancrage y sera pose
            LevelRenderer.renderLineBox(pose, lines, new AABB(BlockPos.containing(a.pos())).deflate(0.06), r, g, b, 0.6F);
        }
        if (a.axis() != null) {
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            line(lines, m, nm, a.pos().subtract(a.axis().scale(0.7)), a.pos().add(a.axis().scale(0.7)), r, g, b, 1);
        }
        if (a.out() != null) {
            Matrix4f m = pose.last().pose();
            Matrix3f nm = pose.last().normal();
            line(lines, m, nm, a.pos(), a.pos().add(a.out().scale(0.8)), r, g, b, 1);
        }
    }

    private static void line(VertexConsumer vc, Matrix4f m, Matrix3f nm, Vec3 p, Vec3 q, float r, float g, float b, float a) {
        Vec3 d = q.subtract(p);
        double len = d.length();
        if (len < 1e-6) {
            return;
        }
        float nx = (float) (d.x / len), ny = (float) (d.y / len), nz = (float) (d.z / len);
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).normal(nm, nx, ny, nz).endVertex();
        vc.vertex(m, (float) q.x, (float) q.y, (float) q.z).color(r, g, b, a).normal(nm, nx, ny, nz).endVertex();
    }
}
