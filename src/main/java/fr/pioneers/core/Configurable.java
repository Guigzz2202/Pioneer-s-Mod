package fr.pioneers.core;

import net.minecraft.core.Direction;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc reglable a la cle (toute cle reconnue par {@link Wrenches}, configurateur de Mekanism compris) : clic droit = lire le mode,
 * accroupi + clic droit = mode suivant. Traite par {@link WrenchInteraction}.
 */
public interface Configurable {

    InteractionResult onRightClick(Player player);

    InteractionResult onSneakRightClick(Player player);

    /** Bloc (tile) qui porte un reglage a la cle, face par face. */
    interface Holder {

        @Nullable
        Configurable getConfigurable(@Nullable Direction side);
    }
}
