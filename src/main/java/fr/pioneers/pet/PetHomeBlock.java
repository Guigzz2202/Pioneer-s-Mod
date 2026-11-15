package fr.pioneers.pet;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.GlobalPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.items.ItemHandlerHelper;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * 1.0.5-beta (lot 5, specification §5.3, decision 9) : NICHE (chien) et PANIER (chat), dans les 11 bois des meubles. Porte / bord bas
 * tournes vers le joueur a la pose ({@code FACING}). Clic droit avec son chien (niche) ou son chat (panier) en laisse : il est lie (mode
 * Autonome, territoire de 30 blocs) et detache. Panier : 3 cases ou le chat rapporte ses poissons ; clic droit main vide : on les prend ;
 * entonnoirs (tuile {@link CatBasketTile}) ; comparateur. Modeles : {@code tools/gc/pets/pet_blocks.py}.
 */
public class PetHomeBlock extends HorizontalDirectionalBlock implements EntityBlock {

    /** Hauteur du coussin du panier (px) : le chat dort dessus. */
    public static final int BASKET_TOP = 4;
    private static final VoxelShape KENNEL = Shapes.or(Block.box(1, 0, 1, 15, 11, 15), Block.box(0.5, 11, 0.5, 15.5, 15, 15.5));
    private static final VoxelShape BASKET = Block.box(1, 0, 1, 15, 6, 15);
    private static final VoxelShape BASKET_COLLISION = Block.box(1, 0, 1, 15, BASKET_TOP, 15);

    public final boolean cat;

    public PetHomeBlock(Properties properties, boolean cat) {
        super(properties);
        this.cat = cat;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return defaultBlockState().setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return cat ? BASKET : KENNEL;
    }

    @NotNull
    @Override
    public VoxelShape getCollisionShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext ctx) {
        return cat ? BASKET_COLLISION : KENNEL;
    }

    @NotNull
    @Override
    public BlockState rotate(BlockState state, Rotation rot) {
        return state.setValue(FACING, rot.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState mirror(BlockState state, Mirror mirror) {
        return state.rotate(mirror.getRotation(state.getValue(FACING)));
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return cat ? new CatBasketTile(pos, state) : null;
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player,
          @NotNull InteractionHand hand, @NotNull BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        Pet led = led(level, player, cat);
        if (led != null) {
            if (!level.isClientSide) {
                link(led, level, pos, player);
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        ItemStack held = player.getItemInHand(hand);
        if (cat && held.isEmpty() && level.getBlockEntity(pos) instanceof CatBasketTile t && !t.empty()) {
            if (!level.isClientSide) {
                for (int i = 0; i < t.items.getSlots(); i++) {
                    ItemStack s = t.items.extractItem(i, 64, false);
                    if (!s.isEmpty()) {
                        //1.0.5-beta (lot 31) : les prises du chat vont dans le panier de peche du joueur s'il en a un
                        ItemHandlerHelper.giveItemToPlayer(player, fr.pioneers.home.bag.BagEvents.deliver(player, s));
                    }
                }
            }
            return InteractionResult.sidedSuccess(level.isClientSide);
        }
        if (!level.isClientSide && held.isEmpty()) {
            player.displayClientMessage(Component.translatable(cat ? "pet.pioneers.msg.basket_hint" : "pet.pioneers.msg.kennel_hint")
                  .withStyle(ChatFormatting.GRAY), true);
        }
        return held.isEmpty() ? InteractionResult.sidedSuccess(level.isClientSide) : InteractionResult.PASS;
    }

    /** Chien (niche) ou chat (panier) que le joueur tient en laisse et qu'il commande (null : aucun). */
    @Nullable
    public static Pet led(Level level, Player player, boolean cat) {
        List<Mob> mobs = level.getEntitiesOfClass(Mob.class, new AABB(player.blockPosition()).inflate(12),
              m -> m.getLeashHolder() == player && m instanceof Pet p && p.isCat() == cat);
        for (Mob m : mobs) {
            if (PetMaster.canCommand(m, player)) {
                return (Pet) m;
            }
        }
        return null;
    }

    /** Lie l'animal a cette niche / ce panier : mode Autonome, laisse rendue. */
    public static void link(Pet p, Level level, BlockPos pos, Player player) {
        p.setHome(GlobalPos.of(level.dimension(), pos.immutable()));
        p.setMode(PetMode.AUTONOMOUS);
        p.self().setOrderedToSit(false);
        if (p.self().isLeashed()) {
            p.self().dropLeash(true, !player.getAbilities().instabuild);
        }
        player.displayClientMessage(Component.translatable(p.isCat() ? "pet.pioneers.msg.linked_basket" : "pet.pioneers.msg.linked_kennel",
              p.self().getDisplayName(), PetCore.radius()), true);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState next, boolean moving) {
        if (!state.is(next.getBlock()) && level.getBlockEntity(pos) instanceof CatBasketTile t) {
            for (int i = 0; i < t.items.getSlots(); i++) {
                Block.popResource(level, pos, t.items.getStackInSlot(i));
            }
        }
        super.onRemove(state, level, pos, next, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return cat;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof CatBasketTile t ? t.comparator() : 0;
    }
}
