package fr.pioneers.client.farm.herd;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeDeformation;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * 1.0.4-beta (lot 33, Guillaume : « Les modeles de lama et de chevaux sont a ameliorer selon la race ») : SILHOUETTES DES RACES
 * ({@link GeneratedHerdShapes}, source {@code T:farm2/herd_shapes.py}).
 * <ul>
 *     <li>Cheval : {@link #applyHorse} pose les PROPORTIONS de la race apres l'animation vanilla (le modele, la barde et les crins y passent :
 *     memes mouvements) : echelles des jambes, du corps, de l'encolure, de la tete et de la queue ; jambes et tete deplacees pour suivre le
 *     corps (largeur, longueur, hauteur) ; tout le cheval remonte de {@link #lift} (sabots au sol) ; la tete garde sa taille propre
 *     (contre-echelle de l'encolure) et reste au bout de l'encolure. {@link #hairLayer} : crins en plus (criniere, toupet, queue, fanons)
 *     dans un modele aux memes noms de parties que le cheval vanilla (texture {@code <race>_hair.png}).</li>
 *     <li>Lama : {@link #llamaLayer} = un modele par race (memes noms que LlamaModel, oreilles en banane et toison en enfants) ;
 *     {@link #fit} ajuste le tapis vanilla aux boites de la race.</li>
 * </ul>
 */
public final class HerdShapes {

    private HerdShapes() {
    }

    private static final Map<String, GeneratedHerdShapes.HorseShape> HORSES = new HashMap<>();
    private static final Map<String, GeneratedHerdShapes.LlamaShape> LLAMAS = new HashMap<>();

    static {
        for (GeneratedHerdShapes.HorseShape s : GeneratedHerdShapes.HORSES) {
            HORSES.put(s.id(), s);
        }
        for (GeneratedHerdShapes.LlamaShape s : GeneratedHerdShapes.LLAMAS) {
            LLAMAS.put(s.id(), s);
        }
    }

    @Nullable
    public static GeneratedHerdShapes.HorseShape horse(@Nullable String breed) {
        return breed == null ? null : HORSES.get(breed);
    }

    @Nullable
    public static GeneratedHerdShapes.LlamaShape llama(@Nullable String breed) {
        return breed == null ? null : LLAMAS.get(breed);
    }

    // ------------------------------------------------------------------ cheval

    /** Parties du cheval vanilla qui portent la tete (contre-echelle de l'encolure). */
    static final String[] HEAD_LIKE = {"head", "upper_mouth", "left_saddle_mouth", "right_saddle_mouth", "mouth_saddle_wrap", "head_saddle"};
    static final String[] LEGS = {"left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg"};
    static final String[] BABY_LEGS = {"left_front_baby_leg", "right_front_baby_leg", "left_hind_baby_leg", "right_hind_baby_leg"};

    /**
     * Hauteur (px du modele) dont le cheval remonte : ventre plus bas de 2 x (hauteur - 1), jambes plus longues (bas du sabot a 9,99 px du
     * pivot, 15,49 pour les jambes de poulain) ; = {@code herd_shapes.horse_lift}.
     */
    public static float lift(GeneratedHerdShapes.HorseShape s, boolean young) {
        return 2F * (s.bodyH() - 1F) + (young ? 15.49F : 9.99F) * (s.legLen() - 1F);
    }

    /** Valeurs posees par l'animation vanilla ({@code prepareMobModel}) avant les proportions : relues a chaque image (aucun cumul). */
    public static final class Base {
        float headY, headZ, headRot, tailRot;
        final float[] legY = new float[8], legZ = new float[8];

        public void store(Map<String, ModelPart> p) {
            ModelPart hp = p.get("head_parts");
            headY = hp.y;
            headZ = hp.z;
            headRot = hp.xRot;
            tailRot = p.get("tail").xRot;
            for (int i = 0; i < 4; i++) {
                legY[i] = p.get(LEGS[i]).y;
                legZ[i] = p.get(LEGS[i]).z;
                legY[i + 4] = p.get(BABY_LEGS[i]).y;
                legZ[i + 4] = p.get(BABY_LEGS[i]).z;
            }
        }
    }

    /** Parties d'un modele de cheval par nom (toute la hierarchie). */
    public static Map<String, ModelPart> parts(ModelPart root) {
        Map<String, ModelPart> out = new HashMap<>();
        collect(root, out);
        return out;
    }

    private static void collect(ModelPart part, Map<String, ModelPart> out) {
        for (String name : new String[]{"body", "head_parts", "head", "mane", "upper_mouth", "tail", "saddle", "left_saddle_mouth", "right_saddle_mouth",
                                        "left_saddle_line", "right_saddle_line", "head_saddle", "mouth_saddle_wrap", "left_ear", "right_ear",
                                        "left_front_leg", "right_front_leg", "left_hind_leg", "right_hind_leg", "left_front_baby_leg",
                                        "right_front_baby_leg", "left_hind_baby_leg", "right_hind_baby_leg"}) {
            if (part.hasChild(name)) {
                ModelPart c = part.getChild(name);
                out.put(name, c);
                collect(c, out);
            }
        }
    }

    /**
     * 1.0.4-beta (lot 35) : POSE INITIALE de toutes les parties (positions, rotations, echelles), appelee AVANT l'animation vanilla de chaque
     * image ({@code HerdHorseModel.prepareMobModel}) : l'animation vanilla laisse y / z des jambes arriere tels qu'elle les trouve ; sans
     * cela, les proportions de la race s'y ajoutaient a chaque image (pattes arriere qui s'envolent).
     */
    public static void restoreHorse(Map<String, ModelPart> p) {
        for (ModelPart m : p.values()) {
            m.resetPose();
            m.xScale = m.yScale = m.zScale = 1F;
        }
    }

    /** Remet les echelles et positions propres a l'etat de repos (cheval sans race, ou avant d'appliquer une autre race). */
    public static void resetHorse(Map<String, ModelPart> p) {
        for (ModelPart m : p.values()) {
            m.xScale = m.yScale = m.zScale = 1F;
        }
        for (String n : HEAD_LIKE) {
            ModelPart m = p.get(n);
            if (m != null) {
                m.x = m.getInitialPose().x;
                m.y = m.getInitialPose().y;
                m.z = m.getInitialPose().z;
            }
        }
        ModelPart body = p.get("body");
        body.z = body.getInitialPose().z;
        for (int i = 0; i < 4; i++) {
            p.get(LEGS[i]).x = p.get(LEGS[i]).getInitialPose().x;
            p.get(BABY_LEGS[i]).x = p.get(BABY_LEGS[i]).getInitialPose().x;
        }
    }

    /**
     * PROPORTIONS d'une race sur un modele de cheval deja anime ({@code base} : valeurs de l'animation vanilla de cette image). Repere du
     * modele vanilla : corps pivot (0, 11, 5), boite x -5..5, y -8..2, z -17..5 ; encolure (head_parts) pivot (0, 4, -12) ; jambes pivot
     * (+-4, 14, -12 / 7), boite x -3..1 / -1..3 (bord exterieur a +-5), bas du sabot a 9,99.
     */
    public static void applyHorse(Map<String, ModelPart> p, @Nullable GeneratedHerdShapes.HorseShape s, boolean young, Base base) {
        resetHorse(p);
        if (s == null) {
            return;
        }
        float lift = lift(s, young), bw = s.bodyW(), bh = s.bodyH(), bl = s.bodyL();
        ModelPart body = p.get("body"), hp = p.get("head_parts"), tail = p.get("tail");
        body.xScale = bw;
        body.yScale = bh;
        body.zScale = bl;
        body.y -= lift;
        //encolure : sous le dessus du corps, au bout de sa longueur ; port de la race
        hp.y = base.headY - 8F * (bh - 1F) - lift;
        hp.z = base.headZ - 17F * (bl - 1F);
        hp.xRot = base.headRot + s.neckTilt();
        hp.xScale = hp.zScale = s.neckThick();
        hp.yScale = s.neckLen();
        //tete a sa taille propre, posee au bout de l'encolure (point d'attache (0, -6, 1,5) du repere de l'encolure)
        float nt = s.neckThick(), nl = s.neckLen(), hs = s.head();
        for (String n : HEAD_LIKE) {
            ModelPart m = p.get(n);
            if (m == null) {
                continue;
            }
            m.xScale = m.zScale = hs / nt;
            m.yScale = hs / nl;
            m.y = 6F * (hs - nl) / nl;
            m.z = 1.5F * (nt - hs) / nt;
        }
        //queue : epaisseur et longueur propres (le corps l'a deja mise a son echelle), port
        float bm = (bh + bl) / 2F;
        tail.xScale = s.tailThick() / bw;
        tail.zScale = s.tailThick() / bm;
        tail.yScale = s.tailLen() / bm;
        tail.xRot = base.tailRot - s.tailLift();
        //jambes : longueur, epaisseur ; sous les bords du corps ; pivot remonte avec le corps
        for (int i = 0; i < 8; i++) {
            ModelPart leg = p.get(i < 4 ? LEGS[i] : BABY_LEGS[i - 4]);
            boolean left = (i % 4) % 2 == 0, front = (i % 4) < 2;
            leg.yScale = s.legLen();
            leg.xScale = leg.zScale = s.legThick();
            leg.x = (left ? 1F : -1F) * (5F * bw - s.legThick());
            leg.y = base.legY[i] + 2F * (bh - 1F) - lift;
            leg.z = base.legZ[i] + (front ? -17F : 2F) * (bl - 1F);
        }
    }

    /** Pivots et rotations de repos du cheval vanilla (HorseModel.createBodyMesh) : nom, parent, x, y, z, xRot. */
    static final Object[][] HORSE_POSES = {
        {"body", "", 0F, 11F, 5F, 0F}, {"head_parts", "", 0F, 4F, -12F, 0.5235988F}, {"head", "head_parts", 0F, 0F, 0F, 0F},
        {"left_ear", "head", 0F, 0F, 0F, 0F}, {"right_ear", "head", 0F, 0F, 0F, 0F}, {"mane", "head_parts", 0F, 0F, 0F, 0F},
        {"upper_mouth", "head_parts", 0F, 0F, 0F, 0F}, {"left_saddle_mouth", "head_parts", 0F, 0F, 0F, 0F},
        {"right_saddle_mouth", "head_parts", 0F, 0F, 0F, 0F}, {"left_saddle_line", "head_parts", 0F, 0F, 0F, -0.5235988F},
        {"right_saddle_line", "head_parts", 0F, 0F, 0F, -0.5235988F}, {"head_saddle", "head_parts", 0F, 0F, 0F, 0F},
        {"mouth_saddle_wrap", "head_parts", 0F, 0F, 0F, 0F}, {"tail", "body", 0F, -5F, 2F, 0.5235988F}, {"saddle", "body", 0F, 0F, 0F, 0F},
        {"left_hind_leg", "", 4F, 14F, 7F, 0F}, {"right_hind_leg", "", -4F, 14F, 7F, 0F}, {"left_front_leg", "", 4F, 14F, -12F, 0F},
        {"right_front_leg", "", -4F, 14F, -12F, 0F}, {"left_hind_baby_leg", "", 4F, 14F, 7F, 0F}, {"right_hind_baby_leg", "", -4F, 14F, 7F, 0F},
        {"left_front_baby_leg", "", 4F, 14F, -12F, 0F}, {"right_front_baby_leg", "", -4F, 14F, -12F, 0F}};

    /** Modele des CRINS d'une race : memes parties que le cheval vanilla (un HorseModel s'y construit), seulement les crins en plus. */
    public static LayerDefinition hairLayer(GeneratedHerdShapes.HorseShape s) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("", mesh.getRoot());
        for (Object[] pose : HORSE_POSES) {
            String name = (String) pose[0];
            CubeListBuilder cubes = CubeListBuilder.create();
            for (float[] h : s.hair()) {
                if (GeneratedHerdShapes.HAIR_PARTS[(int) h[0]].equals(name)) {
                    cubes.texOffs((int) h[1], (int) h[2]).addBox(h[3], h[4], h[5], h[6], h[7], h[8], new CubeDeformation(h[9]));
                }
            }
            defs.put(name, defs.get((String) pose[1]).addOrReplaceChild(name, cubes,
                  PartPose.offsetAndRotation((float) pose[2], (float) pose[3], (float) pose[4], (float) pose[5], 0F, 0F)));
        }
        return LayerDefinition.create(mesh, 64, 64);
    }

    // ------------------------------------------------------------------ lama

    /** Modele d'un lama de race (128 x 64) ; {@code decor} : sans oreilles, toison ni meches (tapis). */
    public static LayerDefinition llamaLayer(GeneratedHerdShapes.LlamaShape s) {
        MeshDefinition mesh = new MeshDefinition();
        Map<String, PartDefinition> defs = new HashMap<>();
        defs.put("", mesh.getRoot());
        for (GeneratedHerdShapes.Part part : s.parts()) {
            CubeListBuilder cubes = CubeListBuilder.create();
            for (float[] c : part.cubes()) {
                cubes.texOffs((int) c[0], (int) c[1]).addBox(c[2], c[3], c[4], c[5], c[6], c[7], new CubeDeformation(c[8]));
            }
            defs.put(part.name(), defs.get(part.parent()).addOrReplaceChild(part.name(), cubes,
                  PartPose.offsetAndRotation(part.x(), part.y(), part.z(), part.xRot(), part.yRot(), part.zRot())));
        }
        return LayerDefinition.create(mesh, 128, 64);
    }

    /** Boites du lama vanilla (repere de leur partie) : encolure de la tete, corps, patte. */
    static final float[] V_NECK = {-4F, -16F, -6F, 4F, 2F, 0F}, V_BODY = {-6F, -10F, -7F, 6F, 8F, 3F}, V_LEG = {-2F, 0F, -2F, 2F, 14F, 2F};

    /**
     * Ajuste une partie du TAPIS vanilla ({@code LLAMA_DECOR}) a la boite de la race : echelle par axe et decalage tourne par la rotation
     * de la partie (la boite vanilla {@code from} devient {@code to} dans le repere de la partie), pivot {@code (px, py, pz)}.
     */
    public static void fit(ModelPart part, float[] from, float[] to, float px, float py, float pz) {
        float kx = (to[3] - to[0]) / (from[3] - from[0]), ky = (to[4] - to[1]) / (from[4] - from[1]), kz = (to[5] - to[2]) / (from[5] - from[2]);
        Vector3f o = new Vector3f(to[0] - kx * from[0], to[1] - ky * from[1], to[2] - kz * from[2]);
        new Quaternionf().rotationZYX(part.zRot, part.yRot, part.xRot).transform(o);
        part.x = px + o.x;
        part.y = py + o.y;
        part.z = pz + o.z;
        part.xScale = kx;
        part.yScale = ky;
        part.zScale = kz;
    }

    /** Tapis vanilla ajuste au lama de la race (apres son animation de l'image). */
    public static void fitDecor(ModelPart root, GeneratedHerdShapes.LlamaShape s) {
        float lift = s.lift();
        fit(root.getChild("head"), V_NECK, s.neck(), 0F, 7F - lift, -6F);
        fit(root.getChild("body"), V_BODY, s.body(), 0F, 5F - lift, 2F);
        float[] leg = {-s.legW() / 2F, 0F, -s.legW() / 2F, s.legW() / 2F, s.legLen(), s.legW() / 2F};
        fit(root.getChild("right_hind_leg"), V_LEG, leg, -3.5F, 24F - s.legLen(), 6F);
        fit(root.getChild("left_hind_leg"), V_LEG, leg, 3.5F, 24F - s.legLen(), 6F);
        fit(root.getChild("right_front_leg"), V_LEG, leg, -3.5F, 24F - s.legLen(), -5F);
        fit(root.getChild("left_front_leg"), V_LEG, leg, 3.5F, 24F - s.legLen(), -5F);
        root.getChild("right_chest").visible = false;
        root.getChild("left_chest").visible = false;
    }
}
