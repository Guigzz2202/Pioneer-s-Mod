package fr.pioneers.space.mining;

import fr.pioneers.common.port.PortConfigurable;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Port minier : dans un mur ou le toit de la base. Le mode se regle au configurateur de Mekanism (accroupi + clic droit) :
 * entree des tetes de forage, sortie du minerai (ejection automatique) ou alimentation en energie.
 */
public class MiningPortBlock extends Block implements EntityBlock {

    public enum Mode implements StringRepresentable {
        INPUT, OUTPUT, ENERGY;

        @NotNull
        @Override
        public String getSerializedName() {
            return name().toLowerCase();
        }

        public String getTranslationKey() {
            return "mining.pioneers.port." + getSerializedName();
        }
    }

    public static final EnumProperty<Mode> MODE = EnumProperty.create("mode", Mode.class);

    public MiningPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, Mode.OUTPUT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        //Configurateur : c'est lui qui regle le mode (accroupi + clic droit) ; main vide : interface de la structure
        if (PortConfigurable.isConfigurator(player.getItemInHand(hand)) || !player.getItemInHand(hand).isEmpty()
            || player.isSecondaryUseActive()) {
            return InteractionResult.PASS;
        }
        if (level.getBlockEntity(pos) instanceof MiningPortTile port) {
            return port.openController(player, hand, hit);
        }
        return InteractionResult.PASS;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new MiningPortTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide || type != PiTileEntityTypes.MINING_PORT.get() ? null : (lvl, pos, st, tile) -> ((MiningPortTile) tile).tickServer();
    }
}
