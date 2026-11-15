package fr.pioneers.world.ruins;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import javax.annotation.Nullable;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;

/**
 * 1.9 (MAJ RP, bloc 1) : un scrap (categorie x tier). Aucune recette ne le fabrique : il vient des ruines et des Sentinelles. L'infobulle donne
 * le tier, la categorie et le recycleur minimal.
 */
public class ScrapItem extends Item {

    private static final String[] RECYCLER = {"ruins.pioneers.recycler_mk1", "ruins.pioneers.recycler_mk2", "ruins.pioneers.recycler_mk3"};
    public final ScrapCategory category;
    public final int tier;

    public ScrapItem(ScrapCategory category, int tier) {
        super(new Item.Properties());
        this.category = category;
        this.tier = tier;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("ruins.pioneers.scrap.tooltip", Component.translatable("ruins.pioneers.category." + category.id()), tier);
        tips.spec("ruins.pioneers.scrap.recycler", Component.translatable(RECYCLER[Math.max(0, Math.min(2, tier - 1))]));
    }
}
