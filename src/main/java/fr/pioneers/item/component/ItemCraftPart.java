package fr.pioneers.item.component;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Composant du robot agricole ou du rover minier (voir {@link CraftPart}).
 */
public class ItemCraftPart extends Item {

    private final CraftPart part;

    public ItemCraftPart(CraftPart part) {
        super(new Properties());
        this.part = part;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
    }
}
