package fr.pioneers.farm.client.device;

import fr.pioneers.Pioneers;
import fr.pioneers.farm.crow.Crow;
import net.minecraft.client.model.HierarchicalModel;
import net.minecraft.client.model.geom.ModelLayerLocation;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;

/**
 * Corbeau. 1.0.5-beta (lot 4, spec §3 « nouveau modele ») : silhouette de corvide, texture 64 x 64 ({@code T:fauna/crow_art.py}, plumage
 * noir aux reflets bleutes) :
 * <ul>
 *     <li>corps : dos, poitrail bombe, manteau ; queue en EVENTAIL (3 rectrices, ouverte en vol) ;</li>
 *     <li>tete : crane, plumes de gorge herissees, BEC FORT (mandibule superieure epaisse et pointe busquee, mandibule inferieure, soies
 *     nasales) ;</li>
 *     <li>ailes en deux segments (bras, main) et 4 remiges ecartees au bout (les « doigts » du corbeau) ; repliees le long du corps au sol ;</li>
 *     <li>pattes : tarse et doigts (3 devant, 1 derriere).</li>
 * </ul>
 * Animations ({@link #pose}, PUR : le banc {@code tools/farmtest/AnimalGeom.java} pose le vrai modele) : vol battu (bras et main
 * dephases), plane (ailes tendues, remiges et queue ouvertes), envol (grands battements, corps cabre, pattes pendantes), pose (ailes
 * repliees), sautillement (le corbeau sautille a pieds joints), picorage (la tete plonge, le corps penche).
 */
public class CrowModel extends HierarchicalModel<Crow> {

    public static final ModelLayerLocation LAYER = new ModelLayerLocation(Pioneers.rl("crow"), "main");
    public static final int TEX_W = 64, TEX_H = 64;
    /** Pivots au repos (px du modele ; sol a y 24, l'avant vers -z). */
    static final float BODY_Y = 19.5F, HEAD_Y = 17.8F, HEAD_Z = -4.0F, WING_X = 2.4F, WING_Y = 17.6F, WING_Z = -2.4F, LEG_Y = 21.3F;
    /** Duree de l'envol (ticks depuis que le corbeau a quitte le sol). */
    public static final float TAKEOFF_TICKS = 14F;

    private final ModelPart root, body, head, beak, tail, tailLeft, tailRight, leftWing, rightWing, leftHand, rightHand, leftLeg, rightLeg;
    private final ModelPart[] leftTips = new ModelPart[4], rightTips = new ModelPart[4];

    /**
     * Etat d'une image (pur) : {@code glide} 0 = battements .. 1 = ailes tendues ; {@code flap}/{@code flapSpeed} : phase et force des
     * battements ; {@code takeoff} 1 a l'instant de l'envol .. 0 ; {@code hop}/{@code hopAmount} : pas au sol ; angles en degres.
     */
    public record Pose(float age, float headYaw, float headPitch, boolean flying, float glide, float flap, float flapSpeed, boolean pecking,
                       float takeoff, float hop, float hopAmount) {

        public static Pose rest(float age) {
            return new Pose(age, 0, 0, false, 0, 0, 0, false, 0, 0, 0);
        }
    }

    public CrowModel(ModelPart root) {
        this.root = root;
        this.body = root.getChild("body");
        this.tail = body.getChild("tail");
        this.tailLeft = tail.getChild("tail_left");
        this.tailRight = tail.getChild("tail_right");
        this.head = root.getChild("head");
        this.beak = head.getChild("beak");
        this.leftWing = root.getChild("left_wing");
        this.rightWing = root.getChild("right_wing");
        this.leftHand = leftWing.getChild("left_hand");
        this.rightHand = rightWing.getChild("right_hand");
        for (int i = 0; i < 4; i++) {
            leftTips[i] = leftHand.getChild("left_tip" + i);
            rightTips[i] = rightHand.getChild("right_tip" + i);
        }
        this.leftLeg = root.getChild("left_leg");
        this.rightLeg = root.getChild("right_leg");
    }

