package fr.pioneers.core.block;

import fr.pioneers.core.recipe.RecipeLock;
import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Objet de bloc avec sa description en infobulle. 1.11.2 (infobulles unifiees, {@link Tooltips}) : description courte d'abord (une longue :
 * premiere phrase, le reste sous Maj), puis les caracteristiques du bloc ({@code Block.appendHoverText}), puis l'etat de la pile (recette
 * verrouillee d'une machine cassee).
 */
public class DescribedBlockItem extends BlockItem {

    private final String description;

    public DescribedBlockItem(Block block, Properties properties, String description) {
        super(block, properties);
        this.description = description;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(description);
        //Caracteristiques du bloc (generateurs, cables, buffers...) : le bloc ecrit dans la meme infobulle (Tooltips.of), chaque ligne a sa place
        super.appendHoverText(stack, level, tooltip, flag);
        tips = Tooltips.of(tooltip);
        //MAJ Recettes (lot 7) : machine cassee avec sa recette verrouillee (elle la reprend a la pose)
        ResourceLocation locked = RecipeLock.of(stack);
        if (locked != null) {
            Component name = level == null ? Component.literal(locked.getPath()) : level.getRecipeManager().byKey(locked)
                  .map(r -> r.getResultItem(level.registryAccess())).filter(r -> !r.isEmpty()).map(r -> (Component) r.getHoverName())
                  .orElse(Component.literal(locked.getPath()));
            tips.state("gui.pioneers.recipe_lock.item", name);
        }
    }
}
