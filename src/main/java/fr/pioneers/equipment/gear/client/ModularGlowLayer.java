package fr.pioneers.equipment.gear.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.Pioneers;
import fr.pioneers.equipment.armor.client.ArmorModels;
import fr.pioneers.equipment.gear.ModularArmorItem;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/**
 * 0.20.15 : couche lumineuse des armures modulaires : le meme modele 3D que l'armure portee, rendu en {@code RenderType.eyes} (additif,
 * pleine lumiere) avec la texture {@code textures/models/armor/<tier>_glow.png} (noire sauf les cubes lumineux) : visieres, noyaux, liserés
 * et halos brillent dans le noir.
 */
public class ModularGlowLayer extends RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> {

    private static final EquipmentSlot[] SLOTS = {EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD};
    /** 0.20.46 (audit) : texture lumineuse par id d'armure, creee une fois (une ResourceLocation par piece et par image avant). */
    private static final Map<String, ResourceLocation> GLOW = new HashMap<>();

    public ModularGlowLayer(RenderLayerParent<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> parent) {
        super(parent);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private void copyPose(HumanoidModel<LivingEntity> model) {
        HumanoidModel parent = getParentModel();
        parent.copyPropertiesTo(model);
    }

    @Override
    public void render(@NotNull PoseStack pose, @NotNull MultiBufferSource buffers, int light, @NotNull AbstractClientPlayer player, float limbSwing,
          float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        if (player.isInvisible()) {
            return;
        }
        for (EquipmentSlot slot : SLOTS) {
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ModularArmorItem armor) || armor.getEquipmentSlot() != slot) {
                continue;
            }
            HumanoidModel<LivingEntity> model = ArmorModels.model(armor.armorId(), slot);
            copyPose(model);
            model.setAllVisible(false);
            switch (slot) {
                case HEAD -> {
                    model.head.visible = true;
                    model.hat.visible = true;
                }
                case CHEST -> {
                    model.body.visible = true;
                    model.rightArm.visible = true;
                    model.leftArm.visible = true;
                }
                case LEGS -> {
                    model.body.visible = true;
                    model.rightLeg.visible = true;
                    model.leftLeg.visible = true;
                }
                default -> {
                    model.rightLeg.visible = true;
                    model.leftLeg.visible = true;
                }
            }
            ResourceLocation tex = GLOW.computeIfAbsent(armor.armorId(), id -> Pioneers.rl("textures/models/armor/" + id + "_glow.png"));
            model.renderToBuffer(pose, buffers.getBuffer(RenderType.eyes(tex)), 0xF000F0, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1);
        }
    }
}