    public static LayerDefinition create() {
        MeshDefinition mesh = new MeshDefinition();
        PartDefinition r = mesh.getRoot();
        PartDefinition body = r.addOrReplaceChild("body", CubeListBuilder.create()
              .texOffs(0, 0).addBox(-2.5F, -2.5F, -4F, 5, 5, 8)
              .texOffs(28, 0).addBox(-2F, -2.2F, -5.4F, 4, 4.4F, 2.4F)
              .texOffs(42, 0).addBox(-2F, -3.1F, -3F, 4, 1, 6), PartPose.offset(0, BODY_Y, 0));
        PartDefinition tail = body.addOrReplaceChild("tail", CubeListBuilder.create().texOffs(0, 15).addBox(-1F, -0.3F, 0, 2, 0.6F, 7.5F),
              PartPose.offset(0, -1.4F, 3.6F));
        tail.addOrReplaceChild("tail_left", CubeListBuilder.create().texOffs(20, 15).addBox(-0.8F, -0.25F, 0, 1.6F, 0.5F, 7),
              PartPose.offset(0.7F, 0.05F, 0.2F));
        tail.addOrReplaceChild("tail_right", CubeListBuilder.create().texOffs(20, 15).mirror().addBox(-0.8F, -0.25F, 0, 1.6F, 0.5F, 7),
              PartPose.offset(-0.7F, 0.05F, 0.2F));
        PartDefinition head = r.addOrReplaceChild("head", CubeListBuilder.create()
              .texOffs(0, 24).addBox(-2F, -3.4F, -3.4F, 4, 4, 4)
              .texOffs(16, 24).addBox(-1.5F, -0.2F, -3F, 3, 1.4F, 2.4F), PartPose.offset(0, HEAD_Y, HEAD_Z));
        head.addOrReplaceChild("beak", CubeListBuilder.create()
              .texOffs(28, 24).addBox(-1F, -0.8F, -2.6F, 2, 1.6F, 2.6F)
              .texOffs(38, 24).addBox(-0.6F, -0.5F, -4.4F, 1.2F, 1.2F, 1.9F)
              .texOffs(46, 24).addBox(-0.7F, 0.6F, -3.8F, 1.4F, 0.6F, 3.2F)
              .texOffs(56, 24).addBox(-1.1F, -1F, -1F, 2.2F, 0.7F, 1.2F), PartPose.offset(0, -1.6F, -3.4F));
        wing(r, "left", 1);
        wing(r, "right", -1);
        leg(r, "left_leg", 1.1F);
        leg(r, "right_leg", -1.1F);
        return LayerDefinition.create(mesh, TEX_W, TEX_H);
    }

    /** Aile : bras (4,5 x 5), main (4 x 4,3), 4 remiges ; {@code s} = 1 a gauche (+x), -1 a droite (miroir). */
    private static void wing(PartDefinition r, String side, int s) {
        boolean m = s < 0;
        PartDefinition arm = r.addOrReplaceChild(side + "_wing", CubeListBuilder.create().texOffs(0, 34).mirror(m)
              .addBox(m ? -4.5F : 0, -0.5F, 0, 4.5F, 1, 5), PartPose.offset(s * WING_X, WING_Y, WING_Z));
        PartDefinition hand = arm.addOrReplaceChild(side + "_hand", CubeListBuilder.create().texOffs(20, 34).mirror(m)
              .addBox(m ? -4F : 0, -0.35F, 0, 4, 0.7F, 4.3F), PartPose.offset(s * 4.5F, 0, 0.2F));
        for (int i = 0; i < 4; i++) {
            hand.addOrReplaceChild(side + "_tip" + i, CubeListBuilder.create().texOffs(38, 34 + i * 2).mirror(m)
                  .addBox(m ? -2.8F : 0, -0.2F, -0.45F, 2.8F, 0.4F, 0.9F), PartPose.offset(s * 3.8F, 0, 0.55F + i * 1.05F));
        }
    }

    private static void leg(PartDefinition r, String name, float x) {
        r.addOrReplaceChild(name, CubeListBuilder.create()
              .texOffs(0, 44).addBox(-0.45F, 0, -0.45F, 0.9F, 2.4F, 0.9F)
              .texOffs(6, 44).addBox(-0.9F, 2.3F, -1.7F, 1.8F, 0.4F, 2.4F)
              .texOffs(16, 44).addBox(-0.3F, 2.3F, 0.4F, 0.6F, 0.4F, 1), PartPose.offset(x, LEG_Y, 0.6F));
    }

    @Override
    public ModelPart root() {
        return root;
    }

    @Override
    public void setupAnim(Crow crow, float limbSwing, float limbAmount, float age, float yaw, float pitch) {
        float pt = age - (int) age;
        boolean flying = crow.isFlying();
        float takeoff = flying ? Math.max(0, 1 - (crow.airTicks + pt) / TAKEOFF_TICKS) : 0;
        pose(new Pose(age, yaw, pitch, flying, Mth.lerp(pt, crow.oGlide, crow.glide), Mth.lerp(pt, crow.oFlap, crow.flap),
              Mth.lerp(pt, crow.oFlapSpeed, crow.flapSpeed), crow.pecking() && !flying, takeoff, limbSwing, limbAmount));
    }

