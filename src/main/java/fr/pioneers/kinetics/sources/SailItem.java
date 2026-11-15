package fr.pioneers.kinetics.sources;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.registries.PiKinetics;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Aile de moulin : toile (legere, se dechire dans les orages) ou planches (Cp plus eleve, plus lourde). Clic droit sur un moyeu. */
public class SailItem extends Item {

    public final int type;

    public SailItem(int type, Properties properties) {
        super(properties);
        this.type = type;
    }

    public static int typeOf(ItemStack stack) {
        return stack.getItem() instanceof SailItem sail ? sail.type : 0;
    }

    public static ItemStack stack(int type) {
        return new ItemStack(type == 2 ? PiKinetics.PLANK_SAIL.get() : PiKinetics.CANVAS_SAIL.get());
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
    }
}
