package fr.pioneers.tech.bronze;

import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 15, Guillaume : « le controleur a une texture connectee une fois forme ») : controleur d'un four primitif (haut fourneau,
 * fonderie automatique ; le four a coke a la meme propriete dans {@code CokeOvenBlock}) : etat {@code formed} en plus de {@code lit}. Forme,
 * il prend l'habillage des foyers en briques refractaires qui l'entourent (plinthe de fonte, filet de laiton, briques au palier de chauffe
 * commun ; modeles {@code <id>_formed[_lit]} de {@code T:core/p105_lot15.py}) ; non forme (et en objet) : la vraie forme posee d'avant.
 * L'etat est tenu par la tuile ({@link #sync}) ; rien n'en depend cote serveur (affichage seulement). 1.0.5-beta (lot 23) : palier de chauffe
 * {@link #HEAT} peint par la tuile avec celui de toute la structure ({@link HeatPalette}) : le controleur forme a la couleur de ses voisins.
 */
public class HouseControllerBlock extends LitBronzeBlock {

    public static final BooleanProperty FORMED = BooleanProperty.create("formed");
    /** 1.0.5-beta (lot 23) : palier de chauffe de la structure formee ({@link HeatPalette} : le meme que ses briques). */
    public static final IntegerProperty HEAT = HeatPalette.HEAT;

    public HouseControllerBlock(Properties properties, BiFunction<BlockPos, BlockState, ? extends BronzeTile> factory, @Nullable VoxelShape shape,
          double smokeY) {
        super(properties, factory, shape, smokeY);
        registerDefaultState(defaultBlockState().setValue(FORMED, false).setValue(HEAT, 0));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FORMED, HEAT);
    }

    /** Met l'etat forme du controleur a jour (seulement s'il change ; clients prevenus, voisins non : pur affichage). */
    public static void sync(@Nullable Level level, BlockPos pos, boolean formed) {
        if (level == null || level.isClientSide) {
            return;
        }
        BlockState state = level.getBlockState(pos);
        if (state.hasProperty(FORMED) && state.getValue(FORMED) != formed) {
            level.setBlock(pos, state.setValue(FORMED, formed), Block.UPDATE_CLIENTS);
        }
    }
}
