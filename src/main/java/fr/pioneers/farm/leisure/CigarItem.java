package fr.pioneers.farm.leisure;

import fr.pioneers.core.tooltip.Tooltips;
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

/** 1.5 : cigare roule a la main (3 tabacs seches : 2 cigares). Clic droit avec de quoi l'allumer : il devient un {@link LitCigarItem}. */
public class CigarItem extends Item {

    public CigarItem(Properties properties) {
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
        player.displayClientMessage(Component.translatable("leisure.pioneers.cigar.lit"), true);
        ItemStack lit = LitCigarItem.make(level.getGameTime() + LitCigarItem.BURN);
        return InteractionResultHolder.success(ItemUtils.createFilledResult(stack, player, lit, false));
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc(getDescriptionId() + ".desc");
    }
}
