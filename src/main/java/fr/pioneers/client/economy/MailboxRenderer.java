package fr.pioneers.client.economy;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.Pioneers;
import fr.pioneers.core.RenderClock;
import fr.pioneers.economy.mail.MailboxTile;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 1.0.4-beta (lot 29) : le « ! » au-dessus d'une boite aux lettres quand son PROPRIETAIRE (le joueur local) a des lettres non lues
 * ({@link MailClient#unread}) ; personne d'autre ne le voit. Panneau toujours tourne vers la camera, plein eclairage, qui flotte doucement
 * (texture {@code textures/misc/mail_notice.png}, {@code tools/gc/economy/mailbox.py}). Hauteur et taille : {@link #Y}, {@link #W}, {@link #H}.
 */
public class MailboxRenderer implements BlockEntityRenderer<MailboxTile> {

    public static final ResourceLocation TEXTURE = new ResourceLocation(Pioneers.MODID, "textures/misc/mail_notice.png");
    /** Centre du panneau au-dessus du bas du bloc (blocs), demi-largeur et demi-hauteur, amplitude du flottement. */
    public static final float Y = 1.55F, W = 0.16F, H = 0.32F, BOB = 0.06F;

    public MailboxRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    /** Le panneau est-il a dessiner pour ce joueur ? (proprietaire et lettres non lues) */
    public static boolean shown(MailboxTile tile) {
        Minecraft mc = Minecraft.getInstance();
        return mc.player != null && MailClient.unread() > 0 && mc.player.getUUID().equals(tile.owner());
    }

    @Override
    public void render(@NotNull MailboxTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (!shown(tile) || tile.getLevel() == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float t = (float) (RenderClock.time(partial) % 2400.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        pose.pushPose();
        pose.translate(0.5, Y + BOB * Math.sin(t * 0.12), 0.5);
        pose.mulPose(mc.getEntityRenderDispatcher().cameraOrientation());
        float pulse = 1F + 0.05F * (float) Math.sin(t * 0.25);
        pose.scale(pulse, pulse, pulse);
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        quad(vc, m, n, -W, -H, 1, 1);
        quad(vc, m, n, W, -H, 0, 1);
        quad(vc, m, n, W, H, 0, 0);
        quad(vc, m, n, -W, H, 1, 0);
        pose.popPose();
    }

    private static void quad(VertexConsumer vc, Matrix4f m, Matrix3f n, float x, float y, float u, float v) {
        vc.vertex(m, x, y, 0).color(255, 255, 255, 255).uv(u, v).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
              .normal(n, 0, 1, 0).endVertex();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull MailboxTile tile) {
        return true;
    }
}
