package fr.pioneers.client.farm.herd;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.client.kinetics.KDraw;
import fr.pioneers.farm.device.HutchBlock;
import fr.pioneers.farm.device.HutchTile;
import fr.pioneers.farm.herd.GeneratedHerd;
import fr.pioneers.farm.herd.HutchLife;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.RabbitModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.animal.Rabbit;
import net.minecraft.world.inventory.InventoryMenu;
import net.minecraft.world.level.block.state.BlockState;

/**
 * 1.0.4-beta (lot 28, Guillaume : « On doit voir les lapins a l'interieur » du clapier) : les lapins LOGES (sans entite, {@link HutchTile})
 * dessines dans le clapier, chacun avec la robe et les oreilles de sa race (memes modeles que {@link HerdRabbitRenderer}), adultes et petits
 * a leur taille, sur la litiere (sol a {@link #FLOOR} px). Repere : celui de la case principale tournee selon la facade (facade au nord,
 * case laterale vers x &lt; 0 : {@link HutchBlock#sideOf}). Appele par {@code DeviceRenderer}.
 * <p>
 * 1.0.4-beta (lot 33, Guillaume : « L'animation du clapier doit etre mieux ») : la VIE des lapins vient de {@link HutchLife} (pur, cote
 * client, sur l'etat reel synchronise : lapins, adultes / lapereaux, reserve) : ils sautent d'une place a l'autre (demi-tour vers le but,
 * pattes detendues comme le lapin vanilla), mangent au RATELIER (tete basse, machoire), se couchent (corps bas, oreilles rabattues), font
 * leur toilette, se retournent, remuent nez et oreilles ; les lapereaux jouent. Le FOIN du ratelier ({@code T:farm2/herd.py} : ratelier du
 * modele) est a la hauteur de la reserve de nourriture ; clapier vide : ratelier et litiere seuls. Rien cote serveur, aucun paquet.
 */
public final class HutchRabbits {

    private HutchRabbits() {
    }

    /** Hauteur de la litiere (px) : {@code T:farm2/herd.py HUTCH_FLOOR}. */
    public static final float FLOOR = 6.5F;
    /**
     * Echelle d'un lapin adulte de taille 1 dans le clapier et d'un petit (lot 33 : 0,42 / 0,27 au lieu de 0,5 / 0,3 : huit lapins y tiennent sans
     * se chevaucher) ; MAX : le plus grand (geant des Flandres) garde les oreilles sous le
     * plafond ({@link #CEILING}) : le modele vanilla d'un adulte (rendu a 0,6) a le bout des oreilles a {@link #EAR_TOP} px a l'echelle 1.
     */
    public static final float ADULT = 0.42F, KIT = 0.27F, MAX = 0.62F;
    /** Dessous du toit (px, T:farm2/herd.py : toit de 13 a 16) et hauteur du bout des oreilles d'un adulte a l'echelle 1 (17 px x 0,6). */
    public static final float CEILING = 13F, EAR_TOP = 10.2F;
    /** Couche : le corps descend de {@link #LIE_DROP} px (repere du modele, avant l'echelle). */
    public static final float LIE_DROP = 1.6F;

    private static RabbitModel<Rabbit> plain, lop, fur;
    private static ModelPart plainRoot, lopRoot, furRoot;

    private static void models() {
        if (plain == null) {
            var set = Minecraft.getInstance().getEntityModels();
            plainRoot = set.bakeLayer(HerdModels.RABBIT);
            lopRoot = set.bakeLayer(HerdModels.RABBIT_LOP);
            furRoot = set.bakeLayer(HerdModels.RABBIT_FUR);
            plain = new RabbitModel<>(plainRoot);
            lop = new HerdModels.Lop(lopRoot);
            fur = new RabbitModel<>(furRoot);
        }
    }

    /** Taille d'un lapin dans le clapier (echelle du modele vanilla). */
    public static float scale(GeneratedHerd.RabbitBreed breed, boolean adult) {
        float s = adult ? ADULT * (breed == null ? 1F : breed.scale()) : KIT;
        return Math.min(MAX, s);
    }

    private static final float DEG = Mth.DEG_TO_RAD;

    /**
     * Pose un modele de lapin (racine de {@link HerdModels#rabbit}) d'apres une {@link HutchLife.Pose} : pattes du saut (formule du lapin
     * vanilla), couche (pattes repliees, dos plat, oreilles rabattues), tete (baissee, tournee), oreilles, nez. Toutes les parties repartent
     * de leur pose de repos (aucun cumul d'une image a l'autre). Public : banc {@code tools/farmtest/HutchBench.java}.
     */
    public static void pose(ModelPart root, HutchLife.Pose p, boolean lopEars) {
        root.getAllParts().forEach(ModelPart::resetPose);
        float j = p.jump(), l = p.lie();
        ModelPart lh = root.getChild("left_haunch"), rh = root.getChild("right_haunch"), lf = root.getChild("left_hind_foot"),
              rf = root.getChild("right_hind_foot"), fl = root.getChild("left_front_leg"), fr = root.getChild("right_front_leg"),
              body = root.getChild("body"), head = root.getChild("head"), le = root.getChild("left_ear"), re = root.getChild("right_ear"),
              nose = root.getChild("nose"), tail = root.getChild("tail");
        lh.xRot = rh.xRot = Mth.lerp(l, (j * 50F - 21F) * DEG, -6F * DEG);
        lf.xRot = rf.xRot = j * 50F * DEG;
        fl.xRot = fr.xRot = Mth.lerp(l, (j * -40F - 11F) * DEG, -78F * DEG);
        body.xRot = Mth.lerp(l, -20F * DEG, -9F * DEG);
        tail.xRot = Mth.lerp(l, -20F * DEG, -6F * DEG);
        //couche : avant du corps et tete plus bas (le corps entier descend aussi de LIE_DROP dans render)
        head.y += l * 0.8F;
        nose.y += l * 0.8F;
        fl.y += l * 0.6F;
        fr.y += l * 0.6F;
        head.xRot = p.headPitch();
        head.yRot = p.headYaw();
        nose.xRot = p.headPitch() + p.nose();
        nose.yRot = p.headYaw();
        if (lopEars) {
            le.xRot = re.xRot = p.headPitch();
            le.yRot = re.yRot = p.headYaw();
            le.zRot = -0.06F + p.ear() * 0.3F;
            re.zRot = 0.06F - p.ear() * 0.3F;
        } else {
            float back = -p.earBack() * 1.15F;
            le.xRot = p.headPitch() + back + p.ear();
            re.xRot = p.headPitch() + back + p.ear() * 0.4F;
            le.yRot = p.headYaw() + 0.2617994F + p.earBack() * 0.25F;
            re.yRot = p.headYaw() - 0.2617994F - p.earBack() * 0.25F;
        }
        le.y = re.y = head.y;
    }

