package fr.pioneers.farm.kitchen;

import fr.pioneers.farm.PiKitchen;
import java.util.Locale;
import java.util.function.BiFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.BaseEntityBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.CampfireBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

/**
 * Bloc d'un poste de cuisine ou de transformation (un seul type de tuile pour tous : la classe de tuile est choisie par {@link Kind}).
 * 1.0.5-beta (lot 41) : clic droit = l'interface du poste ; Maj + clic droit main vide = un geste sur un poste manuel ({@link #use}). {@link #LIT} :
 * fourneau et four a pain allumes.
 */
public class StationBlock extends BaseEntityBlock {

    public static final DirectionProperty FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty LIT = BlockStateProperties.LIT;

    /** Postes : forme (px, facade au nord), tuile, chauffe-t-il le bloc du dessus, a-t-il une flamme. */
    public enum Kind {
        CUTTING_BOARD(StationTiles.CuttingBoard::new, false, false, 2, 0, 3, 14, 1.5, 13),
        KITCHEN_STOVE(StationTiles.Stove::new, true, true, 0, 0, 0, 16, 16, 16),
        COOKING_POT(StationTiles.CookingPot::new, false, false, 2, 0, 2, 14, 11, 14),
        FRYING_PAN(StationTiles.FryingPan::new, false, false, 1, 0, 1, 15, 3, 15),
        BREAD_OVEN(StationTiles.Oven::new, false, true, 0, 0, 0, 16, 16, 16),
        MIXING_BOWL(StationTiles.MixingBowl::new, false, false, 2, 0, 2, 14, 7, 14),
        DRYING_RACK(StationTiles.DryingRack::new, false, false, 0, 0, 6, 16, 16, 10),
        PRESERVES_JAR(StationTiles.Jar::new, false, false, 4, 0, 4, 12, 12, 12),
        FERMENTING_VAT(StationTiles.Vat::new, false, false, 1, 0, 1, 15, 14, 15),
        AGING_BARREL(StationTiles.Barrel::new, false, false, 1, 0, 1, 15, 16, 15),
        BREW_KETTLE(StationTiles.Kettle::new, false, false, 1, 0, 1, 15, 14, 15),
        COPPER_STILL(StationTiles.Still::new, false, false, 1, 0, 1, 15, 16, 15),
        FRUIT_PRESS(StationTiles.Press::new, false, false, 1, 0, 1, 15, 16, 15),
        CHEESE_PRESS(StationTiles.CheesePress::new, false, false, 2, 0, 2, 14, 14, 14),
        CAN_SEALER(StationTiles.Sealer::new, false, false, 2, 0, 2, 14, 12, 14),
        BUTTER_CHURN(StationTiles.MixingBowl::new, false, false, 3, 0, 3, 13, 15, 13),
        FEEDER(StationTiles.Feeder::new, false, false, 0, 0, 2, 16, 8, 14),
        WATER_TROUGH(StationTiles.Trough::new, false, false, 0, 0, 2, 16, 7, 14),
        HONEY_EXTRACTOR(HoneyExtractorTile::new, false, false, 2, 0, 2, 14, 15, 14),
        /** 1.6 (lot A2) : fumoir (grand poste 1 x 2, formes dans {@link BigStationBlock}). */
        SMOKEHOUSE(SmokehouseTile::new, false, false, 0, 0, 0, 16, 16, 16);

        final BiFunction<BlockPos, BlockState, StationTile> factory;
        private final boolean heats, flame;
        final VoxelShape[] shapes = new VoxelShape[4];

        Kind(BiFunction<BlockPos, BlockState, StationTile> factory, boolean heats, boolean flame, double x0, double y0, double z0, double x1, double y1,
             double z1) {
            this.factory = factory;
            this.heats = heats;
            this.flame = flame;
            for (int i = 0; i < 4; i++) {
                //Rotation de la boite (facade nord) autour du centre
                double ax0 = x0, az0 = z0, ax1 = x1, az1 = z1;
                for (int r = 0; r < i; r++) {
                    double nx0 = 16 - az1, nx1 = 16 - az0, nz0 = ax0, nz1 = ax1;
                    ax0 = nx0;
                    ax1 = nx1;
                    az0 = nz0;
                    az1 = nz1;
                }
                shapes[i] = Block.box(ax0, y0, az0, ax1, y1, az1);
            }
        }

        public boolean heats() {
            return heats;
        }

        public boolean flame() {
            return flame;
        }

        public String id() {
            return name().toLowerCase(Locale.ROOT);
        }

        /** 1.6 (lot A2) : grand poste de plusieurs cases ({@link BigStationBlock}) ? */
        public boolean big() {
            return BigStationBlock.isBig(this);
        }

        /** Poste a gestes (l'avancement augmente au clic : animation du geste). */
        public boolean stroke() {
            return this == CUTTING_BOARD || this == MIXING_BOWL || this == FRUIT_PRESS || this == CHEESE_PRESS || this == CAN_SEALER
                  || this == BUTTER_CHURN || this == HONEY_EXTRACTOR;
        }

