package fr.pioneers.bronze.foundry;

import fr.pioneers.bronze.BronzeBlock;
import fr.pioneers.registries.PiBronze;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Table de coulee : clic droit avec un moule = le poser ; main vide = prendre la piece, sinon le moule ; Maj + clic = l'interface.
 * <p>1.0.2-beta (lot 2, notes de Guillaume : « la table de coulee ne doit plus etre positionnee dans un certain sens, elle se connecte au
 * fourneau a creuset adjacent ; sa texture s'adapte lors de la connexion ») : plus d'orientation a la pose : la table cherche un fourneau a
 * creuset contre un de ses quatre cotes (ou un fondeur d'alliages a vapeur tourne vers elle), se tourne vers lui ({@link #FACING} = dos vers
 * la source) et passe {@link #LINKED} (modele {@code casting_table_linked} : rigole d'acier depuis le creuset, bec verseur) ; seule, son plateau
 * a un rebord net. Recalcule a chaque changement d'un voisin horizontal (et a la premiere seconde d'une table posee avant le lot 2).
 */
public class CastingTableBlock extends BronzeBlock {

    public static final BooleanProperty LINKED = BooleanProperty.create("linked");

    public CastingTableBlock(Properties properties) {
        super(properties, CastingTableTile::new, Block.box(0, 0, 0, 16, 14, 16));
        registerDefaultState(defaultBlockState().setValue(LINKED, false));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(LINKED);
    }

    /** Cote ou se trouve la source de metal (fourneau a creuset, ou fondeur d'alliages tourne vers la table), ou null. */
    @Nullable
    public static Direction source(BlockGetter level, BlockPos pos) {
        Direction found = null;
        for (Direction d : Direction.Plane.HORIZONTAL) {
            BlockState next = level.getBlockState(pos.relative(d));
            if (next.is(PiBronze.CRUCIBLE_FURNACE.getBlock())) {
                //Le fourneau qui regarde la table d'abord (son devant), sinon le premier trouve
                if (next.getValue(FACING) == d.getOpposite()) {
                    return d;
                }
                if (found == null) {
                    found = d;
                }
            }
        }
        return found;
    }

    /** Etat raccorde (ou seul) de la table d'apres ses voisins ; {@code fallback} = orientation sans source. */
    public static BlockState linked(BlockState state, BlockGetter level, BlockPos pos) {
        Direction from = source(level, pos);
        return from == null ? state.setValue(LINKED, false) : state.setValue(LINKED, true).setValue(FACING, from.getOpposite());
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return linked(super.getStateForPlacement(context), context.getLevel(), context.getClickedPos());
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbour, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighbourPos) {
        return direction.getAxis().isHorizontal() ? linked(state, level, pos) : state;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (player.isShiftKeyDown() || !(level.getBlockEntity(pos) instanceof CastingTableTile table)) {
            return super.use(state, level, pos, player, hand, hit);
        }
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        ItemStack held = player.getItemInHand(hand);
        if (!table.outputSlot().isEmpty()) {
            player.getInventory().placeItemBackInInventory(table.outputSlot().getStack().copy());
            table.outputSlot().setStack(ItemStack.EMPTY);
        } else if (held.getItem() instanceof MoldItem mold && CastingUnit.tableMold(mold) && table.moldSlot().isEmpty()) {
            table.moldSlot().setStack(held.split(1));
        } else if (held.isEmpty() && !table.moldSlot().isEmpty() && table.amount() == 0) {
            player.setItemInHand(hand, table.moldSlot().getStack().copy());
            table.moldSlot().setStack(ItemStack.EMPTY);
        } else {
            return super.use(state, level, pos, player, hand, hit);
        }
        return InteractionResult.CONSUME;
    }
}
