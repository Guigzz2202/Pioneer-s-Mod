package fr.pioneers.tech.nuclear;

import fr.pioneers.core.tooltip.Tooltips;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dosimetre (0.17.0) : dans la barre rapide ou en main, affiche le debit de dose et la dose recue dans la barre d'action et crepite comme un
 * compteur Geiger ({@link RadiationHandler}) ; clic droit : bilan dans le chat.
 */
public class DosimeterItem extends Item {

    public DosimeterItem(Properties properties) {
        super(properties);
    }

    @NotNull
    @Override
    public InteractionResultHolder<ItemStack> use(@NotNull Level level, @NotNull Player player, @NotNull InteractionHand hand) {
        if (player instanceof ServerPlayer server) {
            double rate = RadiationHandler.rate(server), dose = RadiationHandler.dose(server);
            server.sendSystemMessage(Component.translatable("nuclear.pioneers.dosimeter.report", RadiationHandler.format(rate),
                  RadiationHandler.format(dose), Component.translatable("nuclear.pioneers.dose." + RadiationHandler.stage(dose)))
                  .withStyle(RadiationHandler.color(rate)));
        }
        return InteractionResultHolder.sidedSuccess(player.getItemInHand(hand), level.isClientSide);
    }

    @Override
    public void appendHoverText(@NotNull ItemStack stack, @Nullable Level level, @NotNull List<Component> tooltip, @NotNull TooltipFlag flag) {
        Tooltips tips = Tooltips.of(tooltip);
        tips.desc("tooltip.pioneers.dosimeter");
    }
}
