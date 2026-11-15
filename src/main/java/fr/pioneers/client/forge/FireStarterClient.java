package fr.pioneers.client.forge;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.Pioneers;
import fr.pioneers.forge.casting.FireStarterItem;
import fr.pioneers.registries.PiForge;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.Nullable;

/**
 * 1.10.1 (Guillaume : « ameliorer l'animation de l'allume feu ») : geste de l'archet de feu, a la place de l'animation de la brosse.
 * <ul>
 * <li>Modele : predicat {@code pioneers:drill} = image du geste (archet decale le long de la corde, fuseau tourne, braise qui nait puis
 * grossit) ; images ecrites par {@code tools/gc/core/held3d_tools.py} ({@code DRILL_FRAMES} x {@code HEAT_LEVELS}, a garder egaux a
 * {@link #FRAMES} et {@link #LEVELS}).</li>
 * <li>Premiere personne : l'archet vient devant soi et bas (6 ticks), appuye sur la paumelle ; la main suit l'archet de cote et appuie a
 * chaque retour.</li>
 * <li>Troisieme personne : bras droit qui pousse et tire l'archet, bras gauche appuye sur la paumelle.</li>
 * </ul>
 * Cadence commune avec le serveur : {@link FireStarterItem#strokePhase}. Apercu du geste : {@code tools/hand/fire_starter.py}.
 */
public final class FireStarterClient implements IClientItemExtensions {

    public static final FireStarterClient INSTANCE = new FireStarterClient();
    /** Images par aller-retour et niveaux de braise (= held3d_tools.DRILL_FRAMES, HEAT_LEVELS). */
    public static final int FRAMES = 12, LEVELS = 3;

    /** Bras droit qui tire l'archet, bras gauche appuye sur la paumelle (cree au chargement des objets, pendant le chargement du mod). */
    private static final HumanoidModel.ArmPose DRILLING = HumanoidModel.ArmPose.create("PIONEERS_FIRE_BOW", true, (model, entity, arm) -> {
        float s = Mth.sin(Mth.TWO_PI * FireStarterItem.strokePhase(usedTicks(entity)));
        model.rightArm.xRot = -0.95F + 0.06F * Mth.abs(s);
        model.rightArm.yRot = -0.30F + 0.32F * s;
        model.rightArm.zRot = 0;
        model.leftArm.xRot = -1.15F + 0.04F * Mth.abs(s);
        model.leftArm.yRot = 0.55F;
        model.leftArm.zRot = 0;
    });

    private FireStarterClient() {
    }

    /** Ticks de geste (avec la fraction d'image), 0 si l'entite n'utilise pas l'objet. */
    static float usedTicks(LivingEntity entity) {
        if (!entity.isUsingItem()) {
            return 0;
        }
        return entity.getTicksUsingItem() + Minecraft.getInstance().getFrameTime();
    }

    private static float smooth(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    /** Valeur du predicat pioneers:drill : 0 au repos, sinon le milieu de la marche de l'image (niveau de braise, image du geste). */
    public static float drill(ItemStack stack, @Nullable LivingEntity entity) {
        if (entity == null || !entity.isUsingItem() || entity.getUseItem() != stack) {
            return 0;
        }
        float t = usedTicks(entity);
        float p = FireStarterItem.progress(t);
        int level = p < 0.35F ? 0 : p < 0.75F ? 1 : 2;
        float phase = FireStarterItem.strokePhase(t);
        int frame = Mth.floor((phase - Mth.floor(phase)) * FRAMES) % FRAMES;
        return (level * FRAMES + frame + 1.5F) / (FRAMES * LEVELS + 1);
    }

    /** Predicat du modele (appele par CastingClient au demarrage du client). */
    static void registerProperties() {
        ItemProperties.register(PiForge.FIRE_STARTER.get(), Pioneers.rl("drill"), (stack, level, entity, seed) -> drill(stack, entity));
    }

    @Nullable
    @Override
    public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
        return entity.isUsingItem() && entity.getUsedItemHand() == hand && entity.getUseItemRemainingTicks() > 0 ? DRILLING : null;
    }

    @Override
    public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer player, HumanoidArm arm, ItemStack stack, float partialTick, float equip,
          float swing) {
        if (!player.isUsingItem() || player.getUseItem() != stack || player.getUseItemRemainingTicks() <= 0) {
            return false;
        }
        int i = arm == HumanoidArm.RIGHT ? 1 : -1;
        float t = player.getTicksUsingItem() + partialTick;
        float in = smooth(t / 6);
        float phase = FireStarterItem.strokePhase(t);
        float s = Mth.sin(Mth.TWO_PI * phase);
        //Appui a chaque retour de l'archet (deux fois par aller-retour), un peu plus fort quand on force a la fin
        float press = (0.010F + 0.008F * FireStarterItem.progress(t)) * Mth.abs(Mth.cos(Mth.TWO_PI * phase)) * in;
        pose.translate(i * (0.56F - 0.20F * in) + i * 0.022F * s * in, -0.52F + 0.06F * in - press, -0.72F - 0.04F * in);
        pose.mulPose(Axis.XP.rotationDegrees(-10 * in));
        pose.mulPose(Axis.YP.rotationDegrees(i * (8 * in + 3 * s * in)));
        pose.mulPose(Axis.ZP.rotationDegrees(i * 1.5F * s * in));
        return true;
    }
}
