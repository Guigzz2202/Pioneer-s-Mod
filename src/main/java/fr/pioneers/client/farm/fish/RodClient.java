package fr.pioneers.client.farm.fish;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.farm.fish.PiBobber;
import fr.pioneers.farm.fish.PiRodItem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix4f;
import org.joml.Vector3f;

/**
 * 1.6 (Guillaume : « rendre plus fluide l'animation de la peche quand on lance ») : geste du lancer. Armer : la canne se releve vers
 * l'arriere, la main monte (et tremble un peu a pleine force) ; lacher : la canne fouette vers l'avant, depasse la position de repos vers
 * l'eau, puis revient (12 ticks, amplitude selon l'armement). Premiere personne : {@link #applyForgeHandTransform} ; troisieme personne : bras
 * leve puis abattu ({@link #CASTING}). Le bout de la canne ({@link #tipOffset}) sert d'attache au fil ({@link BobberRenderer}). Meme calcul
 * en Python pour l'apercu : {@code tools/hand/rod_cast.py}.
 */
public final class RodClient implements IClientItemExtensions {

    public static final RodClient INSTANCE = new RodClient();

    /** Temps des cles du fouet (ticks depuis le lacher). */
    static final float[] KEYS = {0, 2.5F, 6, 12};
    public static final float WHIP_TICKS = 12;
    /** Pivot de la canne en premiere personne (la poignee, dans le repere de la main). */
    private static final float PIVOT_Y = -0.324F, PIVOT_Z = 0.204F;
    /** Bras au repos en troisieme personne (pose ITEM sans balancement), leve a pleine force, abaisse au bout du fouet. */
    static final float ARM_REST = -Mth.PI / 10, ARM_UP = -2.8F, ARM_DOWN = 0.12F;

    /** Bras leve (armer) puis abattu (fouet), en troisieme personne (cree au chargement des objets, pendant le chargement du mod). */
    private static final HumanoidModel.ArmPose CASTING = HumanoidModel.ArmPose.create("PIONEERS_CASTING", false, (model, entity, arm) -> {
        if (!(entity instanceof Player player)) {
            return;
        }
        float[] g = gesture(player, Minecraft.getInstance().getFrameTime());
        if (g == null) {
            return;
        }
        ModelPart part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        part.xRot = part.xRot * 0.5F * (1 - g[6]) + ARM_REST + armDelta(g);
        part.yRot = i * -0.15F * g[7] + model.head.yRot * 0.4F * g[6];
        part.zRot = i * 0.10F * g[7];
    });

    private RodClient() {
    }

