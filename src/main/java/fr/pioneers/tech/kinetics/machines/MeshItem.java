package fr.pioneers.tech.kinetics.machines;

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
 * Maille de tamis : toile de fibre (1), grille de cuivre (2 : chances x1,3, second tirage 30 %), bronze (3) et acier (4) aux ages suivants.
 * S'use a chaque passe.
 */
public class MeshItem extends Item {

    public final int tier;

    public MeshItem(int tier, Properties properties) {
        super(properties);
        this.tier = tier;
    }

    public float chanceBonus() {
        return switch (tier) {
            case 2 -> 1.3F;
            case 3 -> 1.6F;
            case 4 -> 2.0F;
            default -> 1.0F;
        };
    }

    public float secondRoll() {
        return switch (tier) {
            case 2 -> 0.3F;
            case 3 -> 0.5F;
            case 4 -> 0.7F;
            default -> 0F;
        };
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.spec("tooltip.pioneers.mesh", Math.round(chanceBonus() * 100), Math.round(secondRoll() * 100));
        tips.spec("tooltip.pioneers.uses_left", stack.getMaxDamage() - stack.getDamageValue(), stack.getMaxDamage());
    }
}
