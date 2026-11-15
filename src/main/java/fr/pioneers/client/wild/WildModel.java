package fr.pioneers.client.wild;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import fr.pioneers.wild.Deer;
import fr.pioneers.wild.Wild7Rules;
import fr.pioneers.wild.WildAnimal;
import fr.pioneers.wild.WildRules;
import java.util.ArrayList;
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
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 6) : MODELE PARAMETRE du gibier (une silhouette par espece et par sexe : {@link GeneratedWildShapes}). Corps au pivot de la
 * hanche, pattes / cou / bosse / queue en ENFANTS du corps (rien ne se detache), tete et museau en enfants du cou ou du corps. Chaque image
 * repart de la POSE INITIALE (lecon du lot 35 : modele partage par toutes les betes). Poses (sorte : 0 sanglier, 1 cerf, 2 dindon, 3 lievre,
 * 4 bison) :
 * <ul>
 *     <li>marche, course (galop par paires ; le cerf en l'air replie les pattes : bond ; le lievre detend les pattes arriere) ;</li>
 *     <li>couche (ventre au sol, pattes repliees devant et le long des flancs ; le dindon couve, tete rentree) ;</li>
 *     <li>broute / fouille (cou et tete au sol ; le groin du sanglier fouille, le dindon picore) ;</li>
 *     <li>animation propre : le dindon FAIT LA ROUE (plumes en eventail, ailes trainantes, tete en arriere), le cerf BRAME (cou tendu,
 *     museau en l'air), le lievre se DRESSE aux aguets, la biche leve la tete ; charge tete basse (sanglier, bison) ; queue relevee du cerf
 *     qui fuit (miroir) ;</li>
 *     <li>mort (pattes raides, le jeu couche la bete) ; petits (tete x 1,35) ; bois du cerf a la taille de la saison
 *     ({@link Deer#antlers}).</li>
 * </ul>
 */
public class WildModel<T extends WildAnimal> extends EntityModel<T> {

    public final GeneratedWildShapes.Shape shape;
    final ModelPart root, body, head, snout;
    @Nullable
    final ModelPart neck, tail, rightEar, leftEar, rightAntler, leftAntler, rightWing, leftWing, beard, tusks;
    final ModelPart[] legs;   // quadrupede : avant droite, avant gauche, arriere droite, arriere gauche ; dindon : droite, gauche
    final ModelPart[] fan;
    /** Pour chaque patte : bord arriere de ses boites (z le plus grand) : couchee vers l'avant, c'est sa hauteur au-dessus du sol. */
    final float[] legBack;
    final List<ModelPart> all = new ArrayList<>();
    /** 1.0.5-beta (lot 7) : toutes les parties par nom (anneaux du serpent, poitrail des oiseaux...). */
    final Map<String, ModelPart> named = new HashMap<>();
    final int kind;

    public WildModel(ModelPart root, GeneratedWildShapes.Shape shape) {
        this.root = root;
        this.shape = shape;
        this.kind = shape.kind();
        Map<String, ModelPart> m = new HashMap<>();
        m.put("", root);
        List<ModelPart> fans = new ArrayList<>();
        for (GeneratedWildShapes.Part p : shape.parts()) {
            ModelPart mp = m.get(p.parent()).getChild(p.name());
            m.put(p.name(), mp);
            all.add(mp);
            if (p.name().startsWith("fan_")) {
                fans.add(mp);
            }
        }
        body = m.get("body");
        head = m.get("head");
        snout = m.get("snout");
        neck = m.get("neck");
        tail = m.get("tail");
        rightEar = m.get("right_ear");
        leftEar = m.get("left_ear");
        rightAntler = m.get("right_antler");
        leftAntler = m.get("left_antler");
        rightWing = m.get("right_wing");
        leftWing = m.get("left_wing");
        beard = m.get("beard");
        tusks = m.get("tusks");
        named.putAll(m);
        legs = shape.legs() == 0 ? new ModelPart[0] : shape.legs() == 2 ? new ModelPart[]{m.get("right_leg"), m.get("left_leg")}
                                 : new ModelPart[]{m.get("right_front_leg"), m.get("left_front_leg"), m.get("right_hind_leg"), m.get("left_hind_leg")};
        fan = fans.toArray(new ModelPart[0]);
        legBack = new float[legs.length];
        String[] names = shape.legs() == 0 ? new String[0] : shape.legs() == 2 ? new String[]{"right_leg", "left_leg"}
                                           : new String[]{"right_front_leg", "left_front_leg", "right_hind_leg", "left_hind_leg"};
        for (GeneratedWildShapes.Part p : shape.parts()) {
            for (int i = 0; i < names.length; i++) {
                if (p.name().equals(names[i])) {
                    for (float[] c : p.cubes()) {
                        legBack[i] = Math.max(legBack[i], c[4] + c[7] + c[8]);
                    }
                }
            }
        }
    }

    /** Couche d'une silhouette (parties, boites, UV du generateur ; texture texW x texH). */
    public static LayerDefinition layer(GeneratedWildShapes.Shape s) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("", mesh.getRoot());
        for (GeneratedWildShapes.Part part : s.parts()) {
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
    public void setupAnim(@NotNull T e, float limb, float amount, float age, float headYaw, float headPitch) {
        restore();
        float yaw = Mth.clamp(headYaw, -60F, 60F) * Mth.DEG_TO_RAD, pitch = Mth.clamp(headPitch, -30F, 30F) * Mth.DEG_TO_RAD;
        if (e.isBaby()) {
            head.xScale = head.yScale = head.zScale = 1.35F;
        }
        antlers(e);
        if (kind >= fr.pioneers.wild.Wild9Rules.BLUE_WHALE) {
            Wild9Anim.setup(this, e, limb, amount, age, yaw, pitch);  //1.0.5-beta (lot 9) : mer et cotes
            return;
        }
        if (kind >= fr.pioneers.wild.Wild8Rules.GIRAFFE) {
            Wild8Anim.setup(this, e, limb, amount, age, yaw, pitch);  //1.0.5-beta (lot 8) : savane et jungle
            return;
        }
        if (kind >= Wild7Rules.WOLF) {
            Wild7Anim.setup(this, e, limb, amount, age, yaw, pitch);  //1.0.5-beta (lot 7) : predateurs et petite faune
            return;
        }
        if (e.deathTime > 0) {
            dead();
            return;
        }
        int act = e.action();
        if (act == WildRules.REST) {
            lying(age);
            return;
        }
        float a = Math.min(1F, amount);
        if (shape.legs() == 2) {
            walkBird(limb, a, age);
        } else {
            walkQuad(e, limb, a);
        }
        look(yaw, pitch);
        switch (act) {
            case WildRules.GRAZE -> graze(age);
            case WildRules.DISPLAY -> display(age);
            case WildRules.ALERT -> alert(age);
            case WildRules.CHARGE -> head.xRot += 0.4F;
            default -> {
            }
        }
        tailAndEars(act, limb, a, age);
    }

    void antlers(T e) {
        if (rightAntler == null) {
            return;
        }
        float g = e instanceof Deer d ? d.antlers() : 1F;
        boolean show = g > 0.04F;
        float s = 0.25F + 0.75F * g;
        for (ModelPart p : new ModelPart[]{rightAntler, leftAntler}) {
            p.visible = show;
            p.xScale = p.yScale = p.zScale = s;
        }
    }

    void look(float yaw, float pitch) {
        if (neck != null) {
            neck.yRot = yaw * 0.5F;
            head.yRot = yaw * 0.5F;
            head.xRot += pitch * 0.6F;
            neck.xRot += pitch * 0.3F;
        } else {
            head.yRot = yaw;
            head.xRot += pitch;
        }
    }

    void walkQuad(T e, float limb, float a) {
        float amp = kind == WildRules.BISON ? 1.0F : 1.3F;
        float walk = Mth.cos(limb * 0.6662F) * amp * a, walk2 = Mth.cos(limb * 0.6662F + Mth.PI) * amp * a;
        boolean airborne = !e.onGround() && a > 0.3F;
        if (kind == WildRules.DEER && airborne) {
            //bond : pattes avant repliees vers l'arriere, pattes arriere detendues
            legs[0].xRot = legs[1].xRot = -1.0F;
            legs[2].xRot = legs[3].xRot = 0.9F;
            body.xRot = -0.12F;
            return;
        }
        if (a > 0.6F) {
            float g = (a - 0.6F) / 0.4F, ph = limb * (kind == WildRules.HARE ? 0.7F : 0.55F);
            float front = Mth.cos(ph) * 1.1F * a, hind = Mth.cos(ph + Mth.PI * 0.85F) * (kind == WildRules.HARE ? 1.4F : 1.1F) * a;
            legs[0].xRot = Mth.lerp(g, walk2, front);
            legs[1].xRot = Mth.lerp(g, walk, front * 0.9F);
            legs[2].xRot = Mth.lerp(g, walk, hind);
            legs[3].xRot = Mth.lerp(g, walk2, hind * 0.9F);
            body.xRot = g * 0.06F * Mth.sin(ph);
        } else {
            legs[0].xRot = walk2;
            legs[1].xRot = walk;
            legs[2].xRot = walk;
            legs[3].xRot = walk2;
        }
    }

    void walkBird(float limb, float a, float age) {
        float w = Mth.cos(limb * 0.6662F) * 1.2F * a;
        legs[0].xRot = w;
        legs[1].xRot = -w;
        if (neck != null) {
            neck.xRot += Mth.sin(limb * 0.6662F * 2F) * 0.18F * a;      //le dindon hoche la tete en marchant
        }
        if (rightWing != null && a > 0.6F) {
            rightWing.zRot = 0.35F + 0.2F * Mth.sin(age * 0.8F);
            leftWing.zRot = -rightWing.zRot;
        }
    }

    void graze(float age) {
        switch (kind) {
            case WildRules.DEER -> {
                neck.xRot += 1.25F;
                head.xRot += 0.35F + 0.08F * Mth.sin(age * 0.25F);
            }
            case WildRules.TURKEY -> {
                neck.xRot += 1.05F + 0.3F * Math.max(0F, Mth.sin(age * 0.7F));
                head.xRot += 0.4F;
            }
            case WildRules.BOAR -> {
                head.xRot += 0.55F;
                snout.xRot += 0.18F * Mth.sin(age * 0.65F);          //le groin fouille la terre
                head.yRot += 0.12F * Mth.sin(age * 0.3F);
            }
            case WildRules.BISON -> head.xRot += 0.5F + 0.05F * Mth.sin(age * 0.2F);
            default -> head.xRot += 0.45F + 0.08F * Mth.sin(age * 0.5F);
        }
    }

    void display(float age) {
        if (kind == WildRules.TURKEY && fan.length > 0) {
            //la roue : plumes dressees en eventail, ailes trainantes, poitrail gonfle, tete en arriere
            float spread = 2.7F / Math.max(1, fan.length - 1);
            for (int i = 0; i < fan.length; i++) {
                fan[i].xRot = -0.25F;
                fan[i].zRot = (i - (fan.length - 1) / 2F) * spread;
            }
            body.xRot = -0.12F;
            if (neck != null) {
                neck.xRot -= 0.45F;
                head.xRot += 0.3F;
            }
            if (rightWing != null) {
                rightWing.zRot = 0.3F;
                leftWing.zRot = -0.3F;
                rightWing.xRot = leftWing.xRot = -0.25F;
            }
            body.xScale = body.zScale = 1.0F + 0.04F * Mth.sin(age * 0.4F);
        } else if (kind == WildRules.DEER && neck != null) {
            //brame : cou tendu en avant, museau vers le ciel
            neck.xRot += 0.2F;
            head.xRot -= 0.85F;
            snout.xRot += 0.15F * Math.max(0F, Mth.sin(age * 0.5F));
        }
    }

    void alert(float age) {
        if (kind == WildRules.HARE) {
            //dresse sur les pattes arriere : le corps pivote sur la hanche, pattes avant pendantes
            body.xRot = -0.75F;
            legs[0].xRot = legs[1].xRot = 0.65F;
            legs[2].xRot = legs[3].xRot = 0.75F;
            head.xRot += 0.7F;
        } else if (neck != null) {
            neck.xRot -= 0.3F;
            head.xRot += 0.2F;
        }
        if (rightEar != null) {
            rightEar.xRot -= 0.15F + 0.1F * Mth.sin(age * 0.9F);
            leftEar.xRot = rightEar.xRot;
        }
    }

    void tailAndEars(int act, float limb, float a, float age) {
        if (tail != null) {
            if (kind == WildRules.DEER && act == WildRules.FLEE) {
                tail.xRot = 2.0F;                                   //queue relevee : le miroir blanc de la fuite
            } else if (kind == WildRules.HARE) {
                tail.xRot += 0.4F * a;
            } else {
                tail.zRot = 0.25F * Mth.sin(age * 0.18F) + 0.2F * a * Mth.cos(limb * 0.6662F);
            }
        }
        if (rightEar != null && kind != WildRules.TURKEY) {
            //petit coup d'oreille de temps en temps
            float t = age % 80F;
            if (t < 6F) {
                rightEar.zRot += 0.25F * Mth.sin(t * 1.05F);
            }
            if (kind == WildRules.HARE && act == WildRules.FLEE) {
                rightEar.xRot -= 0.5F;
                leftEar.xRot -= 0.5F;
            }
        }
    }

    void lying(float age) {
        body.y = shape.lieY();
        body.xRot = 0F;
        float half = Mth.PI / 2F, lw = shape.legW();
        if (shape.legs() == 2) {
            //le dindon couve : pattes repliees sous le ventre, tete rentree
            for (ModelPart l : legs) {
                l.xRot = -half;
                l.y = -lw / 2F;
                l.yScale = 0.5F;
            }
            if (neck != null) {
                neck.xRot -= 0.5F;
                neck.yScale = 0.6F;
            }
            return;
        }
        float fold = Math.min(1F, 6F / (shape.legLen() + 1F));
        for (int i = 0; i < 4; i++) {
            legs[i].xRot = -half;
            legs[i].y = -legBack[i];
            legs[i].yScale = fold;
        }
        float side = shape.bodyW() / 2F + lw / 2F + 0.25F;
        legs[2].x = -side;
        legs[3].x = side;
        legs[2].z -= 1F;
        legs[3].z -= 1F;
        legs[0].z -= 1F;
        legs[1].z -= 1F;
        if (neck != null) {
            neck.xRot -= 0.15F;
            head.xRot += 0.15F + 0.03F * Mth.sin(age * 0.05F);
        } else {
            head.y += 1.5F;
            head.xRot = 0.15F + 0.03F * Mth.sin(age * 0.05F);
        }
        if (tail != null) {
            tail.xRot = half * 0.8F;
        }
    }

    void dead() {
        if (shape.legs() == 2) {
            legs[0].xRot = 0.5F;
            legs[1].xRot = 0.6F;
            return;
        }
        legs[0].xRot = legs[1].xRot = -0.45F;
        legs[2].xRot = legs[3].xRot = 0.45F;
        head.xRot += 0.3F;
    }

    @Override
    public void renderToBuffer(@NotNull PoseStack pose, @NotNull VertexConsumer buf, int light, int overlay, float r, float g, float b, float a) {
        root.render(pose, buf, light, overlay, r, g, b, a);
    }
}
