package fr.pioneers.logistics.warehouse;

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

/** 1.0.4-beta (lot 1) : objet d'un bloc de l'entrepot : description ({@code block.pioneers.<id>.desc}), contenance des caisses, contenu de la trappe. */
public class WarehouseBlockItem extends BlockItem {

    public WarehouseBlockItem(Block block, Properties properties) {
        super(block, properties);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        String key = getDescriptionId() + ".desc";
        if (Language.getInstance().has(key)) {
            tips.desc(key);
        }
        if (getBlock() instanceof CrateHatchBlock) {
            for (CrateLayout.Length l : CrateLayout.Length.values()) {
                tips.spec("tooltip.pioneers.warehouse_crate.size", l.size(), l.types(), l.piles);
            }
        }
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
