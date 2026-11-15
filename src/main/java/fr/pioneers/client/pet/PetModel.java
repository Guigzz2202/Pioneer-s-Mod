package fr.pioneers.client.pet;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.pet.Pet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.animal.Cat;
import net.minecraft.world.entity.animal.Wolf;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.5-beta (lot 5) : MODELE PARAMETRE d'un chien ou d'un chat de race (une silhouette par race : {@link GeneratedPetShapes}). Corps au pivot
 * de la hanche, pattes / poitrail / tete / queue en ENFANTS du corps (rien ne se detache, quelle que soit la pose), museau / oreilles /
 * collier en enfants de la tete. Chaque image repart de la POSE INITIALE (lecon du lot 35 : aucun etat d'une image ou d'une bete a l'autre,
 * meme modele partage par toutes les betes de la race). Poses : debout, pas, course (galop des chiens), assis (theta du corps, cuisses vers
 * l'avant, pattes avant a la verticale : constantes calculees par le generateur pour que les pieds touchent le sol), couche (ventre au sol,
 * pattes avant allongees, pattes arriere le long des flancs), en boule (chat : tete tournee vers le flanc, queue enroulee), tete penchee
 * (chien interesse), ebrouement, queue qui remue, petits (tete plus grosse). Collier : rendu a part, teint ({@link PetRenderer}).
 */
public class PetModel<T extends LivingEntity> extends EntityModel<T> {

    final GeneratedPetShapes.Shape shape;
    final ModelPart root, body, chest, head, snout, tail, collar, rightEar, leftEar;
    final ModelPart tail2;
    final ModelPart[] legs;   // avant droite, avant gauche, arriere droite, arriere gauche
    final List<ModelPart> all;
    /** Vrai le temps du rendu du collier : seul le collier est dessine. */
    boolean collarOnly;
    float headRoll, bodyRoll, tailLift, shade = 1F;
    /** Gonflement du pelage du corps (poil long) : les pattes couchees le long des flancs passent au-dehors. */
    final float fur;

    public PetModel(ModelPart root, GeneratedPetShapes.Shape shape) {
        this.root = root;
        this.shape = shape;
        Map<String, ModelPart> m = new HashMap<>();
        collect(root, m);
        body = m.get("body");
        chest = m.get("chest");
        head = m.get("head");
        snout = m.get("snout");
        tail = m.get("tail");
        tail2 = m.get("tail2");
        collar = m.get("collar");
        rightEar = m.get("right_ear");
        leftEar = m.get("left_ear");
        legs = new ModelPart[]{m.get("right_front_leg"), m.get("left_front_leg"), m.get("right_hind_leg"), m.get("left_hind_leg")};
        all = root.getAllParts().toList();
        float g = 0F;
        for (GeneratedPetShapes.Part p : shape.parts()) {
            if (p.name().equals("body") || p.name().equals("chest")) {
                for (float[] c : p.cubes()) {
                    g = Math.max(g, c[8]);
                }
            }
        }
        fur = g;
    }

    private static void collect(ModelPart p, Map<String, ModelPart> out) {
        for (String n : new String[]{"body", "chest", "head", "snout", "tail", "tail2", "collar", "right_ear", "left_ear", "right_front_leg",
              "left_front_leg", "right_hind_leg", "left_hind_leg"}) {
            find(p, n, out);
        }
    }

    private static void find(ModelPart p, String n, Map<String, ModelPart> out) {
        if (out.containsKey(n)) {
            return;
        }
        if (p.hasChild(n)) {
            out.put(n, p.getChild(n));
            return;
        }
        for (String c : new String[]{"body", "head", "tail"}) {
            if (p.hasChild(c)) {
                find(p.getChild(c), n, out);
            }
        }
    }

