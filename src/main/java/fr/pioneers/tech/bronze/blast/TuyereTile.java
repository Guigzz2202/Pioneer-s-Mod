package fr.pioneers.tech.bronze.blast;

import fr.pioneers.tech.bronze.AirBlastReceiver;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.tech.bronze.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/** Tuyere : recoit l'air d'un soufflet ou d'une soufflerie ; le haut fourneau vient le prendre a chaque tick. */
public class TuyereTile extends PiBlockEntity implements AirBlastReceiver {

    private double air;

    public TuyereTile(BlockPos pos, BlockState state) {
        super(PiBronze.TUYERE_TILE.get(), pos, state);
    }

    @Override
    public void blast(double strength) {
        air += strength;
    }

    /** L'air recu depuis le dernier passage (puis remis a zero). */
    public double drain() {
        double a = air;
        air = 0;
        return a;
    }
}