        /** Recettes du poste (null : aucune, ex. fourneau). */
        @Nullable
        public Station station() {
            return switch (this) {
                case CUTTING_BOARD -> Station.CUTTING;
                case COOKING_POT -> Station.COOKING;
                case FRYING_PAN -> Station.FRYING;
                case BREAD_OVEN -> Station.BAKING;
                case MIXING_BOWL -> Station.MIXING;
                case DRYING_RACK -> Station.DRYING;
                case PRESERVES_JAR -> Station.PRESERVING;
                case FERMENTING_VAT -> Station.FERMENTING;
                case AGING_BARREL -> Station.AGING;
                case BREW_KETTLE -> Station.BREWING;
                case COPPER_STILL -> Station.DISTILLING;
                case FRUIT_PRESS -> Station.PRESSING;
                case CHEESE_PRESS -> Station.CHEESE_PRESSING;
                case BUTTER_CHURN -> Station.CHURNING;
                case HONEY_EXTRACTOR -> Station.EXTRACTING;
                case SMOKEHOUSE -> Station.SMOKING;
                default -> null;
            };
        }
    }

    final Kind kind;

    public StationBlock(Kind kind, Properties properties) {
        super(properties);
        this.kind = kind;
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false));
    }

    public Kind kind() {
        return kind;
    }

    /** 1.6 (lot A2) : bloc d'un poste (grand poste : {@link BigStationBlock}). */
    public static StationBlock create(Kind kind, Properties properties) {
        return kind.big() ? new BigStationBlock(kind, properties) : new StationBlock(kind, properties);
    }

    /** 1.6 (lot A2) : un feu de camp (vanilla ou du mod) juste sous cette case ? (sechoir pose sur le feu : etat {@link #LIT}). */
    public static boolean campfireBelow(BlockGetter level, BlockPos pos) {
        BlockState s = level.getBlockState(pos.below());
        return s.is(BlockTags.CAMPFIRES) || s.getBlock() instanceof CampfireBlock;
    }

    /** Sechoir : etat lit = pose sur un feu de camp (modele a piquets plantes de part et d'autre du feu). */
    static void syncRackOverFire(Level level, BlockPos pos, BlockState state) {
        if (state.getBlock() instanceof StationBlock b && b.kind == Kind.DRYING_RACK) {
            boolean over = campfireBelow(level, pos);
            if (state.getValue(LIT) != over) {
                level.setBlock(pos, state.setValue(LIT, over), 3);
            }
        }
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT);
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
        if (kind == Kind.DRYING_RACK) {
            state = state.setValue(LIT, campfireBelow(context.getLevel(), context.getClickedPos()));
        }
        return state;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        //1.6 (lot A2) : le sechoir suit le feu de camp du dessous (pose, retire)
        if (kind == Kind.DRYING_RACK && dir == Direction.DOWN) {
            state = state.setValue(LIT, other.is(BlockTags.CAMPFIRES) || other.getBlock() instanceof CampfireBlock);
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public RenderShape getRenderShape(BlockState state) {
        return RenderShape.MODEL;
    }

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        //nord = 0, est = 1, sud = 2, ouest = 3 (rotation horaire)
        int i = switch (state.getValue(FACING)) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
        return kind.shapes[i];
    }

    @Override
    public VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return kind == Kind.DRYING_RACK ? Shapes.empty() : getShape(state, level, pos, context);
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return kind.factory.apply(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (type != PiKitchen.STATION_TILE.get()) {
            return null;
        }
        return level.isClientSide ? (l, p, s, t) -> ((StationTile) t).clientTick() : (l, p, s, t) -> ((StationTile) t).serverTick();
    }

    /**
     * 1.0.5-beta (lot 41, retour 60 de Guillaume : « plus d'interactions directes, une vraie interface ») : clic droit = l'interface du poste
     * (interface standard v2) ; Maj + clic droit main vide sur un poste manuel = UN geste (coup de couteau, de fouet, tour de vis, de
     * manivelle : le geste principal, aussi au bouton de l'interface). Plus aucun objet, seau ni bouteille utilise sur le poste.
     */
    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (hand != InteractionHand.MAIN_HAND) {
            return InteractionResult.PASS;
        }
        if (!(level.getBlockEntity(pos) instanceof StationTile tile)) {
            return InteractionResult.PASS;
        }
        boolean gesture = player.isShiftKeyDown() && player.getMainHandItem().isEmpty() && player.getOffhandItem().isEmpty() && tile.manualGesture();
        if (level.isClientSide) {
            return InteractionResult.SUCCESS;
        }
        if (gesture) {
            tile.manual(player, 0);
        } else if (player instanceof ServerPlayer sp) {
            NetworkHooks.openScreen(sp, tile, tile.getBlockPos());
        }
        return InteractionResult.CONSUME;
    }

    @Override
    public void onRemove(BlockState state, Level level, BlockPos pos, BlockState newState, boolean moving) {
        if (!state.is(newState.getBlock()) && level.getBlockEntity(pos) instanceof StationTile tile) {
            tile.dropContents();
        }
        super.onRemove(state, level, pos, newState, moving);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (kind.flame && state.getValue(LIT)) {
            double x = pos.getX() + 0.5, y = pos.getY(), z = pos.getZ() + 0.5;
            Direction f = state.getValue(FACING);
            if (random.nextInt(3) == 0) {
                level.addParticle(ParticleTypes.CAMPFIRE_COSY_SMOKE, x + f.getStepX() * -0.3, y + 1.4, z + f.getStepZ() * -0.3, 0, 0.04, 0);
            }
            if (random.nextInt(5) == 0) {
                level.addParticle(ParticleTypes.FLAME, x + f.getStepX() * 0.45 + (random.nextDouble() - 0.5) * 0.3, y + 0.25,
                      z + f.getStepZ() * 0.45 + (random.nextDouble() - 0.5) * 0.3, 0, 0.005, 0);
            }
        }
        if (level.getBlockEntity(pos) instanceof StationTile tile) {
            tile.particles(random);
        }
    }

    @Override
    public boolean hasAnalogOutputSignal(BlockState state) {
        return false;
    }
}
