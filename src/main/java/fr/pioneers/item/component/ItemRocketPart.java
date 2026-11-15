package fr.pioneers.item.component;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.space.RocketTier;
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
 * Composant de fusee d'un tier donne (voir {@link RocketPart}).
 */
public class ItemRocketPart extends Item {

    private final RocketPart part;
    private final RocketTier tier;

    public ItemRocketPart(RocketPart part, RocketTier tier) {
        super(new Properties().stacksTo(64).rarity(tier.level >= 8 ? Rarity.EPIC : tier.level >= 4 ? Rarity.RARE : tier.level >= 2 ? Rarity.UNCOMMON : Rarity.COMMON));
        this.part = part;
        this.tier = tier;
    }

    public RocketTier getTier() {
        return tier;
    }

    @NotNull
    @Override
    public Component getName(@NotNull ItemStack stack) {
        return Component.translatable("item.pioneers." + part.prefix(), tier.level);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.rocket_part.desc", tier.level);
    }
}