    /** Pose de l'image (PUR : toutes les valeurs animees sont ecrites a chaque appel). */
    public void pose(Pose p) {
        float t = p.age();
        root.y = 0;
        body.y = BODY_Y;
        body.xRot = -0.12F;
        head.y = HEAD_Y;
        head.z = HEAD_Z;
        head.yRot = p.headYaw() * Mth.DEG_TO_RAD;
        head.xRot = p.headPitch() * Mth.DEG_TO_RAD;
        beak.xRot = 0;
        tail.xRot = -0.22F + Mth.sin(t * 0.2F) * 0.03F;
        tailLeft.yRot = 0.07F;
        tailRight.yRot = -0.07F;
        leftLeg.y = rightLeg.y = LEG_Y;
        leftLeg.xRot = rightLeg.xRot = 0;
        if (p.flying()) {
            float g = Mth.clamp(p.glide(), 0, 1), up = Mth.clamp(p.takeoff(), 0, 1);
            //Battements : la main suit le bras avec un retard (aile qui fouette) ; a l'envol, grands battements, corps cabre
            float speed = Math.max(p.flapSpeed(), up);
            float beat = Mth.sin(p.flap() * 0.9F) * speed * (1 + 0.45F * up);
            float lag = Mth.sin(p.flap() * 0.9F - 0.7F) * speed;
            float glideArm = -0.1F - 0.035F * Mth.sin(t * 0.25F);
            float arm = Mth.lerp(g, -0.15F - beat * 1.0F, glideArm);
            float hand = Mth.lerp(g, -lag * 0.5F, -0.06F);
            spread(arm, hand, 0, 0);
            float fan = Mth.lerp(g, 0.1F, 0.24F);
            for (int i = 0; i < 4; i++) {
                leftTips[i].yRot = -(i - 1.5F) * fan;
                rightTips[i].yRot = (i - 1.5F) * fan;
                leftTips[i].zRot = rightTips[i].zRot = 0;
            }
            body.xRot = Mth.lerp(g, -0.06F, 0.03F) - 0.4F * up;
            head.xRot += 0.25F * up;
            tail.xRot = Mth.lerp(g, -0.06F, 0.0F) + 0.25F * up;
            tailLeft.yRot = 0.07F + 0.3F * g + 0.12F * up;
            tailRight.yRot = -tailLeft.yRot;
            //Pattes repliees sous le ventre en vol, pendantes a l'envol
            leftLeg.xRot = rightLeg.xRot = Mth.lerp(up, 1.25F, 0.25F);
            return;
        }
        //Au sol : ailes repliees le long du corps (remiges sur la queue)
        fold(Mth.sin(t * 0.08F) * 0.015F);
        if (p.pecking()) {
            //Picorage : la tete plonge par saccades, le corps penche en avant
            float peck = Mth.abs(Mth.sin(t * 0.9F));
            head.xRot = 0.5F + peck * 0.45F;
            head.y = HEAD_Y + 0.4F + peck * 0.4F;
            head.z = HEAD_Z - 0.3F;
            body.xRot = 0.16F + 0.06F * peck;
            tail.xRot = -0.5F;
            beak.xRot = 0.05F;
            return;
        }
        //Sautillement : le corbeau avance par petits bonds a pieds joints
        float bounce = Mth.abs(Mth.sin(p.hop() * 1.3F)) * Mth.clamp(p.hopAmount() * 1.6F, 0, 1);
        if (bounce > 0) {
            root.y = -bounce * 1.8F;
            leftLeg.xRot = rightLeg.xRot = -0.45F * bounce;
            body.xRot = -0.12F - 0.12F * bounce;
            tail.xRot = -0.22F + 0.2F * bounce;
            leftWing.zRot += 0.12F * bounce;
            rightWing.zRot -= 0.12F * bounce;
        }
    }

    /** Ailes ouvertes : envergure selon x ; {@code arm}/{@code hand} : angle (rad) du bras et de la main, negatif = vers le haut. */
    private void spread(float arm, float hand, float yArm, float yHand) {
        leftWing.xRot = rightWing.xRot = 0;
        leftWing.yRot = yArm;
        rightWing.yRot = -yArm;
        leftWing.zRot = arm;
        rightWing.zRot = -arm;
        leftHand.xRot = rightHand.xRot = 0;
        leftHand.yRot = yHand;
        rightHand.yRot = -yHand;
        leftHand.zRot = hand;
        rightHand.zRot = -hand;
    }

    /** Ailes repliees : bras tourne vers l'arriere et rabattu le long du flanc, main dans le prolongement, remiges serrees. */
    private void fold(float breathe) {
        leftWing.xRot = rightWing.xRot = -1.5F;
        leftWing.yRot = -1.52F;
        rightWing.yRot = 1.52F;
        leftWing.zRot = -0.06F - breathe;
        rightWing.zRot = 0.06F + breathe;
        leftHand.xRot = rightHand.xRot = 0;
        leftHand.yRot = 0.1F;
        rightHand.yRot = -0.1F;
        leftHand.zRot = rightHand.zRot = 0;
        for (int i = 0; i < 4; i++) {
            leftTips[i].yRot = 0.04F * i;
            rightTips[i].yRot = -0.04F * i;
            leftTips[i].zRot = rightTips[i].zRot = 0;
        }
    }
}
