package fr.pioneers.client.gear;

import fr.pioneers.client.armor.ArmorModels;
import java.util.function.Consumer;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 1.0.3-beta (lot 9) : extensions client des objets electriques (appelees par {@code initializeClient} via {@code DistExecutor} : les
 * objets ne citent aucune classe du client) : modele 3D porte (armure Voltaique, sac dorsal du jetpack) et pose a deux mains du
 * lance-flammes (comme le fusil a plasma).
 */
@OnlyIn(Dist.CLIENT)
public final class ElectricClient {

    private ElectricClient() {
    }

    public static void armor(String id, Consumer<IClientItemExtensions> consumer) {
        ArmorModels.extensions(() -> id, consumer);
    }

    public static void twoHanded(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public HumanoidModel.ArmPose getArmPose(LivingEntity entity, InteractionHand hand, ItemStack stack) {
                return HumanoidModel.ArmPose.CROSSBOW_HOLD;
            }
        });
    }
}