    static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Valeur aux cles {@link #KEYS} (segments lisses). */
    static float keyed(float t, float... values) {
        if (t <= KEYS[0]) {
            return values[0];
        }
        for (int k = 1; k < KEYS.length; k++) {
            if (t <= KEYS[k]) {
                return Mth.lerp(smooth((t - KEYS[k - 1]) / (KEYS[k] - KEYS[k - 1])), values[k - 1], values[k]);
            }
        }
        return values[values.length - 1];
    }

    /**
     * Etat du geste de ce joueur, null s'il n'arme ni ne vient de lancer. Valeurs : angle de la canne (degres, positif = relevee vers
     * l'arriere), roulis, deplacement de la main x/y/z (premiere personne), force (0..1), poids du geste dans le bras (0..1), part
     * « bras leve » (0..1).
     */
    @Nullable
    static float[] gesture(Player player, float partial) {
        if (PiRodItem.charging(player)) {
            float ticks = PiRodItem.chargeTicks(player, partial);
            float c = PiBobber.windUp(ticks), a = 28 * c, r = 12 * c;
            if (ticks >= PiBobber.CHARGE_TICKS) {
                float t = player.tickCount + partial;
                a += 0.8F * Mth.sin(t * 1.9F);
                r += 0.5F * Mth.sin(t * 2.3F);
            }
            return new float[]{a, r, -0.09F * c, 0.20F * c, 0.07F * c, c, Math.min(1, c * 3), c};
        }
        float[] cast = PiBobber.recentCast(player, partial);
        if (cast == null || cast[0] > WHIP_TICKS) {
            return null;
        }
        //Le fouet part de la pose armee au moment du lacher (k = part d'armement)
        float t = cast[0], k = cast[1];
        float a = keyed(t, 28 * k, -10 - 15 * k, -6 - 8 * k, 0);
        float r = keyed(t, 12 * k, 0, 0, 0);
        float lx = keyed(t, -0.09F * k, 0, 0, 0), ly = keyed(t, 0.20F * k, -0.04F * k, -0.02F * k, 0), lz = keyed(t, 0.07F * k, -0.12F * k, -0.07F * k, 0);
        float weight = keyed(t, 1, 1, 1, 0), up = keyed(t, k, 0, 0, 0);
        return new float[]{a, r, lx, ly, lz, k, weight, up};
    }

    /** Ecart du bras (troisieme personne) au repos : leve en armant, abattu au fouet. */
    static float armDelta(float[] g) {
        //L'angle de la canne (premiere personne) pilote le bras : +28 degres = bras leve derriere la tete, -25 = bras abaisse, canne pointee
        //droit devant vers l'eau
        float a = g[0];
        return a >= 0 ? (ARM_UP - ARM_REST) * a / 28F : (ARM_DOWN - ARM_REST) * -a / 25F;
    }

    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        if (!(entity instanceof Player player) || PiBobber.rodHand(player) != hand) {
            return null;
        }
        return gesture(player, Minecraft.getInstance().getFrameTime()) != null ? CASTING : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick, float equip,
          float swing) {
        InteractionHand hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        boolean using = player.isUsingItem() && player.getUsedItemHand() == hand && player.getUseItemRemainingTicks() > 0;
        boolean rodHand = PiBobber.rodHand(player) == hand;
        float[] g = rodHand ? gesture(player, partialTick) : null;
        if (g == null) {
            if (!using) {
                if (!rodHand) {
                    return false;
                }
                //1.0.2-beta : canne au repos (bouchon a l'eau) : la pose vanilla, refaite ici pour relever le bout de la canne (attache du fil)
                vanillaIdle(pose, i, equip, swing);
                captureFirstPerson(pose, player, stack, arm);
                return true;
            }
            //Moulinet (combat) : la canne reste en main, un leger tremblement quand le fil est tres tendu
            pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
            PiBobber b = PiBobber.of(player);
            float tension = b != null && b.state() == PiBobber.FIGHT ? b.tension() : 0;
            if (tension > 0.7F) {
                float t = player.tickCount + partialTick, s = (tension - 0.7F) * 0.02F;
                pose.translate(Mth.sin(t * 2.7F) * s, Mth.sin(t * 3.3F) * s, 0);
            }
            if (rodHand) {
                captureFirstPerson(pose, player, stack, arm);
            }
            return true;
        }
        pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        pose.translate(i * g[2], g[3], g[4]);
        pose.translate(0, PIVOT_Y, PIVOT_Z);
        pose.mulPose(Axis.XP.rotationDegrees(g[0]));
        pose.mulPose(Axis.ZP.rotationDegrees(i * g[1]));
        pose.translate(0, -PIVOT_Y, -PIVOT_Z);
        captureFirstPerson(pose, player, stack, arm);
        return true;
    }

    /** Pose vanilla d'un objet tenu sans etre utilise (ItemInHandRenderer.renderArmWithItem : balancement du coup, bras, geste d'attaque). */
    private static void vanillaIdle(PoseStack pose, int i, float equip, float swing) {
        float f5 = -0.4F * Mth.sin(Mth.sqrt(swing) * Mth.PI), f6 = 0.2F * Mth.sin(Mth.sqrt(swing) * Mth.PI * 2), f10 = -0.2F * Mth.sin(swing * Mth.PI);
        pose.translate(i * f5, f6, f10);
        pose.translate(i * 0.56F, -0.52F + equip * -0.6F, -0.72F);
        float f = Mth.sin(swing * swing * Mth.PI);
        pose.mulPose(Axis.YP.rotationDegrees(i * (45.0F + f * -20.0F)));
        float f1 = Mth.sin(Mth.sqrt(swing) * Mth.PI);
        pose.mulPose(Axis.ZP.rotationDegrees(i * f1 * -20.0F));
        pose.mulPose(Axis.XP.rotationDegrees(f1 * -80.0F));
        pose.mulPose(Axis.YP.rotationDegrees(i * -45.0F));
    }

