package fr.pioneers.client.farm.leisure;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.farm.leisure.GeneratedLeisure;
import fr.pioneers.farm.leisure.Instrument;
import fr.pioneers.farm.leisure.InstrumentItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderHandEvent;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;

/**
 * 1.6 : animation des instruments de musique cote client. Chaque note jouee (par soi : {@link TunePlayer#play} ; par un autre joueur :
 * {@link fr.pioneers.farm.leisure.PacketNote.Fx}) est notee par joueur ; l'age de la derniere note anime les bras (coup de mediator, soufflet,
 * baguettes qui frappent tour a tour) et l'instrument.
 * <ul>
 *   <li>3e personne : poses de bras a deux mains (IClientItemExtensions.getArmPose, une pose par instrument) ; l'instrument est dessine contre
 *   le corps, ou devant la bouche pour la flute qui joue, par {@link InstrumentLayer} (le modele en main est cache : vues thirdperson a 0) ;</li>
 *   <li>1re personne : {@link #onRenderHand} place l'instrument lui-meme (et les baguettes du tambour).</li>
 * </ul>
 * Chiffres : {@link GeneratedLeisure} (LAYER, FIRST_PERSON, ARM_*, STICK_*), produits par tools/gc/farm2/leisure_hold.py, dont les fonctions de
 * mouvement sont le double Python de celles-ci (apercu : tools/gc/farm2/leisure_hand.py).
 */
@Mod.EventBusSubscriber(modid = Pioneers.MODID, value = Dist.CLIENT)
public final class LeisureClient {

    /** Tambour porte (sans baguettes) et baguette en main : modeles en plus (ModelEvent.RegisterAdditional, {@link LeisureClientSetup}). */
    public static final ResourceLocation DRUM_WORN = Pioneers.rl("item/leisure/drum_worn"), DRUM_STICK = Pioneers.rl("item/leisure/drum_stick");
    /** Derniere note de chaque joueur (id d'entite) : heure client en ticks et nombre de notes (le tambour frappe d'une main puis de l'autre). */
    private static final Map<Integer, long[]> NOTES = new HashMap<>();
    private static final HumanoidModel.ArmPose[] POSES = new HumanoidModel.ArmPose[Instrument.values().length];
    private static final float NEVER = 1.0E4F;

