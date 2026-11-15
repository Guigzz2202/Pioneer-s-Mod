package fr.pioneers.robotics;

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
 * Carte d'amelioration robotique (a placer dans un hub ou un relais) : un bonus et sa contrepartie.
 */
public class ItemRoboticUpgrade extends Item {

    public final RoboticUpgrade upgrade;

    public ItemRoboticUpgrade(RoboticUpgrade upgrade) {
        super(new Properties().stacksTo(upgrade.max));
        this.upgrade = upgrade;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        String base = "item.pioneers." + upgrade.itemId();
        tips.good(base + ".plus");
        tips.warn(base + ".minus");
        tips.hint(upgrade.forHub ? "robotics.pioneers.upgrade.for_hub" : "robotics.pioneers.upgrade.for_relay",
              upgrade.max);
    }
}
