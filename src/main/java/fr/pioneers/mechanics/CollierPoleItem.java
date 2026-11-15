package fr.pioneers.mechanics;

import fr.pioneers.core.tooltip.Tooltips;
import fr.pioneers.mechanics.charcoal.CharcoalPits;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/** Pique de charbonnier : enfoncee dans une fosse (couverture ou buche), elle donne l'avancement, la temperature et les fuites. */
public class CollierPoleItem extends Item {

    public CollierPoleItem(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResult useOn(@NotNull UseOnContext context) {
        if (context.getLevel() instanceof ServerLevel level && context.getPlayer() != null) {
            Component report = CharcoalPits.report(level, context.getClickedPos());
            context.getPlayer().displayClientMessage(report != null ? report : Component.translatable("item.pioneers.collier_pole.none"), true);
        }
        return InteractionResult.sidedSuccess(context.getLevel().isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("item.pioneers.collier_pole.desc");
    }
}
