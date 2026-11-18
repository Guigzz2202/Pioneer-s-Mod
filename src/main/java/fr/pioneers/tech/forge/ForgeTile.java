package fr.pioneers.tech.forge;

import fr.pioneers.tech.bronze.BronzeTile;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

/**
 * Base des tuiles de la forge primaire (creuset d'argile, soufflet a main, foyer, enclumes, bac de trempe, moule pose) : tout ce que donne
 * {@link BronzeTile} (cases exposees aux entonnoirs, synchro client limitee {@code markClient}/{@code flushClient}, {@code writeClient}/
 * {@code readClient}, tick serveur et client) plus l'interaction a la main {@link #use}.
 */
public abstract class ForgeTile extends BronzeTile {

    protected ForgeTile(BlockEntityType<?> type, BlockPos pos, BlockState state) {
        super(type, pos, state);
    }

    /**
     * Clic droit sur le bloc (appele des deux cotes). {@link InteractionResult#PASS} = rien (le bloc ouvre alors l'interface si
     * {@link #hasMenu()}).
     */
    public InteractionResult use(Player player, InteractionHand hand, BlockHitResult hit) {
        return InteractionResult.PASS;
    }

    /** Vrai si le bloc a une interface standard ({@code MachineMenu}) a ouvrir quand {@link #use} ne fait rien. */
    public boolean hasMenu() {
        return false;
    }

    /** Hauteur de la zone machine de l'interface standard (seulement si {@link #hasMenu()}). */
    @Override
    public int guiHeight() {
        return 80;
    }
}
