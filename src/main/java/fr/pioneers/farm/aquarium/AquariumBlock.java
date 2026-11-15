package fr.pioneers.farm.aquarium;

import fr.pioneers.common.block.Connections;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.AbstractGlassBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.4-beta (lot 28, Guillaume : « Aquarium » ; reponse : « deco connectable, bloc de verre qui se connecte en grand bassin ; on y met
 * poissons et crustaces du mod (et poissons vanilla) au seau, ils nagent dedans ; pas d'elevage ») : bloc de verre a cadre d'acier noirci,
 * TEXTURES CONNECTEES (famille {@code pioneers:connects/aquarium} : les aquariums voisins forment un seul bassin, cadre seulement sur le
 * pourtour), {@link #FILLED} = eau dedans (seau d'eau : remplit la case vide la plus basse du bassin ; seau vide : reprend l'eau de la case
 * pleine la plus haute sans habitant). Habitants et eau : {@link AquariumTile} ; rendu {@code J:client/farm/aquarium/AquariumRenderer}.
 */
public class AquariumBlock extends AbstractGlassBlock implements EntityBlock, Connections.Connected {

    public static final TagKey<Block> FAMILY = Connections.family("aquarium");
    public static final BooleanProperty FILLED = BooleanProperty.create("filled");

    public AquariumBlock(Properties properties) {
        super(properties);
        registerDefaultState(Connections.unconnected(stateDefinition.any()).setValue(FILLED, false));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        Connections.addProperties(builder);
        builder.add(FILLED);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return Connections.connect(defaultBlockState(), context.getLevel(), context.getClickedPos(), FAMILY);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction side, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        return Connections.update(state, side, neighbor, FAMILY);
    }

    @Override
    public TagKey<Block> connectFamily() {
        return FAMILY;
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onPlace(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState old, boolean moving) {
        super.onPlace(state, level, pos, old, moving);
        Connections.heal(state, level, pos, old);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new AquariumTile(pos, state);
    }

    @Override
    @SuppressWarnings("deprecation")
    public @NotNull InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        return Aquariums.use(state, level, pos, player, hand);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof AquariumTile tile) {
            tile.release();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    /** Lumiere du verre : laisse passer le ciel (comme le verre vanilla). */
    @Override
    public boolean propagatesSkylightDown(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos) {
        return !state.getValue(FILLED);
    }
}
