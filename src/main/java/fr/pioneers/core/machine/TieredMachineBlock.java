package fr.pioneers.core.machine;

import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Bloc d'une machine du socle declinee en 4 tiers (1.0, lot 4 : petites machines spatiales MKI a MKIV). Une seule tile pour les 4 blocs ;
 * elle lit son tier dans le bloc ({@link #tierOf(BlockState)}).
 */
public class TieredMachineBlock extends MachineBlock {

    public static final String[] TIERS = {"basic", "advanced", "elite", "ultimate"};

    private final int tier;

    public TieredMachineBlock(int tier, Properties properties, Supplier<? extends BlockEntityType<?>> type, BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(properties, type, factory);
        this.tier = tier;
    }

    /** 0 (MKI) a 3 (MKIV). */
    public int tier() {
        return tier;
    }

    /** Tier du bloc d'une machine (0 si le bloc n'a pas de tier). */
    public static int tierOf(BlockState state) {
        return state.getBlock() instanceof TieredMachineBlock tiered ? tiered.tier : 0;
    }
}
