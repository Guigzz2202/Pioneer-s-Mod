package fr.pioneers.farm.hemp;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.farm.leisure.Smoking;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ItemUtils;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 6, Guillaume : « chanvre + produit fumable ») : joint de chanvre roule (papier + tetes de chanvre sechees, etabli agricole).
 * Clic droit avec de quoi l'allumer (allumettes, briquet : comme le cigare, {@link Smoking#strike}) : il devient un {@link LitHempJointItem}.
 */
public class HempJointItem extends Item {

    public HempJointItem(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) {
            return InteractionResultHolder.success(stack);
        }
        if (!Smoking.strike(player, player.getX(), player.getEyeY(), player.getZ())) {
            player.displayClientMessage(Component.translatable("leisure.pioneers.pipe.no_light"), true);
            return InteractionResultHolder.success(stack);
        }
        player.displayClientMessage(Component.translatable("hemp.pioneers.joint.lit"), true);
        ItemStack lit = LitHempJointItem.make(level.getGameTime() + LitHempJointItem.BURN);
        return InteractionResultHolder.success(ItemUtils.createFilledResult(stack, player, lit, false));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
    }
}
