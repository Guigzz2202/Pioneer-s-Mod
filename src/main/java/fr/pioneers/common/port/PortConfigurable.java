package fr.pioneers.common.port;

import fr.pioneers.core.Configurable;
import fr.pioneers.core.Wrenches;
import java.util.function.Supplier;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * Reglage d'un port au configurateur de Mekanism ou a une cle (voir WrenchInteraction), comme les ports des multiblocs de Mekanism :
 * clic droit pour lire le mode, accroupi + clic droit pour passer au suivant.
 */
public class PortConfigurable implements Configurable {

    private final Supplier<Component> current;
    private final Runnable cycle;

    /**
     * @param current nom du mode actuel.
     * @param cycle   passe au mode suivant.
     */
    public PortConfigurable(Supplier<Component> current, Runnable cycle) {
        this.current = current;
        this.cycle = cycle;
    }

    @Override
    public InteractionResult onSneakRightClick(Player player) {
        if (!player.level().isClientSide) {
            cycle.run();
            player.displayClientMessage(Component.translatable("port.pioneers.configurator.changed", current.get()).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult onRightClick(Player player) {
        if (!player.level().isClientSide) {
            player.displayClientMessage(Component.translatable("port.pioneers.configurator.view", current.get()).withStyle(ChatFormatting.GRAY), true);
        }
        return InteractionResult.SUCCESS;
    }

    /** Le joueur tient-il un configurateur ou une cle (tags forge:tools/wrench, forge:wrenches) ? Le clic doit alors passer a l'outil. */
    public static boolean isConfigurator(ItemStack stack) {
        return Wrenches.isWrench(stack);
    }
}
