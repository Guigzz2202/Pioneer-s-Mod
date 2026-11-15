package fr.pioneers.space.module;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Module de fusee (voir {@link RocketModule}) : il s'installe dans les emplacements du complexe de lancement.
 */
public class ItemRocketModule extends Item {

    private final RocketModule module;

    public ItemRocketModule(RocketModule module) {
        super(new Properties().stacksTo(module == RocketModule.PASSENGER ? 1 : 16).rarity(module.requiredResearch() != null ? Rarity.UNCOMMON : Rarity.COMMON));
        this.module = module;
    }

    public RocketModule getModule() {
        return module;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.rocket_module.desc");
        tips.custom(Component.translatable(module.getTranslationKey() + ".desc").withStyle(style -> style.withColor(module.color)));
        if (module.maxStack > 1 && module.maxStack < 7) {
            tips.spec("item.pioneers.rocket_module.max", module.maxStack);
        }
    }
}
