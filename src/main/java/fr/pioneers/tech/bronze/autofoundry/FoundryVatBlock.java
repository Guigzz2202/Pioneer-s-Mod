package fr.pioneers.tech.bronze.autofoundry;

import fr.pioneers.core.common.block.Connections;
import java.util.Map;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import org.jetbrains.annotations.NotNull;

/**
 * 1.0.4-beta (lot 23, brief : « blocs de cuve de la fonderie primitive : leur donner reellement l'allure d'une cuve, connectes entre eux
 * pour former une cuve continue ») : CUVE de la fonderie automatique. Six booleens {@code north..down} (famille
 * {@code pioneers:connects/foundry_vat}, comme les textures connectees des carcasses : {@link Connections}) : le modele (multipart de
 * {@code T:bronze/auto_foundry.py vat_parts}) ne dresse une paroi de briques refractaires cerclee de fer que vers les cotes sans cuve
 * voisine, un fond sous la cuve du bas et le bain (croute, puis metal en fusion anime) sous le rebord de la cuve du haut : les cuves posees
 * cote a cote et l'une sur l'autre forment UNE cuve ouverte. La chaleur ({@link FoundryPartBlock#HEAT}) reste peinte par le controleur.
 */
public class FoundryVatBlock extends FoundryPartBlock implements Connections.Connected {

    public static final TagKey<Block> FAMILY = Connections.family("foundry_vat");
    /**
     * 1.0.4-beta (lot 26, PJ de Guillaume : « la texture de la fonderie automatique bug quand on superpose des blocs de cuve partiellement ») :
     * {@code above_<cote>} = il y a une cuve en DIAGONALE HAUTE de ce cote (au-dessus du voisin). Une cuve qui porte une cuve ({@code up})
     * alors que son voisin n'en porte pas dresse une JUPE sous la paroi de la cuve du dessus, jusque dans le bain du voisin
     * ({@code T:bronze/auto_foundry.py vat_parts}) : plus de fente entre le bain et la paroi, ni de faces interieures visibles. Recalcule par
     * {@link #connectAll} (pose, rendu client d'apres les voisins reels : {@code ConnectedTexturesModel}).
     */
    public static final Map<Direction, BooleanProperty> ABOVE = Map.of(Direction.NORTH, BooleanProperty.create("above_north"), Direction.EAST,
          BooleanProperty.create("above_east"), Direction.SOUTH, BooleanProperty.create("above_south"), Direction.WEST,
          BooleanProperty.create("above_west"));

    public FoundryVatBlock(Properties properties) {
        super(properties, true);
        BlockState s = Connections.unconnected(defaultBlockState());
        for (BooleanProperty p : ABOVE.values()) {
            s = s.setValue(p, false);
        }
        registerDefaultState(s);
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        Connections.addProperties(builder);
        for (Direction d : Direction.Plane.HORIZONTAL) {
            builder.add(ABOVE.get(d));
        }
    }

    @Override
    public TagKey<Block> connectFamily() {
        return FAMILY;
    }

    /** Six cotes de la famille et les quatre cuves en diagonale haute. */
    @Override
    public BlockState connectAll(BlockState state, BlockGetter level, BlockPos pos) {
        state = Connections.connect(state, level, pos, FAMILY);
        BlockPos up = pos.above();
        for (Direction d : Direction.Plane.HORIZONTAL) {
            state = state.setValue(ABOVE.get(d), level.getBlockState(up.relative(d)).is(FAMILY));
        }
        return state;
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return connectAll(defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        //un voisin direct a change : ses cotes, et les diagonales hautes relues (le voisin du dessus ou d'un cote a pu changer)
        return connectAll(Connections.update(state, side, neighbor, FAMILY), level, pos);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }

    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return false;
    }
}
