package fr.pioneers.client.wild;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.hunt.TrophyBlock;
import fr.pioneers.hunt.TrophyTile;
import fr.pioneers.wild.Wild7Rules;
import fr.pioneers.wild.Wild9Rules;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 10, specification §6.4) : rendu des TROPHEES avec le VRAI modele de l'espece (silhouettes du generateur des lots 6, 7 et 9,
 * {@link WildModel}) et sa vraie robe :
 * <ul>
 *   <li>cerf : cou, tete, museau, oreilles et BOIS du male, cou sortant de l'ecusson, tete droite ;</li>
 *   <li>ours : TAPIS au sol (corps aplati, pattes ecartees a plat, tete entiere relevee, gueule vers {@code FACING}) ;</li>
 *   <li>requin (grand requin blanc) : tout le corps de profil le long de la planche, queue et tete legerement courbees, gueule ouverte.</li>
 * </ul>
 * {@link #draw} est le seul code de pose (le banc de la planche l'appelle aussi) : repere du bloc tourne vers FACING, origine au centre du
 * bloc au niveau du sol, mur en z = +0,5.
 */
public class TrophyRenderer implements BlockEntityRenderer<TrophyTile> {

    /** Echelles des betes et profondeur (px depuis le mur) ou elles sortent de l'ecusson. */
    public static final float DEER_SCALE = 0.95F, BEAR_SCALE = 0.8F, SHARK_SCALE = 0.42F;
    public static final float DEER_Y = 6F, DEER_FROM_WALL = 1.6F, SHARK_Y = 8F, SHARK_FROM_WALL = 4.4F;
    /** 1.0.5-beta (lot 13) : cou du cerf et machoire du requin un peu plus etroits (faces hors du plan de la tete : aucun scintillement). */
    public static final float NECK_X = 0.94F, JAW_X = 0.95F;

    /** Les trois modeles (cerf male, ours, grand requin blanc). */
    public static final class Models {
        public final WildModel<WildAnimal> deer, bear, shark;

        public Models() {
            deer = model(WildRules.DEER, WildRules.MALE);
            bear = model(Wild7Rules.BEAR, WildRules.MALE);
            shark = model(Wild9Rules.GREAT_WHITE, WildRules.MALE);
        }

        static WildModel<WildAnimal> model(int kind, int sex) {
            int i = WildRenderer.shapeIndex(kind, sex);
            return new WildModel<>(WildModel.layer(WildRenderer.SHAPES[i]).bakeRoot(), WildRenderer.SHAPES[i]);
        }
    }

    public static final ResourceLocation DEER_TEX = WildRenderer.tex("deer_male"), BEAR_TEX = WildRenderer.tex("bear_brown"),
          SHARK_TEX = WildRenderer.tex("great_white_shark");
    private final Models models = new Models();

    public TrophyRenderer(BlockEntityRendererProvider.Context ctx) {
    }

    public static ResourceLocation texture(TrophyBlock.Kind k) {
        return k == TrophyBlock.Kind.DEER ? DEER_TEX : k == TrophyBlock.Kind.BEAR ? BEAR_TEX : SHARK_TEX;
    }

    @Override
    public void render(@NotNull TrophyTile tile, float pt, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, int overlay) {
        if (!(tile.getBlockState().getBlock() instanceof TrophyBlock b)) {
            return;
        }
        Direction facing = tile.getBlockState().getValue(TrophyBlock.FACING);
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.mulPose(Axis.YP.rotationDegrees(180F - facing.toYRot()));
        draw(b.kind, models, pose, buffers.getBuffer(RenderType.entityCutoutNoCull(texture(b.kind))), light, overlay);
        pose.popPose();
    }

    /** Pose et dessine la bete (repere : centre du bloc au sol, avant = -z, mur en z = +0,5). */
    public static void draw(TrophyBlock.Kind k, Models models, PoseStack pose, VertexConsumer buf, int light, int overlay) {
        switch (k) {
            case DEER -> deer(models.deer, pose, buf, light, overlay);
            case BEAR -> bear(models.bear, pose, buf, light, overlay);
            default -> shark(models.shark, pose, buf, light, overlay);
        }
    }

    static ModelPart part(WildModel<?> m, String name) {
        return m.named.get(name);
    }

    /** Tete de cerf : le cou sort de l'ecusson vers l'avant et vers le haut, la tete droite, les bois a pleine taille. */
    static void deer(WildModel<?> m, PoseStack pose, VertexConsumer buf, int light, int overlay) {
        m.restore();
        ModelPart neck = part(m, "neck"), head = part(m, "head");
        neck.x = neck.y = neck.z = 0;
        neck.xRot = 1.05F;
        head.xRot = 0.244F - 1.05F + 0.12F;
        //1.0.5-beta (lot 13, « bug de superposition du trophee de cerf ») : dans cette pose, le cou et la tete (meme largeur) se recouvrent :
        //leurs flancs etaient dans le MEME plan (scintillement) ; cou un peu plus etroit que la tete (la tete, enfant du cou, garde sa largeur)
        neck.xScale = NECK_X;
        head.xScale = 1F / NECK_X;
        pose.pushPose();
        pose.translate(0, DEER_Y / 16.0, 0.5 - DEER_FROM_WALL / 16.0);
        pose.scale(-DEER_SCALE, -DEER_SCALE, DEER_SCALE);
        neck.render(pose, buf, light, overlay);
        pose.popPose();
    }

    /** Tapis d'ours : corps aplati, pattes ecartees a plat, tete entiere un peu relevee. */
    static void bear(WildModel<?> m, PoseStack pose, VertexConsumer buf, int light, int overlay) {
        m.restore();
        ModelPart body = part(m, "body"), head = part(m, "head");
        pose.pushPose();
        pose.scale(-BEAR_SCALE, -BEAR_SCALE, BEAR_SCALE);
        //corps : seulement sa boite, ecrasee a 1,7 px, centree
        for (ModelPart c : new ModelPart[]{part(m, "hump"), part(m, "tail"), head, part(m, "right_front_leg"), part(m, "left_front_leg"),
                                           part(m, "right_hind_leg"), part(m, "left_hind_leg")}) {
            if (c != null) {
                c.visible = false;
            }
        }
        body.x = 0;
        body.y = 0;
        body.z = 7.5F;
        body.yScale = 0.15F;
        body.xScale = 1.3F;
        body.zScale = 1.05F;
        body.render(pose, buf, light, overlay);
        //pattes a plat, ecartees (avant vers l'avant, arriere vers l'arriere)
        String[] legs = {"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"};
        float[][] at = {{-6.4F, -6.0F}, {6.4F, -6.0F}, {-6.4F, 6.5F}, {6.4F, 6.5F}};
        for (int i = 0; i < 4; i++) {
            ModelPart leg = part(m, legs[i]);
            leg.visible = true;
            boolean right = i % 2 == 0, front = i < 2;
            leg.x = at[i][0];
            leg.y = -1.3F;
            leg.z = at[i][1];
            leg.xRot = front ? -1.5708F : 1.5708F;                         //couchee vers l'avant (pattes de devant) ou l'arriere
            leg.yRot = (right ? 1 : -1) * (front ? 0.55F : -0.55F);        //ecartee vers l'exterieur
            leg.zRot = 0;
            leg.xScale = 1.45F;
            leg.yScale = 1.25F;
            leg.zScale = 0.3F;
            leg.render(pose, buf, light, overlay);
        }
        //tete relevee, posee a l'avant du tapis
        head.visible = true;
        head.x = 0;
        head.y = -4.4F;
        head.z = -9.4F;
        head.xRot = -0.22F;
        head.render(pose, buf, light, overlay);
        pose.popPose();
    }

    /** Requin de profil sur sa planche : corps le long du mur, gueule ouverte, queue et tete legerement courbees. */
    static void shark(WildModel<?> m, PoseStack pose, VertexConsumer buf, int light, int overlay) {
        m.restore();
        ModelPart body = part(m, "body");
        body.x = 0;
        body.y = 0;
        body.z = -5F;
        part(m, "head").yRot = -0.12F;
        part(m, "jaw").xRot = 0.42F;
        part(m, "jaw").xScale = JAW_X;                                      //1.0.5-beta (lot 13) : flancs de la machoire hors du plan de la tete
        part(m, "tail").yRot = 0.22F;
        part(m, "tail_2").yRot = 0.26F;
        pose.pushPose();
        pose.translate(0, SHARK_Y / 16.0, 0.5 - SHARK_FROM_WALL / 16.0);
        pose.mulPose(Axis.YP.rotationDegrees(90F));
        pose.scale(-SHARK_SCALE, -SHARK_SCALE, SHARK_SCALE);
        body.render(pose, buf, light, overlay);
        pose.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen(@NotNull TrophyTile tile) {
        return true;
    }
}