    /** Couche d'une race (parties, boites, UV du generateur ; texture texW x texH). */
    public static LayerDefinition layer(GeneratedPetShapes.Shape s) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("", mesh.getRoot());
        for (GeneratedPetShapes.Part part : s.parts()) {
            CubeListBuilder cubes = CubeListBuilder.create();
            for (float[] c : part.cubes()) {
                cubes.texOffs((int) c[0], (int) c[1]).mirror(c[9] > 0).addBox(c[2], c[3], c[4], c[5], c[6], c[7], new CubeDeformation(c[8]));
            }
            defs.put(part.name(), defs.get(part.parent()).addOrReplaceChild(part.name(), cubes,
                  PartPose.offsetAndRotation(part.x(), part.y(), part.z(), part.xRot(), part.yRot(), part.zRot())));
        }
        return LayerDefinition.create(mesh, s.texW(), s.texH());
    }

    /** Pose initiale de toutes les parties (positions, rotations, echelles 1, visibles). */
    void restore() {
        for (ModelPart p : all) {
            p.resetPose();
            p.xScale = p.yScale = p.zScale = 1F;
            p.visible = true;
            p.skipDraw = false;
        }
    }

    @Override
    public void prepareMobModel(@NotNull T e, float limbSwing, float limbSwingAmount, float partialTick) {
        headRoll = bodyRoll = 0F;
        tailLift = 0F;
        shade = 1F;
        if (e instanceof Wolf w) {
            headRoll = w.getHeadRollAngle(partialTick) + w.getBodyRollAngle(partialTick, 0F);
            bodyRoll = w.getBodyRollAngle(partialTick, -0.16F);
            tailLift = w.getTailAngle() - (float) Math.PI / 5F;
            if (w.isWet()) {
                shade = w.getWetShade(partialTick);
            }
        }
    }

    /** Repos de la bete : debout, couche, en boule (chat vanilla couche avec son maitre : en boule). */
    static int rest(LivingEntity e) {
        if (e instanceof Pet p) {
            int r = p.rest();
            if (r == Pet.AWAKE && e instanceof Cat c && c.isLying()) {
                return Pet.CURLED;
            }
            return r;
        }
        return e instanceof Cat c && c.isLying() ? Pet.CURLED : Pet.AWAKE;
    }

    @Override
    public void setupAnim(@NotNull T e, float limbSwing, float amount, float age, float headYaw, float headPitch) {
        restore();
        boolean cat = shape.cat();
        int rest = rest(e);
        boolean sit = rest == Pet.AWAKE && e instanceof TamableAnimal t && t.isInSittingPose();
        float yaw = headYaw * Mth.DEG_TO_RAD, pitch = headPitch * Mth.DEG_TO_RAD;
        if (e.isBaby()) {
            head.xScale = head.yScale = head.zScale = 1.35F;
        }
        collar.visible = collarOnly;
        if (rest != Pet.AWAKE) {
            lying(rest == Pet.CURLED, age);
        } else if (sit) {
            sitting(yaw, pitch);
        } else {
            standing(e, limbSwing, amount, age, yaw, pitch, cat);
        }
        body.zRot += bodyRoll;
        head.zRot += headRoll;
        if (collarOnly) {
            for (ModelPart p : all) {
                p.skipDraw = p != collar;
            }
        }
    }

    void standing(T e, float limb, float amount, float age, float yaw, float pitch, boolean cat) {
        float a = Math.min(1F, amount), amp = cat ? 1.0F : 1.4F;
        float walk = Mth.cos(limb * 0.6662F) * amp * a, walk2 = Mth.cos(limb * 0.6662F + (float) Math.PI) * amp * a;
        if (!cat && a > 0.6F) {
            //galop : paires avant / arriere ensemble, dos qui ondule
            float g = (a - 0.6F) / 0.4F, ph = limb * 0.55F;
            float front = Mth.cos(ph) * 1.1F * a, hind = Mth.cos(ph + (float) Math.PI * 0.85F) * 1.1F * a;
            legs[0].xRot = Mth.lerp(g, walk2, front);
            legs[1].xRot = Mth.lerp(g, walk, front * 0.9F);
            legs[2].xRot = Mth.lerp(g, walk, hind);
            legs[3].xRot = Mth.lerp(g, walk2, hind * 0.9F);
            body.xRot = g * 0.07F * Mth.sin(ph);
        } else {
            legs[0].xRot = walk2;
            legs[1].xRot = walk;
            legs[2].xRot = walk;
            legs[3].xRot = walk2;
        }
        if (cat && e.isCrouching()) {
            body.y += 1F;
            pitch += 0.2F;
        }
        head.yRot = yaw;
        head.xRot = pitch;
        if (cat) {
            tail.xRot += 0.2F * a;
            if (tail2 != null) {
                tail2.xRot += 0.45F * Mth.cos(limb * 0.6662F) * a + 0.06F * Mth.sin(age * 0.07F);
            }
        } else {
            tail.xRot = Mth.clamp(tail.xRot + tailLift * 0.8F, 0.05F, 2.8F);
            tail.yRot = Mth.cos(limb * 0.6662F) * 1.4F * a * 0.5F;
            if (shape.ear() == 2) {
                rightEar.zRot += 0.25F * a * Mth.sin(limb * 0.6662F);
                leftEar.zRot -= 0.25F * a * Mth.sin(limb * 0.6662F);
            }
        }
    }

    void sitting(float yaw, float pitch) {
        float th = shape.sitTheta();
        body.y = shape.sitY();
        body.xRot = -th;
        legs[0].xRot = th;
        legs[1].xRot = th;
        legs[0].yScale = legs[1].yScale = shape.sitFrontScale();
        float hindRel = -shape.sitHind() + th;
        float out = shape.legW() * 0.5F;
        legs[2].xRot = hindRel;
        legs[3].xRot = hindRel;
        legs[2].x -= out;
        legs[3].x += out;
        head.xRot = th + pitch;
        head.yRot = yaw;
        tail.xRot = 1.2F + th;
        tail.yRot = 0.35F;
        if (tail2 != null) {
            tail2.xRot = 0.1F;
            tail2.zRot = 0.7F;
        }
    }

    void lying(boolean curled, float age) {
        float lw = shape.legW();
        body.y = shape.lieY();
        body.xRot = 0F;
        float half = (float) Math.PI / 2F;
        for (int i = 0; i < 4; i++) {
            legs[i].xRot = -half;
            legs[i].y = -lw / 2F;
        }
        //pattes avant allongees devant le poitrail, pattes arriere le long des flancs (au-dehors du pelage)
        legs[0].z -= 1.5F;
        legs[1].z -= 1.5F;
        float side = shape.bodyW() / 2F + lw / 2F + fur + 0.25F;
        legs[2].x = -side;
        legs[3].x = side;
        legs[2].z -= 1F;
        legs[3].z -= 1F;
        head.y += 1.5F;
        head.xRot = 0.12F + 0.03F * Mth.sin(age * 0.05F);
        tail.xRot = half + 0.05F;
        tail.yRot = 0.35F;
        if (curled) {
            legs[0].yScale = legs[1].yScale = 0.8F;
            legs[2].yScale = legs[3].yScale = 0.85F;
            head.yRot = 1.75F;
            head.xRot = 0.35F;
            head.y += 0.5F;
            head.x -= 0.5F;
            tail.yRot = -1.25F;
            if (tail2 != null) {
                tail2.xRot = 0F;
                tail2.zRot = -1.1F;
            }
        } else if (tail2 != null) {
            tail2.xRot = 0.1F;
            tail2.zRot = 0.5F;
        }
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack pose, @NotNull VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buf, light, overlay, r * shade, g * shade, b * shade, a);
    }
}
