package fr.pioneers.client.lighting;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.lighting.LightFixtureBlock;
import fr.pioneers.lighting.LightLinks;
import fr.pioneers.lighting.LightSwitchBlock;
import fr.pioneers.network.PacketLighting;
import fr.pioneers.network.PiNetwork;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHighlightEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 5) : APERCU DES LIAISONS de l'eclairage. En regardant un interrupteur ou une lumiere, le client demande ses liaisons
 * toutes les {@link #PERIOD} ticks ({@link PacketLighting.Ask}) ; tant que la reponse est fraiche, un trait part de l'interrupteur vers
 * chacune de ses lumieres (ambre : sur « marche », gris bleute : sur « arret ») et chaque lumiere reliee est cerclee. Apres chaque liaison
 * (le serveur pousse la reponse), les traits restent {@link #SHOW} ticks meme en regardant ailleurs.
 * 1.0.4-beta (lot 33) : tout cela SEULEMENT cle a molette en main (principale ou secondaire, {@link LightLinks#showsLinks}) : sans cle,
 * ni demande au serveur ni trait (meme juste apres une liaison).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LightLinkPreview {

    static final int PERIOD = 10, STALE = 30, SHOW = 60;

    @Nullable
    private static BlockPos target, sw;
    private static boolean on;
    private static List<BlockPos> lights = List.of();
    private static int age = 1_000, sinceAsk;

    private LightLinkPreview() {
    }

    /** Reponse du serveur. */
    public static void receive(@Nullable BlockPos s, boolean isOn, List<BlockPos> ls) {
        sw = s;
        on = isOn;
        lights = List.copyOf(ls);
        age = 0;
    }

    /** Cle a molette en main (principale ou secondaire) : seule condition pour voir les liaisons (lot 33). */
    static boolean wrench() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && LightLinks.showsLinks(mc.player.getMainHandItem(), mc.player.getOffhandItem());
    }

    private static boolean lighting(Block b) {
        return b instanceof LightSwitchBlock || b instanceof LightFixtureBlock;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        age++;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null || mc.screen != null || !wrench()) {
            target = null;
            return;
        }
        HitResult hit = mc.hitResult;
        BlockPos pos = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK ? b.getBlockPos() : null;
        if (pos == null || !lighting(mc.level.getBlockState(pos).getBlock())) {
            target = null;
            return;
        }
        sinceAsk++;
        if (!pos.equals(target) || sinceAsk >= PERIOD) {
            target = pos.immutable();
            sinceAsk = 0;
            PiNetwork.sendToServer(new PacketLighting.Ask(target));
        }
    }

    @SubscribeEvent
    public static void highlight(RenderHighlightEvent.Block event) {
        if (sw == null || lights.isEmpty() || !wrench()) {
            return;
        }
        boolean looking = target != null && event.getTarget().getBlockPos().equals(target) && age < STALE;
        if (!looking && age >= SHOW) {
            return;
        }
        Vec3 cam = event.getCamera().getPosition();
        PoseStack pose = event.getPoseStack();
        VertexConsumer vc = event.getMultiBufferSource().getBuffer(RenderType.lines());
        float r = on ? 1F : 0.55F, g = on ? 0.78F : 0.64F, b = on ? 0.32F : 0.74F;
        Vec3 a = Vec3.atCenterOf(sw).subtract(cam);
        for (BlockPos l : lights) {
            Vec3 c = Vec3.atCenterOf(l).subtract(cam);
            line(pose, vc, a, c, r, g, b);
            LevelRenderer.renderLineBox(pose, vc, new AABB(l).inflate(0.02).move(-cam.x, -cam.y, -cam.z), r, g, b, 0.85F);
        }
        LevelRenderer.renderLineBox(pose, vc, new AABB(sw).inflate(0.04).move(-cam.x, -cam.y, -cam.z), 1F, 0.9F, 0.55F, 1F);
    }

    private static void line(PoseStack pose, VertexConsumer vc, Vec3 a, Vec3 b, float r, float g, float bl) {
        PoseStack.Pose last = pose.last();
        Vec3 n = b.subtract(a).normalize();
        vc.vertex(last.pose(), (float) a.x, (float) a.y, (float) a.z).color(r, g, bl, 1F).normal(last.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
        vc.vertex(last.pose(), (float) b.x, (float) b.y, (float) b.z).color(r, g, bl, 1F).normal(last.normal(), (float) n.x, (float) n.y, (float) n.z).endVertex();
    }
}
