package fr.pioneers.world.hunt.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import fr.pioneers.world.hunt.MusketItem;
import fr.pioneers.world.hunt.RevolverItem;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 1.0.5-beta (lot 10) : tenue des armes a l'ecran (client) :
 * <ul>
 *   <li>troisieme personne : au repos (arme dechargee) bras le long du corps (pose vanilla « objet ») ; chargee : VISEE a deux mains (pose de
 *   l'arbalete chargee) ; pendant le chargement : pose de chargement de l'arbalete (vanilla, d'apres l'usage CROSSBOW) ;</li>
 *   <li>premiere personne : pendant le chargement du mousquet, le canon se releve (baguette dans le canon : images du modele) et redescend
 *   a la fin ; le revolver se couche sur le cote, barillet ouvert vers le joueur.</li>
 * </ul>
 * Courbes PURES {@link #raise} et {@link #tilt} (reprises par le banc de la planche).
 */
public final class HuntItems {

    private HuntItems() {
    }

    /** Angle du canon releve (degres) pendant le chargement du mousquet : monte sur les 15 premiers %, redescend sur les 12 derniers. */
    public static float raise(float t) {
        float up = Mth.clamp(t / 0.15F, 0, 1), down = Mth.clamp((1 - t) / 0.12F, 0, 1);
        float k = Math.min(up, down);
        return 62F * k * k * (3 - 2 * k);
    }

    /** Inclinaison (degres) du revolver pendant le rechargement : barillet tourne vers le joueur. */
    public static float tilt(float t) {
        float k = Mth.clamp(t / 0.2F, 0, 1);
        return 38F * k * k * (3 - 2 * k);
    }

    static float progress(LocalPlayer p, ItemStack stack, float pt, int need) {
        if (!p.isUsingItem() || p.getUseItem() != stack) {
            return -1F;
        }
        float used = stack.getUseDuration() - (p.getUseItemRemainingTicks() - pt + 1F);
        return Mth.clamp(used / Math.max(1, need), 0F, 1F);
    }

    static void armTransform(PoseStack pose, HumanoidArm arm, float equip) {
        int k = arm == HumanoidArm.RIGHT ? 1 : -1;
        pose.translate(k * 0.56F, -0.52F + equip * -0.6F, -0.72F);
    }

    public static IClientItemExtensions musket() {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand hand, ItemStack stack) {
                if (e.isUsingItem() && e.getUseItem() == stack) {
                    return null;
                }
                return MusketItem.loaded(stack) ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer p, HumanoidArm arm, ItemStack stack, float pt, float equip,
                  float swing) {
                float t = progress(p, stack, pt, MusketItem.reloadTicks(p));
                if (t < 0) {
                    return false;
                }
                int k = arm == HumanoidArm.RIGHT ? 1 : -1;
                float a = raise(t);
                armTransform(pose, arm, equip);
                pose.translate(k * -0.18F * a / 62F, 0.05F * a / 62F, -0.1F * a / 62F);
                pose.mulPose(Axis.XP.rotationDegrees(a));
                pose.mulPose(Axis.ZP.rotationDegrees(k * 10F * a / 62F));
                return true;
            }
        };
    }

    public static IClientItemExtensions revolver() {
        return new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity e, InteractionHand hand, ItemStack stack) {
                if (e.isUsingItem() && e.getUseItem() == stack) {
                    return null;
                }
                return RevolverItem.rounds(stack) > 0 ? HumanoidModel.ArmPose.CROSSBOW_HOLD : null;
            }

            @Override
            public boolean applyForgeHandTransform(PoseStack pose, LocalPlayer p, HumanoidArm arm, ItemStack stack, float pt, float equip,
                  float swing) {
                float t = progress(p, stack, pt, 20);
                if (t < 0) {
                    return false;
                }
                int k = arm == HumanoidArm.RIGHT ? 1 : -1;
                float a = tilt(t);
                armTransform(pose, arm, equip);
                pose.translate(k * -0.16F * a / 38F, 0.08F * a / 38F, 0.1F * a / 38F);
                pose.mulPose(Axis.ZP.rotationDegrees(k * a));
                pose.mulPose(Axis.XP.rotationDegrees(a * 0.5F));
                return true;
            }
        };
    }

    /** Pour l'interface : le joueur en cours d'usage de cette pile ? */
    static boolean using(Player p, ItemStack s) {
        return p.isUsingItem() && p.getUseItem() == s;
    }
}
