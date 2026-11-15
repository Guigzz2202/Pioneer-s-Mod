package fr.pioneers.world.nature;

import fr.pioneers.Pioneers;
import fr.pioneers.core.block.PiBlockEntity;
import fr.pioneers.farm.FarmCard;
import fr.pioneers.tech.kinetics.KineticEvents;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.Containers;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.LeavesBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.items.IItemHandler;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Saignee : posee sur le flanc d'une buche d'arbre vivant (le tronc est derriere elle), elle recueille la seve sans energie. Epicea :
 * resine brute ; acajou (jungle) : latex brut. Le debit grandit avec la hauteur du tronc et se partage entre les saignees d'un meme tronc.
 * Clic droit main vide : on prend le contenu ; accroupi : seulement l'etat. Les goulottes la vident par toutes les faces ; celle en bronze
 * verse d'elle-meme dans un inventaire place dessous.
 */
public class ResinTapBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final IntegerProperty FILL = IntegerProperty.create("fill", 0, 3);
    /** Raisons d'arret : en marche, pas un arbre vivant, essence sans seve, pleine, autre seve deja dedans. */
    public static final int RUNNING = 0, NOT_TREE = 1, NO_SAP = 2, FULL = 3, MIXED = 4;

    public final boolean bronze;
    private final VoxelShape[] shapes = new VoxelShape[4];

    public ResinTapBlock(boolean bronze, Properties properties) {
        super(properties);
        this.bronze = bronze;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(FILL, 0));
        //Forme dessinee facade au nord (tronc au sud) puis tournee
        double[] box = bronze ? new double[]{4, 1, 7, 12, 13, 16} : new double[]{5, 2, 8, 11, 12, 16};
        for (Direction d : Direction.Plane.HORIZONTAL) {
            shapes[d.get2DDataValue()] = rotated(box, d);
        }
    }

    private static VoxelShape rotated(double[] b, Direction facing) {
        double x0 = b[0], y0 = b[1], z0 = b[2], x1 = b[3], y1 = b[4], z1 = b[5];
        return switch (facing) {
            case SOUTH -> Block.box(16 - x1, y0, 16 - z1, 16 - x0, y1, 16 - z0);
            case EAST -> Block.box(16 - z1, y0, x0, 16 - z0, y1, x1);
            case WEST -> Block.box(z0, y0, 16 - x1, z1, y1, 16 - x0);
            default -> Block.box(x0, y0, z0, x1, y1, z1);
        };
    }

    public int capacity() {
        return bronze ? 16 : 4;
    }

    /** Ticks par unite pour un tronc de 4 buches et une seule saignee. */
    public int baseTicks() {
        return bronze ? 1800 : 3600;
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, FILL);
    }

    @NotNull
    @Override
    public VoxelShape getShape(@NotNull BlockState state, @NotNull BlockGetter level, @NotNull BlockPos pos, @NotNull CollisionContext context) {
        VoxelShape shape = shapes[state.getValue(FACING).get2DDataValue()];
        return shape == null ? Shapes.block() : shape;
    }

    @NotNull
    @Override
    public RenderShape getRenderShape(@NotNull BlockState state) {
        return RenderShape.MODEL;
    }

    @Nullable
    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        Direction clicked = context.getClickedFace();
        if (clicked.getAxis().isHorizontal()) {
            BlockState state = defaultBlockState().setValue(FACING, clicked);
            if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                return state;
            }
        }
        for (Direction d : context.getNearestLookingDirections()) {
            if (d.getAxis().isHorizontal()) {
                BlockState state = defaultBlockState().setValue(FACING, d.getOpposite());
                if (state.canSurvive(context.getLevel(), context.getClickedPos())) {
                    return state;
                }
            }
        }
        return null;
    }

    @Override
    public boolean canSurvive(@NotNull BlockState state, @NotNull LevelReader level, @NotNull BlockPos pos) {
        return level.getBlockState(pos.relative(state.getValue(FACING).getOpposite())).is(BlockTags.LOGS);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState updateShape(@NotNull BlockState state, @NotNull Direction direction, @NotNull BlockState neighbor, @NotNull LevelAccessor level,
          @NotNull BlockPos pos, @NotNull BlockPos neighborPos) {
        if (direction == state.getValue(FACING).getOpposite() && !state.canSurvive(level, pos)) {
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, direction, neighbor, level, pos, neighborPos);
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return state.setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @NotNull
    @Override
    @SuppressWarnings("deprecation")
    public InteractionResult use(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull Player player, @NotNull InteractionHand hand,
          @NotNull BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof Tile tile)) {
            return InteractionResult.PASS;
        }
        if (!player.getItemInHand(hand).isEmpty() && !player.isShiftKeyDown()) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide) {
            if (!player.isShiftKeyDown() && !tile.contents.isEmpty()) {
                ItemStack taken = tile.contents.copy();
                tile.setContents(ItemStack.EMPTY);
                if (!player.addItem(taken)) {
                    player.drop(taken, false);
                }
                level.playSound(null, pos, SoundEvents.HONEY_BLOCK_BREAK, SoundSource.BLOCKS, 0.7F, 1.2F);
                KineticEvents.award((ServerLevel) level, pos, "resin_tap");
            }
            tile.rescan();
            player.displayClientMessage(tile.status(), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    @Override
    @SuppressWarnings("deprecation")
    public void onRemove(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos, @NotNull BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof Tile tile && !tile.contents.isEmpty()) {
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, tile.contents.copy());
            tile.contents = ItemStack.EMPTY;
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    @SuppressWarnings("deprecation")
    public boolean hasAnalogOutputSignal(@NotNull BlockState state) {
        return true;
    }

    @Override
    @SuppressWarnings("deprecation")
    public int getAnalogOutputSignal(@NotNull BlockState state, @NotNull Level level, @NotNull BlockPos pos) {
        return level.getBlockEntity(pos) instanceof Tile tile ? tile.contents.getCount() * 15 / capacity() : 0;
    }

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new Tile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(@NotNull Level level, @NotNull BlockState state, @NotNull BlockEntityType<T> type) {
        return level.isClientSide ? null : createTickerHelper(type, PiNature.RESIN_TAP_TILE.get(), (l, p, s, tile) -> tile.serverTick((ServerLevel) l));
    }

    /** Seve donnee par la buche (resine, latex) ou null si l'essence n'en donne pas. */
    @Nullable
    static Item sapOf(BlockState log) {
        String id = log.is(PiNature.RESIN_LOGS) ? "raw_resin" : log.is(PiNature.LATEX_LOGS) ? "raw_latex" : null;
        if (id == null) {
            return null;
        }
        Item item = ForgeRegistries.ITEMS.getValue(Pioneers.rl(id));
        return item == null || item == Items.AIR ? null : item;
    }

    public static class Tile extends PiBlockEntity {

        ItemStack contents = ItemStack.EMPTY;
        private int progress, interval, reason = NOT_TREE;
        private long scanned = Long.MIN_VALUE / 2;
        @Nullable
        private Item sap;
        private final IItemHandler handler = new Handler();
        private final LazyOptional<IItemHandler> cap = LazyOptional.of(() -> handler);

        public Tile(BlockPos pos, BlockState state) {
            super(PiNature.RESIN_TAP_TILE.get(), pos, state);
        }

        private ResinTapBlock tap() {
            return getBlockState().getBlock() instanceof ResinTapBlock b ? b : PiNature.WOODEN_RESIN_TAP.getBlock();
        }

        void serverTick(ServerLevel level) {
            if ((level.getGameTime() + worldPosition.asLong()) % 20 != 0) {
                return;
            }
            if (level.getGameTime() - scanned >= 200) {
                rescan();
            }
            if (reason == RUNNING || reason == FULL || reason == MIXED) {
                reason = RUNNING;
                if (sap != null && !contents.isEmpty() && contents.getItem() != sap) {
                    reason = MIXED;
                } else if (contents.getCount() >= tap().capacity()) {
                    reason = FULL;
                } else if (sap != null) {
                    progress += 20;
                    if (progress >= interval) {
                        progress = 0;
                        setContents(contents.isEmpty() ? new ItemStack(sap) : contents.copyWithCount(contents.getCount() + 1));
                    }
                }
            }
            if (tap().bronze && !contents.isEmpty()) {
                BlockEntity below = level.getBlockEntity(worldPosition.below());
                if (below != null) {
                    below.getCapability(ForgeCapabilities.ITEM_HANDLER, Direction.UP).ifPresent(target -> {
                        ItemStack rest = ItemHandlerHelper.insertItemStacked(target, contents.copy(), false);
                        if (rest.getCount() != contents.getCount()) {
                            setContents(rest);
                        }
                    });
                }
            }
        }

        /** Releve le tronc : essence, hauteur, feuillage naturel, saignees voisines ; fixe le delai par unite ou la raison d'arret. */
        void rescan() {
            if (level == null) {
                return;
            }
            scanned = level.getGameTime();
            Direction facing = getBlockState().getValue(FACING);
            BlockPos log = worldPosition.relative(facing.getOpposite());
            BlockState logState = level.getBlockState(log);
            sap = sapOf(logState);
            if (!logState.is(BlockTags.LOGS)) {
                reason = NOT_TREE;
                return;
            }
            Block wood = logState.getBlock();
            BlockPos bottom = log;
            for (int i = 0; i < 16 && level.getBlockState(bottom.below()).is(wood); i++) {
                bottom = bottom.below();
            }
            BlockPos top = bottom;
            int count = 1;
            while (count < 16 && level.getBlockState(top.above()).is(wood)) {
                top = top.above();
                count++;
            }
            if (count < 3 || !naturalLeaves(top)) {
                reason = NOT_TREE;
                return;
            }
            if (sap == null) {
                reason = NO_SAP;
                return;
            }
            int taps = 0;
            for (BlockPos p = bottom; p.getY() <= top.getY(); p = p.above()) {
                for (Direction d : Direction.Plane.HORIZONTAL) {
                    BlockState near = level.getBlockState(p.relative(d));
                    if (near.getBlock() instanceof ResinTapBlock && near.getValue(FACING) == d) {
                        taps++;
                    }
                }
            }
            interval = tap().baseTicks() * 4 / Math.min(8, count) * Math.max(1, taps);
            if (reason == NOT_TREE || reason == NO_SAP) {
                reason = RUNNING;
            }
        }

        private boolean naturalLeaves(BlockPos top) {
            for (BlockPos p : BlockPos.betweenClosed(top.offset(-2, -1, -2), top.offset(2, 3, 2))) {
                BlockState state = level.getBlockState(p);
                if (state.is(BlockTags.LEAVES) && !(state.hasProperty(LeavesBlock.PERSISTENT) && state.getValue(LeavesBlock.PERSISTENT))) {
                    return true;
                }
            }
            return false;
        }

        /** 1.6 : fiche au regard synthetique : recolte (contenu / contenance), rythme, ou ce qui l'arrete en un mot. */
        public List<FarmCard.Line> lookLines() {
            List<FarmCard.Line> lines = new ArrayList<>();
            int cap = tap().capacity();
            Component name = sap != null ? new ItemStack(sap).getHoverName() : contents.isEmpty() ? FarmCard.word("empty") : contents.getHoverName();
            switch (reason) {
                case NOT_TREE -> lines.add(FarmCard.Line.text(FarmCard.word("tap.not_tree"), FarmCard.BAD));
                case NO_SAP -> lines.add(FarmCard.Line.text(FarmCard.word("tap.no_sap"), FarmCard.WARN));
                default -> {
                    lines.add(FarmCard.Line.gauge(Component.literal("").append(name), contents.getCount() / (float) Math.max(1, cap),
                          FarmCard.ratio(contents.getCount(), cap), reason == FULL ? FarmCard.WARN : 0xC89A5A));
                    if (reason == MIXED) {
                        lines.add(FarmCard.Line.pair(FarmCard.label("state"), FarmCard.word("tap.mixed"), FarmCard.WARN));
                    } else if (reason == FULL) {
                        lines.add(FarmCard.Line.pair(FarmCard.label("state"), FarmCard.word("full"), FarmCard.WARN));
                    } else {
                        lines.add(FarmCard.Line.pair(FarmCard.label("rate"), FarmCard.word("every", FarmCard.duration(Math.max(1, interval / 20))), FarmCard.INFO));
                    }
                }
            }
            return lines;
        }

        public Component status() {
            int cap = tap().capacity();
            Component name = sap != null ? new ItemStack(sap).getHoverName() : contents.isEmpty()
                  ? Component.translatable("block.pioneers.resin_tap.empty") : contents.getHoverName();
            return switch (reason) {
                case NOT_TREE -> Component.translatable("block.pioneers.resin_tap.not_tree");
                case NO_SAP -> Component.translatable("block.pioneers.resin_tap.no_sap");
                case FULL -> Component.translatable("block.pioneers.resin_tap.full", name, contents.getCount(), cap);
                case MIXED -> Component.translatable("block.pioneers.resin_tap.mixed", contents.getHoverName());
                default -> Component.translatable("block.pioneers.resin_tap.info", name, contents.getCount(), cap, Math.max(1, interval / 20));
            };
        }

        void setContents(ItemStack stack) {
            contents = stack;
            setChanged();
            if (level != null) {
                int cap = tap().capacity();
                int fill = stack.isEmpty() ? 0 : Math.max(1, Math.min(3, (stack.getCount() * 3 + cap - 1) / cap));
                BlockState state = getBlockState();
                if (state.hasProperty(FILL) && state.getValue(FILL) != fill) {
                    level.setBlock(worldPosition, state.setValue(FILL, fill), Block.UPDATE_ALL);
                }
                level.updateNeighbourForOutputSignal(worldPosition, state.getBlock());
            }
        }

        @NotNull
        @Override
        public <T> LazyOptional<T> getCapability(@NotNull Capability<T> capability, @Nullable Direction side) {
            return capability == ForgeCapabilities.ITEM_HANDLER ? cap.cast() : super.getCapability(capability, side);
        }

        @Override
        public void invalidateCaps() {
            super.invalidateCaps();
            cap.invalidate();
        }

        @Override
        protected void saveAdditional(@NotNull CompoundTag tag) {
            super.saveAdditional(tag);
            tag.put("contents", contents.save(new CompoundTag()));
            tag.putInt("progress", progress);
        }

        @Override
        public void load(@NotNull CompoundTag tag) {
            super.load(tag);
            contents = ItemStack.of(tag.getCompound("contents"));
            progress = tag.getInt("progress");
        }

        /** Une case, extraction seule. */
        private class Handler implements IItemHandler {

            @Override
            public int getSlots() {
                return 1;
            }

            @NotNull
            @Override
            public ItemStack getStackInSlot(int slot) {
                return contents;
            }

            @NotNull
            @Override
            public ItemStack insertItem(int slot, @NotNull ItemStack stack, boolean simulate) {
                return stack;
            }

            @NotNull
            @Override
            public ItemStack extractItem(int slot, int amount, boolean simulate) {
                if (contents.isEmpty() || amount <= 0) {
                    return ItemStack.EMPTY;
                }
                int n = Math.min(amount, contents.getCount());
                ItemStack out = contents.copyWithCount(n);
                if (!simulate) {
                    setContents(n >= contents.getCount() ? ItemStack.EMPTY : contents.copyWithCount(contents.getCount() - n));
                }
                return out;
            }

            @Override
            public int getSlotLimit(int slot) {
                return tap().capacity();
            }

            @Override
            public boolean isItemValid(int slot, @NotNull ItemStack stack) {
                return false;
            }
        }
    }
}
