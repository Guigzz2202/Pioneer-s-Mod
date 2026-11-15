package fr.pioneers.farm.kitchen;

import fr.pioneers.farm.PiKitchen;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * 1.6 (lot A2, demande de Guillaume : « la brasserie devrait avoir des blocs plus gros ») : grand poste de 2 a 4 cases posees d'un coup
 * (cuve de fermentation 2 x 2, chaudron de brassage 1 x 2 haut, alambic et fut 2 large x 2 haut, fumoir 1 x 2 haut).
 * <ul>
 *     <li>La partie {@link #PART} 0 (la case posee) porte la tuile du poste et dessine TOUT le modele ; les autres cases portent une
 *     {@link StationPartTile} qui renvoie capacites (entonnoirs, tuyaux) et fiche au regard a la principale ; un clic sur n'importe quelle case
 *     est un clic sur le poste.</li>
 *     <li>Pose refusee (message) si une case n'est pas libre ; le poste s'etend vers la droite du joueur qui le pose, vers l'arriere et vers
 *     le haut.</li>
 *     <li>Casser une case casse tout (verification de voisinage dans {@link #updateShape}) ; seule la principale lache le poste (butin
 *     conditionne a {@code part = 0}) et son contenu.</li>
 *     <li>Les postes poses en un bloc avant la 1.6 se chargent en partie principale seule (tuile {@code structure} 0) : ils continuent de
 *     marcher, sans rien perdre, et leurs cases manquantes sont posees d'un coup des qu'elles sont toutes libres (essai toutes les 5 s ; la fiche
 *     au regard demande de faire la place). Tant qu'ils n'ont pas grandi, un voisin qui change ne les casse pas.</li>
 * </ul>
 * Cases ({@link #cells}) et formes : recopiees de {@code tools/gc/farm2/art_big_stations.py} (LAYOUT, BOXES), repere du modele (facade au
 * nord, pixels, origine au coin de la case principale).
 */
public class BigStationBlock extends StationBlock {

    public static final IntegerProperty PART = IntegerProperty.create("part", 0, 3);

    /** Cases (blocs, repere du modele) et formes par case et quart de tour. */
    private record Layout(int[][] cells, VoxelShape[][] shapes, double[][] boxes) {
    }

    private static final Map<Kind, Layout> LAYOUTS = new EnumMap<>(Kind.class);
    /** Cases du fut de vieillissement d'avant la 1.6.2 (2 x 2) : ses anciennes cases secondaires sont retirees par {@link #migrate}. */
    private static final int[][] OLD_BARREL = {{-1, 0, 0}, {0, 1, 0}, {-1, 1, 0}};

    static {
        layout(Kind.FERMENTING_VAT, new int[][]{{0, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {-1, 0, 1}},
              new double[][]{{-14.6, 0, 7.4, 14.6, 15.2, 24.6}, {-8.6, 0, 1.4, 8.6, 15.2, 30.6}});
        layout(Kind.BREW_KETTLE, new int[][]{{0, 0, 0}, {0, 1, 0}},
              new double[][]{{1.0, 0, 1.0, 15.0, 13.6, 15.0}, {0.6, 18.0, 0.6, 15.4, 23.4, 15.4}, {5.6, 23.4, 5.6, 10.4, 32, 10.4}});
        layout(Kind.COPPER_STILL, new int[][]{{0, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {-1, 1, 0}},
              new double[][]{{1.0, 0, 1.0, 15.0, 14.6, 15.0}, {3.4, 14.6, 3.4, 12.6, 24.6, 12.6}, {-14.0, 0, 3.0, -2.0, 15.4, 15.0},
                    {-9.6, 0, 0.3, -6.4, 3.4, 3.0}, {-7.0, 13.4, 7.2, 5.8, 22.6, 8.8}});
        //1.6.2 : barrique couchee 1 x 2, un bloc de haut (cave a tonneaux) ; avant : 2 x 2 (OLD_BARREL, reprise par migrate)
        layout(Kind.AGING_BARREL, new int[][]{{0, 0, 0}, {0, 0, 1}},
              new double[][]{{1.0, 0, 1.0, 15.0, 15.8, 31.0}, {6.8, 2.4, 0.3, 9.2, 7.0, 1.0}});
        layout(Kind.SMOKEHOUSE, new int[][]{{0, 0, 0}, {0, 1, 0}},
              new double[][]{{0.6, 0, 0.6, 15.4, 11.0, 15.4}, {1.0, 11.0, 1.0, 15.0, 27.0, 15.0}, {0.0, 27.0, 0.0, 16.0, 29.0, 16.0},
                    {3.6, 29.0, 0.0, 12.4, 31.9, 16.0}});
    }

    private static void layout(Kind kind, int[][] cells, double[][] boxes) {
        VoxelShape[][] shapes = new VoxelShape[cells.length][4];
        for (int p = 0; p < cells.length; p++) {
            for (int q = 0; q < 4; q++) {
                VoxelShape shape = Shapes.empty();
                for (double[] b : boxes) {
                    //Boite ramenee dans la case p, rognee a la case
                    double x0 = Math.max(0, b[0] - 16 * cells[p][0]), y0 = Math.max(0, b[1] - 16 * cells[p][1]), z0 = Math.max(0, b[2] - 16 * cells[p][2]);
                    double x1 = Math.min(16, b[3] - 16 * cells[p][0]), y1 = Math.min(16, b[4] - 16 * cells[p][1]), z1 = Math.min(16, b[5] - 16 * cells[p][2]);
                    if (x1 - x0 < 0.05 || y1 - y0 < 0.05 || z1 - z0 < 0.05) {
                        continue;
                    }
                    //Quart de tour horaire autour du centre de la case (meme regle que StationBlock.Kind)
                    for (int r = 0; r < q; r++) {
                        double nx0 = 16 - z1, nx1 = 16 - z0, nz0 = x0, nz1 = x1;
                        x0 = nx0;
                        x1 = nx1;
                        z0 = nz0;
                        z1 = nz1;
                    }
                    shape = Shapes.or(shape, Block.box(x0, y0, z0, x1, y1, z1));
                }
                //Une case sans rien de solide garde un petit socle (sinon on ne pourrait ni la viser ni la casser)
                shapes[p][q] = shape.isEmpty() ? Block.box(7, 0, 7, 9, 1, 9) : shape.optimize();
            }
        }
        LAYOUTS.put(kind, new Layout(cells, shapes, boxes));
    }

    static boolean isBig(Kind kind) {
        return LAYOUTS.containsKey(kind);
    }

    public BigStationBlock(Kind kind, Properties properties) {
        super(kind, properties);
        registerDefaultState(stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(LIT, false).setValue(PART, 0));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, LIT, PART);
    }

    // ------------------------------------------------------------------
    // Cases
    // ------------------------------------------------------------------

    private Layout layout() {
        return LAYOUTS.get(kind);
    }

    /** Nombre de cases du poste. */
    public int parts() {
        return layout().cells.length;
    }

    private static int quarter(Direction facing) {
        return switch (facing) {
            case EAST -> 1;
            case SOUTH -> 2;
            case WEST -> 3;
            default -> 0;
        };
    }

    /** Decalage (blocs, monde) de la case {@code part} depuis la case principale. */
    private BlockPos offset(Direction facing, int part) {
        if (part >= parts()) {
            return BlockPos.ZERO;                                               //case d'une ancienne disposition (voir migrate)
        }
        int[] c = layout().cells[part];
        return rotate(c, facing);
    }

    private static BlockPos rotate(int[] c, Direction facing) {
        int dx = c[0], dz = c[2];
        for (int q = quarter(facing); q > 0; q--) {
            int t = dx;
            dx = -dz;
            dz = t;
        }
        return new BlockPos(dx, c[1], dz);
    }

    /** Case {@code part} d'un poste dont la principale est en {@code main}. */
    public BlockPos cell(BlockPos main, Direction facing, int part) {
        return main.offset(offset(facing, part));
    }

    /** Case principale (celle qui porte la tuile) de la case {@code pos}. */
    public BlockPos mainPos(BlockState state, BlockPos pos) {
        int part = state.hasProperty(PART) ? state.getValue(PART) : 0;
        return part == 0 ? pos : pos.subtract(offset(state.getValue(FACING), part));
    }

    /** Boite de rendu de tout le poste (tuile principale). */
    public AABB renderBox(BlockPos main, Direction facing) {
        AABB box = new AABB(main);
        for (int p = 1; p < parts(); p++) {
            box = box.minmax(new AABB(cell(main, facing, p)));
        }
        return box.inflate(0.5);
    }

    /** Taille du poste (largeur, profondeur, hauteur) pour le message de place. */
    private int[] size() {
        int[] lo = {0, 0, 0}, hi = {0, 0, 0};
        for (int[] c : layout().cells) {
            for (int i = 0; i < 3; i++) {
                lo[i] = Math.min(lo[i], c[i]);
                hi[i] = Math.max(hi[i], c[i]);
            }
        }
        return new int[]{hi[0] - lo[0] + 1, hi[2] - lo[2] + 1, hi[1] - lo[1] + 1};
    }

    // ------------------------------------------------------------------
    // Pose et casse
    // ------------------------------------------------------------------

    @Nullable
    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        Direction facing = context.getHorizontalDirection().getOpposite();
        BlockState state = defaultBlockState().setValue(FACING, facing);
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (int p = 1; p < parts(); p++) {
            BlockPos c = cell(pos, facing, p);
            if (level.isOutsideBuildHeight(c) || !level.getBlockState(c).canBeReplaced()
                  || !level.isUnobstructed(state.setValue(PART, p), c, CollisionContext.empty())) {
                if (level.isClientSide && context.getPlayer() != null) {
                    int[] s = size();
                    context.getPlayer().displayClientMessage(Component.translatable("farm.pioneers.station.no_room", s[0], s[1], s[2])
                          .withStyle(ChatFormatting.GOLD), true);
                }
                return null;
            }
        }
        return state;
    }

    @Override
    public void setPlacedBy(Level level, BlockPos pos, BlockState state, @Nullable LivingEntity placer, ItemStack stack) {
        super.setPlacedBy(level, pos, state, placer, stack);
        if (!level.isClientSide) {
            Direction facing = state.getValue(FACING);
            for (int p = 1; p < parts(); p++) {
                level.setBlock(cell(pos, facing, p), state.setValue(PART, p).setValue(LIT, false), 3);
            }
            if (level.getBlockEntity(pos) instanceof StationTile tile) {
                tile.structure = 1;
                tile.sync();
            }
        }
    }

    /** Index de la case {@code other} dans le poste de la case {@code pos} (-1 : hors du poste). */
    private int indexOf(BlockState state, BlockPos pos, BlockPos other) {
        BlockPos main = mainPos(state, pos);
        Direction facing = state.getValue(FACING);
        for (int p = 0; p < parts(); p++) {
            if (cell(main, facing, p).equals(other)) {
                return p;
            }
        }
        return -1;
    }

    @Override
    public BlockState updateShape(BlockState state, Direction dir, BlockState other, LevelAccessor level, BlockPos pos, BlockPos otherPos) {
        if (state.getValue(PART) >= parts()) {
            return Blocks.AIR.defaultBlockState();                              //case d'une ancienne disposition
        }
        //Une case du poste a change : si ce n'est plus la bonne partie (cassee, remplacee), tout le poste part (la principale lache son butin)
        int index = indexOf(state, pos, otherPos);
        if (index >= 0 && !(other.is(this) && other.getValue(PART) == index && other.getValue(FACING) == state.getValue(FACING))) {
            //Poste d'avant la 1.6 qui n'a pas encore grandi : ses voisins ne sont pas (encore) ses cases
            if (state.getValue(PART) == 0 && level.getBlockEntity(pos) instanceof StationTile tile && tile.structure != 1) {
                return super.updateShape(state, dir, other, level, pos, otherPos);
            }
            return Blocks.AIR.defaultBlockState();
        }
        return super.updateShape(state, dir, other, level, pos, otherPos);
    }

    @Override
    public void playerWillDestroy(Level level, BlockPos pos, BlockState state, Player player) {
        //Case secondaire cassee en creatif : la principale part sans rien lacher
        if (!level.isClientSide && player.isCreative() && state.getValue(PART) != 0) {
            BlockPos main = mainPos(state, pos);
            BlockState ms = level.getBlockState(main);
            if (ms.is(this) && ms.getValue(PART) == 0) {
                level.setBlock(main, Blocks.AIR.defaultBlockState(), 35);
                level.levelEvent(player, 2001, main, Block.getId(ms));
            }
        }
        super.playerWillDestroy(level, pos, state, player);
    }

    /**
     * Poste pose en un bloc avant la 1.6 (tuile {@code structure} 0) : ses cases manquantes sont posees d'un coup des qu'elles sont toutes
     * libres (et chargees) ; vrai si le poste est complet.
     */
    boolean grow(Level level, BlockPos main, BlockState state) {
        Direction facing = state.getValue(FACING);
        List<Integer> missing = new ArrayList<>();
        for (int p = 1; p < parts(); p++) {
            BlockPos c = cell(main, facing, p);
            if (!level.isLoaded(c)) {
                return false;
            }
            BlockState s = level.getBlockState(c);
            if (s.is(this) && s.getValue(PART) == p && s.getValue(FACING) == facing) {
                continue;
            }
            if (level.isOutsideBuildHeight(c) || !s.canBeReplaced()) {
                return false;
            }
            missing.add(p);
        }
        for (int p : missing) {
            level.setBlock(cell(main, facing, p), state.setValue(PART, p).setValue(LIT, false), 3);
        }
        return true;
    }

    /**
     * 1.6.2 : fut de vieillissement pose en 2 x 2 (1.6.0) : ses anciennes cases secondaires sont retirees sans butin ; vrai si le poste doit
     * reprendre sa nouvelle forme (cases manquantes posees par {@link #grow}).
     */
    private boolean migrate(Level level, BlockPos main, BlockState state) {
        Direction facing = state.getValue(FACING);
        boolean changed = false;
        for (int[] c : OLD_BARREL) {
            BlockPos at = main.offset(rotate(c, facing));
            BlockState s = level.getBlockState(at);
            boolean current = false;
            for (int p = 1; p < parts(); p++) {
                current |= cell(main, facing, p).equals(at);
            }
            if (!current && s.is(this) && s.getValue(PART) != 0 && s.getValue(FACING) == facing) {
                level.setBlock(at, Blocks.AIR.defaultBlockState(), 3 | 32);
                changed = true;
            }
        }
        for (int p = 1; p < parts(); p++) {
            BlockState s = level.getBlockState(cell(main, facing, p));
            changed |= !(s.is(this) && s.getValue(PART) == p && s.getValue(FACING) == facing);
        }
        return changed;
    }

    // ------------------------------------------------------------------
    // Forme, tuile, clics
    // ------------------------------------------------------------------

    @Override
    public VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        Layout l = layout();
        int p = Math.min(state.getValue(PART), l.cells.length - 1);
        return l.shapes[p][quarter(state.getValue(FACING))];
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return state.getValue(PART) == 0 ? kind.factory.apply(pos, state) : new StationPartTile(pos, state);
    }

    @Nullable
    @Override
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (state.getValue(PART) != 0 || type != PiKitchen.STATION_TILE.get()) {
            return null;
        }
        if (level.isClientSide) {
            return (l, p, s, t) -> ((StationTile) t).clientTick();
        }
        return (l, p, s, t) -> {
            StationTile tile = (StationTile) t;
            if (tile.structure == 1 && kind == Kind.AGING_BARREL && l.getGameTime() % 100 == Math.floorMod(p.asLong(), 100) && migrate(l, p, s)) {
                tile.structure = 0;                                             //grandira dans sa nouvelle forme des que la place est libre
                tile.structureRetry = 0;
            }
            if (tile.structure != 1 && --tile.structureRetry <= 0) {
                tile.structureRetry = 100;
                if (grow(l, p, s)) {
                    tile.structure = 1;
                    tile.sync();
                }
            }
            tile.serverTick();
        };
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (state.getValue(PART) != 0) {
            if (state.getValue(PART) >= parts()) {
                return InteractionResult.PASS;
            }
            BlockPos main = mainPos(state, pos);
            BlockState ms = level.getBlockState(main);
            return ms.is(this) ? super.use(ms, level, main, player, hand, hit) : InteractionResult.PASS;
        }
        return super.use(state, level, pos, player, hand, hit);
    }

    @Override
    public void animateTick(BlockState state, Level level, BlockPos pos, RandomSource random) {
        if (state.getValue(PART) == 0 && level.getBlockEntity(pos) instanceof StationTile tile) {
            BigStationFx.particles(tile, state, random);
        }
    }
}