    // ------------------------------------------------------------------ 1.0.2-beta : bout de la canne RELEVE au rendu (attache du fil)
    /*
     * Guillaume : « le fil de la canne a peche bouge de son point d'origine lors du lancer ». Avant, le bout de la canne etait ESTIME (decalage
     * du geste ajoute aux constantes du fil vanilla, bras de la 3e personne approche par un levier fixe) : pendant le lancer, le fil partait a
     * cote de la canne. Il est maintenant RELEVE la ou la canne est dessinee, avec les memes transformations que le rendu : 1re personne a la
     * fin de applyForgeHandTransform (pose de la main, balancement et oscillation de la vue compris, puis vue firstperson du modele cuit), 3e
     * personne par la couche RodTipLayer (bras pose par l'animation du joueur, vue thirdperson). Le fil s'y attache pendant tout le lancer ;
     * sans releve recent (canne pas encore dessinee), l'ancienne estimation sert de repli.
     */

    /** Bout de la canne dans le modele (px) : droite, ployee (bend 0,4), tres ployee (0,8) ; memes points que models/item/held3d/*_fishing_rod*. */
    private static final float[][] TIP_PX = {{14.3F, 14.8F, 8F}, {15.9F, 12.7F, 8F}, {16.9F, 10.1F, 8F}};
    /** Releve trop vieux (ms) : on revient a l'estimation. */
    private static final long FRESH_MS = 120;
    private static float[] fpTip;
    private static long fpTime;
    private static final java.util.Map<Integer, double[]> TP_TIPS = new java.util.HashMap<>();

    /** Point du bout de la canne dans le repere du modele de l'objet (blocs, avant le recentrage de -0,5). */
    static org.joml.Vector4f tipPoint(Player player) {
        PiBobber b = PiBobber.of(player);
        float bend = b != null && b.state() == PiBobber.FIGHT ? b.tension() : 0;
        float[] t = TIP_PX[bend >= 0.8F ? 2 : bend >= 0.4F ? 1 : 0];
        return new org.joml.Vector4f(t[0] / 16F, t[1] / 16F, t[2] / 16F, 1);
    }

    /** Applique a {@code pose} la vue {@code context} du modele cuit de la canne (memes regles que le rendu de l'objet) puis le recentrage. */
    static void applyModelView(PoseStack pose, Player player, ItemStack stack, net.minecraft.world.item.ItemDisplayContext context, boolean left) {
        Minecraft mc = Minecraft.getInstance();
        net.minecraft.client.resources.model.BakedModel model = mc.getItemRenderer().getModel(stack, player.level(), player, player.getId());
        model.applyTransform(context, pose, left);
        pose.translate(-0.5F, -0.5F, -0.5F);
    }

    /** 1re personne : releve du bout de la canne a l'ecran (coordonnees du plan proche, -1..1, champ de la main 70 degres). */
    private static void captureFirstPerson(PoseStack pose, LocalPlayer player, ItemStack stack, HumanoidArm arm) {
        boolean left = arm == HumanoidArm.LEFT;
        pose.pushPose();
        applyModelView(pose, player, stack, left ? net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_LEFT_HAND
                                                 : net.minecraft.world.item.ItemDisplayContext.FIRST_PERSON_RIGHT_HAND, left);
        org.joml.Vector4f v = pose.last().pose().transform(tipPoint(player));
        pose.popPose();
        if (v.z > -0.05F) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        float tan = (float) Math.tan(35 * Mth.DEG_TO_RAD);
        fpTip = new float[]{v.x / (-v.z * tan * aspect), v.y / (-v.z * tan)};
        fpTime = net.minecraft.Util.getMillis();
    }

