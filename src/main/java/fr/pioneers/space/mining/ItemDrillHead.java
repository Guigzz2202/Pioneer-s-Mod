package fr.pioneers.space.mining;

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
 * Tete de forage : s'installe dans le controleur de la base miniere et s'use a chaque unite foree.
 */
public class ItemDrillHead extends Item {

    private final DrillHead head;

    public ItemDrillHead(DrillHead head) {
        super(new Properties().durability(head.durability));
        this.head = head;
    }

    public DrillHead getHead() {
        return head;
    }

    @Override
    public boolean isEnchantable(@NotNull ItemStack stack) {
        return false;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("mining.pioneers.drill.tier", head.tier);
        tips.spec("mining.pioneers.drill.speed", String.format("%.2f", head.speed));
        tips.spec("mining.pioneers.drill.wear", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage());
        tips.hint("mining.pioneers.drill.hint");
    }
}
