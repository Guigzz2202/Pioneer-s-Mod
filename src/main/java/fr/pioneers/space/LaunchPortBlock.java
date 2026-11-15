package fr.pioneers.space;

import fr.pioneers.common.PortMode;
import fr.pioneers.common.port.PortConfigurable;
import fr.pioneers.registries.PiTileEntityTypes;
import net.minecraft.core.BlockPos;
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
 * Port du complexe de lancement : remplace n'importe quelle plaque du disque. Il donne acces au reservoir d'hydrogene et au quai
 * du complexe (objets, fluide, gaz, donnees) pour les tuyaux, cables et convoyeurs.
 * Chaque port est specialise : objets, fluide, gaz ou donnees, en entree (hydrogene, fusees repliees, tout ce qui doit partir) ou en
 * sortie (vide le quai a la reception et l'ejecte dans les blocs voisins).
 * Le mode se regle au configurateur de Mekanism (accroupi + clic droit) ; un clic droit main vide ouvre l'interface du complexe.
 */
public class LaunchPortBlock extends Block implements EntityBlock {

    public static final EnumProperty<PortMode> MODE = EnumProperty.create("mode", PortMode.class);

    public LaunchPortBlock(Properties properties) {
        super(properties);
        registerDefaultState(stateDefinition.any().setValue(MODE, PortMode.ITEM_INPUT));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(MODE);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (PortConfigurable.isConfigurator(player.getItemInHand(hand))) {
            return InteractionResult.PASS;
        }
        return LaunchPadBlock.openCore(level, pos, player, hand, hit);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new LaunchPortTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide || type != PiTileEntityTypes.LAUNCH_PORT.get() ? null : (lvl, pos, st, tile) -> ((LaunchPortTile) tile).tickServer();
    }
}
