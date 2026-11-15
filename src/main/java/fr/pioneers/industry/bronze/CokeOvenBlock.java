package fr.pioneers.industry.bronze;

import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.bronze.HouseControllerBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

/**
 * Four a coke : four de briques refractaires (FACING, LIT : cuisson en cours). 1.0.4-beta (lot 25) : la fumee sort du dessus de la voute de
 * la structure ({@code CokeOvenTile.clientTick}, {@code T:industry/nbt_models.py HOUSE_FX}), plus du dessus du controleur (un bloc de
 * briques le couvre depuis le lot 24). 1.0.5-beta (lot 15) : etat {@code formed} (controleur connecte a la paroi formee) ; la fumee sort du
 * trou de la cheminee.
 */
public class CokeOvenBlock extends BronzeBlock {

    public static final BooleanProperty LIT = BlockStateProperties.LIT;
    /** 1.0.5-beta (lot 15) : structure formee (habillage des foyers voisins, {@link HouseControllerBlock#sync}). */
    public static final BooleanProperty FORMED = HouseControllerBlock.FORMED;
    /** 1.0.5-beta (lot 23) : palier de chauffe de la structure ({@link fr.pioneers.bronze.HeatPalette}). */
    public static final net.minecraft.world.level.block.state.properties.IntegerProperty HEAT = HouseControllerBlock.HEAT;

    public CokeOvenBlock(Properties properties) {
        super(properties, CokeOvenTile::new, null);
        registerDefaultState(defaultBlockState().setValue(LIT, false).setValue(FORMED, false).setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LIT, FORMED);
        builder.add(HEAT);
    }
}