    /** 1re personne : bout de la canne releve a la derniere image (plan proche, -1..1), ou null. */
    @Nullable
    public static float[] firstPersonTip() {
        return fpTip != null && net.minecraft.Util.getMillis() - fpTime < FRESH_MS ? fpTip : null;
    }

    /** 3e personne : releve du bout de la canne (position dans le monde), par {@link RodTipLayer}. */
    static void storeThirdPerson(Player player, double x, double y, double z) {
        TP_TIPS.put(player.getId(), new double[]{x, y, z, net.minecraft.Util.getMillis()});
    }

    /** 3e personne : bout de la canne de ce joueur releve a la derniere image (monde), ou null. */
    @Nullable
    public static net.minecraft.world.phys.Vec3 thirdPersonTip(Player player) {
        double[] t = TP_TIPS.get(player.getId());
        if (t == null || net.minecraft.Util.getMillis() - (long) t[3] >= FRESH_MS) {
            return null;
        }
        return new net.minecraft.world.phys.Vec3(t[0], t[1], t[2]);
    }

    // ------------------------------------------------------------------ bout de la canne (attache du fil)

    /** Bout de la canne a l'ecran (premiere personne, champ de 70 degres de la main) pour ce geste. */
    private static Vector3f tipView(float[] g, int i) {
        Matrix4f m = new Matrix4f().translate(i * 0.56F, -0.52F, -0.72F);
        if (g != null) {
            m.translate(i * g[2], g[3], g[4]).translate(0, PIVOT_Y, PIVOT_Z).rotateX(g[0] * Mth.DEG_TO_RAD).rotateZ(i * g[1] * Mth.DEG_TO_RAD)
                  .translate(0, -PIVOT_Y, -PIVOT_Z);
        }
        //Vue firstperson_righthand de minecraft:item/handheld_rod : rotation 0 / 90 / 25, translation 0 / 1,6 / 0,8 px, echelle 0,68
        m.translate(0, 1.6F / 16, 0.8F / 16).rotateXYZ(0, i * 90 * Mth.DEG_TO_RAD, i * 25 * Mth.DEG_TO_RAD).scale(0.68F).translate(-0.5F, -0.5F, -0.5F);
        //Bout de la canne sur la texture (32 px : colonne 29, ligne 3)
        return m.transformPosition(new Vector3f(14.75F / 16, 14.25F / 16, 0.5F));
    }

    /**
     * Decalage du bout de la canne par rapport au repos, en coordonnees du plan proche de la camera (x et y de -1 a 1 sur l'ecran) pour la
     * premiere personne, ou {avance, hauteur} en blocs pour la troisieme personne. {0, 0} hors du geste.
     */
    public static float[] tipOffset(Player player, float partial, boolean firstPerson) {
        float[] g = gesture(player, partial);
        if (g == null) {
            return new float[]{0, 0};
        }
        if (firstPerson) {
            int i = PiBobber.rodSide(player);
            Vector3f rest = tipView(null, i), now = tipView(g, i);
            Minecraft mc = Minecraft.getInstance();
            float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
            float tan = (float) Math.tan(35 * Mth.DEG_TO_RAD);
            float rx = rest.x / (-rest.z * tan * aspect), ry = rest.y / (-rest.z * tan);
            float nx = now.z < -0.05F ? now.x / (-now.z * tan * aspect) : rx, ny = now.z < -0.05F ? now.y / (-now.z * tan) : 1.2F;
            return new float[]{Mth.clamp(nx - rx, -1, 1), Mth.clamp(ny - ry, -1, 1.4F)};
        }
        //Troisieme personne : le bout tourne avec le bras autour de l'epaule (vecteur de repos : 0,8 devant, 0,2 sous l'epaule)
        float d = armDelta(g);
        float up = -0.205F, fwd = 0.8F;
        float c = Mth.cos(d), s = Mth.sin(d);
        float up2 = up * c - fwd * s, fwd2 = up * s + fwd * c;
        return new float[]{fwd2 - fwd, up2 - up};
    }
}
