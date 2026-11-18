package fr.pioneers.logistics.naval;

import fr.pioneers.logistics.naval.client.HarborEquipmentItemRenderer;
import fr.pioneers.core.block.DescribedBlockItem;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import org.jetbrains.annotations.NotNull;

/** 0.20.14 : objet d'un equipement de quai : dalle et machine en 3D en main (icone plate dans l'inventaire, modele separe par vue). */
public class HarborEquipmentItem extends DescribedBlockItem {

    public HarborEquipmentItem(Block block, Properties properties, String description) {
        super(block, properties, description);
    }

    @Override
    public void initializeClient(@NotNull Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return HarborEquipmentItemRenderer.instance();
            }
        });
    }
}
