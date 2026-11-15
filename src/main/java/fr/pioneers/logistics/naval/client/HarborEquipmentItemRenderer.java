package fr.pioneers.logistics.naval.client;

import com.mojang.blaze3d.vertex.PoseStack;
import fr.pioneers.logistics.rail.client.RailMesh;
import fr.pioneers.logistics.naval.HarborEquipment;
import fr.pioneers.logistics.naval.HarborEquipmentBlock;
import java.util.EnumMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

/** 0.20.14 : equipement de quai en main, au sol, dans un cadre : dalle et machine au repos, reduites pour tenir dans un bloc. */
public class HarborEquipmentItemRenderer extends BlockEntityWithoutLevelRenderer {

    private static HarborEquipmentItemRenderer instance;
    private static final Map<HarborEquipment, RailMesh> CACHE = new EnumMap<>(HarborEquipment.class);

    private HarborEquipmentItemRenderer() {
        super(Minecraft.getInstance().getBlockEntityRenderDispatcher(), Minecraft.getInstance().getEntityModels());
    }

    public static HarborEquipmentItemRenderer instance() {
        if (instance == null) {
            instance = new HarborEquipmentItemRenderer();
        }
        return instance;
    }

    /** Dalle de quai (beton, bande jaune cote eau) et machine au repos, en px (0..16, y 0..). */
    public static RailMesh mesh(HarborEquipment kind) {
        return CACHE.computeIfAbsent(kind, k -> HarborModels.withSlab(k, 0, 0, 0, 1.5F, 0.5F));
    }

    @Override
    public void renderByItem(@NotNull ItemStack stack, @NotNull ItemDisplayContext context, @NotNull PoseStack pose, @NotNull MultiBufferSource buffers,
          int light, int overlay) {
        if (!(stack.getItem() instanceof BlockItem bi) || !(bi.getBlock() instanceof HarborEquipmentBlock b)) {
            return;
        }
        pose.pushPose();
        pose.translate(0.5, 0, 0.5);
        pose.scale(0.42F, 0.42F, 0.42F);
        pose.translate(-0.5, 0, -0.5);
        HarborEquipmentRenderer.draw(pose, buffers, light, mesh(b.kind).mesh);
        pose.popPose();
    }
}
