package fr.pioneers.space.mining;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Module du rover minier : a placer dans l'un des trois emplacements de modules du controleur.
 */
public class ItemRoverModule extends Item {

    private final RoverModule module;

    public ItemRoverModule(RoverModule module) {
        super(new Properties().stacksTo(1));
        this.module = module;
    }

    public RoverModule getModule() {
        return module;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.state("mining.pioneers.module." + module.name().toLowerCase(Locale.ROOT) + ".effect");
        tips.hint("mining.pioneers.module.hint");
    }
}
