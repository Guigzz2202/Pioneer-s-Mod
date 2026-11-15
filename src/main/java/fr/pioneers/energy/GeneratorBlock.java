package fr.pioneers.energy;

import fr.pioneers.core.machine.TieredMachineBlock;
import java.util.function.BiFunction;
import java.util.function.Supplier;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

/** 0.20.45 : bloc d'un generateur renouvelable (un par genre et par tier) ; infobulle : production maximale et condition principale. */
public class GeneratorBlock extends TieredMachineBlock {

    public final EnergyKind kind;

    public GeneratorBlock(EnergyKind kind, int tier, Properties properties, Supplier<? extends BlockEntityType<?>> type,
          BiFunction<BlockPos, BlockState, BlockEntity> factory) {
        super(tier, properties, type, factory);
        this.kind = kind;
    }

    // API Machine (lot 1) : l'infobulle de l'objet est tiree de la fiche de la machine (api.machine.MachineTooltips, tools/gc/api).
}
