package fr.pioneers.client.nuclear;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.core.RenderClock;
import fr.pioneers.core.machine.MachineBlock;
import fr.pioneers.industry.multi.ControllerBlock;
import fr.pioneers.industry.multi.ControllerTile;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Matrix4f;

/**
 * 1.0.4-beta (lot 25) : ECRAN DU CONTROLEUR EN MARCHE, pour TOUS les multiblocs industriels (« une animation pour chacun » : meme ceux dont
 * la machine est enfermee dans des toles) : une ligne de balayage lumineuse descend l'ecran (une passe toutes les 1,6 s) et un point
 * d'activite clignote a cote de la barre d'etat. Deux quads additifs, 0,1 px devant l'ecran (dans la niche, 0,8 px derriere la facade : rien
 * ne depasse du bloc), seulement quand le bloc est ACTIVE. Ecran du modele : {@code T:industry/visuals.py controller_model} (de x 3,2 a 12,8,
 * y 5,6 a 12,6, face a z 0,9, facade au nord avant rotation).
 */
final class ControllerScreens {

    private static final float X0 = 3.4F, X1 = 12.6F, Y0 = 5.8F, Y1 = 12.4F, Z = 0.8F;

    private ControllerScreens() {
    }

    static void render(ControllerTile tile, float partialTick, PoseStack pose, MultiBufferSource buffers) {
        Level level = tile.getLevel();
        BlockState state = tile.getBlockState();
        if (level == null || !state.hasProperty(MachineBlock.ACTIVE) || !state.getValue(MachineBlock.ACTIVE)
            || !state.hasProperty(ControllerBlock.FACING)) {
            return;
        }
        float t = (float) (RenderClock.time(partialTick) % 24_000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue
        //facade du modele au nord : rotation de l'etat (y de l'etat = 180 + y du sens), autour du centre du bloc
        float rot = (state.getValue(ControllerBlock.FACING).toYRot() + 180) % 360;
        pose.pushPose();
        pose.translate(0.5, 0.5, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(-rot));
        pose.translate(-0.5, -0.5, -0.5);
        pose.scale(1 / 16F, 1 / 16F, 1 / 16F);
        Matrix4f m = pose.last().pose();
        VertexConsumer b = buffers.getBuffer(RenderType.eyes(RenderReactorCore.GLOW));
        //ligne de balayage (du haut vers le bas), plus pale en bout de course
        float f = (t % 32F) / 32F;
        float y = Y1 - 0.5F - f * (Y1 - Y0 - 0.9F), k = 0.55F * (1 - f * 0.6F);
        quad(b, m, X0, y, X1, y + 0.45F, 0.35F * k, 0.95F * k, 0.75F * k);
        //point d'activite (clignote)
        if ((int) (t / 10) % 2 == 0) {
            quad(b, m, X1 - 1.6F, Y0 + 0.2F, X1 - 0.7F, Y0 + 1.1F, 0.25F, 0.9F, 0.4F);
        }
        pose.popPose();
    }

    /** Rectangle face au nord (vers -z), a z = {@link #Z} px. */
    private static void quad(VertexConsumer b, Matrix4f m, float x0, float y0, float x1, float y1, float r, float g, float bl) {
        CoreDraw.quad(b, m, new float[][]{{x1, y0, Z, 0, 1}, {x0, y0, Z, 1, 1}, {x0, y1, Z, 1, 0}, {x1, y1, Z, 0, 0}}, r, g, bl, 1,
              RenderReactorCore.FULL, false);
    }
}
