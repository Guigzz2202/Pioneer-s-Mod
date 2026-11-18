package fr.pioneers.tech.energy;

import fr.pioneers.tech.energy.client.WindRotorItemRenderer;
import fr.pioneers.core.block.DescribedBlockItem;
import java.util.function.Consumer;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.level.block.Block;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;

/**
 * 1.9.2 (retours de Guillaume) : objet du rotor d'eolienne. Dans l'inventaire, l'icone plate (modele {@code forge:separate_transforms}) ;
 * en main, au sol et en cadre, le vrai rotor dessine par {@code client/energy/WindRotorItemRenderer} (meme dessin qu'en jeu, reduit).
 */
public class WindRotorItem extends DescribedBlockItem {

    public WindRotorItem(Block block, Properties properties, String description) {
        super(block, properties, description);
    }

    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                return WindRotorItemRenderer.instance();
            }
        });
    }
}
