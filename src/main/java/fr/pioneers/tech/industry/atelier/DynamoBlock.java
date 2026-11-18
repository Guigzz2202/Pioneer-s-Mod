package fr.pioneers.tech.industry.atelier;

import fr.pioneers.society.clan.ClanApi;
import fr.pioneers.core.Directions;
import fr.pioneers.tech.kinetics.AxisKineticBlock;
import fr.pioneers.tech.kinetics.KineticNode;
import fr.pioneers.tech.kinetics.KineticPlacement;
import fr.pioneers.logistics.transport.CableTier;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DirectionProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.capabilities.ForgeCapabilities;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * Dynamo (4 tiers) : convertit la rotation de son arbre (axe du bloc) en energie, poussee par les quatre autres faces.
 * <p>
 * 1.12.4 (lot F4, Guillaume : « ameliorer l'orientation native du generateur a dynamo quand il est pose ») : etat {@link #FACING} = facade
 * (boite a bornes), toujours perpendiculaire a l'axe ; le socle est a l'oppose. A la pose (voir {@link #placement}) :
 * <ul>
 *   <li>axe : celui qui raccorde le plus de bouts d'arbre voisins ({@link KineticPlacement#score}) ; sans voisin, un axe horizontal qui
 *   presente le flanc au joueur (pose au sol ou au plafond) ou longe le mur clique ; Maj : axe du regard ;</li>
 *   <li>facade : vers un cable ou une machine qui prend l'energie, sinon dos au support clique (socle contre le sol, le mur...), sinon vers
 *   le joueur.</li>
 * </ul>
 * Clic droit : interface (vitesse, couple, puissance, rendement, reserve, etat, excitation).
 */
public class DynamoBlock extends AxisKineticBlock {

    public static final DirectionProperty FACING = BlockStateProperties.FACING;

    public final CableTier tier;

    public DynamoBlock(CableTier tier, Properties properties) {
        super(properties);
        this.tier = tier;
        registerDefaultState(defaultBlockState().setValue(AXIS, Direction.Axis.Z).setValue(FACING, Direction.UP));
    }

    @Override
    protected void createBlockStateDefinition(@NotNull StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(FACING);
    }

    @Override
    public BlockState getStateForPlacement(@NotNull BlockPlaceContext context) {
        return placement(this, context);
    }

    /** Etat de pose (serveur et apercu client : meme calcul). */
    public static BlockState placement(DynamoBlock block, BlockPlaceContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        Player player = context.getPlayer();
        Direction clicked = context.getClickedFace();
        Direction.Axis axis;
        if (player != null && player.isShiftKeyDown()) {
            axis = context.getNearestLookingDirection().getAxis();
        } else {
            axis = null;
            int best = 0;
            for (Direction.Axis a : Direction.Axis.values()) {
                int score = KineticPlacement.score(block, level, pos, a);
                if (score > best || score == best && score > 0 && a == clicked.getAxis()) {
                    best = score;
                    axis = a;
                }
            }
            if (axis == null) {
                //Sans arbre voisin : axe horizontal ; au sol / au plafond, le flanc face au joueur ; contre un mur, l'axe longe le mur
                axis = clicked.getAxis() == Direction.Axis.Y ? context.getHorizontalDirection().getClockWise().getAxis() : clicked.getClockWise().getAxis();
            }
        }
        return block.defaultBlockState().setValue(AXIS, axis).setValue(FACING, facade(level, pos, axis, clicked, player));
    }

    /** Facade : vers un recepteur d'energie, sinon dos au support clique, sinon vers le joueur (toujours perpendiculaire a l'axe). */
    static Direction facade(Level level, BlockPos pos, Direction.Axis axis, Direction clicked, @Nullable Player player) {
        for (Direction d : Directions.ALL) {
            if (d.getAxis() == axis) {
                continue;
            }
            //(une machine de l'autre cote d'une frontiere de claim refusee ne recevra rien : elle n'oriente pas la facade)
            BlockEntity other = ClanApi.canTransfer(level, pos, pos.relative(d)) ? level.getBlockEntity(pos.relative(d)) : null;
            if (other != null && !(other instanceof KineticNode)
                && other.getCapability(ForgeCapabilities.ENERGY, d.getOpposite()).map(e -> e.canReceive()).orElse(false)) {
                return d;
            }
        }
        if (clicked.getAxis() != axis) {
            return clicked;
        }
        Vec3 look = player != null ? player.getLookAngle() : new Vec3(0, -1, 0);
        Direction best = Direction.UP;
        double score = -2;
        for (Direction d : Directions.ALL) {
            if (d.getAxis() == axis) {
                continue;
            }
            double s = -(look.x * d.getStepX() + look.y * d.getStepY() + look.z * d.getStepZ()) + (d == Direction.UP ? 0.15 : 0);
            if (s > score) {
                score = s;
                best = d;
            }
        }
        return best;
    }

    /** Facade valide (perpendiculaire a l'axe) ; un etat d'avant 1.12.4 (facade par defaut sur l'axe) prend le dessus ou le nord. */
    public static Direction facing(BlockState state) {
        Direction f = state.hasProperty(FACING) ? state.getValue(FACING) : Direction.UP;
        Direction.Axis axis = axis(state);
        return f.getAxis() != axis ? f : axis == Direction.Axis.Y ? Direction.NORTH : Direction.UP;
    }

    @NotNull
    @Override
    public BlockState rotate(@NotNull BlockState state, @NotNull Rotation rotation) {
        return super.rotate(state, rotation).setValue(FACING, rotation.rotate(state.getValue(FACING)));
    }

    @Override
    protected InteractionResult interact(BlockState state, Level level, BlockPos pos, Player player, InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide && player instanceof ServerPlayer serverPlayer && level.getBlockEntity(pos) instanceof DynamoTile tile) {
            NetworkHooks.openScreen(serverPlayer, tile, pos);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }

    // API Machine (lot 1) : l'infobulle de l'objet est tiree de la fiche de la machine (api.machine.MachineTooltips, tools/gc/api).

    @Override
    public BlockEntity newBlockEntity(@NotNull BlockPos pos, @NotNull BlockState state) {
        return new DynamoTile(pos, state);
    }
}
