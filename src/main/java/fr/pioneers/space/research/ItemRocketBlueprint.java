package fr.pioneers.space.research;

import fr.pioneers.common.PiLang;
import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.space.RocketTier;
import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Plan de fusee (ou des modules logistiques) : obtenu a la table de recherche, il doit etre place dans la table d'assemblage pour construire ce tier
 * (il n'est pas consomme).
 */
public class ItemRocketBlueprint extends Item {

    /** Plans qui ne sont pas des fusees : modules logistiques (soutes). */
    public enum Special {LOGISTICS}

    private final RocketTier tier;
    @Nullable
    private final Special special;

    public ItemRocketBlueprint(RocketTier tier) {
        super(new Properties().stacksTo(1).rarity(tier.level >= 3 ? Rarity.EPIC : Rarity.RARE));
        this.tier = tier;
        this.special = null;
    }

    public ItemRocketBlueprint(Special special) {
        super(new Properties().stacksTo(1).rarity(Rarity.RARE));
        this.tier = null;
        this.special = special;
    }

    @Nullable
    public RocketTier getTier() {
        return tier;
    }

    @Override
    public boolean isFoil(@NotNull ItemStack stack) {
        return true;
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        if (special == Special.LOGISTICS) {
            tips.desc(PiLang.DESCRIPTION_BLUEPRINT_CARGO.translateColored(ChatFormatting.GRAY));
        } else {
            tips.desc(PiLang.DESCRIPTION_BLUEPRINT.translateColored(ChatFormatting.GRAY, tier.level));
        }
    }
}