    /** Pose des bras de l'instrument tenu (la flute seulement quand elle joue). */
    public static final IClientItemExtensions EXTENSIONS = new IClientItemExtensions() {
        @Nullable
        @Override
        public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
            if (!(stack.getItem() instanceof InstrumentItem item) || item.instrument == Instrument.FLUTE && !playing(entity, 0)) {
                return null;
            }
            return pose(item.instrument);
        }
    };

    private LeisureClient() {
    }

    // ------------------------------------------------------------------------------------------------ notes jouees

    /** Une note vient d'etre jouee par ce joueur. */
    public static void heard(int entityId) {
        long[] n = NOTES.computeIfAbsent(entityId, k -> new long[2]);
        n[0] = TunePlayer.clock;
        n[1]++;
    }

    /** Ticks depuis la derniere note de ce joueur (avec la fraction d'image). */
    public static float age(LivingEntity entity, float partial) {
        long[] n = NOTES.get(entity.getId());
        return n == null ? NEVER : TunePlayer.clock - n[0] + partial;
    }

    /** Main de la derniere frappe du tambour : 0 droite, 1 gauche. */
    public static int parity(LivingEntity entity) {
        long[] n = NOTES.get(entity.getId());
        return n == null ? 0 : (int) (n[1] & 1);
    }

    public static float now(float partial) {
        return TunePlayer.clock + partial;
    }

    /** Elan d'une note jouee il y a age ticks : 1 au coup, puis s'eteint. */
    static float impulse(float age) {
        return (float) Math.exp(-age / 3.0);
    }

    /** 1 pendant qu'on joue, retombe a 0 en une demi-seconde. */
    static float playingFactor(float age) {
        return Mth.clamp((GeneratedLeisure.PLAYING_TICKS - age) / 10F, 0F, 1F);
    }

    static float bellows(float age, float now) {
        return playingFactor(age) * Mth.sin(now * 0.22F);
    }

    /** Le joueur joue : note recente, ou (soi-meme) clavier ouvert ou air connu en cours. */
    public static boolean playing(LivingEntity entity, float partial) {
        if (playingFactor(age(entity, partial)) > 0) {
            return true;
        }
        Minecraft mc = Minecraft.getInstance();
        return entity == mc.player && (mc.screen instanceof InstrumentScreen || TunePlayer.playing() != null);
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase == TickEvent.Phase.END && TunePlayer.clock % 200 == 0) {
            NOTES.values().removeIf(n -> TunePlayer.clock - n[0] > 1200);       // joueurs partis ou qui ne jouent plus
        }
    }

    // ------------------------------------------------------------------------------------------------ 3e personne : bras

    /**
     * Cree les poses (valeurs d'enum ajoutees a ArmPose) au demarrage du client, AVANT tout rendu : le switch de HumanoidModel.poseRightArm
     * fige le nombre de valeurs a sa premiere execution (une pose creee plus tard = ArrayIndexOutOfBoundsException, 1.6.3).
     */
    public static void initPoses() {
        for (Instrument instrument : Instrument.values()) {
            if (POSES[instrument.ordinal()] == null) {
                POSES[instrument.ordinal()] = HumanoidModel.ArmPose.create("PIONEERS_" + instrument.name(), true,
                      (model, entity, arm) -> poseArms(model, entity, instrument));
            }
        }
    }

    @Nullable
    static HumanoidModel.ArmPose pose(Instrument instrument) {
        return POSES[instrument.ordinal()];
    }

    /** Les deux bras (la pose est a deux mains : elle est appliquee en dernier). Double Python : leisure_hold.arms. */
    static void poseArms(HumanoidModel<?> model, LivingEntity entity, Instrument instrument) {
        float partial = Minecraft.getInstance().getFrameTime();
        float age = age(entity, partial), now = now(partial), k = impulse(age);
        float[] r = GeneratedLeisure.ARM_RIGHT[instrument.ordinal()].clone(), l = GeneratedLeisure.ARM_LEFT[instrument.ordinal()].clone();
        switch (instrument) {
            case GUITAR -> {
                r[0] += -0.35F * k * Mth.sin(age * 1.4F);                         // coup de mediator
                l[1] += 0.04F * Mth.sin(now * 0.3F) * playingFactor(age);        // la main gauche glisse sur le manche
            }
            case ACCORDION -> {
                float b = bellows(age, now);
                r[1] += 0.18F * b;
                l[1] -= 0.18F * b;
            }
            case DRUM -> {
                float lift = age < 3.5F ? -0.5F * k * Mth.sin(Math.min(Mth.PI, age * 0.9F)) : 0;
                if (parity(entity) != 0) {
                    l[0] += lift;
                } else {
                    r[0] += lift;
                }
            }
            default -> {
                r[0] += model.head.xRot;
                l[0] += model.head.xRot;
                r[1] += model.head.yRot;
                l[1] += model.head.yRot;
                r[2] += 0.04F * Mth.sin(now * 0.5F) * playingFactor(age);        // doigts qui bougent
            }
        }
        set(model.rightArm, r);
        set(model.leftArm, l);
    }

    private static void set(ModelPart arm, float[] rot) {
        arm.xRot = rot[0];
        arm.yRot = rot[1];
        arm.zRot = rot[2];
    }

    /** Contre le corps : (rotation z en plus en degres, echelle x relative, decalage y). Double Python : leisure_hold.layer_extra. */
    static float[] layerExtra(Instrument instrument, float age, float now) {
        float k = impulse(age);
        return switch (instrument) {
            case GUITAR -> new float[]{3F * k * Mth.sin(age * 1.4F), 1F, -0.01F * k};
            case ACCORDION -> new float[]{0F, 1F + 0.14F * bellows(age, now), 0F};
            case DRUM -> new float[]{0F, 1F, -0.012F * k};
            default -> new float[]{0F, 1F, 0F};
        };
    }

    // ------------------------------------------------------------------------------------------------ 1re personne

    /** Instrument en 1re personne (la flute seulement quand elle joue : sinon, tenue vanilla). Double Python : leisure_hand.first. */
    @SubscribeEvent
    public static void onRenderHand(RenderHandEvent event) {
        ItemStack stack = event.getItemStack();
        LocalPlayer player = Minecraft.getInstance().player;
        if (!(stack.getItem() instanceof InstrumentItem item) || player == null) {
            return;
        }
        Instrument instrument = item.instrument;
        float partial = event.getPartialTick();
        if (instrument == Instrument.FLUTE && !playing(player, partial)) {
            return;
        }
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        float side = arm == HumanoidArm.RIGHT ? 1 : -1;
        float age = age(player, partial), now = now(partial), k = impulse(age), equip = event.getEquipProgress();
        float dy = 0, drz = 0, sx = 1;
        switch (instrument) {                                                   // double Python : leisure_hold.fp_extra
            case GUITAR -> {
                dy = -0.02F * k;
                drz = 4F * k * Mth.sin(age * 1.4F);
            }
            case ACCORDION -> sx = 1F + 0.14F * bellows(age, now);
            case DRUM -> dy = -0.015F * k;
            default -> dy = 0.008F * Mth.sin(now * 0.15F) * playingFactor(age);
        }
        float[] p = GeneratedLeisure.FIRST_PERSON[instrument.ordinal()];
        PoseStack pose = event.getPoseStack();
        pose.pushPose();
        pose.translate(side * p[0], p[1] + dy - 0.6F * equip, p[2]);
        pose.mulPose(new Quaternionf().rotationXYZ(p[3] * Mth.DEG_TO_RAD, side * p[4] * Mth.DEG_TO_RAD, side * p[5] * Mth.DEG_TO_RAD));
        pose.mulPose(Axis.ZP.rotationDegrees(side * drz));
        pose.scale(p[6] * sx, p[6], p[6]);
        render(stack, instrument == Instrument.DRUM ? extra(DRUM_WORN) : itemModel(stack, player), pose, event.getMultiBufferSource(),
              event.getPackedLight(), side < 0);
        pose.popPose();
        if (instrument == Instrument.DRUM) {
            float[] q = GeneratedLeisure.FP_STICK;
            int parity = parity(player);
            for (int s = -1; s <= 1; s += 2) {                                  // s = -1 : baguette de droite
                boolean striking = (s < 0) == (parity == 0) && age < 3.5F;
                float lift = striking ? 0.06F * Mth.sin(Math.min(Mth.PI, age * 0.9F)) : 0;
                pose.pushPose();
                pose.translate(q[0] * -s, q[1] + lift - 0.6F * equip, q[2]);
                pose.mulPose(new Quaternionf().rotationXYZ(q[3] * Mth.DEG_TO_RAD, -s * q[4] * Mth.DEG_TO_RAD, -s * q[5] * Mth.DEG_TO_RAD));
                pose.scale(q[6], q[6], q[6]);
                pose.translate(GeneratedLeisure.GRIP, GeneratedLeisure.GRIP, 0);
                render(stack, extra(DRUM_STICK), pose, event.getMultiBufferSource(), event.getPackedLight(), false);
                pose.popPose();
            }
        }
        event.setCanceled(true);
    }

    // ------------------------------------------------------------------------------------------------ rendu

    /** Placement [tx, ty, tz, rx, ry, rz, s] (comme ItemTransform, sans miroir). */
    static void place(PoseStack pose, float[] p) {
        pose.translate(p[0], p[1], p[2]);
        pose.mulPose(new Quaternionf().rotationXYZ(p[3] * Mth.DEG_TO_RAD, p[4] * Mth.DEG_TO_RAD, p[5] * Mth.DEG_TO_RAD));
        pose.scale(p[6], p[6], p[6]);
    }

    static BakedModel itemModel(ItemStack stack, LivingEntity entity) {
        return Minecraft.getInstance().getItemRenderer().getModel(stack, entity.level(), entity, entity.getId());
    }

    static BakedModel extra(ResourceLocation id) {
        return Minecraft.getInstance().getModelManager().getModel(id);
    }

    /** Modele centre sur l'origine du repere courant (contexte NONE : aucune vue du modele appliquee). */
    static void render(ItemStack stack, BakedModel model, PoseStack pose, MultiBufferSource buffers, int light, boolean left) {
        Minecraft.getInstance().getItemRenderer().render(stack, ItemDisplayContext.NONE, left, pose, buffers, light, OverlayTexture.NO_OVERLAY,
              model);
    }
}