    public static void render(HutchTile tile, float time, PoseStack pose, MultiBufferSource buffers, int light) {
        BlockState state = tile.getBlockState();
        boolean dbl = state.getBlock() instanceof HutchBlock && state.getValue(HutchBlock.PART) == HutchBlock.Part.MAIN;
        hay(tile.food(), dbl, pose, buffers, light);
        List<HutchTile.Resident> rabbits = tile.rabbits();
        if (rabbits.isEmpty()) {
            return;
        }
        models();
        boolean[] adult = new boolean[rabbits.size()];
        for (int i = 0; i < adult.length; i++) {
            adult[i] = rabbits.get(i).adult;
        }
        HutchLife.Pose[] poses = HutchLife.poses(tile.getBlockPos().asLong(), adult, tile.food(), dbl, time);
        for (int i = 0; i < poses.length; i++) {
            HutchTile.Resident r = rabbits.get(i);
            GeneratedHerd.RabbitBreed b = r.data.rabbit();
            HutchLife.Pose p = poses[i];
            float s = scale(b, r.adult);
            //le saut ne fait jamais passer les oreilles dans le toit
            float hop = Math.min(p.hop(), Math.max(0F, CEILING - FLOOR - EAR_TOP * s));
            boolean lopEars = b != null && b.lop();
            pose.pushPose();
            pose.translate(p.x() / 16F, (FLOOR + hop - p.lie() * LIE_DROP * s) / 16F, p.z() / 16F);
            pose.mulPose(Axis.YP.rotationDegrees(p.yaw()));
            pose.scale(-s, -s, s);
            pose.translate(0F, -1.501F, 0F);
            RabbitModel<Rabbit> m = lopEars ? lop : plain;
            pose(lopEars ? lopRoot : plainRoot, p, lopEars);
            m.young = !r.adult;
            ResourceLocation tex = b != null ? Pioneers.rl("textures/entity/herd/rabbit/" + b.id() + ".png")
                                              : new ResourceLocation("textures/entity/rabbit/brown.png");
            VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(tex));
            m.renderToBuffer(pose, vc, light, OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
            if (b != null && b.longHair() && r.adult && r.data.woolTimer <= 0) {
                pose(furRoot, p, false);
                fur.young = false;
                fur.renderToBuffer(pose, buffers.getBuffer(RenderType.entityCutoutNoCull(HerdRabbitRenderer.Fur.TEXTURE)), light,
                      OverlayTexture.NO_OVERLAY, 1F, 1F, 1F, 1F);
            }
            pose.popPose();
        }
    }

    /** Foin du ratelier a la hauteur de la reserve, avec quelques brins qui depassent quand il est plein. */
    private static void hay(int food, boolean dbl, PoseStack pose, MultiBufferSource buffers, int light) {
        float h = HutchLife.hayHeight(food);
        if (h <= 0F) {
            return;
        }
        float[] rx = dbl ? GeneratedHerd.HUTCH_RACK_DOUBLE : GeneratedHerd.HUTCH_RACK_SINGLE;
        float in = GeneratedHerd.HUTCH_HAY_INSET, y0 = GeneratedHerd.HUTCH_HAY_Y0, z0 = GeneratedHerd.HUTCH_HAY_Z0, z1 = GeneratedHerd.HUTCH_HAY_Z1;
        var atlas = Minecraft.getInstance().getTextureAtlas(InventoryMenu.BLOCK_ATLAS);
        KDraw.Tex side = KDraw.of(atlas.apply(new ResourceLocation("minecraft", "block/hay_block_side")));
        KDraw.Tex top = KDraw.of(atlas.apply(new ResourceLocation("minecraft", "block/hay_block_top")));
        KDraw d = new KDraw(pose, buffers, light, OverlayTexture.NO_OVERLAY);
        d.box(rx[0] + in, y0, z0, rx[1] - in, y0 + h, z1, side, top);
        if (h > GeneratedHerd.HUTCH_HAY_H * 0.6F) {
            float w = rx[1] - rx[0] - 2 * in;
            d.box(rx[0] + in + w * 0.2F, y0 + h, z0 + 0.4F, rx[0] + in + w * 0.45F, y0 + h + 0.5F, z1 - 0.6F, side, top);
            d.box(rx[0] + in + w * 0.6F, y0 + h, z0 + 0.7F, rx[0] + in + w * 0.85F, y0 + h + 0.35F, z1 - 0.3F, side, top);
        }
    }
}
