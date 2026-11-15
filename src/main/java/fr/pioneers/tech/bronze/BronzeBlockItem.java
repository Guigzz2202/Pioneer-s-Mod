package fr.pioneers.tech.bronze;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Objet d'un bloc de l'age du bronze : sa description ({@code block.pioneers.<id>.desc}) en infobulle. */
public class BronzeBlockItem extends BlockItem {

    public BronzeBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        String key = getDescriptionId() + ".desc";
        if (Language.getInstance().has(key)) {
            tips.desc(key);
        }
    }
}
