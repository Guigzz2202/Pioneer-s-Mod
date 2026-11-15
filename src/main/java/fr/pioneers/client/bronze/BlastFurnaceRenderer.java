package fr.pioneers.client.bronze;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.bronze.blast.BlastFurnaceTile;
import fr.pioneers.bronze.blast.BlastLayout;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.core.RenderClock;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.world.inventory.InventoryMenu;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * 0.20.40 : le haut fourneau vivant (demande de Guillaume : « plus sympa et anime »), dessine depuis le trou de coulee, repere tourne vers
 * l'avant (cuve en z -16..0) :
 * <ul>
 *     <li>la charge dans la cuve, a la hauteur de son remplissage, rougeoyante quand le four est chaud ;</li>
 *     <li>les flammes qui lechent la charge (plans croises, texture animee, balancement) ; 1.0.4-beta (lot 25) : elles restent dans la cuve,
 *     la flamme et la fumee du gueulard sont des particules ;</li>
 *     <li>1.0.4-beta (lot 24) : plus de coulee dessinee devant le trou de coulee (controleur plein).</li>
 * </ul>
 * Les briques rougeoient d'elles-memes (etat {@code heat}) et les tuyeres s'allument (etat {@code lit}). {@link #draw} sert aussi au banc.
 */
public class BlastFurnaceRenderer implements BlockEntityRenderer<BlastFurnaceTile> {

    /**
     * 0.20.46 (audit) : type de rendu garde (les plans de flamme n'ont qu'une face : on reste sans elimination des faces arriere, seule la
     * recherche du type a chaque image disparait).
     */
    @Nullable
    private static RenderType type;

    public BlastFurnaceRenderer(BlockEntityRendererProvider.Context context) {
    }

    private static RenderType type() {
        RenderType t = type;
        if (t == null) {
            t = RenderType.entityCutoutNoCull(InventoryMenu.BLOCK_ATLAS);
            type = t;
        }
        return t;
    }

    @Override
    public void render(@NotNull BlastFurnaceTile tile, float partial, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (!tile.shownFormed() || tile.getLevel() == null) {
            return;
        }
        float time = (float) (RenderClock.time(partial) % 24_000.0);   //1.0.5-beta (lot 29) : horloge de rendu continue (l'heure du monde recule chaque seconde sur un serveur lent)
        VertexConsumer vc = buffers.getBuffer(type());
        draw(pose, vc, tile.facing(), light, overlay, tile.shownTemperature(), tile.shownFill(), tile.shownBurning(), tile.shownPour(), time);
    }

    /** Dessin complet (aussi appele par le banc d'apercu). */
    public static void draw(PoseStack pose, VertexConsumer vc, Direction facing, int light, int overlay, int temperature, float fill, boolean burning,
          int pour, float time) {
        float heat = Math.max(0, Math.min(1, (temperature - 400) / 1200F));
        boolean hot = burning && temperature > 700;
        int glow = hot ? LightTexture.FULL_BRIGHT : light;
        //Dessus de la charge : du creuset (y 16) au gueulard (y 16 x HEIGHT)
        float top = 16 + Math.max(0.05F, fill) * (16 * (BlastLayout.HEIGHT - 1) - 20);
        //1.0.4-beta (lot 24) : la cuve est a BlastLayout.AXIS_DEPTH cases derriere le trou de coulee (forme de Guillaume)
        float back = -16F * (BlastLayout.AXIS_DEPTH - 1);
        //Un seul repere (les KDraw partagent la pile) : avant du four = +z
        KDraw cold = new KDraw(pose, vc, light, overlay), lit = new KDraw(pose, vc, glow, overlay);
        cold.push().face(facing);
        (hot ? lit : cold).box(0.4F, top - 1.5F, back - 15.6F, 15.6F, top, back - 0.4F, hot ? "blast_burden_hot" : "blast_burden",
              hot ? "blast_burden_hot" : "blast_burden");
        //Flammes : quatre plans croises au-dessus de la charge, plus hauts et plus clairs avec la chaleur
        if (burning && temperature > 450) {
            KDraw.Tex flame = KDraw.sprite("blast_flame");
            Matrix4f m = pose.last().pose();
            Matrix3f n = pose.last().normal();
            for (int i = 0; i < 4; i++) {
                float phase = i * 1.7F;
                float h = (9 + 34 * heat) * (0.82F + 0.18F * (float) Math.sin(time * 0.31F + phase));
                //1.0.4-beta (lot 25, « rien ne doit depasser ») : les flammes restent dans la cuve (sous le gueulard : la flamme qui en sort
                //est en particules, BlastFurnaceTile.clientTick) et dans la colonne (plan de 12 px, balancement compris)
                h = Math.max(0, Math.min(h, 16 * BlastLayout.HEIGHT - 0.25F - (top - 0.5F)));
                float sway = 1.6F * (float) Math.sin(time * 0.19F + phase * 1.3F);
                double a = Math.PI / 4 * i;
                float dx = (float) Math.cos(a) * 6, dz = (float) Math.sin(a) * 6;
                int shade = (int) (200 + 55 * heat);
                plane(vc, m, n, flame, 8 - dx, top - 0.5F, back - 8 - dz, 8 + dx, back - 8 + dz, h, sway, shade, LightTexture.FULL_BRIGHT, overlay);
            }
        }
        //1.0.4-beta (lot 24, Guillaume : « il faut retirer la coulee devant le controleur ») : plus de jet ni de rigole devant le trou de
        //coulee (controleur plein) ; la coulee se voit aux etincelles et aux lingots qui sortent
        cold.pop();
    }

    /**
     * Plan vertical texture (flamme) de la base (x0, y, z0)-(x1, y, z1), haut de {@code h} px, sommet decale de {@code sway} le long du plan :
     * la texture entiere est etiree sur la flamme (pas de repetition).
     */
    private static void plane(VertexConsumer vc, Matrix4f m, Matrix3f n, KDraw.Tex t, float x0, float y, float z0, float x1, float z1,
          float h, float sway, int shade, int light, int overlay) {
        float lx = x1 - x0, lz = z1 - z0;
        float len = (float) Math.sqrt(lx * lx + lz * lz);
        float ux = len < 1e-4F ? 0 : lx / len, uz = len < 1e-4F ? 0 : lz / len;
        float sx = ux * sway, sz = uz * sway;
        float[][] p = {{x0, y, z0}, {x1, y, z1}, {x1 + sx, y + h, z1 + sz}, {x0 + sx, y + h, z0 + sz}};
        float[][] uv = {{t.u(0), t.v(16)}, {t.u(16), t.v(16)}, {t.u(16), t.v(0)}, {t.u(0), t.v(0)}};
        float nx = -uz, nz = ux;
        for (int k = 0; k < 4; k++) {
            vc.vertex(m, p[k][0] / 16F, p[k][1] / 16F, p[k][2] / 16F).color(shade, shade, shade, 255).uv(uv[k][0], uv[k][1]).overlayCoords(overlay)
                  .uv2(light).normal(n, nx, 0, nz).endVertex();
        }
    }
}
