package fr.pioneers.logistics.transport.legacy;

import fr.pioneers.logistics.transport.PiTransport;
import fr.pioneers.logistics.transport.logistic.CableLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.11 (D2c) : fibre optique heritee ({@code <tier>_data_cable[_clear]}), tile {@code pioneers:data_cable}. Convertie en cable logistique
 * du meme niveau (canal de donnees). 1.0.6 : plus de proprietes de faces dans l'etat (voir {@link LegacyCableBlock}) : les anciennes
 * valeurs none / cable / plug / disabled d'un monde sauve sont ignorees a la lecture.
 */
public class LegacyFiberBlock extends LegacyCableBlock {

    public LegacyFiberBlock(CableLevel level, boolean clear, Properties properties) {
        super(LegacyFamily.DATA, level, clear, properties);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new LegacyCableTile(PiTransport.LEGACY_FIBER_TILE.get(), pos, state);
    }
}
